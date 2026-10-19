# 乗り込み口の目印・戻る光の A/B/C の撮影台本を書く (docs/review/markers/)。python tools/gen_marker_shots.py [部分] > tools/shots/markers.txt
# 部分: all (既定) / test
import sys

part = sys.argv[1] if len(sys.argv) > 1 else "all"
L = []
add = L.append
add("# 乗り込み口の目印・戻る光の選定用の絵 (tools/gen_marker_shots.py が書く)")
add("# ./gradlew runShots -Pscript=tools/shots/markers.txt  絵は run/shots/markers/shots/markers/")
add("world markers flat")
add("size 1280 720")
add("timeout 6000")
add("render 16")
add("clouds off")
add("cmd skyabyss cinematic stop")
add("cmd gamerule doDaylightCycle false")
add("cmd gamerule doWeatherCycle false")
add("cmd weather clear")
add("cmd time set 6000")
add("cmd gamemode spectator")
add("camera 0.5 -50 60 180 0")
add("waitchunks")
# 崖と口 (潮殻・余燼の横の口。正面は +z 向き)
add("cmd fill -16 -60 -16 16 -42 6 minecraft:deepslate_tiles")
add("cmd fill -12 -60 -16 12 -50 6 minecraft:stone_bricks")
add("cmd fill -4 -60 -9 4 -53 7 minecraft:air")
add("cmd fill -6 -60 5 6 -57 6 minecraft:air")
add("cmd skyabyss opening whale")
add("hud off")
add("wait 200")


def go(x, y, z, yaw, pitch):
    add(f"camera {x} {y} {z} {yaw} {pitch}")
    add("wait 6")


def zone(x, z):
    add(f"camera {x} -50 {z + 40} 180 0")
    add("waitchunks")


def setstyle(kind, s):
    add(f"marker {kind} {s}")
    add("wait 8")


def tm(day):
    add("cmd time set 6000" if day else "cmd time set 18000")
    add("wait 4")


STY = ["current", "a", "b", "c"]
if part in ("all", "test"):
    # 乗り込み口: 余燼の口 (横) の遠景・近景、昼・夜
    add("marker clear")
    add("marker fake board ember 0.5 -60 0.5 0")
    add("wait 20")
    for day in (True, False):
        dn = "day" if day else "night"
        tm(day)
        for s in STY:
            setstyle("board", s)
            go(0.5, -52, 112, 180, -6)
            add(f"shot board_ember_{s}_far_{dn}")
            go(0.5, -58.4, 22, 180, -8)
            add(f"shot board_ember_{s}_near_{dn}")
        if part == "test":
            break
if part == "all":
    # 各巨神の紋様 (夜・近景。A/B/C)
    tm(False)
    for titan, vertical in (("tide_shell", False), ("ember", False)):
        add("marker clear")
        add(f"marker fake board {titan} 0.5 -60 0.5 0")
        add("wait 20")
        for s in STY[1:]:
            setstyle("board", s)
            go(0.5, -58.4, 22, 180, -8)
            add(f"shot board_{titan}_{s}_near_night")
    zone(200, 0)
    for titan in ("stone_whale", "thunder_wing"):
        add("marker clear")
        add(f"marker fake board {titan} 200.5 -60 0.5 0")
        add("wait 20")
        for s in STY[1:]:
            setstyle("board", s)
            go(200.5, -58.4, 18, 180, -10)
            add(f"shot board_{titan}_{s}_near_night")
        # 遠景も (昼) 1 枚ずつ
        tm(True)
        for s in STY:
            setstyle("board", s)
            go(200.5, -52, 110, 180, -6)
            add(f"shot board_{titan}_{s}_far_day")
        tm(False)
    # 戻る光 (岩鯨の色。遠景 60、近景 4)
    zone(400, 0)
    add("marker clear")
    add("marker titan stone_whale")
    add("marker fake return 400.5 -60 0.5")
    add("wait 20")
    for day in (True, False):
        dn = "day" if day else "night"
        tm(day)
        for s in STY:
            setstyle("return", s)
            go(400.5, -57, 62, 180, 2)
            add(f"shot return_{s}_far_{dn}")
            go(400.5, -58.4, 5.5, 180, 8)
            add(f"shot return_{s}_near_{dn}")
    # 巨神ごとの B (夜・近景)
    tm(False)
    for titan in ("stone_whale", "tide_shell", "thunder_wing", "ember"):
        add(f"marker titan {titan}")
        setstyle("return", "b")
        go(400.5, -58.4, 5.5, 180, 12)
        add(f"shot return_b_{titan}_night")
add("marker clear")
add("quit")
print("\n".join(L))
