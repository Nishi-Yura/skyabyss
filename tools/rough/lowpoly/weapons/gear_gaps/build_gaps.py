"""五章「深淵」の手の武器 3 点 x 3 案: メッシュ・アトラス書き出し + 案ごとの絵 (回転 4 方向 / 手と地面 / 強化) + 比較シート。
深淵の専用伝説 (../abyss/build_abyss.py) と同じ作り。階級は品で決まる (灯り番の剣 = rare、沈め錨・鼓動の短剣 = legend)。

実行: python -I tools/rough/lowpoly/weapons/gear_gaps/build_gaps.py [武器 id ...] [--quick]
出力: docs/review/roughs_gear_gaps/raw/<id>/<k>/{<階級>[_plusN]_atlas.png, _glowmask.png, <階級>_mesh.json, icon.png}
      docs/review/roughs_gear_gaps/<id>/opt_<k>_views.png・opt_<k>_actions.png・compare.png (選定ページの絵)
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import scene  # noqa: E402
from scene import *  # noqa: F401,F403,E402
import mock  # noqa: E402
from mock import third_person, first_person, xf  # noqa: E402
from wkit import export_mesh, rot_z, V, block  # noqa: E402
import wkit  # noqa: E402
import d_gaps as D  # noqa: E402
import paint_gaps as PG  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = wkit.ROOT
OUT = ROOT / "docs/review/roughs_gear_gaps"
RAW = OUT / "raw"
PLAT = (22, 18, 34)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]

# 光の色 (scene.view が読む)。持ち方は既存の型 (mock.POSES) を借りる
for k, rgb in PG.GLOW_RGB.items():
    scene.GLOW_RGB["gg_" + k] = rgb


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def vk(w):
    """描画の鍵: 光の色は品の色、持ち方は借りた型"""
    key = "gg_" + w["glow"]
    mock.POSES.setdefault(key, mock.POSES[w["pose"]])
    mock.FP.setdefault(key, mock.FP[w["pose"]])
    return key


def make(wid, k, plus=0):
    w = D.WEAPONS[wid]
    fn, inlay, em, _ = w["opts"][k]
    faces = fn()
    faces, size, P, N, M, L = build(faces)
    atlas, glow = PG.paint(faces, size, P, N, M, L, w["glow"], w["tier"], plus, inlay, em)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def save_pair(r, d, st):
    save_img(r["atlas"], d / f"{st}_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, d / f"{st}_glowmask.png")


def build_option(wid, k, quick=False):
    w = D.WEAPONS[wid]
    d = RAW / wid / k
    d.mkdir(parents=True, exist_ok=True)
    res = {}
    base = None
    tier = w["tier"]
    for plus in ((0,) if quick else (0, 5, 10)):
        r = make(wid, k, plus)
        st = tier + (f"_plus{plus}" if plus else "")
        save_pair(r, d, st)
        if plus == 0:
            base = r
            export_mesh(r["faces"], r["size"], d / f"{tier}_mesh.json", f"{wid}_{k}_{tier}", f"{tier}_atlas.png", f"{tier}_glowmask.png")
        else:
            assert r["size"] == base["size"], "uv layout changed between plus levels"
        res[plus] = r
    icon(res[0], wid).save(d / "icon.png")
    return res


# ------------------------------------------------------------------ アイコン (32x32。ゲーム内の傾き -45 度のまま正面から。build_abyss と同じ)

def icon(r, wid):
    w = D.WEAPONS[wid]
    cen = V(*w["cen"])
    fs = xf(r["faces"], rot_z(45), V(0, 0, 0), 1.0, cen)
    pts = np.concatenate([f.verts for f in fs])
    ext = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
    sc = 28.0 / ext * 4
    mid = (pts.max(0) + pts.min(0)) / 2
    im = view(fs, r["atlas"], r["glow"], vk(w), 180, 0, (128, 128), (mid[0], mid[1], 0), sc, ss=2, outline=False)
    small = Image.fromarray(np.array(im)).resize((32, 32), Image.BOX)
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


# ------------------------------------------------------------------ 絵

def label(dr, xy, text, sz=14, fill=(226, 220, 244)):
    dr.text(xy, text, font=font(sz), fill=fill)


def on_plat(im):
    bg = Image.new("RGBA", im.size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def tile(r, w, yaw, pitch, size, scale, cen):
    return on_plat(view(r["faces"], r["atlas"], r["glow"], vk(w), yaw, pitch, size, cen, scale))


def ground_img(r, wid, size=(360, 330)):
    w = D.WEAPONS[wid]
    fs = xf(r["faces"], rot_z(-45), V(0, 0, 0), 0.5, V(*w["cen"]))
    slab = [(block("b", (0, -8.0, 0), (9, 2, 9), taper=(1, 1)), (96, 94, 104))]
    allf, at_, gl_ = compose([(fs, r["atlas"], r["glow"])], slab)
    return view(allf, at_, gl_, vk(w), 215, -24, size, (0, -1, 0), 17.0)


def views_image(wid, res):
    w = D.WEAPONS[wid]
    r = res[0]
    TW, TH = 240, 360
    Wd = Image.new("RGB", (TW * 4, TH + 30), PLAT)
    dr = ImageDraw.Draw(Wd)
    for i, (n, yaw, pit) in enumerate((("前", 180, 0), ("横", 270, 0), ("後ろ", 0, 0), ("前 3/4 上", 150, -16))):
        Wd.paste(tile(r, w, yaw, pit, (TW, TH), w["scale"], w["cen"]), (TW * i, 0))
        label(dr, (TW * i + 8, TH + 6), f"{n}  ({r['tris']} 面)" if i == 0 else n)
    return Wd


def actions_image(wid, res):
    w = D.WEAPONS[wid]
    r = res[0]
    key = vk(w)
    CW, CH = 300, 380
    rows = [(CH, [("手に持つ 三人称", on_plat(third_person(r["faces"], r["atlas"], r["glow"], key, w["grip"], size=(CW, CH)))),
                  ("手に持つ 一人称", on_plat(first_person(r["faces"], r["atlas"], r["glow"], key, w["grip"], size=(CW, CH)))),
                  ("地面に落ちた姿", on_plat(ground_img(r, wid, (CW, CH))))])]
    if 5 in res:
        sc = w["scale"] * 0.85
        rows.append((300, [(f"強化 +{p}", tile(res[p], w, 160, -8, (CW, 300), sc, w["cen"])) for p in (0, 5, 10)]))
    H = sum(h + 28 for h, _ in rows)
    Wd = Image.new("RGB", (900, H), PLAT)
    dr = ImageDraw.Draw(Wd)
    y = 0
    for h, cs in rows:
        for i, (n, im) in enumerate(cs):
            Wd.paste(im, (CW * i, y))
            label(dr, (CW * i + 8, y + h + 4), n)
        y += h + 28
    return Wd


def compare_image(wid, allres, icons):
    w = D.WEAPONS[wid]
    TW, TH = 230, 360
    Wd = Image.new("RGB", (TW * 6, TH + 120), PLAT)
    dr = ImageDraw.Draw(Wd)
    for i, k in enumerate("abc"):
        r = allres[k][0]
        for j, (yaw, pit) in enumerate(((180, 0), (150, -14))):
            Wd.paste(tile(r, w, yaw, pit, (TW, TH), w["scale"], w["cen"]), (TW * (2 * i + j), 28))
        label(dr, (TW * 2 * i + 10, 4), f"案 {k.upper()}   {r['tris']} 面", 18, (255, 232, 160))
        ic = icons[k]
        Wd.paste(ic.resize((64, 64), Image.NEAREST), (TW * 2 * i + 10, TH + 40), ic.resize((64, 64), Image.NEAREST))
        Wd.paste(ic, (TW * 2 * i + 84, TH + 56), ic)
        label(dr, (TW * 2 * i + 124, TH + 60), "32x32 のアイコン", 12)
    label(dr, (10, TH + 104), f"{w['jp']} ({wid}) 3 案", 14)
    return Wd


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    quick = "--quick" in sys.argv
    for wid in args or list(D.WEAPONS):
        out = OUT / wid
        out.mkdir(parents=True, exist_ok=True)
        allres, icons = {}, {}
        for k in "abc":
            res = build_option(wid, k, quick)
            allres[k] = res
            icons[k] = Image.open(RAW / wid / k / "icon.png").convert("RGBA")
            print(wid, k, "tris", res[0]["tris"], "atlas", res[0]["size"], flush=True)
            views_image(wid, res).save(out / f"opt_{k}_views.png")
            if not quick:
                actions_image(wid, res).save(out / f"opt_{k}_actions.png")
        compare_image(wid, allres, icons).save(out / "compare.png")


if __name__ == "__main__":
    main()
