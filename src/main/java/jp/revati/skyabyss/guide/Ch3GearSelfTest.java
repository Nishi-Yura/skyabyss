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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 三章の備えの品 (guide/Ch3Gear。21 の 1.2・5.1) の自己テスト。段: "ch3 gear" (TravelSelfTest.steps の三つ目)。
//  - 峰越えの旅装 4 部位の値 (防御 19・強靭 11・アンコモン・鍛えない・必要 Lv 19・修理は銅線) と GearCurve.TRAVEL[2]、備えの身の灯
//  - 感電の傷: 雷と雷を使う敵の傷だけ。旅装の部位 × 6.25%・避雷の油で半分 (着て雷を受けると実際に減る)
//  - 雷の砥石 (+2.2・20 分・岩/塩の砥石と重ねない・備えの刃は大きい方だけ)、団子 (吸収 II 60 秒・落下速度低下 20 秒)、油 (避雷 90 秒・糧の点 2)
//  - 手段の名前が雷翼のときだけ三章の品、レシピ 9 本 (旅装の素材の合計は 焦げ羽 12・帯電綿 6・銅線 6・潮殻の殻片 4)・鍵・覚書 (三章) が解く
//  - 風読みの小旗 (消えない・30 秒の待ち・光の筋が立つ・方位の文)、鼓動守 (雷翼) の編み方の表と編み直し、一式の合図
public final class Ch3GearSelfTest {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Ch3GearSelfTest() {
    }

    public static SelfTestRunner.Step step() {
        return SelfTestRunner.Step.once("ch3 gear", Ch3GearSelfTest::run);
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ch3gear_a")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        Growth.get(a).level = 30;
        // 新しい人は湧いた直後の 3 秒は傷を受けないので、それを消す (雷の傷を比べるため)
        try {
            java.lang.reflect.Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(a, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        try {
            armor(f, a);
            curve(f);
            shock(f, level, a);
            consumables(f, level, a);
            remedies(f);
            texts(f);
            recipes(f, server, level, a);
            streamer(f, level, a);
            ward(f, a);
            gearSignal(f, a);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch3 gear crashed", e);
            f.add("crashed: " + e);
        } finally {
            Quests.forgetAll(a);
            Growth.get(a).discoveries.clear();
            a.getInventory().clearContent();
            for (EquipmentSlot s : SLOTS) {
                a.setItemSlot(s, ItemStack.EMPTY);
            }
            a.removeAllEffects();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch3 gear PASSED (peak crossing values, shock damage -6.25%/piece and lightning oil -50%, thunder whetstone, dumpling and oil, "
                    + "chapter remedies, 9 recipes and lock, wind streamer, thunder wing ward weave and refill, gear-made signal)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch3 gear FAILED: {}", m));
        }
    }

    private static Item[] pieces() {
        return new Item[] {Ch3Gear.HELMET.get(), Ch3Gear.CHESTPLATE.get(), Ch3Gear.LEGGINGS.get(), Ch3Gear.BOOTS.get()};
    }

    private static void tickEquipment(FakePlayer a) {
        try {
            var m = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
            m.setAccessible(true);
            m.invoke(a);
        } catch (ReflectiveOperationException e) {
            a.tick();
        }
    }

    // ---------------------------------------------------------------- 旅装
    private static void armor(List<String> f, FakePlayer a) {
        Item[] pieces = pieces();
        int[] want = {3, 7, 6, 3};
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
            check(f, gear.tier() == GearTier.UNCOMMON && !gear.enhanceable() && gear.dungeon().equals("peak_crossing"), "piece " + i + " must be uncommon, not enhanceable, from peak_crossing");
            check(f, GearLevel.required(pieces[i]) == 19, "piece " + i + " required level " + GearLevel.required(pieces[i]) + " (want 19)");
            ItemStack stack = new ItemStack(pieces[i]);
            check(f, !AffixRules.eligible(stack) && !ForgeRules.canEnhance(stack) && !ForgeRules.accepts(stack), "piece " + i + " takes affixes, enhancement or the forge");
            check(f, stack.getMaxDamage() > 0 && armor.getEquipmentSlot() == SLOTS[i], "piece " + i + " has no durability or the wrong slot");
            check(f, armor.getMaterial().value().repairIngredient().get().test(new ItemStack(Ch3Gear.COPPER_WIRE.get())), "piece " + i + " is not repaired with copper wire");
        }
        check(f, defense == 19 && Math.abs(toughness - 11) < 1e-6, "peak crossing set " + defense + " / " + toughness + " (want 19 / 11)");
        for (int i = 0; i < 4; i++) {
            a.setItemSlot(SLOTS[i], new ItemStack(pieces[i]));
        }
        tickEquipment(a);
        check(f, a.getAttributeValue(Attributes.ARMOR) >= 19 - 1e-6 && Ch3Gear.worn(a) == 4, "wearing the set gives armor " + a.getAttributeValue(Attributes.ARMOR)
                + ", pieces " + Ch3Gear.worn(a));
        for (EquipmentSlot s : SLOTS) {
            a.setItemSlot(s, ItemStack.EMPTY);
        }
        tickEquipment(a);
    }

    private static void curve(List<String> f) {
        GearCurve.Travel t = GearCurve.TRAVEL.get(2);
        check(f, t.id().equals("peak_crossing") && t.chapter() == 3 && t.stage().equals("thunder_wing") && t.level() == 19 && t.armor() == 19 && t.toughness() == 11
                && t.whetstone() == 2.2, "the chapter-3 travel row is " + t);
        check(f, Ch3Gear.ROW == t && Math.abs(Ch3Gear.WHETSTONE_ATTACK - 2.2) < 1e-9, "Ch3Gear does not read its numbers from the table");
        GearCurve.Stage wing = Readiness.stage("thunder_wing");
        if (wing == null) {
            f.add("no thunder_wing stage");
            return;
        }
        double goal = Readiness.goalHearts(wing.id());
        double atGoal = Readiness.body(t.armor(), t.toughness(), goal * 2, wing, goal);
        double oneMore = Readiness.body(t.armor(), t.toughness(), (goal + 1) * 2, wing, goal);
        double titanSet = Readiness.body(wing.armor(), wing.toughness(), goal * 2, wing, goal);
        check(f, Readiness.colour(atGoal) != Readiness.Colour.RED, "the peak crossing set at the goal health is red (" + atGoal + ")");
        check(f, Readiness.colour(oneMore) == Readiness.Colour.GREEN, "the peak crossing set with one more heart is not green (" + oneMore + ")");
        check(f, atGoal < titanSet, "the peak crossing set should stay below the Thunder Wing set (" + atGoal + " vs " + titanSet + ")");
    }

    // ---------------------------------------------------------------- 感電の傷
    private static void shock(List<String> f, ServerLevel level, FakePlayer a) {
        net.minecraft.world.Difficulty before = level.getServer().getWorldData().getDifficulty();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        try {
            shockHits(f, level, a);
        } finally {
            level.getServer().setDifficulty(before, true);
        }
    }

    private static void shockHits(List<String> f, ServerLevel level, FakePlayer a) {
        check(f, Math.abs(Ch3Gear.shockFactor(0, false) - 1.0) < 1e-9 && Math.abs(Ch3Gear.shockFactor(4, false) - 0.75) < 1e-9
                && Math.abs(Ch3Gear.shockFactor(2, false) - 0.875) < 1e-9 && Math.abs(Ch3Gear.shockFactor(4, true) - 0.375) < 1e-9
                && Math.abs(Ch3Gear.shockFactor(0, true) - 0.5) < 1e-9, "the shock factor rule is wrong");
        check(f, Ch3Gear.isShock(level.damageSources().lightningBolt()) && !Ch3Gear.isShock(level.damageSources().generic())
                && !Ch3Gear.isShock(level.damageSources().fall()), "isShock should take lightning only");
        a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        float bare = hit(a, level);
        for (int i = 0; i < 4; i++) {
            a.setItemSlot(SLOTS[i], new ItemStack(pieces()[i]));
        }
        tickEquipment(a);
        // 旅装の防具の値は雷の傷にも効くので、比べる間だけ打ち消す (倍率だけを見る)
        ResourceLocation cancel = ResourceLocation.fromNamespaceAndPath("skyabyss", "selftest.cancel_armor");
        a.getAttribute(Attributes.ARMOR).addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(cancel, -19,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        a.getAttribute(Attributes.ARMOR_TOUGHNESS).addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(cancel, -11,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        float dressed = hit(a, level);
        a.addEffect(new MobEffectInstance(Ch3Gear.GROUNDED, 200, 0));
        float oiled = hit(a, level);
        check(f, bare > 0 && Math.abs(dressed - bare * 0.75f) < 0.05f, "a lightning hit in the full set did " + dressed + " (bare " + bare + ", want x0.75)");
        check(f, Math.abs(oiled - bare * 0.375f) < 0.05f, "a lightning hit with the set and the oil did " + oiled + " (want x0.375 of " + bare + ")");
        a.removeAllEffects();
        a.getAttribute(Attributes.ARMOR).removeModifier(cancel);
        a.getAttribute(Attributes.ARMOR_TOUGHNESS).removeModifier(cancel);
        for (EquipmentSlot s : SLOTS) {
            a.setItemSlot(s, ItemStack.EMPTY);
        }
        tickEquipment(a);
        a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        a.setHealth(20);
    }

    // 雷 8 を一度受けて減った体力
    private static float hit(FakePlayer a, ServerLevel level) {
        a.setHealth(a.getMaxHealth());
        a.invulnerableTime = 0;
        float before = a.getHealth();
        a.hurt(level.damageSources().lightningBolt(), 8f);
        float lost = before - a.getHealth();
        a.setHealth(a.getMaxHealth());
        a.invulnerableTime = 0;
        return lost;
    }

    // ---------------------------------------------------------------- 砥石・団子・油
    private static void consumables(List<String> f, ServerLevel level, FakePlayer a) {
        ItemStack dumpling = new ItemStack(Ch3Gear.WIND_DUMPLING.get());
        FoodProperties food = dumpling.get(DataComponents.FOOD);
        check(f, food != null && food.nutrition() >= 6 && food.effects().size() == 2
                && food.effects().stream().anyMatch(e -> e.effect().getEffect().is(MobEffects.ABSORPTION) && e.effect().getAmplifier() == 1 && e.effect().getDuration() == 1200)
                && food.effects().stream().anyMatch(e -> e.effect().getEffect().is(MobEffects.SLOW_FALLING) && e.effect().getDuration() == 400),
                "the wind dumpling must give absorption II 60 s and slow falling 20 s");
        check(f, Readiness.pointsPerItem(dumpling) >= 8 - 1e-6, "the dumpling counts " + Readiness.pointsPerItem(dumpling) + " provision points (absorption II)");
        ItemStack oil = new ItemStack(Ch3Gear.LIGHTNING_OIL.get());
        check(f, Math.abs(Readiness.pointsPerItem(oil) - Ch3Gear.OIL_POINTS) < 1e-6, "the oil counts " + Readiness.pointsPerItem(oil) + " provision points (want 2)");
        ItemStack drunk = oil.copy();
        drunk.getItem().finishUsingItem(drunk, level, a);
        MobEffectInstance g = a.getEffect(Ch3Gear.GROUNDED);
        check(f, g != null && g.getDuration() >= Ch3Gear.OIL_TICKS - 5 && Ch3Gear.OIL_TICKS == 1800, "the lightning oil does not ground for 90 s");
        a.removeAllEffects();

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        double thunder = Readiness.weaponDps(sword, Ch3Gear.WHETSTONE_ATTACK);
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        a.getInventory().add(new ItemStack(Ch2Gear.SALT_WHETSTONE.get(), 2));
        a.getInventory().add(new ItemStack(Ch3Gear.THUNDER_WHETSTONE.get(), 2));
        check(f, Math.abs(Readiness.bestDps(a) - thunder) < 1e-6, "salt and thunder whetstones were stacked or the thunder one ignored (" + Readiness.bestDps(a) + " vs " + thunder + ")");
        a.getInventory().clearContent();
        a.addEffect(new MobEffectInstance(Ch2Gear.SALT_WHETTED, 1000, 0));
        ItemStack stone = new ItemStack(Ch3Gear.THUNDER_WHETSTONE.get(), 2);
        a.setItemInHand(InteractionHand.MAIN_HAND, stone);
        stone.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, stone.getCount() == 1, "using a thunder whetstone did not use one up");
        MobEffectInstance e = a.getEffect(Ch3Gear.THUNDER_WHETTED);
        check(f, e != null && e.getDuration() >= Ch3Gear.WHETSTONE_TICKS - 5, "the thunder edge is not 20 minutes");
        check(f, !a.hasEffect(Ch2Gear.SALT_WHETTED), "the salt edge stayed under the thunder edge (they must not stack)");
        double withEdge = a.getAttributeValue(Attributes.ATTACK_DAMAGE);
        a.removeEffect(Ch3Gear.THUNDER_WHETTED);
        check(f, Math.abs(withEdge - a.getAttributeValue(Attributes.ATTACK_DAMAGE) - 2.2) < 1e-6, "the thunder edge does not add 2.2 attack");
        a.addEffect(new MobEffectInstance(Ch3Gear.THUNDER_WHETTED, 1000, 0));
        ItemStack rock = new ItemStack(TravelGear.ROCK_WHETSTONE.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, rock);
        rock.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, a.hasEffect(TravelGear.WHETTED) && !a.hasEffect(Ch3Gear.THUNDER_WHETTED), "the rock whetstone did not replace the thunder edge");
        a.addEffect(new MobEffectInstance(Ch3Gear.THUNDER_WHETTED, 1000, 0));
        ItemStack salt = new ItemStack(Ch2Gear.SALT_WHETSTONE.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, salt);
        salt.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, a.hasEffect(Ch2Gear.SALT_WHETTED) && !a.hasEffect(Ch3Gear.THUNDER_WHETTED), "the salt whetstone did not replace the thunder edge");
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.getCooldowns().removeCooldown(Ch3Gear.THUNDER_WHETSTONE.get());
        a.removeAllEffects();
    }

    private static void remedies(List<String> f) {
        List<Component> blade = Readiness.remedies(Readiness.Lamp.BLADE, "thunder_wing");
        check(f, has(blade, "thunder_whetstone") && !has(blade, "salt_whetstone") && !has(blade, "whetstone"), "the thunder wing blade remedy should name the thunder whetstone only");
        check(f, has(Readiness.remedies(Readiness.Lamp.BODY, "thunder_wing"), "peak_crossing") && has(Readiness.remedies(Readiness.Lamp.PROVISIONS, "thunder_wing"), "wind_dumpling")
                && has(Readiness.remedies(Readiness.Lamp.PROVISIONS, "thunder_wing"), "lightning_oil"), "the thunder wing remedies are incomplete");
        check(f, !has(Readiness.remedies(Readiness.Lamp.BLADE, "tide_shell"), "thunder_whetstone"), "the tide shell remedies name a chapter-3 item");
    }

    private static boolean has(List<Component> list, String name) {
        for (Component c : list) {
            if (c.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith("." + name)) {
                return true;
            }
        }
        return false;
    }

    private static void texts(List<String> f) {
        List<String> keys = new ArrayList<>(List.of("effect.skyabyss.thunder_whetted", "effect.skyabyss.grounded", "message.skyabyss.thunder_whetstone.used",
                "message.skyabyss.lightning_oil.used", "item.skyabyss.peak_crossing_armor.desc", "gear.skyabyss.origin.peak_crossing",
                "message.skyabyss.wind_streamer.read", "message.skyabyss.wind_streamer.now", "message.skyabyss.wind_streamer.still",
                "message.skyabyss.readiness.remedy.name.peak_crossing", "message.skyabyss.readiness.remedy.name.thunder_whetstone",
                "message.skyabyss.readiness.remedy.name.wind_dumpling", "message.skyabyss.readiness.remedy.name.lightning_oil",
                "dialogue.skyabyss.artisan.ch3a_first", "dialogue.skyabyss.artisan.ch3p_teach", "dialogue.skyabyss.artisan.ch3t_ward_weave",
                "dialogue.skyabyss.artisan.ch3a_ward", "dialogue.skyabyss.artisan.a_menu.teach3", "dialogue.skyabyss.artisan.a_menu.ward3"));
        for (String d : List.of("n", "ne", "e", "se", "s", "sw", "w", "nw")) {
            keys.add("message.skyabyss.wind_streamer.dir." + d);
        }
        for (String id : List.of("scorched_feather", "static_cotton", "copper_wire", "thunder_ward_thread", "thunder_whetstone", "wind_dumpling", "lightning_oil", "wind_streamer")) {
            keys.add("item.skyabyss." + id);
            keys.add("item.skyabyss." + id + ".desc");
        }
        for (String p : List.of("helmet", "chestplate", "leggings", "boots")) {
            keys.add("item.skyabyss.peak_crossing_" + p);
            keys.add("item.skyabyss.peak_crossing_" + p + ".story");
        }
        for (int i = 1; i <= 7; i++) {
            keys.add(WayfarerNotes.lineKey(3, i));
        }
        for (String lang : List.of("ja_jp", "en_us")) {
            Map<String, String> t = lang("/assets/skyabyss_ch3gear/lang/" + lang + ".json");
            for (String k : keys) {
                check(f, t.containsKey(k), lang + " is missing " + k);
            }
        }
    }

    private static Map<String, String> lang(String path) {
        Map<String, String> out = new HashMap<>();
        try (var in = Ch3GearSelfTest.class.getResourceAsStream(path)) {
            if (in != null) {
                com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                        .entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (java.io.IOException | RuntimeException e) {
            // 読めなければ空
        }
        return out;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("skyabyss", name);
    }

    private static void recipes(List<String> f, MinecraftServer server, ServerLevel level, FakePlayer a) {
        var manager = server.getRecipeManager();
        Map<Item, Integer> total = new HashMap<>();
        for (String piece : List.of("peak_crossing_helmet", "peak_crossing_chestplate", "peak_crossing_leggings", "peak_crossing_boots")) {
            var holder = manager.byKey(id(piece));
            check(f, holder.isPresent(), "the recipe " + piece + " is missing");
            holder.ifPresent(h -> {
                check(f, h.value().getResultItem(server.registryAccess()).is(BuiltInRegistries.ITEM.get(id(piece))), piece + " makes the wrong item");
                for (Ingredient ing : h.value().getIngredients()) {
                    if (!ing.isEmpty()) {
                        total.merge(ing.getItems()[0].getItem(), 1, Integer::sum);
                    }
                }
            });
        }
        Item shard = BuiltInRegistries.ITEM.get(id("tide_shell_shard"));
        check(f, shard != Items.AIR && total.getOrDefault(Ch3Gear.SCORCHED_FEATHER.get(), 0) == 12 && total.getOrDefault(Ch3Gear.STATIC_COTTON.get(), 0) == 6
                && total.getOrDefault(Ch3Gear.COPPER_WIRE.get(), 0) == 6 && total.getOrDefault(shard, 0) == 4,
                "the peak crossing set costs " + total + " (want feather 12, cotton 6, wire 6, tide shell shard 4)");
        check(f, Ch3Gear.RECIPES.size() == 9 && WayfarerNotes.recipes(3).size() == 9, "the chapter-3 notes should hold 9 recipes, got " + WayfarerNotes.recipes(3));
        for (ResourceLocation r : WayfarerNotes.recipes(3)) {
            check(f, manager.byKey(r).isPresent(), "the recipe " + r + " is missing");
            check(f, RecipeLock.locked(r), r + " is not locked");
        }
        var helmet = manager.byKey(id("peak_crossing_helmet"));
        if (helmet.isEmpty()) {
            return;
        }
        for (ResourceLocation r : WayfarerNotes.recipes(3)) {
            manager.byKey(r).ifPresent(h -> a.getRecipeBook().remove(h));
        }
        a.getInventory().clearContent();
        CraftingMenu menu = new CraftingMenu(9, a.getInventory(), ContainerLevelAccess.create(level, BlockPos.ZERO));
        a.containerMenu = menu;
        Item fe = Ch3Gear.SCORCHED_FEATHER.get();
        Item co = Ch3Gear.STATIC_COTTON.get();
        Item[] grid = {fe, co, fe, co, shard, co};
        for (int i = 0; i < 6; i++) {
            menu.getSlot(1 + i).set(new ItemStack(grid[i]));
        }
        check(f, menu.getSlot(0).getItem().is(Ch3Gear.HELMET.get()), "the crafting grid did not show the peak crossing hood");
        check(f, RecipeLock.sweep(a) && !menu.getSlot(0).hasItem(), "the locked peak crossing result was not cleared");
        check(f, WayfarerNotes.unlockRecipes(a, 3) == 9, "the chapter-3 notes did not unlock all nine recipes");
        menu.getSlot(1).set(new ItemStack(fe));
        check(f, menu.getSlot(0).getItem().is(Ch3Gear.HELMET.get()) && !RecipeLock.sweep(a), "an unlocked peak crossing recipe was cleared");
        for (int i = 1; i <= 9; i++) {
            menu.getSlot(i).set(ItemStack.EMPTY);
        }
        a.containerMenu = a.inventoryMenu;
    }

    // ---------------------------------------------------------------- 風読みの小旗
    private static void streamer(List<String> f, ServerLevel level, FakePlayer a) {
        check(f, Ch3Gear.compass(new Vec3(0, 0, -1)).equals("n") && Ch3Gear.compass(new Vec3(1, 0, 0)).equals("e") && Ch3Gear.compass(new Vec3(0, 0, 1)).equals("s")
                && Ch3Gear.compass(new Vec3(-1, 0, 0)).equals("w") && Ch3Gear.compass(new Vec3(1, 0, 1).normalize()).equals("se"), "the compass names are wrong");
        for (long day = 0; day < 8; day++) {
            Vec3 w = Ch3Gear.downwind(day);
            check(f, Math.abs(w.length() - 1) < 1e-6 && w.y == 0, "the downwind of day " + day + " is not a flat unit vector");
        }
        check(f, Ch3Gear.streamerMessage(new Vec3(1, 0, 0), 0).getContents() instanceof TranslatableContents tc && tc.getKey().endsWith(".now"), "a storm now should say now");
        check(f, Ch3Gear.streamerMessage(new Vec3(1, 0, 0), 6000).getContents() instanceof TranslatableContents tc2 && tc2.getKey().endsWith(".read")
                && tc2.getArgs().length == 2 && Integer.valueOf(5).equals(tc2.getArgs()[1]), "6000 ticks should read as 5 minutes");
        ItemStack flag = new ItemStack(Ch3Gear.WIND_STREAMER.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, flag);
        int before = Ch3Gear.activeStreamers();
        flag.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, flag.getCount() == 1 && a.getCooldowns().isOnCooldown(Ch3Gear.WIND_STREAMER.get()), "the streamer was consumed or has no cooldown");
        check(f, Ch3Gear.activeStreamers() == before + 1, "the streamer did not stand (" + Ch3Gear.activeStreamers() + ")");
        flag.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, Ch3Gear.activeStreamers() == before + 1, "the streamer stood twice within its cooldown");
        check(f, Ch3Gear.STREAMER_TICKS == 600 && Ch3Gear.STREAMER_COOLDOWN == 600, "the streamer should last 30 s and wait 30 s");
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 鼓動守 (雷翼)
    private static void ward(List<String> f, FakePlayer a) {
        HeartbeatWard.Weave w = HeartbeatWard.weave(3);
        check(f, w != null && w.titan().equals("thunder_wing") && w.thread().equals("skyabyss:thunder_ward_thread") && w.refill().equals("skyabyss:static_cotton")
                && w.refillCount() == 8 && w.wovenFlag().equals("ch3t_woven"), "the chapter-3 weave is " + w);
        check(f, GuideSetup.wardOf("thunder_wing") != null, "there is no thunder wing ward item");
        a.getInventory().clearContent();
        Growth.get(a).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
        check(f, HeartbeatWard.remake(a, "thunder_wing") && a.getInventory().countItem(GuideSetup.wardOf("thunder_wing")) == 1, "the thunder wing ward was not woven");
        check(f, !HeartbeatWard.remakeable(a, "thunder_wing"), "a second thunder wing ward was offered while one is carried");
        a.getInventory().clearContent();
        Growth.get(a).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
    }

    private static void gearSignal(List<String> f, FakePlayer a) {
        a.getInventory().clearContent();
        a.getInventory().add(new ItemStack(Ch3Gear.HELMET.get()));
        a.getInventory().add(new ItemStack(Ch3Gear.CHESTPLATE.get()));
        check(f, !Ch3Gear.hasFullSet(a) && !Ch3Gear.signalIfComplete(a), "half a peak crossing set counted as the full set");
        a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Ch3Gear.LEGGINGS.get()));
        a.getInventory().add(new ItemStack(Ch3Gear.BOOTS.get()));
        check(f, Ch3Gear.hasFullSet(a) && Ch3Gear.signalIfComplete(a), "the full peak crossing set did not send " + Ch3Gear.SIGNAL_GEAR_MADE);
        a.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }
}
