"""岩鯨 (stone_whale) の採用案 B をゲームに入れる (第4回: 胸びれを左右同じにし、箱を減らして 3 段の LOD にする)。

使い方 (リポジトリ直下で): python tools/install_stone_whale.py
形の元: tools/rough/stone_whale_v2.py の案 B (格子に彫る関数をそのまま使う。胸びれ・尾びれ・岩の板・藤壺だけ変える)
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/stone_whale.geo.json         近景 (体の軸からカメラまで 64 ブロック未満)。0.5 単位の格子
  geo/entity/stone_whale_mid.geo.json     中景 (64〜160 ブロック)。2 単位 (1 ブロック) の格子
  geo/entity/stone_whale_far.geo.json     遠景 (160 ブロック以上)。4 単位 (2 ブロック) の格子
  textures/entity/stone_whale.png / _glowmask.png  3 つのモデルで共有する 1 枚のテクスチャ
  animations/entity/stone_whale.animation.json
確認用: docs/roughs/v5/stone_whale.png, docs/roughs/v5/whale_fins.png (胸びれを下から)

ゲームでの向き (GeckoLib 4.9.3 の仕様を確かめた結果):
- 箱の X は反転して描かれる (BakedModelFactory は x を -(origin.x + size.x) にする)。
  Blockbench の -X 側はゲームでは体の右、+X 側は左。錨石は +X 側 (ゲームでは左のわき腹) に置く。
- 面の uv を書かない面は、GeckoLib が四角形を作らない (buildQuad が null を返し、描画で飛ばす)。
  外から見えない面 (同じ骨の箱で覆われた面) は書かない。全部の面が隠れた箱は置かない。

箱を減らす方法:
- 箱は材料ごとではなく骨ごとにまとめる (格子を骨ごとに 6 通りの軸の順で貪欲に合わせ、一番少ないものを使う)。
  テクスチャは面ごとに描き直し、面の画素ごとに、その裏のセルの材料で塗る。だから材料の境目は箱の境目と関係ない。
  発光する刻文・目は材料として残るので、テクスチャと発光マスクに残る。
- 体の断面の幅は 1 単位で丸める (案 B は 0.5 単位)。上下の端の帯は 0.5 単位の高さのまま (横から見た輪郭は同じ段)。
- 岩の板: 1 単位の小さな浮き彫りをばらまく代わりに、長さ 3〜6 単位・帯 2〜3 本分の板にまとめる (数を減らす)。
- 藤壺 (0.5 単位の粒): 箱にしないで、腹の下の面のテクスチャの粒にする (同じ場所)。
- 外からつながっていない体の中の空洞は埋める (見えない)。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
import stone_whale_v2 as V  # noqa: E402
from voxel import Face, FACE_SHADE, load_geo, render, sheet  # noqa: E402

# 格子を X 方向に広げる (第5回)。案 B の格子は x -64〜+64 単位だが、胸びれの先は x ±71 まで出る。
# numpy の負の添字は反対側に回り込むので、右の胸びれ (-X) の先 7 単位が +X 側 (左の胸びれの先の後ろ) に
# 置かれ、左の胸びれの先は x 64 で切れていた (ゲームで右の胸びれの先の近くに浮いて見えた小片)。
# ci() と Model() は呼ぶときに V.OX / V.NX を読むので、ここで差し替える。OX は 8 の倍数のまま (LOD の箱の境目は同じ)。
V.OX, V.NX = 192, 384     # x -96〜+96 単位

RES = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/roughs/v5/stone_whale.png"
FIN_PREVIEW = ROOT / "docs/roughs/v5/whale_fins.png"

YC = V.YC
HULL_Q = 1.0            # 体の断面の幅の段 (単位)

# 骨。部品の番号は格子のキー (部品 * 16 + 材料) に使う
PARTS = {"head": 1, "jaw": 2, "body": 3, "tail": 4, "fin_left": 5, "fluke": 6, "ruins": 7, "spine": 8,
         "chain": 9, "fin_right": 10}
PART_NAME = {v: k for k, v in PARTS.items()}
MAT = V.MAT
MAT_NAME = V.MAT_NAME

# 胸びれ (左右で同じ形。根の位置だけ、わき腹の幅に合わせる)
FIN_Z0, FIN_Z1 = -58, -40     # 根の前後 (18 単位)
FIN_Y = YC - 6.0              # 根の厚みの中心の高さ
FIN_SPAN = 40                 # 外への長さ (x 方向、単位)。軸に沿った長さは約 44 単位 (全長 294 の約 15%)
FIN_SWEEP = 0.6               # 前縁が外へ 1 単位につき後ろへ下がる量
FIN_DROP = 0.3                # 外へ 1 単位につき下がる量
FIN_STEP = 2                  # 外への列の幅 (単位)
FIN_ROOT_T = 4.0              # 根の厚み
JAW_PIVOT = [0, 34, -66]      # 顎の後ろ上端
ARCH_BROKEN = 0.18            # アーチの前後の端の石を抜く割合

# 錨石 (+X、ゲームでは左のわき腹)。StoneWhaleEntity の定数と合わせる: 中心 x 21, y 39, z 61 (単位)
ANCHOR = (19.5, 22.5, YC - 3, YC + 1, 60, 62)

LODS = [  # 名前, 格子の倍率 (0.5 単位の何倍), 1 単位あたりの画素
    ("near", 1, 2.0),
    ("mid", 4, 1.0),
    ("far", 8, 0.5),
]


def key(part, mat):
    return PARTS[part] * 16 + MAT[mat]


# ---------------------------------------------------------------- 格子に彫る

class Whale(V.Model):
    """案 B の Model。部品の番号を PARTS に置き換え、体・胸びれ・岩の板・藤壺・錨石を差し替える"""

    def put(self, x0, x1, y0, y1, z0, z1, k, mode="empty"):
        self.raw(x0, x1, y0, y1, z0, z1, self.remap(k), mode)

    @staticmethod
    def remap(k):
        """stone_whale_v2 のキー (V.PART) を PARTS のキーにする。fins は後で左右に分ける"""
        if k == 0:
            return 0
        part = V.PART_NAME[k // 16]
        if part == "fins":
            part = "fin_left"
        return PARTS[part] * 16 + k % 16

    def raw(self, x0, x1, y0, y1, z0, z1, k, mode="empty"):
        """PARTS のキーのまま置く。格子の外にはみ出す箱は止める (負の添字は反対側に回り込むため)"""
        if x1 > x0 and y1 > y0 and z1 > z0:
            lo = (V.ci(x0), V.cj(y0), V.ck(z0))
            hi = (V.ci(x1), V.cj(y1), V.ck(z1))
            if min(lo) < 0 or any(h > n for h, n in zip(hi, self.K.shape)):
                raise ValueError("格子の外: %r" % ((x0, x1, y0, y1, z0, z1),))
        V.Model.put(self, x0, x1, y0, y1, z0, z1, k, mode)

    def body(self):
        """案 B の体。断面の幅を HULL_Q 単位で丸める (他は同じ)"""
        q = HULL_Q
        for zs in range(V.Z0, V.Z1):
            zc = zs + 0.5
            W, H = V.body_dims(zc)
            bottom = YC - H / 2
            rows = []
            for a, b in zip(V.row_edges(H)[:-1], V.row_edges(H)[1:]):
                y0, y1 = bottom + a, bottom + b
                t = ((y0 + y1) / 2 - YC) / (H / 2)
                p = 2.2 if t < 0 else 2.0
                hw = (W / 2) * max(0.0, 1 - abs(t) ** p) ** (1 / p)
                xl = -max(0.5, round(hw * V.LS / q) * q)
                xr = max(0.5, round(hw * V.RS / q) * q)
                rows.append((y0, y1, xl, xr, t))
            self.SL[zs] = dict(rows=rows, top=bottom + H, bottom=bottom, zc=zc)
            for (y0, y1, xl, xr, t) in rows:
                part = V.part_of(zc)
                if zc < -66 and y1 <= V.MOUTH + 1e-9:
                    part = "jaw"
                mat = "belly" if t < -0.55 else "stone"
                self.raw(xl, xr, y0, y1, zs, zs + 1, key(part, mat), "set")

    @staticmethod
    def hull_part(zs, y1):
        """z セル zs で上端 y1 までの体の帯の骨 (body() と同じ決め方: 頭の口の線より下は顎)"""
        zc = zs + 0.5
        if zc < -66 and y1 <= V.MOUTH + 1e-9:
            return "jaw"
        return V.part_of(zc)

    def throat(self):
        """案 B の喉の溝 (腹の下の細い隆起)。隆起は下の体の帯と同じ骨にする
        (案 B は z -66 より前も head にしていたので、顎の下に head の隆起が乗り、顎が開くと浮いた)"""
        for zs in range(-118, -14):
            r0 = self.SL[zs]["rows"][0]
            y0, xl, xr = r0[0], r0[2], r0[3]
            for px in (-8, -6, -4, -2, 0, 2, 4, 6, 8):
                if px >= xl + 0.5 and px + 1 <= xr - 0.5:
                    self.raw(px, px + 1, y0 - 0.5, y0, zs, zs + 1, key(self.hull_part(zs, y0), "belly"))

    def flank(self, side, zs, y):
        r = self.row_at(zs, y)
        if not r:
            return None
        return r[3] if side > 0 else r[2]

    def fin(self, side):
        """胸びれ: 根は体の側面に面で接し、外へ行くほど後ろへ流れて下がり、細く、段々に薄くなる。
        外へ FIN_STEP 単位ごとの列を置く (前縁・後縁の 2 割は 1 段薄い)。左右は同じ形"""
        part = "fin_left" if side > 0 else "fin_right"
        k = key(part, "fin")
        # 根の面: 根の範囲のわき腹で一番外の x
        ys = np.arange(FIN_Y - FIN_ROOT_T / 2, FIN_Y + FIN_ROOT_T / 2, 0.5) + 0.25
        fl = [self.flank(side, zs, y) for zs in range(FIN_Z0, FIN_Z1) for y in ys]
        fl = [f for f in fl if f is not None]
        root = max(fl) if side > 0 else min(fl)
        for i in range(0, FIN_SPAN, FIN_STEP):
            s = (i + FIN_STEP / 2) / FIN_SPAN
            chord = 3.0 + 15.0 * (1 - s ** 1.6)
            lead = FIN_Z0 + FIN_SWEEP * FIN_SPAN * s
            if s > 0.86:                       # 先を丸める
                chord *= math.sqrt(max(0.0, 1 - ((s - 0.86) / 0.14) ** 2)) * 0.6 + 0.4
            za, zb = V.qh(lead), V.qh(lead + chord)
            th = max(1.0, V.qh(FIN_ROOT_T * (1 - s) ** 0.8 + 0.5))
            yc = FIN_Y - FIN_DROP * (i + FIN_STEP / 2)
            edge = V.qh(0.2 * (zb - za))
            x0, x1 = (root + i, root + i + FIN_STEP) if side > 0 else (root - i - FIN_STEP, root - i)
            y0 = V.qh(yc - th / 2)
            self.raw(x0, x1, y0, y0 + th, za + edge, zb - edge, k)
            if th > 1.0:
                y0e = V.qh(yc - (th - 1) / 2)
                self.raw(x0, x1, y0e, y0e + th - 1, za, za + edge, k)
                self.raw(x0, x1, y0e, y0e + th - 1, zb - edge, zb, k)
            else:
                self.raw(x0, x1, y0, y0 + th, za, za + edge, k)
                self.raw(x0, x1, y0, y0 + th, zb - edge, zb, k)
        # 根: 根の面からわき腹までのすきまを埋める
        th0 = max(1.0, V.qh(FIN_ROOT_T + 0.5))
        y0 = V.qh(FIN_Y - th0 / 2)
        for zs in range(FIN_Z0, FIN_Z1):
            for yy in np.arange(y0, y0 + th0, 0.5):
                f = self.flank(side, zs, yy + 0.25)
                if f is None:
                    continue
                if side > 0 and f < root:
                    self.raw(f, root, yy, yy + 0.5, zs, zs + 1, k)
                if side < 0 and f > root:
                    self.raw(root, f, yy, yy + 0.5, zs, zs + 1, k)
        return root

    def arch(self, za0, za1, R, tag, glyph_p=0.0):
        """案 B のアーチ (同じ石・同じ刻文)。欠けは前後の端の面の石だけにする
        (中の石を抜くと、外から見える欠けは少ないのに箱が大きく増えるため)"""
        cy = int(round(self.SL[(za0 + za1) // 2]["top"]))
        for zs in range(za0, za1):
            end = zs in (za0, za1 - 1)
            for ix in range(-R - 1, R + 1):
                for iy in range(-R - 1, R + 1):
                    cx, cyy = ix + 0.5, iy + 0.5
                    r = math.hypot(cx, cyy)
                    ang = math.degrees(math.atan2(cyy, cx))
                    if not (R - 3 <= r <= R and -25 <= ang <= 205):
                        continue
                    if end and V.hz(ix, iy, zs, tag) < ARCH_BROKEN:
                        continue
                    glyph = glyph_p and V.hz(ix, iy, zs, tag + 7) < glyph_p
                    self.put(ix, ix + 1, cy + iy, cy + iy + 1, zs, zs + 1,
                             V.key("ruins", "glyph" if glyph else "ruin"))

    def ruins_extra(self):
        """案 B の塔・階段・壁・石片。塔の歯状の上端は縁だけに並べる。壁の崩れは上の 3 段だけにする"""
        top = int(round(self.SL[-18]["top"]))

        def tower(x, y, z):
            if y == top + 17:              # 歯: 縁の 1 つおき
                rim = x in (-5, 4) or z in (-24, -13)
                return V.key("ruins", "ruin") if rim and (x + z) % 2 else None
            if y == top + 8 and -2 <= x < 2:
                return V.key("ruins", "glyph")
            return V.key("ruins", "ruin")

        self.blocks(-5, 5, top - 1, top + 18, -24, -12, tower, broken=0.04, tag=11)
        t1 = int(round(self.SL[-30]["top"]))
        self.blocks(-5, 5, t1 - 1, t1 + 2, -36, -30, V.key("ruins", "ruin"), broken=0.06, tag=21)
        t2 = int(round(self.SL[-24]["top"]))
        self.blocks(-5, 5, t2 - 1, t2 + 4, -30, -24, V.key("ruins", "ruin"), broken=0.06, tag=22)
        wtop = int(round(self.top_min(10, 24))) + 6
        for x0, tag in ((-14, 31), (12, 32)):
            def wall(x, y, z, tag=tag):
                d = wtop - y              # 上から何段目か (1 が一番上)
                if d <= 3 and V.hz(x, y, z, tag) < (0.55, 0.35, 0.15)[d - 1]:
                    return None
                return V.key("ruins", "ruin")
            self.blocks(x0, x0 + 2, YC, wtop, 10, 24, wall)
        for k in range(14):
            x = int(-14 + 28 * V.hz(k, 3))
            z = int(-100 + 200 * V.hz(k, 4))
            if z not in self.SL:
                continue
            # 石片は x の列での背の高さに置く (第4回までは背の中央の高さに置いたので、x が外寄りの石片は
            # 丸い背から浮いていた)
            tops = [r[1] for r in self.SL[z]["rows"] if r[2] <= x and x + 1 <= r[3]]
            if not tops:
                continue
            top_z = int(math.floor(max(tops)))
            self.put(x, x + 1, top_z - 1, top_z + 1 + (k % 2), z, z + 1, V.key("ruins", "ruin"))

    def plates(self, prob):
        """岩の板: わき腹の中ほどの帯に、長さ 3〜6 単位・帯 2〜3 本分の板 (1 単位の浮き彫り)。
        板の外の面は板の中で一番外の帯から 1 単位。内側はわき腹まで埋める (すきまを作らない)"""
        n = 0
        for side in (-1, 1):
            zs = -128
            while zs < 130:
                L = 3 + int(V.hz(zs, side, 7) * 4)
                rows = self.SL[zs]["rows"]
                cand = [ri for ri, r in enumerate(rows) if -0.5 < r[4] < 0.75 and r[1] - r[0] >= 1]
                if cand and V.hz(zs, side, 8) < prob:
                    ri0 = cand[int(V.hz(zs, side, 9) * len(cand))]
                    nr = 2 + int(V.hz(zs, side, 10) * 2)
                    rr = [r for r in rows[ri0:ri0 + nr] if r[1] - r[0] >= 1]
                    y0, y1 = rr[0][0], rr[-1][1]
                    zr = [z for z in range(zs, zs + L) if z in self.SL]
                    outs = [self.flank(side, z, y + 0.5) for z in zr for y in np.arange(y0, y1, 1.0)]
                    outs = [o for o in outs if o is not None]
                    if outs:
                        face = (max(outs) + 1) if side > 0 else (min(outs) - 1)
                        for z in zr:
                            for y in np.arange(y0, y1, 0.5):
                                f = self.flank(side, z, y + 0.25)
                                if f is None:
                                    continue
                                # 板のセルは裏の体の帯と同じ骨 (頭・顎・体・尾の境をまたぐ板が別の骨に浮かないように)
                                k = key(self.hull_part(z, y + 0.5), "plate")
                                if side > 0:
                                    self.raw(f, face, y, y + 0.5, z, z + 1, k)
                                else:
                                    self.raw(face, f, y, y + 0.5, z, z + 1, k)
                        n += 1
                    zs += L + 1
                else:
                    zs += 2
        return n

    def barnacles(self):
        """藤壺: 案 B と同じ場所の腹の下のセルを、殻の材料 (crust) に替える (テクスチャの粒になる)"""
        for zs in range(-120, 130):
            r = self.SL[zs]["rows"][0]
            y0, xl, xr = r[0], r[2], r[3]
            for xi in range(int(math.floor(xl)) + 1, int(math.ceil(xr)) - 1):
                for half in (0, 1):
                    if V.hz(xi, zs, half, 5) < 0.10:
                        z = zs + 0.5 * half
                        part = V.part_of(zs + 0.5)
                        if zs + 0.5 < -66:
                            part = "jaw"
                        self.raw(xi, xi + 0.5, y0, y0 + 0.5, z, z + 0.5, key(part, "crust"), "replace")

    def chain(self):
        """錨石 (+X のわき腹、z 60〜62) と、垂れる鎖の短い 3 節。わき腹とのすきまは鉄で埋める"""
        x0, x1, y0, y1, z0, z1 = ANCHOR
        k = key("chain", "iron")
        self.raw(x0, x1, y0, y1, z0, z1, k)
        for zs in range(z0, z1):
            for yy in np.arange(y0, y1, 0.5):
                f = self.flank(1, zs, yy + 0.25)
                if f is not None and f < x0:
                    self.raw(f, x0, yy, yy + 0.5, zs, zs + 1, k)
        # 鎖の 3 節 (y 37〜28)。節どうし・錨石と面で接する輪にする (第4回までは 1 単位ずつ離れて浮いていた)。
        # 偶数の節は x-y 面の輪 (x 20.5〜22.5)、奇数の節は z-y 面の輪 (z 59.5〜61.5)。どちらも穴は 1 単位
        cx0, cz0 = x0 + 1.5, z0                 # 鎖の芯 (x 21〜22, z 60〜61)
        for i in range(3):
            ytop = y0 - 3 * i
            yb = ytop - 3
            if i % 2 == 0:
                self.raw(cx0 - 0.5, cx0 + 1.5, yb, ytop, cz0, cz0 + 1, k)
                self.raw(cx0, cx0 + 1, yb + 0.5, ytop - 0.5, cz0, cz0 + 1, 0, "set")
            else:
                self.raw(cx0, cx0 + 1, yb, ytop, cz0 - 0.5, cz0 + 1.5, k)
                self.raw(cx0, cx0 + 1, yb + 0.5, ytop - 0.5, cz0, cz0 + 1, 0, "set")


def build():
    o = dict(V.OPTS["b"])
    o["lift"] = 0                       # 尾びれは左右同じ高さ (案 B は左が 4 単位上がっていた)
    M = Whale(o)
    M.body()
    M.mouth()
    M.baleen()
    M.eyes()
    M.throat()
    root_l = M.fin(1)
    root_r = M.fin(-1)
    M.fluke(o["lift"])
    M.spine()
    for (a, b, R) in o["arches"]:
        M.arch(a, b, R, tag=a * 3 + 1, glyph_p=0.2)
    M.pillar(-46, -42, -2, 2, 26, tag=41)
    M.ruins_extra()
    nplates = M.plates(0.42)
    M.barnacles()
    M.chain()
    fill_enclosed(M.K)
    M.island_log = connect_islands(M.K, M.K, 1)
    return M, root_l, root_r, nplates


def fill_enclosed(K):
    """外とつながっていない空洞 (体の中) を、まわりと同じ部品のセルで埋める"""
    lab, n = ndimage.label(K == 0)
    outside = lab[0, 0, 0]
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        if i == outside or sl is None:
            continue
        sl2 = tuple(slice(max(0, s.start - 1), s.stop + 1) for s in sl)
        comp = lab[sl2] == i
        ring = ndimage.binary_dilation(comp) & ~comp
        keys = K[sl2][ring]
        parts = set((keys // 16).tolist())
        if len(parts) == 1:
            vals, cnt = np.unique(keys, return_counts=True)
            K[sl2][comp] = vals[np.argmax(cnt)]


# ---------------------------------------------------------------- LOD の格子

def downsample(K, f):
    """f x f x f のセルを 1 つにする。部品は多数決、材料は光る材料を優先して多数決。
    セルを埋めるかは部品ごとの割合 (薄い部品は低い割合でも残す)"""
    if f == 1:
        return K.copy()
    sh = [(-(-n // f)) * f for n in K.shape]
    P = np.zeros(sh, K.dtype)
    P[:K.shape[0], :K.shape[1], :K.shape[2]] = K
    a, b, c = sh[0] // f, sh[1] // f, sh[2] // f
    blk = P.reshape(a, f, b, f, c, f).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, f ** 3)
    nz = blk != 0
    filled = nz.sum(-1)
    occ = np.argwhere(filled > 0)
    out = np.zeros((a, b, c), K.dtype)
    thin = {PARTS["fin_left"], PARTS["fin_right"], PARTS["fluke"], PARTS["spine"]}
    thr_thick = 0.5 if f <= 4 else 0.35
    thr_thin = 0.25 if f <= 4 else 0.12
    for (i, j, k) in occ:
        cells = blk[i, j, k]
        cells = cells[cells != 0]
        parts, pc = np.unique(cells // 16, return_counts=True)
        p = int(parts[np.argmax(pc)])
        if p == PARTS["chain"] and f > 2:
            continue
        thr = thr_thin if p in thin else thr_thick
        if len(cells) < thr * f ** 3:
            continue
        mats = cells[cells // 16 == p] % 16
        # 光る材料: 1 単位の格子では 2 セルあれば残す。粗い格子では、刻文が 35% 以上、目が一定のセル数以上のときだけ
        # (少しの刻文で粗い箱がまるごと光らないように)
        eye = mats == MAT["eye"]
        gly = np.isin(mats, [MAT["glyph"], MAT["cyan"]])
        if f == 2:
            pick = mats[eye | gly] if (eye | gly).sum() >= 2 else None
        elif eye.sum() >= (8 if f <= 4 else 12):
            pick = mats[eye]
        elif gly.sum() >= 0.35 * len(mats):
            pick = mats[gly]
        else:
            pick = None
        if pick is None:
            rest = mats[~(eye | gly)]
            pick = rest if len(rest) else mats
        vals, cnt = np.unique(pick, return_counts=True)
        out[i, j, k] = p * 16 + int(vals[np.argmax(cnt)])
    return out


# ---------------------------------------------------------------- 離れ島 (骨ごとのつながり)

# 一緒に動く骨の組。body に対して動かない ruins / spine は body と 1 つの組 (遺跡や椎骨は体の上に立つ)。
# chain は 1 つの塊 (錨石と 3 節) であればよい: 錨石は尾の付け根 (z 60〜62) のわき腹に接するが、
# StoneWhaleEntity の鎖の位置が chain の骨 (体に固定) を前提にしているので tail には移さない。
RIGID = {"body": ("body", "ruins", "spine"), "chain": ("chain",), "head": ("head",), "jaw": ("jaw",),
         "tail": ("tail",), "fluke": ("fluke",), "fin_left": ("fin_left",), "fin_right": ("fin_right",)}


def islands(G):
    """組ごとに、一番大きい塊とつながらない塊 (面で接するものだけをつながりとする) を返す。
    [(組, セル数, 格子の lo, hi, 接している別の組の部品)]"""
    out = []
    P = G // 16
    for g, names in RIGID.items():
        m = np.isin(P, [PARTS[n] for n in names])
        if not m.any():
            continue
        lab, n = ndimage.label(m)
        if n <= 1:
            continue
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        big = int(np.argmax(sizes)) + 1
        for i, sl in enumerate(ndimage.find_objects(lab), start=1):
            if i == big:
                continue
            sl2 = tuple(slice(max(0, s.start - 1), s.stop + 1) for s in sl)
            comp = lab[sl2] == i
            ring = ndimage.binary_dilation(comp) & ~comp & ~m[sl2]
            touch = sorted(set(PART_NAME[int(v)] for v in np.unique(P[sl2][ring]) if v))
            lo = [s.start for s in sl]
            hi = [s.stop for s in sl]
            out.append((g, int(sizes[i - 1]), lo, hi, touch))
    return out


def connect_islands(G, K, f):
    """組の一番大きい塊とつながらない塊を直す。何をしたかの一覧を返す。
    1. 粗い格子 (f > 1) では、元の格子でその組のセルがある空きセルを通して 3 セルまででつなぐ。
    2. つなげず、別の組の骨に接している塊は、一番多く接している骨に移す (その骨と一緒に動く。
       例: 頭の上の椎骨や遺跡は head に乗っているので head に移す)。材料はそのまま。
    3. 何にも接していない塊 (空中に浮いた石) は消す。"""
    log = []
    if f > 1:
        sh = [(-(-n // f)) * f for n in K.shape]
        Pf = np.zeros(sh, K.dtype)
        Pf[:K.shape[0], :K.shape[1], :K.shape[2]] = K // 16
        a, b, c = sh[0] // f, sh[1] // f, sh[2] // f
        blk = Pf.reshape(a, f, b, f, c, f).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, f ** 3)
    for g, names in RIGID.items():
        ids = [PARTS[n] for n in names]
        for _ in range(4):
            P = G // 16
            m = np.isin(P, ids)
            lab, n = ndimage.label(m)
            if n <= 1:
                break
            sizes = ndimage.sum(m, lab, range(1, n + 1))
            big = int(np.argmax(sizes)) + 1
            bridged = False
            for i, sl in enumerate(ndimage.find_objects(lab), start=1):
                if i == big or sl is None:
                    continue
                size = int(sizes[i - 1])
                if f > 1:
                    cand = (G == 0) & np.isin(blk, ids).any(-1)
                    path = _bridge(lab == i, lab == big, cand, limit=3)
                    if path is not None:
                        vv, cc = np.unique(G[lab == i], return_counts=True)
                        for p in path:
                            G[p] = vv[np.argmax(cc)]
                        log.append("%s: %d セルの塊を %d セルでつないだ" % (g, size, len(path)))
                        bridged = True
                        continue
                sl2 = tuple(slice(max(0, s_.start - 1), s_.stop + 1) for s_ in sl)
                comp = lab[sl2] == i
                ring = ndimage.binary_dilation(comp) & ~m[sl2]
                touch = P[sl2][ring]
                touch = touch[touch != 0]
                sub = G[sl2]
                if len(touch):
                    vv, cc = np.unique(touch, return_counts=True)
                    to = int(vv[np.argmax(cc)])
                    sub[comp] = to * 16 + sub[comp] % 16
                    log.append("%s: %d セルの塊を %s に移した" % (g, size, PART_NAME[to]))
                else:
                    sub[comp] = 0
                    log.append("%s: %d セルの浮いた塊を消した" % (g, size))
            if not bridged:
                break
    return log


def _bridge(src, dst, cand, limit):
    """src から cand のセルだけを通って dst に届く一番短い道 (limit セルまで)。なければ None"""
    from collections import deque
    start = [tuple(p) for p in np.argwhere(src)]
    prev = {p: None for p in start}
    q = deque((p, 0) for p in start)
    shape = src.shape
    while q:
        p, d = q.popleft()
        for ax in range(3):
            for dd in (-1, 1):
                n = list(p)
                n[ax] += dd
                if not (0 <= n[ax] < shape[ax]):
                    continue
                n = tuple(n)
                if n in prev:
                    continue
                if dst[n]:
                    out = []
                    while p is not None and not src[p]:
                        out.append(p)
                        p = prev[p]
                    return out
                if cand[n] and d < limit:
                    prev[n] = p
                    q.append((n, d + 1))
    return None


# ---------------------------------------------------------------- 箱にまとめる

def _runs(K):
    """K (a, b, c) を c の連続 -> b -> a の順に、同じキーで合わせる"""
    A, B, C = K.shape
    pad = np.zeros((A, B, C + 2), K.dtype)
    pad[..., 1:-1] = K
    cur, prv, nxt = pad[..., 1:-1], pad[..., :-2], pad[..., 2:]
    occ = cur != 0
    st = np.argwhere(occ & (prv != cur))
    en = np.argwhere(occ & (nxt != cur))
    kk = cur[st[:, 0], st[:, 1], st[:, 2]].astype(np.int64)
    a, b, c0, c1 = st[:, 0], st[:, 1], st[:, 2], en[:, 2] + 1
    o = np.lexsort((b, a, c1, c0, kk))
    kk, a, b, c0, c1 = kk[o], a[o], b[o], c0[o], c1[o]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (c0[1:] == c0[:-1]) & (c1[1:] == c1[:-1]) & (a[1:] == a[:-1]) & (b[1:] == b[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    kk, a, c0, c1, b0, b1 = kk[gs], a[gs], c0[gs], c1[gs], b[gs], b[ge] + 1
    o = np.lexsort((a, b1, b0, c1, c0, kk))
    kk, a, c0, c1, b0, b1 = kk[o], a[o], c0[o], c1[o], b0[o], b1[o]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (c0[1:] == c0[:-1]) & (c1[1:] == c1[:-1]) & (b0[1:] == b0[:-1]) & \
        (b1[1:] == b1[:-1]) & (a[1:] == a[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    return np.stack([kk[gs], a[gs], a[ge] + 1, b0[gs], b1[gs], c0[gs], c1[gs]], 1)


def mesh_best(K):
    """6 通りの軸の順で合わせて、箱の一番少ないものを返す。[キー, x0, x1, y0, y1, z0, z1]"""
    from itertools import permutations
    best = None
    for perm in permutations(range(3)):
        r = _runs(np.ascontiguousarray(np.transpose(K, perm)))
        out = np.zeros_like(r)
        out[:, 0] = r[:, 0]
        for i, ax in enumerate(perm):
            out[:, 1 + 2 * ax] = r[:, 1 + 2 * i]
            out[:, 2 + 2 * ax] = r[:, 2 + 2 * i]
        if best is None or len(out) < len(best):
            best = out
    return best


def mesh_parts(K):
    """骨ごとに合わせる。岩の板は体と別の組にする (体の帯の合わせを崩さない)"""
    P = K // 16
    G = np.where(K % 16 == MAT["plate"], 1, 0)
    boxes = []
    for p in sorted(set(np.unique(P).tolist()) - {0}):
        m = P == p
        idx = np.argwhere(m)
        lo, hi = idx.min(0), idx.max(0) + 1
        sl = tuple(slice(l, h) for l, h in zip(lo, hi))
        sub = np.where(m[sl], p * 4 + G[sl] + 1, 0).astype(np.int16)
        b = mesh_best(sub)
        b[:, 0] = p
        b[:, 1:3] += lo[0]
        b[:, 3:5] += lo[1]
        b[:, 5:7] += lo[2]
        boxes.append(b)
    return np.concatenate(boxes)


# ---------------------------------------------------------------- 面とテクスチャ

FACE_AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def face_exposed(K, box):
    """面の外側のセルが空か、別の骨なら、その面は見える (骨は動くので、継ぎ目の面は残す)"""
    p, x0, x1, y0, y1, z0, z1 = (int(v) for v in box)
    lo, hi = [x0, y0, z0], [x1, y1, z1]
    res = {}
    for f, (ax, d) in FACE_AXIS.items():
        a, b = list(lo), list(hi)
        if d < 0:
            a[ax], b[ax] = lo[ax] - 1, lo[ax]
        else:
            a[ax], b[ax] = hi[ax], hi[ax] + 1
        if a[ax] < 0 or b[ax] > K.shape[ax]:
            res[f] = True
            continue
        nb = K[a[0]:b[0], a[1]:b[1], a[2]:b[2]] // 16
        res[f] = bool(np.any(nb != p))
    return res


def face_size(cells_size, fname, ppc):
    a, b, d = cells_size
    w, h = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}[fname]
    return max(1, int(round(w * ppc))), max(1, int(round(h * ppc)))


def pack(items, S):
    """棚詰め。items = [(id, w, h)]"""
    pos, x, y, shelf = {}, 0, 0, 0
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w > S:
            x, y, shelf = 0, y + shelf, 0
        if y + h > S:
            return None
        pos[k] = (x, y)
        x += w
        shelf = max(shelf, h)
    return pos


def paint_face(K, f, unit, o, s, fname, w, h):
    """面の画素ごとに、裏のセル (面から内へ半セル) の材料で塗る。模様は stone_whale_v2 の paint と同じ"""
    P = V.world_grid(o, s, fname, w, h)
    ax, d = FACE_AXIS[fname]
    Q = P.copy()
    Q[..., ax] -= d * unit * 0.5
    gi = np.floor((Q[..., 0] * 2 + V.OX) / f).astype(int)
    gj = np.floor((Q[..., 1] * 2 + V.OY) / f).astype(int)
    gk = np.floor((Q[..., 2] * 2 + V.OZ) / f).astype(int)
    gi = np.clip(gi, 0, K.shape[0] - 1)
    gj = np.clip(gj, 0, K.shape[1] - 1)
    gk = np.clip(gk, 0, K.shape[2] - 1)
    mats = K[gi, gj, gk] % 16
    tex = np.zeros((h, w, 4), np.uint8)
    glow = np.zeros((h, w, 4), np.uint8)
    kind = "tb" if fname in ("up", "down") else "side"
    for m in np.unique(mats):
        name = MAT_NAME.get(int(m), "stone")
        tt = np.zeros((h, w, 4), np.uint8)
        gg = np.zeros((h, w, 4), np.uint8)
        V.paint(tt, gg, 0, 0, w, h, name, kind, P, False)
        sel = mats == m
        tex[sel] = tt[sel]
        glow[sel] = gg[sel]
    return tex, glow


def build_lod(K, f, ppu):
    """1 つの LOD の箱と面 (テクスチャはまだ置かない)"""
    unit = f * 0.5
    boxes = mesh_parts(K)
    cubes = []
    for bx in boxes:
        ex = face_exposed(K, bx)
        if not any(ex.values()):
            continue                    # 全部の面が隠れた箱は置かない
        p, x0, x1, y0, y1, z0, z1 = (int(v) for v in bx)
        cells = (x1 - x0, y1 - y0, z1 - z0)
        o = ((x0 * f - V.OX) / 2, (y0 * f - V.OY) / 2, (z0 * f - V.OZ) / 2)
        s = (cells[0] * unit, cells[1] * unit, cells[2] * unit)
        faces = {}
        for fname, vis in ex.items():
            if vis:
                faces[fname] = face_size(cells, fname, unit * ppu)
        cubes.append(dict(part=PART_NAME[p], o=o, s=s, faces=faces))
    return cubes, len(boxes)


def bones(pivots):
    out = []
    for name, parent in [("body", None), ("head", "body"), ("jaw", "head"), ("tail", "body"),
                         ("fin_left", "body"), ("fin_right", "body"), ("fluke", "tail"),
                         ("ruins", "body"), ("spine", "body"), ("chain", "body")]:
        b = {"name": name, "pivot": pivots[name]}
        if parent:
            b["parent"] = parent
        out.append(b)
    return out


def make_geo(cubes, uvs, S, pivots):
    bl = bones(pivots)
    for b in bl:
        b["cubes"] = []
    idx = {b["name"]: b for b in bl}
    for c, uv in zip(cubes, uvs):
        idx[c["part"]]["cubes"].append({"origin": [round(v, 3) for v in c["o"]],
                                        "size": [round(v, 3) for v in c["s"]], "uv": uv})
    for b in bl:
        if not b["cubes"]:
            b.pop("cubes")
    return {"format_version": "1.12.0",
            "minecraft:geometry": [{"description": {
                "identifier": "geometry.stone_whale", "texture_width": S, "texture_height": S,
                "visible_bounds_width": 160, "visible_bounds_height": 80, "visible_bounds_offset": [0, 20, 0]},
                "bones": bl}]}


# ---------------------------------------------------------------- アニメーション (角度は度。sin は度で受け取る)

def anim():
    t = "query.anim_time"
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.stone_whale.swim": {
                "loop": True,
                "animation_length": 8.0,
                "bones": {
                    "head": {"rotation": [f"math.sin({t} * 90 - 45) * 1.5", f"math.sin({t} * 90) * 2.5", "0"]},
                    "jaw": {"rotation": [f"(0.5 + 0.5 * math.sin({t} * 90 - 60)) * 8", "0", "0"]},
                    "tail": {"rotation": ["0", f"math.sin({t} * 90 - 90) * 5", "0"]},
                    "fluke": {"rotation": ["0", "0", f"math.sin({t} * 90 - 135) * 8"]},
                    # 胸びれ: 左右で逆位相に上下する (Z 軸)。周期 2 秒。pivot は根
                    "fin_left": {"rotation": ["0", "0", f"math.sin({t} * 180) * 6"]},
                    "fin_right": {"rotation": ["0", "0", f"-math.sin({t} * 180) * 6"]},
                },
            }
        },
    }


# ---------------------------------------------------------------- 確認用の絵

def mirror_faces(faces):
    """ゲームの向きに合わせて X を反転する (GeckoLib と同じ)。
    反転すると面の表裏が入れ替わるので、横の辺の向きを逆にして、画素も横に反転する (同じ点に同じ画素)"""
    m = np.array([-1, 1, 1])
    out = []
    for f in faces:
        g = None if f.glow is None else f.glow[:, ::-1]
        out.append(Face((f.origin + f.eu) * m, -f.eu * m, f.ev * m, f.tex[:, ::-1], g, f.shade))
    return out


def marker(center, size, color):
    """小さな色の箱 (印)。ゲームの向きの座標で置く"""
    c = np.array(center, float)
    h = np.array(size, float) / 2
    x0, y0, z0 = c - h
    x1, y1, z1 = c + h
    sx, sy, sz = 2 * h
    corners = {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }
    tex = np.array(color, np.uint8).reshape(1, 1, 4)
    return [Face(np.array(co, float), np.array(eu, float), np.array(ev, float), tex, None, FACE_SHADE[n])
            for n, (co, eu, ev) in corners.items()]


def quads(geo):
    return sum(len(c["uv"]) for b in geo["minecraft:geometry"][0]["bones"] for c in b.get("cubes", []))


def cube_count(geo):
    return sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])


def check_overlap(cubes):
    """箱を 0.5 単位の格子に戻して、2 個以上入るセルを数える"""
    lo = np.min([c["o"] for c in cubes], 0)
    hi = np.max([np.add(c["o"], c["s"]) for c in cubes], 0)
    off = np.floor(lo * 2).astype(int)
    grid = np.zeros(np.ceil(hi * 2).astype(int) - off + 1, np.uint8)
    for c in cubes:
        a = np.round(np.array(c["o"]) * 2).astype(int) - off
        b = a + np.round(np.array(c["s"]) * 2).astype(int)
        grid[a[0]:b[0], a[1]:b[1], a[2]:b[2]] += 1
    return int((grid > 1).sum())


def main():
    M, root_l, root_r, nplates = build()
    K = M.K
    print("fin roots", root_l, root_r, "plates", nplates)
    ys = np.arange(FIN_Y - FIN_ROOT_T / 2, FIN_Y + FIN_ROOT_T / 2, 0.5) + 0.25
    piv_y = FIN_Y
    pivots = {"body": [0, YC, 0], "head": [0, YC, -60], "jaw": JAW_PIVOT, "tail": [0, YC, 60],
              "fin_left": [root_l, piv_y, (FIN_Z0 + FIN_Z1) / 2], "fin_right": [root_r, piv_y, (FIN_Z0 + FIN_Z1) / 2],
              "fluke": [0, YC, 138], "ruins": [0, YC, 0], "spine": [0, YC, 0], "chain": [0, YC, 60]}
    del ys

    lods = {}
    print("near islands:", *M.island_log, sep="\n  ")
    island_logs = {"near": M.island_log}
    for name, f, ppu in LODS:
        G = downsample(K, f)
        if f > 1:
            island_logs[name] = connect_islands(G, K, f)
            print(name, "islands:", *island_logs[name], sep="\n  ")
        left = islands(G)
        assert not left, (name, left)
        cubes, raw = build_lod(G, f, ppu)
        lods[name] = (G, f, ppu, cubes)
        print(name, "boxes", raw, "kept", len(cubes), "quads", sum(len(c["faces"]) for c in cubes), flush=True)

    # 1 枚のテクスチャに 3 つの LOD の面を詰める
    items = []
    for name, (G, f, ppu, cubes) in lods.items():
        for ci, c in enumerate(cubes):
            for fname, (w, h) in c["faces"].items():
                items.append(((name, ci, fname), w, h))
    for S in (1024, 2048, 4096):
        pos = pack(items, S)
        if pos:
            break
    print("atlas", S, "pixels used", sum(w * h for _, w, h in items))
    tex = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S, 4), np.uint8)
    geos = {}
    for name, (G, f, ppu, cubes) in lods.items():
        uvs = []
        for ci, c in enumerate(cubes):
            uv = {}
            for fname, (w, h) in c["faces"].items():
                u, v = pos[(name, ci, fname)]
                t, g = paint_face(G, f, f * 0.5, c["o"], c["s"], fname, w, h)
                tex[v:v + h, u:u + w] = t
                glow[v:v + h, u:u + w] = g
                uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            uvs.append(uv)
        geos[name] = make_geo(cubes, uvs, S, pivots)
        print(name, "overlap cells", check_overlap(cubes))
    print("glow pixels", int((glow[..., 3] > 0).sum()))

    geo_dir = RES / "geo/entity"
    tex_dir = RES / "textures/entity"
    anim_dir = RES / "animations/entity"
    files = {"near": "stone_whale.geo.json", "mid": "stone_whale_mid.geo.json", "far": "stone_whale_far.geo.json"}
    for name, fn in files.items():
        (geo_dir / fn).write_text(json.dumps(geos[name], ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tex_dir / "stone_whale.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(tex_dir / "stone_whale_glowmask.png", optimize=True)
    (anim_dir / "stone_whale.animation.json").write_text(json.dumps(anim(), ensure_ascii=False, indent=1) + "\n",
                                                         encoding="utf-8")
    for name in files:
        print(name, "cubes", cube_count(geos[name]), "quads", quads(geos[name]))

    preview(geos, tex, glow)


def gecko_pose(rot):
    """アニメーションの回転 (度、{骨: (rx, ry, rz)}) を、load_geo の pose (Blockbench の向き) にする。
    GeckoLib 4.9.3 は X を反転した空間で、pivot の x も反転し、回転の X と Y の符号を反転して回す
    (Rz(rz) Ry(-ry) Rx(-rx))。これを X の反転 M で Blockbench の向きに戻すと M R M = Rz(-rz) Ry(ry) Rx(-rx)
    なので、pose は (-rx, ry, -rz)。描いたあと mirror_faces で X を反転すると、ゲームと同じ形になる"""
    return {b: (-r[0], r[1], -r[2]) for b, r in rot.items()}


def preview(geos, tex, glow):
    def faces_of(name, pose=None):
        return mirror_faces(load_geo(geos[name], tex, glowmask=glow, pose=pose))

    near = faces_of("near")
    flap = faces_of("near", pose=gecko_pose({"fin_left": (0, 0, 6), "fin_right": (0, 0, -6), "fluke": (0, 0, 8)}))
    cols = []
    for name, label in (("near", "近景 (64 未満)"), ("mid", "中景 (64〜160)"), ("far", "遠景 (160 以上)")):
        fs = near if name == "near" else faces_of(name)
        cols.append(("%s %d 箱" % (label, cube_count(geos[name])),
                     [render(fs, yaw=90, pitch=0, size=(460, 460)),
                      render(fs, yaw=35, pitch=35, size=(460, 460)),
                      render(fs, yaw=180, pitch=8, size=(460, 460))]))
    # 胸びれの拡大 (頭の側の半分だけ)、動かした姿勢を斜め下から、錨石 (ゲームの左のわき腹。マゼンタ = 錨石、緑 = 鎖の付け根)
    def cen(f):
        return f.origin + f.eu / 2 + f.ev / 2
    fin_close = [f for f in near if cen(f)[2] <= -20]
    anchor = np.array([-21.0, 39.0, 61.0])          # ゲームの向き (X は反転済み)
    chain_top = np.array([-21.0, 28.0, 61.0])
    marks = marker(anchor, (3.4, 4.4, 2.4), (255, 0, 255, 255)) + marker(chain_top, (1.4, 1.4, 1.4), (0, 255, 0, 255))
    close = [f for f in near if abs(cen(f)[2] - 61) < 40 and cen(f)[1] > 12] + marks
    cols.append(("近景の拡大", [render(fin_close, yaw=200, pitch=10, size=(460, 460)),
                               render(flap, yaw=140, pitch=-20, size=(460, 460)),
                               render(close, yaw=90, pitch=10, size=(460, 460), margin=0.05)]))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sht = sheet("岩鯨 第5回: 胸びれの先の離れ片を直す / 骨ごとの離れ島をなくす",
                cols, note="ゲームの向き (X は反転済み)。行: 側面 / 斜め上 / 正面。右列: 胸びれ (頭の側の半分を前から) / 斜め下から (ひれを動かした姿勢) / 錨石のまわり (マゼンタ = 錨石、緑 = 鎖の付け根)。")
    sht.save(PREVIEW)
    print("preview", PREVIEW)
    fins_preview(geos, tex, glow)


def fins_preview(geos, tex, glow):
    """胸びれを下から見上げた絵 (ゲームの向き。GeckoLib の X 反転と回転の符号に合わせる)。
    行: 近景 / 中景 / 遠景。列: 静止 / ひれを上げた姿勢 (sin = 1) / 下げた姿勢 (sin = -1)。
    頭の側の半分 (z < 0) だけ描く。下から見るので、画像の右がゲームの体の右"""
    def cen(f):
        return f.origin + f.eu / 2 + f.ev / 2

    cols = []
    for title, s in (("静止", 0.0), ("sin = +1", 1.0), ("sin = -1", -1.0)):
        rot = {"fin_left": (0, 0, 6 * s), "fin_right": (0, 0, -6 * s)}
        imgs = []
        for name in ("near", "mid", "far"):
            fs = mirror_faces(load_geo(geos[name], tex, glowmask=glow, pose=gecko_pose(rot)))
            fs = [f for f in fs if cen(f)[2] < 0]
            imgs.append(render(fs, yaw=180, pitch=-80, size=(520, 400), margin=0.04))
        cols.append((title, imgs))
    sht = sheet("岩鯨 第5回: 胸びれを下から (行: 近景 / 中景 / 遠景)", cols,
                note="ゲームの向き。下から見上げ、頭が上。胸びれの先に離れた片がないこと。")
    FIN_PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sht.save(FIN_PREVIEW)
    print("fins preview", FIN_PREVIEW)


if __name__ == "__main__":
    main()
