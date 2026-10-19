"""潮殻の稚貝 (young_tide_shell) 低ポリ 3 案: メッシュ書き出し + 案ごとの回転台シート + 比較 + 説明 JSON。

実行 (リポジトリのどこからでも):
  python tools/rough/lowpoly/young_tide_shell/sheet.py a      案 A のメッシュ・アトラス・シート (b, c も同じ。並べて走らせてよい)
  python tools/rough/lowpoly/young_tide_shell/sheet.py compare 3 案の前 3/4 を並べた比較 + tide_shell_desc.json
  python tools/rough/lowpoly/young_tide_shell/sheet.py         全部
出力: docs/review/young_roughs/young_tide_shell_mesh/<k>/young_tide_shell_<k>_{mesh.json,atlas.png,glowmask.png,front.png,stats.json}
      docs/review/young_roughs/tide_shell_<k>.png、tide_shell_compare.png、tide_shell_desc.json
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
TITLE = "潮殻の稚貝 (ちがい)"
DESC = {
    "a": ("A 平背の子亀",
          "成体の潮殻をそのまま縮めた子海亀。低く広い青緑の甲羅が浅い鉢になり、底の平らな鱗板が 6 人の腰掛け、縁の頂を真珠の粒を連ねた桃色の帯が囲む。"
          "大きな前ひれ 2 枚で水をかき、額の真珠層の杯に歌のかけらが灯る。"),
    "b": ("B 巻き殻の稚貝",
          "生まれたての鸚鵡貝。後ろに虎縞の大きな巻き殻が立ち、前を向いた殻口が 3 列目の背もたれになる。"
          "殻口から前へ開いた体の部屋の床が真珠層の甲板。前は頭巾と触手の冠、横に大きな目で、頭巾の先に歌のかけらが灯る。"),
    "c": ("C 開き貝の稚貝",
          "平たい底の扇貝。下の殻が丸い舟、上の殻は後ろの蝶番から扇のように立って背もたれになる。"
          "殻の間からはみ出す青い外套膜の縁に光る小さな目の粒が並び、甲板の左右に珊瑚の手すり、前の唇に大きな真珠 (歌のかけら)。"),
}
PW, PH = 400, 300
YAWS = [(180 + 45 * k) % 360 for k in range(8)]
YAW_NAMES = ["正面 (0°)", "右前 (45°)", "右横 (90°)", "右後ろ (135°)", "後ろ (180°)", "左後ろ (225°)", "左横 (270°)", "左前 (315°)"]
SC8, CEN8 = 2.05, (0, 24, -4)


def labeled_grid(ims, labels, cols):
    rows = (len(ims) + cols - 1) // cols
    W = Image.new("RGB", (PW * cols, (PH + 28) * rows), (16, 16, 22))
    d = ImageDraw.Draw(W)
    for i, (im, lb) in enumerate(zip(ims, labels)):
        x, y = PW * (i % cols), (PH + 28) * (i // cols)
        W.paste(im, (x, y))
        d.text((x + 8, y + PH + 4), lb, font=F(16), fill=CY)
    return W


def option_sheet(k):
    r, mod = get_option(k)
    st = stats(r)
    yaw_ims = [on_bg(render_pose(r, {}, y, 15, (PW, PH), CEN8, SC8)) for y in YAWS]
    grid8 = labeled_grid(yaw_ims, YAW_NAMES, 4)
    strip = [
        on_bg(render_pose(r, {}, 180, 90, (PW, PH), (0, 24, -4), 1.75, riders=True)),
        on_bg(render_pose(r, {}, 270, 2, (PW, PH), (0, 24, -4), 1.75, riders=True)),
        on_bg(render_pose(r, G.pose_at("move", 0.3), 212, 15, (PW, PH), CEN8, SC8)),
        on_bg(render_pose(r, G.pose_at("appear", 0.8), 212, 15, (PW, PH), CEN8, SC8)),
    ]
    strip_lb = ["真上 (前が上)・6 人の座る位置", "横・人を乗せた高さ (水面 0.4)", "move (海を滑る 0.3 s)", "appear の途中 (0.8 s)"]
    # 横の絵に水面の線
    d2 = ImageDraw.Draw(strip[1])
    wy = int(PH / 2 - (6.4 - 24) * 1.75)
    d2.line([(0, wy), (PW, wy)], fill=(70, 130, 170), width=1)
    grid4 = labeled_grid(strip, strip_lb, 4)
    head = 74
    W = Image.new("RGB", (grid8.width, head + grid8.height + 36 + grid4.height), (16, 16, 22))
    d = ImageDraw.Draw(W)
    d.text((14, 8), f"{TITLE} — {mod.NAME}", font=F(24), fill=WH)
    lo, hi = st["lo"], st["hi"]
    d.text((14, 42), f"三角形 {st['tris']}・アトラス {st['atlas']}²・閉じていない辺 {st['open_edges']}・鞍の上面 y = {st['seat_top'][0]} ブロック・"
                     f"大きさ 幅 {hi[0] - lo[0]:.1f} × 長さ {hi[2] - lo[2]:.1f} × 高さ {hi[1]:.1f} ブロック (前ひれ込み)",
           font=F(15), fill=GR)
    W.paste(grid8, (0, head))
    d.text((14, head + grid8.height + 8), "乗せる・動く (人の影は幅 0.6・座高 1.2 の灰色の箱。座る位置 x ±1.0、z -2 / 0 / +2)", font=F(17), fill=WH)
    W.paste(grid4, (0, head + grid8.height + 36))
    PAGE.mkdir(parents=True, exist_ok=True)
    W.save(PAGE / f"tide_shell_{k}.png")
    on_bg(render_pose(r, {}, 212, 15, (PW, PH), CEN8, SC8)).save(FOLDER / k / f"young_tide_shell_{k}_front.png")
    (FOLDER / k / f"young_tide_shell_{k}_stats.json").write_text(json.dumps(st, ensure_ascii=False, indent=1), encoding="utf-8")
    print(k, st)


def compare():
    ims = [Image.open(FOLDER / k / f"young_tide_shell_{k}_front.png").convert("RGB") for k in "abc"]
    W = Image.new("RGB", (PW * 3, PH + 64), (16, 16, 22))
    d = ImageDraw.Draw(W)
    d.text((10, 6), f"{TITLE} — 低ポリ 3 案 (前 3/4)", font=F(20), fill=WH)
    for i, (im, k) in enumerate(zip(ims, "abc")):
        W.paste(im, (PW * i, 34))
        d.text((PW * i + 8, PH + 38), DESC[k][0], font=F(16), fill=CY)
    W.save(PAGE / "tide_shell_compare.png")
    desc = {"title": TITLE}
    for k in "abc":
        desc[k] = {"title": DESC[k][0], "desc": DESC[k][1]}
    (PAGE / "tide_shell_desc.json").write_text(json.dumps(desc, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def main():
    args = sys.argv[1:] or ["a", "b", "c", "compare"]
    for a in args:
        if a == "compare":
            compare()
        else:
            option_sheet(a)


if __name__ == "__main__":
    main()
