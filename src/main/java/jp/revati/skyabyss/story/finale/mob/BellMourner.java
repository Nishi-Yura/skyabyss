package jp.revati.skyabyss.story.finale.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.finale.BellIsle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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

// 鐘の亡者 (鐘の島。docs/design/50 の 4.2.1・一品一案「割れた鐘を抱く亡者」)。前の都の人の抜け殻。
//   打つ     近くでは割れた鐘で殴る (予告 0.35 秒)
//   鳴らす   近く (SILENCE_NEAR) で鐘が鳴っていて、獲物が見えていれば、鐘を頭上へ振り上げ (予告 1 秒) → 割れた鐘を鳴らして、まわりの鐘の固まりを消す
//            (BellIsle.silence。鐘雲がほどける)。倒すと止む
public class BellMourner extends CloudMob {
    public static final String ID = "bell_mourner";
    public static final double HP = 78;
    public static final double DMG = 12;
    // 鳴っている鐘を探す広さ (鐘雲の固まる半径 16 + 少し)
    static final double SILENCE_NEAR = 20;
    static final int RING_TELL = 20;
    static final int RING_COOLDOWN = 160;

    public BellMourner(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "ring", "strike", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Ring());
        goalSelector.addGoal(2, new Tell(this, 0.9, 2.2, 7, 30, "strike", 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 鳴らす: 鐘を振り上げているあいだは止まる。振り下ろしで近くの鐘の固まりを消す
    final class Ring extends Goal {
        private int t = -1;
        private int cooldown = 60;

        Ring() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) <= 18 && level() instanceof ServerLevel sl
                    && BellIsle.ringingNear(sl, blockPosition(), SILENCE_NEAR) > 0;
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= RING_TELL + 6 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            getNavigation().stop();
            playAction("ring");
            playSound(CloudMobsSetup.MOURNER_TELL.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            getNavigation().stop();
            LivingEntity tg = getTarget();
            if (tg != null) {
                getLookControl().setLookAt(tg, 30f, 30f);
            }
            if (t < RING_TELL && t % 3 == 0) {
                // 振り上げた鐘のまわりに朝の光の粒 (予告)
                sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 2.4, getZ(), 4, 0.5, 0.3, 0.5, 0.02);
            }
            if (t == RING_TELL) {
                playSound(CloudMobsSetup.MOURNER_RING.get(), 1.6f, 0.8f);
                sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.6, getZ(), 1, 0, 0, 0, 0);
                BellIsle.silence(sl, blockPosition(), SILENCE_NEAR);
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = RING_COOLDOWN;
        }
    }
}
