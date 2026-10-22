package jp.revati.skyabyss.story.ch4;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.Ch2Gear;
import jp.revati.skyabyss.guide.Ch3Gear;
import jp.revati.skyabyss.guide.TravelGear;
import jp.revati.skyabyss.guide.WhaleRoadArmorItem;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.readiness.Readiness;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

// 四章「足もとから来るもの」の備えの品 (docs/design/21 の 1.2・6.1)。一〜三章の TravelGear・Ch2Gear・Ch3Gear と同じ型。
//  - 灰歩きの旅装 (4 部位): 防御 3/7/6/4 = 20、強靭 3.25 × 4 = 13 (GearCurve.TRAVEL[3])、余燼の背の熱のたまり −2.5% × 着ている部位 (一式 −10%。
//    EmberHeat の重ねる倍率)、アンコモン、炉で鍛えない、必要 Lv 25。修理は煤鉄。一式で合図 SIGNAL_GEAR_MADE。着た姿は低ポリ (AshWalkingArmorRenderer)
//  - 煤の砥石: 「煤の刃」(攻撃 +2.6、20 分)。岩・塩・雷の砥石とは重ねない (替わる)。煤の刃のあいだ、灰被りの系統の殻へ 1.5 倍 (Ch4Mobs.shellBonus)
//  - 灰焼きのパン: 食べると吸収 II 60 秒と、余燼の背の熱 −15
// レシピは旅の覚書 (四章) に教わるまで作れない (RecipeLock。Ch4Setup.RECIPES)。生成は tools/gen/ch4/gen_ch4_gear.py (レシピ・文・絵)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch4Gear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, SkyAbyss.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, SkyAbyss.MODID);

    public static final int CHAPTER = 4;
    public static final String TITAN = "ember";
    public static final GearCurve.Travel ROW = GearCurve.TRAVEL.get(CHAPTER - 1);
    public static final String SIGNAL_GEAR_MADE = "ch4_travel_gear_made";
    public static final String SIGNAL_WHETSTONE_USED = "ch4_whetstone_used";

    public static final int[] ARMOR_DEFENSE = {3, 7, 6, 4};
    public static final float ARMOR_TOUGHNESS = 3.25f;
    // 熱のたまりの軽さ: 1 部位 2.5% (一式 10%)
    public static final double HEAT_PER_PIECE = 0.025;
    public static final double WHETSTONE_ATTACK = ROW.whetstone();
    public static final int WHETSTONE_TICKS = 20 * 60 * 20;
    public static final int BREAD_ABSORB_TICKS = 20 * 60;
    public static final float BREAD_COOL = 15f;

    public static final DeferredHolder<MobEffect, MobEffect> SOOT_WHETTED = EFFECTS.register("soot_whetted", SootWhettedEffect::new);

    public static final Holder<ArmorMaterial> MATERIAL = MATERIALS.register("ash_walking", () -> new ArmorMaterial(
            new EnumMap<>(Map.of(
                    ArmorItem.Type.HELMET, ARMOR_DEFENSE[0],
                    ArmorItem.Type.CHESTPLATE, ARMOR_DEFENSE[1],
                    ArmorItem.Type.LEGGINGS, ARMOR_DEFENSE[2],
                    ArmorItem.Type.BOOTS, ARMOR_DEFENSE[3],
                    ArmorItem.Type.BODY, 7)),
            9,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(Ch4Setup.SOOT_IRON.get()),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ash_walking"))),
            ARMOR_TOUGHNESS,
            0f));

    public static final DeferredItem<Item> HELMET = armor("ash_walking_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<Item> CHESTPLATE = armor("ash_walking_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<Item> LEGGINGS = armor("ash_walking_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<Item> BOOTS = armor("ash_walking_boots", ArmorItem.Type.BOOTS);
    public static final List<DeferredItem<Item>> PIECES = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    public static final DeferredItem<Item> ASH_BAKED_BREAD = ITEMS.register("ash_baked_bread",
            () -> new AshBreadItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.8f)
                    .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, BREAD_ABSORB_TICKS, 1), 1.0f).build())));

    static {
        // 熱のたまり (余燼の背): 着ている部位ごとに 2.5% 軽く (重ねる倍率)
        EmberHeat.addModifier(true, p -> 1.0 - HEAT_PER_PIECE * worn(p));
        // 備えの刃: 煤の砥石は効いているか持ち歩いているとき +2.6 (ほかの砥石と重ねず、大きい方)
        TravelGear.addWhetstoneKind(p -> p.hasEffect(SOOT_WHETTED) || TravelGear.carries(p, Ch4Setup.SOOT_WHETSTONE.get()) ? WHETSTONE_ATTACK : 0);
        Readiness.registerRemedy(Readiness.Lamp.BODY, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.ash_walking"));
        Readiness.registerRemedy(Readiness.Lamp.BLADE, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.soot_whetstone"));
        Readiness.registerRemedy(Readiness.Lamp.PROVISIONS, TITAN, () -> Component.translatable("message.skyabyss.readiness.remedy.name.ash_baked_bread"));
    }

    private Ch4Gear() {
    }

    private static DeferredItem<Item> armor(String id, ArmorItem.Type type) {
        return ITEMS.register(id, () -> new AshWalkingArmorItem(MATERIAL, type, new Item.Properties().rarity(Rarity.UNCOMMON)));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MATERIALS.register(modEventBus);
        EFFECTS.register(modEventBus);
        modEventBus.addListener(Ch4Gear::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (DeferredItem<Item> i : List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS, ASH_BAKED_BREAD)) {
                event.accept(i.get());
            }
        }
    }

    // ---------------------------------------------------------------- 旅装
    public static boolean isAshWalking(ItemStack stack) {
        for (DeferredItem<Item> p : PIECES) {
            if (stack.is(p.get())) {
                return true;
            }
        }
        return false;
    }

    public static int worn(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (isAshWalking(e.getItemBySlot(slot))) {
                n++;
            }
        }
        return n;
    }

    // 熱のたまりの倍率 (純粋な部分。自己テストが呼ぶ)
    public static double heatFactor(int pieces) {
        return 1.0 - HEAT_PER_PIECE * Math.max(0, Math.min(4, pieces));
    }

    public static boolean hasFullSet(Player player) {
        for (DeferredItem<Item> piece : PIECES) {
            if (player.getInventory().countItem(piece.get()) > 0) {
                continue;
            }
            boolean on = false;
            for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                on |= player.getItemBySlot(slot).is(piece.get());
            }
            if (!on) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isAshWalking(event.getCrafting())) {
            Tasks.later(2, () -> {
                if (hasFullSet(player)) {
                    Quests.signal(player, SIGNAL_GEAR_MADE);
                }
            });
        }
    }

    // ---------------------------------------------------------------- 煤の砥石 (SootWhetstoneItem から)
    // 岩・塩・雷の砥石の研ぎは煤の研ぎに替わる (重ねない)
    public static boolean applyWhetstone(ServerPlayer player) {
        player.removeEffect(TravelGear.WHETTED);
        player.removeEffect(Ch2Gear.SALT_WHETTED);
        player.removeEffect(Ch3Gear.THUNDER_WHETTED);
        player.addEffect(new MobEffectInstance(SOOT_WHETTED, WHETSTONE_TICKS, 0, false, true, true));
        Notice.hint(player, "message.skyabyss.soot_whetstone.used");
        Quests.signal(player, SIGNAL_WHETSTONE_USED);
        return true;
    }

    public static class SootWhettedEffect extends MobEffect {
        public SootWhettedEffect() {
            super(MobEffectCategory.BENEFICIAL, 0xD2551A);
            addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "effect.soot_whetted"), WHETSTONE_ATTACK,
                    AttributeModifier.Operation.ADD_VALUE);
        }
    }

    // ---------------------------------------------------------------- 灰焼きのパン
    public static class AshBreadItem extends Item {
        public AshBreadItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (entity instanceof ServerPlayer sp) {
                EmberHeat.cool(sp, BREAD_COOL);
            }
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skyabyss.ash_baked_bread.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    // ---------------------------------------------------------------- 旅装の品 (一章の鯨道の旅装と同じ型。着た姿は低ポリ)
    public static class AshWalkingArmorItem extends WhaleRoadArmorItem {
        public AshWalkingArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
            super(material, type, properties);
        }

        @Override
        public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
            consumer.accept(new GeoRenderProvider() {
                private jp.revati.skyabyss.story.ch4.client.AshWalkingArmorRenderer renderer;

                @Override
                @SuppressWarnings("removal")
                public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                        @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                    if (this.renderer == null) {
                        this.renderer = new jp.revati.skyabyss.story.ch4.client.AshWalkingArmorRenderer();
                    }
                    this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                    return this.renderer;
                }
            });
        }

        @Override
        public String dungeon() {
            return "ash_walking";
        }

        @Override
        public int requiredLevel() {
            return ROW.level();
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.ash_walking_armor.desc", false);
        }
    }
}
