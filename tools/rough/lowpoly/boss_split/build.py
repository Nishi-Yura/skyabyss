#!/usr/bin/env python3
"""層の主 4 体・淵の王 5 体の「見た目を分ける」ラフ (2026-11-11 の声「??の主全部見た目同じ。手抜き？」「分けるラフを出して」)。

層の主: 形はもともと別 (骸骨・巻貝・樽・蜘蛛) なのに、11/08 の金継ぎで四体とも同じ黒と金に塗られていた。由来ごとの色と光に塗り分ける三案。
淵の王: 体の型が一つで、宝石の色しか違わなかった。共通の金の籠の冠を外し、王ごとに別の頭・下半身・色にする三案。
  (王の三案は、その王のためだけに考えた形。ほかの王と同じ部品は使わない)

絵の塗り直しは、今の絵の明るさを保ったまま色の段 (ランプ) を差し替える (光る画素は光の色の段へ、金の飾りは金物の段へ)。
描き方はゲームと同じ決まり (光る画素は光らせて重ねる)。正方形でない絵も正しく読む。

    python -X utf8 -I tools/rough/lowpoly/boss_split/build.py [名前 ...]
出力: docs/review/boss_split/<名前>.png
"""
from __future__ import annotations

import importlib.util
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "gen/mobs/dungeon_bosses_lp"))
from blp import *  # noqa: E402,F401,F403
import blp  # noqa: E402
from lpkit import Face, render  # noqa: E402

spec_kh = importlib.util.spec_from_file_location("king_heads", Path(__file__).resolve().parent.parent / "king_heads/heads.py")
kh = importlib.util.module_from_spec(spec_kh)
spec_kh.loader.exec_module(kh)

spec_ = importlib.util.spec_from_file_location("he", ROOT / "tools/install/install_heart_eyes.py")
he = importlib.util.module_from_spec(spec_)
spec_.loader.exec_module(he)

A = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "docs/review/boss_split"
V3 = np.array


# ---------------------------------------------------------------- 読み込み (正方形でない絵も)
GEO_ALIAS = {"elder_thrall": "ash_thrall"}


def load(stem):
    data = json.loads((A / f"lowpoly/{stem}.json").read_text(encoding="utf-8"))
    gp_ = A / f"geo/entity/{stem}_lowpoly.geo.json"
    if not gp_.exists():
        gp_ = A / f"geo/entity/{stem}.geo.json"
    if not gp_.exists():
        # 変種は元の種の骨を使う (古い灰被り = 灰被り)
        gp_ = A / f"geo/entity/{GEO_ALIAS.get(stem, stem)}_lowpoly.geo.json"
    geo = json.loads(gp_.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    xf = he.rest_xf(geo)
    img = np.array(Image.open(A / f"textures/lowpoly/{data.get('texture', stem)}.png").convert("RGBA"))
    gp = A / f"textures/lowpoly/{data.get('texture', stem)}_glowmask.png"
    glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 0 if gp.exists() else np.zeros(img.shape[:2], bool)
    H, W = img.shape[:2]
    faces = []
    for bn, b in data["bones"].items():
        if not b["vertices"]:
            continue
        Rm, t = xf(bn)
        Vw = np.array(b["vertices"], float) @ Rm.T + t
        UV = np.array(b["uvs"], float) * np.array([W, H])
        for tr in b["triangles"]:
            v = Vw[tr]
            n = np.cross(v[1] - v[0], v[2] - v[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n = n / np.linalg.norm(n)
            f = Face(verts=v, bone=bn, mat="x", ref=v.mean(0) - n)
            f.normal = n
            f.uv = UV[tr]
            faces.append(f)
    return faces, img, glow


def square(img, glow):
    H, W = img.shape[:2]
    S = max(H, W)
    a = np.zeros((S, S, 4), np.uint8)
    a[:H, :W] = img
    g = np.zeros((S, S), bool)
    g[:H, :W] = glow
    return a, g


# ---------------------------------------------------------------- 塗り直し
def ramp_of(cols, x):
    cols = np.asarray(cols, float)
    x = np.clip(x, 0, 1) * (len(cols) - 1)
    i = np.clip(np.floor(x).astype(int), 0, len(cols) - 2)
    t = (x - i)[..., None]
    return cols[i] * (1 - t) + cols[i + 1] * t


def recolor(img, glow, body, metal, light, keep_glow=None):
    """body: 体の段 (暗→明)、metal: 金の飾りの段、light: 光る画素の段。明るさの並びは今の絵のまま"""
    rgb = img[..., :3].astype(float)
    a = img[..., 3] > 0
    lum = (0.3 * rgb[..., 0] + 0.59 * rgb[..., 1] + 0.11 * rgb[..., 2]) / 255
    mx, mn = rgb.max(-1), rgb.min(-1)
    sat = (mx - mn) / np.maximum(mx, 1)
    goldish = a & ~glow & (rgb[..., 0] > rgb[..., 2] + 40) & (rgb[..., 1] > rgb[..., 2] + 20) & (sat > 0.35) & (mx > 90)
    # 体: 今の絵の明るさを 0..1 に広げてから段へ (暗い絵でも段の幅を使う)
    lo, hi = np.percentile(lum[a & ~goldish & ~glow], [3, 97]) if (a & ~goldish & ~glow).any() else (0, 1)
    nb = np.clip((lum - lo) / max(1e-3, hi - lo), 0, 1)
    out = rgb.copy()
    m = a & ~goldish & ~glow
    out[m] = ramp_of(body, nb[m])
    out[goldish] = ramp_of(metal, np.clip(lum[goldish] * 1.2, 0, 1))
    g = a & glow
    out[g] = ramp_of(light, np.clip(lum[g] * 0.9, 0.15, 0.8))
    res = img.copy()
    res[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    return res


# ---------------------------------------------------------------- 色の段
BONE_W = [(70, 62, 52), (130, 120, 102), (186, 176, 154), (226, 218, 198), (246, 240, 226)]
BLOOD = [(40, 6, 10), (84, 14, 20), (130, 30, 34), (176, 60, 56)]
RUST = [(36, 22, 18), (74, 40, 26), (118, 66, 40), (160, 100, 62), (196, 140, 96)]
IRON = [(30, 30, 34), (58, 58, 64), (96, 96, 104), (140, 140, 148)]
BRONZE = [(60, 40, 20), (110, 76, 40), (160, 118, 64), (200, 160, 100)]
SILVER = [(80, 86, 96), (140, 148, 160), (196, 204, 214), (236, 240, 246)]
COPPER = [(70, 34, 20), (130, 70, 40), (190, 110, 64), (228, 160, 110)]
SALT_W = [(120, 128, 128), (176, 184, 182), (214, 222, 220), (240, 246, 244), (252, 254, 252)]
VERDI = [(20, 46, 44), (40, 90, 80), (70, 140, 120), (120, 190, 164), (180, 230, 210)]
PEARL = [(90, 70, 96), (150, 130, 160), (206, 190, 214), (236, 226, 240), (250, 246, 252)]
NAVY = [(10, 14, 30), (22, 32, 64), (40, 60, 110), (70, 100, 160), (120, 150, 210)]
STEEL = [(36, 40, 48), (70, 76, 88), (110, 118, 132), (160, 168, 182), (210, 216, 226)]
PALEBLUE = [(70, 90, 120), (120, 150, 190), (176, 204, 236), (220, 236, 252), (248, 252, 255)]
HEARTRED = [(30, 6, 18), (70, 14, 34), (120, 24, 50), (170, 50, 70), (210, 100, 110)]
TIN = [(54, 56, 58), (96, 98, 100), (140, 142, 144), (184, 186, 186), (220, 222, 222)]
CHAR = [(14, 12, 12), (30, 26, 24), (52, 46, 42), (80, 72, 66), (112, 102, 94)]
ASHW = [(90, 86, 82), (140, 134, 128), (186, 180, 172), (220, 214, 206), (240, 236, 230)]
SCORCH = [(30, 18, 12), (62, 36, 22), (98, 60, 36), (136, 88, 54), (170, 120, 80)]
VOID = [(4, 4, 8), (10, 10, 18), (20, 18, 32), (34, 30, 52), (54, 48, 80)]
VIOLET = [(20, 10, 34), (44, 22, 70), (78, 40, 116), (120, 70, 166), (170, 120, 210)]
LAV = [(80, 76, 96), (130, 126, 150), (180, 176, 200), (220, 216, 236), (244, 242, 252)]
# 光の段
L_BLUE = [(60, 140, 255), (130, 200, 255), (220, 245, 255)]
L_AMBER = [(220, 120, 30), (255, 180, 60), (255, 230, 150)]
L_RED = [(200, 30, 30), (255, 80, 60), (255, 170, 140)]
L_TEAL = [(30, 170, 160), (90, 230, 210), (190, 255, 240)]
L_PINK = [(220, 120, 200), (255, 170, 230), (255, 230, 250)]
L_CYAN = [(60, 200, 255), (140, 235, 255), (230, 255, 255)]
L_YELLOW = [(230, 200, 40), (255, 236, 110), (255, 252, 210)]
L_ORANGE = [(230, 90, 20), (255, 150, 40), (255, 210, 120)]
L_WHITE = [(200, 200, 220), (236, 236, 250), (255, 255, 255)]
L_VIOLET = [(140, 80, 255), (190, 140, 255), (235, 215, 255)]

PAL = {"bone": BONE_W, "blood": BLOOD, "rust": RUST, "iron": IRON, "bronze": BRONZE, "silver": SILVER, "copper": COPPER, "salt": SALT_W,
       "verdi": VERDI, "pearl": PEARL, "navy": NAVY, "steel": STEEL, "paleblue": PALEBLUE, "char": CHAR, "ashw": ASHW, "scorch": SCORCH,
       "void": VOID, "violet": VIOLET, "lav": LAV, "cloth": [(30, 26, 24), (56, 50, 46), (84, 76, 70)],
       "g_blue": L_BLUE, "g_amber": L_AMBER, "g_red": L_RED, "g_teal": L_TEAL, "g_cyan": L_CYAN, "g_yellow": L_YELLOW, "g_orange": L_ORANGE,
       "g_white": L_WHITE, "g_violet": L_VIOLET, "g_pink": L_PINK}


# ---------------------------------------------------------------- 王の部品 (王ごと・案ごとに別)
class Ref:
    def __init__(self, faces):
        self.faces = faces
        V = np.concatenate([f.verts for f in faces])
        self.lo, self.hi = V.min(0), V.max(0)
        self.c = (self.lo + self.hi) / 2

    def box(self, *names):
        sel = [f for f in self.faces if f.bone in names]
        V = np.concatenate([f.verts for f in sel]) if sel else np.stack([self.lo, self.hi])
        lo, hi = V.min(0), V.max(0)
        return lo, hi, (lo + hi) / 2


def head_top(R):
    lo, hi, c = R.box("head")
    return V3([c[0], hi[1], c[2]]), c, (hi - lo)


# 骨の王
def kb_a(m, R):
    """A 牛の頭蓋の冠: 角の生えた大きな獣の頭蓋をかぶる"""
    top, hc, hs = head_top(R)
    m.add("skull", ell("root", "bone", hc + V3([0, 1.5, -0.5]), (hs[0] * 0.75, hs[1] * 0.6, hs[2] * 0.8), segs=10, rings=4))
    m.add("snout", ell("root", "bone", hc + V3([0, -0.5, -hs[2] * 0.7]), (hs[0] * 0.35, hs[1] * 0.35, hs[2] * 0.45), segs=8, rings=3))
    for s in (1, -1):
        m.add(f"socket{s}", ell("root", "blood", hc + V3([s * hs[0] * 0.3, 1.2, -hs[2] * 0.62]), (0.9, 1.0, 0.5), segs=8, rings=2))
        m.add(f"horn{s}", cone("root", "bone", hc + V3([s * hs[0] * 0.55, hs[1] * 0.5, 0]), (s, 0.4, 0.2), 10, 1.5, sides=6, rings=4, bend=(s * -1, 5, -1)))


def kb_b(m, R):
    """B 肋の裳裾: 衣の下半分が外へ反った肋骨の籠になる (衣の裾を外す)"""
    lo, hi, c = R.box("body", "robe_f", "robe_b")
    for k in range(10):
        a = k / 10 * math.tau
        top = V3([c[0] + math.cos(a) * 4, hi[1] - 2, c[2] + math.sin(a) * 3.5])
        mid = V3([c[0] + math.cos(a) * 9, (lo[1] + hi[1]) / 2, c[2] + math.sin(a) * 8])
        bot = V3([c[0] + math.cos(a) * 6.5, lo[1] + 0.5, c[2] + math.sin(a) * 6])
        m.add(f"rib{k}", tub("root", "bone", [top, mid, bot], [0.8, 0.7, 0.5], None, sides=5))
    kh.bone_head(m, R)  # 頭は獣の頭蓋 (tools/rough/lowpoly/king_heads/heads.py)


def kb_c(m, R):
    """C 墓守の頭巾: 深い頭巾をかぶり、中から骨の面が覗く"""
    top, hc, hs = head_top(R)
    m.add("hood", ell("root", "cloth", hc + V3([0, 1.0, 0.8]), (hs[0] * 0.8, hs[1] * 0.8, hs[2] * 0.85), segs=12, rings=4))
    m.add("hoodtip", cone("root", "cloth", hc + V3([0, hs[1] * 0.6, 1.2]), (0, 0.6, 1), 5, 2.2, sides=6, rings=3, bend=(0, -1.5, 1)))
    m.add("mask", ell("root", "bone", hc + V3([0, 0, -hs[2] * 0.55]), (hs[0] * 0.38, hs[1] * 0.45, 0.6), segs=10, rings=3))
    for s in (1, -1):
        m.add(f"eyeh{s}", ell("root", "void", hc + V3([s * hs[0] * 0.15, 0.6, -hs[2] * 0.62]), (0.6, 0.8, 0.3), segs=6, rings=2))


# 塩の王
def ks_a(m, R):
    """A 塩の晶の頭: 頭そのものが塩の結晶の塊になる (顔はない。2026-11-11 の声「顔無くても王に見える」)"""
    kh.salt_head(m, R)


def ks_b(m, R):
    """B 塩の大角: 頭から鹿の角のような塩の大角が左右へ広がる"""
    top, hc, hs = head_top(R)
    for s in (1, -1):
        b = hc + V3([s * hs[0] * 0.4, hs[1] * 0.4, 0])
        p1 = b + V3([s * 5, 6, 1])
        p2 = p1 + V3([s * 4, 5, -1])
        m.add(f"beam{s}", tub("root", "salt", [b, p1, p2], [1.0, 0.8, 0.5], None, sides=6))
        for k, (dx, dy) in enumerate(((2, 4), (3, 2), (1, 5))):
            q = p1 + (p2 - p1) * (k / 3)
            m.add(f"tine{s}{k}", cone("root", "salt", q, (s * dx * 0.2, 1, 0.1 * k), dy + 2, 0.5, sides=5, rings=2))


def ks_c(m, R):
    """C 塩の面紗の巫女王: 頭から足もとまで届く白い面紗をかぶる"""
    top, hc, hs = head_top(R)
    lo, hi, c = R.box("body", "robe_f", "robe_b")
    for k in range(14):
        a = math.radians(-160 + k * 320 / 13)
        r = hs[0] * 0.7
        p0 = hc + V3([math.sin(a) * r, hs[1] * 0.5, -math.cos(a) * r * 0.9])
        p1 = V3([c[0] + math.sin(a) * (r + 4), (lo[1] + hc[1]) / 2, c[2] - math.cos(a) * (r + 3)])
        p2 = V3([c[0] + math.sin(a) * (r + 6), lo[1] + 1, c[2] - math.cos(a) * (r + 5)])
        m.add(f"veil{k}", tub("root", "salt", [p0, p1, p2], [0.5, 0.45, 0.3], None, sides=4))


# 雷の王
def kt_a(m, R):
    """A 浮かぶ棘の後光: 頭の後ろに鉄の棘の輪が浮かび、輪の内側が光る"""
    top, hc, hs = head_top(R)
    c = hc + V3([0, 1.5, hs[2] * 0.6 + 1.5])
    ring = [c + V3([math.cos(t) * 8, math.sin(t) * 8, 0]) for t in np.linspace(0, math.tau, 25)]
    m.add("ring", tub("root", "copper", ring, [0.6] * len(ring), None, sides=5))
    ring2 = [c + V3([math.cos(t) * 6.6, math.sin(t) * 6.6, 0.2]) for t in np.linspace(0, math.tau, 25)]
    m.add("ring_in", tub("root", "glow", ring2, [0.3] * len(ring2), None, sides=4))
    for k in range(12):
        t = k / 12 * math.tau
        m.add(f"spk{k}", cone("root", "steel", c + V3([math.cos(t) * 8, math.sin(t) * 8, 0]), (math.cos(t), math.sin(t), 0), 3.5, 0.5, sides=4, rings=1))


def kt_b(m, R):
    """B 避雷の塔の王: 冠の代わりに背の高い鉄の塔、脚は消えて雷の尾で浮く (衣と脚を外す)"""
    kh.storm_head(m, R)  # 頭は閉じた兜と避雷針
    lo, hi, c = R.box("body")
    rng = np.random.default_rng(5)
    for k in range(5):
        pts = [V3([c[0], lo[1] + 2, c[2]])]
        for j in range(5):
            pts.append(pts[-1] + V3([rng.uniform(-2, 2), -3.5, rng.uniform(-2, 2)]))
        m.add(f"tail{k}", tub("root", "glow", pts, [0.6 - j * 0.08 for j in range(6)], None, sides=4))


def kt_c(m, R):
    """C 稲妻の枝角: 頭から稲妻の形に枝分かれした光る角が伸びる"""
    top, hc, hs = head_top(R)
    for s in (1, -1):
        p = hc + V3([s * hs[0] * 0.3, hs[1] * 0.4, 0])
        pts = [p]
        for j, (dx, dy) in enumerate(((3, 3), (-1, 3), (3, 3), (-1, 3), (2, 2))):
            pts.append(pts[-1] + V3([s * dx, dy, 0.3 * j]))
        m.add(f"bolt{s}", tub("root", "glow", pts, [0.7, 0.65, 0.55, 0.45, 0.35, 0.2], None, sides=4))
        for j in (2, 4):
            q = pts[j]
            m.add(f"br{s}{j}", tub("root", "glow", [q, q + V3([s * 3, 1, -1]), q + V3([s * 4, 3, -1])], [0.4, 0.3, 0.15], None, sides=4))


# 灰の王
def ka_a(m, R):
    """A 燠の冠: 頭の上で大きな火が燃え、まわりを焦げた鉄の輪が囲む"""
    top, hc, hs = head_top(R)
    for k in range(10):
        a = k / 10 * math.tau
        m.add(f"flame{k}", cone("root", "glow", hc + V3([math.cos(a) * 2.4, hs[1] * 0.4, math.sin(a) * 2.4]), (math.cos(a) * 0.3, 1, math.sin(a) * 0.3),
                                6 + (k % 3) * 3, 1.3, sides=5, rings=3, bend=(math.sin(a) * 1.5, 0, -math.cos(a) * 1.5)))
    ring = [hc + V3([math.cos(t) * 4, hs[1] * 0.4, math.sin(t) * 4]) for t in np.linspace(0, math.tau, 17)]
    m.add("band", tub("root", "iron", ring, [0.6] * len(ring), None, sides=5))


def ka_b(m, R):
    """B 煙突の冠と煤の外套: 頭から三本の煙突、肩から裾まで煤けた外套がぼろぼろに垂れる"""
    kh.ash_head(m, R)  # 頭は炉と三本の煙突
    lo, hi, c = R.box("chest", "body")
    for k in range(12):
        x = c[0] - 9 + k * 1.6
        L = R.c[1] + 4 - (k % 3) * 2
        m.add(f"rag{k}", box("root", "cloth", V3([x, hi[1] - L / 2, hi[2] + 1.2]), (1.5, L, 0.4), taper=(0.6, 1.0)))


def ka_c(m, R):
    """C 熔け落ちる王: 頭と肩から熔けた鉄が垂れ、滴が床まで伸びる"""
    top, hc, hs = head_top(R)
    lo, hi, c = R.box("chest")
    rng = np.random.default_rng(9)
    for k in range(12):
        a = rng.uniform(0, math.tau)
        b = (hc + V3([math.cos(a) * hs[0] * 0.45, hs[1] * 0.3, math.sin(a) * hs[2] * 0.45])) if k < 5 else V3([c[0] + math.cos(a) * 6, hi[1], c[2] + math.sin(a) * 3])
        L = rng.uniform(4, 12)
        m.add(f"drip{k}", tub("root", "glow", [b, b + V3([0, -L * 0.6, 0]), b + V3([0, -L, 0])], [0.8, 0.6, 0.3], None, sides=5))
        m.add(f"drop{k}", ell("root", "glow", b + V3([0, -L - 0.4, 0]), (0.5, 0.7, 0.5), segs=6, rings=2))


# 影の王
def kh_a(m, R):
    """A 無貌の面: 頭が白い卵形の面だけになり、裾が床まで長く引きずる"""
    kh.shadow_head(m, R)  # 頭は白い面と頭巾
    lo, hi, c = R.box("body", "robe_f", "robe_b")
    for k in range(14):
        a = k / 14 * math.tau
        p0 = V3([c[0] + math.cos(a) * 5, lo[1] + 6, c[2] + math.sin(a) * 4])
        p1 = V3([c[0] + math.cos(a) * 9, 0.6, c[2] + math.sin(a) * 9])
        m.add(f"train{k}", tub("root", "void", [p0, (p0 + p1) / 2 + V3([0, -1, 0]), p1], [1.4, 1.0, 0.3], None, sides=5))


def kh_b(m, R):
    """B 影の長角: 頭から細く長い黒い角が後ろへ伸び、背の高い影になる"""
    top, hc, hs = head_top(R)
    for s in (1, -1):
        for k in range(2):
            b = hc + V3([s * (1.2 + k * 1.4), hs[1] * 0.4, 0.5])
            m.add(f"horn{s}{k}", cone("root", "void", b, (s * 0.2, 1, 0.5), 16 - k * 5, 0.9 - k * 0.2, sides=5, rings=4, bend=(s * 1, -1, 4)))
    for s in (1, -1):
        m.add(f"eye{s}", ell("root", "glow", hc + V3([s * hs[0] * 0.18, 0.4, -hs[2] * 0.52]), (0.5, 0.25, 0.2), segs=6, rings=2))


def kh_c(m, R):
    """C 鏡の欠片の冠: 頭のまわりに割れた鏡の欠片が浮かび、銀に光る"""
    top, hc, hs = head_top(R)
    rng = np.random.default_rng(11)
    for k in range(10):
        a = k / 10 * math.tau
        p = hc + V3([math.cos(a) * 6, 2 + math.sin(a * 2) * 1.5, math.sin(a) * 5])
        m.add(f"shard{k}", cone("root", "silver", p, (rng.uniform(-0.3, 0.3), 1, rng.uniform(-0.3, 0.3)), rng.uniform(2.5, 4.5), 1.4, sides=3, rings=1))
        m.add(f"shardg{k}", ell("root", "glow", p + V3([0, 1, 0]), (0.25, 0.25, 0.25), segs=5, rings=1))


# ---------------------------------------------------------------- 案の表
# 層の主: (題, 外す骨, [(案, 体の段, 金物の段, 光の段, 説明)])
LORDS = {
    "lord_rib": ("肋の主 (骨の層)", [("A 白骨と青い灯", "bone", "rust", "g_blue", "白く乾いた骨、錆びた継ぎ金、灯は青白い火"),
                                   ("B 錆びた灯台守", "rust", "iron", "g_amber", "錆と煤の体、灯は琥珀色"),
                                   ("C 血の肋", "blood", "bone", "g_red", "赤黒い肉と、白い骨の継ぎ目")]),
    "lord_salt": ("白殻の主 (塩の層)", [("A 塩の白", "salt", "silver", "g_teal", "塩の結晶の白い殻、潮の青緑の光"),
                                     ("B 緑青の殻", "verdi", "bronze", "g_cyan", "古い銅の緑青の殻"),
                                     ("C 真珠層", "pearl", "silver", "g_pink", "真珠の内側のような淡い紫と桃")]),
    "lord_storm": ("鼓動まねの主 (雷の層)", [("A 青白い雷の肉", "paleblue", "steel", "g_cyan", "血の気のない青白い肉と、雷の脈"),
                                          ("B 心筋の赤", "heart_red", "bronze", "g_violet", "むき出しの心筋の赤と、紫の脈"),
                                          ("C 錫の鼓", "tin", "copper", "g_yellow", "錫の樽の体と、黄色い稲妻")]),
    "lord_ash": ("灯り喰いの主 (灰の層)", [("A 煤と燠", "char", "bronze", "g_orange", "真っ黒な煤の体と、燠の割れ目"),
                                        ("B 灰の白", "ashw", "iron", "g_red", "燃え尽きた白い灰の体と、赤い火"),
                                        ("C 焦げ茶の甲", "scorch", "copper", "g_amber", "焦げた茶の甲羅と、琥珀の灯")]),
}
PAL["heart_red"] = HEARTRED
PAL["tin"] = TIN

# 淵の王: (題, [(案, 体, 金物, 光, 外す骨, 部品の関数)])
KINGS = {
    "king_bone_c0": ("骨の王", [("A 獣の頭蓋の冠", "bone", "bronze", "g_red", {"crown"}, kb_a),
                               ("B 肋の裳裾", "char", "bone", "g_red", {"crown", "head", "robe_f", "robe_b"}, kb_b),
                               ("C 墓守の頭巾", "iron", "bone", "g_amber", {"crown"}, kb_c)]),
    "king_salt_c0": ("塩の王", [("A 塩の晶の頭", "salt", "silver", "g_teal", {"crown", "head"}, ks_a),
                               ("B 塩の大角", "verdi", "salt", "g_teal", {"crown"}, ks_b),
                               ("C 塩の面紗", "lav", "silver", "g_cyan", {"crown"}, ks_c)]),
    "king_storm_c0": ("雷の王", [("A 浮かぶ棘の後光", "navy", "copper", "g_cyan", {"crown"}, kt_a),
                                ("B 避雷の塔 (脚なし)", "steel", "copper", "g_blue", {"crown", "head", "robe_f", "robe_b", "leg_l", "leg_r"}, kt_b),
                                ("C 稲妻の枝角", "paleblue", "silver", "g_yellow", {"crown"}, kt_c)]),
    "king_ash_c0": ("灰の王", [("A 燠の冠", "char", "bronze", "g_orange", {"crown"}, ka_a),
                              ("B 煙突と煤の外套", "ashw", "iron", "g_red", {"crown", "head"}, ka_b),
                              ("C 熔け落ちる王", "scorch", "copper", "g_orange", {"crown"}, ka_c)]),
    "king_shadow_c0": ("影の王", [("A 無貌の面", "void", "silver", "g_white", {"crown", "head"}, kh_a),
                                 ("B 影の長角", "violet", "iron", "g_violet", {"crown"}, kh_b),
                                 ("C 鏡の欠片の冠", "void", "silver", "g_white", {"crown"}, kh_c)]),
}


def parts(fn, R, glow_ramp):
    m = Mount("split", "split")
    fn(m, R)
    pal = {k: M_(v, lit=0.34) for k, v in PAL.items() if not k.startswith("g_")}
    pal["glow"] = M_(PAL[glow_ramp], base=0.8, lit=0.1, min=1)
    pal["eye"] = M_(EYE, base=0.5, lit=0.2, noise=0.0, min=0)
    m.pal = dict(mats=pal, glow_mats=["glow"])
    size, atlas, glow = blp.paint_mount(m, m.pal, None, 256)
    return m.F, atlas, glow


def merge(faces, img, glow, addf, adda, addg):
    sb = img.shape[1]
    S = max(sb + adda.shape[1], img.shape[0])
    at = np.zeros((S, S, 4), np.uint8)
    at[:img.shape[0], :sb] = img
    at[:adda.shape[0], sb:sb + adda.shape[1]] = adda
    g = np.zeros((S, S), bool)
    g[:glow.shape[0], :sb] = glow
    g[:addg.shape[0], sb:sb + addg.shape[1]] = addg
    out = list(faces)
    for f in addf:
        h = Face(verts=f.verts, bone=f.bone, mat=f.mat, ref=f.ref)
        h.normal = f.normal
        h.uv = f.uv + np.array([sb, 0.0])
        out.append(h)
    return out, at, g


def sheet(name, title, cols):
    F = voxel.font
    allV = np.concatenate([np.concatenate([f.verts for f in c[1]]) for c in cols])
    lo, hi = allV.min(0), allV.max(0)
    c = (lo + hi) / 2
    Rf = float(np.max(hi - lo)) / 2
    PW, PH = 360, 400
    cv = Image.new("RGB", (20 + len(cols) * (PW + 10), 110 + 2 * (PH + 10)), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), f"{title} — 見た目を分けるラフ", font=F(26), fill=(236, 232, 218))
    for i, (label, faces, at, g, note) in enumerate(cols):
        x = 20 + i * (PW + 10)
        d.text((x, 50), label, font=F(18), fill=(150, 232, 255))
        d.text((x, 76), note, font=F(13), fill=(176, 176, 192))
        a2, g2 = square(at, g)
        for j, (yaw, pitch) in enumerate(((180, 6), (215, 12))):
            im = render(faces, a2[..., :3], g2, yaw, pitch, (PW, PH), c, PW * 0.46 / Rf)
            bgi = Image.new("RGBA", (PW, PH), (28, 28, 38, 255))
            bgi.alpha_composite(im)
            cv.paste(bgi.convert("RGB"), (x, 110 + j * (PH + 10)))
    cv.save(OUT / f"{name}.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(LORDS) + list(KINGS)
    for name in names:
        faces, img, glow = load(name)
        cols = [("今の姿", faces, img, glow, "11/08 の姿")]
        if name in LORDS:
            title, opts = LORDS[name]
            for label, body, metal, light, note in opts:
                cols.append((label, faces, recolor(img, glow, PAL[body], PAL[metal], PAL[light]), glow, note))
        else:
            title, opts = KINGS[name]
            R = Ref(faces)
            for label, body, metal, light, drop, fn in opts:
                kept = [f for f in faces if f.bone not in drop]
                ri = recolor(img, glow, PAL[body], PAL[metal], PAL[light])
                af, aa, ag = parts(fn, R, light)
                mf, ma, mg = merge(kept, ri, glow, af, aa, ag)
                cols.append((label, mf, ma, mg, fn.__doc__.split(":")[1].strip()[:26]))
        sheet(name, title, cols)
        print(name, "ok", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
