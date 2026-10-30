# -*- coding: utf-8 -*-
"""終章の結末の語り (巡礼者の座で結末を選んだとき。docs/design/50 の 4.4)。pxkit / parts で描く。三つの語り × 5 枚:
  finale_end_calm    鎮める (心臓を癒し、引き合いを戻す。月が遠のき空が晴れ、巨神の歌が戻る)
  finale_end_break   壊す (心臓を壊し、巡礼者を封じる。空に裂け目、巨神は深く眠る)
  finale_end_accept  受け入れる (巡礼者を地上に招く。翼が昼の空を巡り、都の鐘が鳴りやむ)

  python -X utf8 tools/story/pixel/finale_end.py

出力は docs/review/story_pixel/finale_end_<結末>_<絵>.png (240x135) と _x4.png。ゲームへは install_reels.py (finale_end_reels)。
"""
import math

import numpy as np

import pxkit as K
import parts as P
from opening import moon

OUT = K.ROOT / "docs/review/story_pixel"
GOLD = ["#0c1024", "#2a3460", "#52649c", "#8ca4d4", "#c4d4f0", "#f4f4f8", "#ffffff", "#fff0c8", "#f4d890", "#dcb250", "#a87c34", "#5c4424",
        "#2a2a34", "#5a5c6c", "#9a9cac", "#d8d0c0"]
DUSK = ["#08060e", "#1a1028", "#3a2448", "#5c3c6c", "#8c6c9c", "#c4a4c8", "#f4e8f0", "#ffffff", "#f4c890", "#c87850", "#7c3c3c", "#3c1c24",
        "#24242e", "#4a4a58", "#8a8a98", "#c8b4a0"]


def mesh(c, name, x, y, w, h, yaw, pitch=8, tint=None, silhouette=None):
    img, _ = K.render_mesh(name, (w, h), yaw=yaw, pitch=pitch, tint=None if tint is None else tuple(int(v * 255) for v in tint),
                           silhouette=silhouette)
    c.sprite(img, int(x - w / 2), int(y - h / 2))


def clouds(c, y0, rows, seed, color=(248, 246, 244), alpha=0.85):
    rng = np.random.default_rng(seed)
    for row in range(rows):
        for _ in range(30):
            c.disc(rng.uniform(-10, 250), y0 + row * 11 + rng.uniform(-3, 3), rng.uniform(6, 14) + row * 2, color, alpha=alpha)


def seat(c, x, y, s=1.0, stone=(236, 232, 226), gold=(220, 178, 80)):
    """巡礼者の座 (白い椅子と金の輪) と小さな人影"""
    c.poly([(x - 14 * s, y), (x + 14 * s, y), (x + 12 * s, y - 4 * s), (x - 12 * s, y - 4 * s)], stone)
    c.poly([(x - 8 * s, y - 4 * s), (x + 8 * s, y - 4 * s), (x + 7 * s, y - 34 * s), (x - 7 * s, y - 34 * s)], stone)
    for a in np.linspace(0, math.tau, 40):
        c.disc(x + math.cos(a) * 11 * s, y - 34 * s + math.sin(a) * 11 * s, 1.2 * s, gold)


def person(c, x, y, color=(24, 24, 34), s=1.0):
    c.poly([(x - 1.5 * s, y), (x + 1.5 * s, y), (x + 1.5 * s, y - 7 * s), (x - 1.5 * s, y - 7 * s)], color)
    c.disc(x, y - 8.5 * s, 1.6 * s, color)


def heart(c, x, y, r, color=(200, 70, 60), glow=(120, 40, 40), cracked=False):
    c.glow(x, y, r * 3, glow, 1.0)
    c.disc(x, y, r, color)
    c.disc(x - r * 0.3, y - r * 0.3, r * 0.35, tuple(min(255, int(v * 1.3)) for v in color))
    if cracked:
        P.bolt(c, x - r, y - r * 0.6, x + r * 0.8, y + r * 0.7, 3, color=(20, 10, 14), glow=(90, 30, 30), width=1)
        P.bolt(c, x + r * 0.2, y - r, x - r * 0.4, y + r, 8, color=(20, 10, 14), glow=(90, 30, 30), width=1)


def town(c, base, color, tower=(120, 70), lit=None):
    rng = np.random.default_rng(11)
    x = 0
    while x < 240:
        w = rng.integers(8, 16)
        h = rng.integers(6, 16)
        c.poly([(x, base), (x + w, base), (x + w, base - h), (x + w / 2, base - h - 5), (x, base - h)], color)
        if lit is not None and rng.random() < 0.6:
            c.disc(x + w / 2, base - h / 2, 1.0, lit)
        x += w + rng.integers(0, 3)
    bx, top = tower
    c.poly([(bx - 6, base), (bx + 6, base), (bx + 5, top), (bx - 5, top)], color)
    c.poly([(bx - 8, top), (bx + 8, top), (bx, top - 12)], color)
    c.poly([(0, base), (240, base), (240, 135), (0, 135)], color)


# ---------------------------------------------------------------- 鎮める
def calm1():
    c = K.Canvas()
    c.vgrad([(0, (120, 150, 210)), (0.6, (250, 236, 200)), (1, (240, 240, 245))])
    clouds(c, 96, 4, 21)
    seat(c, 120, 92)
    person(c, 120, 94)
    # 座から地の底へ降りる金の光
    for k in range(-2, 3):
        P.line(c, 120 + k, 92, 120 + k * 3, 135, (255, 230, 160))
    c.glow(120, 120, 30, (255, 220, 150), 0.8)
    return c.quantize(GOLD)


def calm2():
    c = K.Canvas()
    c.vgrad([(0, (20, 16, 24)), (1, (50, 30, 30))])
    for k in range(-1, 2):
        P.line(c, 120 + k, 0, 120 + k, 62, (255, 230, 160))
    heart(c, 120, 78, 16, color=(220, 110, 80), glow=(160, 90, 50))
    P.ripples(c, 120, 78, 4, (255, 210, 150), spread=12)
    return c.quantize(GOLD)


def calm3():
    c = K.Canvas()
    c.vgrad([(0, (110, 160, 220)), (0.8, (220, 230, 240)), (1, (240, 236, 220))])
    c.ridge(110, 5, 0.05, 4, (80, 100, 90), rough=0.6)
    c.poly([(0, 114), (240, 114), (240, 135), (0, 135)], (70, 90, 80))
    for name, x, y, w, h, yaw in (("stone_whale_far", 52, 60, 110, 52, 120), ("tide_shell", 100, 100, 80, 40, 200),
                                  ("thunder_wing_far", 170, 46, 110, 56, 220), ("ember_titan_far", 206, 98, 80, 44, 250)):
        mesh(c, name, x, y, w, h, yaw, tint=(0.85, 0.9, 1.0))
        P.ripples(c, x, y - h * 0.3, 3, (255, 244, 210), spread=8)
    return c.quantize(GOLD)


def calm4():
    c = K.Canvas()
    c.vgrad([(0, (90, 150, 230)), (0.85, (200, 220, 245)), (1, (240, 240, 245))])
    moon(c, 196, 22, 7, base=(240, 240, 250), shade=(200, 205, 220))
    c.glow(40, 30, 40, (255, 240, 200), 0.6)
    town(c, 118, (70, 72, 90), lit=None)
    P.ripples(c, 120, 74, 3, (255, 240, 200), spread=10)
    return c.quantize(GOLD)


def calm5():
    c = K.Canvas()
    c.vgrad([(0, (140, 170, 220)), (0.6, (252, 240, 210)), (1, (250, 250, 250))])
    clouds(c, 100, 3, 25)
    c.glow(120, 60, 70, (255, 240, 200), 0.9)
    for i, x in enumerate((60, 100, 140, 180)):
        mesh(c, "npc_guest_moon_past%d" % (i + 1), x, 82, 34, 46, 190 + (i - 1.5) * 12, tint=(1.0, 1.0, 1.0))
        c.glow(x, 70, 16, (255, 244, 220), 0.5)
    return c.quantize(GOLD)


# ---------------------------------------------------------------- 壊す
def break1():
    c = K.Canvas()
    c.vgrad([(0, (40, 30, 70)), (0.7, (180, 120, 120)), (1, (220, 200, 210))])
    clouds(c, 98, 4, 31, color=(230, 220, 230))
    seat(c, 120, 94, stone=(210, 200, 210))
    person(c, 120, 96)
    # 鎖が切れる (金の線が途中で弾ける)
    for sx in (-1, 1):
        P.line(c, 120 + sx * 4, 60, 120 + sx * 70, 0, (220, 180, 90))
        c.glow(120 + sx * 30, 34, 8, (255, 240, 200), 1.0)
    P.bolt(c, 112, 40, 128, 70, 4, color=(255, 255, 255), glow=(200, 160, 220), width=1)
    return c.quantize(DUSK)


def break2():
    c = K.Canvas()
    c.vgrad([(0, (10, 8, 14)), (1, (40, 20, 24))])
    heart(c, 120, 70, 18, color=(150, 40, 40), glow=(80, 20, 30), cracked=True)
    rng = np.random.default_rng(7)
    for _ in range(24):
        a = rng.uniform(0, math.tau)
        d = rng.uniform(22, 60)
        c.disc(120 + math.cos(a) * d, 70 + math.sin(a) * d, rng.uniform(0.8, 2.0), (200, 90, 70))
    return c.quantize(DUSK)


def sky_crack(c, x0, y0, x1, y1, seed):
    pts = []
    rng = np.random.default_rng(seed)
    for t in np.linspace(0, 1, 16):
        pts.append((x0 + (x1 - x0) * t + rng.uniform(-4, 4), y0 + (y1 - y0) * t + rng.uniform(-6, 6)))
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        for w in range(-3, 4):
            P.line(c, ax, ay + w * 0.6, bx, by + w * 0.6, (24, 12, 40) if abs(w) < 2 else (230, 180, 240))
    c.glow((x0 + x1) / 2, (y0 + y1) / 2, 50, (160, 110, 200), 0.5)


def break3():
    c = K.Canvas()
    c.vgrad([(0, (50, 40, 90)), (0.7, (200, 150, 150)), (1, (230, 210, 210))])
    mesh(c, "pilgrim_plume", 150, 40, 80, 70, 200, pitch=0, silhouette=(70, 50, 100))
    sky_crack(c, 20, 30, 230, 56, 5)
    clouds(c, 104, 3, 33, color=(230, 220, 230))
    return c.quantize(DUSK)


def break4():
    c = K.Canvas()
    c.vgrad([(0, (20, 20, 50)), (0.8, (90, 80, 120)), (1, (120, 110, 140))])
    c.stars(50, 41, ymax=60)
    sky_crack(c, 30, 20, 210, 34, 9)
    c.ridge(112, 4, 0.05, 6, (40, 36, 60), rough=0.6)
    c.poly([(0, 116), (240, 116), (240, 135), (0, 135)], (34, 30, 50))
    for name, x, y, w, h, yaw in (("stone_whale_far", 60, 104, 100, 34, 100), ("ember_titan_far", 180, 106, 80, 30, 260)):
        mesh(c, name, x, y, w, h, yaw, pitch=2, tint=(0.4, 0.4, 0.55))
    return c.quantize(DUSK)


def break5():
    c = K.Canvas()
    c.vgrad([(0, (30, 26, 60)), (0.7, (140, 110, 140)), (1, (180, 150, 160))])
    sky_crack(c, 10, 26, 230, 44, 13)
    town(c, 116, (40, 34, 52), lit=(255, 210, 140))
    return c.quantize(DUSK)


# ---------------------------------------------------------------- 受け入れる
def accept1():
    c = K.Canvas()
    c.vgrad([(0, (150, 180, 230)), (0.6, (252, 240, 210)), (1, (250, 250, 250))])
    c.glow(120, 10, 80, (255, 240, 200), 1.0)
    mesh(c, "pilgrim_plume", 120, 34, 120, 70, 180, pitch=-10)
    clouds(c, 100, 3, 51)
    seat(c, 120, 100, s=0.7)
    person(c, 104, 100)
    return c.quantize(GOLD)


def accept2():
    c = K.Canvas()
    c.vgrad([(0, (120, 160, 220)), (0.5, (255, 244, 220)), (1, (250, 250, 250))])
    c.glow(120, 60, 60, (255, 250, 230), 1.0)
    for yaw, x, y in ((150, 70, 54), (210, 170, 54), (180, 120, 30)):
        mesh(c, "pilgrim_plume", x, y, 110, 80, yaw, pitch=0)
    c.disc(120, 62, 12, (255, 252, 240))
    clouds(c, 104, 3, 53)
    return c.quantize(GOLD)


def accept3():
    c = K.Canvas()
    c.vgrad([(0, (60, 90, 160)), (0.7, (220, 220, 240)), (1, (245, 245, 250))])
    c.stars(20, 61, ymax=40)
    mesh(c, "pilgrim_moon", 140, 50, 70, 80, 200, pitch=6)
    clouds(c, 106, 3, 55)
    return c.quantize(GOLD)


def accept4():
    c = K.Canvas()
    c.vgrad([(0, (110, 160, 230)), (0.85, (220, 230, 245)), (1, (240, 240, 245))])
    c.glow(70, 34, 40, (255, 244, 210), 0.7)
    mesh(c, "pilgrim_plume", 70, 34, 120, 64, 160, pitch=-20, tint=(1.0, 0.98, 0.9))
    town(c, 118, (70, 72, 90))
    for x in (60, 70, 160, 178, 196):
        person(c, x, 118, color=(40, 40, 54), s=0.8)
    return c.quantize(GOLD)


def accept5():
    c = K.Canvas()
    c.vgrad([(0, (140, 170, 230)), (0.6, (255, 240, 210)), (1, (250, 250, 250))])
    c.glow(190, 30, 60, (255, 240, 210), 0.9)
    mesh(c, "pilgrim_plume", 186, 34, 100, 60, 200, pitch=-10)
    for i in range(4):
        x, y = 50 + i * 30, 100 - i * 14
        mesh(c, "npc_guest_moon_past%d" % (i + 1), x, y, 22, 30, 160, tint=(1.0, 0.98, 0.92))
    clouds(c, 112, 2, 57)
    return c.quantize(GOLD)


REELS = {
    "calm": [
        ("p1", calm1, "still", "minecraft:block.beacon.activate", "座に手を置くと、光が雲を抜けて、地の底へ降りていった。", "When you laid your hand on the seat, light passed through the clouds and down into the depths."),
        ("p2", calm2, "still", "minecraft:entity.warden.heartbeat", "緩んでいた心臓の鼓動が、ゆっくり揃っていく。空と地が、また引き合う。",
         "The loosened heart began, slowly, to beat in time again. Sky and earth drew on one another once more."),
        ("p3", calm3, "left", "skyabyss:titan_song", "巨神たちの歌が、また聞こえはじめた。", "The titans' song could be heard again."),
        ("p4", calm4, "up", "minecraft:block.bell.use", "月は遠のき、空が晴れた。都の鐘が、朝を告げている。", "The moon drew away and the sky cleared. The city's bell announced the morning."),
        ("p5", calm5, "still", "minecraft:block.amethyst_block.resonate", "前の私たちは、約束を果たした顔で、光に還っていった。",
         "My former selves, their promise kept, returned to the light."),
    ],
    "break": [
        ("p1", break1, "still", "minecraft:block.chain.break", "座を断ち、巡礼者をつなぐ鎖を切った。", "You struck the seat and cut the chains that bound the Pilgrim."),
        ("p2", break2, "still", "minecraft:entity.warden.heartbeat", "地の底の心臓は砕け、空と地の引き合いが、ほどけた。",
         "The heart in the depths shattered, and the pull between sky and earth came undone."),
        ("p3", break3, "left", "minecraft:block.glass.break", "巡礼者は、空の裂け目の向こうへ封じられた。", "The Pilgrim was sealed away beyond a crack in the sky."),
        ("p4", break4, "down", "minecraft:entity.elder_guardian.ambient", "巨神たちは深く眠りについた。もう歌わない。", "The titans sank into a deep sleep. They sing no more."),
        ("p5", break5, "still", "minecraft:block.bell.resonate", "裂けた空の下でも、人は町に灯をともす。世界は、変わったまま続いていく。",
         "Even beneath the broken sky, people light their lamps. The world goes on, changed."),
    ],
    "accept": [
        ("p1", accept1, "up", "minecraft:entity.phantom.flap", "座を空けて、巡礼者を地上へ招いた。", "You left the seat empty, and invited the Pilgrim down to the earth."),
        ("p2", accept2, "still", "minecraft:block.beacon.activate", "巡礼者は、はじめて雲の下へ降りてきた。……全身は、光に包まれて見えない。",
         "For the first time, the Pilgrim came down beneath the clouds. ...Its whole form stayed hidden in light."),
        ("p3", accept3, "left", "minecraft:block.chain.break", "引きずられていた月は鎖を解かれ、空の高みで静かに止まった。",
         "The moon it had dragged was freed of its chains, and came to rest quietly high in the sky."),
        ("p4", accept4, "right", "minecraft:block.bell.resonate", "都の鐘は鳴りやんだ。空を見張る者は、もういらない。翼が、昼の空を巡っている。",
         "The city's bell fell silent. No one need watch the sky any longer. A wing circles the daytime sky."),
        ("p5", accept5, "up", "minecraft:block.amethyst_block.resonate", "前の私たちは翼に乗って、空の向こうへ行った。約束の、その先へ。",
         "My former selves rode the wing beyond the sky. Past the promise, and further still."),
    ],
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for ending, panels in REELS.items():
        for pid, fn, *_ in panels:
            img = fn()
            img.save(OUT / f"finale_end_{ending}_{pid}.png")
            img.resize((img.width * 4, img.height * 4), 0).save(OUT / f"finale_end_{ending}_{pid}_x4.png")
            print("wrote", ending, pid)


if __name__ == "__main__":
    main()
