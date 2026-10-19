#!/usr/bin/env python3
"""関節をアニメの最大回転まで曲げた「直す前 / 直した後」の接写の比べ表を作る (docs/review/previews/watertight_sheet.png)。

メッシュ JSON (と atlas) を、骨の親子の回転・pivot のとおりに曲げて、ソフトウェアの正射影で描く (ゲームと同じ座標・同じ向き計算)。
裏面 (中) が見えている画素は紫で塗る。直す前 = git の参照 (既定 m1-sky-pass) のメッシュ、直した後 = 作業ツリーのメッシュ。

  python tools/watertight_sheet.py [--before-ref m1-sky-pass] [--out docs/review/previews/watertight_sheet.png]
"""
from __future__ import annotations

import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as ma  # noqa: E402

ROOT = ma.ROOT
TEXDIR = os.path.join(ma.ASSETS, "textures", "lowpoly")

# (メッシュ名, 曲げる骨, 回転 (度), 注目点の骨, 見る向き yaw, pitch, 見出し)
CASES = [
    ("rib_archer", "arm_l", (-70, 0, 35), "arm_l", 200, 10, "骸骨の射手: 肩を上げたとき"),
    ("feather_warden", "arm_r", (-80, 0, -40), "arm_r", 160, 10, "羽の番人: 腕を振ったとき"),
    ("ash_smith", "arm_r", (-110, 0, 20), "arm_r", 150, 12, "灰の鍛え手: 槌を振り下ろす肩"),
    ("raiju", "leg_fl", (40, 0, 0), "leg_fl", 120, 12, "雷獣: 前脚の付け根"),
    ("whalebone_captain", "armR", (-70, 0, -30), "armR", 160, 10, "鯨骨の船長: 肩"),
    ("thunder_wing", "wing_l", (0, 0, 55), "wing_l", 140, 15, "雷翼 (巨神): 翼の付け根", 0.2),
    ("stone_whale", "jaw", (28, 0, 0), "jaw", 30, -10, "岩鯨 (巨神): 顎を開いたとき", 0.2),
    ("tide_beckoner", "claw_big", (-50, 40, -30), "claw_big", 160, 10, "潮招き: 大きなはさみの付け根"),
]


def git_show(ref, relpath):
    out = subprocess.run(["git", "show", "%s:%s" % (ref, relpath)], cwd=ROOT, capture_output=True)
    if out.returncode != 0:
        return None
    return json.loads(out.stdout.decode("utf-8"))


def load_atlas(data, name):
    tex = data.get("texture", name)
    tex = tex.split(":")[-1]
    tex = tex[:-4] if tex.endswith(".png") else tex
    p = os.path.join(TEXDIR, tex + ".png")
    if not os.path.exists(p):
        return None
    return np.array(Image.open(p).convert("RGBA"))


def pose_matrices(data, pose):
    bones = data["bones"]
    mats = {}

    def get(n):
        if n in mats:
            return mats[n]
        b = bones[n]
        pn = b.get("parent")
        pm = get(pn) if pn in bones else np.eye(4)
        piv = np.array(b.get("pivot", [0, 0, 0]), float)
        R = np.eye(4)
        if n in pose:
            R[:3, :3] = ma.euler_matrix(*pose[n])
        T1 = np.eye(4)
        T1[:3, 3] = piv
        T0 = np.eye(4)
        T0[:3, 3] = -piv
        mats[n] = pm @ T1 @ R @ T0
        return mats[n]
    for n in bones:
        get(n)
    return mats


def render(data, atlas, pose, yaw, pitch, center, scale, size=(560, 560), ss=2):
    mats = pose_matrices(data, pose)
    W, H = size[0] * ss, size[1] * ss
    zbuf = np.full((H, W), np.inf)
    img = np.zeros((H, W, 3), float)
    img[:] = (28, 30, 40)
    ya, pa = math.radians(yaw), math.radians(pitch)
    # カメラの基底 (right, up, forward)。-x 反転 (描画空間) を含めて x を鏡に写す
    fwd = np.array([math.sin(ya) * math.cos(pa), -math.sin(pa), math.cos(ya) * math.cos(pa)])
    right = np.cross(np.array([0, 1.0, 0]), fwd)
    right /= np.linalg.norm(right)
    up = np.cross(fwd, right)
    light = np.array([0.35, 0.8, -0.5])
    light /= np.linalg.norm(light)
    ctr = np.array(center, float)
    for n, b in data["bones"].items():
        V = np.array(b.get("vertices", []), float).reshape(-1, 3)
        if not len(V):
            continue
        T = np.array(b["triangles"], int).reshape(-1, 3)
        UV = np.array(b["uvs"], float).reshape(-1, 2)
        Vh = np.c_[V, np.ones(len(V))] @ mats[n].T
        P = Vh[:, :3]
        sx = ((P - ctr) @ right) * scale * ss + W / 2
        sy = -((P - ctr) @ up) * scale * ss + H / 2
        sz = (P - ctr) @ fwd
        for t in T:
            a, bb, c = t
            xs = (sx[a], sx[bb], sx[c])
            ys = (sy[a], sy[bb], sy[c])
            x0, x1 = int(max(0, math.floor(min(xs)))), int(min(W - 1, math.ceil(max(xs))))
            y0, y1 = int(max(0, math.floor(min(ys)))), int(min(H - 1, math.ceil(max(ys))))
            if x1 < x0 or y1 < y0:
                continue
            nrm = np.cross(P[bb] - P[a], P[c] - P[a])
            ln = np.linalg.norm(nrm)
            if ln < 1e-9:
                continue
            nrm /= ln
            front = np.dot(nrm, fwd) < 0
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(den) < 1e-9:
                continue
            l1 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
            l2 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
            l3 = 1 - l1 - l2
            m = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
            if not m.any():
                continue
            z = l1 * sz[a] + l2 * sz[bb] + l3 * sz[c]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            upd = m & (z < sub)
            if not upd.any():
                continue
            if front:
                col = np.array([180.0, 180, 180])
                if atlas is not None:
                    u = l1 * UV[a, 0] + l2 * UV[bb, 0] + l3 * UV[c, 0]
                    v = l1 * UV[a, 1] + l2 * UV[bb, 1] + l3 * UV[c, 1]
                    ix = np.clip((u * atlas.shape[1]).astype(int), 0, atlas.shape[1] - 1)
                    iy = np.clip((v * atlas.shape[0]).astype(int), 0, atlas.shape[0] - 1)
                    tex = atlas[iy, ix][..., :3].astype(float)
                else:
                    tex = np.broadcast_to(col, gx.shape + (3,))
                shade = 0.55 + 0.45 * max(0.0, float(np.dot(-nrm, -light) if False else np.dot(nrm, light)))
                px = tex * shade
            else:
                px = np.broadcast_to(np.array([150.0, 30, 190]), gx.shape + (3,))
            ii = img[y0:y1 + 1, x0:x1 + 1]
            ii[upd] = px[upd]
            sub[upd] = z[upd]
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    return im.resize(size, Image.LANCZOS)


def font(size):
    for p in ("C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def main(argv):
    ref = "m1-sky-pass"
    out = os.path.join(ROOT, "docs", "review", "previews", "watertight_sheet.png")
    if "--before-ref" in argv:
        ref = argv[argv.index("--before-ref") + 1]
    if "--out" in argv:
        out = argv[argv.index("--out") + 1]
    cell = 420
    cols = []
    for case in CASES:
        name, bone, rot, focus, yaw, pitch, title = case[:7]
        zoom = case[7] if len(case) > 7 else None
        rel = "src/main/resources/assets/skyabyss/lowpoly/%s.json" % name
        after = json.load(open(os.path.join(ROOT, rel), encoding="utf-8"))
        before = git_show(ref, rel)
        if before is None or bone not in after["bones"]:
            print("skip", name)
            continue
        piv = np.array(after["bones"][focus]["pivot"], float)
        allV = np.vstack([np.array(b["vertices"]) for b in after["bones"].values() if b.get("vertices")])
        ext = float((allV.max(0) - allV.min(0)).max())
        # 注目する骨の大きさに合わせて寄る
        bv = np.array(after["bones"][bone]["vertices"])
        bsz = float((bv.max(0) - bv.min(0)).max())
        span = ext * zoom if zoom else min(ext, max(bsz * 2.2, ext * 0.18))
        scale = cell / span
        atlas_b, atlas_a = load_atlas(before, name), load_atlas(after, name)
        ims = []
        for data, atlas in ((before, atlas_b), (after, atlas_a)):
            ims.append(render(data, atlas, {bone: rot}, yaw, pitch, piv, scale, (cell, cell)))
        cols.append((title, ims, "%s の %s を %s 度" % (name, bone, tuple(rot))))
        print("rendered", name)
    n = len(cols)
    perrow = 2
    rows = (n + perrow - 1) // perrow
    gap, head = 14, 70
    Wd = perrow * (2 * cell + gap) + gap
    Hd = head + rows * (cell + 54) + gap
    sheet = Image.new("RGB", (Wd, Hd), (20, 22, 30))
    d = ImageDraw.Draw(sheet)
    d.text((gap, 12), "直す前 (左) と直した後 (右): 関節をアニメの最大回転まで曲げた接写", fill=(240, 240, 240), font=font(26))
    d.text((gap, 44), "紫 = 裏面が見えている (中が見えている)。直した後は付け根に玉・襟を足し、開いた縁を塞いだ", fill=(190, 190, 210), font=font(17))
    for i, (title, ims, sub) in enumerate(cols):
        r, c = divmod(i, perrow)
        x0 = gap + c * (2 * cell + gap)
        y0 = head + r * (cell + 54)
        d.text((x0, y0), title + "   " + sub, fill=(235, 235, 235), font=font(16))
        sheet.paste(ims[0], (x0, y0 + 26))
        sheet.paste(ims[1], (x0 + cell, y0 + 26))
        d.text((x0 + 8, y0 + 30), "前", fill=(255, 190, 120), font=font(18))
        d.text((x0 + cell + 8, y0 + 30), "後", fill=(150, 230, 160), font=font(18))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print("wrote", out, sheet.size)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
