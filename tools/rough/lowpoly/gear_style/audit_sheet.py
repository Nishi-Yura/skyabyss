"""現行の武器 17 点 (レア/伝説の実装メッシュ) の正面・3/4 を 1 枚に並べる (gear_audit 用の目視)。
実行: python tools/rough/lowpoly/gear_style/audit_sheet.py <out.png>"""
import sys, importlib, time
from pathlib import Path
HERE = Path(__file__).resolve().parent
W = HERE.parent / "weapons"
sys.path.insert(0, str(W)); sys.path.insert(0, str(W / "abyss"))
from scene import *
from paint import paint
import d_thunder, d_ember, d_storm_ember as se
import d_abyss as D, paint_abyss as PA
from PIL import ImageDraw, ImageFont

def font(sz):
    for p in ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc"]:
        try: return ImageFont.truetype(p, sz)
        except Exception: pass
    return ImageFont.load_default()

items = []
for m, rar in (("item_whalebone_greatsword", "rare"), ("item_captain_pike", "legend"), ("item_waterway_fang", "legend"),
               ("item_tide_shell_trident", "rare"), ("item_coral_harpoon", "legend"), ("item_tidecaller_claw", "legend"),
               ("item_coral_shield", "rare")):
    mod = importlib.import_module(m)
    items.append((mod.ID, mod.TITAN, lambda mod=mod, rar=rar: (mod.faces(rar), mod.inlay, mod.EDGE, rar)))
for id_, tt, fn, inl, em, rar in (("thunder_wing_bow", "thunder", d_thunder.b, d_thunder.inlay_b, (), "rare"),
        ("warden_glaive", "thunder", se.glaive, se.inlay_glaive, (), "legend"), ("raiju_claw", "thunder", se.claw, se.inlay_claw, (), "legend"),
        ("ember_war_axe", "ember", d_ember.a, d_ember.inlay_a, ("edge",), "rare"), ("smith_hammer", "ember", se.hammer, se.inlay_hammer, ("edge",), "legend"),
        ("thrall_poker", "ember", se.poker, se.inlay_poker, ("edge",), "legend")):
    items.append((id_, tt, lambda fn=fn, inl=inl, em=em, rar=rar: (fn(rar), inl, em, rar)))
tiles = []


def shots(faces, atlas, glow, tt):
    row = []
    for yaw, pitch in ((180, 0), (150, -12)):
        im = view(faces, atlas, glow, tt, yaw, pitch, (200, 300), (0, 8, 0), 11)
        bg = Image.new("RGBA", (200, 300), (30, 34, 42, 255)); bg.alpha_composite(im); row.append(bg.convert("RGB"))
    return row


for id_, tt, f in items:
    t0 = time.time()
    faces, inl, em, rar = f()
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, tt, rar, 0, inl, em)
    tiles.append((id_, rar, tri_count(faces), shots(faces, atlas, glow, tt)))
    print(id_, round(time.time() - t0, 1), "s", tri_count(faces), flush=True)
for wid in ("abyss_greatsword", "abyss_bell_staff", "abyss_storm_bow", "abyss_ash_scythe"):
    w = D.WEAPONS[wid]; fn, inl, em, _ = w["opts"]["a"]
    faces = fn(0) if w["stages"] > 1 else fn()
    faces, size, P, N, M, L = build(faces)
    atlas, glow = PA.paint(faces, size, P, N, M, L, w["titan"], 0, inl, em)
    tiles.append((wid, "legend", tri_count(faces), shots(faces, atlas, glow, w["titan"])))
cols = 6
rows = (len(tiles) + cols - 1) // cols
S = Image.new("RGB", (cols * 400, rows * 330), (22, 24, 30))
d = ImageDraw.Draw(S)
for i, (id_, rar, tr, row) in enumerate(tiles):
    x, y = (i % cols) * 400, (i // cols) * 330
    S.paste(row[0], (x, y)); S.paste(row[1], (x + 200, y))
    d.text((x + 6, y + 304), f"{id_} {rar} {tr}", font=font(15), fill=(235, 235, 240))
S.save(sys.argv[1]); print(S.size)
