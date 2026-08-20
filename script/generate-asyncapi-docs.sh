#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$PROJECT_DIR"

npx --yes @asyncapi/cli@6.0.2 generate fromTemplate \
  docs/asyncapi.yaml \
  @asyncapi/html-template@3.5.4 \
  --output src/main/resources/static/asyncapi \
  --param singleFile=true \
  --force-write \
  --no-interactive
