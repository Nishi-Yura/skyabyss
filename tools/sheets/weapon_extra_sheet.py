"""tools/gen/shots/gen_weapon_extra_shots.py の絵をまとめる。
使い方: python tools/sheets/weapon_extra_sheet.py [撮った絵のフォルダ] [出力の頭 (既定 docs/review/previews/weapon_lp_extra)]
出力: <頭>_left.png (行 = 武器、列 = 一人称/三人称/横向き x 右利き・左利き)、<頭>_foil.png (輝きと、ないとき)、<頭>_skel.png (スケルトンの引き絵の一覧)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/weapon_lp_extra/shots/weapon_lp_extra"
PFX = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews/weapon_lp_extra"
LEFT_IDS = ["whalebone_greatsword", "captain_pike", "thunder_wing_bow", "coral_shield", "raiju_claw", "ember_war_axe", "warden_glaive", "smith_hammer"]
FOIL_IDS = ["whalebone_greatsword", "thunder_wing_bow", "coral_shield"]
BOX = {"fp": (560, 200, 1280, 720), "tp": (440, 170, 840, 640), "tp34": (440, 170, 840, 640)}
CELL = 230


def load(name, box):
    f = SRC / f"{name}.png"
    if not f.exists():
        return None
    im = Image.open(f).convert("RGB")
    if box:
        im = im.crop(box)
    s = CELL / max(im.size)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


def sheet(rows, cols, labels, path):
    W = 110 + CELL * len(cols)
    out = Image.new("RGB", (W, 16 + CELL * len(rows)), (20, 20, 24))
    d = ImageDraw.Draw(out)
    for c, t in enumerate(cols):
        d.text((110 + c * CELL + 4, 2), t, fill=(255, 255, 0))
    for r, row in enumerate(rows):
        d.text((4, 16 + r * CELL + CELL // 2), labels[r], fill=(255, 255, 0))
        for c, im in enumerate(row):
            if im is not None:
                out.paste(im, (110 + c * CELL, 16 + r * CELL))
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path)
    print(path)


def main():
    cols = [f"{v} {h}" for v in ("fp", "tp", "tp34") for h in ("right", "left")]
    rows = [[load(f"left_{w}_{v}_{t}", (0, 200, 720, 720) if (v == "fp" and t == "l") else BOX[v]) for v in ("fp", "tp", "tp34") for t in ("r", "l")] for w in LEFT_IDS]
    sheet(rows, cols, [w[:16] for w in LEFT_IDS], Path(str(PFX) + "_left.png"))
    rows = [[load(f"foil_{w}_fp", BOX["fp"]), load(f"nofoil_{w}_fp", BOX["fp"]), load(f"foil_{w}_tp", BOX["tp"]), load(f"nofoil_{w}_tp", BOX["tp"])] for w in FOIL_IDS]
    rows.append([load("foil_world", (140, 120, 1140, 700)), None, None, None])
    sheet(rows, ["fp foil", "fp plain", "tp foil", "tp plain"], [w[:16] for w in FOIL_IDS] + ["world"], Path(str(PFX) + "_foil.png"))
    ims = [load(f"skel_{n:02d}", None) for n in range(40)]
    if any(ims):
        per = 8
        rws = [ims[i:i + per] for i in range(0, 40, per)]
        sheet(rws, [str(i) for i in range(per)], [f"{i * per}" for i in range(len(rws))], Path(str(PFX) + "_skel.png"))


if __name__ == "__main__":
    main()
