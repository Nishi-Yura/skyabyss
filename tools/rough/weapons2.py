"""武器ラフ (第3版): 大剣と鉾のアイコンを、刃の向き (u) と幅 (v) の座標で組み立てる。
牙と矢は weapons.py の第2版のアイコンを使う。厚みは全品目で「全体 1、鍔・柄頭・宝石・金具だけ 2」。刃の縁で厚みを変えると斜めの刃が櫛の歯のように割れるため、刃は一定の厚みにする。

使い方: python tools/rough/weapons2.py
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from voxel import bevel_depth, extrude, icon_big, load_png, render, sheet, voxel_faces  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/roughs/weapons"


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# 案ごとの配色。edge=刃の暗い縁, lit=光る縁, body=刃の地, dark=影の縁, guard/guard_d=鍔, grip/grip_d=柄, gem=宝石
PALETTES = {
    "a": dict(edge="#7a6448", lit="#fff6dc", body="#e3d3ac", dark="#a88f68", guard="#8a6a4a", guard_d="#5a4230",
              grip="#6b4a32", grip_d="#43301f", pommel="#d9c9a0", gem="#5fe8ff"),
    "b": dict(edge="#3c4249", lit="#d7dde2", body="#9aa3ab", dark="#5d666f", guard="#56616a", guard_d="#363d44",
              grip="#4f6a3a", grip_d="#33452a", pommel="#8c949b", gem="#5fe8ff"),
    "c": dict(edge="#1d5a62", lit="#c9fbff", body="#5fc9d2", dark="#2f8c96", guard="#2a6e76", guard_d="#164850",
              grip="#1f3f48", grip_d="#122a31", pommel="#7fe0e8", gem="#e9fdff"),
}

# 刃の幅 (v の範囲) と鍔の長さ。案ごとに形も変える
SHAPES = {
    "a": dict(blade=(14, 17), guard=(10, 21), guard_u=(-1, 0), tip_from=12),
    "b": dict(blade=(13, 18), guard=(9, 22), guard_u=(-2, 0), tip_from=11),   # 刃も鍔も太い
    "c": dict(blade=(14, 17), guard=(11, 20), guard_u=(-1, 0), tip_from=10),  # 鍔の両端が刃の側へ反る
}


def greatsword(opt):
    p = {k: rgb(v) for k, v in PALETTES[opt].items()}
    RAISED.update({p["guard"], p["guard_d"], p["pommel"]})
    s = SHAPES[opt]
    img = np.zeros((16, 16, 4), np.uint8)
    glow = np.zeros((16, 16), bool)
    v0, v1 = s["blade"]
    mid = (v0 + v1) / 2
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y
            c = None
            # 刃: 根元 (u=1) から切っ先 (右上) へ。先ほど細くする
            if 1 <= u <= 15 and v0 <= v <= v1:
                half = (v1 - v0) / 2
                if u >= s["tip_from"]:
                    half -= (u - s["tip_from"]) * 0.75
                if abs(v - mid) <= half + 0.01:
                    if v == v0 or abs(v - (mid - half)) < 0.6:
                        c = p["edge"]
                    elif v == v1 or abs(v - (mid + half)) < 0.6:
                        c = p["dark"]
                    elif v == int(mid):
                        c = p["lit"]
                    else:
                        c = p["body"]
            # 鍔: 刃に直角
            gu0, gu1 = s["guard_u"]
            g0, g1 = s["guard"]
            if gu0 <= u <= gu1 and g0 <= v <= g1:
                c = p["guard_d"] if (u == gu0 or v in (g0, g1)) else p["guard"]
                if opt == "c" and v in (g0, g1):
                    c = p["guard"]
            if opt == "c":
                # 鍔の両端が刃の側へ反る角
                if (u == 1 and v in (g0, g1)) or (u == 2 and v in (g0 + 1, g1 - 1)):
                    c = p["guard_d"]
            # 柄
            if -9 <= u <= gu0 - 1 and 15 <= v <= 16:
                c = p["grip"] if (u + v) % 4 else p["grip_d"]
            # 柄頭
            if -11 <= u <= -10 and 14 <= v <= 17:
                c = p["pommel"] if v in (15, 16) else p["guard_d"]
            if c is not None:
                img[y, x] = c
    # 鍔の中央の宝石
    for (x, y) in [(7, 8), (8, 8)] if opt != "b" else [(7, 8), (8, 8), (7, 9)]:
        pass
    gx, gy = 7, 8
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y
            if u == s["guard_u"][1] and int(mid) <= v <= int(mid) + 1:
                img[y, x] = p["gem"]
                glow[y, x] = True
            if opt == "c" and u >= 13 and v == int(mid) and img[y, x, 3]:
                glow[y, x] = True  # 異形: 切っ先だけが光る
    return img, glow


PIKE = {
    "a": dict(shaft="#d8cba8", shaft_d="#8f7f5e", head="#eee4c8", head_d="#9c8b66", edge="#6d5d42", hook=(19, 22), band="#5fe8ff"),
    "b": dict(shaft="#6a5a44", shaft_d="#3f3426", head="#b8bec4", head_d="#5f676f", edge="#30363c", hook=(19, 23), band="#d0a050"),
    "c": dict(shaft="#2f6670", shaft_d="#173c43", head="#a9f3f8", head_d="#3f9aa3", edge="#1b4e55", hook=(19, 21), band="#e9fdff"),
}


def pike(opt):
    p = {k: (rgb(v) if isinstance(v, str) else v) for k, v in PIKE[opt].items()}
    RAISED.add(p["band"])
    img = np.zeros((16, 16, 4), np.uint8)
    glow = np.zeros((16, 16), bool)
    h0, h1 = p["hook"]
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y
            c = None
            # 柄: 左下から右上へ細く
            if -15 <= u <= 6 and 15 <= v <= 16:
                c = p["shaft"] if v == 15 else p["shaft_d"]
            # 穂先: 細長い刃
            if 7 <= u <= 15 and 14 <= v <= 17:
                half = 2 - max(0, u - 11) * 0.6
                if abs(v - 15.5) <= half:
                    c = p["head"] if v <= 15 else p["head_d"]
                    if abs(abs(v - 15.5) - half) < 0.6:
                        c = p["edge"]
            # 鉤: 穂先の根元から横へ張り出し、根元側へ反る三日月
            if 3 <= u <= 8 and 17 <= v <= h1:
                curve = (v - 17) * 0.8          # 外へ行くほど根元側へ
                if 6 - curve <= u <= 8 - curve * 0.5:
                    c = p["head_d"] if (v == h1 or u <= 6 - curve + 0.5) else p["head"]
            # 鉤の根の帯 (宝石・金具)
            if u in (5, 6) and v in (15, 16):
                c = p["band"]
                glow[y, x] = opt != "b"
            # 石突き
            if -17 <= u <= -16 and 14 <= v <= 17:
                c = p["edge"]
            if c is not None:
                img[y, x] = c
    return img, glow


RAISED = set()  # 一段厚くする色 (鍔・柄頭・金具)。下の作り手が登録する


FANG = {
    # 牙: 根元が太く、先へ細くなって反る。a=白い牙と青黒の柄、b=黄ばんだ牙に血の筋と返し、c=半透明の水色の牙
    "a": dict(fang="#eef2ea", fang_m="#c3cfc6", fang_d="#7f8f8a", guard="#2c4a55", guard_d="#1a2d35", grip="#35505a", grip_d="#203540", blood=None, barbs=False),
    "b": dict(fang="#efe2c4", fang_m="#cdb88f", fang_d="#8c7553", guard="#4a3428", guard_d="#2e2019", grip="#5a3f2e", grip_d="#3a281d", blood="#b0283a", barbs=True),
    "c": dict(fang="#d8fdff", fang_m="#8fe6ef", fang_d="#3f9aa3", guard="#1f4a52", guard_d="#123036", grip="#24434b", grip_d="#152a30", blood=None, barbs=False),
}


def fang(opt):
    p = {k: (rgb(v) if isinstance(v, str) else v) for k, v in FANG[opt].items()}
    RAISED.update({p["guard"], p["guard_d"]})
    img = np.zeros((16, 16, 4), np.uint8)
    glow = np.zeros((16, 16), bool)
    for y in range(16):
        for x in range(16):
            u, v = x - y, x + y
            c = None
            # 牙の刃: u=1..12。幅は根元 4 から先へ細く、中心線は先ほど +v 側 (外側) へ反る
            if 1 <= u <= 15:
                half = 2.3 - (u - 1) * 0.15
                centre = 15.0 + (u / 15) ** 2 * 3.0
                d = v - centre
                if abs(d) <= half:
                    if d <= -half + 1:
                        c = p["fang_d"]          # 内側 (影) の縁
                    elif d >= half - 1:
                        c = p["fang"]            # 外側の光る縁
                    else:
                        c = p["fang_m"]
                    if p["blood"] and abs(d) < 0.6 and u <= 9:
                        c = p["blood"]           # 根元の溝に血の筋
                    if opt == "c" and d >= half - 1 and u >= 6:
                        glow[y, x] = True        # 外縁の先半分が光る
                # 返し (b): 内側の縁から根元向きの小さな棘
                if p["barbs"] and u in (4, 8, 12) and -half - 1.6 <= d < -half:
                    c = p["fang_d"]
            # 鍔 (小さめ)
            if u in (-1, 0) and 11 <= v <= 19:
                c = p["guard_d"] if v in (11, 19) or u == -1 else p["guard"]
            # 柄
            if -8 <= u <= -2 and 14 <= v <= 15:
                c = p["grip"] if (u + v) % 4 else p["grip_d"]
            # 柄頭
            if -10 <= u <= -9 and 13 <= v <= 16:
                c = p["fang_m"] if v in (14, 15) else p["guard_d"]
            if c is not None:
                img[y, x] = c
    return img, glow


def depth_for(icon, glow):
    a = icon[..., 3] > 0
    d = np.where(a, 1, 0)
    for y in range(16):
        for x in range(16):
            if a[y, x] and (glow[y, x] or tuple(int(c) for c in icon[y, x]) in RAISED):
                d[y, x] = 2
    return d


def render_item(icon, glow):
    d = depth_for(icon, glow)
    g, gl = extrude(icon, d, glow)
    f = voxel_faces(g, gl)
    return [icon_big(icon, 12),
            render(f, yaw=25, pitch=30, size=(440, 440), margin=0.04),
            render(f, yaw=115, pitch=20, size=(440, 440), margin=0.04)]


def main():
    from PIL import Image
    makers = {"whalebone_greatsword": greatsword, "captain_pike": pike, "waterway_fang": fang}
    names = {"whalebone_greatsword": "鯨骨の大剣", "captain_pike": "番兵長の鉾",
             "waterway_fang": "水路の顎牙", "bone_spike_arrow": "骨棘の矢"}
    labels = {"a": "A 王道", "b": "B 重厚", "c": "C 異形"}
    for item in names:
        cols = []
        for opt in "abc":
            folder = OUT / item / opt
            folder.mkdir(parents=True, exist_ok=True)
            if item in makers:
                icon, glow = makers[item](opt)
                Image.fromarray(icon, "RGBA").save(folder / "icon.png")
            else:
                icon = load_png(folder / "icon.png")
                gpath = folder / "glow.png"
                glow = (load_png(gpath)[..., 3] > 0) if gpath.exists() else np.zeros((16, 16), bool)
            d = depth_for(icon, glow)
            Image.fromarray((d * 60).astype(np.uint8), "L").save(folder / "depth.png")
            cols.append((labels[opt], render_item(icon, glow)))
        sheet(f"{names[item]} ({item})", cols, "行: 16x16 拡大 / 3D 斜め / 3D 裏側から (厚み: 全体2画素、鍔・柄頭・宝石は4画素)") \
            .save(ROOT / f"docs/roughs/weapons_{item}.png")
        print("sheet", item)


if __name__ == "__main__":
    main()
