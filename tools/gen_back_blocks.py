"""雷翼の背の避雷の柱・戻しの鏡と、余燼の燠の灯のブロック (モデル・32x32 の絵・blockstate・アイテムのモデル)。

使い方 (リポジトリ直下で): python tools/gen_back_blocks.py
- 避雷の柱 (12 の 120): storm_rod (銅の台) / storm_rod_bulb (段の電球。lit で青白く光る) / storm_rod_shaft (軸) / storm_rod_tip (先の針)
  雷翼の言葉: 磨いた銅、青白い光、白い羽。バニラの避雷針・銅の電球は使わない
- 戻しの鏡 (reset_mirror): 前室の台の上の、取っ手つきの銅の手鏡。facing は鏡面の向き
- 燠の灯 (cinder_lamp, 13 の 6.1・00 の 126): 鎖で吊るす (hanging) / 床に置く灯り。鉄と黒石の籠の中に赤く熾る炭。光 12
絵はすべて 32x32 (art direction)。乱数は seed 固定。出力は同じ物ができる
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "src/main/resources/assets/skyabyss"
D = ROOT / "src/main/resources/data/skyabyss"

COPPER_L = (236, 170, 110)
COPPER = (200, 120, 72)
COPPER_M = (170, 98, 60)
COPPER_D = (126, 70, 44)
COPPER_DD = (84, 46, 32)
ICE = (110, 190, 255)
ICE_L = (235, 248, 255)
ICE_D = (58, 104, 168)
FEATHER = (226, 228, 222)
FEATHER_G = (160, 168, 182)
IRON = (74, 76, 86)
IRON_D = (44, 46, 54)
IRON_L = (110, 114, 126)
BASALT = (50, 50, 58)
BASALT_L = (72, 72, 82)


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, d):
    return clamp((c[0] + d, c[1] + d, c[2] + d))


def canvas(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (32, 32), fill)


def put(img, x, y, c):
    if 0 <= x < 32 and 0 <= y < 32:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def noise_fill(img, x0, y0, x1, y1, base, amp, rng):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, shade(base, rng.randint(-amp, amp)))


def save_png(img, rel):
    p = A / "textures" / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def bevel(img, x0, y0, x1, y1, light, dark):
    """矩形の縁に、左上は明るく右下は暗い縁を引く"""
    for x in range(x0, x1 + 1):
        put(img, x, y0, light)
        put(img, x, y1, dark)
    for y in range(y0, y1 + 1):
        put(img, x0, y, light)
        put(img, x1, y, dark)


# ================================================================ 避雷の柱 (雷翼: 磨いた銅・青白い光・白い羽)

def copper_plate(rng, w=32, h=32, rivets=True):
    """羽の筋が斜めに走る、磨いた銅の板 (32x32 の全面)"""
    img = canvas()
    for y in range(32):
        for x in range(32):
            c = shade(COPPER, rng.randint(-8, 8))
            if (x + y // 2) % 8 == 0:
                c = shade(COPPER_L, rng.randint(-5, 5))
            elif (x + y // 2) % 8 == 4:
                c = shade(COPPER_M, rng.randint(-4, 4))
            put(img, x, y, c)
    bevel(img, 0, 0, 31, 31, COPPER_L, COPPER_DD)
    bevel(img, 1, 1, 30, 30, COPPER_M, COPPER_D)
    if rivets:
        for (x, y) in ((4, 4), (27, 4), (4, 27), (27, 27)):
            put(img, x, y, COPPER_L)
            put(img, x + 1, y, COPPER_D)
            put(img, x, y + 1, COPPER_D)
            put(img, x + 1, y + 1, COPPER_DD)
    return img


def rod_side(rng):
    img = copper_plate(rng)
    # 帯: 青白い象嵌の線 (y 14〜17)
    for y in range(13, 19):
        for x in range(2, 30):
            put(img, x, y, COPPER_D if y in (13, 18) else COPPER_DD)
    for x in range(3, 29):
        put(img, x, 15, ICE)
        put(img, x, 16, ICE_L if x % 4 else ICE)
    # 羽の模様 (上の板): 中心の羽軸と左右に開く羽弁
    for i in range(0, 9):
        put(img, 16, 4 + i, FEATHER)
        put(img, 15, 4 + i, FEATHER_G)
        for s in (-1, 1):
            xx = 16 + s * (1 + i // 2) + (0 if s > 0 else -1)
            put(img, xx, 5 + i, FEATHER if i % 2 == 0 else FEATHER_G)
    # 下の板に、光の印 (三つの点)
    for x in (10, 16, 22):
        put(img, x, 24, ICE)
        put(img, x + 1, 24, ICE_L)
        put(img, x, 25, ICE_D)
    return img


def rod_top(rng):
    img = copper_plate(rng, rivets=False)
    cx = cy = 15.5
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - cx, y - cy)
            if 11.5 <= r < 13.5:
                put(img, x, y, COPPER_DD)
            elif 9.5 <= r < 11.5:
                put(img, x, y, ICE if (x + y) % 5 else ICE_L)
            elif 7.5 <= r < 9.5:
                put(img, x, y, COPPER_D)
            elif r < 5.5:
                put(img, x, y, shade(COPPER_M, rng.randint(-5, 5)))
            if 5.5 <= r < 7.5:
                put(img, x, y, COPPER_DD)
    bevel(img, 0, 0, 31, 31, COPPER_L, COPPER_DD)
    return img


def rod_pole(rng, tip=False):
    """細い銅の柱の面 (縦の円筒の陰)。tip なら上へいくほど青白く光る"""
    img = canvas()
    for y in range(32):
        for x in range(32):
            d = abs(x - 15.5) / 16.0
            base = shade(COPPER, int(30 - 90 * d * d) + rng.randint(-5, 5))
            if (y // 4) % 4 == 3 and 2 <= y % 4 <= 3 and not tip:
                base = shade(COPPER_D, rng.randint(-5, 5))
            put(img, x, y, base)
    if tip:
        for y in range(32):
            k = 1 - y / 31.0   # 上 (y=0) が先
            if k > 0.55:
                for x in range(32):
                    d = abs(x - 15.5) / 16.0
                    if d < 0.8:
                        t = (k - 0.55) / 0.45
                        # 銅 → 青白 → 白 (先がほぼ白く光る)
                        if t < 0.6:
                            c = tuple(int(a + (b - a) * (t / 0.6)) for a, b in zip(COPPER, ICE))
                        else:
                            c = tuple(int(a + (b - a) * ((t - 0.6) / 0.4)) for a, b in zip(ICE, ICE_L))
                        put(img, x, y, shade(c, int(-30 * d)))
    else:
        # 銅の羽根の節 (帯) の中に青白い点
        for y in (6, 22):
            for x in range(8, 24):
                put(img, x, y, COPPER_DD)
            for x in (13, 15, 17, 19):
                put(img, x, y + 1, ICE)
    return img


def rod_band(rng):
    """電球の上下の輪 (銅の帯)"""
    img = copper_plate(rng, rivets=False)
    for x in range(2, 30):
        put(img, x, 15, COPPER_DD)
        put(img, x, 16, COPPER_L)
    for x in (5, 11, 17, 23):
        put(img, x, 8, COPPER_L)
        put(img, x + 1, 8, COPPER_D)
        put(img, x, 24, COPPER_L)
        put(img, x + 1, 24, COPPER_D)
    return img


def rod_bulb(rng, lit):
    img = copper_plate(rng, rivets=False)
    # 窓 (ガラスの電球): x 8〜23、y 3〜28
    x0, x1, y0, y1 = 8, 23, 3, 28
    rect(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, COPPER_DD)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dx = (x - (x0 + x1) / 2) / ((x1 - x0) / 2)
            dy = (y - (y0 + y1) / 2) / ((y1 - y0) / 2)
            r = math.hypot(dx, dy * 0.8)
            if lit:
                # 中心が白く、外が青白い
                t = max(0.0, 1 - r)
                c = tuple(int(a + (b - a) * t) for a, b in zip(ICE, ICE_L))
                c = tuple(int(v * (0.75 + 0.25 * t)) for v in c)
                if r > 0.85:
                    c = shade(ICE_D, int(40 * (1 - r)))
            else:
                t = max(0.0, 1 - r)
                c = shade((54, 62, 80), int(18 * t) + rng.randint(-3, 3))
            put(img, x, y, c)
    # 銅の線 (フィラメント): ジグザグ
    fil = ICE_L if lit else COPPER_D
    pts = [(12, 8), (19, 11), (12, 14), (19, 17), (12, 20), (19, 23)]
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = max(abs(xb - xa), abs(yb - ya))
        for i in range(n + 1):
            put(img, xa + (xb - xa) * i // n, ya + (yb - ya) * i // n, fil)
    # ガラスの光沢
    for y in range(5, 12):
        put(img, 10, y, (170, 205, 245) if lit else (96, 108, 132))
    return img


# ================================================================ 戻しの鏡 (取っ手つきの銅の手鏡)

def reset_mirror_atlas(rng):
    """4 区画 (各 16x16 px = uv の 8x8): A 鏡面 / B 銅の縁・台 / C 取っ手の巻き / D 暗い銅"""
    img = canvas()
    # B: 銅 (16..31, 0..15)
    for y in range(16):
        for x in range(16):
            c = shade(COPPER, rng.randint(-8, 8))
            if (x + y // 2) % 6 == 0:
                c = shade(COPPER_L, rng.randint(-5, 5))
            put(img, 16 + x, y, c)
    bevel(img, 16, 0, 31, 15, COPPER_L, COPPER_DD)
    # A: 鏡面 (0..15, 0..15): 銅の縁と、白青い磨いたガラス。右上から左下へ光の筋
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(COPPER_D, rng.randint(-5, 5)))
    for y in range(2, 14):
        for x in range(2, 14):
            t = (x + (15 - y)) / 28.0
            c = tuple(int(a + (b - a) * t) for a, b in zip(ICE_D, ICE_L))
            put(img, x, y, shade(c, rng.randint(-3, 3)))
    for i in range(3, 12):
        put(img, i, 15 - i, (255, 255, 255))
        put(img, i + 1, 15 - i, ICE_L)
    bevel(img, 0, 0, 15, 15, COPPER_L, COPPER_DD)
    bevel(img, 1, 1, 14, 14, COPPER_DD, COPPER_M)
    # 羽の縁取り (四隅)
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)):
        put(img, x, y, FEATHER)
    # C: 取っ手の巻き (0..15, 16..31): 暗い銅に白い羽根の斜めの巻き
    for y in range(16):
        for x in range(16):
            c = shade(COPPER_D, rng.randint(-5, 5))
            if (x + y) % 5 in (0, 1):
                c = FEATHER if (x + y) % 5 == 0 else FEATHER_G
            put(img, x, 16 + y, c)
    # D: 暗い銅 (16..31, 16..31)
    for y in range(16):
        for x in range(16):
            put(img, 16 + x, 16 + y, shade(COPPER_DD, rng.randint(-5, 5)))
    bevel(img, 16, 16, 31, 31, COPPER_D, (60, 30, 22))
    return img


# ================================================================ 燠の灯

def cinder_lamp_atlas(rng):
    """A 籠の鉄 / B 熾 (光る炭) / C 鎖 / D 黒石の笠。各 16x16 px (uv の 8x8)"""
    img = canvas()
    # A: 鉄の籠 (0..15, 0..15): 暗い鉄に縁の明るい筋と縁の熾の赤
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(IRON, rng.randint(-7, 7)))
    bevel(img, 0, 0, 15, 15, IRON_L, IRON_D)
    for x in range(2, 14):
        put(img, x, 7, IRON_D)
        put(img, x, 8, IRON_L)
    put(img, 7, 2, (200, 70, 30))
    put(img, 8, 13, (200, 70, 30))
    # B: 熾 (16..31, 0..15): 暗い炭の地に赤橙のひび、中心に黄白の芯
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5) / 10.6
            core = max(0.0, 1.0 - r)
            base = (60 + 150 * core, 18 + 90 * core, 10 + 20 * core)
            crack = ((x * 3 + y * 5) % 11 in (0, 1)) or ((x * 5 - y * 2) % 13 == 0)
            if crack:
                base = (255, 120 + int(70 * core), 30)
            if core > 0.62:
                base = (255, 225 - int((1 - core) * 150), 120)
            put(img, 16 + x, y, clamp(tuple(v + rng.randint(-8, 8) for v in base)))
    # C: 鎖 (0..15, 16..31): 縦に並ぶ輪 (暗い鉄、縁だけ明るい)
    for y in range(16):
        for x in range(16):
            put(img, x, 16 + y, IRON_D)
    for y in range(16):
        for x in range(16):
            ring = ((y // 5) % 2 == 0 and 6 <= x <= 9) or ((y // 5) % 2 == 1 and 5 <= x <= 10)
            hole = ((y % 5) in (1, 2, 3)) and ((((y // 5) % 2 == 0) and x in (7, 8)) or (((y // 5) % 2 == 1) and x in (7, 8)))
            c = shade(IRON, rng.randint(-5, 5)) if ring else shade(IRON_D, -8)
            if ring and not hole and x in (6, 5):
                c = IRON_L
            if ring and hole:
                c = shade(IRON_D, -10)
            put(img, x, 16 + y, c)
    # D: 黒石の笠 (16..31, 16..31): 玄武岩に熾の赤い継ぎ目
    for y in range(16):
        for x in range(16):
            c = shade(BASALT, rng.randint(-6, 6))
            if (x + y) % 7 == 0:
                c = shade(BASALT_L, rng.randint(-3, 3))
            put(img, 16 + x, 16 + y, c)
    for x in range(1, 15):
        put(img, 16 + x, 16 + 14, (190, 62, 28))
    bevel(img, 16, 16, 31, 31, BASALT_L, (30, 30, 36))
    return img


# ================================================================ モデルの部品

FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, tex, uv=None, rotation=None, faces=None):
    """tex は '#name'。uv は {面: [u0,v0,u1,v1]} か、全面で同じ [..]。uv なしは座標から自動"""
    e = {"from": list(frm), "to": list(to), "faces": {}}
    for f in (faces or FACES):
        face = {"texture": tex}
        if isinstance(uv, dict):
            if f in uv:
                face["uv"] = uv[f]
        elif uv is not None:
            face["uv"] = uv
        e["faces"][f] = face
    if rotation:
        e["rotation"] = rotation
    return e


def model(textures, elements, display=True):
    m = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    return m


# ================================================================ 書き出し

def main():
    rng = random.Random(20261018)
    # ---- 避雷の柱
    save_png(rod_side(rng), "block/storm_rod_side.png")
    save_png(rod_top(rng), "block/storm_rod_top.png")
    save_png(rod_pole(rng), "block/storm_rod_pole.png")
    save_png(rod_pole(rng, tip=True), "block/storm_rod_tip.png")
    save_png(rod_band(rng), "block/storm_rod_band.png")
    save_png(rod_bulb(rng, False), "block/storm_rod_bulb_off.png")
    save_png(rod_bulb(rng, True), "block/storm_rod_bulb_on.png")

    # 台: 下の板 (16x8)、段 (12x4)、首 (6x4)。上面は輪の絵
    base_tex = {"particle": "skyabyss:block/storm_rod_side", "side": "skyabyss:block/storm_rod_side",
                "top": "skyabyss:block/storm_rod_top", "pole": "skyabyss:block/storm_rod_pole"}
    side_top = {"up": {"texture": "#top"}}
    els = [
        box((0, 0, 0), (16, 8, 16), "#side"),
        box((2, 8, 2), (14, 12, 14), "#side"),
        box((5, 12, 5), (11, 16, 11), "#pole"),
    ]
    els[0]["faces"]["up"] = {"texture": "#top", "uv": [0, 0, 16, 16]}
    els[0]["faces"]["down"] = {"texture": "#top"}
    els[1]["faces"]["up"] = {"texture": "#top", "uv": [2, 2, 14, 14]}
    els[1]["faces"]["down"] = {"texture": "#top", "uv": [2, 2, 14, 14]}
    write(A / "models/block/storm_rod.json", model(base_tex, els))
    write(A / "blockstates/storm_rod.json", {"variants": {"": {"model": "skyabyss:block/storm_rod"}}})

    # 軸: 銅の柱 (6x16) と、白青い羽の飾りの板 (x 方向の薄い板 2 枚)
    shaft_tex = {"particle": "skyabyss:block/storm_rod_pole", "pole": "skyabyss:block/storm_rod_pole", "band": "skyabyss:block/storm_rod_band"}
    els = [
        box((5, 0, 5), (11, 16, 11), "#pole"),
        box((3.5, 4, 5.5), (12.5, 6, 10.5), "#band"),
        box((4, 9, 5.5), (12, 10.5, 10.5), "#band"),
    ]
    write(A / "models/block/storm_rod_shaft.json", model(shaft_tex, els))
    write(A / "blockstates/storm_rod_shaft.json", {"variants": {"": {"model": "skyabyss:block/storm_rod_shaft"}}})

    # 針: 細い銅の針の先が青白く光る (下が太く、上が細い)
    tip_tex = {"particle": "skyabyss:block/storm_rod_tip", "tip": "skyabyss:block/storm_rod_tip", "pole": "skyabyss:block/storm_rod_pole",
               "band": "skyabyss:block/storm_rod_band"}
    els = [
        box((5.5, 0, 5.5), (10.5, 3, 10.5), "#band"),
        box((6.5, 3, 6.5), (9.5, 9, 9.5), "#pole"),
        box((7.25, 9, 7.25), (8.75, 16, 8.75), "#tip", uv={"north": [0, 0, 16, 7], "south": [0, 0, 16, 7], "east": [0, 0, 16, 7], "west": [0, 0, 16, 7]}),
    ]
    write(A / "models/block/storm_rod_tip.json", model(tip_tex, els))
    write(A / "blockstates/storm_rod_tip.json", {"variants": {"": {"model": "skyabyss:block/storm_rod_tip"}}})

    # 電球: 銅の輪 + ガラスの玉。lit で青白く光る
    for name, tex in (("off", "storm_rod_bulb_off"), ("on", "storm_rod_bulb_on")):
        bt = {"particle": "skyabyss:block/" + tex, "glass": "skyabyss:block/" + tex, "band": "skyabyss:block/storm_rod_band",
              "pole": "skyabyss:block/storm_rod_pole"}
        els = [
            box((3, 2, 3), (13, 14, 13), "#glass"),
            box((2, 0, 2), (14, 2, 14), "#band"),
            box((2, 14, 2), (14, 16, 14), "#band"),
            box((6, 0, 6), (10, 16, 10), "#pole"),
        ]
        # 玉の上下は輪が覆う。玉の面は x 3..13 の窓だけが見える
        write(A / f"models/block/storm_rod_bulb_{name}.json", model(bt, els))
    write(A / "blockstates/storm_rod_bulb.json", {"variants": {
        "lit=false": {"model": "skyabyss:block/storm_rod_bulb_off"},
        "lit=true": {"model": "skyabyss:block/storm_rod_bulb_on"}}})

    # ---- 戻しの鏡
    save_png(reset_mirror_atlas(rng), "block/reset_mirror.png")
    rm_tex = {"particle": "skyabyss:block/reset_mirror", "t": "skyabyss:block/reset_mirror"}
    A_, B_, C_, D_ = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16], [8, 8, 16, 16]
    els = [
        box((3.5, 0, 3.5), (12.5, 2, 12.5), "#t", uv=D_),
        box((4.5, 2, 4.5), (11.5, 3.5, 11.5), "#t", uv=B_),
        box((7, 3.5, 7), (9, 8, 9), "#t", uv=B_),
        {"from": [4, 7.5, 7.25], "to": [12, 15.5, 8.75],
         "rotation": {"origin": [8, 9, 8], "axis": "x", "angle": -22.5},
         "faces": {"north": {"texture": "#t", "uv": A_}, "south": {"texture": "#t", "uv": B_}, "east": {"texture": "#t", "uv": D_},
                   "west": {"texture": "#t", "uv": D_}, "up": {"texture": "#t", "uv": D_}, "down": {"texture": "#t", "uv": D_}}},
        # 取っ手: 台の右側に立つ短い柄と握り (引いて鏡を戻す)
        box((11.25, 2, 7.25), (12.75, 6.5, 8.75), "#t", uv=C_),
        box((10.5, 6.5, 6.5), (13.5, 8.5, 9.5), "#t", uv=C_),
    ]
    write(A / "models/block/reset_mirror.json", model(rm_tex, els))
    write(A / "blockstates/reset_mirror.json", {"variants": {
        "facing=north": {"model": "skyabyss:block/reset_mirror"},
        "facing=east": {"model": "skyabyss:block/reset_mirror", "y": 90},
        "facing=south": {"model": "skyabyss:block/reset_mirror", "y": 180},
        "facing=west": {"model": "skyabyss:block/reset_mirror", "y": 270}}})

    # ---- 燠の灯
    save_png(cinder_lamp_atlas(rng), "block/cinder_lamp.png")
    lt = {"particle": "skyabyss:block/cinder_lamp", "t": "skyabyss:block/cinder_lamp"}
    cage, ember, chain, cap = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16], [8, 8, 16, 16]

    def lamp(dy, hanging):
        """dy: 籠を下へずらす量 (置く灯りは床に座る)。吊る灯りは上に鎖をつなぐ"""
        e = []
        # 底の皿
        e.append(box((4.5, 1 - dy, 4.5), (11.5, 2.5 - dy, 11.5), "#t", uv=cap))
        # 熾 (光る炭の塊)
        e.append(box((5.75, 2.5 - dy, 5.75), (10.25, 8 - dy, 10.25), "#t", uv=ember))
        # 籠の柱 4 本
        for (x, z) in ((4.75, 4.75), (10.25, 4.75), (4.75, 10.25), (10.25, 10.25)):
            e.append(box((x, 2.5 - dy, z), (x + 1, 9 - dy, z + 1), "#t", uv=cage))
        # 籠の帯 (中ほどの輪)
        e.append(box((5.5, 5 - dy, 4.5), (10.5, 5.75 - dy, 5.5), "#t", uv=cage))
        e.append(box((5.5, 5 - dy, 10.5), (10.5, 5.75 - dy, 11.5), "#t", uv=cage))
        e.append(box((4.5, 5 - dy, 5.5), (5.5, 5.75 - dy, 10.5), "#t", uv=cage))
        e.append(box((10.5, 5 - dy, 5.5), (11.5, 5.75 - dy, 10.5), "#t", uv=cage))
        # 笠 (黒石) 二段
        e.append(box((4, 9 - dy, 4), (12, 10.5 - dy, 12), "#t", uv=cap))
        e.append(box((5.5, 10.5 - dy, 5.5), (10.5, 12 - dy, 10.5), "#t", uv=cap))
        if hanging:
            # 鎖: 向きを変えた二つの輪を重ね、天井の鎖へつなぐ
            e.append(box((7.25, 12 - dy, 7.5), (8.75, 14.5 - dy, 8.5), "#t", uv=[3, 8, 5, 16]))
            e.append(box((7.5, 12 - dy, 7.25), (8.5, 14.5 - dy, 8.75), "#t", uv=[3, 8, 5, 16]))
            e.append(box((7.25, 14 - dy, 7.5), (8.75, 16, 8.5), "#t", uv=[3, 8, 5, 16]))
            e.append(box((7.5, 14 - dy, 7.25), (8.5, 16, 8.75), "#t", uv=[3, 8, 5, 16]))
        else:
            # 持ち手の輪
            e.append(box((6.5, 12 - dy, 7.5), (9.5, 12.75 - dy, 8.5), "#t", uv=chain))
            e.append(box((6.5, 12 - dy, 7.5), (7.25, 14.5 - dy, 8.5), "#t", uv=chain))
            e.append(box((8.75, 12 - dy, 7.5), (9.5, 14.5 - dy, 8.5), "#t", uv=chain))
            e.append(box((6.5, 14 - dy, 7.5), (9.5, 14.75 - dy, 8.5), "#t", uv=chain))
        return e

    write(A / "models/block/cinder_lamp.json", model(lt, lamp(1, False)))
    write(A / "models/block/cinder_lamp_hanging.json", model(lt, lamp(0, True)))
    write(A / "blockstates/cinder_lamp.json", {"variants": {
        "hanging=false,waterlogged=false": {"model": "skyabyss:block/cinder_lamp"},
        "hanging=false,waterlogged=true": {"model": "skyabyss:block/cinder_lamp"},
        "hanging=true,waterlogged=false": {"model": "skyabyss:block/cinder_lamp_hanging"},
        "hanging=true,waterlogged=true": {"model": "skyabyss:block/cinder_lamp_hanging"}}})
    write(A / "models/item/cinder_lamp.json", {"parent": "skyabyss:block/cinder_lamp"})
    write(D / "loot_table/blocks/cinder_lamp.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "skyabyss:cinder_lamp"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # 作業台のレシピ: 燠の殻 + 鎖 + 鉄の粒 + 石炭
    write(D / "recipe/cinder_lamp.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": [" C ", "NHN", " K "],
        "key": {"C": {"item": "minecraft:chain"}, "N": {"item": "minecraft:iron_nugget"},
                "H": {"item": "skyabyss:cinder_husk"}, "K": {"item": "minecraft:coal"}},
        "result": {"id": "skyabyss:cinder_lamp", "count": 1}})
    print("wrote storm_rod*, reset_mirror, cinder_lamp")


if __name__ == "__main__":
    main()
