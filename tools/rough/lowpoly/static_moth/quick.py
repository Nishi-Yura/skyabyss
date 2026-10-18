"""確認用: python quick.py out.png [a b c]"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from sm_common import *  # noqa
import importlib


def main():
    out = sys.argv[1]
    keys = sys.argv[2:] or list("abc")
    views = [(212, 22, 0, (0, 6.5, 0), 20), (180, 6, 0, (0, 6.5, 0), 20), (180, 80, 0, (0, 6.5, 0), 20),
             (212, 14, 50, (0, 7.5, 0), 20), (192, 6, 0, HC_, 46)]
    ims = []
    for k in keys:
        mod = importlib.import_module("sm_" + k)
        r = build_option(k, mod, mod.HINT)
        print(k, r["data"]["triangle_count"], r["size"], {b: v["triangle_count"] for b, v in r["data"]["bones"].items()})
        for yaw, pitch, flap, cen, sc in views:
            if sc == 46:
                cen = mod.HEAD_CENTER
            ims.append(render_pose(r, yaw, pitch, (420, 300), cen, sc, flap))
    n = len(views)
    w = Image.new("RGB", (420 * n, 300 * len(keys)), (26, 26, 34))
    for i, im in enumerate(ims):
        w.paste(im, ((i % n) * 420, (i // n) * 300), im)
    w.save(out)


HC_ = (0, 6.5, -4.5)
main()
