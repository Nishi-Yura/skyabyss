package jp.revati.skyabyss.guide;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.tide.TideOcean;
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

// 潮の刻みの砂時計 (二章の道具。消耗しない)。右クリックで、ワールドの潮 (TideOcean。人ごとの見かけではない) の
// 次の干潮・満潮までの秒数を画面の帯 (Notice.bar) に一度だけ出す。クールダウン 30 秒。潮見の貝と対にして、背中の潮の時計の予習。
// 満潮まで = 干潮の刻の終わりまで (今が干潮ならその刻の、そうでなければ次の刻の)。TideOcean の刻の長さは読めないので、isLowTide で一歩ずつ進めて数える
public class TideHourglassItem extends Item {
    // 数える上限 (干潮の刻の周期の 2 回分)。これを超えたら潮が止まっている (読めない)
    static final long SCAN_LIMIT = 24000L * 2;

    // low = 今が干潮の刻。toLow = 次の干潮の刻の始まりまで (干潮なら 0)。toHigh = 満ち潮 (その干潮の刻の終わり) まで。読めなければ toHigh = -1
    public record Reading(boolean low, long toLow, long toHigh) {
        public int secondsToLow() {
            return (int) Math.ceil(toLow / 20.0);
        }

        public int secondsToHigh() {
            return (int) Math.ceil(toHigh / 20.0);
        }
    }

    public TideHourglassItem(Properties properties) {
        super(properties);
    }

    // 純粋な部分 (自己テストが直接呼ぶ)
    public static Reading read(long dayTime) {
        boolean low = TideOcean.isLowTide(dayTime);
        long toLow = low ? 0 : TideOcean.ticksUntilLowTide(dayTime);
        long t = toLow;
        while (t < SCAN_LIMIT && TideOcean.isLowTide(dayTime + t)) {
            t++;
        }
        return new Reading(low, toLow, t >= SCAN_LIMIT || t == toLow ? -1 : t);
    }

    public static Component message(Reading r) {
        if (r.toHigh() < 0) {
            return Component.translatable("message.skyabyss.tide_hourglass.still");
        }
        if (r.low()) {
            return Component.translatable("message.skyabyss.tide_hourglass.low", r.secondsToHigh());
        }
        return Component.translatable("message.skyabyss.tide_hourglass.high", r.secondsToLow(), r.secondsToHigh());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (player instanceof ServerPlayer sp) {
            Reading r = read(sp.server.overworld().getDayTime());
            Notice.bar(sp, message(r));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.6f, 1.4f);
            player.getCooldowns().addCooldown(this, Ch2Gear.HOURGLASS_COOLDOWN);
            Quests.signal(sp, Ch2Gear.SIGNAL_TIDE_READ);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.tide_hourglass.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
