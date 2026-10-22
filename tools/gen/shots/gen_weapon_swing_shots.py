"""武器の一振りを、実際に振って tick ごとに撮る台本 tools/shots/weapon_swing.txt を作る (一人称・三人称の自分の姿)。

使い方: python tools/gen/shots/gen_weapon_swing_shots.py [武器の id ...]   (省くと近接の全部)
撮る: ./gradlew runShots -Pscript=tools/shots/weapon_swing.txt
絵: run/shots/weapon_swing/shots/weapon_swing/<武器>_<old|new>_<fp|tp>_<tick>.png
  old = 昔の動き (バニラの 6 tick。台本 swingclock off)、new = 攻撃速度で長さが変わる今の動き。tick = 振りを始めてから何 tick 後か
まとめ: python tools/sheets/weapon_swing_sheet.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ALL = ["whalebone_greatsword", "captain_pike", "waterway_fang", "raiju_claw", "warden_glaive", "ember_war_axe",
       "smith_hammer", "thrall_poker", "tide_shell_trident", "tidecaller_claw", "coral_harpoon"]
TICKS = [1, 3, 5, 7, 9, 11]
HEAD = """# 武器の一振りを実際に振って tick ごとに撮る (tools/gen/shots/gen_weapon_swing_shots.py が作る)
world shots flat
size 1280 720
timeout 2400
render 6
cmd time set noon
cmd weather clear
cmd gamemode creative
wait 200
camera 0 -60 8 180 0
waitchunks
hud on
wait 350"""


def main():
    # 末尾に ! を付けた id は今の動きだけ撮る (昔の絵を撮り直さないとき)。--new-only は全部そうする
    new_only_all = "--new-only" in sys.argv
    items = [a for a in sys.argv[1:] if not a.startswith("--")] or ALL
    out = [HEAD]
    for w in items:
        new_only = new_only_all or w.endswith("!")
        w = w.rstrip("!")
        out.append("cmd time set noon")
        out.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
        for mode in (("new",) if new_only else ("old", "new")):
            out.append("swingclock " + ("off" if mode == "old" else "on"))
            for view, persp in (("fp", "first"), ("tp", "front")):
                out.append("perspective " + persp)
                out.append("hud on" if view == "fp" else "hud off")
                out.append("wait 20")
                for t in TICKS:
                    out.append("chat clear")
                    out.append("wait 30")
                    out.append("swing")
                    out.append(f"wait {t}")
                    out.append(f"shot {w}_{mode}_{view}_{t:02d}")
    out.append("swingclock on")
    out.append("quit")
    (ROOT / "tools/shots/weapon_swing.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(len(out), "lines")


if __name__ == "__main__":
    main()
