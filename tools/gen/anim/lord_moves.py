# -*- coding: utf-8 -*-
"""層の主 4 体の技の動き (windup_<技> と strike_<技>) の緩急を作り直す (2026-11-11 の声「予備動作 → 溜め → 一気に振る → 余韻」)。

主は骨組みが一体ずつ違う (骸骨・殻・六本脚・顎だけの体) ので、姿は今の動きから読む:
  W = 溜めの終わりの姿、C = 当たりの姿 (当たりの動きのうち W からいちばん離れた姿)、A = 予備動作 (W と逆へ少し)。
時の運びと癖は新しく (王は tools/gen/anim/king_moves.py。癖は王とも主どうしとも別):
  肋の主  骸骨: 溜めの終わりに顎がカタカタ鳴り、胸と頭が小刻みに震える。当たりは速く、余韻で骨がもう一度鳴る
  白殻の主 殻: 予備動作で殻へ深くこもり、溜めはじっと動かない。当たりは殻から一気に飛び出し、すぐ殻へ引っこむ
  鼓動まねの主 心臓: 溜めの間、心臓の打つ間がだんだん速くなり、脚がざわざわ動く。当たりで心臓が大きくふくらみ、余韻は大きい
  灯り喰いの主 顎: 溜めの間に二度大きく息を吸って体がふくらむ。当たりは重く、体が沈んで弾む
当たりは 1.2 秒 (BossLook.STRIKE_TICKS = 24)。溜め → 当たりは混ぜずに切り替える (GuardMob.bossState)。

    python -X utf8 -I tools/gen/anim/lord_moves.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ANIM = ROOT / "src/main/resources/assets/skyabyss/animations/entity"
CH = ("rotation", "position", "scale")

STYLES = {
    "lord_rib": dict(ta=0.3, tw=1.2, deep=1.08, snap=0.05, stop=0.1, follow=1.2, lag=0.03, chatter="jaw", tremble=("chest", "head"), rattle=True),
    "lord_salt": dict(ta=0.5, tw=1.0, deep=1.0, snap=0.04, stop=0.08, follow=1.08, lag=0.0, a_k=0.7, retract=True),
    "lord_storm": dict(ta=0.25, tw=1.1, deep=1.12, snap=0.06, stop=0.08, follow=1.3, lag=0.04, beat="heart", skitter=True),
    "lord_ash": dict(ta=0.45, tw=1.35, deep=1.08, snap=0.1, stop=0.12, follow=1.12, lag=0.02, breath=True, bounce=True),
}


def depth_of(geo):
    par = {b["name"]: b.get("parent") for b in geo["bones"]}

    def d(n):
        k = 0
        while par.get(n) and par[n] != "root":
            n = par[n]
            k += 1
        return k
    return {n: d(n) for n in par}


def keys_of(ch):
    """keyframes → [(t, vec)] (数だけの鍵。式は読まない)。"""
    if isinstance(ch, list):
        return [(0.0, ch)] if all(isinstance(x, (int, float)) for x in ch) else []
    out = []
    for t, v in ch.items():
        if isinstance(v, dict):
            v = v.get("vector", v.get("post"))
        if isinstance(v, list) and all(isinstance(x, (int, float)) for x in v):
            out.append((float(t), [float(x) for x in v]))
    return sorted(out)


def rel(vec, ch):
    return [x - 1.0 for x in vec] if ch == "scale" else list(vec)


def absv(vec, ch):
    return [round(x + 1.0, 3) for x in vec] if ch == "scale" else [round(x, 2) for x in vec]


def mix(a, b, t):
    return [x + (y - x) * t for x, y in zip(a, b)]


def scl(a, k):
    return [x * k for x in a]


def add(a, b):
    return [x + y for x, y in zip(a, b)]


def derive(wind, strike):
    """骨・ch ごとの W と C (相対値)。"""
    poses = {}
    bones = set(wind.get("bones", {})) | set(strike.get("bones", {}))
    for bn in bones:
        for ch in CH:
            wk = keys_of(wind.get("bones", {}).get(bn, {}).get(ch, {}))
            sk = keys_of(strike.get("bones", {}).get(bn, {}).get(ch, {}))
            W = rel(wk[-1][1], ch) if wk else [0.0, 0.0, 0.0]
            C = W
            best = -1.0
            for t, v in sk:
                if t <= 0.0 or t > 0.45:
                    continue
                r = rel(v, ch)
                dd = sum((x - y) ** 2 for x, y in zip(r, W))
                if dd > best:
                    best, C = dd, r
            if best < 0 and sk:
                C = rel(sk[min(1, len(sk) - 1)][1], ch)
            if any(abs(x) > 1e-3 for x in W + C):
                poses[(bn, ch)] = (W, C)
    return poses


def track(keys, L, ch):
    out = {}
    for t, v, e in keys:
        t = min(max(0.0, t), L)
        out[f"{t:.4f}"] = {"vector": absv(v, ch), "easing": e}
    return dict(sorted(out.items(), key=lambda kv: float(kv[0])))


def make(name, move, wind, strike, depth):
    st = STYLES[name]
    poses = derive(wind, strike)
    wb, sb = {}, {}
    Lw, Ls = 2.0, 1.2
    for (bn, ch), (W, C) in poses.items():
        lag = st["lag"] * depth.get(bn, 0)
        A = scl(W, -0.3 * st.get("a_k", 1.0))
        if st.get("retract"):
            # 殻へこもる: 予備動作は W の向きへ深く (逆ではなく、溜めの姿の先へ)
            A = scl(W, 1.0 + st["a_k"]) if any(W) else scl(C, -0.35)
        D = scl(W, st["deep"])
        if ch == "scale" and st.get("breath"):
            D = add(D, [0.06, 0.06, 0.06])
        # ---- 溜め
        k = [(0.0, [0, 0, 0], "easeInOutSine"), (st["ta"] + lag, A, "easeInOutSine")]
        k.append((st["tw"] + lag, W, "easeOutCubic"))
        if st.get("chatter") == bn and ch == "rotation":
            t, i = st["tw"] + 0.2, 0
            while t < Lw - 0.05:
                k.append((t, add(W, [-14 if i % 2 == 0 else 0, 0, 0]), "linear"))
                t += 0.07
                i += 1
        if bn in st.get("tremble", ()) and ch == "rotation":
            t, i = st["tw"] + 0.15, 0
            while t < Lw - 0.05:
                f = (t - st["tw"]) / (Lw - st["tw"])
                k.append((t, add(mix(W, D, f), [2.2 * (1 if i % 2 else -1), 0, 1.4 * (1 if i % 2 else -1)]), "linear"))
                t += 0.05
                i += 1
        if st.get("beat") == bn and ch == "scale":
            # 打つ間が速くなる鼓動
            for tb in (0.35, 0.8, 1.15, 1.42, 1.62, 1.78, 1.9):
                k.append((tb, add(W, [0.14, 0.14, 0.14]), "easeOutQuad"))
                k.append((tb + 0.07, mix([0, 0, 0], W, min(1.0, tb / st["tw"])), "easeInQuad"))
        if st.get("skitter") and bn.startswith("leg") and ch == "rotation":
            t, i = st["tw"] + 0.1, 0
            while t < Lw - 0.06:
                k.append((t, add(W, [0, 0, 5 * (1 if (i + int(bn[-1])) % 2 else -1)]), "linear"))
                t += 0.09
                i += 1
        if st.get("breath") and ch == "scale" and bn == "body":
            for i, tb in enumerate((0.55, 1.05)):
                k.append((tb, add(scl(W, 0.5), [0.1 + 0.04 * i] * 3), "easeInOutSine"))
                k.append((tb + 0.22, scl(W, 0.3), "easeInOutSine"))
        k.append((Lw, D, "easeInOutSine"))
        wb.setdefault(bn, {})[ch] = track(k, Lw, ch)
        # ---- 当たり
        snap = st["snap"] + lag
        Cx = scl(C, 1.12) if ch != "scale" else scl(C, 1.25)
        stop = snap + st["stop"]
        F = scl(C, st["follow"])
        k = [(0.0, D, "easeInOutSine"), (snap, Cx, "easeInQuad"), (stop, mix(Cx, C, 0.3), "linear")]
        if st.get("rattle") and ch == "rotation" and bn in ("jaw", "head", "chest"):
            for i in range(4):
                k.append((stop + 0.05 + i * 0.05, add(C, [(-10 if bn == "jaw" else 2.5) * (1 if i % 2 == 0 else -0.4), 0, 0]), "linear"))
        if st.get("bounce") and ch == "position":
            k.append((stop + 0.1, add(C, [0, 1.6, 0]), "easeOutQuad"))
            k.append((stop + 0.22, add(C, [0, -0.8, 0]), "easeInOutSine"))
            k.append((stop + 0.32, add(C, [0, 0.4, 0]), "easeInOutSine"))
        ft = max(stop + 0.25, 0.5 + lag)
        if st.get("retract"):
            # すぐ殻へ引っこむ: 余韻は短く、溜めの姿の方へ戻ってから元へ
            k.append((stop + 0.12, F, "easeOutQuad"))
            k.append((stop + 0.4, scl(W, 0.8), "easeInOutSine"))
            k.append((Ls - 0.2, scl(W, 0.2), "easeInOutSine"))
        else:
            k.append((ft, F, "easeOutQuad"))
            k.append((ft + 0.22, mix(F, C, 0.6), "easeInOutSine"))
            k.append((min(Ls - 0.12, ft + 0.44), scl(C, -0.08), "easeInOutSine"))
        k.append((Ls, [0, 0, 0], "easeInOutSine"))
        sb.setdefault(bn, {})[ch] = track(k, Ls, ch)
    return ({"loop": "hold_on_last_frame", "animation_length": Lw, "bones": wb}, {"animation_length": Ls, "bones": sb})


def main():
    src = ROOT / "tools/sources/lord_moves_base"
    src.mkdir(parents=True, exist_ok=True)
    for name in STYLES:
        p = ANIM / f"{name}.animation.json"
        base = src / p.name
        if not base.exists():
            base.write_text(p.read_text(encoding="utf-8"), encoding="utf-8")
        orig = json.loads(base.read_text(encoding="utf-8"))["animations"]
        data = json.loads(p.read_text(encoding="utf-8"))
        geo = json.loads((ROOT / f"src/main/resources/assets/skyabyss/geo/entity/{name}_lowpoly.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
        depth = depth_of(geo)
        moves = [k.split(".windup_")[1] for k in orig if ".windup_" in k]
        for mv in moves:
            w, s = make(name, mv, orig[f"animation.{name}.windup_{mv}"], orig[f"animation.{name}.strike_{mv}"], depth)
            data["animations"][f"animation.{name}.windup_{mv}"] = w
            data["animations"][f"animation.{name}.strike_{mv}"] = s
        p.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        print(name, moves)


if __name__ == "__main__":
    main()
