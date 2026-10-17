"""中ボス「潮招き」(tide_beckoner) のラフ 3 案: 山のような大きな蟹。片方の大きなはさみが、招き潮の主題。

実行: リポジトリ直下で  PYTHONIOENCODING=utf-8 python tools/rough/tide_caller.py
出力:
  docs/review/tide_shell_roughs/tide_caller/<a|b|c>/{front34,side,top}.png  (案ごとの確認の絵)
  docs/review/tide_shell_roughs/tide_caller.png                            (3 案の比較シート)

座標 (ラフと同じ決まり): y が上、前は +Z、床は y=0、1 単位 = 1/16 ブロック。
  大きいはさみは -X 側 (ゲームの右。骨の名前 _r と同じ)。小さいはさみは +X 側。
  install_tide_caller.py がこの build() を使う。乱数なし。同じ入力は同じ箱になる。

作り方:
- 形は箱の組み合わせ (段・台・腕の節・指の節)。箱の端は 0.5 単位の格子に合わせる。
  角は十字の 2 箱で落として丸みを出す。段を重ねて山の形にする。
- 箱は先に置いたものを優先し、後の箱は引かれて重ならない (骨をまたぐ重なりは作れない)。
- 球や曲面を細かい箱で刻むと箱が増えるので、曲面は段で近似する (箱は 700 以下に収まる)。
- 光る画素 (glowmask) は、海硝子の材料だけ: はさみの先・目の瞳・案 C の灯。
"""
from __future__ import annotations

import copy
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import tide_mobs as tm  # noqa: E402

REVIEW = tm.REVIEW
OUT = REVIEW / "tide_caller"
SHEET = REVIEW / "tide_caller.png"
VIEWS = (("front34", 205, 18), ("side", 270, 8), ("top", 205, 80))
OPTIONS = ("a", "b", "c")
OPTION_NAME = {"a": "A 潮の大亀", "b": "B 珊瑚の山", "c": "C 潮見の塔"}

BONES = ["body", "claw_big", "claw_big_pincer", "claw_small", "eyes",
         "leg_r1", "leg_r2", "leg_r3", "leg_l1", "leg_l2", "leg_l3"]
PARENT = {b: "body" for b in BONES[1:]}
PARENT["claw_big_pincer"] = "claw_big"
# 骨の支点 (絶対座標)。回転・移動はこの点を中心に行う
PIVOT = {
    "body": (0, 12, 0),
    "claw_big": (-22, 26, 4),
    "claw_big_pincer": (-42, 26, 56),
    "claw_small": (20, 22, 6),
    "eyes": (0, 18, 20),
    "leg_r1": (-20, 10, 14), "leg_r2": (-20, 10, 0), "leg_r3": (-20, 10, -14),
    "leg_l1": (20, 10, 14), "leg_l2": (20, 10, 0), "leg_l3": (20, 10, -14),
}


# ---------------------------------------------------------------- 箱の組み立て (箱どうしは重ならない)

def _subtract(p, q):
    """箱 p から箱 q を引いた残り (重ならない箱の列)。p, q = (lo, hi)。"""
    (lo, hi), (qlo, qhi) = p, q
    ilo = [max(lo[a], qlo[a]) for a in range(3)]
    ihi = [min(hi[a], qhi[a]) for a in range(3)]
    if any(ihi[a] <= ilo[a] for a in range(3)):
        return [p]
    out = []
    cl, ch = list(lo), list(hi)
    for a in range(3):
        if cl[a] < ilo[a]:
            h2 = list(ch)
            h2[a] = ilo[a]
            out.append((list(cl), h2))
            cl[a] = ilo[a]
        if ch[a] > ihi[a]:
            l2 = list(cl)
            l2[a] = ihi[a]
            out.append((l2, list(ch)))
            ch[a] = ihi[a]
    return out


class Builder:
    """箱を置く。先に置いたものが優先し、後の箱は引かれて重ならない形になる。"""

    def __init__(self):
        self.items = []  # (骨, lo, hi, 材料)

    def box(self, bone, lo, hi, mat):
        pieces = [(list(lo), list(hi))]
        for _, l2, h2, _ in self.items:
            nxt = []
            for p in pieces:
                nxt += _subtract(p, (l2, h2))
            pieces = nxt
        for l, h in pieces:
            if all(h[a] - l[a] > 1e-9 for a in range(3)):
                self.items.append((bone, l, h, mat))

    def tiers(self, bone, y0, y1, a, c, mat, cz=0.0):
        """X=0、Z=cz を中心にした、角を落とした台 (半幅 a と c の 2 つの箱の十字)。a と c は (X の半幅, Z の半幅)。"""
        self.box(bone, (-a[0], y0, cz - a[1]), (a[0], y1, cz + a[1]), mat)
        self.box(bone, (-c[0], y0, cz - c[1]), (c[0], y1, cz + c[1]), mat)

    def limb(self, bone, a, b, mat):
        """節を 2 つ置く。a と b は (中心 x, y, z, 半幅 x, y, z)。"""
        for ax, ay, az, aw, ah, ad in (a, b):
            self.box(bone, (ax - aw, ay - ah, az - ad), (ax + aw, ay + ah, az + ad), mat)


def legs_boxes():
    """6 本の脚。付け根は甲羅の側面、膝は床近く、足先は床に届く。右 (-X) と左 (+X) は対称。
    各脚: 上の節 (付け根から膝へ)、下の節 (膝から足先へ)、足先の板。"""
    out = {}
    for side, s in (("r", -1), ("l", 1)):
        for k, (hz, kz, fz) in enumerate(((14, 20, 30), (0, 2, 5), (-14, -16, -26)), start=1):
            def xr(a0, a1):
                return (min(s * a0, s * a1), max(s * a0, s * a1))
            ux, lx, fx = xr(20, 40), xr(40, 46), xr(44, 48)
            zlo, zhi = min(kz, fz) - 2, max(kz, fz) + 2
            out[f"leg_{side}{k}"] = [
                ((ux[0], 6, hz - 2.5), (ux[1], 13, hz + 2.5), "crab_dk"),
                ((lx[0], 1, zlo - 0.5), (lx[1], 6, zhi + 0.5), "crab_dk"),
                ((fx[0], 0, fz - 2.5), (fx[1], 2.5, fz + 2.5), "crab_dk"),
            ]
    return out


# ---------------------------------------------------------------- 案

SPEC = {
    # 大きなはさみ (腕・掌・指・歯)、小さいはさみ
    "a": dict(arm="crab", palm="crab", finger="crab_dk", teeth="crab_dk", small="crab"),
    "b": dict(arm="crab_dk", palm="coral_o", finger="coral_p", teeth="coral_v", small="coral_o"),
    "c": dict(arm="ruin", palm="ruin", finger="crab", teeth="crab_dk", small="ruin"),
}
def _tier_list():
    """甲羅の段。高さ 3 単位ずつ、床 (y=10) から頂 (y=34) まで。半径は丸い山の輪郭に沿って狭まる。"""
    out = []
    for k in range(8):
        y0, y1 = 10 + 3 * k, 13 + 3 * k
        yc = (y0 + y1) / 2
        f = np.sqrt(max(0.0, 1.0 - ((yc - 10.0) / 26.0) ** 2))
        ax = max(3.0, round(30.0 * f * 2) / 2)      # 半幅 X
        az = max(3.0, round(24.0 * f * 2) / 2)      # 半幅 Z
        bx = max(2.5, round((ax - 5.0) * 2) / 2)    # 角を落とした内側の箱 (X は狭く、Z は長い)
        bz = round((az + 3.0) * 2) / 2
        out.append((y0, y1, (ax, az), (bx, bz)))
    return out


TIERS = _tier_list()
# 縁の藤壺 (箱の角の 2 点)
BARNACLES = [
    ((-33, 11, 4), (-30, 14, 7)), ((-32, 15, -10), (-29, 18, -7)),
    ((30, 11, -6), (33, 14, -3)), ((29, 12, 12), (32, 15, 15)),
    ((-14, 10, 25), (-11, 13, 28)), ((12, 10, -26), (15, 13, -23)), ((20, 10, 26), (23, 13, 29)),
]


def build_items(opt):
    """箱を先に置いたものが優先する順で作る。戻り値: Builder。"""
    sp = SPEC[opt]
    B = Builder()

    # --- 甲羅 (骨 body): 丸い山の段、口の牙、藤壺
    for y0, y1, t1, t2 in TIERS:
        B.tiers("body", y0, y1, t1, t2, "ruin")
    B.box("body", (-8, 10, 22), (8, 16, 28), "dark")            # 口の穴
    B.box("body", (-7, 12, 27), (-4, 18, 32), "bone")            # 牙 (左右)
    B.box("body", (4, 12, 27), (7, 18, 32), "bone")
    if opt in ("a", "c"):
        for lo, hi in BARNACLES:
            B.box("body", lo, hi, "barnacle")
    if opt == "a":
        # 背の石の板 (遺跡の棘)
        B.box("body", (-3, 30, -16), (3, 40, -6), "ruin")
        B.box("body", (-2, 34, -2), (2, 42, 6), "ruin")
        B.box("body", (-3, 26, 12), (3, 34, 18), "ruin")
    if opt == "b":
        # 背の珊瑚の山: 枝珊瑚の房と、脳珊瑚の丘
        B.box("body", (-24, 17, -18), (-16, 24, -10), "coral_p")
        B.box("body", (-20, 24, -14), (-16, 31, -10), "coral_p")
        B.box("body", (14, 17, -16), (24, 24, -6), "coral_o")
        B.box("body", (18, 24, -12), (22, 30, -8), "coral_o")
        B.box("body", (-8, 22, -22), (8, 30, -8), "brain")
        B.box("body", (-5, 30, -18), (5, 35, -10), "brain")
        B.box("body", (-16, 22, 4), (-8, 30, 12), "coral_v")
        B.box("body", (-14, 30, 6), (-10, 37, 10), "coral_v")
        B.box("body", (12, 19, 12), (18, 27, 18), "coral_o")
    if opt == "c":
        # 潮見の塔: 背に石の塔、頂に灯 (四方の壁と、中の灯)
        B.box("body", (-6, 30, -12), (6, 42, 0), "ruin")
        B.box("body", (-6, 42, -12), (-3.5, 46, 0), "ruin")
        B.box("body", (3.5, 42, -12), (6, 46, 0), "ruin")
        B.box("body", (-3.5, 42, -12), (3.5, 46, -9.5), "ruin")
        B.box("body", (-3.5, 42, -2.5), (3.5, 46, 0), "ruin")
        B.box("body", (-2.5, 42, -8.5), (2.5, 44.5, -3.5), "seaglass")

    # --- 大きなはさみ (骨 claw_big): 肩、上腕、前腕、掌、固定の指
    B.limb("claw_big", (-30, 22, 6, 9, 7, 9), (-40, 20, 18, 8, 7, 9), sp["arm"])
    B.limb("claw_big", (-42, 18, 32, 8, 6, 9), (-46, 16, 44, 7, 6, 7), sp["arm"])
    B.box("claw_big", (-56, 10, 42), (-30, 36, 66), sp["palm"])          # 掌 (大きな箱)
    B.box("claw_big", (-52, 6, 38), (-34, 40, 70), sp["palm"])           # 掌の角を落とした箱
    B.box("claw_big", (-50, 4, 62), (-36, 14, 74), sp["finger"])         # 固定の指
    B.box("claw_big", (-48, 4, 74), (-38, 12, 84), sp["finger"])
    B.box("claw_big", (-46, 4, 84), (-40, 10, 90), "seaglass")           # 固定の指の先 (光る)
    if opt in ("a", "b"):
        # 固定の指の内側の歯 (小さな箱の列)
        for z in (66, 70, 74, 78):
            B.box("claw_big", (-38, 10, z), (-36.5, 14, z + 2), sp["teeth"])
    # --- 動く指 (骨 claw_big_pincer): 掌の付け根のちょうつがいから。掌と重なる所は掌が持つ
    B.box("claw_big_pincer", (-52, 30, 58), (-38, 38, 72), sp["finger"])
    B.box("claw_big_pincer", (-50, 30, 72), (-40, 36, 84), sp["finger"])
    B.box("claw_big_pincer", (-48, 30, 84), (-42, 34, 90), "seaglass")   # 動く指の先 (光る)

    # --- 小さいはさみ (骨 claw_small)
    B.limb("claw_small", (23, 20, 4, 5, 4, 5), (27, 16, 14, 5, 5, 6), sp["small"])
    B.box("claw_small", (28, 9, 18), (42, 24, 32), sp["small"])
    B.box("claw_small", (31, 4, 32), (38, 12, 40), sp["small"])

    # --- 目 (骨 eyes): 2 本の柄と、前を向いた瞳 (光る)
    for sx in (-1, 1):
        cx = 8 * sx
        B.box("eyes", (cx - 1.5, 18, 18), (cx + 1.5, 38, 21), "crab")
        B.box("eyes", (cx - 3, 38, 18), (cx + 3, 44, 25), "dark")
        B.box("eyes", (cx - 2, 40, 25), (cx + 2, 42.5, 25.5), "seaglass")

    # --- 脚 (骨 leg_*)
    for name, boxes in legs_boxes().items():
        for lo, hi, mat in boxes:
            B.box(name, lo, hi, mat)
    return B


def prune_buried(items):
    """6 つの面すべてが他の箱に覆われた箱を外す (見えない箱)。外しても、周りの箱の見え方は変わらない。"""
    def covered(p, skip):
        for i, (_, l2, h2, _) in enumerate(items):
            if i != skip and all(l2[a] - 1e-6 < p[a] < h2[a] + 1e-6 for a in range(3)):
                return True
        return False

    keep = []
    for i, (bone, lo, hi, mat) in enumerate(items):
        all_hidden = True
        for ax in range(3):
            a1, a2 = [k for k in range(3) if k != ax]
            for plane in (lo[ax] - 0.25, hi[ax] + 0.25):
                n1 = int(round((hi[a1] - lo[a1]) * 2))
                n2 = int(round((hi[a2] - lo[a2]) * 2))
                for u in range(n1):
                    for v in range(n2):
                        p = [0.0, 0.0, 0.0]
                        p[ax] = plane
                        p[a1] = lo[a1] + 0.25 + 0.5 * u
                        p[a2] = lo[a2] + 0.25 + 0.5 * v
                        if not covered(p, i):
                            all_hidden = False
                            break
                    if not all_hidden:
                        break
                if not all_hidden:
                    break
            if not all_hidden:
                break
        if not all_hidden:
            keep.append((bone, lo, hi, mat))
    return keep


def build(opt):
    """案 opt の tm.Model を作る。戻り値: (Model, 情報 dict)。骨の親子と支点を入れる。"""
    assert opt in OPTIONS, opt
    B = build_items(opt)
    items = prune_buried(B.items)
    m = tm.Model(f"tide_beckoner_{opt}")
    for b in BONES:
        m.bone(b, PIVOT[b], PARENT.get(b))
    for bone, lo, hi, mat in items:
        m.box(bone, *lo, *hi, mat)
    lo = np.min([bx["b"][:3] for bx in m.boxes], 0)
    hi = np.max([bx["b"][3:] for bx in m.boxes], 0)
    info = dict(n=len(m.boxes), lo=tuple(float(v) for v in lo), hi=tuple(float(v) for v in hi))
    return m, info


def render_views(geo, tex, gl):
    """確認の絵 (front34 / side / top) を描き、画像の列を返す。"""
    g = tm.to_game(copy.deepcopy(geo))
    faces = vx.load_geo(g, tex, gl)
    return [vx.render(faces, yaw=-yaw, pitch=pitch, size=(440, 400)).transpose(tm.FLIP) for _, yaw, pitch in VIEWS]


def run():
    OUT.mkdir(parents=True, exist_ok=True)
    cols = []
    for opt in OPTIONS:
        m, info = build(opt)
        geo, tex, gl = m.finish()
        over = vx.check_overlaps(geo)
        assert not over, (opt, over[:5])
        assert gl[..., 3].any(), f"光る画素がない: {opt}"
        buried = tm.buried_count(m.boxes)
        d = OUT / opt
        d.mkdir(parents=True, exist_ok=True)
        imgs = render_views(geo, tex, gl)
        for (name, _, _), im in zip(VIEWS, imgs):
            im.save(d / f"{name}.png")
        print(f"{opt}: 箱 {info['n']}、重なり {'なし' if not over else over}、埋まり {buried}、範囲 lo {info['lo']} hi {info['hi']}")
        cols.append((f"{OPTION_NAME[opt]}  箱 {info['n']}", imgs))
    sheet = vx.sheet("潮招き (tide_beckoner) のラフ 3 案", cols,
                     note="左から A・B・C。上: 正面 3/4、中: 横、下: 真上。大きいはさみは -X 側 (ゲームの右)。")
    sheet.save(SHEET)


if __name__ == "__main__":
    run()
