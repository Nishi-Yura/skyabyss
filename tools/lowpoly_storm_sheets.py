#!/usr/bin/env python3
"""Contact sheets for the storm-back + coral low-poly installs (docs/design/15_high_poly.md section 9.6).
Reads run/shots/<script>/shots/<script>/ (tools/gen_lowpoly_storm_shots.py scripts) and writes
docs/review/previews/lp_storm_<name>_sheet.png / lp_coral_thrall_sheet.png: the mesh above the cube model, same pose in the same column.
Only the shots on the sheets are copied to docs/review/previews/ (kept light).
    python tools/lowpoly_storm_sheets.py"""
import os
import shutil

from PIL import Image, ImageDraw

OUT = os.path.join("docs", "review", "previews")
RUN = os.path.join("run", "shots")
W, H = 400, 225
COLS = 5


def idle(m):
    return [m + "_idle_front", m + "_idle_three_quarter", m + "_idle_side", m + "_idle_back", m + "_face"]


# sheet name -> (script dir, (mesh prefix, cube prefix), stems)
SHEETS = {
    "lp_coral_thrall": ("lp_coral_thrall", ("lp_coral_", "cubes_coral_"),
                        ["thrall_idle_front", "thrall_idle_three_quarter", "thrall_idle_side", "thrall_idle_back", "thrall_face",
                         "thrall_attack_09", "thrall_death_16", "statue_idle_front", "statue_idle_three_quarter", "statue_face"]),
    "lp_storm_warden": ("lp_storm_warden", ("lp_storm_", "cubes_storm_"),
                        idle("feather_warden") + ["feather_warden_attack_09", "feather_warden_dive_08", "feather_warden_stunned_06", "feather_warden_death_16"]),
    "lp_storm_moth": ("lp_storm_moth", ("lp_storm_", "cubes_storm_"),
                      idle("static_moth") + ["static_moth_attack_06", "static_moth_death_16"]),
    "lp_storm_supplicant": ("lp_storm_supplicant", ("lp_storm_", "cubes_storm_"),
                            idle("thunder_supplicant") + ["thunder_supplicant_cast_08", "thunder_supplicant_attack_06", "thunder_supplicant_death_16"]),
    "lp_storm_raiju": ("lp_storm_raiju", ("lp_storm_", "cubes_storm_"),
                       idle("raiju") + ["raiju_claw_08", "raiju_roar_14", "raiju_pounce_leap_08", "raiju_rail_charge_06", "raiju_stun_fall_14"]),
    "lp_storm_heart": (None, ("lp_storm_", "cubes_storm_"),
                       [("lp_storm_heart_p1", "heart_p1_front"), ("lp_storm_heart_p1", "heart_p1_three_quarter"), ("lp_storm_heart_p1", "heart_p1_core"),
                        ("lp_storm_heart_p2", "heart_p2_front"), ("lp_storm_heart_p2", "heart_p2_three_quarter"),
                        ("lp_storm_heart_p2", "heart_p2_core"), ("lp_storm_heart_p3", "heart_p3_front"),
                        ("lp_storm_heart_p3", "heart_p3_three_quarter"), ("lp_storm_heart_p3", "heart_p3_core"), ("lp_storm_heart_p3", "heart_p3_back")]),
    "lp_storm_heart_cinematic": ("lp_storm_heart_p1", ("lp_storm_", "cubes_storm_"),
                                 ["heart_intro_24", "heart_intro_56", "heart_death_24", "heart_death_64", "heart_death_98"]),
    "lp_storm_moth_charged": ("lp_storm_moth_charged", ("lp_storm_", "cubes_storm_"),
                              ["moth_charged_" + k for k in "abcdef"]),
}


def src(script, prefix, stem):
    return os.path.join(RUN, script, "shots", script, prefix + stem + ".png")


def tile(path, label):
    if os.path.exists(path):
        im = Image.open(path).convert("RGB")
        w, h = im.size
        im = im.crop((w // 4, h // 6, w * 3 // 4, h * 5 // 6 + 0)).resize((W, H), Image.LANCZOS)
    else:
        im = Image.new("RGB", (W, H), (40, 40, 40))
    ImageDraw.Draw(im).text((6, 4), label, fill=(255, 255, 0))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (script, (pm, pc), stems) in SHEETS.items():
        items = []
        for st in stems:
            sc, stem = st if isinstance(st, tuple) else (script, st)
            if name == "lp_storm_moth_charged":
                m, c = "lp_storm_" + stem, "cubes_storm_" + stem
            else:
                m, c = pm + stem, pc + stem
            items.append((sc, m, c))
        rows = (len(items) + COLS - 1) // COLS
        sheet = Image.new("RGB", (W * COLS, H * 2 * rows))
        for i, (sc, m, c) in enumerate(items):
            x, y = (i % COLS) * W, (i // COLS) * H * 2
            sheet.paste(tile(os.path.join(RUN, sc, "shots", sc, m + ".png"), "mesh " + m[3:]), (x, y))
            sheet.paste(tile(os.path.join(RUN, sc, "shots", sc, c + ".png"), "cube " + c[6:]), (x, y + H))
        path = os.path.join(OUT, name + "_sheet.png")
        sheet.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
