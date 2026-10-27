package jp.revati.skyabyss.titan.end;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.group.FirstRun;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

// 追憶の宝の、巨神ごとの行き先 (版 30)。任意の翼の宝は、その巨神の初回の心臓の報いへ移した。
// 心臓を止めたとき、戦った人それぞれに一度ずつ渡す (人ごとに一度きり。FirstRun.grantKeepsake)。
// 翼をなくした巨神 (岩鯨・雷翼・余燼) には、ここの品はない
public final class HeartKeepsakes {
    public static final String TIDE_SUMMIT = "tide_summit";

    private HeartKeepsakes() {
    }

    // TitanEnd.onHeartStopped から。ids = 戦った人 (参加者)
    public static void grant(ServerLevel back, String titan, Collection<UUID> ids) {
        if (!Titans.TIDE_SHELL.equals(titan)) {
            return;
        }
        for (UUID id : ids) {
            ServerPlayer player = back.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                tideSummit(player);
            }
        }
    }

    // 潮殻の初回の心臓の報い: 鸚鵡貝と共鳴の欠片 (備えの品と素材だけ)
    private static void tideSummit(ServerPlayer player) {
        List<ItemStack> items = List.of(new ItemStack(jp.revati.skyabyss.guide.Ch2Gear.SMALL_PEARL.get(), 2),
                new ItemStack(ModItems.RESONANCE_SHARD_TIDE_SHELL.get(), 1));
        if (FirstRun.grantKeepsake(player, TIDE_SUMMIT, items)) {
            Notice.unlock(player, "message.skyabyss.tide.keepsake_given");
            ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 1.2f);
        } else {
            Notice.bar(player, ModText.hint("message.skyabyss.tide.keepsake_taken"));
        }
    }
}
