"""全職の専用武器の空き 11 点の低ポリメッシュを入れる (祠の剣・鎚・弓と、職の章の武器 8 点。docs/design/43 の 8 節)。

入力: docs/review/roughs_starter/raw/<id>/<案>/ (祠の品。形の段 common) ・ docs/review/roughs_jobgear/raw/<id>/<案>/ (章の品。形の段 rare)
       {<段>[_pullN]_mesh.json, <段>[_pullN][_plusN]_atlas.png, _glowmask.png, icon[_pullN].png}
       (作り方: tools/rough/lowpoly/weapons/starter/build_starter.py ・ chapter_gear/build_gear.py)
出力は install_lowpoly_abyss_weapons.py と同じ形 (弓は引き絵の段ごとのメッシュとモデル <id>_pulling_N)。持ち方は似た武器のモデルを写す。

使い方: python tools/install/install_job_weapons.py [品 id ...] [--option whale_bow=b ...]   (省くと全部 A 案。何度流してもよい。
       docs/review/roughs_one/raw/<id>/a がある品は一品一案のほうを入れる)
"""
from __future__ import annotations
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../lib',)]

import json
import shutil
import sys
from pathlib import Path

from PIL import Image

import mesh_close  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
LEVELS = ["", "_plus5", "_plus10"]
# 品 → (ラフの置き場, 形の段, 持ち方を借りる品, 引き絵の段の数)
ITEMS = {
    "shrine_sword": ("roughs_starter", "common", "abyss_lamp_sword", 1),
    "shrine_hammer": ("roughs_starter", "common", "ember_war_axe", 1),
    "shrine_bow": ("roughs_starter", "common", "thunder_wing_bow", 4),
    "whale_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "whale_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "tide_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "tide_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "storm_hammer": ("roughs_jobgear", "rare", "ember_war_axe", 1),
    "ember_greatsword": ("roughs_jobgear", "rare", "whalebone_greatsword", 1),
    "ember_bow": ("roughs_jobgear", "rare", "thunder_wing_bow", 4),
    "ember_dagger": ("roughs_jobgear", "rare", "waterway_fang", 1),
    # 職の空きを埋めた章の武器 (2026-10-27。一品一案だけ)
    "whale_dagger": ("roughs_one", "rare", "waterway_fang", 1),
    "tide_dagger": ("roughs_one", "rare", "waterway_fang", 1),
    "storm_sword": ("roughs_one", "rare", "whalebone_greatsword", 1),
    "storm_dagger": ("roughs_one", "rare", "waterway_fang", 1),
}
# 伝説 20 本 (形の段 legend) と五章の 3 本 (rare)。abyss/LegendGear。作り方: tools/rough/lowpoly/weapons/legends/build_legends.py
_HOLD = {"sword": ("whalebone_greatsword", 1), "hammer": ("ember_war_axe", 1), "staff": ("abyss_bell_staff", 1),
         "bell": ("abyss_bell_staff", 1), "dagger": ("waterway_fang", 1), "bow": ("thunder_wing_bow", 4)}
for _id, _kind in (("abyss_whale_hammer", "hammer"), ("abyss_whale_staff", "staff"), ("abyss_whale_bow", "bow"), ("abyss_whale_fang", "dagger"),
                   ("abyss_whale_bell_staff", "bell"), ("abyss_tide_sword", "sword"), ("abyss_tide_hammer", "hammer"),
                   ("abyss_tide_staff", "staff"), ("abyss_tide_bow", "bow"), ("abyss_tide_dagger", "dagger"), ("abyss_storm_sword", "sword"),
                   ("abyss_storm_hammer", "hammer"), ("abyss_storm_staff", "staff"), ("abyss_storm_dagger", "dagger"),
                   ("abyss_storm_bell_staff", "bell"), ("abyss_ember_sword", "sword"), ("abyss_ember_staff", "staff"),
                   ("abyss_ember_bow", "bow"), ("abyss_ember_dagger", "dagger"), ("abyss_ember_bell_staff", "bell")):
    ITEMS[_id] = ("roughs_legends", "legend") + _HOLD[_kind]
for _id, _kind in (("abyss_lamp_staff", "staff"), ("abyss_depth_bow", "bow"), ("abyss_toll_bell_staff", "bell")):
    ITEMS[_id] = ("roughs_ch5", "rare") + _HOLD[_kind]
# 一品一案 (2026-10-26。tools/rough/lowpoly/weapons/one/build_one.py の docs/review/roughs_one/raw/<id>/a/)。章の杖と鈴杖 8 本もここから入れる
for _id in ("whale_staff", "whale_bell_staff", "tide_staff", "tide_bell_staff", "storm_staff", "storm_bell_staff", "ember_staff", "ember_censer"):
    ITEMS[_id] = ("roughs_one", "rare") + _HOLD["staff"]
# 元からの品の作り直し (2026-10-27。tools/rough/lowpoly/weapons/one/o_remake.py)。持ち方は品のいまのモデルのまま (借りる品 = 自分)
REMAKE = {"waterway_fang": ("legend", 1), "captain_pike": ("legend", 1), "whalebone_greatsword": ("rare", 1), "abyss_greatsword": ("legend", 1),
          "tidecaller_claw": ("legend", 1), "coral_harpoon": ("legend", 1), "raiju_claw": ("legend", 1), "warden_glaive": ("legend", 1),
          "abyss_storm_bow": ("legend", 4), "smith_hammer": ("legend", 1), "ember_war_axe": ("rare", 1), "abyss_ash_scythe": ("legend", 1)}
for _id, (_tier, _st) in REMAKE.items():
    ITEMS[_id] = ("roughs_one", _tier, _id, _st)
# 六章「月」の武器 (tools/rough/lowpoly/weapons/one/o_moon.py。6 職それぞれにレア (形の段 rare) とヒロイック (形の段 legend))
for _id, _kind in (("moon_light_sword", "sword"), ("moon_scale_hammer", "hammer"), ("moon_tide_staff", "staff"), ("moon_waning_bow", "bow"),
                   ("moon_crescent_dagger", "dagger"), ("moon_starlight_bell_staff", "bell")):
    ITEMS[_id] = ("roughs_one", "rare") + _HOLD[_kind]
for _id, _kind in (("moon_waning_blade", "sword"), ("moon_full_hammer", "hammer"), ("moon_new_staff", "staff"), ("moon_starfall_bow", "bow"),
                   ("moon_umbra_dagger", "dagger"), ("moon_halo_bell_staff", "bell")):
    ITEMS[_id] = ("roughs_one", "legend") + _HOLD[_kind]
# 巨神のヒロイック武器 17 本 (2026-10-28。tools/rough/lowpoly/weapons/heroic/build_heroic.py の docs/review/roughs_heroic/raw/<id>/<案>/。形の段 rare)
HEROIC = {"whale_choir_hammer": ("hammer", "c"), "whale_star_staff": ("staff", "c"), "whale_rib_longbow": ("bow", "c"), "whale_deep_bell_staff": ("bell", "a"),
          "tide_reef_hammer": ("hammer", "c"), "tide_pearl_staff": ("staff", "b"), "tide_coral_bow": ("bow", "b"), "tide_surf_bell_staff": ("bell", "a"),
          "storm_anvil_hammer": ("hammer", "b"), "storm_cloud_staff": ("staff", "b"), "storm_gale_bow": ("bow", "b"), "storm_thunder_bell_staff": ("bell", "a"),
          "ember_cinder_blade": ("sword", "c"), "ember_ash_knife": ("dagger", "a"), "ember_kiln_staff": ("staff", "a"), "ember_flare_bow": ("bow", "b"),
          "ember_censer_bell_staff": ("bell", "a")}
ONE = [i for i in ITEMS if (ROOT / "docs/review/roughs_one/raw" / i / "a").is_dir()]
for _id in ONE:
    _src, _tier, _analog, _st = ITEMS[_id]
    ITEMS[_id] = ("roughs_one", _tier, _analog, _st)


def bake_glow(atlas: Path, mask: Path):
    a = Image.open(atlas).convert("RGBA")
    m = Image.open(mask).convert("RGBA")
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    lit = 0
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            if m.getpixel((x, y))[3] > 0:
                out.putpixel((x, y), a.getpixel((x, y)))
                lit += 1
    return out, lit


def install(item: str, opt: str):
    srcdir, tier, analog, stages = ITEMS[item]
    src = ROOT / "docs/review" / srcdir / "raw" / item / opt
    mesh_dir = ASSETS / "lowpoly/items" / item
    tex_dir = ASSETS / "textures/lowpoly/items" / item
    mesh_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    for f in list(mesh_dir.glob("*.json")) + list(tex_dir.glob("*.png")):
        f.unlink()
    # 描く側は品の階級の名 (GearTier.meshTier) で探す。ヒロイックは legend の名で置く (絵は描いた rare の塗りのまま)
    dest = "legend" if item in HEROIC else tier
    for stg in range(stages):
        st = tier if stg == 0 else f"{tier}_pull{stg - 1}"
        dt = st.replace(tier, dest, 1)
        mesh = json.loads((src / f"{st}_mesh.json").read_text(encoding="utf-8"))
        mesh["texture"] = f"items/{item}/{dt}_atlas"
        mesh.pop("glowmask", None)
        mesh, _ = mesh_close.close_mesh(mesh)
        (mesh_dir / f"{dt}.json").write_text(json.dumps(mesh, separators=(",", ":")), encoding="utf-8")
        for lv in LEVELS:
            name = f"{st}{lv}_atlas"
            dname = f"{dt}{lv}_atlas"
            shutil.copyfile(src / (name + ".png"), tex_dir / (dname + ".png"))
            glow, lit = bake_glow(src / (name + ".png"), src / f"{st}{lv}_glowmask.png")
            if lit > 0:
                glow.save(tex_dir / (dname + "_glow.png"))
    models = ASSETS / "models/item"
    names = [item] + [f"{item}_pulling_{i}" for i in range(stages - 1)]
    for k, name in enumerate(names):
        a_name = name.replace(item, analog)
        data = json.loads((models / f"{a_name}.json").read_text(encoding="utf-8").replace(analog, item))
        (models / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        shutil.copyfile(src / ("icon.png" if k == 0 else f"icon_pull{k - 1}.png"), ASSETS / "textures/item" / f"{name}.png")
    print(item, "option", opt, "installed")


for _id, (_kind, _opt) in HEROIC.items():
    ITEMS[_id] = ("roughs_heroic", "rare") + _HOLD[_kind]


def main():
    choice = {i: "a" for i in ITEMS}
    for _id, (_kind, _opt) in HEROIC.items():
        choice[_id] = _opt
    args = sys.argv[1:]
    if "--option" in args:
        i = args.index("--option")
        for kv in args[i + 1:]:
            if "=" in kv:
                k, v = kv.split("=")
                choice[k] = v
    only = [a for a in args if not a.startswith("--") and "=" not in a]
    for item in ITEMS:
        if not only or item in only:
            install(item, choice[item])


if __name__ == "__main__":
    main()
