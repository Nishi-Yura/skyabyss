# -*- coding: utf-8 -*-
"""あいだに座すもの (tower_master) 低ポリ 一品一案。超大型レイド「あいだの塔」の主 (docs/design/54 の 2.5。手本は Wynncraft の Annihilation)。
橋の先の玉座の段に座ったまま動かない巨人。高さ約 40 ブロック。天 (巡礼者) と淵 (心臓) の引き合いそのもの。
  上半身   白と金の衣、胸に光る核 (打てる所)。背に羽の光輪
  下半身   黒い石の脚と腰、紫に光る割れ目、鎖が下の闇へ垂れる
  右腕     金と白 (天)。左腕 黒い石と鎖 (淵)。両手は橋の北の広場に置かれる (主の前 24・左右 13・下 10 ブロック)
  頭       白い布で顔を覆う。右に金の角、左に黒い角、角のあいだに天秤の印 (光る)
座標: 設計は 1 ブロック = 16 で書き、書き出す前に SCALE (1/5) で縮める (絵の画素が巨大にならないように)。ゲームでは描くときに 5 倍に広げる。
原点 = 玉座に座る所 (印 boss)。正面は -Z (橋の方)。
骨: root > body > head > halo / horn / sigil、body > arm_l > fore_l > hand_l、arm_r > fore_r > hand_r、core、legs。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

STONE = [(22, 20, 28), (38, 36, 46), (58, 56, 68), (84, 82, 96)]
VOIDG = [(120, 70, 210), (160, 110, 240), (200, 160, 255), (235, 215, 255)]
ROBEW = [(176, 172, 160), (208, 204, 192), (230, 228, 218), (246, 244, 236)]

SCALE = 0.2
_BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 60, 0)), "head": ("body", (0, 360, -10)),
         "halo": ("head", (0, 400, 90)), "core": ("body", (0, 250, -96)),
         "arm_l": ("body", (-150, 320, -10)), "fore_l": ("arm_l", (-205, 120, -120)), "hand_l": ("fore_l", (-210, -110, -330)),
         "arm_r": ("body", (150, 320, -10)), "fore_r": ("arm_r", (205, 120, -120)), "hand_r": ("fore_r", (210, -110, -330)),
         "legs": ("root", (0, 0, 0))}
BONES = {k: (v[0], tuple(c * SCALE for c in v[1])) for k, v in _BONES.items()}
PAL = make_pal({
    "stone": (STONE, 0.5, dict(crack=0.10, noise=0.22)),
    "robe": (ROBEW, 0.62, dict(crack=0.03, noise=0.14)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "veil": (PLASTER, 0.6, dict(crack=0.02, noise=0.10)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "void": (VOIDG, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "iron": (IRON, 0.5, dict(crack=0.04, noise=0.14)),
}, glow=["void", "dawn"])


def hoop(bone, mat, y, rx, rz, cx=0.0, zc=0.0, r=4.0, n=16):
    pts = [(cx + math.cos(a) * rx, y, zc + math.sin(a) * rz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 1, 0))


def chain(bone, x, y0, y1, z, step=24.0, r=6.0):
    """縦の鎖 (輪を交互に向けて並べる)"""
    f = []
    k = 0
    y = y0
    while y > y1:
        if k % 2 == 0:
            f += part(f"ch{bone}{x:.0f}{z:.0f}{k}", hoop(bone, "iron", y, r * 1.6, r * 0.6, cx=x, zc=z, r=r * 0.35, n=10))
        else:
            pts = [(x + math.cos(a) * r * 0.6, y + math.sin(a) * r * 1.6, z) for a in [i / 10 * 2 * math.pi for i in range(11)]]
            f += part(f"ch{bone}{x:.0f}{z:.0f}{k}", tube(bone, "iron", pts, ra=r * 0.35, sides=4, hint=(0, 0, 1)))
        y -= step
        k += 1
    return f


def build(k):
    F = []
    # ---- 下半身 (黒い石): 腰と、組んで座る脚 (膝は前へ、脛は段の下へ)
    hips = [ring(-30, 150, -110, 110, n=16), ring(20, 160, -120, 115, n=16), ring(70, 140, -100, 100, n=16)]
    F += part("hips", loft("legs", "stone", hips, caps=("stone", "stone")))
    for s in (-1, 1):
        F += part(f"thigh{s}", tube("legs", "stone", [(s * 95, 30, -40), (s * 115, 60, -150), (s * 120, 55, -205)], ra=[70, 64, 58], sides=8, hint=(1, 0, 0)))
        F += part(f"knee{s}", ellipsoid("legs", "stone", (s * 120, 58, -210), (62, 60, 58), segs=10, rings=5))
        F += part(f"shin{s}", tube("legs", "stone", [(s * 120, 40, -230), (s * 118, -60, -245), (s * 112, -150, -250)], ra=[52, 46, 40], sides=8, hint=(1, 0, 0)))
        F += part(f"foot{s}", ellipsoid("legs", "stone", (s * 112, -160, -275), (44, 22, 60), segs=8, rings=3))
        # 紫の割れ目 (膝から腿へ)
        F += part(f"crack{s}", tube("legs", "void", [(s * 150, 70, -200), (s * 160, 60, -160), (s * 150, 70, -110), (s * 162, 50, -70)],
                                   ra=4.5, sides=4, hint=(0, 1, 0)))
        # 腰から下の闇へ垂れる鎖
        F += chain("legs", s * 130, -40, -420, 20)
        F += chain("legs", s * 60, -40, -380, 90)
    # ---- 上半身 (白と金の衣)
    torso = [ring(60, 150, -100, 100, n=18), ring(150, 140, -96, 96, n=18), ring(240, 160, -98, 100, n=18), ring(310, 170, -92, 96, n=18),
             ring(340, 120, -70, 70, n=18), ring(355, 50, -40, 40, n=18)]
    F += part("torso", loft("body", "robe", torso, caps=("robe", "robe")))
    F += part("belt", hoop("body", "gold", 70, 152, 102, r=8))
    F += part("collar", hoop("body", "gold", 330, 150, 88, r=9))
    F += part("seam", tube("body", "gold", [(0, 75, -101), (0, 160, -97), (0, 230, -99)], ra=6, sides=4, hint=(1, 0, 0)))
    # 左の胸から肩へ、黒い石が衣を食う (淵の側)
    F += part("stonepatch", ellipsoid("body", "stone", (-110, 280, -40), (70, 60, 80), segs=10, rings=5))
    F += part("stonecrack", tube("body", "void", [(-60, 300, -95), (-90, 260, -100), (-70, 220, -102), (-100, 190, -98)], ra=4, sides=4, hint=(0, 0, 1)))
    # 胸の核 (打てる所) と天秤の飾り
    F += part("core", ellipsoid("core", "dawn", (0, 250, -110), (34, 34, 20), segs=12, rings=6))
    F += part("corering", tube("core", "gold", [(math.cos(a) * 46, 250 + math.sin(a) * 46, -108) for a in [i / 16 * 2 * math.pi for i in range(17)]],
                               ra=6, sides=4, hint=(0, 0, 1)))
    F += part("beam", tube("core", "gold", [(-90, 300, -104), (90, 300, -104)], ra=5, sides=4, hint=(0, 1, 0)))
    for s in (-1, 1):
        F += part(f"panline{s}", tube("core", "gold", [(s * 90, 300, -104), (s * 90, 270, -104)], ra=2.5, sides=4, hint=(1, 0, 0)))
        F += part(f"pan{s}", ellipsoid("core", "gold", (s * 90, 266, -104), (22, 5, 22), segs=10, rings=3))
    # ---- 腕 (右 = 金と白、左 = 黒い石と鎖)
    for s, mat, trim in ((1, "robe", "gold"), (-1, "stone", "iron")):
        a, f_, h = ("arm_r", "fore_r", "hand_r") if s > 0 else ("arm_l", "fore_l", "hand_l")
        F += part(f"shoulder{s}", ellipsoid(a, mat, (s * 160, 320, -10), (70, 62, 70), segs=10, rings=5))
        F += part(f"upper{s}", tube(a, mat, [(s * 165, 310, -10), (s * 205, 120, -120)], ra=[58, 46], sides=8, hint=(1, 0, 0)))
        F += part(f"elbow{s}", ellipsoid(f_, mat, (s * 205, 120, -120), (46, 46, 46), segs=8, rings=4))
        F += part(f"fore{s}", tube(f_, mat, [(s * 205, 120, -120), (s * 210, -100, -320)], ra=[44, 38], sides=8, hint=(1, 0, 0)))
        F += part(f"cuff{s}", hoop(f_, trim, -60, 44, 44, cx=s * 209, zc=-280, r=7, n=14))
        # 手 (橋の北の広場に置かれる。掌を下に、指を前へ)
        F += part(f"palm{s}", ellipsoid(h, mat, (s * 210, -140, -380), (62, 26, 70), segs=10, rings=4))
        for i, dx in enumerate((-44, -15, 15, 44)):
            fx = s * 210 + dx
            F += part(f"finger{s}{i}", tube(h, mat, [(fx, -140, -430), (fx, -138, -480), (fx, -150, -515)], ra=[16, 14, 10], sides=6, hint=(1, 0, 0)))
        F += part(f"thumb{s}", tube(h, mat, [(s * 210 - s * 60, -135, -380), (s * 210 - s * 95, -145, -420)], ra=[18, 13], sides=6, hint=(1, 0, 0)))
        if s < 0:
            # 左手首の枷と、地へ垂れる鎖
            F += part("shackle", hoop(f_, "iron", -95, 48, 48, cx=-210, zc=-325, r=10, n=14))
            F += chain(f_, -210, -110, -300, -300, step=22, r=7)
            F += part("armcrack", tube(f_, "void", [(-240, 60, -170), (-250, 0, -230), (-238, -50, -280)], ra=4, sides=4, hint=(0, 1, 0)))
        else:
            F += part("armband", hoop(a, "gold", 230, 60, 60, cx=180, zc=-60, r=7, n=14))
    # ---- 頭: 白い布で覆った顔、金と黒の角、角のあいだの天秤の印
    F += part("neck", tube("head", "robe", [(0, 340, -10), (0, 370, -15)], ra=36, sides=8, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "veil", (0, 410, -20), (56, 64, 58), segs=12, rings=6))
    veil = [ring(450, 60, -62, 40, n=16), ring(410, 64, -70, 46, n=16), ring(370, 60, -66, 44, n=16), ring(340, 46, -56, 30, n=16)]
    F += part("veil", loft("head", "robe", veil, caps=("robe", "robe")))
    F += part("veilband", hoop("head", "gold", 448, 60, 61, zc=-11, r=5))
    for s, mat in ((1, "gold"), (-1, "stone")):
        F += part(f"horn{s}", tube("head", mat, [(s * 50, 450, -10), (s * 95, 500, 0), (s * 110, 560, 20), (s * 95, 610, 45)],
                                   ra=[18, 14, 9, 2], sides=6, hint=(0, 0, 1)))
    F += part("sigil", tube("head", "dawn", [(math.cos(a) * 26, 540 + math.sin(a) * 26, -20) for a in [i / 14 * 2 * math.pi for i in range(15)]],
                            ra=4, sides=4, hint=(0, 0, 1)))
    F += part("sigilbar", tube("head", "dawn", [(-26, 548, -20), (26, 532, -20)], ra=3, sides=4, hint=(0, 0, 1)))
    # ---- 背の羽の光輪 (金の輪と、放射の羽)
    F += part("haloring", tube("halo", "gold", [(math.cos(a) * 220, 410 + math.sin(a) * 220, 110) for a in [i / 36 * 2 * math.pi for i in range(37)]],
                               ra=9, sides=4, hint=(0, 0, 1)))
    for i in range(18):
        a = i / 18 * 2 * math.pi
        cx, cy = math.cos(a) * 220, 410 + math.sin(a) * 220
        ox, oy = math.cos(a) * 300, 410 + math.sin(a) * 300
        F += part(f"feather{i}", tube("halo", "robe" if i % 2 else "gold", [(cx, cy, 112), ((cx + ox) / 2, (cy + oy) / 2, 116), (ox, oy, 120)],
                                      ra=[16, 12, 2], sides=4, hint=(0, 0, 1)))
    # 書き出す大きさへ縮める (骨の支点も SCALE で縮めてある)
    for f in F:
        f.verts = f.verts * SCALE
        f.loc = f.verts
        f.ref = f.ref * SCALE
    return F, []


POSES = {
    "idle": {},
    "rake": {"arm_r": (20, -25, 0), "fore_r": (-30, -20, 0), "body": (0, -12, 0)},
    "clap": {"arm_l": (25, 30, 0), "arm_r": (25, -30, 0), "fore_l": (-20, 25, 0), "fore_r": (-20, -25, 0)},
    "moon": {"arm_l": (-70, 0, -25), "arm_r": (-70, 0, 25), "fore_l": (-40, 0, 0), "fore_r": (-40, 0, 0), "head": (-20, 0, 0)},
}

SPEC = dict(
    id="tower_master", short="あいだに座すもの", kind="超大型レイドの主 一品一案 (54 の 2.5)",
    title="あいだに座すもの (tower_master) — 橋の先に座る、天と淵の引き合いの巨人・低ポリ + ドット絵 一案",
    sub=["上半身は天 (白と金の衣・羽の光輪・金の角)、下半身と左腕は淵 (黒い石・紫の割れ目・鎖)。白い布で顔を覆い、角のあいだに天秤の印。",
         "胸の核が打てる所。両手は橋の北の広場に置かれる。高さ約 40 ブロック、動かない (Wynncraft の Annihilation が手本)。"],
    labels=["あいだに座すもの"],
    rows=[("前 3/4 (橋から見上げる)", "idle", 205, 4, 560, (0, 34, -24), 3.1),
          ("右手の払い", "rake", 230, 4, 560, (0, 34, -24), 3.1),
          ("合掌", "clap", 180, 8, 560, (0, 34, -30), 3.1),
          ("落ちる月を呼ぶ / 後ろ 3/4", "moon", 30, 8, 560, (0, 44, -8), 2.75)],
    options={"a": dict(name="天と淵の巨人", desc="上は白と金と羽の光輪、下と左腕は黒い石と鎖。顔は白い布、胸に光る核と天秤。")},
    extra="骨 12 本。光るのは胸の核・角のあいだの印・紫の割れ目。",
    budget="主 4000〜8000", pw=520,
    later="段が進むと石の割れ目が広がり、光輪が赤くなる (引き合いの高まり)。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
