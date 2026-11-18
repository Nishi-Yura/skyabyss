# -*- coding: utf-8 -*-
"""look a（手で組んだ x32 の模型）がある武器の持ち方を、種類ごとの決まりに合わせる（models/item/<id>.json の display）。

古い取り込み（tools/install/*.py）は持ち方を似た武器から写すので、流し直すとここで決めた持ち方が消える。
取り込みを流したら、必ずこれも流す（何度流してもよい）。

決まり（2026-11-18 の祠の 6 品で決めた。ユーザーが見て OK を出した）:
- 振る武器（剣・大剣・短剣・牙・爪・鎚・斧・鎌）: 三人称は前で上へ起こして構える。z を 55 → 20（左手 -35 → -70）
- 振る武器の三人称の高さは、借りた値から 1.5 下げる（4.0 → 2.5、4.2 → 2.7、4.5 → 3.0）。
  2026-11-19「手から浮いてない？」: 上へ起こすと握りが拳の先へ出ていた。短剣・剣・大剣で 1.5 下げると握りが手に入るのを撮って確かめた
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
DROP_Y = 1.5


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
        y = d["thirdperson_righthand"]["translation"][1]
        if rot == tuple(RAISED_R):
            if round(y, 2) in BORROWED_Y:
                changed.append(i)
                if not check:
                    for hand in ("thirdperson_righthand", "thirdperson_lefthand"):
                        d[hand]["translation"][1] = round(d[hand]["translation"][1] - DROP_Y, 2)
                    p.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            continue
        if rot != TP_R:
            print(f"{i}: 三人称の回転が想定外 {rot}（手で確かめる）")
            continue
        changed.append(i)
        if not check:
            d["thirdperson_righthand"]["rotation"] = list(RAISED_R)
            d["thirdperson_lefthand"]["rotation"] = list(RAISED_L)
            for hand in ("thirdperson_righthand", "thirdperson_lefthand"):
                d[hand]["translation"][1] = round(d[hand]["translation"][1] - DROP_Y, 2)
            p.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(("differs: " if check else "updated: ") + ", ".join(changed) if changed else "ok")
    if check and changed:
        sys.exit(1)


if __name__ == "__main__":
    main()
