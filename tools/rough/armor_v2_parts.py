"""Parts catalogue of the armor design base (docs/design/16_armor_design.md): a layered-plate (lamellae) frame
that every titan set is built on, plus the hooks where the titan's own motif is added.

Roles: M = {"main", "alt", "trim", "void", "glow"} -> material names of the set.
All functions write into an armor_kit.Armor in vanilla humanoid coordinates; right side first, left is mirrored
unless the set wants an asymmetric element (asym_*).

Contact rules baked in (see 16, section 6):
  * head armour keeps |x|,|z| <= 4.9 up to y 29.6 and covers the whole head box (|x|,|z| < 4.6, up to y 32.6)
  * body armour |x| <= 4.6 (touches the arm shell, never enters it); nothing wider than 4.6 on the body bone below y 22
    (arms hang beside it), wide flare is carried by the leg bones (y < 11.4, under the arm cuffs)
  * back protrusions stay at y >= 15 and out of the arm's swing (arm swung back 30 deg reaches z = 5 near y 13)
  * hanging cloth belongs to the leg bone (split), never to the body bone
"""
from __future__ import annotations

H, B = "armorHead", "armorBody"
RA, LA, RL, LL, RB, LB = "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot"


def frame(a, bone, lo, hi, face, mat, *, inset=0.9, w=0.8, h=0.4, corner=1.3, ch=0.7):
    """Raised frame (4 strips + 4 corner studs) on one face of an axis-aligned plate: relief that catches the game's
    face shading.  Pieces are disjoint and sit outside the plate (they touch it, never enter it)."""
    ax, sg = {"north": (2, -1), "south": (2, 1), "east": (0, 1), "west": (0, -1), "up": (1, 1)}[face]
    u, v = [i for i in range(3) if i != ax]
    lo, hi = list(lo), list(hi)
    plane = hi[ax] if sg > 0 else lo[ax]
    u0, u1, v0, v1 = lo[u] + inset, hi[u] - inset, lo[v] + inset, hi[v] - inset

    def mk(a0, a1, b0, b1, hh):
        l, hgh = [0, 0, 0], [0, 0, 0]
        l[u], hgh[u], l[v], hgh[v] = a0, a1, b0, b1
        l[ax], hgh[ax] = (plane, plane + hh) if sg > 0 else (plane - hh, plane)
        a.box(bone, tuple(l), tuple(hgh), mat)

    for cu in (u0, u1 - corner):
        for cv in (v0, v1 - corner):
            mk(cu, cu + corner, cv, cv + corner, ch)
    mk(u0 + corner, u1 - corner, v0, v0 + w, h)
    mk(u0 + corner, u1 - corner, v1 - w, v1, h)
    mk(u0, u0 + w, v0 + corner, v1 - corner, h)
    mk(u1 - w, u1, v0 + corner, v1 - corner, h)


def kabuto(a, M, *, neck=2, cheek=True, visor=True, band_h=1.0, tier_split=28.6):
    """Helmet: two-tier dome, trim band, visor slit, cheek wings, flaring neck lames (back)."""
    a.box(H, (-4.6, 25.2, -4.6), (4.6, tier_split, 4.6), M["main"])
    a.box(H, (-4.9, tier_split, -4.9), (4.9, tier_split + band_h, 4.9), M["trim"])
    a.box(H, (-4.5, tier_split + band_h, -4.5), (4.5, 32.6, 4.5), M["alt"])
    if visor:
        a.box(H, (-3.4, 25.8, -5.0), (3.4, 27.6, -4.6), M["void"], hide=("down",))
        a.box(H, (-2.8, 26.5, -5.15), (2.8, 26.9, -5.0), M["glow"])
    if cheek:
        for sx in (-1, 1):
            a.slab(H, (sx * 5.2, 27.0, -0.4), (0.8, 2.6, 6.0), M["alt"], rot=(0, 0, sx * -10))
    for i in range(neck):
        a.slab(H, (0, 26.4 - 1.6 * i, 5.4 + 0.8 * i), (9.0 + 1.2 * i, 1.7, 0.9), M["alt"], rot=(-14 - 8 * i, 0, 0))


def cuirass(a, M, *, ridge=True, spine=True, belt_gem=True, framed=True, front=True, chest=(-4.6, 17.4, -3.4, 4.6, 23.4, 3.0)):
    x0, y0, z0, x1, y1, z1 = chest
    a.box(B, (x0, y0, z0), (x1, y1, z1), M["main"])
    if ridge:
        a.box(B, (-1.0, 18.0, z0 - 0.6), (1.0, 22.4, z0), M["glow"])
        a.box(B, (-3.6, 18.0, z0 - 0.4), (-1.0, 22.6, z0), M["trim"])
        a.box(B, (1.0, 18.0, z0 - 0.4), (3.6, 22.6, z0), M["trim"])
    elif framed and front:
        frame(a, B, (x0, y0, z0), (x1, y1, z1), "north", M["trim"])
    if framed:
        frame(a, B, (x0, y0, z0), (x1, y1, z1), "south", M["trim"])
    a.box(B, (-3.4, 23.4, -2.6), (3.4, 25.0, 2.6), M["trim"])      # collar
    a.box(B, (-4.4, 14.6, -3.0), (4.4, y0, 2.8), M["alt"])          # abdomen
    a.box(B, (-4.6, 12.4, -3.3), (4.6, 14.6, 3.1), M["trim"])       # belt
    if belt_gem:
        a.box(B, (-1.2, 12.9, -3.7), (1.2, 14.1, -3.3), M["glow"])
    if spine:
        for i, y in enumerate((22.0, 19.6, 17.2)):
            a.slab(B, (0, y, 4.8 + 0.5 * i), (1.0, 2.4, 3.0 + 0.3 * i), M["trim"], rot=(-16, 0, 0))


def arm_shell(a, M, bone=RA, sign=-1, *, top=22.0, cuff=True, framed=True):
    """x range -8.4..-4.6 (right).  Mirrored later by the caller when symmetric."""
    s = -sign  # sign -1 = right arm (x < 0), +1 = left arm
    a.box(bone, (min(s * -8.4, s * -4.6), 13.2, -2.5), (max(s * -8.4, s * -4.6), top, 2.5), M["main"])
    if cuff:
        a.box(bone, (min(s * -9.2, s * -4.6), 11.4, -3.3), (max(s * -9.2, s * -4.6), 13.2, 3.3), M["trim"])
    a.box(bone, (min(s * -6.0, s * -4.6), top, -2.4), (max(s * -6.0, s * -4.6), top + 1.0, 2.4), M["alt"])   # shoulder cap
    if framed:
        lo = (min(s * -8.4, s * -4.6), 13.2, -2.5)
        hi = (max(s * -8.4, s * -4.6), top, 2.5)
        frame(a, bone, lo, hi, "west" if sign < 0 else "east", M["trim"], inset=0.8)


def sode(a, M, bone=RA, *, layers=3, origin=(-6.9, 23.0, 0), tilt=-8, depth=3.6, length=6.4, rim=True, mats=None):
    """Layered pauldron on a tilted frame: each lame is smaller and sits higher; outer rim strips in trim."""
    g = a.group(bone, origin, (0, 0, tilt))
    y = 0.0
    th = [2.2, 2.0, 1.6, 1.3]
    mats = mats or [M["main"], M["alt"], M["main"], M["alt"]]
    for i in range(layers):
        L = length - 1.0 * i - 0.6 * (i > 1)
        d = depth - 0.5 * i
        h = th[i]
        g.box((-(L - 0.6), y, -d), (0.6 - 0.8 * i * 0.5, y + h, d), mats[i])
        if rim:
            g.box((-(L - 0.6) - 0.4, y + 0.2, -d + 0.4), (-(L - 0.6), y + h - 0.2, d - 0.4), M["trim"])
        y += h
    return g


def leg_set(a, M, *, tassets=3, tass_mat=None, knee=True):
    a.box(RL, (-4.4, 4.6, -2.4), (-0.1, 10.6, 2.4), M["main"])
    a.box(RL, (-4.5, 10.6, -2.7), (-0.1, 12.0, 2.7), M["trim"])
    frame(a, RL, (-4.4, 4.6, -2.4), (-0.1, 10.6, 2.4), "south", M["trim"], inset=0.7, corner=1.0, ch=0.5)
    a.box(RL, (-3.4, 10.7, -3.1), (-1.2, 11.9, -2.7), M["glow"])        # hip gem (every piece carries a little glow)
    for i in range(tassets):
        mm = (tass_mat or M["alt"]) if i != 1 else M["trim"]
        a.slab(RL, (-2.3 - 0.2 * i, 9.6 - 2.1 * i, -3.2 - 0.5 * i), (4.2 + 0.4 * i, 2.0, 0.9), mm, rot=(14 + 5 * i, 0, 0))
    if knee:
        a.slab(RL, (-5.4, 8.0, 0), (1.0, 4.0, 5.2), M["alt"], rot=(0, 0, -12))
    a.mirror_bone(RL, LL)


def boots(a, M, *, toe=True):
    a.box(RB, (-4.4, 0.0, -2.4), (-0.1, 3.6, 2.4), M["main"])
    a.box(RB, (-4.7, 3.6, -2.7), (-0.1, 4.6, 2.7), M["trim"])      # folded cuff
    a.box(RB, (-3.2, 3.7, -3.1), (-1.3, 4.5, -2.7), M["glow"])      # ankle gem
    a.box(RB, (-4.3, 0.0, -4.4), (-0.2, 1.8, -2.4), M["alt"])
    a.slab(RB, (-2.3, 2.8, -3.0), (3.2, 2.2, 0.8), M["trim"], rot=(8, 0, 0))
    if toe:
        a.slab(RB, (-2.3, 2.4, -5.0), (3.6, 0.8, 2.6), M["trim"], rot=(-22, 0, 0))
    a.mirror_bone(RB, LB)
