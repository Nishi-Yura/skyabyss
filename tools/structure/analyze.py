"""書き出した構造 (sbd.Dump) を調べる部品。ゲームを動かさずに、建築の抜けをデータで見つける。

- openings(d, seed): 起点の部屋から外へ抜ける口 (囲まれた空間の縁で、囲まれていない空間に接する所) を、かたまりごとに並べる
- walk(d, start): 歩いて行ける所 (自己テストと同じ決まり: 足と頭が空き、足元が固いか水。上り 1・下り 3、上るときは頭上 3 マス目も空き)
- water(d): 今ある水がこのまま流れたら、どの空気が水で埋まるか (静的な見積もり。多めに出る)
- signs(d): 看板の一覧

岩鯨・潮殻の背の自己テスト (TitanBackSelfTest / TideBackSelfTest) にある「背の穴・到達・水を流してからの空気」と同じことを、
どの構造にも使える形でここにまとめた。新しく背中の検査を書くときは、まずこれで見る。
"""
from __future__ import annotations

from collections import deque

import numpy as np
from scipy import ndimage

from sbd import Dump

N6 = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]


# ---------------------------------------------------------------- 囲まれた空間と、外への口
def enclosed(d: Dump) -> np.ndarray:
    """当たりのない所 (空気・水・草など) のうち、真上にも真下にも、いつか固いものがある所 (屋根と床のあいだ)。

    横の向きは見ない: 壁の穴から横へ光線を引くと、穴の筋の分だけ部屋の中まで「外」になって、口の場所がぼやけるため。
    壁の穴の外側は、ふつう真上が空なので「外」になり、口は穴そのもので見つかる。ひさしの下は「中」に数えられる
    """
    solid = d.collide
    above = np.flip(np.maximum.accumulate(np.flip(solid, axis=0), axis=0), axis=0)  # その高さ以上に固いもの
    below = np.maximum.accumulate(solid, axis=0)  # その高さ以下に固いもの
    hit = np.zeros_like(solid)
    hit[:-1] = above[1:]
    lo = np.zeros_like(solid)
    lo[1:] = below[:-1]
    return hit & lo & ~solid


def openings(d: Dump, seed, min_cells: int = 1, through_water: bool = False):
    """seed の空間 (囲まれた所だけをたどる) が、囲まれていない空間に接する口。

    返り値: {'room': 部屋の大きさ, 'openings': [{'cells', 'center', 'min', 'max'}...] (大きい順)}
    わざと開けた口 (階段の出口・門) も出るので、想定の口と見比べる。ガラスの窓は固いので口にならない。
    水は既定では壁とみなす (through_water=False)。海に浮かぶ島で、部屋が水の下を通って海とつながって見えないように
    """
    enc = enclosed(d)
    open_ = ~d.collide if through_water else ~d.collide & ~d.water
    enc = enc & open_
    labels, _ = ndimage.label(enc)
    iy, iz, ix = d.index(*seed)
    lab = labels[iy, iz, ix]
    if lab == 0:
        # 起点が台や柱の中なら、近く (3 マス以内) の囲まれた空き所から始める
        best = None
        for dy in range(-3, 4):
            for dz in range(-3, 4):
                for dx in range(-3, 4):
                    p = (iy + dy, iz + dz, ix + dx)
                    if all(0 <= p[k] < labels.shape[k] for k in range(3)) and labels[p]:
                        r = dy * dy + dz * dz + dx * dx
                        if best is None or r < best[0]:
                            best = (r, p)
        if best:
            iy, iz, ix = best[1]
            lab = labels[iy, iz, ix]
    if lab == 0:
        # 起点が囲まれていない (もう外) か、固いブロックの中
        return {"room": 0, "openings": [], "seed_open": bool(open_[iy, iz, ix])}
    room = labels == lab
    # 部屋の縁で、隣が「当たりがなく囲まれていない」所 (外) か、書き出しの範囲の外
    outside = open_ & ~enc
    touch = np.zeros_like(room)
    for axis in range(3):
        for step in (1, -1):
            shifted = np.roll(outside, step, axis=axis)
            edge = [slice(None)] * 3
            edge[axis] = 0 if step == 1 else -1
            shifted[tuple(edge)] = True  # 範囲の外は外とみなす
            touch |= shifted
    mouth = room & touch
    ml, mn = ndimage.label(mouth, structure=np.ones((3, 3, 3)))
    out = []
    for i, sl in enumerate(ndimage.find_objects(ml), start=1):
        cells = int((ml[sl] == i).sum())
        if cells < min_cells:
            continue
        lo = d.world(sl[0].start, sl[1].start, sl[2].start)
        hi = d.world(sl[0].stop - 1, sl[1].stop - 1, sl[2].stop - 1)
        pts = np.argwhere(ml[sl] == i)
        c = pts.mean(axis=0)
        center = d.world(int(round(c[0])) + sl[0].start, int(round(c[1])) + sl[1].start, int(round(c[2])) + sl[2].start)
        out.append({"cells": cells, "center": center, "min": lo, "max": hi})
    out.sort(key=lambda o: -o["cells"])
    return {"room": int(room.sum()), "openings": out, "seed_open": True}


def rooms(d: Dump, min_cells: int = 8):
    """囲まれた空間のかたまり (部屋) の一覧。大きい順"""
    labels, _ = ndimage.label(enclosed(d))
    out = []
    for i, sl in enumerate(ndimage.find_objects(labels), start=1):
        if sl is None:
            continue
        cells = int((labels[sl] == i).sum())
        if cells >= min_cells:
            out.append({"cells": cells, "min": d.world(sl[0].start, sl[1].start, sl[2].start),
                        "max": d.world(sl[0].stop - 1, sl[1].stop - 1, sl[2].stop - 1)})
    out.sort(key=lambda o: -o["cells"])
    return out


# ---------------------------------------------------------------- 歩いて行ける所
def walk(d: Dump, start, up: int = 1, down: int = 3, limit: int = 2_000_000):
    """start (足の位置) から歩いて行ける足の位置と歩数。{(x,y,z): 歩数} と {子: 親}。

    決まりは TideBackSelfTest.walk と同じ: 足と頭が当たりなし、足元が当たりありか足が水。
    横へ 1 歩: 上り 1 (頭上 3 マス目も空き)、下り 3 まで (落ちる筋が空き)。水の中なら 1 つ上へも行ける
    """
    sy, sz, sx = d.shape
    passable = ~d.collide
    water = d.water

    def ok(iy, iz, ix):
        return 0 <= iy < sy - 1 and 0 < iy and 0 <= iz < sz and 0 <= ix < sx

    def stand(iy, iz, ix):
        return ok(iy, iz, ix) and passable[iy, iz, ix] and passable[iy + 1, iz, ix] and (not passable[iy - 1, iz, ix] or water[iy, iz, ix])

    s = d.index(*start)
    if not stand(*s):
        # 起点が浮いていれば、下へ落とす
        iy, iz, ix = s
        while iy > 1 and not stand(iy, iz, ix):
            iy -= 1
        s = (iy, iz, ix)
    dist = {s: 0}
    parent = {}
    q = deque([s])
    while q and len(dist) < limit:
        p = q.popleft()
        iy, iz, ix = p
        dd = dist[p] + 1
        nxt = []
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nz, nx = iz + dz, ix + dx
            if not (0 <= nz < sz and 0 <= nx < sx):
                continue
            for dy in range(up, -down - 1, -1):
                m = (iy + dy, nz, nx)
                if not ok(*m):
                    continue
                if dy > 0 and not (ok(iy + 2, iz, ix) and passable[iy + 2, iz, ix]):
                    continue
                if dy < 0 and not all(ok(iy + k, nz, nx) and passable[iy + k, nz, nx] for k in range(dy, 2)):
                    continue
                if stand(*m):
                    nxt.append(m)
                    break
        if water[iy, iz, ix] and stand(iy + 1, iz, ix):
            nxt.append((iy + 1, iz, ix))
        for m in nxt:
            if m not in dist:
                dist[m] = dd
                parent[m] = p
                q.append(m)
    world = {d.world(*k): v for k, v in dist.items()}
    wparent = {d.world(*k): d.world(*v) for k, v in parent.items()}
    return world, wparent


def path_to(parent, goal):
    """walk の親の表から、起点から goal までの道 (高さが変わる所だけ)"""
    steps = []
    cur = goal
    while cur is not None and len(steps) < 2000:
        steps.append(cur)
        cur = parent.get(cur)
    steps.reverse()
    out, prev = [], None
    for p in steps:
        if p[1] != prev:
            out.append(p)
            prev = p[1]
    return out


def nearest_reached(dist, target, r: int = 2):
    """target から水平 r・高さ 1 以内で、いちばん近くに歩いて着いた所 (着かなければ None)"""
    tx, ty, tz = target
    best = None
    for (x, y, z), v in dist.items():
        if abs(x - tx) <= r and abs(z - tz) <= r and abs(y - ty) <= 1:
            if best is None or v < best[1]:
                best = ((x, y, z), v)
    return best


# ---------------------------------------------------------------- 水の広がり (静的な見積もり)
def water(d: Dump, max_rounds: int = 4):
    """今の水がこのまま流れたら、どこまで広がるか。

    決まり (バニラの FlowingFluid に寄せた): 下が空いていれば下へ落ちる (落ちた水は横へ 7 マス広がる元になる)。
    源でない水は、下が空き (または水) なら横へは広がらない。横へは 1 マスごとに 1 段弱まり、7 マスまで。
    横に源が 2 つ以上あり、下が固いか源の空き所は、新しい源になる (無限の水)。
    バニラは「近くの落ち口へ向かう方へだけ流れる」ので、実際より多めに出る (埋まるかもしれない所を見る用)

    返り値: dict(filled=水になる空き所の bool 配列 (今は水でない所だけ), level=到達した弱まりの段 (源 0))
    """
    sy, sz, sx = d.shape
    fillable = ~d.collide & ~d.water & ~d.lava
    level = np.full(d.shape, 99, dtype=np.int8)
    is_src = d.water & d.source
    level[d.water] = 1
    level[is_src] = 0
    falling = np.zeros(d.shape, dtype=bool)
    for _ in range(max_rounds):
        # 動き出す所: 水で、隣 (下か横) に入れる所があるもの
        wet = level < 99
        cand = np.zeros_like(wet)
        for axis, step in ((0, 1), (1, 1), (1, -1), (2, 1), (2, -1)):
            nb = np.roll(fillable, step, axis=axis)
            cand |= nb
        frontier = [tuple(p) for p in np.argwhere(wet & cand)]
        q = deque(frontier)
        while q:
            iy, iz, ix = q.popleft()
            lv = int(level[iy, iz, ix])
            src = bool(is_src[iy, iz, ix])
            below_open = iy > 0 and (fillable[iy - 1, iz, ix] or level[iy - 1, iz, ix] < 99)
            if iy > 0 and fillable[iy - 1, iz, ix] and level[iy - 1, iz, ix] > 0:
                level[iy - 1, iz, ix] = 0
                falling[iy - 1, iz, ix] = True
                q.append((iy - 1, iz, ix))
            if below_open and not src:
                continue
            nl = (1 if falling[iy, iz, ix] else lv + 1)
            if nl > 7:
                continue
            for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nz, nx = iz + dz, ix + dx
                if 0 <= nz < sz and 0 <= nx < sx and fillable[iy, nz, nx] and level[iy, nz, nx] > nl:
                    level[iy, nz, nx] = nl
                    falling[iy, nz, nx] = False
                    q.append((iy, nz, nx))
        # 新しい源: 横の源が 2 つ以上で、下が固いか源
        srcmask = (level == 0) & ~falling
        count = np.zeros(d.shape, dtype=np.int8)
        for axis, step in ((1, 1), (1, -1), (2, 1), (2, -1)):
            count += np.roll(srcmask, step, axis=axis).astype(np.int8)
        below_solid = np.roll(d.collide | srcmask, 1, axis=0)
        new = fillable & (level > 0) & (level < 99) & (count >= 2) & below_solid
        if not new.any():
            break
        level[new] = 0
        falling[new] = False
        is_src = is_src | new
    filled = fillable & (level < 99)
    return {"filled": filled, "level": level}


def clusters(mask: np.ndarray, d: Dump, top: int = 20):
    """bool 配列のかたまり (26 近傍) を大きい順に"""
    lab, _ = ndimage.label(mask, structure=np.ones((3, 3, 3)))
    out = []
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        if sl is None:
            continue
        cells = int((lab[sl] == i).sum())
        f = np.argwhere(lab[sl] == i)[0]
        out.append({"cells": cells, "min": d.world(sl[0].start, sl[1].start, sl[2].start),
                    "max": d.world(sl[0].stop - 1, sl[1].stop - 1, sl[2].stop - 1),
                    "first": d.world(f[0] + sl[0].start, f[1] + sl[1].start, f[2] + sl[2].start)})
    out.sort(key=lambda o: -o["cells"])
    return out[:top]


def waterlogged_leaks(d: Dump):
    """水に浸かった形 (waterlogged=true のサンゴ・階段・はしごなど) で、横か下が空いているもの。
    水の源と同じで、隣のブロックが更新されたとき (壊す・置く) に水が流れ出す。建てた直後は止まって見えるので見落としやすい"""
    logged = d.water & ~np.array([n == "minecraft:water" for n in d.names], dtype=bool)[d.ids]
    fillable = ~d.collide & ~d.water & ~d.lava
    near = np.zeros_like(fillable)
    near[1:] |= fillable[:-1]
    near[:, 1:] |= fillable[:, :-1]
    near[:, :-1] |= fillable[:, 1:]
    near[:, :, 1:] |= fillable[:, :, :-1]
    near[:, :, :-1] |= fillable[:, :, 1:]
    return logged & near


def flowing_now(d: Dump):
    """今すでに流れている水 (源でない水)。流れが止まったあとの書き出しなら、ここが水の漏れた所"""
    return d.water & ~d.source


# ---------------------------------------------------------------- 看板
def signs(d: Dump):
    out = []
    for s in d.header.get("signs", []):
        lines = [l.get("key") or l.get("text", "") for l in s.get("front", [])]
        back = [l.get("key") or l.get("text", "") for l in s.get("back", [])]
        out.append({"pos": tuple(s["pos"]), "block": s.get("block", ""), "front": lines, "back": back})
    return out
