# -*- coding: utf-8 -*-
"""Blockbench から書き出した .bbmodel を item id の名前にして tools/models/x32/<id>.bbmodel に置き、bb_import で取り込む。

  python -I tools/models/x32/look5/install.py <書き出した.bbmodel> <id>
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
src, item = sys.argv[1], sys.argv[2]
bb = json.loads(Path(src).read_text(encoding='utf-8'))
bb['name'] = item
dst = ROOT / 'tools/models/x32' / f'{item}.bbmodel'
dst.write_text(json.dumps(bb, ensure_ascii=False), encoding='utf-8')
subprocess.run([sys.executable, '-I', str(ROOT / 'tools/gen/pixel/bb_import.py'), str(dst)], check=True, cwd=ROOT)
