"""名づきの番「真珠守り」(pearl_keeper。潮殻の背の気室の頂、24 の 5 章) のゲームの素材を書く。

使い方: リポジトリ直下で  python tools/gen/world/gen_pearl_keeper_assets.py [--preview]
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/pearl_keeper.geo.json            箱の geo (低ポリが切れているときの姿。骨は低ポリのメッシュと同じ 14 本)
  textures/entity/pearl_keeper.png            箱のテクスチャ (1 ブロック 32 画素。宣言の大きさの 2 倍の絵)
  textures/entity/pearl_keeper_glowmask.png   光る画素 (真珠・目)
  animations/entity/pearl_keeper.animation.json
低ポリのメッシュは tools/install/install_lowpoly_pearl_keeper.py (案は tools/rough/lowpoly/pearl_keeper/opt_{a,b,c}.py)。

骨 (作図は前 = -Z。geo は x と z を反転し、root を Y 軸で 180 度回す。潮招きと同じ決まり):
  root > body > shell > pearl
              > head (宿借りの柔らかい体・目柄。殻に籠もると縮む)
              > claw_r (大きいはさみ = ゲームの右) > claw_r_pincer
              > claw_l (小さいはさみ)
              > leg_r1..3, leg_l1..3
アニメの角度はファイルに書く値そのまま (GeckoLib が読む値)。x が負 = 前へうつむく、正 = 反る。
claw_r の x が正 = はさみを振り上げる、claw_r_pincer の x が正 = 指を開く。leg_r の z が正・leg_l の z が負 = 脚を上げる。
位置は y (上下) だけ使う。

アニメの長さ (秒。Java の技の時間に使う):
  idle 3.0 (loop)、walk 1.2 (loop)、hurt 0.3、death 2.0 (hold)、emerge 1.5、
  updraft 1.4 (溜め 1.0)、downdraft 1.6 (溜め 1.2)、charge 1.6 (溜め 0.8、転がり 0.8〜1.35)、
  volley 1.5 (溜め 1.0、吐く 1.0 / 1.15 / 1.3)、shell_in 0.5 (hold)、shelled 2.0 (loop)、shell_out 0.6
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
NAME = "pearl_keeper"

# 骨 (作図空間 = 前が -Z)。低ポリのラフ (tools/rough/lowpoly/pearl_keeper/pk.py) もここを読む。
SKELETON_DRAW = {
    "root": (None, (0.0, 0.0, 0.0)),
    "body": ("root", (0.0, 24.0, 4.0)),
    "shell": ("body", (0.0, 14.0, 6.0)),
    "pearl": ("shell", (0.0, 44.0, -4.0)),
    "head": ("body", (0.0, 14.0, -10.0)),
    "claw_r": ("body", (11.0, 15.0, -14.0)),
    "claw_r_pincer": ("claw_r", (21.0, 21.0, -36.0)),
    "claw_l": ("body", (-10.0, 14.0, -14.0)),
    "leg_r1": ("body", (11.0, 11.0, -9.0)),
    "leg_r2": ("body", (12.0, 11.0, -2.0)),
    "leg_r3": ("body", (11.0, 11.0, 5.0)),
    "leg_l1": ("body", (-11.0, 11.0, -9.0)),
    "leg_l2": ("body", (-12.0, 11.0, -2.0)),
    "leg_l3": ("body", (-11.0, 11.0, 5.0)),
}

# ====================================================================== 箱 (案 A「螺旋の宿」の箱の姿)
# (骨, 材, 中心 (作図空間), 大きさ)。重ならないこと (触れるのはよい)。
BOXES = [
    # 殻: 体の巻き → 2 段目 → 3 段目 → 殻頂、殻口の唇、真珠の台
    ("shell", "shell", (0.0, 20.0, 8.0), (24, 20, 24)),
    ("shell", "lip", (0.0, 19.5, -5.0), (22, 19, 2)),
    ("shell", "shell2", (2.0, 34.0, 12.0), (18, 8, 16)),
    ("shell", "shell", (3.0, 41.0, 14.0), (12, 6, 10)),
    ("shell", "shell2", (4.0, 46.5, 15.0), (6, 5, 6)),
    ("shell", "lip", (0.0, 32.75, -2.0), (4, 5.5, 4)),
    ("shell", "lip", (0.0, 37.0, -4.0), (10, 3, 10)),
    ("pearl", "pearl", (0.0, 44.0, -4.0), (11, 11, 11)),
    # 頭胸と目柄
    ("head", "crab", (0.0, 15.5, -12.0), (16, 9, 12)),
    ("head", "crab2", (4.0, 23.5, -15.0), (2, 7, 2)),
    ("head", "crab2", (-4.0, 23.5, -15.0), (2, 7, 2)),
    ("head", "eye", (4.0, 28.5, -15.0), (3, 3, 3)),
    ("head", "eye", (-4.0, 28.5, -15.0), (3, 3, 3)),
    # 大きいはさみ
    ("claw_r", "claw", (12.0, 15.5, -19.0), (6, 5, 10)),
    ("claw_r", "claw", (20.0, 17.0, -31.0), (12, 15, 14)),
    ("claw_r", "tip", (20.0, 11.5, -43.0), (6, 4, 10)),
    ("claw_r_pincer", "claw", (20.0, 19.5, -42.0), (5, 5, 8)),
    ("claw_r_pincer", "tip", (20.0, 19.0, -47.0), (4, 4, 2)),
    # 小さいはさみ
    ("claw_l", "claw", (-12.5, 13.5, -18.0), (5, 4, 8)),
    ("claw_l", "claw", (-15.0, 12.0, -26.5), (6, 7, 9)),
    ("claw_l", "tip", (-15.0, 12.0, -34.0), (4, 5, 6)),
]
for s, side in ((1, "r"), (-1, "l")):
    for i, (z, x0, x1, xf) in enumerate(((-9.0, 11.0, 20.0, 21.5), (-2.0, 12.0, 22.0, 23.5), (5.0, 12.0, 20.0, 21.5)), start=1):
        BOXES.append((f"leg_{side}{i}", "crab", (s * (x0 + x1) / 2, 12.0, z), (x1 - x0, 4, 4)))
        BOXES.append((f"leg_{side}{i}", "tip", (s * xf, 5.0, z), (3, 10, 3)))

PAL = {
    "shell": [(150, 146, 166), (196, 192, 208), (226, 222, 234), (246, 242, 250)],
    "shell2": [(150, 146, 166), (196, 192, 208), (226, 222, 234), (246, 242, 250)],
    "band": [(28, 88, 96), (48, 126, 128), (86, 168, 160)],
    "lip": [(204, 150, 168), (236, 196, 206), (252, 228, 232)],
    "pearl": [(214, 232, 244), (240, 250, 255), (255, 255, 255)],
    "crab": [(128, 36, 34), (182, 64, 48), (220, 102, 70), (242, 146, 106)],
    "crab2": [(70, 18, 22), (128, 36, 34), (182, 64, 48)],
    "claw": [(128, 36, 34), (182, 64, 48), (220, 102, 70), (242, 146, 106)],
    "tip": [(176, 168, 156), (220, 214, 200), (244, 240, 228)],
    "eye": [(8, 10, 16), (18, 20, 28), (32, 34, 44)],
}
FACE_LIGHT = {"up": 1.0, "north": 0.75, "south": 0.6, "west": 0.55, "east": 0.55, "down": 0.3}
TEX_W = 128   # 宣言の大きさ (1 単位 = 1 画素)。絵はこの 2 倍
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
    # 箱 UV を詰める (幅 TEX_W の行に並べる)
    placed = []
    x = y = rowh = 0
    for bone, mat, c, s in BOXES:
        w, h, d = (int(math.ceil(v)) for v in s)
        bw, bh = 2 * (w + d), d + h
        if x + bw > TEX_W:
            x, y, rowh = 0, y + rowh, 0
        placed.append((x, y))
        x += bw
        rowh = max(rowh, bh)
    tex_h = y + rowh
    tex_h = int(2 ** math.ceil(math.log2(max(tex_h, 16))))
    img = np.zeros((tex_h * PX, TEX_W * PX, 4), np.uint8)
    glow = np.zeros_like(img)
    rng = np.random.default_rng(4242)
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
            # 面の縁を一段暗く (手描き風の縁取り)
            edge = (xx == 0) | (yy == 0) | (xx == W - 1) | (yy == H - 1)
            t = t - 0.18 * edge
            idx = np.clip((t * len(ramp)).astype(int), 0, len(ramp) - 1)
            col = ramp[idx]
            if mat in ("shell", "shell2") and face not in ("up", "down"):
                # 青緑の螺旋の帯 (斜めの縞)
                band = ((yy // PX + xx // PX + (0 if mat == "shell" else 3)) % 9) < 2
                bramp = np.array(PAL["band"], float)
                col = np.where(band[..., None], bramp[np.clip(idx, 0, 2)], col)
            if mat == "eye" and face in ("north", "west", "east"):
                pup = (np.abs(xx - W / 2 + 0.5) < PX * 0.6) & (np.abs(yy - H / 2 + 0.5) < PX * 0.6)
                col = np.where(pup[..., None], np.array([180, 240, 255.0]), col)
                g = glow[fv * PX:fv * PX + H, fu * PX:fu * PX + W]
                g[pup] = (255, 255, 255, 255)
            y0, x0 = fv * PX, fu * PX
            img[y0:y0 + H, x0:x0 + W, :3] = np.clip(col, 0, 255).astype(np.uint8)
            img[y0:y0 + H, x0:x0 + W, 3] = 255
            if mat == "pearl":
                glow[y0:y0 + H, x0:x0 + W] = (255, 255, 255, 255)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.6, 0]},
        "bones": [bones[b] for b in SKELETON_DRAW]}]}
    return geo, img, glow


# ====================================================================== アニメ (ファイルの値そのまま)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


LEGS = [f"leg_{s}{i}" for s in "rl" for i in (1, 2, 3)]
SMALL = (0.15, 0.15, 0.15)
ONE = (1, 1, 1)
# 殻に籠もった姿: 柔らかい体・小さいはさみ・脚は縮んで殻口の中へ、大きいはさみは内へ折って殻口の戸にする
SHELLED = {
    "body": {"position": (0, -9, 0)},
    "head": {"scale": SMALL},
    "claw_l": {"scale": SMALL},
    "claw_r": {"rotation": (40, -95, -40), "scale": (0.9, 0.9, 0.9)},
    "claw_r_pincer": {"rotation": (-5, 0, 0)},
    **{b: {"rotation": (0, 0, 35 if b[4] == "r" else -35), "scale": (0.2, 0.2, 0.2)} for b in LEGS},
}
REST = {"rotation": (0, 0, 0), "scale": ONE, "position": (0, 0, 0)}


def frames(*pairs):
    return {t: v for t, v in pairs}


ANIM = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        # 殻がゆっくり息をする (膨らむ・しぼむ)。真珠が脈打つ。目柄と触角がぴくつく。
        "shell": {"scale": [f"1 + {sn(0.02, 120)}", f"1 + {sn(0.03, 120)}", f"1 + {sn(0.02, 120)}"]},
        "pearl": {"scale": [f"1 + {sn(0.05, 240)}", f"1 + {sn(0.05, 240)}", f"1 + {sn(0.05, 240)}"]},
        "body": {"position": ["0", sn(0.5, 120, 180), "0"]},
        "head": {"rotation": [sn(2, 240), sn(4, 120, 30), "0"]},
        "claw_r": {"rotation": [f"4 + {sn(3, 120)}", sn(2, 120, 90), "0"]},
        "claw_r_pincer": {"rotation": [f"6 + {sn(5, 240)}", "0", "0"]},
        "claw_l": {"rotation": [f"6 + {sn(6, 240, 60)}", sn(4, 120), "0"]},
    }},
    "walk": {"loop": True, "len": 1.2, "bones": {
        **{b: {"rotation": ["0", sn(14, 300, ph), f"{'' if b[4] == 'r' else '-'}(6 + {sn(6, 300, ph + 90)})"]}
           for b, ph in zip(LEGS, (0, 120, 240, 180, 300, 60))},
        "body": {"position": ["0", "0.6 * math.abs(math.sin(300 * query.anim_time))", "0"]},
        "shell": {"rotation": ["0", "0", sn(2.5, 300)]},
        "claw_r": {"rotation": ["8", "0", "0"]},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": frames((0, (0, 0, 0)), (0.08, (7, 0, -3)), (0.3, (0, 0, 0)))},
        "head": {"scale": frames((0, ONE), (0.08, (0.8, 0.8, 0.8)), (0.3, ONE))},
        "pearl": {"scale": frames((0, ONE), (0.08, (0.88, 0.88, 0.88)), (0.3, ONE))},
    }},
    "death": {"loop": "hold", "len": 2.0, "bones": {
        # 横へ傾いて沈み、脚が縮み上がり、はさみが垂れ、真珠がしぼむ
        "body": {"rotation": frames((0, (0, 0, 0)), (0.6, (-8, 0, -16)), (2.0, (-10, 0, -22))),
                 "position": frames((0, (0, 0, 0)), (0.6, (0, -5, 0)), (2.0, (0, -7, 0)))},
        "claw_r": {"rotation": frames((0, (0, 0, 0)), (0.8, (-30, 10, 0)), (2.0, (-38, 12, 0)))},
        "claw_r_pincer": {"rotation": frames((0, (0, 0, 0)), (2.0, (20, 0, 0)))},
        "claw_l": {"rotation": frames((0, (0, 0, 0)), (2.0, (-30, -10, 0)))},
        "head": {"scale": frames((0, ONE), (2.0, (0.7, 0.7, 0.7))), "rotation": frames((0, (0, 0, 0)), (2.0, (-20, 0, 0)))},
        "pearl": {"scale": frames((0, ONE), (0.4, (1.15, 1.15, 1.15)), (2.0, (0.6, 0.6, 0.6)))},
        **{b: {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 0, 45 if b[4] == "r" else -45)))} for b in LEGS},
    }},
}


def from_to(src, dst, t0, t1):
    """src → dst (どちらも {骨: {ch: 値}}) を t0..t1 で。片方に無い ch は REST。"""
    out = {}
    for b in set(src) | set(dst):
        for ch in set(src.get(b, {})) | set(dst.get(b, {})):
            out.setdefault(b, {})[ch] = {t0: src.get(b, {}).get(ch, REST[ch]), t1: dst.get(b, {}).get(ch, REST[ch])}
    return out


def add_keys(spec, more):
    """spec に {骨: {ch: {t: v}}} の鍵を足す。"""
    for b, chans in more.items():
        for ch, keys in chans.items():
            spec.setdefault(b, {}).setdefault(ch, {}).update(keys)
    return spec


# 籠もる・出る・籠もったまま
ANIM["shell_in"] = {"loop": "hold", "len": 0.5, "bones": from_to({}, SHELLED, 0, 0.5)}
_shelled = {b: {ch: [str(x) for x in v] for ch, v in chans.items()} for b, chans in SHELLED.items()}
_shelled["pearl"] = {"scale": [f"1 + {sn(0.1, 180)}"] * 3}
_shelled["shell"] = {"scale": [f"1 + {sn(0.015, 180, 90)}", f"1 + {sn(0.02, 180, 90)}", f"1 + {sn(0.015, 180, 90)}"]}
ANIM["shelled"] = {"loop": True, "len": 2.0, "bones": _shelled}
so = from_to(SHELLED, {}, 0, 0.35)
add_keys(so, {"claw_r": {"rotation": {0.35: (50, 15, 0), 0.6: (0, 0, 0)}, "scale": {0.35: ONE, 0.6: ONE}},
              "claw_r_pincer": {"rotation": {0.35: (30, 0, 0), 0.6: (0, 0, 0)}},
              "body": {"rotation": {0: (0, 0, 0), 0.35: (8, 0, 0), 0.6: (0, 0, 0)}}})
ANIM["shell_out"] = {"loop": False, "len": 0.6, "bones": so}

# 現れる: 籠もった殻が震え (0〜0.4)、体が出て (0.4〜1.0)、はさみを振り上げて反り (1.0〜1.25)、構える (1.5)
em = from_to(SHELLED, {}, 0.4, 1.0)
add_keys(em, {b: {ch: {0: v}} for b, chans in SHELLED.items() for ch, v in chans.items()})
add_keys(em, {"shell": {"rotation": {0: (0, 0, 0), 0.1: (0, 0, 3), 0.2: (0, 0, -3), 0.3: (0, 0, 2), 0.4: (0, 0, 0)}},
              "claw_r": {"rotation": {1.25: (60, 20, 0), 1.5: (8, 0, 0)}},
              "claw_r_pincer": {"rotation": {1.0: (0, 0, 0), 1.25: (35, 0, 0), 1.5: (6, 0, 0)}},
              "body": {"rotation": {0: (0, 0, 0), 1.0: (0, 0, 0), 1.25: (14, 0, 0), 1.5: (0, 0, 0)}},
              "pearl": {"scale": {0: ONE, 1.0: ONE, 1.2: (1.3, 1.3, 1.3), 1.5: ONE}}})
ANIM["emerge"] = {"loop": False, "len": 1.5, "bones": em}

# 吹き上げ: はさみを引き上げ、殻が息を吸って膨らむ (溜め 1.0)。1.0 で殻がしぼんで息を上へ吐き、はさみを突き上げる。
ANIM["updraft"] = {"loop": False, "len": 1.4, "bones": {
    "claw_r": {"rotation": frames((0, (0, 0, 0)), (0.7, (45, -10, 0)), (1.0, (55, -12, 0)), (1.12, (75, -5, 0)), (1.4, (0, 0, 0)))},
    "claw_r_pincer": {"rotation": frames((0, (0, 0, 0)), (1.0, (30, 0, 0)), (1.12, (-5, 0, 0)), (1.4, (0, 0, 0)))},
    "claw_l": {"rotation": frames((0, (0, 0, 0)), (0.7, (40, 10, 0)), (1.0, (48, 12, 0)), (1.12, (65, 5, 0)), (1.4, (0, 0, 0)))},
    "body": {"rotation": frames((0, (0, 0, 0)), (1.0, (8, 0, 0)), (1.12, (2, 0, 0)), (1.4, (0, 0, 0))),
             "position": frames((0, (0, 0, 0)), (1.0, (0, -1.5, 0)), (1.12, (0, 2.5, 0)), (1.4, (0, 0, 0)))},
    "shell": {"scale": frames((0, ONE), (1.0, (1.1, 1.14, 1.1)), (1.12, (0.92, 0.88, 0.92)), (1.4, ONE))},
    "pearl": {"scale": frames((0, ONE), (1.0, (1.15, 1.15, 1.15)), (1.12, (1.3, 1.3, 1.3)), (1.4, ONE))},
    "head": {"rotation": frames((0, (0, 0, 0)), (1.0, (12, 0, 0)), (1.4, (0, 0, 0)))},
}}

# 吹き下ろし: 後ろ脚で立ち上がり、殻を高く (溜め 1.2)、1.2〜1.3 で叩きつけて息を下へ吐く。
ANIM["downdraft"] = {"loop": False, "len": 1.6, "bones": {
    "body": {"rotation": frames((0, (0, 0, 0)), (0.9, (24, 0, 0)), (1.2, (28, 0, 0)), (1.3, (-10, 0, 0)), (1.6, (0, 0, 0))),
             "position": frames((0, (0, 0, 0)), (1.2, (0, 4, 0)), (1.3, (0, -2, 0)), (1.6, (0, 0, 0)))},
    "claw_r": {"rotation": frames((0, (0, 0, 0)), (1.2, (60, 10, 0)), (1.3, (-15, 0, 0)), (1.6, (0, 0, 0)))},
    "claw_r_pincer": {"rotation": frames((0, (0, 0, 0)), (1.2, (30, 0, 0)), (1.3, (0, 0, 0)))},
    "claw_l": {"rotation": frames((0, (0, 0, 0)), (1.2, (50, -10, 0)), (1.3, (-12, 0, 0)), (1.6, (0, 0, 0)))},
    "shell": {"scale": frames((0, ONE), (1.2, (1.08, 1.1, 1.08)), (1.3, (0.94, 0.9, 0.94)), (1.6, ONE))},
    "leg_r1": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 0, 25)), (1.3, (0, 0, -5)), (1.6, (0, 0, 0)))},
    "leg_l1": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 0, -25)), (1.3, (0, 0, 5)), (1.6, (0, 0, 0)))},
    "leg_r2": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 0, 12)), (1.3, (0, 0, -3)), (1.6, (0, 0, 0)))},
    "leg_l2": {"rotation": frames((0, (0, 0, 0)), (1.2, (0, 0, -12)), (1.3, (0, 0, 3)), (1.6, (0, 0, 0)))},
}}

# 突進: 殻に籠もり (0〜0.5) 後ろへ揺すって (0.8)、前へ一回転 (0.8〜1.35)、出る (1.35〜1.6)。
# -360 → 0 は同じ姿なので、1.35 の直後に戻す (次のアニメへ混ぜるとき逆回りしないため)。
ch = from_to({}, SHELLED, 0, 0.5)
add_keys(ch, {b: {c: {1.35: v}} for b, chans in SHELLED.items() for c, v in chans.items() if b != "body"})
add_keys(ch, {b: {c: {1.6: REST[c]}} for b, chans in SHELLED.items() for c in chans if b != "body"})
ch["body"] = {"position": frames((0, (0, 0, 0)), (0.5, (0, -6, 0)), (0.8, (0, -6, 0)), (1.0, (0, 4, 0)), (1.2, (0, 4, 0)),
                                 (1.35, (0, -4, 0)), (1.6, (0, 0, 0))),
              "rotation": frames((0, (0, 0, 0)), (0.5, (0, 0, 0)), (0.8, (14, 0, 0)), (1.35, (-360, 0, 0)), (1.36, (0, 0, 0)),
                                 (1.6, (0, 0, 0)))}
ANIM["charge"] = {"loop": False, "len": 1.6, "bones": ch}

# 真珠の礫: 真珠が膨らんで光り (溜め 1.0)、1.0・1.15・1.3 で真珠を 3 つ吐く (そのたびにうつむいて真珠が跳ねる)。
ANIM["volley"] = {"loop": False, "len": 1.5, "bones": {
    "pearl": {"scale": frames((0, ONE), (0.5, (1.2, 1.2, 1.2)), (0.7, (1.12, 1.12, 1.12)), (1.0, (1.4, 1.4, 1.4)),
                              (1.05, (1.1, 1.1, 1.1)), (1.15, (1.35, 1.35, 1.35)), (1.2, (1.08, 1.08, 1.08)),
                              (1.3, (1.3, 1.3, 1.3)), (1.35, (1.0, 1.0, 1.0)), (1.5, ONE))},
    "body": {"rotation": frames((0, (0, 0, 0)), (1.0, (10, 0, 0)), (1.05, (2, 0, 0)), (1.15, (8, 0, 0)), (1.2, (1, 0, 0)),
                                (1.3, (7, 0, 0)), (1.35, (0, 0, 0)), (1.5, (0, 0, 0)))},
    "claw_r": {"rotation": frames((0, (0, 0, 0)), (1.0, (15, 25, -10)), (1.35, (15, 25, -10)), (1.5, (0, 0, 0)))},
    "claw_l": {"rotation": frames((0, (0, 0, 0)), (1.0, (15, -25, 10)), (1.35, (15, -25, 10)), (1.5, (0, 0, 0)))},
    "shell": {"scale": frames((0, ONE), (1.0, (1.06, 1.08, 1.06)), (1.35, (0.98, 0.97, 0.98)), (1.5, ONE))},
}}


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
    """{骨: {rotation, scale, position}} (ファイルの値)。低ポリの確認の絵 (pk.pose_body) に渡す。"""
    return {b: {ch_: tuple(eval_channel(v, t)) for ch_, v in chans.items()} for b, chans in ANIM[name]["bones"].items()}


def verify(geo, anim):
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    for an, a in anim["animations"].items():
        for b in a["bones"]:
            assert b in names, (an, b)
    req = ["idle", "walk", "hurt", "death", "emerge", "updraft", "downdraft", "charge", "volley", "shell_in", "shelled",
           "shell_out"]
    for r in req:
        assert f"animation.{NAME}.{r}" in anim["animations"], r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="箱の姿を docs/review/pearl_keeper/cube_preview.png に描く")
    a = ap.parse_args()
    geo, img, glow = build_geo()
    anim = anim_json()
    verify(geo, anim)
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/entity" / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}.png")
    assert glow[..., 3].any()
    Image.fromarray(glow, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}_glowmask.png")
    (ASSETS / "animations/entity" / f"{NAME}.animation.json").write_text(json.dumps(anim, indent=1, ensure_ascii=False) + "\n",
                                                                        encoding="utf-8")
    n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{NAME}: 箱 {n}、テクスチャ {img.shape[1]}x{img.shape[0]}、アニメ {len(anim['animations'])}")
    for k, v in ANIM.items():
        print(f"  animation.{NAME}.{k}: {v['len']} s = {round(v['len'] * 20)} tick, loop={v['loop']}")
    if a.preview:
        sys.path.insert(0, str(ROOT / "tools/rough"))
        import voxel as vx
        faces = vx.load_geo(geo, img, glow)
        out = ROOT / "docs/review/pearl_keeper"
        out.mkdir(parents=True, exist_ok=True)
        ims = [vx.render(faces, yaw=y, pitch=18, size=(360, 360)) for y in (215, 35, 270)]
        W = Image.new("RGB", (360 * 3, 360))
        for i, im in enumerate(ims):
            W.paste(im.convert("RGB"), (360 * i, 0))
        W.save(out / "cube_preview.png")


if __name__ == "__main__":
    main()
