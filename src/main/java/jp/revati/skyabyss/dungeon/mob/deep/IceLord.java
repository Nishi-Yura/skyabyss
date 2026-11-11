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
// 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
//   二連の薙ぎ 右前脚の扇 (0.8 秒) → 体を返して左前脚の扇 (1.3 秒)。寄った人へ
//   叩きつけ  立ち上がって両前脚を掲げ、床へ叩きつける (予告 1.2 秒)。前へ氷の棘の円が五つ、少しずつ遅れて走る。叩いたあとに隙
//   登場は雪の床を割って背の氷柱が突き出し、頭が出て唸り、体を引き上げて雪を払い、立ち上がって吠える (intro)。後半も立って吠える (scream)
public class IceLord extends DungeonBoss {
    static final int SWIPE = 1;
    static final int ICICLES = 2;
    static final int CHARGE = 3;
    static final int MAUL = 4;
    static final int SLAM = 5;
    public static final int SWIPE_WARN = 16;
    public static final double SWIPE_RANGE = 6;
    public static final int ICICLE_WARN = 24;
    public static final int CHARGE_WARN = 22;
    public static final double CHARGE_LENGTH = 13;
    public static final int MAUL_HIT2 = 26;
    public static final int SLAM_WARN = 24;
    private int lastMaul = -200;
    private int lastSlam = -300;
    private int lastIcicles = -200;
    private int lastCharge = -200;

    public IceLord(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ice_lord", () -> SoundEvents.POLAR_BEAR_AMBIENT, () -> SoundEvents.POLAR_BEAR_HURT, () -> SoundEvents.POLAR_BEAR_DEATH,
                "swipe", "maul", "roar", "slam", "charge", "intro", "scream", "hurt", "death");
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
                jp.revati.skyabyss.combat.prop.AttackProps.fall(server(), "icicle", this, new Vec3(s.x, getY(), s.z), ICICLE_WARN, 12, 14f, 1.4f, 16);
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
        if (distance <= 14 && tickCount - lastSlam > 260) {
            move = SLAM;
            lastSlam = tickCount;
            begin(target, "slam");
            cue("ice_lord", "slam", SLAM_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            for (int k = 0; k < 5; k++) {
                Vec3 c = position().add(f.scale(3.0 + k * 2.6));
                Telegraph.in(server(), TelegraphShape.circle(c.x, c.z, 2.0))
                        .floor(getY()).warn(SLAM_WARN + k * 3).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onImpact(server -> {
                            jp.revati.skyabyss.combat.prop.AttackProps.rise(server, "ice_spike", this, new Vec3(c.x, getY(), c.z), random.nextFloat() * 360f, 0.75f, 16);
                            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), c.x, getY() + 0.8, c.z, 24, 0.6, 0.9, 0.6, 0.12);
                            server.playSound(null, c.x, getY(), c.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2f, 0.5f);
                        })
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 1.0f);
                            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                            player.hurtMarked = true;
                        })
                        .start();
            }
            return;
        }
        if (distance <= SWIPE_RANGE && tickCount - lastMaul > 180 && random.nextFloat() < 0.5f) {
            move = MAUL;
            lastMaul = tickCount;
            begin(target, "maul");
            cue("ice_lord", "maul", MAUL_HIT2);
            claw(target, SWIPE_WARN);
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

    private void claw(LivingEntity target, int warn) {
        Vec3 f = forward();
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), SWIPE_RANGE, Math.toRadians(110)))
                .floor(getY()).warn(warn).danger().owner(this).target(target)
                .victims(this::canHit)
                .onHit((server, player) -> player.hurt(attackSource(), damage() * 0.85f))
                .start();
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == MAUL && tick == SWIPE_WARN) {
            LivingEntity t = getTarget();
            if (t != null && t.isAlive()) {
                lockYawToward(t);
                claw(t, MAUL_HIT2 - SWIPE_WARN);
            }
        }
    }

    // 登場: 雪の床を割って這い出て、立ち上がって吠える (アニメ intro 4.8 秒)
    @Override
    protected String introAnim() {
        return "intro";
    }

    @Override
    protected int introLength() {
        return 96;
    }

    @Override
    protected int introCardAt() {
        return 64;
    }

    @Override
    protected void enterPhase2(LivingEntity target) {
        playAction("scream");
        super.enterPhase2(target);
    }

    @Override
    protected String entrance() {
        return "grow";
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
            case MAUL -> MAUL_HIT2 + 12;
            case SLAM -> SLAM_WARN + 12 + 14;
            default -> SWIPE_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == CHARGE || move == SLAM) {
            // 突っ込んだあとは滑って止まる (隙)
            opening();
        }
        cooldown = move == SWIPE ? 14 + random.nextInt(10) : 24;
        super.finish();
    }
}
