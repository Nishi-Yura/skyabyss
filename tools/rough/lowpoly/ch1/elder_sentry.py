"""老いた歩哨 (elder_sentry) 低ポリ ラフ 3 案。骨の番兵 (bone_sentinel) の変種。

作り方: 骨の番兵の本体・盾のメッシュ (bone_sentinel_model.build_model) をそのまま読み、部品を足す。
全体を少し大きく (背が高い)、盾は一回り大きく、肩に苔、盾に琥珀の割れ目の目。三案は部品だけ違う:
A 苔の守り手 (背の苔の幕 + 肩の苔の塊) / B 石板の守り手 (胸と腹の石の板、折れた角、石の盾) / C 頭巾の長老 (頭巾・首の骨の笛の鎖・背の旗)。
老番兵 (elder_sentinel.py) は同じ三案の大きな版 (全体を大きく、腰の房・腿の板を足し、盾を大きく)。

技の予告 (姿勢): 盾の突進 = 盾を前へ・体を前へ傾ける (床の線 1.2 秒) / 骨の笛 = 骨の笛を口へ / 足踏みの輪 = 片脚を上げ拳を下へ。
座標: 1 単位 = 1/16 ブロック、y が上、前 = -Z。
"""
import math

from ck import *  # noqa
from bone_sentinel_model import build_model  # noqa
import ck as _ck  # noqa

# 盾の割れた目は光る (材質 eyeglow の面の画素は琥珀で光らせ、光る画素のマスクに入れる)。ck の paint を包む。
_paint0 = _ck.paint


def _paint_glow(faces, size, P, N, M, pal, eyes=()):
    atlas, glow = _paint0(faces, size, P, N, M, pal, eyes)
    ids = [i for i, f in enumerate(faces) if f.mat == "eyeglow"]
    if ids:
        sel = np.isin(np.where(M >= 0, M, 0), ids) & (M >= 0)
        atlas = atlas.copy()
        atlas[sel, :3] = (255, 196, 84)
        atlas[sel, 3] = 255
        glow = glow | sel
    return atlas, glow


_ck.paint = _paint_glow


def _check_buried_hull(faces, thr=0.45, tol=0.35):
    """ck.check_buried と同じ意味の埋まり検査。ただし Delaunay を使わず凸包の不等式だけで判定する
    (薄い板の盾は Delaunay が平面に退化して、何でも中に入ったと出るため)。このファイルの出力だけに使う。"""
    from scipy.spatial import ConvexHull
    parts = {}
    for f in faces:
        parts.setdefault(getattr(f, "part", f.bone), []).append(f)
    samp, hulls = {}, {}
    for k, fs in parts.items():
        v = np.vstack([f.verts for f in fs])
        samp[k] = np.vstack([f.verts.mean(0)[None] for f in fs] + [f.verts for f in fs])
        try:
            hulls[k] = (ConvexHull(v), v)
        except Exception:
            hulls[k] = None
    bad = []
    for a in parts:
        for b in parts:
            if a == b or hulls[b] is None or hulls[a] is None:
                continue
            hb, vb = hulls[b]
            if np.ptp(samp[a], 0).prod() > np.ptp(vb, 0).prod():
                continue
            s = samp[a] @ hb.equations[:, :3].T + hb.equations[:, 3]
            inside = s.max(1) <= 0
            if not inside.any():
                continue
            depth = -s.max(1)
            frac = float(((depth > tol) & inside).sum()) / len(samp[a])
            if frac > thr:
                bad.append((a, b, round(frac, 2)))
    return bad


_ck.check_buried = _check_buried_hull

S_E = 1.12         # 全体の大きさ (骨の番兵より背が高い)
SHIELD_E = 1.15     # 盾の大きさ
SOCK = np.array([7.5, 9.0, 0.0])   # 盾の握り (骨の番兵の socket_off の pivot)
SHIELD_OFF = np.array([0.8, 0.0, 0.0])   # 盾を手から外へ離す (手首が盾に埋まらないように)

# 骨の名前・親・pivot (骨の番兵の geo と同じ。盾は socket_off の代わりに shield を左前腕の下へ置く)
BASE_BONES = {
    "body": (None, [0.0, 12.0, 0.0]),
    "head": ("body", [0.0, 24.0, 0.0]),
    "left_arm": ("body", [5.5, 20.0, 0.0]),
    "left_forearm": ("left_arm", [5.5, 14.5, 0.0]),
    "shield": ("left_forearm", [7.5, 9.0, 0.0]),
    "right_arm": ("body", [-5.5, 20.0, 0.0]),
    "right_forearm": ("right_arm", [-5.5, 14.5, 0.0]),
    "leg_l": ("body", [2.0, 10.0, 0.0]),
    "leg_r": ("body", [-2.0, 10.0, 0.0]),
}


def bones_for(S):
    return {k: (p, [float(x) * S for x in piv]) for k, (p, piv) in BASE_BONES.items()}


def xf(faces, fn):
    """面の頂点・基準点・絵の座標を fn で写す (正の倍率と平行移動だけなので向きは変わらない)。"""
    for f in faces:
        f.verts = np.array([fn(p) for p in f.verts], float)
        if f.loc is not None:
            f.loc = np.array([fn(p) for p in f.loc], float)
        f.ref = np.asarray(fn(f.ref), float)
    return faces


def arc_grid(bone, mat, rows_def, angles, ref):
    """高さごとの行 (y, rx, rz, zc, dy列) で、角度 angles の弧の格子を作る。x = rx sin a, z = zc + rz cos a (後ろが +z)。"""
    rows = []
    for y, rx, rz, zc, dy in rows_def:
        rows.append([np.array([rx * math.sin(math.radians(a)), y + (dy[j] if dy else 0.0),
                               zc + rz * math.cos(math.radians(a))]) for j, a in enumerate(angles)])
    return grid(bone, mat, rows, ref)


# ====================================================================== 三案の部品 (骨の番兵の単位)

def part_cloak(F):
    """A: 背の苔の幕 (背だけ。前は開く) と裾の苔。"""
    A = [-90, -60, -30, 0, 30, 60, 90]
    hem = [0.0, -1.2, 0.2, -1.6, 0.1, -1.0, 0.0]
    F += part("cloak", arc_grid("body", "moss", [(23.0, 4.8, 3.6, 0.0, None), (19.5, 4.9, 3.8, 0.0, None),
                                                 (16.0, 4.4, 3.5, 0.0, None), (12.8, 3.4, 2.9, 0.0, hem)],
                                A, ref=(0, 17, 0)))
    for s in (-1, 1):
        F += part(f"hemmoss{s}", ellipsoid("body", "moss", (s * 2.9, 11.9, 3.2), (1.2, 0.8, 1.1), segs=6, rings=2))
    F += part("mosstuft", ellipsoid("body", "moss", (0.0, 16.5, 4.3), (1.0, 2.2, 0.8), segs=6, rings=2))


def part_plates(F):
    """B: 胸と腹の石の板、折れた角 (左は残り、右は折れて短い)。"""
    F += part("chest_plate", ellipsoid("body", "stone", (0, 18.6, -3.5), (3.2, 2.0, 1.0), segs=8, rings=3))
    F += part("belly_plate", ellipsoid("body", "stone", (0, 13.6, -3.0), (2.6, 1.2, 0.9), segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"forearm_plate{s}", ellipsoid("left_forearm" if s > 0 else "right_forearm", "stone",
                                                 (s * 3.9, 12.5, -0.2), (1.6, 2.2, 1.5), segs=6, rings=2))
    # 矢の代わりの骨笛: 右の前腕の先から前へ伸びる (予告で口へ上げる)
    F += part("flute", tube("right_forearm", "bone", [(-5.5, 9.6, -1.4), (-5.5, 9.2, -4.5), (-5.5, 8.9, -7.5)],
                            ra=[0.45, 0.4, 0.35], rb=[0.45, 0.4, 0.35], sides=4, hint=(1, 0, 0)))
    F += part("horn_l", spike("head", "bone", (2.6, 31.2, -0.8), (0.45, 1.0, -0.05), 4.0, 0.9, sides=4, rings=2))
    F += part("horn_r", spike("head", "stone", (-2.6, 31.2, -0.8), (-0.4, 1.0, 0.0), 1.6, 0.8, sides=4, rings=1))


def part_hood(F):
    """C: 頭巾 (頭と背。顔の前は開く)、首の骨の笛の鎖、背の旗の竿と破れた布。"""
    A = [-120, -80, -40, 0, 40, 80, 120]
    rows = [(22.8, 4.6, 4.2, -0.4, None), (26.0, 4.5, 4.4, -0.6, None), (29.2, 3.5, 3.6, -0.8, None),
            (31.4, 2.0, 2.4, -0.9, None)]
    F += part("hood", arc_grid("head", "cloth", rows, A, ref=(0, 27, -0.5)))
    cap = [np.array([0.4 * math.sin(math.radians(a)), 32.7, -0.9 + 0.4 * math.cos(math.radians(a))]) for a in A]
    F += part("hood_cap", poly("head", "cloth", cap, (0, 27, -0.5)))
    beads = [(-2.4, 20.9, -3.6), (-1.4, 19.4, -4.3), (0.0, 18.6, -4.6), (1.4, 19.4, -4.3), (2.4, 20.9, -3.6)]
    for i, p in enumerate(beads):
        F += part(f"bead{i}", ellipsoid("body", "bone", p, (0.45, 0.45, 0.45), segs=6, rings=2))
    F += part("whistle", tube("body", "bone", [(0.0, 18.6, -4.6), (0.0, 16.8, -4.9), (0.0, 15.4, -5.0)],
                              ra=[0.45, 0.4, 0.3], rb=[0.45, 0.4, 0.3], sides=4, hint=(1, 0, 0)))
    F += part("pole", tube("body", "bone", [(-3.0, 17.5, 3.6), (-3.2, 25.5, 4.2), (-3.0, 33.8, 4.6)],
                           ra=[0.4, 0.35, 0.3], rb=[0.4, 0.35, 0.3], sides=4, hint=(1, 0, 0)))
    # 破れた旗: 竿から右へ垂れる。右の端は段ごとに違う欠け
    ys = [33.2, 30.0, 26.6, 23.2, 20.0]
    edge = [3.4, 2.4, 3.0, 2.0, 2.8]
    rows = []
    for y, e in zip(ys, edge):
        rows.append([np.array([x, y, 4.8]) for x in (-3.0, -1.3, 0.3, e)])
    F += part("banner", grid("body", "cloth", rows, ref=(0, 25, 0)))


def part_big(F):
    """老番兵だけ: 腰の房 (前へ垂れる布) と腿の石の板。"""
    for x in (-3.2, -1.6, 0.0, 1.6, 3.2):
        rows = [[np.array([x + dx, y, -3.2]) for dx in (-0.8, 0.0, 0.8)] for y in (13.4, 11.6, 9.8, 8.0)]
        F += part(f"tassel{x}", grid("body", "cloth", rows, ref=(0, 15, 0)))
    for s in (-1, 1):
        F += part(f"thigh{s}", ellipsoid("leg_l" if s > 0 else "leg_r", "stone", (s * 2.4, 6.6, -2.2),
                                         (1.9, 1.7, 0.8), segs=6, rings=3))


# ====================================================================== 組み立て

def build_variant(k, S, shield_s, big=False):
    """k = a/b/c。骨の番兵の本体と盾を読み、部品を足して S 倍する。戻り値: (faces, eyes)。"""
    body, sf, ex = build_model()
    # 盾の中央の骨の棘は、琥珀の割れ目の目の場所に当たるので外す (目を盾の面の中央に置く)
    sf = [f for f in sf if not (f.mat == "bone" and f.verts[:, 0].mean() > 0.5 and 0 < f.verts[:, 1].mean() < 4.5)]
    xf(sf, lambda p: SOCK + SHIELD_OFF + p * shield_s)
    smap = {"shield": "plate", "shield_back": "stone", "shield_edge": "stone"}
    if k == "b":
        smap["shield"] = "stone"        # B は盾も石の板
    for f in sf:
        f.mat = smap.get(f.mat, f.mat)
    F = body + sf
    # 三案に共通: 肩の苔、盾の割れた琥珀の目
    F += part("moss_l", ellipsoid("left_arm", "moss", (6.2, 24.1, 0.3), (2.6, 0.9, 2.4), segs=7, rings=3))
    F += part("moss_r", ellipsoid("right_arm", "moss", (-6.2, 24.1, 0.3), (2.6, 0.9, 2.4), segs=7, rings=3))
    F += part("tuft_l", ellipsoid("left_arm", "moss", (7.6, 21.0, -1.8), (1.2, 1.8, 1.0), segs=6, rings=2))
    F += part("tuft_r", ellipsoid("right_arm", "moss", (-7.6, 21.0, -1.8), (1.2, 1.8, 1.0), segs=6, rings=2))
    sh = shield_s
    ex_ = SOCK + SHIELD_OFF + np.array([1.3 * sh + 0.25 * sh, 2.5 * sh, 0.0])
    F += part("shield_eye", ellipsoid("shield", "eyeglow", ex_, (0.45 * sh, 1.1 * sh, 0.9 * sh), segs=6, rings=2))
    # 割れ目: 目の上に細い骨の欠片を一本 (埋まらないよう外へ)
    F += part("shield_crack", spike("shield", "bone", ex_ + np.array([0.3 * sh, 1.2 * sh, 0.0]),
                                    (0.1, 1.0, 0.0), 1.2 * sh, 0.25 * sh, sides=3, rings=1))
    if k == "a":
        part_cloak(F)
    elif k == "b":
        part_plates(F)
    else:
        part_hood(F)
    if big:
        part_big(F)
    xf(F, lambda p: p * S)
    eyes = [dict(c=np.asarray(c, float) * S, rx=1.5 * S, ry=1.0 * S, pr=(0.55 * S, 0.38 * S), tilt=0.15, zr=2.4 * S)
            for c in ex["eyes"]]
    return F, eyes


def nose_for(S):
    return np.array([0.0, 27.3, -5.4]) * S


def pal_for(S):
    return {"mats": {
        "bone": mat(BONE, 0.7, crack=0.05),
        "tooth": mat(BONE, 0.78, crack=0.0),
        "mouth": mat(DARK, 0.5, crack=0.0, min=0),
        "stone": mat(STONE, 0.66, crack=0.22, stain=dict(ramp=MOSS, thr=0.93, up=0.3, conc=0.2)),
        "plate": mat(BONE, 0.66, crack=0.16, stain=dict(ramp=MOSS, thr=0.95, up=0.3, conc=0.2)),
        "moss": mat(MOSS, 0.6, crack=0.0, noise=0.25),
        "amber": mat(AMBER, 0.7, crack=0.6, min=0),
        "cloth": mat(BROWN, 0.55, crack=0.1, noise=0.2),
        "dark": mat(DARK, 0.5, crack=0.0, min=0),
    }, "skull_bone": "head", "face_mat": "bone", "noses": [nose_for(S)]}


def build(k):
    return build_variant(k, S_E, SHIELD_E, big=False)


# 予告の姿勢 (骨の剛体の回転 (x, y, z 度)。前 = -Z。盾は左前腕の下で、前腕を y 軸で 90 度回すと板が前を向く)
POSES = {
    "idle": {},
    # 盾の突進: 体を沈めて前へ傾け、盾を前に低く構える (板は前を向く)
    "charge": {"body": (-26, 0, 0), "head": (-8, 0, 0), "left_arm": (62, 0, 0), "left_forearm": (0, 90, 0),
               "right_arm": (40, 0, 0), "right_forearm": (-30, 0, 0), "leg_l": (30, 0, 0), "leg_r": (-14, 0, 0)},
    # 骨笛: 頭を後ろへ反らし、右腕を曲げて笛を口へ (solver の値)
    "whistle": {"head": (14, 0, 0), "right_arm": (180, -40, 10), "right_forearm": (-150, 0, 0)},
    # 足踏みの輪: 片脚を高く上げ、拳を頭の上へ
    "stomp": {"body": (4, 0, 0), "leg_r": (80, 0, 0), "right_arm": (170, 0, 0), "left_arm": (-8, 0, 0)},
}

SPEC = dict(
    id="elder_sentry", short="老いた歩哨", kind="洞の主 (名づき・変種) 3案",
    title="老いた歩哨 (elder_sentry) — 骨の番兵の変種・低ポリ + ドット絵 3 案",
    sub=["骨の番兵の本体に部品を足した変種。背が高く、盾に琥珀の割れ目の目、肩に苔。HP 140。",
         "予告の三つ: 盾の突進 (盾を前へ・床の線 1.2 秒) / 骨笛 (骨の笛を口へ・射手を呼ぶ) / 足踏みの輪 (片脚を上げ拳を下へ)。"],
    labels=["A 苔の守り手", "B 石板の守り手", "C 頭巾の長老"],
    rows=[("前 3/4 (待機。盾の琥珀の割れ目と肩の苔)", "idle", 212, 10, 420, (0, 18, 0), 9.0),
          ("横 (A の背の幕、B の折れた角、C の竿)", "idle", 270, 6, 420, (0, 18, 0), 9.0),
          ("予告 1: 盾の突進 (床の線 1.2 秒)", "charge", 240, 8, 420, (0, 18, 0), 9.0),
          ("予告 2: 骨笛 (右腕で口へ・頭を反らす。反対側の前 3/4)", "whistle", 148, 6, 420, (0, 18, 0), 9.0),
          ("予告 2: 骨笛 (横から)", "whistle", 90, 4, 420, (0, 18, 0), 9.0),
          ("予告 3: 足踏みの輪 (片脚を高く・拳を頭上へ。反対側の前 3/4)", "stomp", 148, 8, 420, (0, 18, 0), 9.0),
          ("予告 3: 足踏みの輪 (横から)", "stomp", 90, 4, 420, (0, 18, 0), 9.0),
          ("後ろ 3/4 (背の幕・頭巾・旗)", "idle", 32, 12, 420, (0, 18, 0), 9.0)],
    options={
        "a": dict(name="A 苔の守り手", desc="背が高く、肩に苔の塊、背には苔の幕（背だけ。前は開く）。裾がちぎれた苔。盾は骨の板で琥珀の割れ目の目。苔の緑が洞の奥の影で一番目立つ。突進は盾を前へ傾けて、幕が後ろへ流れる。"),
        "b": dict(name="B 石板の守り手", desc="胸と腹に石の板を貼った、割れ殻の守り。左の角は残り、右の角は折れて短い。盾も石の板で、灰青の重い輪郭。一番硬く見える案。骨笛は石の板の上で胸の前に下がる。"),
        "c": dict(name="C 頭巾の長老", desc="頭巾が頭と背を覆い、顔の前は開く。首に骨の笛を鎖で下げ、背に破れた旗の竿。縦に長いシルエットで、骨笛の予告が一番読める（笛を口へ上げると鎖と笛が揺れる）。")},
    extra="骨の番兵の本体 (骨 9 本) + 盾 (左前腕の下) を流用し、部品を足す。盾の琥珀の目は割れた素材（amber、ひび多め）。全体は骨の番兵の 1.12 倍。A/B/C は色と部品だけ違う。",
    budget="900〜1,200",
    atlas_h=300)

if __name__ == "__main__":
    run(SPEC, build, pal_for(S_E), bones_for(S_E), POSES, hint=176)
