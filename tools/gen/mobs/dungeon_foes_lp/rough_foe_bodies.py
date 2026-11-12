"""敵の体の作り直しの案 A/B/C (2026-11-12 の全モデルの確かめで、粗いと分かった敵 13 体。声「直す」)。
見本は二体: 沈み船の水夫 (人の形) と 帯電毛玉 (人でない形)。絵だけ (ゲームには入れない)。

    python -X utf8 -I tools/gen/mobs/dungeon_foes_lp/rough_foe_bodies.py

  A 作り込み    今の姿のまま、面を増やして丸め、部品を足す (服のしわと継ぎ・帯と留め具・指・顔の彫り・毛の房の段)
  B 姿勢と輪郭  体の比を変えて輪郭で読ませる (猫背・長い腕・大きな手、毛玉は大きくふくらんで縮む核)。細部は A より少なめ
  C 土地の材    体を土地の材が覆う (水夫は珊瑚と藤壺と海藻が育つ、毛玉は雷の晶と銅の針)。巨神ごとの敵の材の決まりに寄せる
絵は docs/review/foe_bodies/rough_<a|b|c>.png
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from foes import *  # noqa: E402,F401,F403
import foes as F  # noqa: E402

OUT = ROOT / "docs/review/foe_bodies"
DOWN = [(150, 156, 176), (186, 192, 210), (216, 222, 236), (240, 244, 252)]
COPPER = [(90, 48, 26), (136, 76, 40), (184, 112, 60), (224, 156, 96)]
KELP = [(26, 50, 30), (40, 76, 42), (60, 104, 56), (88, 134, 74)]
SKINW = [(70, 92, 96), (96, 122, 124), (124, 150, 150), (156, 180, 176)]


def mats_():
    return pal_({"sea": M_(SKINW, streak=0.05), "cloth": M_(STRIPE, stripe=4, axis="u"), "barn": M_(BARN, lit=0.34), "wood": M_(WOOD), "iron": M_(IRON),
                 "coral": M_(CORAL, lit=0.36), "kelp": M_(KELP, streak=0.1), "leather": M_(LEATHR), "glow": M_(GLOWSEA, base=0.7, lit=0.2),
                 "down": M_(DOWN, lit=0.3, noise=0.2), "copper": M_(COPPER, lit=0.42), "spark": M_(SPARK, base=0.8, lit=0.1, min=1),
                 "cry": M_(CRY, base=0.64, lit=0.22), "storm": M_(STORM)}, glow=["glow", "spark", "cry"])


# ================================================================ 沈み船の水夫
def sailor(v):
    m = Mount(f"ds_{v}", "沈み船の水夫")
    hunch = 3.0 if v == "b" else 0.0
    biped_bones(m, (0, 14, 0), (0, 15, 0), (0, 26.2 - hunch * 0.5, -0.5 - hunch), (5.6, 23.6, 0), 3.2, 13)
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F.leg(m, b, "cloth", (s * 3.2, 13.5, 0), (s * 3.6, 7, -1), (s * 3.5, 2, 0), 3.4 if v != "b" else 3.0, foot_mat="sea")
    if v == "a":
        m.add("hips", ell("body", "cloth", (0, 14, 0), (5.4, 2.8, 3.6), segs=12, rings=3), core=True)
        m.add("torso_c", lof("torso", "cloth", [ysec(14.6, 10.8, 7.2), ysec(18.0, 12.0, 8.0), ysec(22.0, 12.4, 8.2), ysec(24.6, 11.0, 7.0), ysec(25.6, 6.0, 4.6)]))
        m.add("belly", ell("torso", "sea", (0, 17.6, -3.4), (3.6, 3.2, 1.6), segs=10, rings=3))
        m.add("belt", lof("torso", "leather", [ysec(15.0, 11.2, 7.6), ysec(16.2, 11.2, 7.6)]))
        m.add("buckle", ell("torso", "iron", (0, 15.6, -3.9), (1.0, 0.8, 0.3), segs=6, rings=1))
        for k in range(3):
            m.add(f"button{k}", ell("torso", "barn", (0.6, 19.4 + k * 1.8, -4.1), (0.35, 0.35, 0.2), segs=6, rings=1))
        m.add("collar", lof("torso", "cloth", [ysec(24.4, 11.4, 7.4), ysec(25.6, 8.0, 5.6)]))
        m.add("tear", cone("torso", "sea", P(-3.4, 18.6, -3.8), (0, -1, -0.3), 2.6, 1.0, sides=4))
        for k, (x, y, z) in enumerate(((3, 22, -3.8), (-4, 18, -3.8), (5.0, 24, 1), (-2, 25, 3.6), (4.6, 17, 2.6))):
            for j in range(2):
                m.add(f"barn{k}{j}", cone("torso", "barn", P(x + j * 0.8, y + j * 0.6, z), (x * 0.08, 0.2, z * 0.08), 1.4 - j * 0.3, 1.0 - j * 0.2, sides=6, rings=1))
        m.add("neck", ell("head", "sea", (0, 26.0, -0.4), (2.2, 1.8, 2.2), segs=8, rings=2))
        m.add("headc", ell("head", "sea", (0, 29, -0.6), (3.4, 3.6, 3.4), segs=12, rings=4))
        m.add("jaw", ell("head", "sea", (0, 27.2, -2.0), (2.6, 1.4, 2.0), segs=10, rings=2))
        m.add("brow", lof("head", "sea", [ysec(30.4, 6.4, 1.6, zc=-3.2), ysec(31.0, 6.0, 1.2, zc=-3.0)]))
        m.add("hat", lof("head", "cloth", [ysec(31.2, 9, 9), ysec(31.6, 9.2, 9.2), ysec(32.2, 6.4, 6.4), ysec(34.0, 5.0, 5.0), ysec(34.6, 3.6, 3.6)]))
        m.add("hatband", lof("head", "leather", [ysec(32.4, 6.6, 6.6), ysec(33.0, 6.2, 6.2)]))
        m.add("kelp_hat", tub("head", "kelp", [P(3.2, 32.0, 2.6), P(4.2, 29.0, 3.6), P(3.6, 26.0, 3.8)], [0.5, 0.4, 0.2], None, sides=4))
    elif v == "b":
        m.add("hips", ell("body", "cloth", (0, 14, 0.6), (5.0, 2.6, 3.4), segs=10, rings=2), core=True)
        m.add("torso_c", lof("torso", "cloth", [ysec(14.6, 10.0, 7.0), ysec(19.0, 11.4, 9.0, zc=0.8), ysec(23.0, 12.0, 9.6, zc=0.4), ysec(25.0, 7.0, 6.0, zc=-1.6)]))
        m.add("hump", ell("torso", "sea", (0, 22.6, 3.6), (4.6, 3.6, 3.0), segs=10, rings=3))
        m.add("belly", ell("torso", "sea", (0, 17.6, -3.4), (4.4, 4.0, 2.4), segs=10, rings=3))
        m.add("neck", ell("head", "sea", (0, 24.8, -2.6), (2.0, 1.6, 2.4), segs=8, rings=2))
        m.add("headc", ell("head", "sea", (0, 27.2, -4.0), (3.6, 3.2, 3.8), segs=10, rings=3))
        m.add("jaw", ell("head", "sea", (0, 25.4, -5.4), (2.8, 1.6, 2.2), segs=8, rings=2))
        m.add("hat", lof("head", "cloth", [ysec(29.2, 10.4, 10.4, zc=-3.4), ysec(30.0, 6.4, 6.4, zc=-3.6), ysec(32.0, 4.4, 4.4, zc=-3.8)]))
    else:
        m.add("hips", ell("body", "cloth", (0, 14, 0), (5.4, 2.8, 3.6), segs=10, rings=2), core=True)
        m.add("torso_c", lof("torso", "cloth", [ysec(14.6, 10.8, 7.2), ysec(19.0, 12.2, 8.2), ysec(23.0, 12.2, 8.0), ysec(25.4, 7.0, 5.0)]))
        for k, (x, y, z, h) in enumerate(((3.6, 22.4, -3.6, 4.0), (5.2, 24.2, 0.6, 5.4), (-4.4, 23.0, 2.2, 3.6), (-2.6, 17.0, -3.8, 3.0))):
            out = np.array([x, 0, z]) / max(1e-6, math.hypot(x, z))
            m.add(f"coral{k}", tub("torso", "coral", [P(x, y, z), P(x, y, z) + out * h * 0.4 + P(0, h * 0.6, 0), P(x, y, z) + out * h * 0.5 + P(0.6, h, 0)],
                                    [0.9, 0.6, 0.2], None, sides=6))
            m.add(f"coralb{k}", tub("torso", "coral", [P(x, y, z) + out * h * 0.3 + P(0, h * 0.5, 0), P(x, y, z) + out * h * 0.9 + P(0, h * 0.7, 0)], [0.45, 0.15],
                                     None, sides=5))
        for k in range(10):
            a = k * 0.62
            x, z = math.cos(a) * 5.6, math.sin(a) * 3.8
            m.add(f"barn{k}", cone("torso", "barn", P(x, 16.0 + (k % 4) * 2.4, z), (x * 0.1, 0.2, z * 0.1), 1.4, 1.0, sides=6, rings=1))
        for k in range(5):
            x = -4 + k * 2
            m.add(f"kelp{k}", tub("torso", "kelp", [P(x, 15.6, -3.8 + abs(x) * 0.1), P(x + 0.4, 12.0, -4.2), P(x - 0.2, 8.4, -3.8)], [0.6, 0.5, 0.2], [0.15] * 3,
                                   hint=(0, 0, 1), sides=4))
        m.add("neck", ell("head", "sea", (0, 26.0, -0.4), (2.2, 1.8, 2.2), segs=8, rings=2))
        m.add("headc", ell("head", "sea", (0, 29, -0.6), (3.4, 3.6, 3.4), segs=10, rings=3))
        m.add("shell_crown", tub("head", "barn", [P(0, 31.6, 0), P(0, 34.4, 0.4), P(0, 36.0, 1.2)], [2.8, 1.8, 0.3], None, sides=8))
        for k in range(5):
            a = k / 5 * math.tau
            m.add(f"spine{k}", cone("head", "coral", (math.cos(a) * 2.4, 32.4, math.sin(a) * 2.4), (math.cos(a), 1.2, math.sin(a)), 2.2, 0.4, sides=4))
        m.add("glowgill", ell("head", "glow", (0, 28.0, 2.8), (2.0, 0.6, 0.6), segs=8, rings=1))
    for s in (1, -1):
        eye(m, "head", (s * 1.3, 29.4 - (2.4 if v == "b" else 0), -3.6 - (3.4 if v == "b" else 0)), 0.7, (s * 0.2, 0, -1), (170, 255, 240), glow=True)
    if v == "b":
        F.arm(m, "arm_l", "sea", (5.6, 23.6, 0), (9, 16, -2), (7, 10, -5), 2.4, fist=2.8)
        F.arm(m, "arm_r", "sea", (-5.6, 23.6, 0), (-9, 21, -4), (-6, 23, -10), 2.4, fist=2.8)
    else:
        F.arm(m, "arm_l", "sea", (5.6, 23.6, 0), (8, 18, -2), (6, 14, -5), 2.6, fist=1.8)
        F.arm(m, "arm_r", "sea", (-5.6, 23.6, 0), (-8, 22, -4), (-5, 24, -9), 2.6, fist=1.8)
    if v == "a":
        for s, b, w in ((1, "arm_l", P(6, 14, -5)), (-1, "arm_r", P(-5, 24, -9))):
            for k in range(3):
                m.add(f"finger{b}{k}", cone(b, "sea", tuple(w + P((k - 1) * 0.7, -2.4, -0.4)), (0, -1, -0.2), 1.4, 0.4, sides=4))
            m.add(f"cuff{b}", ell(b, "cloth", tuple(P(s * 7.6, 18.6 if s > 0 else 22.4, -2 if s > 0 else -4)), (2.9, 1.0, 2.9), segs=8, rings=1))
    m.add("spear", tub("arm_r", "wood", [P(-5, 22.8, -1), P(-5, 22.8, -22)], [0.6, 0.6], None, sides=6))
    m.add("tip", cone("arm_r", "iron", P(-5, 22.8, -21.6), (0, 0, -1), 3.4, 1.0, sides=4, rings=1))
    m.pal = mats_()
    m.detail = lambda c, col, glow: (speckle(c, col, ["sea"], (30, 90, 80), density=0.08, size=0.8), glow)
    m.view = dict(center=(0, 19, -3), scale=8.0)
    return m


# ================================================================ 帯電毛玉
def puff(v):
    m = Mount(f"sp_{v}", "帯電毛玉")
    m.bone("body", "root", (0, 8, 0))
    c = P(0, 8, 0)
    if v == "a":
        m.add("core", ell("body", "down", c, (5.2, 4.8, 5.2), segs=12, rings=5), core=True)
        rng = np.random.default_rng(4)
        for k in range(26):
            d = rng.normal(size=3)
            d = d / np.linalg.norm(d)
            base = c + d * 4.4
            m.add(f"tuft{k}", cone("body", "down", tuple(base), tuple(d + rng.normal(size=3) * 0.15), 3.6 + (k % 3), 1.5, sides=5, rings=2, bend=tuple(rng.normal(size=3) * 0.6)))
            if k % 3 == 0:
                m.add(f"spark{k}", ell("body", "spark", tuple(base + d * (4.0 + k % 3)), (0.35, 0.35, 0.35), segs=4, rings=1))
        m.add("seed", ell("body", "copper", tuple(c + P(0, -4.6, 0)), (1.6, 1.2, 1.6), segs=8, rings=2))
        for s in (1, -1):
            eye(m, "body", (s * 1.6, 9.0, -4.4), 0.8, (s * 0.2, 0, -1), (240, 250, 255), glow=False)
    elif v == "b":
        m.add("core", ell("body", "spark", c, (2.4, 2.4, 2.4), segs=10, rings=4), core=True)
        m.add("shell", ell("body", "down", c, (7.2, 6.4, 7.2), segs=12, rings=5))
        for k in range(8):
            a = k / 8 * math.tau
            d = P(math.cos(a), 0.2 * (k % 2), math.sin(a))
            m.add(f"spike{k}", cone("body", "down", tuple(c + d * 6.4), tuple(d), 4.0, 2.0, sides=6, rings=2))
        for s in (1, -1):
            eye(m, "body", (s * 2.4, 9.4, -6.4), 1.1, (s * 0.2, 0, -1), (240, 250, 255), glow=False)
    else:
        m.add("core", ell("body", "storm", c, (4.6, 4.2, 4.6), segs=10, rings=4), core=True)
        rng = np.random.default_rng(9)
        for k in range(10):
            d = rng.normal(size=3)
            d = d / np.linalg.norm(d)
            m.add(f"cry{k}", cone("body", "cry", tuple(c + d * 3.6), tuple(d), 4.0 + (k % 3) * 1.2, 1.1, sides=5, rings=1))
        for k in range(14):
            d = rng.normal(size=3)
            d = d / np.linalg.norm(d)
            m.add(f"needle{k}", cone("body", "copper", tuple(c + d * 4.2), tuple(d), 3.0, 0.3, sides=4))
        m.add("fluff", ell("body", "down", tuple(c + P(0, 3.2, 0)), (4.0, 1.8, 4.0), segs=10, rings=2))
        for s in (1, -1):
            eye(m, "body", (s * 1.5, 8.8, -4.2), 0.8, (s * 0.2, 0, -1), (200, 230, 255), glow=True)
    m.pal = mats_()
    m.detail = None
    m.view = dict(center=(0, 8, 0), scale=15.0)
    return m


def now_tile(name, label):
    sys.path.insert(0, str(ROOT / "tools/audit"))
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import model_gallery as G
    G.TILE = 330
    f, d, a, gl = G.load(G.RES / "lowpoly" / f"{name}.json")
    return G.tile(f, a, gl, "", "").crop((0, 24, 330, 330)).resize((330, 306))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = {}
    for v in "abc":
        tiles = []
        for fn, label in ((sailor, "沈み船の水夫"), (puff, "帯電毛玉")):
            m = fn(v)
            size, atlas, glow = paint_mount(m, m.pal, m.detail, 256)
            tris = sum(len(f.verts) - 2 for f in m.F)
            cen = np.array(m.view["center"], float)
            for yaw, pitch in ((205, 10), (150, 6)):
                tiles.append((f"{label}  {tris}", view(m.F, atlas, glow, yaw, pitch, (330, 330), cen, m.view["scale"] * 0.95)))
            print(v, label, tris, flush=True)
        rows[v] = tiles
    for v, tiles in rows.items():
        cv = Image.new("RGB", (4 * 340 + 10, 364), (16, 16, 22))
        d = ImageDraw.Draw(cv)
        for i, (label, img) in enumerate(tiles):
            x = 10 + i * 340
            d.text((x, 6), label, font=voxel.font(15), fill=(150, 232, 255))
            cv.paste(img, (x, 30))
        cv.save(OUT / f"rough_{v}.png")
    cv = Image.new("RGB", (2 * 340 + 10, 340), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (n, label) in enumerate((("drowned_sailor", "今・沈み船の水夫"), ("static_puff", "今・帯電毛玉"))):
        d.text((10 + i * 340, 6), label, font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(now_tile(n, label), (10 + i * 340, 30))
    cv.save(OUT / "now.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
