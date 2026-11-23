#!/usr/bin/env python3
"""心臓 4 体の作り込みのラフ (2026-11-11 の声「ラフを出して」。骨掘りの大百足の方向で、心臓にも恐ろしい見せ場を)。
今の模型 (lowpoly/<心臓>_p1.json と textures/lowpoly/<心臓>_p1.png) はそのまま読み、足す部品だけを作って重ねて描く。

  A 裂ける口  正面が縦に裂け、縁に牙の列、奥に赤い喉と一つ目
  B 肋骨の籠  巨大な肋骨が下から反って心臓を抱え、肋のあいだに筋が垂れる。先は鉤爪
  C 眼の群れ  面のあちこちに目が開き、まわりへ血の筋が走る

    python -X utf8 -I tools/rough/lowpoly/heart_horror/build.py
出力: docs/review/heart_horror/<心臓>.png (今の姿・A・B・C の正面と斜め)
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "gen/mobs/dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
from lpkit import Face, render  # noqa: E402

OUT = ROOT / "docs/review/heart_horror"
LOW = ROOT / "src/main/resources/assets/skyabyss/lowpoly"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"

HEARTS = {
    "stone_whale_heart": dict(title="岩鯨の心臓", bone=[(150, 140, 120), (196, 186, 164), (228, 220, 200), (244, 238, 224)],
                              maw=[(40, 6, 12), (80, 14, 22), (124, 26, 34), (168, 50, 50)], iris=(255, 170, 60),
                              flesh=[(60, 24, 26), (92, 40, 38), (126, 60, 52)], vein=[(120, 20, 24), (170, 40, 36), (220, 80, 60)]),
    "tide_heart": dict(title="潮殻の心臓", bone=[(180, 120, 110), (220, 164, 150), (240, 200, 186), (250, 228, 216)],
                       maw=[(30, 10, 30), (60, 20, 56), (96, 34, 84), (140, 60, 120)], iris=(120, 240, 255),
                       flesh=[(70, 30, 60), (104, 48, 86), (140, 72, 112)], vein=[(40, 120, 150), (70, 170, 200), (130, 220, 240)]),
    "storm_heart": dict(title="雷翼の心臓", bone=[(150, 156, 170), (192, 198, 210), (224, 228, 238), (244, 246, 252)],
                        maw=[(16, 18, 40), (30, 34, 70), (50, 60, 110), (90, 110, 170)], iris=(210, 240, 255),
                        flesh=[(40, 44, 70), (64, 70, 104), (96, 104, 140)], vein=[(90, 160, 255), (150, 200, 255), (220, 240, 255)]),
    "ember_heart": dict(title="余燼の心臓", bone=[(40, 34, 32), (70, 60, 54), (104, 90, 80), (140, 124, 110)],
                        maw=[(120, 30, 10), (190, 70, 20), (240, 130, 40), (255, 200, 100)], iris=(255, 220, 120),
                        flesh=[(50, 20, 14), (80, 34, 20), (112, 52, 30)], vein=[(200, 70, 20), (250, 140, 40), (255, 210, 110)]),
}


def load_base(name):
    data = json.loads((LOW / f"{name}.json").read_text(encoding="utf-8"))
    size = data["texture_size"][0]
    atlas = np.array(Image.open(TEX / f"{name}.png").convert("RGBA"))
    gpath = TEX / f"{name}_glowmask.png"
    glow = np.array(Image.open(gpath).convert("RGBA"))[..., 3] > 0 if gpath.exists() else np.zeros(atlas.shape[:2], bool)
    # 骨の休めの回し (geo の rotation) をかけて組む (lp_*_heart/common.bone_xf と同じ)
    geo = json.loads((ROOT / f"src/main/resources/assets/skyabyss/geo/entity/{name}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    defs = {g["name"]: (g.get("parent"), np.array(g["pivot"], float), [float(x) for x in g.get("rotation", [0, 0, 0])]) for g in geo["bones"]}
    cache = {}

    def xf(n):
        if n in cache:
            return cache[n]
        par, pv, rot = defs[n]
        Rm = rot_z(rot[2]) @ rot_y(rot[1]) @ rot_x(rot[0])
        t = pv - Rm @ pv
        if par is not None and par in defs:
            Rp, tp = xf(par)
            Rm, t = Rp @ Rm, Rp @ t + tp
        cache[n] = (Rm, t)
        return cache[n]
    faces = []
    for bname, b in data["bones"].items():
        Rb, tb = xf(bname) if bname in defs else (np.eye(3), np.zeros(3))
        if not b["vertices"]:
            continue
        V = np.array(b["vertices"], float) @ Rb.T + tb
        UV = np.array(b["uvs"], float) * size
        for t in b["triangles"]:
            v = V[t]
            n = np.cross(v[1] - v[0], v[2] - v[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n = n / np.linalg.norm(n)
            f = Face(verts=v, bone=bname, mat="base", ref=v.mean(0) - n)
            f.normal = n
            f.uv = UV[t]
            faces.append(f)
    return faces, atlas, glow


def bounds(faces):
    V = np.concatenate([f.verts for f in faces])
    lo, hi = V.min(0), V.max(0)
    return (lo + hi) / 2, float(np.max(hi - lo)) / 2, lo, hi


# ---------------------------------------------------------------- 足す部品 (心臓の中心 c と半径 R から)
def v_maw(m, c, R, front_z):
    """A 裂ける口: 正面に縦の裂け目、縁の牙の列、奥の赤い喉と一つ目"""
    z = front_z
    m.add("throat", ell("root", "maw", c + np.array([0, 0, 0]) * 0 + np.array([c[0], c[1], z + R * 0.08]) - c, (R * 0.30, R * 0.58, R * 0.14), segs=12, rings=4))
    eye(m, "root", np.array([c[0], c[1] + R * 0.04, z - R * 0.04]), R * 0.17, (0, 0, -1), ICOL, glow=True, lid=(40, 6, 12))
    for s in (1, -1):
        m.add(f"lip{s}", tub("root", "flesh", [np.array([c[0] + s * R * 0.2 * math.sqrt(max(0.0, 1 - (yy / 0.62) ** 2)) + s * R * 0.08, c[1] + yy * R, z - R * 0.06])
                                               for yy in np.linspace(-0.6, 0.6, 7)], [R * 0.06] * 7, None, sides=6))
        for k in range(9):
            yy = -0.52 + k * 0.13
            w = math.sqrt(max(0.0, 1 - (yy / 0.62) ** 2))
            base = np.array([c[0] + s * (R * 0.2 * w + R * 0.06), c[1] + yy * R, z - R * 0.08])
            L = R * (0.16 + 0.08 * w) * (0.85 + 0.3 * ((k * 7) % 3) / 2)
            m.add(f"tooth{s}{k}", cone("root", "bone", base, (-s, -0.15 * np.sign(yy), -0.5), L, R * 0.045, sides=4, rings=2, bend=(0, -np.sign(yy) * L * 0.15, 0)))
    for s in (1, -1):
        m.add(f"fang{s}", cone("root", "bone", np.array([c[0] + s * R * 0.1, c[1] + R * 0.62, z - R * 0.06]), (-s * 0.2, -1, -0.4), R * 0.42, R * 0.07, sides=5, rings=3,
                              bend=(0, 0, -R * 0.08)))


def v_cage(m, c, R, front_z):
    """B 肋骨の籠: 下から反って抱える巨大な肋骨、肋のあいだの筋、先の鉤爪"""
    # 正面 (-Z、角度 -90°) の前だけを開けて、まわりに八本
    for k in range(8):
        a = math.radians(-90 + 40 + k * (280 / 7))
        d = np.array([math.cos(a), 0, math.sin(a)])
        pts = [c + d * R * 0.55 + np.array([0, -R * 1.05, 0]), c + d * R * 1.25 + np.array([0, -R * 0.55, 0]), c + d * R * 1.38 + np.array([0, R * 0.15, 0]),
               c + d * R * 1.15 + np.array([0, R * 0.85, 0]), c + d * R * 0.62 + np.array([0, R * 1.18, 0])]
        m.add(f"rib{k}", tub("root", "bone", pts, [R * 0.11, R * 0.10, R * 0.09, R * 0.07, R * 0.04], None, sides=6))
        m.add(f"claw{k}", cone("root", "bone", pts[-1], c + np.array([0, R * 0.3, 0]) - pts[-1], R * 0.32, R * 0.05, sides=4, rings=2, bend=(0, -R * 0.12, 0)))
        if k % 2 == 0:
            mid = (pts[2] + pts[3]) / 2
            m.add(f"sinew{k}", tub("root", "flesh", [mid, (mid + c) / 2 + np.array([0, -R * 0.25, 0]), c + d * R * 0.75 + np.array([0, -R * 0.1, 0])],
                                   [R * 0.03, R * 0.04, R * 0.035], None, sides=4))
    m.add("spine", tub("root", "bone", [c + np.array([0, -R * 1.1, R * 0.5]), c + np.array([0, -R * 0.9, R * 1.2]), c + np.array([0, -R * 0.2, R * 1.45])],
                       [R * 0.14, R * 0.12, R * 0.08], None, sides=7))


def v_eyes(m, c, R, front_z):
    """C 眼の群れ: 前の半球に大小の目が開き、まわりに血の筋"""
    rng = np.random.default_rng(7)
    spots = []
    for k in range(11):
        for _ in range(40):
            th = rng.uniform(-0.95, 0.95)
            ph = rng.uniform(-1.05, 1.05)
            d = np.array([math.sin(ph) * math.cos(th), math.sin(th), -math.cos(ph) * math.cos(th)])
            if all(np.dot(d, q) < 0.93 for q in spots):
                spots.append(d)
                break
    for k, d in enumerate(spots):
        r = R * (0.15 if k == 0 else rng.uniform(0.06, 0.11))
        if k == 0:
            d = np.array([0, 0.05, -1.0])
            d = d / np.linalg.norm(d)
        p = c + d * R * 1.0
        m.add(f"socket{k}", ell("root", "maw", p - d * r * 0.2, (r * 1.35, r * 1.35, r * 1.35), segs=8, rings=3))
        eye(m, "root", p + d * r * 0.25, r, tuple(d), ICOL, glow=True, lid=(40, 8, 14))
        side = np.cross(d, [0, 1, 0])
        side = side / (np.linalg.norm(side) + 1e-9)
        up = np.cross(side, d)
        for j in range(3):
            a = rng.uniform(0, math.tau)
            t = side * math.cos(a) + up * math.sin(a)
            q0 = p + t * r * 1.2
            q1 = q0 + t * r * 1.6 + d * r * 0.1
            q2 = q1 + (t + rng.normal(size=3) * 0.4) * r * 1.2
            m.add(f"vein{k}{j}", tub("root", "vein", [q0, q1, q2], [r * 0.16, r * 0.11, r * 0.05], None, sides=4))


CORE = {"tide_heart": {"heart"}, "storm_heart": {"heart"}}
VARIANTS = [("A 裂ける口", v_maw), ("B 肋骨の籠", v_cage), ("C 眼の群れ", v_eyes)]
ICOL = (255, 170, 60)


def build_add(name, spec, fn, c, R, front_z):
    global ICOL
    ICOL = spec["iris"]
    m = Mount("hh_" + name, spec["title"])
    fn(m, c, R, front_z)
    m.pal = dict(mats={"eye": M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0), "bone": M_(spec["bone"], lit=0.36), "maw": M_(spec["maw"], lit=0.2, streak=0.06),
                       "flesh": M_(spec["flesh"], lit=0.3, streak=0.05), "vein": M_(spec["vein"], base=0.7, lit=0.2, min=1)}, glow_mats=["vein"] if name == "ember_heart" else [])
    m.detail = None
    size, atlas, glow = blp.paint_mount(m, m.pal, None, 256)
    return m.F, atlas, glow, size


def merge(base, add):
    bf, ba, bg = base
    af, aa, ag = add
    sb = ba.shape[1]
    # 描く側は正方形の絵を前提にする
    W = H = max(sb + aa.shape[1], ba.shape[0], aa.shape[0])
    atlas = np.zeros((H, W, 4), np.uint8)
    atlas[:ba.shape[0], :sb] = ba
    atlas[:aa.shape[0], sb:] = aa
    glow = np.zeros((H, W), bool)
    glow[:bg.shape[0], :sb] = bg
    glow[:ag.shape[0], sb:] = ag
    out = list(bf)
    for f in af:
        g = Face(verts=f.verts, bone=f.bone, mat=f.mat, ref=f.ref)
        g.normal = f.normal
        g.uv = f.uv + np.array([sb, 0.0])
        out.append(g)
    return out, atlas, glow


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    F = voxel.font
    for name, spec in HEARTS.items():
        base = load_base(name + "_p1")
        cf, Rfull, flo, fhi = bounds(base[0])
        # 足す部品は心臓の芯 (潮殻・雷翼は渦や翼を除いた心臓の骨) の大きさに合わせる
        core = CORE.get(name)
        cfaces = [f for f in base[0] if core is None or f.bone in core]
        c, R, lo, hi = bounds(cfaces)
        R = float(min(hi[0] - lo[0], hi[2] - lo[2])) / 2 * (0.96 if core is None else 1.05)
        c = np.array([c[0], lo[1] + (hi[1] - lo[1]) * 0.5, (lo[2] + hi[2]) / 2])
        front_z = lo[2] + R * 0.02
        cols = [("今の姿", base)]
        for label, fn in VARIANTS:
            af, aa, ag, _ = build_add(name, spec, fn, c, R, front_z)
            cols.append((label, merge(base, (af, aa, ag))))
        PW, PH = 360, 360
        cv = Image.new("RGB", (20 + len(cols) * (PW + 10), 80 + 2 * (PH + 10)), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        d.text((20, 14), f"{spec['title']} — 作り込みのラフ (今の姿の上に足す)", font=F(26), fill=(236, 232, 218))
        sc = PW * 0.40 / max(Rfull, R * 1.45)
        for i, (label, (faces, atlas, glow)) in enumerate(cols):
            x = 20 + i * (PW + 10)
            d.text((x, 50), label, font=F(18), fill=(150, 232, 255))
            for j, (yaw, pitch) in enumerate(((180, 6), (215, 14))):
                im = render(faces, atlas[..., :3], glow, yaw, pitch, (PW, PH), cf, sc)
                bg = Image.new("RGBA", (PW, PH), (28, 28, 38, 255))
                bg.alpha_composite(im)
                cv.paste(bg.convert("RGB"), (x, 80 + j * (PH + 10)))
        cv.save(OUT / f"{name}.png")
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
