"""潮殻の背の本番 (島・三つの試練・潮招き) の素材を作る (独自合成。乱数の種は固定)。

使い方: リポジトリ直下で  python tools/gen/mobs/gen_tide_back_assets.py
出力:
  src/main/resources/assets/skyabyss/textures/item/tide_pearl.png            潮の真珠 (32x32)
  src/main/resources/assets/skyabyss/textures/block/tide_chime_stone{,_active}.png  潮鳴り石 (32x32)
  src/main/resources/assets/skyabyss/{blockstates,models/block,models/item}/...     潮鳴り石・真珠のモデル
  src/main/resources/assets/skyabyss/sounds/tide_back/*.ogg                  効果音 20 個
  src/main/resources/assets/skyabyss/sounds.json に、足りない項目だけ足す

音:
- horn_low / horn_high : 満ちる 10 秒前の低い潮鳴り (法螺貝) / 引く 10 秒前の高い音
- chime                : 潮鳴り石が鳴る (水の底で響く鐘)
- pearl_take / pearl_return : 真珠を取り出す / 潮に引かれて大貝へ戻る
- plug_flow / lock     : 潮見の間の栓の水が流れる / 溜めの戸が開け閉めする
- reef_nest_open / close / break : 群れの巣の口が開く・閉じる・崩れる
- tide_beckoner_*      : 潮招き (ambient・hurt・death・raise・slam・beckon・breath・burrow・emerge・roar)
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../sound')]
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_sky_sounds as G  # noqa: E402
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
SOUNDS = ASSETS / "sounds/tide_back"
G.rng = np.random.default_rng(20261017)
rng = G.rng


# ---------------------------------------------------------------- 絵
def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def pearl():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    cx, cy, r = 15.5, 16.5, 10.5
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d > r:
                continue
            # 光は左上から。縁は青緑、真ん中は乳白、右下に虹の帯
            lx, ly = (x - cx) / r, (y - cy) / r
            shade = 0.55 + 0.45 * max(0.0, -0.55 * lx - 0.65 * ly + 0.5 * (1 - d / r))
            base = np.array([226, 232, 222], float) * shade
            rim = max(0.0, (d / r) - 0.7) / 0.3
            base = base * (1 - rim * 0.55) + np.array([70, 150, 150]) * rim * 0.55
            iris = max(0.0, 0.35 - abs((lx + ly) * 0.7 - 0.45))
            base += np.array([60, -10, 70]) * iris
            q = 6
            c = tuple(int(max(0, min(255, round(v / q) * q))) for v in base)
            px[x, y] = c + (255,)
    # 光の点と縁の暗い線
    for (x, y) in ((11, 11), (12, 11), (11, 12), (12, 12), (13, 10)):
        px[x, y] = (250, 252, 246, 255)
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r - 0.9 < d <= r:
                px[x, y] = (38, 82, 88, 255)
    return img


def chime_stone(active):
    img = Image.new("RGBA", (32, 32))
    px = img.load()
    stone = [hexc(c) for c in ("#3C5A5C", "#46676A", "#527477", "#5F8285")]
    for y in range(32):
        for x in range(32):
            h = (x * 7 + y * 13 + (x // 4) * 5 + (y // 3) * 3) % 11
            px[x, y] = stone[min(3, h // 3)]
    # 縁の面取り
    for i in range(32):
        for (x, y) in ((i, 0), (0, i)):
            px[x, y] = hexc("#7FA2A2")
        for (x, y) in ((i, 31), (31, i)):
            px[x, y] = hexc("#2A4244")
    # 螺旋の溝 (巻貝の線)。鳴ると光る
    groove = hexc("#BFFFF2") if active else hexc("#22383A")
    glow = hexc("#6FE6D8") if active else hexc("#2E4A4C")
    cx, cy = 15.5, 15.5
    for k in range(900):
        a = k / 900 * 3.1 * 2 * np.pi
        rr = 1.5 + a * 1.55
        x = int(round(cx + np.cos(a) * rr))
        y = int(round(cy + np.sin(a) * rr))
        if 2 <= x <= 29 and 2 <= y <= 29:
            px[x, y] = groove
            for (dx, dy) in ((1, 0), (0, 1)):
                if px[x + dx, y + dy] not in (groove,):
                    px[x + dx, y + dy] = glow
    return img


def write_models():
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "blockstates/tide_chime_stone.json").write_text(json.dumps({"variants": {
        "active=false": {"model": "skyabyss:block/tide_chime_stone"},
        "active=true": {"model": "skyabyss:block/tide_chime_stone_active"}}}, indent=2) + "\n", encoding="utf-8")
    for name in ("tide_chime_stone", "tide_chime_stone_active"):
        (ASSETS / f"models/block/{name}.json").write_text(json.dumps(
            {"parent": "minecraft:block/cube_all", "textures": {"all": f"skyabyss:block/{name}"}}, indent=2) + "\n", encoding="utf-8")
    (ASSETS / "models/item/tide_pearl.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/tide_pearl"}}, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- 音
def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def tone(t, f0, harmonics=8, odd=True):
    phase = 2 * np.pi * np.cumsum(f0 if np.ndim(f0) else np.full_like(t, f0)) / SR
    return sum(np.sin(h * phase) / (h ** (0.9 if (h % 2 or not odd) else 1.7)) for h in range(1, harmonics + 1))


def bubbles(t, rate, lo, hi, dur=0.05, amp=1.0):
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        t0 = rng.uniform(0, max(0.001, t[-1] - dur))
        f0 = rng.uniform(lo, hi)
        i0 = int(t0 * SR)
        k = np.arange(int(dur * SR)) / SR
        f = f0 * (1 + 2.5 * k / dur)
        seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / (dur * 0.35)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0]
    return out * amp


def horn(pitch_from, pitch_to, seconds):
    t = t_axis(seconds)
    f0 = np.interp(t, [0, seconds * 0.3, seconds * 0.8, seconds], [pitch_from * 0.94, pitch_from, pitch_to, pitch_to * 0.97])
    f0 *= 1 + 0.006 * np.sin(2 * np.pi * 4.5 * t)
    body = lowpass(tone(t, f0, 12), 1200 if pitch_from > 150 else 700)
    breath = band_noise(len(t), 200, 1600) * 0.12
    amp = env(t, [(0, 0), (0.35, 0.9), (seconds * 0.6, 1.0), (seconds - 0.6, 0.6), (seconds, 0)])
    return finish(reverb((body * 0.6 + breath) * amp, 4.0, 0.45))


def horn_low():
    return horn(73, 82, 4.5)


def horn_high():
    return horn(196, 165, 3.5)


def chime():
    t = t_axis(5.0)
    out = np.zeros_like(t)
    for f, a, d in ((330, 1.0, 2.2), (495, 0.6, 1.6), (742, 0.45, 1.2), (1112, 0.25, 0.8), (165, 0.5, 3.0)):
        out += np.sin(2 * np.pi * f * t) * np.exp(-t / d) * a
    out = lowpass(out, 3000)
    out += bubbles(t, 18, 500, 1500, amp=0.1) * env(t, [(0, 0), (0.3, 1), (2.5, 0.2), (5, 0)])
    return finish(reverb(out, 4.5, 0.5))


def pearl_take():
    t = t_axis(1.6)
    sweep = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.5, 1.6], [600, 1400, 1500])) / SR) * env(t, [(0, 0), (0.05, 0.6), (0.6, 0.3), (1.6, 0)])
    bell = sum(np.sin(2 * np.pi * f * t) * np.exp(-t / 0.6) for f in (1568, 2349)) * 0.3
    shell = band_noise(len(t), 300, 2500) * env(t, [(0, 0), (0.02, 0.8), (0.15, 0.1), (1.6, 0)]) * 0.4
    return finish(reverb(sweep * 0.4 + bell + shell, 2.0, 0.4))


def pearl_return():
    t = t_axis(2.4)
    whoosh = lowpass(band_noise(len(t), 100, 1800), 900) * env(t, [(0, 0), (0.4, 1.0), (1.4, 0.5), (2.4, 0)])
    down = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 2.4], [900, 240])) / SR) * env(t, [(0, 0), (0.2, 0.4), (2.0, 0.1), (2.4, 0)])
    return finish(reverb(whoosh * 0.7 + down * 0.4 + bubbles(t, 40, 300, 1200, amp=0.3), 2.5, 0.45))


def plug_flow():
    t = t_axis(1.8)
    rush = lowpass(band_noise(len(t), 150, 3000), 1500) * env(t, [(0, 0), (0.1, 0.8), (1.2, 0.6), (1.8, 0)])
    gurgle = bubbles(t, 60, 200, 700, dur=0.08, amp=0.5)
    clunk = np.sin(2 * np.pi * 90 * t) * np.exp(-t / 0.08)
    return finish(reverb(rush * 0.6 + gurgle + clunk * 0.6, 1.5, 0.3), -4.0)


def lock():
    t = t_axis(1.2)
    grind = band_noise(len(t), 80, 900) * env(t, [(0, 0), (0.05, 1), (0.6, 0.7), (0.8, 0), (1.2, 0)])
    thud = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.2], [70, 45])) / SR) * env(t, [(0, 0), (0.75, 0), (0.8, 1), (1.2, 0)])
    return finish(reverb(grind * 0.5 + thud, 1.2, 0.3), -3.0)


def nest_open():
    t = t_axis(1.4)
    crack = band_noise(len(t), 600, 4000) * env(t, [(0, 0), (0.02, 1), (0.3, 0.2), (1.4, 0)])
    return finish(reverb(crack * 0.6 + bubbles(t, 70, 200, 900, amp=0.5), 1.2, 0.3), -4.0)


def nest_close():
    t = t_axis(1.0)
    clack = sum(band_noise(len(t), 800, 3500) * env(t, [(0, 0), (s, 0), (s + 0.01, 1), (s + 0.08, 0), (1.0, 0)]) for s in (0.05, 0.18, 0.3))
    return finish(reverb(clack * 0.6, 0.8, 0.25), -4.0)


def nest_break():
    t = t_axis(2.2)
    crunch = band_noise(len(t), 200, 5000) * env(t, [(0, 0), (0.02, 1), (0.5, 0.5), (1.4, 0.1), (2.2, 0)])
    low = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 2.2], [120, 50])) / SR) * env(t, [(0, 0), (0.05, 1), (1.0, 0.2), (2.2, 0)])
    return finish(reverb(crunch * 0.7 + low * 0.6 + bubbles(t, 50, 300, 1200, amp=0.3), 2.0, 0.35))


def crab_click(t, rate, amp):
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        i0 = int(rng.uniform(0, t[-1] - 0.03) * SR)
        k = np.arange(int(0.025 * SR)) / SR
        seg = band_noise(len(k), 1500, 6000) * np.exp(-k / 0.004)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0] * rng.uniform(0.3, 1.0)
    return out * amp


def beckoner_ambient():
    t = t_axis(2.4)
    grumble = lowpass(band_noise(len(t), 40, 400), 250) * env(t, [(0, 0), (0.5, 0.8), (1.8, 0.6), (2.4, 0)])
    return finish(reverb(grumble + crab_click(t, 12, 0.25) + bubbles(t, 10, 200, 600, amp=0.2), 2.0, 0.35), -4.0)


def beckoner_hurt():
    t = t_axis(0.8)
    crack = band_noise(len(t), 400, 5000) * env(t, [(0, 0), (0.01, 1), (0.2, 0.2), (0.8, 0)])
    groan = lowpass(band_noise(len(t), 60, 500), 300) * env(t, [(0, 0), (0.05, 0.8), (0.6, 0.2), (0.8, 0)])
    return finish(reverb(crack * 0.6 + groan, 0.8, 0.3), -3.0)


def beckoner_death():
    t = t_axis(4.0)
    groan = lowpass(band_noise(len(t), 40, 600), 350) * env(t, [(0, 0), (0.3, 1), (2.5, 0.6), (4, 0)])
    down = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 4], [90, 35])) / SR) * env(t, [(0, 0), (0.2, 0.7), (3.5, 0.2), (4, 0)])
    crunch = band_noise(len(t), 300, 4000) * env(t, [(0, 0), (2.4, 0), (2.45, 1), (3.0, 0.2), (4, 0)])
    return finish(reverb(groan + down * 0.6 + crunch * 0.5 + bubbles(t, 25, 200, 800, amp=0.3), 3.5, 0.45))


def beckoner_raise():
    t = t_axis(1.0)
    creak = lowpass(band_noise(len(t), 100, 1500), 700) * env(t, [(0, 0), (0.1, 0.6), (0.9, 1.0), (1.0, 0)])
    rise = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.0], [60, 140])) / SR) * env(t, [(0, 0), (0.2, 0.5), (1.0, 0.8)])
    return finish(reverb(creak + rise * 0.5 + crab_click(t, 20, 0.2), 1.0, 0.3), -3.0)


def beckoner_slam():
    t = t_axis(2.2)
    boom = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.4, 2.2], [80, 38, 30])) / SR) * env(t, [(0, 0), (0.01, 1), (0.8, 0.3), (2.2, 0)])
    sand = band_noise(len(t), 200, 4000) * env(t, [(0, 0), (0.01, 1), (0.4, 0.3), (1.5, 0), (2.2, 0)])
    splash = lowpass(band_noise(len(t), 300, 5000), 3000) * env(t, [(0, 0), (0.05, 0.6), (0.9, 0.2), (2.2, 0)])
    return finish(reverb(boom + sand * 0.5 + splash * 0.4, 2.0, 0.35))


def beckoner_beckon():
    t = t_axis(3.0)
    horn_part = lowpass(tone(t, np.interp(t, [0, 1.5, 3.0], [55, 62, 52]), 10), 500) * env(t, [(0, 0), (0.4, 0.8), (2.2, 0.9), (3, 0)])
    swell = lowpass(band_noise(len(t), 60, 1200), 600) * env(t, [(0, 0), (1.5, 0.3), (2.6, 1.0), (3, 0)])
    return finish(reverb(horn_part * 0.6 + swell * 0.6 + crab_click(t, 8, 0.2), 3.0, 0.45))


def beckoner_breath():
    t = t_axis(1.2)
    inhale = band_noise(len(t), 300, 2000) * env(t, [(0, 0), (0.6, 0.6), (0.7, 0), (1.2, 0)])
    puff = bubbles(t, 80, 300, 1200, dur=0.06, amp=0.6) * env(t, [(0, 0), (0.7, 0), (0.75, 1), (1.2, 0)])
    return finish(reverb(inhale * 0.4 + puff, 1.0, 0.3), -3.0)


def beckoner_burrow():
    t = t_axis(1.8)
    dig = band_noise(len(t), 150, 3000) * (0.6 + 0.4 * np.sin(2 * np.pi * 7 * t)) * env(t, [(0, 0), (0.1, 1), (1.4, 0.7), (1.8, 0)])
    low = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.8], [70, 40])) / SR) * env(t, [(0, 0), (0.2, 0.5), (1.8, 0)])
    return finish(reverb(lowpass(dig, 1800) + low * 0.6, 1.5, 0.3), -3.0)


def beckoner_emerge():
    t = t_axis(1.8)
    burst = band_noise(len(t), 100, 5000) * env(t, [(0, 0), (0.02, 1), (0.5, 0.4), (1.8, 0)])
    boom = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 1.8], [90, 40])) / SR) * env(t, [(0, 0), (0.02, 1), (1.0, 0.2), (1.8, 0)])
    return finish(reverb(burst * 0.6 + boom + crab_click(t, 18, 0.3), 2.0, 0.35))


def beckoner_roar():
    t = t_axis(2.6)
    roar = lowpass(band_noise(len(t), 50, 900) * (1 + 0.5 * np.sin(2 * np.pi * 9 * t)), 500) * env(t, [(0, 0), (0.2, 1), (2.0, 0.8), (2.6, 0)])
    stomps = sum(np.sin(2 * np.pi * 45 * t) * env(t, [(0, 0), (s, 0), (s + 0.01, 1), (s + 0.35, 0), (2.6, 0)]) for s in (0.3, 0.9, 1.5))
    return finish(reverb(roar + stomps * 0.8 + crab_click(t, 25, 0.3), 2.5, 0.4))


SOUND_LIST = {
    "tide_horn_low": horn_low, "tide_horn_high": horn_high, "tide_chime": chime,
    "tide_pearl_take": pearl_take, "tide_pearl_return": pearl_return, "tide_plug_flow": plug_flow, "tide_lock": lock,
    "reef_nest_open": nest_open, "reef_nest_close": nest_close, "reef_nest_break": nest_break,
    "tide_beckoner_ambient": beckoner_ambient, "tide_beckoner_hurt": beckoner_hurt, "tide_beckoner_death": beckoner_death,
    "tide_beckoner_raise": beckoner_raise, "tide_beckoner_slam": beckoner_slam, "tide_beckoner_beckon": beckoner_beckon,
    "tide_beckoner_breath": beckoner_breath, "tide_beckoner_burrow": beckoner_burrow, "tide_beckoner_emerge": beckoner_emerge,
    "tide_beckoner_roar": beckoner_roar,
}


def main():
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    pearl().save(ASSETS / "textures/item/tide_pearl.png")
    chime_stone(False).save(ASSETS / "textures/block/tide_chime_stone.png")
    chime_stone(True).save(ASSETS / "textures/block/tide_chime_stone_active.png")
    write_models()
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUND_LIST.items():
        data = fn().astype(np.float32)
        write_ogg(SOUNDS / f"{name}.ogg", data)
        print(name, "%.1f s" % (len(data) / SR))
    path = ASSETS / "sounds.json"
    text = path.read_text(encoding="utf-8")
    sounds = json.loads(text)
    added = 0
    for name in SOUND_LIST:
        if name not in sounds:
            sounds[name] = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:tide_back/{name}"}]}
            added += 1
    if added:
        # 今のファイルの書き方を崩さないよう、最後の } の前に一行ずつ足す
        body = text.rstrip()
        assert body.endswith("}")
        body = body[:-1].rstrip()
        extra = []
        for name in SOUND_LIST:
            if name not in json.loads(text):
                v = {"subtitle": f"subtitles.skyabyss.{name}", "sounds": [{"name": f"skyabyss:tide_back/{name}"}]}
                extra.append("  " + json.dumps(name) + ": " + json.dumps(v, ensure_ascii=False))
        nl = chr(10)
        path.write_text(body + "," + nl + ("," + nl).join(extra) + nl + "}" + nl, encoding="utf-8")
    print("sounds.json: added", added)


if __name__ == "__main__":
    main()
