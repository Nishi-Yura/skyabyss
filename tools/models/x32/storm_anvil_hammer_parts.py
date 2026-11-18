# -*- coding: utf-8 -*-
"""嵐の鉄床の鎚（x32・伝説）。前の形: 太い鉄紺の円筒の頭（鉄床）、+x の打つ面に淡い雷の円盤、頭の上に銅と銅緑の羽根の軸 2 本、
鉄紺の柄、淡い青の輪、銅茶の握り、尖った石突き。嵐のシリーズ（基準は storm_hammer）。
伝説（60 の 1.6）: 頭を一回り大きく（幅 24・高さ 12）、銅の稲妻の筋を頭の表と裏に走らせ、打つ面の雷を大きく。原点は握りの真ん中。
"""
NAME = "storm_anvil_hammer"
SERIES = "storm"

PARTS = [
    seg("butt_1", "ironnavy", (0, -13), (0, -18), 3, 3, "worn"),
    seg("butt_2", "ironnavy", (0, -18), (0, -21), 1.5, 1.5, "plain"),
    ("pommel", "stormring", [-3, -13, -3], [3, -11.5, 3], "plain"),
    ("grip", "copper", [-2.5, -11.5, -2.5], [2.5, 7, 2.5], "band"),
    ("ring_high", "stormring", [-3.5, 7, -3.5], [3.5, 9, 3.5], "plain"),
    ("shaft", "ironnavy", [-2, 9, -2], [2, 30, 2], "grain"),
    # 頭: 円筒（八角）幅 24・高さ 12
    ("head_a", "ironnavy", [-11, 30, -5], [11, 42, 5], "worn"),
    ("head_b", "ironnavy", [-11, 31.5, -6.5], [11, 40.5, 6.5], "worn"),
    ("head_c", "ironnavy", [-11, 29, -3.5], [11, 43, 3.5], "worn"),
    # 両端の縁（淡い輪）
    ("rim_r", "stormring", [11, 30, -5.5], [12.5, 42, 5.5], "plain"),
    ("rim_l", "stormring", [-12.5, 30, -5.5], [-11, 42, 5.5], "plain"),
    # 打つ面（+x）の雷の円盤（光る芯は小さく）
    ("disc", "bolt", [12.5, 31.5, -4], [13.5, 40.5, 4], "shine"),
    ("disc_core_glow", "bolt", [13.5, 34, -1.5], [14, 38, 1.5], "gem"),
    # 頭の上の羽根の軸 2 本
    seg("quill_l1", "verdigris", (-3, 42), (-5, 48), 2, 2, "grad"),
    seg("quill_l2", "feather", (-5, 48), (-7, 52), 1.5, 2.5, "grad"),
    seg("quill_r1", "copper", (3, 42), (4.5, 47), 2, 2, "grad"),
    seg("quill_r2", "feather", (4.5, 47), (6.5, 50), 1.5, 2.5, "grad"),
    # 雷の結晶（頭の表と裏）
    ("socket_f", "copper", [-2.5, 32, -7.5], [2.5, 40, -6.5], "plain"),
    ("crystal_f_glow", "bolt", [-1.5, 33, -8.5], [1.5, 39, -7.5], "gem"),
    ("socket_b", "copper", [-2.5, 32, 6.5], [2.5, 40, 7.5], "plain"),
    ("crystal_b_glow", "bolt", [-1.5, 33, 7.5], [1.5, 39, 8.5], "gem"),
]



def zseg(name, mat, p0, p1, w, d, kind, z):
    """seg を z の位置へずらす（頭の表・裏の面に貼る細い部品）"""
    n, m, frm, to, kd, rot, org = seg(name, mat, p0, p1, w, d, kind)
    return (n, m, [frm[0], frm[1], frm[2] + z], [to[0], to[1], to[2] + z], kd, rot, [org[0], org[1], org[2] + z])


# 銅の稲妻の筋（頭の表と裏に貼る）
for side, z in (("f", -6.8), ("b", 6.8)):
    PARTS += [zseg(f"bolt_{side}1", "copper", (-8, 39), (-3, 35), 1.5, 0.6, "plain", z),
              zseg(f"bolt_{side}2", "copper", (-3, 35), (1, 38), 1.5, 0.6, "plain", z),
              zseg(f"bolt_{side}3", "copper", (1, 38), (7, 32), 1.5, 0.6, "plain", z)]
