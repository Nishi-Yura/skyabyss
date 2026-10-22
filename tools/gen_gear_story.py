"""装備のツールチップの「物語の一行」(灰色の斜体。巨神の声で、短く) の文を書く。docs/design/03 の 11章 (旧 17-8、00 の 181)。

  src/main/resources/assets/skyabyss_gear_story/lang/{ja_jp,en_us}.json   : item.skyabyss.<id>.story (装備 48 点。防具は部位ごと)

ツールチップの組み立ては item/ModGear.tooltip (説明の次の行に、このキーがあれば出す)。キーがない品には何も出さない。
自己テスト (SELFTEST gear curve) が、登録された装備のすべてにこの行が日英であること、長さの上限を確かめる。

使い方 (リポジトリ直下): python tools/gen_gear_story.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss_gear_story/lang"

# 巨神ごとの声。岩鯨 = 遅く低く背の者を抱く / 潮殻 = 引いては満ちるささやき / 雷翼 = 風と羽音、速く高い / 余燼 = ゆっくり冷める熱、灰
LINES = [
    # ---- 岩鯨
    ("whalebone_greatsword", "「重いのは、背負ったものの数だ。」", "\"It is heavy by the count of what it has carried.\""),
    ("stone_whale_helmet", "「雲の上でも、頭は静かにしておけ。」", "\"Keep your head quiet, even above the clouds.\""),
    ("stone_whale_chestplate", "「私の肋は、おまえを数えている。」", "\"My ribs are counting you.\""),
    ("stone_whale_leggings", "「歩みは遅くていい。落ちなければ。」", "\"Walk slowly. Only do not fall.\""),
    ("stone_whale_boots", "「背の上の道は、足が覚えている。」", "\"Your feet remember the road on my back.\""),
    ("resonance_charm", "「傷は、歌で塞ぐ。昔からそうだ。」", "\"Wounds close with song. They always have.\""),
    ("captain_pike", "「番をするのは、眠れなかった者だ。」", "\"Only those who could not sleep stand guard.\""),
    ("waterway_fang", "「水の底にも道はある。長くは持たぬ。」", "\"There is a road under the water too. It does not last.\""),
    ("stone_whale_plating", "「殻を足せば、重さが増すだけだ。それでも。」", "\"More shell is only more weight. Even so.\""),
    ("moss_talisman", "「苔は、遅いものにだけ根づく。」", "\"Moss only takes root on the slow.\""),
    # ---- 潮殻
    ("tide_shell_helmet", "「潮の音が止む前に、被れ。」", "\"Put it on before the tide's sound stops.\""),
    ("tide_shell_chestplate", "「満ちるときは、殻の内側にいろ。」", "\"When the water rises, stay inside the shell.\""),
    ("tide_shell_leggings", "「足首まで海なら、まだ戻れる。」", "\"If the sea is only at your ankles, you can still turn back.\""),
    ("tide_shell_boots", "「引いた砂は、すぐ道を忘れる。」", "\"Ebbed sand forgets its roads quickly.\""),
    ("tide_shell_trident", "「突いた水は、同じ形に戻る。」", "\"Water you pierce returns to the same shape.\""),
    ("coral_shield", "「棘は、守りたい者のために生えた。」", "\"The thorns grew for those it wanted to keep.\""),
    ("tide_chime_charm", "「鳴らせ。海は、呼ばれるのを待っている。」", "\"Ring it. The sea is waiting to be called.\""),
    ("tidecaller_claw", "「潮は、爪の届くだけ招ける。」", "\"You can call the tide only as far as the claw reaches.\""),
    ("coral_harpoon", "「珊瑚は、ゆっくり人を忘れさせる。」", "\"Coral makes one forget, slowly.\""),
    ("moontide_pearl", "「月が引くものを、泡が覚えている。」", "\"The bubbles remember what the moon pulls away.\""),
    # ---- 雷翼
    ("thunder_wing_helmet", "「風の声が変わったら、伏せろ。」", "\"When the wind's voice changes, get down.\""),
    ("thunder_wing_chestplate", "「羽は雷を恐れない。落ちるだけだ。」", "\"A feather does not fear lightning. It only falls.\""),
    ("thunder_wing_leggings", "「飛べぬ者の足にも、風は触れる。」", "\"The wind touches the legs of those who cannot fly.\""),
    ("thunder_wing_boots", "「地を蹴るたび、空が一寸近づく。」", "\"Each kick of the ground brings the sky an inch closer.\""),
    ("thunder_wing_bow", "「矢は、翼の骨から生まれた。」", "\"Arrows were born from the bones of a wing.\""),
    ("storm_anklet", "「巡りの輪。落ちる者に、風を貸す。」", "\"A ring of the cycle. It lends wind to the falling.\""),
    ("storm_charm", "「雷は、呼ばれた数だけ落ちる。」", "\"Lightning falls as many times as it is called.\""),
    ("raiju_claw", "「雷獣は、獲物を数えるのが速い。」", "\"The thunder beast counts its prey quickly.\""),
    ("warden_glaive", "「羽守は、落ちるものを見逃さない。」", "\"The feather wardens never miss what falls.\""),
    ("storm_eye_feather", "「嵐の目は、いつもこちらを見ている。」", "\"The eye of the storm is always looking back.\""),
    # ---- 余燼
    ("ember_helmet", "「熱は、頭から先に逃げていく。」", "\"Heat leaves through the head first.\""),
    ("ember_chestplate", "「胸の火は、消えたのではない。眠った。」", "\"The fire in the chest is not out. It sleeps.\""),
    ("ember_leggings", "「灰の上は、思ったより歩ける。」", "\"Ash is easier to walk on than you think.\""),
    ("ember_boots", "「冷めた足跡にも、赤い芯が残る。」", "\"Even cold footprints keep a red core.\""),
    ("ember_war_axe", "「燃やすのではない。くすぶらせるのだ。」", "\"Not to burn. To smolder.\""),
    ("ember_mantle", "「裏地には、まだ私の体温がある。」", "\"My warmth is still in the lining.\""),
    ("kindling_charm", "「吸った熱は、いつか誰かに返る。」", "\"The heat you take will come back to someone.\""),
    ("smith_hammer", "「鍛え手は、最後に自分を打った。」", "\"The smith struck himself last.\""),
    ("thrall_poker", "「灰の番人は、火掻きだけを手放さなかった。」", "\"The ash thralls let go of everything but the poker.\""),
    ("ember_lantern", "「灯は、倒れる前に一度だけ吠える。」", "\"A lantern roars once, before it falls.\""),
    # ---- 鼓動守 (21 の 1.4。guide/HeartbeatWard)
    ("heartbeat_ward_stone_whale", "「鼓動はひとつ、おまえのために打ち残してある。」", "\"I kept one beat back, for you.\""),
    ("heartbeat_ward_tide_shell", "「引いた波も、一度だけは引き返す。」", "\"Even the ebb turns back, once.\""),
    ("heartbeat_ward_thunder_wing", "「雷は、落ちる前にためらうことがある。」", "\"Lightning will hesitate, now and then, before it falls.\""),
    ("heartbeat_ward_ember", "「灰の下には、息ひとつぶんの火がいつも残る。」", "\"Under the ash, one breath of fire always stays.\""),
    # ---- 旅装 (一章の橋渡し。21 の 1.2・P5。巨神の声ではなく、道を歩いた人の声)
    ("whale_road_helmet", "「帽子は、空より先に雨を覚える。」", "\"A cap learns the rain before it learns the sky.\""),
    ("whale_road_chestplate", "「肩の殻は、誰かの雨を受けてきた。」", "\"The shell on the shoulder has taken someone else's rain.\""),
    ("whale_road_leggings", "「歩いた道の数だけ、裾が薄くなる。」", "\"The hem wears thin by the count of roads walked.\""),
    ("whale_road_boots", "「靴底は、道の石を覚えている。」", "\"A sole remembers the stones of the road.\""),
]


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    write("ja_jp.json", {f"item.skyabyss.{i}.story": ja for i, ja, _ in LINES})
    write("en_us.json", {f"item.skyabyss.{i}.story": en for i, _, en in LINES})
    print(f"{len(LINES)} gear story lines written")


if __name__ == "__main__":
    main()
