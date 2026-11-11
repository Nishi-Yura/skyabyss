# -*- coding: utf-8 -*-
"""四つの心臓・心臓の番人・終章の主たちの技の動きの緩急を作り直す (2026-11-11 の声「予備動作 → 溜め → 一気に振る → 余韻。ここが一番効きます」)。

心臓と番人の技は、溜めと当たりが一本の動きになっていて、当たりの時刻が技の予告 (戦いの決まり) と合っている。
当たりの時刻は動かさず、その前後を作り直す (自動で強めただけの punch_anims.py の版は使わない。元は git の HEAD の版):
  当たり ti = 鍵と鍵のあいだで姿がいちばん大きく変わる所 (二番目・三番目に大きく、離れた当たりも)。その前の鍵 tw = 溜めの姿
  ・ 予備動作: 溜めへ向かう前に、溜めと逆へ少し (tp と tw のあいだ)
  ・ 溜め: tw から ti の直前まで、さらに深く溜め続ける (心臓ごとの癖: 震え・息・重さ)
  ・ 一気に振る: ti の直前の数 tick で当たりの姿へ (少し行き過ぎ)
  ・ 止め: 当たりの姿で一瞬止まる (ヒットストップ)
  ・ 余韻: 動きの向きのまま行き過ぎてから、もとの続きへ
心臓ごとの癖:
  石鯨の心臓  重い: 振りは遅めで、当たりで体が沈んで二度弾む
  潮の心臓    しなやか: 体の先ほど遅れて当たる (重なり)、余韻が大きい
  雷翼の心臓  電気: 溜めの間ずっと震え、振りがいちばん速い。余韻にびくっと引きつる
  燠の心臓    息: 溜めの間に大きく息を吸ってふくらみ、当たりで吐く
  心臓の番人  骨の騎士: 予備動作が大きく、振りは速く、止めが長い

    python -X utf8 -I tools/gen/anim/heart_moves.py
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
REL = "src/main/resources/assets/skyabyss/animations/entity"
SKIP = ("idle", "walk", "run", "intro", "death", "hurt", "flinch", "stagger", "roar_1", "roar_2", "phase", "split", "ebb", "reclose",
        "closed", "listen", "kneel", "frozen", "stunned", "perch", "walk_flared")

STYLES = {
    "stone_whale_heart": dict(antic=0.22, deep=0.12, snap=0.1, stop=0.1, over=0.1, follow=0.14, lag=0.0, bounce=True),
    "tide_heart": dict(antic=0.18, deep=0.1, snap=0.08, stop=0.05, over=0.12, follow=0.28, lag=0.035),
    "storm_heart": dict(antic=0.15, deep=0.14, snap=0.05, stop=0.06, over=0.14, follow=0.16, lag=0.01, tremble=2.5, twitch=True),
    "ember_heart": dict(antic=0.2, deep=0.12, snap=0.07, stop=0.08, over=0.1, follow=0.18, lag=0.02, breath=True),
    "heart_warden": dict(antic=0.32, deep=0.12, snap=0.05, stop=0.12, over=0.12, follow=0.18, lag=0.02),
    # 終章 (2026-11-11 の声「終章から順番に全部仕上げて」)
    "founder": dict(antic=0.26, deep=0.12, snap=0.05, stop=0.08, over=0.12, follow=0.2, lag=0.03),
    "last_bellkeeper": dict(antic=0.24, deep=0.12, snap=0.09, stop=0.12, over=0.1, follow=0.16, lag=0.02, bounce=True),
    "lamp_eye": dict(antic=0.18, deep=0.12, snap=0.05, stop=0.08, over=0.12, follow=0.16, lag=0.0, tremble=1.5),
    "rewinder": dict(antic=0.22, deep=0.1, snap=0.06, stop=0.14, over=0.1, follow=0.14, lag=0.0, twitch=True),
    "pilgrim_plume": dict(antic=0.2, deep=0.1, snap=0.07, stop=0.05, over=0.14, follow=0.28, lag=0.04),
    "pilgrim_hand": dict(antic=0.26, deep=0.14, snap=0.08, stop=0.12, over=0.1, follow=0.16, lag=0.03, bounce=True),
    "pilgrim_eye": dict(antic=0.18, deep=0.12, snap=0.05, stop=0.07, over=0.12, follow=0.18, lag=0.0, tremble=2.0),
    "pilgrim_moon": dict(antic=0.22, deep=0.1, snap=0.1, stop=0.12, over=0.1, follow=0.2, lag=0.0),
    # 月
    "moon_core": dict(antic=0.18, deep=0.12, snap=0.05, stop=0.08, over=0.12, follow=0.2, lag=0.03, tremble=1.2),
    "moon_scale": dict(antic=0.3, deep=0.14, snap=0.1, stop=0.14, over=0.1, follow=0.16, lag=0.03, bounce=True),
    "full_keeper": dict(antic=0.22, deep=0.12, snap=0.07, stop=0.1, over=0.12, follow=0.2, lag=0.03),
    "waning_blade": dict(antic=0.2, deep=0.12, snap=0.04, stop=0.06, over=0.14, follow=0.24, lag=0.035),
    "shadow_embracer": dict(antic=0.26, deep=0.12, snap=0.06, stop=0.06, over=0.12, follow=0.3, lag=0.05),
    # 章の道中
    "elder_sentry": dict(antic=0.24, deep=0.12, snap=0.05, stop=0.1, over=0.12, follow=0.16, lag=0.02, twitch=True),
    "shore_warden": dict(antic=0.22, deep=0.1, snap=0.07, stop=0.06, over=0.12, follow=0.26, lag=0.04),
    "whalebone_captain": dict(antic=0.28, deep=0.12, snap=0.08, stop=0.12, over=0.1, follow=0.16, lag=0.03, bounce=True),
    "elder_thrall": dict(antic=0.3, deep=0.12, snap=0.06, stop=0.08, over=0.12, follow=0.18, lag=0.02, tremble=1.5),
    "ash_smith": dict(antic=0.3, deep=0.14, snap=0.1, stop=0.14, over=0.1, follow=0.16, lag=0.03, bounce=True),
    "scorched_warden": dict(antic=0.2, deep=0.1, snap=0.04, stop=0.06, over=0.14, follow=0.2, lag=0.03),
    "tide_beckoner": dict(antic=0.26, deep=0.12, snap=0.09, stop=0.12, over=0.1, follow=0.16, lag=0.02, bounce=True),
    "raiju": dict(antic=0.2, deep=0.12, snap=0.04, stop=0.06, over=0.14, follow=0.22, lag=0.03, twitch=True),
}


def head_version(name):
    out = subprocess.run(["git", "show", f"HEAD:{REL}/{name}.animation.json"], cwd=ROOT, capture_output=True)
    return json.loads(out.stdout.decode("utf-8"))


def vec(v):
    if isinstance(v, list):
        return v
    if isinstance(v, dict):
        return v.get("vector", v.get("post"))
    return None


def numeric(v):
    return isinstance(v, list) and len(v) == 3 and all(isinstance(x, (int, float)) for x in v)


def chan_keys(kv):
    if not isinstance(kv, dict):
        return None
    ks = []
    for t, v in kv.items():
        vv = vec(v)
        if not numeric(vv):
            return None
        e = v.get("easing", "linear") if isinstance(v, dict) else "linear"
        ks.append((float(t), [float(x) for x in vv], e))
    return sorted(ks)


def at(ks, t):
    if t <= ks[0][0]:
        return list(ks[0][1])
    for (t0, v0, _), (t1, v1, _) in zip(ks[:-1], ks[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return list(ks[-1][1])


def lin(a, b, k):
    return [x + (y - x) * k for x, y in zip(a, b)]


def hits(chans, L):
    """当たりの時刻 [(tw, ti)] (大きい順に三つまで、0.4 秒以上離れたもの)。"""
    times = sorted({t for ks in chans for t, _, _ in ks})
    segs = []
    for t0, t1 in zip(times[:-1], times[1:]):
        d = 0.0
        for ks in chans:
            a, b = at(ks, t0), at(ks, t1)
            d += sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5
        segs.append((d, t0, t1))
    # 動きの最初からの変わり (振り上げ) と、最後の鍵への変わり (元へ戻る) は当たりではない
    L_end = times[-1]
    segs = [sg for sg in segs if sg[1] > 1e-6 and sg[2] < L_end - 1e-6]
    segs.sort(reverse=True)
    out = []
    for d, t0, t1 in segs:
        if not out:
            out.append((t0, t1, d))
        elif d > out[0][2] * 0.6 and all(abs(t1 - o[1]) > 0.4 for o in out):
            out.append((t0, t1, d))
        if len(out) == 3:
            break
    prev_of = {}
    for t0, t1, _ in out:
        earlier = [t for t in times if t < t0]
        prev_of[t1] = earlier[-1] if earlier else None
    return [(t0, t1, prev_of[t1]) for t0, t1, _ in sorted(out, key=lambda x: x[1])]


def depth_of(name):
    g = ROOT / f"src/main/resources/assets/skyabyss/geo/entity/{name}_lowpoly.geo.json"
    if not g.exists():
        g = ROOT / f"src/main/resources/assets/skyabyss/geo/entity/{name}_p1_lowpoly.geo.json"
    if not g.exists():
        # 変種は元の種の骨 (古い灰被り = 灰被り)
        g = ROOT / f"src/main/resources/assets/skyabyss/geo/entity/{ {'elder_thrall': 'ash_thrall'}.get(name, name)}_lowpoly.geo.json"
    geo = json.loads(g.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    par = {b["name"]: b.get("parent") for b in geo["bones"]}

    def d(n):
        k = 0
        while par.get(n) and par[n] != "root":
            n = par[n]
            k += 1
        return k
    return {n: d(n) for n in par}


def rework(name, anim, depth):
    st = STYLES[name]
    L = float(anim.get("animation_length", 0) or 0)
    chans = []
    for bone, b in anim.get("bones", {}).items():
        for ch in ("rotation", "position", "scale"):
            ks = chan_keys(b.get(ch))
            if ks and len(ks) >= 2:
                if ch == "scale":
                    # 大きさは 1 からのずれで扱い、書くときに戻す (月の核の脈打つ珠など)
                    ks = [(t, [x - 1.0 for x in v], e) for t, v, e in ks]
                chans.append((bone, ch, ks))
    if not chans or L <= 0:
        return 0
    hs = hits([ks for _, _, ks in chans], L)
    n = 0
    for bone, ch, ks in chans:
        lag = st["lag"] * max(0, depth.get(bone, 0) - 1)
        new = {t: (v, e) for t, v, e in ks}
        for tw, ti, tp in hs:
            if ti - tw < 0.05:
                continue
            coil, hit = at(ks, tw), at(ks, ti)
            dirv = [h - c for h, c in zip(hit, coil)]
            if sum(abs(x) for x in dirv) < 0.5:
                continue
            ti2 = min(L - 0.02, ti + lag)
            # 区間の古い鍵を消す (tw と当たりの余韻のあいだ)
            for t in [t for t in new if tw < t < min(L, ti2 + st["stop"] + st["follow"] + 0.02)]:
                del new[t]
            # 予備動作
            if tp is not None and tw - tp >= 0.3:
                pv = at(ks, tp)
                new[tp + (tw - tp) * 0.35] = ([p - (c - p) * st["antic"] for p, c in zip(pv, coil)], "easeInOutSine")
            deep = [c - d * st["deep"] for c, d in zip(coil, dirv)]
            snap = min(st["snap"], (ti2 - tw) * 0.45)
            if ti2 - snap - tw > 0.08:
                new[tw] = (coil, "easeOutCubic")
                if st.get("tremble") and ch == "rotation":
                    t, i = tw + 0.06, 0
                    while t < ti2 - snap - 0.03:
                        f = (t - tw) / max(1e-3, ti2 - snap - tw)
                        j = st["tremble"] * (1 if i % 2 else -1) * (0.3 + 0.7 * f)
                        new[t] = ([x + j for x in lin(coil, deep, f)], "linear")
                        t += 0.05
                        i += 1
                if st.get("breath") and ch == "position":
                    mid = tw + (ti2 - snap - tw) * 0.5
                    new[mid] = ([x for x in lin(coil, deep, 0.5)][0:1] + [lin(coil, deep, 0.5)[1] + 1.2] + [lin(coil, deep, 0.5)[2]], "easeInOutSine")
                new[ti2 - snap] = (deep, "easeInOutSine")
            hitx = [h + d * st["over"] for h, d in zip(hit, dirv)]
            new[ti2] = (hitx, "easeInQuad")
            t_stop = ti2 + st["stop"]
            if t_stop < L:
                new[t_stop] = (lin(hitx, hit, 0.3), "linear")
            if st.get("bounce") and ch == "position" and t_stop + 0.25 < L:
                new[t_stop + 0.1] = ([hit[0], hit[1] + 1.4, hit[2]], "easeOutQuad")
                new[t_stop + 0.2] = ([hit[0], hit[1] - 0.6, hit[2]], "easeInOutSine")
            tf = t_stop + st["follow"]
            if tf < L - 0.05:
                after = at(ks, tf)
                fv = [a + d * st["follow"] * 0.9 for a, d in zip(after, dirv)]
                if st.get("twitch") and ch == "rotation":
                    new[t_stop + 0.05] = ([x + 5 for x in lin(hit, fv, 0.4)], "linear")
                    new[t_stop + 0.09] = (lin(hit, fv, 0.5), "linear")
                new[tf] = (fv, "easeOutQuad")
            n += 1
        b = anim["bones"][bone]
        off = 1.0 if ch == "scale" else 0.0
        b[ch] = {f"{t:.4f}": {"vector": [round(x + off, 3) for x in v], "easing": e} for t, (v, e) in sorted(new.items())}
    return len(hs)


def main():
    log = {}
    for name in STYLES:
        data = head_version(name)
        depth = depth_of(name)
        done = []
        for key, anim in data["animations"].items():
            short = key.split(".")[-1]
            if short in SKIP or anim.get("loop") is True:
                continue
            k = rework(name, anim, depth)
            if k:
                done.append(f"{short}:{k}")
        head_txt = subprocess.run(["git", "show", f"HEAD:{REL}/{name}.animation.json"], cwd=ROOT, capture_output=True).stdout.decode("utf-8")
        line1 = head_txt.splitlines()[1]
        ind = len(line1) - len(line1.lstrip())
        (ROOT / REL / f"{name}.animation.json").write_text(json.dumps(data, ensure_ascii=False, indent=ind), encoding="utf-8")
        log[name] = done
        print(name, " ".join(done))
    # 自動の強め (punch_anims.py) はもうかけない
    pl = Path(__file__).resolve().parent / "punched.json"
    if pl.exists():
        p = json.loads(pl.read_text(encoding="utf-8"))
        for name in STYLES:
            p.pop(name, None)
        pl.write_text(json.dumps(p, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
