package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨掘りの大百足 (docs/design/55 の 1 章、鯨骨の坑の主。形は 00 の 364 = A 骨殻の大百足)。
//   噛む     前の扇 (予告 0.7 秒)。寄った人へ
//   尾で薙ぐ まわりの輪 (予告 0.9 秒)。寄った人へ、ときどき
//   潜る     床へ潜り、主の間の壁の掘り穴 (holes) の一つから出て、穴から人へ向かう長い帯を突進する (予告 1.3 秒)。
//            掘り穴がなければ (卵から出したとき) 人の足もとの輪から突き上げる。離れた人へ、または間を置いて
//   落石     体力が半分を切ると、吠えて天井を揺らし、人のまわりに落石の輪を降らせる (予告 1.5 秒)
//   潜っている間は見えず、傷を受けない (人は掘り穴の帯の予告を見て避ける)
public class BoneCentipede extends DungeonBoss {
    static final int BITE = 1;
    static final int SWEEP = 2;
    static final int BURROW = 3;
    static final int CHARGE = 4;
    static final int ROCKS = 5;
    public static final int BITE_WARN = 14;
    public static final double BITE_RANGE = 6.5;
    public static final int SWEEP_WARN = 18;
    public static final double SWEEP_RADIUS = 7;
    public static final int BURROW_DOWN = 20;
    public static final int BURROW_HIDDEN = 24;
    public static final int CHARGE_WARN = 26;
    public static final double CHARGE_LENGTH = 26;
    public static final int ROCKS_WARN = 30;
    private int lastSweep = -400;
    private int lastBurrow = -200;
    private int lastRocks = -400;
    private Vec3 rise;

    public BoneCentipede(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_centipede", () -> SoundEvents.SILVERFISH_AMBIENT, () -> SoundEvents.SILVERFISH_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "bite", "sweep", "burrow", "emerge", "roar", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 480)
                .add(Attributes.ATTACK_DAMAGE, 12)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("burrow");
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (hp() < 0.55f && tickCount - lastRocks > 280) {
            rocks(target);
            return;
        }
        if (distance > 9 || tickCount - lastBurrow > 360) {
            burrow(target);
            return;
        }
        if (distance < SWEEP_RADIUS - 0.5 && tickCount - lastSweep > 160) {
            move = SWEEP;
            lastSweep = tickCount;
            begin(target, "sweep");
            cue("bone_centipede", "sweep", SWEEP_WARN);
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), SWEEP_RADIUS))
                    .floor(getY()).warn(SWEEP_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 12, SWEEP_RADIUS * 0.4, 0.3, SWEEP_RADIUS * 0.4, 0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.85f);
                        Vec3 away = player.position().subtract(position()).normalize();
                        player.knockback(1.2, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance <= BITE_RANGE) {
            move = BITE;
            begin(target, "bite");
            cue("bone_centipede", "bite", BITE_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE, Math.toRadians(60)))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.1f);
                        player.knockback(0.9, -f.x, -f.z);
                    })
                    .start();
        }
    }

    // 潜る (床へ潜り、掘り穴か人の足もとから出て突進する)
    void burrow(LivingEntity target) {
        move = BURROW;
        lastBurrow = tickCount;
        begin(target, "burrow");
        cue("bone_centipede", "burrow", BURROW_DOWN + BURROW_HIDDEN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
    }

    // つっかえたら潜って出直す (地を蹴って移る代わり)
    @Override
    protected void unstick(LivingEntity target) {
        if (onGround()) {
            burrow(target);
        } else {
            super.unstick(target);
        }
    }

    // 吠えて天井から落石: 人ごとに輪を一つ、ほかに二つ
    void rocks(LivingEntity target) {
        move = ROCKS;
        lastRocks = tickCount;
        begin(target, "roar");
        cue("bone_centipede", "rocks", ROCKS_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
        java.util.List<Vec3> spots = new java.util.ArrayList<>();
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            spots.add(position().add(Math.cos(a) * (6 + random.nextDouble() * 8), 0, Math.sin(a) * (6 + random.nextDouble() * 8)));
        }
        for (Vec3 s : spots) {
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 2.6))
                    .floor(getY()).warn(ROCKS_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), s.x, getY() + 5, s.z, 40, 1.0, 1.5, 1.0, 0.1);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.9f))
                    .start();
        }
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move != BURROW) {
            return;
        }
        LivingEntity target = getTarget();
        if (tick == BURROW_DOWN) {
            // 床の下へ: 見えず、傷を受けない。掘り穴 (なければ人の足もと) へ移る
            setInvisible(true);
            setInvulnerable(true);
            server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()), getX(), getY() + 0.5, getZ(), 60, 1.5, 0.4, 1.5, 0.1);
            if (!holes.isEmpty() && target != null) {
                // 人から遠すぎず近すぎない穴
                Vec3 best = holes.get(random.nextInt(holes.size()));
                for (Vec3 h : holes) {
                    double d = h.distanceTo(target.position());
                    if (d > 9 && d < best.distanceTo(target.position()) + random.nextDouble() * 6) {
                        best = h;
                    }
                }
                rise = best;
            } else {
                rise = target != null ? target.position() : position();
            }
            teleportTo(rise.x, rise.y, rise.z);
            if (holes.isEmpty() && target != null) {
                Telegraph.in(server(), TelegraphShape.circle(rise.x, rise.z, 3.5))
                        .floor(rise.y).warn(BURROW_HIDDEN).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 1.2f);
                            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.9, 0));
                            player.hurtMarked = true;
                        })
                        .start();
            }
        }
        if (tick == BURROW_DOWN + BURROW_HIDDEN) {
            setInvisible(false);
            setInvulnerable(false);
            server().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(), 50, 1.4, 0.8, 1.4, 0.15);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4f, 1.3f);
            if (!holes.isEmpty() && target != null && target.isAlive()) {
                // 掘り穴から人へ突進 (RegionBoss.charge: 帯の予告のあと走る)
                cue("bone_centipede", "charge", CHARGE_WARN);
                charge(target, CHARGE, "emerge", CHARGE_WARN, CHARGE_LENGTH, 1.6, 0.9, () -> SoundEvents.RAVAGER_ROAR);
            } else {
                playAction("emerge");
            }
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case BITE -> BITE_WARN + 12;
            case SWEEP -> SWEEP_WARN + 12;
            case BURROW -> BURROW_DOWN + BURROW_HIDDEN + 20;
            case CHARGE -> CHARGE_WARN + (int) Math.ceil(CHARGE_LENGTH / 0.9) + 12;
            default -> ROCKS_WARN + 14;
        };
    }

    @Override
    protected void finish() {
        if (isInvisible()) {
            setInvisible(false);
            setInvulnerable(false);
        }
        cooldown = switch (move) {
            case CHARGE, ROCKS -> 36;
            case BURROW -> 6;
            default -> 16 + random.nextInt(12);
        };
        super.finish();
    }
}
