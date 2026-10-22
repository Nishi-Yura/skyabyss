package jp.revati.skyabyss.story.ch4;

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

// 煤の砥石 (四章の砥石。21 の 1.2)。右クリックで使うと「煤の刃」(攻撃 +2.6、20 分。Ch4Gear.applyWhetstone。ほかの砥石の研ぎは替わる)。
// 煤の刃のあいだ、灰被りの屑・古い灰被りの殻へ入る傷が 1.5 倍 (殻の計算が Ch4Mobs.shellBonus で読む)
public class SootWhetstoneItem extends Item {
    public SootWhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (player instanceof ServerPlayer sp) {
            Ch4Gear.applyWhetstone(sp);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 0.9f);
            player.getCooldowns().addCooldown(this, 40);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
