"""嵐の上 (雷翼の背) の効果音を合成する (独自合成。乱数の種は固定)。tools/gen/sound/gen_tide_sounds.py と同じ作り方。

使い方: python tools/gen/sound/gen_storm_sounds.py
出力先: src/main/resources/assets/skyabyss/sounds/storm/  (.ogg)
登録: src/main/resources/assets/skyabyss/sounds.json に storm_* の項目を足す (sound event は skyabyss:<名前>)

- storm_wing_call.ogg    : 遠くの猛禽の鳴き声。低めの一声 (雷翼の鳴き声。遠い、深い)
- storm_thunder_far.ogg  : 遠くの雷。最初の割れる音のあとに低い地鳴り
- storm_gale.ogg         : 昇り風の唸り。5 秒で切れ目なくループする
- storm_back_wind.ogg    : 背中の風の環境音。12 秒で切れ目なくループする (北から南へ流れる風)
- storm_rise_warn.ogg    : 10 秒前の予告 (低い風の唸りが上がっていく。昇り風が来る)
- storm_fall_warn.ogg    : 10 秒前の予告 (遠くの高い鳴き声が下がっていく。凪が来る)
- storm_crosswind.ogg    : 横風の口笛 (1.5 秒で上がる)
- storm_mirror_turn.ogg  : 羽鏡を回す音 (銅の小さなかちりという音と震え)
- storm_receiver_lit.ogg : 受け羽が光った音 (明るい鈴の和音)
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


def rise_warn():
    t = t_axis(10.0)
    n = len(t)
    # 低い唸りが少しずつ上がり、最後の 2 秒で強くなる (昇り風が来る合図)
    growl = band_noise(n, 60, 320) * env(t, [(0, 0.1), (4, 0.4), (8, 0.8), (9.5, 1.0), (10, 0)])
    drone = sweep(t, [(0, 55), (5, 70), (9, 92), (10, 96)]) * env(t, [(0, 0), (1, 0.4), (6, 0.6), (9.5, 0.8), (10, 0)])
    rush = band_noise(n, 500, 2400) * env(t, [(0, 0), (5, 0.05), (9, 0.35), (10, 0)]) * 0.5
    # 残響の尾は 10 秒で切る (予告の長さを 10 秒に合わせる)
    return finish(reverb(growl * 0.9 + drone * 0.35 + rush, 2.5, 0.35)[:SR * 10], -3.0)


def fall_warn():
    t = t_axis(10.0)
    n = len(t)
    # 遠くの高い鳴き声が、10 秒かけて下がっていく (凪が来る合図)。ところどころ揺れる
    cry = sweep(t, [(0, 1250), (3, 1050), (6, 820), (9, 640), (10, 600)])
    cry *= 1 + 0.03 * np.sin(2 * np.pi * 5.0 * t)
    cry *= env(t, [(0, 0), (0.6, 0.9), (4, 0.6), (8.5, 0.5), (10, 0)])
    air = lowpass(band_noise(n, 300, 2000), 1400) * env(t, [(0, 0), (2, 0.25), (9, 0.2), (10, 0)])
    return finish(reverb(lowpass(cry * 0.5 + air * 0.4, 3000), 3.0, 0.45)[:SR * 10], -4.0)


def crosswind():
    t = t_axis(1.5)
    n = len(t)
    whistle = sweep(t, [(0, 620), (1.5, 1900)]) * env(t, [(0, 0), (0.2, 0.8), (1.2, 0.8), (1.5, 0)])
    breath = band_noise(n, 1200, 5000) * env(t, [(0, 0), (0.3, 0.3), (1.5, 0.5), (1.5, 0)]) * 0.4
    return finish(reverb(whistle * 0.6 + breath, 1.2, 0.25), -4.0)


def mirror_turn():
    t = t_axis(0.9)
    n = len(t)
    # 銅の板が触れて鳴る音: 高い非整数倍音が速く減る、その後に小さな震え
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((2140.0, 1.0), (3410.0, 0.6), (5230.0, 0.35), (7790.0, 0.2)))
    ring *= env(t, [(0, 0), (0.005, 1.0), (0.12, 0.45), (0.5, 0.1), (0.9, 0)])
    tick = band_noise(n, 2000, 9000) * env(t, [(0, 0), (0.004, 1.0), (0.03, 0.0), (0.9, 0)]) * 0.6
    shiver = band_noise(n, 800, 3000) * env(t, [(0, 0), (0.1, 0.0), (0.3, 0.25), (0.6, 0.1), (0.9, 0)]) * 0.25
    return finish(reverb(ring * 0.5 + tick + shiver, 0.9, 0.2), -5.0)


def receiver_lit():
    t = t_axis(1.8)
    # 明るい鈴の和音 (完全 5 度と 8 度)。少しずれた二重の音で、きらめきを出す
    base = [1046.5, 1568.0, 2093.0, 2637.0]
    bell = sum(np.sin(2 * np.pi * f * t) * np.exp(-t * (2.2 + 0.4 * i)) for i, f in enumerate(base))
    shim = sum(np.sin(2 * np.pi * f * 1.004 * t) * np.exp(-t * 3.0) for f in base[1:]) * 0.4
    up = sweep(t, [(0, 1046.5), (0.12, 2093.0)]) * env(t, [(0, 0), (0.02, 0.4), (0.12, 0.0), (1.8, 0)]) * 0.3
    return finish(reverb(bell * 0.5 + shim * 0.3 + up, 2.0, 0.3), -4.0)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("storm_wing_call", wing_call), ("storm_thunder_far", thunder_far), ("storm_gale", gale),
                     ("storm_back_wind", back_wind), ("storm_rise_warn", rise_warn), ("storm_fall_warn", fall_warn),
                     ("storm_crosswind", crosswind), ("storm_mirror_turn", mirror_turn),
                     ("storm_receiver_lit", receiver_lit)):
        data = fn().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(name, "%.2f s" % (len(data) / SR), "peak %.3f" % float(np.max(np.abs(data))))


if __name__ == "__main__":
    main()
