"""灰被り用の追加: 暖色の光の描画・溶岩のひび・2 つの姿 (殻 / 燃え上がり) の書き出しと描画。"""
from __future__ import annotations

import inspect as _inspect
from itertools import product

from common import *  # noqa
from common import _mk  # noqa
import lpkit as _lpkit

# ====================================================================== 暖色の光の描画 (lpkit の render の光のにじみだけ橙に)
_ns = dict(vars(_lpkit))
exec(compile(_inspect.getsource(_lpkit.render).replace("[140, 230, 255]", "[255, 140, 40]"), "warm_render", "exec"), _ns)
render_warm = _ns["render"]


# ====================================================================== 溶岩のひび (ボロノイの縁)

def worley_edge(P, cell, seed=0):
    """3D 位置 P (..., 3) のボロノイ図の f2 - f1 (単位) と f1。縁 (ひび) ほど小さい。"""
    Q = np.asarray(P, float) / cell
    base = np.floor(Q)
    f1 = np.full(Q.shape[:-1], 9.0)
    f2 = np.full(Q.shape[:-1], 9.0)
    for dx, dy, dz in product((-1, 0, 1), repeat=3):
        c = base + np.array([dx, dy, dz])
        pt = c + np.stack([hash3(c, seed), hash3(c, seed + 1), hash3(c, seed + 2)], -1) * 0.9 + 0.05
        d = np.linalg.norm(Q - pt, axis=-1)
        new1 = d < f1
        f2 = np.where(new1, f1, np.minimum(f2, d))
        f1 = np.where(new1, d, f1)
    return (f2 - f1) * cell, f1 * cell


def lava_extra(mats_on, cell=3.4, thr=0.34, halo=0.8, seed=3, glowing=True,
               core=(255, 232, 120), mid=(255, 132, 34), edge=(150, 42, 24), spark=0.0, bones=None, exclude=()):
    """mats_on の材質の面に、ひびの溶岩を描く。glowing=False なら光らない (冷えた暗い赤の線だけ)。"""
    def fn(ctx, col, glow):
        e, f1 = worley_edge(ctx["P"], cell, seed)
        sel = np.isin(ctx["mats"], list(mats_on)) & ctx["owned"]
        if bones is not None:
            sel &= np.isin(ctx["bones"], list(bones))
        if exclude:
            sel &= ~np.isin(ctx["bones"], list(exclude))
        bay = ctx["bay"]
        c_core = sel & (e < thr * 0.5)
        c_mid = sel & (e < thr) & ~c_core
        c_halo = sel & (e < halo) & ~c_core & ~c_mid & (bay > -0.05)
        col = col.copy()
        col[c_halo] = edge
        col[c_mid] = mid
        col[c_core] = core
        if spark > 0:
            sp_ = sel & (hash3(np.floor(ctx["P"] * 1.0), seed + 7) < spark) & ~c_core
            col[sp_] = mid
            c_mid = c_mid | sp_
        if glowing:
            glow = glow | c_core | c_mid
        return col, glow
    return fn


def chain_extra(*fns):
    def fn(ctx, col, glow):
        for f in fns:
            if f is not None:
                col, glow = f(ctx, col, glow)
        return col, glow
    return fn


# ====================================================================== 書き出し (2 つの姿: 殻 / 燃え上がり)

CRUST = [b for b in BONES if b.startswith("crust_")]
FLARE = [b for b in BONES if b.startswith("flare_")]
HIDE = {"n": lambda b: b.startswith("flare_"), "f": lambda b: b.startswith("crust_")}


def vis(faces, state):
    return [f for f in faces if not HIDE[state](f.bone)]


def tri_state(faces, state):
    return tri_count(vis(faces, state))


def _mask(glow, size, path):
    if glow.any():
        gm = np.zeros((size, size, 4), np.uint8)
        gm[glow] = (255, 255, 255, 255)
        save_png(gm, path)


def fit_atlas(faces, hint, step=16):
    """面積に合わせた最小の正方形アトラス (hint から step 刻みで大きく)。2 のべき乗でなくてよい。"""
    size = hint
    while True:
        r = build_atlas(faces, size, 0)
        if r[0] == size:
            return r
        size += step


def build_option(key, build, body_hint=128, weapon_hint=32, extra_note=""):
    """build() -> dict(body, weapon, pal_n, pal_f, ex_n, ex_f, wpal, wex)。"""
    b = build()
    body, weapon = b["body"], b["weapon"]
    d = OUT / SUBJ / key
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = fit_atlas(body, body_hint)
    atlas_n, glow_n = paint(body, size, P, N, M, b["pal_n"], b.get("ex_n"))
    atlas_f, glow_f = paint(body, size, P, N, M, b["pal_f"], b.get("ex_f"))
    save_png(atlas_n, d / f"{NAME}_{key}_atlas.png")
    save_png(atlas_f, d / f"{NAME}_{key}_atlas_flared.png")
    _mask(glow_n, size, d / f"{NAME}_{key}_glowmask.png")
    _mask(glow_f, size, d / f"{NAME}_{key}_glowmask_flared.png")
    data = export_mesh(body, size, d / f"{NAME}_{key}_mesh.json", f"{NAME}_{key}", f"{NAME}_{key}_atlas.png", BONES,
                       {"glowmask": f"{NAME}_{key}_glowmask.png",
                        "texture_flared": f"{NAME}_{key}_atlas_flared.png",
                        "glowmask_flared": f"{NAME}_{key}_glowmask_flared.png",
                        "states": {"normal_hidden_bones": FLARE, "flared_hidden_bones": CRUST,
                                   "note": "same UVs for both states; swap the atlas and hide crust_* (flared) or flare_* (normal) per render, "
                                           "like the in-game ash_thrall / ash_thrall_flared"},
                        "note": "geo space (root carries rotation [0,180,0]); vertices are absolute like geo cubes; 20 bones = in-game "
                                "ash_thrall.geo.json; socket_main is an empty bone (poker = separate mesh)" + extra_note})
    wsize, wP, wN, wM, wL = fit_atlas(weapon, weapon_hint)
    watlas, wglow = paint(weapon, wsize, wP, wN, wM, b["wpal"], b.get("wex"))
    save_png(watlas, d / f"{NAME}_{key}_poker_atlas.png")
    _mask(wglow, wsize, d / f"{NAME}_{key}_poker_glowmask.png")
    wdata = export_mesh(weapon, wsize, d / f"{NAME}_{key}_poker_mesh.json", f"{NAME}_{key}_poker",
                        f"{NAME}_{key}_poker_atlas.png", STAFF_BONES,
                        {"glowmask": f"{NAME}_{key}_poker_glowmask.png",
                         "attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in SOCKET], "socket": True,
                                    "note": "vertices are in the socket bone's local space (origin = socket pivot), same as geo/gear/ash_poker.geo.json"}},
                        flip=False)
    return dict(key=key, body=body, weapon=weapon, size=size, atlas={"n": atlas_n, "f": atlas_f}, glow={"n": glow_n, "f": glow_f},
                data=data, bsize=wsize, batlas=watlas, bglow=wglow, bdata=wdata)


def combined_atlas(r, state):
    S, BS = r["size"], r["bsize"]
    side = S + BS
    big = np.zeros((side, side, 4), np.uint8)
    big[:S, :S] = r["atlas"][state]
    big[S:S + BS, :BS] = r["batlas"]
    glow = np.zeros((side, side), bool)
    glow[:S, :S] = r["glow"][state]
    glow[S:S + BS, :BS] = r["bglow"]
    return big, glow, S


def weapon_faces_shifted(r, yoff):
    import copy
    out = []
    for f in r["weapon"]:
        g = copy.copy(f)
        g.uv = f.uv + np.array([0, yoff])
        out.append(g)
    return out


def render_pose(r, pose, yaw, pitch, size, center, scale, state="n", body=True, weapon=True):
    big, glow, yoff = combined_atlas(r, state)
    faces = []
    if body:
        faces += pose_body(vis(r["body"], state), pose)
    if weapon:
        faces += pose_staff(weapon_faces_shifted(r, yoff), pose)
    return render_warm(faces, big, glow, yaw, pitch, size, center, scale)


def flame_extra(mats_on=("flame",), seed=2):
    """炎の面: 縁が赤橙、中心が黄白の熱い芯 (面の縁からの距離で段をつける)。"""
    def fn(ctx, col, glow):
        sel = np.isin(ctx["mats"], list(mats_on)) & ctx["owned"]
        heat = np.array([getattr(f, "heat", 0.5) for f in ctx["faces"]])[ctx["fi"]]
        v = heat * 3.1 - 0.3 + 0.7 * ctx["bay"] + 0.5 * (np.minimum(ctx["D"], 2.0) - 0.8) + 0.3 * (hash1(ctx["fi"], seed) - 0.5)
        pal = np.array([(255, 90, 20), (255, 140, 36), (255, 196, 80), (255, 238, 160)], float)
        idx = np.clip(np.floor(v), 0, 3).astype(int)
        col = col.copy()
        col[sel] = pal[idx][sel]
        glow = glow | sel
        return col, glow
    return fn


def brick_extra(mats_on, bh=5, bw=9, seed=3, bricks=((52, 20, 16), (70, 26, 18), (40, 16, 14)), hot=0.1):
    """面ごとの煉瓦積み。目地が光り、ところどころ煉瓦ごと赤熱する (窯)。"""
    def fn(ctx, col, glow):
        LU, LV, fi = ctx["LU"], ctx["LV"], ctx["fi"]
        sel = np.isin(ctx["mats"], list(mats_on)) & ctx["owned"] & ctx["INS"]
        row = LV // bh
        off = (row % 2) * (bw // 2)
        cu = (LU + off) // bw
        mortar = (LV % bh == 0) | ((LU + off) % bw == 0)
        pick = (hash1(fi * 7 + row * 3 + cu, seed) * 3).astype(int) % 3
        bc = np.array(bricks, float)[pick]
        redhot = hash1(fi * 11 + row * 5 + cu * 2, seed + 4) < hot
        col = col.copy()
        body = sel & ~mortar
        col[body] = bc[body]
        mg = sel & mortar
        col[mg] = (255, 150, 40)
        rh = body & redhot
        col[rh] = (232, 96, 28)
        glow = glow | mg | rh
        return col, glow
    return fn


def worley2(Q, seed=0):
    """2D ボロノイの f2 - f1 (セル単位)。"""
    base = np.floor(Q)
    f1 = np.full(Q.shape[:-1], 9.0)
    f2 = np.full(Q.shape[:-1], 9.0)
    for dx, dy in product((-1, 0, 1), repeat=2):
        c = base + np.array([dx, dy])
        h = hash3(np.concatenate([c, np.zeros(c.shape[:-1] + (1,))], -1), seed)
        h2 = hash3(np.concatenate([c, np.ones(c.shape[:-1] + (1,))], -1), seed + 5)
        pt = c + np.stack([h, h2], -1) * 0.85 + 0.07
        d = np.linalg.norm(Q - pt, axis=-1)
        new1 = d < f1
        f2 = np.where(new1, f1, np.minimum(f2, d))
        f1 = np.where(new1, d, f1)
    return f2 - f1


def crack2d_extra(mats_on, cell=6.0, thr=0.16, seed=1, min_area=24, prob=0.55, color=(118, 42, 26), color2=None, glowing=False,
                  halo=None):
    """面ごとに別のひび (面の中の座標で描く)。冷えた殻の割れ目・熱がすけた赤い線。"""
    def fn(ctx, col, glow):
        fi = ctx["fi"]
        off = np.stack([hash1(fi, seed) * 80.0, hash1(fi, seed + 1) * 80.0], -1)
        Q = (np.stack([ctx["LU"] + 0.5, ctx["LV"] + 0.5], -1) + off) / cell
        e = worley2(Q, seed)
        sel = np.isin(ctx["mats"], list(mats_on)) & ctx["owned"] & ctx["INS"] & (hash1(fi, seed + 3) < prob) & (ctx["area"] >= min_area)
        on = sel & (e < thr)
        col = col.copy()
        if halo is not None:
            col[sel & (e < thr * 2.0) & ~on & (ctx["bay"] > 0.0)] = halo
        col[on] = color
        if color2 is not None:
            col[on & (ctx["bay"] > 0.1)] = color2
        if glowing:
            glow = glow | on
        return col, glow
    return fn
