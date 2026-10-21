#!/usr/bin/env python3
"""町のクエストの限定の人 (docs/design/35 の 3.5。8 人) の絵を書く。形は係の人の案 A (attendant_a) をそのまま使い、色だけを替える。

  python tools/gen_town_guests.py

書くもの:
  assets/skyabyss/textures/lowpoly/guest_<人>.png          低ポリの絵 (attendant_a のメッシュと同じ UV。tools/rough/lowpoly/fac/attendants.py で描く)
  assets/skyabyss/textures/gui/npc/face/guest_<人>_<表情>.png   会話の肖像 (128x128。tools/gen_attendant_portraits.py の描き方)
  assets/skyabyss/animations/entity/town_guest.animation.json   動き (係の人のものの写し。名前を animation.town_guest.* に)
  docs/review/town_quests/guests.png                          8 人の前 3/4 と肖像の一覧
係の人の道具 (attendants.py・gen_attendant_portraits.py) は書き換えない。色の表をここで足して、その関数を呼ぶ。
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(ASSETS, "textures", "lowpoly")
REVIEW = os.path.join(ROOT, "docs", "review", "town_quests")
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rough"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rough", "lowpoly", "fac"))

import attendants as A  # noqa: E402
import gen_attendant_portraits as GAP  # noqa: E402
import gen_portraits as GP  # noqa: E402

# 低ポリの色 (attendants.PAL と同じ形)。coat = 上着 / role = 前掛け / trim = 縁 / acc = 襟 / hair = 髪 / guard = 前腕 / sash = 帯
PAL = {
    "apprentice":     dict(coat=(92, 80, 62), role=(70, 110, 130), trim=(60, 44, 30), acc=(180, 150, 90), hair=(60, 44, 34), skin="mid", guard=(100, 80, 58), sash=(90, 60, 40)),
    "bellkeeper":     dict(coat=(70, 64, 80), role=(150, 120, 60), trim=(46, 40, 52), acc=(196, 160, 80), hair=(210, 206, 198), skin="old", guard=(80, 74, 88), sash=(120, 96, 50)),
    "fisher":         dict(coat=(60, 84, 96), role=(196, 170, 90), trim=(40, 56, 64), acc=(120, 140, 150), hair=(90, 70, 50), skin="tan", guard=(80, 90, 96), sash=(150, 130, 70)),
    "bard":           dict(coat=(120, 40, 60), role=(210, 180, 110), trim=(70, 30, 40), acc=(220, 200, 120), hair=(170, 120, 70), skin="pale", guard=(110, 50, 66), sash=(60, 40, 90)),
    "child":          dict(coat=(160, 120, 90), role=(200, 190, 170), trim=(120, 84, 60), acc=(180, 90, 80), hair=(120, 80, 50), skin="pale", guard=(170, 140, 110), sash=(180, 90, 80), lady=True),
    "weaver":         dict(coat=(90, 100, 140), role=(200, 170, 150), trim=(60, 66, 100), acc=(170, 80, 90), hair=(60, 40, 40), skin="mid", guard=(100, 108, 140), sash=(170, 80, 90), lady=True),
    "ash_apprentice": dict(coat=(64, 58, 56), role=(150, 70, 40), trim=(36, 32, 30), acc=(220, 120, 50), hair=(40, 30, 28), skin="tan", guard=(54, 48, 46), sash=(120, 56, 32)),
    "herbalist":      dict(coat=(88, 100, 70), role=(150, 120, 90), trim=(60, 70, 48), acc=(140, 160, 90), hair=(200, 198, 190), skin="old", guard=(96, 104, 80), sash=(110, 130, 80), lady=True),
}
# 肖像の色 (gen_attendant_portraits.R と同じ形)
R = {
    "apprentice":     dict(skin=(196, 150, 112), hair=(60, 44, 34), coat=(92, 80, 62), role=(70, 110, 130), acc=(180, 150, 90), head="band", headc=(70, 110, 130), bg=(90, 150, 180)),
    "bellkeeper":     dict(skin=(200, 164, 136), hair=(214, 210, 202), coat=(70, 64, 80), role=(150, 120, 60), acc=(196, 160, 80), head="none", beard=True, old=True, bg=(200, 170, 90)),
    "fisher":         dict(skin=(176, 124, 88), hair=(90, 70, 50), coat=(60, 84, 96), role=(196, 170, 90), acc=(120, 140, 150), head="kerchief", headc=(60, 84, 96), beard=True, bg=(80, 140, 170)),
    "bard":           dict(skin=(212, 176, 146), hair=(170, 120, 70), coat=(120, 40, 60), role=(210, 180, 110), acc=(220, 200, 120), head="beret", headc=(60, 40, 90), feather=True, bg=(190, 90, 120)),
    "child":          dict(skin=(214, 176, 150), hair=(120, 80, 50), coat=(160, 120, 90), role=(200, 190, 170), acc=(180, 90, 80), head="none", lady=True, bg=(110, 120, 170)),
    "weaver":         dict(skin=(196, 150, 112), hair=(60, 40, 40), coat=(90, 100, 140), role=(200, 170, 150), acc=(170, 80, 90), head="kerchief", headc=(170, 80, 90), lady=True, bg=(150, 110, 180)),
    "ash_apprentice": dict(skin=(176, 120, 82), hair=(40, 30, 28), coat=(64, 58, 56), role=(150, 70, 40), acc=(220, 120, 50), head="band", headc=(90, 80, 76), bg=(230, 110, 50)),
    "herbalist":      dict(skin=(206, 180, 160), hair=(204, 200, 192), coat=(88, 100, 70), role=(150, 120, 90), acc=(140, 160, 90), head="kerchief", headc=(110, 130, 80), lady=True, old=True, bg=(120, 170, 100)),
}
JA = {"apprentice": "鯨追いの見習い", "bellkeeper": "鐘守", "fisher": "浜を捨てた漁師", "bard": "旅の吟遊詩人",
      "child": "雷を怖がる子", "weaver": "機織り", "ash_apprentice": "灰を運ぶ弟子", "herbalist": "薬草摘みの老婆"}
GUESTS = list(PAL)


def atlases():
    A.PAL.update(PAL)
    faces = A.LOOKS["a"]()
    size, P, N, M, L = A.build_atlas(faces, 256, 0)
    out = {}
    for g in GUESTS:
        img, glow = A.RA_PAINT(faces, size, P, N, M, A.pal_for(g), ())
        img = A.face_paint(img, faces, P, N, M, g)
        Image.fromarray(img, "RGBA").save(os.path.join(TEX, "guest_%s.png" % g))
        out[g] = img
    r = dict(faces=faces, size=size, atlases=out, glow=np.zeros((size, size), bool))
    return r


def portraits():
    GAP.R.update(R)
    out = {}
    for g in GUESTS:
        for f in GP.FACES:
            im = GAP.portrait(g, f)
            im.save(os.path.join(GP.FACE_DIR, "guest_%s_%s.png" % (g, f)))
            out[g, f] = im
    return out


def animation():
    src = os.path.join(ASSETS, "animations", "entity", "attendant.animation.json")
    data = json.load(open(src, encoding="utf-8"))
    data["animations"] = {n.replace("animation.attendant.", "animation.town_guest."): v for n, v in data["animations"].items()}
    for need in ("idle", "walk", "talk"):
        assert "animation.town_guest." + need in data["animations"], need
    with open(os.path.join(ASSETS, "animations", "entity", "town_guest.animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def sheet(r, faces):
    F = A.voxel.font
    PW, PH, S = 200, 300, 96
    cols = 4
    W = 20 * 2 + cols * (PW + 3 * S + 30)
    H = 70 + 2 * (PH + 40)
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), "町のクエストの限定の人 — 形は係の人の案 A、色だけ替える (前 3/4 と肖像 neutral / smile / grave)", font=F(20), fill=(236, 232, 218))
    for i, g in enumerate(GUESTS):
        x = 20 + (i % cols) * (PW + 3 * S + 30)
        y = 60 + (i // cols) * (PH + 40)
        view = A.render_view(r, r["atlases"][g], r["glow"], A.POSES["idle"], A.BONES, 212, 12, (PW, PH), (0, 15.0, 0), 10.0, ref=False)
        cv.paste(view, (x, y))
        for j, f in enumerate(GP.FACES):
            cv.paste(faces[g, f].resize((S, S), Image.NEAREST), (x + PW + 4 + j * (S + 2), y + 20))
        d.text((x + 2, y + PH + 4), JA[g] + "  " + g, font=F(14), fill=(150, 232, 255))
    os.makedirs(REVIEW, exist_ok=True)
    cv.save(os.path.join(REVIEW, "guests.png"))


def main():
    r = atlases()
    faces = portraits()
    animation()
    sheet(r, faces)
    print("guests", len(GUESTS), "->", TEX, GP.FACE_DIR)


if __name__ == "__main__":
    main()
