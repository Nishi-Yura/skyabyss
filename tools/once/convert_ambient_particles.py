# -*- coding: utf-8 -*-
"""残りの粒 (景色・仕掛け・罠・演出) を docs/design/59 の決まりに直す (一度だけ流す変換)。

使い方: python -I tools/once/convert_ambient_particles.py
  場所から属性を決める: 潮殻・二章 = 水 / 雷翼・三章 = 雷 / 余燼・四章 = 熾 / 岩鯨・一章 = 石 / 五章・深淵 = 深淵 /
                         六章・月・塔 = 月 / 終章 = 紙 / ほかは元の粒の意味から (AUTO)
  1. 粒を出す文を convert_attack_particles と同じ決まりで直す (当たりの粒は消す)
  2. 関数に渡している粒 (ParticleTypes.X) も置き換える。比べる所 (== / !=) と import は残す
  除く: ダンジョンの敵 (dungeon/mob。息の目印に ParticleTypes を比べて使う)、自己テスト、mod の粒そのもの
"""
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.argv = ["x", "AUTO"]
import convert_attack_particles as C  # noqa: E402

ROOT = os.path.join(HERE, "..", "..", "src", "main", "java", "jp", "revati", "skyabyss")
FX = C.FX


def element_for(rel):
    r = rel.replace("\\", "/")
    rules = [
        (("/tide/", "story/ch2/"), "WATER"),
        (("/storm/", "story/ch3/"), "STORM"),
        (("/ember/", "story/ch4/"), "EMBER"),
        (("/whale/", "story/ch1/", "titan/dungeon/", "StoneWhale", "WhaleChain", "WhaleKeeper"), "STONE"),
        (("story/ch5/", "abyss/"), "ABYSS"),
        (("story/ch6/", "tower/", "Moon"), "MOON"),
        (("story/finale/",), "PAPER"),
    ]
    for keys, el in rules:
        if any(k in r for k in keys):
            return el
    return "AUTO"


def excluded(rel):
    r = rel.replace("\\", "/")
    return (r.startswith("dungeon/mob/") or "SelfTest" in r or r.startswith("fx/") or r.startswith("client/fx/")
            or r.endswith("client/combat/AttackFx.java"))


def replace_args(path, el):
    t = io.open(path, encoding="utf-8").read()
    t = re.sub(r"new (?:net\.minecraft\.core\.particles\.)?BlockParticleOption\((?:net\.minecraft\.core\.particles\.)?ParticleTypes\.BLOCK,\s*[^;]*?defaultBlockState\(\)\)",
               lambda m: "%s.type(%s.Element.%s, %s.Kind.SHARD)" % (FX, FX, "STONE" if el == "AUTO" else el, FX), t)

    def rep(m):
        expr = "ParticleTypes." + m.group(1)
        e = C.auto_element(expr) if el == "AUTO" else el
        return "%s.type(%s.Element.%s, %s.Kind.%s)" % (FX, FX, e, FX, C.kind_of(expr))
    out = []
    for line in t.split("\n"):
        if line.lstrip().startswith("import ") or re.search(r"[=!]=\s*(?:net\.minecraft\.core\.particles\.)?ParticleTypes\.", line) \
                or re.search(r"ParticleTypes\.[A-Z_]+\s*[=!]=", line) or "case " in line and "ParticleTypes." in line:
            out.append(line)
            continue
        out.append(re.sub(r"(?:net\.minecraft\.core\.particles\.)?ParticleTypes\.([A-Z_]+)(?![\w(])", rep, line))
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out))


def main():
    C.DROP_IMPACT = True
    files = []
    for d, _, fs in os.walk(ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, ROOT)
            if excluded(rel):
                continue
            s = io.open(p, encoding="utf-8").read()
            if "ParticleTypes." in s or "DustParticleOptions(" in s:
                files.append((rel, p))
    for rel, p in sorted(files):
        el = element_for(rel)
        C.ELEMENT = el
        C.convert(p)
        replace_args(p, el)
    print(len(files), "files")


if __name__ == "__main__":
    main()
