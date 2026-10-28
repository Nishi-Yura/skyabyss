# -*- coding: utf-8 -*-
"""技の一覧。木の形・段・前提は Skill.java から、名前は lang (日英) から読み、一言の説明だけここに書く。

職 (docs/design/43。6 職。持った武器で職が決まる): 戦士 = 岩守り rock、鍛冶師 = 灰の鍛え人 ash、魔法使い = 星読み star、
弓使い = 骨の射手 bone、暗殺者 = 風読み wind、呪術師 = 潮歌い tide。旅人・橋の木は削った (43 の 3 章)。
淵・前世の技 (転生の報い) は pages_d で説明する。アイコンの番号は Skill.iconIndex() と同じ (36 未満は icons.png、125〜 は icons2.png)。"""
import json
import os
import re

from sitelib import ROOT

JAVA = os.path.join(ROOT, "src", "main", "java", "jp", "revati", "skyabyss", "rpg", "skill", "Skill.java")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "lang")

# 職の順 (43 の 2 章の表)
TREES = ["rock", "ash", "star", "bone", "wind", "tide"]
JOBS = {"rock": "warrior", "ash": "smith", "star": "mage", "bone": "archer", "wind": "assassin", "tide": "shaman"}
KIND = {"ACTIVE": "A", "PASSIVE": "P", "KEYSTONE": "K"}

DESC = {
    "titan_cleave": ("前方の扇を斬る。大きな相手に強い", "Cone slash ahead; hits big foes harder."),
    "riposte": ("傷を受けた直後の近接が強くなる", "Your next melee hit after taking damage is stronger."),
    "rock_call": ("周りの敵の狙いを自分へ引く", "Pull nearby enemies' aggro onto you."),
    "stone_stance": ("防具と押されにくさが上がる", "More armor and knockback resistance."),
    "whale_sweep": ("周りを薙ぎ払い、軽く押し出す", "A sweeping blow around you with a light shove."),
    "shield_rush": ("見ている敵の手前まで一気に詰めて打つ。地に足が着いているときだけ", "Rush to the foe you look at and strike; only while on the ground."),
    "shield_bash": ("前の敵を盾で突いて押し出し、足を鈍らせる", "Ram foes ahead with the shield; pushes and slows."),
    "bone_swing": ("見ている 1 体に重い一撃。大きな相手に強い", "One heavy blow on the foe you look at; strong against large foes."),
    "spin_cut": ("まわりを薙ぎながら前へ進む", "Sweep around you while dashing forward."),
    "twin_fang": ("見ている敵に二度刺す。背後から強い", "Two quick stabs; stronger from behind."),
    "spark_burst": ("まわりの敵に傷と炎", "Damage and burn foes around you."),
    "snare_shot": ("見ている 1 体に矢の傷と鈍足", "Arrow damage and a slow on the foe you look at."),
    "stardust": ("まわりの敵に魔法の傷を降らせる", "Rain spell damage on foes around you."),
    "stone_oath": ("近くの仲間が受ける傷の一部を自分が受ける", "Take a share of nearby allies' damage."),
    "lunge": ("見ている敵の手前まで詰めて斬る", "Dash to the foe you look at and cut."),
    "nimble": ("移動が少し速く、落下の傷が減る", "Slightly faster on foot; less fall damage."),
    "backstep": ("後ろへ素早く下がる", "Hop back about four blocks."),
    "foresight": ("予告をかわした直後、攻撃が速くなる", "Attack faster right after dodging a floor telegraph."),
    "wind_rend": ("素早い二連。背後から当てると強い", "Quick double strike; stronger from behind."),
    "mist_veil": ("しばらく姿を消して速くなる。消えている間の最初の一撃が強い", "Vanish for a while and move faster; the first strike out of hiding hits hard."),
    "lone_wind": ("近くに他の人がいない間、攻撃と移動が上がる", "Stronger attack and speed while no one is nearby."),
    "ward_song": ("仲間に短い吸収ハートを与える", "Gives allies a short absorption shield."),
    "calm_heart": ("自分と仲間のマナの回復が上がる", "Faster mana recovery for you and allies."),
    "ebb_tide": ("前方の敵を足もとへ引き寄せて鈍らせる", "Pull foes ahead to your feet and slow them."),
    "ripple": ("技を当てた敵の動きを鈍らせる", "Enemies your skills hit move slower."),
    "cycle_song": ("仲間のマナの回復を、しばらく大きく上げる", "Greatly boosts allies' mana recovery for a while."),
    "curse_mark": ("見ている敵に印を付ける。印の敵は誰の攻撃でも傷つきやすく、攻撃が弱る", "Mark the foe you look at: it takes more from everyone and hits softer."),
    "lamp_charm": ("足もとに灯を置き、そばの自分と仲間の護りの体力を満たし続ける", "Set a lamp at your feet that keeps topping up nearby allies' absorption."),
    "chorus_voice": ("近くの仲間が多いほど技が強くなる", "Skills grow stronger with more allies nearby."),
    "temper": ("手の武器を熾にして炎を乗せる", "Set your weapon aglow with extra fire damage."),
    "ember_heart": ("火の傷が減り、熾火に乗っても燃えない", "Less fire damage; ember ground cannot burn you."),
    "ember_hammer": ("叩きつけて地に熾火を残す", "Slam the ground and leave burning embers."),
    "forge_memory": ("技を使うほど、次の近接が強くなる", "Each skill use charges your next melee hit."),
    "ash_scatter": ("周りの敵に灰をかぶせて弱らせる", "Cover nearby foes in ash to weaken them."),
    "forge_stomp": ("足もとを踏み鳴らし、まわりに火の波。小さな敵は浮く", "Stomp and send out a wave of fire; small foes are lifted."),
    "smith_eye": ("武器の強化段階が、技の傷に響く", "Your weapon's enhancement level boosts skill damage."),
    "cloud_pierce": ("まっすぐ貫く矢", "A piercing arrow in a straight line."),
    "far_shot": ("距離が離れるほど飛び道具が強い", "Ranged damage grows with distance."),
    "bone_trap": ("足もとに罠を置き、踏んだ敵を縛る", "Set traps that bind whoever steps on them."),
    "hawk_eye": ("当てた敵に印を付け、皆の攻撃が強くなる", "Mark foes you hit so everyone deals more to them."),
    "sky_rain": ("狙った所へ光の矢を降らせる", "Rain light arrows onto the aimed spot."),
    "archer_leap": ("近くに敵がいるとき、後ろへ軽く跳ぶ", "A light hop backward while a foe is close."),
    "still_string": ("動かずに引いた矢が強くなる", "Arrows drawn while standing still hit harder."),
    "thunder_bolt": ("見ている向きへ稲妻を放つ。極めると近くの敵へ跳ねる", "Cast a bolt of lightning ahead; at max rank it chains to a nearby foe."),
    "star_reading": ("杖を持っている間、マナの戻りが速い", "Mana returns faster while you hold a staff."),
    "ember_orb": ("ゆっくり飛ぶ火の玉。当たると弾けて燃やす", "A slow fireball that bursts and burns where it lands."),
    "frost_tide": ("自分のまわりに冷たい潮。敵を傷つけて鈍らせる", "A cold tide around you that hurts and slows foes."),
    "blink": ("見ている向きへ跳ぶ。落ちた高さは消える", "Teleport ahead; fall height is cleared."),
    "star_oath": ("星読みの技の傷が上がる。そのかわりマナを多く使う", "Stargazer skills hit harder but cost more mana."),
}

# 職ごとの見出し: (武器 ja, 役目 ja, 武器 en, 役目 en, 絵)。絵は島の祠で受け取る始めの武器
TREE_META = {
    "rock": ("剣・大剣・鉾・槍・盾", "壁。狙いを引き、仲間の前に立つ", "Sword, greatsword, glaive, spear, shield", "The wall: draw aggro and stand in front", "img/item/shrine_sword.png"),
    "ash": ("鎚・斧", "火の重い一撃", "Hammer, axe", "Heavy blows wreathed in fire", "img/item/shrine_hammer.png"),
    "star": ("杖", "遠い魔法の攻め（雷・火・潮）", "Staff", "Ranged magic: lightning, fire and tide", "img/item/shrine_staff.png"),
    "bone": ("弓・クロスボウ", "遠い攻めと罠", "Bow, crossbow", "Ranged offense and traps", "img/item/shrine_bow.png"),
    "wind": ("短剣・牙・爪", "速い攻め。背後から", "Dagger, fang, claw", "Fast offense from behind", "img/item/shrine_dagger.png"),
    "tide": ("鈴・祭具", "支えと崩し（回復はない）", "Bell, ritual tools", "Support and disruption (no healing)", "img/item/shrine_bell_staff.png"),
}


def load():
    ja = json.load(open(os.path.join(LANG, "ja_jp.json"), encoding="utf8"))
    en = json.load(open(os.path.join(LANG, "en_us.json"), encoding="utf8"))
    src = open(JAVA, encoding="utf8").read()
    skills = []
    pat = re.compile(r'^\s+([A-Z_]+)\("([a-z_]+)",\s*SkillTree\.([A-Z]+),\s*Kind\.([A-Z]+),\s*(\d+),\s*(\d+),\s*(\d+)((?:,\s*"[a-z_]+")*)\)', re.M)
    ordinal = 0
    first_star = None
    for m in pat.finditer(src):
        name, sid, tree, kind, rank, row, col, pre = m.groups()
        if name == "THUNDER_BOLT":
            first_star = ordinal
        if tree.lower() in TREES:
            skills.append(dict(id=sid, tree=tree.lower(), kind=KIND[kind], rank=int(rank), row=int(row), col=int(col),
                               pre=re.findall(r'"([a-z_]+)"', pre), ordinal=ordinal))
        ordinal += 1
    assert first_star is not None
    for i, s in enumerate(skills):
        s["idx"] = i
        o = s["ordinal"]
        # Skill.iconIndex() と同じ
        s["icon"] = o if o < 36 else 125 + (o - first_star)
        s["ja"] = ja["skill.skyabyss." + s["id"]]
        s["en"] = en["skill.skyabyss." + s["id"]]
        s["dja"], s["den"] = DESC[s["id"]]
    tn = {}
    for t in TREES:
        tn[t] = (ja["skilltree.skyabyss." + t], en["skilltree.skyabyss." + t])
    jn = {}
    for t, j in JOBS.items():
        jn[t] = (ja["job.skyabyss." + j], en["job.skyabyss." + j], ja["job.skyabyss." + j + ".role"], en["job.skyabyss." + j + ".role"])
    return skills, tn, jn


if __name__ == "__main__":
    sk, tn, jn = load()
    print(tn, jn)
    for s in sk:
        print(s["idx"], s["icon"], s["id"], s["tree"], s["kind"], s["rank"], s["row"], s["col"], s["pre"])
