#!/usr/bin/env python3
"""遺物ブロック (箱・休み火・レバー・ボタン・感圧板・鐘・碑・試練済みの封印) の 4 巨神の様式を、32px のドット絵と模型で書く。
リポジトリ直下で `python tools/gen/items/gen_relic_blocks.py` (PIL のみ。何度流しても同じ)。

様式 (ブロックの状態 titan = whale / tide / storm / ember):
  whale = 鯨骨と深層岩 (青い光) / tide = 鱗と貝 (青緑と珊瑚) / storm = 銅と羽 (淡い金) / ember = 玄武岩と琥珀
素材の絵 (textures/block/relic_<t>_*.png): stone 石 / trim 象嵌 (骨・貝・銅・琥珀) / accent 差し色 / glow・glow_off 光る筋 /
  flame 炎 (4 コマ) / tablet 碑の面 / seal 封印の面。形は模型 (models/block/relic_*.json)。向き・状態は blockstates。
出力: assets/skyabyss/{textures/block,models/block,models/item,blockstates}/ と lang (ja_jp / en_us)。
"""
import json
import math
import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent
A = ROOT / "src/main/resources/assets/skyabyss"
TEX = A / "textures/block"
MOD = A / "models/block"
ITM = A / "models/item"
BST = A / "blockstates"
S = 32
TITANS = ["whale", "tide", "storm", "ember"]

PAL = {
    "whale": dict(o=(12, 16, 24), d=(30, 39, 54), m=(47, 60, 80), l=(76, 93, 116), h=(112, 132, 154),
                  k1=(128, 120, 96), k2=(184, 175, 146), k3=(228, 222, 198), a1=(70, 84, 104), a2=(104, 124, 146),
                  g=(70, 200, 224), g2=(190, 250, 255), gd=(20, 66, 84)),
    "tide": dict(o=(14, 14, 36), d=(26, 38, 84), m=(40, 62, 120), l=(70, 100, 160), h=(112, 146, 200),
                 k1=(150, 98, 178), k2=(208, 176, 228), k3=(248, 236, 250), a1=(160, 50, 76), a2=(232, 108, 124),
                 g=(84, 226, 190), g2=(204, 255, 238), gd=(20, 80, 76)),
    "storm": dict(o=(14, 18, 22), d=(38, 49, 58), m=(60, 75, 84), l=(90, 108, 118), h=(128, 146, 154),
                  k1=(120, 62, 36), k2=(188, 110, 62), k3=(236, 168, 108), a1=(176, 156, 92), a2=(238, 224, 152),
                  g=(240, 222, 140), g2=(255, 250, 214), gd=(92, 80, 40), v=(66, 132, 112)),
    "ember": dict(o=(10, 9, 11), d=(27, 25, 29), m=(45, 42, 47), l=(68, 64, 70), h=(98, 92, 98),
                  k1=(140, 70, 16), k2=(214, 126, 30), k3=(255, 198, 88), a1=(52, 40, 40), a2=(120, 84, 60),
                  g=(255, 152, 40), g2=(255, 232, 144), gd=(92, 38, 10), r=(196, 52, 20)),
}


def hsh(x, y, s=0):
    v = (x * 73856093) ^ (y * 19349663) ^ (s * 83492791)
    v = (v ^ (v >> 13)) * 1274126177
    return (v >> 8) & 255


def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


class Cv:
    def __init__(self, h=S):
        self.w, self.h = S, h
        self.im = Image.new("RGBA", (S, h), (0, 0, 0, 0))
        self.px = self.im.load()

    def p(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = tuple(c)[:3] + (255,)

    def get(self, x, y):
        return self.px[x % self.w, y % self.h]


def save_tex(cv, name):
    TEX.mkdir(parents=True, exist_ok=True)
    cv.im.save(TEX / f"{name}.png")


# ------------------------------------------------------------------ 石
def tide_scales():
    """鱗: 8px の円い鱗が 5px ずつずれて重なる (下の段が上の段の縁にかぶさる)。上の縁に光、下の縁に影"""
    P, c = PAL["tide"], Cv()
    for y in range(S):
        for x in range(S):
            c.px[x, y] = P["d"] + (255,)
    for row in range(-1, 8):
        for k in range(-1, 5):
            cx = k * 8 + 3.5 + (row % 2) * 4
            cy = row * 5 + 1.0
            tone = hsh(row % 7, k % 4, 7) % 3
            for y in range(int(cy) - 6, int(cy) + 7):
                for x in range(int(cx) - 6, int(cx) + 7):
                    dx, dy = x - cx, (y - cy) * 1.15
                    d = math.hypot(dx, dy)
                    if d > 4.4 or y < cy - 1.5:
                        continue
                    base = (P["m"], mix(P["m"], P["l"], 0.35), mix(P["m"], P["d"], 0.35))[tone]
                    col = base
                    if hsh(x, y, 5) < 26:
                        col = mix(col, P["d"], 0.5)
                    if d > 3.5:
                        col = P["o"]
                    elif dy > 1.2 and d > 2.6:
                        col = mix(col, P["d"], 0.55)
                    elif dy < 0.2 and d > 1.6:
                        col = P["l"]
                    if abs(dx) < 0.6 and dy > 0 and d < 3.5:
                        col = mix(col, P["l"], 0.4)
                    c.p(x % S, y % S, col)
    return c


def stone_tex(t):
    if t == "tide":
        return tide_scales()
    P, c = PAL[t], Cv()
    for y in range(S):
        for x in range(S):
            r = hsh(x, y, 11)
            col = P["m"]
            if t == "whale":                       # 深層岩の煉瓦 (8px の段、16px の石)
                row, ry = y // 8, y % 8
                off = (row % 2) * 8
                bx = (x + off) % 16
                tone = hsh(row, (x + off) // 16, 3)
                col = P["d"] if tone < 90 else (P["l"] if tone > 220 else P["m"])
                if r < 38:
                    col = mix(col, P["d"], 0.6)
                elif r > 226:
                    col = mix(col, P["h"], 0.4)
                if ry == 7 or bx == 15:
                    col = P["o"]
                elif ry == 0 or bx == 0:
                    col = P["l"]
                elif hsh(row, y, 5) < 14 and bx > 3:   # 層理の筋
                    col = P["d"]
            elif t == "tide":                      # 鱗 (5px の段・8px の鱗、下の段が上に重なる)
                row = y // 5
                ry = y % 5
                off = (row % 2) * 4
                bx = (x + off) % 8
                dx = bx - 3.5
                dy = ry - 0.0
                dist = (dx * dx / 16.0 + (dy * dy) / 20.0)
                tone = hsh(row, (x + off) // 8, 7)
                base = mix(P["m"], P["d"], (tone % 60) / 120)
                col = base
                if r < 36:
                    col = mix(col, P["d"], 0.5)
                elif r > 228:
                    col = mix(col, P["l"], 0.5)
                if ry == 4:
                    col = P["o"] if abs(dx) < 3.2 else P["d"]          # 鱗の縁 (下の段の影)
                if ry == 3:
                    col = mix(col, P["d"], 0.35)
                if ry == 0:
                    col = P["l"] if abs(dx) < 2.8 else mix(P["l"], col, 0.5)   # 上の縁の光
                if bx in (0, 7) and ry >= 1:
                    col = mix(col, P["d"], 0.5)
            elif t == "storm":                     # 風化した薄い石の板 (16x11)、縁に緑青
                row, ry = y // 11, y % 11
                off = (row % 2) * 8
                bx = (x + off) % 16
                tone = hsh(row, (x + off) // 16, 9)
                col = P["d"] if tone < 80 else (P["l"] if tone > 215 else P["m"])
                if r < 40:
                    col = mix(col, P["d"], 0.6)
                elif r > 226:
                    col = mix(col, P["h"], 0.4)
                if ry == 10 or bx == 15:
                    col = P["o"]
                elif ry == 0 or bx == 0:
                    col = mix(P["l"], P["v"], 0.25)
                elif (ry == 9 or bx == 14) and hsh(x, y, 4) < 120:
                    col = mix(col, P["v"], 0.45)             # 緑青のにじみ
                if bx == 2 and ry == 2:
                    col = P["k2"]                             # 銅の鋲
                if bx == 13 and ry == 8:
                    col = P["k2"]
            else:                                  # 玄武岩の柱 (六角の柱の切り口を縦に)
                cols = (0, 7, 15, 22, 32)
                ci = max(i for i in range(4) if x >= cols[i])
                cx0, cx1 = cols[ci], cols[ci + 1] - 1
                tone = hsh(ci, 5, 17)
                col = P["m"] if tone > 100 else P["d"]
                if r < 40:
                    col = mix(col, P["o"], 0.55)
                elif r > 224:
                    col = mix(col, P["l"], 0.5)
                if x == cx1:
                    col = P["o"]
                elif x == cx0:
                    col = P["l"]
                if hsh(ci, y // 6, 21) < 22 and x > cx0 + 1 and x < cx1 - 1 and y % 6 == 0:
                    col = P["o"]                              # 横のひび
                if x == cx1 and hsh(x, y, 31) < 46:
                    col = P["gd"]                             # 継ぎ目の熾の光
                if x == cx1 and hsh(x, y, 32) < 14:
                    col = P["k1"]
            c.p(x, y, col)
    return c


# ------------------------------------------------------------------ 象嵌 (骨・貝・銅・琥珀)
def trim_tex(t):
    P, c = PAL[t], Cv()
    for y in range(S):
        for x in range(S):
            r = hsh(x, y, 13)
            if t == "whale":                       # 鯨骨: 縦の繊維と小さな孔
                fib = hsh(x, y // 5, 2)
                col = P["k2"]
                if fib < 60:
                    col = P["k1"]
                elif fib > 200:
                    col = P["k3"]
                if r < 22:
                    col = mix(col, P["k1"], 0.7)
                if hsh(x // 2, y // 3, 8) < 8 and (x % 2 == 0):
                    col = P["k1"]
                if y % 16 == 0:
                    col = P["k3"]
                elif y % 16 == 15:
                    col = P["k1"]
            elif t == "tide":                      # 貝: 下から広がる放射の畝
                ang = math.atan2(x - 15.5, 36 - y)
                ridge = int((ang + 1.6) / 0.16)
                d = math.hypot(x - 15.5, 36 - y)
                col = P["k2"] if ridge % 2 == 0 else P["k3"]
                if ridge % 4 == 1:
                    col = P["k1"]
                ring = int(d / 6)
                if int(d) % 6 == 0:
                    col = mix(col, P["k1"], 0.55)
                if r < 20:
                    col = mix(col, P["k1"], 0.5)
                if y < 3:
                    col = P["a2"] if (x // 2) % 2 == 0 else P["a1"]   # 珊瑚の縁
            elif t == "storm":                     # 銅: 板と、羽の杉綾 (淡い金)
                col = P["k2"]
                if r < 40:
                    col = P["k1"]
                elif r > 220:
                    col = P["k3"]
                row = y // 8
                ry = y % 8
                if ry == 0:
                    col = P["k3"]
                elif ry == 7:
                    col = P["k1"]
                # 羽 (中央の軸から斜めの羽枝)
                cx = 8 + (x // 16) * 16
                dx = abs(x - cx)
                if dx == 0 and 1 <= ry <= 6:
                    col = P["a2"]
                elif dx <= 6 and 1 <= ry <= 6 and (ry + dx) % 3 == 0 and ry >= 2:
                    col = P["a1"]
                if hsh(x, y, 5) < 14 and y > 20:
                    col = mix(col, P["v"], 0.7)           # 緑青
            else:                                  # 琥珀: 渦と光、黒い混じり
                v = math.sin(x * 0.45 + math.sin(y * 0.33) * 2.0) + math.cos(y * 0.5 + x * 0.12)
                col = P["k2"]
                if v > 0.9:
                    col = P["k3"]
                elif v < -0.8:
                    col = P["k1"]
                if r < 14:
                    col = mix(P["a1"], P["k1"], 0.3)
                if r > 244:
                    col = P["g2"]
                if x % 16 == 0 or y % 16 == 0:
                    col = P["k1"]
                elif x % 16 == 1 or y % 16 == 1:
                    col = mix(col, P["k3"], 0.4)
            c.p(x, y, col)
    return c


def accent_tex(t):
    P, c = PAL[t], Cv()
    for y in range(S):
        for x in range(S):
            r = hsh(x, y, 19)
            if t == "whale":                       # 青鋼のような暗い骨
                col = P["a1"] if r > 40 else P["d"]
                if r > 232:
                    col = P["a2"]
            elif t == "tide":                      # 珊瑚
                col = P["a1"]
                if (x + y // 2) % 5 == 0:
                    col = P["a2"]
                if r < 30:
                    col = mix(P["a1"], P["o"], 0.5)
            elif t == "storm":                     # 淡い金の羽金具
                col = P["a1"] if (y % 4) else P["a2"]
                if r > 230:
                    col = P["g2"]
            else:                                  # 熾の鉄
                col = P["a1"] if r > 50 else P["o"]
                if r > 236:
                    col = P["k1"]
                if hsh(x, y, 3) < 10:
                    col = P["gd"]
            c.p(x, y, col)
    return c


# ------------------------------------------------------------------ 光る筋
def glow_tex(t, on=True):
    P, c = PAL[t], Cv()
    for y in range(S):
        for x in range(S):
            v = (hsh(x // 3, y // 3, 5) + hsh(x // 2, y // 2, 6) + hsh(x, y, 7)) / 3
            if on:
                col = P["g"]
                if v > 140:
                    col = P["g2"]
                elif v < 92:
                    col = mix(P["g"], P["gd"], 0.55)
            else:
                col = P["gd"]
                if v > 150:
                    col = mix(P["gd"], P["g"], 0.35)
                elif v < 90:
                    col = mix(P["gd"], P["o"], 0.6)
            c.p(x, y, col)
    return c


# ------------------------------------------------------------------ 炎 (4 コマ。32x128)
def flame_tex(t):
    P = PAL[t]
    cv = Cv(S * 4)
    for f in range(4):
        ph = f * math.pi / 2
        for y in range(S):
            yy = S - 1 - y                         # 下から
            if t == "whale":                       # 細く高い魂の炎
                hgt = 28
                if yy > hgt:
                    continue
                k = yy / hgt
                half = 5.0 * (1 - k) ** 0.8 + 0.6 if k > 0.04 else 3
                cx = 15.5 + math.sin(yy * 0.35 + ph) * 1.6 * k
            elif t == "tide":                      # 波打つ二股
                hgt = 22
                if yy > hgt:
                    continue
                k = yy / hgt
                half = 7.0 * (1 - k * k) + 0.4
                cx = 15.5 + math.sin(yy * 0.55 + ph) * 2.2 * k
            elif t == "storm":                     # 稲妻のようなぎざぎざ
                hgt = 26
                if yy > hgt:
                    continue
                k = yy / hgt
                half = 4.2 * (1 - k) + 0.7
                cx = 15.5 + (((yy // 4 + f) % 2) * 2 - 1) * 2.4 * k
            else:                                  # 広い橙の炎、先が二つ
                hgt = 24
                if yy > hgt:
                    continue
                k = yy / hgt
                half = 7.5 * (1 - k ** 1.6) + 0.5
                cx = 15.5 + math.sin(yy * 0.4 + ph) * 2.0 * k
                if k > 0.7 and abs(math.sin((cx) * 0.7 + ph)) < 0.3:
                    half *= 0.4
            for x in range(S):
                d = abs(x - cx)
                if d <= half:
                    k2 = d / max(half, 0.01)
                    if k2 > 0.8:
                        col = P["gd"] if t != "ember" else P["r"]
                        if t == "whale" or t == "tide":
                            col = mix(P["gd"], P["g"], 0.5)
                    elif k2 > 0.45:
                        col = P["g"]
                    else:
                        col = P["g2"]
                    if yy < 3:
                        col = P["g2"] if k2 < 0.6 else P["g"]
                    cv.p(x, f * S + y, col)
        # 火の粉
        for i in range(3):
            sx = 8 + hsh(f, i, 41) % 16
            sy = f * S + 2 + hsh(f, i, 42) % 12
            if cv.px[sx, sy][3] == 0:
                cv.p(sx, sy, P["g2"])
    return cv


# ------------------------------------------------------------------ 碑の面
def tablet_tex(t):
    P, c = PAL[t], Cv()
    st = stone_tex(t)
    for y in range(S):
        for x in range(S):
            c.px[x, y] = st.px[x, y]
    # 外枠 (象嵌)
    tr = trim_tex(t)
    for y in range(S):
        for x in range(S):
            if x < 2 or x > 29 or y < 2 or y > 29:
                c.px[x, y] = tr.px[x, y]
            elif x in (2, 29) or y in (2, 29):
                c.px[x, y] = P["o"]
    for y in range(3, 29):
        for x in range(3, 29):
            c.p(x, y, mix(P["d"], P["o"], 0.55) if hsh(x, y, 3) > 20 else P["d"])
    # 刻んだ光る字 (行ごとに長さを変える)
    for i, y in enumerate((7, 12, 17, 22)):
        w = (16, 11, 16, 7)[i]
        x = 8
        while x < 8 + w:
            run = 2 + hsh(x, y, 7) % 3
            for xx in range(x, min(x + run, 8 + w)):
                c.p(xx, y, P["g"])
                c.p(xx, y + 1, P["gd"])
            if hsh(x, y, 8) < 90:
                c.p(x, y - 1, P["g2"])
            x += run + 2
    # 角の飾り
    for x, y in ((4, 4), (27, 4), (4, 27), (27, 27)):
        c.p(x, y, P["g"])
    return c


# ------------------------------------------------------------------ 封印の面 (越えた印。開いた目)
def seal_tex(t):
    P, c = PAL[t], Cv()
    st = stone_tex(t)
    tr = trim_tex(t)
    cx = cy = 15.5
    for y in range(S):
        for x in range(S):
            c.px[x, y] = st.px[x, y]
            if x < 2 or x > 29 or y < 2 or y > 29:
                c.px[x, y] = tr.px[x, y]
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d <= 13.4:
                r = hsh(x, y, 61)
                col = P["m"] if r > 40 else P["d"]
                if d > 12:
                    col = P["o"]
                c.p(x, y, col)
            if 8.6 <= d <= 10.8:
                c.p(x, y, tr.px[x, y][:3])
            elif 10.8 < d <= 11.6:
                c.p(x, y, P["o"])
            elif d < 8.6:
                c.p(x, y, mix(P["d"], P["gd"], 0.6))
    # 開いた目
    for y in range(32):
        for x in range(32):
            dd = abs(x - cx) / 7.0 + abs(y - cy) / 4.6
            if dd <= 1:
                c.p(x, y, P["g"] if dd > 0.4 else P["g2"])
            elif dd <= 1.12:
                c.p(x, y, P["gd"])
    for y in range(13, 19):
        for x in range(14, 18):
            if math.hypot(x - 15.5, y - 15.5) <= 2.3:
                c.p(x, y, P["o"])
    c.p(14, 14, P["g2"])
    # 放射の筋
    for ang in range(0, 360, 45):
        for r in (11.8, 12.4):
            c.p(int(round(cx + math.cos(math.radians(ang)) * r)), int(round(cy + math.sin(math.radians(ang)) * r)), P["g"])
    return c


# ------------------------------------------------------------------ 模型の部品
def face_all(tex, uv=None, glow=False, only=None, cull=None):
    f = {}
    for d in ("north", "east", "south", "west", "up", "down"):
        if only is not None and d not in only:
            continue
        e = {"texture": "#" + tex}
        if uv and d in uv:
            e["uv"] = uv[d]
        elif uv and "all" in uv:
            e["uv"] = uv["all"]
        if glow:
            e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        if cull and d in cull:
            e["cullface"] = d
        f[d] = e
    return f


def box(a, b, tex, rot=None, glow=False, only=None, uv=None, cull=None):
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": face_all(tex, uv, glow, only, cull)}
    if glow:
        e["shade"] = False
    if rot:
        e["rotation"] = {"origin": rot[0], "axis": rot[1], "angle": rot[2]}
    return e


def plane_x(cx, y0, y1, half, tex="flame"):
    """x 方向に広がる板 (z = cx 面): 両面"""
    return {"from": [cx - half, y0, 8], "to": [cx + half, y1, 8], "shade": False,
            "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#" + tex, "neoforge_data": {"block_light": 15, "sky_light": 15}},
                      "south": {"uv": [0, 0, 16, 16], "texture": "#" + tex, "neoforge_data": {"block_light": 15, "sky_light": 15}}}}


def plane_z(cz, y0, y1, half, tex="flame"):
    return {"from": [8, y0, cz - half], "to": [8, y1, cz + half], "shade": False,
            "faces": {"east": {"uv": [0, 0, 16, 16], "texture": "#" + tex, "neoforge_data": {"block_light": 15, "sky_light": 15}},
                      "west": {"uv": [0, 0, 16, 16], "texture": "#" + tex, "neoforge_data": {"block_light": 15, "sky_light": 15}}}}


def model(t, elements, extra_tex=None, cutout=False, particle="stone", glow_on=True, flame=False):
    tx = {"stone": f"skyabyss:block/relic_{t}_stone", "trim": f"skyabyss:block/relic_{t}_trim", "accent": f"skyabyss:block/relic_{t}_accent",
          "glow": f"skyabyss:block/relic_{t}_glow" if glow_on else f"skyabyss:block/relic_{t}_glow_off"}
    tx["particle"] = tx[particle]
    if flame:
        tx["flame"] = f"skyabyss:block/relic_{t}_flame"
    if extra_tex:
        tx.update(extra_tex)
    m = {"parent": "minecraft:block/block", "textures": tx, "elements": elements}
    if cutout or flame:
        m["render_type"] = "minecraft:cutout"
    return m


def wr(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


# ------------------------------------------------------------------ 箱
def chest_model(t, open_):
    el = []
    # 体 (蓋の下)。四隅の柱 (象嵌) と、帯 (蓋との継ぎ目)
    el.append(box([1.5, 0, 1.5], [14.5, 9, 14.5], "stone"))
    for x0, z0 in ((1, 1), (13, 1), (1, 13), (13, 13)):
        el.append(box([x0, 0, z0], [x0 + 2, 9, z0 + 2], "trim"))
    el.append(box([1.25, 6, 1.25], [14.75, 8, 14.75], "accent"))
    # 錠 (前 = 北)。受け板と光る細い口
    el.append(box([6, 4.5, 0.75], [10, 9.5, 1.5], "trim"))
    el.append(box([7.4, 5.5, 0.5], [8.6, 8.5, 0.75], "glow", glow=True))
    if open_:
        el.append(box([2, 9, 2], [14, 9.25, 14], "glow", glow=True, only=["up"]))     # 口の中の光
        el.append(box([2.5, 8.75, 2.5], [13.5, 9, 13.5], "accent"))
    # 蓋。開いた蓋は奥の縁 (z 13.5) を軸に 45 度起こす
    rot = ([8, 9, 13.5], "x", 45) if open_ else None
    lid = [box([1.5, 9, 1.5], [14.5, 14, 14.5], "stone", rot=rot)]
    lid.append(box([1.25, 9, 1.25], [14.75, 10.5, 14.75], "trim", rot=rot))
    lid.append(box([1.25, 12.5, 1.25], [14.75, 14, 14.75], "accent", rot=rot) if t != "tide" else box([1.25, 12.5, 1.25], [14.75, 14, 14.75], "trim", rot=rot))
    # 巨神ごとの蓋の飾り
    if t == "whale":
        for z in (4, 7.5, 11):
            lid.append(box([2, 14, z], [14, 14.75, z + 1.5], "trim", rot=rot))
    elif t == "tide":
        for i, (w, y) in enumerate(((8, 14), (5, 14.9), (2.4, 15.8))):
            lid.append(box([8 - w / 2, y, 3.5], [8 + w / 2, y + 0.9, 9], "trim", rot=rot))
        lid.append(box([6.25, 14, 2], [9.75, 14.75, 3.5], "accent", rot=rot))
    elif t == "storm":
        lid.append(box([7, 14, 2], [9, 14.75, 14], "accent", rot=rot))
        lid.append(box([3, 14, 3], [5, 15.5, 7], "trim", rot=rot if rot else ([4, 14, 5], "z", 22.5)))
        lid.append(box([11, 14, 3], [13, 15.5, 7], "trim", rot=rot if rot else ([12, 14, 5], "z", -22.5)))
    else:
        for x in (3, 7, 11):
            lid.append(box([x, 14, 3 + (x % 5)], [x + 2, 15.2, 5 + (x % 5)], "trim", rot=rot))
        lid.append(box([2, 14, 12.5], [14, 14.9, 14], "accent", rot=rot))
    el.extend(lid)
    return model(t, el, glow_on=True)


# ------------------------------------------------------------------ 休み火
def hearth_model(t, lit):
    el = []
    # 台と縁
    el.append(box([0, 0, 0], [16, 3, 16], "stone", cull={"down": "down"}))
    for a, b in (([0, 3, 0], [16, 6, 2]), ([0, 3, 14], [16, 6, 16]), ([0, 3, 2], [2, 6, 14]), ([14, 3, 2], [16, 6, 14])):
        el.append(box(a, b, "stone"))
    el.append(box([0, 5.5, 0], [16, 6.25, 2.25], "trim") if t != "ember" else box([0, 5.5, 0], [16, 6, 2], "accent"))
    el.append(box([0, 5.5, 13.75], [16, 6.25, 16], "trim") if t != "ember" else box([0, 5.5, 14], [16, 6, 16], "accent"))
    el.append(box([2, 3, 2], [14, 3.5, 14], "glow", glow=True, only=["up"]))
    # 熾 (中央の塊)
    el.append(box([5, 3.5, 5], [11, 4.5, 11], "accent"))
    el.append(box([6, 4.5, 6], [10, 5.5, 10], "glow", glow=lit))
    # 巨神ごとの飾り
    if t == "whale":                           # 肋骨のかご: 縁の中ほどから火へ傾く四本
        for (ax, rot, at) in (("x", -22.5, [7, 6, 0.5]), ("x", 22.5, [7, 6, 13.5]), ("z", 22.5, [0.5, 6, 7]), ("z", -22.5, [13.5, 6, 7])):
            o = [8, 6, 8]
            if at[0] == 7:
                el.append(box(at, [at[0] + 2, at[1] + 7, at[2] + 2], "trim", rot=([at[0] + 1, 6, at[2] + 1], ax, rot)))
            else:
                el.append(box(at, [at[0] + 2, at[1] + 7, at[2] + 2], "trim", rot=([at[0] + 1, 6, at[2] + 1], ax, rot)))
    elif t == "tide":                          # 貝殻の扇を背に、珊瑚の角
        el.append(box([2, 6, 1], [14, 7.5, 3], "trim"))
        el.append(box([3.5, 7.5, 1.25], [12.5, 9.5, 2.75], "trim"))
        el.append(box([5.5, 9.5, 1.5], [10.5, 11.5, 2.5], "trim"))
        el.append(box([7, 11.5, 1.75], [9, 12.5, 2.25], "accent"))
        for x in (1, 13):
            el.append(box([x, 6, 12.5], [x + 2, 10, 14.5], "accent"))
        el.append(box([0.5, 6, 0.5], [2.5, 8, 2.5], "accent"))
        el.append(box([13.5, 6, 0.5], [15.5, 8, 2.5], "accent"))
    elif t == "storm":                         # 銅の三脚と、羽の金具
        for x, z, rt in ((2, 1, ([3, 6, 2], "x", -22.5)), (12, 1, ([13, 6, 2], "x", -22.5)), (7, 13, ([8, 6, 14], "x", 22.5))):
            el.append(box([x, 6, z], [x + 2, 13, z + 2], "trim", rot=rt))
        el.append(box([2, 5.5, 2], [14, 6.5, 3], "accent"))
        el.append(box([0, 6, 7], [3, 10, 9], "accent", rot=([1.5, 6, 8], "z", 22.5)))
        el.append(box([13, 6, 7], [16, 10, 9], "accent", rot=([14.5, 6, 8], "z", -22.5)))
    else:                                      # 玄武岩の柱と琥珀
        for x0, z0, h in ((0, 0, 11), (13, 0, 8), (0, 13, 9), (13, 13, 12)):
            el.append(box([x0, 3, z0], [x0 + 3, h, z0 + 3], "stone"))
            el.append(box([x0 + 0.5, h, z0 + 0.5], [x0 + 2.5, h + 0.75, z0 + 2.5], "trim"))
        el.append(box([6, 3.5, 12.5], [10, 7, 14.5], "trim"))
    if lit:
        el.append(plane_x(8, 3.5, 15.5, 6))
        el.append(plane_z(8, 3.5, 15.5, 6))
    return model(t, el, flame=lit, particle="stone")


# ------------------------------------------------------------------ レバー (土台は 6x3x8。柄を ±45 度)
def lever_model(t, powered):
    el = []
    el.append(box([5, 0, 4], [11, 3, 12], "stone", cull={"down": "down"}))
    el.append(box([4.5, 2, 3.5], [11.5, 3.25, 12.5], "trim"))
    el.append(box([6, 3.25, 5], [10, 3.45, 11], "glow", glow=powered, only=["up"]) if False else box([6, 3.25, 5.5], [10, 3.5, 10.5], "glow", glow=True, only=["up"]))
    el.append(box([5.5, 0.5, 3.5], [10.5, 1.5, 4.25], "accent"))
    ang = -45 if powered else 45
    rot = ([8, 3, 8], "x", ang)
    el.append(box([7, 2.5, 7], [9, 11, 9], "trim", rot=rot))
    if t == "whale":
        el.append(box([6.5, 10.5, 6.5], [9.5, 12.5, 9.5], "trim", rot=rot))
        el.append(box([7.25, 11, 7.25], [8.75, 12, 8.75], "glow", glow=powered, rot=rot))
    elif t == "tide":
        el.append(box([6, 10, 6.75], [10, 12.5, 9.25], "trim", rot=rot))
        el.append(box([7.25, 12.5, 7.25], [8.75, 13.5, 8.75], "accent", rot=rot))
        el.append(box([7.25, 10.75, 6.5], [8.75, 11.75, 6.75], "glow", glow=powered, rot=rot))
    elif t == "storm":
        el.append(box([6.75, 5, 6], [9.25, 10.5, 7], "accent", rot=rot))
        el.append(box([6.75, 5, 9], [9.25, 10.5, 10], "accent", rot=rot))
        el.append(box([6.5, 10.5, 6.5], [9.5, 12, 9.5], "accent", rot=rot))
        el.append(box([7.25, 11, 7.25], [8.75, 12.5, 8.75], "glow", glow=powered, rot=rot))
    else:
        el.append(box([6.5, 10, 6.5], [9.5, 12.5, 9.5], "trim", rot=rot))
        el.append(box([7.25, 12.5, 7.25], [8.75, 13.25, 8.75], "glow", glow=powered, rot=rot))
        el.append(box([6.5, 5, 6.5], [9.5, 6, 9.5], "accent", rot=rot))
    return model(t, el, glow_on=powered)


# ------------------------------------------------------------------ ボタン (5..11 x 6..10。2 / 1 の高さ)
def button_model(t, pressed):
    h = 1.0 if pressed else 2.0
    el = []
    el.append(box([4.5, 0, 5.5], [11.5, 1, 10.5], "stone", cull={"down": "down"}))
    el.append(box([5, 0.9, 6], [11, 1.05, 10], "accent", only=["up"]))
    if t == "whale":
        el.append(box([6, 0.9, 7], [10, h, 9], "trim"))
        el.append(box([7.25, h, 7.5], [8.75, h + 0.12, 8.5], "glow", glow=pressed, only=["up"]))
    elif t == "tide":
        el.append(box([6, 0.9, 7], [10, h - 0.5, 9], "trim"))
        el.append(box([6.75, h - 0.5, 7.25], [9.25, h, 8.75], "trim"))
        el.append(box([7.5, h, 7.75], [8.5, h + 0.12, 8.25], "glow", glow=pressed, only=["up"]))
    elif t == "storm":
        el.append(box([6.25, 0.9, 6.75], [9.75, h, 9.25], "trim"))
        el.append(box([5.5, 0.9, 7.5], [6.25, h - 0.25, 8.5], "accent"))
        el.append(box([9.75, 0.9, 7.5], [10.5, h - 0.25, 8.5], "accent"))
        el.append(box([7.25, h, 7.5], [8.75, h + 0.12, 8.5], "glow", glow=pressed, only=["up"]))
    else:
        el.append(box([6, 0.9, 7], [10, h, 9], "trim"))
        el.append(box([6.75, h, 7.5], [9.25, h + 0.12, 8.5], "glow", glow=pressed, only=["up"]))
    el.append(box([5, 1, 5.5], [5.25, 1.25, 10.5], "glow", glow=pressed))
    el.append(box([10.75, 1, 5.5], [11, 1.25, 10.5], "glow", glow=pressed))
    return model(t, el, glow_on=pressed)


# ------------------------------------------------------------------ 感圧板 (1..15 の板。1 / 0.5)
def plate_model(t, pressed):
    h = 0.5 if pressed else 1.0
    el = [box([1, 0, 1], [15, h, 15], "stone", cull={"down": "down"})]
    el.append(box([1, h - 0.05, 1], [15, h, 2.5], "accent", only=["up"]))
    el.append(box([1, h - 0.05, 13.5], [15, h, 15], "accent", only=["up"]))
    inl = h + 0.2
    el.append(box([3, h - 0.05, 3], [13, inl, 13], "trim"))
    if t == "whale":
        el.append(box([4, inl, 7.25], [12, inl + 0.1, 8.75], "stone", only=["up"]))
    elif t == "tide":
        el.append(box([5, inl, 5], [11, inl + 0.25, 11], "trim"))
    elif t == "storm":
        el.append(box([7.25, inl, 3.5], [8.75, inl + 0.1, 12.5], "accent", only=["up"]))
    else:
        el.append(box([5, inl, 5], [11, inl + 0.1, 11], "accent", only=["up"]))
    el.append(box([7, inl + 0.1, 4.5], [9, inl + 0.2, 11.5], "glow", glow=pressed, only=["up"]))
    el.append(box([4.5, inl + 0.1, 7], [11.5, inl + 0.2, 9], "glow", glow=pressed, only=["up"]))
    return model(t, el, glow_on=pressed)


# ------------------------------------------------------------------ 鐘 (門の枠に吊る。枠は X 方向に 16 幅、奥行 4..12)
def bell_model(t, ring):
    el = []
    post = "trim" if t == "whale" else "stone"
    for x0 in (0, 13):
        el.append(box([x0, 0, 5], [x0 + 3, 14, 11], post, cull={"down": "down"}))
    el.append(box([0, 13, 5], [16, 16, 11], "stone"))
    el.append(box([0, 15, 4.75], [16, 16, 11.25], "accent"))
    if t == "whale":
        el.append(box([0, 13, 4.75], [16, 13.75, 11.25], "trim"))
    elif t == "tide":
        el.append(box([0.5, 13.5, 4.5], [3.5, 14.5, 11.5], "accent"))
        el.append(box([12.5, 13.5, 4.5], [15.5, 14.5, 11.5], "accent"))
    elif t == "storm":
        el.append(box([3, 14, 5.5], [13, 15, 10.5], "trim"))
    else:
        for x in (3, 7, 11):
            el.append(box([x, 13.25, 5.5], [x + 2, 13.9, 10.5], "trim"))
    rot = ([8, 13, 8], "x", 22.5) if ring else None
    el.append(box([7.25, 11, 7.25], [8.75, 14, 8.75], "accent", rot=rot))
    body = []
    body.append(box([6.25, 9.5, 6.25], [9.75, 11.25, 9.75], "trim", rot=rot))
    body.append(box([5, 6, 5], [11, 10, 11], "trim", rot=rot))
    body.append(box([4, 3.5, 4], [12, 6.5, 12], "trim", rot=rot))
    lip = "accent" if t in ("tide", "storm") else "trim"
    body.append(box([3.5, 2.25, 3.5], [12.5, 3.75, 12.5], lip, rot=rot))
    if t == "whale":
        for x in (5, 7.5, 10):
            body.append(box([x, 4, 3.75], [x + 1, 9.5, 4.25], "stone", rot=rot))
    elif t == "tide":
        body.append(box([4.5, 7, 4.75], [11.5, 7.75, 5.25], "accent", rot=rot))
        body.append(box([4.25, 4.5, 3.75], [11.75, 5.25, 4.25], "accent", rot=rot))
    elif t == "storm":
        body.append(box([7.4, 3.75, 3.75], [8.6, 10, 4.25], "accent", rot=rot))
        body.append(box([6, 6, 4.75], [10, 6.5, 5.25], "accent", rot=rot))
    else:
        body.append(box([4.25, 4.75, 3.75], [11.75, 5.5, 4.25], "stone", rot=rot))
        body.append(box([4.75, 8, 4.75], [11.25, 8.75, 5.25], "stone", rot=rot))
    body.append(box([7.25, 1, 7.25], [8.75, 3, 8.75], "glow", glow=ring, rot=rot))
    el.extend(body)
    if ring:                                    # 音の輪 (鐘の下と横に、薄い光の枠)
        for yy, w in ((1.0, 2.0), (-0.25, 1.0)):
            pass
        for y0, w in ((0.5, 6.5), (0.0, 7.5)):
            el.append(box([8 - w, y0, 8 - w], [8 + w, y0 + 0.2, 8 - w + 0.4], "glow", glow=True, only=["up", "down"]))
            el.append(box([8 - w, y0, 8 + w - 0.4], [8 + w, y0 + 0.2, 8 + w], "glow", glow=True, only=["up", "down"]))
            el.append(box([8 - w, y0, 8 - w], [8 - w + 0.4, y0 + 0.2, 8 + w], "glow", glow=True, only=["up", "down"]))
            el.append(box([8 + w - 0.4, y0, 8 - w], [8 + w, y0 + 0.2, 8 + w], "glow", glow=True, only=["up", "down"]))
    return model(t, el, glow_on=ring)


# ------------------------------------------------------------------ 碑 (壁掛け / 立碑)。形は元の模型のまま、飾りと面の絵だけ巨神ごと
def tablet_models(t):
    tx = {"particle": f"skyabyss:block/relic_{t}_stone", "front": f"skyabyss:block/relic_{t}_tablet", "side": f"skyabyss:block/relic_{t}_stone",
          "trim": f"skyabyss:block/relic_{t}_trim", "accent": f"skyabyss:block/relic_{t}_accent"}
    # 壁掛け (14x13x3、奥の壁にぴったり。文の面は北)
    wall = [{"from": [1, 2, 13], "to": [15, 15, 16], "faces": {
        "north": {"uv": [1, 1, 15, 14], "texture": "#front"}, "south": {"uv": [1, 1, 15, 14], "texture": "#side", "cullface": "south"},
        "east": {"uv": [13, 1, 16, 14], "texture": "#side"}, "west": {"uv": [13, 1, 16, 14], "texture": "#side"},
        "up": {"uv": [1, 13, 15, 16], "texture": "#side"}, "down": {"uv": [1, 13, 15, 16], "texture": "#side"}}}]
    crown_w = tablet_crown(t, 15, 13, 16, 1, 15)
    wall += crown_w
    # 立碑 (台 12x2x6 と碑 10x13x4)
    stand = [
        {"from": [2, 0, 5], "to": [14, 2, 11], "faces": face_all("trim")},
        {"from": [3, 2, 6], "to": [13, 15, 10], "faces": {
            "north": {"uv": [3, 1, 13, 14], "texture": "#front"}, "south": {"uv": [3, 1, 13, 14], "texture": "#front"},
            "east": {"uv": [6, 1, 10, 14], "texture": "#side"}, "west": {"uv": [6, 1, 10, 14], "texture": "#side"},
            "up": {"uv": [3, 6, 13, 10], "texture": "#side"}}},
    ]
    stand += tablet_crown(t, 15, 6, 10, 3, 13, standing=True)
    return ({"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tx, "elements": wall},
            {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tx, "elements": stand})


def tablet_crown(t, y, z0, z1, x0, x1, standing=False):
    """碑の天辺の飾り (y = 天辺)。壁掛けは z 13..16、立碑は 6..10"""
    el = []
    zc0, zc1 = z0 - 0.25, z1 + 0.25 if standing else z1
    top = y + 1.0
    if t == "whale":                            # 二本の骨の突起
        for x in (x0 + 0.5, x1 - 2.5):
            el.append(box([x, y, z0 + 0.5], [x + 2, y + 1, z1 - 0.5], "trim"))
    elif t == "tide":                           # 貝の冠 (段)
        el.append(box([x0 + 3, y, z0 + 0.5], [x1 - 3, y + 0.5, z1 - 0.5], "trim"))
        el.append(box([x0 + 5, y + 0.5, z0 + 0.75], [x1 - 5, y + 1, z1 - 0.75], "trim"))
    elif t == "storm":                          # 羽の二枚
        el.append(box([x0 + 0.5, y, z0 + 0.5], [x0 + 3, y + 1, z1 - 0.5], "accent"))
        el.append(box([x1 - 3, y, z0 + 0.5], [x1 - 0.5, y + 1, z1 - 0.5], "accent"))
    else:                                       # 玄武岩の三つの突起 (琥珀)
        for x, hh in ((x0 + 1, 0.6), (x0 + (x1 - x0) / 2 - 1, 1.0), (x1 - 3, 0.6)):
            el.append(box([x, y, z0 + 0.5], [x + 2, y + hh, z1 - 0.5], "trim"))
    return el


# ------------------------------------------------------------------ 封印
def seal_model(t):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": f"skyabyss:block/relic_{t}_seal"}}


# ------------------------------------------------------------------ blockstates
LEVER_ROT = {("ceiling", "east"): (180, 270), ("ceiling", "north"): (180, 180), ("ceiling", "south"): (180, 0), ("ceiling", "west"): (180, 90),
             ("floor", "east"): (0, 90), ("floor", "north"): (0, 0), ("floor", "south"): (0, 180), ("floor", "west"): (0, 270),
             ("wall", "east"): (90, 90), ("wall", "north"): (90, 0), ("wall", "south"): (90, 180), ("wall", "west"): (90, 270)}
YROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def rotspec(model_name, x=0, y=0, uvlock=False):
    d = {"model": f"skyabyss:block/{model_name}"}
    if x:
        d["x"] = x
    if y:
        d["y"] = y
    if uvlock:
        d["uvlock"] = True
    return d


def gen_all():
    for t in TITANS:
        save_tex(stone_tex(t), f"relic_{t}_stone")
        save_tex(trim_tex(t), f"relic_{t}_trim")
        save_tex(accent_tex(t), f"relic_{t}_accent")
        save_tex(glow_tex(t, True), f"relic_{t}_glow")
        save_tex(glow_tex(t, False), f"relic_{t}_glow_off")
        save_tex(flame_tex(t), f"relic_{t}_flame")
        wr(TEX / f"relic_{t}_flame.png.mcmeta", {"animation": {"frametime": 3, "interpolate": True}})
        save_tex(tablet_tex(t), f"relic_{t}_tablet")
        save_tex(seal_tex(t), f"relic_{t}_seal")
        # 模型
        for op in (False, True):
            wr(MOD / f"relic_cache_{t}{'_open' if op else ''}.json", chest_model(t, op))
        for lit in (False, True):
            wr(MOD / f"rest_hearth_{t}{'_lit' if lit else '_cold'}.json", hearth_model(t, lit))
        for on in (False, True):
            wr(MOD / f"relic_lever_{t}{'_on' if on else '_off'}.json", lever_model(t, on))
            wr(MOD / f"relic_button_{t}{'_pressed' if on else '_up'}.json", button_model(t, on))
            wr(MOD / f"relic_plate_{t}{'_pressed' if on else '_up'}.json", plate_model(t, on))
            wr(MOD / f"relic_bell_{t}{'_ring' if on else '_still'}.json", bell_model(t, on))
        w, s = tablet_models(t)
        wr(MOD / f"relic_tablet_{t}_wall.json", w)
        wr(MOD / f"relic_tablet_{t}_standing.json", s)
        wr(MOD / f"relic_seal_{t}.json", seal_model(t))

    # blockstates (titan を最後のキーに)
    v = {}
    for t in TITANS:
        for f, y in YROT.items():
            for op in (False, True):
                v[f"facing={f},open={'true' if op else 'false'},titan={t}"] = rotspec(f"relic_cache_{t}{'_open' if op else ''}", 0, y)
    wr(BST / "relic_cache.json", {"variants": v})
    v = {}
    for t in TITANS:
        for lit in (False, True):
            v[f"lit={'true' if lit else 'false'},titan={t}"] = rotspec(f"rest_hearth_{t}{'_lit' if lit else '_cold'}")
    wr(BST / "rest_hearth.json", {"variants": v})
    v = {}
    for t in TITANS:
        for (face, f), (x, y) in LEVER_ROT.items():
            for pw in (False, True):
                # バニラと同じ: powered=true が角度 -45 の模型、false が +45 の模型 (模型側は off=+45 / on=-45)
                v[f"face={face},facing={f},powered={'true' if pw else 'false'},titan={t}"] = rotspec(f"relic_lever_{t}{'_on' if pw else '_off'}", x, y)
    wr(BST / "relic_lever.json", {"variants": v})
    v = {}
    for t in TITANS:
        for (face, f), (x, y) in LEVER_ROT.items():
            for pw in (False, True):
                v[f"face={face},facing={f},powered={'true' if pw else 'false'},titan={t}"] = rotspec(
                    f"relic_button_{t}{'_pressed' if pw else '_up'}", x, y, uvlock=(face == "wall"))
    wr(BST / "relic_button.json", {"variants": v})
    v = {}
    for t in TITANS:
        for pw in (False, True):
            v[f"powered={'true' if pw else 'false'},titan={t}"] = rotspec(f"relic_plate_{t}{'_pressed' if pw else '_up'}")
    wr(BST / "relic_plate.json", {"variants": v})
    v = {}
    for t in TITANS:
        for f, y in YROT.items():
            for rg in (False, True):
                v[f"facing={f},ring={'true' if rg else 'false'},titan={t}"] = rotspec(f"relic_bell_{t}{'_ring' if rg else '_still'}", 0, y)
    wr(BST / "relic_bell.json", {"variants": v})
    v = {}
    for t in TITANS:
        for f, y in YROT.items():
            for st in (False, True):
                v[f"facing={f},standing={'true' if st else 'false'},titan={t}"] = rotspec(f"relic_tablet_{t}_{'standing' if st else 'wall'}", 0, y)
    wr(BST / "tablet.json", {"variants": v})
    wr(BST / "trial_seal.json", {"variants": {f"titan={t}": {"model": f"skyabyss:block/relic_seal_{t}"} for t in TITANS}})
    # 旧い封印の模型・絵は 鯨の様式へ置き換える
    # 持ち物の模型 (鯨の様式)
    for name, mdl in (("relic_cache", "relic_cache_whale"), ("rest_hearth", "rest_hearth_whale_lit"), ("relic_lever", "relic_lever_whale_on"),
                      ("relic_button", "relic_button_whale_up"), ("relic_plate", "relic_plate_whale_up"), ("relic_bell", "relic_bell_whale_still"),
                      ("trial_seal", "relic_seal_whale")):
        wr(ITM / f"{name}.json", {"parent": f"skyabyss:block/{mdl}"})


# ------------------------------------------------------------------ lang
LANG = {
    "ja_jp": {
        "block.skyabyss.relic_cache": "遺物の箱",
        "block.skyabyss.rest_hearth": "休み火",
        "block.skyabyss.relic_lever": "遺物のレバー",
        "block.skyabyss.relic_button": "遺物のボタン",
        "block.skyabyss.relic_plate": "遺物の感圧板",
        "block.skyabyss.relic_bell": "遺物の鐘",
        "block.skyabyss.tablet": "碑",
        "block.skyabyss.trial_seal": "試練済みの封印",
    },
    "en_us": {
        "block.skyabyss.relic_cache": "Relic Cache",
        "block.skyabyss.rest_hearth": "Rest Hearth",
        "block.skyabyss.relic_lever": "Relic Lever",
        "block.skyabyss.relic_button": "Relic Button",
        "block.skyabyss.relic_plate": "Relic Pressure Plate",
        "block.skyabyss.relic_bell": "Relic Bell",
        "block.skyabyss.tablet": "Inscribed Tablet",
        "block.skyabyss.trial_seal": "Cleared Trial Seal",
    },
}


def gen_lang():
    for lang, add in LANG.items():
        for ns in ("skyabyss", "skyabyss_trial"):
            p = ROOT / f"src/main/resources/assets/{ns}/lang/{lang}.json"
            d = json.load(open(p, encoding="utf-8"))
            ch = False
            for k, val in add.items():
                if ns == "skyabyss_trial" and k != "block.skyabyss.trial_seal":
                    continue
                if ns == "skyabyss" and k == "block.skyabyss.trial_seal":
                    continue
                if d.get(k) != val and (k in d or ns == "skyabyss" or k.endswith("trial_seal")):
                    if k.endswith("tablet") and k in d:
                        continue
                    d[k] = val
                    ch = True
            if ch:
                with open(p, "w", encoding="utf-8", newline="\n") as f:
                    json.dump(d, f, ensure_ascii=False, indent=2)
                    f.write("\n")


if __name__ == "__main__":
    gen_all()
    gen_lang()
    # 旧い封印の模型は新しい模型へ (blockstate が使わなくなったので消す)
    for old in (MOD / "trial_seal.json", TEX / "trial_seal.png"):
        if old.exists():
            old.unlink()
    print("ok")
