"""C 黒曜の鱗: 小さな板が多く、それぞれが尖った頂点を持つ結晶 (黒曜石) のうろこ。継ぎ目の角から尖った棘が立ち、
割れ目は細く明るい。赤みのある黒いガラス質で、面の縁がきらりと光る。段階 2 では椀の縁に鱗が逆立ち、段階 3 で灰に曇る。"""
from family import *  # noqa

OBS = np.array([(14, 10, 14), (30, 20, 26), (54, 36, 42), (90, 60, 64), (150, 106, 98)])
ASH_O = np.array([(86, 80, 84), (126, 118, 120), (164, 156, 154), (200, 192, 186), (230, 222, 212)])
CFG = dict(
    Df=2.0, seeds=(40, 7), plate=OBS, ash=ASH_O, plate_base=[0.50, 0.50, 0.58], pit=[0.03, 0.03, 0.05], salt=3, sparkle=0.05,
    SH={1: dict(top=0.0, jit_h=0.3, dome=2.2, gap=0.8, chamfer=0.7, jit_v=0.3, tilt=0.0, apex_off=1.4, spikes=(2.2, 0.5, 0.30)),
        2: dict(top=-0.2, jit_h=0.4, dome=2.0, gap=1.5, chamfer=0.7, jit_v=0.4, tilt=0.0, apex_off=1.5, spikes=(2.2, 0.5, 0.30)),
        3: dict(top=-0.3, jit_h=0.5, dome=1.4, gap=1.6, chamfer=0.7, jit_v=0.4, tilt=0.04, apex_off=1.2, spikes=(1.8, 0.45, 0.18), flake=(0.6,))},
    glow_w=[0.38, 0.44, 0.12], heat_w=[0.25, 0.22, 0.20], floor_v=[0.32, 0.26, -0.10], floor_cool=[0.04, 0.0, 0.3],
    win2=26, win3=30, hole3=0.12, heart=(8, 3), prongs=6, strand_thick=0.9, rim_tilt=0.1,
)
OPT = make_opt("c", CFG)
