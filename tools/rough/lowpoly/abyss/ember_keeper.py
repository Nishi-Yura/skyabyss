"""燠の番人 (ember_keeper) 低ポリ ラフ 3 案。灰の層の人型の番人。熱の気配が先に来る (近づくと空気が揺れる)。
A 炭の番人 (玄武の人型、胸に熾のひび、両手の間に熾の玉) / B 焚き火の甲冑 (黒石の板の鎧、隙間から熾の通気、頭は兜の切れ目に熾) / C 灰の僧 (灰布の長い外套、頭の上の高い頭巾、鎖で吊るした熾の灯し)
骨: root > body > head / arm_l / arm_r ; root > leg_l / leg_r。人の高さ約 28 単位 (1.8 ブロック)。三角形は 400〜600 が目安。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 13.0, 0)), "head": ("body", (0, 23.0, 0)),
         "arm_l": ("body", (4.4, 21.0, 0)), "arm_r": ("body", (-4.4, 21.0, 0)),
         "leg_l": ("root", (1.8, 12.5, 0)), "leg_r": ("root", (-1.8, 12.5, 0)),
         # 2026-10-23 (C を作り直し): 裾・前腕・頭巾の尾・鎖・灯しを分けた (骨 15)
         "hem": ("body", (0, 13.0, 0)), "forearm_l": ("arm_l", (5.0, 17.6, -0.9)), "forearm_r": ("arm_r", (-5.0, 17.6, -0.9)),
         "hood_t1": ("head", (0, 27.2, 1.6)), "hood_t2": ("hood_t1", (0, 28.9, 5.1)),
         "chain": ("forearm_l", (5.1, 14.2, -2.7)), "lamp": ("chain", (5.15, 11.4, -2.8))}
PAL = {"mats": {
    "basalt": mat(BASALT, 0.56, crack=0.1, noise=0.16),
    "char": mat(CHAR, 0.5, crack=0.0, noise=0.1),
    "ash": mat(ASH, 0.6, crack=0.04, noise=0.2),
    "soot": mat(SOOT, 0.5, crack=0.0, noise=0.12),
    "ember": mat(EMBER, 0.78, crack=0.0, noise=0.05, min=2),
    "ashd": mat(ASH, 0.42, crack=0.02, noise=0.22),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.08),
    "rope": mat([(40, 32, 24), (70, 56, 40), (102, 84, 60), (130, 110, 80)], 0.55, crack=0.0, noise=0.18),
    "wrap": mat([(30, 26, 26), (52, 46, 44), (78, 70, 64), (104, 96, 88)], 0.5, crack=0.0, noise=0.2),
    "iron": mat([(14, 14, 18), (32, 32, 38), (56, 56, 64), (88, 88, 96)], 0.55, crack=0.0, noise=0.1),
}, "glow_mats": ["ember"]}


def legs(F, mat_="char", foot_fix=0.0):
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(b, tube(b, mat_, [(s * 1.8, 12.5, 0), (s * 2.2, 6.0, -0.4), (s * 2.0, 0.9, -1.2)],
                          ra=[1.5, 1.15, 0.95], sides=9, caps=(True, True)))


def arms(F, mat_, hands_together=False):
    for s, b in ((1, "arm_l"), (-1, "arm_r")):
        hx = 0.8 * s if hands_together else 3.6 * s
        F += part(b, tube(b, mat_, [(s * 4.4, 21.0, 0), (s * 5.6, 16.0, -0.8), (hx, 12.8, -2.2)],
                          ra=[1.2, 0.95, 0.75], sides=9, caps=(True, True)))


def build_a(k):
    """A 炭の番人: 玄武の人型。胸の熾のひび、両手で熾の玉を支える。足は太い炭の柱。"""
    F = []
    R = [ring(12.0, 3.0, -1.6, 1.6, n=16), ring(17.0, 3.7, -1.9, 1.9, n=16), ring(22.0, 3.2, -1.4, 1.4, n=16)]
    F += part("body", loft("body", "basalt", R, caps=("char", "char")))
    for i, (x, y) in enumerate(((-1.0, 16.0), (0.8, 18.2), (-0.5, 20.6))):
        F += part(f"crack{i}", tube("body", "ember", [(x - 0.6, y, -1.5), (x, y + 0.3, -1.8), (x + 0.6, y - 0.1, -1.5)], ra=[0.14, 0.2, 0.14], sides=3, caps=(True, True)))
    F += part("head", ellipsoid("head", "basalt", (0, 25.0, -0.2), (2.0, 2.2, 2.0), segs=8, rings=3))
    F += part("eyes", ellipsoid("head", "ember", (0, 25.2, -2.0), (1.0, 0.25, 0.2), segs=5, rings=1))
    arms(F, "basalt", hands_together=True)
    F += part("orb", ellipsoid("arm_l", "ember", (0, 12.6, -2.6), (1.2, 1.2, 1.2), segs=8, rings=3))
    legs(F, "char")
    return F, [eye(-0.5, 25.2, -2.3, 0.22), eye(0.5, 25.2, -2.3, 0.22)]


def build_b(k):
    """B 焚き火の甲冑: 黒石の板の鎧。胸と肩に板、板の隙間から熾の通気。頭は兜の切れ目に熾。"""
    F = []
    R = [ring(11.0, 3.4, -2.0, 2.0, n=16), ring(18.0, 4.2, -2.4, 2.4, n=16), ring(23.0, 3.6, -1.8, 1.8, n=16)]
    F += part("body", loft("body", "soot", R, caps=("char", "soot")))
    for i, y in enumerate((14.0, 17.2, 20.4)):
        F += part(f"plate{i}", ellipsoid("body", "basalt", (0, y, -2.0), (3.6, 0.9, 0.6), segs=6, rings=1))
        F += part(f"vent{i}", tube("body", "ember", [(-2.6, y - 0.3, -2.6), (0, y + 0.2, -2.9), (2.6, y - 0.3, -2.6)], ra=[0.16, 0.2, 0.16], sides=3, caps=(True, True)))
    F += part("helm", ellipsoid("head", "basalt", (0, 24.4, -0.2), (2.4, 2.4, 2.2), segs=8, rings=3))
    F += part("slit", ellipsoid("head", "ember", (0, 23.6, -2.2), (1.3, 0.25, 0.2), segs=5, rings=1))
    for i, s in enumerate((-1, 1)):
        F += part(f"spike{i}", cone("head", "basalt", (s * 1.2, 26.4, 0), (s * 0.3, 1.0, 0), 2.4, 0.5, sides=4, rings=2))
    arms(F, "soot")
    legs(F, "basalt")
    return F, [eye(-0.8, 23.6, -2.3, 0.2), eye(0.8, 23.6, -2.3, 0.2)]


def _arc(bone, mat_, p0, p1, p2, r=0.5, n=6, sides=4, taper=None):
    """3 点を通る曲がった管 (二次の曲線)。両端を塞ぐ。"""
    p0, p1, p2 = (np.asarray(p, float) for p in (p0, p1, p2))
    pts = [(1 - t) ** 2 * p0 + 2 * t * (1 - t) * p1 + t * t * p2 for t in [i / n for i in range(n + 1)]]
    ra = r if taper is None else [taper[0] + (taper[1] - taper[0]) * i / n for i in range(n + 1)]
    return tube(bone, mat_, pts, ra=ra, sides=sides, caps=(True, True))


def build_c(k):
    """C 灰の僧 (2026-10-23 作り直し。前は外套の円錐と左腕だけで、右腕・脚の骨が空、歩くと衣が滑った):
    上の外套 (body) と裾 (hem) を分け、裾の下から包んだ足先が出る (歩くと裾が揺れ、足が前へ出る)。
    頭巾は顔の縁を太い折り返しで囲み、奥は虚の黒に熾の目二つ。頭巾の尾は二節 (hood_t1 > hood_t2) で背へ流れる。
    両腕は袖 (arm_*) と広がる袖口の前腕 (forearm_*) に分け、左手から鎖 (chain) で熾の灯し (lamp: 籠の格子と上下の笠) を吊る。
    右手は炭の指を開く (熱の輪を出す手)。胸の合わせ目から熾のひびがのぞく (近づく人に先に見える熱)。縄の帯を腰に巻く。"""
    F = []
    # ---- 上の外套 (body): 腰 11.6 → 首 23.6
    R = [ring(11.6, 4.0, -3.0, 3.0, n=14), ring(14.5, 3.9, -2.8, 2.9, n=14), ring(17.6, 3.8, -2.6, 2.8, n=14),
         ring(20.4, 4.2, -2.5, 2.7, n=14), ring(22.4, 3.8, -2.2, 2.4, n=14), ring(23.6, 2.0, -1.5, 1.7, n=14)]
    F += part("cloak", loft("body", "ash", R, caps=("ash", "char")))
    # 肩掛け (上から被せた短い外套)。下の縁は外套の外へ少し出る
    M = [ring(23.8, 2.6, -2.0, 2.2, n=14), ring(22.6, 4.5, -2.9, 3.1, n=14), ring(20.6, 4.9, -3.2, 3.4, n=14)]
    F += part("mantle", loft("body", "ashd", M, caps=("ashd", "ashd")))
    # 胸の合わせ目 (暗い縦の溝) と、その奥の熾のひび
    F += part("seam", ellipsoid("body", "char", (0, 16.6, -2.62), (0.9, 3.6, 0.5), segs=6, rings=3))
    for i, (x, y, l) in enumerate(((-0.25, 15.0, 1.2), (0.3, 16.8, 1.4), (-0.2, 18.4, 1.0))):
        F += part(f"crack{i}", tube("body", "ember", [(x - 0.45, y - 0.5 * l, -2.75), (x + 0.2, y, -2.95), (x - 0.15, y + 0.5 * l, -2.75)],
                                    ra=[0.16, 0.26, 0.16], sides=4, caps=(True, True)))
    # 縄の帯 (腰を一周、前で結んで垂らす)
    pts = []
    for j in range(13):
        a = math.radians(14 + 332 * j / 12)
        c = math.cos(a)
        pts.append((4.15 * math.sin(a), 12.6 + 0.25 * c, -3.15 * c if c > 0 else 3.15 * -c))
    F += part("belt", tube("body", "rope", pts, ra=0.42, sides=4, caps=(True, True)))
    F += part("knot", ellipsoid("body", "rope", (0.0, 12.5, -3.35), (0.75, 0.6, 0.5), segs=6, rings=2))
    F += part("tassel", _arc("body", "rope", (0.2, 12.2, -3.4), (0.6, 10.6, -3.7), (0.4, 8.8, -3.6), r=0.3, n=4, sides=4, taper=(0.32, 0.18)))
    # ---- 裾 (hem): 腰 13.6 → 床 0.4。裾は前後に開き、縁は波打つ
    H = [ring(13.6, 3.85, -2.85, 2.95, n=14), ring(9.5, 4.4, -3.3, 3.4, n=14), ring(5.0, 4.9, -3.7, 3.9, n=14),
         ring(1.6, 5.3, -4.0, 4.3, n=14)]
    lip = ring(0.4, 5.5, -4.1, 4.5, n=14)
    lip = [p + np.array([0, (0.5 if j % 2 else -0.2), 0]) for j, p in enumerate(lip)]
    H.append(lip)
    F += part("skirt", loft("hem", "ash", H, mats=["ash", "ash", "ash", "ashd"], caps=("ash", "char")))
    # 裾の縁の破れ (垂れた布の切れ端)
    for j, a in enumerate((150, 205, 30)):
        sa, ca = math.sin(math.radians(a)), math.cos(math.radians(a))
        x, z = 5.3 * sa, (-4.0 if ca > 0 else 4.3) * abs(ca)
        F += part(f"rag{j}", cone("hem", "ashd", (x * 0.96, 1.9, z * 0.96), (sa * 0.08, -1.0, ca * 0.08), 1.5, 0.55, sides=4, rings=2))
    # ---- 足 (leg_*): 脛は裾の中、包んだ足先だけが前へ出る
    for s, b in ((1, "leg_l"), (-1, "leg_r")):
        F += part(b, tube(b, "char", [(s * 1.8, 12.5, 0), (s * 1.9, 6.5, -0.6), (s * 1.9, 1.6, -1.6)], ra=[1.3, 1.05, 0.9], sides=7, caps=(True, True)))
        F += part(b + "_foot", ellipsoid(b, "wrap", (s * 1.9, 0.8, -3.2), (1.05, 0.8, 2.0), segs=8, rings=2))
        F += part(b + "_toe", ellipsoid(b, "char", (s * 1.9, 0.6, -4.9), (0.75, 0.5, 0.55), segs=6, rings=2))
    # ---- 頭巾 (head): 高い頭巾、顔の縁の折り返し、奥の虚
    F += part("hood", ellipsoid("head", "ash", (0, 25.5, 0.4), (2.5, 3.3, 2.6), segs=10, rings=4))
    F += part("face", ellipsoid("head", "void", (0, 25.0, -1.85), (1.45, 1.85, 0.65), segs=8, rings=2))
    F += part("brim", tube("head", "ashd", [(-1.55, 22.9, -1.7), (-1.95, 24.8, -2.05), (-1.4, 26.8, -2.15), (0, 27.6, -2.2),
                                            (1.4, 26.8, -2.15), (1.95, 24.8, -2.05), (1.55, 22.9, -1.7)],
                           ra=[0.5, 0.55, 0.55, 0.55, 0.55, 0.55, 0.5], sides=5, hint=(0, 0, 1), caps=(True, True)))
    F += part("neck", ellipsoid("head", "char", (0, 23.0, 0.2), (1.6, 1.0, 1.5), segs=8, rings=2))
    # ---- 頭巾の尾 (二節)
    F += part("tail1", tube("hood_t1", "ash", [(0, 26.4, 1.0), (0, 28.0, 2.9), (0, 28.9, 5.2)], ra=[1.35, 1.15, 0.95], sides=6, hint=(1, 0, 0), caps=(True, True)))
    F += part("tail2", tube("hood_t2", "ashd", [(0, 28.9, 4.9), (0, 29.3, 7.1), (0, 28.7, 9.0), (0, 27.5, 10.4)], ra=[0.95, 0.75, 0.45, 0.0],
                            sides=6, hint=(1, 0, 0), caps=(True, False)))
    # ---- 腕 (袖) と前腕 (広がる袖口・炭の手)
    for s, a_, f_ in ((1, "arm_l", "forearm_l"), (-1, "arm_r", "forearm_r")):
        F += part(a_ + "_sh", ellipsoid(a_, "ashd", (s * 4.3, 21.6, 0.1), (1.7, 1.6, 1.8), segs=8, rings=2))
        F += part(a_, tube(a_, "ash", [(s * 4.3, 21.4, 0.1), (s * 4.8, 19.4, -0.4), (s * 5.0, 17.4, -0.9)], ra=[1.35, 1.2, 1.1], sides=8, caps=(True, True)))
        F += part(f_, tube(f_, "ash", [(s * 5.0, 17.9, -0.8), (s * 5.05, 16.4, -1.6), (s * 5.1, 15.0, -2.4)], ra=[1.1, 1.35, 1.65], sides=8, caps=(True, True)))
        F += part(f_ + "_cuff", tube(f_, "ashd", [(s * 5.1, 15.3, -2.25), (s * 5.12, 14.75, -2.55)], ra=[1.75, 1.7], sides=8, caps=(True, True)))
        F += part(f_ + "_hand", ellipsoid(f_, "char", (s * 5.12, 14.2, -2.75), (0.8, 0.95, 0.8), segs=7, rings=2))
    # 右手: 開いた炭の指 (熱の輪を出す手) と掌の熾
    for j, (dx, dz) in enumerate(((-0.45, -0.45), (0.0, -0.6), (0.45, -0.45), (-0.6, 0.25))):
        F += part(f"finger{j}", cone("forearm_r", "char", (-5.12 + dx * 0.6, 13.7, -2.75 + dz * 0.6), (dx * 0.6, -1.0, dz), 1.3, 0.3, sides=4, rings=2))
    F += part("palm_glow", ellipsoid("forearm_r", "ember", (-5.12, 13.6, -2.75), (0.45, 0.22, 0.45), segs=6, rings=1))
    # ---- 左手から鎖と灯し
    F += part("grip", ellipsoid("chain", "iron", (5.12, 14.0, -2.75), (0.55, 0.35, 0.55), segs=6, rings=1))
    for j in range(4):
        y = 13.6 - j * 0.6
        F += part(f"link{j}", ellipsoid("chain", "iron", (5.13, y, -2.76), (0.18 if j % 2 else 0.34, 0.42, 0.34 if j % 2 else 0.18), segs=6, rings=2))
    F += part("lamp_ring", ellipsoid("lamp", "iron", (5.15, 11.75, -2.8), (0.35, 0.4, 0.35), segs=6, rings=2))
    F += part("lamp_top", ellipsoid("lamp", "soot", (5.15, 11.25, -2.8), (1.15, 0.45, 1.15), segs=8, rings=2))
    F += part("lamp_core", ellipsoid("lamp", "ember", (5.15, 10.15, -2.8), (0.8, 1.05, 0.8), segs=8, rings=3))
    for j in range(4):
        a = math.radians(45 + 90 * j)
        x, z = 5.15 + 0.95 * math.cos(a), -2.8 + 0.95 * math.sin(a)
        F += part(f"bar{j}", tube("lamp", "iron", [(x, 11.1, z), (x + 0.08 * math.cos(a), 10.15, z + 0.08 * math.sin(a)), (x, 9.2, z)],
                                  ra=0.15, sides=4, caps=(True, True)))
    F += part("lamp_base", ellipsoid("lamp", "soot", (5.15, 9.1, -2.8), (1.0, 0.38, 1.0), segs=8, rings=2))
    F += part("lamp_tip", cone("lamp", "iron", (5.15, 8.85, -2.8), (0, -1, 0), 1.0, 0.35, sides=4, rings=2))
    return F, [eye(-0.6, 25.2, -2.45, 0.2), eye(0.6, 25.2, -2.45, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "bow": {"body": (14, 0, 0), "head": (-8, 0, 0), "arm_l": (-50, 0, -15), "forearm_l": (-30, 0, 0),
                             "arm_r": (-50, 0, 15), "forearm_r": (-30, 0, 0)},
         "reach": {"arm_l": (0, 0, -40)}}

LATER = {'a': '歪み: 胸のひびが全身に広がり、目が熾の穴になる / 崩れ: 玉が割れて二つになり、手が炭に戻る。', 'b': '歪み: 板が外れて熾の心臓が露出する / 崩れ: 鎧が溶けて黒い柱の人型だけが残る。', 'c': '歪み: 外套が燃え上がり、灯しが二つになる / 崩れ: 外套が灰の柱になり、人影が消えて灯しだけが浮く。'}

SPEC = dict(
    id="ember_keeper", short="燠の番人", kind="灰の層の敵 3案 (人型・熱)",
    title="燠の番人 (ember_keeper) — 熱の気配で近づく灰の層の人型・低ポリ + ドット絵 3 案",
    sub=["灰の層の人型の番人。近づくと周りの空気が揺れ、胸の熾が先に見える。ゆっくり歩き、熱の強い所 (火・熾) に立つ。",
         "左の目盛りは人 (1.8 ブロック)。熾の橙は光る画素。"],
    labels=["A 炭の番人", "B 焚き火の甲冑", "C 灰の僧"],
    poses=POSES,
    options={
        "a": dict(name="A 炭の番人", desc="玄武の人型。胸の熾のひびと、両手の間の熾の玉。動きが一番ゆっくりで、手の玉が近づく人の顔を照らす。見る人には人間に近い形で、灰の層の人型の基準になる。"),
        "b": dict(name="B 焚き火の甲冑", desc="黒石の板を重ねた鎧。板の隙間から熾の通気がのぞき、兜の切れ目に熾。肩の棘が尖り、怒ると熱が見えるように見える。鎧の板は一枚ずつ別の骨に分ける案。"),
        "c": dict(name="C 灰の僧", desc="灰布の長い外套の人影。裾は床まで、頭巾の尾が背へ流れる。鎖で吊った熾の灯しを掲げて歩く。近づくまで姿が分からず、灯しの光で人型だと知れる。"),
    },
    extra="灰の層 (余燼) の人型の番人。骨 7 本 (root > body > head / arm_l / arm_r、root > leg_l / leg_r)。鍵は熾のひび・玉・灯し。",
    budget="人型 400〜600",
    later=LATER,
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 13, 0),6.0, None),
        ("横", "idle", 270, 6, 260, (0, 13, 0),6.0, None),
        ("後ろ 3/4", "idle", 32, 16, 260, (0, 13, 0),6.0, None),
        ("前かがみ (熱が出る)", "bow", 212, 18, 260, (0, 13, 0),6.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=256)
