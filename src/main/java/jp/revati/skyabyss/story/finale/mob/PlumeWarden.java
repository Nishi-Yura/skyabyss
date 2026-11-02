package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.finale.PromiseIsle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 羽の番人 (約束の島。docs/design/50 の 4.2.4・一品一案「三日月の盾の番兵」)。巡礼者の先触れに仕える番兵の抜け殻。
//   突き  REACH の内に来ると槍を下げて構え (TELL = 1 秒。地面に光の矢印)、矢印の向きへ DASH tick まっすぐ突っ込む。
//         当たった人は傷 ×1.2 と強い押し出し (雲の縁から落とされる)。盾の円の内 (PromiseIsle.shielded) は押されない。
//         足もとが無い所の手前では止まる (自分から雲へは落ちない)
public class PlumeWarden extends CloudMob {
    public static final String ID = "plume_warden";
    public static final double HP = 96;
    public static final double DMG = 13;
    static final double REACH = 9;
    static final int TELL = 20;
    static final int DASH = 9;
    static final double SPEED = 1.0;
    static final double PUSH = 1.5;

    public PlumeWarden(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "charge", "hurt", "death");
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Charge());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // dir の先 1 に足場があるか (無ければ雲の上。止まる)
    boolean groundAhead(Vec3 dir) {
        BlockPos ahead = BlockPos.containing(getX() + dir.x * 1.2, getY() - 0.5, getZ() + dir.z * 1.2);
        for (int i = 0; i < 3; i++) {
            if (!level().getBlockState(ahead.below(i)).getCollisionShape(level(), ahead.below(i)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    final class Charge extends Goal {
        private int t = -1;
        private int cooldown = 30;
        private int repath;
        private Vec3 dir = new Vec3(0, 0, 1);
        private final Set<UUID> hit = new HashSet<>();

        Charge() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity tg = getTarget();
            if (tg == null || !(level() instanceof ServerLevel sl)) {
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
            if (t < 0) {
                getLookControl().setLookAt(tg, 30f, 30f);
                if (distanceTo(tg) <= REACH && cooldown == 0 && hasLineOfSight(tg)) {
                    t = 0;
                    Vec3 to = new Vec3(tg.getX() - getX(), 0, tg.getZ() - getZ());
                    dir = to.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : to.normalize();
                    hit.clear();
                    getNavigation().stop();
                    playAction("charge");
                    playSound(CloudMobsSetup.WARDEN_TELL.get(), 1.2f, 1.0f);
                } else if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(tg, 1.0);
                }
                return;
            }
            t++;
            getNavigation().stop();
            setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            yBodyRot = getYRot();
            if (t < TELL) {
                // 光の矢印 (突っ込む筋と先の矢じり)
                if (t % 3 == 0) {
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    for (double d = 1.0; d <= SPEED * DASH + 1; d += 0.8) {
                        Vec3 q = position().add(dir.scale(d));
                        sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y + 0.12, q.z, 1, 0, 0, 0, 0);
                    }
                    Vec3 tip = position().add(dir.scale(SPEED * DASH + 1));
                    for (int s = -1; s <= 1; s += 2) {
                        for (double d = 0.4; d <= 1.6; d += 0.4) {
                            Vec3 q = tip.subtract(dir.scale(d)).add(side.scale(s * d));
                            sl.sendParticles(ParticleTypes.WAX_ON, q.x, q.y + 0.12, q.z, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return;
            }
            if (t == TELL) {
                playSound(CloudMobsSetup.WARDEN_CHARGE.get(), 1.4f, 1.0f);
            }
            if (t < TELL + DASH && groundAhead(dir) && !horizontalCollision) {
                setDeltaMovement(dir.x * SPEED, getDeltaMovement().y, dir.z * SPEED);
                hasImpulse = true;
                sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0.01);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.9, 0.5, 0.9), e -> e.isAlive() && !e.isSpectator())) {
                    if (!hit.add(p.getUUID())) {
                        continue;
                    }
                    strike(p, dmg(1.2));
                    playSound(CloudMobsSetup.WARDEN_HIT.get(), 1.2f, 1.0f);
                    if (!PromiseIsle.shielded(p)) {
                        p.push(dir.x * PUSH, 0.4, dir.z * PUSH);
                        p.hurtMarked = true;
                    }
                    if (jp.revati.skyabyss.story.finale.FinaleHints.once(p, "warden_first")) {
                        Notice.narrate(p, "message.skyabyss.isle4.warden_first");
                    }
                }
                return;
            }
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
            if (t >= TELL + DASH + 8) {
                t = -1;
                cooldown = 70;
            }
        }

        @Override
        public void stop() {
            t = -1;
            getNavigation().stop();
        }
    }
}
