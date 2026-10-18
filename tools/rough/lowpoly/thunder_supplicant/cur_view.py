"""今のモデルを描いて確認する (開発用)。python cur_view.py out.png"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from old import *  # noqa
from PIL import Image  # noqa

if __name__ == "__main__":
    f, n, m = old_faces()
    print(n, m)
    ims = [voxel.render(f, yaw=y, pitch=14, size=(480, 520), margin=0.03) for y in (205, 25, 270)]
    W = Image.new("RGB", (1440, 520))
    for i, im in enumerate(ims):
        W.paste(im, (i * 480, 0))
    W.save(sys.argv[1])
