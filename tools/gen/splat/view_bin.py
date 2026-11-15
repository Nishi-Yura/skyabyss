# -*- coding: utf-8 -*-
"""点の模型 (.bin, SPL3) を正射影で描いて下見する。python -I tools/gen/splat/view_bin.py <名前> [--yaw 200,150] [--px 900] [--out パス]"""
import argparse
import struct
import sys
import zlib
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402


def read3(path):
    raw = Path(path).read_bytes()
    assert raw[:4] == b"SPL3"
    b = zlib.decompress(raw[4:])
    o = 0
    nb = struct.unpack_from(">I", b, o)[0]
    o += 4
    for _ in range(nb):
        ln = struct.unpack_from(">H", b, o)[0]
        o += 2 + ln
    n = struct.unpack_from(">I", b, o)[0]
    o += 4
    lo = np.array(struct.unpack_from(">fff", b, o))
    o += 12
    step, smax = struct.unpack_from(">ff", b, o)
    o += 8
    q = np.zeros((n, 3), np.int64)
    for k in range(3):
        acc = 0
        for i in range(n):
            v = 0
            sh = 0
            while True:
                c = b[o]
                o += 1
                v |= (c & 0x7F) << sh
                sh += 7
                if not c & 0x80:
                    break
            acc += (v >> 1) ^ -(v & 1)
            q[i, k] = acc
    arr = np.frombuffer(b, np.uint8, offset=o)
    size = arr[0:n] / 255 * smax
    rgb = np.stack([arr[n:2 * n], arr[2 * n:3 * n], arr[3 * n:4 * n]], 1).astype(float)
    flags = arr[7 * n:8 * n].astype(int)
    return lo + q * step, size, rgb, flags


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--yaw", default="200,150")
    ap.add_argument("--px", type=int, default=900)
    ap.add_argument("--out")
    a = ap.parse_args()
    P, size, rgb, flags = read3(S.ROOT / "src/main/resources/assets/skyabyss/splat" / (a.name + ".bin"))
    out = a.out or str(S.ROOT / "docs/review/splat_bosses" / (a.name + "_big.png"))
    S.preview(P, size, rgb, flags, out, yaw_list=tuple(int(y) for y in a.yaw.split(",")), px=a.px)
    print(out)


if __name__ == "__main__":
    main()
