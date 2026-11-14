package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.combat.prop.PropFx;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.foe.ContinentFoe;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 鞴の小鬼 (docs/design/55 の 4 の 7、00 の 381 = B。熔けた坑道の道中)。
//   火吹き  背の鞴で前へ火を吹きつける (前の扇、予告 0.8 秒、1 秒のあいだ燃える)。少し離れているとき
//   突き    寄られたら火掻き棒で突く (前の細い帯、予告 0.8 秒)
public class BellowsImp extends ContinentFoe {
    static final int BREATH = 1;
    static final int POKE = 2;
    public static final int WARN = 16;
    public static final double BREATH_RANGE = 5.5;
    public static final double POKE_RANGE = 2.6;
    private int move;

    public BellowsImp(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bellows_imp", () -> SoundEvents.PIGLIN_AMBIENT, () -> SoundEvents.PIGLIN_HURT, () -> SoundEvents.PIGLIN_DEATH,
                "breath", "poke", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 18)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 22);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.1, 3.5));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        Vec3 f = forward();
        if (distance <= POKE_RANGE) {
            move = POKE;
            begin(target, "poke");
            Telegraph.in(server(), TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(f), POKE_RANGE + 1.4, 0.5))
                    .floor(getY()).warn(WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onHit((server, player) -> {
                        player.hurt(attackSource(), damage() * 0.8f);
                        player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40));
                    })
                    .start();
            return;
        }
        if (distance > BREATH_RANGE) {
            return;
        }
        move = BREATH;
        begin(target, "breath");
        Telegraph.in(server(), TelegraphShape.cone(getX(), getZ(), Telegraph.angleOf(f), BREATH_RANGE, Math.toRadians(32))).prop(PropFx.shots("fireball", 3).size(0.6f))
                .floor(getY()).warn(WARN).active(20).repeatEvery(10).danger().owner(this).target(target)
                .startSound(SoundEvents.FIRECHARGE_USE, 0.8f, 0.7f)
                .victims(this::canHit)
                .onHit((server, player) -> {
                    player.hurt(attackSource(), damage() * 0.7f);
                    player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 60));
                })
                .start();
    }

    @Override
    protected void during(int tick) {
        if (move == BREATH && tick >= WARN && tick < WARN + 20 && tick % 2 == 0) {
            Vec3 f = forward();
            for (int i = 1; i <= 5; i++) {
                server().sendParticles(ParticleTypes.FLAME, getX() + f.x * i, getY() + 0.9 - i * 0.08, getZ() + f.z * i, 3, 0.15 * i, 0.1, 0.15 * i, 0.01);
            }
        }
    }

    @Override
    protected int length() {
        return move == BREATH ? WARN + 24 : WARN + 8;
    }

    @Override
    protected void finish() {
        cooldown = move == BREATH ? 46 + random.nextInt(20) : 18 + random.nextInt(12);
        move = 0;
    }
}
