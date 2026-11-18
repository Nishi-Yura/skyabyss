# -*- coding: utf-8 -*-
"""暗い背景で、取り込んだ模型（表と裏の斜め）と参照の斧を同じくらいの大きさで並べる。  python dark_compare.py out.png id [id2 ...]"""
import importlib.util, sys
from pathlib import Path
from PIL import Image
spec=importlib.util.spec_from_file_location('ll',str(Path(__file__).with_name("look_lineup.py"))); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
m.SCALE=9; m.CELL_W=420; m.CELL_H=640; m.ORIGIN_Y=420
ids=sys.argv[2:]
W=Image.new('RGBA',(420*len(ids)*2+600,640),(16,16,22,255))
x=0
for i in ids:
    for yaw in (30,210):
        W.alpha_composite(m.render(i,yaw),(x,0)); x+=420
ref=Image.open(str(Path(__file__).resolve().parents[3] / "docs/review/refs_1118/02_crystal_axe.jpg")).convert('RGBA').crop((100,0,700,600)).resize((600,600),Image.NEAREST)
W.alpha_composite(ref,(x,20))
W.convert('RGB').save(sys.argv[1])
