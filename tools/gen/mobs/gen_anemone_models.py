"""光るイソギンチャク (持ち運ぶ灯の品) のアイテムモデルを作る。python -I tools/gen/mobs/gen_anemone_models.py

灯りの台・苗床・数え石のモデルは消えた (それぞれのブロックは登録されていない) ので作らない。"""
import json
from pathlib import Path

A = Path(__file__).resolve().parents[3] / "src/main/resources/assets/skyabyss"


def w(path, obj):
    p = A / path
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


w("models/item/glow_anemone.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/glow_anemone"}})
print("ok")
