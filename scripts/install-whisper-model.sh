#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/whisper/ggml-base.bin"
mkdir -p "$(dirname "$DEST")"
URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
curl -L --fail --retry 3 -o "$DEST" "$URL"
echo "SHA-1:"
sha1sum "$DEST"
