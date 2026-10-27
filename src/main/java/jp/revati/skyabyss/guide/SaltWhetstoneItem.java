package jp.revati.skyabyss.guide;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 塩の砥石 (二章の砥石)。右クリックで使うと「塩の刃」(攻撃 +1.8) が 20 分。一つ使う。岩の砥石の研ぎとは重ねない (替わる)。
// 水の中でも切れる: 水の中の一撃は塩が散って +1 (Ch2Gear.onIncoming)。備えの刃は持ち歩いているだけで数える
public class SaltWhetstoneItem extends Item {
    public SaltWhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (player instanceof ServerPlayer sp) {
            Ch2Gear.applyWhetstone(sp);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1.5f);
            player.getCooldowns().addCooldown(this, 40);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.salt_whetstone.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
