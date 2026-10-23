# -*- coding: utf-8 -*-
"""深淵 v3 (2026-10-23 の試遊の直し) の文: 道案内・はじめての手引き・生き残りの一言・道の碑・帰りの祠・主の名乗りと段と倒れ・ボスバー・都の大坑道の碑。

python -I tools/abyss/gen_abyss_v3_texts.py
出力: src/main/resources/assets/skyabyss_abyss_v3/lang/ja_jp.json・en_us.json (この版の文)
      src/main/resources/assets/skyabyss/lang/ja_jp.json・en_us.json に碑の文 (sign.skyabyss.abyss_return.* と sign.skyabyss.abyss_shaft.*。碑は本の lang を読む) を足す
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss_abyss_v3', 'lang')
MAIN = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'skyabyss', 'lang')
G = 'guide.skyabyss.abyss_v3.'
M = 'message.skyabyss.abyss_v3.'

T = {}


def t(key, ja, en):
    T[key] = (ja, en)


# ---------------------------------------------------------------- 道案内 (目的の帯。%1$s などは AbyssGuide が渡す)
t(G + 'survivor', '野営の%1$sと話す', 'Talk to %1$s at the camp')
t(G + 'mark', '%1$sを取りに行く（印 %2$s／3）', 'Fetch the %1$s (marks %2$s/3)')
t(G + 'nest', '%1$sの台を囲む巣を払う（印 %2$s／3）', 'Clear the nest around the %1$s (marks %2$s/3)')
t(G + 'terr', '%1$sの台を守る縄張りの主を倒す（印 %2$s／3）', 'Defeat the territory boss guarding the %1$s (marks %2$s/3)')
t(G + 'gate', '印が三つそろった。野営の北の格子の門へ', 'Three marks. Go to the barred gate north of the camp')
t(G + 'arena', '門の奥の主の間へ。%1$sが待つ', 'Go down to the lord\'s hall. %1$s waits')
t(G + 'lord', '%1$sを倒す', 'Defeat %1$s')
t(G + 'descend', '主の間の奥の下りの鎖で、%1$sへ', 'Take the descending chain at the back of the hall to %1$s')
t(G + 'descend_heart', '主の間の奥の下りの鎖で、%1$sへ', 'Take the descending chain at the back of the hall to %1$s')
t(G + 'heart_room', '奥の廊下を抜けて、心臓の間へ', 'Go through the corridor into the heart chamber')
t(G + 'warden', '心臓の番人を倒す。灯籠を灯し、光の中で', 'Defeat the Heart Warden. Light the lanterns and fight in the light')
t(G + 'heart_touch', '心臓の台に触れる', 'Touch the heart\'s altar')
t(G + 'home', '上りの鎖で野営へ戻り、骨の野営の白い磁石石から地上へ', 'Climb back by the chains and return to the surface from the white lodestone in the bone camp')

# ---------------------------------------------------------------- はじめての手引き
t(M + 'tip.home', '手引き：野営の西の壁ぞいの白い祠。磁石石に触れると、いつでも地上（都の下の門）へ帰れる。',
  'Tip: the white shrine by the west wall of the camp. Touch its lodestone to return to the surface (the gate under the capital) at any time.')
t(M + 'tip.board', '手引き：道の口のわきの碑を右クリックすると、その道の印がまだか、何に守られているかが分かる。',
  'Tip: right-click the tablet beside each route mouth to see whether its mark is still there and what guards it.')
t(M + 'tip.marks', '手引き：三本の道の奥に、それぞれ印の台がある。三つそろうと野営の北の格子の門が上がる。',
  'Tip: each of the three routes ends at a mark altar. With all three, the barred gate north of the camp rises.')
t(M + 'tip.shortcut', '手引き：印を取ると、その道から野営へまっすぐ戻る近道の格子が開く。帰りはそちらが近い。',
  'Tip: taking a mark opens the barred shortcut from that route straight back to camp.')
t(M + 'tip.arena', '手引き：主の床の赤い印は、技の落ちる所。赤が消える前に外へ。主が吠える間は刃が通らない。',
  'Tip: red marks on the floor show where the lord\'s attacks land. Step out before the red fades. Blades do not bite while it roars.')

# ---------------------------------------------------------------- 生き残りの一言 (野営で近くに来たとき、段ごとに一度)
SV = {
    'bone': ('灯り番のミオ', 'Mio the lamp-keeper'),
    'salt': ('測量士のハヤセ', 'Hayase the surveyor'),
    'storm': ('薬師のシズ', 'Shizu the apothecary'),
    'ash': ('隊長のトウヤ', 'Captain Toya'),
}
LINES = {
    'bone': {
        'marks': ('道の口の碑を見てごらん。どの道の印がまだか、書いてあるから。灯りは三歩ごとに置いておいたよ',
                  'Check the tablets by the route mouths. They tell which marks are left. I set lamps every few steps.'),
        'guard': ('南の道の骨笛の印は、骨の巣に囲まれてる。三つの波を全部倒さないと、台に近づけない',
                  'The bone-flute mark down the south route is ringed by a bone nest. Beat all three waves first.'),
        'lord': ('門の奥の肋の主は、光の外では刃を弾く。四つの灯台を灯して、光の中で斬って。吹き消しに来たら、赤い輪に立って守るの',
                 'The rib lord shrugs off blades outside the light. Light the four lamp-stands and fight inside the glow. When it comes to snuff one, stand in the red ring.'),
        'after': ('やったね。下りの鎖は主の間の奥。次は塩の層……白い殻の重さに気をつけて',
                  'You did it. The chain down is at the back of the hall. Next is the salt layer. Mind the weight of the white shells.'),
    },
    'salt': {
        'marks': ('この層は重い。息を整えて、道を一本ずつ。塩の床は滑るから、縁から落ちないように',
                  'This layer is heavy. Breathe, and take the routes one at a time. The salt floor slips; stay off the edges.'),
        'guard': ('西の道の白殻の印は、縄張りの白い貝守が抱えてる。あれを倒せば台に手が届く',
                  'A territorial white shell-keeper hoards the white-shell mark on the west route. Bring it down to reach the altar.'),
        'lord': ('白殻の主は、人の足もとに重さを落とす。赤い輪が出たら、すぐ外へ。輪から輪へ逃げ道を作っておくんだ',
                 'The white-shell lord drops weight under your feet. When the red ring appears, step out at once, and keep an escape path open.'),
        'after': ('下りの鎖で雷の層へ。あそこは鼓動がうるさい。どれが本物か、耳で選べ',
                  'Take the chain down to the storm layer. It is loud with heartbeats there. Pick the real one by ear.'),
    },
    'storm': {
        'marks': ('雷の層は道が三つに割れてる。扉の仕掛けは、灯りを近づけると読めるよ。薬が要るなら言って',
                  'The storm routes split three ways. You can read the door locks if you bring a light close. Ask if you need salves.'),
        'guard': ('西の道の羽虫の印は、雷の巣の真ん中。羽虫は群れで来る。三つ目の波に雷の像がまじるから、先にそれを',
                  'The moth mark on the west route sits in the middle of a storm nest. The third wave brings a lightning idol; break it first.'),
        'lord': ('鼓動まねの主は三つに分かれる。鼓動の音がする方が本物。写しを叩くと弾けて赤い輪が出るから、離れて',
                 'The heartbeat mimic splits in three. The one with the heartbeat is real. Hitting a copy bursts it into a red ring, so back away.'),
        'after': ('次は灰の層。灯りを喰う主がいる。光の術を切らさないで',
                  'Next is the ash layer, with a lord that eats light. Keep your light art ready.'),
    },
    'ash': {
        'marks': ('灰の層は迷路だ。焼けた印の跡をたどれ。壁の熾火は道しるべにもなる',
                  'The ash layer is a maze. Follow the scorched trail marks. The embers in the walls point the way, too.'),
        'guard': ('西の道の燠の番の印は、大きな燠の番が抱えてる。火を浴びたら、すぐ灰の中から出ろ',
                  'A great ember-keeper holds the ember mark on the west route. If you catch fire, get out of the ash at once.'),
        'lord': ('灯り喰いの主は闇の波で灯りを全部消す。闇の間は刃が通らない。光の術で主を照らせ。そこが勝ち筋だ',
                 'The light-eater snuffs every lamp with a wave of dark. Blades fail in that dark. Light it up with your light art; that is how you win.'),
        'after': ('この下が心臓だ。……俺たちはここで待つ。帰れなくなったら、上の白い磁石石を思い出せ',
                  'Below is the heart. We will wait here. If you lose your way, remember the white lodestone above.'),
    },
}
for layer, (ja_name, en_name) in SV.items():
    for state, (ja, en) in LINES[layer].items():
        t(M + 'survivor.%s.%s' % (layer, state), '%s「%s」' % (ja_name, ja), '%s: "%s"' % (en_name, en))

# ---------------------------------------------------------------- 道の碑 (右クリック)
t(M + 'board.head', '%1$s「%2$s」 ― この層の印 %3$s／3', '%1$s "%2$s" - marks on this layer %3$s/3')
t(M + 'board.done', '%1$s：取った。近道の格子は開いている', '%1$s: taken. The shortcut bars are open')
t(M + 'board.open', '%1$s：まだ台の上にある。道の奥へ', '%1$s: still on its altar, at the end of this route')
t(M + 'board.guarded', '%1$s：%2$sに守られている。先に払う', '%1$s: guarded by %2$s. Clear it first')
t(M + 'guard.nest_bone', '骨の巣', 'a bone nest')
t(M + 'guard.nest_storm', '雷の巣', 'a storm nest')
t(M + 'guard.terr_salt', '縄張りの白い貝守', 'a territorial white shell-keeper')
t(M + 'guard.terr_ash', '大きな燠の番', 'a great ember-keeper')

# ---------------------------------------------------------------- 帰りの祠
t(M + 'return.rise', '白い光が体を持ち上げる……', 'White light lifts you...')
t(M + 'return.title', '地上へ戻った', 'Back on the surface')
t(M + 'return.sub', '都の下の門の前。深淵の冷えが、少しずつ抜けていく', 'Before the gate under the capital. The chill of the abyss slowly leaves you')

# ---------------------------------------------------------------- 主の名乗り・段・倒れ
LORD = {
    'bone': ('積み上がった骨の王', 'king of piled bones',
             '肋が開いた。骨の雨が来る', 'Its ribs split open. Bone rain is coming',
             '魂の火が骨から噴き出した。床のすべてが危ない', 'Soul fire bursts from the bones. Nowhere on the floor is safe'),
    'salt': ('沈めた重さの主', 'keeper of sunken weight',
             '殻が鳴り、潮の輪が広がりはじめた。主のそばへ', 'Its shell rings; a tide ring spreads. Get close to it',
             '白殻が転がりだした。突進の線から外れろ', 'The white shell starts to roll. Leave the charge line'),
    'storm': ('鳴りやまない鼓動', 'the heartbeat that never stops',
              '雷が十字に走りだした', 'Lightning starts running in a cross',
              '鼓動が速くなる。雷が足もとに集まる', 'The heartbeat quickens; lightning gathers underfoot'),
    'ash': ('灯りを喰う闇', 'the dark that eats light',
            '熾火の雨が降りはじめた', 'Embers begin to rain',
            '灯り喰いが飛びかかってくる。線の外へ', 'The light-eater lunges. Get off the line'),
}
for layer, (ep_ja, ep_en, p2_ja, p2_en, p3_ja, p3_en) in LORD.items():
    t(M + 'lord.%s.epithet' % layer, ep_ja, ep_en)
    t(M + 'lord.%s.phase2' % layer, p2_ja, p2_en)
    t(M + 'lord.%s.phase3' % layer, p3_ja, p3_en)
t(M + 'lord.fell', '%sを倒した', '%s has fallen')
t(M + 'lord.fell_sub', '主の間の奥で、下りの鎖が光っている', 'At the back of the hall, the descending chain glows')
t('objective.skyabyss.abyss_v3.lord_roar', '主が吠えている。刃は通らない。離れて構えろ', 'The lord roars. Blades will not bite. Back off and get ready')

# ---------------------------------------------------------------- ボスバーの段の名前
t('gui.skyabyss.boss.abyss.phase.1', '一の段', 'Phase I')
t('gui.skyabyss.boss.abyss.phase.2', '二の段', 'Phase II')
t('gui.skyabyss.boss.abyss.phase.3', '三の段', 'Phase III')
t('gui.skyabyss.boss.abyss.roaring', '咆哮', 'Roaring')

# 碑の文 (本の lang に入れる)
SIGNS = {
    'sign.skyabyss.abyss_return.1': ('地上へ', 'To the surface'),
    'sign.skyabyss.abyss_return.2': ('白い磁石石に触れよ', 'Touch the white lodestone'),
    'sign.skyabyss.abyss_return.3': ('都の下の門へ帰る', 'Returns you below the capital'),
    'sign.skyabyss.abyss_shaft.1': ('大坑道', 'The Great Shaft'),
    'sign.skyabyss.abyss_shaft.2': ('この下、底の境', 'Below: the threshold'),
    'sign.skyabyss.abyss_shaft.3': ('降りた者の名を刻め', 'Carve the names of those who went down'),
    'sign.skyabyss.abyss_gate.1': ('深淵の門', 'The Abyss Gate'),
    'sign.skyabyss.abyss_gate.2': ('天の鍵で格子は溶ける', 'The sky key melts the bars'),
    'sign.skyabyss.abyss_gate.3': ('帰りは骨の野営の白い石', 'Return by the white stone in the bone camp'),
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main():
    for loc, idx in (('ja_jp', 0), ('en_us', 1)):
        write(os.path.join(OUT, loc + '.json'), {k: v[idx] for k, v in T.items()})
        p = os.path.join(MAIN, loc + '.json')
        with open(p, encoding='utf-8') as f:
            d = json.load(f)
        for k, v in SIGNS.items():
            d[k] = v[idx]
        write(p, d)
    print('wrote', len(T), 'texts and', len(SIGNS), 'sign lines')


if __name__ == '__main__':
    main()
