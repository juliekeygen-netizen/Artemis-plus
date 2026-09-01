#!/usr/bin/env bash
set -euo pipefail

# Read `apksigner verify --print-certs` output from stdin and print exactly one normalized
# certificate SHA-256 digest. Android build-tools have emitted several label variants, including:
#   Signer #1 certificate SHA-256 digest: <hex>
#   V2 Signer: certificate SHA-256 digest: <hex>
# The parser deliberately keys off the final `SHA-256 digest:` label rather than the first colon.

input="$(cat)"
line="$(printf '%s\n' "$input" | grep -i -m1 'certificate[[:space:]].*SHA-256[[:space:]]*digest[[:space:]]*:' || true)"

if [[ -z "$line" ]]; then
  echo 'No certificate SHA-256 digest line found in apksigner output.' >&2
  exit 1
fi

# Strip everything through the final `SHA-256 digest:` label, then normalize whitespace and
# optional colon-separated hex formatting.
digest="$(printf '%s\n' "$line" \
  | sed -E 's/^.*SHA-256[[:space:]]*digest[[:space:]]*:[[:space:]]*//I' \
  | tr -d '[:space:]:' \
  | tr '[:upper:]' '[:lower:]')"

if [[ ! "$digest" =~ ^[0-9a-f]{64}$ ]]; then
  echo 'Certificate SHA-256 digest is not exactly 64 hexadecimal characters.' >&2
  echo "Certificate line: $line" >&2
  exit 1
fi

printf '%s\n' "$digest"
