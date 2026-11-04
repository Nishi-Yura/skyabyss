package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 主の隙と傷の倍率 (docs/design/47 の 2.1)。2026-10-28 の試遊「ごり押しでは絶対に倒せないようにしてほしい」→ ソウル型 + 主ごとの仕掛け。
//   主 (BossFooting.planted) が人から受ける傷は、ふだん ×BreakGauge.NORMAL、崩れている間 ×BreakGauge.BROKEN。
//   技 (持ち主のある予告 Telegraph) が当たり終わると OPEN_TICKS の隙。隙の間は崩しゲージが ×BreakGauge.OPENING で溜まる。
//   主ごとの仕掛け (心臓の領域) を解くと BIG_OPEN_TICKS の大きな隙 (BreakGauge.solved)。環境・仕掛けの傷 (人が打ったのでない傷) はそのまま。
//   ふつうの敵には倍率を掛けない (2026-11-04 に身構えを外した。連打は近接の決まり MeleeHits で効かない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossOpenings {
    private BossOpenings() {
    }

    // この印を持つ敵は主として扱わない (壊すのが目的の的・動かない繋ぎ手など。技を出さないので隙も来ないため)
    public static final String NO_GUARD_TAG = "skyabyss_no_guard";
    public static final int OPEN_TICKS = 60;
    public static final int BIG_OPEN_TICKS = 120;

    // 隙の終わりの時刻 (ゲーム時間)。主ごと
    private static final Map<UUID, Long> OPEN_UNTIL = new HashMap<>();

    // 自己テスト用: 主の見分けを差し替える (null なら BossFooting.planted)
    public static java.util.function.Predicate<LivingEntity> plantedForTest;

    // 主か。動かない的 (AI のない敵。自己テストの的・訓練の的) は技を出さず隙も来ないので主にしない
    public static boolean planted(LivingEntity e) {
        if (plantedForTest != null) {
            return plantedForTest.test(e);
        }
        return !(e instanceof net.minecraft.world.entity.Mob m && m.isNoAi()) && !e.getTags().contains(NO_GUARD_TAG) && BossFooting.planted(e);
    }

    // Telegraph から: 持ち主の技が当たり終わった
    public static void moveEnded(Entity owner) {
        if (owner instanceof LivingEntity le && le.isAlive() && planted(le)) {
            open(le, OPEN_TICKS);
        }
    }

    // 隙を開ける (長い方を残す)
    public static void open(LivingEntity boss, int ticks) {
        long until = boss.level().getGameTime() + ticks;
        Long was = OPEN_UNTIL.get(boss.getUUID());
        if (was == null || was < until) {
            OPEN_UNTIL.put(boss.getUUID(), until);
        }
        if (boss.level() instanceof ServerLevel level) {
            // 見て分かる合図: 白い火花と澄んだ音
            level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 14,
                    boss.getBbWidth() * 0.4, boss.getBbHeight() * 0.3, boss.getBbWidth() * 0.4, 0.04);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.4f, 1.3f);
        }
    }

    public static boolean isOpen(LivingEntity boss) {
        Long until = OPEN_UNTIL.get(boss.getUUID());
        return until != null && boss.level().getGameTime() < until;
    }

    // 人から受ける傷の倍率 (主はふだん/崩れ、ほかは 1)
    public static float multiplier(LivingEntity target) {
        if (!planted(target)) {
            return 1f;
        }
        return BreakGauge.broken(target) ? BreakGauge.BROKEN : BreakGauge.NORMAL;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        // 心臓の主の技は一律 HeartBalance.HIT_SCALE 倍 (docs/design/47 の 2.3)
        if (target instanceof Player && event.getSource().getEntity() instanceof jp.revati.skyabyss.entity.HeartBoss) {
            event.setAmount(event.getAmount() * (float) HeartBalance.HIT_SCALE);
            return;
        }
        if (target.level().isClientSide() || target instanceof Player) {
            return;
        }
        // 人が打った傷だけ (矢・魔法も人の傷)。環境・仕掛けの傷はそのまま
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        float m = multiplier(target);
        if (m != 1f) {
            event.setAmount(event.getAmount() * m);
        }
    }

    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        OPEN_UNTIL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        OPEN_UNTIL.clear();
    }
}
