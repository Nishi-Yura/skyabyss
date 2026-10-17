"""余燼の背の体の「漏れ」を調べる (13 の 18 章)。広間の空気から体の中を広げ、決めた口 (口・背の亀裂・火の井戸と火口・背の上への出口)
を通らずに体の外へ出るかを見る。体の外 = 空が見える所か、腹の下の張り出し (その下に灰の原まで何もない所)。

使い方: python tools/structure/ember_leaks.py run/shots/ember_back/dumps/ember_back.sbd
"""
import math
import sys
from collections import deque
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sbd  # noqa: E402

HUB = (0, 90, -34)
CRACK = (-4, 4, 12, 58)          # x1 x2 z1 z2 (谷から上)
WELL_R = 7.1
BACK_EXIT = (-3, 3, -30, 6, 118)  # x1 x2 z1 z2 y>=
PLAIN_TOP = 63


def allowed(x, y, z, crater):
    if z <= -128:  # 口 (顎の蝶番より前。唇のすき間も口のうち)
        return True
    if CRACK[0] <= x <= CRACK[1] and CRACK[2] <= z <= CRACK[3] and y >= 78:
        return True
    d = math.hypot(x, z + 34)
    if d <= WELL_R and y >= 104 or d <= 15 and y >= crater - 2:
        return True
    return BACK_EXIT[0] <= x <= BACK_EXIT[1] and BACK_EXIT[2] <= z <= BACK_EXIT[3] and y >= BACK_EXIT[4]


def main():
    d = sbd.load(sys.argv[1])
    solid = d.collide
    sy, sz, sx = solid.shape
    ox, oy, oz = d.origin
    # 空が見える (その上に固いものが無い)
    above = np.flip(np.maximum.accumulate(np.flip(solid, axis=0), axis=0), axis=0)
    sky = np.zeros_like(solid)
    sky[:-1] = ~above[1:]
    sky[-1] = True
    # 腹の下の張り出し: その下、灰の原の上まで固いものが無い
    floor_y = PLAIN_TOP + 1 - oy
    below = np.zeros_like(solid)
    acc = np.zeros((sz, sx), bool)
    for iy in range(floor_y, sy):
        below[iy] = ~acc
        acc |= solid[iy]
    under = below.copy()
    under[:floor_y] = False
    crater_col = solid[:, -34 - oz, 8 - ox]
    crater = oy + int(np.nonzero(crater_col)[0].max())
    seen = np.zeros_like(solid)
    start = (HUB[1] - oy, HUB[2] - oz, HUB[0] - ox)
    q = deque([start])
    seen[start] = True
    leaks = []
    n = 0
    while q:
        iy, iz, ix = q.popleft()
        x, y, z = ix + ox, iy + oy, iz + oz
        if allowed(x, y, z, crater):
            continue
        if sky[iy, iz, ix] or under[iy, iz, ix]:
            leaks.append((x, y, z))
            continue
        n += 1
        for dy, dz, dx in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            j, k, i = iy + dy, iz + dz, ix + dx
            if 0 <= j < sy and 0 <= k < sz and 0 <= i < sx and not seen[j, k, i] and not solid[j, k, i]:
                seen[j, k, i] = True
                q.append((j, k, i))
    print(f"crater floor {crater}; inside air {n} cells; leaks {len(leaks)}")
    for p in leaks[:20]:
        print("  leak at", p)
    return 1 if leaks else 0


if __name__ == "__main__":
    sys.exit(main())
