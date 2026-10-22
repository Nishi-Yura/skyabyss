#!/usr/bin/env python3
"""心臓の番人 (HeartWarden, docs/design/36 の 6・6.6) の素材を入れる。案 B (肋の獣) の本番モデル。

  python tools/rough/lowpoly/abyss/warden_model.py     (先に形と絵を作る: docs/review/warden_model/)
  PYTHONIOENCODING=utf-8 python -I tools/gen/world/gen_heart_warden.py

入力: docs/review/warden_model/heart_warden_{mesh.json,atlas.png,glowmask.png}
出力 (assets/skyabyss/ の下):
  lowpoly/heart_warden.json, textures/lowpoly/heart_warden(.png|_glowmask.png), geo/entity/heart_warden_lowpoly(_debug).geo.json   (tools/install/install_lowpoly.py)
  geo/entity/heart_warden.geo.json + textures/entity/heart_warden.png    LowPoly.enabled = false のときの箱の代用 (デバッグの箱を写す)
  animations/entity/heart_warden.animation.json                          idle・walk・claw・lunge・snuff・phase・hurt・death

動きの決まり (ゲームの数字は変えない。HeartWarden の予告と同じ長さ):
  claw   18 tick (扇の予告。腕を後ろへ引き、前へ扇に振る。当たるのは最後)
  lunge  22 tick (踏み込みの予告。体を後ろへ反らし、踏み込みで前へ)
  snuff  60 tick (灯りへ身をかがめ、息を吹きかける。胸が膨らむ)
  phase  40 tick (段が変わる: 肋が開き、頭が上がる。鼓動が速くなる)
  death  24 tick で沈んで溶ける (体が下がって縮み、最後の姿で止める)
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../abyss')]
import json
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_abyss_mobs_b as G  # noqa: E402  (動きの部品: kf・bone・S・loop・once。深淵の敵と同じ約束)

ROOT = os.path.dirname(HERE)
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
MODEL = os.path.join(ROOT, "docs", "review", "warden_model")
NAME = "heart_warden"


def scale(frames):
    """大きさの鍵 (time -> [x, y, z])。回転と違い符号を変えない"""
    return G.kf(frames, False)


def pos(frames):
    return G.kf(frames, False)


def animations():
    p = "animation.%s." % NAME
    # 待機: 胸が息をする (肋の骨の大きさを少し変える)、鼓動 (心臓の骨が打つ)、頭と腕が少し揺れる
    idle = {
        "head": G.bone([G.S(0, 2.5, 60), "0", "0"]),
        "arm_l": G.bone([G.S(0, 3, 60), "0", "0"]),
        "arm_r": G.bone([G.S(180, 3, 60), "0", "0"]),
    }
    idle["ribs"] = {"scale": scale({0: (1, 1, 1), 1.5: (1.03, 1.05, 1.03), 3.0: (1, 1, 1)})}
    idle["heart"] = {"scale": scale({0: (1, 1, 1), 0.12: (1.2, 1.2, 1.2), 0.3: (1, 1, 1), 0.45: (1.14, 1.14, 1.14), 0.75: (1, 1, 1), 1.2: (1, 1, 1)})}
    # 歩き: 脚が交互に動き、胸が前後に揺れる、腕は垂れて揺れる
    walk = G.legs_walk(["leg_l", "leg_r"], 360, 18, 3)
    walk["body"] = G.bone(["0", "0", G.S(0, 2, 360)], [0, "math.abs(%s)*0.4" % G.S(0, 1, 360), 0])
    walk["arm_l"] = G.bone([G.S(0, 14, 360), "0", "0"])
    walk["arm_r"] = G.bone([G.S(180, 14, 360), "0", "0"])
    walk["heart"] = {"scale": scale({0: (1, 1, 1), 0.2: (1.12, 1.12, 1.12), 0.5: (1, 1, 1), 1.0: (1, 1, 1)})}
    # 爪 (18 tick = 0.9 秒): 0〜0.4 腕を後ろへ引く (予告)、0.4〜0.6 前へ扇に振る (当たる)、残りで戻る
    claw = {
        "arm_l": G.bone(G.kf({0: (0, 0, 0), 0.4: (30, 0, -40), 0.6: (-80, 60, 0), 0.75: (-80, -30, 0), 0.9: (0, 0, 0)}),
                        None),
        "arm_r": G.bone(G.kf({0: (0, 0, 0), 0.4: (30, 0, 40), 0.6: (-80, 60, 0), 0.75: (-80, -30, 0), 0.9: (0, 0, 0)}),
                        None),
        "body": G.bone(G.kf({0: (0, 0, 0), 0.4: (10, 0, 0), 0.6: (-14, 0, 0), 0.9: (0, 0, 0)}), None),
    }
    # 踏み込み (22 tick = 1.1 秒): 0〜0.5 体を後ろへ反らして溜める (予告の線が出る)、0.5〜0.8 前へ踏み込む、残りで戻る
    lunge = {
        "body": G.bone(G.kf({0: (0, 0, 0), 0.5: (22, 0, 0), 0.8: (-34, 0, 0), 1.1: (0, 0, 0)}), None),
        "arm_l": G.bone(G.kf({0: (0, 0, 0), 0.5: (20, 0, -20), 0.8: (-90, 0, -10), 1.1: (0, 0, 0)}), None),
        "arm_r": G.bone(G.kf({0: (0, 0, 0), 0.5: (20, 0, 20), 0.8: (-90, 0, 10), 1.1: (0, 0, 0)}), None),
        "head": G.bone(G.kf({0: (0, 0, 0), 0.8: (-18, 0, 0), 1.1: (0, 0, 0)}), None),
    }
    # 吹き消し (60 tick = 3 秒): 0〜0.8 灯りへかがむ、0.8〜2.4 腕を伸ばして息を吹く (胸が膨らむ)、2.4〜3.0 起きる
    snuff = {
        "body": G.bone(G.kf({0: (0, 0, 0), 0.8: (-38, 0, 0), 2.4: (-38, 0, 0), 3.0: (0, 0, 0)}), None),
        "head": G.bone(G.kf({0: (0, 0, 0), 0.8: (-30, 0, 0), 2.4: (-30, 0, 0), 3.0: (0, 0, 0)}), None),
        "arm_r": G.bone(G.kf({0: (0, 0, 0), 0.8: (-100, 0, -10), 2.4: (-100, 0, -10), 3.0: (0, 0, 0)}), None),
        "arm_l": G.bone(G.kf({0: (0, 0, 0), 0.8: (-60, 0, 10), 2.4: (-60, 0, 10), 3.0: (0, 0, 0)}), None),
        "ribs": {"scale": scale({0: (1, 1, 1), 1.6: (1, 1, 1), 2.0: (1.12, 1.12, 1.12), 2.2: (1.08, 1.08, 1.08), 3.0: (1, 1, 1)})},
    }
    # 段の変わり目 (40 tick ≈ 2 秒): 肋が開き、頭が上がり、腕が横へ開く。鼓動が速くなる
    phase = {
        "ribs": {"scale": scale({0: (1, 1, 1), 0.5: (1.14, 1.04, 1.14), 1.4: (1.14, 1.04, 1.14), 2.0: (1, 1, 1)})},
        "head": G.bone(G.kf({0: (0, 0, 0), 0.5: (-24, 0, 0), 1.4: (-24, 0, 0), 2.0: (0, 0, 0)}), None),
        "arm_l": G.bone(G.kf({0: (0, 0, 0), 0.5: (0, 0, 40), 1.4: (0, 0, 40), 2.0: (0, 0, 0)}), None),
        "arm_r": G.bone(G.kf({0: (0, 0, 0), 0.5: (0, 0, -40), 1.4: (0, 0, -40), 2.0: (0, 0, 0)}), None),
        "heart": {"scale": scale({0: (1, 1, 1), 0.3: (1.3, 1.3, 1.3), 0.5: (1, 1, 1), 0.8: (1.3, 1.3, 1.3), 1.0: (1, 1, 1), 1.2: (1.3, 1.3, 1.3), 1.4: (1, 1, 1), 2.0: (1, 1, 1)})},
    }
    # 被弾: 体が少し縮む (小さく跳ねる)
    hurt = {"body": G.bone(G.kf({0: (0, 0, 0), 0.06: (0, 0, 8), 0.22: (0, 0, 0)}), None)}
    # 死: 1.2 秒で沈んで溶ける (体が下がって縮む)。最後の姿で止める (サーバーの死のあとも描く)
    death = {
        "body": {"position": pos({0: (0, 0, 0), 1.2: (0, -34, 0)}),
                 "scale": scale({0: (1, 1, 1), 0.6: (0.8, 0.8, 0.8), 1.2: (0.05, 0.05, 0.05)})},
    }
    return {
        p + "idle": G.loop(3.0, clean(idle)),
        p + "walk": G.loop(1.0, clean(walk)),
        p + "claw": G.once(0.9, clean(claw)),
        p + "lunge": G.once(1.1, clean(lunge)),
        p + "snuff": G.once(3.0, clean(snuff)),
        p + "phase": G.once(2.0, clean(phase)),
        p + "hurt": G.once(0.22, clean(hurt)),
        p + "death": G.once(1.2, clean(death), True),
    }


def clean(bones):
    """空の骨と空の鍵を外す (GeckoLib が読める形にする)"""
    out = {}
    for name, b in bones.items():
        d = {k: v for k, v in b.items() if v}
        if d:
            out[name] = d
    return out


def install():
    mesh = os.path.join(MODEL, NAME + "_mesh.json")
    atlas = os.path.join(MODEL, NAME + "_atlas.png")
    gmask = os.path.join(MODEL, NAME + "_glowmask.png")
    subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), NAME, "--mesh", mesh, "--atlas", atlas,
                           "--glow", gmask], cwd=ROOT)
    tex = os.path.join(A, "textures", "lowpoly")
    ent = os.path.join(A, "textures", "entity")
    os.makedirs(ent, exist_ok=True)
    # 箱の代用 (LowPoly.enabled = false)
    shutil.copyfile(os.path.join(A, "geo", "entity", NAME + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", NAME + ".geo.json"))
    shutil.copyfile(os.path.join(tex, NAME + ".png"), os.path.join(ent, NAME + ".png"))
    anim_path = os.path.join(A, "animations", "entity", NAME + ".animation.json")
    os.makedirs(os.path.dirname(anim_path), exist_ok=True)
    with open(anim_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"format_version": "1.8.0", "animations": animations()}, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    install()
    print("done")
