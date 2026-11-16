# -*- coding: utf-8 -*-
"""手彫り・設計から彫った模型が無い低ポリのメッシュ全部を mesh_sculpt.py で彫る (巨神は別: mesh_sculpt.py --titan)。
点の数の目安は種類ごと (データの大きさを抑える): NPC 3 万・敵 4 万・防具 1.2 万・品 0.8 万・攻撃の形と持ち物 1.2 万。
  python -I tools/gen/splat/sculpt_rest.py [--only 頭] [--skip-existing] [--list ファイル]
"""
import argparse
import sys
import time
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mesh_sculpt as MS  # noqa: E402

A = MS.ASSETS
TITANS = {f"{t}{s}" for t in ("stone_whale", "tide_shell", "thunder_wing", "ember_titan") for s in ("", "_mid", "_far")}


def budget(name):
    if name.startswith("items/"):
        return 8000.0
    if name.startswith("armor/"):
        return 12000.0
    if name.startswith("npc_") and "_prop" in name:
        return 12000.0
    if name.startswith("prop_"):
        return 12000.0
    if name.startswith("npc_"):
        return 30000.0
    return 40000.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="")
    ap.add_argument("--skip-existing", action="store_true")
    ap.add_argument("--list", default="", help="名前を 1 行に 1 つ書いたファイル (この名前だけ彫る)")
    a = ap.parse_args()
    names = sorted(str(p.relative_to(A / "lowpoly")).replace("\\", "/")[:-5] for p in (A / "lowpoly").rglob("*.json"))
    todo = [n for n in names if n not in TITANS and n.startswith(a.only)]
    if a.list:
        want = set(Path(a.list).read_text(encoding="utf-8").split())
        todo = [n for n in todo if n in want]
    if a.skip_existing:
        todo = [n for n in todo if not (A / "splat" / (n + ".bin")).exists()]
    print(len(todo), "models", flush=True)
    t0 = time.time()
    for i, n in enumerate(todo):
        try:
            MS.sculpt_mesh(n, False, budget(n))
        except Exception:  # noqa: BLE001
            print("FAILED", n, traceback.format_exc().splitlines()[-1], flush=True)
        if i % 20 == 0:
            print(f"[{i}/{len(todo)}] {time.time() - t0:.0f}s", flush=True)


if __name__ == "__main__":
    main()
