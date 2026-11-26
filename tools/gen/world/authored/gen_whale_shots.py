# -*- coding: utf-8 -*-
"""鯨見の町の試作の撮影の台本を書く (前 = 今の世界 whale_authored、後 = 前もって組んだ土地の新しい世界 whale_after)。
同じ場所・同じ向きから撮るので、並べて比べられる。

    python -I tools/gen/world/authored/gen_whale_shots.py
    SKYABYSS_AUTHORED=0 SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/whale_cmp_before.txt -PshotsName=whale_authored
    SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/whale_cmp_after.txt -PshotsName=whale_after
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]

# 名前, x, y, z, yaw (0=南 90=西 180=北 270=東), pitch。y が None の所は、組んだ土地の地面 + 2 (道から見た目の高さ)
VIEWS = [
    ("air_se", -10, 165, -650, 135, 30),
    ("air_nw", -240, 165, -880, 315, 30),
    ("air_far_s", -120, 175, -560, 180, 22),
    ("top", -120, 250, -760, 0, 90),
    ("gate_east", -10, None, -761, 90, 4),
    ("gate_west", -226, None, -724, 225, 4),
    ("gate_north", -118, None, -870, 0, 4),
    ("road_far_e", 60, None, -730, 115, 4),
    ("hill_view", -270, 172, -600, 225, 16),
    ("spire_se", -160, 138, -700, 53, -4),
    ("spire_nw", -140, 142, -850, 135, -4),
    ("trees_all", -45, 150, -705, 0, 10),
    ("massif_s", -20, 145, -680, 34, 2),
    ("grove_over", -54, 152, -868, 180, 18),
    ("grove_in", -54, None, -893, 180, -4),
    ("tree_a", -80, 142, -680, 0, -3),
    ("tree_b", -45, 142, -685, 0, -3),
    ("tree_c", -10, 142, -680, 0, -3),
    ("pond", 25, 146, -598, 180, 14),
    ("arch", 2, None, -805, 233, -6),
]


def ground(x, z):
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import alib
    return alib.tile_ground("whale", x, z)


def script(world: str, kind: str, build: bool) -> str:
    out = [f"# 鯨見の町の試作の比べ ({'後' if build else '前'})。gen_whale_shots.py が書く",
           f"world {world} {kind}", "size 1600 900", "timeout 3000", "render 12", "guiscale 2", "clouds off", "wait 100",
           "cmd skyabyss cinematic stop", "cmd time set 4000", "cmd gamerule doDaylightCycle false", "cmd gamerule doMobSpawning false",
           "cmd weather clear"]
    if build:
        out += ["cmd skyabyss dev opening build", "wait 100", "cmd gamemode creative", "cmd tp @s -120 118 -760",
                "waitchunks 600", "wait 3600"]
    out += ["cmd gamemode spectator", "hud off"]
    for name, x, y, z, yaw, pitch in VIEWS:
        if y is None:
            y = ground(x, z) + 2
        out += [f"camera {x} {y} {z} {yaw} {pitch}", "waitchunks 600", "wait 60", "close", "wait 5", f"shot {name}"]
    return "\n".join(out) + "\n"


def main():
    shots = ROOT / "tools" / "shots"
    (shots / "whale_cmp_before.txt").write_text(script("whale_authored", "continent", False), encoding="utf-8")
    (shots / "whale_cmp_after.txt").write_text(script("whale_after", "continent", True), encoding="utf-8")
    print("wrote whale_cmp_before.txt / whale_cmp_after.txt")


if __name__ == "__main__":
    main()
