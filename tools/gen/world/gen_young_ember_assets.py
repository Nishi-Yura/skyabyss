"""余燼の仔 (young_ember。余燼の巨神の「子」= 分かれた鼓動・歌のかけら。28 章) のゲームの素材を書く。

乗っている間だけ現れ、パーティ全員 (最大 6 人。2 列 x 3 段) を背の鞍に乗せて地面を駆ける大きな乗り物。

使い方: リポジトリ直下で  python tools/gen/world/gen_young_ember_assets.py
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/young_ember.geo.json            箱の geo (低ポリが切れているときの姿。骨は低ポリのメッシュと同じ 13 本)
  textures/entity/young_ember.png            箱のテクスチャ (1 ブロック 32 画素。宣言の大きさの 2 倍の絵)
  textures/entity/young_ember_glowmask.png   光る画素 (燠のひび・胸の核 = 歌のかけら・目・火口)
  animations/entity/young_ember.animation.json
  tools/sources/young/young_ember_notes.json (Java 側への覚え: 鞍の高さ・当たり判定・骨・アニメの長さ)
低ポリのメッシュは tools/install/install_lowpoly_young_ember.py (案は tools/rough/lowpoly/young_ember/opt_{a,b,c}.py)。

骨 (作図は前 = -Z。geo は x と z を反転し、root を Y 軸で 180 度回す。真珠守りと同じ決まり。最後の向きは前 = -Z):
  root > body > saddle (鞍。アニメで動かさない)
              > core (胸の核 = 歌のかけら。脈打つ)
              > neck > head > jaw
              > tail > tail2
              > leg_fr, leg_fl, leg_br, leg_bl (作図の +x = r)
鞍の上面 SEAT_Y = 40 単位 = 2.5 ブロック (x -1.6..1.6、z -3.2..3.2 ブロックを平らに覆う)。
座席 (ブロック): x = +-1.0、z = -2.0 / 0 / +2.0。
アニメ: idle 3.0 (loop)、move 1.2 (loop。四つ足の速歩)、appear 1.6 (歌のかけらから育つ)、dissolve 1.4 (hold。縮んで歌に溶ける)。
idle・move では鞍の上面が 0.15 ブロック (2.4 単位) より動かない (verify で確かめる)。
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
NAME = "young_ember"
SEAT_Y = 40.0          # 単位 (1/16 ブロック)
SEAT_HALF = (25.6, 51.2)
SEATS = [(sx * 16.0, SEAT_Y, sz * 16.0) for sz in (-2.0, 0.0, 2.0) for sx in (-1.0, 1.0)]
HITBOX = [4.6, 3.4]

# 骨 (作図空間 = 前が -Z)。低ポリのラフ (tools/rough/lowpoly/young_ember/ye.py) もここを読む。
SKELETON_DRAW = {
    "root": (None, (0.0, 0.0, 0.0)),
    "body": ("root", (0.0, 28.0, 0.0)),
    "saddle": ("body", (0.0, 40.0, 0.0)),
    "core": ("body", (0.0, 28.0, -70.0)),
    "neck": ("body", (0.0, 36.0, -60.0)),
    "head": ("neck", (0.0, 50.0, -80.0)),
    "jaw": ("head", (0.0, 46.0, -88.0)),
    "tail": ("body", (0.0, 28.0, 60.0)),
    "tail2": ("tail", (0.0, 24.0, 80.0)),
    "leg_fr": ("body", (24.0, 22.0, -40.0)),
    "leg_fl": ("body", (-24.0, 22.0, -40.0)),
    "leg_br": ("body", (24.0, 22.0, 40.0)),
    "leg_bl": ("body", (-24.0, 22.0, 40.0)),
}
LEGS = ["leg_fr", "leg_fl", "leg_br", "leg_bl"]

# ====================================================================== 箱 (案 A「燠石の仔熊」の箱の姿)
# (骨, 材, 中心 (作図空間), 大きさ)。重ならないこと (触れるのはよい)。
BOXES = [
    ("body", "rock", (0.0, 26.0, 0.0), (64, 22, 120)),          # 胴 y 15..37、z -60..60
    ("saddle", "bone", (0.0, 38.5, 0.0), (56, 3, 108)),         # 鞍の腰掛け 上面 = 40
    ("saddle", "bone", (29.0, 41.0, 0.0), (2, 2, 108)),         # 手すり
    ("saddle", "bone", (-29.0, 41.0, 0.0), (2, 2, 108)),
    ("saddle", "bone", (0.0, 42.0, -56.0), (40, 4, 4)),         # 前の鞍頭
    ("core", "core", (0.0, 27.0, -63.0), (10, 8, 6)),           # 胸の核 (歌のかけら)
    ("neck", "ash", (0.0, 42.0, -68.0), (22, 16, 16)),          # 首 y 34..50、z -76..-60
    ("head", "rock", (0.0, 54.0, -88.0), (28, 20, 24)),         # 頭 y 44..64、z -100..-76
    ("head", "ash", (0.0, 50.0, -104.0), (16, 8, 8)),           # 鼻づら
    ("head", "eye", (9.0, 58.0, -100.5), (4, 4, 1)),
    ("head", "eye", (-9.0, 58.0, -100.5), (4, 4, 1)),
    ("head", "rock", (10.0, 66.5, -84.0), (6, 5, 6)),
    ("head", "rock", (-10.0, 66.5, -84.0), (6, 5, 6)),
    ("jaw", "dark", (0.0, 42.0, -98.0), (18, 4, 16)),
    ("body", "rock", (0.0, 41.0, 61.0), (20, 8, 12)),           # 背の小さな火口 (親の火山)
    ("body", "lava", (0.0, 45.5, 61.0), (10, 1, 6)),
    ("tail", "rock", (0.0, 26.0, 68.0), (18, 14, 16)),
    ("tail2", "rock", (0.0, 24.0, 81.0), (10, 8, 10)),
]
for leg in LEGS:
    sx = 1 if leg[-1] == "r" else -1
    z = -40.0 if leg[4] == "f" else 40.0
    BOXES.append((leg, "rock", (sx * 22.0, 9.0, z), (14, 12, 14)))
    BOXES.append((leg, "claw", (sx * 22.0, 1.5, z - 1.0), (16, 3, 18)))

PAL = {
    "rock": [(24, 23, 27), (36, 35, 40), (50, 48, 54), (66, 64, 70)],
    "ash": [(40, 34, 35), (56, 48, 47), (74, 64, 61), (94, 82, 76)],
    "bone": [(160, 150, 130), (196, 188, 164), (226, 218, 194)],
    "claw": [(40, 36, 36), (70, 64, 60), (104, 96, 88)],
    "dark": [(16, 15, 18), (36, 10, 10), (62, 18, 16)],
    "lava": [(236, 104, 24), (255, 160, 44), (255, 214, 100)],
    "core": [(255, 160, 44), (255, 214, 100), (255, 240, 180)],
    "eye": [(236, 104, 24), (255, 160, 44), (255, 214, 100)],
}
GLOW_MATS = ("lava", "core", "eye")
SEAM_MATS = ("rock", "ash")
SEAM = [(190, 56, 14), (236, 104, 24), (255, 160, 44)]
FACE_LIGHT = {"up": 1.0, "north": 0.75, "south": 0.6, "west": 0.55, "east": 0.55, "down": 0.3}
TEX_W = 512   # 宣言の大きさ (1 単位 = 1 画素)。絵はこの 2 倍
PX = 2


def flip(p):
    return (-p[0], p[1], -p[2])


def bone_geo_pivot(b):
    return [float(v) + 0.0 for v in flip(SKELETON_DRAW[b][1])]


def check_overlaps(boxes):
    out = []
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            (_, _, c0, s0), (_, _, c1, s1) = boxes[i], boxes[j]
            ov = [min(c0[k] + s0[k] / 2, c1[k] + s1[k] / 2) - max(c0[k] - s0[k] / 2, c1[k] - s1[k] / 2) for k in range(3)]
            if all(o > 1e-6 for o in ov):
                out.append((i, j, boxes[i][:2], boxes[j][:2]))
    return out


def _uv_rects(u, v, w, h, d):
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "west": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def build_geo():
    over = check_overlaps(BOXES)
    assert not over, over
    placed = []
    x = y = rowh = 0
    order = sorted(range(len(BOXES)), key=lambda i: -(BOXES[i][3][2] + BOXES[i][3][1]))
    pos = {}
    for i in order:
        w, h, d = (int(math.ceil(v)) for v in BOXES[i][3])
        bw, bh = 2 * (w + d), d + h
        if x + bw > TEX_W:
            x, y, rowh = 0, y + rowh, 0
        pos[i] = (x, y)
        x += bw
        rowh = max(rowh, bh)
    placed = [pos[i] for i in range(len(BOXES))]
    tex_h = int(2 ** math.ceil(math.log2(max(y + rowh, 16))))
    img = np.zeros((tex_h * PX, TEX_W * PX, 4), np.uint8)
    glow = np.zeros_like(img)
    rng = np.random.default_rng(1717)
    bones = {b: {"name": b, "pivot": bone_geo_pivot(b)} for b in SKELETON_DRAW}
    for b, (parent, _) in SKELETON_DRAW.items():
        if parent:
            bones[b]["parent"] = parent
    bones["root"]["rotation"] = [0.0, 180.0, 0.0]
    for (bone, mat, c, s), (u, v) in zip(BOXES, placed):
        lo = [c[k] - s[k] / 2 for k in range(3)]
        hi = [c[k] + s[k] / 2 for k in range(3)]
        g_lo = [-hi[0], lo[1], -hi[2]]
        cube = {"origin": [round(a + 0.0, 4) for a in g_lo], "size": [float(a) for a in s], "uv": [u, v]}
        bones[bone].setdefault("cubes", []).append(cube)
        w, h, d = (int(math.ceil(a)) for a in s)
        ramp = np.array(PAL[mat], float)
        for face, (fu, fv, fw, fh) in _uv_rects(u, v, w, h, d).items():
            if fw <= 0 or fh <= 0:
                continue
            H, W = fh * PX, fw * PX
            yy, xx = np.mgrid[0:H, 0:W]
            lit = FACE_LIGHT[face]
            t = 0.25 + 0.6 * lit + 0.12 * (rng.random((H, W)) - 0.5) + 0.1 * (1 - yy / max(H - 1, 1)) * (face != "up")
            edge = (xx == 0) | (yy == 0) | (xx == W - 1) | (yy == H - 1)
            t = t - 0.18 * edge
            idx = np.clip((t * len(ramp)).astype(int), 0, len(ramp) - 1)
            col = ramp[idx]
            g = glow[fv * PX:fv * PX + H, fu * PX:fu * PX + W]
            if mat in SEAM_MATS:
                # 燠のひび: 斜めにジグザグに走る細い線 (面ごとに位置を変える)
                ph = rng.random() * 40
                k = ((xx + np.round(3 * np.sin(yy * 0.35 + ph))).astype(int) + yy // 2 + int(ph)) % 23
                seam = (k == 0) & (rng.random((H, W)) > 0.08)
                col = np.where(seam[..., None], np.array(SEAM, float)[np.clip(idx, 0, 2)], col)
                g[seam] = (255, 255, 255, 255)
            y0, x0 = fv * PX, fu * PX
            img[y0:y0 + H, x0:x0 + W, :3] = np.clip(col, 0, 255).astype(np.uint8)
            img[y0:y0 + H, x0:x0 + W, 3] = 255
            if mat in GLOW_MATS:
                g[...] = (255, 255, 255, 255)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 12, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2.5, 0]},
        "bones": [bones[b] for b in SKELETON_DRAW]}]}
    return geo, img, glow


# ====================================================================== アニメ (ファイルの値そのまま)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


ONE = (1, 1, 1)


def frames(*pairs):
    return {t: v for t, v in pairs}


def sc(v):
    return (v, v, v)


# 速歩: 対角の脚 (右前 + 左後ろ、左前 + 右後ろ) が組。1 周 1.2 秒 = 300 度/秒。
GAIT = {"leg_fr": 0, "leg_bl": 0, "leg_fl": 180, "leg_br": 180}
ANIM = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        # 胴がゆっくり息をする (上下 0.4 単位)。胸の核が脈打つ。首と尾がゆれ、顎が少し開く。
        "body": {"position": ["0", sn(0.4, 120), "0"]},
        "core": {"scale": [f"1 + {sn(0.12, 240)}"] * 3},
        "neck": {"rotation": [sn(2, 120, 60), sn(3, 120, 30), "0"]},
        "head": {"rotation": [sn(3, 120, 120), sn(5, 60), "0"]},
        "jaw": {"rotation": [f"-3 + {sn(3, 240)}", "0", "0"]},
        "tail": {"rotation": ["0", sn(8, 120), "0"]},
        "tail2": {"rotation": ["0", sn(10, 120, 60), "0"]},
    }},
    "move": {"loop": True, "len": 1.2, "bones": {
        **{b: {"rotation": [sn(24, 300, ph), "0", "0"]} for b, ph in GAIT.items()},
        "body": {"position": ["0", "1.2 * math.abs(math.sin(300 * query.anim_time))", "0"],
                 "rotation": ["0", "0", sn(0.8, 300)]},
        "neck": {"rotation": [sn(4, 600, 90), "0", "0"]},
        "head": {"rotation": [sn(4, 600, 180), "0", "0"]},
        "tail": {"rotation": [sn(3, 600), sn(10, 300), "0"]},
        "tail2": {"rotation": ["0", sn(14, 300, 60), "0"]},
        "core": {"scale": [f"1 + {sn(0.15, 600)}"] * 3},
    }},
    "appear": {"loop": False, "len": 1.6, "bones": {
        # 足もとの歌のかけら (光る核) が先に膨らみ、体がそのまわりに育つ。最後に首を振って身震い。
        "root": {"scale": frames((0, sc(0.04)), (0.5, sc(0.25)), (1.1, sc(1.08)), (1.35, sc(0.97)), (1.6, ONE))},
        "core": {"scale": frames((0, sc(4.0)), (0.5, sc(3.0)), (1.1, sc(1.3)), (1.6, ONE))},
        "body": {"rotation": frames((0, (0, 0, 0)), (1.1, (0, 0, 0)), (1.25, (0, 0, 3)), (1.4, (0, 0, -2)), (1.6, (0, 0, 0)))},
        "neck": {"rotation": frames((0, (-20, 0, 0)), (1.1, (-10, 0, 0)), (1.35, (12, 0, 0)), (1.6, (0, 0, 0)))},
        "head": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 15, 0)), (1.35, (0, -15, 0)), (1.5, (0, 6, 0)), (1.6, (0, 0, 0)))},
        "jaw": {"rotation": frames((0, (0, 0, 0)), (1.2, (-14, 0, 0)), (1.45, (0, 0, 0)))},
        "tail": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 20, 0)), (1.4, (0, -12, 0)), (1.6, (0, 0, 0)))},
    }},
    "dissolve": {"loop": "hold", "len": 1.4, "bones": {
        # 伏せて (0.3)、核が膨らんで光り、体が縮んで歌のかけらに溶ける (root の拡大 → 0)。
        "root": {"scale": frames((0, ONE), (0.3, sc(1.03)), (1.0, sc(0.35)), (1.4, sc(0.0)))},
        "core": {"scale": frames((0, ONE), (0.4, sc(1.6)), (1.0, sc(2.6)), (1.4, sc(3.5)))},
        "body": {"position": frames((0, (0, 0, 0)), (0.3, (0, -2, 0)), (1.4, (0, -2, 0)))},
        "neck": {"rotation": frames((0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.4, (-16, 0, 0)))},
        "tail": {"rotation": frames((0, (0, 0, 0)), (0.3, (-10, 0, 0)), (1.4, (-10, 0, 0)))},
        **{b: {"rotation": frames((0, (0, 0, 0)), (0.3, (0, 0, 8 if b[-1] == "r" else -8)), (1.4, (0, 0, 8 if b[-1] == "r" else -8)))}
           for b in LEGS},
    }},
}


def _num(v):
    return float(round(v, 4)) + 0.0


def anim_json():
    out = {}
    for name, a in ANIM.items():
        bones = {}
        for bone, chans in a["bones"].items():
            bj = {}
            for ch_, val in chans.items():
                if isinstance(val, dict):
                    bj[ch_] = {f"{t:.4g}": {"vector": [_num(x) for x in v]} for t, v in sorted(val.items())}
                else:
                    bj[ch_] = list(val)
            bones[bone] = bj
        entry = {"animation_length": a["len"], "bones": bones}
        entry["loop"] = "hold_on_last_frame" if a["loop"] == "hold" else bool(a["loop"])
        out[f"animation.{NAME}.{name}"] = entry
    return {"format_version": "1.8.0", "animations": out}


def eval_channel(val, t):
    if isinstance(val, dict):
        ks = sorted(val)
        if t <= ks[0]:
            return np.array(val[ks[0]], float)
        for k0, k1 in zip(ks, ks[1:]):
            if k0 <= t <= k1:
                u = (t - k0) / (k1 - k0) if k1 > k0 else 0
                return np.array(val[k0], float) * (1 - u) + np.array(val[k1], float) * u
        return np.array(val[ks[-1]], float)
    env = {"msin": lambda d: math.sin(math.radians(d)), "mabs": abs, "t": t}
    return np.array([float(eval(str(e).replace("math.sin", "msin").replace("math.abs", "mabs")
                                .replace("query.anim_time", "t"), {}, env)) for e in val])


def pose_at(name, t):
    """{骨: {rotation, scale, position}} (ファイルの値)。低ポリの確認の絵 (ye.pose_body) に渡す。"""
    return {b: {ch_: tuple(eval_channel(v, t)) for ch_, v in chans.items()} for b, chans in ANIM[name]["bones"].items()}


# ---------------------------------------------------------------------- 姿勢 (作図空間で点を動かす。root の拡大も含む)

def _rot_geo(rx, ry, rz):
    def R(axis, a):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        if axis == "x":
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == "y":
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return R("z", -rz) @ R("y", -ry) @ R("x", -rx)


def pose_point_draw(p, bone, pose):
    """作図空間の点 p (骨 bone に付く) を pose のときの作図空間へ。GeckoLib の順 (pivot へ寄せ → 拡大 → 回転 → 戻す → 位置)。
    geo 空間 (x・z 反転) で計算し、root の拡大・位置も含める (root の休めの 180 度は含めない)。"""
    q = np.array([-p[0], p[1], -p[2]], float)
    while bone is not None:
        parent, pivot_d = SKELETON_DRAW[bone]
        pv = np.array([-pivot_d[0], pivot_d[1], -pivot_d[2]], float)
        st = pose.get(bone)
        if st is not None:
            d = q - pv
            if "scale" in st:
                d = d * np.array(st["scale"], float)
            if "rotation" in st:
                d = _rot_geo(*st["rotation"]) @ d
            q = d + pv
            if "position" in st:
                ps = np.array(st["position"], float)
                q = q + np.array([-ps[0], ps[1], ps[2]])
        bone = parent
    return np.array([-q[0], q[1], -q[2]])


def saddle_drift():
    """idle・move の間の鞍の上面の四隅と座席の、休めの姿からの最大のずれ (単位)。"""
    pts = [np.array(s) for s in SEATS] + [np.array((sx * SEAT_HALF[0], SEAT_Y, sz * SEAT_HALF[1]))
                                          for sx in (-1, 1) for sz in (-1, 1)]
    worst = {}
    for name in ("idle", "move"):
        m = 0.0
        for i in range(61):
            t = ANIM[name]["len"] * i / 60
            pose = pose_at(name, t)
            for p in pts:
                m = max(m, float(np.linalg.norm(pose_point_draw(p, "saddle", pose) - p)))
        worst[name] = m
    return worst


def verify(geo, anim):
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    assert geo["minecraft:geometry"][0]["bones"][0]["name"] == "root"
    for an, a in anim["animations"].items():
        for b in a["bones"]:
            assert b in names, (an, b)
    for r, ln in (("idle", 3.0), ("move", 1.2), ("appear", 1.6), ("dissolve", 1.4)):
        assert anim["animations"][f"animation.{NAME}.{r}"]["animation_length"] == ln, r
    drift = saddle_drift()
    for k, v in drift.items():
        assert v <= 2.4, (k, v)
    return drift


def write_notes(drift):
    notes = {"model": NAME, "seat_y": SEAT_Y / 16.0, "seat_y_units": SEAT_Y, "hitbox": HITBOX,
             "seats_blocks": [[s[0] / 16.0, s[1] / 16.0, s[2] / 16.0] for s in SEATS],
             "saddle_flat_area_blocks": {"x": [-1.6, 1.6], "z": [-3.2, 3.2]},
             "front": "-Z (geo root carries rotation [0,180,0] like pearl_keeper; final front = -Z)",
             "bones": list(SKELETON_DRAW), "anims": {k: v["len"] for k, v in ANIM.items()},
             "loops": {k: ("hold_on_last_frame" if v["loop"] == "hold" else bool(v["loop"])) for k, v in ANIM.items()},
             "saddle_max_drift_blocks": {k: round(v / 16.0, 4) for k, v in drift.items()},
             "notes": "Entity (not LivingEntity). saddle bone is never animated; idle/move move the seat <= 0.15 block. "
                      "appear/dissolve scale root (dissolve ends at scale 0, hold). core = glowing song mote (glowmask)."}
    p = ROOT / "tools/sources/young" / f"{NAME}_notes.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(notes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    geo, img, glow = build_geo()
    anim = anim_json()
    drift = verify(geo, anim)
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/entity" / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}.png")
    assert glow[..., 3].any()
    Image.fromarray(glow, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}_glowmask.png")
    (ASSETS / "animations/entity" / f"{NAME}.animation.json").write_text(json.dumps(anim, indent=1, ensure_ascii=False) + "\n",
                                                                        encoding="utf-8")
    write_notes(drift)
    n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{NAME}: 箱 {n}、テクスチャ {img.shape[1]}x{img.shape[0]}、アニメ {len(anim['animations'])}、鞍のずれ {drift}")
    for k, v in ANIM.items():
        print(f"  animation.{NAME}.{k}: {v['len']} s = {round(v['len'] * 20)} tick, loop={v['loop']}")


if __name__ == "__main__":
    main()
