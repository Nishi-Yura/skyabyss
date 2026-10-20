# -*- coding: utf-8 -*-
"""docs/review/gear_style/index.html を書く (pearl_keeper/index.html と同じ作り。選択は choices/gear_style と choices/gear_anim)。
実行: python tools/rough/lowpoly/gear_style/page.py   (先に make_sheets.py と make_anim.py で docs/review/gear_style/img を作る)"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
PAGE = ROOT / "docs/review/gear_style"
CSS = (ROOT / "docs/review/pearl_keeper/index.html").read_text(encoding="utf-8")
CSS = CSS[CSS.index("<style>"):CSS.index("</style>") + 8]

STYLES = {
    "A": dict(
        name="量塊 (Carved Mass)",
        tag="骨と石の大きな塊。太く、止まって見える",
        refs="モンスターハンター（大剣・素材の塊）、ダークソウル（単純で太い輪郭）",
        pillars=["輪郭は太く大きな 1 つ。細い棒・環・布は使わない", "材質は骨と石の 2 つが主役。苔は上向きの面だけ", "光は 1 か所（宝石・視き穴・樋の付け根）だけ", "防具は面の大きな板。肩は二段の石板、兜は視き穴 1 本"],
        weapons="大剣 = 断ち切りの厚い板（背は直線、先は斜め）／弓 = 太く短い骨弓に石の握り／槍 = 広い葉の穂と下がり羽根／杖 = 骨の環に抱かれた核／短剣 = 肉厚の鉈",
        armor="全面の兜（背びれの冠）、骨の胸板と石の殻、二段の石板の肩（右だけ大きな牙）、太い腿当てと膝の骨、重い鉄靴",
        good="遠目で最も読める（黒塗りで 5 武器と防具がはっきり別）。作りやすく、面の予算に収まる（武器 156〜280 面・防具 680 面）。階級の差も大きな骨の板で出せる",
        bad="「重い戦士」一本調子になりやすい。潮殻・雷翼・余燼の軽い巨神には重い。物語（使い込み）は少ない",
        feel="動き: 溜めが長く、命中で止まる（ヒットストップ 2〜3 tick）。画面が揺れる。重さが主役",
        ),
    "B": dict(
        name="祭具 (Ritual Grace)",
        tag="細く長く、金の縁と垂れる布。動きが見える",
        refs="原神（槍・弓・剣の長い輪郭、環と房）、ゼルダ ティアーズ オブ ザ キングダム（材質の言葉がそろった形）",
        pillars=["輪郭は細く長い。鍔は鯨の尾びれ、頭に環、柄頭に房", "材質は象牙の骨・淡い石・紺の布・金の縁", "光は核と、刃の中心の細い筋", "防具は布が輪郭を作る。頭巾と背に浮く環、長い外套、前垂れ"],
        weapons="大剣 = 細い長刃と尾びれの鍔・日輪の環・吹き流し／弓 = 先が尾びれに反る長弓／槍 = 柳葉の穂と背の半月の環／杖 = 尾びれが抱く真珠／短剣 = 波刃と環の柄頭",
        armor="頭巾と背の環の兜、金の帯の胴衣、小さな淡い石の肩当てと浮く環、背の長い外套、白い前垂れ、足首に小さな尾びれ",
        good="優美で「特別な品」に見える。止まっていても布と環で動きが見える。レジェンドで環を増やすと階級が輪郭で分かる。軽い巨神（潮殻・雷翼）に向く",
        bad="面が増える（レジェンドの大剣 632 面、防具 1,048 面）ので予算に収める作業が要る。細い線が多く、32px で潰れやすい。重さが出にくい",
        feel="動き: 流れる弧、袈裟 → 返しの二連、回して突く。布と環がたなびく。ヒットストップは短く、残像が長い",
        ),
    "C": dict(
        name="遺骸 (Grown Remains)",
        tag="骨そのものを使う。非対称で、使い込まれている",
        refs="エルデンリング・ブラッドボーン（素材そのものの武器、朽ちた装備）、モンスターハンター（怪物の部位を使う）",
        pillars=["輪郭は左右非対称で、欠け・ぎざぎざを 1 点持つ", "骨そのものが武器（顎骨の刃・肋の弓・牙の穂・椎骨の杖頭）", "縄・包帯・護符・ぼろ布・苔で来歴を語る", "光はひびの奥と核だけ（水色）"],
        weapons="大剣 = 顎骨の欠けた刃（歯のぎざぎざ、折れた先の核）／弓 = 長さの違う肋の弓／槍 = ねじれた牙の穂と返し／杖 = 曲がった骨の杖に椎骨の頭／短剣 = 返しのある反った牙",
        armor="頭骨と顎骨の兜（右に長い角、左は折れ）、肋の外骨格と背の椎骨、左だけ大きな椎骨の肩、包帯の脚と骨の膝、爪先の骨爪",
        good="物語がある。巨神の素材らしさが最も出る。非対称で「手で作った品」に見える。レジェンドは肋の反りと鉤が増える",
        bad="輪郭が複雑で、遠目では A より読みにくい。ぼろ布・護符は面が増える（防具 1,176 面）。やりすぎると散らかる",
        feel="動き: 引きずって薙ぎ上げ、叩きつけて反動でよろける。逆手で刺して裂く。乱暴で重心が崩れる",
        ),
}
TRIS = {
    "A": dict(w="大剣 212 / 弓 156 / 槍 170 / 杖 280 / 短剣 176", a="4 部位 680"),
    "B": dict(w="大剣 390 / 弓 276 / 槍 334 / 杖 396 / 短剣 294", a="4 部位 1,048"),
    "C": dict(w="大剣 302 / 弓 268 / 槍 306 / 杖 272 / 短剣 200", a="4 部位 1,176"),
}
CLASSES = [("greatsword", "大剣", "溜めて断つ"), ("spear", "槍", "引いて突く"), ("bow", "弓", "引き・狙い・放ち"), ("staff", "杖", "溜めて放つ"), ("dagger", "短剣", "速い連続")]
ANIM_TXT = {
    ("greatsword", "A"): "断ち下ろし。頂点で一拍止め、一閃、命中で 3 tick 止まって画面が揺れ、刃が沈む。重さの見せ方が主役",
    ("greatsword", "B"): "袈裟斬り → 切り抜け → 返しの逆袈裟。体をひねり、刃が弧を描いて残像が長い",
    ("greatsword", "C"): "低く引きずって沈み、薙ぎ上げ → 頭上を越えて叩きつけ。反動でよろける",
    ("spear", "A"): "大きく引いて一拍止め、一突き。命中で 2 tick 止まり、引き戻す",
    ("spear", "B"): "頭上で回してから突き、返しながら引いて、小さく速い二の突き。吹き流しがなびく",
    ("spear", "C"): "低く沈んですくい上げ、返しの牙を引っかけて引き倒す。肩が痙攣する",
    ("bow", "A"): "重く構えて引く。満月で腕がわずかに震え、放つと反動で弓が跳ね、弦が震える",
    ("bow", "B"): "腕を弧に開いて流れるように引き、放ったあと弓をくるりと返す",
    ("bow", "C"): "肩をねじって乱暴に引き、放つと体ごと弾かれてよろめく",
    ("staff", "A"): "持ち上げて頂点で核が脈打ち、振り下ろして地を突く。衝撃で画面が揺れ、余波で止まる",
    ("staff", "B"): "腕を開いて円を描き、前へ放つと環が広がって浮く。柄を返して戻る",
    ("staff", "C"): "身を縮めて眼（核）が脈動し、突き出して痙攣、引きずって戻す",
    ("dagger", "A"): "引いて重い一突き、命中で 2 tick 止まり、引き抜いて横一文字",
    ("dagger", "B"): "右上から斜め斬り、左下から切り返して X を描き、突きで止めてくるりと回す",
    ("dagger", "C"): "逆手で振り上げて刺し、引き裂き、ねじり、もう一度裂いて振り払う",
}

HEAD = """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>武器・防具の様式 選定</title>
__CSS__
<style>
.sec{border-top:2px solid var(--line);margin-top:40px;padding-top:6px}
.sec h2{font-size:22px;margin:18px 0 6px}
.pill{margin:0 0 10px;padding-left:1.2em;color:var(--ink)}
.pill li{margin:2px 0}
.two{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.kv{font-size:14px;margin:6px 0}
.kv b{display:inline-block;min-width:5.5em;color:var(--accent)}
.good{border-left:4px solid var(--ok);padding:2px 10px;margin:6px 0;font-size:14px}
.bad{border-left:4px solid #c2542d;padding:2px 10px;margin:6px 0;font-size:14px}
.anim img{min-width:1100px}
.anim.scroll{overflow-x:auto}
.rows{display:grid;gap:6px}
.row{display:flex;flex-wrap:wrap;gap:8px 18px;align-items:center;padding:8px 12px;background:var(--panel);border:1px solid var(--line)}
.row .lab{min-width:6em;font-weight:700}
.row label{display:inline-flex;gap:6px;align-items:center;cursor:pointer}
table.t{border-collapse:collapse;width:100%;font-size:13px}
table.t th,table.t td{border:1px solid var(--line);padding:6px 8px;text-align:left;vertical-align:top}
table.t th{background:var(--panel)}
.tiny{font-size:12px;color:var(--muted)}
@media (max-width:760px){ .two{grid-template-columns:minmax(0,1fr)} }
</style>
</head>
<body>
<div class="wrap">
  <h1>武器・防具の様式 選定（3 方向）</h1>
  <p class="intro">ユーザーの要望「防具・武器のモデルを見直して、もっとかっこよくしたい。ダサいものは少し変えたり作り直したり。アニメーションもかっこよく。名前は変えなくていい」への最初の提案です。ほかのゲームの調べ（<code>docs/research/gear_art_research.md</code>）と今の装備の診断（<code>docs/review/gear_audit.md</code>）から、方向を 3 つ作りました。</p>
  <p class="intro"><b>同じ条件で比べます</b>。題材は岩鯨の一式（大剣・弓・槍・杖・短剣と、兜・胸・脚・靴）。どれも低ポリ + 1 ブロック 32 テクセル、階級は表示がレアの形（階級の並びは別に出します）。形・絵・光る画素はこのページ用のラフで、ゲームの設定ではありません。選ぶと、その方向で全装備を順に作り直します。名前・数値は変えません。</p>
  <div class="intro tiny">見方: 各方向の絵は ① 並べた姿 ② 武器 5 点の回転（正面・3/4・横・斜め後ろ）③ 防具の回転（正面・3/4・横・背中）と部位の近接 ④ 手に持った姿（三人称の模式）⑤ 階級の並び（大剣のコモン → レア → レジェンド）⑥ 黒塗り（遠目）。最後に、武器 5 種 x 3 方向の一人称の攻撃の動き（絵コンテ）があります。</div>

  <section class="sec">
    <h2>3 方向の黒塗り比べ（遠目で読めるか）</h2>
    <p class="intro">黒く塗って、大きいサイズと小さいサイズ（遠目）で並べます。武器 5 点と防具の正面。上から A・B・C。</p>
    <figure class="scroll"><img src="img/sil_A.png" alt="A の黒塗り" loading="lazy"><figcaption>A 量塊</figcaption></figure>
    <figure class="scroll"><img src="img/sil_B.png" alt="B の黒塗り" loading="lazy"><figcaption>B 祭具（細い線が多く、小さくすると環が潰れる所がある）</figcaption></figure>
    <figure class="scroll"><img src="img/sil_C.png" alt="C の黒塗り" loading="lazy"><figcaption>C 遺骸（非対称で個性があるが、小さくすると A より混み合う）</figcaption></figure>
  </section>
"""

SEC = """  <section class="sec">
    <h2>__LETTER__ __NAME__　<span class="tiny">__TAG__</span></h2>
    <div class="option">
      <p class="kv"><b>参考</b>__REFS__</p>
      <ul class="pill">__PILLARS__</ul>
      <p class="kv"><b>武器</b>__WEAPONS__</p>
      <p class="kv"><b>防具</b>__ARMOR__</p>
      <p class="kv"><b>面の数</b>武器 __TW__ ／ 防具 __TA__（03 の予算: 通常 250・レア 300・レジェンド 350・防具 4 部位 3,000 以下）</p>
      <div class="good">良い点: __GOOD__</div>
      <div class="bad">気になる点: __BAD__</div>
      <p class="kv"><b>動きの性格</b>__FEEL__</p>
      <figure class="scroll"><img src="img/hero___L__.png" alt="__LETTER__ の一式と武器 5 点" loading="lazy"><figcaption>一式を着た姿（大剣を持つ）と、武器 5 点</figcaption></figure>
      <figure class="scroll"><img src="img/sheet_weapons___L__.png" alt="__LETTER__ の武器 5 点の回転" loading="lazy"><figcaption>武器 5 点（左から 大剣・弓・槍・杖・短剣）。上から 正面・3/4・横・斜め後ろ</figcaption></figure>
      <figure class="scroll"><img src="img/sheet_armor___L__.png" alt="__LETTER__ の防具" loading="lazy"><figcaption>防具 4 部位（上: 正面・3/4・横・背中。下: 兜・胸（肩・背）・脚・靴の近接）</figcaption></figure>
      <figure class="scroll"><img src="img/sheet_inhand___L__.png" alt="__LETTER__ の手に持つ姿" loading="lazy"><figcaption>一式を着て武器を持つ姿（三人称の模式。持ち方は目安で、ゲームの display の値ではありません）</figcaption></figure>
      <figure class="scroll"><img src="img/sheet_tier___L__.png" alt="__LETTER__ の階級の並び" loading="lazy"><figcaption>階級の並び（大剣。左から コモン・レア・レジェンド、各 正面と 3/4）。輪郭が先に変わる</figcaption></figure>
    </div>
  </section>
"""

CHOICE = """  <section class="sec">
    <h2>どの方向にしますか（様式の選択）</h2>
    <p class="intro">全体をまとめて 1 つ選ぶか、武器と防具を別々に選べます（例: 武器は C、防具は A）。「混ぜる」を選んだら、メモに「A の面 + C の使い込み」のように書いてください。</p>
    <div class="rows" id="styleRows">
      <div class="row" data-field="overall"><span class="lab">全体</span>
        <label><input type="radio" name="overall" value="A"> A 量塊</label><label><input type="radio" name="overall" value="B"> B 祭具</label>
        <label><input type="radio" name="overall" value="C"> C 遺骸</label><label><input type="radio" name="overall" value="mix"> 混ぜる（メモに書く）</label></div>
      <div class="row" data-field="weapons"><span class="lab">武器だけ</span>
        <label><input type="radio" name="weapons" value="A"> A</label><label><input type="radio" name="weapons" value="B"> B</label><label><input type="radio" name="weapons" value="C"> C</label></div>
      <div class="row" data-field="armor"><span class="lab">防具だけ</span>
        <label><input type="radio" name="armor" value="A"> A</label><label><input type="radio" name="armor" value="B"> B</label><label><input type="radio" name="armor" value="C"> C</label></div>
    </div>
    <label class="note-l" for="note_style">直してほしい点・混ぜ方・やりすぎ／足りない所（任意）</label>
    <textarea class="note" id="note_style" rows="4"></textarea>
    <div class="save" id="save_style">読み込み中…</div>
    <div class="intro tiny" style="margin-top:10px">私（作った側）の見立て: 武器は A の太い輪郭の上に C の使い込み（巻き・護符・欠け）を足し、B の布と環は<b>レジェンドだけ</b>に使う。防具は A の面の大きさを土台に、B の布（外套・前垂れ）を足して重さと動きの両方を出す。巨神ごとに重さを変える（岩鯨・余燼 = A 寄り、潮殻・雷翼 = B 寄り、深淵の専用伝説 = C 寄り）案も成り立つ。決め手は「遠目で読めること」と「面の予算」。</div>
  </section>
"""

ANIM_HEAD = """  <section class="sec">
    <h2>攻撃の動き（武器 5 種 x 3 方向。一人称の絵コンテ）</h2>
    <p class="intro">8 コマで、上段が一人称（透視。腕は箱の模式）、下段が横からの全身（刃先の軌跡 = 残像つき）。コマの下に、動きの名前と tick（1 tick = 1/20 秒 = 50 ms）。ヒットストップ・画面の揺れ・残像・溜めの長さが方向ごとに違います。これはキー姿勢の絵で、ゲームの動き（<code>SwingMove</code>）に落とすときは、コマの間を補間します。弓は引きの 4 段階の絵を使います。</p>
    <p class="intro tiny">見る所: ① 溜めの長さ（重さ）② 一閃の速さ ③ 命中の止まり（ヒットストップ）④ 残身（流れ）⑤ 武器種ごとの型の違い。一人称の絵は腕が太く見えます（模式）。</p>
"""

ANIM_CLASS = """    <h3 style="margin:26px 0 4px;font-size:19px">__JP__　<span class="tiny">型: __TYPE__</span></h3>
    <div class="option"><div class="rows">__ITEMS__</div>
      <div class="row" data-field="__ID__"><span class="lab">__JP__の動き</span>
        <label><input type="radio" name="__ID__" value="A"> A</label><label><input type="radio" name="__ID__" value="B"> B</label><label><input type="radio" name="__ID__" value="C"> C</label><label><input type="radio" name="__ID__" value="mix"> 混ぜる</label></div>
    </div>
"""
ANIM_ITEM = """      <div><div class="tiny"><b>__L__ __NAME__</b>（__TITLE__）: __TXT__</div>
      <figure class="scroll anim"><img src="img/anim___L_____ID__.png" alt="__JP__ __L__ の絵コンテ" loading="lazy"></figure></div>
"""

TAIL = """    <label class="note-l" for="note_anim">動きについて（溜め・止まり・残像・速さ・画面の揺れの強さなど。任意）</label>
    <textarea class="note" id="note_anim" rows="4"></textarea>
    <div class="save" id="save_anim">読み込み中…</div>
  </section>

  <section class="sec">
    <h2>参考</h2>
    <p class="intro">調べ: <code>docs/research/gear_art_research.md</code>（かっこよさの作り方・10 か条・動きの原則）／診断: <code>docs/review/gear_audit.md</code>（今の全装備の keep / tweak / rebuild）／作り方: <code>tools/rough/lowpoly/gear_style/</code>（<code>make_sheets.py</code> 絵、<code>make_anim.py</code> 動き、<code>page.py</code> このページ）。</p>
    <table class="t"><tr><th>武器種</th><th>型</th><th>全体 (tick)</th><th>溜め</th><th>命中の止まり</th></tr>
      <tr><td>大剣</td><td>溜めて断つ</td><td>28〜36</td><td>12〜13</td><td>A 3 / B なし / C 画面の揺れ</td></tr>
      <tr><td>槍</td><td>引いて突く</td><td>26〜32</td><td>7〜12</td><td>A 2 / B 二連 / C 引き倒し</td></tr>
      <tr><td>弓</td><td>引き・狙い・放ち</td><td>26〜34</td><td>引き 12〜18</td><td>反動 + 弦の震え（A 重い・B 返す・C 弾かれる）</td></tr>
      <tr><td>杖</td><td>溜めて放つ</td><td>28〜32</td><td>12〜14</td><td>A 地を突く / B 環が浮く / C 痙攣</td></tr>
      <tr><td>短剣</td><td>速い連続</td><td>20〜24</td><td>3〜4</td><td>A 2 / B 連斬り / C 逆手の裂き</td></tr></table>
  </section>
</div>
<script>
(() => {
  const dbp = (async () => { const c = window.claude && window.claude.use ? window.claude : null; return c ? await c.use('db') : null; })();
  function wire(docId, fields, noteId, saveId) {
    const noteEl = document.getElementById(noteId), saveEl = document.getElementById(saveId);
    let db = null, timer = null;
    const state = () => {
      const o = { at: Date.now(), note: noteEl.value };
      for (const f of fields) { const r = document.querySelector('input[name=' + f + ']:checked'); o[f] = r ? r.value : ''; }
      return o;
    };
    const save = async () => {
      if (!db) return;
      saveEl.textContent = '保存中…';
      try { await db.doc('choices/' + docId).set(state()); saveEl.textContent = '保存しました'; }
      catch (e) { saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
    };
    for (const f of fields) document.querySelectorAll('input[name=' + f + ']').forEach(r => r.addEventListener('change', save));
    noteEl.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(save, 800); });
    (async () => {
      db = await dbp;
      if (!db) { saveEl.textContent = 'この表示では選択を保存できません (サインインして開いてください)'; return; }
      try {
        const snap = await db.collection('choices').get();
        const docs = snap.docs || snap;
        for (const d of docs) {
          if (d.id !== docId) continue;
          const data = typeof d.data === 'function' ? d.data() : d.data;
          if (!data) continue;
          for (const f of fields) if (data[f]) { const r = document.querySelector('input[name=' + f + '][value="' + data[f] + '"]'); if (r) r.checked = true; }
          if (data.note) noteEl.value = data.note;
        }
        saveEl.textContent = '選ぶと自動で保存されます';
      } catch (e) { saveEl.textContent = '前回の選択を読み込めませんでした'; }
    })();
  }
  wire('gear_style', ['overall', 'weapons', 'armor'], 'note_style', 'save_style');
  wire('gear_anim', __CLASSES__, 'note_anim', 'save_anim');
})();
</script>
</body>
</html>
"""


def esc(s):
    return s


def main():
    h = HEAD.replace("__CSS__", CSS)
    out = [h]
    for L in "ABC":
        d = STYLES[L]
        s = SEC
        for k, v in dict(LETTER=L, NAME=d["name"], TAG=d["tag"], REFS=d["refs"], WEAPONS=d["weapons"], ARMOR=d["armor"], TW=TRIS[L]["w"], TA=TRIS[L]["a"],
                         GOOD=d["good"], BAD=d["bad"], FEEL=d["feel"]).items():
            s = s.replace("__" + k + "__", v)
        s = s.replace("__PILLARS__", "".join(f"<li>{p}</li>" for p in d["pillars"])).replace("__L__", L)
        out.append(s)
    out.append(CHOICE)
    out.append(ANIM_HEAD)
    import importlib
    sys.path.insert(0, str(HERE))
    seqs = importlib.import_module("seqs")
    for cid, jp, typ in CLASSES:
        items = ""
        for L in "ABC":
            it = ANIM_ITEM
            title = seqs.SEQ[(cid, L)]["title"]
            for k, v in dict(L=L, NAME=STYLES[L]["name"].split(" ")[0], TITLE=title, TXT=ANIM_TXT[(cid, L)], ID=cid, JP=jp).items():
                it = it.replace("__" + k + "__", v)
            items += it
        s = ANIM_CLASS
        for k, v in dict(JP=jp, TYPE=typ, ITEMS=items, ID=cid).items():
            s = s.replace("__" + k + "__", v)
        out.append(s)
    t = TAIL.replace("__CLASSES__", json.dumps([c[0] for c in CLASSES]))
    out.append(t)
    (PAGE / "index.html").write_text("".join(out), encoding="utf-8")
    print("wrote", PAGE / "index.html")


if __name__ == "__main__":
    main()
