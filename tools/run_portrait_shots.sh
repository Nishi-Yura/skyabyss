#!/bin/sh
# 3 案ぶんの会話画面を順に撮る (一度に動かすゲームは 1 本): sh tools/run_portrait_shots.sh
cd "$(dirname "$0")/.."
for s in a b c; do
  python tools/gen_portraits.py --install $s
  rm -rf run/shots/portraits_$s/saves run/shots/portraits_$s/shots
  ./gradlew runShots -Pscript=tools/shots/portraits.txt -PshotsName=portraits_$s
  for f in run/shots/portraits_$s/shots/portraits/portrait_*.png; do
    cp "$f" "docs/review/portraits/ingame_${s}_$(basename "$f" | sed 's/^portrait_//')"
  done
done
python tools/gen_portraits.py --install a
