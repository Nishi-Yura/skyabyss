#!/usr/bin/env python3
"""生成スクリプトが、いまコミットされているデータ (data/ と assets/*/lang) を再現できるか (ドリフトの有無) を調べる。

使い方 (リポジトリ直下で):
    PYTHONPATH=tools/lib python tools/audit/check_generators.py                 # 一覧 (GENERATORS) を順に走らせ、最後にコミット済みと違うファイルを出す
    PYTHONPATH=tools/lib python tools/audit/check_generators.py --isolated      # 1 本ずつ別々に走らせて、本ごとに出す
    PYTHONPATH=tools/lib python tools/audit/check_generators.py story_quests --diff   # 名前の一部で絞る / 差分の中身も出す (DIFF_LINES=行数)
    ... --list 一覧だけ / --keep 一時コピーを消さない / --strict JSON のキー順・PNG のバイト列まで同じを要求

仕組み: tools/ ・ src/main/resources ・ docs/design を一時フォルダへコピーして git で固め、生成スクリプトをそのコピーの中で走らせて、
書き換わったファイルを報告する (本物は触らない)。改行コードの違い (Windows の Python は CRLF で書く)・PNG の再圧縮・JSON のキー順は無視。
同じファイルを何本かが書くので、既定は一覧の順に続けて走らせた最後の状態を比べる (順番は一覧のとおり)。
終了コード: ドリフトまたは失敗があれば 1。説明は tools/README.md の「生成スクリプトとコミット済みデータの同期」。
"""
import os, shutil, subprocess, sys, tempfile, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# 出力が src/main/resources のデータ・lang に及ぶ生成スクリプト (ROOT からの相対パス)。
GENERATORS = """
tools/gen/world/gen_continent.py
""".split()

COPY = ["tools", "src/main/resources", "docs/design"]
# 見る場所: データ (data/) と文 (assets/*/lang)。モデル・テクスチャ・音は見ない (再エンコードで中身が同じでもバイトが変わるため)。
# docs/design の表 (07 など) は生成物の写しなので見ない (データが正)。
WATCH = ["src/main/resources/data", ":(glob)src/main/resources/assets/*/lang/**"]
RESTORE = ["src", "docs"]


def sh(args, cwd, **kw):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace", **kw)


TEXT = {".json", ".md", ".mcfunction", ".txt", ".mcmeta", ".py", ".lang", ".csv"}


def lf(top, only=None):
    """コピーを LF に揃える (本物は Windows の autocrlf で CRLF のことがある。生成物は LF で書かれる)。"""
    roots = [os.path.join(top, o) for o in only] if only else [top]
    for dp, _, fs in (x for r in roots for x in os.walk(r)):
        for f in fs:
            if os.path.splitext(f)[1].lower() in TEXT:
                p = os.path.join(dp, f)
                b = open(p, "rb").read()
                if b"\r\n" in b:
                    open(p, "wb").write(b.replace(b"\r\n", b"\n"))


def changed(tmp, watch):
    """書き換わったファイル。改行コード (CRLF/LF) だけの違い、PNG の再圧縮、JSON のキー順だけの違いは無視する (--strict で無効)。"""
    lf(tmp, ["src/main/resources", "docs/design"])  # Windows の Python はテキストを CRLF で書く。本物は autocrlf で LF に戻るので同じ扱いにする
    sh(["git", "add", "-N", "--"] + watch, tmp)
    out = sh(["git", "diff", "--ignore-cr-at-eol", "--name-status", "--"] + watch, tmp).stdout.split(chr(10))
    res = []
    for m in out:
        if not m.strip():
            continue
        st, _, path = m.partition(chr(9))
        if st == "M" and not STRICT and same_content(tmp, path):
            continue
        res.append(st + " " + path)
    return res


STRICT = False


def same_content(tmp, path):
    """バイト列は違っても中身が同じか。PNG は画素 (RGBA) が同じ、JSON は構文木が同じ (キー順は無視) なら同じ扱い。"""
    base = subprocess.run(["git", "show", "HEAD:" + path], cwd=tmp, capture_output=True).stdout
    cur = (Path(tmp) / path).read_bytes()
    ext = os.path.splitext(path)[1].lower()
    try:
        if ext == ".png":
            import io
            from PIL import Image
            a, b = Image.open(io.BytesIO(base)).convert("RGBA"), Image.open(io.BytesIO(cur)).convert("RGBA")
            return a.size == b.size and a.tobytes() == b.tobytes()
        if ext in (".json", ".mcmeta"):
            import json
            return json.loads(base.decode("utf-8")) == json.loads(cur.decode("utf-8"))
    except Exception:
        return False
    return False


def show(tmp, st):
    n = int(os.environ.get("DIFF_LINES", "60"))
    d = sh(["git", "diff", "--ignore-cr-at-eol", "-U0", "--"] + [x.split(" ", 1)[1] for x in st], tmp).stdout.splitlines()
    print(chr(10).join(x[:300] for x in d[:n]))


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    argv = sys.argv[1:]
    keep = "--keep" in argv
    global STRICT
    STRICT = "--strict" in argv
    show_diff = "--diff" in argv
    watch = list(WATCH)
    if "--paths" in argv:
        i = argv.index("--paths"); watch = argv[i + 1:]; argv = argv[:i]
    names = [a for a in argv if not a.startswith("--")]
    gens = [g for g in GENERATORS if not names or any(n in g for n in names)]
    if "--list" in argv:
        print("\n".join(gens)); return 0
    # 開発用: SKYABYSS_GENCHECK_DIR を指すと、そこに作った一時コピーを使い回す (2 回目からコピーを省く。生成スクリプトだけ最新に同期)
    reuse = os.environ.get("SKYABYSS_GENCHECK_DIR")
    tmp = Path(reuse) if reuse else Path(tempfile.mkdtemp(prefix="skyabyss_gencheck_"))
    keep = keep or bool(reuse)
    try:
        if reuse and (tmp / ".git").exists():
            for c in ("tools/gen", "tools/abyss", "tools/lib", "tools/trial_lang"):
                shutil.copytree(ROOT / c, tmp / c, dirs_exist_ok=True, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
        else:
            tmp.mkdir(parents=True, exist_ok=True)
            for c in COPY:
                if (ROOT / c).exists():
                    shutil.copytree(ROOT / c, tmp / c, dirs_exist_ok=True, ignore=shutil.ignore_patterns("__pycache__", "*.pyc"))
            lf(tmp)
            sh(["git", "init", "-q"], tmp)
            sh(["git", "config", "core.autocrlf", "false"], tmp)
            sh(["git", "add", "-A"], tmp)
            sh(["git", "-c", "user.name=x", "-c", "user.email=x@x", "commit", "-q", "-m", "base"], tmp)
        env = dict(os.environ, PYTHONPATH=str(tmp / "tools/lib"), PYTHONIOENCODING="utf-8")
        isolated = "--isolated" in sys.argv
        bad = 0
        seen = {}
        touched = {}  # ファイル -> それを書き換えた (コミット済みと違う状態にした) 生成スクリプト
        for g in gens:
            t0 = time.time()
            r = sh([sys.executable, g], tmp, env=env, timeout=900)
            st = changed(tmp, watch)
            for line in st:
                path = line.split(" ", 1)[1]
                try:
                    h = hash((tmp / path).read_bytes())
                except OSError:
                    h = None
                if seen.get(path) != h:  # この生成スクリプトが中身を変えたファイルだけ記録する
                    seen[path] = h
                    touched.setdefault(path, []).append(os.path.basename(g))
            if r.returncode != 0:
                bad += 1
                print(f"FAIL  {g}  exit {r.returncode}: " + (r.stderr.strip().splitlines() or [""])[-1][:200])
            elif isolated:
                print(f"{'DRIFT' if st else 'OK   '} {g}  ({time.time() - t0:.0f}s)")
                bad += bool(st)
                for line in st[:15]:
                    print("     ", line)
                if len(st) > 15:
                    print(f"      ... +{len(st) - 15} files")
                if st and show_diff:
                    show(tmp, st)
            else:
                print(f"ran   {g}  ({time.time() - t0:.0f}s)")
            if isolated:
                sh(["git", "checkout", "-q", "--"] + RESTORE, tmp)
                sh(["git", "clean", "-fdq", "--"] + RESTORE, tmp); sh(["git", "reset", "-q"], tmp)
        if not isolated:
            st = changed(tmp, watch)
            print()
            if not st:
                print("全生成スクリプトを順に走らせた結果が、コミット済みのデータと一致 (ドリフトなし)")
            for line in st:
                path = line.split(" ", 1)[1]
                print("DRIFT", line, " <- " + ",".join(dict.fromkeys(touched.get(path, ["?"]))))
            if st and show_diff:
                show(tmp, st)
            exp = os.environ.get("SKYABYSS_GENCHECK_EXPORT")  # ドリフトしたファイルの生成結果をここへ書き出す (差分を調べる用)
            if exp:
                for line in st:
                    path = line.split(" ", 1)[1]
                    if (tmp / path).exists():
                        os.makedirs(os.path.dirname(os.path.join(exp, path)), exist_ok=True)
                        shutil.copyfile(tmp / path, os.path.join(exp, path))
            bad += len(st)
            sh(["git", "checkout", "-q", "--"] + RESTORE, tmp)
            sh(["git", "clean", "-fdq", "--"] + RESTORE, tmp); sh(["git", "reset", "-q"], tmp)
        print("drift/fail:", bad)
        return 1 if bad else 0
    finally:
        if keep:
            print("kept", tmp)
        else:
            shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
