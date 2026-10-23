#!/usr/bin/env python3
"""巨神・心臓の仕上げの比較シート (docs/review/boss_quality_2026-10-23.md)。

  python tools/sheets/boss_quality_sheet.py            ゲームの絵 (run/shots/boss_quality_v2/) を並べる
  python tools/sheets/boss_quality_sheet.py --offline  手で描いた正射影の前/後 (git の BEFORE の版の絵と今の絵)

出力: docs/review/boss_quality_v2/ (ingame_sheet.png、offline_*.png、元の絵の縮小コピー)
"""
import io
import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "docs", "review", "boss_quality_v2")
SHOTS = os.path.join(ROOT, "run", "shots", "boss_quality_v2")
A = "src/main/resources/assets/skyabyss/"
BEFORE = "4c54f1fe"  # 仕上げの前 (m1-sky-pass)


def font(sz):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if os.path.exists(f):
            return ImageFont.truetype(f, sz)
    return ImageFont.load_default()


def grid(items, cols, cell, title, path):
    w, h = cell
    rows = (len(items) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w + (cols + 1) * 8, rows * (h + 30) + 52), (18, 18, 24))
    d = ImageDraw.Draw(sheet)
    d.text((10, 10), title, font=font(22), fill=(236, 232, 218))
    for i, (label, im) in enumerate(items):
        x = 8 + (i % cols) * (w + 8)
        y = 48 + (i // cols) * (h + 30)
        if im is not None:
            im = im.convert("RGB")
            im.thumbnail((w, h))
            sheet.paste(im, (x, y))
        d.text((x + 2, y + h + 4), label, font=font(16), fill=(150, 232, 255))
    sheet.save(path)
    print("wrote", path)


INGAME = [
    ("shell_near", "合わせ貝: 上から (右奥の 4 つは旧い印の文字)"),
    ("shell_far", "合わせ貝: 20 ブロック先から"),
    ("shell_low", "合わせ貝: 床の高さから"),
    ("tide_windup", "潮殻の心臓: 真珠の当たりの 3 tick 前 (溜めの震え)"),
    ("tide_stagger", "潮殻の心臓: ひるみ (領域が砕けた瞬間)"),
    ("storm_windup", "雷翼の心臓: 刃の当たりの 3 tick 前"),
    ("storm_stagger", "雷翼の心臓: ひるみ"),
    ("ember_windup", "余燼の心臓: 脈の当たりの 3 tick 前"),
    ("ember_stagger", "余燼の心臓: ひるみ"),
    ("whale_windup", "岩鯨の心臓: 突きの当たりの 3 tick 前"),
    ("whale_stagger", "岩鯨の心臓: ひるみ"),
    ("w_lit_idle", "心臓の番人: 光の中 (目・筋の網・肋の淡い光)"),
    ("w_lit_claw_wind", "心臓の番人: 爪の溜め (予告の 15 tick 目)"),
    ("w_lit_claw_hit", "心臓の番人: 爪の当たり (予告の 18 tick 目)"),
    ("w_lit_hurt", "心臓の番人: 被弾"),
]


def ingame():
    os.makedirs(OUT, exist_ok=True)
    items = []
    for name, label in INGAME:
        p = os.path.join(SHOTS, name + ".png")
        im = Image.open(p) if os.path.exists(p) else None
        if im is not None:
            small = im.convert("RGB")
            small.thumbnail((960, 540))
            small.save(os.path.join(OUT, name + ".jpg"), quality=86)
        items.append((label, im))
    grid(items, 3, (420, 236), "巨神・心臓の仕上げ (2026-10-23)  ゲームの絵 (音なし・日本語)", os.path.join(OUT, "ingame_sheet.png"))


# ---------------------------------------------------------------- 正射影の前/後
def git_bytes(rev, path):
    try:
        return subprocess.check_output(["git", "show", "%s:%s" % (rev, path)], cwd=ROOT, stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError:
        return None


def load_mesh(name, rev=None):
    raw = git_bytes(rev, A + "lowpoly/%s.json" % name) if rev else open(os.path.join(ROOT, A, "lowpoly", name + ".json"), "rb").read()
    m = json.loads(raw)
    tex = git_bytes(rev, A + "textures/lowpoly/%s.png" % name) if rev else open(os.path.join(ROOT, A, "textures/lowpoly", name + ".png"), "rb").read()
    at = np.array(Image.open(io.BytesIO(tex)).convert("RGBA"))
    gp = A + "textures/lowpoly/%s_glowmask.png" % name
    graw = git_bytes(rev, gp) if rev else (open(os.path.join(ROOT, gp), "rb").read() if os.path.exists(os.path.join(ROOT, gp)) else None)
    gl = np.array(Image.open(io.BytesIO(graw)).convert("L")) > 0 if graw else np.zeros(at.shape[:2], bool)
    P, U = [], []
    for b in m["bones"].values():
        V = np.array(b["vertices"], float)
        UV = np.array(b["uvs"], float)
        if len(UV) == 0:
            continue
        if UV.max() > 1.001:
            UV = UV / np.array(m["texture_size"], float)
        for t in b["triangles"]:
            P.append(V[t])
            U.append(UV[t])
    P = np.array(P)
    P[:, :, 0] *= -1
    return P, np.array(U), at, gl


def render(data, yaw, pitch, W=320, H=360):
    P, U, at, gl = data
    y, p = math.radians(yaw), math.radians(pitch)
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    Q = P @ (Rx @ Ry).T
    mn, mx = Q.reshape(-1, 3).min(0), Q.reshape(-1, 3).max(0)
    sc = min((W - 20) / (mx[0] - mn[0]), (H - 20) / (mx[1] - mn[1]))
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    img = np.zeros((H, W, 3))
    z = np.full((H, W), -1e9)
    cov = np.zeros((H, W), bool)
    S = np.array(at.shape[1::-1])
    L = np.array([-0.4, 0.8, 0.45])
    L /= np.linalg.norm(L)
    for tri, uv, t3 in zip(Q, U, P):
        n = np.cross(t3[1] - t3[0], t3[2] - t3[0])
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        sh = 0.55 + 0.45 * max(0, float(n / ln @ L))
        xs = (tri[:, 0] - cx) * sc + W / 2
        ys = -(tri[:, 1] - cy) * sc + H / 2
        x0, x1 = int(max(0, xs.min())), int(min(W - 1, xs.max() + 1))
        y0, y1 = int(max(0, ys.min())), int(min(H - 1, ys.max() + 1))
        if x1 < x0 or y1 < y0:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        l2 = 1 - l0 - l1
        ins = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if not ins.any():
            continue
        zz = l0 * tri[0, 2] + l1 * tri[1, 2] + l2 * tri[2, 2]
        sub = z[y0:y1 + 1, x0:x1 + 1]
        up = ins & (zz > sub)
        if not up.any():
            continue
        u = l0 * uv[0, 0] + l1 * uv[1, 0] + l2 * uv[2, 0]
        v = l0 * uv[0, 1] + l1 * uv[1, 1] + l2 * uv[2, 1]
        ui = np.clip((u * S[0]).astype(int), 0, S[0] - 1)
        vi = np.clip((v * S[1]).astype(int), 0, S[1] - 1)
        col = at[vi, ui, :3].astype(float)
        col = np.where(gl[vi, ui][..., None], col, col * sh)
        sub[up] = zz[up]
        img[y0:y1 + 1, x0:x1 + 1][up] = col[up]
        cov[y0:y1 + 1, x0:x1 + 1][up] = True
    out = np.where(cov[..., None], img, np.array([30, 32, 40]))
    return Image.fromarray(out.clip(0, 255).astype(np.uint8))


def offline():
    os.makedirs(OUT, exist_ok=True)
    items = []
    for rev, lab in ((BEFORE, "前"), (None, "後")):
        data = load_mesh("heart_warden", rev)
        for yaw, pitch, vlab in ((180, 5, "正面"), (215, 10, "斜め"), (0, 5, "後ろ")):
            items.append(("心臓の番人 %s・%s" % (lab, vlab), render(data, yaw, pitch)))
    grid(items, 3, (320, 360), "心臓の番人の絵 (前 = 4c54f1fe、後 = 今)。光の中の姿 (正射影、手で描いた確かめ)", os.path.join(OUT, "offline_warden.png"))
    marks = Image.open(os.path.join(ROOT, A, "textures/font/shell_marks.png")).convert("RGBA")
    bg = Image.new("RGBA", marks.size, (40, 90, 120, 255))
    bg.alpha_composite(marks)
    bg.resize((marks.width * 4, marks.height * 4), Image.NEAREST).save(os.path.join(OUT, "shell_marks_font.png"))
    print("wrote", os.path.join(OUT, "shell_marks_font.png"))


if __name__ == "__main__":
    if "--offline" in sys.argv:
        offline()
    else:
        ingame()
