#!/bin/bash
# 自己テスト用のワールドの雛形 (背を建て済み)。同じソースなら建て直さずに写せる。
#   bash tools/dev/world_template.sh key            今のソースの雛形ハッシュを出す
#   bash tools/dev/world_template.sh restore         雛形があれば run/world に写す (mc_run.sh が自動で呼ぶ。run/world があれば何もしない)
#   bash tools/dev/world_template.sh make [ONLY]     雛形を作る: 自己テストを ONLY (既定 "titan back (build)") だけ流し、できた run/world を保存する
#                                                    (ゲームの枠を取る。run/world は作り直すので、手元のワールドは消える)
# ハッシュは titan/ abyss/ story/ch5/ registry/ block/ と data/ のソース全部の中身から取る。
# どれかを一文字でも変えると別の雛形になる (古い雛形で試して見落とす、を避けるため)。雛形は C:/Users/nisimoto/Desktop/.mc_templates/<hash>/world。
# 古い雛形 (7 日より前) は make のときに消す。
set -u
BASE="C:/Users/nisimoto/Desktop/.mc_templates"
J=src/main/java/jp/revati/skyabyss
tkey() {
  { find $J/titan $J/abyss $J/story/ch5 $J/registry $J/block src/main/resources/data -type f 2>/dev/null | sort | xargs sha1sum 2>/dev/null; echo "seed=4242"; } | sha1sum | cut -c1-16
}
cmd=${1:-key}
h=$(tkey)
case "$cmd" in
  key) echo "$h" ;;
  restore)
    if [ -d run/world ]; then exit 0; fi
    if [ -d "$BASE/$h/world" ]; then
      mkdir -p run
      cp -r "$BASE/$h/world" run/world
      echo "[world_template] restored template $h into run/world"
    else
      echo "[world_template] no template for $h (make one with: bash tools/dev/world_template.sh make)"
    fi
    ;;
  make)
    only=${2:-titan back (build)}
    rm -rf run/world
    find "$BASE" -maxdepth 1 -mindepth 1 -type d -mtime +7 -exec rm -rf {} + 2>/dev/null
    SKYABYSS_NO_TEMPLATE=1 SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="$only" bash tools/dev/mc_run.sh runServer || exit 1
    if [ ! -d run/world ]; then echo "[world_template] run/world was not produced"; exit 1; fi
    rm -rf "$BASE/$h"
    mkdir -p "$BASE/$h"
    cp -r run/world "$BASE/$h/world"
    rm -f "$BASE/$h/world/session.lock"
    echo "[world_template] saved template $h ($(du -sm "$BASE/$h" | cut -f1) MB)"
    ;;
  *) echo "usage: world_template.sh key|restore|make [ONLY]"; exit 2 ;;
esac
