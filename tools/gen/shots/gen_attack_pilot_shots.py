# -*- coding: utf-8 -*-
"""攻撃の見せ方の決まり (docs/design/59) の見本: 岩鯨の心臓の技を、溜め・当たり・あと の 3 枚ずつ撮る台本を書く。

  python -I tools/gen/shots/gen_attack_pilot_shots.py
  python -I tools/gen/ui/gen_fx_particles.py --style a   (粒の絵の案を入れる)
  ./gradlew runShots --no-daemon -Pscript=tools/shots/attack_pilot.txt -PshotsName=attack_pilot_a
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
# (技, 予告の長さ, 続く長さ)
MOVES = [("THRUST", 24, 0), ("SPIKE", 22, 0), ("LEAP", 24, 0), ("CRACK", 30, 0), ("VOLLEY", 30, 0), ("BEAM", 40, 24), ("CHAIN", 24, 0),
         ("SHELL_DROP", 30, 0), ("ROAR", 22, 0)]
L = []
a = L.append
a("# 攻撃の見せ方の見本 (岩鯨の心臓)。生成: tools/gen/shots/gen_attack_pilot_shots.py")
a("world attack_pilot flat")
a("size 1280 720")
a("timeout 1500")
a("render 8")
a("clouds off")
a("cmd time set noon")
a("cmd weather clear")
a("cmd gamerule doDaylightCycle false")
a("cmd gamemode survival Dev")
a("cmd effect give Dev minecraft:resistance 1000000 4 true")
a("cmd fill -30 -61 -30 30 -61 30 minecraft:polished_deepslate")
a("cmd fill -3 -47 16 3 -47 21 minecraft:barrier")
a("camera minecraft:overworld 0.5 -46 18.5 180 34")
a("waitchunks")
a("wait 20")
a("cmd skyabyss cinematic stop")
a("wait 10")
a("camera minecraft:overworld 0.5 -46 18.5 180 34")
a("hud off")
a("cmd summon skyabyss:stone_whale_heart 0 -59 0 {PersistenceRequired:1b,Rotation:[0f,0f]}")
a("wait 10")
a("cmd skyabyss cinematic stop")
a("wait 5")
a("camera minecraft:overworld 0.5 -46 18.5 180 34")
a("hud off")
a("wait 110")
for mv, warn, active in MOVES:
    a("cmd skyabyss dev force @e[type=skyabyss:stone_whale_heart,limit=1] " + mv)
    a("wait %d" % (warn * 2 // 3))
    a("shot %s_1_charge" % mv.lower())
    a("wait %d" % (warn - warn * 2 // 3 + 1 + active // 2))
    a("shot %s_2_hit" % mv.lower())
    a("wait 10")
    a("shot %s_3_after" % mv.lower())
    a("wait 60")
a("quit")
out = ROOT / "tools/shots/attack_pilot.txt"
out.write_text("\n".join(L) + "\n", encoding="utf-8")
print("wrote", out, len(L))
