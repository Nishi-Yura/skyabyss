package jp.revati.skyabyss.abyss.gear;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearEvents;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.GearExchange;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.ch5.depths.DepthLoot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.common.util.FakePlayer;

// 五章「深淵」の装備の自己テスト (SKYABYSS_SELFTEST=1、段 "abyss gear")。
//  - 表: 10 品と素材 3 つが登録され、由来が深淵、必要 Lv (レア 40・伝説 42)、図鑑が TitanGearSets にある、強化の燃料が錨鉄
//  - 数値と強さの曲線: 武器の攻撃力・速さ、+0 / +10 が深淵の段の目安 ±5%、余燼の段から逆転しない (+0 は余燼 +0 の 1.1 倍以上・余燼 +10 以下、+10 は余燼 +10 より上)。
//    鎧 23 / 15 (+3 で 18)、余燼の鎧より硬い
//  - 一式・固有効果: 2 部位の強靭、暗い所の傷、錘の深さ、短剣の拍の数え方
//  - 報酬: 層の主の鎧の一部位、番人の図鑑・天井・心核・交換、深みの箱。レシピ 2 つと荷運びの取り替えの品が解ける
//  - 絵: 品のモデル・32x32 のアイコン・低ポリのメッシュがある
public final class AbyssGearSelfTest {
    private AbyssGearSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            table(f);
            curve(f, notes);
            effects(server, f);
            loot(server, f, notes);
            recipes(server, f);
            assets(f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("abyss gear selftest crashed", e);
            f.add("crashed: " + e);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST abyss gear: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss gear PASSED (table, levels, stats, curve vs ember, set bonuses, lord/warden/chest loot, forge, recipes, porter, assets)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss gear FAILED: {}", x));
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
        check(f, TitanGearSets.get(AbyssGear.DUNGEON) == AbyssGear.SET, "abyss set is not registered in TitanGearSets");
        check(f, TitanGearSets.all().get(0) == TitanGearSets.STONE_WHALE, "stone whale should be the first gear set");
        check(f, AbyssGear.SET.collection().size() == 10 && AbyssGear.SET.exclusives().equals(List.of(AbyssGear.ANCHOR, AbyssGear.DAGGER, AbyssGear.PLUMB)),
                "abyss codex should hold 10 items with 3 exclusives: " + AbyssGear.SET.collection());
        for (String id : AbyssGear.SET.collection()) {
            check(f, TitanGearSet.registered(id), id + " is not registered");
            Item it = item(id);
            check(f, it instanceof ModGear g && g.dungeon().equals(AbyssGear.DUNGEON), id + " is not abyss gear");
            boolean ex = AbyssGear.SET.isExclusive(id);
            check(f, it instanceof ModGear g && g.tier() == (ex ? GearTier.LEGENDARY : GearTier.RARE), id + " has the wrong tier");
            int want = ex ? 42 : 40;
            check(f, GearLevel.required(it) == want, id + " requires Lv " + GearLevel.required(it) + " (want " + want + ")");
            check(f, TitanGearSets.of(it) == AbyssGear.SET, id + " does not map to the abyss set");
        }
        for (String id : List.of(AbyssGear.SHARD, AbyssGear.CORE, AbyssGear.RESONANCE_SHARD)) {
            check(f, TitanGearSet.registered(id) && GearLevel.required(item(id)) == 0, id + " missing or has a level requirement");
        }
        check(f, AbyssGear.SET.shardItem().orElse(null) == AbyssGearSetup.SHARD.get(), "anchor iron is not the set's shard");
        check(f, ModItems.resonanceShard(AbyssGear.DUNGEON) == AbyssGearSetup.RESONANCE_SHARD.get() && ModItems.isResonanceShard(AbyssGearSetup.RESONANCE_SHARD.get()),
                "the abyss resonance fragment is not wired into ModItems");
        check(f, near(AbyssGear.SET.chance(AbyssGear.HELMET), 0.25) && near(AbyssGear.SET.chance(AbyssGear.LAMP_SWORD), 0.20)
                && near(AbyssGear.SET.chance(AbyssGear.ANCHOR), 0.04) && near(AbyssGear.SET.chance(AbyssGear.PLUMB), 0.02), "warden chances do not match 03 12.3");
        check(f, AbyssGear.RARE_LEVEL == 40 && AbyssGear.LEGEND_LEVEL == 42, "abyss levels should be 40 / 42");
        check(f, !ForgeRules.canEnhance(new ItemStack(item(AbyssGear.CHARM))) && !ForgeRules.canEnhance(new ItemStack(item(AbyssGear.PLUMB)))
                && !ForgeRules.canEnhance(new ItemStack(item(AbyssGear.LISTENER))), "charm and trinkets must not be enhanced");
        check(f, ForgeRules.canEnhance(new ItemStack(item(AbyssGear.LAMP_SWORD))) && ForgeRules.canEnhance(new ItemStack(item(AbyssGear.HELMET))),
                "sword and armor should be enhanceable");
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

    private static GearCurve.Stage ember() {
        return GearCurve.STAGES.stream().filter(s -> s.id().equals("ember")).findFirst().orElseThrow();
    }

    private static void curve(List<String> f, List<String> notes) {
        Object[][] weapons = {
                {AbyssGear.LAMP_SWORD, AbyssGear.SWORD_ATTACK, AbyssGear.SWORD_SPEED},
                {AbyssGear.ANCHOR, AbyssGear.ANCHOR_ATTACK, AbyssGear.ANCHOR_SPEED},
                {AbyssGear.DAGGER, AbyssGear.DAGGER_ATTACK, AbyssGear.DAGGER_SPEED}};
        for (Object[] w : weapons) {
            Item it = item((String) w[0]);
            check(f, near(GearEvents.weaponAttack(it), (double) w[1]), w[0] + " attack " + GearEvents.weaponAttack(it) + " (want " + w[1] + ")");
            check(f, near(speed(it), (double) w[2]), w[0] + " speed " + speed(it) + " (want " + w[2] + ")");
        }
        GearCurve.Stage st = AbyssGear.STAGE;
        GearCurve.Stage em = ember();
        double[][] dps = {
                {AbyssGear.swordDps(0), AbyssGear.swordDps(5), AbyssGear.swordDps(10)},
                {AbyssGear.anchorDps(0), AbyssGear.anchorDps(5), AbyssGear.anchorDps(10)},
                {AbyssGear.daggerDps(0), AbyssGear.daggerDps(5), AbyssGear.daggerDps(10)}};
        String[] names = {"lamp sword", "sinking anchor", "heartbeat dagger"};
        for (int i = 0; i < dps.length; i++) {
            double d0 = dps[i][0];
            double d10 = dps[i][2];
            notes.add(names[i] + " dps +0 / +5 / +10 = " + fmt(d0) + " / " + fmt(dps[i][1]) + " / " + fmt(d10));
            check(f, d0 >= st.dps0() * 0.95 && d0 <= st.dps0() * 1.05, names[i] + " +0 " + fmt(d0) + " is not within 5% of " + st.dps0());
            check(f, d10 >= st.dps10() * 0.95 && d10 <= st.dps10() * 1.05, names[i] + " +10 " + fmt(d10) + " is not within 5% of " + st.dps10());
            check(f, d0 >= em.dps0() * 1.1 && d0 <= em.dps10(), names[i] + " +0 " + fmt(d0) + " should sit between ember +0 x1.1 and ember +10");
            check(f, d10 > em.dps10(), names[i] + " +10 " + fmt(d10) + " should beat ember +10 " + em.dps10());
        }
        check(f, st.level() > em.level() && st.dps0() > em.dps0() && st.dps10() > em.dps10() && st.armor() >= em.armor() && st.toughness() > em.toughness(),
                "the abyss stage should grow past the ember stage");
        // 鎧
        double defense = 0;
        double toughness = 0;
        for (var a : AbyssGearSetup.armor()) {
            defense += ((ArmorItem) a.get()).getDefense();
            toughness += ((ArmorItem) a.get()).getToughness();
        }
        check(f, defense == st.armor() && near(toughness + AbyssGear.SET2_TOUGHNESS, st.toughness()),
                "abyss armor " + defense + " / " + toughness + " (+" + AbyssGear.SET2_TOUGHNESS + ") want " + st.armor() + " / " + st.toughness());
        double hitAbyss = st.bossHit() * GearCurve.armorTaken(st.bossHit(), st.armor(), st.toughness());
        double hitEmber = st.bossHit() * GearCurve.armorTaken(st.bossHit(), em.armor(), em.toughness());
        notes.add("abyss hit " + st.bossHit() + " taken: abyss armor +0 " + fmt(hitAbyss) + ", ember armor +0 " + fmt(hitEmber));
        check(f, hitAbyss < hitEmber, "abyss armor +0 should take less than ember armor +0");
        // 旅装 (四章のアンコモン) < 余燼のレア < 深淵のレア: 階級の逆転がない
        GearCurve.Travel ash = GearCurve.TRAVEL.get(3);
        check(f, ash.armor() < em.armor() && em.armor() <= st.armor() && ash.toughness() < st.toughness(), "travel / ember / abyss armor should climb");
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
        FakePlayer p = player(level, "abyss_gear_test", 44);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            p.setItemSlot(slots[i], new ItemStack(AbyssGearSetup.armor().get(i).get()));
        }
        check(f, AbyssDiverArmorItem.piecesWorn(p) == 4 && AbyssGearEvents.armorSet(p), "four abyss pieces should count as the set");
        check(f, near(jp.revati.skyabyss.story.ch5.AbyssPressure.factor(jp.revati.skyabyss.story.ch5.AbyssPressure.WEIGHT_FACTORS, p), AbyssGear.SET4_WEIGHT),
                "the set should lighten the climbing weight to x" + AbyssGear.SET4_WEIGHT);
        check(f, near(jp.revati.skyabyss.story.ch5.AbyssPressure.factor(jp.revati.skyabyss.story.ch5.AbyssPressure.VOICE_FACTORS, p), AbyssGear.SET4_VOICE),
                "the set should slow the darkness build-up to x" + AbyssGear.SET4_VOICE);
        // 暗い所 (世界の底の近く、明るさ 0) の傷 ×0.85。灯りの中では掛からない
        boolean dark = AbyssGear.inDark(p);
        double taken = AbyssGearEvents.takenFactor(p, level.damageSources().generic());
        check(f, !dark || near(taken, AbyssGear.SET4_DARK_TAKEN), "dark damage factor " + taken + " (dark " + dark + ")");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 0));
        check(f, near(AbyssGearEvents.takenFactor(p, level.damageSources().generic()), 1.0), "the dark reduction must not stack with resistance");
        // 錘の深さ
        check(f, near(AbyssGear.plumbBonus(true, 480, 0), 0.12) && near(AbyssGear.plumbBonus(true, 100, 0), 0.02) && near(AbyssGear.plumbBonus(true, 2000, 0), 0.12),
                "plumb bonus in the abyss");
        check(f, near(AbyssGear.plumbBonus(false, 0, 64), 0) && near(AbyssGear.plumbBonus(false, 0, 0), 0.04) && near(AbyssGear.plumbBonus(false, 0, -64), 0.08)
                && near(AbyssGear.plumbBonus(false, 0, 200), 0), "plumb bonus on the surface");
        // 短剣の拍: 4 度目ごと (+5 から 3 度目ごと)。別の相手や 1.5 秒の空きで数え直す
        int n = 0;
        int beats = 0;
        for (int i = 0; i < 8; i++) {
            n = PulseDaggerItem.count(n, i > 0, 10, AbyssGear.DAGGER_EVERY);
            if (n == 0) {
                beats++;
            }
        }
        check(f, beats == 2, "dagger beats over 8 hits: " + beats + " (want 2)");
        check(f, PulseDaggerItem.count(2, false, 10, 4) == 1 && PulseDaggerItem.count(2, true, 40, 4) == 1 && PulseDaggerItem.count(2, true, 10, 3) == 0,
                "dagger chain resets on a new target or a long gap");
        // 必要 Lv に届かない人は鎧を着られない
        FakePlayer low = player(level, "abyss_gear_low", 30);
        check(f, !new ItemStack(AbyssGearSetup.HELMET.get()).canEquip(EquipmentSlot.HEAD, low), "a Lv 30 player could wear abyss armor");
    }

    // ---------------------------------------------------------------- 報酬・炉
    private static void loot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(4242);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        List<ItemStack> first = AbyssGearLoot.rollLord(r, "salt", random);
        check(f, first.stream().anyMatch(s -> s.getItem() == AbyssGearSetup.CHESTPLATE.get()) && r.owned.contains(AbyssGear.CHESTPLATE),
                "the salt lord should give the cuirass the first time");
        check(f, first.stream().anyMatch(s -> s.getItem() == AbyssGearSetup.SHARD.get()), "the lord should give anchor iron");
        List<ItemStack> again = AbyssGearLoot.rollLord(r, "salt", random);
        check(f, again.stream().noneMatch(s -> s.getItem() == AbyssGearSetup.CHESTPLATE.get())
                && again.stream().filter(s -> s.getItem() == AbyssGearSetup.SHARD.get()).mapToInt(ItemStack::getCount).sum() >= 7,
                "an owned piece should turn into extra anchor iron");
        for (String layer : List.of("bone", "salt", "storm", "ash")) {
            check(f, AbyssGear.ARMOR.contains(AbyssGear.lordPiece(layer)), layer + " lord piece");
        }
        check(f, List.of("bone", "salt", "storm", "ash").stream().map(AbyssGear::lordPiece).distinct().count() == 4, "each lord should give a different piece");
        // 番人: 初回は心核、交換が開く
        TitanMemoryData.Record w = new TitanMemoryData.Record();
        List<ItemStack> stacks = new ArrayList<>();
        AbyssGearLoot.Roll roll = AbyssGearLoot.rollWarden(w, 0, random, TitanGearSet::registered, stacks);
        check(f, roll.core() && w.firstCleared && w.clears == 1, "the first warden clear should give a core and open the exchange");
        check(f, stacks.stream().anyMatch(s -> s.getItem() == AbyssGearSetup.RESONANCE_SHARD.get()), "the warden should give abyss resonance fragments");
        check(f, GearExchange.state(AbyssGear.SET, w) != jp.revati.skyabyss.forge.ForgeRules.ExchangeState.NO_CLEAR, "exchange should open after the warden");
        // 天井: 図鑑 10 品がそろうまでの平均 (4000 人)
        int total = 0;
        int worst = 0;
        for (int i = 0; i < 4000; i++) {
            TitanMemoryData.Record s = new TitanMemoryData.Record();
            int k = 0;
            while (AbyssGear.SET.collected(s) < 10 && k < 200) {
                AbyssGearLoot.rollWarden(s, 0, random, TitanGearSet::registered, new ArrayList<>());
                k++;
            }
            total += k;
            worst = Math.max(worst, k);
        }
        double avg = total / 4000.0;
        notes.add("warden clears to fill the abyss codex: avg " + fmt(avg) + ", worst " + worst);
        check(f, avg < 20 && worst < 200, "the abyss codex takes too long: avg " + fmt(avg) + " worst " + worst);
        // 深みの箱: 王の褒美は錨鉄を必ず含む
        List<ItemStack> chest = DepthLoot.roll(10, 3, new Random(7));
        check(f, chest.stream().anyMatch(s -> s.getItem() == AbyssGearSetup.SHARD.get()), "a king chest should hold anchor iron");
        int gear = 0;
        Random cr = new Random(11);
        for (int i = 0; i < 2000; i++) {
            List<ItemStack> out = new ArrayList<>();
            AbyssGearLoot.chest(out, 5, 3, cr);
            gear += (int) out.stream().filter(s -> s.getItem() instanceof ModGear).count();
        }
        check(f, gear > 200 && gear < 400, "king chests should give gear about 15% of the time: " + gear + " / 2000");
        // 炉: 錨鉄で鍛える
        ItemStack sword = new ItemStack(item(AbyssGear.LAMP_SWORD));
        check(f, ForgeRules.shardItem(sword) == AbyssGearSetup.SHARD.get(), "the lamp sword should be enhanced with anchor iron");
        FakePlayer p = player(server.overworld(), "abyss_gear_forge", 44);
        p.getInventory().add(new ItemStack(AbyssGearSetup.SHARD.get(), 10));
        p.getInventory().add(new ItemStack(ModItems.RESONANCE_CRYSTAL.get(), 8));
        check(f, ForgeRules.enhance(p, sword) && ModGear.enhance(sword) == 1, "the lamp sword was not enhanced with anchor iron");
    }

    // ---------------------------------------------------------------- レシピと荷運び
    private static void recipes(MinecraftServer server, List<String> f) {
        for (String r : List.of("abyss_lantern_charm", "abyss_pulse_listener")) {
            check(f, server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, r)).isPresent(), "recipe " + r + " does not resolve");
        }
        ItemStack relic = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_relic_bone_2")));
        check(f, relic.hasCraftingRemainingItem() && relic.getCraftingRemainingItem().getItem() == relic.getItem(), "the relic should stay when traced at a crafting table");
        JsonObject porter = json("/data/skyabyss/dialogue/ch5/attendant_abyss_porter.json", f);
        if (porter != null) {
            String text = porter.toString();
            for (String id : List.of(AbyssGear.SHARD, AbyssGear.LAMP_SWORD, AbyssGear.LISTENER)) {
                check(f, text.contains("\"" + id + "\"") && TitanGearSet.registered(id), "the porter should trade " + id);
            }
        }
    }

    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = AbyssGearSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("unreadable " + path + " " + e);
            return null;
        }
    }

    // ---------------------------------------------------------------- 絵と文
    private static boolean exists(String path) {
        try (InputStream in = AbyssGearSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] pngSize(String path) {
        try (InputStream in = AbyssGearSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return new int[] {0, 0};
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            return img == null ? new int[] {0, 0} : new int[] {img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return new int[] {0, 0};
        }
    }

    private static void assets(List<String> f) {
        String a = "/assets/skyabyss/";
        List<String> all = new ArrayList<>(AbyssGear.SET.collection());
        all.addAll(List.of(AbyssGear.SHARD, AbyssGear.CORE, AbyssGear.RESONANCE_SHARD));
        Map<String, String> ja = lang("ja_jp", f);
        Map<String, String> en = lang("en_us", f);
        for (String id : all) {
            String path = id.substring(id.indexOf(':') + 1);
            check(f, exists(a + "models/item/" + path + ".json"), "item model missing: " + path);
            check(f, java.util.Arrays.equals(pngSize(a + "textures/item/" + path + ".png"), new int[] {32, 32}), "icon is not 32x32: " + path);
            check(f, ja.containsKey("item.skyabyss." + path) && en.containsKey("item.skyabyss." + path), "name missing (ja/en): " + path);
        }
        for (String w : List.of("abyss_lamp_sword:rare", "abyss_sinking_anchor:legend", "abyss_pulse_dagger:legend",
                "abyss_pulse_listener:rare", "abyss_lantern_charm:rare", "abyss_surveyor_plumb:legend")) {
            String[] p = w.split(":");
            check(f, exists(a + "lowpoly/items/" + p[0] + "/" + p[1] + ".json"), "low-poly mesh missing: " + w);
        }
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            check(f, exists(a + "lowpoly/armor/abyss_diver/" + piece + ".json") && exists(a + "lowpoly/armor/abyss_diver/" + piece + "_rare.json"),
                    "armor mesh missing: " + piece);
            check(f, exists(a + "textures/lowpoly/armor/abyss_diver/" + piece + "_rare.png"), "armor atlas missing: " + piece);
        }
    }

    private static Map<String, String> lang(String locale, List<String> f) {
        Map<String, String> map = new java.util.HashMap<>();
        JsonObject o = json("/assets/skyabyss_abyss_gear/lang/" + locale + ".json", f);
        if (o != null) {
            o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        }
        return map;
    }
}
