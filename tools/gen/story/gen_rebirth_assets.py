"""転生の見せ方の素材を作る (docs/design/31)。32×32 の手描き風ピクセル画と、閉じた箱のモデル・blockstate。
  python tools/gen/story/gen_rebirth_assets.py
出力: assets/skyabyss/textures/entity/rebirth_back.png, textures/block/rebirth_stone*.png, record_board*.png,
      models/block/rebirth_stone.json, record_board.json, record_board_fresh.json, blockstates/rebirth_stone.json, record_board.json
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources", "assets", "skyabyss")


def out(*p):
    path = os.path.join(ROOT, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


# ---------------------------------------------------------------- 背中の飾り (灰色。段の色を掛ける)
def back_texture():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    g = (214, 218, 222, 255)
    # 紋章 (0..15, 0..15): 菱形の縁 + 中の ✦
    rect(img, 0, 0, 15, 15, (150, 154, 160, 255))
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 7.2:
                px(img, x, y, (232, 235, 238, 255) if d > 5.2 else (188, 192, 198, 255))
            if d <= 7.2 and d > 6.0:
                px(img, x, y, (96, 100, 108, 255))
    for i in range(-5, 6):
        px(img, 7, 7 + i, (255, 255, 255, 255))
        px(img, 8, 7 + i, (255, 255, 255, 255))
        px(img, 7 + i, 7, (255, 255, 255, 255))
        px(img, 7 + i, 8, (255, 255, 255, 255))
    for d in (-1, 0, 1):
        px(img, 7 + d, 7 + d, (255, 255, 255, 255))
    # 布 (16..31, 0..15): たて織り + 下に縁取り
    rnd = random.Random(7)
    for y in range(16):
        for x in range(16):
            base = 205 if (x % 4) < 2 else 186
            base += rnd.randint(-6, 6)
            px(img, 16 + x, y, (base, base + 2, base + 5, 255))
    rect(img, 16, 13, 31, 15, (250, 250, 250, 255))
    rect(img, 16, 12, 31, 12, (150, 154, 160, 255))
    for x in range(16, 32, 4):
        px(img, x, 14, (150, 154, 160, 255))
    # 羽 (0..15, 16..31): 付け根が濃く先が薄い。縁は明るい
    for y in range(16):
        for x in range(16):
            a = int(255 - y * 8)
            c = 255 - (y * 4)
            px(img, x, 16 + y, (c, c, 255, a))
    for y in range(16):
        px(img, 7, 16 + y, (255, 255, 255, 255))
        px(img, 8, 16 + y, (255, 255, 255, 255))
    # 縁の区画 (16..31, 16..31): 一色
    rect(img, 16, 16, 31, 31, (176, 180, 186, 255))
    img.save(out("textures", "entity", "rebirth_back.png"))


# ---------------------------------------------------------------- 順位の石
def stone_texture():
    rnd = random.Random(31)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    base = (74, 88, 97)
    for y in range(32):
        for x in range(32):
            n = rnd.randint(-9, 9)
            c = (base[0] + n, base[1] + n, base[2] + n + 2, 255)
            # 縦の層 (石の積み)
            if y % 11 == 10:
                c = shade(c, 0.72)
            px(img, x, y, c)
    # 欠けと光の当たり
    for _ in range(26):
        x, y = rnd.randrange(32), rnd.randrange(32)
        px(img, x, y, shade((base[0], base[1], base[2], 255), 1.28))
        px(img, x + 1, y, shade((base[0], base[1], base[2], 255), 0.8))
    # 下の苔
    for x in range(32):
        h = rnd.randint(1, 4)
        for y in range(32 - h, 32):
            px(img, x, y, (58 + rnd.randint(-6, 6), 92 + rnd.randint(-8, 8), 62, 255))
    # 中央の紋: 光る ✦ (青緑)
    teal = (79, 208, 196, 255)
    pale = (159, 243, 234, 255)
    for i in range(-6, 7):
        px(img, 16, 13 + i, teal)
        px(img, 16 + i, 13, teal)
    for i in range(-3, 4):
        px(img, 16 + i, 13 + i, shade(teal, 0.8))
        px(img, 16 + i, 13 - i, shade(teal, 0.8))
    rect(img, 15, 12, 17, 14, pale)
    px(img, 16, 13, (255, 255, 255, 255))
    img.save(out("textures", "block", "rebirth_stone.png"))
    glow = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    for y in range(32):
        for x in range(32):
            t = y / 31
            c = (int(70 + 160 * (1 - t)), int(200 + 40 * (1 - t)), int(190 + 50 * (1 - t)), 255)
            if (x // 4 + y // 4) % 2:
                c = shade(c, 0.88)
            px(glow, x, y, c)
    rect(glow, 12, 4, 19, 8, (230, 255, 252, 255))
    glow.save(out("textures", "block", "rebirth_stone_glow.png"))


# ---------------------------------------------------------------- 飾り板
def board_texture(fresh):
    rnd = random.Random(21 + int(fresh))
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
    frame = (92, 78, 62)
    face = (44, 52, 58)
    for y in range(32):
        for x in range(32):
            n = rnd.randint(-5, 5)
            if x < 2 or x > 29 or y < 2 or y > 29:
                c = (frame[0] + n, frame[1] + n, frame[2] + n, 255)
            else:
                c = (face[0] + n, face[1] + n, face[2] + n + 2, 255)
            px(img, x, y, c)
    # 角の金具
    metal = (150, 140, 118, 255)
    for (cx, cy) in ((2, 2), (29, 2), (2, 29), (29, 29)):
        rect(img, cx - 1, cy - 1, cx + 1, cy + 1, metal)
    line = (230, 184, 74, 255) if fresh else (140, 150, 156, 255)
    dim = shade(line, 0.7)
    # 上の ✦
    for i in range(-3, 4):
        px(img, 16, 6 + i, line)
        px(img, 16 + i, 6, line)
    px(img, 16, 6, (255, 244, 200, 255) if fresh else (220, 226, 230, 255))
    # 4 本の記録の線 (名前の線と時間の線)
    for row in range(4):
        y = 12 + row * 5
        rect(img, 5, y, 5 + 9 + (row * 3 % 5), y, line if row == 0 else dim)
        rect(img, 22, y, 26, y, line if row == 0 else dim)
    if fresh:
        # 縁の光
        for x in range(2, 30):
            px(img, x, 2, (255, 224, 130, 255))
            px(img, x, 29, (255, 224, 130, 255))
        for y in range(2, 30):
            px(img, 2, y, (255, 224, 130, 255))
            px(img, 29, y, (255, 224, 130, 255))
    img.save(out("textures", "block", "record_board_fresh.png" if fresh else "record_board.png"))


def face(tex, uv):
    return {"uv": uv, "texture": tex}


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def models():
    st = "#stone"

    def sides(uv, tex=st):
        return {d: face(tex, uv) for d in ("north", "south", "east", "west")}

    base = box([3, 0, 3], [13, 3, 13], {**sides([3, 13, 13, 16]), "up": face(st, [3, 3, 13, 13]), "down": face(st, [3, 3, 13, 13])})
    pillar = box([5, 2, 5], [11, 13, 11], {**sides([5, 3, 11, 14]), "up": face(st, [5, 5, 11, 11]), "down": face(st, [5, 5, 11, 11])})
    cap = box([4, 12, 4], [12, 14, 12], {**sides([4, 2, 12, 4]), "up": face(st, [4, 4, 12, 12]), "down": face(st, [4, 4, 12, 12])})
    gl = "#glow"
    crystal = box([6, 13, 6], [10, 15.5, 10], {**sides([6, 0, 10, 3], gl), "up": face(gl, [6, 6, 10, 10]), "down": face(gl, [6, 6, 10, 10])})
    stone = {"textures": {"stone": "skyabyss:block/rebirth_stone", "glow": "skyabyss:block/rebirth_stone_glow", "particle": "skyabyss:block/rebirth_stone"},
             "elements": [base, pillar, cap, crystal]}
    json.dump(stone, open(out("models", "block", "rebirth_stone.json"), "w", encoding="utf-8"), indent=2)
    json.dump({"variants": {"": {"model": "skyabyss:block/rebirth_stone"}}}, open(out("blockstates", "rebirth_stone.json"), "w"), indent=2)

    for name in ("record_board", "record_board_fresh"):
        t = "#front"
        edge = {d: face(t, [0, 14, 16, 16]) for d in ("east", "west", "up", "down")}
        plaque = box([1, 5, 6.5], [15, 15, 9.5], {"north": face(t, [0, 0, 16, 16]), "south": face("#back", [0, 0, 16, 16]), **edge})
        leg_edge = {d: face("#back", [0, 0, 2, 6]) for d in ("north", "south", "east", "west", "up", "down")}
        leg_l = box([2.5, 0, 7.2], [4.5, 6, 8.8], leg_edge)
        leg_r = box([11.5, 0, 7.2], [13.5, 6, 8.8], leg_edge)
        model = {"textures": {"front": "skyabyss:block/" + name, "back": "skyabyss:block/record_board", "particle": "skyabyss:block/record_board"},
                 "elements": [leg_l, leg_r, plaque]}
        json.dump(model, open(out("models", "block", name + ".json"), "w", encoding="utf-8"), indent=2)
    variants = {}
    for fresh in ("false", "true"):
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            variants["facing=%s,fresh=%s" % (facing, fresh)] = {"model": "skyabyss:block/record_board" + ("_fresh" if fresh == "true" else ""), "y": rot}
    json.dump({"variants": variants}, open(out("blockstates", "record_board.json"), "w"), indent=2)


if __name__ == "__main__":
    back_texture()
    stone_texture()
    board_texture(False)
    board_texture(True)
    models()
    print("ok")
