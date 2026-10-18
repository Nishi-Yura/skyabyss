"""雷蛾: 今のモデルと低ポリ 3 案の比較画像・メッシュ・アトラス・manifest を書き出す。

実行: python tools/rough/lowpoly/static_moth/compare.py
出力: docs/review/lowpoly_roughs/lp_static_moth/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/lp_static_moth.png / lp_static_moth_manifest.json
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from sm_common import *  # noqa
from PIL import Image, ImageDraw  # noqa
import sm_a, sm_b, sm_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 綿毛の雷蛾", sm_a), ("b", "B 稲妻翅", sm_b), ("c", "C 銅コイルの燕尾蛾", sm_c)]
COLW, GAP, X0 = 376, 16, 24


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_faces(flap=0.0):
    g = json.loads((ASSETS / "geo/entity/static_moth.geo.json").read_text(encoding="utf-8"))
    tex = ASSETS / "textures/entity/static_moth.png"
    gm = ASSETS / "textures/entity/static_moth_glowmask.png"
    pose = {"wing_l": (0, 0, flap), "wing_r": (0, 0, -flap)} if flap else None
    return voxel.load_geo(g, tex, gm, pose), sum(len(b.get("cubes", [])) for b in g["minecraft:geometry"][0]["bones"])


def fit_old(faces, yaw, pitch, size, scale, center):
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    Vm = Rx @ Ry
    pts = np.array([Vm @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    bc = np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2])
    cc = (Vm @ np.asarray(center, float))[:2]
    shift = (cc - bc) * scale
    need = int(max(size) + 2 * max(abs(shift[0]), abs(shift[1])) + 40)
    side = max(need, int(scale * span) + 8)
    m = (1 - scale * span / side) / 2
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(side, side), margin=m)
    x0 = int(round(side / 2 + shift[0] - size[0] / 2))
    y0 = int(round(side / 2 - shift[1] - size[1] / 2))
    return im.crop((x0, y0, x0 + size[0], y0 + size[1])).convert("RGB")


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod, getattr(mod, "HINT", 128))
        r["label"] = label
        r["mod"] = mod
        results.append(r)
        print(key, "tris", r["data"]["triangle_count"], r["size"],
              {k: v["triangle_count"] for k, v in r["data"]["bones"].items() if k != "root"})
    F = voxel.font
    of, n_cubes = old_faces()
    ofl, _ = old_faces(50)
    W = 1600
    canvas = Image.new("RGB", (W, 2800), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "雷蛾 (static_moth) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "骨は今の geo と同じ (root > body > wing_l / wing_r / antennae)。今のアニメがそのまま動く。翅は両面の薄い板 (no-cull)。"
                     "1 ブロック = 32 テクセル・最近傍。光る画素 = 目・翅の稲妻。",
           font=F(15), fill=GR)

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

    CEN = (0, 6.8, 0.5)
    S0 = 13.5
    H1 = 300
    top = 108
    top = row(top, H1, "上から見た 3/4", lambda: fit_old(of, 212, 28, (COLW, H1), S0, CEN),
              lambda r: render_pose(r, 212, 28, (COLW, H1), CEN, S0)) + 52
    for i in range(4):
        x = colx(i)
        if i == 0:
            t, col = f"今 (箱 {n_cubes} 個)", WH
        else:
            r = results[i - 1]
            t, col = f"{r['label']}  {r['data']['triangle_count']} 三角形", CY
        d.text((x, top - 52), t, font=F(19), fill=col)
    top = row(top, H1, "正面", lambda: fit_old(of, 180, 4, (COLW, H1), S0, CEN),
              lambda r: render_pose(r, 180, 4, (COLW, H1), CEN, S0)) + 24
    top = row(top, H1, "背面", lambda: fit_old(of, 0, 10, (COLW, H1), S0, CEN),
              lambda r: render_pose(r, 0, 10, (COLW, H1), CEN, S0)) + 24
    H4 = 340
    top = row(top, H4, "翅を広げた姿勢 (真上から。休みの形。尾・縁の形がここで分かる)",
              lambda: fit_old(of, 180, 84, (COLW, H4), 11.0, (0, 6.8, 2.0)),
              lambda r: render_pose(r, 180, 84, (COLW, H4), (0, 6.8, 2.5), 11.0)) + 24
    top = row(top, H1, "翅を打ち上げた姿勢 (羽ばたきの上の端。先が 50 度上がる。斜め前から)",
              lambda: fit_old(ofl, 205, 10, (COLW, H1), 13.5, (0, 8.0, 0.5)),
              lambda r: render_pose(r, 205, 10, (COLW, H1), (0, 8.0, 0.5), 13.5, flap=50)) + 24
    H3 = 330
    top = row(top, H3, "顔のアップ (前)", lambda: fit_old(of, 190, 6, (COLW, H3), 30.0, (0, 6.8, -4.5)),
              lambda r: render_pose(r, 190, 6, (COLW, H3), r["mod"].HEAD_CENTER, r["mod"].HEAD_SCALE)) + 24
    # ---------------- アトラスと説明
    top5 = top + 4
    notes = {
        "a": ["顔: 丸い複眼 (青白く光る)、銅の巻き管、羽枝のある銅の触角",
              "体: ふさふさの灰青の毛 (襟・胸の毛束)",
              "翅: 丸く波打つ縁 + 白い綿毛。銅の翅脈、コバルトの目玉模様 (中心が白く光る)",
              "放電: 翅の先に小さな稲妻のぎざぎざ"],
        "b": ["顔: 吊り上がった細い目、前へ突き出す 2 本の銅の避雷針の角",
              "体: 暗い藍の殻 + 銅の肩当て・背びれ・腹の帯、尾に避雷針 2 本",
              "翅: 稲妻の折れ線の縁、後ろは長い雷の尾。翅脈は白金に光る稲妻の筋",
              "放電: 筋の光る量 = 帯電 (満ちると明るい)"],
        "c": ["顔: 大きな六角の複眼、細い鞭の触角と先の光の玉",
              "体: 銅線を巻いたコイルの腹 (輪のすき間が光る)、緑青の脚",
              "翅: 銅の枠と淡い水色のガラス板、長い燕尾。枠の交点が青く光る",
              "放電: 尾の先に小さな稲妻"],
    }
    d.text((X0, top - 16), "アトラス (2 倍表示) と設計メモ", font=F(16), fill=WH)
    x = colx(0)
    tx = Image.open(ASSETS / "textures/entity/static_moth.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    bgc = bgc.convert("RGB").resize((tx.width // 2 * 1, tx.height // 2 * 1), Image.NEAREST)
    canvas.paste(bgc, (x, top + 4))
    d.text((x, top + bgc.height + 10), f"今のテクスチャ {tx.width}×{tx.height} (半分に縮小)", font=F(14), fill=WH)
    y = top + bgc.height + 40
    for t in (f"今: 箱 {n_cubes} 個", "予算: 普通の敵 約 600 三角形", "骨: root(Y 180 度) > body >", "   wing_l / wing_r / antennae",
              "アニメ: idle walk attack hurt death", "翅は rest で水平・先が +-14 度ほど上がる"):
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
        d.text((x, yy), f"体 {pt('body')} / 翅 {pt('wing_l')}×2 / 触角 {pt('antennae')} = {r['data']['triangle_count']}",
               font=F(14), fill=GR)
        yy += 24
        for t in notes[r["key"]]:
            for s0 in range(0, len(t), 25):
                d.text((x, yy), t[s0:s0 + 25], font=F(13), fill=GR)
                yy += 18
    end = top + asz_max + 300
    out = OUT / "lp_static_moth.png"
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], end)))
    canvas.save(out)
    print("saved", out, canvas.size)

    tri = {k: r["data"]["triangle_count"] for k, r in zip("abc", results)}
    manifest = {
        "id": "lp_static_moth", "short": "雷蛾 低ポリ", "kind": "低ポリ 3案",
        "title": "雷蛾 (static_moth) 低ポリ非立方体 3 案",
        "sheets": ["lowpoly_roughs/lp_static_moth.png"],
        "extra": ("設計の狙い: 雷翼の巨神の嵐と静電気 (灰青・白・銅・稲妻の白青) で、余燼の火の粒蛾 (ember_moth: 暗い石の灰色・橙の火) と"
                  "はっきり別の見た目にする。統一スタイル (非立方体の低ポリ + 32テクセル/ブロックのドット絵) で 3 方向の輪郭を作り比べた。"
                  "骨の名前・階層・pivot は現行 geo と同じ (root は Y 180 度回転、wing_l / wing_r / antennae は body の子。頂点は geo 空間)。"
                  "翅は両面の薄い板で、厚みのある縁を付けてある (描画は no-cull)。今のアニメ (羽ばたき・体のゆれ・死に方) がそのまま動く。"
                  "光る画素は目と稲妻の筋・目玉模様の中心・触角の先 (帯電すると翅の模様が明るくなる設計に合わせ、翅の光る面積が帯電の量になる)。"
                  f" 三角形: A {tri['a']} / B {tri['b']} / C {tri['c']} (目標: 普通の敵 約 600)。アトラス "
                  + " / ".join(f"{k.upper()} {r['size']}x{r['size']}" for k, r in zip("abc", results))
                  + "。出力: docs/review/lowpoly_roughs/lp_static_moth/{a,b,c}/。生成: tools/rough/lowpoly/static_moth/compare.py。"),
        "options": {
            "a": {"name": "A 綿毛の雷蛾",
                  "desc": "ヤママユガ型。丸く波打つ大きな翅に白い綿毛の縁取りと銅の翅脈、コバルトの目玉模様 (中心が白く光る)。ふさふさの灰青の胸、羽枝のある銅の触角、翅の先に小さな放電。やわらかく可愛いが静電気を帯びた感じ。"},
            "b": {"name": "B 稲妻翅",
                  "desc": "嵐雲色 (暗い藍) のぎざぎざ翅。縁は稲妻の折れ線で後ろの翅は長い雷の尾、翅脈は白金に光る稲妻そのもの。銅の肩当て・背びれ・腹の帯、前へ突き出す 2 本の避雷針の角、吊り上がった細い目。いちばん敵らしく攻撃的。"},
            "c": {"name": "C 銅コイルの燕尾蛾",
                  "desc": "長い燕尾の翅は銅の枠と淡い水色のガラス板を組んだ窓で、枠の交点が小さなコンデンサのように青く光る。腹は銅線を巻いたコイル (輪のすき間が光る)、大きな複眼と光の玉の付いた細い鞭の触角。緑青のさびで発電機の質感。"},
        },
    }
    (OUT / "lp_static_moth_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
