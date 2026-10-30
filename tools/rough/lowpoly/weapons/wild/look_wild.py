"""見直し用の一枚: 品を大きめに 2 方向 (前 3/4 と後ろ 3/4) で並べる (形の確認だけ。強化なし)。
実行: python -I tools/rough/lowpoly/weapons/wild/look_wild.py <o_wild1 ...> <out.png> [武器 id ...]
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
mod, out = sys.argv[1], sys.argv[2]
want = sys.argv[3:]
sys.argv = [sys.argv[0], mod, "--quick"]
import build_wild as O  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

B = O.B
ids = want or list(O.M.WEAPONS)
TW, TH = 300, 420
cols = min(4, len(ids))
S = Image.new("RGB", (TW * 2 * cols, (TH + 30) * ((len(ids) + cols - 1) // cols)), B.PLAT)
dr = ImageDraw.Draw(S)
for n, wid in enumerate(ids):
    w = O.M.WEAPONS[wid]
    r = B.make(wid, "a", 0)
    x, y = (n % cols) * TW * 2, (n // cols) * (TH + 30)
    for j, (yaw, pit) in enumerate(((155, -12), (25, -12))):
        S.paste(B.tile(r, w, yaw, pit, (TW, TH), w["scale"] * 1.32, w["cen"]), (x + j * TW, y))
    B.label(dr, (x + 8, y + TH + 6), f"{w['jp']}  {wid}  {r['tris']} 面", 15)
S.save(out)
print("saved", out)
