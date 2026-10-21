package jp.revati.skyabyss.story.ch4.mob;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
import jp.revati.skyabyss.story.ch4.DeadForgePit;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 熾り蟲 (docs/design/21 の 6.1): 熱に寄る小さな群れ。背中の火の粉蛾の「いちばん熱いものへ寄る」の予習。HP 6、触れて 3。叩けば一撃。
//   寄る先 (いちばん熱いもの): 1 温石を置いた台 (消えた炉の坑の入口の間。DeadForgePit.warmStandNear。台があれば人より先に台へ寄り、台のまわりを回る)
//                              2 温石を持つ人 (16 ブロックまで) 3 近くの人 (8 ブロックまで)
//   触れて傷: 狙いと 0.9 以内で 3 (1 秒に一度)。死ぬと小さな火の粉 (傷なし)
//   人に寄るとき、温石を持っていれば一度だけ一行 (message.skyabyss.ch4g.mite.drawn)
public class CinderMite extends EmberMob {
    public static final float HEALTH = 6;
    public static final float DAMAGE = 3;
    public static final double SPEED = 0.3;
    public static final double STONE_RANGE = 16;
    public static final double NEAR_RANGE = 8;
    public static final double TOUCH = 0.9;
    private int touchCooldown;

    public CinderMite(EntityType<? extends Monster> type, Level level) {
        super(type, level, "cinder_mite");
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SeekHeatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // 温石を持っているか (手・持ち物)
    public static boolean carriesWarmStone(Player p) {
        return p.getInventory().countItem(EmberTitanSetup.WARM_STONE.get()) > 0;
    }

    // いちばん熱い人 (温石を持つ人が先)。いなければ null
    @Nullable
    public ServerPlayer hottestPlayer() {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : server.players()) {
            if (!canAttack(p) || p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double d = p.distanceTo(this);
            boolean stone = carriesWarmStone(p);
            double reach = stone ? STONE_RANGE : NEAR_RANGE;
            if (d > reach) {
                continue;
            }
            double score = stone ? d - 100 : d;
            if (score < bestD) {
                bestD = score;
                best = p;
            }
        }
        return best;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.3), getY() + 0.3, getRandomZ(0.3), 0, 0.01, 0);
            }
            return;
        }
        if (touchCooldown > 0) {
            touchCooldown--;
        }
        if (tickCount == 5 && !flocked && jp.revati.skyabyss.guide.ChapterMobSpawner.isLive(getUUID())) {
            flock();
        }
        if (tickCount % 40 == 0 && getTags().contains(FLOCK_TAG) && level().getNearestPlayer(this, FLOCK_KEEP) == null) {
            discard();
        }
        LivingEntity t = getTarget();
        if (t != null && t.isAlive() && touchCooldown == 0 && distanceTo(t) <= TOUCH + t.getBbWidth() / 2) {
            touchCooldown = 20;
            doHurtTarget(t);
        }
    }

    // ひとり歩きの一匹 (ChapterMobSpawner) は、まわりに 2〜4 匹の仲間を連れて群れになる (仲間は保存しない。人が 64 より離れると消える)
    public static final String FLOCK_TAG = "skyabyss_ch4_flock";
    public static final double FLOCK_KEEP = 64;
    private boolean flocked;

    public int flock() {
        flocked = true;
        if (!(level() instanceof ServerLevel server)) {
            return 0;
        }
        int n = 2 + random.nextInt(3);
        int made = 0;
        for (int i = 0; i < n; i++) {
            CinderMite m = Ch4Setup.CINDER_MITE.get().create(server);
            if (m == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2;
            m.moveTo(getX() + Math.cos(a) * 1.2, getY(), getZ() + Math.sin(a) * 1.2, random.nextFloat() * 360f, 0f);
            m.flocked = true;
            m.addTag(FLOCK_TAG);
            m.addTag(jp.revati.skyabyss.entity.GuardMob.TEMP_TAG);
            m.setGroupId(groupId());
            if (server.addFreshEntity(m)) {
                made++;
            }
        }
        return made;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.2, getZ(), 3, 0.15, 0.1, 0.15, 0.0);
            server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.2, getZ(), 8, 0.2, 0.15, 0.2, 0.02);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, Ch4Setup.SOOT_IRON.get(), 0.2, 1, 1);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch4Setup.MITE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch4Setup.MITE_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    // ---------------------------------------------------------------- いちばん熱いものへ寄る
    static final class SeekHeatGoal extends Goal {
        private final CinderMite mob;
        private int repath;
        private BlockPos stand;

        SeekHeatGoal(CinderMite mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return mob.tickCount % 10 == 0 && find();
        }

        @Override
        public boolean canContinueToUse() {
            return (stand != null || (mob.getTarget() != null && mob.getTarget().isAlive())) && (mob.tickCount % 20 != 0 || find());
        }

        private boolean find() {
            stand = mob.level() instanceof ServerLevel s ? DeadForgePit.warmStandNear(s, mob.blockPosition(), STONE_RANGE) : null;
            if (stand != null) {
                mob.setTarget(null);
                return true;
            }
            ServerPlayer p = mob.hottestPlayer();
            if (p != null && mob.getTarget() != p) {
                mob.setTarget(p);
                if (carriesWarmStone(p) && Growth.discover(p, "ch4_mite_drawn")) {
                    Notice.narrate(p, "message.skyabyss.ch4g.mite.drawn");
                }
            }
            return p != null;
        }

        @Override
        public void stop() {
            stand = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--repath > 0) {
                return;
            }
            repath = 6;
            if (stand != null) {
                // 台のまわりを回る (半径 1.5)
                double a = (mob.tickCount * 0.08 + mob.getId()) % (Math.PI * 2);
                Vec3 c = Vec3.atBottomCenterOf(stand);
                mob.getNavigation().moveTo(c.x + Math.cos(a) * 1.5, c.y - 1, c.z + Math.sin(a) * 1.5, 1.1);
                return;
            }
            LivingEntity t = mob.getTarget();
            if (t != null) {
                mob.getLookControl().setLookAt(t, 30f, 30f);
                mob.getNavigation().moveTo(t, 1.2);
            }
        }
    }

    // 群れ: まわり (6 ブロック) の熾り蟲の数 (自己テスト・ひとり歩きの群れの数)
    public static int swarm(ServerLevel level, Vec3 at) {
        List<CinderMite> list = level.getEntitiesOfClass(CinderMite.class, new AABB(at, at).inflate(6), CinderMite::isAlive);
        return list.size();
    }
}
