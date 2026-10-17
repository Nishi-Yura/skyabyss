"""モデルの「透けて中が見える」穴を直す (面の左右の取り違えの修正)。

原因: 生成スクリプト (install_stone_whale.py など) は、箱の面を「見える面だけ」書き出す (隣の箱に埋もれた面は書かない)。
その面の名前は「west = 箱の x の小さい側」と考えて付けてあるが、GeckoLib は west の面を「箱の x の大きい側」に置く
(BakedModelFactory が X を鏡に写して焼くため。east はその逆。north と south、up と down は一致する)。
そのため east / west の面が反対の側に付き、隣の箱が無い側の面が抜けて、横から見ると中が見える。
また、north / south / east / west の面は、画像の横方向 (u) も GeckoLib とは逆に張られていた。

直し方 (形はゲームの見た目のまま変えない):
  - east と west の面の名前を入れ替える
  - north / south / east / west の面の u を反転する (uv = [u + 幅, v]、uv_size = [-幅, 高さ]。GeckoLib は負の幅を受け付ける)
  - geo の description に "skyabyss_face_sides": "geckolib" を書く (二度は直さない)
up / down は向きも一致しているので触らない。箱ごとの uv (配列) の立方体は標準の並びなので触らない。

使い方 (リポジトリ直下で):
  python tools/fix_geo_face_sides.py --check            # 全 geo を検査する (穴の数。直さない)
  python tools/fix_geo_face_sides.py --apply            # 直せる geo を直す (印のあるものは飛ばす)
  python tools/fix_geo_face_sides.py --apply <名前>...   # 名前を指定して直す (geo/entity/<名前>.geo.json)
  python tools/fix_geo_face_sides.py --fill [<名前>...]  # 向きを直したあとも残る穴 (面が元から抜けている所) に、入口の面を足して塞ぐ
生成スクリプトで geo を書き出し直したら、そのあとに --apply --fill をもう一度実行する (書き出し直すと元の向きに戻るため)。
心臓 4 種 (*_heart_p1..3) は install_heart_v4.py などが GeckoLib の向きで書くので対象外 (--check の穴は 0)。

検査の方法: モデルを休め姿勢の箱として、あらゆる向きから光線を飛ばす。最初に当たった面が「裏から見た面」なら、
手前の面が抜けていて中が見えている。GeckoLib の面の位置の決まりで数える (骨の回転は無視する)。
"""
from __future__ import annotations

import glob
import json
import os
import sys

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEO = os.path.join(ROOT, "src/main/resources/assets/skyabyss/geo/entity")
MARK = "skyabyss_face_sides"

# キー -> (軸, 側)。側 +1 = 箱の大きい側
GECKO = {"west": (0, 1), "east": (0, -1), "north": (2, -1), "south": (2, 1), "up": (1, 1), "down": (1, -1)}
NAIVE = {"west": (0, -1), "east": (0, 1), "north": (2, -1), "south": (2, 1), "up": (1, 1), "down": (1, -1)}
SIDE_FACES = ("north", "south", "east", "west")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def holes(path, mapping, nrays=2000, seed=1):
    """(裏から見える光線の数, 光線の数, 骨ごとの数)"""
    g = load(path)["minecraft:geometry"][0]
    quads = []
    mn = np.full(3, 1e9)
    mx = np.full(3, -1e9)
    for b in g["bones"]:
        for c in b.get("cubes", []):
            o = np.array(c["origin"], float)
            s = np.array(c["size"], float)
            mn = np.minimum(mn, o)
            mx = np.maximum(mx, o + s)
            uv = c.get("uv")
            keys = list(uv.keys()) if isinstance(uv, dict) else list(mapping.keys())
            for k in keys:
                ax, side = mapping[k]
                o1, o2 = [i for i in range(3) if i != ax]
                plane = o[ax] + (s[ax] if side > 0 else 0)
                quads.append((ax, plane, o[o1], o[o1] + s[o1], o[o2], o[o2] + s[o2], side, b["name"]))
    if not quads:
        return 0, nrays, {}
    A = np.array([q[0] for q in quads])
    P = np.array([q[1] for q in quads], float)
    L1 = np.array([q[2] for q in quads], float)
    H1 = np.array([q[3] for q in quads], float)
    L2 = np.array([q[4] for q in quads], float)
    H2 = np.array([q[5] for q in quads], float)
    N = np.array([q[6] for q in quads], float)
    names = [q[7] for q in quads]
    rng = np.random.default_rng(seed)
    d = rng.normal(size=(nrays, 3))
    d /= np.linalg.norm(d, axis=1)[:, None]
    tgt = mn + (mx - mn) * rng.random((nrays, 3))
    org = tgt - d * np.linalg.norm(mx - mn) * 1.2
    bad = 0
    per_bone = {}
    for i in range(0, nrays, 200):
        od, dd = org[i:i + 200], d[i:i + 200]
        n = len(od)
        tmin = np.full(n, np.inf)
        nsgn = np.zeros(n)
        nax = np.zeros(n, int)
        who = np.zeros(n, int)
        for ax in range(3):
            m = A == ax
            if not m.any():
                continue
            o1, o2 = [j for j in range(3) if j != ax]
            idx = np.nonzero(m)[0]
            with np.errstate(divide="ignore", invalid="ignore"):
                t = (P[m][None, :] - od[:, ax][:, None]) / dd[:, ax][:, None]
            p1 = od[:, o1][:, None] + t * dd[:, o1][:, None]
            p2 = od[:, o2][:, None] + t * dd[:, o2][:, None]
            ok = (t > 1e-6) & (p1 >= L1[m][None] - 1e-9) & (p1 <= H1[m][None] + 1e-9) \
                & (p2 >= L2[m][None] - 1e-9) & (p2 <= H2[m][None] + 1e-9)
            t = np.where(ok, t, np.inf)
            j = t.argmin(1)
            tm = t[np.arange(n), j]
            better = tm < tmin
            tmin = np.where(better, tm, tmin)
            nsgn = np.where(better, N[m][j], nsgn)
            nax = np.where(better, ax, nax)
            who = np.where(better, idx[j], who)
        back = np.isfinite(tmin) & (nsgn * dd[np.arange(n), nax] > 0)
        bad += int(back.sum())
        for w in who[back]:
            per_bone[names[w]] = per_bone.get(names[w], 0) + 1
    return bad, nrays, per_bone


KEY_AT = {(ax, side): k for k, (ax, side) in GECKO.items()}


def fill_holes(path, rounds=4, nrays=6000):
    """向きを直したあとも残る穴 (最初に当たるのが裏の面) を、入口の面を足して塞ぐ。足した面の絵は同じ箱の別の面から借りる。
    足した面の数を返す (書き換えた geo の description に "skyabyss_holes_filled" として足し算で残す)"""
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    compact = raw.count("\n") < 3
    data = json.loads(raw)
    g = data["minecraft:geometry"][0]
    cubes = [c for b in g["bones"] for c in b.get("cubes", [])]
    boxes = [(np.array(c["origin"], float), np.array(c["size"], float)) for c in cubes]
    if not boxes:
        return 0
    mn = np.min([b[0] for b in boxes], axis=0)
    mx = np.max([b[0] + b[1] for b in boxes], axis=0)
    added = 0
    for rnd in range(rounds):
        quads = []
        for ci, c in enumerate(cubes):
            uv = c.get("uv")
            if not isinstance(uv, dict):
                continue
            o, s = boxes[ci]
            for k in uv:
                ax, side = GECKO[k]
                o1, o2 = [i for i in range(3) if i != ax]
                quads.append((ax, o[ax] + (s[ax] if side > 0 else 0), o[o1], o[o1] + s[o1], o[o2], o[o2] + s[o2], side, ci))
        if not quads:
            return added
        A = np.array([q[0] for q in quads])
        P = np.array([q[1] for q in quads], float)
        L1 = np.array([q[2] for q in quads], float)
        H1 = np.array([q[3] for q in quads], float)
        L2 = np.array([q[4] for q in quads], float)
        H2 = np.array([q[5] for q in quads], float)
        N = np.array([q[6] for q in quads], float)
        CI = np.array([q[7] for q in quads])
        rng = np.random.default_rng(100 + rnd)
        d = rng.normal(size=(nrays, 3))
        d /= np.linalg.norm(d, axis=1)[:, None]
        tgt = mn + (mx - mn) * rng.random((nrays, 3))
        org = tgt - d * np.linalg.norm(mx - mn) * 1.2
        todo = {}
        for i in range(0, nrays, 200):
            od, dd = org[i:i + 200], d[i:i + 200]
            n = len(od)
            tmin = np.full(n, np.inf)
            nsgn = np.zeros(n)
            nax = np.zeros(n, int)
            who = np.zeros(n, int)
            for ax in range(3):
                m = A == ax
                if not m.any():
                    continue
                o1, o2 = [j for j in range(3) if j != ax]
                idx = np.nonzero(m)[0]
                with np.errstate(divide="ignore", invalid="ignore"):
                    t = (P[m][None, :] - od[:, ax][:, None]) / dd[:, ax][:, None]
                p1 = od[:, o1][:, None] + t * dd[:, o1][:, None]
                p2 = od[:, o2][:, None] + t * dd[:, o2][:, None]
                ok = (t > 1e-6) & (p1 >= L1[m][None] - 1e-9) & (p1 <= H1[m][None] + 1e-9) \
                    & (p2 >= L2[m][None] - 1e-9) & (p2 <= H2[m][None] + 1e-9)
                t = np.where(ok, t, np.inf)
                j = t.argmin(1)
                tm = t[np.arange(n), j]
                better = tm < tmin
                tmin = np.where(better, tm, tmin)
                nsgn = np.where(better, N[m][j], nsgn)
                nax = np.where(better, ax, nax)
                who = np.where(better, idx[j], who)
            back = np.isfinite(tmin) & (nsgn * dd[np.arange(n), nax] > 0)
            for r in np.nonzero(back)[0]:
                ci = int(CI[who[r]])
                o, s = boxes[ci]
                with np.errstate(divide="ignore", invalid="ignore"):
                    t1 = (o - od[r]) / dd[r]
                    t2 = (o + s - od[r]) / dd[r]
                tn = np.minimum(t1, t2)
                ax = int(np.argmax(tn))
                side = -1 if dd[r][ax] > 0 else 1          # 光線が入ってくる側 (進む向きの手前の面)
                todo[(ci, KEY_AT[(ax, side)])] = True
        if not todo:
            break
        for (ci, key) in todo:
            uv = cubes[ci]["uv"]
            if key in uv:
                continue
            opposite = {"east": "west", "west": "east", "north": "south", "south": "north", "up": "down", "down": "up"}[key]
            src = uv.get(opposite) or next(iter(uv.values()), None)
            if src is None:
                continue
            uv[key] = {"uv": list(src["uv"]), "uv_size": list(src["uv_size"])}
            cubes[ci]["uv"] = {k: uv[k] for k in ("north", "east", "south", "west", "up", "down") if k in uv}
            added += 1
    if added:
        desc = g["description"]
        desc["skyabyss_holes_filled"] = int(desc.get("skyabyss_holes_filled", 0)) + added
        with open(path, "w", encoding="utf-8") as f:
            if compact:
                json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
                if raw.endswith("\n"):
                    f.write("\n")
            else:
                json.dump(data, f, ensure_ascii=False, indent=1)
                f.write("\n")
    return added


def open_faces(path, mapping):
    """書かれていない面のうち、すぐ外側が別の箱の中でないもの (外へ向かって開いている面) の数"""
    g = load(path)["minecraft:geometry"][0]
    cubes = [(np.array(c["origin"], float), np.array(c["size"], float), c.get("uv"))
             for b in g["bones"] for c in b.get("cubes", [])]
    if not cubes:
        return 0
    lo = np.array([c[0] for c in cubes])
    hi = np.array([c[0] + c[1] for c in cubes])
    probes = []
    for o, s, uv in cubes:
        if not isinstance(uv, dict):
            continue
        for k, (ax, side) in mapping.items():
            if k not in uv:
                p = o + s / 2
                p[ax] = (o[ax] + s[ax] if side > 0 else o[ax]) + side * 0.01
                probes.append(p)
    if not probes:
        return 0
    P = np.array(probes)
    inside = np.zeros(len(P), bool)
    for j in range(0, len(cubes), 400):
        m = np.all((P[:, None, :] > lo[j:j + 400][None] + 1e-6) & (P[:, None, :] < hi[j:j + 400][None] - 1e-6), axis=2)
        inside |= m.any(1)
    return int((~inside).sum())


def fix(path):
    with open(path, encoding="utf-8") as f:
        raw = f.read()
    compact = raw.count("\n") < 3        # 元が 1 行に詰めた書き方なら、そのまま詰めて書く (差分を小さく)
    data = json.loads(raw)
    g = data["minecraft:geometry"][0]
    if g["description"].get(MARK) == "geckolib":
        return False
    for b in g["bones"]:
        for c in b.get("cubes", []):
            uv = c.get("uv")
            if not isinstance(uv, dict):
                continue
            new = {}
            for k, f in uv.items():
                f = dict(f)
                if k in SIDE_FACES:
                    u, v = f["uv"]
                    w, h = f["uv_size"]
                    f["uv"] = [u + w, v]
                    f["uv_size"] = [-w, h]
                new[{"east": "west", "west": "east"}.get(k, k)] = f
            c["uv"] = {k: new[k] for k in ("north", "east", "south", "west", "up", "down") if k in new}
    g["description"][MARK] = "geckolib"
    with open(path, "w", encoding="utf-8") as f:
        if compact:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            if raw.endswith("\n"):
                f.write("\n")
        else:
            json.dump(data, f, ensure_ascii=False, indent=1)
            f.write("\n")
    return True


def main(argv):
    apply = "--apply" in argv
    check = "--check" in argv
    names = [a for a in argv if not a.startswith("--")]
    paths = [os.path.join(GEO, n + ".geo.json") for n in names] or sorted(glob.glob(os.path.join(GEO, "*.geo.json")))
    for p in paths:
        n = os.path.basename(p)[:-9]
        marked = load(p)["minecraft:geometry"][0]["description"].get(MARK) == "geckolib"
        gk = holes(p, GECKO)
        og, on = open_faces(p, GECKO), open_faces(p, NAIVE)
        # 書かれていない面が「west = x の小さい側」の読み方のほうで多く塞がっているもの (旧い向きで書かれたもの) を直す。
        # 心臓のように最初から GeckoLib の向きで書かれたものと、左右対称で見分けのつかないものは飛ばす
        needs = not marked and og > on * 1.1
        if check or not apply:
            top = sorted(gk[2].items(), key=lambda kv: -kv[1])[:3]
            print(f"{n:26s} 穴 {gk[0]:4d}/{gk[1]}  開いた面 {og:5d} (旧い向きなら {on:5d})  {'印あり' if marked else ('要修正' if needs else '')}  {top if gk[0] else ''}")
        if apply and (needs or names) and fix(p):
            print("直した:", n)
            gk = holes(p, GECKO)
        if "--fill" in argv and gk[0] > 0:
            added = fill_holes(p)
            print(f"穴を塞いだ: {n} 面を {added} 枚足した (残り {holes(p, GECKO, seed=7)[0]}/{gk[1]})")


if __name__ == "__main__":
    main(sys.argv[1:])
