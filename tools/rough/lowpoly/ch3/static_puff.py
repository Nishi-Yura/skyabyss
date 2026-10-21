"""帯電毛玉 (static_puff) 低ポリ ラフ 3 案。連鎖の予習。HP 10、放電 4。
宙に浮く綿毛の塊。帯電 (4 秒) の予告は「毛が逆立つ」(翅ではない)。人が近くで固まっていると、放電が隣の人へ飛ぶ。
A 綿毛の玉 (たんぽぽの綿毛のような放射の毛、先に青白い火花) / B 静電の羊毛 (もこもこの毛の房の塊、銅の種と黒い目) / C 雷雲の子 (平たい雲の塊、下に垂れる静電の糸、中の核が透ける)
骨: root > body > tuft_n / tuft_s / tuft_e / tuft_w / tuft_t (毛の房 5 組)。逆立つ = 房が外へ開く (tuft_* の回転) + 体が膨らむ。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck3 import *  # noqa

C0 = (0, 8.0, 0)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", C0),
         "tuft_n": ("body", (0, 8.0, -2.0)), "tuft_s": ("body", (0, 8.0, 2.0)), "tuft_e": ("body", (2.0, 8.0, 0)),
         "tuft_w": ("body", (-2.0, 8.0, 0)), "tuft_t": ("body", (0, 10.0, 0))}
PAL = {"mats": {
    "down": mat(DOWN, 0.7, crack=0.0, noise=0.18, facevar=0.08),
    "downd": mat(DOWN, 0.52, crack=0.0, noise=0.2, facevar=0.08),
    "wool": mat(DOWN, 0.64, crack=0.0, noise=0.24, dots=0.1),
    "cloud": mat(ROCK, 0.66, crack=0.0, noise=0.2, facevar=0.06),
    "cloudl": mat(DOWN, 0.6, crack=0.0, noise=0.16),
    "seed": mat(COPPER, 0.62, crack=0.0, noise=0.1),
    "spark": mat(BOLT, 0.8, crack=0.0, min=1),
    "core": mat(BOLT, 0.72, crack=0.0, min=1),
    "dark": mat(DEEP, 0.5, crack=0.0, min=0),
}, "skull_bone": "none", "face_mat": "none", "glow_mats": ["spark", "core"]}

# 房の向き (骨, 向きの基準)
TUFTS = {"tuft_n": (0, 0, -1), "tuft_s": (0, 0, 1), "tuft_e": (1, 0, 0), "tuft_w": (-1, 0, 0), "tuft_t": (0, 1, 0)}


def tuft_of(d):
    """向き d に一番近い房の骨"""
    d = nrm(np.asarray(d, float))
    if d[1] > 0.62:
        return "tuft_t"
    best, bv = "tuft_n", -9
    for b, v in TUFTS.items():
        if b == "tuft_t":
            continue
        x = float(np.dot(d, np.asarray(v, float)))
        if x > bv:
            best, bv = b, x
    return best


def fib(n, seed=0, lo=-0.75):
    """球の上にほぼ均等な n 点の向き (y が lo より上)"""
    out = []
    g = math.pi * (3 - math.sqrt(5))
    for i in range(n):
        y = 1 - (i + 0.5) / n * (1 - lo)
        r = math.sqrt(max(0, 1 - y * y))
        a = g * i + seed
        out.append(np.array([math.cos(a) * r, y, math.sin(a) * r]))
    return out


def build_a(k):
    """A 綿毛の玉: 白い綿毛の芯から、細い毛が放射に伸びる。毛の先に青白い火花。逆立つと毛がまっすぐ開く。"""
    F = []
    F += part("core", ellipsoid("body", "downd", C0, (3.4, 3.2, 3.4), segs=8, rings=3))
    for i, d in enumerate(fib(22, 0.3, -0.85)):
        b = tuft_of(d)
        base = np.asarray(C0) + d * 2.9
        ln = 3.8 + (i % 3) * 0.6
        F += part(f"hair{i}", spike(b, "down", base, d, ln, 0.55, sides=3, rings=1))
        if i % 3 == 0:
            F += part(f"spk{i}", ellipsoid(b, "spark", base + d * (ln + 0.1), (0.32, 0.32, 0.32), segs=4, rings=1))
    # 下の種 (浮く体の重り。銅色の小さな種)
    F += part("seed", ellipsoid("body", "seed", (0, 4.6, 0), (0.9, 1.0, 0.9), segs=5, rings=1))
    return F, []


def build_b(k):
    """B 静電の羊毛: もこもこの毛の房を寄せた塊。中に銅の種、前に黒い目が二つ。逆立つと房がふくらんで火花が散る。"""
    F = []
    F += part("core", ellipsoid("body", "wool", C0, (4.0, 3.6, 4.0), segs=8, rings=3))
    for i, d in enumerate(fib(12, 1.1, -0.6)):
        b = tuft_of(d)
        c = np.asarray(C0) + d * 3.6
        r = 1.6 + (i % 2) * 0.3
        F += part(f"lump{i}", ellipsoid(b, "wool" if i % 3 else "down", c, (r, r * 0.9, r), segs=6, rings=2))
        if i % 4 == 1:
            F += part(f"spk{i}", spike(b, "spark", c + d * (r * 0.6), d, 1.4, 0.3, sides=3, rings=1))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("body", "dark", (s * 1.3, 8.6, -3.85), (0.55, 0.65, 0.35), segs=5, rings=1))
    F += part("seed", ellipsoid("body", "seed", (0, 4.6, 0), (1.0, 0.8, 1.0), segs=5, rings=1))
    return F, []


def build_c(k):
    """C 雷雲の子: 平たい灰青の雲の塊。上はもこもこ、底は平ら。底から静電の糸が垂れ、中の核が隙間から光る。"""
    F = []
    F += part("core", ellipsoid("body", "core", (0, 7.6, 0), (2.4, 1.8, 2.4), segs=6, rings=2))
    puffs = [((0, 9.2, 0), (3.6, 2.6, 3.4), "tuft_t"), ((-3.0, 8.0, 0.4), (2.4, 2.0, 2.4), "tuft_w"), ((3.0, 8.2, -0.2), (2.6, 2.1, 2.4), "tuft_e"),
             ((0.4, 8.0, -2.8), (2.4, 1.9, 2.0), "tuft_n"), ((-0.4, 8.0, 2.8), (2.4, 1.9, 2.0), "tuft_s")]
    for i, (c, r, b) in enumerate(puffs):
        F += part(f"puff{i}", ellipsoid(b, "cloud" if i == 0 else "cloudl", c, r, segs=7, rings=2))
    # 底の平らな板 (雲の底。核を下から隠しきらない輪)
    F += part("base", ellipsoid("body", "cloud", (0, 6.6, 0), (4.0, 0.9, 3.6), segs=8, rings=1))
    for i, (x, z) in enumerate(((-2.0, -1.2), (1.6, -1.6), (0.2, 1.8), (2.6, 1.0), (-2.6, 1.4))):
        b = tuft_of((x, -0.2, z))
        F += part(f"thread{i}", tube(b, "spark", [(x, 6.2, z), (x * 1.1 + 0.3, 4.6, z * 1.1), (x * 1.0 - 0.2, 3.0, z)], ra=[0.22, 0.16, 0.06], sides=3))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


# 逆立つ (帯電の予告): 房が外へ開く。前 = -Z。tuft_n は前へ (x 回転 -) …の向きで開く
BRISTLE = {"tuft_n": (-28, 0, 0), "tuft_s": (28, 0, 0), "tuft_e": (0, 0, -28), "tuft_w": (0, 0, 28), "tuft_t": (0, 0, 0)}
POSES = {
    "idle": {},
    "half": {k: tuple(v * 0.5 for v in vv) for k, vv in BRISTLE.items()},
    "bristle": BRISTLE,
}

SPEC = dict(
    id="static_puff", short="帯電毛玉", kind="地上の敵 3案 (P16)",
    title="帯電毛玉 (static_puff) — 宙に浮く綿毛の塊・低ポリ + ドット絵 3 案",
    sub=["三章の地上の敵 (峰・高台・焼けた見張り塔の中の間)。HP 10、放電 4。4 秒かけて帯電し、満ちると放電する。人が固まっていると隣の人へ飛ぶ (離れて立て)。",
         "予告は「毛が逆立つ」(房が外へ開き、火花が散る)。背中の雷蛾の連鎖の予習。全体 約 0.7 ブロック。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 綿毛の玉", "B 静電の羊毛", "C 雷雲の子"],
    rows=[("前 3/4 (ふだん)", "idle", 212, 14, 300, (0, 8, 0), 16.0),
          ("横", "idle", 270, 4, 300, (0, 8, 0), 16.0),
          ("予告: 毛が逆立ちはじめる (2 秒)", "half", 200, 18, 300, (0, 8, 0), 16.0),
          ("予告: 逆立ちきった (4 秒。放電の直前)", "bristle", 200, 18, 300, (0, 8, 0), 16.0),
          ("上から", "bristle", 180, 70, 300, (0, 8, 0), 14.0)],
    options={
        "a": dict(name="A 綿毛の玉", desc="白い綿毛の芯から細い毛が放射に伸び、毛の先に青白い火花の粒が光る。たんぽぽの綿毛を大きくした形で、逆立つと毛がまっすぐ開いて一回り大きく見える。毛の先の光で帯電の向きが読める。"),
        "b": dict(name="B 静電の羊毛", desc="もこもこの毛の房を寄せた塊に黒い目が二つ。中に銅の種。かわいく見えて、逆立つと房がふくらみ火花の棘が立つ。生き物らしさが一番強く、群れで漂うと羊の群れのよう。"),
        "c": dict(name="C 雷雲の子", desc="灰青の平たい雲の塊。底から静電の糸が垂れ、雲の隙間から中の核が光る。雷翼の嵐の雲の子どもという話が付く。逆立つと雲の房が開いて核が見える (予告がいちばん光で分かる)。")},
    extra="三章の言葉 (白い羽毛・峰の灰青・銅・雷の青白)。骨 7 本 (root > body > 房 5)。逆立つは房の骨の回転だけ (アニメでは体の膨らみの scale も足す)。光るのは火花と核だけ。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
