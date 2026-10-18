"""武器を手に持ったときの item モデルの display (向き・位置・大きさ) の表。

プレイヤーが持つ武器 (item モデル、models/item/<id>.json の base.display) の「持ち方」をここで決める。
武器ごとに三人称 (tp) と一人称 (fp) を持つ。値はどれも右手の分 (rotation = [x, y, z] 度, translation, scale)。
左手の分は left_of で作る (エンジンが左手のとき自分で一部を反転するので、そのぶんを見込んだ値)。ground / head / fixed は共通 (バニラの item/handheld と同じ)。

使い方:
  python tools/weapon_display.py            # 表を models/item/*.json の display に書き込む (display だけ。形は触らない)
  python tools/weapon_display.py --check    # 食い違いがあれば一覧して終了コード 1
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

ROOT = Path(__file__).resolve().parents[1]
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


# 武器ごとの持ち方: (三人称, 一人称)。右手の値
HELD = {
    # 剣・爪・斧・鎚: バニラの向きのまま、大きさと位置だけ合わせる
    "whalebone_greatsword": (t((0, -90, 55), (0, 4.5, 0.5), 1.2), t((0, -90, 25), (0.5, 4.6, 1.13), 0.7)),
    "waterway_fang": (t((0, -90, 55), (0, 4.0, 0.5), 0.85), t((0, -90, 25), (1.13, 3.2, 1.13), 0.68)),
    "raiju_claw": (t((0, -90, 55), (0, 4.0, 0.5), 0.85), t((0, -90, 25), (1.13, 3.2, 1.13), 0.68)),
    "tidecaller_claw": (t((0, -90, 55), (0, 4.0, 0.5), 0.85), t((0, -90, 25), (1.13, 3.2, 1.13), 0.68)),
    "ember_war_axe": (t((0, -90, 55), (0, 4.0, 0.5), 1.0), t((0, -90, 25), (0.5, 4.6, 1.13), 0.66)),
    "smith_hammer": (t((0, -90, 55), (0, 4.0, 0.5), 0.9), t((0, -90, 25), (0.5, 4.6, 1.13), 0.5)),
    # 槍・長柄・火かき棒: 穂先が前へ
    "captain_pike": (t((0, 90, -45), (0, 0, -3), 1.3), t((20, 90, -45), (0, 0, -5), 0.9)),
    "tide_shell_trident": (t((0, 90, -45), (0, 0, -3), 1.3), t((20, 90, -45), (0, 0, -5), 0.9)),
    "coral_harpoon": (t((0, 90, -45), (0, 0, -3), 1.3), t((20, 90, -45), (0, 0, -5), 0.9)),
    "warden_glaive": (t((0, 90, -45), (0, 0, -3), 1.25), t((20, 90, -45), (0, 0, -5), 0.9)),
    "thrall_poker": (t((0, 90, -45), (0, 0, -3), 1.1), t((20, 90, -45), (0, 0, -5), 0.8)),
}
# 雷翼の弓 (引き絵 3 枚も同じ持ち方)。三人称は今のまま (None)、一人称だけ小さく左へ寄せて、弓全体が見えるようにする
for _bow in ("thunder_wing_bow", "thunder_wing_bow_pulling_0", "thunder_wing_bow_pulling_1", "thunder_wing_bow_pulling_2"):
    HELD[_bow] = (None, t((0, -90, 25), (-1.0, 3.5, 1.13), 0.55))


def left_of(d):
    """左手の分。エンジン (ItemTransform.apply) は左手のとき、回転の y・z と移動の x の符号を自分で反転する。
    反転後の姿が「右手の姿を体の中心で鏡に映したもの」になるように、書く値を決める:
    鏡に映した武器は、刃の面 (斜めの対称面) についても映したものと同じ形なので、モデルを z 軸まわりに -90 度回した値になる。
    つまり左手は rotation の z だけ -90 する (バニラの [0,-90,55] / [0,90,-55] のように対にしない)。"""
    m = copy.deepcopy(d)
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
    out["thirdperson_lefthand"] = left_of(out["thirdperson_righthand"])
    out["firstperson_lefthand"] = left_of(out["firstperson_righthand"])
    for k, v in COMMON.items():
        out.setdefault(k, copy.deepcopy(v))
    return out


def main():
    check = "--check" in sys.argv
    bad = []
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
