# -*- coding: utf-8 -*-
"""弓: Blockbench で組んだ基の弓（まっすぐの弦 string_0）を取り込み、引きの 3 段（pull0/1/2）は弦だけを差し替えて作る。
弦の段の箱と UV は設計ファイルの build（<id>.json の string_p0_* など）にある。

  python -I tools/models/x32/look5/install_bow.py <書き出した.bbmodel> <id> <作業フォルダ>
"""
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE))
from lib import variant_bbmodel  # noqa: E402

src, item, work = sys.argv[1], sys.argv[2], Path(sys.argv[3])
subprocess.run([sys.executable, '-I', str(HERE / 'install.py'), src, item], check=True, cwd=ROOT)
cubes = json.loads((work / f'{item}.json').read_text(encoding='utf-8'))
for k in ('0', '1', '2'):
    parts = [c for c in cubes if c['name'].startswith(f'string_p{k}_')]
    dst = ROOT / 'tools/models/x32' / f'{item}_pull{k}.bbmodel'
    variant_bbmodel(ROOT / 'tools/models/x32' / f'{item}.bbmodel', dst, f'{item}_pull{k}', parts, 'string_')
    subprocess.run([sys.executable, '-I', str(ROOT / 'tools/gen/pixel/bb_import.py'), str(dst)], check=True, cwd=ROOT)
