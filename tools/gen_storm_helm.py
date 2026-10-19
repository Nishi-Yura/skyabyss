# -*- coding: utf-8 -*-
"""雷翼の背の拍の舵 (25 の 5章 C) のブロック: 凪の舵 (淡い青と銀) と嵐の舵 (暗い石と金) のモデルと 32x32 の絵。

使い方 (リポジトリ直下で): python tools/gen_storm_helm.py
- 彫った台座 (二段の台・くびれた柱・襟) の上に、船の舵輪 (八角の縁・八本の輻・把手の宝石・中心の宝石)
- 状態 active=false は宝石が暗い、active=true は宝石が明るく光る (face の light_emission)。固定している間だけ光る
- どの部品も隣と 0.5 以上重ねて埋め込む (継ぎ目に隙間を作らない。閉じたメッシュ)
- 絵はすべて 32x32。乱数は seed 固定。出力は同じ物ができる
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "src/main/resources/assets/skyabyss"

KINDS = {
    "calm": {
        "ped": [(176, 196, 214), (150, 172, 194), (122, 146, 172), (96, 118, 146), (206, 222, 236)],
        "inlay": (226, 238, 248),
        "metal": [(236, 242, 248), (200, 212, 226), (160, 176, 196), (118, 134, 158), (250, 252, 255)],
        "gem_off": [(78, 98, 124), (60, 78, 102), (44, 58, 80), (96, 118, 146)],
        "gem_on": [(190, 245, 255), (130, 220, 250), (90, 190, 240), (240, 253, 255)],
    },
    "storm": {
        "ped": [(62, 64, 76), (48, 50, 62), (36, 38, 48), (26, 28, 36), (84, 86, 100)],
        "inlay": (226, 178, 70),
        "metal": [(232, 186, 82), (196, 146, 52), (150, 104, 36), (98, 66, 28), (252, 220, 130)],
        "gem_off": [(104, 72, 30), (80, 54, 24), (56, 38, 20), (130, 92, 40)],
        "gem_on": [(255, 244, 150), (255, 214, 80), (255, 176, 40), (255, 252, 214)],
    },
}


def tex(path, fn, seed):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (32, 32))
    px = im.load()
    for y in range(32):
        for x in range(32):
            px[x, y] = fn(x, y, rnd) + (255,)
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def ped_fn(k):
    pal = KINDS[k]["ped"]
    inlay = KINDS[k]["inlay"]

    def f(x, y, rnd):
        # 石積み (8 ずつの段、段ごとに半分ずらす) と目地
        row = y // 8
        off = 8 if row % 2 else 0
        bx = (x + off) % 16
        edge = y % 8 == 0 or bx == 0
        base = pal[(x * 3 + y * 5 + row) % 3]
        if edge:
            return pal[3]
        if y % 8 == 1 or bx == 1:
            return pal[4]
        # 彫り: 中ほどの段に波 (凪) / 稲妻の溝 (嵐) の象嵌
        if 12 <= y <= 13:
            wave = int(1.5 * math.sin(x * 0.7)) if k == "calm" else (1 if (x // 3) % 2 else 0)
            if y == 12 + (1 if wave > 0 else 0):
                return inlay
        return base if rnd.random() > 0.12 else pal[(x + y) % 3 + 1 if (x + y) % 3 + 1 < 4 else 0]
    return f


def metal_fn(k):
    pal = KINDS[k]["metal"]

    def f(x, y, rnd):
        # 縦の艶の筋と、縁の暗い線
        if x in (0, 31) or y in (0, 31):
            return pal[3]
        stripe = (x // 2 + (y // 8)) % 3
        c = pal[stripe]
        if x % 8 == 1:
            c = pal[4]
        return c if rnd.random() > 0.08 else pal[(stripe + 1) % 3]
    return f


def gem_fn(k, on):
    pal = KINDS[k]["gem_on" if on else "gem_off"]

    def f(x, y, rnd):
        # 面取りの宝石: 中心ほど明るい
        d = max(abs(x - 15.5), abs(y - 15.5)) / 15.5
        if d > 0.9:
            return pal[2]
        if d > 0.62:
            return pal[1]
        c = pal[0]
        if (x + y) % 11 == 0:
            c = pal[3]
        return c
    return f


# ---------------------------------------------------------------- モデル
CX, CY, CZ = 8.0, 10.0, 8.0
R_RIM = 4.0
R_SPOKE = 4.6
R_GEM = 5.0


def face(t):
    return {"texture": "#" + t}


def box(frm, to, t, rot=None, emit=False):
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for d in ("north", "south", "east", "west", "up", "down"):
        f = face(t)
        if emit:
            f["light_emission"] = 15
        el["faces"][d] = f
    if rot:
        angle, ox, oy = rot
        el["rotation"] = {"angle": angle, "axis": "z", "origin": [ox, oy, CZ]}
    return el


def wheel(on):
    gem = "gem"
    els = []
    # 彫った台座: 広い土台・段・くびれた柱・襟 (どれも隣と重ねる)
    els.append(box((2.5, 0, 2.5), (13.5, 2.5, 13.5), "ped"))
    els.append(box((4, 2, 4), (12, 4.5, 12), "ped"))
    els.append(box((6, 4, 6), (10, 8.2, 10), "ped"))
    els.append(box((5.2, 7.6, 5.2), (10.8, 9.0, 10.8), "ped"))
    # 軸 (襟から中心の宝石へ)
    els.append(box((7.2, 8.6, 7.2), (8.8, CY + 0.6, 8.8), "metal"))
    # 八本の輻: 縦・横と、±45 度の二本
    L = R_SPOKE
    els.append(box((CX - 0.5, CY - L, CZ - 0.6), (CX + 0.5, CY + L, CZ + 0.6), "metal"))
    els.append(box((CX - L, CY - 0.5, CZ - 0.6), (CX + L, CY + 0.5, CZ + 0.6), "metal"))
    els.append(box((CX - 0.5, CY - L, CZ - 0.6), (CX + 0.5, CY + L, CZ + 0.6), "metal", rot=(45, CX, CY)))
    els.append(box((CX - 0.5, CY - L, CZ - 0.6), (CX + 0.5, CY + L, CZ + 0.6), "metal", rot=(-45, CX, CY)))
    # 八角の縁: 縦横の辺と、斜めの辺 (中心から 45 度の位置、辺は縁に沿う向き)
    side = R_RIM * math.tan(math.pi / 8) * 2 + 0.9
    half = side / 2
    for sx, sy in ((0, 1), (0, -1)):
        cy = CY + sy * R_RIM
        els.append(box((CX - half, cy - 0.5, CZ - 0.8), (CX + half, cy + 0.5, CZ + 0.8), "metal"))
    for sx in (1, -1):
        cx = CX + sx * R_RIM
        els.append(box((cx - 0.5, CY - half, CZ - 0.8), (cx + 0.5, CY + half, CZ + 0.8), "metal"))
    d = R_RIM  # 斜めの辺の中心 = 縁の 45 度の点 (半径 R_RIM の八角の辺の中点)
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        cx = CX + sx * d * math.cos(math.pi / 4)
        cy = CY + sy * d * math.sin(math.pi / 4)
        ang = -45 if sx * sy > 0 else 45
        els.append(box((cx - half, cy - 0.5, CZ - 0.8), (cx + half, cy + 0.5, CZ + 0.8), "metal", rot=(ang, cx, cy)))
    # 中心の宝石と、把手の宝石 (八つ。輻の先、縁の外)
    els.append(box((CX - 1.5, CY - 1.5, CZ - 1.5), (CX + 1.5, CY + 1.5, CZ + 1.5), gem, emit=on))
    for k in range(8):
        a = math.pi / 4 * k
        gx = CX + R_GEM * math.cos(a)
        gy = CY + R_GEM * math.sin(a)
        els.append(box((gx - 0.8, gy - 0.8, CZ - 0.9), (gx + 0.8, gy + 0.8, CZ + 0.9), gem, emit=on))
    return els


def model(kind, on):
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": f"skyabyss:block/storm_helm_{kind}_ped",
            "ped": f"skyabyss:block/storm_helm_{kind}_ped",
            "metal": f"skyabyss:block/storm_helm_{kind}_metal",
            "gem": f"skyabyss:block/storm_helm_{kind}_gem_{'on' if on else 'off'}",
        },
        "elements": wheel(on),
    }


def main():
    for i, k in enumerate(KINDS):
        tex(A / f"textures/block/storm_helm_{k}_ped.png", ped_fn(k), 100 + i)
        tex(A / f"textures/block/storm_helm_{k}_metal.png", metal_fn(k), 200 + i)
        tex(A / f"textures/block/storm_helm_{k}_gem_off.png", gem_fn(k, False), 300 + i)
        tex(A / f"textures/block/storm_helm_{k}_gem_on.png", gem_fn(k, True), 400 + i)
        for on in (False, True):
            p = A / f"models/block/storm_helm_{k}_{'on' if on else 'off'}.json"
            p.write_text(json.dumps(model(k, on), indent=2) + "\n", encoding="utf-8")
    print("helm assets written")


if __name__ == "__main__":
    main()
