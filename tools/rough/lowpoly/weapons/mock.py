"""手に持った姿の模型 (一人称・三人称)。プレイヤーは平色の箱。向きと大きさは display の値の目安
(tools/lib/weapon_display.py の値そのものではない)。"""
from __future__ import annotations

import copy

from scene import *
from wkit import block, rot_x, rot_y, rot_z, V, nrm

SKIN = (196, 156, 126)
SHIRT = (64, 88, 120)
PANTS = (56, 54, 92)
HAIR = (58, 40, 32)
SHOE = (60, 60, 66)

# 武器ごとの持ち方: (腕を前へ上げる角度, 武器の軸が前へ倒れる角度, roll の y 回転, 三人称の倍率, 一人称の倍率, 握りの点)
POSES = {
    "whale": dict(arm=62, pitch=40, roll=90, s3=1.2, s1=0.7),
    "tide": dict(arm=78, pitch=76, roll=90, s3=1.3, s1=0.9),
    "thunder": dict(arm=84, pitch=0, roll=90, s3=0.9, s1=0.55),
    "ember": dict(arm=58, pitch=44, roll=-90, s3=1.0, s1=0.66),
}


def xf(faces, R, t, s, pivot):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = ((f.verts - pivot) * s) @ R.T + t
        g.normal = R @ f.normal
        g.ref = ((f.ref - pivot) * s) @ R.T + t
        out.append(g)
    return out


def body_parts(arm_deg, right_only=False):
    """プレイヤーの箱 (1 単位 = 1/16 ブロック。足元が y=0、正面 -Z)。右手 (+x 側) が武器を持つ。"""
    parts = []

    def bx(c, size, rgb, R=None):
        parts.append((block("b", c, size, rot=R), rgb))

    if not right_only:
        bx((0, 6, 0), (8, 12, 4), PANTS)
        bx((0, 18, 0), (8, 12, 4), SHIRT)
        bx((0, 28, 0), (8, 8, 8), SKIN)
        bx((0, 31.2, 0.3), (8.4, 1.8, 8.4), HAIR)
        bx((-6, 18, 0), (4, 12, 4), SKIN)
        bx((-2, 0.4, 0), (3.8, 1.0, 4.2), SHOE)
        bx((2, 0.4, 0), (3.8, 1.0, 4.2), SHOE)
    # 右腕: 肩 (6, 22) を軸に前へ上げる
    R = rot_x(arm_deg)
    sh = V(6, 22, 0)
    c = sh + R @ V(0, -6, 0)
    bx(c, (4, 12, 4), SKIN, R)
    hand = sh + R @ V(0, -11.0, 0)
    return parts, hand


FP = {
    "whale": lambda: rot_z(22) @ rot_x(-20),
    "tide": lambda: rot_z(14) @ rot_x(-58),
    "thunder": lambda: rot_y(90) @ rot_z(0),
    "ember": lambda: rot_z(20) @ rot_x(-22),
}


def third_person(faces, atlas, glow, titan, grip, size=(230, 300), yaw=240, pitch=-10):
    p = POSES[titan]
    parts, hand = body_parts(p["arm"])
    R = rot_x(-p["pitch"]) @ rot_y(p["roll"])
    wf = xf(faces, R, hand, p["s3"], V(*grip))
    allf, at_, gl_ = compose([(wf, atlas, glow)], parts)
    return view(allf, at_, gl_, titan, yaw, pitch, size, (0, 22, -10), 6.0)


def first_person(faces, atlas, glow, titan, grip, size=(300, 300), yaw=32, pitch=-14):
    p = POSES[titan]
    # 一人称 (模式): 右腕だけを後ろ上から。広い面がこちらを向く (今の display の rotation の向きの目安)
    parts, hand = body_parts(72, right_only=True)
    wf = xf(faces, FP[titan](), hand, p["s1"] * 1.25, V(*grip))
    allf, at_, gl_ = compose([(wf, atlas, glow)], parts)
    return view(allf, at_, gl_, titan, yaw, pitch, size, (6, 14, -10), 8.0)
