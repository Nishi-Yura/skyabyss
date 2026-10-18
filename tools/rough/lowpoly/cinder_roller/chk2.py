"""玉で、脚・触角・尾・腹・継ぎ目 (内側に収まる部品) が殻の面とぶつかっていないか: python chk2.py abc"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent))
import model as MD, posing as PO

L = {"leg", "ant", "tail", "belly", "seam"}
for s in sys.argv[1]:
    F, _ = MD.build(s)
    pf = PO.pose_faces(F, PO.anim_state("curled", 0.0))
    h = PO.intersections(pf, skip=lambda a, b: (a[2] in L and b[2] in L) or (a[2] not in L and b[2] not in L))
    print(s, len(h), PO.summarize(h, 10))
