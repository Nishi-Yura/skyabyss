"""雷翼の背の拡大 (docs/design/26 の 9 章) の撮影台本 tools/shots/storm_scale.txt を書く。
使い方: python tools/gen_storm_scale_shots.py → ./gradlew runShots -Pscript=tools/shots/storm_scale.txt → python tools/storm_scale_sheet.py
撮る場所の表 SHOTS は (名前, x, y, z, 向き, 傾き, 視野)。向き 0 = 南・90 = 西・180 = 北・270 = 東、傾き 90 = 真下。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHOTS = [
    ("arrival", 0.5, 150.2, 172, 180, 5, 90),
    ("road", 0.5, 149.0, 145, 180, 6, 90),
    ("plaza", 0.5, 149.4, 125, 180, 10, 95),
    ("plaza_bay", -9, 149.5, 111, 90, 8, 95),
    ("gate", 0.5, 152.5, 92, 180, 8, 95),
    ("tail_hall", 0.5, 147.6, 44, 180, 5, 95),
    ("hub_south", 0.5, 136.8, 8.5, 180, 8, 100),
    ("hub_up", 0.5, 136.6, 5, 180, 30, 100),
    ("hub_floor", 0.5, 150.0, -33, 0, 45, 100),
    ("hub_tower", 4.5, 136.6, 6, 270, 22, 100),
    ("west_pass", -27.5, 136.6, -24, 90, 12, 100),
    ("w0", -53.5, 153.0, -24, 90, 6, 100),
    ("valley1", -72.5, 153.0, -24, 90, 12, 100),
    ("island", -91.5, 153.0, -24, 90, 8, 100),
    ("elbow", -121, 153.0, -24, 90, 6, 100),
    ("vault_w", -94.5, 153.2, -39.5, 180, 6, 100),
    ("rib_low", -162, 153.0, -24, 90, -35, 100),
    ("rib_shell", -168, 205, -100, 0, 8, 80),
    ("e0", 53.5, 153.0, -24, 270, 6, 100),
    ("circuit1", 73.5, 153.0, -24, 270, 18, 100),
    ("circuit2", 105, 153.0, -24, 270, 18, 100),
    ("circuit3", 131, 153.0, -24, 270, 18, 100),
    ("circuit4", 158, 153.0, -24, 270, 18, 100),
    ("east_ante", 180, 153.0, -24, 270, 6, 100),
    ("vault_e", 183.5, 153.2, -39.5, 180, 6, 100),
    ("nest", 196, 154.5, -24, 270, 6, 100),
    ("neck", 0.5, 149.2, -50, 180, 4, 95),
    ("crown", 0.5, 149.4, -80, 180, 8, 100),
    ("eye", 0.5, 151.0, -116, 180, 12, 100),
    ("beak", 0.5, 149.9, -141, 180, 5, 95),
    ("keel", 0.5, 120.0, -45, 0, 4, 95),
    ("overview", 0, 215, 238, 180, 24, 85),
]

out = []
out.append("# 雷翼の背の拡大 (docs/design/26 の 9 章、版 20) の確かめ。./gradlew runShots -Pscript=tools/shots/storm_scale.txt")
out.append("# 出力: run/shots/storm_scale/shots/storm_scale/*.png → python tools/storm_scale_sheet.py で docs/review/previews/storm_scale_sheet.png (音は自動で 0)")
out.append("# この台本は python tools/gen_storm_scale_shots.py が書く (場所の表はそちら)")
out.append("world storm_scale flat")
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
with open(ROOT / "tools" / "shots" / "storm_scale.txt", "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(out) + "\n")
print(len(SHOTS), "shots")
