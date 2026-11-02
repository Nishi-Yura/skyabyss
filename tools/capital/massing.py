# -*- coding: utf-8 -*-
"""家の形の部品 (2026-10-27「もっと色んなバリエーション、クオリティの高い建築」)。vox.Body (壁の作り) の上に、形の違う家と屋根を足す。

名作の街 (WesterosCraft の王都・ElysiumFire の Novigrad・Wynncraft の町) と建築の手引きから取った決まり:
  - 形が先: 足もとの形 (細長い・L 字・塔)、棟の向き (妻入りと平入りを混ぜる)、階の数で、屋根の線を揃えない
  - 奥行き: 上の階を通りへ一つ張り出す・軒を出す・屋根の裾を反らせる (一段目だけ緩く)
  - 屋根: 急な切妻・緩い切妻・反りの切妻・寄棟・半切妻・段の妻。色は土地の瓦を主に、二〜三の色を混ぜる
  - 飾りの棘 (避雷の棒) は特別な建物だけ。普通の家に付けると町の屋根の線が騒がしくなる

roof(v, st, x0, z0, x1, z1, y, axis, kind, mat=None, over=1) → 棟の高さ
house_kind(...) はそれぞれ一棟を建て、(Body, 棟の高さ) を返す。座標は壁の外の面。
"""
from __future__ import annotations

from vox import Vox, Body, Mat, Style, DX, OPP, gable_roof, hip_spire, timber_upper, door_canopy, _dormer, _chimney, _transpose_into

PROFILES = {
    'steep': None,          # 1 段で 2 上がる (今の切妻)
    'low': [1] * 20,        # 1 段で 1
    'bell': [1] + [2] * 20,  # 裾だけ緩く、上は急 (反り)
    'kick': [1, 1] + [2] * 20,
}


def roof(v: Vox, st: Style, x0, z0, x1, z1, y, axis='x', kind='steep', mat=None, over=1, dormers=0, chimney=None, finial=False,
         gable_fill=None):
    """屋根を一つ。kind = steep | low | bell | kick | hip | hip_low | half_hip | stepped。返り値 = 棟 (いちばん上の段) の高さ"""
    if kind == 'steep':
        return gable_roof(v, st, x0, z0, x1, z1, y, axis=axis, over=over, steep=True, dormers=dormers, chimney=chimney, mat=mat,
                          finial=finial, gable_fill=gable_fill)
    if axis == 'z':
        tmp = Vox()
        tmp.rng = v.rng
        ridge = roof(tmp, st, z0, x0, z1, x1, y, 'x', kind, mat, over, dormers, None, finial, gable_fill)
        _transpose_into(v, tmp)
        if chimney:
            _chimney(v, st, chimney[0], chimney[1], y, ridge)
        return ridge
    if kind in ('hip', 'hip_low'):
        return _hip(v, st, x0, z0, x1, z1, y, mat, over, 2 if kind == 'hip' else 1, chimney)
    if kind == 'half_hip':
        return _profile(v, st, x0, z0, x1, z1, y, mat, over, [2] * 20, hip_from=2, dormers=dormers, chimney=chimney, gable_fill=gable_fill)
    if kind == 'stepped':
        r = _profile(v, st, x0, z0, x1, z1, y, mat, over, [2] * 20, dormers=dormers, chimney=chimney, gable_fill=gable_fill, edge=False)
        _stepped_gables(v, st, x0, z0, x1, z1, y, over)
        return r + 3
    return _profile(v, st, x0, z0, x1, z1, y, mat, over, PROFILES[kind], dormers=dormers, chimney=chimney, gable_fill=gable_fill)


def _profile(v, st, x0, z0, x1, z1, y, mat, over, rises, hip_from=None, dormers=0, chimney=None, gable_fill=None, edge=True):
    """棟が東西の切妻を、段ごとの上がり (rises) で。hip_from = その段から妻を寄棟に (半切妻)"""
    R = mat or st.roof
    E = st.roof_edge if edge else R
    gf = gable_fill or st.infill
    za, zb = z0 - over, z1 + over
    xa, xb = x0 - over, x1 + over
    yy = y
    zn, zs = za, zb
    layer = 0
    ea, eb = xa, xb
    while zn <= zs:
        rise = rises[min(layer, len(rises) - 1)]
        hipped = hip_from is not None and layer >= hip_from
        if hipped and layer > hip_from:
            ea, eb = ea + 1, eb - 1
        for r in range(rise):
            yl = yy + r
            for x in range(ea, eb + 1):
                end = x in (ea, eb) and not hipped
                m = E if end else R
                if zn == zs:
                    v.set(x, yl, zn, m.slab('bottom') if r == rise - 1 else m.full)
                    continue
                v.set(x, yl, zn, m.stairs('south'))
                v.set(x, yl, zs, m.stairs('north'))
                v.set(x, yl, zn + 1, m.full, keep=True)
                v.set(x, yl, zs - 1, m.full, keep=True)
            if hipped:
                for z in range(zn + 1, zs):
                    v.set(ea, yl, z, R.stairs('east'))
                    v.set(eb, yl, z, R.stairs('west'))
                    v.set(ea + 1, yl, z, R.full, keep=True)
                    v.set(eb - 1, yl, z, R.full, keep=True)
                v.set(ea, yl, zn, R.stairs('south', 'bottom', 'outer_left'))
                v.set(eb, yl, zn, R.stairs('south', 'bottom', 'outer_right'))
                v.set(ea, yl, zs, R.stairs('north', 'bottom', 'outer_right'))
                v.set(eb, yl, zs, R.stairs('north', 'bottom', 'outer_left'))
            else:
                for x in (x0, x1):
                    for z in range(zn + 1, zs):
                        v.set(x, yl, z, gf)
        if zn + 1 == zs:
            for x in range(ea, eb + 1):
                m = E if x in (ea, eb) and not hipped else R
                v.set(x, yy + rise, zn, m.slab('bottom'))
                v.set(x, yy + rise, zs, m.slab('bottom'))
            yy += rise
            break
        yy += rise
        zn, zs = zn + 1, zs - 1
        layer += 1
    ridge = yy
    if z1 - z0 >= 6 and hip_from is None:
        for x in (x0, x1):
            v.set(x, y + 2, (z0 + z1) // 2, st.glass)
            v.set(x, y + 3, (z0 + z1) // 2, st.glass)
    if dormers:
        span = x1 - x0
        for k in range(dormers):
            xd = x0 + round((k + 1) * span / (dormers + 1))
            _dormer(v, st, xd, z1, y, 'south', R)
            _dormer(v, st, xd, z0, y, 'north', R)
    if chimney:
        _chimney(v, st, chimney[0], chimney[1], y, ridge)
    return ridge


def _hip(v, st, x0, z0, x1, z1, y, mat, over, rise, chimney):
    """寄棟 (四方の流れ)。長い向きに棟が残る"""
    R = mat or st.roof
    xa, za, xb, zb = x0 - over, z0 - over, x1 + over, z1 + over
    yy = y
    while xa <= xb and za <= zb:
        for r in range(rise):
            yl = yy + r
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    n, s_, w, e = z == za, z == zb, x == xa, x == xb
                    if not (n or s_ or w or e):
                        continue
                    if za == zb or xa == xb:
                        v.set(x, yl, z, R.slab('bottom') if r == rise - 1 else R.full)
                    elif n and w:
                        v.set(x, yl, z, R.stairs('south', 'bottom', 'outer_left'))
                    elif n and e:
                        v.set(x, yl, z, R.stairs('south', 'bottom', 'outer_right'))
                    elif s_ and w:
                        v.set(x, yl, z, R.stairs('north', 'bottom', 'outer_right'))
                    elif s_ and e:
                        v.set(x, yl, z, R.stairs('north', 'bottom', 'outer_left'))
                    elif n:
                        v.set(x, yl, z, R.stairs('south'))
                    elif s_:
                        v.set(x, yl, z, R.stairs('north'))
                    elif w:
                        v.set(x, yl, z, R.stairs('east'))
                    else:
                        v.set(x, yl, z, R.stairs('west'))
            # 流れの裏を埋める (段の間から中が見えない)
            for x in range(xa + 1, xb):
                for z in range(za + 1, zb):
                    if x in (xa + 1, xb - 1) or z in (za + 1, zb - 1):
                        v.set(x, yl, z, R.full, keep=True)
        yy += rise
        if za == zb or xa == xb:
            break
        xa, za, xb, zb = xa + 1, za + 1, xb - 1, zb - 1
        if (xb - xa) < 0 or (zb - za) < 0:
            break
        if zb - za == 0 or xb - xa == 0:
            pass
    if chimney:
        _chimney(v, st, chimney[0], chimney[1], y, yy)
    return yy


def _stepped_gables(v, st, x0, z0, x1, z1, y, over, rises=None):
    """段の妻 (商家の妻): 妻の壁 (x0 と x1) を屋根の線より高く、二列ずつの段に。軒の出の分の屋根は外し、各段の上に縁石"""
    rises = rises or [2] * 40
    T = st.trim
    za, zb = z0 - over, z1 + over

    def roof_h(z):
        L = min(z - za, zb - z)
        return y + sum(rises[:L + 1])
    wall = st.wall_main if hasattr(st, 'wall_main') else st.infill
    for x, xo in ((x0, x0 - 1), (x1, x1 + 1)):
        for z in range(za, zb + 1):
            for yy in range(y - 1, roof_h(z) + 3):
                v.set(xo, yy, z, 'air')
        zs = list(range(z0, z1 + 1))
        groups = [zs[i:i + 2] for i in range(0, len(zs), 2)]
        mid = len(groups) // 2
        for gi, g in enumerate(groups):
            top = max(roof_h(z) for z in g) + 1
            if gi == mid or (len(groups) % 2 == 0 and gi == mid - 1):
                top += 1
            for z in g:
                for yy in range(y, top + 1):
                    v.set(x, yy, z, wall)
                v.set(x, top + 1, z, T.slab('bottom'))
        zc = (z0 + z1) // 2
        v.set(x, roof_h(zc) + 4, zc, T.wall())
        # 段の妻の窓 (二つ)
        for z in (zc - 1, zc + 1) if (z1 - z0) >= 6 else (zc,):
            v.set(x, y + 1, z, st.glass)
            v.set(x, y + 2, z, st.glass)


# ---------------------------------------------------------------- 家の形
def _body(v, st, x0, z0, x1, z1, y0, floors, fh, front, sides, ground='window', sealed=True, infill=None, plinth=2, ornate=True):
    return Body(v, st, x0, z0, x1, z1, y0, floors=floors, fh=fh, bay=4, plinth=plinth, door_side=front, ground=ground,
                sides=sides, infill=infill, pots=0.35, sealed=sealed, ornate=ornate).build()


def jetty_brackets(v, st, b, out):
    """張り出しの下の持ち送り (木の逆さの階段)"""
    from vox import _facade_cells
    cells, o = _facade_cells(b.x0, b.z0, b.x1, b.z1, out)
    dx, dz = DX[o]
    for i, (cx, cz) in enumerate(cells):
        if i % 2 == 0 and 0 < i < len(cells) - 1:
            v.set(cx + dx, b.top, cz + dz, '%s_stairs[facing=%s,half=top,shape=straight,waterlogged=false]' % (st.wood, OPP[o]), keep=True)


def grow(rect, side, k):
    x0, z0, x1, z1 = rect
    if side == 'north':
        z0 -= k
    elif side == 'south':
        z1 += k
    elif side == 'west':
        x0 -= k
    else:
        x1 += k
    return x0, z0, x1, z1


def shrink(rect, side, k):
    return grow(rect, side, -k)


def ridge_axis(front, gable_front):
    """妻入り = 棟が通りに向かう (front が南北なら棟は南北 = 'z')"""
    ns = front in ('north', 'south')
    return ('z' if ns else 'x') if gable_front else ('x' if ns else 'z')


def build_house(town, kind, rect, front, floors, roof_kind, mat=None, sides=None, quest=False, infill=None, fh=5, extras=()):
    """一棟。kind = row (平入りの町家) | gable (妻入りの町家) | jetty (上の階の張り出し) | ell (L 字) | tower (塔の家) | cottage | merchant (段の妻の商家)
    rect = 一階の外の面。返り値 = (Body の列, 棟の高さ)"""
    v, st0 = town.v, town.st
    rng = v.rng
    st = _plain(st0, mat or st0.roof, keep_edge=kind in ('merchant', 'tower'))
    sides = sides or ('north', 'south', 'east', 'west')
    x0, z0, x1, z1 = rect
    sealed = not quest
    if quest:
        floors = max(2, floors)
    infill = infill or _skin(st0, rng)
    bodies = []
    gable_front = kind in ('gable', 'merchant', 'jetty') and rng.random() < (0.9 if kind != 'jetty' else 0.5)
    ax = ridge_axis(front, gable_front)
    chimney = None
    if 'chimney' in extras:
        chimney = (x0 + 2, z0 + 2) if (x0 + z0) % 2 else (x1 - 2, z1 - 2)
    if kind == 'jetty' and floors >= 2:
        low = _body(v, st, x0, z0, x1, z1, 0, 1, fh, front, sides, sealed=sealed, infill=infill)
        up_rect = grow(rect, front, 1)
        up = _body(v, st, *up_rect, fh, floors - 1, fh, front, sides, ground='window', sealed=sealed, infill=infill, plinth=0)
        up.door_side = front
        timber_upper(v, st, up, plaster=rng.choice(PLASTERS))
        jetty_brackets(v, st, low, front)
        bodies = [low, up]
        top = up.top
        rr = up_rect
    elif kind == 'ell':
        # 本体は通りに平入り、袖 (奥へ伸びる棟) は一階低い
        main = _body(v, st, x0, z0, x1, z1, 0, floors, fh, front, sides, sealed=sealed, infill=infill)
        bodies = [main]
        top = main.top
        rr = rect
        wing = extras_wing(rect, front, rng)
        if wing:
            wf = max(1, floors - 1)
            wb = _body(v, st, *wing, 0, wf, fh, 'none', sides, sealed=True, infill=infill)
            wax = 'z' if front in ('north', 'south') else 'x'
            roof(v, st, *wing, wb.top + 1, axis=wax, kind=roof_kind if roof_kind != 'stepped' else 'steep', mat=mat)
            v.seal(wing[0] + 1, wing[1] + 1, wing[2] - 1, wing[3] - 1, 0)
            town.claim(wing[0] - 1, wing[1] - 1, wing[2] + 1, wing[3] + 1, 'wing')
    elif kind == 'tower':
        b = _body(v, st, x0, z0, x1, z1, 0, floors, fh, front, sides, sealed=sealed, infill=infill)
        bodies = [b]
        top = b.top
        rr = rect
    else:
        g = 'shop' if kind == 'merchant' else 'window'
        b = _body(v, st, x0, z0, x1, z1, 0, floors, fh if kind != 'cottage' else 5, front, sides, ground=g, sealed=sealed, infill=infill,
                  plinth=1 if kind == 'cottage' else 2, ornate=kind != 'cottage')
        if kind in ('gable', 'row', 'cottage') and floors >= 2 and rng.random() < 0.55:
            timber_upper(v, st, b, plaster=rng.choice(PLASTERS))
        bodies = [b]
        top = b.top
        rr = rect
    for (dx_, dy_, dz_, out) in (v.fake_doors[-1:] if sealed and v.fake_doors else []):
        if rng.random() < 0.6:
            door_canopy(v, st, dx_, dy_, dz_, out)
    if kind == 'tower':
        ridge = hip_spire(v, st, rr[0], rr[1], rr[2], rr[3], top + 1, mat=mat, rise=2, finial='lightning_rod', flare=True)
    else:
        dorm = 0
        span = (rr[2] - rr[0]) if ax == 'x' else (rr[3] - rr[1])
        if 'dormer' in extras and span >= 8 and roof_kind in ('steep', 'bell', 'kick', 'low'):
            dorm = 1 if span < 12 else 2
        ridge = roof(v, st, *rr, top + 1, axis=ax, kind=roof_kind, mat=mat, over=1, dormers=dorm, chimney=chimney,
                     finial=kind == 'merchant')
    if sealed:
        v.seal(rr[0] + 1, rr[1] + 1, rr[2] - 1, rr[3] - 1, 0)
    if quest:
        town.furnish_home(bodies[0])
    town.claim(rr[0] - 1, rr[1] - 1, rr[2] + 1, rr[3] + 1, ('quest@%d,%d' if quest else 'house@%d,%d') % (x0, z0))
    return bodies, ridge


def extras_wing(rect, front, rng):
    """L 字の袖: 本体の裏 (front の反対側) へ、片方の端から伸びる"""
    x0, z0, x1, z1 = rect
    back = OPP[front]
    w = 5 if rng.random() < 0.6 else 7
    d = rng.choice((5, 6, 7))
    left = rng.random() < 0.5
    if back in ('north', 'south'):
        if x1 - x0 < w + 3:
            return None
        xa = x0 if left else x1 - w + 1
        if back == 'south':
            return xa, z1 + 1, xa + w - 1, z1 + d
        return xa, z0 - d, xa + w - 1, z0 - 1
    if z1 - z0 < w + 3:
        return None
    za = z0 if left else z1 - w + 1
    if back == 'east':
        return x1 + 1, za, x1 + d, za + w - 1
    return x0 - d, za, x0 - 1, za + w - 1


PLASTERS = ['calcite', 'calcite', 'white_terracotta', 'smooth_quartz', 'mushroom_stem']


def _skin(st, rng):
    """壁の地: 土地の石 (半分)・板・漆喰・苔の石を混ぜる"""
    r = rng.random()
    if r < 0.45:
        return rng.choice(st.house_infills)
    if r < 0.7:
        return '%s_planks' % st.wood
    if r < 0.85:
        return 'calcite'
    return rng.choice(st.base2) if isinstance(st.base2, list) else st.base2


def _plain(st, mat, keep_edge=False):
    """妻の縁を屋根と同じ材にした作風の写し"""
    if keep_edge:
        return st
    from vox import Style
    d = dict(st.__dict__)
    d.pop('key', None)
    d['roof_edge'] = mat
    return Style(st.key, **d)
