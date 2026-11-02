#!/bin/bash
# コミット前の速い確認を一度に: compileJava + 資源の参照と看板の幅 (check_resources) + lang の鍵 + メッシュの水密 + 生成物のずれ + 文字のはみ出し + 階段 (あれば)。ゲームは起動しない (枠は要らない)。
#   bash tools/dev/quick_check.sh            全部
#   bash tools/dev/quick_check.sh --no-java  compileJava を飛ばす (Python の確認だけ)
# 失敗した確認の名前を最後にまとめて出し、ひとつでも失敗なら終了コード 1。
set -u
fails=()
run() {
  local name=$1
  shift
  echo "=== quick_check: $name"
  local t0=$SECONDS
  if "$@"; then
    echo "--- $name OK ($((SECONDS - t0)) s)"
  else
    echo "--- $name FAILED ($((SECONDS - t0)) s)"
    fails+=("$name")
  fi
}
if [ "${1:-}" != "--no-java" ]; then
  run compileJava ./gradlew --no-daemon --offline -q compileJava
fi
[ -f tools/audit/check_resources.py ] && run check_resources python -I tools/audit/check_resources.py
[ -f tools/audit/check_lang_keys.py ] && run check_lang_keys python -I tools/audit/check_lang_keys.py
[ -f tools/audit/mesh_audit.py ] && run mesh_audit python -I tools/audit/mesh_audit.py --check
[ -f tools/audit/check_generators.py ] && run check_generators python tools/audit/check_generators.py
[ -f tools/audit/check_text_fit.py ] && run check_text_fit python tools/audit/check_text_fit.py
for f in tools/audit/check_stairs*.py tools/audit/stairs*.py; do
  [ -f "$f" ] && run "$(basename "$f" .py)" python "$f"
done
echo
if [ ${#fails[@]} -eq 0 ]; then
  echo "quick_check: all OK"
else
  echo "quick_check: FAILED: ${fails[*]}"
  exit 1
fi
