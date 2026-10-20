"""docs/review/armor_series/index.html を作る (画像は tools/rough/lowpoly/armor/build_series.py が img/ に出す)。
実行: python tools/armor_series_page.py"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
D = ROOT / "docs/review/armor_series"
stats = json.loads((D / "img/stats.json").read_text(encoding="utf-8"))

TITANS = ["whale", "tide", "thunder", "ember"]
JP = dict(whale="岩鯨", tide="潮殻", thunder="雷翼", ember="余燼")
TIERS = [("common", "通常"), ("rare", "レア"), ("legend", "伝説")]
OPTS = [
    ("S1", "S1 凛", "飾りを絞った細身",
     "頭巾は頭に沿うキャップ。額の前へ立つ刃が 1 枚と、耳の上の小さな側翼だけ。肩は 1 段の丸い板で、外套は腰まで、前垂れはなし。"
     "静かで軽く、ひと目で「祭具の人」と分かる最小の形。レアで側翼が増え、伝説で背の環が付く。"),
    ("S2", "S2 祭", "標準 (B 祭具にいちばん近い)",
     "頭の弧に沿って 3 枚並ぶ冠 (額 → 頭頂 → うなじ) と耳の上の側翼、頬当て。肩は 2 段で右が大きく、右肩に大きな刃。"
     "帯の垂れが 2 本、膝上まで広がる外套と背の飾り帯、鈴口の袖、脚の前垂れ。飾りと布の量はいちばん釣り合っている。"),
    ("S3", "S3 盛", "布と飾りが多い (祭礼の盛装)",
     "後頭に 7 枚の刃が放射状に開く冠 (光背)。肩は 3 段、胸に 2 枚目の板、袖に帯、外套は足首近くまで、帯の垂れが 3 本、前垂れも長い。"
     "遠目にいちばん輪郭が大きく、伝説の背の環と合わせると神官の姿になる。そのぶん面は多い。"),
]
TITAN_ROWS = [
    ("whale", "岩鯨 (量)", "鯨骨の反った刃・青灰の石 (苔のまだら)", "生成りの縄", "水色", "まっすぐ", "骨と石の落ち着き。冠・肩・足首が同じ反りの曲線"),
    ("tide", "潮殻 (枝)", "貝の扇と珊瑚の枝・真珠の板 (虹色の艶)", "褪せた薔薇色", "桃", "波打つ", "貝殻の扇が冠と肩に開く。白い板に青緑の布"),
    ("thunder", "雷翼 (羽)", "白い羽・銅の板 (緑青)・紺の布", "青白い亜麻", "白青", "羽の尖り", "羽が冠と肩に放射する。銅の板が暖かい差し色"),
    ("ember", "余燼 (割れ)", "割れた玄武岩の角・黒い石と鉄・煤けた布", "暗い赤", "橙 (割れ目)", "破れた裾", "黒い板に溶岩の筋が走る。帯の赤が唯一の暖色"),
]
SHARED = [
    ("兜", "顔を開けた頭巾 + 額の環と宝石 + 冠の刃 (弧に沿う / 後頭に放射) + 耳の上の側翼 + 頬当て"),
    ("胸", "立てた襟 + 面取りした胸板 (中央に光の筋) + 巨神色の帯と留め具 + 腰の垂れ + 背の外套と飾り帯"),
    ("肩・腕", "丸い段の肩 (右が大きく、右肩に大きな刃) + 縁の環のある袖 + 外側の籠手 (レア以上は光の筋)"),
    ("脚・靴", "前垂れ + 面取りの草摺 + 膝の半球 (右に宝石) + 縁の環 + 靴の甲の板と足首の飾り"),
    ("階級", "通常 = 灰の縁 / レア = 青みの縁 + 部品 +1 + 光の筋 / 伝説 = 金の縁 + 背の環 + 肩 +1 + 面取りの縁の光"),
    ("やらないこと", "A 量塊 (全身が板の殻) の言葉は使わない。体の箱を板で包まず、布が輪郭を作り、板は要所だけ"),
]


def tri_table(o):
    rows = []
    for T in TITANS:
        cells = "".join(f"<td>{stats[o][T][r]['tris']:,}</td>" for r, _ in TIERS)
        rows.append(f"<tr><th>{JP[T]}</th>{cells}</tr>")
    return ("<table class=\"t\"><tr><th>一式の面 (三角形)</th>" + "".join(f"<th>{n}</th>" for _, n in TIERS) + "</tr>" + "".join(rows) + "</table>")


def radios(name, values):
    return "".join(f'<label><input type="radio" name="{name}" value="{v}"> {t}</label>' for v, t in values)


OPT_VALUES = [("S1", "S1 凛"), ("S2", "S2 祭"), ("S3", "S3 盛"), ("mix", "混ぜる (メモに書く)")]


def build():
    h = []
    h.append(HEAD)
    h.append('<div class="wrap">')
    h.append("<h1>巨神の防具シリーズ — 統一案の選定</h1>")
    h.append('<p class="intro">岩鯨の防具は作り直す前の版に戻しました (ゲームに入っているのはその版です)。A 量塊の言葉は二度と使いません。'
             "B 祭具 (布が輪郭を作り、板は要所だけ) を土台に、<b>4 巨神が同じ言葉で揃い、素材と飾りだけが巨神ごとに違う</b>シリーズを作りました。"
             "より滑らかに・より細かく・重く見えないように、面取りした板と丸い断面、層になった縁、帯と外套、控えめな光の筋でまとめています。"
             "S1 / S2 / S3 は飾りの量・布の量・兜の形の違いです。<b>まだゲームには入れていません</b> (選んでから入れ、インベントリの絵もそれに合わせて作り直します)。</p>")
    h.append('<p class="intro">見る順: ① 3 案の一覧 (下の「全体」) → ② 回転して見る → ③ 着た姿 → ④ 巨神ごとに選ぶ。画像はドット絵の拡大のため、少しにじんで見えます。</p>')

    h.append('<section class="sec"><h2>シリーズの共通の言葉と、巨神ごとの違い</h2>')
    h.append('<table class="t"><tr><th>部位</th><th>4 巨神と 3 案に共通</th></tr>' + "".join(f"<tr><th>{a}</th><td>{b}</td></tr>" for a, b in SHARED) + "</table>")
    h.append('<div class="scroll"><table class="t"><tr><th>巨神</th><th>材質と冠・肩の刃</th><th>帯の色</th><th>光</th><th>外套の裾</th><th>印象</th></tr>'
             + "".join(f"<tr><th>{n}</th><td>{m}</td><td>{s}</td><td>{g}</td><td>{hm}</td><td>{im}</td></tr>" for _, n, m, s, g, hm, im in TITAN_ROWS)
             + "</table></div></section>")

    h.append('<section class="sec"><h2>全体: 3 案を並べて (レア・4 巨神の正面と背面)</h2>')
    for o, nm, sub, desc in OPTS:
        h.append(f'<div class="option"><h3>{nm} — {sub}</h3><p>{desc}</p>'
                 f'<figure class="scroll"><img src="img/family_{o}.png" alt="{nm} 4 巨神" loading="lazy" style="min-width:760px"></figure>'
                 f'<figcaption>上段 = 正面、下段 = 背面。左から 岩鯨・潮殻・雷翼・余燼。</figcaption></div>')
    h.append('<fieldset class="opts" role="radiogroup" aria-label="全体の好み">'
             + "".join(f'<label class="opt"><input type="radio" name="overall" value="{v}"><span class="letter">{v[1] if v != "mix" else "+"}</span><span>{t}</span></label>' for v, t in OPT_VALUES)
             + "</fieldset>")
    h.append('<label class="note-l" for="note_series">全体の感想 (好きな所・直したい所・混ぜたい所。任意)</label><textarea class="note" id="note_series" rows="4"></textarea>'
             '<div class="save" id="save_series">読み込み中…</div></section>')

    h.append('<section class="sec"><h2>回転して見る (ドラッグか下のスライダー)</h2>'
             '<div class="viewer" id="viewer"><div class="vctl">'
             '<span>案</span><span class="seg" data-k="o"></span><span>巨神</span><span class="seg" data-k="t"></span><span>階級</span><span class="seg" data-k="r"></span></div>'
             '<div class="vstage" id="vstage" role="img" aria-label="防具の回転表示"></div>'
             '<input type="range" id="vrange" min="0" max="11" value="0" aria-label="回転の角度">'
             '<label class="auto"><input type="checkbox" id="vauto" checked> 自動で回す</label></div></section>')

    for o, nm, sub, desc in OPTS:
        h.append(f'<section class="sec"><h2>{nm} — 4 巨神 × 3 階級</h2><p class="intro">{desc}</p>'
                 f'<figure class="scroll"><img src="img/sheet_{o}.png" alt="{nm} 一覧" loading="lazy" style="min-width:1080px"></figure>'
                 f'<figcaption>行 = 岩鯨・潮殻・雷翼・余燼。列 = 通常 / レア / 伝説、それぞれ 正面・斜め前・背面。</figcaption>{tri_table(o)}</section>')

    h.append('<section class="sec"><h2>着た姿 (模式。レア)</h2><p class="intro">左 = 一人称で見下ろしたときに視界に入る範囲 (頭と兜は消し、腕を前へ)、'
             '中 = 三人称・後ろから (歩き)、右 = 三人称・前から (歩き)。外套が脚に刺さらないか・肩の板が腕の動きで邪魔にならないかを見るための図です。'
             'ゲーム内の実写は、インストールしていないため今回は撮っていません (選んだあとに撮ります)。</p>')
    for o, nm, sub, desc in OPTS:
        h.append(f'<details class="wn"><summary>{nm}</summary>')
        for T in TITANS:
            h.append(f'<figure class="scroll"><img src="img/worn_{o}_{T}.png" alt="{nm} {JP[T]} 着用" loading="lazy" style="min-width:900px"><figcaption>{JP[T]}</figcaption></figure>')
        h.append("</details>")
    h.append("</section>")

    h.append('<section class="sec"><h2>巨神ごとに選ぶ (統一感を保ちたいので、基本は同じ案にそろえるのがおすすめ)</h2>'
             '<p class="intro">各巨神 3 行 = S1 / S2 / S3 (上から)、4 列 = 通常正面 / レア正面 / 伝説正面 / 伝説背面。「混ぜる」を選んだら、メモに「兜は S3、肩は S2」のように書いてください。</p>')
    for T in TITANS:
        h.append(f'<div class="option" id="t_{T}"><h3>{JP[T]}</h3><figure class="scroll"><img src="img/cmp_{T}.png" alt="{JP[T]} 3 案の比べ" loading="lazy" style="min-width:760px"></figure>'
                 f'<div class="row" data-field="{T}"><span class="lab">{JP[T]}の案</span>{radios("pick_" + T, OPT_VALUES)}</div>'
                 f'<label class="note-l" for="note_{T}">{JP[T]}へのメモ (任意)</label><textarea class="note" id="note_{T}" rows="3"></textarea>'
                 f'<div class="save" id="save_{T}">読み込み中…</div></div>')
    h.append("</section>")

    h.append('<section class="sec"><h2>参考</h2><p class="intro">前の B 祭具 (旧防具 / 今回の土台): <a href="../gear_style/img/sheet_armor_B.png">gear_style の B 防具シート</a>。'
             "調べ: <code>docs/research/gear_art_research.md</code>。作り方: <code>tools/rough/lowpoly/armor/</code> の <code>sk.py</code> (部品) / <code>sstyle.py</code> (形) / "
             "<code>spaint.py</code> (絵) / <code>build_series.py</code> (画像)。インストールは選んだあとに既存の <code>install_armor_lowpoly.py</code> へ渡す形で行います。</p></section>")
    h.append("</div>")
    h.append(SCRIPT)
    h.append("</body></html>")
    (D / "index.html").write_text("\n".join(h), encoding="utf-8")
    print("wrote", D / "index.html")


HEAD = """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>巨神の防具シリーズ 選定</title>
<style>
:root{
  --bg:#eceef0; --panel:#f8f9fa; --ink:#1b2129; --muted:#5b6672; --line:#c9d0d6;
  --accent:#0a8fa6; --accent-ink:#ffffff; --ok:#2f8a4b; --shot:#1c2028;
  --body:"Zen Kaku Gothic New","Hiragino Sans","Yu Gothic",system-ui,sans-serif;
}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){
  --bg:#101318; --panel:#171b22; --ink:#e3e8ee; --muted:#93a0ad; --line:#2a313b;
  --accent:#5fdcef; --accent-ink:#0b1216; --ok:#6fd18c; color-scheme:dark}}
:root[data-theme="dark"]{
  --bg:#101318; --panel:#171b22; --ink:#e3e8ee; --muted:#93a0ad; --line:#2a313b;
  --accent:#5fdcef; --accent-ink:#0b1216; --ok:#6fd18c; color-scheme:dark}
*{box-sizing:border-box}
body{background:var(--bg);color:var(--ink);font-family:var(--body);font-size:15px;line-height:1.7;margin:0}
.wrap{max-width:1100px;margin:0 auto;padding-inline:16px;padding-block:28px 80px}
h1{font-weight:700;font-size:clamp(24px,4vw,34px);margin:0 0 8px;letter-spacing:.03em;text-wrap:balance}
h2{font-size:19px;margin:34px 0 10px}
.intro{color:var(--muted);max-width:72ch;margin:0 0 8px}
figure{margin:0}
img{display:block;width:100%;height:auto;background:var(--shot);border:1px solid var(--line)}
.option{background:var(--panel);border:1px solid var(--line);padding:14px;margin:16px 0}
.option h3{margin:0 0 4px;font-size:18px}
.option p{margin:0 0 10px;color:var(--muted)}
figcaption{font-size:12px;color:var(--muted);margin-top:2px}
.scroll{overflow-x:auto}
.sec{border-top:2px solid var(--line);margin-top:40px;padding-top:6px}
.sec h2{font-size:22px;margin:18px 0 6px}
.opts{border:0;padding:0;margin:12px 0 0;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.opt{display:flex;gap:10px;align-items:center;padding:12px;background:var(--panel);border:1px solid var(--line);cursor:pointer}
.opt input{position:absolute;opacity:0;pointer-events:none}
.letter{font-size:20px;font-weight:700;width:36px;height:36px;display:grid;place-items:center;border:1px solid var(--line);flex:none}
.opt:has(input:checked){border-color:var(--accent)}
.opt:has(input:checked) .letter{background:var(--accent);color:var(--accent-ink);border-color:var(--accent)}
.opt:has(input:focus-visible){outline:2px solid var(--accent);outline-offset:2px}
.note{width:100%;font:inherit;color:var(--ink);background:var(--panel);border:1px solid var(--line);padding:8px 10px;resize:vertical}
.note:focus{outline:2px solid var(--accent)}
.note-l{font-size:13px;color:var(--muted);display:block;margin:14px 0 4px}
.save{color:var(--muted);font-size:13px;margin-top:6px}
.row{display:flex;flex-wrap:wrap;gap:8px 18px;align-items:center;padding:8px 12px;background:var(--bg);border:1px solid var(--line)}
.row .lab{font-weight:700;min-width:6em}
.row label{cursor:pointer}
table.t{border-collapse:collapse;width:100%;font-size:14px;margin:8px 0 14px;background:var(--panel)}
table.t th,table.t td{border:1px solid var(--line);padding:5px 9px;text-align:left;vertical-align:top}
table.t th{background:var(--bg);white-space:nowrap}
.viewer{background:var(--panel);border:1px solid var(--line);padding:14px;display:grid;gap:10px;justify-items:center}
.vctl{display:flex;flex-wrap:wrap;gap:6px 12px;align-items:center;justify-content:center}
.vctl>span:not(.seg){color:var(--muted);font-size:13px}
.seg{display:inline-flex;border:1px solid var(--line)}
.seg button{font:inherit;font-size:14px;padding:4px 12px;border:0;background:var(--panel);color:var(--ink);cursor:pointer;border-right:1px solid var(--line)}
.seg button:last-child{border-right:0}
.seg button[aria-pressed=true]{background:var(--accent);color:var(--accent-ink)}
.vstage{width:min(100%,360px);aspect-ratio:150/230;background-color:var(--shot);background-repeat:no-repeat;border:1px solid var(--line);cursor:ew-resize;touch-action:pan-y;user-select:none}
#vrange{width:min(100%,360px)}
.auto{font-size:13px;color:var(--muted)}
details.wn{background:var(--panel);border:1px solid var(--line);padding:6px 12px;margin:8px 0}
details.wn summary{cursor:pointer;font-weight:700;padding:4px 0}
@media (max-width:760px){ .opts{grid-template-columns:minmax(0,1fr)} }
</style>
</head>
<body>"""

SCRIPT = """<script>
(() => {
  const dbp = (async () => { const c = window.claude && window.claude.use ? window.claude : null; return c ? await c.use('db') : null; })();
  function wire(docId, fields, noteId, saveId) {
    const noteEl = document.getElementById(noteId), saveEl = document.getElementById(saveId);
    let db = null, timer = null;
    const state = () => {
      const o = { at: Date.now(), note: noteEl.value };
      for (const [key, name] of fields) { const r = document.querySelector('input[name=' + name + ']:checked'); o[key] = r ? r.value : ''; }
      return o;
    };
    const save = async () => {
      if (!db) return;
      saveEl.textContent = '保存中…';
      try { await db.doc('choices/' + docId).set(state()); saveEl.textContent = '保存しました'; }
      catch (e) { saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
    };
    for (const [, name] of fields) document.querySelectorAll('input[name=' + name + ']').forEach(r => r.addEventListener('change', save));
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
          for (const [key, name] of fields) if (data[key]) { const r = document.querySelector('input[name=' + name + '][value="' + data[key] + '"]'); if (r) r.checked = true; }
          if (data.note) noteEl.value = data.note;
        }
        saveEl.textContent = '選ぶと自動で保存されます';
      } catch (e) { saveEl.textContent = '前回の選択を読み込めませんでした'; }
    })();
  }
  wire('armor_series', [['overall', 'overall']], 'note_series', 'save_series');
  for (const t of ['whale', 'tide', 'thunder', 'ember']) wire('armor_' + t, [['pick', 'pick_' + t]], 'note_' + t, 'save_' + t);

  // 回転ビューア (12 コマの帯)
  const O = [['S1', 'S1 凛'], ['S2', 'S2 祭'], ['S3', 'S3 盛']], T = [['whale', '岩鯨'], ['tide', '潮殻'], ['thunder', '雷翼'], ['ember', '余燼']], R = [['common', '通常'], ['rare', 'レア'], ['legend', '伝説']];
  const st = { o: 'S2', t: 'whale', r: 'rare', f: 0 };
  const stage = document.getElementById('vstage'), range = document.getElementById('vrange'), auto = document.getElementById('vauto');
  const lists = { o: O, t: T, r: R };
  document.querySelectorAll('#viewer .seg').forEach(seg => {
    const k = seg.dataset.k;
    lists[k].forEach(([v, n]) => {
      const b = document.createElement('button'); b.type = 'button'; b.textContent = n; b.dataset.v = v;
      b.addEventListener('click', () => { st[k] = v; draw(); });
      seg.appendChild(b);
    });
  });
  function draw() {
    stage.style.backgroundImage = 'url(img/turn_' + st.o + '_' + st.t + '_' + st.r + '.png)';
    stage.style.backgroundSize = '1200% 100%';
    stage.style.backgroundPosition = (st.f / 11 * 100) + '% 0';
    range.value = st.f;
    document.querySelectorAll('#viewer .seg').forEach(seg => seg.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(st[seg.dataset.k] === b.dataset.v))));
  }
  range.addEventListener('input', () => { st.f = +range.value; draw(); });
  let drag = null;
  stage.addEventListener('pointerdown', e => { drag = { x: e.clientX, f: st.f }; stage.setPointerCapture(e.pointerId); auto.checked = false; });
  stage.addEventListener('pointermove', e => { if (!drag) return; const w = stage.clientWidth; st.f = ((drag.f - Math.round((e.clientX - drag.x) / (w / 12))) % 12 + 12) % 12; draw(); });
  stage.addEventListener('pointerup', () => { drag = null; });
  setInterval(() => { if (auto.checked) { st.f = (st.f + 1) % 12; draw(); } }, 160);
  draw();
})();
</script>"""

if __name__ == "__main__":
    build()
