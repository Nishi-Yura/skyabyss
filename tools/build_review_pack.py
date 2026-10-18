# レビュー用パック (docs/review/previews/review_pack/) の JPEG を作る。
# 使い方: python tools/build_review_pack.py
# 入力: run/shots/<撮影名>/shots/<台本名>/<絵>.png (台本の撮影の出力)。下の MANIFEST で選んだ絵だけを縮めて書く。
# 出力: 幅 960 以下・JPEG・1 枚 200 KB 以下 (品質を下げて合わせる)。撮れなかった絵は表示して、終了コード 1 にする。
import os
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "docs", "review", "previews", "review_pack")
MAX_W = 960
MAX_BYTES = 200 * 1024

# (撮影フォルダ名, 台本の名前, 絵の名前, 出すファイル名)
MANIFEST = [
    ("rc_cin", "cinematic_demo", "cin_open_00", "01_opening_00_fade_in"),
    ("rc_cin", "cinematic_demo", "cin_open_01_eyelid", "02_opening_eyelid"),
    ("rc_cin", "cinematic_demo", "cin_open_02_lying", "03_opening_lying"),
    ("rc_cin", "cinematic_demo", "cin_open_03_title", "04_opening_title_frame"),
    ("rc_cin", "cinematic_demo", "cin_open_05_heaven", "05_opening_heaven"),
    ("rc_cin", "cinematic_demo", "cin_open_07_return", "06_opening_return"),
    ("rc_cin", "cinematic_demo", "cin_whale_00", "07_whale_first_sight_start"),
    ("rc_cin", "cinematic_demo", "cin_whale_02_push", "08_whale_first_sight_push"),
    ("rc_cin", "cinematic_demo", "cin_whale_03_whale", "09_whale_first_sight_whale"),
    ("rc_cin", "cinematic_demo", "cin_whale_04_orbit", "10_whale_first_sight_orbit"),
    ("rc_heart", "heart_cinematics", "stone_intro_40", "11_heart_appear_stone_40"),
    ("rc_heart", "heart_cinematics", "stone_intro_72", "12_heart_appear_stone_72"),
    ("rc_heart", "heart_cinematics", "stone_death_44", "13_heart_death_stone_44"),
    ("rc_endwhale", "titan_end_whale", "whale_01_song_stops", "14_dormancy_whale_song_stops"),
    ("rc_endwhale", "titan_end_whale", "whale_03_sinking", "15_dormancy_whale_sinking"),
    ("rc_endwhale", "titan_end_whale", "whale_05_dormant", "16_dormancy_whale_dormant"),
    ("rc_cards", "chapter_cards", "chapter_card_whale", "17_card_chapter1_ja"),
    ("rc_cards", "chapter_cards", "chapter_aftermath_whale", "18_card_aftermath_ja"),
    ("rc_cards", "chapter_cards", "chapter_card_with_levelup_and_caption", "19_card_with_levelup_and_caption"),
    ("rc_small", "review_small_elements", "small_song_stones_row", "20_small_song_stones_row"),
    ("rc_small", "review_small_elements", "small_song_stone_close", "21_small_song_stone_close"),
    ("rc_small", "review_small_elements", "small_storm_vane", "22_small_storm_vane"),
    ("rc_small", "review_small_elements", "small_travel_waymark", "23_small_travel_waymark"),
    ("rc_t_whale", "lp_titan_whale", "lp_titan_whale_mid_ground", "24_titan_whale_mid"),
    ("rc_t_tide", "lp_titan_tide", "lp_titan_tide_mid_ground", "25_titan_tide_mid"),
    ("rc_t_storm2", "lp_titan_storm", "lp_titan_storm_mid_ground", "26_titan_storm_mid"),
    ("rc_t_ember", "lp_titan_ember", "lp_titan_ember_mid_ground", "27_titan_ember_mid"),
    ("rc_backs", "review_backs_lineup", "backs_whale_lineup", "28_back_lineup_whale"),
    ("rc_backs", "review_backs_lineup", "backs_tide_lineup", "29_back_lineup_tide"),
    ("rc_backs", "review_backs_lineup", "backs_storm_lineup", "30_back_lineup_storm"),
    ("rc_backs", "review_backs_lineup", "backs_ember_lineup", "31_back_lineup_ember"),
    ("rc_npc", "lp_npc", "lp_npc_storyteller_idle_three_quarter", "32_npc_storyteller_three_quarter"),
    ("rc_npc", "lp_npc", "lp_npc_storyteller_face", "33_npc_storyteller_face"),
    ("rc_armor", "armor_lp_stone_whale", "armor_lp_stone_whale_player_common_idle", "34_armor_stone_whale_player_idle"),
    ("rc_armor", "armor_lp_stone_whale", "armor_lp_stone_whale_legend10_q34", "35_armor_stone_whale_legend10_q34"),
    ("rc_weapon", "weapon_lp_ember_war_axe", "ember_war_axe_fp_legend_p5", "36_weapon_ember_war_axe_first_person_legend"),
    ("rc_weapon", "weapon_lp_ember_war_axe", "ember_war_axe_tp_common_p5", "37_weapon_ember_war_axe_third_person_common"),
]


def save_small(src, dst):
    img = Image.open(src).convert("RGB")
    if img.width > MAX_W:
        h = round(img.height * MAX_W / img.width)
        img = img.resize((MAX_W, h), Image.LANCZOS)
    for q in (85, 80, 75, 70, 65, 60, 55, 50, 45, 40):
        img.save(dst, "JPEG", quality=q, optimize=True)
        if os.path.getsize(dst) <= MAX_BYTES:
            return q
    return -1


def main():
    os.makedirs(OUT, exist_ok=True)
    missing = []
    for folder, script, shot, name in MANIFEST:
        src = os.path.join(ROOT, "run", "shots", folder, "shots", script, shot + ".png")
        if not os.path.isfile(src):
            missing.append(f"{folder}/{script}/{shot}")
            continue
        dst = os.path.join(OUT, name + ".jpg")
        q = save_small(src, dst)
        print(f"{name}.jpg quality={q} bytes={os.path.getsize(dst)}")
    if missing:
        print("MISSING:")
        for m in missing:
            print("  " + m)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
