"""防具 4 部位の組み立て (様式の絵) と描画。"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE)); sys.path.insert(0, str(HERE.parent / "armor")); sys.path.insert(0, str(HERE.parent / "weapons"))
import aview  # noqa: E402
import spaint  # noqa: E402
from akit import lpkit, cull_inside, build_atlas, tri_count  # noqa: E402
from PIL import Image  # noqa: E402


def build_piece(faces, style):
    faces = cull_inside([f for f in faces if f is not None])
    lpkit.TEX = 2.0
    size, P, N, M, L = build_atlas(faces, 32, 0)
    atlas, glow = spaint.paint(faces, size, P, N, M, L, style)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def build_set(pieces, style):
    return {k: build_piece(v, style) for k, v in pieces.items()}


def render(res, yaw, pitch=-6, size=(300, 400), scale=9.5, only=None, center=(0, 18.5, 0), pose=None, mann=True):
    return aview.render(res, "whale", yaw, pitch, size, scale, center=center, only=only, pose=pose, mann=mann)


def tris(res):
    return sum(r["tris"] for r in res.values())
