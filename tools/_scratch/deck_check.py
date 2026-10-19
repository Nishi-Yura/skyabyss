import gzip, struct, sys

d = gzip.open('src/main/resources/data/skyabyss/titan_back/whale.bin').read()
o = 4
x0, y0, z0, nx, ny, nz = struct.unpack('>6i', d[o:o + 24]); o += 24
n = struct.unpack('>h', d[o:o + 2])[0]; o += 2
for i in range(n):
    l = struct.unpack('>h', d[o:o + 2])[0]; o += 2 + l
runs = {}
for a in range(nx):
    for c in range(nz):
        b, segs = struct.unpack('>hh', d[o:o + 4]); o += 4
        r = struct.unpack('>%dh' % (segs * 3), d[o:o + segs * 6]); o += segs * 6
        runs[(a + x0, c + z0)] = r


def solid(x, y, z):
    r = runs.get((x, z))
    if not r:
        return False
    for i in range(0, len(r), 3):
        if r[i] + y0 <= y < r[i] + y0 + r[i + 1]:
            return True
    return False


DECK = 119
# name: (x1,x2,z1,z2,floorY,height)
BOXES = {
    'dstair': (5, 21, 8, 12, 103, 22),
    'D0': (22, 44, 4, 24, DECK, 9),
    'W02': (7, 20, 20, 66, DECK - 7, 21),
    'W06': (24, 39, 27, 42, DECK, 8),
    'C1': (41, 45, 26, 66, DECK, 6),
    'S0': (-22, 46, 68, 80, DECK, 9),
    'W07': (-20, 8, 83, 111, DECK, 10),
    'W10': (14, 46, 83, 113, DECK, 10),
}
if len(sys.argv) > 1:
    pass
bad = 0
for name, (x1, x2, z1, z2, fy, h) in BOXES.items():
    miss = []
    top = fy + h + 1
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            ring = x in (x1 - 1, x2 + 1) or z in (z1 - 1, z2 + 1)
            for y in range(fy - 1 if not ring else fy, top + 2):
                if (ring or y == top or y == top + 1 or y == fy - 1) and not solid(x, y, z):
                    miss.append((x, y, z))
    print(name, 'missing solid blocks:', len(miss), miss[:6])
    bad += len(miss)
print('total', bad)

# overlap with existing rooms (ring-expanded volumes: interior +-1 in x/z, y floor..floor+h+1)
EXIST = {
    'ENTRY': (-3, 3, -14, 15, 103, 6), 'E2': (24, 46, 5, 47, 103, 12), 'CK': (24, 46, 56, 76, 103, 8),
    'E2_PASS': (33, 43, -6, 3, 103, 6), 'CK_PASS': (33, 43, 49, 54, 103, 6), 'EXIT_PASS': (4, 21, -7, -3, 103, 6),
    'LIBRARY': (-34, -12, -8, 18, 103, 10), 'LIBRARY_PASS': (-11, -4, 3, 9, 103, 6), 'E1': (34, 54, -54, -8, 103, 11),
    'MAZE_EXIT_ROOM': (21, 27, -7, -3, 88, 5),
    'SHRINE_STAIR': (-3, 3, 15, 61, 103, 53),
}


def vol(b, ring=1):
    x1, x2, z1, z2, fy, h = b
    return (x1 - ring, x2 + ring, z1 - ring, z2 + ring, fy, fy + h + 1)


def inter(a, b):
    return not (a[1] < b[0] or b[1] < a[0] or a[3] < b[2] or b[3] < a[2] or a[5] < b[4] or b[5] < a[4])


for n1, b1 in BOXES.items():
    for n2, b2 in EXIST.items():
        if inter(vol(b1, 1), vol(b2, 1)):
            print('OVERLAP', n1, n2, vol(b1), vol(b2))
names = list(BOXES)
for i in range(len(names)):
    for j in range(i + 1, len(names)):
        if inter(vol(BOXES[names[i]], 0), vol(BOXES[names[j]], 0)):
            print('DECK OVERLAP interior', names[i], names[j])
