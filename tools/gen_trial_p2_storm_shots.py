"""雷翼の試練の甲板 (docs/design/29 の P2。版 22) の撮影台本 tools/shots/trial_p2_storm.txt を書く。
使い方: python tools/gen_trial_p2_storm_shots.py → ./gradlew runShots --no-daemon -Pscript=tools/shots/trial_p2_storm.txt -PshotsName=trial_p2_storm → python tools/trial_p2_storm_sheet.py
撮る場所の表 SHOTS は (名前, x, y, z, 向き, 傾き, 視野)。向き 0 = 南・90 = 西・180 = 北・270 = 東、傾き 90 = 真下。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHOTS = [
    ("bridge", 19.5, 167.0, 38.5, 0, 12, 100),
    ("yard", 0.5, 172.0, 65.5, 0, 20, 105),
    ("yard_gates", -8.5, 167.0, 73.5, 90, 6, 100),
    ("s02_start", -34.5, 167.0, 73.5, 90, 8, 100),
    ("s02_chain", -44.5, 173.0, 73.5, 90, 14, 110),
    ("s02_mid", -83.5, 168.0, 70.5, 90, 8, 100),
    ("s03_start", 33.5, 167.0, 73.5, 270, 8, 100),
    ("s03_chain", 44.5, 171.0, 73.5, 270, 12, 110),
    ("s03_mid", 66.5, 170.0, 73.5, 270, 8, 100),
    ("s03_top", 75.5, 205.0, 73.5, 270, 72, 90),
    ("s10_start", -11.5, 167.0, 96.5, 90, 8, 100),
    ("s10_chain", -10.5, 172.0, 96.5, 90, 16, 110),
    ("s09_foyer", 14.5, 167.0, 95.5, 270, 8, 100),
    ("s09_arena", 25.5, 171.0, 104.5, 270, 22, 105),
    ("overview", 0.0, 262.0, 100.0, 0, 86, 100),
]

out = []
out.append("# 雷翼の試練の甲板 (docs/design/29 の P2、版 22) の確かめ。./gradlew runShots --no-daemon -Pscript=tools/shots/trial_p2_storm.txt -PshotsName=trial_p2_storm")
out.append("# 出力: run/shots/trial_p2_storm/shots/trial_p2_storm/*.png → python tools/trial_p2_storm_sheet.py で docs/review/previews/trial_p2_storm_sheet.png")
out.append("world trial_p2_storm flat")
out.append("size 1280 720")
out.append("timeout 3600")
out.append("render 12")
out.append("clouds off")
out.append("")
out.append("cmd time set noon")
out.append("cmd weather clear")
out.append("cmd gamemode creative")
out.append("camera 0.5 -60 0.5 0 0")
out.append("waitchunks")
out.append("wait 40")
out.append("cmd skyabyss dev arrive storm_back")
out.append("waitchunks 1500")
out.append("wait 80")
out.append("cmd skyabyss titan storm calm")
out.append("chat clear")
out.append("cmd gamemode spectator")
out.append("cmd effect give @p minecraft:night_vision infinite 0 true")
out.append("hud off")
for name, x, y, z, yaw, pitch, fov in SHOTS:
    if name == "overview":
        out.append("render 16")
    out.append("fov %d" % fov)
    out.append("camera skyabyss:storm_back %s %s %s %d %d" % (x, y, z, yaw, pitch))
    out.append("waitchunks 600")
    out.append("wait 30")
    out.append("close")
    out.append("wait 4")
    out.append("shot " + name)
out.append("quit")
with open(ROOT / "tools" / "shots" / "trial_p2_storm.txt", "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(out) + "\n")
print(len(SHOTS), "shots")
