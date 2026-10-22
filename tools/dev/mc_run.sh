#!/bin/bash
# ゲームを起動する gradle (runServer・runShots) を、共有の枠を取ってから流す。枠は二つ (同時に二つまで)。
#   使い方 (worktree の中で):  bash tools/dev/mc_run.sh runServer        (環境変数はそのまま渡る)
#                               bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/x.txt -PshotsName=x
#   枠        C:/Users/nisimoto/Desktop/.mc_lock (一つ目。前からの決まりと同じ) と .mc_lock2 (二つ目)。空いた方を取る
#   ポート    サーバーは枠ごとに別のポート (25565 / 25585) にする (二つ同時に立つと同じポートを取り合う)
#   後始末    終わったら (失敗しても) 自分の枠だけ外す。compileJava だけなら枠は要らない
set -u
SLOTS=("C:/Users/nisimoto/Desktop/.mc_lock" "C:/Users/nisimoto/Desktop/.mc_lock2")
PORTS=(25565 25585)
slot=-1
while [ $slot -lt 0 ]; do
  for i in 0 1; do
    if mkdir "${SLOTS[$i]}" 2>/dev/null; then
      slot=$i
      break
    fi
  done
  [ $slot -lt 0 ] && sleep 5
done
trap 'rmdir "${SLOTS[$slot]}" 2>/dev/null' EXIT
mkdir -p run
if [ -f run/server.properties ]; then
  sed -i "s/^server-port=.*/server-port=${PORTS[$slot]}/" run/server.properties
  grep -q '^server-port=' run/server.properties || echo "server-port=${PORTS[$slot]}" >> run/server.properties
else
  echo "server-port=${PORTS[$slot]}" > run/server.properties
fi
echo "[mc_run] slot $((slot + 1)) port ${PORTS[$slot]}: gradle $*"
./gradlew --no-daemon --offline "$@"
