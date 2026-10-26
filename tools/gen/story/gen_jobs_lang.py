# -*- coding: utf-8 -*-
# 職 (docs/design/43) の文。python tools/gen/story/gen_jobs_lang.py
import json

KV = {
    'job.skyabyss.warrior': ('戦士', 'Warrior'),
    'job.skyabyss.warrior.role': ('剣と盾で前に立つ。敵の狙いを引く壁', 'Stands in front with sword and shield. The wall that draws the enemy'),
    'job.skyabyss.smith': ('鍛冶師', 'Smith'),
    'job.skyabyss.smith.role': ('鎚と斧に火を宿す、重い一撃', 'Heavy blows with fire in hammer and axe'),
    'job.skyabyss.mage': ('魔法使い', 'Mage'),
    'job.skyabyss.mage.role': ('杖で雷と火を放つ、遠い魔法の攻め', 'Casts lightning and fire from afar with a staff'),
    'job.skyabyss.archer': ('弓使い', 'Archer'),
    'job.skyabyss.archer.role': ('遠くから射抜き、罠で縛る', 'Shoots from afar and binds with traps'),
    'job.skyabyss.assassin': ('暗殺者', 'Assassin'),
    'job.skyabyss.assassin.role': ('短剣で背後を突く、速い攻め', 'Fast strikes from behind with daggers'),
    'job.skyabyss.shaman': ('呪術師', 'Shaman'),
    'job.skyabyss.shaman.role': ('唄で守り、呪いで弱らせる支え', 'Shields with songs and weakens with curses'),
    'job.skyabyss.subtitle': ('%s（%s）', '%s (%s)'),
    'message.skyabyss.skill.no_job_weapon': ('技を使うには、職の武器を持とう（剣・鎚・杖・弓・短剣・鈴の杖）', 'Hold a job weapon to use skills (sword, hammer, staff, bow, dagger, bell staff)'),
    'message.skyabyss.skill.job_now': ('職: %s', 'Job: %s'),
    'gui.skyabyss.skill.job_held': ('（いまの職）', '(current job)'),
    'gui.skyabyss.skill.bar_nojob': ('職なし', 'No job'),
    # 島の祠の職選び (rpg/skill/JobChoice・JobChoiceScreen)
    'gui.skyabyss.job_choice.title': ('職を選ぶ', 'Choose a Job'),
    'gui.skyabyss.job_choice.lead': ('宿り木の祠が、六つの得物を差し出している', 'The mistletoe shrine offers six weapons'),
    'gui.skyabyss.job_choice.start': ('この職で始める', 'Begin as this'),
    'gui.skyabyss.job_choice.confirm': ('もう一度押して決める', 'Press again'),
    'gui.skyabyss.job_choice.later': ('あとで', 'Later'),
    'gui.skyabyss.job_choice.pick': ('左から職を一つ選ぼう。その職の始めの武器を受け取り、最初の技を一つ覚える。', 'Pick a job on the left. You receive its first weapon and learn one skill.'),
    'gui.skyabyss.job_choice.weapon': ('受け取る武器: %s', 'Weapon: %s'),
    'gui.skyabyss.job_choice.skills': ('使う技: %s', 'Skills: %s'),
    'gui.skyabyss.job_choice.sep': ('・', ', '),
    'gui.skyabyss.job_choice.skills_soon': ('技は旅の先で覚える。', 'Its skills are learned later on the journey.'),
    'gui.skyabyss.job_choice.first': ('最初に覚える技: %s', 'First skill: %s'),
    'gui.skyabyss.job_choice.switch': ('ほかの職の武器を持てば、いつでもその職になれる。', "Hold another job's weapon to switch to that job at any time."),
    'message.skyabyss.job.chosen': ('%sになった。%sを受け取った', 'You are now a %s. Received: %s'),
    'item.skyabyss.shrine_staff': ('宿り木の杖', 'Mistletoe Staff'),
    'item.skyabyss.shrine_staff.desc': ('島の祠が星読みに授ける流木の杖。星の石が淡く光る。', 'A driftwood staff the island shrine gives to stargazers. Its star stone glows faintly.'),
    'item.skyabyss.shrine_bell_staff': ('祠の鈴杖', 'Shrine Bell Staff'),
    'item.skyabyss.shrine_bell_staff.desc': ('島の祠が潮歌いに授ける鈴の杖。振ると潮の音がする。', 'A bell staff the island shrine gives to tide singers. It sounds like the tide when shaken.'),
    'item.skyabyss.shrine_dagger': ('宿り木の短剣', 'Mistletoe Dagger'),
    'item.skyabyss.shrine_dagger.desc': ('島の祠が風読みに授ける短剣。軽く、速い。', 'A dagger the island shrine gives to wind readers. Light and quick.'),
}
for lang, i in (('ja_jp', 0), ('en_us', 1)):
    p = f'src/main/resources/assets/skyabyss/lang/{lang}.json'
    s = open(p, encoding='utf8').read()
    d = json.loads(s)
    ins = ''.join(f'  "{k}": {json.dumps(v[i], ensure_ascii=False)},\n' for k, v in KV.items() if k not in d)
    s = s.replace('{\n', '{\n' + ins, 1)
    json.loads(s)
    open(p, 'w', encoding='utf8', newline='\n').write(s)
print('ok')
