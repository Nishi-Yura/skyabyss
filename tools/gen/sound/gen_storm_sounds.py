"""嵐の上 (雷翼の背) の効果音を合成する (独自合成。乱数の種は固定)。tools/gen/sound/gen_tide_sounds.py と同じ作り方。

使い方: python tools/gen/sound/gen_storm_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/storm/  (.ogg)
登録: src/main/resources/assets/skyabyss/sounds.json に storm_* の項目を足す (sound event は skyabyss:<名前>)

- storm_wing_call.ogg    : 遠くの猛禽の鳴き声。低めの一声 (雷翼の鳴き声。遠い、深い)
- storm_thunder_far.ogg  : 遠くの雷。最初の割れる音のあとに低い地鳴り
- storm_gale.ogg         : 昇り風の唸り。5 秒で切れ目なくループする
- storm_back_wind.ogg    : 背中の風の環境音。12 秒で切れ目なくループする (北から南へ流れる風)
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/storm"
G.rng = np.random.default_rng(20261017)
rng = G.rng


def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def sweep(t, f_points):
    """周波数の折れ線 (時刻, Hz) を積分した正弦波"""
    ts, fs = zip(*f_points)
    f = np.interp(t, ts, fs)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def wing_call():
    t = t_axis(2.6)
    # 一声目は高く裂け、2 声目はそれより低く短い。遠くなので、こもらせて残響を足す
    cry = sweep(t, [(0, 380), (0.25, 620), (0.6, 560), (1.0, 640), (1.4, 420), (2.6, 300)])
    cry = cry + 0.5 * sweep(t, [(0, 760), (0.25, 1240), (0.6, 1120), (1.0, 1280), (1.4, 840), (2.6, 600)])
    vib = 1 + 0.02 * np.sin(2 * np.pi * 6.5 * t)
    cry = cry * vib
    rasp = band_noise(len(t), 900, 3000) * 0.18
    amp = env(t, [(0, 0), (0.12, 1.0), (0.5, 0.35), (0.65, 0.9), (1.2, 0.6), (1.8, 0.2), (2.6, 0)])
    dry = (cry * 0.55 + rasp) * amp
    return finish(reverb(lowpass(dry, 2600), 3.5, 0.45), -4.0)


def thunder_far():
    t = t_axis(6.0)
    n = len(t)
    crack = band_noise(n, 200, 2400) * env(t, [(0, 0), (0.03, 1.0), (0.35, 0.2), (0.6, 0)])
    rumble = band_noise(n, 25, 140) * env(t, [(0, 0), (0.3, 0.7), (1.5, 1.0), (3.5, 0.6), (5.2, 0.2), (6.0, 0)])
    rumble = lowpass(rumble, 220)
    roll = band_noise(n, 60, 380) * env(t, [(0, 0), (0.6, 0.3), (2.0, 0.55), (4.0, 0.25), (6.0, 0)]) * 0.5
    return finish(reverb(lowpass(crack * 0.5, 1800) + rumble * 0.9 + lowpass(roll, 600), 5.0, 0.55), -3.0)


def gale():
    seconds = 5.0
    t = t_axis(seconds)
    n = len(t)
    # 周期 5 秒の包絡線 (つなぎ目が合う)。帯域のノイズは FFT で作るので周期的につながる
    swell = 0.5 + 0.5 * np.sin(2 * np.pi * t / seconds - np.pi / 2)
    body = band_noise(n, 180, 1600) * (0.45 + 0.55 * swell ** 2)
    whistle = band_noise(n, 1200, 3200) * (swell ** 6) * 0.25
    low = band_noise(n, 50, 150) * (0.5 + 0.5 * np.cos(2 * np.pi * t / seconds)) * 0.45
    return finish(reverb(body + whistle + low, 1.8, 0.3, circular=True), -6.0, trim=False)


def back_wind():
    seconds = 12.0
    t = t_axis(seconds)
    n = len(t)
    # 北から南へ流れる風: 4 秒ごとの緩いうねり (周期は 12 秒に合わせる)。高い所の細かい風切りを少し
    gust = 0.5 + 0.5 * np.sin(2 * np.pi * t / 4.0 - np.pi / 2)
    gust2 = 0.5 + 0.5 * np.sin(2 * np.pi * t / 6.0 + 1.0)
    air = band_noise(n, 150, 1800) * (0.35 + 0.65 * gust)
    thin = band_noise(n, 2200, 6000) * (gust2 ** 3) * 0.2
    low = band_noise(n, 40, 140) * (0.4 + 0.6 * gust2) * 0.4
    return finish(reverb(air + thin + low, 2.2, 0.35, circular=True), -7.0, trim=False)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("storm_wing_call", wing_call), ("storm_thunder_far", thunder_far), ("storm_gale", gale),
                     ("storm_back_wind", back_wind)):
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR), "peak %.3f" % float(np.max(np.abs(data))))


if __name__ == "__main__":
    main()
