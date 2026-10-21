# -*- coding: utf-8 -*-
"""ゲーム内の監査 (run/build_audit_report.txt) の都の見つけた所を、設計図の座標に直して種類ごとに数える。

python -I tools/capital/audit_map.py <ox> <oy> <oz> <rot> [種類...]
  ox oy oz rot = 報告の o= と r= (SiteAudit の label)。設計図の座標 (x, y, z) と、そこにある物を出す
"""
import os
import re
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vox  # noqa: E402
import town  # noqa: E402


def inv(x, z, rot):
    for _ in range(rot & 3):
        x, z = z, -x
    return x, z


def main():
    ox, oy, oz, rot = (int(a) for a in sys.argv[1:5])
    kinds = set(sys.argv[5:])
    t = town.Town(vox.STYLES['b'])
    v = t.build()
    rep = open(os.path.join('run', 'build_audit_report.txt'), encoding='utf-8').read().split('\n')
    kind = None
    rows = []
    for ln in rep:
        if ln.startswith('### '):
            kind = ln[4:].split(' ')[0]
        m = re.match(r'- (-?\d+),(-?\d+),(-?\d+): (.*)', ln)
        if m and kind and (not kinds or kind in kinds):
            rows.append((kind, int(m.group(1)), int(m.group(2)), int(m.group(3)), m.group(4)))
    # y のずれを、報告の物の名前と設計図が合う所で決める
    best, bdy = -1, 0
    for dy in range(-4, 5):
        ok = 0
        for (k, x, y, z, msg) in rows[:300]:
            bx, bz = inv(x - ox, z - oz, rot)
            s = v.get(bx, y - oy + dy, bz)
            mm = re.search(r'block\.minecraft\.([a-z_]+)\)', msg)
            if s and mm and s.split('[')[0] == mm.group(1):
                ok += 1
        if ok > best:
            best, bdy = ok, dy
    if best <= 0:
        bdy = -1
    print('dy', bdy, 'matched', best)
    per = Counter()
    for (k, x, y, z, msg) in rows:
        bx, bz = inv(x - ox, z - oz, rot)
        by = y - oy + bdy
        where = 'other'
        for (nm, a, b, c, d) in t.parts:
            if a <= bx <= c and b <= bz <= d and nm != 'block':
                where = nm.split('@')[0]
                break
        per[(k, where)] += 1
        if len(sys.argv) > 5:
            print('%s %s at bp (%d,%d,%d): %s | %s' % (k, where, bx, by, bz, (v.get(bx, by, bz) or 'air').split('[')[0], msg[:140]))
    for (k, w), n in sorted(per.items(), key=lambda kv: -kv[1])[:40]:
        print('%-16s %-18s %d' % (k, w, n))


if __name__ == '__main__':
    main()
