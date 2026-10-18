"""泡吹き: 今のモデルと低ポリ 3 案の比較画像・メッシュ・アトラス・manifest を書き出す。

実行: python tools/rough/lowpoly/bubble_puffer/compare.py
出力: docs/review/lowpoly_roughs/lp_bubble_puffer/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/lp_bubble_puffer.png / lp_bubble_puffer_manifest.json
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from pf_common import *  # noqa
from PIL import Image, ImageDraw  # noqa
import opt_a, opt_b, opt_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A まんまる提灯", opt_a), ("b", "B 棘玉の鎧", opt_b), ("c", "C ラッパ喉", opt_c)]
COLW, GAP, X0 = 376, 16, 24


def bg_panel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


# ---------------------------------------------------------------- 今のモデル (箱)
def old_faces(inflated=False):
    g = json.loads((ASSETS / "geo/entity/bubble_puffer.geo.json").read_text(encoding="utf-8"))
    tex = ASSETS / "textures/entity/bubble_puffer.png"
    gm = ASSETS / "textures/entity/bubble_puffer_glowmask.png"
    if not inflated:
        return voxel.load_geo(g, tex, gm), sum(len(b.get("cubes", [])) for b in g["minecraft:geometry"][0]["bones"])
    out = []
    for only_spines in (True, False):
        gg = copy.deepcopy(g)
        for b in gg["minecraft:geometry"][0]["bones"]:
            if (b["name"] == "spines") != only_spines:
                b["cubes"] = []
        fs = voxel.load_geo(gg, tex, gm)
        k = INFLATE_BODY * (INFLATE_SPINES if only_spines else 1.0)
        for f in fs:
            f.origin = CEN + (f.origin - CEN) * k
            f.eu = f.eu * k
            f.ev = f.ev * k
        out += fs
    return out, 0


def fit_old(faces, yaw, pitch, size, scale, center):
    """今のモデルを、作図と同じ縮尺 (1 単位 = scale ピクセル)・同じ中心で描いて size に収める。"""
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
    shift = (cc - bc) * scale                      # 画面 (x 右, y 上) での中心のずれ
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
    ofi, _ = old_faces(True)
    W = 1600
    canvas = Image.new("RGB", (W, 2400), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((24, 14), "泡吹き (bubble_puffer) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (ゲームに入れる版の形式)", font=F(26), fill=WH)
    d.text((24, 50), "骨は今の geo と同じ (root > body > spines / fin_r / fin_l / tail)。今のアニメ (膨らみ・ひれ・尾) がそのまま動く。"
                     "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。膨らみは body 1.45 倍・spines さらに 1.12 倍。",
           font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    def colx(i):
        return X0 + i * (COLW + GAP)

    names = ["今 (箱)"] + [o[1] for o in OPTS]
    S0 = 12.0

    def row(top, h, title, old_fn, new_fn, note=None):
        d.text((X0, top - 22), title, font=F(16), fill=WH)
        for i in range(4):
            x = colx(i)
            bgp = bg_panel(COLW, h)
            if i == 0:
                im = old_fn()
                bgp.paste(im, (0, 0))
            else:
                im = new_fn(results[i - 1])
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (x, top))
            frame(x, top, COLW, h)
        return top + h + 8

    top = 108
    H1 = 330
    CYM = (0, 6.8, 0)
    top = row(top, H1, "前 3/4", lambda: fit_old(of, 212, 13, (COLW, H1), S0, (0, 6.8, 0)),
              lambda r: render_pose(r, 212, 13, (COLW, H1), CYM, S0)) + 52
    # 見出し (三角形の数)
    for i in range(4):
        x = colx(i)
        if i == 0:
            t, col = f"今 (箱 {n_cubes} 個)", WH
        else:
            r = results[i - 1]
            t, col = f"{r['label']}  {r['data']['triangle_count']} 三角形", CY
        d.text((x, top - 52), t, font=F(19), fill=col)
    top = row(top, H1, "後ろ 3/4", lambda: fit_old(of, 32, 13, (COLW, H1), S0, (0, 6.8, 0)),
              lambda r: render_pose(r, 32, 13, (COLW, H1), CYM, S0)) + 24
    H3 = 330
    top = row(top, H3, "顔のアップ (前)", lambda: fit_old(of, 192, 6, (COLW, H3), 24.0, (0, 5.4, -4.0)),
              lambda r: render_pose(r, 192, 6, (COLW, H3), r["mod"].HEAD_CENTER, 24.0)) + 24
    H4 = 330
    top = row(top, H4, "膨らんだ姿勢 (inflated: body 1.45 倍・棘 さらに 1.12 倍。縮尺は上の段の約 0.63 倍)",
              lambda: fit_old(ofi, 212, 13, (COLW, H4), 7.6, (0, 7.5, 0)),
              lambda r: render_pose(r, 212, 13, (COLW, H4), (0, 7.5, 0), 7.6, inflated=True)) + 24
    H5 = 270
    top = row(top, H5, "真横 (ひれ・尾・口の出方)", lambda: fit_old(of, 90, 0, (COLW, H5), 11.0, (0, 6.5, 1.5)),
              lambda r: render_pose(r, 90, 0, (COLW, H5), (0, 6.5, 1.5), 11.0)) + 20
    # ---------------- アトラスと説明
    top5 = top + 4
    notes = {
        "a": ["顔: 大きな丸い目 (半分かぶさるまぶた)、すぼめた赤い唇、2 本の前歯",
              "体: 12 角の玉。背の青緑の斑・クリーム色の腹",
              "棘: 全身にらせん状 (背は長め)、先の細い短い円錐",
              "ひれ: 丸い扇の胸びれ・二股の尾びれ (筋は縁の線)"],
        "b": ["顔: 吊り上がった細い目・重いまゆ板、横広の口に太い牙 2 本",
              "体: 八角の殻に骨色の帯 2 本。帯から長い棘が星形に出る",
              "棘: 橙と暗い赤の縞。殻にふじつぼ",
              "ひれ: 骨ばった 3 本の刃・短い 3 本棘の尾"],
        "c": ["顔: 短い柄の先の大きな目 (半分閉じたまぶた)、ラッパ形の口と前歯 2 本",
              "体: 後ろへ細くなる涙形。あごの下に泡袋。泡の輪の模様",
              "棘: 背びれの扇と先の短い棘、頬の短い棘だけ",
              "ひれ: 翼のような胸びれ・三枚のひらひらの尾"],
    }
    d.text((X0, top - 16), "アトラス (2 倍表示) と設計メモ", font=F(16), fill=WH)
    x = colx(0)
    tx = Image.open(ASSETS / "textures/entity/bubble_puffer.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    canvas.paste(bgc.convert("RGB"), (x, top + 4))
    d.text((x, top + tx.height + 10), f"今のテクスチャ {tx.width}×{tx.height} (等倍)", font=F(14), fill=WH)
    y = top + tx.height + 40
    for t in (f"今: 箱 {n_cubes} 個 (面 882)", "予算: 普通の敵 約 600 三角形", "骨: root(Y 180 度) > body >",
              "   spines / fin_r / fin_l / tail", "アニメ: idle walk attack hurt death", "   inflate inflated deflate"):
        d.text((x, y), t, font=F(13), fill=GR)
        y += 20
    for i in range(1, 4):
        r = results[i - 1]
        x = colx(i)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        asz = r["size"] * 2
        canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (x, top + 4))
        frame(x, top + 4, asz, asz)
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        yy = top + asz + 14
        d.text((x, yy), f"{r['label']}  アトラス {r['size']}×{r['size']}", font=F(15), fill=CY)
        yy += 24
        d.text((x, yy), f"体 {pt('body')} / 棘 {pt('spines')} / 胸びれ {pt('fin_r')}×2 / 尾 {pt('tail')} = {r['data']['triangle_count']}",
               font=F(14), fill=GR)
        yy += 24
        for t in notes[r["key"]]:
            for s0 in range(0, len(t), 25):
                d.text((x, yy), t[s0:s0 + 25], font=F(13), fill=GR)
                yy += 18
    end = top + 256 + 230
    out = OUT / "lp_bubble_puffer.png"
    canvas = canvas.crop((0, 0, W, min(canvas.size[1], end)))
    canvas.save(out)
    print("saved", out, canvas.size)

    manifest = {
        "id": "lp_bubble_puffer", "short": "泡吹き 低ポリ", "kind": "低ポリ 3案",
        "title": "泡吹き (bubble_puffer) 低ポリ非立方体 3 案",
        "sheets": ["lowpoly_roughs/lp_bubble_puffer.png"],
        "extra": ("設計の狙い: 泡を吹くフグ (唇・2 本の前歯・まぶたの目・ひれ・膨らみ) の同一性を保ち、統一スタイル "
                  "(非立方体の低ポリ + 32テクセル/ブロックのドット絵) で 3 方向の輪郭を作り比べる。骨の名前・階層・pivot は現行 geo と同じ "
                  "(root は Y 180 度回転、spines / fin_r / fin_l / tail は body の子。頂点は geo 空間) なので、今のアニメ "
                  "(idle・walk・attack・hurt・death・inflate・inflated・deflate) がそのまま動く。膨らみは body 1.45 倍・spines さらに 1.12 倍で、"
                  "どの案も膨らんだ姿を比較画像に載せた。光るマスクは瞳のみ。 "
                  + f"三角形: A {results[0]['data']['triangle_count']} / B {results[1]['data']['triangle_count']} / "
                  f"C {results[2]['data']['triangle_count']} (目標: 普通の敵 約 600)。アトラス "
                  + " / ".join(f"{k.upper()} {r['size']}x{r['size']}" for k, r in zip("abc", results))
                  + "。出力: docs/review/lowpoly_roughs/lp_bubble_puffer/{a,b,c}/。生成: tools/rough/lowpoly/bubble_puffer/compare.py。"),
        "options": {
            "a": {"name": "A まんまる提灯",
                  "desc": "面取りした丸い玉の体に、半分まぶたの大きな目、すぼめた赤い唇と 2 本の前歯。全身に先の細い短い棘、丸い扇の胸びれ、二股の尾。いちばんフグらしい輪郭で、膨らむとそのまま玉が大きくなる。青緑の背・クリーム色の腹・赤い唇。"},
            "b": {"name": "B 棘玉の鎧",
                  "desc": "ウニと潮殻を混ぜた八角の甲冑。骨色の帯 2 本から長い棘が星形に出て、目は吊り上がった細い目と重いまゆ板、横広の口に太い牙 2 本。ひれは骨ばった刃、尾は短い 3 本棘。藍の殻・骨色の帯・橙の縞の棘。いちばん敵らしく怖い。"},
            "c": {"name": "C ラッパ喉",
                  "desc": "後ろへ細くなる涙形に、前へ突き出したラッパ形の口とあごの下の泡袋。短い柄の先に大きな目、背の扇びれ、翼のような胸びれ、三枚のひらひらの尾。棘は控えめで「泡を吹く」道具を前に出す。藍の背・ミントの腹と袋・珊瑚色のひれ。"},
        },
    }
    (OUT / "lp_bubble_puffer_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
