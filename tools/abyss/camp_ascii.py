# -*- coding: utf-8 -*-
"""野営の上から見た図 (床 +1 の高さ)。python -I tools/abyss/camp_ascii.py [layer] [dy]"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audit_layers as A  # noqa: E402
import gen_abyss_layers as g  # noqa: E402

L = sys.argv[1] if len(sys.argv) > 1 else 'storm'
dy = int(sys.argv[2]) if len(sys.argv) > 2 else 1
b = A.decode(A.build())
Y = g.CAMP_Y[L]
for z in range(-22, 23):
    row = ''
    for x in range(-22, 23):
        st = b.get((x, Y + dy, z), '#')
        n = st.split('[')[0]
        row += '.' if n in ('air', 'cave_air') else ('|' if 'log' in n else ('L' if 'lantern' in n else ('+' if n in ('chain',) else '#')))
    print(row)
