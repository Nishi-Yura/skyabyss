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
