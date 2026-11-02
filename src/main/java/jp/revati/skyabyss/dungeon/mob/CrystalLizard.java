package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
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

// 背晶の大蜥蜴 (docs/design/55 の 1 章、雷晶の空洞の主。形は 00 の 366 = A 背晶の大蜥蜴)。
//   噛む      前の扇 (予告 0.7 秒)
//   尾で薙ぐ  後ろの扇 (予告 0.8 秒)。後ろに人がいるとき
//   突進      前の長い帯 (予告 1.2 秒)。離れた人へ
//   溜まり    技を一つ出すたびに背の結晶が溜まる (CHARGE_MAX)。満ちると後ろ脚で立って雷を放つ:
//             足もとの輪 (半径 6) と、人ごとの雷の輪 (予告 1.6 秒)。放つと溜まりは 0 に戻る
//   主の間の結晶 (印 crystal_*) に人が技を当てて満たすと、大蜥蜴の溜まりが抜ける (Mechanics から drain)
public class CrystalLizard extends DungeonBoss {
    static final int BITE = 1;
    static final int TAIL = 2;
    static final int DASH = 3;
    static final int CHARGE_UP = 4;
    public static final int BITE_WARN = 14;
    public static final double BITE_RANGE = 6;
    public static final int TAIL_WARN = 16;
    public static final double TAIL_RANGE = 7;
    public static final int DASH_WARN = 24;
    public static final double DASH_LENGTH = 16;
    public static final int CHARGE_WARN = 32;
    public static final int CHARGE_MAX = 5;
    private int charge;
    private int lastDash = -200;

    public CrystalLizard(EntityType<? extends Monster> type, Level level) {
        super(type, level, "crystal_lizard", () -> SoundEvents.HOGLIN_AMBIENT, () -> SoundEvents.RAVAGER_HURT, () -> SoundEvents.RAVAGER_DEATH,
                "bite", "tail", "dash", "charge_up", "discharge", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.ATTACK_DAMAGE, 18)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected boolean holdsAction(String action) {
        return action.equals("charge_up");
    }

    // 主の間の結晶が満ちた: 溜まりが抜ける (Mechanics.strike から)
    public void drain() {
        charge = 0;
        if (!level().isClientSide) {
            server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 60, 1.6, 1.0, 1.6, 0.3);
            server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2f, 0.7f);
        }
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (charge >= CHARGE_MAX) {
            discharge(target);
            return;
        }
        Vec3 f = forward();
        Vec3 to = target.position().subtract(position());
        boolean behind = to.x * f.x + to.z * f.z < 0;
        if (behind && distance <= TAIL_RANGE) {
            move = TAIL;
            begin(target, "tail");
            cue("crystal_lizard", "tail", TAIL_WARN);
            // 尾は後ろの扇 (向きは人の側へ固定せず、体の後ろ)
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f.scale(-1)), TAIL_RANGE, Math.toRadians(110)))
                    .floor(getY()).warn(TAIL_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.9f);
                        player.knockback(1.5, f.x, f.z);
                    })
                    .start();
            charge++;
            return;
        }
        if (distance > 7 && distance <= DASH_LENGTH && tickCount - lastDash > 160) {
            lastDash = tickCount;
            cue("crystal_lizard", "dash", DASH_WARN);
            charge(target, DASH, "dash", DASH_WARN, DASH_LENGTH, 1.5, 0.85, () -> SoundEvents.RAVAGER_ROAR);
            charge++;
            return;
        }
        if (distance <= BITE_RANGE) {
            move = BITE;
            begin(target, "bite");
            cue("crystal_lizard", "bite", BITE_WARN);
            Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BITE_RANGE, Math.toRadians(70)))
                    .floor(getY()).warn(BITE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.1f))
                    .start();
            charge++;
        }
    }

    void discharge(LivingEntity target) {
        move = CHARGE_UP;
        charge = 0;
        begin(target, "charge_up");
        cue("crystal_lizard", "discharge", CHARGE_WARN);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        List<Vec3> spots = new ArrayList<>();
        spots.add(position());
        for (ServerPlayer p : server().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(30), this::canHit)) {
            spots.add(p.position());
        }
        for (int i = 0; i < spots.size(); i++) {
            Vec3 s = spots.get(i);
            double r = i == 0 ? 6.0 : 3.0;
            Telegraph.in(server(), TelegraphShape.circle(s.x, s.z, r))
                    .floor(getY()).warn(CHARGE_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, s.x, getY() + 1, s.z, 80, r * 0.4, 1.2, r * 0.4, 0.4);
                        server.playSound(null, s.x, getY(), s.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.4f, 1.2f);
                    })
                    .onHit((server, player) -> player.hurt(attackSource(), damage() * 1.0f))
                    .start();
        }
    }

    @Override
    protected void during(int tick) {
        super.during(tick);
        if (move == CHARGE_UP) {
            if (tick % 4 == 0) {
                server().sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.6, getZ(), 6, 1.2, 0.5, 1.6, 0.1);
            }
            if (tick == CHARGE_WARN) {
                playAction("discharge");
            }
        }
    }

    @Override
    protected int length() {
        return switch (move) {
            case BITE -> BITE_WARN + 10;
            case TAIL -> TAIL_WARN + 12;
            case DASH -> DASH_WARN + (int) Math.ceil(DASH_LENGTH / 0.85) + 12;
            default -> CHARGE_WARN + 20;
        };
    }

    @Override
    protected void finish() {
        cooldown = move == CHARGE_UP || move == DASH ? 30 : 14 + random.nextInt(10);
        super.finish();
    }
}
