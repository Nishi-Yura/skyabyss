#!/usr/bin/env python3
"""低ポリの模型の「絵ではあるのにゲームでは透明」「絵では光るのにゲームでは光らない」を見つける (2026-11-11 の声)。

ゲームは絵の透明度 0.1 未満の画素を描かない (切り抜き)。ラフの絵は透明度を見ずに色だけで描いていたので、ずれに気づかなかった。
  hole   三角形の uv の中ほどの画素が透明 → ゲームでは穴
  edge   三角形の頂点の画素が透明 (端だけ欠ける)
  glow   発光マスクの大きさが絵と違う (光らない) / マスクに光る画素が無い (落ちる)
  name   lowpoly の "texture" の絵が無い

    python -I tools/audit/lowpoly_texture_audit.py [名前の頭 ...]
出力: docs/review/lowpoly_texture_audit.txt (悪い順)
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/skyabyss"
CUT = 26  # 0.1 * 255


def sample(alpha, u, v):
    h, w = alpha.shape
    x = np.clip((u * w).astype(int), 0, w - 1)
    y = np.clip((v * h).astype(int), 0, h - 1)
    return alpha[y, x]


def audit(path):
    d = json.loads(path.read_text(encoding="utf-8"))
    tex = d.get("texture", path.stem)
    tp = A / f"textures/lowpoly/{tex}.png"
    out = dict(name=path.stem, hole=0, edge=0, tris=0, notes=[])
    if not tp.exists():
        out["notes"].append(f"texture missing: {tex}")
        return out
    img = np.array(Image.open(tp).convert("RGBA"))
    alpha = img[..., 3]
    mp = A / f"textures/lowpoly/{tex}_glowmask.png"
    if mp.exists():
        m = Image.open(mp)
        if m.size != (img.shape[1], img.shape[0]):
            out["notes"].append(f"glowmask size {m.size} != texture {img.shape[1]}x{img.shape[0]}")
        elif not (np.array(m.convert("RGBA"))[..., 3] > 0).any():
            out["notes"].append("glowmask has no lit pixel")
    for b in d["bones"].values():
        if not b["triangles"]:
            continue
        UV = np.array(b["uvs"], float)
        T = np.array(b["triangles"], int)
        tri_uv = UV[T]                      # (n,3,2)
        cen = tri_uv.mean(1)
        a_c = sample(alpha, cen[:, 0], cen[:, 1])
        # 中ほど (頂点と重心の間) の三点
        mids = (tri_uv + cen[:, None, :]) / 2
        a_m = np.stack([sample(alpha, mids[:, k, 0], mids[:, k, 1]) for k in range(3)], 1)
        hole = (a_c < CUT) | ((a_m < CUT).sum(1) >= 2)
        verts = np.stack([sample(alpha, tri_uv[:, k, 0], tri_uv[:, k, 1]) for k in range(3)], 1)
        edge = ~hole & ((verts < CUT).sum(1) >= 2)
        out["hole"] += int(hole.sum())
        out["edge"] += int(edge.sum())
        out["tris"] += len(T)
    return out


def main(prefixes):
    rows = []
    for p in sorted((A / "lowpoly").glob("*.json")):
        if prefixes and not any(p.stem.startswith(x) for x in prefixes):
            continue
        try:
            rows.append(audit(p))
        except Exception as e:  # noqa: BLE001
            rows.append(dict(name=p.stem, hole=0, edge=0, tris=0, notes=[f"error {e}"]))
    rows.sort(key=lambda r: (-(r["hole"] / max(1, r["tris"])), -r["hole"]))
    lines = ["名前\t穴の三角形\t端の欠け\t三角形\t気づき"]
    bad = 0
    for r in rows:
        if r["hole"] or r["notes"]:
            bad += 1
        lines.append(f"{r['name']}\t{r['hole']}\t{r['edge']}\t{r['tris']}\t{'; '.join(r['notes'])}")
    out = ROOT / "docs/review/lowpoly_texture_audit.txt"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(rows)} meshes, {bad} with holes or glow notes -> {out}")
    for line in lines[1:26]:
        print(line)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main(sys.argv[1:])
