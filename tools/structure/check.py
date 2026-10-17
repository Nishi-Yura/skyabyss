"""書き出した構造 (.sbd) から図と検査の報告を作る。ゲームは動かさない。

    python tools/structure/check.py run/shots/dump_backs/dumps/titan_back.sbd --out docs/review --prefix structure_titan_back \\
        --walk-from arrival --goal shrine --goal hub --seed hub --section y=94 --section x=0

出力 (<out>/<prefix>_*):
  top.png       真上から (地図の色、高さの陰)
  iso.png       等角の図 (南東の上から)
  sections.png  断面 (--section y=.. / x=.. / z=..。何枚でも)
  walk.png      歩いて行ける所 (足の高さで色) と起点・目標の印
  report.txt    歩いて着くか (目標ごと)、部屋から外への口、水の広がりの見積もり、今流れている水、看板の一覧

起点・目標・部屋は、ヘッダの起点の名前 (titan_back: arrival / shrine / hub、tide_back: arrival / rest_hub など) か "x,y,z"。
"x,~,z" はその列でいちばん上の立てる所。目標に着かなければ終了コード 1
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import analyze  # noqa: E402
import render  # noqa: E402
from sbd import load  # noqa: E402


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dump")
    ap.add_argument("--out", default="docs/review")
    ap.add_argument("--prefix", default=None, help="出力の名前の頭 (既定 structure_<dump の名前>)")
    ap.add_argument("--walk-from", default=None, help="歩き始め (既定: ヘッダの arrival)")
    ap.add_argument("--goal", action="append", default=[], help="歩いて着くか調べる所 (何度でも)。既定: ヘッダの起点すべて")
    ap.add_argument("--seed", action="append", default=[], help="外への口を調べる部屋の中の一点 (何度でも)")
    ap.add_argument("--section", action="append", default=[], help="断面 y=<高さ> / x=<x> / z=<z>")
    ap.add_argument("--iso-step", type=int, default=1, help="等角の図を間引く (大きい構造は 2)")
    ap.add_argument("--no-water", action="store_true", help="水の広がりの見積もりを飛ばす")
    ap.add_argument("--min-opening", type=int, default=1, help="この大きさより小さい口は書かない")
    args = ap.parse_args(argv)

    t0 = time.time()
    d = load(args.dump)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    prefix = args.prefix or f"structure_{d.name}"
    rep: list[str] = []

    def say(s=""):
        print(s)
        rep.append(s)

    sx, sy, sz = d.header["size"]
    say(f"# {d.name}  ({d.dimension}, origin {d.origin}, size {sx}x{sy}x{sz}, game time {d.header.get('gameTime')})")
    say(f"block kinds {len(d.palette)}; most common: " + ", ".join(f"{s} {c}" for s, c in d.block_counts()[1:8]))
    points = {k: tuple(v) for k, v in d.header.get("points", {}).items()}

    # ---------------------------------------------------------------- 図
    render.label(render.top(d), f"{d.name} top (north up)").save(out / f"{prefix}_top.png")
    render.iso(d, step=args.iso_step).save(out / f"{prefix}_iso.png")
    if args.section:
        imgs = []
        for s in args.section:
            axis, value = s.split("=")
            imgs.append(render.label(render.section(d, axis.strip(), int(value)), f"{d.name} section {axis}={value}"))
        w = max(i.width for i in imgs)
        sheet = Image.new("RGB", (w, sum(i.height + 6 for i in imgs)), (255, 255, 255))
        y = 0
        for i in imgs:
            sheet.paste(i, (0, y))
            y += i.height + 6
        sheet.save(out / f"{prefix}_sections.png")

    # ---------------------------------------------------------------- 歩いて行ける所
    failed = False
    start_name = args.walk_from or ("arrival" if "arrival" in points else None)
    if start_name:
        start = d.point(start_name)
        dist, parent = analyze.walk(d, start)
        say("")
        say(f"## walk from {start_name} {start}: {len(dist)} standing places reached")
        goals = args.goal or [k for k in points if k != start_name]
        marks = {start_name: start}
        for g in goals:
            p = d.point(g)
            marks[g] = p
            hit = analyze.nearest_reached(dist, p)
            if hit:
                say(f"  {g} {p}: reached in {hit[1]} steps; path " + " > ".join(f"{x},{y},{z}" for x, y, z in analyze.path_to(parent, hit[0])[:30]))
            else:
                failed = True
                # いちばん近くまで来た所
                best = min(dist, key=lambda q: (q[0] - p[0]) ** 2 + (q[1] - p[1]) ** 2 * 4 + (q[2] - p[2]) ** 2)
                say(f"  {g} {p}: NOT REACHED (closest reached {best}, {dist[best]} steps)")
        render.overlay_walk(d, dist, marks=marks).save(out / f"{prefix}_walk.png")

    # ---------------------------------------------------------------- 部屋から外への口
    seeds = args.seed
    if seeds:
        say("")
        say("## openings (where an enclosed room touches open space; glass counts as wall)")
        for sname in seeds:
            p = d.point(sname)
            r = analyze.openings(d, p, min_cells=args.min_opening)
            if r["room"] == 0:
                say(f"  {sname} {p}: not inside an enclosed room (open={r['seed_open']})")
                continue
            say(f"  {sname} {p}: room {r['room']} cells, {len(r['openings'])} opening(s)")
            for o in r["openings"][:25]:
                say(f"    {o['cells']:5d} cells around {o['center']}  (box {o['min']} .. {o['max']})")

    # ---------------------------------------------------------------- 水
    if not args.no_water:
        say("")
        flowing = analyze.flowing_now(d)
        say(f"## water: {int(d.water.sum())} water blocks, {int((d.water & d.source).sum())} sources, {int(flowing.sum())} flowing now")
        for c in analyze.clusters(flowing, d, top=10):
            say(f"  flowing {c['cells']:5d} in {c['min']} .. {c['max']}")
        leaks = analyze.waterlogged_leaks(d)
        if leaks.any():
            say(f"  waterlogged blocks next to open space (water flows out once a neighbour updates): {int(leaks.sum())}")
            for c in analyze.clusters(leaks, d, top=10):
                say(f"    {c['cells']:5d} in {c['min']} .. {c['max']}  e.g. {c['first']} {d.at(*c['first'])['state']}")
        w = analyze.water(d)
        filled = w["filled"]
        enc = analyze.enclosed(d)
        inside = filled & enc
        say(f"  static estimate: water could spread into {int(filled.sum())} open cells ({int(inside.sum())} of them inside enclosed rooms)")
        for c in analyze.clusters(inside, d, top=15):
            say(f"    inside rooms {c['cells']:5d} in {c['min']} .. {c['max']}")

    # ---------------------------------------------------------------- 看板
    signs = analyze.signs(d)
    say("")
    say(f"## signs: {len(signs)}")
    for s in signs:
        front = " / ".join(x for x in s["front"] if x)
        back = " / ".join(x for x in s["back"] if x)
        say(f"  {s['pos']} {s['block'].split('[')[0]}: {front}" + (f"  [back] {back}" if back else ""))
    bes = d.header.get("blockEntities", [])
    if bes:
        kinds = {}
        for b in bes:
            kinds[b["type"]] = kinds.get(b["type"], 0) + 1
        say("## other block entities: " + ", ".join(f"{k} {v}" for k, v in sorted(kinds.items())))

    say("")
    say(f"done in {time.time() - t0:.1f} s; images in {out}/{prefix}_*.png")
    (out / f"{prefix}_report.txt").write_text("\n".join(rep) + "\n", encoding="utf-8")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
