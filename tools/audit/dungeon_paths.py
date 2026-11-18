# -*- coding: utf-8 -*-
"""ダンジョンの層 (data/skyabyss/dungeon/*.json) を、入口 (entry) から先へ歩いて行けるかの確かめ。
部屋の層は entry → next、主の間は entry → boss (主の立つ所の近く)。
扉 (door_*_lo / _hi の箱) は開いたものとして通す。歩ける = 足の下が固い・体の 2 マスが通れる。
一歩で 1 段まで上がれ (階段・半ブロックも)、4 段まで落ちられる。梯子・蔓・水の中は上下に動ける。
使い方: python -I tools/audit/dungeon_paths.py [名前の一部]
"""
import json
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIR = ROOT / "src/main/resources/data/skyabyss/dungeon"

PASS_KEYS = ("light_block", "torch", "button", "carpet", "pressure_plate", "flower", "grass", "fern", "lichen", "vine", "ladder",
             "sign", "banner", "rail", "tripwire", "lever", "seagrass", "kelp", "coral", "pickle", "sapling",
             "redstone_wire", "mushroom", "bush", "roots", "sprouts", "cobweb", "water", "bubble", "bars")
CLIMB_KEYS = ("ladder", "vine", "water", "kelp", "seagrass", "scaffolding", "bubble")


def passable(b):
    if b is None:
        return True
    name = b.split("[")[0].replace("minecraft:", "")
    if name in ("air", "cave_air", "void_air", "light"):
        return True
    if "slab" in name and "type=bottom" in b:
        return False
    if name == "snow":
        return "layers=1" in b or "layers=2" in b
    if name == "chain":
        return "axis=y" in b
    return any(k in name for k in PASS_KEYS) and "glass" not in name


def solid_floor(b):
    if b is None:
        return False
    name = b.split("[")[0]
    if passable(b):
        return "bars" in name and False
    return True


def load(path):
    d = json.loads(path.read_text(encoding="utf-8"))
    pal = d["palette"]
    cells = {}
    for run in d["runs"]:
        y, z, x = run[0], run[1], run[2]
        i = 3
        while i < len(run):
            idx, n = run[i], run[i + 1]
            for k in range(n):
                if idx >= 0:
                    cells[(x + k, y, z)] = pal[idx]
            x += n
            i += 2
    return d, cells


def door_cells(marks):
    out = set()
    for k, lo in marks.items():
        if k.startswith("door_") and k.endswith("_lo"):
            hi = marks.get(k[:-3] + "_hi")
            if hi:
                for x in range(min(lo[0], hi[0]), max(lo[0], hi[0]) + 1):
                    for y in range(min(lo[1], hi[1]), max(lo[1], hi[1]) + 1):
                        for z in range(min(lo[2], hi[2]), max(lo[2], hi[2]) + 1):
                            out.add((x, y, z))
    return out


def check(path):
    d, cells = load(path)
    marks = d["marks"]
    doors = door_cells(marks)

    def at(p):
        return None if p in doors else cells.get(p)

    def free(p):
        return passable(at(p))

    def climb(p):
        b = at(p)
        return b is not None and any(k in b.split("[")[0] for k in CLIMB_KEYS)

    def stand(p):
        x, y, z = p
        if not (free(p) and free((x, y + 1, z))):
            return False
        return solid_floor(at((x, y - 1, z))) or climb(p) or climb((x, y - 1, z))

    goal_name = "next" if "next" in marks else "boss" if "boss" in marks else None
    if "entry" not in marks or goal_name is None:
        return None
    start = tuple(marks["entry"])
    goal = tuple(marks[goal_name])
    # 入口の立てる所 (印の高さの前後)
    starts = [(start[0], start[1] + dy, start[2]) for dy in (0, 1, -1, 2)]
    starts = [s for s in starts if stand(s)]
    if not starts:
        return f"entry {start} is not standable"
    seen = set(starts)
    q = deque(starts)
    near = 2 if goal_name == "next" else 6
    best = None
    while q:
        p = q.popleft()
        x, y, z = p
        dist = abs(x - goal[0]) + abs(z - goal[2]) + abs(y - goal[1])
        if best is None or dist < best[0]:
            best = (dist, p)
        if abs(x - goal[0]) <= near and abs(z - goal[2]) <= near and abs(y - goal[1]) <= 2:
            return None
        nxt = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (1, 0, -1, -2, -3, -4):
                n = (x + dx, y + dy, z + dz)
                if dy == 1 and not free((x, y + 2, z)):
                    continue
                if stand(n):
                    # 落ちる時は、横の列の上が空いていること
                    if dy < 0 and not all(free((x + dx, y + k, z + dz)) for k in range(dy + 1, 2)):
                        continue
                    nxt.append(n)
                    break
        if climb(p) or climb((x, y - 1, z)):
            for dy in (1, -1):
                n = (x, y + dy, z)
                if stand(n):
                    nxt.append(n)
        for n in nxt:
            if n not in seen:
                seen.add(n)
                q.append(n)
    return f"{goal_name} {goal} not reached; closest {best[1]} (off {best[0]}), walked {len(seen)} cells"


def main():
    pat = sys.argv[1] if len(sys.argv) > 1 else ""
    bad = 0
    for f in sorted(DIR.glob("*.json")):
        if pat not in f.stem:
            continue
        r = check(f)
        print(("OK   " if r is None else "FAIL ") + f.stem + ("" if r is None else "  " + r))
        bad += r is not None
    print(f"{bad} floor(s) with no way through")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
