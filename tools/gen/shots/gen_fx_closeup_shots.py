# -*- coding: utf-8 -*-
"""攻撃の粒 (docs/design/59) の見比べ: 属性ごとに 破片・光の粒・煙 を目の前に出して撮る台本を書く。

  python -I tools/gen/shots/gen_fx_closeup_shots.py
  python -I tools/gen/ui/gen_fx_particles.py --style a
  ./gradlew runShots --no-daemon -Pscript=tools/shots/fx_closeup.txt -PshotsName=fx_closeup_a
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ELEMENTS = ["stone", "water", "storm", "ember", "abyss", "moon", "paper"]
L = []
a = L.append
a("# 攻撃の粒の見比べ (目の前に出す)。生成: tools/gen/shots/gen_fx_closeup_shots.py")
a("world fx_closeup flat")
a("size 1280 720")
a("timeout 900")
a("render 6")
a("clouds off")
a("cmd time set noon")
a("cmd gamerule doDaylightCycle false")
a("cmd gamemode creative Dev")
a("cmd fill -8 -61 -8 8 -61 8 minecraft:polished_deepslate")
a("camera minecraft:overworld 0.5 -60 4.5 180 8")
a("waitchunks")
a("wait 20")
a("cmd skyabyss cinematic stop")
a("wait 10")
a("camera minecraft:overworld 0.5 -60 4.5 180 8")
a("hud off")
a("wait 10")
for el in ELEMENTS:
    # 左 = 破片、真ん中 = 光の粒、右 = 煙
    a("cmd particle skyabyss:fx_%s_shard -1.3 -58.6 0.5 0.35 0.35 0.2 0.05 18 force" % el)
    a("cmd particle skyabyss:fx_%s_glow 0.5 -58.4 0.5 0.35 0.35 0.2 0.01 14 force" % el)
    a("cmd particle skyabyss:fx_%s_smoke 2.3 -58.6 0.5 0.3 0.3 0.2 0.005 10 force" % el)
    a("wait 3")
    a("shot %s" % el)
    a("wait 40")
a("quit")
(ROOT / "tools/shots/fx_closeup.txt").write_text("\n".join(L) + "\n", encoding="utf-8")
print("ok")
