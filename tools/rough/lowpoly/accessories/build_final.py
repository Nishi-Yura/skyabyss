"""採用した A 案の装飾品 16 品 (雷翼 4・余燼 5・岩鯨 3・潮殻 4) を、階級 3 段 (common / rare / legend) ずつ書き出す。
出力: docs/review/lowpoly_roughs/accessories_final/<品の id>/<段>_{mesh.json,atlas.png,glowmask.png} と summary.json (面数)。
装飾品は鍛えない (03 の 4 章・6 章) ので、強化の +5 / +10 の絵は無い。
実行: python build_final.py   (tools/install/install_lowpoly_accessories.py がこの出力をゲームへ入れる)"""
from __future__ import annotations

import json

import ember
import thunder
import tide
import whale
from common import *

FINAL = ROOT / "docs/review/lowpoly_roughs/accessories_final"
BUDGET = dict(worn=200, charm=150, held=150)


def main():
    summary = {}
    for mod in (thunder, ember, whale, tide):
        for iid, it in mod.ITEMS.items():
            row = {"jp": it["jp"], "kind": it["kind"], "rar": it["rar"], "titan": mod.TITAN, "tris": {}}
            for rar in ("common", "rare", "legend"):
                r = make(it["fn"]["a"], mod.TITAN, rar)
                save(r, FINAL / iid, rar)
                row["tris"][rar] = r["tris"]
                if r["tris"] > BUDGET[it["kind"]]:
                    print("OVER", iid, rar, r["tris"])
            summary[iid] = row
            print(iid, row["tris"])
    (FINAL / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
