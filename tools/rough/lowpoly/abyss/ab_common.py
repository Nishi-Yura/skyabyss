"""層の主・歪んだ王・生き残りのラフ (docs/review/roughs_abyss/) の共通部品。

ck_ab (深淵の敵のラフ) の run をそのまま使い、出力先と比べシートの体裁だけ替える。
  - 出力: docs/review/roughs_abyss/<id>.png と <id>/{a,b,c}/ (メッシュ・アトラス・光る画素のマスク)
  - シート: 上に 3 案の名前と一行の案、下に 前 / 3/4 / 横 (と動きの行) を縦に並べる。携帯の幅でも読めるよう文字は大きめ。
座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ck_ab as CA  # noqa: E402
from ck_ab import *  # noqa: E402,F401,F403

ROOT = Path(__file__).resolve().parents[4]
OUT2 = ROOT / "docs/review/roughs_abyss"
CA.OUT = OUT2
CA._ck.OUT = OUT2
CA.write_manifest = lambda spec, results: None
V_ = CA.V_
eye_at = CA.eye

# ---- 層の色の言葉 (docs/design/36 の 4.1 と同じ)
BONEC = [(34, 30, 28), (80, 72, 62), (126, 114, 92), (168, 156, 126), (210, 200, 170), (236, 228, 204)]
CALC = [(56, 58, 62), (104, 106, 110), (152, 154, 158), (200, 202, 204), (234, 234, 230)]
ASHY = [(38, 36, 38), (72, 70, 72), (110, 108, 108), (150, 148, 144)]
HOLLOW = [(10, 6, 8), (22, 14, 16), (40, 26, 28)]
SALT = [(84, 92, 102), (140, 150, 158), (196, 204, 208), (228, 236, 236), (250, 252, 250)]
SALTC = [(60, 70, 80), (104, 116, 126), (150, 162, 170), (200, 210, 214)]
SEA = [(16, 36, 46), (30, 64, 78), (52, 98, 110), (86, 136, 140)]
TUFF = [(36, 34, 34), (66, 62, 58), (100, 94, 86), (138, 130, 116)]
OXCU = [(26, 56, 54), (44, 92, 84), (70, 134, 118), (106, 176, 150)]
BASALT = [(10, 10, 12), (24, 24, 28), (42, 42, 48), (66, 64, 70)]
SOOT = [(6, 6, 8), (16, 14, 16), (30, 26, 28), (48, 42, 42)]
EMBER = [(110, 30, 8), (190, 70, 14), (240, 130, 34), (255, 196, 90)]
RED = [(90, 14, 14), (160, 30, 26), (220, 60, 44)]
BLOODL = [(60, 20, 28), (110, 44, 54), (160, 80, 84)]


def PALX(extra=None, glow=("teal",)):
    m = {
        "void": mat(VOID, 0.5, crack=0.0, noise=0.12),
        "slate": mat(SLATE, 0.5, crack=0.0, noise=0.12),
        "teal": mat(TEAL, 0.75, crack=0.0, noise=0.06, min=2),
        "bone": mat(BONEC, 0.62, crack=0.14, noise=0.22),
        "calc": mat(CALC, 0.6, crack=0.12, noise=0.2),
        "ash": mat(ASHY, 0.55, crack=0.18, noise=0.26),
        "hollow": mat(HOLLOW, 0.5, crack=0.0, noise=0.05),
        "salt": mat(SALT, 0.66, crack=0.08, noise=0.14),
        "saltc": mat(SALTC, 0.6, crack=0.12, noise=0.18),
        "sea": mat(SEA, 0.6, crack=0.0, noise=0.1),
        "tuff": mat(TUFF, 0.6, crack=0.14, noise=0.2),
        "oxcu": mat(OXCU, 0.62, crack=0.1, noise=0.14),
        "basalt": mat(BASALT, 0.55, crack=0.1, noise=0.16),
        "soot": mat(SOOT, 0.5, crack=0.0, noise=0.1),
        "flesh": mat(FLESH, 0.6, crack=0.0, noise=0.14),
        "red": mat(RED, 0.7, crack=0.0, noise=0.08, min=2),
        "ember": mat(EMBER, 0.7, crack=0.0, noise=0.08, min=2),
        "bolt": mat(LIGHT_, 0.7, crack=0.0, noise=0.06, min=2),
    }
    if extra:
        m.update(extra)
    return {"mats": m, "glow_mats": list(glow)}


LIGHT_ = [(96, 130, 180), (160, 192, 232), (210, 228, 250), (246, 252, 255)]


# ---- 形の部品
def loop(bone, mat_, cx, cy, cz, rx, rz, r=0.5, n=8, sides=4, tilt=0.0, gap=24.0, at=180.0):
    """y 軸まわりの輪の管 (肋・縄・環)。角度 at (0 = 前、180 = 後ろ) に gap 度の切れ目を開けて、両端を塞ぐ (水密)。
    tilt は x 方向への傾き。"""
    pts = []
    for k in range(n + 1):
        a = math.radians(at - 180.0 + gap / 2 + (360.0 - gap) * k / n)
        pts.append(V_(cx + rx * math.sin(a), cy + tilt * math.sin(a), cz - rz * math.cos(a)))
    return tube(bone, mat_, pts, ra=r, sides=sides, hint=(0, 1, 0), caps=(True, True))


def hoop(bone, mat_, cx, cy, cz, rx, ry, r=0.5, n=8, sides=4, gap=24.0, at=0.0):
    """z 軸に垂直な縦の輪の管 (肋の輪・ふいご)。角度 at (0 = 上、180 = 下) に切れ目。"""
    pts = []
    for k in range(n + 1):
        a = math.radians(at + 180.0 + gap / 2 + (360.0 - gap) * k / n)
        pts.append(V_(cx + rx * math.sin(a), cy - ry * math.cos(a), cz))
    return tube(bone, mat_, pts, ra=r, sides=sides, hint=(0, 0, 1), caps=(True, True))


def arc(bone, mat_, p0, p1, p2, r=0.5, n=6, sides=4, taper=None):
    """3 点を通る曲がった管 (二次の曲線)。taper=(r0, r1) で太さを変える。"""
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    pts = [(1 - t) ** 2 * p0 + 2 * t * (1 - t) * p1 + t * t * p2 for t in [i / n for i in range(n + 1)]]
    ra = r if taper is None else [taper[0] + (taper[1] - taper[0]) * i / n for i in range(n + 1)]
    return tube(bone, mat_, pts, ra=ra, sides=sides, caps=(True, True))


def rod(bone, mat_, p0, p1, r=0.5, sides=5, r1=None):
    r1 = r if r1 is None else r1
    return tube(bone, mat_, [p0, p1], ra=[r, r1], sides=sides, caps=(True, True))


def ball(bone, mat_, c, r, segs=8, rings=3):
    return ellipsoid(bone, mat_, c, (r, r, r), segs=segs, rings=rings)


def ell(bone, mat_, cx, cy, cz, rx, ry, rz, segs=8, rings=3):
    return ellipsoid(bone, mat_, (cx, cy, cz), (rx, ry, rz), segs=segs, rings=rings)


def mirror_x(f):
    return f


# ---- 比べシート
def _wrap(d, text, font, width):
    lines, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=font) > width and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def sheet(spec, results, bones, rows, out_png):
    F = CA._ck.voxel.font
    n = len(results)
    PW = spec.get("pw", 380)
    GAP, X0 = 14, 20
    W = X0 * 2 + n * PW + (n - 1) * GAP
    f_t, f_n, f_d, f_r = F(34), F(28), F(21), F(19)
    probe = ImageDraw.Draw(Image.new("RGB", (4, 4)))
    # 見出し (被写体名と一行の説明)
    sub_lines = []
    for s in spec.get("sub", []):
        sub_lines += _wrap(probe, s, f_d, W - 48)
    cap_lines = [_wrap(probe, spec["options"][k]["desc"], f_d, PW - 8) for k in "abc"[:n]]
    cap_h = 40 + 26 * max(len(c) for c in cap_lines) + 12
    top = 24 + 46 + 26 * len(sub_lines) + 12
    total = top + cap_h + sum(34 + r[4] + 8 for r in rows) + 30
    cv = Image.new("RGB", (W, total), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 18), spec["title"], font=f_t, fill=(236, 232, 218))
    y = 24 + 46
    for line in sub_lines:
        d.text((24, y), line, font=f_d, fill=(176, 176, 192))
        y += 26
    y = top
    for i in range(n):
        x = X0 + i * (PW + GAP)
        d.text((x + 2, y), spec["options"]["abc"[i]]["name"], font=f_n, fill=(150, 232, 255))
        yy = y + 40
        for line in cap_lines[i]:
            d.text((x + 4, yy), line, font=f_d, fill=(226, 224, 214))
            yy += 26
    y += cap_h
    for head, pose, yaw, pitch, ph, cen, sc, only in rows:
        d.text((X0, y + 2), head, font=f_r, fill=(150, 232, 255))
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            rr = dict(r)
            if only is not None:
                rr["faces"] = [f for f in r["faces"] if getattr(f, "part", f.bone) in only]
            im = CA._ck.render_view(rr, r["atlas"], r["glow"], spec["poses"][pose], bones, yaw, pitch, (PW, ph), cen, sc)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
        y += ph + 12
    cv = cv.crop((0, 0, W, y + 6))
    cv.save(out_png)


CA.sheet = sheet


def go(spec, build, pal, bones, rows, hint=128, pupil=None):
    import numpy as _np
    if pupil is not None:
        CA._ck.ra.PUPIL = _np.array(pupil)
    spec["rows"] = rows
    return CA.run(spec, build, pal, bones, rows, hint=hint)


def std_rows(poses, cen, sc, ph=400, extra=None):
    """前 / 3/4 / 横 の 3 行 (+ 動きの行)。poses = 動きの行の (見出し, 姿勢名) の列。"""
    r = [("前", "idle", 180, 8, ph, cen, sc, None),
         ("3/4", "idle", 212, 14, ph, cen, sc, None),
         ("横", "idle", 270, 6, ph, cen, sc, None)]
    for h, p in (poses or []):
        r.append((h, p, 212, 10, ph, cen, sc, None))
    return r
