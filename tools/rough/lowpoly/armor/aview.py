"""組み立て (部位ごとのアトラス)・描画 (プレイヤーの箱 + 防具)・保存。"""
from __future__ import annotations

import numpy as np
from PIL import Image

from akit import *  # noqa: F401,F403
from akit import mannequin, cull_inside, pose_faces, export_mesh, save_img, tri_count
import apaint
import scene  # weapons/scene.py (compose, view)

PLAT = (30, 34, 42)
SS = 2


def build_piece(faces, titan, rarity="common", plus=0):
    faces = cull_inside([f for f in faces if f is not None])
    lpkit.TEX = 2.0
    size, P, N, M, L = build_atlas(faces, 32, 0)
    atlas, glow = apaint.paint(faces, size, P, N, M, L, titan, rarity, plus)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def build_set(pieces, titan, rarity="common", plus=0):
    """pieces: {piece: [faces]} -> {piece: built}"""
    return {k: build_piece(v, titan, rarity, plus) for k, v in pieces.items()}


def set_tris(res):
    return sum(r["tris"] for r in res.values())


def scene_of(res, only=None, pose=None, mann=True):
    parts = [(r["faces"], r["atlas"], r["glow"]) for k, r in res.items() if only is None or k in only]
    flat = mannequin() if mann else []
    faces, atlas, glow = scene.compose(parts, flat)
    if pose:
        faces = pose_faces(faces, pose)
    return faces, atlas, glow


def render(res, titan, yaw, pitch, size, scale, center=(0, 18.5, 0), pose=None, only=None, mann=True, bg=PLAT):
    faces, atlas, glow = scene_of(res, only, pose, mann)
    im = scene.view(faces, atlas, glow, titan, yaw, pitch, size, center, scale, ss=SS)
    bgi = Image.new("RGBA", size, bg + (255,))
    bgi.alpha_composite(im)
    # プレビューは左右を戻す (armor_kit.render_set と同じ。右腕が画面の左)
    return bgi.convert("RGB").transpose(Image.FLIP_LEFT_RIGHT)


def save_set(res, base, titan_key, suffix="", mesh=True):
    base.mkdir(parents=True, exist_ok=True)
    for piece, r in res.items():
        stem = piece + suffix
        save_img(r["atlas"], base / f"{stem}_atlas.png")
        gm = np.zeros((r["size"], r["size"], 4), np.uint8)
        gm[r["glow"]] = (255, 255, 255, 255)
        save_img(gm, base / f"{stem}_glowmask.png")
        if mesh:
            export_mesh(r["faces"], r["size"], base / f"{stem}_mesh.json", f"{titan_key}_{stem}",
                        f"{stem}_atlas.png", f"{stem}_glowmask.png")
