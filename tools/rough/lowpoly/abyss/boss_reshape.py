"""選んだ見せ方に合わせて形を変える三体のラフ (2026-11-08 の声「モデルの形変えて。もっと作り込んで」)。どれも 3 案。
  深みの王 (喪服の王)   : 深い紫の天鵞絨と金の縁。弔いの王。王冠の上に二重の金の輪 (後光はゲームの描き手)、黒い花びら
     A 長い喪のマント / B 玉座を背負う / C 冠の檻
  心臓の番人 (錆の門番) : 錆びた鉄と古い骨。背に鍵穴の後光、奥に深淵の青緑の光
     A 歩く門 / B 鍵持ちの騎士 / C 鉄の檻
  始祖 (雲の喪章)       : 灰白の雲の体に黒いレースの喪章、白い羽根
     A 鐘の裳 / B 雲の翼の喪主 / C 霧の巡礼
王は五つの元素の王で同じ形を使い、色の差し (光る所) だけ元素ごと。下の二行は周が進んだ姿の見本 (高く伸び・傾き・うねる)。

    python -I -X utf8 tools/rough/lowpoly/abyss/boss_reshape.py [king|warden|founder]  → docs/review/roughs_abyss/<id>.png
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from hum import *  # noqa

VELVET = [(12, 6, 18), (28, 12, 38), (48, 22, 62), (78, 42, 96), (112, 74, 134)]
GOLD = [(96, 66, 20), (150, 108, 36), (204, 160, 64), (240, 206, 110), (255, 236, 170)]
RUSTC = [(30, 16, 10), (62, 32, 20), (100, 54, 30), (140, 84, 48), (176, 116, 70)]
IRONC = [(16, 16, 20), (34, 34, 40), (58, 58, 66), (92, 92, 102)]
CLOUD = [(126, 128, 140), (166, 168, 178), (202, 204, 212), (230, 230, 236), (248, 248, 250)]
LACE = [(4, 4, 6), (12, 10, 14), (24, 22, 26), (40, 36, 42)]
KEYG = [(30, 130, 124), (70, 196, 184), (140, 240, 226), (210, 255, 246)]
WHITEG = [(190, 200, 214), (226, 232, 242), (250, 252, 255)]
WAXC = [(150, 132, 100), (200, 182, 146), (236, 224, 192)]
FLAMEC = [(230, 120, 30), (252, 176, 60), (255, 222, 130), (255, 246, 210)]

EXTRA = {
    "velvet": mat(VELVET, 0.6, crack=0.0, noise=0.14),
    "gold": mat(GOLD, 0.7, crack=0.0, noise=0.08),
    "rust": mat(RUSTC, 0.6, crack=0.16, noise=0.28),
    "iron": mat(IRONC, 0.6, crack=0.06, noise=0.16),
    "cloud": mat(CLOUD, 0.66, crack=0.0, noise=0.1),
    "lace": mat(LACE, 0.5, crack=0.0, noise=0.08),
    "keyg": mat(KEYG, 0.75, crack=0.0, noise=0.06, min=2),
    "whiteg": mat(WHITEG, 0.8, crack=0.0, noise=0.04, min=2),
    "wax": mat(WAXC, 0.6, crack=0.0, noise=0.08),
    "flame": mat(FLAMEC, 0.8, crack=0.0, noise=0.04, min=2),
}


def robe(F, h, name, m, y0, y1, r0, r1, rz=0.8, n=10, flare=0.0, cz=0.0, bone="body"):
    """胴の下から裾へ広がる衣 (輪をつないだ筒)。flare は裾の波 (裾の輪を上下に揺らす量)"""
    rings = []
    steps = 6
    for k in range(steps + 1):
        t = k / steps
        y = y0 + (y1 - y0) * t
        rx = r0 + (r1 - r0) * t ** 1.4
        ring = h.ring(y, rx, rx * rz, cz, n=n)
        if k == steps and flare:
            ring = [p + np.array([0, flare * h.S * math.sin(3 * math.atan2(p[2], p[0])), 0]) for p in ring]
        rings.append(ring)
    F += part(name, loft(bone, m, rings, caps=(m, m)))


def cape(F, h, name, m, top_y, bot_y, w0, w1, z=2.6, th=0.5, bone="body"):
    """背の外套 (平たい筒)。肩から裾へ広がる"""
    rings = []
    for k in range(6):
        t = k / 5
        y = top_y + (bot_y - top_y) * t
        w = w0 + (w1 - w0) * t
        rings.append(h.ring(y, w, th, z + 0.8 * t, n=8))
    F += part(name, loft(bone, m, rings, caps=(m, m)))


def crown(F, h, m, y, r, n=7, tall=2.6, bone="head", glow=None):
    F += part("crown_band", loop(bone, m, 0, y * h.S, 0, r * h.S, r * h.S, r=0.45 * h.S, n=12, gap=8))
    for i in range(n):
        a = 2 * math.pi * i / n
        base = h.P(r * math.sin(a), y, -r * math.cos(a))
        F += part(f"crown_spike{i}", cone(bone, m, base, (math.sin(a) * 0.25, 1, -math.cos(a) * 0.25), (tall + (0.8 if i % 2 == 0 else 0)) * h.S, 0.45 * h.S, sides=4, rings=2))
        if glow and i % 2 == 0:
            F += part(f"crown_gem{i}", ball(bone, glow, h.P(r * math.sin(a), y + 0.3, -r * math.cos(a) - 0.1), 0.42 * h.S, segs=6, rings=2))


# ====================================================================== 深みの王 (喪服の王)
KS = 1.9
kh = H(KS)


def king_a(k):
    """A 長い喪のマント: 床まで届く天鵞絨の衣と背の外套、高い襟、冠の下から垂れる薄布、金の笏"""
    F = []
    h = kh
    h.torso(F, "velvet", chest=4.2, depth=2.5)
    for s in (-1, 1):
        h.arm(F, s, "velvet", "bone", r=1.5, ln=1.1)
    eyes = h.head(F, "bone", "calc", s=1.05)
    robe(F, h, "robe", "velvet", 12.6, 0.4, 3.0, 7.2, flare=0.6)
    F += part("hem", loop("body", "gold", 0, 0.9 * KS, 0, 7.3 * KS, 7.3 * 0.8 * KS, r=0.4 * KS, n=14, gap=6))
    F += part("belt", loop("body", "gold", 0, 12.4 * KS, 0, 3.0 * KS, 2.0 * KS, r=0.35 * KS, n=12, gap=6))
    cape(F, h, "cape", "velvet", 22.0, 0.8, 4.6, 8.0)
    for s in (-1, 1):
        F += part(f"collar{s}", cone("body", "velvet", h.P(s * 2.2, 22.4, 0.6), (s * 0.35, 1, 0.3), 4.2 * KS, 1.0 * KS, sides=4, rings=2))
        F += part(f"pauldron{s}", ellipsoid("body", "gold", h.P(s * 4.6, 21.8, 0.2), (1.7 * KS, 0.9 * KS, 1.6 * KS), segs=8, rings=3))
    crown(F, h, "gold", 26.8, 1.9, n=7, tall=2.8, glow="red")
    F += part("veil", loft("head", "lace", [h.ring(26.4, 2.3, 2.1, 0.3, n=8), h.ring(23.0, 2.9, 2.6, 0.6, n=8), h.ring(20.6, 3.4, 2.8, 0.9, n=8)], caps=("lace", "lace")))
    F += part("sceptre", rod("arm_r", "gold", h.P(5.4, 6.0, -1.4), h.P(5.4, 28.0, -1.4), r=0.4 * KS))
    F += part("sceptre_orb", ball("arm_r", "red", h.P(5.4, 29.0, -1.4), 1.0 * KS))
    return F, eyes


def king_b(k):
    """B 玉座を背負う: 背に玉座の背もたれ (燭台の蝋燭が並ぶ)、前かがみ、長い腕。衣は膝まで"""
    F = []
    h = kh
    h.torso(F, "velvet", chest=4.0, depth=2.4, hunch=1.4)
    for s in (-1, 1):
        h.arm(F, s, "velvet", "bone", r=1.4, ln=1.5, out=0.4)
        h.leg(F, s, "bone", "calc")
    eyes = h.head(F, "bone", "calc", s=1.0, dz=-1.2)
    robe(F, h, "robe", "velvet", 12.6, 5.0, 3.0, 5.4, flare=0.4)
    F += part("hem", loop("body", "gold", 0, 5.4 * KS, 0, 5.5 * KS, 4.4 * KS, r=0.35 * KS, n=12, gap=6))
    F += part("throne", ellipsoid("body", "velvet", h.P(0, 28.0, 3.6), (5.4 * KS, 9.0 * KS, 0.9 * KS), segs=10, rings=4))
    F += part("throne_rim", hoop("body", "gold", 0, 28.0 * KS, 4.0 * KS, 5.6 * KS, 9.2 * KS, r=0.45 * KS, n=16, gap=4))
    for i, x in enumerate((-4.2, -2.1, 0, 2.1, 4.2)):
        top = 34.0 + 2.6 * (1 - abs(x) / 4.2)
        F += part(f"candle{i}", rod("body", "wax", h.P(x, top - 2.4, 3.6), h.P(x, top + 0.6, 3.6), r=0.42 * KS))
        F += part(f"flame{i}", cone("body", "flame", h.P(x, top + 0.6, 3.6), (0, 1, 0), 1.6 * KS, 0.4 * KS, sides=4, rings=2))
    crown(F, h, "gold", 26.4, 1.8, n=5, tall=2.0, glow="red")
    return F, eyes


def king_c(k):
    """C 冠の檻: 頭を包む大きな金の檻の冠、衣は裾で八本の帯に裂けて引きずる、四本の腕で胸の前に弔いの花輪"""
    F = []
    h = kh
    h.torso(F, "velvet", chest=3.9, depth=2.3)
    for s in (-1, 1):
        h.arm(F, s, "velvet", "bone", r=1.35, ln=1.0)
        h.arm(F, s, "velvet", "bone", r=1.1, ln=0.8, out=0.6, fwd=1.6, tag="lo")
    eyes = h.head(F, "bone", "calc", s=0.95)
    robe(F, h, "robe", "velvet", 12.6, 6.0, 3.0, 5.0)
    for i in range(8):
        a = 2 * math.pi * (i + 0.5) / 8
        p0 = np.array(h.P(4.6 * math.sin(a), 6.4, -3.6 * math.cos(a)))
        p1 = np.array(h.P(6.4 * math.sin(a), 2.6, -5.0 * math.cos(a)))
        p2 = np.array(h.P(8.8 * math.sin(a), 0.4, -7.4 * math.cos(a)))
        F += part(f"strip{i}", arc("body", "velvet" if i % 2 else "lace", p0, p1, p2, r=0.9 * KS, n=5, sides=4))
    for i in range(6):
        a = math.pi * i / 6
        F += part(f"cage{i}", hoop("head", "gold", 0, 25.4 * KS, 0, 3.2 * KS, 4.0 * KS, r=0.28 * KS, n=12, gap=10, at=0) if i == 0 else
                  loop("head", "gold", 0, (22.6 + i * 1.3) * KS, 0, (3.0 - abs(i - 2.5) * 0.25) * KS, (3.0 - abs(i - 2.5) * 0.25) * KS, r=0.24 * KS, n=12, gap=8))
    crown(F, h, "gold", 29.4, 1.4, n=5, tall=2.2, glow="red")
    F += part("wreath", loop("body", "lace", 0, 15.0 * KS, -4.6 * KS, 2.6 * KS, 1.0 * KS, r=0.7 * KS, n=12, gap=6))
    for i in range(5):
        a = 2 * math.pi * i / 5
        F += part(f"rose{i}", ball("body", "red", h.P(2.6 * math.sin(a), 15.0 + 1.0 * math.cos(a), -4.6 - 0.6), 0.55 * KS, segs=6, rings=2))
    return F, eyes


# ====================================================================== 心臓の番人 (錆の門番)
WS = 1.9
wh = H(WS)


def warden_a(k):
    """A 歩く門: 胴が錆びた鉄の門 (柱二本と半円の梁、格子)。胸の格子の奥に青緑に光る鍵穴。頭は梁の上の小さな骨"""
    F = []
    h = wh
    for s in (-1, 1):
        F += part(f"pillar{s}", rod("body", "rust", h.P(s * 4.2, 10.0, 0), h.P(s * 4.2, 24.0, 0), r=1.1 * WS, sides=6))
        h.arm(F, s, "iron", "bone", r=1.2, ln=1.3, out=0.8)
        h.leg(F, s, "bone", "calc")
    F += part("arch", arc("body", "rust", h.P(-4.2, 24.0, 0), h.P(0, 30.0, 0), h.P(4.2, 24.0, 0), r=1.0 * WS, n=8, sides=6))
    for i, x in enumerate((-2.4, -0.8, 0.8, 2.4)):
        F += part(f"bar{i}", rod("body", "iron", h.P(x, 10.4, -0.4), h.P(x, 26.0 - abs(x) * 0.8, -0.4), r=0.35 * WS))
    F += part("crossbar", rod("body", "rust", h.P(-4.2, 12.0, -0.4), h.P(4.2, 12.0, -0.4), r=0.6 * WS))
    F += part("keyhole", ball("body", "keyg", h.P(0, 19.6, 0.4), 1.4 * WS))
    F += part("keyhole_slot", cone("body", "keyg", h.P(0, 19.0, 0.4), (0, -1, 0), 3.6 * WS, 1.0 * WS, sides=4, rings=2))
    F += part("backplate", ellipsoid("body", "iron", h.P(0, 18.0, 1.6), (4.4 * WS, 7.4 * WS, 0.7 * WS), segs=8, rings=3))
    eyes = h.head(F, "bone", "calc", s=0.8, dy=6.0)
    # 首: 梁の頂から頭骨の下へ (頭が浮かないように)
    F += part("neck", rod("body", "bone", h.P(0, 29.2, 0), h.P(0, 29.6 + 0.8, 0), r=0.7 * WS))
    F += part("neck_plate", ellipsoid("body", "rust", h.P(0, 29.8, 0), (1.6 * WS, 0.6 * WS, 1.2 * WS), segs=8, rings=2))
    for s in (-1, 1):
        F += part(f"horn{s}", cone("head", "rust", h.P(s * 1.3, 32.0, 0), (s * 0.6, 1, 0.2), 4.0 * WS, 0.55 * WS, sides=4, rings=2))
    return F, eyes


def warden_b(k):
    """B 鍵持ちの騎士: 錆びた鎧の古い骨の騎士。兜の鍵穴の覗き窓が光る。右手に背丈ほどの大鍵、背に鎖の外套"""
    F = []
    h = wh
    h.torso(F, "rust", chest=4.3, depth=2.6)
    for s in (-1, 1):
        h.arm(F, s, "rust", "bone", r=1.55, ln=1.1)
        h.leg(F, s, "rust", "iron")
        F += part(f"pauldron{s}", ellipsoid("body", "iron", h.P(s * 5.0, 21.8, 0), (2.2 * WS, 1.3 * WS, 2.0 * WS), segs=8, rings=3))
        F += part(f"pauldron_spike{s}", cone("body", "rust", h.P(s * 5.6, 22.8, 0), (s * 0.6, 1, 0), 2.6 * WS, 0.5 * WS, sides=4, rings=2))
    F += part("helm", ellipsoid("head", "iron", h.P(0, 25.0, 0), (2.3 * WS, 2.8 * WS, 2.3 * WS), segs=10, rings=4))
    F += part("helm_slot", ellipsoid("head", "keyg", h.P(0, 25.4, -2.1), (0.45 * WS, 0.9 * WS, 0.3 * WS), segs=6, rings=2))
    F += part("helm_slot2", cone("head", "keyg", h.P(0, 24.6, -2.1), (0, -1, 0), 1.6 * WS, 0.35 * WS, sides=4, rings=2))
    eyes = []
    F += part("key_shaft", rod("arm_r", "rust", h.P(5.2, 4.0, -1.6), h.P(5.2, 30.0, -1.6), r=0.55 * WS, sides=6))
    F += part("key_bow", hoop("arm_r", "rust", 5.2 * WS, 32.6 * WS, -1.6 * WS, 2.4 * WS, 2.4 * WS, r=0.6 * WS, n=12, gap=6))
    F += part("key_gem", ball("arm_r", "keyg", h.P(5.2, 32.6, -1.6), 0.9 * WS))
    for i in range(3):
        F += part(f"key_bit{i}", ellipsoid("arm_r", "rust", h.P(6.4, 5.6 + i * 1.5, -1.6), (1.2 * WS, 0.5 * WS, 0.4 * WS), segs=6, rings=2))
    for i in range(7):
        x = -4.2 + i * 1.4
        for j in range(4):
            F += part(f"chain{i}_{j}", hoop("body", "iron", x * WS, (20.0 - j * 3.2) * WS, 2.9 * WS, 0.55 * WS, 0.8 * WS, r=0.18 * WS, n=6, gap=20))
    return F, eyes


def warden_c(k):
    """C 鉄の檻: 胸が鉄の檻で、中に青緑に光る心臓が浮く。長い古い骨の手足、鍵穴の形に曲がった角"""
    F = []
    h = wh
    for s in (-1, 1):
        h.arm(F, s, "bone", "calc", r=1.0, ln=1.6, out=0.3)
        h.leg(F, s, "bone", "calc", ln=1.2)
    for i in range(6):
        a = math.pi * i / 6
        F += part(f"cagebar{i}", hoop("body", "rust", 0, 17.4 * WS, 0, (3.8 * abs(math.cos(a)) + 0.4) * WS, 6.2 * WS, r=0.35 * WS, n=12, gap=8) if False else
                  arc("body", "rust", h.P(3.8 * math.cos(a) * 0.3, 11.0, 2.4 * math.sin(a) - 1.2), h.P(4.6 * math.cos(a), 17.4, 3.2 * math.sin(a) - 1.6),
                      h.P(2.0 * math.cos(a) * 0.5, 23.4, 1.6 * math.sin(a) - 0.8), r=0.4 * WS, n=6, sides=5))
    for y in (11.2, 17.4, 23.0):
        F += part(f"cagering{y}", loop("body", "iron", 0, y * WS, 0, (4.2 if y == 17.4 else 2.4) * WS, (3.0 if y == 17.4 else 1.8) * WS, r=0.35 * WS, n=12, gap=6))
    F += part("spine", rod("body", "bone", h.P(0, 10.0, 1.6), h.P(0, 24.0, 1.6), r=0.7 * WS))
    F += part("heart", ellipsoid("body", "keyg", h.P(0, 17.6, 0), (1.8 * WS, 2.2 * WS, 1.6 * WS), segs=8, rings=4))
    eyes = h.head(F, "bone", "calc", s=1.0)
    for s in (-1, 1):
        F += part(f"horn{s}", arc("head", "rust", h.P(s * 1.4, 26.4, 0), h.P(s * 4.0, 29.0, 0.4), h.P(s * 2.4, 31.6, 0.2), r=0.6 * WS, n=6, taper=(0.6 * WS, 0.15 * WS)))
    return F, eyes


# ====================================================================== 始祖 (雲の喪章)
FS = 1.7
fh = H(FS)


def founder_a(k):
    """A 鐘の裳: 腰から下が鐘の形の裳 (雲の白)。頭巾と顔を覆う黒いレース。手に鐘を下げた長い杖。裳に黒いレースの帯が二重に巻く"""
    F = []
    h = fh
    h.torso(F, "cloud", chest=3.4, depth=2.0, waist=2.2)
    for s in (-1, 1):
        h.arm(F, s, "cloud", "cloud", r=1.2, ln=1.1)
    eyes = h.head(F, "cloud", "cloud", s=0.95, hollow=False)
    rings = [h.ring(12.6, 2.4, 1.8, n=12), h.ring(10.0, 3.6, 3.0, n=12), h.ring(6.0, 5.6, 4.8, n=12), h.ring(2.0, 7.4, 6.4, n=12), h.ring(0.6, 8.2, 7.0, n=12)]
    F += part("bell_skirt", loft("body", "cloud", rings, caps=("cloud", "cloud")))
    F += part("lip", loop("body", "lace", 0, 1.0 * FS, 0, 8.3 * FS, 7.1 * FS, r=0.45 * FS, n=16, gap=4))
    for y, rx in ((5.0, 6.2), (8.6, 4.4)):
        F += part(f"band{y}", loop("body", "lace", 0, y * FS, 0, rx * FS, rx * 0.86 * FS, r=0.3 * FS, n=14, gap=6))
    F += part("hood", loft("head", "cloud", [h.ring(27.4, 1.0, 1.0, 0.4, n=8), h.ring(26.0, 2.5, 2.4, 0.3, n=8), h.ring(23.0, 2.8, 2.7, 0.6, n=8), h.ring(21.0, 3.2, 2.8, 0.8, n=8)], caps=("cloud", "cloud")))
    F += part("veil", ellipsoid("head", "lace", h.P(0, 24.2, -1.9), (1.7 * FS, 2.0 * FS, 0.35 * FS), segs=8, rings=3))
    F += part("staff", rod("arm_r", "lace", h.P(5.4, 1.0, -1.4), h.P(5.4, 32.0, -1.4), r=0.32 * FS))
    F += part("staff_bell", loft("arm_r", "cloud", [h.ring(31.0, 0.3, 0.3, -1.4, n=8, cx=5.4), h.ring(29.6, 1.0, 1.0, -1.4, n=8, cx=5.4), h.ring(28.0, 1.6, 1.6, -1.4, n=8, cx=5.4)], caps=("cloud", "cloud")))
    F += part("staff_clapper", ball("arm_r", "whiteg", h.P(5.4, 27.6, -1.4), 0.5 * FS))
    return F, eyes


def founder_b(k):
    """B 雲の翼の喪主: 背に雲の羽を扇に重ねた翼。顔は幾重もの黒いレースの薄布。腕から黒い帯が長く垂れる"""
    F = []
    h = fh
    h.torso(F, "cloud", chest=3.5, depth=2.1)
    for s in (-1, 1):
        h.arm(F, s, "cloud", "cloud", r=1.25, ln=1.0)
        h.leg(F, s, "cloud", "cloud")
        for i in range(6):
            # 肩甲骨から外と下へ扇に開く (いちばん上の羽でも頭の高さまで)
            a = math.radians(-50 + i * 16)
            c = h.P(s * (4.0 + 5.4 * math.cos(a)), 19.0 + 5.0 * math.sin(a), 2.8 + i * 0.2)
            F += part(f"wing{s}_{i}", ellipsoid("body", "cloud" if i % 2 else "whiteg", c, ((4.6 - i * 0.3) * FS, 1.0 * FS, 0.5 * FS), segs=8, rings=3, rot=None))
            if i == 0:
                F += part(f"wing_root{s}", rod("body", "cloud", h.P(s * 2.4, 20.0, 1.8), c, r=0.8 * FS))
        p0 = np.array(h.P(s * 5.6, 15.0, -0.2))
        F += part(f"ribbon{s}", arc(f"arm_{'l' if s < 0 else 'r'}", "lace", p0, p0 + np.array(h.P(s * 1.6, -6.0, 1.0)), p0 + np.array(h.P(s * 0.6, -13.0, 2.4)), r=0.45 * FS, n=6, sides=4))
    eyes = h.head(F, "cloud", "cloud", s=0.95, hollow=False)
    for i in range(3):
        F += part(f"veil{i}", loft("head", "lace", [h.ring(27.0 - i * 0.6, 2.2 + i * 0.3, 2.1 + i * 0.3, 0.2, n=8), h.ring(22.0 - i * 1.6, 2.8 + i * 0.5, 2.6 + i * 0.4, 0.4, n=8)], caps=("lace", "lace")))
    robe(F, h, "robe", "cloud", 12.4, 1.0, 2.8, 4.6)
    return F, eyes


def founder_c(k):
    """C 霧の巡礼: 背の高い痩せた姿、上へ尖る長い頭巾。裾は雲の塊に溶ける。肩から黒いレースの細い帯が何本も垂れる"""
    F = []
    h = fh
    h.torso(F, "cloud", chest=3.0, depth=1.9, waist=2.0)
    for s in (-1, 1):
        h.arm(F, s, "cloud", "cloud", r=1.0, ln=1.4)
    eyes = h.head(F, "cloud", "cloud", s=0.9, hollow=False)
    F += part("hood", cone("head", "cloud", h.P(0, 25.6, 0.4), (0, 1, 0.35), 10.0 * FS, 2.6 * FS, sides=8, rings=4))
    F += part("face_veil", ellipsoid("head", "lace", h.P(0, 24.0, -1.8), (1.5 * FS, 2.2 * FS, 0.3 * FS), segs=8, rings=3))
    robe(F, h, "robe", "cloud", 12.4, 2.0, 2.6, 4.2)
    for i in range(9):
        a = 2 * math.pi * i / 9
        F += part(f"puff{i}", ellipsoid("body", "cloud" if i % 3 else "whiteg", h.P(4.6 * math.sin(a), 1.4 + (i % 2) * 0.8, -4.0 * math.cos(a)), (2.4 * FS, 1.4 * FS, 2.2 * FS), segs=8, rings=3))
    for i in range(8):
        x = -3.4 + i * 0.97
        p0 = np.array(h.P(x, 21.8, 0.6 + abs(x) * 0.2))
        F += part(f"strip{i}", arc("body", "lace", p0, p0 + np.array(h.P(x * 0.15, -8.0, 0.8)), p0 + np.array(h.P(x * 0.3, -17.0 + (i % 3) * 2.0, 1.4)), r=0.25 * FS, n=6, sides=4))
    return F, eyes


SPECS = {
    "king": dict(build={"a": king_a, "b": king_b, "c": king_c}, S=KS, h=kh, glow=("red", "flame"),
                 spec=dict(id="reshape_king", short="深みの王 (喪服の王)", kind="形の 3 案",
                           title="深みの王「喪服の王」— 形を変える 3 案",
                           sub=["選んだ見せ方 A 喪服の王 (深い紫の天鵞絨と金の縁) の上で、形を作り直す。五つの元素の王は同じ形で、光る所の色だけ元素ごと。",
                                "冠の上の二重の金の輪・降る黒い花びらは、ゲームの描き手が足す。下の二行は周が進んだ姿。"],
                           labels=["A 長い喪のマント", "B 玉座を背負う", "C 冠の檻"],
                           options={"a": dict(name="A 長い喪のマント", desc="床まで届く衣と外套、高い襟、冠から垂れる薄布、金の笏。いちばん王らしい。"),
                                    "b": dict(name="B 玉座を背負う", desc="背に玉座の背もたれと燭台の蝋燭。前かがみで腕が長い。叩きつけが映える。"),
                                    "c": dict(name="C 冠の檻", desc="頭を包む金の檻の冠、裾が八本の帯に裂ける、四本の腕で弔いの花輪。"),},
                           extra="喪の言葉 (天鵞絨・金・黒いレース・赤い宝石)。", budget="王 900〜1600", pw=380, king_h=58.0)),
    "warden": dict(build={"a": warden_a, "b": warden_b, "c": warden_c}, S=WS, h=wh, glow=("keyg",),
                   spec=dict(id="reshape_warden", short="心臓の番人 (錆の門番)", kind="形の 3 案",
                             title="心臓の番人「錆の門番」— 形を変える 3 案",
                             sub=["選んだ見せ方 B 錆の門番 (錆びた鉄と古い骨、奥に深淵の青緑の光) の上で、形を作り直す。",
                                  "背の鍵穴の後光はゲームの描き手が足す。"],
                             labels=["A 歩く門", "B 鍵持ちの騎士", "C 鉄の檻"],
                             options={"a": dict(name="A 歩く門", desc="胴が錆びた門。格子の奥に光る鍵穴。門そのものが歩く。"),
                                      "b": dict(name="B 鍵持ちの騎士", desc="錆びた鎧の骨の騎士。兜の鍵穴が光る。背丈ほどの大鍵と鎖の外套。"),
                                      "c": dict(name="C 鉄の檻", desc="胸が鉄の檻で、中に光る心臓。長い骨の手足、鍵穴の形の角。"),},
                             extra="門の言葉 (錆・鉄・古い骨・青緑の光)。", budget="1200〜2000", pw=380, king_h=58.0)),
    "founder": dict(build={"a": founder_a, "b": founder_b, "c": founder_c}, S=FS, h=fh, glow=("whiteg",),
                    spec=dict(id="reshape_founder", short="始祖 (雲の喪章)", kind="形の 3 案",
                              title="終章 始祖「雲の喪章」— 形を変える 3 案",
                              sub=["選んだ見せ方 C 雲の喪章 (灰白の雲の体に黒いレース) の上で、形を作り直す。最後の鐘守・巡礼者も同じ言葉で揃える。",
                                   "降る白い羽根はゲームの描き手が足す。"],
                              labels=["A 鐘の裳", "B 雲の翼の喪主", "C 霧の巡礼"],
                              options={"a": dict(name="A 鐘の裳", desc="腰から下が鐘の形。顔を覆う黒いレース、鐘を下げた杖。鐘の主らしい。"),
                                       "b": dict(name="B 雲の翼の喪主", desc="背に雲の羽を重ねた翼、幾重もの薄布、腕から垂れる黒い帯。"),
                                       "c": dict(name="C 霧の巡礼", desc="背の高い痩せた姿、尖った頭巾、裾が雲に溶け、黒い細帯が何本も垂れる。"),},
                              extra="雲の言葉 (雲の白・黒いレース・白い光)。", budget="900〜1500", pw=380, king_h=58.0)),
}


def main(keys):
    for key in keys or list(SPECS):
        d = SPECS[key]
        spec = dict(d["spec"])
        spec["poses"] = {"idle": {}}
        pal = PALX(EXTRA, glow=d["glow"])
        bones_ = bones(d["S"])
        rows = king_rows((0, 36, 0), 3.9)
        go(spec, lambda k, b=d["build"]: b[k](k), pal, bones_, rows, hint=192, pupil=[(200, 60, 60), (255, 200, 200)])


if __name__ == "__main__":
    main(sys.argv[1:])
