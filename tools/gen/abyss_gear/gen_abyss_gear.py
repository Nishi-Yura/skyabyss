"""五章「深淵」の装備の文とレシピ (docs/design/03 の 12 章)。

出力:
  src/main/resources/assets/skyabyss_abyss_gear/lang/{ja_jp,en_us}.json   名前・説明・物語の一行・由来・炉・記録・知らせ
  src/main/resources/data/skyabyss/recipe/abyss_lantern_charm.json         遺品「空の手燭」をなぞる (遺品は残る: AbyssRelicItem)
  src/main/resources/data/skyabyss/recipe/abyss_pulse_listener.json        遺品「心臓の形の石」をなぞる
荷運びのロクの取り替えは データを直接直す（生成スクリプトはない） の TRADES。
使い方: python -I tools/gen/abyss_gear/gen_abyss_gear.py   (何度流してもよい)
"""
from __future__ import annotations

import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
LANG = os.path.join(ROOT, "src/main/resources/assets/skyabyss_abyss_gear/lang")
RECIPE = os.path.join(ROOT, "src/main/resources/data/skyabyss/recipe")

# (鍵, 日本語, 英語)
T = [
    # ---- 名前
    ("item.skyabyss.abyss_diver_helmet", "淵潜りの兜", "Abyss Diver's Helm"),
    ("item.skyabyss.abyss_diver_chestplate", "淵潜りの胴", "Abyss Diver's Cuirass"),
    ("item.skyabyss.abyss_diver_leggings", "淵潜りのすね当て", "Abyss Diver's Greaves"),
    ("item.skyabyss.abyss_diver_boots", "淵潜りの靴", "Abyss Diver's Boots"),
    ("item.skyabyss.abyss_lamp_sword", "灯り番の剣", "Lamp-Keeper's Sword"),
    ("item.skyabyss.abyss_pulse_listener", "鼓動の聴き筒", "Pulse Listener"),
    ("item.skyabyss.abyss_lantern_charm", "灯籠の護符", "Lantern Charm"),
    ("item.skyabyss.abyss_sinking_anchor", "沈め錨", "Sinking Anchor"),
    ("item.skyabyss.abyss_pulse_dagger", "鼓動の短剣", "Heartbeat Dagger"),
    ("item.skyabyss.abyss_surveyor_plumb", "測量士の錘", "Surveyor's Plumb"),
    ("item.skyabyss.abyss_anchor_iron", "錨鉄", "Anchor Iron"),
    ("item.skyabyss.abyss_core", "深淵の心核", "Abyss Core"),
    ("item.skyabyss.resonance_shard_abyss", "共鳴の欠片（深淵）", "Resonance Fragment (Abyss)"),
    # ---- 説明
    ("item.skyabyss.abyss_diver_armor.desc",
     "前の探掘隊が深淵へ下りるときに着た、錨鉄の板と鎖の鎧。2 部位で強靭 +3。4 部位で深淵の上昇の重み −40%・闇のたまり −20%、どこでも暗い所（明るさ 4 以下）で受ける傷 −15%",
     "The anchor-iron plate and chain the last excavation party wore down. 2 pieces: +3 toughness. 4 pieces: abyss climbing weight -40% and darkness build-up -20%; anywhere, -15% damage taken in the dark (light 4 or less)"),
    ("item.skyabyss.abyss_lamp_sword.desc",
     "灯り番が角灯を吊るして振った剣。灯りの中（明るさ 8 以上）では深く入る（+10%）。闇の中で斬った敵は 4 秒光る（+5 で 6 秒、まわりの敵も）",
     "The lamp-keeper's sword, a lantern hung from its guard. Cuts deeper in the light (light 8+, +10%). Foes struck in the dark glow for 4 s (+5: 6 s, and those nearby)"),
    ("item.skyabyss.abyss_pulse_listener.desc",
     "装飾の枠に付けていると、4 秒ごとに 12 ブロック内の敵の鼓動を聞き、壁の向こうでも輪郭を光らせる。深淵では闇のたまり −15%",
     "Worn as a trinket, every 4 s it hears the heartbeats of foes within 12 blocks and outlines them, even through walls. In the abyss, darkness builds 15% slower"),
    ("item.skyabyss.abyss_lantern_charm.desc",
     "護符の枠に付けて使うと、足もとに灯りを置く（半径 8、60 秒。光の術の灯りと同じ）。まわりの敵を光らせて押し返し、自分は 30 秒夜目が利く。待ち 45 秒",
     "Use from the charm slot to set a light at your feet (radius 8, 60 s; the same as a Light art lamp). Nearby foes glow and are pushed back; you gain night vision for 30 s. Cooldown 45 s"),
    ("item.skyabyss.abyss_sinking_anchor.desc",
     "錨の欠けた爪を柄に付けた両手の武器。3 度に 1 度（+5 から 2 度に 1 度）、まわりの敵に重しが落ち、傷と鈍足を与えて地面へ引き下ろす",
     "A two-handed weapon made from a broken anchor fluke. Every 3rd hit (every 2nd from +5) a weight falls on nearby foes, hurting and slowing them and dragging them down"),
    ("item.skyabyss.abyss_pulse_dagger.desc",
     "同じ敵を続けて刺すと拍が数えられ、4 度目ごと（+5 から 3 度目ごと）に追い打ちの拍が入り、体力が少し戻る",
     "Keep stabbing the same foe and the beats are counted: every 4th hit (every 3rd from +5) lands an extra beat and heals you a little"),
    ("item.skyabyss.abyss_surveyor_plumb.desc",
     "装飾の枠に付けていると、深いほど与える傷が増える（深淵では深さ 40 ごとに +1%、最大 +12%。地上では y 64 から 16 下がるごとに +1%、最大 +8%）。落下の傷 −30%",
     "Worn as a trinket, you deal more damage the deeper you are (abyss: +1% per 40 depth, up to +12%; surface: +1% per 16 blocks below y 64, up to +8%). -30% fall damage"),
    ("item.skyabyss.abyss_anchor_iron.desc", "深淵の底から上がってきた、重く冷たい鉄。深淵の装備を鍛え、直す",
     "Heavy, cold iron brought up from the abyss floor. Enhances and repairs abyss gear"),
    ("item.skyabyss.abyss_core.desc", "心臓の番人の胸に残っていた、まだ脈を打つ核。伝説の装備の材料",
     "A core still beating, left in the warden's chest. A material for legendary gear"),
    ("item.skyabyss.resonance_shard_abyss.desc",
     "深淵の共鳴の欠片。深淵の専用の逸品との交換に 30 個。心臓の番人・層の主が落とし、深淵のヒロイックの品を分解しても得られる",
     "The abyss's Resonance Fragment. 30 trade for an abyss exclusive. Dropped by the heart's warden and the layer lords, and returned when you dismantle abyss heroic gear"),
    # ---- 物語の一行 (GearCurveSelfTest: 28 字まで、英語 70 字まで、重ならない)
    ("item.skyabyss.abyss_diver_helmet.story", "「灯りは額に。目は闇に慣らすな。」", "\"Light on your brow. Never let your eyes get used to the dark.\""),
    ("item.skyabyss.abyss_diver_chestplate.story", "「重いのは鎧ではない。帰り道だ。」", "\"It isn't the armor that's heavy. It's the way back up.\""),
    ("item.skyabyss.abyss_diver_leggings.story", "「膝を折るな。折れば、もう上がれない。」", "\"Don't kneel. Kneel, and you won't climb again.\""),
    ("item.skyabyss.abyss_diver_boots.story", "「足音を消せ。壁の向こうが聞いている。」", "\"Quiet your steps. Something behind the wall is listening.\""),
    ("item.skyabyss.abyss_lamp_sword.story", "「灯りを持つ者が、いちばん先を歩く。」", "\"Whoever holds the lamp walks in front.\""),
    ("item.skyabyss.abyss_pulse_listener.story", "「聞こえる鼓動は、ひとつではない。」", "\"The heartbeat you hear is never just one.\""),
    ("item.skyabyss.abyss_lantern_charm.story", "「消えた灯籠の数だけ、闇が近い。」", "\"Every lamp that goes out brings the dark one step closer.\""),
    ("item.skyabyss.abyss_sinking_anchor.story", "「錨は、沈めるためにある。」", "\"An anchor is made to sink.\""),
    ("item.skyabyss.abyss_pulse_dagger.story", "「拍に合わせて刺せ。心臓はそう打つ。」", "\"Strike on the beat. That's how a heart strikes.\""),
    ("item.skyabyss.abyss_surveyor_plumb.story", "「錘は嘘をつかない。深さだけを告げる。」", "\"A plumb never lies. It only tells you how deep.\""),
    # ---- 由来・炉・記録
    ("gear.skyabyss.origin.abyss", "深淵の専用装備", "Abyss gear"),
    ("gear.skyabyss.origin_weapon.abyss", "深淵の専用武器", "Abyss weapon"),
    ("gui.skyabyss.forge.exchange_no_clear.abyss", "心臓の番人を倒すと、選べるようになる", "Defeat the heart's warden to choose"),
    ("gui.skyabyss.forge.exchange_all_owned.abyss", "深淵の専用の逸品は、すべて手にしている", "You hold every abyss exclusive"),
    ("skyabyss_record.h.abyss", "深淵", "Abyss"),
    ("skyabyss_record.short.abyss", "深淵", "Abyss"),
    # ---- 知らせ
    ("message.skyabyss.abyss_gear.lord_reward", "層の主が遺したもの: %s", "The lord left behind: %s"),
    ("message.skyabyss.abyss_gear.warden_reward", "番人が遺したもの: %s", "The warden left behind: %s"),
]


def recipe(result, pattern, keys):
    return {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
            "key": {k: {"item": v} for k, v in keys.items()}, "result": {"id": result, "count": 1}}


RECIPES = {
    # 空の手燭 (骨の層の墓 2) をなぞって、灯籠を小さく写す
    "abyss_lantern_charm": recipe("skyabyss:abyss_lantern_charm", [" R ", "ILI", " I "],
                                  {"R": "skyabyss:abyss_relic_bone_2", "I": "skyabyss:abyss_anchor_iron", "L": "minecraft:soul_lantern"}),
    # 心臓の形の石 (灰の層の墓 3) をなぞって、鼓動を聞く筒を作る
    "abyss_pulse_listener": recipe("skyabyss:abyss_pulse_listener", ["IRI", "IAI", " I "],
                                   {"R": "skyabyss:abyss_relic_ash_3", "I": "skyabyss:abyss_anchor_iron", "A": "minecraft:amethyst_shard"}),
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    keys = [k for k, _, _ in T]
    dup = {k for k in keys if keys.count(k) > 1}
    if dup:
        raise SystemExit("duplicate keys: %s" % sorted(dup))
    for k, j, e in T:
        if k.endswith(".story"):
            assert len(j) <= 28 and j.startswith("「") and j.endswith("」"), k
            assert len(e) <= 70, (k, len(e))
    write(os.path.join(LANG, "ja_jp.json"), {k: j for k, j, _ in T})
    write(os.path.join(LANG, "en_us.json"), {k: e for k, _, e in T})
    for name, r in RECIPES.items():
        write(os.path.join(RECIPE, name + ".json"), r)
    print("wrote %d texts, %d recipes" % (len(T), len(RECIPES)))


if __name__ == "__main__":
    main()
