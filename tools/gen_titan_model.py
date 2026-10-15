"""第一の巨神「岩鯨」のモデル・テクスチャ・アニメーションを作る。

使い方: python tools/gen_titan_model.py [--preview 出力先.png]
出力先: src/main/resources/assets/skyabyss/
  geo/entity/stone_whale.geo.json            : GeckoLib (Bedrock形式) のモデル。Blockbenchで開ける
  textures/entity/stone_whale.png            : テクスチャ (宣言サイズの2倍の解像度で描く)
  textures/entity/stone_whale_glowmask.png   : 暗闇で光る部分 (目と背中の古代の紋様)
  animations/entity/stone_whale.animation.json

モデルの単位は px (16px = 1ブロック)。ゲーム内では8倍に拡大して描くので、全長はおよそ140ブロックになる。
頭は -Z 向き。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
TEX_W, TEX_H = 1024, 1024   # geo.json に書く宣言サイズ
RES = 2                     # 実際の画像はこの倍率で描き、密度を上げる
rng = np.random.default_rng(7)

# ---------------------------------------------------------------- 形
# 体は Z方向の薄い輪切りを並べて作る。各輪切りは「幅いっぱいで低い箱」と「高さいっぱいで細い箱」の2つで、
# 断面が角の落ちた八角形になる。これで箱っぽさを消し、なめらかな鯨の輪郭にする。
# 材質: hide=岩の皮膚, belly=腹, moss=背中の苔と土, ruin=古代の石組み, bone=牙や角, eye=目, fin=ひれ
SLICE = 8
#            z     幅   高さ  中心y
PROFILE = [(-112, 30, 18, 15), (-104, 44, 26, 16), (-92, 54, 32, 17), (-76, 60, 36, 18),
           (-60, 62, 38, 19), (-40, 66, 42, 20), (-16, 68, 44, 21), (12, 66, 43, 21),
           (36, 60, 40, 21), (60, 50, 34, 21), (84, 38, 26, 21), (108, 28, 19, 21),
           (132, 18, 13, 21), (150, 12, 9, 21)]


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
        cubes.append(((-(w - 2 * inset) / 2, bottom, z), (w - 2 * inset, h, SLICE), "belly" if False else "hide"))
        # 背中の苔の層 (胴の上だけ)
        if -70 <= z < 120:
            mw = w - 2 * inset - 4
            cubes.append(((-mw / 2, bottom + h, z), (mw, 2, SLICE), "moss"))
        # 腹の明るい帯
        if not is_head:
            bw = w - 2 * inset - 6
            cubes.append(((-bw / 2, bottom - 1, z), (bw, 1, SLICE), "belly"))
    return parts


def top_y(z):
    w, h, yc = profile(z)
    return yc + h / 2 + 2


PARTS = slices()
EYE_Z = -86
EYE_Y = profile(EYE_Z)[2] + 3
EYE_X = profile(EYE_Z)[0] / 2

BONES = [
    ("body", None, (0, 21, 0), PARTS["body"] + [
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
        ((-14, top_y(-36) + 18, -36), (28, 4, 4), "ruin"),
        # 中ほどの祠と、尾の側の降り口の台
        ((-8, top_y(26), 22), (16, 10, 14), "ruin"),
        ((-10, top_y(26) + 10, 20), (20, 3, 18), "ruin"),
        ((-7, top_y(60) - 1, 54), (14, 2, 12), "ruin"),
    ]),
    ("fin_left", "body", (30, 14, -26), [
        ((30, 11, -40), (24, 4, 28), "fin"),
        ((54, 11.5, -32), (18, 3, 22), "fin"),
        ((72, 12, -22), (14, 2, 14), "fin"),
        ((86, 12.5, -14), (8, 1, 8), "fin"),
    ]),
    ("fin_right", "body", (-30, 14, -26), [
        ((-54, 11, -40), (24, 4, 28), "fin"),
        ((-72, 11.5, -32), (18, 3, 22), "fin"),
        ((-86, 12, -22), (14, 2, 14), "fin"),
        ((-94, 12.5, -14), (8, 1, 8), "fin"),
    ]),
    ("head", "body", (0, 20, -62), PARTS["head"] + [
        ((-EYE_X - 1, EYE_Y, EYE_Z), (2, 5, 7), "eye"),
        ((EYE_X - 1, EYE_Y, EYE_Z), (2, 5, 7), "eye"),
        # 額の角のような岩
        ((-16, top_y(-84) - 2, -88), (6, 8, 6), "bone"),
        ((10, top_y(-84) - 2, -88), (6, 8, 6), "bone"),
    ]),
    ("jaw", "head", (0, 10, -64), PARTS["jaw"] + [
        ((-14, profile(-104)[2] - 4, -106), (3, 4, 3), "bone"),
        ((11, profile(-104)[2] - 4, -106), (3, 4, 3), "bone"),
    ]),
    ("tail1", "body", (0, 21, 64), PARTS["tail1"]),
    ("tail2", "tail1", (0, 21, 96), PARTS["tail2"]),
    ("tail3", "tail2", (0, 21, 124), PARTS["tail3"]),
    ("fluke", "tail3", (0, 21, 150), [
        ((-40, 18, 146), (34, 3, 16), "fin"),
        ((6, 18, 146), (34, 3, 16), "fin"),
        ((-52, 18.5, 156), (14, 2, 12), "fin"),
        ((38, 18.5, 156), (14, 2, 12), "fin"),
        ((-60, 19, 166), (8, 1, 8), "fin"),
        ((52, 19, 166), (8, 1, 8), "fin"),
        ((-6, 16, 144), (12, 7, 12), "hide"),
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
    "belly": ((122, 118, 108), (92, 88, 82)),
    "moss": ((78, 104, 52), (56, 78, 40)),
    "ruin": ((70, 70, 78), (50, 50, 58)),
    "bone": ((196, 188, 166), (150, 142, 122)),
    "eye": ((26, 22, 18), (20, 18, 16)),
    "fin": ((76, 82, 92), (54, 58, 68)),
}


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
    n = value_noise(H, W, 6, seed) * 0.55 + value_noise(H, W, 2, seed + 1) * 0.3 + rng.random((H, W)) * 0.15
    col = dark + (light - dark) * n[..., None]

    if material in ("hide", "fin", "belly"):
        # ひび割れ: ノイズの等高線を細い暗線にする
        crack = np.abs(value_noise(H, W, 10, seed + 2) - 0.5) < 0.018
        col[crack] *= 0.6
        if face == "side":
            # 上から苔が垂れ、下ほど暗い
            t = np.linspace(0, 1, H)[:, None]
            moss_amt = np.clip((0.22 - t) / 0.22 + (value_noise(H, W, 4, seed + 3) - 0.5) * 0.6, 0, 1)
            col = col * (1 - moss_amt[..., None]) + np.array(PALETTE["moss"][0], float) * moss_amt[..., None]
            col *= (1.05 - 0.35 * t)[..., None]
        if face == "bottom":
            col *= 0.72
            # 腹のフジツボ
            spots = value_noise(H, W, 3, seed + 4) > 0.78
            col[spots] = col[spots] * 0.6 + np.array([170, 160, 140]) * 0.4
        if face == "top" and material != "belly":
            moss_amt = np.clip((value_noise(H, W, 5, seed + 5) - 0.35) * 2.2, 0, 1)
            col = col * (1 - moss_amt[..., None]) + np.array(PALETTE["moss"][0], float) * moss_amt[..., None]
    if material == "moss":
        # 草地の明暗と、ところどころ露出した土
        dirt = value_noise(H, W, 7, seed + 6) > 0.72
        col[dirt] = col[dirt] * 0.4 + np.array([96, 74, 52]) * 0.6
        if face == "side":
            t = np.linspace(0, 1, H)[:, None]
            col = col * (t < 0.35)[..., None] + (np.array([92, 72, 52]) * (0.8 + 0.2 * n[..., None])) * (t >= 0.35)[..., None]
    if material == "ruin":
        # 石組みの目地
        yy, xx = np.mgrid[0:H, 0:W]
        joint = ((yy % (4 * RES)) == 0) | (((xx + (yy // (4 * RES)) * 3 * RES) % (6 * RES)) == 0)
        col[joint] *= 0.65
        # 古代の紋様 (上面以外の面にうっすら光る線)
        if face == "side" and W > 6 and H > 6:
            rune = (np.abs(np.sin(xx / (W / 3.0) * math.pi) * 0.4 + 0.5 - yy / H) < 0.035)
            col[rune] = col[rune] * 0.5 + np.array([120, 200, 230]) * 0.5
            glow[Y:Y + H, X:X + W][rune] = (150, 220, 255, 255)
    if material == "eye":
        yy, xx = np.mgrid[0:H, 0:W]
        cy, cx = H / 2, W / 2
        iris = ((yy - cy) / (H / 2)) ** 2 + ((xx - cx) / (W / 2)) ** 2 < 0.55
        col[iris] = (255, 196, 92)
        glow[Y:Y + H, X:X + W][iris] = (255, 200, 110, 255)

    img[Y:Y + H, X:X + W, :3] = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, 3] = 255


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
            paint_cube(img, glow, uv, size, material, 100 + index * 7)
            geo_cubes.append({"origin": list(origin), "size": list(size), "uv": list(uv)})
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

    # 8秒で一周する、ゆったりした泳ぎ。角度は度、時間は秒
    s = lambda speed, phase, amp: f"math.sin(query.anim_time * {speed} - {phase}) * {amp}"
    anim = {
        "format_version": "1.8.0",
        "animations": {
            "animation.stone_whale.swim": {
                "loop": True,
                "animation_length": 8.0,
                "bones": {
                    "body": {"rotation": [s(45, 0, 1.2), "0", s(22.5, 0, 1.0)], "position": ["0", s(45, 30, 2.5), "0"]},
                    "head": {"rotation": [s(45, 40, 2.0), s(22.5, 20, 3.0), "0"]},
                    "jaw": {"rotation": [f"-math.max(0, {s(30, 0, 1)}) * 7", "0", "0"]},
                    "tail1": {"rotation": [s(45, 60, 4.0), s(22.5, 60, 2.0), "0"]},
                    "tail2": {"rotation": [s(45, 110, 7.0), s(22.5, 110, 3.0), "0"]},
                    "tail3": {"rotation": [s(45, 160, 10.0), s(22.5, 160, 4.0), "0"]},
                    "fluke": {"rotation": [s(45, 210, 14.0), "0", "0"]},
                    "fin_left": {"rotation": [s(45, 20, 3.0), "0", s(45, 0, 14.0)]},
                    "fin_right": {"rotation": [s(45, 20, 3.0), "0", f"-{s(45, 0, 14.0)}"]},
                },
            }
        },
    }
    (ROOT / "animations/entity/stone_whale.animation.json").write_text(json.dumps(anim, indent=1), encoding="utf-8")
    print(f"bones={len(BONES)} cubes={index}")
    return geo


def preview(geo, path):
    """横と上から見た簡易プレビュー (アニメーションなし、形の確認用)"""
    cubes = [(c["origin"], c["size"]) for b in geo["minecraft:geometry"][0]["bones"] for c in b["cubes"]]
    scale = 2
    side = Image.new("RGB", (300 * scale, 140 * scale), (200, 215, 235))
    top = Image.new("RGB", (300 * scale, 200 * scale), (200, 215, 235))
    from PIL import ImageDraw
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


if __name__ == "__main__":
    geo = build()
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(geo, sys.argv[2])
