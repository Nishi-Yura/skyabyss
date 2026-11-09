package jp.revati.skyabyss.entity.foe.late;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 熾吐き (灰の荒野。遠くから撃つ。A 熾のヒキガエル): 10〜18 の距離を保ち、熾の塊を山なりに吐く
// (予告 1.0 秒: 頬の袋が光って膨らみ、狙った人の足もとに円 1.8)。落ちた所に 3 秒の燃える溜まり (中の人は火がつく)。
// 寄られると (4 より近い) 灰を吹いて (予告 0.5 秒、自分のまわり 3: 目くらみ 1.5 秒) 下がる
public class EmberSpitter extends LateFoe {
    public static final int WARN = 20;
    public static final double RADIUS = 1.8;
    public static final int END = 26;
    public static final int POOL = 60;
    public static final int PUFF_WARN = 10;
    public static final double PUFF_RADIUS = 3;
    public static final int PUFF_END = PUFF_WARN + 6;
    public static final double MIN_RANGE = 10;
    public static final double MAX_RANGE = 18;
    private boolean puffing;

    public EmberSpitter(EntityType<? extends Monster> type, Level level) {
        super(type, level, "ember_spitter", MobSounds.MOSS_CRAWLER_AMBIENT, MobSounds.MOSS_CRAWLER_HURT, MobSounds.MOSS_CRAWLER_DEATH, "spit", "puff", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new KeepDistanceGoal(this, MIN_RANGE, MAX_RANGE));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 4) {
            puffing = true;
            begin(target, "puff");
            Telegraph.in(server(), TelegraphShape.circle(getX(), getZ(), PUFF_RADIUS))
                    .floor(getY()).warn(PUFF_WARN).danger().owner(this).target(target)
                    .victims(this::canHit)
                    .onImpact(server -> server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 40, 1.2, 0.4, 1.2, 0.02))
                    .onHit((server, player) -> player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this))
                    .start();
            return;
        }
        if (distance > MAX_RANGE + 2) {
            return;
        }
        puffing = false;
        begin(target, "spit");
        double x = target.getX();
        double z = target.getZ();
        double y = target.getY();
        Telegraph.in(server(), TelegraphShape.circle(x, z, RADIUS))
                .floor(y).warn(WARN).danger().owner(this).target(target)
                .startSound(MobSounds.MOSS_CRAWLER_AMBIENT.get(), 1.0f, 0.6f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 16, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0.1);
                    pool(server, x, y, z);
                })
                .onHit((server, player) -> player.hurt(attackSource(), damage()))
                .start();
    }

    // 燃える溜まり (3 秒。中の人に火)。予告の印は出さない (見える炎の粒が溜まりの形)
    private void pool(ServerLevel server, double x, double y, double z) {
        for (int t = 0; t < POOL; t += 10) {
            final int at = t;
            jp.revati.skyabyss.rpg.skill.SkillEffects.later(server, at, () -> {
                server.sendParticles(ParticleTypes.FLAME, x, y + 0.1, z, 12, RADIUS * 0.45, 0.05, RADIUS * 0.45, 0.01);
                for (ServerPlayer p : server.players()) {
                    if (canHit(p) && Math.hypot(p.getX() - x, p.getZ() - z) < RADIUS && Math.abs(p.getY() - y) < 1.5) {
                        p.igniteForSeconds(2);
                    }
                }
            });
        }
    }

    @Override
    protected int length() {
        return puffing ? PUFF_END : END;
    }

    @Override
    protected void finish() {
        if (puffing) {
            retreat = 30;
            cooldown = 20;
        } else {
            cooldown = 45 + random.nextInt(25);
        }
        puffing = false;
    }
}
