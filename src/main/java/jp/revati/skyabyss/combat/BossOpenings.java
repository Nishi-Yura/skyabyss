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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 構えと隙 (docs/design/47 の 2.1 と 3)。2026-10-28 の試遊「ごり押しでは絶対に倒せないようにしてほしい」→ ソウル型 + 主ごとの仕掛け。
//   主 (BossFooting.planted) は、ふだんは構えていて人から受ける傷 ×GUARD。技 (持ち主のある予告 Telegraph) が当たり終わると OPEN_TICKS の隙 (×OPEN)。
//   主ごとの仕掛け (心臓の領域) を解くと BIG_OPEN_TICKS の大きな隙。環境・仕掛けの傷 (人が打ったのでない傷) は構えに関わらずそのまま。
//   ふつうの敵は、BRACE_WINDOW の間に BRACE_HITS 回打たれると BRACE_TICKS のあいだ身構える (×BRACE)。連打だけで押し切れない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossOpenings {
    private BossOpenings() {
    }

    public static final float GUARD = 0.3f;
    public static final float OPEN = 1.6f;
    public static final int OPEN_TICKS = 60;
    public static final int BIG_OPEN_TICKS = 120;
    public static final float BRACE = 0.4f;
    public static final int BRACE_HITS = 5;
    public static final int BRACE_WINDOW = 60;
    public static final int BRACE_TICKS = 40;

    // 隙の終わりの時刻 (ゲーム時間)。主ごと
    private static final Map<UUID, Long> OPEN_UNTIL = new HashMap<>();
    // ふつうの敵: 打たれた時刻 (直近 BRACE_HITS 回) と身構えの終わり
    private static final Map<UUID, long[]> HITS = new HashMap<>();
    private static final Map<UUID, Long> BRACED_UNTIL = new HashMap<>();

    // 自己テスト用: 主の見分けを差し替える (null なら BossFooting.planted)
    public static java.util.function.Predicate<LivingEntity> plantedForTest;

    static boolean planted(LivingEntity e) {
        return plantedForTest != null ? plantedForTest.test(e) : BossFooting.planted(e);
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

    public static boolean isBraced(LivingEntity mob) {
        Long until = BRACED_UNTIL.get(mob.getUUID());
        return until != null && mob.level().getGameTime() < until;
    }

    // 人から受ける傷の倍率 (主は構え/隙、ふつうの敵は身構え)。hit = 数えるか (ふつうの敵の打たれた回数)
    public static float multiplier(LivingEntity target, boolean count) {
        if (planted(target)) {
            return isOpen(target) ? OPEN : GUARD;
        }
        if (!(target instanceof Enemy)) {
            return 1f;
        }
        long now = target.level().getGameTime();
        if (isBraced(target)) {
            return BRACE;
        }
        if (count) {
            long[] h = HITS.computeIfAbsent(target.getUUID(), k -> new long[BRACE_HITS]);
            System.arraycopy(h, 1, h, 0, h.length - 1);
            h[h.length - 1] = now;
            if (h[0] > 0 && now - h[0] <= BRACE_WINDOW) {
                BRACED_UNTIL.put(target.getUUID(), now + BRACE_TICKS);
                java.util.Arrays.fill(h, 0);
                if (target.level() instanceof ServerLevel level) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1f, 0.8f);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }
        return 1f;
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
        float m = multiplier(target, true);
        if (m != 1f) {
            event.setAmount(event.getAmount() * m);
            if (m < 1f && planted(target) && target.level() instanceof ServerLevel level && target.tickCount % 2 == 0) {
                // 構えに当たった: 鈍い音
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.25f, 1.8f);
            }
        }
    }

    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        UUID id = event.getEntity().getUUID();
        OPEN_UNTIL.remove(id);
        HITS.remove(id);
        BRACED_UNTIL.remove(id);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        OPEN_UNTIL.clear();
        HITS.clear();
        BRACED_UNTIL.clear();
    }
}
