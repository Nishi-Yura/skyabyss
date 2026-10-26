package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.tide.TideOcean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.util.FakePlayer;

// 二章の備えの品 (guide/Ch2Gear。21 の 1.2・4.1) の自己テスト。段: "ch2 gear" (TravelSelfTest.steps の二つ目)。
//  - 潮渡りの旅装 4 部位の値 (防御 18・強靭 9・水中移動効率 0.1 × 4・アンコモン・鍛えない・必要 Lv 13) と GearCurve.TRAVEL[1]、備えの身の灯
//  - 塩の砥石 (+1.8・20 分・岩の砥石と重ねない・備えの刃は大きい方だけ・水の中の +1)、塩漬け (水中呼吸 60 秒)、粉薬 (3❤・浮遊と鈍足を治す)、糧の点
//  - 手段の名前が章 (巨神) ごとに分かれる
//  - レシピ 8 本 (旅装の素材の合計は 小真珠 10・塩結晶 12・岩鯨の鱗片 4・革 3)・鍵・覚書 (二章) が解く・旅嚢の素材
//  - 砂時計の潮の計算 (一歩ずつ数えた答えと一致)・使うと帯に一度・30 秒の待ち・消えない
//  - 鼓動守 (潮殻) の編み方の表と、編み直し (1 日 1 つ)
//  - 一式で合図 ch2_travel_gear_made
public final class Ch2GearSelfTest {
    private Ch2GearSelfTest() {
    }

    public static SelfTestRunner.Step step() {
        return SelfTestRunner.Step.once("ch2 gear", Ch2GearSelfTest::run);
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ch2gear_a"));
        Growth.get(a).level = 30;
        try {
            armor(f, a);
            curve(f);
            consumables(f, level, a);
            remedies(f);
            texts(f);
            recipes(f, server, level, a);
            hourglass(f, level, a);
            ward(f, a);
            gearSignal(f, a);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch2 gear crashed", e);
            f.add("crashed: " + e);
        } finally {
            Quests.forgetAll(a);
            Growth.get(a).discoveries.clear();
            a.getInventory().clearContent();
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                a.setItemSlot(s, ItemStack.EMPTY);
            }
            a.removeAllEffects();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch2 gear PASSED (tide wader values and water efficiency, salt whetstone, pickle and powder, chapter remedies, 8 recipes and lock, hourglass tide math, tide shell ward weave and refill, gear-made signal)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch2 gear FAILED: {}", m));
        }
    }

    private static Item[] pieces() {
        return new Item[] {Ch2Gear.HELMET.get(), Ch2Gear.CHESTPLATE.get(), Ch2Gear.LEGGINGS.get(), Ch2Gear.BOOTS.get()};
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ---------------------------------------------------------------- 旅装
    private static void armor(List<String> f, FakePlayer a) {
        Item[] pieces = pieces();
        int[] want = {3, 7, 5, 3};
        double defense = 0;
        double toughness = 0;
        for (int i = 0; i < 4; i++) {
            if (!(pieces[i] instanceof ArmorItem armor) || !(pieces[i] instanceof ModGear gear)) {
                f.add("piece " + i + " is not a ModGear armor item");
                continue;
            }
            check(f, armor.getDefense() == want[i], "piece " + i + " defense " + armor.getDefense() + " (want " + want[i] + ")");
            defense += armor.getDefense();
            toughness += armor.getToughness();
            check(f, gear.tier() == GearTier.UNCOMMON && !gear.enhanceable() && gear.dungeon().equals("tide_wader"), "piece " + i + " must be uncommon, not enhanceable, from tide_wader");
            check(f, GearLevel.required(pieces[i]) == 13, "piece " + i + " required level " + GearLevel.required(pieces[i]) + " (want 13)");
            ItemStack stack = new ItemStack(pieces[i]);
            check(f, !AffixRules.eligible(stack) && !ForgeRules.canEnhance(stack) && !ForgeRules.accepts(stack), "piece " + i + " takes affixes, enhancement or the forge");
            check(f, stack.getMaxDamage() > 0 && armor.getEquipmentSlot() == SLOTS[i], "piece " + i + " has no durability or the wrong slot");
            double[] water = {0};
            stack.forEachModifier(SLOTS[i], (attr, mod) -> {
                if (attr.equals(Attributes.WATER_MOVEMENT_EFFICIENCY)) {
                    water[0] += mod.amount();
                }
            });
            check(f, Math.abs(water[0] - Ch2Gear.WATER_EFFICIENCY) < 1e-6, "piece " + i + " water movement efficiency " + water[0] + " (want 0.1)");
            check(f, armor.getMaterial().value().repairIngredient().get().test(new ItemStack(Ch2Gear.SALT_CRYSTAL.get())), "piece " + i + " is not repaired with salt crystals");
        }
        check(f, defense == 18 && Math.abs(toughness - 9) < 1e-6, "tide wader set " + defense + " / " + toughness + " (want 18 / 9)");
        // 着ると水中移動効率が一式で 0.4 (部位の修飾子が効く)
        double before = a.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
        for (int i = 0; i < 4; i++) {
            a.setItemSlot(SLOTS[i], new ItemStack(pieces[i]));
        }
        tickEquipment(a);
        double worn = a.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
        check(f, Math.abs(worn - before - 0.4) < 1e-6, "wearing the tide wader set gives water efficiency +" + (worn - before) + " (want 0.4)");
        double armorValue = a.getAttributeValue(Attributes.ARMOR);
        check(f, armorValue >= 18 - 1e-6, "wearing the set gives armor " + armorValue);
        for (EquipmentSlot s : SLOTS) {
            a.setItemSlot(s, ItemStack.EMPTY);
        }
        tickEquipment(a);
    }

    // 装備の変化を属性に反映する (LivingEntity.tick の装備の比較だけを呼ぶ)
    private static void tickEquipment(FakePlayer a) {
        try {
            var m = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
            m.setAccessible(true);
            m.invoke(a);
        } catch (ReflectiveOperationException e) {
            a.tick();
        }
    }

    // ---------------------------------------------------------------- GearCurve との照合 (二章の段)
    private static void curve(List<String> f) {
        GearCurve.Travel t = GearCurve.TRAVEL.get(1);
        check(f, t.id().equals("tide_wader") && t.chapter() == 2 && t.stage().equals("tide_shell") && t.level() == 13 && t.armor() == 18 && t.toughness() == 9
                && t.whetstone() == 1.8, "the chapter-2 travel row is " + t);
        check(f, Ch2Gear.ROW == t && Math.abs(Ch2Gear.WHETSTONE_ATTACK - 1.8) < 1e-9, "Ch2Gear does not read its numbers from the table");
        GearCurve.Stage tide = Readiness.stage("tide_shell");
        if (tide == null) {
            f.add("no tide_shell stage");
            return;
        }
        double goal = Readiness.goalHearts(tide.id());
        double atGoal = Readiness.body(t.armor(), t.toughness(), goal * 2, tide, goal);
        double oneMore = Readiness.body(t.armor(), t.toughness(), (goal + 1) * 2, tide, goal);
        double titanSet = Readiness.body(tide.armor(), tide.toughness(), goal * 2, tide, goal);
        check(f, Readiness.colour(atGoal) != Readiness.Colour.RED, "the tide wader set at the goal health is red (" + atGoal + ")");
        check(f, Readiness.colour(oneMore) == Readiness.Colour.GREEN, "the tide wader set with one more heart is not green (" + oneMore + ")");
        check(f, atGoal < titanSet, "the tide wader set should stay below the Tide Shell set (" + atGoal + " vs " + titanSet + ")");
    }

    // ---------------------------------------------------------------- 砥石・塩漬け・粉薬
    private static void consumables(List<String> f, ServerLevel level, FakePlayer a) {
        // 塩漬け: 水中呼吸 60 秒 + 満腹。糧の点 2
        ItemStack pickle = new ItemStack(Ch2Gear.TIDE_PICKLE.get());
        FoodProperties food = pickle.get(DataComponents.FOOD);
        check(f, food != null && food.nutrition() >= 6 && food.effects().size() == 1
                && food.effects().get(0).effect().getEffect().is(MobEffects.WATER_BREATHING) && food.effects().get(0).effect().getDuration() == 1200,
                "the tide pickle must be a filling food with water breathing for 60 s");
        check(f, Math.abs(Readiness.pointsPerItem(pickle) - Ch2Gear.PICKLE_POINTS) < 1e-6, "the pickle counts " + Readiness.pointsPerItem(pickle) + " provision points (want 2)");
        // 粉薬: 3❤ (6 点)、浮遊と鈍足を治す
        ItemStack powder = new ItemStack(Ch2Gear.PEARL_POWDER.get());
        check(f, Math.abs(Readiness.pointsPerItem(powder) - 6) < 1e-6, "the powder counts " + Readiness.pointsPerItem(powder) + " provision points (want 6)");
        a.setHealth(4);
        a.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 600, 0));
        a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
        ItemStack eaten = powder.copy();
        eaten.getItem().finishUsingItem(eaten, level, a);
        check(f, Math.abs(a.getHealth() - 10) < 1e-3, "the powder healed to " + a.getHealth() + " (want 4 + 6)");
        check(f, !a.hasEffect(MobEffects.LEVITATION) && !a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the powder did not cure levitation and slowness");
        a.setHealth(a.getMaxHealth());
        a.removeAllEffects();

        // 塩の砥石: 塩の刃 (攻撃 +1.8。20 分)。一つ減る。合図
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        double base = Readiness.weaponDps(sword, 0);
        double salted = Readiness.weaponDps(sword, Ch2Gear.WHETSTONE_ATTACK);
        double rocked = Readiness.weaponDps(sword, TravelGear.WHETSTONE_ATTACK);
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        check(f, Math.abs(Readiness.bestDps(a) - base) < 1e-6, "the blade lamp counted a whetstone nobody has");
        a.getInventory().add(new ItemStack(Ch2Gear.SALT_WHETSTONE.get(), 2));
        check(f, Math.abs(Readiness.bestDps(a) - salted) < 1e-6, "a carried salt whetstone was not counted (" + Readiness.bestDps(a) + " vs " + salted + ")");
        a.getInventory().add(new ItemStack(TravelGear.ROCK_WHETSTONE.get(), 2));
        check(f, Math.abs(Readiness.bestDps(a) - salted) < 1e-6, "rock and salt whetstones were stacked by the blade lamp (" + Readiness.bestDps(a) + ", want " + salted + ")");
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        a.addEffect(new MobEffectInstance(TravelGear.WHETTED, 1000, 0));
        check(f, Math.abs(Readiness.bestDps(a) - rocked) < 1e-6, "the rock honed blade alone was not counted");
        ItemStack stone = new ItemStack(Ch2Gear.SALT_WHETSTONE.get(), 2);
        a.setItemInHand(InteractionHand.MAIN_HAND, stone);
        double attackBase = a.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.5;
        stone.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, stone.getCount() == 1, "using a salt whetstone did not use one up (" + stone.getCount() + ")");
        MobEffectInstance e = a.getEffect(Ch2Gear.SALT_WHETTED);
        check(f, e != null && e.getDuration() >= Ch2Gear.WHETSTONE_TICKS - 5 && Ch2Gear.WHETSTONE_TICKS == 24000, "the salt edge is not 20 minutes");
        check(f, !a.hasEffect(TravelGear.WHETTED), "the rock honed blade stayed under the salt edge (they must not stack)");
        check(f, Math.abs(a.getAttributeValue(Attributes.ATTACK_DAMAGE) - attackBase - 1.8) < 1e-6,
                "the salt edge adds " + (a.getAttributeValue(Attributes.ATTACK_DAMAGE) - attackBase) + " attack (want 1.8)");
        check(f, a.getCooldowns().isOnCooldown(Ch2Gear.SALT_WHETSTONE.get()), "the salt whetstone has no short cooldown");
        // 岩の砥石で研ぎ直すと塩の刃は消える
        ItemStack rock = new ItemStack(TravelGear.ROCK_WHETSTONE.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, rock);
        rock.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, a.hasEffect(TravelGear.WHETTED) && !a.hasEffect(Ch2Gear.SALT_WHETTED), "the rock whetstone did not replace the salt edge");
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.removeAllEffects();
        // 水の中でも切れる: 塩の刃で水の中の近接の一撃だけ +1
        check(f, Ch2Gear.wetBonus(true, true, true) == 1.0f && Ch2Gear.wetBonus(true, true, false) == 0 && Ch2Gear.wetBonus(false, true, true) == 0
                && Ch2Gear.wetBonus(true, false, true) == 0, "the wet bonus rule is wrong");
    }

    // ---------------------------------------------------------------- 手段の名前は章ごと
    private static void remedies(List<String> f) {
        check(f, has(Readiness.remedies(Readiness.Lamp.BLADE, "tide_shell"), "salt_whetstone") && !has(Readiness.remedies(Readiness.Lamp.BLADE, "tide_shell"), "whetstone"),
                "the tide shell blade remedy should name the salt whetstone only");
        check(f, has(Readiness.remedies(Readiness.Lamp.BLADE, "stone_whale"), "whetstone") && !has(Readiness.remedies(Readiness.Lamp.BLADE, "stone_whale"), "salt_whetstone"),
                "the stone whale blade remedy should name the rock whetstone only");
        check(f, has(Readiness.remedies(Readiness.Lamp.BODY, "tide_shell"), "tide_wader") && has(Readiness.remedies(Readiness.Lamp.PROVISIONS, "tide_shell"), "tide_pickle")
                && has(Readiness.remedies(Readiness.Lamp.PROVISIONS, "tide_shell"), "pearl_powder") && has(Readiness.remedies(Readiness.Lamp.PROVISIONS, "tide_shell"), "ward"),
                "the tide shell remedies are incomplete");
        check(f, has(Readiness.remedies(Readiness.Lamp.BLADE), "salt_whetstone") && has(Readiness.remedies(Readiness.Lamp.BLADE), "whetstone"), "remedies(lamp) should still list every chapter");
    }

    private static boolean has(List<Component> list, String name) {
        for (Component c : list) {
            if (c.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith("." + name)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 文 (日英)
    private static void texts(List<String> f) {
        List<String> keys = new ArrayList<>(List.of("effect.skyabyss.salt_whetted", "message.skyabyss.salt_whetstone.used", "item.skyabyss.tide_wader_armor.desc",
                "gear.skyabyss.origin.tide_wader", "message.skyabyss.tide_hourglass.low", "message.skyabyss.tide_hourglass.high", "message.skyabyss.tide_hourglass.still",
                "message.skyabyss.readiness.remedy.name.tide_wader", "message.skyabyss.readiness.remedy.name.salt_whetstone",
                "message.skyabyss.readiness.remedy.name.tide_pickle", "message.skyabyss.readiness.remedy.name.pearl_powder"));
        for (String id : List.of("small_pearl", "salt_crystal", "pearl_ward_thread", "shell_chip", "salt_whetstone", "tide_pickle", "pearl_powder", "tide_hourglass")) {
            keys.add("item.skyabyss." + id);
            keys.add("item.skyabyss." + id + ".desc");
        }
        for (String p : List.of("helmet", "chestplate", "leggings", "boots")) {
            keys.add("item.skyabyss.tide_wader_" + p);
            keys.add("item.skyabyss.tide_wader_" + p + ".story");
        }
        for (int i = 1; i <= 7; i++) {
            keys.add(WayfarerNotes.lineKey(2, i));
        }
        for (String lang : List.of("ja_jp", "en_us")) {
            Map<String, String> t = lang("/assets/skyabyss_ch2gear/lang/" + lang + ".json");
            for (String k : keys) {
                check(f, t.containsKey(k), lang + " is missing " + k);
            }
        }
    }

    private static Map<String, String> lang(String path) {
        Map<String, String> out = new HashMap<>();
        try (var in = Ch2GearSelfTest.class.getResourceAsStream(path)) {
            if (in != null) {
                com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (java.io.IOException | RuntimeException e) {
            // 読めなければ空
        }
        return out;
    }

    // ---------------------------------------------------------------- レシピ・鍵
    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("skyabyss", name);
    }

    private static void recipes(List<String> f, MinecraftServer server, ServerLevel level, FakePlayer a) {
        Item shard = BuiltInRegistries.ITEM.get(id("stone_whale_shard"));
        madeSet(f, List.of("tide_wader_helmet", "tide_wader_chestplate", "tide_wader_leggings", "tide_wader_boots"), "smithy", 2,
                Map.of(Ch2Gear.SMALL_PEARL.get(), 10, Ch2Gear.SALT_CRYSTAL.get(), 12, shard, 4), "tide wader set");
        check(f, Ch2Gear.RECIPES.size() == 8 && WayfarerNotes.recipes(2).size() == 8, "the chapter-2 notes should hold 8 items, got " + WayfarerNotes.recipes(2));
        madeChapter(f, 2);
        // 二章の素材は旅嚢の品ではない (旅嚢の画面の枠が埋まっている。足すなら画面の並びから直す)。持ち物のまま
        for (Item mat : List.of(Ch2Gear.SMALL_PEARL.get(), Ch2Gear.SALT_CRYSTAL.get(), Ch2Gear.PEARL_WARD_THREAD.get(), Ch2Gear.SHELL_CHIP.get())) {
            check(f, jp.revati.skyabyss.pouch.PouchEntries.entry(mat) == null, mat + " became a pouch item (check the pouch layout first)");
        }
        noCrafting(f, server, level, a);
    }

    // 誂え (作業台はない。2026-10-26)。部位がその店のその章の誂えで、素材の合計が want
    private static void madeSet(List<String> f, List<String> pieces, String shop, int chapter, Map<Item, Integer> want, String what) {
        Map<Item, Integer> total = new HashMap<>();
        for (String piece : pieces) {
            jp.revati.skyabyss.facility.MadeToOrder.Entry e = jp.revati.skyabyss.facility.MadeToOrder.find(piece);
            check(f, e != null && e.good().made() && e.shop().id().equals(shop) && e.good().chapter() == chapter,
                    piece + " is not made to order at the " + shop + " from chapter " + chapter);
            if (e != null) {
                e.materials().forEach((k, v) -> total.merge(k, v, Integer::sum));
            }
        }
        check(f, total.equals(want), "the " + what + " costs " + total + " (want " + want + ")");
    }

    // 覚書の章の品は、みなその章から誂えられる (並ぶ品も可)
    private static void madeChapter(List<String> f, int chapter) {
        for (ResourceLocation r : WayfarerNotes.recipes(chapter)) {
            jp.revati.skyabyss.facility.MadeToOrder.Entry e = jp.revati.skyabyss.facility.MadeToOrder.find(r.toString());
            if (r.getPath().equals("hare_quill_arrow")) {
                continue;
            }
            check(f, e != null && (!e.good().made() || e.good().chapter() == chapter), r + " is not made to order from chapter " + chapter);
        }
    }

    // 作業台では何も作れない (レシピを一つも読まない)
    private static void noCrafting(List<String> f, MinecraftServer server, ServerLevel level, FakePlayer a) {
        check(f, server.getRecipeManager().getRecipes().isEmpty(), "recipes are still loaded (" + server.getRecipeManager().getRecipes().size() + ")");
        CraftingMenu menu = new CraftingMenu(7, a.getInventory(), ContainerLevelAccess.create(level, BlockPos.ZERO));
        a.containerMenu = menu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
        check(f, !menu.getSlot(0).hasItem(), "the crafting grid still makes something from planks");
        for (int i = 1; i <= 9; i++) {
            menu.getSlot(i).set(ItemStack.EMPTY);
        }
        a.containerMenu = a.inventoryMenu;
    }

    // ---------------------------------------------------------------- 砂時計
    private static void hourglass(List<String> f, ServerLevel level, FakePlayer a) {
        // 一日の中の何点かで、一歩ずつ数えた答えと比べる
        long[] samples = {0, 1, 5000, 9800, 10999, 11000, 11001, 12000, 14599, 14600, 14601, 18000, 21799, 21800, 23999, 24000 * 3 + 12345};
        long windowLength = -1;
        for (long t : samples) {
            TideHourglassItem.Reading r = TideHourglassItem.read(t);
            long toLow = 0;
            while (!TideOcean.isLowTide(t + toLow)) {
                toLow++;
            }
            long toHigh = toLow;
            while (TideOcean.isLowTide(t + toHigh)) {
                toHigh++;
            }
            check(f, r.low() == TideOcean.isLowTide(t) && r.toLow() == toLow && r.toHigh() == toHigh,
                    "hourglass at " + t + " read " + r + " (want low " + TideOcean.isLowTide(t) + ", " + toLow + ", " + toHigh + ")");
            check(f, r.toLow() == TideOcean.ticksUntilLowTide(t), "hourglass at " + t + " disagrees with ticksUntilLowTide");
            if (!r.low()) {
                long len = r.toHigh() - r.toLow();
                check(f, windowLength < 0 || windowLength == len, "the low-tide window length changed: " + len + " vs " + windowLength);
                windowLength = len;
            } else {
                check(f, r.toLow() == 0 && r.toHigh() > 0, "inside low tide the hourglass should count to the high tide");
            }
            Component msg = TideHourglassItem.message(r);
            check(f, msg.getContents() instanceof TranslatableContents tc && tc.getKey().equals(r.low() ? "message.skyabyss.tide_hourglass.low" : "message.skyabyss.tide_hourglass.high"),
                    "hourglass message at " + t);
        }
        check(f, windowLength > 0, "no low-tide window was measured");
        TideHourglassItem.Reading r = TideHourglassItem.read(14600);
        check(f, r.secondsToLow() == (int) Math.ceil(r.toLow() / 20.0), "seconds are not rounded up");
        // 使う: 消えない・30 秒の待ち
        ItemStack glass = new ItemStack(Ch2Gear.TIDE_HOURGLASS.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, glass);
        glass.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, glass.getCount() == 1 && a.getCooldowns().isOnCooldown(Ch2Gear.TIDE_HOURGLASS.get()), "the hourglass was consumed or has no cooldown");
        check(f, Ch2Gear.HOURGLASS_COOLDOWN == 600, "the hourglass cooldown is not 30 s");
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 鼓動守 (潮殻)
    private static void ward(List<String> f, FakePlayer a) {
        HeartbeatWard.Weave w = HeartbeatWard.weave(2);
        check(f, w != null && w.titan().equals("tide_shell") && w.thread().equals("skyabyss:pearl_ward_thread") && w.refill().equals("skyabyss:salt_crystal")
                && w.refillCount() == 8 && w.wovenFlag().equals("ch2t_woven"), "the chapter-2 weave is " + w);
        for (HeartbeatWard.Weave each : HeartbeatWard.WEAVES) {
            check(f, BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(each.thread())) && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(each.refill()))
                    && GuideSetup.wardOf(each.titan()) != null, "weave " + each.chapter() + " names an unknown item");
        }
        a.getInventory().clearContent();
        Growth.get(a).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
        check(f, HeartbeatWard.remakeable(a, "tide_shell"), "someone without a tide shell ward cannot have one woven");
        check(f, HeartbeatWard.remake(a, "tide_shell") && a.getInventory().countItem(GuideSetup.HEARTBEAT_WARD_TIDE_SHELL.get()) == 1, "the tide shell ward was not woven");
        check(f, !HeartbeatWard.remakeable(a, "tide_shell"), "a second tide shell ward was offered while one is carried");
        a.getInventory().clearContent();
        check(f, !HeartbeatWard.remakeable(a, "tide_shell"), "a second tide shell ward was offered on the same day");
        check(f, HeartbeatWard.remakeable(a, "stone_whale"), "the daily limit should be per titan");
        Growth.get(a).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
    }

    // ---------------------------------------------------------------- 一式の合図
    private static void gearSignal(List<String> f, FakePlayer a) {
        a.getInventory().clearContent();
        a.getInventory().add(new ItemStack(Ch2Gear.HELMET.get()));
        a.getInventory().add(new ItemStack(Ch2Gear.CHESTPLATE.get()));
        check(f, !Ch2Gear.hasFullSet(a) && !Ch2Gear.signalIfComplete(a), "half a tide wader set counted as the full set");
        a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Ch2Gear.LEGGINGS.get()));
        a.getInventory().add(new ItemStack(Ch2Gear.BOOTS.get()));
        check(f, Ch2Gear.hasFullSet(a) && Ch2Gear.signalIfComplete(a), "the full tide wader set did not send " + Ch2Gear.SIGNAL_GEAR_MADE);
        a.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }
}
