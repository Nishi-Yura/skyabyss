"""土地の町の 8 人の体 3 案ずつ (2026-10-28 のクオリティ点検。都の町の人の体を借りていて、同じ顔の人が別の町にいた)。
名は guest_<人>_<a|b|c>。選んだ案は cast の guest_<人> として足してから build / install する。

  python -I tools/rough/lowpoly/npc2/cast_folk.py [人 ...]   -> docs/review/npc_models_v2/roughs/guest_<人>_<案>.png と folk_<人>.png (3 案を並べた絵)
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cast as C  # noqa: E402
from cast import SKIN, HOLD_L, HOLD_L_LOW, STAFF_R, HOLD2_LOW, add  # noqa: E402
import props as PR  # noqa: E402
import props2 as P2  # noqa: E402

SHORT = dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[(-1.2, 27.8, 1.4, 0.7, -0.3), (1.0, 27.8, 1.4, 0.7, 0.3)])
BALDISH = dict(style="short", front=27.0, side=24.4, back=23.4, fringe=[])
LONG = dict(style="long", front=27.6, side=24.4, back=23.2, fringe=[(-2.2, 27.8, 2.4, 0.8, -0.5), (2.0, 27.8, 2.2, 0.8, 0.5)])
BUN = dict(style="bun", front=27.6, side=24.6, back=23.6, fringe=[(-2.0, 27.9, 1.6, 0.7, -0.5)])
BRAID = dict(style="braid", front=27.8, side=24.6, back=23.6, fringe=[(-1.6, 28.0, 1.6, 0.7, -0.4), (1.2, 28.0, 1.5, 0.65, 0.4)])

FOLK = {}


def lantern_l(c):
    """左手に提げる角灯 (props.mio_lantern の左手の形)"""
    h = PR.hand(c, 1)
    return PR.lantern(c, PR.V(0.0, h[1] - 3.2, h[2] - 0.4), s=1.3, bone="prop_l", hook=PR.V(0, h[1] + 0.2, h[2] - 0.4))


def folk(pid, ja, opts):
    FOLK[pid] = ja
    for k, kw in zip("abc", opts):
        kw = dict(kw)
        kw["ja"] = f"{ja} 案 {k.upper()}"
        add(f"guest_{pid}_{k}", **kw)


# ---------------------------------------------------------------- はじまりの島の渡し守 (年を取った、無口な舟の人)
folk("island_ferryman", "島の渡し守", [
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(150, 140, 130), coat=(96, 80, 60), mantle=(140, 120, 84), shirt=(200, 190, 160), trim=(60, 48, 36),
                               hat=(186, 160, 100), trousers=(70, 60, 48), boot=(50, 40, 32), metal=(150, 130, 90)),
         face=dict(eye="narrow", iris=(90, 80, 70), brow="thick", mouth="neutral", marks=[(["w"], 4, 48)]), hair=BALDISH, hat=dict(kind="kasa"),
         beard="full", hairline=27.0, build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True)), lower="trousers", sleeve="wide", boots="boot",
         arms=STAFF_R, props={"prop_r": P2.guide_staff}),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(110, 80, 56), coat=(52, 70, 92), vest=(160, 120, 70), shirt=(210, 200, 176), trim=(36, 46, 60),
                                 hat=(52, 70, 92), trousers=(46, 54, 66), boot=(40, 34, 30), metal=(170, 150, 100), scarf=(170, 70, 50)),
         face=dict(eye="kind", iris=(80, 100, 120), brow="flat", mouth="smile"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="stout", upper=dict(vest=True, scarf=True, buttons=[14.0, 16.0]), lower="trousers", sleeve="rolled", props={"prop_r": P2.harpoon}),
    dict(anim="hold_l", pal=dict(skin=SKIN["brown"], hair=(40, 36, 34), coat=(120, 100, 70), apron=(80, 96, 100), shirt=(190, 176, 140), trim=(70, 56, 40),
                                 hat=(80, 96, 100), trousers=(60, 52, 44), boot=(46, 38, 30), metal=(130, 140, 140)),
         face=dict(eye="narrow", iris=(70, 60, 50), brow="stern", mouth="frown"), hair=SHORT, hat=dict(kind="band"), beard="goatee", hairline=27.4,
         build="slim", upper=dict(apron=dict(bottom=5.0, top=17.0, w=3.0), sash=True), lower="trousers", sleeve="rolled", arms=HOLD_L_LOW,
         props={"prop_l": P2.rope_coil}),
])

# ---------------------------------------------------------------- 汐守の渡し守 (港町の陽気な船頭)
folk("tide_ferryman", "汐守の渡し守", [
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(70, 50, 36), coat=(230, 226, 214), vest=(60, 110, 150), shirt=(240, 236, 226), trim=(40, 80, 120),
                                 hat=(240, 236, 226), trousers=(60, 90, 120), boot=(150, 120, 80), metal=(200, 170, 90), scarf=(60, 110, 150)),
         face=dict(eye="glint", iris=(70, 110, 140), brow="arched", mouth="smile"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="avg", upper=dict(vest=True, scarf=True, lapels=True), lower="trousers", sleeve="rolled", props={"prop_r": P2.harpoon}),
    dict(anim="free", pal=dict(skin=SKIN["mid"], hair=(160, 110, 60), coat=(60, 100, 140), mantle=(220, 210, 180), shirt=(230, 224, 210), trim=(30, 60, 90),
                               hat=(220, 210, 180), trousers=(50, 70, 96), boot=(70, 56, 40), metal=(210, 180, 100)),
         face=dict(eye="round", iris=(80, 120, 150), brow="soft", mouth="grin"), hair=LONG, hat=dict(kind="brim"), beard="none", hairline=27.6,
         build="slim", upper=dict(mantle=dict(bottom=17.0, clasp=True), sash=True), lower="coat_long", sleeve="fitted"),
    dict(anim="hold_l", pal=dict(skin=SKIN["ruddy"], hair=(90, 60, 40), coat=(46, 72, 110), apron=(200, 190, 150), shirt=(220, 214, 196), trim=(30, 50, 80),
                                 hat=(46, 72, 110), trousers=(46, 60, 80), boot=(56, 44, 34), metal=(180, 160, 110)),
         face=dict(eye="kind", iris=(90, 110, 130), brow="thick", mouth="smile"), hair=BALDISH, hat=dict(kind="kerchief"), beard="full", hairline=27.0,
         build="broad", upper=dict(apron=dict(bottom=4.6, top=18.0, w=3.2), collar=True, collar_mat="trim"), lower="trousers", sleeve="rolled",
         arms=HOLD_L, props={"prop_l": P2.map_tube}),
])

# ---------------------------------------------------------------- 内海の渡し守 (両岸に一人ずつ。静かな女の船頭)
folk("inner_ferryman", "内海の渡し守", [
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(40, 40, 50), coat=(80, 120, 120), mantle=(60, 90, 96), shirt=(220, 220, 210), trim=(40, 66, 70),
                               hat=(200, 180, 130), skirt=(70, 100, 104), trousers=(50, 60, 60), boot=(60, 50, 40), metal=(190, 170, 110)),
         face=dict(eye="lady", iris=(70, 120, 120), brow="soft", mouth="small", blush=0.15), hair=BRAID, hat=dict(kind="kasa"), hairline=27.8,
         build="lady", upper=dict(mantle=dict(bottom=16.0, clasp=True)), lower="skirt", sleeve="wide", arms=HOLD_L_LOW, props={"prop_l": P2.rope_coil}),
    dict(anim="hold_r", pal=dict(skin=SKIN["mid"], hair=(120, 80, 50), coat=(120, 96, 70), vest=(70, 110, 110), shirt=(226, 220, 200), trim=(70, 50, 36),
                                 hat=(70, 110, 110), trousers=(70, 60, 50), boot=(60, 46, 34), metal=(170, 150, 100), scarf=(70, 110, 110)),
         face=dict(eye="lady", iris=(90, 80, 60), brow="flat", mouth="lips", lip=(170, 96, 86)), hair=BUN, hat=dict(kind="kerchief"), hairline=27.6,
         build="lady", upper=dict(vest=True, scarf=True), lower="trousers", sleeve="rolled", props={"prop_r": P2.guide_staff}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(30, 28, 30), coat=(60, 80, 90), apron=(170, 150, 110), shirt=(210, 200, 180), trim=(30, 40, 50),
                                 hat=(60, 80, 90), skirt=(60, 80, 90), trousers=(50, 56, 60), boot=(46, 40, 34), metal=(150, 160, 160)),
         face=dict(eye="calm", iris=(60, 70, 80), brow="soft", mouth="neutral"), hair=LONG, hat=dict(kind="band"), hairline=27.6,
         build="lady", upper=dict(apron=dict(bottom=5.0, top=17.0, w=3.0), sash=True), lower="robe", sleeve="fitted", arms=HOLD_L_LOW,
         props={"prop_l": P2.rope_coil}),
])

# ---------------------------------------------------------------- 汐守の灯台守 (灯りを守る、目の細い年寄り)
folk("tide_lightkeeper", "汐守の灯台守", [
    dict(anim="hold_l", pal=dict(skin=SKIN["old"], hair=(220, 216, 208), coat=(40, 50, 70), mantle=(200, 196, 180), shirt=(220, 214, 200), trim=(170, 140, 70),
                                 hat=(40, 50, 70), trousers=(40, 46, 60), boot=(40, 36, 32), metal=(220, 190, 100), glass=(250, 230, 150)),
         face=dict(eye="narrow", iris=(80, 100, 130), brow="thick", mouth="neutral"), hair=BALDISH, hat=dict(kind="cap"), beard="full", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True), buttons=[14.0, 16.0, 18.0]), lower="coat_long", sleeve="fitted",
         arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="free", pal=dict(skin=SKIN["tan"], hair=(150, 140, 130), coat=(210, 200, 170), vest=(170, 60, 50), shirt=(230, 224, 210), trim=(120, 40, 36),
                               hat=(170, 60, 50), trousers=(60, 60, 70), boot=(50, 40, 34), metal=(200, 170, 90)),
         face=dict(eye="kind", iris=(90, 90, 100), brow="soft", mouth="smile"), hair=SHORT, hat=dict(kind="beret"), beard="goatee", hairline=27.4,
         build="slim", upper=dict(vest=True, lapels=True, buttons=[13.0, 15.0]), lower="trousers", sleeve="fitted"),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(120, 100, 80), coat=(60, 90, 110), apron=(150, 120, 80), shirt=(210, 200, 176), trim=(40, 60, 80),
                                 hat=(150, 120, 80), trousers=(50, 60, 70), boot=(46, 40, 34), metal=(180, 160, 110)),
         face=dict(eye="narrow", iris=(70, 90, 110), brow="flat", mouth="smirk", marks=[(["w"], 4, 47)]), hair=SHORT, hat=dict(kind="brim"),
         beard="stubble", hairline=27.4, build="stout", upper=dict(apron=dict(bottom=6.0, top=18.0, w=3.0), pouches=[(3.6, 10.4, "pouch")]),
         lower="trousers", sleeve="rolled", props={"prop_r": P2.bottle}),
])

# ---------------------------------------------------------------- 風待の里の長 (山里の年寄りの女)
folk("storm_elder", "風待の里の長", [
    dict(anim="hold_r", pal=dict(skin=SKIN["old"], hair=(230, 228, 224), coat=(110, 60, 70), mantle=(220, 200, 150), shirt=(226, 214, 190), trim=(70, 40, 44),
                                 hat=(220, 200, 150), skirt=(100, 56, 64), trousers=(70, 50, 50), boot=(60, 46, 38), metal=(200, 180, 120)),
         face=dict(eye="kind", iris=(100, 90, 80), brow="soft", mouth="small", marks=[(["w"], 4, 48)]), hair=BUN, hat=dict(kind="hood"), hairline=27.6,
         build="old", upper=dict(mantle=dict(bottom=14.0, clasp=True)), lower="robe", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.guide_staff}),
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(200, 196, 190), coat=(60, 80, 60), apron=(190, 170, 120), shirt=(220, 210, 186), trim=(40, 56, 40),
                                 hat=(190, 170, 120), skirt=(60, 80, 60), trousers=(50, 60, 50), boot=(50, 40, 32), metal=(170, 160, 120)),
         face=dict(eye="calm", iris=(90, 100, 80), brow="flat", mouth="neutral"), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.8,
         build="old", upper=dict(apron=dict(bottom=4.0, top=17.0, w=3.0), sash=True), lower="skirt", sleeve="rolled", arms=HOLD_L,
         props={"prop_l": P2.herb_basket_arm}),
    dict(anim="free", pal=dict(skin=SKIN["pale"], hair=(210, 210, 214), coat=(60, 70, 110), mantle=(150, 150, 170), shirt=(220, 220, 230), trim=(200, 180, 110),
                               hat=(60, 70, 110), skirt=(56, 64, 100), trousers=(50, 56, 80), boot=(56, 50, 46), metal=(210, 190, 120)),
         face=dict(eye="sharp", iris=(80, 90, 130), brow="arched", mouth="small"), hair=LONG, hat=dict(kind="tall"), hairline=27.6,
         build="old", upper=dict(mantle=dict(bottom=16.0, clasp=True), collar=True, collar_mat="trim"), lower="robe", sleeve="wide"),
])

# ---------------------------------------------------------------- 山小屋の番 (峰の道の小屋を守る若い男)
folk("storm_hutkeeper", "山小屋の番", [
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(90, 64, 44), coat=(130, 70, 50), mantle=(200, 190, 170), shirt=(210, 200, 180), trim=(70, 40, 30),
                                 hat=(200, 190, 170), trousers=(70, 60, 50), boot=(60, 46, 34), metal=(150, 140, 130)),
         face=dict(eye="round", iris=(90, 80, 70), brow="thick", mouth="grin"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="broad", upper=dict(mantle=dict(bottom=17.0, clasp=True), pouches=[(-3.6, 10.4, "pouch")]), lower="trousers", sleeve="fitted",
         props={"prop_r": P2.shovel}),
    dict(anim="free", pal=dict(skin=SKIN["tan"], hair=(40, 36, 34), coat=(70, 90, 70), vest=(180, 160, 120), shirt=(200, 196, 180), trim=(40, 50, 40),
                               hat=(180, 160, 120), trousers=(56, 60, 50), boot=(50, 40, 32), metal=(140, 150, 150), scarf=(150, 60, 40)),
         face=dict(eye="narrow", iris=(70, 70, 60), brow="flat", mouth="neutral"), hair=SHORT, hat=dict(kind="band"), beard="none", hairline=27.4,
         build="slim", upper=dict(vest=True, scarf=True, bandolier=True), lower="trousers", sleeve="rolled"),
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(150, 100, 60), coat=(90, 80, 110), apron=(130, 110, 80), shirt=(210, 200, 186), trim=(60, 50, 80),
                                 hat=(90, 80, 110), trousers=(60, 56, 70), boot=(50, 40, 36), metal=(160, 150, 140)),
         face=dict(eye="kind", iris=(100, 80, 60), brow="soft", mouth="smile"), hair=SHORT, hat=dict(kind="kerchief"), beard="goatee", hairline=27.4,
         build="avg", upper=dict(apron=dict(bottom=6.0, top=18.0, w=3.0), collar=True, collar_mat="trim"), lower="tails", sleeve="fitted",
         arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
])

# ---------------------------------------------------------------- 灰の民の生き残り (灰炉の町。煤にまみれた)
folk("ember_survivor", "灰の民の生き残り", [
    dict(anim="hold_l", pal=dict(skin=SKIN["brown"], hair=(50, 46, 44), coat=(70, 64, 60), mantle=(110, 100, 90), shirt=(150, 140, 126), trim=(160, 70, 30),
                               hat=(110, 100, 90), trousers=(56, 50, 46), boot=(40, 36, 32), metal=(180, 90, 40)),
         face=dict(eye="sharp", iris=(150, 80, 40), brow="stern", mouth="frown", marks=[(["w"], 4, 47)]), hair=SHORT, hat=dict(kind="hood"),
         beard="stubble", hairline=27.4, build="slim", upper=dict(mantle=dict(bottom=15.0, clasp=True), sash=True), lower="trousers", sleeve="wide",
         arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(110, 60, 40), coat=(90, 50, 40), apron=(60, 56, 54), shirt=(170, 150, 120), trim=(40, 34, 30),
                                 hat=(60, 56, 54), trousers=(60, 50, 44), boot=(40, 34, 30), metal=(200, 110, 40)),
         face=dict(eye="lady", iris=(160, 90, 40), brow="worried", mouth="small"), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.8, build="lady",
         upper=dict(apron=dict(bottom=4.0, top=17.0, w=3.0), pouches=[(3.6, 10.4, "pouch")]), lower="skirt", sleeve="rolled",
         props={"prop_r": P2.bottle}),
    dict(anim="hold_l", pal=dict(skin=SKIN["ruddy"], hair=(30, 28, 26), coat=(50, 48, 46), vest=(130, 60, 30), shirt=(160, 150, 136), trim=(30, 28, 26),
                                 hat=(130, 60, 30), trousers=(46, 42, 40), boot=(36, 32, 28), metal=(170, 100, 50)),
         face=dict(eye="round", iris=(140, 80, 40), brow="thick", mouth="neutral"), hair=SHORT, hat=dict(kind="band"), beard="full", hairline=27.4,
         build="broad", upper=dict(vest=True, collar=True, collar_mat="trim"), lower="trousers", sleeve="rolled", arms=HOLD_L_LOW,
         props={"prop_l": P2.rope_coil}),
])

# ---------------------------------------------------------------- 焼けた砦の見張り (灰炉の町。槍を持つ番兵)
folk("ember_watch", "焼けた砦の見張り", [
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(60, 50, 40), coat=(110, 40, 30), mantle=(60, 56, 54), shirt=(170, 150, 126), trim=(50, 44, 40),
                                 hat=(130, 130, 136), trousers=(60, 50, 44), boot=(40, 34, 30), metal=(150, 150, 156)),
         face=dict(eye="sharp", iris=(100, 70, 50), brow="stern", mouth="frown"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="broad", upper=dict(mantle=dict(bottom=17.0, clasp=True), bandolier=True), lower="trousers", sleeve="fitted", gloves=True,
         arms=STAFF_R, props={"prop_r": P2.spear}),
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(130, 90, 60), coat=(70, 60, 56), vest=(150, 70, 40), shirt=(180, 166, 140), trim=(40, 36, 34),
                                 hat=(70, 60, 56), trousers=(50, 46, 44), boot=(40, 36, 32), metal=(170, 160, 150)),
         face=dict(eye="narrow", iris=(90, 70, 50), brow="flat", mouth="neutral", marks=[(["w"], 4, 48)]), hair=SHORT, hat=dict(kind="kerchief"),
         beard="goatee", hairline=27.4, build="slim", upper=dict(vest=True, sash=True), lower="trousers", sleeve="rolled", arms=HOLD_L,
         props={"prop_l": lantern_l}),
    dict(anim="free", pal=dict(skin=SKIN["brown"], hair=(40, 36, 34), coat=(90, 80, 70), mantle=(140, 50, 36), shirt=(170, 156, 130), trim=(200, 150, 60),
                               hat=(140, 50, 36), trousers=(56, 50, 46), boot=(40, 34, 30), metal=(200, 150, 60)),
         face=dict(eye="calm", iris=(120, 90, 50), brow="thick", mouth="smirk"), hair=LONG, hat=dict(kind="band"), beard="none", hairline=27.6,
         build="avg", upper=dict(mantle=dict(bottom=16.0, clasp=True), collar=True, collar_mat="trim"), lower="coat_long", sleeve="fitted",
         gloves=True),
])


# 選定 (2026-10-28): 8 人とも A。cast の guest_<人> として登録する (install_npc2 が読む。人の動きの型も A のもの)
PICK = {p: "a" for p in ("island_ferryman", "tide_ferryman", "inner_ferryman", "tide_lightkeeper", "storm_elder", "storm_hutkeeper", "ember_survivor", "ember_watch")}
for _p, _k in PICK.items():
    C.CAST[f"guest_{_p}"] = C.CAST[f"guest_{_p}_{_k}"]


def sheet(pid):
    out = Path(__file__).resolve().parents[4] / "docs/review/npc_models_v2/roughs"
    ims = [Image.open(out / f"guest_{pid}_{k}.png").convert("RGB") for k in "abc"]
    W = sum(i.width for i in ims)
    H = max(i.height for i in ims) + 36
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    import voxel
    d.text((10, 6), f"{FOLK[pid]} 3 案", font=voxel.font(22), fill=(236, 232, 218))
    x = 0
    for im in ims:
        cv.paste(im, (x, 36))
        x += im.width
    cv.save(out / f"folk_{pid}.png")


if __name__ == "__main__":
    import build as B
    ids = sys.argv[1:] or list(FOLK)
    B.main([f"guest_{p}_{k}" for p in ids for k in "abc"])
    for p in ids:
        sheet(p)
    print("ok", len(ids))
