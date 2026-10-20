"""一人称の攻撃の動き (模式) のコマ割り。腕は肩から回る箱、武器は握りを手に置く。
1 コマ = 1 つの姿勢 (キー)。コマの間の軌跡 (刃先と刃の中ほど) を補間して、残像 (smear) として重ねる。
座標: 1 単位 = 1/16 ブロック。プレイヤーは正面 -Z。見る向きは後ろから (yaw 0) で、主の手は画面の右。"""
from __future__ import annotations

import copy
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent / "weapons"))
from scene import *  # noqa: F401,F403,E402
from scene import compose, view  # noqa: E402
from mock import xf  # noqa: E402
from wkit import block, rot_x, rot_y, rot_z, V, nrm  # noqa: E402
import lpkit  # noqa: E402
from PIL import ImageDraw, ImageFont  # noqa: E402

SKIN = (196, 156, 126)
SLEEVE = (64, 88, 120)
CUFF = (196, 176, 130)
BG = (24, 27, 34)
SH_R = V(6.0, 22.0, 0.0)
SH_L = V(-6.0, 22.0, 0.0)
ARMLEN = 11.6
PIVOT = V(0, 14, 0)

FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def align_down(d):
    """-Y を d に合わせる回転。"""
    d = nrm(d)
    a = V(0, -1, 0)
    v = np.cross(a, d)
    c = float(np.dot(a, d))
    if np.linalg.norm(v) < 1e-8:
        return np.eye(3) if c > 0 else rot_z(180)
    K = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + K + K @ K * (1.0 / (1.0 + c))


def arm_faces(shoulder, R, sleeve=True):
    """肩から R で向いた腕 (袖 + 素肌)。"""
    out = []
    parts = [(block("a", (0, -3.0, 0), (4.4, 6.0, 4.4)), SLEEVE), (block("a", (0, -8.6, 0), (4.0, 5.6, 4.0)), SKIN),
             (block("a", (0, -5.9, 0), (4.6, 0.8, 4.6)), CUFF)]
    for fs, rgb in parts:
        out.append(([_xform(f, R, shoulder) for f in fs], rgb))
    return out


def _xform(f, R, t):
    g = copy.copy(f)
    g.verts = f.verts @ R.T + t
    g.normal = R @ f.normal
    g.ref = R @ f.ref + t
    return g


def euler_arm(ap, ay):
    return rot_y(ay) @ rot_x(ap)


def pose_scene(model, kf, hold="R", base_roll=60.0, two=None, bow_stage=None, string_mid=None, use_body=True, with_body=False):
    """model = dict(faces, atlas, glow, grip)。kf = キー (dict)。(faces, atlas, glow, tip, mid) を返す。"""
    sh_main = SH_R if hold == "R" else SH_L
    sh_off = SH_L if hold == "R" else SH_R
    Ra = euler_arm(kf["ap"], kf["ay"] * (1 if hold == "R" else -1))
    hand = sh_main + Ra @ V(0, -ARMLEN, 0)
    Rw = rot_y(kf.get("wy", 0) * (1 if hold == "R" else -1)) @ rot_x(-kf["wp"]) @ rot_z(kf.get("wr", 0)) @ rot_y(base_roll)
    grip = V(*model["grip"])
    wt = V(*kf.get("wt", (0, 0, 0)))
    hand_w = hand + wt
    wf = xf(model["faces"], Rw, hand_w, 1.0, grip)
    flat = []
    # 主の腕
    for fs, rgb in arm_faces(sh_main, Ra):
        flat.append((fs, rgb))
    # 副の腕
    if kf.get("off") == "weapon":
        tgt = hand_w + Rw @ V(0, kf.get("d2", 5.0), 0)
    elif kf.get("off") == "string":
        tgt = hand_w + Rw @ (string_mid - grip)
    else:
        tgt = sh_off + euler_arm(kf.get("lap", 14), kf.get("lay", 8) * (1 if hold == "R" else -1)) @ V(0, -ARMLEN, 0)
    d = tgt - sh_off
    Rl = align_down(d)
    for fs, rgb in arm_faces(sh_off, Rl):
        flat.append((fs, rgb))
    # 体の回転 (ひねり・のけぞり) を全体に掛ける
    twist, lean = (kf.get("tw", 0), kf.get("ln", 0)) if use_body else (0, 0)
    Rb = rot_y(twist) @ rot_x(lean)

    def body(fs):
        out = []
        for f in fs:
            g = copy.copy(f)
            g.verts = (f.verts - PIVOT) @ Rb.T + PIVOT
            g.normal = Rb @ f.normal
            g.ref = (f.ref - PIVOT) @ Rb.T + PIVOT
            out.append(g)
        return out

    wf = body(wf)
    flat = [(body(fs), rgb) for fs, rgb in flat]
    if with_body:
        flat += [(body(block("b", (0, 18, 0), (8, 12, 4))), (64, 88, 120)), (body(block("b", (0, 28, 0), (8, 8, 8))), SKIN),
                 (body(block("b", (0, 31.2, 0.3), (8.4, 1.8, 8.4))), (58, 40, 32)),
                 (block("b", (0, 6, 0), (8, 12, 4)), (56, 54, 92)), (block("b", (-2, 0.4, 0), (3.8, 1.0, 4.2)), (60, 60, 66)),
                 (block("b", (2, 0.4, 0), (3.8, 1.0, 4.2)), (60, 60, 66))]
    faces, atlas, glow = compose([(wf, model["atlas"], model["glow"])], flat)
    # 軌跡の点 (刃先 = 武器の最も高い点、中ほど = 握りと刃先の中点)
    tipl = V(*model["tip"])
    tip = Rb @ ((Rw @ (tipl - grip) + hand_w) - PIVOT) + PIVOT
    mid = Rb @ ((Rw @ ((tipl + grip) / 2 - grip) + hand_w) - PIVOT) + PIVOT
    return faces, atlas, glow, tip, mid


class Cam:
    def __init__(self, yaw=0.0, pitch=-6.0, size=(250, 300), center=(2.0, 16.0, -6.0), scale=7.2):
        self.yaw, self.pitch, self.size, self.center, self.scale = yaw, pitch, size, np.asarray(center, float), scale
        self.V = rot_x(pitch) @ rot_y(yaw)

    def project(self, p, shake=(0, 0)):
        cv = self.V @ np.asarray(p, float)
        cen = self.V @ self.center
        return ((cv[0] - cen[0]) * self.scale + self.size[0] / 2 + shake[0], -(cv[1] - cen[1]) * self.scale + self.size[1] / 2 + shake[1])


def render_frame(model, kf, cam, hold="R", base_roll=60.0, trail=None, **kw):
    faces, atlas, glow, tip, mid = pose_scene(model, kf, hold, base_roll, **kw)
    shake = kf.get("shake", (0, 0))
    c = cam.center + V(-shake[0] / cam.scale, shake[1] / cam.scale, 0)
    im = view(faces, atlas, glow, "whale", cam.yaw, cam.pitch, cam.size, c, cam.scale, ss=2, outline=True)
    bg = Image.new("RGBA", cam.size, BG + (255,))
    # 軌跡 (残像) は武器の後ろに敷く
    if trail:
        ov = Image.new("RGBA", cam.size, (0, 0, 0, 0))
        dr = ImageDraw.Draw(ov)
        pts_t = [cam.project(p, (-shake[0], shake[1]) if False else (0, 0)) for p in trail["tip"]]
        pts_m = [cam.project(p) for p in trail["mid"]]
        col = trail["color"]
        if len(pts_t) > 1:
            dr.polygon(pts_t + pts_m[::-1], fill=col + (70,))
            dr.line(pts_t, fill=col + (200,), width=2)
            dr.line(pts_m, fill=col + (90,), width=1)
        bg.alpha_composite(ov)
    bg.alpha_composite(im)
    return bg.convert("RGB"), (tip, mid)


def trail_points(model, keys, i0, i1, hold="R", base_roll=60.0, n=6, **kw):
    """キー i0 から i1 の間を n 分割して刃先と中ほどの軌跡を返す。"""
    tips, mids = [], []
    for t in np.linspace(0, 1, n * (i1 - i0) + 1):
        k = lerp_key(keys, i0 + t * (i1 - i0))
        _, _, _, tip, mid = pose_scene(model, k, hold, base_roll, **kw)
        tips.append(tip); mids.append(mid)
    return dict(tip=tips, mid=mids)


NUM = ("ap", "ay", "wp", "wy", "wr", "tw", "ln", "d2", "lap", "lay")


def lerp_key(keys, x):
    i = int(math.floor(x))
    i = max(0, min(len(keys) - 2, i))
    t = x - i
    a, b = keys[i], keys[i + 1]
    t = t * t * (3 - 2 * t)
    out = dict(a)
    for k in NUM:
        if k in a or k in b:
            out[k] = a.get(k, 0) * (1 - t) + b.get(k, 0) * t
    if "wt" in a or "wt" in b:
        wa, wb = np.array(a.get("wt", (0, 0, 0)), float), np.array(b.get("wt", (0, 0, 0)), float)
        out["wt"] = tuple(wa * (1 - t) + wb * t)
    out["off"] = a.get("off") if t < 0.5 else b.get("off")
    return out
