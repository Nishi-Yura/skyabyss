package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.ModText;
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

// 破れた頁。右クリックで読むと、出どころ (村 / 岩鯨の背) の伝承のうち、まだ記されていないものが一つ書に移る。
// どの項目になるかは読んだ人ごとに選ぶので、同じ宝箱の頁でも、読む人が違えば違う話になる。
// もう知っていることしか書かれていなければ、頁は手元に残る
public class LorePageItem extends Item {
    public LorePageItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(String source) {
        ItemStack stack = new ItemStack(LoreSetup.LORE_PAGE.get());
        stack.set(LoreSetup.PAGE_SOURCE.get(), source);
        return stack;
    }

    public static String source(ItemStack stack) {
        String source = stack.get(LoreSetup.PAGE_SOURCE.get());
        return source == null ? "" : source;
    }

    // この人がまだ記していない、その出どころの頁の項目 (出どころが空の頁は、どこの頁でもよい)
    public static List<LoreEntry> candidates(ServerPlayer player, String source) {
        LoreState state = Lore.state(player);
        List<LoreEntry> list = new ArrayList<>();
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            if (!entry.unlockable() || state.has(entry.id())) {
                continue;
            }
            for (LoreTrigger t : entry.unlock()) {
                if (t.type().equals("page") && (source.isEmpty() || t.target().equals(source))) {
                    list.add(entry);
                    break;
                }
            }
        }
        return list;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(stack);
        }
        List<LoreEntry> choices = candidates(server, source(stack));
        if (choices.isEmpty()) {
            server.sendSystemMessage(ModText.hint("message.skyabyss.lore.page_known"));
            return InteractionResultHolder.fail(stack);
        }
        LoreEntry picked = choices.get(level.random.nextInt(choices.size()));
        Lore.unlock(server, picked.id(), true);
        server.sendSystemMessage(ModText.lore("message.skyabyss.lore.page_read"));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.9f);
        stack.consume(1, player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String source = source(stack);
        String key = source.isEmpty() ? "item.skyabyss.lore_page.desc" : "item.skyabyss.lore_page.desc." + source;
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
