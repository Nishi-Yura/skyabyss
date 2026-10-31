package jp.revati.skyabyss.tower;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// あいだの塔の橋の上の一戦 (docs/design/54 の 2.4・2.5。手本は Wynncraft の Annihilation)。
//   時間    30 分 (LIMIT)。越えると主が巻き戻しを唱えて全滅 (隊の上に残りの時間の帯)
//   休み場  南の入口の癒しの輪 (体力が戻る)
//   落ちる  橋の上面より FALL 下へ落ちた人は入口へ戻し、体力の 2 割を失う
//   主      「あいだに座すもの」との一戦は TowerFight (技・段・落ちる月・側の目・群れ)。南の天然磁石で都へ戻れる
public final class TowerArena {
    public static final int LIMIT = 30 * 60 * 20;
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.4f), 1.4f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6f, 0.4f, 1.0f), 1.4f);
    final TowerRun run;
    final ServerBossEvent timer = new ServerBossEvent(Component.translatable("message.skyabyss.tower.timer", 30), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    final TowerFight fight;

    TowerArena(TowerRun run) {
        this.run = run;
        this.fight = new TowerFight(run);
    }

    // 主と側の目を湧かせる (Towers.start が橋を流し込んだあとに)
    void begin(ServerLevel level) {
        fight.spawn(level);
    }

    void tick(ServerLevel level, long t) {
        long elapsed = t - run.startedTick;
        // 残りの時間の帯
        if (t % 20 == 0) {
            long left = Math.max(0, LIMIT - elapsed);
            timer.setProgress(left / (float) LIMIT);
            timer.setName(Component.translatable("message.skyabyss.tower.timer", (left / 20 + 59) / 60));
            for (ServerPlayer p : run.online()) {
                if (p.level() == level) {
                    timer.addPlayer(p);
                } else {
                    timer.removePlayer(p);
                }
            }
        }
        fight.tick(level, t);
        // 癒しの輪
        if (t % 20 == 0) {
            Vec3 heal = run.markCenter("heal");
            for (ServerPlayer p : run.present()) {
                if (p.position().subtract(heal).horizontalDistance() < 5 && Math.abs(p.getY() - heal.y) < 2) {
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 2, true, false));
                }
            }
            level.sendParticles(GOLD, heal.x, heal.y + 0.1, heal.z, 8, 1.5, 0, 1.5, 0);
        }
        // 落ちた人
        int fy = TowerWorld.ARENA_Y;
        for (ServerPlayer p : run.online()) {
            if (p.level() == level && p.getY() < fy - TowerWorld.FALL && Math.abs(p.getX() - TowerWorld.origin(run.slot).getX()) < 80) {
                Vec3 e = run.markCenter("entry");
                p.teleportTo(level, e.x, e.y, e.z, 180f, 0f);
                p.resetFallDistance();
                p.setDeltaMovement(Vec3.ZERO);
                if (!p.isCreative()) {
                    p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * 0.2f));
                }
                level.sendParticles(ParticleTypes.END_ROD, e.x, e.y + 1, e.z, 20, 0.4, 0.8, 0.4, 0.02);
                Notice.hint(p, "message.skyabyss.tower.fell");
            }
        }
    }

    // 時間切れ
    boolean timedOut(long t) {
        return t - run.startedTick >= LIMIT;
    }

    void close() {
        timer.removeAllPlayers();
        fight.close();
    }
}
