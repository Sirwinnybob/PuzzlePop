#!/usr/bin/env bash
# Usage: tools/icon_sheet.sh [out.png]
# Renders every app/src/main/res/drawable/ic_*.xml (light tile, dark tile, true-size preview)
# onto one contact-sheet PNG. Needs python3 and ImageMagick (`magick` with librsvg).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 -I "$here/icon_sheet.py" "${1:-icon-sheet.png}"
