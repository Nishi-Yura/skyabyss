"""B 避雷の銅鎧鳥: 硬い面と銅の縁取りで組んだ、鎧をまとった鳥。後ろへ強く掃いた三日月の翼、羽根は先を落とした平らな板、
頭は角ばった仮面 (銅の庇・細い目)、冠は扇形に広がる九本の太い避雷針、尾は二本の長い燕尾。背に銅の屋根の塔と避雷の鰭。"""
from kit import *  # noqa
from kit2 import *  # noqa
from opt_a import P as PA, chest_scales  # noqa

S = lambda a, b, x: smooth(a, b, x)
P = dict(PA)
P.update(
    body_p=3.3, body_n=8, neck_p=3.3, neck_n=8, head_p=3.5, head_n=8, beak_p=2.3, head_under="belly",
    crest=[(-13, -84, 22, (-0.45, -0.1)), (-9.8, -86, 28, (-0.32, -0.1)), (-6.5, -88, 34, (-0.18, -0.12)), (-3.2, -90, 38, (-0.06, -0.1)),
           (0, -91, 42, (0, -0.1)), (3.2, -90, 38, (0.06, -0.1)), (6.5, -88, 34, (0.18, -0.12)), (9.8, -86, 28, (0.32, -0.1)),
           (13, -84, 22, (0.45, -0.1))],
    crest_r=1.25, eye_sc=1.0, eye_ry=2.4, rim_ry=3.4, rim_mat="copper", brow_mat="copper", brow_h=3.0, brow_dy=3.6,
    zle=lambda x: -42.0 + 8 * S(60, 150, x), zte=lambda x: 4 + 3 * S(20, 60, x) + 6 * S(60, 150, x),
    sec_n=12, sec_tipw=0.5, sec_w=1.2, sec_end=lambda x: 36 - 18 * S(70, 140, x), sec_jit=0.5, sec_curl=0.5, sec_top="sec",
    cov_rows=[dict(f=0.04, n=12, x0=26, x1=146, len=8, w=9, mat="copper", tipw=0.6, mid=False),
              dict(f=0.26, n=12, x0=28, x1=146, len=15, w=12.5, mat="cov", tipw=0.6),
              dict(f=0.50, n=12, x0=30, x1=146, len=15, w=12.5, mat="cov", tipw=0.6, mid=False),
              dict(f=0.74, n=12, x0=32, x1=140, len=16, w=12.5, mat="sec", tipw=0.6, lift=0.5)],
    veins=dict(n=10, x0=36, x1=140, f=0.40, amp=0.07, w=1.1, forks=[(60, 0.40, 0.2), (100, 0.40, -0.22)]),
    prim_tips=[(0.0, 160, 74, 58.5), (0.14, 177, 72, 58.5), (0.28, 192, 64, 58.5), (0.42, 205, 52, 59), (0.56, 215, 38, 59.5),
               (0.70, 223, 24, 60), (0.85, 229, 8, 60.5), (1.0, 233, -10, 61)],
    prim_w=16.0, prim_tipw=0.5, prim_curl=0.5, prim_bend=V(0, 0, 4),
    tail_n=7, tail_ang=34, tail_len=62, tail_w=8.5, tail_taper=-0.55, tail_tipw=0.3,
    knob_ring=True, gate_mat="copper",
)


def armour(bsh, nsh, hsh, lod):
    faces = []
    if lod == 2:
        return faces
    # 肩の銅の板 (左右)
    if lod == 0:
        for side in (1, -1):
            for k, (z, y) in enumerate(((-34, 52), (-24, 56), (-12, 58))):
                p, nn = bsh.at(z, y, side)
                faces += plinth("body", "copper", p, nn, (0, 1, 0), 7.5, 4.2, 1.6, 0.7, 4, embed=1.4, rot=45)
    # 首の環と、くちばしの根の環
    ring_pts = lambda c, rx, ry, k: [c + V(math.cos(a) * rx, math.sin(a) * ry, 0) for a in [2 * math.pi * i / k for i in range(k + 1)]]
    k = 12 if lod == 0 else 8
    faces += tube("neck", "copper", loop(V(0, 46.0, -66.0), 14.6, 16.4, k, p=3.3), 1.5, 2.2, sides=4, hint=(0, 0, 1), phase=45)
    faces += tube("head", "copper", loop(V(0, 52.0, -102.0), 9.4, 9.0, k, p=3.0), 1.2, 1.6, sides=4, hint=(0, 0, 1), phase=45)
    return faces


def spine_fins(bsh, lod):
    faces = []
    if lod == 2:
        return faces
    step = 8 if lod == 0 else 16
    z = -42
    i = 0
    while z <= 50:
        top = bsh.top(z)
        h = 7 + 6 * math.sin(math.pi * (z + 42) / 92) + (hash1(i, 3) * 2)
        base = V(0, top - 1.0, z)
        if abs(z + 8) > 12:   # 天窓のまわりは避ける
            faces += spike("ruins", "copper", base, V(0, 1, 0.5), h, 1.6, sides=4, rings=1, hint=(0, 0, 1), phase=0)
        z += step
        i += 1
    return faces


def pagoda(x, z, y0, lod):
    faces = []
    tiers = ((7.0, 9.0), (5.4, 8.0), (4.0, 7.0))
    for t, (w, h) in enumerate(tiers):
        yb = y0 + sum(hh for _, hh in tiers[:t]) + 1.0 * t
        faces += slab("ruins", "stone", (x, yb + h / 2, z), (w, h, w), taper=0.3, jit=0.2, tag=int(abs(x) * 3 + t))
        if lod == 0:
            faces += slab("ruins", "copper", (x, yb + h + 0.6, z), (w + 3.4, 1.2, w + 3.4), taper=1.2, tag=int(abs(x) + t + 9))
            c = V(x, yb + h * 0.5, z - w / 2 - 0.3)
            faces += slab("ruins", "glass", c, (1.4, 3.2, 0.8))
    top = y0 + 24 + 3
    faces += spike("ruins", "copper", V(x, top, z), V(0, 1, 0), 11.0, 1.1, sides=4, rings=2, hint=(1, 0, 0))
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
    faces += crest_rods(P, lod)
    w = build_wing(P, lod, "knob_w")
    faces += w + mirror_x(w, swap={"knob_w": "knob_t"})
    faces += tail_fan(P, lod)
    lg = legs(P, lod, 1)
    faces += lg + mirror_x(lg)
    if lod == 0:
        faces += chest_scales(bsh, lod)
    faces += armour(bsh, nsh, hsh, lod)
    faces += spine_fins(bsh, lod)
    faces += skylight(P, lod)
    for sx in (1, -1):
        faces += pagoda(sx * 16.5, -8.0, 55.0, lod)
    faces += stairs(P, lod)
    faces += gate(P, lod)
    return faces


CTX = dict(eye_z=-100.0, eye_y=57.0, tipband=3, bar_w=0.12,
           pal=dict(back=A_([(30, 36, 54), (42, 50, 72), (54, 62, 90), (68, 78, 106), (84, 96, 126)]),
                    copper=A_([(96, 52, 28), (146, 80, 40), (196, 112, 54), (232, 148, 80), (252, 190, 124)])))
OPT = dict(id="b", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
