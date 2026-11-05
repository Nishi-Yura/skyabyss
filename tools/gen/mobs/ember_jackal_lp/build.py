#!/usr/bin/env python3
"""熾の山犬 (灰の荒野の敵。2026-11-05 の頁 s_ember_foe = A。ラフは tools/rough/lowpoly/ember_foe/roughs.py の ember_foe_a) の形と動き。

    python -X utf8 -I tools/gen/mobs/ember_jackal_lp/build.py [--no-install]

痩せた灰色の山犬。肩と背の毛の割れ目、尾の先から熾が透ける (光る)。三、四頭で回り込み、後ろから噛む (Java: entity/foe/EmberJackal)。
長さは 1/16 ブロック、前は -z。部品と書き出しは逃げる獣と同じ (rare_beasts_lp/beasts.py・dungeon_bosses_lp/blp.py)。
骨: body / neck / head / jaw / ear_l・ear_r / tail・tail2 / leg_{fl,fr,bl,br} (+ _lo)。
動きの名前は Java の PackBeast と同じ: idle・walk・bite・howl・hurt・death (run は使わないが作っておく)。
→ assets (lowpoly・geo・animations・textures) と docs/review/ember_jackal/ember_jackal_sheet.png
"""
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "rare_beasts_lp"))
import beasts as B  # noqa: E402
from beasts import *  # noqa: E402,F401,F403
from beasts import keys, eye, leg4, flinch, side_fall, pal_, P  # noqa: E402
import blp  # noqa: E402

blp.REVIEW = blp.ROOT / "docs/review/ember_jackal"

SOOT = [(40, 36, 38), (58, 54, 56), (80, 74, 74), (106, 98, 94), (132, 122, 114)]
ASHP = [(96, 90, 86), (126, 118, 110), (156, 146, 134), (184, 174, 160)]


def ember_jackal():
    m = Mount("ember_jackal", "熾の山犬")
    m.bone("body", "root", (0, 10.0, 0))
    m.bone("neck", "body", (0, 12.4, -5.0))
    m.bone("head", "neck", (0, 14.4, -7.6))
    m.bone("jaw", "head", (0, 13.6, -9.4))
    m.bone("ear_l", "head", (1.2, 16.2, -8.2))
    m.bone("ear_r", "head", (-1.2, 16.2, -8.2))
    m.bone("tail", "body", (0, 11.4, 6.6))
    m.bone("tail2", "tail", (0, 9.8, 9.8))
    # 胴: 胸が深く、腰へ細る。背の線は肩で少し高い
    body = [rsec(-6.4, 13.4, 8.0, 2.6, n=12), rsec(-4.0, 13.8, 6.8, 3.0, n=12), rsec(-1.0, 13.4, 8.2, 2.8, n=12),
            rsec(2.2, 13.0, 9.2, 2.5, n=12), rsec(5.0, 12.8, 9.0, 2.6, n=12), rsec(7.0, 12.2, 9.6, 2.0, n=12)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("chest", ell("body", "ash", (0, 9.8, -5.2), (2.2, 2.6, 2.0), segs=10, rings=3))
    m.add("neck_m", tub("neck", "fur", [P(0, 12.0, -4.6), P(0, 13.4, -6.4), P(0, 14.4, -7.8)], [2.3, 1.9, 1.7], None, sides=8))
    m.add("ruff", ell("neck", "fur", (0, 13.0, -5.4), (2.5, 2.2, 2.0), segs=8, rings=2))
    # 頭: 細長い鼻づら。下あごは jaw (噛むときに開く)
    hd = [rsec(-7.2, 16.2, 13.0, 2.0, n=10), rsec(-8.8, 16.0, 13.2, 2.1, n=10), rsec(-10.4, 15.0, 13.6, 1.3, n=10),
          rsec(-12.2, 14.6, 13.8, 0.9, n=10), rsec(-13.4, 14.4, 13.9, 0.6, n=10)]
    m.add("skull", lof("head", "fur", hd), core=True)
    m.add("nose", ell("head", "dark", (0, 14.3, -13.6), (0.5, 0.4, 0.4), segs=6, rings=1))
    m.add("jaw_m", lof("jaw", "ash", [rsec(-9.0, 13.8, 12.8, 1.5, n=8), rsec(-11.0, 13.6, 12.9, 1.0, n=8), rsec(-12.8, 13.6, 13.1, 0.55, n=8)]))
    for s in (1, -1):
        m.add(f"fang{s}", cone("jaw", "bone", P(s * 0.5, 13.7, -12.2), (0, 1, 0), 0.9, 0.22, sides=4, rings=1))
    for s, k in ((1, "l"), (-1, "r")):
        e = "ear_" + k
        m.add(f"ear{k}", cone(e, "fur", P(s * 1.2, 15.8, -8.2), (s * 0.3, 1, 0.25), 3.0, 0.95, sides=5, rings=2))
        m.add(f"earin{k}", cone(e, "ember", P(s * 1.2, 16.0, -8.55), (s * 0.3, 1, 0.25), 2.2, 0.5, sides=4, rings=1))
        eye(m, "head", (s * 1.35, 15.2, -9.4), 0.42, (s * 0.7, 0.1, -0.7), (255, 150, 60), glow=True)
        # 毛の割れ目: 肩から背へ斜めに走る熾の筋
        m.add(f"seam{s}", box("body", "ember", (s * 2.2, 12.2, -2.4), (0.4, 0.5, 5.0), taper=(1, 0.6), cut=0.1))
        m.add(f"seamb{s}", box("body", "ember", (s * 1.9, 11.4, 3.4), (0.35, 0.45, 3.2), taper=(1, 0.5), cut=0.1))
    m.add("ridge", box("body", "ember", (0, 13.25, -1.0), (0.5, 0.35, 7.0), taper=(1, 0.5), cut=0.1))
    leg4(m, "fur", {"fl": ((1.9, 9.6, -4.6), (2.1, 5.0, -5.0), (2.1, 0.9, -4.8)), "fr": ((-1.9, 9.6, -4.6), (-2.1, 5.0, -5.0), (-2.1, 0.9, -4.8)),
                    "bl": ((1.9, 9.6, 4.8), (2.1, 5.4, 6.2), (2.1, 0.9, 5.4)), "br": ((-1.9, 9.6, 4.8), (-2.1, 5.4, 6.2), (-2.1, 0.9, 5.4))},
         1.25, (0.95, 0.5, 1.5), paw_mat="dark", lo_thin=0.7)
    for s in (1, -1):
        m.add(f"haunch{s}", ell("body", "fur", (s * 1.9, 9.6, 5.0), (1.4, 2.2, 2.4), segs=8, rings=2))
        m.add(f"shoulder{s}", ell("body", "fur", (s * 1.9, 10.0, -4.6), (1.3, 2.2, 2.0), segs=8, rings=2))
    m.add("tail_a", tub("tail", "fur", [P(0, 11.4, 6.4), P(0, 10.8, 8.4), P(0, 9.8, 10.0)], [1.0, 1.15, 1.0], None, sides=7))
    m.add("tail_b", tub("tail2", "fur", [P(0, 9.8, 9.8), P(0, 8.6, 11.4), P(0, 7.6, 12.4)], [1.0, 0.8, 0.5], None, sides=7))
    m.add("tailtip", ell("tail2", "ember", (0, 7.6, 12.5), (0.6, 0.6, 0.8), segs=6, rings=2))
    m.pal = pal_({"fur": M_(SOOT, lit=0.32), "ash": M_(ASHP), "dark": M_(CHAR), "bone": M_(CREAM),
                  "ember": M_(MAG, base=0.72, lit=0.2)}, glow=["ember"])
    m.detail = lambda c, col, glow: (speckle(c, col, ["fur"], (34, 30, 30), density=0.06, size=0.6), glow)
    m.view = dict(center=(0, 9, 0), scale=11.0)

    idle, wk, rn = quad_gaits(walk=(0.6, 20, 26, 20, 28), run=(1.8, 40, 52, 40, 50), run_ph=GALLOP_PH, neck="neck", head="head",
                              tails=("tail", "tail2"), ears=("ear_l", "ear_r"), bob=(0.3, 1.2), pitch=(1.0, 5.0), idle_period=3.6,
                              run_base=(-4.0, 6.0))
    # 待機は頭を低く、尾を下げて (獲物を測る犬の構え)
    idle.rot("neck", x=W(10.0).s(1.6, 1, 30, "t"))
    # 噛む (1.1 秒。Java: 予告 0.4 秒で跳ぶ): 身を沈める → 跳んで口を開く → 噛み合わせる → 戻る
    crouch = {"body": {"pos": (0, -1.4, 1.0), "rot": (-6, 0, 0)}, "neck": {"rot": (14, 0, 0)}, "head": {"rot": (-8, 0, 0)},
              "leg_fl": {"rot": (-24, 0, 0)}, "leg_fr": {"rot": (-24, 0, 0)}, "leg_bl": {"rot": (24, 0, 0)}, "leg_br": {"rot": (24, 0, 0)},
              "tail": {"rot": (-14, 0, 0)}, "ear_l": {"rot": (-30, 0, 0)}, "ear_r": {"rot": (-30, 0, 0)}}
    leap = {"body": {"pos": (0, 1.2, -2.0), "rot": (8, 0, 0)}, "neck": {"rot": (-12, 0, 0)}, "head": {"rot": (-14, 0, 0)}, "jaw": {"rot": (34, 0, 0)},
            "leg_fl": {"rot": (-52, 0, 0)}, "leg_fr": {"rot": (-48, 0, 0)}, "leg_bl": {"rot": (44, 0, 0)}, "leg_br": {"rot": (40, 0, 0)},
            "tail": {"rot": (16, 0, 0)}, "ear_l": {"rot": (-40, 0, 0)}, "ear_r": {"rot": (-40, 0, 0)}}
    snap = dict(leap, jaw={"rot": (0, 0, 0)}, head={"rot": (6, 0, 0)}, body={"pos": (0, 0.2, -1.4), "rot": (2, 0, 0)})
    bite = keys("bite", 1.1, [(0.0, {}), (0.36, crouch), (0.5, leap), (0.62, leap), (0.7, snap), (0.86, snap), (1.1, {})])
    # 遠吠え (1.5 秒。山は 1.0 秒 = Java の howlAt 20 tick): 座って天を仰ぎ、口を開く (x の回りは正で鼻先が下がる)。背の熾が強まる (光は Java の火の粉)
    sit = {"body": {"pos": (0, -0.9, 1.4), "rot": (-22, 0, 0)}, "leg_bl": {"rot": (-48, 0, 0)}, "leg_br": {"rot": (-48, 0, 0)},
           "leg_bl_lo": {"rot": (80, 0, 0)}, "leg_br_lo": {"rot": (80, 0, 0)}, "leg_fl": {"rot": (16, 0, 0)}, "leg_fr": {"rot": (16, 0, 0)}}
    up = dict(sit, neck={"rot": (-30, 0, 0)}, head={"rot": (-20, 0, 0)}, jaw={"rot": (26, 0, 0)}, tail={"rot": (-10, 0, 0)},
              ear_l={"rot": (20, 0, 0)}, ear_r={"rot": (20, 0, 0)})
    howl = keys("howl", 1.5, [(0.0, {}), (0.4, sit), (0.75, up), (1.15, up), (1.5, {})])
    m.gaits = [idle, wk, rn, bite, howl, flinch(amt=(-10, 0, 8)), side_fall(m, drop=-0.6)]
    m.poses = [("bite", 0.36), ("bite", 0.55), ("howl", 1.0), ("death", 1.2)]
    return m


def build(install=True):
    t0 = time.time()
    m = ember_jackal()
    size, atlas, glow = blp.paint_mount(m, m.pal, m.detail, 512)
    tris = sum(len(f.verts) - 2 for f in m.F)
    info = dict(tris=tris, atlas=size, bones=len(m.bones), glow=int(glow.sum()))
    print("ember_jackal", info, f"{time.time() - t0:.1f}s", flush=True)
    if install:
        blp.install(m, "ember_jackal", size, atlas, glow, m.gaits, (3, 3))
    blp.sheet(m, atlas, glow, m.gaits, "ember_jackal", info, m.poses)
    return info


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    build("--no-install" not in sys.argv)
