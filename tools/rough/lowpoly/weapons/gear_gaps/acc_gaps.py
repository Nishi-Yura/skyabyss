"""五章「深淵」の装飾品・護符 3 点 x 3 案と、素材 1 点 (錨鉄) の低ポリ。武器 (d_gaps.py) と同じ材質の言葉と絵 (paint_gaps.py)。
座標: 1 単位 = 1/16 ブロック、y 上、正面 -Z。品は原点まわり (おおよそ y -8..8)。装飾品は鍛えないので +5 / +10 の絵は無い。

実行: python -I tools/rough/lowpoly/weapons/gear_gaps/acc_gaps.py
出力: docs/review/roughs_gear_gaps/raw/<id>/<k>/{<階級>_mesh.json, <階級>_atlas.png, <階級>_glowmask.png, icon.png}
      docs/review/roughs_gear_gaps/<id>/compare.png
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import scene  # noqa: E402
from scene import *  # noqa: F401,F403,E402
from wkit import block, prism, curve, extrude, V, rot_x, rot_y, rot_z, export_mesh  # noqa: E402
import wkit  # noqa: E402
from lpkit import ellipsoid, spike  # noqa: E402
import paint_gaps as PG  # noqa: E402
from d_gaps import cage  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = wkit.ROOT
OUT = ROOT / "docs/review/roughs_gear_gaps"
RAW = OUT / "raw"
PLAT = (22, 18, 34)
for k, rgb in PG.GLOW_RGB.items():
    scene.GLOW_RGB["gg_" + k] = rgb


def ell(mat, c, r, segs=6, rings=2, rot=None):
    return ellipsoid(BONE, mat, c, r, segs=segs, rings=rings, rot=rot)


def ring(mat, c, R, r, n=8, ax="z"):
    """輪 (n 個の短い箱を並べる。どの箱も隣へ沈める)"""
    f = []
    for i in range(n):
        a = 360.0 * i / n
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        L = 2 * R * math.tan(math.pi / n) + r * 0.8
        if ax == "z":
            f += block(mat, (c[0] + R * ca, c[1] + R * sa, c[2]), (L, r, r), rot=rot_z(a + 90))
        else:
            f += block(mat, (c[0] + R * ca, c[1], c[2] + R * sa), (L, r, r), rot=rot_y(-a + 90))
    return f


# ====================================================================== 鼓動の聴き筒 (装飾・レア、鼓動の赤)
def li_a():
    """A 聴き喇叭: 錨鉄の細い筒が根で巻き、先が喇叭に開く。巻きの真ん中に赤い鼓動の核。"""
    f = prism("acc", (0, -6.0, 0), (0, -1.0, 0), 0.9, 0.9, sides=6, caps=(True, False))
    f += prism("spine", (0, -1.0, 0), (0, 3.4, 0), 0.9, 2.6, sides=8, caps=(False, False))
    f += prism("dark", (0, 3.2, 0), (0, 3.5, 0), 2.5, 2.5, sides=8, caps=(True, True))
    f += prism("trim", (0, 3.4, 0), (0, 4.0, 0), 2.8, 2.8, sides=8, caps=(True, True))
    f += ring("acc", (0, -3.0, 0), 2.6, 0.8, n=8)
    f += ell("gem", (0, -3.0, 0), (1.3, 1.3, 1.0))
    f += block("trim", (0, -6.4, 0), (1.6, 0.6, 1.6))
    return f


def li_b():
    """B 聴き円盤: 胸に当てる錨鉄の円盤 (縁は鋲)、真ん中の赤い核から二本の細い管が垂れる。"""
    f = prism("acc", (0, 0, -0.6), (0, 0, 0.6), 4.4, 4.4, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += prism("trim", (0, 0, -0.9), (0, 0, -0.5), 4.6, 4.6, sides=8, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, 0, -1.0), (1.6, 1.6, 0.8))
    for s in (1, -1):
        f += curve("cord", [(s * 1.0, -3.6, 0), (s * 1.6, -5.4, 0.2), (s * 1.2, -7.4, 0.4)], [0.5, 0.45, 0.4], [0.5, 0.45, 0.4], sides=4, caps=(False, True))
    f += block("spine", (0, 4.8, 0), (1.6, 1.2, 1.2))
    return f


def li_c():
    """C 巻き貝の耳: 塩の層の白い貝を錨鉄の帯で留めた耳当て。口の奥で赤い核が光る。"""
    f = ell("edge", (0, 0, 0), (3.6, 4.2, 2.6), segs=7, rings=3)
    f += ell("spine", (0.6, 1.8, -0.2), (2.2, 2.4, 1.9), segs=6, rings=2)
    f += ell("gem", (-0.6, -0.6, -2.0), (1.2, 1.4, 0.8))
    f += block("acc", (0, -0.4, 0), (7.8, 1.2, 3.0))
    f += block("trim", (0, -0.4, 0), (8.0, 0.4, 3.2))
    return f


# ====================================================================== 灯籠の護符 (護符・レア、灯りの琥珀)
def ch_a():
    """A 小さな灯籠: 心臓の間の灯籠を小さく写した籠。上に吊り環、下に錨鉄の重り。"""
    f = cage((0, 0, 0), 3.4, 5.0, 3.4, bar=0.8)
    f += block("acc", (0, 3.6, 0), (3.0, 1.0, 3.0), taper=(0.5, 0.5))
    f += ring("trim", (0, 5.4, 0), 1.1, 0.5, n=6)
    f += block("acc", (0, -3.4, 0), (2.0, 1.2, 2.0), taper=(0.6, 0.6))
    return f


def ch_b():
    """B 灯の円札: 錨鉄の六角の札に、灯の形の窓。窓の奥で琥珀が燃える。"""
    f = prism("acc", (0, 0, -0.7), (0, 0, 0.7), 4.6, 4.6, sides=6, caps=(True, True), hint=(1, 0, 0), phase=30)
    f += prism("trim", (0, 0, -1.0), (0, 0, -0.6), 4.8, 4.8, sides=6, caps=(True, True), hint=(1, 0, 0), phase=30)
    f += extrude("gem", [(0, 2.8), (-1.4, 0.4), (-1.0, -1.8), (1.0, -1.8), (1.4, 0.4)], 1.0, c=(0, 0, -1.1))
    f += ring("acc", (0, 5.6, 0), 1.1, 0.5, n=6)
    return f


def ch_c():
    """C 吊り灯の鈴: 鐘の形の灯。口の下から琥珀の灯がのぞき、上に鎖。"""
    f = prism("acc", (0, -2.0, 0), (0, 3.0, 0), 3.6, 1.4, sides=8, caps=(False, True))
    f += prism("trim", (0, -2.4, 0), (0, -1.6, 0), 3.9, 3.9, sides=8, caps=(True, True))
    f += ell("gem", (0, -2.2, 0), (2.2, 1.6, 2.2))
    f += block("acc", (0, 4.0, 0), (0.7, 1.6, 0.7))
    f += ring("acc", (0, 5.6, 0), 0.9, 0.45, n=6)
    return f


# ====================================================================== 測量士の錘 (装飾・伝説、淵の紫)
def pl_a():
    """A 下げ振り: 涙の形の錨鉄の錘。上の環から紐、先は細く尖り、腹に紫の目盛りが光る。"""
    f = spike(BONE, "spine", (0, -2.4, 0), (0, -1, 0), 4.6, 2.6, sides=8, rings=2, taper=0.8)
    f += prism("spine", (0, -2.8, 0), (0, 1.6, 0), 2.6, 1.2, sides=8, caps=(True, True))
    f += prism("trim", (0, 1.4, 0), (0, 2.2, 0), 1.4, 1.4, sides=8, caps=(True, True))
    f += ring("trim", (0, 3.4, 0), 1.0, 0.45, n=6)
    f += curve("cord", [(0, 4.2, 0), (0.3, 6.0, 0), (0, 7.6, 0)], [0.3, 0.3, 0.3], [0.3, 0.3, 0.3], sides=4, caps=(True, True))
    for y in (-2.2, -0.6):
        f += prism("gem", (0, y, 0), (0, y + 0.4, 0), 2.62 if y < -1 else 2.2, 2.62 if y < -1 else 2.2, sides=8, caps=(True, True))
    return f


def pl_b():
    """B 測鉛: 円筒の重い鉛と巻いた測り縄。縄に紫の結び目が並ぶ (深さの目盛り)。"""
    f = prism("spine", (0, -6.0, 0), (0, 0.0, 0), 1.9, 1.6, sides=8, caps=(True, True))
    f += prism("trim", (0, -0.2, 0), (0, 0.6, 0), 1.0, 1.0, sides=6, caps=(True, True))
    f += ring("cord", (0, 2.6, 0), 2.6, 0.9, n=8)
    f += ring("cord", (0, 3.4, 0), 2.4, 0.9, n=8)
    for i in range(3):
        a = math.radians(30 + 120 * i)
        f += block("gem", (2.5 * math.cos(a), 3.0, 2.5 * math.sin(a)), (0.9, 1.2, 0.9))
    f += curve("cord", [(0, 0.6, 0), (0.2, 1.8, 0), (0, 4.6, 0), (0, 7.0, 0)], [0.3] * 4, [0.3] * 4, sides=4, caps=(True, True))
    return f


def pl_c():
    """C 小さな錨: 錨の形の錘。腕の先に紫の爪、胴に目盛り。沈め錨と同じ言葉の小品。"""
    f = prism("spine", (0, -5.0, 0), (0, 4.0, 0), 0.8, 0.7, sides=6, caps=(True, True))
    for s in (1, -1):
        f += curve("acc", [(s * 0.4, -4.6, 0), (s * 2.4, -4.4, 0), (s * 3.6, -2.8, 0)], [0.7, 0.6, 0.5], [0.7, 0.6, 0.5], sides=4, caps=(False, True))
        f += spike(BONE, "gem", (s * 3.6, -2.9, 0), (s * 0.3, 1, 0), 1.4, 0.55, sides=4, rings=1)
    f += block("acc", (0, 2.4, 0), (4.4, 0.8, 0.8))
    f += ring("trim", (0, 5.4, 0), 1.0, 0.5, n=6)
    for y in (-1.6, 0.0):
        f += block("gem", (0, y, -0.6), (0.9, 0.4, 0.4))
    return f


# ====================================================================== 錨鉄 (素材。1 案)
def anchor_iron():
    """錨鉄: 錨の胴を折った短い鉄の棒。断面の真ん中が紫に光り、鎖の環が一つ残る。"""
    f = block("acc", (0, 0, 0), (9.0, 3.0, 3.4), rot=rot_z(-30), taper=(0.92, 0.92))
    f += block("gem", (3.7, 2.2, 0), (1.2, 2.0, 2.2), rot=rot_z(-30))
    f += ring("spine", (-4.0, -3.4, 0), 1.4, 0.7, n=6)
    return f


ITEMS = {
    "abyss_pulse_listener": dict(jp="鼓動の聴き筒", tier="rare", glow="pulse", opts=dict(
        a=(li_a, "聴き喇叭。細い筒の先が喇叭に開き、巻きの真ん中に赤い鼓動の核。"),
        b=(li_b, "聴き円盤。胸に当てる錨鉄の円盤と赤い核、二本の管。"),
        c=(li_c, "巻き貝の耳。白い貝を錨鉄の帯で留めた耳当て、口の奥で赤い核。"))),
    "abyss_lantern_charm": dict(jp="灯籠の護符", tier="rare", glow="lamp", opts=dict(
        a=(ch_a, "小さな灯籠。心臓の間の灯籠を写した籠。いちばん素直に「灯籠」。"),
        b=(ch_b, "灯の円札。六角の札に灯の形の窓。札らしく平たい。"),
        c=(ch_c, "吊り灯の鈴。鐘の形の灯、口の下から琥珀がのぞく。"))),
    "abyss_surveyor_plumb": dict(jp="測量士の錘", tier="legend", glow="void", opts=dict(
        a=(pl_a, "下げ振り。涙の形の錘、腹に紫の目盛り。測量の道具と一目で分かる。"),
        b=(pl_b, "測鉛。円筒の鉛と巻いた測り縄、紫の結び目が深さの目盛り。"),
        c=(pl_c, "小さな錨。錨の形の錘、腕の先に紫の爪。沈め錨と対になる。"))),
    "abyss_anchor_iron": dict(jp="錨鉄", tier="rare", glow="void", material=True, opts=dict(
        a=(anchor_iron, "錨の胴を折った短い鉄の棒。断面が紫に光り、鎖の環が一つ残る。"),)),
}


def font(sz):
    for p in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc"):
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(iid, k):
    it = ITEMS[iid]
    fn = it["opts"][k][0]
    faces, size, P, N, M, L = build(fn())
    atlas, glow = PG.paint(faces, size, P, N, M, L, it["glow"], it["tier"], 0, None, ())
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def icon(r, glow_key, yaw=200, pitch=-14):
    """32x32: 3/4 から写して縮め、1 画素の暗い輪郭 (武器のアイコンと同じ仕上げ)"""
    pts = np.concatenate([f.verts for f in r["faces"]])
    mid = (pts.max(0) + pts.min(0)) / 2
    ext = float(np.max(np.ptp(pts, 0)))
    im = view(r["faces"], r["atlas"], r["glow"], "gg_" + glow_key, yaw, pitch, (256, 256), tuple(mid), 26.0 / ext * 8, ss=2, outline=False)
    big = Image.fromarray(np.array(im))
    box = big.getbbox()
    if box:
        big = big.crop(box)
    s = 30.0 / max(big.size)
    w, h = max(1, round(big.size[0] * s)), max(1, round(big.size[1] * s))
    small = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    small.paste(big.resize((w, h), Image.BOX), ((32 - w) // 2, (32 - h) // 2))
    arr = np.array(small).astype(float)
    solid = arr[..., 3] >= 70
    rgb = np.clip(arr[..., :3] * 1.45 + 10, 0, 255)
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = rgb[solid].astype(np.uint8)
    out[solid, 3] = 255
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    out[~solid & near] = (14, 10, 22, 255)
    return Image.fromarray(out, "RGBA")


def tile(r, glow_key, yaw, pitch, size, scale):
    pts = np.concatenate([f.verts for f in r["faces"]])
    mid = tuple((pts.max(0) + pts.min(0)) / 2)
    im = view(r["faces"], r["atlas"], r["glow"], "gg_" + glow_key, yaw, pitch, size, mid, scale)
    bg = Image.new("RGBA", size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def main():
    for iid, it in ITEMS.items():
        keys = list(it["opts"])
        TW, TH = 230, 300
        sheet = Image.new("RGB", (TW * 2 * len(keys), TH + 120), PLAT)
        dr = ImageDraw.Draw(sheet)
        for i, k in enumerate(keys):
            r = make(iid, k)
            d = RAW / iid / k
            d.mkdir(parents=True, exist_ok=True)
            st = it["tier"]
            save_img(r["atlas"], d / f"{st}_atlas.png")
            gm = np.zeros((r["size"], r["size"], 4), np.uint8)
            gm[r["glow"]] = (255, 255, 255, 255)
            save_img(gm, d / f"{st}_glowmask.png")
            export_mesh(r["faces"], r["size"], d / f"{st}_mesh.json", f"{iid}_{k}_{st}", f"{st}_atlas.png", f"{st}_glowmask.png")
            ic = icon(r, it["glow"])
            ic.save(d / "icon.png")
            for j, (yaw, pit) in enumerate(((180, 0), (210, -18))):
                sheet.paste(tile(r, it["glow"], yaw, pit, (TW, TH), 16.0), (TW * (2 * i + j), 28))
            dr.text((TW * 2 * i + 10, 4), f"案 {k.upper()}   {r['tris']} 面", font=font(18), fill=(255, 232, 160))
            sheet.paste(ic.resize((64, 64), Image.NEAREST), (TW * 2 * i + 10, TH + 40), ic.resize((64, 64), Image.NEAREST))
            sheet.paste(ic, (TW * 2 * i + 84, TH + 56), ic)
            print(iid, k, "tris", r["tris"], "atlas", r["size"], flush=True)
        dr.text((10, TH + 104), f"{it['jp']} ({iid}) {len(keys)} 案", font=font(14), fill=(226, 220, 244))
        (OUT / iid).mkdir(parents=True, exist_ok=True)
        sheet.save(OUT / iid / "compare.png")


if __name__ == "__main__":
    main()
