"""中ボス「灰の鍛え手」の音を作る (独自合成。乱数の種は固定。13 の 5.2節、11.2節の担当 E)。

使い方: python tools/gen/sound/gen_ember_smith_sounds.py   (tools/ の中から import するので、リポジトリ直下からでよい)
出力: src/main/resources/assets/skyabyss/sounds/ember_smith/*.ogg
      src/main/resources/assets/skyabyss/sounds.json の ash_smith_* の行 (自分の行だけ書き直す)
      src/main/resources/assets/skyabyss_ember/lang の字幕は手で足してある (subtitles.skyabyss.ash_smith_*)

音 (重い鉄と、ふいごの息。鍛冶場の音を大きくしたもの):
- ash_smith_slam   : 大鎚が床を打つ (低い衝撃、石の割れる粒、鉄の響き)。溜めの始まりにも低く鳴らす
- ash_smith_inhale : ふいごが息を吸う (風を引く長い音、革のきしみ)。亀裂の「しゅう」は高く速くして使う
- ash_smith_blast  : 熱風が噴く (ごうと吹き出す息、火の粉のはぜる音)。噴気・焼き入れ・火花の構えにも使う
- ash_smith_sparks : 鎚が火箸の塊を叩く (金床の澄んだ響き、散る火花の粒)
- ash_smith_rage   : 前掛けを投げ捨てて吼える (低い唸り、ふいごの鳴り)
- ash_smith_hurt   : 鉄の板がきしむ鈍い音
- ash_smith_death  : 膝をつき、ふいごの息が抜けて、鎚が落ちる
"""
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(HERE))
import gen_sky_sounds as G  # noqa: E402
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg  # noqa: E402

import json  # noqa: E402

ROOT = HERE.parent
OUT = ROOT / "src/main/resources/assets/skyabyss/sounds/ember_smith"
SOUNDS_JSON = ROOT / "src/main/resources/assets/skyabyss/sounds.json"
G.rng = np.random.default_rng(20261020)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def grains(t, times, lo, hi, dur=0.012, amp=1.0):
    out = np.zeros_like(t)
    for t0 in times:
        i0 = int(t0 * SR)
        n = int(dur * SR)
        if i0 >= len(out):
            continue
        seg = band_noise(n, lo, hi) * np.exp(-np.arange(n) / (n * 0.25)) * rng.uniform(0.4, 1.0)
        out[i0:i0 + n] += seg[:max(0, len(out) - i0)]
    return out * amp


def sweep(t, f0, f1):
    f = np.interp(t, [0, t[-1]], [f0, f1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def ring(t, hz, decay, amp=1.0):
    return np.sin(2 * np.pi * hz * t) * np.exp(-t * decay) * amp


def slam():
    t = t_axis(1.6)
    thud = sweep(t, 90, 32) * env(t, [(0, 1.0), (0.08, 0.8), (0.6, 0.1), (1.6, 0)])
    crack = grains(t, [0.0, 0.01, 0.03, 0.06, 0.1, 0.16], 500, 6000, dur=0.05, amp=1.1)
    rubble = grains(t, np.sort(rng.uniform(0.05, 0.9, 26)), 1500, 8000, dur=0.01, amp=0.35)
    iron = (ring(t, 187, 5.0, 0.3) + ring(t, 311, 7.0, 0.2) + ring(t, 523, 9.0, 0.12))
    return finish(reverb(thud * 1.1 + crack + rubble + iron, 1.6, 0.35), -1.5)


def inhale():
    t = t_axis(2.2)
    wind = lowpass(band_noise(len(t), 120, 1400), 1200) * env(t, [(0, 0), (0.5, 0.35), (1.6, 0.9), (2.0, 0.5), (2.2, 0)])
    creak = band_noise(len(t), 250, 1200) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * t))) * env(t, [(0, 0), (0.3, 0.25), (1.5, 0.35), (2.2, 0)])
    leather = grains(t, np.sort(rng.uniform(0.0, 2.0, 8)), 200, 1500, dur=0.08, amp=0.3)
    return finish(reverb(wind + creak * 0.5 + leather, 1.2, 0.25), -3.5)


def blast():
    t = t_axis(1.5)
    gust = lowpass(band_noise(len(t), 60, 1800), 1600) * env(t, [(0, 0), (0.04, 1.0), (0.35, 0.7), (1.5, 0)])
    hiss = band_noise(len(t), 2000, 8000) * env(t, [(0, 0), (0.05, 0.5), (0.9, 0.25), (1.5, 0)])
    pops = grains(t, np.sort(rng.uniform(0.05, 1.2, 22)), 1800, 9000, dur=0.006, amp=0.5)
    boom = sweep(t, 70, 40) * env(t, [(0, 0.8), (0.3, 0.3), (0.8, 0)])
    return finish(reverb(gust + hiss * 0.5 + pops + boom * 0.6, 1.3, 0.3), -2.0)


def sparks():
    t = t_axis(1.3)
    hit = ring(t, 880, 8.0, 0.8) + ring(t, 1320, 11.0, 0.4) + ring(t, 2210, 15.0, 0.2)
    click = grains(t, [0.0, 0.004], 1500, 9000, dur=0.01, amp=1.0)
    scatter = grains(t, np.sort(rng.uniform(0.02, 0.9, 40)), 3000, 11000, dur=0.005, amp=0.5)
    return finish(reverb(hit + click + scatter, 1.0, 0.25), -3.0)


def rage():
    t = t_axis(2.6)
    growl = lowpass(band_noise(len(t), 50, 600), 500) * (0.7 + 0.3 * np.sin(2 * np.pi * 11 * t)) * env(t, [(0, 0), (0.2, 1.0), (1.8, 0.9), (2.6, 0)])
    tone = sweep(t, 60, 95) * env(t, [(0, 0), (0.3, 0.8), (1.6, 0.7), (2.6, 0)])
    bell = lowpass(band_noise(len(t), 150, 1500), 1300) * env(t, [(0, 0), (0.5, 0.0), (0.7, 0.6), (1.5, 0.8), (2.2, 0.2), (2.6, 0)])
    return finish(reverb(growl + tone * 0.6 + bell * 0.6, 1.8, 0.35), -1.5)


def hurt():
    t = t_axis(0.7)
    creak = band_noise(len(t), 200, 1800) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 22 * t))) * env(t, [(0, 0), (0.03, 1), (0.3, 0.35), (0.7, 0)])
    clang = ring(t, 240, 7.0, 0.5) + ring(t, 417, 9.0, 0.3)
    return finish(reverb(lowpass(creak, 2200) + clang + grains(t, [0.0], 600, 4000, dur=0.03), 0.8, 0.25), -3.0)


def death():
    t = t_axis(3.2)
    sigh = lowpass(band_noise(len(t), 60, 1200), 900) * env(t, [(0, 0), (0.15, 0.9), (1.2, 0.5), (2.2, 0.05), (3.2, 0)])
    thump = sweep(t, 80, 34) * env(t, [(0, 0), (1.3, 0), (1.35, 1.0), (2.0, 0.1), (3.2, 0)])
    drop = (ring(np.maximum(t - 1.8, 0), 160, 4.0, 0.6) + ring(np.maximum(t - 1.8, 0), 410, 6.0, 0.3)) * (t > 1.8)
    pops = grains(t, np.sort(rng.uniform(0.2, 2.8, 16)), 2000, 8000, dur=0.008, amp=0.3)
    return finish(reverb(sigh + thump * 0.9 + drop + pops, 2.0, 0.4), -1.5)


SOUNDS = {
    "ash_smith_slam": slam,
    "ash_smith_inhale": inhale,
    "ash_smith_blast": blast,
    "ash_smith_sparks": sparks,
    "ash_smith_rage": rage,
    "ash_smith_hurt": hurt,
    "ash_smith_death": death,
}


def write_sounds_json():
    data = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    for name in SOUNDS:
        data[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:ember_smith/{name}"}]}
    SOUNDS_JSON.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = np.nan_to_num(fn()).astype(np.float32)
        assert np.abs(data).max() > 0.05, name
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR))
    write_sounds_json()


if __name__ == "__main__":
    main()
