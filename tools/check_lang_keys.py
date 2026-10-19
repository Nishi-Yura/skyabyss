# コード中の翻訳キー (文字列リテラル) が ja_jp / en_us にあるかを調べる。LangSelfTest (4) と同じ規則。
# 使い方: python tools/check_lang_keys.py   (リポジトリ直下から)。見つからないキーを一覧する。
# 規則: 「<種類>.skyabyss[_名前空間].<名前>」の形の文字列リテラル (行コメントは除く)
import json, re, glob, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KEY = re.compile(r'"([a-z][A-Za-z]*\.skyabyss(?:_[a-z0-9_]+)?\.[A-Za-z0-9_.]*[A-Za-z0-9_])"(\s*\+)?')
# 偽陽性の許可リスト (理由つき)。LangSelfTest の ALLOW と同じ内容にする
ALLOW = {
    "sign.skyabyss.library_",  # TitanBackSelfTest: startsWith の頭 (WingsBuilder が番号を足す。実キーは lang にある)
    "sign.skyabyss.ch3_wind_",  # StormChapterSelfTest: startsWith の頭 (tabletKey(index) が実キーを作る。実キーは lang にある)
    "opening.skyabyss.hint_move",  # OpeningStorySelfTest: 「この生キーはもう無い」ことを確かめる否定の検査
}


def lang():
    keys = {"ja_jp": set(), "en_us": set()}
    for loc in keys:
        for p in glob.glob(os.path.join(ROOT, "src/main/resources/assets/*/lang/" + loc + ".json")):
            with open(p, encoding="utf-8") as f:
                keys[loc] |= set(json.load(f).keys())
    return keys


def scan():
    found = {}
    for p in glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True):
        with open(p, encoding="utf-8") as f:
            for n, line in enumerate(f, 1):
                s = line.strip()
                if s.startswith("//") or s.startswith("*") or s.startswith("/*"):
                    continue
                for m in KEY.finditer(line):
                    found.setdefault((m.group(1), bool(m.group(2))), []).append(os.path.relpath(p, ROOT).replace("\\", "/") + ":" + str(n))
    # データ (クエスト・開幕の部品など) の JSON の中の文字列。キーは "..." の値として出てくる
    for p in glob.glob(os.path.join(ROOT, "src/main/resources/data/**/*.json"), recursive=True):
        with open(p, encoding="utf-8") as f:
            for n, line in enumerate(f, 1):
                for m in KEY.finditer(line):
                    found.setdefault((m.group(1), False), []).append(os.path.relpath(p, ROOT).replace("\\", "/") + ":" + str(n))
    return found


def exists(key, prefix, names):
    if prefix:  # "key" + n の形: その頭のキーが一つでもあればよい
        return any(n.startswith(key) for n in names)
    return key in names or (key + ".1") in names  # 看板は key.1 ... key.4


if __name__ == "__main__":
    k = lang()
    f = scan()
    miss = {}
    for (key, prefix), where in sorted(f.items()):
        if key in ALLOW:
            continue
        for loc in ("ja_jp", "en_us"):
            if not exists(key, prefix, k[loc]):
                miss.setdefault((key, prefix), []).append(loc)
    for (key, prefix), locs in miss.items():
        print(key, "(prefix)" if prefix else "", locs, f[(key, prefix)][0], len(f[(key, prefix)]))
    print("scanned", len(f), "keys; missing", len(miss))
