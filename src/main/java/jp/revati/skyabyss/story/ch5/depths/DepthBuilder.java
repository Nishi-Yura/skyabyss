package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 区画を建てる (docs/design/36 の 9)。AbyssBuild と同じく、一度に建てず 1 tick に BUDGET_MS だけ進める。
//   建てる始まり: だれかが区画に入る前に、次の区画を頼む (DepthBuilder.request)。頼まれた区画だけ建つ。頼みがなければ何もしない
//   順: 頼まれた順。一つ建て終えたら版を記録し、次へ。建てている途中で止まっても、次に頼まれたとき最初から (変わるブロックだけ置く)
//   中身: DepthLayout が出す置く一覧を、順に置く。箱は中身の表 (loot) をつける
//   場所の決まり (灯り・足場・灯籠) は、建て終えたときに持っておく (geometry)。ワールドを開き直したあとは、必要になった時に作り直す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthBuilder {
    public static final int VERSION = 1;
    static final long BUDGET_MS = 20;

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Map<Integer, DepthLayout.Result> GEOM = new HashMap<>();

    private static final class Job {
        final int index;
        DepthLayout.Result layout;
        int next;

        Job(int index) {
            this.index = index;
        }
    }

    private DepthBuilder() {
    }

    public static boolean built(MinecraftServer server, int index) {
        return DepthData.get(server).built.getOrDefault(index, 0) >= VERSION;
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    // 建ててほしいと頼む (建て終えていれば、また頼まない)
    public static void request(MinecraftServer server, int index) {
        if (index < 1 || built(server, index)) {
            return;
        }
        for (Job j : QUEUE) {
            if (j.index == index) {
                return;
            }
        }
        QUEUE.addLast(new Job(index));
        SkyAbyss.LOGGER.info("[abyss depth] queued section {}", index);
    }

    // 区画の決まり (建てた時の場所の情報)。なければ作る (ワールドの種から)。建てていない区画でも作れる
    public static DepthLayout.Result geometry(MinecraftServer server, int index) {
        DepthLayout.Result g = GEOM.get(index);
        if (g == null) {
            g = DepthLayout.build(DepthPlan.plan(seed(server), index)).stripped();
            GEOM.put(index, g);
        }
        return g;
    }

    public static long seed(MinecraftServer server) {
        return server.overworld().getSeed();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        step(event.getServer());
    }

    static void step(MinecraftServer server) {
        Job job = QUEUE.peekFirst();
        if (job == null) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            QUEUE.pollFirst();
            return;
        }
        long seed = seed(server);
        if (job.layout == null) {
            job.layout = DepthLayout.build(DepthPlan.plan(seed, job.index));
        }
        long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L;
        var puts = job.layout.puts();
        while (job.next < puts.size()) {
            DepthLayout.Put p = puts.get(job.next++);
            place(level, p, seed ^ job.index);
            if ((job.next & 255) == 0 && System.nanoTime() > deadline) {
                return;
            }
        }
        QUEUE.pollFirst();
        GEOM.put(job.index, job.layout.stripped());
        DepthData d = DepthData.get(server);
        d.built.put(job.index, VERSION);
        d.setDirty();
        SkyAbyss.LOGGER.info("[abyss depth] section {} built ({} blocks)", job.index, puts.size());
    }

    // 一つ置く (同じなら置かない。箱は中身の表をつける)
    static void place(ServerLevel level, DepthLayout.Put p, long seed) {
        BlockPos b = new BlockPos(p.x(), p.y(), p.z());
        if (level.getBlockState(b) != p.state()) {
            level.setBlock(b, p.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (p.loot() != null) {
            BlockEntity be = level.getBlockEntity(b);
            if (be instanceof RandomizableContainerBlockEntity c) {
                c.setLootTable(p.loot(), seed ^ (p.x() * 31L + p.z() * 17L + p.y()));
                c.setChanged();
            }
        }
    }

    // 頼まれた区画を、その場で最後まで建てる (自己テストと開発用)。建てた区画の数を返す
    public static int finishNow(MinecraftServer server, int index) {
        request(server, index);
        int guard = 0;
        while (busy() && guard++ < 100_000) {
            step(server);
        }
        return built(server, index) ? 1 : 0;
    }

    // 停止のとき (開き直しに備えて捨てる)
    public static void reset() {
        QUEUE.clear();
        GEOM.clear();
    }
}
