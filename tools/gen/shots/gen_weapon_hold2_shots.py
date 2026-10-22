"""持ち方の見直し (槍の角度・弓の矢・盾の構え・大きさの順) を撮る台本 tools/shots/weapon_hold2.txt を作る。
撮る: ./gradlew runShots --no-daemon -Pscript=tools/shots/weapon_hold2.txt   絵: run/shots/weapon_hold2/shots/weapon_hold2/
まとめ: python tools/sheets/weapon_hold2_sheet.py"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MELEE = ["waterway_fang", "raiju_claw", "tidecaller_claw", "whalebone_greatsword", "captain_pike", "tide_shell_trident", "coral_harpoon",
         "warden_glaive", "thrall_poker", "ember_war_axe", "smith_hammer"]
BOW = "thunder_wing_bow"
HEAD = """# 持ち方の見直し (tools/gen/shots/gen_weapon_hold2_shots.py が作る)
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


SPEARS = ["captain_pike", "tide_shell_trident", "coral_harpoon", "warden_glaive", "thrall_poker"]


def main():
    import sys
    only = set(sys.argv[1:])  # spears / others / shield / bow (省くと全部)
    want = lambda g: not only or g in only
    out = [HEAD]

    def v(name, persp, hud, yaw=0):
        out.extend([f"perspective {persp}", f"bodyyaw {yaw}", "hud on" if hud else "hud off", "close", "wait 8", "chat clear", f"shot {name}"])

    for tier in ("common", "legend"):
        out.append(f"weapontier {tier}")
        for w in MELEE:
            if not want("spears" if w in SPEARS else "others"):
                continue
            out.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
            out.append("mainhand right")
            v(f"{w}_{tier}_fpIdle", "first", True)
            v(f"{w}_{tier}_tpIdle", "front", False, 0)
            v(f"{w}_{tier}_tpSide", "front", False, 70)
            if tier == "common":
                for persp, tag in (("first", "fp"), ("front", "tp")):
                    out.extend([f"perspective {persp}", "bodyyaw 0", "hud on" if tag == "fp" else "hud off", "wait 30", "chat clear", "swing", "wait 4", f"shot {w}_{tier}_{tag}Swing"])
    out.append("weapontier common")
    if want("shield"):
        out.append("cmd item replace entity @s weapon.mainhand with skyabyss:coral_shield")
        v("coral_shield_common_fpIdle", "first", True)
        v("coral_shield_common_tpIdle", "front", False, 0)
        out.extend(["perspective first", "bodyyaw 0", "hud on", "use", "wait 12", "chat clear", "shot coral_shield_common_fpGuard", "perspective front", "hud off", "wait 4",
                    "shot coral_shield_common_tpGuard", "bodyyaw 45", "shot coral_shield_common_tpGuardSide", "stopuse", "wait 20"])
        # 副の手に盾、主の手に槍 (ふつうの使い方)
        out.extend(["cmd item replace entity @s weapon.mainhand with skyabyss:captain_pike", "cmd item replace entity @s weapon.offhand with skyabyss:coral_shield",
                    "perspective first", "bodyyaw 0", "hud on", "wait 10", "chat clear", "shot offhand_shield_fpIdle", "use", "wait 12", "chat clear", "shot offhand_shield_fpGuard",
                    "perspective front", "hud off", "wait 4", "shot offhand_shield_tpGuard", "bodyyaw 45", "shot offhand_shield_tpGuardSide", "stopuse", "wait 10",
                    "cmd item replace entity @s weapon.offhand with minecraft:air"])
    if want("bow"):
        out.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{BOW}")
        for persp, tag, hud in (("first", "fp", True), ("front", "tp", False)):
            for n, ticks in enumerate((5, 15, 26)):
                out.extend([f"perspective {persp}", "bodyyaw 0", "hud on" if hud else "hud off", "use", f"wait {ticks}", "chat clear", f"shot {BOW}_{tag}Draw{n}", "stopuse", "wait 1", f"shot {BOW}_{tag}Release{n}a", "wait 2", f"shot {BOW}_{tag}Release{n}"] + (["perspective back", "wait 3", f"shot {BOW}_{tag}Release{n}b"] if tag == "tp" else ["wait 4", f"shot {BOW}_{tag}Release{n}b"]) + ["wait 25"])
    out.append("quit")
    (ROOT / "tools/shots/weapon_hold2.txt").write_text(chr(10).join(out) + chr(10), encoding="utf-8")


if __name__ == "__main__":
    main()
