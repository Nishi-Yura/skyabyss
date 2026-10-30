#!/bin/bash
# ゲームを起動する gradle (runServer・runShots) を、共有の枠を取ってから流す。
#   使い方 (worktree の中で):  bash tools/dev/mc_run.sh runServer        (環境変数はそのまま渡る)
#                               bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/x.txt -PshotsName=x
#   枠の数    環境変数 MC_SLOTS (既定 4。上限 8)。ただし実際に取るときは空きメモリを見る (下の MC_SLOT_MB)
#   枠        C:/Users/nisimoto/Desktop/.mc_lock (一つ目。前からの決まりと同じ)、.mc_lock2 、.mc_lock3 …
#   メモリ    サーバー一つ ≒ 3 GB。空き物理メモリが MC_SLOT_MB (既定 3500) 未満なら、枠が空いていても取らずに待つ
#             (MC_SKIP_MEM_CHECK=1 で見ない。一つ目の枠だけは、空きが少なくても誰も動かしていなければ取る)
#   待ち      待っている間は 30 秒おきに「どの枠が誰で、空きメモリはいくつか」を出す
#   ポート    サーバーは起動のたびに 25600〜25999 から空いていそうなポートを選ぶ (枠を使わずに立てた人とも取り合わない)
#   後始末    終わったら (失敗しても) 自分の枠だけ外す。持ち主が居なくなった枠 (6 時間より古い) は掃除する。compileJava だけなら枠は要らない
#   ワールド  SKYABYSS_SELFTEST があり run/world が無ければ、同じソースの雛形 (tools/dev/world_template.sh で作る) を写す。SKYABYSS_NO_TEMPLATE=1 で無効
set -u
SLOTS_N=${MC_SLOTS:-4}
[ "$SLOTS_N" -gt 8 ] && SLOTS_N=8
[ "$SLOTS_N" -lt 1 ] && SLOTS_N=1
SLOT_MB=${MC_SLOT_MB:-3500}
BASE="C:/Users/nisimoto/Desktop"
slot_dir() { if [ "$1" -eq 0 ]; then echo "$BASE/.mc_lock"; else echo "$BASE/.mc_lock$(($1 + 1))"; fi; }

free_mb() {
  # 空き物理メモリ (MB)。取れなければ大きな値を返して止めない
  local kb
  kb=$(powershell -NoProfile -Command "(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory" 2>/dev/null | tr -d '\r')
  if [ -n "$kb" ] && [ "$kb" -eq "$kb" ] 2>/dev/null; then echo $((kb / 1024)); else echo 99999; fi
}

if [ -n "${SKYABYSS_SELFTEST_LIST:-}" ]; then
  # 段の一覧だけ (ワールドを読まずに終わる) なら、枠もメモリも要らない
  mkdir -p run
  exec ./gradlew --no-daemon --offline "$@"
fi
slot=-1
waited=0
while [ $slot -lt 0 ]; do
  # 古い枠 (持ち主が落ちて残ったもの) を掃除
  for i in $(seq 0 $((SLOTS_N - 1))); do
    d=$(slot_dir $i)
    if [ -d "$d" ] && [ -n "$(find "$d" -maxdepth 0 -mmin +360 2>/dev/null)" ]; then
      echo "[mc_run] clearing stale slot $((i + 1)) (older than 6 h)"
      rm -rf "$d" 2>/dev/null
    fi
  done
  mem=99999
  [ "${MC_SKIP_MEM_CHECK:-0}" != "1" ] && mem=$(free_mb)
  busy=0
  for i in $(seq 0 $((SLOTS_N - 1))); do [ -d "$(slot_dir $i)" ] && busy=$((busy + 1)); done
  if [ "$mem" -ge "$SLOT_MB" ] || [ "$busy" -eq 0 ]; then
    for i in $(seq 0 $((SLOTS_N - 1))); do
      if mkdir "$(slot_dir $i)" 2>/dev/null; then
        slot=$i
        break
      fi
    done
  fi
  if [ $slot -lt 0 ]; then
    if [ $((waited % 30)) -eq 0 ]; then
      echo "[mc_run] waiting: ${busy}/${SLOTS_N} slots busy, free RAM ${mem} MB (need ${SLOT_MB} MB); $((waited / 60)) min so far"
    fi
    sleep 5
    waited=$((waited + 5))
  fi
done
mydir=$(slot_dir $slot)
echo "pid=$$ cwd=$(pwd) at=$(date +%H:%M)" > "$mydir/owner.txt" 2>/dev/null || true
trap 'rm -f "$mydir/owner.txt" 2>/dev/null; rmdir "$mydir" 2>/dev/null' EXIT
mkdir -p run
PORT=$((25600 + RANDOM % 400))
if [ -f run/server.properties ]; then
  sed -i "s/^server-port=.*/server-port=${PORT}/" run/server.properties
  grep -q '^server-port=' run/server.properties || echo "server-port=${PORT}" >> run/server.properties
else
  echo "server-port=${PORT}" > run/server.properties
fi
# 雛形のワールド (背・深淵を建て済み)。無ければ何もしない
if [ -n "${SKYABYSS_SELFTEST:-}" ] && [ "${SKYABYSS_NO_TEMPLATE:-0}" != "1" ] && [ ! -d run/world ] && [ -x tools/dev/world_template.sh -o -f tools/dev/world_template.sh ]; then
  bash tools/dev/world_template.sh restore || true
fi
echo "[mc_run] slot $((slot + 1))/${SLOTS_N} port ${PORT} free RAM $(free_mb) MB: gradle $*"
./gradlew --no-daemon --offline "$@"
