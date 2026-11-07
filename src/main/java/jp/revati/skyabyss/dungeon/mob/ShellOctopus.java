package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 殻負いの大蛸 (docs/design/55 の 1 章、潮の洞の主。形は 00 の 365 = B 殻負いの大蛸)。
//   叩きつけ  真ん中の腕を振り上げて、前の長い帯へ叩きつける (予告 0.8 秒)
//   薙ぎ      両端の腕で前の広い扇を薙ぐ (予告 0.9 秒)。両端の腕が残っているときだけ
//   墨        人ごとの足もとに墨の輪 (予告 1.2 秒)。当たると目がくらむ
//   殻ごもり  体力が 7 割・4 割を切ったとき一度ずつ、殻にこもって 5 秒、受ける傷が 4 分の 1。こもったまま腕で二本の帯を叩く
//   渦        まわりの輪 (内 4・外 12) で人を引き寄せ、続けて足もとの輪を叩く (予告 1.1 秒 → 0.7 秒)。輪の外か内へ逃げる
//   殻ごもりから出た直後は大きな隙。後半 (体力半分から) は叩きつけが三本になり、墨が二度降る。腕が減るほど技が速い。洞の水母を 2 体呼ぶ
//   腕を切る  体力が 8・6・4・2 割を切るたびに腕が一本落ちる (両端から)。腕は見えなくなり、攻め手が減る (55 の 1 章「腕を一本ずつ切る」)
public class ShellOctopus extends DungeonBoss {
    static final EntityDataAccessor<Integer> ARMS = SynchedEntityData.defineId(ShellOctopus.class, EntityDataSerializers.INT);
    static final int SLAM = 1;
    static final int SWEEP = 2;
    static final int INK = 3;
    static final int WITHDRAW = 4;
    static final int WHIRL = 5;
    public static final int WHIRL_WARN = 22;
    public static final int WHIRL_SLAM = 14;
    public static final int SLAM_WARN = 16;
    public static final double SLAM_LENGTH = 10;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RANGE = 8;
    public static final int INK_WARN = 24;
    public static final int WITHDRAW_TICKS = 100;
    // 腕の落ちる順 (骨 arm<k>。真ん中の 2 は最後まで残す)
    public static final int[] CUT_ORDER = {4, 0, 3, 1};
    private int lastSweep = -200;
    private int lastInk = -300;
    private int withdrawn;
    private int lastWhirl = -200;

    public ShellOctopus(EntityType<? extends Monster> type, Level level) {
        super(type, level, "shell_octopus", () -> SoundEvents.SQUID_AMBIENT, () -> SoundEvents.SQUID_HURT, () -> SoundEvents.ELDER_GUARDIAN_DEATH,
                "slam", "sweep", "ink", "withdraw", "unwithdraw", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 640)
                .add(Attributes.ATTACK_DAMAGE, 15)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ARMS, 5);
    }

    // 残っている腕の数 (5..1)。描く側は切れた腕の骨を隠す
    public int arms() {
        return entityData.get(ARMS);
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("withdraw");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (move == WITHDRAW && act > 0) {
            amount *= 0.25f;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide) {
            int left = Math.max(1, Math.min(5, (int) Math.ceil(hp() * 5 - 1e-4)));
            if (left < arms()) {
                entityData.set(ARMS, left);
                server().sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.2, getZ(), 40, 1.2, 0.6, 1.2, 0.05);
                server().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6f, 0.5f);
                for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32), ServerPlayer::isAlive)) {
                    Notice.hint(p, "message.skyabyss.dungeon.octopus_arm", arms());
                }
            }
        }
        return hit;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        float h = hp();
        if ((h < 0.7f && withdrawn == 0) || (h < 0.4f && withdrawn == 1)) {
            withdrawn++;
            move = WITHDRAW;
            begin(target, "withdraw");
            cue("shell_octopus", "withdraw", 20);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.SHULKER_CLOSE, SoundSource.HOSTILE, 2f, 0.5f);
            return;
        }
        if (tickCount - lastWhirl > 220 && distance > 4 && distance < 12) {
            whirl(target);
            return;
        }
        if (tickCount - lastInk > 240 && distance > 4) {
            ink(target);
            return;
        }
        if (distance <= SWEEP_RANGE && tickCount - lastSweep > 140 && arms() >= 4) {
            move = SWEEP;
            lastSweep = tickCount;
            begin(target, "sweep");
            cue("shell_octopus", "sweep", SWEEP_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWEEP_RANGE, Math.toRadians(130)))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SPLASH, getX() + f.x * 4, getY() + 0.5, getZ() + f.z * 4, 40, 3, 0.3, 3, 0.1))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        Vec3 away = player.position().subtract(position()).normalize();
                        player.knockback(1.3, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance <= SLAM_LENGTH + 1) {
            move = SLAM;
            begin(target, "slam");
            cue("shell_octopus", "slam", SLAM_WARN);
            double a = Telegraph.angleOf(forward());
            slamLine(target, a, SLAM_WARN, 1.15f);
            if (phase2) {
                // 後半: 両脇の腕が少し遅れて続く
                slamLine(target, a - 0.45, SLAM_WARN + 8, 0.9f);
                slamLine(target, a + 0.45, SLAM_WARN + 8, 0.9f);
            }
        }
    }

    // 渦: まわりの輪で引き寄せ、続けて足もとを叩く
    void whirl(LivingEntity target) {
        move = WHIRL;
        lastWhirl = tickCount;
        begin(target, "sweep");
        cue("shell_octopus", "whirl", WHIRL_WARN);
        Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), 4, 12))
                .floor(getY()).warn(WHIRL_WARN).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.5, getZ(), 80, 5, 0.3, 5, 0.2))
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.4f);
                    Vec3 in = position().subtract(player.position()).normalize();
                    player.setDeltaMovement(in.x * 1.3, 0.35, in.z * 1.3);
                    player.hurtMarked = true;
                })
                .start();
    }

    void slamLine(LivingEntity target, double angle, int warn, float k) {
        Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), angle, SLAM_LENGTH, 1.4))
                .floor(getY()).warn(warn).danger().owner(this).target(target)
                .victims(this::canHit)
                .onImpact(server -> {
                    Vec3 tip = position().add(Math.cos(angle) * SLAM_LENGTH * 0.7, 0, Math.sin(angle) * SLAM_LENGTH * 0.7);
                    server.sendParticles(ParticleTypes.SPLASH, tip.x, getY() + 0.3, tip.z, 50, 1.2, 0.2, 1.2, 0.2);
                    server.playSound(null, tip.x, getY(), tip.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6f, 0.6f);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage() * k))
                .start();
    }

    void ink(LivingEntity target) {
        move = INK;
        lastInk = tickCount;
        begin(target, "ink");
        cue("shell_octopus", "ink", INK_WARN);
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
            Vec3 s = p.position();
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 3.0))
                    .floor(s.y).warn(INK_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 1, s.z, 60, 1.4, 0.8, 1.4, 0.02))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.5f);
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                    })
                    .start();
        }
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == WHIRL && tick == WHIRL_WARN + 4) {
            LivingEntity t = getTarget();
            cue("shell_octopus", "slam", WHIRL_SLAM);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), 5))
                    .floor(getY()).warn(WHIRL_SLAM).danger().owner(this).target(t)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 80, 2.5, 0.2, 2.5, 0.2))
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.1f))
                    .start();
        }
        if (move == INK && phase2 && tick == INK_WARN) {
            // 後半: 墨がもう一度
            LivingEntity t = getTarget();
            if (t != null) {
                for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
                    Vec3 s = p.position();
                    Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 3.0))
                            .floor(s.y).warn(INK_WARN - 4).danger().owner(this).target(t)
                            .victims(this::canHit)
                            .onImpact(server -> server.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + 1, s.z, 60, 1.4, 0.8, 1.4, 0.02))
                            .onHit((server, player) -> {
                                player.hurt(attackSource(), damage() * 0.5f);
                                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                            })
                            .start();
                }
            }
        }
        if (move == WITHDRAW) {
            LivingEntity t = getTarget();
            // こもったまま、殻の口から腕で二本の帯を叩く (中ほどで一度)
            if (tick == 40 && t != null) {
                double base = Telegraph.angleOf(t.position().subtract(position()));
                slamLine(t, base - 0.5, 24, 0.9f);
                slamLine(t, base + 0.5, 24, 0.9f);
                cue("shell_octopus", "slam", 24);
            }
            if (tick == WITHDRAW_TICKS - 16) {
                playAction("unwithdraw");
                server().playSound(null, getX(), getY(), getZ(), SoundEvents.SHULKER_OPEN, SoundSource.HOSTILE, 2f, 0.5f);
            }
        }
    }

    @Override
    protected String bossId() {
        return "shell_octopus";
    }

    @Override
    protected String minionId() {
        return "skyabyss:cave_jelly";
    }

    @Override
    protected int length() {
        return switch (move) {
            case SLAM -> SLAM_WARN + 14;
            case SWEEP -> SWEEP_WARN + 12;
            case INK -> phase2 ? INK_WARN * 2 + 2 : INK_WARN + 6;
            case WHIRL -> WHIRL_WARN + 4 + WHIRL_SLAM + 10;
            default -> WITHDRAW_TICKS;
        };
    }

    @Override
    protected void finish() {
        // 殻から出た直後は大きな隙。腕が減るほど荒れて、次の技までが短い
        if (move == WITHDRAW) {
            opening();
        }
        cooldown = Math.max(8, (move == WITHDRAW ? 24 : 18 + random.nextInt(10)) - (5 - arms()) * 2);
        super.finish();
    }
}
