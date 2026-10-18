"""防具用の低ポリ道具 (lpkit / weapons/wkit の上に載せる薄い層)。

座標: 1 単位 = 1/16 ブロック。プレイヤーの骨 (geo の絶対座標。頭 y24-32、胴 y12-24、脚 y0-12、正面 -Z)。
右腕・右脚は x < 0 (armorRight*)、左は x > 0。防具の面は骨の名前を持ち、メッシュは骨ごとに出す。
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
LP = HERE.parent
ROOT = HERE.parents[3]
for _p in (HERE, LP / "weapons", LP, ROOT / "tools/rough"):
    sys.path.insert(0, str(_p))
import lpkit  # noqa: E402
from lpkit import (tube, spike, loft, poly, transform, tri_count, build_atlas, rot_x, rot_y, rot_z,  # noqa: E402,F401
                   nrm, Face, edge_fields, fill_outside)
from lpkit import _mk  # noqa: E402
import wkit  # noqa: E402
from bone_sentinel_lp import hash1, vnoise, BAYER  # noqa: E402

OUT = ROOT / "docs/review/lowpoly_roughs/armor"

PIV = {
    "armorHead": (0, 24, 0), "armorBody": (0, 24, 0),
    "armorRightArm": (-5, 22, 0), "armorLeftArm": (5, 22, 0),
    "armorRightLeg": (-1.9, 12, 0), "armorLeftLeg": (1.9, 12, 0),
    "armorRightBoot": (-1.9, 12, 0), "armorLeftBoot": (1.9, 12, 0),
}
PIECES = {
    "helmet": ["armorHead"],
    "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
    "leggings": ["armorRightLeg", "armorLeftLeg"],
    "boots": ["armorRightBoot", "armorLeftBoot"],
}
BONE_PIECE = {b: p for p, bs in PIECES.items() for b in bs}
ARM = {-1: "armorRightArm", 1: "armorLeftArm"}
LEG = {-1: "armorRightLeg", 1: "armorLeftLeg"}
BOOT = {-1: "armorRightBoot", 1: "armorLeftBoot"}


def V(*a):
    return np.array(a, float)


def tag(faces, bone):
    for f in faces:
        f.bone = bone
    return faces


# ------------------------------------------------------------------ 形

def bx(bone, mat, c, size, rot=None, taper=(1.0, 1.0), skip=(), shear=0.0):
    """箱 (上面の x, z の倍率 taper)。skip に 'b' 'u' で下・上を作らない。"""
    bu = tuple(s for s in skip if s in ("b", "u"))
    out = tag(wkit.block(mat, c, size, rot=rot, taper=taper, skip=bu, shear=shear), bone)
    dirs = {"+z": (0, 0, 1), "-z": (0, 0, -1), "+x": (1, 0, 0), "-x": (-1, 0, 0)}
    for s in skip:
        if s in dirs:
            d = np.array(dirs[s], float)
            out = [f for f in out if np.dot(f.normal, d) < 0.8]
    return out


def ext(bone, mat, pts, thick, c=(0, 0, 0), rot=None, mats=None):
    return tag(wkit.extrude(mat, pts, thick, c=c, rot=rot, mats=mats), bone)


def feather(bone, mat, base, tip, w, th=0.6, mats=None, big=True, n=None):
    """羽: 先の尖った多角形の板。広い面の向きは n (省略なら d に垂直な x か z のうち d の成分が小さい軸)。"""
    base, tip = V(*base), V(*tip)
    d = tip - base
    L = float(np.linalg.norm(d))
    y = d / L
    if n is None:
        n = V(1, 0, 0) if abs(y[0]) < abs(y[2]) else V(0, 0, 1)
    n = V(*n)
    z = n - y * float(np.dot(n, y))
    z = z / np.linalg.norm(z)
    x = np.cross(y, z)
    R = np.stack([x, y, z], 1)
    if big:
        pts = [(0, 0), (w * 0.8, L * 0.25), (w, L * 0.6), (0, L), (-w, L * 0.6), (-w * 0.8, L * 0.25)]
    else:
        pts = [(0, 0), (w, L * 0.4), (0, L), (-w, L * 0.4)]
    return tag(wkit.extrude(mat, pts, th, c=base, rot=R, mats=mats), bone)


def spk(bone, mat, base, d, L, r, sides=4, rings=2, bend=(0, 0, 0), phase=45.0, hint=(0, 1, 0), rb=None):
    return tag(spike(bone, mat, base, d, L, r, sides=sides, rings=rings, bend=bend, hint=hint, phase=phase, rb=rb), bone)


def crv(bone, mat, pts, ra, rb=None, sides=4, caps=(False, False), phase=45.0, hint=(0, 0, 1)):
    return tube(bone, mat, [V(*p) for p in pts], ra, rb, sides=sides, hint=hint, phase=phase, caps=caps)


def rr(y, hx, zf, zb, cut=1.0, keel=0.0, cx=0.0, dy=None, hxb=None):
    """角を落とした輪 (前 = -Z)。keel > 0 で前の中央が尖る。頂点の順は右前から時計まわり。
    dy は頂点ごとの y のずれ (歯・裾)。hxb は後ろ側の幅。"""
    hb = hx if hxb is None else hxb
    pts = []
    if keel > 0:
        pts.append((0.0, zf - keel))
    pts += [(hx - cut, zf), (hx, zf + cut), (hb, zb - cut), (hb - cut, zb),
            (-(hb - cut), zb), (-hb, zb - cut), (-hx, zf + cut), (-(hx - cut), zf)]
    out = []
    for i, (x, z) in enumerate(pts):
        yy = y + (dy[i] if dy is not None else 0.0)
        out.append(V(cx + x, yy, z))
    return out


def nver(keel=0.0):
    return 9 if keel > 0 else 8


def shell(bone, mat, rings, caps=(False, False), mats=None):
    return tag(loft(bone, mat, rings, caps=caps, mats=mats), bone)


def hide_front(faces, ymax, zcos=0.45):
    """顔の前を開ける: y が ymax 未満で前 (-Z) を向く面を消す。"""
    out = []
    for f in faces:
        if f.verts[:, 1].max() < ymax + 1e-6 and f.normal[2] < -zcos:
            continue
        out.append(f)
    return out


def at(faces, R=None, t=(0, 0, 0)):
    return transform(faces, R, t)


def mirror_x(faces, bone=None):
    out = []
    for f in faces:
        g = copy.deepcopy(f)
        g.verts = (f.verts * np.array([-1, 1, 1]))[::-1].copy()
        g.ref = f.ref * np.array([-1, 1, 1])
        g.normal = f.normal * np.array([-1, 1, 1])
        g.loc = g.verts.copy()
        if bone:
            g.bone = bone
        out.append(g)
    return out


# ------------------------------------------------------------------ プレイヤーの箱

BOXES = {  # bone -> (lo, hi)
    "armorHead": ((-4, 24, -4), (4, 32, 4)),
    "armorBody": ((-4, 12, -2), (4, 24, 2)),
    "armorRightArm": ((-8, 12, -2), (-4, 24, 2)),
    "armorLeftArm": ((4, 12, -2), (8, 24, 2)),
    "armorRightLeg": ((-4, 0, -2), (0, 12, 2)),
    "armorLeftLeg": ((0, 0, -2), (4, 12, 2)),
}
SKIN, SHIRT, PANTS, HAIR = (196, 150, 120), (70, 92, 150), (60, 60, 110), (58, 40, 32)


def mannequin():
    """プレイヤーの箱 (平色)。顔は肌色に目 2 つ、髪が頭の上と後ろ。(faces, [(faces, rgb)])"""
    groups = []
    for bone, (lo, hi) in BOXES.items():
        c = [(lo[i] + hi[i]) / 2 for i in range(3)]
        s = [hi[i] - lo[i] for i in range(3)]
        rgb = SKIN if bone == "armorHead" else SHIRT if bone in ("armorBody",) else PANTS if "Leg" in bone else SHIRT
        fs = tag(wkit.block("mq", c, s), bone)
        if bone in ("armorRightArm", "armorLeftArm"):
            sx = 1 if hi[0] > 0 else -1
            sl = tag(wkit.block("mq", (sx * 6, 20, 0), (4, 8, 4)), bone)
            hd = tag(wkit.block("mq", (sx * 6, 14, 0), (4, 4, 4)), bone)
            groups.append((sl, SHIRT))
            groups.append((hd, SKIN))
            continue
        if bone == "armorHead":
            hair = [f for f in fs if f.normal[1] > 0.5 or f.normal[2] > 0.5]
            skin = [f for f in fs if not (f.normal[1] > 0.5 or f.normal[2] > 0.5)]
            groups.append((hair, HAIR))
            groups.append((skin, SKIN))
            eyes = []
            for s_ in (-1, 1):
                q = [V(s_ * 1.0 - 0.5 * 1, 27.2, -4.03), V(s_ * 1.0 + 0.5, 27.2, -4.03),
                     V(s_ * 1.0 + 0.5, 28.2, -4.03), V(s_ * 1.0 - 0.5, 28.2, -4.03)]
                q = [V(s_ * 2.0 + dx, y, -4.03) for dx, y in ((-0.6, 27.0), (0.6, 27.0), (0.6, 28.0), (-0.6, 28.0))]
                f = _mk(q, bone, "mq", V(0, 28, 0))
                if f:
                    eyes.append(f)
            groups.append((eyes, (40, 36, 60)))
            continue
        if "Leg" in bone:
            sx = 1 if hi[0] > 0 else -1
            pa = tag(wkit.block("mq", (sx * 2, 8, 0), (4, 8, 4)), bone)
            sh = tag(wkit.block("mq", (sx * 2, 2, 0), (4, 4, 4)), bone)
            groups.append((pa, PANTS))
            groups.append((sh, (46, 46, 60)))
            continue
        groups.append((fs, rgb))
    return groups


def cull_inside(faces, margin=0.05):
    """プレイヤーの箱の内側に完全に埋まる面を捨てる (見えない面)。"""
    out = []
    for f in faces:
        hid = False
        for lo, hi in BOXES.values():
            lo_, hi_ = np.array(lo) + margin, np.array(hi) - margin
            if np.all(f.verts >= lo_ - 1e-6) and np.all(f.verts <= hi_ + 1e-6):
                hid = True
                break
        if not hid:
            out.append(f)
    return out


# ------------------------------------------------------------------ 姿勢 (骨ごとの回転)

def pose_faces(faces, pose):
    """pose: {bone: (rx, ry, rz)} 度。骨の pivot まわりに回す。"""
    out = []
    for f in faces:
        b = getattr(f, "bone", None)
        if b in pose:
            R = rot_z(pose[b][2]) @ rot_y(pose[b][1]) @ rot_x(pose[b][0])
            g = copy.copy(f)
            p = np.array(PIV[b], float)
            g.verts = (f.verts - p) @ R.T + p
            g.normal = R @ f.normal
            g.ref = R @ (f.ref - p) + p
            out.append(g)
        else:
            out.append(f)
    return out


# ------------------------------------------------------------------ 出力

def export_mesh(piece_faces, size, path, name, texture, glowfile, tex=2.0):
    """骨ごとのメッシュ JSON (15 §9.1 の形式。頂点はモデル全体の絶対座標)。"""
    bones = {}
    INSET = 0.02
    total = 0
    for f in piece_faces:
        b = bones.setdefault(f.bone, dict(parent=None, pivot=list(PIV[f.bone]), vertices=[], uvs=[], triangles=[]))
        base = len(b["vertices"])
        cen = f.uv.mean(0)
        for p, uv in zip(f.verts, f.uv):
            d = cen - uv
            nn = np.linalg.norm(d)
            uv = uv + d / nn * min(INSET, nn * 0.4) if nn > 1e-9 else uv
            b["vertices"].append([round(float(x), 3) for x in p])
            b["uvs"].append([round(float(uv[0]) / size, 5), round(float(uv[1]) / size, 5)])
        for a, c, d in f.tris():
            b["triangles"].append([base + a, base + c, base + d])
            total += 1
    for b in bones.values():
        b["triangle_count"] = len(b["triangles"])
    data = {"format_version": 1, "name": name,
            "units": "1/16 block, y up, front = -Z (geo absolute coordinates of the player armor bones)",
            "uv": "normalized, origin top-left (v down)", "texture": texture, "glowmask": glowfile,
            "texture_size": [size, size], "texels_per_unit": tex, "uv_inset_texels": INSET,
            "triangle_count": total, "bones": bones}
    Path(path).write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def save_img(arr, path):
    Image.fromarray(arr, "RGBA").save(path)
