package jp.revati.skyabyss.titan.travel;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

// 巨神の呼び笛。吹くと (右クリック)、認めてくれた巨神が迎えに来る。乗り場の上で吹くと、海図を開き直す。
// 吹く仕草はヤギの角笛と同じ。音は tools/gen_titan_travel_assets.py で合成した独自の音
public class TitanCallItem extends Item {
    private final Supplier<TitanKind> kind;

    public TitanCallItem(Supplier<TitanKind> kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public TitanKind kind() {
        return kind.get();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            Voyages.CallResult result = Voyages.call(server, kind.get(), false);
            if (result == Voyages.CallResult.REFUSED) {
                return InteractionResultHolder.fail(stack);
            }
            if (result == Voyages.CallResult.CALLED) {
                player.getCooldowns().addCooldown(this, 60);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 30;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.stone_whale_call.desc").withStyle(s -> s.withColor(0x7FD6D0).withItalic(true)));
        tooltip.add(Component.translatable("item.skyabyss.stone_whale_call.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.stone_whale_call.rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
