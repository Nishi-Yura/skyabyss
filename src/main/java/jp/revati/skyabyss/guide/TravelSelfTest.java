package jp.revati.skyabyss.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.ch1.Ch1Prep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.util.FakePlayer;

// 旅装・砥石・糧と薬 (docs/design/21 の 1.2、P5。一章の分) の自己テスト。段: "travel gear"。
//  - 防具 4 部位の値 (防御 17・強靭 5・踏ん張り 0.1・アンコモン・鍛えない・特性なし・必要 Lv 7) と、GearCurve.TRAVEL の表との照合 (巨神の装備を超えない、段ごとに伸びる、備えの身の灯)
//  - 砥石 (使うと効き目・攻撃 +1.5・備えの刃に入る・手段の登録)・固焼き・軟膏 (糧の点・鈍足を治す)
//  - レシピ (7 本。旅装の素材の合計は備えの頼みの集める数と同じ)・鍵 (覚書か職人に教わるまで作業台の完成品が空になる)・旅装を一式作ると合図
public final class TravelSelfTest {
    private TravelSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        // 二章の分 (Ch2Gear) は別の段 "ch2 gear"
        return List.of(SelfTestRunner.Step.once("travel gear", TravelSelfTest::run), Ch2GearSelfTest.step(), Ch3GearSelfTest.step());
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        Growth.get(p).level = 30;
        return p;
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = player(level, "travel_a");
        try {
            armor(f);
            curve(f);
            consumables(f, level, a);
            recipes(f, server);
            lock(f, server, level, a);
            gearSignal(f, a);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST travel gear crashed", e);
            f.add("crashed: " + e);
        } finally {
            Quests.forgetAll(a);
            Growth.get(a).discoveries.clear();
            a.getInventory().clearContent();
            a.removeAllEffects();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST travel gear PASSED (armor values, GearCurve table, whetstone, hardtack and salve, made to order at the smithy, no crafting, gear-made signal)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST travel gear FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 防具の値
    private static void armor(List<String> f) {
        Item[] pieces = {TravelGear.HELMET.get(), TravelGear.CHESTPLATE.get(), TravelGear.LEGGINGS.get(), TravelGear.BOOTS.get()};
        int[] want = {2, 7, 5, 3};
        double defense = 0;
        double toughness = 0;
        double knockback = 0;
        for (int i = 0; i < 4; i++) {
            check(f, pieces[i] instanceof ArmorItem && pieces[i] instanceof ModGear, "piece " + i + " is not a ModGear armor item");
            if (!(pieces[i] instanceof ArmorItem armor) || !(pieces[i] instanceof ModGear gear)) {
                continue;
            }
            check(f, armor.getDefense() == want[i], "piece " + i + " defense " + armor.getDefense() + " (want " + want[i] + ")");
            defense += armor.getDefense();
            toughness += armor.getToughness();
            knockback += armor.getMaterial().value().knockbackResistance();
            check(f, gear.tier() == GearTier.UNCOMMON && !gear.enhanceable(), "piece " + i + " must be uncommon and not enhanceable");
            check(f, GearLevel.required(pieces[i]) == GearCurve.TRAVEL.get(0).level() && GearLevel.required(pieces[i]) == 7, "piece " + i + " required level " + GearLevel.required(pieces[i]));
            ItemStack stack = new ItemStack(pieces[i]);
            check(f, !AffixRules.eligible(stack) && !ForgeRules.canEnhance(stack), "piece " + i + " takes affixes or enhancement");
            check(f, stack.getMaxDamage() > 0, "piece " + i + " has no durability");
            check(f, armor.getEquipmentSlot() == new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}[i], "piece " + i + " is in the wrong slot");
        }
        GearCurve.Travel t = GearCurve.TRAVEL.get(0);
        check(f, defense == t.armor() && Math.abs(toughness - t.toughness()) < 1e-6, "whale road set " + defense + " / " + toughness + " (want " + t.armor() + " / " + t.toughness() + ")");
        check(f, Math.abs(knockback - 0.1) < 1e-6, "whale road knockback resistance " + knockback + " (want 0.1)");
        check(f, defense == 17 && Math.abs(toughness - 5) < 1e-6, "the chapter-1 set must be 17 / 5");
    }

    // ---------------------------------------------------------------- GearCurve との照合
    private static void curve(List<String> f) {
        check(f, GearCurve.TRAVEL.size() == 4, "the travel gear table must have four chapters");
        GearCurve.Travel prev = null;
        for (GearCurve.Travel t : GearCurve.TRAVEL) {
            GearCurve.Stage stage = Readiness.stage(t.stage());
            check(f, stage != null, t.id() + ": unknown stage " + t.stage());
            if (stage == null) {
                continue;
            }
            double ratio = t.armor() / stage.armor();
            check(f, ratio >= 0.85 && ratio <= 0.92, t.id() + " defense is " + t.armor() + " / " + stage.armor() + " of the stage armor +0 (want 85〜92%)");
            check(f, t.toughness() <= stage.toughness() && t.armor() < stage.armor(), t.id() + " must stay below the titan set (the bridge, not the destination)");
            check(f, t.level() < stage.level(), t.id() + " required level " + t.level() + " must be below the stage recommendation " + stage.level());
            if (prev != null) {
                check(f, t.armor() > prev.armor() && t.toughness() > prev.toughness() && t.level() > prev.level() && t.whetstone() > prev.whetstone(),
                        t.id() + " does not grow over " + prev.id());
                check(f, t.armor() >= prev.armor() && t.armor() < Readiness.stage(prev.stage()).armor() + 3, t.id() + " defense jumps past the previous titan set");
            }
            prev = t;
        }
        // 備えの身の灯: 旅装の防御・強靭で、目安の体力のとき黄より下には落ちず、体力が一つ多ければ緑。巨神の装備 +0 には届かない
        GearCurve.Stage whale = GearCurve.STAGES.get(0);
        double goal = Readiness.goalHearts(whale.id());
        GearCurve.Travel t = GearCurve.TRAVEL.get(0);
        double atGoal = Readiness.body(t.armor(), t.toughness(), goal * 2, whale, goal);
        double oneMore = Readiness.body(t.armor(), t.toughness(), (goal + 1) * 2, whale, goal);
        double titanSet = Readiness.body(whale.armor(), whale.toughness(), goal * 2, whale, goal);
        check(f, Readiness.colour(atGoal) != Readiness.Colour.RED, "the whale road set at the goal health is red (" + atGoal + ")");
        check(f, Readiness.colour(oneMore) == Readiness.Colour.GREEN, "the whale road set with a little more health is not green (" + oneMore + ")");
        check(f, atGoal < titanSet && titanSet >= 0.99, "the whale road set should stay below the Stone Whale set (" + atGoal + " vs " + titanSet + ")");
        // 砥石: 表の値 (一章は攻撃 +1.5)
        check(f, Math.abs(TravelGear.WHETSTONE_ATTACK - t.whetstone()) < 1e-6 && t.whetstone() == 1.5, "the whetstone attack must come from the table (1.5)");
    }

    // ---------------------------------------------------------------- 砥石・固焼き・軟膏
    private static void consumables(List<String> f, ServerLevel level, FakePlayer a) {
        // 固焼き: 吸収 II を 60 秒 + 満腹。備えの糧の点 8
        ItemStack hardtack = new ItemStack(TravelGear.ROAD_HARDTACK.get());
        FoodProperties food = hardtack.get(DataComponents.FOOD);
        check(f, food != null && food.nutrition() >= 6 && food.effects().size() == 1, "hardtack is not a filling food with one effect");
        if (food != null && food.effects().size() == 1) {
            MobEffectInstance e = food.effects().get(0).effect();
            check(f, e.getEffect().is(MobEffects.ABSORPTION) && e.getAmplifier() == 1 && e.getDuration() == 1200, "hardtack must give absorption II for 60 s");
        }
        check(f, Math.abs(Readiness.pointsPerItem(hardtack) - 8) < 1e-6, "hardtack counts " + Readiness.pointsPerItem(hardtack) + " provision points (want 8)");
        // 軟膏: 再生 I を 8 秒 (3.2 点)・鈍足を治す
        ItemStack salve = new ItemStack(TravelGear.MOSS_SALVE.get());
        check(f, Math.abs(Readiness.pointsPerItem(salve) - 3.2) < 1e-6, "salve counts " + Readiness.pointsPerItem(salve) + " provision points (want 3.2)");
        a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
        check(f, a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the test player is not slowed");
        ItemStack eaten = salve.copy();
        eaten.getItem().finishUsingItem(eaten, level, a);
        check(f, !a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the salve did not cure slowness");
        check(f, a.hasEffect(MobEffects.REGENERATION) && a.getEffect(MobEffects.REGENERATION).getDuration() <= 160, "the salve did not give regeneration for 8 s");
        a.removeAllEffects();

        // 砥石: 使うと「研いだ刃」 (攻撃 +1.5。20 分)。一つ減る。備えの刃に入る
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        double base = Readiness.weaponDps(sword, 0);
        double honed = Readiness.weaponDps(sword, TravelGear.WHETSTONE_ATTACK);
        check(f, honed > base, "a whetstone did not raise the blade dps");
        check(f, Readiness.hasWhetstone(), "the whetstone was not registered with Readiness");
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        check(f, Math.abs(Readiness.bestDps(a) - base) < 1e-6, "the blade lamp counted a whetstone nobody has");
        a.getInventory().add(new ItemStack(TravelGear.ROCK_WHETSTONE.get(), 2));
        check(f, Math.abs(Readiness.bestDps(a) - honed) < 1e-6, "a carried whetstone was not counted (" + Readiness.bestDps(a) + " vs " + honed + ")");
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        ItemStack stone = new ItemStack(TravelGear.ROCK_WHETSTONE.get(), 2);
        a.setItemInHand(InteractionHand.MAIN_HAND, stone);
        double attackBefore = a.getAttributeValue(Attributes.ATTACK_DAMAGE);
        stone.getItem().use(level, a, InteractionHand.MAIN_HAND);
        check(f, stone.getCount() == 1, "using a whetstone did not use one up (" + stone.getCount() + ")");
        MobEffectInstance whetted = a.getEffect(TravelGear.WHETTED);
        check(f, whetted != null && whetted.getDuration() >= TravelGear.WHETSTONE_TICKS - 5 && TravelGear.WHETSTONE_TICKS == 24000, "the whetted effect is not 20 minutes");
        check(f, Math.abs(a.getAttributeValue(Attributes.ATTACK_DAMAGE) - attackBefore - 1.5) < 1e-6,
                "the honed blade adds " + (a.getAttributeValue(Attributes.ATTACK_DAMAGE) - attackBefore) + " attack (want 1.5)");
        a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        a.getInventory().clearContent();
        a.getInventory().add(sword.copy());
        check(f, Math.abs(Readiness.bestDps(a) - honed) < 1e-6, "an active honed blade was not counted by the blade lamp");
        a.removeAllEffects();
        check(f, Math.abs(Readiness.bestDps(a) - base) < 1e-6, "the blade lamp kept counting after the effect ran out");
        // 手段の名前 (灯が赤・黄のときの一行)
        check(f, hasRemedy(Readiness.Lamp.BODY, "travel_gear") && hasRemedy(Readiness.Lamp.BLADE, "whetstone") && hasRemedy(Readiness.Lamp.PROVISIONS, "hardtack")
                && hasRemedy(Readiness.Lamp.PROVISIONS, "salve"), "the readiness remedies for the road gear are not registered");
        // 文 (日英)
        for (String lang : List.of("ja_jp", "en_us")) {
            Map<String, String> texts = lang(lang);
            for (String key : List.of("item.skyabyss.whale_road_helmet", "item.skyabyss.whale_road_chestplate", "item.skyabyss.whale_road_leggings",
                    "item.skyabyss.whale_road_boots", "item.skyabyss.whale_road_armor.desc", "gear.skyabyss.origin.whale_road", "gear.skyabyss.origin_weapon.stone_whale", "gear.skyabyss.origin_weapon.tide_shell", "gear.skyabyss.origin_weapon.thunder_wing", "item.skyabyss.rock_whetstone",
                    "item.skyabyss.rock_whetstone.desc", "item.skyabyss.road_hardtack", "item.skyabyss.road_hardtack.desc", "item.skyabyss.moss_salve",
                    "item.skyabyss.moss_salve.desc", "effect.skyabyss.whetted", "message.skyabyss.whetstone.used", "message.skyabyss.recipe.locked",
                    "message.skyabyss.readiness.remedy.name.travel_gear", "message.skyabyss.readiness.remedy.name.whetstone",
                    "message.skyabyss.readiness.remedy.name.hardtack", "message.skyabyss.readiness.remedy.name.salve", "wayfarer_notes.ch1.line.7",
                    "item.skyabyss.whale_road_helmet.story", "item.skyabyss.whale_road_boots.story")) {
                check(f, texts.containsKey(key) || lang(lang.equals("ja_jp") ? "gear_story_ja" : "gear_story_en").containsKey(key), lang + " is missing " + key);
            }
        }
    }

    private static boolean hasRemedy(Readiness.Lamp lamp, String name) {
        for (Component c : Readiness.remedies(lamp)) {
            if (c.getContents() instanceof TranslatableContents tc && tc.getKey().endsWith("." + name)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> lang(String which) {
        Map<String, String> out = new HashMap<>();
        // 各専用武器の由来の一行は、巨神ごとの名前空間の lang にある
        List<String> paths = switch (which) {
            case "gear_story_ja" -> List.of("/assets/skyabyss_gear_story/lang/ja_jp.json");
            case "gear_story_en" -> List.of("/assets/skyabyss_gear_story/lang/en_us.json");
            default -> List.of("/assets/skyabyss_ch1/lang/" + which + ".json", "/assets/skyabyss/lang/" + which + ".json",
                    "/assets/skyabyss_ember/lang/" + which + ".json", "/assets/skyabyss_storm_gear/lang/" + which + ".json",
                    "/assets/skyabyss_tide_gear/lang/" + which + ".json");
        };
        for (String path : paths) {
            try (var in = TravelSelfTest.class.getResourceAsStream(path)) {
                if (in != null) {
                    var o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
                }
            } catch (java.io.IOException | RuntimeException e) {
                // 読めなければ空 (キーなしの失敗になる)
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- レシピ
    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("skyabyss", name);
    }

    private static void recipes(List<String> f, MinecraftServer server) {
        // 旅装 4 部位は鍛冶場の誂え (一章から)。素材は岩殻片 12・骨片 2 (鉄・革は掘れないので鐘銭で払う)
        check(f, jp.revati.skyabyss.quest.QuestManager.quest("ch1/prep_gear") == null, "the old prep_gear quest should be absorbed into the trail steps");
        madeSet(f, List.of("whale_road_helmet", "whale_road_chestplate", "whale_road_leggings", "whale_road_boots"), "smithy", 1,
                Map.of(GuideSetup.ROCK_SHELL_SCRAP.get(), 12, GuideSetup.BONE_CHIP.get(), 2), "whale road set");
        for (String other : List.of("rock_whetstone", "road_hardtack", "moss_salve")) {
            check(f, jp.revati.skyabyss.facility.MadeToOrder.find(other) != null, other + " cannot be had in town");
        }
        madeChapter(f, 1);
        // 序盤の素材 (岩殻片・骨片) は旅嚢の品 (誂えは旅嚢の中の分も払いに使う)。鉄・革・砥石は袋の品ではない
        for (Item mat : List.of(GuideSetup.ROCK_SHELL_SCRAP.get(), GuideSetup.BONE_CHIP.get())) {
            check(f, jp.revati.skyabyss.pouch.PouchEntries.entry(mat) != null, mat + " is not a pouch item");
        }
        for (Item mat : List.of(Items.IRON_INGOT, Items.LEATHER, TravelGear.ROCK_WHETSTONE.get())) {
            check(f, jp.revati.skyabyss.pouch.PouchEntries.entry(mat) == null, mat + " is a pouch item");
        }
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

    // item の ID から (旅装の名前)
    private static final class BuiltInRegistriesItem {
        static Item of(String path) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id(path));
        }
    }

    // ---------------------------------------------------------------- 鍵 (覚書か職人に教わるまで作れない)
    private static void lock(List<String> f, MinecraftServer server, ServerLevel level, FakePlayer a) {
        a.getInventory().clearContent();
        noCrafting(f, server, level, a);
        check(f, !ForgeRules.accepts(new ItemStack(TravelGear.HELMET.get())) && ForgeRules.accepts(new ItemStack(jp.revati.skyabyss.registry.ModItems.STONE_WHALE_HELMET.get())),
                "the forge must refuse the road gear (no farming whale fragments) and still take titan gear");
        // 誂え: 素材と鐘銭を払うと、鍛冶場が兜を作る
        jp.revati.skyabyss.facility.ShopDef smithy = jp.revati.skyabyss.facility.ShopManager.shop("smithy");
        jp.revati.skyabyss.facility.Coins.give(a, 40);
        a.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 3));
        check(f, smithy != null && jp.revati.skyabyss.facility.ShopService.buy(a, smithy, "made_whale_road_helmet", 1) == jp.revati.skyabyss.facility.ShopService.Result.OK
                && a.getInventory().countItem(TravelGear.HELMET.get()) == 1 && a.getInventory().countItem(GuideSetup.ROCK_SHELL_SCRAP.get()) == 0,
                "the smithy did not make the whale road hood from the scraps");
        jp.revati.skyabyss.facility.Coins.take(a, jp.revati.skyabyss.facility.Coins.count(a));
        a.getInventory().clearContent();
    }

    private static void fillHelmet(AbstractContainerMenu menu) {
        Item scrap = GuideSetup.ROCK_SHELL_SCRAP.get();
        menu.getSlot(1).set(new ItemStack(scrap));
        menu.getSlot(2).set(new ItemStack(scrap));
        menu.getSlot(3).set(new ItemStack(scrap));
        menu.getSlot(4).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(6).set(new ItemStack(Items.IRON_INGOT));
    }

    private static void clearGrid(AbstractContainerMenu menu) {
        for (int i = 1; i <= 9; i++) {
            menu.getSlot(i).set(ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- 旅装を一式作ると合図 (任意。どの段も読まないが、合図は残す)
    private static void gearSignal(List<String> f, FakePlayer a) {
        Quests.forgetAll(a);
        a.getInventory().clearContent();
        // 一部だけでは合図は出ない
        a.getInventory().add(new ItemStack(TravelGear.HELMET.get()));
        a.getInventory().add(new ItemStack(TravelGear.CHESTPLATE.get()));
        check(f, !TravelGear.hasFullSet(a) && !TravelGear.signalIfComplete(a), "half a set counted as the full set");
        // 着ている分と持ち物の分を合わせて一式 (着ている分も数える)
        a.setItemSlot(EquipmentSlot.LEGS, new ItemStack(TravelGear.LEGGINGS.get()));
        a.getInventory().add(new ItemStack(TravelGear.BOOTS.get()));
        check(f, TravelGear.hasFullSet(a), "worn and carried pieces were not counted as a set");
        check(f, TravelGear.signalIfComplete(a), "the full set did not send " + Ch1Prep.SIGNAL_GEAR_MADE);
        a.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }
}
