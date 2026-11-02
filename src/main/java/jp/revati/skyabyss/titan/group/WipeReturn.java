package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 心臓の戦いで全員が倒れたら、背の休み場ではなく地上で目覚める (2026-10-28 の試遊「全員死んだら巨神の前に起きる。エンドコンテンツで行った場合は祠の前」)。
// 物語の一度目 (FirstRun) は地上の巨神の前 (Titans.sendDown)、周回 (祠から) は祠の前 (TitanShrine.sendTo) で、周回の順路はそこで終わる。
// 倒れた人は死の画面にいるので、起き上がって背の休み場に立ったところを運ぶ (ここで待つ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WipeReturn {
    private WipeReturn() {
    }

    private record Home(String titan, boolean replay) {
    }

    private static final Map<UUID, Home> PENDING = new HashMap<>();

    // GroupBossRoom から: 心臓の戦いの全滅。倒れた人を地上へ帰す印を付ける
    public static void wiped(ServerLevel back, UUID group, Set<UUID> fallen) {
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            return;
        }
        boolean replay = !FirstRun.isFirstRun(back, group);
        if (replay) {
            jp.revati.skyabyss.trial.TrialRoute.end(back, group);
        }
        for (UUID id : fallen) {
            PENDING.put(id, new Home(titan, replay));
        }
        SkyAbyss.LOGGER.info("Heart wipe: group {} on the {} goes {} ({} people)", group, titan, replay ? "to the shrine" : "down to the titan", fallen.size());
    }

    public static boolean pending(UUID id) {
        return PENDING.containsKey(id);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() || event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        for (var it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.isRemoved()) {
                // 死の画面・抜けている人は、起き上がる・戻るのを待つ
                continue;
            }
            it.remove();
            Home h = e.getValue();
            if (!(h.replay() && jp.revati.skyabyss.titan.shrine.TitanShrine.sendTo(p, h.titan()))) {
                Titans.sendDown(p, h.titan());
            }
            Notice.narrate(p, h.replay() ? "message.skyabyss.heart_wipe.shrine" : "message.skyabyss.heart_wipe.titan");
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
