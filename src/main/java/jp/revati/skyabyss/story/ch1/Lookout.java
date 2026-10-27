package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningBeats;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 見張りの丘 (E9): 高台の石の台に、真鍮の筒 (避雷針を寝かせたもの) が据えてある。右クリックで覗く。
// 夕方 (一日の時刻 10500〜13000)・空が荒れていないとき、遠い雲に「雲より暗い縁」が見える。岩鯨の方角と、「大きさ」を比べるものなしで先に見せる。
// 岩鯨が出ていればその方角、まだなら世界ごとに決まった方角 (岩鯨の出現は 3 日目の夕方より前)。頁 ch1/lookout が記される
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Lookout {
    static final int FROM = 10500;
    static final int TO = 13000;
    static final double NEAR = 5.0;

    private Lookout() {
    }

    // 夕方か (一日の時刻。純粋)
    public static boolean dusk(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= FROM && t <= TO;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.LIGHTNING_ROD) || !atLookout(level, pos)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        peer(player, level, pos);
    }

    // pos が見張りの丘の筒か (登録された場所の近く)
    static boolean atLookout(ServerLevel level, BlockPos pos) {
        for (ChapterSites.Site s : ChapterSites.sites(level.getServer(), Sites.LOOKOUT)) {
            if (Math.hypot(s.pos().getX() - pos.getX(), s.pos().getZ() - pos.getZ()) <= NEAR && Math.abs(s.pos().getY() - pos.getY()) <= 6) {
                return true;
            }
        }
        return false;
    }

    // 覗く。夕方なら縁が見える。覗けたら true
    static boolean peer(ServerPlayer player, ServerLevel level, BlockPos scope) {
        if (!dusk(level.getDayTime()) || level.isThundering()) {
            Notice.narrate(player, "message.skyabyss.ch1.lookout.wait");
            return false;
        }
        Component dir = Component.translatable("message.skyabyss.guide.dir." + OpeningBeats.compass(scope, edgeToward(level, scope)));
        Notice.narrate(player, "message.skyabyss.ch1.lookout.edge", dir);
        Tasks.later(90, () -> Notice.narrate(player, "message.skyabyss.ch1.lookout.size"));
        Tasks.later(160, () -> Lore.unlock(player, Ch1.LOOKOUT, true));
        return true;
    }

    // 縁の見える方角の先の一点 (岩鯨が出ていればその位置、いなければ世界ごとに決まった方角の 400 ブロック先)
    static BlockPos edgeToward(ServerLevel level, BlockPos from) {
        StoneWhaleEntity whale = WhaleKeeper.current(level.getServer().overworld());
        if (whale != null) {
            return whale.blockPosition();
        }
        BlockPos last = WhaleKeeper.lastKnown(level.getServer().overworld());
        if (last != null) {
            return last;
        }
        long seed = level.getSeed();
        double angle = Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 20, 8L) * (Math.PI / 4.0);
        return from.offset((int) Math.round(Math.cos(angle) * 400), 0, (int) Math.round(Math.sin(angle) * 400));
    }
}
