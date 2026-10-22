# docs/review/markers/index.html を書く (選定ページ。docs/review/anemone/index.html の作りを借りる)
import re

src = open('docs/review/anemone/index.html', encoding='utf-8').read()
head = src[:src.index('<style>')].replace('灯の絵 選定', '光の目印 選定')
css = re.search(r'<style>(.*?)</style>', src, re.S).group(1)
css += '''
.opts{grid-template-columns:repeat(4,minmax(0,1fr))}
.opt{align-items:flex-start}
.gal{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:10px 0 18px}
.gal figure{margin:0}
.gal img{display:block;width:100%;height:auto;border:1px solid var(--line);background:#161c22}
.gal figcaption{font-size:12px;color:var(--muted)}
h3{font-size:16px;margin:22px 0 4px}
@media (max-width:760px){.opts{grid-template-columns:minmax(0,1fr)}.gal{grid-template-columns:minmax(0,1fr)}}
'''


def gal(items):
    return '<div class="gal">' + ''.join(
        f'<figure><img src="{f}.jpg" alt="" loading="lazy"><figcaption>{c}</figcaption></figure>' for f, c in items) + '</div>'


S = ['current', 'a', 'b', 'c']
N = {'current': '今の', 'a': 'A', 'b': 'B', 'c': 'C'}
board = ''
for s in S:
    board += f'<h3>{N[s]}{"" if s == "current" else " 案"}（余燼の口）</h3>' + gal([
        (f'board_ember_{s}_far_day', '遠く・昼'), (f'board_ember_{s}_far_night', '遠く・夜'),
        (f'board_ember_{s}_near_day', '近く・昼'), (f'board_ember_{s}_near_night', '近く・夜')])
board += '<h3>巨神ごとの形（夜・近く。縦の柱の岩鯨・雷翼、横の口の潮殻）</h3>' + gal(
    [(f'board_{t}_{s}_near_night', f'{tn} {s.upper()}') for t, tn in (('stone_whale', '岩鯨'), ('thunder_wing', '雷翼'), ('tide_shell', '潮殻')) for s in 'abc'])
board += '<h3>縦の柱を遠くから（昼。岩鯨・雷翼）</h3>' + gal(
    [(f'board_{t}_{s}_far_day', f'{tn} {N[s]}') for t, tn in (('stone_whale', '岩鯨'), ('thunder_wing', '雷翼')) for s in S])
ret = ''
for s in S:
    ret += f'<h3>{N[s]}{"" if s == "current" else " 案"}（岩鯨の背中）</h3>' + gal([
        (f'return_{s}_far_day', '遠く(60)・昼'), (f'return_{s}_far_night', '遠く(60)・夜'),
        (f'return_{s}_near_day', '近く・昼'), (f'return_{s}_near_night', '近く・夜')])
ret += '<h3>B 案は巨神ごとに紋様と粒が変わります（夜・近く）</h3>' + gal([
    (f'return_b_{t}_night', tn) for t, tn in (('stone_whale', '岩鯨: 波の輪・泡のような粒'), ('tide_shell', '潮殻: 巻き貝の渦・オウム貝の粒'),
                                              ('thunder_wing', '雷翼: 稲妻の星・火花'), ('ember', '余燼: 炎の花弁・小さな炎'))])
BO = {'a': ('A 光の筋と床の紋様', '太く柔らかい光の筋が昇り、足もとに巨神の紋様の輪。輪が入口へ縮むように脈打つ。遠くでは筋が太く濃くなる'),
      'b': ('B 欠片の輪と矢印', '巨神の色の欠片が入口を二重に回り、細い筋と、入口へ流れる山形の矢印が地面に。遠くでは筋が濃く'),
      'c': ('C 体が応える', '入口の縁に灯と菱形のルーンが順に走り、両脇に提灯、入口へ流れる光の綱。縦の柱では螺旋の綱。ごく淡い標つき'),
      'current': ('今のまま', '光の柱と直線の枠')}
RO = {'a': ('A 浮かぶ灯の精', '小さな光の玉が浮かび、足もとの淡い輪に踏み込む。細い糸で床とつながる'),
      'b': ('B 床のルーンの輪', '巨神ごとの紋様の輪が回り、粒が立ちのぼる。ごく淡い光の筒つき'),
      'c': ('C 光の拱門', '揺らぐ光の幕の拱門。縁を光が走る。常にこちらを向く'),
      'current': ('今のまま', '光の柱と直線の枠')}


def radios(name, T):
    return ''.join(
        f'<label class="opt"><input type="radio" name="{name}" value="{k}"><span class="letter">{"今" if k == "current" else k.upper()}</span>'
        f'<span>{T[k][0]}<small>{T[k][1]}</small></span></label>' for k in ['a', 'b', 'c', 'current'])


script = '''
(() => {
  const parts = [
    { name: 'board', doc: 'board_marker', note: document.getElementById('board_note'), saveEl: document.getElementById('board_save') },
    { name: 'ret', doc: 'return_marker', note: document.getElementById('return_note'), saveEl: document.getElementById('return_save') }
  ];
  let db = null;
  const chosen = p => { const r = document.querySelector(`input[name=${p.name}]:checked`); return r ? r.value : ''; };
  async function save(p) {
    if (!db) return;
    p.saveEl.textContent = '保存中…';
    try {
      await db.doc('choices/' + p.doc).set({ option: chosen(p), note: p.note.value, at: Date.now() });
      p.saveEl.textContent = '保存しました';
    } catch (e) { p.saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
  }
  for (const p of parts) {
    document.querySelectorAll(`input[name=${p.name}]`).forEach(r => r.addEventListener('change', () => save(p)));
    let timer = null;
    p.note.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => save(p), 800); });
  }
  (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    db = c ? await c.use('db') : null;
    if (!db) { parts.forEach(p => p.saveEl.textContent = 'この表示では選択を保存できません (サインインして開いてください)'); return; }
    try {
      const snap = await db.collection('choices').get();
      const docs = snap.docs || snap;
      for (const d of docs) {
        const p = parts.find(x => x.doc === d.id);
        if (!p) continue;
        const data = typeof d.data === 'function' ? d.data() : d.data;
        if (!data) continue;
        if (data.option) { const r = document.querySelector(`input[name=${p.name}][value="${data.option}"]`); if (r) r.checked = true; }
        if (data.note) p.note.value = data.note;
      }
      parts.forEach(p => p.saveEl.textContent = '選ぶと自動で保存されます');
    } catch (e) { parts.forEach(p => p.saveEl.textContent = '前回の選択を読み込めませんでした'); }
  })();
})();
'''

body = f'''</head>
<body>
<div class="wrap">
  <h1>入る場所の光・帰る場所の光</h1>
  <p class="intro">どちらも硬い四角い枠をやめて、柔らかい光・床の紋様・浮かぶ欠片・点の連なりで見せる 3 案です。ゲームのクライアント設定の boardMarker と returnMarker で切り替えられ、いまの既定は「今のまま」です。選んだ案を既定に入れ替えます。絵は撮影用の平らな世界に岩の口を建てて撮ったもので、実際の巨神の体とは周りの見た目が違います。</p>

  <h2>1. 入る場所の目印</h2>
  <fieldset class="opts" id="board_opts">{radios('board', BO)}</fieldset>
  <label class="note-l" for="board_note">直してほしい点 (任意)</label>
  <textarea id="board_note" class="note" rows="2" placeholder="例: Aの筋を、もっと遠くから見えるように"></textarea>
  <div class="save" id="board_save">読み込み中…</div>
  {board}

  <h2>2. 帰る場所の光</h2>
  <fieldset class="opts" id="return_opts">{radios('ret', RO)}</fieldset>
  <label class="note-l" for="return_note">直してほしい点 (任意)</label>
  <textarea id="return_note" class="note" rows="2" placeholder="例: Cの幕を、もっと淡く"></textarea>
  <div class="save" id="return_save">読み込み中…</div>
  {ret}
</div>
<script>{script}</script>
</body>
</html>
'''
open('docs/review/markers/index.html', 'w', encoding='utf-8').write(head + '<style>' + css + '</style>\n' + body)
