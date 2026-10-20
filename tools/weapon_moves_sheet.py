#!/usr/bin/env python3
"""武器の動き (weapon_moves/*.json) の一人称キーフレームの帯を 1 枚にまとめる: docs/review/previews/weapon_moves_sheet.png

ゲームを動かさずに、JSON の値 (TX/TY/TZ/RX/RY/RZ = 一人称の手元) から、武器を針金の絵にして連続の絵を描く。
武器の形は目安 (種類ごとの簡単な輪郭)。ゲームの手元の変換 (WeaponClient#applyStep) と同じ順に回す:
  移動 → Y(45 + RY) → Z(RZ) → X(RX) → Y(-45)。視野は 70 度。
各帯 = 1 段 (連撃の 1 振り)。枠の下に、振りの長さ (攻撃速度から決まる tick) での経過 tick。
当たる枠 (命中) は縁を赤茶で囲み、サーバーがダメージを入れる tick とヒットストップを書く。
使い方: python tools/weapon_moves_sheet.py   (Pillow。重くない)
"""
import json
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "weapon_moves")
OUT = os.path.join(ROOT, "docs", "review", "previews", "weapon_moves_sheet.png")

# 攻撃速度 (4 + 品の差。MoveSet#durationFor で 12 / 速度 を 6..16 tick に丸める)
SPEED = {"whalebone_greatsword": 1.0, "captain_pike": 1.1, "tide_shell_trident": 1.5, "coral_harpoon": 1.8, "warden_glaive": 1.5, "waterway_fang": 2.2,
         "raiju_claw": 2.0, "tidecaller_claw": 1.0, "ember_war_axe": 1.0, "smith_hammer": 0.8, "thrall_poker": 1.6, "abyss_greatsword": 0.9,
         "abyss_bell_staff": 1.6, "abyss_ash_scythe": 1.2}
ORDER = ["whalebone_greatsword", "abyss_greatsword", "captain_pike", "tide_shell_trident", "coral_harpoon", "warden_glaive", "waterway_fang", "raiju_claw",
         "tidecaller_claw", "thrall_poker", "ember_war_axe", "smith_hammer", "abyss_ash_scythe", "abyss_bell_staff", "thunder_wing_bow", "abyss_storm_bow",
         "coral_shield"]
# 形: (種類, 長さ)
SHAPE = {"whalebone_greatsword": ("blade", 1.55), "abyss_greatsword": ("blade", 1.7), "captain_pike": ("spear", 2.1), "tide_shell_trident": ("trident", 1.9),
         "coral_harpoon": ("spear", 1.7), "warden_glaive": ("glaive", 2.0), "waterway_fang": ("dagger", 0.62), "raiju_claw": ("claw", 0.6),
         "tidecaller_claw": ("claw", 0.75), "thrall_poker": ("poker", 1.0), "ember_war_axe": ("axe", 1.2), "smith_hammer": ("hammer", 1.3),
         "abyss_ash_scythe": ("scythe", 1.7), "abyss_bell_staff": ("staff", 1.6), "thunder_wing_bow": ("bow", 1.1), "abyss_storm_bow": ("bow", 1.2),
         "coral_shield": ("shield", 0.7)}

BG = (236, 232, 224)
CARD = (226, 221, 211)
INK = (70, 66, 60)
SUB = (128, 122, 112)
WEAPON = (96, 90, 82)
HEAD = (62, 58, 52)
HIT = (160, 96, 72)
TRAIL = (184, 160, 120)
GLOWC = (110, 140, 160)
HAND = (205, 190, 170)

FONT_PATHS = ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/YuGothR.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(size):
    for p in FONT_PATHS:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def ease(name, s):
    s = max(0.0, min(1.0, s))
    return {
        "linear": s, "smooth": s * s * (3 - 2 * s), "in": s * s, "out": 1 - (1 - s) ** 2,
        "in_out": 2 * s * s if s < 0.5 else 1 - 2 * (1 - s) ** 2, "in_cubic": s ** 3, "out_cubic": 1 - (1 - s) ** 3,
        "in_back": 2.2 * s ** 3 - 1.2 * s * s, "out_back": 1 + 2.4 * (s - 1) ** 3 + 1.4 * (s - 1) ** 2,
        "snap": 1 - (1 - s) ** 4, "spring": 1 - math.exp(-5 * s) * math.cos(9 * s),
    }.get(name, s * s * (3 - 2 * s))


def tracks(step):
    tr = {}
    for key in step["keys"]:
        for ch, v in key["v"].items():
            tr.setdefault(ch, []).append((key["t"], v, key["e"]))
    return tr


def val(tr, ch, t, default=0.0):
    pts = tr.get(ch)
    if not pts:
        return default
    if t <= pts[0][0]:
        return pts[0][1]
    for i in range(1, len(pts)):
        if t <= pts[i][0]:
            t0, v0, _ = pts[i - 1]
            t1, v1, e = pts[i]
            s = 1.0 if t1 <= t0 else (t - t0) / (t1 - t0)
            return v0 + (v1 - v0) * ease(e, s)
    return pts[-1][1]


def wrap(d):
    d = d % 360
    return d - 360 if d > 180 else d


def rot_y(p, deg):
    a = math.radians(deg)
    x, y, z = p
    return (x * math.cos(a) + z * math.sin(a), y, -x * math.sin(a) + z * math.cos(a))


def rot_z(p, deg):
    a = math.radians(deg)
    x, y, z = p
    return (x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a), z)


def rot_x(p, deg):
    a = math.radians(deg)
    x, y, z = p
    return (x, y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a))


BASE = (0.56, -0.52, -0.72)
# 武器の根もとの向き (手の中で、前上へ)。軸 = 前上、横 = 右
AXIS = (0.12, 0.86, -0.50)
SIDE = (0.99, -0.10, 0.10)


def local(p_axis, p_side):
    return (AXIS[0] * p_axis + SIDE[0] * p_side, AXIS[1] * p_axis + SIDE[1] * p_side, AXIS[2] * p_axis + SIDE[2] * p_side)


def pose_points(tr, t, pts):
    """武器の座標 (軸方向, 横) の組 → 視点の空間の点。"""
    w = weight(tr, t)
    tx, ty, tz = val(tr, "TX", t) * w, val(tr, "TY", t) * w, val(tr, "TZ", t) * w
    ry = wrap(val(tr, "RY", t)) * w
    rz = wrap(val(tr, "RZ", t)) * w
    rx = val(tr, "RX", t) * w
    out = []
    for a, s in pts:
        p = local(a, s)
        p = rot_y(p, -45)
        p = rot_x(p, rx)
        p = rot_z(p, rz)
        p = rot_y(p, 45 + ry)
        out.append((BASE[0] + tx + p[0], BASE[1] + ty + p[1], BASE[2] + tz + p[2]))
    return out


def weight(tr, t, bi=0.12, bo=0.2):
    a = min(1.0, t / bi) if bi > 0 else 1.0
    b = min(1.0, (1 - t) / bo) if bo > 0 else 1.0
    w = min(a, b)
    return w * w * (3 - 2 * w)


def shape_polys(kind, L):
    """(軸, 横) の多角形たち (濃さ, 点の列)。"""
    polys = []
    shaft = lambda a0, a1, w=0.025: [(a0, -w), (a1, -w), (a1, w), (a0, w)]
    if kind == "blade":
        polys += [(0, shaft(-0.18 * L, 0.2 * L, 0.03)), (1, [(0.2 * L, -0.15), (0.2 * L, 0.15), (0.24 * L, 0.15), (0.24 * L, -0.15)]),
                  (1, [(0.24 * L, -0.07), (0.9 * L, -0.07), (L, 0), (0.9 * L, 0.07), (0.24 * L, 0.07)])]
    elif kind == "dagger":
        polys += [(0, shaft(-0.12, 0.12, 0.025)), (1, [(0.12, -0.06), (0.12, 0.06), (L, 0.0)])]
    elif kind == "claw":
        polys += [(0, shaft(-0.1, 0.12, 0.03)), (1, [(0.12, -0.09), (0.12, 0.09), (L, 0.0)]), (1, [(0.12, 0.09), (0.12, 0.16), (L * 0.85, 0.05)])]
    elif kind == "poker":
        polys += [(0, shaft(-0.1, 0.25, 0.025)), (1, [(0.25, -0.012), (L, -0.012), (L, 0.012), (0.25, 0.012)]), (1, [(L, -0.05), (L, 0.05), (L + 0.08, 0.0)])]
    elif kind in ("spear", "trident", "glaive"):
        polys += [(0, shaft(-0.5, L, 0.02))]
        if kind == "spear":
            polys += [(1, [(L, -0.07), (L, 0.07), (L + 0.3, 0.0)])]
        elif kind == "trident":
            polys += [(1, [(L, -0.12), (L, 0.12), (L + 0.05, 0.12), (L + 0.05, -0.12)]), (1, [(L, -0.11), (L, -0.08), (L + 0.32, -0.1)]),
                      (1, [(L, 0.08), (L, 0.11), (L + 0.32, 0.1)]), (1, [(L, -0.025), (L, 0.025), (L + 0.4, 0.0)])]
        else:
            polys += [(1, [(L - 0.15, 0.0), (L - 0.15, 0.1), (L + 0.2, 0.06), (L + 0.28, -0.05), (L + 0.05, -0.02)])]
    elif kind in ("axe", "hammer"):
        polys += [(0, shaft(-0.25, L, 0.03))]
        polys += [(1, [(L - 0.05, -0.05), (L - 0.05, 0.28), (L + 0.2, 0.22), (L + 0.2, -0.05)])] if kind == "axe" else \
                 [(1, [(L - 0.12, -0.2), (L - 0.12, 0.2), (L + 0.16, 0.2), (L + 0.16, -0.2)])]
    elif kind == "scythe":
        polys += [(0, shaft(-0.3, L, 0.02)), (1, [(L, -0.02), (L + 0.05, 0.28), (L + 0.25, 0.5), (L + 0.2, 0.3), (L + 0.05, 0.0)])]
    elif kind == "staff":
        polys += [(0, shaft(-0.45, L, 0.025))]
        r = 0.1
        polys += [(1, [(L + 0.1 + r * math.cos(a * math.pi / 6), r * math.sin(a * math.pi / 6)) for a in range(12)])]
    elif kind == "bow":
        arc = [(0.0 + 0.45 * L * math.sin(math.radians(a)) * 1.0, -0.16 * math.cos(math.radians(a))) for a in range(-80, 81, 10)]
        polys += [(1, [(p[0], p[1] * 0.0 + p[1] - 0.0) for p in arc] + [(p[0], p[1] - 0.02) for p in reversed(arc)])]
    elif kind == "shield":
        polys += [(1, [(-0.1, -0.28), (0.5, -0.28), (0.6, 0.0), (0.5, 0.28), (-0.1, 0.28)])]
    return polys


def project(p, size, cx, cy, scale):
    x, y, z = p
    if z > -0.12:
        z = -0.12
    f = 1.0 / math.tan(math.radians(35))
    nx = x / -z * f
    ny = y / -z * f
    return (cx + nx * scale, cy - ny * scale)


def draw_frame(img, d0, ox, oy, size, kind, L, tr, t, hit, trail_prev):
    # 枠ごとに別の画像へ描いて貼る (はみ出しを切る)
    fr = Image.new("RGB", (size, size), (244, 241, 235))
    d = ImageDraw.Draw(fr)
    sc = size / 2.4
    cx = 0.6 * sc
    cy = size / 2
    d.line([cx - 5, cy, cx + 5, cy], fill=(200, 194, 182))
    d.line([cx, cy - 5, cx, cy + 5], fill=(200, 194, 182))
    polys = shape_polys(kind, L)
    for k, tt in enumerate(trail_prev):
        for shade, poly in polys:
            if shade == 1:
                pts = [project(p, size, cx, cy, sc) for p in pose_points(tr, tt, poly)]
                d.polygon(pts, fill=TRAIL if k == 0 else (214, 196, 160))
    gx, gy = project(pose_points(tr, t, [(0, 0)])[0], size, cx, cy, sc)
    for shade, poly in polys:
        pts = [project(p, size, cx, cy, sc) for p in pose_points(tr, t, poly)]
        d.polygon(pts, fill=HEAD if shade else WEAPON)
    d.ellipse([gx - 4, gy - 4, gx + 4, gy + 4], fill=HAND, outline=SUB)
    glow = val(tr, "GLOW", t, 0.0)
    if glow > 0.3:
        d.ellipse([size - 16, 5, size - 6, 15], fill=GLOWC)
    d.rectangle([0, 0, size - 1, size - 1], outline=HIT if hit else (200, 194, 182), width=4 if hit else 1)
    img.paste(fr, (ox, oy))


def main():
    S = 2  # 描くときの倍率 (縮めてなめらかにする)
    f_title, f_small, f_tiny = font(15 * S), font(11 * S), font(10 * S)
    W = {}
    for item in ORDER:
        with open(os.path.join(SRC, item + ".json"), encoding="utf-8") as f:
            W[item] = json.load(f)
    FR = 7
    size = 92 * S
    gap = 6 * S
    label_w = 150 * S
    col_w = label_w + FR * (size + gap) + 18 * S
    # 列に分ける: 武器 17 品を 2 列に (段の数で高さを見て)
    heights = {}
    for item in ORDER:
        heights[item] = 38 * S + len(W[item]["steps"]) * (size + 24 * S + 6 * S) + 10 * S
    cols = [[], []]
    tot = sum(heights.values())
    acc = 0
    for item in ORDER:
        c = 0 if acc < tot / 2 else 1
        cols[c].append(item)
        acc += heights[item]
    colh = [sum(heights[i] for i in c) for c in cols]
    top = 64 * S
    img = Image.new("RGB", (2 * col_w + 30 * S, top + max(colh) + 20 * S), BG)
    d = ImageDraw.Draw(img)
    d.text((14 * S, 12 * S), "武器の動き: 一人称のキーフレーム (連撃の 1 段ごと)", fill=INK, font=font(20 * S))
    d.text((14 * S, 38 * S), "枠の下 = 振りの長さ (攻撃速度から決まる tick) での経過 tick。赤茶の枠 = ダメージが入る瞬間 (サーバーも同じ tick)。ヒットストップは当たった後に止まる tick。薄い帯 = 刃の軌跡 / 青い点 = 光る画素が強まる", fill=SUB, font=f_small)
    for ci, col in enumerate(cols):
        x0 = 15 * S + ci * (col_w)
        y = top
        for item in col:
            data = W[item]
            speed = SPEED.get(item)
            dur = None
            if speed:
                dur = max(6, min(16, round(12 / speed)))
            elif data.get("fixedDuration"):
                dur = data["fixedDuration"]
            strike = data["strike"]
            stick = max(1, min((dur or 12) - 1, round(strike * (dur or 12)))) if strike > 0 and dur else None
            d.rectangle([x0, y, x0 + col_w - 10 * S, y + heights[item] - 6 * S], fill=CARD)
            d.text((x0 + 8 * S, y + 6 * S), data["label"], fill=INK, font=f_title)
            meta = (f"振り {dur} tick / 命中 {stick} tick" if stick else f"返し {dur} tick") + f" / 連撃 {len(data['steps'])} 段"
            d.text((x0 + 8 * S, y + 6 * S + 18 * S), meta, fill=SUB, font=f_tiny)
            kind, L = SHAPE[item]
            yy = y + 38 * S
            for si, step in enumerate(data["steps"]):
                tr = tracks(step)
                hs = step["hitstop"]
                d.text((x0 + 8 * S, yy + 4 * S), f"{si + 1}. {step['name']}", fill=INK, font=f_small)
                info = f"ヒットストップ {hs} / 揺れ {step['shake']:g}"
                d.text((x0 + 8 * S, yy + 22 * S), info, fill=SUB, font=f_tiny)
                times = sorted({k["t"] for k in step["keys"]})
                hit_t = next((k["t"] for k in step["keys"] if k.get("hit")), None)
                # 7 コマ: 0 と 1 を除く key から、命中を必ず入れて均等に選ぶ
                inner = [t for t in times if 0.0 < t < 1.0]
                pick = []
                if len(inner) <= FR - 1:
                    pick = inner[:]
                else:
                    idx = sorted({round(i * (len(inner) - 1) / (FR - 2)) for i in range(FR - 1)})
                    pick = [inner[i] for i in idx]
                    if hit_t is not None and hit_t not in pick:
                        pick[min(range(len(pick)), key=lambda i: abs(pick[i] - hit_t))] = hit_t
                frames = ([0.0] + pick)[:FR - 1] + [1.0]
                frames = sorted(set(frames))
                for fi, t in enumerate(frames[:FR]):
                    fx = x0 + label_w + fi * (size + gap)
                    prev = []
                    if val(tr, "TRAIL", t, 0) > 0.3:
                        prev = [max(0, t - 0.05), max(0, t - 0.1)]
                    draw_frame(img, d, fx, yy, size, kind, L, tr, t, hit_t is not None and abs(t - hit_t) < 1e-6, prev)
                    tk = f"{t * (dur or 12):.0f}" if dur else f"{t:.2f}"
                    label = f"t{tk}" + ("  命中" if hit_t is not None and abs(t - hit_t) < 1e-6 else "")
                    d.text((fx + 4 * S, yy + size + 3 * S), label, fill=HIT if "命中" in label else SUB, font=f_tiny)
                yy += size + 24 * S + 6 * S
            y += heights[item]
    img = img.resize((img.width // S, img.height // S), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, optimize=True)
    print("saved", os.path.normpath(OUT), img.size)


if __name__ == "__main__":
    sys.exit(main())
