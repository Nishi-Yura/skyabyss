package jp.revati.skyabyss.guide;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Suppliers;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.armor.TideWaderArmorRenderer;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

// 潮渡りの旅装 (二章の旅装。21 の 1.2)。防御 18 / 強靭 9 (4 部位の合計)、アンコモン、炉で鍛えない、必要 Lv 13 (GearCurve.TRAVEL[1])。
// 一章の鯨道の旅装 (WhaleRoadArmorItem) と同じ型を継ぐ (炉が受け付けない判定 ForgeRules もそのまま効く)。
// 一式の効果「水中の移動速度の低下を軽くする」は、部位ごとの水中移動効率 +0.1 (4 部位で 0.4)。着たときの見た目は低ポリ (client/armor/TideWaderArmorRenderer)
public class TideWaderArmorItem extends WhaleRoadArmorItem {
    private final java.util.function.Supplier<ItemAttributeModifiers> modifiers;

    public TideWaderArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.modifiers = Suppliers.memoize(() -> super.getDefaultAttributeModifiers().withModifierAdded(Attributes.WATER_MOVEMENT_EFFICIENCY,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_wader_water." + type.getName()), Ch2Gear.WATER_EFFICIENCY,
                        AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.bySlot(type.getSlot())));
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return modifiers.get();
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private TideWaderArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new TideWaderArmorRenderer();
                }
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    // 由来の行は「アンコモン · 潮渡りの旅装」
    @Override
    public String dungeon() {
        return "tide_wader";
    }

    @Override
    public int requiredLevel() {
        return Ch2Gear.ROW.level();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.tide_wader_armor.desc", false);
    }
}
