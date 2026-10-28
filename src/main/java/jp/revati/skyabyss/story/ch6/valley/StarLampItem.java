package jp.revati.skyabyss.story.ch6.valley;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 星の灯 (星術「星灯り」。docs/design/46 の 4.2・4.3.1 = 道具)。手に持って右クリックで足もとに星の印を置き、まわり 6 を照らす。
// 同時に 2 つ・30 秒。職を問わず使え、技の枠を食わない。星の溜まり (野営) でもらう
public class StarLampItem extends Item {
    public static final int COOLDOWN = 30;

    public StarLampItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            StarLamps.place(sl, player.blockPosition(), player.getUUID());
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // 持ち物に星の灯があるか
    public static boolean carries(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof StarLampItem) {
                return true;
            }
        }
        return p.getOffhandItem().getItem() instanceof StarLampItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.star_lamp.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.star_lamp.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
