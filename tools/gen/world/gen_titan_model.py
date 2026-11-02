"""第一の巨神「岩鯨」のモデル・テクスチャ・アニメーションを作る。

使い方: python tools/gen/world/gen_titan_model.py [--preview 出力先.png]
出力先: src/main/resources/assets/skyabyss/
  geo/entity/stone_whale.geo.json            : GeckoLib (Bedrock形式) のモデル。Blockbenchで開ける
  textures/entity/stone_whale.png            : テクスチャ (宣言サイズの2倍の解像度で描く)
  textures/entity/stone_whale_glowmask.png   : 暗闇で光る部分 (目、背中の古代の紋様、青い共鳴の文字)
  animations/entity/stone_whale.animation.json

モデルの単位は px (16px = 1ブロック)。ゲーム内では8倍に拡大して描くので、1px = 0.5ブロック、全長はおよそ130ブロック。
頭は -Z 向き。胴の寸法 (PROFILE) は鎖の付け根に合わせてあるので変えないこと。
鎖の付け根 (ゲーム内の 後ろ30・横21・上20 ブロック) は、モデル座標では z=+60, x=-42 (付け根は x=-20, y=54), y=40 px。
"""

# ---------------------------------------------------------------------------
# 上書き防止: 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: 岩鯨の geo (近景) / stone_whale.png / _glowmask.png / animation
#   採用版の生成元: tools/install/install_stone_whale.py
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/world/gen_titan_model.py は停止中です: 採用済みの素材 (岩鯨の geo (近景) / stone_whale.png / _glowmask.png / animation) を上書きするため。"
              "採用版の生成元は tools/install/install_stone_whale.py です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss"
TEX_W, TEX_H = 1024, 1024   # geo.json に書く宣言サイズ
RES = 2                     # 実際の画像はこの倍率で描き、密度を上げる
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- 形
# 体は Z方向の薄い輪切りを並べて作る。各輪切りは「幅いっぱいで低い箱」と「高さいっぱいで細い箱」の2つで、
# 断面が角の落ちた八角形になる。輪切りは細かくして (SLICE)、段々の少ない滑らかな輪郭にする。
# 材質: hide=岩の皮膚, belly=腹, moss=背中の苔と土, ruin=古代の石組み, bone=牙や角やあばら,
#       eye=目, fin=ひれ, groove=溝 (暗い), crust=フジツボと岩のかさぶた, glyph=青く光る文字
SLICE = 6
#            z     幅   高さ  中心y   (鎖の付け根の位置に合わせて変更しないこと)
PROFILE = [(-112, 30, 18, 15), (-104, 44, 26, 16), (-92, 54, 32, 17), (-76, 60, 36, 18),
           (-60, 62, 38, 19), (-40, 66, 42, 20), (-16, 68, 44, 21), (12, 66, 43, 21),
           (36, 60, 40, 21), (60, 50, 34, 21), (84, 38, 26, 21), (108, 28, 19, 21),
           (132, 18, 13, 21), (150, 12, 9, 21)]
RIB_Z = (-34, -16, 2, 20, 38)   # あばらの位置 (輪切りの始まりのz)
CHAIN_Z = 60                     # 鎖の付け根のある所。ここの脇腹には突起を置かない


def profile(z):
    for (z0, *a), (z1, *b) in zip(PROFILE, PROFILE[1:]):
        if z0 <= z <= z1:
            t = (z - z0) / (z1 - z0)
            t = t * t * (3 - 2 * t)
            return [x + (y - x) * t for x, y in zip(a, b)]
    return PROFILE[-1][1:]


def bone_for(z):
    if z < -62:
        return "head"
    if z < 64:
        return "body"
    if z < 96:
        return "tail1"
    if z < 124:
        return "tail2"
    return "tail3"


def slices():
    parts = {}
    for z in range(-112, 152, SLICE):
        w, h, yc = profile(z + SLICE / 2)
        w, h = round(w / 2) * 2, round(h / 2) * 2
        bottom = yc - h / 2
        name = bone_for(z)
        cubes = parts.setdefault(name, [])
        inset = max(2, round(w * 0.14))
        drop = max(2, round(h * 0.18))
        is_head = name == "head"
        # 頭は下あごを別の骨にするので、下の方は jaw に回す
        if is_head:
            jaw_h = max(4, round(h * 0.3))
            parts.setdefault("jaw", []).append(((-(w - 2 * inset) / 2, bottom, z), (w - 2 * inset, jaw_h, SLICE), "belly"))
            bottom += jaw_h
            h -= jaw_h
        cubes.append(((-w / 2, bottom + drop, z), (w, h - 2 * drop, SLICE), "hide"))
        cubes.append(((-(w - 2 * inset) / 2, bottom, z), (w - 2 * inset, h, SLICE), "hide"))
        # 背中の苔の層 (胴の上だけ)
        if -70 <= z < 120:
            mw = w - 2 * inset - 4
            cubes.append(((-mw / 2, bottom + h, z), (mw, 2, SLICE), "moss"))
        if not is_head:
            bw = w - 2 * inset - 6
            a, b = -bw / 2, bw / 2
            if name == "body" and -40 <= z < 60:
                # 腹の溝: 腹の帯を2本の暗い溝で3つに分ける
                g1, g2 = -w * 0.2, w * 0.2
                cubes.append(((a, bottom - 1, z), (g1 - 1 - a, 1, SLICE), "belly"))
                cubes.append(((g1 - 1, bottom - 1, z), (2, 1, SLICE), "groove"))
                cubes.append(((g1 + 1, bottom - 1, z), (g2 - 1 - (g1 + 1), 1, SLICE), "belly"))
                cubes.append(((g2 - 1, bottom - 1, z), (2, 1, SLICE), "groove"))
                cubes.append(((g2 + 1, bottom - 1, z), (b - (g2 + 1), 1, SLICE), "belly"))
            else:
                cubes.append(((a, bottom - 1, z), (bw, 1, SLICE), "belly"))
        # あばら骨の突起 (脇腹から少しはみ出す)
        if name == "body" and any(abs(z - r) < 1 for r in RIB_Z):
            cubes.append(((w / 2 - 1, bottom + 2, z + 1), (3, 5, 4), "bone"))
            cubes.append(((-w / 2 - 2, bottom + 2, z + 1), (3, 5, 4), "bone"))
    return parts


def top_y(z):
    w, h, yc = profile(z)
    return yc + h / 2 + 2


def crusts():
    """胴の上と脇腹にフジツボと岩のかさぶたを散らす。鎖の付け根の側は避ける"""
    r = np.random.default_rng(11)
    out = []
    for _ in range(34):
        z = int(r.uniform(-58, 112))
        w, h, yc = profile(z)
        s = int(r.integers(2, 5))
        if r.random() < 0.6:
            x = int(r.uniform(-w / 2 + 4, w / 2 - 4 - s))
            if x < 0 and abs(z - CHAIN_Z) < 16:
                continue
            out.append(((x, yc + h / 2 - 1, z), (s, 2 + int(r.integers(0, 2)), s), "crust"))
        else:
            side = 1 if r.random() < 0.5 else -1
            if side < 0 and abs(z - CHAIN_Z) < 20:
                continue
            y = int(yc + r.uniform(-h / 4, h / 4))
            x = w / 2 - 1 if side > 0 else -w / 2 - s + 1
            out.append(((x, y, z), (2, s, s), "crust"))
    return out


PARTS = slices()
EYE_Z = -86
EYE_Y = profile(EYE_Z)[2] + 3
EYE_X = profile(EYE_Z)[0] / 2
MOUTH_W, MOUTH_H, MOUTH_YC = profile(-110)
MOUTH_Y = MOUTH_YC - MOUTH_H / 2 + max(4, round(MOUTH_H * 0.3))

BONES = [
    ("body", None, (0, 21, 0), PARTS["body"] + crusts() + [
        # 側面の岩の隆起 (フジツボのような塊)
        ((-35, 12, -30), (3, 12, 18), "hide"),
        ((32, 16, 6), (3, 10, 22), "hide"),
        ((-34, 20, 24), (3, 8, 12), "hide"),
    ]),
    ("ruins", "body", (0, 60, 0), [
        # 背骨に沿った古代の柱列。いくつかは折れている
        ((-14, top_y(-36), -36), (4, 18, 4), "ruin"),
        ((10, top_y(-36), -36), (4, 18, 4), "ruin"),
        ((-14, top_y(-14), -14), (4, 11, 4), "ruin"),
        ((10, top_y(-14), -14), (4, 20, 4), "ruin"),
        ((-14, top_y(8), 8), (4, 19, 4), "ruin"),
        ((10, top_y(8), 8), (4, 7, 4), "ruin"),
        ((-6, top_y(-60), -60), (3, 10, 3), "ruin"),
        ((-14, top_y(-36) + 18, -36), (28, 4, 4), "ruin"),
        # 中ほどの祠と、尾の側の降り口の台
        ((-8, top_y(26), 22), (16, 10, 14), "ruin"),
        ((-10, top_y(26) + 10, 20), (20, 3, 18), "ruin"),
        ((-7, top_y(60) - 1, 54), (14, 2, 12), "ruin"),
    ] + [
        # 祠の台の上に並ぶ、青く光る共鳴の文字の線
        ((-8, top_y(26) + 13, z), (16, 1, 1), "glyph") for z in (23, 26, 29, 32, 35)
    ]),
    ("fin_left", "body", (30, 14, -26), [
        ((30, 11, -40), (24, 4, 28), "fin"),
        ((54, 11.5, -32), (18, 3, 22), "fin"),
        ((72, 12, -22), (14, 2, 14), "fin"),
        ((86, 12.5, -14), (8, 1, 8), "fin"),
        ((40, 10, -54), (16, 3, 12), "fin"),
    ]),
    ("fin_right", "body", (-30, 14, -26), [
        ((-54, 11, -40), (24, 4, 28), "fin"),
        ((-72, 11.5, -32), (18, 3, 22), "fin"),
        ((-86, 12, -22), (14, 2, 14), "fin"),
        ((-94, 12.5, -14), (8, 1, 8), "fin"),
        ((-56, 10, -54), (16, 3, 12), "fin"),
    ]),
    ("head", "body", (0, 20, -62), PARTS["head"] + [
        ((-EYE_X - 1, EYE_Y, EYE_Z), (2, 5, 7), "eye"),
        ((EYE_X - 1, EYE_Y, EYE_Z), (2, 5, 7), "eye"),
        # 額の角のような岩
        ((-16, top_y(-84) - 2, -88), (6, 8, 6), "bone"),
        ((10, top_y(-84) - 2, -88), (6, 8, 6), "bone"),
        # 口の切れ目 (前から見て)
        ((-MOUTH_W * 0.42, MOUTH_Y - 0.5, -113), (MOUTH_W * 0.84, 1, 1), "groove"),
    ]),
    ("jaw", "head", (0, 10, -64), PARTS["jaw"] + [
        ((-14, profile(-104)[2] - 4, -106), (3, 4, 3), "bone"),
        ((11, profile(-104)[2] - 4, -106), (3, 4, 3), "bone"),
    ]),
    ("tail1", "body", (0, 21, 64), PARTS["tail1"]),
    ("tail2", "tail1", (0, 21, 96), PARTS["tail2"]),
    ("tail3", "tail2", (0, 21, 124), PARTS["tail3"]),
    ("fluke", "tail3", (0, 21, 150), [
        # 尾びれ: 左右の葉と、その先の段々
        ((-40, 18, 146), (34, 3, 16), "fin"),
        ((6, 18, 146), (34, 3, 16), "fin"),
        ((-52, 18.5, 156), (14, 2, 12), "fin"),
        ((38, 18.5, 156), (14, 2, 12), "fin"),
        ((-60, 19, 166), (8, 1, 8), "fin"),
        ((52, 19, 166), (8, 1, 8), "fin"),
        ((-6, 16, 144), (12, 7, 12), "hide"),
        # 尾びれの付け根の溝
        ((-3, 18.5, 146), (6, 1, 2), "groove"),
    ]),
]


# ---------------------------------------------------------------- UVの割り当て
def box_uv_size(size):
    w, h, d = size
    return 2 * (d + w), d + h


def pack(cubes):
    """ボックスUVの領域を棚詰めで並べる"""
    order = sorted(range(len(cubes)), key=lambda i: -box_uv_size(cubes[i][1])[1])
    x = y = shelf = 0
    out = {}
    for i in order:
        cw, ch = (math.ceil(v) for v in box_uv_size(cubes[i][1]))
        if x + cw > TEX_W:
            x, y, shelf = 0, y + shelf + 2, 0
        out[i] = (x, y)
        x += cw + 2
        shelf = max(shelf, ch)
    if y + shelf > TEX_H:
        raise SystemExit(f"texture overflow: {y + shelf}")
    return out


# ---------------------------------------------------------------- 色塗り
PALETTE = {
    "hide": ((84, 88, 96), (58, 62, 72)),
    "belly": ((104, 100, 92), (72, 68, 64)),   # 腹は背中より暗い
    "moss": ((78, 104, 52), (50, 72, 36)),
    "ruin": ((70, 70, 78), (50, 50, 58)),
    "bone": ((196, 188, 166), (150, 142, 122)),
    "eye": ((26, 22, 18), (20, 18, 16)),
    "fin": ((76, 82, 92), (54, 58, 68)),
    "groove": ((40, 40, 46), (28, 28, 34)),
    "crust": ((150, 140, 118), (96, 90, 76)),
    "glyph": ((52, 58, 66), (36, 40, 48)),
}
CYAN = np.array([96, 232, 255], float)
MOSS = np.array(PALETTE["moss"][0], float)


def value_noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    gh, gw = h // scale + 2, w // scale + 2
    g = r.random((gh, gw))
    ys = np.linspace(0, gh - 1.001, h)
    xs = np.linspace(0, gw - 1.001, w)
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]
    b = g[y0][:, x0 + 1]
    c = g[y0 + 1][:, x0]
    d = g[y0 + 1][:, x0 + 1]
    top = a + (b - a) * fx
    bot = c + (d - c) * fx
    return top + (bot - top) * fy[:, None]


def paint_face(img, glow, x, y, w, h, material, face, seed):
    """1つの面を描く。face: top / bottom / side"""
    if w <= 0 or h <= 0:
        return
    W, H = int(w * RES), int(h * RES)
    X, Y = int(x * RES), int(y * RES)
    light, dark = (np.array(c, float) for c in PALETTE[material])
    yy, xx = np.mgrid[0:H, 0:W]
    n = value_noise(H, W, 6, seed) * 0.55 + value_noise(H, W, 2, seed + 1) * 0.3 + rng.random((H, W)) * 0.15
    col = dark + (light - dark) * n[..., None]
    gmask = np.zeros((H, W), bool)
    gcol = np.array([150, 230, 255], float)

    if material in ("hide", "fin", "belly", "crust"):
        # 石の板: 6px (ブロック換算 0.75) ごとに、段ごとに半分ずらした板。左上が明るく、右下が暗い
        P = 6 * RES
        lx = (xx + ((yy // P) % 2) * (P // 2)) % P
        ly = yy % P
        col[(ly < RES) | (lx < RES)] *= 1.14
        col[(ly >= P - RES) | (lx >= P - RES)] *= 0.8
        col[(lx == 0) | (ly == 0)] *= 0.55
        # ひび割れ: ノイズの等高線を細い暗線にする
        crack = np.abs(value_noise(H, W, 10, seed + 2) - 0.5) < 0.018
        col[crack] *= 0.6
        if face == "side":
            # 上から苔が垂れ、下ほど暗い
            t = np.linspace(0, 1, H)[:, None]
            moss_amt = np.clip((0.22 - t) / 0.22 + (value_noise(H, W, 4, seed + 3) - 0.5) * 0.6, 0, 1)
            col = col * (1 - moss_amt[..., None]) + MOSS * moss_amt[..., None]
            col *= (1.05 - 0.35 * t)[..., None]
        if face == "bottom":
            col *= 0.6
            # 腹のフジツボ
            spots = value_noise(H, W, 3, seed + 4) > 0.8
            col[spots] = col[spots] * 0.5 + np.array([170, 160, 140]) * 0.5
        if face == "top" and material != "belly":
            # 苔は地面の起伏に沿って、ところどころ濃く残る
            moss_amt = np.clip((value_noise(H, W, 5, seed + 5) - 0.35) * 2.2, 0, 1)
            col = col * (1 - moss_amt[..., None]) + MOSS * moss_amt[..., None]
    if material == "crust":
        # 岩のかさぶた: 明るい粒と、縁の欠け
        col = col * 0.85 + (rng.random((H, W)) > 0.8)[..., None] * 60
    if material == "moss":
        # 草地の明暗と、ところどころ露出した土
        dirt = value_noise(H, W, 7, seed + 6) > 0.72
        col[dirt] = col[dirt] * 0.4 + np.array([96, 74, 52]) * 0.6
        if face == "side":
            t = np.linspace(0, 1, H)[:, None]
            col = col * (t < 0.35)[..., None] + (np.array([92, 72, 52]) * (0.8 + 0.2 * n[..., None])) * (t >= 0.35)[..., None]
    if material == "ruin":
        # 石組みの目地と、少し欠けた角
        joint = ((yy % (4 * RES)) == 0) | (((xx + (yy // (4 * RES)) * 3 * RES) % (6 * RES)) == 0)
        col[joint] *= 0.65
        if face == "side" and W > 6 and H > 6:
            # 古代の紋様 (横にうねる線)
            rune = (np.abs(np.sin(xx / (W / 3.0) * math.pi) * 0.4 + 0.5 - yy / H) < 0.035)
            col[rune] = col[rune] * 0.4 + CYAN * 0.6
            gmask |= rune
        if face == "top" and W > 6 and H > 6:
            # 上面の紋様: 横線と、十字の印
            rune = (np.abs(yy - H * 0.5) < 0.6 * RES) & (xx > W * 0.12) & (xx < W * 0.88)
            tick = (np.abs(xx - W * 0.5) < 0.6 * RES) & (np.abs(yy - H * 0.5) < H * 0.3)
            m = rune | tick
            col[m] = col[m] * 0.4 + CYAN * 0.6
            gmask |= m
    if material == "glyph":
        # 青く光る文字の線: 面全体が光る
        col = CYAN * (0.85 + 0.15 * n[..., None])
        gmask[:] = True
        gcol = CYAN
    if material == "groove":
        col *= 0.8
    if material == "eye":
        cy, cx = H / 2, W / 2
        iris = ((yy - cy) / (H / 2)) ** 2 + ((xx - cx) / (W / 2)) ** 2 < 0.55
        col[iris] = (255, 196, 92)
        gmask |= iris
        gcol = np.array([255, 200, 110], float)

    # 角の暗がり (AO): 面の縁を少し暗くして、箱の角を締める
    if material not in ("glyph", "eye"):
        edge = np.minimum.reduce([xx, W - 1 - xx, yy, H - 1 - yy]) < 2 * RES
        col[edge] *= 0.85

    img[Y:Y + H, X:X + W, :3] = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, 3] = 255
    if gmask.any():
        gview = glow[Y:Y + H, X:X + W]
        gview[gmask, :3] = gcol.astype(np.uint8)
        gview[gmask, 3] = 255


def paint_cube(img, glow, uv, size, material, seed):
    w, h, d = size
    u, v = uv
    paint_face(img, glow, u + d, v, w, d, material, "top", seed)
    paint_face(img, glow, u + d + w, v, w, d, material, "bottom", seed + 10)
    paint_face(img, glow, u, v + d, d, h, material, "side", seed + 20)
    paint_face(img, glow, u + d, v + d, w, h, material, "side", seed + 30)
    paint_face(img, glow, u + d + w, v + d, d, h, material, "side", seed + 40)
    paint_face(img, glow, u + 2 * d + w, v + d, w, h, material, "side", seed + 50)


# ---------------------------------------------------------------- 書き出し
def check_uv(uv, size):
    w, h, d = size
    if uv[0] < 0 or uv[1] < 0 or uv[0] + 2 * (d + w) > TEX_W or uv[1] + d + h > TEX_H:
        raise SystemExit(f"uv out of texture: uv={uv} size={size}")


def build():
    flat = [(b, c) for b in BONES for c in b[3]]
    uvs = pack([c for _, c in flat])
    img = np.zeros((TEX_H * RES, TEX_W * RES, 4), np.uint8)
    glow = np.zeros_like(img)

    geo_bones = []
    index = 0
    for name, parent, pivot, cubes in BONES:
        geo_cubes = []
        for origin, size, material in cubes:
            uv = uvs[index]
            check_uv(uv, size)
            paint_cube(img, glow, uv, size, material, 100 + index * 7)
            geo_cubes.append({"origin": [round(v, 3) for v in origin], "size": list(size), "uv": list(uv)})
            index += 1
        bone = {"name": name, "pivot": list(pivot), "cubes": geo_cubes}
        if parent:
            bone["parent"] = parent
        geo_bones.append(bone)

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.stone_whale",
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 40,
                "visible_bounds_height": 20,
                "visible_bounds_offset": [0, 4, 0],
            },
            "bones": geo_bones,
        }],
    }
    (ROOT / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "animations/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "geo/entity/stone_whale.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ROOT / "textures/entity/stone_whale.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(ROOT / "textures/entity/stone_whale_glowmask.png", optimize=True)

    # 8秒で一周する泳ぎ。角度は度、時間は秒。周期は4秒 (90度/秒) なので8秒の中に2回入り、ループの継ぎ目がずれない。
    # GeckoLib の math.sin は引数を度で受け取る。周波数と位相は弧度で書き、式に出すときに度へ直す。
    # 体の横振りは頭から尾へ位相を遅らせて波を送る。尾ほど振れ幅が大きい。尾びれは上下に打つ。
    W1 = math.pi / 2
    def wave(phase, amp, omega=W1, bias=0.0):
        return f"math.sin(query.anim_time * {math.degrees(omega):.4f} - {math.degrees(phase):.4f}) * {amp}"
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.stone_whale.swim": {
                "loop": True,
                "animation_length": 8.0,
                "bones": {
                    # ゆっくりした上下の揺れ (8秒で1往復)
                    # 胴 (背中の島と鎖の留め具を含む) は動かさない。胴が揺れると、エンティティの位置から計算する鎖の付け根とずれる
                    "head": {"rotation": [wave(0.3, 1.5), wave(0.0, 3.0), "0"]},
                    "jaw": {"rotation": ["-(0.5 + 0.5 * math.sin(query.anim_time * 90 - 57.2958)) * 6", "0", "0"]},
                    "tail1": {"rotation": ["0", wave(1.0, 7.0), "0"]},
                    "tail2": {"rotation": ["0", wave(1.5, 10.0), "0"]},
                    "tail3": {"rotation": ["0", wave(2.0, 13.0), "0"]},
                    "fluke": {"rotation": [wave(2.5, 12.0), "0", "0"]},
                    # ひれは2秒で1往復し、左右は逆に打つ
                    "fin_left": {"rotation": ["0", "0", wave(0.0, 6.0, math.pi)]},
                    "fin_right": {"rotation": ["0", "0", "-" + wave(0.0, 6.0, math.pi)]},
                },
            }
        },
    }
    (ROOT / "animations/entity/stone_whale.animation.json").write_text(json.dumps(anim, indent=1), encoding="utf-8")
    print(f"bones={len(BONES)} cubes={index}")
    return geo, img, glow


def preview(geo, path, img=None, glow=None):
    """横と上から見た簡易プレビュー (アニメーションなし、形の確認用)"""
    from PIL import ImageDraw
    cubes = [(c["origin"], c["size"]) for b in geo["minecraft:geometry"][0]["bones"] for c in b["cubes"]]
    scale = 2
    side = Image.new("RGB", (300 * scale, 140 * scale), (200, 215, 235))
    top = Image.new("RGB", (300 * scale, 200 * scale), (200, 215, 235))
    ds, dt = ImageDraw.Draw(side), ImageDraw.Draw(top)
    for (x, y, z), (w, h, d) in sorted(cubes, key=lambda c: c[0][0]):
        ds.rectangle([(z + 120) * scale, (90 - y - h) * scale, (z + d + 120) * scale, (90 - y) * scale],
                     fill=(90, 95, 105), outline=(40, 40, 50))
    for (x, y, z), (w, h, d) in sorted(cubes, key=lambda c: c[0][1] + c[1][1]):
        g = int(80 + (y + h) * 1.5)
        dt.rectangle([(z + 120) * scale, (x + 100) * scale, (z + d + 120) * scale, (x + w + 100) * scale],
                     fill=(g, g + 10, g - 10), outline=(40, 40, 50))
    out = Image.new("RGB", (300 * scale, 340 * scale))
    out.paste(side, (0, 0))
    out.paste(top, (0, 140 * scale))
    out.save(path)
    if img is not None:
        tex = Image.fromarray(img, "RGBA").convert("RGB")
        g = Image.fromarray(glow, "RGBA").convert("RGB")
        both = Image.new("RGB", (tex.width * 2 + 8, tex.height), (0, 0, 0))
        both.paste(tex, (0, 0))
        both.paste(g, (tex.width + 8, 0))
        both.resize((both.width // 2, both.height // 2)).save(str(path).replace(".png", "_tex.png"))


if __name__ == "__main__":
    geo, img, glow = build()
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(geo, sys.argv[2], img, glow)
