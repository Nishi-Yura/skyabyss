"""火の粉蛾 (ember_moth, ひのこが): 熱に寄る小さな蛾。翅を広げて幅約 1.2 ブロック (19 単位)、群れで飛ぶ。

蛾に見せる要点: 羽毛のような櫛形の触角、ふさふさの太い胸、前翅 (三角) と後翅 (丸い) の 2 枚ずつ、翅の模様。
翅は 0.5 単位の板。外へ行くほど 0.5 ずつ上がる (浅い V 字) ので、薄い絵の板に見えない。前翅の前縁は 0.5 盛り上がる。
羽ばたき: 前翅と後翅は同じ骨の軸 (翅の付け根の上の線) で回す (前後の翅の位置関係が変わらない = 重ならない)。
"""
from __future__ import annotations

import math

import ember_mobs_common as E

BY = 8.0  # 体の中心の高さ (浮いている)


def in_poly(u, z, poly):
    inside = False
    n = len(poly)
    for i in range(n):
        x1, z1 = poly[i]
        x2, z2 = poly[(i + 1) % n]
        if (z1 > z) != (z2 > z):
            xi = x1 + (z - z1) * (x2 - x1) / (z2 - z1)
            if u < xi:
                inside = not inside
    return inside


WINGS = {
    "a": dict(fore=[(2.0, 0.0), (2.0, 3.5), (5.0, 4.5), (10.0, 4.5), (9.5, 2.5), (7.5, -1.5), (4.5, -1.0)],
              hind=[(2.0, 1.0), (5.5, 0.5), (7.5, -1.0), (8.0, -3.0), (6.5, -5.5), (4.0, -6.0), (2.0, -3.0)]),
    "b": dict(fore=[(2.0, 0.0), (2.0, 3.5), (5.5, 5.0), (10.5, 6.0), (9.5, 3.0), (8.0, -1.0), (6.5, 0.0), (5.0, -1.0), (3.5, -0.5)],
              hind=[(2.0, 1.0), (5.5, 0.5), (7.0, -1.0), (7.0, -3.0), (5.5, -4.5), (5.0, -8.5), (4.0, -8.5), (3.5, -5.0), (2.0, -3.0)]),
    "c": dict(fore=[(2.0, 0.5), (2.0, 3.5), (5.0, 4.0), (9.5, 3.5), (9.0, 2.0), (6.5, 0.0), (4.0, 0.0)],
              hind=[(2.0, 1.0), (5.0, 0.5), (6.5, -1.0), (6.5, -2.5), (4.5, -4.0), (2.0, -3.0)]),
}


def step(u):
    return 0.0 if u < 5 else (0.5 if u < 8 else 1.0)


def build(opt: str):
    sc = E.Sculpt(f"ember_moth_{opt}")
    sc.bone("body", (0, BY, 0))
    sc.bone("head", (0, BY, 4), "body")
    for s, side in ((-1, "r"), (1, "l")):
        sc.bone(f"wing_{side}", (s * 2.0, BY + 0.5, 1.0), "body")
    W = WINGS[opt]

    # ---- 翅 (前翅は y = BY..BY+0.5 + 段、後翅は 0.5 下)
    for s, side in ((-1, "r"), (1, "l")):
        bone = f"wing_{side}"
        for kind, poly, y0, mat in (("fore", W["fore"], BY, "forewing"), ("hind", W["hind"], BY - 0.5, "hindwing")):
            def pred(x, y, z, poly=poly, y0=y0):
                u = s * x
                if u < 2.0:
                    return False
                yy = y0 + step(u)
                return yy <= y <= yy + 0.5 and in_poly(u, z, poly)
            sc.add(bone, pred, mat, (min(s * 2, s * 11), BY - 1, -9, max(s * 2, s * 11), BY + 2, 7))
        # 前縁の盛り上がり (前翅の前の縁の上に 0.5)
        fore = W["fore"]

        def costa(x, y, z, fore=fore):
            u = s * x
            if not (3.5 <= u <= 9.0):
                return False
            yy = BY + step(u) + 0.5
            return yy <= y <= yy + 0.5 and in_poly(u, z, fore) and not in_poly(u, z + 0.5, fore)
        sc.add(bone, costa, "vein", (min(s * 2, s * 11), BY - 1, -9, max(s * 2, s * 11), BY + 3, 7))

    # ---- 胸 (ふさふさ)
    sc.add("body", E.ell((0, BY + 0.25, 1.75), (2.0, 2.0, 2.25)), "fuzz", E.bb((0, BY + 0.25, 1.75), (2.0, 2.0, 2.25)))
    sc.add("body", lambda x, y, z: E.ell((0, BY + 0.25, 1.75), (2.5, 2.5, 2.75))(x, y, z) and abs(x) < 2.0
           and E.blob_noise(x, y, z, s=0.5, seed=2) > 0.55, "fuzz_lt", E.bb((0, BY + 0.25, 1.75), (2.5, 2.5, 2.75)))
    # 首の襟 (明るい毛)
    sc.add("body", lambda x, y, z: E.ell((0, BY + 0.5, 3.75), (1.75, 1.75, 0.75))(x, y, z), "fuzz_lt", E.bb((0, BY + 0.5, 3.75), (1.75, 1.75, 0.75)))

    # ---- 頭と目
    hc = (0, BY + 0.25, 5.0)
    sc.add("head", E.ell(hc, (1.5, 1.5, 1.25)), "fuzz", E.bb(hc, (1.5, 1.5, 1.25)))
    sc.recolor(lambda x, y, z: abs(x) >= 0.75 and z >= 4.75 and abs(y - hc[1]) <= 1.0, "eye", E.bb(hc, (2, 2, 2)), bone="head")
    # 口吻の名残 (下の小さな突起)
    sc.box("head", -0.5, BY - 1.5, 5.5, 0.5, BY - 1.0, 6.0, "fuzz")

    # ---- 触角 (櫛形の羽毛)
    for s in (-1, 1):
        p0 = (s * 0.5, BY + 1.5, 5.5)
        p1 = (s * 3.5, BY + 4.0, 9.0)
        sc.line("head", p0, p1, "antenna")
        dx, dz = p1[0] - p0[0], p1[2] - p0[2]
        L = math.hypot(dx, dz)
        px, pz = -dz / L, dx / L
        for t in (0.25, 0.40, 0.55, 0.70, 0.85):
            p = [p0[i] + (p1[i] - p0[i]) * t for i in range(3)]
            ln = 1.4 * (1.0 - 0.6 * t)
            for sg in (-1, 1):
                q = (p[0] + sg * px * ln, p[1] - 0.25, p[2] + sg * pz * ln)
                sc.line("head", p, q, "antenna_b")

    # ---- 腹
    if opt in "ab":
        def abd(x, y, z):
            if not (-7.5 <= z <= 0.25):
                return False
            t = (0.25 - z) / 7.75
            r = 1.75 * (1.0 - 0.55 * t * t) if t > 0.15 else 1.75
            yc = BY - 0.25 - 0.8 * t
            return x * x + (y - yc) ** 2 <= r * r
        sc.add("body", abd, None, (-2, BY - 3, -8, 2, BY + 2, 0.5),
               mat_fn=lambda c: "abd_glow" if (math.floor(-c[2] / 1.5) % 2 == 1) else "abd")
        # 尾の先の毛房
        sc.add("body", lambda x, y, z: abs(x) <= 0.75 and BY - 1.75 <= y <= BY - 0.75 and -8.5 <= z <= -7.0, "fuzz_lt",
               (-1, BY - 2, -9, 1, BY, -6.5))
    else:   # 燭の蛾: 蝋燭のような太い腹と、尾の先の炎
        def candle(x, y, z):
            return -7.0 <= z <= 0.25 and x * x + (y - (BY - 0.5)) ** 2 <= (1.75 if z > -1.5 else 2.0) ** 2
        sc.add("body", candle, "wax", (-2.5, BY - 3, -7.5, 2.5, BY + 2, 0.5))
        # 垂れた蝋
        for x0, z0, ln in ((-1.5, -1.5, 1.5), (0.5, -3.5, 2.0), (1.0, -5.5, 1.0), (-1.0, -6.0, 2.5)):
            sc.box("body", x0, BY - 2.5 - ln, z0, x0 + 0.5, BY - 2.5, z0 + 0.5, "wax", over=False)
        sc.box("body", -0.25 - 0.25, BY + 0.0, -8.0, 0.5, BY + 0.5, -7.0, "wick")
        sc.add("body", lambda x, y, z: (x / 0.8) ** 2 + ((y - (BY + 0.75)) / 1.25) ** 2 + ((z + 7.75) / 0.8) ** 2 <= 1.0,
               "flame", (-1.5, BY - 1, -9, 1.5, BY + 2.5, -6.5))
        sc.box("body", -0.5, BY + 2.0, -8.0, 0.0, BY + 2.5, -7.5, "flame")

    # ---- 脚 (細く、ぶら下がる)
    for s in (-1, 1):
        for z0, z1 in ((3.0, 4.5), (1.75, 1.75), (0.5, -1.0)):
            sc.line("body", (s * 0.75, BY - 1.75, z0), (s * 1.75, BY - 2.5, z1), "leg")
            sc.line("body", (s * 1.75, BY - 2.5, z1), (s * 1.75, BY - 3.25, z1 - 0.5), "leg")
    return sc


MATS = {
    "a": {"forewing": "wing_grey", "hindwing": "wing_grey", "vein": "ash", "fuzz": "fuzz", "fuzz_lt": "ash", "eye": "eye",
          "antenna": "ash", "antenna_b": "ash", "abd": "ash", "abd_glow": "ember", "leg": "slag"},
    "b": {"forewing": "wing_or", "hindwing": "wing_or", "vein": "deepred", "fuzz": "fuzz", "fuzz_lt": "ash", "eye": "eye",
          "antenna": "rust", "antenna_b": "rust", "abd": "deepred", "abd_glow": "ember", "leg": "slag"},
    "c": {"forewing": "wing_soot", "hindwing": "wing_soot", "vein": "slag", "fuzz": "fuzz", "fuzz_lt": "ash", "eye": "eye",
          "antenna": "ash", "antenna_b": "ash", "wax": "wax", "wick": "char", "flame": "molten", "leg": "slag"},
}


def set_ramps(opt):
    for nm, rp in MATS[opt].items():
        E.tm.RAMP[nm] = E.EMBER_RAMP[rp]


def make_paint(opt):
    W = WINGS[opt]
    R = E.EMBER_RAMP

    def wing_info(X, Z, mat):
        u = abs(X)
        poly = W["fore"] if mat == "forewing" else W["hind"]
        edge = not all(in_poly(u + du, Z + dz, poly) for du, dz in ((0.75, 0), (-0.75, 0), (0, 0.75), (0, -0.75)))
        return u, edge

    def eyespot(u, Z, mat):
        if opt == "a":
            c, r = ((6.5, 1.5), 1.2) if mat == "forewing" else ((5.25, -3.25), 1.5)
        elif opt == "c":
            c, r = ((8.25, 3.0), 0.7) if mat == "forewing" else ((4.0, -2.0), 0.6)
        else:
            return None
        d = math.hypot(u - c[0], Z - c[1]) / r
        return d

    def vein(u, Z, mat):
        # 付け根 (2, 1) から放射状に出る筋
        a = math.degrees(math.atan2(Z - 1.0, u - 2.0))
        rr = math.hypot(u - 2.0, Z - 1.0)
        if rr < 1.0:
            return False
        for ang in ((50, 28, 8, -15, -40) if mat == "forewing" else (-10, -40, -70, -95)):
            if abs(((a - ang + 180) % 360) - 180) * math.radians(1) * rr < 0.3:
                return True
        return False

    def paint(X, Y, Z, f, mat, st):
        if mat in ("forewing", "hindwing"):
            u, edge = wing_info(X, Z, mat)
            if opt == "a":
                d = eyespot(u, Z, mat)
                if d is not None and d < 0.45:
                    return R["molten"][2]
                if d is not None and d < 0.8:
                    return R["ember"][1]
                if d is not None and d < 1.05:
                    return R["char"][1]
                if edge:
                    return R["wing_grey"][0] if E.h01(X, Y, Z) > 0.5 else R["ash"][2]
                band = math.floor(math.hypot(u - 2, Z - 1) * 1.4) % 4 == 0
                return R["wing_grey"][0] if band else (R["wing_grey"][2] if E.h01(X, Y, Z) > 0.85 else R["wing_grey"][1])
            if opt == "b":
                if vein(u, Z, mat):
                    return R["molten"][2]
                if edge:
                    return R["char"][2]
                rr = math.hypot(u - 2, Z - 1) / 9.0
                return R["wing_or"][2] if rr > 0.55 else (R["wing_or"][1] if rr > 0.25 else R["deepred"][2])
            if opt == "c":
                d = eyespot(u, Z, mat)
                if d is not None and d < 0.8:
                    return R["ember"][2]
                if edge:
                    return R["ash"][1] if E.h01(X, Y, Z) > 0.4 else R["wing_soot"][0]
                return R["wing_soot"][2] if E.h01(X, Y, Z) > 0.88 else R["wing_soot"][1]
        if mat == "eye":
            return R["ember"][2] if (E.h01(X, Y, Z) > 0.6) else R["deepred"][2]
        if mat == "abd_glow":
            return R["ember"][2] if f != "down" else R["molten"][2]
        if mat == "abd":
            return R["ash"][0] if opt == "a" else R["deepred"][0]
        if mat == "wax":
            if Z < -5.5:
                return R["wax"][3] if E.h01(X, Y, Z) > 0.5 else R["molten"][3]
            return R["wax"][2] if E.h01(X, Y, Z) > 0.7 else R["wax"][1]
        if mat == "flame":
            return R["molten"][3] if Y < BY + 1.5 else R["molten"][2]
        if mat in ("fuzz", "fuzz_lt"):
            h = E.h01(X, Y, Z)
            rp = R["fuzz"] if mat == "fuzz" else R["ash"]
            return rp[3] if h < 0.2 else (rp[0] if h > 0.85 else None)
        if mat == "antenna_b":
            return R["ash"][3] if opt != "b" else R["ember"][2]
        return None

    def lit(X, Y, Z, f, mat, st):
        if mat in ("abd_glow", "flame", "eye"):
            return True
        if mat == "wax":
            return Z < -5.5 and f != "down"
        if mat in ("forewing", "hindwing"):
            u, _ = wing_info(X, Z, mat)
            if opt == "b":
                return vein(u, Z, mat)
            d = eyespot(u, Z, mat)
            return d is not None and d < (0.8 if opt == "a" else 0.8)
        if mat == "antenna_b" and opt == "b":
            return True
        return False
    return paint, lit


def flap_pose(a=38.0):
    return {"wing_r": (0, 0, -a), "wing_l": (0, 0, a)}


LABELS = {"a": "A 灰の翅 (火の目玉模様)", "b": "B 燃える翅 (光る翅脈)", "c": "C 燭の蛾 (蝋燭の腹)"}


def run():
    folder = E.MOBS / "ember_moth"
    cols, stats = [], {}
    for opt in "abc":
        set_ramps(opt)
        sc = build(opt)
        paint, lit = make_paint(opt)
        m = E.build_model(sc, paint, lit)
        r = E.export_states(m, folder / opt, ("base",))
        tex, gl, gpx = r["states"]["base"]
        allb = {b["name"] for b in r["geo"]["minecraft:geometry"][0]["bones"]}
        flap_hits = E.posed_overlaps(r["geo"], flap_pose())
        flap_hits2 = E.posed_overlaps(r["geo"], flap_pose(70.0))
        xs = [c["origin"][0] + d for b in r["geo"]["minecraft:geometry"][0]["bones"] for c in b.get("cubes", []) for d in (0, c["size"][0])]
        st = dict(boxes=r["n"], overlaps=len(r["overlaps"]), overlaps_flap38_sat=len(flap_hits), overlaps_flap70_sat=len(flap_hits2),
                  buried=E.buried_in_state(m._boxes, allb), glow_px=gpx, wingspan_blocks=round((max(xs) - min(xs)) / 16, 2))
        if flap_hits2:
            st["flap70_hits"] = flap_hits2[:6]
        stats[opt] = st
        print("ember_moth", opt, st)
        rows = []
        views = [("front34", 205, 22, None), ("side", 270, 8, None), ("back34", 25, 30, None), ("top", 180, 80, None),
                 ("flap_up", 205, 18, flap_pose(70.0)), ("flap_mid", 160, 12, flap_pose(38.0)), ("lava_light", 205, 22, "dim")]
        for name, yaw, pitch, pose in views:
            if pose == "dim":
                fc = E.faces_of(r["geo"], E.lava_light(tex), gl)
            else:
                fc = E.faces_of(r["geo"], tex, gl, pose=pose)
            img = E.render(fc, yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        cols.append((LABELS[opt], rows))
    (folder / "stats.json").write_text(E.json.dumps(stats, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    E.vx.sheet("ember_moth ラフ (火の粉蛾)", cols,
               note="行: 正面3/4 / 横 / 背面3/4 / 真上 / 羽ばたき (上げ70度) / 羽ばたき (38度、前から) / 溶岩の暗がり。翅の幅約1.2ブロック").save(E.REVIEW / "ember_moth.png")
    return stats


if __name__ == "__main__":
    run()
