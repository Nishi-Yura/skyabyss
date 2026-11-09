"""奥のダンジョン四つの装飾品 (docs/design/55 の 7 章。主を初めて倒した人に一つ)。形は主の形 (2026-11-09 の頁でどれも A) から取った一品一案。
材質の言葉はその土地の巨神と同じ (砦 = 岩鯨、神殿 = 潮殻、氷穴 = 雷翼、熔岩の管 = 余燼)。

    python -X utf8 -I tools/rough/lowpoly/accessories/deep_trinkets.py

  fort_bone_horn        砦の骨の角笛: 野盗王の呼ぶ角笛。反った骨の角に革の巻き、口に鉄の輪
  temple_coral_bell     祭司の珊瑚の鈴: 珊瑚の枝に下がる貝の鈴と真珠
  ice_bear_fang         大熊の氷の牙: 大きな牙を氷の留め具で包み、芯に青い晶
  wyrm_ember_scale      大蛇の熾の鱗: 黒石の鱗の板、継ぎ目から熾が透ける
出力は dungeon_final.py と同じ (accessories_final/<id>/rare_*・textures/item/<id>.png・install_lowpoly_accessories の手順)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dungeon as D  # noqa: E402
import dungeon_final as F  # noqa: E402
from common import *  # noqa: E402,F401,F403


def horn(r):
    # 反った骨の角 (根が太く先が細い)。根に革の巻き二本、口に鉄の輪
    f = D.bent("main", [(-4.8, -2.4, 0), (-2.0, 0.6, 0), (1.6, 1.8, 0), (4.6, 0.2, 0)], [1.6, 1.3, 0.9, 0.35])
    for x in (-3.2, -1.8):
        f += torus("acc", (x, -0.8 + (x + 3.2) * 0.9, 0), 1.5, 0.3, 0.3, n=8, k=3, ax="x")
    f += torus("guard", (-4.8, -2.4, 0), 1.7, 0.35, 0.35, n=8, k=3, ax="x")
    f += D.cord((0.4, 3.2, 0))
    return f


def coral_bell(r):
    # 珊瑚の枝 (三つ叉) と、その下に下がる貝の鈴と真珠
    f = D.bent("main", [(0, 4.6, 0), (0, 2.0, 0), (0.4, -0.4, 0)], [0.6, 0.55, 0.45])
    f += D.bent("main", [(0, 2.6, 0), (-2.2, 3.8, 0), (-3.0, 5.2, 0)], [0.4, 0.3, 0.15])
    f += D.bent("main", [(0, 1.6, 0), (2.4, 2.6, 0), (3.2, 4.2, 0)], [0.4, 0.3, 0.15])
    f += ell("guard", (0.4, -2.4, 0), (2.2, 2.0, 2.0), segs=8, rings=2)
    f += ell("acc", (0.4, -3.6, 0), (2.4, 0.5, 2.2), segs=8, rings=1)
    f += ell("gem", (0.4, -4.4, 0), (0.8, 0.8, 0.8), segs=6, rings=1)
    f += D.cord((0, 5.6, 0))
    return f


def bear_fang(r):
    # 大きく反った牙を、氷の留め具 (二枚の板) が挟む。留め具の芯に青い晶
    f = D.bent("main", [(0, 2.6, 0), (0.6, -0.6, 0), (0.2, -3.6, 0), (-1.0, -6.0, 0)], [1.4, 1.2, 0.8, 0.15])
    f += block("guard", (0, 2.4, 0), (3.6, 2.4, 2.2), taper=(0.85, 0.9))
    f += ell("gem", (0, 2.4, -1.2), (0.9, 0.9, 0.5), segs=6, rings=1)
    for s in (1, -1):
        f += lpkit.spike("w", "acc", (s * 1.8, 3.2, 0), (s * 0.6, 1, 0), 1.8, 0.5, sides=4, rings=1)
    f += D.cord((0, 4.4, 0))
    return f


def wyrm_scale(r):
    # 黒石の鱗の板 (木の葉の形)。真ん中の稜と、継ぎ目から透ける熾
    f = hexdisc("main", (0, 0, 0), 4.0, 1.2, sides=6, phase=0)
    f += block("guard", (0, 0, -0.5), (0.8, 6.6, 0.8), taper=(0.6, 0.6))
    for s in (1, -1):
        f += block("gem", (s * 1.8, -0.4, -0.45), (0.4, 4.2, 0.4))
    f += ell("gem", (0, -0.2, -0.9), (0.9, 0.9, 0.5), segs=6, rings=1)
    f += D.cord((0, 4.6, 0))
    return f


# id, 形, 巨神の材質
ITEMS = [("fort_bone_horn", horn, "whale"), ("temple_coral_bell", coral_bell, "tide"),
         ("ice_bear_fang", bear_fang, "thunder"), ("wyrm_ember_scale", wyrm_scale, "ember")]


def main():
    for iid, fn, titan in ITEMS:
        r = make(fn, titan, "rare")
        save(r, F.FINAL / iid, "rare")
        F.icon(r, titan).save(F.ICON_DIR / f"{iid}.png")
        path = F.MODEL_DIR / f"{iid}.json"
        if not path.exists():
            path.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{iid}"}}) + "\n", encoding="utf-8")
        print(iid, r["tris"], "tris", flush=True)
    sys.path.insert(0, str(ROOT / "tools/install"))
    import install_lowpoly_accessories as I
    I.ITEMS = [iid for iid, _, _ in ITEMS]
    I.TIERS = ["rare"]
    I.TIER_OF.update({iid: "rare" for iid, _, _ in ITEMS})
    I.install_meshes()
    I.install_models()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
