"""中ボス「灰の鍛え手」(ash_smith) と、大鎚・火箸・ボスバーの絵を、ゲームの素材として書き出す (13 の 5.2節、14 章の推奨 A 鞴の巨人)。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/install_ember_smith.py [--option a|b|c] [--no-preview]
出力:
  src/main/resources/assets/skyabyss/geo/entity/ash_smith.geo.json
  src/main/resources/assets/skyabyss/textures/entity/ash_smith{,_glowmask,_glowmask_white}.png
      (白の発光 _glowmask_white は、鞴が白く光る間 (吐いたあとの弱み) と怒りで、赤い発光の代わりに重ねる)
  src/main/resources/assets/skyabyss/animations/entity/ash_smith.animation.json
  src/main/resources/assets/skyabyss/{geo,textures,animations}/gear/{smith_hammer,smith_tongs}.*  (別のモデル。受け口 socket_main / socket_off に付く)
  src/main/resources/assets/skyabyss/textures/gui/boss/ash_smith_{frame,notches,icon}.png  (10 段のボスバー。帯は橙。描くのは AshSmithClient)
  docs/review/previews/ember_smith/*.png  (確認の絵。git には入れない)

形の元は rough/ember_smith.py の案 (14 章の推奨は A。--option で B・C に差し替えられる)。ここで足す・直すこと:
- アニメーション (意図した角度で書き、GeckoLib の符号はこのスクリプトが直す。install_ember_mobs.py と同じ)。
- 部屋の明るさで色が沈まない描き方 (08 の 19.8節): 基のテクスチャの暗い画素を持ち上げ (lift)、光る所は glowmask を目の光で足す (描くのは AshSmithRenderer)。
- 検査: 立方体の重なり 0 (静止)、光る画素が 0 でない、アニメの姿勢ごとの重なり (分離軸)。

座標・角度の決まりは install_ember_mobs.py と同じ (前が +z、y が上、1 単位 = 1/16 ブロック。書き出す geo は root 骨を yaw 180 度回す。
ANIM と骨の初めの角度は「意図した向き」で書き、ファイルには (-x, +y, -z) で書く)。
"""
from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "rough"))
import install_ember_mobs as M  # noqa: E402
import ember_smith as S  # noqa: E402

E = M.E
tm = M.tm
vx = M.vx
sn = M.sn

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/ember_smith"
BOSS = ASSETS / "textures/gui/boss"

REST = {"socket_main": S.SOCKET_REST}


# ---------------------------------------------------------------- アニメーション
# 値は意図した向き (度)。時刻は秒。サーバーの時間 (AshSmith.java) と合わせてある:
#   slam   溜め 20 tick (1.0 秒)。打つのは 21 tick めの手前 (キーは 1.05 秒)。
#   breath 吸う 30 tick (1.5 秒)、吐くのは 30 tick めで、扉を閉じるように 0.4 秒で縮む
#   sparks 溜め 16 tick (0.8 秒)、火花が出るのは 0.8 秒
#   quench 槽に着いてから 2.6 秒 (湯気が出るのは 0.6 秒から)
#   rage   1.5 秒 (30 tick)

BODY_REST = (0, 0, 0)


def pose_up(extra=None):
    # 鎚を高く振り上げた姿 (rough の POSE_UP)
    d = {"arm_r": (-160, 0, -8), "forearm_r": (-25, 0, 0), "arm_l": (-20, 0, 6), "forearm_l": (-15, 0, 0),
         "body": (-10, 0, 0), "head": (8, 0, 0), "socket_main": (20, 0, 0)}
    d.update(extra or {})
    return d


def keys(times, poses, bone, ch="rotation"):
    return {t: tuple(p.get(bone, (0, 0, 0))) for t, p in zip(times, poses)}


def track(times, poses, bones, ch="rotation"):
    return {b: {ch: keys(times, poses, b)} for b in bones}


ANIM = {
    "idle": {"loop": True, "len": 3.6, "bones": {
        # 重い息。胸が上下し、背の鞴がゆっくり膨らんで縮む。頭は肩の前でゆれる
        "body": {"rotation": [sn(1.3, 100), "0", sn(0.6, 100, 90)]},
        "head": {"rotation": [sn(2.0, 100, 40), sn(3, 50), "0"]},
        "arm_l": {"rotation": [sn(2.5, 100, 20), "0", f"2 + {sn(1.5, 100)}"]},
        "arm_r": {"rotation": [sn(1.0, 100, 10), "0", "0"]},
        "bellows_r": {"scale": [f"1 + {sn(0.03, 100)}", "1", f"1 + {sn(0.05, 100)}"]},
        "bellows_l": {"scale": [f"1 + {sn(0.03, 100)}", "1", f"1 + {sn(0.05, 100)}"]},
    }},
    "walk": {"loop": True, "len": 1.6, "bones": {
        # 短い脚で重く踏みしめる。上体を左右に揺らし、鎚の腕はあまり振らない
        "leg_r": {"rotation": [sn(-13, 225), "0", "0"]},
        "leg_l": {"rotation": [sn(13, 225), "0", "0"]},
        "arm_l": {"rotation": [sn(9, 225), "0", "2"]},
        "arm_r": {"rotation": [sn(-3, 225), "0", "0"]},
        "body": {"rotation": ["2", sn(3, 225), sn(2, 450)], "position": ["0", "-0.5 * math.abs(math.sin(225 * query.anim_time))", "0"]},
        "head": {"rotation": ["0", sn(-3, 225), "0"]},
        "bellows_r": {"rotation": [sn(1.5, 450), "0", "0"]},
        "bellows_l": {"rotation": [sn(1.5, 450, 90), "0", "0"]},
    }},
}


def slam_anim(length, rest_end, strong):
    t = [0.0, 0.45, 0.9, 1.05, 1.4, length]
    p0 = {}
    p1 = {"arm_r": (-105, 0, -6), "forearm_r": (-18, 0, 0), "socket_main": (12, 0, 0), "body": (-6, 0, 0), "head": (4, 0, 0),
          "arm_l": (-10, 0, 4)}
    p2 = pose_up()
    # 打ち下ろし: 上体を前へ倒し、鎚の頭を前の床へ。強い版 (冷えた鎚) は同じ形で、体を深く倒す
    p3 = {"arm_r": (-30, 0, -3), "forearm_r": (-10, 0, 0), "socket_main": (30, 0, 0), "body": (16 if strong else 12, 0, 0),
          "head": (-9, 0, 0), "arm_l": (14, 0, 4), "forearm_l": (-12, 0, 0)}
    p4 = dict(p3)
    p5 = rest_end
    poses = [p0, p1, p2, p3, p4, p5]
    bones = ["arm_r", "forearm_r", "socket_main", "body", "head", "arm_l", "forearm_l"]
    return {"loop": False, "len": length, "bones": track(t, poses, bones)}


ANIM["slam"] = slam_anim(1.7, {}, False)
ANIM["slam_cold"] = slam_anim(1.7, {}, True)

# 鞴の息: 背をそらして鞴を大きく膨らませ (吸う 1.5 秒)、前へ吐いて一気に縮む。吐いたあとの 3 秒は白く光る (描画の側で替える)
ANIM["breath"] = {"loop": False, "len": 2.3, "bones": {
    "body": {"rotation": {0: (0, 0, 0), 0.4: (-6, 0, 0), 1.45: (-14, 0, 0), 1.55: (10, 0, 0), 1.9: (8, 0, 0), 2.3: (0, 0, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 1.45: (10, 0, 0), 1.55: (-8, 0, 0), 2.3: (0, 0, 0)}},
    "arm_r": {"rotation": {0: (0, 0, 0), 1.45: (-10, 0, -22), 1.6: (-6, 0, -10), 2.3: (0, 0, 0)}},
    "arm_l": {"rotation": {0: (0, 0, 0), 1.45: (-10, 0, 22), 1.6: (-6, 0, 10), 2.3: (0, 0, 0)}},
    "bellows_r": {"scale": {0: (1, 1, 1), 0.3: (1.03, 1.0, 1.1), 1.45: (1.12, 1.03, 1.42), 1.55: (0.96, 0.98, 0.9), 2.0: (1, 1, 1), 2.3: (1, 1, 1)}},
    "bellows_l": {"scale": {0: (1, 1, 1), 0.3: (1.03, 1.0, 1.1), 1.45: (1.12, 1.03, 1.42), 1.55: (0.96, 0.98, 0.9), 2.0: (1, 1, 1), 2.3: (1, 1, 1)}},
}}

# 焼き入れ: 槽の前で身をかがめ、鎚の頭を槽へ沈める (2.6 秒)。湯気が出るのは 0.6 秒から
ANIM["quench"] = {"loop": False, "len": 2.6, "bones": {
    "body": {"rotation": {0: (0, 0, 0), 0.5: (26, 0, 0), 2.0: (26, 0, 0), 2.6: (0, 0, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 0.5: (-12, 0, 0), 2.0: (-12, 0, 0), 2.6: (0, 0, 0)}},
    "arm_r": {"rotation": {0: (0, 0, 0), 0.5: (-32, 0, 0), 2.0: (-32, 0, 0), 2.6: (0, 0, 0)}},
    "forearm_r": {"rotation": {0: (0, 0, 0), 0.5: (-30, 0, 0), 2.0: (-30, 0, 0), 2.6: (0, 0, 0)}},
    "socket_main": {"rotation": {0: (0, 0, 0), 0.5: (30, 0, 0), 2.0: (30, 0, 0), 2.6: (0, 0, 0)}},
    "arm_l": {"rotation": {0: (0, 0, 0), 0.5: (-18, 0, 8), 2.0: (-18, 0, 8), 2.6: (0, 0, 0)}},
    "leg_r": {"rotation": {0: (0, 0, 0), 0.5: (-10, 0, 0), 2.0: (-10, 0, 0), 2.6: (0, 0, 0)}},
    "leg_l": {"rotation": {0: (0, 0, 0), 0.5: (-10, 0, 0), 2.0: (-10, 0, 0), 2.6: (0, 0, 0)}},
}}

# 火花散らし: 火箸の塊を前へ掲げ、鎚で叩く (0.8 秒で叩く)。叩いたあとに腕を戻す
ANIM["sparks"] = {"loop": False, "len": 1.8, "bones": {
    "arm_l": {"rotation": {0: (0, 0, 0), 0.4: (-72, 0, 10), 1.2: (-72, 0, 10), 1.8: (0, 0, 0)}},
    "forearm_l": {"rotation": {0: (0, 0, 0), 0.4: (-22, 0, 0), 1.2: (-22, 0, 0), 1.8: (0, 0, 0)}},
    "arm_r": {"rotation": {0: (0, 0, 0), 0.4: (-118, 0, -10), 0.72: (-128, 0, -10), 0.82: (-62, 0, -6), 1.1: (-60, 0, -6), 1.8: (0, 0, 0)}},
    "forearm_r": {"rotation": {0: (0, 0, 0), 0.4: (-24, 0, 0), 0.82: (-14, 0, 0), 1.1: (-14, 0, 0), 1.8: (0, 0, 0)}},
    "socket_main": {"rotation": {0: (0, 0, 0), 0.4: (14, 0, 0), 0.82: (-6, 0, 0), 1.1: (-6, 0, 0), 1.8: (0, 0, 0)}},
    "body": {"rotation": {0: (0, 0, 0), 0.4: (-4, 0, 0), 0.82: (8, 0, 0), 1.1: (6, 0, 0), 1.8: (0, 0, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 0.4: (4, 0, 0), 0.82: (-6, 0, 0), 1.8: (0, 0, 0)}},
}}

# 怒り: 前掛けを投げ捨て (描画の側で隠す)、のけぞって両腕を広げ、鞴が脈打つ (1.5 秒)
ANIM["rage"] = {"loop": False, "len": 1.6, "bones": {
    "body": {"rotation": {0: (0, 0, 0), 0.3: (-16, 0, 0), 1.2: (-14, 0, 0), 1.6: (0, 0, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 0.3: (-18, 0, 0), 1.2: (-16, 0, 0), 1.6: (0, 0, 0)}},
    "arm_r": {"rotation": {0: (0, 0, 0), 0.3: (-36, 0, -62), 1.2: (-34, 0, -58), 1.6: (0, 0, 0)}},
    "arm_l": {"rotation": {0: (0, 0, 0), 0.3: (-36, 0, 62), 1.2: (-34, 0, 58), 1.6: (0, 0, 0)}},
    "forearm_r": {"rotation": {0: (0, 0, 0), 0.3: (-20, 0, 0), 1.2: (-20, 0, 0), 1.6: (0, 0, 0)}},
    "forearm_l": {"rotation": {0: (0, 0, 0), 0.3: (-20, 0, 0), 1.2: (-20, 0, 0), 1.6: (0, 0, 0)}},
    "bellows_r": {"scale": {0: (1, 1, 1), 0.3: (1.1, 1.02, 1.3), 0.6: (1.02, 1, 1.1), 0.9: (1.12, 1.03, 1.36), 1.2: (1.04, 1, 1.14), 1.6: (1, 1, 1)}},
    "bellows_l": {"scale": {0: (1, 1, 1), 0.3: (1.1, 1.02, 1.3), 0.6: (1.02, 1, 1.1), 0.9: (1.12, 1.03, 1.36), 1.2: (1.04, 1, 1.14), 1.6: (1, 1, 1)}},
}}

ANIM["hurt"] = {"loop": False, "len": 0.35, "bones": {
    "body": {"rotation": {0: (0, 0, 0), 0.08: (-4, 0, 2), 0.35: (0, 0, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 0.08: (-5, 0, -3), 0.35: (0, 0, 0)}},
}}

# 倒れる: 膝をつき、前へ崩れる。鎚は床に落ち、火箸は塊をつかんだまま
ANIM["death"] = {"loop": "hold", "len": 2.4, "bones": {
    "body": {"rotation": {0: (0, 0, 0), 0.7: (18, 0, 3), 1.6: (46, 0, 5), 2.4: (52, 0, 5)},
             "position": {0: (0, 0, 0), 0.7: (0, -7, 1), 1.6: (0, -13, 3), 2.4: (0, -14, 3)}},
    "leg_r": {"rotation": {0: (0, 0, 0), 0.7: (-48, 0, 0), 1.6: (-78, 0, 0), 2.4: (-80, 0, 0)},
              "position": {0: (0, 0, 0), 0.7: (0, -7, 0), 1.6: (0, -13, 0), 2.4: (0, -14, 0)}},
    "leg_l": {"rotation": {0: (0, 0, 0), 0.7: (-44, 0, 0), 1.6: (-74, 0, 0), 2.4: (-76, 0, 0)},
              "position": {0: (0, 0, 0), 0.7: (0, -7, 0), 1.6: (0, -13, 0), 2.4: (0, -14, 0)}},
    "head": {"rotation": {0: (0, 0, 0), 0.7: (14, 0, 6), 2.4: (24, 6, 8)}},
    "arm_r": {"rotation": {0: (0, 0, 0), 1.6: (4, 0, -14), 2.4: (8, 0, -18)}},
    "arm_l": {"rotation": {0: (0, 0, 0), 1.6: (-30, 0, 10), 2.4: (-34, 0, 12)}},
    "bellows_r": {"scale": {0: (1, 1, 1), 0.8: (0.9, 1, 0.82), 2.4: (0.8, 0.96, 0.7)}},
    "bellows_l": {"scale": {0: (1, 1, 1), 0.8: (0.9, 1, 0.82), 2.4: (0.8, 0.96, 0.7)}},
}}


# ---------------------------------------------------------------- 本体と武器

def lift(tex, gl):
    """基のテクスチャの暗い画素を持ち上げる (08 の 19.8節。部屋の明かりは弱く暖色で、暗い赤茶は泥のように沈むため)。
    光る画素 (glowmask) は触らない。持ち上げ: c' = 255 * (c / 255) ** 0.78 * 1.08 + 5"""
    out = tex.astype(float)
    a = (tex[..., 3] > 0) & (gl[..., 3] == 0)
    for ch in range(3):
        v = out[..., ch][a] / 255.0
        out[..., ch][a] = np.clip(255.0 * v ** 0.78 * 1.08 + 5, 0, 255)
    return out.astype(np.uint8)


def whiten(gl):
    """発光の色を白へ寄せた版 (吐いたあとの鞴・怒りの白い光)"""
    out = gl.astype(float)
    a = gl[..., 3] > 0
    white = np.array([255, 248, 232], float)
    out[..., :3][a] = out[..., :3][a] * 0.25 + white * 0.75
    return out.astype(np.uint8)


def mean_rgb(tex, gl=None):
    a = tex[..., 3] > 0
    if gl is not None:
        a &= gl[..., 3] == 0
    return tex[..., :3][a].mean(0).round(1).tolist()


def build_boss(opt):
    builders = {"a": S.build_a, "b": S.build_b, "c": S.build_c}
    B, bones = builders[opt]()
    m, pruned = S.to_model(f"ash_smith_{opt}", B, bones)
    geo_raw, tex, gl = m.finish()
    over = vx.check_overlaps(geo_raw)
    assert not over, ("ash_smith", over[:5])
    assert (gl[..., 3] > 0).any(), "光る画素がない: ash_smith"
    buried = tm.buried_count(m.boxes) if len(m.boxes) <= 1200 else None
    hm, hb = S.hammer(opt)
    tg, tb = S.tongs(opt)
    hgeo, htex, hgl = hm.finish()
    tgeo, ttex, tgl = tg.finish()
    for nm, (g, gl2) in (("hammer", (hgeo, hgl)), ("tongs", (tgeo, tgl))):
        o = vx.check_overlaps(g)
        assert not o, (nm, o[:5])
        assert (gl2[..., 3] > 0).any(), f"光る画素がない: {nm}"
    game, view = M.to_files(geo_raw, REST)
    info = dict(boxes=len(m.boxes), overlaps=len(over), buried=buried, pruned_hidden=pruned,
                glow_px=int((gl[..., 3] > 0).sum()), hammer_boxes=len(hm.boxes), tongs_boxes=len(tg.boxes),
                hammer_glow=int((hgl[..., 3] > 0).sum()), tongs_glow=int((tgl[..., 3] > 0).sum()),
                mean_before=mean_rgb(tex, gl))
    return dict(game=game, view=view, tex=tex, gl=gl, info=info, hammer=(hgeo, htex, hgl), tongs=(tgeo, ttex, tgl))


def write_gear(name, parts):
    geo, tex, gl = parts
    M.write_geo("gear", name, geo)
    M.write_tex("gear", name, tex, gl)
    anim = {"format_version": "1.8.0", "animations": {f"animation.{name}.idle": {"loop": True, "animation_length": 1.0, "bones": {}}}}
    (ASSETS / "animations/gear").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/gear" / f"{name}.animation.json").write_text(json.dumps(anim, indent=1) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- ボスバー (10 段、帯は橙。寸法は RaijuClient・PhasedBossBar と同じ)

BW, BH = 182, 16
TRACK = (3, 3, 178, 9)
DARK = (20, 12, 10)
GROOVE = (34, 20, 16)
IRON = (74, 64, 62)
IRON_L = (132, 116, 108)
BRONZE = (190, 128, 52)
BRONZE_D = (130, 80, 34)
EMBER = (255, 170, 60)
FILL = (235, 128, 40)
FILL_TOP = (255, 196, 110)


def _px(a, x, y, c, alpha=255):
    if 0 <= x < a.shape[1] and 0 <= y < a.shape[0]:
        a[y, x] = (*c, alpha)


def bar_frame():
    a = np.zeros((BH, BW, 4), np.uint8)
    for y in range(12):
        for x in range(BW):
            edge = min(y, 11 - y, x, BW - 1 - x)
            c = IRON if edge == 0 else IRON_L if edge == 1 else DARK if edge == 2 else GROOVE
            a[y, x] = (*c, 255)
    # 上下の縁に鋲の列 (8 画素ごと)、両端に青銅の当て金
    for x in range(10, BW - 10, 8):
        _px(a, x, 0, BRONZE)
        _px(a, x, 11, BRONZE)
        _px(a, x + 1, 0, BRONZE_D)
        _px(a, x + 1, 11, BRONZE_D)
    for x0 in (0, BW - 5):
        for y in range(12):
            for x in range(x0, x0 + 5):
                edge = min(y, 11 - y, x - x0, x0 + 4 - x)
                _px(a, x, y, BRONZE_D if edge == 0 else BRONZE)
        _px(a, x0 + 2, 5, EMBER)
        _px(a, x0 + 2, 6, EMBER)
    return a


def bar_notches():
    a = np.zeros((BH, BW, 4), np.uint8)
    x0, y0, x1, y1 = TRACK
    for k in range(1, 10):
        x = x0 + round((x1 - x0) * k / 10)
        for y in range(y0, y1):
            _px(a, x, y, (24, 12, 8), 210)
            _px(a, x + 1, y, (255, 200, 130), 60)
    return a


ICON = [
    "................",
    "....########....",
    "...##########...",
    "..############..",
    "..#BBBBBBBBBB#..",
    "..#BBBBBBBBBB#..",
    "..#.EEEEEEEE.#..",
    "..#BBBBBBBBBB#..",
    "..##BBBBBBBB##..",
    "..##.BBBBBB.##..",
    "...#.b.bb.b.#...",
    "...#..bbbb..#...",
    "...##.b..b.##...",
    "....##....##....",
    "................",
    "................",
]


def bar_icon():
    a = np.zeros((16, 16, 4), np.uint8)
    pal = {"#": IRON_L, "B": (96, 82, 76), "b": BRONZE, "E": EMBER, ".": DARK}
    for y, row in enumerate(ICON):
        for x, ch in enumerate(row):
            if ch == ".":
                if any(0 <= y + dy < 16 and 0 <= x + dx < 16 and ICON[y + dy][x + dx] != "." for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    a[y, x] = (*DARK, 220)
                continue
            a[y, x] = (*pal[ch], 255)
    return a


def bar_sample(fr, nt, ic, frac):
    img = Image.new("RGBA", (BW + 4, 36), (40, 36, 40, 255))
    img.alpha_composite(Image.fromarray(ic, "RGBA"), (2, 0))
    d = ImageDraw.Draw(img)
    d.text((22, 3), "Ash Smith", fill=(255, 255, 255))
    img.alpha_composite(Image.fromarray(fr, "RGBA"), (2, 18))
    x0, y0, x1, y1 = TRACK
    n = round((x1 - x0) * frac)
    if n > 0:
        d.rectangle([2 + x0, 18 + y0, 2 + x0 + n - 1, 18 + y1 - 1], fill=(*FILL, 255))
        d.line([2 + x0, 18 + y0, 2 + x0 + n - 1, 18 + y0], fill=(*FILL_TOP, 255))
    img.alpha_composite(Image.fromarray(nt, "RGBA"), (2, 18))
    return img.resize((img.width * 3, img.height * 3), Image.NEAREST)


def write_bar():
    BOSS.mkdir(parents=True, exist_ok=True)
    fr, nt, ic = bar_frame(), bar_notches(), bar_icon()
    Image.fromarray(fr, "RGBA").save(BOSS / "ash_smith_frame.png")
    Image.fromarray(nt, "RGBA").save(BOSS / "ash_smith_notches.png")
    Image.fromarray(ic, "RGBA").save(BOSS / "ash_smith_icon.png")
    return bar_sample(fr, nt, ic, 0.62)


# ---------------------------------------------------------------- 姿勢の重なりと確認の絵

def posed_hits(boss, name, times, hide=()):
    worst = []
    spec = ANIM
    for t in times:
        rot, pos = M.pose_at(spec, name, t)
        g = E.posed_geo(boss["view"], pos, hide)
        # 武器は大鎚だけ付けて調べる (握りは拳の穴を通る作りなので、右の前腕との重なりは数えない)
        wg = E.attach(g, boss["hammer"][0], "socket_main")
        gb = g["minecraft:geometry"][0]["bones"]
        gb += [b for b in wg["minecraft:geometry"][0]["bones"] if b["name"].startswith("wpn_")]
        hits = [h for h in E.posed_overlaps(g, rot, tol=M.POSED_TOL) if not ("wpn_" in h and "forearm_r" in h)]
        if len(hits) > len(worst):
            worst = [f"t={t:.2f}: {h}" for h in hits]
    return worst


def faces(boss, rot=None, pos=None, hide=()):
    g = E.posed_geo(boss["view"], pos, hide)
    out = vx.load_geo(g, boss["tex"], boss["gl"], pose=rot)
    for (wg, wt, wgl), sock in ((boss["hammer"], "socket_main"), (boss["tongs"], "socket_off")):
        ag = E.attach(g, wg, sock)
        out += vx.load_geo(ag, wt, wgl, pose=rot)
    return out


def shot(boss, yaw, pitch, name=None, t=0.0, size=(360, 400), hide=()):
    if name:
        rot, pos = M.pose_at(ANIM, name, t)
    else:
        rot, pos = None, None
    return E.render(faces(boss, rot, pos, hide), yaw, pitch, size=size)


def previews(boss, bar):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    tiles = []

    def add(img, text):
        tiles.append(E.label(img, text))

    add(shot(boss, 205, 14), "待機 正面 3/4")
    add(shot(boss, 270, 6), "待機 横")
    add(shot(boss, 28, 20), "待機 背 (鞴)")
    add(shot(boss, 235, 10, "slam", 0.9), "鎚打ち 振り上げ")
    add(shot(boss, 250, 8, "slam", 1.05), "鎚打ち 打つ")
    add(shot(boss, 28, 18, "breath", 1.45), "鞴の息 吸いきり (背)")
    add(shot(boss, 205, 14, "quench", 1.0), "焼き入れ")
    add(shot(boss, 215, 10, "sparks", 0.7), "火花散らし")
    add(shot(boss, 205, 12, "rage", 0.8), "怒り")
    add(shot(boss, 235, 12, "death", 2.4), "倒れる")
    cols = 4
    w, h = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), (12, 12, 18))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    sheet.save(PREVIEW / "ash_smith.png")
    bar.save(PREVIEW / "ash_smith_bar.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", default="a", choices=("a", "b", "c"))
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()

    boss = build_boss(args.option)
    tex, gl = boss["tex"], boss["gl"]
    lifted = lift(tex, gl)
    boss["info"]["mean_after"] = mean_rgb(lifted, gl)
    M.write_geo("entity", "ash_smith", boss["game"])
    M.write_tex("entity", "ash_smith", lifted, gl)
    Image.fromarray(whiten(gl), "RGBA").save(ASSETS / "textures/entity/ash_smith_glowmask_white.png")
    M.write_anim("entity", "ash_smith", ANIM)
    write_gear("smith_hammer", boss["hammer"])
    write_gear("smith_tongs", boss["tongs"])
    bar = write_bar()

    checks = {}
    for name, hide, step in (("idle", (), 0.4), ("walk", (), 0.2), ("slam", (), 0.17), ("slam_cold", (), 0.17), ("breath", (), 0.23),
                             ("quench", (), 0.26), ("sparks", (), 0.18), ("rage", (), 0.16), ("hurt", (), 0.07)):
        ln = ANIM[name]["len"]
        n = int(ln / step) + 1
        checks[name] = posed_hits(boss, name, [min(ln, i * step) for i in range(n)], hide)
    print("ash_smith", json.dumps(boss["info"], ensure_ascii=False))
    bad = 0
    for k, v in checks.items():
        print(f"posed {k}: {len(v)}" + (f"  {v[:4]}" if v else ""))
        bad += len(v)
    if not args.no_preview:
        previews({**boss, "tex": lifted}, bar)
    print("posed overlaps total", bad)


if __name__ == "__main__":
    main()
