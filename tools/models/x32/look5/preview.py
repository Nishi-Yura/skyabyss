# -*- coding: utf-8 -*-
"""設計の下見: 設計ファイルの品を作り、仮の .bbmodel を tools/models/x32 に置いて取り込み、暗い背景で並べる。
Blockbench で組む前に形と色を詰めるためのもの（最後は必ず Blockbench で組んだ版で上書きする）。

  python -I tools/models/x32/look5/preview.py <作業フォルダ> <出力.png> <id> [id ...]
"""
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE))
import whale_bold  # noqa: E402
import tide_bold  # noqa: E402
MODS = [whale_bold, tide_bold]
from lib import write_bbmodel  # noqa: E402

work, out, ids = Path(sys.argv[1]), sys.argv[2], sys.argv[3:]
for i in ids:
    next(getattr(md, i) for md in MODS if hasattr(md, i))().build(work)
    cubes = json.loads((work / f'{i}.json').read_text(encoding='utf-8'))
    only = (lambda n: not n.startswith('string_') or n == 'string_0') if 'bow' in i else None
    dst = ROOT / 'tools/models/x32' / f'{i}.bbmodel'
    write_bbmodel(cubes, work / f'{i}_base.png', dst, i, only)
    subprocess.run([sys.executable, '-I', str(ROOT / 'tools/gen/pixel/bb_import.py'), str(dst)], check=True, cwd=ROOT)
subprocess.run([sys.executable, '-I', str(ROOT / 'tools/gen/pixel/dark_compare.py'), out] + ids, check=True, cwd=ROOT)
from PIL import Image  # noqa: E402
im = Image.open(out)
vw = 450
n = len(ids)
crops = [im.crop((vw * 2 * k, 40, vw * 2 * k + vw * 2, 620)) for k in range(n)]
cols = 4
rows = (n + cols - 1) // cols
sheet = Image.new('RGB', (cols * vw * 2, rows * 580), (16, 16, 22))
for k, c in enumerate(crops):
    sheet.paste(c, ((k % cols) * vw * 2, (k // cols) * 580))
sheet = sheet.resize((sheet.width // 2, sheet.height // 2))
sheet.save(out.replace('.png', '_sheet.png'))
print('sheet', sheet.size)
