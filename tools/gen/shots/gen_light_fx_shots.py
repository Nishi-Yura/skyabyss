# -*- coding: utf-8 -*-
# 光の表現 (2026-11-15 の選定: 裂け目・光線と溜め・当たりの輪・核の光・軌道) の A/B/C を撮る台本を書く。
#   python -I tools/gen/shots/gen_light_fx_shots.py
#   ./gradlew runShots --no-daemon -Pscript=tools/shots/light_fx.txt
#   python -I tools/sheets/light_fx_sheet.py  -> docs/review/light_fx/*.png
import os

THEMES = ["stone_whale", "tide_shell", "thunder_wing", "ember", "abyss", "moon"]
STYLES = ["a", "b", "c"]
L = []
a = L.append

a("# 光の表現の選定用の絵。生成: tools/gen/shots/gen_light_fx_shots.py")
a("world lightfx flat")
a("size 1280 720")
a("timeout 3000")
a("render 8")
a("clouds off")
a("cmd time set midnight")
a("cmd weather clear")
a("cmd gamerule doDaylightCycle false")
a("cmd gamemode survival Dev")
a("cmd effect give Dev minecraft:resistance 1000000 4 true")
a("cmd effect give Dev minecraft:saturation 1000000 1 true")
a("cmd fill -30 -61 -30 30 -61 30 minecraft:smooth_basalt destroy")
a("cmd fill -3 -45 15 3 -45 20 minecraft:barrier")
a("cmd kill @e[type=!minecraft:player,distance=..80]")
a("camera minecraft:overworld 0.5 -60 14.5 180 -6")
a("waitchunks")
a("wait 20")
a("cmd skyabyss cinematic stop")
a("wait 20")
a("camera minecraft:overworld 0.5 -60 14.5 180 -6")
a("hud off")
a("wait 10")
a("chat clear")

# ---- 1. 場に立つ裂け目 (領域の宣言の形)。巨神ごと x 案。宣言 80 tick の 60 (開ききり) と 14 (線が走る) で止める
a("# ---- 1. 場の裂け目")
for st in STYLES:
    a("lightfx rift " + st)
    for th in THEMES:
        a("rift freeze 60")
        a("rift world %s 0.5 -53.5 0.5 10 80" % th)
        a("wait 3")
        a("shot rift_%s_%s" % (th, st))
    a("rift freeze 14")
    a("rift world stone_whale 0.5 -53.5 0.5 10 80")
    a("wait 3")
    a("shot rift_open_%s" % st)
    a("rift freeze 74")
    a("rift world stone_whale 0.5 -53.5 0.5 10 80")
    a("wait 3")
    a("shot rift_burst_%s" % st)
a("rift clear")

# ---- 2. 渡る裂け目 (目の前に開いて覆う)
a("# ---- 2. 渡る裂け目")
for st in STYLES:
    a("lightfx rift " + st)
    for th in (["stone_whale"] if st != "a" else ["stone_whale", "tide_shell", "thunder_wing", "ember", "abyss", "moon"]):
        for age in ([12, 19, 24, 32, 47] if th == "stone_whale" else [19, 32]):
            a("rift freeze %d" % age)
            a("rift pass %s" % th)
            a("wait 3")
            a("shot pass_%s_%s_%02d" % (th, st, age))
a("rift freeze off")
a("rift clear")

# ---- 3. 当たりの輪 (その場に置く)
a("# ---- 3. 当たりの輪")
for st in STYLES:
    a("lightfx ring " + st)
    for wait, name in ((2, "early"), (5, "mid")):
        a("lightring 0.5 -59.88 0.5 8 bfe6cc")
        a("wait %d" % wait)
        a("shot ring_%s_%s" % (st, name))
        a("wait 30")

# ---- 4. 心臓: 溜めの筋・光線・振り回し・核の光
a("# ---- 4. 心臓")
a("camera minecraft:overworld 0.5 -44 17.5 180 36")
for heart, ent, moves in (("whale", "stone_whale_heart", (("THRUST", 24, 0), ("BEAM", 40, 24))),
                          ("ember", "ember_heart", (("BREATH", 24, 0),))):
    a("cmd summon skyabyss:%s 0 -59 0 {PersistenceRequired:1b,Rotation:[0f,0f]}" % ent)
    a("wait 10")
    a("cmd skyabyss cinematic stop")
    a("camera minecraft:overworld 0.5 -44 17.5 180 36")
    a("wait 110")
    for st in STYLES:
        a("lightfx all " + st)
        a("wait 2")
        a("shot core_%s_%s" % (heart, st))
        for mv, warn, active in moves:
            a("cmd skyabyss dev force @e[type=skyabyss:%s,limit=1] %s" % (ent, mv))
            a("wait %d" % (warn // 2))
            a("shot %s_%s_gather_%s" % (heart, mv.lower(), st))
            a("wait %d" % (warn - warn // 2 + (active // 2 if active else -1)))
            a("shot %s_%s_fire_%s" % (heart, mv.lower(), st))
            a("wait 4")
            a("shot %s_%s_after_%s" % (heart, mv.lower(), st))
            a("wait 70")
    a("cmd kill @e[type=skyabyss:%s]" % ent)
    a("wait 120")
    a("cmd skyabyss cinematic stop")
    a("chat clear")

# ---- 5. 領域の始まり (本物の宣言。A)
a("# ---- 5. 領域の始まり")
a("lightfx all a")
a("cmd summon skyabyss:stone_whale_heart 0 -59 0 {PersistenceRequired:1b,Rotation:[0f,0f]}")
a("wait 10")
a("cmd skyabyss cinematic stop")
a("camera minecraft:overworld 0.5 -60 22.5 180 -14")
a("wait 110")
a("cmd skyabyss dev force @e[type=skyabyss:stone_whale_heart,limit=1] DOMAIN")
for w, n in ((20, "domain_open"), (24, "domain_wide"), (14, "domain_burst")):
    a("wait %d" % w)
    a("shot " + n)
a("hud on")
a("wait 4")
a("shot domain_hud")
a("hud off")
a("cmd kill @e[type=skyabyss:stone_whale_heart]")
a("wait 120")
a("cmd skyabyss cinematic stop")

# ---- 6. 軌道 (月の主)。技は自分で選ぶので、続けて撮る
a("# ---- 6. 軌道")
a("camera minecraft:overworld 0.5 -60 10.5 180 -4")
for st in STYLES:
    a("lightfx orbit " + st)
    a("cmd summon skyabyss:waning_blade 0.5 -60 0.5 {PersistenceRequired:1b,Rotation:[0f,0f]}")
    a("wait 40")
    a("cmd skyabyss cinematic stop")
    a("camera minecraft:overworld 0.5 -60 10.5 180 -4")
    for i in range(10):
        a("wait 12")
        a("shot orbit_%s_%02d" % (st, i))
    a("cmd kill @e[type=skyabyss:waning_blade]")
    a("wait 40")
    a("cmd skyabyss cinematic stop")
    a("chat clear")
a("quit")

# 場面が終わると HUD が戻るので、止めるたびに隠し直す
L2 = []
for x in L:
    L2.append(x)
    if x == "cmd skyabyss cinematic stop":
        L2.append("hud off")
L = L2
out = os.path.join("tools", "shots", "light_fx.txt")
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(L) + "\n")
print("wrote", out, len(L), "lines")
