package jp.revati.skyabyss.tower.gear;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.tower.gear.client.TowerArmorRenderer;
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

// 塔の防具 (docs/design/54 の 2.6。ヒロイック・必要 Lv 50、一式 4 部位)。天秤の甲冑 (重)・渡りの装束 (軽)・光輪の衣 (術)。
// 着た姿は低ポリ (client/TowerArmorRenderer が lowpoly/armor/<一式>/<部位>_legend)。4 部位の効果は TowerGearEvents
public class TowerArmorItem extends ArmorItem implements ModGear, GeoItem {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    final String set;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TowerArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String set) {
        super(material, type, properties.durability(type.getDurability(48)));
        this.set = set;
    }

    public String set() {
        return set;
    }

    @Override
    public GearTier tier() {
        return GearTier.HEROIC;
    }

    @Override
    public String dungeon() {
        return TowerGear.DUNGEON;
    }

    @Override
    public int requiredLevel() {
        return TowerGear.REQUIRED_LEVEL;
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
            private TowerArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new TowerArmorRenderer(set);
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
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss." + set + ".desc", true);
    }

    // 身につけているこの一式の数 (必要 Lv に届いている部位だけ)
    public static int piecesWorn(LivingEntity entity, String set) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = entity.getItemBySlot(slot);
            if (s.getItem() instanceof TowerArmorItem a && a.set.equals(set) && (!(entity instanceof Player p) || GearLevel.meets(p, s))) {
                count++;
            }
        }
        return count;
    }
}
