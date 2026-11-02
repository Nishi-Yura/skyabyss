package jp.revati.skyabyss.item.wild;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.wild.client.WildArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// 野の装備の防具 (docs/design/51 の 5 章。一式 4 部位)。着た姿は低ポリ (client/WildArmorRenderer が lowpoly/armor/<一式>/<部位>[_階級])。
// 一式の効果 (2 部位・4 部位) は WildArmorEvents
public class WildArmorItem extends ArmorItem implements WildGear, GeoItem {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    final WildGearTypes.ArmorSet set;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WildArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, WildGearTypes.ArmorSet set) {
        super(material, type, properties.durability(type.getDurability(40)));
        this.set = set;
    }

    public WildGearTypes.ArmorSet set() {
        return set;
    }

    @Override
    public String region() {
        return set.region();
    }

    @Override
    public int wildLevel() {
        return set.level();
    }

    @Override
    public GearTier tier() {
        return set.tier();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private WildArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new WildArmorRenderer(set.id());
                }
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return super.canEquip(stack, armorType, entity) && GearLevel.usable(entity, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!GearLevel.meets(player, stack)) {
            if (!level.isClientSide) {
                GearLevel.denyMessage(player, stack);
            }
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss." + set.id() + ".desc", true);
    }

    // 身につけているこの一式の数
    public static int piecesWorn(LivingEntity entity, WildGearTypes.ArmorSet set) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof WildArmorItem a && a.set == set
                    && (!(entity instanceof Player p) || GearLevel.meets(p, entity.getItemBySlot(slot)))) {
                count++;
            }
        }
        return count;
    }

    // 身につけている野の一式 (いちばん多く着ている一式と、その数)。一式は一度に一つだけ効く (2 と 4 は同じ一式)
    public static java.util.Map<WildGearTypes.ArmorSet, Integer> worn(LivingEntity entity) {
        java.util.Map<WildGearTypes.ArmorSet, Integer> m = new java.util.HashMap<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = entity.getItemBySlot(slot);
            if (s.getItem() instanceof WildArmorItem a && (!(entity instanceof Player p) || GearLevel.meets(p, s))) {
                m.merge(a.set, 1, Integer::sum);
            }
        }
        return m;
    }

}
