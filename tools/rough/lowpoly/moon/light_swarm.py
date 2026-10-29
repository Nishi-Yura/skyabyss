# -*- coding: utf-8 -*-
"""光の群れ (light_swarm) 低ポリ 一品一案。満月の野の敵 (docs/design/46 の 4.5)。
欠けた光の結晶が群れて一つの体になったもの。真ん中に銀の欠けた輪 (三日月の芯) と星の目、まわりを石英の結晶が
彗星の形 (前が詰まり、後ろへ流れる) に囲む。満ちでは結晶が後ろへ揃って突っ込み、引きでは散って漂う。
骨: root > core > ring_f (前の結晶) / ring_m (中) / ring_t (尾)。宙に浮く (芯の高さ 1.3 ブロック)。
結晶はどれも両側の尖った柱 (閉じた形)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

CY = 21.0   # 芯の高さ
BONES = {"root": (None, (0, 0, 0)), "core": ("root", (0, CY, 0)),
         "ring_f": ("core", (0, CY, -2)), "ring_m": ("core", (0, CY, 2)), "ring_t": ("core", (0, CY, 7))}
QUARTZ = [(70, 92, 130), (130, 160, 200), (186, 210, 238), (226, 238, 252)]
PAL = make_pal({
    "quartz": (QUARTZ, 0.68, dict(crack=0.04, noise=0.1)),
    "silver": (SILVER, 0.66, dict(crack=0.05, noise=0.14)),
    "hollow": (SHADOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "star": (STAR, 0.8, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["star"])


def crystal(bone, m, c, d, L, r, back=0.45, sides=5):
    """両側の尖った結晶 (前へ L、後ろへ L*back)"""
    d = np.asarray(d, float)
    d = d / np.linalg.norm(d)
    c = np.asarray(c, float)
    hint = (0, 1, 0) if abs(d[1]) < 0.8 else (1, 0, 0)
    return spike(bone, m, c, d, L, r, sides=sides, rings=2, hint=hint) + spike(bone, m, c, -d, L * back, r, sides=sides, rings=1, hint=hint)


def build(k):
    F = []
    # 芯: 欠けた銀の輪 (三日月) を縦に立て、内に影の玉と星の目
    F += part("heart", ellipsoid("core", "star", (0, CY, 0), (1.9, 1.9, 1.7), segs=10, rings=5))
    pts = []
    for i in range(12):
        a = math.radians(-150 + i * 300 / 11)   # 右下が欠けた輪
        pts.append((math.sin(a) * 3.4, CY + math.cos(a) * 3.4, 0.0))
    F += part("crescent", tube("core", "silver", pts, ra=[0.7] * 5 + [0.95] * 2 + [0.7] * 5, rb=1.1, sides=5, hint=(0, 0, 1),
                               caps=(True, True)))
    F += part("cup", ellipsoid("core", "hollow", (0, CY, 0.9), (2.4, 2.4, 1.6), segs=10, rings=4))
    # 結晶の群れ: 彗星の形。前は短く詰まり、尾ほど長い結晶が後ろへ流れる
    rnd = np.random.default_rng(7)
    groups = [("ring_f", 9, -2.6, 4.6, 2.6, 0.8), ("ring_f", 6, -4.6, 2.6, 1.8, 0.6), ("ring_m", 10, 1.6, 5.4, 3.6, 0.85),
              ("ring_m", 6, 3.6, 7.2, 2.0, 0.55), ("ring_t", 7, 7.0, 3.6, 5.0, 0.75)]
    n = 0
    for bone, cnt, z, rad, L, r in groups:
        for i in range(cnt):
            a = 2 * math.pi * (i + rnd.uniform(-0.2, 0.2)) / cnt + (0.4 if bone == "ring_m" else 0)
            rr = rad * rnd.uniform(0.8, 1.1)
            c = (math.cos(a) * rr, CY + math.sin(a) * rr, z + rnd.uniform(-0.8, 0.8))
            # 向き: 後ろ (+Z) へ流れ、少し外へ開く
            d = (math.cos(a) * 0.35, math.sin(a) * 0.35, 1.0)
            m = "star" if (n % 5 == 2) else "quartz"
            F += part(f"cr{n}", crystal(bone, m, c, d, L * rnd.uniform(0.85, 1.2), r))
            n += 1
    # 尾の芯の結晶 (いちばん長い)
    F += part("tailspine", crystal("ring_t", "quartz", (0, CY, 8.5), (0, 0.05, 1), 6.5, 0.8, back=0.35))
    F += part("tailstar", crystal("ring_t", "star", (0, CY, 7.0), (0, 0, 1), 1.4, 0.45))
    return F, [eye_at(0, CY, -1.75, 0.9)]


POSES = {
    "idle": {},
    "dash": {"core": (-8, 0, 0), "ring_f": (12, 0, 20), "ring_m": (-10, 0, 0), "ring_t": (-14, 0, 0)},
    "scatter": {"core": (0, 0, 0), "ring_f": (0, 0, 60), "ring_m": (0, 0, -45), "ring_t": (18, 0, 30)},
}

SPEC = dict(
    id="light_swarm", short="光の群れ", kind="満月の野の敵 一品一案 (46 の 4.5)",
    title="光の群れ (light_swarm) — 欠けた光の結晶が群れて一つの体・低ポリ + ドット絵 一案",
    sub=["満ちでは結晶が後ろへ揃い、線の予告 (0.8 秒) のあと素早く突っ込む。引きでは散って漂い、傷が通りやすい (×1.5)。",
         "宙に浮く (芯の高さ 1.3 ブロック)。銀の欠けた輪の芯に星の目。結晶は石英の白青、五つに一つが光る。"],
    labels=["光の群れ"],
    rows=[("前 3/4", "idle", 212, 14, 360, (0, 20, 1), 13.0),
          ("横", "idle", 270, 4, 360, (0, 20, 1), 13.0),
          ("突っ込む (満ち)", "dash", 222, 10, 360, (0, 20, 1), 13.0),
          ("散る (引き)", "scatter", 32, 14, 360, (0, 20, 1), 13.0)],
    options={"a": dict(name="欠けた輪の芯の群れ", desc="銀の欠けた輪 (三日月) の芯に星の目、まわりを石英の結晶が彗星の形に囲む。尾ほど長い結晶。")},
    extra="月の言葉 (石英の白青・銀・星の光・影)。骨 5 本。",
    budget="小さな敵 400〜800", pw=560,
    later="二段目 = 結晶が増え、光る結晶が三つに一つ。三段目 = 輪が満ちて丸くなり、尾が二股。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
