"""岩鯨の心臓 (最終ボス「岩鯨の心臓」) のモデル・テクスチャ・アニメーションを作る (GeckoLib / Bedrock形式)。

使い方: python tools/gen_heart.py
出力先: src/main/resources/assets/skyabyss/
  geo/entity/stone_whale_heart.geo.json
  textures/entity/stone_whale_heart.png (+ _glowmask.png)
  animations/entity/stone_whale_heart.animation.json

構造: 石と骨の鎧に包まれた大きな心臓 (高さ約4ブロック、幅約3ブロック)。
 - heart: 心臓の本体。鼓動で大きさが脈打つ (根元は高さ 36px)
   - 前面の石の殻は左右の房 (shell_l / shell_r) と中央の胸 (shell_c) に分かれ、露出の時に開く
   - 中には光る核 (core) と、水晶の脈 (vein) が房の表面を走る
   - 肋骨の籠 (cage) が心臓を囲む。頭には骨の冠
   - beam_nozzle: 血流の奔流を放つ砲口 (前面中央)
 - chain_a〜d: 天井の梁 (hook) から吊るされた四本の鎖。揺れる
単位は px (16px = 1ブロック)。足元が y=0、正面は -Z。テクスチャは宣言サイズの2倍で描く。
テクスチャは全て決定的 (シード固定の乱数のみ)。光る部分は _glowmask.png に書き、それ以外は透明にする。
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
NAME = "stone_whale_heart"
TEX_W = 256
TEX_H = 256
RES = 2

PALETTE = {
    "bone": ((226, 216, 192), (168, 154, 128)),
    "darkbone": ((176, 162, 136), (118, 104, 84)),
    "stone": ((98, 104, 118), (56, 60, 72)),
    "flesh": ((150, 80, 86), (80, 38, 44)),
    "vein": ((120, 240, 255), (60, 150, 170)),
}
GLOW_CORE = (150, 240, 255)
YELLOW = np.array((196, 170, 112.0))


# ---------------------------------------------------------------- 形の組み立て
class Model:
    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.bones = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, material, decal=None):
        c = {"origin": list(origin), "size": list(size), "material": material}
        if decal:
            c["decal"] = decal
        bone["cubes"].append(c)


def heart_model():
    m = Model(NAME, TEX_W, TEX_H)
    m.bone("root", None, (0, 0, 0))
    h = m.bone("heart", "root", (0, 36, 0))

    # 心臓の本体: 左右の房と、下へすぼまる体
    m.cube(h, (-17, 44, -7), (16, 19, 14), "flesh")
    m.cube(h, (1, 44, -7), (16, 19, 14), "flesh")
    m.cube(h, (-13, 34, -6), (26, 12, 12), "flesh")
    m.cube(h, (-10, 26, -5), (20, 10, 10), "flesh")
    m.cube(h, (-7, 18, -4), (14, 9, 8), "flesh")
    m.cube(h, (-4, 11, -3), (8, 8, 6), "flesh")
    m.cube(h, (-2, 7, -2), (4, 5, 4), "flesh")
    # 房の上の石の縁
    m.cube(h, (-17.5, 62, -7.5), (16.5, 2, 15), "stone")
    m.cube(h, (1, 62, -7.5), (16.5, 2, 15), "stone")
    m.cube(h, (-6, 60, -6), (12, 3, 12), "darkbone")

    # 光る核: 中央の胸の奥にあり、殻が開くと見える
    m.cube(h, (-6, 30, -8), (12, 14, 2), "core")

    # 水晶の脈: 房の表面を走る青く光る筋 (殻と肉のあいだ)
    veins(m, h)

    # 肋骨の籠: 心臓を四段で囲む (前は殻の外)
    cage(m, h)

    # 頭の骨の冠
    for x in (-12, -6, 0, 6, 12):
        tall = 2 if x == 0 else 0
        m.cube(h, (x - 1, 63, -5), (2, 6 + tall, 2), "bone")

    # 左右の殻 (房を覆う。露出の時に外へ開く)
    shell_l = m.bone("shell_l", "heart", (0, 50, -8))
    m.cube(shell_l, (-17.5, 43, -10.5), (16.5, 20, 2.1), "stone")
    m.cube(shell_l, (-17.5, 62, -10.5), (16.5, 2, 2.1), "darkbone")
    for x in (-14.6, -9.6, -4.6):
        m.cube(shell_l, (x, 44, -11.2), (1.2, 17, 0.9), "bone")
    shell_r = m.bone("shell_r", "heart", (0, 50, -8))
    m.cube(shell_r, (1, 43, -10.5), (16.5, 20, 2.1), "stone")
    m.cube(shell_r, (1, 62, -10.5), (16.5, 2, 2.1), "darkbone")
    for x in (4.6, 9.6, 14.6):
        m.cube(shell_r, (x, 44, -11.2), (1.2, 17, 0.9), "bone")

    # 中央の胸の殻: 下の縁を軸に前へ倒れる。前面には光るルーン
    shell_c = m.bone("shell_c", "heart", (0, 30, -8))
    m.cube(shell_c, (-7, 30, -10.5), (14, 15, 2.1), "stone", decal="rune")
    m.cube(shell_c, (-7, 36, -11.2), (14, 1.2, 0.9), "bone")
    m.cube(shell_c, (-7, 41, -11.2), (14, 1.2, 0.9), "bone")

    # 血流の砲口: 殻の前から突き出し、奔流を放つ (左右に振れる)
    nozzle = m.bone("beam_nozzle", "heart", (0, 36, -11))
    m.cube(nozzle, (-4, 32, -11), (8, 8, 2), "darkbone")
    m.cube(nozzle, (-3, 33, -16), (6, 6, 5), "vein")

    # 肋骨の籠 (心臓の周り)
    # 天井の梁と四本の鎖
    root = m.bones[0]
    m.cube(root, (-16, 92, -3), (32, 3, 5), "stone")
    m.cube(root, (-3, 95, -2), (6, 3, 4), "darkbone")
    for name, x, z in (("chain_a", -14, -2), ("chain_b", 14, -2), ("chain_c", -6, 5), ("chain_d", 6, 5)):
        ch = m.bone(name, "root", (x, 62, z))
        for i in range(9):
            y = 62 + i * 3.4
            size = (2.0, 3.0, 1.0) if i % 2 == 0 else (1.0, 3.0, 2.0)
            origin = (x - size[0] / 2, y, z - size[2] / 2)
            m.cube(ch, origin, size, "stone" if i % 2 == 0 else "darkbone")
    return m


def veins(m, parent, seed=7):
    """房の前面に、ランダムに伸びる水晶の脈を置く (z -8.2〜-7.0 で殻と肉のあいだ)"""
    rng = np.random.default_rng(seed)
    for _ in range(11):
        side = 1 if rng.random() < 0.5 else -1
        x = int(round(side * rng.uniform(3, 14)))
        y = int(round(rng.uniform(46, 58)))
        for _step in range(4):
            lo, hi = (2, 15) if side > 0 else (-16, -3)
            x = min(max(x, lo), hi)
            y = min(max(y, 45), 61)
            m.cube(parent, (x, y, -8.2), (1, 1, 1.2), "vein")
            d = rng.integers(0, 4)
            dx, dy = [(1, 0), (0, 1), (-1, 0), (0, -1)][d]
            x += dx
            y += dy


def cage(m, parent):
    """肋骨の籠: 四段の輪。前は殻の外に出る"""
    rings = [(24, 12, 13), (34, 18, 16), (44, 22, 18), (54, 22, 17)]
    for y, rx, rz in rings:
        for a in range(0, 360, 30):
            r = math.radians(a)
            x = rx * math.sin(r)
            z = -rz * math.cos(r)
            m.cube(parent, (round(x - 1.1, 2), y - 1.1, round(z - 1.1, 2)), (2.2, 2.2, 2.2), "bone")


# ---------------------------------------------------------------- テクスチャ
def value_noise(h, w, scale, seed):
    r = np.random.default_rng(seed)
    g = r.random((h // scale + 2, w // scale + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((w + 2 * scale, h + 2 * scale), Image.BICUBIC)
    return np.asarray(img, float)[scale:scale + h, scale:scale + w] / 255.0


def _core(img, glow, X, Y, W, H):
    """光る核: 中心が白く、外へ向かって青く消える"""
    yy, xx = np.mgrid[0:H, 0:W]
    cx, cy = (W - 1) / 2, (H - 1) / 2
    d = np.sqrt(((xx - cx) / max(1.0, W / 2)) ** 2 + ((yy - cy) / max(1.0, H / 2)) ** 2)
    halo = np.clip(1.15 - d, 0, 1)
    core = np.array(GLOW_CORE, float)
    col = core * (1 - halo[..., None]) + np.array((236, 252, 255.0)) * halo[..., None]
    col = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, :3] = col
    img[Y:Y + H, X:X + W, 3] = 255
    glow[Y:Y + H, X:X + W, :3] = col
    glow[Y:Y + H, X:X + W, 3] = 255


def paint(img, glow, x, y, w, h, material, face, seed, decal=None):
    """1面を描く。face は top/bottom/side/front/back。decal は front 面にだけ適用する。"""
    W = max(1, int(round(w * RES)))
    H = max(1, int(round(h * RES)))
    X, Y = int(round(x * RES)), int(round(y * RES))
    if material == "core":
        _core(img, glow, X, Y, W, H)
        return
    light, dark = (np.array(c, float) for c in PALETTE[material])
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:H, 0:W]
    fy = yy / max(1, H - 1)
    edge = np.minimum(np.minimum(xx, W - 1 - xx), np.minimum(yy, H - 1 - yy))
    vertical = face in ("side", "front", "back")

    n = value_noise(H, W, 4, seed) * 0.5 + value_noise(H, W, 1, seed + 1) * 0.3 + rng.random((H, W)) * 0.2
    col = dark + (light - dark) * n[..., None]

    if material in ("bone", "darkbone"):
        col = dark + (light - dark) * (0.72 * value_noise(H, W, 8, seed) + 0.28 * value_noise(H, W, 3, seed + 1))[..., None]
        ridge = 0.5 + 0.5 * np.sin(xx * 0.6 + value_noise(H, W, 8, seed + 4) * 4)
        col *= (0.95 + 0.06 * ridge)[..., None]
        crack = np.abs(value_noise(H, W, 6, seed + 2) - 0.5) < 0.006
        col[crack] *= 0.55
        yel = np.clip(1 - edge / (2.0 * RES), 0, 1) * 0.22
        col = col * (1 - yel[..., None]) + YELLOW * yel[..., None]
    elif material == "stone":
        P = 4 * RES
        joint = ((yy % P) == 0) | ((xx % P) == 0)
        joint &= value_noise(H, W, 2, seed + 5) > 0.22
        col[joint] *= 0.6
        rivet = ((yy % P) == 2) & ((xx % P) == 2)
        col[rivet] = light * 1.12
    elif material == "flesh":
        # 脈打つ肉: 縦の筋と、湿った斑点
        streak = 0.5 + 0.5 * np.sin(xx * 0.9 + value_noise(H, W, 6, seed + 3) * 5)
        col *= (0.86 + 0.2 * streak)[..., None]
        wet = value_noise(H, W, 3, seed + 4) > 0.72
        col[wet] *= 0.7
        if face == "top":
            col *= 1.1
    elif material == "vein":
        # 水晶の脈は全面が光る。縁だけ少し暗い
        col = light * (0.72 + 0.28 * n)[..., None]

    if vertical:
        col *= (1.12 - 0.3 * fy)[..., None]
        col[fy > 0.85] *= 0.86
    else:
        col *= (1.05 if face == "top" else 0.9)

    bw = RES if min(W, H) >= 6 else 0
    if bw:
        col[(yy >= bw) & (yy < 2 * bw) & (edge >= bw)] *= 1.15
        col[(xx >= bw) & (xx < 2 * bw) & (edge >= bw)] *= 1.05
        col[edge < bw] *= 0.62

    gl = np.zeros((H, W, 4), float)
    if material == "vein":
        gl[..., :3] = np.clip(col, 0, 255)
        gl[..., 3] = 255
    if decal and face == "front":
        if decal == "rune":
            fx = xx / max(1, W - 1)
            for ry in (0.3, 0.7):
                line = (np.abs(fy - ry) < 0.12) & (fx > 0.08) & (fx < 0.92) & ~((fx > 0.44) & (fx < 0.56))
                gl[line, :3] = GLOW_CORE
                gl[line, 3] = 255
            for rx in (0.2, 0.8):
                tick = (np.abs(fx - rx) < 0.06) & (fy > 0.1) & (fy < 0.9)
                gl[tick, :3] = GLOW_CORE
                gl[tick, 3] = 255
            lit = gl[..., 3] > 0
            col[lit] = GLOW_CORE

    img[Y:Y + H, X:X + W, :3] = np.clip(col, 0, 255)
    img[Y:Y + H, X:X + W, 3] = 255
    glow[Y:Y + H, X:X + W] = np.clip(gl, 0, 255).astype(np.uint8)


def build(model):
    cubes = [c for b in model.bones for c in b["cubes"]]
    # ボックスUVを棚詰めで並べる
    order = sorted(range(len(cubes)), key=lambda i: -(cubes[i]["size"][2] + cubes[i]["size"][1]))
    x = y = shelf = 0
    for i in order:
        w, hgt, d = cubes[i]["size"]
        cw = math.ceil(2 * (d + w)) + 1
        ch = math.ceil(d + hgt) + 1
        if x + cw > model.tex_w:
            x, y, shelf = 0, y + shelf, 0
        cubes[i]["uv"] = [x, y]
        x += cw
        shelf = max(shelf, ch)
    if y + shelf > model.tex_h:
        raise SystemExit(f"{model.name}: texture overflow {y + shelf} > {model.tex_h}")
    img = np.zeros((model.tex_h * RES, model.tex_w * RES, 4), np.uint8)
    glow = np.zeros_like(img)
    for n, c in enumerate(cubes):
        u, v = c["uv"]
        w, hgt, d = c["size"]
        mat = c["material"]
        seed = 1000 + n * 13
        paint(img, glow, u + d, v, w, d, mat, "top", seed)
        paint(img, glow, u + d + w, v, w, d, mat, "bottom", seed + 1)
        paint(img, glow, u, v + d, d, hgt, mat, "side", seed + 2)
        paint(img, glow, u + d, v + d, w, hgt, mat, "front", seed + 3, decal=c.get("decal"))
        paint(img, glow, u + d + w, v + d, d, hgt, mat, "side", seed + 4)
        paint(img, glow, u + 2 * d + w, v + d, w, hgt, mat, "back", seed + 5)
    bones = []
    for b in model.bones:
        out = {k: v for k, v in b.items() if k != "cubes"}
        out["cubes"] = [{"origin": [round(v, 3) for v in c["origin"]], "size": [round(v, 3) for v in c["size"]], "uv": c["uv"]}
                        for c in b["cubes"]]
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{model.name}", "texture_width": model.tex_w, "texture_height": model.tex_h,
                        "visible_bounds_width": 7, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3.5, 0]},
        "bones": bones}]}
    (ROOT / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / f"geo/entity/{model.name}.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ROOT / f"textures/entity/{model.name}.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(ROOT / f"textures/entity/{model.name}_glowmask.png", optimize=True)
    return len(cubes)


# ---------------------------------------------------------------- アニメーション
def sin(speed, amp, phase=0):
    return f"math.sin(query.anim_time * {speed} + {phase}) * {amp}"


def keyframes(*frames):
    return {f"{t:.2f}": list(v) for t, v in frames}


def anims():
    idle = {"loop": True, "animation_length": 1.6, "bones": {
        # 二拍の鼓動 (ドクン・ドクン)
        "heart": {"scale": keyframes((0, (1, 1, 1)), (0.12, (1.07, 1.07, 1.07)), (0.24, (1, 1, 1)),
                                     (0.36, (1.05, 1.05, 1.05)), (0.6, (1, 1, 1)), (1.6, (1, 1, 1)))},
        # math.sin は度数法。周期 360/225 = 1.6秒 でループの長さと合わせる
        "chain_a": {"rotation": ["0", "0", sin(225, 1.5)]},
        "chain_b": {"rotation": ["0", "0", sin(225, -1.5, 60)]},
        "chain_c": {"rotation": [sin(225, 1.2, 120), "0", "0"]},
        "chain_d": {"rotation": [sin(225, -1.2, 180), "0", "0"]},
    }}
    anim = {
        "animation.stone_whale_heart.idle": idle,
        "animation.stone_whale_heart.walk": idle,
        # 鼓動の衝撃波: 大きく二度脈打つ
        "animation.stone_whale_heart.beat": {"animation_length": 0.9, "bones": {
            "heart": {"scale": keyframes((0, (1, 1, 1)), (0.1, (1.12, 1.12, 1.12)), (0.25, (1, 1, 1)),
                                         (0.35, (1.1, 1.1, 1.1)), (0.6, (1, 1, 1)), (0.9, (1, 1, 1)))}}},
        # 骨の雨: 心臓が上を仰ぎ、鎖が跳ねる
        "animation.stone_whale_heart.rain": {"animation_length": 2.0, "bones": {
            "heart": {"rotation": keyframes((0, (0, 0, 0)), (0.5, (-14, 0, 0)), (1.5, (-14, 0, 0)), (2.0, (0, 0, 0)))},
            "chain_a": {"rotation": keyframes((0, (0, 0, 0)), (0.5, (0, 0, 10)), (1.5, (0, 0, -10)), (2.0, (0, 0, 0)))},
            "chain_b": {"rotation": keyframes((0, (0, 0, 0)), (0.5, (0, 0, -10)), (1.5, (0, 0, 10)), (2.0, (0, 0, 0)))}}},
        # 血流の奔流: 砲口が振れ、心臓が前へ傾く (砲口は 1.5秒で構え、3秒かけて振る)
        "animation.stone_whale_heart.beam": {"animation_length": 4.8, "bones": {
            "beam_nozzle": {"rotation": keyframes((0, (0, 0, 0)), (1.5, (0, -50, 0)), (4.5, (0, 50, 0)), (4.8, (0, 0, 0)))},
            "heart": {"rotation": keyframes((0, (0, 0, 0)), (1.5, (8, 0, 0)), (4.5, (8, 0, 0)), (4.8, (0, 0, 0)))}}},
        # 召喚: 心臓が縮んでから大きく膨らむ
        "animation.stone_whale_heart.summon": {"animation_length": 1.6, "bones": {
            "heart": {"scale": keyframes((0, (1, 1, 1)), (0.3, (0.92, 0.92, 0.92)), (0.8, (1.15, 1.15, 1.15)),
                                         (1.2, (1, 1, 1)), (1.6, (1, 1, 1)))},
            "chain_a": {"rotation": keyframes((0, (0, 0, 0)), (0.8, (0, 0, -8)), (1.6, (0, 0, 0)))},
            "chain_d": {"rotation": keyframes((0, (0, 0, 0)), (0.8, (0, 0, 8)), (1.6, (0, 0, 0)))}}},
        # 露出: 左右の殻が開き、中央の胸が倒れて核が見える (6秒保つ)
        "animation.stone_whale_heart.expose": {"animation_length": 6.0, "bones": {
            "shell_l": {"rotation": keyframes((0, (0, 0, 0)), (0.5, (0, -62, 0)), (5.5, (0, -62, 0)), (6.0, (0, 0, 0)))},
            "shell_r": {"rotation": keyframes((0, (0, 0, 0)), (0.5, (0, 62, 0)), (5.5, (0, 62, 0)), (6.0, (0, 0, 0)))},
            "shell_c": {"rotation": keyframes((0, (0, 0, 0)), (0.6, (-70, 0, 0)), (5.5, (-70, 0, 0)), (6.0, (0, 0, 0)))}}},
    }
    (ROOT / "animations/entity").mkdir(parents=True, exist_ok=True)
    (ROOT / f"animations/entity/{NAME}.animation.json").write_text(
        json.dumps({"format_version": "1.8.0", "animations": anim}, indent=1), encoding="utf-8")


def main():
    model = heart_model()
    count = build(model)
    anims()
    print("ok", NAME, "cubes", count)


if __name__ == "__main__":
    main()
