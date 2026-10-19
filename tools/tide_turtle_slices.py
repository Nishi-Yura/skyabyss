#!/usr/bin/env python3
"""Print solid-block footprints of the tide turtle (turtle.bin) around the tower: for each y, the x/z extent of solid blocks inside a window.
Usage: python tools/tide_turtle_slices.py [ymin ymax step]"""
import gzip
import struct
import sys


def load(path):
    f = gzip.open(path, 'rb')
    assert f.read(4) == b'TBT1'
    x0, y0, z0, nx, ny, nz = struct.unpack('>6i', f.read(24))
    n = struct.unpack('>h', f.read(2))[0]
    pal = []
    for _ in range(n):
        ln = struct.unpack('>h', f.read(2))[0]
        pal.append(f.read(ln).decode())
    cols = {}
    for a in range(nx):
        for c in range(nz):
            struct.unpack('>h', f.read(2))
            segs = struct.unpack('>h', f.read(2))[0]
            runs = [struct.unpack('>3h', f.read(6)) for _ in range(segs)]
            cols[(x0 + a, z0 + c)] = runs
    return (x0, y0, z0, nx, ny, nz), pal, cols


def solid(cols, pal, x, y, z, oy=47):
    for (y0, ln, idx) in cols.get((x, z), []):
        if oy + y0 <= y < oy + y0 + ln and pal[idx] != "air":
            return True
    return False


if __name__ == '__main__':
    dims, pal, cols = load('src/main/resources/data/skyabyss/tide_back/turtle.bin')
    print(dims)
    ymin, ymax, step = (int(v) for v in sys.argv[1:4]) if len(sys.argv) > 3 else (96, 184, 4)
    for y in range(ymin, ymax + 1, step):
        xs = []
        zs = []
        n = 0
        for (x, z) in cols:
            if abs(x) > 40 or abs(z + 3) > 40:
                continue
            if solid(cols, pal, x, y, z):
                xs.append(x)
                zs.append(z)
                n += 1
        if xs:
            # radius test: solid cells inside the circle r<=9.5 around (0,-3) vs ring 9.5..11
            ring = sum(1 for (x, z) in zip(xs, zs) if 9.5 < ((x) ** 2 + (z + 3) ** 2) ** 0.5 <= 11.5)
            print(y, 'x', min(xs), max(xs), 'z', min(zs), max(zs), 'cells', n, 'ring9.5-11.5', ring)
        else:
            print(y, 'empty')
