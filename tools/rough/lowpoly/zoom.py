"""確認用: 部分のアップ。python zoom.py out.png"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bone_sentinel_compare as C  # noqa
from bone_sentinel_compare import Image  # noqa


def main():
    r = C.main()
    big, bg_glow, yoff = C.combined_atlas(r)
    sz = (460, 500)
    panels = [((190, 60), (0, 31.0, 0), 44), ((200, 12), (0, 12.0, -1), 36), ((150, 10), (0, 18.0, 1), 34)]
    img = Image.new("RGB", (sum(sz[0] + 10 for _ in panels), 500))
    x = 0
    for (yaw, pitch), cen, sc in panels:
        bg = C.panel_bg(*sz)
        im = C.render_pose(r, big, bg_glow, yoff, {}, yaw, pitch, sz, cen, sc)
        bg.paste(im, (0, 0), im)
        img.paste(bg, (x, 0))
        x += sz[0] + 10
    img.save(sys.argv[1])


if __name__ == "__main__":
    main()
