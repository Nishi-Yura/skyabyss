"""武器 D2 (整え) の手持ちの確認。台本 tools/shots/weapon_d2.txt を作り (python tools/gen/shots/gen_weapon_d2_shots.py)、
撮る: ./gradlew runShots --no-daemon --offline -Pscript=tools/shots/weapon_d2.txt  (絵は run/shots/weapon_d2/shots/weapon_d2/)
まとめ: python tools/gen/shots/gen_weapon_d2_shots.py sheet  -> docs/review/previews/weapon_d2_sheet.png (全部の武器 x 一人称・三人称正面・三人称斜め、伝説)"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../../sheets')]
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_weapon_hold2_shots as H
import weapon_hold2_sheet as S

ROOT = Path(__file__).resolve().parents[3]
ITEMS = H.MELEE + ["abyss_greatsword", "abyss_bell_staff", "abyss_ash_scythe", "coral_shield", "thunder_wing_bow", "abyss_storm_bow"]
OUT = ROOT / "run/shots/weapon_d2/shots/weapon_d2"


def script():
    out = [H.HEAD.replace("weapon_hold2", "weapon_d2"), "weapontier legend"]

    def v(name, persp, hud, yaw=0):
        out.extend([f"perspective {persp}", f"bodyyaw {yaw}", "hud on" if hud else "hud off", "close", "wait 8", "chat clear", f"shot {name}"])
    for w in ITEMS:
        out.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{w}")
        out.append("mainhand right")
        v(f"{w}_fp", "first", True)
        v(f"{w}_tp", "front", False, 0)
        v(f"{w}_tpSide", "front", False, 70)
        if w.endswith("bow"):
            for persp, tag, hud in (("first", "fp", True), ("front", "tp", False)):
                out.extend([f"perspective {persp}", "bodyyaw 0", "hud on" if hud else "hud off", "use", "wait 26", "chat clear", f"shot {w}_{tag}Draw", "stopuse", "wait 25"])
    out.append("quit")
    (ROOT / "tools/shots/weapon_d2.txt").write_text(chr(10).join(out) + chr(10), encoding="utf-8")


def sheet():
    lang = json.loads((ROOT / "src/main/resources/assets/skyabyss/lang/ja_jp.json").read_text(encoding="utf-8"))
    cols = [("一人称", "fp"), ("三人称 正面", "tp"), ("三人称 斜め", "tpSide")]
    W, Hh, LB = S.W, S.H, 150
    sh = Image.new("RGB", (LB + 3 * W + 2 * W, 36 + Hh * len(ITEMS)), (24, 26, 32))
    d = ImageDraw.Draw(sh)
    f, fs = S.font(16), S.font(14)
    for c, (n, _) in enumerate(cols):
        d.text((LB + c * W + 8, 8), n, font=f, fill=(255, 255, 255))
    d.text((LB + 3 * W + 8, 8), "弓の引き (一人称 / 三人称)", font=f, fill=(255, 255, 255))
    for r, it in enumerate(ITEMS):
        y = 36 + r * Hh
        d.text((8, y + 8), lang.get(f"item.skyabyss.{it}", it), font=f, fill=(255, 230, 160))
        d.text((8, y + 32), it, font=fs, fill=(150, 150, 160))
        for c, (_, v) in enumerate(cols):
            p = OUT / f"{it}_{v}.png"
            if p.exists():
                sh.paste(S.crop(Image.open(p).convert("RGB"), v, it), (LB + c * W, y))
        for k, v in enumerate(("fpDraw", "tpDraw")):
            p = OUT / f"{it}_{v}.png"
            if p.exists():
                sh.paste(S.crop(Image.open(p).convert("RGB"), v, it), (LB + (3 + k) * W, y))
    dst = ROOT / "docs/review/previews/weapon_d2_sheet.png"
    sh.save(dst, optimize=True)
    print(dst, sh.size)


if __name__ == "__main__":
    sheet() if "sheet" in sys.argv else script()
