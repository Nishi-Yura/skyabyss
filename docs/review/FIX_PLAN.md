# 修正の分担 (2026-10-16)

根拠: docs/review/whale_completeness.md (以下 W)、docs/review/assets_audit.md (以下 A)。
全員: git commit しない。自分の持ち物でないファイルは触らない (共有ファイルは Edit で小さく、直前に読み直す)。`./gradlew compileJava --offline` を通す。docs/roughs/IMPLEMENTED.md ではなく docs/review/FIX_LOG.md に自分の節を追記する。

| 担当 | 持ち物 (書いてよいファイル) | 内容 |
|---|---|---|
| T 試練 | titan/dungeon/TitanBackTrials.java, TitanBackData.java, TitanBackRules.java, TitanBackSelfTest.java, HeartArenaBuilder.java, TitanBackBuilder.java (復活位置まわり), entity/GuardMob.java, entity/StoneWhaleHeart.java, entity/WhaleboneCaptain.java (保存・ピースフルの扱いのみ), 02 の §4・§7・§8 | W の must-fix 2・3・4・5・6・7 と関連する should-fix |
| H 岩鯨 | titan/StoneWhaleEntity.java, TitanEvents.java, TitanTravel.java, WhaleChainEntity.java, 新しい TitanWorldData など, 02 の §2 | W の must-fix 1 と、鯨・鎖・乗り降りの should-fix |
| L 報酬 | loot/** (TitanLoot, TitanMemoryData), 報酬の lang キー, 報酬に関わる item の説明文, 02 の §6, 03 の岩鯨の節 | 周回前提の報酬に作り直す + W/A の報酬メッセージの修正 (A の M2) |
| A 素材 | textures/item/3d/*, tools/gen_*.py と tools/rough/*.py の上書き防止, 報酬以外の lang の文言, GuardRenderers の番兵の発光層 | A の M1 と should-fix・片付け |
| D 設計 | docs/review/dungeon_design_review.md (新規), 02 の §1・§3・§5・§9 と冒頭 | 01・02 から見たダンジョン全体の見直し + 02 の食い違い (W の must-fix 8) |

## 担当どうしの約束 (API)
- 報酬の入口は L が作る。T は撃破時にこれを呼ぶ:
  - `TitanLoot.onHeartDefeat(ServerLevel level, List<ServerPlayer> participants)` — 心臓を倒したとき
  - `TitanLoot.onCaptainDefeat(ServerLevel level, List<ServerPlayer> participants)` — 番兵長を倒したとき
  - `participants` は「その戦いに参加した人」(戦いの開始時からその部屋にいた人、途中で死んで復活待ちの人も含む。島にいるだけの人は含まない)。T が数える。死んでいて受け取れない人の分は、L が「次に復活したとき渡す」ように保存する。
  - 道中の雑魚の低確率ドロップは L が loot table かイベントで扱う。
- T と L は、上の名前と引数を変えない。どちらかが先にできていなくても、この形で書く。
