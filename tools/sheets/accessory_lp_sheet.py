"""低ポリの装飾品の撮影 (tools/gen/shots/gen_accessory_lp_shots.py の台本) から、巨神ごとのコンタクトシートを作る。

使い方: python tools/sheets/accessory_lp_sheet.py [thunder|ember|whale|tide ...]
入力: run/shots/acc_lp_<巨神>/shots/acc_lp_<巨神>/*.png
出力: docs/review/previews/acc_lp_<巨神>.png (行 = 品。左から 平絵 (一人称・三人称)、低ポリ 一人称 3 階級、三人称 3 階級、地面と額縁。
      体に付ける品は、その下に 前・後ろ・斜め・横 x 3 階級の姿)
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../gen/shots',)]
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_accessory_lp_shots import TITANS, TIERS, WORN_VIEWS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/previews"
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/msgothic.ttc",
         "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"]
JP = {"storm_charm": "嵐の護符", "storm_anklet": "嵐纏の足環", "storm_eye_feather": "嵐の目の羽根", "gale_feather": "風切りの羽根",
      "kindling_charm": "熾りの護符", "ember_mantle": "熾火の外套", "ember_lantern": "余燼の灯", "ember_horn": "熾火の角杯", "cinder_lamp": "燠の灯",
      "resonance_charm": "共鳴の護符", "moss_talisman": "苔の護石", "stone_whale_plating": "岩鯨の補強板",
      "tide_chime_charm": "潮鳴りの護符", "moontide_pearl": "月潮の真珠", "tide_watching_shell": "潮見の貝", "tide_pearl": "潮の真珠"}
RJ = {"common": "コモン", "rare": "レア", "legend": "伝説", "flat": "平絵(旧)"}
BG = (36, 40, 48)
# 体に付けた姿の切り出し (1280x720 の中央)。頭の品・体の品・足の品で位置を変える
WORN_BOXES = {"head": (515, 290, 765, 540), "body": (515, 340, 765, 590), "legs": (515, 400, 765, 650)}
WORN_PART = {"storm_anklet": "legs", "storm_eye_feather": "head"}


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def load(d, name, box=None, size=(256, 144)):
    p = d / f"{name}.png"
    if not p.exists():
        return Image.new("RGB", size, (60, 30, 30))
    im = Image.open(p).convert("RGB")
    if box:
        im = im.crop(box)
    return im.resize(size, Image.LANCZOS)


def sheet(titan):
    d = ROOT / f"run/shots/acc_lp_{titan}/shots/acc_lp_{titan}"
    items = TITANS[titan]
    TW, TH = 256, 144
    WW = 192
    W = TW * 9 + 20
    H = 40
    tile = (W - 20) // 12
    for _, _, worn in items:
        H += 30 + TH + 6 + ((24 + tile + 12) if worn else 0)
    im = Image.new("RGB", (W, H), BG)
    dr = ImageDraw.Draw(im)
    dr.text((10, 6), f"{titan} の装飾品 (A 案 低ポリ) — 一人称・三人称・地面と額縁・体に付けた姿", font=font(20), fill=(255, 255, 255))
    y = 40
    for item, natural, worn in items:
        dr.text((10, y + 4), f"{JP[item]} ({item}) — 実際の階級: {RJ[natural]}", font=font(16), fill=(255, 232, 160))
        y += 30
        names = [f"{item}_fp_flat", f"{item}_tp_flat"] + [f"{item}_fp_{t}" for t in TIERS] + [f"{item}_tp_{t}" for t in TIERS] + [f"{item}_world"]
        labels = ["平絵 一人称", "平絵 三人称"] + [f"一人称 {RJ[t]}" for t in TIERS] + [f"三人称 {RJ[t]}" for t in TIERS] + ["地面と額縁"]
        for i, (n, lb) in enumerate(zip(names, labels)):
            box = (490, 380, 770, 537) if "_tp_" in n else None
            if n.endswith("_world"):
                box = None
            im.paste(load(d, n, box, (TW, TH)), (10 + i * TW, y))
            dr.text((14 + i * TW, y + 2), lb, font=font(12), fill=(255, 255, 255))
        y += TH + 6
        if worn:
            dr.text((10, y + 2), "体に付けた姿 (前・後ろ・斜め・横)。左から コモン 4 枚・レア 4 枚・伝説 4 枚", font=font(14), fill=(200, 205, 215))
            y += 24
            # 12 枚を 1 行 (192 幅) に並べる
            for ti, tier in enumerate(TIERS):
                for vi, (vn, _, _) in enumerate(WORN_VIEWS):
                    n = f"{item}_worn_{vn}_{tier}"
                    xx = 10 + (ti * 4 + vi) * tile
                    im.paste(load(d, n, WORN_BOXES[WORN_PART.get(item, 'body')], (tile - 2, tile - 2)), (xx, y))
            y += tile + 12
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"acc_lp_{titan}.png"
    im.crop((0, 0, W, y + 6)).save(out)
    print("wrote", out)


if __name__ == "__main__":
    for t in sys.argv[1:] or list(TITANS):
        sheet(t)
