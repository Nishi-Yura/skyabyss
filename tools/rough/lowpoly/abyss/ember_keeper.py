"""燠の番人 (ember_keeper) 低ポリ ラフ 3 案。灰の層の人型の番人。熱の気配が先に来る (近づくと空気が揺れる)。
A 炭の番人 (玄武の人型、胸に熾のひび、両手の間に熾の玉) / B 焚き火の甲冑 (黒石の板の鎧、隙間から熾の通気、頭は兜の切れ目に熾) / C 灰の僧 (灰布の長い外套、頭の上の高い頭巾、鎖で吊るした熾の灯し)
骨: root > body > head / arm_l / arm_r ; root > leg_l / leg_r。人の高さ約 28 単位 (1.8 ブロック)。三角形は 400〜600 が目安。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 13.0, 0)), "head": ("body", (0, 23.0, 0)),
         "arm_l": ("body", (4.4, 21.0, 0)), "arm_r": ("body", (-4.4, 21.0, 0)),
         "leg_l": ("root", (1.8, 12.5, 0)), "leg_r": ("root", (-1.8, 12.5, 0))}
PAL = {"mats": {
    "basalt": mat(BASALT, 0.56, crack=0.1, noise=0.16),
    "char": mat(CHAR, 0.5, crack=0.0, noise=0.1),
    "ash": mat(ASH, 0.6, crack=0.04, noise=0.2),
    "soot": mat(SOOT, 0.5, crack=0.0, noise=0.12),
    "ember": mat(EMBER, 0.78, crack=0.0, noise=0.05, min=2),
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


def build_c(k):
    """C 灰の僧: 灰布の長い外套 (裾は床まで)、頭巾の高い尾、鎖で吊った熾の灯し。"""
    F = []
    R = [ring(0.5, 5.2, -4.0, 4.0, n=16), ring(5.0, 5.0, -3.8, 3.8, n=16), ring(9.0, 4.6, -3.4, 3.4, n=16), ring(13.0, 3.9, -2.9, 2.9, n=16), ring(17.0, 3.2, -2.4, 2.4, n=16), ring(20.5, 2.6, -1.9, 1.9, n=16), ring(23.0, 2.2, -1.6, 1.6, n=16)]
    F += part("cloak", loft("body", "ash", R, caps=("char", "ash")))
    F += part("hood", ellipsoid("head", "char", (0, 25.6, 0.4), (2.2, 3.2, 2.2), segs=8, rings=3))
    F += part("face", ellipsoid("head", "soot", (0, 24.6, -1.8), (1.2, 1.4, 0.4), segs=5, rings=1))
    F += part("hood_tail", tube("head", "char", [(0, 27.0, 1.8), (0, 30.5, 4.0), (0, 33.0, 6.8)], ra=[1.2, 0.9, 0.3], sides=4, caps=(True, True)))
    F += part("arm_l", tube("arm_l", "ash", [(4.4, 21.0, 0), (5.2, 17.0, -1.2), (4.8, 15.0, -2.2)], ra=[1.0, 0.75, 0.6], sides=9, caps=(True, True)))
    F += part("chain", tube("arm_l", "soot", [(4.8, 15.0, -2.2), (5.2, 13.0, -2.8), (5.6, 11.0, -3.2)], ra=0.16, sides=3, caps=(True, True)))
    F += part("lamp", ellipsoid("arm_l", "ember", (5.6, 10.2, -3.2), (0.9, 1.1, 0.9), segs=8, rings=3))
    return F, [eye(-0.7, 24.6, -2.3, 0.18), eye(0.7, 24.6, -2.3, 0.18)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "bow": {"body": (14, 0, 0), "head": (-8, 0, 0)}, "reach": {"arm_l": (0, 0, -40)}}

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
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
