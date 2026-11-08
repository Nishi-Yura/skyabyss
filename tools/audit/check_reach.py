# -*- coding: utf-8 -*-
"""この Mod が建てる建物で、上らせる気の無い足場を探す (ゲームは動かさない)。

    python tools/audit/check_reach.py                 # data/skyabyss/opening/*.json (地上の建物) と dungeon/*.json (地下の階) を全部
    python tools/audit/check_reach.py bone_pit        # 名前の一部で絞る
    ... -v 見つけた足場の場所を全部出す

2026-11-08 の声「このような上らせる気の無い建築のおき方やめて」(地下の坑の木組みの足場に、上がる段も梯子も無かった)。
足場の見方 (おおよそ。人の動きをまねる):
  立てる所  下が当たりのある床 (半ブロック・階段・丸ごと)、足と頭のマスに当たりが無い (柵・壁は 1.5 で跨げない)
  動ける    となりの立てる所へ、上り 1.0 まで (跳んで上がれる高さ)、下りは 8 まで。梯子・蔓・足場 (scaffolding) の柱は上下につながる
  始まり    印 entry / door / start (あれば)、地上の建物は地面の高さ (立つ高さ 0〜1) のすべての所
規則:
  DECK_UNREACHABLE  始まりから行けない平らな足場 (同じ高さで 4 マス以上つながる所) のうち、
                    ・縁に柵か壁 (手すり) がある、または
                    ・宝箱・印 (chest_*) がその上にある、または
                    ・地下の階 (dungeon/*.json) の板・半ブロックの床
                    屋根 (手すりの無い平らな屋根) は対象にしない
許す例は ALLOW に理由つきで書く。指摘が 1 つでもあれば終了コード 1。
"""
from __future__ import annotations

import glob
import json
import os
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_stairs import load_blueprint, name, props, top  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'skyabyss')

# 許す例: (設計図の名前の一部, 足場の一マス (x, y, z) か None, 理由)
ALLOW = [
    # 潮の洞 2 の難破船の帆桁 (帆の掛かる横木。足場ではない)
    ('tide_cave_2', (-11, 8, 2), 'shipwreck yard arm, not a deck'),
    # 柵の砦村の真ん中の塔 (封じた塔) の頂の狼煙の台。屋根の飾り
    ('hamlet_', (-3, 20, -3), 'beacon fire on top of the sealed watch tower'),
    # 四章 煤の窯の跡: 崩れた窯の上 (煉瓦の壁の残り)。屋根で足場ではない
    ('ch4_soot_kiln', None, 'top of the ruined kiln'),
    # 都と土地の町の城壁の上 (封じた塔のあいだの胸壁) と塔の頂。登れるようにするかは持ち主が決める (2026-11-08)
    ('town_', None, 'rampart tops between sealed towers (owner decides)'),
    ('capital', None, 'rampart tops between sealed towers (owner decides)'),
]

CLIMB = ('ladder', 'vine', 'scaffolding', 'twisting_vines', 'weeping_vines', 'cave_vines')
UP = 1.01
DOWN = 8.0


def passable(s):
    return top(s) == 0.0 or name(s) in CLIMB


def climbable(s):
    return s is not None and any(c in name(s) for c in CLIMB)


def surfaces(cells):
    """立てる所: (x, y, z) -> 立つ高さ (y はそのマス = 足のマス)"""
    out = {}
    for (x, y, z), s in cells.items():
        t = top(s)
        n = name(s)
        if t == 0.0 or t > 1.0:
            continue
        # 半ブロック (下) と階段は、そのマスの中に立つ
        if n.endswith('_slab') and props(s).get('type') == 'bottom' or n.endswith('_stairs'):
            feet = (x, y, z)
            stand = y + (0.5 if n.endswith('_slab') else 1.0)
            if passable(cells.get((x, y + 1, z))) and passable(cells.get((x, y + 2, z))) if n.endswith('_stairs') else passable(cells.get((x, y + 1, z))):
                out[(x, y + 1, z) if n.endswith('_stairs') else feet] = stand
            continue
        feet = (x, y + 1, z)
        if feet in cells and not passable(cells[feet]):
            continue
        if not passable(cells.get((x, y + 2, z))):
            continue
        out[feet] = y + t
    # 梯子の柱の中も立てる所として数える (上下につなぐため)
    for (x, y, z), s in cells.items():
        if climbable(s):
            out.setdefault((x, y, z), float(y))
    return out


def reach(cells, surf, starts):
    by_col = {}
    for (x, y, z), h in surf.items():
        by_col.setdefault((x, z), []).append(((x, y, z), h))
    seen = set(starts)
    q = deque(starts)
    while q:
        p = q.popleft()
        x, y, z = p
        h = surf[p]
        cand = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cand.extend(by_col.get((x + dx, z + dz), ()))
        if climbable(cells.get(p)) or climbable(cells.get((x, y - 1, z))):
            cand.extend(by_col.get((x, z), ()))
        # 一マスの穴は跳び越せる (同じ高さか下へ。間のマスの足と頭が空いていること)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            mid = (x + dx, y, z + dz)
            if mid in surf or not passable(cells.get(mid)) or not passable(cells.get((x + dx, y + 1, z + dz))):
                continue
            for q2, h2 in by_col.get((x + 2 * dx, z + 2 * dz), ()):
                if q2 not in seen and -2.0 <= h2 - h <= 0.01:
                    seen.add(q2)
                    q.append(q2)
        for q2, h2 in cand:
            if q2 in seen:
                continue
            dh = h2 - h
            if q2[0] == x and q2[2] == z:
                ok = abs(q2[1] - y) <= 1 and (climbable(cells.get(q2)) or climbable(cells.get((x, q2[1] - 1, z))) or dh < 0)
            else:
                ok = dh <= UP and -dh <= DOWN
            if ok:
                seen.add(q2)
                q.append(q2)
    return seen


def railing(cells, comp):
    edge = 0
    for (x, y, z) in comp:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = (x + dx, y, z + dz)
            if nb in comp:
                continue
            n = name(cells.get(nb))
            if n.endswith(('_fence', '_wall')):
                edge += 1
    return edge


def decks(cells, surf, reached):
    """行けない立てる所を、同じ高さでつながる塊に分ける"""
    left = {p for p in surf if p not in reached and not climbable(cells.get(p))}
    out = []
    while left:
        p = left.pop()
        comp = {p}
        q = deque([p])
        while q:
            x, y, z = q.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nb = (x + dx, y, z + dz)
                if nb in left and abs(surf[nb] - surf[(x, y, z)]) < 0.01:
                    left.discard(nb)
                    comp.add(nb)
                    q.append(nb)
        out.append(comp)
    return out


def check(path, verbose=False):
    d, cells = load_blueprint(path)
    base = os.path.splitext(os.path.basename(path))[0]
    underground = os.sep + 'dungeon' + os.sep in path or '/dungeon/' in path.replace('\\', '/')
    marks = d.get('marks') or {}
    if isinstance(marks, list):
        marks = {}
    # 開く戸 (鉄格子の戸・扉の箱 door_*_lo / door_*_hi) は開いているものとして見る
    for k, lo in marks.items():
        if k.startswith('door_') and k.endswith('_lo'):
            hi = marks.get(k[:-3] + '_hi')
            if hi:
                for x in range(min(lo[0], hi[0]), max(lo[0], hi[0]) + 1):
                    for y in range(min(lo[1], hi[1]), max(lo[1], hi[1]) + 1):
                        for z in range(min(lo[2], hi[2]), max(lo[2], hi[2]) + 1):
                            cells.pop((x, y, z), None)
    surf = surfaces(cells)
    starts = []
    for k in ('entry', 'door', 'start', 'spawn'):
        m = marks.get(k)
        if m and len(m) >= 3:
            for dy in (0, 1, -1):
                p = (m[0], m[1] + dy, m[2])
                if p in surf:
                    starts.append(p)
                    break
    if not underground:
        starts.extend(p for p, h in surf.items() if -0.01 <= h <= 1.01)
    if not starts:
        return []
    reached = reach(cells, surf, starts)
    chests = {tuple(v[:3]) for k, v in marks.items() if k.startswith('chest') and isinstance(v, list) and len(v) >= 3}
    found = []
    for comp in decks(cells, surf, reached):
        if len(comp) < 4:
            continue
        h = surf[next(iter(comp))]
        floor_blocks = [name(cells.get((x, y - 1, z)) if not name(cells.get((x, y, z))).endswith(('_slab', '_stairs')) else cells.get((x, y, z))) for (x, y, z) in comp]
        wooden = sum(1 for n in floor_blocks if n.endswith(('_planks', '_slab', '_log', '_wood'))) >= len(comp) * 0.6
        # 地上の建物: 屋根の下 (封じた家の二階など。外から見えない) は数えない。空の見える足場だけ
        if not underground:
            covered = sum(1 for (x, y, z) in comp if any(top(cells.get((x, y + k, z))) > 0 for k in range(2, 10)))
            if covered >= len(comp) * 0.5:
                continue
        rail = railing(cells, comp)
        chest = any((x, y, z) in chests or (x, y - 1, z) in chests for (x, y, z) in comp) or any(c in comp for c in chests)
        if rail >= 3 or chest or underground and wooden:
            sample = min(comp)
            if any(a[0] in base and (a[1] is None or a[1] in comp) for a in ALLOW):
                continue
            why = 'chest' if chest else 'railing' if rail >= 3 else 'wooden floor underground'
            found.append((base, sample, len(comp), h, why))
    return found


def main(argv):
    verbose = '-v' in argv
    names = [a for a in argv if not a.startswith('-')]
    paths = sorted(glob.glob(os.path.join(DATA, 'opening', '*.json'))) + sorted(glob.glob(os.path.join(DATA, 'dungeon', '*.json')))
    total = 0
    for path in paths:
        base = os.path.splitext(os.path.basename(path))[0]
        if names and not any(n in base for n in names):
            continue
        try:
            found = check(path, verbose)
        except (KeyError, ValueError, TypeError) as e:
            print(f'{base}: skipped ({e})')
            continue
        for b, sample, size, h, why in found:
            total += 1
            print(f'DECK_UNREACHABLE {b} at {sample} ({size} cells, stand {h:g}, {why})')
    print(f'check_reach: {total} findings')
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
