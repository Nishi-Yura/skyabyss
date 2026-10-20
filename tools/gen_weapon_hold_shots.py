"""武器の持ち姿を全種・全階級で撮る台本 tools/shots/weapon_hold.txt を作る (一人称 右/左、三人称 前/横/左、弓の引き)。

使い方: python tools/gen_weapon_hold_shots.py [--tiers common,legend] [武器の id ...]
撮る: ./gradlew runShots --no-daemon -Pscript=tools/shots/weapon_hold.txt
絵: run/shots/weapon_hold/shots/weapon_hold/hold_<武器>_<階級>_<視点>.png
まとめ: python tools/weapon_hold_sheet.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALL = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw", "coral_shield",
       "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker",
       "abyss_greatsword", "abyss_ash_scythe", "abyss_bell_staff", "abyss_storm_bow"]
BOWS = ("thunder_wing_bow", "abyss_storm_bow")
HEAD = """# 武器の持ち姿 (tools/gen_weapon_hold_shots.py が作る)
world shots flat
size 960 540
timeout 3000
render 6
cmd time set noon
cmd weather clear
cmd gamemode creative
camera 0 -60 8 180 0
waitchunks
hud on
wait 350
cmd skyabyss cinematic stop
close
wait 20
swingpose off"""


def main():
    args = sys.argv[1:]
    tiers = ["common", "legend"]
    if "--tiers" in args:
        i = args.index("--tiers")
        tiers = args[i + 1].split(",")
        del args[i:i + 2]
    items = args or ALL
    out = [HEAD]

    def view(name, persp, hud, yaw=0):
        out.extend([f"perspective {persp}", f"bodyyaw {yaw}", "hud on" if hud else "hud off", "close", "wait 8", "chat clear", f"shot hold_{name}"])

    for tier in tiers:
        out.append(f"weapontier {tier}")
        for w in items:
            abyss = w.startswith("abyss_")
            if abyss and tier != "legend":
                continue
            out.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
            out.append("mainhand right")
            view(f"{w}_{tier}_fpR", "first", True)
            view(f"{w}_{tier}_tpR", "front", False, 0)
            view(f"{w}_{tier}_tpSide", "front", False, 50)
            if tier == "common" or abyss:
                out.append("mainhand left")
                view(f"{w}_{tier}_fpL", "first", True)
                view(f"{w}_{tier}_tpL", "front", False, 0)
                out.append("mainhand right")
            if w in BOWS and (tier == "common" or abyss):
                out.append("perspective first")
                out.extend(["hud on", "bodyyaw 0", "use", "wait 26", "chat clear", f"shot hold_{w}_{tier}_fpDraw", "perspective front", "bodyyaw 0", "hud off", "wait 4",
                            f"shot hold_{w}_{tier}_tpDraw", "stopuse", "wait 20"])
    out.append("quit")
    (ROOT / "tools/shots/weapon_hold.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(len(out), "lines")


if __name__ == "__main__":
    main()
