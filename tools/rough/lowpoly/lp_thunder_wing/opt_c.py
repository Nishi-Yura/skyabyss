"""C 嵐を纏う羽毛の鳥 (生きた鳥): 丸い体にふさふさの羽毛。首の襟 (逆立つ羽根の輪)、頬の長い羽根、後ろへなびく羽根の冠 (先に銅)、
先が裂けて垂れた風切り、重なる鱗状の雨覆、脚の羽袴。肩・翼のうしろ・尾の根に嵐の雲のかたまりがまとわりつき、
背の遺跡は羽毛に半ば埋もれて傾く。"""
from kit2 import *  # noqa
from opt_a import P as PA  # noqa

S = lambda a, b, x: smooth(a, b, x)
P = dict(PA)
P.update(
    body_p=2.15, neck_p=2.2, head_p=2.15, beak_p=1.9,
    body_st=[(-50, 43, 18, 15, 15), (-44, 42, 23, 16.5, 16.5), (-32, 38, 27, 20.5, 20.5), (-20, 38, 30.5, 24.5, 24.5), (-8, 37, 30.5, 23.5, 23.5),
             (4, 40, 30.5, 22.5, 22.5), (16, 38, 27, 20.5, 20.5), (28, 40, 26.5, 18.5, 18.5), (40, 40, 22.5, 14.5, 14.5),
             (52, 38.5, 15, 12.5, 12.5), (64, 40.5, 9.5, 3.5, 3.5), (72, 41, 5, 1.8, 1.8)],
    beak_up=[(-99, 53.5, 8.5, 7.5, 5, 2.2), (-106, 54.0, 6.5, 6.0, 4.4), (-111, 53.0, 5.2, 5.2, 3.6), (-116, 51.0, 3.8, 4.2, 3.0),
             (-120.5, 47.6, 2.5, 3.4, 2.5), (-122.5, 43.5, 1.0, 2.4, 2.0)],
    crest=[], eye_sc=1.3, eye_ry=3.9, rim_mat="belly_f", brow_mat="plume", brow_h=2.8,
    zle=lambda x: -44.0, zte=lambda x: 8 + 2 * S(20, 60, x) - 6 * S(90, 150, x),
    sec_n=15, sec_w=1.9, sec_jit=7.0, sec_tipw=0.3, sec_curl=5.0, sec_drop=5.0, sec_sweep=5.0,
    sec_end=lambda x: 66 - 30 * S(70, 140, x),
    cov_rows=[dict(f=0.08, n=14, x0=24, x1=146, len=8, w=8, mat="cov", tipw=0.5, cap=False, mid=False),
              dict(f=0.28, n=14, x0=26, x1=146, len=13, w=10.5, mat="cov", tipw=0.4, cap=False),
              dict(f=0.48, n=14, x0=28, x1=146, len=14, w=11, mat="cov", tipw=0.4, cap=False, mid=False),
              dict(f=0.82, n=14, x0=32, x1=140, len=16, w=12, mat="sec", tipw=0.3, cap=False, lift=0.4)],
    veins=dict(n=12, x0=34, x1=142, f=0.44, amp=0.06, w=1.4, forks=[(58, 0.44, 0.2), (90, 0.44, -0.22), (120, 0.44, 0.2)]),
    prim_tips=[(0.0, 172, 68, 56), (0.1, 185, 70, 55), (0.2, 197, 64, 55), (0.3, 208, 56, 56), (0.4, 217, 46, 57),
               (0.5, 224, 34, 58), (0.62, 229, 20, 59), (0.74, 232, 6, 60), (0.87, 234, -12, 61), (1.0, 235, -30, 63)],
    prim_w=12.5, prim_tipw=0.25, prim_curl=0.0, prim_bend=V(0, 5, 3),
    tail_n=9, tail_ang=44, tail_len=94, tail_w=13, tail_tipw=0.3, tail_curl=-5.0, tail_taper=0.15,
    sec_cap=False, prim_cap=False, tail_cap=False,
    sky_c=(0, 59.0, -6), sky_r=8.5, knob_ring=False,
)


def ruff(nsh, lod):
    faces = []
    if lod == 2:
        return faces
    rows = [(-56, 12, 1.0), (-66, 12, 1.0), (-76, 11, 0.9)] if lod == 0 else [(-58, 10, 1.0), (-72, 10, 1.0)]
    for r, (z, k, s) in enumerate(rows):
        for i, (p, d) in enumerate(shell_ring(nsh, z, k)):
            ph = (i + 0.5 * (r % 2)) / k
            if d[1] < -0.2 and r < 2:     # 喉は短く
                ln = 8.0
            else:
                ln = 13.0
            tip = p + V(d[0] * 3.2, (d[1] * 3.2) - 1.5, ln)
            faces += scale("neck", p - V(d[0], d[1], 0) * 0.6, tip, 7.0 if lod == 0 else 9.0,
                           "plume" if d[1] > -0.2 else "belly_f", tipw=0.15, mid=1.5, segs=1)
    return faces


def hackles(bsh, lod):
    faces = []
    if lod == 2:
        return faces
    n = 0
    for r, z in enumerate((-46, -40, -34, -28, -22, -16, -10, -4, 2, 8, 14)) if lod == 0 else enumerate((-44, -30, -16, -2, 12)):
        for k, y in enumerate((26, 33, 40, 47, 54, 60)):
            for side in (1, -1):
                if y < 30 and z > -20 and lod == 0 and False:
                    continue
                p, nn = bsh.at(z + 1.5 * (k % 2), y, side)
                if y >= 56:
                    tip = p + V(0, -1.0, 9.0) + nn * 1.2
                    mt = "cov"
                elif y <= 34:
                    tip = p + V(0, -2.8, 7.0) + nn * 1.8
                    mt = "belly_f"
                else:
                    tip = p + V(0, -1.8, 8.0) + nn * 1.6
                    mt = "cov" if y > 46 else "belly_f"
                faces += scale("body", p - nn * 0.3, tip, 8.0, mt, tipw=0.0, mid=1.5)
    return faces


def head_extras(hsh, lod):
    faces = []
    if lod == 2:
        return faces
    # 冠: 後ろへなびく羽根。先に銅
    k = 9 if lod == 0 else 5
    for i in range(k):
        t = i / (k - 1) * 2 - 1
        root = V(t * 9.0, 64.5 - 1.6 * abs(t), -86.0 - 4.5 * (1 - abs(t)))
        tip = root + V(t * 5.0, 18.0 + 9 * (1 - abs(t)), 14.0 + 6 * (1 - abs(t)))
        faces += feather("crest", root, tip, 6.5, th=0.9, top="plume", bot="plume", tipw=0.0, mid=1.5, bend=V(0, 3, 8), segs=2 if lod == 0 else 1)
        if lod == 0:
            end = tip + V(0, 3, 8) * 1.0
            faces += spike("crest", "copper", end - V(0, 2.0, 5.0), V(0.0, 0.5, 1.0), 6.0, 0.7, sides=3, rings=1, hint=(1, 0, 0))
    if lod == 0:
        # 頬の長い羽根
        for side in (1, -1):
            for j in range(5):
                p, nn = hsh.at(-93 - 1.8 * j, 49 + 2.2 * j, side)
                faces += feather("head", p, p + V(side * 0.6, -6.5 + 1.2 * j, 13 - j), 5.5, th=0.9, top="plume", bot="belly_f", tipw=0.0)
    return faces


def clouds(lod):
    faces = []
    if lod == 2:
        return faces
    segs, rings = (6, 2) if lod == 0 else (6, 1)
    # (骨, x, y, z, 半径) 雲のかたまり。小さな玉を三つずつ寄せる
    cl = [("wing", 30, 63, -34, 8), ("wing", 56, 55, 30, 9), ("wing", 90, 54, 36, 9), ("wing", 120, 54, 26, 7)]
    if lod == 1:
        cl = cl[:3]
    sd = 0
    for (b_, x, y, z, r) in cl:
        for j in range(3 if lod == 0 else 2):
            dx, dz = (j - 1) * r * 0.9, ((j % 2) - 0.5) * r * 0.8
            sz = r * (0.9 + 0.25 * hash1(sd, 5))
            for side in (1, -1):
                faces += puff("wing_l" if side > 0 else "wing_r", (side * (x + dx), y + 0.6 * (j % 2), z + dz), (sz, sz * 0.5, sz * 0.85), sd, segs, rings)
            sd += 1
    for i, (x, y, z, r) in enumerate([(-20, 52, 30, 8), (20, 52, 30, 8)]):
        for j in range(3 if lod == 0 else 1):
            faces += puff("body", (x + (j - 1) * r * 0.9, y + 0.5 * (j % 2), z + ((j % 2) - 0.5) * r), (r, r * 0.55, r * 0.9), 80 + i * 3 + j, segs, rings)
    for j in range(3 if lod == 0 else 1):
        faces += puff("tail", ((j - 1) * 7, 46.5, 76 + 3 * (j % 2)), (7, 3, 6), 90 + j, segs, rings)
    return faces


def trousers(side, lod):
    faces = []
    if lod == 2:
        return faces
    b = "leg_l" if side > 0 else "leg_r"
    n = 8 if lod == 0 else 6
    for i in range(n):
        a = 2 * math.pi * i / n
        root = V(12 * side + math.sin(a) * 5, 34.0, 38 + math.cos(a) * 5)
        tip = root + V(math.sin(a) * 2.0, -12.5, math.cos(a) * 2.0)
        faces += scale(b, root, tip, 6.5, "belly_f", tipw=0.0, mid=1.4)
    return faces


def tower_c(x, z, lean, lod):
    faces = []
    y0 = 55.0
    faces += slab("ruins", "stone", (x, y0 + 7, z), (4.2, 14, 4.2), taper=0.4, jit=0.5, tag=int(abs(x)) + 5, skew=(lean, 0))
    if lod == 0:
        faces += slab("ruins", "stone", (x + lean, y0 + 14.8, z), (6.0, 2.0, 6.0), taper=0.7, jit=0.4, tag=int(abs(x)) + 9)
        faces += spike("ruins", "copper", V(x + lean, y0 + 15.8, z), V(lean * 0.05, 1, 0), 7.0, 0.9, sides=4, rings=1, hint=(1, 0, 0))
        faces += slab("ruins", "glass", (x - 2.4 * (1 if x > 0 else -1) + lean * 0.4, y0 + 9, z), (0.8, 3.2, 1.5))
        for j in range(4):
            a = j * 1.5708 + 0.4
            root = V(x + math.sin(a) * 2.2, y0 + 1.5, z + math.cos(a) * 2.2)
            faces += feather("ruins", root, root + V(math.sin(a) * 4, 4.5 + j % 2 * 2, math.cos(a) * 4 + 3), 5.0, th=0.9, top="plume", bot="plume", tipw=0.0)
    else:
        faces += spike("ruins", "copper", V(x + lean, y0 + 14.0, z), V(0, 1, 0), 6.0, 1.0, sides=3, rings=1, hint=(1, 0, 0))
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
    faces += head_extras(hsh, lod)
    w = build_wing(P, lod, "knob_w")
    faces += w + mirror_x(w, swap={"knob_w": "knob_t"})
    faces += tail_fan(P, lod)
    lg = legs(P, lod, 1) + trousers(1, lod)
    faces += lg + mirror_x(lg)
    faces += ruff(nsh, lod)
    faces += hackles(bsh, lod)
    faces += clouds(lod)
    faces += skylight(P, lod)
    faces += tower_c(15.5, -8.0, 2.5, lod) + tower_c(-15.5, -8.0, -1.5, lod)
    faces += stairs(P, lod)
    faces += gate(dict(P, gate_mat="stone"), lod)
    return faces


CTX = dict(base_cloud=0.82, eye_z=-100.0, eye_y=57.0, tipband=4, bar_w=0.2,
           pal=dict(back=A_([(46, 54, 76), (60, 70, 98), (76, 88, 120), (94, 108, 140), (114, 128, 160), (136, 150, 180)]),
                    cloud=A_([(150, 160, 186), (186, 194, 214), (214, 220, 234), (236, 240, 248), (252, 253, 255)]),
                    plume=A_([(100, 112, 140), (130, 142, 172), (164, 176, 202), (200, 210, 230), (232, 238, 250)])))
OPT = dict(id="c", build=build, ctx=CTX, tex=(2.0, 1.0, 0.5))
