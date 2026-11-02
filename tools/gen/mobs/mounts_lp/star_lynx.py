"""星読みの大猫 (低ポリの乗り物)。
選んだ案: B (00 の 347)。月の野の大山猫。夜の青の毛に星の点 (光る)・房の耳・頬の襟毛・尾の先の三日月 (光る)。
骨: body / neck / head / tail / leg_{fl,fr,bl,br} (+ _lo)。"""
from mlp_extra import *  # noqa

ID = "star_lynx"
TITLE = "星読みの大猫"


def _shape():
    """B 星読みの大猫: 月の野の大山猫。夜の青の毛に星の点 (光る)、房のある耳、頬の襟毛、尾の先は三日月。"""
    NB = [(12, 16, 40), (20, 28, 62), (32, 44, 88), (48, 64, 116), (70, 90, 146)]
    CR = [(130, 136, 160), (170, 176, 198), (206, 212, 228), (232, 236, 246)]
    STAR = [(170, 190, 240), (210, 226, 255), (244, 250, 255)]
    m = Mount("star_lynx", "星読みの大猫")
    m.bone("body", "root", (0, 21, 0))
    m.bone("neck", "body", (0, 24, -11))
    m.bone("head", "neck", (0, 29, -16))
    m.bone("tail", "body", (0, 24, 13))
    quad_bones(m, {"fl": ((3.6, 19.0, -10.4), (3.7, 10.0, -10.8)), "fr": ((-3.6, 19.0, -10.4), (-3.7, 10.0, -10.8)),
                   "bl": ((3.6, 20.0, 8.4), (3.7, 10.2, 11.0)), "br": ((-3.6, 20.0, 8.4), (-3.7, 10.2, 11.0))})
    body = [rsec(-14, 25.0, 16.0, 4.8, 20.5, n=14), rsec(-9, 28.0, 14.6, 7.0, 21.0, n=14), rsec(-2, 28.0, 15.4, 6.6, 21.5, n=14),
            rsec(5, 27.6, 16.0, 6.6, 21.6, n=14), rsec(11, 27.0, 17.0, 6.0, 22.0, n=14), rsec(14.6, 25.0, 18.6, 4.4, 22.0, n=14)]
    m.add("torso", lof("body", "fur", body), core=True)
    m.add("chest", ell("body", "cream", (0, 18.6, -12.6), (4.0, 4.6, 3.0), segs=10, rings=3))
    m.add("neck", limb("neck", "fur", [(0, 22.6, -9.0), (0, 25.4, -12.8), (0, 28.4, -15.6)], [7.6, 6.6, 6.2], [7.6, 6.6, 6.4], n=12))
    hd = [rsec(-14.2, 33.0, 24.6, 4.2, 29.0, n=14), rsec(-16.8, 34.6, 24.0, 5.8, 29.2, n=14), rsec(-19.8, 33.6, 24.6, 5.4, 29.0, n=14),
          rsec(-22.4, 31.2, 25.6, 3.6, 28.4, n=14), rsec(-24.0, 29.6, 26.4, 2.0, 28.0, n=14)]
    m.add("skull", lof("head", "fur", hd), core=True)
    m.add("muzzle", ell("head", "cream", (0, 26.8, -22.2), (2.6, 1.8, 2.0), segs=8, rings=2))
    m.add("nose", ell("head", "eye", (0, 28.4, -24.0), (0.9, 0.6, 0.6), segs=6, rings=1))
    sk = tri_arrays(m.faces_of(["skull"]))

    def side_parts(s, k):
        d = nrm(np.array([s * 0.7, 0.18, -0.68]))
        c = ray_far(sk, (0, 30.4, -18.4), d) - d * 0.55
        m.add(f"eye_{k}", ell("head", "eye", c, (1.3, 1.2, 1.2), segs=8, rings=2))
        m.eyes.append(dict(c=c, r=1.4, dir=tuple(d), a=1.0, b=1.1, bone="head", iris=(240, 210, 110), lid=(14, 18, 40), pa=0.2, pb=0.9, glow=True))
        ep = np.array([s * 2.8, 33.4, -16.6])
        m.add(f"ear_{k}", tub("head", "fur", [ep, ep + np.array([s * 0.6, 3.0, 0.3]), ep + np.array([s * 1.0, 5.8, 0.6])], [3.0, 2.0, 0.3], [1.3, 1.0, 0.2], sides=4))
        m.add(f"tuft_{k}", cone("head", "fur_d", ep + np.array([s * 1.0, 5.4, 0.6]), (s * 0.1, 1, 0.1), 3.6, 0.6, sides=3, rings=1))
        for j in range(4):
            a = math.radians(-50 + j * 30)
            cp = ray_far(sk, (0, 27.4, -17.0), (s * math.cos(a), math.sin(a) * 0.6 - 0.2, 0.25))
            m.add(f"ruff_{k}{j}", cone("head", "cream" if j < 2 else "fur", cp - np.array([s * 0.5, 0, 0]), (s * 1, -0.2 + 0.2 * j, 0.5), 4.2, 1.5, sides=4, rings=1))
    sides(side_parts)
    tail = [(0, 24.0, 12.4), (0, 22.8, 19.0), (0, 23.4, 26.4), (0, 27.4, 32.0), (0, 32.6, 33.0), (0, 35.0, 30.0)]
    m.add("tail", limb("tail", "fur", tail, [3.4, 3.2, 3.0, 2.8, 2.4, 1.6], [3.4, 3.2, 3.0, 2.8, 2.4, 1.6], n=10))
    arc = [np.array([0, 35.0 + 3.6 * math.sin(math.radians(a)), 30.0 + 3.6 * math.cos(math.radians(a))]) for a in range(60, 301, 40)]
    m.add("tail_moon", tub("tail", "star", arc, [0.5, 1.0, 1.3, 1.4, 1.3, 1.0, 0.5], None, sides=5, hint=(1, 0, 0)))
    for k in ("fl", "fr"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, None, (s * 3.7, 3.8, -11.4), (4.6, 2.8, 2.4), (5.4, 3.2, 2.6), mat="fur", lo_mat="fur", hoof_mat="fur_d", hoof=(3.4, 2.0, 4.0), foot="paw")
    for k in ("bl", "br"):
        s = 1 if k.endswith("l") else -1
        hoof_leg(m, k, (s * 3.9, 14.6, 6.4), (s * 3.7, 3.8, 10.2), (5.4, 2.8, 2.4), (8.0, 3.2, 2.6), mat="fur", lo_mat="fur", hoof_mat="fur_d",
                 hoof=(3.4, 2.0, 4.0), foot="paw")
    m.saddle_kw = dict(cy=21.0, length=6.4, half_angle=58, seat_len=5.2, seat_half=34, blanket=0.8,
                       mats=dict(pad="clothr", trim="brass", seat="leath", strap="leathd", metal="brass"))
    m.prefer_pose = (-30.0, 24.0)
    m.pal = pal({"fur": M_(NB, streak=0.08, ao=0.14), "fur_d": M_(NB[:3], min=0), "cream": M_(CR, streak=0.08), "star": M_(STAR, base=0.64, lit=0.2)},
                glow=["star"], cloth=((90, 80, 40), (126, 112, 56), (170, 150, 80), (210, 190, 110)))

    def detail(c, col, glow):
        col = fur(c, col, ["fur"], scale=(1.0, 0.24, 1.0), amt=0.1)
        col = countershade(c, col, ["fur"], (150, 160, 196), top_k=0.9, belly_from=-0.35)
        P = c["P"]
        sel = np.isin(c["mats"], ["fur"]) & c["owned"] & (c["N"][..., 1] > -0.2)
        n = RC.hash3(np.floor(P / 0.9), 13)
        star = sel & (n < 0.035)
        col = np.where(star[..., None], np.array([226, 236, 255]), col)
        return col, glow | star
    m.detail = detail
    m.view = dict(center=(0, 24, 2), scale=3.7)
    m.caption = ["月の野の大山猫", "夜の青の毛に星の点（光る）・房の耳・頬の襟毛", "尾の先の三日月が光る。細く速い見た目"]
    return m


def gaits(m):
    idle, wk, rn = quad_gaits(walk=(1.5, 24, 34, 22, 34), run=(3.4, 44, 66, 40, 56), run_ph=GALLOP_PH, neck="neck", head="head", tails=("tail",),
                              bob=(0.5, 1.6), pitch=(1.2, 5.0))
    # 尾はゆっくり大きく (先の三日月が弧を描く)
    idle.rot("tail", x=W(0).s(4.0, 1, 0, "t"), y=W(0).s(10.0, 1, 40, "t").s(3.0, 3, 0, "t"))
    jump = quad_jump(neck="neck", head="head", tails=("tail",))
    return [idle, wk, rn, jump]


def build():
    m = _shape()
    m.gaits = gaits(m)
    m.hint = 256
    return m
