"""NPC v2 の人の仕様。名前 = 入れる時のメッシュ名の後ろ (npc_<名>)。
pal: 色 (skin / hair / coat / ...)。face: 目・眉・口のドットの型 (kit.face_pixels)。hair / hat / beard: 形。upper / lower: 服の重ね。
arms: 腕の姿勢 {"r": (肘, 手首), "l": ...}。props: {受け口: 関数}。anim: 動きの型 (free / hold2 / hold_r / hold_l / shoulder_r / ground_r)。
色は会話の肖像 (tools/gen/mobs/gen_attendant_portraits.py の R、gen_town_guests.py の R) と揃える。"""
from __future__ import annotations

import props as PR
import props2 as P2
import props3 as P3

SKIN = {"pale": (232, 192, 160), "mid": (214, 166, 128), "tan": (190, 140, 100), "brown": (150, 102, 70), "ruddy": (222, 160, 128),
        "old": (214, 178, 150)}

# 腕の姿勢 (肘, 手首)。左 = +x
HOLD_L = {"l": ((5.0, 15.9, -1.4), (3.2, 15.0, -5.2))}            # 左手で胸に抱える
HOLD_L_LOW = {"l": ((5.3, 16.0, -0.6), (5.2, 13.0, -3.6))}        # 左手を前へ (提げる)
BIRD_L = {"l": ((5.3, 16.2, -0.8), (5.1, 16.4, -4.6))}            # 左の前腕を水平に (鳥を止める)
STAFF_R = {"r": ((-5.4, 16.0, -0.9), (-5.3, 13.4, -3.4))}         # 右手で杖を立てる
HOLD2 = {"r": ((-5.3, 16.4, -1.5), (-2.2, 15.4, -5.4)), "l": ((5.3, 16.4, -1.5), (2.2, 15.4, -5.4))}
HOLD2_LOW = {"r": ((-5.3, 16.2, -1.2), (-2.6, 13.6, -4.8)), "l": ((5.3, 16.2, -1.2), (2.6, 13.6, -4.8))}

CAST = {}


def add(name, **kw):
    CAST[name] = kw


def both(a, b):
    d = dict(a)
    d.update(b)
    return d


# ====================================================================== 都の係の人 (施設の番人)

add("guild_clerk", ja="組合の受付の書記", anim="hold_l",
    pal=dict(skin=SKIN["mid"], hair=(86, 62, 44), coat=(70, 84, 104), vest=(176, 138, 58), shirt=(214, 206, 186), trim=(56, 44, 34),
             metal=(200, 168, 84), trousers=(52, 50, 60), boot=(54, 40, 32), book=(120, 46, 40)),
    face=dict(eye="calm", iris=(84, 110, 140), brow="soft", mouth="smile"),
    hair=dict(style="short", front=27.6, side=25.4, back=23.8, widow=0.3, fringe=[(-1.4, 27.9, 1.4, 0.7, -0.4), (0.4, 28.0, 1.3, 0.7, 0.3)]),
    hairline=27.6, specs=True, build="slim",
    upper=dict(vest=True, collar=True, collar_mat="shirt", buttons=[13.2, 15.4, 17.6], pouches=[(3.4, 10.4, "pouch")]),
    lower="tails", sleeve="fitted", arms=HOLD_L, props={"prop_l": PR.ledger})

add("smith", ja="鍛冶師", anim="hold_r",
    pal=dict(skin=SKIN["tan"], hair=(36, 30, 30), coat=(66, 62, 64), shirt=(120, 110, 96), apron=(126, 76, 46), trim=(40, 30, 24), hat=(70, 52, 40),
             hat2=(70, 52, 40), trousers=(46, 42, 44), boot=(40, 30, 26), metal=(170, 98, 52), glove=(90, 60, 40), leather=(96, 62, 40)),
    face=dict(eye="narrow", iris=(70, 50, 36), brow="thick", mouth="neutral", marks=[(["w"], 4, 49)]),
    hair=dict(style="short", front=27.8, side=25.0, back=23.6, off=0.35, fringe=[]),
    hat=dict(kind="band"), beard="full", hairline=27.6, build="broad",
    upper=dict(apron=dict(bottom=5.2, top=18.8, w=3.4), collar=False, pouches=[(-3.6, 10.6, "pouch")]),
    lower="trousers", sleeve="rolled", gloves=False, arms={}, props={"prop_r": P2.hammer})

add("tool_merchant", ja="道具屋", anim="free",
    pal=dict(skin=SKIN["mid"], hair=(104, 74, 48), coat=(86, 96, 62), shirt=(170, 150, 108), trim=(92, 64, 40), hat=(120, 100, 64),
             hat2=(80, 64, 40), metal=(178, 142, 70), trousers=(70, 58, 44), boot=(60, 44, 30), leather=(116, 82, 50)),
    face=dict(eye="kind", iris=(96, 76, 44), brow="arched", mouth="grin", blush=0.15),
    hair=dict(style="short", front=27.2, side=24.8, back=23.8, fringe=[]),
    hat=dict(kind="cap"), beard="moustache", hairline=27.2, build="stout",
    upper=dict(placket=True, lapels=True, buttons=[14.0, 16.2], pouches=[(3.8, 10.4, "pouch"), (-3.8, 10.4, "pouch")]),
    lower="coat_long", sleeve="fitted", props={"prop_back": P2.tool_pack})

add("apothecary", ja="薬師", anim="hold_l",
    pal=dict(skin=SKIN["pale"], hair=(150, 146, 138), coat=(84, 110, 90), apron=(196, 200, 172), trim=(60, 86, 52), hat=(96, 130, 92),
             metal=(120, 150, 96), trousers=(60, 66, 56), boot=(56, 46, 38), glass=(110, 190, 150)),
    face=dict(eye="old", iris=(90, 120, 90), brow="soft", mouth="small", marks=[(["w."], 4, 47)]),
    hair=dict(style="short", front=27.6, side=24.8, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), specs=True, hairline=27.4, build="slim",
    upper=dict(apron=dict(bottom=4.0, top=17.6, w=3.0), collar=True, pouches=[(-3.4, 10.4, "vial"), (-1.8, 10.2, "vial"), (3.6, 10.4, "pouch")]),
    lower="coat_long", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P2.flask})

add("storage_keeper", ja="預かり番", anim="hold2",
    pal=dict(skin=SKIN["brown"], hair=(60, 46, 36), coat=(98, 76, 56), vest=(170, 92, 46), shirt=(180, 164, 130), trim=(70, 74, 80),
             metal=(120, 126, 134), trousers=(56, 50, 46), boot=(46, 36, 30)),
    face=dict(eye="calm", iris=(60, 44, 30), brow="flat", mouth="neutral"),
    hair=dict(style="short", front=28.0, side=25.2, back=24.0, off=0.35, fringe=[]),
    beard="full", hairline=28.0, build="stout",
    upper=dict(vest=True, buttons=[14.4, 16.6, 18.8], pouches=[(3.8, 10.4, "keys")]),
    lower="trousers", sleeve="rolled", arms=HOLD2_LOW, props={"prop_r": P2.crate})

add("innkeeper", ja="宿の女将", anim="hold_l",
    pal=dict(skin=SKIN["ruddy"], hair=(150, 62, 46), coat=(122, 58, 56), skirt=(110, 52, 50), apron=(222, 210, 180), trim=(150, 62, 56),
             hat=(176, 70, 56), shirt=(226, 214, 190), metal=(206, 170, 96), trousers=(60, 40, 40), boot=(70, 44, 36)),
    face=dict(eye="lady", iris=(120, 70, 50), brow="arched", mouth="lips", blush=0.3, lip=(178, 70, 70)),
    hair=dict(style="bun", front=27.8, side=24.8, back=23.8, fringe=[(-2.0, 28.0, 1.6, 0.7, -0.5), (1.6, 28.0, 1.4, 0.65, 0.5)]),
    hat=dict(kind="kerchief"), hairline=27.8, build="lady",
    upper=dict(apron=dict(bottom=4.6, top=17.0, w=3.2), collar=True, collar_mat="shirt"),
    lower="skirt", sleeve="puff", arms=HOLD_L, props={"prop_l": P2.tray})

add("perch_keeper", ja="止まり木の番", anim="hold_l",
    pal=dict(skin=SKIN["tan"], hair=(120, 92, 62), coat=(98, 120, 140), mantle=(212, 218, 214), trim=(54, 118, 124), metal=(90, 160, 166),
             trousers=(70, 70, 76), boot=(60, 48, 38), feather=(230, 230, 220), leather=(120, 90, 60)),
    face=dict(eye="sharp", iris=(70, 130, 140), brow="flat", mouth="smirk"),
    hair=dict(style="ponytail", front=27.6, side=25.0, back=23.8, fringe=[(1.8, 28.0, 1.8, 0.7, 0.4)]),
    hairline=27.6, build="slim",
    upper=dict(mantle=dict(bottom=16.4, clasp=True), buttons=[13.0, 15.0], belt=True),
    lower="tails", sleeve="fitted", arms=BIRD_L, props={"prop_l": P2.bird})

add("shrine_priest", ja="社の祭司", anim="hold_r",
    pal=dict(skin=SKIN["old"], hair=(226, 222, 212), coat=(222, 218, 206), skirt=(222, 218, 206), mantle=(176, 56, 44), trim=(214, 176, 80),
             sash=(176, 56, 44), hat=(40, 36, 40), hat2=(214, 176, 80), metal=(214, 176, 80), trousers=(200, 196, 186), boot=(60, 50, 44)),
    face=dict(eye="old", iris=(110, 90, 70), brow="none", mouth="neutral", marks=[(["w"], 4, 47), (["ww"], 0, 54)]),
    hair=dict(style="short", front=27.0, side=24.6, back=23.6, fringe=[]),
    hat=dict(kind="tall"), brow3d=dict(mat="brow", r=0.5, arch=0.1), beard="long", hairline=26.8, build="old",
    upper=dict(mantle=dict(bottom=17.0, clasp=True), sash=True, collar=True, collar_mat="trim"),
    lower="robe", sleeve="wide", arms=STAFF_R, props={"prop_r": P2.bell_staff})

add("training_master", ja="師範", anim="hold_r",
    pal=dict(skin=SKIN["tan"], hair=(40, 34, 32), coat=(214, 206, 184), skirt=(48, 60, 104), sash=(40, 44, 60), trim=(170, 160, 136), hat=(214, 206, 184),
             trousers=(48, 60, 104), boot=(50, 40, 34), tie=(214, 206, 184)),
    face=dict(eye="sharp", iris=(50, 40, 34), brow="stern", mouth="neutral"),
    hair=dict(style="topknot", front=28.0, side=25.2, back=23.8, off=0.35, fringe=[]),
    hat=dict(kind="band", mat="hat"), beard="stubble", hairline=27.8, build="broad",
    face_extra=dict(stubble=True),
    upper=dict(sash=True, lapels=True, collar=False),
    lower="skirt", flare=1.6, sleeve="wide", arms={}, props={"prop_r": P2.bokken})

add("black_marketeer", ja="顔を隠した商人", anim="hold_l",
    pal=dict(skin=SKIN["mid"], hair=(28, 26, 32), coat=(36, 34, 40), mantle=(68, 44, 68), trim=(120, 104, 70), hat=(44, 38, 50), hat2=(68, 44, 68),
             metal=(120, 104, 70), trousers=(30, 28, 34), boot=(26, 22, 24)),
    face=dict(eye="sharp", iris=(190, 160, 80), brow="stern", mouth="none", mask=(70, 58, 78)),
    hair=dict(style="short", front=27.6, side=25.0, back=23.8, fringe=[(-1.6, 27.9, 2.0, 0.75, -0.3)]),
    hat=dict(kind="hood"), hairline=27.6, build="slim",
    upper=dict(mantle=dict(bottom=15.0, clasp=True), belt=True, pouches=[(3.6, 10.4, "pouch"), (-3.6, 10.4, "pouch")]),
    lower="coat_long", sleeve="fitted", gloves=True, arms=HOLD_L_LOW, props={"prop_l": P2.coin_pouch})

add("gravekeeper", ja="墓守", anim="hold_r",
    pal=dict(skin=SKIN["pale"], hair=(176, 174, 168), coat=(92, 92, 88), scarf=(74, 86, 70), trim=(50, 52, 50), hat=(62, 62, 60), hat2=(40, 40, 40),
             metal=(150, 146, 132), trousers=(52, 52, 50), boot=(40, 38, 36)),
    face=dict(eye="sleepy", iris=(110, 120, 110), brow="worried", mouth="frown", marks=[(["w"], 4, 47), (["w.w"], 3, 52)]),
    hair=dict(style="long", front=27.0, side=24.4, back=23.0, fringe=[(-2.4, 27.2, 2.4, 0.7, -0.4), (2.2, 27.2, 2.2, 0.7, 0.4)]),
    hat=dict(kind="brim"), hairline=27.0, build="old",
    upper=dict(scarf=True, lapels=True, buttons=[13.4, 15.6]),
    lower="coat_long", sleeve="fitted", arms=STAFF_R, props={"prop_r": P2.shovel})

add("drunkard", ja="酔いどれ", anim="hold_r",
    pal=dict(skin=SKIN["ruddy"], hair=(96, 76, 58), coat=(110, 92, 70), vest=(122, 62, 78), shirt=(170, 156, 126), trim=(76, 60, 46), metal=(140, 120, 80),
             trousers=(70, 60, 50), boot=(54, 42, 34)),
    face=dict(eye="sleepy", iris=(100, 80, 60), brow="worried", mouth="grin", blush=0.45, drunk=True, stubble=True),
    hair=dict(style="short", front=27.4, side=24.8, back=23.4, fringe=[(-2.2, 27.8, 2.0, 0.8, -0.6), (0.8, 27.9, 2.2, 0.8, 0.4), (2.6, 27.4, 1.8, 0.7, 0.7)]),
    beard="stubble", hairline=27.4, build="stout",
    upper=dict(vest=True, buttons=[15.0], pouches=[(-3.8, 10.4, "pouch")]),
    lower="trousers", sleeve="rolled", props={"prop_r": P2.bottle})

add("trade_keeper", ja="取引所の番", anim="hold_l",
    pal=dict(skin=SKIN["mid"], hair=(70, 52, 40), coat=(44, 84, 82), vest=(196, 160, 82), shirt=(220, 212, 190), trim=(58, 40, 28), hat=(44, 84, 82),
             hat2=(196, 160, 82), metal=(214, 184, 104), trousers=(44, 52, 56), boot=(48, 36, 28)),
    face=dict(eye="calm", iris=(70, 100, 90), brow="flat", mouth="smirk"),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[]),
    hat=dict(kind="cap"), specs=True, beard="moustache", hairline=27.2, build="avg",
    upper=dict(vest=True, collar=True, collar_mat="shirt", buttons=[13.0, 15.0, 17.0], pouches=[(3.6, 10.4, "pouch")]),
    lower="tails", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P2.scales})

# 都の案内人 (門と広場に立つ。話すと施設の一覧 → 選んだ施設の戸まで色つきの灯りの道。docs/design/35 の 6.12)。
# 白い外套 (長い上着 + 肩掛け + 背へ下ろした頭巾)、金の縁、角灯を提げた杖に四色の房
add("guide", ja="都の案内人", anim="hold_r",
    pal=dict(skin=SKIN["mid"], hair=(70, 52, 40), coat=(232, 230, 222), mantle=(242, 240, 234), trim=(196, 162, 78), sash=(70, 108, 150),
             metal=(206, 170, 90), metal2=(96, 90, 84), trousers=(120, 112, 100), boot=(76, 56, 40), leather=(118, 86, 54)),
    face=dict(eye="kind", iris=(80, 110, 130), brow="soft", mouth="smile", blush=0.12),
    hair=dict(style="short", front=27.6, side=25.0, back=23.8, fringe=[(-1.6, 27.9, 1.6, 0.7, -0.4), (0.6, 28.0, 1.5, 0.7, 0.3)]),
    hairline=27.6, build="avg",
    upper=dict(mantle=dict(bottom=16.2, clasp=True, hood_down=True), sash=True, collar=True, collar_mat="trim", pouches=[(3.6, 10.4, "pouch")]),
    lower="coat_long", sleeve="fitted", arms=STAFF_R, props={"prop_r": P2.guide_staff})

# ====================================================================== 深淵の生き残り (探掘隊)

add("survivor_bone", ja="灯り番ミオ", anim="hold2",
    pal=dict(skin=SKIN["pale"], hair=(92, 60, 42), coat=(126, 106, 80), skirt=(116, 96, 72), shirt=(196, 182, 150), trim=(64, 46, 32),
             sash=(130, 52, 40), scarf=(90, 80, 72), trousers=(60, 52, 48), boot=(50, 38, 30), metal=(170, 150, 110), metal2=(70, 66, 66), tie=(130, 52, 40)),
    face=dict(eye="lady", iris=(150, 110, 60), brow="worried", mouth="lips", blush=0.25, lip=(168, 92, 84),
              marks=[(["..f", "f.."], 4, 48)]),
    hair=dict(style="braid", front=27.8, side=24.6, back=23.6, fringe=[(-2.2, 28.0, 1.8, 0.7, -0.5), (-0.6, 28.1, 1.6, 0.7, -0.2), (1.2, 28.0, 1.5, 0.65, 0.4)]),
    hairline=27.8, build="lady",
    upper=dict(sash=True, scarf=True, scarf_mat="scarf"), lower="robe", sleeve="fitted",
    arms=HOLD2, props={"prop_r": PR.mio_lantern})

add("survivor_salt", ja="測量士ハヤセ", anim="ground_r",
    pal=dict(skin=SKIN["mid"], hair=(150, 96, 56), coat=(66, 88, 98), skirt=(70, 92, 100), sash=(150, 70, 50), shirt=(190, 168, 120), trim=(40, 52, 76),
             scarf=(190, 168, 120), trousers=(52, 56, 64), boot=(46, 38, 32), metal=(186, 146, 70), leather=(110, 80, 50)),
    face=dict(eye="round", iris=(80, 110, 130), brow="worried", mouth="neutral", marks=[(["f.f"], 4, 49)]),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[(-1.8, 27.9, 2.0, 0.75, -0.4), (0.2, 28.0, 2.2, 0.8, 0.1), (2.0, 27.9, 1.8, 0.7, 0.5)]),
    hairline=27.4, specs=True, build="slim",
    upper=dict(sash=True, scarf=True, bandolier=True, pouches=[(3.6, 10.4, "book")]),
    lower="robe", sleeve="fitted",
    arms={"r": ((-6.0, 16.4, -2.0), (-5.6, 14.0, -4.0)), "l": ((5.8, 16.0, -2.2), (3.6, 14.8, -5.4))},
    props={"prop_r": PR.hayase_tripod, "prop_l": PR.hayase_board})

add("survivor_storm", ja="薬師シズ", anim="free",
    pal=dict(skin=SKIN["pale"], hair=(60, 56, 66), coat=(86, 108, 98), skirt=(80, 102, 92), apron=(196, 200, 172), sash=(70, 104, 70), trim=(60, 86, 52),
             hat=(200, 190, 150), hat2=(150, 136, 100), cord=(120, 90, 60), trousers=(60, 66, 60), boot=(50, 44, 38), tie=(70, 104, 70)),
    face=dict(eye="lady", iris=(80, 130, 110), brow="soft", mouth="small", blush=0.2),
    hair=dict(style="long", front=27.6, side=24.4, back=23.4, fringe=[(-2.4, 27.8, 2.6, 0.75, -0.5), (2.4, 27.8, 2.6, 0.75, 0.5), (0.0, 28.0, 1.6, 0.7, 0.0)]),
    hat=dict(kind="kasa"), hairline=27.6, build="lady",
    upper=dict(sash=True, apron=dict(bottom=3.0, top=17.0, w=2.8), pouches=[(-3.4, 10.4, "vial")]),
    lower="robe", sleeve="fitted", props={"prop_back": PR.shizu_basket})

add("survivor_ash", ja="隊長トウヤ", anim="shoulder_r",
    pal=dict(skin=SKIN["tan"], hair=(110, 104, 100), coat=(74, 70, 68), apron=(124, 40, 38), trim=(150, 154, 160), metal=(170, 174, 180), metal2=(110, 112, 118),
             trousers=(52, 50, 50), boot=(40, 34, 30), leather=(80, 60, 44)),
    face=dict(eye="sharp", iris=(110, 100, 90), brow="stern", mouth="frown", stubble=True, marks=[(["w"], 4, 47), (["S", "S"], 2, 47, 1)]),
    hair=dict(style="short", front=28.0, side=25.0, back=23.8, off=0.35, fringe=[(1.6, 28.2, 1.4, 0.6, 0.5)]),
    hairline=27.8, build="broad",
    upper=dict(apron=dict(bottom=6.4, top=19.0, w=3.0), pauldron=True, rope_coil=True, collar=True, collar_mat="trim"),
    lower="trousers", sleeve="fitted", gloves=True,
    arms={"r": ((-6.4, 17.4, -1.2), (-4.8, 21.0, -3.0))}, props={"prop_r": PR.toya_pick})

add("abyss_porter", ja="荷運びロク", anim="free",
    pal=dict(skin=SKIN["brown"], hair=(104, 74, 48), coat=(98, 90, 66), mantle=(168, 148, 108), trim=(92, 64, 40), hat=(168, 148, 108), metal=(178, 142, 70),
             trousers=(70, 60, 46), boot=(56, 42, 30), leather=(110, 82, 54)),
    face=dict(eye="kind", iris=(80, 60, 40), brow="thick", mouth="grin"),
    hair=dict(style="short", front=27.8, side=25.0, back=23.8, fringe=[(-1.0, 28.1, 1.4, 0.7, -0.2), (1.2, 28.1, 1.4, 0.7, 0.3)]),
    hat=dict(kind="band", mat="hat"), beard="stubble", hairline=27.8, build="stout",
    upper=dict(mantle=dict(bottom=17.4, clasp=True), belt=True, pouches=[(3.8, 10.4, "pouch"), (-3.8, 10.4, "pouch")]),
    lower="tails", sleeve="rolled", props={"prop_back": PR.roku_sack})

# ====================================================================== 町の人 (クエストの限定の人)

add("guest_apprentice", ja="鯨追いの見習い", anim="hold_r",
    pal=dict(skin=SKIN["mid"], hair=(60, 44, 34), coat=(92, 80, 62), vest=(70, 110, 130), shirt=(190, 176, 146), trim=(60, 44, 30), hat=(70, 110, 130),
             metal=(180, 150, 90), trousers=(60, 54, 46), boot=(56, 40, 30)),
    face=dict(eye="round", iris=(70, 90, 110), brow="high", mouth="grin", marks=[([".f.f", "f.f."], 3, 49)]),
    hair=dict(style="short", front=27.6, side=25.0, back=23.6, fringe=[(-1.8, 28.0, 1.8, 0.8, -0.5), (0.0, 28.1, 2.0, 0.8, 0.0), (1.8, 28.0, 1.8, 0.8, 0.5)]),
    hat=dict(kind="band"), hairline=27.6, build="slim", head_k=1.15,
    upper=dict(vest=True, pouches=[(3.6, 10.4, "pouch")]),
    lower="trousers", sleeve="rolled", arms=STAFF_R, props={"prop_r": P2.harpoon})

add("guest_bellkeeper", ja="鐘守", anim="hold_l",
    pal=dict(skin=SKIN["old"], hair=(214, 210, 202), coat=(70, 64, 80), skirt=(70, 64, 80), mantle=(150, 120, 60), trim=(196, 160, 80), metal=(196, 160, 80),
             sash=(120, 96, 50), trousers=(60, 56, 66), boot=(46, 40, 40)),
    face=dict(eye="kind", iris=(100, 100, 120), brow="none", mouth="smile", marks=[(["w"], 4, 47)]),
    hair=dict(style="bald"), brow3d=dict(mat="brow", r=0.48), beard="long", hairline=None, build="old",
    upper=dict(mantle=dict(bottom=17.6, clasp=True), sash=True),
    lower="robe", sleeve="wide", arms=HOLD_L_LOW, props={"prop_l": P2.handbell})

add("guest_fisher", ja="浜を捨てた漁師", anim="hold_r",
    pal=dict(skin=SKIN["tan"], hair=(90, 70, 50), coat=(60, 84, 96), apron=(196, 170, 90), shirt=(180, 170, 150), trim=(40, 56, 64), hat=(60, 84, 96),
             metal=(120, 140, 150), trousers=(56, 62, 66), boot=(40, 46, 50)),
    face=dict(eye="narrow", iris=(80, 100, 110), brow="flat", mouth="neutral", marks=[(["w"], 4, 49)]),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), beard="full", hairline=27.4, build="avg",
    upper=dict(apron=dict(bottom=4.0, top=18.0, w=3.2)),
    lower="trousers", sleeve="rolled", boots="boot", props={"prop_r": P2.fishing_rod})

add("guest_bard", ja="旅の吟遊詩人", anim="free",
    pal=dict(skin=SKIN["pale"], hair=(170, 120, 70), coat=(120, 40, 60), vest=(210, 180, 110), shirt=(226, 214, 190), trim=(70, 30, 40), hat=(60, 40, 90),
             hat2=(220, 200, 120), mantle=(60, 40, 90), metal=(220, 200, 120), trousers=(60, 40, 70), boot=(80, 50, 40), feather=(230, 80, 70)),
    face=dict(eye="glint", iris=(70, 130, 90), brow="arched", mouth="smile", blush=0.15),
    hair=dict(style="long", front=27.4, side=24.4, back=23.0, fringe=[(-2.2, 27.8, 2.4, 0.8, -0.5), (2.0, 27.8, 2.2, 0.8, 0.5)]),
    hat=dict(kind="beret", feather=True), beard="goatee", hairline=27.4, build="slim",
    upper=dict(vest=True, mantle=dict(bottom=18.2, clasp=True), collar=True, collar_mat="shirt"),
    lower="tails", sleeve="puff", props={"prop_back": P2.lute})

add("guest_child", ja="雷を怖がる子", anim="hold_l",
    pal=dict(skin=SKIN["pale"], hair=(120, 80, 50), coat=(160, 120, 90), skirt=(160, 120, 90), apron=(200, 190, 170), trim=(120, 84, 60), shirt=(220, 210, 190),
             tie=(180, 90, 80), trousers=(150, 120, 100), boot=(90, 60, 44)),
    face=dict(eye="child", iris=(90, 70, 50), brow="worried", mouth="o", blush=0.35),
    hair=dict(style="sidelocks", front=27.8, side=24.6, back=23.4, fringe=[(-1.6, 28.1, 1.8, 0.75, -0.3), (0.2, 28.2, 1.9, 0.8, 0.0), (1.8, 28.1, 1.7, 0.75, 0.4)]),
    hairline=27.8, build="slim", head_k=1.28, head="round",
    upper=dict(apron=dict(bottom=5.0, top=16.6, w=2.8), collar=True, collar_mat="shirt"),
    lower="skirt", sleeve="puff", arms=HOLD_L, props={"prop_l": P2.doll})

add("guest_weaver", ja="機織り", anim="hold_l",
    pal=dict(skin=SKIN["mid"], hair=(60, 40, 40), coat=(90, 100, 140), skirt=(90, 100, 140), apron=(200, 170, 150), trim=(60, 66, 100), hat=(170, 80, 90),
             shirt=(214, 200, 180), metal=(200, 170, 110), trousers=(70, 60, 70), boot=(60, 44, 40)),
    face=dict(eye="lady", iris=(60, 50, 40), brow="soft", mouth="lips", lip=(170, 80, 90), blush=0.2),
    hair=dict(style="braid", front=27.6, side=24.6, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), hairline=27.6, build="lady",
    upper=dict(apron=dict(bottom=4.4, top=16.8, w=3.0), collar=True, collar_mat="shirt", scarf=False),
    lower="skirt", sleeve="fitted", arms=HOLD_L, props={"prop_l": P2.cloth_bolt})

add("guest_ash_apprentice", ja="灰を運ぶ弟子", anim="hold_r",
    pal=dict(skin=SKIN["tan"], hair=(40, 30, 28), coat=(64, 58, 56), apron=(150, 70, 40), shirt=(110, 100, 92), trim=(36, 32, 30), hat=(90, 80, 76),
             metal=(220, 120, 50), trousers=(50, 46, 44), boot=(36, 30, 28), glove=(100, 70, 46)),
    face=dict(eye="calm", iris=(60, 46, 36), brow="flat", mouth="neutral", marks=[(["e.e", ".e."], 3, 48)]),
    hair=dict(style="short", front=27.6, side=25.0, back=23.6, fringe=[(-1.0, 28.0, 1.6, 0.7, -0.2), (1.0, 28.0, 1.6, 0.7, 0.3)]),
    hat=dict(kind="band"), hairline=27.6, build="slim", head_k=1.15,
    upper=dict(apron=dict(bottom=5.2, top=18.4, w=3.0)),
    lower="trousers", sleeve="rolled", gloves=True, props={"prop_r": P2.bucket})

add("guest_herbalist", ja="薬草摘みの老婆", anim="hold_l",
    pal=dict(skin=SKIN["old"], hair=(200, 198, 190), coat=(88, 100, 70), skirt=(88, 100, 70), apron=(150, 120, 90), trim=(60, 70, 48), hat=(110, 130, 80),
             shirt=(200, 190, 160), metal=(140, 160, 90), trousers=(70, 70, 56), boot=(56, 46, 36), scarf=(140, 110, 80)),
    face=dict(eye="kind", iris=(90, 110, 80), brow="soft", mouth="smile", blush=0.2, marks=[(["w"], 4, 47), (["w.w"], 3, 52)]),
    hair=dict(style="bun", front=27.4, side=24.6, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), hairline=27.2, build="old",
    upper=dict(apron=dict(bottom=4.6, top=16.4, w=3.0), scarf=True),
    lower="skirt", sleeve="fitted", arms=HOLD_L_LOW, props={"prop_l": P2.herb_basket_arm})

add("guest_sailor", ja="年寄りの船乗り", anim="hold_l",
    pal=dict(skin=SKIN["old"], hair=(216, 212, 204), coat=(44, 60, 90), shirt=(210, 200, 180), vest=(210, 200, 180), trim=(30, 40, 62), hat=(44, 60, 90),
             metal=(190, 160, 90), trousers=(40, 48, 70), boot=(40, 36, 34), scarf=(160, 60, 50)),
    face=dict(eye="narrow", iris=(80, 110, 140), brow="none", mouth="smirk", marks=[(["w"], 4, 47)]),
    hair=dict(style="short", front=27.2, side=24.6, back=23.4, fringe=[]),
    hat=dict(kind="kerchief"), brow3d=dict(mat="brow", r=0.45), beard="full", hairline=27.0, build="stout",
    upper=dict(vest=True, scarf=True, lapels=True, buttons=[13.0, 15.0]),
    pal_extra=dict(vest_stripe=2),
    lower="trousers", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P2.rope_coil})

add("guest_mother", ja="子だくさんの母", anim="hold2",
    pal=dict(skin=SKIN["mid"], hair=(110, 70, 44), coat=(150, 90, 70), skirt=(150, 90, 70), apron=(220, 210, 180), trim=(100, 60, 46), hat=(90, 110, 70),
             shirt=(226, 214, 190), metal=(200, 170, 110), trousers=(80, 60, 50), boot=(70, 50, 40)),
    face=dict(eye="kind", iris=(100, 70, 50), brow="soft", mouth="smile", blush=0.3, lip=(180, 90, 80)),
    hair=dict(style="bun", front=27.6, side=24.6, back=23.6, fringe=[(-2.0, 27.9, 1.6, 0.7, -0.5)]),
    hat=dict(kind="kerchief"), hairline=27.6, build="lady",
    upper=dict(apron=dict(bottom=4.6, top=16.8, w=3.2), collar=True, collar_mat="shirt"),
    lower="skirt", sleeve="rolled", arms=HOLD2, props={"prop_r": P2.baby})

add("guest_baker", ja="パン焼き", anim="hold_l",
    pal=dict(skin=SKIN["pale"], hair=(150, 100, 60), coat=(210, 196, 170), apron=(150, 100, 60), shirt=(230, 220, 200), trim=(160, 140, 110), hat=(220, 214, 200),
             metal=(200, 140, 70), trousers=(120, 100, 80), boot=(70, 50, 36)),
    face=dict(eye="kind", iris=(100, 80, 60), brow="soft", mouth="grin", blush=0.3),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), beard="moustache", hairline=27.2, build="stout",
    upper=dict(apron=dict(bottom=4.8, top=18.0, w=3.4), collar=True, collar_mat="shirt"),
    lower="trousers", sleeve="rolled", arms=HOLD_L, props={"prop_l": P2.bread_basket})


# ====================================================================== 市の三人 (食料屋・武具屋・素材屋。2026-10-27 に選んだ)
# 食料屋 A 腸詰めを提げた肉屋 / B 杓子の汁売り / C 乾酪の輪を抱えた女
add("food_vendor", ja="食料屋", anim="hold_l",
    pal=dict(skin=SKIN["ruddy"], hair=(90, 60, 40), coat=(150, 74, 58), apron=(226, 214, 190), shirt=(220, 206, 178), trim=(96, 50, 40), hat=(226, 214, 190),
             metal=(190, 150, 80), trousers=(66, 52, 44), boot=(54, 40, 30)),
    face=dict(eye="kind", iris=(96, 70, 50), brow="thick", mouth="grin", blush=0.3),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[]),
    hat=dict(kind="kerchief"), beard="full", hairline=27.2, build="broad",
    upper=dict(apron=dict(bottom=4.6, top=18.4, w=3.6), collar=True, collar_mat="shirt"),
    lower="trousers", sleeve="rolled", arms=HOLD_L_LOW, props={"prop_l": P3.sausages})

add("outfitter", ja="武具屋", anim="free",
    pal=dict(skin=SKIN["tan"], hair=(52, 42, 36), coat=(78, 70, 66), apron=(104, 70, 44), shirt=(160, 146, 120), trim=(48, 42, 40), metal=(112, 114, 120),
             metal2=(150, 152, 158), trousers=(54, 50, 50), boot=(44, 36, 32), leather=(104, 70, 44), glove=(90, 62, 42)),
    face=dict(eye="narrow", iris=(80, 76, 70), brow="thick", mouth="smirk", marks=[(["S", "S", "S"], 5, 52, -1)]),
    hair=dict(style="short", front=28.2, side=25.0, back=24.0, off=0.3, fringe=[]),
    beard="full", hairline=28.0, build="broad",
    upper=dict(apron=dict(bottom=5.4, top=19.4, w=3.6, bib=True), pauldron=True, collar=True, collar_mat="metal2",
               pouches=[(-3.8, 10.6, "pouch")]),
    lower="trousers", sleeve="rolled", gloves=True, arms={}, props={})

# 素材屋 (選び: C 鉱石を見る目利きの老女)
add("material_trader", ja="素材屋", anim="hold_l",
    pal=dict(skin=SKIN["old"], hair=(206, 202, 196), coat=(60, 76, 86), skirt=(56, 70, 80), sash=(176, 140, 70), trim=(150, 120, 60), hat=(60, 76, 86),
             metal=(190, 160, 90), trousers=(50, 56, 62), boot=(44, 38, 36)),
    face=dict(eye="old", iris=(100, 130, 140), brow="worried", mouth="small", marks=[(["w"], 4, 47)]),
    hair=dict(style="bun", front=27.2, side=24.6, back=23.6, fringe=[]),
    specs=True, hairline=27.0, build="old",
    upper=dict(sash=True, collar=True, collar_mat="trim", mantle=dict(bottom=17.4, clasp=True)),
    lower="robe", sleeve="wide", arms=HOLD_L, props={"prop_l": P3.ore_lump})


# 封切り師 (封の箱を開ける人。docs/design/52 の 5 章)。封蝋の匠: 煤けた赤茶の上着に生成りの前掛け、腰に小刀と封蝋の棒の袋、
# 左手で封の箱を抱える。眼鏡越しに封を見る年配の職人
add("box_opener", ja="封切り師", anim="hold_l",
    pal=dict(skin=SKIN["ruddy"], hair=(150, 140, 132), coat=(112, 52, 42), apron=(214, 200, 168), shirt=(196, 184, 160), trim=(64, 36, 28),
             metal=(206, 168, 86), trousers=(56, 46, 42), boot=(48, 34, 26), leather=(110, 74, 46), wax=(170, 34, 38)),
    face=dict(eye="calm", iris=(110, 84, 60), brow="flat", mouth="smirk", marks=[(["w"], 4, 47)]),
    hair=dict(style="short", front=27.2, side=24.6, back=23.4, fringe=[]),
    specs=True, beard="moustache", hairline=27.0, build="avg",
    upper=dict(apron=dict(bottom=6.4, top=17.6, w=3.1), collar=True, collar_mat="trim", buttons=[18.6],
               pouches=[(-3.8, 10.4, "pouch"), (3.8, 10.6, "pouch")]),
    lower="trousers", sleeve="rolled", arms=HOLD_L, props={"prop_l": P3.sealed_box})


# ====================================================================== 旅の職人 (章ごとに絵だけ替える: variants)

def goggles(c):
    """額へ押し上げた眼鏡 (革の帯と丸いレンズ二つ)。"""
    import kit as K
    hs = c["_hs"]
    F = []
    F += K.part("gog_strap", K.shell("head", "leather", hs, lambda a: 27.7, 28.5, 0.75, top_off=0.75, n=16, rows=1))
    zf = K.prof_at(hs, 28.4)[1]
    for s in (-1, 1):
        cen = K.V(s * 1.55, 28.45, zf - 0.75)
        F += K.part(f"lens{s}", K.disc("head", "glass", cen, 0.95, 0.35, n=8, axis="z"))
        F += K.part(f"lens_rim{s}", K.disc("head", "metal", cen + K.V(0, 0, 0.12), 1.15, 0.32, n=8, axis="z"))
    return F


add("artisan", ja="旅の職人", anim="hold_r", group="artisan",
    pal=dict(skin=SKIN["mid"], hair=(42, 38, 44), coat=(68, 86, 112), apron=(116, 126, 138), shirt=(156, 148, 124), trim=(38, 34, 40), metal=(132, 136, 144),
             trousers=(38, 34, 40), boot=(30, 24, 20), leather=(100, 72, 46), glass=(120, 150, 160)),
    variants={"ch1": dict(apron=(116, 126, 138), trim=(38, 34, 40), metal=(148, 158, 168)),
              "ch2": dict(apron=(200, 206, 204), trim=(38, 34, 40), metal=(228, 232, 226)),
              "ch3": dict(apron=(90, 66, 46), trim=(166, 96, 48), metal=(210, 136, 76)),
              "ch4": dict(apron=(60, 50, 46), trim=(38, 34, 32), metal=(206, 110, 30))},
    face=dict(eye="kind", iris=(80, 96, 110), brow="flat", mouth="smile"),
    hair=dict(style="short", front=27.4, side=24.8, back=23.6, fringe=[(-1.2, 27.8, 1.4, 0.7, -0.3), (1.0, 27.8, 1.4, 0.7, 0.3)]),
    beard="stubble", hairline=27.4, build="avg",
    upper=dict(apron=dict(bottom=6.0, top=18.6, w=3.2), collar=True, collar_mat="trim", buttons=[19.4],
               pouches=[(-3.8, 10.4, "pouch"), (3.8, 10.4, "pouch")]),
    lower="tails", sleeve="fitted", gloves=True, extra_body=[goggles], props={"prop_r": P2.hammer})
