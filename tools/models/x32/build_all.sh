#!/usr/bin/env bash
# x32 の全品（または名前に引数を含む品だけ）を、形の決まり（*_parts.py）から作り直してゲームへ取り込む。
#   bash tools/models/x32/build_all.sh            全部
#   bash tools/models/x32/build_all.sh tide_      名前に tide_ を含む品だけ
# シリーズの色（series/*.py）を変えたら、そのシリーズをこれで作り直す。
set -e
cd "$(dirname "$0")/../../.."
D=tools/models/x32
for spec in $D/*_parts.py; do
  name=$(basename "$spec" _parts.py)
  [[ -n "$1" && "$name" != *"$1"* ]] && continue
  if grep -q '^VARIANTS' "$spec"; then
    python -I tools/gen/pixel/paint_cubes.py "$spec" $D > /dev/null
    for v in "$name" "${name}_pull0" "${name}_pull1" "${name}_pull2"; do
      python -I tools/gen/pixel/bb_import.py "$D/$v.bbmodel"
    done
  else
    python -I tools/gen/pixel/paint_cubes.py "$spec" "$D/$name.bbmodel" > /dev/null
    python -I tools/gen/pixel/bb_import.py "$D/$name.bbmodel"
  fi
done
python -I tools/gen/pixel/look_holds.py
python -I tools/gen/pixel/gear_manifest.py
