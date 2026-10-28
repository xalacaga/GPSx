// SPDX-License-Identifier: AGPL-3.0-only

pub const FIXTURE: &str = include_str!("../fixtures/registration_codec_oracle_v1.json");

#[derive(Debug)]
pub struct Inputs<'a> {
    pub synthetic_number: u32,
    pub seed_16: &'a str,
    pub online_id: &'a str,
    pub account_id: &'a str,
}

#[derive(Debug)]
pub struct Case<'a> {
    pub name: &'a str,
    pub profile: &'a str,
    pub identity: &'a str,
    pub target: u32,
    pub cipher_offset: Option<u8>,
    pub aeropause_offset: Option<u8>,
    pub filler: &'a str,
    pub plaintext_inner: &'a str,
    pub aeropause: &'a str,
    pub encrypted_inner: &'a str,
    pub payload: &'a str,
    pub outer_request: &'a str,
    pub trailing_comma: bool,
}

#[derive(Debug)]
pub struct Oracle<'a> {
    pub inputs: Inputs<'a>,
    pub cases: Vec<Case<'a>>,
}

fn exact<'a>(lines: &mut impl Iterator<Item = &'a str>, expected: &str) -> Option<()> {
    (lines.next()? == expected).then_some(())
}

fn value<'a>(line: &'a str, prefix: &str, suffix: &str) -> Option<&'a str> {
    line.strip_prefix(prefix)?.strip_suffix(suffix)
}

fn string_value<'a>(
    lines: &mut impl Iterator<Item = &'a str>,
    name: &str,
    trailing_comma: bool,
) -> Option<&'a str> {
    let prefix = format!("      \"{name}\":\"");
    let suffix = if trailing_comma { "\"," } else { "\"" };
    value(lines.next()?, &prefix, suffix)
}

fn number_value<'a, T: core::str::FromStr>(
    lines: &mut impl Iterator<Item = &'a str>,
    name: &str,
) -> Option<T> {
    let prefix = format!("      \"{name}\":");
    value(lines.next()?, &prefix, ",")?.parse().ok()
}

fn offset_value<'a>(lines: &mut impl Iterator<Item = &'a str>, name: &str) -> Option<Option<u8>> {
    let prefix = format!("      \"{name}\":");
    let raw = value(lines.next()?, &prefix, ",")?;
    if raw == "null" {
        Some(None)
    } else {
        Some(Some(raw.parse().ok()?))
    }
}

pub fn parse(fixture: &str) -> Option<Oracle<'_>> {
    let mut lines = fixture.lines();
    exact(&mut lines, "{")?;
    exact(&mut lines, "  \"schema_version\":1,")?;
    exact(
        &mut lines,
        "  \"upstream_commit\":\"c8bea2642d0e95402f373af55584a3b98cd89417\",",
    )?;
    exact(&mut lines, "  \"inputs\":{")?;
    let synthetic_number = value(lines.next()?, "    \"synthetic_number\":", ",")?
        .parse()
        .ok()?;
    let seed_16 = value(lines.next()?, "    \"seed_16\":\"", "\",")?;
    let online_id = value(lines.next()?, "    \"online_name\":\"", "\",")?;
    let account_id = value(lines.next()?, "    \"identity_bytes\":\"", "\"")?;
    exact(&mut lines, "  },")?;
    exact(&mut lines, "  \"cases\":[")?;

    let mut cases = Vec::new();
    loop {
        let line = lines.next()?;
        if line == "  ]" {
            break;
        }
        if line != "    {" {
            return None;
        }
        let name = string_value(&mut lines, "name", true)?;
        let profile = string_value(&mut lines, "profile", true)?;
        let identity = string_value(&mut lines, "identity", true)?;
        let target = number_value(&mut lines, "target")?;
        let cipher_offset = offset_value(&mut lines, "cipher_offset")?;
        let aeropause_offset = offset_value(&mut lines, "aeropause_offset")?;
        let filler = string_value(&mut lines, "filler", true)?;
        let plaintext_inner = string_value(&mut lines, "plaintext_inner", true)?;
        let aeropause = string_value(&mut lines, "aeropause", true)?;
        let encrypted_inner = string_value(&mut lines, "encrypted_inner", true)?;
        let payload = string_value(&mut lines, "payload", true)?;
        let outer_request = string_value(&mut lines, "outer_request", false)?;
        let closing = lines.next()?;
        let trailing_comma = match closing {
            "    }," => true,
            "    }" => false,
            _ => return None,
        };
        cases.push(Case {
            name,
            profile,
            identity,
            target,
            cipher_offset,
            aeropause_offset,
            filler,
            plaintext_inner,
            aeropause,
            encrypted_inner,
            payload,
            outer_request,
            trailing_comma,
        });
    }
    exact(&mut lines, "}")?;
    if lines.next().is_some() {
        return None;
    }
    Some(Oracle {
        inputs: Inputs {
            synthetic_number,
            seed_16,
            online_id,
            account_id,
        },
        cases,
    })
}

pub fn decode_hex(value: &str) -> Option<Vec<u8>> {
    if value.len() % 2 != 0 {
        return None;
    }
    value
        .as_bytes()
        .chunks_exact(2)
        .map(|pair| {
            let high = (pair[0] as char).to_digit(16)? as u8;
            let low = (pair[1] as char).to_digit(16)? as u8;
            Some((high << 4) | low)
        })
        .collect()
}

pub fn has_crlf_only(fixture: &str) -> bool {
    let bytes = fixture.as_bytes();
    bytes
        .iter()
        .enumerate()
        .filter(|(_, byte)| **byte == b'\n')
        .all(|(index, _)| index > 0 && bytes[index - 1] == b'\r')
}

pub fn duplicate_first_case() -> String {
    let first_start = FIXTURE.find("    {\r\n").expect("fixture has a first case");
    let first_end = first_start
        + FIXTURE[first_start..]
            .find("\r\n    },")
            .expect("first case has a trailing comma")
        + "\r\n    },".len();
    let first_case = &FIXTURE[first_start..first_end];
    let mut mutated = FIXTURE.to_owned();
    mutated.insert_str(first_end, &format!("\r\n{first_case}"));
    mutated
}
