"""宿借り (drift_hermit) 低ポリ ラフ 3 案。追いかけっこ (敵意なし)。HP 14。
流木の殻を背負った小さな宿借り。人を見ると殻に潜る。殻を叩くと拾い物を吐く。3〜5 匹で浜を行列で歩く。
A 流木の洞 (中の空いた流木の切れ株・節とフジツボ) / B 流木の巻き殻 (流木を巻いた巻貝の形) / C 浮き玉の殻 (流木の枠に古い硝子の浮き玉と珊瑚の芽)
骨: root > body > shell / head / claw_l (大) / claw_r / leg_l1 / leg_l2 / leg_r1 / leg_r2。潜る = 頭・鋏・脚が殻の中へ。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck2 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 3.0, 0)), "shell": ("body", (0, 4.0, 2.0)),
         "head": ("body", (0, 3.6, -2.6)), "claw_l": ("body", (-1.6, 2.8, -2.8)), "claw_r": ("body", (1.6, 2.8, -2.8)),
         "leg_l1": ("body", (-1.8, 2.6, -1.2)), "leg_l2": ("body", (-1.8, 2.6, 0.4)),
         "leg_r1": ("body", (1.8, 2.6, -1.2)), "leg_r2": ("body", (1.8, 2.6, 0.4))}
PAL = {"mats": {
    "wood": mat(DRIFT, 0.64, crack=0.0, noise=0.2, stripe=3, axis="v", stain=dict(ramp=SALT, thr=0.93, up=0.4, conc=0.2)),
    "knot": mat(DRIFT, 0.4, crack=0.0, min=0),
    "barn": mat(SALT, 0.6, crack=0.0, dots=0.2, min=0),
    "crab": mat(CORAL, 0.66, crack=0.0, noise=0.15),
    "claw": mat(CORAL, 0.76, crack=0.0, noise=0.1),
    "tip": mat(SALT, 0.78, crack=0.0),
    "glass": mat([(30, 90, 96), (60, 150, 150), (120, 210, 200), (190, 246, 236)], 0.66, crack=0.0, min=1),
    "teal": mat(TEAL, 0.62, crack=0.0),
    "dark": mat(DEEP, 0.5, crack=0.0, min=0),
}, "skull_bone": "head", "face_mat": "crab", "glow_mats": []}


def crab(F, big=1.0):
    """宿借りの体の前半 (殻から出ている所): 頭・目の柄・鋏 2 (左が大きい)・脚 4。"""
    F += part("thorax", ellipsoid("body", "crab", (0, 3.4, -1.4), (2.0, 1.4, 1.8), segs=6, rings=1))
    F += part("head", ellipsoid("head", "crab", (0, 3.8, -3.0), (1.3, 0.9, 1.0), segs=6, rings=1))
    eyes = []
    for s in (-1, 1):
        F += part(f"stalk{s}", tube("head", "crab", [(s * 0.5, 4.4, -3.4), (s * 0.9, 6.0, -3.8)], ra=[0.28, 0.24], sides=3))
        F += part(f"eye{s}", ellipsoid("head", "dark", (s * 0.95, 6.3, -3.85), (0.42, 0.42, 0.42), segs=4, rings=1))
        F += part(f"ant{s}", tube("head", "teal", [(s * 0.3, 3.9, -3.9), (s * 1.2, 4.6, -6.0), (s * 2.0, 4.0, -7.4)], ra=[0.15, 0.12, 0.05], sides=3))
    for s, b, sc in ((-1, "claw_l", 1.35 * big), (1, "claw_r", 0.85)):
        F += part(f"arm{s}", tube(b, "claw", [(s * 1.6, 2.8, -2.8), (s * 2.2, 2.2, -4.0), (s * 1.8, 1.8, -4.6)], ra=[0.5 * sc, 0.45 * sc, 0.45 * sc], sides=4))
        F += part(f"pincer{s}", ellipsoid(b, "claw", (s * 1.8, 1.8, -5.0), (0.9 * sc, 0.7 * sc, 1.1 * sc), segs=5, rings=1))
        F += part(f"tip{s}", spike(b, "tip", (s * 1.8, 1.8, -5.8 * (1 + 0.05 * (sc - 1))), (0, 0, -1), 1.0 * sc, 0.35 * sc, sides=3, rings=1))
    for s, nm in ((-1, "l"), (1, "r")):
        for j, z in enumerate((-1.2, 0.4)):
            b = f"leg_{nm}{j + 1}"
            F += part(b, tube(b, "crab", [(s * 1.8, 2.6, z), (s * 3.2, 2.8, z - 0.4), (s * 3.9, 0.0, z - 0.6)], ra=[0.36, 0.3, 0.1], sides=3))
    return eyes


def build_a(k):
    """A 流木の洞: 中の空いた流木の切れ株を背負う。節・割れ・フジツボ、白く乾いた塩の跡。"""
    F = []
    eyes = crab(F)
    F += part("log", tube("shell", "wood", [(0, 4.6, -0.6), (0, 4.8, 2.4), (0, 4.6, 5.6)], ra=[2.9, 3.2, 2.7], rb=[2.6, 2.9, 2.4], sides=7, hint=(0, 1, 0), caps=("dark", "wood")))
    F += part("knot", ellipsoid("shell", "knot", (2.4, 6.4, 2.6), (0.8, 0.8, 0.8), segs=5, rings=1))
    F += part("barn", lumps("shell", "barn", [((-2.0, 6.8, 1.4), 0.6), ((-1.0, 7.4, 3.6), 0.5), ((1.4, 7.2, 4.6), 0.45)]))
    F += part("twig", spike("shell", "wood", (-1.6, 7.0, 4.2), (-0.4, 1, 0.6), 2.6, 0.45, sides=3, rings=1))
    return F, eyes


def build_b(k):
    """B 流木の巻き殻: 流木を巻いたような巻貝の形。木目の帯が渦になり、口は前を向く。"""
    F = []
    eyes = crab(F)
    R = [ring(2.2, 2.6, -2.6, 2.8, zc=1.6, n=8), ring(4.2, 3.4, -3.2, 3.4, zc=1.8, n=8), ring(6.6, 2.8, -2.4, 3.2, zc=2.6, n=8),
         ring(8.4, 1.8, -1.4, 2.2, zc=3.4, n=8), ring(9.8, 0.6, -0.4, 0.8, zc=4.0, n=8)]
    F += part("whorl", loft("shell", "wood", R, caps=("dark", "wood")))
    for i, (y, r_) in enumerate(((4.2, 3.5), (6.6, 2.9), (8.4, 1.9))):
        a = [V(math.sin(math.radians(t)) * r_, y + 0.4 * math.sin(math.radians(t * 2)), 1.8 + 0.8 * i + math.cos(math.radians(t)) * r_ * 0.98) for t in (60, 120, 180, 240, 300)]
        F += part(f"band{i}", tube("shell", "knot", a, ra=0.3, sides=3, hint=(0, 1, 0)))
    F += part("barn", lumps("shell", "barn", [((2.4, 5.6, 3.8), 0.55), ((-1.8, 7.4, 3.4), 0.45)]))
    return F, eyes


def build_c(k):
    """C 浮き玉の殻: 流木を組んだ枠に古い硝子の浮き玉を抱え、珊瑚の芽が生える。光ると硝子が青緑に透ける。"""
    F = []
    eyes = crab(F)
    F += part("cup", tube("shell", "wood", [(0, 3.4, -0.2), (0, 3.8, 1.8), (0, 3.6, 4.4)], ra=[2.6, 2.8, 2.2], rb=[2.0, 2.2, 1.8], sides=6, hint=(0, 1, 0), caps=("dark", "wood")))
    F += part("float", ellipsoid("shell", "glass", (0, 6.6, 2.0), (2.7, 2.5, 2.7), segs=8, rings=3))
    for s in (-1, 1):
        F += part(f"strap{s}", tube("shell", "wood", [(s * 2.6, 4.6, 0.4), (s * 1.2, 8.6, 1.4), (-s * 0.8, 9.0, 3.0)], ra=0.35, sides=3, hint=(0, 1, 0)))
    F += part("sprout", stag("shell", "claw", (1.2, 8.6, 3.2), (0.3, 1, 0.2), 2.6, 0.45, [(0.5, (0.8, 0.2, 0), 1.2, 0.25)], sides=3, rings=1))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


HIDE = {"head": (0, 0, 0), "claw_l": (-20, 0, 0), "claw_r": (-20, 0, 0), "leg_l1": (0, 0, -40), "leg_l2": (0, 0, -40),
        "leg_r1": (0, 0, 40), "leg_r2": (0, 0, 40)}
POSES = {
    "idle": {},
    "hide": HIDE,
    "spit": {"body": (16, 0, 0), "head": (20, 0, 0), "claw_l": (40, 0, 0), "claw_r": (40, 0, 0)},
}

SPEC = dict(
    id="drift_hermit", short="宿借り", kind="地上の小物 3案 (P12・敵意なし)",
    title="宿借り (drift_hermit) — 流木の殻の宿借り・低ポリ + ドット絵 3 案",
    sub=["二章の浜の小物。敵意なし。人を見ると殻に潜る。殻を叩くと拾い物を吐く (小真珠・貝の欠け。一匹に一度)。3〜5 匹で浜を行列で歩く。HP 14。",
         "全長 約 0.7 ブロック。潜るのは頭・鋏・脚を殻の中へ (アニメでは position で殻の中へ引く)。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 流木の洞", "B 流木の巻き殻", "C 浮き玉の殻"],
    rows=[("前 3/4", "idle", 212, 16, 300, (0, 4, 0), 17.0),
          ("横", "idle", 270, 6, 300, (0, 4, 0), 17.0),
          ("後ろ 3/4 (殻)", "idle", 32, 18, 300, (0, 4, 0), 17.0),
          ("吐く (体を起こして鋏を上げる)", "spit", 220, 10, 300, (0, 5, 0), 17.0)],
    options={
        "a": dict(name="A 流木の洞", desc="中の空いた流木の切れ株を横に背負う。節と割れ、上にフジツボと小枝、白く乾いた塩の跡。浜の流木そのものなので、潜ると流木にしか見えない (隠れる読みが一番強い)。"),
        "b": dict(name="B 流木の巻き殻", desc="流木を巻いたような巻貝の形。木目の暗い帯が渦を描く。宿借りらしさが一番分かりやすく、行列の輪郭が一つずつ立つ。"),
        "c": dict(name="C 浮き玉の殻", desc="流木を組んだ枠に、古い青緑の硝子の浮き玉を抱える。上に珊瑚の芽。潮の民の落とし物を背負う、という話が付く。硝子が光を拾うので行列が遠くから見える。")},
    extra="二章の言葉 (流木・珊瑚・青緑・白い塩)。骨 10 本。左の鋏が大きい。体は珊瑚色、目の柄の先は黒い玉。潜ると殻だけになり、殻を叩くと体を起こして吐く。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
