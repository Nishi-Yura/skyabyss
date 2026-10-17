"""開幕 (B0) の題字の絵を描く (docs/design/17 の磨き)。

使い方: python tools/gen_opening_title.py
出力先: src/main/resources/assets/skyabyss/textures/gui/opening/
  title_ja.png / title_en.png           : 題字と副題 (明朝体。透明な背景に、白に近い青緑の字)
  title_ja_glow.png / title_en_glow.png : 同じ形の青緑のにじみだけ (OpeningOverlay が、ゆっくり明滅させて重ねる)
四枚は同じ大きさで、重ねればそのまま合う。README のバナー (docs/readme/banner.png) と同じ、明朝体 + 青緑の光。
フォントは Windows の游明朝 (yumin / yuminl) を使う。見つからなければ BIZ UD明朝、MS 明朝の順。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/textures/gui/opening"
FONTS = Path("C:/Windows/Fonts")
W, H = 1536, 384

TEXT = (226, 249, 244)
GLOW = (84, 214, 206)
SHADOW = (4, 14, 20)


def font(names, size, index=0):
    for n in names:
        p = FONTS / n
        if p.exists():
            try:
                return ImageFont.truetype(str(p), size, index=index)
            except OSError:
                continue
    raise SystemExit("明朝体が見つかりません: " + ", ".join(names))


def spaced(draw, text, fnt, cx, y, gap, fill):
    """字間をあけて、中央 cx に揃えて書く"""
    widths = [draw.textlength(ch, font=fnt) for ch in text]
    total = sum(widths) + gap * (len(text) - 1)
    x = cx - total / 2
    for ch, w in zip(text, widths):
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += w + gap


def layers(title, title_font, title_gap, title_y, sub, sub_font, sub_gap, sub_y):
    text = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    mask = Image.new("L", (W, H), 0)
    shadow = Image.new("L", (W, H), 0)
    for img, fill, dx in ((text, TEXT + (255,), 0), (mask, 255, 0)):
        d = ImageDraw.Draw(img)
        spaced(d, title, title_font, W / 2, title_y, title_gap, fill)
        spaced(d, sub, sub_font, W / 2, sub_y, sub_gap, fill if img is text else 200)
    sd = ImageDraw.Draw(shadow)
    spaced(sd, title, title_font, W / 2 + 4, title_y + 5, title_gap, 255)
    spaced(sd, sub, sub_font, W / 2 + 2, sub_y + 3, sub_gap, 200)
    # 字の下の影 (青緑の光を乗せても読めるように)
    shadow_rgba = Image.new("RGBA", (W, H), SHADOW + (0,))
    shadow_rgba.putalpha(shadow.filter(ImageFilter.GaussianBlur(3)).point(lambda v: int(v * 0.85)))
    # 細い線 (題字と副題のあいだ。両端は消える)
    line = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(line)
    ly = (title_y + title_font.size + sub_y) // 2 + 6
    for x in range(W // 2 - 360, W // 2 + 360):
        k = 1 - abs(x - W / 2) / 360
        ld.point((x, ly), fill=TEXT + (int(150 * k * k),))
    base = Image.alpha_composite(Image.alpha_composite(shadow_rgba, line), text)
    # 光: 文字の形を二段にぼかして足す
    big = mask.filter(ImageFilter.GaussianBlur(26)).point(lambda v: min(255, int(v * 1.6)))
    small = mask.filter(ImageFilter.GaussianBlur(9)).point(lambda v: min(255, int(v * 1.1)))
    glow_a = Image.fromarray(__import__("numpy").maximum(__import__("numpy").asarray(big) * 0.75, __import__("numpy").asarray(small) * 0.8).astype("uint8"))
    glow = Image.new("RGBA", (W, H), GLOW + (0,))
    glow.putalpha(glow_a)
    # 字そのものにも、ごく薄く青緑のにじみを足しておく (光だけを消しても字が浮かないように)
    return base, glow


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    mincho = ["yumin.ttf", "yuminl.ttf"]
    ja_title = font(mincho, 150)
    ja_sub = font(["yumin.ttf", "yuminl.ttf"], 50)
    en_title = font(["yumin.ttf"], 118)
    en_sub = font(["yumin.ttf", "yuminl.ttf"], 52)
    ja = layers("天と淵のあいだ", ja_title, 62, 96, "BETWEEN SKY AND ABYSS", ja_sub, 18, 262)
    en = layers("Between Sky and Abyss", en_title, 10, 110, "S K Y   A B Y S S".replace(" ", " "), en_sub, 22, 262)
    for name, (img, glow) in (("ja", ja), ("en", en)):
        img.save(OUT / f"title_{name}.png")
        glow.save(OUT / f"title_{name}_glow.png")
        print(name, img.size)
    # blur: 縮めて表示するので、線形の補間にする
    for name in ("title_ja", "title_en", "title_ja_glow", "title_en_glow"):
        (OUT / f"{name}.png.mcmeta").write_text('{"texture": {"blur": true}}\n', encoding="utf-8")


if __name__ == "__main__":
    main()
