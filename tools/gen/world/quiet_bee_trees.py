# -*- coding: utf-8 -*-
"""バニラの木から蜂の巣を外す (2026-10-31 の試遊「蜂の巣からめっちゃ音が聞こえる」)。
大陸の世界はバニラの生物群系の木をそのまま生やす。木の飾り (decorator) の beehive が蜂入りの巣を付けるが、
この mod はバニラの生き物を世界に入れない (rpg/rules/RpgRules.onJoin) ので、昼のあいだ巣は蜂を出そうとして毎 tick
出る音 (BEEHIVE_EXIT) だけを鳴らし続ける。巣の付く木の configured_feature を、飾りの beehive を除いた形で上書きする。
すでにできた世界の巣は RpgRules が、チャンクを読んだときに取る。
使い方: python -X utf8 tools/gen/world/quiet_bee_trees.py  (先に ./gradlew compileJava などで build/moddev/artifacts を作っておく)
-> src/main/resources/data/minecraft/worldgen/configured_feature/<木>.json
"""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAR = next((ROOT / "build/moddev/artifacts").glob("neoforge-*-client-extra-aka-minecraft-resources.jar"))
DIR = "data/minecraft/worldgen/configured_feature/"
OUT = ROOT / "src/main/resources" / DIR


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(JAR) as z:
        for name in sorted(z.namelist()):
            if not name.startswith(DIR) or not name.endswith(".json"):
                continue
            feature = json.loads(z.read(name))
            decorators = feature.get("config", {}).get("decorators")
            if not decorators or not any(d.get("type") == "minecraft:beehive" for d in decorators):
                continue
            feature["config"]["decorators"] = [d for d in decorators if d.get("type") != "minecraft:beehive"]
            out = OUT / name[len(DIR):]
            with open(out, "w", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(feature, indent=2) + "\n")
            print("quiet", out.name)


if __name__ == "__main__":
    main()
