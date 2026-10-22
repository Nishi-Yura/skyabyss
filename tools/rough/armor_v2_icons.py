"""32x32 item icons drawn from the 3D armor pieces (the rule: the item picture and the 3D model say the same thing).

python tools/rough/armor_v2_icons.py [names] [--out dir]    -> a preview sheet + the 16 icons in <out>
install(names) is called by tools/install/install_armor_v2.py and writes textures/item/<set>_{helmet,chestplate,leggings,boots}.png

Method: render only the bones of one slot from a 3/4 view (the game's face shading, glow included), cut the
silhouette, area-average it into a 28 px box, snap to a small palette, add a 1 px dark outline.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, "tools/rough")
import armor_kit as ak  # noqa: E402
import armor_v2_sets as S  # noqa: E402
import voxel  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/skyabyss"
SLOTS = ("helmet", "chestplate", "leggings", "boots")
VIEW = {"helmet": (200, 14), "chestplate": (188, 8), "leggings": (184, 4), "boots": (192, 12)}
OUTLINE = (22, 20, 30)
BG = 200


def piece_geo(geo, slot):
    import copy
    g = copy.deepcopy(geo)
    keep = set(ak.PIECE_BONES[slot])
    for b in g["minecraft:geometry"][0]["bones"]:
        if b["name"] not in keep:
            b["cubes"] = []
    return g


def render_piece(geo, tex, glow, slot, big=512):
    if slot in ("leggings", "boots"):
        # one leg (3/4 view), its mirror image beside it: reads as a pair of legs / boots, not as one slab
        import copy
        g = piece_geo(geo, slot)
        for b in g["minecraft:geometry"][0]["bones"]:
            if b["name"].startswith("armorLeft"):
                b["cubes"] = []
        faces = ak.faces_of(g, tex, glow)
        yaw, pitch = (190, 2) if slot == "leggings" else (200, 12)
        im = voxel.render(faces, yaw=-yaw, pitch=pitch, size=(big, big), bg="light", margin=0.02, bloom=False, ss=1)
        a1 = np.array(im.transpose(Image.FLIP_LEFT_RIGHT)).astype(float)
        m1 = np.abs(a1 - BG).max(-1) > 2
        ys, xs = np.where(m1)
        a1, m1 = a1[ys.min():ys.max() + 1, xs.min():xs.max() + 1], m1[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        a2, m2 = a1[:, ::-1], m1[:, ::-1]
        gap = int(a1.shape[1] * (0.04 if slot == "leggings" else 0.10))
        W = a1.shape[1] * 2 + gap
        rgb = np.full((a1.shape[0], W, 3), BG, float)
        alpha = np.zeros((a1.shape[0], W), bool)
        rgb[:, :a1.shape[1]][m1] = a1[m1]
        alpha[:, :a1.shape[1]] |= m1
        x0 = a1.shape[1] + gap
        rgb[:, x0:][m2] = a2[m2]
        alpha[:, x0:] |= m2
        return rgb, alpha
    g = piece_geo(geo, slot)
    faces = ak.faces_of(g, tex, glow)
    yaw, pitch = VIEW[slot]
    im = voxel.render(faces, yaw=-yaw, pitch=pitch, size=(big, big), bg="light", margin=0.02, bloom=False, ss=1)
    im = im.transpose(Image.FLIP_LEFT_RIGHT)
    a = np.array(im).astype(float)
    alpha = (np.abs(a - BG).max(-1) > 2)
    return a, alpha


def to_icon(rgb, alpha, size=32, box=28, colors=22):
    ys, xs = np.where(alpha)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    rgb, alpha = rgb[y0:y1, x0:x1], alpha[y0:y1, x0:x1]
    h, w = alpha.shape
    sc = box / max(h, w)
    nw, nh = max(1, round(w * sc)), max(1, round(h * sc))
    # alpha-weighted area averaging
    A = alpha.astype(float)
    num = Image.fromarray((rgb * A[..., None]).astype(np.float32), "RGB") if False else None  # (kept simple below)
    prem = np.dstack([rgb * A[..., None], A])
    chans = [np.array(Image.fromarray(prem[..., i].astype(np.float32), "F").resize((nw, nh), Image.BOX)) for i in range(4)]
    prem2 = np.dstack(chans)
    a2 = prem2[..., 3]
    col = prem2[..., :3] / np.maximum(a2[..., None], 1e-6)
    mask = a2 > 0.5
    # contrast / saturation lift so that small plates read
    lum = col.mean(-1, keepdims=True)
    col = np.clip(lum + (col - lum) * 1.18, 0, 255)
    col = np.clip((col - 128) * 1.12 + 128 + 6, 0, 255)
    img = Image.fromarray(col.astype(np.uint8), "RGB")
    q = img.quantize(colors=colors, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
    col = np.array(q)
    out = np.zeros((size, size, 4), np.uint8)
    oy, ox = (size - nh) // 2, (size - nw) // 2
    sub = out[oy:oy + nh, ox:ox + nw]
    sub[mask, :3] = col[mask]
    sub[mask, 3] = 255
    # outline
    solid = out[..., 3] > 0
    ring = np.zeros_like(solid)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        ring |= np.roll(np.roll(solid, dy, 0), dx, 1)
    ring &= ~solid
    out[ring, :3] = OUTLINE
    out[ring, 3] = 255
    return out


def make(name):
    a = S.SETS[name]()
    geo, tex, glow = a.build(chamfer=S.CHAMFER)
    res = {}
    for slot in SLOTS:
        rgb, alpha = render_piece(geo, tex, glow, slot)
        res[slot] = to_icon(rgb, alpha)
    return res


def install(names=None):
    for n in names or S.SETS:
        for slot, ic in make(n).items():
            Image.fromarray(ic).save(RES / "textures/item" / f"{n}_{slot}.png")
            print("icon", n, slot)


def preview(names, out):
    W = Image.new("RGBA", (len(SLOTS) * 144, len(names) * 144), (44, 44, 58, 255))
    for i, n in enumerate(names):
        for j, (slot, ic) in enumerate(make(n).items()):
            im = Image.fromarray(ic).resize((128, 128), Image.NEAREST)
            W.paste(im, (j * 144 + 8, i * 144 + 8), im)
    W.save(out)


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*", default=list(S.SETS))
    ap.add_argument("--out", default="tools/rough/_out_icons.png")
    ar = ap.parse_args()
    preview(ar.names, ar.out)
