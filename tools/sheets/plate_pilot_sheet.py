# -*- coding: utf-8 -*-
"""画素の板の絵柄の試作の見比べ (docs/review/plate_ui/)。
使い方: 台本 tools/shots/plate_pilot.txt・plate_capital.txt・plate_shop.txt を撮ったあとで
        python -I tools/sheets/plate_pilot_sheet.py [stills|motion|gif|all|audit [台本 ...]|capital|shop|refs <絵の場所 ...>|crop <台本> <名前> x y w h]
入力: run/shots/<台本>/shots/<台本>/*.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/plate_ui"


def shots(script):
    # "<ゲームのフォルダ>:<台本>" なら run/shots/<ゲームのフォルダ>/shots/<台本> (-PshotsName を付けて撮ったとき)
    if ":" in script:
        home, name = script.split(":", 1)
        return ROOT / "run/shots" / home / "shots" / name
    return ROOT / "run/shots" / script / "shots" / script


def font(size):
    for name in ("meiryob.ttc", "YuGothB.ttc", "msgothic.ttc"):
        p = Path("C:/Windows/Fonts") / name
        if p.exists():
            return ImageFont.truetype(str(p), size)
    return ImageFont.load_default()


def grid(items, cols, cell, out, pad=0, head=0):
    """items = [(絵の場所, 札の字)]。head = 絵の上に空ける札の段の高さ"""
    rows = (len(items) + cols - 1) // cols
    cw, ch = cell[0] + pad, cell[1] + pad + head
    sheet = Image.new("RGB", (cols * cw + pad, rows * ch + pad), (14, 15, 20))
    d = ImageDraw.Draw(sheet)
    f = font(max(12, head - 12)) if head else font(14)
    for i, (p, label) in enumerate(items):
        x, y = pad + (i % cols) * cw, pad + (i // cols) * ch
        if Path(p).exists():
            im = Image.open(p).convert("RGB")
            scale = min(cell[0] / im.width, cell[1] / im.height)
            size = (round(im.width * scale), round(im.height * scale))
            im = im.resize(size, Image.NEAREST if scale >= 1 else Image.LANCZOS)
            sheet.paste(im, (x + (cell[0] - size[0]) // 2, y + head + (cell[1] - size[1]) // 2))
        if head:
            d.text((x + 4, y + 4), label, fill=(240, 226, 190), font=f)
        else:
            d.rectangle([x + 4, y + 4, x + 14 + 8 * len(label), y + 22], fill=(0, 0, 0))
            d.text((x + 8, y + 5), label, fill=(255, 230, 120), font=f)
    OUT.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT / out)
    print(OUT / out, sheet.size)


LOOKS = {"paper": "今 (切り絵)", "a": "A 鐘の銅", "b": "B 空見の石板", "c": "C 淵の石"}


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "stills"
    if mode == "stills":
        s = shots("plate_pilot")
        items = [(s / f"{k}_{n}.png", f"{k}_{n}") for n in ("warrior", "archer_hover", "room_mage") for k in "abc"]
        grid(items, 3, (640, 360), "pilot_stills.png")
    elif mode == "motion":
        # plate_motion.txt (動きを 10 倍ゆっくりにして撮ったこま) から、段ごとに 12 こまを等しい間で抜いて並べる
        s = shots("plate_motion")
        for key, label, n in (("open", "開く", 34), ("swap", "職を替える", 22), ("armed", "決めの釦の一度目", 10), ("decide", "決める", 64)):
            pick = [round(i * (n - 1) / 11) for i in range(12)] if n > 12 else list(range(n))
            grid([(s / f"{key}_{i}.png", f"{label} {k + 1}") for k, i in enumerate(pick)], 4, (480, 270), f"pilot_motion_{key}.png", pad=4, head=24)
    elif mode == "gif":
        # 動きの GIF (開く → 職を替える → 決めの釦の一度目 → 決める)。こまの間は撮影の都合で一定ではないので、速さはおおよそ
        s = shots("plate_motion")
        frames, durs = [], []
        for key, n, hold in (("open", 34, 900), ("swap", 22, 900), ("armed", 10, 300), ("decide", 64, 700)):
            for i in range(n):
                p = s / f"{key}_{i}.png"
                if not p.exists():
                    continue
                frames.append(Image.open(p).convert("RGB").resize((854, 480), Image.LANCZOS))
                durs.append((180 if key == "armed" else 30) if i < n - 1 else hold)
        OUT.mkdir(parents=True, exist_ok=True)
        pal = frames[len(frames) // 3].quantize(colors=128, method=Image.MEDIANCUT)
        q = [f.quantize(palette=pal, dither=Image.NONE) for f in frames]
        q[0].save(OUT / "pilot_motion_a.gif", save_all=True, append_images=q[1:], duration=durs, loop=0, optimize=False)
        print(OUT / "pilot_motion_a.gif", len(q))
    elif mode == "capital":
        s = shots("plate_capital")
        grid([(s / f"day_{k}_warrior.png", LOOKS[k] + " ・ 昼の都") for k in ("paper", "a", "b", "c")], 2, (960, 540), "pilot_day.png", pad=8, head=40)
        grid([(s / f"night_{k}_mage.png", LOOKS[k] + " ・ 夜の都") for k in ("paper", "a", "b", "c")], 2, (960, 540), "pilot_night.png", pad=8, head=40)
    elif mode == "shop":
        s = shots("plate_shop")
        grid([(s / f"{k}_shop.png", LOOKS[k] + " ・ 店") for k in ("paper", "a", "b", "c")], 2, (960, 540), "pilot_shop.png", pad=8, head=40)
    elif mode == "refs":
        # refs <参照の絵 ...>: 参照のこまと、同じ幅の A・B・C を一枚に
        s = shots("plate_capital")
        items = [(p, f"参照 {i + 1}") for i, p in enumerate(sys.argv[2:])]
        items += [(s / f"night_{k}_mage.png", LOOKS[k]) for k in ("a", "b", "c")]
        grid(items, 3, (640, 360), "pilot_vs_refs.png", pad=8, head=36)
    elif mode == "all":
        # all [枚目]: plate_all.txt の全画面を 6 枚ずつの一覧に (既定は全部の枚を書き出す)
        # all [台本] [接頭辞]: 既定は plate_all を all_<n>.png に
        script = sys.argv[2] if len(sys.argv) > 2 else "plate_all"
        pre = sys.argv[3] if len(sys.argv) > 3 else "all"
        s = shots(script)
        names = sorted(p.stem for p in s.glob("*.png"))
        for old in OUT.glob(pre + "_*.png"):
            old.unlink()
        for k in range(0, len(names), 6):
            grid([(s / (n + ".png"), n.replace("_1920_g4", "")) for n in names[k:k + 6]], 3, (640, 360), f"{pre}_{k // 6 + 1}.png", pad=2, head=18)
    elif mode == "audit":
        # audit [台本 ...]: 撮った絵の中に残っている切り絵の色 (桃色・濃い桃色・生成りの面) の画素を数える。多い順に出す
        import numpy as np
        pal = {"pink": (0xE4, 0x5A, 0x9C), "pink_hi": (0xF8, 0x9C, 0xD2), "pink_lo": (0x8C, 0x2C, 0x60), "cream": (0xEC, 0xE4, 0xD6), "cream_lo": (0xC9, 0xBF, 0xAE)}
        rows = []
        for script in (sys.argv[2:] or ["plate_all"]):
            for p in sorted(shots(script).glob("*.png")):
                a = np.asarray(Image.open(p).convert("RGB")).astype(int)
                hit = {}
                for k, c in pal.items():
                    n = int((np.abs(a - np.array(c)).sum(axis=2) <= 6).sum())
                    if n >= (4000 if k.startswith("cream") else 200):
                        hit[k] = n
                if hit:
                    rows.append((sum(hit.values()), script.split(":")[-1] + "/" + p.stem, hit))
        for n, name, hit in sorted(rows, reverse=True)[:40]:
            print(n, name, hit)
        print("shots with paper colours:", len(rows))
    elif mode == "crop":
        script, name = sys.argv[2], sys.argv[3]
        x, y, w, h = (int(v) for v in sys.argv[4:8])
        im = Image.open(shots(script) / (name + ".png")).convert("RGB").crop((x, y, x + w, y + h))
        OUT.mkdir(parents=True, exist_ok=True)
        im.save(OUT / "_crop.png")
        print(OUT / "_crop.png", im.size)


if __name__ == "__main__":
    main()
