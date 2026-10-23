# 心臓の技の見た目の台紙 (docs/review/heart_fx/<心臓>.png)。撮影は tools/shots/heart_fx.txt (heart_fx_gen.py が書く)
#   python tools/shots/heart_fx_sheet.py run/shots/heart_fx/shots/heart_fx docs/review/heart_fx
# 一行 = 一つの技。左から 溜め → 当たり → 残り (8 tick おき)
import os
import sys
from PIL import Image, ImageDraw, ImageFont

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
FONT = "C:/Windows/Fonts/YuGothM.ttc"
BG = (36, 38, 42)
INK = (220, 214, 200)
SUB = (150, 146, 138)
ACC = {"whale": (150, 214, 222), "tide": (110, 190, 220), "storm": (236, 224, 150), "ember": (236, 150, 80), "domain": (200, 160, 220)}

SHEETS = [
    ("whale", "岩鯨の心臓 (石・骨)", [
        ("thrust", "骨の突き", "骨の粉が核へ吸い込まれ、帯を骨片が貫く。砂ぼこりが残る"),
        ("sweep", "薙ぎ払い", "扇の縁を砂が這い、面が弾ける"),
        ("leap", "跳びかかり", "床から骨粉が湧き、岩塊が落ちて砕け、衝撃の輪が広がる"),
        ("crack", "地割れ", "ひびが走り、裂け目から溶岩と炎が噴く"),
        ("volley", "骨の連射", "核に集まり、帯を三つの弾が飛ぶ"),
        ("beam", "核の光線", "核へ光が集まり、振り回す帯に骨と光の奔流"),
        ("chain", "骨の鎖", "縁を這う骨粉と、振り回しの縁の骨片"),
        ("shell_drop", "殻落とし", "空から石が降り、影が締まり、落ちて砕ける"),
    ]),
    ("tide", "潮殻の心臓 (水・珊瑚・泡)", [
        ("breath", "潮の息", "しぶきが口へ吸い込まれ、水の奔流が帯を走る。泡が残る"),
        ("shell_strike", "殻打ち", "床から泡としぶきが湧き、面が弾けて衝撃の輪"),
        ("whirl", "渦", "渦を巻いて中心へ流れ、弾ける"),
        ("pearl_rain", "真珠の雨 (槍)", "湧き上がる泡のあと、水柱が立つ"),
        ("ebb", "引き潮", "水が心臓へ引き、面が弾ける"),
        ("moon_wave", "月の波", "縁を泡が這い、振り回しの帯に泡の頭"),
        ("spring_tide", "大潮", "大きな渦と、水柱と、衝撃の輪"),
    ]),
    ("storm", "雷翼の心臓 (雷・火花・閃光)", [
        ("blade", "風刃", "縁を火花が這い、振り回しの縁に雲と火花"),
        ("chain", "連鎖の雷", "帯に火花がまたたき、帯を雷が走る"),
        ("wingbeat", "羽ばたき", "空から風が降り、外へ走る輪が雲を噴く"),
        ("charge", "突進", "雷が集まり、弧を描いて落ち、閃光と衝撃の輪"),
        ("feather", "羽の雨", "空から光の羽が降り、落ちて弾ける"),
        ("flash", "閃光", "光が集まり、扇いっぱいの閃光"),
        ("web", "雷の網", "火花がまたたき、振り回しの帯に雷"),
        ("barrage", "招雷の連打", "火花のまたたき → 空からの稲妻 → 閃光"),
        ("cross", "十字の雷", "縁を這う火花、帯を貫く雷"),
    ]),
    ("ember", "余燼の心臓 (熾・溶岩・煙)", [
        ("breath", "炎の息", "熾が口へ吸い込まれ、炎の奔流。くすぶりが残る"),
        ("pulse", "熱の鼓動", "外へ走る炎の輪"),
        ("orb", "熾の玉", "心臓のまわりを熾が回り、弧を描いて落ちて弾ける"),
        ("gale", "鞴の嵐", "熱い風が渦を巻き、中心が弾けて衝撃の輪"),
        ("pellet", "灰の弾", "弧を描く灰の弾、落ちてくすぶる"),
        ("fading", "消えかけの鼓動", "熾が集まり、大きく弾けて衝撃の輪"),
        ("forge", "炉打ち", "扇の縁を熾が這い、弾け、中心に炎の柱"),
        ("embers", "熾の散り", "熾がまたたき、小さく弾ける"),
    ]),
]
DOMAINS = ("domain", "領域の決まり", [
    ("domain_whale", "石の海", "骨の波の頭と、ひびから立つ骨の柱"),
    ("domain_tide", "合わせ貝", "空から殻が落ちる影と、泡の帯"),
    ("domain_storm", "雷の檻", "火花がまたたく帯と、避雷の柱へ落ちる稲妻"),
    ("domain_ember", "炉心", "湧き上がる熱と、くすぶる炎"),
])

W, H = 384, 216
PAD = 16
LABEL = 230


def frames(prefix):
    return [f"{src}/{prefix}_{i}.png" for i in range(4)]


def sheet(key, title, rows, prefix_of):
    rowh = H + PAD
    canvas = Image.new("RGB", (PAD * 2 + LABEL + (W + 8) * 4, 70 + rowh * len(rows) + PAD), BG)
    d = ImageDraw.Draw(canvas)
    f_title = ImageFont.truetype(FONT, 26)
    f_row = ImageFont.truetype(FONT, 20)
    f_sub = ImageFont.truetype(FONT, 14)
    d.text((PAD, 18), title + "  ― 溜め → 当たり → 残り (左から 0.4 秒おき。床の赤い印はそのまま)", font=f_title, fill=INK)
    y = 70
    for name, label, line in rows:
        d.rectangle([PAD, y + 4, PAD + 4, y + 40], fill=ACC[key])
        d.text((PAD + 14, y), label, font=f_row, fill=INK)
        # 説明は折り返す
        words, cur, ly = list(line), "", y + 34
        for ch in words:
            if d.textlength(cur + ch, font=f_sub) > LABEL - 24:
                d.text((PAD + 14, ly), cur, font=f_sub, fill=SUB)
                cur, ly = ch, ly + 20
            else:
                cur += ch
        d.text((PAD + 14, ly), cur, font=f_sub, fill=SUB)
        for i, path in enumerate(frames(prefix_of(name))):
            x = PAD + LABEL + i * (W + 8)
            if os.path.exists(path):
                im = Image.open(path).convert("RGB").resize((W, H), Image.LANCZOS)
                canvas.paste(im, (x, y))
            else:
                d.rectangle([x, y, x + W, y + H], outline=SUB)
                d.text((x + 10, y + 10), "(no shot)", font=f_sub, fill=SUB)
        y += rowh
    canvas.save(f"{out}/{key}.png", optimize=True)


for key, title, rows in SHEETS:
    sheet(key, title, rows, lambda n, k=key: f"{k}_{n}")
sheet(DOMAINS[0], DOMAINS[1], DOMAINS[2], lambda n: n)
print("sheets written to", out)
