package jp.revati.skyabyss.item.wild;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 野の装備の自己テスト (SKYABYSS_SELFTEST=1、段 "wild gear")。docs/design/51
//  - 表: 武器 102 本・防具 18 式が登録され、職 (名の語)・階級・必要 Lv が表と同じ。帯ごとの数 (職ごとに 2・3・3・4・5、防具 3・3・3・4・5)
//  - 強さの線: 近接の攻撃力 × 速さが目安 E(L) × 階級の係数の 0.6〜1.06 倍、弓の倍率が 0.95〜2.4。由来 (強化の素材) が土地に合う
//  - 落とし物: 土地・層・月・雲海の源があり、主は初めて必ず、帯と階級の合う品が出る。職への寄せ
//  - 絵と文: 品のモデル・32x32 のアイコン・低ポリのメッシュ (階級の形の名)・振りの動き (弓のほか)・防具のメッシュと絵・日英の名前と説明
public final class WildGearSelfTest {
    private WildGearSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            table(f);
            curve(f);
            loot(server, f, notes);
            assets(f, notes);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("wild gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST wild gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST wild gear PASSED (table, jobs, levels, curve, loot, assets)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST wild gear FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static final int[] PER_JOB = {2, 3, 3, 4, 5};

    static void table(List<String> f) {
        check(f, WildGearTable.WEAPONS.size() == 102, "weapons " + WildGearTable.WEAPONS.size() + " != 102");
        check(f, WildGearTable.ARMORS.size() == 18, "armor sets " + WildGearTable.ARMORS.size() + " != 18");
        Map<Job, int[]> perJob = new EnumMap<>(Job.class);
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            Item item = WildGearSetup.item(w.id());
            check(f, item != Items.AIR && WildGearSetup.weapon(item) == w, "not registered: " + w.id());
            check(f, Job.of(item) == w.job(), w.id() + " job " + Job.of(item) + " != " + w.job());
            check(f, item instanceof ModGear g && g.tier() == w.tier() && GearLevel.required(item) == w.level(), w.id() + " tier/level differ");
            check(f, item instanceof ModGear g && g.dungeon().equals(WildGearTypes.forgeOrigin(w.region())), w.id() + " forge origin differs");
            check(f, w.fx().size() >= 1, w.id() + " has no ability");
            perJob.computeIfAbsent(w.job(), k -> new int[5])[w.band() - 1]++;
        }
        for (Job j : Job.values()) {
            check(f, java.util.Arrays.equals(perJob.getOrDefault(j, new int[5]), PER_JOB), j + " band counts " + java.util.Arrays.toString(perJob.get(j)));
        }
        int[] sets = new int[5];
        for (WildGearTypes.ArmorSet a : WildGearTable.ARMORS) {
            sets[a.band() - 1]++;
            var pieces = WildGearSetup.ARMOR_ITEMS.get(a.id());
            check(f, pieces != null && pieces.size() == 4, a.id() + " pieces missing");
            if (pieces != null) {
                for (var p : pieces) {
                    check(f, p.get() instanceof WildArmorItem w && w.set() == a && GearLevel.required(p.get()) == a.level() && w.tier() == a.tier(),
                            a.id() + " piece differs: " + p.getId());
                }
            }
            check(f, !a.set2().isEmpty() || !a.set4().isEmpty(), a.id() + " has no set effect");
        }
        check(f, java.util.Arrays.equals(sets, new int[]{3, 3, 3, 4, 5}), "armor band counts " + java.util.Arrays.toString(sets));
        // 装飾の枠の品: 帯ごとに装飾 2・3・3・4・5、護符 1・1・2・2・3。装飾は体に描く場所を持つ
        int[] trinkets = new int[5];
        int[] charms = new int[5];
        for (WildGearTypes.Accessory x : WildGearTable.ACCESSORIES) {
            Item item = WildGearSetup.item(x.id());
            check(f, item instanceof WildAccessoryItem a && a.row() == x, "accessory not registered: " + x.id());
            check(f, item instanceof jp.revati.skyabyss.item.accessory.Accessory a && a.accessoryKind()
                    == (x.charm() ? jp.revati.skyabyss.item.accessory.AccessoryKind.CHARM : jp.revati.skyabyss.item.accessory.AccessoryKind.TRINKET),
                    x.id() + " slot kind differs");
            check(f, GearLevel.required(item) == x.level() && item instanceof ModGear g && g.tier() == x.tier() && !g.enhanceable(), x.id() + " tier/level/forge differ");
            check(f, x.charm() ? x.cooldown() >= 20 : x.mount() != null, x.id() + (x.charm() ? " has no cooldown" : " has no body mount"));
            check(f, Job.of(item) == null, x.id() + " was mapped to a job");
            (x.charm() ? charms : trinkets)[x.band() - 1]++;
        }
        check(f, java.util.Arrays.equals(trinkets, new int[]{2, 3, 3, 4, 5}) && java.util.Arrays.equals(charms, new int[]{1, 1, 2, 2, 3}),
                "accessory band counts " + java.util.Arrays.toString(trinkets) + " / " + java.util.Arrays.toString(charms));
    }

    // 目安 E(L) (spec.py の E_PTS と同じ)
    static double target(int lv, GearTier tier) {
        double[][] pts = {{1, 8.0}, {8, 10.0}, {14, 12.6}, {20, 14.6}, {26, 16.0}, {40, 19.4}, {50, 23.0}};
        double e = pts[pts.length - 1][1];
        for (int i = 0; i + 1 < pts.length; i++) {
            if (lv >= pts[i][0] && lv <= pts[i + 1][0]) {
                e = pts[i][1] + (pts[i + 1][1] - pts[i][1]) * (lv - pts[i][0]) / (pts[i + 1][0] - pts[i][0]);
                break;
            }
        }
        double k = switch (tier) {
            case UNCOMMON -> 0.95;
            case RARE -> 0.98;
            case EPIC -> 1.02;
            default -> 1.06;
        };
        return e * k;
    }

    static void curve(List<String> f) {
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            if (w.isBow()) {
                check(f, w.bow() >= 0.95 && w.bow() <= 2.4, w.id() + " bow multiplier " + w.bow());
                continue;
            }
            double base = w.attack() * w.speed();
            double t = target(w.level(), w.tier());
            check(f, base <= t * 1.06 && base >= t * 0.6, w.id() + " base dps " + base + " vs target " + t);
        }
        // 帯の中で、上の階級は下の階級より必要 Lv が低くない
        for (Job j : Job.values()) {
            for (int band = 1; band <= 5; band++) {
                int lastLevel = 0;
                int lastTier = -1;
                for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
                    if (w.job() == j && w.band() == band) {
                        check(f, w.level() >= lastLevel && w.tier().ordinal() >= lastTier, w.id() + " out of order in band " + band);
                        lastLevel = w.level();
                        lastTier = w.tier().ordinal();
                    }
                }
            }
        }
    }

    static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource rand = RandomSource.create(7);
        for (int r = 0; r <= 5; r++) {
            check(f, WildLoot.region(r) != null, "no source for region " + r);
        }
        for (int layer = 0; layer <= 4; layer++) {
            check(f, WildLoot.abyss(layer) != null, "no source for abyss layer " + layer);
        }
        net.minecraft.server.level.ServerPlayer p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        // 主: 初めては必ず、帯と階級の合う品
        List<WildLoot.Source> sources = new ArrayList<>();
        for (int r = 0; r <= 5; r++) {
            sources.add(WildLoot.region(r));
        }
        sources.add(WildLoot.abyss(0));
        sources.add(WildLoot.abyss(3));
        sources.add(WildLoot.MOON);
        sources.add(WildLoot.CLOUD);
        for (WildLoot.Source src : sources) {
            String foe = "skyabyss:selftest_" + src.name();
            p.getPersistentData().remove(WildLoot.FIRST + foe);
            ItemStack first = WildLoot.roll(p, src, true, foe, rand);
            check(f, !first.isEmpty() && first.getItem() instanceof WildGear, src.name() + ": first boss kill gave nothing");
            if (first.getItem() instanceof WildGear g) {
                int band = WildGearTypes.band(g.wildLevel());
                boolean heroic = g.tier() == GearTier.HEROIC;
                check(f, heroic || band == src.bossBand(), src.name() + ": boss item band " + band + " != " + src.bossBand());
            }
            p.getPersistentData().remove(WildLoot.FIRST + foe);
        }
        // 土地の敵: 10 万回の抽選で、どの品も帯と階級の表に合う
        Map<String, Integer> got = new HashMap<>();
        for (WildLoot.Source src : sources) {
            for (int i = 0; i < 20000; i++) {
                ItemStack s = WildLoot.roll(p, src, false, "x", rand);
                if (!s.isEmpty()) {
                    got.merge(src.name(), 1, Integer::sum);
                    boolean ok = s.getItem() instanceof WildGear g && (src.rolls().stream().anyMatch(r -> r.band() == WildGearTypes.band(g.wildLevel()) && r.tier() == g.tier())
                            || src.heroic() != null && g.tier() == GearTier.HEROIC);
                    check(f, ok, src.name() + ": unexpected drop " + s.getItem());
                }
            }
        }
        notes.add("drops per 20000 kills: " + got);
        // 職への寄せ: 武器が当たるとき 2/3 ほどが今の職
        int mine = 0, all = 0;
        p.getInventory().clearContent();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WildGearSetup.item("wild_guard_sword")));
        for (int i = 0; i < 3000; i++) {
            ItemStack s = WildLoot.pick(p, r -> WildGearTypes.band(r.level()) == 2 && r.tier() == GearTier.RARE, rand);
            if (WildGearSetup.weapon(s) != null) {
                all++;
                if (Job.of(s) == Job.WARRIOR) {
                    mine++;
                }
            }
        }
        double share = all == 0 ? 0 : (double) mine / all;
        check(f, share > 0.6 && share < 0.8, "job bias " + share + " (want about 0.72)");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // 護符を使う: 草薬の小瓶で体力が戻り、待ちに入り、待ちの最中は使えない
        Item vial = WildGearSetup.item("wild_herb_vial");
        if (vial instanceof WildAccessoryItem charm) {
            // 必要 Lv は問わない (創造)。体力を減らしてから使う
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.setHealth(5f);
            ItemStack s = new ItemStack(vial);
            boolean used = charm.activate(p, s);
            check(f, used && p.getHealth() > 5f && p.getCooldowns().isOnCooldown(vial) && !charm.activate(p, s), "herb vial charm did not heal / cool down");
            p.getCooldowns().removeCooldown(vial);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }
        // 装飾の効き目は付けているあいだの一式の効果と一緒に数える
        check(f, WildArmorEvents.active(p).isEmpty(), "a bare player should have no wild set or trinket effects");
    }

    static void assets(List<String> f, List<String> notes) {
        String a = "/assets/skyabyss/";
        Map<String, String> ja = lang("ja_jp", f);
        Map<String, String> en = lang("en_us", f);
        int missingMesh = 0;
        for (WildGearTypes.Weapon w : WildGearTable.WEAPONS) {
            String mesh = w.tier().meshTier;
            check(f, exists(a + "models/item/" + w.id() + ".json"), "item model missing: " + w.id());
            check(f, java.util.Arrays.equals(pngSize(a + "textures/item/" + w.id() + ".png"), new int[]{32, 32}), "icon is not 32x32: " + w.id());
            if (!exists(a + "lowpoly/items/" + w.id() + "/" + mesh + ".json")) {
                missingMesh++;
                f.add("low-poly mesh missing: " + w.id() + "/" + mesh);
            }
            check(f, w.isBow() || exists(a + "weapon_moves/" + w.id() + ".json"), "weapon moves missing: " + w.id());
            check(f, ja.containsKey("item.skyabyss." + w.id()) && en.containsKey("item.skyabyss." + w.id()), "name missing (ja/en): " + w.id());
            check(f, ja.containsKey("item.skyabyss." + w.id() + ".desc") && en.containsKey("item.skyabyss." + w.id() + ".desc"), "desc missing: " + w.id());
        }
        for (WildGearTypes.ArmorSet s : WildGearTable.ARMORS) {
            String suffix = switch (s.tier().meshTier) {
                case "common" -> "";
                case "rare" -> "_rare";
                default -> "_legend";
            };
            for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
                String id = s.id() + "_" + piece;
                check(f, exists(a + "lowpoly/armor/" + s.id() + "/" + piece + ".json") && exists(a + "lowpoly/armor/" + s.id() + "/" + piece + suffix + ".json"),
                        "armor mesh missing: " + id);
                check(f, exists(a + "textures/lowpoly/armor/" + s.id() + "/" + piece + suffix + ".png"), "armor atlas missing: " + id);
                check(f, java.util.Arrays.equals(pngSize(a + "textures/item/" + id + ".png"), new int[]{32, 32}), "icon is not 32x32: " + id);
                check(f, ja.containsKey("item.skyabyss." + id) && en.containsKey("item.skyabyss." + id), "name missing (ja/en): " + id);
            }
            check(f, ja.containsKey("item.skyabyss." + s.id() + ".desc") && en.containsKey("item.skyabyss." + s.id() + ".desc"), "set desc missing: " + s.id());
        }
        for (WildGearTypes.Accessory x : WildGearTable.ACCESSORIES) {
            check(f, exists(a + "models/item/" + x.id() + ".json") && exists(a + "models/item_flat/" + x.id() + ".json"), "accessory model missing: " + x.id());
            check(f, java.util.Arrays.equals(pngSize(a + "textures/item/" + x.id() + ".png"), new int[]{32, 32}), "icon is not 32x32: " + x.id());
            check(f, exists(a + "lowpoly/items/" + x.id() + "/" + x.tier().meshTier + ".json"), "accessory mesh missing: " + x.id());
            check(f, ja.containsKey("item.skyabyss." + x.id()) && en.containsKey("item.skyabyss." + x.id() + ".desc"), "accessory text missing: " + x.id());
        }
        notes.add("weapons without a low-poly mesh: " + missingMesh);
        check(f, ja.containsKey("message.skyabyss.wild_drop") && en.containsKey("message.skyabyss.wild_drop"), "drop message missing");
    }

    private static boolean exists(String path) {
        try (InputStream in = WildGearSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] pngSize(String path) {
        try (InputStream in = WildGearSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return new int[]{0, 0};
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            return img == null ? new int[]{0, 0} : new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    // 野の装備の言葉は assets/skyabyss_wild/lang にある
    private static Map<String, String> lang(String locale, List<String> f) {
        Map<String, String> map = new HashMap<>();
        for (String ns : List.of("skyabyss", "skyabyss_wild")) {
            try (InputStream in = WildGearSelfTest.class.getResourceAsStream("/assets/" + ns + "/lang/" + locale + ".json")) {
                if (in == null) {
                    f.add("missing lang " + ns + "/" + locale);
                    continue;
                }
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            } catch (Exception e) {
                f.add("unreadable lang " + ns + "/" + locale + " " + e);
            }
        }
        return map;
    }

}
