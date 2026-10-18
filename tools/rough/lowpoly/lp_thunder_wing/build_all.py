"""雷翼 (lp_thunder_wing) の低ポリ 3 案を作り、比較シートと manifest を書く。

実行: python tools/rough/lowpoly/lp_thunder_wing/build_all.py
出力: docs/review/lowpoly_roughs/lp_thunder_wing/{a,b,c}/{near,mid,far}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_thunder_wing.png
      docs/review/lowpoly_roughs/lp_thunder_wing_manifest.json
"""
from __future__ import annotations

import importlib
import json

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
F = voxel.font
CY, GR, WH = (255, 190, 110), (170, 164, 160), (240, 232, 220)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]
NAMES = {"a": "A 嵐の大鷲 (忠実)", "b": "B 避雷の銅鎧鳥", "c": "C 嵐を纏う羽毛の鳥"}
DESC = {
    "a": ("今の姿 (幅広い翼・指のように開いた初列風切 9 枚・銅の避雷針 7 本の冠・扇の尾・背の天窓と風の民の塔 2 本・腰の羽根の門) を、"
          "斜めの面と重なる羽根で刻んだ素直な低ポリ。灰青の背・翼の上、白に灰の横縞の腹と翼の下 (先は暗い帯)、"
          "翼の上を走る稲妻の筋 (光る)、手首の瘤は左が白青・右が金 (光る)、金色の目と鉤の嘴 (鼻孔・目の縁・眉・頬の羽根)。"
          "最も今に近く、遠くから見ても輪郭が変わらない。背のダンジョンは今の外形のまま使える。"),
    "b": ("鎧をまとった鳥。体・首・頭を角ばった面で組み、羽根は先を落とした平らな板、前縁と肩・首・嘴の根は銅の縁取り。"
          "翼は後ろへ強く掃いた三日月 (初列風切 8 枚が後ろへ流れる)、尾は二本の長い燕尾。冠は扇に広がる太い避雷針 9 本、"
          "背に銅の屋根の三段の塔 2 本と避雷の鰭の列、頭は銅の庇と細い目の仮面。硬く、記念碑的で、輪郭が一番とがる。"
          "翼の形が今と変わるので、背の外形は翼から作り直す。"),
    "c": ("生きた鳥の案。丸い体にふさふさの羽毛 (首の襟・胸と背の逆立つ羽根・頬の長い羽根・脚の羽袴)、頭の後ろへなびく羽根の冠 (先だけ銅)、"
          "広く丸い翼 (先が裂けて垂れる風切り・重なる鱗状の雨覆)、長い裂けた尾。肩・翼のうしろ・尾の根に嵐の雲のかたまりがまとわりつき、"
          "背の遺跡は羽毛に半ば埋もれて傾く。最も生き物らしく、最も三角形が多い。雲は飾りで、背の外形に入れるかは別に決める。"),
}
SHORT = {"a": "A", "b": "B", "c": "C"}
cache = {}


# ------------------------------------------------------------------ 今のモデル (箱)

def cur_faces():
    if "cur" not in cache:
        cache["cur"] = voxel.load_geo(GEO / "thunder_wing.geo.json", ASSETS / "textures/entity/thunder_wing.png",
                                      ASSETS / "textures/entity/thunder_wing_glowmask.png")
    return cache["cur"]


def cur_shot(yaw, pitch, size, center, scale):
    faces = cur_faces()
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    Vm = Rx @ Ry
    if "bbox" not in cache:
        cache["bbox"] = {}
    key = (yaw, pitch)
    if key not in cache["bbox"]:
        pts = np.array([Vm @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
        cache["bbox"][key] = (pts.min(0), pts.max(0))
    lo, hi = cache["bbox"][key]
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = int(math.ceil(scale * span))
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(S, S), margin=0.0, ss=1)
    pc = Vm @ np.asarray(center, float)
    cxs, cys = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = (pc[0] - cxs) * scale + S / 2
    py = -(pc[1] - cys) * scale + S / 2
    w, h = size
    pad = max(w, h)
    big = Image.new("RGB", (S + 2 * pad, S + 2 * pad), (14, 14, 20))
    big.paste(im.convert("RGB"), (pad, pad))
    x0, y0 = int(round(px - w / 2)) + pad, int(round(py - h / 2)) + pad
    return big.crop((x0, y0, x0 + w, y0 + h))


def new_shot(res, yaw, pitch, size, center, scale, sky=False):
    im = render(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
    bg = panel_bg(*size, sky).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


def panel_bg(w, h, sky=False):
    if sky:
        yy = np.linspace(0, 1, h)[:, None, None]
        top, bot = np.array([34, 44, 74.0]), np.array([92, 108, 140.0])
        return Image.fromarray(np.clip(np.broadcast_to(top * (1 - yy) + bot * yy, (h, w, 3)), 0, 255).astype(np.uint8))
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


# ------------------------------------------------------------------ 視点 (ヨーの向きは run 時に確かめる)

VIEWS = {
    "side": dict(yaw=90, pitch=3, center=(0, 52, 4), scale=2.9, size=(780, 300)),
    "front": dict(yaw=180, pitch=0, center=(0, 52, 0), scale=1.62, size=(780, 190)),
    "top": dict(yaw=0, pitch=90, center=(0, 52, 4), scale=1.62, size=(780, 500)),
    "below": dict(yaw=205, pitch=-26, center=(0, 45, 0), scale=1.28, size=(470, 400)),
    "face": dict(yaw=128, pitch=6, center=(0, 58, -108), scale=7.4, size=(410, 400)),
    "wingtop": dict(yaw=0, pitch=90, center=(0, 52, 4), scale=0.84, size=(400, 250)),
    "wingfront": dict(yaw=180, pitch=0, center=(0, 52, 0), scale=0.84, size=(400, 140)),
    "ground": dict(yaw=205, pitch=-52, center=(0, 50, 0), scale=0.58, size=(290, 400)),
}


def view_of(name, res=None):
    v = VIEWS[name]
    if res is None:
        return cur_shot(v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
    return new_shot(res, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"], sky=(name == "ground"))


def head_only(opt):
    o = dict(opt)
    b = opt["build"]
    o["build"] = lambda lod: [f for f in b(lod) if f and f.bone in ("head", "crest")]
    return o


def main(save=True):
    results = {}
    for opt in OPTS:
        results[opt["id"]] = {}
        for lod in (0, 1, 2):
            r = build_lod(opt, lod, OUTDIR / opt["id"], save=save)
            results[opt["id"]][lod] = r
            print(opt["id"], lod, r["tris"], "tris, atlas", r["size"], flush=True)
    n_box = {0: box_count("thunder_wing"), 1: box_count("thunder_wing_mid"), 2: box_count("thunder_wing_far")}

    W = 1640
    H = 6000
    canvas = Image.new("RGB", (W, H), (16, 14, 18))
    d = ImageDraw.Draw(canvas)
    d.text((28, 12), "雷翼 (thunder_wing) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (第三の巨神)", font=F(26), fill=WH)
    d.text((28, 48), "骨の名前・階層・pivot は今の geo (body/ruins/gate/neck/head/crest/wing_l/hand_l/wing_r/hand_r/tail/leg_l/leg_r、休めの回転なし) と同じ。翼幅 約 470 単位 (ゲームでは 約 235 ブロック)、全長 約 257 単位。", font=F(15), fill=GR)
    d.text((28, 70), "近 (<64 ブロック) / 中 (<160) / 遠 の 3 段階。テクスチャは近 2・中 1・遠 0.5 テクセル/単位の最近傍。羽根など薄い面は、ひし形断面の細い筒にして表 (灰青) と裏 (白) の両面が出る。左右の翼は完全な鏡像 (手首の瘤の色だけ左 白青・右 金)。", font=F(15), fill=GR)
    y = 104

    def frame(x, yy, w, h):
        d.rectangle([x - 1, yy - 1, x + w, yy + h], outline=(66, 56, 60))

    def cap(x, yy, text, col=GR, size=15):
        d.text((x, yy), text, font=F(size), fill=col)

    subjects = [("今 (箱のモデル)", None)] + [(NAMES[o["id"]], o["id"]) for o in OPTS]
    for label, oid in subjects:
        res = None if oid is None else results[oid][0]
        if oid is None:
            tl = f"箱 近 {n_box[0]:,} / 中 {n_box[1]:,} / 遠 {n_box[2]:,} 個 (近 = 三角形 {n_box[0] * 12:,} 相当)"
        else:
            tl = (f"三角形 近 {results[oid][0]['tris']:,} / 中 {results[oid][1]['tris']:,} / 遠 {results[oid][2]['tris']:,}"
                  f"  (アトラス {results[oid][0]['size']}² / {results[oid][1]['size']}² / {results[oid][2]['size']}²)")
        d.text((20, y), label, font=F(28), fill=CY if oid else WH)
        d.text((560, y + 8), tl, font=F(18), fill=WH)
        y += 44
        sv, fv, tv = VIEWS["side"], VIEWS["front"], VIEWS["top"]
        canvas.paste(view_of("side", res), (20, y)); frame(20, y, *sv["size"])
        canvas.paste(view_of("front", res), (20, y + sv["size"][1] + 10)); frame(20, y + sv["size"][1] + 10, *fv["size"])
        canvas.paste(view_of("top", res), (20 + 780 + 20, y)); frame(20 + 800, y, *tv["size"])
        cap(26, y + 4, "横から (頭は左)")
        cap(26, y + sv["size"][1] + 14, "正面から (翼を広げた幅)")
        cap(26 + 800, y + 4, "真上から")
        y += max(sv["size"][1] + 10 + fv["size"][1], tv["size"][1]) + 14
        x = 20
        for nm, lab in (("below", "正面斜め下から (地上の見え方)"), ("face", "顔のアップ (嘴の筋・鼻孔・鉤・目の縁・頬)")):
            canvas.paste(view_of(nm, res), (x, y)); frame(x, y, *VIEWS[nm]["size"])
            cap(x + 6, y + 4, lab)
            x += VIEWS[nm]["size"][0] + 20
        wt, wf = VIEWS["wingtop"], VIEWS["wingfront"]
        canvas.paste(view_of("wingtop", res), (x, y)); frame(x, y, *wt["size"])
        canvas.paste(view_of("wingfront", res), (x, y + wt["size"][1] + 10)); frame(x, y + wt["size"][1] + 10, *wf["size"])
        cx = x + wt["size"][0] // 2
        d.line([(cx, y), (cx, y + wt["size"][1])], fill=(255, 80, 80), width=1)
        d.line([(cx, y + wt["size"][1] + 10), (cx, y + wt["size"][1] + 10 + wf["size"][1])], fill=(255, 80, 80), width=1)
        cap(x + 4, y + 2, "翼の左右 (上・正面) 赤線 = 中心")
        x += wt["size"][0] + 20
        gv = VIEWS["ground"]
        canvas.paste(view_of("ground", res), (x, y)); frame(x, y, *gv["size"])
        cap(x + 4, y + 2, "地上から見上げる", WH)
        cap(x + 4, y + 20, "(画面の高さ約 1/4 の縮小)", GR, 13)
        y += VIEWS["below"]["size"][1] + 14
        if oid is not None:
            x = 20
            names = ["近 (<64 ブロック)", "中 (<160)", "遠 (それ以上)"]
            for lod in (0, 1, 2):
                r = results[oid][lod]
                im = new_shot(r, 140, 24, (530, 235), (0, 55, 0), 1.06)
                canvas.paste(im, (x, y)); frame(x, y, 530, 235)
                cap(x + 6, y + 4, f"{names[lod]}: {r['tris']:,} 三角形 / 箱 {n_box[lod]:,} 個 / {r['tex']:g} テクセル/単位", WH)
                x += 550
            y += 235 + 22
        else:
            y += 8
        y += 18

    d.text((20, y), "テクセル密度の提案 (A の頭と冠。近景)", font=F(24), fill=WH)
    y += 36
    x = 20
    dens = [(1.0, "1 テクセル/単位 (2/ブロック)"), (2.0, "2 テクセル/単位 (4/ブロック) 【提案】"), (4.0, "4 テクセル/単位 (8/ブロック)")]
    ho = head_only(OPTS[0])
    for tx, lab in dens:
        r = build_lod(ho, 0, save=False, tex=tx)
        v = VIEWS["face"]
        im = new_shot(r, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
        canvas.paste(im, (x, y)); frame(x, y, *v["size"])
        cap(x + 6, y + 4, f"{lab} (頭だけのアトラス {r['size']}²)", WH)
        x += v["size"][0] + 20
    lpkit.TEX = 2.0
    y += VIEWS["face"]["size"][1] + 22
    d.text((20, y), "アトラス (近景。そのまま表示・最近傍)", font=F(24), fill=WH)
    y += 36
    x = 20
    for o in OPTS:
        r = results[o["id"]][0]
        at = Image.fromarray(r["atlas"], "RGBA")
        bg = Image.new("RGBA", at.size, (40, 36, 44, 255))
        bg.alpha_composite(at)
        sm = bg.convert("RGB").resize((512, 512), Image.NEAREST if r["size"] <= 512 else Image.BOX)
        canvas.paste(sm, (x, y)); frame(x, y, 512, 512)
        cap(x + 4, y + 516, f"{NAMES[o['id']]} {r['size']}x{r['size']} (512 に縮小)", CY)
        x += 532
    y += 560
    canvas = canvas.crop((0, 0, W, min(H, y)))
    if save:
        canvas.save(OUT / "lp_thunder_wing.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['tris']:,}・{results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}" for o in OPTS)
        atl = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['size']}²・{results[o['id']][1]['size']}²・{results[o['id']][2]['size']}²" for o in OPTS)
        manifest = {
            "id": "lp_thunder_wing",
            "short": "雷翼 低ポリ",
            "kind": "低ポリ 3案",
            "title": "雷翼 — 低ポリ非立方体 + ドット絵 3 案 (第三の巨神。近・中・遠の 3 段階)",
            "sheets": ["lowpoly_roughs/lp_thunder_wing.png"],
            "extra": ("統一モデル様式 (低ポリ + 低解像度のドット絵) の雷翼。骨名・階層・pivot は今の geo と同じ (休めの回転なし、body / ruins / gate / neck / head / crest / "
                      "wing_l・hand_l / wing_r・hand_r / tail / leg_l・leg_r の 13 本) なので、今のアニメ (巡航の羽ばたき・降下・止まる・うずくまる) がそのまま動く。"
                      "翼幅 約 470 単位 (ゲームでは約 235 ブロック。空の影の 270 より小さい)、全長 約 257 単位。左右の翼は完全な鏡像 (手首の瘤の色だけ左 白青・右 金のまま)。"
                      "薄い面 (羽根・翼) は、ひし形断面の細い筒で閉じた形にして両面 (上は灰青、下は白に暗い先の帯) が必ず出る。"
                      f"三角形 (近 / 中 / 遠): {tri}。今は箱 近 {n_box[0]:,} (三角形 約 {n_box[0] * 12:,} 相当) / 中 {n_box[1]:,} / 遠 {n_box[2]:,}。"
                      "予算案: 近 6,000 以下 / 中 2,000 以下 / 遠 500 以下 (3 案ともそれ以下)。"
                      "テクセル密度の提案: 近 2 テクセル/単位 (1 ブロック 4 テクセル)、中 1 (2/ブロック)、遠 0.5 (1/ブロック)。"
                      f"アトラス (近 / 中 / 遠): {atl}。今の 2048x1138 の約半分。"
                      "背中のダンジョンへの影響: 背中は巨神と同じ形のブロックの鳥 (縮尺 0.85 ブロック/単位) で、ダンジョンは体の中の部屋と骨の回廊。"
                      "3 案とも体の太さ (胴 幅 約 60・高さ 約 48)、首・頭の内側 (眼の間・嘴の根の小部屋)、翼の腕の厚み (高さ 16〜18 単位、手首の瘤 半径 約 11) は今と同じ断面を使うので、"
                      "風の広間・腰の回廊・骨の回廊・手首の洞・竜骨の間の位置はそのまま。外側の違い: A は翼の形・尾・足が今と同じなので、羽の外形は今のブロックの鳥のまま (色と羽の筋だけ新しい面に合わせる)。"
                      "B は翼を後ろへ掃いた形 (初列風切の先が約 80 単位後ろへ流れる) と二本の燕尾、背の塔が 3 段・避雷の鰭の列・首と嘴の根の銅の環が増えるので、翼と尾の外形ブロックを作り直す。"
                      "C は翼の後縁が今より最大 20 単位後ろへ広く、尾が長く、羽根の襟・胸の羽根で首と胸が少し太くなり、雲のかたまり (翼の上・体の上・尾の根) が外にふくらむので、"
                      "外形を新しいメッシュから体積化し直す (雲は白い羊毛などの飾りにするか、入れないかを別に決める)。"
                      "どの案も腕の骨と体の内側を削らないので、回廊の高さ (14 ブロック) は保てる。"
                      "メッシュは lowpoly_roughs/lp_thunder_wing/{a,b,c}/{near,mid,far}_mesh.json (+ atlas・glowmask)。生成: tools/rough/lowpoly/lp_thunder_wing/build_all.py。"),
            "options": {o["id"]: {"name": NAMES[o["id"]], "desc": DESC[o["id"]]} for o in OPTS},
        }
        (OUT / "lp_thunder_wing_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
