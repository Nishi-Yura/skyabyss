# 指定した箱の面の絵を、灰鉄（cinder）の段で n 段明るくする
import json, sys, base64, io
from PIL import Image
p, names, n = sys.argv[1], sys.argv[2].split(','), int(sys.argv[3])
d = json.load(open(p, encoding='utf-8'))
t = d['textures'][0]
im = Image.open(io.BytesIO(base64.b64decode(t['source'].split(',', 1)[1]))).convert('RGBA')
ramp = ['#141012', '#221c1c', '#322a28', '#463b36', '#5c4f48', '#75665c', '#928272', '#b2a290', '#d4c6b4', '#f2ead8']
rgb = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in ramp]
done, cnt = set(), 0
for e in d['elements']:
    if e['name'] not in names:
        continue
    for f in e['faces'].values():
        u0, v0, u1, v1 = f['uv']
        for x in range(int(min(u0, u1)), int(max(u0, u1))):
            for y in range(int(min(v0, v1)), int(max(v0, v1))):
                if (x, y) in done:
                    continue
                c = im.getpixel((x, y))
                if c[:3] in rgb:
                    im.putpixel((x, y), rgb[min(rgb.index(c[:3]) + n, 7)] + (c[3],)); cnt += 1
                done.add((x, y))
b = io.BytesIO(); im.save(b, 'PNG')
t['source'] = 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()
json.dump(d, open(p, 'w', encoding='utf-8'), ensure_ascii=False)
print('lightened', cnt)
