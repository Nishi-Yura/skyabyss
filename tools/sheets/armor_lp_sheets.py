#!/usr/bin/env python3
"""Copy the armor_lp shots to docs/review/previews/ and build one contact sheet per set (armor_lp_<set>_sheet.png).

  python tools/sheets/armor_lp_sheets.py [shots_root]      (default run/shots)
Crops each 1280x720 shot around the figure, so the HUD is left out.
"""
import os
import shutil
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'run', 'shots')
OUT = os.path.join(ROOT, 'docs', 'review', 'previews')
SETS = ['stone_whale', 'tide_shell', 'thunder_wing', 'ember']
CROP = (410, 130, 870, 640)   # player figure region of a 1280x720 shot (460 x 510)
STAND_CROP = (420, 50, 860, 650)   # armor stand rows (440 x 600)
ROWS = [
    ('stands', ['common_front', 'common_back', 'common_q34', 'rare_front', 'rare_back', 'rare_q34']),
    ('stands2', ['legend_front', 'legend_back', 'legend_q34', 'rare5_front', 'rare10_front', 'legend10_q34']),
    ('poses', ['pose_armsup', 'pose_armsside', 'pose_armsback', 'pose_walk', 'legend10_front', 'legend10_back']),
    ('player', ['player_common_idle', 'player_rare_idle', 'player_legend10_idle', 'player_legend10_back', 'player_legend10_walk', 'player_legend10_attack']),
    ('player2', ['player_common_crouch', 'player_rare_crouch', 'player_legend10_crouch', 'player_common_walk', 'player_rare_walk', 'player_rare_attack']),
    ('player3', ['player_riding', 'player_riding_back', 'pose_armsside45', 'pose_armsup', 'player_legend10_walk2', 'player_rare_idle']),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for s in SETS:
        src = os.path.join(SHOTS, f'armor_lp_{s}', 'shots', f'armor_lp_{s}')
        if not os.path.isdir(src):
            print('missing', src)
            continue
        for f in os.listdir(src):
            if f.endswith('.png'):
                shutil.copy(os.path.join(src, f), os.path.join(OUT, f))
        cols = 6
        tw, th = 300, 360
        sheet = Image.new('RGB', (cols * tw, len(ROWS) * (th + 14)), (24, 24, 28))
        d = ImageDraw.Draw(sheet)
        for r, (_, names) in enumerate(ROWS):
            for c, n in enumerate(names):
                p = os.path.join(src, f'armor_lp_{s}_{n}.png')
                if not os.path.exists(p):
                    continue
                im = Image.open(p).convert('RGB').crop(STAND_CROP if r < 3 or n.startswith('pose') else CROP).resize((tw, th), Image.LANCZOS)
                sheet.paste(im, (c * tw, r * (th + 14) + 14))
                d.text((c * tw + 4, r * (th + 14) + 1), n, fill=(230, 230, 230))
        path = os.path.join(OUT, f'armor_lp_{s}_sheet.png')
        sheet.save(path, optimize=True)
        print(path, sheet.size)


if __name__ == '__main__':
    main()
