#!/usr/bin/env python3
"""ダンジョンの主の作り込みの確かめ (2026-11-11): 登場 (数え下ろし・這い出る・名前の札) と、技の姿を撮る台本を書く。

    python -I tools/gen/shots/gen_boss_polish_shots.py <主> [動き:待つ tick ...]
    ./gradlew runShots --no-daemon -Pscript=tools/shots/boss_polish_<主>.txt -PshotsName=boss_polish_<主>

例: python -I tools/gen/shots/gen_boss_polish_shots.py shell_octopus slam:16 grab:28 plunge:24 scream:10
出る絵: <主>_intro_count・_intro_rise・_intro_card・_intro_after と <主>_<動き>
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def script(boss, poses, dist=19.0):
    L = [f"# ダンジョンの主の作り込みの確かめ: {boss} (tools/gen/shots/gen_boss_polish_shots.py が書く)",
         "world shots flat", "size 1280 720", "timeout 900", "render 8", "clouds off", "wait 40", "cmd skyabyss playtest skip opening", "wait 60",
         "cmd skyabyss cinematic stop", "cmd time set 6000", "cmd weather clear", "cmd gamemode spectator",
         "cmd fill -30 -61 -30 30 -61 30 minecraft:smooth_basalt destroy", "lowpoly on", "fov 60", "hud on", "chat clear",
         "cmd tp @s 0 -60 40 180 0", "waitchunks", "wait 60",
         f"cmd kill @e[type=skyabyss:{boss}]",
         "cmd gamemode creative", f"cmd tp @s -7 -60 {dist:.1f} -160 -10", "wait 10",
         "cmd skyabyss playtest skip opening", "cmd skyabyss cinematic stop", "close", "wait 140", "cmd skyabyss cinematic stop", "close", f"cmd tp @s -7 -60 {dist:.1f} -160 -10", "wait 10",
         f"cmd summon skyabyss:{boss} 0 -60 0 {{Rotation:[0f,0f],PersistenceRequired:1b,Tags:[\"m\"]}}",
         "wait 30", f"shot {boss}_intro_count", "wait 60", f"shot {boss}_intro_rise",
         "wait 46", f"shot {boss}_intro_card", "wait 90", f"shot {boss}_intro_after",
         # 技の姿: AI を止めて正面の斜めから
         "cmd gamemode spectator", "hud off",
         f"cmd kill @e[type=skyabyss:{boss}]",
         f"cmd summon skyabyss:{boss} 0 -60 0 {{NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b,Invulnerable:1b,Tags:[\"m\"]}}",
         "camera minecraft:overworld -9 -56 13 -145 12", "wait 40"]
    for name, wait in poses:
        L += [f"cmd skyabyss dev anim @e[tag=m] {name}", f"wait {wait}", f"shot {boss}_{name}", "wait 60"]
    return "\n".join(L) + "\n"


if __name__ == "__main__":
    boss = sys.argv[1]
    poses = [(a.split(":")[0], int(a.split(":")[1])) for a in sys.argv[2:]]
    out = ROOT / "tools/shots" / f"boss_polish_{boss}.txt"
    out.write_text(script(boss, poses), encoding="utf-8")
    print(out)
