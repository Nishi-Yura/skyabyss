"""岩鯨の背の新しい敵 3 体の動き (GeckoLib のアニメーション JSON)。

使い方: python tools/gen_ch1_back_mob_anims.py
出力: assets/skyabyss/animations/entity/{bone_mortar,bone_gull,elder_sentinel}.animation.json

姿勢の値はラフ (tools/rough/lowpoly/ch1/*.py の POSES、メッシュの座標系で x は前へ起こす向きが +) と同じ数字を書き、
R(x, y, z) でアニメの向き (x・y の符号が逆) に直す。時間は秒。予告の動きは Telegraph の予告 tick に合わせた長さ:
  骨の砲手 aim 0.5 秒で砲身が上がる (予告 24 tick のうち)、fire 0.6 秒 (放った反動)
  骨鳥 cry 0.8 秒 (予告の前半 16 tick)、dive 0.45 秒 (後半の 8 tick)
  老番兵 rush 1.2 秒 (予告 24 tick)、stomp 1.0 秒 (予告 20 tick)、flute 1.2 秒 (予告 24 tick)
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/animations/entity"


def R(x=0, y=0, z=0):
    return [-x, -y, z]


def ts(t):
    return "%.2f" % t


def kf(points):
    return {ts(t): v for t, v in points}


def anim(length, bones, loop=False, hold=False):
    a = {"animation_length": length, "bones": bones}
    if loop:
        a["loop"] = True
    if hold:
        a["loop"] = "hold_on_last_frame"
    return a


def rot(points):
    return {"rotation": kf(points)}


def pos(points):
    return {"position": kf(points)}


def both(r, p):
    return {"rotation": kf(r), "position": kf(p)}


# ---------------------------------------------------------------- 骨の砲手
def mortar():
    z = R(0, 0, 0)
    up_cannon = R(22, 0, 0)
    up_head = R(45, 0, 0)
    return {
        # 待機: 頭がゆっくり左右を見回し、砲身が呼吸のようにわずかに揺れる
        "animation.bone_mortar.idle": anim(4.0, {
            "head": rot([(0, R(0, 0, 0)), (1.0, R(0, 9, 0)), (2.0, R(0, 0, 0)), (3.0, R(0, -9, 0)), (4.0, R(0, 0, 0))]),
            "cannon": rot([(0, R(0, 0, 0)), (2.0, R(1.5, 0, 0)), (4.0, R(0, 0, 0))]),
        }, loop=True),
        # 歩き: 重い台座が左右に傾いて進む
        "animation.bone_mortar.walk": anim(1.2, {
            "body": both([(0, R(0, 0, -2.5)), (0.3, R(0, 0, 0)), (0.6, R(0, 0, 2.5)), (0.9, R(0, 0, 0)), (1.2, R(0, 0, -2.5))],
                         [(0, [0, 0, 0]), (0.3, [0, 0.5, 0]), (0.6, [0, 0, 0]), (0.9, [0, 0.5, 0]), (1.2, [0, 0, 0])]),
            "head": rot([(0, R(0, 4, 0)), (0.6, R(0, -4, 0)), (1.2, R(0, 4, 0))]),
        }, loop=True),
        # 予告: 砲身を上げ、頭が盾の上へのぞく (ラフの telegraph)。上がりきったら小さく震えて保つ
        "animation.bone_mortar.aim": anim(0.5, {
            "cannon": rot([(0, z), (0.4, up_cannon), (0.5, R(23.5, 0, 0))]),
            "head": rot([(0, z), (0.4, up_head), (0.5, up_head)]),
        }),
        # 放つ: 反動で砲身が下がり、頭が引っ込む (ラフの fire) から元へ
        "animation.bone_mortar.fire": anim(0.6, {
            "cannon": rot([(0, up_cannon), (0.06, R(-20, 0, 0)), (0.2, R(-12, 0, 0)), (0.6, z)]),
            "head": rot([(0, up_head), (0.06, R(-15, 0, 0)), (0.3, R(-6, 0, 0)), (0.6, z)]),
            "body": pos([(0, [0, 0, 0]), (0.06, [0, 0, 1.2]), (0.3, [0, 0, 0])]),
        }),
    }


# ---------------------------------------------------------------- 骨鳥
def gull():
    def flap(length, lo, hi, bob):
        # 翼は付け根の z 回転で上下。左は負で上、右は正で上 (ラフの idle は左 -8 / 右 +8 が水平に近い滑空)
        return anim(length, {
            "wing_l": rot([(0, R(0, 0, -lo)), (length / 2, R(0, 0, hi)), (length, R(0, 0, -lo))]),
            "wing_r": rot([(0, R(0, 0, lo)), (length / 2, R(0, 0, -hi)), (length, R(0, 0, lo))]),
            "body": pos([(0, [0, 0, 0]), (length / 2, [0, -bob, 0]), (length, [0, 0, 0])]),
            "tail": rot([(0, R(0, 0, 0)), (length / 2, R(8, 0, 0)), (length, R(0, 0, 0))]),
        }, loop=True)

    return {
        "animation.bone_gull.idle": flap(0.9, 32, 18, 0.7),
        "animation.bone_gull.walk": flap(0.6, 38, 22, 0.9),
        # 予告: 頭を上げ、下あごを開き、翼を高く上げて鳴く (ラフの telegraph)
        "animation.bone_gull.cry": anim(0.8, {
            "head": rot([(0, R(0, 0, 0)), (0.25, R(18, 0, 0)), (0.8, R(18, 0, 0))]),
            "jaw": rot([(0, R(0, 0, 0)), (0.25, R(-32, 0, 0)), (0.8, R(-32, 0, 0))]),
            "wing_l": rot([(0, R(0, 0, -8)), (0.25, R(0, 0, -55)), (0.8, R(0, 0, -55))]),
            "wing_r": rot([(0, R(0, 0, 8)), (0.25, R(0, 0, 55)), (0.8, R(0, 0, 55))]),
            "body": rot([(0, R(0, 0, 0)), (0.25, R(8, 0, 0)), (0.8, R(8, 0, 0))]),
        }),
        # 急降下: 体を前へ倒し、翼を背へたたむ (ラフの dive)。最後は翼を開いて起きる
        "animation.bone_gull.dive": anim(0.45, {
            "body": rot([(0, R(8, 0, 0)), (0.08, R(-55, 0, 0)), (0.35, R(-55, 0, 0)), (0.45, R(0, 0, 0))]),
            "head": rot([(0, R(18, 0, 0)), (0.08, R(10, 0, 0)), (0.45, R(0, 0, 0))]),
            "jaw": rot([(0, R(-32, 0, 0)), (0.1, R(0, 0, 0))]),
            "wing_l": rot([(0, R(0, 0, -55)), (0.08, R(0, 70, -8)), (0.35, R(0, 70, -8)), (0.45, R(0, 0, -8))]),
            "wing_r": rot([(0, R(0, 0, 55)), (0.08, R(0, -70, 8)), (0.35, R(0, -70, 8)), (0.45, R(0, 0, 8))]),
        }),
    }


# ---------------------------------------------------------------- 老番兵 (骨の番兵と同じ骨)
def elder():
    z = R(0, 0, 0)
    walk = {
        "leg_l": ["math.sin(query.anim_time * 360 + 0) * 28", "0", "0"],
        "leg_r": ["math.sin(query.anim_time * 360 + 0) * -28", "0", "0"],
        "left_arm": ["math.sin(query.anim_time * 360 + 0) * -18", "0", "-3"],
        "right_arm": ["math.sin(query.anim_time * 360 + 0) * 18", "0", "3"],
        "body": {"rotation": ["4", "math.sin(query.anim_time * 360 + 0) * 3", "0"],
                 "position": ["0", "math.abs(math.sin(query.anim_time * 360 + 0) * 0.8)", "0"]},
        "head": ["0", "math.sin(query.anim_time * 360 + 180) * 3", "0"],
    }
    walk = {k: (v if isinstance(v, dict) else {"rotation": v}) for k, v in walk.items()}
    # 盾の突進の構え (ラフの charge): 体を沈めて前へ傾け、盾を前に低く構える
    charge = {
        "body": R(-26, 0, 0), "head": R(-8, 0, 0), "left_arm": R(62, 0, 0), "left_forearm": R(0, 90, 0),
        "right_arm": R(40, 0, 0), "right_forearm": R(-30, 0, 0), "leg_l": R(30, 0, 0), "leg_r": R(-14, 0, 0),
    }
    # 骨笛 (ラフの whistle): 頭を後ろへ反らし、右腕を曲げて笛を口へ
    whistle = {"head": R(14, 0, 0), "right_arm": R(180, -40, 10), "right_forearm": R(-150, 0, 0)}
    # 足踏みの輪 (ラフの stomp): 片脚を高く上げ、拳を頭の上へ
    stomp = {"body": R(4, 0, 0), "leg_r": R(80, 0, 0), "right_arm": R(170, 0, 0), "left_arm": R(-8, 0, 0)}

    def pose_anim(length, pose, reach, extra=None, hold_until=None):
        bones = {}
        for bone, value in pose.items():
            pts = [(0, z), (reach, value), (hold_until if hold_until else length, value)]
            bones[bone] = rot(pts)
        if extra:
            for bone, data in extra.items():
                bones.setdefault(bone, {}).update(data)
        return anim(length, bones)

    stomp_anim = pose_anim(1.0, stomp, 0.5, hold_until=0.62)
    # 踏み下ろし: 0.62 秒から脚と拳を勢いよく戻す (予告 20 tick = 1.0 秒の最後に当たる)
    stomp_anim["bones"]["leg_r"] = rot([(0, z), (0.5, stomp["leg_r"]), (0.62, stomp["leg_r"]), (0.82, R(-6, 0, 0)), (1.0, z)])
    stomp_anim["bones"]["right_arm"] = rot([(0, z), (0.5, stomp["right_arm"]), (0.62, stomp["right_arm"]), (0.82, R(-20, 0, 0)), (1.0, z)])
    stomp_anim["bones"]["body"] = rot([(0, z), (0.5, stomp["body"]), (0.62, stomp["body"]), (0.82, R(12, 0, 0)), (1.0, z)])
    return {
        "animation.elder_sentinel.idle": anim(3.0, {
            "body": both([(0, R(0, 0, 0)), (1.5, R(1.5, 0, 0)), (3.0, R(0, 0, 0))], [(0, [0, 0, 0]), (1.5, [0, 0.4, 0]), (3.0, [0, 0, 0])]),
            "head": rot([(0, R(0, 0, 0)), (1.0, R(0, 6, 0)), (2.0, R(0, -6, 0)), (3.0, R(0, 0, 0))]),
            "left_arm": rot([(0, R(0, 0, -2)), (1.5, R(2, 0, -3)), (3.0, R(0, 0, -2))]),
            "right_arm": rot([(0, R(0, 0, 2)), (1.5, R(2, 0, 3)), (3.0, R(0, 0, 2))]),
        }, loop=True),
        "animation.elder_sentinel.walk": {"loop": True, "animation_length": 1.1, "bones": walk},
        # 盾の突進: 構えて (0.4 秒)、構えたまま突っ込む (予告 24 tick = 1.2 秒のうち最後の 8 tick が走り)
        "animation.elder_sentinel.rush": pose_anim(1.2, charge, 0.4),
        "animation.elder_sentinel.flute": pose_anim(1.2, whistle, 0.4),
        "animation.elder_sentinel.stomp": stomp_anim,
        # ふつうの殴り (骨の番兵と同じ。老番兵は拳を重く振る)
        "animation.elder_sentinel.attack": anim(0.7, {
            "right_arm": rot([(0, z), (0.26, R(-85, 0, 8)), (0.44, R(-30, 0, -4)), (0.7, z)]),
            "right_forearm": rot([(0, z), (0.26, R(-25, 0, 0)), (0.44, R(-12, 0, 0)), (0.7, z)]),
            "body": rot([(0, z), (0.26, R(-3, 15, 0)), (0.44, R(8, -20, 0)), (0.7, z)]),
        }),
        # 盾で受ける: 盾の腕がわずかに突き出て戻る
        "animation.elder_sentinel.block": anim(0.5, {
            "left_arm": rot([(0, z), (0.1, R(14, 0, 0)), (0.5, z)]),
            "left_forearm": rot([(0, z), (0.1, R(8, 0, 0)), (0.5, z)]),
            "body": rot([(0, z), (0.1, R(3, 0, 0)), (0.5, z)]),
        }),
    }


def write(name, animations):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / (name + ".animation.json")).write_text(
        json.dumps({"format_version": "1.8.0", "animations": animations}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(name, list(animations))


if __name__ == "__main__":
    write("bone_mortar", mortar())
    write("bone_gull", gull())
    write("elder_sentinel", elder())
