"""鯨骨の番兵長 第4回: 鉾をモデルから外し、アイテム (skyabyss:captain_pike) として手の受け口に描く。

直したこと (docs/roughs/GEAR_BRIEF.md):
- 鉾の立方体と、手の「C」形の握り (柄を指で囲む細工) を外した。手はふつうの握りこぶし (手の箱 + 指の段)。
- 受け口の骨 socket_main (右手の子) と socket_off (左手の子) を足した。pivot は握りの中心。
- ゲームでは client/entity/GuardRenderers.java の captainPike が、アイテムの3Dモデルを socket_main に描く。
  その変換と同じもの (PIKE_*) で、この図ではアイテムの立方体を受け口に合成して描く。
- 受け口の向き: 穂先が受け口の -Z、刃が -Y (腕を前へ水平に出すと柄が立ち、刃が前を向く)。
- アニメーションは作り直した: idle / walk (鉾を体の横に立てて持つ)、sweep (両手の横薙ぎ。左手が柄に添い、胴をひねる)、
  leap (屈んで跳び、空中で両手で振りかぶる)、slam (両手で頭上に振りかぶり、前へ打ち下ろす。胴は前へ傾く)、roar。
  各キーは「右の握りの位置・柄の向き・刃の向き」と「左手が柄に乗ること」を目標に、腕の角度を解いて決めた。
  肘は前にしか曲がらず、手が胴にめり込まず、腕が胸の前で交差しないように罰則を入れている。

実行 (リポジトリ直下): python tools/rough/captain_v4.py [--install]
出力:
  docs/roughs/v4/whalebone_captain.png  (列: 2方向。行: 通常 / 薙ぎ払い / 叩きつけ)
  docs/roughs/v4/whalebone_captain/*_keys.png (各動作の全キー)
  --install を付けると、src/main/resources の geo / texture / glowmask / animation を書き換える。
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy.optimize import least_squares

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import captain_v2 as v2  # noqa: E402
from voxel import _rot, font, load_geo, render, sheet  # noqa: E402

ROOT = HERE.parents[1]
OUT = ROOT / "docs" / "roughs" / "v4"
GAME = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
SUBJECT = "whalebone_captain"

# ---------------------------------------------------------------- 骨

GRIP_R = (-14.5, 8.5, -0.25)   # 右手の握りの中心 (手の箱 y 7..10.5 の中)
GRIP_L = (14.5, 8.5, -0.25)
NEW_BONES = []
for _n, _p, _par in v2.BONES:
    if _n.startswith("fR") or _n == "pike":
        continue
    NEW_BONES.append((_n, _p, _par))
    if _n == "handR":
        NEW_BONES.append(("socket_main", GRIP_R, "handR"))
    if _n == "handL":
        NEW_BONES.append(("socket_off", GRIP_L, "handL"))
PIV = {n: np.array(p, float) for n, p, _ in NEW_BONES}
PAR = {n: par for n, _, par in NEW_BONES}

# 解く骨 (右腕 + 受け口、左腕)
RB = ["armR", "forearmR", "handR", "socket_main"]
LB = ["armL", "forearmL", "handL"]
VARS = RB + LB
GROUP = ["body", "head", "legR", "legL"] + VARS

# ---------------------------------------------------------------- 鉾 (アイテムのモデル)
# GuardRenderers.captainPike と同じ値: アイテムの画素座標で柄の軸 x=5.875 z=8、握り y=4、大きさ 1.8
PIKE_GRIP = np.array([5.875, 4.0, 8.0])
PIKE_SCALE = 1.8
# アイテムの軸 → 受け口の軸: +x (刃) → -Y、+y (穂先) → -Z、+z → +X
PIKE_R = np.array([[0, 0, 1], [-1, 0, 0], [0, -1, 0]], float)


def build_b():
    m = v2.build("b")
    for n in m.bnames:
        if n.startswith("fR") or n == "pike":
            m.owner[m.owner == m.bid(n)] = -1
    # 握りこぶし: 手の箱の下に、曲げた指の段 (4本の溝) を付ける
    for xc, bone in ((-14.5, "handR"), (14.5, "handL")):
        m.add(v2.rbox((xc - 1.8, 6.5, -2.2), (xc + 1.8, 7.0, 1.7)), bone, "iron_dk:gv")
    m.bnames += ["socket_main", "socket_off"]
    return m


def pike_geo():
    """アイテムの立方体を、受け口 socket_main の座標に置いた geo (図の合成用)。"""
    item = json.loads((GAME / "models/item/captain_pike.json").read_text(encoding="utf-8"))
    cubes = []
    for e in item["base"]["elements"]:
        f, t = np.array(e["from"], float), np.array(e["to"], float)
        # 要素は -45° 傾けて置いてある。ゲームの変換はその傾きを戻すので、ここでは傾ける前の形を使う
        a = PIKE_SCALE * PIKE_R @ (f - PIKE_GRIP) + PIV["socket_main"]
        b = PIKE_SCALE * PIKE_R @ (t - PIKE_GRIP) + PIV["socket_main"]
        lo, hi = np.minimum(a, b), np.maximum(a, b)
        u = int(next(iter(e["faces"].values()))["uv"][0])
        uv = {k: {"uv": [u, 0], "uv_size": [1, 1]} for k in ("north", "south", "east", "west", "up", "down")}
        cubes.append({"origin": lo.tolist(), "size": (hi - lo).tolist(), "uv": uv})
    bones = []
    for n, p, par in NEW_BONES:
        b = {"name": n, "pivot": list(p), "cubes": cubes if n == "socket_main" else []}
        if par:
            b["parent"] = par
        bones.append(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.pike_preview", "texture_width": 16, "texture_height": 1},
        "bones": bones}]}
    tex = np.array(Image.open(GAME / "textures/item/3d/captain_pike.png").convert("RGBA"))
    return geo, tex


# ---------------------------------------------------------------- 運動学 (voxel.load_geo と同じ変換)

def xforms(pose):
    cache = {}

    def xf(n):
        if n in cache:
            return cache[n]
        R = _rot(*pose.get(n, (0.0, 0.0, 0.0)))
        p = PIV[n]
        M = (R, p - R @ p)
        if PAR[n]:
            PR, Pt = xf(PAR[n])
            M = (PR @ M[0], PR @ M[1] + Pt)
        cache[n] = M
        return M
    return xf


def frame(xf, bone):
    """骨の pivot の位置と、骨の軸 (列: X, Y, Z)。"""
    R, t = xf(bone)
    return R @ PIV[bone] + t, R


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


TORSO = np.array([[17, 9.0, 6.0], [20, 8.5, 5.5], [24, 10.0, 6.5], [30, 10.5, 7.0], [33, 9.5, 6.5]])


def inside_torso(p_body, pad):
    """胴の中にどれだけ入っているか (0 なら外)。p_body は胴の座標。"""
    x, y, z = p_body
    if y < 12 or y > 35:
        return 0.0
    yy = np.clip(y, 17, 33)
    w = np.interp(yy, TORSO[:, 0], TORSO[:, 1]) + pad
    d = np.interp(yy, TORSO[:, 0], TORSO[:, 2]) + pad
    q = (abs(x) / w) ** 2.5 + (abs(z) / d) ** 2.5
    return max(0.0, 1.0 - q)


def pose_from(x, fixed):
    pose = dict(fixed)
    for i, n in enumerate(VARS):
        pose[n] = tuple(float(v) for v in x[3 * i:3 * i + 3])
    return pose


def penalties(pose, xf):
    """肘の向き・手首の曲げ・受け口の回転・胴へのめり込み・腕の交差。"""
    r = []
    Rb, tb = xf("body")
    inv = Rb.T
    for side, arm, fore, hand in (("R", "armR", "forearmR", "handR"), ("L", "armL", "forearmL", "handL")):
        fx, fy, fz = pose[fore]
        r += [0.08 * max(0.0, -fx), 0.08 * max(0.0, fx - 140), 0.04 * fy, 0.04 * fz]   # 肘は前にだけ曲がる
        hx, hy, hz = pose[hand]
        r += [0.03 * max(0.0, abs(a) - 40) for a in (hx, hy, hz)]                     # 手首は大きく曲げない
        for bone, pad in ((fore, 2.2), (hand, 2.0)):
            p, _ = frame(xf, bone)
            pb = inv @ (p - tb)
            r.append(30.0 * inside_torso(pb, pad))
        g, _ = frame(xf, "socket_main" if side == "R" else "socket_off")
        r.append(30.0 * inside_torso(inv @ (g - tb), 2.0))
    sx, sy, sz = pose["socket_main"]
    r += [0.03 * max(0.0, abs(a) - 25) for a in (sx, sy, sz)] + [0.01 * sx, 0.01 * sy, 0.01 * sz]
    # 腕の交差: 胴の座標で、右の握りが左の握りより左へ出ない。肘も同じ
    gR, _ = frame(xf, "socket_main")
    gL, _ = frame(xf, "socket_off")
    eR, _ = frame(xf, "forearmR")
    eL, _ = frame(xf, "forearmL")
    r.append(1.0 * max(0.0, (inv @ (gR - tb))[0] - (inv @ (gL - tb))[0] + 3.0))
    r.append(1.0 * max(0.0, (inv @ (eR - tb))[0] + 2.0))
    r.append(1.0 * max(0.0, -(inv @ (eL - tb))[0] + 2.0))
    return r


def solve_key(spec, guess):
    fixed = {"body": spec.get("body", (0, 0, 0))}
    fixed.update(spec.get("extra", {}))
    G = np.asarray(spec["G"], float)
    d = unit(spec["d"])
    b = np.asarray(spec["b"], float)
    if spec.get("frame") == "body":
        Rb = _rot(*fixed["body"])
        G = Rb @ (G - PIV["body"]) + PIV["body"]
        d, b = Rb @ d, Rb @ b
    b = unit(b - (b @ d) * d)
    left = spec.get("left", False)
    armL = spec.get("armL")
    x0 = np.array(guess, float)
    if armL is not None and not left:
        x0[12:21] = list(armL) + [0, 0, 0, 0, 0, 0]
    free = np.arange(21) if left else np.arange(12)

    def full(z):
        x = x0.copy()
        x[free] = z
        return x

    def res(z):
        x = full(z)
        pose = pose_from(x, fixed)
        xf = xforms(pose)
        g, Rs = frame(xf, "socket_main")
        r = [1.0 * (g - G), 4.0 * (-Rs[:, 2] - d), 4.0 * (-Rs[:, 1] - b)]
        if left:
            gl, Rl = frame(xf, "socket_off")
            v = gl - g
            s = v @ d
            r += [2.0 * (v - s * d), 2.5 * (-Rl[:, 2] - d),
                  [0.6 * max(0.0, spec.get("smin", 6) - s), 0.6 * max(0.0, s - spec.get("smax", 13))]]
        r.append(penalties(pose, xf))
        r.append(0.01 * (x[free] - x0[free]))
        return np.concatenate([np.ravel(np.asarray(a, float)) for a in r])

    best = None
    rng = np.random.default_rng(5)
    starts = [x0[free]] + [x0[free] + rng.normal(0, 35, len(free)) for _ in range(10)]
    for z0 in starts:
        rr = least_squares(res, z0, method="trf", max_nfev=1500)
        if best is None or rr.cost < best.cost:
            best = rr
    x = full(best.x)
    pose = pose_from(x, fixed)
    xf = xforms(pose)
    g, Rs = frame(xf, "socket_main")
    line = (f"grip err {np.linalg.norm(g - G):.2f}, tip dir {np.degrees(np.arccos(np.clip(-Rs[:, 2] @ d, -1, 1))):.1f}deg, "
            f"blade dir {np.degrees(np.arccos(np.clip(-Rs[:, 1] @ b, -1, 1))):.1f}deg")
    if left:
        gl, Rl = frame(xf, "socket_off")
        v = gl - g
        s = v @ d
        line += f"; left off-shaft {np.linalg.norm(v - s * d):.2f} at {s:.1f}, left dir {np.degrees(np.arccos(np.clip(-Rl[:, 2] @ d, -1, 1))):.1f}deg"
    pen = np.linalg.norm(penalties(pose, xf))
    line += f"; penalty {pen:.2f}"
    return x, pose, line


# ---------------------------------------------------------------- キー
# G: 右の握り (モデルの座標)、d: 穂先の向き、b: 刃の向き。left: 左手を柄に添える (smin..smax: 右手から先へ何単位)
# 前は -Z、番兵長の右は -X、上は +Y。

HOLD = dict(G=(-15.5, 17.5, -10.5), d=(0, 1, 0), b=(0, 0, -1), armL=(0, 0, 4))   # 鉾を体の横に立てる

# この体は肩幅が広く腕が短いので、両手で持てるのは「胸の前で、柄がほぼ左右に通る」形だけ (穂先は胴の左)。
# 向きの変化は胴のひねりで出す。これらのキーは胴の座標 (frame="body") で書く。
TWO_HAND = dict(G=(-3.5, 22, -10.5), d=(0.97, 0.05, -0.25), b=(0, 0, -1), left=True, frame="body", smin=5.5, smax=8)
RAISED = dict(G=(-4, 28.5, -7), d=(0.7, 0.6, 0.4), b=(0, 0.5, -0.85), left=True, frame="body", smin=5.5, smax=8)
STRIKE = dict(G=(-4, 23, -9.5), d=(0.85, -0.3, -0.4), b=(0, -0.8, -0.6), left=True, frame="body", smin=5.5, smax=8)

SPECS = {
    "rest": [dict(t=0.0, **HOLD)],
    "sweep": [
        dict(t=0.00, **HOLD),
        # 振りかぶり: 両手で胸の前に構え、胴を左へひねる (穂先は左の後ろ)
        dict(t=0.30, **dict(TWO_HAND, body=(0, -40, 0))),
        # 薙ぎ: 胴を右へひねり、穂先が左前を通る
        dict(t=0.50, **dict(TWO_HAND, body=(0, 35, 0))),
        # 振り抜き: 左手を離し、右腕を伸ばして穂先を右前へ
        dict(t=0.62, G=(-10, 21, -13), d=(0.3, 0, -0.95), b=(-1, 0, -0.3), frame="body", body=(0, 55, 0), armL=(-20, 0, 25)),
        dict(t=0.85, G=(-10, 21, -13), d=(0.3, 0, -0.95), b=(-1, 0, -0.3), frame="body", body=(0, 50, 0), armL=(-15, 0, 20)),
        dict(t=1.10, **HOLD),
    ],
    "slam": [
        # 両手で振りかぶる (胴は左へひねって後ろへ反る。穂先は左肩の上から後ろへ)
        dict(t=0.00, **dict(RAISED, body=(15, -30, 0))),
        # 打ち下ろし: 胴を右へひねって前へ傾け、刃を前の地面へ
        dict(t=0.18, **dict(STRIKE, body=(-22, 45, 0))),
        dict(t=0.55, **dict(STRIKE, body=(-20, 42, 0))),
        dict(t=0.90, **HOLD),
    ],
    "leap": [
        dict(t=0.00, **HOLD),
        dict(t=0.30, **dict(HOLD, body=(-12, 0, 0), extra={"legR": (-15, 0, 0), "legL": (-15, 0, 0)})),
        # 空中: 両手で振りかぶったまま (着地で slam が続く)
        dict(t=0.55, **dict(RAISED, body=(15, -30, 0), extra={"legR": (20, 0, 0), "legL": (-15, 0, 0)})),
        # 着地 (slam が上書きする) まで構えを保つ。着地しないまま終わったときは、4秒で通常の姿勢へ戻る
        dict(t=4.00, **dict(RAISED, body=(15, -30, 0), extra={"legR": (20, 0, 0), "legL": (-15, 0, 0)})),
    ],
    "roar": [
        dict(t=0.00, **HOLD),
        # 鉾を高く掲げ、左腕を広げ、頭を反らせて吠える
        dict(t=0.35, G=(-17, 31, -6), d=(-0.15, 1, -0.1), b=(0, 0, -1), armL=(15, 0, 60), body=(10, 0, 0),
             extra={"head": (25, 0, 0)}),
        dict(t=1.10, G=(-17, 31, -6), d=(-0.15, 1, -0.1), b=(0, 0, -1), armL=(15, 0, 60), body=(10, 0, 0),
             extra={"head": (25, 0, 0)}),
        dict(t=1.40, **HOLD),
    ],
}
LOOP = {}


def solve_all():
    out = {}
    rest_x = None
    for name, specs in SPECS.items():
        keys = []
        guess = np.zeros(21) if rest_x is None else rest_x.copy()
        for s in specs:
            if s is not SPECS["rest"][0] and s == dict(t=s["t"], **HOLD) and rest_x is not None:
                x = rest_x.copy()
                pose = pose_from(x, {"body": (0, 0, 0)})
                line = "hold"
            else:
                x, pose, line = solve_key(s, guess)
            print(f"{name} t={s['t']:.2f}: {line}", flush=True)
            guess = x
            keys.append((s["t"], pose))
        if name == "rest":
            rest_x = guess.copy()
        out[name] = keys
    return out


# ---------------------------------------------------------------- アニメーション (JSON)

def to_json(r):
    """図の角度 (voxel._rot) → アニメーションの JSON の角度。
    GeckoLib は geo の X を反転し、回転の X と Y の符号を反転する (Z→Y→X の順)。
    図の (ax, ay, az) とゲームの姿が一致するのは、JSON に (-ax, ay, -az) を書いたとき (ゲームの姿は図の左右反転)。"""
    return (-float(r[0]), float(r[1]), -float(r[2]))


def rot_list(pose, b):
    return [round(v, 2) + 0.0 for v in to_json(pose.get(b, (0.0, 0.0, 0.0)))]


def clip(poses, loop=False):
    bones = {}
    for b in GROUP:
        if all(all(abs(v) < 1e-9 for v in p.get(b, (0, 0, 0))) for _, p in poses):
            continue
        bones[b] = {"rotation": {f"{t:.2f}": rot_list(p, b) for t, p in poses}}
    return {"loop": loop, "animation_length": round(poses[-1][0], 2) if poses[-1][0] > 0 else 0.05, "bones": bones}


def molang_clips(rest):
    """idle と walk: 鉾を立てて持つ姿勢に、小さな揺れを足す (math.sin は度)。"""
    def m(b, extra=("0", "0", "0")):
        base = to_json(rest.get(b, (0, 0, 0)))
        return [f"{base[i]:.2f} + {extra[i]}" if extra[i] != "0" else f"{base[i]:.2f}" for i in range(3)]

    idle = {"loop": True, "animation_length": 3.0, "bones": {
        "body": {"rotation": ["1.5 + math.sin(query.anim_time * 120) * 1.5", "0", "0"]},
        "head": {"rotation": ["math.sin(query.anim_time * 120 + 90) * 2", "math.sin(query.anim_time * 60) * 4", "0"]},
        "armR": {"rotation": m("armR", ("math.sin(query.anim_time * 120) * 1.5", "0", "0"))},
        "forearmR": {"rotation": m("forearmR")},
        "handR": {"rotation": m("handR")},
        "socket_main": {"rotation": m("socket_main")},
        "armL": {"rotation": ["math.sin(query.anim_time * 120 + 180) * 2", "0", "-4"]},
    }}
    walk = {"loop": True, "animation_length": 1.0, "bones": {
        "legR": {"rotation": ["-math.sin(query.anim_time * 360) * 30", "0", "0"]},
        "legL": {"rotation": ["math.sin(query.anim_time * 360) * 30", "0", "0"]},
        "armR": {"rotation": m("armR", ("math.sin(query.anim_time * 360) * 6", "0", "0"))},
        "forearmR": {"rotation": m("forearmR")},
        "handR": {"rotation": m("handR")},
        "socket_main": {"rotation": m("socket_main")},
        "armL": {"rotation": ["-math.sin(query.anim_time * 360) * 15", "0", "-4"]},
        "body": {"rotation": ["2", "math.sin(query.anim_time * 360) * 2", "0"]},
    }}
    return idle, walk


def write_anim(actions, path):
    rest = actions["rest"][0][1]
    idle, walk = molang_clips(rest)
    data = {"format_version": "1.8.0", "animations": {
        "animation.whalebone_captain.idle": idle,
        "animation.whalebone_captain.walk": walk,
    }}
    for name, poses in actions.items():
        if name == "rest":
            continue
        data["animations"][f"animation.whalebone_captain.{name}"] = clip(poses, LOOP.get(name, False))
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


# ---------------------------------------------------------------- 描画

def draw(geo, tex, gl, pgeo, ptex, pose, yaw, size):
    faces = load_geo(geo, tex, gl, pose=pose) + load_geo(pgeo, ptex, None, pose=pose)
    return render(faces, yaw=yaw, pitch=14, size=size, margin=0.03)


def keyframe_pose(keys, t):
    """キーの間を線形に補間した姿勢。"""
    ts = [k[0] for k in keys]
    if t <= ts[0]:
        return keys[0][1]
    for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            a = (t - t0) / (t1 - t0)
            names = set(p0) | set(p1)
            return {n: tuple((1 - a) * np.array(p0.get(n, (0, 0, 0))) + a * np.array(p1.get(n, (0, 0, 0)))) for n in names}
    return keys[-1][1]


def main(install=False):
    actions = solve_all()
    m = build_b()
    v2.BONES = NEW_BONES
    geo, tex, gl, nbox, nface = v2.export(m, "b", SUBJECT)
    ov = v2.overlaps(m)
    print(f"boxes {nbox} faces {nface} overlaps {len(ov)} texture {tex.shape[1]}x{tex.shape[0]}", flush=True)
    pgeo, ptex = pike_geo()

    rest = actions["rest"][0][1]
    sweep = dict(actions["sweep"])
    slam = dict(actions["slam"])
    rows = [("通常", rest), ("薙ぎ払い", sweep[0.50]), ("叩きつけ", slam[0.18])]
    cols = []
    for label, yaw in (("右前から", 145), ("左前から", 215)):
        cols.append((label, [draw(geo, tex, gl, pgeo, ptex, p, yaw, (460, 600)) for _, p in rows]))
    sheet_img = sheet("鯨骨の番兵長 第4回 (鉾は別のモデル、受け口 socket_main に描く)", cols,
                      "行: 通常 / 薙ぎ払い (途中) / 叩きつけ (打ち下ろし)。鉾はアイテムの3Dモデルを 1.8 倍で合成")
    OUT.mkdir(parents=True, exist_ok=True)
    sheet_img.save(OUT / f"{SUBJECT}.png")

    dbg = OUT / SUBJECT
    dbg.mkdir(parents=True, exist_ok=True)
    for name, keys in actions.items():
        imgs = []
        for yaw in (145, 215):
            for t, p in keys:
                im = draw(geo, tex, gl, pgeo, ptex, p, yaw, (260, 340))
                ImageDraw.Draw(im).text((8, 6), f"{t:.2f}", font=font(16), fill=(220, 220, 230))
                imgs.append(im)
        n = len(keys)
        strip = Image.new("RGB", (n * 270, 2 * 350), (18, 18, 26))
        for i, im in enumerate(imgs):
            strip.paste(im, ((i % n) * 270, (i // n) * 350))
        strip.save(dbg / f"{name}_keys.png")

    if install:
        (GAME / "geo/entity" / f"{SUBJECT}.geo.json").write_text(json.dumps(geo, ensure_ascii=False), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(GAME / "textures/entity" / f"{SUBJECT}.png")
        Image.fromarray(gl, "RGBA").save(GAME / "textures/entity" / f"{SUBJECT}_glowmask.png")
        write_anim(actions, GAME / "animations/entity" / f"{SUBJECT}.animation.json")
        print("installed", flush=True)


if __name__ == "__main__":
    main(install="--install" in sys.argv)
