"""現行の防具 (案 C 骨組み、レア) 4 組を正面・横・背中で 1 枚に (gear_audit の目視用)。
実行: python tools/rough/lowpoly/gear_style/audit_armor.py <out.png>"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
A = HERE.parent / "armor"
sys.path.insert(0, str(A)); sys.path.insert(0, str(HERE.parent / "weapons"))
import aview as BA
import style_c
from PIL import Image

rows = []
for T in ("whale", "tide", "thunder", "ember"):
    res = BA.build_set(style_c.build(T, "rare"), T, "rare")
    ims = [BA.render(res, T, yaw, -6, (300, 420), 9.4) for yaw in (180, 90, 0, 135)]
    rows.append(ims)
    print(T, BA.set_tris(res), flush=True)
S = Image.new("RGB", (1200, 420 * 4))
for r, ims in enumerate(rows):
    for c, im in enumerate(ims):
        S.paste(im, (c * 300, r * 420))
S.save(sys.argv[1])
