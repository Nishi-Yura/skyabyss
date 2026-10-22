"""雷翼の背の任意の翼 (docs/design/26 の 9.5、版 21) の撮影台本 tools/shots/storm_scale2.txt を書く。
使い方: python tools/gen/shots/gen_storm_scale2_shots.py → ./gradlew runShots -Pscript=tools/shots/storm_scale2.txt → python tools/sheets/storm_scale2_sheet.py
撮る場所の表 SHOTS は (名前, x, y, z, 向き, 傾き, 視野)。向き 0 = 南・90 = 西・180 = 北・270 = 東、傾き 90 = 真下。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent.parent
SHOTS = [
    ("hatch", -2.5, 147.8, 33.5, 20, 4, 100),
    ("turret_top", -11.5, 163.7, 36.5, 180, 4, 100),
    ("plaza", 0.5, 167.0, 34.5, 180, 8, 105),
    ("plaza_rose", 0.5, 190.0, 23.5, 270, 80, 90),
    ("vane_face", 8.5, 170.0, 23.5, 90, 12, 100),
    ("rod_face", -8.5, 170.0, 23.5, 270, 12, 100),
    ("vane_lobby", -27.5, 164.6, 23.5, 90, 4, 105),
    ("vane_tier2", -28.5, 187.6, 23.5, 90, 6, 105),
    ("vane_top", -26.5, 225.0, 23.5, 90, 8, 105),
    ("rod_lobby", 27.5, 164.6, 23.5, 270, 4, 105),
    ("rod_tier3", 28.5, 199.6, 23.5, 270, 6, 105),
    ("rod_top", 27.5, 225.0, 23.5, 270, 8, 105),
    ("out_w", -50.5, 164.7, 23.5, 90, 4, 100),
    ("out_e", 48.5, 164.6, 23.5, 270, 4, 100),
    ("tip_w", -188.5, 165.0, 23.5, 90, 6, 100),
    ("garden_w", -10.5, 151.0, 124.5, 35, 12, 105),
    ("garden_e", 10.5, 151.0, 124.5, 328, 12, 105),
    ("hub_vault", 0.5, 137.0, 8.5, 180, 24, 105),
    ("hub_wall", 14.5, 139.0, -12.5, 90, 14, 105),
    ("e0_ribs", 53.5, 153.0, -24, 270, 20, 105),
    ("eye_dome", 0.5, 151.0, -118, 180, 30, 105),
    ("overview", 0, 240, 250, 180, 30, 80),
    ("overview_side", -230, 205, 95, 250, 13, 80),
]

# 使い方 (一部だけ撮り直す): python tools/gen/shots/gen_storm_scale2_shots.py <名前>... → tools/shots/storm_scale2_part.txt (-PshotsName=storm_scale2 で同じ世界・同じ出力へ)
only = sys.argv[1:]
part = bool(only)
if part:
    SHOTS = [x for x in SHOTS if x[0] in only]
out = []
out.append("# 雷翼の背の任意の翼 (docs/design/26 の 9.5、版 21) の確かめ。./gradlew runShots -Pscript=tools/shots/storm_scale2.txt")
out.append("# 出力: run/shots/storm_scale2/shots/storm_scale2/*.png → python tools/sheets/storm_scale2_sheet.py で docs/review/previews/storm_scale2_sheet.png (音は自動で 0)")
out.append("# この台本は python tools/gen/shots/gen_storm_scale2_shots.py が書く (場所の表はそちら)")
out.append("world storm_scale2 flat")
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
    if name.startswith("overview"):
        out.append("render 16")
    out.append("fov %d" % fov)
    out.append("camera skyabyss:storm_back %s %s %s %d %d" % (x, y, z, yaw, pitch))
    out.append("waitchunks 600")
    out.append("wait 30")
    out.append("close")
    out.append("wait 4")
    out.append("shot " + name)
out.append("quit")
with open(ROOT / "tools" / "shots" / ("storm_scale2_part.txt" if part else "storm_scale2.txt"), "w", encoding="utf-8", newline="\n") as fh:
    fh.write("\n".join(out) + "\n")
print(len(SHOTS), "shots")
