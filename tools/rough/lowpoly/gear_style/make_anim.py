"""一人称の動きのコマ割りを描く (上段 = 一人称 (透視)、下段 = 横から全身 + 刃先の軌跡)。
実行: python make_anim.py <out_dir> [A B C] [--only class]
出力: <out_dir>/anim_<style>_<class>.png (8 コマ x 2 段)"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from anim import *  # noqa: F401,F403,E402
from anim import trail_points, font  # noqa: E402
from views import render_fp, render_side  # noqa: E402
import rend  # noqa: E402
import seqs  # noqa: E402
import d_thunder as T  # noqa: E402

FPX = dict(greatsword=2.5, spear=1.0, bow=-1.0, staff=2.0, dagger=5.0)
TRAIL = dict(A=(235, 238, 245), B=(150, 230, 255), C=(235, 225, 150))
SCALES = dict(greatsword=(4.4, 4.6), spear=(3.8, 4.0), bow=(5.0, 4.6), staff=(3.9, 4.0), dagger=(6.0, 5.6))


def model_of(mod, style, cls, stage=0):
    faces = getattr(mod, cls)(1, stage) if cls == "bow" else getattr(mod, cls)(1)
    r = rend.make(faces, style)
    r["grip"] = mod.GRIP[cls]
    r["tip"] = seqs.TIPS[cls]
    return r


def _kw(cls, st=0):
    if cls == "bow":
        return dict(string_mid=V(T.STRING_X[st], T.CY, 0) if st > 0 else None)
    return {}


def strip(style, cls, out_dir):
    mod = importlib.import_module("w_" + style.lower())
    S = seqs.SEQ[(cls, style)]
    keys = S["keys"]
    models = {st: model_of(mod, style, cls, st) for st in (range(4) if cls == "bow" else [0])}
    tr = {}
    for nm in ("smear", "smear2"):
        if S.get(nm) and cls != "bow":
            a, b = S[nm]
            tr[(a, b)] = trail_points(models[0], keys, a, b, S["hold"], S["roll"])
    W, H, cap = 200, 240, 54
    sheet = Image.new("RGB", (8 * W, 2 * H + cap), (18, 20, 26))
    d = ImageDraw.Draw(sheet)
    sc = SCALES[cls]
    for i, k in enumerate(keys):
        st = k.get("stage", 0)
        kw = _kw(cls, st)
        trail = None
        for (a, b), t in tr.items():
            if a < i <= b:
                trail = dict(t, color=TRAIL[style])
        fp = render_fp(models[st], k, (W, H), sc[0], S["hold"], S["roll"], cx=FPX[cls], **kw)
        sd = render_side(models[st], k, (W, H), sc[1], S["hold"], S["roll"], trail=trail, **kw)
        x = i * W
        sheet.paste(fp, (x, 0))
        sheet.paste(sd, (x, H))
        d.rectangle([x, 0, x + W - 1, H - 1], outline=(52, 58, 70))
        d.rectangle([x, H, x + W - 1, 2 * H - 1], outline=(52, 58, 70))
        d.text((x + 6, 2 * H + 4), f"{i + 1} {k['n']}", font=font(13), fill=(232, 234, 240))
        d.text((x + 6, 2 * H + 30), f"{k['t']} tick / {k['t'] * 50} ms", font=font(12), fill=(150, 160, 176))
    d.text((6, 6), "一人称", font=font(13), fill=(190, 200, 215))
    d.text((6, H + 6), "横から (全身・軌跡つき)", font=font(13), fill=(190, 200, 215))
    out = Path(out_dir) / f"anim_{style}_{cls}.png"
    sheet.save(out)
    return out


if __name__ == "__main__":
    args = [a for a in sys.argv[2:] if not a.startswith("--")]
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]
        args = [a for a in args if a != only]
    for s in (args or ["A", "B", "C"]):
        for cls, _ in seqs.CLASSES:
            if only and cls != only:
                continue
            print(strip(s, cls, sys.argv[1]), flush=True)
