package jp.revati.skyabyss.abyss.mobs;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 名のない形 (深み・docs/design/36 の 4・4.1 A 折れた殻)。深みにだけ出る。核のまわりを欠片が回る。宙に浮いて、人との距離を保つ。
//   欠片を飛ばす: 人が見えると、狙いを 1 秒見せてから (線の光) 撃つ。撃つとき、人が狙いの線から 1.3 ブロック以内なら当たる (よけられる)。
public class NamelessShape extends AbyssMobB {
    public static final String ID = "nameless_shape";
    public static final double HP = 80;
    public static final double DMG = 6;
    public static final double HIT_WIDTH = 1.3;
    private static final int SHOOT_TELL = 20;
    private static final int SHOOT_COOLDOWN = 60;
    private static final double RANGE = 16;

    public NamelessShape(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "shoot", "hurt", "death");
        this.xpReward = 16;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.FLYING_SPEED, 0.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Hover(this));
        goalSelector.addGoal(2, new Shard(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.ECHO_SHARD.get(), 0.6, 1, 2);
    }

    // 距離を保つ: 人が近ければ下がり、遠ければ寄る。高さは人の少し上
    static final class Hover extends Goal {
        private final NamelessShape mob;

        Hover(NamelessShape mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() != null && mob.getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            var t = mob.getTarget();
            Vec3 at = mob.position();
            Vec3 want = new Vec3(t.getX(), t.getY() + 2.2, t.getZ());
            Vec3 flat = new Vec3(at.x - t.getX(), 0, at.z - t.getZ());
            double d = flat.length();
            Vec3 dir = d < 1e-4 ? new Vec3(1, 0, 0) : flat.scale(1 / d);
            double goal = d < 5 ? 7 : d > 10 ? 7 : d;
            Vec3 aim = new Vec3(t.getX(), 0, t.getZ()).add(dir.scale(goal));
            Vec3 move = new Vec3(aim.x - at.x, want.y - at.y, aim.z - at.z);
            double len = move.length();
            if (len > 0.05) {
                mob.setDeltaMovement(move.scale(Math.min(0.12, len) / len));
            } else {
                mob.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    // 欠片を飛ばす (予告の線 → 撃つ。線から離れていれば外れる)
    static final class Shard extends Goal {
        private final NamelessShape mob;
        private int cooldown;
        private int wind = -1;
        private Vec3 aim;

        Shard(NamelessShape mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            var t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            var t = mob.getTarget();
            if (t == null || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            if (wind >= 0) {
                Vec3 from = mob.position().add(0, 1.2, 0);
                for (int i = 1; i <= 4; i++) {
                    Vec3 p = from.lerp(aim, i / 4.0);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                if (--wind < 0) {
                    fire(t, from);
                    cooldown = SHOOT_COOLDOWN;
                }
                return;
            }
            if (cooldown == 0 && mob.distanceTo(t) <= RANGE && mob.hasLineOfSight(t)) {
                aim = t.position().add(0, 1, 0);
                mob.playAction("shoot");
                mob.playSound(AbyssMobsBSetup.NAMELESS_SHAPE_SHOOT.get(), 0.8f, 1.0f);
                wind = SHOOT_TELL;
            }
        }

        private void fire(net.minecraft.world.entity.LivingEntity t, Vec3 from) {
            Vec3 to = t.position().add(0, 1, 0);
            Vec3 seg = aim.subtract(from);
            double l2 = seg.lengthSqr();
            double u = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, to.subtract(from).dot(seg) / l2));
            Vec3 closest = from.add(seg.scale(u));
            if (closest.distanceTo(to) <= HIT_WIDTH) {
                mob.strike(t, mob.dmg());
            }
            ((ServerLevel) mob.level()).sendParticles(ParticleTypes.SONIC_BOOM, aim.x, aim.y, aim.z, 1, 0, 0, 0, 0);
        }
    }
}
