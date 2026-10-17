"""中ボス「潮招き」(tide_beckoner) をゲームの素材として書き出す (GeckoLib の geo、テクスチャ、アニメーション)。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python tools/install_tide_caller.py [--option a|b|c]
        既定の案は RECOMMENDED (ラフの 3 案から選んだもの)。
形の元: tools/rough/tide_caller.py の build(案)。箱の組み方と座標の決まりはそこに書いてある。
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/tide_beckoner.geo.json
  textures/entity/tide_beckoner.png、textures/entity/tide_beckoner_glowmask.png
  animations/entity/tide_beckoner.animation.json
確認用 (git には入れない): docs/review/previews/tide_beckoner/*.png

ゲームでの向き (岩鯨・潮殻と同じ決まり): 形は前が +Z で作り、書き出す geo の root を yaw 180 度回して前を -Z にする。
大きいはさみは -X 側 (ゲームの右)。角度は意図した向き (ラフの絵で見える向き) で ANIM に書き、
ファイルには install_tide_mobs.SIGN = (-1, 1, -1) を掛けて書く (GeckoLib が X と Z を反転して読むため)。
位置 (position) は符号を掛けない。

アニメの長さ (秒) は Java 側が攻撃の時間に使うので変えない:
  idle 3.0 (loop)、walk 1.2 (loop)、slam_raise 1.0 (hold)、slam_strike 0.25 (hold)、slam_stuck 1.5 (loop)、
  slam_pull 0.4、beckon 1.5、breath 0.8、burrow 1.0 (hold)、emerge 0.8、roar 1.5、hurt 0.3、death 2.0 (hold)
"""
from __future__ import annotations

import argparse
import copy
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "rough"))
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402
import tide_caller as tc  # noqa: E402
import install_tide_mobs as itm  # noqa: E402

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/tide_beckoner"
NAME = "tide_beckoner"
RECOMMENDED = "a"
SIGN = itm.SIGN


def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


# 大きなはさみの決めた姿勢 (意図した角度)。tools/rough/tide_caller.py の支点 (肩) を中心に回す。
# 腕の長さは固定なので、指先の位置は姿勢で決まる (RAISE の指先は肩から 88 単位の所、約 48 単位の高さ)。
CLAW_RAISE = (-25, -150, -10)     # 後ろの上へ振り上げる (指先 約 (-42, 48, -80))
CLAW_STRIKE = (5, 5, -10)         # 前の床へ打ち下ろす (指先 約 (-40, 3, 86))
CLAW_BECKON = (-30, -20, 5)       # 外へ広げ、手招く (指先 約 (-70, 47, 75))
PINCER_OPEN = -30                 # 動く指を開く (意図した x。閉じるのは正の方向)
PINCER_SHUT = 10

ANIM = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        # 息をするように上下し、目柄がぴくつき、小さいはさみが指を鳴らす
        "body": {"position": ["0", sn(0.6, 120), "0"]},
        "eyes": {"rotation": [sn(3, 240), sn(6, 120, 30), sn(4, 240, 60)]},
        "claw_small": {"rotation": [f"-6 + {sn(8, 240)}", sn(4, 120), "0"]},
        "claw_big": {"rotation": [sn(2, 120), sn(3, 120, 90), "0"]},
        "claw_big_pincer": {"rotation": [f"-8 + {sn(4, 240)}", "0", "0"]},
    }},
    "walk": {"loop": True, "len": 1.2, "bones": {
        # 横へ、脚を交互に出す (右の 1 番と左の 2 番が同じ位相)
        "leg_r1": {"rotation": ["0", sn(12, 300), "0"]},
        "leg_r2": {"rotation": ["0", sn(12, 300, 120), "0"]},
        "leg_r3": {"rotation": ["0", sn(12, 300, 240), "0"]},
        "leg_l1": {"rotation": ["0", sn(12, 300, 180), "0"]},
        "leg_l2": {"rotation": ["0", sn(12, 300, 300), "0"]},
        "leg_l3": {"rotation": ["0", sn(12, 300, 60), "0"]},
        "body": {"position": ["0", "0.5 * math.abs(math.sin(300 * query.anim_time))", "0"], "rotation": ["0", sn(2, 300), "0"]},
        "claw_big": {"rotation": ["-4", "0", "0"]},
    }},
    "slam_raise": {"loop": "hold", "len": 1.0, "bones": {
        "claw_big": {"rotation": {0: (0, 0, 0), 0.5: (-10, -60, -5), 1.0: CLAW_RAISE}},
        "claw_big_pincer": {"rotation": {0: (0, 0, 0), 1.0: (PINCER_OPEN, 0, 0)}},
        "body": {"rotation": {0: (0, 0, 0), 1.0: (-6, 0, 0)}},
    }},
    "slam_strike": {"loop": "hold", "len": 0.25, "bones": {
        "claw_big": {"rotation": {0: CLAW_RAISE, 0.12: (-10, -60, -10), 0.25: CLAW_STRIKE}},
        "claw_big_pincer": {"rotation": {0: (PINCER_OPEN, 0, 0), 0.25: (PINCER_SHUT, 0, 0)}},
        "body": {"rotation": {0: (-6, 0, 0), 0.25: (8, 0, 0)},
                 "position": {0: (0, 0, 0), 0.25: (0, 0, 2)}},
    }},
    "slam_stuck": {"loop": True, "len": 1.5, "bones": {
        # 砂に刺さったまま、体が引く (1.5 秒で 1 周期)
        "claw_big": {"rotation": [f"{CLAW_STRIKE[0]} + {sn(2, 240)}", str(CLAW_STRIKE[1]), str(CLAW_STRIKE[2])]},
        "body": {"rotation": [f"8 + {sn(2, 240)}", "0", "0"], "position": ["0", "0", sn(0.6, 240)]},
    }},
    "slam_pull": {"loop": False, "len": 0.4, "bones": {
        "claw_big": {"rotation": {0: CLAW_STRIKE, 0.2: (-5, -20, -8), 0.4: (0, 0, 0)}},
        "claw_big_pincer": {"rotation": {0: (PINCER_SHUT, 0, 0), 0.4: (0, 0, 0)}},
        "body": {"rotation": {0: (8, 0, 0), 0.4: (0, 0, 0)},
                 "position": {0: (0, 0, 2), 0.4: (0, 0, 0)}},
    }},
    "beckon": {"loop": False, "len": 1.5, "bones": {
        # 広げて、手首を 2 度たぐる (指の先が内へ曲がる)
        "claw_big": {"rotation": {0: (0, 0, 0), 0.3: CLAW_BECKON,
                                  0.45: (CLAW_BECKON[0] - 25, CLAW_BECKON[1], CLAW_BECKON[2]),
                                  0.6: CLAW_BECKON,
                                  0.75: (CLAW_BECKON[0] - 25, CLAW_BECKON[1], CLAW_BECKON[2]),
                                  0.9: CLAW_BECKON,
                                  1.2: (0, 0, 0), 1.5: (0, 0, 0)}},
        "claw_big_pincer": {"rotation": {0: (0, 0, 0), 0.3: (PINCER_OPEN, 0, 0), 0.45: (PINCER_SHUT, 0, 0),
                                         0.6: (PINCER_OPEN, 0, 0), 0.75: (PINCER_SHUT, 0, 0),
                                         0.9: (PINCER_OPEN, 0, 0), 1.2: (0, 0, 0), 1.5: (0, 0, 0)}},
    }},
    "breath": {"loop": False, "len": 0.8, "bones": {
        # 後ろへ反らせ、口を前へ突き出して泡を吐く
        "body": {"rotation": {0: (0, 0, 0), 0.3: (-12, 0, 0), 0.55: (8, 0, 0), 0.8: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.3: (0, 0, -2), 0.55: (0, 0, 3), 0.8: (0, 0, 0)}},
        "eyes": {"rotation": {0: (0, 0, 0), 0.3: (-10, 0, 0), 0.8: (0, 0, 0)}},
    }},
    "burrow": {"loop": "hold", "len": 1.0, "bones": {
        # 砂へ沈む (3 ブロック = 48 単位。終わりは床の下)
        "body": {"position": {0: (0, 0, 0), 0.5: (0, -26, 0), 1.0: (0, -48, 0)},
                 "rotation": {0: (0, 0, 0), 0.5: (6, 0, 0), 1.0: (12, 0, 0)}},
        "claw_big": {"rotation": {0: (0, 0, 0), 0.6: (-20, -30, -20), 1.0: (-40, -30, -35)}},
    }},
    "emerge": {"loop": False, "len": 0.8, "bones": {
        "body": {"position": {0: (0, -48, 0), 0.5: (0, -12, 0), 0.8: (0, 0, 0)},
                 "rotation": {0: (12, 0, 0), 0.8: (0, 0, 0)}},
        "claw_big": {"rotation": {0: (-40, -30, -35), 0.8: (0, 0, 0)}},
    }},
    "roar": {"loop": False, "len": 1.5, "bones": {
        # 前の脚を踏み鳴らす (2 回)、はさみを上げる、体を反らす
        "leg_r1": {"rotation": {0: (0, 0, 0), 0.3: (0, 0, -25), 0.5: (0, 0, 0), 0.9: (0, 0, -25), 1.1: (0, 0, 0)}},
        "leg_l1": {"rotation": {0: (0, 0, 0), 0.3: (0, 0, 25), 0.5: (0, 0, 0), 0.9: (0, 0, 25), 1.1: (0, 0, 0)}},
        "claw_big": {"rotation": {0: (0, 0, 0), 0.4: (-12, -70, -5), 1.1: (-12, -70, -5), 1.5: (0, 0, 0)}},
        "body": {"rotation": {0: (0, 0, 0), 0.4: (-8, 0, 0), 0.5: (-6, 0, 0), 1.5: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.5: (0, -1, 0), 0.9: (0, -1, 0), 1.5: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.08: (-6, 0, 3), 0.3: (0, 0, 0)}},
        "eyes": {"rotation": {0: (0, 0, 0), 0.08: (-12, 0, 0), 0.3: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 2.0, "bones": {
        # 前へ崩れ、はさみが落ち、目柄が垂れる
        "body": {"rotation": {0: (0, 0, 0), 0.6: (20, 0, 12), 2.0: (24, 0, 14)},
                 "position": {0: (0, 0, 0), 0.6: (0, -6, 0), 2.0: (0, -8, 0)}},
        "claw_big": {"rotation": {0: (0, 0, 0), 0.8: (0, 0, 40), 2.0: (0, 0, 55)}},
        "eyes": {"rotation": {0: (0, 0, 0), 2.0: (30, 0, 0)}},
        "leg_r1": {"rotation": {0: (0, 0, 0), 2.0: (0, 0, -30)}},
        "leg_l1": {"rotation": {0: (0, 0, 0), 2.0: (0, 0, 30)}},
    }},
}


def anim_file():
    """アニメを書き出す (符号は install_tide_mobs.anim_json が掛ける)。"""
    itm.write_anim(NAME, ANIM)


def geo_result(opt):
    m, info = tc.build(opt)
    geo, tex, gl = m.finish()
    over = vx.check_overlaps(geo)
    assert not over, over[:5]
    assert gl[..., 3].any(), "光る画素がない"
    r = dict(game=tm.to_game(copy.deepcopy(geo)), view=tm.to_game(copy.deepcopy(geo)), tex=tex, gl=gl, n=info["n"])
    return r, info


def previews(r):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    faces = itm.faces_of
    rows = [
        ("正面3/4 (待機)", tm.game_render(faces(r), 205, 18)),
        ("横 (待機)", tm.game_render(faces(r), 270, 8)),
        ("振り上げ (raise 終わり)", tm.game_render(faces(r, pose=itm.pose_at(ANIM, "slam_raise", 1.0)), 205, 18)),
        ("打ち下ろし (strike 終わり)", tm.game_render(faces(r, pose=itm.pose_at(ANIM, "slam_strike", 0.25)), 205, 18)),
        ("招き (beckon 0.45 秒)", tm.game_render(faces(r, pose=itm.pose_at(ANIM, "beckon", 0.45)), 205, 18)),
        ("咆哮 (roar 0.4 秒)", tm.game_render(faces(r, pose=itm.pose_at(ANIM, "roar", 0.4)), 205, 18)),
        ("死 (death 終わり)", tm.game_render(faces(r, pose=itm.pose_at(ANIM, "death", 2.0)), 205, 18)),
    ]
    cols = [(name, [img]) for name, img in rows]
    vx.sheet("潮招き (取り込み後の確認)", cols,
             note="ゲームの向き (X の鏡写し) で描いた。角度は意図した向き (SIGN を掛ける前)。").save(PREVIEW / "tide_beckoner.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", choices=tc.OPTIONS, default=RECOMMENDED)
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()

    r, info = geo_result(args.option)
    geo = copy.deepcopy(r["game"])
    itm.write_model(NAME, r)
    anim_file()
    print(f"tide_beckoner ({args.option}): 箱 {info['n']}、範囲 lo {info['lo']} hi {info['hi']}")
    if not args.no_preview:
        previews(r)


if __name__ == "__main__":
    main()
