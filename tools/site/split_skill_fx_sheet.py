"""技の見た目の台紙 (tools/sheets/skill_fx_sheet.py が作る skill_fx_sheet.png) から、技ごとのコマを切り出す。

使い方 (リポジトリ直下で):
  python tools/site/split_skill_fx_sheet.py docs/review/previews/skill_fx_sheet.png
出力: tools/sources/site/skill_fx/<技>_{release,impact,linger}.png
  release = 三人称 放ち（3p_b）、impact = 三人称 当たり（3p_c）、linger = 三人称 残り（3p_d）。
  一人称の列（1p_c）は切らない。

台紙の配置は tools/sheets/skill_fx_sheet.py の LABEL_W / HEAD_H / CELL_W / CELL_H と COLS に合わせてある。
台紙の作り方を変えたら、ここも直すこと。原寸の撮影（run/shots/skill_fx/）を使えるなら、
そちらを同じ名前で tools/sources/site/skill_fx/ に置く方が綺麗になる（gen_skill_fx.py は同じ名前で読む）。
"""
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "tools", "sources", "site", "skill_fx")

# 台紙の行の並び（tools/sheets/skill_fx_sheet.py の SKILLS と同じ順）
SKILLS = [
    "titan_cleave", "rock_call", "whale_sweep", "lunge", "backstep", "wind_rend", "ward_song", "ebb_tide",
    "cycle_song", "temper", "ember_hammer", "ash_scatter", "cloud_pierce", "bone_trap", "sky_rain", "signal",
    "dust_charge", "steam_bell", "cinder_volley", "deep_pull", "deep_echo", "deep_maw", "echo_strike", "grave_bell",
    "final_light",
]
# 台紙の列（左から。台紙の名の末尾 → 出すファイルの段）。一人称の列は使わない
COLUMNS = [("3p_b", "release"), ("3p_c", "impact"), ("3p_d", "linger")]
COL_INDEX = {"3p_b": 0, "3p_c": 1, "3p_d": 2}
LABEL_W = 210   # 左の技名の欄
HEAD_H = 40     # 上の見出しの欄
CELL_W, CELL_H = 336, 189
GAP = 8         # 台紙の枠の余白（縦・横の終わり）


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    sheet = Image.open(sys.argv[1]).convert("RGB")
    os.makedirs(OUT, exist_ok=True)
    expect_h = HEAD_H + CELL_H * len(SKILLS) + GAP
    expect_w = LABEL_W + CELL_W * 4 + GAP
    if sheet.height < expect_h or sheet.width < expect_w:
        print("台紙の大きさが合いません: %dx%d（想定 %dx%d 以上）" % (sheet.width, sheet.height, expect_w, expect_h))
        return 1
    n = 0
    for r, skill in enumerate(SKILLS):
        y0 = HEAD_H + r * CELL_H
        for suffix, phase in COLUMNS:
            x0 = LABEL_W + COL_INDEX[suffix] * CELL_W
            cell = sheet.crop((x0, y0, x0 + CELL_W, y0 + CELL_H))
            cell.save(os.path.join(OUT, "%s_%s.png" % (skill, phase)))
            n += 1
    print("wrote", n, "frames to", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
