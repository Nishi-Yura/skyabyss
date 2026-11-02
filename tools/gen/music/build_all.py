# -*- coding: utf-8 -*-
"""曲をまとめて書き出して検め、ゲームの音に入れる。

使い方: python tools/gen/music/build_all.py [<id> ...]   (無ければ全部)
- docs/review/music/<id>.ogg と <id>.png (音量の流れとスペクトログラム) を書く
- 検め: 伴奏だけの和音の合い方 (CHORD_PLAN があれば)、ループのつなぎ目の段差、割れ
- 入れる: src/main/resources/assets/skyabyss/sounds/music/<id>.ogg と sounds.json (install.py)
"""
import importlib
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

import check  # noqa: E402
import gm  # noqa: E402
import install  # noqa: E402
import synth as S  # noqa: E402

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
REVIEW = os.path.join(ROOT, "docs", "review", "music")
TRACKS = ["capital", "meadow", "island", "town_whale", "town_tide", "town_storm", "town_ember", "field_whale",
          "field_tide", "field_storm", "field_ember", "back", "boss", "heart", "abyss", "moon", "title"]


def one(tid):
    gm.use_soundfont()
    mod = importlib.import_module("track_" + tid)
    print("==", tid)
    y = mod.build()
    out = os.path.join(REVIEW, tid + ".ogg")
    S.write_ogg(out, y, S.QUALITY)
    y, _ = sf.read(out)
    print("  %.1f s, %.2f MB, peak %.2f" % (len(y) / S.SR, os.path.getsize(out) / 1e6, np.max(np.abs(y))))
    plan = getattr(mod, "CHORD_PLAN", None)
    if plan:
        acc = mod.build(mute=getattr(mod, "MELODY_GROUPS", ("melody",)))
        check.harmony_report(acc, mod)
    check.level_report(y, os.path.join(REVIEW, tid + ".png"))
    check.loop_report(y)
    return out


def main(ids):
    os.makedirs(REVIEW, exist_ok=True)
    args = []
    for tid in ids or TRACKS:
        if tid == "capital":
            continue
        args += [tid, one(tid)]
    install.main(args)


if __name__ == "__main__":
    main(sys.argv[1:])
