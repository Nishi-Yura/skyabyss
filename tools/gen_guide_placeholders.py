"""章の導き (docs/design/21 の P3) の仮の絵を作る。ユーザーが 2 案から選ぶまでの、はっきり仮と分かる絵 (角にマゼンタの印)。
選んだ絵で、次の 2 枚を置き換える (モデルの JSON は変えなくてよい):
  src/main/resources/assets/skyabyss/textures/item/wayfarer_notes.png    旅の覚書 (32x32)
  src/main/resources/assets/skyabyss/textures/item/heartbeat_ward.png    鼓動守 (32x32。四つの巨神で共通。巨神ごとに分けるなら
                                                                          models/item/heartbeat_ward_<titan>.json の textures を替える)
使い方: python tools/gen_guide_placeholders.py
"""
import os
import zlib
import struct

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "skyabyss", "textures", "item")


def png(path, px, w=32, h=32):
    raw = b""
    for y in range(h):
        raw += b"\x00" + b"".join(bytes(px[y][x]) for x in range(w))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def blank():
    return [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]


def mark(px):
    # 仮の印: 右上にマゼンタの三角
    for y in range(7):
        for x in range(32 - 7 + y, 32):
            px[y][x] = (255, 0, 255, 255)


def notes():
    px = blank()
    for y in range(5, 28):
        for x in range(8, 25):
            edge = x in (8, 24) or y in (5, 27)
            px[y][x] = (92, 62, 36, 255) if edge else (146, 104, 62, 255)
    for y in range(6, 27):
        px[y][10] = (70, 46, 28, 255)
    for y in (11, 14, 17, 20):
        for x in range(13, 22):
            px[y][x] = (222, 206, 160, 255)
    mark(px)
    return px


def ward():
    px = blank()
    cx, cy = 15.5, 17.5
    for y in range(32):
        for x in range(32):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 7 <= d <= 11:
                px[y][x] = (84, 190, 176, 255) if d < 10 else (40, 110, 104, 255)
            elif d < 4:
                px[y][x] = (230, 250, 244, 255)
            elif d < 5:
                px[y][x] = (84, 190, 176, 255)
    mark(px)
    return px


if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    png(os.path.join(ROOT, "wayfarer_notes.png"), notes())
    png(os.path.join(ROOT, "heartbeat_ward.png"), ward())
    print("wrote placeholders in", os.path.abspath(ROOT))
