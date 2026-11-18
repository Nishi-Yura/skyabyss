# look5 の 1 品ずつの流れ（作業フォルダ = スクラッチパッドの work）
#   bash mstep.sh prep <設計モジュール> <id>   設計を build → 下見用 .bbmodel → geo.json と絵（Blockbench の from_geo_json 用）、筆の座標を brush.txt へ
#   bash mstep.sh done <id>                     Blockbench の書き出し out_<id>.bbmodel の名前を戻して取り込む（弓は引きの 3 段も）
set -e
R=/c/Users/nisimoto/Desktop/mod
S="${WORK:-C:/Users/nisimoto/AppData/Local/Temp/claude/789edb19516a07b1/33cb91ee-5ffd-4188-9573-8953440b061c/scratchpad/work}"
mkdir -p "$S/geo"; cd "$S"
if [ "$1" = prep ]; then
  python -I $R/tools/models/x32/look5/$2.py $3 >/dev/null
  python -I -c "
import sys, json; sys.path.insert(0, r'C:/Users/nisimoto/Desktop/mod/tools/models/x32/look5')
from lib import write_bbmodel
write_bbmodel(json.load(open('$3.json')), '$3_base.png', '$3.bbmodel', '$3', (lambda n: not n.startswith('string_') or n.startswith('string_0')) if '$3'.endswith('bow') else None)"
  python -I $R/tools/models/x32/look5/bb2geo.py $3.bbmodel geo
  python -I $R/tools/models/x32/look5/brush.py $2 $3 > brush_$3.txt
  cat brush_$3.txt
else
  python -I $R/tools/models/x32/look5/ungeo.py out_$2.bbmodel $2.bbmodel
  cd $R
  case $2 in *bow) python -I tools/models/x32/look5/install_bow.py "$S/out_$2.bbmodel" $2 "$S" | tail -1 ;; *) python -I tools/models/x32/look5/install.py "$S/out_$2.bbmodel" $2 | tail -1 ;; esac
fi
