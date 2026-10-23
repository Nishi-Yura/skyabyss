#!/usr/bin/env python3
"""灰の屑・灰の番人・古い灰の番人・灰の鍛え手の「外から裏面が見える」面を直す (docs/review/mob_quality_2026-10-23.md の 16・26・29)。

  python -I tools/once/fix_ash_see_through.py          # 直して書く
  python -I tools/once/fix_ash_see_through.py --dry    # 直したあとの透けの画素を数えるだけ

原因 (どれも mesh_close が開いた縁を塞いだ所で、面が自分自身を突き抜けている。開いた辺・向きの食い違いは 0 なので --check では落ちない):
  1. 外套の肩 (crust_body): 外套の上の輪 (y 27.8) が肩の板 (28.2 → 26.2 の円錐) より上へ出ていて、二つをつなぐ帯が肩の板の上に出て
     下向きの面を上から見せていた → 外套の上の輪を肩の板の縁のすぐ上まで下げて、帯を肩の板の下へしまう
  2. 炎の帯・上腕の先 (flare_body・arm_*): 根元を親へ沈める袖 (collar) や縁の蓋が、帯や腕の本体と同じ側へ折り返していて、
     折り返した側の本体が裏返しになっていた → 袖の先 (蓋の頂点) を、縁の輪の反対側 (本体と逆の向き) へ小さく出す
  3. 鍛え手の前腕 (forearm_*): 前腕の筒の下の輪 (y 31.0・半径 9) が籠手 (30.5〜33.9・半径 8.5) の外へはみ出し、
     籠手の上の縁とつなぐ帯が籠手の外に出ていた → 前腕の下の輪を籠手の上の縁の内側 (y 33.6・半径 8.1) へ移し、帯を籠手の上の面にする
頂点を動かすだけ (面・UV・骨は同じ)。生成スクリプトで書き出し直したら、もう一度流す。
"""
import json
import os
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path[:0] = [os.path.join(ROOT, "tools", "lib"), os.path.join(ROOT, "tools", "audit")]
import mesh_audit as ma  # noqa: E402
import mesh_close as mc  # noqa: E402

LP = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "lowpoly")
THRALLS = ("ash_husk", "ash_thrall", "elder_thrall")


def bad_tris(path):
    """骨ごとの、裏から見える三角形の番号 {骨: set(番号)}。"""
    bones = ma.load_mesh(path)
    detail = {}
    ma.see_through(bones, detail=detail)
    out, off = {}, 0
    for n, b in bones.items():
        if len(b.T) == 0:
            continue
        for ti in range(len(b.T)):
            if detail.get(off + ti):
                out.setdefault(n, set()).add(ti)
        off += len(b.T)
    return out


def same(V, p, tol=0.02):
    return np.where(np.linalg.norm(V - p, axis=1) < tol)[0]


def unfold_caps(V, T, bad, pivot):
    """2: 裏から見える三角形を含む塊ごとに、袖の先 (または蓋の頂点) を縁の反対側へ出す。動かした頂点の数を返す。"""
    lab = mc.components(V, T)
    moved = 0
    for L in sorted({int(lab[i]) for i in bad}):
        tt = T[lab == L]
        vi = np.unique(tt)
        P = V[vi]
        # 袖の先 = 親の pivot に近い頂点の塊、無ければ (縁の蓋) 塊の中で「蓋の中心」に最も近い輪
        d = np.linalg.norm(P - pivot, axis=1)
        end = vi[d < 1.6]
        if len(end) < 3:
            continue
        endpos = np.unique(np.round(V[end], 3), axis=0)
        endset = set(int(i) for i in vi if any(np.linalg.norm(V[i] - e) < 0.02 for e in endpos))
        # 縁の輪 = 先の頂点と同じ三角形にある、先でない頂点
        ring = set()
        for t in tt:
            if any(int(x) in endset for x in t):
                ring |= {int(x) for x in t if int(x) not in endset}
        ringpos = np.unique(np.round(V[sorted(ring)], 3), axis=0)
        ringset = set(int(i) for i in vi if any(np.linalg.norm(V[i] - r) < 0.02 for r in ringpos))
        body = [int(i) for i in vi if int(i) not in endset and int(i) not in ringset]
        if not body:
            continue
        rc = ringpos.mean(0)
        u = rc - V[body].mean(0)
        u /= np.linalg.norm(u) + 1e-9
        ec = endpos.mean(0)
        span = float(np.linalg.norm(ringpos - rc, axis=1).mean())
        for i in endset:
            V[i] = rc + u * max(0.25, 0.35 * span) + (V[i] - ec) * 0.2
            moved += 1
        # 折り返しを外すと、帯の本体が裏返しのまま (体積が負) と分かる。塊ごと面の向きを返す
        sel = np.where(lab == L)[0]
        P0, P1, P2 = V[T[sel, 0]], V[T[sel, 1]], V[T[sel, 2]]
        if np.einsum("ij,ij->i", P0, np.cross(P1, P2)).sum() < 0:
            T[sel] = T[sel][:, [0, 2, 1]]
    return moved


def lower_cloak_ring(V, T, bad):
    """1: 帯 (裏から見える三角形) の上の頂点の高さの輪を、肩の板の縁の高さのすぐ上へ下げる。"""
    ys = [V[t][:, 1] for t in T[sorted(bad)]]
    top = float(np.median([y.max() for y in ys]))
    rim = float(np.median([y.min() for y in ys]))
    if top - rim < 0.6:
        return 0
    zc = float(np.median(V[np.abs(V[:, 1] - top) < 0.08][:, 2]))
    sel = np.where((np.abs(V[:, 1] - top) < 0.08) & (np.hypot(V[:, 0], V[:, 2] - zc) < 5.2))[0]
    V[sel, 1] = rim + 0.3
    return len(sel)


def tuck_forearm(V, T, bad, side):
    """3: 籠手からはみ出す前腕の下の輪を、籠手の上の縁の内側へ。"""
    sel = np.where(np.abs(V[:, 1] - 31.0) < 0.02)[0]
    cx, cz = 27.0 * side, 5.0
    r = np.hypot(V[sel, 0] - cx, V[sel, 2] - cz)
    sel = sel[(r > 8.6) & (r < 9.4)]
    k = 8.1 / np.hypot(V[sel, 0] - cx, V[sel, 2] - cz)
    V[sel, 0] = cx + (V[sel, 0] - cx) * k
    V[sel, 2] = cz + (V[sel, 2] - cz) * k
    V[sel, 1] = 33.6
    return len(sel)


def fix(name, dry):
    path = os.path.join(LP, name + ".json")
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    bad = bad_tris(path)
    log = []
    for bn, tris in bad.items():
        b = d["bones"][bn]
        V = np.array(b["vertices"], float)
        T = np.array(b["triangles"], int)
        n = 0
        if bn == "crust_body":
            n = lower_cloak_ring(V, T, tris)
        elif bn.startswith("forearm_") and name == "ash_smith":
            n = tuck_forearm(V, T, tris, 1 if V[:, 0].mean() > 0 else -1)
        elif bn.startswith("flare_"):
            n = unfold_caps(V, T, tris, np.array(b["pivot"], float))
        elif bn.startswith("arm_"):
            n = unfold_arm_cap(V, T, tris)
        if n:
            b["vertices"] = [[round(float(x), 4) for x in v] for v in V]
            b["triangles"] = [[int(x) for x in t] for t in T]
            log.append("%s:%d" % (bn, n))
    if not dry:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(d, f, ensure_ascii=False, separators=(",", ":"))
    return log


def unfold_arm_cap(V, T, bad):
    """2 (上腕): 下の縁の蓋が腕の中へ折れて横へずれている。蓋の頂点 (縁の輪より上にある下端の頂点) を縁の輪の下へ出す。"""
    lab = mc.components(V, T)
    moved = 0
    for L in sorted({int(lab[i]) for i in bad}):
        vi = np.unique(T[lab == L])
        P = V[vi]
        ylo = P[:, 1].min()
        low = vi[P[:, 1] < ylo + 1.2]              # 下端の頂点 (縁の輪 + 蓋)
        if len(low) < 4:
            continue
        Pl = V[low]
        # 縁の輪 = 下端の頂点のうち、腕の本体の側の面 (上の頂点) とつながる頂点
        upper = set(int(i) for i in vi if V[i, 1] > ylo + 3.0)
        ring = set()
        for t in T[lab == L]:
            if any(int(x) in upper for x in t):
                ring |= {int(x) for x in t if int(x) not in upper}
        ringpos = np.unique(np.round(V[sorted(ring)], 3), axis=0)
        cap = [int(i) for i in low if not any(np.linalg.norm(V[i] - r) < 0.02 for r in ringpos)]
        if not cap:
            continue
        rc = ringpos.mean(0)
        u = rc - V[sorted(upper)].mean(0)
        u /= np.linalg.norm(u) + 1e-9
        cc = V[cap].mean(0)
        for i in cap:
            V[i] = rc + u * 0.35 + (V[i] - cc) * 0.6
            moved += 1
    return moved


def main():
    dry = "--dry" in sys.argv
    for name in THRALLS + ("ash_smith",):
        log = fix(name, dry)
        print(name, " ".join(log) or "-")
    if dry:
        return
    for name in THRALLS + ("ash_smith",):
        bones = ma.load_mesh(os.path.join(LP, name + ".json"))
        see = ma.see_through(bones)
        tot = see.pop("__total", 0)
        rows = {n: ma.analyze(b.V, b.T) for n, b in bones.items()}
        errs = sum(r["open"] + r["wind"] + r["inv"] for r in rows.values())
        print("  after %s: see %s / %d px, open+wind+inv %d" % (name, see, tot, errs))


if __name__ == "__main__":
    main()
