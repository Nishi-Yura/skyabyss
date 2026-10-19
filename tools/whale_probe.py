"""部屋の候補を試す道具: python tools/whale_probe.py x1 x2 z1 z2 floor height  (何組でも)"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from whale_bin import Whale

w = Whale()
a = [int(v) for v in sys.argv[1:]]
for i in range(0, len(a), 6):
    x1, x2, z1, z2, f, h = a[i:i + 6]
    print((x1, x2, z1, z2, f, h), "shell", w.margin(x1, x2, z1, z2, f, f + h + 1))
