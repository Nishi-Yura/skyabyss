"""weapon_lp_<id>.txt の台本を、同時に N 本ずつ ./gradlew runShots で流す。

使い方: python tools/run_weapon_lp_shots.py [-j 3] [武器の id ...]   (省くと 13 個全部)
結果: 各 run/shots/weapon_lp_<id>/shots/weapon_lp_<id>/shots.log の最後の行を表示する
"""
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALL = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw", "coral_shield",
       "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker"]


def main():
    args = sys.argv[1:]
    jobs = 3
    if "-j" in args:
        i = args.index("-j")
        jobs = int(args[i + 1])
        del args[i:i + 2]
    items = args or ALL
    running = []
    queue = list(items)
    while queue or running:
        running = [(i, p) for i, p in running if p.poll() is None]
        while queue and len(running) < jobs:
            item = queue.pop(0)
            p = subprocess.Popen([str(ROOT / "gradlew.bat"), "runShots", f"-Pscript=tools/shots/weapon_lp_{item}.txt", "--offline", "-q"], cwd=ROOT,
                                 stdout=open(ROOT / "build" / f"run_{item}.out", "w"), stderr=subprocess.STDOUT)
            running.append((item, p))
            time.sleep(25)
        time.sleep(5)
    for item in items:
        log = ROOT / f"run/shots/weapon_lp_{item}/shots/weapon_lp_{item}/shots.log"
        print(item, log.read_text(encoding="utf-8").strip().splitlines()[-1] if log.exists() else "no log")


if __name__ == "__main__":
    main()
