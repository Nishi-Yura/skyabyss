"""八角の輪・円錐の壁の数値（箱は作らない。place_cube に渡す数値を出すだけ）。
python -I armor_ring.py <name> <cx,cy,cz> <Rb> <tilt> <L> <thick> <w> [k0,k1,...] [y_off_deg]
壁は前（-z）の板を作り、原点 (cx,cy,cz) を軸に [tilt, yoff+45k, 0] で回す。
"""
import json, math, sys
name = sys.argv[1]
cx, cy, cz = map(float, sys.argv[2].split(','))
Rb, t, L, th, w = map(float, sys.argv[3:8])
ks = [int(k) for k in sys.argv[8].split(',')] if len(sys.argv) > 8 and sys.argv[8] != '-' else list(range(8))
yoff = float(sys.argv[9]) if len(sys.argv) > 9 else 0.0
tr = math.radians(t)
z0 = -Rb * math.cos(tr)
y0 = -Rb * math.sin(tr)
out = []
for k in ks:
    r = lambda v: round(v, 2)
    out.append(dict(name=f'{name}{k}', origin=[cx, cy, cz],
                    **{'from': [r(cx - w / 2), r(cy + y0), r(cz + z0)], 'to': [r(cx + w / 2), r(cy + y0 + L), r(cz + z0 + th)]},
                    rotation=[t, (yoff + 45 * k) % 360 if (yoff + 45 * k) % 360 <= 180 else (yoff + 45 * k) % 360 - 360, 0]))
top_r = Rb - L * math.sin(tr)
top_y = cy + L * math.cos(tr)
print(json.dumps(out, separators=(',', ':')))
print(f'# top radius {top_r:.2f} top y {top_y:.2f} side width at top {2*top_r*math.tan(math.pi/8):.2f} bottom {2*Rb*math.tan(math.pi/8):.2f}')
