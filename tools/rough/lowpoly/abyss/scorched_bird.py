"""焦げた鳥の影 (scorched_bird) 低ポリ ラフ 3 案。雷の層の空の敵。天井の闇から降りて、上から掴みに来る。
A 広げた翼 (焦げた大きな翼、骨の爪) / B 折れた翼 (翼が半ばで折れ、羽が煤の破片になって垂れる) / C 長い尾の鳥 (細い尾が長く、尾の先で打つ)
骨: root > body > head / wing_l / wing_r / tail。翼は付け根を胴に沈め、翼の骨の指は翼の膜の中。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 4.0, 0)), "head": ("body", (0, 4.6, -3.2)),
         "wing_l": ("body", (-1.2, 4.6, -0.4)), "wing_r": ("body", (1.2, 4.6, -0.4)), "tail": ("body", (0, 3.6, 3.0))}
PAL = make_pal({
    "tuff": (TUFF, 0.6, dict(crack=0.06, noise=0.12)),
    "soot": (ASHY, 0.55, dict(crack=0.1, noise=0.2)),
    "oxcu": (OXCU, 0.55, dict(crack=0.0, noise=0.08)),
    "light": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
}, glow=["light"])


def build_a(k):
    """A 広げた翼: 焦げた大きな翼。翼の骨の指が膜を張り、先に爪。胴は薄く、影の形が一番大きい。"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 4.0, 0), (1.6, 1.6, 3.2), segs=6, rings=2))
    F += part("head", ellipsoid("head", "soot", (0, 4.6, -3.0), (1.1, 1.0, 1.1), segs=5, rings=1))
    F += part("beak", spike("head", "tuff", (0, 4.4, -4.0), (0, -0.2, -1), 1.6, 0.4, sides=3, rings=1))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"wing_{nm}", ellipsoid(f"wing_{nm}", "soot", (s * 4.4, 5.0, -0.4), (4.4, 0.25, 2.6), segs=6, rings=2))
        F += part(f"claw_{nm}", spike(f"wing_{nm}", "tuff", (s * 7.0, 4.8, -1.0), (s * 0.2, -0.6, 0.0), 1.2, 0.2, sides=3, rings=1))
    F += part("tail", spike("tail", "soot", (0, 3.6, 3.0), (0, -0.1, 1), 2.4, 0.9, sides=4, rings=2))
    return F, [eye_at(-0.5, 4.8, -3.7, 0.25), eye_at(0.5, 4.8, -3.7, 0.25)]


def build_b(k):
    """B 折れた翼: 翼が半ばで折れて垂れる。羽は煤の破片が一枚ずつ離れて、根元の骨だけが付く。"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 4.0, 0), (1.6, 1.6, 3.2), segs=6, rings=2))
    F += part("head", ellipsoid("head", "soot", (0, 4.6, -3.0), (1.1, 1.0, 1.1), segs=5, rings=1))
    F += part("beak", spike("head", "tuff", (0, 4.4, -4.0), (0, -0.2, -1), 1.6, 0.4, sides=3, rings=1))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"wing_{nm}", ellipsoid(f"wing_{nm}", "soot", (s * 2.6, 4.6, -0.4), (2.4, 0.25, 2.2), segs=6, rings=2))
        F += part(f"broke_{nm}", tube(f"wing_{nm}", "tuff", [(s * 2.6, 4.2, -0.6), (s * 3.4, 1.4, -0.6), (s * 3.8, -0.6, -0.4)], ra=[0.4, 0.3, 0.12], sides=4, hint=(0, 0, 1)))
        for i in range(3):
            F += part(f"flake_{nm}{i}", ellipsoid(f"wing_{nm}", "oxcu", (s * (3.0 + i * 0.4), 3.0 - i * 1.2, -0.6), (0.5, 0.2, 0.7), segs=4, rings=1))
    F += part("tail", spike("tail", "soot", (0, 3.6, 3.0), (0, -0.2, 1), 1.8, 0.8, sides=4, rings=2))
    return F, [eye_at(-0.5, 4.8, -3.7, 0.25), eye_at(0.5, 4.8, -3.7, 0.25)]


def build_c(k):
    """C 長い尾の鳥: 翼は小さく、細い尾が長く伸びて先が白い雷の針。尾を振って上から打つ。"""
    F = []
    F += part("body", ellipsoid("body", "soot", (0, 4.0, 0), (1.5, 1.5, 2.8), segs=6, rings=2))
    F += part("head", ellipsoid("head", "soot", (0, 4.6, -2.8), (1.0, 0.9, 1.0), segs=5, rings=1))
    F += part("beak", spike("head", "tuff", (0, 4.4, -3.7), (0, -0.2, -1), 1.2, 0.3, sides=3, rings=1))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"wing_{nm}", ellipsoid(f"wing_{nm}", "soot", (s * 2.6, 4.8, -0.2), (2.6, 0.2, 1.6), segs=5, rings=1))
    F += part("tail", tube("tail", "soot", [(0, 0, 0), (0, -0.4, 2.6), (0, -0.8, 5.4), (0, -0.6, 7.2)], ra=[0.6, 0.35, 0.2, 0.08], sides=4, hint=(1, 0, 0)))
    F += part("tail_tip", ellipsoid("tail", "light", (0, -0.6, 7.4), (0.4, 0.4, 0.4), segs=4, rings=1))
    return F, [eye_at(-0.45, 4.8, -3.4, 0.25), eye_at(0.45, 4.8, -3.4, 0.25)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "dive": {"body": (40, 0, 0), "head": (30, 0, 0), "wing_l": (0, 0, 35), "wing_r": (0, 0, -35), "tail": (-30, 0, 0)},
}

SPEC = dict(
    id="scorched_bird", short="焦げた鳥の影", kind="雷の層の敵 3案 (A3)",
    title="焦げた鳥の影 (scorched_bird) — 天井の闇から降りる空の敵・低ポリ + ドット絵 3 案",
    sub=["雷の層の空の敵。天井の闇から影だけで降りてくる (上を見ないと気づかない)。急降下して掴み、そのまま上へ戻る。",
         "左の赤の目盛りは人 (1.8 ブロック)。影は灯りでは消えず、光の術の輪郭で見える。"],
    labels=["A 広げた翼", "B 折れた翼", "C 長い尾の鳥"],
    rows=[("前 3/4 (翼を広げる)", "idle", 212, 12, 300, (0, 5, 0), 14.0),
          ("横", "idle", 270, 4, 300, (0, 5, 0), 14.0),
          ("上から (影の形)", "idle", 200, 60, 300, (0, 5, 0), 14.0),
          ("急降下", "dive", 230, 10, 300, (0, 5, 0), 14.0),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 5, 0), 14.0)],
    options={
        "a": dict(name="A 広げた翼", desc="焦げた大きな翼を広げ、骨の爪が先に出る。上から見た影が一番大きく、天井の闇に紛れて見つけにくい。"),
        "b": dict(name="B 折れた翼", desc="翼が半ばで折れ、煤の羽根が破片になって離れる。落ちてきた影に見え、動きの乱れで隙が分かる。"),
        "c": dict(name="C 長い尾の鳥", desc="翼は小さく、細い尾が長く伸びて先に白い雷の玉。尾で上から打つ形で、攻撃の距離が一番読める。")},
    extra="雷の言葉 (凝灰岩・煤・酸化した銅・白い雷の光)。骨 6 本 (root > body > head / wing_l wing_r / tail)。翼の膜は翼の骨の子。",
    budget="空の中型 300〜500", atlas_h=150, pw=420,
    later="歪み = 翼の膜に穴が増え、尾の先の光が二つになる。崩れ = 翼が取れて胴だけが落ち、落ちた所で煤の跡が残る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=128)
