package jp.revati.skyabyss.weapon;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;

// 武器ごとの動きの読み込み口。assets/skyabyss/weapon_moves/<品の id>.json を jar (クラスパス) から読むので、
// クライアントも専用サーバーも同じ内容を見る (サーバーは当たる tick、クライアントは絵)。tools/gen/items/gen_weapon_moves.py が JSON を作る
public final class MoveSets {
    // 動きを持つ品 (剣型の武器 17 点 + 弓 2 点 + 盾 + 島の祠の始めの武器 3 点 + 章の杖と鈴杖 8 点)
    public static final List<String> ITEMS = List.of("whalebone_greatsword", "captain_pike", "tide_shell_trident", "coral_harpoon", "warden_glaive",
            "waterway_fang", "raiju_claw", "tidecaller_claw", "ember_war_axe", "smith_hammer", "thrall_poker", "abyss_greatsword", "abyss_bell_staff",
            "abyss_ash_scythe", "abyss_lamp_sword", "abyss_sinking_anchor", "abyss_pulse_dagger", "thunder_wing_bow", "abyss_storm_bow", "coral_shield",
            "shrine_staff", "shrine_bell_staff", "shrine_dagger", "whale_staff", "whale_bell_staff", "tide_staff", "tide_bell_staff",
            "storm_staff", "storm_bell_staff", "ember_staff", "ember_censer", "whale_hammer", "tide_hammer", "storm_hammer", "ember_greatsword",
            "ember_dagger", "whale_dagger", "tide_dagger", "storm_sword", "storm_dagger", "shrine_sword", "shrine_hammer",
            "abyss_whale_hammer", "abyss_whale_staff", "abyss_whale_fang", "abyss_whale_bell_staff", "abyss_tide_sword", "abyss_tide_hammer", "abyss_tide_staff", "abyss_tide_dagger", "abyss_storm_sword", "abyss_storm_hammer", "abyss_storm_staff", "abyss_storm_dagger", "abyss_storm_bell_staff", "abyss_ember_sword", "abyss_ember_staff", "abyss_ember_dagger", "abyss_ember_bell_staff", "abyss_lamp_staff", "abyss_toll_bell_staff",
            "moon_scale_hammer", "moon_tide_staff", "moon_crescent_dagger", "moon_starlight_bell_staff", "moon_waning_blade",
            "moon_light_sword", "moon_full_hammer", "moon_new_staff", "moon_umbra_dagger", "moon_halo_bell_staff");

    private static Map<String, MoveSet> all;

    private MoveSets() {
    }

    public static synchronized MoveSet get(String itemPath) {
        return all().get(itemPath);
    }

    public static synchronized Map<String, MoveSet> all() {
        if (all == null) {
            all = load();
        }
        return all;
    }

    private static Map<String, MoveSet> load() {
        Map<String, MoveSet> m = new LinkedHashMap<>();
        for (String id : ITEMS) {
            String path = "/assets/" + SkyAbyss.MODID + "/weapon_moves/" + id + ".json";
            try (InputStream in = MoveSets.class.getResourceAsStream(path)) {
                if (in == null) {
                    SkyAbyss.LOGGER.error("weapon moves: {} not found", path);
                    continue;
                }
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                m.put(id, MoveSet.parse(id, json));
            } catch (Exception e) {
                SkyAbyss.LOGGER.error("weapon moves: {} is broken", path, e);
            }
        }
        return m;
    }
}
