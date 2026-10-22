"""心臓の番人 (HeartWarden) の本番用モデル。選んだ案 B (肋の獣) を、入れる形にする。
docs/design/36 の 6・6.6。3 案のラフ (warden.py) から形を借り、次を変える:
  - 骨の組: 肋と核は「ribs」(胸が息をする) の中に、心臓の光は「heart」(鼓動) の中に。腕・脚・頭は今までどおり。
  - UV: 面ごとの島をやめ、同じ向きの面を一つの島にまとめる (部品 × 骨 × 主軸 × 向き)。島を詰めて縞にならないようにする。
  - 絵: 手で描いたものではなく、材質ごとの決まりで描く (骨は粒と隙間の暗い線、虚は黒、鎖は暗い鉄の縁の光、心臓は青緑の光)。
  - 発光: 心臓の画素だけ (発光マスク)。肋の縁の薄い光は、マスクが二値なので入れない (残り)。

出力: docs/review/warden_model/ (heart_warden_mesh.json・heart_warden_atlas.png・heart_warden_glowmask.png・heart_warden_views.png)
生成: python tools/rough/lowpoly/abyss/warden_model.py   (numpy / PIL / scipy)
入れる: python tools/gen/world/gen_heart_warden.py   (install_lowpoly と animations を書く)
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import warden as W  # noqa: E402  (案 B の形 build_b と、ラフの描画の部品)
import ck as _ck  # noqa: E402
import ck_ab as AB  # noqa: E402

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/warden_model"
NAME = "heart_warden"
TPU = 4.0                  # 1 単位あたりのテクセル (島の密度)。縞にならない大きさに詰める
PAD = 2                    # 島と島のあいだの余白 (テクセル)

# 部品 → 骨 (本番の骨の組)。胸は ribs、心臓は heart (ribs の子)
PART_BONE = {"rib": "ribs", "spine": "ribs", "core": "ribs", "heart": "heart", "pelvis": "body",
             "neck": "head", "skull": "head", "crown": "head"}
BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", (0, 0, 0)),
    "ribs": ("body", (0, 30, 0)),
    "heart": ("ribs", (0, 33, -2.6)),
    "head": ("body", (0, 45, -1)),
    "arm_l": ("body", (-6.5, 41, 0)),
    "arm_r": ("body", (6.5, 41, 0)),
    "leg_l": ("body", (-3.5, 24, 0)),
    "leg_r": ("body", (3.5, 24, 0)),
}


def remap(faces):
    """部品の名前から本番の骨を決める。鎖・爪は x の正負で左右の腕、脚と足も同じ。"""
    for f in faces:
        p = getattr(f, "part", "")
        side_x = float(f.verts[:, 0].mean())
        if p in ("chain", "hook"):
            f.bone = "arm_l" if side_x < 0 else "arm_r"
        elif p in ("leg", "foot"):
            f.bone = "leg_l" if side_x < 0 else "leg_r"
        else:
            f.bone = PART_BONE.get(p, f.bone)
    return faces


def chart_unwrap(faces):
    """面を (骨, 主軸, 向き) で島にまとめ、島を棚に詰める。島の中の面は、その向きに投影した平面に並ぶ。"""
    groups = {}
    for f in faces:
        n = f.normal
        ax = int(np.argmax(np.abs(n)))
        key = (f.bone, ax, bool(n[ax] >= 0))
        groups.setdefault(key, []).append(f)
    charts = []
    for key, fs in groups.items():
        others = [i for i in range(3) if i != key[1]]
        pts = np.concatenate([f.verts[:, others] for f in fs])
        lo = pts.min(0)
        for f in fs:
            f.uv = (f.verts[:, others] - lo) * TPU
        w, h = np.ceil((pts.max(0) - lo) * TPU) + 1
        charts.append((int(w), int(h), fs))
    charts.sort(key=lambda c: -c[1])
    size = 64
    while True:
        x = y = rowh = 0
        place = []
        ok = True
        for w, h, fs in charts:
            W_, H_ = w + 2 * PAD, h + 2 * PAD
            if x + W_ > size:
                x, y, rowh = 0, y + rowh, 0
            if y + H_ > size:
                ok = False
                break
            place.append((x + PAD, y + PAD, fs))
            x += W_
            rowh = max(rowh, H_)
        if ok:
            break
        size *= 2
    for ox, oy, fs in place:
        for f in fs:
            f.uv = f.uv + np.array([ox, oy])
    return size


def value_noise(p, scale, seed):
    """点ごとの雑音 (0..1)。整数格子の値を滑らかにつなぐ。p は (n,3)。"""
    q = p * scale + seed
    i = np.floor(q)
    t = q - i
    t = t * t * (3 - 2 * t)

    def h(ix, iy, iz):
        v = np.sin(ix * 127.1 + iy * 311.7 + iz * 74.7 + seed * 1.3) * 43758.5453
        return v - np.floor(v)

    c000 = h(i[:, 0], i[:, 1], i[:, 2])
    c100 = h(i[:, 0] + 1, i[:, 1], i[:, 2])
    c010 = h(i[:, 0], i[:, 1] + 1, i[:, 2])
    c110 = h(i[:, 0] + 1, i[:, 1] + 1, i[:, 2])
    c001 = h(i[:, 0], i[:, 1], i[:, 2] + 1)
    c101 = h(i[:, 0] + 1, i[:, 1], i[:, 2] + 1)
    c011 = h(i[:, 0], i[:, 1] + 1, i[:, 2] + 1)
    c111 = h(i[:, 0] + 1, i[:, 1] + 1, i[:, 2] + 1)
    x0 = c000 + (c100 - c000) * t[:, 0]
    x1 = c010 + (c110 - c010) * t[:, 0]
    x2 = c001 + (c101 - c001) * t[:, 0]
    x3 = c011 + (c111 - c011) * t[:, 0]
    y0 = x0 + (x1 - x0) * t[:, 1]
    y1 = x2 + (x3 - x2) * t[:, 1]
    return y0 + (y1 - y0) * t[:, 2]


def mix(a, b, t):
    return np.asarray(a, float)[None, :] * (1 - t[:, None]) + np.asarray(b, float)[None, :] * t[:, None]


def paint_face_texels(mat, p, n, edge):
    """材質ごとの色 (n,3) と、光る画素の印 (n,) を返す。p は面上の位置、n は面の向き、edge は三角形の縁までの近さ (0 で縁)。"""
    up = np.clip(n[:, 1] * 0.5 + 0.5, 0, 1)
    if mat == "bone":
        g = value_noise(p, 1.6, 3.0)
        fine = value_noise(p, 6.0, 9.0)
        t = np.clip(0.30 + 0.45 * up + 0.25 * (g - 0.5) + 0.10 * (fine - 0.5), 0, 1)
        col = mix([58, 50, 42], [226, 214, 186], t)
        crev = np.clip((edge - 0.04) / 0.12, 0, 1)            # 縁の隙間は暗い線
        col *= (0.45 + 0.55 * crev)[:, None]
        return col, np.zeros(len(p), bool)
    if mat == "void":
        g = value_noise(p, 2.0, 5.0)
        col = mix([3, 3, 6], [30, 28, 40], np.clip(0.25 + 0.5 * g + 0.2 * up, 0, 1))
        return col, np.zeros(len(p), bool)
    if mat == "slate":
        g = value_noise(p, 3.0, 11.0)
        t = np.clip(0.25 + 0.35 * up + 0.2 * (g - 0.5), 0, 1)
        col = mix([20, 26, 36], [128, 142, 160], t)
        rim = np.clip(1 - edge / 0.22, 0, 1)                    # 鉄の縁の光 (リンクの輪郭が読める)
        col = col + (rim * 60)[:, None]
        return col, np.zeros(len(p), bool)
    if mat == "teal":
        g = value_noise(p, 4.0, 17.0)
        t = np.clip(0.55 + 0.35 * (g - 0.5) + 0.2 * np.clip(1 - edge / 0.5, 0, 1), 0, 1)
        col = mix([22, 96, 94], [170, 255, 246], t)
        return col, np.ones(len(p), bool)
    col = mix([128, 128, 128], [200, 200, 200], np.full(len(p), 0.5))
    return col, np.zeros(len(p), bool)


def paint(faces, size):
    atlas = np.zeros((size, size, 4), np.uint8)
    glow = np.zeros((size, size), bool)
    for f in faces:
        for a, b, c in f.tris():
            A, B, C = f.uv[a], f.uv[b], f.uv[c]
            lo = np.floor(np.minimum(np.minimum(A, B), C)).astype(int)
            hi = np.ceil(np.maximum(np.maximum(A, B), C)).astype(int) + 1
            lo = np.maximum(lo, 0)
            hi = np.minimum(hi, size)
            xs = np.arange(lo[0], hi[0]) + 0.5
            ys = np.arange(lo[1], hi[1]) + 0.5
            if len(xs) == 0 or len(ys) == 0:
                continue
            gx, gy = np.meshgrid(xs, ys)
            q = np.stack([gx.ravel(), gy.ravel()], -1)
            T = np.array([[B[0] - A[0], C[0] - A[0]], [B[1] - A[1], C[1] - A[1]]])
            if abs(np.linalg.det(T)) < 1e-9:
                continue
            bc = np.linalg.solve(T, (q - A).T).T
            w = np.stack([1 - bc[:, 0] - bc[:, 1], bc[:, 0], bc[:, 1]], -1)
            inside = (w >= -1e-6).all(1)
            if not inside.any():
                continue
            w = w[inside]
            q = q[inside]
            pos = w @ f.verts[[a, b, c]]
            edge = w.min(1)
            col, lit = paint_face_texels(f.mat, pos, np.repeat(np.asarray(f.normal)[None], len(pos), 0), edge)
            xi = q[:, 0].astype(int)
            yi = q[:, 1].astype(int)
            atlas[yi, xi, :3] = np.clip(col, 0, 255).astype(np.uint8)
            atlas[yi, xi, 3] = 255
            glow[yi[lit], xi[lit]] = True
    return atlas, glow


def build():
    faces = remap(W.build_b(None)[0])
    for f in faces:
        f.normal = np.asarray(f.normal, float)
    return faces


def views(faces, atlas, glow, size):
    """確かめの絵: 明るい所の三方向と、爪を前に出した姿 (攻撃)、暗闇 (灯りは左前の一つ)。"""
    r = dict(faces=faces, size=size)
    out = []
    rows = [("正面", 180, 0, {}), ("3/4", 225, 10, {}), ("横", 270, 0, {})]
    for lab, yaw, pitch, pose in rows:
        out.append((lab, _ck.render_view(r, atlas, glow, pose, BONES, yaw, pitch, (300, 320), (0.0, 25.5, 0.0), 5.4)))
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    faces = build()
    size = chart_unwrap(faces)
    atlas, glow = paint(faces, size)
    Image.fromarray(atlas, "RGBA").save(OUT / f"{NAME}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(OUT / f"{NAME}_glowmask.png")
    # 面の向きを確かめるための、島の色分け (明るい所の絵とは別)
    data = _ck.export_mesh(faces, size, OUT / f"{NAME}_mesh.json", NAME, f"{NAME}_atlas.png", BONES,
                           {"glowmask": f"{NAME}_glowmask.png"})
    buried = _ck.check_buried(faces)
    fl = AB.float_check(faces)
    box = AB.bbox_float(faces)
    print("tris", data["triangle_count"], "atlas", size, "buried", buried, "floating", len(fl), "boxfloat", len(box))
    print("bones", {b: sum(1 for f in faces if f.bone == b) for b in BONES})
    cover = float((atlas[..., 3] > 0).mean())
    print("texel coverage", round(cover, 3), "glow texels", int(glow.sum()))
    rv = views(faces, atlas, glow, size)
    GAP, X0, top = 14, 18, 40
    cv = Image.new("RGB", (X0 * 2 + 3 * 300 + 2 * GAP, top + 320 + 40), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    F = _ck.voxel.font
    d.text((X0, 12), "心臓の番人 本番モデル (B 肋の獣・UV 島・決まりの絵)", font=F(22), fill=(236, 232, 218))
    for i, (lab, im) in enumerate(rv):
        x = X0 + i * (300 + GAP)
        cv.paste(im, (x, top))
        d.text((x + 2, top + 326), lab, font=F(16), fill=(150, 232, 255))
    cv.save(OUT / f"{NAME}_views.png")
    Image.fromarray(atlas, "RGBA").resize((512, 512), Image.NEAREST).save(OUT / f"{NAME}_atlas_preview.png")


if __name__ == "__main__":
    main()
