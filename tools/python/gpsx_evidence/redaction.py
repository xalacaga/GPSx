"""Conservative secret redaction for text and JSON-compatible values.

Key classification must stay identical to `crates/gpsx-telemetry/src/lib.rs`; both test
suites check `tests/redaction/key_cases.tsv`. Redaction errs toward removing data.
"""

from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

REDACTED = "[REDACTED]"

_STRONG_PARTS = ("token", "password", "passwd", "passphrase", "secret", "cookie", "credential", "bearer")
_EXACT_TOKENS = frozenset(
    {
        "pin",
        "nonce",
        "serial",
        "uniq",
        "mac",
        "otp",
        "psk",
        "authorization",
        "signature",
        "sessionkey",
        "rpkey",
        "registkey",
        "registrationkey",
        "accountid",
        "onlineid",
        "hostid",
        "macaddress",
        "authcode",
    }
)
_PAIRS = frozenset(
    {
        ("session", "key"),
        ("rp", "key"),
        ("regist", "key"),
        ("registration", "key"),
        ("private", "key"),
        ("api", "key"),
        ("account", "id"),
        ("online", "id"),
        ("host", "id"),
        ("psn", "id"),
        ("user", "id"),
        ("auth", "code"),
        ("authorization", "code"),
    }
)
_WHOLE_KEYS = frozenset({"code", "key", "auth", "sid"})

_AUTH_HEADER_RE = re.compile(r"(?im)\b((?:proxy-)?authorization)([\"']?[ \t]*[:=][ \t]*)([^\r\n\"',;]+)")
_BEARER_RE = re.compile(r"(?i)\bbearer\s+(?!\[REDACTED\])[A-Za-z0-9._~+/=-]+")
_KEY_SEP_RE = re.compile(r"([\"']?)([A-Za-z_][\w.\-]*)\1([ \t]*[:=][ \t]*)")
_URL_RE = re.compile(r"\b[a-z][a-z0-9+.\-]*://[^\s\"'<>]+", re.IGNORECASE)
_MAC_RE = re.compile(r"(?i)\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\b")


def key_tokens(key: object) -> list[str]:
    """Split a key into lowercase words at camelCase boundaries and non-alphanumerics."""
    text = str(key)
    tokens: list[str] = []
    current: list[str] = []
    for index, character in enumerate(text):
        if not character.isalnum():
            if current:
                tokens.append("".join(current))
                current = []
            continue
        previous = text[index - 1] if index > 0 else ""
        following = text[index + 1] if index + 1 < len(text) else ""
        boundary = character.isupper() and (
            previous.islower() or previous.isdigit() or (previous.isupper() and following.islower())
        )
        if boundary and current:
            tokens.append("".join(current))
            current = []
        current.append(character.lower())
    if current:
        tokens.append("".join(current))
    return tokens


def is_sensitive_key(key: object) -> bool:
    tokens = key_tokens(key)
    if len(tokens) == 1 and tokens[0] in _WHOLE_KEYS:
        return True
    if any(token in _EXACT_TOKENS or any(part in token for part in _STRONG_PARTS) for token in tokens):
        return True
    return any(pair in _PAIRS for pair in zip(tokens, tokens[1:]))


def redact_json_value(value: Any, *, parent_key: str = "", allowed_keys: frozenset[str] | None = None) -> Any:
    """Redact a JSON-compatible value.

    With `allowed_keys`, any mapping key outside the allow-list is redacted as well
    (default deny), which is the recommended mode for captures.
    """
    if parent_key and (is_sensitive_key(parent_key) or (allowed_keys is not None and parent_key not in allowed_keys)):
        return REDACTED
    if isinstance(value, Mapping):
        return {
            str(key): redact_json_value(item, parent_key=str(key), allowed_keys=allowed_keys)
            for key, item in value.items()
        }
    if isinstance(value, (list, tuple)):
        items = [redact_json_value(item, allowed_keys=allowed_keys) for item in value]
        return tuple(items) if isinstance(value, tuple) else items
    if isinstance(value, str):
        return redact_text(value)
    return value


def _redact_auth_header(match: re.Match[str]) -> str:
    words = match.group(3).split(None, 1)
    if len(words) == 2 and words[0].isalpha():
        return f"{match.group(1)}{match.group(2)}{words[0]} {REDACTED}"
    return f"{match.group(1)}{match.group(2)}{REDACTED}"


def _value_span(text: str, start: int, *, spaced: bool, json_like: bool) -> tuple[int, int]:
    """Return the [start, end) span of the value that begins at `start`."""
    if start < len(text) and text[start] in "\"'":
        quote = text[start]
        index = start + 1
        while index < len(text) and text[index] != quote:
            index += 2 if text[index] == "\\" else 1
        return start + 1, min(index, len(text))
    line_end = len(text)
    for newline in ("\r", "\n"):
        position = text.find(newline, start)
        if position != -1:
            line_end = min(line_end, position)
    if spaced and not json_like:
        # "password = two words": without a quote the value may contain spaces.
        return start, line_end
    stops = ",;&\"'}] \t" if json_like or not spaced else ""
    end = start
    while end < line_end and text[end] not in stops:
        end += 1
    return start, end


def _redact_assignments(text: str) -> str:
    output: list[str] = []
    position = 0
    for match in _KEY_SEP_RE.finditer(text):
        if match.start() < position:
            continue
        key = match.group(2)
        if not is_sensitive_key(key) or "authorization" in key_tokens(key):
            continue
        separator = match.group(3)
        start, end = _value_span(
            text,
            match.end(),
            spaced=separator != separator.strip(),
            json_like=bool(match.group(1)),
        )
        if end <= start or text[start:end] == REDACTED:
            continue
        output.append(text[position:start])
        output.append(REDACTED)
        position = end
    output.append(text[position:])
    return "".join(output)


def _redact_query(query: str) -> str:
    pairs = [(key, REDACTED if is_sensitive_key(key) else item) for key, item in parse_qsl(query, keep_blank_values=True)]
    return urlencode(pairs)


def _redact_url(match: re.Match[str]) -> str:
    raw = match.group(0)
    try:
        parts = urlsplit(raw)
        netloc = parts.netloc
        if "@" in netloc:
            netloc = f"{REDACTED}@{netloc.rpartition('@')[2]}"
        fragment = _redact_query(parts.fragment) if "=" in parts.fragment else parts.fragment
        return urlunsplit((parts.scheme, netloc, parts.path, _redact_query(parts.query), fragment))
    except ValueError:
        return "[REDACTED_URL]"


def redact_text(text: str) -> str:
    redacted = _AUTH_HEADER_RE.sub(_redact_auth_header, text)
    redacted = _redact_assignments(redacted)
    redacted = _URL_RE.sub(_redact_url, redacted)
    redacted = _BEARER_RE.sub(f"Bearer {REDACTED}", redacted)
    return _MAC_RE.sub(REDACTED, redacted)
