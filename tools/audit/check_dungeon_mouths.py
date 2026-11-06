# -*- coding: utf-8 -*-
"""地下のダンジョンの入口 (data/skyabyss/opening/poi_dungeon_<id>.json) を、地上の口の前から入口の間の帰り着く所 (印 back) まで
体の高さ 2 で歩いて行けるか、設計図の上で辿って確かめる (2026-11-06 の声「ダンジョンの入口、閉じてる」: 口の前の地面の層が坑の頭を塞いでいた)。
設計図に無い所は、地面より下 (y < 0) は土、上は空き (整地) とみる。当たりの無い品 (灯り・鎖・碑・線路など) は通れる。
    python tools/audit/check_dungeon_mouths.py
"""
import json
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OPENING = ROOT / "src/main/resources/data/skyabyss/opening"
SOFT = ("lantern", "chain", "torch", "rail", "carpet", "button", "tablet", "sign", "vine", "flower", "short_grass", "fern", "candle")


def solid(s):
    if s is None:
        return None
    if s == "minecraft:air":
        return False
    return not any(k in s for k in SOFT)


def walkable(path):
    d = json.loads(path.read_text(encoding="utf-8"))
    pal = d["palette"]
    cells = {(b[0], b[1], b[2]): pal[b[3]] for b in d["blocks"]}
    back = tuple(d["marks"]["back"])

    def free(x, y, z):
        s = solid(cells.get((x, y, z)))
        return y >= 0 if s is None else not s

    start = (0, 0, 6)
    q = deque([start])
    seen = {start}
    while q:
        p = q.popleft()
        if p == back:
            return True
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (p[0] + dx, p[1] + dy, p[2] + dz)
            if n in seen or not (-20 <= n[0] <= 20 and -30 <= n[1] <= 8 and -90 <= n[2] <= 12):
                continue
            if free(*n) and free(n[0], n[1] + 1, n[2]):
                seen.add(n)
                q.append(n)
    return False


def main():
    files = sorted(OPENING.glob("poi_dungeon_*.json"))
    bad = [f.name for f in files if not walkable(f)]
    for b in bad:
        print("closed:", b)
    print(f"check_dungeon_mouths: {len(files)} entrances, {len(bad)} closed")
    return 1 if bad or not files else 0


if __name__ == "__main__":
    sys.exit(main())
