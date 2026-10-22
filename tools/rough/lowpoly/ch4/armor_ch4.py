"""第四章の防具「灰歩きの旅装」(4 部位) の低ポリ 3 案。三章の armor_ch3.py と同じ作り (防具の低ポリ様式 C の箱・輪・板 = armor/akit.py)。
余燼の地上の色の言葉 (煤・灰・黒石・灰麻・熾の橙) で塗り分け、形は一〜三章の 3 つの型を受け継ぐ
(A 煤の前掛け = 煤けた革の上着に黒石の肩 3 段と鍛冶の前掛け、B 灰麻の外套 = 灰色の長い外套に煤鉄の縁とボタン、C 灰の頭巾 = 灰麻の頭巾の尾と黒石の小さな肩)。

実行: python -I tools/rough/lowpoly/ch4/armor_ch4.py
出力: docs/review/ch4_roughs/items/armor_ch4/{a,b,c}/<部位>_{mesh.json,atlas.png,glowmask.png}、
      docs/review/ch4_roughs/items/armor_ch4/icons/{a,b,c}_<部位>_32.png、docs/review/ch4_roughs/items/armor_ch4.png (シート)
入れ方: tools/gen/ch4/gen_ch4_gear.py (選んだ案を assets へ)
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

OUT = ROOT / "docs/review/ch4_roughs/items"
MESH_OUT = OUT / "armor_ch4"
TITLE_JP = "灰歩きの旅装"
PIECE_JP = dict(helmet="兜 (頭巾)", chestplate="胸 (上着と肩)", leggings="脚 (旅袴)", boots="靴 (煤の脚絆)")

# ------------------------------------------------------------------ 色 (余燼の地上: 煤・灰・黒石・灰麻・熾の橙)
A8 = np.array
SOOTL = A8([(26, 22, 22), (40, 34, 32), (58, 50, 46), (80, 70, 62), (104, 92, 80)])
ASHC = A8([(70, 66, 66), (100, 96, 92), (132, 126, 120), (164, 158, 150), (196, 190, 180)])
BASALT = A8([(30, 30, 36), (46, 46, 54), (66, 66, 76), (90, 90, 100), (118, 118, 128)])
HEMPC = A8([(92, 80, 60), (122, 108, 82), (150, 136, 104), (178, 164, 130), (206, 194, 160)])
IRON = A8([(30, 30, 38), (50, 50, 60), (76, 76, 88), (108, 108, 120), (146, 146, 158)])
LEATHER_SO = A8([(30, 22, 20), (46, 34, 30), (66, 50, 42), (90, 70, 58)])
DEEP = A8([(12, 10, 12), (22, 18, 20), (34, 28, 30), (48, 40, 40)])
EMBERG = A8([(170, 60, 20), (230, 110, 34), (255, 170, 70), (255, 220, 140)])

# 案ごとの材質の色 (役割 -> パレット)。役割 main/leather/sec/edge/acc/cloth/trim/gem は形の中で使う名前。
OPTIONS = {
    "a": dict(main=SOOTL, leather=LEATHER_SO, sec=BASALT, edge=IRON, acc=HEMPC, cloth=SOOTL,
              trim=DEEP, gem=EMBERG),
    "b": dict(main=ASHC, leather=LEATHER_SO, sec=BASALT, edge=IRON, acc=SOOTL, cloth=HEMPC,
              trim=IRON, gem=EMBERG),
    "c": dict(main=HEMPC, leather=LEATHER_SO, sec=BASALT, edge=BASALT, acc=ASHC, cloth=SOOTL,
              trim=DEEP, gem=EMBERG),
}


# ------------------------------------------------------------------ 形 (部位ごと。戻り値は面のリスト)

def helmet(o):
    b = "armorHead"
    f = []
    # 頭巾: 輪 3 段で頭の上を覆う。額と顔は開ける (前の下段は輪が切れている)
    f += shell(b, "main", [rr(29.6, 4.85, -4.9, 5.0, cut=1.2), rr(31.4, 4.6, -4.6, 4.8, cut=1.2),
                           rr(32.8, 3.4, -3.4, 3.7, cut=1.2)], caps=(False, "main"))
    for s in (-1, 1):   # 耳を覆う布の垂れ
        f += bx(b, "main", (s * 4.9, 27.6, 1.4), (0.9, 4.0, 4.8), taper=(0.8, 0.9), skip=("b",))
    if o == "a":        # 革の帽子の庇 (額に小さな殻の板)、熾の留め金
        f += bx(b, "sec", (0, 30.5, -5.0), (7.8, 1.4, 0.9), taper=(0.9, 0.7), skip=("b", "+z"))
        f += bx(b, "gem", (0, 30.5, -5.6), (1.0, 0.8, 0.4), skip=("b",))
        f += bx(b, "acc", (0, 34.4, 0.4), (0.8, 3.0, 0.8), taper=(0.6, 0.6), skip=("b",))   # 煤鉄の立て飾り
    elif o == "b":      # 煤鉄のボタン 2 つと、額の熾
        for s in (-1, 1):
            f += bx(b, "edge", (s * 1.7, 30.0, -5.1), (0.9, 0.9, 0.5), skip=("b",))
        f += bx(b, "gem", (0, 30.8, -5.25), (1.0, 1.0, 0.4), skip=("b",))
    else:               # 布の頭巾: 後ろへ長く垂れる尾 (形の差) と、殻の小さな立て飾り
        f += bx(b, "main", (0, 25.4, 5.4), (7.4, 8.0, 1.0), taper=(0.95, 1.0), skip=("b", "-z"))
        f += bx(b, "sec", (0, 33.6, 0.4), (1.0, 1.2, 6.4), taper=(0.6, 1.0), skip=("b",))
        f += bx(b, "gem", (0, 30.2, -5.2), (0.9, 0.8, 0.4), skip=("b",))
    return f


def chest(o):
    B = "armorBody"
    f = []
    # 外套の身: 輪 3 段
    f += shell(B, "main", [rr(12.3, 4.6, -2.7, 2.6, cut=0.9), rr(18.2, 4.6, -2.9, 2.8, cut=0.9),
                           rr(24.2, 4.6, -2.7, 2.7, cut=0.9)])
    # 腰の帯 (外へ少し出る)
    f += bx(B, "trim" if o == "b" else "leather", (0, 13.0, 0), (9.4, 1.2, 5.6), skip=("b", "u"))
    f += bx(B, "gem", (0, 13.0, -2.95), (1.2, 1.0, 0.4), skip=("b",))   # 帯の留め金 (熾)
    if o == "a":        # 鍛冶の前掛け (胸から帯の上まで垂れる煤けた革の板) と、前掛けの紐。帯 (y 13) とは重ねない
        f += bx(B, "leather", (0, 18.8, -3.05), (6.0, 9.0, 0.5), taper=(1.0, 0.85))
        f += bx(B, "acc", (0, 23.6, -3.15), (6.6, 0.7, 0.4))
    elif o == "b":      # 前立ての帯と骨ボタン 4 つ (長い外套の前)
        f += bx(B, "trim", (0, 18.6, -3.1), (1.4, 11.0, 0.5))
        for y in (14.4, 17.4, 20.4, 23.0):
            f += bx(B, "edge", (0, y, -3.5), (0.9, 0.9, 0.6))
        f += shell(B, "trim", [rr(12.0, 4.85, -3.1, 3.0, cut=0.9), rr(13.0, 4.85, -3.1, 3.0, cut=0.9)])  # 裾の縁
    # 首の襟: A と C は布の襟巻き (輪と垂れ)、B は濃い石の立ち襟
    if o in ("a", "c"):
        f += shell(B, "acc", [rr(22.4, 4.95, -3.1, 3.0, cut=0.7), rr(24.0, 4.9, -3.2, 3.0, cut=0.7)])
        f += ext(B, "acc", [(-1.5, 23.2), (1.5, 23.2), (1.9, 16.6), (-1.9, 16.6)], 0.6, c=(0, 0, -3.6))
    else:
        f += shell(B, "trim", [rr(22.4, 4.8, -2.9, 2.9, cut=0.9), rr(24.0, 4.8, -3.0, 3.0, cut=0.9)])
    if o == "c":        # 背の殻の板 (小さい)
        f += bx(B, "sec", (0, 21.0, 3.2), (4.2, 5.0, 0.8), taper=(0.9, 1.0), skip=("b", "-z"))
    # 腕 (袖と肩の板)
    for s in (-1, 1):
        ab = ARMB[s]
        g = []
        g += bx(ab, "main", (s * 6.75, 18.0, 0), (4.3, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "leather" if o != "b" else "trim", (s * 6.75, 13.9, 0), (4.6, 0.9, 5.1), skip=("b", "u"))
        if o == "a":    # 殻の肩: 3 段、外へ張り出す
            g += bx(ab, "sec", (s * 8.2, 24.4, 0), (6.6, 1.2, 5.4), rot=rot_z(-s * 18), taper=(0.7, 0.75))
            g += bx(ab, "sec", (s * 9.3, 22.6, 0), (5.2, 1.0, 4.4), rot=rot_z(-s * 28), taper=(0.7, 0.75))
            g += bx(ab, "sec", (s * 10.2, 20.2, 0), (4.2, 0.9, 3.6), rot=rot_z(-s * 36), taper=(0.75, 0.8))
        elif o == "c":  # 小さい肩の板 1 枚
            g += bx(ab, "sec", (s * 8.2, 24.4, 0), (5.0, 0.8, 3.8), rot=rot_z(-s * 12), taper=(0.7, 0.75))
            g += bx(ab, "leather", (s * 6.75, 12.6, 0), (4.6, 1.0, 5.1), skip=("b", "u"))  # 腕当て
        f += g
    return f


def legs(o):
    """脚は 2 本の骨。面は骨ごとの tag を持つので、1 つのリストに入れてよい。"""
    f = []
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 8.4, 0), (4.3, 7.0, 4.8), taper=(0.96, 0.96), skip=("b", "u"))
        g += bx(lb, "trim" if o == "b" else "acc", (cx, 5.6, 0), (4.6, 0.9, 5.1), skip=("b", "u"))
        if o == "b":    # 長い外套の裾 (後ろの垂れ)
            g += bx(lb, "main", (cx, 8.2, 2.8), (4.2, 8.0, 0.6), skip=("b", "-z"))
        else:           # 膝当ての革と、外側の小袋
            g += bx(lb, "leather", (cx, 6.8, -2.6), (3.4, 2.2, 1.0), taper=(0.6, 0.6), skip=("b", "+z"))
        if o == "a":
            g += bx(lb, "leather", (s * 4.5, 9.0, 0.0), (1.0, 2.8, 2.6))
        if s == -1:     # 右の太ももの熾 (光る画素を 1 つ)
            g += bx(lb, "gem", (cx, 8.6, -2.7), (1.0, 0.9, 0.4), skip=("b",))
        f += g
    return f


def boots(o):
    f = []
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "leather", (cx, 2.4, 0), (4.4, 4.4, 4.7), taper=(0.94, 0.94), skip=("b",))
        g += bx(bb, "trim" if o == "b" else "acc", (cx, 4.2, 0), (4.8, 0.7, 5.1), skip=("b", "u"))
        g += bx(bb, "trim" if o == "b" else "sec", (cx, 1.5, -3.0), (4.0, 2.2, 1.8), taper=(0.9, 0.6), skip=("b", "+z"))
        if s == -1:
            g += bx(bb, "gem", (cx, 3.2, -2.6), (1.0, 0.9, 0.4), skip=("b",))
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
    stitch = band & big & np.isin(mats, ["main", "leather", "acc", "cloth"]) & ((((LU // 2) + (LV // 2)) % 3) == 0)
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
        export_mesh(r["faces"], r["size"], base / f"{piece}_mesh.json", f"ch4_travel_{o}_{piece}",
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
    names = {"a": "A 煤の前掛け", "b": "B 灰麻の外套", "c": "C 灰の頭巾"}
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
    names = {"a": "A 煤の前掛け", "b": "B 灰麻の外套", "c": "C 灰の頭巾"}
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
    label(dr, (10, 8), f"{TITLE_JP} 3 案 (第四章の旅装。防具の低ポリ様式 C の箱と板。1 ブロック 32 テクセル)", 22, (255, 255, 255))
    label(dr, (10, 44), "A 煤の前掛け = 黒石の肩 3 段・煤けた革の上着と鍛冶の前掛け・灰麻の襟巻き・兜に煤鉄の立て飾り", 13, (200, 205, 215))
    label(dr, (10, 62), "B 灰麻の外套 = 灰色の長い外套・煤鉄の帯と縁・煤鉄のボタン 4 つ・後ろの裾", 13, (200, 205, 215))
    label(dr, (10, 80), "C 灰の頭巾 = 灰麻の布・後ろへ長い頭巾の尾・革の腕当て・黒石の肩と背の板", 13, (200, 205, 215))
    label(dr, (10, 98), "共通: 熾の色の糸 (縫い目の点線)、熾の留め金 (光る画素)、顔は開けたまま。縁の金属なし (旅の品)。", 13, (200, 205, 215))
    y = 124
    for r in rows:
        S.paste(r, (0, y))
        y += r.size[1]
    S.paste(band, (0, y))
    p = OUT / "armor_ch4.png"
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
