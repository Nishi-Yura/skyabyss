# 専用ブロックの棚おろし (バニラのまま使っている「意味のあるブロック」)

目的: 「modに関係するブロックは専用にして、modの要素だと気づけるようにする。バニラの認識は邪魔しない」(ユーザー 2026-10-20)。
調べ方: src/main/java の `Blocks.*` の使用を数えて分類 (飾りだけの SEA_LANTERN 78 箇所・石材などは対象外)。
見た目の案は docs/review/mod_blocks/index.html (各 3 案)。ここは「どこで使っているか」と「専用化の方針」。実装は選んだあと。

すでに専用なもの (そのまま): 碑 TABLET (語りの碑)、試練の碑 TRIAL_TABLET、試練の板 TRIAL_BOARD、墓 GRAVE、封印の扉 SEALED_GATE、共鳴石、灯の台、共鳴の炉、崩れる骨。
実装の注意: 専用化しても「バニラと同じ当たり判定・状態」を保つと、CampBlocks の許可表・各 Caches の `is(Blocks.CHEST)` 判定・自己テストの差し替えが少なく済む。

## 優先度 高 (見た目案を作った 8 種)

| # | バニラ | 使っている場所 (数) | 専用ブロック案 | 残す挙動 |
|---|---|---|---|---|
| 1 | CHEST (30) | 宝のキャッシュ: FinCaches (岩鯨の左右の尾びれ・胸びれの先)、TideCaches、StormCaches、EmberCaches、WingsBuilder、StormBirdBuilder、StormExtraBuilder、StormWingBuilder。CampBlocks の野営チェストは別扱い | 遺物の箱 `relic_cache` / Relic Cache。向きあり・開く蓋・光る錠 | 右クリック判定を `is(RELIC_CACHE)` に。取った印は PlayerPersisted のまま。野営用チェストは自分で置く道具なのでバニラのまま |
| 2 | CAMPFIRE / SOUL_CAMPFIRE (14 / 17) | 休み場所: TitanBackBuilder・TitanBackTrials・各 Kit (WhaleDeck/Wings/Tide/Storm/Ember)・TideIslandBuilder。Graves・NpcSpawner (墓の灯・語り部の火)、CampBlocks | 休み火 `rest_hearth` / Rest Hearth。冷えた/灯った (LIT)。墓の火は同じ様式の「墓火」版にもできる | 休息・復活地点・炎のダメージなし。煙は上へ |
| 3 | LEVER (16) | 仕掛け: StormCircuit・StormWingBuilder・RaijuNest (雷)、TideWatch・TideDeckBuilder・T07Seas (潮)。CampBlocks は拒否判定のみ | 遺物のレバー `relic_lever` / Relic Lever。入で台座の溝が光る | POWERED・FACE (床/壁/天井)・赤石の出力。StormCircuit の `is(Blocks.LEVER)` 判定を差し替え |
| 4 | (ボタン) | いまは使用 0。今後の押す仕掛け用 | 遺物のボタン `relic_button` / Relic Button。押すと沈み、輪が光る | 向き・押している刻・出力はバニラのボタン相当 |
| 5 | POLISHED_BLACKSTONE_PRESSURE_PLATE (1) | TideWatch (見張りの潮の板) | 遺物の感圧板 `relic_plate` / Relic Plate。踏むと沈んで縁が光る | 踏んだ間だけ出力 |
| 6 | 看板 (本編はほぼ無し。自己テストと opening の SiteKit.writeSign が残る) | 開幕の遺跡 (RibBuilder 等)。LORE_SIGNS はすでに TABLET で置換済み | 新ブロックは不要。語りの碑 TABLET の見た目を作り直し、SiteKit の看板だけ TABLET に差し替え | 目を向ける/右クリックで字幕に文 |
| 7 | 試練の封印 (IRON_BARS 11 と封印の見た目) | CleftCave (割れ目の格子)、EmberBodyBuilder・AshSmithArena (鍛冶の格子)。閉じる扉は SEALED_GATE (専用済み) | 試練の封印 `trial_seal` / Trial Seal。閉じた目 → 開いた目。※「試練を越えた印」ブロックは別の人が作成中なので、ここは見た目案だけ。格子は見た目だけ専用の `relic_grate` にする案も可 (任意) | 封印中は通れず、越えると開く (SealedGateBlock と同じ OPEN)。グループごとの見た目の上書きは HubTablets と同方式 |
| 8 | BELL (7) | 鐘楼 BellTower、WhaleDeckBuilder、StormExtraBuilder、ShelterBuilder (開幕)、CampBlocks | 遺物の鐘 `relic_bell` / Relic Bell。鳴る間は音の輪が光る | 叩くと鳴る。BellTower の `is(Blocks.BELL)` 判定を差し替え。音は自前に差し替えも可 |

## 優先度 中 (案は出さない。上の様式を流用して後で)

| バニラ | 使っている場所 | 専用化の方針 |
|---|---|---|
| LECTERN (6) | SongChamber・T06Choir (歌の間)、WhaleDeckBuilder・StormExtraBuilder・TideDeckBuilder (語りの台) | 語りの台 `relic_lectern`: 碑と同じ様式の斜めの台。歌の儀式の判定は据え置き |
| REDSTONE_LAMP (5) | AnemoneLights・EmberZones (灯の合図) | 合図の灯 `signal_lamp`: 点灯色を巨像ごとに。点灯 = LIT |
| LIGHTNING_ROD (20) | Lookout (見晴らし)・StormVane (風見) と嵐の背の飾り | 見晴らし・風見の役がある箇所だけ専用に。飾りの杭はバニラのまま |
| LANTERN / SOUL_LANTERN (7 / 11)、TORCH 系 (6) | 道しるべ・区画の灯 (TitanBackTrials・ZonePalette・W11Coffin)・RouteLamps | 道しるべに使うものだけ `route_lamp`。区画の飾りの灯はバニラのまま |
| BARREL (3)・CRAFTING_TABLE (7)・FURNACE (2)・ANVIL (2) | CampBlocks の野営の道具、EastWingBuilder の樽 | 野営の道具は自分で置くものなのでバニラのまま。樽 (飾り) は箱の専用ブロックで足りる |
| TRIPWIRE_HOOK・NOTE_BLOCK・TARGET・OBSERVER・DISPENSER・BEACON | ほぼ CampBlocks の「置けない場所」リストだけ | 作らない |
| 扉・フェンスゲート・トラップドア | 本編では未使用 (自己テストのみ) | 必要になったら封印の見た目を流用 |

## 実装の順 (選ばれたあと)
1. 巨像ごとに見た目を変える仕組み (HubTablets の「そのグループの画面でだけ光る」と同じ見かけの上書き) があれば、1 つのブロックで A/B/C を巨像別の様式に出し分けることもできる。
2. 箱 → 休み火 → レバー → 鐘 → 感圧板/ボタン → 碑 → 封印、の順 (使われている数が多い順)。
3. 差し替えはビルダーの `Blocks.X` → `ModBlocks.X` と判定側 (`is(Blocks.CHEST)` 等) を同時に。LAYOUT_VERSION を上げ、自己テストの期待値も更新。
