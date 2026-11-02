package jp.revati.skyabyss.story.ch6.field;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 月の砂時計 (星術「一瞬止め」の道具。docs/design/46 の 4.5)。右クリックで 2 秒、まわりの敵と飛び物と満ち引きの石を止める (MomentStop)。
// 使い直しは 12 秒。職を問わず使え、技の枠を食わない。満月の野の月の溜まり (野営) でもらう
public class MoonHourglassItem extends Item {
    public MoonHourglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (MomentStop.cast(sp) < 0) {
                return InteractionResultHolder.fail(stack);
            }
            player.getCooldowns().addCooldown(this, MomentStop.COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // 持ち物に砂時計があるか
    public static boolean carries(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof MoonHourglassItem) {
                return true;
            }
        }
        return p.getOffhandItem().getItem() instanceof MoonHourglassItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.moon_hourglass.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.moon_hourglass.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
