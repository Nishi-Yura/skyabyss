"""終章の神器の防具 3 式 (docs/design/50 の 4.5。神器・必要 Lv 50)。一式ごとに一品一案。雲の島の三人の番を形にする:
  重 鐘守の重鎧 (divine_bellkeeper)   最後の鐘守。鐘の形の兜と肩当て、胸に鐘の紋、腰と肩から小さな鐘が下がる。背に空の藍の外套
  軽 霞渡りの装束 (divine_mistwalker) 霞鳥。くちばしの庇の頭巾、肩に灰白の羽の肩掛け、軽い巻き脚。背に長い羽の裳裾
  術 灯守の法衣 (divine_lampkeeper)   灯守の目。空の藍の法衣に白の重ね、胸に灯の目の留め、背に金の灯籠 (光る芯)、腰に小さな灯が二つ
塗りは雲海の言葉 (雲の白・金・空の藍・朝の光)。部品・出し方・塗りの仕組みは塔の防具 (../tower/build_tower_armor.py) と同じ (野の防具の部品を借りる)。

実行: python -I tools/rough/lowpoly/armor/divine/build_divine_armor.py [一式 id ...]
出力: docs/review/roughs_divine/raw/armor_<一式>/a/...・docs/review/roughs_divine/armor_<一式>/opt_a.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "wild"))
sys.path.insert(0, str(HERE.parent / "tower"))
import build_wild_armor as W  # noqa: E402
from build_wild_armor import (A, bx, crv, ARM, LEG, BOOT, bplate, ngon, drape, lume, spk, ring_tube, dloft, sring, N,  # noqa: E402,F401
                              hood, helm, torso, collar, belt, sleeve, pauldron, trousers, boot, pair)

ROOT = W.ROOT
SETS = {}


def armor(sid, ja, glow, mats):
    def deco(cls):
        SETS[sid] = dict(cls=cls, ja=ja, glow=glow, mats=mats)
        return cls
    return deco


def bell(b, c, r=0.7, h=1.1, mat="brass"):
    """小さな鐘 (口が下)。撞木は光る"""
    x, y, z = c
    rings = [sring(yy, r * k, z - r * k, z + r * k, n=10, p=2.0, cx=x) for yy, k in ((y - h, 1.0), (y - h * 0.8, 0.92), (y - h * 0.3, 0.6), (y, 0.3))]
    f = dloft(b, mat, rings, caps=(mat, mat))
    f += A.E(b, "gem", (x, y - h - 0.15, z), (r * 0.3, r * 0.3, r * 0.3))
    return f


# ====================================================================== 重: 鐘守の重鎧
@armor("divine_bellkeeper", "鐘守の重鎧", "dawn", dict(plate="cloudwhite", bevel="gold", brass="gold", cloth="bluecloth", cape="bluecloth",
                                                     sash="bluecloth", dark="dark"))
class Bellkeeper:
    """鐘守の重鎧: 最後の鐘守の白い重鎧。兜は鐘の形 (裾が広がり、頂に小さな鐘)、肩当ても伏せた鐘。胸に鐘の紋、腰と肩から小さな金の鐘が下がる"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel", visor=True, slit_mat="dark")
        f += dloft(b, "bevel", [sring(23.4, 6.6, -6.8, 6.6, n=16, p=2.4), sring(24.4, 6.0, -6.2, 6.0, n=16, p=2.4)], caps=("bevel", "bevel"))
        f += lume(b, (0, 28.8, -6.25), (3.0, 0.3, 0.15))
        f += bell(b, (0, 36.8, 0), r=0.9, h=1.4)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.5)
        f += bplate(B, "plate", "bevel", ngon(8.4, 9.0, 8, bottom=0.3), c=(0, 18.6, -3.8), h=0.9, ins=0.7, bow=0.9)
        # 胸の鐘の紋
        pts = [(-1.6, 16.4), (-1.5, 18.2), (-1.0, 19.6), (0, 20.4), (1.0, 19.6), (1.5, 18.2), (1.6, 16.4)]
        f += crv(B, "brass", [(x, y, -4.8) for x, y in pts] + [(-1.6, 16.4, -4.8)], 0.16, 0.16, sides=4)
        f += A.E(B, "gem", (0, 17.0, -4.95), (0.45, 0.45, 0.3))
        f += belt(B, "sash", buckle="brass")
        for x in (-3.0, 0.0, 3.0):
            f += crv(B, "brass", [(x, 12.4, -3.3), (x, 11.6, -3.4)], 0.06, 0.06, sides=3)
            f += bell(B, (x, 11.6, -3.4), r=0.55, h=0.9)
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", glove="plate")
            g += pauldron(ARM[s], s, "plate", layers=4, big=1.3, edge="bevel")
            g += crv(ARM[s], "brass", [(s * 8.6, 23.0, -1.0), (s * 8.8, 21.8, -1.0)], 0.06, 0.06, sides=3)
            g += bell(ARM[s], (s * 8.8, 21.8, -1.0), r=0.55, h=0.9)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate")
                               + bplate(LEG[s], "plate", "bevel", ngon(2.6, 4.0, 8), c=(s * 2.05, 10.0, -2.9), h=0.6, ins=0.3, bow=0.6)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="bevel", toe="plate", heel="plate", tall=6.8)
                               + lume(BOOT[s], (s * 2.05, 4.4, -3.1), (0.3, 0.5, 0.15))))

    @staticmethod
    def extra(piece):
        """一段上: 背に地まで届く空の藍の外套 (縁は金)、兜の両脇に金の鐘の耳飾り"""
        if piece == "helmet":
            b = "armorHead"
            f = []
            for s in (-1, 1):
                f += crv(b, "brass", [(s * 6.0, 26.4, 0), (s * 6.4, 25.0, 0)], 0.07, 0.07, sides=3)
                f += bell(b, (s * 6.4, 25.0, 0), r=0.6, h=1.0)
            return {b: f}
        if piece == "chestplate":
            B = "armorBody"
            return {B: drape(B, ["cape", "cape", "cape", "cape", "cape", "brass"], 0, 23.8, -1.4, 3.3, 4.4, 6.4, rows=7, cols=6, fold=0.5, flare_z=2.4, t=0.4,
                             hem=[0.0, -0.6, 0.2, -0.8, 0.0, -0.4])}
        return {}


# ====================================================================== 軽: 霞渡りの装束
@armor("divine_mistwalker", "霞渡りの装束", "frost", dict(cloth="cloudwhite", cape="whitecloth", brass="gold", sash="bluecloth", plate="cloudwhite",
                                                      bevel="gold", fur="whitefur", dark="dark"))
class Mistwalker:
    """霞渡りの装束: 霞鳥の羽をまとう軽い旅装。頭巾の前にくちばしの庇、肩に灰白の羽の肩掛け、胸を空の藍の帯が斜めに渡り、脚は軽い巻き"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=None, peak=0.0)
        f += spk(b, "brass", (0, 31.4, -6.0), (0, -0.35, -1), 2.6, 1.0, sides=5, rings=2)
        for s in (-1, 1):
            for k in range(3):
                f += W._feather(b, "cape" if k % 2 else "cloth", (s * 5.6, 31.0 - 0.6 * k, 1.0 + 0.8 * k), -s * (60 + 12 * k), L=2.8 - 0.4 * k, w=0.55)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += belt(B, "sash", buckle="brass")
        f += crv(B, "sash", [(-4.4, 23.4, -3.1), (-1.0, 19.6, -3.3), (2.6, 15.2, -3.25), (4.6, 12.9, -3.1)], 0.35, 0.14, sides=4)
        f += A.E(B, "gem", (-1.0, 19.6, -3.6), (0.4, 0.4, 0.25))
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cape"], s * 2.4, 13.0, 4.0, 2.95, 2.3, 3.0, rows=4, cols=4, fold=0.5, flare_z=1.2, t=0.4, hem=[0.0, -1.0, 0.4, -1.2])
        out = {B: f}
        for s in (-1, 1):
            g = sleeve(ARM[s], s, "cloth", cuff="sash", glove="plate")
            # 羽の肩掛け: 肩を覆う短い垂れと、裾から下がる羽
            g += drape(ARM[s], ["cape", "cape", "brass"], s * 6.0, 25.0, 20.4, 0.0, 3.0, 3.4, rows=3, cols=4, fold=0.2, flare_z=0.5, t=0.3, ov=(s, 0, 0))
            for k in range(5):
                g += W._feather(ARM[s], "cloth" if k % 2 else "cape", (s * 9.0, 21.0, -2.4 + 1.2 * k), 180 + s * 12, L=4.2 - 0.3 * abs(k - 2), w=0.75)
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", wraps=(3.4, 5.0, 6.6, 8.2))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate", cuff="cape", toe="plate", tall=5.6) + lume(BOOT[s], (s * 2.05, 4.6, -3.0), (0.3, 0.3, 0.15))))

    @staticmethod
    def extra(piece):
        """一段上: 背に長い羽の裳裾 (霞鳥の尾)、頭巾の後ろに二本の長い冠羽"""
        if piece == "helmet":
            b = "armorHead"
            f = []
            for s in (-1, 1):
                f += W._feather(b, "brass", (s * 1.2, 34.0, 4.0), s * 20 + 180, L=5.0, w=0.7)
            return {b: f}
        if piece == "chestplate":
            B = "armorBody"
            f = []
            for k in range(7):
                x = -3.0 + k
                f += W._feather(B, "cape" if k % 2 else "cloth", (x, 13.0, 3.4), 180 + (x * 4), L=11.0 - abs(x) * 1.2, w=1.0)
            return {B: f}
        return {}


# ====================================================================== 術: 灯守の法衣
@armor("divine_lampkeeper", "灯守の法衣", "dawn", dict(cloth="bluecloth", cape="cloudwhite", brass="gold", sash="gold", plate="cloudwhite",
                                                     bevel="gold", dark="dark"))
class Lampkeeper:
    """灯守の法衣: 灯守の目に仕えた者の空の藍の法衣。白の重ねと金の縁、胸に灯の目の留め (金の輪の中の光る瞳)、背に金の灯籠 (中に朝の光の芯)"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=16.0, peak=1.2)
        f += ring_tube(b, "brass", (0, 32.4, -5.9), 0.9, 0.14, n=14, plane="xy")
        f += A.E(b, "gem", (0, 32.4, -6.0), (0.5, 0.5, 0.3))
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        for s in (-1, 1):
            f += bx(B, "cape", (s * 2.6, 18.4, -3.0), (2.2, 11.0, 0.35))
        f += collar(B, "cape", h=2.6, flare=1.2)
        f += belt(B, "sash")
        # 灯の目の留め
        f += ring_tube(B, "brass", (0, 20.4, -3.4), 1.3, 0.18, n=16, plane="xy")
        f += A.E(B, "gem", (0, 20.4, -3.55), (0.75, 0.75, 0.35))
        f += A.E(B, "dark", (0, 20.4, -3.85), (0.25, 0.4, 0.12))
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cloth", "cape"], s * 2.6, 13.0, -1.6, 2.95, 2.1, 3.4, rows=6, cols=4, fold=0.4, flare_z=1.4, t=0.45)
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 12.6, -1.4, -2.95, 2.3, 3.0, rows=5, cols=4, fold=0.3, flare_z=0.6, t=0.45,
                       out=-1.0, ov=(0, 0, -1))
        # 背の灯籠
        cz = 4.8
        for x, z in ((-1.4, cz - 1.0), (1.4, cz - 1.0), (-1.4, cz + 1.0), (1.4, cz + 1.0)):
            f += crv(B, "brass", [(x, 15.6, z), (x, 21.0, z)], 0.12, 0.12, sides=4)
        f += bx(B, "brass", (0, 21.3, cz), (3.4, 0.5, 2.6))
        f += bx(B, "brass", (0, 15.4, cz), (3.4, 0.5, 2.6))
        f += spk(B, "brass", (0, 21.5, cz), (0, 1, 0), 1.6, 1.4, sides=4, rings=1)
        f += A.E(B, "gem", (0, 18.3, cz), (0.9, 1.6, 0.7))
        f += crv(B, "brass", [(0, 18.4, 3.0), (0, 18.4, cz - 1.0)], 0.16, 0.16, sides=4)
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.2)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cape", cuff="bevel", toe="plate", tall=5.0) + lume(BOOT[s], (s * 2.05, 4.6, -3.0), (0.3, 0.3, 0.15))))

    @staticmethod
    def extra(piece):
        """一段上: 腰から小さな灯が二つ下がる、背に白い長い垂れ"""
        if piece == "chestplate":
            B = "armorBody"
            f = drape(B, ["cape", "cape", "cape", "cape", "brass"], 0, 23.8, -2.0, 3.2, 3.6, 5.6, rows=6, cols=6, fold=0.4, flare_z=2.0, t=0.4)
            for s in (-1, 1):
                x = s * 4.0
                f += crv(B, "brass", [(x, 12.4, -2.8), (x, 10.6, -3.0)], 0.06, 0.06, sides=3)
                f += bx(B, "brass", (x, 10.4, -3.0), (0.9, 0.3, 0.9))
                f += A.E(B, "gem", (x, 9.7, -3.0), (0.4, 0.6, 0.4))
                f += bx(B, "brass", (x, 9.0, -3.0), (0.9, 0.3, 0.9))
            return {B: f}
        return {}


# ====================================================================== 出す
def build_set(sid):
    cfg = SETS[sid]
    cls = cfg["cls"]
    W.CUR["set"] = dict(mats=cfg["mats"], glow=cfg["glow"], rarity="heroic")
    A.OUT = ROOT / f"docs/review/roughs_divine/armor_{sid}"
    A.RAW = ROOT / f"docs/review/roughs_divine/raw/armor_{sid}"
    A.NAMES["a"] = cfg["ja"]
    A.HEAD = "終章の神器の防具"
    A.GLOWK["a"] = "divine_" + sid
    A.CLOTH_OF["a"] = "canvas"
    A.DESC["a"] = (cls.__doc__ or "").split(":", 1)[-1].strip()
    A.scene.GLOW_RGB["ad2_divine_" + sid] = tuple(int(c) for c in W.PW.GLOWS[cfg["glow"]][-2])
    A.PALS["G_divine_" + sid] = W.PW.GLOWS[cfg["glow"]]
    pieces = ((cls.helmet, "helmet"), (cls.chestplate, "chestplate"), (cls.leggings, "leggings"), (cls.boots, "boots"))
    pieces = tuple((W._plus(fn, cls.extra, piece), piece) for fn, piece in pieces)
    A.BUILD["a"] = tuple(W._lit(fn, piece) for fn, piece in pieces)
    sys.argv = [sys.argv[0], "a"]
    A.main()


def main(ids):
    for sid in ids or list(SETS):
        build_set(sid)


if __name__ == "__main__":
    main([a for a in sys.argv[1:] if not a.startswith("-")])
