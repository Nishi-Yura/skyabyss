"""docs/review/weapon_detail/index.html を作る (armor_series の骨組みを流用)。実行: python wdetail_page.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
R = ROOT / "docs/review"
src = (R / "armor_series/index.html").read_text(encoding="utf-8")
css = src[src.index('<style>'):src.index('</style>') + 8]
st = json.loads((R / "weapon_detail/img/stats.json").read_text(encoding="utf-8"))
W = [("whalebone_greatsword", "鯨骨の大剣", "岩鯨", "大剣", "白い鯨の骨と青灰の石。鍔の円盤・刃の根・柄に飾りが足されます。"),
     ("tide_shell_trident", "潮殻の三叉槍", "潮殻", "槍", "真珠層の穂と薔薇色の帯。柄の環・穂の山形・真珠が足されます。"),
     ("thunder_wing_bow", "雷翼の弓", "雷翼", "弓", "銅の肢と風切羽。肢の環・光の筋・握りの巻きが足されます。"),
     ("waterway_fang", "水路の牙", "岩鯨", "短剣", "反った 1 本の牙。刃の光の筋・柄の巻き・背の副牙が足されます。"),
     ("ember_war_axe", "余燼の戦斧", "余燼", "斧", "割れた玄武岩の頭と鉄の帯。頭の割れ目の光・鉄板の飾りが足されます。"),
     ("coral_shield", "珊瑚の盾", "潮殻", "盾", "帆立の貝殻の盾。縁の光の輪・中央の金具・珊瑚の鋲が足されます。")]
LV = [("D1", "D1 今のまま", "今ゲームに入っている形と同じ。基準です。"),
      ("D2", "D2 整え", "輪郭は同じまま、角を面取りし、柄・鍔に金属の環、宝石、光る細い線を 1 本足す。"),
      ("D3", "D3 細密", "D2 に加えて、浮き彫りの山形・巻いた柄・重ねた鍔・その巨神の副モチーフ (牙・珊瑚・羽・鉄板) を足す。")]
tot = {l: sum(st[w[0]][l] for w in W) for l, _, _ in LV}


def pct(w, l):
    return round((st[w][l] / st[w]['D1'] - 1) * 100)


rows = ''.join(f"<tr><th>{w[1]} ({w[3]})</th>" + ''.join(
    f"<td>{st[w[0]][l]}" + ('' if l == 'D1' else f" (+{pct(w[0], l)}%)") + "</td>" for l, _, _ in LV) + "</tr>" for w in W)
rows += "<tr><th>6 本の合計</th>" + ''.join(
    f"<td>{tot[l]}" + ('' if l == 'D1' else f" (+{round((tot[l] / tot['D1'] - 1) * 100)}%)") + "</td>" for l, _, _ in LV) + "</tr>"


def radio(name, opts):
    return ''.join(f'<label class="opt"><input type="radio" name="{name}" value="{v}"><span class="letter">{v[:2] if v != "mix" else "混"}</span><span>{t}</span></label>' for v, t in opts)


LVO = [("D1", "D1 今のまま"), ("D2", "D2 整え"), ("D3", "D3 細密")]
sections = ''
for wid, jp, ti, kind, desc in W:
    t = st[wid]
    held = ''.join(f'<figure><img src="img/held_{wid}_{l}.png" alt="{jp} {l} を持った姿" loading="lazy"><figcaption>{l} 手に持った姿 (防具 S2 {ti}・レア)</figcaption></figure>' for l, _, _ in LV)
    picks = ''.join(f'<label><input type="radio" name="pick_{wid}" value="{v}"> {t_}</label>' for v, t_ in LVO)
    sections += f'''<div class="option" id="w_{wid}"><h3>{jp} <small>({kind}・{ti})</small></h3><p>{desc} 三角形: D1 {t['D1']} / D2 {t['D2']} / D3 {t['D3']}。</p>
<figure class="scroll"><img src="img/sheet_{wid}.png" alt="{jp} 3 段階の比べ" loading="lazy" style="min-width:760px"><figcaption>上から D1 / D2 / D3。左から 正面・斜め・背面・防具 S2 を着て持った姿。</figcaption></figure>
<details class="wn"><summary>手に持った姿を大きく見る</summary><div class="held">{held}</div></details>
<div class="row" data-field="{wid}"><span class="lab">{jp}の細かさ</span>{picks}</div>
<label class="note-l" for="note_{wid}">{jp}の感想 (任意)</label><textarea class="note" id="note_{wid}" rows="2"></textarea><div class="save" id="save_{wid}">読み込み中…</div></div>
'''
fam = ''.join(f'<div class="option"><h3>{n}</h3><p>{d} 6 本の合計 {tot[l]} 面。</p><figure class="scroll"><img src="img/family_{l}.png" alt="{n} 6 本" loading="lazy" style="min-width:900px"></figure></div>' for l, n, d in LV)
extra_css = '.held{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.held img{min-width:0}.wsel{display:flex;flex-wrap:wrap;gap:8px 14px;align-items:center;justify-content:center}@media (max-width:760px){.held{grid-template-columns:minmax(0,1fr)}}</style>'
overall = radio("overall", [("D1", "D1 今のまま"), ("D2", "D2 整え (おすすめ)"), ("D3", "D3 細密"), ("mix", "混ぜる (下に書く)")])
head = f'''<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>武器の細かさ 選定</title>
{css.replace('</style>', extra_css)}
</head>
<body>
<div class="wrap">
<h1>武器の細かさ — 防具 S2 祭に合わせる</h1>
<p class="intro">防具は S2 祭 (布の輪郭に、要所だけの面取りした板・重ねた縁・宝石・細い光の線) に決まりました。ここでは武器をどこまで細かくするかを 3 段階で見比べます。どの段階でも<b>量塊 (厚いブロック) の言葉は使わず、武器の名前と輪郭は今のまま、閉じた立体 (中が見えない)</b> です。</p>
<p class="intro">代表 6 本 (大剣・槍・弓・短剣・斧・盾) を 4 巨神にまたがって選びました。レアの見た目で、防具 S2 のレアを着たマネキンに持たせた姿も付けています。ゲームには入れていません (ラフです)。画像はドット絵の拡大のため少しにじみます。</p>
<section class="sec"><h2>3 段階で何が変わるか</h2>
<table class="t"><tr><th>段階</th><th>やること</th><th>見た目の変化</th></tr>
<tr><th>D1 今のまま</th><td>手を入れない。基準。</td><td>今ゲームに入っている武器そのもの。防具 S2 と並べると、武器だけ飾りが少なく見えます。</td></tr>
<tr><th>D2 整え</th><td>輪郭は同じ。大きな箱の角を面取り (角を斜めに落とす) し、柄や鍔に金属の細い環、宝石を 1 つ、<b>光る細い線を 1 本</b>足す。</td><td>防具の「面取りした板・重ねた縁・宝石・細い光の線」と同じ言葉になります。遠目の形は変わらず、近づくと整って見えます。</td></tr>
<tr><th>D3 細密</th><td>D2 に加えて、浮き彫りの山形の模様、巻いた柄、重ねた鍔、巨神の副モチーフ (牙・珊瑚・羽・鉄板・真珠) を足す。</td><td>祭具らしい密度になります。そのぶん三角形が増え、小さく持つと細かい飾りは潰れて見えやすい。輪郭は読めるまま。</td></tr></table>
<div class="scroll"><table class="t"><tr><th>三角形の数 (レア)</th><th>D1 今のまま</th><th>D2 整え</th><th>D3 細密</th></tr>{rows}</table></div>
<p class="intro"><b>おすすめ: D2 整え。</b>防具 S2 とそろい、軽さも保てます (1 本あたり +23〜50%)。D3 は倍近くまで増える武器があり、手に持つと細部が見えにくいので、「伝説階級だけ D3」のように使い分けるのが良さそうです。好みで D3 を選んでも構いません。</p>
</section>
<section class="sec"><h2>全体: 6 本を 3 段階で並べて</h2><p class="intro">左から 鯨骨の大剣・潮殻の三叉槍・雷翼の弓・水路の牙・余燼の戦斧・珊瑚の盾。</p>{fam}
<fieldset class="opts" role="radiogroup" aria-label="全体の細かさ">{overall}</fieldset>
<label class="note-l" for="note_wd">全体の感想 (混ぜたい場合は、どの武器をどの段階にしたいか。任意)</label><textarea class="note" id="note_wd" rows="4"></textarea><div class="save" id="save_wd">読み込み中…</div></section>
<section class="sec"><h2>回転して見る (ドラッグか下のスライダー)</h2><div class="viewer" id="viewer"><div class="wsel"><span>武器</span><span class="seg" data-k="w"></span></div><div class="wsel"><span>段階</span><span class="seg" data-k="l"></span></div><div class="vstage" id="vstage" style="aspect-ratio:150/210" role="img" aria-label="回転する武器"></div><input type="range" id="vrange" min="0" max="11" value="0" aria-label="回転"><label class="auto"><input type="checkbox" id="vauto" checked> 自動で回す</label></div></section>
<section class="sec"><h2>武器ごとに見て、選ぶ</h2><p class="intro">好みが武器ごとに違う場合は、ここで 1 本ずつ選んでください (全体の選択とは別に保存されます)。</p>{sections}</section>
<section class="sec"><h2>参考</h2><p class="intro">防具 S2 祭: <a href="../armor_series/index.html">armor_series</a>。作り方: <code>tools/rough/lowpoly/weapons/wdetail.py</code> (D2/D3 は今の item_*.py の形に、面取りと追加部品を足して作っています)。</p></section>
</div>
'''
js = src[src.index('<script>'):]
a = js.index("  wire('armor_series'")
b = js.index("  // 回転ビューア")
js = js[:a] + "  wire('weapon_detail', [['overall', 'overall']], 'note_wd', 'save_wd');\n  for (const t of " + json.dumps([w[0] for w in W]) + ") wire('weapon_detail_' + t, [['pick', 'pick_' + t]], 'note_' + t, 'save_' + t);\n" + js[b:]
c = js.index("  const O = [")
d = js.index("  const stage = ")
js = js[:c] + "  const Wl = " + json.dumps([[w[0], w[1]] for w in W], ensure_ascii=False) + ", Ll = [['D1','D1 今のまま'],['D2','D2 整え'],['D3','D3 細密']];\n  const st = { w: Wl[0][0], l: 'D2', f: 0 };\n" + js[d:]
js = js.replace("const lists = { o: O, t: T, r: R };", "const lists = { w: Wl, l: Ll };")
js = js.replace("'url(img/turn_' + st.o + '_' + st.t + '_' + st.r + '.png)'", "'url(img/turn_' + st.w + '_' + st.l + '.png)'")
(R / "weapon_detail/index.html").write_text(head + js, encoding="utf-8")
print(tot, [pct(w[0], 'D2') for w in W], [pct(w[0], 'D3') for w in W])
