# -*- coding: utf-8 -*-
"""土地全体の前と後を同じ場所から撮る台本を書く。
前 = 書き出しに使った世界 (land_<土地>)、後 = 前もって組んだ土地の新しい世界 (land_<土地>_after)。

    python -I tools/gen/world/authored/gen_land_shots.py whale
    SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/land_shots_whale_before.txt -PshotsName=land_whale
    SKYABYSS_AUTHORED=1 SKYABYSS_OPENING_SITES=1 bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/land_shots_whale_after.txt -PshotsName=land_whale_after
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
# 名前, 見る所 x, z, 説明
SPOTS = {
    "whale": [
        ("town", -120, -760, "鯨見の町"),
        ("hamlet_ne", 724, -1112, "骨垣の砦村 (北東)"),
        ("hamlet_nw", -528, -1204, "鐘見の里 (北西)"),
        ("lair", 230, -1000, "岩鯨の居場所のまわり"),
        ("dock_s", -256, -529, "南の渡し場"),
        ("dock_ne", 1042, -834, "北東の渡し場"),
        ("mid_e", 420, -720, "東の丘"),
        ("mid_n", 0, -1300, "北の丘"),
        ("far_ne", 1150, -1250, "北東の外れ"),
        ("far_w", -560, -800, "西の外れ"),
    ],
}


def script(land, world, build):
    out = [f"# {land} の土地の比べ ({'後' if build else '前'})。gen_land_shots.py が書く", f"world {world} continent", "size 1600 900",
           "timeout 2400", "render 12", "guiscale 2", "clouds off", "wait 100", "cmd skyabyss cinematic stop", "cmd time set 4000",
           "cmd gamerule doDaylightCycle false", "cmd gamerule doMobSpawning false", "cmd weather clear"]
    if build:
        out += ["cmd skyabyss dev opening build", "wait 100", "cmd gamemode creative"]
        for name, x, z, _ in SPOTS[land]:
            out += [f"cmd tp @s {x} 200 {z}", "waitchunks 300", "wait 300"]
    out += ["cmd gamemode spectator", "hud off"]
    for name, x, z, _ in SPOTS[land]:
        out += [f"camera {x + 70} 185 {z + 70} 135 30", "waitchunks 1500", "wait 200", "close", "wait 5", f"shot {name}"]
    return "\n".join(out) + "\n"


def main():
    land = sys.argv[1] if len(sys.argv) > 1 else "whale"
    d = ROOT / "tools" / "shots"
    (d / f"land_shots_{land}_before.txt").write_text(script(land, f"land_{land}", False), encoding="utf-8")
    (d / f"land_shots_{land}_after.txt").write_text(script(land, f"land_{land}_after", True), encoding="utf-8")
    print("ok")


def sheet(land="whale"):
    """前と後を左右に並べた絵 → docs/review/authored_<土地>_land.png"""
    from PIL import Image, ImageDraw, ImageFont
    before = ROOT / f"run/shots/land_{land}/shots/land_shots_{land}_before"
    after = ROOT / f"run/shots/land_{land}_after/shots/land_shots_{land}_after"
    W, H = 720, 405
    try:
        f = ImageFont.truetype("C:/Windows/Fonts/meiryo.ttc", 18)
    except OSError:
        f = ImageFont.load_default()
    spots = SPOTS[land]
    img = Image.new("RGB", (W * 2 + 12, 44 + len(spots) * (H + 34)), (24, 24, 28))
    d = ImageDraw.Draw(img)
    d.text((12, 10), "今", font=f, fill=(230, 230, 230))
    d.text((W + 24, 10), "前もって組んだ", font=f, fill=(250, 220, 140))
    y = 44
    for name, _, _, label in spots:
        d.text((12, y + 4), label, font=f, fill=(200, 200, 200))
        for k, base in enumerate((before, after)):
            p = base / f"{name}.png"
            if p.exists():
                img.paste(Image.open(p).convert("RGB").resize((W, H)), (k * (W + 12), y + 30))
        y += H + 34
    out = ROOT / f"docs/review/authored_{land}_land.png"
    img.save(out)
    print(out)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[2] == "sheet":
        sheet(sys.argv[1])
    else:
        main()
