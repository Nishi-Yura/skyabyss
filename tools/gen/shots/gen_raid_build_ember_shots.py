# 余燼のレイドの部屋の建築の撮影の台本を書く (docs/design/13 の 19.3c)。
#   python -I tools/gen/shots/gen_raid_build_ember_shots.py tools/shots/raid_build_ember.txt
import sys
rooms = {  # id: (hx, hz, h)
    "ER1": (12, 15, 8), "ER2": (11, 13, 8), "ER3": (13, 13, 9), "ER4": (9, 27, 10), "ER5": (9, 12, 8), "ER6": (11, 11, 9), "ERM": (13, 13, 14)}
L = ["# 余燼のレイドの部屋の建築 (docs/design/39 の 4.4)。/skyemberraid room <id> で区画 1 の原点に建て、入口・中・見下ろし・振り返りを撮る",
     "#   ./gradlew runShots -Pscript=tools/shots/raid_build_ember.txt -PshotsName=<before|after>   絵は docs/review/raid_build_ember/",
     "world raid_build_ember flat", "size 1280 720", "timeout 3000", "render 10", "clouds off", "",
     "wait 300", "cmd skyabyss cinematic stop", "wait 10", "cmd skyabyss cinematic stop", "cmd skyabyss playtest skip opening",
     "cmd time set midnight", "cmd weather clear", "cmd gamerule doDaylightCycle false", "cmd gamerule doMobSpawning false", "hud off",
     "cmd gamemode spectator", ""]
for rid, (hx, hz, h) in rooms.items():
    n = rid.lower()
    rest = hz + 4
    L += [f"# ---- {rid}", f"cmd skyemberraid room {rid}", "wait 40", "waitchunks", "wait 60", "chat clear"]
    L += [f"cmd skyemberraid at 0 0 {rest} 180 2", "wait 30", f"shot {n}_a_entry"]
    L += [f"cmd skyemberraid at 0 0.5 {hz - 1} 180 8", "wait 30", f"shot {n}_b_inside"]
    if rid == "ERM":
        L += ["cmd skyemberraid at 6 9 7 140 34", "wait 30", f"shot {n}_c_overview"]
        L += ["cmd skyemberraid at -6 7 -8 -35 22", "wait 30", f"shot {n}_d_back"]
    else:
        L += [f"cmd skyemberraid at {hx - 1} {h - 2} {hz - 1} 140 32", "wait 30", f"shot {n}_c_overview"]
        L += [f"cmd skyemberraid at {-(hx - 1)} {max(2, h - 4)} {-(hz - 1)} -40 22", "wait 30", f"shot {n}_d_back"]
    L += ["cmd skyemberraid end", "wait 10", ""]
L.append("quit")
open(sys.argv[1], "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
