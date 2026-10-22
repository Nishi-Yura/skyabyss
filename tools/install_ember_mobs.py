"""余燼の背の敵 3 種 (灰の番人・火の粉蛾・燠虫) と灰の番人の火掻き棒を、ゲームの素材として書き出す (13 の 5.1節、14 章)。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/install_ember_mobs.py [--option thrall=a,moth=a,roller=a]
出力:
  src/main/resources/assets/skyabyss/geo/entity/{ash_thrall,ember_moth,cinder_roller}.geo.json
  src/main/resources/assets/skyabyss/textures/entity/<名前>{,_glowmask}.png  (灰の番人は燃え上がった姿の ash_thrall_flared{,_glowmask}.png も)
  src/main/resources/assets/skyabyss/animations/entity/<名前>.animation.json
  src/main/resources/assets/skyabyss/{geo,textures,animations}/gear/ash_poker.*  (火掻き棒。受け口 socket_main に付く別モデル)
  docs/review/previews/ember_mobs/*.png  (確認用の絵。git には入れない)

形の元は rough/ember_mobs_{thrall,moth,roller}.py の案 (14 章の推奨はすべて A。--option で B・C に差し替えられる。
灰の番人の武器は案ごとに違う: A 火掻き棒、B 鎚、C 火箸)。ここでは次を足す:
- 燠虫: 玉の中心 (0, PY, 0) に骨 roll を足し、body をその子にした (転がるときは roll を回す。歩く姿では動かない)。
- アニメーション (意図した角度で書き、GeckoLib の符号はこのスクリプトが直す。tools/README と install_tide_mobs.py と同じ)。
- 検査: 立方体の重なり 0 (静止)、光る画素が 0 でない (状態ごと・武器も)、埋まった箱、アニメの姿勢ごとの重なり (分離軸)。

座標: ラフと同じ (前が +z、y が上、1 単位 = 1/16 ブロック)。書き出す geo は root 骨を yaw 180 度回す。
角度: ANIM と骨の初めの角度は「意図した向き」(ラフの絵で見える向き。x が正 = +y が前へ倒れる) で書き、
ファイルには (-x, +y, -z) で書く。位置 (position) はそのまま書く。
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "rough"))
import ember_mobs_common as E  # noqa: E402
import ember_mobs_moth as MO  # noqa: E402
import ember_mobs_roller as RO  # noqa: E402
import ember_mobs_thrall as TH  # noqa: E402

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/ember_mobs"
SIGN = (-1, 1, -1)
vx = E.vx
tm = E.tm


# ---------------------------------------------------------------- 書き出しの共通部分

def to_files(geo_raw, rest=None):
    """意図した角度の geo (root なし) から、ファイルに書く geo (符号を直す) と確認用の geo (意図した角度) を作る。"""
    view = tm.to_game(copy.deepcopy(geo_raw))
    game = tm.to_game(copy.deepcopy(geo_raw))
    for g, signed in ((view, False), (game, True)):
        for b in g["minecraft:geometry"][0]["bones"]:
            if rest and b["name"] in rest:
                r = rest[b["name"]]
                b["rotation"] = [float(r[i] * (SIGN[i] if signed else 1)) for i in range(3)]
    return game, view


def write_geo(folder, name, geo):
    geo = copy.deepcopy(geo)
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + name
    (ASSETS / "geo" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo" / folder / f"{name}.geo.json").write_text(json.dumps(geo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def write_tex(folder, name, tex, gl):
    assert (gl[..., 3] > 0).any(), f"光る画素がない: {name}"
    (ASSETS / "textures" / folder).mkdir(parents=True, exist_ok=True)
    Image.fromarray(tex, "RGBA").save(ASSETS / "textures" / folder / f"{name}.png")
    Image.fromarray(gl, "RGBA").save(ASSETS / "textures" / folder / f"{name}_glowmask.png")


def glow_px(gl):
    return int((gl[..., 3] > 0).sum())


# ---------------------------------------------------------------- アニメーション
# 値は意図した向き (度)。{"loop": True|False|"hold", "len": 秒, "bones": {骨: {"rotation"|"position"|"scale": 値}}}
# 値は 3 つの molang の式 (文字列。query.anim_time と math.sin を使う。sin は度) か、キーフレーム {時刻: (x, y, z)} (直線で補間)。

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


def _num(v):
    return float(round(v, 4))


def _signed(expr, sgn):
    return expr if sgn == 1 else ("0" if expr.strip() == "0" else f"-({expr})")


def anim_json(prefix, spec):
    out = {}
    for name, a in spec.items():
        bones = {}
        for bone, chans in a["bones"].items():
            bj = {}
            for ch, val in chans.items():
                sg = SIGN if ch == "rotation" else (1, 1, 1)
                if isinstance(val, dict):
                    bj[ch] = {f"{t:.4g}": {"vector": [_num(v[i] * sg[i]) for i in range(3)]} for t, v in sorted(val.items())}
                else:
                    bj[ch] = [_signed(val[i], sg[i]) for i in range(3)]
            bones[bone] = bj
        entry = {"animation_length": a["len"], "bones": bones}
        entry["loop"] = "hold_on_last_frame" if a["loop"] == "hold" else bool(a["loop"])
        out[f"animation.{prefix}.{name}"] = entry
    return {"format_version": "1.8.0", "animations": out}


def write_anim(folder, prefix, spec):
    (ASSETS / "animations" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations" / folder / f"{prefix}.animation.json").write_text(
        json.dumps(anim_json(prefix, spec), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def eval_channel(val, t):
    if isinstance(val, dict):
        ks = sorted(val)
        if t <= ks[0]:
            return np.array(val[ks[0]], float)
        for k0, k1 in zip(ks, ks[1:]):
            if k0 <= t <= k1:
                u = (t - k0) / (k1 - k0) if k1 > k0 else 0
                return np.array(val[k0], float) * (1 - u) + np.array(val[k1], float) * u
        return np.array(val[ks[-1]], float)
    env = {"msin": lambda d: math.sin(math.radians(d)), "mabs": abs, "t": t}
    res = []
    for e in val:
        e2 = e.replace("math.sin", "msin").replace("math.abs", "mabs").replace("query.anim_time", "t")
        res.append(float(eval(e2, {}, env)))
    return np.array(res)


def pose_at(spec, name, t):
    """意図した回転 {骨: (x, y, z)} と位置 {骨: (x, y, z)} (確認の絵と重なりの検査用。拡大は入れない)"""
    rot, pos = {}, {}
    for bone, chans in spec[name]["bones"].items():
        if "rotation" in chans:
            rot[bone] = tuple(eval_channel(chans["rotation"], t))
        if "position" in chans:
            pos[bone] = tuple(eval_channel(chans["position"], t))
    return rot, pos


# 姿勢の重なりの許し: 関節 (腰・肩) で触れ合う立方体の、1 単位 (1/16 ブロック) までの食い込みは数えない
POSED_TOL = 1.01


def posed_hits(view, spec, name, times, hide=(), weapon=None, socket="socket_main"):
    """アニメの各時刻で、別の骨の立方体どうしが 1 単位を超えて食い込む組を数える (武器があれば受け口に付けて一緒に)。"""
    worst = []
    for t in times:
        rot, pos = pose_at(spec, name, t)
        g = E.posed_geo(view, pos, hide)
        if weapon is not None:
            # 武器の骨を受け口の子として足した geo (体の立方体も残す)
            wg = E.attach(g, weapon, socket)
            gb = g["minecraft:geometry"][0]["bones"]
            gb += [b for b in wg["minecraft:geometry"][0]["bones"] if b["name"].startswith("wpn_")]
        # posed_overlaps は geo の rotation に姿勢を足す。view の rotation は意図した初めの角度
        # 武器と、それを握る前腕 (拳) の重なりは数えない: 握りが拳の穴を通る作りで、手首の返しは受け口の回転で表すため
        hits = [h for h in E.posed_overlaps(g, rot, tol=POSED_TOL)
                if not ("wpn_" in h and ("forearm_r" in h or "crust_fore_r" in h))]
        if len(hits) > len(worst):
            worst = [f"t={t:.2f}: {h}" for h in hits]
    return worst


# ---------------------------------------------------------------- 灰の番人

THRALL_REST = {"socket_main": (0, 0, 8)}     # 火掻き棒を少し外へ傾けて立てて持つ (ラフの絵と同じ)

ANIM_THRALL = {
    "idle": {"loop": True, "len": 3.2, "bones": {
        # 灰を吸って吐くような、ゆっくりした胸の上下と、首の揺れ
        "body": {"rotation": [sn(1.5, 112.5), "0", sn(0.8, 112.5, 90)]},
        "head": {"rotation": [sn(2.5, 112.5, 45), sn(5, 56.25), sn(2, 112.5, 180)]},
        "arm_l": {"rotation": [sn(3, 112.5, 30), "0", f"3 + {sn(2, 112.5)}"]},
    }},
    "walk": {"loop": True, "len": 1.2, "bones": {
        # 前かがみに足を引きずる歩き
        "leg_r": {"rotation": [sn(-14, 300), "0", "0"]},
        "leg_l": {"rotation": [sn(14, 300), "0", "0"]},
        "arm_l": {"rotation": [sn(14, 300), "0", "4"]},
        "arm_r": {"rotation": [sn(-4, 300), "0", "0"]},
        "body": {"rotation": ["3", sn(2, 300), "0"],
                 "position": ["0", "-0.3 * math.abs(math.sin(300 * query.anim_time))", "0"]},
        "head": {"rotation": ["-2", sn(-3, 300), "0"]},
    }},
    # 燃え上がった姿の歩き (1.3 倍の速さ。腕を振って前のめりに走る)
    "walk_flared": {"loop": True, "len": 0.7, "bones": {
        "leg_r": {"rotation": [sn(-15, 514.3), "0", "0"]},
        "leg_l": {"rotation": [sn(15, 514.3), "0", "0"]},
        "arm_l": {"rotation": [sn(26, 514.3), "0", "6"]},
        "arm_r": {"rotation": [f"-10 + {sn(-8, 514.3)}", "0", "0"]},
        "body": {"rotation": ["6", sn(3, 514.3), "0"],
                 "position": ["0", "-0.6 * math.abs(math.sin(514.3 * query.anim_time))", "0.5"]},
        "head": {"rotation": ["-8", sn(-4, 514.3), "0"]},
    }},
    "attack": {"loop": False, "len": 1.0, "bones": {
        # 0.45 秒で腕を外から頭の上へ上げ、火掻き棒を頭の上で後ろへ寝かせる (振りかぶり)。0.55 秒で前の下へ振り下ろす
        # (サーバーは 10 tick で当てる)。手首の返しは受け口の回転 (握りは拳の穴を通るので、拳と武器の重なりは検査で数えない)
        "arm_r": {"rotation": {0: (0, 0, 0), 0.25: (-80, -40, -25), 0.45: (-140, -45, -30), 0.55: (-38, 0, -14), 0.72: (-34, 0, -10), 1.0: (0, 0, 0)}},
        "forearm_r": {"rotation": {0: (0, 0, 0), 0.45: (-15, 0, 0), 0.55: (-25, 0, 0), 0.72: (-25, 0, 0), 1.0: (0, 0, 0)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 0.25: (60, 0, -4), 0.45: (125, 0, -8), 0.55: (160, 0, -8), 0.72: (160, 0, -8), 1.0: (0, 0, 0)}},
        "body": {"rotation": {0: (0, 0, 0), 0.45: (-5, -6, 0), 0.55: (8, 5, 0), 0.72: (7, 4, 0), 1.0: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.45: (4, 6, 0), 0.55: (-5, -4, 0), 1.0: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.45: (-10, 0, 6), 0.55: (12, 0, 4), 1.0: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.35, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.08: (-4, 0, 3), 0.35: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.08: (-6, 0, -4), 0.35: (0, 0, 0)}},
    }},
    # 殻が割れる: のけぞって両腕を広げ、胸を開く (このあと燃え上がる)
    "shell_break": {"loop": False, "len": 0.8, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.15: (-7, 0, 0), 0.5: (-6, 0, 0), 0.8: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.15: (-8, 0, 0), 0.5: (-7, 0, 0), 0.8: (0, 0, 0)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 0.15: (-20, 0, -40), 0.5: (-20, 0, -36), 0.8: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.15: (-20, 0, 40), 0.5: (-20, 0, 36), 0.8: (0, 0, 0)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 0.15: (0, 0, 40), 0.5: (0, 0, 36), 0.8: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.4, "bones": {
        # 膝をつき、前へ崩れる (燃え尽きた灰の山のように低くなる)
        "body": {"rotation": {0: (0, 0, 0), 0.4: (14, 0, 4), 1.0: (70, 0, 8), 1.4: (78, 0, 8)},
                 "position": {0: (0, 0, 0), 0.4: (0, -5, 0), 1.0: (0, -12, 2), 1.4: (0, -13, 2)}},
        "leg_r": {"rotation": {0: (0, 0, 0), 0.4: (-60, 0, 0), 1.0: (-84, 0, 0), 1.4: (-88, 0, 0)},
                  "position": {0: (0, 0, 0), 0.4: (0, -5, 0), 1.0: (0, -12, 0), 1.4: (0, -13, 0)}},
        "leg_l": {"rotation": {0: (0, 0, 0), 0.4: (-54, 0, 0), 1.0: (-82, 0, 0), 1.4: (-86, 0, 0)},
                  "position": {0: (0, 0, 0), 0.4: (0, -5, 0), 1.0: (0, -12, 0), 1.4: (0, -13, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.4: (20, 0, 10), 1.4: (-20, 20, 14)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 1.0: (-60, 0, 20), 1.4: (-70, 0, 24)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 1.0: (-50, 0, -16), 1.4: (-60, 0, -20)}},
    }},
}


# 外套の肩の前の垂れ (体の骨のセル) を、腕の骨へ移す (肩当てとして腕と一緒に動く)。
# 体に残すと、腕を前へ振るたびに上腕が垂れを 1〜1.5 単位くぐる (姿勢の検査で見つけた)
FLAP = ((3.5, 5.5), (23.5, 26.5), (1.5, 3.5))


def shoulder_flaps(sc):
    n = 0
    for q, (bone, mat) in list(sc.cells.items()):
        if bone != "body":
            continue
        x, y, z = ((v + 0.5) / 2 for v in q)
        if FLAP[0][0] <= abs(x) <= FLAP[0][1] and FLAP[1][0] <= y <= FLAP[1][1] and FLAP[2][0] <= z <= FLAP[2][1]:
            sc.cells[q] = ("arm_r" if x < 0 else "arm_l", mat)
            n += 1
    return n


def build_thrall(opt):
    TH.set_ramps()
    sc = TH.build(opt)
    moved = shoulder_flaps(sc)
    paint, lit = TH.make_paint(opt)
    m = E.build_model(sc, paint, lit)
    states = {}
    geo = None
    for st in ("crusted", "flared"):
        m.state = st
        g, tex, gl = m.finish()
        geo = geo or g
        states[st] = (tex, gl)
    over = vx.check_overlaps(geo)
    assert not over, ("ash_thrall", over[:5])
    bones = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
    crust = [b for b in bones if b.startswith("crust_")]
    flare = [b for b in bones if b.startswith("flare_")]
    allb = set(bones)
    info = dict(boxes=len(m.boxes), overlaps=len(over),
                buried_crusted=E.buried_in_state(m._boxes, allb - set(flare)),
                buried_flared=E.buried_in_state(m._boxes, allb - set(crust)),
                glow_crusted=glow_px(states["crusted"][1]), glow_flared=glow_px(states["flared"][1]), flap_cells_moved=moved,
                crust_bones=crust, flare_bones=flare)
    # 武器 (別のモデル。root を付けない)
    wname, wfn, _ = TH.WEAPONS[opt]
    for nm, rp in TH.WEAPON_MATS.items():
        tm.RAMP[nm] = E.EMBER_RAMP[rp]
    wm = E.build_model(wfn(), TH.weapon_paint, TH.weapon_lit)
    wgeo, wtex, wgl = wm.finish()
    TH.set_ramps()
    wover = vx.check_overlaps(wgeo)
    assert not wover, ("weapon", wover[:5])
    info["weapon"] = dict(name=wname, boxes=len(wm.boxes), overlaps=len(wover), glow=glow_px(wgl),
                          buried=E.buried_in_state(wm._boxes, {"weapon"}))
    game, view = to_files(geo, THRALL_REST)
    return dict(game=game, view=view, states=states, info=info, weapon=(wgeo, wtex, wgl), wname=wname, crust=crust, flare=flare)


# ---------------------------------------------------------------- 火の粉蛾

def flap(phase=0):
    # 右の翅 (x が負) は z が負で上がる。上げ 70 度から、水平 (0 度) まで
    return f"-(35 + {sn(35, 1500, phase)})"


ANIM_MOTH = {
    "idle": {"loop": True, "len": 0.24, "bones": {
        "wing_r": {"rotation": ["0", "0", flap()]},
        "wing_l": {"rotation": ["0", "0", f"-({flap()})"]},
        "body": {"position": ["0", sn(0.6, 1500, 90), "0"], "rotation": [sn(3, 1500, 120), "0", "0"]},
    }},
    "walk": {"loop": True, "len": 0.24, "bones": {
        "wing_r": {"rotation": ["0", "0", flap()]},
        "wing_l": {"rotation": ["0", "0", f"-({flap()})"]},
        "body": {"position": ["0", sn(0.5, 1500, 90), "0"], "rotation": [f"8 + {sn(3, 1500, 120)}", "0", "0"]},
    }},
    # 触れに来る: 翅を上げて溜め、頭から突っ込む
    "attack": {"loop": False, "len": 0.4, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.12: (-15, 0, 0), 0.22: (30, 0, 0), 0.4: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.12: (0, 0.5, -1), 0.22: (0, -0.5, 2.5), 0.4: (0, 0, 0)}},
        "wing_r": {"rotation": {0: (0, 0, -30), 0.12: (0, 0, -68), 0.22: (0, 0, 0), 0.4: (0, 0, -30)}},
        "wing_l": {"rotation": {0: (0, 0, 30), 0.12: (0, 0, 68), 0.22: (0, 0, 0), 0.4: (0, 0, 30)}},
    }},
    "hurt": {"loop": False, "len": 0.25, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.06: (-10, 0, 18), 0.25: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 0.8, "bones": {
        # 翅をたたんで、腹を上にして落ちる
        "body": {"rotation": {0: (0, 0, 0), 0.4: (0, 0, 110), 0.8: (0, 0, 170)},
                 "position": {0: (0, 0, 0), 0.4: (0, -3, 0), 0.8: (0, -6, 0)}},
        "wing_r": {"rotation": {0: (0, 0, -20), 0.3: (0, 0, -70), 0.8: (0, 0, -68)}},
        "wing_l": {"rotation": {0: (0, 0, 20), 0.3: (0, 0, 70), 0.8: (0, 0, 68)}},
    }},
}


def build_moth(opt):
    MO.set_ramps(opt)
    sc = MO.build(opt)
    paint, lit = MO.make_paint(opt)
    m = E.build_model(sc, paint, lit)
    geo, tex, gl = m.finish()
    over = vx.check_overlaps(geo)
    assert not over, ("ember_moth", over[:5])
    allb = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    info = dict(boxes=len(m.boxes), overlaps=len(over), buried=E.buried_in_state(m._boxes, allb), glow=glow_px(gl))
    game, view = to_files(geo)
    return dict(game=game, view=view, tex=tex, gl=gl, info=info)


# ---------------------------------------------------------------- 燠虫

N = RO.N
PY = RO.PY
CY = RO.CY
LIMBS = [f"leg{k}_{s}" for k in range(1, N - 1) for s in "rl"] + ["antenna_r", "antenna_l", "uropods"] + [f"mem{k}" for k in range(N - 1)]
DIZZY_DROP = -5.0      # 背を下にしたとき、玉の中心まわりに 180 度回して下ろす高さ


def curl_rot(extra=None):
    d = {f"seg{k}": ((k - 4) * 40.0, 0.0, 0.0) for k in range(N)}
    d.update(extra or {})
    return d


def curl_pos():
    return {f"seg{k}": (0.0, PY - CY, -RO.zk(k)) for k in range(N)}


def curl_keys(t0, t1, rev=False):
    """seg ごとの回転と位置のキー (t0 で伸びた姿、t1 で丸まった姿。rev なら逆)"""
    out = {}
    for k in range(N):
        r = ((k - 4) * 40.0, 0.0, 0.0)
        p = (0.0, PY - CY, -RO.zk(k))
        a, b = ((0, 0, 0), r) if not rev else (r, (0, 0, 0))
        pa, pb = ((0, 0, 0), p) if not rev else (p, (0, 0, 0))
        out[f"seg{k}"] = {"rotation": {t0: a, t1: b}, "position": {t0: pa, t1: pb}}
    return out


def curl_hold():
    return {f"seg{k}": {"rotation": [str((k - 4) * 40.0), "0", "0"], "position": ["0", str(PY - CY), str(-RO.zk(k))]} for k in range(N)}


def legs_wave(amp_y, amp_z, f):
    """脚を前後に振り (y)、外の下へだけ開く (z。体の側へは上げない: 節や膜に食い込まないように)。"""
    out = {}
    for k in range(1, N - 1):
        for s, sgn, ph in (("r", 1, 0), ("l", -1, 180)):
            phase = k * 45 + ph
            out[f"leg{k}_{s}"] = {"rotation": ["0", sn(amp_y, f, phase),
                                               f"{sgn * amp_z / 2} + {sn(sgn * amp_z / 2, f, phase + 90)}"]}
    return out


def anim_roller():
    a = {}
    idle = {"body": {"position": ["0", sn(0.15, 150), "0"]},
            "antenna_r": {"rotation": [sn(6, 150), sn(10, 220), "0"]},
            "antenna_l": {"rotation": [sn(6, 150, 90), sn(-10, 220, 60), "0"]}}
    idle.update(legs_wave(4, 3, 150))
    a["idle"] = {"loop": True, "len": 2.4, "bones": idle}
    walk = {"body": {"position": ["0", f"0.2 * math.abs(math.sin(720 * query.anim_time))", "0"], "rotation": ["0", sn(2, 360), "0"]},
            "antenna_r": {"rotation": [sn(8, 720), sn(12, 360), "0"]},
            "antenna_l": {"rotation": [sn(8, 720, 90), sn(-12, 360), "0"]}}
    walk.update(legs_wave(16, 4, 720))
    a["walk"] = {"loop": True, "len": 0.5, "bones": walk}
    # 丸まる (0.3 秒、そのまま止める)
    a["curl"] = {"loop": "hold", "len": 0.3, "bones": curl_keys(0.0, 0.3)}
    # 丸まって震える (転がる前の 0.8 秒の予告)
    curled = curl_hold()
    curled["roll"] = {"rotation": [sn(2, 1800), "0", sn(5, 2400)], "position": [sn(0.3, 2400, 90), "0", "0"]}
    a["curled"] = {"loop": True, "len": 0.3, "bones": curled}
    # 転がる (玉の中心のまわりに前へ。0.25 秒で一回り)
    rolling = curl_hold()
    rolling["roll"] = {"rotation": ["1440 * query.anim_time", "0", "0"]}
    a["rolling"] = {"loop": True, "len": 0.25, "bones": rolling}
    # 開く (丸まった姿から伸びた姿へ。0.35 秒)
    a["uncurl"] = {"loop": False, "len": 0.35, "bones": curl_keys(0.0, 0.35, rev=True)}
    # 壁にぶつかって目を回す: 玉が開いて背を下にし (腹が光る)、脚をばたつかせる
    pop = curl_keys(0.0, 0.25, rev=True)
    pop["roll"] = {"rotation": {0: (0, 0, 0), 0.25: (0, 0, 180)}, "position": {0: (0, 0, 0), 0.25: (0, DIZZY_DROP, 0)}}
    a["pop"] = {"loop": "hold", "len": 0.25, "bones": pop}
    dizzy = {"roll": {"rotation": [sn(4, 720), sn(25, 360), f"180 + {sn(8, 720)}"], "position": ["0", str(DIZZY_DROP), "0"]}}
    dizzy.update(legs_wave(16, 24, 1100))
    dizzy["antenna_r"] = {"rotation": [sn(20, 900), sn(20, 700), "0"]}
    dizzy["antenna_l"] = {"rotation": [sn(20, 900, 90), sn(-20, 700), "0"]}
    a["dizzy"] = {"loop": True, "len": 1.0, "bones": dizzy}
    # 起き上がる (背を下にした姿から)
    a["rightself"] = {"loop": False, "len": 0.35, "bones": {
        "roll": {"rotation": {0: (0, 0, 180), 0.35: (0, 0, 360)}, "position": {0: (0, DIZZY_DROP, 0), 0.2: (0, DIZZY_DROP + 2, 0), 0.35: (0, 0, 0)}}}}
    a["hurt"] = {"loop": False, "len": 0.3, "bones": {"body": {"rotation": {0: (0, 0, 0), 0.06: (6, 0, 10), 0.3: (0, 0, 0)}}}}
    death = {"roll": {"rotation": {0: (0, 0, 0), 0.3: (0, 0, 120), 0.5: (0, 0, 180), 1.0: (0, 0, 180)},
                      "position": {0: (0, 0, 0), 0.3: (0, DIZZY_DROP * 0.6, 0), 0.5: (0, DIZZY_DROP, 0), 1.0: (0, DIZZY_DROP, 0)}}}
    for k in range(1, N - 1):
        for s, sgn in (("r", -1), ("l", 1)):
            death[f"leg{k}_{s}"] = {"rotation": {0: (0, 0, 0), 0.5: (0, 0, sgn * 20), 1.0: (0, 0, sgn * 40)}}
    a["death"] = {"loop": "hold", "len": 1.0, "bones": death}
    return a


ANIM_ROLLER = anim_roller()


def build_roller(opt):
    RO.set_ramps(opt)
    sc = RO.build(opt)
    # 玉の中心に roll の骨を足し、body をその子にする (転がるときに玉の中心で回す)
    body = next(b for b in sc.bones if b["name"] == "body")
    body["parent"] = "roll"
    sc.bones.insert(0, {"name": "roll", "pivot": [0.0, float(PY), 0.0]})
    paint, lit = RO.make_paint(opt)
    m = E.build_model(sc, paint, lit)
    geo, tex, gl = m.finish()
    over = vx.check_overlaps(geo)
    assert not over, ("cinder_roller", over[:5])
    allb = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    info = dict(boxes=len(m.boxes), overlaps=len(over), buried=E.buried_in_state(m._boxes, allb), glow=glow_px(gl))
    game, view = to_files(geo)
    return dict(game=game, view=view, tex=tex, gl=gl, info=info)


# ---------------------------------------------------------------- 火掻き棒の素材

def write_weapon(th):
    wgeo, wtex, wgl = th["weapon"]
    name = "ash_" + th["wname"]
    write_geo("gear", name, wgeo)
    write_tex("gear", name, wtex, wgl)
    anim = {"format_version": "1.8.0", "animations": {f"animation.{name}.idle": {"loop": True, "animation_length": 1.0, "bones": {}}}}
    (ASSETS / "animations/gear").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/gear" / f"{name}.animation.json").write_text(json.dumps(anim, indent=1) + "\n", encoding="utf-8")
    return name


# ---------------------------------------------------------------- 確認の絵

def shot(view, tex, gl, yaw, pitch, rot=None, pos=None, hide=(), weapon=None, size=(360, 360)):
    fc = E.faces_of(view, tex, gl, pose=rot, pos=pos, hide=hide, weapon=weapon)
    return E.render(fc, yaw, pitch, size=size)


def previews(th, mo, ro):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    ct, cg = th["states"]["crusted"]
    ft, fg = th["states"]["flared"]
    wpn = th["weapon"]
    tiles = []

    def add(img, text):
        tiles.append(E.label(img, text))

    add(shot(th["view"], ct, cg, 205, 15, hide=th["flare"], weapon=wpn), "灰の番人 殻")
    add(shot(th["view"], ft, fg, 205, 15, hide=th["crust"], weapon=wpn), "灰の番人 燃え上がり")
    for t, nm in ((0.45, "振りかぶり"), (0.55, "振り下ろし")):
        rot, pos = pose_at(ANIM_THRALL, "attack", t)
        add(shot(th["view"], ct, cg, 250, 10, rot=rot, pos=pos, hide=th["flare"], weapon=wpn), f"灰の番人 {nm}")
    rot, pos = pose_at(ANIM_THRALL, "death", 1.4)
    add(shot(th["view"], ft, fg, 250, 15, rot=rot, pos=pos, hide=th["crust"], weapon=wpn), "灰の番人 倒れる")
    for t, nm in ((0.06, "翅上げ"), (0.18, "翅下げ")):
        rot, pos = pose_at(ANIM_MOTH, "idle", t)
        add(shot(mo["view"], mo["tex"], mo["gl"], 205, 22, rot=rot, pos=pos), f"火の粉蛾 {nm}")
    add(shot(ro["view"], ro["tex"], ro["gl"], 205, 18), "燠虫 歩く")
    rot, pos = pose_at(ANIM_ROLLER, "curled", 0)
    add(shot(ro["view"], ro["tex"], ro["gl"], 205, 18, rot=rot, pos=pos, hide=LIMBS), "燠虫 丸まり")
    rot, pos = pose_at(ANIM_ROLLER, "rolling", 0.05)
    add(shot(ro["view"], ro["tex"], ro["gl"], 270, 8, rot=rot, pos=pos, hide=LIMBS), "燠虫 転がる")
    rot, pos = pose_at(ANIM_ROLLER, "dizzy", 0.1)
    add(shot(ro["view"], ro["tex"], ro["gl"], 205, 25, rot=rot, pos=pos), "燠虫 目を回す")
    cols = 4
    w, h = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, h * rows), (12, 12, 18))
    for i, im in enumerate(tiles):
        sheet.paste(im, ((i % cols) * w, (i // cols) * h))
    sheet.save(PREVIEW / "ember_mobs.png")
    for i, im in enumerate(tiles):
        im.save(PREVIEW / f"{i:02d}.png")


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", default="thrall=a,moth=a,roller=a", help="案の差し替え (例 thrall=c)")
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()
    opt = dict(kv.split("=") for kv in args.option.split(","))

    th = build_thrall(opt.get("thrall", "a"))
    write_geo("entity", "ash_thrall", th["game"])
    write_tex("entity", "ash_thrall", *th["states"]["crusted"])
    write_tex("entity", "ash_thrall_flared", *th["states"]["flared"])
    write_anim("entity", "ash_thrall", ANIM_THRALL)
    wname = write_weapon(th)

    mo = build_moth(opt.get("moth", "a"))
    write_geo("entity", "ember_moth", mo["game"])
    write_tex("entity", "ember_moth", mo["tex"], mo["gl"])
    write_anim("entity", "ember_moth", ANIM_MOTH)

    ro = build_roller(opt.get("roller", "a"))
    write_geo("entity", "cinder_roller", ro["game"])
    write_tex("entity", "cinder_roller", ro["tex"], ro["gl"])
    write_anim("entity", "cinder_roller", ANIM_ROLLER)

    # 15 章 (ハイポリゴン化): 書き出した素の形を、面の整理・面取り・飾りで彫り直す (rough/hp_mobs_storm_ember.py)
    sys.path.insert(0, str(HERE / "rough"))
    import hp_mobs_storm_ember as hp
    for name in ("ash_thrall", "ember_moth", "cinder_roller"):
        hp.install_mob(name)

    # アニメの姿勢ごとの重なり (別の骨の立方体どうし)。灰の番人は武器を付け、殻のある姿で調べる (素の形での検査)
    checks = {}
    th_times = [i * 0.1 for i in range(15)]
    for name in ("idle", "walk", "walk_flared", "attack", "hurt", "shell_break"):
        hide = th["crust"] if name == "walk_flared" else th["flare"]
        checks[f"ash_thrall.{name}"] = posed_hits(th["view"], ANIM_THRALL, name, [t for t in th_times if t <= ANIM_THRALL[name]["len"]],
                                                  hide=hide, weapon=th["weapon"][0])
    for name in ("idle", "attack", "hurt"):
        checks[f"ember_moth.{name}"] = posed_hits(mo["view"], ANIM_MOTH, name, [i * 0.03 for i in range(14) if i * 0.03 <= ANIM_MOTH[name]["len"]])
    for name, hide in (("idle", ()), ("walk", ()), ("curled", LIMBS), ("rolling", LIMBS), ("dizzy", ()), ("hurt", ())):
        ln = ANIM_ROLLER[name]["len"]
        checks[f"cinder_roller.{name}"] = posed_hits(ro["view"], ANIM_ROLLER, name, [ln * i / 8 for i in range(9)], hide=hide)
    print("ash_thrall", json.dumps(th["info"], ensure_ascii=False))
    print("ember_moth", json.dumps(mo["info"], ensure_ascii=False))
    print("cinder_roller", json.dumps(ro["info"], ensure_ascii=False))
    print("weapon", wname)
    bad = 0
    for k, v in checks.items():
        print(f"posed {k}: {len(v)}" + (f"  {v[:4]}" if v else ""))
        bad += len(v)
    if not args.no_preview:
        previews(th, mo, ro)
    print("posed overlaps total", bad)


if __name__ == "__main__":
    main()
