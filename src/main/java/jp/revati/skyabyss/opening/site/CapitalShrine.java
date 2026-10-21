package jp.revati.skyabyss.opening.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;

// 四巨神の社の鍵の間 (docs/design/33 の 2.2、00 の 56): 社が開いたあと、巨神の石 (3×3 の柱) に触れると、
// その巨神の欠片を持っていれば「石のほうへ引かれる」、二つ以上なら一度だけ「互いに引き合う」と語る。持っていなければ冷たい。世界は変えない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CapitalShrine {
    private CapitalShrine() {
    }

    static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
    private static final String PAIR_TAG = "skyabyss_shrine_pair";

    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || event.getLevel().dimension() != Level.OVERWORLD
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SiteData.Entry e = SiteData.get(player.serverLevel()).first("shelter");
        if (e == null || !CapitalDoors.opened(player.server, "shrine")) {
            return;
        }
        BlockPos hit = event.getPos();
        for (String titan : TITANS) {
            BlockPos s = e.extra("stone_" + titan);
            if (s == null || Math.abs(hit.getX() - s.getX()) > 1 || Math.abs(hit.getZ() - s.getZ()) > 1 || hit.getY() < s.getY() || hit.getY() > s.getY() + 9) {
                continue;
            }
            touch(player, titan, s);
            event.setCanceled(true);
            return;
        }
    }

    static void touch(ServerPlayer player, String titan, BlockPos stone) {
        if (!Progression.stopped(player.server, player.getUUID(), titan)) {
            Notice.hint(player, "message.skyabyss.capital.stone.cold");
            return;
        }
        player.serverLevel().playSound(null, stone, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f);
        int held = 0;
        for (String t : TITANS) {
            if (Progression.stopped(player.server, player.getUUID(), t)) {
                held++;
            }
        }
        if (held >= 2 && !player.getPersistentData().getBoolean(PAIR_TAG)) {
            player.getPersistentData().putBoolean(PAIR_TAG, true);
            Notice.narrate(player, "message.skyabyss.capital.stone.pair");
            player.serverLevel().playSound(null, stone, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 0.6f);
            return;
        }
        Notice.narrate(player, "message.skyabyss.capital.stone.pull");
    }
}
