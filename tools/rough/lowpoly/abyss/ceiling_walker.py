"""逆さ歩き (ceiling_walker) 低ポリ ラフ 3 案。深淵だけの敵 (どの層にも出る)。天井を逆さに歩く人型。見ている間は動かず、目を離すと落ちてくる。
モデルは天井に立つ向き (足が上、頭が下) で作る。絵は天井の下から見上げた形 (頭が下に垂れる) で出す。
A 逆さの人 (人の形そのまま、石板の青灰。手は下に垂れる) / B 天を掴む手 (足は短く、長い手が天井を掴む) / C 垂れた衣 (衣の裾が頭まで覆い、頭だけが衣の下から出る)
骨: root > body > head / arm_l / arm_r ; root > leg_l / leg_r。足の先は天井 (y = 28) に付く。三角形は 400〜600 が目安。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

CEIL = 28.0
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 16.0, 0)), "head": ("body", (0, 5.5, 0)),
         "arm_l": ("body", (3.4, 21.0, 0)), "arm_r": ("body", (-3.4, 21.0, 0)),
         "leg_l": ("root", (1.6, 18.0, 0)), "leg_r": ("root", (-1.6, 18.0, 0))}
PAL = {"mats": {
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.14),
    "slate_b": mat(SLATE, 0.42, crack=0.0, noise=0.1),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.1),
    "teal": mat(TEAL, 0.6, crack=0.0, noise=0.1),
    "bone": mat(BONEW, 0.55, crack=0.0, noise=0.12),
}, "glow_mats": []}


def legs_up(F, m, foot_out=1.2):
    """脚は天井へ伸びる (足先が y = CEIL に付く)。"""
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(b, tube(b, m, [(s * 1.6, 18.0, 0), (s * 2.0, 23.0, 0.4), (s * 2.2, CEIL - 0.8, 0.2 * foot_out)],
                          ra=[1.2, 0.95, 0.8], sides=9, caps=(True, True)))


def build_a(k):
    """A 逆さの人: 人の形そのまま。頭が下、腕は下へ垂れる。顔はなく、頭は影のまま。"""
    F = []
    R = [ring(18.0, 2.4, -1.4, 1.4, n=16), ring(12.0, 3.0, -1.6, 1.6, n=16), ring(6.5, 2.2, -1.2, 1.2, n=16)]
    F += part("body", loft("body", "slate", R, caps=("slate_b", "slate")))
    F += part("head", ellipsoid("head", "void", (0, 4.4, 0), (1.9, 2.3, 1.9), segs=10, rings=4))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(b, tube(b, "slate", [(s * 2.4, 21.0, 0), (s * 4.6, 15.0, -0.6), (s * 4.2, 9.0, -1.0)],
                          ra=[1.45, 1.15, 0.8], sides=9, caps=(True, True)))
    legs_up(F, "slate_b")
    return F, [eye(-0.7, 3.9, -2.0, 0.2), eye(0.7, 3.9, -2.0, 0.2)]


def build_b(k):
    """B 天を掴む手: 足は短く、長い手が天井へ伸びて掴む。掴んだ指先が天井の石を割る形。"""
    F = []
    R = [ring(19.0, 2.6, -1.4, 1.4, n=16), ring(13.0, 3.0, -1.6, 1.6, n=16), ring(8.0, 2.4, -1.2, 1.2, n=16)]
    F += part("body", loft("body", "slate_b", R, caps=("void", "slate_b")))
    F += part("head", ellipsoid("head", "slate", (0, 6.0, 0), (1.8, 2.2, 1.8), segs=10, rings=4))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(b, tube(b, "slate", [(s * 2.4, 21.0, 0), (s * 6.4, 25.0, -0.6), (s * 7.6, CEIL - 0.8, -1.4)],
                          ra=[1.3, 0.95, 0.65], sides=9, caps=(True, True)))
        for j, dz in enumerate((-0.7, 0.0, 0.7)):
            F += part(b, cone(b, "bone", (s * 7.6, CEIL - 1.0, -1.4 + dz), (s * 0.2, 1.0, dz * 0.3), 1.3, 0.22, sides=3, rings=1))
    legs_up(F, "slate_b", foot_out=0.6)
    return F, [eye(-0.7, 5.0, -2.0, 0.2), eye(0.7, 5.0, -2.0, 0.2)]


def build_c(k):
    """C 垂れた衣: 衣が天井から垂れて体を覆う。頭だけが衣の裾から出て、髪のように垂れた布がその前を隠す。"""
    F = []
    R = [ring(CEIL - 1.0, 2.8, -1.8, 1.8, n=16), ring(22.0, 3.6, -2.2, 2.2, n=16), ring(14.0, 4.4, -2.6, 2.6, n=16),
         ring(6.0, 5.0, -2.8, 2.8, n=16), ring(0.6, 5.2, -3.0, 3.0, n=16)]
    F += part("robe", loft("body", "void", R, caps=("void", "void")))
    F += part("hem_fold", tube("body", "slate", [(-4.4, 2.5, -2.2), (-3.6, 6.0, -3.2), (-4.2, 12.0, -2.4)], ra=[0.8, 0.8, 0.7], sides=6, caps=(True, True)))
    F += part("head", ellipsoid("head", "slate", (0, 0.2, -1.2), (1.8, 2.0, 1.8), segs=10, rings=4))
    F += part("chin", ellipsoid("head", "slate_b", (0, -1.2, -1.6), (1.1, 0.8, 0.8), segs=6, rings=2))
    F += part("arm_l", tube("arm_l", "void", [(3.4, 21.0, 0), (4.6, 16.0, -0.4), (4.2, 11.0, -0.6)], ra=[1.45, 1.15, 0.9], sides=9, caps=(True, True)))
    F += part("arm_r", tube("arm_r", "void", [(-3.4, 21.0, 0), (-4.6, 16.0, -0.4), (-4.2, 11.0, -0.6)], ra=[1.45, 1.15, 0.9], sides=9, caps=(True, True)))
    return F, [eye(-0.7, 0.4, -2.9, 0.2), eye(0.7, 0.4, -2.9, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "look": {"head": (0, 0, 0), "body": (0, 0, 8)}}

SPEC = dict(
    id="ceiling_walker", short="逆さ歩き", kind="深淵だけの敵 3案 (天井)",
    title="逆さ歩き (ceiling_walker) — 天井を逆さに歩く人型。目を離すと落ちる・低ポリ 3 案",
    sub=["深淵だけ (どの層にも出る)。天井に立つ向きで作り、絵は天井の下から見上げた形 (頭が下)。",
         "左の目盛りは人 (1.8 ブロック) を床に置いた時の大きさ。目を離すと落ちる (動きの話で、形は作らない)。"],
    labels=["A 逆さの人", "B 天を掴む手", "C 垂れた衣"],
    poses=POSES,
    options={
        "a": dict(name="A 逆さの人", desc="人の形そのまま、天井に立つ向き。石板の青灰で、頭は影の中。腕は下へ垂れ、見上げた時の頭上の人影になる。一番素直に「逆さ」が伝わる。"),
        "b": dict(name="B 天を掴む手", desc="足は短く、長い腕が天井を掴む。指先が天井の石を割る形で、目を離すと腕が縮んで落ちる予告に見える。手の骨の白で、見上げた時に指が先に目に入る。"),
        "c": dict(name="C 垂れた衣", desc="衣が天井から垂れて体を覆い、頭だけが裾の下から出る。衣の裾が床の近くまで届き、見上げると布の影しか見えない。近づくと頭が覗く。"),
    },
    extra="深淵だけの敵 (天井で動く)。骨 7 本 (root > body > head / arm_l / arm_r、root > leg_l / leg_r)。足は天井 (y = 28) に付く。",
    budget="人型 400〜600",
    pw=380, atlas_h=170,
)
ROWS = [("下から見上げる (頭が下)", "idle", 212, -18, 260, (0, 14, 0),6.0, None),
        ("横 (天井の高さ)", "idle", 270, 6, 260, (0, 14, 0),6.0, None),
        ("斜め下から", "idle", 200, -50, 260, (0, 14, 0),6.0, None),
        ("見ている間 (少しだけ向く)", "look", 232, -18, 260, (0, 14, 0),6.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
