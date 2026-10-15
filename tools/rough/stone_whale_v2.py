"""岩鯨 (stone_whale) 第2回ラフ: 高解像度の3案 A/B/C (前回の案C の形を彫り直す)。

使い方 (リポジトリ直下で): python tools/rough/stone_whale_v2.py
出力: docs/roughs/v2/stone_whale/<a|b|c>/ (geo.json, texture.png, glowmask.png, side/top/front/back.png)
      docs/roughs/v2/stone_whale.png (比較シート), docs/roughs/v2/stone_whale.md (説明と検査)

作り方:
- 形はまず「格子」(1 セル = 0.5 単位) に彫る。体は 1 単位の輪切り、断面は 0.5 単位で丸める。
  彫った格子を、同じ部品どうしで貪欲に合わせて箱にする (greedy meshing)。
  格子の 1 セルには 1 つの箱しか入らないので、箱どうしは重ならない。
- check_overlaps (O(n^2)) は箱が 1 万を超えると遅すぎるため、この script では箱を格子に戻して数える
  check_grid を使う (重なり数と、箱の体積の合計が格子の充填数と一致するかを見る)。
- テクスチャは 2 px / 単位 (1 セル = 1 画素)。面ごとの uv。模様はワールド座標で決める。
- 鎖の付け根は z=60 の -X 側 (尾側の左わき腹)。乱数なし (ハッシュで決める)。
- 頭は -Z、尾は +Z。全長は約 294 単位 (頭の先から尾びれの先まで)。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import load_geo, render, sheet  # noqa: E402

OUT = ROOT / "docs/roughs/v2/stone_whale"
PREV = ROOT / "docs/roughs/stone_whale/c"

YC = 40                  # 体の中心の高さ
CELL = 2                 # 1 単位あたりのセル数
OX, OY, OZ = 128, 16, 280  # 格子の原点 (セル)
NX, NY, NZ = 256, 256, 620
Z0, Z1 = -132, 138       # 体の輪切りの範囲
LS, RS = 1.08, 0.92      # 左 (-X) は少し張る、右は少し細い (非対称)
MOUTH = YC - 6           # 口の線の高さ
KEYS = [(-132, 40, 34), (-110, 52, 44), (-80, 56, 50), (-40, 58, 52), (-5, 56, 50),
        (40, 50, 44), (80, 34, 30), (114, 20, 18), (138, 9, 8)]   # (z, 全幅, 全高)

PART = {"head": 1, "jaw": 2, "body": 3, "tail": 4, "fins": 5, "fluke": 6, "ruins": 7, "spine": 8, "chain": 9}
MAT = {"stone": 1, "belly": 2, "moss": 3, "dark": 4, "bone": 5, "fin": 6, "ruin": 7, "glyph": 8,
       "eye": 9, "cyan": 10, "iron": 11, "plate": 12, "baleen": 13, "brow": 14, "crust": 15}
PART_NAME = {v: k for k, v in PART.items()}
MAT_NAME = {v: k for k, v in MAT.items()}
BONES = [("body", None, (0, YC, 0)), ("head", "body", (0, YC, -60)), ("jaw", "head", (0, MOUTH, -128)),
         ("tail", "body", (0, YC, 60)), ("fins", "body", (0, YC, 0)), ("fluke", "tail", (0, YC, 138)),
         ("ruins", "body", (0, YC, 0)), ("spine", "body", (0, YC, 0)), ("chain", "body", (0, YC, 60))]

# 限られたパレット (材料ごとに 2〜3 色)
PAL = {
    "stone": [(96, 100, 110), (84, 88, 100), (72, 76, 88)], "stone_mortar": (40, 42, 52),
    "moss": [(86, 122, 60), (68, 100, 46)],
    "plate": [(118, 122, 132), (104, 108, 120), (92, 96, 108)],
    "belly": [(190, 182, 164), (168, 160, 144)], "belly_mortar": (124, 116, 104),
    "ruin": [(108, 104, 112), (90, 86, 96), (124, 118, 122)], "ruin_mortar": (56, 52, 62),
    "dark": (40, 42, 52),
    "bone": [(206, 198, 174), (178, 170, 148), (150, 140, 120)],
    "fin": [(72, 78, 90), (58, 62, 74)], "fin_ray": (44, 46, 56),
    "eye": (255, 196, 92), "pupil": (30, 24, 20),
    "cyan": (96, 232, 255), "cyan_hi": (206, 250, 255),
    "iron": [(78, 80, 90), (58, 60, 68)],
    "baleen": (226, 220, 196),
    "brow": [(60, 58, 68), (48, 46, 56)],
    "crust": [(152, 142, 120), (120, 112, 94)], "speck": (186, 176, 146),
}

OPTS = {
    "a": dict(name="A 忠実 (案Cの高解像化)", lift=4, plates=0.14,
              arches=[(-6, 4, 14), (30, 38, 14)], extra=None, veins=False),
    "b": dict(name="B 遺跡を足す (塔・階段・壁)", lift=4, plates=0.14,
              arches=[(-6, 4, 14), (30, 38, 14), (-72, -66, 10)], extra="ruins", veins=False),
    "c": dict(name="C 有機・古代 (骨・結晶)", lift=4, plates=0.24,
              arches=[(-6, 4, 14), (30, 38, 14)], extra="fossil", veins=True),
}
CONCEPT = {
    "a": ("案Cの形 (細身の青鯨型、背に骨の稜線、二つの半円アーチと一本柱、左が長い胸びれ、左が高い尾びれ) を、"
          "0.5 単位の段で彫り直す。喉の溝は腹の上の小さな隆起、口の線は暗い溝、目は庇つきの琥珀色。"
          "胸びれ・尾びれは根から先へ段々に薄くなる。背の骨は一つずつ離れた椎骨。"
          "岩の板と藤壺は小さな浮き彫り。アーチは小さな石ブロックを組み、欠けを抜いてある。"),
    "b": ("Aの形に、背の遺跡を足す。中央に段で上がる階段と、青い刻みを入れた塔 (上は歯状)、"
          "左右の崩れた壁、散らばった石片、三つ目の小さなアーチ。アーチの石の一部に刻文の青い光を入れる。"),
    "c": ("Aの形に、古い化石の質感を足す。右わき腹の外皮が剥がれて骨が見え、左右のわき腹に肋の骨が出る。"
          "背には青い結晶の殻片が刺さり、表面の石の割れ目に青い光の脈が走る。"),
}


# ---------------------------------------------------------------- ハッシュ (乱数の代わり)

def hz(*vals):
    h = 2166136261
    for v in vals:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 2246822519) & 0xFFFFFFFF
    h ^= h >> 13
    return (h & 0xFFFF) / 65536.0


def nzv(a, b):
    h = (a.astype(np.int64) * 73856093) ^ (b.astype(np.int64) * 19349663)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def key(part, mat):
    return PART[part] * 16 + MAT[mat]


def part_of(zc):
    return "head" if zc < -60 else ("body" if zc < 60 else "tail")


def qh(v):
    """0.5 単位に丸める"""
    return round(v * 2) / 2


def prof(z):
    for (z0, w0, h0), (z1, w1, h1) in zip(KEYS, KEYS[1:]):
        if z0 <= z <= z1:
            t = (z - z0) / (z1 - z0)
            t = t * t * (3 - 2 * t)
            return w0 + (w1 - w0) * t, h0 + (h1 - h0) * t
    return KEYS[-1][1], KEYS[-1][2]


def body_dims(zc):
    W, H = prof(zc)
    f = 1.0
    if zc < -124:  # 鼻先を丸くする (四分円)
        d = (zc + 132) / 8.0
        f = math.sqrt(max(0.0, 1 - (1 - d) ** 2))
    W, H = max(2.0, W * f), max(2.0, H * f)
    return 2 * round(W / 2), 2 * round(H / 2)


def row_edges(H):
    """体の断面の帯の境目。中ほどは 1 単位、上と下の 2 単位は 0.5 単位 (丸みを出す段)"""
    e = [0.0]
    while e[-1] < H - 1e-9:
        y = e[-1]
        step = 0.5 if (y < 2 or y > H - 2 - 1e-9) else 1.0
        e.append(min(H, y + step))
    return e


# ---------------------------------------------------------------- 格子に彫る

class Model:
    def __init__(self, opt):
        self.opt = opt
        self.K = np.zeros((NX, NY, NZ), np.int16)
        self.SL = {}

    # --- 格子の基本操作
    def put(self, x0, x1, y0, y1, z0, z1, k, mode="empty"):
        if x1 <= x0 or y1 <= y0 or z1 <= z0:
            return
        sl = (slice(ci(x0), ci(x1)), slice(cj(y0), cj(y1)), slice(ck(z0), ck(z1)))
        reg = self.K[sl]
        if mode == "empty":      # すでにある所は残す (重ならない)
            self.K[sl] = np.where(reg == 0, k, reg)
        elif mode == "set":      # 上書き (k=0 なら削る)
            self.K[sl] = k
        elif mode == "replace":  # 既にある所だけ材料を替える
            self.K[sl] = np.where(reg != 0, k, reg)

    def blocks(self, x0, x1, y0, y1, z0, z1, k, broken=0.0, tag=0):
        """1 単位の石ブロックを並べる。broken の割合で欠けを抜く (崩れた縁)。k は関数でも可"""
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    if broken and hz(x, y, z, tag) < broken:
                        continue
                    kk = k(x, y, z) if callable(k) else k
                    if kk is None:
                        continue
                    self.put(x, x + 1, y, y + 1, z, z + 1, kk)

    def row_at(self, zs, y):
        if zs not in self.SL:
            return None
        for r in self.SL[zs]["rows"]:
            if r[0] <= y < r[1]:
                return r
        return None

    def top_min(self, z0, z1):
        return min(self.SL[z]["top"] for z in range(z0, z1) if z in self.SL)

    # --- 体 (1 単位の輪切り、帯ごとの箱)
    def body(self):
        for zs in range(Z0, Z1):
            zc = zs + 0.5
            W, H = body_dims(zc)
            bottom = YC - H / 2
            rows = []
            e = row_edges(H)
            for a, b in zip(e, e[1:]):
                y0, y1 = bottom + a, bottom + b
                t = ((y0 + y1) / 2 - YC) / (H / 2)
                p = 2.2 if t < 0 else 2.0     # 断面は楕円に近い丸み (箱にしない)
                hw = (W / 2) * max(0.0, 1 - abs(t) ** p) ** (1 / p)
                xl = -max(0.5, qh(hw * LS))
                xr = max(0.5, qh(hw * RS))
                rows.append((y0, y1, xl, xr, t))
            self.SL[zs] = dict(rows=rows, top=bottom + H, bottom=bottom, zc=zc)
            for (y0, y1, xl, xr, t) in rows:
                part = part_of(zc)
                if zc < -66 and y1 <= MOUTH + 1e-9:
                    part = "jaw"                      # 口より下は下あご
                mat = "belly" if t < -0.55 else "stone"
                self.put(xl, xr, y0, y1, zs, zs + 1, key(part, mat), "set")

    def mouth(self):
        """口の線: 頭の側面に、外側を一段削った暗い溝"""
        ym = MOUTH
        for zs in range(-128, -74):
            r = self.row_at(zs, ym + 0.25)
            if not r:
                continue
            xl, xr = r[2], r[3]
            self.put(xl, xl + 0.5, ym, ym + 0.5, zs, zs + 1, 0, "set")
            self.put(xr - 0.5, xr, ym, ym + 0.5, zs, zs + 1, 0, "set")
            self.put(xl + 0.5, xr - 0.5, ym, ym + 0.5, zs, zs + 1, key("head", "dark"), "set")

    def baleen(self):
        """口の前の縁に、ひげ板 (白い細い板) を並べる。鼻先の面に面で接する"""
        ym = MOUTH
        r = self.row_at(Z0, ym + 1.0)
        if not r:
            return
        xl, xr = r[2], r[3]
        for k in range(-6, 6):
            if xl + 0.5 <= k and k + 0.5 <= xr - 0.5:
                self.put(k, k + 0.5, ym + 0.5, ym + 2.0, Z0 - 0.5, Z0, key("head", "baleen"))

    def eyes(self):
        """目と眉: 琥珀色の目 (0.5 単位突き出す)、その上に庇の眉 (前が長く出る)"""
        for side in (-1, 1):
            for zs in range(-113, -110):
                r = self.row_at(zs, YC - 1.0)
                if not r:
                    continue
                xl, xr = r[2], r[3]
                x0, x1 = (xr, xr + 0.5) if side > 0 else (xl - 0.5, xl)
                self.put(x0, x1, YC - 2, YC, zs, zs + 1, key("head", "eye"))
            for zs in range(-114, -107):
                r = self.row_at(zs, YC + 0.5)
                if not r:
                    continue
                xl, xr = r[2], r[3]
                prot = 1.0 if zs < -110 else 0.5
                x0, x1 = (xr, xr + prot) if side > 0 else (xl - prot, xl)
                self.put(x0, x1, YC, YC + 1, zs, zs + 1, key("head", "brow"))

    def throat(self):
        """喉の溝: 腹の下に、長手方向の細い隆起を並べる (隆起の間が溝になる)"""
        for zs in range(-118, -14):
            r0 = self.SL[zs]["rows"][0]
            y0, xl, xr = r0[0], r0[2], r0[3]
            for px in (-8, -6, -4, -2, 0, 2, 4, 6, 8):
                if px >= xl + 0.5 and px + 1 <= xr - 0.5:
                    self.put(px, px + 1, y0 - 0.5, y0, zs, zs + 1, key(part_of(zs + 0.5), "belly"))

    def fins(self, side, z0, z1, L, T):
        """胸びれ: 根は体の側面に面で接し、外へ行くほど細く、下がって、段々に薄くなる"""
        yf = YC - 4.0
        for zs in range(z0, z1):
            s = (zs + 0.5 - z0) / (z1 - z0)
            ext = L * math.sin(math.pi * s) ** 0.6
            if ext < 1.0:
                continue
            rows = [r for r in self.SL[zs]["rows"] if r[0] < yf + T and r[1] > yf - T]
            if not rows:
                continue
            root = min(r[2] for r in rows) if side < 0 else max(r[3] for r in rows)
            for i in range(int(ext)):
                fy = yf - 0.25 * i
                th = T * (1 - i / (ext + 1)) ** 0.7 * (0.6 + 0.4 * math.sin(math.pi * s))
                th = max(0.5, qh(th))
                y0 = qh(fy - th / 2)
                x0, x1 = (root - i - 1, root - i) if side < 0 else (root + i, root + i + 1)
                self.put(x0, x1, y0, y0 + th, zs, zs + 1, key("fins", "fin"))

    def fluke(self, lift):
        """尾びれ: 左右の葉。根から外へ、後ろへ流れる。中央は後ろが短い (切れ込み)。左は持ち上がる"""
        for side in (-1, 1):
            for i in range(0, 58):
                x0, x1 = (i, i + 1) if side > 0 else (-(i + 1), -i)
                za = 138 + 0.15 * i
                zb = (146 + 0.28 * i) if i >= 4 else (140 + 0.5 * i)
                for zs in range(138, int(round(zb))):
                    if zs + 1 <= za:
                        continue
                    s = min(1.0, max(0.0, (zs + 0.5 - za) / max(1e-6, zb - za)))
                    th = qh(max(0.5, (3.0 - 0.04 * i) * (1 - 0.5 * s)))
                    yc_f = YC - 1 + (lift if side < 0 else 0)
                    y0 = qh(yc_f - th / 2)
                    self.put(x0, x1, y0, y0 + th, zs, zs + 1, key("fluke", "fin"))

    def spine(self, z0=-108, z1=104):
        """背の骨の稜線: 間をあけた椎骨 (それぞれ離れた骨)。背の面から立つ"""
        zv = z0
        while zv + 2 <= z1:
            zs_list = [z for z in range(int(math.floor(zv)), int(math.ceil(zv + 2))) if z in self.SL]
            top = min(self.SL[z]["top"] for z in zs_list)
            self.put(-1.5, 1.5, top - 1, top + 2, zv, zv + 2, key("spine", "bone"))
            self.put(-0.5, 0.5, top + 2, top + 3.5, zv, zv + 2, key("spine", "bone"))
            zv += 3.5

    def arch(self, za0, za1, R, tag, glyph_p=0.0):
        """背に立つ半円のアーチ。小さな石ブロック (1 単位) を環に並べる。欠けを抜き、脚は体に埋まる"""
        cy = int(round(self.SL[(za0 + za1) // 2]["top"]))
        for zs in range(za0, za1):
            for ix in range(-R - 1, R + 1):
                for iy in range(-R - 1, R + 1):
                    cx, cyy = ix + 0.5, iy + 0.5
                    r = math.hypot(cx, cyy)
                    ang = math.degrees(math.atan2(cyy, cx))
                    if not (R - 3 <= r <= R and -25 <= ang <= 205):
                        continue
                    if hz(ix, iy, zs, tag) < 0.12:
                        continue
                    glyph = glyph_p and hz(ix, iy, zs, tag + 7) < glyph_p
                    self.put(ix, ix + 1, cy + iy, cy + iy + 1, zs, zs + 1,
                             key("ruins", "glyph" if glyph else "ruin"))

    def pillar(self, za0, za1, x0, x1, h, tag):
        top = int(round(self.top_min(za0, za1)))
        self.blocks(x0, x1, top - 1, top + h, za0, za1, key("ruins", "ruin"), broken=0.08, tag=tag)
        self.put(x0 - 1, x1 + 1, top + h, top + h + 1, za0, za1, key("ruins", "ruin"))

    def ruins_extra(self):
        """B: 中央の塔 (刻文の帯・歯状の上端)、段の階段、崩れた壁、散らばった石片"""
        top = int(round(self.SL[-18]["top"]))

        def tower(x, y, z):
            if y >= top + 16 and (x + z) % 2 == 0:
                return None
            if y == top + 8 and -2 <= x < 2:
                return key("ruins", "glyph")
            return key("ruins", "ruin")

        self.blocks(-5, 5, top - 1, top + 18, -24, -12, tower, broken=0.04, tag=11)
        t1 = int(round(self.SL[-30]["top"]))
        self.blocks(-5, 5, t1 - 1, t1 + 2, -36, -30, key("ruins", "ruin"), broken=0.06, tag=21)
        t2 = int(round(self.SL[-24]["top"]))
        self.blocks(-5, 5, t2 - 1, t2 + 4, -30, -24, key("ruins", "ruin"), broken=0.06, tag=22)
        self.blocks(-14, -12, YC, int(round(self.top_min(10, 24))) + 6, 10, 24,
                    key("ruins", "ruin"), broken=0.25, tag=31)
        self.blocks(12, 14, YC, int(round(self.top_min(10, 24))) + 6, 10, 24,
                    key("ruins", "ruin"), broken=0.25, tag=32)
        for k in range(14):
            x = int(-14 + 28 * hz(k, 3))
            z = int(-100 + 200 * hz(k, 4))
            if z not in self.SL:
                continue
            top_z = int(round(self.SL[z]["top"]))
            self.put(x, x + 1, top_z - 1, top_z + 1 + (k % 2), z, z + 1, key("ruins", "ruin"))

    def ribs(self):
        """C: 肋の骨が両わき腹から 0.5 単位出る (化石の露出)"""
        for zs in range(-96, 96, 4):
            r_ = self.SL[zs]["rows"]
            for (y0, y1, xl, xr, t) in r_:
                if y1 <= YC - 12 or y0 >= YC + 2:
                    continue
                a, b = max(y0, YC - 12), min(y1, YC + 2)
                self.put(xr, xr + 0.5, a, b, zs, zs + 0.5, key(part_of(zs + 0.5), "bone"))
                self.put(xl - 0.5, xl, a, b, zs, zs + 0.5, key(part_of(zs + 0.5), "bone"))

    def bone_patch(self):
        """C: 右わき腹の外皮が剥がれて、骨の面が見える所"""
        for zs in range(-70, -40):
            for (y0, y1, xl, xr, t) in self.SL[zs]["rows"]:
                if y1 <= YC - 8 or y0 >= YC + 2:
                    continue
                self.put(xr - 1, xr, y0, y1, zs, zs + 1, key(part_of(zs + 0.5), "bone"), "replace")

    def crystals(self):
        """C: 背に刺さる青い結晶の殻片 (光る)"""
        for k in range(12):
            z = int(-100 + 190 * hz(k, 1))
            x = int(-14 + 28 * hz(k, 2))
            if z not in self.SL:
                continue
            top = int(round(self.SL[z]["top"]))
            self.put(x, x + 1, top, top + 2 + (k % 2), z, z + 1, key("ruins", "cyan"))

    def plates(self, prob):
        """岩の板: 両わき腹の段から 1 単位出る小さな浮き彫り"""
        for zs in range(-128, 134):
            for ri, (y0, y1, xl, xr, t) in enumerate(self.SL[zs]["rows"]):
                if y1 - y0 < 1 or t < -0.6:
                    continue
                part = part_of(zs + 0.5)
                if hz(zs, ri, 1) < prob:
                    self.put(xl - 1, xl, y0, y1, zs, zs + 1, key(part, "plate"))
                if hz(zs, ri, 2) < prob:
                    self.put(xr, xr + 1, y0, y1, zs, zs + 1, key(part, "plate"))

    def barnacles(self):
        """藤壺: 腹の下に 0.5 単位の小さな粒"""
        for zs in range(-120, 130):
            r = self.SL[zs]["rows"][0]
            y0, xl, xr = r[0], r[2], r[3]
            for xi in range(int(math.floor(xl)) + 1, int(math.ceil(xr)) - 1):
                for half in (0, 1):
                    if hz(xi, zs, half, 5) < 0.10:
                        z = zs + 0.5 * half
                        self.put(xi, xi + 0.5, y0 - 0.5, y0, z, z + 0.5, key(part_of(zs + 0.5), "crust"))

    def chain(self):
        """鎖の付け根 (尾側の左わき腹、z=60) の錨石と、垂れる鎖の短い区間。鎖の本体はエンティティで描く"""
        r = self.row_at(60, YC)
        xl = r[2]
        self.put(xl - 3, xl, YC - 3, YC + 1, 60, 62, key("chain", "iron"))
        for k in range(3):
            ytop = YC - 4 - 3 * k
            self.put(xl - 2.5, xl - 1.5, ytop - 2, ytop, 60, 61, key("chain", "iron"))


def build(kid):
    o = OPTS[kid]
    M = Model(o)
    M.body()
    M.mouth()
    M.baleen()
    M.eyes()
    M.throat()
    M.fins(-1, -104, -36, 22, 4.5)     # 左の胸びれ (長い)
    M.fins(1, -92, -44, 13, 3.5)       # 右の胸びれ (短い)
    M.fluke(o["lift"])
    M.spine()
    for (a, b, R) in o["arches"]:
        M.arch(a, b, R, tag=a * 3 + 1, glyph_p=0.2 if o["extra"] == "ruins" else 0.0)
    M.pillar(-46, -42, -2, 2, 26, tag=41)
    if o["extra"] == "ruins":
        M.ruins_extra()
    if o["extra"] == "fossil":
        M.ribs()
        M.bone_patch()
        M.crystals()
    M.plates(o["plates"])
    M.barnacles()
    M.chain()
    return M


# ---------------------------------------------------------------- 格子 <-> 箱

def ci(x):
    return int(round(x * CELL)) + OX


def cj(y):
    return int(round(y * CELL)) + OY


def ck(z):
    return int(round(z * CELL)) + OZ


def mesh(K):
    """格子を箱にする。x 方向の連続 -> z 方向 -> y 方向の順に同じ材料を合わせる。
    返り値: (N, 7) の配列 [key, x0, x1, y0, y1, z0, z1] (セル単位、格子の座標)"""
    Kx = np.moveaxis(K, 0, -1)                       # (NY, NZ, NX)
    pad = np.zeros((NY, NZ, NX + 2), np.int16)
    pad[..., 1:-1] = Kx
    cur, prv, nxt = pad[..., 1:-1], pad[..., :-2], pad[..., 2:]
    occ = cur != 0
    st = np.argwhere(occ & (prv != cur))
    en = np.argwhere(occ & (nxt != cur))
    kk = cur[st[:, 0], st[:, 1], st[:, 2]].astype(np.int64)
    y, z, x0, x1 = st[:, 0], st[:, 1], st[:, 2], en[:, 2] + 1
    # x の連続 -> 1 行 (y, z) ごとの箱
    # z 方向に伸ばす
    order = np.lexsort((z, y, x1, x0, kk))
    kk, y, z, x0, x1 = kk[order], y[order], z[order], x0[order], x1[order]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (x0[1:] == x0[:-1]) & (x1[1:] == x1[:-1]) & \
        (y[1:] == y[:-1]) & (z[1:] == z[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    kk, x0, x1, y, z0, z1 = kk[gs], x0[gs], x1[gs], y[gs], z[gs], z[ge] + 1
    # y 方向に伸ばす
    order = np.lexsort((y, z1, z0, x1, x0, kk))
    kk, x0, x1, y, z0, z1 = kk[order], x0[order], x1[order], y[order], z0[order], z1[order]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (x0[1:] == x0[:-1]) & (x1[1:] == x1[:-1]) & \
        (z0[1:] == z0[:-1]) & (z1[1:] == z1[:-1]) & (y[1:] == y[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    out = np.stack([kk[gs], x0[gs], x1[gs], y[gs], y[ge] + 1, z0[gs], z1[gs]], 1)
    return out


def check_grid(boxes, K):
    """箱どうしの重なりを、箱を格子に戻して数える。
    返り値: (重なったセル数, 箱の体積の合計 == 格子の充填数 か)"""
    cnt = np.zeros(K.shape, np.uint8)
    for (_, x0, x1, y0, y1, z0, z1) in boxes:
        cnt[x0:x1, y0:y1, z0:z1] += 1
    over = int((cnt > 1).sum())
    vol = int(sum((x1 - x0) * (y1 - y0) * (z1 - z0) for (_, x0, x1, y0, y1, z0, z1) in boxes))
    filled = int((K != 0).sum())
    return over, vol == filled, filled


# ---------------------------------------------------------------- テクスチャ (2 px / 単位)

def face_corners(o, s):
    x0, y0, z0 = o
    sx, sy, sz = s
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    return {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }


def world_grid(o, s, fname, w, h):
    co, eu, ev = face_corners(o, s)[fname]
    co, eu, ev = (np.array(v, float) for v in (co, eu, ev))
    cols = (np.arange(w) + 0.5) / w
    rows = 1.0 - (np.arange(h) + 0.5) / h
    return co + cols[None, :, None] * eu + rows[:, None, None] * ev


def paint(tex, glow, u, v, w, h, mat, kind, P, veins):
    if kind == "tb":
        A, Bc = P[..., 0], P[..., 2]
    else:
        A, Bc = P[..., 2], P[..., 1]
    Yw = P[..., 1]
    A2 = np.floor(A * 2).astype(np.int64)
    B2 = np.floor(Bc * 2).astype(np.int64)
    n = nzv(A2, B2)
    gm = np.zeros((h, w), bool)
    base = np.zeros((h, w, 3), float)
    if mat in ("stone", "plate", "belly", "ruin"):
        tw, th = {"stone": (8, 5), "plate": (6, 4), "belly": (6, 4), "ruin": (4, 4)}[mat]
        tones = np.array({"stone": PAL["stone"], "plate": PAL["plate"], "belly": PAL["belly"],
                          "ruin": PAL["ruin"]}[mat], float)
        mort = np.array({"stone": PAL["stone_mortar"], "plate": PAL["stone_mortar"],
                         "belly": PAL["belly_mortar"], "ruin": PAL["ruin_mortar"]}[mat], float)
        row = np.floor_divide(B2, th)
        off = (row % 2) * (tw // 2)
        bx = np.floor_divide(A2 + off, tw)
        m = ((A2 + off) % tw == 0) | (B2 % th == 0)
        idx = (bx * 7 + row * 3 + (nzv(A2 // 4, B2 // 4) * 3).astype(np.int64)) % len(tones)
        base = tones[idx]
        base[m] = mort
        chip = (n > 0.93) & ~m          # 欠けた縁
        base[chip] *= 0.7
        if mat == "stone":
            # 苔は背の上の面だけ (上を向く面と、背のきわ)。腹と下の面にはつけない
            top_ = np.clip((Yw - (YC + 6)) / 10.0, 0, 1) if kind == "tb" else np.clip((Yw - (YC + 14)) / 10.0, 0, 1)
            mp = top_ * 0.6
            mm = (nzv(A2 // 2, B2 // 2) < mp) & ~m
            mc = np.array(PAL["moss"], float)[(n > 0.5).astype(int)]
            base[mm] = mc[mm]
            if veins:                     # 青い光の脈
                vf = np.abs(np.sin(A * 2.3 + 1.7 * np.sin(Bc * 0.9))) < 0.09
                base[vf] = PAL["cyan"]
                gm |= vf
    elif mat == "glyph":
        base[:] = PAL["cyan"]
        base[(B2 % 3) == 0] *= 0.75
        gm[:] = True
    elif mat == "cyan":
        base[:] = PAL["cyan"]
        base[(A2 + B2) % 2 == 0] = PAL["cyan_hi"]
        gm[:] = True
    elif mat == "eye":
        base[:] = PAL["eye"]
        base[h // 2, w // 2] = PAL["pupil"]
        gm[:] = True
    elif mat == "dark":
        base[:] = PAL["dark"]
        base *= (0.9 + 0.2 * n)[..., None]
    elif mat == "bone":
        tones = np.array(PAL["bone"], float)
        base = tones[(n * 3).astype(int) % 3]
        base[(B2 % 4) == 0] *= 0.85
    elif mat == "fin":
        tones = np.array(PAL["fin"], float)
        base = tones[(np.floor_divide(B2, 3)) % 2]
        base[(A2 % 7) == 0] = PAL["fin_ray"]
    elif mat == "iron":
        tones = np.array(PAL["iron"], float)
        base = tones[((A2 + B2) // 2) % 2]
    elif mat == "baleen":
        base[:] = PAL["baleen"]
        base[(A2 % 2) == 0] *= 0.9
    elif mat == "brow":
        tones = np.array(PAL["brow"], float)
        base = tones[(n * 2).astype(int) % 2]
    elif mat == "crust":
        tones = np.array(PAL["crust"], float)
        base = tones[(n * 2).astype(int) % 2]
        base[n > 0.85] = PAL["speck"]
    base = np.clip(base, 0, 255)
    tex[v:v + h, u:u + w, :3] = base.astype(np.uint8)
    tex[v:v + h, u:u + w, 3] = 255
    if mat in ("eye", "cyan", "glyph"):
        gm[:] = True
    if gm.any():
        gl = glow[v:v + h, u:u + w]
        gl[gm, :3] = base[gm].astype(np.uint8)
        gl[gm, 3] = 255


def pack(items, S):
    pos, x, y, shelf = {}, 1, 0, 0   # (0,0) の 1 画素は、外から見えない面の共通の色として空けておく
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w + 1 > S:
            x, y, shelf = 0, y + shelf + 1, 0
        if y + h > S:
            return None
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


def exposed(K, x0, x1, y0, y1, z0, z1):
    """箱の 6 面のうち、外に出ている面 (隣が空いている) を調べる。隠れた面は 1x1 画素で済ませる"""
    def free(sl):
        return not np.all(K[sl] != 0)
    return {
        "west": free((slice(x0 - 1, x0), slice(y0, y1), slice(z0, z1))),
        "east": free((slice(x1, x1 + 1), slice(y0, y1), slice(z0, z1))),
        "down": free((slice(x0, x1), slice(y0 - 1, y0), slice(z0, z1))),
        "up": free((slice(x0, x1), slice(y1, y1 + 1), slice(z0, z1))),
        "south": free((slice(x0, x1), slice(y0, y1), slice(z1, z1 + 1))),
        "north": free((slice(x0, x1), slice(y0, y1), slice(z0 - 1, z0))),
    }


def make_geo(boxes, veins, K):
    cubes = []
    for (k, x0, x1, y0, y1, z0, z1) in boxes:
        k, x0, x1, y0, y1, z0, z1 = (int(v) for v in (k, x0, x1, y0, y1, z0, z1))
        sc = (x1 - x0, y1 - y0, z1 - z0)
        cubes.append(dict(part=PART_NAME[k // 16], mat=MAT_NAME[k % 16], cells=sc,
                          cell_lo=(x0, y0, z0), cell_hi=(x1, y1, z1),
                          o=((x0 - OX) / CELL, (y0 - OY) / CELL, (z0 - OZ) / CELL),
                          s=(sc[0] / CELL, sc[1] / CELL, sc[2] / CELL)))
    items = []
    for ci_, c in enumerate(cubes):
        a, b, d = c["cells"]
        fp = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}
        lo, hi = c["cell_lo"], c["cell_hi"]
        ex = exposed(K, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2])
        c["exp"] = ex
        for fname, (w, h) in fp.items():
            if ex[fname]:
                items.append(((ci_, fname), w, h))
    for S in (512, 1024, 2048, 4096):
        pos = pack(items, S)
        if pos:
            break
    tex = np.zeros((S, S, 4), np.uint8)
    glow = np.zeros((S, S, 4), np.uint8)
    tex[0, 0] = (*PAL["stone"][2], 255)
    cube_uv = {}
    for ci_, c in enumerate(cubes):
        uv = {}
        a, b, d = c["cells"]
        fp = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}
        for fname, (w, h) in fp.items():
            if not c["exp"][fname]:
                uv[fname] = {"uv": [0, 0], "uv_size": [1, 1]}   # 隠れた面
                continue
            u, v = pos[(ci_, fname)]
            uv[fname] = {"uv": [u, v], "uv_size": [w, h]}
            kind = {"up": "tb", "down": "tb"}.get(fname, "side")
            P = world_grid(c["o"], c["s"], fname, w, h)
            paint(tex, glow, u, v, w, h, c["mat"], kind, P, veins)
        cube_uv[ci_] = uv
    bones_out = []
    for name, parent, piv in BONES:
        b = {"name": name, "pivot": list(piv)}
        if parent:
            b["parent"] = parent
        b["cubes"] = [{"origin": list(c["o"]), "size": list(c["s"]), "uv": cube_uv[ci_]}
                      for ci_, c in enumerate(cubes) if c["part"] == name]
        bones_out.append(b)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.stone_whale_v2",
                "texture_width": S,
                "texture_height": S,
                "visible_bounds_width": 90,
                "visible_bounds_height": 60,
                "visible_bounds_offset": [0, 20, 0],
            },
            "bones": bones_out,
        }],
    }
    return geo, tex, glow, cubes


def views_of(geo, tex, glow, cubes):
    faces = load_geo(geo, tex, glowmask=glow)
    back = [f for f in faces if f.origin[1] >= YC + 8 and -20 <= f.origin[2] <= 50]
    return faces, back


def main():
    results, images = {}, {}
    # 比較用: 前回の案C
    prev_geo = json.loads((PREV / "stone_whale.geo.json").read_text(encoding="utf-8"))
    prev_faces = load_geo(prev_geo, np.array(Image.open(PREV / "stone_whale.png").convert("RGBA")),
                          glowmask=np.array(Image.open(PREV / "stone_whale_glowmask.png").convert("RGBA")))
    prev_back = [f for f in prev_faces if f.origin[1] >= YC + 8 and -20 <= f.origin[2] <= 50]
    images["prev"] = [render(prev_faces, yaw=90, pitch=0, size=(420, 420)),
                      render(prev_faces, yaw=35, pitch=35, size=(420, 420)),
                      render(prev_faces, yaw=0, pitch=0, size=(420, 420)),
                      render(prev_back, yaw=25, pitch=30, size=(420, 420))]

    for kid in OPTS:
        o = OPTS[kid]
        M = build(kid)
        boxes = mesh(M.K)
        over, vol_ok, filled = check_grid(boxes, M.K)
        geo, tex, glow, cubes = make_geo(boxes, o["veins"], M.K)
        d = OUT / kid
        d.mkdir(parents=True, exist_ok=True)
        (d / "geo.json").write_text(json.dumps(geo, separators=(",", ":")), encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(d / "texture.png", optimize=True)
        Image.fromarray(glow, "RGBA").save(d / "glowmask.png", optimize=True)
        faces, back = views_of(geo, tex, glow, cubes)
        views = [render(faces, yaw=90, pitch=0, size=(420, 420)),
                 render(faces, yaw=35, pitch=35, size=(420, 420)),
                 render(faces, yaw=0, pitch=0, size=(420, 420)),
                 render(back, yaw=25, pitch=30, size=(420, 420))]
        for name, im in zip(("side", "top", "front", "back"), views):
            im.save(d / f"{name}.png")
        images[kid] = views
        zs = [c["o"][2] for c in cubes] + [c["o"][2] + c["s"][2] for c in cubes]
        ys = [c["o"][1] for c in cubes] + [c["o"][1] + c["s"][1] for c in cubes]
        xs = [c["o"][0] for c in cubes] + [c["o"][0] + c["s"][0] for c in cubes]
        mins = {}
        for c in cubes:
            mins[c["part"]] = mins.get(c["part"], 0) + 1
        results[kid] = dict(boxes=len(cubes), tex=tex.shape[0], over=over, vol_ok=vol_ok,
                            length=(min(zs), max(zs)), width=(min(xs), max(xs)), height=(min(ys), max(ys)),
                            filled=filled, parts=mins, faces=len(faces))
        print(kid, "boxes", len(cubes), "tex", tex.shape[0], "over", over, "vol_ok", vol_ok,
              "z", (min(zs), max(zs)), "x", (min(xs), max(xs)), "y", (min(ys), max(ys)), flush=True)

    labels = [("前回の案C (比較)", images["prev"])] + \
             [(OPTS[k]["name"], images[k]) for k in OPTS]
    sht = sheet("岩鯨 stone_whale 第2回 (高解像度) 3案", labels,
                "行: 側面 / 3/4 上から / 正面 / 背の遺跡の近景。左端は比較用の前回案")
    (ROOT / "docs/roughs/v2").mkdir(parents=True, exist_ok=True)
    sht.save(ROOT / "docs/roughs/v2/stone_whale.png")
    write_md(results)


def write_md(results):
    lines = [
        "# 岩鯨 (stone_whale) 第2回ラフ: 高解像度の3案",
        "",
        "生成: `tools/rough/stone_whale_v2.py` (乱数なし。再実行で同じ結果)。",
        "出力: `docs/roughs/v2/stone_whale/<a|b|c>/` (geo.json、texture.png、glowmask.png、side/top/front/back.png)、比較シート `docs/roughs/v2/stone_whale.png`。",
        "",
        "## 共通の作り方",
        "",
        "- 形は 0.5 単位の格子 (1 セル = 0.5 単位 = 1/32 ブロック) に彫ってから、同じ部品どうしを箱に合わせる。箱どうしは面で接するだけで、重ならない。",
        "- 体は 1 単位の輪切り (z 方向)。各輪切りの断面は、中ほど 1 単位・上下の端 0.5 単位の帯に割り、帯ごとの幅を 0.5 単位で丸める。楕円に近い断面の段で、丸い胴に見える。鼻先は四分円で丸める。",
        "- 体の長さは z -132〜138 (胴)、尾びれの先まで約 294 単位。頭は -Z、尾は +Z。",
        "- 喉の溝: 腹の下に、長手方向の細い隆起 (0.5 単位) を並べる。隆起の間が溝になる。",
        "- 目: 琥珀色の光る目 (0.5 単位突出)。その上に庇の眉 (前側が 1 単位、後ろが 0.5 単位出る)。",
        "- 口の線: 頭の側面の高さ一段に、外側を 0.5 単位削った暗い溝。前の縁に白いひげ板 (0.5 単位) を並べる。",
        "- 胸びれ: 左 (-X) は長く (22 単位)、右は短く (13 単位)。根は体の側面に面で接し、外へ行くほど細く、下がり、段で薄くなる。",
        "- 尾びれ: 根から後ろへ流れる。中央は切れ込み。左 (-X) は 4 単位持ち上がる。後ろの縁ほど薄い段。",
        "- 背の骨: 間をあけた椎骨 (3 単位ごとに 2 単位の骨と、その上の棘)。それぞれ離れた骨。",
        "- 二つの半円アーチ: 1 単位の石ブロックを環に並べる。約 12% のブロックを抜いて、欠けた縁にする。脚は体の面に埋まる。柱は 2x2 単位、高さ 26 単位で欠けあり。",
        "- 岩の板・藤壺: 両わき腹の段に 1 単位の浮き彫り (板)、腹の下に 0.5 単位の粒。",
        "- 鎖: 尾側の左わき腹 (z=60) に錨石の突起 (3 単位) と、垂れる鎖の短い 3 節。鎖の本体はエンティティで描く。",
        "- テクスチャ: 2 px / 単位 (1 セル = 1 画素)。面ごとの uv。模様は各面のワールド座標で決めるので、輪切りをまたいで石の目・苔がつながる。",
        "- パレット: 材料ごとに 2〜3 色 (石の青灰、苔の緑、腹の淡い色、骨の白茶、鉄、琥珀、青い光)。",
        "- 重なり検査: `check_overlaps` (O(n^2)) は箱が多く遅すぎるため、この script では箱を格子に戻して数える `check_grid` を使う。重なったセル数と、箱の体積の合計が格子の充填数に一致するかを見る。",
        "",
    ]
    for kid in OPTS:
        r = results[kid]
        parts = ", ".join(f"{k} {v}" for k, v in sorted(r["parts"].items(), key=lambda kv: PART[kv[0]]))
        lines += [
            f"## {OPTS[kid]['name']}",
            "",
            CONCEPT[kid],
            "",
            f"- 直方体の数 {r['boxes']} (部品別: {parts})。ラフのため多い。本実装では LOD を考える。",
            f"- テクスチャ {r['tex']}x{r['tex']} (2 px / 単位)。",
            f"- 寸法 (単位): 長さ z {r['length'][0]}〜{r['length'][1]}、幅 x {r['width'][0]}〜{r['width'][1]}、高さ y {r['height'][0]}〜{r['height'][1]}。",
            f"- 重なり検査: {'重なりなし (重なったセル 0、体積の合計 = 格子の充填数 ' + str(r['filled']) + ')' if r['over'] == 0 and r['vol_ok'] else '要確認: 重なったセル ' + str(r['over']) + ', 体積一致 ' + str(r['vol_ok'])}",
            "",
        ]
    lines += ["## 重なり検査の結果", "", "| 案 | 直方体 | 重なり |", "|---|---|---|"]
    for kid in OPTS:
        r = results[kid]
        ok = "0 件" if r["over"] == 0 and r["vol_ok"] else f"{r['over']} セル / 体積一致 {r['vol_ok']}"
        lines.append(f"| {OPTS[kid]['name']} | {r['boxes']} | {ok} |")
    (ROOT / "docs/roughs/v2/stone_whale.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
