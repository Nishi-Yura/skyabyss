# -*- coding: utf-8 -*-
"""防具の .bbmodel (tools/models/x32/armor/) を、プレイヤーの人形に着せて 4 方向から描く (下見・見比べ用)。

座標は Blockbench (x32) のまま。人形はプレイヤーの箱 (頭 16、胴 16x24x8、腕 8、脚 8) で、顔を描いて隠れていないか見る。
面の向きで明暗を付ける (ゲームの光に近い)。

  python -I tools/gen/pixel/armor_view.py <出力.png> <a.bbmodel> [<b.bbmodel> ...] [--labels A,B,C] [--pose walk]
"""
import base64
import io
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bb_armor_import import face_corners, pick_texture, rot_point, uv_rect_corners  # noqa: E402

BG = (24, 28, 36)
SKIN = (196, 150, 118)
SHIRT = (70, 120, 150)
PANTS = (60, 60, 110)
HAIR = (70, 46, 30)

# 骨の pivot (Blockbench x32。x は geo の逆)
PIV = {"armorHead": (0, 48, 0), "armorBody": (0, 48, 0), "armorRightArm": (10, 44, 0), "armorLeftArm": (-10, 44, 0),
       "armorRightLeg": (3.8, 24, 0), "armorLeftLeg": (-3.8, 24, 0), "armorRightBoot": (3.8, 24, 0), "armorLeftBoot": (-3.8, 24, 0)}


def mannequin():
    """(骨, from, to, 色, 顔か)"""
    return [
        ("armorHead", (-8, 48, -8), (8, 64, 8), SKIN, True),
        ("armorBody", (-8, 24, -4), (8, 48, 4), SHIRT, False),
        ("armorRightArm", (8, 24, -4), (16, 48, 4), SKIN, False),
        ("armorLeftArm", (-16, 24, -4), (-8, 48, 4), SKIN, False),
        ("armorRightLeg", (0, 0, -4), (8, 24, 4), PANTS, False),
        ("armorLeftLeg", (-8, 0, -4), (0, 24, 4), PANTS, False),
    ]


def face_pixels_head(face, w, h):
    """頭の箱の前面に目と口と髪を描いた小さな絵"""
    img = Image.new("RGB", (8, 8), SKIN)
    d = ImageDraw.Draw(img)
    if face == "north":
        d.rectangle([0, 0, 7, 1], fill=HAIR)
        d.point([(1, 4), (2, 4)], fill=(255, 255, 255))
        d.point([(5, 4), (6, 4)], fill=(255, 255, 255))
        d.point([(2, 4), (5, 4)], fill=(60, 50, 120))
        d.rectangle([3, 6, 4, 6], fill=(110, 60, 50))
    elif face == "up":
        d.rectangle([0, 0, 7, 7], fill=HAIR)
    else:
        d.rectangle([0, 0, 7, 2], fill=HAIR)
    return img


LIGHT = {"up": 1.0, "north": 0.82, "south": 0.82, "east": 0.68, "west": 0.68, "down": 0.5}


def load_bb(path):
    bb = json.loads(Path(path).read_text(encoding="utf-8"))
    tex = pick_texture(bb)
    img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
    uw, uh = tex.get("uv_width", img.width), tex.get("uv_height", img.height)
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    chains = {}

    def walk(nodes, chain, bone):
        for n in nodes:
            if isinstance(n, str):
                chains[n] = (bone, chain)
            else:
                g = groups.get(n["uuid"], n)
                walk(n.get("children", []), chain + [g], bone or g.get("name"))

    walk(bb["outliner"], [], None)
    quads = []  # (骨, 4 点, 4 uv(画素), 面, 光る)
    for e in bb["elements"]:
        if e.get("type", "cube") != "cube" or e.get("visibility") is False:
            continue
        bone, chain = chains.get(e["uuid"], (None, []))
        infl = e.get("inflate", 0)
        frm = [e["from"][i] - infl for i in range(3)]
        to = [e["to"][i] + infl for i in range(3)]
        for face, fd in e["faces"].items():
            if fd.get("texture") is None or "uv" not in fd:
                continue
            pts = face_corners(frm, to, face)
            if any(abs(a) > 1e-6 for a in e.get("rotation", [0, 0, 0])):
                pts = [rot_point(p, e["rotation"], e.get("origin", [0, 0, 0])) for p in pts]
            for g in reversed(chain):
                if any(abs(a) > 1e-6 for a in g.get("rotation", [0, 0, 0])):
                    pts = [rot_point(p, g["rotation"], g.get("origin", [0, 0, 0])) for p in pts]
            uvc = [(u * img.width / uw, v * img.height / uh) for u, v in uv_rect_corners(fd["uv"], fd.get("rotation", 0))]
            quads.append((bone, pts, uvc, face, e.get("name", "").endswith("_glow")))
    return quads, np.asarray(img).astype(np.float32)


POSES = {
    "stand": {},
    "walk": {"armorRightArm": (-30, 0, 0), "armorLeftArm": (30, 0, 0), "armorRightLeg": (30, 0, 0), "armorLeftLeg": (-30, 0, 0),
             "armorRightBoot": (30, 0, 0), "armorLeftBoot": (-30, 0, 0)},
    "raise": {"armorRightArm": (0, 0, 80), "armorLeftArm": (0, 0, -80)},
}


def pose_pt(p, bone, pose):
    r = POSES[pose].get(bone)
    return rot_point(p, r, PIV[bone]) if r else p


def render(quads, atlas, yaw, size=360, scale=3.9, pose="stand", cy=40):
    W = H = size
    col = np.zeros((H, W, 3), np.float32)
    col[:] = BG
    zb = np.full((H, W), 1e9, np.float32)
    cs, sn = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    # 見る向き: yaw=0 は前 (-Z 側) から。カメラは -Z に立って +Z を見る。画面の右 = -X
    pitch = math.radians(8)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def proj(p):
        x, y, z = p
        x, z = x * cs - z * sn, x * sn + z * cs
        y2, z2 = y * cp - z * sp, y * sp + z * cp
        y, z = y2, z2
        return (W / 2 - x * scale, H / 2 - (y - cy) * scale, z)

    def raster(P, uvs, tex, shade, glow):
        A = np.array([proj(p) for p in P])
        xs, ys = A[:, 0], A[:, 1]
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
        if x0 > x1 or y0 > y1:
            return
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = A[list(tri)]
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            l1 = ((b[1] - c[1]) * (gx - c[0]) + (c[0] - b[0]) * (gy - c[1])) / den
            l2 = ((c[1] - a[1]) * (gx - c[0]) + (a[0] - c[0]) * (gy - c[1])) / den
            l3 = 1 - l1 - l2
            m = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4)
            if not m.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            uv = [uvs[i] for i in tri]
            u = l1 * uv[0][0] + l2 * uv[1][0] + l3 * uv[2][0]
            v = l1 * uv[0][1] + l2 * uv[1][1] + l3 * uv[2][1]
            th, tw = tex.shape[:2]
            ui = np.clip(u.astype(int), 0, tw - 1)
            vi = np.clip(v.astype(int), 0, th - 1)
            px = tex[vi, ui]
            alpha = px[..., 3] > 8 if px.shape[-1] == 4 else np.ones(m.shape, bool)
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            mm = m & alpha & (z < sub)
            if not mm.any():
                continue
            sub[mm] = z[mm]
            k = 1.0 if glow else shade
            col[y0:y1 + 1, x0:x1 + 1][mm] = px[..., :3][mm] * k

    # 面の明るさ: 回したあとの法線で決める
    def shade_of(P):
        p = np.array(P)
        n = np.cross(p[1] - p[0], p[2] - p[0])
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            return 0.8
        n = n / ln
        return 0.5 + 0.5 * max(0.0, n[1]) + 0.32 * abs(n[0]) * 0 + 0.32 * (1 - abs(n[1]))

    for bone, f, t, c, face in mannequin():
        for fn in ("north", "south", "east", "west", "up", "down"):
            P = [pose_pt(p, bone, pose) for p in face_corners(f, t, fn)]
            if face:
                tex = np.asarray(face_pixels_head(fn, 8, 8).convert("RGBA")).astype(np.float32)
            else:
                tex = np.zeros((1, 1, 4), np.float32)
                tex[0, 0] = (*c, 255)
            th, tw = tex.shape[:2]
            uvs = [(0, 0), (0, th), (tw, th), (tw, 0)]
            raster(P, uvs, tex, LIGHT[fn] * 0.8, False)
    for bone, P, uvc, face, glow in quads:
        P = [pose_pt(p, bone, pose) if bone in PIV else p for p in P]
        raster(P, uvc, atlas, shade_of(P), glow)
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8))


def main():
    args = sys.argv[1:]
    labels, pose = None, "stand"
    if "--labels" in args:
        i = args.index("--labels")
        labels = args[i + 1].split(",")
        del args[i:i + 2]
    if "--pose" in args:
        i = args.index("--pose")
        pose = args[i + 1]
        del args[i:i + 2]
    out, paths = args[0], args[1:]
    views = [(0, "前"), (35, "3/4"), (90, "横"), (180, "後ろ")]
    S = 360
    sheet = Image.new("RGB", (S * len(views), (S + 28) * len(paths)), BG)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 20)
    except OSError:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(sheet)
    for r, p in enumerate(paths):
        quads, atlas = load_bb(p)
        name = labels[r] if labels else Path(p).stem
        d.text((10, r * (S + 28) + 3), name, fill=(230, 230, 230), font=font)
        for c, (yaw, vn) in enumerate(views):
            im = render(quads, atlas, yaw, S, pose=pose)
            sheet.paste(im, (c * S, r * (S + 28) + 28))
            if r == 0:
                d.text((c * S + S - 60, 3), vn, fill=(160, 170, 190), font=font)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
