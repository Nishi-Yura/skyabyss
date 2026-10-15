"""岩鯨の背で手に入る装備・道具のテクスチャを作る。

使い方: python tools/gen_items.py [--preview 出力先.png]
出力先: src/main/resources/assets/skyabyss/textures/
  item/*.png : 32x32 のアイコン (16 種)
  models/armor/stone_whale_layer_1.png, stone_whale_layer_2.png : 着たときの見た目 (256x128、バニラの4倍の密度)

描き方:
- 光は左上から一定。輪郭からの距離を面の傾きとみなし、法線と光の内積を 4 段のランプに量子化する。
- 輪郭の 1px は、光の当たる側だけ色付きの縁、影の側は暗い輪郭線にする。
- 重なる面には、下の面に 1px の接触影を落とす。
- 乱数は全て seed 付きの numpy 生成器。Python の hash() は使わない。
"""
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# 停止中 (2026-10-15): このスクリプトは古い第 1 版の 32px 生成器。
# 採用済みのアイコン・ブロック (docs/roughs/v4/items_blocks/b、生成器は tools/rough/items_blocks_32.py) と
# 防具の見た目 (textures/models/armor) を上書きしてしまうので、実行すると何も書かずに終了する。
# 新しい絵は tools/rough/items_blocks_32.py で作り、docs/roughs/v4/items_blocks/b/ から手でコピーする。
# どうしても動かす必要があるときだけ、環境変数 SKYABYSS_ALLOW_GEN_ITEMS=1 を付ける。
# ---------------------------------------------------------------------------
import os as _os

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_GEN_ITEMS") != "1":
    sys.exit("tools/gen_items.py は停止中です: 採用済みの 32x32 テクスチャを上書きするため。"
             "新しい絵は tools/rough/items_blocks_32.py を使ってください (詳細はファイル先頭のコメント)。")

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/textures"
S = 32
K = 4  # 着る見た目: バニラ (64x32) の 1px を K x K で描く

# 光源: 左上・手前寄り
LIGHT = np.array([-0.55, -0.55, 0.63])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# 明暗 4 段 (暗 -> 明)
OUTLINE = np.array([20, 18, 28], float)
STONE = np.array([[34, 38, 48], [58, 63, 76], [88, 95, 110], [128, 136, 152]], float)
BONE = np.array([[136, 122, 100], [180, 168, 144], [216, 206, 184], [244, 238, 222]], float)
CYAN = np.array([[18, 68, 98], [36, 124, 164], [104, 212, 246], [200, 250, 255]], float)
GOLD = np.array([[92, 58, 22], [160, 112, 44], [218, 172, 84], [252, 230, 156]], float)
MOSS = np.array([[34, 56, 28], [60, 94, 46], [96, 140, 64], [150, 194, 104]], float)
VIOLET = np.array([[40, 22, 80], [84, 52, 150], [146, 110, 228], [208, 182, 255]], float)
TEAL = np.array([[10, 40, 46], [22, 84, 92], [50, 136, 142], [118, 202, 204]], float)
WOOD = np.array([[36, 26, 18], [64, 46, 32], [100, 74, 50], [136, 104, 72]], float)
LEATHER = np.array([[30, 20, 14], [58, 40, 28], [92, 66, 46], [126, 92, 62]], float)
MEAT = np.array([[64, 32, 26], [118, 62, 44], [160, 94, 64], [204, 138, 100]], float)
FAT = np.array([[150, 130, 106], [196, 180, 156], [222, 210, 188], [248, 242, 228]], float)
STEEL = np.array([[44, 50, 62], [88, 98, 116], [148, 158, 176], [214, 222, 236]], float)
CLOTH = np.array([[20, 32, 62], [40, 62, 114], [68, 102, 168], [110, 150, 218]], float)
RUNE = np.array([120, 230, 255], float)


# ---------------------------------------------------------------- 共通の道具
def coords():
    """ピクセル中心の座標 (yy, xx)"""
    return np.mgrid[0:S, 0:S] + 0.5


def poly(points):
    im = Image.new("L", (S, S), 0)
    ImageDraw.Draw(im).polygon([tuple(p) for p in points], fill=255)
    return np.asarray(im) > 0


def band(p0, p1, half_w):
    a, b = np.array(p0, float), np.array(p1, float)
    d = b - a
    n = np.array([-d[1], d[0]]) / np.hypot(d[0], d[1]) * half_w
    return [tuple(a + n), tuple(b + n), tuple(b - n), tuple(a - n)]


def seg(p0, p1, width=1.0):
    return poly(band(p0, p1, width / 2))


def disk(cx, cy, r):
    yy, xx = coords()
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def ring(cx, cy, r, t):
    yy, xx = coords()
    return np.abs(np.hypot(xx - cx, yy - cy) - r) <= t / 2


def rect(x0, x1, y0, y1):
    """ピクセル番号 (両端を含む) の矩形"""
    yy, xx = np.mgrid[0:S, 0:S]
    return (xx >= x0) & (xx <= x1) & (yy >= y0) & (yy <= y1)


def erode(m):
    p = np.pad(m, 1)
    return m & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]


def dilate(m):
    p = np.pad(m, 1)
    return m | p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:]


def blur3(a):
    p = np.pad(a, 1)
    h, w = a.shape
    return sum(p[1 + dy:1 + dy + h, 1 + dx:1 + dx + w] for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9.0


def depth(mask, cap=4):
    """輪郭からの深さ (輪郭=1, 内側ほど大きい, cap で頭打ち)"""
    d = np.zeros(mask.shape)
    cur = mask.copy()
    for _ in range(cap):
        d += cur
        cur = erode(cur)
    return d


def shade_of(mask):
    """深さの傾きから面の法線を作り、光との内積 s を返す"""
    d = depth(mask)
    gy, gx = np.gradient(d)
    n = np.stack([-gx, -gy, np.ones_like(d)], axis=-1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return d, n @ LIGHT


def noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // scale + 2, w // scale + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * scale, h + 2 * scale), Image.BICUBIC)
    return np.asarray(img, float)[scale:scale + h, scale:scale + w] / 255.0


def to_rgba(rgb, alpha):
    out = np.zeros((*rgb.shape[:2], 4), np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255)
    out[..., 3] = alpha
    return out


def save(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr).save(path)


class Canvas:
    """32x32 のアイコンを重ねて描くための画布"""

    def __init__(self):
        self.rgb = np.zeros((S, S, 3))
        self.alpha = np.zeros((S, S), bool)

    def put(self, mask, ramp, edge=None, spec=False):
        """マスクを面として描く。edge に ramp と同じ 4 段を渡すと、輪郭をその色で縁取る。"""
        ramp = np.asarray(ramp, float)
        d, s = shade_of(mask)
        lv = np.digitize(s, [0.2, 0.6, 0.8])
        rgb = ramp[lv]
        rim = mask & (d < 1.5)
        if edge is None:
            rgb[rim] = OUTLINE
            rgb[rim & (s >= 0.6)] = ramp[1]
        else:
            rgb[rim] = np.asarray(edge, float)[lv[rim]]
        if spec:
            hi = mask & (d < 2.5) & (s >= 0.8) & ~rim
            rgb[hi] = rgb[hi] * 0.35 + 255 * 0.65
        near = dilate(mask) & ~mask & self.alpha
        self.rgb[near] *= 0.55
        self.rgb[mask] = rgb[mask]
        self.alpha |= mask

    def set(self, mask, color):
        color = np.asarray(color, float)
        if color.ndim == 1:
            self.rgb[mask] = color
        else:
            self.rgb[mask] = color[mask]
        self.alpha |= mask

    def glow(self, mask, color, strength=0.5):
        a = blur3(blur3(mask.astype(float)))
        a = np.clip(a * strength * 4, 0, 1) * self.alpha
        c = np.asarray(color, float)
        self.rgb = self.rgb * (1 - a[..., None]) + c * a[..., None]

    def image(self):
        return to_rgba(self.rgb, np.where(self.alpha, 255, 0).astype(np.uint8))


# ---------------------------------------------------------------- アイコン (32x32)
def greatsword():
    c = Canvas()
    yy, xx = coords()
    blade = poly([(10, 20), (22, 7), (27, 1), (30, 2), (29, 6), (16, 24), (12, 25)])
    fuller = seg((14, 20), (24, 9), 0.9) & blade
    guard = seg((9, 20), (17, 28), 3.0)
    grip = seg((12, 24), (5, 31), 2.4)
    pommel = disk(3.5, 29.5, 2.2)
    c.put(blade, BONE, spec=True)
    c.set(fuller, BONE[1])
    c.put(guard, STONE)
    c.put(grip, LEATHER)
    c.set(grip & (np.floor(xx + yy) % 3 == 0), LEATHER[0])
    c.put(pommel, STONE)
    c.set(seg((14, 18), (16, 16), 0.9) & blade, RUNE)
    return c.image()


def charm():
    c = Canvas()
    body = disk(16, 18, 10)
    cord = (seg((16, 9), (16, 3), 1.4) | ring(16, 3.5, 3.2, 1.4)) & ~body
    c.put(cord, LEATHER)
    c.put(body, STONE)
    for x, y in ((16.5, 9.5), (16.5, 26.5), (7.5, 18.5), (25.5, 18.5)):
        c.set(disk(x, y, 0.9) & body, BONE[3])
    rune = ring(16.5, 18.5, 6.2, 1.3) & body
    c.set(rune, RUNE)
    c.set(disk(16.5, 18.5, 2.4) & body, [210, 250, 255])
    c.glow(rune, RUNE, 0.6)
    return c.image()


def jerky():
    c = Canvas()
    for x0, y0 in [(5, 8), (10, 12), (15, 8)]:
        m = poly([(x0, y0), (x0 + 8, y0 - 2), (x0 + 11, y0 + 14), (x0 + 3, y0 + 17)])
        c.put(m, MEAT)
        c.set(seg((x0 + 2, y0 + 4), (x0 + 9, y0 + 7), 0.8) & m, FAT[1])
        c.set(seg((x0 + 4, y0 + 10), (x0 + 8, y0 + 12), 0.8) & m, FAT[2])
    return c.image()


def armor_icon(kind):
    c = Canvas()
    if kind == "helmet":
        dome = poly([(6, 22), (6, 13), (9, 6), (16, 3), (23, 6), (26, 13), (26, 22), (21, 22), (21, 15), (11, 15), (11, 22)])
        c.put(dome, STONE)
        opening = poly([(11, 15), (21, 15), (21, 22), (11, 22)])
        c.set(opening, [14, 16, 22])                         # 顔の開口部は奥が暗い
        chin = poly([(12, 19), (20, 19), (20, 22), (12, 22)])
        c.put(chin, STONE)                                   # 顎当て
        c.set(seg((16, 4), (16, 9), 0.9) & dome, STONE[0])
        c.set(rect(8, 24, 9, 9) & dome, BONE[2])            # 眉の骨
        c.set(rect(9, 23, 11, 12) & dome, [10, 12, 16])      # 覗き穴の奥は真っ暗
        c.set(rect(9, 23, 11, 11) & dome, [70, 78, 92])      # 覗き穴の上の縁
        rune = (rect(10, 22, 6, 6) | rect(11, 11, 5, 5) | rect(16, 16, 5, 5) | rect(21, 21, 5, 5)) & dome
        c.set(rune, RUNE)
        c.glow(rune, RUNE, 0.5)
        c.set(rect(6, 10, 20, 21) & dome, BONE[2])           # 頬当ての骨縁
        c.set(rect(22, 26, 20, 21) & dome, BONE[2])
        for x, y in ((8.5, 17.5), (23.5, 17.5)):
            c.set(disk(x, y, 0.9) & dome, BONE[3])
    elif kind == "chestplate":
        whole = poly([(5, 7), (12, 5), (16, 8), (20, 5), (27, 7), (25, 14), (23, 13), (23, 27), (9, 27), (9, 13), (7, 14)])
        c.put(whole, STONE)
        c.set(rect(9, 23, 19, 19) & whole, STONE[0])          # 胸板と腹当ての継ぎ目
        c.set(rect(9, 23, 20, 20) & whole, STONE[3])
        c.put(poly([(12, 5), (16, 8), (20, 5), (20, 7), (16, 10), (12, 7)]), BONE)   # 襟の骨
        c.set(rect(9, 23, 26, 26) & whole, BONE[2])           # 裾の骨縁
        rune = (rect(15, 16, 12, 24) | rect(13, 18, 14, 14) | rect(13, 18, 18, 18) | rect(13, 18, 22, 22)) & whole
        c.set(rune, RUNE)
        c.glow(rune, RUNE, 0.5)
        for x, y in ((11.5, 11.5), (21.5, 11.5), (11.5, 22.5), (21.5, 22.5)):
            c.set(disk(x, y, 0.9) & whole, BONE[3])
    elif kind == "leggings":
        waist = poly([(8, 5), (24, 5), (24, 9), (8, 9)])
        left = poly([(7, 9), (15, 9), (15, 27), (7, 27)])
        right = poly([(17, 9), (25, 9), (25, 27), (17, 27)])
        c.put(waist, STONE)
        c.set(rect(8, 24, 5, 5) & waist, BONE[2])            # 帯の上縁
        c.set(rect(15, 16, 6, 7) & waist, RUNE)              # 帯の中央の紋
        c.put(left, STONE)
        c.put(right, STONE)
        for m in (left, right):
            c.set(rect(7, 25, 25, 26) & m, BONE[2])          # 裾の骨縁
        for x, y in ((10.5, 7.5), (21.5, 7.5)):
            c.set(disk(x, y, 0.8) & waist, BONE[3])
        c.set(seg((11, 15), (11, 22), 0.8) & left, STONE[0])  # 脚の板の継ぎ目
        c.set(seg((21, 15), (21, 22), 0.8) & right, STONE[0])
    elif kind == "boots":
        left = poly([(5, 13), (12, 13), (12, 22), (14, 26), (5, 26)])
        right = poly([(19, 13), (26, 13), (26, 26), (17, 26), (19, 22)])
        for m in (left, right):
            c.put(m, STONE)
            c.set(rect(0, 31, 13, 14) & m, BONE[2])          # 筒の上縁の骨
            c.set(rect(0, 31, 25, 26) & m, STEEL[0])          # 靴底
        c.set(disk(9.5, 18.5, 0.9) & left, BONE[3])          # 留め具
        c.set(disk(22.5, 18.5, 0.9) & right, BONE[3])
        c.set(rect(9, 9, 22, 23) & left, RUNE)
        c.set(rect(22, 22, 22, 23) & right, RUNE)
    return c.image()


def captain_pike():
    c = Canvas()
    haft = seg((2, 30), (23, 9), 2.0)
    blade = poly([(20, 11), (16, 8), (21, 1), (29, 0), (31, 2), (26, 11)])
    ferrule = seg((15, 17), (19, 13), 2.6)
    wrap = seg((9, 23), (13, 19), 2.6)
    c.put(haft, WOOD)
    c.put(blade, STEEL, edge=GOLD, spec=True)
    c.set(seg((22, 9), (28, 2), 0.8) & blade, STEEL[0])     # 刃の樋
    c.set(seg((23, 7), (27, 4), 0.8) & blade, RUNE)         # 刃の紋
    c.put(ferrule, GOLD, edge=GOLD)
    c.put(wrap, CLOTH)
    return c.image()


def waterway_fang():
    c = Canvas()
    blade = poly([(11, 20), (15, 12), (21, 6), (28, 2), (27, 7), (23, 13), (18, 19), (13, 23)])
    guard = seg((9, 19), (15, 25), 2.0)
    grip = seg((4, 28), (10, 22), 2.6)
    c.put(blade, BONE, spec=True)
    c.put(guard, GOLD, edge=GOLD)
    c.put(grip, TEAL)
    c.set(seg((17, 11), (23, 6), 0.9) & blade, [200, 250, 255])   # 水の光
    return c.image()


def moss_talisman():
    c = Canvas()
    yy, xx = coords()
    ang = np.arctan2(yy - 18.5, xx - 16)
    rad = np.hypot(xx - 16, yy - 18.5)
    reff = 9.2 + 0.5 * np.sin(3 * ang + 1.0) + 0.35 * np.cos(5 * ang)
    pebble = rad < reff
    cord = ring(16, 6, 3.2, 1.5) & (yy < 9)
    c.put(cord, LEATHER)
    c.put(pebble, STONE)
    mossy = pebble & (rad > reff * 0.45) & (noise(S, S, 3, 232) > 0.55) & (yy < 22)
    c.put(mossy, MOSS)
    c.set(mossy & (xx < 16) & (yy < 14) & ~erode(mossy), MOSS[3])
    return c.image()


def bone_spike_arrow():
    c = Canvas()
    fin_a = poly([(6, 26), (9, 23), (3, 18), (2, 21)])
    fin_b = poly([(6, 26), (9, 23), (14, 28), (10, 30)])
    shaft = seg((4, 28), (23, 9), 1.8)
    head = poly([(31, 1), (20, 6), (26, 12)])
    barb_a = poly([(20, 6), (17, 5), (21, 10)])
    barb_b = poly([(26, 12), (27, 17), (22, 12)])
    c.put(fin_a, CLOTH)
    c.put(fin_b, CLOTH)
    c.put(shaft, WOOD)
    c.put(head, BONE, spec=True)
    c.put(barb_a, BONE)
    c.put(barb_b, BONE)
    return c.image()


def stone_whale_core():
    c = Canvas()
    yy, xx = coords()
    r = np.hypot(xx - 16, yy - 16)
    orb = r < 8.5
    t = np.clip(r / 8.5, 0, 1)[..., None]
    glow = np.array([236, 252, 255]) + (np.array([30, 136, 180]) - np.array([236, 252, 255])) * t
    c.set(orb, glow)
    c.put(ring(16, 16, 9.5, 2.6), STONE)
    c.put(poly([(9, 2), (23, 2), (20, 6), (12, 6)]), STONE)
    c.put(poly([(9, 30), (23, 30), (20, 26), (12, 26)]), STONE)
    for bx in (10.5, 16.0, 21.5):
        c.put(seg((bx, 4), (bx, 28), 1.8), BONE)
    c.set(disk(12.5, 12.5, 1.5), [255, 255, 255])
    c.glow(orb, CYAN[2], 0.3)
    return c.image()


def stone_whale_shard():
    c = Canvas()
    shard = poly([(7, 22), (10, 12), (16, 7), (21, 10), (26, 6), (25, 15), (28, 21), (22, 27), (14, 26), (8, 28), (5, 25)])
    c.put(shard, STONE)
    vein = np.zeros((S, S), bool)
    for p0, p1 in [((8, 23), (12, 19)), ((12, 19), (15, 20)), ((15, 20), (19, 14)), ((19, 14), (24, 12))]:
        vein |= seg(p0, p1, 1.2)
    c.set(vein & shard, BONE[2])
    return c.image()


def resonance_crystal():
    c = Canvas()
    left = poly([(8, 11), (12, 15), (12, 28), (6, 28), (4, 18)])
    right = poly([(22, 12), (27, 9), (28, 20), (26, 28), (21, 28)])
    main = poly([(16, 2), (21, 10), (20, 28), (12, 28), (11, 10)])
    c.put(left, VIOLET, spec=True)
    c.put(right, CYAN, spec=True)
    c.put(main, CYAN, spec=True)
    c.set(seg((13.5, 6), (13.5, 24), 0.6) & main, [235, 252, 255])
    c.set(seg((23.5, 14), (23.5, 24), 0.6) & right, [210, 240, 255])
    c.set(disk(15.5, 4.5, 0.8) & main, [255, 255, 255])
    c.glow(main, CYAN[2], 0.25)
    return c.image()


def resonance_shard():
    c = Canvas()
    shard = poly([(16, 9), (21, 15), (18, 24), (14, 24), (11, 15)])
    left = poly([(16, 9), (11, 15), (14, 24), (16, 24)])
    right = shard & ~left
    c.put(left, VIOLET, spec=True)
    c.put(right, CYAN, spec=True)
    core = disk(16, 15.5, 1.1) & shard
    c.set(core, [240, 252, 255])
    c.glow(core, RUNE, 0.4)
    return c.image()


def stone_whale_plating():
    c = Canvas()
    yy, xx = coords()
    d = np.hypot((xx - 16) / 12.5, (yy - 13) / 11.5)
    plate = (d < 1.0) & (yy < 26)
    c.put(plate, STONE, edge=GOLD)
    c.set((d > 0.8) & plate, GOLD[2])                  # 金の縁取り
    for a in np.radians([200, 245, 295, 340]):
        rx, ry = 16 + 12.5 * 0.68 * np.cos(a), 13 + 11.5 * 0.68 * np.sin(a)
        c.set(disk(rx, ry, 1.5) & plate, BONE[3])
        c.set(disk(rx, ry, 0.6) & plate, [60, 54, 46])
    gem = (np.abs(xx - 16) + np.abs(yy - 13)) < 2.6
    c.set(gem & plate, RUNE)
    c.set((np.abs(xx - 16) + np.abs(yy - 13)) < 1.0, [235, 252, 255])
    c.glow(gem & plate, RUNE, 0.5)
    return c.image()


# ---------------------------------------------------------------- 着たときの見た目 (64x32 の配置を K 倍で描く)
def box_faces(u, v, w, h, d):
    """ボックスUVの各面の (x, y, 幅, 高さ, 面の種類)"""
    return [
        (u + d, v, w, d, "top"), (u + d + w, v, w, d, "bottom"),
        (u, v + d, d, h, "side"), (u + d, v + d, w, h, "front"),
        (u + d + w, v + d, d, h, "side"), (u + 2 * d + w, v + d, w, h, "back"),
    ]


def rivet(p, r, c):
    """2px の骨の鋲。左上に光、右下に影、真下に落ち影"""
    H, W = p.shape[:2]
    if 0 <= r and r + 3 <= H and 0 <= c and c + 2 <= W:
        p[r:r + 2, c:c + 2] = BONE[2]
        p[r, c] = BONE[3]
        p[r + 1, c + 1] = BONE[0]
        p[r + 2, c:c + 2] = OUTLINE


def bone_bar(p, r0, r1, c0, c1, vertical=False):
    """骨の帯。光の当たる縁を明るく、影の縁を暗くして立体の稜線を出す"""
    H, W = p.shape[:2]
    if vertical:
        c1 = min(c1, W - 1)
        if c1 - c0 < 3:
            return
        p[r0:r1, c0] = BONE[3]
        p[r0:r1, c0 + 1:c1 - 1] = BONE[2]
        p[r0:r1, c1 - 1] = BONE[0]
    else:
        r1 = min(r1, H - 1)
        if r1 - r0 < 3:
            return
        p[r0, c0:c1] = BONE[3]
        p[r0 + 1:r1, c0:c1] = BONE[2]
        p[r1, c0:c1] = BONE[0]


def base_plate(H, W, mult, seed):
    """石の板: 色味の違う 3 種のどれか、斑 (まだら)、欠けた角、細いひび割れ"""
    g = np.random.default_rng(seed)
    tex = noise(H, W, 3, seed) * 0.6 + noise(H, W, 1, seed + 1) * 0.4
    tone = [0.9, 1.0, 1.1][seed % 3]
    plate = STONE[2] * mult * tone * (0.88 + 0.24 * tex)[..., None]
    yy, xx = np.mgrid[0:H, 0:W]
    plate[yy < 2] *= 1.28                       # 上縁の光
    plate[yy >= H - 2] *= 0.55                  # 下縁の影
    plate[(xx < 2) & (yy >= 2)] *= 1.12
    plate[(xx >= W - 2) & (yy >= 2)] *= 0.8
    if W >= 8 and H >= 8:                       # 角の欠け
        for r, c in ((0, W - 1), (H - 1, 0)):
            plate[r, c] = OUTLINE
    for _ in range(2):                          # 細いひび割れ
        r, c = int(g.integers(3, H - 3)), int(g.integers(3, W - 3))
        for _ in range(6):
            plate[r, c] *= 0.5
            r = min(max(r + int(g.integers(-1, 2)), 1), H - 2)
            c = min(max(c + int(g.integers(-1, 2)), 1), W - 2)
    return plate


def bone_border(p, t, mult):
    """縁の骨飾り: 外側は暗く、内側に明るい稜線"""
    H, W = p.shape[:2]
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.minimum(np.minimum(yy, xx), np.minimum(H - 1 - yy, W - 1 - xx))
    edge = d < t
    p[edge] = BONE[2] * mult
    p[d == 0] = BONE[1] * mult
    if t >= 2:
        p[d == 1] = BONE[3] * mult
    p[edge & (yy >= H - t)] *= 0.7


def seams(p, seed):
    """横の板の継ぎ目 (バニラ 3px ごと)。継ぎ目の下は少し明るく、上は暗い"""
    H = p.shape[0]
    for r in range(12, H - 2, 12):
        p[r] *= 0.5
        p[r + 1] *= 1.15


def scales(p, seed, row_h=6, seg_w=8):
    """鱗状の板 (ラメラー): 段ごとに半分ずらし、一枚ごとに明度を変える"""
    H, W = p.shape[:2]
    g = np.random.default_rng(seed + 500)
    yy, xx = np.mgrid[0:H, 0:W]
    row = yy // row_h
    off = (row % 2) * (seg_w // 2)
    cell = (xx + off) // seg_w
    tone = g.uniform(0.86, 1.14, size=(H // row_h + 2, W // seg_w + 3))
    p *= tone[row, cell][..., None]
    lower = (yy % row_h) == row_h - 1
    p[lower] *= 0.4                             # 鱗の下端の影
    p[(yy % row_h) == 0] *= 1.25                # 鱗の上端の光
    p[((xx + off) % seg_w == 0) & ~lower] *= 0.6  # 鱗の間の隙間


def moss_patch(p, seed, count):
    """苔の塊を継ぎ目の隙間に置く (1px の点ではなく 3x4 程度の塊)"""
    g = np.random.default_rng(seed)
    H, W = p.shape[:2]
    for _ in range(count):
        k = int(g.integers(1, max(2, H // 12)))
        r = min(max(12 * k - 2, 2), H - 5)
        c = int(g.integers(2, max(3, W - 5)))
        blob = g.random((3, 4)) > 0.3
        for i in range(3):
            for j in range(4):
                if blob[i, j] and c + j < W:
                    p[r + i, c + j] = MOSS[2] if (i + j) % 2 == 0 else MOSS[1]
        if c + 1 < W:
            p[r + 1, c + 1] = MOSS[3]


def head_front(p, W, H):
    bone_bar(p, int(H * 0.3), int(H * 0.3) + 3, 2, W - 2)     # 眉の稜
    r0, r1 = int(H * 0.42), int(H * 0.52)
    c0, c1 = int(W * 0.16), int(W * 0.84)
    p[r0:r1, c0:c1] = [10, 12, 16]                              # 覗き穴の奥は真っ暗
    p[r0, c0:c1] = [96, 104, 120]                               # 覗き穴の上の縁
    mid = (r0 + r1) // 2
    p[mid, c0 + 1:c1 - 1] = [70, 200, 235]                      # 覗き穴の中の光の線
    p[mid - 1, c0 + 1:c1 - 1] = [22, 66, 92]
    p[mid + 1, c0 + 1:c1 - 1] = [22, 66, 92]
    rr = int(H * 0.2)
    p[rr:rr + 2, int(W * 0.2):int(W * 0.8)] = RUNE              # 紋様の線
    for cc in (0.2, 0.5, 0.8):
        p[rr - 3:rr, int(W * cc) - 1:int(W * cc) + 1] = RUNE
    rivet(p, int(H * 0.74), int(W * 0.12))
    rivet(p, int(H * 0.74), int(W * 0.8))


def head_side(p, W, H):
    bone_bar(p, int(H * 0.66), int(H * 0.66) + 3, 1, W - 1)     # 頬当ての縁
    rivet(p, int(H * 0.3), W // 2 - 1)
    rivet(p, int(H * 0.45), W // 2 - 1)


def head_top(p, W, H):
    bone_bar(p, 1, H - 1, W // 2 - 1, W // 2 + 2, vertical=True)  # 兜の鶏冠
    moss_patch(p, 301, 2)


def head_back(p, W, H):
    bone_bar(p, 1, H - 1, W // 2 - 1, W // 2 + 2, vertical=True)  # 背の稜
    rivet(p, int(H * 0.5), W // 2 - 1)


def chest_front(p, W, H):
    belt = int(H * 0.6)
    bone_bar(p, belt, belt + 3, 1, W - 1)                       # 腰の骨帯
    cx = W // 2
    top, bot = int(H * 0.2), int(H * 0.82)
    for r in range(top + 3, bot - 2, 6):                        # 紋様の横木
        p[r, cx - 5:cx + 5] = CYAN[1]
    p[top:bot, cx - 1:cx + 1] = RUNE                            # 紋様の縦線
    for r, c in ((int(H * 0.1), int(W * 0.2)), (int(H * 0.1), int(W * 0.7))):
        rivet(p, r, c)


def chest_side(p, W, H):
    bone_bar(p, int(H * 0.55), int(H * 0.55) + 3, 1, W - 1)
    rivet(p, int(H * 0.2), W // 2 - 1)
    rivet(p, int(H * 0.85), W // 2 - 1)


def chest_back(p, W, H):
    bone_bar(p, 1, H - 1, W // 2 - 1, W // 2 + 2, vertical=True)  # 背骨の稜
    rivet(p, int(H * 0.2), W // 2 - 1)
    rivet(p, int(H * 0.8), W // 2 - 1)


def arm_front(p, W, H):
    for c in (W // 4, 3 * W // 4 - 1):                          # 縦のストラップ
        bone_bar(p, 1, H - 1, c - 1, c + 1, vertical=True)
    for r in (int(H * 0.35), int(H * 0.75)):                    # 横のストラップ
        bone_bar(p, r, r + 2, 1, W - 1)
    rivet(p, int(H * 0.35), W // 2 - 1)
    rivet(p, int(H * 0.75), W // 2 - 1)


def arm_side(p, W, H):
    rivet(p, int(H * 0.5), W // 2 - 1)


def arm_top(p, W, H):
    """肩当て: 中央が盛り上がった丸い板"""
    yy, xx = np.mgrid[0:H, 0:W]
    dist = np.hypot((yy - H / 2) / H, (xx - W / 2) / W)
    p *= (1.25 - 0.6 * dist)[..., None]
    rivet(p, H // 2 - 1, W // 2 - 1)
    moss_patch(p, 303, 1)


def leg_front(p, W, H):
    rivet(p, int(H * 0.3), W // 2 - 1)
    rivet(p, int(H * 0.6), W // 2 - 1)


def leg_side(p, W, H):
    rivet(p, int(H * 0.4), W // 2 - 1)


def boot_front(p, W, H):
    bone_bar(p, int(H * 0.84), int(H * 0.84) + 3, 1, W - 1)     # つま先の金具
    bone_bar(p, int(H * 0.7), int(H * 0.7) + 2, 1, W - 1)       # 足首の板の継ぎ目
    rivet(p, int(H * 0.5), W // 2 - 1)


def boot_side(p, W, H):
    bone_bar(p, int(H * 0.7), int(H * 0.7) + 2, 1, W - 1)
    rivet(p, int(H * 0.4), W // 2 - 1)


def paint_face(rgb, alpha, x, y, w, h, face, seed, style="blocks", rows=None, detail=None, moss=0):
    X, Y, W, H = x * K, y * K, w * K, h * K
    mult = {"top": 1.12, "front": 1.0, "side": 0.84, "back": 0.8, "bottom": 0.6}[face]
    plate = base_plate(H, W, mult, seed)
    if style == "scales":
        scales(plate, seed)
    else:
        seams(plate, seed)
    t = 2 if min(W, H) >= 8 else 1
    bone_border(plate, t, mult)
    if moss:
        moss_patch(plate, seed + 9, moss)
    if detail is not None:
        detail(plate, W, H)
    a = np.full((H, W), 255, np.uint8)
    if rows is not None:
        r0, r1 = round(H * rows[0]), round(H * rows[1])
        a[:r0] = 0
        a[r1:] = 0
        bone_bar(plate, r0, r0 + 2, 0, W)                       # 切り口の骨縁
    rgb[Y:Y + H, X:X + W] = np.clip(plate, 0, 255)
    alpha[Y:Y + H, X:X + W] = a


def armor_layers():
    rgb1 = np.zeros((32 * K, 64 * K, 3))
    a1 = np.zeros((32 * K, 64 * K), np.uint8)
    rgb2 = np.zeros_like(rgb1)
    a2 = np.zeros_like(a1)
    seed = 100

    def put(canvas_rgb, canvas_a, face_box, style, detail, rows=None, moss=0):
        nonlocal seed
        x, y, w, h, f = face_box
        paint_face(canvas_rgb, canvas_a, x, y, w, h, f, seed, style=style, rows=rows,
                   detail=detail.get(f) if isinstance(detail, dict) else detail, moss=moss)
        seed += 1

    # layer_1: 兜 (頭), 胴, 腕, ブーツ (脚の下 38%)
    head = {"front": head_front, "side": head_side, "top": head_top, "back": head_back}
    for fb in box_faces(0, 0, 8, 8, 8):
        put(rgb1, a1, fb, "blocks", head)
    body = {"front": chest_front, "side": chest_side, "back": chest_back}
    for fb in box_faces(16, 16, 8, 12, 4):
        put(rgb1, a1, fb, "scales", body, moss=1 if fb[4] == "top" else 0)
    arm = {"front": arm_front, "side": arm_side, "top": arm_top}
    for fb in box_faces(40, 16, 4, 12, 4):
        put(rgb1, a1, fb, "scales", arm)
    boot = {"front": boot_front, "side": boot_side}
    for x, y, w, h, f in box_faces(0, 16, 4, 12, 4):
        rows = None if f in ("top", "bottom") else (0.62, 1.0)
        put(rgb1, a1, (x, y, w, h, f), "blocks", boot, rows=rows)
    # layer_2: 腰当て (胴の下 1/3) と脚当て (脚の上 3/4)
    for x, y, w, h, f in box_faces(16, 16, 8, 12, 4):
        rows = None if f in ("top", "bottom") else (0.66, 1.0)
        put(rgb2, a2, (x, y, w, h, f), "scales", {"front": chest_side}, rows=rows)
    legs = {"front": leg_front, "side": leg_side}
    for x, y, w, h, f in box_faces(0, 16, 4, 12, 4):
        rows = None if f in ("top", "bottom") else (0.0, 0.75)
        put(rgb2, a2, (x, y, w, h, f), "scales", legs, rows=rows)
    return to_rgba(rgb1, a1), to_rgba(rgb2, a2)


ICON_NAMES = [
    "whalebone_greatsword", "resonance_charm", "cloud_jerky",
    "stone_whale_helmet", "stone_whale_chestplate", "stone_whale_leggings", "stone_whale_boots",
    "captain_pike", "waterway_fang", "moss_talisman", "bone_spike_arrow",
    "stone_whale_core", "stone_whale_shard", "resonance_crystal", "resonance_shard", "stone_whale_plating",
]


def write_preview(path):
    cell = 136
    out = Image.new("RGBA", (8 * cell, 2 * cell + 140), (90, 120, 150, 255))
    for i, n in enumerate(ICON_NAMES):
        im = Image.open(ROOT / f"item/{n}.png").resize((128, 128), Image.NEAREST)
        out.paste(im, ((i % 8) * cell, (i // 8) * cell), im)
    for i, n in enumerate(["stone_whale_layer_1", "stone_whale_layer_2"]):
        im = Image.open(ROOT / f"models/armor/{n}.png").resize((256, 128), Image.NEAREST)
        out.paste(im, (i * (256 + 8), 2 * cell + 4), im)
    out.save(path)


def main():
    icons = {
        "whalebone_greatsword": greatsword, "resonance_charm": charm, "cloud_jerky": jerky,
        "stone_whale_helmet": lambda: armor_icon("helmet"),
        "stone_whale_chestplate": lambda: armor_icon("chestplate"),
        "stone_whale_leggings": lambda: armor_icon("leggings"),
        "stone_whale_boots": lambda: armor_icon("boots"),
        "captain_pike": captain_pike, "waterway_fang": waterway_fang, "moss_talisman": moss_talisman,
        "bone_spike_arrow": bone_spike_arrow, "stone_whale_core": stone_whale_core,
        "stone_whale_shard": stone_whale_shard, "resonance_crystal": resonance_crystal,
        "resonance_shard": resonance_shard, "stone_whale_plating": stone_whale_plating,
    }
    for name, make in icons.items():
        save(make(), ROOT / f"item/{name}.png")
    l1, l2 = armor_layers()
    save(l1, ROOT / "models/armor/stone_whale_layer_1.png")
    save(l2, ROOT / "models/armor/stone_whale_layer_2.png")
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        write_preview(Path(sys.argv[2]))
    print("ok")


if __name__ == "__main__":
    main()
