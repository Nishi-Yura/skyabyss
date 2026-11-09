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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 珊瑚に呑まれた祭司 (奥のダンジョン「沈んだ神殿」の主。頁で A)。長衣の祭司の亡骸に珊瑚と貝、三叉の槍。
//   潮の槍    槍を引いて前の細い帯を突く (予告 0.8 秒)
//   渦        人ごとの足もとと、まわりに渦の円 (予告 1.4 秒): 傷 + 鈍足
//   満ち潮    槍を掲げて回り、自分のまわりの輪 (内 3〜外 13) に潮が満ちる (予告 1.6 秒)。祭司のそばへ寄れば避けられる
public class TempleKeeper extends DungeonBoss {
    static final int THRUST = 1;
    static final int WHIRL = 2;
    static final int FLOOD = 3;
    public static final int THRUST_WARN = 16;
    public static final double THRUST_LENGTH = 7;
    public static final int WHIRL_WARN = 28;
    public static final int FLOOD_WARN = 32;
    public static final double FLOOD_INNER = 3;
    public static final double FLOOD_OUTER = 13;
    private int lastWhirl = -200;
    private int lastFlood = -300;

    public TempleKeeper(EntityType<? extends Monster> type, Level level) {
        super(type, level, "temple_keeper", () -> SoundEvents.DROWNED_AMBIENT, () -> SoundEvents.DROWNED_HURT, () -> SoundEvents.DROWNED_DEATH,
                "thrust", "whirl", "flood", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 720)
                .add(Attributes.ATTACK_DAMAGE, 16)
                .add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (hp() < 0.75f && tickCount - lastFlood > 320) {
            move = FLOOD;
            lastFlood = tickCount;
            begin(target, "flood");
            cue("temple_keeper", "flood", FLOOD_WARN);
            Telegraph.in(server(), TelegraphShape.ring(getX(), getZ(), FLOOD_INNER, FLOOD_OUTER))
                    .floor(getY()).warn(FLOOD_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.4, getZ(), 200, FLOOD_OUTER * 0.45, 0.3, FLOOD_OUTER * 0.45, 0.2);
                        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2f, 0.6f);
                    })
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 1.1f);
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
                    })
                    .start();
            return;
        }
        if (tickCount - lastWhirl > 180 && distance > 4) {
            move = WHIRL;
            lastWhirl = tickCount;
            begin(target, "whirl");
            cue("temple_keeper", "whirl", WHIRL_WARN);
            List<Vec3> spots = new ArrayList<>();
            for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(28), this::canHit)) {
                spots.add(p.position());
            }
            for (int i = 0; i < 2; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                spots.add(position().add(Math.cos(a) * (4 + random.nextDouble() * 8), 0, Math.sin(a) * (4 + random.nextDouble() * 8)));
            }
            for (Vec3 s : spots) {
                Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, 3.2))
                        .floor(getY()).warn(WHIRL_WARN).danger().owner(this).target(target)
                        .victims(this::canHit)
                        .onImpact(server -> server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, s.x, getY() + 0.3, s.z, 40, 1.2, 0.6, 1.2, 0.1))
                        .onHit((server, player) -> {
                            player.hurt(attackSource(), damage() * 0.8f);
                            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                        })
                        .start();
            }
            return;
        }
        if (distance <= THRUST_LENGTH + 1) {
            move = THRUST;
            begin(target, "thrust");
            cue("temple_keeper", "thrust", THRUST_WARN);
            Vec3 f = target.position().subtract(position()).multiply(1, 0, 1).normalize();
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), THRUST_LENGTH, 0.8))
                    .floor(getY()).warn(THRUST_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.CRIT, getX() + f.x * 4, getY() + 1.2, getZ() + f.z * 4, 10, 1.2, 0.2, 1.2, 0.1))
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.2f))
                    .start();
        }
    }

    @Override
    protected String bossId() {
        return "temple_keeper";
    }

    @Override
    protected String minionId() {
        return "skyabyss:drowned_sailor";
    }

    @Override
    protected int length() {
        return switch (move) {
            case WHIRL -> WHIRL_WARN + 10;
            case FLOOD -> FLOOD_WARN + 12;
            default -> THRUST_WARN + 10;
        };
    }

    @Override
    protected void finish() {
        if (move == FLOOD) {
            opening();
        }
        cooldown = move == THRUST ? 14 + random.nextInt(10) : 24;
        super.finish();
    }
}
