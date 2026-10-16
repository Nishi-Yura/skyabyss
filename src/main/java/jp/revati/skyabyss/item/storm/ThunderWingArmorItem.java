package jp.revati.skyabyss.item.storm;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.storm.client.ThunderWingArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

// 雷翼の羽衣 (レア、4 部位)。防御 3/8/7/3、強靭 3.5。一式の効果 (2 部位で落下の傷 −50%、4 部位で嵐の衣と空駆け) は StormGearEvents。
// 着たときの見た目は GeckoLib の 3D モデル (client/ThunderWingArmorRenderer)。潮殻の鎧 (TideShellArmorItem) と同じ形
public class ThunderWingArmorItem extends ArmorItem implements ModGear, GeoItem {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ThunderWingArmorItem(Type type, Properties properties) {
        super(StormGearSetup.MATERIAL, type, properties.durability(type.getDurability(35)));
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
            private ThunderWingArmorRenderer renderer;

            @Override
            @SuppressWarnings("removal")
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                    @Nullable EquipmentSlot equipmentSlot, HumanoidModel<T> original) {
                if (this.renderer == null) {
                    this.renderer = new ThunderWingArmorRenderer();
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
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public String dungeon() {
        return StormGear.TITAN;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.thunder_wing_armor.desc", true);
    }

    // 身につけている雷翼の羽衣の数 (必要 Lv に届かない品は着られないので数えなくてよい)
    public static int piecesWorn(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof ThunderWingArmorItem) {
                count++;
            }
        }
        return count;
    }
}
