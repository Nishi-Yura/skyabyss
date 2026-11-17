#!/usr/bin/env bash
# 祠のシリーズ（x32）を形の決まり（*_parts.py）から作り直し、ゲームへ取り込む。
#   bash tools/models/x32/build_shrine.sh
set -e
cd "$(dirname "$0")/../../.."
D=tools/models/x32
for n in staff sword hammer dagger bell_staff; do
  python -I tools/gen/pixel/paint_cubes.py $D/shrine_${n}_parts.py $D/shrine_${n}.bbmodel
done
python -I tools/gen/pixel/paint_cubes.py $D/shrine_bow_parts.py $D
for n in staff sword hammer dagger bell_staff bow bow_pull0 bow_pull1 bow_pull2; do
  python -I tools/gen/pixel/bb_import.py $D/shrine_${n}.bbmodel
done
