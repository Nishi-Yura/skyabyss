# -*- coding: utf-8 -*-
"""通り道の規則 (build audit と同じ見方)。town.py の灯り (柱・腕・吊り灯り) はここを通してから置く。tools/capital/audit.py も使う。

 - 戸の通り道 (DOOR_BLOCKED): 戸の下の段の、戸の向きの両側 (前と後ろ) の一マス、その上の一マスが、どちらも空いていること
 - 頭の上 (HEAD_DENT): 幅 1 の廊下の真ん中で、頭の高さ (足もとの一つ上) にブロックが出ていないこと。
   足もと F が空いていて、F の下が床、F の左右 (廊下の横) は壁、前後 (廊下の筋) は高さ 3 の空いた列
   (BuildAudit.java の DOOR_BLOCKED と BuildAuditExtra.java の HEAD_DENT の同じ規則)

使い方:
    passage.door_blocked(g, x, y, z, facing)   # 戸 (下の半分) の通り道が塞がっているか
    passage.head_dent_at(g, x, y, z)           # (x, y, z) に何か置くと、頭の上の出っぱりになるか (灯りの置き場に使う)
    passage.scan(cells)                        # 設計図全体の違反の一覧 [(kind, (x, y, z), 説明)]
"""
from __future__ import annotations

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)
sys.path.insert(0, os.path.join(_HERE, '..', 'audit'))
import check_stairs as cs  # noqa: E402
from vox import DX, OPP  # noqa: E402

GROUND = -1  # 設計図の地面の上面 (town.py の座標)。この下の設計図に無い所は固い


def grid(cells):
    return cs.Grid(cells, ground=GROUND)


def _clear(g, x, y, z):
    return g.top(x, y, z) == 0


def door_blocked(g, x, y, z, facing):
    """戸 (下の半分) の (x, y, z)。戸の向き facing の前と後ろの一マス (足と頭) のどちらかが塞がっていれば、その側の文字列を返す。通れれば None"""
    dx, dz = DX[facing]
    for side, (sx, sz) in (('front', (dx, dz)), ('back', (-dx, -dz))):
        nx, nz = x + sx, z + sz
        if not _clear(g, nx, y, nz) or not _clear(g, nx, y + 1, nz):
            return side
    return None


def _open_column(g, x, y, z):
    return _clear(g, x, y, z) and _clear(g, x, y + 1, z) and _clear(g, x, y + 2, z) and g.top(x, y - 1, z) >= 1


def head_dent_at(g, x, y, z):
    """(x, y, z) に何か (ブロック) を置いたとき、廊下の真ん中の頭の高さの出っぱりになるか。
    足もと (x, y-1, z) が空いていて床があり、両横が壁、前後が空いた廊下 (高さ 3 の空き) のときに True"""
    fx, fy, fz = x, y - 1, z
    if not _clear(g, fx, fy, fz) or g.top(fx, fy - 1, fz) < 1:
        return False
    for ax, az in ((0, -1), (1, 0)):  # 廊下の筋 (北 - 南、東 - 西)
        wx, wz = (1, 0) if az else (0, 1)  # 廊下の横 (筋に直角)
        walled = g.top(fx + wx, fy, fz + wz) >= 1 and g.top(fx - wx, fy, fz - wz) >= 1
        if walled and _open_column(g, fx + ax, fy, fz + az) and _open_column(g, fx - ax, fy, fz - az):
            return True
    return False


def _problems_at(g, cells, x, y, z):
    """(x, y, z) の一マスの違反 (あれば種類の一覧)。戸は下の半分だけ見る"""
    s = cells.get((x, y, z))
    if s is None:
        return []
    out = []
    n = cs.name(s)
    if n.endswith('_door') and cs.props(s).get('half') == 'lower':
        if door_blocked(g, x, y, z, cs.props(s).get('facing', 'south')):
            out.append('DOOR_BLOCKED')
    if cs.top(s) >= 1 and head_dent_at(g, x, y, z):
        out.append('HEAD_DENT')
    return out


def front_blocked(g, x, y, z, facing):
    """偽の戸 (跳ね上げ戸) と碑の前: 向き facing の前の二マス (足と頭) が空いているか。塞がっていれば True"""
    dx, dz = DX[facing]
    return not _clear(g, x + dx, y, z + dz) or not _clear(g, x + dx, y + 1, z + dz)


def placement_ok(cells, changes, reach=2, doors=(), stands=()):
    """changes = {(x, y, z): 状態} を置いてよいか。置く前と後で、近く (reach マス) の違反が増えなければ True。
    doors = 偽の戸 (x, y, z, 向き) の一覧 (前が空いていること)、stands = 碑 (x, y, z, 向き) の一覧 (前が空いていること)。
    cells は書き換えて戻す (呼ぶ側の辞書は変わらない)。town.py の灯りの置き場 (柱・吊り灯り) はこれを通す"""
    if not changes:
        return True
    xs = [p[0] for p in changes]
    ys = [p[1] for p in changes]
    zs = [p[2] for p in changes]
    box = [(x, y, z)
           for x in range(min(xs) - reach, max(xs) + reach + 1)
           for y in range(min(ys) - reach, max(ys) + reach + 2)
           for z in range(min(zs) - reach, max(zs) + reach + 1)]
    def count(g):
        found = set()
        for (x, y, z) in box:
            for k in _problems_at(g, cells, x, y, z):
                found.add((k, x, y, z))
        for (x, y, z, f) in doors:
            if front_blocked(g, x, y, z, f):
                found.add(('FAKE_DOOR', x, y, z))
        for (x, y, z, f) in stands:
            if front_blocked(g, x, y, z, f):
                found.add(('STAND', x, y, z))
        return found

    before = count(grid(cells))
    saved = {p: cells.get(p) for p in changes}
    try:
        for p, s in changes.items():
            cells[p] = s
        after = count(grid(cells))
    finally:
        for p, s in saved.items():
            if s is None:
                cells.pop(p, None)
            else:
                cells[p] = s
    return after <= before


def scan(cells):
    """設計図の違反: DOOR_BLOCKED (戸の通り道) と HEAD_DENT (頭の上の出っぱり)。[(kind, (x, y, z), 説明)]"""
    g = grid(cells)
    out = []
    for (x, y, z), s in cells.items():
        n = cs.name(s)
        if n.endswith('_door') and cs.props(s).get('half') == 'lower':
            side = door_blocked(g, x, y, z, cs.props(s).get('facing', 'south'))
            if side:
                out.append(('DOOR_BLOCKED', (x, y, z), 'the %s side of the door is blocked' % side))
        if cs.top(s) >= 1 and head_dent_at(g, x, y, z):
            out.append(('HEAD_DENT', (x, y, z), '%s pokes down into a corridor at head height' % n))
    return out


if __name__ == '__main__':
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(_HERE, '..', '..', 'src', 'main', 'resources', 'data', 'skyabyss', 'opening', 'capital.json')
    _, cells = cs.load_blueprint(path)
    found = scan(cells)
    for kind, p, msg in found:
        print('%s at %s: %s' % (kind, p, msg))
    print('passage: %d problems' % len(found))
    sys.exit(1 if found else 0)
