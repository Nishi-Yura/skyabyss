"""余燼の巨神 (第四の巨神) まわりの効果音を合成する (独自合成。乱数の種は固定)。

使い方: python tools/gen/sound/gen_ember_sounds.py  (リポジトリ直下から実行する)
出力先: src/main/resources/assets/skyabyss/sounds/ember/

- rumble.ogg     (約 5 秒): 地の底の深い地鳴り。遠くで岩がすれ合う音が、ゆっくり膨らんで消える。地中を進むときと、浮き沈みのときに鳴る
- breath_in.ogg  (約 8 秒): 眠る巨神が息を吸う。低い気流が巨きな洞へ吸い込まれ、灰のしゅっという音と、かすかな残り火のぱちぱちが上がる。最後は低い緊張を保って止まる
- breath_out.ogg (約 6 秒): 熱い吐息。炉の扉を開けたような熱風がうなり、残り火がはぜ、喉の奥の低い唸りを残して消える
- swallow.ogg    (約 2.5 秒): 口に呑まれる。石のごくりという低い音が下がり、そのあと温かくこもったハムが残る。乗るときと降りるときに鳴る
- wall_scrape.ogg (約 5 秒): 洞窟で壁の向こうから聞こえる、巨きなものが岩を擦る音。こもらせてあり、重い擦れが数回きしむ
- far_beat.ogg   (約 3 秒): 遠くの深い心音。どく、どくん、の二拍を、こもった低音と長い残響で
- world_turn.ogg (約 7 秒): 章の終わりの夜明けの地鳴り。巨きな大地がうねって一度寝返りを打ち、低く沈んで落ち着く
- heat_warn.ogg  (約 1.2 秒): 身体が焦げ始める前触れ。上がっていくじゅっという音と、低い警告の音が二拍
- scorch.ogg     (約 0.6 秒): 焼ける短い一撃。鋭いじゅっという音と小さな芯の衝撃

音色の方針: 熱・灰・石の音だけを使う (水の泡や電気の音は入れない)。音量は tide と同じく finish() で揃える。
"""
from pathlib import Path

import numpy as np

import gen_sky_sounds as G
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg

OUT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/sounds/ember"
G.rng = np.random.default_rng(20261017)
rng = G.rng


def lowpass(x, hz, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** order), len(x))


def highpass(x, hz, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    r = (f / hz) ** order
    return np.fft.irfft(spec * r / (1 + r), len(x))


def sweep(t, ts, fs):
    """折れ線の周波数で鳴らす正弦波 (位相は積分して連続にする)"""
    f = np.interp(t, ts, fs)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def crackle(t, rate, lo=1500, hi=8000):
    """残り火のはぜる音: 数ミリ秒の短い雑音の粒を、ばらばらに散らす"""
    out = np.zeros_like(t)
    for _ in range(int(rate * t[-1])):
        i0 = int(rng.uniform(0, t[-1] - 0.02) * SR)
        m = int(rng.uniform(0.002, 0.012) * SR)
        k = np.arange(m) / SR
        seg = rng.standard_normal(m) * np.exp(-k / 0.003) * rng.uniform(0.2, 1.0)
        out[i0:i0 + m] += seg[:len(out) - i0]
    out = highpass(lowpass(out, hi), lo)
    return out / (np.max(np.abs(out)) + 1e-9)


def fit(x, seconds, peak_db=-2.0):
    """残響の尻尾を含む長さを、名目の秒数に揃えて finish() で音量を揃える"""
    n = int(SR * seconds)
    x = x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])
    return finish(x, peak_db)


def rumble():
    t = t_axis(5.0)
    n = len(t)
    # 地の底の岩 (20〜130 Hz) と、ゆっくり下がる重い低音
    deep = lowpass(band_noise(n, 22, 130), 220)
    sub = sweep(t, [0, 2.5, 5], [34, 30, 27]) * 0.5
    # 岩がすれ合う揺れ。うねるように周期が揺れる
    wobble = 0.5 + 0.5 * np.sin(2 * np.pi * 3.7 * t + 0.8 * np.sin(2 * np.pi * 0.6 * t))
    grind = lowpass(band_noise(n, 60, 400) * wobble, 300) * 0.35
    amp = env(t, [(0, 0), (1.5, 0.8), (2.8, 1.0), (4.2, 0.5), (5.0, 0.0)])
    dry = (deep * 0.8 + sub + grind) * amp
    return fit(reverb(dry, 3.0, 0.35), 5.0, -3.0)


def breath_in():
    t = t_axis(8.0)
    n = len(t)
    # 低い気流が洞へ吸い込まれる。後半ほど明るくなって速さを感じさせる
    air = band_noise(n, 60, 700) * env(t, [(0, 0), (2, 0.4), (6, 0.9), (7.4, 0.7), (8, 0.6)])
    bright = band_noise(n, 500, 1800) * env(t, [(0, 0), (4, 0.05), (7, 0.35), (8, 0.25)]) * 0.35
    # 灰のしゅっという音と、かすかな残り火
    ash = band_noise(n, 2500, 7000) * env(t, [(0, 0), (3, 0.1), (7, 0.35), (8, 0.3)]) * 0.25
    embers = crackle(t, 6) * env(t, [(0, 0), (2, 0.2), (7, 0.6), (8, 0.4)]) * 0.35
    # 最後は低い緊張を保つ (5度の二音がゆっくり揺れる)
    hold = (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.5 * t))
    hold *= (0.7 + 0.3 * np.sin(2 * np.pi * 2.0 * t)) * env(t, [(0, 0), (6, 0), (7.2, 0.4), (8, 0.5)]) * 0.3
    dry = air * 0.8 + bright + ash + embers + hold
    return fit(reverb(dry, 2.5, 0.3), 8.0)


def breath_out():
    t = t_axis(6.0)
    n = len(t)
    # 炉の扉を開けたような熱風: 最初に強く吹き出し、少しずつ細る
    blast = band_noise(n, 180, 3200) * env(t, [(0, 0.05), (0.35, 1.0), (1.6, 0.85), (3.6, 0.45), (5.2, 0.12), (6, 0)])
    roar = band_noise(n, 80, 500) * env(t, [(0, 0), (0.3, 0.8), (2.5, 0.7), (5.5, 0)])
    # 喉の奥の低い唸り (ゆっくり震える)
    throat = sweep(t, [0, 6], [52, 40]) * (0.5 + 0.5 * np.sin(2 * np.pi * 4.5 * t))
    throat *= env(t, [(0, 0), (0.5, 0.4), (3, 0.5), (6, 0)]) * 0.4
    embers = crackle(t, 25) * env(t, [(0, 0.5), (3, 0.8), (6, 0)]) * 0.5
    dry = blast * 0.7 + roar * 0.5 + throat * 0.6 + embers
    return fit(reverb(dry, 1.5, 0.2), 6.0)


def swallow():
    t = t_axis(2.5)
    n = len(t)
    # 石のごくり: 120 Hz から 40 Hz へ落ちる低い音と、短い石の当たる音
    gulp = sweep(t, [0, 0.45], [120, 40]) * env(t, [(0, 0), (0.02, 1.0), (0.3, 0.5), (0.6, 0)])
    stone = lowpass(band_noise(n, 150, 900), 600) * env(t, [(0, 0), (0.01, 1), (0.12, 0.2), (0.25, 0)])
    # 呑まれたあとの、こもった温かいハム
    hum = sum(np.sin(2 * np.pi * 72 * h * t + p) / h ** 1.5 for h, p in [(1, 0), (2, 0.4), (3, 1.1), (4, 0.2)])
    hum = lowpass(hum, 500) * env(t, [(0, 0), (0.5, 0), (0.9, 0.7), (1.8, 0.6), (2.5, 0)])
    dry = gulp * 0.9 + stone * 0.6 + hum * 0.5
    return fit(reverb(dry, 1.2, 0.25), 2.5)


def wall_scrape():
    t = t_axis(5.0)
    n = len(t)
    # 壁の向こうで巨きなものが岩を擦る。数回、重く押し付けるきしみ
    g = np.zeros(n)
    for p in (0.8, 1.5, 2.4, 3.1, 3.9):
        m = (t >= p) & (t < p + 0.35)
        g += np.where(m, np.sin(np.pi * (t - p) / 0.35) ** 2, 0)
    base = env(t, [(0, 0), (0.3, 0.7), (4.6, 0.7), (5, 0)])
    scrape = band_noise(n, 250, 1600) * (0.15 + 0.85 * g) * base * 0.6
    low = band_noise(n, 25, 90) * (0.3 + 0.7 * g) * base * 0.6
    # 壁を通した音なので、高い音はこもらせる
    dry = lowpass(scrape + low, 700)
    return fit(reverb(dry, 2.5, 0.4), 5.0, -3.0)


def far_beat():
    t = t_axis(3.0)

    def thump(t0, amp):
        k = np.clip(t - t0, 0, None)
        # 始めだけ少し音程が落ちる、深い鼓動
        phase = 2 * np.pi * (40 * k + 20 * 0.03 * (1 - np.exp(-k / 0.03)))
        return np.sin(phase) * np.exp(-k / 0.12) * (t >= t0) * amp

    # どく (強い) 、どくん (少し弱い) の二拍
    beats = thump(0.35, 1.0) + thump(0.75, 0.7)
    dry = lowpass(beats, 260)
    return fit(reverb(dry, 3.5, 0.6), 3.0, -3.0)


def world_turn():
    t = t_axis(7.0)
    n = len(t)
    # 大地がうねる低い音。音程が一度沈み、持ち上がって寝返りを打つ
    f = np.interp(t, [0, 2.5, 3.9, 5, 7], [34, 30, 25, 30, 27])
    phase = 2 * np.pi * np.cumsum(f) / SR
    groan = np.sin(phase) * 0.6 + np.sin(2 * phase) * 0.3
    rock = lowpass(band_noise(n, 35, 220), 260) * 0.6
    grind = band_noise(n, 80, 300) * env(t, [(0, 0), (2.5, 0), (3.5, 0.4), (4.6, 0.35), (5.6, 0)]) * 0.3
    amp = env(t, [(0, 0), (1.6, 0.45), (3.2, 0.9), (4.2, 1.0), (5.4, 0.6), (6.5, 0.25), (7, 0)])
    dry = (groan + rock + grind) * amp
    return fit(reverb(dry, 3.0, 0.4), 7.0)


def heat_warn():
    t = t_axis(1.2)
    n = len(t)
    # 上がっていくじゅっという音
    sizzle = band_noise(n, 1800, 8000) * env(t, [(0, 0), (0.6, 0.5), (1.0, 0.9), (1.2, 0)])
    # 低い警告の音が二拍、ゆっくり持ち上がる
    phase = 2 * np.pi * np.cumsum(np.interp(t, [0, 1.2], [192, 216])) / SR
    pulse = 0.5 + 0.5 * np.sin(2 * np.pi * 5.5 * t)
    tone = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * pulse
    tone *= env(t, [(0, 0), (0.1, 0.6), (1.0, 0.6), (1.2, 0)]) * 0.5
    return fit(sizzle * 0.8 + tone, 1.2, -3.0)


def scorch():
    t = t_axis(0.6)
    n = len(t)
    # 鋭いじゅっという音と、芯の小さな衝撃
    sear = band_noise(n, 2000, 9000) * np.exp(-t / 0.14) * np.minimum(1, t / 0.005)
    tick = np.sin(2 * np.pi * 110 * t) * np.exp(-t / 0.03) * 0.5
    return fit(sear + tick, 0.6)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    makers = [
        ("rumble", rumble), ("breath_in", breath_in), ("breath_out", breath_out),
        ("swallow", swallow), ("wall_scrape", wall_scrape), ("far_beat", far_beat),
        ("world_turn", world_turn), ("heat_warn", heat_warn), ("scorch", scorch),
    ]
    for name, make in makers:
        data = make().astype(np.float32)
        write_ogg(OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.2f}s")


if __name__ == "__main__":
    main()
