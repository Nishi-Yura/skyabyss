"""外れの集落の住人 16 人と霜の渡し守の体 3 案ずつ (2026-11-06。world/continent/FrontierHamlets、town/RegionFolk)。
名は guest_<人>_<a|b|c>。選んだ案 (PICK) は cast の guest_<人> として足す (install_npc2 が読む)。

  python -I tools/rough/lowpoly/npc2/cast_hamlet.py [人 ...]   -> docs/review/npc_models_v2/roughs/guest_<人>_<案>.png と folk_<人>.png (3 案を並べた絵)
  python -I tools/rough/lowpoly/npc2/cast_hamlet.py pick [人 ...] -> 選んだ案を guest_<人> として書き出す (そのあと install_npc2.py guest_<人>)
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cast as C  # noqa: E402
from cast import SKIN, HOLD_L, HOLD_L_LOW, STAFF_R, HOLD2_LOW, BIRD_L  # noqa: E402
import props as PR  # noqa: E402
import props2 as P2  # noqa: E402
import props3 as P3  # noqa: E402
import cast_folk as CF  # noqa: E402
from cast_folk import SHORT, BALDISH, LONG, BUN, BRAID, lantern_l  # noqa: E402

PONY = dict(style="ponytail", front=27.6, side=25.0, back=23.8, fringe=[(1.8, 28.0, 1.8, 0.7, 0.4)])
TOPKNOT = dict(style="topknot", front=28.0, side=25.2, back=23.8, off=0.35, fringe=[])
SIDELOCKS = dict(style="sidelocks", front=27.8, side=24.6, back=23.4,
                 fringe=[(-1.6, 28.1, 1.8, 0.75, -0.3), (0.2, 28.2, 1.9, 0.8, 0.0), (1.8, 28.1, 1.7, 0.75, 0.4)])
PICK_ARM = {"r": ((-6.4, 17.4, -1.2), (-4.8, 21.0, -3.0))}

IDS = []


def folk(pid, ja, opts):
    IDS.append(pid)
    CF.folk(pid, ja, opts)


# ================================================================ 骨垣の砦村 (岩鯨の北東。骨の垣と狼煙台。骨の白・岩の灰・苔の緑)
folk("bonefort_beacon", "骨垣の狼煙番トウジ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["old"], hair=(206, 200, 190), coat=(92, 84, 72), mantle=(196, 186, 162), shirt=(214, 204, 182), trim=(60, 52, 42),
                                 hat=(150, 64, 40), trousers=(66, 60, 52), boot=(48, 40, 32), metal=(200, 170, 90), glass=(255, 200, 110)),
         face=dict(eye="narrow", iris=(90, 84, 74), brow="thick", mouth="frown", marks=[(["w"], 4, 48)]), hair=BALDISH, hat=dict(kind="band"),
         beard="full", hairline=27.0, build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True), sash=True), lower="trousers", sleeve="rolled",
         arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(170, 160, 150), coat=(70, 78, 66), vest=(186, 176, 150), shirt=(210, 202, 180), trim=(44, 50, 42),
                                 hat=(70, 78, 66), trousers=(58, 62, 54), boot=(50, 42, 34), metal=(190, 186, 170), scarf=(160, 60, 40)),
         face=dict(eye="sharp", iris=(80, 80, 70), brow="stern", mouth="neutral"), hair=SHORT, hat=dict(kind="cap"), beard="moustache", hairline=27.2,
         build="stout", upper=dict(vest=True, scarf=True), lower="trousers", sleeve="rolled", arms=STAFF_R, props={"prop_r": P2.spear}),
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(180, 176, 168), coat=(110, 100, 84), mantle=(80, 76, 66), shirt=(206, 196, 174), trim=(52, 46, 38),
                                 hat=(80, 76, 66), trousers=(70, 64, 54), boot=(46, 38, 30), metal=(170, 160, 130)),
         face=dict(eye="old", iris=(90, 90, 80), brow="thick", mouth="neutral"), hair=LONG, hat=dict(kind="hood"), beard="long", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=12.0, clasp=True)), lower="robe", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.guide_staff}),
])

folk("bonefort_scribe", "骨垣の写字生セツ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(46, 40, 38), coat=(84, 90, 104), apron=(200, 192, 170), shirt=(226, 220, 206), trim=(50, 54, 66),
                                 hat=(84, 90, 104), skirt=(70, 74, 88), boot=(56, 46, 38), metal=(180, 160, 110)),
         face=dict(eye="lady", iris=(70, 70, 90), brow="soft", mouth="small"), hair=BUN, hat=dict(kind="kerchief"), hairline=27.6,
         build="lady", upper=dict(apron=dict(bottom=6.0, top=17.0, w=2.8), collar=True, collar_mat="shirt"), lower="robe", sleeve="fitted",
         arms=HOLD_L, props={"prop_l": PR.ledger}),
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(120, 70, 44), coat=(120, 108, 86), mantle=(70, 76, 66), shirt=(220, 212, 192), trim=(60, 52, 40),
                                 hat=(70, 76, 66), skirt=(96, 86, 70), boot=(54, 44, 34), metal=(170, 150, 100)),
         face=dict(eye="round", iris=(90, 80, 60), brow="worried", mouth="small", blush=0.12), hair=BRAID, hat=dict(kind="hood"), hairline=27.8,
         build="lady", upper=dict(mantle=dict(bottom=16.0, clasp=True)), lower="skirt", sleeve="fitted", arms=HOLD_L, props={"prop_l": P2.map_tube}),
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(30, 28, 30), coat=(60, 56, 64), vest=(150, 140, 120), shirt=(230, 226, 214), trim=(40, 36, 44),
                                 hat=(150, 140, 120), trousers=(56, 52, 60), boot=(44, 38, 34), metal=(190, 170, 120)),
         face=dict(eye="calm", iris=(60, 60, 70), brow="flat", mouth="neutral"), hair=SIDELOCKS, hat=dict(kind="beret"), hairline=27.8,
         build="slim", upper=dict(vest=True, collar=True, collar_mat="shirt"), lower="tails", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P2.flask}),
])

# ================================================================ 鐘見の里 (岩鯨の北西。都の鐘を聞く里。藍・灰・白樺)
folk("bellwatch_crone", "鐘見の鐘聞きオトメ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["old"], hair=(214, 210, 204), coat=(60, 70, 96), mantle=(170, 160, 140), shirt=(216, 208, 190), trim=(40, 46, 66),
                                 hat=(60, 70, 96), skirt=(56, 62, 84), boot=(50, 42, 36), metal=(210, 180, 100)),
         face=dict(eye="old", iris=(80, 80, 90), brow="soft", mouth="small"), hair=BUN, hat=dict(kind="kerchief"), hairline=27.4,
         build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True)), lower="skirt", sleeve="wide", arms=HOLD_L_LOW, props={"prop_l": P2.handbell}),
    dict(anim="hold_r", pal=dict(skin=SKIN["old"], hair=(190, 186, 180), coat=(110, 96, 80), mantle=(70, 60, 56), shirt=(210, 200, 180), trim=(56, 46, 40),
                                 hat=(70, 60, 56), skirt=(90, 78, 64), boot=(46, 38, 32), metal=(170, 150, 110)),
         face=dict(eye="narrow", iris=(80, 70, 60), brow="worried", mouth="frown"), hair=BUN, hat=dict(kind="hood"), hairline=27.4,
         build="old", upper=dict(mantle=dict(bottom=13.0, clasp=True)), lower="robe", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.guide_staff}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(170, 166, 160), coat=(90, 104, 84), apron=(200, 190, 160), shirt=(214, 206, 186), trim=(50, 60, 46),
                                 hat=(200, 190, 160), skirt=(70, 82, 64), boot=(50, 42, 34), metal=(170, 150, 100)),
         face=dict(eye="kind", iris=(80, 90, 70), brow="soft", mouth="smile"), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.6,
         build="old", upper=dict(apron=dict(bottom=5.0, top=17.0, w=3.0)), lower="skirt", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.herb_basket_arm}),
])

folk("bellwatch_miner", "鐘見の青石掘りイワオ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(50, 44, 40), coat=(74, 80, 92), apron=(120, 92, 64), shirt=(190, 186, 176), trim=(44, 48, 56),
                                 hat=(150, 140, 100), trousers=(60, 62, 68), boot=(44, 38, 32), metal=(120, 170, 220), leather=(110, 84, 58)),
         face=dict(eye="narrow", iris=(70, 80, 90), brow="thick", mouth="neutral"), hair=SHORT, hat=dict(kind="helmet"), beard="stubble", hairline=27.4,
         build="broad", upper=dict(apron=dict(bottom=6.0, top=16.0, w=3.2), pouches=[(-3.6, 10.6, "pouch")]), lower="trousers", sleeve="rolled",
         arms=HOLD_L, props={"prop_l": P3.ore_lump}),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(90, 64, 44), coat=(96, 80, 64), vest=(60, 66, 80), shirt=(200, 192, 172), trim=(50, 42, 34),
                                 hat=(60, 66, 80), trousers=(66, 58, 48), boot=(48, 40, 32), metal=(160, 160, 170)),
         face=dict(eye="sharp", iris=(80, 70, 60), brow="stern", mouth="smirk"), hair=SHORT, hat=dict(kind="band"), beard="full", hairline=27.2,
         build="stout", upper=dict(vest=True, buttons=[14.0, 16.0]), lower="trousers", sleeve="rolled", arms=PICK_ARM, props={"prop_r": PR.toya_pick}),
    dict(anim="hold_r", pal=dict(skin=SKIN["brown"], hair=(30, 28, 26), coat=(70, 74, 70), mantle=(120, 110, 90), shirt=(196, 188, 170), trim=(40, 44, 40),
                                 hat=(120, 110, 90), trousers=(56, 58, 54), boot=(42, 36, 30), metal=(150, 150, 150)),
         face=dict(eye="calm", iris=(60, 60, 60), brow="flat", mouth="neutral"), hair=BALDISH, hat=dict(kind="cap"), beard="goatee", hairline=27.0,
         build="broad", upper=dict(mantle=dict(bottom=16.0, clasp=True)), lower="trousers", sleeve="rolled", arms=STAFF_R, props={"prop_r": P2.shovel}),
])

# ================================================================ 灯守の里 (潮殻の東。灯台守の家。紺・白・灯の黄)
folk("lightvillage_apprentice", "灯守見習いのミナト", [
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(60, 44, 34), coat=(40, 60, 96), vest=(220, 200, 120), shirt=(232, 228, 216), trim=(30, 40, 66),
                                 hat=(40, 60, 96), trousers=(44, 54, 76), boot=(60, 46, 34), metal=(220, 190, 90), glass=(255, 220, 120)),
         face=dict(eye="round", iris=(70, 90, 120), brow="soft", mouth="grin"), hair=SHORT, hat=dict(kind="cap"), beard="none", hairline=27.4,
         build="slim", upper=dict(vest=True), lower="trousers", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="hold_r", pal=dict(skin=SKIN["mid"], hair=(110, 70, 40), coat=(60, 90, 100), apron=(200, 190, 160), shirt=(226, 220, 204), trim=(36, 56, 64),
                                 hat=(200, 190, 160), trousers=(50, 70, 76), boot=(56, 44, 34), metal=(170, 160, 140)),
         face=dict(eye="child", iris=(80, 100, 90), brow="worried", mouth="o"), hair=SHORT, hat=dict(kind="kerchief"), beard="none", hairline=27.4,
         build="slim", upper=dict(apron=dict(bottom=6.0, top=16.0, w=2.8)), lower="trousers", sleeve="rolled", props={"prop_r": P2.bucket}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(30, 30, 34), coat=(150, 140, 110), mantle=(40, 56, 80), shirt=(220, 214, 196), trim=(30, 40, 56),
                                 hat=(40, 56, 80), trousers=(60, 60, 56), boot=(50, 40, 32), metal=(200, 180, 110)),
         face=dict(eye="glint", iris=(60, 80, 100), brow="arched", mouth="smile"), hair=PONY, hat=dict(kind="beret"), beard="none", hairline=27.6,
         build="slim", upper=dict(mantle=dict(bottom=17.0, clasp=True)), lower="trousers", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P2.flask}),
])

folk("lightvillage_comber", "灯守の里の浜拾いアオ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(40, 34, 30), coat=(70, 120, 130), apron=(220, 210, 180), shirt=(234, 228, 212), trim=(40, 70, 76),
                                 hat=(220, 210, 180), skirt=(60, 96, 104), boot=(150, 120, 80), metal=(200, 190, 170)),
         face=dict(eye="round", iris=(60, 90, 90), brow="soft", mouth="smile", blush=0.15), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.8,
         build="lady", upper=dict(apron=dict(bottom=8.0, top=16.0, w=3.0)), lower="skirt", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.herb_basket_arm}),
    dict(anim="hold_r", pal=dict(skin=SKIN["mid"], hair=(150, 96, 56), coat=(200, 180, 140), vest=(70, 100, 120), shirt=(230, 224, 208), trim=(50, 70, 84),
                                 hat=(200, 180, 140), skirt=(80, 90, 100), boot=(120, 96, 64), metal=(180, 170, 150)),
         face=dict(eye="glint", iris=(80, 100, 110), brow="arched", mouth="grin"), hair=PONY, hat=dict(kind="brim"), hairline=27.6,
         build="lady", upper=dict(vest=True, sash=True), lower="skirt", sleeve="rolled", props={"prop_r": P2.bucket}),
    dict(anim="hold_l", pal=dict(skin=SKIN["brown"], hair=(24, 22, 22), coat=(90, 80, 64), mantle=(60, 110, 120), shirt=(220, 212, 190), trim=(40, 60, 64),
                                 hat=(60, 110, 120), trousers=(70, 64, 54), boot=(80, 64, 46), metal=(170, 160, 140)),
         face=dict(eye="calm", iris=(60, 70, 70), brow="flat", mouth="small"), hair=LONG, hat=dict(kind="band"), hairline=27.6,
         build="slim", upper=dict(mantle=dict(bottom=17.0, clasp=True)), lower="trousers", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.rope_coil}),
])

# ================================================================ 潮垣の砦村 (潮殻の南東。潮の垣。潮の青緑・砂・塩の白)
folk("tidefort_watch", "潮垣の見張りカジ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(40, 36, 34), coat=(40, 80, 90), mantle=(170, 160, 130), shirt=(214, 206, 186), trim=(30, 50, 56),
                                 hat=(150, 156, 160), trousers=(50, 62, 66), boot=(48, 40, 32), metal=(170, 176, 180), metal2=(110, 116, 120), leather=(100, 80, 56)),
         face=dict(eye="sharp", iris=(70, 90, 90), brow="stern", mouth="frown"), hair=SHORT, hat=dict(kind="helmet"), beard="stubble", hairline=27.4,
         build="broad", upper=dict(mantle=dict(bottom=15.0, clasp=True), pauldron=True), lower="trousers", sleeve="fitted", arms=STAFF_R, props={"prop_r": P2.spear}),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(110, 70, 44), coat=(60, 90, 110), vest=(190, 170, 120), shirt=(220, 212, 190), trim=(40, 56, 70),
                                 hat=(60, 90, 110), trousers=(54, 64, 74), boot=(50, 42, 34), metal=(170, 160, 140), scarf=(200, 190, 160)),
         face=dict(eye="narrow", iris=(80, 90, 100), brow="thick", mouth="smirk"), hair=SHORT, hat=dict(kind="cap"), beard="full", hairline=27.2,
         build="stout", upper=dict(vest=True, scarf=True), lower="trousers", sleeve="rolled", props={"prop_r": P2.harpoon}),
    dict(anim="hold_l", pal=dict(skin=SKIN["brown"], hair=(28, 26, 26), coat=(120, 110, 86), mantle=(36, 70, 76), shirt=(206, 198, 178), trim=(30, 46, 50),
                                 hat=(36, 70, 76), trousers=(60, 58, 50), boot=(44, 38, 30), metal=(150, 160, 160), leather=(90, 70, 50)),
         face=dict(eye="calm", iris=(60, 70, 70), brow="flat", mouth="neutral"), hair=TOPKNOT, hat=dict(kind="band"), beard="goatee", hairline=27.6,
         build="avg", upper=dict(mantle=dict(bottom=16.0, clasp=True), sash=True), lower="trousers", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P3.round_shield}),
])

folk("tidefort_netmender", "潮垣の網繕いフサ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["old"], hair=(200, 196, 190), coat=(70, 96, 100), apron=(206, 196, 170), shirt=(220, 212, 194), trim=(44, 62, 66),
                                 hat=(206, 196, 170), skirt=(60, 80, 86), boot=(60, 50, 40), metal=(170, 160, 140)),
         face=dict(eye="old", iris=(80, 90, 90), brow="soft", mouth="small"), hair=BUN, hat=dict(kind="kerchief"), hairline=27.4,
         build="old", upper=dict(apron=dict(bottom=5.0, top=17.0, w=3.0)), lower="skirt", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.rope_coil}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(160, 156, 150), coat=(110, 90, 70), mantle=(150, 140, 116), shirt=(214, 204, 184), trim=(60, 50, 40),
                                 hat=(150, 140, 116), skirt=(86, 72, 58), boot=(50, 42, 34), metal=(160, 150, 130)),
         face=dict(eye="kind", iris=(80, 70, 60), brow="worried", mouth="smile"), hair=BUN, hat=dict(kind="hood"), hairline=27.4,
         build="old", upper=dict(mantle=dict(bottom=14.0, clasp=True)), lower="robe", sleeve="wide", arms=HOLD_L, props={"prop_l": P2.cloth_bolt}),
    dict(anim="hold_r", pal=dict(skin=SKIN["old"], hair=(180, 176, 172), coat=(60, 70, 80), vest=(170, 150, 110), shirt=(212, 206, 190), trim=(40, 46, 54),
                                 hat=(170, 150, 110), skirt=(70, 76, 84), boot=(48, 42, 36), metal=(160, 150, 140)),
         face=dict(eye="narrow", iris=(70, 70, 70), brow="flat", mouth="neutral"), hair=BRAID, hat=dict(kind="brim"), hairline=27.6,
         build="old", upper=dict(vest=True, sash=True), lower="skirt", sleeve="rolled", props={"prop_r": P2.bucket}),
])

# ================================================================ 熾垣の砦村 (余燼の南東。炉で焼いた垣。炭・錆の赤・黄土)
folk("emberfort_smith", "熾垣の砦鍛冶タタラ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["brown"], hair=(36, 30, 28), coat=(70, 60, 56), apron=(110, 70, 44), shirt=(190, 170, 140), trim=(40, 34, 30),
                                 hat=(150, 60, 36), trousers=(56, 50, 46), boot=(40, 34, 30), metal=(170, 160, 150), glove=(90, 64, 44), leather=(110, 76, 50)),
         face=dict(eye="sharp", iris=(90, 70, 50), brow="thick", mouth="frown", marks=[(["S", "S"], 2, 47, 1)]), hair=BALDISH, hat=dict(kind="band"),
         beard="full", hairline=27.0, build="broad", upper=dict(apron=dict(bottom=5.0, top=19.0, w=3.4)), lower="trousers", sleeve="rolled",
         props={"prop_r": P2.hammer}),
    dict(anim="hold2", pal=dict(skin=SKIN["tan"], hair=(110, 60, 40), coat=(90, 56, 44), vest=(60, 54, 50), shirt=(200, 180, 150), trim=(50, 36, 30),
                                hat=(60, 54, 50), trousers=(60, 50, 44), boot=(44, 36, 30), metal=(150, 140, 130)),
         face=dict(eye="narrow", iris=(80, 60, 40), brow="stern", mouth="neutral"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.2,
         build="stout", upper=dict(vest=True), lower="trousers", sleeve="rolled", arms=HOLD2_LOW, props={"prop_r": P2.crate}),
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(60, 50, 46), coat=(120, 96, 60), apron=(60, 56, 52), shirt=(206, 190, 160), trim=(70, 52, 34),
                                 hat=(60, 56, 52), trousers=(70, 60, 48), boot=(46, 38, 30), metal=(160, 150, 140), leather=(100, 70, 46)),
         face=dict(eye="calm", iris=(70, 60, 50), brow="flat", mouth="smirk"), hair=TOPKNOT, hat=dict(kind="band"), beard="moustache", hairline=27.6,
         build="broad", upper=dict(apron=dict(bottom=6.0, top=18.0, w=3.2), pouches=[(-3.6, 10.6, "pouch")]), lower="trousers", sleeve="rolled",
         arms=STAFF_R, props={"prop_r": P2.shovel}),
])

folk("emberfort_survivor", "熾垣の灰見のシノ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["mid"], hair=(40, 34, 32), coat=(110, 60, 44), mantle=(90, 86, 80), shirt=(206, 196, 176), trim=(60, 40, 32),
                                 hat=(90, 86, 80), trousers=(64, 56, 50), boot=(44, 36, 30), metal=(170, 160, 150), scarf=(170, 160, 140)),
         face=dict(eye="sharp", iris=(80, 60, 50), brow="stern", mouth="small", marks=[(["w"], 4, 48)]), hair=PONY, hat=dict(kind="hood"), hairline=27.6,
         build="slim", upper=dict(mantle=dict(bottom=15.0, clasp=True), scarf=True), lower="trousers", sleeve="fitted", arms=STAFF_R, props={"prop_r": P2.spear}),
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(170, 150, 130), coat=(80, 76, 72), mantle=(140, 70, 50), shirt=(214, 204, 186), trim=(50, 46, 44),
                                 hat=(140, 70, 50), skirt=(70, 64, 60), boot=(50, 42, 36), metal=(200, 170, 100), glass=(255, 190, 110)),
         face=dict(eye="lady", iris=(90, 80, 70), brow="worried", mouth="small"), hair=LONG, hat=dict(kind="kerchief"), hairline=27.6,
         build="lady", upper=dict(mantle=dict(bottom=16.0, clasp=True)), lower="skirt", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(24, 22, 22), coat=(60, 58, 56), vest=(150, 110, 70), shirt=(200, 190, 170), trim=(40, 38, 36),
                                 hat=(60, 58, 56), trousers=(56, 52, 48), boot=(40, 34, 30), metal=(160, 160, 160), leather=(100, 76, 50)),
         face=dict(eye="narrow", iris=(70, 60, 50), brow="flat", mouth="frown"), hair=SHORT, hat=dict(kind="band"), hairline=27.4,
         build="slim", upper=dict(vest=True, sash=True), lower="trousers", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P3.round_shield}),
])

# ================================================================ 煙見の里 (余燼の北。煙を読む里。煤・黄土・煙の灰青)
folk("smokewatch_reader", "煙見の煙読みユウ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["old"], hair=(200, 198, 194), coat=(96, 96, 104), mantle=(150, 120, 80), shirt=(212, 204, 186), trim=(56, 56, 62),
                                 hat=(96, 96, 104), trousers=(70, 68, 70), boot=(46, 40, 36), metal=(170, 150, 110)),
         face=dict(eye="sleepy", iris=(90, 90, 100), brow="thick", mouth="neutral"), hair=BALDISH, hat=dict(kind="tall"), beard="long", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=14.0, clasp=True)), lower="robe", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.guide_staff}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(170, 168, 164), coat=(130, 110, 74), vest=(80, 80, 88), shirt=(208, 200, 180), trim=(66, 56, 40),
                                 hat=(130, 110, 74), trousers=(74, 66, 56), boot=(48, 40, 32), metal=(170, 160, 140)),
         face=dict(eye="narrow", iris=(80, 80, 80), brow="arched", mouth="smirk"), hair=SHORT, hat=dict(kind="kasa"), beard="goatee", hairline=27.2,
         build="old", upper=dict(vest=True, sash=True), lower="trousers", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.flask}),
    dict(anim="hold_l", pal=dict(skin=SKIN["brown"], hair=(150, 146, 140), coat=(70, 66, 70), mantle=(110, 100, 90), shirt=(200, 192, 176), trim=(44, 40, 44),
                                 hat=(110, 100, 90), trousers=(60, 56, 56), boot=(42, 36, 32), metal=(160, 150, 130)),
         face=dict(eye="old", iris=(70, 70, 70), brow="worried", mouth="frown"), hair=LONG, hat=dict(kind="hood"), beard="full", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=12.0, clasp=True)), lower="robe", sleeve="wide", arms=HOLD_L, props={"prop_l": PR.ledger}),
])

folk("smokewatch_twin", "煙見の双子ヨル", [
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(36, 30, 30), coat=(80, 84, 96), vest=(150, 110, 70), shirt=(220, 212, 196), trim=(50, 52, 60),
                                 hat=(80, 84, 96), trousers=(60, 60, 66), boot=(56, 44, 34), metal=(170, 160, 140), scarf=(180, 90, 60)),
         face=dict(eye="round", iris=(80, 70, 60), brow="worried", mouth="small"), hair=SHORT, hat=dict(kind="band"), beard="none", hairline=27.4,
         build="slim", upper=dict(vest=True, scarf=True), lower="trousers", sleeve="fitted", arms=HOLD_L, props={"prop_l": P2.doll}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(110, 70, 44), coat=(120, 100, 70), mantle=(70, 70, 76), shirt=(214, 206, 188), trim=(60, 50, 36),
                                 hat=(70, 70, 76), trousers=(66, 60, 52), boot=(50, 40, 32), metal=(170, 160, 140)),
         face=dict(eye="child", iris=(90, 80, 60), brow="soft", mouth="neutral"), hair=SHORT, hat=dict(kind="cap"), beard="none", hairline=27.4,
         build="slim", upper=dict(mantle=dict(bottom=17.0, clasp=True)), lower="trousers", sleeve="rolled", arms=BIRD_L, props={"prop_l": P2.bird}),
    dict(anim="free", pal=dict(skin=SKIN["pale"], hair=(24, 22, 24), coat=(60, 60, 66), mantle=(150, 140, 120), shirt=(224, 218, 204), trim=(40, 40, 46),
                               hat=(150, 140, 120), trousers=(54, 54, 60), boot=(44, 38, 34), metal=(160, 150, 130)),
         face=dict(eye="calm", iris=(60, 60, 70), brow="flat", mouth="small"), hair=LONG, hat=dict(kind="kerchief"), beard="none", hairline=27.6,
         build="slim", upper=dict(mantle=dict(bottom=16.0, clasp=True), sash=True), lower="trousers", sleeve="fitted", props={"prop_back": P2.tool_pack}),
])

# ================================================================ 霜垣の砦村 (嵐の高地の北西。凍る垣。氷の青・毛皮の灰・白)
folk("frostfort_hunter", "霜垣の狩人シモ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(70, 56, 44), coat=(90, 100, 110), mantle=(200, 196, 188), shirt=(206, 200, 188), trim=(50, 56, 64),
                                 hat=(200, 196, 188), trousers=(70, 70, 70), boot=(60, 48, 38), metal=(180, 190, 200), leather=(100, 80, 60)),
         face=dict(eye="sharp", iris=(70, 90, 110), brow="thick", mouth="smirk"), hair=SHORT, hat=dict(kind="hood"), beard="full", hairline=27.2,
         build="broad", upper=dict(mantle=dict(bottom=14.0, clasp=True)), lower="trousers", sleeve="fitted", boots="boot", arms=STAFF_R, props={"prop_r": P2.spear}),
    dict(anim="free", pal=dict(skin=SKIN["tan"], hair=(40, 36, 34), coat=(110, 96, 76), vest=(170, 166, 156), shirt=(210, 202, 186), trim=(60, 52, 40),
                               hat=(170, 166, 156), trousers=(66, 60, 52), boot=(50, 42, 34), metal=(170, 170, 170), leather=(110, 84, 58)),
         face=dict(eye="narrow", iris=(70, 70, 60), brow="stern", mouth="neutral"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="stout", upper=dict(vest=True, bandolier=True), lower="trousers", sleeve="rolled", boots="boot", props={"prop_back": P3.quiver}),
    dict(anim="hold_r", pal=dict(skin=SKIN["pale"], hair=(200, 190, 170), coat=(60, 76, 96), mantle=(140, 130, 116), shirt=(220, 216, 208), trim=(36, 46, 60),
                                 hat=(60, 76, 96), trousers=(56, 62, 72), boot=(50, 44, 40), metal=(190, 200, 210)),
         face=dict(eye="calm", iris=(90, 110, 130), brow="flat", mouth="neutral"), hair=LONG, hat=dict(kind="band"), beard="goatee", hairline=27.6,
         build="avg", upper=dict(mantle=dict(bottom=15.0, clasp=True), scarf=True), lower="trousers", sleeve="fitted", boots="boot", props={"prop_r": P2.harpoon}),
])

folk("frostfort_fletcher", "霜垣の矢羽師ハヤ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(160, 170, 180), coat=(70, 90, 110), apron=(190, 180, 160), shirt=(226, 224, 218), trim=(40, 54, 70),
                                 hat=(70, 90, 110), skirt=(60, 74, 90), boot=(70, 56, 44), metal=(190, 196, 206)),
         face=dict(eye="lady", iris=(90, 120, 150), brow="arched", mouth="small"), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.8,
         build="lady", upper=dict(apron=dict(bottom=8.0, top=16.0, w=2.8)), lower="skirt", sleeve="fitted", arms=BIRD_L, props={"prop_l": P2.bird}),
    dict(anim="free", pal=dict(skin=SKIN["mid"], hair=(50, 40, 36), coat=(120, 110, 96), mantle=(190, 186, 178), shirt=(214, 208, 194), trim=(60, 54, 46),
                               hat=(190, 186, 178), trousers=(70, 66, 60), boot=(54, 46, 38), metal=(170, 170, 170), leather=(100, 80, 56)),
         face=dict(eye="glint", iris=(80, 80, 70), brow="soft", mouth="smile"), hair=PONY, hat=dict(kind="hood"), hairline=27.6,
         build="lady", upper=dict(mantle=dict(bottom=15.0, clasp=True)), lower="trousers", sleeve="fitted", props={"prop_back": P3.quiver}),
    dict(anim="free", pal=dict(skin=SKIN["tan"], hair=(30, 28, 30), coat=(56, 70, 84), vest=(170, 150, 120), shirt=(220, 214, 200), trim=(36, 44, 56),
                               hat=(170, 150, 120), trousers=(56, 62, 70), boot=(50, 42, 36), metal=(180, 180, 180)),
         face=dict(eye="calm", iris=(60, 70, 80), brow="flat", mouth="neutral"), hair=BUN, hat=dict(kind="beret", feather=True), hairline=27.6,
         build="slim", upper=dict(vest=True, collar=True, collar_mat="shirt"), lower="trousers", sleeve="fitted", props={"prop_back": P2.tool_pack}),
])

# ================================================================ 風車の里 (嵐の高地の南西。風車と苔祠。麦わら・草の緑・空の青)
folk("windmill_keeper", "風車の里の風車番カザミ", [
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(130, 90, 56), coat=(200, 186, 150), vest=(70, 100, 70), shirt=(232, 226, 210), trim=(50, 70, 50),
                                 hat=(70, 100, 70), trousers=(90, 80, 64), boot=(60, 46, 34), metal=(170, 160, 140)),
         face=dict(eye="kind", iris=(80, 100, 70), brow="thick", mouth="grin"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="stout", upper=dict(vest=True, buttons=[14.0, 16.0]), lower="trousers", sleeve="rolled", arms=STAFF_R, props={"prop_r": P2.shovel}),
    dict(anim="hold_l", pal=dict(skin=SKIN["tan"], hair=(60, 50, 40), coat=(100, 120, 140), apron=(220, 214, 196), shirt=(230, 226, 214), trim=(60, 76, 90),
                                 hat=(220, 214, 196), trousers=(70, 80, 90), boot=(56, 46, 36), metal=(170, 160, 140)),
         face=dict(eye="round", iris=(70, 90, 110), brow="soft", mouth="smile"), hair=SHORT, hat=dict(kind="kerchief"), beard="moustache", hairline=27.4,
         build="avg", upper=dict(apron=dict(bottom=5.0, top=17.0, w=3.0)), lower="trousers", sleeve="rolled", arms=HOLD_L, props={"prop_l": P2.bread_basket}),
    dict(anim="hold_r", pal=dict(skin=SKIN["old"], hair=(190, 180, 160), coat=(140, 120, 80), mantle=(80, 96, 70), shirt=(214, 206, 186), trim=(70, 60, 40),
                                 hat=(190, 170, 110), trousers=(80, 70, 56), boot=(50, 42, 34), metal=(180, 120, 80)),
         face=dict(eye="old", iris=(80, 80, 70), brow="flat", mouth="neutral"), hair=BALDISH, hat=dict(kind="kasa"), beard="full", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True)), lower="trousers", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.bell_staff}),
])

folk("windmill_mossguard", "風車の里の苔守ミドリ", [
    dict(anim="hold_l", pal=dict(skin=SKIN["pale"], hair=(60, 80, 56), coat=(60, 96, 70), apron=(200, 196, 170), shirt=(226, 222, 206), trim=(36, 60, 44),
                                 hat=(60, 96, 70), skirt=(50, 80, 60), boot=(60, 50, 40), metal=(120, 200, 230)),
         face=dict(eye="calm", iris=(70, 110, 80), brow="soft", mouth="small"), hair=LONG, hat=dict(kind="hood"), hairline=27.6,
         build="lady", upper=dict(apron=dict(bottom=6.0, top=17.0, w=2.8)), lower="robe", sleeve="wide", arms=HOLD_L_LOW, props={"prop_l": P2.herb_basket_arm}),
    dict(anim="hold_l", pal=dict(skin=SKIN["mid"], hair=(40, 36, 30), coat=(110, 120, 90), mantle=(60, 80, 60), shirt=(220, 214, 196), trim=(50, 60, 40),
                                 hat=(110, 120, 90), skirt=(70, 80, 60), boot=(54, 44, 34), metal=(150, 210, 230), glass=(150, 220, 255)),
         face=dict(eye="lady", iris=(70, 90, 70), brow="arched", mouth="smile", blush=0.12), hair=BRAID, hat=dict(kind="kerchief"), hairline=27.8,
         build="lady", upper=dict(mantle=dict(bottom=16.0, clasp=True)), lower="skirt", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": lantern_l}),
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(150, 160, 120), coat=(80, 70, 56), vest=(70, 110, 80), shirt=(214, 208, 190), trim=(50, 44, 36),
                                 hat=(70, 110, 80), skirt=(70, 64, 54), boot=(50, 42, 34), metal=(160, 200, 220)),
         face=dict(eye="kind", iris=(80, 100, 80), brow="worried", mouth="neutral"), hair=BUN, hat=dict(kind="band"), hairline=27.6,
         build="lady", upper=dict(vest=True, sash=True), lower="skirt", sleeve="rolled", arms=STAFF_R, props={"prop_r": P2.bell_staff}),
])

# ================================================================ 霜の渡し守 (霜の小島への海峡。厚着の舟の人)
folk("isle_ferryman", "霜の渡し守", [
    dict(anim="hold_r", pal=dict(skin=SKIN["ruddy"], hair=(150, 140, 130), coat=(60, 70, 84), mantle=(200, 196, 190), shirt=(210, 204, 192), trim=(36, 42, 52),
                                 hat=(200, 196, 190), trousers=(56, 60, 68), boot=(50, 42, 36), metal=(170, 176, 186)),
         face=dict(eye="narrow", iris=(80, 100, 120), brow="thick", mouth="neutral", marks=[(["w"], 4, 48)]), hair=SHORT, hat=dict(kind="hood"),
         beard="full", hairline=27.2, build="broad", upper=dict(mantle=dict(bottom=14.0, clasp=True), scarf=True), lower="trousers", sleeve="fitted",
         boots="boot", arms=STAFF_R, props={"prop_r": P2.guide_staff}),
    dict(anim="hold_l", pal=dict(skin=SKIN["old"], hair=(200, 200, 200), coat=(90, 86, 80), mantle=(130, 120, 106), shirt=(206, 200, 186), trim=(54, 50, 44),
                                 hat=(150, 140, 110), trousers=(66, 62, 56), boot=(46, 40, 34), metal=(160, 160, 160)),
         face=dict(eye="old", iris=(90, 90, 90), brow="flat", mouth="frown"), hair=BALDISH, hat=dict(kind="kasa"), beard="long", hairline=27.0,
         build="old", upper=dict(mantle=dict(bottom=15.0, clasp=True)), lower="trousers", sleeve="wide", arms=HOLD_L_LOW, props={"prop_l": P2.rope_coil}),
    dict(anim="hold_r", pal=dict(skin=SKIN["tan"], hair=(40, 36, 34), coat=(40, 56, 70), vest=(180, 176, 168), shirt=(214, 210, 200), trim=(30, 40, 50),
                                 hat=(40, 56, 70), trousers=(50, 56, 64), boot=(46, 40, 34), metal=(170, 170, 170), scarf=(190, 186, 176)),
         face=dict(eye="calm", iris=(70, 80, 90), brow="stern", mouth="smirk"), hair=SHORT, hat=dict(kind="cap"), beard="stubble", hairline=27.4,
         build="stout", upper=dict(vest=True, scarf=True), lower="trousers", sleeve="rolled", props={"prop_r": P2.harpoon}),
])

# 選定 (2026-11-06、頁「外れの住人の体」): 17 人とも推奨のまま
PICK = {p: "a" for p in IDS}
PICK.update(smokewatch_reader="b", frostfort_fletcher="b")  # 煙読み: A の高い帽子は桶に見える。矢羽師: 背の矢筒の方が職がわかる
for _p, _k in PICK.items():
    C.CAST[f"guest_{_p}"] = dict(C.CAST[f"guest_{_p}_{_k}"], ja=CF.FOLK[_p])


if __name__ == "__main__":
    import build as B
    args = sys.argv[1:]
    if args and args[0] == "pick":
        ids = args[1:] or IDS
        B.main([f"guest_{p}" for p in ids])
        print("ok picked", len(ids))
    else:
        ids = args or IDS
        B.main([f"guest_{p}_{k}" for p in ids for k in "abc"])
        for p in ids:
            CF.sheet(p)
        print("ok", len(ids))
