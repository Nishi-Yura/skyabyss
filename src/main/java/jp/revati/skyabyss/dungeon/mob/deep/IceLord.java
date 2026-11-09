package jp.revati.skyabyss.dungeon.mob.deep;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.dungeon.mob.DungeonBoss;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 氷鎧の大熊 (奥のダンジョン「吹雪の氷穴」の主。頁で A)。背と肩に氷の板の生えた白い大熊。床は滑る青氷。
//   薙ぎ払い  前脚で前の扇を薙ぐ (予告 0.8 秒)
//   氷柱落とし 立ち上がって吠え、天井から人ごとの足もとと、まわりに氷柱の円 (予告 1.2 秒): 傷 + 鈍足
//   突進      滑る床を前へまっすぐ突っ込む (予告 1.1 秒、前の帯 13)
public class IceLord extends DungeonBoss {
    static final int SWIPE = 1;
    static final int ICICLES = 2;
    static final int CHARGE = 3;
    public static final int SWIPE_WARN = 16;
    public static final double SWIPE_RANGE = 6;
    public static final int ICICLE_WARN = 24;
    public static final int CHARGE_WARN = 22;
    public static final double CHARGE_LENGTH = 13;
    private int lastIcicles = -200;
    private int lastCharge = -200;

    public IceLord(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ice_lord", () -> SoundEvents.POLAR_BEAR_AMBIENT, () -> SoundEvents.POLAR_BEAR_HURT, () -> SoundEvents.POLAR_BEAR_DEATH,
                "swipe", "roar", "charge", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 880)
                .add(Attributes.ATTACK_DAMAGE, 19)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected double reach() {
        return 4.5;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (tickCount - lastIcicles > 200 && (distance > 5 || random.nextFloat() < 0.3f)) {
            move = ICICLES;
            lastIcicles = tickCount;
            begin(target, "roar");
            cue("ice_lord", "icicles", ICICLE_WARN);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.HOSTILE, 2f, 0.7f);
            List<Vec3> spots = new ArrayList<>();
            for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
                spots.add(p.position());
            }
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                spots.add(position().add(Math.cos(a) * (4 + random.nextDouble() * 9), 0, Math.sin(a) * (4 + random.nextDouble() * 9)));
            }
            for (Vec3 s : spots) {
                Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 2.5))
                        .floor(getY()).warn(ICICLE_WARN).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onImpact(server -> {
                            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), s.x, getY() + 0.5, s.z, 30, 0.8, 0.5, 0.8, 0.1);
                            server.playSound(null, s.x, getY(), s.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2f, 0.7f);
                        })
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 0.9f);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), this);
                        })
                        .start();
            }
            return;
        }
        if (distance > 5 && distance < CHARGE_LENGTH && tickCount - lastCharge > 180) {
            move = CHARGE;
            lastCharge = tickCount;
            begin(target, "charge");
            cue("ice_lord", "charge", CHARGE_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), CHARGE_LENGTH, 1.6))
                    .floor(getY()).warn(CHARGE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> setDeltaMovement(f.x * 2.0, 0.05, f.z * 2.0))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.1f);
                        player.knockback(1.8, -f.x, -f.z);
                    })
                    .start();
            return;
        }
        if (distance <= SWIPE_RANGE) {
            move = SWIPE;
            begin(target, "swipe");
            cue("ice_lord", "swipe", SWIPE_WARN);
            Vec3 f = forward();
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWIPE_RANGE, Math.toRadians(120)))
                    .floor(getY()).warn(SWIPE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> player.hurt(attackSource(), damage()))
                    .start();
        }
    }

    @Override
    protected String bossId() {
        return "ice_lord";
    }

    @Override
    protected String minionId() {
        return "skyabyss:storm_jelly";
    }

    @Override
    protected int length() {
        return switch (move) {
            case ICICLES -> ICICLE_WARN + 12;
            case CHARGE -> CHARGE_WARN + 16;
            default -> SWIPE_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == CHARGE) {
            // 突っ込んだあとは滑って止まる (隙)
            opening();
        }
        cooldown = move == SWIPE ? 14 + random.nextInt(10) : 24;
        super.finish();
    }
}
