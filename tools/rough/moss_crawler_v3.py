"""苔這い (moss_crawler) 第3回: 頭と胴体を高解像度で作り直す。脚は第2回ラフの A をそのまま使う。

- 立方体は 0.5 単位まで。テクスチャは 1 単位 = 2 画素、面ごとの uv (mobs_small_v2 の Model を使う)。
- 頭: 前板に眼窩の穴 (奥の壁の前に光る目)、鼻の穴、眉の出っ張り、頬骨、上顎と歯、別の下顎 (蝶番で開く) と歯。
- 胴: 椎体 (1 単位, 間は椎間板), 棘突起, 0.5 段の肋骨の弧 (6 対, 間は 1 単位の隙間), 胸骨の節, 骨盤。
- 苔: 椎弓の横と棘突起の上に層のある房、脊椎の隙間に垂れるひも、頭蓋の上と横。
- 部品は面で接するだけ。Model.box で作るたびに重なりを調べる (重なれば例外)。

座標は第2回ラフと同じ (y が上、前は +z)。書き出す geo だけ、root 骨を yaw 180 度回して
前が -z になるようにする (src の苔這いと同じ向き。頭が -z)。
実行: リポジトリ直下で  python tools/rough/moss_crawler_v3.py
出力: docs/roughs/v3/moss_crawler/ (geo.json, texture.png, glowmask.png)、docs/roughs/v3/moss_crawler.png
      src/main/resources/assets/skyabyss/ の geo / textures/entity / animations/entity (苔這いの分だけ)
"""
from __future__ import annotations

import json
import math
import shutil
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import mobs_small_v2 as v2  # noqa: E402

ROOT = vx.ROOT
OUT = ROOT / "docs" / "roughs" / "v3"
ASSET = OUT / "moss_crawler"
PREV = ROOT / "docs" / "roughs" / "v2" / "moss_crawler" / "a"
SRC = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
VIEW = (360, 360)

EYE = "#9FF5FF"
BONE_DEEP = v2.BONE_DEEP
v2.RAMP["tooth"] = ("#A89E84", "#D9D0B6", "#EDE5CC", "#F3EDDA")
v2.RAMP["eye"] = ("#5FB8C4", "#7FDDEA", "#9FF5FF", "#D2F8FF")
RAMP = v2.RAMP

SLOTS = (-8, -6, -4, -2, 0, 2)          # 椎体・肋骨の z の始まり (厚み 1 単位)
SEG_OF = {-8: "s1", -6: "s1", -4: "s2", -2: "s2", 0: "s3", 2: "s3"}
GAPS = (-7, -5, -3, -1, 1)              # 椎間板と肋骨の隙間 (厚み 1 単位)
ZC = {"s1": -6.0, "s2": -2.0, "s3": 2.0}  # 脚の付け根 (第2回 A と同じ)
SOCKETS = [(-2.5, -0.5, 8.0, 9.5), (0.5, 2.5, 8.0, 9.5)]
NOSE = [(-0.5, 0.5, 7.0, 8.0)]

POSE_APEX = {  # 飛びかかりの頂点 (0.35 秒)。度。正の x で前が下がり、顎が開く
    "s1": (-12, 0, 0), "s3": (-8, 0, 0), "skull": (-4, 0, 0), "jaw": (40, 0, 0),
    "s3l_u": (-50, 0, 0), "s3r_u": (-50, 0, 0), "s3l_l": (-15, 0, 0), "s3r_l": (-15, 0, 0),
    "s1l_u": (50, 0, 0), "s1r_u": (50, 0, 0), "s1l_l": (10, 0, 0), "s1r_l": (10, 0, 0),
    "s2l_u": (-30, 0, 0), "s2r_u": (-30, 0, 0),
}


class Mdl(v2.Model):
    """v2 の Model に、置くたびの重なり検査を足したもの。"""

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, detail=None, glow=None):
        v = (x0, y0, z0, x1, y1, z1)
        assert all(float(a * 2).is_integer() for a in v), (bone, v)
        assert x1 > x0 and y1 > y0 and z1 > z0, (bone, v)
        for b in self.boxes:
            o = b["b"]
            if all(min(v[i + 3], o[i + 3]) > max(v[i], o[i]) for i in range(3)):
                raise ValueError(f"重なり: {bone} {v} と {b['bone']} {o}")
        super().box(bone, x0, y0, z0, x1, y1, z1, mat, detail, glow)


def in_rects(X, Y, rects):
    return any(a <= X <= b and c <= Y <= d for a, b, c, d in rects)


def back_wall(X, Y, Z, f):
    """眼窩と鼻の奥の壁 (頭蓋の前の面 z=7) は暗い骨色にする。"""
    if f == "south" and abs(Z - 7.0) < 1e-6 and (in_rects(X, Y, SOCKETS) or in_rects(X, Y, NOSE)):
        return BONE_DEEP
    return None


def moss_det(X, Y, Z, f):
    """苔の塗りを、位置で決まる濃淡のまだらにする。"""
    h = ((math.floor(X * 2) * 73856093) ^ (math.floor(Y * 2) * 19349663) ^ (math.floor(Z * 2) * 83492791)) & 0xFFFF
    return RAMP["moss"][1] if h % 3 else RAMP["moss"][2]


def always(X, Y, Z, f):
    return True


def rib_rows():
    """肋骨の弧を 0.5 単位のマス目にする (上の行から、y 9 → 4)。x の -1..1 は椎体の所なので空ける。"""
    rows = []
    for r in range(10):
        y0 = 9 - 0.5 * (r + 1)
        row = []
        for c in range(16):
            x0 = -4 + 0.5 * c
            cx, cy = x0 + 0.25, y0 + 0.25
            on = ((cx >= 1 or cx <= -1) and y0 >= 4
                  and 0.74 <= math.hypot(cx / 3.25, (cy - 6.5) / 2.75) <= 1.0)
            row.append("#" if on else ".")
        rows.append("".join(row))
    return rows


def skull_plate():
    """頭蓋の前板 (x -3..3, y 6..10.5, 12 x 9 マス)。眼窩 2 つと鼻の穴を開ける。"""
    rows = []
    for r in range(9):
        row = ["#"] * 12
        if 2 <= r <= 4:
            for c in list(range(1, 5)) + list(range(7, 11)):
                row[c] = "."
        if r in (5, 6):
            row[5] = row[6] = "."
        rows.append("".join(row))
    return rows


def build():
    m = Mdl("moss_crawler")
    BN, DK, TR, MS, TH, EY = "bone", "bone_dirty", "trim", "moss", "tooth", "eye"

    # ---- 骨 (bone): 背骨 3 節、頭、顎、脚 (第2回 A と同じ名前と位置)
    m.bone("s1", (0, 4, -6))
    m.bone("s2", (0, 4, -2), "s1")
    m.bone("s3", (0, 4, 2), "s2")
    m.bone("skull", (0, 7, 4), "s3")
    m.bone("jaw", (0, 4, 4.5), "skull")
    legs = []
    for seg in ("s1", "s2", "s3"):
        for side, sgn in (("l", -1), ("r", 1)):
            name = f"{seg}{side}"
            legs.append((seg, name, sgn))
            m.bone(f"{name}_u", (sgn * 1.5, 4.0, ZC[seg]), seg)
            m.bone(f"{name}_l", (sgn * 1.5, 2.5, ZC[seg]), f"{name}_u")

    # ---- 脚 (第2回 A のまま)
    for seg, name, sgn in legs:
        zc = ZC[seg]
        x_in = sgn * 1.5
        m.chain(f"{name}_u", x_in, sgn, 4.0, [(1.5, 1.5, 2, BN)], zc=zc)
        m.chain(f"{name}_l", x_in, sgn, 2.5,
                [(0.5, 2.0, 2, DK), (1.5, 1.5, 2, BN), (0.5, 2.5, 3, TR, zc + 0.5)], zc=zc)
        fx0 = min(x_in, x_in + sgn * 2.5)
        for dx in (0.5, 1.5):
            m.box(f"{name}_l", fx0 + dx, 0, zc + 2, fx0 + dx + 0.5, 0.5, zc + 2.5, TR)  # 爪

    # ---- 胴: 椎体、棘突起、胸骨の節、肋骨
    for i, z0 in enumerate(SLOTS):
        seg = SEG_OF[z0]
        m.box(seg, -1, 6.5, z0, 1, 8.5, z0 + 1, BN)        # 椎体
        m.box(seg, -1, 8.5, z0, 1, 9, z0 + 1, BN)          # 椎弓
        m.box(seg, -0.5, 9, z0, 0.5, 10.5, z0 + 1, BN)     # 棘突起
        m.box(seg, -1, 3.5, z0, 1, 4, z0 + 1, DK)          # 胸骨の節
        m.plate(seg, -4, 9, z0, rib_rows(), 1.0, BN)       # 肋骨 (左右)
    for g0 in GAPS:
        seg = SEG_OF[g0 - 1]
        m.box(seg, -0.5, 6.5, g0, 0.5, 7.5, g0 + 1, DK)    # 椎間板
        m.box(seg, -1, 5.5, g0, -0.5, 8.5, g0 + 0.5, MS, detail=moss_det)     # 垂れる苔
        m.box(seg, 0.5, 6, g0 + 0.5, 1, 8, g0 + 1, MS, detail=moss_det)

    # 骨盤 (背骨の後ろの端に付く)
    for sgn in (-1, 1):
        x0, x1 = sorted((sgn * 1, sgn * 2.5))
        xa, xb = sorted((sgn * 1, sgn * 2.5))
        m.box("s1", xa, 7.5, -9, xb, 9, -8, DK)
        xa2, xb2 = sorted((sgn * 2, sgn * 2.5))
        m.box("s1", xa2, 6.5, -9, xb2, 7.5, -8, DK)
    m.box("s1", -1, 6.5, -9, 1, 8.5, -8, DK)               # 仙骨

    # 首 (最後の椎体と頭蓋の間)
    m.box("s3", -1, 6.5, 3, 1, 8.5, 4, DK)

    # ---- 頭: 頭蓋、前板 (眼窩と鼻の穴)、眉、頬骨、上顎と歯、目
    m.box("skull", -2.5, 6, 4, 2.5, 7.5, 7, DK, detail=back_wall)      # 頭蓋の底
    m.box("skull", -3, 7.5, 4, 3, 10.5, 7, BN, detail=back_wall)       # 脳函
    m.box("skull", -2.5, 10.5, 4.5, 2.5, 11.5, 6.5, BN)                # 丸み (上)
    m.box("skull", -0.5, 11.5, 5, 0.5, 12, 6, DK)                      # 頭頂の稜
    m.plate("skull", -3, 10.5, 7, skull_plate(), 1.0, BN)              # 前板
    m.box("skull", -2.5, 9.5, 8, -0.5, 10, 8.5, BN)                    # 眉 (左)
    m.box("skull", 0.5, 9.5, 8, 2.5, 10, 8.5, BN)                      # 眉 (右)
    m.box("skull", -0.5, 9.5, 8, 0.5, 10, 8.5, BN)                     # 眉 (中央)
    m.box("skull", 3, 6.5, 5.5, 3.5, 7.5, 7.5, BN)                     # 頬骨 (右)
    m.box("skull", -3.5, 6.5, 5.5, -3, 7.5, 7.5, BN)                   # 頬骨 (左)
    m.box("skull", -2.5, 5.5, 7, 2.5, 6, 8, BN)                        # 上顎
    for x0 in (-2, -1, 0, 1, 2):
        m.box("skull", x0, 5, 7.5, x0 + 0.5, 5.5, 8, TH)               # 上の歯
    m.box("skull", -2, 8, 7, -1, 9, 7.5, EY, glow=always)              # 目 (左、眼窩の奥)
    m.box("skull", 1, 8, 7, 2, 9, 7.5, EY, glow=always)                # 目 (右)

    # ---- 下顎 (jaw): 蝶番は後ろ (z 4.5) にあり、前が下がって開く
    m.box("jaw", -2, 3.5, 4.5, 2, 4.5, 8.5, BN)                        # 下顎の体
    m.box("jaw", -1, 3, 7.5, 1, 3.5, 8.5, BN)                          # おとがい
    for x0 in (-1.5, -0.5, 0.5, 1.5):
        m.box("jaw", x0, 4.5, 7.5, x0 + 0.5, 5, 8, TH)                 # 下の歯

    # ---- 苔: 背骨の上の層のある房、垂れるひも (ひもは隙間に下がる)
    for i, z0 in enumerate(SLOTS):
        seg = SEG_OF[z0]
        if i % 2 == 0:
            m.box(seg, -1, 9, z0, -0.5, 9.5, z0 + 1, MS, detail=moss_det)
        else:
            m.box(seg, 0.5, 9, z0 + 0.5, 1, 9.5, z0 + 1, MS, detail=moss_det)
        if i % 3 == 0:
            m.box(seg, -0.5, 10.5, z0, 0, 11, z0 + 1, MS, detail=moss_det)
        elif i % 3 == 1:
            m.box(seg, 0, 10.5, z0 + 0.5, 0.5, 11, z0 + 1, MS, detail=moss_det)
        if i in (2, 4):
            m.box(seg, -1, 9.5, z0 + 0.5, -0.5, 10, z0 + 1, MS, detail=moss_det)

    # ---- 苔: 頭蓋の上と横
    m.box("skull", -2, 11.5, 4.5, -0.5, 12, 5.5, MS, detail=moss_det)
    m.box("skull", -1.5, 12, 4.5, -0.5, 12.5, 5, MS, detail=moss_det)
    m.box("skull", 0.5, 11.5, 5, 2, 12, 6.5, MS, detail=moss_det)
    m.box("skull", 1, 12, 5.5, 2, 12.5, 6.5, MS, detail=moss_det)
    m.box("skull", -3.5, 8.5, 4.5, -3, 10, 5, MS, detail=moss_det)     # 横に垂れる (左)
    m.box("skull", -3.5, 7.5, 4.5, -3, 8.5, 5, MS, detail=moss_det)
    m.box("skull", 3, 8.5, 5, 3.5, 10, 5.5, MS, detail=moss_det)       # 横に垂れる (右)

    return m


def finish_geo(m):
    """Model から geo・テクスチャ・光る部分を作り、root 骨 (yaw 180 度) を付けた geo を返す。"""
    geo_rough, tex, gl = m.finish()
    overlaps = vx.check_overlaps(geo_rough)  # 回転前の形で重なりを確かめる
    g = geo_rough["minecraft:geometry"][0]
    for b in g["bones"]:
        if "parent" not in b:
            b["parent"] = "root"
    g["bones"].insert(0, {"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 180.0, 0.0]})
    return geo_rough, tex, gl, overlaps


def head_faces(faces):
    """頭の近景。物体の前側 (-z) で、頭より上の面だけ。"""
    keep = []
    for f in faces:
        c = f.origin + 0.5 * (f.eu + f.ev)
        if c[2] < -3.0 and c[1] > 3.0:
            keep.append(f)
    return keep


def views_new(geo, tex, gl):
    """新しい案の 4 枚 (正面3/4, 背面3/4, 頭の近景, 飛びかかり)。root で前が -z になっている。"""
    rest = vx.load_geo(geo, tex, gl)
    posed = vx.load_geo(geo, tex, gl, pose=POSE_APEX)
    return [
        vx.render(rest, yaw=205, pitch=18, size=VIEW),
        vx.render(rest, yaw=25, pitch=18, size=VIEW),
        vx.render(head_faces(rest), yaw=205, pitch=12, size=VIEW),
        vx.render(posed, yaw=210, pitch=14, size=VIEW),
    ]


def views_prev():
    """前回の A (v2) の 4 枚。こちらは前が +z (v2 のまま)。"""
    rest = vx.load_geo(PREV / "geo.json", PREV / "texture.png", PREV / "glowmask.png")
    posed = vx.load_geo(PREV / "geo.json", PREV / "texture.png", PREV / "glowmask.png",
                        pose={"s1": (20, 0, 0), "s1l_u": (-60, 0, 0), "s1r_u": (-60, 0, 0),
                              "s3l_u": (-60, 0, 0), "s3r_u": (-60, 0, 0), "s1l_l": (30, 0, 0), "s1r_l": (30, 0, 0)})
    head = [f for f in rest if (f.origin + 0.5 * (f.eu + f.ev))[2] > 3.0 and (f.origin + 0.5 * (f.eu + f.ev))[1] > 5.0]
    return [
        vx.render(rest, yaw=25, pitch=18, size=VIEW),
        vx.render(rest, yaw=205, pitch=18, size=VIEW),
        vx.render(head, yaw=25, pitch=12, size=VIEW),
        vx.render(posed, yaw=30, pitch=14, size=VIEW),
    ]


def animations():
    """idle (2 秒)、walk (0.5 秒, 三組の脚の交互)、pounce (0.8 秒, 1 回)。molang の sin / cos は度。"""
    legs = {"s1l": 0, "s1r": 180, "s2l": 180, "s2r": 0, "s3l": 0, "s3r": 180}  # 三角の歩き方
    walk_bones = {"s2": {"position": ["0", "math.abs(math.sin(query.anim_time * 720)) * 0.5", "0"],
                         "rotation": ["math.sin(query.anim_time * 720) * 2", "0", "0"]}}
    for n, p in legs.items():
        walk_bones[f"{n}_u"] = {"rotation": [f"math.sin(query.anim_time * 720 + {p}) * 28", "0", "0"]}
        walk_bones[f"{n}_l"] = {"rotation": [f"14 + math.sin(query.anim_time * 720 + {p} + 90) * 12", "0", "0"]}

    def key(vals):
        return {t: [v, 0, 0] for t, v in vals.items()}

    pounce = {
        "s1": {"rotation": key({"0.0": 0, "0.15": 8, "0.35": -12, "0.6": -6, "0.8": 0})},
        "s3": {"rotation": key({"0.0": 0, "0.15": 12, "0.35": -8, "0.6": -4, "0.8": 0})},
        "skull": {"rotation": key({"0.0": 0, "0.15": 14, "0.35": -4, "0.6": -2, "0.8": 0})},
        "jaw": {"rotation": key({"0.0": 0, "0.2": 10, "0.4": 40, "0.55": 6, "0.8": 0})},
    }
    for n in ("s3l", "s3r"):
        pounce[f"{n}_u"] = {"rotation": key({"0.0": 0, "0.15": 20, "0.35": -50, "0.6": -30, "0.8": 0})}
        pounce[f"{n}_l"] = {"rotation": key({"0.0": 0, "0.15": 40, "0.35": -20, "0.6": 0, "0.8": 0})}
    for n in ("s1l", "s1r"):
        pounce[f"{n}_u"] = {"rotation": key({"0.0": 0, "0.15": 20, "0.35": 40, "0.6": 20, "0.8": 0})}
        pounce[f"{n}_l"] = {"rotation": key({"0.0": 0, "0.15": -30, "0.35": 10, "0.6": 0, "0.8": 0})}
    for n in ("s2l", "s2r"):
        pounce[f"{n}_u"] = {"rotation": key({"0.0": 0, "0.15": 0, "0.35": -30, "0.6": -10, "0.8": 0})}

    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.moss_crawler.idle": {
                "loop": True, "animation_length": 2.0,
                "bones": {
                    "s2": {"rotation": ["math.sin(query.anim_time * 180) * 1.2", "0", "0"]},
                    "s1": {"rotation": ["0", "math.sin(query.anim_time * 180 + 90) * 2.5", "0"]},
                    "skull": {"rotation": ["math.sin(query.anim_time * 180 + 90) * 2.5",
                                           "math.sin(query.anim_time * 180) * 4", "0"]},
                    "jaw": {"rotation": ["2 + math.sin(query.anim_time * 180 + 180) * 1.5", "0", "0"]},
                },
            },
            "animation.moss_crawler.walk": {"loop": True, "animation_length": 0.5, "bones": walk_bones},
            "animation.moss_crawler.pounce": {"animation_length": 0.8, "bones": pounce},
        },
    }


def install(geo_path, tex_path, gl_path):
    shutil.copyfile(geo_path, SRC / "geo" / "entity" / "moss_crawler.geo.json")
    shutil.copyfile(tex_path, SRC / "textures" / "entity" / "moss_crawler.png")
    shutil.copyfile(gl_path, SRC / "textures" / "entity" / "moss_crawler_glowmask.png")
    (SRC / "animations" / "entity" / "moss_crawler.animation.json").write_text(
        json.dumps(animations(), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    m = build()
    geo_rough, tex, gl, overlaps = finish_geo(m)
    ASSET.mkdir(parents=True, exist_ok=True)
    geo_path = ASSET / "geo.json"
    tex_path = ASSET / "texture.png"
    gl_path = ASSET / "glowmask.png"
    geo_path.write_text(json.dumps(geo_rough, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tex_path)
    Image.fromarray(gl, "RGBA").save(gl_path)
    install(geo_path, tex_path, gl_path)
    print("boxes", len(m.boxes), "texture", tex.shape[1], "x", tex.shape[0])
    print("overlaps:", overlaps if overlaps else "none")

    cols = [("前回の A (v2)", views_prev()),
            ("第3回: 頭と胴体を高解像度に", views_new(geo_rough, tex, gl))]
    note = "列: 左が前回の A、右が第3回。行: 正面3/4、背面3/4、頭の近景、飛びかかり (頂点)"
    vx.sheet("moss_crawler ラフ 第3回 (高解像度)", cols, note).save(OUT / "moss_crawler.png")


if __name__ == "__main__":
    main()
