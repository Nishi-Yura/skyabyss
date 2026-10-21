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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

// 巨神の呼び笛 (docs/design/28)。出会った巨神の笛は、その巨神の「子」を足もとへ呼ぶ: 子が現れ、乗ると行き先を決める海図が開く。
// パーティ全員 (最大 6 人) が乗れる。成体の巨神は動かない。眠っている巨神の笛は応えない。
// 笛を持った人のそばに仲間の子がいれば、吹くとその子に乗る。乗っている間に吹くと、海図を開き直す。
// 巨神の背 (ダンジョン) の中で吹くと、地上へ下りる (ReturnBeacons)。吹く仕草はヤギの角笛と同じ。音は tools/gen_titan_travel_assets.py で合成した独自の音
public class TitanCallItem extends Item implements jp.revati.skyabyss.pouch.PouchUsable {
    private final String titanId;
    // 旅のできる巨神 (岩鯨・潮殻) の型 (記録・旅嚢の表示用)。雷翼・余燼は null
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
        boolean ok = jp.revati.skyabyss.titan.young.YoungCarriers.whistle(player, titanId);
        if (ok) {
            player.getCooldowns().addCooldown(this, 10);
            jp.revati.skyabyss.tutorial.TutorialHints.done(player, jp.revati.skyabyss.tutorial.TutorialHints.WHISTLE);
        }
        return ok;
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
