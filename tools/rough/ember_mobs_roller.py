"""燠虫 (cinder_roller, おきむし): 溶岩のダンゴムシ。長さ約 1.4 ブロック、丸まって転がる。

体は 9 枚の節 (0 = 尾, 8 = 頭)。どの節も「くさび」の中にだけセルを置く:
  節 k の軸 (y = CY, z = z_k) から見て、前後の幅が |z - z_k| <= (y - CY) * tan(19.5 度)。
丸まりの姿勢では、各節の骨を軸 (= 骨の pivot) のまわりに (k - 4) * 40 度回し、pivot を玉の中心へ動かす
(GeckoLib の rotation + position のキー)。9 枚 x 40 度 = 360 度。くさびは中心を共有して角度が違うので、
回しても節どうしは重ならない。脚・触角・尾肢は丸まりの時は隠す (本物のように内側にしまう / scale 0)。
歩く姿勢では節は上で接し、下 (腹) はくさびの V の切れ目になって、光る腹と脚が見える。
"""
from __future__ import annotations

import math

import ember_mobs_common as E

CY = 1.5                    # くさびの軸の高さ
T = math.tan(math.radians(19.5))
STEP = 2.5                  # 節の間隔 (歩く姿勢)
N = 9
PY = 6.0                    # 丸まった玉の中心の高さ
PROF_A = (0.70, 0.84, 0.94, 1.0, 1.0, 1.0, 0.96, 0.88, 0.70)  # 尾 -> 頭 の幅の割合
PROF_B = (0.72, 0.86, 0.95, 1.0, 1.0, 1.0, 0.97, 0.90, 0.74)
A0, B0 = 5.5, 5.5


def zk(k):
    return 0.25 + STEP * (k - 4)


def wedge(k):
    z0 = zk(k)

    def ok(x, y, z):
        ylo = y - 0.25
        dz = max(abs(z - 0.25 - z0), abs(z + 0.25 - z0))
        return ylo - CY > 0 and dz <= (ylo - CY) * T + 1e-9
    return ok


def dome(a, b, p=2.2):
    return lambda x, y: y >= CY and (abs(x) / a) ** p + (abs(y - CY) / b) ** p <= 1.0


def build(opt: str):
    sc = E.Sculpt(f"cinder_roller_{opt}")
    sc.bone("body", (0, CY, 0))
    for k in range(N):
        sc.bone(f"seg{k}", (0, CY, zk(k)), "body")
    raise_top = 0.5 if opt in "bc" else 0.0

    for k in range(N):
        a, b = A0 * PROF_A[k], B0 * PROF_B[k]
        w = wedge(k)
        z0 = zk(k)
        zl, zh = z0 - 1.25, z0 + 1.25
        outer = dome(a, b)
        inner = dome(a - 1.25, b - 1.25)
        bone = f"seg{k}"

        def front_round(x, y, z, k=k, a=a):
            if k == N - 1 and z > z0:     # 頭の前は丸い
                return (abs(x) / a) ** 2 + ((z - zl) / 2.6) ** 2 <= 1.0
            if k == 0 and z < z0:         # 尾の後ろも丸い
                return (abs(x) / a) ** 2 + ((zh - z) / 2.6) ** 2 <= 1.0
            return True

        def pred(x, y, z, outer=outer, w=w, fr=front_round):
            return zl <= z <= zh and outer(x, y) and w(x, y, z) and fr(x, y, z)

        def mat(c, inner=inner):
            x, y, z = c
            return "belly" if inner(x, y) and y < CY + 2.0 else "plate"
        sc.add(bone, pred, None, (-a - 1, CY, zl, a + 1, CY + b + 1, zh), mat_fn=mat)

        if opt == "b":   # 鉱滓の瘤 (外側に 0.5)、一部に光る鉱石
            out2 = dome(a + 0.5, b + 0.5)

            def knob(x, y, z, out2=out2, w=w, fr=front_round, k=k):
                return (zl + 0.25 <= z <= zh - 0.25 and out2(x, y) and not outer(x, y) and w(x, y, z) and fr(x, y, z)
                        and E.blob_noise(x + 0.3 * k, y, z, s=1.0, seed=3) > 0.70 and y > CY + 2.0)
            sc.add(bone, knob, None, (-a - 1, CY, zl, a + 1, CY + b + 1, zh),
                   mat_fn=lambda c: "ore" if E.h01(c[0] * 3.1, c[1] * 1.7, c[2]) < 0.30 else "plate")
        if opt == "c":   # 瓦: 節の後ろ半分を 0.5 高くして重ね瓦に、背の中央に棟瓦
            out2 = dome(a + 0.5, b + 0.5)

            def lip(x, y, z, out2=out2, w=w, fr=front_round, k=k):
                return (zl <= z <= z0 + 0.25 and out2(x, y) and not outer(x, y) and w(x, y, z) and fr(x, y, z)
                        and y > CY + 3.75)
            sc.add(bone, lip, "tile", (-a - 1, CY, zl, a + 1, CY + b + 1, zh))
            if k in (2, 4, 6):
                top = CY + b + 0.5

                def ridge(x, y, z, top=top, w=w):
                    return abs(x) <= 0.75 and top <= y <= top + 0.6 and zl + 0.25 <= z <= zh - 0.25 and w(x, y, z)
                sc.add(bone, ridge, "tile_dk", (-1, top - .5, zl, 1, top + 1, zh))

    # 節の間の膜 (歩く姿勢の V の切れ目を埋める柔らかい肉。丸まりでは隠す = 節の内側にしまわれる)
    for k in range(N - 1):
        nm = f"mem{k}"
        sc.bone(nm, (0, CY, zk(k) + 1.25), f"seg{k}", hideable=True)
        a = A0 * min(PROF_A[k], PROF_A[k + 1]) - 0.5
        b = B0 * min(PROF_B[k], PROF_B[k + 1]) - 0.5
        prof = dome(a, b)
        hole = dome(a - 1.0, b - 1.0)
        zl, zh = zk(k), zk(k + 1)
        sc.add(nm, lambda x, y, z, prof=prof, hole=hole, zl=zl, zh=zh: zl <= z <= zh and prof(x, y) and not hole(x, y) and 2.5 <= y <= CY + 3.5,
               "membrane", (-a - 1, 2.5, zl, a + 1, CY + b + 1, zh))

    # 頭: 目 (横の前寄り。既にあるセルの色を変える)
    kh = N - 1
    ah, bh = A0 * PROF_A[kh], B0 * PROF_B[kh]
    sc.recolor(lambda x, y, z: abs(x) >= ah - 1.6 and CY + 0.6 <= y <= CY + 1.6 and zk(kh) - 0.25 <= z <= zk(kh) + 1.0,
               "eye", (-ah - 1, CY, zk(kh) - 2, ah + 1, CY + 3, zk(kh) + 2), bone=f"seg{kh}")

    # 触角 (頭の前の下から、前と外へ。折れて先が下がる)
    for s, nm in ((-1, "antenna_r"), (1, "antenna_l")):
        base = (s * 1.25, CY + 1.25, zk(kh) + 1.0)
        sc.bone(nm, base, f"seg{kh}")
        zf = zk(kh) + 1.25
        sc.line(nm, (s * 1.5, CY + 1.25, zf + 0.25), (s * 3.25, CY + 2.25, zf + 2.25), "plate_dk")
        sc.line(nm, (s * 3.25, CY + 2.25, zf + 2.25), (s * 4.75, CY + 0.75, zf + 3.75), "plate_dk")

    # 尾肢 (尾の後ろの 2 本の短い突起)
    sc.bone("uropods", (0, CY, zk(0)), "seg0")
    for s in (-1, 1):
        x0, x1 = sorted((s * 1.0, s * 2.0))
        sc.box("uropods", x0, CY + 0.5, zk(0) - 3.0, x1, CY + 1.0, zk(0) - 2.0, "plate_dk")
        x0, x1 = sorted((s * 1.5, s * 2.5))
        sc.box("uropods", x0, CY + 0.0, zk(0) - 3.5, x1, CY + 0.5, zk(0) - 2.5, "plate_dk")

    # 脚 (節 1..7 に 1 対ずつ。腹の下の帯 z_k +- 0.25 の中で、外へ下へ)
    for k in range(1, N - 1):
        z0 = zk(k)
        zl, zh = z0 - 0.25, z0 + 0.25
        spread = 0.5 if k in (1, 7) else 0.0
        for s, side in ((-1, "r"), (1, "l")):
            nm = f"leg{k}_{side}"
            sc.bone(nm, (s * 1.5, 2.5, z0), f"seg{k}")

            def xr(a, b, s=s):
                return sorted((s * a, s * b))
            sc.box(nm, xr(1.5, 3.5)[0], 2.0, zl, xr(1.5, 3.5)[1], 2.5, zh, "leg")
            sc.box(nm, xr(3.5, 4.5)[0], 1.0, zl, xr(3.5, 4.5)[1], 2.0, zh, "leg")
            sc.box(nm, xr(4.5, 5.0 + spread)[0], 0.0, zl, xr(4.5, 5.0 + spread)[1], 1.0, zh, "leg_tip")
    return sc


# ---------------------------------------------------------------- 塗り

RAMPS = {
    "a": {"membrane": "deepred", "plate": "basalt", "plate_dk": "basalt", "belly": "ember", "leg": "deepred", "leg_tip": "basalt"},
    "b": {"membrane": "deepred", "plate": "slag", "plate_dk": "slag_dk", "belly": "ember", "leg": "deepred", "leg_tip": "slag_dk", "ore": "molten"},
    "c": {"membrane": "deepred", "plate": "tile", "plate_dk": "tile_dk", "belly": "ember", "leg": "deepred", "leg_tip": "rust", "tile": "tile",
          "tile_dk": "tile_dk"},
}


def seg_of(Z):
    return min(range(N), key=lambda k: abs(Z - zk(k)))


def make_paint(opt):
    def edge_d(Z):
        k = seg_of(Z)
        return min(abs(Z - (zk(k) - 1.25)), abs(Z - (zk(k) + 1.25)))

    def paint(X, Y, Z, f, mat, st):
        if mat == "eye":
            return "#FFB040" if (f in ("east", "west") and abs(Y - (CY + 1.1)) < 0.3) else E.EMBER_RAMP["basalt"][0]
        if mat in ("leg",):
            return E.EMBER_RAMP["deepred"][2] if Y > 1.5 else E.EMBER_RAMP["ember"][1]
        if mat == "belly":
            return E.EMBER_RAMP["ember"][2] if E.h01(X, Y, Z) > 0.3 else E.EMBER_RAMP["molten"][1]
        if mat == "ore":
            return E.EMBER_RAMP["molten"][2]
        if mat == "membrane":
            return E.EMBER_RAMP["ember"][1] if Y < CY + 1.6 else E.EMBER_RAMP["deepred"][1 if E.h01(X, Y, Z) > 0.3 else 0]
        ramp = E.EMBER_RAMP[RAMPS[opt].get(mat, "basalt")]
        if mat in ("plate", "tile", "tile_dk"):
            if f in ("north", "south") and Y < CY + 2.0:   # くさびの奥 (腹側): 溶岩の色
                return E.EMBER_RAMP["ember"][1]
            if f in ("north", "south") and Y < CY + 3.0:
                return E.EMBER_RAMP["deepred"][2]
            if opt == "a":
                if f == "up" and edge_d(Z) < 0.3:
                    return E.EMBER_RAMP["ember"][2]          # 節の継ぎ目の溶岩
                if E.voronoi_edge(X, Y, Z, s=2.2, w=0.22, seed=5) and f == "up" and abs(X) > 1.0:
                    return E.EMBER_RAMP["deepred"][1]
                h = E.h01(X, Y, Z)
                if h < 0.12:
                    return ramp[2]
                if h > 0.9:
                    return ramp[0]
                return None
            if opt == "b":
                if f == "up" and edge_d(Z) < 0.3:
                    return E.EMBER_RAMP["deepred"][1]
                h = E.h01(X, Y, Z)
                if h < 0.18:
                    return ramp[3]
                if h < 0.35:
                    return ramp[2]
                if h > 0.85:
                    return E.EMBER_RAMP["rust"][1]
                return None
            if opt == "c":
                if f == "up" and edge_d(Z) < 0.3:
                    return E.EMBER_RAMP["ember"][1]
                if f == "up" and (math.floor((X + 0.75 * (seg_of(Z) % 2)) * 2) % 3 == 0):
                    return ramp[0]                       # 瓦の縦の目地
                if E.h01(X, Y, Z) < 0.1:
                    return E.EMBER_RAMP["rust"][2]
                return None
        return None

    def lit(X, Y, Z, f, mat, st):
        if mat in ("belly", "ore", "leg"):
            return True
        if mat == "membrane":
            return Y < CY + 1.6
        if mat == "eye":
            return f in ("east", "west") and abs(Y - (CY + 1.1)) < 0.3
        if mat in ("plate", "tile", "tile_dk"):
            if f in ("north", "south") and Y < CY + 3.0:
                return True
            if f == "up" and edge_d(Z) < 0.3 and opt in "ac":
                return True
            if opt == "a" and f == "up" and abs(X) > 1.0 and E.voronoi_edge(X, Y, Z, s=2.2, w=0.22, seed=5):
                return True
        return False
    return paint, lit


def set_ramps(opt):
    for nm, rp in RAMPS[opt].items():
        E.tm.RAMP[nm] = E.EMBER_RAMP[rp]
    E.tm.RAMP["eye"] = E.EMBER_RAMP["basalt"]


def curl_pose():
    rot = {f"seg{k}": ((k - 4) * 40.0, 0.0, 0.0) for k in range(N)}
    pos = {f"seg{k}": (0.0, PY - CY, -zk(k)) for k in range(N)}
    hide = [f"leg{k}_{s}" for k in range(1, N - 1) for s in "rl"] + ["antenna_r", "antenna_l", "uropods"] + [f"mem{k}" for k in range(N - 1)]
    return rot, pos, hide


LABELS = {"a": "A 玄武の殻 (継ぎ目の溶岩)", "b": "B 鉱滓の殻 (光る鉱石の粒)", "c": "C 瓦の殻 (灰の民の屋根瓦)"}


def run():
    folder = E.MOBS / "cinder_roller"
    cols, stats = [], {}
    for opt in "abc":
        set_ramps(opt)
        sc = build(opt)
        paint, lit = make_paint(opt)
        m = E.build_model(sc, paint, lit)
        r = E.export_states(m, folder / opt, ("base",))
        tex, gl, gpx = r["states"]["base"]
        rot, pos, hide = curl_pose()
        g_curl = E.posed_geo(r["geo"], pos, hide)
        curl_hits = E.posed_overlaps(g_curl, rot)
        walk_hits = E.posed_overlaps(r["geo"])
        allb = {b["name"] for b in r["geo"]["minecraft:geometry"][0]["bones"]}
        buried = E.buried_in_state(m._boxes, allb)
        st = dict(boxes=r["n"], overlaps=len(r["overlaps"]), overlaps_walk_sat=len(walk_hits),
                  overlaps_curled_sat=len(curl_hits), buried=buried, dropped_hidden=r["dropped"], glow_px=gpx)
        if curl_hits:
            st["curl_hits_sample"] = curl_hits[:8]
        stats[opt] = st
        print("cinder_roller", opt, st)
        rows = []
        views = [("front34", 205, 18, None), ("side", 270, 8, None), ("back34", 25, 22, None),
                 ("under", 205, -40, None), ("curled34", 205, 18, "curl"), ("curled_side", 270, 8, "curl"), ("curled_back34", 320, 30, "curl"),
                 ("lava_light", 205, 18, "dim")]
        for name, yaw, pitch, mode in views:
            if mode == "curl":
                fc = E.faces_of(r["geo"], tex, gl, pose=rot, pos=pos, hide=hide)
            elif mode == "dim":
                fc = E.faces_of(r["geo"], E.lava_light(tex), gl)
            else:
                fc = E.faces_of(r["geo"], tex, gl)
            img = E.render(fc, yaw, pitch)
            img.save(folder / opt / f"render_{name}.png")
            rows.append(img)
        cols.append((LABELS[opt], rows))
    (folder / "stats.json").write_text(E.json.dumps(stats, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    E.vx.sheet("cinder_roller ラフ (燠虫: 歩く姿と丸まった玉)", cols,
               note="行: 正面3/4 / 横 / 背面3/4 / 腹 (下から) / 丸まり3/4 / 丸まり横 (真横) / 丸まり背面3/4 / 溶岩の暗がり。長さ約1.4ブロック、玉は約0.6ブロック").save(E.REVIEW / "cinder_roller.png")
    return stats


if __name__ == "__main__":
    run()
