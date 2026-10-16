"""雷翼の羽衣 (thunder_wing の防具 4 部位) の採用案をゲームに入れる。

使い方 (リポジトリ直下で):
  python tools/install_storm_armor.py                  採用案 (既定は推奨案) を入れる
  python tools/install_storm_armor.py --option a       A 案を入れる
  python tools/install_storm_armor.py --from-sources   tools/sources/storm_armor/ から入れ直す (ラフを作り直さない)

形の元: tools/rough/storm_armor.py が作る docs/review/storm_gear_roughs/armor/<案>/。
        入れたあと、その素材を tools/sources/storm_armor/ にも写す (あとから入れ直せるように)。

出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/armor/thunder_wing_armor.geo.json                 GeckoLib の防具モデル (骨は stone_whale_armor と同じ 8 本)
  textures/armor/thunder_wing_armor.png                 本体テクスチャ
  textures/armor/thunder_wing_armor_glowmask.png        発光マスク (画素が 1 つ以上ある)
  animations/armor/thunder_wing_armor.animation.json    空のアニメーション (防具は静止)
  textures/models/armor/thunder_wing_layer_{1,2}.png    バニラの防具の層 (64x32)
  textures/item/thunder_wing_{helmet,chestplate,leggings,boots}.png   32x32 のアイコン
  models/item/thunder_wing_{helmet,chestplate,leggings,boots}.json    item/generated
"""
import argparse
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
from storm_armor import ARMOR_OUT, OPTIONS, PIECES, RECOMMEND  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
SOURCES = ROOT / "tools/sources/storm_armor"
NAME = "thunder_wing_armor"


def files_of(src: Path):
    """入れる素材の (元, 先) の組"""
    yield src / f"{NAME}.geo.json", RES / "geo/armor" / f"{NAME}.geo.json"
    yield src / f"{NAME}.png", RES / "textures/armor" / f"{NAME}.png"
    yield src / f"{NAME}_glowmask.png", RES / "textures/armor" / f"{NAME}_glowmask.png"
    for n in (1, 2):
        yield src / "layers" / f"thunder_wing_layer_{n}.png", RES / "textures/models/armor" / f"thunder_wing_layer_{n}.png"
    for p in PIECES:
        yield src / "icons" / f"thunder_wing_{p}.png", RES / "textures/item" / f"thunder_wing_{p}.png"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--option", choices=sorted(OPTIONS), default=RECOMMEND, help="入れる案 (既定は推奨案)")
    ap.add_argument("--from-sources", action="store_true", help="tools/sources/storm_armor/ から入れ直す")
    args = ap.parse_args()

    if args.from_sources:
        src = SOURCES
        opt = (SOURCES / "option.txt").read_text(encoding="utf-8").strip()
    else:
        opt = args.option
        src = ARMOR_OUT / opt
    if not (src / f"{NAME}.geo.json").exists():
        sys.exit(f"素材が見つからない: {src} (先に python tools/rough/storm_armor.py を実行)")

    copies = list(files_of(src))
    for s, d in copies:
        d.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(s, d)
        print("書いた:", d.relative_to(ROOT))

    anim = RES / "animations/armor" / f"{NAME}.animation.json"
    anim.parent.mkdir(parents=True, exist_ok=True)
    anim.write_text(json.dumps({"format_version": "1.8.0", "animations": {}}, indent=2) + "\n", encoding="utf-8")
    print("書いた:", anim.relative_to(ROOT))

    for p in PIECES:
        m = RES / "models/item" / f"thunder_wing_{p}.json"
        m.parent.mkdir(parents=True, exist_ok=True)
        m.write_text(json.dumps({"parent": "minecraft:item/generated",
                                 "textures": {"layer0": f"skyabyss:item/thunder_wing_{p}"}}, indent=2) + "\n",
                     encoding="utf-8")
        print("書いた:", m.relative_to(ROOT))

    # 採用した素材を tools/sources/storm_armor/ に写す (入れ直し用)
    if not args.from_sources:
        for s, _ in copies:
            target = SOURCES / s.relative_to(src)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(s, target)
        (SOURCES / "option.txt").write_text(opt + "\n", encoding="utf-8")
        print("写した:", SOURCES.relative_to(ROOT), f"(案 {opt})")
    print(f"採用案: {OPTIONS[opt]['title']}")


if __name__ == "__main__":
    main()
