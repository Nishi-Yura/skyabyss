#!/bin/bash
# 自己テストを「新しい大陸のワールド」で流す (docs/HANDOFF.md の自己テスト。前は scratchpad にあって消えた)。
#   bash tools/dev/selftest_fresh.sh                       全部 (378 段。1〜2 時間)
#   bash tools/dev/selftest_fresh.sh "cloudsea,skills"     段の指定 (SKYABYSS_SELFTEST_ONLY と同じ書き方)
#   bash tools/dev/selftest_fresh.sh "" myworld            ワールド名を決める (省くと selftest_<日時>)
# run/server.properties の level-name を一時的に替え、終わったら元に戻す。level-type は skyabyss:continent のまま。
# 雛形 (world_template.sh) は使わない (新しい世界で、建設から流すのが目的)。
set -u
only=${1:-}
name=${2:-selftest_$(date +%m%d_%H%M)}
props=run/server.properties
[ -f "$props" ] || { echo "no $props"; exit 1; }
orig=$(grep '^level-name=' "$props" | head -1)
grep -q '^level-type=skyabyss' "$props" || echo "[selftest_fresh] warning: level-type is not skyabyss:continent"
restore() { if [ -n "$orig" ]; then sed -i "s|^level-name=.*|$orig|" "$props"; fi; }
trap restore EXIT
if [ -n "$orig" ]; then sed -i "s|^level-name=.*|level-name=$name|" "$props"; else echo "level-name=$name" >> "$props"; fi
rm -rf "run/$name"
echo "[selftest_fresh] world run/$name only='${only}'"
SKYABYSS_NO_TEMPLATE=1 SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="$only" bash tools/dev/mc_run.sh runServer
code=$?
echo "[selftest_fresh] exit $code (log: run/logs/latest.log)"
exit $code
