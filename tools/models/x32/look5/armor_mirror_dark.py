# 右の箱の並びを左へ返し、淵の側の素材に替える: 頭なし(主の色) -> d_、b_(金) -> c_(紫)。k_/g_/d_ はそのまま。
import json, sys
src = json.load(open(sys.argv[1]))
out = []
for e in src:
    n = e['name']
    n = (n[:-2] + '_l') if n.endswith('_r') else n + '_l'
    if n.startswith('b_'):
        n = 'c_' + n[2:]
    elif not n.startswith(('k_', 'g_', 'd_', 'c_')):
        n = 'd_' + n
    f, t = e['from'], e['to']
    m = dict(name=n, **{'from': [-t[0], f[1], f[2]], 'to': [-f[0], t[1], t[2]]})
    if 'origin' in e:
        o = e['origin']; m['origin'] = [-o[0], o[1], o[2]]
    if 'rotation' in e:
        r = e['rotation']; m['rotation'] = [r[0], -r[1], -r[2]]
    out.append(m)
print(json.dumps(out, separators=(',', ':')))
