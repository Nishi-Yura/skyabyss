"""A 案の確認: python pva.py <module> <out.png> — 品 x 階級 (コモン・レア・伝説)、各 2 視点"""
import importlib
import sys
from common import *

mod = importlib.import_module(sys.argv[1])
out = sys.argv[2]
items = list(mod.ITEMS.items())
T = 190
W = Image.new("RGB", (6 * T, len(items) * T), (40, 44, 52))
BUD = dict(worn=200, charm=150, held=150)
for row, (iid, it) in enumerate(items):
    for ci, rar in enumerate(("common", "rare", "legend")):
        r = make(it["fn"]["a"], mod.TITAN, rar)
        flag = "" if r["tris"] <= BUD[it["kind"]] else " OVER"
        print(iid, rar, r["tris"], flag)
        for k, (yaw, pitch) in enumerate(((180, 0), (150, -22))):
            im = view(r["faces"], r["atlas"], r["glow"], mod.TITAN, yaw, pitch, (T, T), (0, 0, 0), 11.5)
            bg = Image.new("RGBA", (T, T), (30, 34, 42, 255))
            bg.alpha_composite(im)
            W.paste(bg.convert("RGB"), ((ci * 2 + k) * T, row * T))
W.save(out)
