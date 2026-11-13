#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""番の弓霊 (弓使いの技 bow_spirit。2026-11-13 の声「弓のペットみたいなの召喚して勝手に撃ってくれる」) の形。
rpg/skill/BowSpirits が prop_bow_spirit (肩のそばに浮かぶ霊。AttackProp の FOLLOW) と prop_bow_spirit_fly (霊の放つ矢) を出す。

    python -X utf8 -I tools/gen/mobs/attack_props/build_bow_spirit.py [--install a|b|c]

  A 骨弓の霊   宙に立つ骨の弓。弦の後ろに青緑の霊の灯 (目が二つ)、尾が後ろへ流れる
  B 骨の小鳥   骨の頭の小さな鳥。翼の先が弓の両端になっていて、胸に矢をつがえる
  C 灯の射手   骨の籠に灯った霊火。籠の前に小さな弓が据わる
前 = -Z (持ち主の見ている向き)。大きさ 1 で 16 = 1 ブロック (呼ぶときは 0.75 倍)。絵は docs/review/skill_props/bow_spirit.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rough_skill_kinds as RSK  # noqa: E402
from rough_skill_kinds import *  # noqa: E402,F401,F403

C0 = 8.0  # 霊の芯の高さ


def bow_arc(m, nm, x0=0.0, y0=C0, z0=0.0, h=8.0, bulge=3.0, w=0.75, mat="bone", string=True):
    """縦の弓 (前 = -Z へ膨らむ)。両端に白い弭、後ろに弦"""
    pts = [P(x0, y0 + h * t, z0 - bulge * (1 - t * t)) for t in np.linspace(-1, 1, 11)]
    m.add(nm, tub("body", mat, pts, [w * 0.7] + [w] * 9 + [w * 0.7], None, hint=(1, 0, 0), sides=5))
    for s in (1, -1):
        m.add(f"{nm}_tip{s}", cone("body", "edgew", (x0, y0 + s * h, z0), (0, s, 0.4), 2.2, w * 0.8, sides=4))
    if string:
        m.add(f"{nm}_str", tub("body", "sigil", [P(x0, y0 + h, z0 + 0.3), P(x0, y0 - h, z0 + 0.3)], [0.22, 0.22], None, hint=(1, 0, 0), sides=4))


def arrow(m, nm, x0=0.0, y0=C0, z0=0.5, ln=11.0, w=0.4):
    m.add(nm, tub("body", "bone", [P(x0, y0, z0), P(x0, y0, z0 - ln)], [w, w], None, sides=5))
    m.add(nm + "_head", cone("body", "edgew", (x0, y0, z0 - ln), (0, 0, -1), 2.6, w * 2.4, sides=4))
    for s in (1, -1):
        m.add(f"{nm}_fl{s}", tub("body", "sigil", [P(x0, y0, z0), P(x0 + s * 1.2, y0, z0 + 1.6), P(x0 + s * 1.0, y0, z0 - 0.6)],
                                 [0.2, 0.25, 0.15], None, hint=(0, 1, 0), sides=4))


def wisp(m, nm, c, r=2.4, tail=True):
    """霊の灯: 青緑の芯と白い目、後ろ下へ流れる尾"""
    c = np.asarray(c, float)
    m.add(nm, ell("body", "sigil", tuple(c), (r, r * 1.1, r), segs=8, rings=4))
    # 頭の上にゆらぐ淡い炎 (霊だとわかる印)
    m.add(nm + "_flame", cone("body", "windp", tuple(c + P(0, r * 0.8, 0.3)), (0, 1, 0.35), r * 1.3, r * 0.55, sides=5))
    for s in (1, -1):
        m.add(f"{nm}_eye{s}", ell("body", "edgew", tuple(c + P(s * r * 0.4, r * 0.15, -r * 0.95)), (0.45, 0.6, 0.3), segs=6, rings=2))
    if tail:
        pts = [c + P(0.6 * math.sin(k * 0.9), -0.9 * k, 1.4 * k) for k in range(6)]
        m.add(nm + "_tail", tub("body", "sigil", pts, [r * 0.8 * (1 - k / 6) + 0.15 for k in range(6)], None, hint=(1, 0, 0), sides=5))


# ---------------------------------------------------------------- A 骨弓の霊
def opt_a(m):
    bow_arc(m, "bow", z0=-1.0, h=8.5, bulge=3.2)
    arrow(m, "arw", z0=1.6, ln=12.0)
    wisp(m, "soul", P(0, C0, 3.6), r=2.3)
    # 弓を抱く細い霊の腕 (弓の握りと矢筈へ)
    for s, (a, b) in ((1, (P(1.6, C0 - 0.6, 3.0), P(0.5, C0, -3.6))), (-1, (P(-1.6, C0 - 0.4, 3.0), P(-0.4, C0, 1.8)))):
        m.add(f"arm{s}", tub("body", "windp", [a, (a + b) / 2 + P(s * 1.2, -0.6, 0), b], [0.55, 0.45, 0.35], None, hint=(0, 1, 0), sides=5))


# ---------------------------------------------------------------- B 骨の小鳥
def opt_b(m):
    body = P(0, C0, 1.0)
    m.add("body", ell("body", "bone", tuple(body), (2.6, 2.4, 3.6), segs=8, rings=4))
    m.add("breast", ell("body", "windp", tuple(body + P(0, -0.4, -1.4)), (1.9, 1.8, 2.0), segs=8, rings=3))
    head = body + P(0, 2.6, -2.6)
    m.add("skull", ell("body", "bone", tuple(head), (1.9, 1.8, 2.0), segs=8, rings=4))
    m.add("beak", cone("body", "edgew", tuple(head + P(0, -0.3, -1.6)), (0, -0.35, -1), 2.6, 0.8, sides=4))
    for s in (1, -1):
        m.add(f"eye{s}", ell("body", "sigil", tuple(head + P(s * 1.05, 0.3, -1.0)), (0.5, 0.6, 0.5), segs=6, rings=2))
        # 翼 = 弓の腕 (外へ反り、先が白い弭)
        pts = [body + P(s * (1.8 + 7.0 * t), 1.2 + 3.2 * t * t, -0.8 - 2.4 * math.sin(t * math.pi * 0.8)) for t in np.linspace(0, 1, 8)]
        m.add(f"wing{s}", tub("body", "bone", pts, [1.4, 1.5, 1.3, 1.1, 0.9, 0.7, 0.55, 0.4], None, hint=(0, 1, 0), sides=5))
        m.add(f"wtip{s}", cone("body", "edgew", tuple(pts[-1]), (s * 0.5, 0.7, 0.2), 2.0, 0.5, sides=4))
        m.add(f"str{s}", tub("body", "sigil", [pts[-1], body + P(0, 0.4, 2.8)], [0.18, 0.18], None, hint=(0, 1, 0), sides=4))
    arrow(m, "arw", y0=C0 + 0.4, z0=2.8, ln=10.0, w=0.35)
    tail = [body + P(0, -0.6 - 0.3 * k, 3.0 + 1.3 * k) for k in range(4)]
    m.add("tail", tub("body", "sigil", tail, [1.1, 0.8, 0.5, 0.2], None, hint=(1, 0, 0), sides=5))


# ---------------------------------------------------------------- C 灯の射手
def opt_c(m):
    c = P(0, C0, 1.5)
    wisp(m, "soul", c, r=1.9, tail=False)
    for k in range(6):
        a = k / 6 * math.tau
        pts = [c + P(math.cos(a) * 3.4 * math.sin(t * math.pi), 4.2 * math.cos(t * math.pi), math.sin(a) * 3.4 * math.sin(t * math.pi)) for t in np.linspace(0, 1, 7)]
        m.add(f"rib{k}", tub("body", "bone", pts, [0.45] * 7, None, hint=(0, 1, 0), sides=4))
    m.add("cap", ell("body", "bone", tuple(c + P(0, 4.3, 0)), (1.6, 0.8, 1.6), segs=6, rings=2))
    m.add("foot", cone("body", "bone", tuple(c + P(0, -4.0, 0)), (0, -1, 0), 2.6, 1.2, sides=5))
    bow_arc(m, "bow", z0=-3.4, h=5.2, bulge=2.2, w=0.6)
    arrow(m, "arw", z0=-2.8, ln=7.0, w=0.32)
    m.add("flame", cone("body", "sigil", tuple(c + P(0, 4.9, 0)), (0, 1, 0.2), 3.0, 0.9, sides=5))


def fly(m):
    # 霊の矢: 細い骨の矢と、尾を引く青緑の光 (前 = -Z、芯 y = CY)
    y = KT.CY
    m.add("shaft", tub("body", "bone", [P(0, y, 6), P(0, y, -5)], [0.45, 0.45], None, sides=5))
    m.add("head", cone("body", "edgew", (0, y, -5), (0, 0, -1), 3.0, 1.2, sides=4))
    m.add("trail", tub("body", "sigil", [P(0, y, 3), P(0, y, 8), P(0, y, 13)], [0.9, 0.6, 0.2], None, hint=(1, 0, 0), sides=5))
    for s in (1, -1):
        m.add(f"fl{s}", tub("body", "windp", [P(0, y, 5.5), P(s * 1.4, y, 7.0), P(s * 1.2, y, 4.0)], [0.25, 0.3, 0.2], None, hint=(0, 1, 0), sides=4))


OPTS = {"a": ("A 骨弓の霊 (推し)", opt_a), "b": ("B 骨の小鳥", opt_b), "c": ("C 灯の射手", opt_c)}


def build(name, fn, center=(0, 0, 0)):
    m = Mount("prop_" + name, name)
    m.bone("body", "root", center)
    fn(m)
    m.pal = dict(mats=rmats(), glow_mats=sorted(RGLOW))
    m.detail = None
    m.hint = 256
    return m


def idle(fly_model):
    g = Gait("idle", 2.0)
    if fly_model:
        g.rot("body", None, None, W(0).s(0.5, 1, 0, "t"))
    else:
        # ゆっくり左右に揺れて、少し前へ傾く
        g.rot("body", W(0).s(4, 1, 0, "t"), W(0).s(6, 1, 0.25, "t"), None)
    return g


def tile(m, yaw=210, pitch=18):
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
    lo = np.min([f.verts.min(0) for f in m.F], axis=0)
    hi = np.max([f.verts.max(0) for f in m.F], axis=0)
    c = (lo + hi) / 2
    sc = 220 / max(1.0, float(np.max(hi - lo)))
    return (size, atlas, glow), view(m.F, atlas, glow, yaw, pitch, (300, 300), c, sc, ground=False), sum(len(f.verts) - 2 for f in m.F)


def main():
    pick = None
    if "--install" in sys.argv:
        pick = sys.argv[sys.argv.index("--install") + 1]
    REVIEW.mkdir(parents=True, exist_ok=True)
    cv = Image.new("RGB", (4 * 310 + 10, 2 * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (key, (label, fn)) in enumerate(OPTS.items()):
        m = build("bow_spirit", fn)
        paint, img, tris = tile(m)
        img2 = tile(m, yaw=320, pitch=10)[1]
        d.text((10 + i * 310, 10), f"{label}  {tris}", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(img, (10 + i * 310, 34))
        cv.paste(img2, (10 + i * 310, 34 + 334))
        print(key, "tris", tris, flush=True)
        if pick == key:
            blp.install(m, "prop_bow_spirit", *paint, [idle(False)], (4, 8))
    mf = build("bow_spirit_fly", fly, (0, KT.CY, 0))
    paint, img, tris = tile(mf, yaw=250, pitch=20)
    d.text((10 + 3 * 310, 10), f"霊の矢  {tris}", font=voxel.font(15), fill=(150, 232, 255))
    cv.paste(img, (10 + 3 * 310, 34))
    if pick:
        blp.install(mf, "prop_bow_spirit_fly", *paint, [idle(True)], (4, 8))
    cv.save(REVIEW / "bow_spirit.png")
    print("wrote", REVIEW / "bow_spirit.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
