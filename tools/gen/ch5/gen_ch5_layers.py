# -*- coding: utf-8 -*-
"""五章の深淵の中の四つの区切り (docs/design/36 の 8 章。2026-10-23 の作り直し)。

python -I tools/gen/ch5/gen_ch5_layers.py   (gen_ch5_entrance.py が SECTION_STAGES を読み、main_abyss の段に差し込む)
書くもの:
  data/skyabyss/quest/ch5/lamp_keeper.json      骨: 灯り番のミオ「探掘隊の灯り」(隠し部屋の灯りを持ち帰る)
  data/skyabyss/quest/ch5/surveyor_map.json     塩: 測量士のハヤセ「破れた地図」(地図の切れ端を三枚)
  data/skyabyss/quest/ch5/shadow_rest.json      雷: 薬師のシズ「戸の奥の影」(前の私の影を眠らせる)
  data/skyabyss/quest/ch5/captain_pages.json    灰: 隊長のトウヤ「散らした手記」(灰の層の手記を三冊読む)
  data/skyabyss/dialogue/ch5/attendant_survivor_<層>.json・attendant_abyss_porter.json (荷運びのロク。品の取り替え)
  assets/skyabyss_abyss_v2/lang/ja_jp.json・en_us.json (区切りの題字・場面・語り部・印・仕掛け・主・手記・会話・段の文)
物語: 都の探掘隊 (隊長トウヤ・灯り番ミオ・測量士ハヤセ・薬師シズ・荷運びロクと、帰らなかった二人) が、井戸の鼓動の元を探して先に下りた。
  層ごとに一人ずつ残り、何があったかが層を下りるたびに分かる。最後に、隊長が心臓のかけらを持ち出そうとして番人に止められたこと、
  番人は心臓を守るのではなく、世界が錨からほどけないように見張っていること、番人の声が「お前の声」だったことが分かる
"""
from __future__ import annotations

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data', 'skyabyss')
LANG = os.path.join(RES, 'assets', 'skyabyss_abyss_v2', 'lang')
CH = 'chapter2'
LAYERS = ['bone', 'salt', 'storm', 'ash']
SURVIVOR = {L: 'skyabyss:attendant_survivor_%s' % L for L in LAYERS}
PORTER = 'skyabyss:attendant_abyss_porter'


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


def q_active(quest, stage=None):
    c = {'type': 'quest', 'quest': quest, 'is': 'active'}
    if stage:
        c['stage'] = stage
    return c


def q_is(quest, is_):
    return {'type': 'quest', 'quest': quest, 'is': is_}


# ---------------------------------------------------------------- main_abyss の段 (gate のあと、descend の前)
def section_stages():
    out = []
    for L in LAYERS:
        out.append({'id': '%s_camp' % L, 'xp': 60, 'objectives': [{'id': 'talk', 'type': 'talk', 'target': SURVIVOR[L], 'topic': 'ch5_%s_camp' % L}]})
        out.append({'id': '%s_marks' % L, 'xp': 160, 'objectives': [
            {'id': 'm%d' % i, 'type': 'signal', 'targets': ['abyss_mark_%s_%d' % (L, i)], 'count': 1} for i in (1, 2, 3)]})
        out.append({'id': '%s_lord' % L, 'xp': 260, 'objectives': [{'id': 'defeat', 'type': 'signal', 'targets': ['abyss_lord_%s' % L], 'count': 1}]})
    return out


SECTION_STAGES = section_stages()

T = []  # (キー, 日本語, 英語)


def t(key, ja, en):
    T.append((key, ja, en))


# ---------------------------------------------------------------- 区切りの題字・場面・語り部
NAMES = {'bone': ('五章の一', '骨の層', 'Chapter 5-I', 'The Bone Layer'), 'salt': ('五章の二', '塩の層', 'Chapter 5-II', 'The Salt Layer'),
         'storm': ('五章の三', '雷の層', 'Chapter 5-III', 'The Storm Layer'), 'ash': ('五章の四', '灰の層', 'Chapter 5-IV', 'The Ash Layer'),
         'heart': ('五章の終わり', '心臓', 'Chapter 5, End', 'The Heart')}
for k, (tj, nj, te, ne) in NAMES.items():
    t('chapter.skyabyss.abyss_v2.%s.tab' % k, tj, te)
    t('chapter.skyabyss.abyss_v2.%s.title' % k, nj, ne)
SCENE = {
    'bone': ('焚き火が、まだ温かい。……誰かが、ここで帰りを待っていた。', '北の封じ石に、三つの穴。印をはめる穴だ。',
             'The fire is still warm. ...Someone waited here for others to return.', 'Three hollows in the north seal. Places for marks.'),
    'salt': ('空気が重い。息を吸うたび、肺に塩がたまるみたいだ。', 'ここにも野営の跡。上の層と同じ人たちのものだ。',
             'The air is heavy. Every breath settles salt in my lungs.', 'Another camp. The same people as above.'),
    'storm': ('壁の銅が鳴っている。雷じゃない。……鼓動だ。', '三つの道の奥で、拍子がずれて聞こえる。',
              'The copper in the walls is humming. Not thunder. ...A heartbeat.', 'From the three paths, the beat comes back out of step.'),
    'ash': ('灯りが、すぐ痩せる。闇がここを好いている。', '灰の上に、靴の跡。行きの跡ばかりで、帰りの跡がない。',
            'Light thins out fast here. The dark likes this place.', 'Footprints in the ash. All going in. None coming back.'),
    'heart': ('打っている。足の裏から、骨の芯まで。', 'この先に心臓がある。……そして、それを見張るものが。',
              'It is beating. Through my soles, into my bones.', 'The heart is ahead. ...And the thing that watches it.'),
}
for k, (a, b, ea, eb) in SCENE.items():
    t('cinematic.skyabyss.abyss_v2.%s.hero1' % k, a, ea)
    t('cinematic.skyabyss.abyss_v2.%s.hero2' % k, b, eb)
TELLER = {
    'bone': ('語り部「骨の層。巨神たちの鼓動が、最初に落ちてたまる所だよ」', '語り部「前に下りた探掘隊は、ここで三手に分かれた。印は三つ。……灯りを絶やさないで」',
             'Storyteller: "The bone layer. Where the titans\' beats first fall and pool."',
             'Storyteller: "The excavation party that went before split three ways here. Three marks. ...Keep your light alive."'),
    'salt': ('語り部「塩の層。下りるほど、体は重くなる。重さは、心臓に近いしるしなんだ」', '語り部「測量士がひとり、ここに残っている。地図を描く人は、最後まで帰り道を描こうとする」',
             'Storyteller: "The salt layer. The deeper you go, the heavier you get. Weight means the heart is near."',
             'Storyteller: "A surveyor stayed behind here. Mapmakers keep drawing the way home to the very end."'),
    'storm': ('語り部「雷の層。ここの雷は、心臓と同じ拍子で落ちる」', '語り部「拍子を聞きなさい。本物の鼓動は、いつもひとつだけだから」',
              'Storyteller: "The storm layer. Lightning here falls in time with the heart."',
              'Storyteller: "Listen to the beat. There is only ever one true heartbeat."'),
    'ash': ('語り部「灰の層。灯りを喰うものたちの巣だよ」', '語り部「隊長は、ここまで来た。……この先を知っているのは、あの人だけ」',
            'Storyteller: "The ash layer. A nest of things that eat light."', 'Storyteller: "The captain got this far. ...Only he knows what lies beyond."'),
    'heart': ('語り部「心臓の間。番人は、灯りと重さと鼓動と闇を使う。層の主たちが、ひとつずつ教えてくれたはずだよ」', '語り部「全部、思い出して。……行っておいで」',
              'Storyteller: "The heart chamber. The warden uses light, weight, the beat and the dark. The layer lords taught you each one."',
              'Storyteller: "Remember all of it. ...Go on."'),
}
for k, (a, b, ea, eb) in TELLER.items():
    t('message.skyabyss.abyss_v2.teller.%s_1' % k, a, ea)
    t('message.skyabyss.abyss_v2.teller.%s_2' % k, b, eb)

# ---------------------------------------------------------------- 印・門・鎖
MARKS = {
    'bone_1': ('灯皿の印', 'Brazier Mark'), 'bone_2': ('骨笛の印', 'Bone Flute Mark'), 'bone_3': ('肋の橋の印', 'Rib Bridge Mark'),
    'salt_1': ('塩の殻の印', 'Salt Crust Mark'), 'salt_2': ('塩風の印', 'Salt Wind Mark'), 'salt_3': ('白殻の印', 'White Shell Mark'),
    'storm_1': ('鼓動の戸の印', 'Heartbeat Door Mark'), 'storm_2': ('雷の回廊の印', 'Thunder Hall Mark'), 'storm_3': ('羽虫の印', 'Moth Mark'),
    'ash_1': ('灰の迷路の印', 'Ash Maze Mark'), 'ash_2': ('三つの術の印', 'Three Arts Mark'), 'ash_3': ('燠の番の印', 'Ember Keeper Mark'),
}
for k, (j, e) in MARKS.items():
    t('abyss_v2.skyabyss.mark.%s' % k, j, e)
t('message.skyabyss.abyss_v2.mark_got', '%1$sを手に入れた（%2$s／3）。', 'You took the %1$s (%2$s/3).')
t('message.skyabyss.abyss_v2.mark_have', 'この印はもう持っている（%s／3）。', 'You already hold this mark (%s/3).')
t('message.skyabyss.abyss_v2.mark_mate', '%1$sが%2$sを取った。仲間の印も同じに光る。', '%1$s took the %2$s. Your mark glows with it.')
t('message.skyabyss.abyss_v2.marks_all', '印が三つそろった。野営の北の封じ石へ。', 'All three marks. Go to the seal north of the camp.')
t('message.skyabyss.abyss_v2.mark_guarded.nest_bone', '台の上を骨笛の群れが囲んでいる。巣を払わないと触れられない。', 'Bone flutes swarm the altar. Clear the nest first.')
t('message.skyabyss.abyss_v2.mark_guarded.nest_storm', '羽虫の帯電で、台に手が届かない。巣を払おう。', 'The moths\' charge keeps your hand off the altar. Clear the nest.')
t('message.skyabyss.abyss_v2.mark_guarded.terr_salt', '白い貝守が、この印を殻の奥にしまいこんでいる。', 'The white shell keeper has the mark shut away in its shell.')
t('message.skyabyss.abyss_v2.mark_guarded.terr_ash', '燠の番がまだ近くにいる。台の火が消えない。', 'The ember keeper is still near. The altar\'s fire will not die.')
t('message.skyabyss.abyss_v2.gate_closed', '封じ石の穴が冷たい。印が足りない（%s／3）。', 'The seal\'s hollows are cold. Not enough marks (%s/3).')
t('message.skyabyss.abyss_v2.gate_open', '三つの印が光り、封じ石が道をあけた。', 'The three marks flare and the seal lets you through.')
t('message.skyabyss.abyss_v2.gate_back', '封じ石を抜けて、野営へ戻った。', 'You pass back through the seal to the camp.')
t('message.skyabyss.abyss_v2.descend_closed', '鎖は張りつめたまま動かない。この間の主が、まだ見ている。', 'The chain is taut and will not move. The lord of this hall is still watching.')
t('message.skyabyss.abyss_v2.descend', '鎖を伝って、下の層へ下りた。', 'You ride the chain down to the next layer.')
t('message.skyabyss.abyss_v2.ascend', '鎖をたぐって、上の野営へ戻った。', 'You haul yourself up the chain to the camp above.')

# ---------------------------------------------------------------- 仕掛け
t('message.skyabyss.abyss_v2.hint.bone', '三つの灯皿。どれも冷えきっている。……光の術なら、同時に灯せるかもしれない。',
  'Three braziers, all stone cold. ...The light art might kindle them all at once.')
t('message.skyabyss.abyss_v2.hint.salt', '床が白い殻でできている。踏むたびに、軽い音で鳴る。体が軽ければ、もつかもしれない。',
  'The floor is a white crust. It rings thin under each step. If you were lighter, it might hold.')
t('message.skyabyss.abyss_v2.hint.well', '井戸の底から、塩の風が吹き上がってくる。体が軽ければ、乗れそうだ。',
  'A salt wind rises from the bottom of the well. If you were light enough, it could carry you.')
t('message.skyabyss.abyss_v2.hint.storm', '三つの戸。どれかの奥で、何かが打っている。鼓動の術で聞き分けられそうだ。',
  'Three doors. Something beats behind one of them. The heartbeat art could tell which.')
t('message.skyabyss.abyss_v2.hint.hall', '回廊の床が焦げている。雷は拍子で落ちる。床の赤い輪から離れて。',
  'The hall floor is scorched. Lightning falls on the beat. Keep clear of the red rings.')
t('message.skyabyss.abyss_v2.hint.maze', '真っ暗だ。光の術の灯りの中にだけ、燠の跡が浮かぶ。', 'Pitch black. Only inside the light art\'s glow do the ember tracks show.')
t('message.skyabyss.abyss_v2.hint.ash', '封の戸の前に、三つの台。光と重さと鼓動の印が刻んである。三つを続けて使えば……',
  'Three pedestals before the sealed door, marked light, weight and beat. Use all three in turn...')
t('message.skyabyss.abyss_v2.door_bone_open', '三つの灯皿が一度に燃えた。奥の骨の戸が、崩れて落ちる。', 'All three braziers flare together. The bone door beyond crumbles away.')
t('message.skyabyss.abyss_v2.hidden_open', '光が当たったところから、壁がほどけるように崩れた。奥に小さな部屋がある。',
  'Where the light touched, the wall came apart. There is a small room behind it.')
t('message.skyabyss.abyss_v2.doors_heard', '鼓動が聞こえる。……心臓の粒が浮かんでいる戸の奥からだ。', 'There is the heartbeat. ...From behind the door where the heart motes drift.')
t('message.skyabyss.abyss_v2.door_true', '戸の奥から、確かな鼓動。道が続いている。', 'A steady heartbeat beyond the door. The way goes on.')
t('message.skyabyss.abyss_v2.door_wrong', '鼓動じゃない。羽音だ。', 'That was no heartbeat. Wings.')
t('message.skyabyss.abyss_v2.seal_open', '三つの台が順に灯り、封の戸が灰になって崩れた。', 'The three pedestals light in turn, and the sealed door collapses into ash.')
t('message.skyabyss.abyss_v2.find_empty', '中はもう空だ。', 'It is empty now.')
t('message.skyabyss.abyss_v2.find.hidden_bone', '箱の中に、煤けた角灯。探掘隊の焼き印がある。……ミオが探していたものだ。',
  'A sooty lantern in the chest, branded with the excavation party\'s mark. ...The one Mio was looking for.')
t('message.skyabyss.abyss_v2.find.frag_salt_1', '箱の中に地図の切れ端。塩の殻の床の下に「穴、浅い。殻は重いと割れる」。',
  'A map fragment. Under the salt floor, in a neat hand: "Pit is shallow. Crust breaks under weight."')
t('message.skyabyss.abyss_v2.find.frag_salt_2', '地図の切れ端。井戸の印の横に「風、上へ。体を軽く」。', 'A map fragment. Beside the well: "Wind blows up. Make yourself light."')
t('message.skyabyss.abyss_v2.find.frag_salt_3', '地図の切れ端。縄張りの印に、太い字で「行くな」。', 'A map fragment. Across the territory, in heavy strokes: "Don\'t."')
t('item.skyabyss.abyss_v2.fragment', 'ハヤセの地図の切れ端', 'Hayase\'s Map Fragment')
t('item.skyabyss.abyss_v2.party_lantern', '探掘隊の角灯', 'Excavation Party Lantern')

# ---------------------------------------------------------------- 戦い (巣・縄張り・待ち伏せ・影)
t('message.skyabyss.abyss_v2.nest_wake.nest_bone', '骨笛の巣が鳴りだした。群れが来る。', 'The bone flute nest starts to wail. A swarm is coming.')
t('message.skyabyss.abyss_v2.nest_wake.nest_storm', '巣の羽虫が、一斉に帯電した。', 'Every moth in the nest charges at once.')
t('message.skyabyss.abyss_v2.nest_clear.nest_bone', '巣が静かになった。台の上の印に手が届く。', 'The nest falls silent. The mark on the altar is in reach.')
t('message.skyabyss.abyss_v2.nest_clear.nest_storm', '羽音が止んだ。台の帯電が抜けていく。', 'The wings go still. The charge drains from the altar.')
t('message.skyabyss.abyss_v2.wave', '群れ %1$s／%2$s', 'Wave %1$s/%2$s')
t('message.skyabyss.abyss_v2.terr_wake.terr_salt', '縄張りの主が、殻を開いた。', 'The lord of this ground opens its shell.')
t('message.skyabyss.abyss_v2.terr_wake.terr_ash', '燠の番が、灰の中から起き上がった。', 'The ember keeper rises out of the ash.')
t('message.skyabyss.abyss_v2.terr_clear.terr_salt', '白い貝守が崩れた。殻の奥で、印が光っている。', 'The white shell keeper breaks apart. A mark glows inside the shell.')
t('message.skyabyss.abyss_v2.terr_clear.terr_ash', '燠の番が灰に還った。台の火が細くなる。', 'The ember keeper returns to ash. The altar fire gutters.')
t('abyss_v2.skyabyss.elite.terr_salt', '縄張りの白い貝守', 'Territorial Shell Keeper')
t('abyss_v2.skyabyss.elite.terr_ash', '大きな燠の番', 'Great Ember Keeper')
t('message.skyabyss.abyss_v2.ambush_bone', '壁の窪みの骨が、起き上がった。', 'The bones in the wall niches stand up.')
t('message.skyabyss.abyss_v2.ambush_bridge', '橋の両端から、骨拾いが這い上がってくる。', 'Bone pickers crawl up onto both ends of the bridge.')
t('message.skyabyss.abyss_v2.shadow_1', '戸の奥に、誰かが座っている。……自分と同じ背格好だ。', 'Someone is sitting behind the door. ...The same build as you.')
t('message.skyabyss.abyss_v2.shadow_2', '影が顔を上げた。「まだ、下りるの？」', 'The shadow looks up. "Still going down?"')
t('message.skyabyss.abyss_v2.shadow_gone', '影がほどけて、静かになった。シズに伝えよう。', 'The shadow unravels and is still. Tell Shizu.')

# ---------------------------------------------------------------- 層の主
LORDS = {
    'bone': ('肋の主', 'Lord of Ribs',
             '肋の主を倒せ。灯台を灯し、光の中で斬れ', 'Defeat the Lord of Ribs. Light the lamps and strike inside the light',
             '間の奥で、積み上がった肋が立ち上がった。', 'At the back of the hall, a heap of ribs stands up.',
             '灯台は右クリックで灯る。光の外では、刃が骨に弾かれる。……番人と同じだ。',
             'The lamps light with a right-click. Outside the light, your blade glances off. ...Just like the warden.',
             '肋の主が崩れた。光の中なら、闇のものにも刃は届く。', 'The Lord of Ribs collapses. In the light, even things of the dark can be cut.',
             '光の外では、刃が通らない。', 'Your blade will not bite outside the light.'),
    'salt': ('白殻の主', 'Lord of White Shells',
             '白殻の主を倒せ。足もとの赤い輪から出ろ', 'Defeat the Lord of White Shells. Step out of the red rings',
             '天井から、白い殻の塊が落ちてきた。', 'A mass of white shell drops from the ceiling.',
             '赤い輪には重さが落ちる。重さの術の中なら、押しつぶされずに済む。',
             'Weight falls inside the red rings. Under the weight art, it will not crush you.',
             '白殻の主が割れた。重さは、避けるか、軽くなって受け流せる。', 'The Lord of White Shells cracks open. Weight can be dodged, or shrugged off if you are light.',
             '', ''),
    'storm': ('鼓動まねの主', 'Lord of Mimicked Beats',
              '鼓動まねの主を倒せ。鼓動の聞こえる方が本物', 'Defeat the Lord of Mimicked Beats. The one with the heartbeat is real',
              '銅の壁が一斉に鳴り、間に鼓動が満ちた。', 'The copper walls ring together and the hall fills with a heartbeat.',
              '主は写しに分かれる。鼓動を立てるのは本物だけ。鼓動の術を使えば、本物の輪郭が見える。',
              'It splits into copies. Only the real one beats. The heartbeat art will outline the true one.',
              '鼓動まねの主が黙った。本物の鼓動は、いつもひとつ。', 'The Lord of Mimicked Beats falls silent. There is only ever one true heartbeat.',
              '', ''),
    'ash': ('灯り喰いの主', 'Lord of Eaten Light',
            '灯り喰いの主を倒せ。闇の波のあとは、光の術で照らせ', 'Defeat the Lord of Eaten Light. After the dark wave, light it with your art',
            '灯りが一つずつ消え、闇の奥で口が開いた。', 'The lights go out one by one, and a mouth opens in the dark.',
            '闇の波が来ると、灯りが全部消える。闇の中の主には刃が届かない。光の術で主を照らせ。',
            'When the dark wave comes, every light dies, and the lord cannot be cut in the dark. Light it with your art.',
            '灯り喰いの主が闇に溶けた。闇が来ても、灯りは自分で点けられる。', 'The Lord of Eaten Light dissolves into the dark. Even when darkness comes, you can make your own light.',
            '闇の中では、刃が届かない。光の術を。', 'Your blade cannot reach it in the dark. Use the light art.'),
}
for L, v in LORDS.items():
    t('abyss_v2.skyabyss.lord.%s' % L, v[0], v[1])
    t('objective.skyabyss.abyss_v2.lord_%s' % L, v[2], v[3])
    t('message.skyabyss.abyss_v2.lord_wake.%s' % L, v[4], v[5])
    t('message.skyabyss.abyss_v2.lord_teach.%s' % L, v[6], v[7])
    t('message.skyabyss.abyss_v2.lord_fall.%s' % L, v[8], v[9])
    if v[10]:
        t('message.skyabyss.abyss_v2.lord_dim.%s' % L, v[10], v[11])
t('objective.skyabyss.abyss_v2.flash_light_lamps', '灯台を灯せ（右クリック）', 'Light the lamps (right-click)')
t('objective.skyabyss.abyss_v2.flash_guard_lamp', '灯りを守れ。赤い輪の中に立て', 'Guard the light. Stand inside the red ring')
t('objective.skyabyss.abyss_v2.flash_weight', '赤い輪から出ろ。重さの術なら耐えられる', 'Leave the red rings. The weight art lets you endure')
t('objective.skyabyss.abyss_v2.flash_beat', '鼓動の聞こえる方が本物', 'The one with the heartbeat is real')
t('objective.skyabyss.abyss_v2.flash_dark', '闇の中では刃が届かない。光の術で照らせ', 'No blade reaches it in the dark. Light it with your art')
t('message.skyabyss.abyss_v2.dark_coming', '主が三度吠えた。闇の波が来る。', 'The lord howls three times. The dark wave is coming.')
# 番人の戦いの帯 (story/ch5/warden/HeartWarden)
t('objective.skyabyss.abyss_v2.warden_p1', '灯籠を点け、光の中の番人を斬れ', 'Light the lanterns and strike the warden in the light')
t('objective.skyabyss.abyss_v2.warden_snuff', '灯りを守れ。番人が灯籠を吹き消しに来る', 'Guard the light. The warden is coming to snuff a lantern')
t('objective.skyabyss.abyss_v2.warden_p2', '鼓動の聞こえる方が本物', 'The one with the heartbeat is real')
t('objective.skyabyss.abyss_v2.warden_crush', '赤い輪は重い。外へ出るか、重さの術で耐えろ', 'The red rings are heavy. Get out, or endure with the weight art')
t('objective.skyabyss.abyss_v2.warden_p3', '闇の波のあとは、光の術で照らせ', 'After the dark wave, make light with your art')

# ---------------------------------------------------------------- 手記 (野営の帳面 0、道ごと 1〜3)
JOURNALS = {
    'bone': [
        ('探掘隊の帳面「都の井戸が、夜ごとに脈を打つ。ギルドの頼みで、六人で元を探しに下りる。隊長トウヤ」',
         '帳面の続き「北の封じ石に穴が三つ。印をはめる穴らしい。三手に分かれて探す。灯りはミオの係」',
         'Party ledger: "The capital\'s wells pulse every night. At the guild\'s request, six of us go down to find the source. — Captain Touya"',
         'Ledger, continued: "Three hollows in the north seal, for marks it seems. We split three ways. Mio keeps the lights."'),
        ('書見台の頁「灯皿の間。三つを同時に灯せば戸が開く、と壁にある。……壁の字が、わたしたちの字に似ている」',
         '頁の続き「隊長は気のせいだと言った。でも、印を刻んだのは、わたしたちより前に来た誰かだ」',
         'Lectern page: "The brazier room. The wall says to light all three at once and the door opens. ...The writing looks like ours."',
         'Page, continued: "The captain says I imagine it. But someone came before us and cut these marks."'),
        ('書見台の頁「巣の中で、骨笛がずっと鳴っていた。ゴロウが噛まれた。浅い傷だと言っている」',
         '頁の続き「印は取れた。帰りに、まっすぐ野営へ戻る抜け道を見つけた。格子が向こうから開いた」',
         'Lectern page: "The bone flutes in the nest never stopped. Gorou was bitten. He says it is shallow."',
         'Page, continued: "We got the mark. On the way back we found a straight path to the camp. The bars opened from the far side."'),
        ('書見台の頁「肋の橋は一人ずつ。下は浅い。落ちても梯子で戻れる。落ちたのはロクで、三回」',
         '頁の続き「主の間の前で、ミオの灯りが吹き消された。あの子は、灯りを取りに戻ると言った」',
         'Lectern page: "The rib bridge, one at a time. The pit is shallow; a ladder leads back. Roku fell. Three times."',
         'Page, continued: "Before the lord\'s hall, Mio\'s lamp was blown out. She said she would go back for a light."'),
    ],
    'salt': [
        ('ハヤセの帳面「下りるほど体が重い。上るのはもっと重い。この重さは、下に何か大きなものがある重さだ」',
         '帳面の続き「隊長は言った。心臓のかけらを都へ持ち帰れば、井戸の揺れは止まる、と。わしは、うなずいてしまった」',
         'Hayase\'s ledger: "The deeper, the heavier. Climbing is worse. This is the weight of something vast below us."',
         'Ledger, continued: "The captain said: bring a shard of the heart to the capital and the wells will stop shaking. I nodded."'),
        ('書見台の頁「塩の殻の床。重いものは落ちる。荷を全部置いて、体を軽くして渡った」',
         '頁の続き「わしの地図は、ここで殻と一緒に割れた。切れ端は三つの道に散った」',
         'Lectern page: "The salt crust floor. Heavy things fall through. We left every pack and went across light."',
         'Page, continued: "My map broke here along with the crust. The pieces scattered down all three paths."'),
        ('書見台の頁「井戸の底の風は、軽い者しか運ばない。シズが先に上がって、縄を下ろしてくれた」',
         '頁の続き「縄は、もう切れている。……ここから先、わしは上へは戻れんだろう」',
         'Lectern page: "The wind in the well carries only the light. Shizu went up first and dropped a rope."',
         'Page, continued: "The rope has snapped now. ...From here, I doubt I will ever climb back up."'),
        ('書見台の頁「白い貝守の縄張り。印を殻にしまいこんでいる。ゴロウの傷が、白く固まりはじめた」',
         '頁の続き「塩の主は、重さを落としてくる。輪の中にいたら、骨が鳴った」',
         'Lectern page: "The white shell keeper\'s ground. It keeps the mark shut in its shell. Gorou\'s wound has started to harden white."',
         'Page, continued: "The salt lord drops weight on you. Inside its ring, my bones creaked."'),
    ],
    'storm': [
        ('シズの帳面「雷が拍子で落ちる。数えると、心臓と同じ速さ。ここは心臓の音を聞く層だ」',
         '帳面の続き「隊長と言い合いになった。心臓は宝じゃない。誰かが交わした約束みたいなものだ、とわたしは言った」',
         'Shizu\'s ledger: "Lightning falls on a beat. Counting it, it is the heart\'s pace. This is the layer where you hear the heart."',
         'Ledger, continued: "I argued with the captain. The heart is no treasure, I said. It is more like a promise someone made."'),
        ('書見台の頁「三つの戸。違う戸の奥に、自分そっくりの影が座っていた。ゴロウが話しかけて、帰ってこなかった」',
         '頁の続き「本物の戸の奥だけ、鼓動が聞こえる。耳を澄ませば、分かったはずなのに」',
         'Lectern page: "Three doors. Behind a wrong one sat a shadow just like one of us. Gorou spoke to it and did not come back."',
         'Page, continued: "Only behind the true door is there a heartbeat. We would have known, if we had listened."'),
        ('書見台の頁「雷の回廊。赤い輪が光ってから落ちる。拍を数えて、一区間ずつ走った」',
         '頁の続き「ナギが転んだ。拍に遅れた。……雷は待ってくれない」',
         'Lectern page: "The thunder hall. The red ring glows, then the strike. We counted beats and ran one stretch at a time."',
         'Page, continued: "Nagi stumbled. A beat too late. ...The lightning does not wait."'),
        ('書見台の頁「鼓動まねの主は、写しに分かれる。叩いた写しが弾けて、ナギの腕を焼いた」',
         '頁の続き「隊長とロクだけが下へ行った。わたしはここで、上ってくる誰かを待つことにした」',
         'Lectern page: "The mimic lord splits into copies. A copy burst when struck and burned Nagi\'s arm."',
         'Page, continued: "Only the captain and Roku went on down. I decided to wait here for whoever climbs up."'),
    ],
    'ash': [
        ('トウヤの帳面「ここまで来たのは、俺とロクだけ。ロクは上へ帰した。荷運びは、帰り道を運ぶのが仕事だ」',
         '帳面の続き「灯りを喰うものがいる。灯りはすぐ痩せる。手記は散らしておく。誰もついてこないように」',
         'Touya\'s ledger: "Only Roku and I made it this far. I sent Roku back up. A porter\'s job is to carry the way home."',
         'Ledger, continued: "Something here eats light. Lights wither fast. I will scatter my notes so no one follows."'),
        ('隊長の手記（一）「灰の迷路を抜けた。灯りの中にだけ、燠の跡が見えた。誰かが、先に道をつけていた」',
         '手記の続き「心臓の間に着いた。冷たいのに、打っていた。かけらを削ろうとして、刃を当てた」',
         'Captain\'s notes (1): "Through the ash maze. Only in the light could I see the ember trail. Someone had blazed it before me."',
         'Notes, continued: "I reached the heart chamber. Cold, yet beating. I set my blade to it to cut a shard."'),
        ('隊長の手記（二）「番人が来た。姿は見えない。灯りの中にだけ、肋の形が浮かんだ」',
         '手記の続き「番人は心臓を守っていたんじゃない。心臓から世界がほどけないように、見張っていたんだ」',
         'Captain\'s notes (2): "The warden came. I could not see it. Only in the light did a shape of ribs appear."',
         'Notes, continued: "It was not guarding the heart. It was watching so the world would not come loose from it."'),
        ('隊長の手記（三）「番人は言った。『まだ足りない』。その声は、俺の声じゃなかった」',
         '手記の続き「お前の声だった。……これを読んでいる、お前の声だ」',
         'Captain\'s notes (3): "The warden spoke. \'It is not enough yet.\' The voice was not mine."',
         'Notes, continued: "It was your voice. ...Yours, the one reading this."'),
    ],
}
for L, pages in JOURNALS.items():
    for i, (a, b, ea, eb) in enumerate(pages):
        t('message.skyabyss.abyss_v2.journal.%s_%d_1' % (L, i), a, ea)
        t('message.skyabyss.abyss_v2.journal.%s_%d_2' % (L, i), b, eb)

# ---------------------------------------------------------------- 人 (名前)
PEOPLE = {'survivor_bone': ('灯り番のミオ', 'Mio the Lamp Keeper'), 'survivor_salt': ('測量士のハヤセ', 'Hayase the Surveyor'),
          'survivor_storm': ('薬師のシズ', 'Shizu the Apothecary'), 'survivor_ash': ('隊長のトウヤ', 'Captain Touya'),
          'abyss_porter': ('荷運びのロク', 'Roku the Porter')}
for k, (j, e) in PEOPLE.items():
    t('entity.skyabyss.attendant.%s' % k, j, e)
    t('entity.skyabyss.attendant_%s' % k, j, e)

# ---------------------------------------------------------------- 会話
DIALOGUES = {}


def npc_path(role):
    return role.split(':')[1]


def node_text(role, node, ja, en):
    t('dialogue.skyabyss.%s.%s' % (npc_path(role), node), ja, en)


def opt_text(role, node, opt, ja, en):
    t('dialogue.skyabyss.%s.%s.%s' % (npc_path(role), node, opt), ja, en)


# 層ごとの生き残りの会話: 段の話 (ch5_<層>_camp)・頼みごと (side_offer → side_return)・探掘隊の話 (party)・主の間のあと (after)
SIDE = {'bone': 'ch5/lamp_keeper', 'salt': 'ch5/surveyor_map', 'storm': 'ch5/shadow_rest', 'ash': 'ch5/captain_pages'}
TALK = {
    'bone': {
        'camp': [('grave', '……足音。帰ってきたの？　隊長？', '...Footsteps. Are you back? Captain?'),
                 ('neutral', 'ちがう、知らない人だ。わたしはミオ。都の探掘隊の、灯り番。みんなが帰るまで、ここで火を守ってる', 'No. A stranger. I\'m Mio, lamp keeper of the capital\'s excavation party. I keep this fire until everyone comes back.'),
                 ('grave', '北の封じ石を通るには、印が三つ要る。道は三本。隊はここで三手に分かれた。……灯りを、絶やさないでね', 'To pass the north seal you need three marks. Three paths. The party split three ways here. ...Don\'t let your light die.')],
        'offer': [('grave', '肋の主に灯りを吹き消されて、隊の角灯をどこかに落としたの。光の中でだけ崩れる壁がある、って隊長が言ってた', 'The Lord of Ribs blew my light out and I dropped the party lantern somewhere. The captain said some walls crumble only in light.'),
                  ('neutral', '光の術を覚えたなら、東の道の広間の壁を照らしてみて。……見つけたら、持ってきてくれる？', 'If you have the light art, shine it on the walls of the hall on the east path. ...Will you bring it back if you find it?')],
        'return': [('smile', 'それ……！　焼き印、隊のだ。ありがとう', 'That...! The brand is ours. Thank you.'),
                   ('neutral', 'これで、下りていった人たちにも灯りが届く気がする。持っていって。わたしには、この火があるから', 'Somehow I feel the light reaches the ones who went down now. Keep it. I have this fire.')],
        'party': [('neutral', '隊は六人。隊長のトウヤ、測量士のハヤセ、薬師のシズ、荷運びのロク、ゴロウ、ナギ。それとわたし', 'Six of us. Captain Touya, Hayase the surveyor, Shizu the apothecary, Roku the porter, Gorou, Nagi. And me.'),
                  ('grave', '都の井戸が夜ごとに脈を打つから、その元を探しに来たの。……下へ行った人は、誰も上ってこない', 'The wells in the capital pulsed every night, so we came to find the source. ...No one who went down has come back up.')],
        'after': [('neutral', '肋の主を倒したの？　あれは、光の外では斬れなかった。番人と同じだって、隊長が言ってた', 'You beat the Lord of Ribs? It couldn\'t be cut outside the light. The captain said the warden is the same.')],
    },
    'salt': {
        'camp': [('grave', 'おお……上から来たか。わしはハヤセ。探掘隊の測量士だ。足をこっちへ。座ったままですまんな', 'Oh... from above, are you. I am Hayase, the party\'s surveyor. Come closer. Forgive me for not standing.'),
                 ('neutral', '体が重くて、もう上れん。深淵は下りるほど重くなる。この重さは、下に途方もなく大きなものがある重さだ', 'My body is too heavy to climb now. The abyss grows heavier the deeper you go. It is the weight of something immense below.'),
                 ('grave', '隊長は、心臓のかけらを都へ持ち帰れば井戸の揺れが止まると言った。わしは、うなずいてしまった。……ここの印も三つだ', 'The captain said a shard of the heart would stop the wells shaking. I nodded along. ...There are three marks here too.')],
        'offer': [('neutral', 'わしの地図は、塩の殻の床で割れて散った。切れ端は三つの道に一枚ずつ。帰り道を描いた地図だ', 'My map broke on the salt crust and scattered. One piece down each of the three paths. It was a map of the way home.'),
                  ('grave', '拾ってきてくれんか。わしは帰れんでも、誰かが帰れるように', 'Would you gather them? Even if I can\'t go home, so that someone can.')],
        'return': [('smile', 'そうだ、この線だ。わしの字だ。……これで、上への道がつながる', 'Yes, this line. My hand. ...Now the way up joins together.'),
                   ('neutral', '代わりにこれを持っていけ。倒れた所を指す羅針だ。重さに負けそうになったら、重さの術を思い出せ', 'Take this in exchange. A compass that points to where you last fell. When the weight is beating you, remember the weight art.')],
        'party': [('neutral', 'ゴロウは巣で噛まれた傷が白く固まって、塩の層で動けなくなった。シズが最後まで手を握っていた', 'The bite Gorou took in the nest hardened white, and he could not move past the salt layer. Shizu held his hand to the end.')],
        'after': [('neutral', '白殻の主を割ったか。あれの重さは、避けるか、軽くなって受け流すしかない。番人も、きっと重さを落としてくる', 'You cracked the shell lord. Its weight you dodge, or go light and let it pass. The warden will drop weight on you too, surely.')],
    },
    'storm': {
        'camp': [('grave', '止まって。……息をして。そう、あなたの鼓動はひとつね。よかった、影じゃない', 'Stop. ...Breathe. Yes, one heartbeat. Good. You\'re not a shadow.'),
                 ('neutral', 'わたしはシズ。隊の薬師。ここの雷は、心臓と同じ拍子で落ちるの。数えれば、逃げる間が分かる', 'I\'m Shizu, the party\'s apothecary. Lightning here falls on the heart\'s beat. Count it and you\'ll know when to run.'),
                 ('grave', '隊長とは、ここで言い合いになった。心臓は宝じゃない、誰かが交わした約束みたいなものだって。……印を三つ、集めて', 'The captain and I argued here. The heart is no treasure, I said. More like a promise someone made. ...Gather the three marks.')],
        'offer': [('grave', '鼓動の戸の、違う戸の奥に、ゴロウそっくりの影がいた。話しかけたナギは、それからおかしくなった', 'Behind a wrong heartbeat door there was a shadow just like Gorou. Nagi spoke to it, and was never right after.'),
                  ('neutral', 'あの影を、眠らせてあげて。あれはたぶん、前にここへ来た誰か。……ひょっとしたら、あなたかもしれない', 'Lay that shadow to rest. It\'s probably someone who came here before. ...It might even be you.')],
        'return': [('neutral', '眠った？　……ありがとう。影は、帰れなかった人の形をしている', 'It sleeps? ...Thank you. Shadows wear the shape of those who never made it home.'),
                   ('smile', '薬を持っていって。鼓動の術で、あなたの周りの人も癒せるはずよ', 'Take some medicine. The heartbeat art can mend the people around you too.')],
        'party': [('grave', 'ナギは雷の回廊で、拍に遅れた。隊長とロクだけが下へ行った。わたしは、上ってくる誰かを待つことにしたの', 'Nagi fell a beat behind in the thunder hall. Only the captain and Roku went down. I chose to wait for whoever comes up.')],
        'after': [('neutral', '鼓動まねの主を黙らせたのね。本物の鼓動は、いつもひとつ。番人の影も、きっと同じ', 'You silenced the mimic lord. The true heartbeat is only ever one. The warden\'s shadows will be the same.')],
    },
    'ash': {
        'camp': [('grave', '……来たか。灰の中を、まっすぐここまで。お前は、迷わないんだな', '...So you came. Straight through the ash. You don\'t get lost, do you.'),
                 ('neutral', '探掘隊の隊長、トウヤだ。仲間を全部、上に置いてきた。ロクだけは帰した。帰り道を運ぶのが、荷運びの仕事だからな', 'Touya, captain of the excavation party. I left everyone above. Only Roku I sent home. A porter carries the way back.'),
                 ('grave', '心臓の間を見たいなら、ここの印も三つだ。……その先のことは、俺の手記に書いた。散らしてある。誰もついてこないように', 'If you want the heart chamber, three marks again. ...What lies beyond, I wrote in my notes. Scattered, so no one would follow.')],
        'offer': [('neutral', '手記は三冊。灰の層の三本の道に一冊ずつ。……読んでから、戻ってこい。それでも下りるなら、止めない', 'Three notebooks, one down each of the ash paths. ...Read them and come back. If you still go down after that, I won\'t stop you.')],
        'return': [('grave', '読んだか。……俺は、心臓のかけらを削ろうとした。都のためだと言いながら', 'You read them. ...I tried to cut a shard from the heart. Telling myself it was for the capital.'),
                   ('grave', '番人は心臓を守ってるんじゃない。世界が錨からほどけないように見張ってる。そして、あの声は……お前の声だった', 'The warden doesn\'t guard the heart. It watches so the world doesn\'t come loose from its anchor. And that voice... it was yours.'),
                   ('neutral', '灯り、重さ、鼓動、闇。層の主たちが一つずつ教えたはずだ。全部使え。……行け', 'Light, weight, the beat, the dark. The layer lords taught you each one. Use all of them. ...Go.')],
        'party': [('neutral', 'ミオはまだ火を守ってるか。ハヤセは地図を、シズは誰かを待って。……みんな、待つのが上手すぎる', 'Is Mio still keeping the fire? Hayase with his map, Shizu waiting for someone. ...They\'re all too good at waiting.')],
        'after': [('neutral', '灯り喰いの主も越えたか。闇が来ても、灯りは自分で点けられる。番人の前で、それを忘れるな', 'Past the Lord of Eaten Light too. When darkness falls, you can make your own light. Don\'t forget that before the warden.')],
    },
}


def build_survivor(L):
    role = SURVIVOR[L]
    side = SIDE[L]
    d = TALK[L]
    nodes = {}
    entries = [{'node': 'ch5_%s_camp' % L, 'if': q_active('main_abyss', '%s_camp' % L)},
               {'node': 'side_return', 'if': q_active(side, 'return')},
               {'node': 'menu'}]

    def chain(prefix, lines, last_opts, last_do=None):
        for i, (face, ja, en) in enumerate(lines):
            nid = prefix if i == 0 else '%s%d' % (prefix, i + 1)
            node_text(role, nid, ja, en)
            n = {'face': face}
            if i + 1 < len(lines):
                n['next'] = '%s%d' % (prefix, i + 2)
            else:
                n['options'] = last_opts
                if last_do:
                    n['do'] = last_do
            nodes[nid] = n
        return '%s%d' % (prefix, len(lines)) if len(lines) > 1 else prefix

    last = chain('ch5_%s_camp' % L, d['camp'], [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}])
    opt_text(role, last, 'ok', 'わかった', 'Understood')
    last = chain('side_offer', d['offer'], [{'id': 'go', 'goto': 'end', 'kind': 'quest', 'do': [{'type': 'quest_start', 'quest': side}]},
                                            {'id': 'no', 'goto': 'menu', 'kind': 'talk'}])
    opt_text(role, last, 'go', '引き受ける', 'I\'ll do it')
    opt_text(role, last, 'no', 'あとで', 'Later')
    last = chain('side_return', d['return'], [{'id': 'ok', 'goto': 'end', 'kind': 'talk'}])
    opt_text(role, last, 'ok', '……', '...')
    last = chain('party', d['party'], [{'id': 'ok', 'goto': 'menu', 'kind': 'talk'}])
    opt_text(role, last, 'ok', 'ほかのことを聞く', 'Ask about something else')
    last = chain('after', d['after'], [{'id': 'ok', 'goto': 'menu', 'kind': 'talk'}])
    opt_text(role, last, 'ok', 'ほかのことを聞く', 'Ask about something else')
    nodes['menu'] = {'face': 'neutral', 'options': [
        {'id': 'side', 'goto': 'side_offer', 'kind': 'quest', 'if': q_is(side, 'none')},
        {'id': 'party', 'goto': 'party', 'kind': 'talk'},
        {'id': 'after', 'goto': 'after', 'kind': 'talk', 'if': {'type': 'any', 'of': [
            q_is('main_abyss', 'done')] + [q_active('main_abyss', s) for s in stages_after(L)]}},
        {'id': 'bye', 'goto': 'end', 'kind': 'leave'},
    ]}
    node_text(role, 'menu', {'bone': '火のそばにいて。暖かいから', 'salt': '何か聞きたいことはあるか', 'storm': '鼓動、ちゃんとひとつね',
                             'ash': '……何だ'}[L],
              {'bone': 'Stay by the fire. It\'s warm.', 'salt': 'Anything you wish to ask?', 'storm': 'Still one heartbeat. Good.', 'ash': '...What.'}[L])
    opt_text(role, 'menu', 'side', {'bone': '困っていることは？', 'salt': '地図のことを聞く', 'storm': '影のことを聞く', 'ash': '手記のことを聞く'}[L],
             {'bone': 'Is something wrong?', 'salt': 'Ask about the map', 'storm': 'Ask about the shadow', 'ash': 'Ask about the notes'}[L])
    opt_text(role, 'menu', 'party', '探掘隊のことを聞く', 'Ask about the excavation party')
    opt_text(role, 'menu', 'after', '主のことを話す', 'Talk about the lord')
    opt_text(role, 'menu', 'bye', '行く', 'Leave')
    return {'npc': role, 'entries': entries, 'nodes': nodes}


def stages_after(L):
    order = [s['id'] for s in SECTION_STAGES] + ['descend', 'warden', 'heart', 'report']
    i = order.index('%s_lord' % L)
    return order[i + 1:]


# 頼みごと
SIDE_QUESTS = {
    'bone': ('ch5/lamp_keeper', '探掘隊の灯り', 'The Party Lantern', 'ミオが落とした、隊の角灯を探す。', 'Find the party lantern Mio lost.',
             [('find', '光の中でだけ崩れる壁の奥に、角灯がある。', 'The lantern lies behind a wall that crumbles only in light.',
               [('take', 'signal', ['abyss_hidden_bone'], '東の道の広間で、光の術を壁に当てる', 'Shine the light art on a wall in the east hall')])],
             '「灯りが届く気がする」', '"I feel the light reaching them."'),
    'salt': ('ch5/surveyor_map', '破れた地図', 'The Torn Map', 'ハヤセの地図の切れ端を、三本の道から拾ってくる。', 'Gather Hayase\'s map fragments from the three paths.',
             [('find', '切れ端は三つの道の箱に一枚ずつ。', 'One fragment lies in a chest on each path.',
               [('f%d' % i, 'signal', ['abyss_frag_salt_%d' % i], '地図の切れ端（%s）' % ['塩の殻の床', '貯め池', '潮だまり'][i - 1],
                 'Map fragment (%s)' % ['salt crust', 'cistern', 'tide pool'][i - 1]) for i in (1, 2, 3)])],
             '「誰かが帰れるように」', '"So that someone can go home."'),
    'storm': ('ch5/shadow_rest', '戸の奥の影', 'The Shadow Behind the Door', '鼓動の戸の奥にいる影を、眠らせる。', 'Lay to rest the shadow behind the heartbeat doors.',
              [('find', '違う戸の奥に、自分によく似た影が座っている。', 'Behind a wrong door sits a shadow much like you.',
                [('rest', 'signal', ['abyss_shadow_storm'], '戸の奥の影を倒す', 'Defeat the shadow behind the door')])],
              '「影は、帰れなかった人の形」', '"Shadows wear the shape of those who never came home."'),
    'ash': ('ch5/captain_pages', '散らした手記', 'The Scattered Notes', '隊長トウヤの手記を、灰の層の三本の道で読む。', 'Read Captain Touya\'s notes on the three ash paths.',
            [('find', '手記は三冊。迷路・封の間・燠の縄張りの先に一冊ずつ。', 'Three notebooks: past the maze, the seal room and the ember ground.',
              [('j%d' % i, 'signal', ['abyss_journal_ash_%d' % i], '隊長の手記（%s）' % ['一', '二', '三'][i - 1], 'Captain\'s notes (%d)' % i) for i in (1, 2, 3)])],
            '「あの声は、お前の声だった」', '"That voice was yours."'),
}
REWARD_ITEM = {'bone': ('minecraft:soul_lantern', 2), 'salt': ('minecraft:recovery_compass', 1), 'storm': ('minecraft:golden_apple', 2),
               'ash': ('minecraft:golden_carrot', 8)}


def build_side(L):
    qid, tj, te, sj, se, stages, dj, de = SIDE_QUESTS[L]
    key = 'quest.skyabyss.' + qid.replace('/', '.')
    t(key + '.title', tj, te)
    t(key + '.summary', sj, se)
    t(key + '.done', dj, de)
    out = []
    for (sid, jj, je, obs) in stages:
        t('%s.%s.journal' % (key, sid), jj, je)
        objs = []
        for (oid, typ, targets, oj, oe) in obs:
            objs.append({'id': oid, 'type': typ, 'targets': targets, 'count': 1})
            t('%s.%s.%s' % (key, sid, oid), oj, oe)
        out.append({'id': sid, 'xp': 80, 'objectives': objs})
    out.append({'id': 'return', 'objectives': [{'id': 'talk', 'type': 'talk', 'target': SURVIVOR[L], 'topic': 'side_return'}]})
    t('%s.return.journal' % key, '%sに伝えに戻ろう。' % PEOPLE['survivor_' + L][0], 'Go back and tell %s.' % PEOPLE['survivor_' + L][1])
    t('%s.return.talk' % key, '%sに話す' % PEOPLE['survivor_' + L][0], 'Talk to %s' % PEOPLE['survivor_' + L][1])
    item, count = REWARD_ITEM[L]
    return {'category': 'sub', 'chapter': CH, 'order': 70 + LAYERS.index(L), 'giver': SURVIVOR[L], 'stages': out,
            'reward': {'xp': 300, 'items': [{'item': item, 'count': count}], 'affinity': {SURVIVOR[L]: 3}}}


# 荷運びのロク (塩の野営): 深淵の素材と、食べ物・灯りの取り替え
TRADES = [('chips', 'skyabyss:abyss_bone_chip', 6, 'minecraft:cooked_beef', 4, '骨のかけら 6 → 焼いた肉 4', 'Bone chips ×6 → Cooked beef ×4'),
          ('salt', 'skyabyss:abyss_salt_crystal', 6, 'minecraft:golden_carrot', 3, '塩の結晶 6 → 金のにんじん 3', 'Salt crystals ×6 → Golden carrots ×3'),
          ('copper', 'skyabyss:abyss_storm_copper', 4, 'minecraft:lantern', 4, '雷の銅 4 → ランタン 4', 'Storm copper ×4 → Lanterns ×4')]


def build_porter():
    role = PORTER
    nodes = {'intro': {'face': 'smile', 'do': [{'type': 'flag', 'flag': 'abyss_porter_intro'}], 'next': 'intro2'},
             'intro2': {'face': 'neutral', 'next': 'menu'},
             'trade_done': {'face': 'smile', 'next': 'menu'},
             'trade_none': {'face': 'neutral', 'next': 'menu'},
             'menu': {'face': 'neutral', 'options': []}}
    node_text(role, 'intro', 'よっ。生きた顔を見るのは久しぶりだ。荷運びのロク。隊長に帰れって言われて、帰り道の途中で座りこんでる',
              'Hey. Been a while since I saw a living face. Roku, the porter. The captain told me to go home, and I sat down halfway.')
    node_text(role, 'intro2', '深淵のがらくた、持ってるだろ。食い物と取り替えてやるよ。帰り道を運ぶのが、おれの仕事だ',
              'You\'re carrying abyss junk, right? I\'ll swap you food for it. Carrying the way home is my job.')
    node_text(role, 'menu', '何と取り替える？', 'What\'ll it be?')
    node_text(role, 'trade_done', 'まいど。上に戻ったら、ミオによろしく', 'Pleasure. Say hi to Mio when you\'re back up.')
    node_text(role, 'trade_none', '足りないな。もう少し拾ってきな', 'Not enough. Go pick up a bit more.')
    for (oid, give_item, gn, get_item, cn, ja, en) in TRADES:
        nodes['menu']['options'].append({'id': oid, 'goto': 'trade_done', 'kind': 'give',
                                         'if': {'type': 'item', 'item': give_item, 'count': gn},
                                         'do': [{'type': 'take', 'item': give_item, 'count': gn}, {'type': 'give', 'item': get_item, 'count': cn}]})
        opt_text(role, 'menu', oid, ja, en)
    nodes['menu']['options'].append({'id': 'bye', 'goto': 'end', 'kind': 'leave'})
    opt_text(role, 'menu', 'bye', '行く', 'Leave')
    return {'npc': role, 'entries': [{'if': {'type': 'not', 'of': {'type': 'flag', 'flag': 'abyss_porter_intro'}}, 'node': 'intro'}, {'node': 'menu'}],
            'nodes': nodes}


# ---------------------------------------------------------------- main_abyss の段の文
STAGE_TEXT = {
    'bone': ('深淵の最初の層。野営に、探掘隊の灯り番が残っていた。話を聞こう。', '灯り番のミオと話す',
             '北の封じ石を通るには、三本の道の奥の印が三つ要る。', '探掘隊の灯り番が言っていた。北の封じ石には印が三つ要る。三本の道の奥を探そう。',
             '封じ石の奥、肋の主の間。灯台を灯し、光の中で斬る。', '肋の主を倒す'),
    'salt': ('塩の層の野営に、測量士が座りこんでいた。', '測量士のハヤセと話す',
             '塩の層の三つの印を集める。', '重さの術を覚えた。塩の殻の床、塩風の井戸、白い貝守の縄張り。三つの印を集めよう。',
             '白殻の主の間。足もとの赤い輪には重さが落ちる。', '白殻の主を倒す'),
    'storm': ('雷の層の野営で、薬師が誰かを待っていた。', '薬師のシズと話す',
              '雷の層の三つの印を集める。', '鼓動の術を覚えた。鼓動の戸、雷の回廊、羽虫の巣。三つの印を集めよう。',
              '鼓動まねの主の間。鼓動の聞こえる方が本物。', '鼓動まねの主を倒す'),
    'ash': ('灰の層の野営に、探掘隊の隊長がいた。', '隊長のトウヤと話す',
            '灰の層の三つの印を集める。', '灯りがすぐ痩せる層。灰の迷路、三つの術の封、燠の番の縄張り。三つの印を集めよう。',
            '灯り喰いの主の間。闇の波のあとは、光の術で照らす。', '灯り喰いの主を倒す'),
}
STAGE_TEXT_EN = {
    'bone': ('The first layer of the abyss. A lamp keeper of the excavation party stayed at the camp. Hear her out.', 'Talk to Mio the lamp keeper',
             'The north seal needs three marks from the ends of the three paths.', 'The lamp keeper said the north seal needs three marks. Search the three paths.',
             'Beyond the seal: the Lord of Ribs. Light the lamps and strike in the light.', 'Defeat the Lord of Ribs'),
    'salt': ('A surveyor sits slumped at the salt layer camp.', 'Talk to Hayase the surveyor',
             'Gather the three marks of the salt layer.', 'You learned the weight art. The salt crust floor, the salt wind well, the shell keeper\'s ground. Gather three marks.',
             'The Lord of White Shells. Weight falls inside the red rings.', 'Defeat the Lord of White Shells'),
    'storm': ('At the storm layer camp, an apothecary waits for someone.', 'Talk to Shizu the apothecary',
              'Gather the three marks of the storm layer.', 'You learned the heartbeat art. The heartbeat doors, the thunder hall, the moth nest. Gather three marks.',
              'The Lord of Mimicked Beats. The one with the heartbeat is real.', 'Defeat the Lord of Mimicked Beats'),
    'ash': ('The party\'s captain is at the ash layer camp.', 'Talk to Captain Touya',
            'Gather the three marks of the ash layer.', 'Light withers fast here. The ash maze, the three-arts seal, the ember keeper\'s ground. Gather three marks.',
            'The Lord of Eaten Light. After the dark wave, make light with your art.', 'Defeat the Lord of Eaten Light'),
}
for L in LAYERS:
    j, e = STAGE_TEXT[L], STAGE_TEXT_EN[L]
    k = 'quest.skyabyss.main_abyss.'
    t(k + '%s_camp.journal' % L, j[0], e[0])
    t(k + '%s_camp.talk' % L, j[1], e[1])
    t(k + '%s_marks.journal' % L, j[3], e[3])
    for i in (1, 2, 3):
        t(k + '%s_marks.m%d' % (L, i), MARKS['%s_%d' % (L, i)][0], MARKS['%s_%d' % (L, i)][1])
    t(k + '%s_lord.journal' % L, j[4], e[4])
    t(k + '%s_lord.defeat' % L, j[5], e[5])


# ---------------------------------------------------------------- 手直しの取り込み
# 目的の帯の一言は lang を直接直してあり、上の文には戻していない。書き出しの前にここで上書きし、
# コミット済みの lang と同じにする (tools/audit/check_generators.py で確かめる)。
HAND_EDITS = [  # (鍵, 日本語, 英語)
    ("objective.skyabyss.abyss_v2.lord_ash", "灯り喰いの主。闇の波のあとは光の術で照らせ", "Lord of Eaten Light: after the dark, cast light"),
    ("objective.skyabyss.abyss_v2.warden_p1", "灯りを点け、光の中の番人を斬れ", "Light the lamps; strike the warden in the light"),
    ("objective.skyabyss.abyss_v2.warden_snuff", "灯りを守れ。番人が吹き消しに来る", "Guard the light. It comes to snuff a lamp"),
    ("objective.skyabyss.abyss_v2.lord_bone", "肋の主を倒せ。灯台を灯し、光の中で斬れ", "Lord of Ribs: light the lamps, strike in the light"),
    ("objective.skyabyss.abyss_v2.lord_salt", "白殻の主を倒せ。足もとの赤い輪から出ろ", "Lord of White Shells: leave the red rings"),
    ("objective.skyabyss.abyss_v2.lord_storm", "鼓動まねの主を倒せ。鼓動の聞こえる方が本物", "Lord of Mimicked Beats: the heartbeat is real"),
    ("objective.skyabyss.abyss_v2.flash_weight", "赤い輪から出ろ。重さの術なら耐えられる", "Leave the red rings, or endure with weight"),
    ("objective.skyabyss.abyss_v2.flash_dark", "闇の中では刃が届かない。光の術で照らせ", "Blades miss in the dark. Cast light"),
    ("objective.skyabyss.abyss_v2.warden_crush", "赤い輪は重い。外へ出るか、重さの術で耐えろ", "Red rings crush. Leave, or endure with weight"),
    ("objective.skyabyss.abyss_v2.warden_p3", "闇の波のあとは、光の術で照らせ", "After the dark wave, cast light"),
]


def main():
    sides = {}
    for L in LAYERS:
        sides[L] = build_side(L)
        write(os.path.join(DATA, 'quest', SIDE[L] + '.json'), sides[L])
        write(os.path.join(DATA, 'dialogue', 'ch5', 'attendant_survivor_%s.json' % L), build_survivor(L))
    write(os.path.join(DATA, 'dialogue', 'ch5', 'attendant_abyss_porter.json'), build_porter())
    _hand = {k: (j, e) for k, j, e in HAND_EDITS}
    T[:] = [(k,) + _hand.get(k, (j, e)) for k, j, e in T]
    keys = [k for k, _, _ in T]
    dup = {k for k in keys if keys.count(k) > 1}
    if dup:
        raise SystemExit('duplicate keys: %s' % sorted(dup))
    write(os.path.join(LANG, 'ja_jp.json'), {k: j for k, j, _ in T})
    write(os.path.join(LANG, 'en_us.json'), {k: e for k, _, e in T})
    print('wrote 4 side quests, 5 dialogues, %d texts' % len(T))


if __name__ == '__main__':
    main()
