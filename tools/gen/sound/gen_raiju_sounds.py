"""中ボス「雷獣」の効果音を合成する (独自合成。乱数の種は固定)。

使い方: リポジトリ直下で  python tools/gen/sound/gen_raiju_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/raiju/  (sounds.json の raiju_* が指す)

技ごとに別の音にする (12 の 5.3節の「予告は粒に加えて、技ごとに別の音」を雷獣にも)。
- ambient.ogg      : 喉の低い唸りと、毛先のぱちぱちという静電
- hurt.ogg         : 短い吠え声と、火花の散る音
- death.ogg        : 長く尾を引く遠吠えが雷鳴に変わり、静電が消えていく
- rail_charge.ogg  : 雷渡りの溜め (1.0 秒)。高く上がっていく電気の唸り
- rail.ogg         : 雷渡り。乾いた雷の割れる音と、すぐの轟き
- pounce.ogg       : 飛びかかりの溜め。身を低くする唸りと、尾の火花
- claw.ogg         : 爪の一振り。風を切る音と、ばちっという放電
- roar.ogg         : 雷鳴の咆哮。獣の吠え声に、近い雷鳴が重なる
- stun.ogg         : 接地した柱に落ちる。放電が地へ抜けるじゅっという音と、どさりと落ちる音
- ground.ogg       : 接地の栓を引く。銅の取っ手の重い金属音と、電気が抜けるしゅうという音
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/raiju"
G.rng = np.random.default_rng(20261024)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def highpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec * (f / hz) ** 4 / (1 + (f / hz) ** 4), len(x))


def saw(t, f):
    ph = np.cumsum(np.broadcast_to(f, t.shape)) / SR
    return 2 * (ph % 1.0) - 1


def crackle(t, rate, amp=1.0, start=0.0, end=None):
    """静電のぱちぱち: 短い高い音の粒を散らす"""
    end = t[-1] if end is None else end
    out = np.zeros_like(t)
    n = int(rate * (end - start))
    for _ in range(n):
        t0 = rng.uniform(start, max(start, end - 0.01))
        i0 = int(t0 * SR)
        k = int(rng.uniform(0.001, 0.006) * SR)
        seg = rng.standard_normal(k) * np.exp(-np.arange(k) / (k * 0.25)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + k] += seg[:len(out) - i0]
    return highpass(out, 2500) * amp


def growl(t, f0, amp=1.0, rough=18):
    """獣の唸り: 低い鋸の波に、ざらつきの揺れと口の響き (帯域ノイズで包む)"""
    f = f0 * (1 + 0.04 * np.sin(2 * np.pi * rough * t) + 0.02 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t)))
    x = saw(t, f) * (0.7 + 0.3 * np.sin(2 * np.pi * rough * t))
    body = lowpass(x, 900) + 0.4 * lowpass(highpass(x, 400), 1600)
    return body * amp


def crack(t, t0, amp=1.0):
    """雷の割れる音: 鋭い立ち上がりの広帯域の破裂と、すぐ低く轟く尾"""
    n = len(t)
    k = np.clip(t - t0, 0, None)
    snap = band_noise(n, 800, 12000) * np.exp(-k / 0.03) * (t >= t0)
    rumble = lowpass(band_noise(n, 30, 400), 250) * np.exp(-k / 0.6) * (t >= t0) * 0.9
    return (snap + rumble) * amp


def ambient():
    t = t_axis(2.2)
    x = growl(t, 62, 0.6) * env(t, [(0, 0), (0.3, 0.8), (1.6, 0.9), (2.2, 0)])
    return finish(reverb(x + crackle(t, 45, 0.5), 1.2, 0.25), -6.0)


def hurt():
    t = t_axis(0.6)
    f = np.interp(t, [0, 0.12, 0.6], [180, 260, 120])
    x = growl(t, f, 0.9, rough=30) * env(t, [(0, 0), (0.03, 1), (0.25, 0.6), (0.6, 0)])
    return finish(reverb(x + crackle(t, 120, 0.6, 0, 0.35), 0.8, 0.2), -3.0)


def death():
    t = t_axis(4.0)
    f = np.interp(t, [0, 0.4, 1.6, 2.6, 4.0], [150, 320, 300, 160, 90])
    howl = lowpass(saw(t, f), 1400) * env(t, [(0, 0), (0.3, 0.8), (1.8, 0.9), (2.6, 0.3), (3.5, 0)])
    thunder = crack(t, 2.2, 0.8)
    fade = crackle(t, 80, 0.6, 0, 3.8) * env(t, [(0, 1), (2.0, 1), (3.8, 0)])
    return finish(reverb(howl + thunder + fade, 3.0, 0.45))


def rail_charge():
    t = t_axis(1.1)
    f = np.interp(t, [0, 1.0, 1.1], [90, 640, 700])
    buzz = (saw(t, f) * 0.5 + saw(t, f * 1.5) * 0.25)
    buzz = lowpass(buzz, 3000) * env(t, [(0, 0), (0.1, 0.4), (0.95, 1.0), (1.1, 0)])
    return finish(reverb(buzz + crackle(t, 160, 0.7) * env(t, [(0, 0.2), (1.0, 1)]), 0.8, 0.2), -3.0)


def rail():
    t = t_axis(2.4)
    x = crack(t, 0.0, 1.0) + 0.4 * crack(t, 0.09, 0.6)
    return finish(reverb(x, 2.5, 0.4))


def pounce():
    t = t_axis(0.9)
    x = growl(t, np.interp(t, [0, 0.8, 0.9], [70, 110, 90]), 0.9, rough=24) * env(t, [(0, 0), (0.1, 0.9), (0.8, 1.0), (0.9, 0)])
    return finish(reverb(x + crackle(t, 90, 0.5), 0.8, 0.2), -3.0)


def claw():
    t = t_axis(0.45)
    n = len(t)
    swish = band_noise(n, 600, 5000) * env(t, [(0, 0), (0.08, 1.0), (0.18, 0.2), (0.45, 0)]) * 0.7
    zap = crackle(t, 400, 1.0, 0.12, 0.2) + band_noise(n, 2000, 9000) * np.exp(-np.clip(t - 0.14, 0, None) / 0.02) * (t >= 0.14) * 0.6
    return finish(reverb(swish + zap, 0.5, 0.15), -3.0)


def roar():
    t = t_axis(3.2)
    f = np.interp(t, [0, 0.3, 1.2, 1.6, 3.2], [90, 210, 180, 120, 70])
    voice = growl(t, f, 1.0, rough=22) * env(t, [(0, 0), (0.2, 0.9), (1.3, 1.0), (1.8, 0.4), (2.6, 0)])
    thunder = crack(t, 0.9, 0.9) + 0.6 * crack(t, 1.35, 0.7)
    return finish(reverb(voice + thunder + crackle(t, 120, 0.4), 3.0, 0.4))


def stun():
    t = t_axis(1.8)
    n = len(t)
    fizz = band_noise(n, 2500, 10000) * env(t, [(0, 0), (0.02, 1), (0.5, 0.5), (1.4, 0)]) * 0.6
    drop = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.3, 0.32, 0.6], [400, 60, 50, 40])) / SR)
    drop = drop * np.exp(-np.clip(t - 0.3, 0, None) / 0.12) * (t >= 0.28) * 0.8
    yelp = growl(t, np.interp(t, [0, 0.15, 0.5], [320, 420, 200]), 0.5, rough=40) * env(t, [(0, 0), (0.05, 1), (0.4, 0)])
    return finish(reverb(fizz + drop + yelp + crackle(t, 70, 0.5, 0.3, 1.6), 1.2, 0.3), -2.5)


def ground():
    t = t_axis(1.4)
    n = len(t)
    k = t
    clunk = (np.sin(2 * np.pi * 180 * k) + 0.6 * np.sin(2 * np.pi * 517 * k) + 0.4 * np.sin(2 * np.pi * 1340 * k)) * np.exp(-k / 0.18)
    hiss = band_noise(n, 1500, 9000) * env(t, [(0, 0), (0.08, 0.9), (0.9, 0.3), (1.4, 0)]) * 0.5
    return finish(reverb(clunk * 0.8 + hiss + crackle(t, 60, 0.4, 0.05, 0.6), 1.0, 0.25), -3.0)


SOUNDS = {"ambient": ambient, "hurt": hurt, "death": death, "rail_charge": rail_charge, "rail": rail,
          "pounce": pounce, "claw": claw, "roar": roar, "stun": stun, "ground": ground}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))


if __name__ == "__main__":
    main()
