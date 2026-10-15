"""潮の民の敵 3 種のラフ: 珊瑚憑き (coral_thrall) / 磯走り (reef_scuttler) / 泡吹き (bubble_puffer)、
と珊瑚の銛 (coral_spear)。

実行: リポジトリ直下で  python tools/rough/tide_mobs.py
出力: docs/review/tide_shell_roughs/mobs/<subject>/<a|b|c>/ (geo.json, texture.png, glowmask.png, render_*.png)
      docs/review/tide_shell_roughs/mobs/coral_spear/ (銛の geo。珊瑚憑きの受け口 socket_main に付ける)
      docs/review/tide_shell_roughs/<subject>.png (比較シート)

座標: y が上。前は +z で作り、書き出す geo の root 骨を yaw 180 度回して前が -z になるようにする。
      1 単位 = 1/16 ブロック。立方体は 0.5 単位の格子 (泡吹きの体だけ 1 単位の格子)。
      テクスチャは 1 単位 = 2 画素、面ごとの uv。光る画素は glowmask に入れる。
      回転は使わない (重なり検査が全部の立方体に効くように)。
      描画は GeckoLib の鏡写し (x を反転) に合わせ、yaw を反転して左右反転した絵にする (sentinel_v4 と同じ)。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402

ROOT = vx.ROOT
REVIEW = ROOT / "docs" / "review" / "tide_shell_roughs"
MOBS = REVIEW / "mobs"
VIEW = (360, 360)
TEX_W = 256
FLIP = Image.FLIP_LEFT_RIGHT
FACES = ("north", "south", "east", "west", "up", "down")
GLOW_MATS = {"seaglass"}
SOCKET_P = (-5.25, 11.75, 5.25)  # 右の拳の中心 (受け口 socket_main の pivot)

# 材質ごとの 4 段の色 (暗い, 中間, 明るい, 最も明るい)
RAMP = {
    "bone": ("#7D7260", "#A99F88", "#CFC6AE", "#EAE3CF"),
    "bone_dk": ("#3F3829", "#5E5546", "#7E7463", "#9A907D"),
    "barnacle": ("#33302C", "#5A544C", "#837C70", "#ABA392"),
    "coral_p": ("#6F2741", "#AD4762", "#D87487", "#F2A8B2"),
    "coral_o": ("#7E3A1F", "#C0603A", "#E88B5C", "#F8BB8E"),
    "coral_v": ("#3D2656", "#6A479A", "#9A77C4", "#C7B0E4"),
    "brain": ("#6F2741", "#AD4762", "#D87487", "#F2A8B2"),
    "cloth": ("#213A39", "#37645F", "#5A8F86", "#84B4AA"),
    "kelp": ("#153A27", "#2C6642", "#4B9262", "#78BC88"),
    "seaglass": ("#17606C", "#2AA3B1", "#8BEEF8", "#DDFDFF"),
    "dark": ("#0C0A10", "#1A1721", "#2A2633", "#3B3547"),
    "skin": ("#24565F", "#3F8189", "#6AB0B0", "#9ED6CF"),
    "belly": ("#7F9080", "#B9C7B0", "#DDE7D2", "#F3F8EA"),
    "tendril": ("#243F5C", "#3F6E97", "#70A6CC", "#A6D4EE"),
    "crab": ("#6E2A18", "#A94B2A", "#DA7748", "#F2A777"),
    "crab_dk": ("#3F1A10", "#6B2F1C", "#8C4426", "#AD5F3A"),
    "ruin": ("#3D4248", "#6B7078", "#9AA0A8", "#C7CCD2"),
    "clay": ("#4E3223", "#7E5438", "#A9785A", "#CFA37E"),
}


def hexrgb(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def h01(X, Y, Z):
    """位置で決まる 0..1 の擬似乱数 (同じ位置なら毎回同じ値)。"""
    i, j, k = math.floor(X * 2), math.floor(Y * 2), math.floor(Z * 2)
    h = ((i * 73856093) ^ (j * 19349663) ^ (k * 83492791)) & 0xFFFF
    return h / 65536.0


def style(mat, X, Y, Z, f):
    """材質ごとの模様。None なら面の明るさだけで塗る。"""
    ramp = RAMP[mat]
    h = h01(X, Y, Z)
    if mat == "brain":
        return ramp[0] if (math.floor(X * 2) + math.floor(Y * 2)) % 3 == 0 else None
    if mat in ("coral_p", "coral_o", "coral_v"):
        if h < 0.10:
            return ramp[3]
        if h < 0.22:
            return ramp[2]
        if h > 0.90:
            return ramp[0]
        return None
    if mat == "barnacle":
        if h < 0.30:
            return ramp[3] if h < 0.12 else ramp[2]
        return None
    if mat == "cloth":
        if h > 0.92:
            return ramp[3]
        return ramp[1] if math.floor((X + Y) * 2) % 4 == 0 else None
    if mat == "kelp":
        return ramp[2] if math.floor(Y * 2) % 4 == 0 else None
    if mat in ("bone", "bone_dk"):
        if h < 0.05:
            return ramp[0]
        if h > 0.97:
            return ramp[3]
        return None
    if mat in ("skin", "crab", "tendril"):
        if h < 0.10:
            return ramp[3]
        if h > 0.92:
            return ramp[0]
        return None
    if mat == "ruin":
        return ramp[0] if (X % 2.0) < 0.25 or (Z % 2.0) < 0.25 else None
    if mat == "clay":
        return ramp[0] if math.floor((X - Z) * 2) % 7 == 0 else None
    return None


def face_px(b, f):
    x0, y0, z0, x1, y1, z1 = b
    sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
    if f in ("north", "south"):
        return int(round(2 * sx)), int(round(2 * sy))
    if f in ("east", "west"):
        return int(round(2 * sz)), int(round(2 * sy))
    return int(round(2 * sx)), int(round(2 * sz))


def world(b, f, c, r):
    """画素 (c, r) の中心のワールド座標 (1 画素 = 0.5 単位)。"""
    x0, y0, z0, x1, y1, z1 = b
    cx, cy = (c + 0.5) / 2, (r + 0.5) / 2
    if f == "south":
        return x0 + cx, y1 - cy, z1
    if f == "north":
        return x1 - cx, y1 - cy, z0
    if f == "east":
        return x1, y1 - cy, z1 - cx
    if f == "west":
        return x0, y1 - cy, z0 + cx
    if f == "up":
        return x0 + cx, y1, z0 + cy
    return x0 + cx, y0, z0 + cy


def shade(ramp, f, c, r, w, h):
    dk, md, lt, hi = ramp
    if f == "up":
        return hi if (r == 0 and h > 1) else lt
    if f == "down":
        return dk
    if c == 0 or r == 0:
        return lt
    if c == w - 1 or r == h - 1:
        return dk
    return md


def in_any(X, Y, rects):
    return any(a <= X <= b and c <= Y <= d for a, b, c, d in rects)


# ---------------------------------------------------------------- モデル

class Model:
    """箱 (立方体) を積むモデル。置くたびに重なりを調べ、重なれば例外にする。"""

    def __init__(self, ident: str):
        self.ident = ident
        self.bones: list[dict] = []
        self.boxes: list[dict] = []
        self.extra = None   # (X,Y,Z,f,mat) -> 色 か None。模様より優先 (目など)
        self.glowfn = None  # (X,Y,Z,f,mat) -> bool。seaglass の材質は常に光る

    def bone(self, name, pivot, parent=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat):
        v = (x0, y0, z0, x1, y1, z1)
        assert all(float(a * 2).is_integer() for a in v), (self.ident, bone, v, "0.5 単位の格子ではない")
        assert x1 > x0 and y1 > y0 and z1 > z0, (self.ident, bone, v)
        for o in self.boxes:
            ob = o["b"]
            if all(min(v[i + 3], ob[i + 3]) > max(v[i], ob[i]) for i in range(3)):
                raise ValueError(f"重なり {self.ident}: {bone} {v} と {o['bone']} {ob}")
        self.boxes.append({"bone": bone, "b": tuple(float(a) for a in v), "mat": mat})

    def paint(self, X, Y, Z, f, mat):
        if self.extra:
            r = self.extra(X, Y, Z, f, mat)
            if r:
                return r
        return style(mat, X, Y, Z, f)

    def finish(self):
        items = []
        for i, bx in enumerate(self.boxes):
            for f in FACES:
                w, h = face_px(bx["b"], f)
                items.append((h, w, i, f))
        items.sort(key=lambda t: (-t[0], -t[1], t[2], t[3]))
        pos, x, y, shelf = {}, 0, 0, 0
        for h, w, i, f in items:
            if x + w > TEX_W:
                x, y, shelf = 0, y + shelf, 0
            pos[(i, f)] = (x, y, w, h)
            x += w
            shelf = max(shelf, h)
        H = y + shelf
        H += H % 2
        tex = np.zeros((H, TEX_W, 4), np.uint8)
        gl = np.zeros((H, TEX_W, 4), np.uint8)
        for i, bx in enumerate(self.boxes):
            mat = bx["mat"]
            ramp = RAMP[mat]
            for f in FACES:
                u, v, w, h = pos[(i, f)]
                for r in range(h):
                    for c in range(w):
                        X, Y, Z = world(bx["b"], f, c, r)
                        col = self.paint(X, Y, Z, f, mat) or shade(ramp, f, c, r, w, h)
                        tex[v + r, u + c] = (*hexrgb(col), 255)
                        lit = mat in GLOW_MATS or (self.glowfn is not None and self.glowfn(X, Y, Z, f, mat))
                        if lit:
                            gl[v + r, u + c] = (*hexrgb(col), 255)
        bones_out = []
        for bn in self.bones:
            cubes = []
            for i, bx in enumerate(self.boxes):
                if bx["bone"] != bn["name"]:
                    continue
                x0, y0, z0, x1, y1, z1 = bx["b"]
                uv = {}
                for f in FACES:
                    u, v, w, h = pos[(i, f)]
                    uv[f] = {"uv": [u, v], "uv_size": [w, h]}
                cubes.append({"origin": [x0, y0, z0], "size": [x1 - x0, y1 - y0, z1 - z0], "uv": uv})
            b = dict(bn)
            b["cubes"] = cubes
            bones_out.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": TEX_W,
                            "texture_height": H, "visible_bounds_width": 3, "visible_bounds_height": 3,
                            "visible_bounds_offset": [0, 1.5, 0]},
            "bones": bones_out}]}
        return geo, tex, gl


def sb(m, bone, sx, a, b, y0, y1, z0, z1, mat):
    """左右対称の箱。x は sx 側の a..b (sx = -1 なら x は -b..-a)。"""
    lo, hi = sorted((sx * a, sx * b))
    m.box(bone, lo, y0, z0, hi, y1, z1, mat)


def buried_count(boxes):
    """6 面すべてが他の箱に覆われて見えない箱の数 (内側に埋まった立方体)。"""
    lo = np.array([bx["b"][:3] for bx in boxes], float)
    hi = np.array([bx["b"][3:] for bx in boxes], float)
    n, cnt = len(boxes), 0
    for i in range(n):
        all_hidden = True
        for ax in range(3):
            for side in (0, 1):
                plane = hi[i, ax] if side else lo[i, ax]
                off = 0.25 if side else -0.25
                a1, a2 = [k for k in range(3) if k != ax]
                g1 = np.arange(lo[i, a1] + 0.25, hi[i, a1], 0.5)
                g2 = np.arange(lo[i, a2] + 0.25, hi[i, a2], 0.5)
                M1, M2 = np.meshgrid(g1, g2, indexing="ij")
                P = np.zeros((M1.size, 3))
                P[:, ax] = plane + off
                P[:, a1] = M1.ravel()
                P[:, a2] = M2.ravel()
                inside = ((P[:, None, :] > lo[None] + 1e-6) & (P[:, None, :] < hi[None] - 1e-6)).all(-1)
                inside[:, i] = False
                if not inside.any(1).all():
                    all_hidden = False
                    break
            if not all_hidden:
                break
        cnt += all_hidden
    return cnt


def to_game(geo):
    """書き出し用: 全ての親なし骨を root (yaw 180 度) の子にする。"""
    g = geo["minecraft:geometry"][0]
    for b in g["bones"]:
        if "parent" not in b:
            b["parent"] = "root"
    g["bones"].insert(0, {"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 180.0, 0.0]})
    return geo


def export(m, folder: Path, root=True):
    """geo.json・テクスチャ・光る画素を書き出す。root=False なら銛のように root を付けない。"""
    geo, tex, gl = m.finish()
    overlaps = vx.check_overlaps(geo)  # root を付ける前に調べる (回転があると比べられないため)
    buried = buried_count(m.boxes) if len(m.boxes) <= 400 else None
    out_geo = to_game(copy.deepcopy(geo)) if root else geo
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "geo.json").write_text(json.dumps(out_geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(folder / "texture.png")
    Image.fromarray(gl, "RGBA").save(folder / "glowmask.png")
    assert gl[..., 3].any(), f"光る画素がない: {m.ident}"
    return dict(geo=out_geo, tex=tex, gl=gl, n=len(m.boxes), overlaps=overlaps, buried=buried)


def bleach(tex):
    """石膏化 (珊瑚が白くなった) の色。光る画素は glowmask 側なので触らない。"""
    out = tex.astype(float)
    a = out[..., 3] > 0
    tint = np.array([236, 230, 214], float)
    out[..., :3][a] = out[..., :3][a] * 0.4 + tint * 0.6
    return out.astype(np.uint8)


# ---------------------------------------------------------------- 銛 (別モデル)

def build_spear():
    m = Model("coral_spear")
    m.bone("spear", (0, 0, 0))

    def ring(y0, y1, mat):  # 軸の周りを囲む輪 (軸の 1x1 の穴を残す)
        m.box("spear", -1, y0, -1, -0.5, y1, 1, mat)
        m.box("spear", 0.5, y0, -1, 1, y1, 1, mat)
        m.box("spear", -0.5, y0, -1, 0.5, y1, -0.5, mat)
        m.box("spear", -0.5, y0, 0.5, 0.5, y1, 1, mat)

    def rod(y0, y1, mat):
        m.box("spear", -0.5, y0, -0.5, 0.5, y1, 0.5, mat)

    m.box("spear", -0.5, -11.5, -0.5, 0.5, -10.5, 0.5, "bone_dk")  # 石突き (地面に当たる)
    rod(-10.5, -2, "bone_dk")
    ring(-2, 2, "cloth")          # 握りの布巻き (手の中心)
    rod(2, 8, "bone")
    ring(8, 9, "coral_o")         # 珊瑚の節
    rod(9, 14, "bone")
    ring(14, 15, "coral_v")
    rod(15, 20, "bone")
    m.box("spear", -1, 20, -1, 1, 21, 1, "coral_p")               # 根元の輪
    m.box("spear", -1.5, 21, -0.5, 1.5, 22, 0.5, "coral_p")       # 銛の首
    m.box("spear", -2.5, 21.5, -0.5, -1.5, 23, 0.5, "coral_o")    # 返し (左)
    m.box("spear", 1.5, 21.5, -0.5, 2.5, 23, 0.5, "coral_o")      # 返し (右)
    m.box("spear", -1, 22, -0.5, 1, 23.5, 0.5, "coral_p")         # 穂
    m.box("spear", -0.5, 23.5, -0.5, 0.5, 26, 0.5, "seaglass")    # 穂先 (海硝子が光る)
    return m


# ---------------------------------------------------------------- 珊瑚憑き

EYE_OUT = ((-2.5, -0.5, 27.5, 29.5), (0.5, 2.5, 27.5, 29.5))
EYE_IN = ((-2, -1, 28, 29), (1, 2, 28, 29))


def face_eyes(fz):
    """頭の前の面 (z = fz) に、くぼんだ目と光る海硝子を描く。"""
    def extra(X, Y, Z, f, mat):
        if mat == "bone" and f == "south" and abs(Z - fz) < 1e-6:
            if in_any(X, Y, EYE_IN):
                return RAMP["seaglass"][2]
            if in_any(X, Y, EYE_OUT):
                return RAMP["dark"][0]
        return None

    def glow(X, Y, Z, f, mat):
        return mat == "bone" and f == "south" and abs(Z - fz) < 1e-6 and in_any(X, Y, EYE_IN)
    return extra, glow


LEG_T = {  # 脚の前後の範囲 (z0, z1) と、付け根の x・膝の x・足先の x
    "a": dict(zs=((-3, -2), (-0.5, 0.5), (2, 3)), xin=5, xk=6.5, xo=7.5),
    "b": dict(zs=((-3, -2), (-0.5, 0.5), (2, 3)), xin=4, xk=5.5, xo=6.5),
    "c": dict(zs=((-3, -2.5), (-1.5, -1), (0, 0.5), (1.5, 2)), xin=3.5, xk=6.5, xo=6.5),
}


def thrall_model(opt: str):
    m = Model(f"coral_thrall_{opt}")
    fz = 4.5 if opt == "c" else 3.0                 # 顔の前の面
    sz0, sz1 = (-1.5, 4.5) if opt == "c" else (-3.0, 3.0)  # 頭の前後
    ct = 24.5 if opt == "c" else 25.0               # 胸の上
    cz = 1.5 if opt == "a" else 2.0                 # 胸の前後 (半分)
    m.extra, m.glowfn = face_eyes(fz)

    m.bone("body", (0, 14, 0))
    m.bone("head", (0, 26.5, 0), "body")
    m.bone("arm_r", (-4.5, 24, 0), "body")
    m.bone("forearm_r", (-5.25, 16, 0), "arm_r")
    m.bone("socket_main", SOCKET_P, "forearm_r")   # 銛を持つ受け口 (右の拳の中心)
    m.bone("arm_l", (4.5, 24, 0), "body")
    m.bone("forearm_l", (5.25, 16, 0), "arm_l")
    m.bone("leg_r", (-2, 13.5, 0))
    m.bone("leg_l", (2, 13.5, 0))

    # 脚 (腿, すね, 足)
    for sx in (-1, 1):
        lb = "leg_r" if sx < 0 else "leg_l"
        sb(m, lb, sx, 0.5, 3.5, 7, 13.5, -1.5, 1.5, "bone_dk")
        sb(m, lb, sx, 1, 3, 1, 7, -1, 1, "bone_dk")
        sb(m, lb, sx, 0.5, 3.5, 0, 1, -1, 2.5, "barnacle")

    # 腰とぼろ布の裾
    m.box("body", -4, 13.5, -2, 4, 16, 2, "bone_dk")
    for a, b, y0 in ((-4, -2.5, 10), (-2, 0, 11.5), (0.5, 2.5, 8.5), (3, 4, 11)):
        m.box("body", a, y0, 2, b, 13.5, 2.5, "cloth")
    if opt != "c":
        for a, b, y0 in ((-3.5, -1, 10.5), (1, 3.5, 9.5)):
            m.box("body", a, y0, -2.5, b, 13.5, -2, "cloth")

    # 胸と背骨
    m.box("body", -4, 16, -cz, 4, ct, cz, "bone")
    if opt != "c":
        m.box("body", -0.5, 16, -cz - 0.5, 0.5, 24, -cz, "bone_dk")
    if opt == "a":  # 斜めの布の帯
        for k in range(7):
            m.box("body", -3.5 + k, 22 - 0.5 * k, cz, -2.5 + k, 23.5 - 0.5 * k, cz + 0.5, "cloth")
    if opt == "b":  # 右半身を脳珊瑚が包む
        m.box("body", 0.5, 16.5, cz, 2.5, 24.5, cz + 1, "brain")
        m.box("body", 2.5, 17.5, cz, 4, 24.5, cz + 1, "brain")
    if opt == "c":  # 背の瘤と、海藻の外套
        m.box("head", -3, 24.5, -2.5, 3, 26, -0.5, "kelp")
        for a, b, y0, y1 in ((-3.5, -2.5, 6, 22), (-1.5, -0.5, 9, 24), (0.5, 1.5, 7, 20), (2.5, 3.5, 10, 23)):
            m.box("body", a, y0, -2.5, b, y1, -2, "kelp")

    # 両腕 (右は銛を持つ。左は A と C が垂れた手、B は珊瑚の棍棒)
    for sx, arm, fa in ((-1, "arm_r", "forearm_r"), (1, "arm_l", "forearm_l")):
        sb(m, arm, sx, 4, 6.5, 16, 24, -1.5, 1.5, "bone")
        sb(m, arm, sx, 4, 6.5, 24, 25.5, -2, 2, "barnacle")
        if opt == "b":
            sb(m, arm, sx, 6.5, 7, 22, 25.5, -2, 2, "barnacle")  # 大きな肩当て
        # 肩から伸びる珊瑚の枝 (肩の殻の上)
        sb(m, arm, sx, 5, 6.5, 25.5, 27, -1.5, -0.5, "coral_o")
        sb(m, arm, sx, 4.5, 5.5, 25.5, 26.5, 0.5, 1.5, "coral_p")
    sb(m, "forearm_r", -1, 4, 6.5, 13.5, 16, -1, 1.5, "bone")      # 前腕 1
    sb(m, "forearm_r", -1, 4, 6.5, 12, 14, 1.5, 4, "bone")         # 前腕 2 (前へ)
    sb(m, "forearm_r", -1, 4, 6.5, 10.5, 13, 4, 6.5, "bone_dk")    # 手 (銛を握る)
    sb(m, "forearm_l", 1, 4, 6.5, 8.5, 16, -1, 1, "bone")
    if opt == "b":
        m.box("forearm_l", 4.5, 5, -0.5, 5.5, 8.5, 0.5, "bone_dk")   # 棍棒の柄
        m.box("forearm_l", 3.5, 0, -1.5, 6.5, 5, 2.5, "brain")       # 棍棒の頭
    else:
        sb(m, "forearm_l", 1, 4, 6.5, 6.5, 8.5, -1, 1.5, "bone_dk")  # 垂れた手

    # 首と頭
    if opt == "c":
        m.box("head", -1.5, 24.5, 0, 1.5, 26.5, 2.5, "bone_dk")
    else:
        m.box("head", -1.5, 25, -1, 1.5, 26.5, 1, "bone_dk")
    m.box("head", -3.5, 26.5, sz0, 3.5, 33.5, sz1, "bone")
    m.box("head", -3.5, 30.5, sz1, 3.5, 31.5, sz1 + 0.5, "bone_dk")  # 眉
    if opt == "a":  # 珊瑚の冠
        m.box("head", -3.5, 33.5, -2.5, 3.5, 34, 2.5, "coral_p")
        m.box("head", -2.5, 34, -1, -2, 36.5, -0.5, "coral_o")
        m.box("head", -3, 35, -1, -2.5, 36, -0.5, "coral_o")
        m.box("head", -0.5, 34, -1, 0.5, 37, 1, "coral_v")
        m.box("head", 2, 34, -0.5, 2.5, 36, 0.5, "coral_p")
        m.box("head", 2.5, 35, -0.5, 3, 35.5, 0.5, "coral_p")
    if opt == "b":  # 脳珊瑚の頭
        m.box("head", -3.5, 33.5, -3, 3.5, 34.5, 3, "brain")
        m.box("head", -2.5, 34.5, -2, -1, 35.5, -0.5, "brain")
        m.box("head", 0, 34.5, -1, 1.5, 35.5, 1, "brain")
        m.box("head", 2, 34.5, 0, 3, 35.5, 2, "brain")
    if opt == "c":  # 光るイソギンチャク
        m.box("head", -2.5, 33.5, 0, 2.5, 34, 3, "coral_p")
        for i, x0 in enumerate((-2.5, -1.5, -0.5, 0.5, 1.5)):
            z0 = 0.5 if i % 2 == 0 else 1.5
            m.box("head", x0, 34, z0, x0 + 0.5, 36, z0 + 0.5, "coral_v")
            m.box("head", x0, 36, z0, x0 + 0.5, 36.5, z0 + 0.5, "seaglass")
    return m


# ---------------------------------------------------------------- 銛の受け口の合成 (表示用)

def mob_faces(geo_game, tex, gl, spear=None, socket="socket_main"):
    """珊瑚憑きの面 (+ 受け口に付けた銛の面)。銛は別の geo・別のテクスチャのまま描く。"""
    faces = vx.load_geo(geo_game, tex, gl)
    if spear is None:
        return faces
    sp_geo, sp_tex, sp_gl = spear
    g = copy.deepcopy(geo_game)
    gb = g["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == socket)
    for b in gb["bones"]:
        b["cubes"] = []  # 体の立方体は銛の面の計算では使わない
    sb_ = copy.deepcopy(sp_geo)["minecraft:geometry"][0]["bones"]
    for b in sb_:
        b["name"] = "spear_" + b["name"]
        b["parent"] = socket if "parent" not in b else "spear_" + b["parent"]
        b["pivot"] = [a + c for a, c in zip(b["pivot"], p)]
        for c in b.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
    gb["bones"] += sb_
    return faces + vx.load_geo(g, sp_tex, sp_gl)


def game_render(faces, yaw, pitch):
    """ゲーム内の見た目 (x の鏡写し) に合わせて描く。"""
    return vx.render(faces, yaw=-yaw, pitch=pitch, size=VIEW).transpose(FLIP)


THRALL_VIEWS = [(205, 18), (270, 8), (25, 18)]  # 正面3/4, 横, 背面3/4


# ---------------------------------------------------------------- 磯走り

LEG_S = {  # 脚の前後 (z0, z1)、胴から膝の x、足先の x、上の節の高さ、下の節の高さ
    "a": dict(zs=((-3, -2), (-0.5, 0.5), (2, 3)), th=0.0, xin=5, xk=6.5, xo=7.5, uy=(2, 3.5), ly=(0, 2), lx=6.5),
    "b": dict(zs=((-3, -2), (-0.5, 0.5), (2, 3)), th=0.0, xin=4, xk=5.5, xo=6.5, uy=(2, 3.5), ly=(0, 2), lx=5.5),
    "c": dict(zs=((-3, -2.5), (-1.5, -1), (0, 0.5), (1.5, 2)), th=0.0, xin=3.5, xk=6.5, xo=6.5, uy=(3, 3.5), ly=(0, 3), lx=6),
}


def scuttler_model(opt: str):
    m = Model(f"reef_scuttler_{opt}")
    m.bone("body", (0, 3, 0))
    m.bone("leg_r", (-5, 3, 0), "body")
    m.bone("leg_l", (5, 3, 0), "body")
    m.bone("claw_r", (-4, 3, 4), "body")
    m.bone("claw_l", (4, 3, 4), "body")
    L = LEG_S[opt]
    if opt == "a":
        m.box("body", -5, 3, -4, 5, 5.5, 4, "crab")
        m.box("body", -4.5, 5.5, -3.5, 4.5, 6, 3.5, "crab")
        m.box("body", -2, 6, -3, 2, 7, 0, "ruin")          # 割れた瓦の小さな塔
        fzb, top_front = 4, 3.5
    elif opt == "b":
        m.box("body", -4, 3, -3, 4, 5.5, 3, "crab")
        m.box("body", -3.5, 5.5, -2.5, 3.5, 6, 2.5, "crab")
        m.box("body", -3.5, 2.5, -8, 3.5, 5.5, -3, "clay")  # 壺の胴
        m.box("body", -3, 5.5, -7.5, 3, 6.5, -3.5, "clay")  # 壺の肩
        m.box("body", -3.5, 6.5, -8, 0, 7, -3, "clay")      # 壺の縁 (左)
        m.box("body", 0.5, 6.5, -8, 3.5, 7, -6, "clay")     # 壺の縁 (右、欠けている)
        fzb, top_front = 3, 2.5
    else:
        m.box("body", -3.5, 3, -3, 3.5, 5, 3.5, "crab")
        for x0, z0 in ((-2.5, -2), (-0.5, -2), (1.5, -2), (-1.5, 0), (0.5, 0), (2.5, 0), (-2.5, 2), (-0.5, 2), (1.5, 2)):
            m.box("body", x0, 5, z0, x0 + 0.5, 6.5, z0 + 0.5, "crab_dk")  # 棘
        m.box("body", -2, 5, 0.5, -1, 5.5, 1.5, "kelp")     # 海藻の房
        m.box("body", 1, 5, -0.5, 2, 5.5, 0.5, "kelp")
        fzb, top_front = 3.5, 3.5

    # 目の柄と目 (光る)
    top_y = 5 if opt == "c" else 6
    eye_base = 7.5 if opt == "c" else 8.5
    for sx in (-1, 1):
        sb(m, "body", sx, 1.5, 2.5, top_y, eye_base, top_front - 1, top_front, "crab")
        sb(m, "body", sx, 1.5, 2.5, eye_base, eye_base + 1, top_front - 1, top_front, "seaglass")

    # 脚 (両側)
    for sx in (-1, 1):
        lb = "leg_r" if sx < 0 else "leg_l"
        for z0, z1 in L["zs"]:
            sb(m, lb, sx, L["xin"], L["xk"], L["uy"][0], L["uy"][1], z0, z1, "crab_dk")
            sb(m, lb, sx, L["lx"], L["xo"], L["ly"][0], L["ly"][1], z0, z1, "crab_dk")

    # はさみ (前の両角から前へ)
    for sx in (-1, 1):
        cb = "claw_r" if sx < 0 else "claw_l"
        if opt == "c":
            zf = 3.5
            sb(m, cb, sx, 3, 4.5, 3, 4, zf, zf + 1, "crab")
            sb(m, cb, sx, 3, 4.5, 2.5, 3.5, zf + 1, zf + 1.5, "crab")
            sb(m, cb, sx, 3.5, 5, 3.5, 4, zf + 1.5, zf + 3, "crab")
            sb(m, cb, sx, 3, 4.5, 2, 2.5, zf + 1.5, zf + 3, "crab_dk")
        else:
            zf = fzb
            sb(m, cb, sx, 3.5, 5.5, 3, 4.5, zf, zf + 2, "crab")
            sb(m, cb, sx, 3.5, 5.5, 2.5, 4, zf + 2, zf + 3, "crab")
            sb(m, cb, sx, 4, 6.5, 3.5, 4.5, zf + 3, zf + 5.5, "crab")
            sb(m, cb, sx, 3.5, 6, 2, 3, zf + 3, zf + 5.5, "crab_dk")
    if opt == "b":
        m.glowfn = lambda X, Y, Z, f, mat: mat == "clay" and f == "up" and -0.5 <= X <= 0.5 and Z <= -6
    return m


# ---------------------------------------------------------------- 泡吹き

def fib(n):
    out, golden = [], math.pi * (3 - math.sqrt(5))
    for i in range(n):
        y = 1 - 2 * (i + 0.5) / n
        r = math.sqrt(max(0.0, 1 - y * y))
        th = golden * i
        out.append((math.cos(th) * r, y, math.sin(th) * r))
    return out


def ball(c, R, mat_fn):
    cx, cy, cz = c
    n = int(math.ceil(R)) + 1
    out = {}
    for x in range(cx - n, cx + n):
        for y in range(cy - n, cy + n):
            for z in range(cz - n, cz + n):
                px, py, pz = x + 0.5 - cx, y + 0.5 - cy, z + 0.5 - cz
                if px * px + py * py + pz * pz <= R * R:
                    out[(x, y, z)] = mat_fn(x, y, z)
    return out


def add_cells(m, cells, bone="body"):
    """1 単位のセル (x, y, z: 最小の隅) を、x 方向に同じ材質で続く所をまとめて箱にする。"""
    keys = sorted(cells, key=lambda k: (k[1], k[2], k[0]))
    i = 0
    while i < len(keys):
        x, y, z = keys[i]
        mat = cells[keys[i]]
        x1, j = x + 1, i + 1
        while j < len(keys) and keys[j] == (x1, y, z) and cells[keys[j]] == mat:
            x1 += 1
            j += 1
        m.box(bone, x, y, z, x1, y + 1, z + 1, mat)
        i = j


def puff_extra(kind, c, s):
    """泡吹きの顔 (前の面の目と口)、ボックス状の殻の板目。"""
    def eye_c(u, v, w):  # 月明かりのクラゲ (C) の 3 つの光る目
        return w >= 3.5 and -1.5 <= u <= 1.5 and 1 <= v <= 2

    def extra(X, Y, Z, f, mat):
        if kind == "b" and mat == "coral_o":
            if math.floor(X * 2) % 6 == 0 or math.floor(Y * 2) % 6 == 0:
                return RAMP["coral_o"][0]
            return None
        if f != "south":
            return None
        u, v, w = (X - c[0]) / s, (Y - c[1]) / s, (Z - c[2]) / s
        if w < 3.5:
            return None
        if kind == "a":
            if (-3.5 <= u <= -1 or 1 <= u <= 3.5) and 0.5 <= v <= 3.5:
                if hl_a(u, v, w):
                    return RAMP["seaglass"][3]
                return RAMP["dark"][0]
            if -1 <= u <= 1 and -3 <= v <= -2.5:
                return RAMP["dark"][0]
        if kind == "b" and (-4 <= u <= -1.5 or 1.5 <= u <= 4) and -0.5 <= v <= 2.5:
            return RAMP["dark"][0]
        if kind == "c" and eye_c(u, v, w):
            return RAMP["seaglass"][2]
        return None

    def hl_a(u, v, w):  # 丸い目の白い光 (A)
        return w >= 3.5 and (-2.5 <= u <= -2 or 2 <= u <= 2.5) and 2.5 <= v <= 3

    def glow(X, Y, Z, f, mat):
        if kind == "c":
            if mat == "belly" and f == "up":
                return True  # 月明かりの縁
            if f == "south":
                u, v, w = (X - c[0]) / s, (Y - c[1]) / s, (Z - c[2]) / s
                return eye_c(u, v, w)
        if kind == "a" and f == "south":
            u, v, w = (X - c[0]) / s, (Y - c[1]) / s, (Z - c[2]) / s
            return hl_a(u, v, w)
        return False
    return extra, glow


def puffer_a(s):
    R = 7.5 * s
    c = (0, int(round(R + 1.5)), 0)
    cells = ball(c, R, lambda x, y, z: "belly" if y < c[1] - 0.2 * R else "skin")
    L = 2 if s < 1.2 else 3
    for d in fib(40):
        for k in range(L):
            p = [c[i] + d[i] * (R + 0.5 + k) for i in range(3)]
            cells.setdefault(tuple(math.floor(v) for v in p), "bone")  # 棘
    for sg in (-1, 1):
        for z in (-1, 0):
            cells.setdefault((7 if sg > 0 else -8, c[1] - 2, z), "skin")  # ひれ
    return c, cells


def puffer_b(s):
    hx, hy, hz = 7 * s, 6 * s, 6 * s
    cut = 16 * s
    c = (0, int(round(hy + 2)), 0)
    cells = {}
    for x in range(-int(hx) - 2, int(hx) + 2):
        for y in range(c[1] - int(hy) - 2, c[1] + int(hy) + 2):
            for z in range(-int(hz) - 2, int(hz) + 2):
                px, py, pz = x + 0.5, y + 0.5 - c[1], z + 0.5
                if abs(px) <= hx and abs(py) <= hy and abs(pz) <= hz and abs(px) + abs(py) + abs(pz) <= cut:
                    cells[(x, y, z)] = "coral_o"
    sac = ball((0, c[1] - 2, int(hz) + 1), 3.5 * s, lambda x, y, z: "seaglass")  # 泡の袋 (光る)
    for k, v in sac.items():
        cells.setdefault(k, v)
    for sg in (-1, 1):
        for z in (-2, -1):
            cells.setdefault((7 if sg > 0 else -8, c[1], z), "coral_o")  # ひれ
    return c, cells


def puffer_c(s):
    R = 5.5 * s
    c = (0, int(round(R + 9 * s)), 0)
    cells = ball(c, R, lambda x, y, z: "belly" if y >= c[1] + 0.35 * R else "tendril")
    for i, (ang, L) in enumerate(zip(range(0, 360, 45), (9, 11, 8, 12, 9, 10, 8, 11))):
        r = 3.5 * s
        x = round(r * math.cos(math.radians(ang)))
        z = round(r * math.sin(math.radians(ang)))
        n = int(round(L * s))
        y0 = c[1] - int(R) - 1
        for k in range(n):
            key = (x, y0 - k, z)
            if key not in cells:
                cells[key] = "seaglass" if k >= n - 2 else "tendril"  # 触手の先が光る
    cells.setdefault((0, c[1] - int(R) - 1, 0), "tendril")
    for d in fib(18):
        p = [c[i] + d[i] * (R + 0.5) for i in range(3)]
        cells.setdefault(tuple(math.floor(v) for v in p), "skin")
    return c, cells


def drop_enclosed(cells):
    """6 方向すべて別のセルに囲まれた内側のセルを外す (見えず、埋まった立方体になるため)。"""
    dirs = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    inner = [k for k in cells if all((k[0] + dx, k[1] + dy, k[2] + dz) in cells for dx, dy, dz in dirs)]
    for k in inner:
        del cells[k]
    return len(inner)


def build_puffer(kind, s):
    m = Model(f"bubble_puffer_{kind}_{'x' if s > 1 else 'n'}")
    m.bone("body", (0, 0, 0))
    c, cells = {"a": puffer_a, "b": puffer_b, "c": puffer_c}[kind](s)
    drop_enclosed(cells)
    add_cells(m, cells)
    m.extra, m.glowfn = puff_extra(kind, c, s)
    return m


# ---------------------------------------------------------------- 書き出しと描画

def report(label, r):
    print(f"{label}: 箱 {r['n']}、重なり {r['overlaps'] or 'なし'}、埋まり {r['buried'] if r['buried'] is not None else '(検査省略)'}")


def run_spear():
    folder = MOBS / "coral_spear"
    r = export(build_spear(), folder, root=False)
    report("coral_spear", r)
    faces = vx.load_geo(r["geo"], r["tex"], r["gl"])
    for name, yaw in (("front34", 30), ("side", 120), ("back34", 210)):
        vx.render(faces, yaw=yaw, pitch=12, size=VIEW).save(folder / f"render_{name}.png")
    return r


def run_thrall(spear_r):
    folder = MOBS / "coral_thrall"
    sp = (spear_r["geo"], spear_r["tex"], spear_r["gl"])
    sp_bleached = (spear_r["geo"], bleach(spear_r["tex"]), spear_r["gl"])
    cols = []
    labels = {"a": "A 細身・珊瑚の冠", "b": "B 重装・脳珊瑚", "c": "C 前かがみ・海藻"}
    for opt in "abc":
        r = export(thrall_model(opt), folder / opt)
        report(f"coral_thrall/{opt}", r)
        rows = []
        for name, (yaw, pitch) in zip(("front34", "side", "back34"), THRALL_VIEWS):
            img = game_render(mob_faces(r["geo"], r["tex"], r["gl"], sp), yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        statue = game_render(mob_faces(r["geo"], bleach(r["tex"]), r["gl"], sp_bleached), 205, 10)
        statue.save(folder / opt / "render_statue.png")
        rows.append(statue)
        cols.append((labels[opt], rows))
    sheet = vx.sheet("coral_thrall ラフ (珊瑚憑き + 珊瑚の銛)", cols,
                     note="行: 正面3/4 / 横 / 背面3/4 / 石膏化 (潮が満ちて凍った姿、推奨候補の確認用)")
    sheet.save(REVIEW / "coral_thrall.png")
    return cols


def run_scuttler():
    folder = MOBS / "reef_scuttler"
    cols = []
    labels = {"a": "A 瓦の殻", "b": "B 壺を背負う", "c": "C 棘のクモガニ"}
    for opt in "abc":
        r = export(scuttler_model(opt), folder / opt)
        report(f"reef_scuttler/{opt}", r)
        rows = []
        for name, yaw, pitch in (("front34", 205, 18), ("side", 270, 8), ("back34", 25, 18), ("top", 205, 70)):
            img = game_render(vx.load_geo(r["geo"], r["tex"], r["gl"]), yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        cols.append((labels[opt], rows))
    vx.sheet("reef_scuttler ラフ (磯走り)", cols,
             note="行: 正面3/4 / 横 / 背面3/4 / 真上 (小さく速い群れ。幅約 0.9 ブロック)").save(REVIEW / "reef_scuttler.png")
    return cols


def run_puffer():
    folder = MOBS / "bubble_puffer"
    cols = []
    labels = {"a": "A まるい棘玉", "b": "B 箱型の鎧と泡袋", "c": "C 月明かりのクラゲ混じり"}
    for kind in "abc":
        r = export(build_puffer(kind, 1.0), folder / kind)
        report(f"bubble_puffer/{kind} (通常)", r)
        ri = export(build_puffer(kind, 1.6), folder / kind / "inflated")
        report(f"bubble_puffer/{kind} (膨らみ)", ri)
        rows = []
        for name, yaw, pitch, rr in (("front34", 205, 18, r), ("side", 270, 8, r),
                                     ("inflated_front34", 205, 18, ri), ("inflated_side", 270, 8, ri)):
            img = game_render(vx.load_geo(rr["geo"], rr["tex"], rr["gl"]), yaw, pitch)
            sub = folder / kind / ("inflated" if name.startswith("inflated") else "")
            sub.mkdir(parents=True, exist_ok=True)
            img.save(sub / f"render_{name.replace('inflated_', '')}.png")
            rows.append(img)
        cols.append((labels[kind], rows))
    vx.sheet("bubble_puffer ラフ (泡吹き: 通常と膨らみ)", cols,
             note="行: 通常の正面3/4 / 通常の横 / 膨らみ (棘が出て約1.6倍) の正面3/4 / 膨らみの横").save(REVIEW / "bubble_puffer.png")
    return cols


def main():
    spear_r = run_spear()
    run_thrall(spear_r)
    run_scuttler()
    run_puffer()


if __name__ == "__main__":
    main()
