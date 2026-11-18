# -*- coding: utf-8 -*-
"""look a（手で組んだ x32 の模型）がある武器の持ち方を、種類ごとの決まりに合わせる（models/item/<id>.json の display）。

古い取り込み（tools/install/*.py）は持ち方を似た武器から写すので、流し直すとここで決めた持ち方が消える。
取り込みを流したら、必ずこれも流す（何度流してもよい）。

決まり（2026-11-18 の祠の 6 品で決めた。ユーザーが見て OK を出した）:
- 振る武器（剣・大剣・短剣・牙・爪・鎚・斧・鎌）: 三人称は前で上へ起こして構える。z を 55 → 20（左手 -35 → -70）
- 振る武器の三人称は、借りた値から高さを 2 下げ（4.0 → 2.0、4.2 → 2.2、4.5 → 2.5）、z を 2 足して体の内側へ寄せる（0.5 → 2.5）。
  2026-11-19「手から浮いてない？」→「まだ浮いてる。もうちょっと全体的に内側」: 上へ起こすと握りが拳の先・体の外へ出ていた。
  短剣で x・z・高さを撮り比べ、z が体の内外に効くのを確かめた（x はほとんど変わらない）
- 杖・鈴杖・弓・槍・長柄: 今の持ち方のまま（三人称の位置と弓の倒し方は WeaponItemRenderer の look a の枝で足す）

  python -I tools/gen/pixel/look_holds.py [--check]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
MANIFEST = ROOT / "tools/models/x32/manifest.json"

RAISED = {"sword", "greatsword", "dagger", "fang", "claw", "hammer", "maul", "mace", "axe", "scythe"}
TP_R = (0, -90, 55)      # 振る武器の、借りた持ち方の三人称（右手）
RAISED_R, RAISED_L = [0, -90, 20], [0, -90, -70]
BORROWED_Y = (4.0, 4.2, 4.5)   # 借りた持ち方の三人称の高さ
DROP_Y, IN_Z = 2.0, 2.0
BORROWED_Z = 0.5


def settle(d):
    """三人称の位置を決まりに合わせる。借りた値（または前の決まりで 1.5 だけ下げた値）から直すので、何度流しても同じ"""
    t = d["thirdperson_righthand"]["translation"]
    base = {round(y - 1.5, 2): y for y in BORROWED_Y}
    y0 = round(t[1], 2)
    y0 = base.get(y0, y0)
    if y0 not in BORROWED_Y or round(t[2], 2) != BORROWED_Z:
        return False
    for hand in ("thirdperson_righthand", "thirdperson_lefthand"):
        d[hand]["translation"][1] = round(y0 - DROP_Y, 2)
        d[hand]["translation"][2] = round(BORROWED_Z + IN_Z, 2)
    return True


def main():
    check = "--check" in sys.argv
    rows = json.loads(MANIFEST.read_text(encoding="utf-8"))
    changed = []
    for i, r in rows.items():
        if not (A / "lowpoly/look/a" / f"{i}.json").exists() or r["kind"] not in RAISED:
            continue
        p = A / "models/item" / f"{i}.json"
        data = json.loads(p.read_text(encoding="utf-8"))
        d = data["base"]["display"]
        rot = tuple(round(v) for v in d["thirdperson_righthand"]["rotation"])
        if rot == tuple(RAISED_R):
            if settle(d):
                changed.append(i)
                if not check:
                    p.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            continue
        if rot != TP_R:
            print(f"{i}: 三人称の回転が想定外 {rot}（手で確かめる）")
            continue
        changed.append(i)
        if not check:
            d["thirdperson_righthand"]["rotation"] = list(RAISED_R)
            d["thirdperson_lefthand"]["rotation"] = list(RAISED_L)
            settle(d)
            p.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(("differs: " if check else "updated: ") + ", ".join(changed) if changed else "ok")
    if check and changed:
        sys.exit(1)


if __name__ == "__main__":
    main()
