# -*- coding: utf-8 -*-
"""武器の作り直しの台帳を作る（tools/models/x32/manifest.json と docs/review/gear_manifest.md）。

対象 = 振りの動き (assets/weapon_moves) を持つ品 + 弓 (lowpoly/items の *_bow, *_longbow) + 盾 (coral_shield, travel_shield)。
飾り・箱・採集の道具は入れない。
- 職・種類・階級・土地: 野の武器は WildGearTable.java の行から、それ以外は id の形と lowpoly/items/<id>/ の段の名前から
- シリーズ: 野の武器は土地ごと、それ以外は id の頭（whale, tide, ... abyss_whale は abyss）
- 状態: lowpoly/look/a/<id>.json があれば done。既にある状態（manifest.json の status）は消さずに残す

  python -I tools/gen/pixel/gear_manifest.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
OUT = ROOT / "tools/models/x32/manifest.json"
MD = ROOT / "docs/review/gear_manifest.md"

JOB_ROLE = {"WARRIOR": "gear-warrior", "SMITH": "gear-smith", "MAGE": "gear-mage", "ARCHER": "gear-archer",
            "ASSASSIN": "gear-assassin", "SHAMAN": "gear-shaman"}


def kind_of(i):
    rules = [("bell_staff", "bell"), ("bell_hammer", "hammer"), ("censer", "bell"), ("handbell", "bell"), ("totem", "totem"),
             ("longbow", "bow"), ("bow", "bow"), ("greatsword", "greatsword"), ("cinder_blade", "greatsword"),
             ("warden_blade", "greatsword"), ("waning_blade", "greatsword"), ("oar_blade", "greatsword"),
             ("glaive", "glaive"), ("halberd", "glaive"), ("scythe", "scythe"), ("pike", "spear"), ("spear", "spear"),
             ("trident", "spear"), ("harpoon", "spear"), ("sword", "sword"), ("axe", "axe"), ("maul", "hammer"),
             ("hammer", "hammer"), ("anchor", "hammer"), ("mace", "hammer"), ("staff", "staff"), ("poker", "poker"),
             ("claw", "claw"), ("fang", "fang"), ("dagger", "dagger"), ("knife", "dagger"), ("sickle", "claw"), ("shield", "shield")]
    for k, v in rules:
        if k in i:
            return v
    return "?"


ROLE_OF_KIND = {"sword": "gear-warrior", "greatsword": "gear-warrior", "spear": "gear-warrior", "glaive": "gear-warrior",
                "shield": "gear-warrior", "hammer": "gear-smith", "axe": "gear-smith", "scythe": "gear-smith",
                "staff": "gear-mage", "poker": "gear-mage", "bow": "gear-archer", "dagger": "gear-assassin",
                "fang": "gear-assassin", "claw": "gear-assassin", "bell": "gear-shaman", "totem": "gear-shaman"}


def series_of(i):
    for p in ("abyss", "divine", "moon", "tower", "ember", "storm", "tide", "whale", "shrine"):
        if i.startswith(p + "_"):
            return p
    return "other"


def main():
    wild = {}
    src = (ROOT / "src/main/java/jp/revati/skyabyss/item/wild/WildGearTable.java").read_text(encoding="utf-8")
    for m in re.finditer(r'weapon\("(wild_\w+)", Job\.(\w+), "(\w+)", GearTier\.(\w+), (\d+), "(\w+)"', src):
        wild[m.group(1)] = dict(job=m.group(2), kind=m.group(3), tier=m.group(4).lower(), level=int(m.group(5)), land=m.group(6))
    ids = {p.stem for p in (A / "weapon_moves").glob("*.json")}
    ids |= {p.name for p in (A / "lowpoly/items").iterdir() if p.is_dir() and (p.name.endswith("bow"))}
    ids |= {"coral_shield", "travel_shield"}
    old = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
    rows = {}
    for i in sorted(ids):
        tiers = sorted({q.stem.split("_pull")[0] for q in (A / "lowpoly/items" / i).glob("*.json")}) if (A / "lowpoly/items" / i).exists() else []
        if i in wild:
            w = wild[i]
            row = dict(series="wild_" + w["land"], kind=w["kind"], tier=w["tier"], level=w["level"], role=JOB_ROLE[w["job"]])
        else:
            k = kind_of(i)
            row = dict(series=series_of(i), kind=k, tier=(tiers[0] if tiers else "?"), level=None, role=ROLE_OF_KIND.get(k, "?"))
        row["bow"] = row["kind"] == "bow"
        done = (A / "lowpoly/look/a" / f"{i}.json").exists()
        row["status"] = "done" if done else old.get(i, {}).get("status", "todo")
        rows[i] = row
    OUT.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")
    # 人が読む表
    by = {}
    for i, r in rows.items():
        by.setdefault(r["series"], []).append((i, r))
    lines = ["# 武器の作り直しの台帳（x32・look a）", "", "tools/gen/pixel/gear_manifest.py が作る。状態は tools/models/x32/manifest.json。", "",
             f"全 {len(rows)} 品、済み {sum(r['status'] == 'done' for r in rows.values())}。", ""]
    for s in sorted(by):
        items = by[s]
        lines.append(f"## {s}（{len(items)}、済み {sum(r['status'] == 'done' for _, r in items)}）")
        lines.append("")
        lines.append("| id | 種類 | 階級 | 担当 | 状態 |")
        lines.append("| --- | --- | --- | --- | --- |")
        for i, r in items:
            lines.append(f"| {i} | {r['kind']} | {r['tier']} | {r['role']} | {r['status']} |")
        lines.append("")
    MD.write_text("\n".join(lines), encoding="utf-8")
    print(len(rows), "weapons;", {s: len(v) for s, v in sorted(by.items())})
    unknown = [i for i, r in rows.items() if "?" in (r["kind"], r["role"], r["tier"])]
    if unknown:
        print("unknown:", unknown)


if __name__ == "__main__":
    main()
