package jp.revati.skyabyss.story;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 天の鍵 (1 章の終わり、ベータの終わり)。docs/design/14_beta.md の 4 章。
// 四体の巨神の心臓を自分で止め (段階 4)、天の鍵の欠片を四つ持っていると、欠片が一つの鍵になる (1 秒ごとに見る)。
// - 欠片 4 つを取り、天の鍵を渡す (入らなければ足もと。本人だけが拾える)
// - 伝承「天の鍵」(chapter1/sky_key_whole) を記し、クエストへ合図 sky_key_assembled を送る
// - その人の画面に場面 (SkyKeyScene) を出す。鍵は空を指さず、足もとへ脈を打つ (2 章の深淵の心臓へのほのめかし)
// - ワールドで最初なら、全員に一行
// 欠片の数え方: 欠片はどの巨神のものも同じ品なので、「止めた心臓の数」(段階) と「持っている数」の両方で見る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkyKey {
    public static final String LORE = "chapter1/sky_key_whole";
    public static final String SIGNAL = "sky_key_assembled";
    public static final int FRAGMENTS = ProgressRules.TITAN_ORDER.size();

    private SkyKey() {
    }

    public static boolean assembled(ServerPlayer player) {
        return StoryData.get(player.server).assembled(player.getUUID());
    }

    public static int fragmentsHeld(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.SKY_KEY_FRAGMENT.get());
    }

    // 鍵がそろう条件 (記録はまだ、四つの心臓を止めた、欠片を四つ持っている)
    public static boolean ready(ServerPlayer player) {
        return !assembled(player) && Progression.stage(player) >= FRAGMENTS && fragmentsHeld(player) >= FRAGMENTS;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 20 != 7
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (ready(player)) {
            assemble(player, true);
        }
    }

    // 欠片を一つの鍵にする。force (コマンド) なら欠片が足りなくても進め、あるだけ取る。scene = false なら場面を出さない (自己テスト)
    public static boolean assemble(ServerPlayer player, boolean scene) {
        StoryData data = StoryData.get(player.server);
        if (data.assembled(player.getUUID())) {
            return false;
        }
        int taken = player.getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.SKY_KEY_FRAGMENT.get()), FRAGMENTS,
                player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        long day = player.server.overworld().getDayTime() / 24000L;
        boolean first = data.mark(player.getUUID(), player.getGameProfile().getName(), day);
        Quests.give(player, new ItemStack(StorySetup.SKY_KEY.get()));
        Lore.unlock(player, LORE, false);
        Quests.signal(player, SIGNAL);
        SkyAbyss.LOGGER.info("{} assembled the sky key from {} fragment(s) on day {}", player.getGameProfile().getName(), taken, day);
        Notice.narrate(player, "message.skyabyss.story.key_formed");
        if (first) {
            List<ServerPlayer> others = player.server.getPlayerList().getPlayers().stream().filter(p -> p != player).toList();
            others.forEach(p -> Notice.narrate(p, "message.skyabyss.story.key_formed_world", player.getDisplayName()));
        }
        // ほかの人にも見える小さな印: 光の粒が、空から足もとへ落ちて地面に沈む
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 6, player.getZ(), 40, 0.4, 3.0, 0.4, 0.0);
        if (scene) {
            PacketDistributor.sendToPlayer(player, new SkyKeyScenePayload((int) day + 1, false));
        }
        return true;
    }

    // 場面だけを見直す (コマンド)
    public static void replay(ServerPlayer player) {
        long day = StoryData.get(player.server).day(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SkyKeyScenePayload((int) Math.max(0, day) + 1, true));
    }

    // 記録を消す (鍵の品は取り上げない。テスト用)
    public static void reset(ServerPlayer player) {
        StoryData.get(player.server).clear(player.getUUID());
        Lore.lock(player, LORE);
    }
}
