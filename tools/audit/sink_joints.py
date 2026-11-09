"""関節の付け根を親へ沈める (issue #16。決まり「水密なモデル: 部品は親に沈めて継ぎ目から中が見えない」)。

メッシュの監査 (mesh_audit.joint_check) で detach / shallow の子の骨について、付け根の頂点 (支点に近い側の 3 割) を
骨の向きと逆へ (支点の方へ) 少しずつ動かし、休めの姿勢でもアニメの最大の回転でも親に埋まるまで沈める。
動かすのは付け根の頂点だけ (UV はそのまま。袖や脚の付け根が胴の中へ伸びる)。球を足すことはしない (関節の玉は使わない決まり)。
沈められた骨はメッシュの "skyabyss_sunk" に名前を書く (二度目は動かさない。生成器で作り直したら名前が消えるので、また沈める)。

    python -I tools/audit/sink_joints.py                    # 監査で NG の全部 (浮いて回る飾り FLOATERS は除く)
    python -I tools/audit/sink_joints.py ash_lizard moon_leaper   # メッシュの名前で絞る
    python -I tools/audit/sink_joints.py --dry               # 書き換えずに結果だけ
"""
from __future__ import annotations

import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_audit as ma  # noqa: E402

# 浮いて回る飾り (宙の粒・輪・籠の中の目・頭の上の印・杭の冠)。親から離れて動くのが形なので沈めない (mesh_audit の FLOATERS と同じ)
FLOATERS = ma.FLOATERS
STEP = 0.4
MAX_FRAC = 0.45   # 部品の長さのこの割合までしか沈めない (それ以上は形が崩れる)
ROOT_FRAC = 0.3


def limb_axis(b):
    c = b.V.mean(0)
    a = c - b.pivot
    if np.linalg.norm(a) < 1e-6:
        far = b.V[np.argmax(np.linalg.norm(b.V - b.pivot, axis=1))]
        a = far - b.pivot
    return a / max(1e-9, np.linalg.norm(a))


def status(bones, mesh_name, bone):
    res = ma.joint_check({k: v for k, v in bones.items()}, mesh_name)
    return res.get(bone, {}).get("status", "ok")


def sink(bones, mesh_name, bone):
    b = bones[bone]
    a = limb_axis(b)
    t = (b.V - b.pivot) @ a
    t0, t1 = float(t.min()), float(t.max())
    length = max(1e-6, t1 - t0)
    root = t <= t0 + ROOT_FRAC * length
    orig = b.V.copy()
    s = 0.0
    while s < MAX_FRAC * length:
        s += STEP
        V = orig.copy()
        # 付け根ほど大きく動かす (付け根の端で s、3 割の所で 0)
        w = np.clip(1 - (t[root] - t0) / (ROOT_FRAC * length), 0, 1)
        V[root] -= np.outer(w, a) * s
        b.V = V
        st = status(bones, mesh_name, bone)
        if st == "ok":
            return s, st
    b.V = orig
    return 0.0, status(bones, mesh_name, bone)


def main(argv):
    dry = "--dry" in argv
    flt = [x for x in argv if not x.startswith("--")]
    done = []
    for path in ma.mesh_files():
        name = os.path.splitext(os.path.basename(path))[0]
        p = path.replace(os.sep, "/")
        if "/items/" in p or "/armor/" in p or (flt and name not in flt):
            continue
        bones = ma.load_mesh(path)
        if not bones:
            continue
        res = ma.joint_check(bones, name)
        bad = [n for n, r in res.items() if r["status"] in ("detach", "shallow") and (name, n) not in FLOATERS]
        if not bad:
            continue
        d = ma.load_json(path)
        sunk = set(d.get("skyabyss_sunk", []))
        changed = False
        for n in bad:
            if n in sunk:
                continue
            s, st = sink(bones, name, n)
            print(f"{name}/{n}: {res[n]['status']} -> {st} (sunk {s:.1f})")
            if s > 0:
                d["bones"][n]["vertices"] = [[round(float(x), 4) for x in v] for v in bones[n].V]
                sunk.add(n)
                changed = True
                done.append((name, n, s, st))
        if changed and not dry:
            d["skyabyss_sunk"] = sorted(sunk)
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                json.dump(d, f, ensure_ascii=False, separators=(",", ":"))
    print(len(done), "joints sunk")


if __name__ == "__main__":
    main(sys.argv[1:])
