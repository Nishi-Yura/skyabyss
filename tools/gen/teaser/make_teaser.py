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
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SHOTS = os.path.join(ROOT, "run", "shots", "teaser", "shots")
STORY = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures", "gui", "story")
MUSIC = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "sounds", "music")
FONTS = os.path.join(ROOT, "tools", "sources", "fonts")
PLATE = os.path.join(ROOT, "docs", "readme", "plate_title.png")
OUT_DIR = os.path.join(ROOT, "build", "teaser")
FPS = 30
W, H = 1920, 1080

# ------------------------------------------------------------------ 場面の並び
# kind: "rec" = 録画 (src = teaser_x/場面、from = 何枚目から)、"story" = 物語の絵 (src = 章/pN、寄り方)、"title" = 題の札、"black" = 黒
# sec = 長さ (秒)、cap = 字幕 (無ければ None)、fade = 入りと出の黒 (秒)、bright = 明るさの倍率 (暗い録画)
CUTS = [
    dict(kind="story", src="opening/p1", sec=3.0, zoom=(1.00, 1.10), pan=(0.0, -0.03), cap=None, fade=(1.0, 0.3)),
    dict(kind="story", src="opening/p8", sec=3.6, zoom=(1.00, 1.08), pan=(0.0, -0.02), cap="名もない島で、旅人が目を覚ます。", fade=(0.3, 0.3)),
    dict(kind="rec", src="teaser_a/a1_island", start=0, sec=4.0, cap=None, fade=(0.3, 0.2)),
    dict(kind="story", src="opening/p5", sec=3.6, zoom=(1.04, 1.14), pan=(0.03, 0.0), cap="雲の上を、巨神が渡っていく。", fade=(0.3, 0.3)),
    dict(kind="rec", src="teaser_a/a3_capital", start=0, sec=4.0, cap="鐘の都から、旅が始まる。", fade=(0.3, 0.2)),
    dict(kind="rec", src="teaser_a/a4_whale_low", start=10, sec=6.0, cap="その背に、上れ。", fade=(0.2, 0.2)),
    dict(kind="story", src="opening/p4", sec=3.4, zoom=(1.00, 1.10), pan=(0.0, 0.02), cap="四体の巨神、四つの土地。", fade=(0.2, 0.6)),
    dict(kind="black", sec=0.5),
    dict(kind="rec", src="teaser_b/b1_mage", start=0, sec=3.0, cap="六つの職、それぞれの技。", fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_b/b2_warrior", start=0, sec=2.8, cap=None, fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_b/b3_archer", start=0, sec=2.8, cap=None, fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_b/b4_dungeon_boss", start=0, sec=3.4, bright=1.8, cap="地下に眠る、四つのダンジョン。", fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_c/c1_tower_reveal", start=0, sec=3.8, cap="超大型レイド「あいだの塔」", fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_c/c2_tower_stars", start=0, sec=3.4, cap=None, fade=(0.0, 0.0)),
    dict(kind="rec", src="teaser_c/c3_tower_breath", start=0, sec=3.0, cap=None, fade=(0.0, 0.5)),
    dict(kind="title", sec=6.5, fade=(0.6, 0.0)),
]

# 音: (曲, 曲の中の始まり 秒, 映像の何秒から)。重なりは CROSS 秒で溶かし、終わりは END_FADE 秒で消える
MUSIC_PLAN = [("title", 0.0, 0.0), ("boss", 28.0, None)]  # None = 後半の始まり (黒の札のところ)
CROSS = 1.2
END_FADE = 3.0


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


CAP_FONT = None
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
    src = os.path.join(STORY, cut["src"] + ".png")
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
        bg = story_frame(dict(src="opening/p1", zoom=(1.1, 1.1)), 0.0, size)
        bg = Image.blend(bg, Image.new("RGB", size, (6, 8, 18)), 0.45)
        plate = Image.open(PLATE).convert("RGBA")
        s = (h * 0.62) / plate.height
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
        if c["kind"] == "black":
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
    global CAP_FONT, SMALL_FONT
    preview = "--preview" in sys.argv
    only = sys.argv[sys.argv.index("--only") + 1] if "--only" in sys.argv else None
    size = (W // 2, H // 2) if preview else (W, H)
    CAP_FONT = font("DotGothic16-Regular.ttf", int(size[1] * 0.046))
    SMALL_FONT = font("DotGothic16-Regular.ttf", int(size[1] * 0.030))
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
