"""巨神「岩鯨」まわりの効果音を合成する。

使い方: python tools/gen_titan_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/titan/

- song.ogg : 岩鯨の歌。低くうねりながら上下する、鯨のような長い声
- wind.ogg : 背中の上で吹き続ける高空の風（12秒で切れ目なくループする）
- chain.ogg: 巨大な鎖が下りる、重い金属のきしみ
"""

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: sounds/titan/*.ogg (採用済みの音)
#   採用版の生成元: 今の src/ が正本
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen_titan_sounds.py は停止中です: 採用済みの素材 (sounds/titan/*.ogg (採用済みの音)) を上書きするため。"
              "採用版の生成元は 今の src/ が正本 です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
from pathlib import Path

import numpy as np

from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss/sounds/titan"


def song():
    t = t_axis(9.0)
    # 鯨の歌: ゆっくり上がって、うねりながら沈む
    f0 = np.interp(t, [0, 2.0, 3.5, 5.5, 7.5, 9.0], [62, 96, 88, 120, 70, 58])
    f0 = np.convolve(f0, np.ones(4000) / 4000, mode="same")
    f0 *= 1 + 0.006 * np.sin(2 * np.pi * 5.2 * t)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    voice = sum(np.sin(h * phase) / (h ** 1.2) for h in range(1, 10))
    # 喉の奥で鳴る、くぐもった倍音のうなり
    growl = np.sin(phase * 0.5) * (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t)) * 0.25
    amp = env(t, [(0, 0), (1.0, 0.8), (3.0, 1.0), (4.0, 0.6), (5.0, 1.0), (7.5, 0.7), (8.8, 0.0), (9.0, 0)])
    dry = (voice * 0.5 + growl) * amp + band_noise(len(t), 80, 600) * amp * 0.05
    return finish(reverb(dry, 5.0, 0.55))


def wind():
    # 突風が来ては去り、間は静かになる (一定の「ザーザー」をやめた版)。24 秒でつながるループ
    seconds = 24.0
    t = t_axis(seconds)
    n = len(t)
    # 突風の山 (中心の秒, 幅の秒, 強さ)。周期 24 秒でつなぐので、距離は円で測る
    gusts = [(3.0, 1.8, 1.0), (8.5, 1.2, 0.6), (13.5, 2.4, 0.9), (20.0, 1.5, 0.7)]
    gust = np.zeros(n)
    for c, w, a in gusts:
        d = np.abs(((t - c) + seconds / 2) % seconds - seconds / 2)
        gust += a * np.exp(-(d / w) ** 2)
    level = 0.05 + 0.95 * np.clip(gust, 0, 1)
    low = band_noise(n, 60, 380) * level
    high = band_noise(n, 600, 2600) * (level ** 2) * 0.25
    # 遠くで風が何かの隙間を鳴らす、細い笛 (突風のときだけ、ごく小さく)
    whistle = np.sin(2 * np.pi * 523.25 * t + 3 * np.sin(2 * np.pi * t / seconds * 2)) * (level ** 3) * 0.02
    return finish(reverb(low * 0.8 + high + whistle, 2.5, 0.3, circular=True), -7.0, trim=False)


def chain():
    t = t_axis(3.0)
    clank = np.zeros_like(t)
    for start in (0.0, 0.32, 0.61, 0.95, 1.22, 1.6, 1.9):
        tt = np.clip(t - start, 0, None)
        hit = sum(np.sin(2 * np.pi * f * tt) for f in (180, 433, 711, 1290)) * np.exp(-tt * 9) * (t >= start)
        clank += hit * (0.6 + 0.4 * np.random.default_rng(int(start * 100)).random())
    groan = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 3], [70, 45])) / SR) * env(t, [(0, 0), (0.3, 0.6), (2.2, 0.5), (3.0, 0)])
    return finish(reverb(clank * 0.35 + groan * 0.5, 2.5, 0.35))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(_sys.argv[1:])
    for name, make in [("song", song), ("wind", wind), ("chain", chain)]:
        if only and name not in only:
            continue
        data = make().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.1f}s")


if __name__ == "__main__":
    main()
