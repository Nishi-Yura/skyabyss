package jp.revati.skyabyss.titan.travel;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

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

// 巨神の呼び笛。出会った巨神 (TitanCalls) は、いつでもこれで呼べる: 巨神が吹いた人の近くへ来て、20 分 (TitanStay) 背へ乗れる姿で待つ
// (昼夜・潮・嵐の刻に関わらない。眠っている巨神は応えない)。
// 旅のできる巨神 (岩鯨・潮殻) は、心臓を倒したあとの笛が旅の呼び (迎えに来て、行き先まで運ぶ)。しゃがんで吹くと、旅でなく背へ乗るための呼びになる。
// 乗り場の上で吹くと、海図を開き直す。吹く仕草はヤギの角笛と同じ。音は tools/gen_titan_travel_assets.py で合成した独自の音
public class TitanCallItem extends Item implements jp.revati.skyabyss.pouch.PouchUsable {
    private final String titanId;
    // 旅のできる巨神 (岩鯨・潮殻) だけ。雷翼・余燼は null (旅はない。背へ乗る呼びだけ)
    @Nullable
    private final Supplier<TitanKind> kind;

    public TitanCallItem(String titanId, @Nullable Supplier<TitanKind> kind, Properties properties) {
        super(properties);
        this.titanId = titanId;
        this.kind = kind;
    }

    // 巨神の名前 (Titans の名前)
    public String titanId() {
        return titanId;
    }

    // 旅のできる巨神なら、その型。なければ null
    @Nullable
    public TitanKind kind() {
        return kind == null ? null : kind.get();
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
        if (jp.revati.skyabyss.titan.end.ReturnBeacons.whistleInBack(player, titanId)) {
            return true;
        }
        TitanKind travel = kind();
        // 旅の呼び: 心臓を倒して認められた人が、しゃがまずに吹いたとき (旅の最中の人は、乗り場の上の吹き直しも旅の呼び)
        if (travel != null && (Voyages.involving(player.getUUID()) != null
                || (!player.isShiftKeyDown() && Voyages.isUnlocked(player, travel)))) {
            Voyages.CallResult result = Voyages.call(player, travel, false);
            if (result == Voyages.CallResult.REFUSED) {
                return false;
            }
            if (result == Voyages.CallResult.CALLED) {
                player.getCooldowns().addCooldown(this, 60);
            }
            jp.revati.skyabyss.tutorial.TutorialHints.done(player, jp.revati.skyabyss.tutorial.TutorialHints.WHISTLE);
            return true;
        }
        // 背へ乗るための呼び: いつでも、出会った巨神を近くへ呼ぶ
        TitanCalls.Result result = TitanCalls.call(player, titanId, false);
        if (result == TitanCalls.Result.REFUSED) {
            return false;
        }
        if (result == TitanCalls.Result.CALLED) {
            player.getCooldowns().addCooldown(this, 100);
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
