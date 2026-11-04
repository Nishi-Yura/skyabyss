package jp.revati.skyabyss.fishing;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.world.continent.ContinentMap;
import net.minecraft.util.RandomSource;

// 釣りの魚の表 (docs/design/54 の 6.2.4.1)。五つの土地に 6 種ずつと、どこでもごくまれな「巨神の夢を見た魚」。
//   region = 釣れる土地 (ContinentMap の番号。ANY = どこでも)、rarity = C ふつう・U ときどき・R まれ・L ごくまれ
//   大きさは mm (min〜max。大きいものほど出にくい)、sell = 食べ物屋が買い取る鐘銭。絵は tools/gen/items/gen_fish.py (textures/item/fish_<id>.png)
public final class Fish {
    public static final int ANY = -1;

    public record Species(String id, int region, char rarity, int minMm, int maxMm) {
        public String itemId() {
            return "fish_" + id;
        }

        public int sell() {
            return switch (rarity) {
                case 'U' -> 15;
                case 'R' -> 40;
                case 'L' -> 300;
                default -> 6;
            };
        }
    }

    // まれさごとの出る割合 (百分率)。L はどの土地でも、残りはその土地の表から
    public static final int PCT_COMMON = 60;
    public static final int PCT_UNCOMMON = 30;
    public static final int PCT_RARE = 9;
    public static final int PCT_LEGEND = 1;

    public static final List<Species> ALL = List.of(
            new Species("crucian", ContinentMap.MEADOW, 'C', 80, 220),
            new Species("sweetfish", ContinentMap.MEADOW, 'C', 120, 280),
            new Species("bell_carp", ContinentMap.MEADOW, 'U', 300, 800),
            new Species("mud_catfish", ContinentMap.MEADOW, 'C', 250, 700),
            new Species("striped_dace", ContinentMap.MEADOW, 'C', 60, 140),
            new Species("mill_trout", ContinentMap.MEADOW, 'U', 200, 550),
            new Species("moss_sculpin", ContinentMap.WHALE, 'C', 80, 250),
            new Species("songstone_flounder", ContinentMap.WHALE, 'U', 200, 500),
            new Species("bone_cod", ContinentMap.WHALE, 'C', 300, 900),
            new Species("barnacle_bream", ContinentMap.WHALE, 'U', 200, 480),
            new Species("ash_herring", ContinentMap.WHALE, 'C', 150, 300),
            new Species("whalebone_eel", ContinentMap.WHALE, 'R', 600, 1600),
            new Species("pearl_bream", ContinentMap.TIDE, 'U', 200, 500),
            new Species("tideshell_puffer", ContinentMap.TIDE, 'U', 100, 350),
            new Species("kelp_seahorse", ContinentMap.TIDE, 'R', 80, 250),
            new Species("coral_parrotfish", ContinentMap.TIDE, 'C', 200, 600),
            new Species("moon_mackerel", ContinentMap.TIDE, 'C', 250, 600),
            new Species("swirl_shark", ContinentMap.TIDE, 'R', 500, 1400),
            new Species("thunder_char", ContinentMap.STORM, 'U', 200, 550),
            new Species("cloud_trout", ContinentMap.STORM, 'C', 200, 500),
            new Species("pass_yamame", ContinentMap.STORM, 'C', 150, 350),
            new Species("lightning_eel", ContinentMap.STORM, 'R', 600, 1800),
            new Species("featherfin_flyingfish", ContinentMap.STORM, 'U', 200, 400),
            new Species("glacier_icefish", ContinentMap.STORM, 'C', 50, 150),
            new Species("ember_snapper", ContinentMap.EMBER, 'U', 250, 600),
            new Species("ash_catfish", ContinentMap.EMBER, 'C', 300, 800),
            new Species("hotspring_goldfish", ContinentMap.EMBER, 'C', 50, 200),
            new Species("obsidian_spinefish", ContinentMap.EMBER, 'U', 150, 400),
            new Species("firetail", ContinentMap.EMBER, 'C', 120, 300),
            new Species("flame_dragonfish", ContinentMap.EMBER, 'R', 500, 1200),
            new Species("dream_fish", ANY, 'L', 400, 2000));

    private Fish() {
    }

    @Nullable
    public static Species byId(String id) {
        for (Species s : ALL) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    // 釣りの土地 (はじまりの島と、大陸の外は都のまわりの表)
    public static int fishingRegion(int region) {
        return region >= ContinentMap.WHALE && region <= ContinentMap.EMBER ? region : ContinentMap.MEADOW;
    }

    public static List<Species> of(int region, char rarity) {
        List<Species> out = new ArrayList<>();
        for (Species s : ALL) {
            if (s.region == region && s.rarity == rarity) {
                out.add(s);
            }
        }
        return out;
    }

    // 一匹を引く (まれさを決め、その土地の表から。表にないまれさは一つ下へ)
    public static Species roll(int region, RandomSource random) {
        return roll(region, random, false);
    }

    // boost = 釣り場 (FishingSpots): ときどき・まれが 2 倍 (ふつうが減る)
    public static Species roll(int region, RandomSource random, boolean boost) {
        int r = random.nextInt(100);
        if (r < PCT_LEGEND) {
            return byId("dream_fish");
        }
        int rare = boost ? PCT_RARE * 2 : PCT_RARE;
        int unc = boost ? PCT_UNCOMMON * 2 : PCT_UNCOMMON;
        char rarity = r < PCT_LEGEND + rare ? 'R' : r < PCT_LEGEND + rare + unc ? 'U' : 'C';
        int land = fishingRegion(region);
        for (char c : new char[] {rarity, 'U', 'C'}) {
            if (c == 'U' && rarity == 'C') {
                continue;
            }
            List<Species> pool = of(land, c);
            if (!pool.isEmpty()) {
                return pool.get(random.nextInt(pool.size()));
            }
        }
        return of(ContinentMap.MEADOW, 'C').get(0);
    }

    // 大きさ (mm)。大きいほど出にくい (0〜1 の乱数を 1.7 乗)
    public static int size(Species s, RandomSource random) {
        double t = Math.pow(random.nextDouble(), 1.7);
        return s.minMm + (int) Math.round((s.maxMm - s.minMm) * t);
    }

    // 釣り大会の点 (その種の一番大きい大きさに対する割合。千分率)
    public static int score(Species s, int sizeMm) {
        return (int) Math.round(1000.0 * sizeMm / s.maxMm);
    }
}
