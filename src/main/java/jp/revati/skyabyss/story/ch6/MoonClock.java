package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 月の一日と位相 (docs/design/46 の 3.3)。月の一日 = 24000 tick (実時間 20 分)。四つの位相を 6000 tick (5 分) ずつ:
//   0 新月 → 1 上弦 → 2 満月 → 3 下弦。月のディメンションの経過時間 (gameTime) で決める (地上の時刻とは別。世界で一つ)
//   位相が変わると、月にいる人へ上の帯で知らせる (位相の名と一言)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonClock {
    public static final long DAY = 24000L;
    public static final long PHASE = DAY / 4;
    public static final String[] PHASES = {"new", "first", "full", "last"};
    private static int last = -1;

    private MoonClock() {
    }

    public static int phase(long gameTime) {
        return (int) ((gameTime % DAY) / PHASE);
    }

    public static int phase(ServerLevel moon) {
        return phase(time(moon));
    }

    // 月の時計の今 (ゲームの時刻 + 開発用のずれ)
    public static long time(ServerLevel level) {
        return level.getGameTime() + MoonWorld.Data.get(level.getServer()).clockOffset;
    }

    // 開発用: 位相 n の頭へ進める
    public static void skipTo(ServerLevel level, int n) {
        long t = time(level);
        long target = (t / DAY) * DAY + n * PHASE;
        if (target <= t) {
            target += DAY;
        }
        MoonWorld.Data d = MoonWorld.Data.get(level.getServer());
        d.clockOffset += target - t;
        d.setDirty();
    }

    // 次の位相までの tick
    public static long untilNext(long gameTime) {
        return PHASE - gameTime % PHASE;
    }

    public static Component name(int phase) {
        return Component.translatable("message.skyabyss.moon.phase." + PHASES[phase]);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel moon = event.getServer().getLevel(MoonWorld.LEVEL);
        if (moon == null) {
            return;
        }
        int now = phase(moon);
        if (now != last) {
            if (last >= 0) {
                for (ServerPlayer p : moon.players()) {
                    Notice.place(p, name(now), Component.translatable("message.skyabyss.moon.phase." + PHASES[now] + ".sub"));
                }
            }
            last = now;
        }
    }
}
