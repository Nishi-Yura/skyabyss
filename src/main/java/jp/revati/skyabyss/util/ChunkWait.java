package jp.revati.skyabyss.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 岸・陸を探す前に、探す範囲の区画が読み込まれているかを見て、読み込まれるまで次の tick 以降に回す。
// 同期の getChunk は区画を生成するので、一度に数百 ms (新しい地形で 1 秒近く) 止まる。
// ready: 範囲 (中心から reach ブロック) の区画がすべて読み込まれていれば true。足りなければ区画の券を出して false。
// later: ready になったら (または 200 tick 過ぎたら、そのまま) then を流す。探しの結果は then の中で、前と同じ同期の探しで出す。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChunkWait {
    // 岸を探す範囲 (TideOcean.standableShore の 48) と、陸を探す範囲 (EmberGround.landingAt の 16)
    public static final int SHORE_REACH = 48;
    public static final int LAND_REACH = 16;
    // 区画が読み込まれるのを待つ最長 (tick)。過ぎたら、そのまま同期で探す (まれに止まる)
    private static final int GIVE_UP_TICKS = 200;

    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_chunk_wait", Comparator.comparingLong(ChunkPos::toLong), 40);
    private static final List<Pending> QUEUE = new ArrayList<>();

    private record Pending(ServerLevel level, double x, double z, int reach, Runnable then, long deadline) {
    }

    private ChunkWait() {
    }

    // 中心 (x, z) から reach ブロックの区画がすべて読み込まれているか。足りなければ、その区画を読み込む券を出す
    public static boolean ready(ServerLevel level, double x, double z, int reach) {
        int minX = Mth.floor(x - reach) >> 4;
        int maxX = Mth.floor(x + reach) >> 4;
        int minZ = Mth.floor(z - reach) >> 4;
        int maxZ = Mth.floor(z + reach) >> 4;
        boolean all = true;
        for (int cx = minX; cx <= maxX && all; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                // 完成した区画だけを数える (読み込まない。hasChunk は途中の段階の区画でも真になり、同期の探しが生成で止まる)
                if (level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) {
                    all = false;
                    break;
                }
            }
        }
        if (!all) {
            ChunkPos center = new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
            int radius = Math.max(Math.max(center.x - minX, maxX - center.x), Math.max(center.z - minZ, maxZ - center.z));
            level.getChunkSource().addRegionTicket(TICKET, center, radius, center);
        }
        return all;
    }

    // ready になったら then を流す (今すぐ ready なら、その場で流す)
    public static void later(ServerLevel level, double x, double z, int reach, Runnable then) {
        if (ready(level, x, z, reach)) {
            then.run();
            return;
        }
        QUEUE.add(new Pending(level, x, z, reach, then, level.getGameTime() + GIVE_UP_TICKS));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = QUEUE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (ready(p.level(), p.x(), p.z(), p.reach()) || p.level().getGameTime() >= p.deadline()) {
                it.remove();
                due.add(p);
            }
        }
        for (Pending p : due) {
            try {
                p.then().run();
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("chunk wait: a delayed landing search failed", e);
            }
        }
    }

    // 自己テスト用: 待っている数
    public static int pending() {
        return QUEUE.size();
    }
}
