"""職の切り替え (戦いと職の頁とトップ)。Wynncraft の職の頁と MineShoku の職の欄を手本にした形:
左に職の立ち姿、真ん中に説明と三つの型 (五つの帯)、右に六つの職の一覧。選ぶと切り替わる (js/main.js)。

職・型・使う技の並びは docs/design/43・48 と assets/skyabyss/skill_tree/<職>.json に合わせる。型の帯 (1〜5) は遊びの手ごたえの目安で、
溜まるもの (48 の 2.6) と技の働きから決めた。技の名前は lang から引く。
"""
from sitelib import esc, lang

STATS = ["難しさ", "攻め", "守り", "届く距離", "速さ"]

# (id, 名前, 副題, 武器, 役目, 主な基礎, 説明, 幹の技, 使う技, 動く絵の技, [(型, 一言, 溜まるもの, (難, 攻, 守, 距, 速))])
JOBS = [
    ("warrior", "戦士", "岩守り", "剣・大剣・鉾・槍・盾", "前に立つ壁", "筋力・耐久",
     "前に立って敵の狙いを引きつけ、仲間の前で受け止めます。大きな相手を斬る巨神斬りが幹の技です。盾で耐えるか、大剣で斬るか、振り回して群れを薙ぐかを型で選びます。",
     "titan_cleave", ["titan_cleave", "shield_rush", "rock_call", "shield_bash", "bone_swing", "whale_sweep", "spin_cut"], "titan_cleave",
     [("盾の守り手", "傷を受けるほど重みが溜まり、満ちた盾殴りが岩を砕く。", "岩の重み", (2, 2, 5, 1, 2)),
      ("巨神斬りの剣士", "ふつうの斬りで研ぎを溜め、巨神斬りに全部をのせる。", "研ぎ", (3, 5, 2, 1, 3)),
      ("鯨尾の振り回し", "回し斬りで当てるほど勢いがつき、振りも足も速くなる。", "勢い", (2, 3, 3, 2, 4))]),
    ("smith", "鍛冶師", "灰の鍛え人", "鎚・斧", "火の重い一撃", "筋力・精神",
     "鎚と斧で、火をまとった重い一撃を振るいます。武器を焼き入れて鍛え、灰と火花で敵の群れを崩します。技を使うほど炉が熱くなる職です。",
     "temper", ["temper", "ember_hammer", "forge_stomp", "ash_scatter", "spark_burst"], "ember_hammer",
     [("熾火の槌", "技で炉の熱を溜め、満ちた熾火の槌が前に火の柱を立てる。", "炉の熱", (2, 5, 3, 2, 2)),
      ("焼き入れ", "焼き入れた武器で当てて鍛えを溜め、炎の傷を重ねる。", "鍛え", (3, 4, 3, 1, 3)),
      ("灰散らし", "灰をかぶせた敵に技を当てて層を重ね、火花散らしで弾けさせる。", "灰の層", (4, 4, 2, 3, 3))]),
    ("mage", "魔法使い", "星読み", "杖", "遠くからの魔法", "精神",
     "杖で遠くから魔法を撃ちます。雷・火・潮の三つの型を持ち、守りは薄いぶん、届く距離と攻めは随一です。転移で間合いを取り直せます。",
     "thunder_bolt", ["thunder_bolt", "ember_orb", "stardust", "frost_tide", "blink"], "stardust",
     [("雷", "雷の矢を重ねた敵から、まわりの敵へ雷が跳ねる。", "帯電", (3, 4, 1, 5, 3)),
      ("火", "燃えている敵に当てるほど、熾火の玉が大きく強くなる。", "熾火", (2, 5, 1, 4, 2)),
      ("潮", "凍て潮で冷やした敵を凍らせ、凍った敵へ雷の矢を通す。", "凍え", (4, 3, 3, 4, 3))]),
    ("archer", "弓使い", "骨の射手", "弓・クロスボウ", "遠くからの攻めと罠", "敏捷",
     "弓とクロスボウで遠くから狙い、罠と足止めの矢で敵の足を止めます。動かずに狙うか、罠を張るか、矢を雨のように降らせるかを型で選びます。",
     "cloud_pierce", ["cloud_pierce", "archer_leap", "snare_shot", "bone_trap", "sky_rain"], "sky_rain",
     [("雲穿ち", "動かずに狙いを溜め、雲穿ちで何体も貫く。", "狙い", (3, 5, 1, 5, 2)),
      ("骨の罠", "罠どうしを糸でつなぎ、足止めの矢でまとめてはぜさせる。", "罠の糸", (4, 3, 3, 3, 3)),
      ("天の驟雨", "ふつうの矢で矢筒を満たし、天の驟雨でまとめて降らせる。", "空の矢筒", (2, 4, 2, 4, 4))]),
    ("assassin", "暗殺者", "風読み", "短剣・牙・爪", "速い攻めと背後", "敏捷",
     "短剣・牙・爪で素早く踏み込み、背後から刺します。霧に隠れ、跳び退いて、また詰める。手数と速さの職です。",
     "lunge", ["lunge", "backstep", "mist_veil", "twin_fang", "wind_rend"], "wind_rend",
     [("影の刺客", "一体に影の印を重ね、風裂きで一度に弾けさせる。", "影の印", (3, 5, 1, 1, 4)),
      ("風の舞い手", "違う技をつなぐほど舞が重なり、傷が増え、待ちが縮む。", "舞", (5, 4, 2, 2, 5)),
      ("孤風", "まわりに仲間がいないあいだに溜め、次の一撃を 2.5 倍にする。", "孤り", (3, 5, 2, 1, 3))]),
    ("shaman", "呪術師", "潮歌い", "鈴・祭具", "支えと崩し", "精神",
     "鈴と祭具で唄い、仲間を守り、敵を引き寄せ、呪いで崩します。パーティの支え役ですが、呪いの型なら一人でも削れます。",
     "ward_song", ["ward_song", "ebb_tide", "lamp_charm", "cycle_song", "curse_mark"], "ward_song",
     [("守りの唄", "唄で調べを溜め、灯の守り札を広く長くする。", "調べ", (2, 1, 5, 3, 2)),
      ("引き潮", "引き潮で敵を引くたびに潮位が上がり、満ち潮で押し流す。", "潮位", (3, 3, 3, 3, 3)),
      ("呪い", "同じ敵に呪いの印を重ね、三つで弾けて隣の敵へ移る。", "呪いの重ね", (4, 4, 2, 3, 2))]),
]


def skill_name(sid):
    return lang("skill.skyabyss." + sid)


def bars(vals):
    rows = []
    for name, v in zip(STATS, vals):
        cells = "".join('<i class="on"></i>' if i < v else "<i></i>" for i in range(5))
        rows.append('<div class="bar-row"><span>%s</span><span class="cells" role="img" aria-label="%s %d / 5">%s</span></div>' % (name, name, v, cells))
    return '<div class="bars">%s</div>' % "".join(rows)


def panel(j, first):
    jid, name, sub, weapons, role, stat, desc, trunk, skills, clip, archs = j
    arch_html = "".join(
        '<div class="arch"><h4>%s</h4><p>%s</p><p class="gauge">溜まるもの: <b>%s</b></p>%s</div>' % (a, line, gauge, bars(vals))
        for a, line, gauge, vals in archs)
    chips = "".join('<span class="chip%s">%s</span>' % (" g" if s == trunk else "", skill_name(s)) for s in skills)
    return """<div class="job-panel" id="job-%(id)s" role="tabpanel" aria-labelledby="tab-%(id)s"%(hid)s>
<div class="job-figure"><img src="img/job/%(id)s.webp" alt="%(name)sの装いで武器を構えた姿" loading="%(load)s"></div>
<div class="job-body">
<p class="kicker">%(sub)s</p>
<h3>%(name)s</h3>
<p>%(desc)s</p>
<dl class="job-facts"><dt>武器</dt><dd>%(weapons)s</dd><dt>役目</dt><dd>%(role)s</dd><dt>主な基礎</dt><dd>%(stat)s</dd></dl>
<div class="archs">%(archs)s</div>
<div class="job-skills"><p class="small">使う技（木の中から 4 つまで。金の枠が幹の技）</p><div class="chips">%(chips)s</div></div>
<figure class="job-clip"><img src="img/skill_fx/%(clip)s.webp" alt="%(clipname)sを使ったところ" width="640" height="360" loading="lazy"><figcaption>%(clipname)s</figcaption></figure>
</div>
</div>""" % dict(id=jid, name=name, sub=sub, desc=desc, weapons=weapons, role=role, stat=stat, archs=arch_html, chips=chips,
                  clip=clip, clipname=skill_name(clip), hid="" if first else " data-hide", load="eager" if first else "lazy")


def switcher():
    tabs = "".join(
        '<button type="button" role="tab" id="tab-%s" aria-controls="job-%s" aria-selected="%s" data-job="%s">'
        '<img class="face" src="img/job/%s_face.webp" width="48" height="48" alt=""><span><b>%s</b><small>%s</small></span></button>'
        % (j[0], j[0], "true" if i == 0 else "false", j[0], j[0], j[1], j[2]) for i, j in enumerate(JOBS))
    panels = "".join(panel(j, i == 0) for i, j in enumerate(JOBS))
    return '<div class="jobs-ui"><div class="job-tabs" role="tablist" aria-label="職を選ぶ">%s</div><div class="job-panels">%s</div></div>' % (tabs, panels)


def strip():
    """トップの職の帯: 六人の立ち姿を並べ、戦いと職の頁の各職へ"""
    return '<div class="job-strip">%s</div>' % "".join(
        '<a href="combat.html#job-%s"><img src="img/job/%s.webp" alt="%s" loading="lazy"><b>%s</b><small>%s</small></a>'
        % (j[0], j[0], esc(j[1]), j[1], j[2]) for j in JOBS)
