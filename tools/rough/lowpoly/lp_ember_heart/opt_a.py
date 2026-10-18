"""A 玄武岩の板割れ: 採用済みの B 板割れの素直な低ポリ化。丸い玉を黒い玄武岩の板 (面取りの屋根) が覆い、
細い割れ目の底が熾の橙に光る。段階 2 で上が熔け落ちて椀になり、熔けた垂れが下がる。段階 3 は白い灰の板に穴。"""
from family import *  # noqa

BAS = np.array([(24, 22, 26), (42, 38, 42), (64, 58, 60), (90, 82, 80), (122, 112, 104)])
CFG = dict(
    Df=2.2, seeds=(24, 4), plate=BAS, plate_base=[0.52, 0.50, 0.58], pit=[0.05, 0.06, 0.06],
    SH={1: dict(top=0.0, jit_h=0.25, dome=0.8, gap=1.0, chamfer=1.2, jit_v=0.3, tilt=0.0, apex_off=0.5),
        2: dict(top=-0.2, jit_h=0.4, dome=0.7, gap=1.8, chamfer=1.2, jit_v=0.4, tilt=0.0, apex_off=0.6),
        3: dict(top=-0.3, jit_h=0.5, dome=0.5, gap=1.9, chamfer=1.1, jit_v=0.5, tilt=0.04, apex_off=0.8, flake=(0.55,))},
    glow_w=[0.34, 0.40, 0.10], heat_w=[0.28, 0.25, 0.20], floor_v=[0.28, 0.22, -0.12], floor_cool=[0.06, 0.0, 0.35],
)
OPT = make_opt("a", CFG)
