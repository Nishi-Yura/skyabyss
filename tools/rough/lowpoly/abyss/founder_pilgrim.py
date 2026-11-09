"""終章 始祖「雲の喪章」C 霧の巡礼 (2026-11-08 の選定。ラフ docs/review/roughs_abyss/reshape_founder.png の C)。
初代の空見 (見上げすぎて目を失い、布に目を描いた人) の由来はそのまま、灰白の雲の体と黒いレースの喪章で作り直す:
  背の高い痩せた姿、後ろへ反って上へ尖る長い頭巾。顔を覆う黒いレースに、白く光る描いた目。
  裾は雲の塊に溶ける。肩から黒いレースの細い帯が何本も垂れる (肩掛けの骨で揺れる)。
  右手の見張りの竿 (金の照準の輪の中に小鐘、青銅の穂先) と左の腰の小鐘は前のまま (技の動きが竿を使う)。
骨は前の始祖と同じ (動き founder.animation.json をそのまま使う): body・head・arm_l/r・pole (右腕の子)・shawl・hipbell・leg_l/r。

    python -I -X utf8 tools/rough/lowpoly/abyss/founder_pilgrim.py [--install]   → docs/review/abyss_boss_models/src/founder/
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
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 17, 0)), "head": ("body", (0, 30.5, 0)),
         "arm_l": ("body", (-4.6, 28.4, 0)), "arm_r": ("body", (4.6, 28.4, 0)), "pole": ("arm_r", (6.0, 17.6, -1.8)),
         "shawl": ("body", (0, 29.0, 0.5)), "hipbell": ("body", (-4.0, 18.6, -0.6)),
         "leg_l": ("body", (-1.8, 17, 0)), "leg_r": ("body", (1.8, 17, 0))}

K.MATS.setdefault("cloudw", dict(ramp=[(118, 120, 132), (156, 158, 170), (194, 196, 206), (224, 224, 232), (246, 246, 250)], grain=0.14, crev=0.35))
K.MATS.setdefault("lace", dict(ramp=[(4, 4, 6), (12, 10, 14), (24, 20, 26), (38, 34, 40)], grain=0.3, crev=0.3, weave=0.6))
K.MATS.setdefault("gold", dict(ramp=[(90, 60, 18), (140, 100, 32), (196, 152, 60), (236, 200, 104), (255, 234, 168)], grain=0.08, crev=0.5, rim=0.4))
K.MATS.setdefault("skinp", dict(ramp=[(150, 140, 136), (190, 182, 178), (222, 216, 212), (240, 236, 234)], grain=0.08, crev=0.3))


def ring_pts(y, rx, rzf, rzb, n=12, cz=0.0, cx=0.0, wob=0.0, seed=0.0):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        r_ = 1.0 + wob * math.sin(a * 3 + seed)
        z = -math.cos(a) * (rzf if math.cos(a) > 0 else rzb)
        pts.append(P(cx + rx * math.sin(a) * r_, y, cz + z * r_))
    return pts


def build():
    F = []
    b = "body"
    # 脚 (衣の下。雲に溶けるので見えない芯だけ)
    for s in (-1, 1):
        lg = side_bone(s, "leg")
        F += part(f"leg{s}", tube(lg, "cloudw", [P(s * 1.8, 17.0, 0), P(s * 1.9, 3.0, 0.1)], ra=1.2, sides=7, caps=(True, True)))
    # 衣: 腰から下へ広がり、裾は雲の塊に溶ける
    F += part("robe", loft(b, "cloudw", [ring_pts(18.0, 2.6, 1.9, 2.0, n=12), ring_pts(12.0, 3.4, 2.5, 2.7, n=12, wob=0.04, seed=1),
                                         ring_pts(6.0, 4.2, 3.2, 3.4, n=12, wob=0.06, seed=2), ring_pts(2.6, 4.8, 3.7, 3.9, n=12, wob=0.08, seed=3)], caps=(True, True)))
    for i in range(11):
        a = 2 * math.pi * i / 11
        r = 4.6 + (i % 3) * 0.5
        F += part(f"puff{i}", ellipsoid(b, "cloudw", P(r * math.sin(a), 1.6 + (i % 2) * 1.0, -r * 0.8 * math.cos(a)), (2.4 - (i % 2) * 0.4, 1.5, 2.1), segs=9, rings=4))
    for i in range(5):
        a = 2 * math.pi * (i + 0.5) / 5
        F += part(f"puff_hi{i}", ellipsoid(b, "cloudw", P(3.8 * math.sin(a), 4.6, -3.0 * math.cos(a)), (1.6, 1.1, 1.5), segs=8, rings=3))
    # 胴: 細く高い。黒いレースの帯を斜めに掛け、胸に金の留め
    F += part("torso", loft(b, "cloudw", [ring_pts(17.6, 2.5, 1.8, 1.9, n=12), ring_pts(23.0, 2.9, 1.9, 2.0, n=12), ring_pts(28.0, 3.4, 2.0, 2.1, n=12),
                                          ring_pts(29.8, 2.0, 1.4, 1.5, n=12)], caps=(True, True)))
    F += part("sash", tube(b, "lace", [P(-3.0, 28.6, -1.6), P(-0.6, 24.0, -2.2), P(2.4, 19.0, -1.9), P(3.0, 17.4, -1.4)], ra=0.55, sides=4, hint=(0, 0, 1), caps=(True, True)))
    F += part("brooch", ellipsoid(b, "gold", P(-1.8, 27.0, -2.1), (0.7, 0.7, 0.3), segs=8, rings=3))
    F += part("brooch_gem", ellipsoid(b, "pale", P(-1.8, 27.0, -2.35), (0.35, 0.35, 0.15), segs=6, rings=2))
    F += part("belt", loop(b, "lace", 0, 17.8, 0, 2.7, 2.0, r=0.4, n=14, gap=6, at=0, sides=4))
    # 肩掛け (shawl の骨): 肩の雲の襟と、そこから垂れる黒いレースの細帯
    sh = "shawl"
    for s in (-1, 1):
        F += part(f"shoulder{s}", ellipsoid(sh, "cloudw", P(s * 3.2, 28.8, 0.2), (2.3, 1.1, 2.1), segs=10, rings=4))
    for i in range(10):
        x = -3.9 + i * 0.87
        z = 0.8 + abs(x) * 0.25
        ln = 16.0 + (i % 3) * 3.0 - abs(x) * 0.6
        p0 = np.asarray(P(x, 28.6, z))
        F += part(f"strip{i}", tube(sh, "lace", [p0, p0 + np.asarray(P(x * 0.12, -ln * 0.45, 0.6)), p0 + np.asarray(P(x * 0.25, -ln, 1.2 + (i % 2) * 0.6))],
                                    ra=[0.32, 0.26, 0.12], sides=4, hint=(0, 0, 1), caps=(True, True)))
        if i % 3 == 0:
            F += part(f"stripknot{i}", ellipsoid(sh, "gold", tuple(p0 + np.asarray(P(x * 0.25, -ln - 0.2, 1.2))), (0.3, 0.4, 0.3), segs=6, rings=2))
    # 腕: 細い雲の袖、手首で広がり、黒いレースの袖口。白い手
    for s in (-1, 1):
        a_ = side_bone(s, "arm")
        x = s * 4.6
        F += part(f"sleeve{s}", tube(a_, "cloudw", [P(x, 28.2, 0), P(x + s * 0.5, 23.0, 0.2), P(x + s * 1.0, 18.4, -0.6)], ra=[1.1, 1.25, 1.9], sides=8, caps=(True, True)))
        F += part(f"cuff{s}", ellipsoid(a_, "lace", P(x + s * 1.0, 18.0, -0.7), (2.0, 0.5, 2.0), segs=10, rings=2))
        F += part(f"hand{s}", ellipsoid(a_, "skinp", P(x + s * 1.1, 16.8, -0.9), (0.7, 1.0, 0.55), segs=7, rings=3))
        for k, dz in enumerate((-0.4, 0.0, 0.4)):
            F += part(f"finger{s}{k}", tube(a_, "skinp", [P(x + s * 1.1, 16.0, -0.9 + dz), P(x + s * 1.15, 14.6, -1.2 + dz)], ra=[0.18, 0.1], sides=4, caps=(True, True)))
    # 頭: 首、黒いレースで覆った顔に白く光る描いた目、後ろへ反って上へ尖る長い頭巾、低く結んだ白い髪
    h = "head"
    F += part("neck", tube(h, "skinp", [P(0, 29.6, 0.1), P(0, 31.4, 0.0)], ra=0.8, sides=6, caps=(True, True)))
    F += part("skull", ellipsoid(h, "skinp", P(0, 33.4, 0.1), (1.7, 2.1, 1.9), segs=10, rings=5))
    F += part("face_veil", ellipsoid(h, "lace", P(0, 33.2, -1.25), (1.6, 2.0, 0.85), segs=10, rings=4))
    F += part("painted_eye", ellipsoid(h, "pale", P(0, 33.8, -2.05), (0.75, 0.38, 0.15), segs=8, rings=2))
    F += part("painted_pupil", ellipsoid(h, "lace", P(0, 33.8, -2.18), (0.25, 0.25, 0.08), segs=6, rings=2))
    hood = []
    for k in range(9):
        t = k / 8
        y = 31.0 + t * 14.0
        r = 2.9 * (1 - t) ** 0.9 + 0.15
        cz = 0.6 + 4.6 * t * t
        hood.append(ring_pts(y, r, r * 0.95, r * 1.05, n=10, cz=cz))
    F += part("hood", loft(h, "cloudw", hood, caps=(True, True)))
    F += part("hood_rim", loop(h, "lace", 0, 31.2, -0.2, 2.6, 2.2, r=0.35, n=12, gap=110, at=180, sides=4))
    F += part("hair", tube(h, "skinp", [P(0, 32.0, 2.0), P(0, 29.0, 2.8), P(0, 26.0, 2.6)], ra=[0.8, 0.6, 0.3], sides=6, caps=(True, True)))
    F += part("hair_clasp", loop(h, "gold", 0, 29.6, 2.75, 0.75, 0.75, r=0.2, n=8, gap=8, sides=4))
    # 見張りの竿 (pole の骨): 長い軸、金の照準の輪と小鐘、青銅の穂先、石突き
    pl = "pole"
    px, pz = 6.0, -1.8
    F += part("pole_shaft", tube(pl, "lace", [P(px, 2.0, pz), P(px, 17.6, pz), P(px, 38.0, pz)], ra=0.4, sides=6, caps=(True, True)))
    F += part("pole_ring", hoop(pl, "gold", px, 40.6, pz, 2.4, 2.4, r=0.35, n=14, gap=6))
    F += part("pole_ring2", loop(pl, "gold", px, 40.6, pz, 2.4, 2.4, r=0.22, n=14, gap=6, sides=4))
    F += part("pole_bell", loft(pl, "bronze", [ring_pts(41.6, 0.3, 0.3, 0.3, n=8, cx=px, cz=pz), ring_pts(40.4, 0.9, 0.9, 0.9, n=8, cx=px, cz=pz),
                                                ring_pts(39.2, 1.3, 1.3, 1.3, n=8, cx=px, cz=pz)], caps=(True, True)))
    F += part("pole_tip", cone(pl, "bronze", P(px, 43.0, pz), (0, 1, 0), 3.2, 0.55, sides=5, rings=2))
    F += part("pole_butt", cone(pl, "gold", P(px, 2.2, pz), (0, -1, 0), 1.6, 0.5, sides=5, rings=1))
    # 握り: 竿の骨の支点 (手の中) に太い革巻き。腕を振っても竿が手から離れて見えない (mesh_audit の joint detach)
    F += part("pole_grip", ellipsoid(pl, "lace", P(px, 17.6, pz), (1.0, 2.2, 1.0), segs=8, rings=4))
    # 三本の垂れ布は付け根を少しずつずらす (一点から出すと蓋が溶け合い、面の向きの直しが裏返る。2026-11-09)
    for k in range(3):
        F += part(f"pole_ribbon{k}", tube(pl, "lace", [P(px + 0.4 + k * 0.15, 38.4 - k * 0.5, pz + 0.1 * k), P(px + 1.4 + k * 0.4, 34.0 - k, pz + 0.4), P(px + 1.0 + k * 0.6, 29.0 - k * 1.5, pz + 0.8)],
                                          ra=[0.25, 0.2, 0.08], sides=4, hint=(0, 0, 1), caps=(True, True)))
    # 腰の小鐘 (hipbell)
    hb = "hipbell"
    F += part("hipbell", loft(hb, "bronze", [ring_pts(18.6, 0.3, 0.3, 0.3, n=8, cx=-4.0, cz=-0.6), ring_pts(17.4, 0.9, 0.9, 0.9, n=8, cx=-4.0, cz=-0.6),
                                             ring_pts(16.0, 1.3, 1.3, 1.3, n=8, cx=-4.0, cz=-0.6)], caps=(True, True)))
    F += part("hipbell_clap", ellipsoid(hb, "pale", P(-4.0, 15.6, -0.6), (0.35, 0.35, 0.35), segs=6, rings=2))
    return F


def main(argv):
    F = build()
    K.export("founder", F, BONES, tpu=3.0, crackglow=0.0, seed=5.0, center=(0, 24, 0), scale=4.6)
    if "--install" in argv:
        d = K.OUT / "founder"
        env = dict(os.environ)
        env["PYTHONPATH"] = os.pathsep.join([str(ROOT / "tools/lib"), str(ROOT / "tools/audit")])
        subprocess.check_call([sys.executable, str(ROOT / "tools/install/install_lowpoly.py"), "founder", "--mesh", str(d / "founder_mesh.json"),
                               "--atlas", str(d / "founder_atlas.png"), "--glow", str(d / "founder_glowmask.png")], cwd=ROOT, env=env)
        stages()


def stages():
    """姿の段の絵 (CloudMobRenderers が段 2・3 で <id>_s1・_s2 に替える)。同じ並びの絵を、段 2 は灰を濃く (雲が曇る)、段 3 はさらに暗く光を強く"""
    import numpy as np
    from PIL import Image
    tex = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"
    base = np.asarray(Image.open(tex / "founder.png").convert("RGBA")).astype(float)
    glow = Image.open(tex / "founder_glowmask.png")
    gm = np.asarray(glow.convert("RGBA"))[..., 3] > 40
    for suf, k, tint in (("_s1", 0.82, (0.94, 0.96, 1.04)), ("_s2", 0.62, (0.9, 0.9, 1.08))):
        a = base.copy()
        a[..., :3] = np.clip(a[..., :3] * k * np.array(tint), 0, 255)
        a[gm, :3] = base[gm, :3]
        Image.fromarray(a.astype(np.uint8), "RGBA").save(tex / f"founder{suf}.png")
        glow.save(tex / f"founder{suf}_glowmask.png")


if __name__ == "__main__":
    main(sys.argv[1:])
