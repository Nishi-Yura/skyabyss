# -*- coding: utf-8 -*-
"""技の変異 (rpg/skill/Mutation) の絵 (icons3.png、256x64、32x32 の升目 8x2) を書く。Skill の絵の番号 200 + n (SkillHud.drawIcon)。
2026-11-09 の声「武器やスキルツリーで、スキル変化あるけど、アイコンとかも変える？」→ 案 A: 形が変わる変異だけ専用の絵。
刻印・書き換え・秘伝は元の絵のまま、縁の色で示す (SkillHud.changeFrame)。

    python -I tools/gen/ui/gen_mutation_icons.py

並びは Mutation の並びと同じ (0 断崖 … 9 凍て海)。縁は左上が巨神の色、右下が変異の紫。地は少し紫がかった暗い色。
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from gen_deep_skill_assets import TAG, WHITE, arc, dot, line, medallion, put, rgba, ring, shade  # noqa: E402

ROOT = HERE.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill/icons3.png"

MUT = (200, 160, 255)
DISC = (36, 32, 54)
FIRE = (255, 138, 74)
HOT = (255, 214, 120)
BOLT = (255, 236, 140)
ICE = (190, 230, 255)


def cleave_cliff(i):
    # 断崖: 地面から突き上がる岩の壁と、その頂を割る刃
    r = TAG["rock"]
    put(i, [(5, 25), (9, 14), (12, 17), (15, 8), (19, 13), (22, 10), (27, 25)], rgba(shade(r, 0.7)))
    put(i, [(8, 25), (11, 17), (14, 19), (16, 11), (19, 16), (24, 25)], rgba(r))
    line(i, [(16, 12), (17, 18), (15, 24)], rgba(shade(r, 0.4)), 1)
    line(i, [(9, 6), (23, 20)], WHITE, 2)
    put(i, [(7, 4), (11, 5), (9, 8)], WHITE)
    line(i, [(5, 26), (27, 26)], rgba(MUT), 1)


def sweep_tail(i):
    # 鯨尾の地鳴らし: 振り下ろす尾びれと、地を走る揺れの輪
    r = TAG["rock"]
    put(i, [(16, 6), (20, 12), (18, 16), (16, 14), (14, 16), (12, 12)], rgba(r))
    put(i, [(7, 4), (14, 8), (16, 6), (18, 8), (25, 4), (20, 11), (12, 11)], rgba(shade(r, 0.8)))
    line(i, [(16, 15), (16, 19)], rgba(r), 3)
    arc(i, (7, 18, 25, 28), 180, 360, rgba(MUT), 1)
    arc(i, (3, 16, 29, 30), 190, 350, rgba(MUT, 150), 1)
    line(i, [(8, 23), (24, 23)], rgba(shade(r, 0.6)), 2)


def rend_blade(i):
    # 飛ぶ刃: 三日月の刃 (塗った円を地の色の円で欠く) が尾を引いて飛ぶ
    w = TAG["wind"]
    dot(i, 18, 16, rgba(w), 8)
    dot(i, 14, 16, rgba(DISC), 8)
    dot(i, 19, 16, WHITE, 1)
    for k, y in enumerate((11, 16, 21)):
        line(i, [(4 + k * 2, y), (10, y)], rgba(w, 200 - k * 50))
    dot(i, 25, 9, rgba(MUT), 1)


def rain_comet(i):
    # 雷星: 雷の尾を引いて落ちる星
    dot(i, 20, 20, rgba(BOLT), 4)
    dot(i, 20, 20, WHITE, 2)
    put(i, [(8, 6), (13, 9), (11, 11), (17, 15), (14, 15), (17, 18), (10, 13), (12, 12)], rgba(BOLT, 210))
    line(i, [(6, 10), (16, 18)], rgba(MUT, 160), 1)
    line(i, [(11, 4), (19, 15)], rgba(MUT, 120), 1)
    for x, y in ((24, 13), (26, 22), (14, 25)):
        i.putpixel((x, y), WHITE)


def ebb_whirl(i):
    # 渦潮: 内へ巻く渦
    t = TAG["tide"]
    arc(i, (5, 5, 27, 27), 0, 300, rgba(shade(t, 0.8)), 2)
    arc(i, (9, 9, 23, 23), 60, 360, rgba(t), 2)
    arc(i, (13, 13, 19, 19), 120, 420, WHITE, 1)
    dot(i, 16, 16, rgba(MUT), 1)


def lunge_surge(i):
    # 潮走り: 波頭に乗って踏み込む刃
    t = TAG["tide"]
    put(i, [(4, 24), (10, 16), (16, 13), (22, 14), (25, 18), (20, 17), (16, 20), (12, 24)], rgba(t))
    arc(i, (14, 10, 26, 22), 200, 330, WHITE, 1)
    line(i, [(12, 12), (27, 7)], WHITE, 2)
    put(i, [(27, 5), (29, 7), (26, 9)], WHITE)
    line(i, [(4, 26), (24, 26)], rgba(MUT), 1)


def hammer_meteor(i):
    # 熾の墜落: 炎の尾を引いて斜めに落ちる鎚と、着く所の輪
    put(i, [(4, 4), (14, 10), (10, 14)], rgba(FIRE, 170))
    put(i, [(6, 3), (15, 8), (12, 11)], rgba(HOT, 150))
    put(i, [(12, 9), (20, 17), (16, 21), (8, 13)], rgba(shade(TAG["bone"], 0.9)))
    put(i, [(13, 11), (18, 16), (16, 18), (11, 13)], rgba(FIRE))
    line(i, [(18, 19), (24, 25)], rgba((140, 96, 60)), 2)
    arc(i, (14, 23, 30, 30), 180, 360, rgba(HOT), 2)
    dot(i, 25, 8, rgba(MUT), 1)


def trap_mine(i):
    # 熾火の罠: 地に埋めた熾火の玉と四方の棘
    for a, b in (((16, 5), (16, 10)), ((16, 22), (16, 27)), ((5, 16), (10, 16)), ((22, 16), (27, 16))):
        line(i, [a, b], rgba(TAG["bone"]), 2)
    dot(i, 16, 16, rgba(shade(FIRE, 0.7)), 6)
    dot(i, 16, 16, rgba(FIRE), 4)
    dot(i, 16, 16, rgba(HOT), 2)
    ring(i, (7, 7, 25, 25), rgba(MUT, 150), 1)


def thunder_cloud(i):
    # 雷雲: 黒い雲と二つの稲妻
    for x, y, r in ((11, 11, 5), (17, 9, 6), (22, 12, 4)):
        dot(i, x, y, rgba((70, 74, 96)), r)
    put(i, [(7, 12), (26, 12), (26, 15), (7, 15)], rgba((70, 74, 96)))
    put(i, [(12, 16), (15, 16), (13, 21), (16, 21), (11, 28), (12, 22), (10, 22)], rgba(BOLT))
    put(i, [(19, 16), (22, 16), (20, 20), (22, 20), (18, 25), (19, 21), (17, 21)], rgba(BOLT, 200))
    line(i, [(9, 9), (14, 6)], rgba(MUT), 1)


def frozen_sea(i):
    # 凍て海: 波の上に突き出る氷の刃
    t = TAG["tide"]
    for k, x in enumerate((9, 16, 23)):
        h = 14 if k == 1 else 10
        put(i, [(x - 3, 22), (x, 22 - h), (x + 3, 22)], rgba(ICE if k == 1 else shade(ICE, 0.85)))
        line(i, [(x, 22 - h + 2), (x, 21)], WHITE, 1)
    arc(i, (4, 20, 16, 28), 180, 360, rgba(t), 2)
    arc(i, (15, 20, 28, 28), 180, 360, rgba(t), 2)
    dot(i, 24, 7, rgba(MUT), 1)


ICONS = [
    (cleave_cliff, TAG["rock"]), (sweep_tail, TAG["rock"]), (rend_blade, TAG["wind"]), (rain_comet, TAG["wind"]),
    (ebb_whirl, TAG["tide"]), (lunge_surge, TAG["tide"]), (hammer_meteor, TAG["ash"]), (trap_mine, TAG["ash"]),
    (thunder_cloud, TAG["wind"]), (frozen_sea, TAG["tide"]),
]


def sheet():
    from PIL import Image
    img = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    for n, (fn, rim) in enumerate(ICONS):
        m = medallion(rim, MUT, disc=DISC)
        fn(m)
        img.paste(m, ((n % 8) * 32, (n // 8) * 32))
    return img


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet().save(OUT)
    print("wrote", OUT)
