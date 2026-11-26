# -*- coding: utf-8 -*-
"""前もって組んだ土地の看板が、元の世界 (書き出し) と同じ字かを比べる。

    python -I tools/gen/world/authored/sign_check.py gen [土地 ...]   # tools/shots/land_signs.txt を書く
    SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/land_signs.txt -PshotsName=land_signs
    python -I tools/gen/world/authored/sign_check.py cmp [土地 ...]   # 違う看板・無い看板を出す

元の看板は run/shots/land_<土地>/dumps/*.sbd の header の signs。組んだ世界では看板のまわりだけを小さく書き出す。
"""
import glob
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "structure"))
import sbd  # noqa: E402

LANDS = ["meadow", "outskirts", "whale", "tide", "storm", "ember"]
CELL = 160
SCRIPT = ROOT / "tools" / "shots" / "land_signs.txt"
OUT = ROOT / "run" / "shots" / "land_signs" / "dumps"


def text(s):
    return json.dumps([s.get("front"), s.get("back")], ensure_ascii=False, sort_keys=True)


def originals(land):
    out = {}
    for f in glob.glob(str(ROOT / "run" / "shots" / f"land_{land}" / "dumps" / "*.sbd")):
        for s in sbd.load(f).header.get("signs", []):
            out[tuple(s["pos"])] = s
    return out


def clusters(land):
    cl = {}
    for p in originals(land):
        cl.setdefault((p[0] // CELL, p[2] // CELL), []).append(p)
    return sorted(cl.items())


def gen(lands):
    L = ["# 組んだ土地の看板のまわりだけを書き出す。sign_check.py gen が書く", "world land_signs continent", "size 960 540",
         "timeout 10800", "render 8", "wait 100", "cmd skyabyss cinematic stop", "cmd time set 4000",
         "cmd gamerule doDaylightCycle false", "cmd gamerule doMobSpawning false", "cmd weather clear",
         "cmd skyabyss dev opening build", "wait 100", "cmd gamemode creative"]
    n = 0
    for land in lands:
        for (cx, cz), ps in clusters(land):
            x0, x1 = min(p[0] for p in ps) - 1, max(p[0] for p in ps) + 1
            y0, y1 = min(p[1] for p in ps) - 12, max(p[1] for p in ps) + 12  # 起伏で列が上下しても入るように
            z0, z1 = min(p[2] for p in ps) - 1, max(p[2] for p in ps) + 1
            L += [f"cmd tp @s {(x0 + x1) // 2} 200 {(z0 + z1) // 2}", "waitchunks 300", "wait 500",
                  f"cmd skyabyss dev dump sg_{land}_{cx}_{cz} {x0} {y0} {z0} {x1} {y1} {z1}", "waitdump"]
            n += 1
    SCRIPT.write_text("\n".join(L) + "\n", encoding="utf-8")
    print(f"wrote {SCRIPT.relative_to(ROOT)} ({n} spots)")


def cmp(lands):
    bad = 0
    for land in lands:
        now = {}
        for f in glob.glob(str(OUT / f"sg_{land}_*.sbd")):
            for s in sbd.load(f).header.get("signs", []):
                now[(s["pos"][0], s["pos"][2])] = s  # 起伏の土地は高さが変わるので x, z で合わせる
        orig = {(p[0], p[2]): s for p, s in originals(land).items()}
        missing = [p for p in orig if p not in now]
        diff = [p for p in orig if p in now and text(orig[p]) != text(now[p])]
        extra = [p for p in now if p not in orig]
        print(f"{land}: {len(orig)} signs; missing {len(missing)} {missing[:3]}; different {len(diff)} {diff[:3]}; extra {len(extra)} {extra[:3]}")
        for p in diff[:3]:
            print("   was", text(orig[p])[:160])
            print("   now", text(now[p])[:160])
        bad += len(missing) + len(diff)
    return bad


if __name__ == "__main__":
    mode, lands = sys.argv[1], sys.argv[2:] or LANDS
    if mode == "gen":
        gen(lands)
    else:
        sys.exit(1 if cmp(lands) else 0)
