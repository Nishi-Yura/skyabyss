"""雷翼の雛 (young_thunder_wing) 低ポリ 3 案: メッシュ書き出し + 案ごとの回転シート + 比較 + 説明 json。

実行: python tools/rough/lowpoly/young_thunder_wing/sheet.py [a b c]   (省略で 3 案すべて + 比較)
出力: tools/rough/lowpoly/young_thunder_wing/out/{a,b,c}/ (young_thunder_wing_<k>_mesh.json・_atlas.png・_glowmask.png)
      tools/rough/lowpoly/young_thunder_wing/out/report_<k>.json (三角形・開いた辺・鞍の高さ)
      docs/review/young_roughs/thunder_wing_<k>.png (8 方向 + 乗り手の上・横 + move・appear の途中)
      docs/review/young_roughs/thunder_wing_compare.png、thunder_wing_desc.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
F = voxel.font
TITLE = "雷翼の雛"
DESC = {
    "a": "灰青の産毛でふくらんだ丸い雛。生えかけの翼に、金に光る雷の羽軸が数本だけ混じる。"
         "背は白い産毛の座布団の鞍で、縁を立てた羽根の柵と産毛の枕が囲む。いちばん「雛」らしく、やわらかい。",
    "b": "細身の若い猛禽。長い初列風切を指のように開き、翼の上を稲妻の筋が走る。"
         "背は骨の肋で腹へ締めた白い骨の鞍台で、羽軸の手すりと銅の柱がつく。頭に成体と同じ銅の避雷針の冠。いちばん速く、成体に近い。",
    "c": "ずんぐり丸い雛で、翼は短い。腹のまわりに浮かぶ雷の輪 (青白く光る輪・稲妻の歯・銅の節) が体を運ぶ。"
         "背は産毛の巣の鞍で、ふくらんだ縁が丸く囲む。額に光る稲妻の巻き毛。遠目に輪で見分けがつく。",
}
YAWS = [(180, "正面"), (225, "斜め前 45°"), (270, "横 90°"), (315, "斜め後ろ 135°"), (0, "背面 180°"), (45, "斜め後ろ 225°"),
        (90, "横 270°"), (135, "斜め前 315°")]
PW, PH = 420, 300


def labeled_grid(ims, labels, cols):
    rows = (len(ims) + cols - 1) // cols
    W = Image.new("RGB", (PW * cols, (PH + 28) * rows), (16, 16, 22))
    d = ImageDraw.Draw(W)
    for i, (im, lb) in enumerate(zip(ims, labels)):
        x, y = PW * (i % cols), (PH + 28) * (i // cols)
        W.paste(im, (x, y))
        d.text((x + 8, y + PH + 4), lb, font=F(16), fill=CY)
    return W


def span_scale(r, w=PW - 20):
    lo = np.min([f.verts.min(0) for f in r["body"]], 0)
    hi = np.max([f.verts.max(0) for f in r["body"]], 0)
    return w / max(hi[0] - lo[0], hi[2] - lo[2])


def span_len(r):
    lo = np.min([f.verts.min(0) for f in r["body"]], 0)
    hi = np.max([f.verts.max(0) for f in r["body"]], 0)
    return hi[2] - lo[2]


def option_sheet(k, r, mod, rep):
    sc = span_scale(r)
    cen = (0, 30, 0)
    turn = [on_bg(render_pose(r, {}, y, 15, (PW, PH), cen, sc)) for y, _ in YAWS]
    g1 = labeled_grid(turn, [n for _, n in YAWS], 4)
    scb = (PH - 30) / 200.0 * 1.0
    rid = riders()
    bot = [on_bg(render_pose(r, {}, 180, 89.9, (PW, PH), (0, 36, 0), min(sc * 1.25, (PH - 24) / span_len(r)), rid)),
           on_bg(render_pose(r, {}, 270, 4, (PW, PH), (0, 34, 0), (PW - 20) / 200.0, riders())),
           on_bg(render_pose(r, G.pose_at("move", 0.3), 212, 15, (PW, PH), cen, sc)),
           on_bg(render_pose(r, G.pose_at("appear", 0.85), 212, 15, (PW, PH), cen, sc))]
    g2 = labeled_grid(bot, ["上から (座る 6 人: 2 列 × 3)", "横から (座る 6 人。鞍の高さ %.2f ブロック)" % (SEAT_Y / 16),
                            "move (羽ばたき 0.3 秒)", "appear (現れる途中 0.85 秒)"], 4)
    head = 78
    W = Image.new("RGB", (g1.width, head + g1.height + 36 + g2.height), (16, 16, 22))
    d = ImageDraw.Draw(W)
    d.text((14, 8), "%s (young_thunder_wing) — %s" % (TITLE, mod.NAME), font=F(24), fill=WH)
    d.text((14, 42), "%d 三角形・開いた辺 %d・アトラス %d・鞍の上面 %.2f ブロック (6 席 x=±1, z=-2/0/+2)・大きさ 幅 %.1f × 長さ %.1f × 高さ %.1f ブロック"
           % (rep["tris"], rep["open_edges"], rep["atlas"], rep["seat_max"] / 16, rep["hi"][0] - rep["lo"][0],
              rep["hi"][2] - rep["lo"][2], rep["hi"][1] - rep["lo"][1]), font=F(15), fill=GR)
    W.paste(g1, (0, head))
    d.text((14, head + g1.height + 8), "乗り手と動き", font=F(18), fill=WH)
    W.paste(g2, (0, head + g1.height + 36))
    return W


def main():
    only_cmp = "compare" in sys.argv[1:]
    keys = [a for a in sys.argv[1:] if a in ("a", "b", "c")] or list("abc")
    REVIEW.mkdir(parents=True, exist_ok=True)
    opts = {}
    for k in keys:
        r, mod = get_option(k)
        rep = report(r)
        (OUT / f"report_{k}.json").write_text(json.dumps(rep, ensure_ascii=False, indent=1), encoding="utf-8")
        print(k, rep)
        if not only_cmp:
            option_sheet(k, r, mod, rep).save(REVIEW / f"thunder_wing_{k}.png")
        opts[k] = (r, mod)
    if len(opts) == 3:
        sc = min(span_scale(opts[k][0], 540) for k in "abc")   # 3 案を同じ縮尺で (大きさを比べられるように)
        ims = [on_bg(render_pose(opts[k][0], {}, 212, 15, (560, 400), (0, 30, 0), sc)) for k in "abc"]
        W = Image.new("RGB", (560 * 3, 400 + 34), (16, 16, 22))
        d = ImageDraw.Draw(W)
        for i, k in enumerate("abc"):
            W.paste(ims[i], (560 * i, 0))
            d.text((560 * i + 10, 406), opts[k][1].NAME, font=F(18), fill=CY)
        W.save(REVIEW / "thunder_wing_compare.png")
        desc = {"title": TITLE, **{k: {"title": opts[k][1].NAME, "desc": DESC[k]} for k in "abc"}}
        (REVIEW / "thunder_wing_desc.json").write_text(json.dumps(desc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
