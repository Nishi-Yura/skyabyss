#!/usr/bin/env python3
"""技の稽古 (都の訓練場の師範が付ける手ほどき) のクエスト・会話・文を書く。

  python tools/gen/story/gen_skill_lesson.py

書くもの (毎回すべて書き直す。手で直さないこと):
  data/skyabyss/quest/lesson/skills.json                 一章の稽古 (会う → 覚えて置く → 木偶に当てる → 背後から → 構え → 振り直し)
  data/skyabyss/quest/lesson/<名前>.json                 後の章の一段だけの稽古 (新しい木・要が開いたとき。tutorial.SkillLesson が始める)
  data/skyabyss/dialogue/attendant_training_master_lesson.json   師範の会話の足し書き ("extends": true)
  assets/skyabyss_lesson/lang/{ja_jp,en_us}.json         文 (クエスト・会話・師範の一言・画面の案内)
仕組みは tutorial.SkillLesson (合図・木偶の反応・報酬・飛ばす/もう一度) と client.tutorial.SkillLessonClient (案内の行・枠の光)。
合図: skill_lesson:slotted (枠に技がある) / skill_lesson:dummy (技が木偶に当たった。一回の使用につき一つ) /
      skill_lesson:behind (木偶を背後から打った) / skill_lesson:stance (構えを変えた) /
      skill_lesson:tree:<木> (その木の技が木偶か敵に当たった) / skill_lesson:keystone (要を覚えた)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RES = os.path.join(ROOT, "src", "main", "resources")
QUEST = os.path.join(RES, "data", "skyabyss", "quest", "lesson")
DIALOGUE = os.path.join(RES, "data", "skyabyss", "dialogue")
LANG = os.path.join(RES, "assets", "skyabyss_lesson", "lang")

MASTER = "skyabyss:attendant_training_master"
LESSON = "lesson/skills"

JA = {}
EN = {}


def t(key, ja, en):
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


def say(line, ja, en):
    """師範の一言 (段が済んだとき・後の稽古が始まったときに字幕で出る)。"""
    t("message.skyabyss.lesson.say." + line, ja, en)
    return {"type": "lesson", "act": "say", "line": line}


QUESTS = []


def quest(name, chapter, order, title, summary, done_text, stages, start_when=None):
    """stages: [(段 id, (journal ja, en), [目的], xp, do)]。目的: dict に "text": (ja, en) を足したもの。"""
    base = "quest.skyabyss.lesson." + name
    t(base + ".title", *title)
    t(base + ".summary", *summary)
    t(base + ".done", *done_text)
    js = []
    for sid, journal, objectives, xp, do in stages:
        t(base + "." + sid + ".journal", *journal)
        obs = []
        for ob in objectives:
            ob = dict(ob)
            text = ob.pop("text")
            t(base + "." + sid + "." + ob["id"], *text)
            obs.append(ob)
        st = {"id": sid, "objectives": obs}
        if xp:
            st["xp"] = xp
        if do:
            st["do"] = do
        js.append(st)
    q = {"category": "sub", "order": order, "giver": MASTER, "chapter": chapter, "min_stage": 0, "stages": js}
    if start_when:
        q["start_when"] = start_when
    QUESTS.append((name, q))


def sig(oid, s, ja, en, count=1):
    o = {"id": oid, "type": "signal", "target": s, "party": False, "text": (ja, en)}
    if count > 1:
        o["count"] = count
    return o


def talk(oid, topic, ja, en):
    return {"id": oid, "type": "talk", "target": MASTER, "topic": topic, "text": (ja, en)}


def at(q, stage):
    return {"type": "quest", "quest": q, "is": "active", "stage": stage}


def flag(f):
    return {"type": "flag", "npc": MASTER, "flag": f}


# ######################################################################
# 一章: 師範の稽古
# ######################################################################
quest("skills", "titan_whale", 205,
      ("師範の稽古", "The Master's Lesson"),
      ("都の訓練場の師範が、背に上がる前に技の手ほどきをしてくれる。",
       "The training master in the capital offers to drill you in skills before you climb a titan's back."),
      ("一通りの型を教わった。背の上で迷ったら、また訓練場へ戻ればいい。",
       "You've learned the basic forms. If you lose your way on a back, the yard is always here."),
      [("meet", ("都に着いたら、訓練場の師範を訪ねるといい。鯨追いはみな、背に上がる前にここで木偶を打つという。",
                 "Once in the capital, call on the training master. Every whale chaser, they say, beats the dummies here before going up."),
        [talk("meet", "lesson_offer", "訓練場の師範に稽古を頼む", "Ask the training master for a lesson")], 0, None),
       ("learn", ("技は、技の画面で点を使って覚える。覚えた技は空いた枠に入り、枠のキーで使う。",
                  "Skills are learned with points on the skill screen. A new skill drops into an empty slot and is used with that slot's key."),
        [sig("slot", "skill_lesson:slotted", "技を一つ覚えて、枠に置く", "Learn a skill and slot it")], 10,
        [say("learn", "覚えたな。枠の数字が、その技のキーだ。", "Good. The key on the slot is how you use it.")]),
       ("cast", ("木偶に技を当てる。技はマナを使い、使うとしばらく待ちになる。待ちが明けたら、もう一度。",
                 "Hit a dummy with the skill. Skills cost mana and need time to recover. Once it's ready, hit again."),
        [sig("hit", "skill_lesson:dummy", "技を木偶に二度当てる", "Hit a dummy with a skill twice", 2)], 10,
        [say("cast", "マナは HP の右の帯だ。待ちの間は刃でつなげ。", "Mana is the bar beside your HP. While a skill recovers, keep swinging.")]),
       ("behind", ("木偶の背後へ回って打つ。背後からの一撃は、風裂きなどの技で大きく伸びる。",
                   "Circle behind a dummy and strike. Hits from behind grow much stronger with skills like Wind Rend."),
        [sig("back", "skill_lesson:behind", "木偶の背後から打つ", "Strike a dummy from behind")], 10,
        [say("behind", "それだ。背の上の獣も、背中は見ていない。", "That's it. The beasts on a back don't watch their own backs either.")]),
       ("stance", ("構えを切り替える。守は硬く、疾は速く、歌はマナの戻りが早い。戦う前に選んでおく。",
                   "Switch your stance. Guard is sturdy, Swift is fast, Song recovers mana faster. Pick one before a fight."),
        [sig("stance", "skill_lesson:stance", "構えを切り替える", "Switch your stance")], 10,
        [say("stance", "構えは一つだけだ。替えるたびに少し待つ。", "One stance at a time, and each swap takes a moment.")]),
       ("reset", ("最後に、振り直しのことを師範に聞いておく。覚えた技は、あとから選び直せる。",
                  "Last, ask the master about respecs. The skills you learn can be chosen again later."),
        [talk("reset", "lesson_respec", "師範に振り直しのことを聞く", "Ask the master about respecs")], 10,
        [{"type": "lesson", "act": "finish"}])],
      start_when={"type": "all", "of": [
          {"type": "quest", "quest": "main_song", "is": "started"},
          {"type": "not", "of": at("main_song", "follow_smoke")},
          {"type": "not", "of": flag("lesson_skip")}]})

say("finish", "稽古は終わりだ。忘れたら、いつでも来い。", "That's the lesson. Come back whenever you forget.")
t("message.skyabyss.lesson.reward", "稽古の礼に、鐘銭を受け取った。", "You received bell coins for finishing the lesson.")
t("message.skyabyss.lesson.skipped", "稽古を飛ばした。訓練場の師範に頼めば、いつでも受けられる。",
  "Lesson skipped. Ask the training master any time to take it.")
t("message.skyabyss.lesson.behind", "背後を取った", "From behind!")

# ######################################################################
# 後の章: 新しい木・要が開いたときの一段だけの稽古
# ######################################################################
# (名前, 合図, 章, 題, 開いたときの一言, 目的)
TREES = [
    ("tree_hybrid", "skill_lesson:tree:hybrid", "titan_tide", 206,
     ("橋の技", "Bridge Skills"),
     ("二つの木の技を三つずつ覚えると、そのあいだの橋が開く。技の画面の輪で選べる。",
      "Learn three skills in each of two trees and the bridge between them opens. Find it on the ring of the skill screen."),
     ("橋の技を木偶か敵に当てる", "Land a bridge skill on a dummy or foe")),
    ("tree_keystone", "skill_lesson:keystone", "titan_storm", 207,
     ("要の技", "Keystones"),
     ("Lv 20 で要が開いた。木の一番下にある、型を決める技だ。一つ選んでみろ。",
      "Keystones open at Lv 20. They sit at the bottom of each tree and define a build. Pick one."),
     ("要の技を一つ覚える", "Learn a keystone")),
    ("tree_star", "skill_lesson:tree:star", "", 208,
     ("星術師の技", "Astromancy"),
     ("封じられていた星術師の木が開いた。技の画面に新しい札がある。",
      "The sealed Astromancer tree has opened. There's a new tab on the skill screen."),
     ("星術師の技を木偶か敵に当てる", "Land a Star skill on a dummy or foe")),
    ("tree_abyss", "skill_lesson:tree:abyss", "", 209,
     ("深淵術師の技", "Abyssal Arts"),
     ("深淵術師の木が開いた。技の画面に新しい札がある。",
      "The Abyssal Mystic tree has opened. There's a new tab on the skill screen."),
     ("深淵術師の技を木偶か敵に当てる", "Land an Abyssal skill on a dummy or foe")),
    ("tree_deep", "skill_lesson:tree:deep", "", 210,
     ("淵の技", "Skills of the Deep"),
     ("生まれ直して、淵の木が見えるようになった。転生の数で開く技が増える。",
      "Reborn, you can now see the tree of the Deep. More open with each rebirth."),
     ("淵の技を木偶か敵に当てる", "Land a skill of the Deep on a dummy or foe")),
    ("tree_past", "skill_lesson:tree:past", "", 211,
     ("前世の技", "Past-Life Skills"),
     ("前世の技が開いた。前世の点は、ふだんの点とは別に使える。",
      "Past-life skills have opened. Past-life points are spent separately from your usual points."),
     ("前世の技を木偶か敵に当てる", "Land a past-life skill on a dummy or foe")),
]

for name, signal, chapter, order, title, line, objective in TREES:
    say(name, *line)
    quest(name, chapter, order, title,
          (line[0], line[1]),
          ("新しい技を試した。", "You tried out the new skills."),
          [("try", (line[0], line[1]) if signal.endswith("keystone") else
            (line[0] + "訓練場の木偶でも、背の上の敵でもいい。", line[1] + " A training dummy or a real foe will do."),
            [sig("try", signal, *objective)], 15, None)])

# ######################################################################
# 画面の案内 (client.tutorial.SkillLessonClient)。%s は今のキー設定のキー名
# ######################################################################
t("lesson.skyabyss.hint.learn", "%s で技の画面を開き、光る技を覚える", "Press %s, then learn a glowing skill")
t("lesson.skyabyss.hint.cast", "木偶を向いて %s で技を使う", "Face a dummy and press %s")
t("lesson.skyabyss.hint.behind", "木偶の後ろの光へ回り込んで打つ", "Circle to the light behind a dummy and strike")
t("lesson.skyabyss.hint.stance", "%s で構えを切り替える", "Press %s to switch stance")
t("lesson.skyabyss.hint.reset", "師範に「振り直しのこと (稽古)」を聞く", "Ask the master: \"About respecs (lesson)\"")
t("lesson.skyabyss.screen.learn", "稽古: 光る技を選んで「習得」", "Lesson: glowing skill, then Learn")

# ######################################################################
# 師範の会話の足し書き
# ######################################################################
NODES = {}
MENU = []
BASE = "dialogue.skyabyss.attendant_training_master"


def node(nid, text, face="neutral", nxt=None, options=None, do=None):
    n = {"face": face}
    if do:
        n["do"] = do
    if nxt:
        n["next"] = nxt
    if options:
        n["options"] = []
        for oid, label, goto, *extra in options:
            op = {"id": oid, "goto": goto, "kind": "talk"}
            if extra:
                op.update(extra[0])
            n["options"].append(op)
            t(BASE + "." + nid + "." + oid, *label)
    NODES[nid] = n
    t(BASE + "." + nid, *text)


def menu(oid, label, goto, cond, kind="quest"):
    MENU.append({"id": oid, "goto": goto, "kind": kind, "if": cond})
    t(BASE + ".menu." + oid, *label)


menu("lesson", ("技の稽古を頼む", "Ask for a lesson"), "lesson_offer", at(LESSON, "meet"))
menu("lesson_new", ("技の稽古を頼む", "Ask for a lesson"), "lesson_new", {"type": "quest", "quest": LESSON, "is": "none"})
menu("lesson_respec", ("振り直しのこと (稽古)", "About respecs (lesson)"), "lesson_respec", at(LESSON, "reset"))
menu("lesson_again", ("もう一度、稽古を", "Another lesson, please"), "lesson_again", {"type": "quest", "quest": LESSON, "is": "done"}, kind="talk")
menu("lesson_skip", ("稽古を切り上げる", "End the lesson"), "lesson_skip",
     {"type": "all", "of": [{"type": "quest", "quest": LESSON, "is": "active"}, {"type": "not", "of": at(LESSON, "meet")}]}, kind="talk")

node("lesson_offer", ("背に上がる気か。なら、木偶を相手に型を見せてみろ。覚える、置く、当てる、回り込む、構える。それだけだ。",
                      "Going up a back? Then show me your forms on the dummies. Learn, slot, hit, circle, stance. That's all."),
     do=[{"type": "lesson", "act": "track"}],
     options=[("ok", ("頼む", "Teach me"), "end"),
              ("skip", ("今はいい (飛ばす)", "Not now (skip)"), "lesson_skip")])
node("lesson_new", ("いいだろう。もう一度、頭から見てやる。", "Fine. I'll take you through it from the top."),
     do=[{"type": "unflag", "flag": "lesson_skip"}, {"type": "quest_start", "quest": LESSON}],
     options=[("go", ("始める", "Begin"), "lesson_offer")])
node("lesson_again", ("忘れたか。いい、何度でも見てやる。", "Forgotten already? Fine. As many times as it takes."),
     do=[{"type": "lesson", "act": "replay"}],
     options=[("go", ("始める", "Begin"), "lesson_offer")])
node("lesson_skip", ("わかった。背の上で手が止まったら、ここへ戻れ。", "Understood. If your hands freeze on a back, come back here."),
     do=[{"type": "flag", "flag": "lesson_skip"}, {"type": "lesson", "act": "skip"}])
node("lesson_respec", ("覚えた技は、ここで全部戻せる。Lv 15 までは、ただだ。迷ったら試せ。合わなければ、また振り直せばいい。",
                       "I can undo every skill you've learned, right here. Free until Lv 15. If you're unsure, try things. If they don't fit, respec again."),
     options=[("try", ("振り直しを見る", "Show me the respec"), "respec"),
              ("later", ("今はいい", "Not now"), "end")])


# ====================================================================== 書き出す
def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    for name, q in QUESTS:
        dump(os.path.join(QUEST, name + ".json"), q)
    dump(os.path.join(DIALOGUE, "attendant_training_master_lesson.json"),
         {"npc": MASTER, "extends": True, "nodes": NODES, "add_options": {"menu": {"before": "about", "options": MENU}}})
    dump(os.path.join(LANG, "ja_jp.json"), dict(sorted(JA.items())))
    dump(os.path.join(LANG, "en_us.json"), dict(sorted(EN.items())))
    print("quests", len(QUESTS), "nodes", len(NODES), "lang keys", len(JA))


if __name__ == "__main__":
    main()
