#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARSER="$SCRIPT_DIR/extract-apksigner-sha256.sh"
EXPECTED="88c430db21b298bab7b654ce3b9300e33bf1917df4bf1a73047c9590f0080083"

assert_parses() {
  local name="$1"
  local input="$2"
  local actual
  actual="$(printf '%s\n' "$input" | bash "$PARSER")"
  if [[ "$actual" != "$EXPECTED" ]]; then
    echo "[$name] expected $EXPECTED, got $actual" >&2
    exit 1
  fi
}

assert_rejects() {
  local name="$1"
  local input="$2"
  if printf '%s\n' "$input" | bash "$PARSER" >/dev/null 2>&1; then
    echo "[$name] parser unexpectedly accepted malformed input" >&2
    exit 1
  fi
}

# Exact Android build-tools output observed in live Build Debug APK run #112.
assert_parses 'v2-live-run-112' \
  "V2 Signer: certificate SHA-256 digest: $EXPECTED"

# Common older apksigner label.
assert_parses 'signer-numbered' \
  "Signer #1 certificate SHA-256 digest: $EXPECTED"

# Whitespace around the digest label must not matter.
assert_parses 'label-whitespace' \
  "Signer #1 certificate SHA-256 digest :    ${EXPECTED^^}"

# Some tools/users represent fingerprints as colon-separated bytes.
COLON_DIGEST="$(printf '%s' "$EXPECTED" | sed 's/../&:/g; s/:$//')"
assert_parses 'colon-separated' \
  "Signer #1 certificate SHA-256 digest: $COLON_DIGEST"

# The parser must select the SHA-256 certificate line, not unrelated signer metadata.
assert_parses 'multiline-output' \
  $'Signer #1 certificate DN: CN=Artemis Plus\nSigner #1 certificate SHA-256 digest: '"$EXPECTED"$'\nSigner #1 certificate SHA-1 digest: deadbeef'

assert_rejects 'missing-line' 'Signer #1 certificate DN: CN=Artemis Plus'
assert_rejects 'short-digest' 'Signer #1 certificate SHA-256 digest: deadbeef'
assert_rejects 'non-hex' 'Signer #1 certificate SHA-256 digest: zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz'

echo 'apksigner SHA-256 parser fixtures passed.'
