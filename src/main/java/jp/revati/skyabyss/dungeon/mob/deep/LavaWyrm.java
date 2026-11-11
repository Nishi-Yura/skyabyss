package jp.revati.skyabyss.dungeon.mob.deep;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.dungeon.mob.DungeonBoss;
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
import net.minecraft.world.phys.Vec3;

// 熔岩の大蛇 (奥のダンジョン「熔岩の管の坑」の主。頁で A)。黒石の鱗の大蛇。鱗の継ぎ目と喉が熾で光る。
//   噛みつき  首を引いて前の帯を噛む (予告 0.7 秒)
//   熔岩の吐き 喉を光らせて前の扇へ吐く (予告 1.3 秒)。吐いた床は 3 秒燃える
//   潜り      体力 6 割と 3 割を切ると熔岩に潜り、人の足もとから順に三度飛び出す (光る円、予告 1.5 → 2.0 → 2.5 秒)
// 作り込み (2026-11-11 の声「他のボスたちもこの方向性で」。骨掘りの大百足と同じ方向):
//   伸びる突き 首を引き絞り、とぐろを解いて遠くまで噛む前の長い帯 (予告 0.9 秒、14)。離れた人へ。突いたあとに隙
//   尾の薙ぎ   とぐろを締めて捻り、尾で自分のまわりの輪 (内 3〜外 9) を薙ぐ (予告 0.9 秒)。寄った人へ
//   登場は熔岩の床から角が覗き、首がもたげ、とぐろが浮かび、襟巻を広げて吠える (intro)。後半も襟巻を広げて吠える (scream)
public class LavaWyrm extends DungeonBoss {
    static final int BITE = 1;
    static final int BREATH = 2;
    static final int DIVE = 3;
    public static final int BITE_WARN = 14;
    public static final double BITE_LENGTH = 6;
    public static final int BREATH_WARN = 26;
    public static final double BREATH_RANGE = 10;
    public static final int DIVE_WARN = 30;
    public static final float[] DIVE_AT = {0.6f, 0.3f};
    public static final int LUNGE_WARN = 18;
    public static final double LUNGE_LENGTH = 14;
    public static final int LASH_WARN = 18;
    static final int LUNGE = 11;
    static final int LASH = 12;
    private int lastLunge = -200;
    private int lastLash = -200;
    private int dives;
    private int lastBreath = -200;

    public LavaWyrm(EntityType<? extends Monster> type, Level level) {
        super(type, level, "lava_wyrm", () -> SoundEvents.BLAZE_AMBIENT, () -> SoundEvents.BLAZE_HURT, () -> SoundEvents.BLAZE_DEATH,
                "bite", "lunge", "breath", "lash", "dive", "intro", "scream", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1040)
                .add(Attributes.ATTACK_DAMAGE, 22)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected double reach() {
        return 5.0;
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (dives < DIVE_AT.length && hp() < DIVE_AT[dives]) {
            dives++;
            move = DIVE;
            begin(target, "dive");
            cue("lava_wyrm", "dive", DIVE_WARN);
            List<ServerPlayer> ps = server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(30), this::canHit);
            List<Vec3> spots = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                spots.add(ps.isEmpty() ? target.position() : ps.get(k % ps.size()).position());
            }
            for (int k = 0; k < spots.size(); k++) {
                Vec3 s = spots.get(k);
                Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 3.4))
                        .floor(getY()).warn(DIVE_WARN + k * 10).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onImpact(server -> {
                            jp.revati.skyabyss.combat.prop.AttackProps.rise(server, "lava_pillar", this, new Vec3(s.x, getY(), s.z), random.nextFloat() * 360f, 1.1f, 18);
                            server.sendParticles(ParticleTypes.LAVA, s.x, getY() + 0.4, s.z, 40, 1.2, 0.6, 1.2, 0.15);
                            server.playSound(null, s.x, getY(), s.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
                        })
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 1.1f);
                            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.7, 0));
                            player.hurtMarked = true;
                            player.igniteForSeconds(3);
                        })
                        .start();
            }
            return;
        }
        if (distance <= BREATH_RANGE && tickCount - lastBreath > 180) {
            move = BREATH;
            lastBreath = tickCount;
            begin(target, "breath");
            cue("lava_wyrm", "breath", BREATH_WARN);
            double a = Telegraph.angleOf(target.position().subtract(position()));
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), a, BREATH_RANGE, Math.toRadians(60)))
                    .floor(getY()).warn(BREATH_WARN).active(60).repeatEvery(20).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        for (int i = 2; i < BREATH_RANGE; i += 2) {
                            server.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * i, getY() + 0.4, getZ() + Math.sin(a) * i, 8, i * 0.2, 0.2, i * 0.2, 0.02);
                        }
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.5f);
                        player.igniteForSeconds(2);
                    })
                    .start();
            return;
        }
        if (distance > BITE_LENGTH + 1 && distance <= LUNGE_LENGTH && tickCount - lastLunge > 160) {
            move = LUNGE;
            lastLunge = tickCount;
            begin(target, "lunge");
            cue("lava_wyrm", "lunge", LUNGE_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), LUNGE_LENGTH, 1.3))
                    .floor(getY()).warn(LUNGE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.8f, 0.5f))
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.25f))
                    .start();
            return;
        }
        if (distance <= 8 && tickCount - lastLash > 200 && random.nextFloat() < 0.5f) {
            move = LASH;
            lastLash = tickCount;
            begin(target, "lash");
            cue("lava_wyrm", "lash", LASH_WARN);
            Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), 3, 9))
                    .floor(getY()).warn(LASH_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.3, getZ(), 40, 4, 0.1, 4, 0.1))
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1).normalize();
                        player.knockback(1.4, -away.x, -away.z);
                    })
                    .start();
            return;
        }
        if (distance <= BITE_LENGTH + 1) {
            move = BITE;
            begin(target, "bite");
            cue("lava_wyrm", "bite", BITE_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), BITE_LENGTH, 1.2))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.15f))
                    .start();
        }
    }

    // 登場: 熔岩の床から首をもたげ、襟巻を広げて吠える (アニメ intro 4.8 秒)
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
        return "erupt";
    }

    @Override
    protected String bossId() {
        return "lava_wyrm";
    }

    @Override
    protected String minionId() {
        return "skyabyss:ember_spitter";
    }

    @Override
    protected int length() {
        return switch (move) {
            case BREATH -> BREATH_WARN + 14;
            case LUNGE -> LUNGE_WARN + 14;
            case LASH -> LASH_WARN + 12;
            case DIVE -> DIVE_WARN + 20 + 12;
            default -> BITE_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == DIVE || move == LUNGE) {
            opening();
        }
        cooldown = move == BITE ? 12 + random.nextInt(8) : 24;
        super.finish();
    }
}
