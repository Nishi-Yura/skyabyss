"""岩鯨の武器・防具のまとめの絵 (日本語) docs/review/previews/gear_whale_sheet.png。
上 = 武器の階級の並び (通常 → レア → 伝説)・回転・ゲーム内の持ち姿、下 = 防具の階級の並び (正面・背面)・プレイヤーが着た姿。
武器・防具の絵は低ポリの描画 (tools/rough/lowpoly)、持ち姿・着た姿はゲーム内の撮影 (tools/gen/shots/gen_gear_whale_shots.py / tools/gen/shots/gen_armor_lp_shots.py)。
実行: python tools/sheets/gear_whale_sheet.py"""
import importlib
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
W_DIR = ROOT / "tools/rough/lowpoly/weapons"
A_DIR = ROOT / "tools/rough/lowpoly/armor"
sys.path[:0] = [str(W_DIR), str(A_DIR)]
import build_whale_tide as bw  # noqa: E402
import aview  # noqa: E402
import style_whale  # noqa: E402

GW = ROOT / "run/shots/gear_whale/shots/gear_whale"
AR = ROOT / "run/shots/armor_lp_stone_whale/shots/armor_lp_stone_whale"
BG = (30, 34, 42)
ITEMS = [("item_whalebone_greatsword", "鯨骨の大剣", "whalebone_greatsword"), ("item_captain_pike", "番兵長の鉾", "captain_pike"),
         ("item_waterway_fang", "水路の牙", "waterway_fang")]
TIERS = [("common", "通常"), ("rare", "レア"), ("legend", "伝説")]


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


def main():
    TW, TH = 150, 250
    rows = []
    # --- 武器: 階級の並び (3/4) と回転
    for modname, jp, wid in ITEMS:
        mod = importlib.import_module(modname)
        res = {t: bw.make(mod, t) for t, _ in TIERS}
        sc = 9.5 if wid != "waterway_fang" else 12.0
        cen = (0, 9 if wid != "waterway_fang" else 7, 0)
        tiles, labels = [], []
        for t, tj in TIERS:
            tiles.append(bw.tile(mod, res[t], 150, -8, (TW, TH), sc, cen))
            labels.append(f"{tj} {res[t]['tris']}面")
        for yaw in (180, 90, 215):
            tiles.append(bw.tile(mod, res["legend"], yaw, -8, (TW, TH), sc, cen))
            labels.append("伝説 回転")
        # ゲーム内: 一人称・三人称 (伝説)
        for view, jl in (("fpR", "一人称"), ("tpR", "三人称")):
            p = GW / f"hold_{wid}_legend_{view}.png"
            if p.exists():
                im = Image.open(p).convert("RGB")
                im = im.crop((440, 150, 960, 540)) if view == "fpR" else im.crop((330, 100, 630, 400))
                tiles.append(fit(im, TW + 40, TH))
                labels.append(f"{jl} (ゲーム内)")
        row = Image.new("RGB", (len(tiles) * (TW + 4) + 40, TH + 26), BG)
        dr = ImageDraw.Draw(row)
        x = 0
        for t, l in zip(tiles, labels):
            row.paste(t, (x, 0))
            dr.text((x + 4, TH + 4), l, font=font(13), fill=(225, 228, 236))
            x += t.width + 4
        rows.append((f"{jp} ({wid})", row))
    # --- 防具: 階級の並び
    aview.SS = 2
    atiles, alabels = [], []
    for rar, tj in TIERS:
        res = aview.build_set(style_whale.build("whale", rar), "whale", rar)
        n = aview.set_tris(res)
        for yaw, pitch, jl in ((180, 6, "正面"), (0, 6, "背面"), (215, 16, "斜め")):
            atiles.append(aview.render(res, "whale", yaw, pitch, (190, 300), 6.6))
            alabels.append(f"{tj} {jl}" + (f"  一式 {n} 面" if jl == "正面" else ""))
    for name, jl in (("armor_lp_stone_whale_player_legend10_idle", "プレイヤー 伝説+10 (ゲーム内)"),
                     ("armor_lp_stone_whale_player_legend10_back", "背 (ゲーム内)")):
        p = AR / f"{name}.png"
        if p.exists():
            atiles.append(fit(Image.open(p).convert("RGB").crop((340, 40, 940, 700)), 190, 300))
            alabels.append(jl)
    row = Image.new("RGB", (len(atiles) * 194 + 20, 326), BG)
    dr = ImageDraw.Draw(row)
    for i, (t, l) in enumerate(zip(atiles, alabels)):
        row.paste(t, (i * 194, 0))
        dr.text((i * 194 + 4, 304), l, font=font(12), fill=(225, 228, 236))
    rows.append(("岩鯨の甲殻 (兜・胸・脚・靴)", row))
    # --- 組み立て
    Wd = max(r.width for _, r in rows) + 20
    H = 70 + sum(r.height + 30 for _, r in rows)
    sheet = Image.new("RGB", (Wd, H), (22, 25, 32))
    dr = ImageDraw.Draw(sheet)
    dr.text((14, 10), "岩鯨の武器・防具 (量塊の読みやすさ + 使い込み)", font=font(24), fill=(255, 255, 255))
    dr.text((14, 42), "通常 → レア → 伝説で輪郭が変わる (大剣: 厚い板 → 鉤の鍔と歯 → 背の肋 3 本。鉾: 牙 → 肋の鉤 → 背びれの冠と翼。牙: 反った牙 → 肋の鍔 → 第二の牙。甲殻: 全面の兜 → 牙と背びれ → 冠・両肩の牙・肋の弧)",
            font=font(13), fill=(200, 205, 215))
    y = 70
    for name, r in rows:
        dr.text((14, y), name, font=font(16), fill=(255, 232, 160))
        sheet.paste(r, (10, y + 24))
        y += r.height + 30
    out = ROOT / "docs/review/previews/gear_whale_sheet.png"
    sheet.save(out, optimize=True)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
