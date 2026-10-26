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
    'gui.skyabyss.skill.job_held': ('（いま）', '(now)'),
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
    'message.skyabyss.job.lent': ('%sの始めの武器、%sを受け取った', "Received the %s's first weapon: %s"),
    'message.skyabyss.job.lent_already': ('%sの武器は、もう受け取っている', "You already have the %s's weapon"),
    'item.skyabyss.shrine_staff': ('宿り木の杖', 'Mistletoe Staff'),
    'item.skyabyss.shrine_staff.desc': ('島の祠が星読みに授ける流木の杖。星の石が淡く光る。', 'A driftwood staff the island shrine gives to stargazers. Its star stone glows faintly.'),
    'item.skyabyss.shrine_bell_staff': ('祠の鈴杖', 'Shrine Bell Staff'),
    'item.skyabyss.shrine_bell_staff.desc': ('島の祠が潮歌いに授ける鈴の杖。振ると潮の音がする。', 'A bell staff the island shrine gives to tide singers. It sounds like the tide when shaken.'),
    'item.skyabyss.shrine_dagger': ('宿り木の短剣', 'Mistletoe Dagger'),
    # 祠の始めの武器 (戦士・鍛冶師・弓使い)
    'item.skyabyss.shrine_sword': ('祠の剣', 'Shrine Sword'),
    'item.skyabyss.shrine_sword.desc': ('島の祠が岩守りに授ける剣。まっすぐで扱いやすい。', 'A sword the island shrine gives to rock guards. Straight and easy to handle.'),
    'item.skyabyss.shrine_hammer': ('祠の鎚', 'Shrine Hammer'),
    'item.skyabyss.shrine_hammer.desc': ('島の祠が鍛え人に授ける鎚。重く、よく響く。', 'A hammer the island shrine gives to smiths. Heavy, and it rings well.'),
    'item.skyabyss.shrine_bow': ('祠の弓', 'Shrine Bow'),
    'item.skyabyss.shrine_bow.desc': ('島の祠が射手に授ける弓。素朴だが、よくしなる。', 'A bow the island shrine gives to archers. Plain, but it bends well.'),
    # 職の章の武器 (鍛冶師・弓使い・戦士・暗殺者の空き)
    'item.skyabyss.whale_hammer': ('鯨骨の鎚', 'Whalebone Hammer'),
    'item.skyabyss.whale_hammer.desc': ('岩鯨の背の石を打ち固めた鎚。鍛冶師の鎚。', "A hammer of stone from the stone whale's back. A smith's hammer."),
    'item.skyabyss.whale_bow': ('鯨肋の弓', 'Whale Rib Bow'),
    'item.skyabyss.whale_bow.desc': ('岩鯨の肋を腕にした弓。弓使いの弓。', "A bow whose limbs are the stone whale's ribs. An archer's bow."),
    'item.skyabyss.tide_hammer': ('潮殻の鎚', 'Tideshell Hammer'),
    'item.skyabyss.tide_hammer.desc': ('潮殻の殻を打ち付けた鎚。鍛冶師の鎚。', "A hammer faced with tide shell. A smith's hammer."),
    'item.skyabyss.tide_bow': ('珊瑚の弓', 'Coral Bow'),
    'item.skyabyss.tide_bow.desc': ('潮殻の珊瑚が腕になった弓。弓使いの弓。', "A bow grown from the tide shell's coral. An archer's bow."),
    'item.skyabyss.storm_hammer': ('雷の鎚', 'Thunder Hammer'),
    'item.skyabyss.storm_hammer.desc': ('雷翼の銅を打った鎚。鍛冶師の鎚。', "A hammer of the thunder wing's copper. A smith's hammer."),
    'item.skyabyss.ember_greatsword': ('熾火の大剣', 'Ember Greatsword'),
    'item.skyabyss.ember_greatsword.desc': ('余燼の玄武岩の大剣。戦士の剣。', "A greatsword of the ember titan's basalt. A warrior's sword."),
    'item.skyabyss.ember_bow': ('灰の弓', 'Ash Bow'),
    'item.skyabyss.ember_bow.desc': ('余燼の鉄を腕にした弓。弓使いの弓。', "A bow with limbs of the ember titan's iron. An archer's bow."),
    'item.skyabyss.ember_dagger': ('熾の短剣', 'Ember Dagger'),
    'item.skyabyss.ember_dagger.desc': ('余燼の炉の火箸から打った短剣。暗殺者の短剣。', "A dagger forged from the ember forge's tongs. An assassin's dagger."),
    # 章の杖と鈴杖 (item/ChapterGear。docs/design/43 の 5 段目)
    'item.skyabyss.whale_staff': ('鯨骨の星杖', 'Whalebone Star Staff'),
    'item.skyabyss.whale_staff.desc': ('岩鯨の肋が水色の石を抱く杖。魔法使いの杖。', "A staff whose whale ribs cradle a pale-blue stone. A mage's staff."),
    'item.skyabyss.whale_bell_staff': ('鯨歌いの鈴杖', 'Whalesong Bell Staff'),
    'item.skyabyss.whale_bell_staff.desc': ('岩鯨の背で苔むした石の鐘。呪術師の鈴杖。', "A mossy stone bell from the stone whale's back. A shaman's bell staff."),
    'item.skyabyss.tide_staff': ('潮殻の真珠杖', 'Tideshell Pearl Staff'),
    'item.skyabyss.tide_staff.desc': ('潮殻の真珠が灯る杖。魔法使いの杖。', "A staff lit by a tide shell pearl. A mage's staff."),
    'item.skyabyss.tide_bell_staff': ('潮鳴りの鈴杖', 'Tidecall Bell Staff'),
    'item.skyabyss.tide_bell_staff.desc': ('貝の鐘が潮の音で鳴る。呪術師の鈴杖。', "Shell bells that ring with the sound of the tide. A shaman's bell staff."),
    'item.skyabyss.storm_staff': ('雷羽の杖', 'Stormfeather Staff'),
    'item.skyabyss.storm_staff.desc': ('雷翼の羽と銅が雷を招く杖。魔法使いの杖。', "Thunder wing feathers and copper that call the lightning. A mage's staff."),
    'item.skyabyss.storm_bell_staff': ('雷鳴りの鈴杖', 'Thunderpeal Bell Staff'),
    'item.skyabyss.storm_bell_staff.desc': ('銅の鐘が雷のように鳴る。呪術師の鈴杖。', "Copper bells that peal like thunder. A shaman's bell staff."),
    'item.skyabyss.ember_staff': ('熾火の杖', 'Ember Staff'),
    'item.skyabyss.ember_staff.desc': ('余燼の熾火を宿した杖。魔法使いの杖。', "A staff holding an ember of the ember titan. A mage's staff."),
    'item.skyabyss.ember_censer': ('灰の香炉', 'Ash Censer'),
    'item.skyabyss.ember_censer.desc': ('熾火を焚く香炉の杖。呪術師の鈴杖の代わり。', "A censer staff that burns embers. A shaman's bell staff."),
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
