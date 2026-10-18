"""火の粉蛾: 今のモデルと低ポリ 3 案の比較画像・メッシュ・アトラス・manifest を書き出す。

実行: python tools/rough/lowpoly/ember_moth/compare.py
出力: docs/review/lowpoly_roughs/lp_ember_moth/{a,b,c}/ ・ lp_ember_moth.png ・ lp_ember_moth_manifest.json
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from em_common import *  # noqa
from PIL import Image, ImageDraw  # noqa
import em_a, em_b, em_c  # noqa
import old as OLD  # noqa

CY, GR, WH = (255, 190, 110), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 燃え殻の灰蛾", em_a), ("b", "B 玄武の剣翅蛾", em_b), ("c", "C 火の粉のはね飾り蛾", em_c)]
COLW, GAP, X0 = 376, 16, 24


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def static_a():
    """選ばれた雷蛾 A (綿毛の雷蛾) を、書き出さずにメモリ上で作る (対比用)。"""
    sys.path.insert(0, str(HERE.parent / "static_moth"))
    import importlib
    sm_common = importlib.import_module("sm_common")
    sm_a = importlib.import_module("sm_a")
    faces = sm_a.build()
    size, P, N, M, L = build_atlas(faces, sm_a.HINT, 0)
    atlas, glow = sm_common.paint(faces, size, P, N, M, sm_a.PAL, sm_a.decor)
    return dict(faces=faces, atlas=atlas, glow=glow, size=size)


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod, mod.HINT)
        r["label"] = label
        r["mod"] = mod
        results.append(r)
        print(key, "tris", r["data"]["triangle_count"], r["size"],
              {k: v["triangle_count"] for k, v in r["data"]["bones"].items() if k != "root"})
    F = voxel.font
    of, n_cubes = OLD.old_faces()
    ofl, _ = OLD.old_faces(50)
    sa = static_a()
    W = 1600
    canvas = Image.new("RGB", (W, 3300), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "火の粉蛾 (ember_moth) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "骨は今の geo と同じ (root > body > head / wing_l / wing_r)。今のアニメがそのまま動く。翅は両面の薄い板 (no-cull)。"
                     "1 ブロック = 32 テクセル・最近傍。光る画素 = 目・熾の縁・火の筋。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    def row(top, h, title, old_fn, new_fn):
        d.text((X0, top - 22), title, font=F(16), fill=WH)
        for i in range(4):
            x = colx(i)
            bgp = bg_panel(COLW, h)
            if i == 0:
                bgp.paste(old_fn(), (0, 0))
            else:
                im = new_fn(results[i - 1])
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (x, top))
            frame(x, top, COLW, h)
        return top + h + 8

    def fo(*a):
        return lambda: OLD.C.fit_old(*a)

    H1 = 300
    top = 108
    S0 = 10.5
    top = row(top, H1, "上から見た 3/4", fo(of, 212, 28, (COLW, H1), S0, (0, 7.5, 1.5)),
              lambda r: render_pose(r, 212, 28, (COLW, H1), (0, 8.0, 1.5), S0)) + 52
    for i in range(4):
        x = colx(i)
        if i == 0:
            t, col = f"今 (箱 {n_cubes} 個)", WH
        else:
            r = results[i - 1]
            t, col = f"{r['label']}  {r['data']['triangle_count']} 三角形", CY
        d.text((x, top - 52), t, font=F(19), fill=col)
    top = row(top, H1, "正面", fo(of, 180, 4, (COLW, H1), S0, (0, 7.5, 0)),
              lambda r: render_pose(r, 180, 4, (COLW, H1), (0, 7.5, 0), S0)) + 24
    top = row(top, H1, "背面", fo(of, 0, 10, (COLW, H1), S0, (0, 7.5, 1.5)),
              lambda r: render_pose(r, 0, 10, (COLW, H1), (0, 8.0, 1.5), S0)) + 24
    H4 = 340
    top = row(top, H4, "翅を広げた姿勢 (真上から。休みの形。縁・指・穴の形がここで分かる)",
              fo(of, 180, 84, (COLW, H4), 11.0, (0, 7.5, 2.0)),
              lambda r: render_pose(r, 180, 84, (COLW, H4), (0, 7.5, 2.0), 11.0)) + 24
    top = row(top, H1, "翅を打ち上げた姿勢 (羽ばたきの上の端。先が 50 度上がる。斜め前から)",
              fo(ofl, 205, 10, (COLW, H1), S0, (0, 8.5, 0.5)),
              lambda r: render_pose(r, 205, 10, (COLW, H1), (0, 8.5, 0.5), S0, flap=50)) + 24
    H3 = 330
    top = row(top, H3, "顔のアップ (前)", fo(of, 190, 6, (COLW, H3), 26.0, (0, 8.5, -7.5)),
              lambda r: render_pose(r, 190, 6, (COLW, H3), r["mod"].HEAD_CENTER, r["mod"].HEAD_SCALE)) + 24
    # ---------------- 雷蛾 A との対比 (同じ縮尺の真上と正面)
    H5 = 300
    d.text((X0, top - 22), "雷蛾 (選んだ A 綿毛の雷蛾 = 青灰・白・銅) との対比 (同じ縮尺。左端が雷蛾、右 3 つが火の粉蛾)", font=F(16), fill=WH)
    for k, (yaw, pitch, cen, sc) in enumerate([(180, 84, (0, 7.5, 2.0), 11.0)]):
        for i in range(4):
            x = colx(i)
            bgp = bg_panel(COLW, H5)
            if i == 0:
                im = render(sa["faces"], sa["atlas"], sa["glow"], yaw, pitch, (COLW, H5), (0, 6.5, 0.5), sc)
            else:
                im = render_pose(results[i - 1], yaw, pitch, (COLW, H5), cen, sc)
            bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (x, top))
            frame(x, top, COLW, H5)
    d.text((colx(0), top + 6), "雷蛾 A: 丸い翅・白い綿毛・コバルトの目玉", font=F(14), fill=WH)
    top += H5 + 8
    top += 6
    H6 = 260
    for i in range(4):
        x = colx(i)
        bgp = bg_panel(COLW, H6)
        if i == 0:
            im = render(sa["faces"], sa["atlas"], sa["glow"], 212, 22, (COLW, H6), (0, 6.5, 0.5), 12.5)
        else:
            im = render_pose(results[i - 1], 212, 22, (COLW, H6), (0, 7.5, 1.5), 12.5)
        bgp.paste(im, (0, 0), im)
        canvas.paste(bgp, (x, top))
        frame(x, top, COLW, H6)
    top += H6 + 40
    # ---------------- アトラスと説明
    notes = {
        "a": ["顔: 灰かぶりの丸い頭、赤熱の大きな目、焦げた小枝の触角 (先が赤く光る)",
              "体: 炭の黒に灰の背 (瘤)、腹の熾の帯",
              "翅: 焼け焦げた紙。ぎざぎざに焼け落ちた縁 (炭の黒 → 橙に光る)、焼け抜けた穴",
              "色: 灰・炭・熾の橙 (白と青は使わない)"],
        "b": ["顔: 吊り上がった赤い目、前へ突き出す 2 本の角、玄武岩の頭",
              "体: 黒い玄武岩の胸に背びれと肩当て、腹は節ごとに熔岩の帯、尾の針",
              "翅: 後ろへ流れる剣のような板 (矢じりの輪郭)。割れ目と板の継ぎ目に熔岩",
              "色: 黒い玄武岩・赤〜橙〜黄の熔岩"],
        "c": ["顔: 大きな丸い目 (熾の橙)、櫛状の触角",
              "体: 細長い炭の腹、節が石炭のように光る、長い脚",
              "翅: 4 本の細い羽の指に裂けた翅。指の先ほど赤 → 橙 → 黄の火の色、指のあいだは空き",
              "火の粉: 指の先から離れて漂う小さな菱形 (翅の骨の一部)"],
    }
    d.text((X0, top - 16), "アトラス (2 倍表示) と設計メモ", font=F(16), fill=WH)
    x = colx(0)
    tx = Image.open(ASSETS / "textures/entity/ember_moth.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    bgc = bgc.convert("RGB")
    canvas.paste(bgc, (x, top + 4))
    d.text((x, top + bgc.height + 10), f"今のテクスチャ {tx.width}×{tx.height}", font=F(14), fill=WH)
    y = top + bgc.height + 40
    for t in (f"今: 箱 {n_cubes} 個", "予算: 普通の敵 約 600 三角形", "骨: root(Y 180 度) > body >", "   head / wing_l / wing_r",
              "アニメ: idle walk attack hurt death"):
        d.text((x, y), t, font=F(13), fill=GR)
        y += 20
    asz_max = 0
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        asz = r["size"] * 2
        asz_max = max(asz_max, asz)
        canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (x, top + 4))
        frame(x, top + 4, asz, asz)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        yy = top + asz + 14
        d.text((x, yy), f"{r['label']}  アトラス {r['size']}×{r['size']}", font=F(15), fill=CY)
        yy += 24
        d.text((x, yy), f"体 {pt('body')} / 頭 {pt('head')} / 翅 {pt('wing_l')}×2 = {r['data']['triangle_count']}",
               font=F(14), fill=GR)
        yy += 24
        for t in notes[r["key"]]:
            for s0 in range(0, len(t), 25):
                d.text((x, yy), t[s0:s0 + 25], font=F(13), fill=GR)
                yy += 18
    end = top + asz_max + 300
    out = OUT / "lp_ember_moth.png"
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], end)))
    canvas.save(out)
    print("saved", out, canvas.size)

    tri = {k: r["data"]["triangle_count"] for k, r in zip("abc", results)}
    manifest = {
        "id": "lp_ember_moth", "short": "火の粉蛾 低ポリ", "kind": "低ポリ 3案",
        "title": "火の粉蛾 (ember_moth) 低ポリ非立方体 3 案",
        "sheets": ["lowpoly_roughs/lp_ember_moth.png"],
        "extra": ("設計の狙い: 余燼の巨神の言葉 (灰・くすぶる熾・炭・玄武岩・縁が熱で光る) で作り、雷翼の雷蛾 (選んだ A 綿毛の雷蛾: 丸く波打つ翅・"
                  "白い綿毛・銅の翅脈・コバルトの目玉、青灰・白・銅) と輪郭も色もはっきり変えた。雷蛾は丸く柔らかいが、こちらは 3 案とも"
                  "ぎざぎざ・矢じり・裂けた指と、青と白を使わない黒・灰・赤橙の熱の色。統一スタイル (非立方体の低ポリ + 32テクセル/ブロックのドット絵) 。"
                  "骨の名前・階層・pivot は現行 geo と同じ (root は Y 180 度回転、body の子が head / wing_l / wing_r。頂点は geo 空間で、"
                  "x と z を反転して書き出してある)。翅は両面の薄い板で縁に厚みがある (描画は no-cull)。羽ばたき・体のゆれ・死に方など今のアニメがそのまま動く。"
                  "光る画素は目・熾の縁や筋・腹の帯・触角の先・火の粉。 "
                  f"三角形: A {tri['a']} / B {tri['b']} / C {tri['c']} (目標: 普通の敵 約 600、蛾は小さいので軽め可)。アトラス "
                  + " / ".join(f"{k.upper()} {r['size']}x{r['size']}" for k, r in zip("abc", results))
                  + "。出力: docs/review/lowpoly_roughs/lp_ember_moth/{a,b,c}/。生成: tools/rough/lowpoly/ember_moth/compare.py。"),
        "options": {
            "a": {"name": "A 燃え殻の灰蛾",
                  "desc": "燃え残りの紙のようにぼろぼろに焦げた幅広の翅。縁はぎざぎざに焼け落ちて炭の黒から橙へ光り、焼け抜けた穴のふちも熾の橙。灰かぶりの炭の体、焦げた小枝の触角、赤熱の目。灰の巨神の「燃えかす」らしい。"},
            "b": {"name": "B 玄武の剣翅蛾",
                  "desc": "スズメガ型。後ろへ流れる剣のような矢じりの翅は黒い玄武岩の板で、割れ目と継ぎ目に熔けた橙〜黄が走る。背びれ・肩当て・尾の針、節ごとに光る腹、前へ突き出す 2 本の角、吊り上がった赤い目。いちばん敵らしく硬い。"},
            "c": {"name": "C 火の粉のはね飾り蛾",
                  "desc": "トリバネガ型。翅が 4 本の細い羽の指に裂け、指の先ほど赤→橙→黄の炎の舌になる。指のあいだは空き、先から火の粉が離れて漂う。細長い炭の体で腹の節は石炭のように光る。いちばん軽やかで「火の粉」そのもの。"},
        },
    }
    (OUT / "lp_ember_moth_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
