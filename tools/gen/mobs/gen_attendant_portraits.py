#!/usr/bin/env python3
"""都の施設の番人 (16 役) の対話画面の肖像 (128x128) を、役 x 3 表情 = 48 枚描く。

  python tools/gen/mobs/gen_attendant_portraits.py   -> assets/skyabyss/textures/gui/npc/face/attendant_<役>_<表情>.png
                                               docs/review/npc_roughs/portraits.png (一覧)

描き方は tools/gen/mobs/gen_portraits.py の案 A (粒子のにじむ絵画調: 5 段 + ディザ、縁に逆光、暗い背景に光の暈) をそのまま使う。
頭は共通の形 (旅の職人の肖像から額の眼鏡を外した頭) で、役ごとに替えるのは肌・髪・上着・前掛け (役の色)・襟と、
頭にかぶる物 (布・帽・頭巾・鉢巻)・眼鏡・ひげなど。体は低ポリの案 A (上着と前掛け) に合わせる。
顔を隠した商人は頭巾と、鼻から下を覆う布 (口と鼻は描かない)。
表情: neutral / smile / grave。
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_portraits as GP  # noqa: E402
from gen_portraits import ell, poly, rrect, bez, stroke, taper, eye, brows, nose, head_mask, cloth, knit, stubble_fn  # noqa: E402

ROOT = GP.ROOT
FACE_DIR = GP.FACE_DIR
REVIEW = os.path.join(ROOT, "docs", "review", "npc_roughs")
FACES = GP.FACES
STYLE = "a"   # いまのゲームの肖像と同じ案

# 役の色 (低ポリの PAL と同じ考え: coat = 上着, role = 前掛け, acc = 襟, hair = 髪, head = かぶり物)
# head: none / kerchief (布) / cap (布の帽) / tall (祭司の高い冠) / hood (頭巾) / band (鉢巻) / beret / brim (つば広)
R = {
    "guild_clerk":     dict(skin=(196, 150, 112), hair=(86, 62, 44), coat=(70, 84, 104), role=(176, 138, 58), acc=(196, 158, 76), head="none", part=True, bg=(176, 138, 58)),
    "smith":           dict(skin=(176, 120, 82), hair=(36, 30, 30), coat=(66, 62, 64), role=(126, 76, 46), acc=(170, 98, 52), head="band", headc=(70, 52, 40), beard=True, bg=(220, 110, 50)),
    "tool_merchant":   dict(skin=(196, 150, 112), hair=(104, 74, 48), coat=(86, 96, 62), role=(170, 150, 108), acc=(178, 142, 70), head="cap", headc=(120, 100, 64), bg=(170, 150, 90)),
    "apothecary":      dict(skin=(212, 176, 146), hair=(150, 146, 138), coat=(84, 110, 90), role=(196, 200, 172), acc=(120, 150, 96), head="kerchief", headc=(96, 130, 92), specs=True, bg=(110, 170, 110)),
    "storage_keeper":  dict(skin=(150, 100, 70), hair=(60, 46, 36), coat=(98, 76, 56), role=(170, 92, 46), acc=(120, 126, 134), head="none", beard=True, bg=(170, 100, 50)),
    "innkeeper":       dict(skin=(210, 156, 124), hair=(150, 62, 46), coat=(122, 58, 56), role=(222, 210, 180), acc=(206, 170, 96), head="kerchief", headc=(176, 70, 56), lady=True, bg=(230, 150, 80)),
    "perch_keeper":    dict(skin=(176, 126, 90), hair=(120, 92, 62), coat=(98, 120, 140), role=(212, 218, 214), acc=(90, 160, 166), head="none", feather=True, bg=(120, 180, 200)),
    "shrine_priest":   dict(skin=(200, 164, 136), hair=(226, 222, 212), coat=(222, 218, 206), role=(176, 56, 44), acc=(214, 176, 80), head="tall", headc=(40, 36, 40), old=True, bg=(220, 90, 60)),
    "keep_veteran":    dict(skin=(196, 156, 126), hair=(196, 194, 188), coat=(88, 92, 98), role=(124, 38, 38), acc=(170, 174, 180), head="none", beard=True, scar=True, old=True, bg=(170, 60, 50)),
    "archivist":       dict(skin=(212, 178, 150), hair=(124, 118, 116), coat=(52, 58, 96), role=(200, 184, 140), acc=(176, 148, 82), head="cap", headc=(40, 44, 78), specs=True, old=True, bg=(110, 120, 190)),
    "training_master": dict(skin=(176, 124, 88), hair=(40, 34, 32), coat=(214, 206, 184), role=(48, 60, 104), acc=(40, 44, 60), head="band", headc=(214, 206, 184), topknot=True, bg=(90, 110, 180)),
    "black_marketeer": dict(skin=(190, 146, 112), hair=(28, 26, 32), coat=(36, 34, 40), role=(68, 44, 68), acc=(120, 104, 70), head="hood", headc=(44, 38, 50), mask=(70, 58, 78), bg=(110, 70, 130)),
    "gravekeeper":     dict(skin=(206, 180, 160), hair=(176, 174, 168), coat=(92, 92, 88), role=(74, 86, 70), acc=(150, 146, 132), head="brim", headc=(62, 62, 60), old=True, bg=(120, 140, 130)),
    "mapmaker":        dict(skin=(196, 150, 112), hair=(150, 96, 56), coat=(54, 104, 112), role=(196, 172, 122), acc=(186, 146, 70), head="beret", headc=(150, 70, 50), bg=(80, 160, 170)),
    "drunkard":        dict(skin=(206, 146, 116), hair=(96, 76, 58), coat=(110, 92, 70), role=(122, 62, 78), acc=(140, 120, 80), head="none", beard=True, drunk=True, bg=(200, 110, 90)),
    "trade_keeper":    dict(skin=(196, 150, 112), hair=(70, 52, 40), coat=(44, 84, 82), role=(196, 160, 82), acc=(214, 184, 104), head="cap", headc=(44, 84, 82), specs=True, bg=(200, 170, 90)),
    "stable_keeper":   dict(skin=(190, 140, 104), hair=(104, 76, 52), coat=(116, 84, 56), role=(92, 120, 76), acc=(204, 172, 104), head="brim", headc=(124, 92, 60), beard=True, bg=(170, 130, 80)),
    "guide":           dict(skin=(196, 150, 112), hair=(70, 52, 40), coat=(232, 230, 222), role=(242, 240, 234), acc=(196, 162, 78), head="none", bg=(120, 170, 210)),
    "box_opener":      dict(skin=(222, 160, 128), hair=(150, 140, 132), coat=(112, 52, 42), role=(214, 200, 168), acc=(206, 168, 86), head="none", specs=True, old=True, bg=(170, 60, 50)),
}
ROLES = list(R)


def set_mats(role):
    p = R[role]
    for k in ("skin", "hair", "coat", "role", "acc"):
        GP.MATS["r_" + k] = p[k]
    GP.MATS["r_head"] = p.get("headc", p["hair"])
    GP.MATS["r_mask"] = p.get("mask", (60, 60, 60))
    GP.MATS["r_lipl"] = (176, 66, 70)
    GP.MATS["r_red"] = (206, 96, 90)
    b = np.array(p["bg"], float)
    GP.BG[role] = dict(top=tuple((b * 0.12).astype(int)), bot=tuple((b * 0.32).astype(int)), glow=tuple(b.astype(int)),
                       b=tuple((b * 0.5).astype(int)), b2=tuple((b * 0.65).astype(int)), c=tuple(b.astype(int)))


def mouth(a, expr, cx, cy, w, lip="lip"):
    """gen_portraits.mouth と同じ。重い顔の下唇の影だけ役の肌で塗る。"""
    if expr == "smile":
        a.paint(stroke(bez((cx - w, cy - 2.2), (cx, cy + 4.6), (cx + w, cy - 2.2), 14), 1.9), lip, 0.6, v=0.22)
        a.paint(stroke([(cx - w - 1.2, cy - 3.6), (cx - w - 0.3, cy - 2.2)], 1.0), "line", 0.5, v=0.2)
        a.paint(stroke([(cx + w + 1.2, cy - 3.6), (cx + w + 0.3, cy - 2.2)], 1.0), "line", 0.5, v=0.2)
        a.paint(ell(cx, cy + 0.7, w * 0.55, 0.9), "sclera", 0.6, v=0.6)
    elif expr == "grave":
        a.paint(stroke(bez((cx - w, cy + 2.2), (cx, cy - 2.0), (cx + w, cy + 2.2), 14), 1.8), lip, 0.6, v=0.18)
        a.paint(ell(cx, cy + 3.4, 3.4, 1.2), "r_skin", 1, v=0.3)
    else:
        a.paint(stroke(bez((cx - w + 1, cy + 0.2), (cx, cy + 1.2), (cx + w - 1, cy + 0.2), 14), 1.7), lip, 0.6, v=0.22)
        a.paint(ell(cx, cy + 3.0, 3.6, 1.1), lip, 0.8, v=0.55)


def attendant(a, expr, role):
    p = R[role]
    skin = "r_skin"
    head = p["head"]
    # 体: 上着 (肩)、前掛け (役の色) と革の吊り紐、襟
    a.paint(poly([(0, 130), (4, 108), (26, 95), (52, 90), (76, 90), (102, 95), (124, 108), (128, 130)]), "r_coat", 9, ol=2,
            rim=(255, 210, 160), tex=cloth)
    a.paint(poly([(34, 130), (38, 104), (90, 104), (94, 130)]), "r_role", 4, ol=2, tex=cloth)
    a.paint(poly([(38, 94), (48, 94), (52, 130), (42, 130)]), "strap", 2, ol=1)
    a.paint(poly([(80, 94), (90, 94), (86, 130), (76, 130)]), "strap", 2, ol=1)
    a.paint(rrect(40, 110, 50, 115, 1.5), "r_acc", 1.5, ol=1)
    a.paint(rrect(78, 110, 88, 115, 1.5), "r_acc", 1.5, ol=1)
    if head == "hood":
        # 頭巾の後ろ (顔の後ろに大きく)
        a.paint(ell(64, 66, 37, 40) | poly([(26, 92), (102, 92), (110, 128), (18, 128)]), "r_head", 7, ol=2, rim=(200, 170, 230), tex=cloth)
        a.paint(ell(64, 68, 27, 31), "inner", 4)
    a.paint(ell(64, 98, 27, 9) & poly([(0, 92), (128, 92), (128, 128), (0, 128)]), "r_acc", 3, ol=2, tex=cloth)
    a.paint(rrect(53, 80, 75, 100, 5), skin, 3)
    if p.get("lady"):
        # 肩へ垂れる髪 (布の下から)
        a.paint(taper([(44, 60), (40, 80), (42, 98)], 6.0, 3.0), "r_hair", 2, ol=1, tex=cloth)
        a.paint(taper([(84, 60), (88, 80), (86, 98)], 6.0, 3.0), "r_hair", 2, ol=1, tex=cloth)
    # 耳と髪 (頭巾なら髪と耳は見せない)
    if head != "hood":
        a.paint(ell(42.5, 68, 5, 7), skin, 2, ol=1)
        a.paint(ell(85.5, 68, 5, 7), skin, 2, ol=1)
        a.paint(ell(64, 50, 25, 20) if not p.get("old") else ell(64, 52, 24, 17), "r_hair", 5, ol=2, tex=cloth)
        if p.get("topknot"):
            a.paint(ell(64, 24, 7, 6), "r_hair", 2, ol=2, tex=cloth)
    face = ell(64, 66, 21, 24) | rrect(45, 70, 83, 91, 11)
    if p.get("lady"):
        face = head_mask(64, 66, 20.5, 25, 0.76)
    a.shade(face, 0, 5, 4, 0.5)
    a.paint(face, skin, 6, rim=(255, 206, 160), tex=None if p.get("lady") or p.get("mask") else stubble_fn(46, 82, 78, 96, 0.12))
    if p.get("beard"):
        beard = (ell(64, 86, 18, 10) | rrect(46, 74, 82, 88, 8)) & ~ell(64, 82, 7.5, 3.6)
        a.paint(beard & face, "r_hair", 3, tex=cloth)
    if p.get("drunk"):
        for cx in (51, 77):
            a.paint(ell(cx, 77, 5.5, 3.2), "r_red", 2, v=0.5)
    # 前髪 (頭にかぶる物がなければ)
    if head in ("none", "band"):
        if p.get("part"):
            a.paint(poly([(44, 46), (46, 58), (56, 52), (60, 46)]) | poly([(62, 44), (66, 52), (80, 54), (84, 58), (84, 46)]), "r_hair", 2, tex=cloth)
        elif p.get("old"):
            a.paint(poly([(44, 50), (46, 58), (54, 54), (74, 54), (82, 58), (84, 50)]), "r_hair", 2, tex=cloth)
        else:
            a.paint(poly([(44, 44), (46, 57), (53, 51), (60, 57), (66, 50), (73, 57), (78, 51), (83, 57), (84, 44)]), "r_hair", 2, tex=cloth)
        a.paint(taper([(44, 56), (42, 64)], 3.4, 1.4), "r_hair", 1)
        a.paint(taper([(84, 56), (86, 64)], 3.4, 1.4), "r_hair", 1)
    # 目・眉・鼻・口
    ey = 67
    for side, x in ((-1, 53), (1, 75)):
        eye(a, x, ey, expr if not (p.get("drunk") and expr == "neutral") else "smile", "dark", skin, side)
        if p.get("lady"):
            a.paint(taper([(x + side * 4.5, ey - 1.6), (x + side * 7.5, ey - 3.4)], 1.4, 0.7), "line", 0.5, v=0.1)
        if p.get("drunk") and expr == "neutral":
            # 重いまぶた (半分閉じた目)
            a.paint(ell(x, ey - 2.4, 6.4, 2.8), skin, 1.5, v=0.45)
            a.paint(stroke([(x - 6, ey - 0.2), (x, ey - 0.6), (x + 6, ey - 0.2)], 1.1), "line", 0.5, v=0.12)
    brows(a, expr, 60, "r_hair" if head != "hood" else "hair_dk", xin=3.6, xout=16, w0=3.6 if not p.get("lady") else 2.4, w1=2.2 if not p.get("lady") else 1.2)
    if p.get("mask"):
        # 鼻から下を覆う布 (耳の前から顎の下まで)。上の縁に折り返し
        cover = (poly([(38, 72), (90, 72), (92, 100), (80, 108), (48, 108), (36, 100)]) | ell(64, 98, 26, 10))
        a.shade(cover, 0, -3, 3, 0.35)
        a.paint(cover, "r_mask", 5, ol=2, rim=(200, 170, 230), tex=knit)
        a.paint(rrect(39, 71, 89, 75, 2), "r_mask", 1.5, v=0.7)
    else:
        nose(a, 64, 75, skin, 4.4 if not p.get("lady") else 3.4, 5.2 if not p.get("lady") else 4.4)
        if p.get("drunk"):
            a.paint(ell(64.6, 77, 4.6, 4.2), "r_red", 2.0, soft=1.3)
        mouth(a, expr, 64, 86 if not p.get("beard") else 84.5, 7 if not p.get("lady") else 5.6, "r_lipl" if p.get("lady") else "lip")
    if expr == "grave":
        a.paint(stroke([(61, 56.5), (61.4, 60)], 1.0), "line", 0.5, v=0.2)
        a.paint(stroke([(67, 56.5), (66.6, 60)], 1.0), "line", 0.5, v=0.2)
    if p.get("old"):
        for side in (-1, 1):
            a.paint(stroke([(64 + side * 16.5, 68.5), (64 + side * 20, 69.5)], 0.8), "line", 0.5, v=0.4)
    if p.get("scar"):
        a.paint(stroke([(78, 58), (74, 66), (73, 72)], 1.2), "line", 0.5, v=0.35)
    if p.get("specs"):
        for cx in (53, 75):
            rim_ = ell(cx, ey, 8, 6.4) & ~ell(cx, ey, 6.6, 5.0)
            a.paint(rim_, "brass", 1, v=0.55)
        a.paint(stroke([(61, ey - 1), (67, ey - 1)], 1.2), "brass", 0.5, v=0.55)
    # 頭にかぶる物
    if head == "kerchief":
        k = (ell(64, 48, 27, 18) | poly([(38, 50), (90, 50), (88, 58), (40, 58)])) & ~poly([(0, 59), (128, 59), (128, 128), (0, 128)])
        a.shade(k, 0, 4, 3, 0.5)
        a.paint(k, "r_head", 5, ol=2, rim=(255, 214, 170), tex=knit)
        a.paint(poly([(84, 44), (98, 36), (100, 46), (90, 52)]), "r_head", 2, ol=2, tex=knit)
    elif head == "cap":
        c = ell(64, 44, 25, 15) & ~poly([(0, 54), (128, 54), (128, 128), (0, 128)])
        a.shade(c, 0, 4, 3, 0.5)
        a.paint(c, "r_head", 5, ol=2, rim=(220, 220, 255), tex=cloth)
        a.paint(rrect(39, 50, 89, 56, 3), "r_head", 2, ol=2, v=0.35)
    elif head == "beret":
        c = ell(60, 42, 29, 12)
        a.shade(c, 0, 4, 3, 0.5)
        a.paint(c, "r_head", 5, ol=2, rim=(255, 210, 170), tex=cloth)
        a.paint(ell(66, 30, 2.6, 2.4), "r_head", 1, ol=1)
    elif head == "tall":
        c = rrect(46, 12, 82, 52, 8) & ~poly([(0, 53), (128, 53), (128, 128), (0, 128)])
        a.shade(c, 0, 4, 3, 0.5)
        a.paint(c, "r_head", 5, ol=2, rim=(255, 220, 190), tex=cloth)
        a.paint(rrect(44, 46, 84, 53, 3), "r_head", 2, ol=2, v=0.3)
        a.paint(stroke([(46, 54), (40, 62), (40, 96)], 1.6), "r_role", 1, ol=1)
        a.paint(stroke([(82, 54), (88, 62), (88, 96)], 1.6), "r_role", 1, ol=1)
    elif head == "band":
        b = rrect(40, 48, 88, 55, 3)
        a.shade(b, 0, 3, 3, 0.4)
        a.paint(b, "r_head", 2, ol=2, tex=cloth)
        a.paint(poly([(86, 50), (100, 46), (102, 52), (98, 60), (88, 55)]), "r_head", 2, ol=2)
    elif head == "brim":
        brim = ell(64, 46, 46, 8)
        crown = ell(64, 32, 22, 14) | rrect(42, 28, 86, 46, 8)
        a.shade(brim, 0, 5, 4, 0.7)
        a.paint(brim, "r_head", 5, ol=2, rim=(220, 230, 220), tex=cloth)
        a.paint(crown, "r_head", 6, ol=2, rim=(220, 230, 220), tex=cloth)
        a.paint(crown & poly([(0, 40), (128, 40), (128, 46), (0, 46)]), "strap", 2.0, ol=0)
    elif head == "hood":
        rim_ = ell(64, 66, 31, 36) & ~ell(64, 68, 25.5, 30.5)
        rim_ &= ~poly([(0, 98), (128, 98), (128, 128), (0, 128)])
        a.paint(rim_, "r_head", 3, ol=2, rim=(200, 170, 230), tex=cloth)
        a.shade(rim_, 0, 3, 4, 0.5)
    if p.get("feather"):
        a.paint(taper([(84, 46), (94, 32), (100, 18)], 4.0, 1.0), "feather", 1.5, ol=1)
        a.paint(stroke([(86, 44), (98, 22)], 0.6), "line", 0.5, v=0.4)


def portrait(role, expr):
    set_mats(role)
    a = GP.Art(STYLE, role)
    attendant(a, expr, role)
    img = a.render(GP.background(STYLE, role))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def main():
    os.makedirs(FACE_DIR, exist_ok=True)
    os.makedirs(REVIEW, exist_ok=True)
    imgs = {}
    for role in ROLES:
        for f in FACES:
            im = portrait(role, f)
            im.save(os.path.join(FACE_DIR, "attendant_%s_%s.png" % (role, f)))
            imgs[role, f] = im
    # 一覧 (2 倍。5 役ずつ 3 段、各役の 3 表情を横に)
    S, G = 256, 8
    cols = 5
    W = cols * (3 * S + 2 * 4) + (cols - 1) * 24 + 40
    H = 3 * (S + 34) + 70
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    import voxel  # noqa: E402  (tools/rough の字形)
    d.text((20, 14), "都の施設の番人 — 対話の肖像 (役 x neutral / smile / grave, 2 倍)", font=voxel.font(24), fill=(236, 232, 218))
    for i, role in enumerate(ROLES):
        x0 = 20 + (i % cols) * (3 * S + 8 + 24)
        y0 = 60 + (i // cols) * (S + 34)
        for j, f in enumerate(FACES):
            cv.paste(imgs[role, f].resize((S, S), Image.NEAREST), (x0 + j * (S + 4), y0))
        d.text((x0, y0 + S + 6), role, font=voxel.font(16), fill=(150, 232, 255))
    cv.save(os.path.join(REVIEW, "portraits.png"))
    print("wrote", len(imgs), "portraits ->", FACE_DIR)


if __name__ == "__main__":
    sys.path.insert(0, os.path.join(ROOT, "tools", "rough"))
    main()
