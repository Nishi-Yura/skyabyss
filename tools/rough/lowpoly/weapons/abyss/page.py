"""docs/review/abyss_weapons/index.html を書く (pearl_keeper/index.html と同じ作り。武器ごとに 1 節、選択は choices/abyss_weapon_<短い名>)。
実行: python tools/rough/lowpoly/weapons/abyss/page.py"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import d_abyss as D  # noqa: E402

ROOT = HERE.parents[4]
PAGE = ROOT / "docs/review/abyss_weapons"
SHORT = {"abyss_greatsword": "greatsword", "abyss_bell_staff": "bell_staff", "abyss_storm_bow": "storm_bow", "abyss_ash_scythe": "ash_scythe"}
TITLES = {
    "abyss_greatsword": ("底なしの大剣", "岩鯨の深淵の心臓から出る。攻撃 18・速さ 0.9。技「巨神斬り」を「奈落断ち」(扇が 1.2 秒後にもう一度裂ける) に書き換える。差し色は骨の白紫。",
                         {"a": "裂け刃", "b": "断崖", "c": "牙"}),
    "abyss_bell_staff": ("沈み鐘の杖", "潮殻の深淵の心臓から出る。攻撃 12・速さ 1.6。技「守りの唄」を「沈む唄」(残った守りが体力へ沈む) に書き換える。差し色は青緑の真珠。",
                         {"a": "吊り鐘", "b": "真珠の杯", "c": "二つ鐘"}),
    "abyss_storm_bow": ("雷淵の弓", "雷翼の深淵の心臓から出る。矢 ×1.8。技「雲穿ち」を「天落とし」(止まった所へ雷の柱) に書き換える。差し色は嵐の銅と黄白の稲光。引き絵 3 段つき。",
                        {"a": "稲妻の腕", "b": "雷環", "c": "避雷針"}),
    "abyss_ash_scythe": ("灰喰いの鎌", "余燼の深淵の心臓から出る。攻撃 14・速さ 1.2。技「灰散らし」を「這う灰」(灰が敵から敵へ移る) に書き換える。差し色は熾の橙。",
                         {"a": "大鎌", "b": "草刈り鎌", "c": "顎の鎌"}),
}
CSS = (ROOT / "docs/review/pearl_keeper/index.html").read_text(encoding="utf-8")
CSS = CSS[CSS.index("<style>"):CSS.index("</style>") + 8]
CSS = CSS.replace("--accent:#0a8fa6", "--accent:#6a4bc4").replace("--accent:#5fdcef", "--accent:#b79cff")
CSS = CSS.replace("--shot:#14141c", "--shot:#16121f")

data = {}
for wid, (jp, intro, names) in TITLES.items():
    w = D.WEAPONS[wid]
    data[SHORT[wid]] = dict(jp=jp, intro=intro, opts={k: dict(title=f"{k.upper()} {names[k]}", desc=w["opts"][k][3]) for k in "abc"})

HTML = """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>深淵の専用伝説 選定</title>
__CSS__
<style>
.sec{border-top:2px solid var(--line);margin-top:40px;padding-top:6px}
.sec h2{font-size:22px;margin:18px 0 6px}
</style>
</head>
<body>
<div class="wrap">
  <h1>深淵の専用伝説 4 点の形</h1>
  <p class="intro">深淵の心臓（その巨神の心臓をグループが「深淵」で倒したとき）からだけ出る専用の伝説武器です。今は平らな絵だけなので、ほかの伝説と同じ低ポリ＋1 ブロック 32 画素のドット絵の立体にします。共通の方針は「深淵の紫黒の土台、うっすら内側で光る筋、風化した巨神の素材（岩鯨の骨、潮殻の真珠、雷翼の銅、余燼の玄武岩）の差し色」。派手にしすぎず、輪郭で見分けがつくことを優先しました。階級は伝説だけなので強化は +0 / +5 / +10 の光り方だけ変わります。各武器の A 案を仮にゲームに入れてあります。</p>
  <div id="root"></div>
</div>
<script>
(() => {
  const WEAPONS = __DATA__;
  const mk = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
  const root = document.getElementById('root');
  const dbp = (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    return c ? await c.use('db') : null;
  })();
  for (const id of Object.keys(WEAPONS)) {
    const W = WEAPONS[id];
    const docId = 'abyss_weapon_' + id;
    const sec = mk('section', 'sec');
    sec.appendChild(mk('h2', '', W.jp));
    sec.appendChild(mk('p', 'intro', W.intro));
    sec.appendChild(mk('figure', 'scroll', `<img src="${id}/compare.png" alt="${W.jp} 3 案の並び" loading="lazy"><figcaption>3 案の並び（各案: 前・前 3/4）</figcaption>`));
    const fs = mk('fieldset', 'opts');
    for (const k of Object.keys(W.opts)) {
      const o = mk('section', 'option');
      o.appendChild(mk('h3', '', W.opts[k].title));
      o.appendChild(mk('p', '', W.opts[k].desc));
      o.appendChild(mk('figure', 'scroll', `<img src="${id}/opt_${k}_views.png" alt="${W.opts[k].title} 前・横・後ろ・前 3/4" loading="lazy"><figcaption>前・横・後ろ・前 3/4（回転 4 方向）</figcaption>`));
      o.appendChild(mk('figure', 'scroll', `<img src="${id}/opt_${k}_actions.png" alt="${W.opts[k].title} 手と地面" loading="lazy"><figcaption>手に持つ（三人称・一人称。持ち方は目安）・地面に落ちた姿・強化の光り方${id === 'storm_bow' ? '・弓の引き 3 段' : ''}</figcaption>`));
      sec.appendChild(o);
      fs.appendChild(mk('label', 'opt', `<input type="radio" name="style_${id}" value="${k}"><span class="letter">${k.toUpperCase()}</span><span>${W.opts[k].title}</span>`));
    }
    sec.appendChild(mk('h2', '', `${W.jp}: 採用する案`));
    sec.appendChild(fs);
    sec.appendChild(mk('label', 'note-l', '直してほしい点（任意。色・形・光の量・大きさ、など）'));
    const note = mk('textarea', 'note'); note.rows = 3; note.id = 'note_' + id;
    sec.appendChild(note);
    const saveEl = mk('div', 'save', '読み込み中…');
    sec.appendChild(saveEl);
    root.appendChild(sec);

    let db = null, timer = null;
    const chosen = () => { const r = sec.querySelector('input[type=radio]:checked'); return r ? r.value : ''; };
    const save = async () => {
      if (!db) return;
      saveEl.textContent = '保存中…';
      try {
        await db.doc('choices/' + docId).set({ option: chosen(), note: note.value, at: Date.now() });
        saveEl.textContent = '保存しました';
      } catch (e) { saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
    };
    sec.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', save));
    note.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(save, 800); });
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
          if (data.option) { const r = sec.querySelector(`input[type=radio][value="${data.option}"]`); if (r) r.checked = true; }
          if (data.note) note.value = data.note;
        }
        saveEl.textContent = '選ぶと自動で保存されます';
      } catch (e) { saveEl.textContent = '前回の選択を読み込めませんでした'; }
    })();
  }
})();
</script>
</body>
</html>
"""
out = HTML.replace("__CSS__", CSS).replace("__DATA__", json.dumps(data, ensure_ascii=False))
(PAGE / "index.html").write_text(out, encoding="utf-8")
print("wrote", PAGE / "index.html")
