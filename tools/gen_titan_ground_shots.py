# -*- coding: utf-8 -*-
"""巨神が地形にめり込まない (TitanGround) と、潮殻の乗り場の輪の、直す前後の撮影台本を書く。

使い方: python tools/gen_titan_ground_shots.py   → tools/shots/titan_ground.txt
撮る: SKYABYSS_SHOTS_SEED=4242 ./gradlew runShots -Pscript=tools/shots/titan_ground.txt   (種 4242。音は自動で 0)
絵: run/shots/titan_ground/shots/titan_ground/*.png → python tools/titan_ground_sheet.py → docs/review/previews/titan_ground_sheet.png

場所は SKYABYSS_SELFTEST_ONLY="titan ground" (titan/tide/TitanGroundSelfTest) の記録から取った、種 4242 の岸 (人の立つ点 P、旧い決まりが選ぶ休む場所 OLD、
新しい決まりが同じ人のために選ぶ休む場所 NEW)。「前」は /skyabyss dev titanground false (従来の高さ・場所・標)、「後」は true。
"""
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tools/shots/titan_ground.txt"
SEA = 63

# 場面 1: 丘の岸。人 P=(673,-323)。旧い決まりは 689,-282 (向き -113)、新しい決まりは 595,-302 (向き -15。6〜7 ブロック持ち上げて丘に触れない)
S1_P = (673, -323)
S1_OLD = (689, -282, -113)
S1_NEW = (595, -302, -15)
# 場面 2: 荒い岸。人 P=(547,633)。旧い決まりは 512,660 (向き -38)。同じ場所で測りを入れると、丘の上へ持ち上がる
S2_P = (547, 633)
S2_OLD = (512, 660, -38)

lines = []
add = lines.append


def mc_yaw(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


def look_from(cam, target):
    dx, dy, dz = target[0] - cam[0], target[1] - cam[1], target[2] - cam[2]
    h = math.hypot(dx, dz)
    return mc_yaw(dx, dz), math.degrees(math.atan2(-dy, h))


def camera(cam, target):
    yaw, pitch = look_from(cam, target)
    add(f"camera {cam[0]:.1f} {cam[1]:.1f} {cam[2]:.1f} {yaw:.1f} {pitch:.1f}")


def left(theta_deg):
    t = math.radians(theta_deg)
    return math.cos(t), math.sin(t)


def fwd(theta_deg):
    t = math.radians(theta_deg)
    return -math.sin(t), math.cos(t)


def entrance(shell, side):
    x, z, yaw = shell
    lx, lz = left(yaw)
    fx, fz = fwd(yaw)
    return (x + fx * -32 + lx * side, SEA, z + fz * -32 + lz * side)


def place(shell, guard):
    add(f"cmd skyabyss dev titanground {'true' if guard else 'false'}")
    add(f"cmd skyabyss dev titanground place tide {shell[0]} {shell[1]} {shell[2]}")
    add("wait 80")


def views(prefix, p, shell):
    """人の目の高さからの絵と、沖側から見下ろす絵。"""
    cx, cz = shell[0], shell[1]
    # 人の立つ岸の少し沖寄りの、木より高い所から (木の葉の中に入らない)
    dx0, dz0 = cx - p[0], cz - p[1]
    d0 = math.hypot(dx0, dz0) or 1.0
    camera((p[0] + dx0 / d0 * 25, SEA + 38, p[1] + dz0 / d0 * 25), (cx, SEA + 15, cz))
    add("hud off")
    add("chat clear")
    add("wait 20")
    add(f"shot {prefix}_view")
    dx, dz = cx - p[0], cz - p[1]
    d = math.hypot(dx, dz) or 1.0
    px, pz = -dz / d, dx / d
    camera((cx + px * 120, SEA + 55, cz + pz * 120), (cx, SEA + 8, cz))
    add("chat clear")
    add("wait 20")
    add(f"shot {prefix}_aerial")


def entrance_shots(prefix, shell, guard):
    side = 51 if guard else 33
    e = entrance(shell, side)
    lx, lz = left(shell[2])
    for dist in (10, 30):
        cam = (e[0] + lx * dist, SEA + (5 if dist <= 10 else 18), e[2] + lz * dist)
        camera(cam, (e[0], SEA + 8, e[2]))
        add("hud off")
        add("chat clear")
        add("wait 20")
        add(f"shot {prefix}_door{dist}")


add("# 巨神が地形にめり込まない・潮殻の乗り場の輪: 直す前後の絵 (tools/gen_titan_ground_shots.py が書く)")
add("# SKYABYSS_SHOTS_SEED=4242 ./gradlew runShots -Pscript=tools/shots/titan_ground.txt")
add("world titan_ground normal")
add("size 1280 720")
add("timeout 2700")
add("render 12")
add("clouds on")
add("hud off")
add("cmd gamerule doDaylightCycle false")
add("cmd gamerule doWeatherCycle false")
add("cmd time set 5000")
add("cmd weather clear")
add("cmd skyabyss cinematic stop")
add("cmd gamemode creative")
add("cmd skyabyss progress grant @p stone_whale")
# 浮いて撮る (重力 0)。標は観戦者には送られないので、観戦ではなく創造で
add("cmd attribute @p minecraft:generic.gravity base set 0")
add("wait 20")

# --- 場面 1: 丘の岸で、前は旧い場所・後は新しい場所
camera((S1_P[0] + 0.5, SEA + 8, S1_P[1] + 0.5), (S1_OLD[0], SEA + 20, S1_OLD[1]))
add("waitchunks 1200")
add("wait 40")
place(S1_OLD, False)
views("s1_before", S1_P, S1_OLD)
place(S1_NEW, True)
views("s1_after", S1_P, S1_NEW)

# --- 場面 2: 荒い岸で、同じ場所に前 (めり込む) と後 (持ち上がる)
camera((S2_P[0] + 0.5, SEA + 8, S2_P[1] + 0.5), (S2_OLD[0], SEA + 20, S2_OLD[1]))
add("waitchunks 1200")
add("wait 40")
place(S2_OLD, False)
views("s2_before", S2_P, S2_OLD)
place(S2_OLD, True)
views("s2_after", S2_P, S2_OLD)

# --- 場面 3: 乗り込み口。前は門の口に立つ標 (体に隠れる)、後は門の真外の水の上の輪。10 と 30 ブロックから
camera((S1_P[0] + 0.5, SEA + 8, S1_P[1] + 0.5), (S1_NEW[0], SEA + 20, S1_NEW[1]))
add("waitchunks 1200")
place(S1_NEW, False)
entrance_shots("s3_before", S1_NEW, False)
place(S1_NEW, True)
entrance_shots("s3_after", S1_NEW, True)

add("cmd skyabyss dev titanground true")
add("cmd kill @e[type=skyabyss:tide_shell,distance=..2000]")
add("wait 10")
add("quit")

OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(OUT)
