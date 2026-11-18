# brush coords from the design json: python uv.py <id> name:face:sel ...  sel = all | r0 | rL | c0 | cL | rK (row k)
import json, sys
d = {c['name']: {f['face']: f['uv'] for f in c['faces']} for c in json.load(open(sys.argv[1] + '.json'))}
out = []
for spec in sys.argv[2:]:
    n, f, sel = spec.split(':')
    u0, v0, u1, v1 = d[n][f]
    xs, ys = range(int(min(u0, u1)), int(max(u0, u1))), range(int(min(v0, v1)), int(max(v0, v1)))
    if sel == 'all': pts = [(x, y) for x in xs for y in ys]
    elif sel == 'r0': pts = [(x, ys[0]) for x in xs]
    elif sel == 'rL': pts = [(x, ys[-1]) for x in xs]
    elif sel == 'c0': pts = [(xs[0], y) for y in ys]
    elif sel == 'cL': pts = [(xs[-1], y) for y in ys]
    elif sel.startswith('r'): pts = [(x, ys[int(sel[1:])]) for x in xs]
    elif sel.startswith('c'): pts = [(xs[int(sel[1:])], y) for y in ys]
    out += pts
print(json.dumps([{'x': x + .5, 'y': y + .5} for x, y in dict.fromkeys(out)], separators=(',', ':')))
