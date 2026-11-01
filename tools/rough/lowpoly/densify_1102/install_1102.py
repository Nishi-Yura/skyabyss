# -*- coding: utf-8 -*-
"""密度上げの 33 体を、群の生成スクリプトの入力へ置いてから取り込む (2026-11-02)。

  python -I tools/rough/lowpoly/densify_1102/install_1102.py place    # 元のラフを控えて、after を生成元の入力の名前で置く
  python -I tools/rough/lowpoly/densify_1102/install_1102.py install  # 群ごとに、その群の生成スクリプトの install を 33 体だけに絞って流す
  python -I tools/rough/lowpoly/densify_1102/install_1102.py verify   # 取り込み後の三角形数を索引と照らす

元のラフは docs/review/roughs_1102_densify/<id>/before/rough_original/ に控える。
取り込み前の src/main/resources は git の HEAD に残っている（戻すなら git で）。
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / "docs" / "review" / "roughs_1102_densify"
RES = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss" / "lowpoly"
ENV = dict(os.environ, PYTHONIOENCODING="utf-8")

# (群, id, 案, 生成元の入力フォルダ)  入力フォルダは docs/review 相対。
MOBS = [
    ("moon", "shadow_stitcher", "c", "moon_roughs"), ("moon", "shade_strider", "a", "moon_roughs"),
    ("moon", "shell_hare", "b", "moon_roughs"), ("moon", "dust_crawler", "a", "moon_roughs"),
    ("moon", "rift_wing", "a", "moon_roughs"), ("moon", "cliff_creeper", "b", "moon_roughs"),
    ("moon", "moon_leaper", "a", "moon_roughs"), ("moon", "push_stone", "a", "moon_roughs"),
    ("cloud", "bell_mourner", "a", "cloud_roughs"), ("cloud", "mist_bird", "a", "cloud_roughs"),
    ("cloud", "backfire", "a", "cloud_roughs"), ("cloud", "ash_striker", "a", "cloud_roughs"),
    ("cloud", "fallen_plume", "a", "cloud_roughs"),
    ("abyssB", "soot_child", "a", "abyss_roughs"), ("abyssB", "rope_hanger", "b", "abyss_roughs"),
    ("abyssB", "lamp_eater", "a", "abyss_roughs"), ("abyssB", "past_shadow", "c", "abyss_roughs"),
    ("abyssB", "ceiling_walker", "a", "abyss_roughs"), ("abyssB", "ash_trail", "c", "abyss_roughs"),
    ("abyssB", "nameless_shape", "a", "abyss_roughs"),
    ("abyssA", "bone_picker", "a", "abyss_roughs"), ("abyssA", "charged_moth", "a", "abyss_roughs"),
    ("abyssA", "bone_flute", "a", "abyss_roughs"), ("abyssA", "white_shell_keeper", "a", "abyss_roughs"),
    ("ch3", "lightning_gleaner", "c", "ch3_roughs"), ("ch3", "static_puff", "b", "ch3_roughs"),
    ("ch3", "peak_fledgling", "b", "ch3_roughs"),
    ("ch4", "cinder_mite", "a", "ch4_roughs"),
    ("foes", "grain_rat", "a", "foes_one"), ("foes", "stray_wisp", "a", "foes_one"),
    ("ch1", "bone_gull", "a", "ch1_roughs"),
    ("ch2", "tidepool_clam", "a", "ch2_roughs"), ("ch2", "sand_burrower", "c", "ch2_roughs"),
]
SUFFIX = {"mesh": "_mesh.json", "atlas": "_atlas.png", "glow": "_glowmask.png"}
AFTER = {"mesh": "_after_mesh.json", "atlas": "_after_atlas.png", "glow": "_after_glowmask.png"}


def rough_dir(group, mid, opt, folder):
    if folder == "foes_one":
        return ROOT / "docs/review/foes_one" / mid / "a"
    if folder == "ch1_roughs":
        return ROOT / "docs/review/ch1_roughs" / mid / "a"
    return ROOT / "docs/review" / folder / mid / opt


def place():
    for group, mid, opt, folder in MOBS:
        d = rough_dir(group, mid, opt, folder)
        d.mkdir(parents=True, exist_ok=True)
        bk = OUT / mid / "before" / "rough_original"
        bk.mkdir(parents=True, exist_ok=True)
        stem = "%s_%s" % (mid, opt)
        for k in ("mesh", "atlas", "glow"):
            dst = d / (stem + SUFFIX[k])
            if dst.exists():
                shutil.copy2(dst, bk / dst.name)
            shutil.copy2(OUT / mid / "after" / (mid + AFTER[k]), dst)
        print("placed", mid, "->", d.relative_to(ROOT))
    # abyss B は絵を描き直すので、描き直す前の painted を控える
    for group, mid, opt, folder in MOBS:
        if group != "abyssB":
            continue
        bk = OUT / mid / "before" / "painted_original"
        bk.mkdir(parents=True, exist_ok=True)
        for p in (ROOT / "docs/review/abyss_painted").glob(mid + "_*"):
            shutil.copy2(p, bk / p.name)


def run(cmd, cwd=ROOT):
    print("$", " ".join(cmd))
    subprocess.run(cmd, cwd=str(cwd), env=ENV, check=True)


def gen_call(module_dir, module, body):
    code = ("import sys; sys.path.insert(0, %r); import %s as g; " % (str(module_dir), module)) + body
    return [sys.executable, "-I", "-c", code]


def install():
    by = {}
    for group, mid, opt, folder in MOBS:
        by.setdefault(group, []).append((mid, opt))
    ids = lambda g: [m for m, _ in by[g]]
    gen = ROOT / "tools" / "gen"
    # 月・雲海: 生成スクリプトの install を 33 体に絞って流す
    run(gen_call(gen / "ch6", "gen_moon_mobs", "g.install(only=set(%r))" % ids("moon")))
    run(gen_call(gen / "finale", "gen_cloud_mobs", "g.install(only=set(%r))" % ids("cloud")))
    # 深淵 B: 絵を描き直してから install
    run([sys.executable, "-I", str(ROOT / "tools/abyss/paint_abyss_b.py")] + ids("abyssB"))
    run(gen_call(gen / "abyss", "gen_abyss_mobs_b", "g.install(only=set(%r))" % ids("abyssB")))
    # 深淵 A: 種ごとに段 s1〜s3 を作って入れる（生成スクリプトは --only の一種ずつ）
    for mid in ids("abyssA"):
        run([sys.executable, "-I", str(gen / "abyss" / "gen_abyss_mobs_a.py"), "--only", mid])
    # 三章・四章・大陸・一章・二章
    run(gen_call(gen / "ch3", "gen_ch3_ground_mobs", "g.CHOICE = {%s}; g.install()" % ", ".join("%r: %r" % (m, o) for m, o in by["ch3"])))
    run(gen_call(gen / "ch4", "gen_ch4_ground_mobs", "g.CHOICE = {%s}; g.install()" % ", ".join("%r: %r" % (m, o) for m, o in by["ch4"])))
    run(gen_call(gen / "foes", "gen_foes_one", "g.MOBS = %r; g.install()" % ids("foes")))
    run(gen_call(ROOT / "tools" / "install", "install_ch1_back_mobs", "g.JOBS = [('bone_gull', 'bone_gull', 'a')]; g.main()"))
    run(gen_call(gen / "ch2", "gen_ch2_ground_mobs", "g.CHOICE = {%s}; g.install()" % ", ".join("%r: %r" % (m, o) for m, o in by["ch2"])))


def verify():
    rows = []
    for group, mid, opt, folder in MOBS:
        after = json.loads((OUT / mid / "after" / (mid + "_after_mesh_closed.json")).read_text(encoding="utf-8"))
        want = after["triangle_count"]
        names = [mid] if group != "abyssA" else ["abyss_%s_s%d" % (mid, s) for s in (1, 2, 3)]
        for n in names:
            got = json.loads((RES / (n + ".json")).read_text(encoding="utf-8"))["triangle_count"]
            rows.append((n, got, want, got == want))
    bad = [r for r in rows if not r[3]]
    for r in rows:
        print("%-34s installed %5d  after %5d  %s" % (r[0], r[1], r[2], "ok" if r[3] else "DIFF"))
    print("checked", len(rows), "files; mismatches", len(bad))
    return bad


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "place":
        place()
    elif cmd == "install":
        install()
    elif cmd == "verify":
        verify()
    else:
        print(__doc__)
