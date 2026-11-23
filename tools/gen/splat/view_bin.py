# -*- coding: utf-8 -*-
"""点の模型 (.bin, SPL4) を正射影で描いて下見する。python -I tools/gen/splat/view_bin.py <名前> [--yaw 200,150] [--px 900] [--out パス]"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--yaw", default="200,150")
    ap.add_argument("--px", type=int, default=900)
    ap.add_argument("--out")
    a = ap.parse_args()
    d = S.read_bin(S.ROOT / "src/main/resources/assets/skyabyss/splat" / (a.name + ".bin"))
    P, size, rgb, flags = d["P"], d["size"], d["rgb"].astype(float), d["flags"].astype(int)
    out = a.out or str(S.ROOT / "docs/review/splat_bosses" / (a.name + "_big.png"))
    S.preview(P, size, rgb, flags, out, yaw_list=tuple(int(y) for y in a.yaw.split(",")), px=a.px)
    print(out)


if __name__ == "__main__":
    main()
