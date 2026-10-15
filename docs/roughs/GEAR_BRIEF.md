# 武器・盾・弓をモブと別のモデルにする (共通の決まり)

ユーザーの方針: 武器のモデル・アニメーションと、モブのモデル・アニメーションは別々に作って、表示のときに合体させる。
武器をモブの geo に焼き込むと、(1) 体と重なる部分を「重なり禁止」のために消すことになり形が欠ける、(2) 手に固定されて攻撃が不自然になる。

## 仕組み
- モブの geo には武器の立方体を入れない。代わりに手の先に「受け口」の骨を置く (立方体なし):
  - `socket_main` (利き手。右手の子)、`socket_off` (反対の手。左手の子)。pivot は握りの中心。骨の回転で武器の向きを決める。
- 武器は2種類のどちらかで描く:
  1. **アイテムとして描く** (番兵長の鉾など、プレイヤーも持つ武器): GeckoLib の `BlockAndItemGeoLayer` で、受け口の骨にアイテム (例: skyabyss:captain_pike の3Dモデル) を描く。アイテムの3Dは持ったときと同じ見た目になる。
  2. **独立した GeckoLib モデルとして描く** (弓・盾など、モブ専用で自分の動きがあるもの): `assets/skyabyss/geo/gear/<名前>.geo.json`、`textures/gear/<名前>.png` (+ `_glowmask.png`)、`animations/gear/<名前>.animation.json`。共通の描画層 `client/entity/GearLayer.java` (担当: 番兵長の担当者が作る) が受け口の骨に描き、モブの状態に合わせて武器のアニメーション (例: 弓の弦を引く) を流す。
- `GearLayer` の使い方 (この形で作る / この形で使う):
  ```java
  // 受け口 socketBone に、gear/<gearName> のモデルを描く。animation は毎フレーム呼ばれ、流したいアニメーション名 ("idle", "draw" など) を返す
  renderer.addRenderLayer(new GearLayer<>(renderer, "socket_off", "rib_archer_bow", mob -> mob.isAiming() ? "draw" : "idle"));
  // アイテムを描く場合
  renderer.addRenderLayer(GearLayer.item(renderer, "socket_main", mob -> new ItemStack(ModItems.CAPTAIN_PIKE.get())));
  ```
  アニメーション名は `animation.<gearName>.<名前>`。モブ側の状態は、既存の triggerAnim / 同期データから読む (足りなければ、そのモブの担当が同期データを足す)。
- 重なり検査は、モブ本体と武器それぞれで行う (武器と体の間は検査しない。ただし通常の姿勢で武器が体に深く刺さって見えないようにする)。
- 立方体 0.5 単位・1単位 = 2画素・per-face uv・正面は -Z・molang の sin は度、は従来どおり (docs/roughs/IMPLEMENT_BRIEF.md)。
- 直した後の姿を tools/rough/voxel.py で描き、docs/roughs/v4/<subject>.png に出す (武器を受け口の位置に合成して描く)。Read で必ず確かめる。
- git commit しない。docs/roughs/IMPLEMENTED.md に節を足す。`./gradlew compileJava --offline` を通す。
