package jp.revati.skyabyss.titan.tide.gear;

import java.util.List;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 珊瑚の盾 (レア): 潮殻の背の珊瑚を編んだ盾。バニラの盾と同じに正面の傷を防ぐうえ (左手で構える)、
//  - 構えている間、防げない向き (横・後ろ) からの傷を 10% 減らす (+1%/段、上限 20%)
//  - 近接で防いだ相手に珊瑚の棘 2 (+0.2/段)。+5 から棘の相手は鈍足 I 2 秒
// 効果は TideGearEvents。特性は防具の種類で、左手に持っているあいだ効く (AffixEvents)。必要 Lv に届かないと構えられない
public class CoralShieldItem extends ShieldItem implements ModGear {
    public CoralShieldItem(Properties properties) {
        super(properties.durability(TideGear.SHIELD_DURABILITY));
    }

    public static double guard(ItemStack stack) {
        return Math.min(0.20, TideGear.SHIELD_GUARD + TideGear.SHIELD_GUARD_PER_LEVEL * ModGear.enhance(stack));
    }

    public static float thorns(ItemStack stack) {
        return (float) (TideGear.SHIELD_THORNS + TideGear.SHIELD_THORNS_PER_LEVEL * ModGear.enhance(stack));
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
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(TideGearSetup.shard());
    }

    @Override
    public GearTier tier() {
        return GearTier.EPIC;
    }

    @Override
    public String dungeon() {
        return TideGear.TITAN;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.coral_shield.desc", true);
    }
}
