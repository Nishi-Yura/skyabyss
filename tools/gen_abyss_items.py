"""深淵の専用伝説 4 点の絵 (32x32) と item モデル (docs/design/31 の B04)。

  python tools/gen_abyss_items.py

出力 (すでにある絵は上書き): src/main/resources/assets/skyabyss/textures/item/abyss_*.png と models/item/abyss_*.json、
review 用の拡大 (tools/_scratch/abyss_items_preview.png)。

作りはまだ「平らな 32x32 の手持ち」(item/handheld)。立体 (geo・lowpoly) の武器モデルは未作成で、ほかの伝説と同格にするには
ラフ (3 案) → 立体化の工程が要る (docs/design/31 の B04 の「残り」に記した)。色は深淵の紫黒を土台に、巨神ごとに一色の筋を足す:
岩鯨 = 骨の白紫 / 潮殻 = 青緑 / 雷翼 = 黄白の稲光 / 余燼 = 熾の赤橙。
"""
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/skyabyss/textures/item")
MOD = os.path.join(ROOT, "src/main/resources/assets/skyabyss/models/item")

OUTLINE = (14, 10, 22, 255)
DARK = (38, 28, 58, 255)
MID = (70, 52, 104, 255)
LIGHT = (120, 96, 168, 255)
WRAP = (30, 22, 40, 255)
GOLD = (176, 138, 70, 255)


def canvas():
    return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def line_thick(d, p0, p1, w, color):
    d.line([p0, p1], fill=color, width=w)


def outline(img):
    """不透明画素の外側 1 画素に縁を付ける。"""
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(32):
        for x in range(32):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 32 and 0 <= ny < 32 and px[nx, ny][3] > 0:
                        po[x, y] = OUTLINE
                        break
    return out


def greatsword():
    im = canvas()
    d = ImageDraw.Draw(im)
    # 刃: 左下から右上へ、幅 5 の斜め (暗い鋼 + 明るい縁 + 虚の筋)
    for t in range(0, 21):
        x = 7 + t
        y = 24 - t
        d.rectangle([x - 1, y - 2, x + 1, y], fill=MID)
    for t in range(0, 21):
        x = 7 + t
        y = 24 - t
        d.point((x + 1, y - 2), fill=LIGHT)
        d.point((x, y - 2), fill=LIGHT)
        d.point((x - 1, y), fill=DARK)
        if t % 3 != 1:
            d.point((x, y - 1), fill=(190, 120, 255, 255))
    # 切っ先
    d.polygon([(27, 3), (30, 0), (30, 3)], fill=LIGHT)
    # 鍔
    line_thick(d, (4, 21), (11, 28), 2, GOLD)
    # 柄と頭
    line_thick(d, (4, 27), (1, 30), 2, WRAP)
    d.rectangle([0, 29, 2, 31], fill=GOLD)
    return outline(im)


def bell_staff():
    im = canvas()
    d = ImageDraw.Draw(im)
    # 杖の柄 (右下から左上へ)
    line_thick(d, (27, 30), (12, 12), 2, (54, 42, 70, 255))
    line_thick(d, (28, 30), (13, 12), 1, MID)
    # 吊り輪
    d.ellipse([7, 3, 13, 9], outline=GOLD)
    # 鐘 (青緑)
    teal_dark = (20, 56, 66, 255)
    teal = (46, 120, 130, 255)
    teal_light = (110, 200, 196, 255)
    d.polygon([(4, 18), (7, 9), (13, 9), (16, 18)], fill=teal)
    d.polygon([(4, 18), (7, 9), (9, 9), (7, 18)], fill=teal_light)
    d.polygon([(13, 9), (16, 18), (13, 18), (11, 9)], fill=teal_dark)
    d.rectangle([3, 18, 17, 19], fill=teal_dark)
    # 舌 (淡い光)
    d.rectangle([9, 20, 11, 22], fill=(230, 245, 240, 255))
    # 水の筋
    for x in (6, 10, 14):
        d.point((x, 14), fill=(190, 240, 235, 255))
    return outline(im)


def bow(pull):
    """pull: 0 = 静止、1..3 = 引いている (弦が後ろへ。矢つき)。"""
    im = canvas()
    d = ImageDraw.Draw(im)
    limb = (60, 44, 96, 255)
    lim_hi = (150, 120, 210, 255)
    bolt = (255, 232, 120, 255)
    # 弓幹: 左下から右上への大きな弧
    pts = [(6, 27), (5, 23), (5, 19), (6, 15), (8, 11), (11, 7), (15, 4), (20, 2), (25, 1)]
    for i in range(len(pts) - 1):
        line_thick(d, pts[i], pts[i + 1], 3, limb)
    for i in range(len(pts) - 1):
        d.line([(pts[i][0] + 1, pts[i][1] - 1), (pts[i + 1][0] + 1, pts[i + 1][1] - 1)], fill=lim_hi, width=1)
    # 稲光の筋
    for p in ((6, 20), (8, 12), (14, 6), (21, 3)):
        d.point(p, fill=bolt)
    # 弦: 両端から、握りの内側へ。引くほど後ろ (左下方向) へ
    back = (0, 0, 2, 4)[pull] if pull < 4 else 4
    nock = (14 - back * 2, 17 + back * 2)
    string = (230, 228, 240, 255)
    d.line([(6, 27), nock], fill=string)
    d.line([(25, 1), nock], fill=string)
    if pull > 0:
        # 矢: 弦から右上へ
        d.line([nock, (nock[0] + 11, nock[1] - 11)], fill=(214, 200, 170, 255), width=1)
        d.polygon([(nock[0] + 11, nock[1] - 12), (nock[0] + 14, nock[1] - 14), (nock[0] + 12, nock[1] - 9)], fill=bolt)
    return outline(im)


def scythe():
    im = canvas()
    d = ImageDraw.Draw(im)
    # 柄 (左下から右上へ、長い)
    line_thick(d, (4, 29), (22, 7), 2, (58, 44, 52, 255))
    line_thick(d, (5, 29), (23, 7), 1, (96, 76, 84, 255))
    # 刃: 柄の先から左へ大きく曲がる (熾の赤橙の刃先)
    blade = [(21, 7), (17, 3), (11, 2), (6, 4), (3, 8), (8, 6), (13, 6), (18, 9)]
    d.polygon(blade, fill=(80, 60, 90, 255))
    edge = [(3, 8), (6, 4), (11, 2), (17, 3), (21, 7)]
    d.line(edge, fill=(255, 140, 70, 255), width=1)
    d.line([(4, 8), (7, 5), (11, 3), (16, 4)], fill=(255, 200, 120, 255), width=1)
    # 柄の結び目 (灰)
    for p in ((10, 23), (14, 18)):
        d.rectangle([p[0], p[1], p[0] + 1, p[1] + 1], fill=(150, 140, 150, 255))
    d.rectangle([2, 29, 4, 31], fill=GOLD)
    return outline(im)


def save(img, name):
    img.save(os.path.join(TEX, name + ".png"))


def write_model(name, kind):
    if kind == "handheld":
        data = {"parent": "minecraft:item/handheld", "textures": {"layer0": "skyabyss:item/" + name}}
    elif kind == "bow":
        data = {
            "parent": "minecraft:item/bow",
            "textures": {"layer0": "skyabyss:item/" + name},
            "overrides": [
                {"predicate": {"pulling": 1}, "model": "skyabyss:item/%s_pulling_0" % name},
                {"predicate": {"pulling": 1, "pull": 0.65}, "model": "skyabyss:item/%s_pulling_1" % name},
                {"predicate": {"pulling": 1, "pull": 0.9}, "model": "skyabyss:item/%s_pulling_2" % name},
            ],
        }
    else:
        raise ValueError(kind)
    with open(os.path.join(MOD, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    if "--flat" not in sys.argv:
        print("低ポリ化済み (tools/install_lowpoly_abyss_weapons.py)。平らな絵と item/handheld のモデルへ戻すときだけ --flat を付ける")
        return
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(MOD, exist_ok=True)
    save(greatsword(), "abyss_greatsword")
    write_model("abyss_greatsword", "handheld")
    save(bell_staff(), "abyss_bell_staff")
    write_model("abyss_bell_staff", "handheld")
    save(scythe(), "abyss_ash_scythe")
    write_model("abyss_ash_scythe", "handheld")
    save(bow(0), "abyss_storm_bow")
    write_model("abyss_storm_bow", "bow")
    for i in range(3):
        save(bow(i + 1), "abyss_storm_bow_pulling_%d" % i)
        with open(os.path.join(MOD, "abyss_storm_bow_pulling_%d.json" % i), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"parent": "minecraft:item/bow_pulling_%d" % i, "textures": {"layer0": "skyabyss:item/abyss_storm_bow_pulling_%d" % i}}, f, indent=2)
            f.write("\n")
    # 確認用の拡大 (4 点を横に、8 倍)
    names = ["abyss_greatsword", "abyss_bell_staff", "abyss_storm_bow", "abyss_ash_scythe"]
    sheet = Image.new("RGBA", (32 * 8 * 4 + 20, 32 * 8 + 8), (24, 24, 30, 255))
    for i, n in enumerate(names):
        t = Image.open(os.path.join(TEX, n + ".png")).resize((256, 256), Image.NEAREST)
        sheet.alpha_composite(t, (4 + i * 261, 4))
    scratch = os.path.join(ROOT, "tools", "_scratch")
    os.makedirs(scratch, exist_ok=True)
    sheet.save(os.path.join(scratch, "abyss_items_preview.png"))
    print("ok")


if __name__ == "__main__":
    main()
