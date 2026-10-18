"""確認用: python quick2.py out.png [abc]  — 本番のテクスチャで 休め / 玉"""
import copy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lp import *  # noqa
import posing as PO  # noqa

HIDE = {"antenna_l", "antenna_r", "uropods"}


def hide_limbs(fs):
    return [f for f in fs if not (f.bone in HIDE or f.bone.startswith("leg") or f.bone.startswith("mem"))]


def main():
    out = sys.argv[1]
    styles = sys.argv[2] if len(sys.argv) > 2 else "abc"
    ims = []
    for s in styles:
        r = build_option(s)
        for nm, st, hide in (("rest", {}, False), ("curled", PO.anim_state("curled", 0.0), True)):
            pf = PO.pose_faces(r["faces"], st)
            if hide:
                pf = hide_limbs(pf)
            for yaw, pitch in ((215, 20), (35, 24), (270, 8)):
                ims.append(render(pf, r["atlas"], r["glow"], yaw, pitch, (460, 330), (0, 6, 0), 20))
    cols = 6
    rows = (len(ims) + cols - 1) // cols
    w = Image.new("RGB", (460 * cols, 330 * rows), (26, 26, 34))
    for i, im in enumerate(ims):
        w.paste(im, ((i % cols) * 460, (i // cols) * 330), im)
    w.save(out)
    print("tris", {s: None for s in styles})


main()
