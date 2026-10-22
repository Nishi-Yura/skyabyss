"""真珠守り (pearl_keeper) 低ポリ 3 案: メッシュ書き出し + 案ごとの絵 + 比較シート + manifest + 選定ページの絵。

実行: python tools/rough/lowpoly/pearl_keeper/sheet.py
出力: docs/review/lowpoly_roughs/lp_pearl_keeper/{a,b,c}/ (pearl_keeper_<k>_mesh.json・_atlas.png・_glowmask.png・_views.png・_actions.png)
      docs/review/lowpoly_roughs/lp_pearl_keeper.png (比較シート)、docs/review/lowpoly_roughs/lp_pearl_keeper_manifest.json
      docs/review/pearl_keeper/opt_<k>_views.png・opt_<k>_actions.png・compare.png (選定ページの絵)
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "gen" / "world"))
from view import *  # noqa
from PIL import ImageDraw  # noqa
import gen_pearl_keeper_assets as G  # noqa  (view.pose_of が tools を path に入れる。ここでも入れる)

PAGE = ROOT / "docs/review/pearl_keeper"
CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
F = voxel.font
NAMES = {}
DESC = {
    "a": "真珠層の白い巻貝 (青緑の螺旋の帯、鮑のような息の穴の列) を背負う珊瑚色の宿借り。殻の肩の花びらの台に大きな真珠。右の大きいはさみ。いちばん「宿借り」らしい。",
    "b": "細く高い塔の巻貝 (青緑に白い畝と棘) を立てて背負う、脚の長い深い青の宿借り。真珠は殻の正面の白い肋の籠に埋まる。縦に高く、遠くから塔で見分けがつく。",
    "c": "低く広い円い殻 (青緑の石灰に真珠層の螺旋の筋) に珊瑚の林が生え、頂の開いた二枚貝に真珠。下から白い宿借りが覗き、平たい盾のはさみで顔を守る。横に広く重い。",
}
VIEWS3 = [("前 3/4", 212, 14), ("後ろ 3/4", 32, 14), ("横", 270, 6)]
ACTIONS = [("籠もる (shelled)", "shelled", 0.0), ("吹き上げの溜め (updraft 1.0 s)", "updraft", 1.0),
           ("吹き下ろしの溜め (downdraft 1.2 s)", "downdraft", 1.2), ("真珠の礫 (volley 1.0 s)", "volley", 1.0),
           ("突進の転がり (charge 1.1 s)", "charge", 1.1)]
PW, PH = 360, 360


def labeled(ims, labels, title=None):
    top = 34 if title else 0
    W = Image.new("RGB", (PW * len(ims), PH + 30 + top), (16, 16, 22))
    d = ImageDraw.Draw(W)
    if title:
        d.text((10, 6), title, font=F(20), fill=WH)
    for i, (im, lb) in enumerate(zip(ims, labels)):
        W.paste(im, (PW * i, top))
        d.text((PW * i + 8, top + PH + 5), lb, font=F(16), fill=CY)
    return W


def main():
    opts = {}
    for k in "abc":
        r, mod = get_option(k)
        NAMES[k] = mod.NAME
        opts[k] = r
    PAGE.mkdir(parents=True, exist_ok=True)
    rows = []
    tris = {}
    for k, r in opts.items():
        tris[k] = tri_count(r["body"])
        views = [on_bg(render_pose(r, {}, y, p, (PW, PH), CEN, 5.4)) for _, y, p in VIEWS3]
        far = on_bg(render_pose(r, {}, 212, 22, (PW, PH), CEN, 1.5))
        v_img = labeled(views + [far], [n for n, _, _ in VIEWS3] + ["遠く (約 15 ブロック)"],
                        "%s  %d 三角形・アトラス %d" % (NAMES[k], tris[k], r["size"]))
        acts = [on_bg(render_pose(r, G.pose_at(a, t), 212, 14, (PW, PH), (0, 28, -6), 4.6)) for _, a, t in ACTIONS]
        a_img = labeled(acts, [n for n, _, _ in ACTIONS], "%s  技の構え (ゲームのアニメの値)" % NAMES[k])
        d = FOLDER / k
        v_img.save(d / f"pearl_keeper_{k}_views.png")
        a_img.save(d / f"pearl_keeper_{k}_actions.png")
        v_img.save(PAGE / f"opt_{k}_views.png")
        a_img.save(PAGE / f"opt_{k}_actions.png")
        rows += [v_img, a_img]
    W = max(i.width for i in rows)
    head = 70
    sheet = Image.new("RGB", (W, head + sum(i.height + 10 for i in rows)), (16, 16, 22))
    d = ImageDraw.Draw(sheet)
    d.text((14, 10), "真珠守り (pearl_keeper) — 低ポリ非立方体 3 案 + ドット絵 (1 ブロック 32 画素)", font=F(24), fill=WH)
    d.text((14, 42), "骨 14 本は 3 案共通 (root[Y 180 度] > body > shell > pearl / head / claw_r > claw_r_pincer / claw_l / leg_r1-3・leg_l1-3)。"
                     "当たり判定 幅 3.0 × 高さ 3.4。光るのは真珠 (と A の息の穴・目の瞳)。", font=F(15), fill=GR)
    y = head
    for im in rows:
        sheet.paste(im, (0, y))
        y += im.height + 10
    sheet.save(OUT / "lp_pearl_keeper.png")
    # 選定ページ用の小さな並び (前 3/4 だけ 3 案)
    cmp_ims = [on_bg(render_pose(opts[k], {}, 212, 14, (PW, PH), CEN, 5.4)) for k in "abc"]
    labeled(cmp_ims, [NAMES[k] for k in "abc"]).save(PAGE / "compare.png")

    man = {
        "id": "lp_pearl_keeper",
        "short": "真珠守り 低ポリ",
        "kind": "低ポリ 3案",
        "title": "真珠守り (pearl_keeper) — 低ポリ非立方体 + ドット絵 3 案 (潮殻の背・息の間の頂の名づき)",
        "sheets": ["lowpoly_roughs/lp_pearl_keeper.png"],
        "extra": "24 の 5 章 (案 C「息の間」) の山頂の番。大きな宿借りが貝殻に籠もり、息を吐く技 (吹き上げ・吹き下ろし) を使う。"
                 "骨 14 本は 3 案で共通で、アニメ 12 本 (idle・walk・hurt・death・emerge・updraft・downdraft・charge・volley・shell_in・shelled・shell_out) がどの案でも動く。"
                 "三角形 (全身): A %d / B %d / C %d (上限 約 1,500)。アトラス 512×512。光る画素は真珠 (弱点) が主。"
                 "メッシュは lowpoly_roughs/lp_pearl_keeper/{a,b,c}/pearl_keeper_{a,b,c}_mesh.json (+ atlas・glowmask)。"
                 "生成: tools/rough/lowpoly/pearl_keeper/sheet.py。取り込み: python tools/install/install_lowpoly_pearl_keeper.py [a|b|c]。"
                 % (tris["a"], tris["b"], tris["c"]),
        "options": {k: {"name": NAMES[k], "desc": DESC[k]} for k in "abc"},
    }
    (OUT / "lp_pearl_keeper_manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("tris", tris)


if __name__ == "__main__":
    main()
