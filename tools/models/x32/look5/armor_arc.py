"""円弧を棒の節でつなぐ数値（平面 xy、z は固定）。python -I armor_arc.py name cx,cy z0,z1 R a0,a1,... w0,w1,...
角度は +x から反時計回り（度）。各節は点 a[i] -> a[i+1]、幅 w[i]。"""
import json, math, sys
name = sys.argv[1]
cx, cy = map(float, sys.argv[2].split(','))
z0, z1 = map(float, sys.argv[3].split(','))
R = float(sys.argv[4])
angs = [float(a) for a in sys.argv[5].split(',')]
ws = [float(w) for w in sys.argv[6].split(',')]
r = lambda v: round(v, 2)
out = []
for i in range(len(angs) - 1):
    a, b = math.radians(angs[i]), math.radians(angs[i + 1])
    A = (cx + R * math.cos(a), cy + R * math.sin(a))
    B = (cx + R * math.cos(b), cy + R * math.sin(b))
    L = math.hypot(B[0] - A[0], B[1] - A[1])
    rz = math.degrees(math.atan2(B[1] - A[1], B[0] - A[0]))
    w = ws[i]
    out.append({'name': f'{name}{i}', 'origin': [r(A[0]), r(A[1]), z0],
                'from': [r(A[0] - 0.25), r(A[1] - w / 2), z0], 'to': [r(A[0] + L + 0.25), r(A[1] + w / 2), z1],
                'rotation': [0, 0, r(rz)]})
print(json.dumps(out, separators=(',', ':')))
