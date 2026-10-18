"""交差の検査: python chk.py abc"""
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent))
import model as MD, posing as PO

HID = {"leg", "ant", "tail", "belly", "seam", "spike", "eye", "fin"}
for s in sys.argv[1]:
    F, _ = MD.build(s)
    for nm in ("curled", "rest"):
        st = PO.anim_state("curled", 0.0) if nm == "curled" else {}
        pf = PO.pose_faces(F, st)
        legs = len(PO.intersections(pf, skip=lambda a, b: not (a[2] == "leg" or b[2] == "leg" or a[2] == "ant" or b[2] == "ant")))
        # 脚・触角・尾は玉では内側 (ゲームでは隠す想定) なので、殻の面どうしだけを数える
        h = PO.intersections(pf, skip=lambda a, b: a[2] in HID or b[2] in HID)
        print("  (limbs involved:", legs, ")")
        print(s, nm, len(h))
        for k, v in PO.summarize(h, 14):
            print("   ", v, k)
