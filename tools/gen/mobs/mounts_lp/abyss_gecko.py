"""灯り守の大守宮 (低ポリの乗り物)。
選んだ案: A (00 の 346)。深淵の坑道の灯り番に飼われた大守宮。平たい体・吸盤の指・肩と腰と尾の先の灯り袋 (光る)。
骨: body / head / tail / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp_extra import *  # noqa

ID = "abyss_gecko"
TITLE = "灯り守の大守宮"


def _shape():
    """A 灯り守の大守宮: 坑道の灯り番に飼われた大きな守宮。平たい体、吸盤の指、背と尾の先に灯りの袋 (光る)。"""
    SK = [(18, 28, 30), (28, 42, 44), (40, 58, 58), (54, 76, 74), (72, 96, 92), (94, 118, 112)]
    BELLY = [(120, 120, 108), (160, 158, 140), (196, 192, 170), (222, 218, 196)]
    LAMP = [(170, 92, 24), (226, 150, 52), (252, 202, 104), (255, 236, 176), (255, 250, 228)]
    m = Mount("abyss_gecko", "灯り守の大守宮")
    m.bone("body", "root", (0, 16, 0))
    m.bone("head", "body", (0, 18, -14))
    m.bone("tail", "body", (0, 17, 13))
    body = [rsec(-15, 20.6, 12.8, 5.0, 16.4, n=14, p=2.6), rsec(-11, 22.8, 11.4, 8.0, 16.6, n=14, p=2.6), rsec(-4, 23.4, 11.0, 9.0, 16.8, n=14, p=2.6),
            rsec(4, 23.0, 11.0, 9.0, 16.8, n=14, p=2.6), rsec(10, 22.0, 12.0, 7.4, 16.8, n=14, p=2.6), rsec(15, 20.0, 13.4, 5.0, 16.8, n=14, p=2.6)]
    m.add("torso", lof("body", "skin", body), core=True)
    hd = [rsec(-13, 22.0, 14.0, 5.6, 17.6, n=14, p=2.4), rsec(-17, 23.2, 13.4, 7.2, 17.8, n=14, p=2.4), rsec(-21, 22.4, 13.8, 7.4, 17.8, n=14, p=2.4),
          rsec(-25, 20.4, 14.4, 5.8, 17.4, n=14, p=2.4), rsec(-28, 18.6, 15.2, 3.0, 17.0, n=14, p=2.2)]
    m.add("skull", lof("head", "skin", hd), core=True)
    m.add("jaw", ell("head", "belly", (0, 15.4, -21.0), (6.0, 1.6, 5.6), segs=10, rings=2))
    sk = tri_arrays(m.faces_of(["skull"]))

    def eye(s, k):
        d = nrm(np.array([s * 0.86, 0.42, -0.28]))
        c = ray_far(sk, (0, 18.6, -19.6), d) - d * 0.9
        m.add(f"eye_{k}", ell("head", "eye", c, (2.4, 2.3, 2.5), segs=9, rings=3))
        m.eyes.append(dict(c=c, r=2.6, dir=tuple(d), a=2.0, b=1.0, bone="head", iris=(236, 196, 96), lid=(28, 42, 44), pa=0.2, pb=0.9, glow=True))
    sides(eye)
    tail = [(0, 17.0, 11.0), (0, 17.2, 19.0), (0, 18.4, 27.0), (0, 21.0, 34.0), (0, 25.0, 38.6), (0, 29.4, 39.6)]
    m.add("tail", limb("tail", "skin", tail, [10.0, 8.4, 6.4, 4.8, 3.6, 2.6], [8.0, 6.6, 5.2, 4.0, 3.2, 2.4], n=10))
    m.add("tail_lamp", ell("tail", "lamp", (0, 31.6, 39.8), (2.7, 3.1, 2.7), segs=9, rings=3))
    m.add("tail_lamp_cap", cone("tail", "skin_d", (0, 33.8, 39.8), (0, 1, 0), 1.6, 1.6, sides=6, rings=1))
    for i, z in enumerate((14.0, 19.0, 24.0, 29.0)):
        t = (z - 11) / 28
        m.add(f"spine{i}", cone("tail", "skin_d", (0, 17.2 + 8 * t * t + 3.4 - 1.2 * t, z), (0, 1, 0.3), 2.0 - 0.8 * t, 1.3 - 0.4 * t, sides=4, rings=1))
    # 灯りの袋 (肩と腰のわき。鞍の下を避ける)
    bt = tri_arrays(m.faces_of(["torso"]))

    def sacs(s, k):
        for j, (z, r) in enumerate(((-11.5, 2.4), (9.5, 2.2), (13.5, 1.7))):
            d = nrm(np.array([s * 0.8, 0.6, 0]))
            p = ray_far(bt, (0, 16.6, z), d)
            m.add(f"sac_{k}{j}", ell("body", "lamp", p - d * 0.6, (r, r * 0.8, r * 1.1), segs=8, rings=2))
    sides(sacs)

    def leg(k, s, z):
        m.bone("leg_" + k, "body", (s * 7.0, 15.0, z))
        m.bone("leg_" + k + "_lo", "leg_" + k, (s * 13.0, 13.4, z - 1.0))
        hip, knee, foot = np.array([s * 5.0, 15.6, z]), np.array([s * 13.0, 13.4, z - 1.0]), np.array([s * 14.2, 1.6, z - 3.0])
        m.add(f"{k}_up", limb("leg_" + k, "skin", [hip, lerp(hip, knee, 0.5) + np.array([0, 0.8, 0]), knee], [5.2, 4.6, 3.8], [4.6, 4.2, 3.6], n=8, side=(0, 1, 0)))
        m.add(f"{k}_elbow", ell("leg_" + k + "_lo", "skin", knee, (2.2, 2.2, 2.2), segs=7, rings=2))
        m.add(f"{k}_lo", limb("leg_" + k + "_lo", "skin", [knee, lerp(knee, foot, 0.5), foot + np.array([0, 0.8, 0])], [3.6, 3.0, 2.6], [3.4, 2.8, 2.4], n=8))
        splay_foot(m, "leg_" + k + "_lo", "skin", foot + np.array([0, 0.4, 0]), s, toes=5, toe_len=3.4, disc="belly", fwd=-1.0 if z < 0 else -0.3)
    for k, s, z in (("fl", 1, -10.0), ("fr", -1, -10.0), ("bl", 1, 8.0), ("br", -1, 8.0)):
        leg(k, s, z)
    m.saddle_kw = dict(cy=17.0, length=6.0, half_angle=46, seat_len=5.0, seat_half=32, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 34.0)
    m.pal = pal({"skin": M_(SK, streak=0.04, ao=0.12), "skin_d": M_(SK[:4], min=0), "belly": M_(BELLY),
                 "lamp": M_(LAMP, base=0.64, lit=0.2, noise=0.03)}, glow=["lamp"], cloth=((60, 30, 20), (90, 46, 28), (124, 66, 36), (160, 92, 50)))

    def detail(c, col, glow):
        col = scales(c, col, ["skin"], size=1.3, edge=0.12, k_edge=0.8)
        col = countershade(c, col, ["skin"], (176, 172, 150), top_k=0.88, belly_from=-0.2)
        col = spots(c, col, ["skin"], (20, 30, 32), scale=0.22, thr=0.7, a=0.6, up_only=True)
        return col, glow
    m.detail = detail
    m.view = dict(center=(0, 16, 6), scale=4.0)
    m.caption = ["坑道の灯り番に飼われた大守宮", "平たい体・吸盤の指・肩と腰と尾の先の灯り袋", "低く速い。壁や天井は歩かない（見た目だけ）"]
    return m


def gaits(m):
    idle, wk, rn = quad_gaits(walk=(1.2, 22, 22, 22, 22), run=(2.6, 34, 34, 34, 34), run_ph=TROT_PH, neck=None, head="head", tails=("tail",),
                              bob=(0.25, 0.7), pitch=(0.6, 2.0), run_base=(0.0, 0.0))
    # 守宮は胴をくねらせて進む (横の揺れ)。尾は胴と逆へ
    wk.rot("body", x=W(0).s(0.6, 2, 90), y=W(0).s(6.0, 1, 0), z=W(0).s(1.2, 1, 0))
    wk.rot("tail", x=W(0).s(2.0, 2, 30), y=W(0).s(-12.0, 1, 30))
    rn.rot("body", x=W(0).s(1.4, 2, 90), y=W(0).s(9.0, 1, 0), z=W(0).s(2.0, 1, 0))
    rn.rot("tail", x=W(-4).s(3.0, 2, 30), y=W(0).s(-16.0, 1, 40))
    idle.rot("tail", x=W(0).s(2.0, 1, 40, "t"), y=W(0).s(8.0, 1, 0, "t").s(3.0, 3, 0, "t"))
    jump = quad_jump(neck=None, head="head", tails=("tail",))
    return [idle, wk, rn, jump]


def build():
    m = _shape()
    m.gaits = gaits(m)
    m.hint = 256
    return m
