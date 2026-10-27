package jp.revati.skyabyss.guide;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch1.Ch1Prep;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 旅装・砥石・糧と薬 (P5。一章の分)。各章の地上で、前の巨神の装備がなくても備えを緑まで持っていける品。
// 登録 (品・防具の素材・砥石の効き目) と、備えの三灯への足し込み (Readiness.registerWhetstone / registerRemedy)、旅装を一式作ったときの合図。
// 手に入るのは鍛冶場・薬師の誂え (story/ch1/Ch1Prep.MADE)。
//
// 二〜四章の旅装は同じ型 (GearCurve.TRAVEL の表)。素材 (MATERIAL) と品の登録を足し、誂えの品 (MADE) に並べる。
// 一章の数値 (GearCurve.TRAVEL[0]): 防御 2/7/5/3 = 17、強靭 1.25 × 4 = 5、ノックバック耐性 0.025 × 4 = 0.1、必要 Lv 7、アンコモン
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TravelGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    // 耐久の係数 (鉄 15・ダイヤ 33。ここでは 21)。道具の傷みを意識させる程度
    public static final int DURABILITY_FACTOR = 21;
    // 砥石: 攻撃 +1.5 を 20 分
    public static final double WHETSTONE_ATTACK = GearCurve.TRAVEL.get(0).whetstone();
    public static final int WHETSTONE_TICKS = 20 * 60 * 20;
    // 道の固焼き: 吸収 II を 60 秒
    public static final int HARDTACK_TICKS = 20 * 60;
    // 苔の軟膏: 再生 I を 8 秒 + 鈍足を治す
    public static final int SALVE_REGEN_TICKS = 20 * 8;

    // 防御 2/7/5/3 = 17 (一式)、強靭は 1 部位 1.25 (4 部位で 5)
    public static final int[] ARMOR_DEFENSE = {2, 7, 5, 3};
    public static final float ARMOR_TOUGHNESS = 1.25f;
    public static final float ARMOR_KNOCKBACK = 0.025f;

    public static final DeferredHolder<MobEffect, MobEffect> WHETTED = EFFECTS.register("whetted", WhettedEffect::new);

    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("whale_road", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 7)),
            8,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(GuideSetup.ROCK_SHELL_SCRAP.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "whale_road"))),
            ARMOR_TOUGHNESS,
            ARMOR_KNOCKBACK));

    // ---------------------------------------------------------------- 鯨道の旅装 (4 部位)
    public static final DeferredItem<Item> HELMET = ITEMS.register("whale_road_helmet",
            () -> new WhaleRoadArmorItem(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> CHESTPLATE = ITEMS.register("whale_road_chestplate",
            () -> new WhaleRoadArmorItem(MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> LEGGINGS = ITEMS.register("whale_road_leggings",
            () -> new WhaleRoadArmorItem(MATERIAL, ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> BOOTS = ITEMS.register("whale_road_boots",
            () -> new WhaleRoadArmorItem(MATERIAL, ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---------------------------------------------------------------- 砥石・糧・薬
    public static final DeferredItem<Item> ROCK_WHETSTONE = ITEMS.register("rock_whetstone",
            () -> new RockWhetstoneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> ROAD_HARDTACK = ITEMS.register("road_hardtack",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8f)
                    .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, HARDTACK_TICKS, 1), 1.0f).build())));
    public static final DeferredItem<Item> MOSS_SALVE = ITEMS.register("moss_salve",
            () -> new MossSalveItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder().nutrition(0).saturationModifier(0f).alwaysEdible()
                    .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, SALVE_REGEN_TICKS, 0), 1.0f).build())));

    // 砥石の種類ごとの上乗せ (効いているか持ち歩いているときの値)。備えの刃は一番大きい一つだけを数える。static の登録より前に置く
    private static final List<java.util.function.ToDoubleFunction<Player>> WHETSTONE_KINDS = new java.util.concurrent.CopyOnWriteArrayList<>();

    static {
        // 備えの三灯へ: 砥石 = 効いているか、持ち歩いている (使えばすぐ効く) とき攻撃 +1.5。登録があると刃の手段が「砥石で鍛える」に替わる。
        // 旅装は防具の属性で自然に身の灯に入る。糧と薬は FoodProperties で自然に糧の灯に入る (固焼き = 吸収 II の 8 点、軟膏 = 再生 I の 3.2 点)
        // 砥石の種類 (章ごと) は重ねない: 登録は一つだけで、種類ごとの上乗せの一番大きい値を数える (二章の塩の砥石は addWhetstoneKind で足す)
        addWhetstoneKind(player -> player.hasEffect(WHETTED) || carries(player, ROCK_WHETSTONE.get()) ? WHETSTONE_ATTACK : 0);
        Readiness.registerWhetstone(TravelGear::whetstoneBonus);
        // 手段 (灯が赤・黄のときの一行に出る品の名前)。一章の品は岩鯨の備えのときだけ出す
        Readiness.registerRemedy(Readiness.Lamp.BODY, "stone_whale", () -> Component.translatable("message.skyabyss.readiness.remedy.name.travel_gear"));
        Readiness.registerRemedy(Readiness.Lamp.BLADE, "stone_whale", () -> Component.translatable("message.skyabyss.readiness.remedy.name.whetstone"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, "stone_whale", () -> Component.translatable("message.skyabyss.readiness.remedy.name.hardtack"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, "stone_whale", () -> Component.translatable("message.skyabyss.readiness.remedy.name.salve"));
    }

    private TravelGear() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EFFECTS.register(modEventBus);
        modEventBus.addListener(TravelGear::onCreativeTab);
        // 二章の分 (潮渡りの旅装・塩の砥石・糧と薬・砂時計・素材)
        Ch2Gear.register(modEventBus);
        // 三章の分 (峰越えの旅装・雷の砥石・団子と油・風読みの小旗・素材)
        Ch3Gear.register(modEventBus);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(HELMET.get());
            event.accept(CHESTPLATE.get());
            event.accept(LEGGINGS.get());
            event.accept(BOOTS.get());
            event.accept(ROCK_WHETSTONE.get());
            event.accept(ROAD_HARDTACK.get());
            event.accept(MOSS_SALVE.get());
        }
    }

    // 持ち物に砥石 (一章) があるか
    public static boolean carriesWhetstone(Player player) {
        return carries(player, ROCK_WHETSTONE.get());
    }

    public static boolean carries(Player player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    public static void addWhetstoneKind(java.util.function.ToDoubleFunction<Player> bonus) {
        WHETSTONE_KINDS.add(bonus);
    }

    public static double whetstoneBonus(Player player) {
        double best = 0;
        for (java.util.function.ToDoubleFunction<Player> k : WHETSTONE_KINDS) {
            best = Math.max(best, k.applyAsDouble(player));
        }
        return best;
    }

    // 鯨道の旅装の 4 部位がそろっているか (着ているか持ち物にあるか。同じ部位は一つで数える)
    public static boolean hasFullSet(Player player) {
        List<DeferredItem<Item>> pieces = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);
        for (DeferredItem<Item> piece : pieces) {
            if (player.getInventory().countItem(piece.get()) > 0) {
                continue;
            }
            boolean worn = false;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (player.getItemBySlot(slot).is(piece.get())) {
                    worn = true;
                }
            }
            if (!worn) {
                return false;
            }
        }
        return true;
    }

    // 旅装を作った: 一式そろったら合図 ch1_travel_gear_made (備えの頼み「身ごしらえ」が済む)。作った品は取り出したあとに持ち物へ入るので、少し待ってから数える
    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isWhaleRoad(event.getCrafting())) {
            return;
        }
        Tasks.later(2, () -> signalIfComplete(player));
    }

    // 自己テスト用: 数えて、そろっていれば合図を送る (送ったら true)
    public static boolean signalIfComplete(ServerPlayer player) {
        if (!hasFullSet(player)) {
            return false;
        }
        Quests.signal(player, Ch1Prep.SIGNAL_GEAR_MADE);
        return true;
    }

    public static boolean isWhaleRoad(ItemStack stack) {
        return stack.is(HELMET.get()) || stack.is(CHESTPLATE.get()) || stack.is(LEGGINGS.get()) || stack.is(BOOTS.get());
    }

    // 砥石の攻撃の上乗せ (効き目の中身。属性の修飾子)
    static void addAttackModifier(MobEffect effect) {
        effect.addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "effect.whetted"), WHETSTONE_ATTACK,
                AttributeModifier.Operation.ADD_VALUE);
    }

    // 砥石を使った (RockWhetstoneItem)。効き目を 20 分。使えたら true
    public static boolean applyWhetstone(ServerPlayer player) {
        // 研ぎは重ねない (塩の砥石の刃は岩の砥石で研ぎ直すと替わる)
        player.removeEffect(Ch2Gear.SALT_WHETTED);
        player.removeEffect(Ch3Gear.THUNDER_WHETTED);
        player.addEffect(new MobEffectInstance(WHETTED, WHETSTONE_TICKS, 0, false, true, true));
        Notice.hint(player, "message.skyabyss.whetstone.used");
        return true;
    }

    // 備えの頼み「刃を研ぐ」の「岩の砥石を作る」(2026-10-23: 使うのではなく作るにした)
    @SubscribeEvent
    static void onWhetstoneCrafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getCrafting().is(ROCK_WHETSTONE.get())) {
            Quests.signal(player, Ch1Prep.SIGNAL_WHETSTONE_USED);
        }
    }
}
