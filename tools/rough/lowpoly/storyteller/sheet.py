"""語り部 (storyteller) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。
実行: python tools/rough/lowpoly/storyteller/sheet.py
出力: docs/review/lowpoly_roughs/lp_storyteller.png, lp_storyteller/{a,b,c}/, lp_storyteller_manifest.json"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (255, 200, 130), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 頭巾の語り部", "b": "B 毛糸の語り部", "c": "C 鐘守の語り部"}
FIG = (0, 15, 0)
ROWS = [
    ("前", 450, 200, 6, "rest", FIG, 14.0),
    ("前 3/4", 450, 222, 8, "rest", FIG, 14.0),
    ("横 (腰の曲がりと頭巾の奥行き)", 450, 270, 4, "rest", FIG, 14.0),
    ("後ろ 3/4", 450, 28, 8, "rest", FIG, 14.0),
    ("顔のアップ (開口の縁の中に顔がある。やや斜め)", 400, 208, 6, "rest", (0, 21.2, -3), 31.0),
    ("話している・待機 (talk の 14〜22 度、頭は話し相手へ少し向く)", 450, 214, 6, "talk2", FIG, 14.0),
]


def main():
    opts = {k: get_option(k)[0] for k in "abc"}
    tri = {k: tri_count(r["body"]) for k, r in opts.items()}
    print(tri)
    F = voxel.font
    cv = Image.new("RGB", (W, 6000), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((28, 14), "語り部 (storyteller) — 低ポリ非立方体 3 案 + ドット絵テクスチャ (村の鐘のそばの老婆)", font=F(26), fill=WH)
    d.text((28, 50), "骨 11 本 (root[Y 180 度] > body > head > hair・hair2 / shawl / armL / armR / legL / legR / cord) の名前・階層・pivot は今の geo と同じ。"
                     "今のアニメ 3 本 (idle・walk・talk) がそのまま動く。1 ブロック = 32 テクセル、最近傍。", font=F(15), fill=GR)
    d.text((28, 72), "左端が今のゲーム内のモデル (箱 688 個 / 1,981 面)。三角形は本体のみ (武器なし)。NPC の上限は約 800。顔は頭巾の開口の縁の中に収まり、"
                     "目 (淡い青白) だけが光る。", font=F(15), fill=GR)
    y = 104
    for title, ph, yaw, pitch, pose, cen, sc in ROWS:
        d.text((X0, y), title, font=F(20), fill=CY)
        y += 30
        f, n = old_faces(POSES[pose])
        panels = [old_render(f, yaw, pitch, (PW, ph), cen, sc)]
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
    d.text((X0, y), "テクスチャのアトラス (128x128 を 2 倍で表示)", font=F(20), fill=CY)
    y += 30
    for i, k in enumerate("abc", start=1):
        at = Image.fromarray(opts[k]["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        s = 2 if at.width <= 128 else 1
        im = bgc.convert("RGB").resize((at.width * s, at.height * s), Image.NEAREST)
        cv.paste(im, (xs[i], y))
        d.text((xs[i], y + im.height + 4), "%s  アトラス %d" % (NAMES[k], opts[k]["size"]), font=F(15), fill=CY)
    y += 256 + 36
    for k in "abc":
        per = opts[k]["data"]["bones"]
        g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
        d.text((X0, y), "%s:  全身 %d 三角形 (長衣・靴・縄 %d / 肩掛け %d / 顔 %d / 頭巾 %d / 眉・房 %d / 腕 %d)"
               % (NAMES[k], tri[k], g("body", "legL", "legR", "cord"), g("shawl"), g("head"), g("hair2"), g("hair"),
                  g("armL", "armR")), font=F(17), fill=CY)
        y += 26
    y += 8
    for t in ("光る画素は目 (淡い青白) だけ。色は 3〜5 色の階調 + 手で置いた 2x2 のディザ。顔 (目・口・頬) は 2 画素/単位のドットを一つずつ置いた。",
              "3 案とも顔は頭巾の縁の筒の内側に収まり、浮いた板がない。表情はやわらかい笑みで、眉は内側が高く外側が下がる。"):
        d.text((X0, y), t, font=F(15), fill=GR)
        y += 24
    cv = cv.crop((0, 0, W, y + 14))
    out = OUT / "lp_storyteller.png"
    cv.save(out)
    print("saved", out, cv.size)


if __name__ == "__main__":
    main()
