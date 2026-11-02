"""docs/review/mod_blocks/index.html を作る (docs/review/anemone/index.html の様式を土台に)。python tools/sheets/mod_blocks_page.py"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent / "docs/review"
BL = [
    ("chest", "遺物の箱", "Relic Cache", "宝のキャッシュ (岩鯨の左右の尾びれなど)。向きあり・開く蓋・光る錠", "閉 / 開"),
    ("hearth", "休み火", "Rest Hearth", "休み場所のたき火の代わり。冷えた / 灯った", "冷 / 灯"),
    ("lever", "遺物のレバー", "Relic Lever", "仕掛けのレバー。入にすると台座の溝が光る", "切 / 入"),
    ("button", "遺物のボタン", "Relic Button", "押す仕掛け用 (今後)。押すと沈んで輪が光る", "上 / 押"),
    ("plate", "遺物の感圧板", "Relic Plate", "踏む仕掛け。踏むと沈んで縁が光る", "上 / 踏"),
    ("tablet", "語りの碑", "Lore Tablet (いまの TABLET の見た目)", "看板の代わりの碑。目を向けると字幕に文が出る。暗い / 光る", "暗 / 光"),
    ("seal", "試練の封印", "Trial Seal / Cleared Seal", "封印 → 越えた印。※別の人が作っている「試練を越えた印」ブロックには、見た目の候補としてだけ使えます", "封 / 開"),
    ("bell", "遺物の鐘", "Relic Bell", "鐘楼の謎の鐘。鳴る間は音の輪が光る", "静 / 鳴"),
]
LOOKS = {
    "a": ["A 鯨骨と深層岩", "鯨骨の白と深層岩の青灰。光は青 (岩鯨の脈)。いちばん「背中」らしい"],
    "b": ["B 潮の鱗と貝", "青紫の鱗石と真珠貝・珊瑚。光は青緑。潮の巨像寄りで柔らかい"],
    "c": ["C 灰の鉄と刻印", "炭の灰色に鉄と銅の帯。光は琥珀。嵐・熾の巨像寄りで硬い"],
}

src = (ROOT / "anemone/index.html").read_text(encoding="utf-8")
head = src[:src.index("<body>")].replace("灯の絵 選定", "専用ブロックの見た目")
head = head.replace(".opt img{width:64px;height:64px;", ".opt img{width:100%;max-width:340px;height:auto;")
head = head.replace(".opt{display:flex;gap:10px;align-items:center;", ".opt{display:flex;flex-direction:column;gap:8px;align-items:flex-start;")
head = head.replace("</style>", ".row{display:flex;gap:10px;align-items:center}.states{color:var(--muted);font-size:12px}.bid{font-size:13px;color:var(--muted);font-weight:400}</style>")

body = ('<body>\n<div class="wrap">\n<h1>modの専用ブロックの見た目</h1>\n'
        '<p class="intro">modに関わるブロック (宝の箱・休み場所の火・レバー・ボタン・感圧板・碑・封印・鐘) を、バニラとは別の専用ブロックにして、'
        '「これはmodの要素だ」と気づけるようにする案です。どれも「巨像の遺物」の共通の様式 (彫った石の枠・素材の象嵌・光る一本の筋) で、'
        '素材と光の色だけが 3 案で違います。各ブロック 32×32 のドット絵で、1 枚の中の左右が 2 つの状態です。ブロックごとに好きな案を選べます (混ぜてもよい)。'
        'ゲームのブロックはまだ差し替えていません。</p>\n'
        '<img class="sheet" src="sheet.png" alt="8 種 x 3 案の全体 (各 2 状態)">\n')
for bid, ja, en, desc, st in BL:
    body += (f'<h2>{ja} <span class="bid">{en}</span></h2>\n<p class="intro">{desc}</p>\n<fieldset class="opts" id="{bid}_opts"></fieldset>\n'
             f'<label class="note-l" for="{bid}_note">直してほしい点 (任意)</label>\n'
             f'<textarea id="{bid}_note" class="note" rows="2" placeholder="例: Bの貝をもう少し大きく / これは専用にしなくてよい"></textarea>\n'
             f'<div class="save" id="{bid}_save">読み込み中…</div>\n')
body += ('</div>\n<script>\n(() => {\n  const L = ' + json.dumps(LOOKS, ensure_ascii=False) + ';\n  const BL = '
         + json.dumps([[b[0], b[4]] for b in BL], ensure_ascii=False) + ';\n')
body += '''  const mk = (tag, cls, html) => { const e = document.createElement(tag); if (cls) e.className = cls; if (html != null) e.innerHTML = html; return e; };
  const parts = [];
  for (const [id, st] of BL) {
    const box = document.getElementById(id + '_opts');
    for (const k of Object.keys(L)) {
      box.appendChild(mk('label', 'opt', `<input type="radio" name="${id}" value="${k}"><span class="row"><span class="letter">${k.toUpperCase()}</span><span>${L[k][0]}<small>${L[k][1]}</small></span></span><img src="${id}_${k}.png" alt="${L[k][0]} (${st})"><span class="states">${st}</span>`));
    }
    parts.push({ name: id, doc: 'mod_block_' + id, note: document.getElementById(id + '_note'), saveEl: document.getElementById(id + '_save') });
  }
'''
body += src[src.index("  let db = null;"):]
(ROOT / "mod_blocks/index.html").write_text(head + body, encoding="utf-8")
