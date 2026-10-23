"""余燼の三つの試練 (灰の道・鍛えの間・火守り) の音 14 個を作る (独自合成。乱数の種は固定。13 の 4 章、11.2節の担当 C1・C2・C3)。

使い方: python tools/gen/sound/gen_ember_trial_sounds.py
出力: src/main/resources/assets/skyabyss/sounds/ember_trial/*.ogg と、sounds.json の自分の行 (名前は EmberIds の SOUND_*)

- ember_ash_scatter  : 灰をまく (さらさら落ちる灰と、息のような短い風)
- ember_crust_crack  : 冷え殻の割れ目 (ぴしっ、ぱきっ)
- ember_crust_sink   : 殻が沈む (重い石が溶岩に落ちる、じゅっ)
- ember_engulf       : 呑まれた (溶岩のぼこぼこと低い唸り)
- ember_bellows      : 鞴 (ふうっと空気が抜けて、炉が息を吹き返す)
- ember_anvil_strike : 金床を打つ (金属の鈍い一打と火花)
- ember_anvil_ting   : 打てる色に入った瞬間 (小さく澄んだ「ちん」)
- ember_bloom_crack  : 塊が割れる (鉄の割れる音と、砕ける破片)
- ember_quench       : 焼き入れ (じゅわっと蒸気が立つ)
- ember_door_kindle  : 扉の文様が灯る (低い共鳴と、火が走る音)
- ember_vent_hiss    : 噴気の予告 (しゅうと高い息)
- ember_vent_burst   : 噴気 (どんと吹き上がる轟き)
- ember_hearth_feed  : 熾をくべる (ぼっと火が上がる)
- ember_hearth_douse : 灰をかけられる (どさっと灰、しゅうと火がしぼむ)
"""
import json
from pathlib import Path

import numpy as np

import gen_ember_mob_sounds as M
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

ROOT = Path(__file__).resolve().parent.parent.parent.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/ember_trial"
SOUNDS_JSON = ROOT / "src/main/resources/assets/skyabyss/sounds.json"
M.G.rng = np.random.default_rng(20261020)
rng = M.G.rng
lowpass, grains, crackle, rumble, roar = M.lowpass, M.grains, M.crackle, M.rumble, M.roar


def crust_crack():
    t = t_axis(0.5)
    snap = grains(t, [0.0, 0.03, 0.09], 1200, 7000, dur=0.02, amp=1.1)
    tick = grains(t, np.sort(rng.uniform(0.0, 0.4, 6)), 3000, 9000, dur=0.006, amp=0.5)
    return finish(reverb(snap + tick, 0.5, 0.2), -4.0)


def vent_burst():
    t = t_axis(1.6)
    boom = roar(t, 40, 500) * env(t, [(0, 0), (0.03, 1.0), (0.4, 0.7), (1.6, 0)])
    blast = band_noise(len(t), 800, 7000) * env(t, [(0, 0), (0.03, 0.8), (1.0, 0.3), (1.6, 0)])
    return finish(reverb(boom + blast * 0.5 + crackle(t, 25, amp=0.3), 1.3, 0.3), -1.5)


SOUNDS = {
    "ember_crust_crack": crust_crack,
    "ember_vent_burst": vent_burst,
}


def write_json():
    """sounds.json の自分の行だけを足す (ほかの行は書き換えない)。"""
    raw = SOUNDS_JSON.read_bytes().decode("utf-8")
    data = json.loads(raw)
    for name in SOUNDS:
        data[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:ember_trial/{name}"}]}
    SOUNDS_JSON.write_bytes((json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    write_json()


if __name__ == "__main__":
    main()
