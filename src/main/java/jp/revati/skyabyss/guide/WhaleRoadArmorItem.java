package jp.revati.skyabyss.guide;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.client.armor.WhaleRoadArmorRenderer;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
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

// 鯨道の旅装 (一章の旅装)。防御 17 / 強靭 5 (4 部位の合計)、アンコモン (緑、特性なし、炉で鍛えない)、必要 Lv 7。
// 鍛冶場の誂えで手に入る。着たときの見た目は低ポリの 3D (client/armor/WhaleRoadArmorRenderer)。
// 踏ん張り (ノックバック耐性 0.1) は素材 (TravelGear.MATERIAL) の値。二〜四章の旅装は同じ型で足す (GearCurve.TRAVEL の表)
public class WhaleRoadArmorItem extends ArmorItem implements ModGear, GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WhaleRoadArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties.durability(type.getDurability(TravelGear.DURABILITY_FACTOR)));
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
            private WhaleRoadArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new WhaleRoadArmorRenderer();
                }
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    // ---------------------------------------------------------------- ModGear
    @Override
    public GearTier tier() {
        return GearTier.UNCOMMON;
    }

    // 由来は巨神ではなく「鯨道の旅装」(ツールチップの由来の行。必要 Lv は下で決める。炉の分解の素材は岩鯨の鱗片にそろう)
    @Override
    public String dungeon() {
        return "whale_road";
    }

    // 炉で鍛えない・特性は付かない (アンコモン)
    @Override
    public boolean enhanceable() {
        return false;
    }

    @Override
    public int requiredLevel() {
        return GearCurve.TRAVEL.get(0).level();
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
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.whale_road_armor.desc", false);
    }
}
