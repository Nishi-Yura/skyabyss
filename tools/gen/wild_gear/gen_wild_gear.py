# -*- coding: utf-8 -*-
"""野の装備 (docs/design/51) の生成。spec.py の表から作る:
  src/main/java/jp/revati/skyabyss/item/wild/WildGearTable.java   品の表 (登録・能力・落とし物が読む)
  src/main/resources/assets/skyabyss_wild/lang/{ja_jp,en_us}.json  名前・能力の説明・由来・一式の説明

実行: python -I tools/gen/wild_gear/gen_wild_gear.py   (何度流してもよい)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
import spec as S  # noqa: E402

JAVA = ROOT / "src/main/java/jp/revati/skyabyss/item/wild/WildGearTable.java"
LANG = ROOT / "src/main/resources/assets/skyabyss_wild/lang"
JOB = {"warrior": "WARRIOR", "smith": "SMITH", "mage": "MAGE", "archer": "ARCHER", "assassin": "ASSASSIN", "shaman": "SHAMAN"}
TIER = {"uncommon": "UNCOMMON", "rare": "RARE", "epic": "EPIC", "heroic": "HEROIC"}
ROLE_JA = {"heavy": "重", "mid": "中", "light": "軽", "mystic": "術"}
ROLE_EN = {"heavy": "Heavy", "mid": "Medium", "light": "Light", "mystic": "Mystic"}


def num(v):
    s = repr(float(v))
    return s[:-2] if s.endswith(".0") else s


def fx_list(fx):
    if not fx:
        return "List.of()"
    parts = []
    for f in fx:
        args = ", ".join(num(v) for v in f[1:])
        parts.append(f"fx(Kind.{f[0]}{', ' + args if args else ''})")
    return "List.of(" + ", ".join(parts) + ")"


def java():
    lines = []
    for w in S.WEAPONS:
        bow = w["kind"] in ("bow", "longbow")
        st = S.weapon_stats(w)
        attack, speed, mult = (0, 0, st) if bow else (st, w["speed"], 0)
        lines.append(f'            weapon("{w["id"]}", Job.{JOB[w["job"]]}, "{w["kind"]}", GearTier.{TIER[w["tier"]]}, {w["lv"]}, "{w["region"]}", '
                     f'{num(attack)}, {num(speed)}, {num(mult)}, {fx_list(w["fx"])})')
    alines = []
    for a in S.ARMORS:
        pieces, tough = S.armor_stats(a)
        alines.append(f'            armor("{a["id"]}", "{a["role"]}", GearTier.{TIER[a["tier"]]}, {a["lv"]}, "{a["region"]}", '
                      f'new int[]{{{", ".join(str(p) for p in pieces)}}}, {num(tough)}, {fx_list(a["set2"])}, {fx_list(a["set4"])})')
    src = f"""package jp.revati.skyabyss.item.wild;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.wild.WildEffect.Kind;
import jp.revati.skyabyss.rpg.skill.Job;

import static jp.revati.skyabyss.item.wild.WildEffect.fx;

// 野の装備の表 (docs/design/51)。tools/gen/wild_gear/gen_wild_gear.py が spec.py から作る。手で直さない
public final class WildGearTable {{
    private WildGearTable() {{
    }}

    // 武器 {len(S.WEAPONS)} 本: ID・職・種類・階級・必要 Lv・土地・攻撃力・速さ・矢の倍率 (弓だけ)・能力
    public static final List<WildGearTypes.Weapon> WEAPONS = List.of(
{(","+chr(10)).join(lines)});

    // 防具 {len(S.ARMORS)} 式: ID・役・階級・必要 Lv・土地・部位ごとの防御 (兜・胴・脚・靴)・部位ごとの強靭・2 部位の効果・4 部位の効果
    public static final List<WildGearTypes.ArmorSet> ARMORS = List.of(
{(","+chr(10)).join(alines)});

    private static WildGearTypes.Weapon weapon(String id, Job job, String kind, GearTier tier, int level, String region, double attack, double speed,
            double bow, List<WildEffect.Fx> fx) {{
        return new WildGearTypes.Weapon(id, job, kind, tier, level, region, attack, speed, bow, fx);
    }}

    private static WildGearTypes.ArmorSet armor(String id, String role, GearTier tier, int level, String region, int[] defense, double toughness,
            List<WildEffect.Fx> set2, List<WildEffect.Fx> set4) {{
        return new WildGearTypes.ArmorSet(id, role, tier, level, region, defense, toughness, set2, set4);
    }}
}}
"""
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(src, encoding="utf-8")


def lang():
    ja, en = {}, {}
    for key, (j, e) in S.REGIONS.items():
        ja[f"gear.skyabyss.origin.wild_{key}"] = j
        en[f"gear.skyabyss.origin.wild_{key}"] = e
        ja[f"gear.skyabyss.origin_weapon.wild_{key}"] = j
        en[f"gear.skyabyss.origin_weapon.wild_{key}"] = e
    for w in S.WEAPONS:
        ja[f"item.skyabyss.{w['id']}"] = w["ja"]
        en[f"item.skyabyss.{w['id']}"] = w["en"]
        ja[f"item.skyabyss.{w['id']}.desc"] = w["abil_ja"]
        en[f"item.skyabyss.{w['id']}.desc"] = w["abil_en"]
    for a in S.ARMORS:
        for p in S.PIECES:
            ja[f"item.skyabyss.{a['id']}_{p}"] = f"{a['ja']}の{S.PIECE_JA[p]}"
            en[f"item.skyabyss.{a['id']}_{p}"] = f"{a['en']} {S.PIECE_EN[p]}"
        ja[f"item.skyabyss.{a['id']}.desc"] = f"{ROLE_JA[a['role']]}の一式。{a['set_ja']}"
        en[f"item.skyabyss.{a['id']}.desc"] = f"{ROLE_EN[a['role']]} set. {a['set_en']}"
    ja["message.skyabyss.wild_drop"] = "%s を手に入れた"
    en["message.skyabyss.wild_drop"] = "Found %s"
    LANG.mkdir(parents=True, exist_ok=True)
    for name, d in (("ja_jp", ja), ("en_us", en)):
        (LANG / f"{name}.json").write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# 店 (鍛冶屋): アンコモンを章に合わせて並べる (帯 I = 一章から、帯 II = 二章から)。値は鐘銭
SHOP = ROOT / "src/main/resources/data/skyabyss/shop/smithy.json"


def shop():
    d = json.loads(SHOP.read_text(encoding="utf-8"))
    d["goods"] = [g for g in d["goods"] if not g["id"].startswith("wild_")]
    for w in S.WEAPONS:
        if w["tier"] == "uncommon":
            d["goods"].append({"id": w["id"], "item": "skyabyss:" + w["id"], "count": 1, "coins": 8 + w["lv"], "chapter": S.band(w["lv"])})
    for a in S.ARMORS:
        if a["tier"] == "uncommon":
            for p, share in zip(S.PIECES, S.PIECE_SPLIT):
                d["goods"].append({"id": f"{a['id']}_{p}", "item": f"skyabyss:{a['id']}_{p}", "count": 1,
                                   "coins": max(2, round((6 + a["lv"]) * share * 1.6)), "chapter": S.band(a["lv"])})
    SHOP.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    java()
    lang()
    shop()
    print("wild gear:", len(S.WEAPONS), "weapons,", len(S.ARMORS), "armor sets")
