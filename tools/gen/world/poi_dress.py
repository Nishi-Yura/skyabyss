"""大陸の見どころ (データを直接直す（生成スクリプトはない） の部品) に、土地の町と同じ家の型と仕上げを使う (docs/design/45)。

- hamlet_houses(bp, region, houses): 集落の家を家の型 (tools/capital/massing.build_house) で建てて、部品 (BP) に写す
- dress_bp(bp, region): 部品に仕上げ (風化の斑・窓の鎧戸と花の箱・蔦) を掛ける。建物ごとの柱は、地面の上のブロックのつながりで分ける

部品 (BP) の名前は minecraft: 付き、町の道具 (Vox) は付けない。写すときに付け外しする。
"""
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'capital'))

import vox  # noqa: E402
import dress  # noqa: E402
import massing as M  # noqa: E402

MC = 'minecraft:'


def _style_town(region):
    """町の道具を使うための入れ物 (作風・乱数・Vox)。都のまわりの野 (meadow) は都の作風"""
    from region_town import RegionTown, STYLES
    import town as T
    if region in STYLES:
        t = RegionTown(region)
    else:
        t = T.Town(vox.STYLES['b'])
    t.v = vox.Vox(seed=len(region) * 977 + 13)
    t.rng = t.v.rng
    t.parts = []
    t.used = []
    return t


def _to_bp(state):
    return state if ':' in state else MC + state


def _from_bp(state):
    return state[len(MC):] if state.startswith(MC) else state


def hamlet_houses(bp, region, houses):
    """houses = [(rect, front, floors, kind, roof_kind)]。家の型で建てて bp に写す。返り値 = 写したブロックの数"""
    t = _style_town(region)
    for (rect, front, floors, kind, roof_kind) in houses:
        mat = t.st.roof if t.rng.random() < 0.6 or not t.st.roof_alts else t.rng.choice(list(t.st.roof_alts))
        M.build_house(t, kind, rect, front, floors, roof_kind, mat=mat, extras=('chimney',) if floors >= 2 else ())
    t.fix_stairs()
    n = 0
    for (x, y, z), s in t.v.b.items():
        if s in ('air',):
            continue
        bp.set(x, y, z, _to_bp(s))
        n += 1
    for m, p in t.v.marks.items():
        bp.mark(m, *p)
    return n


def dress_bp(bp, region, ivy_share=0.5):
    """部品に仕上げを掛ける (地面の下は触らない)。返り値 = dress の数の表"""
    t = _style_town(region)
    v = t.v
    for (x, y, z), i in bp.cells.items():
        v.b[(x, y, z)] = _from_bp(bp.palette[i])
    # 建物ごとの柱: y >= 1 にブロックのある柱の 8 方のつながり
    up = {(x, z) for (x, y, z), s in v.b.items() if y >= 1 and s not in ('air', 'cave_air')}
    owner = {}
    n = 0
    for c in up:
        if c in owner:
            continue
        n += 1
        owner[c] = n
        dq = deque([c])
        while dq:
            a = dq.popleft()
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    b = (a[0] + dx, a[1] + dz)
                    if b in up and b not in owner:
                        owner[b] = n
                        dq.append(b)
    t.owner = owner
    t.occ = {c: 'bld' for c in owner}
    t.h = {}
    r = {'weather': dress.weather(v, len(region))}
    r['shutters'], r['boxes'] = dress.dress_facades(t)
    r['ivy'] = dress.ivy(t, share=ivy_share)
    for (x, y, z), s in v.b.items():
        if s in ('air',) and (x, y, z) not in bp.cells:
            continue
        bp.set(x, y, z, _to_bp(s))
    return r
