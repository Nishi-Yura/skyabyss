"""心臓の番人「錆の門番」B 鍵持ちの騎士 (2026-11-08 の選定。ラフ docs/review/roughs_abyss/reshape_warden.png の B)。
錆びた鎧の古い骨の騎士。胸当ての鍵穴の形の切れ目の奥に肋と、深淵の青緑に光る心臓。兜の鍵穴の覗き窓が光る。
右手に背丈ほどの大鍵 (頭の輪に青緑の宝石)、背に鎖の外套、肩に棘の肩当て。
骨と骨の位置はいまの番人と同じ (動き heart_warden.animation.json をそのまま使う): body・ribs (胸が息をする)・heart (鼓動)・head・arm_l/r・leg_l/r。
座標は 1 単位 = 1/16 ブロック、前 = -Z、高さ約 54。

    python -I -X utf8 tools/rough/lowpoly/abyss/warden_knight.py   → docs/review/abyss_boss_models/src/heart_warden/ (views・close も)
    入れる: python -I -X utf8 tools/install/install_lowpoly.py heart_warden --mesh … --atlas … --glow …   (このスクリプトが続けて呼ぶ: --install)
"""
import math
import os
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from boss_kit import *  # noqa: E402,F401,F403
import boss_kit as K  # noqa: E402

ROOT = Path(__file__).resolve().parents[4]
BONES = {
    "root": (None, (0, 0, 0)),
    "body": ("root", (0, 0, 0)),
    "ribs": ("body", (0, 30, 0)),
    "heart": ("ribs", (0, 33, -2.6)),
    "head": ("body", (0, 45, -1)),
    "arm_l": ("body", (-6.5, 41, 0)),
    "arm_r": ("body", (6.5, 41, 0)),
    "leg_l": ("body", (-3.5, 24, 0)),
    "leg_r": ("body", (3.5, 24, 0)),
}


def ring_pts(y, rx, rzf, rzb, n=12, cz=0.0, cx=0.0):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        z = -math.cos(a) * (rzf if math.cos(a) > 0 else rzb)
        pts.append(P(cx + rx * math.sin(a), y, cz + z))
    return pts


def build():
    F = []
    # ---- 脚: 鉄の脛当て、錆びた膝当て、骨の腿がのぞく、尖った鉄靴
    for s in (-1, 1):
        lg = side_bone(s, "leg")
        x = s * 3.5
        F += part(f"thigh{s}", tube(lg, "bone", [P(x, 24.0, 0), P(x + s * 0.2, 17.0, 0.2)], ra=[2.1, 1.7], sides=8, caps=(True, True)))
        F += part(f"cuisse{s}", loft(lg, "rust", [ring_pts(23.6, 2.6, 2.4, 1.6, n=10, cx=x), ring_pts(19.0, 2.2, 2.3, 1.2, n=10, cx=x + s * 0.2)], caps=(True, True)))
        F += part(f"knee{s}", ellipsoid(lg, "iron", P(x + s * 0.2, 16.4, -1.0), (2.0, 1.8, 1.6), segs=10, rings=4))
        F += part(f"knee_spike{s}", cone(lg, "rust", P(x + s * 0.2, 16.4, -2.4), (0, 0.1, -1), 2.0, 0.6, sides=4, rings=2))
        F += part(f"greave{s}", loft(lg, "iron", [ring_pts(15.4, 1.9, 2.0, 1.6, n=10, cx=x + s * 0.2), ring_pts(8.0, 1.6, 1.8, 1.4, n=10, cx=x + s * 0.25),
                                                  ring_pts(3.0, 1.5, 1.6, 1.3, n=10, cx=x + s * 0.3)], caps=(True, True)))
        F += part(f"greave_rim{s}", loop(lg, "rust", (x + s * 0.3) * 1.0, 3.2, 0.0, 1.65, 1.6, r=0.3, n=10, gap=8, sides=4))
        F += part(f"sabaton{s}", ellipsoid(lg, "rust", P(x + s * 0.3, 1.4, -1.4), (1.8, 1.4, 3.2), segs=10, rings=4))
        F += part(f"toe{s}", cone(lg, "iron", P(x + s * 0.3, 1.2, -4.2), (0, -0.1, -1), 1.8, 0.8, sides=4, rings=2))
    # ---- 腰: 錆びた草摺 (前後に分かれた板) と鉄の帯
    b = "body"
    for i in range(7):
        a = math.radians(-90 + i * 30)
        cx, cz = 4.6 * math.sin(a), -3.2 * math.cos(a)
        F += part(f"tasset{i}", ellipsoid(b, "rust" if i % 2 == 0 else "iron", P(cx, 22.0, cz), (2.0, 3.6, 0.6), segs=8, rings=3, rot=rot_y(-math.degrees(a))))
    F += part("hips", ellipsoid(b, "iron", P(0, 25.6, 0.2), (5.2, 2.4, 3.6), segs=12, rings=4))
    F += part("belt", loop(b, "rust", 0, 26.6, 0.2, 5.4, 3.8, r=0.55, n=16, gap=6, at=0, sides=5))
    F += part("buckle", ellipsoid(b, "iron", P(0, 26.6, -3.9), (1.2, 1.0, 0.4), segs=8, rings=3))
    # ---- 胸当て (ribs): 前に鍵穴の形の切れ目。奥に骨の肋と心臓
    rb = "ribs"
    F += part("cuirass_back", loft(rb, "rust", [ring_pts(27.4, 5.0, 0.4, 3.4, n=12, cz=0.2), ring_pts(34.0, 6.0, 0.4, 3.9, n=12, cz=0.4), ring_pts(41.0, 6.4, 0.4, 3.7, n=12, cz=0.4),
                                                ring_pts(43.0, 3.6, 0.4, 2.6, n=12, cz=0.4)], caps=(True, True)))
    for s in (-1, 1):
        # 前の左右の板 (真ん中の切れ目を空ける)
        F += part(f"cuirass_front{s}", loft(rb, "rust", [
            [P(s * 1.6, 27.6, -3.0), P(s * 5.0, 27.6, -1.6), P(s * 5.0, 27.6, 0.2), P(s * 1.6, 27.6, -2.2)],
            [P(s * 2.2, 34.0, -3.9), P(s * 6.0, 34.0, -2.0), P(s * 6.0, 34.0, 0.4), P(s * 2.2, 34.0, -3.0)],
            [P(s * 1.4, 40.6, -3.7), P(s * 6.4, 40.6, -1.8), P(s * 6.4, 40.6, 0.4), P(s * 1.4, 40.6, -2.8)],
            [P(s * 1.0, 43.0, -2.4), P(s * 3.6, 43.0, -1.4), P(s * 3.6, 43.0, 0.4), P(s * 1.0, 43.0, -1.6)],
        ], caps=(True, True)))
        F += part(f"cuirass_rim{s}", tube(rb, "iron", [P(s * 1.6, 27.8, -3.1), P(s * 2.3, 34.0, -4.0), P(s * 1.5, 40.4, -3.8), P(s * 1.1, 42.8, -2.5)], ra=0.45, sides=5, caps=(True, True)))
        for k in range(3):
            F += part(f"rivet{s}{k}", ellipsoid(rb, "iron", P(s * (4.0 + k * 0.6), 31.0 + k * 3.6, -2.6 - k * 0.2), (0.45, 0.45, 0.3), segs=6, rings=2))
    # 切れ目の中: 黒い虚、骨の肋、鍵穴の輪
    F += part("hollow", ellipsoid(rb, "void", P(0, 34.4, -0.4), (2.6, 6.4, 2.4), segs=10, rings=4))
    for k, y in enumerate((30.0, 32.4, 36.6, 39.0)):
        F += part(f"ribbar{k}", tube(rb, "bone", [P(-2.0, y, -2.6), P(0, y - 0.6, -3.4), P(2.0, y, -2.6)], ra=0.42, sides=5, caps=(True, True)))
    F += part("keyhole_ring", hoop(rb, "iron", 0, 36.2, -3.6, 2.2, 2.2, r=0.38, n=14, gap=50, at=180))
    F += part("heart", ellipsoid("heart", "teal", P(0, 33.4, -2.0), (1.6, 2.0, 1.4), segs=10, rings=5))
    F += part("heart_vein", tube("heart", "teal", [P(0, 35.2, -1.8), P(0.4, 37.4, -1.2), P(0, 39.6, -0.8)], ra=0.35, sides=4, caps=(True, True)))
    F += part("gorget", loop(rb, "iron", 0, 43.4, 0.0, 3.0, 2.4, r=0.7, n=14, gap=8, at=180, sides=5))
    # ---- 鎖の外套 (背): 肩から垂れる鎖の列
    for i in range(7):
        x = -4.8 + i * 1.6
        for j in range(5):
            F += part(f"chain{i}_{j}", hoop(rb, "iron", x, 41.6 - j * 3.0, 4.4 + j * 0.25, 0.55, 0.85, r=0.2, n=6, gap=24))
    F += part("cloak", loft(rb, "cloth", [ring_pts(42.4, 5.6, 0.1, 0.5, n=10, cz=4.0), ring_pts(33.0, 6.2, 0.1, 0.6, n=10, cz=4.8),
                                          ring_pts(24.0, 6.6, 0.1, 0.6, n=10, cz=5.4)], caps=(True, True)))
    # ---- 腕: 棘の肩当て、腕甲、鉄の籠手
    for s in (-1, 1):
        a_ = side_bone(s, "arm")
        x = s * 6.5
        F += part(f"pauldron{s}", ellipsoid(a_, "iron", P(x + s * 0.6, 41.6, 0.2), (3.4, 2.4, 3.2), segs=12, rings=5))
        F += part(f"pauldron2{s}", ellipsoid(a_, "rust", P(x + s * 1.0, 40.2, 0.2), (3.2, 1.4, 3.0), segs=12, rings=3))
        for k in range(3):
            F += part(f"pspike{s}{k}", cone(a_, "rust", P(x + s * (1.4 + k * 0.4), 43.4 - k * 0.4, -1.0 + k * 1.0), (s * 0.5, 1, 0.1), (3.0 - k * 0.6), 0.55, sides=4, rings=2))
        F += part(f"upperarm{s}", tube(a_, "bone", [P(x + s * 0.3, 39.0, 0), P(x + s * 0.6, 33.6, 0.2)], ra=[1.5, 1.3], sides=8, caps=(True, True)))
        F += part(f"elbow{s}", ellipsoid(a_, "iron", P(x + s * 0.6, 33.0, 0.4), (1.8, 1.6, 1.8), segs=10, rings=4))
        F += part(f"vambrace{s}", loft(a_, "rust", [ring_pts(32.0, 1.7, 1.8, 1.7, n=10, cx=x + s * 0.6, cz=0.2), ring_pts(26.8, 1.9, 2.0, 1.9, n=10, cx=x + s * 0.7, cz=-0.4)], caps=(True, True)))
        F += part(f"gauntlet{s}", ellipsoid(a_, "iron", P(x + s * 0.7, 25.0, -0.6), (1.8, 2.0, 1.6), segs=10, rings=4))
        for k, dz in enumerate((-0.8, 0.0, 0.8)):
            F += part(f"finger{s}{k}", tube(a_, "iron", [P(x + s * 0.8, 23.6, -0.8 + dz), P(x + s * 0.9, 21.8, -1.4 + dz), P(x + s * 0.7, 20.8, -1.0 + dz)], ra=[0.4, 0.32, 0.2], sides=4, caps=(True, True)))
    # ---- 大鍵 (右手): 軸・頭の輪・宝石・歯
    ar = "arm_r"
    kx, kz = 7.3, -2.0
    F += part("key_shaft", tube(ar, "rust", [P(kx, 4.0, kz), P(kx, 24.0, kz), P(kx, 44.0, kz)], ra=0.75, sides=6, caps=(True, True)))
    F += part("key_collar", loop(ar, "iron", kx, 44.4, kz, 1.2, 1.2, r=0.4, n=8, gap=8, sides=4))
    F += part("key_bow", hoop(ar, "rust", kx, 49.0, kz, 3.4, 3.4, r=0.85, n=16, gap=4))
    F += part("key_bow_in", hoop(ar, "iron", kx, 49.0, kz, 2.2, 2.2, r=0.35, n=12, gap=6))
    F += part("key_gem", ellipsoid(ar, "teal", P(kx, 49.0, kz), (1.2, 1.4, 0.7), segs=8, rings=4))
    for k in range(4):
        a = 2 * math.pi * k / 4 + math.pi / 4
        F += part(f"key_bow_spike{k}", cone(ar, "rust", P(kx + 3.4 * math.sin(a), 49.0 + 3.4 * math.cos(a), kz), (math.sin(a), math.cos(a), 0), 1.6, 0.4, sides=4, rings=1))
    for k, (dy, w) in enumerate(((5.0, 2.6), (7.4, 1.8), (9.6, 2.2))):
        F += part(f"key_bit{k}", ellipsoid(ar, "rust", P(kx + w * 0.5 + 0.6, dy, kz), (w * 0.6, 0.8, 0.6), segs=8, rings=3))
    F += part("key_tip", cone(ar, "iron", P(kx, 4.2, kz), (0, -1, 0), 2.0, 0.7, sides=6, rings=2))
    # ---- 頭: 鉄の兜、鍵穴の覗き窓 (青緑)、錆の飾り棘、後ろに垂れる布
    h = "head"
    F += part("helm", loft(h, "iron", [ring_pts(44.0, 2.6, 2.8, 2.6, n=12), ring_pts(47.0, 3.0, 3.1, 3.0, n=12), ring_pts(50.6, 2.8, 2.8, 2.9, n=12),
                                       ring_pts(52.6, 1.6, 1.6, 1.8, n=12), ring_pts(53.4, 0.4, 0.4, 0.4, n=12)], caps=(True, True)))
    F += part("visor_ring", ellipsoid(h, "rust", P(0, 49.0, -2.9), (1.1, 1.1, 0.4), segs=8, rings=3))
    F += part("visor_hole", ellipsoid(h, "teal", P(0, 49.0, -3.15), (0.6, 0.6, 0.25), segs=8, rings=2))
    F += part("visor_slot", ellipsoid(h, "teal", P(0, 47.4, -3.0), (0.35, 1.2, 0.2), segs=6, rings=2))
    F += part("crest", tube(h, "rust", [P(0, 53.0, -1.6), P(0, 55.6, 0.2), P(0, 54.0, 3.4)], ra=[0.6, 0.8, 0.3], sides=4, hint=(1, 0, 0), caps=(True, True)))
    for s in (-1, 1):
        F += part(f"helm_horn{s}", arc(h, "rust", P(s * 2.6, 50.6, 0.2), P(s * 5.0, 53.0, 0.6), P(s * 4.4, 56.4, 1.2), r=0.6, n=5, taper=(0.7, 0.12), sides=4))
        F += part(f"cheek{s}", ellipsoid(h, "rust", P(s * 2.2, 46.4, -1.8), (0.9, 1.8, 1.4), segs=8, rings=3))
    F += part("helm_cloth", loft(h, "cloth", [ring_pts(51.0, 2.6, 0.1, 0.4, n=8, cz=2.4), ring_pts(44.0, 3.2, 0.1, 0.5, n=8, cz=3.2)], caps=(True, True)))
    return F


def main(argv):
    F = build()
    K.export("heart_warden", F, BONES, tpu=2.5, crackglow=0.0, seed=3.0, center=(0, 28, 0), scale=4.0)
    if "--install" in argv:
        d = K.OUT / "heart_warden"
        env = dict(os.environ)
        env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools/lib"), str(ROOT / "tools/audit")])
        subprocess.check_call([sys.executable, str(ROOT / "tools/install/install_lowpoly.py"), "heart_warden", "--mesh", str(d / "heart_warden_mesh.json"),
                               "--atlas", str(d / "heart_warden_atlas.png"), "--glow", str(d / "heart_warden_glowmask.png")], cwd=ROOT, env=env)


if __name__ == "__main__":
    main(sys.argv[1:])
