#!/usr/bin/env python3
"""四章の備えの品 (docs/design/21 の 1.2・6.1) のレシピ・文・絵を入れる。灰歩きの旅装 4 部位と灰焼きのパン、煤の刃の効き目の絵。

  python -I tools/rough/lowpoly/ch4/armor_ch4.py     # 旅装のラフ 3 案 (先に)
  python -I tools/rough/lowpoly/ch4/icons_ch4.py     # 小物のラフ 3 案ずつ (先に。パンと煤の刃の絵もここ)
  PYTHONIOENCODING=utf-8 python -I tools/gen_ch4_gear.py

書くもの:
  data/skyabyss/recipe/ash_walking_{helmet,chestplate,leggings,boots}.json, ash_baked_bread.json
      (旅の覚書 (四章) に教わるまで作れない = guide/RecipeLock。ID は story/ch4/Ch4Setup.RECIPES)
  assets/skyabyss/lowpoly/armor/ash_walking/<部位>.json               メッシュ (水密に閉じる。階級はアンコモン = 無印)
  assets/skyabyss/textures/lowpoly/armor/ash_walking/<部位>.png と _glowmask.png (熾の留め金)
  assets/skyabyss/textures/item/ash_walking_<部位>.png + models/item/ash_walking_<部位>.json   インベントリのアイコン
  assets/skyabyss/textures/item/ash_baked_bread.png + models/item/ash_baked_bread.json
  assets/skyabyss/textures/mob_effect/soot_whetted.png                 煤の刃の効き目の絵
  assets/skyabyss_ch4/lang/{ja_jp,en_us}.json                          名前・説明・知らせ・備えの手段の名前 (四章の文に混ぜる)
  tools/sources/ash_walking/ ・ ash_baked_bread/                       3 案の元絵 (選ばなかった案の控え)

旅装の素材の合計 (4 部位): 煤鉄 14・灰麻 10・雷翼の羽軸 4 (21 の 6.1 の備え 1 と同じ数)。
案は CHOICE で選ぶ (仮の選定: 旅装 A 煤の前掛け、パン A 灰をかぶった丸パン)。何度流しても同じ。
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mesh_close  # noqa: E402

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'skyabyss')
SRC = os.path.join(ROOT, 'docs', 'review', 'ch4_roughs', 'items')
SOURCES = os.path.join(ROOT, 'tools', 'sources')
PIECES = ['helmet', 'chestplate', 'leggings', 'boots']
CHOICE = {'ash_walking': 'a', 'ash_baked_bread': 'a'}

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


I = {'item': 'skyabyss:soot_iron'}
H = {'item': 'skyabyss:ash_hemp'}
S = {'item': 'skyabyss:thunder_wing_shard'}


def shaped(name, pattern, key, result, count=1, category='equipment'):
    write_json(os.path.join(RES, 'data/skyabyss/recipe/%s.json' % name), {
        'type': 'minecraft:crafting_shaped', 'category': category, 'pattern': pattern, 'key': key,
        'result': {'id': 'skyabyss:' + result, 'count': count}})


def recipes():
    # 煤鉄 I・灰麻 H・雷翼の羽軸 S。4 部位の合計: I14 H10 S4
    shaped('ash_walking_helmet', ['IHI', 'H H', ' S '], {'I': I, 'H': H, 'S': S}, 'ash_walking_helmet')           # I2 H3 S1
    shaped('ash_walking_chestplate', ['ISI', 'HIH', 'III'], {'I': I, 'H': H, 'S': S}, 'ash_walking_chestplate')     # I6 H2 S1
    shaped('ash_walking_leggings', ['HSH', 'I I', 'H H'], {'I': I, 'H': H, 'S': S}, 'ash_walking_leggings')         # H4 I2 S1
    shaped('ash_walking_boots', ['ISI', 'IHI'], {'I': I, 'H': H, 'S': S}, 'ash_walking_boots')                     # I4 H1 S1
    # 灰焼きのパン: 小麦 3 + 灰麻 1 -> 2 つ (灰の上で焼く。かまどは要らない)
    write_json(os.path.join(RES, 'data/skyabyss/recipe/ash_baked_bread.json'), {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'item': 'minecraft:wheat'}] * 3 + [H], 'result': {'id': 'skyabyss:ash_baked_bread', 'count': 2}})


def texts():
    put('gear.skyabyss.origin.ash_walking', '灰歩きの旅装', 'Ash Walking Gear')
    put('item.skyabyss.ash_walking_armor.desc', '煤鉄の板を灰麻で綴じた旅の装い。熱が体に溜まりにくい（余燼の背の熱のたまり 一部位 −2.5%、一式で −10%）',
        'Travel gear of soot iron plates laced with ash hemp. Heat builds up more slowly (heat on the ember\'s back −2.5% per piece, −10% for the set)')
    for p, nj, ne, sj, se in (('helmet', '灰歩きの頭巾', 'Ash Walking Hood', '「灰の降る日は、口を覆え。」', '"On days the ash falls, cover your mouth."'),
                              ('chestplate', '灰歩きの上着', 'Ash Walking Coat', '「火の前に立つ者は、まず前を守る。」', '"Whoever stands before a fire guards their front first."'),
                              ('leggings', '灰歩きの裾', 'Ash Walking Trousers', '「灰は膝から冷える。」', '"Ash goes cold from the knees."'),
                              ('boots', '灰歩きの脚絆', 'Ash Walking Gaiters', '「温かい地面ほど、深く沈む。」', '"The warmer the ground, the deeper you sink."')):
        put('item.skyabyss.ash_walking_%s' % p, nj, ne)
        put('item.skyabyss.ash_walking_%s.story' % p, sj, se)
    put('item.skyabyss.ash_baked_bread', '灰焼きのパン', 'Ash-Baked Bread')
    put('item.skyabyss.ash_baked_bread.desc', '灰の上で焼いた固いパン。腹が落ち着くと、体の芯の熱も引く（吸収 II 60 秒・余燼の背の熱 −15）',
        'Hard bread baked on hot ash. A full stomach settles the heat in your core too (Absorption II 60 s, −15 heat on the ember\'s back)')
    put('effect.skyabyss.soot_whetted', '煤の刃', 'Soot Blade')
    put('message.skyabyss.soot_whetstone.used', '刃を研いだ。煤の黒い筋が、刃に残る。', 'You hone the blade. A black streak of soot stays on the edge.')
    put('message.skyabyss.readiness.remedy.name.ash_walking', '灰歩きの旅装', 'Ash Walking Gear')
    put('message.skyabyss.readiness.remedy.name.soot_whetstone', '煤の砥石', 'Soot Whetstone')
    put('message.skyabyss.readiness.remedy.name.ash_baked_bread', '灰焼きのパン', 'Ash-Baked Bread')


def item_model(name):
    write_json(os.path.join(ASSETS, 'models', 'item', name + '.json'),
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'skyabyss:item/' + name}})


def armor():
    o = CHOICE['ash_walking']
    mesh_dir = os.path.join(ASSETS, 'lowpoly', 'armor', 'ash_walking')
    tex_dir = os.path.join(ASSETS, 'textures', 'lowpoly', 'armor', 'ash_walking')
    os.makedirs(mesh_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    tris = 0
    for p in PIECES:
        base = os.path.join(SRC, 'armor_ch4', o)
        d = json.load(open(os.path.join(base, p + '_mesh.json'), encoding='utf-8'))
        d.pop('triangle_count', None)
        d.pop('glowmask', None)
        d['texture'] = 'armor/ash_walking/' + p
        d['socket'] = False
        for b in d['bones'].values():
            b.pop('triangle_count', None)
        tris += sum(len(b['triangles']) for b in d['bones'].values())
        d, _ = mesh_close.close_mesh(d)
        with open(os.path.join(mesh_dir, p + '.json'), 'w', encoding='utf-8') as f:
            json.dump(d, f, separators=(',', ':'))
        img = Image.open(os.path.join(base, p + '_atlas.png')).convert('RGBA')
        mask = np.array(Image.open(os.path.join(base, p + '_glowmask.png')).convert('RGBA'))[..., 3] > 0
        if not mask.any():
            sys.exit(p + ': no glow pixels (GeckoLib crashes on an empty glowmask)')
        glow = np.array(img)
        glow[~mask] = 0
        img.save(os.path.join(tex_dir, p + '.png'), optimize=True)
        Image.fromarray(glow, 'RGBA').save(os.path.join(tex_dir, p + '_glowmask.png'), optimize=True)
        shutil.copyfile(os.path.join(SRC, 'armor_ch4', 'icons', '%s_%s_32.png' % (o, p)), os.path.join(ASSETS, 'textures', 'item', 'ash_walking_%s.png' % p))
        item_model('ash_walking_' + p)
    keep = os.path.join(SOURCES, 'ash_walking')
    os.makedirs(keep, exist_ok=True)
    for opt in 'abc':
        for p in PIECES:
            shutil.copyfile(os.path.join(SRC, 'armor_ch4', 'icons', '%s_%s_32.png' % (opt, p)), os.path.join(keep, 'rough_%s_%s.png' % (opt, p)))
    shutil.copyfile(os.path.join(SRC, 'armor_ch4.png'), os.path.join(keep, 'sheet.png'))
    return tris


def bread():
    o = CHOICE['ash_baked_bread']
    shutil.copyfile(os.path.join(SRC, 'icons', 'ash_baked_bread_%s_32.png' % o), os.path.join(ASSETS, 'textures', 'item', 'ash_baked_bread.png'))
    item_model('ash_baked_bread')
    keep = os.path.join(SOURCES, 'ash_baked_bread')
    os.makedirs(keep, exist_ok=True)
    for opt in 'abc':
        shutil.copyfile(os.path.join(SRC, 'icons', 'ash_baked_bread_%s_32.png' % opt), os.path.join(keep, 'rough_%s.png' % opt))
    eff = os.path.join(ASSETS, 'textures', 'mob_effect')
    os.makedirs(eff, exist_ok=True)
    shutil.copyfile(os.path.join(SRC, 'icons', 'soot_whetted_18.png'), os.path.join(eff, 'soot_whetted.png'))


def main():
    recipes()
    texts()
    tris = armor()
    bread()
    for code, table in (('ja_jp', ja), ('en_us', en)):
        path = os.path.join(RES, 'assets/skyabyss_ch4/lang/%s.json' % code)
        with open(path, encoding='utf-8') as f:
            merged = json.load(f)
        merged.update(table)
        write_json(path, dict(sorted(merged.items())))
    print('ash walking armor (%s): 4 meshes (%d triangles), 4 icons; bread (%s); soot blade icon; %d texts' % (CHOICE['ash_walking'], tris, CHOICE['ash_baked_bread'], len(ja)))


if __name__ == '__main__':
    main()
