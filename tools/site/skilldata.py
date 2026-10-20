"""技の一覧。木の形・段・前提は Skill.java から、名前は lang (日英) から読み、一言の説明だけここに書く。"""
import json
import os
import re

from sitelib import ROOT

JAVA = os.path.join(ROOT, "src", "main", "java", "jp", "revati", "skyabyss", "rpg", "skill", "Skill.java")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "lang")

TREES = ["rock", "wind", "tide", "ash", "bone", "traveler"]
KIND = {"ACTIVE": "A", "PASSIVE": "P", "KEYSTONE": "K"}

DESC = {
    "titan_cleave": ("前方の扇を斬る。大きな相手に強い", "Cone slash ahead; hits big foes harder."),
    "riposte": ("傷を受けた直後の近接が強くなる", "Your next melee hit after taking damage is stronger."),
    "rock_call": ("周りの敵の狙いを自分へ引く", "Pull nearby enemies' aggro onto you."),
    "stone_stance": ("防具と押されにくさが上がる", "More armor and knockback resistance."),
    "whale_sweep": ("周りを薙ぎ払い、軽く押し出す", "A sweeping blow around you with a light shove."),
    "stone_oath": ("近くの仲間が受ける傷の一部を自分が受ける", "Take a share of nearby allies' damage."),
    "lunge": ("見ている敵の手前まで詰めて斬る", "Dash to the foe you look at and cut."),
    "nimble": ("移動が少し速く、落下の傷が減る", "Slightly faster on foot; less fall damage."),
    "backstep": ("後ろへ素早く下がる", "Hop back about four blocks."),
    "foresight": ("予告をかわした直後、攻撃が速くなる", "Attack faster right after dodging a floor telegraph."),
    "wind_rend": ("素早い二連。背後から当てると強い", "Quick double strike; stronger from behind."),
    "lone_wind": ("近くに他の人がいない間、攻撃と移動が上がる", "Stronger attack and speed while no one is nearby."),
    "ward_song": ("仲間に短い吸収ハートを与える", "Gives allies a short absorption shield."),
    "calm_heart": ("自分と仲間のマナの回復が上がる", "Faster mana recovery for you and allies."),
    "ebb_tide": ("前方の敵を足もとへ引き寄せて鈍らせる", "Pull foes ahead to your feet and slow them."),
    "ripple": ("技を当てた敵の動きを鈍らせる", "Enemies your skills hit move slower."),
    "cycle_song": ("仲間のマナの回復を、しばらく大きく上げる", "Greatly boosts allies' mana recovery for a while."),
    "chorus_voice": ("近くの仲間が多いほど技が強くなる", "Skills grow stronger with more allies nearby."),
    "temper": ("手の武器を熾にして炎を乗せる", "Set your weapon aglow with extra fire damage."),
    "ember_heart": ("火の傷が減り、熾火に乗っても燃えない", "Less fire damage; ember ground cannot burn you."),
    "ember_hammer": ("叩きつけて地に熾火を残す", "Slam the ground and leave burning embers."),
    "forge_memory": ("技を使うほど、次の近接が強くなる", "Each skill use charges your next melee hit."),
    "ash_scatter": ("周りの敵に灰をかぶせて弱らせる", "Cover nearby foes in ash to weaken them."),
    "smith_eye": ("武器の強化段階が、技の傷に響く", "Your weapon's enhancement level boosts skill damage."),
    "cloud_pierce": ("まっすぐ貫く矢", "A piercing arrow in a straight line."),
    "far_shot": ("距離が離れるほど飛び道具が強い", "Ranged damage grows with distance."),
    "bone_trap": ("足もとに罠を置き、踏んだ敵を縛る", "Set traps that bind whoever steps on them."),
    "hawk_eye": ("当てた敵に印を付け、皆の攻撃が強くなる", "Mark foes you hit so everyone deals more to them."),
    "sky_rain": ("狙った所へ光の矢を降らせる", "Rain light arrows onto the aimed spot."),
    "still_string": ("動かずに引いた矢が強くなる", "Arrows drawn while standing still hit harder."),
    "signal": ("見ている先に光の標を立てて、仲間に知らせる", "Plant a light beacon that all allies can see."),
    "readiness_reward": ("備えの三灯が満ちているとき、少し強くなる", "A small bonus while your readiness lamps are full."),
    "camp_hand": ("焚き火の近くでマナが速く戻り、満腹が減りにくい", "Faster mana and slower hunger near campfires."),
    "appraiser": ("再鍛造の結晶が減り、特性の幅が見える", "Cheaper reforging; affix ranges become visible."),
    "fleet_foot": ("体力が少ないとき、短く速く走れる", "A short burst of speed when badly hurt."),
    "journal": ("戦いの外なら、どこでも刻印と型を切り替えられる", "Swap runes and loadouts anywhere outside combat."),
}

TREE_META = {
    "rock": ("筋力・耐久", "壁・近接", "Strength / Vitality", "Tank / melee", "img/item/whalebone_greatsword.png"),
    "wind": ("敏捷", "機動・短い間合いの攻め", "Agility", "Mobility / close offense", "img/item/waterway_fang.png"),
    "tide": ("精神", "支え・崩し", "Spirit", "Support / disruption", "img/item/tide_shell_trident.png"),
    "ash": ("筋力・精神", "近接の攻め・火", "Strength / Spirit", "Melee offense / fire", "img/item/smith_hammer.png"),
    "bone": ("敏捷", "遠い間合いの攻め・罠", "Agility", "Ranged offense / traps", "img/item/thunder_wing_bow.png"),
    "traveler": ("全員", "共通の木（旅の便利）", "Everyone", "Shared utility tree", "img/item/wayfarer_notes.png"),
}


def load():
    ja = json.load(open(os.path.join(LANG, "ja_jp.json"), encoding="utf8"))
    en = json.load(open(os.path.join(LANG, "en_us.json"), encoding="utf8"))
    src = open(JAVA, encoding="utf8").read()
    skills = []
    pat = re.compile(r'^\s+([A-Z_]+)\("([a-z_]+)",\s*SkillTree\.([A-Z]+),\s*Kind\.([A-Z]+),\s*(\d+),\s*(\d+),\s*(\d+)((?:,\s*"[a-z_]+")*)\)', re.M)
    for m in pat.finditer(src):
        _, sid, tree, kind, rank, row, col, pre = m.groups()
        if tree.lower() not in TREES:
            continue  # 橋・淵・前世の技は別の表 (pages_d) で説明する
        skills.append(dict(id=sid, tree=tree.lower(), kind=KIND[kind], rank=int(rank), row=int(row), col=int(col),
                           pre=re.findall(r'"([a-z_]+)"', pre)))
    assert len(skills) == 36, len(skills)
    for i, s in enumerate(skills):
        s["idx"] = i
        s["ja"] = ja["skill.skyabyss." + s["id"]]
        s["en"] = en["skill.skyabyss." + s["id"]]
        s["dja"], s["den"] = DESC[s["id"]]
    tn = {}
    for t in TREES:
        tn[t] = (ja["skilltree.skyabyss." + t], en["skilltree.skyabyss." + t])
    return skills, tn


if __name__ == "__main__":
    sk, tn = load()
    print(tn)
    for s in sk[:8]:
        print(s["idx"], s["id"], s["tree"], s["kind"], s["rank"], s["row"], s["col"], s["pre"])
