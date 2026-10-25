import unittest
from pathlib import Path

from gpsx_evidence.redaction import REDACTED, is_sensitive_key, redact_json_value, redact_text

SECRET = "S3CR3T"
KEY_CASES = Path(__file__).resolve().parents[3] / "tests" / "redaction" / "key_cases.tsv"


class AdversarialRedactionTests(unittest.TestCase):
    """Closing tests for review findings R1-R3, R5 and R6."""

    def test_key_classification_matches_shared_corpus(self) -> None:
        checked = 0
        for line in KEY_CASES.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            key, expected = line.split("\t")
            with self.subTest(key=key):
                self.assertEqual(is_sensitive_key(key), expected == "sensitive")
            checked += 1
        self.assertGreater(checked, 40)

    def test_text_secrets_never_survive(self) -> None:
        samples = [
            f"accessToken={SECRET}",
            f"sessionKey: {SECRET}",
            f"rpKey={SECRET}",
            f"registKey={SECRET}",
            f'{{"session_key": "{SECRET}"}}',
            f"{{'rp_key': '{SECRET}'}}",
            f"Authorization: Basic {SECRET}",
            f"Proxy-Authorization: Digest {SECRET}",
            f"https://example.test/redirect?code={SECRET}&state=x",
            f"https://example.test/cb#access_token={SECRET}",
            f"https://user:{SECRET}@example.test/",
            f"com.example.app://redirect?code={SECRET}",
            f"pin={SECRET}",
            f"account_id={SECRET}",
            f"host-id:{SECRET}",
            f"password = hunter {SECRET}",
            f"fps=60 session_key={SECRET} jitter_ms=2",
        ]
        for sample in samples:
            with self.subTest(sample=sample):
                self.assertNotIn(SECRET, redact_text(sample))

    def test_json_secret_keys_in_any_case_style(self) -> None:
        for key in ("sessionKey", "SESSION_KEY", "rp-key", "registKey", "macAddress", "pin", "accountId", "host_id", "code"):
            with self.subTest(key=key):
                self.assertEqual(redact_json_value({key: SECRET}), {key: REDACTED})

    def test_device_identifier_formats(self) -> None:
        for sample in ("AA-BB-CC-DD-EE-FF", "aa:bb:cc:dd:ee:ff"):
            with self.subTest(sample=sample):
                self.assertEqual(redact_text(sample), REDACTED)

    def test_safe_telemetry_keys_are_not_over_redacted(self) -> None:
        value = {"unique_frames": 12, "deserialize_ms": 3, "author": "x", "keyframe_interval": 60, "error_code": 7}
        self.assertEqual(redact_json_value(value), value)

    def test_safe_pairs_after_a_secret_survive(self) -> None:
        self.assertEqual(
            redact_text(f"fps=60 session_key={SECRET} jitter_ms=2"),
            f"fps=60 session_key={REDACTED} jitter_ms=2",
        )

    def test_allow_list_mode_denies_unknown_keys(self) -> None:
        value = {"fps": 60, "vendor_blob": "opaque", "nested": {"fps": 59}}
        self.assertEqual(
            redact_json_value(value, allowed_keys=frozenset({"fps", "nested"})),
            {"fps": 60, "vendor_blob": REDACTED, "nested": {"fps": 59}},
        )


class RedactionTests(unittest.TestCase):
    def test_redacts_nested_secret_keys(self) -> None:
        value = {"safe": 7, "session": {"accessToken": "abc", "latency_ms": 5}}
        self.assertEqual(
            redact_json_value(value),
            {"safe": 7, "session": {"accessToken": REDACTED, "latency_ms": 5}},
        )

    def test_redacts_bearer_token(self) -> None:
        self.assertEqual(redact_text("Authorization: Bearer abc.def-123"), f"Authorization: Bearer {REDACTED}")

    def test_redacts_secret_query_but_keeps_safe_query(self) -> None:
        result = redact_text("open https://example.test/cb?token=abc&state=ready")
        self.assertIn("token=%5BREDACTED%5D", result)
        self.assertIn("state=ready", result)
        self.assertNotIn("abc", result)

    def test_redacts_assignment(self) -> None:
        self.assertEqual(redact_text("session_key=hunter2"), f"session_key={REDACTED}")

    def test_preserves_non_secret_telemetry(self) -> None:
        original = "fps=59.9 jitter_ms=2.4 decoder=hardware"
        self.assertEqual(redact_text(original), original)

    def test_redacts_device_identifiers(self) -> None:
        self.assertEqual(redact_text("Uniq=11:22:33:aa:bb:cc"), f"Uniq={REDACTED}")


if __name__ == "__main__":
    unittest.main()

