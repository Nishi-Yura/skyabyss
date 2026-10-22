"""広間の広さ B (docs/design/26 の 8.7) の確認シート (控えめな色・日本語)。

  SKYABYSS_ROOMPLAY_DUMP=run/room_play.json SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="room play" ./gradlew runServer
  python tools/sheets/room_size_b_sheet.py run/room_play.json   -> docs/review/previews/room_size_b_sheet.png

部屋を上から見た図。左が 1 人、右が 6 人。入口 (矢印)、残る湧き場所 (濃い点。薄い点は使わない)、
働く罠 (円。塗り = 働く)、立つ柱 (四角。枠だけ = そのグループには立たない)。
数字は RoomPlay と同じもの (自己テスト room play が保証する)。
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "docs", "review", "previews", "room_size_b_sheet.png")

BG = (236, 233, 226)
PANEL = (246, 244, 239)
LINE = (190, 184, 172)
TEXT = (66, 62, 56)
DIM = (130, 124, 114)
SPAWN = (92, 104, 128)
SPAWN_OFF = (200, 198, 192)
TRAP = (176, 112, 92)
PILLAR = (110, 118, 112)
DOOR = (96, 136, 112)

FONTS = [r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc"]


def font(size):
    for f in FONTS:
        if os.path.exists(f):
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


NAMES = {
    "e1": "第一の広間 (練兵場)", "e2": "第二の広間 (射手の回廊)", "ck": "番兵の詰所", "e3": "番兵長の広間",
    "w10": "砦 W10", "w11": "棺の廊下 W11", "e10": "鞴持ちの炉 E10", "s09": "嵐の巣 S09",
}


def armed(total, n):
    n = max(1, min(n, 6))
    return max(1, min(total, math.ceil(total * (0.35 + 0.13 * (n - 1)) - 1e-9))) if total > 0 else 0


def tier(i, total):
    if i < math.ceil(total * 0.5):
        return 0
    return 1 if i < math.ceil(total * 0.8) else 2


def tier_for(n):
    return 2 if n >= 5 else 1 if n >= 3 else 0


def draw_room(d, room, n, ox, oy, cell, f_small):
    w = room["x2"] - room["x1"] + 1
    h = room["z2"] - room["z1"] + 1
    s = min((cell - 20) / w, (cell - 40) / h)
    rx = ox + (cell - w * s) / 2
    ry = oy + 30

    def px(x, z):
        return rx + (x - room["x1"]) * s, ry + (z - room["z1"]) * s

    d.rectangle([rx, ry, rx + w * s, ry + h * s], fill=PANEL, outline=LINE, width=2)
    # 湧き場所
    kept = {tuple(p) for p in room["spawnsByN"][str(min(n, 6))]}
    for p in room["spawns"]:
        x, z = px(p[0] + 0.5, p[1] + 0.5)
        on = tuple(p) in kept
        r = max(3, s * 0.9)
        d.ellipse([x - r, z - r, x + r, z + r], fill=SPAWN if on else None, outline=SPAWN if on else SPAWN_OFF, width=2)
    # 罠
    total = len(room["traps"])
    a = armed(total, n)
    for i, t in enumerate(room["traps"]):
        x, z = px(t[0] + 0.5, t[1] + 0.5)
        r = 2.2 * s
        d.ellipse([x - r, z - r, x + r, z + r], fill=(TRAP + (110,)) if i < a else None, outline=TRAP if i < a else SPAWN_OFF, width=2)
    # 柱
    pl = room["pillars"]
    mt = tier_for(n)
    for i, p in enumerate(pl):
        x0, z0 = px(p[0], p[1])
        x1, z1 = px(p[0] + 2, p[1] + 2)
        on = tier(i, len(pl)) <= mt
        d.rectangle([x0, z0, x1, z1], fill=PILLAR if on else None, outline=PILLAR if on else SPAWN_OFF, width=2)
    # 入口
    ex, ez = px(room["entrance"][0] + 0.5, room["entrance"][1] + 0.5)
    ex = max(rx - 6, min(rx + w * s + 6, ex))
    ez = max(ry - 6, min(ry + h * s + 6, ez))
    d.polygon([(ex - 8, ez), (ex, ez - 6), (ex + 8, ez), (ex, ez + 6)], fill=DOOR)
    d.text((ox + 6, oy + 6), ("1 人" if n == 1 else "6 人") + f"  罠 {a}/{total}  柱 {sum(1 for i in range(len(pl)) if tier(i, len(pl)) <= mt)}/{len(pl)}  敵の湧き {len(kept)}/{len(room['spawns'])}",
           fill=DIM, font=f_small)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "run", "room_play.json")
    rooms = json.load(open(src, encoding="utf-8"))
    cell = 330
    cols = 4
    n_rooms = len(rooms)
    rows = (n_rooms + 1) // 2  # 部屋ごとに 1 人と 6 人の 2 枠
    W = cols * cell + 40
    H = rows * (cell + 26) + 130
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img, "RGBA")
    f_big, f_mid, f_small = font(22), font(15), font(13)
    d.text((20, 14), "広間の広さ B: 同じ広間で、敵を入口側へ・罠と柱で広さを使う", fill=TEXT, font=f_big)
    d.text((20, 46), "左 = 1 人 / 右 = 6 人。▼ 入口 ● 湧き場所 (薄い点は使わない) ◯ 罠の円 (塗り = 働く) ■ 柱 (枠 = 立たない)。予告 40 tick・円の外は無傷。",
           fill=DIM, font=f_mid)
    d.text((20, 68), "罠は人数で 35% → 100%、柱は半分が常設で、3 人から・5 人から増える。部屋の大きさは変えない (26 の 8.7)。", fill=DIM, font=f_mid)
    for i, room in enumerate(rooms):
        col = (i % 2) * 2
        row = i // 2
        ox = 20 + col * cell
        oy = 100 + row * (cell + 26)
        d.text((ox + 6, oy - 8), NAMES.get(room["id"], room["id"]), fill=TEXT, font=f_mid)
        draw_room(d, room, 1, ox, oy + 8, cell, f_small)
        draw_room(d, room, 6, ox + cell, oy + 8, cell, f_small)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
