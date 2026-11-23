# -*- coding: utf-8 -*-
"""岩鯨の防具（stone_whale）の look5 ラフ 3 案（2026-11-21 の参照: 強い一色・板の縁の明るい縁取り・段の厚い板・
角とひれで輪郭を崩す・胸の紋章・長い前垂れ・浮く飾り）。色は鯨の武器と同じ（sea・bone・glow）。バニラの形に縛られない。

  A 鯨骨の騎士: 顎の骨の大角（V 字に上へ）、背びれの冠、3 段の肩、胸に尾びれの紋章、尾びれの前垂れ
  B 潮の司祭: ひれの頭巾、ひげ板の肩掛け、長い前垂れと後ろ垂れ、背に浮く背骨の環
  C 岩殻の巨兵: 右だけの巨大な肩（フジツボ・牙）、肋の格子の胸、岩のひれ 3 枚の兜、重い靴

  cd <作業> && python -I <this> A B C
"""
import math
import sys

import lib
from armor5 import Armor

# 防具の主の色: 鯨の sea を一段明るく（参照の防具は明るい一色が主役）
lib.RAMPS['wsea'] = lib.RAMPS['sea'][1:] + ['#ffffff']

FACE_Y = 59.2   # 額当ての下端（顔は y 48..59.2、|x|<8 を開ける）

# ドット絵の紋章（上の行から。'.' は透明）
FLUKE = [
    "b.........b",
    "bb.......bb",
    "BbB.....BbB",
    ".BbBB.BBbB.",
    "..BBbbbBB..",
    "....BbB....",
    "....BbB....",
    "....BgB....",
    ".....B.....",
]
WAVE = [
    "..bbb....",
    ".b...b...",
    "b..g..b..",
    "b.....b.b",
    ".b...b.b.",
    "..bbb.b..",
    "....bb...",
]
PAL = {'b': ('bone', 4), 'B': ('bone', 2), 'g': ('glow', 4)}


def emblem(a, name, art, cx, cy, z, px=1.0):
    """胸・前垂れの紋章（板の絵）。cx, cy は中心、z は板の z（表の少し前）"""
    w, h = len(art[0]) * px, len(art) * px
    a.picture(name, art, PAL, (cx - w / 2, cy - h / 2), px=px, at=z)


def horn(a, name, s, base, pts, w0, w1, mat='bone', t0=3, t1=5, zc=0.0):
    """xy 面で曲がる角（点の列を節でつなぐ。s で左右を返す）"""
    P = [(s * (base[0] + x), base[1] + y) for x, y in pts]
    n = len(P) - 1
    for k in range(n):
        w = w0 + (w1 - w0) * k / max(1, n - 1)
        a.seg(f'{name}{k}', mat, P[k], P[k + 1], w, w / 2, t0 + (t1 - t0) * k / n, t0 + (t1 - t0) * (k + 1) / n,
              zc=zc, tip=(k == n - 1))


# ------------------------------------------------------------------ A 鯨骨の騎士
def rough_a():
    a = Armor('stone_whale_ra')
    a.on('H')
    a.box('crown', 'wsea', (-9.4, 62.0, -9.4), (9.4, 66.6, 9.4), grad=(62, 4, 66, 6), edge=True)
    a.box('brow', 'wsea', (-10.0, FACE_Y, -10.4), (10.0, 62.6, -8.0), base=4, edge=True)
    a.box('nape', 'wsea', (-9.6, 50.0, 1.0), (9.6, 62.2, 9.8), grad=(50, 2, 62, 4), edge=True)
    a.pair('H', lambda s: a.box('ear', 'wsea', (s * 8.2, 50.4, -8.2), (s * 10.0, FACE_Y + 0.6, 1.4), base=3, edge=True))
    a.pair('H', lambda s: a.box('cheek', 'bone', (s * 8.6, 50.0, -9.4), (s * 10.6, 55.0, -6.0), base=3, rot=(0, s * -14, 0)))
    a.box('nose', 'bone', (-1.0, 55.0, -10.6), (1.0, FACE_Y + 0.2, -9.4), base=4)
    a.box('gem', 'glow', (-1.8, 60.0, -11.0), (1.8, 62.0, -10.2), gem=True)
    # 顎の骨の大角（こめかみから V 字に上へ、先は少し外へ反る）
    a.pair('H', lambda s: horn(a, 'horn', s, (8.0, 63.0), [(0, 0), (3.0, 4.0), (5.0, 9.0), (5.6, 14.0), (4.6, 18.4)], 3.2, 2.0, zc=0.0))
    # 背びれの冠（後ろへ流す 3 枚）
    for k, (z0, hh) in enumerate(((-3.0, 4.0), (1.5, 5.4), (6.0, 4.0))):
        a.box(f'fin{k}', 'wsea', (-1.2, 66.0, z0), (1.2, 66.6 + hh, z0 + 4.0), base=5 + k * 0.3, edge=True, rot=(26, 0, 0), origin=(0, 66, z0 + 2))
    # 胴: 段の胸当て + 尾びれの紋章 + 腹の重ね板、背骨
    a.on('B')
    a.box('chest', 'wsea', (-9.2, 33.0, -6.0), (9.2, 48.4, 5.4), grad=(33, 2, 48, 4), edge=True)
    a.box('breast', 'wsea', (-8.4, 36.0, -7.4), (8.4, 47.4, -5.8), base=4, edge=True)
    a.box('collar', 'bone', (-6.0, 46.8, -7.0), (6.0, 49.4, 5.6), base=3, edge=True)
    emblem(a, 'fluke', FLUKE, 0, 41.8, -7.45, px=1.0)
    for k in range(3):
        y = 33.6 - k * 3.0
        a.box(f'abs{k}', 'wsea', (-7.6 + k * 0.6, y - 2.8, -7.0 + k * 0.3), (7.6 - k * 0.6, y, 5.0), base=3 - k * 0.4, edge=True)
    a.box('belt', 'bone', (-9.6, 24.2, -6.4), (9.6, 26.8, 5.8), base=3, edge='bone')
    a.box('buckle', 'glow', (-1.6, 24.4, -7.0), (1.6, 26.6, -6.2), gem=True)
    a.box('spine', 'bone', (-1.4, 27.0, 5.4), (1.4, 48.0, 7.2), grad=(27, 2, 48, 4), wrap=(27, 48))
    for k in range(4):
        a.box(f'spike{k}', 'bone', (-0.9, 30.0 + k * 4.6, 7.0), (0.9, 32.2 + k * 4.6, 9.8 - k * 0.3), base=4, rot=(-20, 0, 0),
              origin=(0, 31 + k * 4.6, 7))
    # 腕: 3 段の肩（外が上がる）と背骨の棘、籠手

    def arm(s):
        for k in range(3):
            y = 47.0 - k * 3.6
            a.box(f'pauld{k}', 'wsea', (s * (7.4 + k * 0.8), y, -6.4 + k * 0.5), (s * (20.4 - k * 0.6), y + 3.6, 6.4 - k * 0.5),
                  base=5 - k * 0.6, edge=True, rot=(0, 0, s * (16 + k * 5)), origin=(s * 9, y + 2, 0))
        for k in range(3):
            a.box(f'pspike{k}', 'bone', (s * (11.6 + k * 2.6), 50.4 + k * 1.2, -0.9), (s * (13.4 + k * 2.6), 54.0 + k * 1.8, 0.9),
                  base=4, rot=(0, 0, s * (-10 - k * 6)), origin=(s * (12.5 + k * 2.6), 50.4 + k * 1.2, 0))
        a.box('bracer', 'wsea', (s * 8.4, 26.0, -5.0), (s * 17.2, 34.0, 5.0), base=3, edge=True)
        a.box('bracer_fin', 'bone', (s * 16.8, 27.0, -1.0), (s * 19.4, 33.0, 1.0), base=4, rot=(0, 0, s * -8), origin=(s * 17, 30, 0))
        a.box('bracer_glow', 'glow', (s * 17.0, 30.0, -3.0), (s * 17.6, 31.6, -1.4))
        a.box('sleeve', 'wsea', (s * 8.6, 34.0, -4.6), (s * 16.8, 44.0, 4.6), base=2)
    a.pair('RA', arm)
    # 脚: 尾びれの前垂れと後ろ垂れ、横の張り板、尖った膝

    def leg(s):
        a.box('thigh', 'wsea', (s * 0.3, 10.0, -4.8), (s * 8.8, 24.0, 4.8), grad=(10, 1, 24, 3))
        a.box('tasset', 'wsea', (s * 8.4, 15.0, -5.0), (s * 11.4, 24.2, 5.0), base=4, edge=True, rot=(0, 0, s * 12), origin=(s * 8.8, 24, 0))
        a.box('knee', 'wsea', (s * 1.2, 11.0, -6.6), (s * 7.6, 16.4, -4.6), base=4, edge=True)
        a.box('knee_fin', 'bone', (s * 3.4, 15.6, -7.2), (s * 5.4, 19.2, -5.6), base=4, rot=(-14, 0, 0), origin=(s * 4.4, 15.6, -6))
        a.box('apron', 'wsea', (s * -0.2, 13.0, -6.4), (s * 8.0, 24.0, -5.0), grad=(13, 3, 24, 5), edge=True, rot=(6, 0, 0), origin=(s * 4, 24, -5.6))
        a.box('apron_back', 'wsea', (s * -0.2, 12.0, 5.0), (s * 8.0, 24.0, 6.4), grad=(12, 2, 24, 4), edge=True, rot=(-8, 0, 0), origin=(s * 4, 24, 5.6))
    a.pair('RL', leg)
    # 靴: 厚い靴、つま先の板、足首から後ろへ開くひれ

    def boot(s):
        a.box('boot', 'wsea', (s * 0.2, 0.0, -5.0), (s * 8.9, 9.4, 5.0), grad=(0, 1, 9, 3), edge=True)
        a.box('toe', 'wsea', (s * 0.6, 0.0, -7.6), (s * 8.4, 3.6, -5.0), base=4, edge=True)
        a.box('shin', 'bone', (s * 2.0, 3.6, -6.4), (s * 7.0, 9.0, -5.0), base=3, edge=True)
        a.box('fin', 'wsea', (s * 8.6, 3.0, 0.0), (s * 10.4, 9.2, 6.0), base=5, edge=True, rot=(0, s * 20, s * -10), origin=(s * 8.8, 6, 1))
        a.box('ankle_glow', 'glow', (s * 4.0, 5.6, -6.9), (s * 5.2, 6.8, -6.3))
    a.pair('RB', boot)
    return a


# ------------------------------------------------------------------ B 潮の司祭
def rough_b():
    a = Armor('stone_whale_rb')
    a.on('H')
    a.box('hood', 'wsea', (-9.6, 59.4, -9.6), (9.6, 67.0, 9.8), grad=(59, 3, 67, 5), edge=True)
    a.box('hood_back', 'wsea', (-9.8, 47.0, 1.0), (9.8, 59.6, 10.4), grad=(47, 2, 59, 3), edge=True)
    a.pair('H', lambda s: a.box('hood_side', 'wsea', (s * 8.2, 48.6, -8.8), (s * 10.0, 59.6, 1.2), base=3, edge=True))
    a.box('hood_rim', 'bone', (-10.2, 58.6, -10.4), (10.2, 60.8, -8.8), base=4, edge='bone')
    a.box('gem', 'glow', (-1.6, 60.6, -11.0), (1.6, 63.6, -10.2), gem=True)
    # 頭頂の大きなひれ（後ろへ倒れて垂れる）と横のひれ
    a.box('fin', 'wsea', (-1.4, 66.0, -5.0), (1.4, 74.0, 3.0), base=5, edge=True, rot=(34, 0, 0), origin=(0, 66, 0))
    a.box('fin_tip', 'bone', (-1.0, 72.0, 0.0), (1.0, 75.4, 6.0), base=4, edge=True, rot=(58, 0, 0), origin=(0, 72, 2))
    a.pair('H', lambda s: a.box('side_fin', 'wsea', (s * 9.0, 60.0, -1.0), (s * 15.6, 62.0, 7.0), base=5, edge=True,
                                rot=(0, s * -24, s * 26), origin=(s * 9.4, 61, 2)))
    # 胴: 高い襟、波の紋章の胸、綱の帯と真珠、背の環（浮く背骨）
    a.on('B')
    a.box('robe', 'wsea', (-9.0, 25.0, -5.6), (9.0, 48.0, 5.2), grad=(25, 2, 48, 4))
    a.box('collar', 'wsea', (-9.4, 46.0, -7.0), (9.4, 51.0, 6.6), base=5, edge=True)
    a.box('placket', 'bone', (-1.2, 25.0, -6.4), (1.2, 46.0, -5.4), base=3)
    emblem(a, 'wave', WAVE, -4.4, 40.0, -5.7, px=1.0)
    a.box('sash', 'bone', (-9.6, 26.0, -6.6), (9.6, 29.0, 6.0), base=3, wrap=(26, 29), edge='bone')
    a.box('pearl', 'glow', (-2.0, 25.6, -7.6), (2.0, 29.4, -6.4), gem=True)
    for k in range(10):
        ang = math.radians(90 + k * 36)
        x, y = 15.0 * math.cos(ang), 38.0 + 15.0 * math.sin(ang)
        a.box(f'ring{k}', 'bone' if k % 2 else 'wsea', (x - 1.8, y - 1.8, 11.0), (x + 1.8, y + 1.8, 13.4), base=4, edge=True)
    a.box('ring_core', 'glow', (-2.2, 35.8, 11.4), (2.2, 40.2, 13.0), gem=True)
    # 腕: ひげ板の肩掛け（縦の細板）、鐘の袖口

    def arm(s):
        a.box('mantle', 'wsea', (s * 7.6, 44.6, -6.2), (s * 18.0, 48.6, 6.2), base=5, edge=True, rot=(0, 0, s * 12), origin=(s * 9, 46, 0))
        for k in range(5):
            z = -5.0 + k * 2.5
            a.box(f'baleen{k}', 'bone', (s * 17.0, 37.0 - (k % 2) * 1.6, z - 1.0), (s * 18.4, 46.0, z + 1.0), grad=(37, 2, 46, 4),
                  rot=(0, 0, s * 12), origin=(s * 17.6, 46, 0))
        a.box('sleeve', 'wsea', (s * 8.4, 30.0, -4.6), (s * 16.8, 44.0, 4.6), base=3)
        a.box('cuff', 'wsea', (s * 7.8, 24.0, -6.0), (s * 17.8, 30.0, 6.0), base=4, edge=True)
        a.box('cuff_glow', 'glow', (s * 17.8, 26.4, -1.0), (s * 18.4, 28.0, 1.0))
    a.pair('RA', arm)
    # 脚: 長い前垂れと後ろ垂れ（脚の骨に左右 2 枚）、裾は外へ開く

    def leg(s):
        a.box('pants', 'wsea', (s * 0.3, 9.0, -4.6), (s * 8.8, 24.0, 4.6), base=2)
        a.box('tab_f', 'wsea', (s * 0.2, 4.0, -6.4), (s * 7.4, 24.0, -5.0), grad=(4, 3, 24, 5), edge=True, rot=(8, 0, s * -4), origin=(s * 3.6, 24, -5.6))
        a.box('tab_b', 'wsea', (s * 0.2, 4.0, 5.0), (s * 7.4, 24.0, 6.4), grad=(4, 2, 24, 4), edge=True, rot=(-8, 0, s * -4), origin=(s * 3.6, 24, 5.6))
        a.box('tab_side', 'wsea', (s * 8.4, 8.0, -4.4), (s * 9.8, 24.0, 4.4), base=3, edge=True, rot=(0, 0, s * 8), origin=(s * 8.8, 24, 0))
        a.box('tab_glow', 'glow', (s * 3.0, 6.4, -6.8), (s * 4.6, 8.0, -6.0))
    a.pair('RL', leg)

    def boot(s):
        a.box('boot', 'wsea', (s * 0.2, 0.0, -5.0), (s * 8.9, 9.0, 5.0), base=2, wrap=(1, 9))
        a.box('toe', 'bone', (s * 0.8, 0.0, -7.0), (s * 8.2, 2.6, -5.0), base=3, edge='bone')
    a.pair('RB', boot)
    return a


# ------------------------------------------------------------------ C 岩殻の巨兵
def rough_c():
    a = Armor('stone_whale_rc')
    a.on('H')
    a.box('helm', 'wsea', (-10.0, 59.2, -10.2), (10.0, 67.0, 10.0), grad=(59, 3, 67, 5), edge=True)
    a.box('nape', 'wsea', (-10.0, 49.0, 1.0), (10.0, 59.4, 10.4), grad=(49, 2, 59, 4), edge=True)
    a.pair('H', lambda s: a.box('jaw', 'wsea', (s * 8.4, 49.0, -10.4), (s * 11.4, 59.6, 1.2), base=4, edge=True, rot=(0, s * -8, 0)))
    a.box('brow', 'bone', (-10.6, 58.4, -11.2), (10.6, 61.4, -9.6), base=4, edge='bone')
    a.box('gem', 'glow', (-1.8, 61.6, -10.8), (1.8, 64.4, -10.0), gem=True)
    for k, (x, hh, rz) in enumerate(((-5.0, 5.0, 14), (0.0, 7.4, 0), (5.0, 5.0, -14))):
        a.box(f'crest{k}', 'wsea', (x - 1.4, 66.4, -4.0), (x + 1.4, 66.6 + hh, 6.0), base=5, edge=True, rot=(18, 0, rz), origin=(x, 66.6, 1))
    for k, (x, y, z) in enumerate(((7.0, 64.0, 8.6), (4.6, 66.2, 9.0), (8.6, 61.0, 9.6))):
        a.box(f'barn{k}', 'bone', (x - 1.3, y - 1.3, z - 1.3), (x + 1.3, y + 1.3, z + 1.3), base=3, edge='bone')
    # 胴: 厚い胸と肋の格子、奥の光
    a.on('B')
    a.box('chest', 'wsea', (-9.6, 30.0, -6.6), (9.6, 48.6, 6.0), grad=(30, 2, 48, 4), edge=True)
    a.box('grille_bg', 'glow', (-5.4, 34.0, -7.0), (5.4, 45.0, -6.6))
    for k in range(4):
        y = 35.0 + k * 2.6
        a.box(f'rib{k}', 'bone', (-6.4, y, -8.4), (6.4, y + 1.4, -6.6), base=4, edge='bone')
    a.box('sternum', 'bone', (-1.0, 33.6, -8.8), (1.0, 46.0, -6.6), base=3)
    a.box('belly', 'wsea', (-8.8, 25.0, -7.0), (8.8, 30.4, 5.6), base=3, edge=True)
    a.box('back_shell', 'wsea', (-9.0, 30.0, 5.6), (9.0, 47.0, 9.0), grad=(30, 2, 47, 4), edge=True)
    for k, (x, y) in enumerate(((-5.0, 43.0), (-6.4, 40.0), (4.4, 34.0), (6.0, 44.0))):
        a.box(f'barnb{k}', 'bone', (x - 1.3, y - 1.3, 8.6), (x + 1.3, y + 1.3, 10.6), base=3, edge='bone')
    # 右肩: 巨大な岩殻（2 段 + 牙 3 本 + フジツボ）。左肩: 小さめの 1 段
    a.on('RA')
    a.box('pauld', 'wsea', (6.8, 43.0, -8.4), (24.0, 51.6, 8.4), base=4, edge=True, rot=(0, 0, 14), origin=(9, 46, 0))
    a.box('pauld2', 'wsea', (13.0, 37.0, -7.4), (24.6, 43.6, 7.4), base=3, edge=True, rot=(0, 0, 20), origin=(13, 43, 0))
    for k, (x, y, ln) in enumerate(((13.0, 52.0, 7.0), (17.0, 50.0, 5.6), (20.4, 47.6, 4.4))):
        horn(a, f'tusk{k}', 1, (0, 0), [(x, y), (x + ln * 0.35, y + ln * 0.7), (x + ln * 0.75, y + ln)], 2.6, 1.6, zc=0.0)
    for k, (x, y, z) in enumerate(((15.0, 51.0, -6.6), (19.0, 49.6, 6.4), (11.6, 50.8, 6.0))):
        a.box(f'barn{k}', 'bone', (x - 1.3, y - 1.3, z - 1.3), (x + 1.3, y + 1.3, z + 1.3), base=3, edge='bone')
    a.box('pauld_glow', 'glow', (20.0, 44.4, -2.0), (21.0, 47.0, 2.0))
    a.on('LA')
    a.box('pauld', 'wsea', (-18.4, 44.0, -6.6), (-7.6, 49.6, 6.6), base=4, edge=True, rot=(0, 0, -14), origin=(-9, 46, 0))

    def arm(s):
        a.box('sleeve', 'wsea', (s * 8.6, 34.0, -4.8), (s * 16.8, 44.0, 4.8), base=2)
        a.box('gauntlet', 'wsea', (s * 8.0, 24.0, -5.8), (s * 17.8, 34.0, 5.8), base=3, edge=True)
        a.box('knuckle', 'bone', (s * 8.4, 24.0, -6.4), (s * 17.4, 26.6, -5.6), base=4)
    a.pair('RA', arm)

    def leg(s):
        a.box('thigh', 'wsea', (s * 0.3, 10.0, -5.0), (s * 9.0, 24.0, 5.0), grad=(10, 1, 24, 3), edge=True)
        a.box('skirt', 'wsea', (s * 0.0, 16.0, -6.8), (s * 10.0, 24.6, -4.8), base=4, edge=True, rot=(12, 0, s * -6), origin=(s * 5, 24, -5.6))
        a.box('skirt_side', 'wsea', (s * 8.6, 15.0, -5.0), (s * 11.6, 24.6, 5.0), base=3, edge=True, rot=(0, 0, s * 14), origin=(s * 9, 24, 0))
        a.box('knee', 'bone', (s * 1.0, 10.4, -7.0), (s * 7.8, 15.4, -4.8), base=4, edge='bone')
        a.box('knee_glow', 'glow', (s * 3.6, 12.0, -7.4), (s * 5.2, 13.6, -6.9))
    a.pair('RL', leg)

    def boot(s):
        a.box('boot', 'wsea', (s * -0.4, 0.0, -6.0), (s * 9.6, 10.0, 5.6), grad=(0, 1, 10, 3), edge=True)
        a.box('toe', 'wsea', (s * 0.0, 0.0, -8.6), (s * 9.2, 4.0, -6.0), base=4, edge=True)
        a.box('cuff', 'bone', (s * -0.6, 8.0, -6.4), (s * 9.8, 10.6, 6.0), base=3, edge='bone')
    a.pair('RB', boot)
    return a


# ------------------------------------------------------------------ A の階級（2026-11-21 ユーザーが A を選んだ）
# レア: 角に枝、肩の棘と背骨の棘の先が光る、胸の縁に光の筋、背に背びれの板 3 枚、靴のひれが大きい
# 伝説: 頭の後ろに浮く背骨の環、背中から垂れる尾びれの外套（脚の骨で左右に分ける）、肩が 4 段、
#       冠のひれが高い、籠手に骨の刃、胸の核が大きい
def whale_a(tier='common'):
    a = rough_a()
    a.name = 'stone_whale' + {'common': '', 'rare': '_rare', 'legend': '_legend'}[tier]
    if tier == 'common':
        return a
    a.on('H')
    # 角の枝（外へ短く）
    a.pair('H', lambda s: horn(a, 'tine', s, (8.0, 63.0), [(5.0, 9.0), (8.6, 11.0), (10.6, 14.0)], 2.2, 1.6, zc=0.0))
    a.pair('H', lambda s: a.box('horn_glow', 'glow', (s * 12.4, 80.4, -0.6), (s * 13.6, 81.8, 0.6)))
    a.on('B')
    a.pair('B', lambda s: a.box('seam', 'glow', (s * 8.2, 37.0, -7.6), (s * 8.8, 46.6, -7.2)))
    for k, (y, hh) in enumerate(((44.0, 6.0), (38.6, 7.4), (33.0, 5.4))):
        a.box(f'dfin{k}', 'wsea', (-1.2, y, 8.6), (1.2, y + 2.6, 8.6 + hh), base=5, edge=True, rot=(-24, 0, 0), origin=(0, y + 1, 8.6))
    a.on('RB')
    a.pair('RB', lambda s: a.box('fin2', 'wsea', (s * 8.8, 1.0, 3.0), (s * 10.2, 7.0, 9.0), base=6, edge=True,
                                 rot=(0, s * 30, s * -14), origin=(s * 8.8, 4, 3)))
    a.pair('RA', lambda s: a.box('spike_glow', 'glow', (s * 17.6, 56.0, -0.6), (s * 18.8, 57.4, 0.6)))
    if tier == 'rare':
        return a
    # 伝説
    a.on('H')
    # 背骨の環: 頭の後ろに浮く輪（節をつないだ円）と、4 方向の椎骨
    a.arc('halo', 'bone', (0, 70.0), 14.0, 0, 360, 16, 2.4, 1.2, 3, 5, zc=14.0)
    for k in range(4):
        ang = math.radians(45 + k * 90)
        x, y = 14.0 * math.cos(ang), 70.0 + 14.0 * math.sin(ang)
        a.box(f'verte{k}', 'wsea', (x - 2.0, y - 2.0, 12.6), (x + 2.0, y + 2.0, 15.4), base=5, edge=True)
    a.box('halo_core', 'glow', (-1.6, 82.4, 13.4), (1.6, 85.6, 14.6), gem=True)
    for k, (z0, hh) in enumerate(((-1.0, 7.0), (3.5, 8.6))):
        a.box(f'tallfin{k}', 'wsea', (-0.9, 66.0, z0), (0.9, 66.6 + hh, z0 + 3.0), base=6, edge=True, rot=(30, 0, 0), origin=(0, 66, z0 + 1.5))
    a.on('B')
    a.box('core_big', 'glow', (-2.6, 39.6, -8.4), (2.6, 44.8, -7.5), gem=True)

    def cape(s):
        a.box('cape', 'wsea', (s * -0.2, 6.0, 7.0), (s * 9.4, 24.0, 8.6), grad=(6, 2, 24, 5), edge=True,
              rot=(-10, 0, s * -10), origin=(s * 4, 24, 7.6))
        a.box('cape_fluke', 'wsea', (s * 0.0, 3.0, 8.6), (s * 13.0, 7.0, 10.2), base=5, edge=True,
              rot=(-10, 0, s * -22), origin=(s * 2, 7, 9))
        a.box('cape_glow', 'glow', (s * 3.0, 12.0, 8.7), (s * 4.4, 14.0, 9.3))
    a.pair('RL', cape)

    def arm(s):
        a.box('pauld3', 'wsea', (s * 6.6, 50.6, -5.6), (s * 17.6, 53.6, 5.6), base=6, edge=True, rot=(0, 0, s * 12), origin=(s * 9, 52, 0))
        a.box('blade', 'bone', (s * 17.0, 22.0, -0.8), (s * 19.0, 36.0, 0.8), grad=(22, 5, 36, 3), edge='bone',
              rot=(0, 0, s * 6), origin=(s * 17.6, 34, 0))
        a.box('blade_glow', 'glow', (s * 18.9, 25.0, -0.4), (s * 19.5, 32.0, 0.4))
    a.pair('RA', arm)
    return a


ROUGHS = {'A': rough_a, 'B': rough_b, 'C': rough_c}

if __name__ == '__main__':
    for k in sys.argv[1:]:
        ROUGHS[k]().write('.')
