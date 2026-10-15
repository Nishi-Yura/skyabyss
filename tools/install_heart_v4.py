"""岩鯨の心臓 (最終ボス) の第4回ラフ 案A をゲームに入れる。

使い方 (リポジトリ直下で): python tools/install_heart_v4.py
形の元: docs/roughs/v4/boss/a/phaseN.geo.json, phaseN.tex.png, phaseN.glow.png (tools/rough/boss_v4.py が作る)
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/stone_whale_heart_p1|p2|p3.geo.json
  textures/entity/stone_whale_heart_p1|p2|p3.png と _glowmask.png
  animations/entity/stone_whale_heart.animation.json
確認用: docs/roughs/v5/boss.png (GeckoLib の読み方を真似て描いた絵と、ラフの絵を並べる)

向きの変換 (GeckoLib 4.9.3 のバイトコードで確かめた読み方):
- 箱と pivot の X は反転して焼かれる (x → -(x + 幅))。骨とキーフレームの回転は X と Y の符号が反転する。
  位置のキーフレームも X が反転する。回転の順は Z → Y → X (行列 Rz Ry Rx)。
- 焼いた後の空間 (Blockbench の空間) で、体の正面は -Z (GeoEntityRenderer が 180 - yaw 回す)。
- ラフ (voxel.py の描き方) は geo をそのままの右手系で描き、正面は +Z (心核・肺動脈が +Z)。
- ゲームの見た目をラフと同じ (鏡に映さず、正面を前) にするには、焼いた後の形がラフを Y 軸で 180 度
  回したものになればよい。そのための geo は「ラフの Z を反転したもの」:
    箱の origin z → -(z + 奥行き)、pivot z → -z、骨の回転 (rx, ry, rz) → (rx, -ry, -rz)、
    面の uv は north と south、east と west を入れ替える。up はそのまま。down は画素の上下を反転する
    (GeckoLib の down 面は v0 が北、ラフの down 面を 180 度回すと v0 が南になるため)。
- アニメーションもラフの空間 (正面 +Z) で書き、同じ変換 (位置 z と回転 y・z の符号) をかけて出力する。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import FACE_SHADE, Face, load_geo, render, sheet  # noqa: E402

SRC = ROOT / "docs/roughs/v4/boss/a"
ASSET = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/roughs/v5/boss.png"
NAME = "stone_whale_heart"
CY = 12.0

# ---------------------------------------------------------------- geo の変換

SWAP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}


def convert_geo(geo, tex, glow):
    """ラフの geo (正面 +Z) を、GeckoLib で同じ見た目になる geo に変える。tex と glow は down 面を反転して返す"""
    geo = copy.deepcopy(geo)
    tex = tex.copy()
    glow = glow.copy()
    g = geo["minecraft:geometry"][0]
    rects = []
    for b in g["bones"]:
        if "pivot" in b:
            p = b["pivot"]
            b["pivot"] = [p[0], p[1], -p[2] + 0.0]
        if "rotation" in b:
            r = b["rotation"]
            b["rotation"] = [r[0], -r[1] + 0.0, -r[2] + 0.0]
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            c["origin"] = [o[0], o[1], -(o[2] + s[2]) + 0.0]
            assert "rotation" not in c
            uv = {SWAP[k]: v for k, v in c["uv"].items()}
            c["uv"] = {k: uv[k] for k in ("north", "east", "south", "west", "up", "down") if k in uv}
            for k, f in c["uv"].items():
                u, v = f["uv"]
                w, h = f["uv_size"]
                assert w > 0 and h > 0
                rects.append((u, v, w, h))
                if k == "down":
                    tex[v:v + h, u:u + w] = tex[v:v + h, u:u + w][::-1]
                    glow[v:v + h, u:u + w] = glow[v:v + h, u:u + w][::-1]
    # 面ごとの画素の範囲が重ならないこと (down の反転が他の面に及ばない)
    used = np.zeros(tex.shape[:2], int)
    for u, v, w, h in rects:
        used[v:v + h, u:u + w] += 1
    assert used.max() <= 1, "uv の範囲が重なっている"
    return geo, tex, glow


def ensure_bones(geo, names):
    """アニメーションが使う骨のうち、この段階にない骨を、立方体のない骨として足す"""
    g = geo["minecraft:geometry"][0]
    have = {b["name"] for b in g["bones"]}
    added = []
    for n in names:
        if n not in have:
            g["bones"].append({"name": n, "pivot": [0, CY, 0], "cubes": []})
            added.append(n)
    return added


# ---------------------------------------------------------------- アニメーション (ラフの空間で書く)

def phi(i):
    """殻の板 i の向き (ラフの空間、x = cos, z = sin。正面 +Z は 90 度)"""
    return math.radians(22.5 + 45 * i)


def out_dir(i):
    a = phi(i)
    return np.array([math.cos(a), 0.0, math.sin(a)])


def rx_vec(a_deg, v):
    a = math.radians(a_deg)
    y = v[1] * math.cos(a) - v[2] * math.sin(a)
    z = v[1] * math.sin(a) + v[2] * math.cos(a)
    return np.array([v[0], y, z])


HINGE = np.array([0.0, -9.0, 9.0])     # 板の下の縁 (板の座標、pivot から)


def hinge_pos(i, a_deg, target=None):
    """板 i を下の縁を軸に外へ a 度倒す時の、位置のキーフレーム (ラフの空間)。
    target は倒した後の下の縁の位置 (板の座標)。省略すると下の縁は動かない"""
    tgt = HINGE if target is None else np.array(target, float)
    t = tgt - rx_vec(a_deg, HINGE)
    return (t[1] * np.array([0, 1.0, 0]) + t[2] * out_dir(i)).tolist()


def yaw_about_center(b_deg):
    """y 軸まわりの回転 (ラフの空間)。正の b で板の向きの角度が b だけ減る"""
    return [0, b_deg, 0]


def to_game_rot(r):
    return [r[0], -r[1] + 0.0, -r[2] + 0.0]


def to_game_pos(p):
    return [p[0], p[1], -p[2] + 0.0]


def track(keys, kind):
    out = {}
    for t, v in keys:
        v = [float(x) for x in v]
        if kind == "rotation":
            v = to_game_rot(v)
        elif kind == "position":
            v = to_game_pos(v)
        out[f"{t:.2f}"] = [round(x, 3) + 0.0 for x in v]
    return out


def S(s):
    return [s, s, s]


Z3 = [0, 0, 0]
ONE = [1, 1, 1]


def beat(t0, peak1, peak2, length):
    """鼓動の大きさの列 (2 拍)"""
    return [(0, ONE), (t0 + 0.2, S(peak1)), (t0 + 0.45, ONE), (t0 + 0.6, S(peak2)), (t0 + 1.0, ONE), (length, ONE)]


def fall_keys(i, t0, t1, t_end, sink=6.0, wobble=None):
    """板 i が下の縁を軸に外へ倒れ (t0 → t1)、床に寝て、t_end までに床へ沈む"""
    land = [0.0, -11.0, 14.0]     # 下の縁が床の近くに落ちる (pivot から y -11 = 床の上 1)
    pos, rot = [], []
    steps = [0, 30, 60, 90]
    pos.append((0, Z3))
    rot.append((0, Z3))
    if wobble:
        for t, z in wobble:
            rot.append((t, [0, 0, z]))
            pos.append((t, Z3))
    pos.append((t0, Z3))
    rot.append((t0, Z3))
    for k, a in enumerate(steps[1:], 1):
        f = k / 3
        t = t0 + (t1 - t0) * math.sqrt(f)   # だんだん速く倒れる
        tgt = HINGE + (np.array(land) - HINGE) * f
        pos.append((t, hinge_pos(i, a, tgt)))
        rot.append((t, [a, 0, 0]))
    p_land = hinge_pos(i, 90, land)
    pos.append((t1 + 0.12, [p_land[0], p_land[1] + 0.8, p_land[2]]))
    pos.append((t1 + 0.24, p_land))
    rot.append((t1 + 0.12, [86, 0, 0]))
    rot.append((t1 + 0.24, [90, 0, 0]))
    pos.append((t_end - 0.5, p_land))
    pos.append((t_end, [p_land[0], p_land[1] - sink, p_land[2]]))
    rot.append((t_end, [90, 0, 0]))
    return {"position": track(sorted(pos, key=lambda x: x[0]), "position"),
            "rotation": track(sorted(rot, key=lambda x: x[0]), "rotation")}


def tremble(times, amp, sign=1):
    keys = [(0, Z3)]
    for n, t in enumerate(times):
        keys.append((t, [0, 0, amp * sign * (1 if n % 2 == 0 else -1)]))
    return keys


def anims():
    A = {}
    heart_idle = beat(0, 1.05, 1.04, 2.4)
    for name in ("idle", "walk"):
        A[name] = {"loop": True, "animation_length": 2.4, "bones": {
            "heart": {"scale": track(heart_idle, "scale")},
            "heart_veins": {"scale": track(heart_idle, "scale")},
            "core": {"scale": track(beat(0, 1.08, 1.06, 2.4), "scale")},
            "tendrils": {"rotation": track([(0, Z3), (1.2, [3, 0, 0]), (2.4, Z3)], "rotation")},
            # 継ぎ目と割れ目は鼓動に合わせてわずかにふくらむ (光の脈)
            "seams": {"scale": track([(0, ONE), (0.2, [1.03, 1, 1.03]), (0.45, ONE), (2.4, ONE)], "scale")},
            "cracks": {"scale": track([(0, ONE), (0.2, [1.03, 1, 1.03]), (0.45, ONE), (2.4, ONE)], "scale")},
            # 天秤 (鎖と吊られた殻) はゆっくり揺れる。鎖と殻は同じ揺れ (つながったまま)
            "chains": {"rotation": track([(0, [0, 0, -2]), (1.2, [0, 0, 2]), (2.4, [0, 0, -2])], "rotation")},
            "shard": {"rotation": track([(0, [0, 0, -2]), (1.2, [0, 0, 2]), (2.4, [0, 0, -2])], "rotation")},
            "spikes": {"rotation": track([(0, Z3), (1.2, [0, 8, 0]), (2.4, Z3)], "rotation")},
        }}
    if True:
        # 第1段階 → 第2段階の咆哮: 後ろの殻 2 枚 (plate_4, plate_5) が外へ倒れ、床に沈む。
        # 残りの殻は震え、継ぎ目が光る。心臓が強く打つ
        bones = {
            "heart": {"scale": track([(0, ONE), (0.4, S(1.12)), (0.8, S(0.96)), (1.2, S(1.14)), (1.8, ONE), (3.0, ONE)], "scale"),
                      "rotation": track([(0, Z3), (0.6, [-10, 0, 0]), (1.6, [-10, 0, 0]), (2.4, Z3)], "rotation")},
            "plate_4": fall_keys(4, 0.9, 1.7, 3.0, wobble=[(0.3, 2), (0.5, -2), (0.7, 2)]),
            "plate_5": fall_keys(5, 1.0, 1.8, 3.0, wobble=[(0.3, -2), (0.5, 2), (0.7, -2)]),
            "seams": {"scale": track([(0, ONE), (0.4, [1.05, 1, 1.05]), (0.8, ONE), (1.2, [1.05, 1, 1.05]), (1.8, ONE), (3.0, ONE)], "scale")},
            "crown": {"position": track([(0, Z3), (0.9, Z3), (1.1, [0, 1.0, 0]), (1.4, Z3), (3.0, Z3)], "position")},
        }
        # 倒れる殻に接していた継ぎ目 (seam_4, 5, 6) も殻と一緒に沈む
        for k in (4, 5, 6):
            bones[f"seam_{k}"] = {"position": track([(0, Z3), (1.2, Z3), (1.8, [0, -20, 0])], "position"),
                                  "scale": track([(0, ONE), (1.2, ONE), (1.8, [0.01, 0.01, 0.01])], "scale")}
        for i in (0, 1, 2, 3, 6, 7):
            sg = 1 if i % 2 == 0 else -1
            bones[f"plate_{i}"] = {"rotation": track(tremble([1.0, 1.2, 1.4], 1.5, sg) + [(1.8, Z3)], "rotation")}
        A["roar_1"] = {"animation_length": 3.0, "bones": bones}
    if True:
        # 第2段階 → 第3段階の咆哮: 残りの殻の上半分が外へ開き、前の 2 枚 (plate_1, plate_2) が倒れる。
        # 割れ目が強く光り、心臓と血管が大きく打つ。最後は第3段階の模型 (受け皿と吊られた殻) に替わる
        bones = {
            "heart": {"scale": track([(0, ONE), (0.4, S(1.1)), (0.8, S(0.95)), (1.2, S(1.12)), (1.8, ONE), (3.0, ONE)], "scale")},
            "heart_veins": {"scale": track([(0, ONE), (0.4, S(1.16)), (0.8, S(0.95)), (1.2, S(1.2)), (1.8, ONE), (3.0, ONE)], "scale")},
            "cracks": {"scale": track([(0, ONE), (0.6, [1.06, 1, 1.06]), (1.0, ONE), (1.4, [1.06, 1, 1.06]), (2.0, [0.01, 1, 0.01]), (3.0, [0.01, 1, 0.01])], "scale")},
            "plate_1": fall_keys(1, 1.0, 1.8, 3.0),
            "plate_2": fall_keys(2, 1.1, 1.9, 3.0),
            "spikes": {"position": track([(0, Z3), (1.0, Z3), (1.3, [0, 2.5, 0]), (2.2, [0, -14, 0])], "position")},
            "crown": {"position": track([(0, Z3), (1.0, Z3), (1.3, [0, 2.5, 0]), (2.2, [0, -14, 0])], "position"),
                      "scale": track([(0, ONE), (1.6, ONE), (2.2, S(0.01))], "scale")},
        }
        bones["spikes"]["scale"] = track([(0, ONE), (1.6, ONE), (2.2, S(0.01))], "scale")
        for i in (0, 3, 6, 7):
            tilt = [(0, Z3), (0.6, Z3), (1.2, [14, 0, 0]), (3.0, [14, 0, 0])]
            pos = [(0, Z3), (0.6, Z3), (1.2, hinge_pos(i, 14)), (3.0, hinge_pos(i, 14))]
            bones[f"plate_{i}"] = {"rotation": track(tilt, "rotation"), "position": track(pos, "position")}
        A["roar_2"] = {"animation_length": 3.0, "bones": bones}
    # ---- 第1段階の技 ----
    if True:
        # 殻の突き: 心臓がふくらみ (1 秒)、前の殻 (plate_1, plate_2 と両わきの plate_0, plate_3) が前へ突き出す (1.2 秒)
        fwd = np.array([0, 0, 1.0])
        bones = {"heart": {"scale": track([(0, ONE), (1.0, S(1.1)), (1.2, ONE), (1.8, ONE)], "scale"),
                           "position": track([(0, Z3), (1.0, [0, 0, -0.8]), (1.2, [0, 0, 3.0]), (1.8, Z3)], "position")}}
        moves = {"plate_1": 4.5, "plate_2": 4.5, "plate_0": 2.5, "plate_3": 2.5,
                 "seam_1": 3.5, "seam_2": 4.5, "seam_3": 3.5, "seam_0": 1.2, "seam_4": 1.2}
        for name, d in moves.items():
            k = int(name.split("_")[1])
            if name.startswith("plate"):
                spread = out_dir(k) * np.array([1, 0, 0]) * 0.8
            else:
                spread = np.zeros(3)
            p = (fwd * d + spread).tolist()
            back = (fwd * -0.6).tolist()
            bones[name] = {"position": track([(0, Z3), (1.0, back), (1.2, p), (1.45, p), (1.8, Z3)], "position")}
        # ふくらむ間、前の殻は小さく震える
        for name in ("plate_1", "plate_2"):
            sg = 1 if name == "plate_1" else -1
            bones[name]["rotation"] = track(tremble([0.3, 0.5, 0.7, 0.9], 1.2, sg) + [(1.0, Z3)], "rotation")
        A["thrust"] = {"animation_length": 1.8, "bones": bones}
    if True:
        # 石棘の掃射: 根が左から右へ振られ、継ぎ目が光る
        A["sweep"] = {"animation_length": 2.6, "bones": {
            "tendrils": {"rotation": track([(0, Z3), (1.0, [0, 45, 0]), (1.2, [0, 45, 0]), (2.2, [0, -45, 0]), (2.6, Z3)], "rotation")},
            "seams": {"scale": track([(0, ONE), (0.4, [1.05, 1, 1.05]), (1.0, [1.05, 1, 1.05]), (1.4, ONE), (2.6, ONE)], "scale")},
            "crown": {"rotation": track([(0, Z3), (1.0, [0, 30, 0]), (1.2, [0, 30, 0]), (2.2, [0, -30, 0]), (2.6, Z3)], "rotation")},
        }}
    if True:
        # 跳躍の叩きつけ: 殻全体がつぶれて沈み (0.9 秒)、跳ね上がり、1.2 秒で着地する。足と根は床に残る
        squash = [1.07, 0.86, 1.07]
        stretch = [0.95, 1.1, 0.95]
        land = [1.1, 0.9, 1.1]
        sc = [(0, ONE), (0.9, squash), (1.05, stretch), (1.2, land), (1.45, ONE), (2.2, ONE)]
        py = [(0, Z3), (0.9, [0, -0.6, 0]), (1.05, [0, 4.0, 0]), (1.2, [0, -0.3, 0]), (1.45, Z3), (2.2, Z3)]
        bones = {}
        for name in ["heart", "seams"] + [f"plate_{i}" for i in range(8)]:
            bones[name] = {"scale": track(sc, "scale"), "position": track(py, "position")}
        # 冠は殻の上に乗ったまま (殻のつぶれ方に合わせて上下する。上端 21 は中心 12 から 9)
        cy = [(t, [0, p[1] + 9 * (s[1] - 1), 0]) for (t, p), (_, s) in zip(py, sc)]
        bones["crown"] = {"position": track(cy, "position")}
        A["leap"] = {"animation_length": 2.2, "bones": bones}
    # ---- 第2段階の技 ----
    if True:
        # 割れ目の噴出: 割れ目が光って脈打ち、心臓が左右にねじれる (血管も一緒)
        hr = [(0, Z3), (0.6, [0, 4, 0]), (0.9, [0, -4, 0]), (1.2, [0, 4, 0]), (1.5, Z3)]
        A["crack"] = {"animation_length": 2.6, "bones": {
            "cracks": {"scale": track([(0, ONE), (0.5, [1.06, 1, 1.06]), (1.0, ONE), (1.25, [1.06, 1, 1.06]), (1.5, ONE), (2.6, ONE)], "scale")},
            "heart": {"rotation": track(hr, "rotation")},
            "heart_veins": {"rotation": track(hr, "rotation"),
                            "scale": track([(0, ONE), (0.5, S(1.12)), (1.0, ONE), (1.25, S(1.12)), (1.5, ONE)], "scale")},
        }}
    if True:
        # 殻の再閉: 殻が震え (1.5 秒)、開いた後ろへ寄って閉じようとする (1.5 秒で閉じる)。2.0 秒で戻る
        # 開いた所は plate_3 (157.5 度) と plate_6 (292.5 度) の間。plate_3 は角度を増やし、plate_6 は減らす
        bones = {}
        close = {"plate_3": -24, "plate_2": -10, "crack_3": -17, "plate_6": 24, "plate_7": 10, "crack_7": 17}
        for name, b in close.items():
            keys = [(0, Z3), (0.3, [0, b * 0.15, 0]), (1.2, [0, b * 0.4, 0]), (1.5, [0, b, 0]), (1.75, [0, b, 0]), (2.0, Z3)]
            bones[name] = {"rotation": track(keys, "rotation")}
        frac = [(0, 0), (0.3, 0.15), (1.2, 0.4), (1.5, 1.0), (1.75, 1.0), (2.0, 0)]
        for i, name in enumerate(("plate_0", "plate_1", "plate_7", "plate_6", "plate_3", "plate_2")):
            sg = 1 if i % 2 == 0 else -1
            # 0.2 秒ごとに z で震え (1.2 秒まで)、y で閉じる側へ寄る
            times = sorted({0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 0.3, 1.5, 1.75, 2.0})
            b = close.get(name, 0)
            out = []
            for t in times:
                z = 0 if t in (0, 0.3) or t > 1.2 else 1.5 * sg * (1 if round(t / 0.2) % 2 else -1)
                ks = [x for x in frac if x[0] <= t][-1], [x for x in frac if x[0] >= t][0]
                (ta, fa), (tb, fb) = ks
                f = fa if tb == ta else fa + (fb - fa) * (t - ta) / (tb - ta)
                out.append((t, [0, b * f, z]))
            bones[name] = {"rotation": track(out, "rotation")}
        bones["heart"] = {"scale": track([(0, ONE), (1.5, S(0.94)), (1.75, S(0.94)), (2.0, ONE)], "scale")}
        bones["heart_veins"] = {"scale": track([(0, ONE), (1.5, S(0.94)), (1.75, S(0.94)), (2.0, ONE)], "scale")}
        A["reclose"] = {"animation_length": 2.0, "bones": bones}
    if True:
        # 骨の棘の投射: 冠の棘が回って狙いをつけ (1.2 秒)、跳ねる。4 本の棘は 90 度で同じ形に戻る
        A["volley"] = {"animation_length": 1.8, "bones": {
            "spikes": {"rotation": track([(0, Z3), (0.9, [0, -70, 0]), (1.2, [0, -90, 0]), (1.8, [0, -90, 0])], "rotation"),
                       "position": track([(0, Z3), (1.15, Z3), (1.25, [0, 1.5, 0]), (1.45, Z3), (1.8, Z3)], "position")},
            "cracks": {"scale": track([(0, ONE), (0.8, [1.05, 1, 1.05]), (1.5, ONE)], "scale")},
            "crown": {"position": track([(0, Z3), (1.15, Z3), (1.25, [0, 0.8, 0]), (1.45, Z3), (1.8, Z3)], "position")},
        }}
    # ---- 第3段階の技 ----
    if True:
        # 赤い光線: 心核がふくらみ (2 秒)、放つ。心臓と血管が強く打つ
        A["beam"] = {"animation_length": 3.2, "bones": {
            "core": {"scale": track([(0, ONE), (2.0, S(1.5)), (2.2, S(1.1)), (3.0, S(1.1)), (3.2, ONE)], "scale")},
            "heart": {"scale": track([(0, ONE), (1.0, S(1.03)), (2.0, S(1.06)), (2.2, S(0.97)), (3.0, S(0.97)), (3.2, ONE)], "scale")},
            "heart_veins": {"scale": track([(0, ONE), (1.0, S(1.15)), (2.0, S(1.3)), (2.6, ONE)], "scale")},
        }}
    if True:
        # 鎖の振り回し: 天秤が後ろへ引かれ (1.2 秒)、左から前を通って右へ半回転する (3.0 秒)。
        # 鎖 (天秤) と吊られた殻は同じ回転 (y 軸、どちらも x=z=0 の軸)
        yaw = [(0, Z3), (1.2, [0, 25, 0]), (3.0, [0, -180, 0])]
        A["chain"] = {"animation_length": 3.0, "bones": {
            "chains": {"rotation": track(yaw, "rotation")},
            "shard": {"rotation": track(yaw, "rotation")},
        }}
    if True:
        # 殻の落下: 天秤が左右に傾いて揺れ (0.9 秒)、持ち上がり、1.5 秒で落ちて受け皿が震える
        tip = [(0, Z3), (0.3, [0, 0, 8]), (0.6, [0, 0, -8]), (0.9, [0, 0, 4]), (1.2, Z3), (2.2, Z3)]
        lift = [(0, Z3), (0.9, Z3), (1.3, [0, 3.5, 0]), (1.45, [0, 3.5, 0]), (1.55, Z3), (1.65, [0, 0.6, 0]), (1.8, Z3)]
        A["shell_drop"] = {"animation_length": 2.2, "bones": {
            "chains": {"rotation": track(tip, "rotation"), "position": track(lift, "position")},
            "shard": {"rotation": track(tip, "rotation"), "position": track(lift, "position")},
            "bowl": {"rotation": track([(0, Z3), (1.5, Z3), (1.6, [0, 0, 2]), (1.75, [0, 0, -2]), (1.9, [0, 0, 1]), (2.05, Z3)], "rotation")},
        }}
    return {"format_version": "1.8.0",
            "animations": {f"animation.{NAME}.{k}": v for k, v in A.items()}}


# ---------------------------------------------------------------- GeckoLib を真似た描画 (確認用)

def _rot(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def sample(anim, t):
    """アニメーション (geo の空間の値) の時刻 t の値 {骨: {rotation, position, scale}} (線形補間)"""
    out = {}
    for bone, chans in anim["bones"].items():
        o = {}
        for ch, keys in chans.items():
            ks = sorted((float(k), v) for k, v in keys.items())
            if t <= ks[0][0]:
                v = ks[0][1]
            elif t >= ks[-1][0]:
                v = ks[-1][1]
            else:
                for (a, va), (b, vb) in zip(ks, ks[1:]):
                    if a <= t <= b:
                        f = (t - a) / (b - a) if b > a else 1
                        v = [x + (y - x) * f for x, y in zip(va, vb)]
                        break
            o[ch] = v
        out[bone] = o
    return out


def gecko_faces(geo, tex, glow, pose=None):
    """GeckoLib 4.9.3 の焼き方で面を作る (焼いた後の空間)。最後に y 軸で 180 度回し、正面を +Z にする。
    - 箱: x → -(x + 幅)。骨の pivot: x → -x。骨の回転: (-rx, -ry, rz)。位置のキーフレーム: (-px, py, pz)
    - 骨の行列: 位置 → pivot へ → Rz Ry Rx → 大きさ → pivot から戻す (親から積む)
    - 面の四隅と uv は BakedModelFactory.VertexSet / GeoQuad.build のとおり"""
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    sx, sy = tex.shape[1] / tw, tex.shape[0] / th
    bones = {b["name"]: b for b in g["bones"]}
    pose = pose or {}
    cache = {}

    def world(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        p = b.get("pivot", [0, 0, 0])
        piv = np.array([-p[0], p[1], p[2]], float)
        r = np.array(b.get("rotation", [0, 0, 0]), float)
        ps = pose.get(name, {})
        r = r + np.array(ps.get("rotation", [0, 0, 0]), float)
        R = _rot(-r[0], -r[1], r[2])
        sc = np.diag(ps.get("scale", [1, 1, 1]))
        pp = ps.get("position", [0, 0, 0])
        pos = np.array([-pp[0], pp[1], pp[2]], float)
        L = R @ sc
        M = (L, pos + piv - L @ piv)
        if "parent" in b:
            PR, Pt = world(b["parent"])
            M = (PR @ M[0], PR @ M[1] + Pt)
        cache[name] = M
        return M

    D = _rot(0, 180, 0)
    faces = []
    for b in g["bones"]:
        R, t = world(b["name"])
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            x0, x1 = -(o[0] + s[0]), -o[0]
            y0, y1 = o[1], o[1] + s[1]
            z0, z1 = o[2], o[2] + s[2]
            BLB, BRB, TLB, TRB = (x0, y0, z0), (x0, y0, z1), (x0, y1, z0), (x0, y1, z1)
            TLF, TRF, BLF, BRF = (x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (x1, y0, z1)
            quads = {"west": (TRB, TLB, BLB, BRB), "east": (TLF, TRF, BRF, BLF),
                     "north": (TLB, TLF, BLF, BLB), "south": (TRF, TRB, BRB, BRF),
                     "up": (TRB, TRF, TLF, TLB), "down": (BLB, BLF, BRF, BRB)}
            for name, f in c["uv"].items():
                V = [np.array(v, float) for v in quads[name]]
                # 四隅の uv: V0 (u1, v0), V1 (u0, v0), V2 (u0, v1), V3 (u1, v1)
                co, eu, ev = V[2], V[3] - V[2], V[1] - V[2]
                u, v = f["uv"]
                w, h = f["uv_size"]
                a0, a1 = int(round(u * sx)), int(round((u + w) * sx))
                b0, b1 = int(round(v * sy)), int(round((v + h) * sy))
                patch = tex[b0:b1, a0:a1]
                gp = None if glow is None else glow[b0:b1, a0:a1]
                faces.append(Face(D @ (R @ co + t), D @ (R @ eu), D @ (R @ ev), patch, gp, FACE_SHADE[name]))
    return faces


# ---------------------------------------------------------------- 本体

def load_png(p):
    return np.array(Image.open(p).convert("RGBA"))


def main():
    anim = anims()
    used = sorted({b for a in anim["animations"].values() for b in a["bones"]})
    out_geo = {}
    rough = {}
    for ph in (1, 2, 3):
        geo = json.loads((SRC / f"phase{ph}.geo.json").read_text(encoding="utf-8"))
        tex = load_png(SRC / f"phase{ph}.tex.png")
        glow = load_png(SRC / f"phase{ph}.glow.png")
        rough[ph] = (geo, tex, glow)
        g2, t2, gl2 = convert_geo(geo, tex, glow)
        added = ensure_bones(g2, used)
        # 吊られた殻の親 (shard) の pivot を天秤 (chains) と同じ点にする。同じ回転で鎖と殻が離れない
        bmap = {b["name"]: b for b in g2["minecraft:geometry"][0]["bones"]}
        if bmap["chains"].get("cubes"):
            bmap["shard"]["pivot"] = list(bmap["chains"]["pivot"])
        lit = int((gl2[..., 3] > 0).sum())
        assert lit >= 1, "発光マスクに光る画素がない (GeckoLib が落ちる)"
        (ASSET / f"geo/entity/{NAME}_p{ph}.geo.json").write_text(json.dumps(g2, ensure_ascii=False, indent=1), encoding="utf-8")
        Image.fromarray(t2).save(ASSET / f"textures/entity/{NAME}_p{ph}.png")
        Image.fromarray(gl2).save(ASSET / f"textures/entity/{NAME}_p{ph}_glowmask.png")
        out_geo[ph] = (g2, t2, gl2)
        ncubes = sum(len(b.get("cubes", [])) for b in g2["minecraft:geometry"][0]["bones"])
        print(f"phase {ph}: {ncubes} cubes, glow px {lit}, empty bones added: {added}")
    (ASSET / f"animations/entity/{NAME}.animation.json").write_text(json.dumps(anim, ensure_ascii=False, indent=1), encoding="utf-8")

    # 確認の絵: 1 行目 ラフ (voxel の描き方)、2 行目 ゲームの読み方で描いた geo。視点は boss_v4 と同じ
    views = {1: (35, 24), 2: (150, 22), 3: (22, 20)}
    size = 360
    cols = []
    diffs = []
    for ph in (1, 2, 3):
        geo, tex, glow = rough[ph]
        yaw, pitch = views[ph]
        a = render(load_geo(geo, tex, glow), yaw, pitch, (size, size))
        g2, t2, gl2 = out_geo[ph]
        b = render(gecko_faces(g2, t2, gl2[..., 3] > 0), yaw, pitch, (size, size))
        # 下からの視点 (down 面の向きの確認)
        a2 = render(load_geo(geo, tex, glow), yaw + 180, -35, (size, size))
        b2 = render(gecko_faces(g2, t2, gl2[..., 3] > 0), yaw + 180, -35, (size, size))
        d = max(np.abs(np.asarray(a, float) - np.asarray(b, float)).mean(),
                np.abs(np.asarray(a2, float) - np.asarray(b2, float)).mean())
        diffs.append(d)
        cols.append((f"第{ph}段階", [a, b, b2]))
    print("rough vs game-read mean abs diff:", [round(x, 3) for x in diffs])

    # アニメーションの要所
    poses = [
        (1, "roar_1", 1.45, 150), (1, "roar_1", 2.3, 150), (1, "thrust", 1.2, 35), (1, "leap", 0.9, 35), (1, "leap", 1.05, 35),
        (2, "roar_2", 1.5, 120), (2, "roar_2", 2.4, 120), (2, "reclose", 1.5, 150), (2, "volley", 1.25, 35),
        (3, "chain", 1.2, 22), (3, "chain", 2.1, 22), (3, "shell_drop", 0.3, 22), (3, "shell_drop", 1.4, 22), (3, "beam", 2.0, 22),
    ]
    pose_imgs = []
    for ph, name, t, yaw in poses:
        g2, t2, gl2 = out_geo[ph]
        p = sample(anim["animations"][f"animation.{NAME}.{name}"], t)
        im = render(gecko_faces(g2, t2, gl2[..., 3] > 0, p), yaw, 22, (size, size))
        pose_imgs.append((f"{name} {t}s", im))
    cols2 = []
    for label, im in pose_imgs:
        name = label.split()[0]
        if cols2 and cols2[-1][0].startswith(name + " "):
            cols2[-1] = (cols2[-1][0] + " / " + label.split()[1], cols2[-1][1] + [im])
        else:
            cols2.append((label, [im]))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    s1 = sheet("上: ラフ / 中: ゲームの読み方 / 下: 下から", cols)
    s2 = sheet("岩鯨の心臓 案A: アニメーションの要所 (ゲームの読み方)", cols2)
    W = max(s1.width, s2.width)
    out = Image.new("RGB", (W, s1.height + s2.height), (20, 20, 26))
    out.paste(s1, (0, 0))
    out.paste(s2, (0, s1.height))
    out.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
