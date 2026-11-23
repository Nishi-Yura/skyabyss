# -*- coding: utf-8 -*-
"""全装備の数値の表 (run/gear_stats.tsv。自己テスト "gear dump" が書く) を、目安の線と比べる。

  SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="gear dump" bash tools/dev/mc_run.sh runServer
  python -X utf8 -I tools/dev/gear_table.py [--json out.json]   → 外れの多い順の要約を出す

目安: 武器は docs/design/51 の 2 章の E(L) (必要 Lv の巨神のエピック相当の 1 秒あたり)、弓は B(L) (矢の倍率)。
近接の 1 秒あたり = (1 + 攻撃の上乗せ) × (4 + 速さの差) (攻撃は床の足しを含んだ手の値) (item/TierFloor と同じ)。能力 (発動) の上乗せは含まない。
防具は 4 部位の合計を 51 の 2 章の重の表 (防御・強靭) と比べる。
"""
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
E_PTS = [(1, 8.0), (8, 10.0), (14, 12.6), (20, 14.6), (26, 16.0), (40, 19.4), (50, 23.0)]
B_PTS = [(1, 1.0), (8, 1.15), (14, 1.45), (20, 1.75), (26, 1.85), (40, 2.15), (50, 2.2)]
ARMOR_PTS = [(3, 12, 0), (6, 15, 3), (9, 19, 7), (12, 19, 8), (16, 20, 10), (19, 21, 12), (22, 21, 13), (26, 22, 15), (30, 22, 16),
             (32, 22, 15), (35, 23, 16), (38, 23, 16), (40, 23, 17), (42, 24, 17), (44, 24, 18), (46, 25, 18), (48, 25, 19), (50, 26, 20)]
TIERS = ['common', 'uncommon', 'rare', 'epic', 'heroic', 'artifact', 'mythic']
PIECES = ('_helmet', '_chestplate', '_leggings', '_boots')


def lerp(pts, x):
    if x <= pts[0][0]:
        return pts[0][1:]
    for a, b in zip(pts, pts[1:]):
        if x <= b[0]:
            t = (x - a[0]) / (b[0] - a[0])
            return tuple(p + (q - p) * t for p, q in zip(a[1:], b[1:]))
    return pts[-1][1:]


def f(x):
    return float(x) if x else 0.0


def load():
    return list(csv.DictReader((ROOT / 'run/gear_stats.tsv').open(encoding='utf-8'), delimiter='\t'))


def weapons(rows):
    out = []
    for r in rows:
        lv = int(r['req_lv'])
        other = dict(kv.split('=', 1) for kv in r['other_attrs'].split() if kv.startswith('bow.'))
        if 'bow.multiplier' in other:
            v, ref, kind = float(other['bow.multiplier']), lerp(B_PTS, lv)[0], 'bow'
        elif r['slot'] == 'mainhand' and f(r['attack']) > 0:
            v = (1 + f(r["attack"])) * (4 + f(r["speed"]))   # attack は床の足し込み (ItemAttributeModifierEvent を通した値)
            ref, kind = lerp(E_PTS, lv)[0], 'melee'
        else:
            continue
        out.append(dict(id=r['id'], tier=r['tier'], job=r['job'], dungeon=r['dungeon'], lv=lv, kind=kind, value=round(v, 2),
                        ref=round(ref, 2), ratio=round(v / ref, 3), atk=f(r['attack']), spd=f(r['speed']), lines=r['lines']))
    return out


def armor_sets(rows):
    sets = defaultdict(list)
    for r in rows:
        for p in PIECES:
            if r['id'].endswith(p) and (f(r['armor']) or f(r['toughness'])):
                sets[r['id'][:-len(p)]].append(r)
    out = []
    for name, rs in sets.items():
        lv = max(int(r['req_lv']) for r in rs)
        a, t = sum(f(r['armor']) for r in rs), sum(f(r['toughness']) for r in rs)
        ra, rt = lerp([(p[0], p[1], p[2]) for p in ARMOR_PTS], lv)
        out.append(dict(id=name, tier=rs[0]['tier'], dungeon=rs[0]['dungeon'], lv=lv, pieces=len(rs), armor=round(a, 2), tough=round(t, 2),
                        ref_armor=round(ra, 1), ref_tough=round(rt, 1), armor_ratio=round(a / ra, 3), tough_diff=round(t - rt, 1),
                        kb=round(sum(f(r['kb_res']) for r in rs), 2), other=' '.join(r['other_attrs'] for r in rs if r['other_attrs'])))
    return out


def main():
    rows = load()
    W, A = weapons(rows), armor_sets(rows)
    if '--json' in sys.argv:
        Path(sys.argv[sys.argv.index('--json') + 1]).write_text(json.dumps(dict(weapons=W, armor=A), ensure_ascii=False), encoding='utf-8')
    print(f'weapons {len(W)} (melee {sum(w["kind"] == "melee" for w in W)}, bows {sum(w["kind"] == "bow" for w in W)}), armor sets {len(A)}')
    print('ratio to E(L)/B(L) by tier (min / median / max):')
    for t in TIERS:
        for k in ('melee', 'bow'):
            v = sorted(w['ratio'] for w in W if w['tier'] == t and w['kind'] == k)
            if v:
                print(f'  {t:9s} {k:5s} n={len(v):3d}  {v[0]:.2f} / {v[len(v) // 2]:.2f} / {v[-1]:.2f}')
    print('armor sum / table by tier (armor ratio min/median/max, tough diff min/max):')
    for t in TIERS:
        v = sorted(a['armor_ratio'] for a in A if a['tier'] == t)
        d = sorted(a['tough_diff'] for a in A if a['tier'] == t)
        if v:
            print(f'  {t:9s} n={len(v):2d}  {v[0]:.2f} / {v[len(v) // 2]:.2f} / {v[-1]:.2f}   tough {d[0]:+.1f}..{d[-1]:+.1f}')


if __name__ == '__main__':
    main()
