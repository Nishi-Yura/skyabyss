# 修正の記録

各担当が自分の節を追記する (分担は FIX_PLAN.md)。

## A 素材 (2026-10-16)

根拠は assets_audit.md。`./gradlew processResources compileJava --offline` は通った。ゲーム内では確かめていない。

- M1 武器パレット: `textures/item/3d/{captain_pike,waterway_fang,whalebone_greatsword}.png` を 16x1 → 16x16 にした (1 行目を 16 回繰り返す)。
  モデルの uv は v=0.25〜0.75 (16 単位 = 画像全体) なので、16x16 でも 0 行目を指し、色は変わらない。
  3 つの `models/item/*.json` の `texture_size` を [16,1] → [16,16] にそろえた (バニラは読まない。Blockbench 用)。
  `tools/gen_weapon_models_v2.py` の `write_palette()` は常に 16x16 (行を繰り返す) を書くようにし、`texture_size` も [16,16] にした。
  一時フォルダで実行し、パレット 3 枚はバイト単位で、モデル 3 本は JSON として今と同じになることを確かめた。
  block atlas に入る `textures/item`、`textures/block` で、一辺が 16px 未満の画像はもうない (`3d/bone_spike_arrow.png` は 16x16)。
- S1 上書き防止: 次の 11 本は `SKYABYSS_ALLOW_REGEN=1` がないと、日本語の理由を出して何も書かずに終わる。
  `gen_block_textures.py`、`gen_mobs.py`、`gen_titan_model.py`、`gen_heart.py`、`gen_armor_model.py`、`gen_weapon_models.py`、
  `gen_weapon_models_v2.py`、`rough/rib_archer_v3.py`、`rough/captain_v3.py` (`--install` のときだけ)、`gen_sky_sounds.py`、`gen_titan_sounds.py`。
  止めるのは `__main__` のときだけなので、`gen_titan_sounds.py` から `gen_sky_sounds` を import するのは動く。
  `gen_weapon_models_v2.py` の古い 16x16 アイコンのコピーは、さらに `SKYABYSS_COPY_OLD_ICONS=1` のときだけ。
  `tools/README.md` に、採用版の生成元と、止めたスクリプトの一覧を書いた。
- S4 番兵の発光: `GuardRenderers.geo()` に `glow` 引数を足し、`bone_sentinel` だけ `AutoGlowingGeoLayer` を付けない。
  1 画素だけの `textures/entity/bone_sentinel_glowmask.png` を消した。
- S2 文言 (報酬のキーは L の担当なので触っていない):
  `descending`、`chain_hint` (岩鯨の背へ)、`entity.skyabyss.bone_spike` (骨棘)、`gear.skyabyss.tier.uncommon/rare` (上質 / 希少)、
  `gui.skyabyss.forge.max` (全角括弧)、`resonance_charm.desc` (使うと1分間は使えない)、`waterway_fang.desc` (en・ja とも `%%`)。
  括弧のうち `memory_soft`、`legendary_dupe`、`first_clear_hint` は報酬のメッセージなので L に残した。
- S3 欠片: 台座の看板 (`sign.skyabyss.pedestal.2/3`)、`pedestal_sleep`、`fragment_already` を「天の鍵の欠片」/ "Sky Key Fragment" と書くようにした (en・ja)。
  `mid_boss_reward` とアイテム名 (`resonance_shard` の改名など) は報酬側なので L に残した。
- C1 未使用キー: `entity.skyabyss.guard_captain`、`message.skyabyss.arena_begin`、`checkpoint.skyabyss.maze` を en・ja から消した (参照なしを確認)。
  `fragment_already` は残した。台座で受け取り済みの人にこれを出す (S6) のは `TitanBackTrials.java` なので T に残した。
- C2 防具: 表に出ない `textures/models/armor/stone_whale_layer_1/2.png` (256x128) を 16x16 の透明画像にした
  (`ModArmorMaterials` の Layer がこの名前を指すので、ファイルは残す)。
- S5 岩鯨のテクスチャ: `stone_whale.png` と `_glowmask.png` を 2048x2048 → 2048x1056 にした。使っている uv の高さは 1044 (近景) なので、
  1024 には収まらない。16 画素単位に切り上げた 1056 にし、geo 3 つ (近・mid・far) の `texture_height` を 1056 にした。
  `tools/install_stone_whale.py` も同じ高さで書き出すように直し、一時フォルダで実行して、骨と uv は同じ、画素は上 1056 行が一致、
  切り捨てた行は全部透明、アニメーションも同じ、を確かめた (src へはその出力を写した)。

## L 報酬 (2026-10-16)

周回前提の報酬に作り直した。表と期待周回数は 02 の 6 章、03 の 3.1・7.1〜7.3 節。
- `loot/TitanLoot.java`: FIX_PLAN の入口 `onHeartDefeat(ServerLevel, List<ServerPlayer>)` と `onCaptainDefeat(ServerLevel, List<ServerPlayer>)` を用意。
  どちらも参加者の UUID ごとに別々の抽選。番兵長の LivingDeathEvent での報酬 (E3 の範囲の人に配っていた) は消した。番兵長の報酬は T が `onCaptainDefeat` を呼んだときだけ出る。
  - 番兵長: 必ず出るのは経験値 40 だけ。欠片 60%、共鳴の欠片 35%、干し肉 20%、番兵長の鉾 5%。
  - 心臓: 天の鍵の欠片 (一人一回。`Record.fragment` と `TitanBackData.claimFragment` の両方で見る)、岩鯨の欠片 4〜7、共鳴の欠片 1〜3、心核 (初回必ず・以後 25%)、
    装備は部位ごとに別の抽選 (甲殻各 25%、大剣 20%、護符 20%、鉾 4%、顎牙 4%、補強板 2%)、結晶 40%、干し肉 50% (S8 の入手元)、矢 35%、護石 6%。
  - 巨神の記憶を「図鑑 9 品に新しい品が増えなかった撃破」の数に変えた。3 以上で 2 倍、5 で図鑑にない品を保証、全部あれば共鳴の欠片 10。
  - 道中の番兵 (`LivingDropsEvent`, 背中の上でプレイヤーが倒したとき): 欠片・共鳴の欠片・干し肉・矢・護石・甲殻 1% を、倒した人にだけ拾える形で落とす。水路の顎は欠片 10% のみ。
  - 受け取れない人 (倒れている・抜けている) と、持ち物に入りきらない分は `TitanMemoryData` に UUID ごとに預け (保存される)、復活・ログイン・`/titanmemory claim` で渡す。
    倒れている人に直接入れると TitanBackTrials の預かり物の戻しで上書きされる問題 (W の S4 後半) もこれで避ける。
  - 受け取った品を一行で知らせる (`reward_list`)。図鑑の新しい品は `gear_new`。`/titanmemory` は図鑑 9 品を表示。
- `loot/TitanMemoryData.java`: `Record.fragment`、預かり分 `Pending` (アイテムと経験値) を追加。
- lang (ja/en): A の M2 / W の S9 (`boss_reward`、`memory_clears`、`stone_whale_core.desc`)、`mid_boss_reward` (欠片の語を外した)、`memory_soft`・`memory_next_guaranteed`、
  全角括弧 (`memory_soft`、`legendary_dupe`、`first_clear_hint`)。新しいキー `reward_list`、`reward_nothing`、`gear_new`、`reward_pending`、`reward_overflow`、`reward_delivered`、`reward_none_pending`。
- 02 の 5.3 の表の「報酬（6.2節）」を 6.3節 に直した (節番号が変わったため)。
- 自己テスト (`TitanLoot.selfTest`): 1〜4 人 × 1 万組の模擬。甲殻一式 6.6 周、甲殻＋大剣＋護符 8.0 周、図鑑すべて 13.4 周 (最悪 25〜27)、
  4 人組の全員が甲殻一式 9.5 周、天井の発動 7.8%、新しい品の出ない撃破は最長 4 回、番兵長で経験値のみ 19.5%・鉾 5.0%。ほかに天井の境界・重複・全所持の検査。PASSED。
- 未対応 (T の持ち物): `TitanBackTrials.onHeartDefeated` が「島の生きている全員」を渡している (参加者に変える)。番兵長の撃破で `onCaptainDefeat` を呼ぶ箇所がまだない。

## H 岩鯨 (2026-10-16)

根拠は whale_completeness.md (W)。`./gradlew compileJava --offline` は通った。`SKYABYSS_SELFTEST=1 ./gradlew runServer` で `SELFTEST whale PASSED` と `SELFTEST PASSED` を確かめた。プレイヤーが入る実機では確かめていない。

- M1 / S7 岩鯨を見失わない・一体だけ: 新しい `titan/WhaleKeeper.java` と、独立させた `titan/TitanWorldData.java` (保存名 `skyabyss_titans` と `StoneWhaleSpawned` はそのまま。古いワールドも読める)。
  - 水平 640 ブロック以内にプレイヤー (観戦者を除く) がいる間と、誰かが背中にいる間は、岩鯨のいるチャンクに時間切れ付きのチケット `skyabyss_whale` (距離 2、60 tick) を張る。張り直しは岩鯨の外 (オーバーワールドの tick の後) から行うので、チャンクの境目で止まっても次の tick で動き出す。チャンクを移ったら古いチケットを外し、条件が外れたら外す。
  - 本物の UUID、最後の位置と向き、状態 (モード・待ち時間など) を 1 秒ごとに記録する。UUID が違う岩鯨と、オーバーワールド以外の岩鯨は、`EntityJoinLevelEvent` (ディスクからの読み込みを含む) で取り消す。記録のない古いワールドでは、最初に読み込まれた個体を本物とする。
  - 見守り: 本物が読み込まれていなければ、最後の位置にチケットを張って読み込み直し、200 tick で見つからなければ近くのプレイヤーのそばへ呼び直す。誰からも遠いまま 2 分たったとき、チケットを張っているのに 600 tick 動かないときも呼び直す (背中に人がいる間は呼び直さない)。呼び直した個体は状態を引き継ぎ、巡航から始め、降下までの待ち時間を 600〜2400 tick に収める。
  - `titan summon` / `rest` / `board` は記録の UUID で岩鯨を探す。summon は前の個体を消し (読み込まれていなければ次の読み込みで取り消される)、状態を引き継ぐ。二体にならない。
  - 岩鯨の地面の高さ調べは、読み込まれたチャンクだけを見る (±40 ブロック先の同期読み込み・地形生成を起こさない)。
  - 降下 (APPROACH) が 1800 tick で着かなければ、24 ブロック以内ならそこで休み、遠ければ巡航に戻る (見守りの「止まった」判定と別に、降りきれないまま止まらないように)。
  - 出現の日数は `getDayTime` ではなく `getGameTime` で数える (W の N3)。
  - 自己テスト: 一体目の登録とチケット、二体目の参加が取り消されること、呼び直しで古い個体が消え新しい個体が登録されること、状態の引き継ぎ (待ち時間の上限)。終わったら記録を元に戻す。
- S1 引き上げ中のログアウト: ログアウト (サーバー停止を含む) の時点で印を保存し、次のログインで背中の尾の台へ乗せる。背中の次元がなければスローフォール 40 秒。印なしで重力が切れたまま (サーバーが落ちた) なら、重力を戻して宙ならスローフォール 40 秒。
- S2 引き上げ中の死亡: `LivingDeathEvent`、次元の移動、復活で引き上げを外す。鎖の軸から水平 16 ブロックより離れたら打ち切る。打ち切ったとき (岩鯨が見つからない・離れた) は、宙にいればスローフォール 20 秒。
- 地上へ戻る: 錨石・落下・コマンドで戻るとき、岩鯨が読み込まれていなければ、記録の最後の位置から求めた鎖の付け根の下へ降ろす (以前は共有スポーン地点)。
- 鎖の当たり判定: 垂れている鎖がなくなったもの (岩鯨の呼び直しなどで残ったもの) は 2 秒ごとの確認で消える。
- 02 の §2: 2.1 (出現の条件と回数)、2.3 (降下のあきらめ)、2.4 (錨石と鎖の位置を今の値 30.5 / 10.5 / 19.5 / 14 に。W の M8)、2.5 (引き上げ中のできごと)、2.6 (戻る位置)、新しい 2.7 (見守り) を直した。§9 の `titan summon` の行 (D の持ち物) は「出現済みフラグを立てる」のままで、二体にならないことは 2.7 節に書いた。
- 触ったファイル: `titan/StoneWhaleEntity.java`、`TitanEvents.java`、`TitanTravel.java`、`WhaleChainEntity.java`、新規 `TitanWorldData.java`、`WhaleKeeper.java`、`docs/design/02_stone_whale.md` §2。
- 実機で見てほしいこと: シミュレーション距離の外で岩鯨が泳ぎ続け、降りてくること。引き上げ中のログアウトとログイン。`/kill @e[type=skyabyss:stone_whale]` の 10 秒後に呼び直されること。

## T 試練 (2026-10-16)

対象: W の must-fix 2〜7、関連する should-fix (S3・S4・S5・S6・S11、N1・N6・N7・N8)、設計レビューの必須1・3・4・7 (T の分)、A の依頼 (台座の文言)。

| 項目 | 直したこと | 主なファイル |
|---|---|---|
| M2 休み場 | `TitanBackData` に試練の番号 (Epoch、1 から。リセットで +1) を追加。休み場の記録に番号を書き、違えば無効 (尾の台)。乗り込み・地上へ戻る (次元の移動)・焚き火であきらめる・リセットで記録を消す (背中で倒れて連れ戻すときだけ残す)。ログイン時、最後に見た番号が古く体内にいる人は尾の台へ。リセット時に作り直す所・封じる所にいる人は鼓動の間へ移す。番号のない古い記録も無効 | TitanBackTrials, TitanBackData |
| M3 マルチ | 広間と心臓の間で共通の参加の決まり: 一人目が入ると 5 秒の予告 (チャット一回 + アクションバーの秒読み)、扉は開いたまま → 閉じるときに戸口と前室の人を扉の内側 2 マスへ移す → 中にいる人が参加者 (倒れても参加者のまま)。戦いの間、参加者は扉に触れると内側へ戻れる。制圧で出口と入口の両方が開く (E3 は全滅しても東の石へ戻れる)。人数による増加は参加者の数 | TitanBackTrials |
| 報酬の受け取り手 | `TitanLoot.onHeartDefeat(level, participants)` と `TitanLoot.onCaptainDefeat(level, participants)` を、参加者 (オンラインの人。倒れて復活待ちの人を含む) で呼ぶ。番兵長は `LivingDeathEvent` (印付き・背中の個体だけ) で呼ぶ | TitanBackTrials |
| M4 預かり物 | 静的な Map をやめ、`PlayerPersisted.skyabyss_kept` に保存 (再起動・保存して終了でも残る)。死亡イベントを LOWEST で受ける。消滅の呪いは預からない。返す前に再び倒れたら前の分を `skyabyss_kept_extra` に移し、目を覚ましたとき (またはログイン時) に足して返す (入らなければ足元へ)。Clone で両方の記録から消すので重複しない | TitanBackTrials |
| M5 二重のボス | `GuardMob.shouldBeSaved()` が試練の個体 (MOB_TAG / HEART_TAG) で false。古いセーブに残った個体は `EntityJoinLevelEvent` (loadedFromDisk) で取り消す。心臓の撃破は、背中の・印付きの・いま戦っている心臓 (heartId) だけが報告できる (N7/N8)。再起動後は制圧済みの広間の入口と出口も開け直す | GuardMob, StoneWhaleHeart, TitanBackTrials |
| M6 復活位置 | hub を中央の台座から南へ 6 マス。焚き火の休み場は腰掛けを避けて斜め 2 マス (N1)。実行時にも足元・頭・床を確かめ、近くを探す (`safeSpot`) | TitanBackTrials |
| M7 ピースフル | 試練の個体は `shouldDespawnInPeaceful` が false、`canAttack` でピースフルでもプレイヤーを狙える。攻撃はピースフルのときだけ generic の傷で与える (`GuardMob.attackSource`、番兵長の技も同じ)。射手の矢と心臓の骨の棘は 0 のまま。心臓が報告なしに消えたら 5 秒は湧かせ直さない (開け閉めとチャットの繰り返し防止) | GuardMob, WhaleboneCaptain, TitanBackTrials |
| S3 部屋の外への攻撃 | 心臓に心臓の間の範囲を渡し (`bindArena`)、狙いと全技の対象をその内側に限る。外へ出た人は狙いから外す | StoneWhaleHeart |
| S5・必須3 強さ | 心臓: 体力 450、防御 8。人数の倍率 1 + 0.6 ×(参加者 − 1) を、体力ではなく受ける傷を割る形で掛ける (体力の上限 1024 を超えないため。実質の体力は 450 × 倍率)。大技を 14→12 (突き・殻の落下)、12→10 (光線・鎖)。番兵長: 体力 280 × 同じ倍率 (上限 1024) | StoneWhaleHeart, TitanBackTrials |
| 必須4 予告 | 心臓の色の粒 (予告と輪) と番兵長の骨の噴出の輪を、64 ブロック以内の人ごとに overrideLimiter 付きで送る (`GuardMob.warn`)。輪の点の間隔を 1.2→1.6。殻の落下の予告音を砥石に (殻の再閉と同じ音だった)、番兵長の骨の噴出の予告音を召喚の溜め音に (咆哮と同じだった) | GuardMob, StoneWhaleHeart, WhaleboneCaptain |
| S6・必須1 周回 | 心臓を倒したあと、封印の先 (宝物庫・心臓の回廊・心臓の前室・心臓の間) に誰もいない状態が 30 秒続くと試練をリセット (島のほかの場所に人がいても)。全員に「岩鯨の心臓が、再び脈を打ち始めた」 | TitanBackTrials |
| S11 近道 | 背中ではエリトラの滑空を止める。西の翼 (W0・三つの谷・W1・W2) では跳躍とゆっくり落ちる効果を消す | TitanBackRules |
| A の依頼 | 台座を右クリックしたとき、欠片を受け取り済みの人には `fragment_already` を出す (`TitanBackData.hasClaimedFragment` を追加) | TitanBackTrials, TitanBackData |
| N6 自己テスト | 追加: すべての休み場の復活位置が立てる場所か / 休み場の記録が番号で無効になるか (番号なしの古い記録も) / リセットで番号が進むか / 試練の番兵と心臓が保存されないか / ピースフルで試練の心臓と番兵が 2 秒消えないか (終わったら難易度を戻す) | TitanBackSelfTest |

追加した lang キー (ja_jp・en_us): `message.skyabyss.gate_gathering`、`gate_countdown`、`gate_rejoin`、`rest_point_lost`、`trials_renewed`。

確認: `./gradlew compileJava --offline` 通過。`SKYABYSS_SELFTEST=1 ./gradlew runServer --offline` で `SELFTEST whale PASSED` と `SELFTEST PASSED`。最初の実行で、w0・w1・w2・e0・ce1・ce2 の復活位置が腰掛け (階段) に重なっていると検出された (N1) ので、斜め 2 マスへ移して通した。実機 (マルチ、再起動中の戦い、ピースフル) では確かめていない。

02 は §4.1・§4.2・§7 (7.1〜7.4、新しく 7.3.1 保存と再起動・7.3.2 ピースフル)・§8 を直した。ほかの担当へ:
- D: 02 冒頭 22 行の「島に誰もいなくなって3分たつと」に、心臓撃破後 30 秒のリセットを足してほしい。5.3節の「体力 600（人数による強化はない）」「防御 12」「開始: 内側に一人でも入ると入口が閉じ」「技のダメージ 14/12」を、体力 450 × (1 + 0.6 × (参加者 − 1))、防御 8、5 秒の予告と参加の決まり (4.2節)、突き・殻の落下 12、光線・鎖 10 に。想定の強さ (案:「初回は鉄一式＋鉄の剣か弓で、何度か倒れながらクリアできる」) も 5.3節へ。
- A: 看板 `sign.skyabyss.heart_gate.3/.4`「入れば扉は閉じ / 心臓が止まるまで」は、5 秒の予告のあとで閉じる今の形とずれていないか見てほしい。
- 決まっていないこと: ピースフルの扱い (今は戦える形。祠で止める案はユーザーの判断待ち)。

## P ピースフルと東の翼の短縮 (2026-10-16)

ユーザーの決定: (1) ピースフルでは祠で止める、(2) 東の戦いの広間を一つ減らす。あわせてコーディネーターの依頼で物語の碑文 5 枚を置いた。
`./gradlew compileJava --offline` は通った。`SKYABYSS_SELFTEST=1 ./gradlew runServer --offline` で `SELFTEST whale PASSED` と `SELFTEST PASSED`。プレイヤーが入る実機では確かめていない。

- ピースフル (`TitanBackTrials`):
  - 祠の階段の 5 段目 (z=19、x -1〜1、y 109〜112。`TitanBackLayout.shrineSeal()`) に、ピースフルのあいだだけ封印のブロックを置く。ほかの難易度に戻ると外す。読み込まれているときだけ触り、人が立っているマスは埋めない。
  - 封印の手前まで下りた人には、40 tick ごとにアクションバーへ `message.skyabyss.trials_peaceful` (チャットには出さない)。
  - 封印より奥の体内 (地表より下・島の底より上・z ≤ 19) にいる人 (クリエイティブ・観戦者を除く) は尾の台へ移し、同じ文をチャットに一度。ピースフルのままログインした人も次の tick で移る。
  - 戦いの途中でピースフルになったら (`sleepForPeaceful`): 印の付いた番兵と心臓をすべて消し、集合中・戦闘中・閉じたままの広間と心臓の間を告知なしで最初に戻す。ピースフルのあいだは広間と心臓の tick を止める。
  - 外したもの (`GuardMob`): `shouldDespawnInPeaceful` の上書き、ピースフルでも狙える `canAttack`、generic の傷で殴る `doHurtTarget`。`attackSource()` は常に `mobAttack` (番兵長と心臓の技が呼ぶので残した)。保存しない (`shouldBeSaved`) はそのまま。
  - lang (ja・en): `message.skyabyss.trials_peaceful` を追加。
- 東の翼の短縮 (配置の版 2):
  - 第二の広間 E2、旧・番兵長の控えの間 CE2、その間の通路を除いた。流れは E0 → E1 → CE1 (休み場 `ce1`、番兵長の控えの間) → 通路 (x 21〜24、z -40〜-38) → E3。
    CE1 の西の壁 x=25 に戸口 (`CE1_WEST_DOOR`)、E3 の入口 `E3_ENTRANCE` は E3 の東の壁 x=20 (z -40〜-38)。E3 の前室 (扉が閉じるとき中へ入れる範囲) は CE1 と通路 (`E3_ANTE`)。
  - E1 の波: 近接 (番兵 2・苔這い 2) → 射手 (射手 3・番兵 1。旧 E2 の第一波)。E3 は変えていない。
  - 休み場 `ce2` を消した。`ce1` の看板は「番兵長の控えの間」(`rest_e2`)、表示名 `checkpoint.skyabyss.ce1` を「番兵長の控えの間」に。`checkpoint.skyabyss.ce2` と `sign.skyabyss.rest_e1.*` を消し、`rest_e0` の「三つの広間」を「二つの広間」に。古い `ce2` の記録は知らない名前なので無効 (尾の台)。
  - 配置の版: `TitanBackLayout.LAYOUT_VERSION = 2`、`TitanBackData` に `LayoutVersion` (なければ 1) と `needsBuild()`。起動時に版が違えば、`resetProgress()` (試練の番号が進む) のあと島ごと建て直す (`buildIsland` が体内を埋め直すので、古い E2 の空洞は残らない)。`TitanTravel.board` の保険も同じ判定。
  - 推定所要時間: 東の翼 5〜8 分 → 3.5〜6 分 (波 6 → 4 と番兵長)。1 周は約 1.5〜2 分短くなる。
- 物語の碑文: `TitanBackLayout.LORE_SIGNS` の 5 枚を `TitanBackBuilder` の最後に掛ける。lore_heart (3, 96, -54) 西向き、lore_vault (-6, 96, -38) 東向き (提案の x=-5 は壁から離れていたので宝物庫の西端へ)、lore_west (-17, 99, -30) 北向き、lore_east (17, 96, -30) 北向き (提案の z=-31 は背に壁がないので南端へ)、lore_below (11, 83, -6) 北向き (同じく z=-7 → -6)。
- 自己テスト (`TitanBackSelfTest`): 部屋の一覧と広間の湧き場所を E1・E3 と通路に。戸口に CE1 の西の戸口・通路・E3 の入口を足し、旧 E2 の入口と旧通路を外した。除いた所が埋まっていること、祠の封印の場所がふだんは通れること、建てたあと配置の版が記録されること、碑文 5 枚が壁に掛かっていることを足した。ピースフルの段は「消えずに残る」から「封印が置かれ、番兵と心臓が消え、閉じた E1 と心臓の間の扉が開く → 難易度を戻すと封印が消える」に変えた。
- 02 を直した: §1、§3.1 (表・配置の版・碑文)、§3.2、§4.2 (構成・前室・波の表・所要時間)、§7.1、§7.3.2 (書き直し)、§7.4、§8。
- 実機で見てほしいこと: ピースフルで祠の階段を下りたときの見え方と文言、戦闘中に `/difficulty peaceful` にしたとき、通路の幅と E3 の入口の見え方、古いワールドを読み込んだときの建て直し。
