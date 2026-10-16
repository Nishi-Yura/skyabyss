"""燠の巨獣 (4 体目) の敵 3 種のラフ: 灰被り (ash_thrall) / 火の粉蛾 (ember_moth) / 燠虫 (cinder_roller)。

実行: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/rough/ember_mobs.py
出力: docs/review/ember_roughs/mobs/<subject>/<a|b|c>/ (geo.json, texture.png, glowmask.png, render_*.png)
      灰被りは texture_flared.png / glowmask_flared.png / states.json (状態ごとに隠す骨) と、武器の別モデル weapon_<名>/
      docs/review/ember_roughs/<subject>.png (比較シート)、mobs/<subject>/stats.json (箱の数・重なり・光る画素)
部品: ember_mobs_common.py (彫る・まとめる・姿勢の重なり検査・描画)、ember_mobs_thrall.py / _moth.py / _roller.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ember_mobs_moth  # noqa: E402
import ember_mobs_roller  # noqa: E402
import ember_mobs_thrall  # noqa: E402


def main():
    ember_mobs_thrall.run()
    ember_mobs_moth.run()
    ember_mobs_roller.run()


if __name__ == "__main__":
    main()
