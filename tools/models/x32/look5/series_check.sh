# シリーズを仕上げる: 持ち方 → manifest を done → 撮影の台本 → 撮影 → 一覧（BB とゲーム内）
#   bash tools/models/x32/look5/series_check.sh <series名> id...
set -e
cd /c/Users/nisimoto/Desktop/mod
S="C:/Users/nisimoto/AppData/Local/Temp/claude/789edb19516a07b1/33cb91ee-5ffd-4188-9573-8953440b061c/scratchpad"
name=$1; shift
python -I tools/gen/pixel/look_holds.py | tail -1
python -I - "$@" <<'PY'
import json, sys, subprocess
p = 'tools/models/x32/manifest.json'
m = json.load(open(p, encoding='utf-8'))
for i in sys.argv[1:]:
    m[i]['status'] = 'done'
orig = subprocess.run(['git', 'show', 'HEAD:' + p], capture_output=True).stdout.decode('utf-8')
open(p, 'w', encoding='utf-8', newline='\n').write(json.dumps(m, ensure_ascii=False, indent=1) + ('\n' if orig.endswith('\n') else ''))
PY
python -I - "$name" "$@" <<'PY'
import sys
name, ids = sys.argv[1], sys.argv[2:]
s = open('tools/shots/gear_moon.txt', encoding='utf-8').read()
head = s[:s.find('hud on', s.find('gearlook'))]
blk = s[s.find('hud on', len(head)):]
one = blk[:blk.find('hud on', 10)]
open(f'tools/shots/gear_{name}.txt', 'w', encoding='utf-8').write(
    head.replace('gear_moon', f'gear_{name}') + ''.join(one.replace('moon_light_sword', i) for i in ids))
PY
./gradlew runShots --offline --no-daemon -Pscript=tools/shots/gear_$name.txt -PshotsName=gear_$name > "$S/shots_$name.log" 2>&1 || { echo SHOTS FAILED; tail -20 "$S/shots_$name.log"; exit 1; }
mkdir -p "$S/dc"
for i in "$@"; do python -I tools/gen/pixel/dark_compare.py "$S/dc/$i.png" $i >/dev/null; done
python -I - "$S/dc" "$name" "$@" <<'PY'
import sys, math
from PIL import Image
S, name, ids = sys.argv[1], sys.argv[2], sys.argv[3:]
rows = math.ceil(len(ids) / 4)
W = Image.new('RGB', (420 * 4, 320 * rows), (16, 16, 22))
for k, i in enumerate(ids):
    W.paste(Image.open(f'{S}/{i}.png').convert('RGB').crop((0, 0, 840, 640)).resize((420, 320)), ((k % 4) * 420, (k // 4) * 320))
W.save(f'docs/review/{name}_bb_sheet.png')
D = f'run/shots/gear_{name}/shots/gear_{name}'
W = Image.new('RGB', (2560, 360 * rows), (16, 16, 22))
for k, i in enumerate(ids):
    W.paste(Image.open(f'{D}/close_{i}.png').convert('RGB').resize((640, 360)), ((k % 4) * 640, (k // 4) * 360))
W.save(f'docs/review/{name}_ingame.png')
print('sheets ok')
PY
