"""スキルツリーを試すページ (skill-tree.html)。2026-11-07 の声「サイトでスキルツリーのテストできるようにして」。

ゲームの木 (assets/skyabyss/skill_tree/<ジョブ>.json) と、取れる決まり (rpg/skill/SkillData.canTake) をそのまま写す:
  前提 (requires) のどれか一つを取っている・排他 (blocks) を取っていない・同じスキルの刻印は一つだけ・
  型の点 (arch_points。その型の節に使った点の和)・使うスキルは 4 つまで (SkillRules.BASE_SLOTS)・段は最大まで・
  変異はスキルを極めてから・点はレベルの数 (SkillRules.pointsForLevel = Lv、1〜50)。
スキルの最大段と変異の元のスキルは Java (Skill.java・Mutation.java) から読む。名前と効き目は wiki と同じ (wiki.node_name・node_effect)。
絵はゲームの icons.png・icons2.png を site/img/skill/ へ写して使う。動きは site/js/planner.js。
"""
import html
import json
import os
import re
import shutil

import wiki
from sitelib import ROOT, SITE, VERSION, lang, page, page_hero, sec

JAVA = os.path.join(ROOT, "src", "main", "java", "jp", "revati", "skyabyss", "rpg", "skill")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "skill")
STAT_ORDER = ["strength", "agility", "spirit", "vitality"]
STAT_JA = {"strength": "筋力", "agility": "敏捷", "spirit": "精神", "vitality": "耐久"}
KIND_JA = {"active": "使うスキル", "stat": "基礎", "keystone": "要", "trait": "型の要", "effect": "常に効く", "threshold": "閾値",
           "rune": "刻印（スキルを変える）", "rank": "スキルの段", "passive": "常に効く", "mutation": "変異"}


def java_skills():
    """スキルの id → (最大段, 絵の番号は wiki の書き出しから)"""
    src = open(os.path.join(JAVA, "Skill.java"), encoding="utf8").read()
    out = {}
    for m in re.finditer(r'^\s+([A-Z_]+)\("([a-z_]+)",\s*SkillTree\.[A-Z_]+,\s*Kind\.[A-Z_]+,\s*(\d+)', src, re.M):
        out[m.group(2)] = int(m.group(3))
    return out


def java_mutations():
    src = open(os.path.join(JAVA, "Mutation.java"), encoding="utf8").read()
    enum_to_id = {}
    sk = open(os.path.join(JAVA, "Skill.java"), encoding="utf8").read()
    for m in re.finditer(r'^\s+([A-Z_]+)\("([a-z_]+)"', sk, re.M):
        enum_to_id[m.group(1)] = m.group(2)
    return {m.group(1): enum_to_id.get(m.group(2)) for m in re.finditer(r'\("([a-z_]+)",\s*Skill\.([A-Z_]+)', src)}


def name_of(job, n, sk):
    if n.get("kind") == "mutation" and n.get("mutation"):
        return "変異「%s」" % lang("mutation.skyabyss." + n["mutation"])
    name = wiki.node_name(job, n, sk, STAT_JA)
    if n.get("kind") == "rank":
        return name + "（段を上げる）"
    return name


def effect_of(job, n, sk):
    if n.get("kind") == "mutation" and n.get("mutation"):
        e = lang("mutation.skyabyss.%s.effect" % n["mutation"])
    else:
        e = html.unescape(wiki.node_effect(job, n, sk, STAT_JA))
    return e.replace("%%", "%")


def build():
    sk = {s["id"]: s for s in wiki.load(wiki.DUMP)["skills"]}
    max_rank = java_skills()
    mut = java_mutations()
    os.makedirs(os.path.join(SITE, "img", "skill"), exist_ok=True)
    for f in ("icons.png", "icons2.png"):
        shutil.copyfile(os.path.join(TEX, f), os.path.join(SITE, "img", "skill", f))
    jobs = []
    for job, jname in wiki.JOBS:
        t = wiki.load(os.path.join(wiki.ASSETS, "skill_tree", job + ".json"))
        nodes = []
        for n in t["nodes"]:
            skill = n.get("skill") or (mut.get(n.get("mutation")) if n.get("kind") == "mutation" else None)
            kind = n.get("kind")
            if kind == "stat":
                icon = 100 + STAT_ORDER.index(n["stat"])
            elif kind == "rune":
                icon = 104
            elif kind == "effect":
                icon = 145
            elif kind == "threshold":
                icon = 146
            elif kind == "trait":
                icon = 147
            else:
                icon = sk.get(skill, {}).get("icon", 145)
            req = n.get("requires", [])
            nodes.append({
                "id": n["id"], "k": kind, "c": n.get("cost", 1), "x": n["x"], "y": n["y"], "a": n.get("arch") or "",
                "ap": n.get("arch_points", 0), "r": req, "b": n.get("blocks", []), "s": skill or "", "i": icon,
                "st": n.get("stat", ""), "am": n.get("amount", 0), "at": n.get("at", 0),
                "n": name_of(job, n, sk), "e": effect_of(job, n, sk),
            })
        archs = [{"id": a, "n": lang("arch.skyabyss.%s.%s" % (job, a))} for a in t.get("archetypes", [])]
        skills = {}
        for n in nodes:
            if n["s"]:
                skills[n["s"]] = {"n": sk.get(n["s"], {}).get("name") or lang("skill.skyabyss." + n["s"]), "m": max_rank.get(n["s"], 3)}
        jobs.append({"id": job, "n": jname, "archs": archs, "nodes": nodes, "skills": skills})
    data = {"jobs": jobs, "kinds": KIND_JA, "stats": STAT_JA, "statOrder": STAT_ORDER, "slots": 4, "maxLevel": 50}

    body = page_hero("tree", "戦士のスキルツリーの画面", "スキルツリーを試す",
                     "ゲームと同じ木と決まりで、ノードを取ってみられます。ジョブとレベルを選び、ノードを押して取る・外す。組んだ形はリンクで人に渡せます。")
    tool = ('<div class="pl" id="planner">'
            '<div class="pl-bar">'
            '<div class="pl-jobs" role="tablist" aria-label="ジョブ"></div>'
            '<label class="pl-lv">レベル <input type="range" id="pl-lv" min="1" max="50" value="50"> <b id="pl-lv-n">50</b></label>'
            '<button type="button" class="pl-btn" id="pl-reset">全部外す</button>'
            '<button type="button" class="pl-btn" id="pl-share">リンクを写す</button>'
            '</div>'
            '<div class="pl-main">'
            '<div class="pl-board-wrap"><div class="pl-board" id="pl-board" aria-label="スキルツリー"></div></div>'
            '<aside class="pl-side">'
            '<div class="pl-sum" id="pl-sum"></div>'
            '<div class="pl-info" id="pl-info"><p class="small">ノードに指を置く（触れる）と、名前と効き目が出ます。</p></div>'
            '</aside></div>'
            '<p class="small pl-legend">金の縁 = 取った・明るい縁 = 今取れる・暗い = まだ取れない。'
            '取ったノードを押すと外れます（つながりの切れたノードもいっしょに外れます）。'
            'ゲームの中では、振り直しは訓練場の師範で（Lv 15 まで無料）。</p>'
            '<script id="pl-data" type="application/json">%s</script></div>') % esc_json(data)
    rules = ("<ul class=\"list\">"
             "<li>点はレベルの数（Lv 50 で 50 点）。ノードごとに使う点（1〜3）が違います。</li>"
             "<li>取れるのは、前提のノードのどれか一つとつながっているノードです。木のいちばん上（幹のスキル）から伸ばします。</li>"
             "<li>使うスキルは 4 つまで。同じスキルの刻印は一つだけで、並んだ刻印のうち片方を取るともう片方は取れません。</li>"
             "<li>型の奥の「要」は、その型のノードに決まった点（15 など）を使うと取れます。変異は、そのスキルの段を上げきってから。</li>"
             "<li>基礎（筋力・敏捷・精神・耐久）はノードでしか上がりません。閾値のノードは、その基礎が決まった値に届いているときに効きます。</li>"
             "</ul>")
    body += sec("try", "SKILL TREE", "木を組む", "", tool)
    body += sec("rules", "RULES", "取り方の決まり", "ゲームの中の決まりと同じです。", rules, alt=True)
    page("skill-tree.html", "スキルツリーを試す — 天と淵のあいだ", "ジョブごとのスキルツリーを、ゲームと同じ決まりでサイトの上で組んでみる。", body
         + '<script src="js/planner.js?v=%s" defer></script>' % VERSION)


def esc_json(data):
    return json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
