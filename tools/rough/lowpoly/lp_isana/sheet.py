"""イサナ (whale_chaser) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest + 検査。
実行: python tools/rough/lowpoly/lp_isana/sheet.py
出力: docs/review/lowpoly_roughs/lp_isana.png, lp_isana/{a,b,c}/, lp_isana_manifest.json"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (255, 190, 120), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A つば広帽の旅人", "b": "B 荒くれの鯨獲り", "c": "C 若い旅人"}
FIG = ((0, 15, 0), 13.4)
ROWS = [
    ("前 (休め)", 430, 180, 5, "rest", FIG),
    ("前 3/4 (待機 idle: 腕がわずかに揺れるだけ)", 430, 212, 10, "rest", FIG),
    ("横", 430, 270, 4, "rest", FIG),
    ("後ろ (背の荷)", 430, 32, 8, "rest", FIG),
    ("顔のアップ (顔は頭の中。帽子のつばが額に影を落とす)", 400, 185, 5, "rest", ((0, 22.0, -3), 36.0)),
    ("顔 前 3/4", 400, 212, 8, "rest", ((0, 22.0, -3), 36.0)),
    ("話す (talk: 右腕を上げる)", 430, 215, 8, "talk", FIG),
    ("歩く (walk: 脚と腕の振り)", 430, 250, 5, "walk", FIG),
]


def export_all():
    opts = {k: get_option(k)[0] for k in "abc"}
    return opts


def validate(opts):
    geo = json.load(open(ASSETS / "geo/entity/whale_chaser.geo.json"))["minecraft:geometry"][0]["bones"]
    gb = {b["name"]: (b.get("parent"), b["pivot"], b.get("rotation")) for b in geo}
    for k in "abc":
        d = OUT / "lp_isana" / k
        m = json.load(open(d / f"whale_chaser_{k}_lowpoly_mesh.json"))
        assert set(m["bones"]) == set(gb), (set(m["bones"]) ^ set(gb))
        for n, b in m["bones"].items():
            assert b["parent"] == gb[n][0] and b["pivot"] == gb[n][1], n
            if gb[n][2] and n != "root":
                assert b.get("rotation") == gb[n][2], n
            nv = len(b["vertices"])
            assert all(0 <= i < nv for t in b["triangles"] for i in t)
            assert all(-0.001 <= u <= 1.001 for uv in b["uvs"] for u in uv)
        print(k, m["triangle_count"], sorted(p.name for p in d.iterdir()))


def main():
    opts = export_all()
    validate(opts)
    tri = {k: tri_count(r["body"]) for k, r in opts.items()}
    per = {k: r["data"]["bones"] for k, r in opts.items()}
    g = lambda k, *ns: sum(per[k][n]["triangle_count"] for n in ns)
    print(tri)
    F = voxel.font
    cv = Image.new("RGB", (W, 7000), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((28, 14), "イサナ (whale_chaser) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
    d.text((28, 50), "骨 11 本 (root[Y 180 度] > body > head > hat / hair / hood2、body > armL / armR / legL / legR / pack) の名前・階層・pivot は今の geo と同じ。"
                     "今のアニメ 3 本 (idle・walk・talk) がそのまま動く。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
    d.text((28, 72), "左端が今のゲーム内のモデル (箱 690 個)。目は光らせず、顔は頭の中に描く (浮いた板なし)。三角形は全身。NPC の上限 約 800。"
                     "選んだ「A つば広帽の旅人」(帽子で額に影・無精髭・細めた目・小さな荷) の人物像は 3 案とも守る。", font=F(15), fill=GR)
    y = 104
    for title, ph, yaw, pitch, pose, (cen, sc) in ROWS:
        d.text((X0, y), title, font=F(20), fill=CY)
        y += 30
        f, n = old_faces(POSES[pose])
        panels = [old_render(f, yaw, pitch, (PW, ph), cen, sc * (1.0 if sc > 20 else 1.0))]
        labels = ["今 (箱 %d 個)" % n]
        for k in "abc":
            im = render_pose(opts[k], POSES[pose], yaw, pitch, (PW, ph), cen, sc)
            bg = bgpanel(PW, ph)
            bg.paste(im, (0, 0), im)
            panels.append(bg)
            labels.append("%s  %d 三角形" % (NAMES[k], tri[k]))
        for i, (p, lb) in enumerate(zip(panels, labels)):
            cv.paste(p, (xs[i], y))
            d.rectangle([xs[i] - 1, y - 1, xs[i] + PW, y + ph], outline=(60, 60, 78))
            d.text((xs[i] + 2, y + ph + 6), lb, font=F(17), fill=WH if i == 0 else CY)
        y += ph + 38
    d.text((X0, y), "テクスチャのアトラス (最近傍で 2 倍表示)", font=F(20), fill=CY)
    y += 30
    hh = 0
    for i, k in enumerate("abc", start=1):
        at = Image.fromarray(opts[k]["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        sc_ = 1 if at.width >= 256 else 2
        sc_ = min(sc_, 3)
        im = bgc.convert("RGB").resize((at.width * sc_ if at.width * sc_ <= PW else PW, at.height * sc_ if at.width * sc_ <= PW else at.height * PW // at.width), Image.NEAREST)
        cv.paste(im, (xs[i], y))
        d.text((xs[i], y + im.height + 4), "%s  アトラス %d x %d" % (NAMES[k], opts[k]["size"], opts[k]["size"]), font=F(15), fill=CY)
        hh = max(hh, im.height)
    y += hh + 36
    for k in "abc":
        d.text((X0, y), "%s:  全身 %d 三角形 (胴・外套・襟巻き %d / 頭 %d / 帽子 %d / 髪 %d / 腕 %d / 脚 %d / 荷 %d)"
               % (NAMES[k], tri[k], g(k, "body"), g(k, "head"), g(k, "hat"), g(k, "hair"), g(k, "armL", "armR"),
                  g(k, "legL", "legR"), g(k, "pack")), font=F(17), fill=CY)
        y += 26
    y += 8
    for t in ("目は 白目 + 黒目 + 上まぶたと下の隈の 2〜3 色の画素で、細めた目 (まぶたが目の上半分に掛かる)。眉は内側がわずかに下がる。鼻は小さな三角錐。",
              "髭は顎まわりの 2x2 ほどのまばらな画素。光る画素はなし (目は光らない)。背景色は 4〜5 色の階調 + 手で置いた 2x2 のディザ。"):
        d.text((X0, y), t, font=F(15), fill=GR)
        y += 24
    cv = cv.crop((0, 0, W, y + 14))
    out = OUT / "lp_isana.png"
    cv.save(out)
    print("saved", out, cv.size)
    return tri


if __name__ == "__main__":
    main()
