"""武器を手に持ったときの item モデルの display (向き・位置・大きさ) の表。

プレイヤーが持つ武器 (item モデル、models/item/<id>.json の base.display) の「持ち方」をここで決める。
武器ごとに三人称 (tp) と一人称 (fp) を持つ。値はどれも右手の分 (rotation = [x, y, z] 度, translation, scale)。
左手の分は left_of で作る (エンジンが左手のとき自分で一部を反転するので、そのぶんを見込んだ値)。ground / head / fixed は共通 (バニラの item/handheld と同じ)。

使い方:
  python tools/lib/weapon_display.py            # 表を models/item/*.json の display に書き込む (display だけ。形は触らない)
  python tools/lib/weapon_display.py --check    # 食い違いがあれば一覧して終了コード 1
各生成スクリプト (gen_weapon_models_v2 / gen_ember_items / install_storm_gear / install_tide_gear) は、書き出し後に
`weapon_display.display_for(<id>, <今までの display>)` を通す。

持ち方の型:
  vanilla : バニラの handheld (刃が手から斜め上へ。剣・爪・斧・鎚)
  forward : 刃 (穂先) が腕の向きに前へ伸びる持ち方 (槍・長柄・火かき棒。突く動きが「槍を突く」に見える)
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "src/main/resources/assets/skyabyss/models/item"

HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
COMMON = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def t(rot, tr, s):
    return {"rotation": list(rot), "translation": list(tr), "scale": [s, s, s]}


# ---------------------------------------------------------------- 向きを計算で決める持ち方 (forward / staff / bow)
# メッシュは「y が穂先・刃の向き、x が幅 (弓は弦 -x → 腹 +x)、z が板の厚み」。WeaponItemRenderer が先に z 軸まわりに -45 度 (中心 8,8,8) 傾けるので、
# display の回転は (欲しい向き M) x Rz(+45)。手の座標は、三人称が「腕の構え p (腕を前へ上げた角度) で決まる前・上・横」、一人称が「視点 (前 = -z、上 = +y)」。
#   前 F = (0, cos p, -sin p)、上 U = (0, sin p, cos p)、横 L = F x U = (+x)        (三人称の手の座標。p = 0 のとき腕は下ろしたまま)
# 握る点 (メッシュの座標 gx, gy) が手の位置 v_g に来るように移動を決める。左手は書く値の rotation の y・z だけ符号を反転し、移動はそのまま (エンジンが反転して同じ向きになる)。
import numpy as np
from scipy.spatial.transform import Rotation as Rot


def _unit(v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)


def _axes(p_deg):
    p = np.radians(p_deg)
    return _unit((0, np.cos(p), -np.sin(p))), _unit((0, np.sin(p), np.cos(p)))


def _display(mx, my, grip, s, v_g):
    """メッシュの x 軸・y 軸が手の座標のどの向きへ行くか (mx, my) から、display (rotation, translation, scale) を出す。"""
    mx, my = _unit(mx), _unit(my)
    M = np.stack([mx, my, np.cross(mx, my)], axis=1)
    rd = M @ Rot.from_euler("z", 45, degrees=True).as_matrix()
    rot = Rot.from_matrix(rd).as_euler("XYZ", degrees=True)
    tr = np.array(v_g, float) - s * (M @ np.array([grip[0], grip[1] - 8.0, 0.0]))
    r = [round(float(x), 1) for x in rot]
    r = [0.0 if abs(x) < 0.05 else x for x in r]
    return {"rotation": r, "translation": [round(float(x), 2) for x in tr], "scale": [s, s, s]}


def forward_hold(ctx, p, s, grip_y, v_g, pitch=0.0):
    """槍・鉾・銛・鉤槍・火かき棒: 穂先が前 (pitch 度だけ上) へ、手は柄の grip_y のところ。幅は上下 (板の面は横を向く)。"""
    if ctx == "tp":
        F, U = _axes(p)
    else:
        F, U = _unit((0, 0, -1)), _unit((0, 1, 0))
    c, sn = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))
    F2, U2 = F * c + U * sn, U * c - F * sn
    return _display(-U2, F2, (0, grip_y), s, v_g)


def staff_hold(ctx, p, s, grip_y, v_g, lean=0.0):
    """杖: 頭を上に立てて持つ (lean 度だけ前へ倒す)。幅 (頭の横の広がり) は体の左右。"""
    if ctx == "tp":
        F, U = _axes(p)
    else:
        F, U = _unit((0, 0, -1)), _unit((0, 1, 0))
    L = np.cross(F, U)
    c, sn = np.cos(np.radians(lean)), np.sin(np.radians(lean))
    return _display(L, U * c + F * sn, (0, grip_y), s, v_g)


def bow_hold(ctx, p, s, grip, v_g, yaw=0.0):
    """弓: 縦に立てて、弦が手前 (射手の側)・腹が前。yaw 度だけ腹を内側 (左) へ向ける (一人称で弓の横姿が見えるように)。"""
    if ctx == "tp":
        F, U = _axes(p)
    else:
        F, U = _unit((0, 0, -1)), _unit((0, 1, 0))
    L = np.cross(F, U)
    c, sn = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    return _display(F * c - L * sn, U, grip, s, v_g)


# 槍は休んでいるとき、穂先を水平から TP_PITCH / FP_PITCH 度だけ起こす (縦から 40 度ほど。横すぎたので起こした)
TP_PITCH, FP_PITCH = 50.0, 50.0


def _fwd(p, tps, fps, gy, pitch=FP_PITCH, tp_pitch=TP_PITCH):
    return (forward_hold("tp", p, tps, gy, (0, 0, 1.0), tp_pitch), forward_hold("fp", 0, fps, gy, (1.2, -0.8, 0.0), pitch))


BOW_GRIP = (5.0, 8.2)

# 武器ごとの持ち方: (三人称, 一人称)。右手の値
HELD = {
    # 剣・爪・斧・鎚: バニラの向きのまま、大きさと位置だけ合わせる (全体に 15〜25% 大きく。短剣・爪は 35% 大きく)
    "whalebone_greatsword": (t((0, -90, 55), (0, 4.5, 0.5), 1.7), t((0, -90, 25), (0.5, 4.6, 1.13), 0.7)),
    "waterway_fang": (t((0, -90, 55), (0, 4.0, 0.5), 1.05), t((0, -90, 25), (1.13, 3.2, 1.13), 0.57)),
    "raiju_claw": (t((0, -90, 55), (0, 4.0, 0.5), 1.05), t((0, -90, 25), (1.13, 3.2, 1.13), 0.57)),
    "tidecaller_claw": (t((0, -90, 55), (0, 4.0, 0.5), 1.05), t((0, -90, 25), (1.13, 9.5, 1.13), 0.65)),
    "ember_war_axe": (t((0, -90, 55), (0, 4.0, 0.5), 1.3), t((0, -90, 25), (0.5, 4.6, 1.13), 0.68)),
    "smith_hammer": (t((0, -90, 55), (0, 4.0, 0.5), 1.2), t((0, -90, 25), (0.5, 4.6, 1.13), 0.52)),
    # 槍・長柄・火かき棒: 穂先が前へ (手は柄の下から 1/4〜1/3 のところ)
    "captain_pike": _fwd(66, 1.5, 1.05, 5),
    "tide_shell_trident": _fwd(66, 1.5, 1.05, 5),
    "coral_harpoon": _fwd(66, 1.5, 1.05, 5),
    "warden_glaive": _fwd(66, 1.45, 1.05, 5),
    "thrall_poker": _fwd(57, 1.3, 0.95, 4),
    # 深淵の専用伝説: 大剣は鯨骨の大剣、鎌は戦斧と同じ持ち方。杖は頭を上にして柄の 1/3 を握る
    "abyss_greatsword": (t((0, -90, 55), (0, 4.5, 0.5), 1.7), t((0, -90, 25), (0.5, 4.6, 1.13), 0.7)),
    "abyss_ash_scythe": (t((0, -90, 55), (0, 4.0, 0.5), 1.3), t((0, -90, 25), (0.5, 4.6, 1.13), 0.68)),
    "abyss_bell_staff": (staff_hold("tp", 57, 1.3, 4.5, (0, 0, 1.0)), staff_hold("fp", 0, 0.72, 4.5, (1.0, -1.2, 0.0), 8)),
    # 五章「深淵」の装備 (abyss/gear、v2): 灯り番の剣は片手の直剣 (握り y -1.6..3.1)、沈め錨は両手の大鎚と同じ持ち方 (握り y -2.6..4.6)、
    # 鼓動の短剣は水路の牙と同じ (握り y -1.2..2.4)。手はメッシュの y = 8 - 7.8 / 倍率 あたりを握る
    "abyss_lamp_sword": (t((0, -90, 55), (0, 4.2, 0.5), 1.12), t((0, -90, 25), (1.13, 3.2, 1.13), 0.46)),
    "abyss_sinking_anchor": (t((0, -90, 55), (0, 4.0, 0.5), 1.2), t((0, -90, 25), (0.8, 4.0, 1.13), 0.42)),
    "abyss_pulse_dagger": (t((0, -90, 55), (0, 4.0, 0.5), 1.1), t((0, -90, 25), (1.13, 3.2, 1.13), 0.5)),
}
# 弓 (引き絵 3 枚も同じ持ち方。引くときは腕を前へまっすぐ上げる)。弦が射手の側、腹が前、縦に立てる
for _bow in ("thunder_wing_bow", "abyss_storm_bow"):
    HELD[_bow] = (bow_hold("tp", 18, 1.2, BOW_GRIP, (0, 0, 1.0)), bow_hold("fp", 0, 0.7, BOW_GRIP, (0.2, -0.2, 0.0), 30))
    for _n in range(3):
        HELD[f"{_bow}_pulling_{_n}"] = (bow_hold("tp", 90, 1.2, BOW_GRIP, (0, 0, 1.0)), bow_hold("fp", 0, 0.7, BOW_GRIP, (-0.4, -0.2, 0.0), 20))


SOLVED = {"captain_pike", "tide_shell_trident", "coral_harpoon", "warden_glaive", "thrall_poker", "abyss_bell_staff", "thunder_wing_bow", "abyss_storm_bow",
          *(f"{b}_pulling_{n}" for b in ("thunder_wing_bow", "abyss_storm_bow") for n in range(3))}


def left_of(d, item=None):
    """左手の分。エンジン (ItemTransform.apply) は左手のとき、回転の y・z と移動の x の符号を自分で反転する。
    反転後の姿が「右手の姿を体の中心で鏡に映したもの」になるように、書く値を決める:
    鏡に映した武器は、刃の面 (斜めの対称面) についても映したものと同じ形なので、モデルを z 軸まわりに -90 度回した値になる。
    つまり左手は rotation の z だけ -90 する (バニラの [0,-90,55] / [0,90,-55] のように対にしない)。"""
    m = copy.deepcopy(d)
    if item in SOLVED:
        m["rotation"] = [d["rotation"][0], -d["rotation"][1], -d["rotation"][2]]
        return m
    m["rotation"] = [d["rotation"][0], d["rotation"][1], d["rotation"][2] - 90]
    return m


def display_for(item: str, current: dict) -> dict:
    """item の display。表にない武器 (弓・盾など) は今のまま。表にあるものは tp/fp を差し替え、そのほかのキーは今のものを残す。"""
    if item not in HELD:
        return current
    tp, fp = HELD[item]
    out = copy.deepcopy(current)
    if tp is not None:
        out["thirdperson_righthand"] = copy.deepcopy(tp)
    if fp is not None:
        out["firstperson_righthand"] = copy.deepcopy(fp)
    out["thirdperson_lefthand"] = left_of(out["thirdperson_righthand"], item)
    out["firstperson_lefthand"] = left_of(out["firstperson_righthand"], item)
    for k, v in COMMON.items():
        out.setdefault(k, copy.deepcopy(v))
    return out


# 盾 (coral_shield = ふだん、coral_shield_blocking = 構えている間)。一人称だけ作り直す (大きさ・位置)。左手は x の移動を逆にしただけ
SHIELD_FP = {
    # 右手 (主の手) と左手 (副の手) は画面の端からの位置が違うので別々に決める (x の移動は大きいほど右手は左へ・左手は左へ)
    "coral_shield": {"r": ([0, 180, 5], [5, -2, -10], 0.95), "l": ([0, 180, 5], [8, -1, -10], 0.95)},
    "coral_shield_blocking": {"r": ([0, 180, -5], [-19, 3, -11], 0.95), "l": ([0, 180, -5], [6, 3, -11], 0.95)},
}


TP_BLOCK = ([45, -45, 0], [3.51, 8, -2])  # 構えの三人称: 盾の表 (珊瑚の扇) を前へ向け、頭ではなく胸の高さへ


def shield_display(name, current):
    out = copy.deepcopy(current)
    for hand, key in (("r", "firstperson_righthand"), ("l", "firstperson_lefthand")):
        rot, tr, sc = SHIELD_FP[name][hand]
        out[key] = {"rotation": rot, "translation": tr, "scale": [sc] * 3}
    if name == "coral_shield_blocking":
        # 三人称の構え: 左手 (副の手) は右手と同じ数 (エンジンが左手のとき回転 y・z と移動 x を反転して、体の中心で鏡に映した姿になる)
        out["thirdperson_righthand"] = {"rotation": TP_BLOCK[0], "translation": TP_BLOCK[1], "scale": [0.85, 0.85, 0.85]}
        out["thirdperson_lefthand"] = copy.deepcopy(out["thirdperson_righthand"])
    return out


def main():
    check = "--check" in sys.argv
    bad = []
    for name in SHIELD_FP:
        path = MODELS / f"{name}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        new = shield_display(name, data["base"]["display"])
        if new != data["base"]["display"]:
            bad.append(name)
            if not check:
                data["base"]["display"] = new
                path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    for item in HELD:
        path = MODELS / f"{item}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        new = display_for(item, data["base"]["display"])
        if new != data["base"]["display"]:
            bad.append(item)
            if not check:
                data["base"]["display"] = new
                path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(("differs: " if check else "updated: ") + ", ".join(bad) if bad else "ok (nothing to change)")
    if check and bad:
        sys.exit(1)


if __name__ == "__main__":
    main()
