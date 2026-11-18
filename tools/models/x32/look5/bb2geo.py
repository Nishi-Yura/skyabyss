# bbmodel (free format, per-face UV, z-rotation only) -> bedrock geo.json + texture png, to reload a finished model into Blockbench
import base64, json, sys
from pathlib import Path

src, outdir = Path(sys.argv[1]), Path(sys.argv[2])
d = json.loads(src.read_text(encoding='utf-8'))
tex = d['textures'][0]
uw, uh = tex.get('uv_width', tex['width']), tex.get('uv_height', tex['height'])
name = src.stem
png = outdir / f'{name}_tex.png'
png.write_bytes(base64.b64decode(tex['source'].split(',', 1)[1]))
cubes = []
for e in d['elements']:
    f, t, o = e['from'], e['to'], e['origin']
    r = e.get('rotation', [0, 0, 0])
    c = dict(origin=[-t[0], f[1], f[2]], size=[t[0] - f[0], t[1] - f[1], t[2] - f[2]],
             pivot=[-o[0], o[1], o[2]], rotation=[-r[0], -r[1], r[2]], uv={})
    for fn, fc in e['faces'].items():
        u = fc['uv']
        if fn in ('up', 'down'):   # bedrock import flips up/down: feed the far corner and a negative size
            c['uv'][fn] = dict(uv=[u[2], u[3]], uv_size=[u[0] - u[2], u[1] - u[3]])
        else:
            c['uv'][fn] = dict(uv=[u[0], u[1]], uv_size=[u[2] - u[0], u[3] - u[1]])
    cubes.append((e['name'], c))
bones = [dict(name='g_root', pivot=[0, 0, 0], cubes=[c for n, c in cubes])]
geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': f'geometry.{name}', 'texture_width': uw, 'texture_height': uh},
    'bones': bones}]}
(outdir / f'{name}.geo.json').write_text(json.dumps(geo), encoding='utf-8')
print(png, uw, uh, len(cubes))
