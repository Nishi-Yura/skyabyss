# -*- coding: utf-8 -*-
"""箱の並び（手で決めた座標）に、素材の色の段階（ランプ）で面ごとの絵を描き、x32 の bbmodel を書く。

形は手で決める（PARTS の一つ一つの箱）。ここは塗りの決まりを全部の面に同じように当てるだけ:
- 面の向きで地の明るさを決める（上が一番明るく、下が一番暗い。光は左上の前から）
- 面の左上の縁は 1 段明るく、右下の縁は 1 段暗い（ドット絵の縁）
- 長い面は長さに沿って明→暗の段階
- 帯の素材（流木・紐）は 2 画素ごとに明暗を交互
- 1 画素 = 1 単位（x32）。宝石は光る（名前が _glow）

  python -I tools/gen/pixel/paint_cubes.py <spec.py> <出力 bbmodel>
"""
import base64
import importlib.util
import io
import json
import sys
import uuid

from PIL import Image

# 素材の色の段階（暗→明）。祠のシリーズで共通にする
RAMPS = {
    "steel": ["#2c2632", "#4a4450", "#6c6870", "#94908e", "#c0baae", "#ece6d4"],   # 古鉄（影は紫、光は温かい白。木に合わせて青みを抜いた。2026-11-18）
    "patina": ["#16302c", "#244a40", "#356b58", "#4c8f72", "#73b393", "#a9d8b8"],
    "bronze": ["#3a1e1c", "#62341e", "#8e5a2a", "#b8843e", "#dcb062", "#f6e2a0"],
    "gold": ["#4e3410", "#7c5418", "#b07e26", "#dcaa3c", "#f4d276", "#fff2c0"],
    "wood": ["#3a2228", "#5e3c34", "#866044", "#a8845a", "#c8a874", "#e6d29c"],
    "darkwood": ["#261420", "#40221e", "#5e3624", "#7e4e2e", "#a0703c", "#c49656"],
    "cord": ["#2a0a1e", "#4e1226", "#7c1e2a", "#ac3236", "#d6584a", "#f29a74"],
    "sky": ["#14304a", "#1e5478", "#2f86b0", "#55b8de", "#9ae2f6", "#eafcff"],
    "leaf": ["#1c3014", "#2c4a1e", "#44702a", "#64963a", "#8cbc56", "#c0e08a"],
    "string": ["#2a2420", "#4a4038", "#6e6254", "#958877", "#bfb4a2", "#e8e0d0"],
    "ember": ["#4a1206", "#8e2a0a", "#d0560e", "#f49020", "#ffcc58", "#fff4cc"],
}
FACE_BASE = {"up": 4, "north": 3, "south": 3, "west": 3, "east": 3, "down": 2}
LIFT = 0.16   # 色を白へ寄せる割合（2026-11-18「色が濃すぎる」。ゲームの陰が乗るぶん、元の絵は明るめにする）


def hexrgb(h):
    return tuple(round(c + (255 - c) * LIFT) for c in (int(h[i:i + 2], 16) for i in (1, 3, 5))) + (255,)


def paint_face(mat, face, w, h, kind):
    """面の絵 (w×h)。kind: 'plain' / 'band' / 'log'（丸太） / 'gem' / 'grad' / 'shine'（鋼の斜めの光） / 'worn'（緑青の光の筋と擦れ）"""
    ramp = RAMPS[mat]
    n = len(ramp)
    base = FACE_BASE[face]
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            t = base
            if kind in ("grad", "shine", "worn") and h > 3:   # 長さに沿って上が明るく下が暗い
                t += 1 - round(2 * y / (h - 1))
            if kind == "band":                    # 2 画素ごとの明暗の帯（木目は左寄りに明るい筋）
                t += 0 if (y // 2) % 2 == 0 else -1
                if w > 2 and x == 1 and y % 3 == 0:
                    t += 1
            if kind == "shine" and w > 3 and h > 3:  # 左寄りに縦の光の筋（1 画素）と、上の縁の光
                if x == 2 and 1 <= y <= h - 3:
                    t += 2
            if kind == "worn" and w > 3 and h > 3:   # 短い光の筋 2 本と擦れた暗い点
                if (y == 1 and 1 <= x <= min(3, w - 2)) or (y == h // 2 and w // 2 <= x <= w // 2 + 1):
                    t += 2
                if (x * 7 + y * 3) % 11 == 0 and 0 < x < w - 1 and 0 < y < h - 1:
                    t -= 1
            if kind == "log":                     # 丸太: 木口（east/west）は年輪、ほかの面は長さ（x）に沿う木目
                if face in ("east", "west"):
                    cx, cy = (w - 1) / 2, (h - 1) / 2
                    r = max(abs(x - cx), abs(y - cy))
                    t += 0 if int(r) % 2 == 0 else -1
                    if r < 1:
                        t -= 1                    # 芯は暗い
                elif face in ("north", "south"):
                    t += 0 if (y + (x // 5)) % 3 else -1
                    if y == 1 and 2 <= x % 7 <= 4:
                        t += 1                    # 短い光の筋
                else:
                    t += 0 if (x + (y // 4)) % 3 else -1
            if kind == "grain":                   # 縦の木目（長い板・刃の芯）。数画素ごとに暗い筋がずれて走る
                if face in ("north", "south", "east", "west"):
                    t += -1 if (x + y // 7) % 3 == 0 else 0
                else:
                    t += -1 if x % 3 == 0 else 0
            # 丸み: 縦の部品（帯・丸太の柄）は左が明るく右が暗い。横の丸太は上が明るく下が暗い
            if kind in ("band", "grain") and face in ("north", "south", "east", "west") and w > 3:
                t += 1 if x < w * 0.3 else (-1 if x >= w * 0.7 else 0)
            if kind == "log" and face in ("north", "south") and h > 3:
                t += 1 if y < h * 0.3 else (-1 if y >= h * 0.7 else 0)
            if kind == "gem":                     # 中ほど明るく、縁は暗い
                cx, cy = (w - 1) / 2, (h - 1) / 2
                d = max(abs(x - cx) / max(cx, 0.5), abs(y - cy) / max(cy, 0.5))
                t = 4 if d < 0.5 else (3 if d < 0.8 else 2)   # 中は明るく、縁は暗い
                if x == 0 or y == 0:
                    t = min(t, 3)
                if x == int(cx) - (1 if w > 4 else 0) and y == int(cy) - (1 if h > 4 else 0):
                    t = 5                                       # 光の点は 1 つだけ
            elif w > 1 and h > 1:
                if x == 0 or y == 0:              # 左上の縁は明るい
                    t += 1
                if x == 0 and y == 0:             # 角に一番明るい点
                    t += 1
                if x == w - 1 or y == h - 1:      # 右下の縁は 1 段暗い（部品の境を締める）
                    t -= 1
            img.putpixel((x, y), hexrgb(ramp[max(0, min(n - 1, t))]))
    return img


SIZES = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def seg(name, mat, p0, p1, w, d, kind="band", over=0.5):
    """p0 から p1 へ伸びる細長い箱（xy 面の中で z 軸まわりに傾ける）。曲線は短い seg をつないで作る。
    w は太さ（x）、d は奥行き（z）。over だけ先へ伸ばして、つなぎ目に隙間を作らない"""
    import math
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dx, dy) + over
    ang = round(math.degrees(math.atan2(-dx, dy)), 2)
    x0, y0 = p0
    return (name, mat, [x0 - w / 2, y0 - over / 2, -d / 2], [x0 + w / 2, y0 + L, d / 2], kind, [0, 0, ang], [x0, y0, 0])


def build(spec_path, out_path):
    spec = importlib.util.spec_from_file_location("spec", spec_path)
    mod = importlib.util.module_from_spec(spec)
    mod.seg = seg
    spec.loader.exec_module(mod)
    saved = dict(RAMPS)
    RAMPS.update(getattr(mod, "RAMPS", {}))  # シリーズの色（その品の形の決まりに RAMPS = {...} を書く。共通の色の名前を上書きしてもよい）
    try:
        _build(mod, out_path)
    finally:
        RAMPS.clear()
        RAMPS.update(saved)


def _build(mod, out_path):
    if hasattr(mod, "VARIANTS"):           # 1 つの形の決まりから何個も書く（弓の引きの段）
        import os
        for name, parts in mod.VARIANTS.items():
            write(name, parts, os.path.join(out_path, name + ".bbmodel"))
        return
    write(mod.NAME, mod.PARTS, out_path)


def paint_blade(face, w, h, top, nx, mat="steel", y0=10.0, y1=54.0):
    """メッシュの刃の面の絵。濃淡は面ごとではなく世界の高さで決める（面の継ぎ目に帯が出ない）。
    top = 絵の一番上の行の高さ、nx = 面の向きの左右（+ は右向き = 陰）。
    - 平らな面: 鍔の近くは暗く、先へ行くほど明るい。左の縁に 1 画素の光（稜線）、右の縁に 1 画素の陰
    - 斜めの面（刃の縁）: 左を向く面は明るく、右を向く面は暗い。刃の線が白く光る
    - 斜めの光の筋を 1 本（剣の決まった絵と同じ、左下から右上へ）"""
    ramp = RAMPS[mat]
    img = Image.new("RGBA", (w, h))
    bevel = w <= 3
    span = max(1e-6, y1 - y0)
    for y in range(h):
        wy = top - y
        f = (wy - y0) / span                  # 刃の根元 0 → 先 1
        g = -1 if f < 10 / 44 else (0 if f < 28 / 44 else 1)
        for x in range(w):
            if bevel:
                t = (4 if nx < 0 else 2) + g
                if (nx < 0 and x == 0) or (nx >= 0 and x == w - 1):
                    t += 1 if nx < 0 else -1
            else:
                t = 3 + g
                if x == 0:
                    t += 1
                if x == w - 1:
                    t -= 1
                d = (x - (w - 1) / 2) + (y0 + span * 20 / 44 - wy) * -0.5
                if 0 <= d < 1.2 and 0 < x < w - 1:
                    t += 2
                hsh = (x * 7 + int(wy) * 13) % 19      # まばらな粒（打った跡と擦れ）
                if 0 < x < w - 1 and hsh == 0:
                    t -= 1
                elif 0 < x < w - 1 and hsh == 7:
                    t += 1
            img.putpixel((x, y), hexrgb(ramp[max(0, min(len(ramp) - 1, t))]))
    return img


def mesh_element(p, place):
    """("mesh", 名前, 素材, 頂点のリスト, 面のリスト（頂点の番号 3〜4 個）, 塗り方)。
    面は外へ向くように並びを直す（立体の中心から外を向く向き）。面ごとに、世界の上が絵の上になるよう平らに開いて塗る"""
    import numpy as np
    _, name, mat, verts, faces_idx, kind = p
    P = np.array(verts, float)
    center = P.mean(axis=0)
    keys = [f"v{i}" for i in range(len(verts))]
    faces = {}
    for fi, idx in enumerate(faces_idx):
        Q = P[idx]
        n = np.cross(Q[1] - Q[0], Q[2] - Q[0])
        if np.linalg.norm(n) < 1e-9 and len(idx) > 3:
            n = np.cross(Q[2] - Q[0], Q[3] - Q[0])
        if n @ (Q.mean(axis=0) - center) < 0:      # 内向きなら並びを逆に
            idx = idx[::-1]
            Q = P[idx]
            n = -n
        n = n / np.linalg.norm(n)
        up = np.array([0.0, 1.0, 0.0])
        e2 = up - (up @ n) * n
        if np.linalg.norm(e2) < 0.3:
            e2 = np.array([0.0, 0.0, 1.0]) - n[2] * n
        e2 /= np.linalg.norm(e2)
        e1 = np.cross(e2, n)
        uv = np.stack([Q @ e1, -(Q @ e2)], axis=1)
        mn = uv.min(axis=0)
        uv -= mn
        w = max(1, int(np.ceil(uv[:, 0].max() - 1e-6)))
        h = max(1, int(np.ceil(uv[:, 1].max() - 1e-6)))
        a = int(np.argmax(np.abs(n)))
        face = [("east", "west"), ("up", "down"), ("south", "north")][a][0 if n[a] > 0 else 1]
        if kind == "blade":
            img = paint_blade(face, w, h, float((Q @ e2).max()), float(n[0]), mat, float(P[:, 1].min()), float(P[:, 1].max()))
        else:
            img = paint_face(mat, face, w, h, kind)
        ux, uy = place(img)
        faces[f"f{fi}"] = {"uv": {keys[i]: [round(ux + uv[j, 0], 4), round(uy + uv[j, 1], 4)] for j, i in enumerate(idx)},
                           "vertices": [keys[i] for i in idx], "texture": 0}
    return {"name": name, "type": "mesh", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "mesh/" + name + "/" + str(len(verts)))), "export": True, "visibility": True, "locked": False,
            "render_order": "default", "allow_mirror_modeling": True, "color": 0, "origin": [0, 0, 0], "rotation": [0, 0, 0],
            "vertices": {k: list(map(float, v)) for k, v in zip(keys, verts)}, "faces": faces}


def write(NAME, parts, out_path, W=128):
    """parts: (名前, 素材, from, to, 塗り方, 回転(任意), 原点(任意))。絵が 128 に入りきらなければ 256 で描き直す"""
    try:
        return _write(NAME, parts, out_path, W)
    except AssertionError:
        if W >= 512:
            raise
        return write(NAME, parts, out_path, W * 2)


def _write(NAME, parts, out_path, W):
    atlas = Image.new("RGBA", (W, W))
    x = y = rowh = 0
    elements = []
    def place(img):
        nonlocal x, y, rowh
        w, h = img.size
        if x + w > W:
            x, y, rowh = 0, y + rowh, 0
        atlas.paste(img, (x, y))
        at = (x, y)
        x += w
        rowh = max(rowh, h)
        return at

    for p in parts:
        if p[0] == "mesh":
            elements.append(mesh_element(p, place))
            continue
        name, mat, frm, to, kind = p[:5]
        rot = p[5] if len(p) > 5 else [0, 0, 0]
        origin = p[6] if len(p) > 6 else [(frm[i] + to[i]) / 2 for i in range(3)]
        faces = {}
        for face, (a, b) in SIZES.items():
            w = max(1, round(abs(to[a] - frm[a])))
            h = max(1, round(abs(to[b] - frm[b])))
            ux, uy = place(paint_face(mat, face, w, h, kind))
            faces[face] = {"uv": [ux, uy, ux + w, uy + h], "texture": 0}
        elements.append({"name": name, "box_uv": False, "render_order": "default", "locked": False, "export": True,
                         "scope": 0, "allow_mirror_modeling": True, "from": frm, "to": to, "autouv": 0, "color": 0,
                         "rotation": rot, "origin": origin, "faces": faces, "type": "cube", "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, NAME + "/" + name))})
    assert y + rowh <= W, "atlas full"
    buf = io.BytesIO()
    atlas.save(buf, "PNG")
    bb = {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False}, "name": NAME,
          "elements": elements, "outliner": [e["uuid"] for e in elements], "groups": [],
          "textures": [{"name": NAME, "id": "0", "width": W, "height": W, "uv_width": W, "uv_height": W,
                        "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}]}
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(bb, f, ensure_ascii=False, separators=(",", ":"))
    print(NAME, len(elements), "parts")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
