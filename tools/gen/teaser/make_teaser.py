# -*- coding: utf-8 -*-
"""ティザー映像 (60 秒・1920x1080・30 fps) をつなぐ。

素材:
- ゲームの中の録画: run/shots/teaser/shots/teaser_{a,b,c}/<場面>/0000.png … (tools/shots/teaser_{a,b,c}.txt)
- 物語の絵: assets/skyabyss/textures/gui/story/<章>/pN.png (240x135 のドット絵。大きくして、ゆっくり寄る)
- 題の札: docs/readme/plate_title.png (README の題の絵。版は gradle.properties から入る)
- 音: assets/skyabyss/sounds/music/*.ogg (この mod の BGM。前半と後半で替え、終わりは消えていく)
- 字: tools/sources/fonts (DotGothic16・Zen Antique)

出力: build/teaser/skyabyss_teaser.mp4 (H.264・AAC)。絵はファイルに書かず、ffmpeg へ流す。
使い方 (リポジトリ直下で): python -X utf8 tools/gen/teaser/make_teaser.py [--preview] [--only <場面>]
  --preview: 半分の大きさ・速い設定で書く (確かめ用)
ffmpeg は imageio_ffmpeg の同梱のもの。
"""
import math
import os
import subprocess
import sys

import imageio_ffmpeg
from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SHOTS = os.path.join(ROOT, "run", "shots", "teaser", "shots")
STORY = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "story")
MUSIC = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "sounds", "music")
FONTS = os.path.join(ROOT, "tools", "sources", "fonts")
PLATE = os.path.join(ROOT, "docs", "readme", "plate_title.png")
KEY = os.path.join(ROOT, "docs", "review", "teaser_roughs", "key_a.png")  # キービジュアル (tools/gen/teaser/key_visual.py の A)
BAR = 0.1215  # 映画の帯 (上下。2.35:1 になる高さの割合)
OUT_DIR = os.path.join(ROOT, "build", "teaser")
FPS = 30
W, H = 1920, 1080

# ------------------------------------------------------------------ 場面の並び
# kind: "rec" = 録画 (src = teaser_x/場面、from = 何枚目から)、"story" = 物語の絵 (src = 章/pN、寄り方)、"title" = 題の札、"black" = 黒
# sec = 長さ (秒)、cap = 字幕 (無ければ None)、fade = 入りと出の黒 (秒)、bright = 明るさの倍率 (暗い録画)
CUTS = [
    # ---- 世界 (約 9 秒)
    dict(kind="story", path=KEY, sec=3.6, zoom=(1.00, 1.10), pan=(0.0, 0.03), cap="巨神が空を泳ぐ大陸で、", fade=(1.0, 0.3)),
    dict(kind="rec", src="teaser_a/a4_whale_low", start=40, sec=3.0, cap="その背に上り、", fade=(0.2, 0.1)),
    dict(kind="rec", src="teaser_a/a3_capital", start=60, sec=2.6, cap="鐘の都から旅に出る。", fade=(0.1, 0.3)),
    # ---- 遊び (曲を切り替える)
    dict(kind="rec", src="teaser_v2a/v1_warrior_run", start=0, sec=0.9, card="六つの職と技", switch=True),
    dict(kind="rec", src="teaser_v2a/v1_warrior_run", start=122, sec=2.3, card="六つの職と技"),
    dict(kind="rec", src="teaser_v2a/v2_mage", start=10, sec=2.8, card="六つの職と技"),
    dict(kind="rec", src="teaser_v2a/v3_archer", start=20, sec=2.6, card="六つの職と技"),
    dict(kind="rec", src="teaser_v2b/w1_back_run", start=20, sec=3.0, card="巨神の背の試練"),
    dict(kind="rec", src="teaser_v2b/w2_dungeon_fight", start=0, sec=6.2, card="地下のダンジョン"),
    dict(kind="rec", src="teaser_c/c1_tower_reveal", start=10, sec=2.6, card="超大型レイド「あいだの塔」"),
    dict(kind="rec", src="teaser_v2b/w3_tower_dodge", start=0, sec=4.2, card="超大型レイド「あいだの塔」"),
    dict(kind="rec", src="teaser_c/c2_tower_stars", start=20, sec=2.4, card="超大型レイド「あいだの塔」"),
    dict(kind="rec", src="teaser_v2a/v4_sky_ray", start=100, sec=2.6, card="空を飛ぶ乗り物"),
    dict(kind="rec", src="teaser_v2a/v6_capital_walk", start=20, sec=3.2, card="鐘の都と町の人", fade=(0.0, 0.5)),
    dict(kind="title", sec=6.5, fade=(0.6, 0.0)),
]

# 音: (曲, 曲の中の始まり 秒, 映像の何秒から)。重なりは CROSS 秒で溶かし、終わりは END_FADE 秒で消える
MUSIC_PLAN = [("title", 0.0, 0.0), ("boss", 26.0, None)]  # None = 後半の始まり (黒の札のところ)
CROSS = 1.2
END_FADE = 3.0


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


CAP_FONT = None
CARD_FONT = None
SMALL_FONT = None


def ease(t):
    return t * t * (3 - 2 * t)


# ------------------------------------------------------------------ 絵を作る
def rec_frames(src):
    d = os.path.join(SHOTS, src)
    if not os.path.isdir(d):
        return []
    return sorted(os.path.join(d, f) for f in os.listdir(d) if f.endswith(".png"))


_cache = {}


def load(path, size):
    key = (path, size)
    if key not in _cache:
        if len(_cache) > 8:
            _cache.clear()
        im = Image.open(path).convert("RGB")
        if im.size != size:
            im = im.resize(size, Image.LANCZOS)
        _cache[key] = im
    return _cache[key]


def story_frame(cut, t, size):
    """ドット絵を大きくして、ゆっくり寄る (Ken Burns)。粗い画素のまま (NEAREST) 大きくしてから、寄りの分だけ滑らかに縮める"""
    w, h = size
    src = cut.get("path") or os.path.join(STORY, cut["src"] + ".png")
    key = ("story", src, size)
    if key not in _cache:
        base = Image.open(src).convert("RGB")
        scale = math.ceil(w * 1.2 / base.width)
        _cache[key] = base.resize((base.width * scale, base.height * scale), Image.NEAREST)
    big = _cache[key]
    z0, z1 = cut.get("zoom", (1.0, 1.08))
    px, py = cut.get("pan", (0.0, 0.0))
    z = z0 + (z1 - z0) * ease(t)
    cw, ch = big.width / z, big.height / z
    cx = big.width / 2 + px * big.width * ease(t)
    cy = big.height / 2 + py * big.height * ease(t)
    box = (cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
    return big.resize(size, Image.BILINEAR, box=box)


def title_frame(t, size, dur):
    w, h = size
    key = ("title", size)
    if key not in _cache:
        bg = story_frame(dict(path=KEY, zoom=(1.08, 1.08)), 0.0, size)
        bg = Image.blend(bg, Image.new("RGB", size, (6, 8, 18)), 0.35)
        plate = Image.open(PLATE).convert("RGBA")
        s = (h * 0.50) / plate.height
        plate = plate.resize((int(plate.width * s), int(plate.height * s)), Image.NEAREST)
        _cache[key] = (bg, plate)
    bg, plate = _cache[key]
    im = bg.copy()
    a = min(1.0, t * dur / 1.2)
    pl = plate.copy()
    pl.putalpha(pl.getchannel("A").point(lambda v: int(v * a)))
    im.paste(pl, ((w - pl.width) // 2, int(h * 0.08)), pl)
    d = ImageDraw.Draw(im)
    a2 = max(0.0, min(1.0, (t * dur - 1.4) / 1.0))
    if a2 > 0:
        lines = ["Minecraft Java 1.21.1 ・ NeoForge", "nishi-yura.github.io/skyabyss"]
        y = int(h * 0.08) + plate.height + int(h * 0.04)
        for i, s_ in enumerate(lines):
            f = SMALL_FONT if i == 0 else CAP_FONT
            tw = d.textlength(s_, font=f)
            col = (int(200 * a2), int(210 * a2), int(230 * a2)) if i == 0 else (int(242 * a2), int(193 * a2), int(78 * a2))
            d.text(((w - tw) / 2, y), s_, font=f, fill=col)
            y += int(f.size * 1.6)
    return im


def caption(im, text, alpha):
    if not text or alpha <= 0:
        return im
    w, h = im.size
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    tw = d.textlength(text, font=CAP_FONT)
    x, y = (w - tw) / 2, h * 0.80
    if LETTERBOX:
        y = h - h * BAR / 2 - CAP_FONT.size * 0.62
        d.text((x, y), text, font=CAP_FONT, fill=(244, 240, 228, int(255 * alpha)))
        return Image.alpha_composite(im.convert("RGBA"), layer).convert("RGB")
    # 下に薄い帯 (字が景色に負けないように)
    band = Image.new("RGBA", im.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    pad = CAP_FONT.size * 0.9
    bd.rectangle((0, y - pad * 0.6, w, y + CAP_FONT.size + pad * 0.7), fill=(0, 0, 0, int(110 * alpha)))
    band = band.filter(ImageFilter.GaussianBlur(CAP_FONT.size * 0.6))
    d.text((x + 2, y + 2), text, font=CAP_FONT, fill=(0, 0, 0, int(200 * alpha)))
    d.text((x, y), text, font=CAP_FONT, fill=(244, 240, 228, int(255 * alpha)))
    out = Image.alpha_composite(im.convert("RGBA"), band)
    return Image.alpha_composite(out, layer).convert("RGB")


LETTERBOX = True
_VIG = {}


def grade(im):
    """色の仕上げ: 彩度とコントラストを少し上げ、暖かく、明るい所にやわらかい光、周りを少し暗く"""
    w, h = im.size
    im = ImageEnhance.Color(im).enhance(1.14)
    im = ImageEnhance.Contrast(im).enhance(1.07)
    r, g, b = im.split()
    r = r.point(lambda v: min(255, int(v * 1.035 + 2)))
    b = b.point(lambda v: int(v * 0.96))
    im = Image.merge("RGB", (r, g, b))
    small = im.resize((w // 6, h // 6), Image.BILINEAR)
    hi = small.point(lambda v: max(0, v - 170) * 3)
    hi = hi.filter(ImageFilter.GaussianBlur(6)).resize((w, h), Image.BILINEAR)
    im = ImageChops.add(im, ImageEnhance.Brightness(hi).enhance(0.45))
    if (w, h) not in _VIG:
        m = Image.radial_gradient("L").resize((w, h), Image.BILINEAR)
        _VIG[(w, h)] = m.point(lambda v: int(255 - max(0, v - 120) * 0.55))
    return Image.composite(im, Image.new("RGB", (w, h), (0, 0, 0)), _VIG[(w, h)])


def bars(im):
    w, h = im.size
    bh = int(round(h * BAR))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, w, bh), fill=(0, 0, 0))
    d.rectangle((0, h - bh, w, h), fill=(0, 0, 0))
    return im


def card(im, text, alpha):
    """場面の札: 上の帯の左に、金の縁の小さな札 (README の題の絵と同じ色)"""
    if not text or alpha <= 0:
        return im
    w, h = im.size
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = CARD_FONT
    tw = d.textlength(text, font=f)
    pad = f.size * 0.7
    bh = int(round(h * BAR))
    x0 = int(w * 0.045 - (1 - alpha) * 30)
    y0 = int(bh / 2 - (f.size + pad) / 2)
    x1, y1 = int(x0 + tw + pad * 2.6), int(y0 + f.size + pad)
    a = int(255 * alpha)
    gold = (242, 193, 78, a)
    d.rectangle((x0, y0, x1, y1), fill=(14, 18, 40, int(235 * alpha)), outline=gold, width=max(2, f.size // 12))
    d.rectangle((x0 + 5, y0 + 5, x1 - 5, y1 - 5), outline=(120, 92, 40, a), width=1)
    d.polygon([(x0 + pad * 0.7, y0 + (y1 - y0) / 2), (x0 + pad * 1.05, y0 + (y1 - y0) / 2 - f.size * 0.22),
               (x0 + pad * 1.4, y0 + (y1 - y0) / 2), (x0 + pad * 1.05, y0 + (y1 - y0) / 2 + f.size * 0.22)], fill=gold)
    d.text((x0 + pad * 1.9, y0 + pad / 2 - f.size * 0.08), text, font=f, fill=(246, 232, 196, a))
    return Image.alpha_composite(im.convert("RGBA"), layer).convert("RGB")


def fade(im, k):
    if k >= 1.0:
        return im
    return Image.blend(Image.new("RGB", im.size, (0, 0, 0)), im, max(0.0, k))


def frames(size, only=None):
    """(何枚目, 絵) を順に出す"""
    for ci, cut in enumerate(CUTS):
        if only and cut.get("src", cut["kind"]).split("/")[-1] != only:
            continue
        n = int(round(cut["sec"] * FPS))
        recs = rec_frames(cut["src"]) if cut["kind"] == "rec" else []
        if cut["kind"] == "rec" and not recs:
            print("  (no frames for %s, black)" % cut["src"])
        f_in, f_out = cut.get("fade", (0.0, 0.0))
        for i in range(n):
            t = i / max(1, n - 1)
            sec = i / FPS
            if cut["kind"] == "rec":
                if recs:
                    k = min(len(recs) - 1, cut.get("start", 0) + i)
                    im = load(recs[k], size).copy()
                    if cut.get("bright"):
                        im = ImageEnhance.Brightness(im).enhance(cut["bright"])
                else:
                    im = Image.new("RGB", size, (0, 0, 0))
            elif cut["kind"] == "story":
                im = story_frame(cut, t, size)
            elif cut["kind"] == "title":
                im = title_frame(t, size, cut["sec"])
            else:
                im = Image.new("RGB", size, (0, 0, 0))
            if cut["kind"] == "rec" and cut.get("grade", True):
                im = grade(im)
            if LETTERBOX and cut["kind"] != "title":
                im = bars(im)
            if cut.get("card"):
                im = card(im, cut["card"], min(1.0, sec / 0.3, (cut["sec"] - sec) / 0.3))
            # 字幕: 0.35 秒で現れ、終わりの 0.35 秒で消える
            if cut.get("cap"):
                ca = min(1.0, sec / 0.35, (cut["sec"] - sec) / 0.35)
                im = caption(im, cut["cap"], ca)
            k = 1.0
            if f_in > 0:
                k = min(k, sec / f_in)
            if f_out > 0:
                k = min(k, (cut["sec"] - sec) / f_out)
            yield fade(im, k)


def total_sec():
    return sum(c["sec"] for c in CUTS)


def second_half_start():
    s = 0.0
    for c in CUTS:
        if c.get("switch") or c["kind"] == "black":
            return s
        s += c["sec"]
    return total_sec() / 2


def build_audio(ff, out_wav):
    dur = total_sec()
    half = second_half_start()
    a, b = MUSIC_PLAN
    a_len = half + CROSS / 2
    b_start = half - CROSS / 2
    bd = int(b_start * 1000)
    flt = ("[0:a]atrim=start={a0}:duration={al},asetpts=PTS-STARTPTS,afade=t=in:st=0:d=1.0,afade=t=out:st={ao}:d={cr}[a];"
           "[1:a]atrim=start={b0}:duration={bl},asetpts=PTS-STARTPTS,afade=t=in:st=0:d={cr},adelay={bd}|{bd}[b];"
           "[a][b]amix=inputs=2:duration=longest:normalize=0,atrim=duration={dur},afade=t=out:st={fo}:d={fd}[m]").format(
        a0=a[1], al=a_len, ao=a_len - CROSS, cr=CROSS, b0=b[1], bl=dur - b_start, bd=bd, dur=dur, fo=dur - END_FADE, fd=END_FADE)
    cmd = [ff, "-y", "-loglevel", "error", "-i", os.path.join(MUSIC, a[0] + ".ogg"), "-i", os.path.join(MUSIC, b[0] + ".ogg"),
           "-filter_complex", flt, "-map", "[m]", "-ac", "2", "-ar", "48000", out_wav]
    subprocess.check_call(cmd)


def main():
    global CAP_FONT, SMALL_FONT, CARD_FONT
    preview = "--preview" in sys.argv
    only = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    size = (W // 2, H // 2) if preview else (W, H)
    CAP_FONT = font("DotGothic16-Regular.ttf", int(size[1] * 0.046))
    SMALL_FONT = font("DotGothic16-Regular.ttf", int(size[1] * 0.030))
    CARD_FONT = font("DotGothic16-Regular.ttf", int(size[1] * 0.036))
    os.makedirs(OUT_DIR, exist_ok=True)
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    name = "skyabyss_teaser" + ("_preview" if preview else "") + ("_" + only if only else "")
    video = os.path.join(OUT_DIR, name + "_video.mp4")
    enc = [ff, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % size, "-r", str(FPS), "-i", "-",
           "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "veryfast" if preview else "slow", "-crf", "26" if preview else "17",
           "-movflags", "+faststart", video]
    p = subprocess.Popen(enc, stdin=subprocess.PIPE)
    n = 0
    for im in frames(size, only):
        p.stdin.write(im.tobytes())
        n += 1
        if n % 150 == 0:
            print("  %d frames" % n)
    p.stdin.close()
    p.wait()
    if only:
        print("wrote", video, n, "frames")
        return
    wav = os.path.join(OUT_DIR, "teaser_audio.wav")
    build_audio(ff, wav)
    final = os.path.join(OUT_DIR, name + ".mp4")
    subprocess.check_call([ff, "-y", "-loglevel", "error", "-i", video, "-i", wav, "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                           "-shortest", "-movflags", "+faststart", final])
    os.remove(video)
    print("wrote", final, "%d frames, %.1f s" % (n, total_sec()))


if __name__ == "__main__":
    main()
