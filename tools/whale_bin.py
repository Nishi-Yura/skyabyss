"""whale.bin の読み出し (岩鯨の背の形)。部屋の箱が体の殻の内側に収まるかを、ゲームを起動せずに調べる (設計書 26)。
使い方: python tools/whale_bin.py  (主な柱の高さを出す)"""
import gzip, struct, sys
from pathlib import Path
import numpy as np

BIN = Path(__file__).resolve().parents[1] / "src/main/resources/data/skyabyss/titan_back/whale.bin"


def load(path=BIN):
    b = gzip.decompress(Path(path).read_bytes())
    o = 4
    x0, y0, z0, nx, ny, nz = struct.unpack_from(">iiiiii", b, o); o += 24
    n = struct.unpack_from(">h", b, o)[0]; o += 2
    names = []
    for _ in range(n):
        l = struct.unpack_from(">h", b, o)[0]; o += 2
        names.append(b[o:o + l].decode()); o += l
    solid = np.zeros((nx, ny, nz), bool)
    body = np.full((nx, nz), -1, np.int16)
    air = names.index("air") if "air" in names else -1
    for a in range(nx):
        for c in range(nz):
            body[a, c] = struct.unpack_from(">h", b, o)[0]; o += 2
            s = struct.unpack_from(">h", b, o)[0]; o += 2
            for _ in range(s):
                st, ln, v = struct.unpack_from(">hhh", b, o); o += 6
                if v != air:
                    solid[a, st:st + ln, c] = True
    return dict(x0=x0, y0=y0, z0=z0, solid=solid, body=body, names=names)


class Whale:
    def __init__(self):
        d = load(); self.__dict__.update(d)
        from scipy import ndimage
        self.depth = ndimage.distance_transform_cdt(np.pad(self.solid, 1), metric="chessboard")[1:-1, 1:-1, 1:-1]

    def top(self, x, z):
        t = self.body[x - self.x0, z - self.z0]
        return None if t < 0 else self.y0 + int(t)

    def margin(self, x1, x2, z1, z2, y1, y2):
        """箱 (壁を含めて 1 外側) の中で、体の外までの最小距離。0 以下なら殻を突き抜ける (-1 = 範囲外)"""
        sl = (slice(x1 - 1 - self.x0, x2 + 2 - self.x0), slice(y1 - self.y0, y2 + 1 - self.y0), slice(z1 - 1 - self.z0, z2 + 2 - self.z0))
        sub = self.depth[sl]
        return int(sub.min()) - 1 if sub.size else -9


if __name__ == "__main__":
    w = Whale()
    print("x", w.x0, w.x0 + w.solid.shape[0] - 1, "z", w.z0, w.z0 + w.solid.shape[2] - 1)
    for x, z in [(0, 62), (0, 160), (0, 0), (0, -35), (0, -136), (0, 300), (-60, 175), (0, 12)]:
        print((x, z), "top", w.top(x, z))
