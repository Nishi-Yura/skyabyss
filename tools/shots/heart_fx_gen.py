# 心臓の技の見た目 (AttackLook / client AttackFx) の撮影台本を作る。
#   python tools/shots/heart_fx_gen.py > tools/shots/heart_fx.txt
#   ./gradlew runShots -Pscript=tools/shots/heart_fx.txt -PshotsName=heart_fx   (tools/dev/mc_run.sh 経由で)
#   python tools/shots/heart_fx_sheet.py run/shots/heart_fx/shots/heart_fx docs/review/heart_fx
# 技ごとに 4 枚 (溜め → 当たり → 残り を 8 tick おき)。台は telegraph_red.txt と同じ平らな床と見下ろしのカメラ
HEARTS = [
    ("whale", "skyabyss:stone_whale_heart", "-59", "force",
     ["THRUST", "SWEEP", "LEAP", "CRACK", "VOLLEY", "BEAM", "CHAIN", "SHELL_DROP"]),
    ("tide", "skyabyss:tide_heart", "-58.5", "tideforce",
     ["BREATH", "SHELL_STRIKE", "WHIRL", "PEARL_RAIN", "EBB", "MOON_WAVE", "SPRING_TIDE"]),
    ("storm", "skyabyss:thunder_wing_heart", "-58", "force",
     ["BLADE", "CHAIN", "WINGBEAT", "CHARGE", "FEATHER", "FLASH", "WEB", "BARRAGE", "CROSS"]),
    ("ember", "skyabyss:ember_heart", "-58", "emberforce",
     ["BREATH", "PULSE", "ORB", "GALE", "PELLET", "FADING", "FORGE", "EMBERS"]),
]
CAM = "camera minecraft:overworld 0.5 -43 14.5 180 38"
out = []
w = out.append
w("# 心臓の技の見た目 (2026-10-23): 技ごとに 溜め → 当たり → 残り を 4 枚。heart_fx_gen.py が書く")
w("world shots flat")
w("size 960 540")
w("timeout 7200")
w("render 8")
w("clouds off")
w("")


def stage():
    w("cmd time set noon")
    w("cmd weather clear")
    w("cmd gamemode survival Dev")
    w("cmd effect give Dev minecraft:resistance 1000000 4 true")
    w("cmd fill -30 -61 -30 30 -61 30 minecraft:smooth_basalt destroy")
    w("cmd fill -4 -44 11 4 -44 17 minecraft:air")
    w("cmd fill -4 -44 11 4 -44 17 minecraft:barrier")
    w("cmd kill @e[type=!minecraft:player,distance=..80]")
    w("hud off")
    w(CAM)
    w("waitchunks")
    w("wait 20")
    w("cmd skyabyss cinematic stop")
    w("chat clear")


for key, ent, y, force, actions in HEARTS:
    w(f"# ---- {key}")
    stage()
    w(f"cmd summon {ent} 0 {y} 0 {{PersistenceRequired:1b,Rotation:[0f,0f]}}")
    w("wait 10")
    w("cmd skyabyss cinematic stop")
    w(CAM)
    w("wait 100")
    for a in actions:
        w(CAM)
        w("wait 5")
        w(f"cmd skyabyss dev {force} @e[type={ent},limit=1] {a} 6")
        for i in range(4):
            w("wait 8")
            w(f"shot {key}_{a.lower()}_{i}")
        w("wait 50")
    w(f"cmd kill @e[type={ent}]")
    w("cmd kill @e[type=!minecraft:player,distance=..120]")
    w("")

# 領域: 宣言のあと、決まりが回るところ
w("# ---- domains")
stage()
w("wait 300")
for key, ent, y, force, actions in HEARTS:
    w(f"cmd summon {ent} 0 {y} 0 {{PersistenceRequired:1b,Rotation:[0f,0f]}}")
    w("wait 10")
    w("cmd skyabyss cinematic stop")
    w(CAM)
    w("wait 110")
    w(f"cmd skyabyss dev force @e[type={ent},limit=1] DOMAIN")
    w("wait 96")
    for i in range(4):
        w("wait 14")
        w(f"shot domain_{key}_{i}")
    w(f"cmd kill @e[type={ent}]")
    w("wait 120")
    w("cmd skyabyss cinematic stop")
w("echo done")
w("cmd kill @e[type=!minecraft:player,distance=..120]")
w("quit")
print("\n".join(out))
