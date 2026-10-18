"""確認用: python quick.py out.png a [b c]"""
import sys
import importlib
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from scut import *  # noqa


def main():
    out = sys.argv[1]
    keys = sys.argv[2:] or ["a"]
    views = [(212, 20, 1.0), (32, 20, 1.0), (180, 78, 1.0), (270, 8, 1.0)]
    W, H = 440, 330
    img = Image.new("RGB", (W * 4, H * len(keys)), (26, 26, 34))
    for r, k in enumerate(keys):
        m = importlib.import_module("rs_" + k)
        res = build_option(k, m.build, m.PAL, getattr(m, "HINT", 128))
        print(k, "tris", res["data"]["triangle_count"], {b: v["triangle_count"] for b, v in res["data"]["bones"].items()},
              "atlas", res["size"], "glow", int(res["glow"].sum()))
        for i, (yaw, pitch, _) in enumerate(views):
            im = render(res["faces"], res["atlas"], res["glow"], yaw, pitch, (W, H), m.VIEW["cen"], m.VIEW["sc"])
            img.paste(im, (i * W, r * H), im)
    img.save(out)


main()
