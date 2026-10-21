"""ラフの比較シートを、選択用の1枚のページ (Artifact) にまとめる。

使い方: python tools/rough/build_gallery.py
入力: tools/rough/gallery.json (対象・案の説明・シート画像の一覧)
使い方 (別の場所のシート): python tools/rough/build_gallery.py <manifest.json> <シートの基準フォルダ> <出力フォルダ名>
出力: docs/roughs/_gallery/index.html と、縮めたシート画像 (JPEG)
"""
import html
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/roughs"
OUT = SRC / "_gallery"
MAX_W = 1600


def configure(src=None, out=None):
    """optional: gallery sheets live somewhere else than docs/roughs (e.g. docs/review)"""
    global SRC, OUT
    if src:
        SRC = ROOT / src
    OUT = SRC / (out or "_gallery")


def shrink(name: str) -> str:
    src = SRC / name
    # ドット絵のシート (32x32 など) は JPEG だと色がにじむので PNG のまま載せる
    pixel = "32" in Path(name).stem
    dst = OUT / (name.replace("/", "_").rsplit(".", 1)[0] + (".png" if pixel else ".jpg"))
    im = Image.open(src).convert("RGB")
    if im.width > MAX_W:
        im = im.resize((MAX_W, round(im.height * MAX_W / im.width)), Image.LANCZOS)
    if pixel:
        Image.open(src).save(dst, optimize=True)
        return dst.name
    im.save(dst, quality=86, optimize=True)
    return dst.name


def main():
    import sys
    configure(sys.argv[2] if len(sys.argv) > 2 else None, sys.argv[3] if len(sys.argv) > 3 else None)
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = sys.argv[1] if len(sys.argv) > 1 else "tools/rough/gallery.json"
    data = json.loads((ROOT / manifest).read_text(encoding="utf-8"))
    sections = []
    nav = []
    for s in data["subjects"]:
        sid = s["id"]
        imgs = "".join(
            f'<figure class="sheet"><button class="zoom" type="button" data-src="{shrink(f)}" aria-label="{html.escape(s["title"])} のシートを拡大">'
            f'<img loading="lazy" src="{shrink(f)}" alt="{html.escape(s["title"])} の比較シート"></button></figure>'
            for f in s["sheets"])
        opts = "".join(
            f'<label class="opt" for="{sid}-{k}"><input type="radio" name="{sid}" id="{sid}-{k}" value="{k}">'
            f'<span class="letter">{k.upper()}</span><span class="otext"><b>{html.escape(o["name"])}</b>'
            f'<span>{html.escape(o["desc"])}</span></span></label>'
            for k, o in s["options"].items())
        extra = f'<p class="extra">{html.escape(s["extra"])}</p>' if s.get("extra") else ""
        sections.append(f'''
<section class="subject" id="{sid}" data-id="{sid}">
  <header class="shead">
    <p class="kind">{html.escape(s["kind"])}</p>
    <h2>{html.escape(s["title"])}</h2>
    <span class="state" data-state="none">未選択</span>
  </header>
  {extra}
  <div class="sheets">{imgs}</div>
  <fieldset class="opts"><legend>採用する案</legend>{opts}</fieldset>
  <label class="note-l" for="{sid}-note">直してほしい点 (任意)</label>
  <textarea id="{sid}-note" class="note" rows="2" placeholder="例: Bの形でCの色、盾をもっと大きく"></textarea>
</section>''')
        nav.append(f'<a href="#{sid}" data-nav="{sid}">{html.escape(s["short"])}</a>')

    done = "".join(f'<li>{html.escape(d)}</li>' for d in data.get("done", []))
    page = TEMPLATE.replace("{{DONE}}", f'<details class="done"><summary>選ばれてゲームに入れたもの ({len(data.get("done", []))})</summary><ul>{done}</ul></details>' if done else "")
    page = page.replace("{{TITLE}}", html.escape(data.get("title", "岩鯨ラフ選定"))).replace("{{NAV}}", "".join(nav)).replace("{{SECTIONS}}", "".join(sections)) \
        .replace("{{COUNT}}", str(len(data["subjects"]))).replace("{{INTRO}}", html.escape(data["intro"]))
    (OUT / "index.html").write_text(page, encoding="utf-8")
    print("wrote", OUT / "index.html")


TEMPLATE = r'''<!doctype html>
<html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{{TITLE}}</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DotGothic16&family=Zen+Kaku+Gothic+New:wght@400;700&display=swap">
<style>
/* 作業台: 左に対象の目次、右に対象ごとのシートと選択。暗い石の地に、共鳴の水色を一点だけ使う */
:root{
  --bg:#eceef0; --panel:#f8f9fa; --ink:#1b2129; --muted:#5b6672; --line:#c9d0d6;
  --accent:#0a8fa6; --accent-ink:#ffffff; --ok:#2f8a4b;
  --display:"DotGothic16","Zen Kaku Gothic New",system-ui,sans-serif;
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
.wrap{max-width:1180px;margin:0 auto;padding-inline:20px;padding-block:28px 80px}
.top{display:grid;gap:10px;margin-bottom:22px}
h1{font-family:var(--display);font-weight:400;font-size:clamp(26px,4vw,38px);margin:0;letter-spacing:.04em;text-wrap:balance}
.intro{color:var(--muted);max-width:68ch;margin:0}
.progress{display:flex;align-items:center;gap:12px;flex-wrap:wrap;font-variant-numeric:tabular-nums}
.bar{flex:1 1 220px;height:8px;background:var(--line);border-radius:2px;overflow:hidden}
.bar i{display:block;height:100%;width:0;background:var(--accent);transition:width .3s}
.save-state{color:var(--muted);font-size:13px}
.layout{display:grid;grid-template-columns:180px minmax(0,1fr);gap:28px;align-items:start}
nav{position:sticky;top:calc(env(safe-area-inset-top,0px) + 12px);display:grid;gap:2px}
nav a{color:var(--muted);text-decoration:none;padding:5px 10px;border-left:2px solid var(--line);font-size:14px}
nav a.done{color:var(--ink);border-left-color:var(--ok)}
nav a:focus-visible,nav a:hover{color:var(--accent)}
.subject{padding-block:26px;border-top:1px solid var(--line);display:grid;gap:14px}
.shead{display:flex;align-items:baseline;gap:12px;flex-wrap:wrap}
.kind{margin:0;font-size:12px;letter-spacing:.12em;color:var(--muted)}
h2{font-family:var(--display);font-weight:400;font-size:24px;margin:0;flex:1 1 auto}
.state{font-size:13px;padding:2px 10px;border:1px solid var(--line);border-radius:999px;color:var(--muted)}
.state[data-state="chosen"]{border-color:var(--ok);color:var(--ok)}
.extra{margin:0;color:var(--muted);max-width:72ch}
.sheets{display:grid;gap:12px}
.sheet{margin:0}
.zoom{all:unset;cursor:zoom-in;display:block;border:1px solid var(--line);background:#0e1015}
.zoom:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.sheet img{display:block;width:100%;height:auto}
.opts{border:0;padding:0;margin:0;display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
.opts legend{font-size:13px;color:var(--muted);margin-bottom:6px}
.opt{display:flex;gap:10px;align-items:flex-start;padding:12px;background:var(--panel);border:1px solid var(--line);cursor:pointer;min-width:0}
.opt input{position:absolute;opacity:0;pointer-events:none}
.letter{font-family:var(--display);font-size:22px;line-height:1;width:34px;height:34px;display:grid;place-items:center;border:1px solid var(--line);flex:none}
.otext{display:grid;gap:2px;min-width:0;font-size:13px;color:var(--muted)}
.otext b{color:var(--ink);font-size:14px}
.opt:has(input:checked){border-color:var(--accent)}
.opt:has(input:checked) .letter{background:var(--accent);color:var(--accent-ink);border-color:var(--accent)}
.opt:has(input:focus-visible){outline:2px solid var(--accent);outline-offset:2px}
.note-l{font-size:13px;color:var(--muted)}
.note{width:100%;font:inherit;color:var(--ink);background:var(--panel);border:1px solid var(--line);padding:8px 10px;resize:vertical}
.note:focus{outline:2px solid var(--accent);outline-offset:0}
dialog{border:0;padding:0;background:transparent;max-width:96vw;max-height:94vh}
dialog::backdrop{background:rgba(0,0,0,.82)}
dialog img{display:block;max-width:96vw;max-height:94vh;width:auto;height:auto}
.signed-out{display:none;color:var(--muted);font-size:13px}
.done{color:var(--muted);font-size:14px;max-width:72ch}
.done summary{cursor:pointer;color:var(--ink)}
.done ul{margin:6px 0 0;padding-left:1.2em}
@media (max-width:760px){
  .layout{grid-template-columns:minmax(0,1fr)}
  nav{position:static;display:flex;flex-wrap:wrap;gap:4px}
  nav a{border-left:0;border-bottom:2px solid var(--line);padding:4px 8px}
  nav a.done{border-bottom-color:var(--ok)}
  .opts{grid-template-columns:minmax(0,1fr)}
}
@media (prefers-reduced-motion:reduce){.bar i{transition:none}}
</style>

<div class="wrap">
  <div class="top">
    <h1>{{TITLE}}</h1>
    <p class="intro">{{INTRO}}</p>
    {{DONE}}
    <div class="progress"><div class="bar" aria-hidden="true"><i id="pbar"></i></div>
      <span id="pcount">0 / {{COUNT}} 選択済み</span><span class="save-state" id="savestate">読み込み中…</span></div>
  </div>
  <div class="layout">
    <nav aria-label="対象">{{NAV}}</nav>
    <main>{{SECTIONS}}</main>
  </div>
</div>
<dialog id="viewer"><img id="viewer-img" alt="拡大したシート"></dialog>

<script>
(() => {
  const total = {{COUNT}};
  const sections = [...document.querySelectorAll('.subject')];
  const saveState = document.getElementById('savestate');
  let db = null;
  const timers = {};

  function refresh() {
    let n = 0;
    for (const s of sections) {
      const chosen = s.querySelector('input[type=radio]:checked');
      const st = s.querySelector('.state');
      if (chosen) { n++; st.dataset.state = 'chosen'; st.textContent = chosen.value.toUpperCase() + ' を採用'; }
      else { st.dataset.state = 'none'; st.textContent = '未選択'; }
      const nav = document.querySelector(`[data-nav="${s.dataset.id}"]`);
      if (nav) nav.classList.toggle('done', !!chosen);
    }
    document.getElementById('pcount').textContent = `${n} / ${total} 選択済み`;
    document.getElementById('pbar').style.width = (n / total * 100) + '%';
  }

  async function save(id) {
    if (!db) return;
    const s = document.getElementById(id);
    const chosen = s.querySelector('input[type=radio]:checked');
    const note = s.querySelector('.note').value;
    saveState.textContent = '保存中…';
    try {
      await db.doc('choices/' + id).set({ option: chosen ? chosen.value : '', note, at: Date.now() });
      saveState.textContent = '保存しました';
    } catch (e) {
      saveState.textContent = '保存できませんでした。もう一度選び直してください';
    }
  }

  for (const s of sections) {
    const id = s.dataset.id;
    s.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', () => { refresh(); save(id); }));
    s.querySelector('.note').addEventListener('input', () => {
      clearTimeout(timers[id]); timers[id] = setTimeout(() => save(id), 800);
    });
  }

  const viewer = document.getElementById('viewer');
  const vimg = document.getElementById('viewer-img');
  document.querySelectorAll('.zoom').forEach(b => b.addEventListener('click', () => {
    vimg.src = b.dataset.src; viewer.showModal();
  }));
  viewer.addEventListener('click', () => viewer.close());

  refresh();
  (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    db = c ? await c.use('db') : null;
    if (!db) { saveState.textContent = 'この表示では選択を保存できません (サインインして開いてください)'; return; }
    try {
      const snap = await db.collection('choices').get();
      const docs = snap.docs || snap;
      for (const d of docs) {
        const data = typeof d.data === 'function' ? d.data() : d.data;
        const s = document.getElementById(d.id);
        if (!s || !data) continue;
        if (data.option) { const r = s.querySelector(`input[value="${data.option}"]`); if (r) r.checked = true; }
        if (data.note) s.querySelector('.note').value = data.note;
      }
      saveState.textContent = '選ぶと自動で保存されます';
    } catch (e) {
      saveState.textContent = '前回の選択を読み込めませんでした';
    }
    refresh();
  })();
})();
</script>
'''

if __name__ == "__main__":
    main()
