"""五章「深淵」の防具「淵潜りの鎧」(4 部位、レア) の低ポリ 3 案。四章の旅装 (../ch4/armor_ch4.py) と同じ作り (防具の低ポリ様式 C の箱・輪・板 = armor/akit.py)。
深淵の色の言葉 (錨鉄の青灰・紫黒の布・骨の白紫・灯りの琥珀・縁は階級の色 = レアの青い鋼) で塗り分ける。
(A 錨鉄の潜り鎧 = 鋲の打たれた錨鉄の板・額の角灯・胸を斜めに渡る鎖・丸い肩の 3 段、
 B 灯り番の外套 = 紫黒の長い外套・錨鉄の縁・腰に吊った角灯・頭巾の額の灯、
 C 肋と鎖 = 鎖の帷子に肋のような白紫の板・面頬の覗き窓が光る)

実行: python -I tools/rough/lowpoly/gear_gaps/armor_abyss.py
出力: docs/review/roughs_gear_gaps/raw/armor_abyss/{a,b,c}/<部位>_{mesh.json,atlas.png,glowmask.png}、
      docs/review/roughs_gear_gaps/raw/armor_abyss/icons/{a,b,c}_<部位>_32.png、docs/review/roughs_gear_gaps/abyss_diver_armor/compare.png (シート)
入れ方: tools/install/install_abyss_gear.py (選んだ案を assets へ)
"""
from __future__ import annotations

import importlib
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
ARM = ROOT / "tools/rough/lowpoly/armor"
sys.path.insert(0, str(ARM))

import akit  # noqa: E402  (armor/akit.py: 箱・輪・板・骨・アトラス・出力)
from akit import (V, bx, ext, shell, rr, rot_z, rot_x, LEG, ARM as ARMB, BOOT, BONE_PIECE,  # noqa: E402,F401
                  build_atlas, edge_fields, fill_outside, tri_count, export_mesh, save_img, cull_inside,
                  hash1, vnoise, BAYER, pose_faces)
import lpkit  # noqa: E402
from wkit import pick, ramp, LIGHT  # noqa: E402
import aview  # noqa: E402  (armor/aview.py: 描画)
import pose_check  # noqa: E402  (armor/pose_check.py: 姿勢の重なり検査。読むだけ)
import scene  # noqa: E402  (weapons/scene.py: aview が使う描画の土台。アイコンの 1 部位描画に使う)

PIECE_JP = dict(helmet="兜 (頭巾)", chestplate="胸 (上着と肩)", leggings="脚 (旅袴)", boots="靴 (煤の脚絆)")

# ------------------------------------------------------------------ 色 (深淵: 錨鉄・紫黒の布・骨の白紫・灯りの琥珀・レアの青い鋼)
A8 = np.array
AIRON = A8([(26, 28, 38), (42, 46, 60), (62, 68, 86), (88, 96, 116), (124, 132, 152)])
VCLOTH = A8([(16, 12, 26), (26, 20, 42), (40, 30, 62), (56, 44, 84), (76, 60, 108)])
CHAIN = A8([(30, 30, 40), (48, 48, 62), (70, 72, 88), (98, 100, 118), (132, 134, 150)])
RIB = A8([(76, 68, 96), (112, 104, 134), (150, 142, 172), (190, 182, 210), (226, 220, 238)])
LEATHER_V = A8([(24, 18, 26), (38, 28, 40), (54, 42, 56), (74, 58, 76)])
STEEL_R = A8([(34, 48, 72), (52, 76, 110), (78, 110, 152), (112, 150, 196), (160, 196, 236)])
LAMPG = A8([(200, 110, 30), (245, 160, 50), (255, 205, 110), (255, 238, 190)])

OUT = ROOT / "docs/review/roughs_gear_gaps"
MESH_OUT = OUT / "raw/armor_abyss"
SHEET = OUT / "abyss_diver_armor/compare.png"
TITLE_JP = "淵潜りの鎧"
# 案ごとの材質の色 (役割 -> パレット)。役割 main/leather/sec/edge/acc/cloth/trim/gem は形の中で使う名前。
OPTIONS = {
    "a": dict(main=AIRON, leather=LEATHER_V, sec=AIRON, edge=RIB, acc=CHAIN, cloth=VCLOTH, trim=STEEL_R, gem=LAMPG),
    "b": dict(main=VCLOTH, leather=LEATHER_V, sec=AIRON, edge=RIB, acc=AIRON, cloth=VCLOTH, trim=STEEL_R, gem=LAMPG),
    "c": dict(main=CHAIN, leather=LEATHER_V, sec=AIRON, edge=RIB, acc=CHAIN, cloth=VCLOTH, trim=STEEL_R, gem=LAMPG),
}
NAMES = {"a": "A 錨鉄の潜り鎧", "b": "B 灯り番の外套", "c": "C 肋と鎖"}


# ------------------------------------------------------------------ 形 (部位ごと。戻り値は面のリスト)

def lamp(b, c, w=1.4, h=1.6, d=1.0):
    """額や腰の小さな角灯: 錨鉄の枠 (上下の板) と光る芯。枠は芯へ沈める"""
    x, y, z = c
    f = bx(b, "gem", (x, y, z), (w, h, d), skip=("b",))
    f += bx(b, "trim", (x, y + h / 2, z), (w + 0.5, 0.5, d + 0.4))
    f += bx(b, "trim", (x, y - h / 2, z), (w + 0.5, 0.5, d + 0.4))
    return f


def helmet(o):
    b = "armorHead"
    f = []
    if o == "a":        # 潜り兜: 輪 3 段で頭を覆い、顔の前は開ける。額に角灯、耳に丸い錨鉄の板、頭頂に低い畝
        f += shell(b, "main", [rr(29.6, 4.9, -5.0, 5.0, cut=1.2), rr(31.5, 4.7, -4.8, 4.9, cut=1.2),
                               rr(33.0, 3.6, -3.6, 3.8, cut=1.2)], caps=(False, "main"))
        for s in (-1, 1):
            f += bx(b, "sec", (s * 5.2, 28.4, 0.2), (1.0, 3.6, 3.6), taper=(0.8, 0.8), skip=("b",))
        f += lamp(b, (0, 31.0, -5.3))
        f += bx(b, "trim", (0, 33.8, 0.2), (1.0, 1.0, 7.0), taper=(0.7, 1.0), skip=("b",))
    elif o == "b":      # 頭巾: 布の輪 3 段、額に錨鉄の帯と小さな灯、後ろの垂れ
        f += shell(b, "main", [rr(29.6, 4.85, -4.9, 5.0, cut=1.2), rr(31.4, 4.6, -4.6, 4.8, cut=1.2),
                               rr(32.8, 3.4, -3.4, 3.7, cut=1.2)], caps=(False, "main"))
        for s in (-1, 1):
            f += bx(b, "main", (s * 4.9, 27.6, 1.4), (0.9, 4.0, 4.8), taper=(0.8, 0.9), skip=("b",))
        f += bx(b, "sec", (0, 30.4, -5.0), (8.2, 1.0, 0.8), skip=("b", "+z"))
        f += bx(b, "gem", (0, 30.4, -5.5), (1.0, 0.8, 0.4), skip=("b",))
        f += bx(b, "main", (0, 25.6, 5.3), (7.0, 6.0, 1.0), taper=(0.95, 1.0), skip=("b", "-z"))
    else:               # 帷子の頭巾に肋の冠。顔の下半分は面頬 (覗き窓の光る筋)
        f += shell(b, "main", [rr(29.6, 4.85, -4.9, 5.0, cut=1.2), rr(31.4, 4.6, -4.6, 4.8, cut=1.2),
                               rr(32.8, 3.4, -3.4, 3.7, cut=1.2)], caps=(False, "main"))
        f += bx(b, "sec", (0, 27.2, -5.1), (8.6, 2.2, 0.8), skip=("b", "+z"))
        f += bx(b, "gem", (0, 28.6, -5.2), (6.4, 0.5, 0.5), skip=("b",))
        for x in (-2.4, 0, 2.4):
            f += bx(b, "edge", (x, 33.4, 0.2), (0.9, 2.0 if x == 0 else 1.4, 6.0), taper=(0.6, 1.0), skip=("b",))
    return f


def chest(o):
    B = "armorBody"
    f = []
    f += shell(B, "main", [rr(12.3, 4.6, -2.7, 2.6, cut=0.9), rr(18.2, 4.6, -2.9, 2.8, cut=0.9),
                           rr(24.2, 4.6, -2.7, 2.7, cut=0.9)])
    f += bx(B, "leather", (0, 13.0, 0), (9.4, 1.2, 5.6), skip=("b", "u"))
    f += bx(B, "trim", (0, 13.0, -2.95), (1.4, 1.0, 0.4), skip=("b",))
    if o == "a":        # 胸の錨鉄の板 (上下 2 枚) と、右肩から左腰へ渡る鎖、胸の小さな灯
        f += bx(B, "sec", (0, 21.2, -3.0), (7.6, 4.6, 0.7), taper=(0.92, 1.0))
        f += bx(B, "sec", (0, 16.6, -3.0), (6.8, 3.6, 0.7), taper=(1.0, 0.9))
        for i in range(6):
            t = i / 5
            f += bx(B, "acc", (3.4 - 6.6 * t, 23.6 - 9.4 * t, -3.5), (1.0, 1.0, 0.5), rot=rot_z(35))
        f += lamp(B, (2.6, 21.6, -3.6), w=1.1, h=1.2, d=0.6)
    elif o == "b":      # 外套の前立てと錨鉄の縁、腰に吊った角灯
        f += bx(B, "trim", (0, 18.6, -3.1), (1.4, 11.0, 0.5))
        f += shell(B, "sec", [rr(12.0, 4.85, -3.1, 3.0, cut=0.9), rr(13.0, 4.85, -3.1, 3.0, cut=0.9)])
        f += bx(B, "sec", (-3.4, 11.6, -3.2), (0.5, 1.6, 0.5))
        f += lamp(B, (-3.4, 10.2, -3.3), w=1.4, h=1.8, d=1.0)
    else:               # 帷子の上に肋の板 4 段 (左右から胸の真ん中へ)
        for y in (15.0, 17.4, 19.8, 22.2):
            for s in (-1, 1):
                f += bx(B, "edge", (s * 2.1, y, -3.05), (3.6, 1.0, 0.6), rot=rot_z(s * -10))
        f += bx(B, "sec", (0, 18.6, -3.1), (1.4, 10.0, 0.6))
        f += bx(B, "gem", (0, 20.4, -3.5), (0.9, 1.2, 0.4))
    # 襟: 錨鉄の立ち襟
    f += shell(B, "sec" if o != "b" else "trim", [rr(22.4, 4.8, -2.9, 2.9, cut=0.9), rr(24.0, 4.8, -3.0, 3.0, cut=0.9)])
    # 腕 (袖と肩)
    for s in (-1, 1):
        ab = ARMB[s]
        g = []
        g += bx(ab, "main" if o != "a" else "acc", (s * 6.75, 18.0, 0), (4.3, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "trim", (s * 6.75, 13.9, 0), (4.6, 0.9, 5.1), skip=("b", "u"))
        if o == "a":    # 丸い肩の 3 段
            g += bx(ab, "sec", (s * 8.0, 24.4, 0), (6.4, 1.3, 5.6), rot=rot_z(-s * 16), taper=(0.75, 0.8))
            g += bx(ab, "sec", (s * 9.0, 22.6, 0), (5.2, 1.1, 4.8), rot=rot_z(-s * 26), taper=(0.75, 0.8))
            g += bx(ab, "sec", (s * 9.9, 20.4, 0), (4.2, 1.0, 4.0), rot=rot_z(-s * 34), taper=(0.8, 0.85))
        elif o == "b":  # 小さな錨鉄の肩
            g += bx(ab, "sec", (s * 8.2, 24.4, 0), (5.0, 0.9, 4.0), rot=rot_z(-s * 12), taper=(0.7, 0.75))
        else:           # 肋の肩 (2 本)
            for k, y in enumerate((24.2, 22.6)):
                g += bx(ab, "edge", (s * (8.0 + k), y, 0), (5.0 - k, 0.9, 4.4 - k * 0.6), rot=rot_z(-s * (14 + k * 12)), taper=(0.7, 0.8))
        f += g
    return f


def legs(o):
    """脚は 2 本の骨。面は骨ごとの tag を持つので、1 つのリストに入れてよい。"""
    f = []
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth" if o == "b" else "main", (cx, 8.4, 0), (4.3, 7.0, 4.8), taper=(0.96, 0.96), skip=("b", "u"))
        g += bx(lb, "trim", (cx, 5.6, 0), (4.6, 0.9, 5.1), skip=("b", "u"))
        if o == "a":    # 膝の錨鉄の丸い当て
            g += bx(lb, "sec", (cx, 7.0, -2.7), (3.6, 2.6, 1.2), taper=(0.7, 0.7), skip=("b", "+z"))
        elif o == "b":  # 外套の裾 (後ろの垂れ)
            g += bx(lb, "main", (cx, 8.2, 2.8), (4.2, 8.0, 0.6), skip=("b", "-z"))
        else:           # 肋の膝当て
            g += bx(lb, "edge", (cx, 7.2, -2.6), (3.2, 1.0, 1.0), skip=("b", "+z"))
            g += bx(lb, "edge", (cx, 9.0, -2.6), (3.0, 0.9, 0.9), skip=("b", "+z"))
        if s == -1:
            g += bx(lb, "gem", (cx, 9.4, -2.7), (0.9, 0.8, 0.4), skip=("b",))
        f += g
    return f


def boots(o):
    f = []
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "leather" if o == "b" else "sec", (cx, 2.4, 0), (4.4, 4.4, 4.7), taper=(0.94, 0.94), skip=("b",))
        g += bx(bb, "trim", (cx, 4.2, 0), (4.8, 0.7, 5.1), skip=("b", "u"))
        g += bx(bb, "sec" if o != "c" else "edge", (cx, 1.4, -3.0), (4.0, 2.2, 1.8), taper=(0.9, 0.6), skip=("b", "+z"))
        if o == "a":    # 重りの靴底 (錨鉄の厚い底)
            g += bx(bb, "acc", (cx, 0.5, 0), (4.6, 1.0, 5.0), skip=("u",))
        if s == -1:
            g += bx(bb, "gem", (cx, 3.2, -2.6), (0.9, 0.8, 0.4), skip=("b",))
        f += g
    return f


def build(o):
    """案 o (a/b/c) の 4 部位の面 -> {部位: 面のリスト}"""
    return {"helmet": helmet(o), "chestplate": chest(o), "leggings": legs(o), "boots": boots(o)}


# ------------------------------------------------------------------ 描く (材質ごとの小さな絵)

def paint(faces, size, _pos, N, M, L, pal):
    """apaint.paint と同じ構えで、役割 -> パレットの絵を作る。布は織り目、革は粒、殻は筋、骨は小さな穴。"""
    P = pal
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y = L[..., 0], L[..., 1]
    n_lo = vnoise(L * 0.09 + 3, 1)
    n_mid = vnoise(L * 0.3 + 11, 2)
    n_hi = vnoise(L * 0.9 + 5, 7)
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)
    weave = ((LU + LV) % 2 == 0)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(p, base, lw=0.30, nw=0.14, extra=0.0):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * len(p)).astype(int)
        return idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave

    m = lambda name: mats == name
    # 布 (main / acc / cloth): 織り目を少し暗く、布のよごれ (まだら)
    for role, base in (("main", 0.52), ("acc", 0.50), ("cloth", 0.40)):
        p = P[role]
        idx = shaded(p, base, 0.30, 0.12) - weave * (n_mid > 0.6) - (pit < 0.04)
        put(m(role), pick(p, idx))
    # 革 (leather): 粒と、押した跡
    p = P["leather"]
    put(m("leather"), pick(p, shaded(p, 0.46, 0.30, 0.14) - (n_hi > 0.78) - (pit < 0.05)))
    # 殻 (sec): 灰青の殻の筋 (帯の向き)
    p = P["sec"]
    ridge = np.abs(((Y * 0.5 + 0.12 * X) % 1.0) - 0.5) < 0.1
    put(m("sec"), pick(p, shaded(p, 0.56, 0.30, 0.10) - ridge * (n_mid > 0.4) - (pit < 0.03)))
    # 骨 (edge): 白い骨の小さな穴
    p = P["edge"]
    put(m("edge"), pick(p, shaded(p, 0.72, 0.26, 0.06) - (pit < 0.05)))
    # 濃い石 (trim): 縁だけ少し明るく
    p = P["trim"]
    put(m("trim"), pick(p, shaded(p, 0.46, 0.26, 0.08)))
    put(m("trim") & convex, pick(p, shaded(p, 0.62, 0.26, 0.08)))
    # 熾 (gem): 光る (glowmask)
    gm = m("gem")
    put(gm, ramp(P["gem"], 0.35 + 0.45 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay))
    glow |= gm
    # 熾の色の糸 (縫い目): 布と革の島の縁の 2 テクセル幅の点線 (2x2 の点を 2 つおき)。光らない
    big = np.minimum(CW, CH) >= 12  # 小さい島 (兜の輪の細い帯など) には縫わない。大きい面の縁だけ
    band = (D < 2.05) & INS         # 縁から 2 列目まで (1 列だと読めない)
    stitch = band & big & np.isin(mats, ["leather", "cloth"]) & ((((LU // 2) + (LV // 2)) % 3) == 0)
    put(stitch, pick(P["gem"], np.full((H, W), 1)))
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned


def build_piece(faces, pal):
    faces = cull_inside([f for f in faces if f is not None])
    lpkit.TEX = 2.0
    size, pos, N, M, L = build_atlas(faces, 32, 0)
    atlas, glow = paint(faces, size, pos, N, M, L, pal)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def build_set(o):
    pal = OPTIONS[o]
    return {k: build_piece(v, pal) for k, v in build(o).items()}


def set_tris(res):
    return sum(r["tris"] for r in res.values())


# ------------------------------------------------------------------ 保存と検査

def save_set(res, o):
    base = MESH_OUT / o
    base.mkdir(parents=True, exist_ok=True)
    for piece, r in res.items():
        save_img(r["atlas"], base / f"{piece}_atlas.png")
        gm = np.zeros((r["size"], r["size"], 4), np.uint8)
        gm[r["glow"]] = (255, 255, 255, 255)
        save_img(gm, base / f"{piece}_glowmask.png")
        export_mesh(r["faces"], r["size"], base / f"{piece}_mesh.json", f"abyss_diver_{o}_{piece}",
                    f"{piece}_atlas.png", f"{piece}_glowmask.png")


def load_bones(o):
    """保存した 4 部位の mesh.json -> {骨名: 三角形 (n,3,3)} (pose_check.load と同じ形)"""
    bones = {}
    for p in BONE_PIECE_ORDER:
        d = json.loads((MESH_OUT / o / f"{p}_mesh.json").read_text(encoding="utf-8"))
        for n, b in d["bones"].items():
            v = np.array(b["vertices"], dtype=float)
            t = np.array(b["triangles"], dtype=int)
            bones[n] = v[t]
    return bones


BONE_PIECE_ORDER = ["helmet", "chestplate", "leggings", "boots"]


def pose_report(o):
    """pose_check の姿勢と重なりの数え方をそのまま使う。休みの姿勢からの増え分 (+voxels) を返す。"""
    ignore = {("armorRightLeg", "armorRightBoot"), ("armorLeftLeg", "armorLeftBoot")}
    bones = load_bones(o)
    samples = {n: pose_check.sample(tr) for n, tr in bones.items()}
    base = pose_check.pair_overlap(samples, ignore)
    rest_sum = sum(base.values())
    worst = []
    for name, pose in pose_check.poses().items():
        if name == "rest":
            continue
        posed = {n: pose_check.apply(samples[n], n, pose) for n in samples}
        cur = pose_check.pair_overlap(posed, ignore)
        extra = [(cur[k] - base.get(k, 0), k) for k in cur]
        extra.sort(reverse=True)
        top = extra[0]
        worst.append((name, top[0], top[1]))
    return rest_sum, worst


# ------------------------------------------------------------------ インベントリのアイコン (4 部位 x 3 案、32x32)
# 既存の防具アイコン (tools/rough/armor_v2_icons.py) と同じ作り: 3D の部位を 3/4 の角度で描き、
# 縮めて色を減らし、1 画素の暗い輪郭を付ける。絵は 3D と同じ陰影・熾の光を使う。

ICON_DIR = MESH_OUT / "icons"
ICON_VIEW = {"helmet": (200, 14), "chestplate": (188, 8)}  # 兜と胸: armor_v2_icons と同じ角度
ICON_BOX, ICON_COLORS, ICON_OUTLINE = 28, 22, (26, 24, 30)
ICON_SLOT = (46, 52, 62)   # 暗い枠の底
PIECES = ["helmet", "chestplate", "leggings", "boots"]
PIECE_ICON_JP = dict(helmet="兜", chestplate="胸", leggings="脚", boots="靴")


# 脚と靴は 2 本が接して正面では 1 枚の板に見えるので、右の 1 本を斜めから描き、その左右反転を並べる (armor_v2_icons と同じ)
PAIR_BONE = {"leggings": (LEG[-1], 190, 6), "boots": (BOOT[-1], 200, 14)}


def _render(faces, atlas, glow, yaw, pitch, size=512):
    allv = np.concatenate([np.asarray(f.verts, float) for f in faces])
    lo, hi = allv.min(0), allv.max(0)
    center = tuple(float(v) for v in (lo + hi) / 2)
    scale = 440.0 / float(np.linalg.norm(hi - lo))
    im = scene.view(faces, atlas, glow, "whale", yaw, pitch, (size, size), center, scale, ss=1)
    return np.array(im.transpose(Image.FLIP_LEFT_RIGHT)).astype(float)


def icon_rgba(res, piece):
    """1 部位の 3/4 の絵 (透明な背景の RGBA)。脚と靴は右の 1 本の絵と、その反転を並べたもの。"""
    r = res[piece]
    if piece in PAIR_BONE:
        bone, yaw, pitch = PAIR_BONE[piece]
        faces = [f for f in r["faces"] if f.bone == bone]
        a = _render(faces, r["atlas"], r["glow"], yaw, pitch)
        ys, xs = np.where(a[..., 3] > 0.5)
        a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        gap = np.zeros((a.shape[0], max(2, a.shape[1] // 10), 4))
        return np.concatenate([a, gap, a[:, ::-1]], axis=1)
    faces, atlas, glow = aview.scene_of({piece: r}, mann=False)
    yaw, pitch = ICON_VIEW[piece]
    return _render(faces, atlas, glow, yaw, pitch)


def to_icon(rgba):
    """透明の外形で切り、面積平均で ICON_BOX に縮め、色を ICON_COLORS 色に減らし、輪郭を付けて 32x32 にする。"""
    A = rgba[..., 3] / 255.0
    ys, xs = np.where(A > 0.5)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    rgb, A = rgba[y0:y1, x0:x1, :3], A[y0:y1, x0:x1]
    h, w = A.shape
    sc = ICON_BOX / max(h, w)
    nw, nh = max(1, round(w * sc)), max(1, round(h * sc))
    prem = np.dstack([rgb * A[..., None], A])
    chans = [np.array(Image.fromarray(prem[..., i].astype(np.float32), "F").resize((nw, nh), Image.BOX)) for i in range(4)]
    prem2 = np.dstack(chans)
    a2 = prem2[..., 3]
    col = prem2[..., :3] / np.maximum(a2[..., None], 1e-6)
    mask = a2 > 0.5
    lum = col.mean(-1, keepdims=True)
    col = np.clip(lum + (col - lum) * 1.18, 0, 255)            # 彩度を少し上げる (小さい板が読めるように)
    col = np.clip((col - 128) * 1.12 + 128 + 6, 0, 255)
    q = Image.fromarray(col.astype(np.uint8), "RGB").quantize(colors=ICON_COLORS, method=Image.MEDIANCUT, dither=Image.NONE)
    q = np.array(q.convert("RGB"))
    out = np.zeros((32, 32, 4), np.uint8)
    oy, ox = (32 - nh) // 2, (32 - nw) // 2
    sub = out[oy:oy + nh, ox:ox + nw]
    sub[mask, :3] = q[mask]
    sub[mask, 3] = 255
    solid = out[..., 3] > 0
    ring = np.zeros_like(solid)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        ring |= np.roll(np.roll(solid, dy, 0), dx, 1)
    ring &= ~solid
    out[ring, :3] = ICON_OUTLINE
    out[ring, 3] = 255
    return out


def make_icons(results):
    """{(案, 部位): 32x32 の RGBA の配列} を作り、ICON_DIR に a_helmet_32.png の形で保存する。"""
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    out = {}
    for o in "abc":
        for piece in PIECES:
            ic = to_icon(icon_rgba(results[o], piece))
            Image.fromarray(ic).save(ICON_DIR / f"{o}_{piece}_32.png")
            out[(o, piece)] = ic
    return out


def icon_band(icons):
    """シートの下の段: 案ごとに 1 行。1x (原寸) の 4 つと、4x (最近傍) の 4 つ。暗い枠の底に置く。"""
    LABW = 130
    names = NAMES
    W = LABW + 5 * TW + 16
    RH = 150
    top = 40
    band = Image.new("RGB", (W, top + 3 * RH + 8), (24, 28, 36))
    dr = ImageDraw.Draw(band)
    label(dr, (8, 8), "インベントリのアイコン (32x32 の原寸 1x と 4x 最近傍。暗い枠の底に置いた)", 16, (255, 255, 255))
    x1 = LABW
    x4 = LABW + 4 * 48 + 40
    for k, piece in enumerate(PIECES):
        label(dr, (x1 + k * 48 + 8, 26), PIECE_ICON_JP[piece], 12, (200, 205, 215))
        label(dr, (x4 + k * 144 + 52, 26), PIECE_ICON_JP[piece], 12, (200, 205, 215))
    for i, o in enumerate("abc"):
        y = top + i * RH
        label(dr, (8, y + 52), names[o], 16, (255, 232, 160))
        for k, piece in enumerate(PIECES):
            ic = Image.fromarray(icons[(o, piece)])
            for x, size, pad, cell in ((x1 + k * 48, 32, 4, 40), (x4 + k * 144, 128, 4, 136)):
                slot = Image.new("RGBA", (cell, cell), ICON_SLOT + (255,))
                ImageDraw.Draw(slot).rectangle([0, 0, cell - 1, cell - 1], outline=(78, 88, 104, 255))
                slot.alpha_composite(ic.resize((size, size), Image.NEAREST), (pad, pad))
                band.paste(slot.convert("RGB"), (x, y))
    return band


# ------------------------------------------------------------------ 絵 (シート)

TW, TH = 210, 310   # 1 枚の絵
SC = 7.0            # 全身
CU_SC = 13.0        # 兜と胸の拡大
VIEWS = [("正面", 180, 6), ("背面", 0, 6), ("横", 90, 6), ("3/4", 215, 16)]
PLAT = (30, 34, 42)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc",
         "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def label(dr, xy, text, sz=15, fill=(230, 232, 238)):
    dr.text(xy, text, font=font(sz), fill=fill)


def tiles(res):
    out = []
    for _, yaw, pitch in VIEWS:
        out.append(aview.render(res, "whale", yaw, pitch, (TW, TH), SC, center=(0, 18.5, 0)))
    out.append(aview.render(res, "whale", 215, 10, (TW, TH), CU_SC, center=(0, 26.0, 0)))
    return out


def sheet(results, icons):
    LABW = 130
    names = NAMES
    cols = ["正面", "背面", "横", "3/4", "兜と胸 (拡大)"]
    W = LABW + len(cols) * TW + 16
    rows = []
    for o in "abc":
        res = results[o]
        tr = {k: v["tris"] for k, v in res.items()}
        h = TH + 56
        im = Image.new("RGB", (W, h), (24, 28, 36))
        dr = ImageDraw.Draw(im)
        label(dr, (8, 8), names[o], 20, (255, 232, 160))
        label(dr, (8, 36), f"一式 {set_tris(res)}", 14, (200, 205, 215))
        label(dr, (8, 56), f"兜 {tr['helmet']}", 12, (170, 176, 188))
        label(dr, (8, 72), f"胸 {tr['chestplate']}", 12, (170, 176, 188))
        label(dr, (8, 88), f"脚 {tr['leggings']}", 12, (170, 176, 188))
        label(dr, (8, 104), f"靴 {tr['boots']}", 12, (170, 176, 188))
        x = LABW
        for i, t in enumerate(tiles(res)):
            im.paste(t, (x + i * TW, 2))
        for i, c in enumerate(cols):
            label(dr, (x + i * TW + 6, TH + 16), c, 14, (200, 205, 215))
        rows.append(im)
    band = icon_band(icons)
    H = sum(r.size[1] for r in rows) + 124 + band.size[1]
    S = Image.new("RGB", (W, H), (24, 28, 36))
    dr = ImageDraw.Draw(S)
    label(dr, (10, 8), f"{TITLE_JP} 3 案 (五章「深淵」のレアの鎧。防具の低ポリ様式 C の箱と板。1 ブロック 32 テクセル)", 22, (255, 255, 255))
    label(dr, (10, 44), "A 錨鉄の潜り鎧 = 鋲の錨鉄の板・額の角灯・胸を斜めに渡る鎖・丸い肩の 3 段・重りの靴底", 13, (200, 205, 215))
    label(dr, (10, 62), "B 灯り番の外套 = 紫黒の長い外套・錨鉄の縁・腰に吊った角灯・頭巾の額の灯・後ろの裾", 13, (200, 205, 215))
    label(dr, (10, 80), "C 肋と鎖 = 鎖の帷子に白紫の肋の板・肋の冠・面頬の覗き窓が光る", 13, (200, 205, 215))
    label(dr, (10, 98), "共通: 縁はレアの青い鋼、光る画素は灯りの琥珀。顔は開けたまま (C は覗き窓のある面頬)。", 13, (200, 205, 215))
    y = 124
    for r in rows:
        S.paste(r, (0, y))
        y += r.size[1]
    S.paste(band, (0, y))
    p = SHEET
    p.parent.mkdir(parents=True, exist_ok=True)
    S.save(p)
    print("sheet", p, S.size)


def main():
    results = {}
    for o in "abc":
        res = build_set(o)
        save_set(res, o)
        results[o] = res
        print(o, {k: v["tris"] for k, v in res.items()}, "set", set_tris(res))
    icons = make_icons(results)
    print("icons", ICON_DIR)
    sheet(results, icons)
    for o in "abc":
        rest, worst = pose_report(o)
        print(o, "rest overlap (voxels)", rest)
        for name, n, k in worst:
            print("   ", name, "+%d" % n, k)


if __name__ == "__main__":
    main()
