# -*- coding: utf-8 -*-
"""入っている模型を Blockbench に通し直す (2026-11-21「blockbench で直しを必ずしてね」)。
数値だけを .bbmodel へ直接書いた直しを、Blockbench で読み込み → 書き出し → 取り込みに置き換えるための流れ。

  python -I bbpass.py prep <id> <work>   → <work>/<id>.geo.json (箱) と <id>_tex.png、メッシュがあれば <id>_mesh.json を書き、
                                            Blockbench で打つ手順を出す
  python -I bbpass.py done <id> <work> [--edited]  → <work>/out_<id>.bbmodel (Blockbench の書き出し) の箱に名前と UV を戻し、
                                            元の模型と形・絵が同じか確かめてから取り込む (違えば止まる)

Blockbench (MCP) の手順: create_project(free) → from_geo_json(<id>.geo.json の絶対パス) → メッシュがあれば place_mesh(<id>_mesh.json の中身)
 → create_texture(name=<id>, data=<id>_tex.png の絶対パス, 幅・高さ・uv_width・uv_height)
 → export_model(codec project, path=<work>/out_<id>.bbmodel)"""
import base64
import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
X32 = ROOT / 'tools/models/x32'
cmd, i, work = sys.argv[1], sys.argv[2], Path(sys.argv[3])
work.mkdir(parents=True, exist_ok=True)
src = X32 / f'{i}.bbmodel'
d = json.loads(src.read_text(encoding='utf-8'))
tex = d['textures'][0]
# UV の大きさは絵の欄を信じる (resolution の欄が絵と食い違っている模型がある: wild_spark_dagger は 16x16 と 128x16)
W, H = tex.get('uv_width') or d['resolution']['width'], tex.get('uv_height') or d['resolution']['height']
png = base64.b64decode(tex['source'].split(',', 1)[1])


def world(e):
    o = e['origin']
    return {k: [v[j] + o[j] for j in range(3)] for k, v in e['vertices'].items()}


if cmd == 'prep':
    assert len(d['textures']) == 1, 'textures'
    (work / f'{i}_tex.png').write_bytes(png)
    cubes, meshes = [], []
    for e in d['elements']:
        if e.get('type') == 'mesh':
            assert not any(e.get('rotation') or [0, 0, 0]), e['name']
            keys = list(e['vertices']); V = world(e)
            meshes.append(dict(name=e['name'], position=[0, 0, 0], vertices=[[round(x, 4) for x in V[k]] for k in keys],
                               faces=[[keys.index(q) for q in fc['vertices']] for fc in e['faces'].values()]))
            continue
        f, t, o = e['from'], e['to'], e['origin']
        r = e.get('rotation') or [0, 0, 0]
        c = dict(origin=[-t[0], f[1], f[2]], size=[t[0] - f[0], t[1] - f[1], t[2] - f[2]], pivot=[-o[0], o[1], o[2]], rotation=[-r[0], -r[1], r[2]], uv={})
        for fn, fc in e['faces'].items():
            u = fc['uv']
            c['uv'][fn] = dict(uv=[u[2], u[3]], uv_size=[u[0] - u[2], u[1] - u[3]]) if fn in ('up', 'down') else dict(uv=[u[0], u[1]], uv_size=[u[2] - u[0], u[3] - u[1]])
        cubes.append(c)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {'identifier': f'geometry.{i}', 'texture_width': W, 'texture_height': H},
                                                               'bones': [dict(name='g_root', pivot=[0, 0, 0], cubes=cubes)]}]}
    (work / f'{i}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
    if meshes:
        (work / f'{i}_mesh.json').write_text(json.dumps(meshes, separators=(',', ':')), encoding='utf-8')
    print(f'{i}: cubes {len(cubes)} meshes {len(meshes)} tex {W}x{H}')
    print('geo ', (work / f'{i}.geo.json').as_posix())
    print('tex ', (work / f'{i}_tex.png').as_posix())
    if meshes:
        print('mesh', (work / f'{i}_mesh.json').as_posix(), (work / f'{i}_mesh.json').stat().st_size, 'bytes')
else:
    out = work / f'out_{i}.bbmodel'
    n = json.loads(out.read_text(encoding='utf-8'))
    OC = [e for e in d['elements'] if e.get('type') != 'mesh']; OM = {e['name']: e for e in d['elements'] if e.get('type') == 'mesh'}
    NC = [e for e in n['elements'] if e.get('type') != 'mesh']; NM = {e['name']: e for e in n['elements'] if e.get('type') == 'mesh'}
    assert len(OC) == len(NC), f'cubes {len(OC)} -> {len(NC)}'
    assert sorted(OM) == sorted(NM), 'mesh names'

    def geokey(e):
        r = e.get('rotation') or [0, 0, 0]
        return tuple(round(v, 2) for v in list(e['from']) + list(e['to']) + list(r)) + tuple(round(v, 2) for v in (e['origin'] if any(r) else [0, 0, 0]))
    pool = {}
    for e in OC:
        pool.setdefault(geokey(e), []).append(e)
    worst = 0.0
    for e in NC:
        k = geokey(e)
        if not pool.get(k):   # 丸めの境目: いちばん近い残り
            k = min((kk for kk, vv in pool.items() if vv), key=lambda kk: sum((a - b) ** 2 for a, b in zip(kk, geokey(e))))
            worst = max(worst, max(abs(a - b) for a, b in zip(k, geokey(e))))
        o = pool[k].pop(0)
        e['name'] = o['name']; e['autouv'] = 0
        for fn, fc in o['faces'].items():
            e['faces'][fn]['uv'] = fc['uv']
            e['faces'][fn]['texture'] = 0   # the project has the one texture made in Blockbench (create_texture); faces point at it
    ALLOW = '--edited' in sys.argv   # Blockbench の中で形を直した (箱の対応はいちばん近い箱で取る。取り込みの形の比べはしない)
    assert ALLOW or worst < 0.05, f'a cube moved by {worst}'
    for name, o in OM.items():
        e = NM[name]; VO = world(o); VN = world(e); m = {}
        for ok, p in VO.items():
            best = min(VN, key=lambda nk: sum((VN[nk][j] - p[j]) ** 2 for j in range(3)))
            assert sum((VN[best][j] - p[j]) ** 2 for j in range(3)) < 1e-3, (name, ok)
            m[ok] = best
        assert len(set(m.values())) == len(m), name
        nf = {frozenset(fc['vertices']): fc for fc in e['faces'].values()}
        assert len(nf) == len(o['faces']) == len(e['faces']), name
        for fc in o['faces'].values():
            g = nf[frozenset(m[x] for x in fc['vertices'])]
            g['vertices'] = [m[x] for x in fc['vertices']]; g['uv'] = {m[x]: fc['uv'][x] for x in fc['vertices']}
            g['texture'] = 0
    # 絵が同じか
    nt = n['textures'][0]
    a = Image.open(io.BytesIO(png)).convert('RGBA'); b = Image.open(io.BytesIO(base64.b64decode(nt['source'].split(',', 1)[1]))).convert('RGBA')
    assert a.size == b.size and a.tobytes() == b.tobytes(), 'texture differs'
    n['resolution'] = d['resolution']; nt['uv_width'], nt['uv_height'] = W, H
    n['groups'] = []; n['outliner'] = [e['uuid'] for e in n['elements']]
    # 元の並びへ
    order = {e['name']: k for k, e in enumerate(d['elements'])}
    n['elements'].sort(key=lambda e: order[e['name']]); n['outliner'] = [e['uuid'] for e in n['elements']]
    out.write_text(json.dumps(n, ensure_ascii=False), encoding='utf-8')
    look = ROOT / 'src/main/resources/assets/skyabyss/lowpoly/look/a' / f'{i}.json'
    before = json.loads(look.read_text(encoding='utf-8'))
    subprocess.run([sys.executable, '-I', str(ROOT / 'tools/models/x32/look5/install.py'), str(out), i], check=True, cwd=ROOT, capture_output=True)
    after = json.loads(look.read_text(encoding='utf-8'))
    import numpy as np
    def pts(m):
        return sorted(tuple(round(float(x), 2) for x in row) for bn in m['bones'] for row in np.array(m['bones'][bn]['vertices'], float).reshape(-1, 3))
    def uvs(m):
        return np.concatenate([np.array(m['bones'][bn]['uvs'], float).reshape(-1) for bn in sorted(m['bones'])])
    bv, av = pts(before), pts(after)
    same = len(bv) == len(av) and max((max(abs(p - q) for p, q in zip(x, y)) for x, y in zip(bv, av)), default=0) < 0.03
    bu, au = uvs(before), uvs(after)   # 絵の当て方 (UV) も同じか
    same = same and (ALLOW or (bu.shape == au.shape and float(np.abs(bu - au).max()) < 0.002)) and before.get('texture_size') == after.get('texture_size')
    print(f'{i}: passed through Blockbench; cubes {len(NC)} meshes {len(NM)}; look a {"same" if same else "DIFFERS"} ({len(bv)} -> {len(av)} verts)')
    if not same and not ALLOW:
        sys.exit(1)
