"""燠の巨獣 (4 体目) の敵ラフの共通部品。

tide_mobs.py / tide_mobs_v2.py の Model (面ごとの uv、2 画素/単位、glowmask)、game_render、
銛と受け口の考え方をそのまま使い、ここでは次を足す:
- Sculpt: 0.5 単位の格子の「セル」に彫る。辞書なので同じセルに 2 つは置けない (重なりは作れない)。
- 貪欲な直方体まとめ + 見えない箱 (6 面すべて他のセルに覆われた箱) の除去。
  隠せる骨 (殻・炎など、状態で表示を切り替える骨) は「覆う側」に数えない (外れた時に穴が開かないように)。
- 状態ごとのテクスチャ (同じ uv の配置で 2 枚描く: 殻のある姿 / 燃え上がった姿)。
- 姿勢付きの重なり検査 (骨を回し、位置もずらした後で、立方体どうしを分離軸で比べる)。
- 位置のずらし (GeckoLib の position キー) と、骨を隠す描画。

座標: y が上、前は +z で作る (書き出す geo は root 骨で yaw 180 度)。1 単位 = 1/16 ブロック。
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402

ROOT = vx.ROOT
REVIEW = ROOT / "docs" / "review" / "ember_roughs"
MOBS = REVIEW / "mobs"
VIEW = tm.VIEW
h01 = tm.h01
hexrgb = tm.hexrgb

# 燠の巨獣の色 (暗い, 中間, 明るい, 最も明るい)。tm.RAMP に足して Model.finish から使えるようにする
EMBER_RAMP = {
    "basalt": ("#1E1B22", "#34303B", "#4C4654", "#6A6272"),
    "basalt_lt": ("#24202A", "#39343F", "#504956", "#6A6270"),
    "ash": ("#57524E", "#827C76", "#ABA59D", "#D3CEC6"),
    "ash_cloth": ("#4B4642", "#6E6862", "#938C84", "#B9B2A8"),
    "crust": ("#4F4A46", "#716B65", "#958E86", "#BAB3A9"),
    "slag": ("#3A271C", "#5A3E2D", "#7C5840", "#9C7858"),
    "slag_dk": ("#1B120E", "#2E2019", "#433024", "#5A4230"),
    "rust": ("#4F2412", "#7E3E1E", "#A85E2D", "#CB8546"),
    "bronze": ("#43301A", "#6E5129", "#9A763C", "#C29C58"),
    "iron": ("#1E1C1F", "#353238", "#4D4950", "#67626A"),
    "char": ("#0D0A0A", "#191313", "#271E1C", "#372A26"),
    "leather": ("#26170F", "#3E2618", "#5C3A25", "#7C5236"),
    "wood": ("#2C1C12", "#4A3020", "#68452E", "#86603F"),
    "deepred": ("#3A0907", "#68130D", "#982218", "#C03824"),
    "ember": ("#8A2A0A", "#D2551A", "#F58A2A", "#FFC060"),
    "molten": ("#C26A10", "#F5A623", "#FFD25A", "#FFF2B0"),
    "tile": ("#4A1E14", "#7A3322", "#A04A30", "#C26A48"),
    "tile_dk": ("#30140E", "#4E2318", "#6C3322", "#8A4630"),
    "kiln": ("#4A2A1C", "#74442C", "#9A6040", "#BF8358"),
    "wax": ("#8C6A3A", "#C9A15E", "#E8CC8A", "#FFF0C8"),
    "fuzz": ("#4E4A47", "#77716B", "#A09992", "#C8C1B8"),
    "wing_grey": ("#3E3A38", "#5F5955", "#857E77", "#ABA49B"),
    "wing_or": ("#7A2A0C", "#C2501A", "#EE8A30", "#FFC068"),
    "wing_soot": ("#1A1513", "#2C2420", "#40352E", "#56483E"),
    "eye": ("#0A0808", "#140F0E", "#201816", "#2E2320"),
}
tm.RAMP.update(EMBER_RAMP)
GLOW_ALWAYS = {"molten", "ember"}  # この材質は常に光る (Model.finish の GLOW_MATS とは別に、glowfn で扱う)

DIRS6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))


# ---------------------------------------------------------------- 彫る (セルの辞書)

class Sculpt:
    """0.5 単位の格子のセル (i, j, k) -> (骨, 材質)。セルの最小の隅は (i/2, j/2, k/2)。"""

    def __init__(self, ident: str):
        self.ident = ident
        self.cells: dict[tuple, tuple] = {}
        self.bones: list[dict] = []
        self.hideable: set[str] = set()   # 状態によって隠す骨 (覆う側に数えない)

    def bone(self, name, pivot, parent=None, hideable=False):
        b = {"name": name, "pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        self.bones.append(b)
        if hideable:
            self.hideable.add(name)

    def add(self, bone, pred, mat, bbox, over=False, mat_fn=None, sz=1):
        """bbox (単位) の中で pred(セルの中心) が真のセルを埋める。over=False なら空いている所だけ。
        sz=2 なら 1 単位のセル (2x2x2 の半セル) として判定し、全部空いている時だけ埋める。"""
        X0, Y0, Z0 = (int(math.floor(v * 2)) for v in bbox[:3])
        X1, Y1, Z1 = (int(math.ceil(v * 2)) for v in bbox[3:])
        if sz == 2:
            X0 -= X0 % 2
            Y0 -= Y0 % 2
            Z0 -= Z0 % 2
        n = 0
        for i in range(X0, X1, sz):
            for j in range(Y0, Y1, sz):
                for k in range(Z0, Z1, sz):
                    c = ((i + sz / 2) / 2, (j + sz / 2) / 2, (k + sz / 2) / 2)
                    if not pred(*c):
                        continue
                    sub = [(i + a, j + b, k + d) for a in range(sz) for b in range(sz) for d in range(sz)]
                    if not over and any(q in self.cells for q in sub):
                        continue
                    mt = mat_fn(c) if mat_fn else mat
                    if mt is None:
                        continue
                    for q in sub:
                        self.cells[q] = (bone, mt)
                    n += 1
        return n

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, over=False):
        return self.add(bone, lambda x, y, z: True, mat, (x0, y0, z0, x1, y1, z1), over=over)

    def carve(self, pred, bbox, bone=None):
        X0, Y0, Z0 = (int(math.floor(v * 2)) for v in bbox[:3])
        X1, Y1, Z1 = (int(math.ceil(v * 2)) for v in bbox[3:])
        for i in range(X0, X1):
            for j in range(Y0, Y1):
                for k in range(Z0, Z1):
                    q = (i, j, k)
                    if q in self.cells and pred((i + .5) / 2, (j + .5) / 2, (k + .5) / 2):
                        if bone is None or self.cells[q][0] == bone:
                            del self.cells[q]

    def recolor(self, pred, mat, bbox, bone=None, only=None):
        X0, Y0, Z0 = (int(math.floor(v * 2)) for v in bbox[:3])
        X1, Y1, Z1 = (int(math.ceil(v * 2)) for v in bbox[3:])
        for i in range(X0, X1):
            for j in range(Y0, Y1):
                for k in range(Z0, Z1):
                    q = (i, j, k)
                    if q in self.cells and pred((i + .5) / 2, (j + .5) / 2, (k + .5) / 2):
                        b, m = self.cells[q]
                        if (bone is None or b == bone) and (only is None or m in only):
                            self.cells[q] = (b, mat)

    def line(self, bone, p0, p1, mat, r=0.0, over=False):
        """p0 から p1 への線 (0.5 単位の段)。r > 0 なら太さ (中心からの距離)。"""
        p0, p1 = np.array(p0, float), np.array(p1, float)
        L = np.linalg.norm(p1 - p0)
        n = max(2, int(L * 4) + 1)
        for t in np.linspace(0, 1, n):
            p = p0 + (p1 - p0) * t
            q = tuple(int(math.floor(v * 2)) for v in p)
            if r <= 0:
                if over or q not in self.cells:
                    self.cells[q] = (bone, mat)
            else:
                self.add(bone, lambda x, y, z, p=p: (x - p[0]) ** 2 + (y - p[1]) ** 2 + (z - p[2]) ** 2 <= r * r,
                         mat, (p[0] - r - .5, p[1] - r - .5, p[2] - r - .5, p[0] + r + .5, p[1] + r + .5, p[2] + r + .5), over=over)


def ell(c, r, p=2.0):
    """楕円体 (p=2) / 角の丸い箱 (p>2) の判定。"""
    cx, cy, cz = c
    rx, ry, rz = r
    return lambda x, y, z: (abs(x - cx) / rx) ** p + (abs(y - cy) / ry) ** p + (abs(z - cz) / rz) ** p <= 1.0


def bb(c, r, pad=0.5):
    return (c[0] - r[0] - pad, c[1] - r[1] - pad, c[2] - r[2] - pad, c[0] + r[0] + pad, c[1] + r[1] + pad, c[2] + r[2] + pad)


def shell(sc: Sculpt, bone, c, r, t, mat, keep=None, p=2.0):
    """楕円体の外側に厚み t の殻 (空いている所だけ)。keep(x, y, z) が偽の所は外す。"""
    outer = ell(c, (r[0] + t, r[1] + t, r[2] + t), p)
    pred = (lambda x, y, z: outer(x, y, z) and (keep is None or keep(x, y, z)))
    return sc.add(bone, pred, mat, bb(c, (r[0] + t, r[1] + t, r[2] + t)))


def blob_noise(x, y, z, s=2.0, seed=0):
    """粗い塊の雑音 (0..1)。s 単位の塊ごとに同じ値。"""
    return h01(math.floor(x / s) + seed * 31, math.floor(y / s) + seed * 17, math.floor(z / s))


def voronoi_edge(x, y, z, s=3.0, w=0.32, seed=0):
    """3 次元のボロノイの境 (ひび割れの網)。境から w 以内なら True。"""
    gx, gy, gz = x / s, y / s, z / s
    ix, iy, iz = math.floor(gx), math.floor(gy), math.floor(gz)
    d1, d2 = 9.0, 9.0
    for a in (-1, 0, 1):
        for b in (-1, 0, 1):
            for c in (-1, 0, 1):
                cx, cy, cz = ix + a, iy + b, iz + c
                jx = h01(cx * 0.5 + seed, cy * 0.5, cz * 0.5)
                jy = h01(cx * 0.5, cy * 0.5 + seed + 7, cz * 0.5)
                jz = h01(cx * 0.5, cy * 0.5, cz * 0.5 + seed + 13)
                d = (gx - cx - jx) ** 2 + (gy - cy - jy) ** 2 + (gz - cz - jz) ** 2
                if d < d1:
                    d1, d2 = d, d1
                elif d < d2:
                    d2 = d
    return (math.sqrt(d2) - math.sqrt(d1)) * s < w


# ---------------------------------------------------------------- 箱にまとめる

def _grow(q, order, left):
    ext = [1, 1, 1]
    for ax in order:
        while True:
            nxt = list(q)
            nxt[ax] = q[ax] + ext[ax]
            rng = [range(q[i], q[i] + ext[i]) if i != ax else [q[ax] + ext[ax]] for i in range(3)]
            if all((a, b, c) in left for a in rng[0] for b in rng[1] for c in rng[2]):
                ext[ax] += 1
            else:
                break
    return ext


def merge_boxes(cells):
    """(骨, 材質) ごとに、セルを貪欲に直方体へまとめる。返り値: [(骨, 材質, (i0,j0,k0), (ex,ey,ez))]"""
    groups = {}
    for q, bm in cells.items():
        groups.setdefault(bm, set()).add(q)
    out = []
    for bm in sorted(groups):
        left = groups[bm]
        for q in sorted(left, key=lambda t: (t[1], t[2], t[0])):
            if q not in left:
                continue
            best = None
            for order in ((0, 2, 1), (0, 1, 2), (2, 0, 1), (1, 0, 2), (2, 1, 0), (1, 2, 0)):
                e = _grow(q, order, left)
                v = e[0] * e[1] * e[2]
                if best is None or v > best[0]:
                    best = (v, e)
            e = best[1]
            for a in range(e[0]):
                for b in range(e[1]):
                    for c in range(e[2]):
                        left.discard((q[0] + a, q[1] + b, q[2] + c))
            out.append((bm[0], bm[1], q, tuple(e)))
    return out


def drop_buried(boxes, cells, cover_ok):
    """6 面すべてが覆う側のセルに塞がれた箱を外す (外すと他の箱の判定が変わるので 1 つずつ)。
    cover_ok(骨) が偽の骨のセルは覆う側に数えない。"""
    occ = {q: bm for q, bm in cells.items() if cover_ok(bm[0])}
    removed = 0
    changed = True
    while changed:
        changed = False
        for idx, (bone, mat, q, e) in enumerate(boxes):
            if _is_buried(q, e, occ):
                for a in range(e[0]):
                    for b in range(e[1]):
                        for c in range(e[2]):
                            occ.pop((q[0] + a, q[1] + b, q[2] + c), None)
                            cells.pop((q[0] + a, q[1] + b, q[2] + c), None)
                boxes.pop(idx)
                removed += 1
                changed = True
                break
    return removed


def _is_buried(q, e, occ):
    i0, j0, k0 = q
    i1, j1, k1 = i0 + e[0], j0 + e[1], k0 + e[2]
    for j in range(j0, j1):
        for k in range(k0, k1):
            if (i0 - 1, j, k) not in occ or (i1, j, k) not in occ:
                return False
    for i in range(i0, i1):
        for k in range(k0, k1):
            if (i, j0 - 1, k) not in occ or (i, j1, k) not in occ:
                return False
    for i in range(i0, i1):
        for j in range(j0, j1):
            if (i, j, k0 - 1) not in occ or (i, j, k1) not in occ:
                return False
    return True


def buried_in_state(boxes, visible):
    """表示される骨だけで見た時の埋まりの数 (表示される箱のうち 6 面すべて塞がれたもの)。"""
    occ = {}
    for bone, mat, q, e in boxes:
        if bone in visible:
            for a in range(e[0]):
                for b in range(e[1]):
                    for c in range(e[2]):
                        occ[(q[0] + a, q[1] + b, q[2] + c)] = 1
    return sum(1 for bone, mat, q, e in boxes if bone in visible and _is_buried(q, e, occ))


class EModel(tm.Model):
    """tm.Model に、状態 (self.state) で変わる塗りと光を足したもの。"""

    def __init__(self, ident):
        super().__init__(ident)
        self.state = "base"
        self.paintfn = None   # (X,Y,Z,f,mat,state) -> 色 か None
        self.litfn = None     # (X,Y,Z,f,mat,state) -> bool

    def paint(self, X, Y, Z, f, mat):
        if self.paintfn:
            r = self.paintfn(X, Y, Z, f, mat, self.state)
            if r:
                return r
        return tm.style(mat, X, Y, Z, f) if mat in tm.RAMP and mat not in EMBER_RAMP else None


def build_model(sc: Sculpt, paintfn=None, litfn=None):
    """Sculpt -> EModel。見えない箱を外し、箱の数と外した数を返す。"""
    cells = dict(sc.cells)
    boxes = merge_boxes(cells)
    dropped = drop_buried(boxes, cells, lambda b: b not in sc.hideable)
    m = EModel(sc.ident)
    for b in sc.bones:
        m.bone(b["name"], b["pivot"], b.get("parent"))
    for bone, mat, q, e in boxes:
        m.box(bone, q[0] / 2, q[1] / 2, q[2] / 2, (q[0] + e[0]) / 2, (q[1] + e[1]) / 2, (q[2] + e[2]) / 2, mat)
    m.paintfn = paintfn
    m.litfn = litfn
    m.glowfn = (lambda X, Y, Z, f, mat: (mat in GLOW_ALWAYS) or bool(litfn and litfn(X, Y, Z, f, mat, m.state)))
    m._sculpt = sc
    m._boxes = boxes
    m._dropped = dropped
    return m


def export_states(m: EModel, folder: Path, states=("base",), root=True):
    """状態ごとにテクスチャと glowmask を書き出す。最初の状態が texture.png / glowmask.png。"""
    folder.mkdir(parents=True, exist_ok=True)
    out = {}
    geo = None
    for si, st in enumerate(states):
        m.state = st
        g, tex, gl = m.finish()
        if geo is None:
            geo = g
        suffix = "" if si == 0 else f"_{st}"
        Image.fromarray(tex, "RGBA").save(folder / f"texture{suffix}.png")
        Image.fromarray(gl, "RGBA").save(folder / f"glowmask{suffix}.png")
        out[st] = (tex, gl, int((gl[..., 3] > 0).sum()))
        assert out[st][2] > 0, f"光る画素がない: {m.ident} {st}"
    overlaps = vx.check_overlaps(geo)
    out_geo = tm.to_game(copy.deepcopy(geo)) if root else geo
    (folder / "geo.json").write_text(json.dumps(out_geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return dict(geo=out_geo, states=out, n=len(m.boxes), overlaps=overlaps, dropped=m._dropped)


# ---------------------------------------------------------------- 姿勢

def descendants(geo, name):
    bones = geo["minecraft:geometry"][0]["bones"]
    kids = {name}
    changed = True
    while changed:
        changed = False
        for b in bones:
            if b.get("parent") in kids and b["name"] not in kids:
                kids.add(b["name"])
                changed = True
    return kids


def posed_geo(geo, pos=None, hide=()):
    """位置のずらし (骨とその子孫を平行移動) と、骨を隠す (立方体を外す) を施した geo の写し。"""
    g = copy.deepcopy(geo)
    gb = g["minecraft:geometry"][0]["bones"]
    for name, d in (pos or {}).items():
        for b in gb:
            if b["name"] in descendants(g, name):
                b["pivot"] = [a + c for a, c in zip(b["pivot"], d)]
                for c in b.get("cubes", []):
                    c["origin"] = [a + q for a, q in zip(c["origin"], d)]
    hidden = set()
    for h in hide:
        hidden |= descendants(g, h)
    for b in gb:
        if b["name"] in hidden:
            b["cubes"] = []
    return g


def world_tf(geo, pose=None):
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    pose = pose or {}
    cache = {}

    def world(name):
        if name in cache:
            return cache[name]
        b = bones[name]
        piv = np.array(b.get("pivot", [0, 0, 0]), float)
        r = np.array(b.get("rotation", [0, 0, 0]), float) + np.array(pose.get(name, (0, 0, 0)), float)
        R = vx._rot(*r)
        M = (R, piv - R @ piv)
        if "parent" in b:
            PR, Pt = world(b["parent"])
            M = (PR @ M[0], PR @ M[1] + Pt)
        cache[name] = M
        return M
    return world, bones


def posed_overlaps(geo, pose=None, tol=0.03):
    """姿勢を付けた後の立方体どうしの重なり (分離軸の判定)。別の骨の立方体どうしだけ比べる。"""
    world, bones = world_tf(geo, pose)
    obb = []
    for name, b in bones.items():
        R, t = world(name)
        for i, c in enumerate(b.get("cubes", [])):
            o = np.array(c["origin"], float)
            s = np.array(c["size"], float)
            ctr = R @ (o + s / 2) + t
            obb.append((name, i, ctr, R, s / 2))
    hits = []
    n = len(obb)
    ctrs = np.array([o[2] for o in obb]) if obb else np.zeros((0, 3))
    rad = np.array([np.linalg.norm(o[4]) for o in obb]) if obb else np.zeros(0)
    for a in range(n):
        na, ia, ca, Ra, ha = obb[a]
        d = np.linalg.norm(ctrs[a + 1:] - ca, axis=1)
        for off in np.nonzero(d < rad[a] + rad[a + 1:])[0]:
            bi = a + 1 + off
            nb, ib, cb, Rb, hb = obb[bi]
            if nb == na:
                continue
            if _sat(ca, Ra, ha, cb, Rb, hb, tol):
                hits.append(f"{na}[{ia}] x {nb}[{ib}]")
    return hits


def _sat(ca, Ra, ha, cb, Rb, hb, tol):
    T = cb - ca
    axes = [Ra[:, i] for i in range(3)] + [Rb[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            v = np.cross(Ra[:, i], Rb[:, j])
            nv = np.linalg.norm(v)
            if nv > 1e-6:
                axes.append(v / nv)
    for L in axes:
        ra = sum(ha[i] * abs(np.dot(Ra[:, i], L)) for i in range(3))
        rb = sum(hb[i] * abs(np.dot(Rb[:, i], L)) for i in range(3))
        if abs(np.dot(T, L)) > ra + rb - tol:
            return False
    return True


# ---------------------------------------------------------------- 描画

def attach(mob_geo, weapon_geo, socket="socket_main"):
    """武器の geo を、体の geo の受け口の骨の子として付けた geo (武器の立方体だけ持つ) を作る。"""
    g = copy.deepcopy(mob_geo)
    gb = g["minecraft:geometry"][0]
    p = next(b["pivot"] for b in gb["bones"] if b["name"] == socket)
    for b in gb["bones"]:
        b["cubes"] = []
    wb = copy.deepcopy(weapon_geo)["minecraft:geometry"][0]["bones"]
    for b in wb:
        b["name"] = "wpn_" + b["name"]
        b["parent"] = socket if "parent" not in b else "wpn_" + b["parent"]
        b["pivot"] = [a + c for a, c in zip(b["pivot"], p)]
        for c in b.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
    gb["bones"] += wb
    gb["description"] = copy.deepcopy(weapon_geo["minecraft:geometry"][0]["description"])
    return g


def faces_of(geo, tex, gl, pose=None, pos=None, hide=(), weapon=None, weapon_pose=None, socket="socket_main"):
    g = posed_geo(geo, pos, hide)
    faces = vx.load_geo(g, tex, gl, pose=pose)
    if weapon is not None:
        wg, wt, wgl = weapon
        ag = attach(g, wg, socket)
        p = dict(pose or {})
        if weapon_pose:
            p[socket] = tuple(np.array(p.get(socket, (0, 0, 0)), float) + np.array(weapon_pose, float))
        faces += vx.load_geo(ag, wt, wgl, pose=p)
    return faces


def render(faces, yaw, pitch, size=VIEW):
    return vx.render(faces, yaw=-yaw, pitch=pitch, size=size).transpose(Image.FLIP_LEFT_RIGHT)


def lava_light(tex):
    """暗い溶岩の光の下の見た目 (光らない画素を赤く暗くする)。"""
    out = tex.astype(float)
    out[..., 0] *= 0.42
    out[..., 1] *= 0.24
    out[..., 2] *= 0.20
    return np.clip(out, 0, 255).astype(np.uint8)


def label(img, text):
    im = img.copy()
    d = ImageDraw.Draw(im)
    d.text((8, 6), text, font=vx.font(16), fill=(200, 200, 215))
    return im


def pair(a, b, size=VIEW):
    """2 枚 (それぞれ幅の半分の絵) を横に並べる。"""
    w, h = size
    out = Image.new("RGB", (w, h), (12, 12, 18))
    out.paste(a, (0, 0))
    out.paste(b, (w // 2, 0))
    return out
