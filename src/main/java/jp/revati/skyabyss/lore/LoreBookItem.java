package jp.revati.skyabyss.lore;

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

// 伝承の書。右クリックで開く。中身は持ち主ごとの記録 (書そのものには何も書き込まない)。
// だから、なくしても次に何かが記されたとき荷物の中に戻ってくる (Lore.ensureBook)
public class LoreBookItem extends Item implements jp.revati.skyabyss.pouch.PouchUsable {
    public LoreBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            jp.revati.skyabyss.lore.client.LoreClient.openBook();
        } else if (player instanceof ServerPlayer server) {
            // 開く前に最新の記録を送っておく (データパックを入れ替えた直後など)
            Lore.sync(server);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // 旅嚢の輪から開く: 記録を送り、持ち主の画面に書を開かせる (右クリックはクライアントで直接開く)
    @Override
    public boolean usePouch(ServerPlayer player, Runnable consumeOne) {
        Lore.sync(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new jp.revati.skyabyss.pouch.PouchNet.OpenBook());
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.lore_book.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
