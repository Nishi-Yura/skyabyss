"""4 武器 x 3 案 (+ 採りたい案の階級・強化) を作り、メッシュ・アトラス・glowmask・シート・manifest を出す。
実行: python build_all.py"""
from __future__ import annotations

import importlib
import json
import sys

from PIL import ImageDraw, ImageFont

from scene import *
from paint import paint
from mock import third_person, first_person

CHOSEN = dict(whale="a", tide="a", thunder="b", ember="a")
WEAPONS = [  # (key, module, file id, 日本語名, 巨神)
    ("whale", "d_whale", "whale_greatsword", "鯨骨の大剣", "岩鯨", "whalebone_greatsword"),
    ("tide", "d_tide", "tide_trident", "潮殻の三叉槍", "潮殻", "tide_shell_trident"),
    ("thunder", "d_thunder", "thunder_bow", "雷翼の弓", "雷翼", "thunder_wing_bow"),
    ("ember", "d_ember", "ember_war_axe", "余燼の戦斧", "余燼", "ember_war_axe"),
]
OPT_NAMES = {
    "whale": dict(a="A 肋の曲刃", b="B 牙の大剣", c="C 背骨の大剣"),
    "tide": dict(a="A 珊瑚の枝槍", b="B 巻貝と帆立", c="C 三叉の牙"),
    "thunder": dict(a="A 羽の剛弓", b="B 翼弓", c="C 雷の弓"),
    "ember": dict(a="A 割れ板の斧", b="B 二枚刃の鍛造斧", c="C 岩塊の戦斧"),
}
PLAT = (36, 40, 48)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(mod, o, rarity="common", plus=0, stage=None):
    fn, inlay, em = mod.OPTS[o]
    faces = fn(rarity) if stage is None else fn(rarity, stage)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rarity, plus, inlay, em)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def save(r, d, stem, name):
    d.mkdir(parents=True, exist_ok=True)
    save_img(r["atlas"], d / f"{stem}_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, d / f"{stem}_glowmask.png")
    export_mesh(r["faces"], r["size"], d / f"{stem}_mesh.json", name, f"{stem}_atlas.png", f"{stem}_glowmask.png")


def tile(r, titan, yaw, pitch, size, scale, cen=(0, 8, 0)):
    im = view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, cen, scale)
    bg = Image.new("RGBA", size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def main():
    results = {}
    counts = {}
    for key, modname, fid, jp, titan_jp, cur in WEAPONS:
        mod = importlib.import_module(modname)
        res = {}
        for o in "abc":
            d = OUT / o
            if key == "thunder":
                for st in range(4):
                    r = make(mod, o, stage=st)
                    stem = fid if st == 0 else f"{fid}_pull{st - 1}"
                    save(r, d, stem, stem)
                    res[(o, "common", 0, st)] = r
            else:
                r = make(mod, o)
                save(r, d, fid, fid)
                res[(o, "common", 0, 0)] = r
        ch = CHOSEN[key]
        d = OUT / ch
        for rar in ("rare", "legend"):
            for st in (range(4) if key == "thunder" else [0]):
                r = make(mod, ch, rar, stage=st if key == "thunder" else None)
                stem = f"{fid}_{rar}" + ("" if st == 0 else f"_pull{st - 1}") if key == "thunder" else f"{fid}_{rar}"
                if key == "thunder" and st == 0:
                    stem = f"{fid}_{rar}"
                save(r, d, stem, stem)
                res[(ch, rar, 0, st)] = r
        # 強化: 同じメッシュで、アトラスと glowmask だけ差し替え (レアで見せる。伝説も作る)
        for rar in ("rare", "legend"):
            for plus in (5, 10):
                for st in (range(4) if key == "thunder" else [0]):
                    r = make(mod, ch, rar, plus, stage=st if key == "thunder" else None)
                    stem = f"{fid}_{rar}_plus{plus}" + ("" if st == 0 or key != "thunder" else f"_pull{st - 1}")
                    d.mkdir(parents=True, exist_ok=True)
                    save_img(r["atlas"], d / f"{stem}_atlas.png")
                    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
                    gm[r["glow"]] = (255, 255, 255, 255)
                    save_img(gm, d / f"{stem}_glowmask.png")
                    res[(ch, rar, plus, st)] = r
        results[key] = res
        counts[key] = {f"{k[0]}/{k[1]}/+{k[2]}/s{k[3]}": v["tris"] for k, v in res.items() if k[2] == 0}
        print(key, counts[key])
    sheet(results)
    manifest(results)


# ------------------------------------------------------------------ シート

TW, TH = 190, 300
X0 = 20


def label(draw, xy, text, sz=15, fill=(230, 232, 238)):
    draw.text(xy, text, font=font(sz), fill=fill)


def sheet(results):
    WIDTH = 1640
    rows = []
    for key, modname, fid, jp, titan_jp, cur in WEAPONS:
        mod = importlib.import_module(modname)
        res = results[key]
        ch = CHOSEN[key]
        h = 40 + (TH + 26) + (TH + 26) + (190 + 26 if key == "thunder" else 0) + 14
        im = Image.new("RGB", (WIDTH, h), PLAT)
        dr = ImageDraw.Draw(im)
        label(dr, (X0, 8), f"{titan_jp}  {jp}   (採りたい案: {OPT_NAMES[key][ch]})", 22, (255, 232, 160))
        y = 40
        # 行 1: 現在 / A / B / C  (正面と 3/4)
        cf, ca, cg = current_scene(cur)
        scale = 11.6
        x = X0
        curr_tris = None
        pair = [(cf, ca, cg)]
        for yaw, pitch in ((180, 0), (150, -12)):
            t = view(cf, ca, cg, mod.TITAN, yaw, pitch, (TW, TH), (0, 8, 0), scale)
            bg = Image.new("RGBA", (TW, TH), (30, 34, 42, 255))
            bg.alpha_composite(t)
            im.paste(bg.convert("RGB"), (x, y))
            x += TW + 4
        label(dr, (X0, y + TH + 2), f"現在 (ボクセル) 面 {len(cf)}", 14, (200, 200, 210))
        for o in "abc":
            r = res[(o, "common", 0, 0)]
            x0 = x
            for yaw, pitch in ((180, 0), (150, -12)):
                t = tile(r, mod.TITAN, yaw, pitch, (TW, TH), scale)
                im.paste(t, (x, y))
                x += TW + 4
            tg = "  ★" if o == ch else ""
            label(dr, (x0, y + TH + 2), f"{OPT_NAMES[key][o]}  三角形 {r['tris']}{tg}", 14, (255, 232, 160) if o == ch else (230, 232, 238))
        y += TH + 26
        # 行 2: 一人称 / 三人称 / 階級 / 強化
        base = res[(ch, "common", 0, 0)]
        x = X0
        fp = first_person(base["faces"], base["atlas"], base["glow"], mod.TITAN, mod.GRIP)
        bg = Image.new("RGBA", fp.size, (30, 34, 42, 255)); bg.alpha_composite(fp)
        im.paste(bg.convert("RGB"), (x, y)); label(dr, (x, y + TH + 2), "一人称 (模式)", 14); x += fp.size[0] + 4
        tp = third_person(base["faces"], base["atlas"], base["glow"], mod.TITAN, mod.GRIP)
        bg = Image.new("RGBA", tp.size, (30, 34, 42, 255)); bg.alpha_composite(tp)
        im.paste(bg.convert("RGB"), (x, y)); label(dr, (x, y + TH + 2), "三人称 (模式)", 14); x += tp.size[0] + 12
        rw = 150
        for rar, nm in (("common", "通常"), ("rare", "レア"), ("legend", "伝説")):
            r = res[(ch, rar, 0, 0)]
            t = tile(r, mod.TITAN, 165, -8, (rw, TH), 10.6)
            im.paste(t, (x, y)); label(dr, (x, y + TH + 2), f"{nm} {r['tris']}面", 14); x += rw + 3
        x += 9
        for plus in (0, 5, 10):
            r = res[(ch, "rare", plus, 0)]
            t = tile(r, mod.TITAN, 165, -8, (rw, TH), 10.6)
            im.paste(t, (x, y)); label(dr, (x, y + TH + 2), f"レア +{plus}", 14); x += rw + 3
        y += TH + 26
        if key == "thunder":
            # 行 3: 引き絵 4 段階 x 3 案
            sw = 126
            x = X0
            for o in "abc":
                for st in range(4):
                    r = res[(o, "common", 0, st)]
                    t = tile(r, mod.TITAN, 180, 0, (sw, 190), 8.2, (-2, 8, 0))
                    im.paste(t, (x, y))
                    label(dr, (x + 2, y + 192), f"{o.upper()} " + ("休め" if st == 0 else f"引き{st - 1}") + f" {r['tris']}", 12)
                    x += sw + 2
                x += 10
        rows.append(im)
    H = sum(r.size[1] for r in rows) + 90
    W = Image.new("RGB", (WIDTH, H), PLAT)
    dr = ImageDraw.Draw(W)
    label(dr, (X0, 10), "武器の低ポリ 3 案  (32 テクセル/ブロック。三角形数は通常。★ = 階級と強化を見せる案)", 24, (255, 255, 255))
    label(dr, (X0, 44), "階級: 通常 → レア (縁が青み、部品 +1、光 +少し) → 伝説 (縁が金、輪郭が増える、光 +)。強化: メッシュは同じ、絵と光る画素だけ +5 / +10 で変わる。", 15, (200, 205, 215))
    y = 80
    for r in rows:
        W.paste(r, (0, y)); y += r.size[1]
    p = ROOT / "docs/review/lowpoly_roughs/weapons.png"
    W.save(p)
    print("sheet", W.size)


def manifest(results):
    def n(key, o, rar="common", st=0):
        return results[key][(o, rar, 0, st)]["tris"]
    desc = {
        "whale": dict(
            a="肋の曲刃: 反った 1 本の鯨の肋が刃。背に苔のこぶ 3 つ、鍔は反り上がる石の肋 2 本。",
            b="牙の大剣: まっすぐな太い牙の刃に石の環。鍔は鯨の尾びれ (左右に開く扇)、根元に背びれの棘。",
            c="背骨の大剣: 厚い骨の板の背に、棘つきの椎骨の石の柱。鍔は六角の円盤。最も武骨。"),
        "tide": dict(
            a="珊瑚の枝槍: 真珠色の木の葉形の穂と、枝分かれして反る珊瑚の側の穂 2 本 (鹿の角形)。",
            b="巻貝と帆立: 縞の巻貝の穂と、左右に開く帆立の扇 2 枚。丸く、穏やか。",
            c="三叉の牙: 返しつきの 3 本の牙 (側は内へ曲がる銛)。根元に開いた二枚貝の襟。"),
        "thunder": dict(
            a="羽の剛弓: 紺の鋼の肢、腹に白い羽 3 枚ずつ、銅の先。今の弓に最も近い。",
            b="翼弓: 銅の肢に風切羽 (紺・白・紺) が重なる翼の輪郭。上下に翼、握りに銅の巻き。",
            c="雷の弓: 肢が稲妻のように折れ、折れ目が光る。先に羽房。最も尖った輪郭。"),
        "ember": dict(
            a="割れ板の斧: 玄武岩の頭が刃の手前で割れ、割れ目が光る。刃は鍛冶の鉄、背に鉄の棘。",
            b="二枚刃の鍛造斧: 鉄の核が光る、大小の三日月の刃 (両刃)。上に棘。",
            c="岩塊の戦斧: 3 つの岩を鉄の帯でくくった頭に鋸の刃。割れ目が光る。最も粗い。"),
    }
    options = {}
    for o in "abc":
        txt = []
        for key, modname, fid, jp, titan_jp, cur in WEAPONS:
            txt.append(f"{jp} {OPT_NAMES[key][o][2:]}: {desc[key][o]} 三角形 {n(key, o)}"
                       + (f" (引き絵は矢つきで {n(key, o, 'common', 1)})" if key == "thunder" else "") + "。")
        options[o] = {"name": {"a": "A 巨神の生き物の素材 (骨・珊瑚・羽・割れた板) を 1 本の太い輪郭に",
                               "b": "B 輪郭を広げる (翼・扇・二枚刃・尾びれ)",
                               "c": "C 部品を積む・尖らせる (椎骨・牙・稲妻・岩塊)"}[o],
                      "desc": " / ".join(txt)}
    extra = (
        "手に持つ武器の 3D を防具と同じ基盤 (輪郭が先 / 大中小 / 巨神の素材の言葉 / 一点の飾り) で作り直す低ポリのラフ。"
        "4 武器 (鯨骨の大剣・潮殻の三叉槍・雷翼の弓・余燼の戦斧) x 3 案。各行の上段は今のボクセル (面 745〜1,899) と A/B/C の正面・3/4、"
        "下段は ★ の案の一人称・三人称の模式、通常→レア→伝説、レア +0/+5/+10。雷翼の弓は引き絵 4 段階 (休め・引き 0/1/2) を 3 案とも作った。"
        "三角形 (通常 / レア / 伝説、★ の案): "
        + " / ".join(f"{jp} {n(k, CHOSEN[k])}・{n(k, CHOSEN[k], 'rare')}・{n(k, CHOSEN[k], 'legend')}" for k, _, _, jp, _, _ in WEAPONS)
        + "。弓の引き絵は矢 (約 32 面) を含む。予算案: 通常 250 以下 / レア 300 以下 / 伝説 350 以下 (今はレアで 294・伝説で 342 まで)。"
        "階級の見分け: 輪郭が先 (レアで部品 +1 と縁の金属、伝説で輪郭の大きな一点 + 縁が金)、光は 2 番目。縁の金属の色が階級の色 (レア = 青み、伝説 = 金。03 の枠の色と同じ)。"
        "強化はメッシュを変えず、絵と glowmask だけ差し替え (+5 覚醒で刃の光の筋と縁の金属が一段明るい、+10 で光の筋が通って縁が光る)。"
        "作り方は tools/rough/lowpoly/weapons/ (build_all.py)。"
        "採りたい案の目安: 岩鯨 A・潮殻 A・雷翼 B・余燼 A (輪郭が 4 本で重ならず、材質の言葉が敵と合う)。"
    )
    m = {"id": "lp_weapons", "short": "武器", "kind": "低ポリ 3 案",
         "title": "武器 — 低ポリ非立方体 + ドット絵 3 案 (4 巨神の代表武器。階級・強化の見た目つき)",
         "sheets": ["lowpoly_roughs/weapons.png"], "extra": extra, "options": options}
    p = ROOT / "docs/review/lowpoly_roughs/weapons_manifest.json"
    p.write_text(json.dumps(m, ensure_ascii=False, indent=2), encoding="utf-8")
    print("manifest", p)


if __name__ == "__main__":
    main()
