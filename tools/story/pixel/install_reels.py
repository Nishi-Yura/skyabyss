"""語りの紙芝居をゲームへ入れる (client/story/StoryReelScreen が流す)。

  python tools/story/pixel/install_reels.py [語りの名前 ...]   (名前を並べると、その語りの絵と並びだけ入れ直す。文は全部)

  絵: docs/review/story_pixel/<reel>_<panel>.png (240x135。opening.py ほかが描く) → assets/skyabyss/textures/gui/story/<reel>/<panel>.png
  並び: assets/skyabyss/story_reels/<reel>.json (絵の動き・語りを出し切ってから待つ秒・鳴らす音)
  文: assets/skyabyss_story_reel/lang/{ja_jp,en_us}.json の story.skyabyss.<reel>.<panel>
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "docs/review/story_pixel"
A = ROOT / "src/main/resources/assets"
sys.path.insert(0, str(Path(__file__).resolve().parent))

REELS = {
    "opening": [
        ("p1", "still", 2.0, None, "むかし、空と地のあいだに、ひとつの約束があった。", "Long ago, the sky and the earth kept a promise."),
        ("p2", "left", 2.2, "minecraft:entity.phantom.flap", "空には、月を引いて飛ぶ、古いものがいた。", "In the sky flew an old thing that dragged the moon behind it."),
        ("p3", "up", 2.2, "minecraft:entity.warden.heartbeat", "地の底には、世界をつなぎとめる心臓が脈打っていた。", "Deep under the earth beat a heart that held the world in place."),
        ("p4", "still", 2.4, None, "心臓は鼓動を四つに分け、巨神たちに預けた。", "The heart split its beat in four and gave it to the titans."),
        ("p5", "right", 2.4, None, "巨神は歌い、月を遠ざけた。人はその背に町を建てた。", "The titans sang, and kept the moon away. People built towns upon their backs."),
        ("p6", "still", 2.2, "minecraft:block.bell.use", "地上では、空見の一族が鐘を鳴らし、空を見張った。", "On the ground, the Skywatch family rang their bell and watched the sky."),
        ("p7", "down", 2.6, None, "……いま、鐘を鳴らす者はいない。月は、また近づいている。", "...No one rings the bell now. And the moon is drawing near again."),
        ("p8", "left", 2.4, None, "名もない島で、ひとりの旅人が目を覚ます。", "On a nameless island, a traveller wakes."),
        ("p9", "still", 3.0, "minecraft:item.book.page_turn", "「忘れないうちに書け。次は、もっと早く気づけるように。」", "\"Write it down before you forget. So that next time, you notice sooner.\""),
    ],
}
HINT = ("クリックで進む ・ Esc で飛ばす", "Click to continue · Esc to skip")


def chapter_reels():
    """章の変わり目の語り (chapters.py の CHAPTERS) を REELS の形に"""
    import chapters
    out = {}
    for ch, panels in chapters.CHAPTERS.items():
        out[f"chapter_{ch}"] = [(pid, motion, 2.4, sound, ja, en) for pid, fn, motion, sound, ja, en in panels]
    return out


def moon_end_reel():
    """六章の終わりの語り (moon_end.py の PANELS)"""
    import moon_end
    return {"moon_core_end": [(pid, motion, 2.6, sound, ja, en) for pid, fn, motion, sound, ja, en in moon_end.PANELS]}


def finale_ascend_reel():
    """終章の入口の語り (finale_ascend.py の PANELS)"""
    import finale_ascend
    return {"finale_ascend": [(pid, motion, 2.8, sound, ja, en) for pid, fn, motion, sound, ja, en in finale_ascend.PANELS]}


def main():
    REELS.update(chapter_reels())
    REELS.update(moon_end_reel())
    REELS.update(finale_ascend_reel())
    # 語りの名前を並べると、その語りの絵と並びだけを入れ直す (文はいつも全部書く。絵の元を消した語りを壊さないため)
    only = set(sys.argv[1:])
    lang = {"ja_jp": {"story.skyabyss.reel.hint": HINT[0]}, "en_us": {"story.skyabyss.reel.hint": HINT[1]}}
    for reel, panels in REELS.items():
        copy = not only or reel in only
        tex = A / "skyabyss/textures/gui/story" / reel
        tex.mkdir(parents=True, exist_ok=True)
        out = []
        for pid, motion, hold, sound, ja, en in panels:
            src = SRC / f"{reel}_{pid}.png"
            if copy:
                assert src.exists(), src
                shutil.copyfile(src, tex / f"{pid}.png")
            d = {"id": pid, "motion": motion, "hold": hold}
            if sound:
                d["sound"] = sound
            out.append(d)
            lang["ja_jp"][f"story.skyabyss.{reel}.{pid}"] = ja
            lang["en_us"][f"story.skyabyss.{reel}.{pid}"] = en
        j = A / "skyabyss/story_reels" / f"{reel}.json"
        j.parent.mkdir(parents=True, exist_ok=True)
        if copy:
            j.write_text(json.dumps({"_comment": "語りの紙芝居 (tools/story/pixel/install_reels.py)", "panels": out}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for code, d in lang.items():
        p = A / "skyabyss_story_reel/lang" / f"{code}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("installed", ", ".join(REELS))


if __name__ == "__main__":
    main()
