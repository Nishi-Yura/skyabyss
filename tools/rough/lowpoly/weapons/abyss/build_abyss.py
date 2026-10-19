"""深淵の専用伝説 4 点 x 3 案: メッシュ・アトラス書き出し + 案ごとの絵 (回転 4 方向 / 手と地面) + 比較シート。

実行: python tools/rough/lowpoly/weapons/abyss/build_abyss.py [武器 id ...] [--quick]
出力: docs/review/lowpoly_roughs/abyss_weapons/<id>/<k>/{legend[_pullN][_plusN]_atlas.png, _glowmask.png, legend[_pullN]_mesh.json}
      docs/review/abyss_weapons/<短い名>/opt_<k>_views.png・opt_<k>_actions.png・compare.png (選定ページの絵)
--quick: 強化 +5 / +10 のアトラスと手・地面の絵を作らない (形の確認用)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
from scene import *  # noqa: F401,F403,E402
from mock import third_person, first_person, body_parts, xf  # noqa: E402
from wkit import export_mesh, rot_x, rot_y, rot_z, V, block  # noqa: E402
import wkit  # noqa: E402
import d_abyss as D  # noqa: E402
import paint_abyss as PA  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = wkit.ROOT
RAW = ROOT / "docs/review/lowpoly_roughs/abyss_weapons"
PAGE = ROOT / "docs/review/abyss_weapons"
SHORT = {"abyss_greatsword": "greatsword", "abyss_bell_staff": "bell_staff", "abyss_storm_bow": "storm_bow", "abyss_ash_scythe": "ash_scythe"}
PLAT = (22, 18, 34)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(wid, k, plus=0, stage=0):
    w = D.WEAPONS[wid]
    fn, inlay, em, _ = w["opts"][k]
    faces = fn(stage) if w["stages"] > 1 else fn()
    faces, size, P, N, M, L = build(faces)
    atlas, glow = PA.paint(faces, size, P, N, M, L, w["titan"], plus, inlay, em)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def stem(stage, plus):
    s = "legend"
    if stage > 0:
        s += f"_pull{stage - 1}"
    if plus:
        s += f"_plus{plus}"
    return s


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
    for stg in range(w["stages"]):
        base = None
        for plus in ((0,) if quick else (0, 5, 10)):
            r = make(wid, k, plus, stg)
            save_pair(r, d, stem(stg, plus))
            if plus == 0:
                base = r
                export_mesh(r["faces"], r["size"], d / f"{stem(stg, 0)}_mesh.json", f"{wid}_{k}_{stem(stg, 0)}",
                            f"{stem(stg, 0)}_atlas.png", f"{stem(stg, 0)}_glowmask.png")
            else:
                assert r["size"] == base["size"], "uv layout changed between plus levels"
            res[(stg, plus)] = r
        icon(res[(stg, 0)], wid).save(d / (stem(stg, 0).replace("legend", "icon") + ".png"))
    return res


# ------------------------------------------------------------------ アイコン (32x32。ゲーム内の傾き -45 度のまま正面から)

def icon(r, wid):
    w = D.WEAPONS[wid]
    cen = V(*w["cen"])
    fs = xf(r["faces"], rot_z(45), V(0, 0, 0), 1.0, cen)
    pts = np.concatenate([f.verts for f in fs])
    ext = max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
    sc = 28.0 / ext * 4
    mid = (pts.max(0) + pts.min(0)) / 2
    im = view(fs, r["atlas"], r["glow"], w["titan"], 180, 0, (128, 128), (mid[0], mid[1], 0), sc, ss=2, outline=False)
    small = Image.fromarray(np.array(im)).resize((32, 32), Image.BOX)
    arr = np.array(small).astype(float)
    solid = arr[..., 3] >= 70
    rgb = np.clip(arr[..., :3] * 1.45 + 10, 0, 255)
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = rgb[solid].astype(np.uint8)
    out[solid, 3] = 255
    edge = ~solid
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    ol = edge & near
    out[ol] = (14, 10, 22, 255)
    return Image.fromarray(out, "RGBA")


# ------------------------------------------------------------------ 絵

def label(dr, xy, text, sz=14, fill=(226, 220, 244)):
    dr.text(xy, text, font=font(sz), fill=fill)


def tile(r, titan, yaw, pitch, size, scale, cen):
    im = view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, cen, scale)
    bg = Image.new("RGBA", size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def ground_img(r, wid, size=(360, 330)):
    """地面に落ちた姿 (display の ground と同じ: メッシュを z 軸まわりに -45 度、0.5 倍)。石の台の上、斜め上から。"""
    w = D.WEAPONS[wid]
    R = rot_z(-45)
    cen = V(*w["cen"])
    fs = xf(r["faces"], R, V(0, 0, 0), 0.5, cen)
    slab = [(block("b", (0, -8.0, 0), (9, 2, 9), taper=(1, 1)), (96, 94, 104))]
    allf, at_, gl_ = compose([(fs, r["atlas"], r["glow"])], slab)
    return view(allf, at_, gl_, w["titan"], 215, -24, size, (0, -1, 0), 17.0)


def hand_poses(wid):
    return D.WEAPONS[wid]["titan"]


def views_image(wid, k, res, cap):
    w = D.WEAPONS[wid]
    r = res[(0, 0)]
    TW, TH = 240, 360
    ims = []
    for name, yaw, pit in (("前", 180, 0), ("横", 270, 0), ("後ろ", 0, 0), ("前 3/4 上", 150, -16)):
        ims.append((name, tile(r, w["titan"], yaw, pit, (TW, TH), w["scale"], w["cen"])))
    W = Image.new("RGB", (TW * 4, TH + 30), PLAT)
    dr = ImageDraw.Draw(W)
    for i, (n, im) in enumerate(ims):
        W.paste(im, (TW * i, 0))
        label(dr, (TW * i + 8, TH + 6), f"{n}  ({r['tris']} 面)" if i == 0 else n)
    return W


def on_plat(im):
    bg = Image.new("RGBA", im.size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def actions_image(wid, k, res):
    w = D.WEAPONS[wid]
    r = res[(0, 0)]
    titan = w["titan"]
    CW, CH = 300, 380
    rows = []
    cells = [("手に持つ 三人称", on_plat(third_person(r["faces"], r["atlas"], r["glow"], titan, w["grip"], size=(CW, CH)))),
             ("手に持つ 一人称", on_plat(first_person(r["faces"], r["atlas"], r["glow"], titan, w["grip"], size=(CW, CH)))),
             ("地面に落ちた姿", on_plat(ground_img(r, wid, (CW, CH))))]
    rows.append((CH, cells, CW))
    if (0, 5) in res:
        sc = w["scale"] * 0.85
        rows.append((300, [(f"強化 +{p}", tile(res[(0, p)], titan, 160, -8, (CW, 300), sc, w["cen"])) for p in (0, 5, 10)], CW))
    if w["stages"] > 1:
        rows.append((300, [(n, tile(res[(s_, 0)], titan, 180, 0, (225, 300), 9.0, (1.0, 8.0, 0))) for s_, n in enumerate(("休め", "引き 0", "引き 1", "引き 2"))], 225))
    H = sum(h + 28 for h, _, _ in rows)
    W = Image.new("RGB", (900, H), PLAT)
    dr = ImageDraw.Draw(W)
    y = 0
    for h, cs, cw in rows:
        for i, (n, im) in enumerate(cs):
            W.paste(im, (cw * i, y))
            label(dr, (cw * i + 8, y + h + 4), n)
        y += h + 28
    return W


def compare_image(wid, allres):
    w = D.WEAPONS[wid]
    TW, TH = 230, 360
    W = Image.new("RGB", (TW * 6, TH + 56), PLAT)
    dr = ImageDraw.Draw(W)
    for i, k in enumerate("abc"):
        r = allres[k][(0, 0)]
        for j, (yaw, pit) in enumerate(((180, 0), (150, -14))):
            W.paste(tile(r, w["titan"], yaw, pit, (TW, TH), w["scale"], w["cen"]), (TW * (2 * i + j), 28))
        label(dr, (TW * 2 * i + 10, 4), f"案 {k.upper()}   {r['tris']} 面", 18, (255, 232, 160))
    label(dr, (10, TH + 34), f"{w['jp']} ({wid}) 3 案", 14)
    return W


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    quick = "--quick" in sys.argv
    ids = args or list(D.WEAPONS)
    for wid in ids:
        w = D.WEAPONS[wid]
        out = PAGE / SHORT[wid]
        out.mkdir(parents=True, exist_ok=True)
        allres = {}
        for k in "abc":
            res = build_option(wid, k, quick)
            allres[k] = res
            print(wid, k, "tris", res[(0, 0)]["tris"], "atlas", res[(0, 0)]["size"], flush=True)
            views_image(wid, k, res, w["opts"][k][3]).save(out / f"opt_{k}_views.png")
            if not quick:
                actions_image(wid, k, res).save(out / f"opt_{k}_actions.png")
        compare_image(wid, allres).save(out / "compare.png")


if __name__ == "__main__":
    main()
