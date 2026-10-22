# -*- coding: utf-8 -*-
"""One-off: inserts the 'body solidity' section into docs 02 / 08 / 12 / 13 (before their open-questions heading)."""
import os
import re

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'docs', 'design')

COMMON = """## {num}巨神の体に入れない・透けない・重い（titan/solid、client/lowpoly、client/titan）

ベータで「巨神のモデルがスケスケで中が見える。重量感が無い。入口以外は近づけないで」と言われた直しの今の仕様。四体（岩鯨・潮殻・雷翼・余燼）で共通。

- **透けない**: 休めの姿勢では四体とも骨ごとの面はほぼ閉じている（`tools/misc/titan_mesh_holes.py`。外から撃った光線の最初の当たりが裏面なのは 1.5% 以下で、透ける絵素もゼロ）。透けていたのは、ひれ・顎・首・翼が動いて継ぎ目が開いたときに見える骨の開いた縁。読み込み時（`LowPolyMesh.capHoles`）に、骨の開いた縁の輪を内側へ 2 割引っ込めた頂点との扇で塞ぎ、蓋の面は元の絵の一点を取って暗く（0.3 倍）出す。元の面の絵・法線は変えない。描画型は今まで通り `entityCutoutNoCull`（半透明にしない）。LOD の三段（近・中・遠）とも同じ処理。
- **重い**: 同じ読み込みで、頂点ごとに明るさを焼く（`LowPolyMesh.weightShade`）。体の下ほど暗く（底 0.52 倍から 7 割の高さで 1.0）、面が下を向けばさらに 0.82 倍、横向きは 0.96 倍。光る層（発光マスク）には掛けない。体の真上から見た輪郭（`TitanVolume.shadow`）を 4 ブロックの升目で敷いた大きな柔らかい影を、地面・海面（水の上は水面）に落とす（`client/titan/TitanWeight`）。縁は升目の角ごとの濃さでぼかし、高いほど薄い（最大 0.5）。影が見えるよう、岩鯨・雷翼の描画の範囲（`getBoundingBoxForCulling`）は下へ 110 ブロック伸ばした。体の下の柱を 2 tick ごとに数か所選んで（`client/titan/TitanFx`）、雲の高さに体がかかれば雲のちぎれ、海面にかかれば波しぶきと泡、底が地面に近く動いていれば土ぼこりを出す（カメラから 110 ブロック以内、1 体 1 tick に最大 4 か所）。足音・うなりの音は、素材がまだ無いので付けていない。台本 `titanweight on|off` で、焼き込みの陰・蓋・影・粒をまとめて外せる（前後の比べ用）。
- **動き**: 岩鯨の泳ぎは 8 秒 → 12 秒周期（頭・顎・尾・尾びれの波 6 秒、ひれ 3 秒、背骨ぞいの遅れを大きく）。雷翼の羽ばたきは 3 秒 → 4.5 秒、振れ幅 28° → 20°、第二の波を足して打ち下ろしを遅く、手は翼より 80° 遅れる。潮殻・余燼は元から 8〜10 秒の周期なので変えない。書き換えは `tools/misc/titan_heavy_anim.py`。
- **体に入れない**（サーバーが決める）: 巨神の名前の体積の表（`data/skyabyss/titan_volumes/<名前>.json`）を `tools/gen/world/gen_titan_volumes.py` が近景のメッシュから焼く。局所（横 = 左が +、上、前）の 2 ブロック四方の柱ごとに「詰まった区間」の並び。メッシュを 3 ブロック膨らませた「立入禁止」（人が入れない）と、0.5 ブロックだけ膨らませた「体」（矢と弾が消える・人が入ったら一息に外へ出す）の二枚。動く部分は余分に膨らませる（雷翼の翼 12・手 18、ひれ 3、尾びれ 4 など）。
  - 人（`TitanSolids.onPlayerTick`）: 体の真ん中（足の 0.9 上）が立入禁止に入ったら、いちばん近い外へ向かう速さをもらう（縁で 0.2、深いほど強く最大 1.4 ブロック/tick。入ってきた向きの速さは消す）。行き先に立てない向き（地面や壁の中）は選ばない。体そのものに入った・40 tick 居続けた・深さ 7 ブロック超なら、外へ移す。観戦者・乗っている人・鎖の巻き上げ中・巨神での移動に加わっている人は対象外。エリトラや創造の飛行も同じ。
  - 矢・弾（`TitanSolids.onProjectileTick`）: 前の tick の位置から今の位置までを 1 ブロックごとに調べ、体に入ったら消す（細いひれを抜けない）。
  - カメラ（`TitanSolidClient`）: 三人称のカメラが体に当たったら手前へ引く。人が 3 ブロック外にいるので、一人称は体に入らない。
  - 通り道（`SolidTitan.corridors`。局所の箱。ここは立入禁止でも体でも入ってよい）: {corridors}
- **自己テスト**: `SKYABYSS_SELFTEST_ONLY="titan solid"`（`TitanSolidSelfTest`）。体積が読める・体の中の点は押し出されて立入禁止の外に出る・通り道は開き、その隣は閉じている・向きを回しても局所と世界が往復する・16 方向 × 3 つの高さ × 3 つの速さ（歩き・走り・創造の飛行）で近づいても体に入れない（通り道へ入った人は乗り込みとして数える）・毎 tick 0.5 ブロック進みながら回る巨神の前に立つ人は体に入らない・本物の人（FakePlayer）が体の中で押される/深ければ出される・通り道と観戦者は動かない・体の中の矢と速い矢は消え、遠い矢は残る。
- **撮影**: `tools/shots/titan_solid.txt`（`tools/gen/shots/gen_titan_solid_shots.py` が書く）→ `docs/review/previews/titan_solid_sheet.png`。四体の近景・下から・横を、`titanweight off`（前）と `on`（後）で並べる。
- **残り**: 巨神が速く回る雷翼（1 tick 3°）の翼の先は、押し出しが追いつかず体の中に入られることがある（そのときは外へ出される）。足音・うなりの音。ゲーム内の通しの手ざわり（マルチでの近づき方・カメラ）はユーザーの確認待ち。

"""

SPECIFIC = {
    '02_stone_whale.md': ('岩鯨の ', '背中の鎖の下（横 5〜16、後ろ 25.5〜35.5、上は鎖の巻き上げの終わりまで）と、乗り場の鎖の下（頭の左の頬、横 8〜20、前 39〜49、上は座席まで）。'),
    '08_tide_shell.md': ('潮殻の ', '潮門の口（横 27〜46、後ろ 25〜39、水面の下から高さ 17 まで）と、その前の水の上（横 38〜62、舟寄せ）。'),
    '12_thunder_wing.md': ('雷翼の ', '昇り風の柱（左へ 14、前 -4。横 8〜20、前 -10〜2）の根もとから腰の門の上（高さ 30）まで。'),
    '13_ember.md': ('余燼の ', '熱の口（前へ 83、舌の高さ 3。横 ±6.5、前 76〜114、高さ 0〜15）の中と、その前の外。'),
}

for fn, (_, corr) in SPECIFIC.items():
    p = os.path.join(D, fn)
    s = open(p, encoding='utf-8').read()
    if '巨神の体に入れない・透けない・重い' in s:
        continue
    nums = [int(x) for x in re.findall(r'^## (\d+)\. ', s, re.M)]
    nxt = max(nums) + 1 if nums else 0
    m = re.search(r'^## (?:\d+\. )?未決', s, re.M)
    if not m:
        m = re.search(r'^## 旧節番号', s, re.M)
    # number after the last numbered section that is not the open-questions one
    body_nums = [int(x) for x in re.findall(r'^## (\d+)\. (?!未決)', s, re.M)]
    num = ''
    sec = COMMON.format(num=num, corridors=corr)
    s = s[:m.start()] + sec + s[m.start():]
    open(p, 'w', encoding='utf-8').write(s)
    print(fn, 'inserted as', num)
