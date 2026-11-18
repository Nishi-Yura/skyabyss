#!/usr/bin/env python3
"""範囲攻撃の敷き詰めの模型 (2026-11-13 の声「心臓の範囲攻撃、別モデル用意したら？ モデル残るからわかりにくいのと、範囲攻撃なのに間開いてて、
予告の見た目と合ってない」)。

    python -X utf8 -I tools/gen/mobs/attack_props/build_area_props.py [名前 ...] [--no-install]

心臓・領域の予告 (combat/AttackLook の付いた Telegraph) の打つ瞬間に、予告の形をすき間なく埋めるように並べる一区画ぶんの模型。
並べるのはクライアント (client/combat/AreaFx): 六角の格子 (間隔 1.6 ブロック) の各点に一つずつ、向きと大きさを少し変えて置く。
格子の点から一番遠い所は 1.6/√3 ≒ 0.92 ブロック (14.8 単位) なので、どの模型も床の割れ板が半径 15〜16 単位まで届くように作る
(隣と少し重なって、床に穴が見えない)。高さは 1.5 ブロック前後まで (予告の中の自分や味方を隠さない)。
原点は床の真ん中。床より下 (-6 まで) にも伸ばしておく (床から突き出る動きで下が見えない)。

属性ごとに二つ (a・b を交互に置く):
  area_stone_*  石・骨 (岩鯨・石の領域): めくれ上がる岩の板と、突き出る骨の棘
  area_water_*  潮 (潮殻・殻の領域): 割れた床から噴く水と泡の輪、貝の欠片
  area_storm_*  雷 (雷翼・雷の領域): 黒い岩を割って伸びる光る晶と、這う稲妻
  area_ember_*  熾 (余燼・炉の領域): 割れた玄武岩の殻の下の熔岩と、跳ねる火
確かめの絵は docs/review/attack_props/area_sheet.png。
"""
from __future__ import annotations

import math
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
import build_props as bp  # noqa: E402

REVIEW = ROOT / "docs/review/attack_props"
R = 15.5  # 床の割れ板が届く半径 (単位)


def plates(m, mat, n, seed, lift=(1.6, 4.0), r_in=(3.0, 6.0), mat2=None):
    """床の割れ板: 真ん中のまわりに、内側の縁がめくれ上がった板を n 枚。外の縁は半径 R に届き、床へ沈む。
    板は外ほど広い台形で、ねじれ (横の傾き) と欠けで一枚ずつ違える"""
    rng = np.random.default_rng(seed)
    for k in range(n):
        a = (k + rng.uniform(-0.2, 0.2)) / n * math.tau
        ca, sa = math.cos(a), math.sin(a)
        ri = rng.uniform(*r_in)
        ro = R + rng.uniform(-0.5, 1.0)
        hy = rng.uniform(*lift)
        p0 = np.array([ca * ri, hy, sa * ri])
        p1 = np.array([ca * (ri * 0.6 + ro * 0.4), hy * 0.5, sa * (ri * 0.6 + ro * 0.4)])
        p2 = np.array([ca * ro, -1.2, sa * ro])
        w = ro * math.tau / n * 0.5
        roll = rng.uniform(-0.35, 0.35)
        hint = (-sa * math.cos(roll), math.sin(roll), ca * math.cos(roll))
        m.add(f"plate{k}", tub("body", mat2 if (mat2 and k % 3 == 1) else mat, [p0, p1, p2], [w * 0.45, w * 0.8, w * 1.05], [0.9, 1.1, 1.2],
                               hint=hint, sides=4, phase=45))
        # 割れた縁の欠け
        m.add(f"chip{k}", ell("body", mat, p0 + np.array([0, 0.3, 0]), (rng.uniform(1.0, 1.6), rng.uniform(0.6, 1.0), rng.uniform(1.0, 1.6)),
                              segs=5, rings=1, ry=rng.uniform(0, 180)))


def pit(m, mat, r=5.0, depth=1.0):
    """真ん中の割れ口 (板の下を埋める暗い面。下から何も見えない)"""
    m.add("pit", ell("body", mat, (0, -depth, 0), (r, 2.4, r), segs=10, rings=2))


def pebbles(m, mat, n, seed, rmin=6.0, rmax=R):
    rng = np.random.default_rng(seed)
    for k in range(n):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(rmin, rmax)
        m.add(f"peb{k}", ell("body", mat, (math.cos(a) * r, rng.uniform(0.2, 1.0), math.sin(a) * r), (rng.uniform(0.8, 1.6), rng.uniform(0.6, 1.1), rng.uniform(0.8, 1.6)),
                             segs=5, rings=1, ry=rng.uniform(0, 180)))


# ---------------------------------------------------------------- 石・骨
def a_stone_a(m):
    pit(m, "basalt")
    plates(m, "rock", 6, 1, mat2="basalt")
    # 割れ口から突き出る骨の棘 (大一本・小二本、外へ傾く)
    for k, (x, z, h, r, lx, lz) in enumerate(((0.5, -0.5, 24, 3.6, 0.1, -0.08), (-3.4, 2.4, 15, 2.6, -0.5, 0.35), (3.0, 3.2, 11, 2.2, 0.45, 0.4))):
        m.add(f"spike{k}", cone("body", "bone", (x, -6, z), (lx, 1, lz), h + 6, r, sides=6, rings=3, bend=(lx * 3, 0, 1.0)))
    pebbles(m, "rock", 7, 2)


def a_stone_b(m):
    pit(m, "basalt", r=4.4)
    plates(m, "rock", 5, 3, lift=(4.0, 8.0), mat2="basalt")
    # 岩の牙: 板の継ぎ目から短い岩の棘が三本、骨の棘が一本
    for k, (x, z, h, r, lx, lz, mat) in enumerate(((2.4, -1.6, 17, 3.2, 0.35, -0.2, "rock"), (-2.6, -2.0, 13, 2.8, -0.4, -0.3, "basalt"),
                                                   (-0.4, 3.0, 20, 2.9, -0.05, 0.3, "bone"), (6.5, 4.5, 8, 2.0, 0.5, 0.3, "rock"))):
        m.add(f"fang{k}", cone("body", mat, (x, -6, z), (lx, 1, lz), h + 6, r, sides=5, rings=2))
    pebbles(m, "rock", 8, 4)


# ---------------------------------------------------------------- 潮
def a_water_a(m):
    pit(m, "water", r=5.6)
    plates(m, "shell", 5, 5, lift=(2.0, 4.0), mat2="rock")
    # 割れ口から噴く水: 太い噴き上げと、てっぺんの泡の冠、まわりへ落ちる水の舌
    m.add("jet", tub("body", "water", [np.array(p) for p in ((0, -4, 0), (0.4, 8, 0.2), (0, 18, 0), (-0.3, 22, 0))], [5.2, 4.2, 3.6, 1.2], None, sides=10))
    m.add("crown", ell("body", "foam", (0, 19.0, 0), (4.4, 2.0, 4.4), segs=10, rings=2))
    # てっぺんで砕けて外へ散る水の舌 (短く、外へ倒れて下がる)
    for k in range(8):
        a = k / 8 * math.tau + 0.3
        ca, sa = math.cos(a), math.sin(a)
        m.add(f"crest{k}", cone("body", "foam", (ca * 3.4, 19.5, sa * 3.4), (ca, 0.7, sa), 6.0, 1.5, sides=5, rings=2, bend=(ca * 1.5, -2.6, sa * 1.5)))
    # 根元で横へ跳ねる水の輪
    for k in range(7):
        a = k / 7 * math.tau
        ca, sa = math.cos(a), math.sin(a)
        m.add(f"splash{k}", cone("body", "water", (ca * 5.0, 0.5, sa * 5.0), (ca, 1.0, sa), 5.5, 1.6, sides=5, rings=2, bend=(ca * 1.2, -0.6, sa * 1.2)))
    m.add("foamring", ell("body", "foam", (0, 0.8, 0), (R - 1.0, 1.2, R - 1.0), segs=14, rings=1))
    rng = np.random.default_rng(6)
    for k in range(7):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(5, 13)
        m.add(f"drop{k}", ell("body", "foam", (math.cos(a) * r, rng.uniform(8, 20), math.sin(a) * r), (0.9, 1.3, 0.9), segs=5, rings=2))


def a_water_b(m):
    pit(m, "water", r=5.0)
    plates(m, "rock", 6, 7, lift=(2.5, 5.0), mat2="shell")
    # 二本の細い噴き上げと、突き出る貝の棘
    for k, (x, z, h) in enumerate(((-2.4, -1.2, 17), (2.8, 1.6, 12))):
        m.add(f"jet{k}", tub("body", "water", [np.array([x, -4, z]), np.array([x * 1.1, h * 0.6, z * 1.1]), np.array([x * 1.2, h, z * 1.2])], [3.0, 2.4, 0.6], None, sides=8))
        for j in range(5):
            a = j / 5 * math.tau + k
            ca, sa = math.cos(a), math.sin(a)
            m.add(f"spray{k}{j}", cone("body", "foam", (x * 1.2 + ca * 1.0, h - 2.5, z * 1.2 + sa * 1.0), (ca, 0.9, sa), 4.0, 1.0, sides=4, rings=2,
                                       bend=(ca * 1.0, -1.8, sa * 1.0)))
    m.add("shell", cone("body", "shell", (0.6, -6, 3.6), (0.15, 1, 0.3), 19, 2.8, sides=7, rings=3, bend=(0, 0, 1.2)))
    for j in range(2):
        m.add(f"rib{j}", ell("body", "coral", (0.6 + 0.15 * (4 + j * 5) / 1, -2 + (j + 1) * 5, 3.6 + 0.3 * (4 + j * 5)), (2.3 - j * 0.5, 0.6, 2.3 - j * 0.5),
                             segs=8, rings=1))
    m.add("foamring", ell("body", "foam", (0, 0.7, 0), (R - 1.5, 1.0, R - 1.5), segs=14, rings=1))
    pebbles(m, "foam", 6, 8, rmin=8)


# ---------------------------------------------------------------- 雷
def a_storm_a(m):
    pit(m, "cry", r=4.6, depth=0.6)
    # 割れ板は明るい岩 (心臓の部屋の暗い床でも形が読める)
    plates(m, "rock", 6, 9, lift=(2.5, 5.0), mat2="basalt")
    # 光る晶の束 (大一本・中二本) と、板の継ぎ目を這う稲妻
    for k, (x, z, h, r, lx, lz) in enumerate(((0, 0, 22, 4.2, 0.05, 0.05), (3.4, -2.2, 15, 3.2, 0.5, -0.3), (-3.0, 2.6, 13, 3.0, -0.45, 0.4))):
        m.add(f"cry{k}", cone("body", "cry", (x, -6, z), (lx, 1, lz), h + 6, r, sides=6, rings=2))
    rng = np.random.default_rng(10)
    for k in range(4):
        a = k / 4 * math.tau + 0.5
        pts = []
        for t in np.linspace(0, 1, 5):
            rr = 4 + t * (R - 5)
            aa = a + rng.uniform(-0.25, 0.25)
            pts.append(np.array([math.cos(aa) * rr, 1.6 + rng.uniform(0, 1.2), math.sin(aa) * rr]))
        m.add(f"arc{k}", tub("body", "bolt", pts, [0.8, 0.7, 0.6, 0.5, 0.3], None, sides=4))
    pebbles(m, "basalt", 6, 11)


def a_storm_b(m):
    pit(m, "cry", r=4.0, depth=0.6)
    plates(m, "rock", 5, 12, lift=(3.0, 6.0), mat2="basalt")
    # 斜めに割り出る晶の刃 (四本、風車のように外へ倒れる)
    for k in range(4):
        a = k / 4 * math.tau + 0.2
        ca, sa = math.cos(a), math.sin(a)
        h = (17, 11, 15, 9)[k]
        m.add(f"blade{k}", cone("body", "cry", (ca * 2.0, -6, sa * 2.0), (ca * 0.55, 1, sa * 0.55), h + 6, 3.2, sides=4, rings=2))
    m.add("core", ell("body", "bolt", (0, 1.5, 0), (2.6, 2.6, 2.6), segs=8, rings=3))
    rng = np.random.default_rng(13)
    for k in range(5):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(5, 13)
        m.add(f"spark{k}", ell("body", "bolt", (math.cos(a) * r, rng.uniform(3, 12), math.sin(a) * r), (0.6, 0.6, 0.6), segs=4, rings=1))


# ---------------------------------------------------------------- 熾
def a_ember_a(m):
    pit(m, "magma", r=6.0, depth=0.4)
    plates(m, "basalt", 6, 14, lift=(2.5, 5.0), mat2="ash")
    # 噴き上がる熔岩の舌と、跳ねる火の粒
    m.add("gush", tub("body", "magma", [np.array(p) for p in ((0, -4, 0), (0.6, 6, 0.3), (0.2, 14, -0.2), (-0.4, 19, 0))], [4.8, 3.8, 2.6, 0.6], None, sides=9))
    for k in range(5):
        a = k / 5 * math.tau + 0.6
        ca, sa = math.cos(a), math.sin(a)
        m.add(f"tongue{k}", cone("body", "magma", (ca * 2.0, 10, sa * 2.0), (ca, 0.5, sa), 8, 1.4, sides=4, rings=2, bend=(ca * 1.6, -3.0, sa * 1.6)))
    rng = np.random.default_rng(15)
    for k in range(7):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(4, 12)
        m.add(f"ember{k}", ell("body", "magma", (math.cos(a) * r, rng.uniform(4, 16), math.sin(a) * r), (0.8, 0.8, 0.8), segs=5, rings=1))
    # 板の継ぎ目から漏れる熔岩
    for k in range(6):
        a = (k + 0.5) / 6 * math.tau
        m.add(f"seam{k}", tub("body", "magma", [np.array([math.cos(a) * 5, 0.4, math.sin(a) * 5]), np.array([math.cos(a) * 13, 0.2, math.sin(a) * 13])],
                                [0.9, 0.5], None, sides=4))


def a_ember_b(m):
    pit(m, "magma", r=5.0, depth=0.4)
    plates(m, "basalt", 5, 16, lift=(3.5, 7.0), mat2="ash")
    # 熔岩の小さな柱と、殻をかぶった火の塊
    m.add("col", tub("body", "magma", [np.array(p) for p in ((0, -4, 0), (0.4, 8, 0), (0, 15, 0.3))], [3.6, 3.0, 1.4], None, sides=8))
    for k in range(5):
        a = k / 5 * math.tau
        m.add(f"crust{k}", ell("body", "basalt", (math.cos(a) * 3.0, 2 + k * 2.4, math.sin(a) * 3.0), (2.0, 1.8, 2.0), segs=6, rings=2, ry=k * 37))
    for k, (x, z) in enumerate(((-6.5, 3.0), (5.5, -5.0))):
        m.add(f"lump{k}", ell("body", "magma", (x, 2.0, z), (2.4, 2.0, 2.4), segs=7, rings=2))
        m.add(f"lumpc{k}", ell("body", "basalt", (x + 0.6, 3.2, z - 0.4), (1.8, 1.0, 1.6), segs=6, rings=1))
    pebbles(m, "ash", 6, 17)


BUILDERS = {"area_stone_a": a_stone_a, "area_stone_b": a_stone_b, "area_water_a": a_water_a, "area_water_b": a_water_b,
            "area_storm_a": a_storm_a, "area_storm_b": a_storm_b, "area_ember_a": a_ember_a, "area_ember_b": a_ember_b}


def build(name):
    m = Mount(name, name)
    m.bone("body", "root", (0, 0, 0))
    BUILDERS[name](m)
    m.pal = dict(mats=bp.mats(), glow_mats=list(bp.GLOW))
    m.detail = None
    m.hint = 256
    return m


def install(m, name, size, atlas, glow):
    """模型と絵だけを入れる (実体の型を持たないので geo・アニメは要らない。描くのは client/combat/AreaFx)"""
    out = blp.WORK / m.id
    _, has_glow = blp.export_named(m, name, size, atlas, glow, out)
    cmd = [sys.executable, str(ROOT / "tools/install/install_lowpoly.py"), name, "--mesh", str(out / f"{name}_mesh.json"),
           "--atlas", str(out / f"{name}_atlas.png")]
    if has_glow:
        cmd += ["--glow", str(out / f"{name}_glowmask.png")]
    subprocess.check_call(cmd, cwd=str(ROOT))
    # install_lowpoly は骨だけの geo も書くが、実体の無い模型には要らない
    for g in (ROOT / "src/main/resources/assets/skyabyss/geo/entity").glob(name + "_lowpoly*.geo.json"):
        g.unlink()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    names = args or list(BUILDERS)
    REVIEW.mkdir(parents=True, exist_ok=True)
    tiles = []
    for name in names:
        m = build(name)
        size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, m.hint)
        if "--no-install" not in sys.argv:
            install(m, name, size, atlas, glow)
        c = np.array([0, 6, 0])
        sc = 220 / 36
        im = view(m.F, atlas, glow, 210, 28, (300, 300), c, sc, ground=False)
        tiles.append((name, im, sum(len(f.verts) - 2 for f in m.F)))
        print(name, "tris", tiles[-1][2], flush=True)
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    cv = Image.new("RGB", (cols * 310 + 10, rows * 334 + 10), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    for i, (name, im, tris) in enumerate(tiles):
        x, y = 10 + (i % cols) * 310, 10 + (i // cols) * 334
        d.text((x, y), f"{name}  {tris} tris", font=voxel.font(15), fill=(150, 232, 255))
        cv.paste(im, (x, y + 24))
    cv.save(REVIEW / "area_sheet.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
