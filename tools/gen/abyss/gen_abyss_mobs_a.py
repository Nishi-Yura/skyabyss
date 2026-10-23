#!/usr/bin/env python3
"""深淵の敵 A の 9 種 (骨の層 3・塩の層 3・雷の層 3) の素材を選んだラフから作る (docs/design/36 の 4・4.1・4.2)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/abyss/gen_abyss_mobs_a.py [--only rib_guard]

入力: docs/review/abyss_roughs/<id>/<案>/<id>_<案>_mesh.json   (選んだ案。CHOICE)
処理:
  1. ラフの UV は島が上のほんの一部に細く詰まっていて、塗ると縞になる。そこで面ごとに正方形の島 (S×S テクセル) を
     一つずつ割り当てる (三角形の頂点を面ごとに複製し、UV は島の角へ置く)。模様は各テクセルの 3D 位置 (モデル座標) から描く:
     骨の木目・割れ目・塩の結晶・貝の渦・雷の筋・炭の火の筋など。
  2. 三段の姿 (docs/design/36 の 4.2): 段 1 = 元の姿、段 2 = 歪み (一つ下の層)、段 3 = 崩れ (二つ下・深み)。
     骨格は同じ (骨の名前・付け根は変えない)。段 2・3 は根以外の骨を付け根から上下へ伸ばし、模様は暗く、割れ目と発光を増やす。
  3. tools/install/install_lowpoly.py で assets へ入れる (名前は abyss_<id>_s1 / _s2 / _s3)。install は水密化で頂点を溶接し直すので、
     install のあとで面ごとの版 (複製した頂点) の mesh JSON と atlas を上書きする。
出力: docs/review/abyss_roughs/_out/ (中間の PNG と mesh。コミットしない)、assets/skyabyss/lowpoly・textures/lowpoly・geo/entity (install_lowpoly)
"""
import argparse
import json
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RAW = os.path.join(ROOT, "docs", "review", "abyss_roughs")
OUT = os.path.join(RAW, "_out")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
S = 16  # 面一つの島の一辺 (テクセル)

# 選んだ案 (2026-10-22 の選定。docs/design/36 の 4.1)
CHOICE = {
    "bone_picker": "a", "rib_guard": "a", "bone_flute": "a",
    "salt_husk": "a", "tide_hands": "b", "white_shell_keeper": "a",
    "charged_moth": "a", "lightning_idol": "a", "scorched_bird": "b",
}

# 三段の伸び (骨の長さの倍率)、暗さ、発光の強さ
STAGE_STRETCH = {1: 1.0, 2: 1.08, 3: 1.18}
STAGE_DARK = {1: 1.0, 2: 0.86, 3: 0.72}
STAGE_GLOW = {1: 1.0, 2: 1.35, 3: 1.7}


# ---------------------------------------------------------------- ノイズ (numpy のみ)
_rng = np.random.RandomState(20261022)
_PERM = np.concatenate([_rng.permutation(256)] * 4)
_VAL = _rng.random_sample(256)


def _hash(ix, iy, iz, c=0):
    return _VAL[_PERM[(_PERM[(_PERM[ix & 255] + iy) & 255] + iz + c) & 255] & 255]


def vnoise(p, seed=0):
    """3 次元の値ノイズ (0〜1)。p は (M,3)。"""
    f = np.floor(p).astype(np.int64)
    t = p - f
    t = t * t * (3 - 2 * t)
    ix, iy, iz = f[:, 0] + seed * 17, f[:, 1] + seed * 31, f[:, 2] + seed * 53
    out = np.zeros(len(p))
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (t[:, 0] if dx else 1 - t[:, 0]) * (t[:, 1] if dy else 1 - t[:, 1]) * (t[:, 2] if dz else 1 - t[:, 2])
                out += w * _hash(ix + dx, iy + dy, iz + dz)
    return out


def fbm(p, oct=3, seed=0):
    s, a, norm = 0.0, 0.5, 0.0
    q = p.copy()
    for i in range(oct):
        s = s + a * vnoise(q, seed + i)
        norm += a
        q = q * 2.03 + 1.7
        a *= 0.5
    return s / norm


def ridge(p, seed=0):
    """線になる模様 (0 = 線の芯)。"""
    return np.abs(vnoise(p, seed) * 2 - 1)


def cells(p, seed=0):
    """Voronoi の F1 と F2 (結晶の面と縁)。"""
    base = np.floor(p).astype(np.int64)
    f1 = np.full(len(p), 9.0)
    f2 = np.full(len(p), 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = base + np.array([dx, dy, dz])
                q = c + np.stack([_hash(c[:, 0] + seed, c[:, 1], c[:, 2], 0),
                                  _hash(c[:, 0], c[:, 1] + seed, c[:, 2], 7),
                                  _hash(c[:, 0], c[:, 1], c[:, 2] + seed, 13)], 1)
                d = np.sqrt(((q - p) ** 2).sum(1))
                better = d < f1
                f2 = np.where(better, f1, np.minimum(f2, d))
                f1 = np.where(better, d, f1)
    return f1, f2


def mixc(a, b, t):
    t = np.clip(t, 0, 1)[:, None]
    return a * (1 - t) + b * t


def hexc(h):
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (1, 3, 5)])


def tile(h, n):
    return np.tile(hexc(h), (n, 1))


# ---------------------------------------------------------------- 模様 (3D 位置から色)。戻り値: rgb (M,3) 0..1, glow (M,) 0..1
def paint_bone_picker(P, Nr, bone, st):
    n = len(P)
    grain = fbm(P * [0.6, 0.12, 0.6], 3, 1)
    rgb = mixc(tile("#b9ad90", n), tile("#e6dcc2", n), grain)
    crack = ridge(P * 0.55, 3) < 0.07
    dirt = fbm(P * 0.9, 3, 4) > 0.62
    rgb = rgb * (1 - 0.55 * crack[:, None]) * (1 - 0.25 * dirt[:, None])
    return rgb, np.zeros(n)


def paint_rib_guard(P, Nr, bone, st):
    n = len(P)
    base = tile("#d3c6a6", n)
    rib = np.sin(P[:, 1] * 1.1 + fbm(P * 0.5, 2, 5) * 3.0)
    grain = fbm(P * [1.5, 0.3, 1.5], 3, 6)
    rgb = mixc(base * 0.82, base * 1.05, grain) * (0.85 + 0.15 * (rib > 0.2))[:, None]
    crack = ridge(P * 0.8, 8) < 0.06
    rgb = rgb * (1 - 0.6 * crack[:, None])
    chest = (Nr[:, 2] < -0.4) & (np.abs(P[:, 0]) < 1.5) & (P[:, 1] > 9) & (P[:, 1] < 13.5)
    rgb[chest] = hexc("#2a2420")
    return rgb, np.zeros(n)


def paint_bone_flute(P, Nr, bone, st):
    n = len(P)
    skull = tile("#e0d8c4", n)
    grain = fbm(P * 0.9, 3, 9)
    rgb = mixc(skull * 0.8, skull, grain)
    sock = (Nr[:, 2] < -0.3) & (P[:, 1] > 20) & (bone == "head")
    rgb[sock] = hexc("#120e0c")
    crack = ridge(P * 0.7, 10) < 0.05
    rgb = rgb * (1 - 0.5 * crack[:, None])
    return rgb, np.zeros(n)


def paint_salt_husk(P, Nr, bone, st):
    n = len(P)
    f1, f2 = cells(P * 0.7, 11)
    rgb = mixc(tile("#f0f2f2", n), tile("#c9d2d8", n), np.clip(f1 * 1.2, 0, 1))
    rgb[(f2 - f1) < 0.07] = hexc("#6c7d8c")
    crack = ridge(P * 0.9, 12) < 0.05
    rgb = rgb * (1 - 0.45 * crack[:, None])
    return rgb, np.zeros(n)


def paint_tide_hands(P, Nr, bone, st):
    n = len(P)
    rgb = mixc(tile("#3f8a7c", n), tile("#7fc2b0", n), fbm(P * 0.8, 3, 14))
    spots = cells(P * 1.1, 15)[0] < 0.22
    rgb[spots] = rgb[spots] * 0.5 + hexc("#d8c79a") * 0.5
    streak = ridge(P * [0.4, 1.6, 0.4], 16) < 0.08
    rgb = rgb * (1 - 0.25 * streak[:, None])
    return rgb, np.zeros(n)


def paint_white_shell(P, Nr, bone, st):
    n = len(P)
    ang = np.arctan2(P[:, 2], P[:, 0]) + P[:, 1] * 0.22
    band = 0.5 + 0.5 * np.sin(ang * 3 + fbm(P * 0.4, 2, 18) * 4)
    rgb = mixc(tile("#ebe4d2", n), tile("#b49a78", n), band * 0.6)
    crack = ridge(P * 0.6, 19) < 0.05
    rgb = rgb * (1 - 0.4 * crack[:, None])
    return rgb, np.zeros(n)


def paint_charged_moth(P, Nr, bone, st):
    n = len(P)
    rgb = tile("#2b2f3d", n) * (0.85 + 0.3 * fbm(P * 0.7, 3, 20))[:, None]
    vein = ridge(P * 0.9, 21) < 0.08
    rgb[vein] = hexc("#7ff3ff")
    return rgb, vein.astype(float)


def paint_lightning_idol(P, Nr, bone, st):
    n = len(P)
    rgb = tile("#3a3d4a", n) * (0.8 + 0.4 * fbm(P * 0.7, 3, 22))[:, None]
    f1, f2 = cells(P * 0.45, 23)
    rgb[(f2 - f1) < 0.06] = hexc("#55596a")
    bolt = ridge(P * [0.8, 0.35, 0.8], 24) < 0.06
    rgb[bolt] = hexc("#bff6ff")
    return rgb, bolt.astype(float)


def paint_scorched_bird(P, Nr, bone, st):
    n = len(P)
    rgb = tile("#2a1d18", n) * (0.8 + 0.4 * fbm(P * 0.8, 3, 25))[:, None]
    ember = ridge(P * 0.75, 26) < 0.07
    rgb[ember] = hexc("#ff8a3a")
    return rgb, ember.astype(float)


PAINTERS = {
    "bone_picker": paint_bone_picker,
    "rib_guard": paint_rib_guard,
    "bone_flute": paint_bone_flute,
    "salt_husk": paint_salt_husk,
    "tide_hands": paint_tide_hands,
    "white_shell_keeper": paint_white_shell,
    "charged_moth": paint_charged_moth,
    "lightning_idol": paint_lightning_idol,
    "scorched_bird": paint_scorched_bird,
}

# 段 2・3 の崩れの色 (割れ目に出る)
EERIE = {
    "bone_picker": "#ff6a4a", "rib_guard": "#ff5a3a", "bone_flute": "#c8ffb0",
    "salt_husk": "#9fd8ff", "tide_hands": "#8affd0", "white_shell_keeper": "#ff9ad8",
    "charged_moth": "#b58bff", "lightning_idol": "#ffd27a", "scorched_bird": "#ff5a10",
}


# ---------------------------------------------------------------- 面ごとの島 (複製した頂点の版)
def stretch(mesh, stage):
    """段の伸び: 根 (root) 以外の骨を、付け根から上下へ伸ばす (段 1 は変えない)。"""
    m = json.loads(json.dumps(mesh))
    f = STAGE_STRETCH[stage]
    if f == 1.0:
        return m
    for name, b in m["bones"].items():
        if not b["vertices"] or name == "root":
            continue
        pv = np.array(b["pivot"], float)
        for v in b["vertices"]:
            d = np.array(v, float) - pv
            d[1] *= f
            v[0], v[1], v[2] = [round(float(x), 5) for x in (pv + d)]
    return m


def chart_grid():
    """島の中の三角形: 角 (0,0)・(S,0)・(0,S)。テクセル中心の重心座標と、三角形の中かどうか。"""
    ys, xs = np.mgrid[0:S, 0:S]
    x = xs + 0.5
    y = ys + 0.5
    w1 = x / S
    w2 = y / S
    w0 = 1 - w1 - w2
    inside = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2 >= -1e-9)
    return ys.reshape(-1), xs.reshape(-1), np.stack([w0.reshape(-1), w1.reshape(-1), w2.reshape(-1)], 1), inside.reshape(-1)


def build(mesh):
    """面ごとに頂点を複製し、島を割り当てる。戻り値: (版の mesh, atlas の一辺, 塗る点 P・法線 Nr・骨名・島の座標)."""
    tris = []  # (bone name, pa, pb, pc)
    for name, b in mesh["bones"].items():
        if not b["triangles"]:
            continue
        verts = np.array(b["vertices"], float)
        for i, j, k in b["triangles"]:
            tris.append((name, verts[i], verts[j], verts[k]))
    T = len(tris)
    G = int(math.ceil(math.sqrt(T)))
    A = 1
    while A < G * S:
        A *= 2
    ys, xs, bary, inside = chart_grid()
    bary = bary[inside]
    ys = ys[inside]
    xs = xs[inside]
    P_list, N_list, B_list, Y_list, X_list = [], [], [], [], []
    out_bones = {n: {"parent": b.get("parent"), "pivot": b["pivot"], "vertices": [], "uvs": [], "triangles": [], "triangle_count": 0}
                 for n, b in mesh["bones"].items()}
    for ti, (name, pa, pb, pc) in enumerate(tris):
        ox = (ti % G) * S
        oy = (ti // G) * S
        b = out_bones[name]
        base = len(b["vertices"])
        # 島の角 (テクセルの中心より少し内側)
        corners = [(ox + 0.5, oy + 0.5), (ox + S - 0.5, oy + 0.5), (ox + 0.5, oy + S - 0.5)]
        for (cx, cy), p in zip(corners, (pa, pb, pc)):
            b["vertices"].append([round(float(c), 5) for c in p])
            b["uvs"].append([round(cx / A, 6), round(cy / A, 6)])
        b["triangles"].append([base, base + 1, base + 2])
        b["triangle_count"] += 1
        nrm = np.cross(pb - pa, pc - pa)
        ln = np.linalg.norm(nrm)
        nrm = nrm / ln if ln > 1e-9 else np.array([0, 1.0, 0])
        pos = bary[:, 0:1] * pa + bary[:, 1:2] * pb + bary[:, 2:3] * pc
        P_list.append(pos)
        N_list.append(np.tile(nrm, (len(pos), 1)))
        B_list.append(np.array([name] * len(pos)))
        Y_list.append(ys + oy)
        X_list.append(xs + ox)
    out = {k: v for k, v in mesh.items() if k != "bones"}
    out["bones"] = out_bones
    out["texture_size"] = [A, A]
    out["triangle_count"] = T
    return out, A, np.concatenate(P_list), np.concatenate(N_list), np.concatenate(B_list), np.concatenate(Y_list), np.concatenate(X_list)


def paint(kind, stage, mesh):
    out, A, P, Nr, bones, ys, xs = build(mesh)
    rgb, glow = PAINTERS[kind](P, Nr, bones, stage)
    rgb = rgb * STAGE_DARK[stage]
    if stage >= 2:
        eerie = hexc(EERIE[kind])
        veins = ridge(P * 0.6, 40 + stage) < (0.05 if stage == 2 else 0.1)
        glow = np.maximum(glow, veins.astype(float) * (0.6 if stage == 2 else 0.9))
        rgb[veins] = eerie
    glow = np.clip(glow * STAGE_GLOW[stage], 0, 1)
    atlas = np.zeros((A, A, 4), np.uint8)
    mask = np.zeros((A, A, 4), np.uint8)
    atlas[ys, xs, :3] = (np.clip(rgb, 0, 1) * 255).astype(np.uint8)
    atlas[ys, xs, 3] = 255
    lit = glow > 0.5
    mask[ys[lit], xs[lit]] = (255, 255, 255, 255)
    atlas, mask = dilate(atlas, mask, 2)
    return out, atlas, mask, int(lit.sum())


def dilate(atlas, mask, steps):
    for _ in range(steps):
        empty = atlas[:, :, 3] == 0
        if not empty.any():
            break
        new = atlas.copy()
        newm = mask.copy()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            src = np.roll(atlas, (dy, dx), (0, 1))
            srcm = np.roll(mask, (dy, dx), (0, 1))
            take = empty & (new[:, :, 3] == 0) & (src[:, :, 3] > 0)
            new[take] = src[take]
            newm[take] = srcm[take]
        atlas, mask = new, newm
    return atlas, mask


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    a = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    for kind, opt in CHOICE.items():
        if a.only and a.only != kind:
            continue
        src = os.path.join(RAW, kind, opt, "%s_%s_mesh.json" % (kind, opt))
        mesh0 = json.load(open(src, encoding="utf-8"))
        for stage in (1, 2, 3):
            m, atlas, mask, lit = paint(kind, stage, stretch(mesh0, stage))
            name = "abyss_%s_s%d" % (kind, stage)
            mpath = os.path.join(OUT, name + "_mesh.json")
            apath = os.path.join(OUT, name + "_atlas.png")
            gpath = os.path.join(OUT, name + "_glowmask.png")
            m["name"] = name
            m["texture"] = name
            json.dump(m, open(mpath, "w", encoding="utf-8"))
            Image.fromarray(atlas, "RGBA").save(apath)
            Image.fromarray(mask, "RGBA").save(gpath)
            cmd = [sys.executable, "-I", os.path.join(ROOT, "tools", "install/install_lowpoly.py"), name,
                   "--mesh", mpath, "--atlas", apath, "--glow", gpath]
            subprocess.run(cmd, check=True, cwd=ROOT, env=dict(os.environ, PYTHONIOENCODING="utf-8"))
            # install は水密化で頂点を溶接し直す。島ごとの版 (複製した頂点) を上書きして、絵が島に合うようにする
            lp = os.path.join(ASSETS, "lowpoly", name + ".json")
            with open(lp, "w", encoding="utf-8") as f:
                json.dump(m, f, separators=(",", ":"))
            shutil_copy(apath, os.path.join(ASSETS, "textures", "lowpoly", name + ".png"))
            print("%s stage %d: atlas %d px, lit %d px" % (kind, stage, atlas.shape[0], lit))


def shutil_copy(src, dst):
    import shutil
    shutil.copyfile(src, dst)


# 動きのファイル: GuardMob が読む idle と walk (骨は動かさない。動きは AbyssMob の中の数字で決まる)
JA = {"bone_picker": "骨拾い", "rib_guard": "肋の番", "bone_flute": "骨笛", "salt_husk": "塩の抜け殻", "tide_hands": "潮だまりの手",
      "white_shell_keeper": "白い貝守", "charged_moth": "帯電の羽虫", "lightning_idol": "避雷の像", "scorched_bird": "焦げた鳥の影"}
EN = {"bone_picker": "Bone Picker", "rib_guard": "Rib Guard", "bone_flute": "Bone Flute", "salt_husk": "Salt Husk", "tide_hands": "Tide Hands",
      "white_shell_keeper": "White Shell Keeper", "charged_moth": "Charged Moth", "lightning_idol": "Lightning Idol", "scorched_bird": "Scorched Bird"}
MATERIAL_JA = {"abyss_bone_chip": "骨のかけら", "abyss_salt_crystal": "塩の結晶", "abyss_storm_copper": "雷の銅片"}
MATERIAL_EN = {"abyss_bone_chip": "Bone Chip", "abyss_salt_crystal": "Salt Crystal", "abyss_storm_copper": "Storm Copper"}


def write_animations():
    """アニメは tools/gen/abyss/gen_abyss_mobs_a_anims.py が書く (前は idle / walk の空の枠だった)。"""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_abyss_mobs_a_anims
    gen_abyss_mobs_a_anims.write_all()


MATERIAL_COLOR = {"abyss_bone_chip": (214, 202, 170), "abyss_salt_crystal": (232, 238, 242), "abyss_storm_copper": (92, 186, 170)}


def write_items():
    """スポーンエッグ (テンプレートの親だけ) と素材 3 つの仮の絵 (32×32・かけらの形。後で描き直す)。"""
    from PIL import ImageDraw
    base = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
    for kind in CHOICE:
        p = os.path.join(base, "models", "item", "%s_spawn_egg.json" % kind)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"parent": "minecraft:item/template_spawn_egg"}, f, indent=2)
            f.write("\n")
    for name, rgb in MATERIAL_COLOR.items():
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        dark = tuple(max(0, c - 70) for c in rgb) + (255,)
        light = tuple(min(255, c + 25) for c in rgb) + (255,)
        # 不揃いな塊 (かけら)
        d.polygon([(9, 6), (21, 4), (27, 13), (24, 25), (13, 28), (5, 20), (6, 11)], fill=rgb + (255,), outline=dark)
        d.line([(12, 9), (18, 15), (15, 22)], fill=dark, width=1)
        d.line([(20, 8), (21, 13)], fill=light, width=1)
        img.save(os.path.join(base, "textures", "item", name + ".png"))
        p = os.path.join(base, "models", "item", name + ".json")
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}}, f, indent=2)
            f.write("\n")


def write_lang():
    base = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "lang")
    for fname, names, egg_fmt, mats in (
            ("ja_jp.json", JA, "%sのスポーンエッグ", MATERIAL_JA),
            ("en_us.json", EN, "%s Spawn Egg", MATERIAL_EN)):
        path = os.path.join(base, fname)
        raw = open(path, encoding="utf-8", newline="").read()
        nl = "\r\n" if "\r\n" in raw else "\n"
        data = json.loads(raw)
        for kind, name in names.items():
            data["entity.skyabyss.%s" % kind] = name
            data["item.skyabyss.%s_spawn_egg" % kind] = egg_fmt % name
        for key, name in mats.items():
            data["item.skyabyss.%s" % key] = name
        text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text.replace("\n", nl))


if __name__ == "__main__":
    if "--only" not in sys.argv:
        write_animations()
        write_items()
        write_lang()
    main()
