"""迷い灯 (stray_wisp)。島と草原の夜に浮く小物。Lv 1〜4。
一体一案: 夜の野に浮く青白い灯。芯の光の玉を、割れた古い灯籠の骨組み (三本の黒い鉄の肋と傘) が半ば包み、
下へ薄い霧の尾が三筋垂れる。まわりを小さな光の粒が二つめぐる。地面から 1 ブロックほど浮いてゆらゆら漂う。
動き: 近づくと明滅して (予告 0.6 秒、芯が膨らむ)、まわりの人の視界を暗くする (暗闇 3 秒)。弱く、二、三回で消える。
"""
import math

from fk import *  # noqa: F401,F403
from fk import box, run1, part, mat

BONES = {"root": (None, (0, 0, 0)), "core": ("root", (0, 18.0, 0)), "cage": ("core", (0, 18.0, 0)),
         "tail": ("core", (0, 14.0, 0)), "mote_a": ("core", (0, 18.0, 0)), "mote_b": ("core", (0, 18.0, 0))}
IRON = [(18, 20, 26), (34, 36, 44), (54, 56, 66), (76, 78, 88)]
MIST = [(60, 90, 120), (90, 130, 170), (130, 170, 210), (170, 206, 236)]
PAL = {"mats": {"iron": mat(IRON, 0.55, crack=0.05), "mist": mat(MIST, 0.6, noise=0.3), "dark": mat([(8, 8, 12), (18, 16, 20), (30, 28, 32)], 0.5, min=0)},
       "skull_bone": "core", "face_mat": "mist"}


def build(k):
    F = []
    F += part("core", ellipsoid("core", "glow_pale", (0, 18.0, 0), (2.2, 2.6, 2.2), segs=8, rings=3))
    F += part("halo", ellipsoid("core", "glow_blue", (0, 18.0, 0), (1.2, 1.4, 1.2), segs=6, rings=2))
    # 割れた灯籠の骨組み: 上の小さな傘と、三本の肋 (一本は折れて短い)
    F += part("hat", loft("cage", "iron", [[V(2.8 * math.cos(math.radians(a)), 21.2, 2.8 * math.sin(math.radians(a))) for a in range(0, 360, 60)],
                                          [V(0.6 * math.cos(math.radians(a)), 23.0, 0.6 * math.sin(math.radians(a))) for a in range(0, 360, 60)]], caps=("iron", "iron")))
    F += part("ring", tube("cage", "iron", [(0, 23.0, 0), (0, 24.4, 0)], ra=[0.3, 0.3], sides=4, hint=(1, 0, 0)))
    for i, (a, low) in enumerate(((90, 14.4), (210, 14.4), (330, 17.2))):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        F += part(f"rib{i}", tube("cage", "iron", [(2.6 * ca, 21.2, 2.6 * sa), (3.2 * ca, 18.0, 3.2 * sa), (2.4 * ca, low, 2.4 * sa)], ra=[0.28, 0.28, 0.2], sides=4, hint=(0, 1, 0)))
    # 霧の尾 (下へ三筋、揺れる)
    for i, (dx, dz, L) in enumerate(((0.0, 0.6, 7.0), (-1.0, -0.6, 5.4), (1.1, -0.4, 4.6))):
        F += part(f"tail{i}", tube("tail", "mist", [(dx, 16.0, dz), (dx * 1.4, 13.0, dz * 1.2), (dx * 0.6, 16.0 - L, dz * 1.6)], ra=[1.0, 0.6, 0.1], sides=5, hint=(1, 0, 0)))
    # めぐる光の粒
    F += part("mote_a", ellipsoid("mote_a", "glow_blue", (4.6, 19.4, 0), (0.5, 0.5, 0.5), segs=5, rings=1))
    F += part("mote_b", ellipsoid("mote_b", "glow_blue", (-3.6, 16.4, 2.6), (0.4, 0.4, 0.4), segs=5, rings=1))
    return F, []


POSES = {"idle": {}, "telegraph": {"core": (0, 0, 0)}}

SPEC = dict(id="stray_wisp", short="迷い灯", title="迷い灯 (stray_wisp) — 夜の野に浮く青白い灯",
            sub=["芯の光の玉を割れた古い灯籠の骨組みが半ば包み、下へ霧の尾が三筋。まわりを光の粒がめぐる。地面から 1 ブロック浮く。",
                 "予告: 芯が膨らんで明滅 (0.6 秒) → まわりの人の視界を暗くする。"],
            rows=[("前 3/4", "idle", 210, 10, 460, (0, 16, 0), 16.0), ("横", "idle", 270, 6, 460, (0, 16, 0), 16.0),
                  ("後ろ 3/4", "idle", 30, 10, 460, (0, 16, 0), 16.0)], atlas_h=200)

if __name__ == "__main__":
    run1(SPEC, build, PAL, BONES, POSES)
