#!/usr/bin/env python3
"""都の空き家 3 軒 (旧厩舎・地図屋・空見の館) を入れなくする (2026-11-02 の声「完全に入れなくしてもいい。碑もなくしていい」)。

    python -X utf8 -I tools/capital/seal_vacant.py

capital.json をその場で書き換える (何度流しても同じ)。
- 戸 (上下 2 マス) を、戸の左右の壁と同じブロックで塞ぐ。
- 戸のわきの碑を外す (空気に)。
- 印を外す: 碑 sign_<名>、浮く名札 label_<名>、空見の館の戸 open_hall。
- 塞いだ所と外した所に印 seal_<名>_<番号> を置く。前の版で建てた都は、opening/site/CapitalSeal が
  この印の所を今の設計図のブロックで置き直す。
"""
from __future__ import annotations

import os
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "gen", "world"))
from town_outskirts import load, save  # noqa: E402

PATH = os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "skyabyss", "opening", "capital.json")

# 名 → (建物の外接の範囲 x0, z0, x1, z1、碑の印)
VACANT = {
    "stable": ((82, 6, 93, 17), "sign_stable"),
    "mapmaker": ((83, 69, 94, 79), "sign_mapmaker"),
    "hall": ((-58, -96, -40, -84), "sign_hall"),
}
DROP_MARKS = ["sign_stable", "sign_mapmaker", "sign_hall", "label_stable", "label_mapmaker", "open_hall"]


def is_door(s):
    return s is not None and s.split("[")[0].endswith("_door")


def wall_like(s):
    if s is None:
        return False
    n = s.split("[")[0]
    bad = ("air", "door", "pane", "glass", "stairs", "slab", "fence", "wall_", "sign", "banner", "lantern", "torch", "vine", "trapdoor", "button", "tablet")
    return not any(b in n for b in bad)


def wall_for(cells, x, y, z):
    """戸の左右 (と上下) の壁のブロックで、いちばん多いもの"""
    c = Counter()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for dy in (0, -1, 1):
            s = cells.get((x + dx, y + dy, z + dz))
            if wall_like(s):
                c[s] += 2 if dy == 0 else 1
    if not c:
        raise SystemExit("no wall next to the door at %s" % ((x, y, z),))
    return c.most_common(1)[0][0]


def main():
    d, cells = load(PATH)
    marks = d["marks"]
    changed = 0
    for name, ((x0, z0, x1, z1), sign) in VACANT.items():
        spots = [tuple(v) for k, v in marks.items() if k.startswith("seal_%s_" % name)]
        if not spots:
            doors = sorted(p for p, s in cells.items() if x0 <= p[0] <= x1 and z0 <= p[2] <= z1 and is_door(s))
            if not doors:
                raise SystemExit("no door in %s" % name)
            for p in doors:
                spots.append(p)
            if sign in marks:
                spots.append(tuple(marks[sign]))
        for k, p in enumerate(spots):
            s = cells.get(p)
            if is_door(s):
                cells[p] = wall_for(cells, *p)
                changed += 1
            elif s is not None and s.startswith("skyabyss:tablet"):
                cells[p] = "air"
                changed += 1
            marks["seal_%s_%d" % (name, k)] = list(p)
        print(name, [(p, cells.get(p)) for p in spots])
    for k in DROP_MARKS:
        marks.pop(k, None)
    save(PATH, d, cells)
    print("changed", changed, "cells")


if __name__ == "__main__":
    main()
