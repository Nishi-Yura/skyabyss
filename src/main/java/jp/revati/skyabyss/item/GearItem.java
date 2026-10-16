package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 鍛えることのない装飾品 (苔の護石、岩鯨の補強板)。装飾の枠 (item/accessory) に付けているあいだ働く。効果は GearEvents が見る。
// 手で右クリックすると枠へ付ける
public class GearItem extends Item implements ModGear, Accessory {
    private final GearTier tier;
    private final String descKey;
    private final boolean foil;

    public GearItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties);
        this.tier = tier;
        this.descKey = descKey;
        this.foil = foil;
    }

    @Override
    public GearTier tier() {
        return tier;
    }

    @Override
    public boolean enhanceable() {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier);
    }

    @Override
    public AccessoryKind accessoryKind() {
        return AccessoryKind.TRINKET;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && !Accessories.equipFromHand(sp, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier, descKey, false);
    }
}
