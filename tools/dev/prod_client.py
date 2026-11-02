#!/usr/bin/env python3
"""本番の形での起動確認 (docs/release.md の 2): NeoForge のインストーラーで入れたクライアントを、ランチャーなしで起動する小さな起動器。

    python -X utf8 tools/dev/prod_client.py <クライアントのフォルダ> [--script <台本>] [--username <名前>]

<クライアントのフォルダ> は `java -jar neoforge-<版>-installer.jar --installClient <フォルダ>` で入れたもの
(launcher_profiles.json を置いてから)。mods/ に配る jar を入れておく。
- versions/neoforge-*/ と継いだバニラの json を読み、足りないライブラリは json の URL (Mojang・NeoForge の配布元) から取る。
- 素材 (assets) は開発で取った gradle の置き場 (~/.gradle/caches/neoformruntime/assets) を使う (取り直さない)。
- --script を渡すと SKYABYSS_SHOTS に入れ、-Dskyabyss.prodcheck=true を付ける (撮影の台本は本番の jar ではこの印のときだけ動く。開発用のコマンドは使えない)。
- オフラインの名前で入る (一人遊びとオフラインのサーバーだけ)。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

ASSETS = Path.home() / ".gradle/caches/neoformruntime/assets"


def rules_ok(rules):
    if not rules:
        return True
    ok = False
    for r in rules:
        osr = r.get("os", {})
        match = (not osr or osr.get("name") == "windows") and not r.get("features")
        if match:
            ok = r["action"] == "allow"
    return ok


def load(root: Path, vid: str) -> dict:
    v = json.loads((root / "versions" / vid / f"{vid}.json").read_text(encoding="utf-8"))
    if "inheritsFrom" in v:
        parent = load(root, v["inheritsFrom"])
        merged = dict(parent)
        merged.update({k: v[k] for k in v if k not in ("libraries", "arguments")})
        merged["libraries"] = parent.get("libraries", []) + v.get("libraries", [])
        args = {"jvm": list(parent.get("arguments", {}).get("jvm", [])), "game": list(parent.get("arguments", {}).get("game", []))}
        for k in ("jvm", "game"):
            args[k] += v.get("arguments", {}).get(k, [])
        merged["arguments"] = args
        merged["jarId"] = parent.get("jarId", parent["id"])
        return merged
    v["jarId"] = v["id"]
    return v


def flat(args, subs):
    out = []
    for a in args:
        if isinstance(a, dict):
            if not rules_ok(a.get("rules")):
                continue
            val = a["value"]
            vals = val if isinstance(val, list) else [val]
        else:
            vals = [a]
        for s in vals:
            for k, v in subs.items():
                s = s.replace("${" + k + "}", v)
            out.append(s)
    return out


def main(argv):
    root = Path(argv[0]).resolve()
    script = argv[argv.index("--script") + 1] if "--script" in argv else None
    user = argv[argv.index("--username") + 1] if "--username" in argv else "prodcheck"
    vid = next(p.name for p in (root / "versions").iterdir() if p.name.startswith("neoforge-"))
    v = load(root, vid)
    libdir = root / "libraries"
    cp = []
    seen = set()
    for lib in v["libraries"]:
        if not rules_ok(lib.get("rules")):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if not art:
            continue
        path = libdir / art["path"]
        if not path.exists() and art.get("url"):
            path.parent.mkdir(parents=True, exist_ok=True)
            print("download", art["url"], flush=True)
            urllib.request.urlretrieve(art["url"], path)
        key = art["path"]
        if key not in seen and path.exists():
            seen.add(key)
            cp.append(str(path))
    # バニラの jar は載せない (NeoForge は libraries の中の作り直した jar を使う。載せると minecraft の包みが二つになる)
    subs = {"natives_directory": str(root / "natives"), "launcher_name": "skyabyss-prodcheck", "launcher_version": "1",
            "classpath": os.pathsep.join(cp), "library_directory": str(libdir), "classpath_separator": os.pathsep, "version_name": vid,
            "auth_player_name": user, "version_type": "release", "game_directory": str(root), "assets_root": str(ASSETS),
            "assets_index_name": v["assetIndex"]["id"], "auth_uuid": "00000000000000000000000000000001", "auth_access_token": "0",
            "clientid": "0", "auth_xuid": "0", "user_type": "legacy", "resolution_width": "1600", "resolution_height": "900"}
    jvm = ["-Xmx4G"] + (["-Dskyabyss.prodcheck=true"] if script else []) + flat(v["arguments"]["jvm"], subs)
    game = flat(v["arguments"]["game"], subs)
    # 実演・時間の決まりの引数は落とす (quickPlay などは置き換えの残りが出る)
    clean = []
    skip = False
    for i, a in enumerate(game):
        if skip:
            skip = False
            continue
        if a.startswith("--quickPlay") or a == "--demo":
            skip = a.startswith("--quickPlay")
            continue
        clean.append(a)
    env = dict(os.environ)
    if script:
        env["SKYABYSS_SHOTS"] = str(Path(script).resolve())
    (root / "natives").mkdir(exist_ok=True)
    cmd = ["java"] + jvm + [v["mainClass"]] + clean
    print("launch", vid, "with", len(cp), "libraries", flush=True)
    return subprocess.call(cmd, cwd=str(root), env=env)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
