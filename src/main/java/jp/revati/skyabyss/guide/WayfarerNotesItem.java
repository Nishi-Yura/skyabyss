package jp.revati.skyabyss.guide;

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

// 旅の覚書 (WayfarerNotes)。右クリックで開く (クライアントは手帳の画面、サーバーはその章のレシピを解く)
public class WayfarerNotesItem extends Item {
    public WayfarerNotesItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.skyabyss.wayfarer_notes.named", WayfarerNotes.chapterName(WayfarerNotes.chapterOf(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int chapter = WayfarerNotes.chapterOf(stack);
        if (level.isClientSide) {
            jp.revati.skyabyss.guide.client.WayfarerNotesClient.open(chapter);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.wayfarer_notes.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
