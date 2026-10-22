"""装備の自由度の確認シート (品の絵・ツールチップの例・炉の刻みの流れ・費用と落ち方)。
使い方: python tools/sheets/gear_freedom_sheet.py → docs/review/previews/gear_freedom_sheet.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent
ICON = ROOT / "src/main/resources/assets/skyabyss/textures/item"
OUT = ROOT / "docs/review/previews/gear_freedom_sheet.png"
FONT = next(p for p in ["C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"] if Path(p).exists())

BG = (24, 27, 34)
PANEL = (32, 36, 45)
LINE = (58, 66, 80)
TEXT = (200, 205, 214)
DIM = (128, 136, 150)
TEAL = (143, 209, 201)
GOLD = (242, 215, 138)
VIOLET = (201, 168, 255)
BLUE = (91, 140, 150)


def f(size):
    return ImageFont.truetype(FONT, size)


W, H = 1400, 860
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)
d.text((30, 20), "装備の自由度  アスペクト・ソケット・ルーン・世代", font=f(26), fill=TEXT)
d.text((30, 58), "炉の「刻み」つまみ ／ docs/design/03 の 3.6、31 の A02・A06・A07・B15", font=f(14), fill=DIM)

# ---- 品
d.rounded_rectangle((30, 90, 700, 330), 8, fill=PANEL, outline=LINE)
d.text((46, 100), "新しい品", font=f(16), fill=TEXT)
items = [("socket_shard_stone", "岩の欠片"), ("socket_shard_wind", "風の欠片"), ("socket_shard_tide", "潮の欠片"), ("socket_shard_ash", "灰の欠片"),
         ("socket_shard_bone", "骨の欠片"), ("aspect_core", "アスペクトの芯"), ("skill_rune", "技の修飾ルーン")]
for i, (n, label) in enumerate(items):
    im = Image.open(ICON / f"{n}.png").convert("RGBA").resize((72, 72), Image.NEAREST)
    x = 46 + i * 92
    img.paste(im, (x, 134), im)
    d.text((x - 4, 214), label, font=f(11), fill=DIM)
d.text((46, 244), "欠片: 巨神の素材 4 ＋ 結晶 1 で作る。差すとその木のタグが 1 つ数えられ、小さな数字が付く", font=f(12), fill=DIM)
d.text((46, 266), "芯: 伝説の書き換えを抜いたもの。レアの武器・防具に 1 つだけ刻める", font=f(12), fill=DIM)
d.text((46, 288), "ルーン: 1 つの技に 1 枚。力 +10〜30％ か 待ち −10〜20％。合計の力は +100％まで", font=f(12), fill=DIM)

# ---- ツールチップ
d.rounded_rectangle((720, 90, 1370, 330), 8, fill=PANEL, outline=LINE)
d.text((736, 100), "ツールチップの例（レアの大剣）", font=f(16), fill=TEXT)
lines = [
    ("鯨骨の大剣 +3", (120, 170, 255)),
    ("レア · 岩鯨", (120, 170, 255)),
    (" ◇ 攻撃力 +6％", (180, 204, 216)),
    (" ◆ 岩の欠片 ─ 攻撃力 +2％", TEAL),
    (" ◆ 風読み 風裂き の力 +25％", GOLD),
    (" ◈ アスペクト ─ 噛みつき", GOLD),
    ("   技の書き換え: 風裂き ─ 噛みつき", DIM),
    ("第 3 世代", VIOLET),
    (" ❖ 技の待ち −4％", (180, 204, 216)),
    ("あと 3 世代で、形見の特性が開く", DIM),
]
for i, (t, c) in enumerate(lines):
    d.text((740, 128 + i * 19), t, font=f(13), fill=c)

# ---- 炉の画面の見取り図
d.rounded_rectangle((30, 350, 700, 830), 8, fill=PANEL, outline=LINE)
d.text((46, 360), "炉の画面（つまみ 4 つ）", font=f(16), fill=TEXT)
d.rounded_rectangle((50, 392, 340, 560), 6, fill=(20, 23, 30), outline=BLUE)
d.rectangle((66, 440, 90, 464), outline=BLUE)
d.text((60, 420), "素材", font=f(11), fill=DIM)
d.rectangle((120, 440, 144, 464), outline=TEAL)
d.text((112, 420), "装備", font=f(11), fill=DIM)
d.text((160, 442), "名前・階級・特性", font=f(11), fill=DIM)
d.text((70, 480), "左: 素材の枠（欠片・ルーン・芯）と装備の枠", font=f(11), fill=DIM)
d.rounded_rectangle((360, 392, 680, 560), 6, fill=(20, 23, 30), outline=BLUE)
for i, (t, on) in enumerate([("強化", 0), ("再鍛造", 0), ("交換", 0), ("刻み", 1)]):
    x = 372 + i * 74
    d.rounded_rectangle((x, 400, x + 68, 422), 4, fill=(60, 70, 86) if on else (36, 40, 50), outline=TEAL if on else LINE)
    d.text((x + 14, 404), t, font=f(12), fill=TEXT if on else DIM)
for i, t in enumerate(["抽出", "刻印", "穴"]):
    x = 372 + i * 92
    d.rounded_rectangle((x, 434, x + 86, 454), 4, fill=(44, 50, 62), outline=LINE)
    d.text((x + 28, 437), t, font=f(12), fill=TEXT)
d.text((372, 466), "抽出: 書き換え名・結晶 壊す2／残す4", font=f(11), fill=DIM)
d.text((372, 484), "刻印: 芯を置いて「刻む」（結晶 1）", font=f(11), fill=DIM)
d.text((372, 502), "穴: 差す／外す（結晶 1）／穴を足す", font=f(11), fill=DIM)
d.text((372, 520), "   足す: 結晶 2 ＋ 巨神の素材 8（2 つ目 16）", font=f(11), fill=DIM)
d.text((60, 580), "流れ", font=f(14), fill=TEXT)
flow = ["伝説を置く → 「抽出」で芯", "レアを置く → 素材枠の芯を「刻む」", "穴のある装備 → 欠片・ルーンを「差す」", "転生 → 持ち物の装備が +1 世代"]
for i, t in enumerate(flow):
    d.text((60, 608 + i * 24), "・" + t, font=f(13), fill=DIM)
d.text((60, 722), "どの操作もサーバーが費用と条件を毎回検める。創造は結晶を払わない。", font=f(12), fill=DIM)
d.text((60, 744), "分解すると、差した欠片・ルーンとアスペクトの芯は品に戻る。", font=f(12), fill=DIM)

# ---- 数字
d.rounded_rectangle((720, 350, 1370, 830), 8, fill=PANEL, outline=LINE)
d.text((736, 360), "数字", font=f(16), fill=TEXT)
rows = [
    ("穴の抽選（レア）", "0個 50％ / 1個 38％ / 2個 12％"),
    ("穴の抽選（伝説）", "0個 30％ / 1個 45％ / 2個 25％"),
    ("心臓の落ち物（一人ずつ）", "巨神の欠片 55％（1〜2）・ルーン 10％"),
    ("　岩鯨のみ追加", "骨の欠片 25％"),
    ("試練（済ませるたび）", "ルーン 初回 12％ / 以後 4％（極は 2 倍）"),
    ("　初回のみ", "欠片 35％"),
    ("装備そのものの確率", "変えない（図鑑・天井・交換はそのまま）"),
    ("世代", "転生ごとに +1（上限 12）。3 ごとに形見の特性"),
    ("形見の特性", "付いていない種類から、幅の下端の小さな値"),
    ("抽出", "壊す: 結晶 2 / 印を残す: 結晶 4（抜け殻になる）"),
    ("技の力の合計", "+100％（×2.0）で頭打ち（SkillBuild）"),
]
for i, (a, b) in enumerate(rows):
    y = 394 + i * 38
    d.line((736, y - 6, 1354, y - 6), fill=LINE)
    d.text((740, y), a, font=f(13), fill=TEAL)
    d.text((960, y), b, font=f(13), fill=DIM)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
print("wrote", OUT)
