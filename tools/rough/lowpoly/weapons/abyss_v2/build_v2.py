"""五章「深淵」の装備 6 点の作り直し (v2) を書き出し、確認の絵を作る。
実行: python -I tools/rough/lowpoly/weapons/abyss_v2/build_v2.py [id ...] [--quick]
出力: docs/review/roughs_gear_gaps/raw/<id>/v2/{<階級>[_plusN]_atlas.png, _glowmask.png, <階級>_mesh.json}
        (tools/install/install_abyss_gear.py が option v2 で入れる。アイコンは入れたあと icon_from_mesh.py でメッシュから)
      docs/review/abyss_gear_v2/<id>_views.png (前・横・後ろ・3/4・上から) と <id>_hold.png (三人称・一人称の模式・地面・強化 +0/+5/+10)
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import scene  # noqa: E402
from scene import view, compose  # noqa: E402
import mock  # noqa: E402
from mock import third_person, first_person, xf  # noqa: E402
import wkit  # noqa: E402
from wkit import export_mesh, build, save_img, tri_count, rot_z, V, block  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
import paint_v2 as PV  # noqa: E402
import items_v2 as IT  # noqa: E402

ROOT = wkit.ROOT
RAW = ROOT / "docs/review/roughs_gear_gaps/raw"
OUT = ROOT / "docs/review/abyss_gear_v2"
PLAT = (24, 20, 36)
TEX = 3.0   # 1 単位 3 テクセル (基準の 2 より細かく。樋・縫い目・目盛りが読める)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc"]
for k, rgb in PV.GLOW_RGB.items():
    scene.GLOW_RGB["v2_" + k] = rgb
mock.POSES.setdefault("fangw", dict(arm=58, pitch=44, roll=90, s3=0.85, s1=0.6))
mock.FP.setdefault("fangw", mock.FP["whale"])


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def key(it):
    k = "v2_" + it["glow"] + "_" + it.get("pose", "acc")
    scene.GLOW_RGB[k] = PV.GLOW_RGB[it["glow"]]
    if "pose" in it:
        mock.POSES.setdefault(k, mock.POSES[it["pose"]])
        mock.FP.setdefault(k, mock.FP[it["pose"]])
    return k


def make(iid, plus=0):
    it = IT.ITEMS[iid]
    faces, size, P, N, M, L = build(it["fn"](), tex=TEX)
    atlas, glow = PV.paint(faces, size, P, N, M, L, it["glow"], it["tier"], plus, it["inlay"], it.get("edge", ()), it["fuller"])
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def save_pair(r, d, st):
    save_img(r["atlas"], d / f"{st}_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, d / f"{st}_glowmask.png")


def on_plat(im):
    bg = Image.new("RGBA", im.size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def mid_of(r):
    pts = np.concatenate([f.verts for f in r["faces"]])
    return tuple((pts.max(0) + pts.min(0)) / 2), float(np.max(np.ptp(pts, 0)))


def tile(r, it, yaw, pitch, size, scale=None):
    mid, ext = mid_of(r)
    sc = scale or (min(size) * 0.86 / ext if it["kind"] == "acc" else it["scale"])
    cen = mid if it["kind"] == "acc" else it["cen"]
    return on_plat(view(r["faces"], r["atlas"], r["glow"], key(it), yaw, pitch, size, cen, sc))


def views_image(iid, r):
    it = IT.ITEMS[iid]
    TW, TH = 250, 380 if it["kind"] == "weapon" else 300
    vs = (("前", 180, 0), ("横", 270, 0), ("後ろ", 0, 0), ("前 3/4 上", 150, -18), ("後ろ 3/4 下", 330, 16))
    Wd = Image.new("RGB", (TW * len(vs), TH + 34), PLAT)
    dr = ImageDraw.Draw(Wd)
    for i, (n, yaw, pit) in enumerate(vs):
        Wd.paste(tile(r, it, yaw, pit, (TW, TH)), (TW * i, 0))
        dr.text((TW * i + 8, TH + 8), f"{n}  ({r['tris']} 面, アトラス {r['size']}px)" if i == 0 else n, font=font(14), fill=(226, 220, 244))
    return Wd


def ground_img(r, it, size):
    fs = xf(r["faces"], rot_z(-45), V(0, 0, 0), 0.5, V(*it["cen"]))
    slab = [(block("b", (0, -8.0, 0), (9, 2, 9)), (96, 94, 104))]
    allf, at_, gl_ = compose([(fs, r["atlas"], r["glow"])], slab)
    return view(allf, at_, gl_, key(it), 215, -24, size, (0, -1, 0), 17.0)


def hold_image(iid, res):
    it = IT.ITEMS[iid]
    r = res[0]
    CW, CH = 300, 380
    k = key(it)
    row = [("手に持つ 三人称 (模式)", on_plat(third_person(r["faces"], r["atlas"], r["glow"], k, it["grip"], size=(CW, CH)))),
           ("手に持つ 一人称 (模式)", on_plat(first_person(r["faces"], r["atlas"], r["glow"], k, it["grip"], size=(CW, CH)))),
           ("地面に落ちた姿", on_plat(ground_img(r, it, (CW, CH))))]
    rows = [(CH, row)]
    if 5 in res:
        rows.append((340, [(f"強化 +{p}", tile(res[p], it, 160, -8, (CW, 340), it["scale"] * 0.85)) for p in (0, 5, 10)]))
    H = sum(h + 30 for h, _ in rows)
    Wd = Image.new("RGB", (CW * 3, H), PLAT)
    dr = ImageDraw.Draw(Wd)
    y = 0
    for h, cs in rows:
        for i, (n, im) in enumerate(cs):
            Wd.paste(im, (CW * i, y))
            dr.text((CW * i + 8, y + h + 6), n, font=font(14), fill=(226, 220, 244))
        y += h + 30
    return Wd


def build_item(iid, quick=False):
    it = IT.ITEMS[iid]
    d = RAW / iid / "v2"
    d.mkdir(parents=True, exist_ok=True)
    tier = it["tier"]
    res = {}
    levels = (0,) if (quick or it["kind"] == "acc") else (0, 5, 10)
    for plus in levels:
        r = make(iid, plus)
        st = tier + (f"_plus{plus}" if plus else "")
        save_pair(r, d, st)
        if plus == 0:
            export_mesh(r["faces"], r["size"], d / f"{tier}_mesh.json", f"{iid}_v2_{tier}", f"{tier}_atlas.png", f"{tier}_glowmask.png", tex=TEX)
        else:
            assert r["size"] == res[0]["size"] and r["tris"] == res[0]["tris"]
        res[plus] = r
    print(iid, "tris", res[0]["tris"], "atlas", res[0]["size"], flush=True)
    OUT.mkdir(parents=True, exist_ok=True)
    views_image(iid, res[0]).save(OUT / f"{iid}_views.png")
    if it["kind"] == "weapon":
        hold_image(iid, res).save(OUT / f"{iid}_hold.png")
    return res


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    for iid in args or list(IT.ITEMS):
        build_item(iid, "--quick" in sys.argv)


if __name__ == "__main__":
    main()
