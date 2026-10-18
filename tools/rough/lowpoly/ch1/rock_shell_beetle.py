"""岩殻虫 (rock_shell_beetle) 低ポリ ラフ 3 案。盾持ちの予習: 前の殻 (灰青) は傷が半分、背と腹は通る。
立ち上がって (0.6 秒の予告) 体当たり。殻の縁に琥珀の目。HP 22。地上の小物 300〜500 三角形。
A 岩甲虫 (丸いドーム) / B 岩ワラジ (帯の連なり。前の帯を起こして盾にする) / C 背負い板 (やわらかい体が前へ傾けた岩の板を背負う)
"""
from ck import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 5, 0)), "head": ("body", (0, 5, -9)),
         "shield": ("body", (0, 5, -6)),
         "leg_fl": ("body", (-5, 5, -5)), "leg_fr": ("body", (5, 5, -5)),
         "leg_ml": ("body", (-6, 5, 0)), "leg_mr": ("body", (6, 5, 0)),
         "leg_bl": ("body", (-5, 5, 5)), "leg_br": ("body", (5, 5, 5))}
PAL = {"mats": {
    "stone": mat(STONE, 0.66, crack=0.22, stain=dict(ramp=MOSS, thr=0.93, up=0.3, conc=0.2)),
    "rim": mat(BROWN, 0.6, crack=0.1, noise=0.15),
    "bone": mat(BONE, 0.7, crack=0.05),
    "flesh": mat([(40, 34, 36), (80, 66, 60), (118, 98, 84), (152, 130, 108)], 0.62, crack=0.0, noise=0.2),
    "dark": mat(DARK, 0.5, crack=0.0, min=0),
    "moss": mat(MOSS, 0.6, crack=0.0, noise=0.25),
}, "skull_bone": "head", "face_mat": "rim"}


def eye_at(x, y, z, r=0.9):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.6, pr=(0.55, 0.38), tilt=0.15, zr=2.2)


def legs(F, pairs, y0=5.0, kind="insect", splay=1.0):
    """pairs: [(bone, side, z, reach)]。昆虫の 3 節の脚。"""
    for nm, s, z, reach in pairs:
        b = f"leg_{nm}"
        F += part(b, tube(b, "rim", [(s * 5.0, y0, z), (s * (5.8 + reach * 0.3), y0 + 2.0, z + 0.2), (s * (7.5 + reach), y0 - 1.0, z + reach * 0.15),
                                     (s * (8.2 + reach), 0.0, z + reach * 0.3)],
                          ra=[1.3, 1.1, 0.9, 0.3], rb=[1.1, 0.9, 0.8, 0.3], sides=4, hint=(0, 1, 0)))


def build_a(k):
    F, eyes = [], []
    # ドームの殻 (岩)。前が高い。縁は茶の岩殻の割れ目
    R = [ring(4.2, 7.6, -10.5, 9.0, 0, 8), ring(6.6, 8.6, -11.2, 10.0, 0, 8), ring(9.2, 7.0, -9.6, 8.6, 0, 8),
         ring(11.4, 4.2, -6.0, 6.4, 0, 8), ring(12.3, 1.8, -2.4, 2.8, 0, 8)]
    F += part("shell", loft("shield", "stone", R, caps=(False, "stone")))
    # 殻のこぶ (岩の割れ目の板)
    for z, h in ((-3.5, 2.0), (1.5, 2.6), (6.0, 1.8)):
        F += part(f"hump{z}", spike("body", "rim", (0, 12.4 - abs(z) * 0.45, z), (0, 1, 0.1), h, 1.7, sides=4, rings=1))
    for s in (-1, 1):
        F += part(f"flank{s}", ellipsoid("body", "stone", (s * 7.7, 6.0, 0.5), (1.4, 2.2, 5.5), segs=4, rings=1))
        F += part(f"chip{s}", spike("body", "bone", (s * 6.0, 10.4, -4.0), (s * 0.8, 0.9, -0.3), 2.2, 0.8, sides=3, rings=1))
        F += part(f"chip2{s}", spike("body", "bone", (s * 5.6, 10.8, 3.0), (s * 0.7, 0.9, 0.5), 2.0, 0.7, sides=3, rings=1))
    # 苔
    F += part("moss", ellipsoid("body", "moss", (1.5, 11.6, 2.2), (3.0, 0.9, 2.6), segs=5, rings=1))
    # 縁の下の頭 (前のひさしの下)
    F += part("head", ellipsoid("head", "rim", (0, 5.2, -10.0), (3.4, 2.1, 2.8), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"mand{s}", spike("head", "bone", (s * 1.7, 4.0, -12.4), (s * -0.3, -0.35, -1), 3.0, 0.9, sides=3, rings=1))
        F += part(f"ant{s}", tube("head", "rim", [(s * 1.5, 6.2, -11.6), (s * 2.8, 8.0, -14.4), (s * 4.4, 7.0, -17.0)], ra=[0.55, 0.45, 0.15], sides=3))
    eyes = [eye_at(-2.2, 5.9, -11.9), eye_at(2.2, 5.9, -11.9)]
    # 柔らかい尻 (後ろ・腹は殻がない)
    F += part("rump", ellipsoid("body", "flesh", (0, 4.6, 9.4), (4.4, 2.8, 3.2), segs=6, rings=2))
    F += part("belly", ellipsoid("body", "flesh", (0, 3.0, 0.0), (5.4, 1.3, 7.5), segs=6, rings=1))
    legs(F, [("fl", -1, -6, 0.6), ("fr", 1, -6, 0.6), ("ml", -1, -0.5, 1.2), ("mr", 1, -0.5, 1.2), ("bl", -1, 5, 1.0), ("br", 1, 5, 1.0)])
    return F, eyes


def build_b(k):
    """B 岩ワラジ: 帯が 5 枚連なる扁平な体。前の 2 枚が盾の帯 (shield 骨) で、起こして壁になる。"""
    F, eyes = [], []
    bands = [(-7.5, 8.0, 7.8, 4.6), (-3.8, 8.6, 8.8, 4.8), (-0.0, 9.0, 9.2, 4.6), (3.8, 8.4, 8.6, 4.2), (7.4, 6.8, 7.0, 3.6)]
    for i, (z, rx, ry, dz) in enumerate(bands):
        bone = "shield" if i < 2 else "body"
        R = [ring(3.2, rx * 0.95, z - dz, z + dz, 0, 6) , ring(5.8, rx, z - dz * 1.05, z + dz * 1.05, 0, 6),
             ring(8.6, rx * 0.8, z - dz * 0.9, z + dz * 0.9, 0, 6), ring(10.2 - 0.3 * i, rx * 0.4, z - dz * 0.5, z + dz * 0.5, 0, 6)]
        # 帯の中心を手前に寄せて隣と少し重ねず積む (重ならない)
        for m in R:
            for p in m:
                p[2] += 0.0
        F += part(f"band{i}", loft(bone, "stone", R, caps=(False, "rim")))
        F += part(f"crest{i}", spike(bone, "rim", (0, 10.0 - 0.3 * i, z), (0, 1, -0.1), 1.8, 1.3, sides=3, rings=1))
    # 最後の尾の板
    F += part("tail", spike("body", "stone", (0, 5.0, 11.8), (0, -0.05, 1), 4.0, 3.2, sides=4, rings=1))
    # 頭: 低く丸い。前の帯の下
    F += part("head", ellipsoid("head", "rim", (0, 4.8, -12.6), (4.0, 2.4, 2.6), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"ant{s}", tube("head", "rim", [(s * 2.0, 5.8, -14.4), (s * 3.6, 6.4, -17.5), (s * 5.2, 5.2, -20.0)], ra=[0.6, 0.45, 0.15], sides=3))
        F += part(f"tusk{s}", spike("head", "bone", (s * 2.2, 3.4, -14.4), (s * 0.15, -0.3, -1), 2.4, 0.8, sides=3, rings=1))
    eyes = [eye_at(-2.6, 5.4, -14.6, 1.0), eye_at(2.6, 5.4, -14.6, 1.0)]
    legs(F, [("fl", -1, -7.5, 0.8), ("fr", 1, -7.5, 0.8), ("ml", -1, -1.0, 1.2), ("mr", 1, -1.0, 1.2), ("bl", -1, 5.0, 1.0), ("br", 1, 5.0, 1.0)], y0=4.0)
    for s in (-1, 1):
        F += part(f"edge{s}", ellipsoid("body", "bone", (s * 9.4, 4.0, 0.5), (0.9, 1.3, 9.5), segs=4, rings=1))
    return F, eyes


def build_c(k):
    """C 背負い板: 丸くやわらかい体 + 前へ傾けた大きな岩の板 (shield 骨)。琥珀の目は板の縁の下からのぞく。"""
    F, eyes = [], []
    # 板: 前に傾いた厚い岩のスラブ (面取りの 5 角柱)。裏は白っぽい根と苔
    R = [[V(-8, 7.0, -9.0), V(-8.5, 17.0, -8.0), V(-3, 20.5, -7.5), V(3, 20.0, -7.6), V(8.5, 16.5, -8.0), V(8, 7.0, -9.0)],
         [V(-8, 7.0, -5.0), V(-8.5, 18.0, -4.0), V(-3, 21.5, -3.5), V(3, 21.0, -3.6), V(8.5, 17.5, -4.0), V(8, 7.0, -5.0)]]
    F += part("slab", loft("shield", "stone", R, caps=("stone", "rim")))
    for x, y in ((-4, 13), (3, 16), (0, 10)):
        F += part(f"slabcrack{x}", spike("shield", "rim", (x, y, -9.2), (0, 0.2, -1), 1.6, 1.2, sides=3, rings=1))
    F += part("slabmoss", ellipsoid("shield", "moss", (-3.5, 20.8, -5.4), (3.4, 0.9, 2.0), segs=5, rings=1))
    # 体: 低く丸いなめくじ型
    R2 = [ring(2.0, 5.8, -9.5, 8.0, 0, 8), ring(4.2, 7.0, -10.5, 9.2, 0, 8), ring(7.0, 5.8, -8.5, 8.0, 0, 8), ring(8.6, 3.0, -4.5, 5.5, 0, 8)]
    F += part("body", loft("body", "flesh", R2, caps=(False, "flesh")))
    # 頭: 板の下に突き出る
    F += part("head", ellipsoid("head", "flesh", (0, 4.4, -11.6), (3.6, 2.6, 3.0), segs=6, rings=2))
    F += part("brow", ellipsoid("head", "rim", (0, 6.6, -11.6), (4.0, 0.9, 2.6), segs=5, rings=1))
    for s in (-1, 1):
        F += part(f"tusk{s}", spike("head", "bone", (s * 1.8, 3.0, -13.6), (s * 0.35, -0.4, -1), 3.2, 0.9, sides=3, rings=1))
        F += part(f"horn{s}", tube("head", "bone", [(s * 2.8, 6.6, -10.8), (s * 4.6, 8.6, -11.8), (s * 5.4, 11.0, -13.6)], ra=[0.9, 0.7, 0.15], sides=3))
    eyes = [eye_at(-2.4, 5.6, -13.8, 1.0), eye_at(2.4, 5.6, -13.8, 1.0)]
    # 短い 6 本の脚
    for nm, s, z in (("fl", -1, -6), ("fr", 1, -6), ("ml", -1, -0.5), ("mr", 1, -0.5), ("bl", -1, 5), ("br", 1, 5)):
        b = f"leg_{nm}"
        F += part(b, tube(b, "flesh", [(s * 4.6, 4.0, z), (s * 6.4, 2.0, z + 0.3), (s * 7.2, 0.0, z + 0.6)], ra=[1.5, 1.3, 1.0], sides=4, hint=(0, 1, 0)))
        F += part(b + "c", spike(b, "bone", (s * 7.2, 0.6, z + 0.6), (s * 0.3, -0.3, -1), 1.8, 0.8, sides=3, rings=1))
    F += part("tailnub", spike("body", "flesh", (0, 3.4, 9.6), (0, 0.2, 1), 3.4, 2.3, sides=4, rings=1))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


# 立ち上がり (予告 0.6 秒) = 前を持ち上げる。体当たり = 前のめり
POSES = {
    "idle": {},
    "rear": {"body": (-38, 0, 0), "leg_fl": (30, 0, 0), "leg_fr": (30, 0, 0)},
    "rear_b": {"body": (-34, 0, 0), "shield": (-26, 0, 0), "leg_fl": (30, 0, 0), "leg_fr": (30, 0, 0)},
    "charge": {"body": (12, 0, 0), "leg_ml": (0, 0, 0)},
}

SPEC = dict(
    id="rock_shell_beetle", short="岩殻虫", kind="地上の敵 3案 (P7)",
    title="岩殻虫 (rock_shell_beetle) — 盾持ちの予習・低ポリ + ドット絵 3 案",
    sub=["一章の地上の敵。前の灰青の殻は傷が半分、背と腹は通る。立ち上がって (0.6 秒の予告) 体当たり。殻の縁の琥珀の目。HP 22。",
         "全身 約 28 ユニット幅の小型 (地面を這う)。左の黄の目盛りは人 (1.8 ブロック) の高さ。予告の姿は「立ち上がり」の行。"],
    labels=["A 岩甲虫", "B 岩ワラジ", "C 背負い板"],
    rows=[("前から (盾の面。ここを撃っても傷は半分)", "idle", 180, 8, 380, (0, 7, 0), 18.0),
          ("横", "idle", 270, 6, 380, (0, 7, -1), 18.0),
          ("前 3/4", "idle", 212, 14, 400, (0, 7, 0), 18.0),
          ("後ろ 3/4 (背と腹は通る)", "idle", 32, 16, 400, (0, 7, 0), 18.0),
          ("予告: 立ち上がる 0.6 秒 (横から)", "rear", 270, 6, 440, (0, 9, -2), 13.5),
          ("予告: 立ち上がる 0.6 秒 (前 3/4)", "rear", 212, 10, 440, (0, 10, -2), 13.5)],
    options={
        "a": dict(name="A 岩甲虫", desc="丸いドームの殻。縁は茶の割れ目、背に骨の欠片のこぶ、頭は前のひさしの下で、殻の縁の下に琥珀の目 2 つと骨の牙。尻と腹はむき出しのやわらかい肉色で、背を取る狙いが読める。立ち上がると前の殻が壁になる。最も素直な「盾の虫」。"),
        "b": dict(name="B 岩ワラジ", desc="帯の板が 5 枚連なる扁平な体 (ダンゴムシ・ワラジ)。前の 2 枚が盾の骨で、起こすとそのまま壁になる。尾は一枚の厚い板。低く長い横の輪郭で、ほかの 2 案と見分けやすい。腹は明るい色の脚と裏で、ひっくり返すと弱い、という読みも足せる。"),
        "c": dict(name="C 背負い板", desc="やわらかい丸い体が、前へ傾けた岩の板を背負う。殻ではなく「板を盾にしている」ので、背中から回れば板が邪魔にならないのが一目で分かる。板は割れ目と苔つき、頭は板の下から骨の牙と琥珀の目を出す。盾の向きが一番はっきり読める案。")},
    extra="一章の言葉 (灰青の殻・骨の棘・琥珀の目・茶の岩殻の割れ目・苔)。骨 10 本 (root > body > head / shield / leg ×6)。leg は 3 節をひとつの骨で曲げる簡単な作り。立ち上がりは body を後ろへ倒し前脚を上げるだけで読める (ゲームの予告の 0.6 秒)。",
    budget="地上の小物 300〜500", atlas_h=300)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, {k: v for k, v in POSES.items()})
