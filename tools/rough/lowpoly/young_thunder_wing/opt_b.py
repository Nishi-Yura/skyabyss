"""B 案: 若い猛禽 — 細身で羽の生えそろいかけた若鳥。長い初列風切を指のように開き、翼の上を稲妻の筋が走る。
背には骨の肋で腹へ締めた骨と羽軸の鞍台 (白い骨の板、左右に羽軸の手すり、銅の柱)。頭に成体と同じ銅の避雷針の冠、その間に光る歌のかけら。"""
from __future__ import annotations

from bird import *  # noqa

NAME = "B 若い猛禽"
HINT = 512
PAL = palette()

BODY = [(-70, 28, 22, 14), (-60, 24, 42, 22), (-40, 22, 54, 24), (-14, 22, 58, 24), (16, 22, 56, 24), (42, 23, 48, 22),
        (60, 26, 30, 14), (72, 28, 14, 6)]


def half_w(z, y):
    zs = [s[0] for s in BODY]
    w = np.interp(z, zs, [s[2] for s in BODY]) / 2
    yc = np.interp(z, zs, [s[1] for s in BODY])
    return w - 0.4 * max(0.0, abs(y - yc) - 6)


def build():
    F = []
    # ================================================================ 体 (細身・灰青) と白い胸
    F += body_loft(BODY, "back", cut=0.5)
    F += ellipsoid("body", "belly", V(0, 17, -40), (20, 9, 26), segs=8, rings=3)
    F += flank_feathers(half_w, (-40, -22, -4, 14, 32), (20,), ("cov", "sec"), 16.0, 10.0, 2.5)

    # ================================================================ 鞍台: 骨の板 + 骨の肋の締め帯 + 羽軸の手すり + 銅の柱
    out = rrect(V(0, SEAT_Y, 0), V(1, 0, 0), V(0, 0, 1), 60, 114, 0.12)
    F += saddle_plate("saddle", "bone", out, thick=4.0, rim_mat="bone2")
    for z in (-46, -23, 0, 23, 46):           # 骨の肋 (板の縁から腹の下へ回る)
        for s in (1, -1):
            F += ctube("saddle", "bone", [V(s * 27.5, 33.5, z), V(s * 31.5, 27.0, z + 1), V(s * 29.5, 18.0, z + 2), V(s * 17.0, 13.0, z + 2)],
                       [2.4, 2.2, 2.0, 1.8], sides=4, phase=45)
    for s in (1, -1):                         # 羽軸の手すり (長い白い羽軸。銅の輪)
        rail = [V(s * 31.0, 41.0, -58), V(s * 32.0, 42.0, -20), V(s * 32.0, 42.0, 20), V(s * 31.0, 41.0, 58)]
        F += ctube("saddle", "bone", rail, [1.6, 1.9, 1.9, 1.6], sides=5)
        F += cspike("saddle", "bone", rail[-1] + V(0, 0, -0.8), (0, 0.15, 1), 8.0, 1.6, sides=5, rings=2)
        F += cspike("saddle", "bone", rail[0] + V(0, 0, 0.8), (0, 0.15, -1), 6.0, 1.6, sides=5, rings=2)
        for z in (-50, -24, 0, 24, 50):
            F += ctube("saddle", "copper", [V(s * 29.0, 34.0, z), V(s * 31.6, 42.0, z)], [1.5, 1.3], sides=4, phase=45)
            F += ctube("saddle", "copper", [V(s * 32.0 - 0.2, 42.0, z - 1.6), V(s * 32.0 - 0.2, 42.0, z + 1.6)], [2.5, 2.5], sides=4,
                       hint=(0, 1, 0))
        # 手すりに挿した羽根 (稲妻の羽根が 1 本ずつ)
        for i, z in enumerate((-37, -12, 12, 37)):
            F += feather("saddle", "quill" if i in (1, 2) else "sec", V(s * 31.6, 41.0, z), V(s * 40.0, 46.0, z + 10.0), 4.0, t=1.0,
                         up=(s * -0.4, 1, 0), bow=0.5, wtip=1.0)
    # 前の鞍頭 (銅の角) と後ろの背もたれ (羽軸の扇)
    F += cspike("saddle", "copper", V(0, 34.5, -56.0), (0, 0.9, -0.5), 10.0, 3.0, sides=6, rings=2, bend=(0, 0, 2))
    F += fan("saddle", V(0, 34.5, 55.0), 5, 64, 16.0, 4.0, ["bone", "quill", "bone", "quill", "bone"], y_tip=9.0, up=(0, 0.4, -1),
             bow=0.6, wtip=1.0)

    # ================================================================ 首・頭 (鉤の嘴の猛禽)
    F += neck_tube([V(0, 26, -62), V(0, 34, -68), V(0, 44, -74)], [11, 10, 9], "back")
    hc = V(0, 49, -78)
    F += ellipsoid("head", "back", hc, (11.5, 11, 15), segs=10, rings=4, deform=lambda p: p * np.array([1, 1, 1]) +
                   np.array([0, 0, -0.12 * max(0.0, -p[2])]))
    F += ellipsoid("head", "belly", hc + V(0, -5, -5), (8.5, 6, 9), segs=8, rings=2)
    F += fbox("head", "back", hc + V(0, 6.5, -8.0), (19.0, 3.0, 9.0), R=rot_x(-10), taper=(0.8, 0.8), cut=0.4)   # 眉の張り
    up_beak = [hc + V(0, 2.0, -15.0), hc + V(0, 1.6, -22.0), hc + V(0, -1.5, -27.5), hc + V(0, -7.0, -28.5)]
    F += ctube("head", "beak", up_beak, [5.0, 4.0, 2.4, 0.0], [4.0, 3.0, 2.0, 0.0], sides=4, hint=(1, 0, 0), phase=45)
    F += cspike("head", "beak_d", hc + V(0, -4.0, -15.0), (0, -0.1, -1), 9.0, 3.4, sides=4, rings=1, rb=1.6)
    ef, eyes = eye_pair(hc, r=3.2, dx=8.6, dy=3.0, dz=-9.0, out=0.6)
    F += ef
    for a in (-30, -15, 0, 15, 30):            # 銅の避雷針の冠 (後ろへ流れる)
        F += cspike("head", "copper", hc + V(a * 0.18, 8.5, 2 + abs(a) * 0.08), rot_y(a) @ V(0, 0.75, 0.75),
                    18.0 - abs(a) * 0.2, 1.8, sides=4, rings=2, bend=(0, -2, 3))
    # 歌のかけら: 冠の根に光る珠
    F += ellipsoid("mote", "mote", pv("mote") + V(0, 0.5, -8.0), (3.4, 3.4, 3.4), segs=8, rings=3)

    # ================================================================ 翼 (長い初列風切)
    def wing(s):
        W, T = ("wing_r", "wing_r_tip") if s > 0 else ("wing_l", "wing_l_tip")
        G_ = wing_plate(W, "cov", [(20, 30, -12, 30, 5), (48, 31, -10, 30, 4.5), (86, 33, -8, 26, 4)], s)
        G_ += wing_plate(T, "cov", [(80, 33, -8, 24, 4), (106, 34, -6, 18, 3.5), (124, 34.5, -4, 12, 3)], s)
        G_ += trailing(W, list(range(26, 86, 7)), lambda x: -10 + 0.03 * (x - 26) + 10, 20.0, 8.0, ["sec", "sec", "cov"], 31.5, s, 3.0)
        G_ += trailing(T, [84, 92, 100], lambda x: -4.0, 18.0, 7.5, ["sec"], 33.0, s, 5.0)
        roots = [(118, -8), (121, -5), (122, -2), (122, 1), (120, 4), (116, 6), (110, 7), (103, 7)]
        tips = [(170, -16), (174, -4), (172, 8), (166, 19), (156, 28), (144, 34), (130, 37), (116, 38)]
        G_ += primaries(T, roots, tips, 7.5, ["prim", "prim", "prim", "prim", "sec", "sec", "sec", "sec"], 34.0, s, 1.2, 1.4)
        return G_
    F += mirror(wing)

    # ================================================================ 尾 (長い扇) と脚 (後ろへたたむ)
    F += fan("tail", V(0, 29, 62), 7, 56, 22, 8.5, ["tailf", "prim"], y_tip=-2.0, bow=1.2, wtip=3.0)
    F += tucked_legs(12.0, 3.0)
    return F, eyes, chain(vein_paint(("cov", "prim"), rows=((-16.0, 1.0), (-4.0, -1.0)), period=8.0, amp=2.4), seat_paint((110, 104, 92)))
