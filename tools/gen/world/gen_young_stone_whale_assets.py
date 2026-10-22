"""岩鯨の仔 (young_stone_whale。巨神の「子」で一行 6 人を背に乗せて空を泳ぐ。28 章) のゲームの素材を書く。

使い方: リポジトリ直下で  python tools/gen/world/gen_young_stone_whale_assets.py
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/young_stone_whale.geo.json            箱の geo (低ポリが切れているときの姿。骨は低ポリのメッシュと同じ 9 本)
  textures/entity/young_stone_whale.png            箱のテクスチャ (1 ブロック 32 画素。宣言の大きさの 2 倍の絵)
  textures/entity/young_stone_whale_glowmask.png   光る画素 (歌のかけら・瞳)
  animations/entity/young_stone_whale.animation.json
  tools/sources/young/young_stone_whale_notes.json  Java 側への覚え (鞍の高さ・当たり判定・骨・アニメの長さ)
低ポリのメッシュは tools/install/install_lowpoly_young_stone_whale.py (案は tools/rough/lowpoly/young_stone_whale/opt_{a,b,c}.py)。

骨 (作図は前 = -Z。geo は x と z を反転し、root を Y 軸で 180 度回す。真珠守りと同じ決まり。最後の geo の前は -Z):
  root > body > saddle (6 人の鞍。アニメで 0.15 ブロック以上動かさない)
              > mote (額の歌のかけら。光る)
              > fin_r, fin_l (胸びれ。作図の +x = ゲームの右)
              > tail > fluke (尾と尾びれ。上下にあおぐ)
       > cloud (腹の下の小さな雲)
鞍の上面 SEAT_Y = 58 単位 = 3.625 ブロック (3 案とも同じ)。座席は x = ±1.0、z = -2 / 0 / +2 ブロック。
アニメの長さ: idle 3.0 (loop)、move 1.2 (loop)、appear 1.6、dissolve 1.4 (hold)。
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
NAME = "young_stone_whale"
SEAT_Y = 58.0                    # 単位 (1/16 ブロック)
SEAT_RECT = (25.6, 51.2)         # 鞍の上面が平らな範囲 (半幅 x, 半長 z。単位)
SEATS = [(sx * 16.0, SEAT_Y, sz * 16.0) for sz in (-2, 0, 2) for sx in (-1, 1)]
HITBOX = [4.6, 3.4]

# 骨 (作図空間 = 前が -Z)。低ポリのラフ (tools/rough/lowpoly/young_stone_whale/ysw.py) もここを読む。
SKELETON_DRAW = {
    "root": (None, (0.0, 0.0, 0.0)),
    "body": ("root", (0.0, 40.0, 0.0)),
    "saddle": ("body", (0.0, SEAT_Y, 0.0)),
    "mote": ("body", (0.0, 53.0, -68.0)),
    "fin_r": ("body", (26.0, 22.0, -30.0)),
    "fin_l": ("body", (-26.0, 22.0, -30.0)),
    "tail": ("body", (0.0, 36.0, 62.0)),
    "fluke": ("tail", (0.0, 38.0, 96.0)),
    "cloud": ("root", (0.0, 3.0, 4.0)),
}

# ====================================================================== 箱 (案 A「骨の輿」の箱の姿)
# (骨, 材, 中心 (作図空間), 大きさ)。重ならないこと (触れるのはよい)。
BOXES = [
    ("body", "stone", (0.0, 34.0, -10.0), (60, 40, 120)),
    ("body", "stone", (0.0, 33.0, -77.0), (44, 30, 14)),
    ("body", "stone", (0.0, 35.0, 60.0), (40, 30, 20)),
    ("body", "eye", (31.0, 38.0, -60.0), (2, 6, 6)),
    ("body", "eye", (-31.0, 38.0, -60.0), (2, 6, 6)),
    ("body", "chain", (-31.0, 42.0, 41.0), (1, 20, 1)),
    ("body", "stone2", (-32.5, 27.0, 41.0), (3, 10, 6)),
    ("mote", "mote", (0.0, 56.0, -68.0), (4, 4, 4)),
    ("saddle", "deck", (0.0, 56.0, 0.0), (54, 4, 108)),
    ("saddle", "bone", (26.0, 61.0, 0.0), (2, 6, 108)),
    ("saddle", "bone", (-26.0, 61.0, 0.0), (2, 6, 108)),
    ("saddle", "bone", (0.0, 62.0, -52.0), (50, 8, 2)),
    ("saddle", "bone", (0.0, 61.0, 52.0), (50, 6, 2)),
    ("fin_r", "stone2", (40.0, 22.0, -30.0), (20, 3, 16)),
    ("fin_l", "stone2", (-40.0, 22.0, -30.0), (20, 3, 16)),
    ("tail", "stone", (0.0, 36.0, 80.0), (16, 14, 20)),
    ("fluke", "stone2", (0.0, 37.0, 96.0), (60, 4, 12)),
    ("cloud", "cloud", (0.0, 6.0, 0.0), (36, 8, 40)),
    ("cloud", "cloud", (6.0, 5.0, 28.0), (20, 6, 16)),
]

PAL = {
    "stone": [(52, 56, 68), (74, 80, 94), (100, 106, 120), (122, 126, 134)],
    "stone2": [(44, 48, 58), (64, 70, 84), (90, 96, 110)],
    "belly": [(104, 92, 80), (122, 110, 98), (150, 138, 120)],
    "deck": [(150, 140, 120), (186, 176, 152), (214, 204, 180)],
    "bone": [(176, 168, 150), (214, 206, 186), (240, 232, 214)],
    "chain": [(40, 44, 52), (70, 76, 86), (100, 106, 116)],
    "eye": [(8, 10, 16), (18, 20, 28), (32, 34, 44)],
    "mote": [(100, 226, 230), (170, 246, 244), (230, 255, 252)],
    "cloud": [(196, 206, 216), (222, 230, 238), (244, 248, 252)],
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
    rng = np.random.default_rng(5150)
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
        for face, (fu, fv, fw, fh) in _uv_rects(u, v, w, h, d).items():
            if fw <= 0 or fh <= 0:
                continue
            m = "belly" if (mat == "stone" and face == "down") else mat
            ramp = np.array(PAL[m], float)
            H, W = fh * PX, fw * PX
            yy, xx = np.mgrid[0:H, 0:W]
            lit = FACE_LIGHT[face]
            t = 0.25 + 0.6 * lit + 0.12 * (rng.random((H, W)) - 0.5) + 0.1 * (1 - yy / max(H - 1, 1)) * (face != "up")
            edge = (xx == 0) | (yy == 0) | (xx == W - 1) | (yy == H - 1)
            t = t - 0.18 * edge
            idx = np.clip((t * len(ramp)).astype(int), 0, len(ramp) - 1)
            col = ramp[idx]
            if mat == "stone" and face not in ("up", "down"):
                # 下半分は腹の砂色 (箱の横の面の下 40%)
                low = yy > H * 0.6
                bramp = np.array(PAL["belly"], float)
                col = np.where(low[..., None], bramp[np.clip(idx, 0, 2)], col)
            if mat == "deck" and face == "up":
                slat = (xx // PX) % 8 == 0
                col = np.where(slat[..., None], ramp[0], col)
            if mat == "eye" and face in ("north", "west", "east", "south"):
                pup = (np.abs(xx - W / 2 + 0.5) < PX * 0.9) & (np.abs(yy - H / 2 + 0.5) < PX * 0.9)
                col = np.where(pup[..., None], np.array([150, 240, 236.0]), col)
                g = glow[fv * PX:fv * PX + H, fu * PX:fu * PX + W]
                g[pup] = (255, 255, 255, 255)
            y0, x0 = fv * PX, fu * PX
            img[y0:y0 + H, x0:x0 + W, :3] = np.clip(col, 0, 255).astype(np.uint8)
            img[y0:y0 + H, x0:x0 + W, 3] = 255
            if mat == "mote":
                glow[y0:y0 + H, x0:x0 + W] = (255, 255, 255, 255)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 14, "visible_bounds_height": 7, "visible_bounds_offset": [0, 2.5, 0]},
        "bones": [bones[b] for b in SKELETON_DRAW]}]}
    return geo, img, glow


# ====================================================================== アニメ (ファイルの値そのまま)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


ONE = (1, 1, 1)


def frames(*pairs):
    return {t: v for t, v in pairs}


def S(k):
    return (k, k, k)


ANIM = {
    # 浮かんで待つ: ゆっくり上下、ひれと尾が小さくあおぎ、額の歌のかけらが脈打つ。雲がゆらぐ。
    "idle": {"loop": True, "len": 3.0, "bones": {
        "body": {"position": ["0", sn(0.6, 120), "0"], "rotation": [sn(0.6, 120, 60), "0", sn(0.5, 120, 150)]},
        "tail": {"rotation": [sn(4, 120, -40), "0", "0"]},
        "fluke": {"rotation": [sn(7, 120, -90), "0", "0"]},
        "fin_r": {"rotation": [sn(3, 120, 20), "0", sn(6, 120)]},
        "fin_l": {"rotation": [sn(3, 120, 20), "0", f"-({sn(6, 120)})"]},
        "mote": {"scale": [f"1 + {sn(0.15, 240)}"] * 3},
        "cloud": {"rotation": ["0", sn(8, 120), "0"],
                  "scale": [f"1 + {sn(0.06, 240)}", f"1 + {sn(0.1, 240, 90)}", f"1 + {sn(0.06, 240, 180)}"]},
    }},
    # 空を泳ぐ: 尾と尾びれが大きく上下にあおぎ (波が尾の先へ遅れて伝わる)、胸びれがかく。鞍はほとんど動かない。
    "move": {"loop": True, "len": 1.2, "bones": {
        "body": {"position": ["0", sn(0.8, 300), "0"], "rotation": [sn(1.2, 300, 90), "0", "0"]},
        "tail": {"rotation": [sn(12, 300, -40), "0", "0"]},
        "fluke": {"rotation": [sn(18, 300, -110), "0", "0"]},
        "fin_r": {"rotation": [sn(8, 300, 0), sn(6, 300, 60), f"4 + {sn(16, 300, 30)}"]},
        "fin_l": {"rotation": [sn(8, 300, 0), f"-({sn(6, 300, 60)})", f"-(4 + {sn(16, 300, 30)})"]},
        "mote": {"scale": [f"1.1 + {sn(0.12, 600)}"] * 3},
        "cloud": {"position": ["0", sn(1.0, 300, 45), "2"], "scale": ["0.9", "0.8", "1.35"]},
    }},
    # 現れる: 歌のかけら (額の光) が先に大きく灯り、体が足もとで小さな粒から育って (0〜1.2)、一度ふくらんで落ち着く (1.6)。
    "appear": {"loop": False, "len": 1.6, "bones": {
        "root": {"scale": frames((0, S(0.02)), (0.3, S(0.12)), (0.9, S(0.75)), (1.2, S(1.06)), (1.4, S(0.98)), (1.6, ONE)),
                 "position": frames((0, (0, 0, 0)), (1.6, (0, 0, 0)))},
        "mote": {"scale": frames((0, S(8.0)), (0.4, S(5.0)), (0.9, S(2.0)), (1.25, S(1.4)), (1.6, ONE))},
        "body": {"rotation": frames((0, (0, 0, 0)), (0.9, (-6, 0, 0)), (1.3, (2, 0, 0)), (1.6, (0, 0, 0)))},
        "fin_r": {"rotation": frames((0, (0, 0, -30)), (1.1, (0, 0, 20)), (1.6, (0, 0, 0)))},
        "fin_l": {"rotation": frames((0, (0, 0, 30)), (1.1, (0, 0, -20)), (1.6, (0, 0, 0)))},
        "tail": {"rotation": frames((0, (20, 0, 0)), (1.0, (-10, 0, 0)), (1.6, (0, 0, 0)))},
        "fluke": {"rotation": frames((0, (30, 0, 0)), (1.1, (-14, 0, 0)), (1.6, (0, 0, 0)))},
        "cloud": {"scale": frames((0, S(0.0)), (0.6, S(0.4)), (1.2, S(1.15)), (1.6, ONE))},
    }},
    # 歌に溶ける: ひれをたたみ、身を丸めて縮み、額の光だけが残って大きく灯り、最後に全部が点になる (保持)。
    "dissolve": {"loop": "hold", "len": 1.4, "bones": {
        "root": {"scale": frames((0, ONE), (0.2, S(1.04)), (0.8, S(0.6)), (1.2, S(0.12)), (1.4, S(0.01)))},
        "mote": {"scale": frames((0, ONE), (0.5, S(1.8)), (1.0, S(4.0)), (1.4, S(6.0)))},
        "body": {"rotation": frames((0, (0, 0, 0)), (0.6, (8, 0, 0)), (1.4, (14, 0, 0)))},
        "fin_r": {"rotation": frames((0, (0, 0, 0)), (0.6, (0, 0, -35)), (1.4, (0, 0, -40)))},
        "fin_l": {"rotation": frames((0, (0, 0, 0)), (0.6, (0, 0, 35)), (1.4, (0, 0, 40)))},
        "tail": {"rotation": frames((0, (0, 0, 0)), (0.8, (-25, 0, 0)), (1.4, (-30, 0, 0)))},
        "fluke": {"rotation": frames((0, (0, 0, 0)), (0.8, (-30, 0, 0)), (1.4, (-35, 0, 0)))},
        "cloud": {"scale": frames((0, ONE), (0.7, S(1.3)), (1.4, S(0.0)))},
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
    """{骨: {rotation, scale, position}} (ファイルの値)。低ポリの確認の絵 (ysw.pose_body) に渡す。"""
    return {b: {ch_: tuple(eval_channel(v, t)) for ch_, v in chans.items()} for b, chans in ANIM[name]["bones"].items()}


# ---------------------------------------------------------------------- 姿勢 (geo 空間。鞍の揺れの確かめと絵に使う)

def _rx(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rot_geo(rx, ry, rz):
    """GeckoLib の骨の回転 (度): 3 軸とも符号を反転した Z→Y→X。"""
    return _rz(-rz) @ _ry(-ry) @ _rx(-rx)


GEO_BONES = {k: (p, np.array(flip(v), float)) for k, (p, v) in SKELETON_DRAW.items()}


def pose_point_geo(p, bone, pose):
    """geo 空間の点 p (骨 bone の休めの姿勢) を pose のときの geo 空間へ。root の休めの 180 度は含めないが、
    root のアニメ (拡大・位置) は含める。GeckoLib の順: pivot へ寄せ → 拡大 → 回転 → 戻す → 位置。"""
    p = np.asarray(p, float)
    while bone is not None:
        parent, pv_ = GEO_BONES[bone]
        st = pose.get(bone)
        if st is not None:
            if not isinstance(st, dict):
                st = {"rotation": st}
            q = p - pv_
            if "scale" in st:
                q = q * np.array(st["scale"], float)
            if "rotation" in st:
                q = rot_geo(*st["rotation"]) @ q
            p = q + pv_
            if "position" in st:
                ps = np.array(st["position"], float)
                p = p + np.array([-ps[0], ps[1], ps[2]])
        bone = parent
    return p


def saddle_motion():
    """idle と move で鞍の上面の四隅と座席が休めの位置から一番離れる距離 (単位)。"""
    hx, hz = SEAT_RECT
    pts = [(x, SEAT_Y, z) for x in (-hx, 0, hx) for z in (-hz, 0, hz)]
    out = {}
    for name in ("idle", "move"):
        m = 0.0
        for t in np.linspace(0, ANIM[name]["len"], 61):
            pose = pose_at(name, float(t))
            for p in pts:
                g = np.array(flip(p), float)
                m = max(m, float(np.linalg.norm(pose_point_geo(g, "saddle", pose) - g)))
        out[name] = m
    return out


def verify(geo, anim):
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    assert geo["minecraft:geometry"][0]["bones"][0]["name"] == "root"
    for an, a in anim["animations"].items():
        for b in a["bones"]:
            assert b in names, (an, b)
    for r in ("idle", "move", "appear", "dissolve"):
        assert f"animation.{NAME}.{r}" in anim["animations"], r
    sm = saddle_motion()
    for k, v in sm.items():
        assert v <= 2.4, ("saddle moves too much", k, v)
    return sm


def notes(sm):
    return {"model": NAME, "seat_y": SEAT_Y / 16.0, "seat_y_units": SEAT_Y,
            "seats_blocks": [[x / 16.0, z / 16.0] for x, _, z in SEATS],
            "hitbox": HITBOX, "front": "-Z (final geo space; root carries the pipeline's [0,180,0] with x/z-flipped vertices)",
            "bones": list(SKELETON_DRAW), "anims": {k: v["len"] for k, v in ANIM.items()},
            "loops": {k: ("hold_on_last_frame" if v["loop"] == "hold" else bool(v["loop"])) for k, v in ANIM.items()},
            "saddle_max_motion_blocks": {k: round(v / 16.0, 3) for k, v in sm.items()},
            "visual_extent_blocks": {"length_z": 11.9, "width_x_body": 4.4, "width_x_fins": 7.0, "height": 4.6}}


def main():
    geo, img, glow = build_geo()
    anim = anim_json()
    sm = verify(geo, anim)
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/entity" / f"{NAME}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}.png")
    assert glow[..., 3].any()
    Image.fromarray(glow, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}_glowmask.png")
    (ASSETS / "animations/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/entity" / f"{NAME}.animation.json").write_text(json.dumps(anim, indent=1, ensure_ascii=False) + "\n",
                                                                        encoding="utf-8")
    nd = ROOT / "tools/sources/young"
    nd.mkdir(parents=True, exist_ok=True)
    (nd / f"{NAME}_notes.json").write_text(json.dumps(notes(sm), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{NAME}: 箱 {n}、テクスチャ {img.shape[1]}x{img.shape[0]}、アニメ {len(anim['animations'])}、"
          f"鞍の最大の揺れ (単位) {({k: round(v, 2) for k, v in sm.items()})}")


if __name__ == "__main__":
    main()
