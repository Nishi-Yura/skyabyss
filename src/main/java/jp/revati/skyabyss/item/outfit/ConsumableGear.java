package jp.revati.skyabyss.item.outfit;

import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 置き換えの第 2 段 (2026-10-27): バニラの薬 (ポーション・牛乳)・食べ物 (パン・魚・蜂蜜・肉)・矢の代わり。絵は選定の案 (tools/rough/icons_phase2.py)。
//   薬    飲むと効く (バニラの薬と同じ効き目と長さ)。空き瓶は残さない (使い道のない品を渡さない)
//   毒消し  悪い効き目 (毒・弱り・鈍足・吐き気など) だけを消す (牛乳と違い、良い効き目は残す)
//   食べ物  バニラの同じ品と同じ栄養 (おなかは空かないので、戻る体力の量。rpg/NoHunger)
//   矢    旅の矢 = ふつうの矢、光の矢 = 当たった相手が光る矢。拾うとこの品に戻る。飛ぶ姿は mod の絵 (ModArrow)
public final class ConsumableGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 飲み薬: 飲むと effects を受ける
    public static class DraughtItem extends Item {
        private final Consumer<LivingEntity> effect;

        public DraughtItem(Properties properties, Consumer<LivingEntity> effect) {
            super(properties.stacksTo(16));
            this.effect = effect;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.DRINK;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 32;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            if (!level.isClientSide) {
                effect.accept(entity);
            }
            if (!(entity instanceof Player p) || !p.isCreative()) {
                stack.shrink(1);
            }
            return stack;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    // 説明の一行だけ足す食べ物・矢
    public static class DescItem extends Item {
        public DescItem(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    private static Consumer<LivingEntity> give(Holder<MobEffect> effect, int ticks, int amp) {
        return e -> e.addEffect(new MobEffectInstance(effect, ticks, amp));
    }

    // 悪い効き目だけ消す
    static void cure(LivingEntity e) {
        for (MobEffectInstance m : List.copyOf(e.getActiveEffects())) {
            if (m.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                e.removeEffect(m.getEffect());
            }
        }
    }

    private static DeferredItem<Item> draught(String id, Consumer<LivingEntity> effect) {
        return ITEMS.register(id, () -> new DraughtItem(new Item.Properties(), effect));
    }

    public static final DeferredItem<Item> HEALING = draught("healing_draught", give(MobEffects.HEAL, 1, 0));
    public static final DeferredItem<Item> STRONG_HEALING = draught("strong_healing_draught", give(MobEffects.HEAL, 1, 1));
    public static final DeferredItem<Item> REGEN = draught("regen_draught", give(MobEffects.REGENERATION, 20 * 45, 0));
    public static final DeferredItem<Item> FIRE_WARD = draught("fire_ward_draught", give(MobEffects.FIRE_RESISTANCE, 20 * 180, 0));
    public static final DeferredItem<Item> NIGHT_EYE = draught("night_eye_draught", give(MobEffects.NIGHT_VISION, 20 * 180, 0));
    public static final DeferredItem<Item> WATER_BREATH = draught("water_breath_draught", give(MobEffects.WATER_BREATHING, 20 * 180, 0));
    public static final DeferredItem<Item> ANTIDOTE = draught("antidote", ConsumableGear::cure);

    private static DeferredItem<Item> food(String id, int nutrition, float saturation) {
        return ITEMS.register(id, () -> new DescItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    public static final DeferredItem<Item> TOWN_BREAD = food("town_bread", 5, 0.6f);
    public static final DeferredItem<Item> DRIED_FISH = food("dried_fish", 5, 0.6f);
    public static final DeferredItem<Item> SMOKED_MEAT = food("smoked_meat", 8, 0.8f);
    public static final DeferredItem<Item> HONEY_JAR = ITEMS.register("honey_jar", () -> new DraughtItem(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1f).build()), e -> e.removeEffect(MobEffects.POISON)) {
        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            // 食べ物として食べる (栄養) うえで、毒を消す
            if (!level.isClientSide) {
                entity.removeEffect(MobEffects.POISON);
            }
            return entity.eat(level, stack);
        }
    });

    // 飛ぶ矢 (絵は textures/entity/travel_arrow.png・glow_arrow.png。client/ArrowLooks)。拾うと元の品に戻る
    public static class ModArrow extends net.minecraft.world.entity.projectile.AbstractArrow {
        private final boolean glow;

        public ModArrow(net.minecraft.world.entity.EntityType<? extends ModArrow> type, Level level) {
            super(type, level);
            this.glow = type == GLOW_ARROW_ENTITY.get();
        }

        ModArrow(net.minecraft.world.entity.EntityType<? extends ModArrow> type, LivingEntity owner, Level level, ItemStack pickup, @javax.annotation.Nullable ItemStack weapon) {
            super(type, owner, level, pickup, weapon);
            this.glow = type == GLOW_ARROW_ENTITY.get();
        }

        @Override
        protected ItemStack getDefaultPickupItem() {
            return new ItemStack(glow ? GLOW_ARROW.get() : TRAVEL_ARROW.get());
        }

        // 光の矢: 当たった相手が 10 秒光る (バニラの光の矢と同じ)
        @Override
        protected void doPostHurtEffects(LivingEntity living) {
            super.doPostHurtEffects(living);
            if (glow) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), this.getEffectSource());
            }
        }
    }

    // 矢の品: 射ると ModArrow を飛ばす
    public static class ModArrowItem extends ArrowItem {
        private final boolean glow;

        public ModArrowItem(Properties properties, boolean glow) {
            super(properties);
            this.glow = glow;
        }

        @Override
        public net.minecraft.world.entity.projectile.AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @javax.annotation.Nullable ItemStack weapon) {
            return new ModArrow(glow ? GLOW_ARROW_ENTITY.get() : TRAVEL_ARROW_ENTITY.get(), shooter, level, ammo.copyWithCount(1), weapon);
        }

        @Override
        public net.minecraft.world.entity.projectile.Projectile asProjectile(Level level, net.minecraft.core.Position pos, ItemStack stack, net.minecraft.core.Direction direction) {
            ModArrow arrow = new ModArrow(glow ? GLOW_ARROW_ENTITY.get() : TRAVEL_ARROW_ENTITY.get(), level);
            arrow.setPos(pos.x(), pos.y(), pos.z());
            arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
            return arrow;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    private static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, SkyAbyss.MODID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<ModArrow>> TRAVEL_ARROW_ENTITY =
            ENTITIES.register("travel_arrow", () -> net.minecraft.world.entity.EntityType.Builder.<ModArrow>of(ModArrow::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20).build(SkyAbyss.MODID + ":travel_arrow"));
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<ModArrow>> GLOW_ARROW_ENTITY =
            ENTITIES.register("glow_arrow", () -> net.minecraft.world.entity.EntityType.Builder.<ModArrow>of(ModArrow::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5f, 0.5f).eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20).build(SkyAbyss.MODID + ":glow_arrow"));

    public static final DeferredItem<Item> TRAVEL_ARROW = ITEMS.register("travel_arrow", () -> new ModArrowItem(new Item.Properties(), false));
    public static final DeferredItem<Item> GLOW_ARROW = ITEMS.register("glow_arrow", () -> new ModArrowItem(new Item.Properties(), true));

    private ConsumableGear() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        // 備えの三灯 (readiness): 癒しの薬は即時回復と同じ点 (4 / 8)、再生の薬は再生 45 秒の点
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() ->
                jp.revati.skyabyss.readiness.Readiness.registerProvision(stack -> stack.is(HEALING.get()) ? 4 : stack.is(STRONG_HEALING.get()) ? 8
                        : stack.is(REGEN.get()) ? 20 * 45 / 50.0 : 0)));
    }
}
