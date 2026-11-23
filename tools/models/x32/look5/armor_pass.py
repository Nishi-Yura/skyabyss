# -*- coding: utf-8 -*-
"""防具の一式を、Blockbench で組んだ形 → 塗り → Blockbench に通し直し → 取り込み、の順で仕上げる。

  python -X utf8 -I armor_pass.py prep <組> <work> <Blockbench の書き出し.bbmodel> [...] [旧名=新名 ...]
      書き出し (群 armorHead / armorBody / armorRightArm / ... の下に箱) をまとめ、armor_paint.py で組の色に塗り、
      <work>/<組>.geo.json と <組>_tex.png、下見の <組>_painted_view.png を書く。
  Blockbench (MCP): create_project(free, <組>_pass) → from_geo_json(<組>.geo.json の絶対パス)
      → create_texture(name=<組>, data=<組>_tex.png の絶対パス, width/height = 絵の画素, uv_width/uv_height = その 2 倍)
      → export_model(codec project, path=<work>/out_<組>.bbmodel)
  python -X utf8 -I armor_pass.py done <組> <work>
      Blockbench の書き出しの箱に名前と UV を戻し (形と絵が塗ったものと同じか確かめる)、正本
      tools/models/x32/armor/<組>.bbmodel に書き、bb_armor_import.py で取り込み、<work>/<組>_view.png (人形に着せた 4 方向) を書く。
"""
import base64
import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(HERE))
import armor_paint  # noqa: E402

BONES = ['armorHead', 'armorBody', 'armorRightArm', 'armorLeftArm', 'armorRightLeg', 'armorLeftLeg', 'armorRightBoot', 'armorLeftBoot']
PIVOT = {'armorHead': [0, 48, 0], 'armorBody': [0, 48, 0], 'armorRightArm': [10, 44, 0], 'armorLeftArm': [-10, 44, 0],
         'armorRightLeg': [3.8, 24, 0], 'armorLeftLeg': [-3.8, 24, 0], 'armorRightBoot': [3.8, 24, 0], 'armorLeftBoot': [-3.8, 24, 0]}


def top_groups(bb):
    """箱の uuid → 一番外の群の名前"""
    gmap = {g['uuid']: g for g in bb.get('groups', [])}
    out = {}

    def walk(node, top):
        if isinstance(node, str):
            out[node] = top
            return
        g = gmap.get(node.get('uuid'), node)
        name = top or g.get('name')
        for ch in node.get('children', []):
            walk(ch, name)
    for n in bb.get('outliner', []):
        walk(n, None)
    return out


def prep(setname, work, raws):
    # --rename 旧=新: 塗りの名前（頭・_t・_p）だけを付け替える（形は Blockbench の書き出しのまま）
    # -<正規表現>: 名前が合う箱を外す（Blockbench で絵の板に描き直した 3D の飾りなど。外した数を出す）
    import re
    drop = [re.compile(a[1:]) for a in raws if a.startswith('-')]
    raws = [a for a in raws if not a.startswith('-')]
    ren = dict(a.split('=', 1) for a in raws if '=' in a)
    raws = [a for a in raws if '=' not in a]
    dropped = []
    elems = []
    for r in raws:
        bb = json.loads(Path(r).read_text(encoding='utf-8'))
        tg = top_groups(bb)
        for e in bb['elements']:
            if e.get('type', 'cube') != 'cube':
                continue
            if any(rx.search(e['name']) for rx in drop):
                dropped.append(e['name'])
                continue
            bone = tg.get(e['uuid'])
            assert bone in BONES, (r, e['name'], bone)
            e['_bone'] = bone
            e['name'] = ren.get(e['name'], e['name'])
            elems.append(e)
    if drop:
        print(f'外した箱 {len(dropped)}: {" ".join(dropped)}')
    names = [e['name'] for e in elems]
    dup = {n for n in names if names.count(n) > 1}
    assert not dup, f'同じ名前の箱: {sorted(dup)[:8]}'
    merged = {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': setname,
              'resolution': {'width': 16, 'height': 16}, 'elements': elems, 'outliner': [], 'textures': []}
    tmp = work / f'{setname}_merged.bbmodel'
    tmp.write_text(json.dumps(merged, ensure_ascii=False), encoding='utf-8')
    painted = work / f'{setname}_painted.bbmodel'
    armor_paint.main(str(tmp), str(painted), setname)
    d = json.loads(painted.read_text(encoding='utf-8'))
    tex = d['textures'][0]
    W, H = tex['uv_width'], tex['uv_height']
    png = base64.b64decode(tex['source'].split(',', 1)[1])
    (work / f'{setname}_tex.png').write_bytes(png)
    bones = {b: [] for b in BONES}
    for e in d['elements']:
        f, t, o = e['from'], e['to'], e['origin']
        r = e.get('rotation') or [0, 0, 0]
        c = dict(origin=[-t[0], f[1], f[2]], size=[t[0] - f[0], t[1] - f[1], t[2] - f[2]], pivot=[-o[0], o[1], o[2]],
                 rotation=[-r[0], -r[1], r[2]], uv={})
        for fn, fc in e['faces'].items():
            u = fc['uv']
            c['uv'][fn] = dict(uv=[u[2], u[3]], uv_size=[u[0] - u[2], u[1] - u[3]]) if fn in ('up', 'down') else dict(uv=[u[0], u[1]], uv_size=[u[2] - u[0], u[3] - u[1]])
        bones[e['_bone']].append(c)
    gb = []
    for b in BONES:
        if bones[b]:
            p = PIVOT[b]
            gb.append(dict(name=b, pivot=[-p[0], p[1], p[2]], cubes=bones[b]))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {'identifier': f'geometry.{setname}', 'texture_width': W, 'texture_height': H},
                                                               'bones': gb}]}
    (work / f'{setname}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
    # 下見 (人形に着せて)
    view(painted, work / f'{setname}_painted_view.png')
    img = Image.open(io.BytesIO(png))
    print(f'{setname}: cubes {len(d["elements"])} bones {[b["name"] for b in gb]} tex {img.width}x{img.height} (uv {W}x{H})')
    print('geo', (work / f'{setname}.geo.json').resolve().as_posix())
    print('tex', (work / f'{setname}_tex.png').resolve().as_posix())


def view(bbpath, png):
    subprocess.run([sys.executable, '-X', 'utf8', '-I', str(ROOT / 'tools/gen/pixel/armor_view.py'), str(png), str(bbpath)],
                   check=True, cwd=ROOT, capture_output=True)


def done(setname, work):
    d = json.loads((work / f'{setname}_painted.bbmodel').read_text(encoding='utf-8'))
    n = json.loads((work / f'out_{setname}.bbmodel').read_text(encoding='utf-8'))
    NC = [e for e in n['elements'] if e.get('type', 'cube') == 'cube']
    assert len(NC) == len(d['elements']), f'cubes {len(d["elements"])} -> {len(NC)}'

    def geokey(e):
        r = e.get('rotation') or [0, 0, 0]
        return tuple(round(v, 2) for v in list(e['from']) + list(e['to']) + list(r)) + tuple(round(v, 2) for v in (e['origin'] if any(r) else [0, 0, 0]))
    pool = {}
    for e in d['elements']:
        pool.setdefault(geokey(e), []).append(e)
    worst = 0.0
    for e in NC:
        k = geokey(e)
        if not pool.get(k):
            k = min((kk for kk, vv in pool.items() if vv), key=lambda kk: sum((a - b) ** 2 for a, b in zip(kk, geokey(e))))
            worst = max(worst, max(abs(a - b) for a, b in zip(k, geokey(e))))
        o = pool[k].pop(0)
        e['name'] = o['name']
        e['autouv'] = 0
        for fn, fc in o['faces'].items():
            e['faces'][fn]['uv'] = fc['uv']
            e['faces'][fn]['texture'] = 0
    assert worst < 0.05, f'a cube moved by {worst}'
    pt = d['textures'][0]
    nt = n['textures'][0]
    a = Image.open(io.BytesIO(base64.b64decode(pt['source'].split(',', 1)[1]))).convert('RGBA')
    b = Image.open(io.BytesIO(base64.b64decode(nt['source'].split(',', 1)[1]))).convert('RGBA')
    assert a.size == b.size and a.tobytes() == b.tobytes(), 'texture differs'
    nt['uv_width'], nt['uv_height'] = pt['uv_width'], pt['uv_height']
    n['resolution'] = d['resolution']
    dst = ROOT / 'tools/models/x32/armor' / f'{setname}.bbmodel'
    dst.write_text(json.dumps(n, ensure_ascii=False), encoding='utf-8')
    r = subprocess.run([sys.executable, '-X', 'utf8', '-I', str(ROOT / 'tools/gen/pixel/bb_armor_import.py'), str(dst)],
                       check=True, cwd=ROOT, capture_output=True, text=True)
    print(r.stdout.strip().replace('\n', ' | '))
    view(dst, work / f'{setname}_view.png')
    print(f'{setname}: passed through Blockbench -> {dst.relative_to(ROOT).as_posix()}; view {(work / f"{setname}_view.png").as_posix()}')


if __name__ == '__main__':
    cmd, setname, work = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    work.mkdir(parents=True, exist_ok=True)
    if cmd == 'prep':
        prep(setname, work, sys.argv[4:])
    else:
        done(setname, work)
