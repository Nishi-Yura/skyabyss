# -*- coding: utf-8 -*-
"""はじまりの都の建物のラフ (一棟ずつ 3 案。docs/design/33 の 2.2)。

    python -I tools/rough/capital_buildings.py bell        # 大鐘楼 (語り部の家つき) の 3 案 → docs/review/town_roughs/bell_tower_sheet.png
絵は opening_sites.py と同じ描き方 (上から・斜め・人の目線)。採用した案は tools/gen_capital.py に写す。
座標: x = 東、z = 南、y = 上。地面の上面は y=-1、構造物の最初の段が y=0。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
sys.path.insert(0, str(ROOT / "tools/structure"))
import opening_sites as osite  # noqa: E402
from opening_sites import Grid  # noqa: E402

OUT = ROOT / "docs/review/town_roughs"


# ---------------------------------------------------------------- 部品
def box(g, x0, y0, z0, x1, y1, z1, s):
    g.fill(x0, y0, z0, x1, y1, z1, s)


def shell(g, x0, y0, z0, x1, y1, z1, wall, corner=None):
    box(g, x0, y0, z0, x1, y1, z0, wall)
    box(g, x0, y0, z1, x1, y1, z1, wall)
    box(g, x0, y0, z0, x0, y1, z1, wall)
    box(g, x1, y0, z0, x1, y1, z1, wall)
    if corner:
        for cx, cz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            box(g, cx, y0, cz, cx, y1, cz, corner)


def air(g, x0, y0, z0, x1, y1, z1):
    box(g, x0, y0, z0, x1, y1, z1, "air")


def gable(g, x0, x1, z0, z1, y, block="spruce_stairs", cap="spruce_slab[type=bottom]"):
    """東西に棟のある切妻 (opening_sites の _gable_roof と同じ)"""
    osite._gable_roof(g, x0, x1, z0, z1, y, block, cap)


def house(g, x0, z0, x1, z1, wall="spruce_planks", corner="stripped_spruce_log[axis=y]", h=3, door=("south", 0)):
    """語り部の家 (壁 h 段、切妻)。door = (向き, 中心からのずれ)"""
    box(g, x0, -1, z0, x1, -1, z1, "spruce_planks")
    shell(g, x0, 0, z0, x1, h, z1, wall, corner)
    gable(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, h + 1)
    cx = (x0 + x1) // 2 + door[1]
    if door[0] == "south":
        g.set(cx, 0, z1, "spruce_door[facing=north,half=lower,hinge=left,open=false,powered=false]")
        g.set(cx, 1, z1, "spruce_door[facing=north,half=upper,hinge=left,open=false,powered=false]")
    for x in range(x0 + 2, x1 - 1, 3):
        g.set(x, 1, z0, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 1, z1, "glass_pane[north=false,south=false,east=true,west=true]") if x != cx else None
    g.set(x0 + 1, 0, z0 + 1, "red_bed[facing=south,part=head,occupied=false]")
    g.set(x0 + 1, 0, z0 + 2, "red_bed[facing=south,part=foot,occupied=false]")
    g.set(x1 - 1, 0, z0 + 1, "lectern[facing=south,has_book=false,powered=false]")
    g.set(x1 - 1, 0, z1 - 1, "barrel[facing=up,open=false]")
    g.set(cx, h, (z0 + z1) // 2, "lantern[hanging=true]")


def smoke_fire(g, x, z):
    g.set(x, 0, z, "hay_block[axis=y]")
    g.set(x, 1, z, "campfire[facing=north,lit=true,signal_fire=true,waterlogged=false]")
    g.mark("smoke", x, 1, z)


def lantern_post(g, x, y, z, h=3):
    box(g, x, y, z, x, y + h - 1, z, "spruce_fence")
    g.set(x, y + h, z, "lantern[hanging=false]")


# ---------------------------------------------------------------- 大鐘楼 3 案
def bell_a(g: Grid, rng=None):
    """A「石の鐘楼」: 四角い石レンガの塔 9×9・高さ 24。一階は四方のアーチで吹き抜け、鐘はその天井に。頂は鐘の間と尖り屋根。東に語り部の家"""
    g.meta = {"rect": [-6, -6, 14, 7], "level": True}
    x0, z0, x1, z1 = -4, -4, 4, 4
    box(g, x0, -1, z0, x1, -1, z1, "polished_deepslate")
    shell(g, x0, 0, z0, x1, 23, z1, "stone_bricks", "deepslate_bricks")
    for (ax0, az0, ax1, az1) in ((-1, z0, 1, z0), (-1, z1, 1, z1), (x0, -1, x0, 1), (x1, -1, x1, 1)):
        air(g, ax0, 0, az0, ax1, 2, az1)
    box(g, x0 + 1, 3, z0 + 1, x1 - 1, 3, z1 - 1, "spruce_planks")
    g.set(0, 2, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 2, 0)
    for y in (8, 13, 18):
        for (ax0, az0, ax1, az1) in ((0, z0, 0, z0), (0, z1, 0, z1), (x0, 0, x0, 0), (x1, 0, x1, 0)):
            air(g, ax0, y, az0, ax1, y + 1, az1)
    # 鐘の間 (y 20〜22 は四方が開く)
    for (ax0, az0, ax1, az1) in ((-2, z0, 2, z0), (-2, z1, 2, z1), (x0, -2, x0, 2), (x1, -2, x1, 2)):
        air(g, ax0, 20, az0, ax1, 22, az1)
    box(g, x0 + 1, 19, z0 + 1, x1 - 1, 19, z1 - 1, "spruce_planks")
    g.set(0, 22, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    # 尖り屋根 (階段で段々に)
    for i in range(5):
        y = 24 + i
        box(g, x0 + i, y, z0 + i, x1 - i, y, z1 - i, "deepslate_tiles" if i < 4 else "deepslate_tile_slab[type=bottom]")
        if i >= 1:
            air(g, x0 + i + 1, y, z0 + i + 1, x1 - i - 1, y, z1 - i - 1)
    g.set(0, 29, 0, "lantern[hanging=false]")
    for dx, dz in ((-6, 6), (6, 6)):
        lantern_post(g, dx, 0, dz, 2)
    house(g, 6, -5, 13, 4)
    smoke_fire(g, 9, 6)
    g.mark("door", 9, 0, 4)
    g.mark("stand", 0, 0, 6)


def bell_b(g: Grid, rng=None):
    """B「鯨骨の鐘楼」: 深層岩の体に鯨の骨 (骨ブロック) の肋を四隅に立てた塔 11×11・高さ 26。骨の肋が屋根の上で合わさり、鐘は骨の梁に吊る。国の印の骨"""
    g.meta = {"rect": [-7, -7, 15, 8], "level": True}
    x0, z0, x1, z1 = -5, -5, 5, 5
    box(g, x0, -1, z0, x1, -1, z1, "polished_deepslate")
    shell(g, x0 + 1, 0, z0 + 1, x1 - 1, 21, z1 - 1, "deepslate_bricks")
    for (ax0, az0, ax1, az1) in ((-1, z0 + 1, 1, z0 + 1), (-1, z1 - 1, 1, z1 - 1), (x0 + 1, -1, x0 + 1, 1), (x1 - 1, -1, x1 - 1, 1)):
        air(g, ax0, 0, az0, ax1, 3, az1)
    box(g, x0 + 2, 4, z0 + 2, x1 - 2, 4, z1 - 2, "spruce_planks")
    g.set(0, 3, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 3, 0)
    # 四隅の骨の肋: 外側に立ち、上で内へ曲がって頂で合わさる
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for y in range(0, 22):
            g.set(sx * 5, y, sz * 5, "bone_block[axis=y]")
        for i in range(1, 6):
            g.set(sx * (5 - i), 21 + i, sz * (5 - i), "bone_block[axis=y]")
    box(g, -1, 27, -1, 1, 27, 1, "bone_block[axis=y]")
    g.set(0, 28, 0, "lantern[hanging=false]")
    # 骨の梁 (一階の上、鐘の上の骨)
    box(g, x0, 5, 0, x1, 5, 0, "bone_block[axis=x]")
    box(g, 0, 5, z0, 0, 5, z1, "bone_block[axis=z]")
    for y in (9, 14, 19):
        for (ax0, az0, ax1, az1) in ((0, z0 + 1, 0, z0 + 1), (0, z1 - 1, 0, z1 - 1), (x0 + 1, 0, x0 + 1, 0), (x1 - 1, 0, x1 - 1, 0)):
            air(g, ax0, y, az0, ax1, y + 1, az1)
    box(g, x0 + 1, 22, z0 + 1, x1 - 1, 22, z1 - 1, "deepslate_tile_slab[type=bottom]")
    house(g, 7, -5, 14, 4, wall="stone_bricks", corner="bone_block[axis=y]")
    smoke_fire(g, 10, 6)
    g.mark("door", 10, 0, 4)
    g.mark("stand", 0, 0, 7)


def bell_c(g: Grid, rng=None):
    """C「段の鐘楼」: 三段の塔 (13 → 9 → 5)。段ごとに庇と灯り。一段目が鐘の間 (鐘は梁に)、二段目が語り部の部屋 (中に階段)。家は塔の中"""
    g.meta = {"rect": [-8, -8, 8, 8], "level": True}
    tiers = ((-6, 6, 0, 6), (-4, 4, 7, 12), (-2, 2, 13, 18))
    box(g, -6, -1, -6, 6, -1, 6, "stone_bricks")
    for (x0, x1, y0, y1) in tiers:
        shell(g, x0, y0, x0, x1, y1, x1, "stone_bricks" if y0 == 0 else "spruce_planks", "stripped_spruce_log[axis=y]")
        # 庇 (段の上に一回り大きい板)
        box(g, x0 - 1, y1 + 1, x0 - 1, x1 + 1, y1 + 1, x1 + 1, "spruce_slab[type=bottom]")
        air(g, x0, y1 + 1, x0, x1, y1 + 1, x1) if y0 < 13 else None
        for cx, cz in ((x0 - 1, x0 - 1), (x1 + 1, x0 - 1), (x0 - 1, x1 + 1), (x1 + 1, x1 + 1)):
            g.set(cx, y1, cz, "lantern[hanging=true]")
    # 一段目: 四方のアーチ、鐘
    for (ax0, az0, ax1, az1) in ((-1, -6, 1, -6), (-1, 6, 1, 6), (-6, -1, -6, 1), (6, -1, 6, 1)):
        air(g, ax0, 0, az0, ax1, 2, az1)
    box(g, -5, 6, 0, 5, 6, 0, "stripped_spruce_log[axis=x]")
    g.set(0, 5, 0, "bell[attachment=ceiling,facing=north,powered=false]")
    g.mark("bell", 0, 5, 0)
    # 二段目: 語り部の部屋 (床・寝台・書見台)。中の階段 (北東の隅)
    box(g, -3, 6, -3, 3, 6, 3, "spruce_planks")
    for i in range(6):
        g.set(4, i, 4 - i if i <= 4 else -1, "spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]") if i <= 4 else None
    g.set(-3, 7, -3, "red_bed[facing=south,part=head,occupied=false]")
    g.set(-3, 7, -2, "red_bed[facing=south,part=foot,occupied=false]")
    g.set(3, 7, -3, "lectern[facing=south,has_book=false,powered=false]")
    for x in (-2, 2):
        g.set(x, 9, -4, "glass_pane[north=false,south=false,east=true,west=true]")
        g.set(x, 9, 4, "glass_pane[north=false,south=false,east=true,west=true]")
    # 三段目: 見晴らしと頂の灯り
    box(g, -1, 19, -1, 1, 19, 1, "deepslate_tiles")
    g.set(0, 20, 0, "lantern[hanging=false]")
    smoke_fire(g, 0, 8)
    g.mark("door", 0, 0, 6)
    g.mark("stand", 0, 0, 8)


SUBJECTS = {
    "bell": {
        "title": "大鐘楼（語り部の家つき）",
        "options": {"a": bell_a, "b": bell_b, "c": bell_c},
        "notes": {"a": "A 石の鐘楼（別棟の家）", "b": "B 鯨骨の鐘楼（骨の肋）", "c": "C 段の鐘楼（家は塔の中）"},
        "eye": ((0, 1.6, 22), (0, 8, 0)),
    },
}


def render(subject: str):
    sub = SUBJECTS[subject]
    cols = []
    out = OUT / subject
    out.mkdir(parents=True, exist_ok=True)
    for key, fn in sub["options"].items():
        g = Grid()
        fn(g)
        d = osite.to_dump("shelter", g, margin=4)
        osite.srender._colors = lambda dd: osite.colors_of(dd).astype(osite.np.int16)
        top = osite.srender.top(d, scale=10)
        iso = osite.srender.iso(d, step=1)
        f = max(1, 460 // iso.width)
        iso = iso.resize((iso.width * f, iso.height * f), osite.Image.NEAREST)
        if iso.width > 700:
            iso = iso.resize((700, int(iso.height * 700 / iso.width)), osite.Image.NEAREST)
        (ex, ey, ez), (ax, ay, az) = sub["eye"]
        dx, dy, dz = ax - ex, ay - ey, az - ez
        yaw = math.degrees(math.atan2(-dx, dz)) % 360
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        eye = osite.eye_view(d, (ex, ey, ez), yaw, pitch, far=60)
        top.save(out / f"{key}_top.png")
        iso.save(out / f"{key}_iso.png")
        eye.save(out / f"{key}_eye.png")
        cols.append((sub["notes"][key], [top, iso, eye]))
    sh = osite.sheet(sub["title"] + " 3 案（上から・斜め・人の目線）", cols, "ブロックの配置をそのまま描いた図。採用案を tools/gen_capital.py に写す。")
    path = OUT / f"{subject}_sheet.png"
    sh.save(path)
    print("wrote", path)


if __name__ == "__main__":
    render(sys.argv[1] if len(sys.argv) > 1 else "bell")
