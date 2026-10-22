"""乗り物 (docs/design/38_mounts.md) のラフ用の共通部品。abyss のラフ (ab_common / ck_ab、ここに写し) の道具をそのまま使い、
出力先と比べシートの並びだけ替える。
  - 出力: docs/review/roughs_mounts/<id>.png (メッシュ・アトラスは作業用の場所 MOUNT_WORK へ。コミットしない)
  - シート: 上に 3 案の名前と一行の案。下に 前 / 3/4 / 横 と、最後に「乗った人」の行 (鞍の高さの確認)。左の黄色い目盛りが人 (1.8 ブロック)。
  - 人 (pl_* の部品) は乗った人の行にだけ出る。座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
"""
from __future__ import annotations

import math
import os
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ab_common as AC  # noqa: E402
from ab_common import *  # noqa: E402,F401,F403
import ck_ab as CA  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

ROOT = HERE.parents[3]
FINAL = ROOT / "docs/review/roughs_mounts"
WORK = Path(os.environ.get("MOUNT_WORK", tempfile.gettempdir())) / "mount_rough_work"
CA.OUT = WORK
CA._ck.OUT = WORK
AC.OUT2 = WORK

HIDE = [(40, 28, 22), (76, 54, 40), (112, 82, 60), (150, 114, 86)]
HIDED = [(24, 18, 16), (48, 36, 30), (74, 58, 46), (100, 80, 62)]
ASHF = [(44, 44, 48), (84, 84, 88), (124, 124, 126), (166, 164, 160)]
CREAM = [(110, 104, 96), (160, 154, 142), (200, 194, 180), (232, 226, 212)]
LIZ = [(24, 40, 26), (44, 72, 44), (76, 110, 62), (120, 148, 86)]
LIZB = [(70, 60, 36), (110, 96, 56), (150, 134, 84), (190, 174, 118)]
STONEW = [(52, 56, 62), (92, 98, 106), (134, 140, 146), (176, 180, 182)]
BARN = [(150, 144, 124), (192, 186, 164), (222, 216, 194), (240, 236, 220)]
CORAL = [(150, 50, 60), (204, 84, 88), (236, 128, 118), (250, 176, 150)]
CORALB = [(40, 110, 120), (60, 150, 150), (110, 196, 184), (170, 226, 210)]
SHELLC = [(36, 58, 60), (62, 92, 90), (98, 128, 118), (150, 170, 150)]
SKIN = [(60, 90, 70), (96, 128, 92), (130, 160, 112), (170, 190, 140)]
FEATH = [(150, 120, 30), (214, 176, 52), (244, 216, 100), (255, 244, 170)]
STORMB = [(40, 56, 96), (70, 96, 150), (120, 150, 200), (190, 210, 240)]
FOXC = [(120, 40, 14), (176, 70, 20), (220, 110, 40), (244, 160, 70)]
CINDER = [(14, 12, 12), (30, 26, 26), (52, 44, 42), (80, 70, 64)]
LEATH = [(50, 30, 18), (86, 54, 30), (120, 80, 46), (156, 112, 70)]
CLOTHR = [(70, 28, 28), (120, 46, 44), (164, 72, 62)]
PLC = [(52, 62, 84), (88, 102, 130), (124, 140, 168)]
PLS = [(150, 110, 84), (196, 150, 116), (226, 184, 150)]
BRASS = [(90, 66, 24), (150, 112, 40), (204, 160, 70), (240, 206, 118)]


def PALM(extra=None, glow=("ember", "bolt")):
    m = {"hide": mat(HIDE, 0.62, crack=0.0, noise=0.2), "hided": mat(HIDED, 0.55, crack=0.0, noise=0.2),
         "ashf": mat(ASHF, 0.6, crack=0.0, noise=0.2), "cream": mat(CREAM, 0.62, crack=0.0, noise=0.16),
         "liz": mat(LIZ, 0.6, crack=0.0, noise=0.2), "lizb": mat(LIZB, 0.62, crack=0.0, noise=0.18),
         "stonew": mat(STONEW, 0.6, crack=0.12, noise=0.2), "barn": mat(BARN, 0.64, crack=0.1, noise=0.2),
         "coral": mat(CORAL, 0.66, crack=0.0, noise=0.14), "coralb": mat(CORALB, 0.66, crack=0.0, noise=0.14),
         "shellc": mat(SHELLC, 0.6, crack=0.1, noise=0.18), "skin": mat(SKIN, 0.6, crack=0.0, noise=0.16),
         "feath": mat(FEATH, 0.66, crack=0.0, noise=0.14), "stormb": mat(STORMB, 0.62, crack=0.0, noise=0.16),
         "fox": mat(FOXC, 0.64, crack=0.0, noise=0.16), "cinder": mat(CINDER, 0.55, crack=0.1, noise=0.2),
         "leath": mat(LEATH, 0.6, crack=0.0, noise=0.18), "clothr": mat(CLOTHR, 0.6, crack=0.0, noise=0.12),
         "brass": mat(BRASS, 0.7, crack=0.0, noise=0.1),
         "plc": mat(PLC, 0.6, crack=0.0, noise=0.05), "pls": mat(PLS, 0.6, crack=0.0, noise=0.05),
         "ember": mat(EMBER, 0.7, crack=0.0, noise=0.08, min=2),
         "bolt": mat(AC.LIGHT_, 0.7, crack=0.0, noise=0.06, min=2)}
    if extra:
        m.update(extra)
    return {"mats": m, "glow_mats": list(glow)}


BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0))}
B = "body"
POSES = {"idle": {}}


class _Exempt(list):
    def __contains__(self, k):
        return str(k).startswith("pl_")


# ---- 部品 (すべて閉じた形。part() で名前を付けて返す)
def E(m, c, r, segs=8, rings=3, rot=None):
    return ellipsoid(B, m, c, r, segs=segs, rings=rings, rot=rot)


def ER(m, c, r, rx=0.0, ry=0.0, rz=0.0, segs=8, rings=3):
    """回した楕円体 (x → y → z の順に回す)。"""
    return ellipsoid(B, m, c, r, segs=segs, rings=rings, rot=rot_y(ry) @ rot_x(rx) @ rot_z(rz))


def T(m, pts, ra, sides=5, caps=(True, True), hint=(0, 1, 0), rb=None):
    return tube(B, m, pts, ra=ra, rb=rb, sides=sides, caps=caps, hint=hint)


def Ar(m, p0, p1, p2, taper, n=6, sides=5):
    return arc(B, m, p0, p1, p2, taper=taper, n=n, sides=sides)


def Ar4(m, pts, taper, sides=5):
    """点列を通る曲がった管 (taper = (根元の半径, 先の半径))。"""
    n = len(pts) - 1
    ra = [taper[0] + (taper[1] - taper[0]) * i / n for i in range(n + 1)]
    return tube(B, m, pts, ra=ra, sides=sides, caps=(True, True))


def C(m, base, d, length, r, sides=4, rings=2, bend=(0, 0, 0)):
    return cone(B, m, base, d, length, r, sides=sides, rings=rings, bend=bend)


def Bl(m, c, r, segs=8, rings=3):
    return ball(B, m, c, r, segs=segs, rings=rings)


def leg(name, m, hip, knee, ankle, toe, r0, r1, r2, foot_m=None, foot=(3.4, 1.6, 4.6)):
    """二節の脚: 付け根 hip (体に沈める) - knee - ankle - 足先。足は楕円体、関節は玉で埋める。"""
    F = []
    F += part(name + "_up", T(m, [hip, knee], [r0, r1], sides=6))
    F += part(name + "_kn", Bl(m, knee, r1 * 1.12, segs=7, rings=2))
    F += part(name + "_lo", T(m, [knee, ankle, (ankle[0], foot[1] * 0.9, ankle[2])], [r1, r2, r2 * 0.95], sides=6))
    fx, fy, fz = foot
    F += part(name + "_ft", E(foot_m or m, (toe[0], fy * 0.85, (toe[2] + ankle[2]) * 0.5), (fx, fy, fz), segs=7, rings=2))
    return F


def pillar(name, m, x, y_top, z, r0, r1, foot_m=None, foot=(3.6, 1.6, 4.2)):
    """まっすぐな太い脚 (柱)。"""
    F = []
    F += part(name + "_up", T(m, [(x, y_top, z), (x, 2.0, z)], [r0, r1], sides=6))
    F += part(name + "_ft", E(foot_m or m, (x, foot[1] * 0.85, z - 0.4), foot, segs=7, rings=2))
    return F


def loop_y(m, cy, rx, ry, cz, r=0.8, n=12):
    """腹帯: 体の輪切り (x-y 平面の楕円) に沿った輪。背の上だけ開ける。"""
    pts = []
    for k in range(n + 1):
        a = math.radians(24 + 312.0 * k / n)
        pts.append(V_(rx * math.sin(a), cy + ry * math.cos(a), cz))
    return tube(B, m, pts, ra=r, sides=4, hint=(0, 0, 1), caps=(True, True))


def saddle(seat, w=6.5, ln=8.0, m="leath", pad="clothr", girth=None, stir=True, drop=14.0, pommel=True, col_m="brass"):
    """鞍。seat = (x, y, z) は座面の中心 (座面の板は背に半分沈む)。前後の山・腹帯 (girth = (cy, rx, ry, cz))・あぶみ。"""
    sx, sy, sz = seat
    F = []
    F += part("sd_pad", E(pad, (sx, sy - 1.4, sz), (w + 1.5, 2.2, ln + 1.5), segs=8, rings=2))
    F += part("sd_seat", E(m, (sx, sy - 0.2, sz), (w, 1.6, ln), segs=8, rings=2))
    if pommel:
        F += part("sd_front", E(m, (sx, sy + 1.4, sz - ln + 0.8), (w * 0.55, 2.4, 1.6), segs=7, rings=2))
        F += part("sd_back", E(m, (sx, sy + 2.2, sz + ln - 0.8), (w * 0.8, 3.2, 1.6), segs=7, rings=2))
    if girth:
        cy, rx, ry, cz = girth
        F += part("sd_girth", loop_y(m, cy, rx, ry, cz))
    if stir:
        for s in (-1, 1):
            F += part(f"sd_strap{s}", T(m, [(s * (w + 0.4), sy - 1.0, sz), (s * (w + 2.4), sy - drop, sz)], [0.8, 0.8], sides=4))
            F += part(f"sd_stir{s}", E(col_m, (s * (w + 2.6), sy - drop, sz - 0.4), (1.6, 0.9, 2.6), segs=6, rings=2))
    return F


def rider(seat, lean=0.0):
    """乗った人 (四角い人型。座って頭の上まで 20 px)。pl_* の部品。seat = 座面の中心。"""
    sx, sy, sz = seat
    F = []
    F += part("pl_torso", tube(B, "plc", [(sx, sy + 0.5, sz), (sx, sy + 12, sz - lean)], ra=[5.66, 5.66], rb=[2.83, 2.83],
                               sides=4, caps=(True, True), hint=(1, 0, 0)))
    F += part("pl_head", T("pls", [(sx, sy + 12, sz - lean), (sx, sy + 20, sz - lean)], [5.66, 5.66], sides=4, hint=(1, 0, 0)))
    F += part("pl_hair", T("hided", [(sx, sy + 18, sz - lean), (sx, sy + 20.6, sz - lean)], [5.9, 5.9], sides=4, hint=(1, 0, 0)))
    for s in (-1, 1):
        F += part(f"pl_thigh{s}", T("plc", [(sx + s * 2, sy + 1.5, sz), (sx + s * 4.5, sy - 4.0, sz - 6.5)], [2.9, 2.9], sides=4, hint=(1, 0, 0)))
        F += part(f"pl_shin{s}", T("plc", [(sx + s * 4.5, sy - 4.0, sz - 6.5), (sx + s * 5.5, sy - 13.0, sz - 4.0)], [2.9, 2.9], sides=4, hint=(1, 0, 0)))
        F += part(f"pl_arm{s}", T("pls", [(sx + s * 5.2, sy + 10.5, sz - lean), (sx + s * 4.2, sy + 4.0, sz - 9.0 - lean)], [2.9, 2.9], sides=4, hint=(1, 0, 0)))
    return F


def finish(F, seat, girth=None, lean=0.0, **skw):
    """鞍と乗った人を足す。"""
    F += saddle(seat, girth=girth, **skw)
    F += rider(seat, lean)
    return F


# ---- 比べシート
def sheet(spec, results, bones, rows, out_png):
    F = CA._ck.voxel.font
    n = len(results)
    PW = spec.get("pw", 380)
    GAP, X0 = 14, 20
    W = X0 * 2 + n * PW + (n - 1) * GAP
    f_t, f_n, f_d, f_r = F(34), F(28), F(21), F(19)
    probe = ImageDraw.Draw(Image.new("RGB", (4, 4)))
    sub_lines = []
    for s in spec.get("sub", []):
        sub_lines += AC._wrap(probe, s, f_d, W - 48)
    cap_lines = [AC._wrap(probe, spec["options"][k]["desc"], f_d, PW - 8) for k in "abc"[:n]]
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
            if only != "rider":
                rr["faces"] = [f for f in r["faces"] if not str(getattr(f, "part", f.bone)).startswith("pl_")]
            im = CA._ck.render_view(rr, r["atlas"], r["glow"], spec["poses"][pose], bones, yaw, pitch, (PW, ph), cen, sc)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
        y += ph + 12
    cv = cv.crop((0, 0, W, y + 6))
    FINAL.mkdir(parents=True, exist_ok=True)
    cv.save(FINAL / f"{spec['id']}.png")


CA.sheet = sheet
AC.sheet = sheet


def rows_for(cen, sc, ph=330):
    """前 / 3/4 / 横 / 乗った人 (3/4)。cen = 中心 (x, y, z)、sc = 縮尺。"""
    return [("前", "idle", 180, 8, ph, cen, sc, None),
            ("3/4", "idle", 212, 14, ph, cen, sc, None),
            ("横", "idle", 270, 6, ph, cen, sc, None),
            ("乗った人 (3/4)  座面の高さと人の大きさの確認", "idle", 232, 10, ph + 40, cen, sc, "rider")]


def run(spec, build, pal, hint=128, pupil=None):
    spec["exempt"] = _Exempt()
    spec["poses"] = POSES
    spec.setdefault("budget", "乗り物 600〜1200")
    spec.setdefault("extra", "")
    spec.setdefault("kind", "乗り物 3案")
    spec.setdefault("short", spec["title"])
    return AC.go(spec, build, pal, BONES, spec["rows"], hint=hint, pupil=pupil)


def gq(cy, rx, ry, rz, cz, zz, k=0.97):
    """腹帯の輪の寸法: 楕円体 (中心 cy、半径 rx, ry, rz、中心の z = cz) の z = zz での輪切りに沿わせる。"""
    q = max(0.05, 1 - ((zz - cz) / rz) ** 2) ** 0.5
    return (cy, rx * q * k, ry * q * k, zz)


def coral(name, m, x, y, z, h, tip_m=None, lean=(0.0, 0.0), n=3, r=1.6):
    """枝珊瑚: 太い幹と n 本の枝。先に小さな玉。根元 (x, y, z) は殻に沈める。"""
    F = []
    lx, lz = lean
    top = (x + lx, y + h * 0.55, z + lz)
    F += part(name + "_t", T(m, [(x, y - 1.0, z), top], [r * 1.3, r], sides=5))
    for i in range(n):
        a_ = math.radians(i * 360.0 / n + 25)
        tip = (x + lx + math.sin(a_) * h * 0.38, y + h, z + lz + math.cos(a_) * h * 0.38)
        F += part(f"{name}_b{i}", Ar4(m, [top, (top[0] + (tip[0] - top[0]) * 0.5, top[1] + h * 0.18, top[2] + (tip[2] - top[2]) * 0.5), tip], (r, r * 0.7), sides=5))
        F += part(f"{name}_k{i}", Bl(tip_m or m, tip, r * 1.05, segs=6, rings=2))
    return F
