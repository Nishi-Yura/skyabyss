# -*- coding: utf-8 -*-
"""地下のダンジョンの入口 (data/skyabyss/opening/poi_dungeon_<id>.json) を、地上の口の前から入口の間の帰り着く所 (印 back) まで
人の歩き方 (1 ブロックまで跳んで上がれる・体の高さ 2) で行けるか、設計図の上で辿って確かめる (2026-11-06 の声「ダンジョンの入口、閉じてる」: 口の前の地面の層が坑の頭を塞いでいた)。
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
    """人の歩き方で辿る: 足もとの高さ (半ブロック刻み) を持ち、隣へは 1 ブロックまで上がれ (跳ぶ)、4 ブロックまで降りられる。
    下の段・半ブロックの台は足もとを半分上げる。頭の上は 2 ブロック空いていること (2026-11-06 の声「登れない。階段一マス無くて」:
    床から最後の段まで 1.5 あったのを、前の辿り方 (上下に一マスずつ動ける) は見逃していた)"""
    d = json.loads(path.read_text(encoding="utf-8"))
    pal = d["palette"]
    cells = {(b[0], b[1], b[2]): pal[b[3]] for b in d["blocks"]}
    back = tuple(d["marks"]["back"])

    def state(x, y, z):
        s = cells.get((x, y, z))
        return ("soil" if y < 0 else None) if s is None else s

    def free(x, y, z):
        s = state(x, y, z)
        if s is None:
            return True
        if s == "soil":
            return False
        return not solid(s)

    def half(s):
        return isinstance(s, str) and ("stairs" in s or "_slab" in s) and "half=top" not in s and "type=top" not in s and "type=double" not in s

    def surfaces(x, z, lo, hi):
        """柱 (x, z) で足を置ける高さ (2 倍した整数) の一覧。lo..hi は足もとの y の範囲"""
        out = []
        for y in range(lo - 1, hi + 1):
            s = state(x, y, z)
            if s is None or free(x, y, z):
                continue
            top2 = 2 * y + (1 if half(s) else 2)
            fy = y + 1
            if free(x, fy, z) and free(x, fy + 1, z):
                out.append(top2)
        return out

    def reach(start, goal):
        q = deque([start])
        seen = {start}
        while q:
            x, z, f2 = q.popleft()
            if (x, z) == (goal[0], goal[1]) and abs(f2 - goal[2]) <= 1:
                return True
            step(x, z, f2, seen, q)
        return False

    def step(x, z, f2, seen, q):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (-20 <= nx <= 20 and -90 <= nz <= 12):
                continue
            fy = f2 // 2
            for t2 in surfaces(nx, nz, fy - 5, fy + 2):
                if t2 - f2 > 2 or f2 - t2 > 8:
                    continue
                n = (nx, nz, t2)
                if n not in seen:
                    seen.add(n)
                    q.append(n)

    # 行き (口の前 → 帰り着く所) と帰り (帰り着く所 → 口の前。段を登る)
    mouth = (0, 6, 0)
    home = (back[0], back[2], 2 * back[1])
    return reach(mouth, home) and reach(home, mouth)


def main():
    files = sorted(OPENING.glob("poi_dungeon_*.json"))
    bad = [f.name for f in files if not walkable(f)]
    for b in bad:
        print("closed:", b)
    print(f"check_dungeon_mouths: {len(files)} entrances, {len(bad)} closed")
    return 1 if bad or not files else 0


if __name__ == "__main__":
    sys.exit(main())
