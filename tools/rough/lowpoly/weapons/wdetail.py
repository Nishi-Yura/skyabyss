"""武器の細かさ 3 段階 (D1 今のまま / D2 整え / D3 細密) のラフ。防具 S2 のマネキンと並べて見る。
実行: python wdetail.py [weapon_id ...]  -> docs/review/weapon_detail/img/"""
from __future__ import annotations

import importlib
import json
import math
import sys

from PIL import Image

HERE = __import__("pathlib").Path(__file__).resolve().parent
sys.path.append(str(HERE.parent / "armor"))
from scene import *  # noqa: F401,F403
import wkit
from wkit import block, prism, curve, extrude, blade, V, rot_x, rot_y, rot_z, BONE
from paint import paint
import mock
from mock import xf
from td_kit import pearl
from st_kit import plume, band as sband
from wh_kit import tooth
from em_kit import ridge
import aview, spaint, sstyle  # armor
from akit import mannequin, pose_faces, PIV
from common import TITANS
aview.apaint = spaint
aview.SS = 2

OUT = ROOT / "docs/review/weapon_detail/img"
BG = (28, 32, 40)
LEVELS = ["D1", "D2", "D3"]
W = [  # id, module, 巨神, 持ち方, 日本語, 型
    ("whalebone_greatsword", "item_whalebone_greatsword", "whale", "whale", "鯨骨の大剣", "大剣"),
    ("tide_shell_trident", "item_tide_shell_trident", "tide", "tide", "潮殻の三叉槍", "槍"),
    ("thunder_wing_bow", "item_thunder_wing_bow", "thunder", "thunder", "雷翼の弓", "弓"),
    ("waterway_fang", "item_waterway_fang", "whale", "fangw", "水路の牙", "短剣"),
    ("ember_war_axe", "item_ember_war_axe", "ember", "ember", "余燼の戦斧", "斧"),
    ("coral_shield", "item_coral_shield", "tide", "shield", "珊瑚の盾", "盾"),
]
mock.POSES["fangw"] = dict(arm=58, pitch=44, roll=90, s3=0.85, s1=0.6)
mock.POSES["shield"] = dict(arm=62, pitch=0, roll=0, s3=1.0, s1=1.0)
CH = 0.38


from wd2 import *  # noqa: E402,F401,F403  (D2 の部品と EX はここへ移した)
import wd2  # noqa: E402


# ------------------------------------------------------------------ 構築
def make(wid, mod, level):
    L = LEVELS.index(level) + 1
    wkit.CHAMFER[0] = CH if L >= 2 else 0.0
    base = getattr(mod.faces, "__wrapped__", mod.faces)   # 本番は D2 済みなので、素の形を取る
    fs = base("rare") if wid != "thunder_wing_bow" else base("rare", 0)
    wkit.CHAMFER[0] = 0.0
    if L >= 2:
        fs = wd2.d2(wid, lambda *a, **k: base(*a, **k), L)("rare") if wid != "thunder_wing_bow" else wd2.d2(wid, base, L)("rare", 0)
    faces, size, P, N, M, Lc = build(fs)
    atlas, glow = paint(faces, size, P, N, M, Lc, mod.TITAN, "rare", 0, mod.inlay, mod.EDGE)
    return dict(faces=faces, atlas=atlas, glow=glow, tris=tri_count(faces), mod=mod)


ARMOR = {}


def armor(T):
    if T not in ARMOR:
        ARMOR[T] = aview.build_set(sstyle.build(T, "rare", "S2"), T, "rare")
    return ARMOR[T]


def bbox(r):
    v = np.concatenate([f.verts for f in r["faces"]])
    return v.min(0), v.max(0)


def put(im):
    b = Image.new("RGBA", im.size, BG + (255,))
    b.alpha_composite(im)
    return b.convert("RGB")


def tile(r, titan, yaw, pitch, size, cen, scale):
    return put(view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, cen, scale, ss=2))


def held(r, wkey, T, size=(300, 380), yaw=205, pitch=-6):
    A = armor(T)
    parts = [(a["faces"], a["atlas"], a["glow"]) for a in A.values()]
    p = mock.POSES[wkey]
    arm = p["arm"]
    hand = V(*PIV["armorRightArm"]) + rot_x(arm) @ V(0, -11.0, 0)
    grip = V(*r["mod"].GRIP)
    if wkey == "shield":
        R = rot_y(180) @ rot_x(-12)
        hand = hand + V(0, 0, -3.5)
    else:
        R = rot_x(-p["pitch"]) @ rot_y(p["roll"])
    wf = xf(r["faces"], R, hand, p["s3"], grip)
    faces, atlas, glow = compose(parts + [(wf, r["atlas"], r["glow"])], mannequin())
    faces = pose_faces(faces, {"armorRightArm": (arm, 0, 0)})
    im = view(faces, atlas, glow, T, yaw, pitch, size, (0, 20, -8), 7.0, ss=2)
    return put(im).transpose(Image.FLIP_LEFT_RIGHT)


def run(ids):
    OUT.mkdir(parents=True, exist_ok=True)
    sp = OUT / "stats.json"
    stats = json.loads(sp.read_text(encoding="utf-8")) if sp.exists() else {}
    for wid, modn, T, wkey, jp, kind in W:
        if ids and wid not in ids:
            continue
        mod = importlib.import_module(modn)
        res = {lv: make(wid, mod, lv) for lv in LEVELS}
        lo, hi = bbox(res["D3"])
        lo1, hi1 = bbox(res["D1"])
        lo, hi = np.minimum(lo, lo1), np.maximum(hi, hi1)
        cen = tuple((lo + hi) / 2)
        TW, TH = 200, 280
        sc = min(TW * 0.82 / max(hi[0] - lo[0], 1), TH * 0.86 / max(hi[1] - lo[1], 1))
        sc = min(sc, 14.0)
        sheet = Image.new("RGB", (3 * 220 + 228, 3 * 290), BG)
        for i, lv in enumerate(LEVELS):
            r = res[lv]
            strip = Image.new("RGB", (12 * 150, 210))
            for k in range(12):
                strip.paste(tile(r, T, 180 + 30 * k, -8, (150, 210), cen, sc * 0.72), (k * 150, 0))
            strip.save(OUT / f"turn_{wid}_{lv}.png", optimize=True)
            x = 0
            for yaw, pitch, w in ((180, 0, 220), (215, -10, 220), (140, -10, 220)):
                sheet.paste(tile(r, T, yaw, pitch, (w, 290), cen, sc * 0.95), (x, i * 290))
                x += w
            hd = held(r, wkey, T)
            sheet.paste(hd.resize((228, 290)), (x, i * 290))
            hd.save(OUT / f"held_{wid}_{lv}.png", optimize=True)
            stats.setdefault(wid, {})[lv] = r["tris"]
            print(wid, lv, r["tris"], flush=True)
        sheet.save(OUT / f"sheet_{wid}.png", optimize=True)
        sp.write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
        for lv in LEVELS:
            tile(res[lv], T, 200, -6, (200, 280), cen, sc).save(OUT / f"one_{wid}_{lv}.png", optimize=True)


def family():
    for lv in LEVELS:
        im = Image.new("RGB", (6 * 200, 280), BG)
        for i, (wid, *_r) in enumerate(W):
            im.paste(Image.open(OUT / f"one_{wid}_{lv}.png"), (i * 200, 0))
        im.save(OUT / f"family_{lv}.png", optimize=True)


if __name__ == "__main__":
    a = sys.argv[1:]
    run([x for x in a if x != "family"])
    if not a or "family" in a:
        family()
