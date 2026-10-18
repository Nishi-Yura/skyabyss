"""quick preview: python quick.py old|a|b|c  -> scratch png"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import *  # noqa
from PIL import Image  # noqa

SCR = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("quick.png")


def main():
    key = sys.argv[1]
    views = [(212, 13), (32, 13), (150, 6), (270, 6)]
    W, H = 300, 560
    sheet = Image.new("RGB", (W * len(views) + 300, H * 2), (20, 20, 28))
    if key == "old":
        from old import old_faces
        of, n, g = old_faces()
        print("boxes", n, g)
        for i, (yaw, pit) in enumerate(views):
            im = voxel.render(of, yaw=yaw, pitch=pit, size=(W, H), margin=0.04)
            sheet.paste(im, (i * W, 0))
        of, n, g = old_faces(DIVE)
        for i, (yaw, pit) in enumerate(views):
            im = voxel.render(of, yaw=yaw, pitch=pit, size=(W, H), margin=0.04)
            sheet.paste(im, (i * W, H))
        sheet.save(SCR)
        return
    import importlib
    mod = importlib.import_module("opt_" + key)
    r = build_option(key, mod.build, mod.PAL, getattr(mod, "GEAR_PAL", None), body_hint=getattr(mod, "HINT", 128),
                     write=False)
    print(key, "tris", r["tris"], "spear", r["gtris"], "atlas", r["size"], r["gsize"])
    big, gl, yoff = combined_atlas(r)
    for row, pose in enumerate(({}, DIVE)):
        for i, (yaw, pit) in enumerate(views):
            bgp = Image.fromarray(np.clip(voxel._background(W, H, "dark"), 0, 255).astype(np.uint8))
            im = render_pose(r, big, gl, yoff, pose, yaw, pit, (W, H), (0, 24.0, -1), 10.5)
            bgp.paste(im, (0, 0), im)
            sheet.paste(bgp, (i * W, row * H))
    at = Image.fromarray(r["atlas"], "RGBA")
    bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
    bgc.alpha_composite(at)
    sheet.paste(bgc.convert("RGB").resize((300, 300), Image.NEAREST), (W * len(views), 0))
    sheet.save(SCR)


main()
