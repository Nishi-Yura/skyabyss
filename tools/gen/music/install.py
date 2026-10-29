# -*- coding: utf-8 -*-
"""書き出した曲をゲームの音に入れる: sounds/music/<id>.ogg がある曲だけ sounds.json に music.<id> (stream) を書く。
曲の一覧は src/main/java/jp/revati/skyabyss/music/MusicTracks.java の ALL と同じ並び。
使い方: python tools/gen/music/install.py [<id> <ogg> ...]   (引数があれば、その ogg を sounds/music/<id>.ogg へ写してから書く)
"""
import io
import json
import os
import re
import shutil
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
JAVA = os.path.join(ROOT, "src", "main", "java", "jp", "revati", "skyabyss", "music", "MusicTracks.java")


def track_ids():
    src = io.open(JAVA, encoding="utf-8").read()
    block = src[src.index("ALL = List.of("):src.index(");", src.index("ALL = List.of("))]
    return re.findall(r'"([a-z_]+)"', block)


def main(args):
    for i in range(0, len(args) - 1, 2):
        shutil.copy(args[i + 1], os.path.join(ASSETS, "sounds", "music", args[i] + ".ogg"))
    path = os.path.join(ASSETS, "sounds.json")
    data = json.loads(io.open(path, encoding="utf-8").read())
    have = []
    for tid in track_ids():
        key = "music." + tid
        if os.path.exists(os.path.join(ASSETS, "sounds", "music", tid + ".ogg")):
            data[key] = {"sounds": [{"name": "skyabyss:music/" + tid, "stream": True}]}
            have.append(tid)
        else:
            data.pop(key, None)
    io.open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print("music in sounds.json:", ", ".join(have) or "(none)")


if __name__ == "__main__":
    main(sys.argv[1:])
