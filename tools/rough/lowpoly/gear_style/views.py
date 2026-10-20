"""一人称 (透視) と 横から (全身・軌跡つき) の 2 つの見え方。"""
from __future__ import annotations

import copy

from anim import *  # noqa: F401,F403
from anim import pose_scene, Cam, view, Image, ImageDraw, np, V

EYE = V(0.0, 31.0, 9.0)
FD = 24.0


def persp(faces, eye=EYE, fd=FD, near=1.5):
    """目の位置から見た透視を、頂点の移し替えで作る (描画は正射影のまま)。目の後ろの面は捨てる。"""
    out = []

    def w(p):
        d = eye[2] - p[2]
        k = fd / d
        return V((p[0] - eye[0]) * k, eye[1] + (p[1] - eye[1]) * k, -d)

    for f in faces:
        if float((eye[2] - f.verts[:, 2]).min()) < near:
            continue
        g = copy.copy(f)
        g.verts = np.array([w(p) for p in f.verts])
        g.ref = w(f.ref)
        out.append(g)
    return out


def render_fp(model, kf, size=(200, 240), scale=7.0, hold="R", base_roll=60.0, cx=0.0, **kw):
    faces, atlas, glow, tip, mid = pose_scene(model, kf, hold, base_roll, use_body=False, **kw)
    faces = persp(faces)
    sh = kf.get("shake", (0, 0))
    cen = V(cx - sh[0] * 0.15, EYE[1] - 12.0 + sh[1] * 0.15, 0)
    im = view(faces, atlas, glow, "whale", 0.0, 0.0, size, cen, scale, ss=2, outline=True)
    bg = Image.new("RGBA", size, (30, 36, 46, 255))
    ImageDraw.Draw(bg).rectangle([0, int(size[1] * 0.80), size[0], size[1]], fill=(34, 42, 40, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def render_side(model, kf, size=(200, 240), scale=5.0, hold="R", base_roll=60.0, trail=None, **kw):
    faces, atlas, glow, tip, mid = pose_scene(model, kf, hold, base_roll, use_body=True, with_body=True, **kw)
    cam = Cam(yaw=-78.0, pitch=-6.0, size=size, center=(0, 20, -3), scale=scale)
    sh = kf.get("shake", (0, 0))
    im = view(faces, atlas, glow, "whale", cam.yaw, cam.pitch, size, cam.center + V(-sh[0] * 0.1, sh[1] * 0.1, 0), scale, ss=2, outline=True)
    bg = Image.new("RGBA", size, (30, 36, 46, 255))
    ImageDraw.Draw(bg).rectangle([0, int(size[1] * 0.88), size[0], size[1]], fill=(34, 42, 40, 255))
    if trail:
        ov = Image.new("RGBA", size, (0, 0, 0, 0))
        dr = ImageDraw.Draw(ov)
        pt = [cam.project(p) for p in trail["tip"]]
        pm = [cam.project(p) for p in trail["mid"]]
        col = trail["color"]
        dr.polygon(pt + pm[::-1], fill=col + (70,))
        dr.line(pt, fill=col + (210,), width=2)
        bg.alpha_composite(ov)
    bg.alpha_composite(im)
    return bg.convert("RGB")
