# usage: bash mstep.sh prep <id> | bash mstep.sh done <id>
set -e
R=/c/Users/nisimoto/Desktop/mod
S="C:/Users/nisimoto/AppData/Local/Temp/claude/789edb19516a07b1/33cb91ee-5ffd-4188-9573-8953440b061c/scratchpad/moon"
cd "$S"
if [ "$1" = prep ]; then
  python -I $R/tools/models/x32/look5/moon_bold.py $2 >/dev/null
  python -I -c "
import sys, json; sys.path.insert(0, r'C:/Users/nisimoto/Desktop/mod/tools/models/x32/look5')
from lib import write_bbmodel
write_bbmodel(json.load(open('$2.json')), '$2_base.png', '$2.bbmodel', '$2', (lambda n: not n.startswith('string_') or n.startswith('string_0')) if '$2'.endswith('bow') else None)"
  mkdir -p geo && python -I $R/tools/models/x32/look5/bb2geo.py $2.bbmodel geo
else
  python -I $R/tools/models/x32/look5/ungeo.py out_$2.bbmodel $2.bbmodel
  cd $R
  case $2 in *bow) python -I tools/models/x32/look5/install_bow.py "$S/out_$2.bbmodel" $2 "$S" | tail -1 ;; *) python -I tools/models/x32/look5/install.py "$S/out_$2.bbmodel" $2 | tail -1 ;; esac
fi
