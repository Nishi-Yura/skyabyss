#!/usr/bin/env python3
"""NPC v2 (人ごとの形。tools/rough/lowpoly/npc2) をゲームへ入れる。先に python -I tools/rough/lowpoly/npc2/build.py で書き出しておく。

  python -I tools/install/install_npc2.py [名 ...]     (名を省くと書き出してある全員)

一人 (名 = 係の人の役 / survivor_<層> / abyss_porter / guest_<人>) につき書くもの:
  assets/skyabyss/lowpoly/npc_<名>.json                 体のメッシュ (root は回さない。前 = -Z)
  assets/skyabyss/textures/lowpoly/npc_<名>.png          体の絵
  assets/skyabyss/geo/entity/npc_<名>_lowpoly.geo.json   骨だけの geo (名前・親・pivot はメッシュと同じ)
  assets/skyabyss/animations/entity/npc_<名>.animation.json  動き (持ち方の型ごと。名前は animation.attendant.* / animation.town_guest.*)
  assets/skyabyss/lowpoly/npc_<名>_<受け口>.json + textures/lowpoly/npc_<名>_<受け口>.png (+ _glow.png)  持ち物 (受け口の pivot からの相対)
描画は client/npc/NpcModels (v2 のメッシュがある人だけ v2 になる。無い人は前の作りのまま)。
"""
from __future__ import annotations

import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "docs", "review", "npc_models_v2", "roughs")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
TEX = os.path.join(ASSETS, "textures", "lowpoly")
sys.path.insert(0, os.path.join(ROOT, "tools", "rough", "lowpoly", "npc2"))
sys.path.insert(0, os.path.join(ROOT, "tools", "install"))
sys.path.insert(0, os.path.join(ROOT, "tools", "lib"))
sys.path.insert(0, os.path.join(ROOT, "tools", "audit"))
import anim as AN  # noqa: E402
import mesh_close as MC  # noqa: E402

SOCKETS = ("prop_r", "prop_l", "prop_back")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def bake_glow(atlas_path, mask_path, out_path):
    from PIL import Image
    a = Image.open(atlas_path).convert("RGBA")
    m = Image.open(mask_path).convert("RGBA")
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    lit = 0
    ap, mp, op = a.load(), m.load(), out.load()
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            if mp[x, y][3] > 0:
                op[x, y] = ap[x, y]
                lit += 1
    if lit:
        out.save(out_path)
    elif os.path.exists(out_path):
        os.remove(out_path)
    return lit


def install(name, kind):
    """name の書き出し (docs/review/npc_models_v2/roughs/<name>/) を入れる。kind = 動きの型。"""
    d = os.path.join(SRC, name)
    full = "npc_" + name
    mesh = json.load(open(os.path.join(d, name + "_mesh.json"), encoding="utf-8"))
    mesh["name"] = full
    mesh["texture"] = full
    mesh["bones"]["root"]["rotation"] = [0.0, 0.0, 0.0]
    write_json(os.path.join(ASSETS, "lowpoly", full + ".json"), mesh)
    shutil.copyfile(os.path.join(d, name + "_atlas.png"), os.path.join(TEX, full + ".png"))
    for stale in (full + "_glowmask.png",):
        p = os.path.join(TEX, stale)
        if os.path.exists(p):
            os.remove(p)
    bones = []
    for n, b in mesh["bones"].items():
        e = {"name": n, "pivot": b["pivot"]}
        if b.get("parent"):
            e["parent"] = b["parent"]
        bones.append(e)
    desc = {"identifier": "geometry." + full + "_lowpoly", "texture_width": 16, "texture_height": 16,
            "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]}
    write_json(os.path.join(ASSETS, "geo", "entity", full + "_lowpoly.geo.json"),
               {"format_version": "1.12.0", "minecraft:geometry": [{"description": desc, "bones": bones}]})
    props = []
    for sock in SOCKETS:
        pm = os.path.join(d, f"{name}_{sock}_mesh.json")
        pname = f"{full}_{sock}"
        dst = os.path.join(ASSETS, "lowpoly", pname + ".json")
        if not os.path.exists(pm):
            for p in (dst, os.path.join(TEX, pname + ".png"), os.path.join(TEX, pname + "_glow.png")):
                if os.path.exists(p):
                    os.remove(p)
            continue
        g = json.load(open(pm, encoding="utf-8"))
        g["socket"] = True
        g["name"] = pname
        g["texture"] = pname
        write_json(dst, g)
        MC.close_mesh_file(dst, with_collar=False, force=True)
        atlas = os.path.join(d, f"{name}_{sock}_atlas.png")
        shutil.copyfile(atlas, os.path.join(TEX, pname + ".png"))
        mask = os.path.join(d, f"{name}_{sock}_glowmask.png")
        glow = os.path.join(TEX, pname + "_glow.png")
        lit = bake_glow(atlas, mask, glow) if os.path.exists(mask) else 0
        if not lit and os.path.exists(glow):
            os.remove(glow)
        props.append(f"{sock} ({g['triangle_count']} tris{', glow' if lit else ''})")
    prefix = "animation.town_guest" if name.startswith("guest_") else "animation.artisan" if name == "artisan" else "animation.attendant"
    # 絵だけ替える装い (旅の職人の章): textures/lowpoly/npc_<名>_<装い>.png
    import glob
    for vp in glob.glob(os.path.join(d, name + "_ch*_atlas.png")):
        v = os.path.basename(vp)[len(name) + 1:-len("_atlas.png")]
        shutil.copyfile(vp, os.path.join(TEX, f"{full}_{v}.png"))
    with open(os.path.join(ASSETS, "animations", "entity", full + ".animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(AN.animation_file(prefix, kind), f, ensure_ascii=False, indent=1)
    # 水密にする (開いた縁を塞ぎ、子の骨の付け根を親の中へ延ばす。関節の確かめは上の動きの回転幅で行う)
    mp = os.path.join(ASSETS, "lowpoly", full + ".json")
    raw = json.load(open(mp, encoding="utf-8"))
    st = MC.close_mesh_file(mp, with_collar=True, force=True)
    mesh = json.load(open(mp, encoding="utf-8"))
    # 襟 (付け根の延長) が隣の骨の側まで伸びた骨 (両脚が股で近いと起きる) は、襟なしで塞いだものに替える。
    # 脚の付け根は胴の中へ 1.4 単位以上沈めてあるので、襟がなくても関節から中は見えない
    plain, _ = MC.close_mesh(json.loads(json.dumps(raw)), with_collar=False)
    swapped = []
    for n in ("legL", "legR", "armL", "armR"):
        a = mesh["bones"][n]["vertices"]
        b = plain["bones"][n]["vertices"]
        if a and b:
            wa = max(v[0] for v in a) - min(v[0] for v in a)
            wb = max(v[0] for v in b) - min(v[0] for v in b)
            if wa > wb * 1.3 + 0.5:
                mesh["bones"][n] = plain["bones"][n]
                swapped.append(n)
    if swapped:
        mesh["triangle_count"] = sum(len(b.get("triangles", [])) for b in mesh["bones"].values())
        write_json(mp, mesh)
        st = dict(st, plain=swapped)
    print("%-24s %5d tris  %s  anim=%s  close=%s" % (full, mesh["triangle_count"], ", ".join(props) or "-", kind, st))


def main(names):
    import cast as C
    import cast_folk  # noqa: F401  (土地の町の 8 人: 選んだ案を guest_<人> として足す)
    import cast_moon  # noqa: F401  (六章の人: 選んだ案を guest_moon_<人> として足す)
    names = names or [n for n in C.CAST if os.path.exists(os.path.join(SRC, n, n + "_mesh.json"))]
    for n in names:
        install(n, C.CAST[n].get("anim", "free"))


if __name__ == "__main__":
    main(sys.argv[1:])
