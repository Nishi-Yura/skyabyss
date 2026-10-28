"""六章 (月) の人の体 3 案ずつ。いまは区画 1 の「前のループの自分 (一人目)」(docs/design/46 の 4.3)。
名は moon_<人>_<a|b|c>。選んだ案は cast の moon_<人> として足してから build / install する。

前のループの自分: 今の主人公と同じ「初めの旅装」(生成りの麻の上着・帯・布の頭巾・脛の巻き布) が月の砂で色あせた姿。
顔は見せきらない (プレイヤーの肌と似せない)。谷で何度も止まり、ここで待っていた。手には星の光の灯り。

  python -I tools/rough/lowpoly/npc2/cast_moon.py [人 ...]   -> docs/review/npc_models_v2/roughs/moon_<人>_<案>.png と moon_<人>.png (3 案を並べた絵)
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cast as C  # noqa: E402
from cast import HOLD_L_LOW, STAFF_R, add  # noqa: E402
import kit as K  # noqa: E402
import props as PR  # noqa: E402
from kit import V, part, rod, box, disc  # noqa: E402

SHORT = dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[(-1.2, 27.8, 1.4, 0.7, -0.3), (1.0, 27.8, 1.4, 0.7, 0.3)])

# 月の砂で色あせた初めの旅装 (生成りの麻 → 青みの灰)。三案で同じ色、形で分ける
FADED = dict(coat=(158, 156, 150), shirt=(186, 184, 176), mantle=(118, 122, 134), trim=(78, 80, 90), hat=(132, 134, 144), hat2=(92, 96, 110),
             trousers=(112, 112, 116), boot=(84, 80, 76), sash=(96, 102, 124), cord=(150, 146, 132), leather=(96, 88, 80), metal=(170, 178, 192),
             metal2=(120, 126, 140))
STAR_LAMP = {
    "lglass": dict(ramp=[(60, 90, 140), (110, 150, 210), (170, 206, 250), (230, 244, 255)], base=0.62, lit=0.3, noise=0.04, crack=0, min=1),
    "flame": dict(ramp=[(120, 170, 240), (180, 214, 255), (226, 240, 255), (255, 255, 255)], base=0.7, lit=0.2, noise=0.05, crack=0, min=1),
    "star": dict(ramp=[(120, 170, 240), (180, 214, 255), (226, 240, 255), (255, 255, 255)], base=0.7, lit=0.2, noise=0.04, crack=0, min=1),
    "dust": dict(ramp=[(112, 118, 130), (152, 158, 168), (192, 196, 204), (226, 228, 232)], base=0.62, lit=0.3, noise=0.14, crack=0, min=0),
}
MOON = {}


def star_lantern_l(c):
    """左手に提げる角灯。火の代わりに星の光"""
    h = PR.hand(c, 1)
    return PR.lantern(c, PR.V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.3, bone="prop_l", hook=PR.V(0, h[1] + 0.2, h[2] - 0.4))


def star_staff(c):
    """右手の旅の杖: 素木の杖の先に、布で巻いて結んだ星の欠片"""
    h = PR.hand(c, -1)
    F = part("ss_pole", rod("prop_r", "wood", h + V(0, -11.4, -0.1), h + V(0, 10.6, 0.1), 0.4, 0.34, sides=6))
    top = h + V(0, 10.6, 0.1)
    F += part("ss_wrap", disc("prop_r", "cord", top + V(0, -0.6, 0), 0.6, 1.2, n=6))
    F += part("ss_star", K.ellipsoid("prop_r", "star", (top[0], top[1] + 1.4, top[2]), (0.9, 1.3, 0.9), segs=6, rings=3))
    for i, (dx, dz) in enumerate(((0.7, 0.0), (-0.7, 0.0), (0.0, 0.7))):
        F += part(f"ss_tie{i}", rod("prop_r", "cord", top + V(dx * 0.6, 0.0, dz * 0.6), top + V(dx * 1.1, -2.4, dz * 1.1), 0.12, sides=3))
    F += part("ss_foot", disc("prop_r", "metal2", h + V(0, -11.0, -0.1), 0.5, 0.4, n=6))
    return F


def pack_dusty(c):
    """背の荷: 丸めた寝具と小さな背負い袋。上に月の砂が積もる"""
    F = part("pk_bag", box("body", "leather", V(0, 15.4, 3.6), (3.4, 4.0, 1.6)))
    F += part("pk_flap", box("body", "leather2", V(0, 18.6, 3.9), (3.5, 1.0, 1.5)))
    F += part("pk_roll", K.tube("body", "mantle", [V(-4.0, 20.2, 3.8), V(4.0, 20.2, 3.8)], ra=1.3, sides=8, caps=(True, True)))
    for x in (-2.6, 2.6):
        F += part(f"pk_band{x}", K.tube("body", "cord", [V(x, 20.2, 2.4), V(x, 21.6, 3.8), V(x, 20.2, 5.2)], ra=0.2, sides=4, caps=(True, True)))
    F += part("pk_dust", K.ellipsoid("body", "dust", (0, 21.4, 3.8), (3.6, 0.45, 1.0), segs=8, rings=2))
    for s in (-1, 1):
        F += part(f"pk_strap{s}", K.tube("body", "cord", [V(s * 2.0, 21.8, 2.2), V(s * 2.4, 21.6, -1.4), V(s * 2.6, 16.0, -2.4)], ra=0.22, sides=4,
                                         caps=(True, True), hint=(1, 0, 0)))
    return F


def moon(pid, ja, opts):
    MOON[pid] = ja
    for k, kw in zip("abc", opts):
        kw = dict(kw)
        kw["ja"] = f"{ja} 案 {k.upper()}"
        add(f"moon_{pid}_{k}", **kw)


def pal(**kw):
    p = dict(FADED)
    p.update(kw)
    # 色あせた布は斑を抑える (灰の布に斑が強いと迷彩に見える)
    soft = {m: K.cloth(p[k], noise=0.04, streak=0.03, facevar=0.03) for m, k in (("coat", "coat"), ("mantle", "mantle"), ("hat", "hat"),
                                                                                  ("trousers", "trousers"), ("shirt", "shirt"))}
    p["extra_mats"] = {**STAR_LAMP, **soft}
    p["glow_mats"] = ["star"]
    return p


# ---------------------------------------------------------------- 前のループの自分 (一人目)
moon("past1", "前のループの自分 (一人目)", [
    # A 頭巾と口布: 初めの旅装に布の頭巾、口から下を布で覆う。疲れた細い目だけが見える。左手に星の角灯
    dict(anim="hold_l", pal=pal(skin=(196, 170, 150), hair=(70, 70, 78)),
         face=dict(eye="sleepy", iris=(150, 170, 200), brow="worried", mouth="none", mask=(126, 130, 142)), hair=SHORT, hat=dict(kind="hood"),
         hairline=27.4, build="avg", upper=dict(mantle=dict(bottom=15.0, clasp=True), sash=True), lower="trousers", sleeve="rolled",
         arms=HOLD_L_LOW, props={"prop_l": star_lantern_l}),
    # B 影の顔: 頭巾の中が影で、顔の代わりに星の光の目が二つだけ。旅の杖の先に布で結んだ星の欠片
    dict(anim="hold_r", pal=pal(skin=(30, 32, 46), hair=(20, 22, 32)),
         face=dict(eye="patch", brow="none", mouth="none", marks=[(["HH"], 1, 50)]), hair=SHORT, hat=dict(kind="hood"),
         hairline=27.4, build="avg", upper=dict(mantle=dict(bottom=13.0, clasp=True), sash=True), lower="trousers", sleeve="wide",
         arms=STAFF_R, props={"prop_r": star_staff}),
    # C 長く留まった旅人: 裾の長い外套に頭巾と口布、背に寝具と荷 (上に月の砂)。年を経た目。左手に星の角灯
    dict(anim="hold_l", pal=pal(skin=(190, 166, 148), hair=(150, 150, 156), coat=(132, 134, 140)),
         face=dict(eye="old", iris=(150, 170, 200), brow="flat", mouth="none", mask=(110, 114, 128)), hair=SHORT, hat=dict(kind="hood"),
         hairline=27.4, build="slim", upper=dict(mantle=dict(bottom=12.0, clasp=True), sash=True), lower="coat_long", sleeve="wide",
         arms=HOLD_L_LOW, props={"prop_l": star_lantern_l}, extra_body=[pack_dusty]),
])


def sheet(pid):
    out = Path(__file__).resolve().parents[4] / "docs/review/npc_models_v2/roughs"
    ims = [Image.open(out / f"moon_{pid}_{k}.png").convert("RGB") for k in "abc"]
    W = max(i.width for i in ims)
    H = sum(i.height for i in ims) + 36
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    import voxel
    d.text((10, 6), f"{MOON[pid]} 3 案", font=voxel.font(22), fill=(236, 232, 218))
    y = 36
    for im in ims:
        cv.paste(im, (0, y))
        y += im.height
    cv.save(out / f"moon_{pid}.png")


if __name__ == "__main__":
    import build as B
    ids = sys.argv[1:] or list(MOON)
    B.main([f"moon_{p}_{k}" for p in ids for k in "abc"])
    for p in ids:
        sheet(p)
    print("ok", len(ids))
