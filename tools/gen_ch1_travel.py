#!/usr/bin/env python3
"""一章の旅装・砥石・糧と薬 (docs/design/21 の 1.2、P5) のレシピ・文・小物の絵を書き出す。

  python tools/gen_ch1_travel.py

書くもの:
  data/skyabyss/recipe/whale_road_{helmet,chestplate,leggings,boots}.json, rock_whetstone.json, road_hardtack.json, moss_salve.json   作業台のレシピ
    (旅の覚書か職人に教わるまで作れない = guide/RecipeLock。ID は story/ch1/Ch1Prep.RECIPES が WayfarerNotes に登録済み)
  assets/skyabyss/textures/item/{rock_whetstone,road_hardtack,moss_salve}.png と models/item/*.json, textures/mob_effect/whetted.png   小物の絵 (32x32 のドット絵。ラフは無いので、既存の素材の色の言葉で描いた)
  assets/skyabyss_ch1/lang/{ja_jp,en_us}.json   名前・説明・知らせ・備えの手段の名前 (既存のキーに混ぜる)
  assets/skyabyss_gear_story/lang は tools/gen_gear_story.py (旅装 4 部位の物語の一行)

旅装の素材の合計 (4 部位): 岩殻片 12・鉄 7・革 3・骨片 2。備えの頼み「身ごしらえ」の集める数 (tools/gen_ch1_prep.py) はこれと同じ。
"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'skyabyss')

ja = {}
en = {}


def put(key, j, e):
    ja[key] = j
    en[key] = e


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write('\n')


S = {'item': 'skyabyss:rock_shell_scrap'}
I = {'item': 'minecraft:iron_ingot'}
L = {'item': 'minecraft:leather'}
B = {'item': 'skyabyss:bone_chip'}


def shaped(name, pattern, key, result, count=1, category='equipment'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern, 'key': key,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def shapeless(name, ingredients, result, count=1, category='misc'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shapeless', 'category': category, 'ingredients': ingredients,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def recipes():
    # 岩殻片 S・鉄 I・革 L・骨片 B。4 部位の合計: S12 I7 L3 B2
    shaped('whale_road_helmet', ['SSS', 'I I'], {'S': S, 'I': I}, 'whale_road_helmet')
    shaped('whale_road_chestplate', ['S S', 'SSS', 'ILI'], {'S': S, 'I': I, 'L': L}, 'whale_road_chestplate')
    shaped('whale_road_leggings', ['SIS', 'L L', 'I I'], {'S': S, 'I': I, 'L': L}, 'whale_road_leggings')
    shaped('whale_road_boots', ['S S', 'B B'], {'S': S, 'B': B}, 'whale_road_boots')
    # 砥石: 岩殻片 2 + 骨片 1 + 火打ち石 1 -> 2 つ
    shapeless('rock_whetstone', [S, S, B, {'item': 'minecraft:flint'}], 'rock_whetstone', 2)
    # 固焼き: 小麦 3 + 骨片 1 -> 3 つ
    shapeless('road_hardtack', [{'item': 'minecraft:wheat'}] * 3 + [B], 'road_hardtack', 3, 'misc')
    # 軟膏: 苔ブロック + 骨片 + 粘土玉 -> 2 つ
    shapeless('moss_salve', [{'item': 'minecraft:moss_block'}, B, {'item': 'minecraft:clay_ball'}], 'moss_salve', 2)


# ---------------------------------------------------------------- 文
def texts():
    put('item.skyabyss.whale_road_helmet', '鯨道の旅帽', 'Whale Road Cap')
    put('item.skyabyss.whale_road_chestplate', '鯨道の旅着', 'Whale Road Coat')
    put('item.skyabyss.whale_road_leggings', '鯨道の脚当て', 'Whale Road Leggings')
    put('item.skyabyss.whale_road_boots', '鯨道の旅靴', 'Whale Road Boots')
    put('item.skyabyss.whale_road_armor.desc', '岩殻の肩当てを縫いつけた旅の装い。踏ん張りが利く', 'Travelling gear with rock-shell pauldrons sewn on. It holds your footing.')
    put('gear.skyabyss.origin.whale_road', '鯨道の旅装', 'Whale Road gear')
    put('item.skyabyss.rock_whetstone', '岩の砥石', 'Rock Whetstone')
    put('item.skyabyss.rock_whetstone.desc', '使うと 20 分のあいだ刃が研がれる（攻撃 +1.5）', 'Use it to hone your blade for twenty minutes (+1.5 attack).')
    put('item.skyabyss.road_hardtack', '道の固焼き', 'Road Hardtack')
    put('item.skyabyss.road_hardtack.desc', '骨の粉を練り込んで固く焼いた旅の糧。60 秒、吸収の護りがつく', 'Travel bread baked hard with bone meal. Grants absorption for sixty seconds.')
    put('item.skyabyss.moss_salve', '苔の軟膏', 'Moss Salve')
    put('item.skyabyss.moss_salve.desc', '鈍足を治し、少しのあいだ傷を癒やす', 'Cures slowness and mends wounds for a short while.')
    put('effect.skyabyss.whetted', '研いだ刃', 'Honed Blade')
    put('message.skyabyss.whetstone.used', '刃を研いだ。二十分のあいだ、振りが利く', 'You honed your blade. It will bite for twenty minutes.')
    put('message.skyabyss.recipe.locked', 'まだ作り方を知らない。旅の覚書を開くか、旅の職人に教わろう', 'You do not know how to make this yet. Open your wayfarer\'s notes, or ask the traveling artisan.')
    put('message.skyabyss.readiness.remedy.name.travel_gear', '鯨道の旅装', 'the Whale Road gear')
    put('message.skyabyss.readiness.remedy.name.whetstone', '岩の砥石', 'a rock whetstone')
    put('message.skyabyss.readiness.remedy.name.hardtack', '道の固焼き', 'road hardtack')
    put('message.skyabyss.readiness.remedy.name.salve', '苔の軟膏', 'moss salve')


# ---------------------------------------------------------------- 小物の絵 (32x32 のドット絵。暗い 1 画素の輪郭・左上の光・限られた色)
def canvas(w=32, h=32):
    return np.zeros((h, w, 4), dtype=np.uint8)


def fill(img, mask, rgb):
    img[mask] = (rgb[0], rgb[1], rgb[2], 255)


def outline(img, rgb=(18, 20, 28)):
    a = img[..., 3] > 0
    pad = np.pad(a, 1)
    near = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    ring = near & ~a
    img[ring] = (rgb[0], rgb[1], rgb[2], 255)


def grid(w=32, h=32):
    y, x = np.mgrid[0:h, 0:w]
    return x, y


def whetstone():
    img = canvas()
    x, y = grid()
    # 斜めの平たい石 (長辺が右上がり)。座標を回して長方形を取る
    u = (x - 16) * 0.8 + (y - 16) * -0.6   # 長辺方向 (右上へ)
    v = (x - 16) * 0.6 + (y - 16) * 0.8    # 短辺方向
    body = (np.abs(u) <= 12.5) & (np.abs(v) <= 5.2)
    fill(img, body, (86, 98, 112))
    fill(img, body & (v < -1.8), (126, 140, 156))     # 上の面 (明るい)
    fill(img, body & (v > 2.8), (56, 66, 80))         # 下の面 (暗い)
    fill(img, body & (np.abs(u) > 11.0), (72, 82, 96))
    # 削れた筋と琥珀の火花
    for k in (-6, -1, 5):
        fill(img, body & (np.abs(u - k) < 0.7) & (np.abs(v) < 3.2), (66, 76, 90))
    fill(img, body & (np.abs(u + 8) < 1.2) & (np.abs(v + 3) < 1.2), (216, 150, 52))
    spark = ((x - 24) ** 2 + (y - 9) ** 2) <= 2
    fill(img, spark, (240, 190, 90))
    outline(img)
    return img


def hardtack():
    img = canvas()
    x, y = grid()
    r2 = (x - 16) ** 2 + (y - 17) ** 2
    disc = r2 <= 12.5 ** 2
    fill(img, disc, (196, 150, 96))
    fill(img, disc & ((x - 14) ** 2 + (y - 15) ** 2 > 8.5 ** 2) & (x + y > 33), (146, 104, 62))   # 右下の焼き色
    fill(img, disc & (r2 > 10.8 ** 2), (160, 116, 70))                                          # 縁の焼き色
    fill(img, disc & (x + y < 24) & (r2 > 6 ** 2), (222, 182, 124))                           # 左上の光
    # ひび割れと骨粉の粒
    for (cx, cy) in ((11, 14), (15, 12), (19, 16), (14, 21), (21, 22), (9, 20)):
        img[cy, cx] = (240, 232, 208, 255)
    for (x0, y0, x1, y1) in ((10, 18, 17, 21), (16, 13, 22, 17)):
        for t in range(0, 8):
            px = x0 + (x1 - x0) * t // 7
            py = y0 + (y1 - y0) * t // 7
            if disc[py, px]:
                img[py, px] = (112, 78, 44, 255)
    outline(img)
    return img


def salve():
    img = canvas()
    x, y = grid()
    # 浅い鉢 (灰青の殻の半分)。上から見て少し斜め
    rim = ((x - 16) / 12.5) ** 2 + ((y - 17) / 9.5) ** 2 <= 1
    fill(img, rim, (92, 104, 118))
    fill(img, rim & (((x - 16) / 12.5) ** 2 + ((y - 17) / 9.5) ** 2 > 0.72), (62, 72, 86))
    fill(img, rim & (x + y < 26) & (((x - 16) / 12.5) ** 2 + ((y - 17) / 9.5) ** 2 > 0.72), (132, 146, 160))
    inner = ((x - 16) / 10.0) ** 2 + ((y - 16.5) / 7.0) ** 2 <= 1
    fill(img, inner, (72, 110, 56))                       # 苔の軟膏
    fill(img, inner & (y < 15), (110, 154, 78))
    for (cx, cy) in ((12, 15), (17, 13), (20, 17), (14, 19), (22, 14)):
        img[cy, cx] = (170, 206, 110, 255)
    img[16, 18] = (216, 150, 52, 255)                     # 琥珀の粒
    # 厚みの影
    under = ((x - 16) / 12.5) ** 2 + ((y - 20.5) / 9.5) ** 2 <= 1
    fill(img, under & ~rim & (y > 22), (44, 52, 64))
    outline(img)
    return img


def effect_icon():
    img = canvas(18, 18)
    x, y = grid(18, 18)
    u = (x - 9) * 0.8 + (y - 9) * -0.6
    v = (x - 9) * 0.6 + (y - 9) * 0.8
    body = (np.abs(u) <= 7.0) & (np.abs(v) <= 3.2)
    fill(img, body, (112, 128, 148))
    fill(img, body & (v < -1.0), (160, 176, 196))
    fill(img, body & (v > 1.6), (72, 86, 104))
    for (cx, cy) in ((14, 3), (3, 14)):
        img[cy, cx] = (240, 190, 90, 255)
        img[cy, cx + 1 if cx < 17 else cx] = (216, 150, 52, 255)
    outline(img)
    return img


def icons():
    out = os.path.join(ASSETS, 'textures', 'item')
    mdl = os.path.join(ASSETS, 'models', 'item')
    for name, fn in (('rock_whetstone', whetstone), ('road_hardtack', hardtack), ('moss_salve', salve)):
        Image.fromarray(fn(), 'RGBA').save(os.path.join(out, name + '.png'), optimize=True)
        with open(os.path.join(mdl, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'skyabyss:item/' + name}}, f, separators=(',', ':'))
            f.write('\n')
    eff = os.path.join(ASSETS, 'textures', 'mob_effect')
    os.makedirs(eff, exist_ok=True)
    Image.fromarray(effect_icon(), 'RGBA').save(os.path.join(eff, 'whetted.png'), optimize=True)


def main():
    recipes()
    texts()
    icons()
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch1/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update(table)
        write_json(path, dict(sorted(merged.items())))
    print('wrote ch1 travel gear: 7 recipes, 3 item icons + effect icon, %d texts' % len(ja))


if __name__ == '__main__':
    main()
