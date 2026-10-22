"""灯喰い (lamp_eater) 低ポリ ラフ 3 案。深淵だけの敵 (どの層にも出る)。灯りへ寄って、灯りを消す。光の術の灯りも食う。
A 黒い口の球 (虚の黒の球に、前面だけ歯の輪。口の奥に青緑がかすかに灯る) / B 灯の皮 (虚の黒の膜が二枚の翼のように開く。中央の穴が口) / C 長い首の鳥 (細く長い首、先の嘴が開く。嘴の奥に青緑)
骨: root > body > jaw (A は body のみ、B は body > wing_l / wing_r、C は body > neck > head > jaw)。
人の 1 倍から 1.3 倍。三角形は 200〜400 が目安 (単体の敵)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8.0, 0)), "jaw": ("body", (0, 8.0, -3.0)),
         "wing_l": ("body", (-1.0, 8.0, 0)), "wing_r": ("body", (1.0, 8.0, 0)),
         "neck": ("body", (0, 6.0, 0)), "head": ("neck", (0, 16.0, -1.0))}
PAL = {"mats": {
    "void": mat(VOID, 0.5, crack=0.0, noise=0.12),
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.12),
    "bone": mat(BONEW, 0.6, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.75, crack=0.0, noise=0.06, min=2),
}, "glow_mats": ["teal"]}


def build_a(k):
    """A 黒い口の球: 虚の黒の球。前面の口の縁に歯の輪 (石板の骨色の棘)、奥に青緑。"""
    F = []
    F += part("body", ellipsoid("body", "void", (0, 8.0, 0), (4.4, 4.4, 4.4), segs=14, rings=6))
    F += part("mouth", ellipsoid("body", "teal", (0, 8.0, -4.0), (2.2, 2.2, 0.6), segs=8, rings=2))
    for i in range(8):
        a = 2 * 3.14159265 * i / 8
        x, y = 3.2 * math.cos(a), 8.0 + 3.2 * math.sin(a)
        F += part(f"tooth{i}", cone("body", "bone", (x * 0.9, 8.0 + (y - 8.0) * 0.9, -2.0), (x * 0.3, (y - 8.0) * 0.3, -1.0), 2.0, 0.55, sides=8, rings=2))
    return F, []


def build_b(k):
    """B 灯の皮: 虚の黒の厚い膜が二枚の翼のように開く。翼の中に石板の肋が三本ずつ走り、中央の穴が口。"""
    F = []
    F += part("body", ellipsoid("body", "void", (0, 8.0, 0), (2.2, 2.6, 2.2), segs=10, rings=4))
    F += part("mouth", ellipsoid("body", "teal", (0, 8.0, -1.9), (1.1, 1.3, 0.5), segs=8, rings=3))
    for s_, b in ((-1, "wing_l"), (1, "wing_r")):
        F += part(b, ellipsoid(b, "void", (s_ * 5.0, 9.0, 0.2), (3.4, 4.2, 1.5), segs=10, rings=4))
        for r_i, (dx_, dy_) in enumerate(((1.2, 1.6), (2.4, 0.2), (3.2, -1.6))):
            x0, y0 = s_ * 1.6, 9.0
            F += part(b, tube(b, "slate", [(x0, y0, -0.6), (s_ * (5.0 + dx_), y0 + dy_, -1.0), (s_ * (5.0 + dx_ + 0.6), y0 + dy_ - 0.3, -0.9)],
                              ra=[0.42, 0.34, 0.2], sides=6, caps=(True, True)))
    return F, []


def build_c(k):
    """C 長い首の鳥: 細く長い首が曲がって上がり、先の頭に長い嘴。嘴が開くと奥に青緑。"""
    F = []
    F += part("body", ellipsoid("body", "void", (0, 6.0, 0), (3.0, 2.4, 2.8), segs=10, rings=4))
    F += part("neck", tube("neck", "slate", [(0, 6.0, 0), (0, 10.0, 0.6), (0.4, 14.0, -0.4), (0.0, 17.0, -1.2)],
                           ra=[1.9, 1.4, 1.2, 1.1], sides=8, caps=(True, True)))
    F += part("head", ellipsoid("head", "void", (0, 17.6, -1.0), (1.6, 1.6, 1.6), segs=7, rings=3))
    F += part("beak_up", cone("head", "bone", (0, 18.0, -2.4), (0, 0.2, -1.0), 3.6, 0.7, sides=4, rings=3))
    F += part("beak_lo", cone("head", "bone", (0, 16.0, -2.4), (0, -0.1, -1.0), 3.0, 0.45, sides=4, rings=3))
    F += part("throat", ellipsoid("head", "teal", (0, 16.8, -2.2), (0.8, 0.5, 0.4), segs=6, rings=2))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "eat": {"jaw": (-25, 0, 0), "head": (-10, 0, 0), "neck": (0, 0, 0)}}

SPEC = dict(
    id="lamp_eater", short="灯喰い", kind="深淵だけの敵 3案 (灯りを食う)",
    title="灯喰い (lamp_eater) — 灯りへ寄って消す。深淵だけの敵・低ポリ 3 案",
    sub=["深淵だけ (どの層にも出る、巨神の名残ではない)。灯りへ寄って消す。光の術の灯りも食う。",
         "左の目盛りは人 (1.8 ブロック)。口の奥の青緑は食った灯りの残り。"],
    labels=["A 黒い口の球", "B 灯の皮", "C 長い首の鳥"],
    poses=POSES,
    options={
        "a": dict(name="A 黒い口の球", desc="虚の黒の球に、前面の口の縁の歯の輪。奥に青緑がかすかに灯る。転がってきて、灯りの前で口が開く。一番読みやすい「食う」形。"),
        "b": dict(name="B 灯の皮", desc="虚の黒の薄い膜が二枚の翼のように開く。中央の穴が口で、灯りに近づくと翼が閉じて灯りを包む。翼の縁の石板の色で、暗い中でも輪郭が出る。"),
        "c": dict(name="C 長い首の鳥", desc="細く長い首が曲がって上がり、先の嘴が開いて灯りを食う。背の高さで遠くからも見つかる。嘴の奥の青緑が、灯りの消えた後の残り。"),
    },
    extra="深淵だけの敵。骨 7 本 (root > body > jaw / wing_l / wing_r / neck > head)。鍵は食う口と奥の青緑。",
    budget="単体の敵 200〜400",
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 9, 0), 9.5, None),
        ("横", "idle", 270, 6, 260, (0, 9, 0), 9.5, None),
        ("上から", "idle", 200, 60, 260, (0, 9, 0), 9.5, None),
        ("灯りを食う (口が開く)", "eat", 212, 18, 260, (0, 9, 0), 9.5, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=96)
