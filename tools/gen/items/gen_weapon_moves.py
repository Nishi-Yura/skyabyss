#!/usr/bin/env python3
"""武器ごとの動き (MoveSet) の JSON を作る。出力: src/main/resources/assets/skyabyss/weapon_moves/<品の id>.json

ゲームの読み込み側は weapon/MoveSet.java。ここが「正」で、動きを直すときはこのファイルを直して再実行する。
キー = (時刻 t, その key へ向かう区間のイージング, 値)。時刻は振りの進み 0..1。hit=True の key が、サーバーがダメージを入れる瞬間 (strike と同じ時刻)。
チャンネルの意味 (weapon/MoveSet.java):
  三人称 MX/MY/MZ = 利き腕の x/y/z 回転 (ラジアン。MX が負 = 前上へ)、OX/OY/OZ = 反対の腕 (何も持っていないときだけ)、BY = 胴のひねり、PZ = 肩の前後 (負 = 前へ)
  一人称 TX/TY/TZ = 手元の移動 (ブロック。TZ が負 = 前へ)、RX/RY/RZ = 回転 (度。RZ・RY は 360 のような全回転もよい)
  LEAN = 体を前へ倒す度、TRAIL = 刃の軌跡の濃さ 0..1、GLOW = 光る画素の強まり 0..1
"""
import json
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "src", "main", "resources", "assets", "skyabyss", "weapon_moves")


def k(t, e="smooth", hit=False, **v):
    d = {"t": t, "e": e.lower(), "v": {a: round(b, 4) for a, b in v.items()}}
    if hit:
        d["hit"] = True
    return d


def step(name, hitstop, shake, keys, bi=0.12, bo=0.2):
    return {"name": name, "hitstop": hitstop, "shake": shake, "blendIn": bi, "blendOut": bo, "keys": keys}


def inspect_step(name, keys):
    return {"name": name, "hitstop": 0, "shake": 0, "blendIn": 0.12, "blendOut": 0.14, "keys": keys}


def weapon(item, label, family, strike, reach, window, idle, steps, inspect, fixed=0):
    d = {"item": item, "label": label, "family": family, "strike": strike, "reach": reach, "window": window}
    if fixed:
        d["fixedDuration"] = fixed
    d["idle"] = idle
    d["steps"] = steps
    if inspect:
        d["inspect"] = inspect
    return d


# 点検: 目の前にかざして (lift)、ゆっくり 1 回転 (全回転は spin の軸に)、光が走り (GLOW)、構えへ戻す。weapon ごとに向きと高さを変える
def inspect(name, lift, depth, tilt, axis="RY", turns=1.0, mx=-1.1, hold=0.55, side=0.0):
    ax = {"RY": 0.0, "RZ": 0.0}
    spin = 360.0 * turns
    a = {axis: 0.0}
    b = {axis: spin}
    return inspect_step(name, [
        k(0.0, "smooth", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, MX=-0.5, MY=0, OX=-0.4, OY=0, GLOW=0),
        k(0.24, "out_cubic", TX=side, TY=lift, TZ=depth, RX=tilt, MX=mx, MY=-0.35, OX=mx + 0.1, OY=0.5, GLOW=0.2, **a),
        k(0.34, "linear", TX=side, TY=lift + 0.01, TZ=depth, RX=tilt, MX=mx, MY=-0.35, OX=mx + 0.1, OY=0.5, GLOW=0.3, **a),
        k(0.72, "in_out", TX=side, TY=lift, TZ=depth, RX=tilt, MX=mx, MY=0.25, OX=mx + 0.1, OY=0.2, GLOW=1.0, **b),
        k(0.80, "linear", TX=side, TY=lift, TZ=depth, RX=tilt, MX=mx, MY=0.25, OX=mx + 0.1, OY=0.2, GLOW=0.8, **b),
        k(1.0, "in_out", TX=0, TY=0, TZ=0, RX=0, RY=(spin if axis == "RY" else 0), RZ=(spin if axis == "RZ" else 0), MX=-0.5, MY=0, OX=-0.4, OY=0, GLOW=0),
    ])


W = {}

# ------------------------------------------------------------------ 鯨骨の大剣: 重い、頭上から断つ
W["whalebone_greatsword"] = weapon(
    "whalebone_greatsword", "鯨骨の大剣: 一拍止めて断ち下ろし、返しの薙ぎ", "GREATSWORD", 0.5, 0.5, 14,
    {"mx": -0.7, "my": -0.12, "ox": -0.65, "oy": 0.35, "sway": 0.035, "hz": 0.28},
    [
        step("断ち下ろし", 3, 0.9, [
            k(0.0, "smooth", MX=-0.7, MY=-0.12, OX=-0.65, OY=0.35, TRAIL=0, GLOW=0),
            k(0.12, "out", MX=-1.0, OX=-0.9, BY=0.1, LEAN=3, TY=-0.06, TZ=0.04, RX=10),
            k(0.34, "in_out", MX=-2.8, MY=0.2, MZ=0.35, OX=-2.6, OY=-0.2, OZ=-0.4, BY=0.45, LEAN=-7, TX=0.05, TY=0.42, TZ=0.05, RX=6, RY=0, RZ=10, TRAIL=0),
            k(0.42, "linear", MX=-2.86, MY=0.2, MZ=0.35, OX=-2.65, OY=-0.2, OZ=-0.4, BY=0.46, LEAN=-8, TX=0.05, TY=0.45, TZ=0.05, RX=7, RY=0, RZ=10, TRAIL=0),
            k(0.5, "in_cubic", hit=True, MX=-0.3, MY=-0.45, MZ=-0.1, OX=-0.5, OY=0.5, OZ=0, BY=-0.5, LEAN=15, TX=-0.1, TY=-0.08, TZ=-0.55, RX=-72, RY=-12, RZ=15, TRAIL=1, GLOW=1),
            k(0.6, "out", MX=0.05, MY=-0.4, MZ=-0.05, OX=-0.2, OY=0.4, BY=-0.32, LEAN=12, TX=-0.08, TY=-0.13, TZ=-0.62, RX=-82, RY=-8, RZ=10, TRAIL=0.5, GLOW=0.6),
            k(0.78, "smooth", MX=-0.35, MY=-0.2, OX=-0.4, OY=0.4, BY=-0.1, LEAN=5, TY=-0.04, TZ=-0.2, RX=-36, RY=-3, RZ=4, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.7, MY=-0.12, OX=-0.65, OY=0.35, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ]),
        step("返し薙ぎ", 2, 0.55, [
            k(0.0, "smooth", MX=-0.7, MY=-0.12, OX=-0.65, OY=0.35, TRAIL=0),
            k(0.24, "in_out", MX=-1.0, MY=-0.95, MZ=0.2, OX=-0.95, OY=0.1, BY=-0.5, LEAN=2, TX=-0.14, TY=-0.04, TZ=0.05, RX=4, RY=16, RZ=-22, TRAIL=0),
            k(0.34, "linear", MX=-1.02, MY=-1.0, MZ=0.2, OX=-0.97, OY=0.1, BY=-0.52, LEAN=2, TX=-0.15, TY=-0.04, TZ=0.06, RX=4, RY=18, RZ=-24, TRAIL=0),
            k(0.5, "in_cubic", hit=True, MX=-1.3, MY=1.0, MZ=-0.05, OX=-1.25, OY=0.9, BY=0.55, LEAN=8, TX=0.28, TY=0.02, TZ=-0.45, RX=-20, RY=-38, RZ=28, TRAIL=1, GLOW=0.9),
            k(0.62, "out", MX=-1.35, MY=1.25, OX=-1.3, OY=1.1, BY=0.65, LEAN=6, TX=0.34, TY=0.04, TZ=-0.42, RX=-18, RY=-48, RZ=30, TRAIL=0.5, GLOW=0.5),
            k(0.8, "smooth", MX=-0.9, MY=0.4, OX=-0.8, OY=0.6, BY=0.2, LEAN=2, TX=0.1, TZ=-0.15, RY=-15, RZ=10, TRAIL=0, GLOW=0.1),
            k(1.0, "out", MX=-0.7, MY=-0.12, OX=-0.65, OY=0.35, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.1, 0.22),
    ],
    inspect("大剣をかざす", 0.28, -0.12, -8.0, "RY", 1.0, -1.15, side=-0.1),
)

# ------------------------------------------------------------------ 番兵長の鉾: 大きな薙ぎ二つ + 突き
W["captain_pike"] = weapon(
    "captain_pike", "番兵長の鉾: 右から左へ薙ぎ、返して薙ぎ、肩ごと突き", "PIKE", 0.45, 0.8, 14,
    {"mx": -1.15, "my": -0.1, "ox": -1.2, "oy": 0.55, "sway": 0.02, "hz": 0.3},
    [
        step("右薙ぎ", 2, 0.45, [
            k(0.0, "smooth", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, TRAIL=0),
            k(0.3, "in_out", MX=-1.2, MY=1.05, OX=-1.2, OY=1.4, BY=0.55, LEAN=-3, TX=0.28, TY=0.02, TZ=0.05, RX=2, RY=35, TRAIL=0),
            k(0.38, "linear", MX=-1.2, MY=1.1, OX=-1.2, OY=1.45, BY=0.58, TX=0.3, TZ=0.05, RY=38, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-1.2, MY=-1.1, OX=-1.2, OY=-0.3, BY=-0.7, LEAN=6, TX=-0.35, TZ=-0.28, RY=-42, TRAIL=1),
            k(0.58, "out", MX=-1.15, MY=-1.3, OX=-1.2, OY=-0.4, BY=-0.8, LEAN=5, TX=-0.42, TZ=-0.25, RY=-52, TRAIL=0.4),
            k(0.8, "smooth", MX=-1.15, MY=-0.5, OX=-1.2, OY=0.3, BY=-0.3, TX=-0.12, TZ=-0.1, RY=-18, TRAIL=0),
            k(1.0, "out", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, BY=0, LEAN=0, TX=0, TZ=0, RY=0),
        ]),
        step("左返し薙ぎ", 2, 0.45, [
            k(0.0, "smooth", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, TRAIL=0),
            k(0.3, "in_out", MX=-1.2, MY=-1.2, OX=-1.2, OY=-0.4, BY=-0.7, LEAN=-2, TX=-0.38, TZ=0.05, RY=-45, TRAIL=0),
            k(0.38, "linear", MX=-1.2, MY=-1.25, OX=-1.2, OY=-0.45, BY=-0.74, TX=-0.4, TZ=0.05, RY=-48, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-1.2, MY=1.15, OX=-1.2, OY=1.5, BY=0.65, LEAN=6, TX=0.36, TZ=-0.28, RY=40, TRAIL=1),
            k(0.58, "out", MX=-1.15, MY=1.3, OX=-1.2, OY=1.6, BY=0.75, LEAN=5, TX=0.42, TZ=-0.25, RY=50, TRAIL=0.4),
            k(0.8, "smooth", MX=-1.15, MY=0.5, OX=-1.2, OY=1.0, BY=0.3, TX=0.12, TZ=-0.1, RY=18, TRAIL=0),
            k(1.0, "out", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, BY=0, LEAN=0, TX=0, TZ=0, RY=0),
        ]),
        step("肩ごと突き", 2, 0.6, [
            k(0.0, "smooth", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, TRAIL=0, PZ=0),
            k(0.28, "in_out", MX=-1.0, MY=0.25, OX=-1.0, OY=0.95, PZ=3.5, BY=0.4, LEAN=-4, TY=0.04, TZ=0.28, RX=6, TRAIL=0),
            k(0.38, "linear", MX=-1.0, MY=0.25, OX=-1.0, OY=0.95, PZ=3.8, BY=0.42, LEAN=-4, TY=0.04, TZ=0.3, RX=6, TRAIL=0),
            k(0.45, "snap", hit=True, MX=-1.55, MY=-0.25, OX=-1.5, OY=0.6, PZ=-5.0, BY=-0.4, LEAN=9, TY=-0.05, TZ=-0.7, RX=-4, TRAIL=1),
            k(0.55, "out", MX=-1.5, MY=-0.2, OX=-1.45, OY=0.62, PZ=-5.2, BY=-0.36, LEAN=9, TY=-0.05, TZ=-0.72, RX=-4, TRAIL=0.3),
            k(0.8, "smooth", MX=-1.25, MY=-0.12, OX=-1.25, OY=0.55, PZ=-1.5, BY=-0.1, LEAN=3, TZ=-0.2, TRAIL=0),
            k(1.0, "out", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, PZ=0, BY=0, LEAN=0, TY=0, TZ=0, RX=0),
        ]),
    ],
    inspect("鉾を立てて回す", 0.12, -0.1, 6.0, "RZ", 1.0, -1.25, side=0.05),
)

# ------------------------------------------------------------------ 潮殻の三叉槍: 回してから突く
W["tide_shell_trident"] = weapon(
    "tide_shell_trident", "潮殻の三叉槍: 回して突く、引いて素早い突き", "PIKE", 0.4, 0.6, 12,
    {"mx": -1.1, "my": -0.15, "ox": -1.15, "oy": 0.5, "sway": 0.025, "hz": 0.4},
    [
        step("回し突き", 2, 0.5, [
            k(0.0, "smooth", MX=-1.1, MY=-0.15, OX=-1.15, OY=0.5, TRAIL=0, GLOW=0, RZ=0),
            k(0.12, "out", MX=-1.0, MY=0.5, OX=-1.0, OY=0.9, PZ=1.5, BY=0.4, TY=0.06, TZ=0.12, RZ=60),
            k(0.3, "in_out", MX=-1.0, MY=0.55, OX=-1.0, OY=1.0, PZ=3.0, BY=0.55, TY=0.08, TZ=0.25, RZ=220, TRAIL=0.6, GLOW=0.4),
            k(0.4, "snap", hit=True, MX=-1.5, MY=-0.15, OX=-1.45, OY=0.55, PZ=-4.5, BY=-0.4, LEAN=6, TY=-0.04, TZ=-0.65, RX=-3, RZ=360, TRAIL=1, GLOW=1),
            k(0.55, "out", MX=-1.45, MY=-0.12, OX=-1.4, OY=0.55, PZ=-4.8, BY=-0.35, LEAN=6, TZ=-0.7, RZ=360, TRAIL=0.3, GLOW=0.6),
            k(0.8, "smooth", MX=-1.2, MY=-0.12, OX=-1.2, OY=0.5, PZ=-1.2, BY=-0.1, LEAN=2, TZ=-0.2, RZ=360, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-1.1, MY=-0.15, OX=-1.15, OY=0.5, PZ=0, BY=0, LEAN=0, TY=0, TZ=0, RX=0, RZ=360, GLOW=0),
        ], 0.08, 0.2),
        step("引き突き", 1, 0.3, [
            k(0.0, "smooth", MX=-1.1, MY=-0.15, OX=-1.15, OY=0.5, TRAIL=0),
            k(0.25, "in_out", MX=-1.05, MY=0.1, OX=-1.05, OY=0.7, PZ=2.5, BY=0.3, TZ=0.2, RX=3, TRAIL=0),
            k(0.4, "snap", hit=True, MX=-1.5, MY=-0.15, OX=-1.45, OY=0.55, PZ=-4.2, BY=-0.3, LEAN=5, TZ=-0.62, RX=-3, TRAIL=0.9),
            k(0.55, "out", MX=-1.45, MY=-0.12, PZ=-4.4, BY=-0.28, TZ=-0.66, TRAIL=0.2),
            k(1.0, "out", MX=-1.1, MY=-0.15, OX=-1.15, OY=0.5, PZ=0, BY=0, LEAN=0, TZ=0, RX=0, TRAIL=0),
        ], 0.08, 0.22),
    ],
    inspect("三叉槍を回す", 0.14, -0.1, 4.0, "RZ", 2.0, -1.2),
)

# ------------------------------------------------------------------ 珊瑚の銛: 担いで放るように突く
W["coral_harpoon"] = weapon(
    "coral_harpoon", "珊瑚の銛: 肩へ担いで一直線に突き、下から跳ね上げる突き", "PIKE", 0.43, 0.5, 12,
    {"mx": -0.95, "my": -0.2, "ox": -1.0, "oy": 0.55, "sway": 0.02, "hz": 0.33},
    [
        step("投げ突き", 2, 0.5, [
            k(0.0, "smooth", MX=-0.95, MY=-0.2, OX=-1.0, OY=0.55, TRAIL=0),
            k(0.2, "out", MX=-1.7, MY=0.65, OX=-1.5, OY=0.9, PZ=2.5, BY=0.5, LEAN=-3, TX=0.14, TY=0.2, TZ=0.2, RX=14, RY=10, RZ=-4),
            k(0.34, "in_out", MX=-1.85, MY=0.7, OX=-1.6, OY=1.0, PZ=3.4, BY=0.6, LEAN=-4, TX=0.16, TY=0.24, TZ=0.26, RX=16, RY=12, RZ=-6, TRAIL=0),
            k(0.43, "snap", hit=True, MX=-1.45, MY=-0.25, OX=-1.4, OY=0.55, PZ=-5.0, BY=-0.45, LEAN=9, TX=-0.04, TY=-0.06, TZ=-0.78, RX=-10, RY=-6, RZ=2, TRAIL=1),
            k(0.57, "out", MX=-1.4, MY=-0.2, OX=-1.35, OY=0.55, PZ=-5.3, BY=-0.4, LEAN=9, TY=-0.08, TZ=-0.82, RX=-12, TRAIL=0.3),
            k(0.82, "smooth", MX=-1.1, MY=-0.2, OX=-1.1, OY=0.55, PZ=-1.5, BY=-0.1, LEAN=2, TZ=-0.25, RX=-4, TRAIL=0),
            k(1.0, "out", MX=-0.95, MY=-0.2, OX=-1.0, OY=0.55, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0),
        ], 0.1, 0.2),
        step("跳ね上げ突き", 1, 0.35, [
            k(0.0, "smooth", MX=-0.95, MY=-0.2, OX=-1.0, OY=0.55, TRAIL=0),
            k(0.24, "in_out", MX=-0.55, MY=0.3, OX=-0.6, OY=0.8, PZ=2.0, BY=0.3, TY=-0.16, TZ=0.12, RX=22, TRAIL=0),
            k(0.43, "snap", hit=True, MX=-1.7, MY=-0.2, OX=-1.65, OY=0.5, PZ=-4.2, BY=-0.3, LEAN=5, TY=0.1, TZ=-0.62, RX=-26, TRAIL=0.9),
            k(0.58, "out", MX=-1.75, MY=-0.15, OX=-1.7, OY=0.5, PZ=-4.4, TY=0.14, TZ=-0.66, RX=-30, TRAIL=0.2),
            k(1.0, "out", MX=-0.95, MY=-0.2, OX=-1.0, OY=0.55, PZ=0, BY=0, LEAN=0, TY=0, TZ=0, RX=0, TRAIL=0),
        ], 0.08, 0.22),
    ],
    inspect("銛をかかげる", 0.2, -0.12, -14.0, "RZ", 1.0, -1.3),
)

# ------------------------------------------------------------------ 番兵の鉤槍: 引っかけて引き寄せる
W["warden_glaive"] = weapon(
    "warden_glaive", "番兵の鉤槍: 外へ薙いで鉤をかけ、引き寄せる", "GLAIVE", 0.45, 0.8, 12,
    {"mx": -1.15, "my": -0.1, "ox": -1.2, "oy": 0.55, "sway": 0.025, "hz": 0.35},
    [
        step("鉤かけ引き", 2, 0.5, [
            k(0.0, "smooth", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, TRAIL=0, PZ=0),
            k(0.26, "in_out", MX=-1.2, MY=1.0, OX=-1.2, OY=1.6, BY=0.6, LEAN=-4, TX=0.3, TZ=0.1, RY=-30, TRAIL=0),
            k(0.36, "linear", MX=-1.2, MY=1.04, OX=-1.2, OY=1.64, BY=0.62, TX=0.32, TZ=0.1, RY=-32, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-1.2, MY=-0.9, OX=-1.2, OY=-0.4, BY=-0.7, LEAN=6, TX=-0.35, TZ=-0.28, RY=40, TRAIL=1),
            k(0.6, "in_out", MX=-1.0, MY=-0.7, OX=-1.0, OY=-0.1, PZ=2.8, BY=-0.45, LEAN=-2, TX=-0.2, TZ=0.12, RY=22, TRAIL=0.2),
            k(0.82, "smooth", MX=-1.1, MY=-0.3, OX=-1.15, OY=0.3, PZ=1.0, BY=-0.15, TX=-0.06, TZ=0.02, RY=6, TRAIL=0),
            k(1.0, "out", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, PZ=0, BY=0, LEAN=0, TX=0, TZ=0, RY=0),
        ]),
        step("返し鉤", 2, 0.4, [
            k(0.0, "smooth", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, TRAIL=0),
            k(0.26, "in_out", MX=-1.0, MY=-0.9, OX=-1.0, OY=-0.3, BY=-0.5, TX=-0.3, TY=-0.04, RY=36, RZ=-12, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-1.4, MY=0.9, OX=-1.4, OY=1.4, BY=0.6, LEAN=5, TX=0.3, TY=0.06, TZ=-0.3, RY=-34, RZ=14, TRAIL=1),
            k(0.62, "in_out", MX=-1.2, MY=0.5, OX=-1.2, OY=0.9, PZ=2.2, BY=0.3, TX=0.14, TZ=0.1, RY=-16, TRAIL=0.2),
            k(1.0, "out", MX=-1.15, MY=-0.1, OX=-1.2, OY=0.55, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RY=0, RZ=0, TRAIL=0),
        ]),
    ],
    inspect("鉤槍を傾ける", 0.16, -0.1, 10.0, "RZ", 1.0, -1.2),
)

# ------------------------------------------------------------------ 水路の牙: 速い X の斬り
W["waterway_fang"] = weapon(
    "waterway_fang", "水路の牙: 右上から左下へ、返して X、突き抜ける", "FANG", 0.4, 0.5, 8,
    {"mx": -0.5, "my": -0.1, "sway": 0.03, "hz": 0.5},
    [
        step("斜め斬り (／)", 1, 0.2, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0),
            k(0.22, "out", MX=-1.5, MY=0.7, MZ=0.2, BY=0.3, TX=0.14, TY=0.26, TZ=0.04, RX=5, RY=0, RZ=-18, TRAIL=0),
            k(0.4, "snap", hit=True, MX=-0.9, MY=-0.85, MZ=-0.2, BY=-0.4, TX=-0.26, TY=-0.12, TZ=-0.26, RX=-40, RY=30, RZ=38, TRAIL=1),
            k(0.58, "out", MX=-0.7, MY=-0.4, BY=-0.2, TX=-0.12, TY=-0.04, TZ=-0.1, RX=-16, RY=10, RZ=12, TRAIL=0.3),
            k(1.0, "out", MX=-0.5, MY=-0.1, MZ=0, BY=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0),
        ], 0.06, 0.15),
        step("返し斬り (＼)", 1, 0.2, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0),
            k(0.22, "out", MX=-1.4, MY=-0.9, MZ=-0.2, BY=-0.3, TX=-0.18, TY=0.2, TZ=0.04, RX=5, RZ=22, TRAIL=0),
            k(0.4, "snap", hit=True, MX=-0.9, MY=0.9, MZ=0.2, BY=0.4, TX=0.26, TY=-0.1, TZ=-0.26, RX=-38, RY=-30, RZ=-38, TRAIL=1),
            k(0.58, "out", MX=-0.7, MY=0.4, BY=0.2, TX=0.12, TY=-0.04, TZ=-0.1, RX=-14, RY=-10, RZ=-12, TRAIL=0.3),
            k(1.0, "out", MX=-0.5, MY=-0.1, MZ=0, BY=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0),
        ], 0.06, 0.15),
        step("突き抜け", 2, 0.3, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0, PZ=0),
            k(0.25, "in_out", MX=-0.45, MY=0.2, BY=0.3, PZ=1.2, TX=0.1, TZ=0.12, RX=4, RZ=-8, TRAIL=0),
            k(0.4, "snap", hit=True, MX=-1.35, MY=-0.15, BY=-0.35, PZ=-3.0, LEAN=4, TX=-0.04, TY=0.02, TZ=-0.5, RX=-10, RZ=6, TRAIL=0.9, GLOW=0.8),
            k(0.6, "out", MX=-1.2, MY=-0.1, BY=-0.2, PZ=-3.0, TZ=-0.52, TRAIL=0.2, GLOW=0.3),
            k(1.0, "out", MX=-0.5, MY=-0.1, BY=0, PZ=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.18),
    ],
    inspect("牙を指先で返す", 0.22, -0.1, -20.0, "RZ", 2.0, -0.9, side=-0.05),
)

# ------------------------------------------------------------------ 雷駆の爪: 雷の連続突き
W["raiju_claw"] = weapon(
    "raiju_claw", "雷駆の爪: 右、左、跳ね上げの雷突き", "CLAW", 0.4, 0.5, 8,
    {"mx": -0.5, "my": -0.1, "sway": 0.04, "hz": 0.6},
    [
        step("雷突き (右)", 1, 0.2, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.2, "out", MX=-0.7, MY=0.3, BY=0.25, TX=0.1, TY=0.02, TZ=0.12, RX=3, RZ=-10, TRAIL=0, GLOW=0.3),
            k(0.4, "snap", hit=True, MX=-1.35, MY=-0.15, BY=-0.3, LEAN=3, TX=-0.04, TZ=-0.5, RX=-8, RY=4, RZ=6, TRAIL=1, GLOW=1),
            k(0.56, "out", MX=-1.2, MY=-0.1, BY=-0.2, TZ=-0.52, TRAIL=0.3, GLOW=0.5),
            k(1.0, "out", MX=-0.5, MY=-0.1, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.15),
        step("雷突き (左)", 1, 0.2, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.2, "out", MX=-0.7, MY=-0.55, BY=-0.25, TX=-0.14, TY=0.02, TZ=0.12, RX=3, RZ=14, TRAIL=0, GLOW=0.3),
            k(0.4, "snap", hit=True, MX=-1.3, MY=0.2, BY=0.3, LEAN=3, TX=0.06, TZ=-0.5, RX=-8, RY=-6, RZ=-8, TRAIL=1, GLOW=1),
            k(0.56, "out", MX=-1.15, MY=0.15, BY=0.2, TZ=-0.52, TRAIL=0.3, GLOW=0.5),
            k(1.0, "out", MX=-0.5, MY=-0.1, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.15),
        step("雷の跳ね上げ", 2, 0.35, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.25, "in_out", MX=-0.3, MY=0.2, BY=0.3, TY=-0.2, TZ=0.1, RX=16, RZ=-8, TRAIL=0, GLOW=0.4),
            k(0.4, "snap", hit=True, MX=-1.9, MY=-0.1, BY=-0.3, LEAN=4, TY=0.18, TZ=-0.42, RX=-34, RZ=8, TRAIL=1, GLOW=1),
            k(0.6, "out", MX=-1.8, MY=-0.08, BY=-0.2, TY=0.2, TZ=-0.4, RX=-36, TRAIL=0.2, GLOW=0.5),
            k(1.0, "out", MX=-0.5, MY=-0.1, BY=0, LEAN=0, TY=0, TZ=0, RX=0, RZ=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.2),
    ],
    inspect("爪に雷を走らせる", 0.2, -0.1, -12.0, "RZ", 1.0, -0.9),
)

# ------------------------------------------------------------------ 潮呼びの爪: 引き裂き + 挟み切り
W["tidecaller_claw"] = weapon(
    "tidecaller_claw", "潮呼びの爪: 斜めに引き裂き、左右から挟み切る", "CLAW", 0.45, 0.5, 12,
    {"mx": -0.5, "my": -0.1, "sway": 0.03, "hz": 0.4},
    [
        step("引き裂き", 2, 0.5, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0),
            k(0.3, "in_out", MX=-2.2, MY=0.5, MZ=0.3, BY=0.3, LEAN=-3, TY=0.35, TZ=0, RX=5, RZ=-8, TRAIL=0),
            k(0.38, "linear", MX=-2.25, MY=0.5, MZ=0.3, BY=0.32, LEAN=-3, TY=0.37, RX=5, RZ=-8, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-0.5, MY=-0.4, MZ=-0.3, BY=-0.4, LEAN=8, TY=-0.3, TZ=-0.3, RX=-50, RY=15, RZ=25, TRAIL=1, GLOW=0.8),
            k(0.6, "out", MX=-0.35, MY=-0.35, BY=-0.3, LEAN=7, TY=-0.34, TZ=-0.34, RX=-58, RY=14, RZ=28, TRAIL=0.4, GLOW=0.4),
            k(0.8, "smooth", MX=-0.45, MY=-0.15, BY=-0.1, LEAN=3, TY=-0.1, TZ=-0.1, RX=-20, RZ=8, TRAIL=0, GLOW=0.1),
            k(1.0, "out", MX=-0.5, MY=-0.1, MZ=0, BY=0, LEAN=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ]),
        step("挟み切り", 2, 0.45, [
            k(0.0, "smooth", MX=-0.5, MY=-0.1, TRAIL=0),
            k(0.3, "in_out", MX=-1.2, MY=0.9, MZ=0.4, BY=0.45, TX=0.3, TY=0.04, RX=0, RY=34, RZ=-30, TRAIL=0),
            k(0.45, "in_cubic", hit=True, MX=-1.1, MY=-0.9, MZ=-0.2, BY=-0.5, LEAN=5, TX=-0.3, TY=-0.04, TZ=-0.25, RX=-10, RY=-34, RZ=30, TRAIL=1, GLOW=0.9),
            k(0.6, "out", MX=-1.05, MY=-1.05, BY=-0.55, LEAN=4, TX=-0.34, TZ=-0.24, RY=-40, RZ=34, TRAIL=0.4, GLOW=0.4),
            k(1.0, "out", MX=-0.5, MY=-0.1, MZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ]),
    ],
    inspect("爪を開閉する", 0.2, -0.1, -10.0, "RY", 1.0, -0.95),
)

# ------------------------------------------------------------------ 余燼の戦斧: 引きずり上げて叩きつける
W["ember_war_axe"] = weapon(
    "ember_war_axe", "余燼の戦斧: 地を引きずり上げて叩きつけ、横へ割る", "AXE", 0.5, 0.5, 14,
    {"mx": -0.6, "my": -0.1, "sway": 0.03, "hz": 0.3},
    [
        step("引き上げ叩き", 3, 0.8, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.16, "in", MX=0.3, MY=0.1, BY=0.2, LEAN=6, TX=0.06, TY=-0.2, TZ=-0.1, RX=22, RZ=6, TRAIL=0, GLOW=0.3),
            k(0.36, "in_out", MX=-2.6, MY=0.12, MZ=0.2, BY=0.4, LEAN=-6, TX=0.04, TY=0.46, TZ=0.02, RX=6, RZ=8, TRAIL=0, GLOW=0.6),
            k(0.42, "linear", MX=-2.65, MY=0.12, MZ=0.2, BY=0.42, LEAN=-7, TY=0.48, RX=6, RZ=8, GLOW=0.7),
            k(0.5, "in_cubic", hit=True, MX=-0.1, MY=-0.2, MZ=-0.05, BY=-0.45, LEAN=13, TY=-0.34, TZ=-0.32, RX=-66, RZ=12, TRAIL=1, GLOW=1),
            k(0.62, "out", MX=0.15, MY=-0.1, BY=-0.3, LEAN=11, TY=-0.4, TZ=-0.36, RX=-72, RZ=10, TRAIL=0.4, GLOW=0.7),
            k(0.8, "smooth", MX=-0.3, MY=-0.1, BY=-0.1, LEAN=4, TY=-0.1, TZ=-0.12, RX=-26, RZ=4, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.6, MY=-0.1, MZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ]),
        step("横割り", 2, 0.5, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.3, "in_out", MX=-1.2, MY=-0.8, BY=-0.5, LEAN=-2, TX=-0.24, TY=0.06, TZ=0.04, RX=6, RY=20, RZ=-14, TRAIL=0, GLOW=0.3),
            k(0.5, "in_cubic", hit=True, MX=-1.1, MY=0.95, BY=0.55, LEAN=9, TX=0.3, TY=0, TZ=-0.4, RX=-26, RY=-34, RZ=20, TRAIL=1, GLOW=1),
            k(0.64, "out", MX=-1.1, MY=1.15, BY=0.65, LEAN=8, TX=0.36, TZ=-0.38, RX=-24, RY=-44, RZ=22, TRAIL=0.4, GLOW=0.5),
            k(0.82, "smooth", MX=-0.8, MY=0.4, BY=0.2, LEAN=3, TX=0.1, TZ=-0.1, RY=-12, RZ=6, TRAIL=0, GLOW=0.1),
            k(1.0, "out", MX=-0.6, MY=-0.1, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ]),
    ],
    inspect("戦斧の熱を見る", 0.24, -0.1, -6.0, "RY", 1.0, -1.0),
)

# ------------------------------------------------------------------ 鍛え手の大鎚: 一番重い、引きずり上げ + 叩きつけ
W["smith_hammer"] = weapon(
    "smith_hammer", "鍛え手の大鎚: 体ごと叩きつけて止まり、叩き上げ", "HAMMER", 0.55, 0.5, 16,
    {"mx": -0.6, "my": -0.1, "ox": -0.5, "oy": 0.3, "sway": 0.035, "hz": 0.25},
    [
        step("地ならし", 3, 1.0, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, OX=-0.5, OY=0.3, TRAIL=0, GLOW=0),
            k(0.2, "in", MX=0.25, MY=0.15, OX=0.2, OY=0.3, BY=0.2, LEAN=8, TX=0.04, TY=-0.22, TZ=-0.05, RX=20, TRAIL=0, GLOW=0.3),
            k(0.4, "in_out", MX=-3.0, MY=0.1, MZ=0.3, OX=-2.8, OY=-0.2, OZ=-0.3, BY=0.25, LEAN=-10, TY=0.42, TZ=0, RX=5, TRAIL=0, GLOW=0.6),
            k(0.48, "linear", MX=-3.05, MY=0.1, MZ=0.3, OX=-2.85, OY=-0.2, OZ=-0.3, BY=0.26, LEAN=-11, TY=0.44, RX=5, GLOW=0.7),
            k(0.55, "in_cubic", hit=True, MX=-0.05, MY=-0.3, MZ=-0.05, OX=-0.1, OY=0.5, OZ=0, BY=-0.22, LEAN=20, TY=-0.12, TZ=-0.5, RX=-65, TRAIL=1, GLOW=1),
            k(0.68, "linear", MX=0.0, MY=-0.3, OX=-0.1, OY=0.5, BY=-0.12, LEAN=18, TY=-0.1, TZ=-0.45, RX=-52, TRAIL=0.3, GLOW=0.8),
            k(0.85, "smooth", MX=-0.3, MY=-0.15, OX=-0.3, OY=0.4, BY=-0.05, LEAN=6, TY=-0.04, TZ=-0.15, RX=-18, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.6, MY=-0.1, MZ=0, OX=-0.5, OY=0.3, OZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, GLOW=0),
        ], 0.1, 0.22),
        step("叩き上げ", 2, 0.6, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, OX=-0.5, OY=0.3, TRAIL=0),
            k(0.3, "in_out", MX=0.2, MY=0.2, OX=0.1, OY=0.3, BY=0.25, LEAN=8, TY=-0.28, TZ=-0.05, RX=26, TRAIL=0),
            k(0.55, "snap", hit=True, MX=-2.0, MY=-0.2, OX=-1.9, OY=0.4, BY=-0.3, LEAN=-4, TY=0.24, TZ=-0.4, RX=-40, TRAIL=1, GLOW=0.9),
            k(0.7, "out", MX=-2.3, MY=-0.15, OX=-2.2, OY=0.4, BY=-0.2, LEAN=-6, TY=0.3, TZ=-0.35, RX=-46, TRAIL=0.3, GLOW=0.5),
            k(1.0, "out", MX=-0.6, MY=-0.1, OX=-0.5, OY=0.3, BY=0, LEAN=0, TY=0, TZ=0, RX=0, TRAIL=0, GLOW=0),
        ], 0.1, 0.25),
    ],
    inspect("大鎚の重さを量る", 0.14, -0.1, 4.0, "RY", 1.0, -1.0),
)

# ------------------------------------------------------------------ 灰の番人の火掻き: 短い素早い突き
W["thrall_poker"] = weapon(
    "thrall_poker", "灰の番人の火掻き: 短い突き、払い、ひねり突き", "POKER", 0.38, 0.5, 8,
    {"mx": -1.0, "my": -0.1, "sway": 0.03, "hz": 0.45},
    [
        step("突き", 1, 0.2, [
            k(0.0, "smooth", MX=-1.0, MY=-0.1, TRAIL=0, GLOW=0),
            k(0.2, "out", MX=-0.9, MY=0.15, PZ=2.2, BY=0.25, TZ=0.12, RX=6, TRAIL=0, GLOW=0.2),
            k(0.38, "snap", hit=True, MX=-1.5, MY=-0.2, PZ=-4.0, BY=-0.3, TZ=-0.5, RX=-3, TRAIL=0.8, GLOW=0.8),
            k(0.55, "out", MX=-1.4, MY=-0.15, PZ=-4.0, BY=-0.25, TZ=-0.52, TRAIL=0.2, GLOW=0.4),
            k(1.0, "out", MX=-1.0, MY=-0.1, PZ=0, BY=0, TZ=0, RX=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.18),
        step("払い", 1, 0.25, [
            k(0.0, "smooth", MX=-1.0, MY=-0.1, TRAIL=0),
            k(0.22, "out", MX=-1.1, MY=0.7, BY=0.4, TX=0.14, TZ=0.05, RY=18, RZ=-10, TRAIL=0),
            k(0.38, "snap", hit=True, MX=-1.2, MY=-0.8, BY=-0.5, TX=-0.2, TZ=-0.25, RY=-28, RZ=16, TRAIL=1, GLOW=0.8),
            k(0.55, "out", MX=-1.15, MY=-0.95, BY=-0.55, TX=-0.24, TZ=-0.22, RY=-34, RZ=18, TRAIL=0.2, GLOW=0.3),
            k(1.0, "out", MX=-1.0, MY=-0.1, BY=0, TX=0, TZ=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ], 0.06, 0.18),
        step("ひねり突き", 2, 0.3, [
            k(0.0, "smooth", MX=-1.0, MY=-0.1, TRAIL=0),
            k(0.24, "in_out", MX=-0.95, MY=0.35, PZ=2.6, BY=0.35, TZ=0.15, RX=6, RZ=-30, TRAIL=0),
            k(0.38, "snap", hit=True, MX=-1.55, MY=-0.2, PZ=-4.4, BY=-0.35, LEAN=4, TZ=-0.58, RX=-4, RZ=300, TRAIL=0.9, GLOW=1),
            k(0.58, "out", MX=-1.45, MY=-0.15, PZ=-4.4, BY=-0.3, TZ=-0.6, RZ=360, TRAIL=0.2, GLOW=0.4),
            k(1.0, "out", MX=-1.0, MY=-0.1, PZ=0, BY=0, LEAN=0, TZ=0, RX=0, RZ=360, TRAIL=0, GLOW=0),
        ], 0.06, 0.2),
    ],
    inspect("火掻きの先を見る", 0.18, -0.1, -12.0, "RZ", 1.0, -1.0),
)

# ------------------------------------------------------------------ 深淵: 鯨骨の大剣の古い形。もっと重い
W["abyss_greatsword"] = weapon(
    "abyss_greatsword", "深淵の大剣: 長い溜めから床を割る断ち下ろし、体ごと回る薙ぎ", "GREATSWORD", 0.55, 0.55, 16,
    {"mx": -0.7, "my": -0.15, "ox": -0.65, "oy": 0.4, "sway": 0.04, "hz": 0.22},
    [
        step("深淵の断ち", 3, 1.0, [
            k(0.0, "smooth", MX=-0.7, MY=-0.15, OX=-0.65, OY=0.4, TRAIL=0, GLOW=0),
            k(0.12, "out", MX=-0.9, OX=-0.8, BY=0.1, LEAN=4, TY=-0.1, TZ=0.06, RX=12, GLOW=0.2),
            k(0.36, "in_out", MX=-2.85, MY=0.25, MZ=0.4, OX=-2.7, OY=-0.25, OZ=-0.45, BY=0.5, LEAN=-9, TX=0.06, TY=0.5, TZ=0.08, RX=8, RZ=12, TRAIL=0, GLOW=0.7),
            k(0.47, "linear", MX=-2.92, MY=0.25, MZ=0.4, OX=-2.75, OY=-0.25, OZ=-0.45, BY=0.52, LEAN=-10, TX=0.06, TY=0.53, TZ=0.08, RX=9, RZ=12, TRAIL=0, GLOW=0.9),
            k(0.55, "in_cubic", hit=True, MX=-0.25, MY=-0.5, MZ=-0.1, OX=-0.45, OY=0.55, OZ=0, BY=-0.55, LEAN=18, TX=-0.1, TY=-0.14, TZ=-0.6, RX=-78, RY=-14, RZ=16, TRAIL=1, GLOW=1),
            k(0.68, "out", MX=0.1, MY=-0.45, OX=-0.15, OY=0.5, BY=-0.38, LEAN=15, TX=-0.08, TY=-0.2, TZ=-0.68, RX=-88, RY=-10, RZ=12, TRAIL=0.6, GLOW=0.8),
            k(0.84, "smooth", MX=-0.4, MY=-0.25, OX=-0.4, OY=0.45, BY=-0.12, LEAN=6, TY=-0.06, TZ=-0.25, RX=-40, RZ=5, TRAIL=0, GLOW=0.3),
            k(1.0, "out", MX=-0.7, MY=-0.15, OX=-0.65, OY=0.4, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.1, 0.22),
        step("深淵の回し薙ぎ", 3, 0.85, [
            k(0.0, "smooth", MX=-0.7, MY=-0.15, OX=-0.65, OY=0.4, TRAIL=0, GLOW=0),
            k(0.34, "in_out", MX=-1.1, MY=-1.1, MZ=0.2, OX=-1.05, OY=-0.4, BY=-0.75, LEAN=-2, TX=-0.2, TY=0.02, TZ=0.08, RX=4, RY=24, RZ=-26, TRAIL=0, GLOW=0.6),
            k(0.45, "linear", MX=-1.12, MY=-1.15, MZ=0.2, OX=-1.07, OY=-0.45, BY=-0.78, TX=-0.22, TZ=0.08, RX=4, RY=26, RZ=-28, GLOW=0.8),
            k(0.55, "in_cubic", hit=True, MX=-1.3, MY=1.15, MZ=-0.05, OX=-1.25, OY=1.1, BY=0.75, LEAN=10, TX=0.32, TZ=-0.5, RX=-18, RY=-44, RZ=30, TRAIL=1, GLOW=1),
            k(0.7, "out", MX=-1.35, MY=1.4, OX=-1.3, OY=1.3, BY=0.9, LEAN=8, TX=0.4, TZ=-0.46, RX=-16, RY=-56, RZ=32, TRAIL=0.6, GLOW=0.7),
            k(0.85, "smooth", MX=-0.9, MY=0.4, OX=-0.8, OY=0.6, BY=0.25, LEAN=3, TX=0.12, TZ=-0.15, RY=-16, RZ=10, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.7, MY=-0.15, OX=-0.65, OY=0.4, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.1, 0.22),
    ],
    inspect("深淵の大剣を掲げる", 0.3, -0.14, -6.0, "RY", 1.0, -1.2, side=-0.1),
)

# ------------------------------------------------------------------ 深淵の鈴杖: 溜めて放つ。長い溜めに光が貯まる
W["abyss_bell_staff"] = weapon(
    "abyss_bell_staff", "深淵の鈴杖: 掲げて溜め、地を突いて鈴を鳴らす", "POKER", 0.5, 0.6, 12,
    {"mx": -0.9, "my": -0.12, "ox": -0.8, "oy": 0.3, "sway": 0.03, "hz": 0.3},
    [
        step("鈴の一突き (溜めて放つ)", 2, 0.6, [
            k(0.0, "smooth", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, TRAIL=0, GLOW=0),
            k(0.16, "out", MX=-1.6, MY=0.1, OX=-1.4, OY=0.4, PZ=1.0, BY=0.2, LEAN=-2, TY=0.14, TZ=0.1, RX=8, GLOW=0.3),
            k(0.42, "in_out", MX=-2.5, MY=0.15, OX=-2.2, OY=0.2, PZ=2.0, BY=0.3, LEAN=-4, TY=0.34, TZ=0.08, RX=10, RZ=5, GLOW=0.8),
            k(0.46, "linear", MX=-2.55, MY=0.15, OX=-2.25, OY=0.2, PZ=2.0, BY=0.3, LEAN=-4, TY=0.36, TZ=0.08, RX=10, RZ=5, GLOW=1.0),
            k(0.5, "in_cubic", hit=True, MX=-1.2, MY=-0.1, OX=-1.1, OY=0.4, PZ=-3.0, BY=-0.3, LEAN=8, TY=-0.1, TZ=-0.45, RX=-38, RZ=0, TRAIL=0.7, GLOW=1),
            k(0.62, "out_back", MX=-1.1, MY=-0.1, OX=-1.0, OY=0.4, PZ=-3.2, BY=-0.28, LEAN=8, TY=-0.14, TZ=-0.5, RX=-44, TRAIL=0.2, GLOW=0.8),
            k(0.84, "smooth", MX=-1.0, MY=-0.12, OX=-0.9, OY=0.3, PZ=-0.8, BY=-0.08, LEAN=2, TY=-0.04, TZ=-0.14, RX=-14, TRAIL=0, GLOW=0.3),
            k(1.0, "out", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, PZ=0, BY=0, LEAN=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ], 0.1, 0.22),
        step("鈴の円", 1, 0.4, [
            k(0.0, "smooth", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, TRAIL=0, GLOW=0),
            k(0.3, "in_out", MX=-1.3, MY=1.0, OX=-1.2, OY=1.4, BY=0.55, LEAN=-2, TX=0.3, TY=0.06, TZ=0.05, RX=0, RY=28, RZ=-16, TRAIL=0, GLOW=0.5),
            k(0.5, "in_cubic", hit=True, MX=-1.3, MY=-1.0, OX=-1.2, OY=-0.3, BY=-0.65, LEAN=5, TX=-0.3, TY=0, TZ=-0.3, RX=-10, RY=-30, RZ=18, TRAIL=0.9, GLOW=1),
            k(0.66, "out", MX=-1.3, MY=-1.15, OX=-1.2, OY=-0.4, BY=-0.7, LEAN=4, TX=-0.34, TZ=-0.28, RY=-38, TRAIL=0.3, GLOW=0.6),
            k(1.0, "out", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ]),
        step("鈴の震え突き", 2, 0.5, [
            k(0.0, "smooth", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, TRAIL=0, GLOW=0),
            k(0.3, "in_out", MX=-1.0, MY=0.1, OX=-0.9, OY=0.5, PZ=2.5, BY=0.35, TZ=0.2, RX=5, GLOW=0.5),
            k(0.5, "snap", hit=True, MX=-1.55, MY=-0.15, OX=-1.45, OY=0.5, PZ=-4.2, BY=-0.35, LEAN=6, TZ=-0.62, RX=-4, TRAIL=0.8, GLOW=1),
            k(0.58, "spring", MX=-1.5, MY=-0.1, OX=-1.4, OY=0.5, PZ=-4.0, BY=-0.3, TZ=-0.58, RX=-2, TRAIL=0.3, GLOW=0.9),
            k(0.75, "smooth", MX=-1.2, MY=-0.12, OX=-1.1, OY=0.4, PZ=-1.5, BY=-0.1, TZ=-0.2, GLOW=0.4),
            k(1.0, "out", MX=-0.9, MY=-0.12, OX=-0.8, OY=0.3, PZ=0, BY=0, LEAN=0, TZ=0, RX=0, TRAIL=0, GLOW=0),
        ]),
    ],
    inspect("鈴杖を鳴らす", 0.2, -0.1, -8.0, "RY", 1.0, -1.05),
)

# ------------------------------------------------------------------ 深淵の灰鎌: 大きな弧で刈り、鉤で引き戻す
W["abyss_ash_scythe"] = weapon(
    "abyss_ash_scythe", "深淵の灰鎌: 低く構えて弧を刈り上げ、刃先で引き戻す", "AXE", 0.5, 0.8, 14,
    {"mx": -0.7, "my": -0.2, "ox": -0.7, "oy": 0.4, "sway": 0.03, "hz": 0.27},
    [
        step("刈り上げ", 2, 0.7, [
            k(0.0, "smooth", MX=-0.7, MY=-0.2, OX=-0.7, OY=0.4, TRAIL=0, GLOW=0),
            k(0.3, "in_out", MX=-0.35, MY=1.0, MZ=0.3, OX=-0.4, OY=1.4, BY=0.6, LEAN=6, TX=0.3, TY=-0.2, TZ=0.05, RX=10, RY=22, RZ=-24, TRAIL=0, GLOW=0.4),
            k(0.38, "linear", MX=-0.35, MY=1.05, MZ=0.3, OX=-0.4, OY=1.45, BY=0.62, LEAN=6, TX=0.32, TY=-0.22, TZ=0.05, RX=10, RY=24, RZ=-26, GLOW=0.5),
            k(0.5, "in_cubic", hit=True, MX=-1.9, MY=-0.7, MZ=-0.1, OX=-1.7, OY=-0.1, BY=-0.6, LEAN=-2, TX=-0.24, TY=0.3, TZ=-0.4, RX=-30, RY=-30, RZ=32, TRAIL=1, GLOW=1),
            k(0.64, "out", MX=-2.1, MY=-0.9, OX=-1.9, OY=-0.2, BY=-0.7, LEAN=-3, TX=-0.3, TY=0.36, TZ=-0.36, RX=-34, RY=-40, RZ=36, TRAIL=0.5, GLOW=0.6),
            k(0.82, "smooth", MX=-1.0, MY=-0.4, OX=-1.0, OY=0.2, BY=-0.25, LEAN=1, TX=-0.1, TY=0.1, TZ=-0.1, RX=-10, RY=-12, RZ=12, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.7, MY=-0.2, OX=-0.7, OY=0.4, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ]),
        step("引き戻し", 2, 0.5, [
            k(0.0, "smooth", MX=-0.7, MY=-0.2, OX=-0.7, OY=0.4, TRAIL=0),
            k(0.3, "in_out", MX=-1.9, MY=-0.5, OX=-1.7, OY=0.0, BY=-0.45, LEAN=-3, TX=-0.2, TY=0.3, TZ=-0.05, RX=-12, RY=-24, RZ=20, TRAIL=0, GLOW=0.4),
            k(0.5, "in_cubic", hit=True, MX=-0.6, MY=0.9, MZ=-0.1, OX=-0.6, OY=1.3, BY=0.55, LEAN=8, TX=0.28, TY=-0.1, TZ=-0.4, RX=-24, RY=28, RZ=-26, TRAIL=1, GLOW=1),
            k(0.66, "in_out", MX=-0.9, MY=0.5, OX=-0.9, OY=0.9, PZ=2.0, BY=0.25, LEAN=2, TX=0.12, TY=0.0, TZ=0.08, RY=14, RZ=-12, TRAIL=0.2, GLOW=0.5),
            k(1.0, "out", MX=-0.7, MY=-0.2, OX=-0.7, OY=0.4, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, TRAIL=0, GLOW=0),
        ]),
    ],
    inspect("鎌の刃を返す", 0.22, -0.1, -10.0, "RZ", 1.0, -1.05),
)

# ------------------------------------------------------------------ 弓: 引きの段 + 放したあとの返し (第一段は一人称の「はずみ」。三人称の反動は BowRecoil)
# strike = 0: 一撃を預けない (弓はバニラの経路)。steps[0] = 放した直後 0..1 の一人称の動き (fixedDuration tick)
W["thunder_wing_bow"] = weapon(
    "thunder_wing_bow", "雷翼の弓: 流れるように引き、放して弓を返す", "BOW", 0.0, 0.0, 0,
    {"sway": 0.03, "hz": 0.3},
    [
        step("放ちの返し", 0, 0.15, [
            k(0.0, "smooth", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=1),
            k(0.12, "out", TX=0, TY=0.04, TZ=0.1, RX=9, RY=0, RZ=-5, GLOW=1),
            k(0.4, "in_out", TX=-0.03, TY=0.06, TZ=0.05, RX=4, RY=-8, RZ=-70, GLOW=0.7),
            k(0.7, "spring", TX=0, TY=0.02, TZ=0.02, RX=1, RY=-3, RZ=-300, GLOW=0.3),
            k(1.0, "out", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=-360, GLOW=0),
        ], 0.04, 0.12),
    ],
    None,
    fixed=14,
)

W["abyss_storm_bow"] = weapon(
    "abyss_storm_bow", "深淵の雷弓: 重く引き絞り、放して弓が揺れる", "BOW", 0.0, 0.0, 0,
    {"sway": 0.035, "hz": 0.24},
    [
        step("放ちの揺れ", 0, 0.2, [
            k(0.0, "smooth", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=1),
            k(0.1, "out", TX=0, TY=0.05, TZ=0.14, RX=12, RY=0, RZ=-3, GLOW=1),
            k(0.35, "in_out", TX=-0.02, TY=0.07, TZ=0.08, RX=6, RY=-6, RZ=-14, GLOW=0.8),
            k(0.65, "spring", TX=0, TY=0.02, TZ=0.03, RX=2, RY=-2, RZ=6, GLOW=0.4),
            k(1.0, "out", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.04, 0.12),
    ],
    None,
    fixed=18,
)

# ------------------------------------------------------------------ 珊瑚の盾: 殴りつける (主の手に持ったとき)。副の手では、主の手の振りに合わせて小さく前へ出る (クライアント側)
W["coral_shield"] = weapon(
    "coral_shield", "珊瑚の盾: 肩から押し出して殴りつける", "SHIELD", 0.0, 0.0, 10,
    {"sway": 0.02, "hz": 0.3},
    [
        step("盾殴り", 0, 0.3, [
            k(0.0, "smooth", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, MX=-0.5, BY=0),
            k(0.28, "in_out", TX=-0.06, TY=0.02, TZ=0.1, RX=-8, RY=-6, RZ=4, MX=-0.4, BY=0.2),
            k(0.45, "snap", hit=True, TX=0.14, TY=0.04, TZ=-0.36, RX=-18, RY=14, RZ=-4, MX=-1.3, BY=-0.3),
            k(0.62, "out_back", TX=0.12, TY=0.04, TZ=-0.4, RX=-20, RY=16, RZ=-4, MX=-1.35, BY=-0.3),
            k(1.0, "out", TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, MX=-0.5, BY=0),
        ], 0.08, 0.2),
    ],
    inspect("盾の珊瑚を見る", 0.15, -0.1, 4.0, "RY", 1.0, -0.75),
    fixed=9,
)

# ------------------------------------------------------------------ 五章「深淵」の装備 (abyss/gear、v2 のメッシュ)
# 灯り番の剣: 片手の直剣。灯 (角灯) を揺らさないよう肩から小さく振る。袈裟に斬り下ろし → 返して横薙ぎ → 灯を前へ出して突く
W["abyss_lamp_sword"] = weapon(
    "abyss_lamp_sword", "灯り番の剣: 袈裟に斬り下ろし、返して薙ぎ、灯を掲げて突く", "GREATSWORD", 0.45, 0.5, 12,
    {"mx": -0.6, "my": -0.1, "ox": -0.35, "oy": 0.25, "sway": 0.03, "hz": 0.36},
    [
        step("袈裟斬り", 2, 0.45, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, OX=-0.35, OY=0.25, TRAIL=0, GLOW=0, LEAN=0),
            k(0.12, "out", MX=-0.8, MY=0.1, OX=-0.4, OY=0.3, BY=0.12, LEAN=2, TY=-0.04, TZ=0.04, RX=6, RZ=-4),
            k(0.3, "in_out", MX=-2.3, MY=0.55, MZ=0.3, OX=-0.6, OY=0.5, BY=0.42, LEAN=-5, TX=0.14, TY=0.34, TZ=0.04, RX=8, RZ=-16, TRAIL=0, GLOW=0.2),
            k(0.38, "linear", MX=-2.38, MY=0.58, MZ=0.32, OX=-0.62, OY=0.52, BY=0.45, LEAN=-6, TX=0.15, TY=0.36, TZ=0.05, RX=9, RZ=-17, GLOW=0.3),
            k(0.45, "in_cubic", hit=True, MX=-0.55, MY=-0.75, MZ=-0.2, OX=-0.3, OY=0.6, BY=-0.45, LEAN=10, TX=-0.22, TY=-0.12, TZ=-0.4, RX=-52, RY=22, RZ=30, TRAIL=1, GLOW=0.9),
            k(0.56, "out", MX=-0.35, MY=-0.85, MZ=-0.15, OX=-0.25, OY=0.6, BY=-0.38, LEAN=8, TX=-0.28, TY=-0.16, TZ=-0.42, RX=-60, RY=26, RZ=33, TRAIL=0.45, GLOW=0.5),
            k(0.76, "smooth", MX=-0.5, MY=-0.35, OX=-0.32, OY=0.35, BY=-0.12, LEAN=3, TX=-0.08, TY=-0.04, TZ=-0.12, RX=-18, RY=8, RZ=10, TRAIL=0, GLOW=0.15),
            k(1.0, "out", MX=-0.6, MY=-0.1, MZ=0, OX=-0.35, OY=0.25, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.08, 0.2),
        step("返し薙ぎ", 2, 0.4, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, OX=-0.35, OY=0.25, TRAIL=0, GLOW=0, LEAN=0),
            k(0.26, "in_out", MX=-1.05, MY=-0.95, MZ=0.15, OX=-0.5, OY=0.1, BY=-0.5, LEAN=-2, TX=-0.2, TY=0.02, TZ=0.05, RX=2, RY=20, RZ=-18, TRAIL=0, GLOW=0.2),
            k(0.36, "linear", MX=-1.08, MY=-1.02, MZ=0.15, OX=-0.52, OY=0.08, BY=-0.54, LEAN=-2, TX=-0.22, TY=0.02, TZ=0.06, RX=2, RY=22, RZ=-19, GLOW=0.3),
            k(0.45, "in_cubic", hit=True, MX=-1.2, MY=0.95, MZ=-0.05, OX=-0.45, OY=0.55, BY=0.55, LEAN=7, TX=0.3, TY=0.0, TZ=-0.4, RX=-16, RY=-38, RZ=24, TRAIL=1, GLOW=0.9),
            k(0.58, "out", MX=-1.22, MY=1.15, OX=-0.45, OY=0.6, BY=0.62, LEAN=5, TX=0.36, TZ=-0.38, RX=-14, RY=-46, RZ=26, TRAIL=0.45, GLOW=0.45),
            k(0.78, "smooth", MX=-0.85, MY=0.4, OX=-0.4, OY=0.4, BY=0.2, LEAN=2, TX=0.1, TZ=-0.12, RY=-14, RZ=8, TRAIL=0, GLOW=0.1),
            k(1.0, "out", MX=-0.6, MY=-0.1, OX=-0.35, OY=0.25, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.08, 0.2),
        step("灯の突き", 2, 0.45, [
            k(0.0, "smooth", MX=-0.6, MY=-0.1, OX=-0.35, OY=0.25, TRAIL=0, GLOW=0, PZ=0, LEAN=0),
            k(0.26, "in_out", MX=-1.1, MY=0.3, OX=-0.5, OY=0.5, PZ=2.6, BY=0.38, LEAN=-4, TX=0.08, TY=0.08, TZ=0.22, RX=6, RZ=-8, TRAIL=0, GLOW=0.3),
            k(0.36, "linear", MX=-1.12, MY=0.32, OX=-0.52, OY=0.52, PZ=2.9, BY=0.42, LEAN=-4, TX=0.08, TY=0.09, TZ=0.25, RX=7, RZ=-9, GLOW=0.4),
            k(0.45, "snap", hit=True, MX=-1.5, MY=-0.1, OX=-0.3, OY=0.6, PZ=-4.2, BY=-0.38, LEAN=7, TX=-0.02, TY=0.02, TZ=-0.62, RX=-6, RZ=4, TRAIL=0.9, GLOW=1),
            k(0.56, "out", MX=-1.45, MY=-0.08, OX=-0.3, OY=0.6, PZ=-4.4, BY=-0.34, LEAN=7, TZ=-0.65, RX=-6, TRAIL=0.25, GLOW=0.6),
            k(0.8, "smooth", MX=-0.9, MY=-0.1, OX=-0.33, OY=0.35, PZ=-1.2, BY=-0.1, LEAN=2, TZ=-0.18, RX=-2, TRAIL=0, GLOW=0.15),
            k(1.0, "out", MX=-0.6, MY=-0.1, OX=-0.35, OY=0.25, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ], 0.08, 0.2),
    ],
    inspect("灯をかざす", 0.22, -0.1, -8.0, "RY", 1.0, -1.05, side=-0.05),
)

# 沈め錨: 両手の重い錨。頭上へ担ぎ上げて真下へ沈める (床に錨を打つ) → 横から鉤をかけて引き寄せる
W["abyss_sinking_anchor"] = weapon(
    "abyss_sinking_anchor", "沈め錨: 担ぎ上げて真下へ沈め、横から鉤をかけて引き寄せる", "HAMMER", 0.55, 0.55, 16,
    {"mx": -0.6, "my": -0.12, "ox": -0.5, "oy": 0.32, "sway": 0.04, "hz": 0.22},
    [
        step("錨打ち", 3, 1.0, [
            k(0.0, "smooth", MX=-0.6, MY=-0.12, OX=-0.5, OY=0.32, TRAIL=0, GLOW=0),
            k(0.16, "in", MX=0.2, MY=0.12, OX=0.15, OY=0.3, BY=0.2, LEAN=8, TX=0.04, TY=-0.24, TZ=-0.04, RX=22, TRAIL=0, GLOW=0.3),
            k(0.38, "in_out", MX=-3.05, MY=0.12, MZ=0.3, OX=-2.85, OY=-0.2, OZ=-0.3, BY=0.3, LEAN=-11, TY=0.46, TZ=0.02, RX=6, RZ=4, TRAIL=0, GLOW=0.7),
            k(0.47, "linear", MX=-3.12, MY=0.12, MZ=0.3, OX=-2.9, OY=-0.2, OZ=-0.3, BY=0.31, LEAN=-12, TY=0.49, RX=6, RZ=4, GLOW=0.85),
            k(0.55, "in_cubic", hit=True, MX=0.0, MY=-0.32, MZ=-0.05, OX=-0.1, OY=0.5, OZ=0, BY=-0.25, LEAN=22, TY=-0.16, TZ=-0.52, RX=-70, TRAIL=1, GLOW=1),
            k(0.68, "linear", MX=0.05, MY=-0.3, OX=-0.08, OY=0.5, BY=-0.14, LEAN=20, TY=-0.14, TZ=-0.48, RX=-58, TRAIL=0.35, GLOW=0.85),
            k(0.85, "smooth", MX=-0.3, MY=-0.16, OX=-0.3, OY=0.4, BY=-0.05, LEAN=7, TY=-0.05, TZ=-0.16, RX=-20, TRAIL=0, GLOW=0.25),
            k(1.0, "out", MX=-0.6, MY=-0.12, MZ=0, OX=-0.5, OY=0.32, OZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ], 0.1, 0.22),
        step("鉤引き", 2, 0.7, [
            k(0.0, "smooth", MX=-0.6, MY=-0.12, OX=-0.5, OY=0.32, TRAIL=0, GLOW=0, PZ=0),
            k(0.3, "in_out", MX=-1.3, MY=1.05, MZ=0.2, OX=-1.2, OY=1.3, BY=0.65, LEAN=-4, TX=0.3, TY=0.06, TZ=0.08, RX=4, RY=-24, RZ=-10, TRAIL=0, GLOW=0.5),
            k(0.44, "linear", MX=-1.34, MY=1.1, MZ=0.2, OX=-1.24, OY=1.34, BY=0.7, LEAN=-5, TX=0.32, TY=0.06, TZ=0.09, RX=4, RY=-26, RZ=-11, GLOW=0.7),
            k(0.55, "in_cubic", hit=True, MX=-1.2, MY=-0.95, MZ=-0.1, OX=-1.1, OY=-0.4, BY=-0.7, LEAN=9, TX=-0.34, TY=-0.02, TZ=-0.4, RX=-22, RY=34, RZ=16, TRAIL=1, GLOW=1),
            k(0.7, "in_out", MX=-0.9, MY=-0.7, OX=-0.85, OY=-0.1, PZ=2.8, BY=-0.45, LEAN=-3, TX=-0.22, TY=-0.04, TZ=0.12, RX=-8, RY=22, RZ=10, TRAIL=0.3, GLOW=0.6),
            k(0.86, "smooth", MX=-0.7, MY=-0.3, OX=-0.6, OY=0.15, PZ=1.0, BY=-0.15, LEAN=1, TX=-0.06, TZ=0.03, RY=6, RZ=3, TRAIL=0, GLOW=0.2),
            k(1.0, "out", MX=-0.6, MY=-0.12, OX=-0.5, OY=0.32, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.1, 0.24),
    ],
    inspect("錨の重さを量る", 0.16, -0.1, 4.0, "RY", 1.0, -1.0),
)

# 鼓動の短剣: 速い 3 連。刺す → 逆に裂き上げる → 鼓動に合わせて二度押し込む (光が脈打つ)
W["abyss_pulse_dagger"] = weapon(
    "abyss_pulse_dagger", "鼓動の短剣: 刺し、裂き上げ、鼓動に合わせて押し込む", "FANG", 0.4, 0.5, 8,
    {"mx": -0.55, "my": -0.12, "sway": 0.035, "hz": 0.55},
    [
        step("刺し", 1, 0.2, [
            k(0.0, "smooth", MX=-0.55, MY=-0.12, TRAIL=0, GLOW=0, PZ=0, LEAN=0),
            k(0.18, "out", MX=-0.7, MY=0.25, PZ=1.6, BY=0.28, LEAN=-2, TX=0.06, TY=0.03, TZ=0.12, RX=4, RZ=-8, TRAIL=0, GLOW=0.2),
            k(0.3, "linear", MX=-0.72, MY=0.27, PZ=1.8, BY=0.3, LEAN=-2, TX=0.06, TY=0.03, TZ=0.14, RX=4, RZ=-9, GLOW=0.3),
            k(0.4, "snap", hit=True, MX=-1.4, MY=-0.15, PZ=-3.4, BY=-0.32, LEAN=5, TX=-0.03, TY=0.01, TZ=-0.52, RX=-8, RZ=5, TRAIL=0.9, GLOW=1),
            k(0.52, "out", MX=-1.3, MY=-0.12, PZ=-3.5, BY=-0.28, LEAN=4, TZ=-0.54, RX=-8, TRAIL=0.3, GLOW=0.5),
            k(0.74, "smooth", MX=-0.85, MY=-0.12, PZ=-1.0, BY=-0.08, LEAN=1, TZ=-0.14, RX=-2, TRAIL=0, GLOW=0.15),
            k(1.0, "out", MX=-0.55, MY=-0.12, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ], 0.06, 0.15),
        step("裂き上げ", 1, 0.25, [
            k(0.0, "smooth", MX=-0.55, MY=-0.12, TRAIL=0, GLOW=0, LEAN=0),
            k(0.2, "out", MX=-0.2, MY=-0.6, MZ=-0.2, BY=-0.3, LEAN=3, TX=-0.12, TY=-0.2, TZ=0.06, RX=18, RZ=16, TRAIL=0, GLOW=0.2),
            k(0.3, "linear", MX=-0.16, MY=-0.64, MZ=-0.22, BY=-0.33, LEAN=3, TX=-0.13, TY=-0.22, TZ=0.06, RX=20, RZ=17, GLOW=0.3),
            k(0.4, "snap", hit=True, MX=-1.75, MY=0.55, MZ=0.25, BY=0.4, LEAN=-2, TX=0.2, TY=0.2, TZ=-0.34, RX=-40, RY=-20, RZ=-26, TRAIL=1, GLOW=0.9),
            k(0.54, "out", MX=-1.85, MY=0.62, MZ=0.25, BY=0.44, LEAN=-3, TX=0.24, TY=0.24, TZ=-0.32, RX=-44, RY=-22, RZ=-28, TRAIL=0.35, GLOW=0.45),
            k(0.76, "smooth", MX=-1.0, MY=0.2, BY=0.12, LEAN=-1, TX=0.07, TY=0.06, TZ=-0.1, RX=-14, RY=-6, RZ=-8, TRAIL=0, GLOW=0.1),
            k(1.0, "out", MX=-0.55, MY=-0.12, MZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RY=0, RZ=0, GLOW=0),
        ], 0.06, 0.15),
        step("鼓動の押し込み", 2, 0.35, [
            k(0.0, "smooth", MX=-0.55, MY=-0.12, TRAIL=0, GLOW=0, PZ=0, LEAN=0),
            k(0.16, "out", MX=-0.9, MY=0.1, PZ=1.2, BY=0.2, LEAN=-2, TZ=0.1, RX=4, TRAIL=0, GLOW=0.6),
            k(0.24, "in_out", MX=-1.0, MY=0.12, PZ=1.4, BY=0.24, LEAN=-2, TZ=0.12, RX=5, GLOW=0.1),
            k(0.3, "linear", MX=-1.02, MY=0.14, PZ=1.6, BY=0.26, LEAN=-3, TZ=0.14, RX=5, GLOW=0.7),
            k(0.4, "snap", hit=True, MX=-1.45, MY=-0.12, PZ=-3.8, BY=-0.34, LEAN=6, TX=-0.02, TZ=-0.56, RX=-6, RZ=4, TRAIL=0.9, GLOW=1),
            k(0.5, "out", MX=-1.4, MY=-0.1, PZ=-3.2, BY=-0.3, LEAN=5, TZ=-0.48, RX=-5, TRAIL=0.3, GLOW=0.3),
            k(0.6, "out", MX=-1.48, MY=-0.12, PZ=-4.0, BY=-0.34, LEAN=6, TZ=-0.58, RX=-6, TRAIL=0.2, GLOW=0.9),
            k(0.8, "smooth", MX=-0.9, MY=-0.12, PZ=-1.0, BY=-0.08, LEAN=2, TZ=-0.15, RX=-2, TRAIL=0, GLOW=0.15),
            k(1.0, "out", MX=-0.55, MY=-0.12, PZ=0, BY=0, LEAN=0, TX=0, TY=0, TZ=0, RX=0, RZ=0, GLOW=0),
        ], 0.06, 0.18),
    ],
    inspect("短剣の鼓動を聞く", 0.22, -0.1, -18.0, "RZ", 1.0, -0.9, side=-0.05),
)

# 盾・弓は strike = 0 (サーバーは何も預けない)。盾は盾殴りの hit の key だけ持つ


def dump(d):
    lines = ["{"]
    head = [f'"{k_}": {json.dumps(d[k_], ensure_ascii=False)}' for k_ in d if k_ not in ("steps", "inspect")]
    lines.append("  " + ",\n  ".join(head) + ",")

    def stepjson(s, indent):
        pad = " " * indent
        out = [pad + "{"]
        meta = [f'"{k_}": {json.dumps(s[k_], ensure_ascii=False)}' for k_ in s if k_ != "keys"]
        out.append(pad + "  " + ", ".join(meta) + ",")
        out.append(pad + '  "keys": [')
        out.append(",\n".join(pad + "    " + json.dumps(kk, ensure_ascii=False) for kk in s["keys"]))
        out.append(pad + "  ]")
        out.append(pad + "}")
        return "\n".join(out)

    lines.append('  "steps": [')
    lines.append(",\n".join(stepjson(s, 4) for s in d["steps"]))
    lines.append("  ]" + ("," if "inspect" in d else ""))
    if "inspect" in d:
        lines.append('  "inspect":')
        lines.append(stepjson(d["inspect"], 4))
    lines.append("}")
    return "\n".join(lines) + "\n"


# ------------------------------------------------------------------ 仕上げ (2026-10-23 の見直し、docs/review/weapon_quality_2026-10-23.md)
# 一番よくできた鯨骨の大剣の段は「構え → 溜め (止め) → 当たり → 振り抜き → 収まり → 戻り」の 6〜8 キー。
# キーが 5 つの段 (溜めの止めも収まりもない) は、振りが等速に見えて軽い。次の 3 つを足す (当たる時刻は変えない):
#   溜めの止め: 溜めのキーと当たりの間 (溜め側 55%) に、溜めを少し深くした止めのキー (linear)。当たりまでが短くなり、打ちが鋭くなる
#   収まり: 振り抜きと戻りの間 (振り抜き側 45%) に、戻りの 70% まで戻したキー (out)。最後の戻りを smooth にして重さを出す
#   光と体: 段に GLOW が無ければ当たりで 0.6・振り抜きで 0.25。LEAN が無ければ溜め -2・当たり 4・振り抜き 3 (前へ体重を乗せる)
POLISH = {
    "waterway_fang": (0, 1, 2), "raiju_claw": (0, 1, 2), "thrall_poker": (0, 1, 2),
    "coral_harpoon": (1,), "tide_shell_trident": (1,), "warden_glaive": (1,), "tidecaller_claw": (1,),
    "smith_hammer": (1,), "abyss_ash_scythe": (1,), "abyss_bell_staff": (1,),
}
_ROT = ("RX", "RY", "RZ")


def _value_at(keys, ch, t):
    """チャンネル ch の t での値 (そのチャンネルを持つキーの間を直線で。持つキーが無ければ None)。"""
    pts = [(kk["t"], kk["v"][ch]) for kk in keys if ch in kk["v"]]
    if not pts:
        return None
    if t <= pts[0][0]:
        return pts[0][1]
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 0)
    return pts[-1][1]


def polish_step(s):
    ks = s["keys"]
    hi = next(j for j, kk in enumerate(ks) if kk.get("hit"))
    if hi < 2 or hi + 1 >= len(ks):
        return
    wind, hit = ks[hi - 1], ks[hi]
    chans = set().union(*(kk["v"] for kk in ks))
    # 光と体 (先に入れて、止め・収まりのキーにも値が乗るように)
    if "GLOW" not in chans:
        hit["v"]["GLOW"] = 0.6
        if hi + 1 < len(ks):
            ks[hi + 1]["v"]["GLOW"] = 0.25
        ks[-1]["v"]["GLOW"] = 0
        ks[0]["v"].setdefault("GLOW", 0)
    if "LEAN" not in chans:
        wind["v"]["LEAN"] = -2
        hit["v"]["LEAN"] = 4
        if hi + 1 < len(ks):
            ks[hi + 1]["v"]["LEAN"] = 3
        ks[-1]["v"]["LEAN"] = 0
        ks[0]["v"].setdefault("LEAN", 0)
    chans = set().union(*(kk["v"] for kk in ks))
    # 溜めの止め
    if hit["t"] - wind["t"] >= 0.12 and not (hi >= 2 and wind["t"] - ks[hi - 2]["t"] <= 0.1 and ks[hi - 2]["t"] > 0):
        th = round(wind["t"] + 0.55 * (hit["t"] - wind["t"]), 3)
        hv = {}
        for ch in wind["v"]:
            v = wind["v"][ch]
            if ch in ("TRAIL", "GLOW"):
                hv[ch] = v
            elif ch in _ROT or ch in ("BY", "LEAN", "PZ"):
                hv[ch] = round(v * 1.08, 4)
            else:
                base = ks[0]["v"].get(ch, 0.0)
                hv[ch] = round(base + (v - base) * 1.06, 4)
        ks.insert(hi, {"t": th, "e": "linear", "v": hv})
        hi += 1
    # 収まり
    if hi + 3 == len(ks):  # 当たり・振り抜き・戻りだけ
        fol, end = ks[hi + 1], ks[-1]
        ts = round(fol["t"] + 0.45 * (end["t"] - fol["t"]), 3)
        sv = {}
        for ch in fol["v"]:
            a = fol["v"][ch]
            b = _value_at(ks, ch, end["t"])
            b = a if b is None else b
            sv[ch] = round(a + 0.7 * (b - a), 4)
        ks.insert(hi + 2, {"t": ts, "e": "out", "v": sv})
        ks[-1]["e"] = "smooth"


def polish():
    for item, idx in POLISH.items():
        for i in idx:
            polish_step(W[item]["steps"][i])


def main():
    polish()
    os.makedirs(OUT, exist_ok=True)
    for item, d in W.items():
        with open(os.path.join(OUT, item + ".json"), "w", encoding="utf-8", newline="\n") as f:
            f.write(dump(d))
    print("wrote", len(W), "movesets to", os.path.normpath(OUT))


if __name__ == "__main__":
    sys.exit(main())
