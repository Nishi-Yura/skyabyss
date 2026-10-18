"""確認用 (仮の色): python quick.py out.png [a|b|c]  — 形だけを見る"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import numpy as np
from PIL import Image
import lpkit
import model as MD
import posing as PO

COL = {"plate": (90, 96, 110), "riser": (60, 64, 76), "floor": (255, 120, 30), "belly": (255, 160, 60),
       "head": (110, 116, 130), "eye": (255, 220, 90), "leg": (70, 70, 80), "ant": (150, 150, 160),
       "tail": (80, 84, 96), "wing": (70, 76, 90), "seam": (255, 120, 30), "spike": (150, 100, 90), "fin": (180, 180, 190)}


def stub(faces):
    size, P, N, M, L = lpkit.build_atlas(faces, 128, 0)
    img = np.zeros((size, size, 4), np.uint8)
    mats = np.array([f.mat for f in faces])
    own = M >= 0
    for m, c in COL.items():
        sel = own & (mats[np.where(own, M, 0)] == m)
        img[sel, :3] = c
    img[own, 3] = 255
    glow = np.zeros((size, size), bool)
    sel = own & np.isin(mats[np.where(own, M, 0)], ["floor", "eye", "belly", "seam"])
    glow[sel] = True
    return img, glow


def main():
    out, styles = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "abc"
    ims = []
    for s in styles:
        F, _ = MD.build(s)
        print(s, "tris", lpkit.tri_count(F))
        atlas, glow = stub(F)
        poses = [("rest", {}), ("curled", PO.anim_state("curled", 0.0))]
        for nm, st in poses:
            pf = PO.pose_faces(F, st)
            cen = (0, 6, 0)
            for yaw, pitch in ((215, 20), (270, 8), (180, 60)):
                ims.append(lpkit.render(pf, atlas, glow, yaw, pitch, (420, 300), cen, 17))
    cols = 6
    rows = (len(ims) + cols - 1) // cols
    w = Image.new("RGB", (420 * cols, 300 * rows), (26, 26, 34))
    for i, im in enumerate(ims):
        w.paste(im, ((i % cols) * 420, (i // cols) * 300), im)
    w.save(out)


main()
