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
public class TitanCallItem extends Item implements jp.revati.skyabyss.pouch.PouchUsable {
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
        if (player instanceof ServerPlayer server && !usePouch(server, () -> {
        })) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // 吹く (右クリックと、旅嚢の輪・短押しの共通の中身)。断られたら false
    @Override
    public boolean usePouch(ServerPlayer player, Runnable consumeOne) {
        // その巨神の背中の中で吹いたら、呼ばずに「地上へ下りる？」(もう一度で下りる)
        if (jp.revati.skyabyss.titan.end.ReturnBeacons.whistleInBack(player, kind.get().id())) {
            return true;
        }
        Voyages.CallResult result = Voyages.call(player, kind.get(), false);
        if (result == Voyages.CallResult.REFUSED) {
            return false;
        }
        if (result == Voyages.CallResult.CALLED) {
            player.getCooldowns().addCooldown(this, 60);
        }
        jp.revati.skyabyss.tutorial.TutorialHints.done(player, jp.revati.skyabyss.tutorial.TutorialHints.WHISTLE);
        return true;
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
        String key = getDescriptionId();
        tooltip.add(Component.translatable(key + ".desc").withStyle(s -> s.withColor(0x7FD6D0).withItalic(true)));
        tooltip.add(Component.translatable(key + ".use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key + ".rule").withStyle(ChatFormatting.DARK_GRAY));
    }
}
