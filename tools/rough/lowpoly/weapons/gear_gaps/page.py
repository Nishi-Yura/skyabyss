"""docs/review/roughs_gear_gaps/index.html を書く (abyss/page.py と同じ作り。品ごとに 1 節、選択は choices/gear_gaps_<id>)。
実行: python -I tools/rough/lowpoly/weapons/gear_gaps/page.py"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import d_gaps as D  # noqa: E402
import acc_gaps as A  # noqa: E402

ROOT = HERE.parents[4]
PAGE = ROOT / "docs/review/roughs_gear_gaps"
INTRO = {
    "abyss_lamp_sword": "レアの片手剣（攻撃 11・速さ 1.6）。灯りの中で +10%、闇で斬った敵を光らせる。光は灯りの琥珀。",
    "abyss_sinking_anchor": "伝説の両手の錨（攻撃 21・速さ 0.8）。3 度に 1 度、まわりへ重しを落として引き下ろす。光は淵の紫。",
    "abyss_pulse_dagger": "伝説の短剣（攻撃 9・速さ 2.0）。同じ敵への連撃 4 度目ごとに追い打ちの拍。光は鼓動の赤。",
    "abyss_pulse_listener": "レアの装飾。4 秒ごとにまわりの敵の輪郭を光らせる。付けた姿は胸の左。",
    "abyss_lantern_charm": "レアの護符（H キー）。足もとに深淵の灯りを置き、敵を光らせて押し返す。",
    "abyss_surveyor_plumb": "伝説の装飾。深いほど与える傷が増える。付けた姿は腰の右の後ろ。",
    "abyss_diver_armor": "レアの鎧 4 部位（防御 23・強靭 15 + 一式 3）。4 部位で上昇の重みと闇のたまりが軽くなり、暗い所の傷が減る。",
}

data = {}
for wid, w in D.WEAPONS.items():
    data[wid] = dict(jp=w["jp"], intro=INTRO[wid], kind="weapon",
                     opts={k: dict(title=f"{k.upper()} {v[3].split('。')[0]}", desc=v[3]) for k, v in w["opts"].items()})
for iid, it in A.ITEMS.items():
    if it.get("material"):
        continue
    data[iid] = dict(jp=it["jp"], intro=INTRO[iid], kind="acc",
                     opts={k: dict(title=f"{k.upper()} {v[1].split('。')[0]}", desc=v[1]) for k, v in it["opts"].items()})
data["abyss_diver_armor"] = dict(jp="淵潜りの鎧", intro=INTRO["abyss_diver_armor"], kind="armor",
                                 opts={"a": dict(title="A 錨鉄の潜り鎧", desc="鋲の錨鉄の板・額の角灯・胸を斜めに渡る鎖・丸い肩の 3 段・重りの靴底。"),
                                       "b": dict(title="B 灯り番の外套", desc="紫黒の長い外套・錨鉄の縁・腰に吊った角灯・頭巾の額の灯。"),
                                       "c": dict(title="C 肋と鎖", desc="鎖の帷子に白紫の肋の板・肋の冠・面頬の覗き窓が光る。")})

CSS = (ROOT / "docs/review/pearl_keeper/index.html").read_text(encoding="utf-8")
CSS = CSS[CSS.index("<style>"):CSS.index("</style>") + 8]
CSS = CSS.replace("--accent:#0a8fa6", "--accent:#6a4bc4").replace("--accent:#5fdcef", "--accent:#b79cff")

HTML = """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>深淵の装備 選定</title>
__CSS__
<style>
.sec{border-top:2px solid var(--line);margin-top:40px;padding-top:6px}
.sec h2{font-size:22px;margin:18px 0 6px}
</style>
</head>
<body>
<div class="wrap">
  <h1>五章「深淵」の装備の形</h1>
  <p class="intro">五章の深淵で手に入る新しい装備（武器 3・装飾と護符 3・鎧 4 部位）のラフ 3 案です。どれも低ポリの立体で、32x32 のアイコンは立体を写したものです。共通の色は深淵の紫黒の土台に錨鉄（青灰の鉄と鋲）、縁は階級の色（レア = 青い鋼、伝説 = 紫銀）。各品の A 案を仮にゲームに入れてあります。</p>
  <div id="root"></div>
</div>
<script>
(() => {
  const ITEMS = __DATA__;
  const mk = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
  const root = document.getElementById('root');
  const dbp = (async () => {
    const c = window.claude && window.claude.use ? window.claude : null;
    return c ? await c.use('db') : null;
  })();
  for (const id of Object.keys(ITEMS)) {
    const W = ITEMS[id];
    const docId = 'gear_gaps_' + id;
    const sec = mk('section', 'sec');
    sec.appendChild(mk('h2', '', W.jp));
    sec.appendChild(mk('p', 'intro', W.intro));
    sec.appendChild(mk('figure', 'scroll', `<img src="${id}/compare.png" alt="${W.jp} 3 案の並び" loading="lazy"><figcaption>3 案の並び${W.kind === 'armor' ? '（正面・背面・横・3/4・兜と胸の拡大、下はアイコン）' : '（前・前 3/4、下は 32x32 のアイコン）'}</figcaption>`));
    const fs = mk('fieldset', 'opts');
    for (const k of Object.keys(W.opts)) {
      const o = mk('section', 'option');
      o.appendChild(mk('h3', '', W.opts[k].title));
      o.appendChild(mk('p', '', W.opts[k].desc));
      if (W.kind === 'weapon') {
        o.appendChild(mk('figure', 'scroll', `<img src="${id}/opt_${k}_views.png" alt="${W.opts[k].title} 回転 4 方向" loading="lazy"><figcaption>前・横・後ろ・前 3/4</figcaption>`));
        o.appendChild(mk('figure', 'scroll', `<img src="${id}/opt_${k}_actions.png" alt="${W.opts[k].title} 手と地面" loading="lazy"><figcaption>手に持つ（三人称・一人称。持ち方は目安）・地面・強化の光り方</figcaption>`));
      }
      sec.appendChild(o);
      fs.appendChild(mk('label', 'opt', `<input type="radio" name="style_${id}" value="${k}"><span class="letter">${k.toUpperCase()}</span><span>${W.opts[k].title}</span>`));
    }
    sec.appendChild(mk('h2', '', `${W.jp}: 採用する案`));
    sec.appendChild(fs);
    sec.appendChild(mk('label', 'note-l', '直してほしい点（任意）'));
    const note = mk('textarea', 'note'); note.rows = 3;
    sec.appendChild(note);
    const saveEl = mk('div', 'save', '読み込み中…');
    sec.appendChild(saveEl);
    root.appendChild(sec);
    let db = null, timer = null;
    const chosen = () => { const r = sec.querySelector('input[type=radio]:checked'); return r ? r.value : ''; };
    const save = async () => {
      if (!db) return;
      saveEl.textContent = '保存中…';
      try { await db.doc('choices/' + docId).set({ option: chosen(), note: note.value, at: Date.now() }); saveEl.textContent = '保存しました'; }
      catch (e) { saveEl.textContent = '保存できませんでした。もう一度選び直してください'; }
    };
    sec.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', save));
    note.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(save, 800); });
    (async () => {
      db = await dbp;
      if (!db) { saveEl.textContent = 'この表示では選択を保存できません (サインインして開いてください)'; return; }
      try {
        const snap = await db.collection('choices').get();
        for (const d of (snap.docs || snap)) {
          if (d.id !== docId) continue;
          const v = typeof d.data === 'function' ? d.data() : d.data;
          if (v && v.option) { const r = sec.querySelector(`input[type=radio][value="${v.option}"]`); if (r) r.checked = true; }
          if (v && v.note) note.value = v.note;
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
