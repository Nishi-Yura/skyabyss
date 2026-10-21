"""墓 (skyabyss:grave) の絵・モデル・語り部の救いの会話・文を作る。docs/design/11_record_screen.md の 6 章。

  textures/block/grave_stone.png : 苔むした石 (32x32)。灰緑の石に苔の斑
  textures/block/grave_glow.png  : 積み石に埋めた、かすかに光る青緑の印 (32x32。文字ではなく読めない古い印)
  blockstates/grave.json、models/block/grave.json : 三段の積み石 (下 12x4x12、中 9x3x9、上 5x2x5)。中の段の四方に光る印の板
  data/skyabyss/dialogue/storyteller_aa_grave.json   : 語り部の話題「取りに行けない墓がある」と救いの節 (storyteller に足し書き)
  assets/skyabyss_grave/lang/{ja_jp,en_us}.json   : 墓・知らせ・語り部・控え帳の文

使い方 (リポジトリ直下): python tools/gen_grave_assets.py
重なる面は作らない (光る印の板は外側の面だけ。中の段の面にぴったり付けるが、向きが逆なので見えない面は持たない)。
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/skyabyss"
TEX = ASSETS / "textures/block"
S = 32

STONE_DARK = np.array([58, 64, 62], float)
STONE_LIGHT = np.array([112, 120, 114], float)
MOSS_DARK = np.array([48, 84, 44], float)
MOSS_LIGHT = np.array([96, 140, 70], float)
GLOW = np.array([127, 214, 208], float)
GLOW_DARK = np.array([30, 52, 56], float)


def noise(seed, cells):
    r = np.random.default_rng(seed)
    n = r.random((S, S))
    big = np.asarray(Image.fromarray((r.random((cells, cells)) * 255).astype(np.uint8)).resize((S, S), Image.BICUBIC), float) / 255.0
    return np.clip(n * 0.45 + big * 0.65, 0, 1)


def stone():
    t = noise(21, 6)
    img = STONE_DARK + (STONE_LIGHT - STONE_DARK) * t[..., None]
    # 苔: 低い周波数の斑の上位を苔の色に (下ほど濃く)
    m = noise(33, 4)
    yy = np.linspace(0.25, -0.15, S)[:, None]
    mask = (m + yy) > 0.62
    mt = noise(47, 8)
    moss = MOSS_DARK + (MOSS_LIGHT - MOSS_DARK) * mt[..., None]
    img[mask] = moss[mask]
    # 石の目地: 横に二本、短い縦の線
    for y in (10, 21):
        img[y, :] *= 0.78
    for y0, x in ((0, 9), (11, 22), (22, 6)):
        img[y0:y0 + 10, x] *= 0.8
    return img


def glow():
    img = np.array(GLOW_DARK)[None, None, :].repeat(S, 0).repeat(S, 1) * (0.8 + 0.4 * noise(5, 6)[..., None])
    # 縁
    img[0:2, :] *= 0.7
    img[-2:, :] *= 0.7
    img[:, 0:2] *= 0.7
    img[:, -2:] *= 0.7
    # 刻み: 縦の線と短い横線を組み合わせた古い印 (読めない)
    r = np.random.default_rng(9)
    for y0 in (6, 14, 22):
        x = 5
        while x < S - 8:
            k = r.integers(0, 3)
            if k == 0:
                img[y0:y0 + 5, x] = GLOW
                x += 4
            elif k == 1:
                img[y0 + 1, x:x + 4] = GLOW
                img[y0 + 2, x + 1] = GLOW
                x += 6
            else:
                for d in range(5):
                    img[y0 + d, x + (d % 2)] = GLOW
                x += 5
    g = (img[..., 1] > 200)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = np.roll(np.roll(g, dy, 0), dx, 1) & ~g
        img[sh] = img[sh] * 0.55 + GLOW * 0.45
    return img


def save(name, img):
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(TEX / name)


def box(x0, y0, z0, x1, y1, z1, tex, faces=("north", "south", "east", "west", "up", "down")):
    def uv(face):
        if face in ("north", "south"):
            return [x0, 16 - y1, x1, 16 - y0]
        if face in ("east", "west"):
            return [z0, 16 - y1, z1, 16 - y0]
        return [x0, z0, x1, z1]
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {f: {"uv": uv(f), "texture": tex} for f in faces}}


def model():
    el = [
        box(2, 0, 2, 14, 4, 14, "#stone"),
        box(3.5, 4, 3.5, 12.5, 7, 12.5, "#stone"),
        box(5.5, 7, 5.5, 10.5, 9, 10.5, "#stone"),
        # 光る印の板 (外側の面だけ)
        box(6, 4.6, 3.4, 10, 6.4, 3.5, "#glow", ("north",)),
        box(6, 4.6, 12.5, 10, 6.4, 12.6, "#glow", ("south",)),
        box(3.4, 4.6, 6, 3.5, 6.4, 10, "#glow", ("west",)),
        box(12.5, 4.6, 6, 12.6, 6.4, 10, "#glow", ("east",)),
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": True,
            "textures": {"particle": "skyabyss:block/grave_stone", "stone": "skyabyss:block/grave_stone", "glow": "skyabyss:block/grave_glow"},
            "elements": el}


# ---------------------------------------------------------------- 会話
# 潮見の貝をなくした人 (二章以降で、どこにも持っていない)。選択肢の先 ch2_conch_ask は tools/gen_ch2_data.py が書く
CONCH_IF = {"type": "all", "of": [
    {"type": "stage", "min": 1},
    {"type": "no_keepsake", "item": "skyabyss:tide_watching_shell"},
]}
LOST_IF = {"type": "any", "of": [{"type": "grave"}, CONCH_IF]}


def dialogue():
    return {
        "npc": "skyabyss:storyteller",
        "extends": True,
        "add_options": {
            "menu": {
                "before": "bye",
                # 話題の枠は 13 まで (会話の枠の高さ)。墓と潮見の貝 (二章の語り部の足し書き。ch2/storyteller.json) は一つの話題「なくしたものがある」にまとめる
                "options": [{"id": "grave", "kind": "give", "if": LOST_IF, "goto": "lost_ask"}],
            }
        },
        "nodes": {
            "lost_ask": {
                "face": "grave",
                "options": [
                    {"id": "grave", "kind": "give", "if": {"type": "grave"}, "goto": "grave_ask"},
                    {"id": "conch", "kind": "give", "if": CONCH_IF, "goto": "ch2_conch_ask"},
                    {"id": "no", "kind": "talk", "goto": "menu"},
                ],
            },
            "grave_ask": {
                "face": "grave",
                "options": [
                    # 「お願いする」は墓の一覧の画面を開いて会話を終える (どの墓かは画面で選ぶ。GraveChooser)
                    {"id": "yes", "kind": "give", "if": {"type": "grave"}, "do": {"type": "grave_choose"}},
                    {"id": "no", "kind": "talk", "goto": "menu"},
                ],
            },
        },
    }


JA = {
    "block.skyabyss.grave": "墓",
    "skyabyss_grave.made": "倒れた場所のそばに、墓が立った。%s（%s, %s, %s）。控え帳（B）に場所が載っている",
    "skyabyss_grave.kept_all": "持ち物は手もとに残った",
    "skyabyss_grave.not_yours": "%s の墓だ。開けられない",
    "skyabyss_grave.collected": "墓の品を取り戻した",
    "skyabyss_grave.collected_part": "一部を取り戻した（%2$s 種類は墓に残る）",
    "skyabyss_grave.expired": "時の経った墓が崩れて、中身が地面に散らばった",
    "skyabyss_grave.rescue_none": "呼び戻せる墓はない",
    "skyabyss_grave.rescued": "語り部の呼びかけで、品 %s 個と経験値 %s が戻った（墓の %s％）。墓は土に還った",
    "skyabyss_grave.rescued_pending": "入りきらなかった %s 個は、報酬の預かりに入れた。控え帳から受け取れる",
    "dialogue.skyabyss.storyteller.menu.grave": "なくしたものがある。",
    "dialogue.skyabyss.storyteller.lost_ask": "……なくしたのかい。何をだい。",
    "dialogue.skyabyss.storyteller.lost_ask.grave": "取りに行けない墓がある。",
    "dialogue.skyabyss.storyteller.lost_ask.conch": "潮見の貝をなくしてしまった。",
    "dialogue.skyabyss.storyteller.lost_ask.no": "やめておく。",
    "dialogue.skyabyss.storyteller.grave_ask": "……墓かい。土の下の荷は、足の遠い者には重すぎることがある。\nあたしが土に呼びかければ、決まった分だけ戻ってくる。残りは土に還って、墓も消えるよ。どの墓か、あなたが選ぶといい。一つずつだ。",
    "dialogue.skyabyss.storyteller.grave_ask.yes": "お願いする。",
    "dialogue.skyabyss.storyteller.grave_ask.no": "やめておく。",
    # 控え帳 (B) の「墓」の行
    "gui.skyabyss.grave.title": "語り部に頼む墓",
    "gui.skyabyss.grave.count": "墓 %s つ",
    "gui.skyabyss.grave.share": "選んだ墓の中身の %s％ が戻る。墓は消え、残りは失う。",
    "gui.skyabyss.grave.pick": "呼び戻す",
    "gui.skyabyss.grave.place": "%s　%s, %s, %s",
    "gui.skyabyss.grave.contents": "品 %s 個・経験値 %s",
    "gui.skyabyss.grave.footer": "Esc でやめる",
    "gui.skyabyss.grave.age.now": "たった今",
    "gui.skyabyss.grave.age.minutes": "%s 分前",
    "gui.skyabyss.grave.age.hours": "%s 時間前",
    "gui.skyabyss.grave.age.days": "%s 日前",
    "gui.skyabyss.grave.dim.overworld": "地上",
    "gui.skyabyss.grave.dim.nether": "ネザー",
    "gui.skyabyss.grave.dim.end": "エンド",
    "skyabyss_record.r.grave": "墓",
    "skyabyss_record.v.grave_none": "なし",
    "skyabyss_record.v.graves": "%s つ",
    "skyabyss_record.d.grave_none": "いま、あなたの墓はない。倒れたとき、持ち物の一部が墓に入る（装備とホットバーと大事な品は手もとに残る）。",
    "skyabyss_record.d.grave_has": "あなたの墓。倒れた場所のそばに立っていて、時間では消えない。右クリックで中身が手もとへ移る。",
    "skyabyss_record.d.grave_item": "- %s　品 %s 個・経験値 %s　%s（%s, %s, %s）",
    "skyabyss_record.d.grave_rescue": "取りに行けないときは、語り部に頼むと、選んだ墓の中身の %s％ が戻る（墓は消え、残りは失う）。",
    "skyabyss_record.d.grave_rescue_off": "語り部の救いは、このワールドでは使えない。",
    "skyabyss_record.d.grave_owner_only": "墓は持ち主だけが開けられる。",
    "skyabyss_record.d.grave_open": "墓はほかの人も開けられる。",
    "skyabyss_record.d.grave_expire": "墓は %s 分で崩れて、中身が地面に散らばる。",
}

EN = {
    "block.skyabyss.grave": "Grave",
    "skyabyss_grave.made": "A grave has risen near where you fell: %s (%s, %s, %s). Its place is written in the record book (B)",
    "skyabyss_grave.kept_all": "Your belongings stayed with you",
    "skyabyss_grave.not_yours": "This is %s's grave. It will not open for you",
    "skyabyss_grave.collected": "Took back your things from the grave",
    "skyabyss_grave.collected_part": "Took back some things (%2$s kinds remain in the grave)",
    "skyabyss_grave.expired": "An old grave crumbled and its contents spilled onto the ground",
    "skyabyss_grave.rescue_none": "There is no grave to call back",
    "skyabyss_grave.rescued": "At the storyteller's call, %s items and %s experience came back (%s%% of the grave). The grave returned to the earth",
    "skyabyss_grave.rescued_pending": "%s items did not fit and were set aside as held rewards. Claim them in the record book",
    "dialogue.skyabyss.storyteller.menu.grave": "I've lost something.",
    "dialogue.skyabyss.storyteller.lost_ask": "...Lost something, have you. What?",
    "dialogue.skyabyss.storyteller.lost_ask.grave": "There is a grave I can't reach.",
    "dialogue.skyabyss.storyteller.lost_ask.conch": "I lost my tide-watching conch.",
    "dialogue.skyabyss.storyteller.lost_ask.no": "Never mind.",
    "dialogue.skyabyss.storyteller.grave_ask": "...A grave, is it. A load under the earth can be too heavy for those who are far from it.\nIf I call to the earth, a set share will come back. The rest returns to the soil, and the grave goes with it. You choose which grave, one at a time.",
    "dialogue.skyabyss.storyteller.grave_ask.yes": "Please do.",
    "dialogue.skyabyss.storyteller.grave_ask.no": "Never mind.",
    "gui.skyabyss.grave.title": "Which grave to call back",
    "gui.skyabyss.grave.count": "%s graves",
    "gui.skyabyss.grave.share": "%s%% of its contents comes back; the rest is lost.",
    "gui.skyabyss.grave.pick": "Call back",
    "gui.skyabyss.grave.place": "%s  %s, %s, %s",
    "gui.skyabyss.grave.contents": "%s items, %s experience",
    "gui.skyabyss.grave.footer": "Esc to cancel",
    "gui.skyabyss.grave.age.now": "just now",
    "gui.skyabyss.grave.age.minutes": "%s min ago",
    "gui.skyabyss.grave.age.hours": "%s h ago",
    "gui.skyabyss.grave.age.days": "%s days ago",
    "gui.skyabyss.grave.dim.overworld": "Overworld",
    "gui.skyabyss.grave.dim.nether": "Nether",
    "gui.skyabyss.grave.dim.end": "The End",
    "skyabyss_record.r.grave": "Graves",
    "skyabyss_record.v.grave_none": "None",
    "skyabyss_record.v.graves": "%s",
    "skyabyss_record.d.grave_none": "You have no grave now. When you fall, part of your pack goes into one (equipment, hotbar and key items stay with you).",
    "skyabyss_record.d.grave_has": "Your graves. Each stands near where you fell and never fades. Right-click one to move its contents to your pack.",
    "skyabyss_record.d.grave_item": "- %s: %s items, %s experience  %s (%s, %s, %s)",
    "skyabyss_record.d.grave_rescue": "If you cannot reach one, ask the storyteller: %s%% of the grave you choose comes back (the grave vanishes and the rest is lost).",
    "skyabyss_record.d.grave_rescue_off": "The storyteller's rescue is turned off in this world.",
    "skyabyss_record.d.grave_owner_only": "Only the owner can open a grave.",
    "skyabyss_record.d.grave_open": "Anyone can open a grave.",
    "skyabyss_record.d.grave_expire": "A grave crumbles after %s minutes and its contents spill onto the ground.",
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    save("grave_stone.png", stone())
    save("grave_glow.png", glow())
    write_json(ASSETS / "blockstates/grave.json", {"variants": {"": {"model": "skyabyss:block/grave"}}})
    write_json(ASSETS / "models/block/grave.json", model())
    write_json(RES / "data/skyabyss/dialogue/storyteller_aa_grave.json", dialogue())
    write_json(RES / "assets/skyabyss_grave/lang/ja_jp.json", JA)
    write_json(RES / "assets/skyabyss_grave/lang/en_us.json", EN)
    print("grave assets written")


if __name__ == "__main__":
    main()
