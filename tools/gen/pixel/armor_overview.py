# -*- coding: utf-8 -*-
"""防具の一式のゲーム内の撮影 (armor_shots.py → runShots) を、レア度 → シリーズの順に並べた一覧の絵にする。
  python -X utf8 -I tools/gen/pixel/armor_overview.py <撮影の名前> <出力フォルダ>
run/shots/<撮影の名前>/shots/armor_set_check/<組>.png を読み、レア度ごとに 1 枚 (<出力>/armor_<順>_<レア度>.png) を書く。
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
# (レア度, [(シリーズ, [組 ...]) ...])
ORDER = [
    ('コモン', [('装い', ['wayfarer', 'ironclad'])]),
    ('アンコモン', [('章の旅装', ['whale_road', 'tide_wader', 'peak_crossing', 'ash_walking', 'sky_stone']),
                ('野の帯', ['wild_oilskin', 'wild_belfry', 'wild_sailcloth'])]),
    ('レア', [('巨神', ['stone_whale', 'tide_shell', 'thunder_wing', 'ember', 'abyss_diver', 'moon_walker']),
            ('野の帯', ['wild_oldsoldier', 'wild_lurelight', 'wild_windweave', 'wild_delver', 'wild_copperwire'])]),
    ('エピック', [('野の帯', ['wild_ramfleece', 'wild_ashleather', 'wild_saltplate', 'wild_lampkeeper', 'wild_soot', 'wild_deepwarden'])]),
    ('ヒロイック', [('野の帯', ['wild_fortress', 'wild_hanged', 'wild_stargazer', 'wild_cloudknight'])]),
    ('神器', [('神域', ['divine_bellkeeper', 'divine_mistwalker', 'divine_lampkeeper']),
            ('塔', ['tower_scale', 'tower_bridge', 'tower_halo'])]),
]
CROP = (150, 150, 1130, 690)   # 撮影 1280x720 のうち防具立て 5 体の範囲
W = 980 // 2 * 2
SCALE = 0.62


def main(name, out):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    lang = json.loads((ROOT / 'src/main/resources/assets/skyabyss/lang/ja_jp.json').read_text(encoding='utf-8'))
    font = ImageFont.truetype(str(ROOT / 'tools/sources/fonts/DotGothic16-Regular.ttf'), 22)
    big = ImageFont.truetype(str(ROOT / 'tools/sources/fonts/DotGothic16-Regular.ttf'), 34)
    src = ROOT / 'run/shots' / name / 'shots/armor_set_check'
    cw, ch = int((CROP[2] - CROP[0]) * SCALE), int((CROP[3] - CROP[1]) * SCALE)
    cols = 2
    for ri, (rar, series) in enumerate(ORDER, 1):
        rows = []
        for ser, sets in series:
            rows.append(('h', ser))
            for i in range(0, len(sets), cols):
                rows.append(('s', sets[i:i + cols]))
        H = 70 + sum(44 if k == 'h' else ch + 34 for k, _ in rows)
        sheet = Image.new('RGB', (cw * cols + 30, H), (22, 26, 34))
        dr = ImageDraw.Draw(sheet)
        dr.text((14, 14), f'{ri}. {rar}', font=big, fill=(240, 230, 200))
        y = 70
        for k, v in rows:
            if k == 'h':
                dr.text((14, y + 8), f'― {v}', font=font, fill=(180, 200, 230))
                y += 44
                continue
            for c, s in enumerate(v):
                x = 10 + c * (cw + 10)
                p = src / f'{s}.png'
                if p.exists():
                    im = Image.open(p).convert('RGB').crop(CROP).resize((cw, ch), Image.LANCZOS)
                    sheet.paste(im, (x, y + 30))
                else:
                    dr.text((x + 10, y + 60), '撮影なし', font=font, fill=(255, 120, 120))
                nm = lang.get(f'item.skyabyss.{s}_chestplate', '')
                dr.text((x + 4, y + 4), f'{nm}  ({s})', font=font, fill=(230, 230, 230))
            y += ch + 34
        dst = out / f'armor_{ri}_{rar}.png'
        sheet.save(dst)
        print(dst.as_posix(), sheet.size)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
