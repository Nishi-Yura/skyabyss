"""雷翼・余燼の武器・防具・装飾品のまとめの絵 (日本語) docs/review/previews/gear_storm_sheet.png / gear_ember_sheet.png。
低ポリの描画だけ (模式の持ち姿を含む。ゲーム内の撮影は使わない)。
実行: python tools/gear_set_sheet.py storm|ember"""
import importlib
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LP = ROOT / "tools/rough/lowpoly"
sys.path[:0] = [str(LP / "accessories"), str(LP / "weapons"), str(LP / "armor")]
import common as acc_common  # noqa: E402  (装飾品の common。防具の common と同名なので先に読んで取り置く)
import thunder as acc_thunder  # noqa: E402
import ember as acc_ember  # noqa: E402
del sys.modules["common"]
sys.path.remove(str(LP / "accessories"))
sys.path.append(str(LP / "accessories"))
import build_storm_ember as be  # noqa: E402
import mock  # noqa: E402
import aview  # noqa: E402
from scene import view  # noqa: E402

BG = (30, 34, 42)
TIERS = [("common", "通常"), ("rare", "レア"), ("legend", "伝説")]
SETS = dict(
    storm=dict(
        title="雷翼の武器・防具 (雷鳥: 軽さと輪 + 嵐に焼けた傷み)",
        sub="羽の先が焦げ、銅にひびが走り、ひびの中を稲妻が走る。通常 → レア → 伝説で輪郭が変わる (冠の羽 3 → 5 → 7、背の翼 3 → 4 → 5 組と銅の光輪、弓の翼・鉤槍の翼の冠・爪の羽の扇)",
        titan="thunder", armor_mod="style_storm", acc_mod="thunder", armor_name="雷翼の羽衣 (兜・胸・脚・靴)",
        items=["thunder_wing_bow", "warden_glaive", "raiju_claw"], out="gear_storm_sheet.png"),
    ember=dict(
        title="余燼の武器・防具 (火とかげと鍛冶場: 玄武岩の質量 + 冷めゆく溶岩・灰・鉄の帯・鎖)",
        sub="黒い玄武岩の質量に、冷めゆく溶岩の筋と灰、鉄の帯と鎖。通常 → レア → 伝説で輪郭が変わる (背びれ 1 → 3 → 5、肩の大板、尾板、鎖の数)",
        titan="ember", armor_mod="style_ember", acc_mod="ember", armor_name="余燼の鎧 (兜・胸・脚・靴)",
        items=["ember_war_axe", "smith_hammer", "thrall_poker"], out="gear_ember_sheet.png"),
)


def font(n):
    for f in (r"C:\Windows\Fonts\YuGothB.ttc", r"C:\Windows\Fonts\meiryob.ttc", r"C:\Windows\Fonts\meiryo.ttc"):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def fit(img, w, h):
    img = img.copy()
    img.thumbnail((w, h))
    out = Image.new("RGB", (w, h), BG)
    out.paste(img, ((w - img.width) // 2, (h - img.height) // 2))
    return out


def main(which):
    cfg = SETS[which]
    titan = cfg["titan"]
    TW, TH = 150, 250
    rows = []
    for item in cfg["items"]:
        jp, ti, fn, inlay, em, nst, (cy, sc) = be.ITEMS[item]
        res = {t: be.make(item, t) for t, _ in TIERS}
        mod = {"thunder_wing_bow": be.m_bow, "warden_glaive": be.m_gl, "raiju_claw": be.m_cl,
               "ember_war_axe": be.m_ax, "smith_hammer": be.m_hm, "thrall_poker": be.m_pk}[item]
        cen = (3 if nst > 1 else 0, cy, 0)
        tiles, labels = [], []
        for t, tj in TIERS:
            tiles.append(be.tile(res[t], titan, 150, -8, (TW, TH), sc * 0.95, cen))
            labels.append(f"{tj} {res[t]['tris']}面")
        for yaw in (180, 90, 215):
            tiles.append(be.tile(res["legend"], titan, yaw, -8, (TW, TH), sc * 0.95, cen))
            labels.append("伝説 回転")
        for v, jl in (("fp", "一人称"), ("tp", "三人称")):
            r = res["legend"]
            im = mock.first_person(r["faces"], r["atlas"], r["glow"], titan, mod.GRIP, size=(230, 300)) if v == "fp" else \
                mock.third_person(r["faces"], r["atlas"], r["glow"], titan, mod.GRIP, size=(230, 300))
            bg = Image.new("RGBA", im.size, BG + (255,))
            bg.alpha_composite(im)
            tiles.append(fit(bg.convert("RGB"), TW + 40, TH))
            labels.append(f"{jl} (模式・伝説)")
        row = Image.new("RGB", (len(tiles) * (TW + 4) + 80, TH + 26), BG)
        dr = ImageDraw.Draw(row)
        x = 0
        for t, l in zip(tiles, labels):
            row.paste(t, (x, 0))
            dr.text((x + 4, TH + 4), l, font=font(13), fill=(225, 228, 236))
            x += t.width + 4
        rows.append((f"{jp} ({item})", row))
    # 弓の引き (雷翼)
    if which == "storm":
        tiles = []
        for st in range(4):
            r = be.make("thunder_wing_bow", "legend", 0, st)
            tiles.append(be.tile(r, titan, 180, 0, (130, 210), 8.6, (-2, 8, 0)))
        row = Image.new("RGB", (4 * 134, 232), BG)
        dr = ImageDraw.Draw(row)
        for i, t in enumerate(tiles):
            row.paste(t, (i * 134, 0))
            dr.text((i * 134 + 4, 212), ("休め", "引き 1", "引き 2", "引き 3")[i] + " (伝説)", font=font(12), fill=(225, 228, 236))
        rows.append(("雷翼の弓の引き (3 段)", row))
    # 防具
    aview.SS = 2
    style = importlib.import_module(cfg["armor_mod"])
    atiles, alabels = [], []
    for rar, tj in TIERS:
        res = aview.build_set(style.build(titan, rar), titan, rar)
        n = aview.set_tris(res)
        for yaw, pitch, jl in ((180, 6, "正面"), (0, 6, "背面"), (215, 16, "斜め")):
            atiles.append(aview.render(res, titan, yaw, pitch, (190, 300), 6.6))
            alabels.append(f"{tj} {jl}" + (f"  一式 {n} 面" if jl == "正面" else ""))
    row = Image.new("RGB", (len(atiles) * 194 + 20, 326), BG)
    dr = ImageDraw.Draw(row)
    for i, (t, l) in enumerate(zip(atiles, alalabels := alabels)):
        row.paste(t, (i * 194, 0))
        dr.text((i * 194 + 4, 304), l, font=font(12), fill=(225, 228, 236))
    rows.append((cfg["armor_name"], row))
    # 装飾品 (伝説の段)
    amod = dict(thunder=acc_thunder, ember=acc_ember)[cfg["acc_mod"]]
    atl, all_ = [], []
    for iid, it in amod.ITEMS.items():
        for rar, tj in (("common", "通常"), ("legend", "伝説")):
            r = acc_common.make(it["fn"]["a"], amod.TITAN, rar)
            im = view(r["faces"], r["atlas"], r["glow"], titan, 165, -10, (130, 150), (0, 0, 0), 9.0)
            bg = Image.new("RGBA", (130, 150), BG + (255,))
            bg.alpha_composite(im)
            atl.append(bg.convert("RGB"))
            all_.append(f"{it['jp']} {tj} {r['tris']}面")
    row = Image.new("RGB", (len(atl) * 134, 170), BG)
    dr = ImageDraw.Draw(row)
    for i, (t, l) in enumerate(zip(atl, all_)):
        row.paste(t, (i * 134, 0))
        dr.text((i * 134 + 2, 152), l, font=font(11), fill=(225, 228, 236))
    rows.append(("装飾品 (通常 → 伝説)", row))
    Wd = max(r.width for _, r in rows) + 20
    H = 70 + sum(r.height + 30 for _, r in rows)
    sheet = Image.new("RGB", (Wd, H), (22, 25, 32))
    dr = ImageDraw.Draw(sheet)
    dr.text((14, 10), cfg["title"], font=font(24), fill=(255, 255, 255))
    dr.text((14, 42), cfg["sub"], font=font(13), fill=(200, 205, 215))
    y = 70
    for name, r in rows:
        dr.text((14, y), name, font=font(16), fill=(255, 232, 160))
        sheet.paste(r, (10, y + 24))
        y += r.height + 30
    out = ROOT / "docs/review/previews" / cfg["out"]
    sheet.save(out, optimize=True)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1])
