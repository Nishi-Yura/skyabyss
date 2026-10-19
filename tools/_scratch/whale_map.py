import gzip, struct, sys

d = gzip.open('src/main/resources/data/skyabyss/titan_back/whale.bin').read()
assert d[:4] == b'TBT1'
o = 4
x0, y0, z0, nx, ny, nz = struct.unpack('>6i', d[o:o + 24]); o += 24
n = struct.unpack('>h', d[o:o + 2])[0]; o += 2
pal = []
for i in range(n):
    l = struct.unpack('>h', d[o:o + 2])[0]; o += 2
    pal.append(d[o:o + l].decode()); o += l
body = {}
runs = {}
for a in range(nx):
    for c in range(nz):
        b, segs = struct.unpack('>hh', d[o:o + 4]); o += 4
        r = struct.unpack('>%dh' % (segs * 3), d[o:o + segs * 6]); o += segs * 6
        body[(a + x0, c + z0)] = b
        runs[(a + x0, c + z0)] = r
print(x0, y0, z0, nx, ny, nz)


def solid(x, y, z):
    r = runs.get((x, z))
    if not r:
        return False
    for i in range(0, len(r), 3):
        if r[i] + y0 <= y < r[i] + y0 + r[i + 1]:
            return True
    return False


step = int(sys.argv[1]) if len(sys.argv) > 1 else 12
ylev = int(sys.argv[2]) if len(sys.argv) > 2 else 106
for z in range(z0, z0 + nz, step):
    xs = [x for x in range(x0, x0 + nx) if solid(x, ylev, z)]
    tops = [y0 + body[(x, z)] for x in xs if body[(x, z)] >= 0]
    if xs:
        print(z, min(xs), max(xs), 'width', len(xs), 'top max', max(tops) if tops else None,
              'top@x-3', y0 + body[(-3, z)] if body.get((-3, z), -1) >= 0 else None)
