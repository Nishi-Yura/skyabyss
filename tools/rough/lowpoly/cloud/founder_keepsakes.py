# -*- coding: utf-8 -*-
"""初代の空見の報酬の品 (docs/design/53 の 6 章)。一品一案。
  founder_shawl     空見の肩掛け (見た目だけ)。始祖の肩の星図の藍の布を、プレイヤーの体の大きさに。背中に描く (client/founder/ShawlLayer)
  founder_handbell  始祖の小鐘 (記念品)。始祖の腰の小鐘と同じ青銅に緑青の帯、木の柄に金の環
座標はプレイヤーの体の大きさ (1 単位 = 1/16 ブロック、y は上。体は y 12〜24、首の付け根 = y 24、幅 ±4・奥行き ±2)。
出力:
  docs/review/cloud_roughs/<id>.png・<id>/a/                         見直しの絵とメッシュ
  assets/skyabyss/lowpoly/items/<id>/common.json (+ textures/lowpoly/items/<id>/common_atlas(_glow).png)   体に描くメッシュ
  assets/skyabyss/textures/item/<id>.png                              品の画面の絵 (32x32。同じメッシュを写す)

  python -X utf8 tools/rough/lowpoly/cloud/founder_keepsakes.py
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from mk import *  # noqa
sys.path.insert(0, str(HERE.parents[2] / "lib"))
import mesh_close  # noqa: E402

ROOT = HERE.parents[3]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
INDIGO = [(14, 18, 40), (24, 32, 64), (38, 50, 92), (58, 74, 126)]
WOOD = [(52, 36, 24), (80, 56, 36), (110, 80, 52), (140, 106, 72)]
BONES = {"root": (None, (0, 0, 0))}
PAL = make_pal({
    "indigo": (INDIGO, 0.5, dict(crack=0.03, noise=0.16)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "bronze": (BRONZE, 0.66, dict(crack=0.08, noise=0.18)),
    "verdi": (VERDI, 0.55, dict(crack=0.06, noise=0.22)),
    "wood": (WOOD, 0.55, dict(crack=0.05, noise=0.18)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def hoop(bone, mat, y, rx, rz, cx=0.0, zc=0.0, r=0.3, n=14):
    pts = [(cx + math.cos(a) * rx, y, zc + math.sin(a) * rz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 1, 0))


def shawl(k):
    F = []
    # 肩をくるむ短い覆い (体の肩 y 24、幅 ±4 の少し外)
    cape = [ring(18.6, 5.0, -2.9, 3.0, n=14), ring(22.4, 4.95, -2.85, 2.95, n=14), ring(24.4, 4.2, -2.55, 2.6, n=14), ring(25.4, 2.3, -1.5, 1.5, n=14)]
    F += part("cape", loft("root", "indigo", cape, caps=("hollow", "indigo")))
    F += part("hem", hoop("root", "gold", 18.7, 5.05, 2.95, zc=0.05, r=0.3, n=16))
    # 後ろへ長い二本の垂れ (膝の上まで)
    for s in (-1, 1):
        tail = [ring(23.4, 1.4, 2.2, 3.0, cx=s * 1.8), ring(17.0, 1.5, 2.5, 3.3, cx=s * 2.0), ring(11.0, 1.55, 2.8, 3.6, cx=s * 2.2), ring(6.0, 1.5, 3.1, 3.9, cx=s * 2.3)]
        F += part(f"tail{s}", loft("root", "indigo", tail, caps=("indigo", "indigo")))
        F += part(f"tailend{s}", tube("root", "gold", [(s * 2.3 - 1.5, 5.9, 3.5), (s * 2.3 + 1.5, 5.9, 3.5)], ra=0.26, sides=4, hint=(0, 1, 0)))
    # 星 (光る)
    stars = [(-3.6, 21.6, -2.6), (-2.0, 23.6, -2.4), (3.2, 22.6, -2.75), (1.8, 20.4, -2.95), (-4.4, 20.8, 1.4), (4.2, 21.4, 1.8),
             (0.0, 24.6, 2.0), (-2.0, 16.0, 3.4), (2.2, 13.0, 3.7), (-2.3, 9.0, 3.9), (2.3, 8.0, 4.0)]
    for i, (x, y, z) in enumerate(stars):
        F += part(f"star{i}", ellipsoid("root", "dawn", (x, y, z), (0.3, 0.3, 0.3), segs=4, rings=2))
    return F, []


def handbell(k):
    F = []
    # 鐘 (口が下。y 0〜7) と、中の舌の玉
    bell = [ring(0.0, 3.6, -3.6, 3.6, n=14), ring(0.8, 3.2, -3.2, 3.2, n=14), ring(3.0, 2.5, -2.5, 2.5, n=14), ring(5.6, 2.2, -2.2, 2.2, n=14),
            ring(7.0, 1.4, -1.4, 1.4, n=14), ring(7.6, 0.5, -0.5, 0.5, n=14)]
    F += part("bell", loft("root", "bronze", bell, caps=("hollow", "bronze")))
    F += part("lip", hoop("root", "gold", 0.35, 3.45, 3.45, r=0.32, n=16))
    F += part("band", hoop("root", "verdi", 4.2, 2.42, 2.42, r=0.28, n=16))
    F += part("ball", ellipsoid("root", "bronze", (0, 0.4, 0), (0.9, 0.9, 0.9), segs=6, rings=3))
    # 柄 (木) と金の環、上に小さな輪
    F += part("collar", tube("root", "gold", [(0, 7.2, 0), (0, 8.2, 0)], ra=[0.9, 0.7], sides=6, hint=(1, 0, 0)))
    F += part("handle", tube("root", "wood", [(0, 8.0, 0), (0, 13.6, 0)], ra=[0.62, 0.7], sides=6, hint=(1, 0, 0)))
    F += part("ring", hoop("root", "gold", 11.0, 0.78, 0.78, r=0.2, n=10))
    F += part("knob", ellipsoid("root", "gold", (0, 14.2, 0), (0.95, 0.8, 0.95), segs=6, rings=2))
    # 朝の光の小さな刻み (鐘の胴の正面)
    F += part("mark", ellipsoid("root", "dawn", (0, 3.0, -2.55), (0.45, 0.45, 0.16), segs=6, rings=2))
    return F, []


SPECS = {
    "founder_shawl": (shawl, dict(
        id="founder_shawl", short="空見の肩掛け", kind="裏ボスの報酬 一品一案 (53 の 6 章)",
        title="空見の肩掛け (founder_shawl) — 見た目だけ。星図を刺した藍の布、後ろに長い二本の垂れ",
        sub=["初代の空見の肩の布を、プレイヤーの体の大きさに。装飾の枠を使わず、着る・脱ぐは品の右クリック。"],
        labels=["空見の肩掛け"],
        rows=[("前 3/4", "idle", 212, 8, 360, (0, 16, 0), 9.0), ("後ろ 3/4", "idle", 32, 10, 360, (0, 16, 0), 9.0)],
        options={"a": dict(name="星図の肩掛け", desc="藍の布に金の縁と刺した星 (光る)。")},
        extra="骨 1 本 (root)。", budget="300〜700", pw=360), (0, 15.6, 0), 1.15, 205, 10),
    "founder_handbell": (handbell, dict(
        id="founder_handbell", short="始祖の小鐘", kind="裏ボスの報酬 一品一案 (53 の 6 章)",
        title="始祖の小鐘 (founder_handbell) — 青銅に緑青の帯、木の柄に金の環",
        sub=["初代の空見の腰の小鐘。右クリックで澄んだ一打ち、都では大鐘楼が応える。"],
        labels=["始祖の小鐘"],
        rows=[("前 3/4", "idle", 212, 8, 360, (0, 7, 0), 18.0), ("後ろ 3/4", "idle", 32, 10, 360, (0, 7, 0), 18.0)],
        options={"a": dict(name="始祖の小鐘", desc="青銅の鐘に緑青の帯と金の口縁、木の柄。")},
        extra="骨 1 本 (root)。", budget="200〜500", pw=360), (0, 7.2, 0), 1.85, 200, 12),
}


def install(item, r, center, scale, yaw, pitch):
    d = OUT / item / "a"
    mesh = json.loads((d / f"{item}_a_mesh.json").read_text(encoding="utf-8"))
    mesh["texture"] = f"items/{item}/common_atlas"
    mesh, _ = mesh_close.close_mesh(mesh)
    md = ASSETS / "lowpoly/items" / item
    md.mkdir(parents=True, exist_ok=True)
    (md / "common.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
    td = ASSETS / "textures/lowpoly/items" / item
    td.mkdir(parents=True, exist_ok=True)
    atlas = Image.open(d / f"{item}_a_atlas.png").convert("RGBA")
    atlas.save(td / "common_atlas.png")
    mask = Image.open(d / f"{item}_a_glowmask.png").convert("L")
    glow = Image.new("RGBA", atlas.size, (0, 0, 0, 0))
    lit = 0
    ap, mp, gp = atlas.load(), mask.load(), glow.load()
    for y in range(atlas.size[1]):
        for x in range(atlas.size[0]):
            if mp[x, y] > 127:
                gp[x, y] = ap[x, y]
                lit += 1
    if lit:
        glow.save(td / "common_atlas_glow.png")
    # 品の絵: 128 で写して 32 へ (最近傍で縮めると線が欠けるので、4x4 の平均の不透明だけ拾う)
    big = render(r["faces"], r["atlas"], r["glow"], yaw, pitch, (128, 128), center, scale * 4)
    big = big.convert("RGBA")
    small = big.resize((32, 32), Image.BOX)
    px = small.load()
    for y in range(32):
        for x in range(32):
            c = px[x, y]
            px[x, y] = (c[0], c[1], c[2], 255) if c[3] > 110 else (0, 0, 0, 0)
    # 縁取り (外側 1 画素を暗く)
    out = small.copy()
    op = out.load()
    for y in range(32):
        for x in range(32):
            if px[x, y][3]:
                continue
            if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and px[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                op[x, y] = (18, 16, 24, 255)
    out.save(ASSETS / "textures/item" / f"{item}.png")
    print(item, "installed", mesh.get("triangle_count"), "tris, glow", lit)


if __name__ == "__main__":
    for item, (build, spec, center, scale, yaw, pitch) in SPECS.items():
        r = run_one(spec, build, PAL, BONES, {"idle": {}}, hint=128)
        install(item, r, center, scale, yaw, pitch)
