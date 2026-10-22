"""雷翼の雛 (young_thunder_wing。28 の「巨神の子」: 雷翼が分けた鼓動の子、パーティ 6 人を背に乗せて稜線を低く飛ぶ) のゲームの素材を書く。

使い方: リポジトリ直下で  python tools/gen/world/gen_young_thunder_wing_assets.py [--preview]
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/young_thunder_wing.geo.json            箱の geo (低ポリが切れているときの姿。骨は低ポリのメッシュと同じ 14 本)
  textures/entity/young_thunder_wing.png            箱のテクスチャ (1 ブロック 32 画素。宣言の大きさの 2 倍の絵)
  textures/entity/young_thunder_wing_glowmask.png   光る画素 (歌のかけら・目)
  animations/entity/young_thunder_wing.animation.json
  tools/sources/young/young_thunder_wing_notes.json  Java 側への申し送り (鞍の高さ・当たり判定・骨・アニメの長さ)
低ポリのメッシュは tools/install/install_lowpoly_young_thunder_wing.py (案は tools/rough/lowpoly/young_thunder_wing/opt_{a,b,c}.py)。

骨 (作図は前 = -Z。geo は x と z を反転し、root を Y 軸で 180 度回す。真珠守りと同じ決まり。最終の前は -Z):
  root > body > saddle (鞍。6 人の座る平らな面。上面 = SEAT_Y)
              > neck > head > mote (歌のかけら。光る冠の珠)
              > wing_l > wing_l_tip, wing_r > wing_r_tip (作図の +x = 右)
              > tail, leg_l, leg_r
              > halo (C 案の雷の輪。A・B 案は空)
アニメの角度はファイルに書く値そのまま。wing_r の z が正・wing_l の z が負 = 翼を上げる。
座席 (ブロック、作図空間 = 前 -Z): x = ±1.0, z = -2.0 / 0 / +2.0, 高さ SEAT_Y = 2.25。
鞍の動き: idle・move では鞍の上面の点が 0.15 ブロック (2.4 単位) より動かない (verify で確かめる)。

アニメの長さ (秒): idle 3.0 (loop)、move 1.2 (loop、羽ばたき)、appear 1.6 (歌のかけらから育つ)、dissolve 1.4 (hold、縮んで溶ける)
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
NAME = "young_thunder_wing"
SEAT_Y = 36.0            # 単位 (1/16 ブロック) = 2.25 ブロック
SEATS = [(sx * 16.0, -sz * 16.0) for sz in (2.0, 0.0, -2.0) for sx in (-1.0, 1.0)]   # (x, z) 作図空間 (前 -Z)
SADDLE_REGION = (25.6, 51.2)  # |x|, |z| (単位) の範囲で鞍の上面が SEAT_Y

# 骨 (作図空間 = 前が -Z)。低ポリのラフ (tools/rough/lowpoly/young_thunder_wing/yt.py) もここを読む。
SKELETON_DRAW = {
    "root": (None, (0.0, 0.0, 0.0)),
    "body": ("root", (0.0, 36.0, 0.0)),
    "saddle": ("body", (0.0, 36.0, 0.0)),
    "neck": ("body", (0.0, 32.0, -52.0)),
    "head": ("neck", (0.0, 44.0, -72.0)),
    "mote": ("head", (0.0, 58.0, -74.0)),
    "wing_l": ("body", (-28.0, 32.0, -8.0)),
    "wing_l_tip": ("wing_l", (-84.0, 33.0, -6.0)),
    "wing_r": ("body", (28.0, 32.0, -8.0)),
    "wing_r_tip": ("wing_r", (84.0, 33.0, -6.0)),
    "tail": ("body", (0.0, 30.0, 58.0)),
    "leg_l": ("body", (-10.0, 14.0, 8.0)),
    "leg_r": ("body", (10.0, 14.0, 8.0)),
    "halo": ("body", (0.0, 10.0, 0.0)),
}

# ====================================================================== 箱 (案 A の箱の姿)。重ならないこと (触れるのはよい)。
BOXES = [
    ("body", "down", (0.0, 22.0, 2.0), (52, 20, 100)),
    ("saddle", "pad", (0.0, 34.0, 0.0), (60, 4, 112)),
    ("neck", "down", (0.0, 24.0, -56.0), (20, 16, 16)),
    ("head", "down", (0.0, 44.0, -74.0), (28, 24, 24)),
    ("head", "beak", (0.0, 42.0, -92.0), (10, 8, 12)),
    ("head", "eye", (15.0, 46.0, -80.0), (2, 5, 5)),
    ("head", "eye", (-15.0, 46.0, -80.0), (2, 5, 5)),
    ("head", "copper", (0.0, 58.0, -66.0), (4, 4, 6)),
    ("mote", "mote", (0.0, 60.0, -74.0), (8, 8, 8)),
    ("tail", "prim", (0.0, 28.0, 66.0), (28, 3, 28)),
]
for s, side in ((1, "r"), (-1, "l")):
    BOXES += [
        (f"wing_{side}", "cov", (s * 57.0, 29.5, -6.0), (60, 3, 40)),
        (f"wing_{side}_tip", "prim", (s * 111.0, 29.5, 0.0), (48, 2, 36)),
        (f"leg_{side}", "leg", (s * 10.0, 8.0, 8.0), (5, 8, 5)),
        (f"leg_{side}", "talon", (s * 10.0, 2.0, 6.0), (7, 4, 9)),
    ]

PAL = {
    "down": [(78, 90, 120), (96, 110, 140), (120, 132, 162), (156, 168, 196)],
    "pad": [(160, 166, 182), (190, 196, 210), (214, 218, 228)],
    "beak": [(132, 116, 80), (164, 146, 100), (196, 178, 124)],
    "eye": [(10, 10, 16), (20, 20, 28), (32, 32, 42)],
    "copper": [(128, 70, 38), (172, 100, 52), (212, 136, 76)],
    "mote": [(255, 232, 150), (255, 248, 210), (255, 255, 255)],
    "cov": [(50, 58, 84), (66, 76, 104), (84, 96, 126), (104, 118, 148)],
    "prim": [(26, 30, 46), (36, 42, 62), (48, 56, 80), (62, 72, 100)],
    "leg": [(122, 108, 72), (148, 132, 88), (176, 160, 106)],
    "talon": [(24, 24, 30), (38, 38, 46), (56, 56, 66)],
}
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
    rng = np.random.default_rng(5151)
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
            if mat in ("cov", "prim") and face == "up":
                # 翼の上の稲妻の筋 (ジグザグ。光る)
                zig = (np.abs(((xx // PX) % 12) - 6) // 2)
                vein = np.abs(yy // PX - (H // PX) // 2 - zig + 1) < 1
                col = np.where(vein[..., None], np.array([220, 240, 255.0]), col)
                g[vein] = (255, 255, 255, 255)
            if mat == "pad" and face == "up":
                # 座る 6 か所の座布団 (少し暗い楕円)
                for sx, sz in SEATS:
                    cx, cz = (sx - (c[0] - s[0] / 2)) * PX, (sz - (c[2] - s[2] / 2)) * PX
                    m = ((xx - cx) / (5 * PX)) ** 2 + ((yy - cz) / (6 * PX)) ** 2 < 1
                    col = np.where(m[..., None], np.array(PAL["down"][2], float), col)
            if mat == "eye" and face in ("north", "west", "east"):
                pup = (np.abs(xx - W / 2 + 0.5) < PX * 0.8) & (np.abs(yy - H / 2 + 0.5) < PX * 1.2)
                col = np.where(pup[..., None], np.array([255, 220, 120.0]), col)
                g[pup] = (255, 255, 255, 255)
            y0, x0 = fv * PX, fu * PX
            img[y0:y0 + H, x0:x0 + W, :3] = np.clip(col, 0, 255).astype(np.uint8)
            img[y0:y0 + H, x0:x0 + W, 3] = 255
            if mat == "mote":
                glow[y0:y0 + H, x0:x0 + W] = (255, 255, 255, 255)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 18, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2.0, 0]},
        "bones": [bones[b] for b in SKELETON_DRAW]}]}
    return geo, img, glow


# ====================================================================== アニメ (ファイルの値そのまま)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


def neg(e):
    return f"-({e})"


def V3(s):
    return (s, s, s)


ONE = (1, 1, 1)


def frames(*pairs):
    return {t: v for t, v in pairs}


def wings(rz, tz, ry=None):
    """右翼の z の式から左右 4 骨の回転を作る (左は符号を反転)。"""
    out = {"wing_r": {"rotation": ["0", ry or "0", rz]}, "wing_l": {"rotation": ["0", neg(ry) if ry else "0", neg(rz)]},
           "wing_r_tip": {"rotation": ["0", "0", tz]}, "wing_l_tip": {"rotation": ["0", "0", neg(tz)]}}
    return out


def wings_k(rz, tz):
    """右翼の z の鍵 {t: 角度} から左右 4 骨の鍵を作る。"""
    return {"wing_r": {"rotation": {t: (0, 0, a) for t, a in rz.items()}},
            "wing_l": {"rotation": {t: (0, 0, -a) for t, a in rz.items()}},
            "wing_r_tip": {"rotation": {t: (0, 0, a) for t, a in tz.items()}},
            "wing_l_tip": {"rotation": {t: (0, 0, -a) for t, a in tz.items()}}}


ANIM = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        # 低く浮いたまま、翼をゆっくり上下させて風に乗る。首を巡らせ、冠の歌のかけらが脈打つ。
        **wings(f"6 + {sn(5, 120)}", sn(5, 120, -40)),
        "body": {"position": ["0", sn(0.6, 120), "0"], "rotation": [sn(0.5, 120, 90), "0", "0"]},
        "neck": {"rotation": [sn(3, 120, 60), sn(8, 120, 30), "0"]},
        "head": {"rotation": [sn(4, 240), sn(6, 120, 200), sn(3, 240, 90)]},
        "tail": {"rotation": [sn(3, 120, 60), sn(4, 240), "0"]},
        "mote": {"scale": [f"1 + {sn(0.18, 240)}"] * 3},
        "halo": {"rotation": ["0", "query.anim_time * 120", "0"], "scale": [f"1 + {sn(0.03, 240)}"] * 3},
    }},
    "move": {"loop": True, "len": 1.2, "bones": {
        # 羽ばたき (1.2 秒に 1 回)。翼の先は少し遅れてしなる。体は鞍が揺れないほど小さく上下する。
        **wings(f"8 + {sn(32, 300)}", sn(18, 300, -50), sn(4, 300, 90)),
        "body": {"position": ["0", sn(-0.9, 300), "0"], "rotation": [f"-0.8 + {sn(0.4, 300, 30)}", "0", "0"]},
        "neck": {"rotation": [f"-6 + {sn(3, 300, 180)}", "0", "0"]},
        "head": {"rotation": [f"6 + {sn(3, 300, 240)}", "0", "0"]},
        "tail": {"rotation": [sn(5, 300, 120), "0", "0"]},
        "leg_l": {"rotation": ["-40", "0", "0"]},
        "leg_r": {"rotation": ["-40", "0", "0"]},
        "mote": {"scale": [f"1 + {sn(0.12, 600)}"] * 3},
        "halo": {"rotation": ["0", "query.anim_time * 600", "0"]},
    }},
    "appear": {"loop": False, "len": 1.6, "bones": {
        # 持ち主の足もとに歌のかけら (光る珠) が灯り、そこから雛が育つ。翼を上げたまま育ち、最後に広げる。
        "root": {"scale": frames((0, V3(0.04)), (0.45, V3(0.2)), (1.0, V3(0.86)), (1.25, V3(1.06)), (1.6, ONE))},
        "mote": {"scale": frames((0, V3(7)), (0.45, V3(4.5)), (1.0, V3(1.6)), (1.3, V3(1.3)), (1.6, ONE))},
        **wings_k({0: 75, 0.9: 75, 1.3: -6, 1.6: 0}, {0: 50, 1.0: 50, 1.4: -4, 1.6: 0}),
        "head": {"rotation": frames((0, (22, 0, 0)), (1.1, (22, 0, 0)), (1.4, (-10, 0, 0)), (1.6, (0, 0, 0)))},
        "tail": {"rotation": frames((0, (-15, 0, 0)), (1.0, (-15, 0, 0)), (1.6, (0, 0, 0)))},
        "halo": {"scale": frames((0, V3(0.2)), (0.6, V3(0.2)), (1.2, V3(1.12)), (1.6, ONE)),
                 "rotation": frames((0, (0, 0, 0)), (1.6, (0, 360, 0)))},
    }},
    "dissolve": {"loop": "hold", "len": 1.4, "bones": {
        # 翼をたたみ、縮みながら冠の歌のかけらだけが膨らみ、最後は光の粒になって消える (root の大きさ ~0)。
        "root": {"scale": frames((0, ONE), (0.35, V3(1.04)), (1.0, V3(0.38)), (1.4, V3(0.01)))},
        "mote": {"scale": frames((0, ONE), (0.6, V3(2.4)), (1.0, V3(4.0)), (1.4, V3(6.0)))},
        **wings_k({0: 0, 0.6: 75, 1.4: 75}, {0: 0, 0.6: 50, 1.4: 50}),
        "head": {"rotation": frames((0, (0, 0, 0)), (0.6, (22, 0, 0)), (1.4, (22, 0, 0)))},
        "halo": {"scale": frames((0, ONE), (0.7, V3(1.3)), (1.4, V3(0.3))),
                 "rotation": frames((0, (0, 0, 0)), (1.4, (0, 540, 0)))},
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
    """{骨: {rotation, scale, position}} (ファイルの値)。低ポリの確認の絵 (yt.pose_body) に渡す。"""
    return {b: {ch_: tuple(eval_channel(v, t)) for ch_, v in chans.items()} for b, chans in ANIM[name]["bones"].items()}


# ---- 鞍が動かないことの確認 (GeckoLib の順: pivot へ → 拡大 → 回転 → 戻す → 位置。root の 180 度は x,z の反転で相殺)

def _rot_geo(rx, ry, rz):
    def R(axis, a):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        if axis == 0:
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == 1:
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return R(2, -rz) @ R(1, -ry) @ R(0, -rx)


def pose_point_draw(p, bone, pose):
    """作図空間の点 p (骨 bone) を pose の姿勢へ (作図空間で返す)。root の大きさも含める。"""
    q = np.array([-p[0], p[1], -p[2]], float)
    while bone is not None:
        parent, pv = SKELETON_DRAW[bone]
        pv = np.array(flip(pv), float)
        st = pose.get(bone, {})
        d = q - pv
        if "scale" in st:
            d = d * np.array(st["scale"], float)
        if "rotation" in st and bone != "root":
            d = _rot_geo(*st["rotation"]) @ d
        q = d + pv
        if "position" in st:
            ps = np.array(st["position"], float)
            q = q + np.array([-ps[0], ps[1], ps[2]])
        bone = parent
    return np.array([-q[0], q[1], -q[2]])


def saddle_drift():
    """idle・move の間の鞍の上面の四隅と座席の最大の動き (単位)。"""
    pts = [np.array([x, SEAT_Y, z]) for x in (-SADDLE_REGION[0], SADDLE_REGION[0]) for z in (-SADDLE_REGION[1], SADDLE_REGION[1])]
    pts += [np.array([x, SEAT_Y, z]) for x, z in SEATS]
    out = {}
    for an in ("idle", "move"):
        m = 0.0
        for i in range(61):
            t = ANIM[an]["len"] * i / 60
            pose = pose_at(an, t)
            for p in pts:
                m = max(m, float(np.linalg.norm(pose_point_draw(p, "saddle", pose) - p)))
        out[an] = m
    return out


def verify(geo, anim):
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    for an, a in anim["animations"].items():
        for b in a["bones"]:
            assert b in names, (an, b)
    for r in ("idle", "move", "appear", "dissolve"):
        assert f"animation.{NAME}.{r}" in anim["animations"], r
    dr = saddle_drift()
    assert all(v <= 2.4 for v in dr.values()), dr
    return dr


def notes():
    return {"model": NAME, "seat_y": SEAT_Y / 16.0, "seat_y_units": SEAT_Y,
            "seats_blocks_front_minus_z": [[x / 16.0, z / 16.0] for x, z in SEATS],
            "saddle_flat_region_blocks": {"x": [-1.6, 1.6], "z": [-3.2, 3.2]},
            "hitbox": [4.6, 3.4], "bones": list(SKELETON_DRAW),
            "anims": {k: v["len"] for k, v in ANIM.items()},
            "loops": {k: ("hold_on_last_frame" if v["loop"] == "hold" else bool(v["loop"])) for k, v in ANIM.items()},
            "front": "-Z in final geo space (drawn front -Z, flipped x/z, root rest rotation [0,180,0] cancels it)",
            "glow": "textures/entity/young_thunder_wing_glowmask.png (cube) / textures/lowpoly/young_thunder_wing_glowmask.png (low poly)",
            "note": "halo bone is empty in options A/B (C's lightning ring). root scale is animated in appear/dissolve."}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    a = ap.parse_args()
    geo, img, glow = build_geo()
    anim = anim_json()
    dr = verify(geo, anim)
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/entity" / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}.png")
    assert glow[..., 3].any()
    Image.fromarray(glow, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}_glowmask.png")
    (ASSETS / "animations/entity" / f"{NAME}.animation.json").write_text(json.dumps(anim, indent=1, ensure_ascii=False) + "\n",
                                                                        encoding="utf-8")
    nd = ROOT / "tools/sources/young"
    nd.mkdir(parents=True, exist_ok=True)
    (nd / f"{NAME}_notes.json").write_text(json.dumps(notes(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{NAME}: 箱 {n}、テクスチャ {img.shape[1]}x{img.shape[0]}、アニメ {len(anim['animations'])}、鞍の最大の動き {dr}")
    for k, v in ANIM.items():
        print(f"  animation.{NAME}.{k}: {v['len']} s = {round(v['len'] * 20)} tick, loop={v['loop']}")
    if a.preview:
        sys.path.insert(0, str(ROOT / "tools/rough"))
        import voxel as vx
        faces = vx.load_geo(geo, img, glow)
        ims = [vx.render(faces, yaw=y, pitch=18, size=(480, 360)) for y in (215, 35, 270)]
        W = Image.new("RGB", (480 * 3, 360))
        for i, im in enumerate(ims):
            W.paste(im.convert("RGB"), (480 * i, 0))
        W.save(ROOT / "tools/rough/lowpoly/young_thunder_wing/out/cube_preview.png")


if __name__ == "__main__":
    main()
