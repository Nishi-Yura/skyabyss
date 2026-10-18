"""A 嵐の大鷲 (忠実): 今の姿 (幅広い翼・指のように開いた初列風切・銅の避雷針の冠・扇の尾・背の天窓と塔) を、
斜めの面と羽根の重なりで刻んだ素直な低ポリ。"""
from kit2 import *  # noqa

S = lambda a, b, x: smooth(a, b, x)

P = dict(
    body_st=[(-50, 43, 17, 15, 15), (-44, 42, 22, 16, 16), (-32, 38, 26, 20, 20), (-20, 38, 30, 24, 24), (-8, 37, 30, 23, 23),
             (4, 40, 30, 22, 22), (16, 38, 26, 20, 20), (28, 40, 26, 18, 18), (40, 40, 22, 14, 14), (52, 38.5, 14, 12, 12),
             (64, 40.5, 9, 3.2, 3.2), (72, 41, 5, 1.8, 1.8)],
    neck_st=[(-46, 46, 16, 16, 16), (-56, 46, 14, 16, 16), (-62, 46, 14, 16, 16), (-74, 48, 13.5, 14, 14), (-86, 50, 12.5, 12, 12),
             (-94, 52, 10, 9, 9)],
    head_st=[(-83, 57, 10, 6, 6), (-88, 58, 13.5, 8, 8), (-92, 54, 14.5, 12, 12), (-97, 54, 14.5, 12, 12), (-101, 53.5, 13, 11.5, 11.5),
             (-105, 52, 9.5, 8.5, 8.5)],
    head_top="back", head_under="belly",
    beak_up=[(-99, 53.5, 8.5, 7.5, 5, 2.2), (-106, 54.0, 6.5, 6.0, 4.4), (-112, 53.2, 5.2, 5.2, 3.6), (-118, 51.8, 3.6, 4.0, 2.8),
             (-122.5, 49.0, 2.3, 3.2, 2.4), (-124.5, 44.5, 0.9, 2.2, 1.8)],
    beak_lo=[(-99, 46.5, 7.5, 2.4, 2.4), (-107, 46.3, 5.6, 2.6, 1.9), (-115, 46.8, 3.8, 2.2, 1.6), (-120.5, 47.2, 1.6, 1.4, 1.2)],
    eye_zy=(-100.0, 57.0), nostril_zy=(-108.0, 56.0),
    crest=[(-8, -87, 12, (0, -0.15)), (-5.3, -89, 18, (0, -0.15)), (-2.6, -90.5, 23, (0, -0.1)), (0, -91.5, 27, (0, -0.1)),
           (2.6, -90.5, 23, (0, -0.1)), (5.3, -89, 18, (0, -0.15)), (8, -87, 12, (0, -0.15))],
    # 翼
    zle=lambda x: -42.0,
    zte=lambda x: 4 + 3 * S(20, 60, x) - 4 * S(80, 150, x),
    top=lambda x: 62.0,
    bot=lambda x: 44.0 + 4 * S(40, 140, x),
    xs_near=[14, 26, 40, 56, 74, 92, 110, 126, 140, 150], xs_mid=[14, 40, 74, 110, 150], xs_far=[14, 80, 150],
    sec_n=13, sec_x0=28, sec_x1=142, sec_end=lambda x: 44 - 20 * S(70, 140, x), sec_w=1.75,
    cov_rows=[dict(f=0.10, n=14, x0=24, x1=146, len=0.25, w=7, mat="cov", mid=False),
              dict(f=0.34, n=14, x0=26, x1=146, len=0.3, w=9, mat="cov"),
              dict(f=0.56, n=14, x0=28, x1=146, len=0.3, w=9.5, mat="cov", mid=False),
              dict(f=0.76, n=13, x0=30, x1=140, len=0.34, w=10, mat="sec", lift=0.5)],
    veins=dict(n=14, x0=34, x1=140, f=0.40, amp=0.05, w=1.3, forks=[(56, 0.40, 0.18), (88, 0.40, -0.2), (116, 0.40, 0.2)]),
    wrist=(150.0, 59.5, -22.0), hand_end=(208.0, 60.5, -36.0), hand_r0=5.5, knob_r=10.5,
    prim_tips=[(0.0, 166, 50, 58.5), (0.1, 178, 52, 58.5), (0.2, 190, 48, 58.5), (0.32, 201, 40, 59), (0.45, 211, 28, 59.5),
               (0.58, 219, 14, 60), (0.72, 225, -2, 60), (0.86, 228, -18, 60.5), (1.0, 230, -32, 61)],
    prim_w=12.0, prim_tipw=0.3,
    tail_n=11, tail_ang=58, tail_len=64, tail_w=10.5, tail_tipw=0.5,
    sky_c=(0, 61.0, -6), sky_r=9.0,
)
# covert row の len は弦の割合 (feather 側では length = len * chord)
for r_ in P["cov_rows"]:
    r_["len"] = r_["len"] * 46.0


def chest_scales(sh, lod):
    faces = []
    if lod > 0:
        return faces
    zs = [-46, -40, -34, -28, -22, -16]
    for r, z in enumerate(zs):
        for k, y in enumerate((24, 29, 34, 39)):
            for side in (1, -1):
                p, nn = sh.at(z + 1.5 * (k % 2), y + (r % 2) * 2, side)
                tip = p + V(0, -3.4, 4.2) + nn * 1.4
                faces += scale("body", p - nn * 0.3, tip, 6.0, "belly_f", tipw=0.0)
    for r, z in enumerate((-44, -38, -32)):
        for k, y in enumerate((48, 54)):
            for side in (1, -1):
                p, nn = sh.at(z, y, side)
                faces += scale("body", p - nn * 0.3, p + V(0, -1.5, 5.5) + nn * 1.0, 6.0, "cov")
    return faces


def back_feathers(bsh, lod):
    faces = []
    if lod > 0:
        return faces
    for r, z in enumerate((-40, -31, -22, -13, -4, 5, 14, 23, 32)):
        for k, x in enumerate((7, 14, 21, 26)):
            for side in (1, -1):
                if abs(z + 6) < 12 and x < 12:
                    continue
                p, nn = upper_pt(bsh, z + 2.5 * (k % 2), x * side)
                faces += scale("body", p - nn * 0.3, p + V(side * 0.6, -0.8, 9.5) + nn * 1.4, 7.0, "cov" if k < 3 else "sec", tipw=0.0, mid=1.3)
    return faces


def build(lod):
    faces = []
    bsh, f = body_shell(P, lod)
    faces += f
    nsh, f = neck_shell(P, lod)
    faces += f
    hsh, f = head_shell(P, lod)
    faces += f
    faces += beak(P, lod)
    faces += eyes(P, hsh, lod)
    faces += nostrils(P, lod)
    if lod == 0:
        for side in (1, -1):
            for k in range(3):
                p, nn = hsh.at(-95 - 2 * k, 47 + 3 * k, side)
                faces += feather("head", p, p + V(0, -3.5 + k * 0.5, 9 - k), 5.0, th=0.9, top="plume", bot="plume")
    faces += crest_rods(P, lod)
    w = build_wing(P, lod, "knob_w")
    faces += w
    wr = mirror_x(w, swap={"knob_w": "knob_t"})
    faces += wr
    faces += tail_fan(P, lod)
    lg = legs(P, lod, 1)
    faces += lg + mirror_x(lg)
    faces += chest_scales(bsh, lod)
    faces += back_feathers(bsh, lod)
    # 背の遺跡
    faces += skylight(P, lod)
    for sx in (1, -1):
        faces += tower(sx * 15.5, -8.0, 56.0, 22.0 if lod == 0 else 24.0, 4.0, lod)
    faces += stairs(P, lod)
    faces += gate(P, lod)
    return faces


CTX = dict(eye_z=-100.0, eye_y=57.0, tipband=3, bar_f=0.16, bar_w=0.22, prim_glow=True)
OPT = dict(id="a", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
