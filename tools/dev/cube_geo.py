#!/usr/bin/env python3
"""元のキューブの geo とテクスチャ (geo/entity/<名>.geo.json・textures/entity/<名>.png) を、通常のジャーの外に出す・戻す。

通常は入っていない。低ポリの描画 (LowPoly.enabled = true) は使わないので、ジャーとリソースの読み込みが軽くなる。
ゲームの中で `lowpoly off` (キューブの比べ) をするとき、または tools/install の生成スクリプトがキューブを書き直すときに使う。

使い方 (リポジトリ直下で):
    python tools/dev/cube_geo.py status     # 今どちらに何個あるか
    python tools/dev/cube_geo.py back       # tools/sources/geo_cube/ から src/main/resources へ写す (ゲームに入れる。何度でも可)
    python tools/dev/cube_geo.py out        # src/main/resources から tools/sources/geo_cube/ へ移す (生成スクリプトが書き戻した分を外す)

back は写すだけ (tools 側は残す)。out は移す (resources 側は消える)。残すもの (KEEP) は両方とも触らない。
キューブが無い状態で `lowpoly off` にすると、落ちずに低ポリのまま描き、素材ごとに一度だけ警告を出す (LowPoly.cubeOr)。
"""
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
STORE = os.path.join(ROOT, "tools", "sources", "geo_cube")
# 通常のプレイで使うテクスチャ (キューブの絵ではない)。出さない
# (下の 8 枚は、コードが無効時の枝で読むので残した。2026-10-24 の確認: 参照があるのに無いと、絵の読み込みで警告が出る)
KEEP = {"textures/entity/bone_spike.png", "textures/entity/bubble_shot.png", "textures/entity/rebirth_back.png", "textures/entity/watcher_glint.png",
        "textures/entity/ash_smith_glowmask.png", "textures/entity/ash_smith_glowmask_white.png", "textures/entity/ash_thrall_flared.png",
        "textures/entity/coral_thrall_statue.png", "textures/entity/raiju_glowmask.png", "textures/entity/static_moth_charge.png",
        "textures/entity/watcher.png", "textures/entity/watcher_glowmask.png"}


def cube_files(base):
    """base 直下の geo/entity と textures/entity のうち、キューブのものの相対パス (assets/skyabyss からの)"""
    out = []
    geo = os.path.join(base, "geo", "entity")
    if os.path.isdir(geo):
        for f in sorted(os.listdir(geo)):
            if f.endswith(".geo.json") and "_lowpoly" not in f:
                out.append("geo/entity/" + f)
    tex = os.path.join(base, "textures", "entity")
    if os.path.isdir(tex):
        for f in sorted(os.listdir(tex)):
            if f.endswith(".png") and "textures/entity/" + f not in KEEP:
                out.append("textures/entity/" + f)
    return out


def copy(src_root, dst_root, rels, move):
    n = 0
    for rel in rels:
        src = os.path.join(src_root, *rel.split("/"))
        dst = os.path.join(dst_root, *rel.split("/"))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if move:
            shutil.move(src, dst)
        else:
            shutil.copyfile(src, dst)
        n += 1
    return n


def main(argv):
    cmd = argv[1] if len(argv) > 1 else "status"
    if cmd == "status":
        print("resources: %d cube files   tools/sources/geo_cube: %d cube files" % (len(cube_files(RES)), len(cube_files(STORE))))
    elif cmd == "back":
        print("copied %d files to %s" % (copy(STORE, RES, cube_files(STORE), False), RES))
    elif cmd == "out":
        print("moved %d files to %s" % (copy(RES, STORE, cube_files(RES), True), STORE))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
