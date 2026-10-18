"""見る者 (watcher) 低ポリ 3 案の比較シート + manifest。メッシュ・アトラスは view.get_option が書き出す。

実行: python tools/rough/lowpoly/lp_watcher/sheet.py
出力: docs/review/lowpoly_roughs/lp_watcher.png
      docs/review/lowpoly_roughs/lp_watcher/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/lp_watcher_manifest.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import get_option, bg as bgpanel  # noqa
from common import *  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 伸びた影", "b": "B 焼け紙の人", "c": "C うつろな殻"}
F = voxel.font

# ---------------------------------------------------------------- 今のモデル (箱)
GEO = json.loads((ASSETS / "geo/entity/watcher.geo.json").read_text(encoding="utf-8"))
OLD = voxel.load_geo(GEO, ASSETS / "textures/entity/watcher.png", ASSETS / "textures/entity/watcher_glowmask.png")
N_CUBES = sum(len(b.get("cubes", [])) for b in GEO["minecraft:geometry"][0]["bones"])
OLD_EYE = V(-2.75, 30.75, -3.75)


def old_rgba(yaw, pitch, size, center, scale):
    """今のモデルを新しい描画と同じ縮尺・中心で RGBA にする (暗い背景と明るい背景の 2 枚の差で背景を抜く)。"""
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    V_ = Rx @ Ry
    pts = []
    for f in OLD:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(V_ @ (f.origin + a * f.eu + b * f.ev))
    sc = V_ @ np.asarray(center, float)
    pts = np.array(pts)
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    Sz = int(math.ceil(span * scale))
    eff = Sz / span
    ss = 2 if scale < 6 else 1
    dk = np.array(voxel.render(OLD, yaw=yaw, pitch=pitch, size=(Sz, Sz), margin=0.0, ss=ss, bloom=False, outline=False), float)
    lt = np.array(voxel.render(OLD, yaw=yaw, pitch=pitch, size=(Sz, Sz), bg="light", margin=0.0, ss=ss, bloom=False, outline=False), float)
    mask = (np.abs(dk - lt).max(-1) < 8)
    rgba = np.zeros((Sz, Sz, 4), np.uint8)
    rgba[..., :3] = dk.astype(np.uint8)
    rgba[..., 3] = mask * 255
    im = Image.fromarray(rgba, "RGBA")
    cx_, cy_ = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = Sz / 2 + (sc[0] - cx_) * eff
    py = Sz / 2 - (sc[1] - cy_) * eff
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(im, (int(round(size[0] / 2 - px)), int(round(size[1] / 2 - py))))
    return out


class Subject:
    """描画の提供元: 今のモデルか、案 a/b/c。"""

    def __init__(self, key, opt=None):
        self.key, self.opt = key, opt
        if opt is not None:
            e = [f.verts for f in opt["faces"] if f.bone == "eyes"]
            self.eye = np.concatenate(e).mean(0)
        else:
            self.eye = OLD_EYE

    def rgba(self, yaw, pitch, size, center, scale, light=1.0):
        if self.opt is None:
            im = old_rgba(yaw, pitch, size, center, scale)
            if light != 1.0:
                a = np.array(im, float)
                a[..., :3] *= light
                im = Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")
            al = np.array(im, float)
            al[..., 3] *= ALPHA
            return Image.fromarray(al.astype(np.uint8), "RGBA")
        return render_t(self.opt["faces"], self.opt["atlas"], self.opt["glow"], yaw, pitch, size, center, scale, light=light)

    def eye_px(self, yaw, pitch, size, center, scale):
        Vm = rot_x(pitch) @ rot_y(yaw)
        c = Vm @ np.asarray(center, float)
        e = Vm @ self.eye
        return (e[0] - c[0]) * scale + size[0] / 2, -(e[1] - c[1]) * scale + size[1] / 2


def panel_bg(w, h):
    return bgpanel(w, h)


def night_bg(w, h, horizon, seed=3):
    """夜の外: ほぼ黒の空 (地平線の近くだけ少し明るい)、暗い丘、わずかな星。"""
    yy, xx = np.mgrid[0:h, 0:w]
    t = np.clip(yy / max(horizon, 1), 0, 1)[..., None]
    sky = np.array([3, 4, 8.0]) * (1 - t) + np.array([12, 17, 28.0]) * t
    ground = np.array([5, 7, 8.0]) + 2.5 * hash2(xx * 0.9, yy * 0.9, seed)[..., None] + 3.0 * np.clip((yy - horizon) / max(h - horizon, 1), 0, 1)[..., None]
    img = np.where((yy >= horizon)[..., None], ground, sky)
    st = (hash2(xx, yy, seed + 1) > 0.9965) & (yy < horizon - 2)
    img[st] = (60, 68, 88)
    return img


def glint(arr, x, y, ppb, dist):
    """WatcherRenderer の目の光のにじみ (縦長・青白) を足す。半径 (ブロック) = max(0.03, dist * 0.0032)。"""
    r = max(0.03, dist * 0.0032) * ppb
    sx, sy = max(0.5, 0.6 * r * 0.9), max(0.8, 1.8 * r * 0.9)
    h, w = arr.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    g = np.exp(-(((xx + 0.5 - x) / sx) ** 2 + ((yy + 0.5 - y) / sy) ** 2) * 0.9)
    arr += g[..., None] * np.array([150, 190, 255.0]) * 0.95
    return arr


def night_scene(subj, dist, cw, ch, up, yaw=205):
    """夜 (月なし) に dist ブロック先に立つ見る者。縮尺はゲーム内 (1080p・視野 70 度) と同じ。"""
    ppb = 1080.0 / (2 * dist * math.tan(math.radians(35)))
    sc = ppb / 16.0
    horizon = int(ch * 0.86)
    arr = night_bg(cw, ch, horizon)
    foot = horizon + 1
    center = (0.0, 17.0, -1.0)
    size = (cw, ch)
    # 足の位置 (y = 0) が画面の foot 行に来るように、中心の高さを決める
    cy_model = (foot - ch / 2) / sc
    center = (0.0, cy_model, -1.0)
    im = subj.rgba(yaw, 4, size, center, sc, light=0.26)
    a = np.array(im, float)
    fog = 0.30 if dist > 30 else 0.10
    al = a[..., 3:4] / 255.0
    col = a[..., :3] * (1 - fog) + arr[horizon - 2, 0] * fog * 1.0
    arr = arr * (1 - al) + col * al
    ex, ey = subj.eye_px(yaw, 4, size, center, sc)
    arr = glint(arr, ex, ey, ppb, dist)
    out = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    return out.resize((cw * up, ch * up), Image.NEAREST)


class Sheet:
    def __init__(self):
        self.opts = {k: get_option(k) for k in "abc"}
        self.subj = [Subject("old")] + [Subject(k, self.opts[k]) for k in "abc"]
        self.tri = {k: tri_count(self.opts[k]["faces"]) for k in "abc"}
        self.bone_tri = {k: tri_by_bone(self.opts[k]["faces"]) for k in "abc"}
        self.canvas = Image.new("RGB", (W, 4600), (16, 16, 22))
        self.d = ImageDraw.Draw(self.canvas)
        self.y = 0

    def panel(self, i, y, im, w, h, night=False):
        if im.mode == "RGBA":
            b = panel_bg(w, h)
            b.paste(im, (0, 0), im)
            im = b
        self.canvas.paste(im, (xs[i], y))
        self.d.rectangle([xs[i] - 1, y - 1, xs[i] + w, y + h], outline=(60, 60, 78))

    def labels(self, y, extra=("", "", "", "")):
        old = "今 (箱 %d 個 / 面 %d)" % (N_CUBES, len(OLD))
        self.d.text((xs[0] + 2, y), old + extra[0], font=F(17), fill=WH)
        for i, k in enumerate("abc", start=1):
            self.d.text((xs[i] + 2, y), "%s  %d 三角形%s" % (NAMES[k], self.tri[k], extra[i]), font=F(17), fill=CY)

    def row(self, title, ph, yaw, pitch, scale, cen, wide=False):
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        for i, s in enumerate(self.subj):
            self.panel(i, self.y, s.rgba(yaw, pitch, (PW, ph), cen, scale), PW, ph)
        self.labels(self.y + ph + 6)
        self.y += ph + 38

    def night_row(self, title):
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        for i, s in enumerate(self.subj):
            far = night_scene(s, 40, 46, 70, 4)
            mid = night_scene(s, 14, 94, 140, 2)
            pan = Image.new("RGB", (PW, 280), (3, 4, 8))
            pan.paste(far, (0, 0))
            pan.paste(mid, (far.width + 9, 0))
            self.panel(i, self.y, pan, PW, 280)
        self.labels(self.y + 286, extra=("", "", "", ""))
        self.d.text((X0, self.y + 308), "各パネルの左 = 40 ブロック先 (4 倍)、右 = 14 ブロック先 (2 倍)。1080p・視野 70 度の縮尺。目の縦長の光のにじみ (WatcherRenderer) 込み。", font=F(15), fill=GR)
        self.y += 336

    def para(self, text, size=15, fill=GR, x=28, maxw=1540):
        f = F(size)
        line = ""
        for ch in text:
            if self.d.textlength(line + ch, font=f) > maxw:
                self.d.text((x, self.y), line, font=f, fill=fill)
                self.y += size + 5
                line = ch
            else:
                line += ch
        if line:
            self.d.text((x, self.y), line, font=f, fill=fill)
            self.y += size + 5

    def build(self):
        d = self.d
        d.text((28, 14), "見る者 (watcher) — 低ポリ非立方体 3 案 + ドット絵テクスチャ (夜、遠くに立つ「前のループの自分」の影)", font=F(26), fill=WH)
        self.y = 50
        self.para("骨 7 本 (root[Y 180 度] > body > hem / mantle / head / mist、head > eyes) の名前・階層・pivot は今の geo と同じ。今のアニメ idle (body・hem・mantle の微小な揺れと mist の上下) がそのまま動く。"
                  "骨の割り当て: body = 胴、hem = 脚、mantle = 腕、head = 頭、eyes = 目の光、mist = 漂う欠片。")
        self.para("左端が今のモデル (箱。外套の柱で、手足がない)。描画は entityTranslucent (半透明・裏面も描く) なので、このシートも奥から手前へ半透明 (濃さ %.2f) で重ねて描いている。"
                  "プレイヤーの比率 (頭 8・胴 12・腕 12・脚 12) を基にして、顔の特徴 (鼻・眉・指) は作らない。目は縦の切れ目 1 本だけ (横に 2 つ並べるとエンダーマンに見えた)。" % ALPHA)
        self.y = max(self.y + 6, 128)
        self.row("前 (正面)", 520, 205, 8, 11.6, (0, 19.5, -1))
        self.row("前 3/4", 520, 237, 10, 11.6, (0, 19.5, -1))
        self.row("横", 520, 293, 6, 11.6, (0, 19.5, -1))
        self.row("後ろ 3/4 (割れ目・欠けが見える側)", 520, 28, 10, 11.6, (0, 19.5, -1))
        self.night_row("夜の外・遠く (見る者を見つける距離)")
        self.row("顔のアップ", 360, 205, 6, 33.0, (0, 31.0, -1))
        # アトラス
        d.text((X0, self.y), "テクスチャのアトラス (等倍、1 ブロック = 32 テクセル。市松は透明 = 焼け抜けた穴) と、光る画素のマスク", font=F(20), fill=CY)
        self.y += 30
        top = 0
        for i, k in enumerate("abc", start=1):
            r = self.opts[k]
            x = X0 + (i - 1) * 520
            sz = r["size"]
            at = Image.fromarray(r["atlas"], "RGBA")
            chk = Image.new("RGBA", at.size)
            cd = ImageDraw.Draw(chk)
            for yy in range(0, sz, 4):
                for xx in range(0, sz, 4):
                    cd.rectangle([xx, yy, xx + 3, yy + 3], fill=(50, 50, 62, 255) if (xx // 4 + yy // 4) % 2 else (34, 34, 44, 255))
            chk.alpha_composite(at)
            self.canvas.paste(chk.convert("RGB"), (x, self.y))
            gm = Image.fromarray((r["glow"] * 255).astype(np.uint8), "L").convert("RGB")
            self.canvas.paste(gm, (x + sz + 10, self.y))
            d.rectangle([x - 1, self.y - 1, x + sz, self.y + sz], outline=(60, 60, 78))
            d.text((x, self.y + sz + 6), "%s アトラス %d x %d (右: 光る画素)" % (NAMES[k], sz, sz), font=F(15), fill=CY)
            top = max(top, sz)
        self.y += top + 40
        for k in "abc":
            bt = self.bone_tri[k]
            d.text((X0, self.y), "%s:  合計 %d 三角形 (胴 %d / 脚 %d / 腕 %d / 頭 %d / 目 %d / 欠片 %d)。今は箱 %d 個 (面 %d = 三角形換算 %d)"
                   % (NAMES[k], self.tri[k], bt.get("body", 0), bt.get("hem", 0), bt.get("mantle", 0), bt.get("head", 0),
                      bt.get("eyes", 0), bt.get("mist", 0), N_CUBES, len(OLD), len(OLD) * 2), font=F(17), fill=CY)
            self.y += 26
        self.y += 8
        for t in ("パレット (3〜5 色の階調で手描き風): 全案、青白い光 (目・欠片) は今の見る者と同じ。A・C は今と同じ冷たい青灰 (暗い石板色)、B だけ焼け紙の灰茶。"
                  "A 石板 5 + 淡い顔 5。B 焼け紙 5 + 煤 4 (縁の焼け帯) + 青い火の粉 3。C 殻 5 + 内壁 4 + 灯 4。",
                  "光る画素: A は目の切れ目・欠片。B は目の切れ目・欠片・縁の厚みの帯に這う暗い青の火・穴のふちと縁の火の粉 (まばら)。"
                  "C は目の光・胸と頭の灯 (八面体)・欠片と、灯の近くの内壁にうっすら (暗い青の光る画素 = かすか)。",
                  "メッシュは lowpoly_roughs/lp_watcher/{a,b,c}/watcher_{a,b,c}_mesh.json (+ _atlas・_glowmask)。生成: tools/rough/lowpoly/lp_watcher/sheet.py。"):
            self.para(t)
        self.canvas = self.canvas.crop((0, 0, W, self.y + 14))
        out = OUT / "lp_watcher.png"
        self.canvas.save(out)
        print("saved", out, self.canvas.size)


def manifest(s):
    t = s.tri
    data = {
        "id": "lp_watcher",
        "short": "見る者 低ポリ",
        "kind": "低ポリ 3案",
        "title": "見る者 (watcher) — 低ポリ非立方体 + ドット絵 3 案 (夜、遠くに立つ「前のループの自分」の影)",
        "sheets": ["lowpoly_roughs/lp_watcher.png"],
        "extra": (
            "統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の見る者。夜に遠くの丘から見つめてくる、プレイヤーが前のループに残した影。"
            "プレイヤーの比率 (頭 8・胴 12・腕 12・脚 12) の人影で、鼻・眉・指などの顔や手の細部は作らない。目は縦の切れ目 1 本だけ (横に 2 つ並べるとエンダーマンに見えた、という過去の教訓)。"
            "骨 7 本 (root[Y 180 度] > body > hem / mantle / head / mist、head > eyes) の名前・階層・pivot は今の geo と同じで、今のアニメ idle がそのまま動く。"
            "骨の割り当て: body = 胴、hem = 脚、mantle = 腕、head = 頭、eyes = 目の光 1 枚、mist = 漂う欠片。"
            "描画は entityTranslucent (半透明・裏面も描く) 前提。閉じた塊は裏の壁が透けて見えるので、内側の面は C の意図した二重の殻だけにし、A・B は単層。"
            "三角形: A %d、B %d、C %d (普通の敵の目安 約 600 以下)。今は箱 %d 個 (面 %d)。"
            "アトラス: A %d、B %d、C %d (正方形)。B はテクスチャの alpha 0 の画素が焼け抜けた穴・ほつれになる (alpha は 0 か 255 だけ)。"
            "青白い光 (目・欠片) は全案で今の見る者と同じ。A・C は今と同じ冷たい青灰、B だけ焼け紙の灰茶。シートは今 / A / B / C を前・前 3/4・横・後ろ 3/4・夜の遠く (40 ブロックと 14 ブロック先、1080p・視野 70 度の縮尺、目のにじみ込み)・顔のアップ・アトラスで並べる。"
            "メッシュは lowpoly_roughs/lp_watcher/{a,b,c}/watcher_{a,b,c}_mesh.json (+ _atlas・_glowmask)。生成: tools/rough/lowpoly/lp_watcher/sheet.py。"
        ) % (t["a"], t["b"], t["c"], N_CUBES, len(OLD), s.opts["a"]["size"], s.opts["b"]["size"], s.opts["c"]["size"]),
        "options": {
            "a": {"name": "A 伸びた影",
                  "desc": "プレイヤーの輪郭を少しだけ引き伸ばした影。脚と腕が長く (指先が膝の下まで届く)、肩が細く、頭が縦に長く前へ傾く。"
                          "手足の先は指のない尖り。顔は目の切れ目 1 本だけで、他は何もない。単層の面取りした低ポリで、最も軽く、遠くからは「人に似ているが何かが違う」輪郭だけが残る。"
                          "三角形 %d。" % t["a"]},
            "b": {"name": "B 焼け紙の人",
                  "desc": "プレイヤーの形に切り抜いた焼け紙。厚み 2 ほどの薄い板で、縁は千切れて欠け (冠・肩・腕の先・裾)、ところどころ前から後ろへ焼け抜けた穴が空く。"
                          "縁の厚みの帯と穴のふちに、暗い青い火の粉が這う。横から見ると細い線になり、正面だけが人に見える。目は焼け跡の縦の切れ目 1 本。"
                          "三角形 %d。" % t["b"]},
            "c": {"name": "C うつろな殻",
                  "desc": "プレイヤーの形の、中が空っぽの薄い殻 (外と内の二重の面)。背中に縦の割れ目、手足と腰は口が開いた筒で、頭は肩から少し浮く。"
                          "胸と頭の中にだけ、かすかな青白い灯 (小さな八面体) が浮き、背中の割れ目と顔の切れ目 (目) から透けて見える。半透明で内側が見えるのが売り。"
                          "三角形 %d。" % t["c"]},
        },
    }
    (OUT / "lp_watcher_manifest.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    s = Sheet()
    print("tris", s.tri, s.bone_tri)
    s.build()
    manifest(s)
