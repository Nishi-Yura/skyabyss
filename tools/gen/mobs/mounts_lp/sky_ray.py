"""空鱏 (低ポリの乗り物)。
選んだ案: A (00 の 348)。雲海を泳ぐ大きな鱏。菱形の体と大きな翼・二つの頭びれ・尾の先の鐘の房。飛ぶ乗り物 (MountLook.flies、38 の 6 章)。
骨: body / wing_l / wing_l2 / wing_r / wing_r2 / tail。動き: 待機 = ゆっくりのうねり、歩き = ふつうの羽ばたき、駆け = 強い羽ばたき (飛んでいるあいだも跳びの姿にはしない)。"""
from mlp_extra import *  # noqa

ID = "sky_ray"
TITLE = "空鱏"


def _shape():
    """A 空鱏: 雲海を泳ぐ大きな鱏。平たい菱形の体と大きな翼、前に二つの頭びれ、長い尾の先に鐘の房。背は夕べの青に金の斑、腹は雲の白。"""
    BK = [(16, 22, 48), (26, 36, 72), (40, 54, 100), (58, 76, 130), (82, 104, 160)]
    BL = [(170, 176, 196), (204, 210, 226), (230, 234, 244), (246, 248, 252)]
    TIP = [(120, 190, 230), (170, 224, 250), (226, 246, 255)]
    m = Mount("sky_ray", "空鱏")
    m.bone("body", "root", (0, 22, 0))
    for s, k in ((1, "l"), (-1, "r")):
        m.bone("wing_" + k, "body", (s * 13.0, 22.4, 0.0))
        m.bone("wing_" + k + "2", "wing_" + k, (s * 27.0, 24.6, 3.0))
    m.bone("tail", "body", (0, 22, 17))
    body = [rsec(-17, 23.4, 21.2, 5.0, 22.2, n=14, p=2.6), rsec(-12, 25.2, 19.4, 12.0, 22.2, n=14, p=2.6), rsec(-6, 26.6, 18.4, 15.6, 22.2, n=14, p=2.6),
            rsec(0, 27.0, 18.0, 16.6, 22.2, n=14, p=2.6), rsec(6, 26.4, 18.6, 14.4, 22.2, n=14, p=2.6), rsec(12, 25.0, 19.6, 9.6, 22.2, n=14, p=2.6),
            rsec(16, 23.6, 20.6, 4.8, 22.2, n=14, p=2.6), rsec(19, 22.8, 21.4, 1.8, 22.2, n=14, p=2.6)]
    m.add("disc", lof("body", "back", body), core=True)

    def wings(s, k):
        st1 = [(s * 11.0, -12.0, 11.0, 22.4, 5.0), (s * 19.0, -7.0, 10.0, 23.6, 3.4), (s * 27.0, -2.0, 9.0, 24.8, 2.4)]
        st2 = [(s * 26.0, -2.6, 9.2, 24.7, 2.5), (s * 34.0, 2.0, 9.4, 25.2, 1.6), (s * 40.0, 6.4, 10.0, 24.4, 1.0), (s * 44.0, 9.6, 10.6, 23.2, 0.5)]
        if s < 0:
            st1, st2 = st1[::-1], st2[::-1]
        m.add(f"wing_{k}", wing("wing_" + k, "back", st1))
        m.add(f"wing_{k}2", wing("wing_" + k + "2", "back", st2))
        m.add(f"fin_{k}", cone("body", "back", (s * 4.4, 21.8, -15.4), (s * 0.15, -0.05, -1), 7.0, 2.2, sides=5, rings=3, bend=(-s * 1.4, -0.6, 0), rb=1.2))
        c = np.array([s * 6.4, 23.0, -13.0])
        m.add(f"eye_{k}", ell("body", "eye", c, (1.2, 1.1, 1.3), segs=7, rings=2))
        m.eyes.append(dict(c=c, r=1.3, dir=(s * 0.8, 0.3, -0.5), a=0.9, b=0.8, bone="body", iris=(240, 200, 100), lid=(20, 26, 50), pa=0.4, pb=0.7, glow=True))
    sides(wings)
    tail = [(0, 22.2, 16.0), (0, 22.0, 26.0), (0, 21.4, 36.0), (0, 21.6, 46.0), (0, 22.6, 54.0)]
    m.add("tail", tub("tail", "back", tail, [1.6, 1.2, 0.9, 0.7, 0.5], None, sides=5))
    m.add("tassel", cone("tail", "tip", (0, 23.0, 53.0), (0, -0.4, 1), 5.0, 1.6, sides=5, rings=2))
    m.add("tassel_bell", ell("tail", "brass", (0, 22.6, 52.6), (1.2, 1.4, 1.2), segs=7, rings=2))
    m.saddle_kw = dict(cy=22.4, length=6.0, half_angle=40, seat_len=5.0, seat_half=30, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-40.0, 44.0)
    m.pal = pal({"back": M_(BK, lit=0.34, streak=0.04, ao=0.1), "tip": M_(TIP, base=0.64, lit=0.2)}, glow=["tip"],
                cloth=((120, 100, 40), (160, 134, 56), (200, 172, 80), (232, 208, 120)))
    tips = [(44.0, 23.2, 10.0), (-44.0, 23.2, 10.0)]

    def detail(c, col, glow):
        col = countershade(c, col, ["back"], (226, 230, 242), top_k=0.95, belly_from=0.1)
        col = spots(c, col, ["back"], (214, 176, 84), scale=0.3, thr=0.76, a=0.85, up_only=True)
        col, glow = gradient_to(c, col, ["back"], tips, 7.0, TIP, glow=glow, glow_thr=0.45, power=1.4)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 20, 6), scale=2.6)
    m.caption = ["雲海を泳ぐ大きな鱏", "菱形の体と大きな翼・二つの頭びれ・尾の鐘", "背は夕べの青に金の斑、腹は雲の白。翼の先が光る"]
    return m


def flap(name, period, a1, a2, pitch=0.0, bob=0.6):
    g = Gait(name, period=period)
    g.pos("body", y=W(0).s(bob, 1, 90, "t"))
    g.rot("body", x=W(pitch).s(1.2, 1, 180, "t"))
    for s, k in ((1, "l"), (-1, "r")):
        g.rot("wing_" + k, z=W(0).s(s * a1, 1, 0, "t"))
        g.rot("wing_" + k + "2", z=W(0).s(s * a2, 1, -50, "t"))
    g.rot("tail", x=W(0).s(3.0, 1, 120, "t"), y=W(0).s(8.0, 1, 60, "t"))
    return g


def gaits(m):
    idle = flap("idle", 3.2, 7.0, 10.0, bob=0.8)
    walk = flap("walk", 1.6, 16.0, 22.0, pitch=-3.0)
    run = flap("run", 1.0, 26.0, 32.0, pitch=-7.0, bob=0.4)
    jump = jump_keys({"wing_l": {"rot": {0.0: (0, 0, 0), 0.25: (0, 0, 30), 0.6: (0, 0, -12)}}, "wing_r": {"rot": {0.0: (0, 0, 0), 0.25: (0, 0, -30), 0.6: (0, 0, 12)}},
                      "wing_l2": {"rot": {0.0: (0, 0, 0), 0.3: (0, 0, 36), 0.65: (0, 0, -16)}}, "wing_r2": {"rot": {0.0: (0, 0, 0), 0.3: (0, 0, -36), 0.65: (0, 0, 16)}}})
    return [idle, walk, run, jump]


def build():
    m = _shape()
    m.gaits = gaits(m)
    m.hint = 256
    return m
