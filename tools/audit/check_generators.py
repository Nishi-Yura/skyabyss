#!/usr/bin/env python3
"""生成スクリプトが、いまコミットされているデータを再現できるか (ドリフトの有無) を調べる。

使い方 (リポジトリ直下で):
    PYTHONPATH=tools/lib python tools/audit/check_generators.py            # 既定の一覧を全部
    PYTHONPATH=tools/lib python tools/audit/check_generators.py gen_story_quests gen_npc_content
    ... --diff   差分の中身も出す (DIFF_LINES=行数)  /  --keep   一時コピーを消さない / --list  一覧だけ出す / --paths src/main/resources docs/design

仕組み: tools/ ・ src/main/resources ・ docs を一時フォルダへコピーして git で固め、
生成スクリプトを 1 本ずつそのコピーの中で走らせ、書き換わったファイルを報告する (本物は触らない)。
終了コード: ドリフトまたは失敗があれば 1。
"""
import os, shutil, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# 出力が src/main/resources のデータ・lang に及ぶ生成スクリプト (ROOT からの相対パス)。
GENERATORS = """
tools/gen/story/gen_story_quests.py
tools/gen/story/gen_free_quests.py
tools/gen/story/gen_town_quests.py
tools/gen/story/gen_advancements.py
tools/gen/story/gen_ember_lore.py
tools/gen/story/gen_storm_lore.py
tools/gen/story/gen_gear_story.py
tools/gen/story/gen_trial_lang.py
tools/gen/story/gen_opening_text.py
tools/gen/mobs/gen_npc_content.py
tools/gen/items/gen_artisan_dialogue.py
tools/gen/ch1/gen_ch1_content.py
tools/gen/ch1/gen_ch1_prep.py
tools/gen/ch1/gen_ch1_trails.py
tools/gen/ch1/gen_ch1_travel.py
tools/gen/ch2/gen_ch2_data.py
tools/gen/ch3/gen_ch3_content.py
tools/gen/ch4/gen_ch4_content.py
tools/gen/ch5/gen_ch5_entrance.py
tools/gen/ch5/gen_ch5_layers.py
tools/gen/ui/gen_deep_skill_lang.py
tools/gen/world/gen_facility_content.py
tools/abyss/gen_abyss_depth_texts.py
tools/abyss/gen_abyss_layer_texts_a.py
tools/abyss/gen_abyss_layer_texts_b.py
tools/abyss/gen_abyss_nightmare_texts.py
tools/abyss/gen_abyss_relic_texts.py
""".split()

COPY = ["tools", "src/main/resources", "docs"]
WATCH = ["src/main/resources", "docs"]


def sh(args, cwd, **kw):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace", **kw)


TEXT = {".json", ".md", ".mcfunction", ".txt", ".mcmeta", ".py", ".lang", ".csv"}


def lf(top):
    """コピーを LF に揃える (本物は Windows の autocrlf で CRLF のことがある。生成物は LF で書かれる)。"""
    for dp, _, fs in os.walk(top):
        for f in fs:
            if os.path.splitext(f)[1].lower() in TEXT:
                p = os.path.join(dp, f)
                b = open(p, "rb").read()
                if b"\r\n" in b:
                    open(p, "wb").write(b.replace(b"\r\n", b"\n"))


def changed(tmp, watch):
    """書き換わったファイル。改行コード (CRLF/LF) だけの違いは無視する (Windows の autocrlf 対策)。"""
    sh(["git", "add", "-N", "--"] + watch, tmp)
    out = sh(["git", "diff", "--ignore-cr-at-eol", "--name-status", "--"] + watch, tmp).stdout.split("\n")
    return [m.replace("\t", " ") for m in out if m.strip()]


def main():
    argv = sys.argv[1:]
    keep = "--keep" in argv
    show_diff = "--diff" in argv
    watch = list(WATCH)
    if "--paths" in argv:
        i = argv.index("--paths"); watch = argv[i + 1:]; argv = argv[:i]
    names = [a for a in argv if not a.startswith("--")]
    gens = [g for g in GENERATORS if not names or any(n in g for n in names)]
    if "--list" in argv:
        print("\n".join(gens)); return 0
    tmp = Path(tempfile.mkdtemp(prefix="skyabyss_gencheck_"))
    try:
        for c in COPY:
            if (ROOT / c).exists():
                shutil.copytree(ROOT / c, tmp / c, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        lf(tmp)
        sh(["git", "init", "-q"], tmp)
        sh(["git", "config", "core.autocrlf", "false"], tmp)
        sh(["git", "add", "-A"], tmp)
        sh(["git", "-c", "user.name=x", "-c", "user.email=x@x", "commit", "-q", "-m", "base"], tmp)
        env = dict(os.environ, PYTHONPATH=str(tmp / "tools/lib"), PYTHONIOENCODING="utf-8")
        bad = 0
        for g in gens:
            t0 = time.time()
            r = sh([sys.executable, g], tmp, env=env, timeout=900)
            st = changed(tmp, watch)
            tag = "OK   " if r.returncode == 0 and not st else ("DRIFT" if st else "FAIL ")
            print(f"{tag} {g}  ({time.time() - t0:.0f}s)")
            if r.returncode != 0:
                bad += 1
                print("     exit", r.returncode, (r.stderr.strip().splitlines() or [""])[-1][:200])
            if st:
                bad += 1
                for line in st[:15]:
                    print("     ", line)
                if len(st) > 15:
                    print(f"      ... +{len(st) - 15} files")
            if st and show_diff:
                n = int(os.environ.get("DIFF_LINES", "60"))
                d = sh(["git", "diff", "--ignore-cr-at-eol", "-U0", "--"] + watch, tmp).stdout.splitlines()
                print("\n".join(x[:240] for x in d[:n]))
            sh(["git", "checkout", "-q", "--", "."], tmp)
            sh(["git", "clean", "-fdq"], tmp)
        print("drift/fail:", bad)
        return 1 if bad else 0
    finally:
        if keep:
            print("kept", tmp)
        else:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
