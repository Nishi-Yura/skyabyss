# -*- coding: utf-8 -*-
"""バニラの画面 (箱の画面・ボタン・ホットバー・ツールチップ・メニューの背景) を mod の絵柄で描き直したリソースパックを作る。

python -I tools/ui/gen_vanilla_skin.py      → src/main/resources/resourcepacks/skyabyss_ui_<a|b|c>/
  a = 暗い板 (mod の画面と同じ: 暗い青灰色の板、骨色の継ぎ目の縁、青緑の菱形の隅)  … 既定 (仮)
  b = 帳面 (羊皮紙の板、焦げ茶の縁、金の隅。伝承の書と同じ)
  c = 黒い石と金 (炭の石の板、金の縁、琥珀の隅。炉の画面と同じ)
バニラの絵はマス目と窓の位置を読むためだけに使う。板・縁・マス目・窓は一から描き、矢印や炎の枠のような小さな印だけ形を mod の色で塗り直す。
テクスチャは 2 倍の細かさ (256 → 512)。見出しの文字の色は assets/skyabyss/ui/skin.json (client/ui/UiSkin が読む)。
"""
from __future__ import annotations

import io
import json
import os
import random
import shutil
import zipfile

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'resourcepacks')
JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
S = 2  # 細かさ

SKINS = {
    'a': dict(name='暗い板 (mod の画面と同じ)', panel=(27, 33, 41), panel2=(23, 28, 35), edge=(201, 191, 166), edge2=(143, 134, 112),
              corner=(95, 199, 192), slot=(15, 18, 24), slot_lo=(8, 10, 13), slot_hi=(42, 49, 59), window=(10, 12, 16), window2=(22, 60, 66),
              ink=(233, 227, 212), ramp=[(10, 12, 16), (42, 49, 59), (120, 126, 128), (201, 191, 166)], label=0xE9E3D4, label_dim=0xA9B2B8,
              button=(36, 44, 54), button_hi=(48, 72, 78), button_edge=(143, 134, 112), button_edge_hi=(95, 199, 192)),
    'b': dict(name='帳面 (羊皮紙)', panel=(222, 207, 170), panel2=(210, 193, 152), edge=(92, 62, 38), edge2=(64, 42, 26),
              corner=(196, 152, 64), slot=(186, 168, 128), slot_lo=(150, 130, 94), slot_hi=(236, 224, 196), window=(58, 44, 32), window2=(96, 76, 52),
              ink=(52, 38, 26), ramp=[(52, 38, 26), (120, 96, 64), (180, 160, 120), (240, 230, 206)], label=0x3A2A1C, label_dim=0x6E5A44,
              button=(200, 182, 140), button_hi=(226, 206, 156), button_edge=(92, 62, 38), button_edge_hi=(196, 152, 64)),
    'c': dict(name='黒い石と金', panel=(36, 34, 31), panel2=(30, 28, 26), edge=(184, 146, 58), edge2=(120, 92, 36),
              corner=(232, 170, 72), slot=(18, 16, 14), slot_lo=(10, 9, 8), slot_hi=(58, 52, 44), window=(12, 11, 10), window2=(70, 46, 20),
              ink=(236, 222, 190), ramp=[(12, 11, 10), (58, 52, 44), (130, 112, 80), (214, 178, 96)], label=0xECDCB8, label_dim=0xB8A27A,
              button=(44, 40, 36), button_hi=(70, 56, 34), button_edge=(120, 92, 36), button_edge_hi=(232, 170, 72)),
}

V_PANEL = (198, 198, 198)
V_SLOT = (139, 139, 139)
V_DARK = (55, 55, 55)
V_LIGHT = (255, 255, 255)


def near(p, c, tol=6):
    return abs(p[0] - c[0]) <= tol and abs(p[1] - c[1]) <= tol and abs(p[2] - c[2]) <= tol


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def ramp(sk, l):
    r = sk['ramp']
    t = min(0.999, max(0.0, l / 255.0)) * (len(r) - 1)
    i = int(t)
    f = t - i
    a, b = r[i], r[min(i + 1, len(r) - 1)]
    return tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3))


# ---------------------------------------------------------------- 読み取り
def find_slots(im):
    """バニラのマス目 (中が 8B8B8B、上と左の縁が 373737、下と右の縁が FFFFFF) の内側の長方形"""
    w, h = im.size
    px = im.load()
    out = []
    seen = set()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            p = px[x, y]
            if p[3] < 255 or not near(p, V_SLOT, 2) or (x, y) in seen:
                continue
            if not (near(px[x - 1, y], V_DARK, 2) and near(px[x, y - 1], V_DARK, 2)):
                continue
            x1 = x
            while x1 + 1 < w and near(px[x1 + 1, y], V_SLOT, 2):
                x1 += 1
            y1 = y
            while y1 + 1 < h and near(px[x, y1 + 1], V_SLOT, 2):
                y1 += 1
            if x1 - x < 6 or y1 - y < 6:
                continue
            for yy in range(y, y1 + 1):
                for xx in range(x, x1 + 1):
                    seen.add((xx, yy))
            out.append((x, y, x1, y1))
    return out


def find_windows(im, color=(0, 0, 0)):
    """黒い窓 (持ち物の人の姿の枠など) の内側の長方形 (8×8 以上)"""
    w, h = im.size
    px = im.load()
    out = []
    seen = set()
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            p = px[x, y]
            if p[3] < 255 or not near(p, color, 4) or (x, y) in seen:
                continue
            if not near(px[x - 1, y], V_DARK, 4) or not near(px[x, y - 1], V_DARK, 4):
                continue
            x1 = x
            while x1 + 1 < w and near(px[x1 + 1, y], color, 4):
                x1 += 1
            y1 = y
            while y1 + 1 < h and near(px[x, y1 + 1], color, 4):
                y1 += 1
            if x1 - x < 7 or y1 - y < 7:
                continue
            for yy in range(y, y1 + 1):
                for xx in range(x, x1 + 1):
                    seen.add((xx, yy))
            out.append((x, y, x1, y1))
    return out


# ---------------------------------------------------------------- 描く
def noise(rng, c, amt=4):
    d = rng.randint(-amt, amt)
    return tuple(max(0, min(255, v + d)) for v in c)


def put(px, x, y, c, a=255):
    px[x, y] = (c[0], c[1], c[2], a)


def fill2(px, x, y, c, a=255):
    for i in range(S):
        for j in range(S):
            put(px, x * S + i, y * S + j, c, a)


def draw_slot(px, sk, x0, y0, x1, y1):
    """マス目 (内側 x0..x1, y0..y1 の 1 倍の座標)。2 倍で、外の 1 段の縁を含めて描く"""
    X0, Y0, X1, Y1 = (x0 - 1) * S, (y0 - 1) * S, (x1 + 2) * S - 1, (y1 + 2) * S - 1
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            edge_tl = x < X0 + 2 or y < Y0 + 2
            edge_br = x > X1 - 2 or y > Y1 - 2
            if edge_tl and not edge_br:
                c = sk['slot_lo']
            elif edge_br and not edge_tl:
                c = sk['slot_hi']
            elif edge_tl and edge_br:
                c = sk['slot']
            else:
                c = sk['slot']
            put(px, x, y, c)


def draw_window(px, sk, x0, y0, x1, y1):
    X0, Y0, X1, Y1 = (x0 - 1) * S, (y0 - 1) * S, (x1 + 2) * S - 1, (y1 + 2) * S - 1
    hgt = max(1, Y1 - Y0)
    for y in range(Y0, Y1 + 1):
        t = (y - Y0) / hgt
        base = tuple(int(sk['window'][k] + (sk['window2'][k] - sk['window'][k]) * max(0.0, t - 0.4) / 0.6) for k in range(3))
        for x in range(X0, X1 + 1):
            if x < X0 + 2 or y < Y0 + 2:
                c = sk['slot_lo']
            elif x > X1 - 2 or y > Y1 - 2:
                c = sk['slot_hi']
            else:
                c = base
            put(px, x, y, c)


def skin_container(src, sk, seed=1):
    rng = random.Random(seed)
    w, h = src.size
    sp = src.load()
    out = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    op = out.load()
    slots = find_slots(src)
    wins = find_windows(src)
    in_slot = set()
    for (x0, y0, x1, y1) in slots + wins:
        for yy in range(y0 - 1, y1 + 2):
            for xx in range(x0 - 1, x1 + 2):
                in_slot.add((xx, yy))
    # 板と縁と印
    for y in range(h):
        for x in range(w):
            p = sp[x, y]
            if p[3] == 0:
                continue
            if (x, y) in in_slot:
                continue
            # 縁: 透明に接する 3 段 (黒・白・灰)
            d = 9
            for r in range(1, 4):
                for (dx, dy) in ((r, 0), (-r, 0), (0, r), (0, -r)):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < w and 0 <= yy < h) or sp[xx, yy][3] == 0:
                        d = min(d, r)
            if d <= 3:
                if d == 1:
                    c = sk['edge2']
                elif d == 2:
                    c = sk['edge']
                else:
                    c = sk['edge2'] if ((x + y) % 7 == 0) else sk['edge']
                fill2(op, x, y, c)
                continue
            if near(p, V_PANEL, 3):
                for i in range(S):
                    for j in range(S):
                        put(op, x * S + i, y * S + j, noise(rng, sk['panel'] if rng.random() > 0.08 else sk['panel2'], 2))
                continue
            # そのほかの印 (矢印・炎の枠・文字欄など): 形はそのまま、色は明るさで mod の色に
            fill2(op, x, y, ramp(sk, lum(p)), p[3])
    for (x0, y0, x1, y1) in slots:
        draw_slot(op, sk, x0, y0, x1, y1)
    for (x0, y0, x1, y1) in wins:
        draw_window(op, sk, x0, y0, x1, y1)
    # 隅の菱形 (板の外の箱の四隅の近く)
    bb = src.getbbox()
    if bb:
        for (cx, cy) in ((bb[0] + 1, bb[1] + 1), (bb[2] - 2, bb[1] + 1), (bb[0] + 1, bb[3] - 2), (bb[2] - 2, bb[3] - 2)):
            if 0 <= cx < w and 0 <= cy < h and sp[cx, cy][3] > 0:
                X, Y = cx * S + S // 2, cy * S + S // 2
                for dy in range(-3, 4):
                    for dx in range(-3, 4):
                        if abs(dx) + abs(dy) <= 3 and 0 <= X + dx < w * S and 0 <= Y + dy < h * S:
                            put(op, X + dx, Y + dy, sk['corner'] if abs(dx) + abs(dy) < 3 else sk['edge2'])
    return out, len(slots), len(wins)


def skin_sprite_recolor(src, sk):
    """小物 (矢印・炎・ホットバーなど): 2 倍にして、明るさで mod の色に塗り直す (透明はそのまま)"""
    w, h = src.size
    sp = src.load()
    out = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    op = out.load()
    for y in range(h):
        for x in range(w):
            p = sp[x, y]
            if p[3] == 0:
                continue
            fill2(op, x, y, ramp(sk, lum(p)), p[3])
    return out


def button(w, h, sk, state):
    """ボタン (nine-slice、200×20 を 2 倍)。state = normal / highlighted / disabled"""
    im = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    px = im.load()
    fillc = sk['button'] if state != 'highlighted' else sk['button_hi']
    edge = sk['button_edge'] if state != 'highlighted' else sk['button_edge_hi']
    if state == 'disabled':
        fillc = tuple(int(c * 0.7) for c in sk['button'])
        edge = tuple(int(c * 0.6) for c in sk['button_edge'])
    W, H = w * S, h * S
    for y in range(H):
        for x in range(W):
            if x < 2 or y < 2 or x >= W - 2 or y >= H - 2:
                c = edge
            elif y < 4:
                c = tuple(min(255, v + 14) for v in fillc)
            elif y >= H - 4:
                c = tuple(max(0, v - 14) for v in fillc)
            else:
                c = fillc
            put(px, x, y, c)
    if state == 'highlighted':
        for x in range(6, W - 6):
            put(px, x, 4, sk['button_edge_hi'])
    return im


def panel(w, h, sk, alpha=255, seed=3):
    rng = random.Random(seed)
    im = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    px = im.load()
    for y in range(h * S):
        for x in range(w * S):
            put(px, x, y, noise(rng, sk['panel'], 2), alpha)
    return im


def frame(w, h, sk, inner_alpha=0):
    im = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    px = im.load()
    W, H = w * S, h * S
    for y in range(H):
        for x in range(W):
            if x < 2 or y < 2 or x >= W - 2 or y >= H - 2:
                put(px, x, y, sk['edge'])
            elif inner_alpha:
                put(px, x, y, sk['panel'], inner_alpha)
    return im


def save(im, path, mcmeta=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    if mcmeta:
        with open(path + '.mcmeta', 'w', encoding='utf-8') as f:
            json.dump(mcmeta, f, indent=1)


def nine(w, h, border):
    return {'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': h, 'border': border}}}


def hide_inventory_craft(im):
    """持ち物の画面の 2×2 のクラフト枠・矢印・できる物の枠を消す (作る仕組みはない。2026-10-28)。
    枠の下の無地の帯 (1 倍で y 60〜80) を写して上へ敷き詰める。字「クラフト」は client/InventoryCraftHide が消す"""
    k = im.size[0] // 256
    x0, x1 = 95 * k, 173 * k
    band = im.crop((x0, 60 * k, x1, 80 * k))
    y = 5 * k
    while y < 58 * k:
        h = min(band.size[1], 58 * k - y)
        im.paste(band.crop((0, 0, x1 - x0, h)), (x0, y))
        y += h
    return im


def main():
    z = zipfile.ZipFile(JAR)

    def load(name):
        return Image.open(io.BytesIO(z.read('assets/minecraft/textures/gui/' + name))).convert('RGBA')

    def meta(name):
        try:
            return json.loads(z.read('assets/minecraft/textures/gui/' + name + '.mcmeta'))
        except KeyError:
            return None

    containers = [n.split('textures/gui/')[1] for n in z.namelist() if n.startswith('assets/minecraft/textures/gui/container/') and n.endswith('.png')]
    for key, sk in SKINS.items():
        base = os.path.join(OUT, 'skyabyss_ui_' + key)
        if os.path.exists(base):
            shutil.rmtree(base)
        tex = os.path.join(base, 'assets', 'minecraft', 'textures', 'gui')
        report = []
        for c in containers:
            src = load(c)
            im, ns, nw = skin_container(src, sk)
            if c == 'container/inventory.png':
                im = hide_inventory_craft(im)
            save(im, os.path.join(tex, c))
            report.append('%s: %d slots, %d windows' % (c, ns, nw))
        # 箱の画面と同じ作りの板: レシピの本、進捗の窓、タブの見出しの帯
        for nm in ('recipe_book.png', 'advancements/window.png'):
            im, ns, nw = skin_container(load(nm), sk)
            save(im, os.path.join(tex, nm))
        # レシピの本・進捗の小物 (色に意味のある「作れる/作れない」の枠と、達成した/していない枠はそのまま)
        allnames = [n.split('textures/gui/')[1] for n in z.namelist() if n.startswith('assets/minecraft/textures/gui/sprites/') and n.endswith('.png')]
        for nm in allnames:
            short = nm.split('sprites/')[1]
            keep = ('slot_' in short or 'frame_' in short or 'box_' in short)
            if (short.startswith('recipe_book/') or short.startswith('advancements/tab') or short == 'advancements/title_box.png') and not keep:
                save(skin_sprite_recolor(load(nm), sk), os.path.join(tex, nm), meta(nm))
        # 小物: ボタン・つまみ・入力欄・スクロール・タブ
        for st in ('normal', 'highlighted', 'disabled'):
            nm = 'button' if st == 'normal' else 'button_' + st
            save(button(200, 20, sk, st), os.path.join(tex, 'sprites', 'widget', nm + '.png'), nine(200, 20, 3))
        save(button(200, 20, sk, 'normal'), os.path.join(tex, 'sprites', 'widget', 'slider.png'), nine(200, 20, 3))
        save(button(200, 20, sk, 'highlighted'), os.path.join(tex, 'sprites', 'widget', 'slider_highlighted.png'), nine(200, 20, 3))
        save(button(8, 20, sk, 'normal'), os.path.join(tex, 'sprites', 'widget', 'slider_handle.png'), nine(8, 20, 3))
        save(button(8, 20, sk, 'highlighted'), os.path.join(tex, 'sprites', 'widget', 'slider_handle_highlighted.png'), nine(8, 20, 3))
        for nm, st in (('text_field', 'normal'), ('text_field_highlighted', 'highlighted')):
            im = frame(200, 20, sk, inner_alpha=255)
            if st == 'highlighted':
                im = frame(200, 20, dict(sk, edge=sk['corner']), inner_alpha=255)
            save(im, os.path.join(tex, 'sprites', 'widget', nm + '.png'), nine(200, 20, 1))
        save(button(6, 32, sk, 'normal'), os.path.join(tex, 'sprites', 'widget', 'scroller.png'), nine(6, 32, 2))
        for nm in ('tab', 'tab_highlighted', 'tab_selected', 'tab_selected_highlighted'):
            src = load('sprites/widget/' + nm + '.png')
            save(button(src.size[0], src.size[1], sk, 'highlighted' if 'highlighted' in nm or 'selected' in nm else 'normal'),
                 os.path.join(tex, 'sprites', 'widget', nm + '.png'), meta('sprites/widget/' + nm + '.png'))
        # ホットバー・経験値・効果の枠
        for nm in ('hotbar', 'hotbar_selection', 'hotbar_offhand_left', 'hotbar_offhand_right', 'experience_bar_background',
                   'experience_bar_progress', 'effect_background', 'effect_background_ambient', 'jump_bar_background', 'jump_bar_cooldown', 'jump_bar_progress'):
            src = load('sprites/hud/' + nm + '.png')
            im = skin_sprite_recolor(src, sk)
            if nm == 'experience_bar_progress':
                im = skin_sprite_recolor(src, dict(sk, ramp=[(0, 0, 0), sk['corner'], tuple(min(255, v + 60) for v in sk['corner']), (255, 255, 255)]))
            save(im, os.path.join(tex, 'sprites', 'hud', nm + '.png'), meta('sprites/hud/' + nm + '.png'))
        # ツールチップは 1.21.1 では絵がなく、コードで描く (mixin/TooltipSkinMixin が skin.json の色で描く)
        # メニューの背景 (タイトル・設定の画面の裏の模様)
        for nm in ('menu_background.png', 'menu_list_background.png', 'inworld_menu_background.png', 'inworld_menu_list_background.png'):
            try:
                src = load(nm)
            except KeyError:
                continue
            w, h = src.size
            im = panel(w, h, dict(sk, panel=tuple(int(v * 0.8) for v in sk['panel'])), alpha=max(p[3] for p in src.getdata()))
            save(im, os.path.join(tex, nm), meta(nm))
        for nm in ('header_separator.png', 'footer_separator.png', 'inworld_header_separator.png', 'inworld_footer_separator.png'):
            try:
                src = load(nm)
            except KeyError:
                continue
            save(skin_sprite_recolor(src, dict(sk, ramp=[sk['edge2'], sk['edge2'], sk['edge'], sk['edge']])), os.path.join(tex, nm), meta(nm))
        # 見出しの文字の色 (UiSkin)
        os.makedirs(os.path.join(base, 'assets', 'skyabyss', 'ui'), exist_ok=True)
        with open(os.path.join(base, 'assets', 'skyabyss', 'ui', 'skin.json'), 'w', encoding='utf-8') as f:
            json.dump({'label': sk['label'], 'label_dim': sk['label_dim'], 'tooltip_bg': (0xF0 << 24) | (sk['panel'][0] << 16) | (sk['panel'][1] << 8) | sk['panel'][2],
                       'tooltip_edge': (0xFF << 24) | (sk['edge2'][0] << 16) | (sk['edge2'][1] << 8) | sk['edge2'][2],
                       'tooltip_edge2': (0xFF << 24) | (sk['corner'][0] << 16) | (sk['corner'][1] << 8) | sk['corner'][2]}, f)
        with open(os.path.join(base, 'pack.mcmeta'), 'w', encoding='utf-8') as f:
            json.dump({'pack': {'pack_format': 34, 'description': '天と淵のあいだ: 画面の絵柄 %s %s' % (key.upper(), sk['name'])}}, f, ensure_ascii=False, indent=1)
        print(key, len(containers), 'containers;', '; '.join(r for r in report if ' 0 slots' in r)[:300])


if __name__ == '__main__':
    main()
