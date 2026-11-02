# -*- coding: utf-8 -*-
"""2026-11-02 夜 (正式版前の続き): ダンジョン専用の敵の形の選ぶページ (選んだものは db の choices) と、同じ中身の md を書く。
やったこと (主の関節・顎飾り・撮り直し・都の空き家・重さ) の報告も載せる。
絵は docs/review/dungeon_foes_1102/img/ (ラフ tools/rough/lowpoly/dungeon_foes/roughs.py と撮影 tools/shots/load_probe.txt)。

    python -X utf8 tools/gen/review/dungeon_foes_page.py
"""
import html
import json
import os
import shutil

from PIL import Image

ROOT = "C:/Users/nisimoto/Desktop/mod"
OUT = os.path.join(ROOT, "docs/review/dungeon_foes_1102")
ROUGHS = os.path.join(ROOT, "docs/review/roughs_dungeon_foes")
SHOTS = os.path.join(ROOT, "run/shots/load_probe/shots/load_probe")

NONE = {"v": "none", "label": "作らない（その土地の今の敵のまま）"}


def q(no, key, title, why, opts, rec):
    options = []
    for v, (label, sub) in zip("abc", opts):
        options.append({"v": v, "label": "%s %s" % (v.upper(), label), "sub": sub, "rec": v == rec})
    options.append(NONE)
    return {"id": "q%d" % no, "no": no, "title": title, "img": key, "wide": True, "why": why, "options": options}


FOE_Q = [
    # ---- 鯨骨の坑 (Lv 10)
    q(372, "digger", "骨掘りの坑夫（鯨骨の坑・道中）", "人ほどの大きさの道中の敵。今は骸の歩兵・肋の射手を使っている。",
      [("骨の坑夫", "腰の曲がった骸の坑夫。肋骨の籠の胸・布の帽子・鶴嘴を担ぎ、背に光る灯りの籠。振り下ろし（前の短い帯の予告）"),
       ("鯨骨の兜の坑夫", "鯨の頭骨を兜にかぶったずんぐりした坑夫。両手の大きな円匙で前を薙ぐ（扇の予告）"),
       ("四つ腕の掘り骸", "腕が四本の骸。前かがみで這い、上の二本の刃の腕で二度斬る")], "a"),
    q(373, "grub", "骨喰いの小虫（鯨骨の坑・道中の群れ）", "膝ほどの小さな敵。3〜5 匹で出る。",
      [("骨喰いの幼虫", "白く太い節の芋虫。骨の大顎で噛みつく"),
       ("骨甲の団子虫", "骨の板の甲を負う団子虫。丸まって転がり、まっすぐ突っ込む（線の予告。横へ避ける）"),
       ("骨の壁蜘蛛", "小さな頭骨を胴にした六本脚の蜘蛛。壁と天井を這い、上から落ちてくる（足もとの円の予告）")], "b"),
    q(374, "foreman", "坑の頭（鯨骨の坑・中ボス）", "一層目の終わりの中ボス。倒すと二層目への扉が開く。今は岩猪の名のある大物。",
      [("骨鯨の兜の坑頭", "人の倍ほどの大男。鯨の頭骨の兜・骨の肩当て、担いだ大きな骨の槌で地を割る（割れ目が前へ走る予告）"),
       ("骨と岩の掘り機", "坑夫の作った掘る機械。胸の奥に光る鉱石（弱点）、右腕は回る錐で突進"),
       ("鉱石負いの大土竜", "背に光る鉱石の房の大土竜。潜って足もとから出る（主の大百足と似た動きになる）")], "a"),
    # ---- 潮の洞 (Lv 16)
    q(375, "sailor", "沈み船の水夫（潮の洞・道中）", "人ほどの道中の敵。今は岩場の蟹・霧の釣り手などを使っている。",
      [("沈み船の水夫", "ふくれた水死人の水夫。縞の服に藤壺。銛を投げる（線の予告）。遠くから"),
       ("珊瑚の骸の水夫", "珊瑚の生えた骸。片目の眼帯と赤い腰帯、曲刀で二度斬る"),
       ("藻を巻いた空ろの水夫", "中身のない藻の衣の人形。光る魚の角灯を下げ、近くの水を引き寄せる")], "a"),
    q(376, "octoling", "洞の小さな軟体（潮の洞・道中）", "小さめの敵。満ち引きの仕掛けと合わせて出る。",
      [("洞の小蛸", "人の膝ほどの蛸。八本の腕で這い、墨を吐いて目を眩ます（主の大蛸と似る）"),
       ("浮かぶ灯烏賊", "宙を泳ぐ小さな烏賊。胴の灯りが光り、長い触手で叩く"),
       ("洞の水母", "傘の下に光る糸を垂らして漂う。触れると痺れる。満ち潮で水の上を流れてくる")], "c"),
    q(377, "bellcrab", "沈み船の大物（潮の洞・中ボス）", "一層目の終わりの中ボス。今は潮蟹の名のある大物。",
      [("鐘負いの大寄居蟹", "沈んだ船の鐘を殻にした大寄居蟹。鐘を鳴らすと波が来る（足もとの輪の予告）。鐘の国の話と合う"),
       ("船骸負いの蟹", "沈んだ船の舳先を背負う蟹。折れた帆柱と破れた帆、片方の鋏の錨を振り回す"),
       ("藤壺の大海老", "藤壺に覆われた長い大海老。大きな二本の鋏、尾を打って跳ねて下がる")], "a"),
    # ---- 雷晶の空洞 (Lv 22)
    q(378, "beetle", "晶の虫（雷晶の空洞・道中）", "膝の上ほどの道中の敵。今は雷の狼・静電の毛玉などを使っている。",
      [("晶殻の甲虫", "背の殻が青い結晶。前からの傷を殻で受け、結晶の角で突く"),
       ("雷角の兜虫", "二本の大きな結晶の角。角の間に雷を溜めて（光る予告）、前へ放つ"),
       ("晶背の岩亀虫", "平たい岩の亀のような虫。背の結晶の柱を割ると弱る。遅いが硬い")], "b"),
    q(379, "bat", "晶の飛ぶもの（雷晶の空洞・道中）", "宙の敵。結晶の仕掛けの部屋に出る。",
      [("晶翼の蝙蝠", "翼に結晶の生えた蝙蝠の群れ。上から雷の粉を落とす"),
       ("晶の目玉", "結晶の目玉が小さな翼で浮く。見つめた所に雷を落とす（足もとの円の予告）"),
       ("雷の蛾", "翅に結晶の鱗粉が光る大きな蛾。羽ばたくと痺れの粉が舞う（まわりの円）")], "b"),
    q(380, "golem", "晶の大物（雷晶の空洞・中ボス）", "一層目の終わりの中ボス。今は嵐の羊の名のある大物。",
      [("晶洞の傀儡", "割れた晶洞を胴にした岩の巨人。胸の内側の結晶が弱点、大きな岩の拳で叩く"),
       ("晶の大蛇", "岩の節に結晶の生えた大蛇。とぐろを巻き、鎌首から雷を吐く（前の帯の予告）"),
       ("三つ晶の芯", "光る芯のまわりを三つの結晶の板が回る。板で薙ぎ、板を割ると芯がむき出しに")], "a"),
    # ---- 熔けた坑道 (Lv 28)
    q(381, "miner", "炉の坑夫（熔けた坑道・道中）", "人ほどの道中の敵。今は灰の殻・灰の引きずりなどを使っている。",
      [("灰の坑夫", "灰をかぶった坑夫。顔に鉄の覆い、赤く焼けた鶴嘴（鯨骨の坑の坑夫と似る）"),
       ("鞴の小鬼", "背に鞴を負った背の低い小鬼。鉄の仮面と長い火掻き棒、鞴で前へ火を吹きつける（扇の予告）"),
       ("鉱滓の小人形", "冷えた鉱滓の塊の小さな人形。倒れると熔岩溜まりが残る")], "b"),
    q(382, "slug", "熔岩の這うもの（熔けた坑道・道中）", "膝ほどの道中の敵。溶岩の区間の仕掛けと合わせて出る。",
      [("熔岩の蛞蝓", "黒い皮の大きな蛞蝓。背の割れ目が光り、通った跡にしばらく火が残る"),
       ("炉殻の蝸牛", "小さな炉を殻にした蝸牛。殻の口から火の玉を撃つ（遠く）"),
       ("熾火の山椒魚", "熾火の斑の山椒魚。溶岩の中を泳ぎ、跳び出して噛む")], "a"),
    q(383, "warden", "炉の番（熔けた坑道・中ボス）", "一層目の終わりの中ボス。今は灰の蜥蜴の名のある大物。",
      [("鎖の炉番", "背に炉を負った仮面の番人。両手の鎖の先の焼けた鉄球を振り回す（主の巨人も炉を負うので似る）"),
       ("牛頭の鍛冶番", "牛の頭の大男。左手に焼けた鉄の板の盾（前の傷を受ける）、右手の大きな火ばさみで掴んで投げる"),
       ("歩く炉", "四本の鉄の脚で歩く炉。炉の口が顔、口から熔けた鉄を吐き、鎖の腕で引っかける")], "b"),
]

DONE = [
    "ダンジョンの主 3 体の関節: 炉負いの巨人の頭が胴から離れていた（うなずくと浮く）→ 首を足した。大百足の頭の節と大蜥蜴の背の結晶の付け根を深く埋めた。メッシュの検査は巨人が 0 件。残る 2 件（大百足の頭・大蜥蜴の結晶）は部品が多いことによる検査の見かけで、絵では隙間が無い。",
    "骨掘りの顎飾りが襟の下に隠れていた: 胸に下げる首飾りの位置が胸の防具の内側だった → 防具の外へ（ダンジョンの顎飾りと、野の装備の胸の首飾り 4 品も同じ位置なので一緒に）。撮影で胸の上に見える。",
    "撮影のあとで直した 3 か所（潮の洞の床・沈んだ船・溶岩の区間の縁）と主 4 体を撮り直して確かめた。",
    "都の空き家 3 軒（旧厩舎・地図屋・空見の館）: 戸を壁で塞ぎ、碑を外した。前の版で建てた都も、入ったときに同じ形に置き直す（前に開いた空見の館の戸口も塞ぐ）。自己テスト `capital doors` に入れた。",
    "配布物から自己テストのコードを外す: 調べると jar 74 MB のうち自己テストは圧縮で約 2.2 MB（3% ほど）。外すには 200 ファイル余りを作り替えるので、正式版の前にはしない。jar の大きさのもとは低ポリの形のデータ（約 81 MB を詰めたもの）と音。",
]

BUILT = [
    "選んでもらった 12 体（372〜383 = みな推奨）を作り込み、四つのダンジョンの敵の表に入れた。鯨骨の坑 = 骨の坑夫・骨甲の団子虫・中ボス 骨鯨の兜の坑頭、潮の洞 = 沈み船の水夫・洞の水母・中ボス 鐘負いの大寄居蟹、雷晶の空洞 = 雷角の兜虫・晶の目玉・中ボス 晶洞の傀儡、熔けた坑道 = 鞴の小鬼・熔岩の蛞蝓・中ボス 牛頭の鍛冶番。ほかの種（射手・旗手・鍵持ちなど）はその土地の敵のまま。",
    "形はラフの選んだ案を骨に分け、待機・歩き・技・被弾・倒れの動きを付けた。メッシュは閉じていて、関節の検査も 0 件。",
    "技はどれも予告つき（最短 0.8 秒）。中ボスは技の名前が出て、体力が減ると技が一つ増える。晶洞の傀儡は胸の結晶が弱点（前から 1.5 倍、後ろの岩から 0.6 倍）、牛頭の鍛冶番は盾で前からの傷を 0.35 倍に受ける。",
    "自己テスト `dungeon foes`（登録・卵・経験値・形・動き・表・12 体の予告と当たり・盾と胸）と `dungeons` が通る。ゲームの中で撮影した（並べた姿と技の予告）。",
    "前の版で建てた都の世界を今の版で開くと、空き家 3 軒の戸が塞がり碑が消えた（市と人はそのまま）。",
]

BUILT_SHOTS = [("built_small", "並べた姿: 骨甲の団子虫・熔岩の蛞蝓・雷角の兜虫・洞の水母・晶の目玉"), ("built_people", "並べた姿: 鞴の小鬼・沈み船の水夫・骨の坑夫"),
               ("built_mid", "並べた姿: 中ボス 4 体（牛頭の鍛冶番・晶洞の傀儡・鐘負いの大寄居蟹・骨鯨の兜の坑頭）"), ("built_ox", "牛頭の鍛冶番の盾打ちの予告"),
               ("built_beetle", "雷角の兜虫の雷の帯の予告"), ("built_hermit", "鐘負いの大寄居蟹の大鋏の予告"), ("built_upgrade", "前の版の都を今の版で開いた: 旧厩舎の戸が塞がった")]
BUILT_SRC = {"built_small": "run/shots/dungeon_foes/shots/dungeon_foes/row_small.png", "built_people": "run/shots/dungeon_foes/shots/dungeon_foes/row_people.png",
             "built_mid": "run/shots/dungeon_foes/shots/dungeon_foes/row_mid.png", "built_ox": "run/shots/dungeon_foes/shots/dungeon_foes/atk_ox_1.png",
             "built_beetle": "run/shots/dungeon_foes/shots/dungeon_foes/atk_beetle_2.png", "built_hermit": "run/shots/dungeon_foes/shots/dungeon_foes/atk_hermit_1.png",
             "built_upgrade": "run/shots/upgrade_check/shots/upgrade_check/stable.png"}

SEALED = [("sealed_stable", "旧厩舎の戸（塞いだ。碑なし）"), ("sealed_mapmaker", "地図屋の戸（塞いだ。碑なし）"), ("sealed_hall", "空見の館の戸（塞いだ。碑なし）")]

LOAD = []  # (場所, 結果) は load_probe の記録から main で埋める


def esc(s):
    return html.escape(s, quote=False)


def inline(s):
    out, code = [], False
    for part in s.split("`"):
        out.append(("<code>%s</code>" % esc(part)) if code else esc(part))
        code = not code
    return "".join(out)


def items(lst):
    return "".join("<li>%s</li>" % inline(x) for x in lst)


def read_load():
    log = os.path.join(ROOT, "run/shots/load_probe/logs/latest.log")
    names = {"capital": "都の広場", "field": "野（鯨骨の坑の入口のあたり）", "dungeon_boss": "ダンジョンの主の間（大百足と戦う）", "tower": "あいだの塔の戦い"}
    out = []
    if os.path.exists(log):
        for line in open(log, encoding="utf-8", errors="replace"):
            if "LOAD " in line:
                body = line.split("LOAD ", 1)[1].strip()
                key, rest = body.split(":", 1)
                v = rest.split()
                # avg A p95 B max C ms over N ticks, entities E (mod M), chunks K
                text = "平均 %.1f ms・遅いほうの 5%% %.1f ms・いちばん遅い %.0f ms（敵や人など %s）" % (float(v[1]), float(v[3]), float(v[5]), v[11])
                out.append((names.get(key, key), text))
    return out


def copy_imgs():
    os.makedirs(os.path.join(OUT, "img"), exist_ok=True)
    for qq in FOE_Q:
        for v in "abc":
            src = os.path.join(ROUGHS, "%s_%s.png" % (qq["img"], v))
            Image.open(src).convert("RGB").save(os.path.join(OUT, "img", "%s_%s.jpg" % (qq["img"], v)), quality=86)
    for n, rel in BUILT_SRC.items():
        src = os.path.join(ROOT, rel)
        if os.path.exists(src):
            Image.open(src).convert("RGB").resize((960, 540)).save(os.path.join(OUT, "img", n + ".jpg"), quality=84)
    for n, _ in SEALED:
        src = os.path.join(SHOTS, n + ".png")
        if os.path.exists(src):
            Image.open(src).convert("RGB").resize((960, 540)).save(os.path.join(OUT, "img", n + ".jpg"), quality=84)


def write_md(load):
    L = ["# 2026-11-02 夜: 正式版前の続き（ダンジョン専用の敵の形ほか）", "",
         "声（2026-11-02 夜）: 「それで進めて」（小さな直し → 0.9.23 の候補版）、「少し大きめの作業もお願い。都の空き家 3 軒は完全に入れなくしてもいい。碑もなくしていい」。", "",
         "選ぶページ（`page.html`、選んだものは db の `choices`）とこの md は `tools/gen/review/dungeon_foes_page.py` が書く。未決の 372〜383（[00](../../design/00_decisions_needed.md)）と同じ中身。", "",
         "## 入れた（372〜383 = みな推奨。2026-11-02 の声「おすすめで進めて」）", ""] + ["- " + x for x in BUILT] + ["",
         "## やったこと", ""] + ["- " + x for x in DONE] + [""]
    L += ["## 重さ（サーバーの 1 tick。20 秒の間、`/skyabyss dev load`）", ""]
    L += ["- %s: %s" % (k, v) for k, v in load] + ["", "1 tick が 50 ms を超えると遅れが出る。どこも平均は 14 ms 以下。いちばん遅い一回（都・野）は、着いた直後に土地を読み込んだとき。", ""]
    L += ["## ダンジョン専用の敵の形（372〜383。答えた）", "",
          "一つのダンジョンに道中の敵 2 種と中ボス 1 体。絵は斜め前・横・斜め上の後ろ。左の黄色い目盛りが人の背（1.8 ブロック）。選んだ形を作り込み（動き・絵・骨）、技と予告をつけてダンジョンの敵の表に入れる。", ""]
    for qq in FOE_Q:
        L += ["### %d. %s" % (qq["no"], qq["title"]), "", qq["why"], "", "絵: `img/%s_{a,b,c}.jpg`" % qq["img"], ""]
        for o in qq["options"]:
            L.append("- **%s**%s%s" % (o["label"], "（推奨）" if o.get("rec") else "", "。" + o["sub"] if o.get("sub") else ""))
        L.append("")
    with open(os.path.join(OUT, "index.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L))


def write_html(load):
    qs = [dict(qq, group="foe") for qq in FOE_Q]
    sealed = "".join('<figure><img src="img/%s.jpg" alt="%s" loading="lazy" width="960" height="540"><figcaption>%s</figcaption></figure>' % (n, esc(c), esc(c))
                     for n, c in SEALED)
    rows = "".join("<tr><th scope=\"row\">%s</th><td>%s</td></tr>" % (esc(k), esc(v)) for k, v in load) or "<tr><td colspan=\"2\">測っている途中</td></tr>"
    built = "".join('<figure><img src="img/%s.jpg" alt="%s" loading="lazy" width="960" height="540"><figcaption>%s</figcaption></figure>' % (n, esc(c), esc(c))
                    for n, c in BUILT_SHOTS)
    page = (TEMPLATE.replace("%%BUILT%%", items(BUILT)).replace("%%BSHOTS%%", built).replace("%%DONE%%", items(DONE)).replace("%%SEALED%%", sealed).replace("%%LOAD%%", rows)
            .replace("%%DATA%%", json.dumps(qs, ensure_ascii=False)))
    with open(os.path.join(OUT, "page.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page)


def template():
    src = open(os.path.join(ROOT, "tools/gen/review/pets_dungeons_page.py"), encoding="utf-8").read()
    t = src.split('TEMPLATE = r"""', 1)[1].split('"""', 1)[0]
    style = t.split("<main>", 1)[0].replace("<title>名札・乗り物・地下の案 11/02</title>", "<title>ダンジョンの敵の案 11/02</title>")
    script = "<script>" + t.split("<script>", 1)[1]
    body = """<main>
  <header style="display:grid;gap:8px">
    <h1>ダンジョンの敵の案</h1>
    <p class="lede">2026-11-02 夜の続きです。小さな直しと都の空き家は済ませ、重い場所も測りました。ダンジョンの道中の敵と中ボスは、選んでもらった 12 体（どれもおすすめ）を作り込み、技と予告をつけて入れました。今選んでほしいものはありません。</p>
  </header>

  <section>
    <h2>入れた（372〜383 = みな推奨）</h2>
    <ul>%%BUILT%%</ul>
    <div class="shots">%%BSHOTS%%</div>
  </section>

  <section>
    <h2>やったこと</h2>
    <ul>%%DONE%%</ul>
    <div class="shots">%%SEALED%%</div>
  </section>

  <section>
    <h2>重さ（サーバーの 1 tick）</h2>
    <p class="fixed">20 秒の間の 1 tick の時間（平均・遅いほうから 5%・いちばん遅い）。50 ms を超えると遅れが出る。どこも平均は 14 ms 以下。いちばん遅い一回（都 196 ms・野 151 ms）は、着いた直後に土地を読み込んだとき。</p>
    <table class="load"><tbody>%%LOAD%%</tbody></table>
  </section>

  <div class="bar">
    <span class="count" id="count">選んだ数 0 / 0</span>
    <button type="button" id="allrec">まだ選んでいないものを、おすすめで保存</button>
    <span class="state" id="dbstate">選ぶと保存されます（この画面を開いた人みなで同じ記録）。</span>
  </div>

  <section>
    <h2>ダンジョン専用の敵の形（372〜383。答えた）</h2>
    <p class="fixed">一つのダンジョンに道中の敵 2 種と中ボス 1 体。絵は斜め前・横・斜め上の後ろ。左の黄色い目盛りが人の背（1.8 ブロック）。形はラフで、選んだ案を作り込み（動き・絵・骨）、ゲームの中で撮ってから入れます。</p>
    <div class="qs" data-group="foe"></div>
  </section>
</main>

"""
    extra = """.load { border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }
.load th, .load td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); vertical-align: top; overflow-wrap: anywhere; }
.load th { font-weight: 400; color: var(--gold); white-space: nowrap; }
</style>"""
    style = style.replace("</style>", extra, 1)
    return style + body + script


TEMPLATE = template()

if __name__ == "__main__":
    copy_imgs()
    load = read_load()
    write_md(load)
    write_html(load)
    print(len(FOE_Q), "questions,", len(load), "load rows")
