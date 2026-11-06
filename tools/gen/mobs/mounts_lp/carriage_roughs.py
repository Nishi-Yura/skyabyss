#!/usr/bin/env python3
"""二頭立ての車 (乗り物。2026-11-07 の声「前に動物 2 体いて、後ろに乗る場所があるみたいなやつも作りたい」) のラフ 3 案。

    python -I tools/gen/mobs/mounts_lp/carriage_roughs.py [a b c]   -> docs/review/mounts_v2/carriage_<案>_rough.png と carriage_roughs.png

今ある乗り物の獣 (角の荷獣・大蜥蜴・灰鹿) の形を二頭、轅 (ながえ) と軛 (くびき) でつなぎ、後ろに車を組む。乗る人の座りの箱を席に置いて見せる
(前の席 = 手綱を取る持ち主、後ろの席 = 乗れる人数の段で増える人。最大 4 人)。まだゲームには入れない (選んだ案を作り込んでから入れる)。
  A 幌の荷車   : 角の荷獣二頭。四つ輪の荷車に布の幌 (骨の輪で張る)、御者台と荷台の腰掛け、軛に鐘
  B 鐘の屋形車 : 大蜥蜴二頭 (低く長い)。四つ輪の屋形 (板の屋根・開いた横)、屋根の前に鐘、四隅に角灯
  C 舟車       : 灰鹿二頭。舟の形の車体に大きな二つ輪、短い帆柱と吹き流し、後ろに帆布の日よけ
"""
from __future__ import annotations

import copy
import importlib
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from mlp import *  # noqa: E402,F401,F403

WOOD = [(46, 30, 20), (70, 46, 30), (96, 66, 42), (124, 88, 56), (150, 110, 72), (174, 134, 92)]
WOODD = [(26, 18, 14), (40, 28, 20), (58, 40, 28), (78, 56, 38)]
IRON = [(28, 30, 34), (46, 50, 56), (70, 76, 84), (98, 104, 112), (130, 136, 144)]
BRASS2 = [(78, 56, 22), (126, 94, 38), (178, 138, 62), (222, 184, 98), (248, 222, 150)]
CANVAS2 = [(84, 78, 62), (120, 112, 90), (156, 148, 120), (190, 182, 152), (216, 208, 180), (236, 230, 206)]
BONE = [(110, 104, 92), (150, 144, 128), (186, 180, 162), (214, 208, 190), (236, 232, 216)]
RED = [(70, 24, 22), (108, 38, 32), (146, 58, 46), (180, 86, 64)]
TEAL = [(22, 52, 56), (34, 78, 82), (52, 108, 110), (80, 140, 138)]
GLOWY = [(120, 80, 20), (200, 150, 60), (250, 220, 140), (255, 244, 200)]

CART_MATS = {
    "c_wood": dict(ramp=WOOD, base=0.58, lit=0.32, noise=0.06, crack=0, min=1, stripe=3, axis="u"),
    "c_woodd": dict(ramp=WOODD, base=0.56, lit=0.30, noise=0.05, crack=0, min=0),
    "c_iron": dict(ramp=IRON, base=0.55, lit=0.34, noise=0.03, crack=0, min=0),
    "c_brass": dict(ramp=BRASS2, base=0.6, lit=0.36, noise=0.03, crack=0, min=0),
    "c_canvas": dict(ramp=CANVAS2, base=0.62, lit=0.30, noise=0.05, crack=0, min=1, streak=0.08),
    "c_bone": dict(ramp=BONE, base=0.6, lit=0.32, noise=0.04, crack=0, min=1),
    "c_red": dict(ramp=RED, base=0.6, lit=0.26, noise=0.04, crack=0, min=0, stripe=4, axis="u"),
    "c_teal": dict(ramp=TEAL, base=0.6, lit=0.28, noise=0.04, crack=0, min=0),
    "c_glow": dict(ramp=GLOWY, base=0.8, lit=0.2, noise=0.0, crack=0, min=0),
}


def moved(faces, off, scale=1.0, about=(0, 0, 0)):
    """面を写して、about を中心に拡大し off だけ動かす (塗りの局所座標 loc はそのまま = 同じ模様)"""
    about = np.asarray(about, float)
    off = np.asarray(off, float)
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = (np.asarray(f.verts, float) - about) * scale + about + off
        g.ref = (np.asarray(f.ref, float) - about) * scale + about + off
        if f.loc is None:
            g.loc = np.asarray(f.verts, float).copy()
        out.append(g)
    return out


def animal(mid):
    mod = importlib.import_module(mid)
    return mod, mod.build()


def wheel(m, pid, c, r, w, mat="c_wood", rim="c_iron", spokes=8):
    """横を向いた輪 (x が軸)。輪縁・こしき・輻"""
    c = np.asarray(c, float)
    ring = [c + np.array([0, r * math.cos(a), r * math.sin(a)]) for a in np.linspace(0, 2 * math.pi, 17)[:-1]]
    m.add(pid + "_rim", tub("cart", rim, ring + [ring[0]], w * 0.5, None, sides=4, hint=(1, 0, 0)))
    m.add(pid + "_hub", ell("cart", mat, c, (w * 0.9, r * 0.22, r * 0.22), segs=8, rings=2))
    for k in range(spokes):
        a = 2 * math.pi * k / spokes
        m.add(f"{pid}_spoke{k}", tub("cart", mat, [c, c + np.array([0, r * 0.92 * math.cos(a), r * 0.92 * math.sin(a)])], w * 0.22, None, sides=4))


def bench(m, pid, c, wdt, mat="c_wood", back=True, cushion="c_red"):
    c = np.asarray(c, float)
    m.add(pid + "_seat", box("cart", mat, c, (wdt, 1.4, 5.0)))
    m.add(pid + "_cush", box("cart", cushion, c + np.array([0, 1.0, 0]), (wdt - 1.0, 0.7, 4.2)))
    if back:
        m.add(pid + "_back", box("cart", mat, c + np.array([0, 3.4, 2.6]), (wdt, 5.6, 1.0)))


def harness(m, animals_x, z_yoke, y_yoke, cart_front_z, cart_y, mat="c_wood", bell=True):
    """二本の轅 (車から前へ、獣の外側) と軛 (二頭の首の上に渡す横木)"""
    xs = animals_x
    span = abs(xs[0] - xs[1]) / 2 + 6.0
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"shaft_{k}", tub("cart", mat, [(s * span, cart_y, cart_front_z), (s * span, y_yoke - 4.0, z_yoke + 4.0), (s * (span - 1.0), y_yoke - 1.0, z_yoke)],
                                0.8, None, sides=5))
    m.add("pole", tub("cart", mat, [(0, cart_y - 1.0, cart_front_z), (0, y_yoke - 3.0, z_yoke - 2.0)], 0.9, None, sides=5))
    m.add("yoke", tub("cart", "c_woodd", [(-span, y_yoke, z_yoke), (-xs[0] * 0.5, y_yoke + 1.2, z_yoke), (0, y_yoke + 0.6, z_yoke),
                                          (xs[0] * 0.5, y_yoke + 1.2, z_yoke), (span, y_yoke, z_yoke)], 1.0, None, sides=5))
    for s in (1, -1):
        x = s * abs(xs[0])
        m.add(f"collar{'lr'[s < 0]}", tub("cart", "c_red", [(x - 3.0, y_yoke - 1.0, z_yoke), (x, y_yoke + 0.8, z_yoke - 0.6), (x + 3.0, y_yoke - 1.0, z_yoke)],
                                         0.8, None, sides=4))
    if bell:
        m.add("yoke_bell", tub("cart", "c_brass", [(0, y_yoke - 0.6, z_yoke - 0.6), (0, y_yoke - 2.4, z_yoke - 0.8), (0, y_yoke - 3.8, z_yoke - 0.8)],
                               [0.7, 1.4, 1.8], None, sides=8))


def riders(seats, seat_y):
    """座った人の箱 (席の上面 seat_y、位置 seats = [(x, z), ...])。最初の人は手綱の持ち主"""
    out = []
    for x, z in seats:
        out += moved(rider_faces(seat_y, -80.0, 18.0), (x, 0, z))
    return out


# ====================================================================== 三案

def option_a():
    """幌の荷車: 角の荷獣二頭、四つ輪の荷車、骨の輪で張った布の幌"""
    mod, beast = animal("horn_beast")
    m = Mount("carriage_a", "幌の荷車")
    m.bone("cart", "root", (0, 16, 14))
    for s in (1, -1):
        m.F += moved(beast.F, (s * 8.5, 0, -18.0), scale=0.92, about=(0, 0, 0))
    eyes = []
    for s in (1, -1):
        for e in beast.eyes:
            e2 = dict(e)
            e2["c"] = np.asarray(e["c"], float) * 0.92 + np.array([s * 8.5, 0, -18.0])
            eyes.append(e2)
    m.eyes = eyes
    # 荷台 (床・側板・前板・後ろ板)
    m.add("bed", box("cart", "c_wood", (0, 15.0, 16.0), (26.0, 2.0, 36.0)), core=True)
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"side_{k}", box("cart", "c_wood", (s * 12.4, 19.0, 16.0), (1.4, 6.0, 36.0)))
        m.add(f"side_trim_{k}", box("cart", "c_red", (s * 12.6, 21.6, 16.0), (0.9, 1.0, 36.4)))
    m.add("tail_board", box("cart", "c_wood", (0, 19.0, 33.6), (26.0, 6.0, 1.4)))
    m.add("front_board", box("cart", "c_wood", (0, 18.0, -1.6), (26.0, 4.0, 1.4)))
    # 幌: 骨の輪 4 本と布 (半筒)
    for i, z in enumerate((6.0, 14.0, 22.0, 30.0)):
        arc = [(12.0 * math.cos(a), 24.0 + 24.0 * math.sin(a), z) for a in np.linspace(0, math.pi, 11)]
        m.add(f"hoop{i}", tub("cart", "c_bone", arc, 0.7, None, sides=4, hint=(0, 0, 1)))
    rings = []
    for z in np.linspace(6.0, 31.0, 6):
        rings.append([np.array([11.7 * math.cos(a), 24.0 + 23.7 * math.sin(a), z]) for a in np.linspace(-0.05, math.pi + 0.05, 13)]
                     + [np.array([11.7 * math.cos(a), 24.0 + 22.9 * math.sin(a), z]) for a in np.linspace(math.pi + 0.05, -0.05, 13)])
    m.add("hood", lof("cart", "c_canvas", rings))
    # 御者台 (前に張り出す) と荷台の腰掛け
    bench(m, "driver", (0, 21.0, -0.5), 18.0)
    bench(m, "rear", (0, 18.5, 18.0), 20.0, back=False)
    # 輪 (前は小さく、後ろは大きく)
    for s, k in ((1, "l"), (-1, "r")):
        wheel(m, f"wf_{k}", (s * 14.6, 8.0, 4.0), 8.0, 2.2)
        wheel(m, f"wb_{k}", (s * 14.6, 10.0, 28.0), 10.0, 2.4)
    m.add("axle_f", tub("cart", "c_iron", [(-14.6, 8.0, 4.0), (14.6, 8.0, 4.0)], 0.8, None, sides=5))
    m.add("axle_b", tub("cart", "c_iron", [(-14.6, 10.0, 28.0), (14.6, 10.0, 28.0)], 0.8, None, sides=5))
    # 吊り角灯
    m.add("lamp", cbox("cart", "c_iron", (11.0, 30.0, -0.4), (2.4, 3.4, 2.4), taper=0.8, cut=0.3))
    m.add("lamp_glow", ell("cart", "c_glow", (11.0, 29.8, -0.4), (1.0, 1.4, 1.0), segs=6, rings=2))
    harness(m, (8.5, -8.5), -26.0, 30.0, -1.6, 16.0)
    seats = riders([(0, -0.5), (-5.0, 18.0), (5.0, 18.0), (0, 26.0)], 22.6)
    return mod, m, seats, dict(center=(0, 22, 4), scale=3.3)


def option_b():
    """鐘の屋形車: 大蜥蜴二頭、四つ輪の屋形 (板の屋根)、屋根の前に鐘、四隅に角灯"""
    mod, beast = animal("great_lizard")
    m = Mount("carriage_b", "鐘の屋形車")
    m.bone("cart", "root", (0, 16, 14))
    for s in (1, -1):
        m.F += moved(beast.F, (s * 9.0, 0, -20.0), scale=0.95)
    m.eyes = [dict(e, c=np.asarray(e["c"], float) * 0.95 + np.array([s * 9.0, 0, -20.0])) for s in (1, -1) for e in beast.eyes]
    m.add("floor", box("cart", "c_wood", (0, 14.0, 16.0), (24.0, 2.0, 32.0)), core=True)
    m.add("skirt", box("cart", "c_teal", (0, 12.0, 16.0), (24.6, 2.4, 32.6)))
    # 柱 4 本と屋根 (反りのある板)
    for sx in (1, -1):
        for sz, z in (("f", 2.0), ("b", 30.0)):
            m.add(f"post_{sx}{sz}", box("cart", "c_woodd", (sx * 11.0, 29.5, z), (1.4, 31.0, 1.4)))
            m.add(f"lamp_{sx}{sz}", cbox("cart", "c_iron", (sx * 12.6, 30.0, z), (2.0, 3.0, 2.0), taper=0.8, cut=0.3))
            m.add(f"lampg_{sx}{sz}", ell("cart", "c_glow", (sx * 12.6, 29.8, z), (0.8, 1.2, 0.8), segs=6, rings=2))
    roof = []
    for z in np.linspace(-1.0, 33.0, 5):
        roof.append([np.array([x, 46.0 + 0.012 * x * x, z]) for x in np.linspace(-15.0, 15.0, 9)]
                    + [np.array([x, 44.6 + 0.012 * x * x, z]) for x in np.linspace(15.0, -15.0, 9)])
    m.add("roof", lof("cart", "c_red", roof))
    m.add("ridge", box("cart", "c_woodd", (0, 46.6, 16.0), (1.6, 1.4, 35.0)))
    # 鐘 (屋根の前に下げる)
    m.add("bell_post", tub("cart", "c_woodd", [(0, 45.0, -1.0), (0, 45.0, -4.0)], 0.7, None, sides=5))
    m.add("bell", tub("cart", "c_brass", [(0, 44.4, -4.0), (0, 41.8, -4.0), (0, 39.6, -4.0)], [0.8, 2.0, 2.8], None, sides=10))
    # 腰掛け (前向き二列) と手すり
    bench(m, "front", (0, 17.0, 4.0), 20.0)
    bench(m, "back", (0, 17.0, 20.0), 20.0)
    for s, k in ((1, "l"), (-1, "r")):
        m.add(f"rail_{k}", tub("cart", "c_woodd", [(s * 11.6, 19.0, 2.0), (s * 11.6, 19.0, 30.0)], 0.6, None, sides=4))
        wheel(m, f"wf_{k}", (s * 13.4, 7.0, 6.0), 7.0, 2.0)
        wheel(m, f"wb_{k}", (s * 13.4, 7.0, 26.0), 7.0, 2.0)
    m.add("axle_f", tub("cart", "c_iron", [(-13.4, 7.0, 6.0), (13.4, 7.0, 6.0)], 0.7, None, sides=5))
    m.add("axle_b", tub("cart", "c_iron", [(-13.4, 7.0, 26.0), (13.4, 7.0, 26.0)], 0.7, None, sides=5))
    harness(m, (9.0, -9.0), -24.0, 22.0, 0.0, 14.0, bell=False)
    seats = riders([(-5.0, 4.0), (5.0, 4.0), (-5.0, 20.0), (5.0, 20.0)], 19.0)
    return mod, m, seats, dict(center=(0, 20, 2), scale=3.3)


def option_c():
    """舟車: 灰鹿二頭、舟の形の車体に大きな二つ輪、帆柱と吹き流し、後ろに帆布の日よけ"""
    mod, beast = animal("ash_deer")
    m = Mount("carriage_c", "舟車")
    m.bone("cart", "root", (0, 16, 14))
    for s in (1, -1):
        m.F += moved(beast.F, (s * 8.0, 0, -18.0), scale=0.92)
    m.eyes = [dict(e, c=np.asarray(e["c"], float) * 0.92 + np.array([s * 8.0, 0, -18.0])) for s in (1, -1) for e in beast.eyes]
    # 舟の胴 (前が細く上がり、後ろは広い)
    hull = []
    for z, w, top, bot in ((-2.0, 3.0, 26.0, 20.0), (2.0, 9.0, 24.0, 14.0), (10.0, 12.0, 23.0, 11.0), (20.0, 12.4, 23.0, 11.0), (28.0, 11.0, 24.0, 12.0),
                           (33.0, 8.0, 25.0, 15.0)):
        hull.append(rsec(z, top, bot, w, n=12))
    m.add("hull", lof("cart", "c_wood", hull), core=True)
    m.add("gunwale", box("cart", "c_teal", (0, 23.4, 16.0), (25.6, 1.0, 30.0)))
    m.add("prow", cone("cart", "c_bone", (0, 25.0, -2.0), (0, 0.5, -1.0), 7.0, 1.4, sides=5, rings=2, bend=(0, 1.0, 0)))
    # 大きな二つ輪 (胴の真ん中)
    for s, k in ((1, "l"), (-1, "r")):
        wheel(m, f"w_{k}", (s * 14.0, 12.0, 18.0), 12.0, 2.4, spokes=10)
    m.add("axle", tub("cart", "c_iron", [(-14.0, 12.0, 18.0), (14.0, 12.0, 18.0)], 0.9, None, sides=5))
    # 帆柱と吹き流し、後ろの日よけ
    m.add("mast", tub("cart", "c_woodd", [(0, 22.0, 8.0), (0, 56.0, 8.0)], 0.9, 0.6, sides=6))
    m.add("pennant", box("cart", "c_red", (0, 53.0, 13.0), (0.4, 3.0, 9.0)))
    for s in (1, -1):
        m.add(f"awning_post{'lr'[s < 0]}", tub("cart", "c_woodd", [(s * 10.0, 23.0, 30.0), (s * 10.0, 48.0, 30.0)], 0.6, None, sides=4))
    m.add("awning", box("cart", "c_canvas", (0, 48.6, 24.0), (22.0, 0.8, 14.0), R=rot_x(-8)))
    bench(m, "fore", (0, 21.0, 6.0), 18.0)
    bench(m, "aft", (0, 21.0, 24.0), 20.0)
    harness(m, (8.0, -8.0), -26.0, 30.0, -2.0, 20.0)
    seats = riders([(0, 6.0), (-5.0, 24.0), (5.0, 24.0), (0, 30.0)], 22.6)
    return mod, m, seats, dict(center=(0, 22, 2), scale=3.2)


OPTIONS = {"a": option_a, "b": option_b, "c": option_c}


def render_option(k):
    mod, m, seats, vw = OPTIONS[k]()
    pal = copy.deepcopy(mod.PAL)
    pal["mats"].update(CART_MATS)
    pal["glow_mats"] = list(pal.get("glow_mats", [])) + ["c_glow"]

    def detail(c, col, glow):
        if getattr(mod, "detail", None):
            col, glow = mod.detail(c, col, glow)
        col = edge_dark(c, col, ["c_wood", "c_woodd", "c_red", "c_teal", "c_canvas"], 0.8)
        return col, glow
    size, atlas, glow = paint_mount(m, pal, detail, 512)
    cen, sc = np.array(vw["center"], float), vw["scale"]
    PW, PH = 420, 340
    W_ = 20 * 2 + 4 * PW + 3 * 12
    H_ = 80 + 2 * (PH + 40) + 10
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    tris = sum(len(f.verts) - 2 for f in m.F)
    d.text((20, 14), f"二頭立ての車 案 {k.upper()}「{m.title}」 ({tris} 三角形)", font=voxel.font(26), fill=(236, 232, 218))
    d.text((20, 48), "上: 立ち姿 (前 / 斜め前 / 横 / 斜め後ろ)。下: 4 人乗ったところ (前の人が手綱)。左の黄色い目盛りが人 (1.8 ブロック)",
           font=voxel.font(16), fill=(176, 176, 192))
    y = 80
    for i, (yaw, pt) in enumerate(((180, 8), (215, 16), (270, 6), (330, 16))):
        cv.paste(view(m.F, atlas, glow, yaw, pt, (PW, PH), cen, sc), (20 + i * (PW + 12), y))
    y += PH + 30
    for i, (yaw, pt) in enumerate(((215, 22), (270, 8), (150, 20), (35, 26))):
        cv.paste(view_with_rider_faces(m, seats, atlas, glow, yaw, pt, (PW, PH), cen, sc), (20 + i * (PW + 12), y))
    REVIEW.mkdir(parents=True, exist_ok=True)
    out = REVIEW / f"carriage_{k}_rough.png"
    cv.save(out)
    print(k, m.title, tris, "tris ->", out)
    return out


def view_with_rider_faces(m, rider, atlas, glow, yaw, pitch, size, center, scale):
    cols = {"plc": (70, 92, 150), "pls": (206, 160, 124)}
    S = atlas.shape[0]
    big = np.zeros((S + 8, S + 8, 4), np.uint8)
    big[:S, :S] = atlas
    big[S:S + 2, :] = cols["plc"] + (255,)
    big[S + 2:S + 4, :] = cols["pls"] + (255,)
    gl = np.zeros(big.shape[:2], bool)
    gl[:S, :S] = glow
    fs = []
    for f in rider:
        g = copy.copy(f)
        row = S if f.mat == "plc" else S + 2
        g.uv = np.array([[1, row + 0.5]] * len(f.verts), float)
        fs.append(g)
    img = render(m.F + fs, big, gl, yaw, pitch, size, center, scale)
    bg = bg_panel(*size)
    bg.paste(img, (0, 0), img)
    return bg


if __name__ == "__main__":
    ks = sys.argv[1:] or list(OPTIONS)
    outs = [render_option(k) for k in ks]
    if len(outs) == 3:
        ims = [Image.open(o) for o in outs]
        W = max(i.width for i in ims)
        cv = Image.new("RGB", (W, sum(i.height for i in ims)), (16, 16, 22))
        yy = 0
        for i in ims:
            cv.paste(i, (0, yy))
            yy += i.height
        cv.save(REVIEW / "carriage_roughs.png")
    print("ok")
