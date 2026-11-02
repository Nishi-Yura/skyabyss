"""野の装備 (docs/design/51) の武器の塗り。品ごとに材質名 → 素材の表 (MATS) を持ち、素材ごとに描き方 (金属の磨き・木目・革・布目・骨・殻・結晶・毛・縄) を変える。
材質名は他の武器と同じ (main edge spine guard grip wrap acc gem trim dark cord cloth soot chain void)。品の表 (o_wild*.py の _w の mats) で、材質名ごとに素材を決める。
光 (gem と、伝説と強化の筋) は品ごとの光の色 (glow)。縁 (trim) は階級の色: アンコモン = 鉄、レア = 青い鋼、エピック = 紫の銀、ヒロイック = 朱金。
Minecraft らしい低解像度のドット絵: 少ない色の段、4x4 の網点、縁の明暗、ノイズはゆるく。"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from wkit import pick, ramp, LIGHT, hash1, vnoise, BAYER, edge_fields, fill_outside  # noqa: E402,F401

A_ = np.array
# 素材: (描き方, 色の段 (暗 → 明))
S = {
    # 金属
    "steel": ("metal", A_([(40, 44, 52), (66, 72, 82), (98, 106, 118), (136, 144, 156), (176, 184, 194), (214, 220, 228), (240, 244, 248)])),
    "iron": ("metal", A_([(30, 30, 34), (50, 50, 56), (74, 74, 80), (102, 102, 108), (132, 132, 138), (164, 164, 168)])),
    "rust": ("metal", A_([(46, 26, 18), (76, 42, 24), (110, 62, 32), (140, 86, 46), (166, 112, 66), (190, 140, 96)])),
    "bronze": ("metal", A_([(52, 32, 16), (86, 56, 26), (124, 84, 38), (162, 116, 56), (196, 150, 82), (226, 186, 118)])),
    "brass": ("metal", A_([(60, 44, 14), (98, 76, 26), (140, 112, 40), (182, 150, 60), (216, 186, 92), (240, 218, 140)])),
    "copper": ("metal", A_([(58, 26, 14), (98, 46, 24), (140, 70, 36), (182, 98, 54), (214, 132, 80), (238, 172, 120)])),
    "verdigris": ("metal", A_([(28, 60, 52), (44, 90, 76), (66, 122, 102), (96, 152, 128), (136, 182, 158)])),
    "gold": ("metal", A_([(80, 50, 10), (128, 88, 18), (178, 132, 30), (220, 176, 52), (244, 212, 96), (252, 238, 168)])),
    "silver": ("metal", A_([(58, 62, 74), (90, 96, 110), (128, 134, 148), (168, 174, 188), (206, 210, 222), (236, 238, 246)])),
    "blacksteel": ("metal", A_([(12, 12, 18), (24, 24, 32), (38, 38, 50), (56, 56, 72), (80, 80, 100), (112, 112, 136)])),
    "meteor": ("metal", A_([(20, 18, 24), (36, 32, 40), (56, 50, 58), (80, 74, 82), (110, 104, 112), (150, 146, 156)])),
    "molten": ("metal", A_([(40, 18, 14), (70, 30, 20), (110, 44, 24), (160, 70, 30), (210, 110, 40), (246, 170, 70)])),
    # 木
    "oak": ("wood", A_([(54, 36, 20), (82, 56, 32), (110, 78, 46), (140, 102, 62), (168, 128, 82), (194, 156, 106)])),
    "darkwood": ("wood", A_([(26, 16, 10), (42, 28, 18), (62, 42, 28), (84, 58, 40), (108, 78, 54)])),
    "driftwood": ("wood", A_([(74, 70, 64), (104, 98, 88), (136, 128, 116), (166, 158, 144), (196, 188, 174), (222, 216, 204)])),
    "bamboo": ("wood", A_([(70, 78, 30), (100, 110, 44), (136, 146, 66), (172, 178, 96), (206, 206, 132)])),
    "ashwood": ("wood", A_([(28, 26, 26), (44, 40, 40), (64, 58, 56), (88, 80, 76), (114, 104, 98)])),
    # 皮・布・紐
    "leather": ("leather", A_([(36, 22, 14), (58, 36, 22), (82, 52, 32), (106, 70, 44), (132, 92, 60)])),
    "blackleather": ("leather", A_([(14, 12, 12), (26, 22, 22), (40, 34, 32), (58, 50, 46), (78, 68, 62)])),
    "redcloth": ("cloth", A_([(56, 14, 14), (90, 24, 22), (128, 38, 32), (164, 56, 44), (196, 84, 64)])),
    "bluecloth": ("cloth", A_([(18, 26, 52), (28, 42, 80), (42, 62, 112), (62, 86, 142), (92, 116, 170)])),
    "whitecloth": ("cloth", A_([(120, 116, 106), (152, 148, 136), (184, 180, 166), (212, 208, 194), (236, 232, 220)])),
    "greencloth": ("cloth", A_([(22, 40, 22), (36, 62, 34), (54, 88, 48), (76, 116, 64), (104, 146, 86)])),
    "purplecloth": ("cloth", A_([(36, 18, 48), (56, 30, 74), (80, 46, 104), (108, 66, 136), (140, 94, 166)])),
    "sailcloth": ("cloth", A_([(126, 116, 92), (156, 146, 118), (186, 176, 146), (210, 202, 174), (230, 224, 200)])),
    "straw": ("rope", A_([(110, 84, 30), (148, 118, 48), (184, 152, 70), (214, 184, 98), (236, 212, 132)])),
    "rope": ("rope", A_([(70, 54, 34), (100, 80, 52), (130, 106, 72), (160, 134, 96), (186, 162, 122)])),
    "paper": ("cloth", A_([(150, 140, 116), (182, 172, 146), (210, 202, 178), (232, 226, 206), (246, 242, 228)])),
    "fur": ("fur", A_([(46, 40, 36), (72, 64, 58), (100, 92, 84), (130, 122, 112), (160, 152, 142)])),
    "whitefur": ("fur", A_([(120, 118, 122), (152, 150, 156), (182, 182, 188), (210, 210, 216), (234, 234, 238)])),
    "brownfur": ("fur", A_([(40, 26, 16), (62, 42, 26), (88, 60, 38), (114, 82, 54), (140, 106, 74)])),
    # 生きもの・石
    "bone": ("bone", A_([(92, 82, 64), (126, 114, 90), (160, 148, 120), (192, 182, 152), (220, 212, 186), (240, 234, 214)])),
    "tusk": ("bone", A_([(106, 88, 58), (142, 122, 84), (178, 158, 114), (208, 192, 148), (232, 220, 182), (246, 240, 214)])),
    "shell": ("shell", A_([(70, 56, 46), (102, 84, 68), (138, 116, 94), (170, 148, 122), (200, 180, 154), (226, 210, 188)])),
    "rockshell": ("shell", A_([(40, 40, 38), (62, 62, 58), (88, 86, 80), (114, 112, 104), (142, 140, 130)])),
    "crabshell": ("shell", A_([(70, 20, 14), (110, 34, 20), (150, 54, 30), (186, 82, 46), (214, 116, 70), (236, 156, 108)])),
    "nacre": ("nacre", A_([(130, 128, 140), (162, 160, 174), (192, 192, 206), (218, 218, 230), (240, 240, 250)])),
    "salt": ("crystal", A_([(150, 140, 146), (180, 170, 176), (206, 198, 204), (228, 222, 226), (246, 242, 244)])),
    "ice": ("crystal", A_([(70, 110, 140), (100, 146, 176), (138, 182, 208), (180, 214, 232), (220, 238, 248)])),
    "fulgurite": ("crystal", A_([(96, 78, 52), (130, 108, 74), (164, 140, 100), (196, 174, 132), (224, 206, 168)])),
    "glass": ("crystal", A_([(110, 130, 140), (146, 168, 178), (184, 204, 212), (216, 230, 236), (240, 248, 252)])),
    "stone": ("stone", A_([(54, 54, 56), (80, 80, 82), (108, 108, 108), (136, 136, 134), (164, 164, 160)])),
    "basalt": ("stone", A_([(20, 18, 20), (34, 30, 32), (50, 44, 44), (68, 60, 58), (90, 80, 76)])),
    "ash": ("stone", A_([(60, 58, 58), (84, 82, 80), (110, 108, 104), (138, 136, 130), (166, 164, 158)])),
    "moss": ("fur", A_([(26, 42, 18), (40, 64, 26), (58, 88, 36), (80, 112, 48), (106, 138, 66)])),
    "flesh": ("leather", A_([(70, 20, 26), (104, 34, 40), (140, 54, 58), (172, 80, 80), (200, 112, 108)])),
    "cloudwhite": ("cloth", A_([(150, 160, 176), (182, 192, 206), (210, 218, 230), (232, 238, 246), (248, 250, 254)])),
    "prism": ("crystal", A_([(120, 110, 150), (160, 150, 190), (196, 190, 220), (226, 222, 242), (248, 246, 255)])),
    "dark": ("flat", A_([(6, 6, 8), (14, 14, 18), (26, 26, 32)])),
}
# 縁 (trim) の階級の色
TRIM = {
    "uncommon": A_([(40, 44, 50), (66, 72, 80), (100, 108, 116), (140, 148, 156), (184, 190, 196)]),
    "rare": A_([(30, 52, 92), (52, 86, 140), (84, 128, 188), (126, 170, 226), (184, 214, 250)]),
    "epic": A_([(52, 34, 84), (86, 58, 130), (126, 90, 176), (168, 132, 214), (210, 184, 244)]),
    "heroic": A_([(96, 40, 14), (150, 70, 22), (204, 112, 38), (238, 158, 66), (252, 206, 122)]),
}
# 光の色 (暗 → 明)
GLOWS = {
    "spark": A_([(60, 120, 220), (110, 170, 255), (180, 220, 255), (240, 250, 255)]),
    "lamp": A_([(170, 90, 20), (230, 150, 40), (255, 206, 100), (255, 240, 190)]),
    "ember": A_([(170, 40, 10), (230, 90, 20), (255, 150, 50), (255, 220, 140)]),
    "moss": A_([(40, 120, 50), (80, 180, 80), (150, 230, 130), (220, 255, 200)]),
    "frost": A_([(60, 150, 200), (110, 200, 240), (180, 236, 255), (240, 252, 255)]),
    "star": A_([(150, 170, 220), (200, 214, 250), (232, 240, 255), (255, 255, 255)]),
    "void": A_([(70, 30, 120), (120, 60, 190), (176, 120, 240), (226, 196, 255)]),
    "venom": A_([(70, 120, 20), (120, 180, 30), (180, 230, 70), (230, 255, 150)]),
    "heart": A_([(140, 20, 30), (200, 40, 50), (240, 90, 90), (255, 170, 160)]),
    "ink": A_([(30, 60, 140), (50, 100, 200), (110, 160, 240), (190, 220, 255)]),
    "salt": A_([(180, 120, 150), (220, 170, 196), (244, 214, 232), (255, 244, 250)]),
    "sea": A_([(30, 120, 140), (60, 180, 190), (130, 226, 226), (210, 255, 250)]),
    "dawn": A_([(220, 120, 60), (250, 170, 90), (255, 214, 150), (255, 244, 214)]),
    "rainbow": A_([(200, 80, 120), (120, 160, 240), (150, 240, 160), (255, 240, 150)]),
}
GLOW_RGB = {}
# 品ごとの塗り (o_wild*.py の _w が入れる): id → dict(mats={材質名: 素材}, glow=光, rarity=階級)
ITEMS = {}
DEFAULT_MATS = dict(main="steel", edge="steel", spine="iron", guard="iron", grip="leather", wrap="leather", acc="bronze", cord="rope",
                    cloth="redcloth", soot="ash", chain="iron", dark="dark", void="dark")


def register(wid, mats, glow, rarity):
    m = dict(DEFAULT_MATS)
    m.update(mats)
    ITEMS[wid] = dict(mats=m, glow=glow, rarity=rarity)
    GLOW_RGB[wid] = tuple(int(c) for c in GLOWS[glow][-2])


def paint(faces, size, P, N, M, L, glow_key="", tier="rare", plus=0, inlay=None, edge_mats=()):
    item = ITEMS[glow_key]
    D, K, LU, LV, CW, CH, INS = edge_fields(faces, size)
    H = W = size
    yy, xx = np.mgrid[0:H, 0:W]
    fi = np.where(M >= 0, M, 0)
    owned = M >= 0
    mats = np.array([f.mat for f in faces])[fi]
    bay = BAYER[yy % 4, xx % 4] - 0.5
    lit = N @ LIGHT
    edge = (D < 1.05) & INS
    convex = edge & (K > 24)
    concave = edge & (K < -24)
    X, Y, Z = L[..., 0], L[..., 1], L[..., 2]
    n_lo = vnoise(L * 0.09 + 3, 1)
    n_mid = vnoise(L * 0.3 + 11, 2)
    n_hi = vnoise(L * 0.9 + 5, 7)
    pit = hash1(xx * 7 + yy * 13 + fi * 3, 5)
    col = np.zeros((H, W, 3))
    glow = np.zeros((H, W), bool)

    def put(mask, c):
        nonlocal col
        col = np.where(mask[..., None], c, col)

    def shaded(pal, base, lw=0.30, nw=0.14, extra=0.0, ed=True):
        v = base + lw * lit + nw * (n_lo - 0.5) + 0.10 * (n_mid - 0.5) + 0.07 * bay + extra
        idx = np.floor(v * len(pal)).astype(int)
        if ed:
            idx = idx + (convex & (lit > 0)) - (convex & (lit <= 0)) - concave
        return idx

    gp = GLOWS[item["glow"]]
    TM = TRIM[item["rarity"]]
    for name in set(mats[owned].tolist()):
        mask = (mats == name) & owned
        if name == "gem":
            put(mask, ramp(gp, np.minimum(0.18 + 0.55 * (0.5 + 0.5 * lit) * (0.7 + 0.6 * n_mid) + 0.08 * bay, 0.88)))
            glow |= mask
            continue
        if name == "trim":
            tidx = shaded(TM, 0.55, 0.30, 0.06) + (1 if plus >= 5 else 0) + (1 if plus >= 10 else 0) + (mask & convex)
            put(mask, pick(TM, tidx))
            continue
        kind, pal = S[item["mats"].get(name, "steel")]
        if kind == "metal":
            hair = np.sin(Y * 3.1 + X * 0.4 + 6 * n_hi) > 0.86
            idx = shaded(pal, 0.58, 0.38, 0.06) + hair - (pit < 0.03)
        elif kind == "wood":
            grain = np.sin(Y * 0.9 + 3.0 * vnoise(L * 0.25 + 7, 3) * 3) > 0.55
            idx = shaded(pal, 0.52, 0.28, 0.10) - grain
        elif kind == "leather":
            idx = shaded(pal, 0.50, 0.26, 0.16) - (n_hi > 0.78)
        elif kind == "cloth":
            weave = ((np.floor(Y * 1.5) + np.floor(X * 1.5 + Z * 1.5)) % 2) == 0
            idx = shaded(pal, 0.52, 0.30, 0.10) - (weave & (n_hi > 0.45))
        elif kind == "rope":
            twist = (np.floor(Y * 1.4 + X * 0.9 + Z * 0.9) % 2) == 0
            idx = shaded(pal, 0.55, 0.28, 0.06) - twist
        elif kind == "fur":
            strand_ = np.sin(X * 4.0 + Z * 4.0 + 5 * n_hi) > 0.3
            idx = shaded(pal, 0.52, 0.30, 0.18) - strand_ + (n_hi > 0.85)
        elif kind == "bone":
            idx = shaded(pal, 0.58, 0.30, 0.12) - (pit < 0.05) - ((n_mid > 0.72) & (n_hi > 0.6))
        elif kind == "shell":
            ridges = np.sin(np.hypot(X, Z) * 2.2 + Y * 0.6) > 0.5
            idx = shaded(pal, 0.55, 0.30, 0.12) - ridges
        elif kind == "nacre":
            shift = 0.25 * np.sin(X * 1.3 + Y * 0.7 + Z * 1.1)
            idx = shaded(pal, 0.60 + shift, 0.30, 0.06)
        elif kind == "crystal":
            facet = hash1(np.floor(X * 1.2) + np.floor(Y * 0.8) * 7 + np.floor(Z * 1.2) * 13 + fi, 4)
            idx = shaded(pal, 0.50 + 0.3 * facet, 0.40, 0.04) + (convex & (lit > 0))
        elif kind == "stone":
            crack = (n_hi > 0.80) & (n_mid > 0.5)
            idx = shaded(pal, 0.52, 0.28, 0.20) - 2 * crack
        else:
            idx = np.floor(1.2 + 1.2 * (0.5 + 0.5 * lit) + 0.8 * bay).astype(int)
        put(mask, pick(pal, idx))
    kinds = (["legend"] if tier == "legend" else []) + (["p5"] if plus >= 5 else []) + (["p10"] if plus >= 10 else [])
    env = dict(L=L, N=N, mats=mats, convex=convex, concave=concave, D=D, INS=INS, K=K, lit=lit, edge=edge, X=X, Y=Y, Z=Z,
               LU=LU, LV=LV, CW=CW, CH=CH, fi=fi)
    if inlay is not None:
        for k in kinds:
            gmask = inlay(env, k) & INS
            put(gmask, ramp(gp, 0.40 + 0.4 * hash1(LU + LV * 3, 2) + (0.15 if k == "p10" else 0.0)))
            glow |= gmask
    if plus >= 10 and edge_mats:
        em = np.isin(mats, list(edge_mats)) & convex & (lit > -0.1)
        put(em, ramp(gp, 0.45 + 0.3 * hash1(xx + yy, 9)))
        glow |= em
    col = np.clip(col, 0, 255)
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = col.astype(np.uint8)
    img[..., 3] = np.where(owned, 255, 0)
    img[..., :3] = fill_outside(img[..., :3], faces, INS, M)
    gl_ = fill_outside(np.stack([glow] * 3, -1).astype(np.uint8), faces, INS, M)[..., 0] > 0
    return img, gl_ & owned
