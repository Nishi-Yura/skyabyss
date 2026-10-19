"""Build docs/readme/options/preview.html: the three README concepts rendered side by side, with choice + note saved to the artifact db."""
import os, json

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", "docs", "readme", "options"))

CONCEPTS = [
    ("a", "A", "旅の手帳", "絵本・旅日記。羊皮紙の頁に写真を貼った絵。頁ごとに絵に文字を焼き込み、実用情報は付箋・チェック・キーの頁に。"),
    ("b", "B", "降りていく地図", "空から淵まで縦に降りる地図。Y 座標の目盛りつきの帯が、雷雲・雲海・地表・海・地の底・淵と続く。"),
    ("c", "C", "題字札", "ごく少ない大きな札と余白だけの、静かな題字カード。説明は短く、細部は折りたたみ。"),
]

TEMPLATE = r'''<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>README 案の比較</title>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<style>
:root{--bg:#ffffff;--bg2:#f6f8fa;--fg:#1f2328;--muted:#59636e;--line:#d1d9e0;--link:#0969da;--code:#818b981f;--accent:#0969da;--sel:#ddf4ff}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#0d1117;--bg2:#151b23;--fg:#f0f6fc;--muted:#9198a1;--line:#3d444d;--link:#4493f8;--code:#656c7633;--accent:#4493f8;--sel:#102a4c}}
:root[data-theme="dark"]{--bg:#0d1117;--bg2:#151b23;--fg:#f0f6fc;--muted:#9198a1;--line:#3d444d;--link:#4493f8;--code:#656c7633;--accent:#4493f8;--sel:#102a4c}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","Hiragino Sans","Yu Gothic UI","Noto Sans JP",sans-serif}
header.top{position:sticky;top:0;z-index:5;background:var(--bg2);border-bottom:1px solid var(--line);padding:10px 16px;display:flex;flex-wrap:wrap;gap:8px 20px;align-items:center}
header.top h1{font-size:16px;margin:0}
header.top .sp{flex:1}
.theme button{font:inherit;color:var(--fg);background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:3px 10px;cursor:pointer}
.theme button[aria-pressed="true"]{border-color:var(--accent);background:var(--sel)}
#savestate{color:var(--muted);font-size:13px}
.cols{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;padding:14px 16px}
@media (max-width:1100px){.cols{grid-template-columns:1fr}}
.col{border:1px solid var(--line);border-radius:10px;background:var(--bg);display:flex;flex-direction:column;min-width:0}
.col.chosen{border-color:var(--accent);box-shadow:0 0 0 2px var(--accent)}
.col .head{padding:12px 14px;border-bottom:1px solid var(--line);background:var(--bg2);border-radius:10px 10px 0 0}
.col .opt{display:flex;gap:10px;align-items:flex-start;cursor:pointer}
.col .opt input{margin-top:5px;width:18px;height:18px;accent-color:var(--accent)}
.col .letter{font-weight:700;font-size:22px;line-height:1.1;margin-right:4px}
.col .nm{font-weight:700}
.col .ds{font-size:13px;color:var(--muted);margin-top:2px}
.col .note{padding:10px 14px;border-bottom:1px solid var(--line)}
.col .note label{display:block;font-size:12px;color:var(--muted);margin-bottom:4px}
.col .note textarea{width:100%;font:inherit;font-size:13px;color:var(--fg);background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:6px 8px;resize:vertical}
.col .scroll{padding:16px;overflow:auto;max-height:calc(100vh - 150px);min-height:480px}
/* GitHub-like markdown */
.md{font-size:16px;line-height:1.5;word-wrap:break-word}
.md img{max-width:100%;image-rendering:pixelated;vertical-align:middle}
.md p,.md ul,.md ol,.md table,.md details,.md pre,.md blockquote{margin:0 0 16px}
.md a{color:var(--link);text-decoration:none}
.md a:hover{text-decoration:underline}
.md h1,.md h2,.md h3{margin:24px 0 16px;font-weight:600;line-height:1.25}
.md h2{padding-bottom:.3em;border-bottom:1px solid var(--line);font-size:1.5em}
.md code{padding:.2em .4em;font-size:85%;background:var(--code);border-radius:6px;font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
.md pre{padding:16px;background:var(--bg2);border-radius:6px;overflow:auto}
.md pre code{padding:0;background:none}
.md table{border-collapse:collapse;display:block;max-width:100%;overflow:auto}
.md th,.md td{padding:6px 13px;border:1px solid var(--line)}
.md tr:nth-child(2n){background:var(--bg2)}
.md kbd{display:inline-block;padding:3px 5px;font:11px ui-monospace,SFMono-Regular,Consolas,monospace;line-height:10px;color:var(--fg);vertical-align:middle;background:var(--bg2);border:1px solid var(--line);border-bottom-color:var(--line);border-radius:6px;box-shadow:inset 0 -1px 0 var(--line)}
.md summary{cursor:pointer}
.md details>summary{margin-bottom:8px}
.md sub{font-size:12px;color:var(--muted)}
.md em{font-style:italic}
.md ul,.md ol{padding-left:2em}
.md br{line-height:1}
.hint{padding:0 16px 16px;color:var(--muted);font-size:13px}
</style>
</head>
<body>
<header class="top">
  <h1>README の案を選ぶ（A / B / C）</h1>
  <span id="savestate">選ぶと自動で保存されます</span>
  <span class="sp"></span>
  <span class="theme">表示：
    <button type="button" data-t="auto" aria-pressed="true">自動</button>
    <button type="button" data-t="light" aria-pressed="false">ライト</button>
    <button type="button" data-t="dark" aria-pressed="false">ダーク</button>
  </span>
</header>
<p class="hint">GitHub の見え方に近づけた表示です（画像の幅は GitHub と同じく、列の幅に合わせて縮みます）。ライト／ダークを切り替えて、どちらでも崩れないか見てください。</p>
<main class="cols">
__COLS__
</main>
__SOURCES__
<script>
(() => {
  const ids = ['a', 'b', 'c'];
  const state = document.getElementById('savestate');
  let db = null;
  const timers = {};

  // theme toggle (per-viewer convenience only)
  const root = document.documentElement;
  const setTheme = t => {
    if (t === 'auto') root.removeAttribute('data-theme'); else root.setAttribute('data-theme', t);
    document.querySelectorAll('.theme button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.t === t)));
  };
  document.querySelectorAll('.theme button').forEach(b => b.addEventListener('click', () => setTheme(b.dataset.t)));

  // render markdown, resolve relative image paths against the option folder
  for (const id of ids) {
    const src = document.getElementById('md-' + id).textContent;
    const html = marked.parse(src, { gfm: true, breaks: false });
    const box = document.getElementById('render-' + id);
    box.innerHTML = html;
    box.querySelectorAll('img').forEach(img => {
      const s = img.getAttribute('src');
      if (s && !/^(https?:|data:|\/)/.test(s)) img.setAttribute('src', id + '/' + s);
    });
    box.querySelectorAll('a').forEach(a => {
      const h = a.getAttribute('href') || '';
      if (/^(\.\.\/)+/.test(h)) a.setAttribute('href', h.replace(/^(\.\.\/){3}/, '../../'));
      a.setAttribute('target', '_blank'); a.setAttribute('rel', 'noopener');
    });
  }

  function refresh() {
    const sel = document.querySelector('input[name=concept]:checked');
    document.querySelectorAll('.col').forEach(c => c.classList.toggle('chosen', !!sel && c.dataset.id === sel.value));
  }

  async function saveConcept() {
    if (!db) return;
    const sel = document.querySelector('input[name=concept]:checked');
    state.textContent = '保存中…';
    try {
      await db.doc('choices/readme_concept').set({ option: sel ? sel.value : '', at: Date.now() });
      state.textContent = '保存しました';
    } catch (e) { state.textContent = '保存できませんでした。もう一度選び直してください'; }
  }
  async function saveNote() {
    if (!db) return;
    const data = { at: Date.now() };
    for (const id of ids) data[id] = document.getElementById('note-' + id).value;
    state.textContent = '保存中…';
    try {
      await db.doc('choices/readme_note').set(data);
      state.textContent = '保存しました';
    } catch (e) { state.textContent = '保存できませんでした。もう一度入力してください'; }
  }

  document.querySelectorAll('input[name=concept]').forEach(r => r.addEventListener('change', () => { refresh(); saveConcept(); }));
  for (const id of ids) {
    document.getElementById('note-' + id).addEventListener('input', () => {
      clearTimeout(timers.n); timers.n = setTimeout(saveNote, 800);
    });
  }

  refresh();
  (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    db = c ? await c.use('db') : null;
    if (!db) { state.textContent = 'この表示では選択を保存できません (サインインして開いてください)'; return; }
    try {
      const snap = await db.collection('choices').get();
      const docs = snap.docs || snap;
      for (const d of docs) {
        const data = typeof d.data === 'function' ? d.data() : d.data;
        if (!data) continue;
        if (d.id === 'readme_concept' && data.option) {
          const r = document.querySelector(`input[name=concept][value="${data.option}"]`); if (r) r.checked = true;
        }
        if (d.id === 'readme_note') {
          for (const id of ids) if (data[id]) document.getElementById('note-' + id).value = data[id];
        }
      }
      state.textContent = '選ぶと自動で保存されます';
    } catch (e) { state.textContent = '前回の選択を読み込めませんでした'; }
    refresh();
  })();
})();
</script>
</body>
</html>
'''

COL = '''<section class="col" data-id="{id}">
  <div class="head">
    <label class="opt" for="pick-{id}"><input type="radio" name="concept" id="pick-{id}" value="{id}"><span class="letter">{L}</span><span><span class="nm">{name}</span><span class="ds" style="display:block">{desc}</span></span></label>
  </div>
  <div class="note"><label for="note-{id}">{L} について直してほしい点（任意）</label><textarea id="note-{id}" rows="2" placeholder="例: 地図の帯はそのまま、表紙だけ手帳風に"></textarea></div>
  <div class="scroll"><div class="md" id="render-{id}"></div></div>
</section>'''


def main():
    cols, srcs = [], []
    for cid, L, name, desc in CONCEPTS:
        cols.append(COL.format(id=cid, L=L, name=name, desc=desc))
        md = open(os.path.join(ROOT, cid, "README.md"), encoding="utf-8").read()
        assert "</script" not in md
        srcs.append('<script type="text/plain" id="md-%s">%s</script>' % (cid, md))
    html = TEMPLATE.replace("__COLS__", "\n".join(cols)).replace("__SOURCES__", "\n".join(srcs))
    with open(os.path.join(ROOT, "preview.html"), "w", encoding="utf-8") as f:
        f.write(html)
    print("preview.html", round(len(html) / 1024, 1), "KB")


if __name__ == "__main__":
    main()
