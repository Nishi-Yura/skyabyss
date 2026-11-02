"""あいだの塔の防具 3 式 (docs/design/54 の 2.6。ヒロイック・必要 Lv 50)。一式ごとに一品一案。どれも主「あいだに座すもの」と同じ言葉:
半分が天 (白と金)、半分が淵 (黒い石・黒い革と紫の光)、つなぎ目か胸に天秤の印 (金の輪と竿と二つの皿)。
  重 天秤の甲冑 (tower_scale)   白と金の板金の右半分を黒い石の板が食う。兜に金と黒の角、胸に天秤、背に白と黒の二枚の外套
  軽 渡りの装束 (tower_bridge)  橋を渡る者の長い外套。上は白、裾は黒。頭巾の留めに印、右の腰から鎖、背に白と黒の長い襟巻き
  術 光輪の衣 (tower_halo)      顔を白い布で覆う法衣 (主と同じ)。背に金の羽の光輪、白の衣の内は黒、頭の上に小さな光輪
部品・出し方・塗りは野の防具 (../wild/build_wild_armor.py) をそのまま借りる (骨・座標も同じ)。

実行: python -I tools/rough/lowpoly/armor/tower/build_tower_armor.py [一式 id ...]
出力: docs/review/roughs_tower/raw/armor_<一式>/a/<部位>_{mesh.json,atlas.png,glowmask.png}[_plus5|_plus10]・icons/a_<部位>_32.png、
      docs/review/roughs_tower/armor_<一式>/opt_a.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "wild"))
import build_wild_armor as W  # noqa: E402
from build_wild_armor import (A, bx, crv, rot_z, ARM, LEG, BOOT, bplate, ngon, drape, lume, spk, ext, ring_tube, M_SIDE,  # noqa: E402,F401
                              hood, helm, torso, collar, belt, sleeve, pauldron, trousers, boot, pair, sring, dloft, N)

ROOT = W.ROOT
SETS = {}


def armor(sid, ja, glow, mats):
    def deco(cls):
        SETS[sid] = dict(cls=cls, ja=ja, glow=glow, mats=mats)
        return cls
    return deco


def sigil(b, c, R=1.4):
    """天秤の印: 金の輪と光る珠と、輪を横切る斜めの棒"""
    x, y, z = c
    f = ring_tube(b, "brass", c, R, 0.16, n=16, plane="xy")
    f += A.E(b, "gem", (x, y, z - 0.1), (R * 0.4, R * 0.4, 0.3))
    f += crv(b, "brass", [(x - R * 0.9, y + R * 0.35, z - 0.12), (x + R * 0.9, y - R * 0.35, z - 0.12)], 0.1, 0.1, sides=4)
    return f


def balance(b, c, w=3.4):
    """胸の天秤: 竿、真ん中の柱、左に金の皿・右に黒い石の皿、柱の上に光る珠"""
    x, y, z = c
    f = crv(b, "brass", [(x - w, y, z), (x + w, y, z)], 0.16, 0.16, sides=4)
    f += crv(b, "brass", [(x, y - 2.6, z + 0.05), (x, y + 0.6, z)], 0.18, 0.18, sides=4)
    for s, mat in ((-1, "brass"), (1, "abyss")):
        f += crv(b, "brass", [(x + s * (w - 0.3), y - 0.1, z), (x + s * (w - 0.3), y - 1.3, z)], 0.06, 0.06, sides=3)
        f += A.E(b, mat, (x + s * (w - 0.3), y - 1.45, z - 0.05), (1.0, 0.25, 0.6))
    f += A.E(b, "gem", (x, y + 0.9, z - 0.1), (0.5, 0.5, 0.35))
    return f


def crack(b, pts, r=0.09):
    """紫に光る割れ目の線"""
    return crv(b, "lume", pts, r, r, sides=3)


# ====================================================================== 重: 天秤の甲冑
@armor("tower_scale", "天秤の甲冑", "void", dict(plate="cloudwhite", bevel="gold", brass="gold", abyss="basalt", cloth="blackleather", cape="cloudwhite",
                                                 sash="blackleather", chain="iron", dark="dark"))
class Scale:
    """天秤の甲冑: 白と金の板金の右半分を、紫の割れ目の走る黒い石の板が食う重鎧。兜に金の角と黒い角、そのあいだに天秤の印。胸に天秤、肩は左が金の大袖・右が黒い石と鎖"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = helm(b, "plate", "bevel", visor=True, slit_mat="dark")
        f += bx(b, "abyss", (3.1, 28.6, -6.2), (3.0, 7.4, 0.5))
        f += crack(b, [(2.2, 33.6, -6.5), (3.4, 31.6, -6.5), (2.8, 30.2, -6.5)])
        f += spk(b, "brass", (-4.8, 32.4, 0.6), (-0.7, 1.0, 0.25), 4.6, 1.0, sides=6, rings=2)
        f += spk(b, "abyss", (4.8, 32.4, 0.6), (0.7, 1.0, 0.25), 4.6, 1.0, sides=6, rings=2)
        f += sigil(b, (0, 32.6, -5.6), R=1.1)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "plate", bulk=0.5)
        f += bplate(B, "plate", "bevel", ngon(8.6, 9.2, 8, bottom=0.3), c=(0, 18.6, -3.8), h=0.9, ins=0.7, bow=0.9)
        f += bplate(B, "abyss", "abyss", ngon(4.4, 9.4, 6, bottom=0.3), c=(2.6, 18.4, -4.5), h=0.5, ins=0.3, bow=0.3)
        f += crack(B, [(1.4, 22.2, -5.05), (2.8, 20.2, -5.05), (2.2, 18.0, -5.05), (3.6, 15.4, -5.05)])
        f += balance(B, (0, 20.8, -5.15))
        f += belt(B, "sash", buckle="brass")
        for k in range(3):
            f += dloft(B, "plate" if k % 2 == 0 else "abyss", [sring(12.4 - 1.4 * k, 5.0 + 0.2 * k, -3.2 - 0.2 * k, 3.1 + 0.2 * k, n=N, p=3.0),
                                                              sring(11.2 - 1.4 * k, 5.2 + 0.2 * k, -3.4 - 0.2 * k, 3.3 + 0.2 * k, n=N, p=3.0)])
        out = {B: f}
        for s in (-1, 1):
            mat = "plate" if s < 0 else "abyss"
            g = sleeve(ARM[s], s, "cloth", glove=mat)
            g += pauldron(ARM[s], s, mat, layers=4, big=1.25, edge="bevel" if s < 0 else "abyss")
            if s > 0:
                g += crack(ARM[s], [(7.2, 26.0, -3.2), (8.4, 24.6, -3.4), (8.0, 23.2, -3.6)])
                for k in range(3):
                    g += ring_tube(ARM[s], "chain", (9.3, 22.6 - 1.0 * k, 0.0), 0.5, 0.12, n=8, plane="yz" if k % 2 else "xy")
            out[ARM[s]] = g
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "cloth", knee="plate" if s < 0 else "abyss", knee_bev="bevel" if s < 0 else "abyss")
                               + bplate(LEG[s], "plate" if s < 0 else "abyss", "bevel" if s < 0 else "abyss", ngon(2.6, 4.0, 8), c=(s * 2.05, 10.0, -2.9),
                                        h=0.6, ins=0.3, bow=0.6)))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "plate" if s < 0 else "abyss", cuff="bevel" if s < 0 else "abyss", toe="plate" if s < 0 else "abyss",
                                             heel="plate" if s < 0 else "abyss", tall=6.8)
                               + lume(BOOT[s], (s * 2.05, 4.4, -3.1), (0.3, 0.5, 0.15))))

    @staticmethod
    def extra(piece):
        """ヒロイックの一段上: 背に白と黒の二枚の外套 (地まで)、兜の上に印の輪"""
        if piece == "helmet":
            b = "armorHead"
            return {b: ring_tube(b, "brass", (0, 36.4, 2.0), 2.4, 0.16, n=18, plane="xy") + [g for g in A.E(b, "gem", (0, 36.4, 2.0), (0.6, 0.6, 0.4))]}
        if piece == "chestplate":
            B = "armorBody"
            f = drape(B, ["cape", "cape", "cape", "cape", "bevel"], -2.15, 23.8, -1.0, 3.2, 2.15, 3.4, rows=6, cols=4, fold=0.4, flare_z=2.0, t=0.4)
            f += drape(B, ["abyss", "abyss", "abyss", "abyss", "abyss"], 2.15, 23.8, -1.0, 3.25, 2.15, 3.4, rows=6, cols=4, fold=0.4, flare_z=2.0, t=0.4)
            f += crack(B, [(2.4, 18.0, 5.0), (3.2, 12.0, 5.6), (2.6, 6.0, 6.2)])
            return {B: f}
        return {}


# ====================================================================== 軽: 渡りの装束
@armor("tower_bridge", "渡りの装束", "dawn", dict(cloth="cloudwhite", cape="gold", brass="gold", abyss="blackleather", sash="blackleather", plate="leather",
                                                 bevel="gold", chain="iron", dark="dark"))
class Bridge:
    """渡りの装束: 天と淵のあいだの橋を渡る者の長い外套。上は白、裾は黒。頭巾の留めに天秤の印、口もとを黒い布で覆い、右の腰から鎖、斜めに金の帯"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=None, peak=0.6)
        f += bx(b, "abyss", (0, 26.0, -6.15), (8.2, 3.0, 0.5), taper=(0.9, 1.0))
        f += sigil(b, (0, 30.9, -6.5), R=0.9)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += collar(B, "cape", h=2.2, flare=1.0)
        f += belt(B, "sash", buckle="brass")
        f += crv(B, "brass", [(-4.2, 23.4, -3.1), (-1.0, 19.6, -3.3), (2.6, 15.2, -3.25), (4.6, 12.9, -3.1)], 0.3, 0.12, sides=4)
        for s in (-1, 1):
            f += drape(B, ["cloth", "abyss", "abyss", "cape"], s * 2.4, 13.0, 0.4, 2.95, 2.3, 3.4, rows=5, cols=4, fold=0.5, flare_z=1.6, t=0.45,
                       hem=[0.0, -1.0, 0.4, -1.2])
            f += drape(B, ["cloth", "abyss", "abyss", "cape"], s * 2.4, 12.6, 0.6, -2.95, 2.2, 3.0, rows=4, cols=4, fold=0.4, flare_z=0.8, t=0.45,
                       out=-1.0, ov=(0, 0, -1))
        for k in range(4):
            f += ring_tube(B, "chain", (4.9, 12.0 - 0.95 * k, -1.2), 0.45, 0.11, n=8, plane="yz" if k % 2 else "xy")
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", glove="abyss" if s > 0 else "plate", wide=1.05)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "abyss", wraps=(3.4, 5.0, 6.6, 8.2))))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "abyss", cuff="cape", toe="plate", tall=6.4) + lume(BOOT[s], (s * 2.05, 5.4, -3.0), (0.3, 0.3, 0.15))))

    @staticmethod
    def extra(piece):
        """ヒロイックの一段上: 首から背へ、白と黒の二本の長い襟巻き (膝の下まで)。頭巾の後ろに小さな印の輪"""
        if piece == "helmet":
            b = "armorHead"
            return {b: ring_tube(b, "brass", (0, 31.0, 6.6), 1.8, 0.14, n=16, plane="xy") + A.E(b, "gem", (0, 31.0, 6.6), (0.5, 0.5, 0.3))}
        if piece == "chestplate":
            B = "armorBody"
            f = drape(B, ["cloth", "cloth", "cloth", "cloth", "cape"], -1.6, 24.4, 2.0, 3.1, 1.3, 1.8, rows=6, cols=2, fold=0.5, flare_z=2.4, t=0.3)
            f += drape(B, ["abyss", "abyss", "abyss", "abyss", "cape"], 1.6, 24.4, 3.4, 3.15, 1.3, 1.8, rows=6, cols=2, fold=0.5, flare_z=2.6, t=0.3)
            f += crack(B, [(1.6, 20.0, 4.4), (1.9, 14.0, 5.6), (1.5, 8.0, 6.4)])
            return {B: f}
        return {}


# ====================================================================== 術: 光輪の衣
@armor("tower_halo", "光輪の衣", "dawn", dict(cloth="cloudwhite", cape="gold", brass="gold", abyss="blackleather", sash="gold", veil="whitecloth",
                                             plate="cloudwhite", bevel="gold", dark="dark"))
class Halo:
    """光輪の衣: 主と同じく顔を白い布で覆う法衣。額に天秤の印、背に金の羽の光輪。白い衣の前が割れて内の黒が見え、裾は足首まで"""
    @staticmethod
    def helmet():
        b = "armorHead"
        f = hood(b, "cloth", "cape", tail=18.0, peak=0.0)
        f += bx(b, "veil", (0, 27.9, -6.25), (7.4, 5.4, 0.3), taper=(0.92, 1.0))
        f += bx(b, "cape", (0, 30.75, -6.35), (7.6, 0.45, 0.35))
        f += sigil(b, (0, 32.4, -6.0), R=1.0)
        return {b: f}

    @staticmethod
    def chestplate():
        B = "armorBody"
        f = torso(B, "cloth")
        f += bx(B, "abyss", (0, 17.8, -2.95), (2.6, 11.6, 0.4))
        f += crack(B, [(-0.4, 22.6, -3.2), (0.5, 19.2, -3.2), (-0.3, 15.8, -3.2), (0.4, 13.2, -3.2)])
        f += collar(B, "cape", h=2.8, flare=1.4)
        f += belt(B, "sash")
        f += sigil(B, (0, 13.2, -3.45), R=0.9)
        for s in (-1, 1):
            f += drape(B, ["cloth", "cloth", "cloth", "cloth", "cape"], s * 2.6, 13.0, -1.6, 2.95, 2.1, 3.4, rows=6, cols=4, fold=0.4, flare_z=1.4, t=0.45)
            f += drape(B, ["cloth", "cloth", "cloth", "cape"], s * 2.4, 12.6, -1.4, -2.95, 2.3, 3.0, rows=5, cols=4, fold=0.3, flare_z=0.6, t=0.45,
                       out=-1.0, ov=(0, 0, -1))
            f += drape(B, ["abyss", "abyss", "abyss"], s * 0.9, 12.8, 0.2, -3.0, 0.9, 1.0, rows=4, cols=2, fold=0.2, flare_z=0.4, t=0.3, out=-1.0,
                       ov=(0, 0, -1))
        # 背の金の羽の光輪
        R = 6.2
        pts = [(R * math.cos(math.radians(a)), 20.4 + R * math.sin(math.radians(a)), 5.6) for a in range(0, 361, 24)]
        f += crv(B, "brass", pts, 0.22, 0.22, sides=4)
        for a in range(0, 360, 30):
            r = math.radians(a)
            f += W._feather(B, "brass" if a % 60 else "cloth", (R * math.cos(r), 20.4 + R * math.sin(r), 5.7), a - 90, L=2.6, w=0.55)
        f += crv(B, "brass", [(0, 20.4, 3.0), (0, 20.4, 5.6)], 0.2, 0.2, sides=4)
        out = {B: f}
        for s in (-1, 1):
            out[ARM[s]] = sleeve(ARM[s], s, "cloth", cuff="cape", wide=1.2)
        return out

    @staticmethod
    def leggings():
        return pair(lambda s: (LEG[s], trousers(LEG[s], s, "abyss")))

    @staticmethod
    def boots():
        return pair(lambda s: (BOOT[s], boot(BOOT[s], s, "cloth", cuff="cape", toe="plate", tall=5.0) + lume(BOOT[s], (s * 2.05, 4.6, -3.0), (0.3, 0.3, 0.15))))

    @staticmethod
    def extra(piece):
        """ヒロイックの一段上: 頭の上に小さな光輪 (羽 8 枚)、背に地まで届く白い垂れ (縁は金)"""
        if piece == "helmet":
            b = "armorHead"
            R = 4.4
            pts = [(R * math.cos(math.radians(a)), 37.2, R * math.sin(math.radians(a))) for a in range(0, 361, 30)]
            f = crv(b, "brass", pts, 0.16, 0.16, sides=4)
            for a in range(0, 360, 45):
                f += A.E(b, "gem", (R * math.cos(math.radians(a)), 37.2, R * math.sin(math.radians(a))), (0.35, 0.35, 0.35))
            return {b: f}
        if piece == "chestplate":
            B = "armorBody"
            return {B: drape(B, ["cloth", "cloth", "cloth", "cloth", "cape"], 0, 23.8, -2.0, 3.2, 3.6, 5.6, rows=6, cols=6, fold=0.4, flare_z=2.0, t=0.4)}
        return {}


# ====================================================================== 出す
def build_set(sid):
    cfg = SETS[sid]
    cls = cfg["cls"]
    W.CUR["set"] = dict(mats=cfg["mats"], glow=cfg["glow"], rarity="heroic")
    A.OUT = ROOT / f"docs/review/roughs_tower/armor_{sid}"
    A.RAW = ROOT / f"docs/review/roughs_tower/raw/armor_{sid}"
    A.NAMES["a"] = cfg["ja"]
    A.HEAD = "あいだの塔の防具"
    A.GLOWK["a"] = "tower_" + sid
    A.CLOTH_OF["a"] = "canvas"
    A.DESC["a"] = (cls.__doc__ or "").split(":", 1)[-1].strip()
    A.scene.GLOW_RGB["ad2_tower_" + sid] = tuple(int(c) for c in W.PW.GLOWS[cfg["glow"]][-2])
    A.PALS["G_tower_" + sid] = W.PW.GLOWS[cfg["glow"]]
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
