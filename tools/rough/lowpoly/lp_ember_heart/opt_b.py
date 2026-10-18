"""B ずれた熔岩板: 少数の大きな平らな板が、傾き・段差をつけて浮き、割れ目が広く深い。板の側面が高く、
側面の下ほど熾に光る (板の下に熔岩が見える)。焼けた赤褐色のスコリア。段階 2 で縁の板が外へめくれ、段階 3 で板が緩んでずれる。"""
from family import *  # noqa

SCORIA = np.array([(34, 20, 18), (60, 34, 28), (92, 52, 38), (128, 76, 50), (166, 106, 72)])
ASH_W = np.array([(98, 88, 84), (140, 128, 120), (178, 166, 154), (210, 200, 188), (236, 228, 214)])
CFG = dict(
    Df=3.4, seeds=(15, 11), plate=SCORIA, ash=ASH_W, plate_base=[0.50, 0.48, 0.56], pit=[0.09, 0.10, 0.08], salt=2,
    SH={1: dict(top=0.2, jit_h=1.0, dome=0.3, gap=2.0, chamfer=0.9, jit_v=0.5, tilt=0.20, apex_off=0.3),
        2: dict(top=0.0, jit_h=1.0, dome=0.3, gap=2.8, chamfer=0.9, jit_v=0.6, tilt=0.26, apex_off=0.3),
        3: dict(top=-0.2, jit_h=1.2, dome=0.3, gap=3.0, chamfer=0.9, jit_v=0.7, tilt=0.30, apex_off=0.4, flake=(0.7,))},
    glow_w=[0.40, 0.46, 0.14], heat_w=[0.30, 0.25, 0.25], floor_v=[0.25, 0.20, -0.12], floor_cool=[0.08, 0.0, 0.4],
    rim_tilt=0.28, win2=40, win3=42, hole3=0.08, heart=(8, 3), strand_thick=1.15,
)
OPT = make_opt("b", CFG)
