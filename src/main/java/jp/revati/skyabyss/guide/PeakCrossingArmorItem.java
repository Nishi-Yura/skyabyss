package jp.revati.skyabyss.guide;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.client.armor.PeakCrossingArmorRenderer;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

// 峰越えの旅装 (三章の旅装。21 の 1.2)。防御 19 / 強靭 11 (4 部位の合計)、アンコモン、炉で鍛えない、必要 Lv 19 (GearCurve.TRAVEL[2])。
// 一章の鯨道の旅装 (WhaleRoadArmorItem) と同じ型を継ぐ。一式の効果「感電の傷 −25%」は部位ごとに −6.25% (Ch3Gear.shockFactor。着ている部位を数える)。
// 着たときの見た目は低ポリ (client/armor/PeakCrossingArmorRenderer)
public class PeakCrossingArmorItem extends WhaleRoadArmorItem {
    public PeakCrossingArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private PeakCrossingArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new PeakCrossingArmorRenderer();
                }
                this.renderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
                return this.renderer;
            }
        });
    }

    // 由来の行は「アンコモン · 峰越えの旅装」
    @Override
    public String dungeon() {
        return "peak_crossing";
    }

    @Override
    public int requiredLevel() {
        return Ch3Gear.ROW.level();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.peak_crossing_armor.desc", false);
    }
}
