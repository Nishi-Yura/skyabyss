# -*- coding: utf-8 -*-
"""UI 直し 2 の確認シート (標の大きさ・まとめ方・文字のはみ出しの直し)。ゲームは立てず、標の形そのものと検査の結果から描く。

使い方: python tools/sheets/ui_fix2_sheet.py
出力: docs/review/previews/ui_fix2_sheet.png
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../audit',)]
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import check_text_fit as fit  # noqa: E402

OUT = ROOT / "docs/review/previews/ui_fix2_sheet.png"
FONT = "C:/Windows/Fonts/meiryo.ttc"
BG, PANEL, INK, DIM = (22, 26, 33), (34, 40, 50), (232, 236, 240), (140, 152, 164)
GOLD, TEAL, DARK = (255, 216, 102), (127, 224, 216), (5, 6, 10)
DIAMOND = ["     #     ", "    ###    ", "   #####   ", "  #######  ", " ######### ", "###########",
           " ######### ", "  #######  ", "   #####   ", "    ###    ", "     #     "]


def f(n):
    return ImageFont.truetype(FONT, n)


def diamond(d, cx, cy, rgb, k, px=4):
    """11x11 の形を k 倍、1 画素 px のドットで描く (縁取りつき)"""
    size = 11
    s = px * k
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for r, line in enumerate(DIAMOND):
                for c, ch in enumerate(line):
                    if ch == "#":
                        x = cx + (c - size / 2) * s + dx * s * 0.5
                        y = cy + (r - size / 2) * s + dy * s * 0.5
                        d.rectangle([x, y, x + s, y + s], fill=DARK)
    for r, line in enumerate(DIAMOND):
        for c, ch in enumerate(line):
            if ch == "#":
                x = cx + (c - size / 2) * s
                y = cy + (r - size / 2) * s
                d.rectangle([x, y, x + s, y + s], fill=rgb)


def side_dot(d, cx, cy, px=4):
    d.rectangle([cx - 2.5 * px, cy - 2.5 * px, cx + 2.5 * px, cy + 2.5 * px], fill=DARK)
    d.rectangle([cx - 1.5 * px, cy - 1.5 * px, cx + 1.5 * px, cy + 1.5 * px], fill=TEAL)
    d.rectangle([cx - 0.5 * px, cy - 0.5 * px, cx + 0.5 * px, cy + 0.5 * px], fill=DARK)


def main():
    SHIFT = 330
    img = Image.new("RGB", (1100, 760 + SHIFT + 90), BG)
    d = ImageDraw.Draw(img)
    d.text((20, 12), "UI 直し 2: K の技の画面 / 標 / 文字のはみ出し / キー", font=f(22), fill=INK)
    shots = ROOT / "run/shots/ui_fix2/shots/ui_fix2"
    panels = [("ui2_tree_s3", "木のページ (8 本でも下の字にかからない)"), ("ui2_build_s3", "構え・鍵石のページ (タグの凡例つき)"), ("ui2_ext_s3", "変異・紋のページ (下の列が切れない)")]
    for i, (name, cap) in enumerate(panels):
        fp = shots / (name + ".png")
        if fp.exists():
            im = Image.open(fp).convert("RGB").crop((60, 30, 1220, 650)).resize((356, 190), Image.LANCZOS)
            img.paste(im, (20 + i * 366, 50))
        d.text((20 + i * 366, 244), cap, font=f(12), fill=DIM)
    d.text((20, 266), "ページの見出しを上の帯の右に 3 つ並べ (いまのページは金の縁)、ページ送りのボタンをやめた。木のつまみは木の数で詰める。紋の盤は下の列を上げた。", font=f(12), fill=INK)
    d.text((20, 288), "キーの既定: 構え . / 技の枠 R G V Z , / 護符 H / 控え帳 B / 状態 J / 技 K / クエスト U / 旅嚢 I・使う Y / 仲間 O / 合図 M / 知らせ N / 道しるべ ;  (重なり 0、バニラとも重ならない)", font=f(12), fill=GOLD)
    rest = Image.new("RGB", (1100, 760), BG)
    d = ImageDraw.Draw(rest)
    # --- 標
    d.rounded_rectangle([20, 52, 1080, 292], 8, fill=PANEL)
    d.text((36, 62), "目的の標 (GUI 1 画素 = 4 画素の拡大図)", font=f(14), fill=DIM)
    cells = [("これまで: 金 1.0 倍 / 青緑 0.8 倍", [(GOLD, 1.0, 110), (TEAL, 0.8, 250)]),
             ("いま: 金 0.7 倍 / 青緑 0.56 倍", [(GOLD, 0.7, 440), (TEAL, 0.56, 560)])]
    for label, items in cells:
        pass
    d.text((36, 92), "これまで: 金 1.0 倍 / 青緑 0.8 倍", font=f(14), fill=INK)
    diamond(d, 110, 170, GOLD, 1.0)
    diamond(d, 230, 170, TEAL, 0.8)
    d.text((36, 92 + 130), "水色と金が同じ場所に出ていた", font=f(12), fill=DIM)
    d.text((340, 92), "いま: 0.7 倍 / 0.56 倍 (約 30% 小)", font=f(14), fill=INK)
    diamond(d, 400, 170, GOLD, 0.7)
    diamond(d, 500, 170, TEAL, 0.56)
    d.text((340, 222), "別々の場所なら、2 つ出る", font=f(12), fill=DIM)
    d.text((620, 92), "同じ・近い (3 ブロック / 16 画素)", font=f(14), fill=INK)
    diamond(d, 700, 170, GOLD, 0.7)
    side_dot(d, 700 + 6 * 0.7 * 4 + 8, 170 - 6 * 0.7 * 4 - 4)
    d.text((660, 222), "1 つにまとめる: 金 + 右上に小さな青緑の輪", font=f(12), fill=DIM)
    d.text((620, 250), "メインがなければ、サブはふつうに青緑で出る", font=f(12), fill=DIM)
    diamond(d, 1000, 170, TEAL, 0.56)
    # --- 検査
    d.rounded_rectangle([20, 308, 1080, 740], 8, fill=PANEL)
    problems, checked = fit.check_all()
    d.text((36, 318), "文字の検査 (tools/audit/check_text_fit.py): 日本語・英語 × GUI 倍率 2・3・4 × 画面・HUD の箱", font=f(14), fill=INK)
    d.text((36, 344), "検査 %d 件 / 失敗 %d 件。直した主な文 (日本語は幅に収まっていた箇所も、英語が箱を越えていた)。" % (checked, len(problems)), font=f(13), fill=GOLD)
    rows = [
        ("天職の画面「あとで」ボタン (幅 104)", "Later (choose from the K screen)", "Later"),
        ("天職の決定ボタン (幅 70)", "Press again to decide", "Press again / もう一度押す"),
        ("旅嚢の札の説明 (152 で切れていた)", "Right-click: take off the card (back to automatic)", "Right-click: unpin"),
        ("記録帳の値 (114 で切れていた)", "見つけた ... / 全 ... （終えた ...）", "見つけた ... / 全 ..."),
        ("クエスト帳の鍵つきの行 (112)", "After stilling the thunder wing's heart", "After thunder wing"),
        ("報酬の札の必要レベル (カード幅)", "Requires Lv 99 (now Lv 99)", "Needs Lv 99 (you: 99)"),
        ("鍛冶の費用 (132 を越えて右へ)", "結晶 壊す.. ／残す.. （手持ち ..）", "2 行に折り返す"),
        ("技の刻印の名前 (箱 約 42)", "Pierce Through など 78 px", "0.7 倍まで縮め、足りなければ … (全文は効果欄)"),
        ("心臓が止まった札 (3 行まで)", "The heart of the stone whale has stopped. The great body breathes out at last", "The stone whale's heart has stopped. The great body breathes out"),
    ]
    y = 376
    d.text((36, y), "箱", font=f(12), fill=DIM)
    d.text((330, y), "前", font=f(12), fill=DIM)
    d.text((730, y), "後", font=f(12), fill=DIM)
    y += 22
    for box, before, after in rows:
        d.text((36, y), box, font=f(13), fill=INK)
        d.text((330, y), before[:46], font=f(13), fill=(224, 120, 120))
        d.text((730, y), after[:46], font=f(13), fill=(150, 222, 150))
        y += 26
    d.text((36, y + 10), "知らせ (字幕 3 行・中央 2 行・札 3 行・アクションバー 1 行) も、Java のソースから経路を拾って同じ表で検査する。", font=f(12), fill=DIM)
    d.text((36, y + 32), "天職の自動の案内は、進みが足りた人だけ (始まりの人には、クリエイティブでも出ない)。", font=f(12), fill=DIM)
    img.paste(rest, (0, SHIFT - 20))
    img = img.crop((0, 0, 1100, SHIFT - 20 + 740))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()
