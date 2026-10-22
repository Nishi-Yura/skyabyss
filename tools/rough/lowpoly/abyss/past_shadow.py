"""前の私の影 (past_shadow) 低ポリ ラフ 3 案。深み (クリア後) の人型。前のループで留まった自分の残り。
顔は作らない (プレイヤーの肌・顔と似せない)。影の形・残像・濡れた黒で、「似ているのに違う」を出す。
A 切り抜き (薄い板を切った人影、石板の青灰。横から見ると消えそうに薄い) / B 残像 (同じ人影が三つ、少しずつずれて並ぶ。前のめりの一つが今の位置) / C 濡れた影 (虚の黒の人影、腕が長く、顎から細い糸が垂れる)
骨: root > body > head / arm_l / arm_r ; root > leg_l / leg_r。人の高さ約 28 単位。三角形は 400〜600 が目安。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 13.0, 0)), "head": ("body", (0, 23.0, 0)),
         "arm_l": ("body", (3.2, 21.0, 0)), "arm_r": ("body", (-3.2, 21.0, 0)),
         "leg_l": ("root", (1.6, 12.5, 0)), "leg_r": ("root", (-1.6, 12.5, 0))}
PAL = {"mats": {
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.12),
    "slate_b": mat(SLATE, 0.44, crack=0.0, noise=0.1),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.6, crack=0.0, noise=0.1),
}, "glow_mats": []}


def figure(F, m, dx=0.0, th=1.0, tag="", arm=1.0, leg=1.0, head_r=1.9, drip=False):
    """人影 1 体。dx で横へずらす (残像用)。th は厚み、arm・leg は長さ。"""
    R = [ring(12.5, 2.2 * th, -1.2 * th, 1.2 * th, n=16, cx=dx),
         ring(17.5, 2.8 * th, -1.3 * th, 1.3 * th, n=16, cx=dx),
         ring(22.0, 2.3 * th, -1.0 * th, 1.0 * th, n=16, cx=dx)]
    F += part(tag + "body", loft("body", m, R, caps=(m, m)))
    F += part(tag + "head", ellipsoid("head", m, (dx, 25.2, 0), (head_r * 0.8 * th, head_r, head_r * 0.7 * th), segs=10, rings=4))
    F += part(tag + "neck", tube("head", m, [(dx, 21.6, 0), (dx, 23.8, 0)], ra=[0.9 * th, 0.8 * th], sides=6, caps=(True, True)))
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(tag + b, tube(b, m, [(dx + s * 2.0 * th, 21.0, 0), (dx + s * 4.6 * arm, 16.0, -0.6), (dx + s * 5.4 * arm, 11.5, -0.6)],
                                ra=[1.25 * th, 0.95 * th, 0.7 * th], sides=9, caps=(True, True)))
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(tag + b, tube(b, m, [(dx + s * 1.6, 12.5, 0), (dx + s * 1.8, 6.0 * leg, 0), (dx + s * 1.8, 0.6, -0.6)],
                                ra=[1.6 * th, 1.25 * th, 1.05 * th], sides=9, caps=(True, True)))
    return F


def build_a(k):
    """A 切り抜き: 薄い板の人影。石板の青灰で、横から見ると消えそうに薄い。目も口もない。"""
    F = []
    figure(F, "slate", th=0.75)
    return F, []


def build_b(k):
    """B 残像: 同じ人影が三つ、横へずれて並ぶ。今の位置は真ん中で一番濃く、左右の二つは薄い石板 (灰青) の色。"""
    F = []
    figure(F, "slate_b", dx=-7.2, th=0.7, tag="ghost_l_")
    figure(F, "slate", dx=0.0, th=1.0, tag="now_")
    figure(F, "slate_b", dx=7.2, th=0.7, tag="ghost_r_")
    return F, []


def build_c(k):
    """C 濡れた影: 虚の黒の人影。腕が長く、頭が前へ垂れ、顎と肘から細い糸が床へ垂れる。"""
    F = []
    figure(F, "void", th=1.0, arm=1.35, leg=1.0, head_r=1.7)
    for s in (1, -1):
        F += part(f"drip_e{s}", cone("arm_l" if s == 1 else "arm_r", "teal", (s * 6.0, 10.8, -0.6), (0, -1, 0), 4.4, 0.22, sides=3, rings=2))
    F += part("drip_c", cone("head", "teal", (0, 21.0, -1.6), (0, -1, -0.2), 6.0, 0.22, sides=3, rings=2))
    return F, [eye(-0.6, 25.4, -2.2, 0.18), eye(0.6, 25.4, -2.2, 0.18)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "lean": {"body": (-10, 0, 0), "head": (14, 0, 0)}}

SPEC = dict(
    id="past_shadow", short="前の私の影", kind="深み専用の敵 3案 (人型・影)",
    title="前の私の影 (past_shadow) — 前のループの自分の影。顔も肌もない人型・低ポリ 3 案",
    sub=["深み (クリア後) の人型。前のループで留まった自分の残り。顔と肌は作らない (プレイヤーに似せない)。",
         "左の目盛りは人 (1.8 ブロック)。石板の青灰と虚の黒だけで描く。"],
    labels=["A 切り抜き", "B 残像", "C 濡れた影"],
    poses=POSES,
    options={
        "a": dict(name="A 切り抜き", desc="薄い板を切った人影。石板の青灰一色で、横から見ると消えそうに薄い。顔も手の指もなく、人の形だけが残る。深みの中で一番「ただの影」に近い。"),
        "b": dict(name="B 残像", desc="同じ人影が三つ、横へずれて並ぶ。今の位置が真ん中で濃く、左右の二つは灰青の薄い残像。動くと前の動きが少し遅れて見え、前の私が後を追う感じが出る。"),
        "c": dict(name="C 濡れた影", desc="虚の黒の人影。腕が長く、頭が前へ垂れ、肘と顎から細い糸が床へ落ちる (スカルクの青緑の糸)。近づくと糸が伸びて、こちらへ手を伸ばすように見える。"),
    },
    extra="深み専用の人型。骨 7 本 (root > body > head / arm_l / arm_r、root > leg_l / leg_r)。顔も肌もなく、石板と虚の黒のみ。",
    budget="人型 400〜600",
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 13, 0),6.0, None),
        ("横", "idle", 270, 6, 260, (0, 13, 0),6.0, None),
        ("後ろ 3/4", "idle", 32, 16, 260, (0, 13, 0),6.0, None),
        ("前のめり (追う)", "lean", 212, 18, 260, (0, 13, 0),6.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
