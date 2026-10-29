# -*- coding: utf-8 -*-
"""灯蛾 (lamp_moth) 低ポリ 一品一案。灯の島の敵 (docs/design/50 の 4.2.2)。
灯の光から生まれる白金の蛾。翼を広げて 1.4 ブロック。毛の多い白い胴、前翅は広く後翅は尾のように長い。翅に朝の光の目玉の紋 (光る)。羽毛の触角。
寄ってきて、触れる 1 秒前に強く光る (予告)。触れられた人は 6 秒「灯される」。
骨: root > body > head / wing_l / wing_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8, 0)), "head": ("body", (0, 8.6, -2.8)),
         "wing_l": ("body", (-0.9, 9.0, -0.8)), "wing_r": ("body", (0.9, 9.0, -0.8))}
PAL = make_pal({
    "fur": (CLOUDW, 0.5, dict(crack=0.0, noise=0.3)),
    "wing": (CLOUDW, 0.6, dict(crack=0.0, noise=0.12)),
    "gold": (GOLDW, 0.7, dict(crack=0.0, noise=0.12)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["dawn"])


def wing(F, s):
    b = "wing_l" if s < 0 else "wing_r"
    # 前翅: 平たい三角に近い板 (前縁は金)
    fore = [(s * 0.9, 9.0, -1.2), (s * 4.0, 9.6, -2.4), (s * 7.6, 9.8, -2.0), (s * 9.4, 9.6, -0.4)]
    F += part(f"lead{s}", tube(b, "gold", fore, ra=0.2, rb=[0.4, 0.35, 0.3, 0.15], sides=4, hint=(0, 1, 0)))
    for i in range(9):
        t = i / 8
        x = s * (1.2 + 7.6 * t)
        F += part(f"fw{s}{i}", tube(b, "wing", [(x, 9.5 + 0.2 * t, -2.0 + 1.0 * t), (x + s * 0.2, 9.3, 0.6 + 0.2 * t)],
                                    ra=[0.14, 0.12], rb=[1.3, 1.1], sides=4, hint=(0, 1, 0)))
    # 前翅の目玉の紋
    F += part(f"spot{s}", ellipsoid(b, "dawn", (s * 5.6, 9.8, -0.6), (1.4, 0.2, 1.2), segs=8, rings=2))
    F += part(f"spotc{s}", ellipsoid(b, "hollow", (s * 5.6, 9.92, -0.6), (0.3, 0.1, 0.28), segs=6, rings=2))
    # 後翅: 尾のように長く後ろへ
    for i in range(3):
        x0 = s * (1.0 + 1.2 * i)
        F += part(f"hw{s}{i}", tube(b, "wing", [(x0, 9.0, 0.4), (x0 + s * 1.4, 8.6, 3.0), (x0 + s * 1.8, 8.2, 5.4 - i * 0.6)],
                                    ra=[0.12, 0.1, 0.06], rb=[1.2, 1.0, 0.4], sides=4, hint=(0, 1, 0)))
    F += part(f"tailspot{s}", ellipsoid(b, "dawn", (s * 3.0, 8.3, 5.0), (0.45, 0.14, 0.6), segs=6, rings=2))


def build(k):
    F = []
    F += part("thorax", ellipsoid("body", "fur", (0, 9.0, -1.0), (1.2, 1.2, 1.5), segs=8, rings=3))
    F += part("abdomen", ellipsoid("body", "fur", (0, 8.6, 1.8), (0.95, 0.9, 2.2), segs=8, rings=3))
    for i in range(3):
        F += part(f"band{i}", tube("body", "gold", [(math.cos(a) * 0.92, 8.6, 1.0 + i * 1.0 + math.sin(a) * 0.0) if False else
                                                     (math.cos(a) * (0.95 - 0.15 * i), 8.6 + math.sin(a) * (0.9 - 0.15 * i), 1.0 + i * 1.1)
                                                     for a in [j / 8 * 2 * math.pi for j in range(9)]], ra=0.16, sides=4, hint=(0, 0, 1)))
    F += part("ruff", ellipsoid("body", "fur", (0, 9.3, -2.2), (1.3, 1.1, 0.8), segs=8, rings=2))
    F += part("head", ellipsoid("head", "fur", (0, 8.8, -3.0), (0.8, 0.75, 0.7), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "hollow", (s * 0.55, 8.9, -3.4), (0.32, 0.32, 0.3), segs=5, rings=2))
        F += part(f"ant{s}", tube("head", "gold", [(s * 0.3, 9.4, -3.3), (s * 1.2, 11.0, -4.4), (s * 2.0, 11.8, -4.2)],
                                  ra=0.08, rb=[0.4, 0.5, 0.15], sides=4, hint=(0, 0, 1)))
        for j in range(3):
            F += part(f"leg{s}{j}", tube("body", "gold", [(s * 0.8, 8.2, -1.6 + j * 0.8), (s * 1.6, 7.0, -1.8 + j * 0.9)], ra=0.1, sides=3, hint=(0, 1, 0)))
    wing(F, -1)
    wing(F, 1)
    return F, []


POSES = {
    "idle": {},
    "flare": {"wing_l": (0, 0, 40), "wing_r": (0, 0, -40), "body": (-15, 0, 0)},
}

SPEC = dict(
    id="lamp_moth", short="灯蛾", kind="灯の島の敵 一品一案 (50 の 4.2.2)",
    title="灯蛾 (lamp_moth) — 灯の光から生まれる白金の蛾・低ポリ + ドット絵 一案",
    sub=["寄ってきて、触れる 1 秒前に強く光る (予告)。触れられた人は 6 秒「灯される」(陰に入っても光の筋に見つかる)。",
         "毛の多い白い胴に金の帯、白い翅の前縁は金、翅に朝の光の目玉の紋。翼を広げて 1.4 ブロック。"],
    labels=["灯蛾"],
    rows=[("上から", "idle", 200, 50, 300, (0, 9, 1), 10.0),
          ("前 3/4", "idle", 212, 12, 300, (0, 9, 1), 10.0),
          ("光る", "flare", 230, 10, 300, (0, 9, 1), 10.0),
          ("横", "idle", 270, 4, 300, (0, 9, 1), 10.0)],
    options={"a": dict(name="目玉の紋の白金の蛾", desc="白い毛の胴と金の帯、白い翅に金の前縁と朝の光の目玉の紋、羽毛の触角、尾のような後翅。")},
    extra="雲海の言葉 (白・金・朝の光)。骨 5 本。光るのは翅の紋。",
    budget="小さな敵 400〜900", pw=420,
    later="二段目 = 紋が二つずつに。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
