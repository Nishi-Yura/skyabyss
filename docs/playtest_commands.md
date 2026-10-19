# 試遊用コマンド

OP (権限 2) 用。製品の jar でも使える。どれも最後に `[対象]` (`@a` `@p` 名前など) を付けられる。省略すると自分。パーティなら全員を指定する。

## いちばん使うもの

| コマンド | 働き |
|---|---|
| `/skyabyss playtest now` | 今の目的の出来事を、その場で本物の仕組みのまま起こす。語り部・イサナを目の前に出す、空の影・足音・歌・岩鯨の初見と鎖を起こす、次の巨神を近くに降ろす、試練を済ませて心臓の前へ移す、など。起こしたことと次にやることを 1 行で出す |
| `/skyabyss playtest next` | 今の目的を済ませて、その次の出来事を起こす (連打で先へ進める) |

## ほかのコマンド

- `playtest skip opening` 序章を済ませる (書・語り部・イサナ・歌・メインクエストを「岩鯨を見る」まで)
- `playtest whale` 岩鯨の初見の場面と、目の前に鎖を下ろす
- `playtest board <whale|tide|storm|ember>` その背中の入口へ乗せる (前の巨神は止めた扱い。呼ぶ・降ろす・乗るまで自動)
- `playtest goto <titan> <休み場 id|heart|hub>` 背中の中を移す (外にいれば先に乗せる)。休み場 id は岩鯨 `hub w0 w1 w2 e0 ce1 heart`、潮殻 `hub g0 g1 s0 f0 heart`、雷翼 `hub w0 w1 e0 e1 f0 heart`、余燼 `throat hub w0 w1 e0 s0 heart`
- `playtest trials <titan>` その巨神の試練・石を、対象のグループで済ませ、扉を開ける (岩鯨は広間・扉まで。潮殻・雷翼・余燼は石と中ボスの記録まで。扉が足りなければ `goto` か手で)
- `playtest gear <0-4|titan>` 装備・食料・回復薬・体力・レベルを揃える。0 = 岩鯨に挑む (鉄)、1 = 潮殻 (岩鯨の装備)、2 = 雷翼、3 = 余燼、4 = 全部終えた後。備えの灯が緑になる体力まで上げる
- `playtest chapter <1-4>` その章の始めへ (前の巨神は止めた扱い、メインクエスト、装備、その巨神を近くに降ろす)
- `playtest heal` 全快 (効果も消す)
- `playtest day <n>` オーバーワールドを n 日目の朝にする
- `playtest fast <on|off>` サーバーを 2 倍速 (`tick rate 40`) / 等速。昼夜も巨神の時間も速くなる
- `playtest reset` 序章・クエスト・伝承・場面・巨神の進み・成長を最初に戻す
- `playtest god <on|off>` 無敵 (テスト用)
- `skyabyss dev trial build|list|board|cycle <n>|force <ID>…|mods <ID> <符号>|tp <ID|deck|hub>` 試練のプール (29)。`build` は岩鯨の背へ試練の階を置き直す (版 19 から建設で置く)。`tp hub` は鼓動の間、`tp <ID>` は新作のセルの入口
- `playtest help` 一覧

## 例

solo、最初から岩鯨の心臓前まで:

```
/skyabyss playtest now          (語り部が出る。話す)
/skyabyss playtest next         (連打して序章を進める)
/skyabyss playtest board whale
/skyabyss playtest trials whale
/skyabyss playtest goto whale heart
```

全員を岩鯨の心臓前へ:

```
/skyabyss playtest board whale @a
/skyabyss playtest trials whale @a
/skyabyss playtest goto whale heart @a
```

三章から始める (全員):

```
/skyabyss playtest chapter 3 @a
/skyabyss playtest board storm @a
```

やり直し:

```
/skyabyss playtest reset @a
```

## 注意

- `trials` はグループ単位。パーティを組んでから実行する。
- 心臓を止めたあとの運び出し・眠りは普通に進む。眠った巨神は 40 秒で地上から去る (見守りは眠りのあいだ呼び直さない。目覚めたらまた現れる)。`next` で次の巨神へ。
- `next`・`trials` は心臓を止めずに進みだけ記すので、巨神は眠らず・去らない。巨神を眠らせて去らせたいときは心臓を倒すか、`/skyabyss titan end sleep <巨神>`。
- 巨神の子を呼ぶ（出会った巨神の笛。成体は動かない。docs/design/28）: 旅嚢（Y）か手に持って吹くと、足もとに子が現れる。右クリックで乗る（仲間は笛か右クリックで同じ子に乗る。最大 6 人）。持ち主が乗ると海図が開き、行き先を選ぶと 3 秒（仲間待ちは最大 30 秒）で出発する。出会いの記録は `/skyabyss travel met`（全巨神）か `unlock <巨神>`。`/skyabyss travel young <巨神>` は条件を見ずに子を出す、`go <x> <z>` は行き先を座標で決める。確かめる自己テスト: `SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY=young ./gradlew runServer`。
- 背中から地上へ戻る: その巨神の呼び笛（旅嚢）を背中の中で使う → 「地上へ下りる？」→ 5 秒以内にもう一度。`/skyabyss titan leave` でも戻る。試練を越えると 5 分だけ出る光の中でしゃがむ (1.5 秒) と、その背中の真ん中へ戻る。
- `fast on` は全員に効く。試遊が終わったら `fast off`。
