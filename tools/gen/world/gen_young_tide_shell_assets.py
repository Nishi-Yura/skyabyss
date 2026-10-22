"""潮殻の稚貝 (young_tide_shell。巨神の子、28 章) のゲームの素材を書く。乗っている間だけ現れ、パーティ 6 人を背に乗せて海の上を滑る。

使い方: リポジトリ直下で  python tools/gen/world/gen_young_tide_shell_assets.py [--preview]
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/young_tide_shell.geo.json            箱の geo (低ポリが切れているときの姿。骨は低ポリのメッシュと同じ 10 本)
  textures/entity/young_tide_shell.png            箱のテクスチャ (1 ブロック 32 画素。宣言の大きさの 2 倍の絵)
  textures/entity/young_tide_shell_glowmask.png   光る画素 (歌のかけら)
  animations/entity/young_tide_shell.animation.json
  tools/sources/young/young_tide_shell_notes.json  (Java 側への覚え書き: 鞍の高さ・骨・アニメの長さ)
低ポリのメッシュは tools/install/install_lowpoly_young_tide_shell.py (案は tools/rough/lowpoly/young_tide_shell/opt_{a,b,c}.py)。

骨 (作図は前 = -Z。geo は x と z を反転し、root を Y 軸で 180 度回す。真珠守りと同じ決まり。最後の前は -Z):
  root > body > saddle (6 人の座る台。アニメで動かさない)
              > head > song (歌のかけら。光る)
              > fin_fl, fin_fr (前ひれ。作図の +x = fin_fr = ゲームの右)
              > fin_bl, fin_br (後ろひれ)
              > tail
鞍の上面は y = SEAT_Y (ブロック) の平らな面で、x は ±1.6、z は ±3.2 ブロックを覆う。座る位置は x = ±1.0、z = -2 / 0 / +2。
水面は y = 0.4 ブロックあたり (下の体は水に沈む)。

アニメ (秒): idle 3.0 (loop)、move 1.2 (loop)、appear 1.6、dissolve 1.4 (hold。root の scale が 0.02 で終わる)。
idle と move は鞍の上面を 0.15 ブロック (2.4 単位) より動かさない (verify で確かめる)。
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
NAME = "young_tide_shell"
SEAT_Y = 1.5            # ブロック
SEAT = SEAT_Y * 16.0    # 単位
SEATS = [(sx, sz) for sz in (-2.0, 0.0, 2.0) for sx in (-1.0, 1.0)]   # ブロック (作図空間 = 前が -Z。geo でも同じ表)

# 骨 (作図空間 = 前が -Z)。低ポリのラフ (tools/rough/lowpoly/young_tide_shell/ys.py) もここを読む。
SKELETON_DRAW = {
    "root": (None, (0.0, 0.0, 0.0)),
    "body": ("root", (0.0, 12.0, 0.0)),
    "saddle": ("body", (0.0, SEAT, 0.0)),
    "head": ("body", (0.0, 14.0, -66.0)),
    "song": ("head", (0.0, 32.0, -82.0)),
    "fin_fl": ("body", (-42.0, 9.0, -38.0)),
    "fin_fr": ("body", (42.0, 9.0, -38.0)),
    "fin_bl": ("body", (-40.0, 7.0, 44.0)),
    "fin_br": ("body", (40.0, 7.0, 44.0)),
    "tail": ("body", (0.0, 9.0, 62.0)),
}

# ====================================================================== 箱 (案 A「平背の子亀」の箱の姿)
# (骨, 材, 中心 (作図空間), 大きさ)。重ならないこと (触れるのはよい)。
BOXES = [
    ("body", "hull", (0.0, 7.0, 0.0), (88, 14, 120)),
    ("body", "shell", (0.0, 18.5, 0.0), (104, 9, 136)),
    ("saddle", "deck", (0.0, SEAT - 0.5, 0.0), (60, 1, 118)),
    ("saddle", "rail", (31.5, SEAT + 1.0, 0.0), (3, 4, 110)),
    ("saddle", "rail", (-31.5, SEAT + 1.0, 0.0), (3, 4, 110)),
    ("saddle", "rail", (0.0, SEAT + 1.0, -61.0), (66, 4, 4)),
    ("saddle", "rail", (0.0, SEAT + 1.0, 61.0), (66, 4, 4)),
    ("body", "coral", (42.0, 27.0, -42.0), (6, 8, 6)),
    ("body", "coral", (41.0, 26.5, 34.0), (6, 7, 6)),
    ("body", "coral2", (-42.0, 26.0, -34.0), (6, 6, 6)),
    ("body", "coral2", (-40.0, 25.5, 40.0), (5, 5, 5)),
    ("head", "skin", (0.0, 13.0, -72.0), (18, 10, 8)),
    ("head", "skin", (0.0, 15.0, -86.0), (24, 16, 20)),
    ("head", "eye", (12.5, 18.0, -88.0), (1, 4, 4)),
    ("head", "eye", (-12.5, 18.0, -88.0), (1, 4, 4)),
    ("head", "lip", (0.0, 24.0, -86.0), (6, 2, 6)),
    ("song", "song", (0.0, 27.5, -86.0), (5, 5, 5)),
    ("fin_fl", "fin", (-54.0, 9.0, -36.0), (20, 3, 14)),
    ("fin_fl", "fin", (-68.0, 9.0, -32.0), (8, 2, 10)),
    ("fin_fr", "fin", (54.0, 9.0, -36.0), (20, 3, 14)),
    ("fin_fr", "fin", (68.0, 9.0, -32.0), (8, 2, 10)),
    ("fin_bl", "fin", (-50.0, 6.0, 46.0), (12, 3, 12)),
    ("fin_br", "fin", (50.0, 6.0, 46.0), (12, 3, 12)),
    ("tail", "skin", (0.0, 8.0, 72.0), (10, 6, 8)),
]

PAL = {
    "hull": [(150, 128, 96), (186, 164, 124), (214, 196, 156), (232, 220, 184)],
    "shell": [(20, 70, 72), (34, 104, 102), (56, 138, 128), (96, 176, 158)],
    "deck": [(76, 132, 124), (110, 166, 152), (150, 198, 178), (196, 226, 206)],
    "rail": [(204, 150, 168), (236, 196, 206), (252, 228, 232)],
    "lip": [(204, 150, 168), (236, 196, 206), (252, 228, 232)],
    "coral": [(150, 50, 40), (210, 96, 64), (240, 140, 96)],
    "coral2": [(110, 50, 120), (160, 90, 170), (204, 140, 210)],
    "skin": [(150, 128, 96), (186, 164, 124), (214, 196, 156), (232, 220, 184)],
    "fin": [(36, 92, 88), (56, 128, 118), (90, 164, 148)],
    "eye": [(8, 10, 16), (18, 20, 28), (32, 34, 44)],
    "song": [(150, 230, 255), (205, 248, 255), (255, 255, 255)],
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
    order = sorted(range(len(BOXES)), key=lambda i: -(BOXES[i][3][2] + BOXES[i][3][1]))
    pos = {}
    for i in order:
        bone, mat, c, s = BOXES[i]
        w, h, d = (int(math.ceil(v)) for v in s)
        bw, bh = 2 * (w + d), d + h
        if x + bw > TEX_W:
            x, y, rowh = 0, y + rowh, 0
        pos[i] = (x, y)
        x += bw
        rowh = max(rowh, bh)
    placed = [pos[i] for i in range(len(BOXES))]
    tex_h = y + rowh
    tex_h = int(math.ceil(max(tex_h, 16) / 16.0) * 16)   # 2 の累乗にしない (大きな箱が多く、空きが増えるため)
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
            if mat in ("shell", "deck"):
                # 甲羅の板 (鱗板) の継ぎ目: 斜めの格子
                per = 22 if mat == "shell" else 30
                seam = (((xx // PX) + (yy // PX) // 2) % per == 0) | (((yy // PX) + (xx // PX) // 3) % per == 0)
                col = np.where(seam[..., None], ramp[0], col)
            if mat == "hull" and face not in ("up", "down"):
                # 水に浸かる腹: 下ほど青く
                wet = yy > H * 0.45
                col = np.where(wet[..., None], col * np.array([0.7, 0.9, 1.0]), col)
            if mat == "eye" and face in ("north", "west", "east"):
                pup = (np.abs(xx - W / 2 + 0.5) < PX * 0.8) & (np.abs(yy - H / 2 + 0.5) < PX * 0.8)
                col = np.where(pup[..., None], np.array([180, 240, 255.0]), col)
                g = glow[fv * PX:fv * PX + H, fu * PX:fu * PX + W]
                g[pup] = (255, 255, 255, 255)
            y0, x0 = fv * PX, fu * PX
            img[y0:y0 + H, x0:x0 + W, :3] = np.clip(col, 0, 255).astype(np.uint8)
            img[y0:y0 + H, x0:x0 + W, 3] = 255
            if mat == "song":
                glow[y0:y0 + H, x0:x0 + W] = (255, 255, 255, 255)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + NAME, "texture_width": TEX_W, "texture_height": tex_h,
                        "visible_bounds_width": 12, "visible_bounds_height": 6, "visible_bounds_offset": [0, 2.0, 0]},
        "bones": [bones[b] for b in SKELETON_DRAW]}]}
    return geo, img, glow


# ====================================================================== アニメ (ファイルの値そのまま)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


ONE = (1, 1, 1)
REST = {"rotation": (0, 0, 0), "scale": ONE, "position": (0, 0, 0)}
# 前ひれ・後ろひれをたたんだ姿 (現れる前・溶けた後)。z が正 = fin_r* を上げる、の向きは view.py で確かめた
TUCK = {"fin_fl": (0, -50, 20), "fin_fr": (0, 50, -20), "fin_bl": (0, 40, 15), "fin_br": (0, -40, -15)}


def frames(*pairs):
    return {t: v for t, v in pairs}


def mirror(b, v):
    """左右対の骨: *_l* は y と z の符号を反転。"""
    return v if b.endswith("r") else (v[0], -v[1], -v[2])


ANIM = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        # 波に揺られて浮かぶ。歌のかけらが脈打つ。ひれはゆっくり水をかく。
        "body": {"position": ["0", sn(0.6, 120), "0"], "rotation": [sn(0.4, 120, 60), "0", sn(0.6, 120, 90)]},
        "head": {"rotation": [sn(3, 120), sn(6, 120, 40), "0"]},
        "song": {"scale": [f"1 + {sn(0.14, 240)}"] * 3},
        "fin_fr": {"rotation": ["0", sn(6, 120), sn(4, 120, 60)]},
        "fin_fl": {"rotation": ["0", sn(-6, 120), sn(-4, 120, 60)]},
        "fin_br": {"rotation": ["0", sn(4, 120, 180), "0"]},
        "fin_bl": {"rotation": ["0", sn(-4, 120, 180), "0"]},
        "tail": {"rotation": ["0", sn(6, 120), "0"]},
    }},
    "move": {"loop": True, "len": 1.2, "bones": {
        # 海の上を滑る: 前ひれを揃えて大きくかき、後ろひれで舵、体は小さく上下。頭は前へ伸ばす。
        "body": {"position": ["0", sn(0.7, 600), "0"], "rotation": [sn(0.9, 300, 90), "0", sn(0.5, 300)]},
        "head": {"rotation": [f"-4 + {sn(3, 300, 40)}", sn(3, 300), "0"]},
        "song": {"scale": [f"1 + {sn(0.08, 600)}"] * 3},
        "fin_fr": {"rotation": [sn(6, 300, 90), sn(28, 300), sn(16, 300, 90)]},
        "fin_fl": {"rotation": [sn(6, 300, 90), sn(-28, 300), sn(-16, 300, 90)]},
        "fin_br": {"rotation": ["0", sn(14, 300, 180), sn(6, 300, 270)]},
        "fin_bl": {"rotation": ["0", sn(-14, 300, 180), sn(-6, 300, 270)]},
        "tail": {"rotation": ["0", sn(10, 300), "0"]},
    }},
}

# 現れる: 歌のかけら (光る粒) が先に灯り、そこから体が育って (0〜1.1)、少し大きすぎてから戻る。ひれが開く。
_ap = {
    "root": {"scale": frames((0, (0.04, 0.04, 0.04)), (0.5, (0.22, 0.22, 0.22)), (1.1, (1.07, 1.07, 1.07)),
                             (1.35, (0.97, 0.97, 0.97)), (1.6, ONE))},
    "body": {"position": frames((0, (0, -10, 0)), (1.1, (0, 1, 0)), (1.6, (0, 0, 0)))},
    "song": {"scale": frames((0, (5, 5, 5)), (0.6, (3, 3, 3)), (1.1, (1.5, 1.5, 1.5)), (1.6, ONE))},
    "head": {"scale": frames((0, (0.6, 0.6, 0.6)), (1.2, (1.05, 1.05, 1.05)), (1.6, ONE)),
             "rotation": frames((0, (12, 0, 0)), (1.2, (-6, 0, 0)), (1.6, (0, 0, 0)))},
}
for b, v in TUCK.items():
    over = tuple(-0.25 * a for a in v)
    _ap[b] = {"rotation": frames((0, v), (0.7, v), (1.25, over), (1.6, (0, 0, 0)))}
ANIM["appear"] = {"loop": False, "len": 1.6, "bones": _ap}

# 溶ける: ひと息ふくらみ (0.25)、ひれをたたんで沈みながら縮み、歌のかけらだけが最後まで灯って消える。
_ds = {
    "root": {"scale": frames((0, ONE), (0.25, (1.04, 1.04, 1.04)), (0.9, (0.45, 0.45, 0.45)), (1.4, (0.02, 0.02, 0.02)))},
    "body": {"position": frames((0, (0, 0, 0)), (1.4, (0, -8, 0)))},
    "song": {"scale": frames((0, ONE), (0.6, (2.0, 2.0, 2.0)), (1.4, (6, 6, 6)))},
    "head": {"rotation": frames((0, (0, 0, 0)), (0.8, (14, 0, 0)), (1.4, (14, 0, 0)))},
}
for b, v in TUCK.items():
    _ds[b] = {"rotation": frames((0, (0, 0, 0)), (0.8, v), (1.4, v))}
ANIM["dissolve"] = {"loop": "hold", "len": 1.4, "bones": _ds}


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
    """{骨: {rotation, scale, position}} (ファイルの値)。低ポリの確認の絵 (ys.pose_body) に渡す。"""
    return {b: {ch_: tuple(eval_channel(v, t)) for ch_, v in chans.items()} for b, chans in ANIM[name]["bones"].items()}


# ---- 鞍の上面がどれだけ動くか (geo 空間で骨を順に当てる。GeckoLib の順: 拡大 → 回転 → 位置)

def _rot_geo(rx, ry, rz):
    def R(axis, a):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        if axis == "x":
            return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
        if axis == "y":
            return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return R("z", -rz) @ R("y", -ry) @ R("x", -rx)


def pose_geo_point(p, bone, pose):
    p = np.asarray(p, float)
    while bone is not None and bone != "root":
        parent, _ = SKELETON_DRAW[bone]
        pv = np.array(bone_geo_pivot(bone), float)
        st = pose.get(bone)
        if st:
            q = p - pv
            if "scale" in st:
                q = q * np.array(st["scale"], float)
            if "rotation" in st:
                q = _rot_geo(*st["rotation"]) @ q
            p = q + pv
            if "position" in st:
                ps = np.array(st["position"], float)
                p = p + np.array([-ps[0], ps[1], ps[2]])
        bone = parent
    return p


def saddle_drift(name, steps=48):
    a = ANIM[name]
    pts = [np.array(flip((sx * 16, SEAT, sz * 16)), float) for sx in (-1.6, 1.6) for sz in (-3.2, 3.2)]
    pts += [np.array(flip((sx * 16, SEAT, sz * 16)), float) for sx, sz in SEATS]
    worst = 0.0
    for i in range(steps + 1):
        pose = pose_at(name, a["len"] * i / steps)
        for p in pts:
            worst = max(worst, float(np.linalg.norm(pose_geo_point(p, "saddle", pose) - p)))
    return worst / 16.0


def verify(geo, anim):
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    assert geo["minecraft:geometry"][0]["bones"][0]["name"] == "root"
    for an, a in anim["animations"].items():
        for b in a["bones"]:
            assert b in names, (an, b)
    for r in ("idle", "move", "appear", "dissolve"):
        assert f"animation.{NAME}.{r}" in anim["animations"], r
    for r in ("idle", "move"):
        d = saddle_drift(r)
        assert d <= 0.15, (r, d)
    assert ANIM["dissolve"]["bones"]["root"]["scale"][1.4][0] <= 0.05


def notes():
    return {"model": NAME, "seat_y": SEAT_Y, "seats": [[sx, SEAT_Y, sz] for sx, sz in SEATS], "hitbox": [4.6, 3.4],
            "waterline_y": 0.4, "front": "-Z (final geo space; root carries the pearl-keeper style Y 180 rest rotation)",
            "bones": list(SKELETON_DRAW), "anims": {k: v["len"] for k, v in ANIM.items()},
            "loops": {k: ("hold_on_last_frame" if v["loop"] == "hold" else bool(v["loop"])) for k, v in ANIM.items()},
            "saddle_drift_blocks": {k: round(saddle_drift(k), 3) for k in ("idle", "move")},
            "glow": "textures/entity/young_tide_shell_glowmask.png (cube) / textures/lowpoly/young_tide_shell_glowmask.png (low-poly): the song mote"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="箱の姿を docs/review/young_roughs/tide_shell_cube_preview.png に描く")
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
    nd = ROOT / "tools/sources/young"
    nd.mkdir(parents=True, exist_ok=True)
    (nd / f"{NAME}_notes.json").write_text(json.dumps(notes(), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    n = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print(f"{NAME}: 箱 {n}、テクスチャ {img.shape[1]}x{img.shape[0]}、アニメ {len(anim['animations'])}、鞍 y = {SEAT_Y} ブロック")
    for k, v in ANIM.items():
        extra = f", 鞍のずれ {saddle_drift(k):.3f} ブロック" if k in ("idle", "move") else ""
        print(f"  animation.{NAME}.{k}: {v['len']} s = {round(v['len'] * 20)} tick, loop={v['loop']}{extra}")
    if a.preview:
        sys.path.insert(0, str(ROOT / "tools/rough"))
        import voxel as vx
        faces = vx.load_geo(geo, img, glow)
        out = ROOT / "docs/review/young_roughs"
        out.mkdir(parents=True, exist_ok=True)
        ims = [vx.render(faces, yaw=y, pitch=18, size=(360, 360)) for y in (215, 35, 270)]
        W = Image.new("RGB", (360 * 3, 360))
        for i, im in enumerate(ims):
            W.paste(im.convert("RGB"), (360 * i, 0))
        W.save(out / "tide_shell_cube_preview.png")


if __name__ == "__main__":
    main()
