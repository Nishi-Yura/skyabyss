"""武器を持った姿 (一人称・三人称の正面と横向き) を、構えと振りの途中で撮る台本 tools/shots/weapon_poses.txt を作る。

使い方: python tools/gen_weapon_pose_shots.py [--left] [武器の id ...]  (--left = 左利きで撮る。絵の名前に _left)
         (id を省くと全部。minecraft:iron_sword のように名前空間つきも可)
撮る: ./gradlew runShots -Pscript=tools/shots/weapon_poses.txt
絵: run/shots/weapon_poses/shots/weapon_poses/<武器>_<fp|tp|tp34>_<idle|0.2|0.4|0.55|0.75>.png
  fp = 一人称、tp = 三人称 (正面)、tp34 = 三人称 (体を 80 度ずらした横向き)。idle = 構え、数字 = 振りの進み (台本の swingpose)
まとめ: python tools/weapon_pose_sheet.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALL = ["whalebone_greatsword", "captain_pike", "waterway_fang", "raiju_claw", "warden_glaive", "thunder_wing_bow",
       "ember_war_axe", "smith_hammer", "thrall_poker", "tide_shell_trident", "tidecaller_claw", "coral_harpoon", "coral_shield"]
PHASES = ["0.2", "0.4", "0.55", "0.75"]

HEAD = """# 武器を持った姿を、一人称と三人称で、構えと振りの途中 (進み 0.2 / 0.4 / 0.55 / 0.75) で撮る (tools/gen_weapon_pose_shots.py が作る)
world shots flat
size 1280 720
timeout 1200
render 6
cmd time set noon
cmd weather clear
cmd gamemode creative
camera 0 -60 8 180 0
hud on
wait 350
"""


def main():
    left = "--left" in sys.argv
    items = [a for a in sys.argv[1:] if not a.startswith("--")] or ALL
    out = [HEAD.rstrip("\n")]
    if left:
        out.append("mainhand left")
    for w in items:
        item = w if ":" in w else "skyabyss:" + w
        tag = w.replace(":", "_") + ("_left" if left else "")
        out.append("cmd time set noon")
        out.append("cmd weather clear")
        out.append("cmd item replace entity @s weapon.mainhand with " + item)
        for view, persp, body in (("fp", "first", 0), ("tp", "front", 0), ("tp34", "front", 80)):
            out.append("perspective " + persp)
            out.append(f"bodyyaw {body}")
            out.append("hud on" if view == "fp" else "hud off")
            out.append("swingpose off")
            out.append("wait 12")
            out.append("chat clear")
            out.append(f"shot {tag}_{view}_idle")
            for p in PHASES:
                out.append(f"swingpose {p}")
                out.append("wait 3")
                out.append(f"shot {tag}_{view}_{p}")
        out.append("swingpose off")
    if left:
        out.append("mainhand right")
    out.append("quit")
    (ROOT / "tools/shots/weapon_poses.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(len(out), "lines")


if __name__ == "__main__":
    main()
