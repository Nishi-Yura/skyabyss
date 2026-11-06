#!/usr/bin/env python3
"""二頭立ての幌の荷車 (乗り物の見た目 carriage。2026-11-07 の頁「二頭立ての車」の選定: 形 A・最大 6 人・四つの心臓のあと)。
選定の書き添え「後ろの白色の布小さいかも」で、幌を荷台いっぱいに大きくした。最大 6 人なので荷台も一回り大きい。

    python -I tools/gen/mobs/mounts_lp/carriage.py [--no-install] [--no-sheet]

角の荷獣 (horn_beast.py) の形と動きを二頭ぶん写し (骨は a_ / b_ を頭に付ける)、轅と軛でつなぎ、後ろに四つ輪の荷車を組む。
骨: root / cart / wheel_{fl,fr,bl,br} / a_* / b_* (角の荷獣の骨)。歩き・駆けでは輪が進んだ道のりだけ回る (W の lin)。
鞍は付けない (乗る人は腰掛けに座る。席の並びは Java の MountEntity.CARRIAGE_SEATS)。
"""
from __future__ import annotations

import copy
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from mlp import *  # noqa: E402,F401,F403
import horn_beast as HB  # noqa: E402
from carriage_roughs import CART_MATS, wheel as _wheel_rough, bench  # noqa: E402

ID = "carriage"
TITLE = "幌の荷車"
# 二頭の置き場 (1/16 ブロック。前 = -z)
BEASTS = {"a": (8.8, -20.0), "b": (-8.8, -20.0)}
# 輪 (骨の名前, x, 高さ (= 半径), z, 輪の幅)
WHEELS = [("wheel_fl", 16.6, 8.0, 6.0, 2.2), ("wheel_fr", -16.6, 8.0, 6.0, 2.2), ("wheel_bl", 16.6, 10.0, 33.0, 2.4), ("wheel_br", -16.6, 10.0, 33.0, 2.4)]
# 腰掛け (中心 x, y, z, 幅)。前 = 御者台 (持ち主)、後ろに三列
BENCHES = [("driver", 0.0, 21.0, -0.5, 18.0), ("row1", 0.0, 18.5, 13.0, 24.0), ("row2", 0.0, 18.5, 23.0, 24.0), ("row3", 0.0, 18.5, 33.0, 12.0)]


def prefixed(beast, p, off):
    """角の荷獣の骨と面を、骨の名前の頭に p を付けて off だけ動かして写す"""
    off = np.asarray(off, float)
    bones = {}
    for name, (parent, pv) in beast.bones.items():
        if name == "root":
            continue
        bones[p + name] = ("root" if parent == "root" else p + parent, tuple(np.asarray(pv, float) + off))
    faces = []
    for f in beast.F:
        g = copy.copy(f)
        g.verts = np.asarray(f.verts, float) + off
        g.ref = np.asarray(f.ref, float) + off
        if f.loc is None:
            g.loc = np.asarray(f.verts, float).copy()
        g.bone = p + f.bone
        g.part = p + f.part
        faces.append(g)
    eyes = []
    for e in beast.eyes:
        e2 = dict(e)
        e2["c"] = np.asarray(e["c"], float) + off
        e2["bone"] = p + e["bone"]
        eyes.append(e2)
    return bones, faces, eyes


def wheel(m, bone, c, r, w, spokes=8):
    """輪 (骨 bone のまわりに回る)。輪縁・こしき・輻"""
    c = np.asarray(c, float)
    ring = [c + np.array([0, r * math.cos(a), r * math.sin(a)]) for a in np.linspace(0, 2 * math.pi, 17)[:-1]]
    m.add(bone + "_rim", tub(bone, "c_iron", ring + [ring[0]], w * 0.5, None, sides=4, hint=(1, 0, 0)))
    m.add(bone + "_felloe", tub(bone, "c_wood", [c + np.array([0, (r - 1.2) * math.cos(a), (r - 1.2) * math.sin(a)]) for a in np.linspace(0, 2 * math.pi, 17)],
                                 w * 0.36, None, sides=4, hint=(1, 0, 0)))
    m.add(bone + "_hub", ell(bone, "c_woodd", c, (w * 0.95, r * 0.22, r * 0.22), segs=8, rings=2))
    for k in range(spokes):
        a = 2 * math.pi * k / spokes
        m.add(f"{bone}_spoke{k}", tub(bone, "c_wood", [c, c + np.array([0, (r - 1.0) * math.cos(a), (r - 1.0) * math.sin(a)])], w * 0.22, None, sides=4))


def build():
    beast = HB.build()
    m = Mount(ID, TITLE)
    m.bone("cart", "root", (0, 16, 16))
    for b, x, r, z, w in WHEELS:
        m.bone(b, "cart", (x, r, z))
    m.eyes = []
    for p, (x, z) in BEASTS.items():
        bones, faces, eyes = prefixed(beast, p + "_", (x, 0, z))
        for name, (parent, pv) in bones.items():
            m.bones[name] = (parent, pv)
        m.F += faces
        m.eyes += eyes
    # ---------------------------------------------------------------- 荷台 (床・側板・前板・後ろ板。一回り大きく: 幅 32・長さ 44)
    m.add("bed", box("cart", "c_wood", (0, 15.0, 19.0), (30.0, 2.0, 44.0)), core=True)
    m.add("bed_under", box("cart", "c_woodd", (0, 13.4, 19.0), (26.0, 1.6, 40.0)))
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"side_{k}", box("cart", "c_wood", (s * 14.4, 18.6, 19.0), (1.4, 6.0, 44.0)))
        m.add(f"side_trim_{k}", box("cart", "c_red", (s * 14.6, 21.2, 19.0), (0.9, 1.0, 44.4)))
        for i, z in enumerate((2.0, 12.0, 22.0, 32.0)):
            m.add(f"side_rib_{k}{i}", box("cart", "c_woodd", (s * 15.2, 18.0, z), (0.6, 5.2, 1.0)))
    m.add("tail_board", box("cart", "c_wood", (0, 18.6, 40.6), (30.0, 6.0, 1.4)))
    m.add("tail_trim", box("cart", "c_red", (0, 21.2, 41.0), (30.4, 1.0, 0.9)))
    m.add("front_board", box("cart", "c_wood", (0, 18.0, -2.6), (30.0, 4.0, 1.4)))
    # 幌: 骨の輪 5 本と布 (半筒。荷台の前から後ろまで、人の頭の上 = 高さ 48 まで)
    for i, z in enumerate((5.0, 13.0, 21.0, 29.0, 37.0)):
        arc = [(14.0 * math.cos(a), 21.0 + 27.0 * math.sin(a), z) for a in np.linspace(0, math.pi, 11)]
        m.add(f"hoop{i}", tub("cart", "c_bone", arc, 0.75, None, sides=4, hint=(0, 0, 1)))
    rings = []
    for z in np.linspace(4.2, 38.2, 7):
        rings.append([np.array([13.7 * math.cos(a), 21.0 + 26.7 * math.sin(a), z]) for a in np.linspace(-0.04, math.pi + 0.04, 13)]
                     + [np.array([13.7 * math.cos(a), 21.0 + 25.9 * math.sin(a), z]) for a in np.linspace(math.pi + 0.04, -0.04, 13)])
    m.add("hood", lof("cart", "c_canvas", rings))
    # 幌の縁の縄と、後ろに巻き上げた垂れ幕
    m.add("hood_rope_f", tub("cart", "c_rope", [(14.0 * math.cos(a), 21.0 + 27.2 * math.sin(a), 4.0) for a in np.linspace(0, math.pi, 11)], 0.45, None, sides=4,
                             hint=(0, 0, 1)))
    m.add("hood_roll", tub("cart", "c_canvas", [(-11.0, 40.0, 38.6), (0, 44.6, 38.8), (11.0, 40.0, 38.6)], 1.3, None, sides=6))
    # 腰掛け
    for name, x, y, z, wdt in BENCHES:
        bench(m, name, (x, y, z), wdt, back=(name == "driver"))
    # 輪と車軸
    for b, x, r, z, w in WHEELS:
        wheel(m, b, (x, r, z), r, w)
    m.add("axle_f", tub("cart", "c_iron", [(-16.6, 8.0, 6.0), (16.6, 8.0, 6.0)], 0.8, None, sides=5))
    m.add("axle_b", tub("cart", "c_iron", [(-16.6, 10.0, 33.0), (16.6, 10.0, 33.0)], 0.8, None, sides=5))
    m.add("axle_post_f", box("cart", "c_woodd", (0, 11.0, 6.0), (6.0, 4.0, 2.0)))
    m.add("axle_post_b", box("cart", "c_woodd", (0, 12.0, 33.0), (6.0, 2.4, 2.0)))
    # 吊り角灯 (御者台の右前)
    m.add("lamp_arm", tub("cart", "c_woodd", [(13.0, 22.0, -2.6), (13.0, 31.0, -2.6), (13.0, 31.0, -5.0)], 0.5, None, sides=4))
    m.add("lamp", cbox("cart", "c_iron", (13.0, 28.6, -5.0), (2.4, 3.4, 2.4), taper=0.8, cut=0.3))
    m.add("lamp_glow", ell("cart", "c_glow", (13.0, 28.4, -5.0), (1.0, 1.4, 1.0), segs=6, rings=2))
    # 轅 (二頭の外) ・真ん中の竿・軛・首の当て布・軛の鐘
    span = 8.8 + 6.4
    yy, zy = 30.0, -28.0
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"shaft_{k}", tub("cart", "c_wood", [(s * span, 16.0, -2.6), (s * span, yy - 5.0, zy + 6.0), (s * (span - 1.0), yy - 1.0, zy)], 0.85, None, sides=5))
        m.add(f"trace_{k}", tub("cart", "c_leath", [(s * 8.8, yy - 4.0, zy + 2.0), (s * 8.8, 20.0, -6.0), (s * 7.0, 16.5, -2.6)], 0.4, None, sides=4))
    m.add("pole", tub("cart", "c_wood", [(0, 15.0, -2.6), (0, yy - 3.0, zy - 2.0)], 0.95, None, sides=5))
    m.add("yoke", tub("cart", "c_woodd", [(-span, yy, zy), (-4.4, yy + 1.2, zy), (0, yy + 0.6, zy), (4.4, yy + 1.2, zy), (span, yy, zy)], 1.05, None, sides=5))
    for s in (1, -1):
        x = s * 8.8
        m.add(f"collar{'lr'[s < 0]}", tub("cart", "c_red", [(x - 3.2, yy - 1.2, zy), (x, yy + 0.8, zy - 0.6), (x + 3.2, yy - 1.2, zy)], 0.85, None, sides=4))
    m.add("yoke_bell", tub("cart", "c_brass", [(0, yy - 0.6, zy - 0.6), (0, yy - 2.4, zy - 0.8), (0, yy - 3.8, zy - 0.8)], [0.7, 1.4, 1.8], None, sides=8))
    m.core = {"bed"}
    pal = copy.deepcopy(HB.PAL)
    pal["mats"].update(CART_MATS)
    pal["mats"]["c_rope"] = dict(ramp=[(70, 58, 40), (106, 90, 62), (146, 128, 92), (184, 168, 128)], base=0.6, lit=0.26, noise=0.08, crack=0, min=0)
    pal["mats"]["c_leath"] = dict(ramp=[(46, 28, 18), (76, 48, 28), (108, 72, 42), (142, 102, 62)], base=0.6, lit=0.28, noise=0.05, crack=0, min=0)
    pal["glow_mats"] = list(pal.get("glow_mats", [])) + ["c_glow"]
    m.pal = pal
    m.detail = detail
    m.view = dict(center=(0, 22, 0), scale=3.0,
                  zoom=[((0, 26, 20), 215, 18, 1.8), ((0, 30, -28), 200, 10, 2.2), ((0, 22, 0), 300, 26, 1.6), ((16, 9, 20), 270, 4, 2.4)])
    m.gaits = gaits(beast)
    m.hint = 512
    return m


def detail(c, col, glow):
    """角の荷獣の塗りを二頭に (骨の名前の頭の a_ / b_ を外して渡す)、車は縁を暗く"""
    c2 = dict(c)
    b = c["bones"].astype(str)
    flat = [x[2:] if x[:2] in ("a_", "b_") else x for x in b.ravel()]
    c2["bones"] = np.array(flat, dtype=b.dtype).reshape(b.shape)
    col, glow = HB.detail(c2, col, glow)
    col = edge_dark(c, col, ["c_wood", "c_woodd", "c_red", "c_canvas", "c_leath"], 0.8)
    return col, glow


def gaits(beast):
    """角の荷獣の動きを二頭ぶん (b は少しずらす)。歩き・駆けでは輪が道のりだけ回る"""
    base = HB.gaits(beast)
    out = []
    for g in base:
        n = Gait(g.name, period=g.period, stride=g.stride, loop=g.loop)
        n.length = g.length
        if g.keys is not None:
            n.keys = {}
            for bone, ch in g.keys.items():
                for p in ("a_", "b_"):
                    n.keys[p + bone] = ch
        else:
            for bone, ch in g.bones.items():
                for p in ("a_", "b_"):
                    n.bones[p + bone] = dict(ch)
            if g.name in ("walk", "run"):
                for bname, x, r, z, w in WHEELS:
                    # 一ブロック進むと 360 / (2πr) 度回る (r はブロック)。前へ転がる向き
                    deg = 360.0 / (2 * math.pi * r / 16.0)
                    n.rot(bname, x=W(0).s(-deg, 1, 0, "lin"))
                n.pos("cart", y=W(0).s(0.25, 2, 0))
        out.append(n)
    return out


def main(install=True, sheet=True):
    import build_all as BA
    t0 = time.time()
    m = build()
    driver = BENCHES[0]
    seat_top = driver[2] + 1.35
    seat = round((seat_top - 1.0) / 16.0, 3)
    pitch, spread = -80.0, 16.0
    size, atlas, glow = paint_mount(m, m.pal, m.detail, m.hint)
    out = WORK / ID
    data, has_glow = export(m, size, atlas, glow, out, {"seat_y": seat_top, "rider_seat_blocks": seat, "rider_leg_pitch": pitch, "rider_leg_spread": spread})
    name = "mount_" + ID
    info = dict(id=ID, tris=data["triangle_count"], atlas=size, bones=len(m.bones), seat=seat, pitch=pitch, spread=spread)
    print(info, f"{time.time() - t0:.1f}s", flush=True)
    if install:
        cube_geo(m, ASSETS / "geo/entity" / (name + ".geo.json"))
        avg = atlas[..., :3][atlas[..., 3] > 0].mean(0).astype(np.uint8)
        Image.new("RGBA", (16, 16), tuple(int(x) for x in avg) + (255,)).save(ASSETS / "textures/entity" / (name + ".png"))
        cmd = [sys.executable, str(ROOT / "tools/install/install_lowpoly.py"), name, "--mesh", str(out / f"{name}_mesh.json"),
               "--atlas", str(out / f"{name}_atlas.png")]
        cmd += ["--glow", str(out / f"{name}_glowmask.png")] if has_glow else ["--glow", str(out / "none.png")]
        subprocess.check_call(cmd, cwd=str(ROOT))
        write_anims(m.gaits, ASSETS / "animations/entity" / (name + ".animation.json"))
    if sheet:
        BA.make_sheet(m, atlas, glow, seat, pitch, spread, info)
    return info


if __name__ == "__main__":
    main(install="--no-install" not in sys.argv, sheet="--no-sheet" not in sys.argv)
