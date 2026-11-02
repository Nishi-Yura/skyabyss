#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""六章「月」の伝承の頁 (docs/design/46 の 4.9) を書く。データ (data/skyabyss/lore/entry/ch6/) と文 (assets/skyabyss_moon/lang の lore.skyabyss.ch6.*)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_moon_lore.py

  memory_1〜12     前の自分の記憶の断片 (区画ごとに 前の自分と話す・記憶の石・区画の主。story/ch6/MoonMemories)
  watcher_truth    十二がそろうと: 見る者の正体
  core_reason      月の核を止めて語りを見終えると: 核が時間を閉じた理由
  keepsake_<主>    位相の挑戦の段 5 を初めて越えると: 記念の銀の札
本文は 2〜4 段。説明しすぎず、物か問いで終える (05 の書き方)。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "skyabyss", "lore", "entry", "ch6")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_moon", "lang")

# (id, 題 ja, 本文 ja, 題 en, 本文 en)
ENTRIES = [
    ("memory_1", "記憶の断片（灯りの谷・一）",
     "月に着いたとき、野営の火はもう灯っていた。\n\n誰が灯したのかは分からない。薪の組み方は、私のと同じだった。\n\n火のそばに座って、地上を見上げた。丘の上に、小さな人影が一つ。",
     "Memory Fragment (Valley of Lamps, I)",
     "When I reached the moon, the campfire was already lit.\n\nI never learned who lit it. The wood was stacked the way I stack it.\n\nI sat by the fire and looked up at the ground. On a hill, a small figure."),
    ("memory_2", "記憶の断片（灯りの谷・二）",
     "穴の底の石に、私の字で刻んである。\n\n「七度目。台地の上で、また止まった」\n\n刻みはまだ続いている。八、九、と。数えたのは私なのに、覚えているのは七までだ。",
     "Memory Fragment (Valley of Lamps, II)",
     "A stone at the bottom of the hole, carved in my hand.\n\n\"Seventh time. Stopped on the plateau again.\"\n\nThe carving goes on. Eight, nine. I did the counting, yet I remember only up to seven."),
    ("memory_3", "記憶の断片（灯りの谷・三）",
     "影抱きに捕まったとき、月が落ちた。\n\n地上の時間だけが巻き戻っていくのが、ここから見えた。私の体は、巻き戻らなかった。\n\n谷に残った。次に来る私のために、火を灯しておくことにした。",
     "Memory Fragment (Valley of Lamps, III)",
     "The moon fell while the Shadow Embracer held me.\n\nFrom here I watched the ground's time wind back. My body did not wind back with it.\n\nI stayed in the valley. I decided to keep the fire lit for the next me."),
    ("memory_4", "記憶の断片（上弦の崖・一）",
     "崖は何度でも落ちられる。月の上では、落ちても死なない。\n\nだから何度も登った。登るたびに、少しずつ軽くなった。\n\n軽くなったのは体だけではなかった。名前を、どこかに落としてきた。",
     "Memory Fragment (Waxing Cliff, I)",
     "You can fall from the cliff as often as you like. On the moon, falling does not kill you.\n\nSo I climbed again and again. Each climb, I grew a little lighter.\n\nIt was not only my body. Somewhere along the way I dropped my name."),
    ("memory_5", "記憶の断片（上弦の崖・二）",
     "張り出しの端から、地上の大陸がよく見える。\n\n夜の丘に、旅の者が一人。こちらを見上げている。\n\n目が合った気がした。向こうの私は、すぐに目をそらした。あの頃の私も、そうした。",
     "Memory Fragment (Waxing Cliff, II)",
     "From the edge of the ledge, the continent below is plain to see.\n\nOn a hill at night, a traveler. Looking up at me.\n\nI thought our eyes met. The one below looked away at once. I did the same, back then."),
    ("memory_6", "記憶の断片（上弦の崖・三）",
     "月秤は、過ぎた時と来る時を量っていた。\n\n巻き戻るとき、皿は軽いほうへ傾く。重いものだけが、先へ運ばれる。\n\n私は軽すぎた。皿の上に、残された。",
     "Memory Fragment (Waxing Cliff, III)",
     "The Moon Scale weighed the time gone against the time to come.\n\nWhen time winds back, the pans tip toward the lighter side. Only the heavy things are carried forward.\n\nI was too light. I was left on the pan."),
    ("memory_7", "記憶の断片（満月の野・一）",
     "満ち引きを数えるのは、時計がないからではない。\n\n巻き戻るたびに、数が合わなくなる。合わない数だけ、世界が戻った。\n\n今は二百と十二。いや、十一か。",
     "Memory Fragment (Full Moon Field, I)",
     "I do not count the tides for want of a clock.\n\nEach time it winds back, the count stops adding up. The difference is how far the world went back.\n\nTwo hundred and twelve now. Or eleven."),
    ("memory_8", "記憶の断片（満月の野・二）",
     "石柱の陰で、砂時計の砂を手にすくった。\n\n一粒ずつが、止まった一瞬だった。地上の誰かの、言いかけた言葉。閉じかけた扉。\n\n砂は指のあいだから落ちなかった。月の上では、時も軽い。",
     "Memory Fragment (Full Moon Field, II)",
     "In a pillar's shadow I scooped up sand from the hourglass.\n\nEach grain was a stopped moment. Someone's half-spoken word below. A door half closed.\n\nThe sand did not fall through my fingers. On the moon, even time is light."),
    ("memory_9", "記憶の断片（満月の野・三）",
     "望の灯守の灯りには、これまでの満月が映っていた。\n\nどの満月も、最後は落ちてくる。地上の鐘が鳴り、空が割れ、そして全部が始めに戻る。\n\n一つだけ、落ちなかった満月があった。それは、まだ来ていない。",
     "Memory Fragment (Full Moon Field, III)",
     "The Lamp Keeper's light held every full moon so far.\n\nEvery one of them falls in the end. The bells ring below, the sky splits, and everything returns to the start.\n\nOne full moon did not fall. That one has not come yet."),
    ("memory_10", "記憶の断片（下弦の淵・一）",
     "いちばん初めは、一人ではなかった。\n\n灯りを持つ者、綱を結ぶ者、数える者、鏡を持つ者。皆で月に渡った。\n\n巻き戻るたびに、皆は少しずつ私になった。残ったのは、私たちだけだ。",
     "Memory Fragment (Waning Rift, I)",
     "At the very first, I was not alone.\n\nOne carried the lamp, one tied the ropes, one counted, one held the mirror. We crossed to the moon together.\n\nEach rewind, they became a little more like me. Only we are left."),
    ("memory_11", "記憶の断片（下弦の淵・二）",
     "淵の鏡は、映った者の数秒前を覚えている。\n\n巻き戻るたびに、月に残った私たちの姿も、どこかへ映し出された。\n\n映った先は、地上の夜の丘だった。",
     "Memory Fragment (Waning Rift, II)",
     "The rift's mirrors remember whoever they reflect, a few seconds behind.\n\nEach rewind, the image of us left on the moon was cast somewhere too.\n\nIt was cast onto the hills of the ground, at night."),
    ("memory_12", "記憶の断片（下弦の淵・三）",
     "核は、落ちる月を止められない。止めるのは、時間だけだ。\n\n残月の剣守は、そう言って剣を下ろした。核の間への道を、ずっと守っていた。\n\n核を止めれば、巻き戻りは終わる。次の私は、もう来ない。",
     "Memory Fragment (Waning Rift, III)",
     "The core cannot stop the falling moon. It stops only time.\n\nThe Waning Blade Keeper said so and lowered its sword. It had guarded the way to the core all along.\n\nStop the core and the rewinding ends. No next me will come."),
    ("watcher_truth", "見る者",
     "夜の丘に立ち、目を合わせると消える影。村の年寄りが「見る者」と呼んだもの。\n\nあれは、月に残された私たちだった。巻き戻るたびに淵の鏡が映し出した、前の私の残像。\n\n何もしてこなかったのは、何もできなかったからだ。見ることしか、残っていなかった。\n\n目をそらしたのは、私のほうだった。",
     "The Watcher",
     "The shadow that stands on the night hills and vanishes when you meet its eyes. What the village elders called the Watcher.\n\nIt was us, left on the moon. Afterimages of the earlier me, cast down by the rift's mirrors each time the world wound back.\n\nIt did nothing because it could do nothing. Watching was all that remained.\n\nI was the one who looked away."),
    ("core_reason", "月の核",
     "そらの　まるい　ふた。数え歌のむっつめ。\n\n核は、月が落ちるたびに世界の物語を始めに戻してきた。落ちる月を、なかったことにするために。空の上から降りてくるものを、蓋の外に置いておくために。\n\n核は止まった。もう巻き戻らない。\n\n蓋は、外れた。",
     "The Moon's Core",
     "A round lid on the sky. The sixth line of the counting song.\n\nEach time the moon fell, the core returned the world's story to its start. To make the fall never have happened. To keep what descends from above outside the lid.\n\nThe core has stopped. Nothing will wind back now.\n\nThe lid is off."),
    ("keepsake_valley", "銀の札（新月の影抱き）",
     "位相の挑戦で、五段の影抱きを越えた記念の銀の札。\n\n裏に小さく「灯りは、暗がりの中でこそ」と刻んである。",
     "Silver Tag (Shadow Embracer of the New Moon)",
     "A silver tag for besting the Shadow Embracer at the fifth tier of the phase trials.\n\nOn the back, small letters: \"A lamp matters most in the dark.\""),
    ("keepsake_cliff", "銀の札（上弦の月秤）",
     "位相の挑戦で、五段の月秤を越えた記念の銀の札。\n\n片側だけが少し重い。どちらが重いかは、持つたびに変わる。",
     "Silver Tag (Moon Scale of the First Quarter)",
     "A silver tag for besting the Moon Scale at the fifth tier of the phase trials.\n\nOne side is slightly heavier. Which side changes each time you hold it."),
    ("keepsake_field", "銀の札（満月の望の灯守）",
     "位相の挑戦で、五段の望の灯守を越えた記念の銀の札。\n\n満ちのあいだだけ、縁がほのかに光る。",
     "Silver Tag (Lamp Keeper of the Full Moon)",
     "A silver tag for besting the Lamp Keeper at the fifth tier of the phase trials.\n\nIts rim glows faintly, but only while the tide is full."),
    ("keepsake_rift", "銀の札（下弦の残月の剣守）",
     "位相の挑戦で、五段の残月の剣守を越えた記念の銀の札。\n\n鏡に映すと、札は二枚に見える。二枚目は、少し遅れて揺れる。",
     "Silver Tag (Blade Keeper of the Waning Moon)",
     "A silver tag for besting the Blade Keeper at the fifth tier of the phase trials.\n\nHeld to a mirror, it looks like two. The second one sways a moment late."),
    ("keepsake_core", "銀の札（月の核）",
     "位相の挑戦で、五段の月の核を越えた記念の銀の札。\n\n耳に当てると、止まったはずの時計の音が一つだけ聞こえる。",
     "Silver Tag (The Moon's Core)",
     "A silver tag for besting the Moon's Core at the fifth tier of the phase trials.\n\nHold it to your ear and you hear a single tick from a clock that should have stopped."),
]


def main():
    os.makedirs(DATA, exist_ok=True)
    for order, (eid, *_rest) in enumerate(ENTRIES):
        related = ["ch6/watcher_truth"] if eid.startswith("memory_") else []
        with open(os.path.join(DATA, eid + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"chapter": "chapter3", "order": 700 + order, "unlock": [{"type": "manual"}], "related": related}, f, ensure_ascii=False, indent=2)
            f.write("\n")
    for loc, ti, bi in (("ja_jp", 1, 2), ("en_us", 3, 4)):
        p = os.path.join(LANG, loc + ".json")
        d = json.load(open(p, encoding="utf-8"))
        for e in ENTRIES:
            d["lore.skyabyss.ch6.%s.title" % e[0]] = e[ti]
            d["lore.skyabyss.ch6.%s.body" % e[0]] = e[bi]
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
    print("wrote", len(ENTRIES), "entries")


if __name__ == "__main__":
    main()
