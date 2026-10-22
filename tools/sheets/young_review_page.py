"""docs/review/young_roughs/index.html を作る (選定ページ。db choices / young_look_<titan>)。python tools/sheets/young_review_page.py"""
import json, os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
D = os.path.join(ROOT, "docs/review/young_roughs")
TITANS = [("stone_whale", "岩鯨の仔"), ("tide_shell", "潮殻の稚貝"), ("thunder_wing", "雷翼の雛"), ("ember", "余燼の仔")]
data = {}
for t, _ in TITANS:
    data[t] = json.load(open(os.path.join(D, t + "_desc.json"), encoding="utf-8"))
src = open(os.path.join(ROOT, "docs/review/pearl_keeper/index.html"), encoding="utf-8").read()
head = src.split("<body>")[0].replace("<title>真珠守り 選定</title>", "<title>巨神の子 選定</title>")
head = head.replace(".opts{border:0", ".sec{margin-top:44px;padding-top:6px;border-top:2px solid var(--line)}\n.opts{border:0")
page = head + """<body>
<div class="wrap">
  <h1>巨神の子（歌のかけら）の形</h1>
  <p class="intro">巨神ごとの「子」は、笛で現れてパーティ全員（最大 6 人）を運び、降りると歌に溶けます。四体とも低ポリ＋1 ブロック 32 画素のドット絵で、骨とアニメは 3 案共通です（どれを選んでも動きは同じ）。人の影は灰色の箱で示しています。今は仮に A 案をゲームに入れてあります。巨神ごとに 1 案を選び、直してほしい点があれば書いてください。</p>
  <div id="all"></div>
  <div class="save" id="save">読み込み中…</div>
</div>
<script>
(() => {
  const TITANS = %s;
  const DATA = %s;
  const mk = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
  const root = document.getElementById('all');
  const saveEl = document.getElementById('save');
  let db = null; const timers = {};
  const FILE = {stone_whale: 'stone_whale', tide_shell: 'tide_shell', thunder_wing: 'thunder_wing', ember: 'ember'};
  for (const [t, name] of TITANS) {
    const sec = mk('section', 'sec');
    sec.appendChild(mk('h2', '', name + '（' + t + '）'));
    sec.appendChild(mk('figure', 'scroll', `<img src="${FILE[t]}_compare.png" alt="${name} 3 案の並び" loading="lazy"><figcaption>3 案の前 3/4</figcaption>`));
    for (const k of ['a', 'b', 'c']) {
      const o = mk('div', 'option');
      o.appendChild(mk('h3', '', DATA[t][k].title));
      o.appendChild(mk('p', '', DATA[t][k].desc));
      o.appendChild(mk('figure', 'scroll', `<img src="${FILE[t]}_${k}.png" alt="${DATA[t][k].title} 8 方向・人を乗せた図・動き" loading="lazy"><figcaption>8 方向・乗せた図・動き</figcaption>`));
      sec.appendChild(o);
    }
    const fs = mk('fieldset', 'opts');
    for (const k of ['a', 'b', 'c']) {
      fs.appendChild(mk('label', 'opt', `<input type="radio" name="style_${t}" value="${k}"><span class="letter">${k.toUpperCase()}</span><span>${DATA[t][k].title.replace(/^[A-C] /, '')}</span>`));
    }
    sec.appendChild(fs);
    sec.appendChild(mk('label', 'note-l', '直してほしい点（任意）')).setAttribute('for', 'note_' + t);
    const ta = mk('textarea', 'note'); ta.id = 'note_' + t; ta.rows = 2;
    sec.appendChild(ta);
    root.appendChild(sec);
    const chosen = () => { const r = sec.querySelector('input[name=style_' + t + ']:checked'); return r ? r.value : ''; };
    const save = async () => {
      if (!db) return;
      saveEl.textContent = '保存中…';
      try { await db.doc('choices/young_look_' + t).set({ option: chosen(), note: ta.value, at: Date.now() }); saveEl.textContent = '保存しました'; }
      catch (e) { saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
    };
    sec.querySelectorAll('input').forEach(r => r.addEventListener('change', save));
    ta.addEventListener('input', () => { clearTimeout(timers[t]); timers[t] = setTimeout(save, 800); });
  }
  (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    db = c ? await c.use('db') : null;
    if (!db) { saveEl.textContent = 'この表示では選択を保存できません (サインインして開いてください)'; return; }
    try {
      const snap = await db.collection('choices').get();
      for (const d of (snap.docs || snap)) {
        const m = /^young_look_(.+)$/.exec(d.id); if (!m) continue;
        const data = typeof d.data === 'function' ? d.data() : d.data; if (!data) continue;
        if (data.option) { const r = document.querySelector(`input[name=style_${m[1]}][value="${data.option}"]`); if (r) r.checked = true; }
        if (data.note) { const n = document.getElementById('note_' + m[1]); if (n) n.value = data.note; }
      }
      saveEl.textContent = '選ぶと自動で保存されます';
    } catch (e) { saveEl.textContent = '前回の選択を読み込めませんでした'; }
  })();
})();
</script>
</body>
</html>
""" % (json.dumps(TITANS, ensure_ascii=False), json.dumps(data, ensure_ascii=False))
open(os.path.join(D, "index.html"), "w", encoding="utf-8").write(page)
