# 目的の帯の文が幅 230 px に入るかを調べる (ObjectiveBandSelfTest と同じ表・同じ規則。ゲームを立てずに文を直すとき用)。
# 使い方: python tools/audit/check_band_width.py   (リポジトリ直下から)
# 対象: メインのクエストの全目的 (+ .wait 版) と objective.skyabyss.* 。目印の付く目的は距離の分 (52 px) を引く。
import glob, json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WIDTH = 230


def glyph(c):
    if ord(c) >= 0x80:
        return 9
    if c == ' ':
        return 4
    if c in "!,.:;i|":
        return 2
    if c in "'l`":
        return 3
    if c in 'I[]t"(){}*':
        return 4
    if c in "fk<>":
        return 5
    if c in "@~":
        return 7
    return 6


def width(s):
    return sum(glyph(c) for c in s)


FAR = width("990m ") + 18 + 6
TARGETED = ["objective.skyabyss.examine_camp", "objective.skyabyss.follow_smoke", "objective.skyabyss.ask_storyteller",
            "objective.skyabyss.find_isana", "objective.skyabyss.bone_hill",
            "quest.skyabyss.main_song.board.", "quest.skyabyss.main_tide.see.", "quest.skyabyss.main_tide.board.",
            "quest.skyabyss.main_thunder.see.", "quest.skyabyss.main_thunder.board.", "quest.skyabyss.main_ember.see."]


def lang(loc):
    out = {}
    for p in glob.glob(os.path.join(ROOT, "src/main/resources/assets/*/lang/" + loc + ".json")):
        with open(p, encoding="utf-8") as f:
            out.update(json.load(f))
    return out


def main_objectives():
    keys = {}
    for p in glob.glob(os.path.join(ROOT, "src/main/resources/data/skyabyss/quest/**/*.json"), recursive=True):
        with open(p, encoding="utf-8") as f:
            d = json.load(f)
        if d.get("category") != "main":
            continue
        rel = os.path.relpath(p, os.path.join(ROOT, "src/main/resources/data/skyabyss/quest")).replace("\\", "/")[:-5]
        base = "quest.skyabyss." + rel.replace("/", ".")
        for st in d.get("stages", []):
            for ob in st.get("objectives", []):
                keys[base + "." + st["id"] + "." + ob["id"]] = ob.get("count", 1) > 1
    return keys


if __name__ == "__main__":
    ja, en = lang("ja_jp"), lang("en_us")
    keys = main_objectives()
    for k in list(keys):
        for suffix in (".wait", ".wait.prep", ".wait.lamps"):
            if k + suffix in ja or k + suffix in en:
                keys[k + suffix] = keys[k]
    for k in ja:
        if k.startswith("objective.skyabyss."):
            keys[k] = False
    bad = 0
    for k, counted in sorted(keys.items()):
        tg = any(k == t or (t.endswith(".") and k.startswith(t)) for t in TARGETED)
        for loc, tbl in (("ja", ja), ("en", en)):
            t = tbl.get(k)
            if t is None:
                print("MISSING", loc, k)
                bad += 1
                continue
            w = width("◆ " + t + ("（9/9）" if counted else ""))
            lim = WIDTH - FAR if tg else WIDTH
            if w > lim:
                bad += 1
                print(f"{loc} {k} {w}px > {lim}: {t}")
    print(len(keys), "keys; over", bad)
