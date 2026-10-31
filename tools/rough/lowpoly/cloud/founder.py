# -*- coding: utf-8 -*-
"""初代の空見 (founder) 低ポリ 一品一案。裏ボス (docs/design/53)。
鐘の国の始まりの人。見上げすぎて目を失い、麻の布を巻いて、布に開いた目を描いている。身長 2.4 ブロック (人の大きさ)。細身でまっすぐ立つ。
生成りの麻の長衣 (前は開いて藍の下衣。暗い青銅の鐘の内で姿が浮く)、星図を刺した藍の肩掛け (後ろに長い二本の垂れ)、白い髪を低く結ぶ (金の留め)。
右手に見張りの竿 (金の照準の輪の中に小鐘、上に青銅の穂先、下に石突き)。左の腰に小鐘 (本物だけが鳴らす)、右の腰に帳面。
最後の鐘守 (背に大鐘・頭巾・漆喰の面・槌) と並べて、別の人に見えること。
骨: root > body > head / arm_l / arm_r > pole / shawl / hipbell / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

INDIGO = [(14, 18, 40), (24, 32, 64), (38, 50, 92), (58, 74, 126)]
LINEN = [(136, 128, 110), (176, 168, 148), (208, 202, 184), (232, 228, 214)]
SKIN = [(150, 124, 108), (186, 160, 142), (214, 192, 174), (234, 216, 200)]
HAIR = [(150, 150, 156), (186, 186, 192), (214, 214, 218), (238, 238, 242)]

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 17, 0)), "head": ("body", (0, 30.5, 0)),
         "arm_l": ("body", (-4.6, 28.4, 0)), "arm_r": ("body", (4.6, 28.4, 0)), "pole": ("arm_r", (6.0, 17.6, -1.8)),
         "shawl": ("body", (0, 29.0, 0.5)), "hipbell": ("body", (-4.0, 18.6, -0.6)),
         "leg_l": ("body", (-1.8, 17, 0)), "leg_r": ("body", (1.8, 17, 0))}
PAL = make_pal({
    "indigo": (INDIGO, 0.5, dict(crack=0.03, noise=0.16)),
    "linen": (LINEN, 0.62, dict(crack=0.04, noise=0.14)),
    "skin": (SKIN, 0.6, dict(crack=0.02, noise=0.08)),
    "hair": (HAIR, 0.62, dict(crack=0.0, noise=0.2)),
    "gold": (GOLDW, 0.72, dict(crack=0.03, noise=0.12)),
    "bronze": (BRONZE, 0.66, dict(crack=0.08, noise=0.18)),
    "verdi": (VERDI, 0.55, dict(crack=0.06, noise=0.22)),
    "leather": (LEATHER, 0.55, dict(crack=0.05, noise=0.16)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def hoop(bone, mat, y, rx, rz, cx=0.0, zc=0.0, r=0.4, n=14):
    """水平の輪 (縁取り)"""
    pts = [(cx + math.cos(a) * rx, y, zc + math.sin(a) * rz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=r, sides=4, hint=(0, 1, 0))


def vhoop(bone, mat, cx, cy, cz, r, rr=0.32, n=16):
    """縦の輪 (x-y の面。照準の輪)"""
    pts = [(cx + math.cos(a) * r, cy + math.sin(a) * r, cz) for a in [i / n * 2 * math.pi for i in range(n + 1)]]
    return tube(bone, mat, pts, ra=rr, sides=4, hint=(0, 0, 1))


def build(k):
    F = []
    # 藍の長衣: 裾は広がり、腰で細く、胸と肩は細身
    rings = [ring(0.6, 4.9, -4.6, 4.8, n=12), ring(6.0, 4.3, -4.0, 4.2, n=12), ring(13.0, 3.5, -3.1, 3.3, n=12),
             ring(18.6, 3.0, -2.5, 2.6, n=12), ring(23.0, 3.5, -2.6, 2.6, n=12), ring(27.4, 4.2, -2.6, 2.7, n=12),
             ring(29.4, 3.4, -2.2, 2.3, n=12), ring(30.6, 1.4, -1.2, 1.2, n=12)]
    F += part("robe", loft("body", "linen", rings, caps=("hollow", "linen")))
    # 前の開きから見える藍の下衣 (裾から腰まで。長衣に沈める)
    under = [ring(0.8, 1.9, -4.75, -3.6, n=8), ring(6.0, 1.6, -4.15, -3.0, n=8), ring(13.0, 1.2, -3.25, -2.2, n=8),
             ring(18.4, 0.9, -2.62, -1.8, n=8)]
    F += part("under", loft("body", "indigo", under, caps=("indigo", "indigo")))
    # 開きの金の縁 (左右)
    for s in (-1, 1):
        F += part(f"edge{s}", tube("body", "gold", [(s * 2.0, 1.0, -4.72), (s * 1.7, 6.0, -4.12), (s * 1.3, 13.0, -3.22),
                                                   (s * 1.0, 18.6, -2.6), (s * 1.6, 23.0, -2.7), (s * 1.1, 27.6, -2.72)],
                                    ra=0.32, sides=4, hint=(1, 0, 0)))
    F += part("hem", hoop("body", "gold", 1.0, 4.95, 4.75, zc=0.1, r=0.38))
    # 藍の帯と金の留め
    F += part("sash", hoop("body", "indigo", 18.8, 3.1, 2.65, r=0.75))
    F += part("clasp", ellipsoid("body", "gold", (0, 18.8, -3.3), (0.8, 0.8, 0.4), segs=6, rings=2))
    # 足先 (裾から少し出る。布を巻いた足)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"foot{s}", ellipsoid(b, "linen", (s * 1.9, 0.7, -4.2), (1.05, 0.7, 1.6), segs=6, rings=2))
    # 星図の肩掛け: 肩をくるむ短い覆いと、後ろへ長い二本の垂れ
    cape = [ring(25.0, 4.9, -2.9, 3.4, n=14), ring(27.6, 4.8, -2.9, 3.3, n=14), ring(29.6, 3.9, -2.5, 2.8, n=14),
            ring(30.8, 2.0, -1.5, 1.6, n=14)]
    F += part("cape", loft("shawl", "indigo", cape, caps=("hollow", "indigo")))
    F += part("capehem", hoop("shawl", "gold", 25.1, 4.95, 3.25, zc=0.25, r=0.34, n=16))
    for s in (-1, 1):
        tail = [ring(28.4, 1.5, 2.2, 3.1, cx=s * 1.9), ring(22.0, 1.6, 3.0, 3.8, cx=s * 2.2), ring(15.0, 1.7, 3.7, 4.5, cx=s * 2.4),
                ring(9.0, 1.6, 4.4, 5.2, cx=s * 2.6)]
        F += part(f"tail{s}", loft("shawl", "indigo", tail, caps=("indigo", "indigo")))
        F += part(f"tailend{s}", tube("shawl", "gold", [(s * 2.6 - 1.6, 8.8, 4.8), (s * 2.6 + 1.6, 8.8, 4.8)], ra=0.3, sides=4, hint=(0, 1, 0)))
    # 星 (肩掛けに刺した金の点。光るのは描いた目と星だけ)
    stars = [(-3.8, 27.0, -2.4), (-2.2, 29.2, -2.2), (3.4, 28.2, -2.6), (2.0, 26.0, -2.95), (-4.4, 26.2, 1.2),
             (4.2, 26.6, 2.0), (0.0, 29.6, 2.6), (-2.3, 20.0, 3.6), (2.6, 17.0, 4.2), (-2.6, 12.0, 4.8), (2.4, 11.0, 4.9)]
    for i, (x, y, z) in enumerate(stars):
        F += part(f"star{i}", ellipsoid("shawl", "dawn", (x, y, z), (0.32, 0.32, 0.32), segs=4, rings=2))
    # 首と頭
    F += part("neck", tube("head", "skin", [(0, 29.8, 0), (0, 31.6, -0.1)], ra=0.95, sides=6, hint=(1, 0, 0)))
    F += part("face", ellipsoid("head", "skin", (0, 33.6, -0.2), (2.15, 2.6, 2.3), segs=10, rings=5))
    # 白い髪 (後ろへ流し、低く結ぶ)
    F += part("hair", ellipsoid("head", "hair", (0, 34.4, 0.45), (2.35, 2.55, 2.4), segs=10, rings=5))
    F += part("tie", hoop("head", "gold", 31.2, 0.62, 0.62, zc=2.6, r=0.28, n=8))
    F += part("tail_hair", tube("head", "hair", [(0, 32.4, 2.2), (0, 30.0, 2.75), (0, 26.4, 3.0), (0, 23.4, 3.25)],
                                ra=[0.95, 0.75, 0.62, 0.3], sides=6, hint=(1, 0, 0)))
    # 目隠しの麻の布 (目の高さを一巻き) と、布に描いた開いた目 (光る)
    F += part("band", hoop("head", "linen", 33.9, 2.22, 2.38, zc=-0.2, r=0.62, n=16))
    F += part("knot", ellipsoid("head", "linen", (0, 33.9, 2.4), (0.7, 0.6, 0.5), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"bandtail{s}", tube("head", "linen", [(s * 0.3, 33.6, 2.5), (s * 0.7, 31.0, 3.1), (s * 0.9, 28.6, 3.4)],
                                       ra=[0.42, 0.34, 0.22], sides=4, hint=(0, 0, 1)))
    F += part("eyerim", ellipsoid("head", "gold", (0, 33.9, -2.82), (1.55, 0.7, 0.22), segs=10, rings=2))
    F += part("eye", ellipsoid("head", "dawn", (0, 33.9, -2.98), (1.3, 0.5, 0.2), segs=10, rings=2))
    F += part("pupil", ellipsoid("head", "hollow", (0, 33.9, -3.14), (0.42, 0.42, 0.12), segs=6, rings=2))
    # 腕 (広い袖口の麻の袖)
    F += part("upl", tube("arm_l", "linen", [(-4.8, 28.2, 0), (-5.6, 22.6, -0.2)], ra=[1.35, 1.15], sides=6, hint=(1, 0, 0)))
    F += part("fol", tube("arm_l", "linen", [(-5.6, 22.6, -0.2), (-5.3, 18.2, -1.6)], ra=[1.15, 1.7], sides=6, hint=(1, 0, 0)))
    F += part("cuffl", hoop("arm_l", "gold", 18.5, 1.72, 1.72, cx=-5.3, zc=-1.6, r=0.26, n=10))
    F += part("handl", ellipsoid("arm_l", "skin", (-5.2, 17.2, -1.9), (0.85, 1.15, 0.85), segs=6, rings=2))
    F += part("upr", tube("arm_r", "linen", [(4.8, 28.2, 0), (5.7, 22.6, -0.6)], ra=[1.35, 1.15], sides=6, hint=(1, 0, 0)))
    F += part("for", tube("arm_r", "linen", [(5.7, 22.6, -0.6), (6.0, 18.6, -1.8)], ra=[1.15, 1.7], sides=6, hint=(1, 0, 0)))
    F += part("cuffr", hoop("arm_r", "gold", 18.9, 1.72, 1.72, cx=6.0, zc=-1.8, r=0.26, n=10))
    F += part("handr", ellipsoid("arm_r", "skin", (6.0, 17.6, -1.8), (0.95, 1.1, 0.95), segs=6, rings=2))
    # 見張りの竿: 細い柄、上に金の照準の輪 (中に小鐘) と十字の目当て、その上に青銅の穂先、下に石突き
    PX, PZ = 6.0, -1.8
    F += part("shaft", tube("pole", "leather", [(PX, 1.4, PZ), (PX, 41.0, PZ)], ra=0.42, sides=6, hint=(1, 0, 0)))
    F += part("grip", tube("pole", "linen", [(PX, 15.6, PZ), (PX, 19.6, PZ)], ra=0.5, sides=6, hint=(1, 0, 0)))
    F += part("ferrule", tube("pole", "bronze", [(PX, 0.2, PZ), (PX, 1.8, PZ)], ra=[0.18, 0.55], sides=6, hint=(1, 0, 0)))
    F += part("collar", tube("pole", "gold", [(PX, 40.4, PZ), (PX, 41.6, PZ)], ra=0.62, sides=6, hint=(1, 0, 0)))
    F += part("sight", vhoop("pole", "gold", PX, 44.0, PZ, 2.5, rr=0.34))
    F += part("sight2", vhoop("pole", "gold", PX, 44.0, PZ, 1.7, rr=0.18))
    F += part("crossh", tube("pole", "gold", [(PX - 2.5, 44.0, PZ), (PX + 2.5, 44.0, PZ)], ra=0.16, sides=4, hint=(0, 1, 0)))
    F += part("smallbell", loft("pole", "bronze", [ring(42.2, 0.95, PZ - 0.95, PZ + 0.95, cx=PX, n=10), ring(43.0, 0.75, PZ - 0.75, PZ + 0.75, cx=PX, n=10),
                                                    ring(44.4, 0.62, PZ - 0.62, PZ + 0.62, cx=PX, n=10), ring(45.2, 0.3, PZ - 0.3, PZ + 0.3, cx=PX, n=10)],
                                    caps=("hollow", "bronze")))
    F += part("bellband", hoop("pole", "verdi", 43.6, 0.7, 0.7, cx=PX, zc=PZ, r=0.14, n=10))
    F += part("blade", tube("pole", "bronze", [(PX, 46.3, PZ), (PX, 48.6, PZ), (PX, 51.4, PZ)], ra=[0.62, 0.55, 0.05], sides=4, hint=(0, 0, 1)))
    # 左の腰の小鐘 (金の紐で帯から下がる)
    F += part("cord", tube("hipbell", "gold", [(-3.0, 18.8, -1.6), (-3.8, 17.6, -2.0), (-4.1, 16.6, -2.1)], ra=0.16, sides=4, hint=(0, 0, 1)))
    F += part("hb", loft("hipbell", "bronze", [ring(13.9, 1.0, -3.1, -1.1, cx=-4.1, n=10), ring(14.6, 0.85, -2.95, -1.25, cx=-4.1, n=10),
                                               ring(15.8, 0.7, -2.8, -1.4, cx=-4.1, n=10), ring(16.7, 0.36, -2.46, -1.74, cx=-4.1, n=10)],
                          caps=("hollow", "bronze")))
    F += part("hbband", hoop("hipbell", "verdi", 15.2, 0.8, 0.72, cx=-4.1, zc=-2.1, r=0.12, n=10))
    # 右の腰の帳面 (革の表紙、金の留め)
    F += part("book", tube("body", "leather", [(3.0, 15.8, -0.4), (4.2, 15.8, -0.4)], ra=1.5, sides=4, hint=(0, 1, 0)))
    F += part("pages", tube("body", "linen", [(4.15, 15.8, -0.4), (4.4, 15.8, -0.4)], ra=1.3, sides=4, hint=(0, 1, 0)))
    F += part("bookclasp", tube("body", "gold", [(4.5, 15.0, -1.3), (4.5, 16.6, -1.3)], ra=0.2, sides=4, hint=(1, 0, 0)))
    F += part("strap", hoop("body", "leather", 17.4, 3.05, 2.6, r=0.22, n=14))
    return F, []


POSES = {
    "idle": {},
    "thrust": {"arm_r": (80, 0, 0), "pole": (-166, 0, 0), "body": (8, -20, 0), "head": (-6, 15, 0), "arm_l": (-20, 0, -15)},
    "low": {"arm_r": (-88, 0, 0), "pole": (0, 0, 90), "body": (6, -35, 0), "head": (0, 25, 0), "arm_l": (-60, 0, -30)},
    "listen": {"arm_l": (-35, 0, 20), "hipbell": (0, 0, 12), "head": (6, -25, 10)},
}

SPEC = dict(
    id="founder", short="初代の空見", kind="裏ボス 一品一案 (53)",
    title="初代の空見 (founder) — 目隠しに描いた目、星図の肩掛け、見張りの竿・低ポリ + ドット絵 一案",
    sub=["鐘の国の始まりの人。見上げすぎて目を失い、布に開いた目を描く。数え歌の七行を技に、人の大きさで一騎打ち。",
         "麻の長衣に藍の下衣、星図を刺した藍の肩掛け、白い髪を低く結ぶ。照準の輪に小鐘の竿、腰に小鐘と帳面。身長 2.4 ブロック。"],
    labels=["初代の空見"],
    rows=[("前 3/4", "idle", 212, 8, 480, (0, 22, 0), 9.6),
          ("三連の突き (横)", "thrust", 270, 6, 480, (0, 26, -14), 6.4),
          ("ふたつ (竿を低く回す)", "low", 200, 6, 480, (0, 20, 0), 8.8),
          ("聞き耳 / 後ろ 3/4", "listen", 32, 10, 480, (0, 22, 0), 9.6)],
    options={"a": dict(name="目隠しの空見", desc="麻の長衣と星図の藍の肩掛け、麻の目隠しに光る目、見張りの竿 (照準の輪と小鐘)。")},
    extra="雲海の言葉 (鐘の青銅・金・朝の光) に藍と麻を足す。骨 10 本。光るのは描いた目と肩掛けの星だけ。",
    budget="主 1500〜3000", pw=440,
    later="段 3 = 描いた目が強く光り、肩掛けの星が増える。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
