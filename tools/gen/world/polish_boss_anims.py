#!/usr/bin/env python3
"""巨神と心臓の動きの仕上げ (docs/review/boss_quality_2026-10-23.md)。入った animation.json に、名前ごとに上書き・追加する。

何度流しても同じ結果になる (上書きする動きは毎回ここの定義から作り直す。足すぶれは、元の動きを
tools/sources/anim_base/ に一度だけ写して、そこから作る)。心臓の install_*heart*.py・巨神の install を流し直したら、これも流す。

  python tools/gen/world/polish_boss_anims.py

やること:
  1. 岩鯨の心臓: 棘の薙ぎ (sweep) を棘の波の速さ (1.0〜3.4 秒) に合わせる、地割れ (crack) に 1.5 秒の叩きつけ、
     骨の棘の投射 (volley) を 1.5 秒の放ちに合わせる (どれも予告が終わる瞬間 = 当たりの瞬間)
  2. 潮殻・雷翼・余燼の心臓: 技の当たりの直前 0.45 秒の「溜めの震え」と、当たりの瞬間の「押し出し・反動」を、心臓と殻に足す
     (元の動きの位置の鍵が無い骨だけ。大きさ・回転の鍵はそのまま)
  3. 四つの心臓: 「ひるみ」stagger (2 秒)。領域を解いて砕けた瞬間に流す (HeartDomain.end → HeartBoss.playAction)
  4. 巨神: 岩鯨の眠り sleep、潮殻の頭の眠り head_sleep (眠る巨神 DormantTitan は動かず。泳ぎ・歩きのま眠らない)
  5. 被弾のひるみ flinch (四つの心臓と番人。0.45 秒)。技とは別の制御 "hurt" で流す (HeartBoss.hurt・HeartWarden.hurt)。
     GeckoLib は同じ骨を動かす制御どうしでは後のものが上書きするので、ひるみは技が触らない骨だけを動かす:
     心臓は全部の骨を包む骨 flinch を geo に足し (心臓の geo は骨が全部いちばん上にあるため。何度流しても一つだけ)、
     番人はもとからある根の骨 root を動かす。だから技の溜め・当たりの形の上に、体ごとのけぞり・縮みが重なる。
     心臓の geo を書き直す install (install_lowpoly.py・install_*heart*.py・install_heart_atlas_256.py) を流したら、これも流す。
"""
import json
import math
import os
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity")
BASE = os.path.join(ROOT, "tools", "sources", "anim_base")

# 技の予告の長さ (tick)。動きの始まりから当たりまで (各心臓の *_WARN。HeartBoss の playAction と同じ tick に予告が出る)
WARN = {
    "tide_heart": {"breath": 24, "shell_strike": 20, "whirl": 70, "flood": 30, "bubble": 30, "pearl": 20, "ebb": 100,
                   "moon_wave": 40, "pearl_rain": 30},
    "storm_heart": {"summon": 24, "blade": 24, "chain": 20, "wingbeat": 30, "charge": 30, "feather": 24, "flash": 40,
                    "web": 30, "barrage": 20},
    "ember_heart": {"breath": 24, "pulse": 20, "orb": 30, "floor": 40, "gale": 70, "pellet": 20, "fading": 40,
                    "embers": 20, "veil": 30},
}
# 溜めの震えと押し出しを足す骨 (心臓の芯と大きな殻)
MASS = {
    "tide_heart": ["heart", "heart_vein", "core_ring", "core_eye", "chamber", "spiral", "aperture_lip", "barnacle", "frond"],
    "storm_heart": ["heart", "heart_vein", "core", "core_eye", "wing_l", "wing_r", "crest_l", "crest_r"],
    "ember_heart": ["heart", "heart_vein", "heart_crust", "core_ring", "core_eye", "crust", "crust_glow", "ash_shell"],
}
ALL_BONES = {
    "stone_whale_heart": ["base", "bowl", "chains", "core", "cracks", "crack_0", "crack_1", "crack_2", "crack_3", "crack_7",
                          "crown", "heart", "heart_veins", "seams", "shard", "spikes", "tendrils"]
                         + ["plate_%d" % i for i in range(8)] + ["bowl_%d" % i for i in range(8)]
                         + ["bowl_seam_%d" % i for i in range(8)],
    "tide_heart": ["aperture_lip", "barnacle", "chamber", "core_eye", "core_ring", "frond", "heart", "heart_vein",
                   "moon_crescent", "orbit", "shard", "spiral", "vessel", "water_drop"],
    "storm_heart": ["core", "core_eye", "crest_l", "crest_r", "finger", "heart", "heart_vein", "joint", "shed", "vessel",
                    "web", "wing_l", "wing_r"],
    "ember_heart": ["ash_drips", "ash_shell", "cinders", "core_eye", "core_ring", "crust", "crust_glow", "drips", "drops",
                    "heart", "heart_crust", "heart_vein", "vessel"],
}
# ひるみで大きく揺れる殻 (回転のぶれ)
SHELLS = {
    "stone_whale_heart": ["plate_%d" % i for i in range(8)] + ["crown", "tendrils"],
    "tide_heart": ["spiral", "aperture_lip", "chamber", "frond"],
    "storm_heart": ["wing_l", "wing_r", "crest_l", "crest_r"],
    "ember_heart": ["crust", "ash_shell", "crust_glow"],
}
# 心臓の芯 (ひるみで光が鈍る = 縮む)
CORES = {
    "stone_whale_heart": ["heart", "heart_veins", "core"],
    "tide_heart": ["core_eye", "core_ring", "heart"],
    "storm_heart": ["core_eye", "core", "heart"],
    "ember_heart": ["core_eye", "core_ring", "heart"],
}


def t(x):
    return "%.2f" % x


def keys(frames):
    return {t(k): [round(float(c), 3) for c in v] for k, v in sorted(frames.items())}


# ---------------------------------------------------------------- 1. 岩鯨の心臓の三つの技
def whale_sweep():
    # 棘の波: 20〜68 tick に腕の向きが -60 → +60 度 (StoneWhaleHeart.tickSweep)。触手と冠は 1.0 秒までに振りかぶり、3.4 秒までに薙ぐ
    return {"animation_length": 4.0, "bones": {
        "tendrils": {"rotation": keys({0: (0, 0, 0), 0.55: (0, -30, 0), 1.0: (0, -52, 0), 1.1: (0, -50, 0),
                                       2.2: (0, 0, 0), 3.4: (0, 52, 0), 3.6: (0, 48, 0), 4.0: (0, 0, 0)})},
        "crown": {"rotation": keys({0: (0, 0, 0), 1.0: (0, -32, 0), 2.2: (0, 0, 0), 3.4: (0, 32, 0), 4.0: (0, 0, 0)})},
        "spikes": {"rotation": keys({0: (0, 0, 0), 1.0: (0, -20, 0), 3.4: (0, 20, 0), 4.0: (0, 0, 0)})},
        "seams": {"scale": keys({0: (1, 1, 1), 0.4: (1.08, 1, 1.08), 1.0: (1.1, 1, 1.1), 1.2: (1.02, 1, 1.02),
                                 3.4: (1.02, 1, 1.02), 4.0: (1, 1, 1)})},
        "heart": {"rotation": keys({0: (0, 0, 0), 1.0: (0, -8, 0), 3.4: (0, 8, 0), 4.0: (0, 0, 0)}),
                  "position": keys({0: (0, 0, 0), 0.8: (0, -0.5, 0), 1.0: (0, 0.4, 0), 1.3: (0, 0, 0), 4.0: (0, 0, 0)})},
    }}


def whale_crack():
    # 地割れ: 予告 30 tick。0〜1.2 ひびが光りながら脈打つ、1.2〜1.45 心臓が持ち上がって縮む、1.5 叩きつけ (当たり)、残りで戻る
    # 殻 (板・継ぎ目・鉢・冠・鎖…) は全部同じに持ち上がって落ちる (部品が離れない)。中の心臓だけ大きく動く
    lift = keys({0: (0, 0, 0), 1.2: (0, 0.3, 0), 1.45: (0, 0.9, 0), 1.5: (0, -1.2, 0), 1.62: (0, 0.3, 0), 1.8: (0, 0, 0), 2.6: (0, 0, 0)})
    plates = {n: {"position": lift} for n in ALL_BONES["stone_whale_heart"] if n not in ("heart", "heart_veins", "core")}
    b = {
        "cracks": {"scale": keys({0: (1, 1, 1), 0.5: (1.08, 1, 1.08), 0.9: (1.03, 1, 1.03), 1.25: (1.12, 1, 1.12),
                                  1.45: (1.06, 1, 1.06), 1.5: (1.3, 1, 1.3), 1.8: (1.05, 1, 1.05), 2.6: (1, 1, 1)})},
        "heart": {"rotation": keys({0: (0, 0, 0), 0.6: (0, -4, 0), 0.9: (0, 4, 0), 1.2: (0, -4, 0), 1.5: (0, 0, 0)}),
                  "position": keys({0: (0, 0, 0), 1.2: (0, 0.4, 0), 1.45: (0, 1.4, 0), 1.5: (0, -1.6, 0), 1.65: (0, 0.4, 0),
                                    1.85: (0, 0, 0), 2.6: (0, 0, 0)}),
                  "scale": keys({0: (1, 1, 1), 1.45: (0.92, 1.06, 0.92), 1.5: (1.12, 0.88, 1.12), 1.7: (1, 1, 1), 2.6: (1, 1, 1)})},
        "heart_veins": {"rotation": keys({0: (0, 0, 0), 0.6: (0, -4, 0), 0.9: (0, 4, 0), 1.2: (0, -4, 0), 1.5: (0, 0, 0)}),
                        "position": keys({0: (0, 0, 0), 1.2: (0, 0.4, 0), 1.45: (0, 1.4, 0), 1.5: (0, -1.6, 0),
                                          1.65: (0, 0.4, 0), 1.85: (0, 0, 0), 2.6: (0, 0, 0)}),
                        "scale": keys({0: (1, 1, 1), 0.5: (1.1, 1.1, 1.1), 1.0: (1, 1, 1), 1.25: (1.1, 1.1, 1.1),
                                       1.5: (1.18, 0.9, 1.18), 1.8: (1, 1, 1), 2.6: (1, 1, 1)})},
    }
    for n, v in plates.items():
        b.setdefault(n, {}).update(v)
    return {"animation_length": 2.6, "bones": b}


def whale_volley():
    # 骨の棘の投射: 予告 30 tick。0〜1.3 棘が人へ向く、1.3〜1.42 引き絞る、1.5 放つ (当たり)、残りで戻る。技は 40 tick
    return {"animation_length": 2.0, "bones": {
        "spikes": {"rotation": keys({0: (0, 0, 0), 0.9: (0, 70, 0), 1.3: (0, 90, 0), 2.0: (0, 90, 0)}),
                   "position": keys({0: (0, 0, 0), 1.3: (0, 0, 0), 1.42: (0, -0.8, 0), 1.5: (0, 1.9, 0), 1.65: (0, 0.3, 0),
                                     1.8: (0, 0, 0), 2.0: (0, 0, 0)})},
        "cracks": {"scale": keys({0: (1, 1, 1), 0.8: (1.1, 1, 1.1), 1.42: (1.12, 1, 1.12), 1.5: (1.2, 1, 1.2), 1.8: (1, 1, 1)})},
        "crown": {"position": keys({0: (0, 0, 0), 1.3: (0, 0, 0), 1.42: (0, -0.4, 0), 1.5: (0, 1.0, 0), 1.65: (0, 0, 0), 2.0: (0, 0, 0)})},
        "heart": {"scale": keys({0: (1, 1, 1), 1.3: (1.05, 1.05, 1.05), 1.42: (0.94, 0.94, 0.94), 1.5: (1.12, 1.12, 1.12),
                                 1.7: (1, 1, 1), 2.0: (1, 1, 1)})},
    }}


# ---------------------------------------------------------------- 2. 溜めの震えと押し出し
def sample(kf, x):
    """数値の鍵 (時刻の文字列 -> [x, y, z]) を時刻 x で線形に補う"""
    ts = sorted((float(k), v) for k, v in kf.items())
    vals = [(k, [float(c) for c in (v.get("post", v.get("pre")) if isinstance(v, dict) else v)]) for k, v in ts]
    if x <= vals[0][0]:
        return vals[0][1]
    for (t0, v0), (t1, v1) in zip(vals, vals[1:]):
        if x <= t1:
            r = (x - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * r for a, b in zip(v0, v1)]
    return vals[-1][1]


def windup_kick(anim, warn):
    """当たり (warn tick) の前 0.45 秒を小さく震わせ、当たりで下へ押し出して跳ね返る。
    全部の骨に同じずれを足す (部品が離れない)。元の位置の鍵がある骨は、その鍵の値に足し合わせる。"""
    T = warn / 20.0
    L = anim.get("animation_length", T + 0.6)
    f = {0: (0, 0, 0), round(T - 0.45, 2): (0, 0, 0)}
    k = 0
    tt = T - 0.40
    while tt < T - 0.04:
        sg = 1 if k % 2 == 0 else -1
        amp = 0.18 + 0.22 * (k / 4.0)
        f[round(tt, 2)] = (sg * amp, 0.08 * sg, -sg * amp * 0.5)
        tt += 0.1
        k += 1
    f[round(T - 0.02, 2)] = (0, 0.5, 0)
    f[round(T + 0.05, 2)] = (0, -0.9, 0)
    f[round(min(L, T + 0.2), 2)] = (0, 0.25, 0)
    f[round(min(L, T + 0.4), 2)] = (0, 0, 0)
    if L > T + 0.4:
        f[round(L, 2)] = (0, 0, 0)
    bones = anim.setdefault("bones", {})
    for name in ALL_NOW:
        b = bones.setdefault(name, {})
        old = b.get("position")
        if isinstance(old, dict) and old and "vector" not in old:
            times = sorted(set(round(float(x), 2) for x in old) | set(f))
            add = {x: sample(keys(f), x) for x in times}
            b["position"] = keys({x: [o + d for o, d in zip(sample(old, x), add[x])] for x in times})
        elif old is None:
            b["position"] = keys(f)


# ---------------------------------------------------------------- 3. ひるみ
def stagger(h):
    """領域が砕けた瞬間: 全体が下へ沈んで跳ね、横に揺れながら弱まる。殻は傾いて揺れ、芯は鈍く縮む。2 秒で戻る (次の技まで 40 tick)。"""
    bones = {}
    sink = {0: (0, 0, 0), 0.12: (0, -2.4, 0), 0.3: (0.5, 0.6, 0), 0.5: (-0.45, -0.9, 0), 0.75: (0.3, -0.6, 0),
            1.0: (-0.2, -0.7, 0), 1.3: (0.1, -0.4, 0), 1.7: (0, -0.1, 0), 2.0: (0, 0, 0)}
    for name in ALL_BONES[h]:
        bones[name] = {"position": keys(sink)}
    for i, name in enumerate(SHELLS[h]):
        s = 1 if i % 2 == 0 else -1
        bones[name]["rotation"] = keys({0: (0, 0, 0), 0.12: (6 * s, 0, 9 * s), 0.35: (-3 * s, 0, -6 * s), 0.6: (2 * s, 0, 5 * s),
                                        0.9: (-1 * s, 0, -3 * s), 1.3: (0, 0, 1.5 * s), 2.0: (0, 0, 0)})
    for name in CORES[h]:
        bones[name]["scale"] = keys({0: (1, 1, 1), 0.12: (1.25, 0.8, 1.25), 0.35: (0.82, 0.82, 0.82), 0.8: (0.86, 0.86, 0.86),
                                     1.1: (0.92, 0.92, 0.92), 1.4: (0.88, 0.88, 0.88), 2.0: (1, 1, 1)})
    return {"animation_length": 2.0, "bones": bones}


# ---------------------------------------------------------------- 4. 巨神の眠り
def whale_sleep():
    # 眠る岩鯨: 口を閉じ、頭を少し下げ、ひれは止まって息だけでわずかに上下する。尾はほとんど動かない (泳ぎの 1/5 の振れ、半分の速さ)
    return {"loop": True, "animation_length": 12.0, "bones": {
        "head": {"rotation": ["3 + math.sin(query.anim_time * 30) * 0.6", "0", "0"]},
        "jaw": {"rotation": ["0", "0", "0"]},
        "tail": {"rotation": ["0", "math.sin(query.anim_time * 30 - 90) * 0.9", "0"]},
        "fluke": {"rotation": ["0", "0", "math.sin(query.anim_time * 30 - 150) * 1.4"]},
        "fin_left": {"rotation": ["0", "0", "math.sin(query.anim_time * 30) * 0.8"]},
        "fin_right": {"rotation": ["0", "0", "-math.sin(query.anim_time * 30) * 0.8"]},
    }}


def tide_head_sleep():
    # 眠る潮殻の頭: 首を下げて甲羅の方へ引き、ほとんど動かない (見上げ look の逆)
    return {"loop": True, "animation_length": 10.0, "bones": {
        "head": {"rotation": ["14 + math.sin(query.anim_time * 36) * 0.8", "0", "0"],
                 "position": ["0", "-1.5", "3"]},
    }}


# ---------------------------------------------------------------- 5. 被弾のひるみ
GEO = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "geo", "entity")
# 包みの骨の支点 (単位)。岩鯨の心臓は床の鉢ごと揺れるので床 (0) を支点に、浮かぶ三体は芯の高さ
FLINCH_PIVOT = {"stone_whale_heart": [0.0, 0.0, 0.0], "tide_heart": [0.0, 16.0, 0.0],
                "storm_heart": [0.0, 22.0, 0.0], "ember_heart": [0.0, 16.0, 0.0]}


def wrap_geo(h):
    """心臓の geo (段階ごと・低ポリ・低ポリの確かめ用・箱) に、いちばん上の骨を全部包む骨 flinch を足す"""
    n = 0
    for f in sorted(os.listdir(GEO)):
        if not (f.startswith(h + "_p") or f == h + ".geo.json") or not f.endswith(".geo.json"):
            continue
        path = os.path.join(GEO, f)
        with open(path, encoding="utf-8") as fh:
            raw = fh.read()
        d = json.loads(raw)
        lines = raw.strip().split(chr(10))
        compact = len(lines) == 1
        indent = len(lines[1]) - len(lines[1].lstrip(" ")) if not compact else 0
        trail = raw.endswith(chr(10))
        for g in d["minecraft:geometry"]:
            bones = g["bones"]
            if any(b["name"] == "flinch" for b in bones):
                continue
            for b in bones:
                if not b.get("parent"):
                    b["parent"] = "flinch"
            bones.insert(0, {"name": "flinch", "pivot": FLINCH_PIVOT[h]})
        with open(path, "w", encoding="utf-8", newline=chr(10)) as fh:
            # 元の書き方 (一行の低ポリの geo・字下げの箱の geo) のまま書く
            if compact:
                fh.write(json.dumps(d, ensure_ascii=False, separators=(",", ":")))
            else:
                json.dump(d, fh, ensure_ascii=False, indent=indent)
                if trail:
                    fh.write(chr(10))
        n += 1
    return n


def heart_flinch(h):
    # 当たった瞬間 (0.06 秒) に後ろへのけぞって押しつぶれ、0.16 秒に前へ戻りすぎて伸び、0.3 秒の小さな揺れで止まる。
    # 浮かぶ心臓は沈んで跳ね、床の岩鯨の心臓は鉢ごと傾いて揺れる (沈まない)
    floor = h == "stone_whale_heart"
    tilt = 4.0 if floor else 7.0
    return {"animation_length": 0.45, "bones": {"flinch": {
        "rotation": keys({0: (0, 0, 0), 0.06: (-tilt, 0, tilt * 0.45), 0.16: (tilt * 0.4, 0, -tilt * 0.22),
                          0.28: (-tilt * 0.12, 0, tilt * 0.08), 0.45: (0, 0, 0)}),
        "position": keys({0: (0, 0, 0), 0.06: (0, 0 if floor else -0.9, 0), 0.16: (0, 0 if floor else 0.35, 0),
                          0.3: (0, 0, 0), 0.45: (0, 0, 0)}),
        "scale": keys({0: (1, 1, 1), 0.06: (1.07, 0.9, 1.07), 0.16: (0.97, 1.04, 0.97), 0.28: (1.01, 0.99, 1.01),
                       0.45: (1, 1, 1)}),
    }}}


def warden_flinch():
    # 番人: 根 (足もと) を支点にのけぞり、横へ振られ、膝が沈むように縮む。0.32 秒で戻る
    return {"animation_length": 0.32, "bones": {"root": {
        "rotation": keys({0: (0, 0, 0), 0.06: (-6, 0, 4), 0.16: (2.4, 0, -1.6), 0.24: (-0.6, 0, 0.4), 0.32: (0, 0, 0)}),
        "scale": keys({0: (1, 1, 1), 0.06: (1.03, 0.95, 1.03), 0.16: (0.99, 1.02, 0.99), 0.32: (1, 1, 1)}),
    }}}


# ---------------------------------------------------------------- 書き込み
def load(name):
    with open(os.path.join(ANIM, name + ".animation.json"), encoding="utf-8") as f:
        return json.load(f)


def save(name, data):
    with open(os.path.join(ANIM, name + ".animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def base(name):
    """足すぶれの元: 初めて流したときの動きを tools/sources/anim_base/ に写し、以後はそこから作る"""
    os.makedirs(BASE, exist_ok=True)
    p = os.path.join(BASE, name + ".animation.json")
    if not os.path.exists(p):
        shutil.copyfile(os.path.join(ANIM, name + ".animation.json"), p)
    with open(p, encoding="utf-8") as f:
        return json.load(f)


ALL_NOW = []


def main():
    # 心臓
    for h in ("stone_whale_heart", "tide_heart", "storm_heart", "ember_heart"):
        data = base(h) if h in WARN else load(h)
        A = data["animations"]
        p = "animation.%s." % h
        if h == "stone_whale_heart":
            A[p + "sweep"] = whale_sweep()
            A[p + "crack"] = whale_crack()
            A[p + "volley"] = whale_volley()
        else:
            ALL_NOW[:] = ALL_BONES[h]
            for k, w in WARN[h].items():
                if p + k in A:
                    windup_kick(A[p + k], w)
        A[p + "stagger"] = stagger(h)
        A[p + "flinch"] = heart_flinch(h)
        save(h, data)
        print("wrapped", wrap_geo(h), "geo of", h)
        print("polished", h)
    # 巨神
    d = load("stone_whale")
    d["animations"]["animation.stone_whale.sleep"] = whale_sleep()
    save("stone_whale", d)
    d = load("tide_shell")
    d["animations"]["animation.tide_shell.head_sleep"] = tide_head_sleep()
    save("tide_shell", d)
    print("polished stone_whale, tide_shell")
    # 番人のひるみ (gen_heart_warden.py を流し直したら、これも流す)
    d = load("heart_warden")
    d["animations"]["animation.heart_warden.flinch"] = warden_flinch()
    save("heart_warden", d)
    print("polished heart_warden")
    return 0


if __name__ == "__main__":
    sys.exit(main())
