"""スキン (client/FullSkinLayer) の形を箱で組み、面ごとにドット絵を描く道具。
スキンは服だけ (2026-11-01 の声「顔かえるのはいらない」): 着ている人のバニラの体と自分のスキンを残し、Steve の体の大きさ
(頭 8・体 8x12x4・腕と脚 4x12x4、重ね着の層 +0.25〜0.5) より外に服を重ねる。顔は描かない。

座標は geo (1/16 ブロック、足もと y = 0、上へ +y、前 = -Z、体の右 = -X)。T 画素 / 1 単位で描く (既定 4 = バニラの 4 倍の細かさ)。
骨: head / body / armR / armL / legR / legL はバニラの体の部分と同じに動く。ほかの骨は parent に付き、名前の頭で揺れる
  (hair・cloth・cape・ribbon・tail = なびく / skirt = 脚で広がる / float = 浮かぶ / orbit = 付け根のまわりを回る / wing = 羽ばたく)。
面の名前: n (前 -Z) / s (後ろ +Z) / e (+X = 体の左) / w (-X = 体の右) / u (上) / d (下)。
面の絵の向き: 外から見て左上が (0, 0)。n は左 = 体の右、s は左 = 体の左、w は左 = 後ろ、e は左 = 前、u は下 = 前、d は上 = 前。
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

T = 8
ROOT = Path(__file__).resolve().parents[3]

# バニラの体の部分の付け根 (geo)
PIVOTS = {"head": (0, 24, 0), "body": (0, 24, 0), "armR": (-5, 22, 0), "armL": (5, 22, 0), "legR": (-1.9, 12, 0), "legL": (1.9, 12, 0)}
SWAP = {"armR": "armL", "armL": "armR", "legR": "legL", "legL": "legR"}

# 面ごとの外向き・絵の u 軸・v 軸 (v は下向き)
FACES = {
    "n": ((0, 0, -1), (1, 0, 0), (0, -1, 0)),
    "s": ((0, 0, 1), (-1, 0, 0), (0, -1, 0)),
    "e": ((1, 0, 0), (0, 0, 1), (0, -1, 0)),
    "w": ((-1, 0, 0), (0, 0, -1), (0, -1, 0)),
    "u": ((0, 1, 0), (1, 0, 0), (0, 0, -1)),
    "d": ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
}


def rot_matrix(r):
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return X @ Y @ Z


@dataclass
class Face:
    bone: str
    verts: np.ndarray  # (4, 3) 左上・右上・右下・左下 (外から見て)
    img: np.ndarray  # (H, W, 4)
    glow: np.ndarray | None = None  # (H, W) bool
    uv: tuple = None  # アトラスの (x, y) 置き場
    skin: tuple = None  # 着ている人のスキンの絵の (u0, v0, u1, v1) (64x64 の画素。own_ の骨)。アトラスには入れない


@dataclass
class Box:
    bone: str
    frm: tuple
    size: tuple
    paint: object
    rot: tuple = (0, 0, 0)
    pivot: tuple | None = None
    mirror: bool = False
    hide: tuple = ()
    tag: str = ""
    yaw: float = 0.0  # rot の後に、付け根のまわりで Y へ回す (斜めの布)
    skinmap: dict = None  # {面: (u0, v0, u1, v1)} 着ている人のスキンの絵の場所 (64x64 の画素)。own_ の骨の箱だけ
    spin: float = 0.0  # まず箱の真ん中のまわりで Y へ回す (角を落とす斜めのキューブ)。そのあと rot・yaw


class Model:
    def __init__(self, name, outfit=False):
        self.name = name
        # True = 服だけ (着ている人のバニラの体とスキンを残して重ねる)。バニラの体の大きさに合わせて作る
        self.outfit = outfit
        # 太い箱 (幅と奥行きが 1.8 以上、高さ 0.5 以上) は角を落として重ねる (box の rounded=False で止める)
        self.auto_round = True
        self.boxes: list[Box] = []
        self.bones: dict[str, dict] = {}
        for b, p in PIVOTS.items():
            self.bones[b] = dict(parent=None, pivot=p)

    def bone(self, name, parent, pivot):
        self.bones[name] = dict(parent=parent, pivot=tuple(pivot))
        return name

    def box(self, bone, frm, size, paint, rot=(0, 0, 0), pivot=None, hide=(), tag="", mirror=False, yaw=0.0, skinmap=None, rounded=None, spin=0.0):
        x, y, z = frm
        w, h, d = size
        rnd_ok = self.auto_round if rounded is None else rounded
        if rnd_ok and skinmap is None and h >= 0.5 and min(w, d) >= 1.8:
            # 角を落として断面を八角にする: 横に広い箱と奥に広い箱 (十字) に、四隅へ 45 度回した細いキューブを置いて斜めの面にする
            r = min(0.9, min(w, d) * 0.24)
            piv = tuple(pivot) if pivot else (x + w / 2, y + h / 2, z + d / 2)
            self.boxes.append(Box(bone, (x, y, z + r), (w, h, d - 2 * r), paint, tuple(rot), piv, mirror, tuple(hide), tag, yaw, None))
            b = Box(bone, (x + r, y, z), (w - 2 * r, h, d), paint, tuple(rot), piv, mirror, tuple(hide), tag, yaw, None)
            self.boxes.append(b)
            side = r * math.sqrt(2)
            plain = _side_paint(paint)
            for (cx, cz) in ((x + r, z + r), (x + w - r, z + r), (x + r, z + d - r), (x + w - r, z + d - r)):
                self.boxes.append(Box(bone, (cx - side / 2, y, cz - side / 2), (side, h, side), plain, tuple(rot), piv, mirror,
                                      tuple(hide), tag, yaw, None, 45.0))
            return b
        b = Box(bone, tuple(frm), tuple(size), paint, tuple(rot), tuple(pivot) if pivot else None, mirror, tuple(hide), tag, yaw, skinmap, spin)
        self.boxes.append(b)
        return b

    def ring(self, bone, center, y, height, radius, n, thick, paint, tilt=0.0, overlap=1.15, rot_pivot=None, phase=0.0, skip=None, squarish=0.0):
        """輪 (円すい) を斜めの板で: 中心のまわりに n 枚、それぞれ外を向けて tilt だけ外へ傾ける。隣と角が重なる幅にする。
        角度 0 = 前 (-Z)、90 = 体の右 (-X)。skip = (始め, 終わり) の角度は置かない (顔の前など)。squarish = 斜めの向きほど外へ (四角い頭を包む)。"""
        cx, cz = center
        w = 2 * math.pi * radius / n * overlap
        out = []
        for k in range(n):
            a = 360.0 * k / n + phase
            aa = (a + 180) % 360 - 180
            if skip and skip[0] <= aa <= skip[1]:
                continue
            rr = radius * (1 + squarish * abs(math.sin(math.radians(2 * a))))
            px = cx + math.sin(math.radians(a)) * -rr
            pz = cz - math.cos(math.radians(a)) * rr
            piv = (px, y + height, pz)
            out.append(self.box(bone, (px - w / 2, y, pz - thick / 2), (w, height, thick), paint, rot=(tilt, 0, 0), pivot=piv, yaw=a, rounded=False))
        return out

    def pair(self, bone, frm, size, paint, rot=(0, 0, 0), pivot=None, hide=(), tag="", yaw=0.0):
        """体の右 (-X) に置いた箱と、左右を映した箱を作る。骨の名前の R/L も入れ替える。"""
        a = self.box(bone, frm, size, paint, rot, pivot, hide, tag, yaw=yaw)
        mb = SWAP.get(bone, bone.replace("_R", "_L") if bone.endswith("_R") else bone)
        x0 = -(frm[0] + size[0])
        piv = None if pivot is None else (-pivot[0], pivot[1], pivot[2])
        b = self.box(mb, (x0, frm[1], frm[2]), size, paint, (rot[0], -rot[1], -rot[2]), piv, tuple({"e": "w", "w": "e"}.get(h, h) for h in hide), tag, True, -yaw)
        return a, b

    def pair_bone(self, name, parent, pivot):
        """_R の骨と、映した _L の骨を作る。"""
        self.bone(name, parent, pivot)
        pl = SWAP.get(parent, parent.replace("_R", "_L") if parent and parent.endswith("_R") else parent)
        self.bone(name.replace("_R", "_L"), pl, (-pivot[0], pivot[1], pivot[2]))

    # ------------------------------------------------------------------ 面を作る
    def faces(self):
        out = []
        for bx in self.boxes:
            out.extend(box_faces(bx))
        return out


def box_faces(bx: Box):
    x0, y0, z0 = bx.frm
    w, h, d = bx.size
    c = np.array([[x0, y0, z0], [x0 + w, y0 + h, z0 + d]], float)
    piv = np.array(bx.pivot if bx.pivot else ((x0 + w / 2), (y0 + h / 2), (z0 + d / 2)), float)
    R = rot_matrix((0, bx.yaw, 0)) @ rot_matrix(bx.rot)
    cen = np.array([x0 + w / 2, y0 + h / 2, z0 + d / 2])
    S = rot_matrix((0, bx.spin, 0)) if bx.spin else None
    out = []
    for fn, (nrm, uax, vax) in FACES.items():
        if fn in bx.hide:
            continue
        n = np.array(nrm, float)
        u = np.array(uax, float)
        v = np.array(vax, float)
        dims = np.array([w, h, d])
        fw = float(abs(u) @ dims)
        fh = float(abs(v) @ dims)
        center = (c[0] + c[1]) / 2 + n * dims / 2
        tl = center - u * fw / 2 - v * fh / 2
        verts = np.array([tl, tl + u * fw, tl + u * fw + v * fh, tl + v * fh])
        if S is not None:
            verts = (verts - cen) @ S.T + cen
        verts = (verts - piv) @ R.T + piv
        W = max(1, int(round(fw * T)))
        H = max(1, int(round(fh * T)))
        if bx.skinmap and fn in bx.skinmap:
            r = bx.skinmap[fn]
            out.append(Face(bx.bone, verts, placeholder_crop(r, W, H), None, None, tuple(r)))
            continue
        pf = fn
        if bx.mirror and fn in "ew":
            pf = "w" if fn == "e" else "e"
        res = bx.paint(pf, W, H, bx) if callable(bx.paint) else bx.paint[pf](W, H, bx)
        glow = None
        if isinstance(res, tuple):
            res, glow = res
        img = np.asarray(res, np.uint8)
        if img.shape[:2] != (H, W):
            img = np.array(Image.fromarray(img).resize((W, H), Image.NEAREST))
        if bx.mirror:
            img = img[:, ::-1]
            glow = None if glow is None else glow[:, ::-1]
        out.append(Face(bx.bone, verts, img.copy(), None if glow is None else glow.copy()))
    return out


def _side_paint(paint):
    """角を落とすキューブの絵: 前後の面の飾り (編み上げなど) を写さないよう、横の面 (e) の絵で塗る。"""
    def p(face, W, H, box):
        f = "e" if face in "nsew" else face
        return paint(f, W, H, box) if callable(paint) else paint[f](W, H, box)
    return p


# ------------------------------------------------------------------ 着ている人のスキンの代わり (見本の絵だけ)
_PLACE = None


def placeholder_skin():
    """見本の絵で own_ の面に貼る、Steve に似た 64x64 のスキン。"""
    global _PLACE
    if _PLACE is not None:
        return _PLACE
    a = np.zeros((64, 64, 4), np.uint8)
    skin, hair, eye_w, eye_b = (196, 150, 118, 255), (66, 44, 30, 255), (236, 236, 236, 255), (60, 70, 140, 255)
    a[0:16, 0:32] = skin
    a[0:8, 8:16] = hair
    for (x0, x1) in ((0, 8), (16, 32)):
        a[8:11, x0:x1] = hair
    a[8:10, 8:16] = hair
    a[12, 9], a[12, 10], a[12, 13], a[12, 14] = eye_w, eye_b, eye_b, eye_w
    a[14, 11:13] = (150, 100, 80, 255)
    a[16:32, 40:56] = skin
    a[48:64, 32:48] = skin
    _PLACE = a
    return a


def placeholder_crop(r, W, H):
    u0, v0, u1, v1 = r
    a = placeholder_skin()
    x0, x1 = sorted((u0, u1))
    y0, y1 = sorted((v0, v1))
    c = a[int(y0):max(int(y0) + 1, int(np.ceil(y1))), int(x0):max(int(x0) + 1, int(np.ceil(x1)))]
    if u1 < u0:
        c = c[:, ::-1]
    if v1 < v0:
        c = c[::-1]
    return np.array(Image.fromarray(c).resize((max(1, W), max(1, H)), Image.NEAREST))


# ------------------------------------------------------------------ アトラス
def pack(faces, pad=1):
    """高さの順に棚へ詰める。正方形の 2 の累乗。"""
    faces = [f for f in faces if f.skin is None]
    order = sorted(range(len(faces)), key=lambda i: (-faces[i].img.shape[0], -faces[i].img.shape[1]))
    area = sum((f.img.shape[0] + pad * 2) * (f.img.shape[1] + pad * 2) for f in faces)
    size = 64
    while size * size < area * 1.15:
        size *= 2
    while True:
        x = y = row = 0
        ok = True
        for i in order:
            hh, ww = faces[i].img.shape[:2]
            ww2, hh2 = ww + pad * 2, hh + pad * 2
            if x + ww2 > size:
                x = 0
                y += row
                row = 0
            if y + hh2 > size or ww2 > size:
                ok = False
                break
            faces[i].uv = (x + pad, y + pad)
            x += ww2
            row = max(row, hh2)
        if ok:
            break
        size *= 2
    atlas = np.zeros((size, size, 4), np.uint8)
    glow = np.zeros((size, size), bool)
    for f in faces:
        hh, ww = f.img.shape[:2]
        x, y = f.uv
        # 縁を 1 画素ずつ伸ばす (にじみ止め)
        atlas[y - 1:y + hh + 1, x - 1:x + ww + 1] = np.pad(f.img, ((1, 1), (1, 1), (0, 0)), mode="edge")
        if f.glow is not None:
            glow[y:y + hh, x:x + ww] = f.glow
    return size, atlas, glow


def _retry(fn, tries=8):
    """書き込みがまれに一瞬拒まれる (Errno 22) ので、少し待ってやり直す。"""
    import time
    for k in range(tries):
        try:
            return fn()
        except OSError:
            if k == tries - 1:
                raise
            time.sleep(0.4)


def export(model: Model, out_dir: Path, texture: str):
    faces = model.faces()
    size, atlas, glow = pack(faces)
    bones = {}
    for name, b in model.bones.items():
        bones[name] = dict(parent=b["parent"], pivot=[round(float(x), 3) for x in b["pivot"]], vertices=[], uvs=[], triangles=[])
    total = 0
    inset = 0.02
    for f in faces:
        b = bones[f.bone]
        base = len(b["vertices"])
        if f.skin is not None:
            # 着ている人のスキンの絵 (64x64)
            u0, v0, u1, v1 = f.skin
            k = 0.01
            corners = [(u0 + k, v0 + k), (u1 - k, v0 + k), (u1 - k, v1 - k), (u0 + k, v1 - k)]
            sz = 64.0
        else:
            hh, ww = f.img.shape[:2]
            x, y = f.uv
            corners = [(x + inset, y + inset), (x + ww - inset, y + inset), (x + ww - inset, y + hh - inset), (x + inset, y + hh - inset)]
            sz = size
        for p, (cu, cv) in zip(f.verts, corners):
            b["vertices"].append([round(float(q), 4) for q in p])
            b["uvs"].append([round(cu / sz, 6), round(cv / sz, 6)])
        # 外向き (geo で反時計回り) にそろえる
        a, bb, c = f.verts[0], f.verts[1], f.verts[2]
        nrm = np.cross(bb - a, c - a)
        want = f.verts.mean(0) - face_center_hint(f)
        tris = [[0, 1, 2], [0, 2, 3]]
        if np.dot(nrm, outward(f)) < 0:
            tris = [[0, 2, 1], [0, 3, 2]]
        for t in tris:
            b["triangles"].append([base + t[0], base + t[1], base + t[2]])
            total += 1
        del want
    # 形の無い骨も、形のある子を持てば残す (回る付け根など)
    keep = {k for k, v in bones.items() if v["vertices"] or k in PIVOTS}
    grew = True
    while grew:
        grew = False
        for k, v in bones.items():
            if k in keep and v["parent"] and v["parent"] not in keep:
                keep.add(v["parent"])
                grew = True
    bones = {k: v for k, v in bones.items() if k in keep}
    for v in bones.values():
        v["triangle_count"] = len(v["triangles"])
    data = {"format_version": 1, "name": model.name, "units": "1/16 block, y up, front = -Z (geo absolute)", "uv": "normalized, origin top-left",
            "texture": texture, "outfit": model.outfit, "texture_size": [size, size], "texels_per_unit": T, "triangle_count": total, "bones": bones}
    out_dir.mkdir(parents=True, exist_ok=True)
    _retry(lambda: (out_dir / "mesh.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8"))
    _retry(lambda: Image.fromarray(atlas, "RGBA").save(out_dir / "atlas.png"))
    if glow.any():
        g = np.zeros_like(atlas)
        g[glow] = atlas[glow]
        Image.fromarray(g, "RGBA").save(out_dir / "glow.png")
    elif (out_dir / "glow.png").exists():
        (out_dir / "glow.png").unlink()
    return data, atlas, glow, faces


def outward(f: Face):
    # 箱の面は u × v の逆が外向き (u 右、v 下 → 外から見て時計回り)。頂点の並び 左上→右上→右下 の外積は外向きの逆になる
    u = f.verts[1] - f.verts[0]
    v = f.verts[3] - f.verts[0]
    return -np.cross(u, v)


def face_center_hint(f: Face):
    return np.zeros(3)


# ------------------------------------------------------------------ 見本の絵 (ソフトの描画)
def pose_faces(model: Model, faces, pose):
    """pose: {骨: (rx, ry, rz)} を付け根のまわりにかけ、子にも伝える。"""
    mats = {}

    def world(bn):
        if bn in mats:
            return mats[bn]
        b = model.bones[bn]
        R = rot_matrix(pose.get(bn, (0, 0, 0)))
        p = np.array(b["pivot"], float)
        M = np.eye(4)
        M[:3, :3] = R
        M[:3, 3] = p - R @ p
        if b["parent"] and b["parent"] in model.bones:
            M = world(b["parent"]) @ M
        mats[bn] = M
        return M

    out = []
    for f in faces:
        M = world(f.bone)
        v = f.verts @ M[:3, :3].T + M[:3, 3]
        out.append(Face(f.bone, v, f.img, f.glow, f.uv, f.skin))
    return out


def render(faces, yaw=30, pitch=10, scale=14, size=(640, 760), center=(0, 19, 0), bg=(36, 34, 44), light=True):
    W, H = size
    img = np.zeros((H, W, 3), float)
    img[:] = bg
    zbuf = np.full((H, W), np.inf)
    ya, pa = math.radians(yaw), math.radians(-pitch)
    Ry = np.array([[math.cos(ya), 0, math.sin(ya)], [0, 1, 0], [-math.sin(ya), 0, math.cos(ya)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(pa), -math.sin(pa)], [0, math.sin(pa), math.cos(pa)]])
    R = Rx @ Ry
    cen = np.array(center, float)
    L1 = np.array([0.2, 1.0, -0.7])
    L1 /= np.linalg.norm(L1)
    L2 = np.array([-0.2, 1.0, 0.7])
    L2 /= np.linalg.norm(L2)
    for f in faces:
        v = (f.verts - cen) @ R.T
        # 画面: 体の右 (-X) を画面の左に (前から見た向き)。カメラは -Z 側から +Z を見る
        sx = W / 2 + v[:, 0] * scale
        sy = H / 2 - v[:, 1] * scale
        sz = v[:, 2]
        nrm = outward(f)
        nl = np.linalg.norm(nrm)
        nrm = nrm / nl if nl > 1e-9 else np.array([0, 1, 0])
        if light:
            # マイクラの面の明るさ (上 1.0・前後 0.8・横 0.6・下 0.5)。回した後の向きで決める
            nv = nrm
            br = 0.6 * nv[0] ** 2 + 0.8 * nv[2] ** 2 + (1.0 if nv[1] > 0 else 0.5) * nv[1] ** 2
        else:
            br = 1.0
        hh, ww = f.img.shape[:2]
        uvq = np.array([[0, 0], [ww, 0], [ww, hh], [0, hh]], float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = np.array([[sx[i], sy[i]] for i in tri])
            Z = np.array([sz[i] for i in tri])
            U = uvq[list(tri)]
            x0, x1 = int(max(0, math.floor(P[:, 0].min()))), int(min(W - 1, math.ceil(P[:, 0].max())))
            y0, y1 = int(max(0, math.floor(P[:, 1].min()))), int(min(H - 1, math.ceil(P[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            den = (P[1, 1] - P[2, 1]) * (P[0, 0] - P[2, 0]) + (P[2, 0] - P[1, 0]) * (P[0, 1] - P[2, 1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            a = ((P[1, 1] - P[2, 1]) * (xs - P[2, 0]) + (P[2, 0] - P[1, 0]) * (ys - P[2, 1])) / den
            b = ((P[2, 1] - P[0, 1]) * (xs - P[2, 0]) + (P[0, 0] - P[2, 0]) * (ys - P[2, 1])) / den
            c = 1 - a - b
            m = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6)
            if not m.any():
                continue
            z = a * Z[0] + b * Z[1] + c * Z[2]
            u = np.clip((a * U[0, 0] + b * U[1, 0] + c * U[2, 0]).astype(int), 0, ww - 1)
            vv = np.clip((a * U[0, 1] + b * U[1, 1] + c * U[2, 1]).astype(int), 0, hh - 1)
            tex = f.img[vv, u]
            m &= tex[..., 3] >= 128
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            m &= z < sub
            if not m.any():
                continue
            col = tex[..., :3].astype(float)
            g = f.glow[vv, u] if f.glow is not None else np.zeros_like(m)
            col = np.where(g[..., None], col, col * br)
            region = img[y0:y1 + 1, x0:x1 + 1]
            region[m] = col[m]
            sub[m] = z[m]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def mannequin_boxes(model: Model):
    """服だけのスキンの見本用: バニラの体 (Steve の大きさ) を無地の箱で。頭は顔の目印つき。"""
    import paint as P
    body = P.cloth("#c9a58c", "#e0c2ab", "#a88468", edges=False)
    shirt = P.cloth("#4f8fa8", "#6faac0", "#3a6f84", edges=False)
    pants = P.cloth("#3d4a8a", "#55629f", "#2c3668", edges=False)

    def head(face, W, H, box):
        img = body(face, W, H, box)
        if face == "n":
            img[H // 2:H // 2 + 3, W // 4:W // 4 + 3] = (60, 50, 90, 255)
            img[H // 2:H // 2 + 3, W - W // 4 - 3:W - W // 4] = (60, 50, 90, 255)
        if face in "nsewu":
            img[: H // 4 if face != "u" else H] = (70, 50, 35, 255)
        return img
    bx = [Box("head", (-4, 24, -4), (8, 8, 8), head), Box("body", (-4, 12, -2), (8, 12, 4), shirt),
          Box("armR", (-8, 12, -2), (4, 12, 4), shirt), Box("armL", (4, 12, -2), (4, 12, 4), shirt),
          Box("legR", (-4, 0, -2), (4, 12, 4), pants), Box("legL", (0, 0, -2), (4, 12, 4), pants)]
    out = []
    for b in bx:
        out.extend(box_faces(b))
    return out


def sheet(model: Model, path: Path, poses=None, views=((20, 8), (160, 8), (-60, 6)), scale=13, size=(560, 720), title=None):
    faces = model.faces() + (mannequin_boxes(model) if model.outfit else [])
    poses = poses or [{}]
    ims = []
    for pose in poses:
        pf = pose_faces(model, faces, pose)
        for yaw, pitch in views:
            ims.append(render(pf, yaw, pitch, scale, size))
    W = sum(i.width for i in ims)
    out = Image.new("RGB", (W, size[1]), (36, 34, 44))
    x = 0
    for i in ims:
        out.paste(i, (x, 0))
        x += i.width
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path)
    return out


def wardrobe(model: Model, path: Path, yaw=22, pitch=8):
    """衣装棚の絵 (前の 3/4 の全身、128x192、背景は透明)。体の高さに合わせて縮める。服だけのものは人形に着せて描く。"""
    faces = model.faces() + (mannequin_boxes(model) if model.outfit else [])
    ys = np.concatenate([f.verts[:, 1] for f in faces])
    top, bot = float(ys.max()), float(min(0.0, ys.min()))
    scale = 360 / (top - bot + 2)
    im = render(faces, yaw, pitch, scale, (256, 384), center=(0, (top + bot) / 2, 0), bg=(0, 0, 0))
    a = np.array(im.convert("RGBA"))
    a[..., 3] = np.where((a[..., :3] == 0).all(-1), 0, 255)
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a, "RGBA").resize((128, 192), Image.LANCZOS).save(path)


def install(model: Model, sid: str, dst: Path):
    """export の出力 (dst) を mod の中へ: lowpoly/skin_full/<id>.json・textures/lowpoly/skin_full/<id>(_glow).png・衣装棚の絵。"""
    import shutil
    assets = ROOT / "src/main/resources/assets/skyabyss"
    (assets / "lowpoly/skin_full").mkdir(parents=True, exist_ok=True)
    (assets / "textures/lowpoly/skin_full").mkdir(parents=True, exist_ok=True)
    shutil.copy(dst / "mesh.json", assets / "lowpoly/skin_full" / f"{sid}.json")
    shutil.copy(dst / "atlas.png", assets / "textures/lowpoly/skin_full" / f"{sid}.png")
    g = assets / "textures/lowpoly/skin_full" / f"{sid}_glow.png"
    if (dst / "glow.png").exists():
        shutil.copy(dst / "glow.png", g)
    elif g.exists():
        g.unlink()
    wardrobe(model, assets / "textures/gui/skins" / f"{sid}.png")


def main(sid: str, build, argv):
    """スキンの台本の入り口: 見本の絵と mesh.json・atlas.png を docs/review/skins/full/<id>/ へ。install で mod の中へ。"""
    model = build()
    dst = ROOT / "docs/review/skins/full" / sid
    data, atlas, glow, faces = export(model, dst, "skin_full/" + sid)
    sheet(model, dst / "preview.png", views=((15, 10), (150, 10), (-70, 8)), scale=20, size=(620, 960))
    print(sid, data["triangle_count"], "triangles", data["texture_size"], "atlas")
    if "install" in argv:
        install(model, sid, dst)
        print("installed")
