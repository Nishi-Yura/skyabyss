"""岩鯨の甲殻の防具 (GeckoLib) の 3D モデルとテクスチャを作る。

使い方: python tools/gen_armor_model.py
出力先: src/main/resources/assets/skyabyss/
  geo/armor/stone_whale_armor.geo.json            : 8 本のアーマー骨 (armorHead, armorBody, armorRight/LeftArm, armorRight/LeftLeg, armorRight/LeftBoot)
  textures/armor/stone_whale_armor.png             : 本体のテクスチャ (面ごとに 1 単位あたり K=4 texel)
  textures/armor/stone_whale_armor_glowmask.png    : 青い発光部だけを残したマスク (AutoGlowingGeoLayer が読む)
  animations/armor/stone_whale_armor.animation.json: 空のアニメーション (アーマーは静止)

形の作り方:
- 座標はバニラの人型と同じ (1 単位 = 1/16 ブロック、y が上)。骨の pivot はバニラの各部位と揃える。
- 面 (面ごとの uv / uv_size) で密度を上げる。立方体の寸法と texel 数の比は K 倍。
- 板は重ねて段差を付け、骨の帯・びょう・青い核は小さな立方体として別に置く。
  そのため、テクスチャの色は「面の向き (光との内積)」「材質 (石・骨・青・闇)」「面の縁の 1px」の 3 つで決まる。
- 光は左上・手前寄り。アイコン (textures/item/stone_whale_*.png) と同じ石・骨・青の 4 段ランプ。
- 乱数は全て seed 付きの numpy 生成器。Python の hash() は使わない。
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
K = 4  # 1 モデル単位あたりの texel 数
NAME = "stone_whale_armor"

# 光源 (ワールド座標, y が上): 左・上・手前
LIGHT = np.array([-0.45, 0.72, -0.55])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# 明暗 4 段 (暗 -> 明)
OUTLINE = np.array([20, 18, 28], float)
STONE = np.array([[34, 38, 48], [58, 63, 76], [88, 95, 110], [128, 136, 152]], float)
BONE = np.array([[136, 122, 100], [180, 168, 144], [216, 206, 184], [244, 238, 222]], float)
CYAN = np.array([[18, 68, 98], [36, 124, 164], [104, 212, 246], [200, 250, 255]], float)
VOID = np.array([[8, 10, 14], [16, 20, 26], [26, 30, 40], [40, 46, 60]], float)
RAMPS = {"stone": STONE, "bone": BONE, "glow": CYAN, "void": VOID}

FACES = {
    "north": (0, 0, -1), "south": (0, 0, 1),
    "east": (1, 0, 0), "west": (-1, 0, 0),
    "up": (0, 1, 0), "down": (0, -1, 0),
}


# ---------------------------------------------------------------- 形の定義
def box(x0, y0, z0, x1, y1, z1, mat):
    return {"min": (x0, y0, z0), "max": (x1, y1, z1), "mat": mat}


def mirror(b):
    (x0, y0, z0), (x1, y1, z1) = b["min"], b["max"]
    return {"min": (-x1, y0, z0), "max": (-x0, y1, z1), "mat": b["mat"]}


def both(b):
    """左右対称に 2 つ置く"""
    return [b, mirror(b)]


def stud(x, y, z, axis, sign):
    """板の面 (axis 方向, sign 側) から突き出るびょう。半径 0.375、突き出し 0.4。"""
    c = [x, y, z]
    lo = [c[0] - 0.375, c[1] - 0.375, c[2] - 0.375]
    hi = [c[0] + 0.375, c[1] + 0.375, c[2] + 0.375]
    if sign < 0:
        lo[axis], hi[axis] = c[axis] - 0.4, c[axis] + 0.1
    else:
        lo[axis], hi[axis] = c[axis] - 0.1, c[axis] + 0.4
    return {"min": tuple(lo), "max": tuple(hi), "mat": "bone"}


def front_stud(x, y, zf):
    return stud(x, y, zf, 2, -1)


def side_stud(xf, y, z, sign):
    return stud(xf, y, z, 0, sign)


# 頭: 石の兜、骨の帯、暗い覗き穴 (青い横線)、背のひれ
HEAD = [
    box(-5, 24, -5, 5, 32.5, 5, "stone"),          # 兜の殻
    box(-4.6, 32.5, -4.6, 4.6, 33.2, 4.6, "stone"),  # 頭頂の板
    box(-5.25, 24, -5.25, 5.25, 25, 5.25, "bone"),   # 縁の骨
    box(-5.25, 27.6, -5.25, 5.25, 28.8, 5.25, "bone"),  # 額の骨の帯
    box(-3.2, 25.1, -5.7, 3.2, 27.2, -4.9, "void"),  # 覗き穴
    box(-2.6, 26.1, -5.95, 2.6, 26.45, -5.6, "glow"),  # 覗き穴の青い線
    box(-6.0, 25, -4.2, -4.9, 28.2, 4.2, "stone"),   # 頬当て (右)
    box(4.9, 25, -4.2, 6.0, 28.2, 4.2, "stone"),     # 頬当て (左)
    box(-0.9, 33.2, -4.4, 0.9, 34.6, 4.4, "stone"),  # 頭頂の稜
    box(-0.8, 34.6, -4.3, 0.8, 34.9, 4.3, "glow"),   # 稜の青い光
    box(-0.55, 33.1, 0.8, 0.55, 36.6, 4.6, "bone"),  # 背びれ (下)
    box(-0.45, 36.6, 2.2, 0.45, 38.4, 4.2, "bone"),  # 背びれ (先)
    front_stud(-4.1, 25.9, -5.0),
    front_stud(4.1, 25.9, -5.0),
    front_stud(-4.1, 28.2, -5.25),
    front_stud(4.1, 28.2, -5.25),
]

# 胴: 層になった胸当て、中央の青い核、肋骨、骨の襟、背の棘
BODY = [
    box(-4.5, 12.5, -2.9, 4.5, 23.6, 2.9, "stone"),  # 胴の殻
    box(-4.7, 14.0, -3.4, 4.7, 22.6, -2.8, "stone"),  # 胸当て
    box(-4.3, 13.2, -3.9, 4.3, 15.2, -3.1, "stone"),  # 腹当て (胸当ての前に重なる)
    box(-5.05, 12.0, -3.9, 5.05, 13.1, 3.9, "bone"),  # 裾の骨
    box(-1.0, 14.8, -4.0, 1.0, 21.6, -3.5, "glow"),   # 中央の青い核
    box(-4.3, 13.0, 2.9, 4.3, 22.6, 3.7, "stone"),    # 背の板
    box(-0.55, 14.5, 3.6, 0.55, 17.0, 4.4, "bone"),   # 背骨 (上)
    box(-0.55, 18.2, 3.6, 0.55, 21.5, 4.4, "bone"),   # 背骨 (下)
    front_stud(3.9, 22.0, -3.4),
    front_stud(-3.9, 22.0, -3.4),
] + both(box(1.4, 19.7, -3.9, 4.7, 20.5, -3.3, "bone")) \
  + both(box(1.4, 16.6, -3.9, 4.7, 17.4, -3.3, "bone")) \
  + both(box(1.2, 23.0, -2.9, 2.5, 24.9, -1.9, "bone"))  # 襟の骨 (V 字)

# 腕 (右側の定義。左は x を反転して作る)。pivot は (-5, 22, 0)
RIGHT_ARM = [
    box(-8.9, 11.0, -2.8, -3.1, 20.8, 2.8, "stone"),  # 袖の板
    box(-8.5, 12.2, -3.5, -3.5, 16.2, -2.8, "stone"),  # 籠手の前板
    box(-9.05, 11.0, -3.05, -2.95, 11.9, 3.05, "bone"),  # 手首の骨
    box(-10.2, 21.4, -4.4, -2.8, 23.7, 4.4, "stone"),   # 肩当て (上)
    box(-10.4, 19.4, -4.7, -2.6, 21.6, 4.1, "stone"),   # 肩当て (下)
    box(-10.5, 19.0, -4.9, -2.5, 19.5, 4.3, "bone"),    # 肩当ての縁
    box(-7.4, 23.7, -0.6, -5.6, 25.3, 0.6, "bone"),     # 肩の棘
    front_stud(-8.4, 22.4, -4.4),
    front_stud(-4.6, 22.4, -4.4),
    front_stud(-6.0, 15.0, -3.5),
]

# 脚 (右側)。pivot は (-1.9, 12, 0)。x は -0.1 で止めて左脚と重ならないようにする
RIGHT_LEG = [
    box(-4.5, 0.6, -2.7, -0.1, 11.0, 2.7, "stone"),   # 脚の殻
    box(-4.4, 5.8, -3.3, -0.2, 10.6, -2.6, "stone"),  # 腿当て (上)
    box(-4.6, 0.9, -3.6, -0.1, 5.4, -2.8, "stone"),   # 腿当て (下)
    box(-5.0, 1.5, -2.2, -4.5, 10.2, 2.2, "stone"),   # 外側の板
    box(-4.7, 10.9, -2.9, -0.1, 12.2, 2.9, "bone"),   # 腰の帯
    box(-0.7, 11.0, -3.2, -0.1, 11.8, -2.9, "glow"),  # 腰の青い印 (両脚で中央に 1 つ)
    front_stud(-3.6, 8.6, -3.3),
    front_stud(-0.9, 8.6, -3.3),
    front_stud(-3.6, 3.2, -3.6),
]

# 靴 (右側)。pivot は (-1.9, 12, 0)
RIGHT_BOOT = [
    box(-4.7, 0.0, -2.8, -0.1, 7.0, 2.8, "stone"),    # 靴の筒
    box(-4.8, 0.0, -4.7, -0.1, 2.6, -2.6, "stone"),   # つま先の箱
    box(-4.9, 0.0, -4.9, -0.1, 2.0, -4.6, "bone"),    # つま先の骨の覆い
    box(-5.0, 6.6, -3.0, -0.1, 7.9, 3.0, "bone"),     # 上端の骨
    box(-5.0, 4.0, -2.4, -4.65, 6.2, 2.4, "stone"),   # 外側の板
    box(-5.0, 1.8, -1.0, -4.7, 3.6, 1.0, "glow"),     # 外くるぶしの青い線
    front_stud(-2.6, 1.0, -4.9),
    front_stud(-2.4, 5.0, -2.8),
]

BONES = [
    # (骨の名前, pivot, 立方体のリスト)
    ("armorHead", (0, 24, 0), HEAD),
    ("armorBody", (0, 24, 0), BODY),
    ("armorRightArm", (-5, 22, 0), RIGHT_ARM),
    ("armorLeftArm", (5, 22, 0), [mirror(b) for b in RIGHT_ARM]),
    ("armorRightLeg", (-1.9, 12, 0), RIGHT_LEG),
    ("armorLeftLeg", (1.9, 12, 0), [mirror(b) for b in RIGHT_LEG]),
    ("armorRightBoot", (-1.9, 12, 0), RIGHT_BOOT),
    ("armorLeftBoot", (1.9, 12, 0), [mirror(b) for b in RIGHT_BOOT]),
]


# ---------------------------------------------------------------- UV の割り当て
def texel_dims(b):
    (x0, y0, z0), (x1, y1, z1) = b["min"], b["max"]
    dx = max(1, round((x1 - x0) * K))
    dy = max(1, round((y1 - y0) * K))
    dz = max(1, round((z1 - z0) * K))
    return dx, dy, dz


def net_faces(U0, V0, dx, dy, dz):
    """箱の展開図 (標準の box UV) を面ごとの (u, v, w, h) に分ける"""
    return {
        "up": (U0 + dz, V0, dx, dz),
        "down": (U0 + dz + dx, V0, dx, dz),
        "west": (U0, V0 + dz, dz, dy),
        "north": (U0 + dz, V0 + dz, dx, dy),
        "east": (U0 + dz + dx, V0 + dz, dz, dy),
        "south": (U0 + 2 * dz + dx, V0 + dz, dx, dy),
    }


def pack(blocks, pad=1):
    """(w, h, key) の矩形を棚詰めで並べる。一番小さく収まる 2 の冪の正方形を返す。"""
    for size in (256, 512, 1024, 2048):
        x = y = shelf = 0
        pos = {}
        ok = True
        for w, h, key in sorted(blocks, key=lambda b: (-b[1], -b[0])):
            if w > size:
                ok = False
                break
            if x + w > size:
                x = 0
                y += shelf + pad
                shelf = 0
            if y + h > size:
                ok = False
                break
            pos[key] = (x, y)
            x += w + pad
            shelf = max(shelf, h)
        if ok:
            return size, pos
    raise SystemExit("テクスチャに収まらない")


# ---------------------------------------------------------------- テクスチャ
def paint_face(mat, normal, w, h, rng):
    """1 面を描く。光の強さ (4 段) + 縁の 1px + 石の粒。"""
    lam = float(np.dot(np.array(normal, float), LIGHT))
    lvl = int(np.digitize(lam, [0.12, 0.4, 0.62]))
    ramp = RAMPS[mat]
    level = np.full((h, w), lvl, int)
    if mat in ("stone", "bone", "void"):
        # 石の粒: 1 割ほどの texel を前後に 1 段ずらす
        grain = rng.random((h, w))
        step = np.where(rng.random((h, w)) < 0.5, -1, 1)
        level = np.where(grain < 0.1, level + step, level)
        # 縁: 光の当たる上と左を 1 段明るく、下と右は輪郭線の色
        level = np.where((np.arange(h)[:, None] == 0) | (np.arange(w)[None, :] == 0),
                         np.minimum(level + 1, 3), level)
        level = np.clip(level, 0, 3)
        out = ramp[level]
        if mat != "void":
            edge = (np.arange(h)[:, None] == h - 1) | (np.arange(w)[None, :] == w - 1)
            out = np.where(edge[..., None], OUTLINE, out)
    else:
        # 青い面は縁を取らず、全体が光る
        level = np.clip(np.maximum(level, 2), 0, 3)
        out = ramp[level]
    return out


def build_texture(blocks_info, size):
    tex = np.zeros((size, size, 4), np.uint8)
    glow = np.zeros((size, size, 4), np.uint8)
    for info in blocks_info:
        rng = np.random.default_rng(info["seed"])
        for name, (u, v, w, h) in info["faces"].items():
            rgb = paint_face(info["mat"], FACES[name], w, h, rng)
            tex[v:v + h, u:u + w, :3] = np.clip(rgb, 0, 255)
            tex[v:v + h, u:u + w, 3] = 255
            if info["mat"] == "glow":
                glow[v:v + h, u:u + w, :3] = CYAN[3].astype(np.uint8)
                glow[v:v + h, u:u + w, 3] = 255
    return tex, glow


# ---------------------------------------------------------------- 書き出し
def fmt(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def main():
    # 1. 立方体ごとの展開の大きさを決め、棚詰めで置く
    cubes = []  # (bone_index, box, dims, key)
    for bi, (_, _, boxes) in enumerate(BONES):
        for ci, b in enumerate(boxes):
            dx, dy, dz = texel_dims(b)
            cubes.append((bi, ci, b, (dx, dy, dz)))
    blocks = [(2 * dz + 2 * dx, dz + dy, (bi, ci)) for bi, ci, _, (dx, dy, dz) in cubes]
    size, pos = pack(blocks)

    # 2. 幾何 JSON
    geo_bones = []
    infos = []
    for bi, (bname, pivot, boxes) in enumerate(BONES):
        out_cubes = []
        for ci, b in enumerate(boxes):
            (x0, y0, z0), (x1, y1, z1) = b["min"], b["max"]
            dx, dy, dz = texel_dims(b)
            U0, V0 = pos[(bi, ci)]
            faces = net_faces(U0, V0, dx, dy, dz)
            uv = {}
            for fname, (u, v, w, h) in faces.items():
                uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            out_cubes.append({
                "origin": [fmt(x0), fmt(y0), fmt(z0)],
                "size": [fmt(x1 - x0), fmt(y1 - y0), fmt(z1 - z0)],
                "uv": uv,
            })
            infos.append({"mat": b["mat"], "faces": faces, "seed": bi * 1000 + ci})
        geo_bones.append({
            "name": bname,
            "pivot": [fmt(c) for c in pivot],
            "cubes": out_cubes,
        })

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.stone_whale_armor",
                "texture_width": size,
                "texture_height": size,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": geo_bones,
        }],
    }

    # 3. テクスチャ
    tex, glow = build_texture(infos, size)

    geo_path = ROOT / "geo/armor" / f"{NAME}.geo.json"
    tex_path = ROOT / "textures/armor" / f"{NAME}.png"
    glow_path = ROOT / "textures/armor" / f"{NAME}_glowmask.png"
    anim_path = ROOT / "animations/armor" / f"{NAME}.animation.json"
    for p in (geo_path, tex_path, glow_path, anim_path):
        p.parent.mkdir(parents=True, exist_ok=True)

    geo_path.write_text(json.dumps(geo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tex_path)
    Image.fromarray(glow, "RGBA").save(glow_path)
    anim_path.write_text(json.dumps({"format_version": "1.8.0", "animations": {}}, indent=2) + "\n", encoding="utf-8")

    total = sum(len(b[2]) for b in BONES)
    print(f"{total} 個の立方体, テクスチャ {size}x{size}")
    for p in (geo_path, tex_path, glow_path, anim_path):
        print(p)


if __name__ == "__main__":
    main()
