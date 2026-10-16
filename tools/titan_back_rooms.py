"""岩鯨の背の体の中の部屋の外側の箱 (壁・床・天井を含む)。TitanBackLayout と同じ数を手で写したもの。
gen_titan_back_whale.py --fit が、殻までの距離を数えるのに使う。(名前: (x1, x2, z1, z2, y1, y2))"""

DX = 0   # 全体の移し (TitanBackLayout と合わせる)
DZ = 0


def box(x1, x2, z1, z2, floor, height):
    return (x1 - 1 + DX, x2 + 1 + DX, z1 - 1 + DZ, z2 + 1 + DZ, floor, floor + height + 1)


def rooms():
    return {
        "hub": box(-9, 9, -32, -12, 93, 9),
        "entry": box(-1, 1, -11, 4, 93, 4),
        "treasury": box(-6, 6, -44, -34, 93, 7),
        "heart_corridor": (-2 + DX, 2 + DX, -63 + DZ, -46 + DZ, 93, 98),
        "heart_rest": (-4 + DX, 4 + DX, -57 + DZ, -51 + DZ, 93, 98),
        "heart": (-17 + DX, 17 + DX, -97 + DZ, -63 + DZ, 93, 106),
        "w0": box(-22, -16, -26, -18, 93, 6),
        "wa": box(-36, -24, -34, -12, 85, 19),
        "w1": box(-34, -28, -42, -36, 97, 5),
        "wb": box(-31, -22, -56, -44, 85, 19),
        "w2": box(-24, -16, -63, -58, 100, 4),
        "wc": box(-19, -11, -52, -30, 85, 19),
        "e0": box(16, 22, -26, -18, 93, 6),
        "e1": box(24, 36, -34, -12, 93, 8),
        "ce1": box(26, 32, -42, -36, 93, 5),
        "e3": box(11, 19, -52, -30, 93, 8),
        "e3_corr": box(21, 24, -40, -38, 93, 4),
        "maze": (-22 + DX, 22 + DX, -62 + DZ, -2 + DZ, 76, 82),
        "maze_exit": box(10, 14, -10, -6, 81, 4),
    }
