package jp.revati.skyabyss.story.ch6.gear;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.gear.AbyssGear;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearEvents;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 六章「月」の装備の自己テスト (SKYABYSS_SELFTEST=1、段 "moon gear")。docs/design/03 の 12.2。6 職それぞれにレアとヒロイック
//  - 表: 図鑑 11 品と涙・素材 3 つが登録され、由来が月、階級と必要 Lv (エピック以下 50・ヒロイック以上 52)、強化の燃料が月の欠片
//  - 強さの曲線: 武器の攻撃力・速さ、+0 / +10 が月の目安 ±5% (ヒロイックは 1.10 倍まで)、深淵の段から逆転しない。鎧 26 / 16 (+4 で 20)
//  - 一式・固有効果: 4 部位の落下 ×0.5、天秤の向き、背の向き
//  - 報酬: 区画の主の鎧の一部位 (初回)・段の上乗せ、核の初回の心核、図鑑が埋まるまでの回数
//  - 絵と文: 品のモデル・32x32 のアイコン・低ポリのメッシュ・鎧のメッシュ・月の暦のブロック・日英の名前
public final class MoonGearSelfTest {
    private MoonGearSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            table(f);
            curve(f, notes);
            effects(server, f);
            loot(server, f, notes);
            assets(f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("moon gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST moon gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon gear PASSED (table, levels, stats, curve vs abyss, set bonuses, lord/core loot, forge, assets)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST moon gear FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static Item item(String id) {
        return TitanGearSet.item(id).orElseThrow(() -> new IllegalStateException("not registered: " + id));
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ---------------------------------------------------------------- 表
    private static void table(List<String> f) {
        check(f, TitanGearSets.get(MoonGear.DUNGEON) == MoonGear.SET, "moon set is not registered in TitanGearSets");
        check(f, MoonGear.SET.collection().size() == 12 && MoonGear.SET.exclusives().equals(List.of(MoonGear.BLADE, MoonGear.RING)),
                "moon codex should hold 12 items with 2 exclusives: " + MoonGear.SET.collection());
        // 別枠のヒロイック 5 本: 図鑑に入れず、ヒロイック・Lv 52
        for (String id : MoonGear.HEROICS) {
            check(f, TitanGearSet.registered(id) && !MoonGear.SET.collection().contains(id), id + " should be registered and outside the codex");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.tier() == GearTier.HEROIC && g.dungeon().equals(MoonGear.DUNGEON) && GearLevel.required(it) == 52,
                    id + " should be a Lv 52 heroic moon weapon");
        }
        for (String id : MoonGear.SET.collection()) {
            check(f, TitanGearSet.registered(id), id + " is not registered");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.dungeon().equals(MoonGear.DUNGEON), id + " is not moon gear");
            boolean ex = MoonGear.SET.isExclusive(id);
            GearTier want = ex ? GearTier.HEROIC : GearTier.RARE;
            check(f, it instanceof ModGear g && g.tier() == want, id + " has the wrong tier (want " + want.key + ")");
            int lv = ex ? 52 : 50;
            check(f, GearLevel.required(it) == lv, id + " requires Lv " + GearLevel.required(it) + " (want " + lv + ")");
            check(f, TitanGearSets.of(it) == MoonGear.SET, id + " does not map to the moon set");
        }
        for (String id : List.of(MoonGear.SHARD, MoonGear.CORE, MoonGear.RESONANCE_SHARD, MoonGear.TEAR)) {
            check(f, TitanGearSet.registered(id) && GearLevel.required(item(id)) == 0, id + " missing or has a level requirement");
        }
        check(f, MoonGear.SET.shardItem().orElse(null) == MoonGearSetup.SHARD.get(), "moon fragment is not the set's shard");
        check(f, near(MoonGear.SET.chance(MoonGear.HELMET), 0.25) && near(MoonGear.SET.chance(MoonGear.HAMMER), 0.20)
                && near(MoonGear.SET.chance(MoonGear.BLADE), 0.04) && near(MoonGear.SET.chance(MoonGear.RING), 0.04), "core chances do not match 03 12.2");
        check(f, !ForgeRules.canEnhance(new ItemStack(item(MoonGear.RING))) && !ForgeRules.canEnhance(new ItemStack(item(MoonGear.TEAR))),
                "the ring and the tear must not be enhanced");
        ItemStack hammer = new ItemStack(item(MoonGear.HAMMER));
        check(f, ForgeRules.canEnhance(hammer) && ForgeRules.canEnhance(new ItemStack(item(MoonGear.HELMET))), "weapons and armor should be enhanceable");
        check(f, ForgeRules.shardItem(hammer) == MoonGearSetup.SHARD.get(), "moon weapons should be enhanced with moon fragments");
    }

    // ---------------------------------------------------------------- 数値と曲線
    private static double speed(Item item) {
        double s = 4;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_SPEED) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                s += e.modifier().amount();
            }
        }
        return s;
    }

    private static void curve(List<String> f, List<String> notes) {
        Object[][] weapons = {
                {MoonGear.SWORD, MoonGear.SWORD_ATTACK, MoonGear.SWORD_SPEED},
                {MoonGear.FULL_HAMMER, MoonGear.FULL_HAMMER_ATTACK, MoonGear.FULL_HAMMER_SPEED},
                {MoonGear.NEW_STAFF, MoonGear.NEW_STAFF_ATTACK, MoonGear.NEW_STAFF_SPEED},
                {MoonGear.UMBRA_DAGGER, MoonGear.UMBRA_ATTACK, MoonGear.UMBRA_SPEED},
                {MoonGear.HALO_BELL, MoonGear.HALO_ATTACK, MoonGear.HALO_SPEED},
                {MoonGear.HAMMER, MoonGear.HAMMER_ATTACK, MoonGear.HAMMER_SPEED},
                {MoonGear.STAFF, MoonGear.STAFF_ATTACK, MoonGear.STAFF_SPEED},
                {MoonGear.DAGGER, MoonGear.DAGGER_ATTACK, MoonGear.DAGGER_SPEED},
                {MoonGear.BELL, MoonGear.BELL_ATTACK, MoonGear.BELL_SPEED},
                {MoonGear.BLADE, MoonGear.BLADE_ATTACK, MoonGear.BLADE_SPEED}};
        for (Object[] w : weapons) {
            Item it = item((String) w[0]);
            check(f, near(GearEvents.weaponAttack(it), (double) w[1]), w[0] + " attack " + GearEvents.weaponAttack(it) + " (want " + w[1] + ")");
            check(f, near(speed(it), (double) w[2]), w[0] + " speed " + speed(it) + " (want " + w[2] + ")");
        }
        var st = MoonGear.STAGE;
        var ab = AbyssGear.STAGE;
        String[] names = {"scale hammer", "tide staff", "crescent dagger", "starlight bell", "waning blade", "light sword", "full hammer",
                "new staff", "umbra dagger", "halo bell"};
        boolean[] heroic = {false, false, false, false, true, false, true, true, true, true};
        double[][] dps = {
                {MoonGear.hammerDps(0), MoonGear.hammerDps(10)},
                {MoonGear.staffDps(0), MoonGear.staffDps(10)},
                {MoonGear.daggerDps(0), MoonGear.daggerDps(10)},
                {MoonGear.bellDps(0), MoonGear.bellDps(10)},
                {MoonGear.bladeDps(0), MoonGear.bladeDps(10)},
                {MoonGear.swordDps(0), MoonGear.swordDps(10)},
                {MoonGear.fullHammerDps(0), MoonGear.fullHammerDps(10)},
                {MoonGear.newStaffDps(0), MoonGear.newStaffDps(10)},
                {MoonGear.umbraDps(0), MoonGear.umbraDps(10)},
                {MoonGear.haloDps(0), MoonGear.haloDps(10)}};
        for (int i = 0; i < dps.length; i++) {
            double d0 = dps[i][0];
            double d10 = dps[i][1];
            double top = heroic[i] ? 1.10 : 1.05;
            // ヒロイックは同じ職のレアより強い (レアの目安の上限 1.05 倍を越える)
            check(f, !heroic[i] || d0 > st.dps0() * 1.0, names[i] + " heroic +0 " + fmt(d0) + " is not above the stage");
            notes.add(names[i] + " dps +0 / +10 = " + fmt(d0) + " / " + fmt(d10));
            check(f, d0 >= st.dps0() * 0.95 && d0 <= st.dps0() * top, names[i] + " +0 " + fmt(d0) + " is off the moon stage " + st.dps0());
            check(f, d10 >= st.dps10() * 0.95 && d10 <= st.dps10() * top, names[i] + " +10 " + fmt(d10) + " is off the moon stage " + st.dps10());
            check(f, d0 >= ab.dps0() * 1.1 && d0 <= ab.dps10(), names[i] + " +0 " + fmt(d0) + " should sit between abyss +0 x1.1 and abyss +10");
            check(f, d10 > ab.dps10(), names[i] + " +10 " + fmt(d10) + " should beat abyss +10 " + ab.dps10());
        }
        // 弓は残像の矢 (40%)・落ちる星 (40%) 込みで近接の 0.85 倍の目安 (レア ±5%、ヒロイックは 1.10 倍まで)
        var bow = (jp.revati.skyabyss.item.ChapterBowItem) item(MoonGear.BOW);
        var star = (jp.revati.skyabyss.item.ChapterBowItem) item(MoonGear.STARFALL_BOW);
        double b0 = bow.dps(0) * (1 + MoonGear.BOW_ECHO);
        double s0 = star.dps(0) * (1 + MoonGear.STARFALL_SHARE);
        double ranged = st.dps0() * 0.85;
        notes.add("bows +0 (with echo / star): waning " + fmt(b0) + ", starfall " + fmt(s0) + " (ranged target " + fmt(ranged) + ")");
        check(f, b0 >= ranged * 0.95 && b0 <= ranged * 1.05, "waning bow +0 " + fmt(b0) + " is off the ranged target " + fmt(ranged));
        check(f, s0 > b0 && s0 <= ranged * 1.10, "starfall bow +0 " + fmt(s0) + " should beat the waning bow and stay within x1.10");
        check(f, st.level() > ab.level() && st.dps0() > ab.dps0() && st.dps10() > ab.dps10() && st.armor() > ab.armor() && st.toughness() > ab.toughness(),
                "the moon stage should grow past the abyss stage");
        double defense = 0;
        double toughness = 0;
        for (var a : MoonGearSetup.armor()) {
            defense += ((ArmorItem) a.get()).getDefense();
            toughness += ((ArmorItem) a.get()).getToughness();
        }
        check(f, defense == st.armor() && near(toughness + MoonGear.SET2_TOUGHNESS, st.toughness()),
                "moon armor " + defense + " / " + toughness + " (+" + MoonGear.SET2_TOUGHNESS + ") want " + st.armor() + " / " + st.toughness());
    }

    // ---------------------------------------------------------------- 一式・固有効果
    private static FakePlayer player(ServerLevel level, String name, int lv) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        net.minecraft.core.BlockPos spawn = level.getSharedSpawnPos();
        p.setPos(spawn.getX() + 0.5, level.getMinBuildHeight() + 2, spawn.getZ() + 0.5);
        Growth.get(p).level = lv;
        return p;
    }

    private static void effects(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "moon_gear_test", 56);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            p.setItemSlot(slots[i], new ItemStack(MoonGearSetup.armor().get(i).get()));
        }
        check(f, MoonWalkerArmorItem.piecesWorn(p) == 4 && MoonGearEvents.armorSet(p), "four moon pieces should count as the set");
        check(f, near(MoonGearEvents.takenFactor(p, level.damageSources().fall()), MoonGear.SET4_FALL_TAKEN), "the set should halve fall damage on the surface");
        check(f, near(MoonGearEvents.takenFactor(p, level.damageSources().generic()), 1.0), "the moon reduction should not apply on the surface");
        check(f, MoonGear.heavier(80, 100, 10, 20) && !MoonGear.heavier(10, 100, 20, 20), "the scale should tip toward the heavier side");
        // 背: 相手の向きの真後ろから
        FakePlayer t = player(level, "moon_gear_target", 56);
        t.setYBodyRot(0);
        Vec3 fwd = Vec3.directionFromRotation(0, 0);
        check(f, MoonGearEvents.fromBehind(t, t.position().subtract(fwd.scale(2))) && !MoonGearEvents.fromBehind(t, t.position().add(fwd.scale(2))),
                "behind check should tell back from front");
        FakePlayer low = player(level, "moon_gear_low", 40);
        check(f, !new ItemStack(MoonGearSetup.HELMET.get()).canEquip(EquipmentSlot.HEAD, low), "a Lv 40 player could wear moon armor");
    }

    // ---------------------------------------------------------------- 報酬・炉
    private static int count(List<ItemStack> l, Item item) {
        return l.stream().filter(s -> s.getItem() == item).mapToInt(ItemStack::getCount).sum();
    }

    private static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(5151);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        List<ItemStack> first = MoonGearLoot.rollLord(r, "cliff", 0, random, TitanGearSet::registered);
        check(f, count(first, MoonGearSetup.CHESTPLATE.get()) == 1 && r.owned.contains(MoonGear.CHESTPLATE), "the moon scale should give the coat the first time");
        check(f, count(first, MoonGearSetup.SHARD.get()) >= 3 && count(first, MoonGearSetup.RESONANCE_SHARD.get()) == 1, "a lord should give 3-5 fragments and a resonance shard");
        List<ItemStack> again = MoonGearLoot.rollLord(r, "cliff", 4, random, TitanGearSet::registered);
        check(f, count(again, MoonGearSetup.CHESTPLATE.get()) == 0 && count(again, MoonGearSetup.SHARD.get()) >= 3 + 4 + 4
                && count(again, MoonGearSetup.RESONANCE_SHARD.get()) == 3, "a tier 4 trial with the piece owned should give extra fragments and shards");
        check(f, List.of("valley", "cliff", "field", "rift").stream().map(MoonGear::lordPiece).distinct().count() == 4, "each lord should give a different piece");
        TitanMemoryData.Record c = new TitanMemoryData.Record();
        List<ItemStack> stacks = new ArrayList<>();
        MoonGearLoot.Roll roll = MoonGearLoot.rollCore(c, 0, random, TitanGearSet::registered, stacks);
        check(f, roll.core() && c.firstCleared && count(stacks, MoonGearSetup.CORE.get()) == 1, "the first core clear should give a moon heart");
        check(f, count(stacks, MoonGearSetup.RESONANCE_SHARD.get()) >= 2, "the core should give 2 resonance shards");
        // 別枠のヒロイック: 一本ずつ 2% (段 5 で 3%)
        int hero = 0;
        int hero5 = 0;
        for (int i = 0; i < 20000; i++) {
            hero += MoonGearLoot.heroicDrops(0, random, TitanGearSet::registered).size();
            hero5 += MoonGearLoot.heroicDrops(5, random, TitanGearSet::registered).size();
        }
        notes.add("heroic drops per core clear: tier 0 " + fmt(hero / 20000.0) + ", tier 5 " + fmt(hero5 / 20000.0));
        check(f, hero > 1600 && hero < 2400 && hero5 > hero, "heroic drops should be about 0.1 per clear: " + hero + " / 20000");
        int total = 0;
        int worst = 0;
        for (int i = 0; i < 4000; i++) {
            TitanMemoryData.Record s = new TitanMemoryData.Record();
            int k = 0;
            while (MoonGear.SET.collected(s) < 12 && k < 200) {
                MoonGearLoot.rollCore(s, 0, random, TitanGearSet::registered, new ArrayList<>());
                k++;
            }
            total += k;
            worst = Math.max(worst, k);
        }
        double avg = total / 4000.0;
        notes.add("core clears to fill the moon codex: avg " + fmt(avg) + ", worst " + worst);
        check(f, avg < 22 && worst < 200, "the moon codex takes too long: avg " + fmt(avg) + " worst " + worst);
        ItemStack hammer = new ItemStack(item(MoonGear.HAMMER));
        FakePlayer p = player(server.overworld(), "moon_gear_forge", 56);
        p.getInventory().add(new ItemStack(MoonGearSetup.SHARD.get(), 10));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 8));
        check(f, ForgeRules.enhance(p, hammer) && ModGear.enhance(hammer) == 1, "the scale hammer was not enhanced with moon fragments");
    }

    // ---------------------------------------------------------------- 絵と文
    private static boolean exists(String path) {
        try (InputStream in = MoonGearSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] pngSize(String path) {
        try (InputStream in = MoonGearSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return new int[] {0, 0};
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            return img == null ? new int[] {0, 0} : new int[] {img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return new int[] {0, 0};
        }
    }

    private static Map<String, String> lang(String locale, List<String> f) {
        Map<String, String> map = new java.util.HashMap<>();
        try (InputStream in = MoonGearSelfTest.class.getResourceAsStream("/assets/skyabyss/lang/" + locale + ".json")) {
            if (in == null) {
                f.add("missing lang " + locale);
                return map;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            f.add("unreadable lang " + locale + " " + e);
        }
        return map;
    }

    private static void assets(List<String> f) {
        String a = "/assets/skyabyss/";
        List<String> all = new ArrayList<>(MoonGear.SET.collection());
        all.addAll(List.of(MoonGear.SHARD, MoonGear.CORE, MoonGear.RESONANCE_SHARD, MoonGear.TEAR));
        Map<String, String> ja = lang("ja_jp", f);
        Map<String, String> en = lang("en_us", f);
        for (String id : all) {
            String path = id.substring(id.indexOf(':') + 1);
            check(f, exists(a + "models/item/" + path + ".json"), "item model missing: " + path);
            check(f, java.util.Arrays.equals(pngSize(a + "textures/item/" + path + ".png"), new int[] {32, 32}), "icon is not 32x32: " + path);
            check(f, ja.containsKey("item.skyabyss." + path) && en.containsKey("item.skyabyss." + path), "name missing (ja/en): " + path);
            check(f, ja.containsKey("item.skyabyss." + path + ".desc") || path.startsWith("moon_walker_"), "desc missing: " + path);
        }
        all.addAll(MoonGear.HEROICS);
        for (String id : MoonGear.HEROICS) {
            String path = id.substring(id.indexOf(':') + 1);
            check(f, exists(a + "models/item/" + path + ".json") && java.util.Arrays.equals(pngSize(a + "textures/item/" + path + ".png"), new int[] {32, 32})
                    && ja.containsKey("item.skyabyss." + path + ".desc") && en.containsKey("item.skyabyss." + path), "heroic assets missing: " + path);
        }
        for (String w : List.of("moon_light_sword:rare", "moon_scale_hammer:rare", "moon_tide_staff:rare", "moon_waning_bow:rare", "moon_waning_bow:rare_pull2",
                "moon_crescent_dagger:rare", "moon_starlight_bell_staff:rare", "moon_waning_blade:legend", "moon_full_hammer:legend", "moon_new_staff:legend",
                "moon_starfall_bow:legend", "moon_starfall_bow:legend_pull2", "moon_umbra_dagger:legend", "moon_halo_bell_staff:legend")) {
            String[] p = w.split(":");
            check(f, exists(a + "lowpoly/items/" + p[0] + "/" + p[1] + ".json"), "low-poly mesh missing: " + w);
            check(f, exists(a + "weapon_moves/" + p[0] + ".json") || p[0].endsWith("_bow"), "weapon moves missing: " + p[0]);
        }
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            check(f, exists(a + "lowpoly/armor/moon_walker/" + piece + ".json") && exists(a + "lowpoly/armor/moon_walker/" + piece + "_rare.json"),
                    "armor mesh missing: " + piece);
            check(f, exists(a + "textures/lowpoly/armor/moon_walker/" + piece + "_rare.png"), "armor atlas missing: " + piece);
        }
        check(f, exists(a + "blockstates/moon_calendar.json") && exists(a + "models/block/moon_calendar.json")
                && java.util.Arrays.equals(pngSize(a + "textures/block/moon_calendar_top.png"), new int[] {32, 32}), "moon calendar block assets missing");
        for (String k : List.of("message.skyabyss.moon_trial.title", "message.skyabyss.moon_trial.cleared", "moon_trial.skyabyss.core",
                "message.skyabyss.moon_gear.lord_reward", "item.skyabyss.moon_tear.use", "item.skyabyss.moon_walker_armor.desc")) {
            check(f, ja.containsKey(k) && en.containsKey(k), "lang missing (ja/en): " + k);
        }
    }
}
