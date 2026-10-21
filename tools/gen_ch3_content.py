"""三章「風に逆らう嵐」(雷翼。docs/design/12 の 12 章、01 の 4.2) の中身を書き出す。

書くもの:
  data/skyabyss/opening/ch3_{vane,ruin,pack,tablet}.json                小要素の部品 (ChapterSites の青図。座標の約束は Blueprint)
  data/skyabyss/lore/entry/chapter1/storm_{burnt_pack,wind_tablets,size}.json   伝承の項目 3 つ (章は titan_storm)
  data/skyabyss/quest/ch3/{sub_wind_marks,sub_isana_wait}.json          三章のクエスト 2 本 ("chapter": "titan_storm")
  data/skyabyss/dialogue/ch3/whale_chaser_ch3.json                      イサナの足し書き ("extends": true、節は ch3_ で始める)
  assets/skyabyss_ch3/lang/{ja_jp,en_us}.json                           上のすべての文 (看板・知らせ・クエスト・会話・伝承)

実行 (リポジトリ直下):  python tools/gen_ch3_content.py
経験値の予算: 小要素の経験値は一章あたり「その時の 1 レベル分の 1〜1.5 倍」まで (Lv 18 で約 1,150)。
  sub_wind_marks 150 + 150、sub_isana_wait 60 + 200 = 560 (約 0.5 レベル)。
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/skyabyss"
LANG = ROOT / "src/main/resources/assets/skyabyss_ch3/lang"

JA: dict[str, str] = {}
EN: dict[str, str] = {}


def text(key: str, ja: str, en: str) -> None:
    assert key not in JA, key
    JA[key] = ja
    EN[key] = en


def write(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ================================================================ 青図 (座標は構造物の中心が (0, 0)、y=0 が地面の一つ上)
def blueprint(name, palette, blocks, marks, rect, ring=2, level=True, note=""):
    cells = [[x, y, z, palette.index(b)] for (x, y, z, b) in blocks]
    obj = {"site": name, "note": note, "palette": palette, "blocks": cells, "marks": marks,
           "meta": {"rect": rect, "level": level, "ring": ring}}
    write(DATA / "opening" / f"{name}.json", obj)


BRICK = "minecraft:stone_bricks"
MOSS = "minecraft:mossy_stone_bricks"
CRACK = "minecraft:cracked_stone_bricks"
CHISEL = "minecraft:chiseled_stone_bricks"
SLAB = "minecraft:stone_brick_slab[type=bottom]"
WALL = "minecraft:cobblestone_wall"
ROD = "minecraft:lightning_rod[facing=up]"


def vane():
    # 風見鶏の丘: 石の台と銅の針の柱。柱の頭 (top) に、コードが向きを変える矢を付ける (StormVane)
    pal = [BRICK, MOSS, CHISEL, WALL, ROD]
    blocks = []
    for x in range(-1, 2):
        for z in range(-1, 2):
            blocks.append((x, 0, z, CHISEL if (x, z) == (0, 0) else (MOSS if (x + z) % 2 else BRICK)))
    for sx in (-1, 1):
        for sz in (-1, 1):
            blocks.append((sx, 1, sz, WALL))
    for y in range(1, 5):
        blocks.append((0, y, 0, ROD))
    blueprint("ch3_vane", pal, blocks, {"top": [0, 5, 0]}, [-2, -2, 2, 2],
              note="三章 風見鶏の丘。矢 (横向きの避雷針と鉄格子) は StormVane が毎回向きを直す")


def ruin():
    # 避雷の塔の跡: 折れた石の塔の輪と、一本だけ残った銅の針 (バニラの避雷針の使い方を先に見せる)
    pal = [BRICK, MOSS, CRACK, SLAB, ROD]
    blocks = []
    ring = [(x, z) for x in range(-1, 2) for z in range(-1, 2) if (x, z) != (0, 0)]
    for i, (x, z) in enumerate(ring):
        blocks.append((x, 0, z, [BRICK, CRACK, MOSS][i % 3]))
    for (x, z) in ring:
        if (x, z) not in ((1, 1), (-1, 0), (0, -1)):
            blocks.append((x, 1, z, CRACK if (x + z) % 2 else BRICK))
    for (x, z) in ((-1, -1), (1, 0), (0, 1)):
        blocks.append((x, 2, z, CRACK))
    blocks.append((-1, 3, -1, SLAB))
    blocks.append((0, 0, 0, BRICK))
    for y in range(1, 5):
        blocks.append((0, y, 0, ROD))
    for (x, z) in ((2, 1), (-2, -1), (1, -2)):
        blocks.append((x, 0, z, SLAB))
    blueprint("ch3_ruin", pal, blocks, {"rod": [0, 4, 0]}, [-3, -3, 3, 3],
              note="三章 避雷の塔の跡。折れた塔の輪 + 針 1 本")


def pack():
    # 雷に焼かれた旅の荷: 焦げた台地 (黒石・玄武岩・石炭) の上に、割れた荷車と樽
    pal = ["minecraft:blackstone", "minecraft:blackstone_slab[type=bottom]", "minecraft:basalt[axis=y]", "minecraft:coal_block",
           "minecraft:barrel[facing=up]", "minecraft:dark_oak_planks", "minecraft:dark_oak_fence", "minecraft:stripped_dark_oak_log[axis=x]"]
    B, S, BA, C, BR, P, F, L = pal
    blocks = []
    for x in range(-2, 3):
        for z in range(-2, 3):
            edge = abs(x) == 2 or abs(z) == 2
            if edge and (x + z) % 2:
                continue
            blocks.append((x, 0, z, S if edge else (BA if (x * 3 + z) % 4 == 0 else B)))
    blocks.append((1, 0, -1, C))
    blocks.append((0, 1, 0, BR))
    blocks.append((-1, 1, 1, P))
    blocks.append((-2, 1, 1, P))
    blocks.append((-1, 2, 1, F))
    blocks.append((1, 1, 1, L))
    blueprint("ch3_pack", pal, blocks, {"barrel": [0, 1, 0]}, [-3, -3, 3, 3],
              note="三章 雷に焼かれた旅の荷。樽 (mark barrel) の中身は StormSites が入れる")


def tablet():
    pal = [BRICK, MOSS, WALL, SLAB]
    blocks = [(-1, 0, 0, MOSS), (0, 0, 0, BRICK), (1, 0, 0, MOSS), (-1, 1, 0, WALL), (1, 1, 0, WALL), (0, 1, 0, BRICK), (0, 2, 0, SLAB)]
    # 碑 (skyabyss:tablet) は仕上げで mark "tablet" に置く (文の鍵を刻むため)。台の上の段は空けておく
    blocks = [b for b in blocks if b[:3] != (0, 1, 0) and b[:3] != (0, 2, 0)]
    blueprint("ch3_tablet", pal, blocks, {"tablet": [0, 1, 0]}, [-2, -1, 2, 1], ring=2,
              note="三章 風の碑。碑は仕上げで置く")


vane()
ruin()
pack()
tablet()

# ================================================================ 看板 (風の碑 3 枚。4 行・日本語 8 字まで・英語 90 px まで)
TABLETS = [
    (("あの影を", "神と呼んだ", "風の民は", "鳥を見誤った"), ("They called the", "shadow a god.", "The wind folk", "mistook the bird")),
    (("嵐の鳥は", "雲の下を飛ぶ", "影は雲の上", "高さが違う"), ("The storm bird", "flies below", "the clouds. The", "shadow is above")),
    (("気づいた時は", "もう遅かった", "あれは鳥より", "ずっと大きい"), ("When we knew,", "it was too late.", "That one is far", "bigger than it")),
]
for i, (ja, en) in enumerate(TABLETS):
    for j in range(4):
        text(f"sign.skyabyss.ch3_wind_{i + 1}.{j + 1}", ja[j], en[j])

# ================================================================ 知らせ (画面の語り・手がかり。チャットには流さない)
text("message.skyabyss.ch3.burn_line", "遠くの地面に、一本の黒い筋が走っている。雷が落ちたにしては、長すぎる。",
     "A black streak runs across the far ground. Too long for a single bolt.")
text("message.skyabyss.ch3.low_beasts", "獣たちが、地面に身を寄せている。夜だというのに、遠くで雷が鳴った。風上から。",
     "The beasts press low to the ground. Thunder, far off, at night. From upwind.")
text("message.skyabyss.ch3.low_plain", "雲のない夜空の下で、遠くの雷が鳴った。風上から。",
     "Thunder rolled far away under a cloudless night. From upwind.")
text("message.skyabyss.ch3.wing_flash", "稲光の一瞬、雲の下に、翼の形が浮かんだ。……見間違いだろうか。",
     "In one flash, the shape of a wing hung under the clouds. Did I imagine it?")
text("message.skyabyss.ch3.vane", "風見が、風に逆らって回っている。", "The vane turns against the wind.")
text("message.skyabyss.ch3.ruin", "折れた塔の頂に、銅の針が一本だけ残っている。雷は、あの針を選ぶ。",
     "One copper needle is left on the broken tower. Lightning will choose it.")
text("message.skyabyss.ch3.pack", "黒く焼けた荷のそばに、頁が一枚、落ちている。", "A single page lies beside the charred pack.")
text("message.skyabyss.ch3.showcase_1", "山のような鳥が、峰にうずくまっている。翼で、頭を覆っている。",
     "The mountain-sized bird crouches on the peak. It covers its head with its wings.")
text("message.skyabyss.ch3.showcase_2", "その上を、影が渡っていく。……雷翼が、小さく見える。",
     "A shadow crosses above it. The thunder wing looks small.")
text("message.skyabyss.ch3.showcase_3", "雲の上にいるのは、あれだ。雷翼ですら、顔を上げられない。",
     "That is what lives above the clouds. Even the thunder wing cannot lift its head.")
text("message.skyabyss.ch3.aftermath", "語り部は言った。「晴れたのは、ほんの一瞬だ。雲の上のものは、まだそこにいる」",
     "The storyteller said: \"The sky cleared only for a moment. The one above the clouds is still there.\"")
text("chapter.skyabyss.aftermath.titan_storm", "嵐は割れた。雲の上に、影はまだいる。",
     "The storm broke. The shadow is still above the clouds.")

# ================================================================ 伝承の項目 (章 titan_storm。unlock は manual = コードから記す)
LORE = {
    "storm_burnt_pack": (
        171, "焼けた荷の頁",
        "黒く焼けた荷車のそばに、濡れて乾いた帳面の頁が落ちていた。\n\n「嵐の鳥を、空の影と呼んだのは誰だ。私は見たはずだ。あの影のほうが、ずっと高く、ずっと大きかった。」\n\n字に、見覚えがある。自分の字によく似ている。",
        "Page of the Burnt Pack",
        "Beside a charred cart lay a page, soaked and dried again.\n\n\"Who called the storm bird the shadow in the sky? I saw it. The shadow was higher, and far larger.\"\n\nThe handwriting is familiar. It looks a lot like my own."),
    "storm_wind_tablets": (
        172, "風の碑",
        "峰の上に、風の民の碑が三枚、間をおいて立っていた。\n\n影を神と呼んだこと。嵐の鳥は雲の下を飛ぶこと。気づいたときには、もう遅かったこと。\n\n三枚目は、文が途中で終わっている。刻み手が、手を止めたようだ。",
        "The Wind Tablets",
        "Three tablets of the wind folk stood apart on the heights.\n\nThey called the shadow a god. The storm bird flies under the clouds. When they knew, it was too late.\n\nThe third tablet ends mid-sentence. The carver seems to have stopped."),
    "storm_size": (
        173, "影と翼",
        "空の影が通るあいだ、雷翼は峰にうずくまり、翼で頭を覆っていた。山のような鳥が、怯えていた。\n\nその上を、影がゆっくり渡っていった。影は雷翼の何倍もあった。雲の上にいるのは、あれだ。",
        "Shadow and Wing",
        "While the shadow passed, the thunder wing crouched on a peak with its wings over its head. A bird the size of a mountain, afraid.\n\nThe shadow crossed above it, slowly, many times its size. That is what lives above the clouds."),
}
for name, (order, tj, bj, te, be) in LORE.items():
    write(DATA / "lore/entry/chapter1" / f"{name}.json", {
        "chapter": "titan_storm", "order": order,
        "unlock": [{"type": "manual"}],
        "related": ["chapter1/storm_wing", "chapter1/storm_cower"] if name == "storm_size" else ["chapter1/storm_wing"],
    })
    text(f"lore.skyabyss.chapter1.{name}.title", tj, te)
    text(f"lore.skyabyss.chapter1.{name}.body", bj, be)

# ================================================================ クエスト
# 風の跡 (小要素 4 種を、順に関係なく回る。段は一つ。信号は StormSites の onArrive が出す)
write(DATA / "quest/ch3/sub_wind_marks.json", {
    "category": "sub", "chapter": "titan_storm", "order": 44, "min_stage": 2,
    "start_when": {"type": "stage", "min": 2, "max": 2},
    "stages": [{
        "id": "trace", "xp": 150,
        "objectives": [
            {"id": "vane", "type": "signal", "target": "ch3_vane", "party": False},
            {"id": "ruin", "type": "signal", "target": "ch3_ruin", "party": False},
            {"id": "pack", "type": "signal", "target": "ch3_pack", "party": False},
            {"id": "tablets", "type": "signal", "targets": ["ch3_wind_1", "ch3_wind_2", "ch3_wind_3"], "count": 3, "party": False},
        ],
    }],
    "reward": {"xp": 150},
})
text("quest.skyabyss.ch3.sub_wind_marks.title", "風の跡", "Marks of the Wind")
text("quest.skyabyss.ch3.sub_wind_marks.summary", "風に逆らう嵐の、跡をたどる。", "Follow the marks of the storm that runs against the wind.")
text("quest.skyabyss.ch3.sub_wind_marks.done", "風の民は、影を神と呼んだ。鳥のほうが、見誤られていたのかもしれない。",
     "The wind folk called the shadow a god. Perhaps it was the bird they misread.")
text("quest.skyabyss.ch3.sub_wind_marks.trace.journal",
     "朝から、風が逆向きに吹いている。\n高い所に、風見が立っているという。折れた塔の跡と、焼けた荷、風の民の碑も、近くにあるらしい。",
     "Since morning the wind has blown the wrong way.\nA vane is said to stand on the heights, and a broken tower, a charred pack and the wind folk's tablets lie nearby.")
text("quest.skyabyss.ch3.sub_wind_marks.trace.vane", "風見の丘を見つける", "Find the vane on the hill")
text("quest.skyabyss.ch3.sub_wind_marks.trace.ruin", "避雷の塔の跡を見つける", "Find the broken lightning tower")
text("quest.skyabyss.ch3.sub_wind_marks.trace.pack", "雷に焼かれた荷を見つける", "Find the pack burnt by lightning")
text("quest.skyabyss.ch3.sub_wind_marks.trace.tablets", "風の碑を見つける（3 枚）", "Find the wind tablets (3)")

# イサナ: 夕立の刻に峰の足もとへ行く → 聞かせる。乗るのは自分だけ、とイサナが決める (追う人から見送る人へ)
write(DATA / "quest/ch3/sub_isana_wait.json", {
    "category": "sub", "chapter": "titan_storm", "order": 46, "giver": "skyabyss:whale_chaser", "min_stage": 2,
    "unlock": {"type": "stage", "min": 2, "max": 2},
    "stages": [
        {"id": "climb", "xp": 60,
         "objectives": [{"id": "foot", "type": "signal", "target": "ch3_perch_foot", "party": False}]},
        {"id": "tell", "xp": 0,
         "objectives": [{"id": "talk", "type": "talk", "target": "skyabyss:whale_chaser", "topic": "ch3_wait_done2"}]},
    ],
    "reward": {"xp": 200, "affinity": {"skyabyss:whale_chaser": 5}},
})
text("quest.skyabyss.ch3.sub_isana_wait.title", "峰の足もとで", "At the Foot of the Peak")
text("quest.skyabyss.ch3.sub_isana_wait.summary", "夕立の刻に、峰の足もとへ行く。見たものを、イサナに話す。",
     "Go to the foot of the peak in the afternoon storm hour, and tell Isana what you saw.")
text("quest.skyabyss.ch3.sub_isana_wait.done", "イサナは、火のそばで待つと決めた。昇るのは、自分だけだ。",
     "Isana will wait by the fire. Only I will climb.")
text("quest.skyabyss.ch3.sub_isana_wait.climb.journal",
     "夕立の刻に、巨大な鳥が峰へ降りるという。\n峰の足もとまで行って、風がどう動くか、見てこよう。",
     "In the afternoon storm hour the giant bird settles on a peak.\nI will go to the foot of the peak and watch how the wind moves.")
text("quest.skyabyss.ch3.sub_isana_wait.climb.foot", "夕立の刻に、峰の足もとへ行く", "Reach the foot of the peak in the storm hour")
text("quest.skyabyss.ch3.sub_isana_wait.tell.journal", "風は、鳥の足もとで渦を巻いていた。\nイサナのところへ、話しに行こう。",
     "The wind spiralled at the bird's feet.\nI will go and tell Isana.")
text("quest.skyabyss.ch3.sub_isana_wait.tell.talk", "イサナに、峰の足もとの話をする", "Tell Isana about the foot of the peak")

# ================================================================ 会話 (イサナの足し書き。節は ch3_ で始める)
CHASER = "dialogue.skyabyss.whale_chaser."
write(DATA / "dialogue/ch3/whale_chaser_ch3.json", {
    "npc": "skyabyss:whale_chaser",
    "extends": True,
    "entries": [
        {"if": {"type": "quest", "quest": "ch3/sub_isana_wait", "is": "active", "stage": "tell"}, "node": "ch3_wait_done"},
        {"if": {"type": "all", "of": [
            {"type": "stage", "min": 2, "max": 2},
            {"type": "quest", "quest": "ch3/sub_isana_wait", "is": "none"},
            {"type": "not", "of": {"type": "flag", "flag": "c_ch3_asked"}},
        ]}, "node": "ch3_wait_offer"},
    ],
    "nodes": {
        "ch3_wait_offer": {"face": "grave", "do": [{"type": "flag", "flag": "c_ch3_asked"}], "next": "ch3_wait_offer2"},
        "ch3_wait_offer2": {"face": "neutral", "options": [
            {"id": "go", "goto": "end", "kind": "quest", "do": [{"type": "quest_start", "quest": "ch3/sub_isana_wait"}, {"type": "affinity", "amount": 1}]},
        ]},
        "ch3_wait_done": {"face": "grave", "options": [
            {"id": "wind", "goto": "ch3_wait_done2"},
            {"id": "quiet", "goto": "ch3_wait_done2"},
        ]},
        "ch3_wait_done2": {"face": "grave", "next": "ch3_wait_done3"},
        "ch3_wait_done3": {"face": "neutral", "next": "ch3_wait_done4"},
        "ch3_wait_done4": {"face": "smile", "do": [{"type": "flag", "flag": "c_ch3_seeoff"}, {"type": "affinity", "amount": 1}],
                           "options": [{"id": "ok", "goto": "end"}]},
    },
})
text(CHASER + "ch3_wait_offer", "風が、逆向きに吹いていただろう。……あれは、嵐が風に逆らって、こっちへ来ているんだ。\n鯨は空を渡り、潮殻は海に待った。次の奴は、嵐を着て飛ぶ。",
     "The wind blew backwards this morning. A storm is walking up against it, toward us.\nThe whale crossed the sky and the shell waited in the sea. The next one flies wearing a storm.")
text(CHASER + "ch3_wait_offer2", "夕立の刻に、あいつは高い峰へ降りる。峰の足もとまで行って、風がどう動くか、見てきてくれ。\n……俺は行かない。昇るのは、あんただけでいい。",
     "In the afternoon storm hour it settles on a high peak. Go to the foot of the peak and see how the wind moves.\nI won't go. Only you need to climb.")
text(CHASER + "ch3_wait_offer2.go", "見てくる。", "I'll go and see.")
text(CHASER + "ch3_wait_done", "戻ったか。……風は、どうだった。", "You're back. How was the wind?")
text(CHASER + "ch3_wait_done.wind", "渦を巻いていた。", "It spiralled up.")
text(CHASER + "ch3_wait_done.quiet", "黙って頷く。", "(Nod in silence.)")
text(CHASER + "ch3_wait_done2", "俺はずっと、追いかけてきた。鯨も、潮殻もだ。追えばいつか追いつく、そう思ってな。",
     "I have always chased them. The whale, the shell. I thought if I chased long enough, I'd catch up.")
text(CHASER + "ch3_wait_done3", "だが、あの鳥は追うものじゃない。……待つものだ。峰の下で、風に呼ばれた気がした。それが、あんたの話で分かった。",
     "But that bird isn't something you chase. It's something you wait for. Down at the peak I felt called by the wind, and your story told me why.")
text(CHASER + "ch3_wait_done4", "昇るのは、あんただけだ。俺はこの火のそばで待っている。……戻ったら、風の話を聞かせろ。",
     "Only you will climb. I'll wait here by the fire. When you come back, tell me about the wind.")
text(CHASER + "ch3_wait_done4.ok", "ああ。", "I will.")

# ================================================================ lang を書く
# 三章の地上 (tools/gen_ch3_ground.py) も同じ lang に書くので、ここが持たないキーは残す
for _name, _table in (("ja_jp.json", JA), ("en_us.json", EN)):
    _old = json.loads((LANG / _name).read_text(encoding="utf-8")) if (LANG / _name).exists() else {}
    _old.update(_table)
    write(LANG / _name, dict(sorted(_old.items())))

# ================================================================ 看板の幅の検査 (SignFit と同じ表)
ASCII_WIDTHS = "2466662444626266666666666225656766666666466666666666666666464663666665662653666666646666664247"


def width(t: str) -> int:
    w = 0
    for c in t:
        o = ord(c)
        w += 4 if c == " " else (int(ASCII_WIDTHS[o - 33]) if 32 < o < 127 else 9)
    return w


bad = []
for k in JA:
    if k.startswith("sign."):
        for lang, t in (("ja", JA[k]), ("en", EN[k])):
            if width(t) > 90:
                bad.append(f"{k} ({lang}) {width(t)} px: {t}")
        if len(JA[k]) > 8:
            bad.append(f"{k} (ja) {len(JA[k])} chars: {JA[k]}")
print(f"wrote {len(JA)} texts; sign problems: {bad or 'none'}")
