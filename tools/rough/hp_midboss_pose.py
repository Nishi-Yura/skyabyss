"""Pose check for the mid-boss high-poly options: now vs the recommended option, bones bent by hand (no animation file),
to look for gaps or sticking-out corners at the joints.   python tools/rough/hp_midboss_pose.py [name ...]
-> docs/review/high_poly_roughs/<name>_pose.png"""
import sys

sys.path.insert(0, "tools/rough")
import hp_midboss as H
import hp_view
import hpkit
import voxel

POSES = {
    "whalebone_captain": ("C", {"armR": (-70, 0, 0), "forearmR": (-60, 0, 0), "armL": (-30, 0, 15), "forearmL": (-20, 0, 0), "legL": (35, 0, 0), "legR": (-25, 0, 0), "head": (0, 25, 0)}),
    "tide_beckoner": ("C", {"claw_big": (0, 30, 0), "claw_big_pincer": (0, 0, 0), "claw_small": (0, -25, 0), "leg_r1": (0, 25, 0), "leg_l2": (0, -25, 0)}),
    "raiju": ("C", {"leg_fl": (30, 0, 0), "leg_fl_lo": (-40, 0, 0), "leg_br": (-25, 0, 0), "leg_br_lo": (30, 0, 0), "jaw": (25, 0, 0), "tail": (0, 20, 0), "tail_2": (0, -25, 0), "head": (0, 20, 0)}),
    "ash_smith": ("C", {"arm_r": (-80, 0, 0), "forearm_r": (-35, 0, 0), "arm_l": (-20, 0, 20), "forearm_l": (-50, 0, 0), "leg_r": (30, 0, 0), "leg_l": (-25, 0, 0)}),
}
VIEWS = [("front", 180, 10), ("3/4", 215, 20), ("side", 270, 8)]

for name in sys.argv[1:] or list(POSES):
    v, pose = POSES[name]
    cols = []
    for label, var in (("now", "now"), (f"option {v}", v)):
        m = H.BUILD[name](var)
        geo, tex, glow = m.finalize()
        gm = glow if (glow is not None and (glow[..., 3] > 0).any()) else None
        faces = voxel.load_geo(geo, tex, glowmask=gm, pose=pose)
        cols.append((label, [hp_view.shot(faces, y, p, (380, 380)) for _, y, p in VIEWS]))
    voxel.sheet(f"{name} posed ({', '.join(pose)})", cols).save(H.OUT / f"{name}_pose.png")
    print("wrote", name)
