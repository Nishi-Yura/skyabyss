package jp.revati.skyabyss.sky;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 「削られた山頂」のサーバー側。
// 空の影の通過 (SkyEvents) が始まったとき、その経路の下で高い自然の峰を探しておき、
// 影がその真上を過ぎる時刻に、安全を確かめ直してから少しずつ削る (1tick に BUDGET 個まで)。
// 削るのは「1章に入った」プレイヤー (影の目撃者) の近くを通ったときだけ。プレイヤーの建築は PeakScar が対象から外す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PeakScarEvents {
    // 1tick に変えるブロックの上限 (サーバーの負荷を抑える)
    static final int BUDGET = 48;
    // 経路の左右どこまでの峰を探すか (影の翼は片側135まで広がる)
    static final double LATERAL = 64.0;
    // 一度に覚えておく候補の数
    static final int MAX_CANDIDATES = 5;
    // 安全の確かめは1tickに一つの候補まで
    static final int CHECKS_PER_TICK = 1;

    record Target(PeakScar.Candidate candidate, long at, float dirX, float dirZ, float strength) {
    }

    // 削る順番を待っている候補 (時刻順)。最初に安全だった一つだけを削り、残りは捨てる
    private static final Deque<Target> PENDING = new ArrayDeque<>();
    @Nullable
    private static Job job;
    private static long lastPassStart = Long.MIN_VALUE;

    private PeakScarEvents() {
    }

    // 進行中の削り (少しずつブロックを変える)
    static final class Job {
        final PeakScar.Plan plan;
        int index;
        boolean aborted;

        Job(PeakScar.Plan plan) {
            this.plan = plan;
        }

        // budget 個まで進める。終わったら true
        boolean step(ServerLevel level, int budget) {
            List<PeakScar.Change> changes = plan.changes();
            for (int n = 0; n < budget && index < changes.size(); n++, index++) {
                PeakScar.Change c = changes.get(index);
                if (!level.isLoaded(c.pos())) {
                    aborted = true;
                    return true;
                }
                // 計画したときと違うブロックになっていたら (だれかが置いた・掘った)、そこで止める
                if (level.getBlockState(c.pos()) != c.expect()) {
                    aborted = true;
                    SkyAbyss.LOGGER.debug("Peak scar at {} stopped: {} changed", plan.center(), c.pos());
                    return true;
                }
                // 近くへの更新を起こさない (落ちる砂・流れる水・壊れて落ちる物を出さない)。物は落とさない
                level.setBlock(c.pos(), c.to(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return index >= changes.size();
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        SkyPass pass = SkyEvents.activePass();
        if (pass != null && pass.startTick() != lastPassStart) {
            lastPassStart = pass.startTick();
            onPassStarted(level, pass);
        }
        if (job != null) {
            if (job.step(level, BUDGET)) {
                finish(level, job);
                job = null;
            }
            return;
        }
        for (int i = 0; i < CHECKS_PER_TICK && !PENDING.isEmpty() && now >= PENDING.peekFirst().at(); i++) {
            Target t = PENDING.pollFirst();
            PeakScar.Result r = PeakScar.plan(level, t.candidate(), t.dirX(), t.dirZ(), t.strength(), level.random, PeakScar.PLAYER_CLEAR);
            if (r.plan() == null) {
                SkyAbyss.LOGGER.debug("Peak scar candidate {} skipped: {}", t.candidate(), r.reason());
                continue;
            }
            begin(level, r.plan());
            PENDING.clear();
        }
    }

    // 削ってよい頻度 (0 なら削らない)
    static double chance() {
        if (Config.HORROR_EVENT_FREQUENCY.get() <= 0) {
            return 0;
        }
        return Math.min(1.0, Config.PEAK_SCAR_CHANCE.get() * Math.min(1.0, Config.HORROR_EVENT_FREQUENCY.get()));
    }

    // 影の目撃者 (経路の真ん中に一番近い人) が1章に入っていれば、確率で経路の下の峰を候補にする
    static void onPassStarted(ServerLevel level, SkyPass pass) {
        PENDING.clear();
        double chance = chance();
        if (chance <= 0 || level.random.nextDouble() >= chance) {
            return;
        }
        double length = pass.speed() * pass.duration();
        double midX = pass.centerX(pass.duration() / 2.0);
        double midZ = pass.centerZ(pass.duration() / 2.0);
        ServerPlayer witness = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double d = Math.hypot(p.getX() - midX, p.getZ() - midZ);
            if (!p.isSpectator() && d < best) {
                best = d;
                witness = p;
            }
        }
        if (witness == null || best > 120 || !ChapterSky.inChapterOne(witness)) {
            return;
        }
        float strength = ChapterSky.moonNearness(level, witness);
        schedule(level, pass.originX(), pass.originZ(), pass.dirX(), pass.dirZ(), pass.speed(), length,
                pass.startTick(), strength);
    }

    // 経路の下の峰を探し、影がその真上に来る時刻に並べる。見つけた数を返す
    static int schedule(ServerLevel level, double originX, double originZ, float dirX, float dirZ, float speed, double length,
                        long startTick, float strength) {
        List<PeakScar.Candidate> found = PeakScar.findCandidates(level, originX, originZ, dirX, dirZ, length, LATERAL);
        List<Target> targets = new ArrayList<>();
        for (PeakScar.Candidate c : found.subList(0, Math.min(MAX_CANDIDATES, found.size()))) {
            double along = (c.x() - originX) * dirX + (c.z() - originZ) * dirZ;
            long at = startTick + Math.max(40L, (long) (along / Math.max(0.01f, speed)));
            targets.add(new Target(c, at, dirX, dirZ, strength));
        }
        // 高い順に選んだ候補を、影が真上を過ぎる順に試す。最初に安全だった一つだけを削る
        targets.sort(java.util.Comparator.comparingLong(Target::at));
        PENDING.addAll(targets);
        if (!targets.isEmpty()) {
            SkyAbyss.LOGGER.debug("Peak scar: {} candidates under the pass", targets.size());
        }
        return targets.size();
    }

    static void begin(ServerLevel level, PeakScar.Plan plan) {
        job = new Job(plan);
        BlockPos c = plan.center();
        // 遠い岩の擦れる音。近くの人には大きく、遠い人にはかすかに (360ブロックまで)
        level.playSound(null, c.getX() + 0.5, c.getY(), c.getZ() + 0.5, ChapterSkyRegistry.PEAK_SCRAPE.get(), SoundSource.AMBIENT,
                1.0f, 0.9f + level.random.nextFloat() * 0.15f);
        // 削られた所から白い砕けた石が舞う
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState());
        for (int i = 0; i < 6; i++) {
            double t = (i / 5.0 - 0.5) * 14.0;
            level.sendParticles(dust, c.getX() + 0.5 + plan.dirX() * t, c.getY() + 0.5, c.getZ() + 0.5 + plan.dirZ() * t,
                    30, 3.0, 1.0, 3.0, 0.15);
        }
        SkyAbyss.LOGGER.info("A peak was scarred at {} ({} blocks)", c, plan.removed());
    }

    private static void finish(ServerLevel level, Job finished) {
        ChapterSkyData.get(level).addScar(new ChapterSkyData.Scar(finished.plan.center(), level.getGameTime(), finished.plan.removed()));
    }

    // テスト用: 計画をすぐに最後まで進める (サーバーを回さずに確かめる)。止まったら false
    static boolean runNow(ServerLevel level, PeakScar.Plan plan) {
        Job j = new Job(plan);
        while (!j.step(level, BUDGET)) {
            // 続ける
        }
        if (!j.aborted) {
            finish(level, j);
        }
        return !j.aborted;
    }

    static boolean busy() {
        return job != null;
    }

    static int pendingCount() {
        return PENDING.size();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        job = null;
        lastPassStart = Long.MIN_VALUE;
    }
}
