"""武器の 3D と動きの品質の見直し (docs/review/weapon_quality_2026-10-23.md) の台本 tools/shots/weapon_quality.txt を作る。
全 17 武器を品そのものの階級で: 一人称の構え・連撃 3 段の止め絵・点検、三人称の構え・横・振り。盾の構え、弓 2 本の引き。
最後に持ち物の画面 (アイコンの確認。30 品 + 深淵 3 品)。
撮る: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/weapon_quality.txt -PshotsName=weapon_quality
まとめ: python tools/sheets/weapon_quality_sheet.py"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MELEE = [("whalebone_greatsword", "rare"), ("captain_pike", "legend"), ("waterway_fang", "legend"),
         ("tide_shell_trident", "rare"), ("coral_harpoon", "legend"), ("tidecaller_claw", "legend"),
         ("warden_glaive", "legend"), ("raiju_claw", "legend"),
         ("ember_war_axe", "rare"), ("smith_hammer", "legend"), ("thrall_poker", "legend"),
         ("abyss_greatsword", "legend"), ("abyss_ash_scythe", "legend"), ("abyss_bell_staff", "legend")]
BOWS = [("thunder_wing_bow", "rare"), ("abyss_storm_bow", "legend")]
ICONS = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw", "coral_shield",
         "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker",
         "abyss_greatsword", "abyss_ash_scythe", "abyss_bell_staff", "abyss_storm_bow",
         "resonance_charm", "moss_talisman", "stone_whale_plating", "tide_chime_charm", "moontide_pearl", "tide_watching_shell", "tide_pearl",
         "storm_charm", "storm_anklet", "storm_eye_feather", "gale_feather", "kindling_charm", "ember_mantle", "ember_lantern", "ember_horn"]
HEAD = """# 武器の品質の見直し (tools/gen/shots/gen_weapon_quality_shots.py が作る)
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
    out = [HEAD]

    def v(name, persp, hud, yaw=0):
        out.extend([f"perspective {persp}", f"bodyyaw {yaw}", "hud on" if hud else "hud off", "close", "wait 8", "chat clear", f"shot {name}"])

    for w, tier in MELEE:
        out += [f"weapontier {tier}", f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}", "mainhand right", "swingpose off"]
        out.append("wait 40")  # 遅い武器は持ち替えのあと手が上がりきるまで (攻撃の溜め) 時間がかかる
        v(f"{w}_fpIdle", "first", True)
        v(f"{w}_tpIdle", "front", False, 0)
        v(f"{w}_tpSide", "front", False, 70)
        out += ["perspective first", "bodyyaw 0", "hud on"]
        for step in (0, 1, 2):
            for t in (0.25, 0.5):
                out += [f"swingstep {step}", f"swingpose {t}", "wait 3", "chat clear", f"shot {w}_fpS{step}_{int(t * 100)}"]
        out += ["perspective front", "hud off", "swingstep 0", "swingpose 0.5", "wait 3", f"shot {w}_tpS0_50",
                "swingstep 1", "swingpose 0.5", "wait 3", f"shot {w}_tpS1_50", "swingpose off",
                "perspective first", "hud on", "inspectpose 0.5", "wait 3", f"shot {w}_fpInspect", "inspectpose off", "wait 5"]
    out += ["weapontier rare", "cmd item replace entity @s weapon.mainhand with skyabyss:coral_shield"]
    v("coral_shield_fpIdle", "first", True)
    v("coral_shield_tpIdle", "front", False, 0)
    out += ["perspective first", "bodyyaw 0", "hud on", "use", "wait 12", "chat clear", "shot coral_shield_fpGuard", "perspective front", "hud off", "wait 4",
            "shot coral_shield_tpGuard", "bodyyaw 45", "shot coral_shield_tpGuardSide", "stopuse", "wait 20"]
    for b, tier in BOWS:
        out += [f"weapontier {tier}", f"cmd item replace entity @s weapon.mainhand with skyabyss:{b}"]
        v(f"{b}_fpIdle", "first", True)
        v(f"{b}_tpIdle", "front", False, 0)
        for persp, tag, hud in (("first", "fp", True), ("front", "tp", False)):
            out += [f"perspective {persp}", "bodyyaw 0", "hud on" if hud else "hud off", "use", "wait 26", "chat clear", f"shot {b}_{tag}Draw2", "stopuse", "wait 25"]
    # 持ち物の画面でアイコン
    out += ["weapontier off", "cmd clear @s", "cmd gamemode survival"]
    for i, it in enumerate(ICONS):
        slot = f"hotbar.{i}" if i < 9 else f"inventory.{i - 9}"
        out.append(f"cmd item replace entity @s {slot} with skyabyss:{it}")
    out += ["perspective first", "hud on", "guiscale 3", "wait 10", "open inventory", "wait 10", "chat clear", "shot icons_inventory", "close", "guiscale 2", "quit"]
    (ROOT / "tools/shots/weapon_quality.txt").write_text(chr(10).join(out) + chr(10), encoding="utf-8")


if __name__ == "__main__":
    main()
