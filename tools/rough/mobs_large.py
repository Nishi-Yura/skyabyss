"""大型 Mob のラフ 3案: 水路の顎 (waterway_maw) と 鯨骨の番兵長 (whalebone_captain).

実行 (リポジトリ直下): python tools/rough/mobs_large.py
出力:
  docs/roughs/<subject>/<a|b|c>/geo.json, texture.png, glowmask.png
  docs/roughs/<subject>.png  (列 A/B/C、各列は 3/4 前・3/4 後ろ・動作)
  docs/roughs/mobs_large.md  (コンセプトと重なり検査の結果)

決まり: 1単位 = 1画素。静止姿勢は全骨の回転 0 (重なり検査が正確に効くように)。
動作の姿勢は描画時だけ pose で骨を回す (geo.json には書かない)。
面の画像は「外から見た向き」(画像の上の行 = 面の上、左の列 = 外から見て左)。
光は左上から。面の明暗は、上から下へのディザ付きの段階的なグラデーションで描く。
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from voxel import check_overlaps, load_geo, render, sheet  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "roughs"
TW = 256  # テクスチャの幅 (画素)。高さは詰めた量で決まる

ALL = {"north", "south", "east", "west", "up", "down"}
FRONT = {"north"}
BACK = {"south"}
SIDES = {"east", "west"}
WALLS = {"north", "south", "east", "west"}
TOP = {"up"}
BOT = {"down"}


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def _ix(v, n):
    return v + n if v < 0 else v


def fill(img, x0, y0, x1, y1, c, a=255):
    """面の画素を塗る (x は左から、y は上から。負の値は端から。両端を含む)。"""
    h, w = img.shape[:2]
    x0, x1 = sorted((_ix(x0, w), _ix(x1, w)))
    y0, y1 = sorted((_ix(y0, h), _ix(y1, h)))
    x0, y0 = max(x0, 0), max(y0, 0)
    x1, y1 = min(x1, w - 1), min(y1, h - 1)
    if x1 < x0 or y1 < y0:
        return
    img[y0:y1 + 1, x0:x1 + 1, :3] = c
    img[y0:y1 + 1, x0:x1 + 1, 3] = a


def px(img, x, y, c):
    fill(img, x, y, x, y, c)


def glowrect(img, gimg, x0, y0, x1, y1, c):
    fill(img, x0, y0, x1, y1, c)
    fill(gimg, x0, y0, x1, y1, c)


# ---------------------------------------------------------------- 面の描画

def base_paint(img, name, w, h, cube, pal):
    """面の下地。上から下へ段階的に明るさを変え、段の境目はディザで混ぜる。
    光は左上から: 左の列と上の行を少し明るく、右と下を暗くする。"""
    key = cube["mat"]
    if isinstance(key, dict):
        grp = "up" if name == "up" else "down" if name == "down" else "side"
        key = key.get(grp, key.get("all"))
    B = pal[key]
    if name == "up":
        hi, lo = 1.24, 1.0
    elif name == "down":
        hi, lo = 0.92, 0.6
    else:
        hi, lo = 1.22, 0.7
    steps = 10
    for y in range(h):
        t = y / max(1, h - 1)
        k = hi * (1 - t) + lo * t
        s = k * steps
        base = int(np.floor(s))
        frac = s - base
        for x in range(w):
            # 境目の混ぜ方: 市松のディザ (決まった配置)
            kk = base + (1 if ((x + y) % 2) < frac * 2 else 0)
            kv = kk / steps
            # 左の列は少し明るく (光は左上)
            if x == 0:
                kv *= 1.06
            img[y, x, :3] = shade(B, kv)
            img[y, x, 3] = 255
    if cube["outline"] and w >= 3 and h >= 3:
        dk = shade(B, 0.42)
        img[0, :, :3] = dk
        img[-1, :, :3] = dk
        img[:, 0, :3] = shade(B, 0.6)
        img[:, -1, :3] = dk
    # 面の上の縁の明かり (1 画素。角の所は外す)
    if name not in ("down",) and w >= 5 and h >= 5:
        img[1, 2:-2, :3] = shade(B, 1.3)
    return B


def paint_face(cube, name, w, h, pal):
    img = np.zeros((h, w, 4), np.uint8)
    gimg = np.zeros((h, w, 4), np.uint8)
    base_paint(img, name, w, h, cube, pal)
    for fn in cube["paint"]:
        fn(img, gimg, name, w, h, pal)
    return img, gimg


def F(faces, fn):
    """fn(img, gimg, w, h, pal) を、指定の面にだけ当てる。"""
    def run(img, gimg, name, w, h, pal):
        if name in faces:
            fn(img, gimg, w, h, pal)
    return run


def seam_row(c, y):
    return lambda img, gimg, w, h, pal: fill(img, 1, _ix(y, h), w - 2, _ix(y, h), pal[c])


def seam_mid(c):
    return lambda img, gimg, w, h, pal: fill(img, 1, h // 2, w - 2, h // 2, pal[c])


def ribs(c, step=3, start=1):
    def fn(img, gimg, w, h, pal):
        for x in range(start, w - 1, step):
            fill(img, x, 1, x, h - 2, pal[c])
    return fn


def scales(c, step=4, off=0):
    """決まった位置の鱗 (ノイズではない)。斜めに並べる。"""
    def fn(img, gimg, w, h, pal):
        for y in range(1, h - 1):
            for x in range(1, w - 1):
                if (x + y + off) % step == 0:
                    img[y, x, :3] = pal[c]
    return fn


def mosaic(c, step=3, k=1.4):
    """鱗の光: 決まった配置の明るい点を、列ごとに半歩ずらして並べる。"""
    def fn(img, gimg, w, h, pal):
        for x in range(1, w - 1):
            off = 1 if (x // step) % 2 else 0
            for y in range(1 + off, h - 1, step):
                img[y, x, :3] = shade(pal[c], k)
    return fn


def weave(c, step=3):
    def fn(img, gimg, w, h, pal):
        for y in range(1, h - 1):
            if y % step == 1:
                img[y, 1:-1, :3] = shade(pal[c], 1.15)
    return fn


def spots(c, pts):
    def fn(img, gimg, w, h, pal):
        for x, y in pts:
            px(img, x, y, pal[c])
    return fn


def rivets(c, pts):
    """鋲: 1 画素の明るい点 (縁からの位置)。"""
    def fn(img, gimg, w, h, pal):
        for x, y in pts:
            px(img, x, y, shade(pal[c], 1.35))
    return fn


def tatter(depths):
    """布の裾を決まった深さで切る (alpha を 0 に)。"""
    def fn(img, gimg, w, h, pal):
        for x in range(w):
            d = depths[x % len(depths)]
            if d > 0:
                img[max(0, h - d):, x, 3] = 0
                gimg[max(0, h - d):, x, 3] = 0
    return fn


def glow_on(faces, rects, key="glow"):
    """光る矩形 (面の画素座標)。周りを暗い縁で囲み、目の窪みに見せる。"""
    def fn(img, gimg, w, h, pal):
        for x0, y0, x1, y1 in rects:
            fill(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, shade(pal["dark"], 0.6))
            glowrect(img, gimg, x0, y0, x1, y1, pal[key])
    return F(faces, fn)


def glow_full(key="glow"):
    return lambda img, gimg, w, h, pal: glowrect(img, gimg, 0, 0, w - 1, h - 1, pal[key])


# ---------------------------------------------------------------- 骨格 (リグ)

class Rig:
    def __init__(self):
        self.bones = []
        self.cubes = {}

    def bone(self, name, pivot, parent=None):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        self.cubes[name] = []

    def box(self, bone, o, s, mat, paint=(), outline=True):
        self.cubes[bone].append(dict(o=tuple(float(v) for v in o), s=tuple(float(v) for v in s),
                                     mat=mat, paint=list(paint), outline=outline))


def face_dims(s):
    sx, sy, sz = s
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}


def pack(sizes, tw=TW):
    pos = {}
    x = y = rowh = 0
    for key, (w, h) in sorted(sizes.items(), key=lambda kv: (-kv[1][1], -kv[1][0])):
        if x + w > tw:
            x, y, rowh = 0, y + rowh, 0
        pos[key] = (x, y)
        x += w + 1
        rowh = max(rowh, h + 1)
    th = ((y + rowh + 15) // 16) * 16
    return pos, th


def build(rig, pal, ident, vis):
    faces = []
    for bone in rig.bones:
        for ci, cube in enumerate(rig.cubes[bone["name"]]):
            for fname, (w, h) in face_dims(cube["s"]).items():
                img, gimg = paint_face(cube, fname, int(w), int(h), pal)
                faces.append((bone["name"], ci, fname, int(w), int(h), img, gimg))
    pos, th = pack({i: (f[3], f[4]) for i, f in enumerate(faces)})
    tex = np.zeros((th, TW, 4), np.uint8)
    gl = np.zeros((th, TW, 4), np.uint8)
    for i, (_, _, _, w, h, img, gimg) in enumerate(faces):
        u, v = pos[i]
        tex[v:v + h, u:u + w] = img
        gl[v:v + h, u:u + w] = gimg
    bones_out = []
    for bone in rig.bones:
        b = {"name": bone["name"], "pivot": bone["pivot"]}
        if "parent" in bone:
            b["parent"] = bone["parent"]
        cubes = []
        for ci, cube in enumerate(rig.cubes[bone["name"]]):
            uvs = {}
            for i, f in enumerate(faces):
                if f[0] == bone["name"] and f[1] == ci:
                    u, v = pos[i]
                    uvs[f[2]] = {"uv": [u, v], "uv_size": [f[3], f[4]]}
            cubes.append({"origin": list(cube["o"]), "size": list(cube["s"]), "uv": uvs})
        b["cubes"] = cubes
        bones_out.append(b)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{ident}",
                "texture_width": TW,
                "texture_height": th,
                "visible_bounds_width": vis[0],
                "visible_bounds_height": vis[1],
                "visible_bounds_offset": [0, vis[1] / 2, 0],
            },
            "bones": bones_out,
        }],
    }
    return geo, tex, gl


# ---------------------------------------------------------------- 鯨骨の番兵長

CAPTAIN = {
    # A 王道の騎士: 丸い肩当て、長い外套、まっすぐな鉾
    "a": dict(hw=8, hd=5, yl=14, th=14, aw=4, lw=6, pw=6, ph=6, hh=5, ht=8,
              crest=2, horns=False, crown=False, spine=False, robe=False, cape=True,
              cape_w=[3, 4, 4, 3], cape_h=[16, 13, 17, 14], tat=[0, 2, 1, 4, 0, 3, 1],
              shards=False, plates=False),
    # B 重厚: 巨大な肩の板、鯨骨の背骨、角の兜、外套なし
    "b": dict(hw=10, hd=7, yl=12, th=16, aw=6, lw=7, pw=9, ph=9, hh=6, ht=9,
              crest=0, horns=True, crown=False, spine=True, robe=False, cape=False,
              cape_w=[], cape_h=[], tat=[], shards=False, plates=True),
    # C 細長い異形: 三本の冠、長い外套 (ローブ)、胸の結晶
    "c": dict(hw=6, hd=4, yl=16, th=13, aw=4, lw=4, pw=4, ph=4, hh=5, ht=8,
              crest=0, horns=False, crown=True, spine=False, robe=True, cape=False,
              cape_w=[], cape_h=[], tat=[0, 3, 0, 1, 2, 0, 4], shards=True, plates=False),
}

CAPTAIN_PAL = {
    "a": dict(bone="F2E8D0", bone_dk="B3A584", cloth="46597E", iron="8A93A0", iron_dk="5C6370",
              trim="4B5064", moss="5E8A46", glow="7FFFFF", dark="262A36", stone="8E969B", crystal="4FE0F5"),
    "b": dict(bone="E2D6BA", bone_dk="9C8F70", cloth="2C3446", iron="6E7580", iron_dk="454B56",
              trim="353B4A", moss="4F7A3E", glow="8FFFEA", dark="1E222C", stone="7A8186", crystal="4FE0F5"),
    "c": dict(bone="D6E2DE", bone_dk="93A3A0", cloth="5A4A72", iron="737E8C", iron_dk="4A5260",
              trim="3A4358", moss="3F7A5E", glow="70F8FF", dark="232838", stone="5C6670", crystal="4FE0F5"),
}


def captain(opt):
    p = CAPTAIN[opt]
    hw, hd, yl, th, aw, lw = p["hw"], p["hd"], p["yl"], p["th"], p["aw"], p["lw"]
    pw, ph, hh, ht = p["pw"], p["ph"], p["hh"], p["ht"]
    yp = yl + 5          # 胴の下 (腰の上端)
    yt = yp + th         # 肩の上端
    yh = yt + 2          # 兜の下
    top = yh + ht        # 兜の上端
    xa = hw + pw // 2    # 腕の中心 (右は -xa)
    shoulder_y = yt - ph  # 腕の上端 (肩当ての下)
    torso_mat = {"a": "iron_dk", "b": "stone", "c": "trim"}[opt]
    shards = [F(ALL, glow_full())]

    R = Rig()
    R.bone("root", (0, 0, 0))
    R.bone("right_leg", (-lw // 2 - 1, yl, 0), "root")
    R.bone("left_leg", (lw // 2 + 1, yl, 0), "root")
    R.bone("body", (0, yp, 0), "root")
    R.bone("cape", (0, yt, hd), "body")
    R.bone("head", (0, yh, 0), "body")
    R.bone("right_arm", (-xa, shoulder_y, 0), "body")
    R.bone("right_forearm", (-xa, yl, 0), "right_arm")
    R.bone("right_hand", (-xa, 7, 0), "right_forearm")
    R.bone("pike", (-xa, 7, 0), "right_hand")
    R.bone("left_arm", (xa, shoulder_y, 0), "body")
    R.bone("left_forearm", (xa, yl, 0), "left_arm")
    R.bone("left_hand", (xa, 7, 0), "left_forearm")

    # 脚 (ブーツ、脚、前の裾の鎧)
    for side, x0 in (("right_leg", -lw - 1), ("left_leg", 1)):
        R.box(side, (x0, 4, -3), (lw, yl - 4, 6), "cloth", [F(ALL, weave("cloth", 4))])
        R.box(side, (x0, 0, -4), (lw, 4, 7), "trim", [F(SIDES, seam_row("iron_dk", 1))])
        if not p["robe"]:
            R.box(side, (x0, yl - 5, -5), (lw, 5, 2), torso_mat, [F(FRONT, ribs("iron_dk", 3, 1))])
    # 腰
    R.box("body", (-hw + 2, yl, -hd + 1), (2 * hw - 4, yp - yl, 2 * hd - 2), "trim",
          [F(FRONT, seam_mid("iron_dk"))])
    # 胴
    R.box("body", (-hw, yp, -hd), (2 * hw, th, 2 * hd), torso_mat,
          [F(SIDES, seam_mid("iron_dk")), F(BACK, weave("iron", 3))])
    # 胸当て (鯨骨の板)
    R.box("body", (-hw + 2, yp + 2, -hd - 1), (2 * hw - 4, th - 6, 1), "bone",
          [F(FRONT, ribs("bone_dk", 5, 3)), F(FRONT, seam_mid("bone_dk")),
           F(FRONT, spots("bone_dk", [(2, 2), (3, 2)]))])
    if opt == "c":
        R.box("body", (-3, yp + 4, -hd - 2), (2, 4, 1), "crystal", shards)
        R.box("body", (2, yp + 6, -hd - 2), (2, 5, 1), "crystal", shards)
    if p["spine"]:
        # 鯨骨の背骨 (背中に、間を空けて並ぶ節。胴の背に接する)
        for k, y in enumerate(range(yp + 1, yt - 2, 3)):
            R.box("body", (-1, y, hd), (2, 2, 2), "bone",
                  [F(ALL, spots("bone_dk", [(0, 0)]))])
    # 腰の布 (C の外套は胴と脚の前後に)
    if p["robe"]:
        R.box("body", (-hw + 1, 4, -4), (2 * hw - 2, yl - 4, 1), "cloth",
              [F(FRONT, weave("cloth", 3)), F(ALL, tatter(p["tat"]))])
        R.box("body", (-hw + 1, 4, 3), (2 * hw - 2, yl - 4, 1), "cloth",
              [F(BACK, weave("cloth", 3)), F(ALL, tatter(p["tat"][::-1]))])
    # 首
    R.box("body", (-3, yt, -3), (6, yh - yt, 6), "trim")

    # 肩当て (左右)。右肩は鉾の柄の後ろに収める (柄は z -3..-2)
    for sgn in (-1, 1):
        x0 = -(hw + pw) if sgn < 0 else hw
        zc, zs = (-1, hd + 1) if sgn < 0 else (-hd, 2 * hd)
        if opt == "a":
            # 丸い肩: 下の箱と、一回り小さい上の箱 (ドーム)
            R.box("body", (x0, shoulder_y, zc), (pw, ph - 2, zs), "bone",
                  [F(SIDES | FRONT | BACK, spots("bone_dk", [(1, 2), (3, 3)])), F(WALLS, seam_row("bone_dk", 3))])
            R.box("body", (x0 + 1, yt - 2, zc + 1), (pw - 2, 2, zs - 2), "bone",
                  [F(ALL, rivets("bone_dk", [(1, 0)]))])
        else:
            pat = [F(SIDES | FRONT | BACK, spots("bone_dk", [(1, 2), (3, 3), (-3, 2)])),
                   F(WALLS, seam_row("bone_dk", 3))]
            if opt == "b":
                pat.append(F(TOP, lambda i, g, w, h, pal: fill(i, 2, 2, w - 3, h - 3, pal["moss"])))
            R.box("body", (x0, shoulder_y, zc), (pw, ph, zs), "bone", pat)
            if opt == "b":
                R.box("body", (x0 + pw // 2 - 1, yt, zc + 1), (2, 3, 2), "bone")
                R.box("body", (x0 + pw // 2 - 1, yt + 3, zc + 1), (2, 2, 2), "bone")
        if opt == "c" and sgn > 0:
            R.box("body", (x0 + 1, yt, zc + 1), (2, 5, 2), "crystal", shards)

    # 腕 (右は鉾を持つ)
    for side, sgn in (("right", -1), ("left", 1)):
        xc = sgn * xa
        R.box(f"{side}_arm", (xc - aw // 2, yl, -2), (aw, shoulder_y - yl, 4), "iron",
              [F(WALLS, ribs("iron_dk", 2, 1))])
        R.box(f"{side}_forearm", (xc - aw // 2, 7, -2), (aw, yl - 7, 4), "iron_dk",
              [F(ALL, weave("iron", 3))])
        R.box(f"{side}_hand", (xc - aw // 2, 3, -2), (aw, 4, 4), "trim")

    # 鉾 (右手。柄はまっすぐ、穂は先細り)
    px0 = -xa - 1
    shaft_top = 36
    R.box("pike", (px0, 0, -3), (2, shaft_top, 1), "trim",
          [F(WALLS, seam_row("iron_dk", 12)), F(WALLS, seam_row("iron_dk", 24))])
    R.box("pike", (px0 - 2, shaft_top - 3, -3), (2, 1, 1), "iron")
    blade_mat = "crystal" if opt == "c" else "iron"
    blade_paint = [F(FRONT, lambda i, g, w, h, pal: fill(i, w // 2, 0, w // 2, h - 1, pal["bone"]))]
    if opt == "c":
        blade_paint.append(F(ALL, glow_full()))
    R.box("pike", (px0 - 1, shaft_top, -3), (4, 7, 1), blade_mat, blade_paint)

    # 頭 (兜)
    R.box("head", (-hh, yh, -hh), (2 * hh, ht, 2 * hh), "iron",
          [F(SIDES, seam_mid("iron_dk")), F(BACK, seam_mid("iron_dk"))])
    R.box("head", (-hh + 1, yh + 3, -hh - 1), (2 * hh - 2, 4, 1), "trim",
          [glow_on(FRONT, [(1, 1, 2, 1), (-3, 1, -2, 1)])])
    if p["crest"]:
        R.box("head", (-1, top, -4), (2, p["crest"], 6), "bone", [F(ALL, ribs("bone_dk", 2, 1))])
    if p["crown"]:
        R.box("head", (-hh + 1, top, -1), (2, 3, 2), "bone_dk")
        R.box("head", (-1, top, -2), (2, 5, 2), "crystal", shards)
        R.box("head", (hh - 3, top, -1), (2, 3, 2), "bone_dk")
    if p["horns"]:
        for s in (-1, 1):
            c1 = (hh if s > 0 else -(hh + 4), yh + 2, -2)
            c2 = (hh + 3 if s > 0 else -(hh + 6), yh + 5, -1)
            c3 = (hh + 5 if s > 0 else -(hh + 7), yh + 8, -1)
            R.box("head", c1, (4, 3, 4), "bone", [F(ALL, spots("bone_dk", [(1, 1), (2, 1)]))])
            R.box("head", c2, (3, 3, 3), "bone")
            R.box("head", c3, (2, 3, 2), "bone")

    # 外套 (A の裂けた布)
    if p["cape"]:
        R.box("cape", (-hw + 1, yp, hd), (2 * hw - 2, th, 1), "cloth", [F(FRONT | BACK, weave("cloth", 3))])
        x = -hw + 1
        for n, (wd, hgt) in enumerate(zip(p["cape_w"], p["cape_h"])):
            tat = p["tat"]
            R.box("cape", (x, yp - hgt, hd), (wd, hgt, 1), "cloth",
                  [F(BACK | SIDES, weave("cloth", 3)), F(ALL, tatter(tat[n:] + tat[:n]))])
            x += wd

    # 中心に置く。頭・胴の位置の基準は変えない (静止姿勢の骨の回転は 0)
    return R


CAPTAIN_POSE = {"raise": {"right_arm": (135, 0, 0), "pike": (-135, 0, 0)}}


# ---------------------------------------------------------------- 水路の顎

MAW = {
    "a": dict(cranium=18, brow=True, teeth=True, fins_side=False, lure=False),
    "b": dict(cranium=18, brow=True, teeth=True, fins_side=False, lure=False),
    "c": dict(cranium=18, brow=False, teeth=True, fins_side=True, lure=True),
}

MAW_PAL = {
    "a": dict(skin="3A8AA3", belly="D6E8D2", dark="1E5670", fin="4FA7BA", tooth="F6EEDA",
              bone="E6DCC2", glow="8CFFFF", ridge="2A6E85", lure="8CFFFF", gill="1A4A5C"),
    "b": dict(skin="32495C", belly="7A8A8C", dark="1C2836", fin="4A6272", tooth="E8DCBD",
              bone="CFBF96", glow="66E6D6", ridge="5E8058", lure="66E6D6", gill="141E2A"),
    "c": dict(skin="2C4A7A", belly="BCCBE6", dark="1C2445", fin="6E4E9A", tooth="F8F4EA",
              bone="D8E0EA", glow="AEFBFF", ridge="7C5AA8", lure="AEFBFF", gill="161B3A"),
}


def gill_lines(c, xs, y0, y1):
    """えらの切れ目 (縦の暗い線)。決まった列に、決まった範囲で引く。"""
    def fn(img, gimg, w, h, pal):
        for x in xs:
            fill(img, x, y0, x, y1, pal[c])
    return fn


def maw(opt):
    R = Rig()
    yc = 14  # 体の中心の高さ
    R.bone("root", (0, 0, 0))
    R.bone("seg0", (0, yc, -8), "root")
    R.bone("head", (0, yc, -8), "seg0")
    R.bone("jaw", (0, 5, -8), "head")  # 下顎の蝶番 (後ろ上)
    R.bone("seg1", (0, yc, 0), "seg0")
    R.bone("seg2", (0, yc, 8), "seg1")
    R.bone("seg3", (0, yc, 16), "seg2")
    R.bone("seg4", (0, yc, 24), "seg3")
    R.bone("seg5", (0, yc, 32), "seg4")
    R.bone("tail", (0, yc, 40), "seg5")

    skin = {"up": "skin", "side": "skin", "down": "belly"}
    dark = {"up": "dark", "side": "skin", "down": "dark"}

    # 頭蓋: 後ろの大きな箱 (cranium) から、段を付けて細くなる吻 (snout)
    eyes_c = [F(SIDES, lambda i, g, w, h, pal: _eye_socket(i, g, w, h, pal, opt))]
    skin_hl = [F(ALL, mosaic("skin", 3, 1.3))]
    R.box("head", (-9, 10, -22), (18, 14, 14), skin,
          eyes_c + [F(SIDES, _gills)] + skin_hl)
    R.box("head", (-7, 10, -30), (14, 13, 8), skin, [F(SIDES, seam_mid("dark"))] + skin_hl)
    R.box("head", (-5, 10, -36), (10, 11, 6), skin, [F(SIDES, seam_mid("dark"))] + skin_hl)
    # 眉の稜線 (頭蓋の上)
    if MAW[opt]["brow"]:
        R.box("head", (-8, 24, -20), (16, 2, 8), dark,
              [F(FRONT, ribs("dark", 4, 2)), F(TOP, spots("ridge", [(3, 1), (9, 2)]))])
    # 上顎: 頭の下、縁に歯の列 (上の歯は縁にのみ置く)
    R.box("head", (-6, 7, -36), (12, 3, 28), {"up": "belly", "side": "skin", "down": "dark"},
          [F(SIDES, seam_row("dark", 1))])
    for z in (-34, -30, -26, -22, -18, -14, -10):
        R.box("head", (5, 5, z), (1, 2, 1), "tooth")
        R.box("head", (-6, 5, z), (1, 2, 1), "tooth")
    # 下顎: 蝶番から前へ。開くと先が下がる (蝶番に付いたまま)
    R.box("jaw", (-4, 2, -34), (8, 3, 26), {"up": "dark", "side": "skin", "down": "belly"},
          [F(SIDES, mosaic("skin", 3, 1.3))])
    for x in (-2, 2):
        for z in (-32, -24, -16):
            R.box("jaw", (x, 5, z), (1, 2, 1), "tooth")

    if MAW[opt]["lure"]:
        # 提灯: 額の先から細い柄で垂れる (頭蓋の上に接する)
        R.box("head", (-1, 26, -18), (2, 4, 2), "dark")
        R.box("head", (-2, 30, -19), (4, 4, 4), "lure", [F(ALL, glow_full())])

    # 体 (節ごとに細くなる。前の節は頭を受ける)
    segs = [
        ("seg0", (-7, 7, -8), (14, 14, 8)),
        ("seg1", (-6, 8, 0), (12, 12, 8)),
        ("seg2", (-5, 9, 8), (10, 10, 8)),
        ("seg3", (-4, 10, 16), (8, 8, 8)),
        ("seg4", (-3, 11, 24), (6, 6, 8)),
        ("seg5", (-2, 12, 32), (4, 4, 8)),
    ]
    for name, o, s in segs:
        pat = [F(WALLS, mosaic("skin", 3, 1.3)), F(WALLS, scales("dark", 4, 0))]
        if name == "seg0":
            pat.append(F(SIDES, _gills))
        R.box(name, o, s, skin, pat)
    # 背びれ・腹びれ (薄い板。体の上下に接する。後ろへ低くなる)
    dorsal_h = [5, 4, 3, 2]
    for k, (name, o, s) in enumerate(segs[1:5]):
        z0 = o[2]
        top_y = o[1] + s[1]
        R.box(name, (-1, top_y, z0 + 1), (2, dorsal_h[k], 6), "fin", [F(SIDES, ribs("ridge", 2, 1))])
        R.box(name, (-1, o[1] - 3, z0 + 1), (2, 3, 6), "fin", [F(SIDES, ribs("ridge", 2, 1))])
    if MAW[opt]["fins_side"]:
        # 横に広がる薄い鰭 (体の横に接する)
        for name, o, s in segs[1:3]:
            z0 = o[2]
            R.box(name, (o[0] + s[0], o[1] + 2, z0 + 1), (1, 8, 6), "fin",
                  [F(ALL, ribs("ridge", 2, 1))])
            R.box(name, (o[0] - 1, o[1] + 2, z0 + 1), (1, 8, 6), "fin",
                  [F(ALL, ribs("ridge", 2, 1))])
    # 尾びれ (薄い板。横の面が大きく見える)
    R.box("tail", (-1, 4, 40), (2, 20, 7), "fin", [F(SIDES, ribs("ridge", 2, 1))])
    return R


def _gills(img, gimg, w, h, pal):
    """えらの切れ目 (縦の暗い線、後ろ寄り)。"""
    for x in (w - 4, w - 6):
        if 0 < x < w - 1:
            fill(img, x, 3, x, h - 4, pal["gill"])


def _eye_socket(img, gimg, w, h, pal, opt):
    """目の窪みを暗くし、光る画素を置く。顔の前寄り。"""
    # 窪み (暗い)
    fill(img, 2, 3, 6, 6, pal["gill"])
    fill(img, 3, 4, 5, 5, pal["glow"])
    fill(gimg, 3, 4, 5, 5, pal["glow"])


MAW_POSE = {"bite": {"jaw": (-30, 0, 0), "head": (-6, 0, 0), "seg1": (0, 10, 0), "seg2": (0, -8, 0)}}


# ---------------------------------------------------------------- 出力

ITEMS = {
    "waterway_maw": dict(title="水路の顎", views=dict(front=145, back=35, pitch=16), pose="bite",
                         size=(440, 300), margin=0.06, bounds=(5, 2.2), build=maw, pal=MAW_PAL),
    "whalebone_captain": dict(title="鯨骨の番兵長", views=dict(front=145, back=35, pitch=14), pose="raise",
                              size=(330, 460), margin=0.08, bounds=(3, 3.2), build=captain, pal=CAPTAIN_PAL,
                              action_size=(440, 600), action_margin=0.02),
}

CONCEPTS = {
    "waterway_maw": {
        "a": ("A 王道・素直", "青緑の細長い体に白い腹。段のある頭蓋と細く伸びる吻、開く下顎。"
              "上顎の縁と下顎に歯が並ぶ。えらの切れ目と、節ごとに細くなる体。背びれ・腹びれは薄い板。"),
        "b": ("B 重厚・威圧", "黒青の厚い甲殻。眉の稜線と、苔の付いた頬。えらの切れ目を濃くし、"
              "喉の奥が発光する。節は太く、尾びれは大きな板。"),
        "c": ("C 異形・神秘", "頭から細い柄で提灯状の発光体が垂れ、横へ薄い紫の鰭が広がる。"
              "頭蓋は細長く、深海の鮟鱇 (アンコウ) のような形。"),
    },
    "whalebone_captain": {
        "a": ("A 騎士・王道", "丸い肩当てと、長い外套。胸に鯨骨の板。兜に水色のスリット。"
              "鉾はまっすぐ。高さは約45単位 (プレイヤー約29単位の約1.5倍)。"),
        "b": ("B 重厚・威圧", "巨大な肩の板と、背の鯨骨の背骨。角のように反る兜の飾り。"
              "石と苔の鎧で、横幅が大きい。外套はなし。"),
        "c": ("C 細身・異形", "背の高い痩せた体。三本の冠の棘に光る先端。細く長いローブが足まで。"
              "胸に結晶の欠片が埋まる。高さは約47単位。"),
    },
}


def render_set(geo, tex, gl, cfg, pose_map):
    ims = []
    views = [(cfg["views"]["front"], None, cfg["size"], cfg["margin"]),
             (cfg["views"]["back"], None, cfg["size"], cfg["margin"]),
             (cfg["views"]["front"], pose_map, cfg.get("action_size", cfg["size"]),
              cfg.get("action_margin", cfg["margin"]))]
    for yaw, pose, size, margin in views:
        faces = load_geo(geo, tex, gl, pose=pose)
        ims.append(render(faces, yaw=yaw, pitch=cfg["views"]["pitch"], size=size, margin=margin))
    return ims


def main():
    report = {}
    for subject in ("waterway_maw", "whalebone_captain"):
        cfg = ITEMS[subject]
        cols = []
        report[subject] = {}
        for opt in ("a", "b", "c"):
            rig = cfg["build"](opt)
            pal = {k: hexc(v) for k, v in cfg["pal"][opt].items()}
            geo, tex, gl = build(rig, pal, subject, cfg["bounds"])
            report[subject][opt] = check_overlaps(geo)
            d = OUT / subject / opt
            d.mkdir(parents=True, exist_ok=True)
            (d / "geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
            Image.fromarray(tex, "RGBA").save(d / "texture.png")
            Image.fromarray(gl, "RGBA").save(d / "glowmask.png")
            pose_map = (MAW_POSE if subject == "waterway_maw" else CAPTAIN_POSE)[cfg["pose"]]
            cols.append((CONCEPTS[subject][opt][0], render_set(geo, tex, gl, cfg, pose_map)))
        note = "列: A/B/C。行: 3/4 前 / 3/4 後ろ / 動作 (" + ("噛みつき" if subject == "waterway_maw" else "鉾を掲げる") + ")"
        sheet(f"{cfg['title']} ラフ 3案 (1単位 = 1画素)", cols, note).save(OUT / f"{subject}.png")
    write_md(report)
    print(json.dumps(report, ensure_ascii=False))


def write_md(report):
    lines = ["# 大型 Mob ラフ 3案 (水路の顎・鯨骨の番兵長)", "",
             "生成: `tools/rough/mobs_large.py`。1単位 = テクスチャ1画素。静止姿勢の骨の回転は 0。", ""]
    for subject, title in (("waterway_maw", "水路の顎 (waterway_maw)"),
                           ("whalebone_captain", "鯨骨の番兵長 (whalebone_captain)")):
        lines += [f"## {title}", "", f"シート: `docs/roughs/{subject}.png`", ""]
        for opt in ("a", "b", "c"):
            label, text = CONCEPTS[subject][opt]
            ov = report[subject][opt]
            pal = ITEMS[subject]["pal"][opt]
            lines += [f"### {label}", "", text, "",
                      "- 色: " + "、".join(f"{k} #{v}" for k, v in pal.items()),
                      f"- 重なり検査: {'重なりなし' if not ov else '; '.join(ov)}",
                      f"- ファイル: `docs/roughs/{subject}/{opt}/` (geo.json, texture.png, glowmask.png)", ""]
    (OUT / "mobs_large.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
