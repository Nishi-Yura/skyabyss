"""岩鯨の背の体の中の部屋の内側の箱 (x1, x2, z1, z2, 床の y, 高さ)。TitanBackLayout (版 13) と同じ数を手で写したもの。
python tools/titan_back_rooms.py で、各部屋の壁 (1 外側) から体の外までの距離 (殻の厚み。3 未満は印) と、
部屋どうしの重なり (壁の分を含む。つなぎの部屋との重なりは許す) を表にする (tools/whale_bin.py)。"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

FLOOR = 103


def rooms():
    return {
        # 中央
        "hub": (-18, 18, -55, -15, FLOOR, 16),
        "entry": (-3, 3, -14, 15, FLOOR, 6),
        "treasury": (-10, 10, -73, -57, FLOOR, 9),
        "heart_corridor": (-3, 3, -108, -75, FLOOR, 6),
        "heart_rest": (-6, 6, -100, -88, FLOOR, 7),
        "song_shrine": (-20, -9, -106, -92, FLOOR, 7),
        "song_pass": (-8, -7, -98, -94, FLOOR, 5),
        "heart": (-27, 27, -163, -109, FLOOR, 16),
        # 西の翼
        "w0": (-35, -26, -42, -29, FLOOR, 8),
        "conn_w": (-24, -20, -38, -34, FLOOR, 6),
        "wa": (-57, -37, -54, -19, FLOOR, 15),
        "w1": (-54, -45, -67, -56, FLOOR + 5, 7),
        "wb": (-50, -38, -90, -69, FLOOR, 15),
        "w2": (-47, -36, -101, -92, FLOOR + 9, 6),
        "bell_pass": (-44, -40, -105, -102, FLOOR + 9, 6),
        "bell": (-52, -34, -128, -106, FLOOR, 22),
        "w2_conn1": (-35, -31, -99, -95, FLOOR + 9, 6),
        "w2_conn2": (-33, -29, -94, -86, FLOOR + 9, 6),
        "wc": (-32, -20, -85, -49, FLOOR, 15),
        # 東の翼
        "e0": (26, 32, -42, -29, FLOOR, 8),
        "conn_e": (20, 24, -38, -34, FLOOR, 6),
        "e1": (34, 54, -54, -8, FLOOR, 11),
        "ce1": (41, 53, -68, -56, FLOOR, 7),
        "e3_corr": (35, 39, -65, -61, FLOOR, 6),
        "e3": (20, 32, -106, -52, FLOOR, 11),
        "e2_pass": (33, 43, -6, 3, FLOOR, 6),
        "e2": (24, 46, 5, 47, FLOOR, 12),
        "ck_pass": (33, 43, 49, 54, FLOOR, 6),
        "ck": (24, 46, 56, 76, FLOOR, 8),
        # 任意の翼
        "library": (-34, -12, -8, 18, FLOOR, 10),
        "lib_pass": (-11, -5, 3, 9, FLOOR, 6),
        # 闇の水路 (下の層)
        "maze": (-45, 45, -122, 4, 84, 5),
        "maze_exit": (21, 27, -9, -1, 88, 5),
        "exit_pass": (4, 20, -7, -3, FLOOR, 6),
    }


# 重なってよい組 (つなぎの部屋・壁を共有する隣どうし)
ALLOWED = {
    ("hub", "entry"), ("hub", "conn_w"), ("hub", "conn_e"), ("hub", "treasury"), ("hub", "wc"),
    ("maze", "maze_exit"), ("heart_corridor", "heart_rest"), ("w2_conn1", "w2_conn2"),
    ("conn_w", "w0"), ("conn_w", "wa"), ("w0", "wa"), ("wa", "w1"), ("w1", "wb"), ("wb", "w2"), ("w2", "bell_pass"),
    ("bell_pass", "bell"), ("w2", "w2_conn1"), ("w2_conn1", "w2_conn2"), ("w2_conn2", "wc"), ("hub", "w2_conn2"),
    ("conn_e", "e0"), ("e0", "e1"), ("e1", "ce1"), ("ce1", "e3_corr"), ("e3_corr", "e3"), ("e1", "e2_pass"), ("e2_pass", "e2"),
    ("e2", "ck_pass"), ("ck_pass", "ck"), ("treasury", "heart_corridor"), ("heart_corridor", "heart_rest"),
    ("heart_corridor", "heart"), ("heart_rest", "song_pass"), ("song_pass", "song_shrine"), ("entry", "lib_pass"),
    ("lib_pass", "library"), ("entry", "exit_pass"), ("exit_pass", "maze_exit"),
}


def overlaps(a, b):
    """壁 (1 外側) を含めて、二つの箱が体積で重なるか。y は床〜天井 (height + 1)"""
    ax1, ax2, az1, az2, af, ah = a
    bx1, bx2, bz1, bz2, bf, bh = b
    return (ax1 - 1 <= bx2 + 1 and bx1 - 1 <= ax2 + 1 and az1 - 1 <= bz2 + 1 and bz1 - 1 <= az2 + 1
            and af <= bf + bh + 1 and bf <= af + ah + 1)


def inner_overlap(a, b):
    ax1, ax2, az1, az2, af, ah = a
    bx1, bx2, bz1, bz2, bf, bh = b
    return (ax1 <= bx2 and bx1 <= ax2 and az1 <= bz2 and bz1 <= az2 and af + 1 <= bf + bh and bf + 1 <= af + ah)


def main():
    from whale_bin import Whale
    w = Whale()
    R = rooms()
    print("%-16s %-34s %s" % ("room", "box", "shell"))
    for name, (x1, x2, z1, z2, f, h) in R.items():
        m = w.margin(x1, x2, z1, z2, f, f + h + 1)
        print("%-16s %-34s %s%s" % (name, (x1, x2, z1, z2, f, h), m, "   <-- thin" if m < 3 else ""))
    names = list(R)
    bad = 0
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            pair = (a, b) if (a, b) in ALLOWED else (b, a)
            if inner_overlap(R[a], R[b]):
                print("INNER OVERLAP", a, b)
                bad += 1
            elif overlaps(R[a], R[b]) and pair not in ALLOWED:
                print("walls touch (not allowed):", a, b)
                bad += 1
    print("problems:", bad)


if __name__ == "__main__":
    main()
