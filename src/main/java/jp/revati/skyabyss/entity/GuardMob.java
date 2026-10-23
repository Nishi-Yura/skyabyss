package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 岩鯨の背に棲む者たちの共通部分。
// 動き (待機・歩き) は自動で切り替え、攻撃などの動作はサーバーから playAction で流す (全員の画面に同期される)
public abstract class GuardMob extends Monster implements GeoEntity, jp.revati.skyabyss.titan.group.GroupOwned {
    // 一時の個体 (老いた歩哨の骨笛の射手・骨負い): 保存しない
    public static final String TEMP_TAG = "skyabyss_temp_mob";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final String[] actions;

    protected GuardMob(EntityType<? extends Monster> type, Level level, String animPrefix, String... actions) {
        super(type, level);
        this.idle = RawAnimation.begin().thenLoop("animation." + animPrefix + ".idle");
        this.walk = RawAnimation.begin().thenLoop("animation." + animPrefix + ".walk");
        this.actions = actions;
        this.animPrefix = animPrefix;
        this.xpReward = 12;
    }

    private final String animPrefix;

    // 動きの名前の頭 (animation.<これ>.idle など)
    protected final String animPrefix() {
        return animPrefix;
    }

    // どのグループ (パーティ・一人) の試練の敵か。null なら誰にでも見えて誰でも狙える。湧かせる側が addFreshEntity の前に入れる
    @javax.annotation.Nullable
    private java.util.UUID groupId;

    @Override
    @javax.annotation.Nullable
    public java.util.UUID groupId() {
        return groupId;
    }

    @Override
    public void setGroupId(@javax.annotation.Nullable java.util.UUID group) {
        this.groupId = group;
    }

    // 別のグループの人にはパケットを送らない (見えない・当たらない・音も届かない)
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return super.broadcastToPlayer(player) && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, player);
    }

    // 別のグループの人を狙わない
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return super.canAttack(target) && !(target instanceof net.minecraft.server.level.ServerPlayer sp && (jp.revati.skyabyss.titan.group.ReentryRules.guarded(sp) || jp.revati.skyabyss.party.Downed.isDowned(sp)))
                && jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(this, target);
    }

    // 鳴き声・足音は自分のグループにだけ
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!jp.revati.skyabyss.titan.group.GroupOwned.playToGroup(this, sound, volume, pitch)) {
            super.playSound(sound, volume, pitch);
        }
    }

    // 攻撃などの動作アニメーションを流す (サーバー側で呼ぶ)
    public void playAction(String name) {
        triggerAnim("action", name);
        if (!name.equals("hurt") && guardsAgainstHurt(name)) {
            lastActionTick = tickCount;
        }
    }

    // その動作の間は被弾の動きで上書きしないか (既定は全部。鼓動もどきの「呼ぶ」のような、繰り返す飾りの動作は false にする)
    protected boolean guardsAgainstHurt(String action) {
        return true;
    }

    // 最後に被弾以外の動作を流した tick (被弾の動きで攻撃の構え・盾の受けを上書きしないため)
    private int lastActionTick = -1000;

    // その動作のアニメを持っているか (描画側: 倒れるアニメがあるモブはバニラの横倒しをしない)
    public boolean hasAction(String name) {
        for (String a : actions) {
            if (a.equals(name)) {
                return true;
            }
        }
        return false;
    }

    // 被弾・倒れるアニメを GuardMob が流すか (自分で流す種は false のまま。tools/gen/mobs/gen_guard_hurt_death.py で付けた種は true)
    protected boolean autoHurtDeath() {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        // 構え・攻撃・盾の受けの途中 (動作を流してから 1.5 秒の内) は被弾の動きを流さない。予告の形を崩さない
        if (hit && autoHurtDeath() && !level().isClientSide && isAlive() && hasAction("hurt") && tickCount - lastActionTick > 30) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (autoHurtDeath() && !level().isClientSide && hasAction("death")) {
            playAction("death");
        }
    }

    // ---- 層の主・歪んだ王の見た目 (abyss.boss.BossLook)。0 ならふつうの姿
    private static final EntityDataAccessor<Integer> BOSS_LOOK = SynchedEntityData.defineId(GuardMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOSS_ACT = SynchedEntityData.defineId(GuardMob.class, EntityDataSerializers.INT);
    private int bossSerial;
    // 画面の側だけ: 見た値と、その値になった tick
    private int bossSeenAct;
    private int bossActAt;
    private int bossSeenPhase;
    private int bossPhaseAt = -1000;
    private int bossSwingAt = -1000;
    private boolean bossSwung;
    private static final java.util.Map<String, RawAnimation> BOSS_ANIMS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_LOOK, 0).define(BOSS_ACT, 0);
    }

    public int bossLook() {
        return entityData.get(BOSS_LOOK);
    }

    public void setBossLook(int look) {
        entityData.set(BOSS_LOOK, look);
    }

    public int bossAct() {
        return entityData.get(BOSS_ACT);
    }

    public void setBossAct(int code, int ticks, boolean strike) {
        bossSerial = (bossSerial + 1) & 0x7f;
        entityData.set(BOSS_ACT, (code & 0xff) | (Math.min(255, Math.max(0, ticks)) << 8) | (strike ? 1 << 16 : 0) | (bossSerial << 17));
    }

    public void clearBossAct() {
        entityData.set(BOSS_ACT, 0);
    }

    // 主・王は倒れる動きを長めに見せる (戦いの決まりは死んだ時点で進む。体が消えるのを遅らせるだけ)
    @Override
    protected void tickDeath() {
        if (bossLook() == 0) {
            super.tickDeath();
            return;
        }
        ++deathTime;
        if (deathTime >= jp.revati.skyabyss.abyss.boss.BossLook.DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    private static RawAnimation bossAnim(String name, int kind) {
        return BOSS_ANIMS.computeIfAbsent(kind + name, k -> switch (kind) {
            case 0 -> RawAnimation.begin().thenLoop(name);
            case 1 -> RawAnimation.begin().thenPlayAndHold(name);
            default -> RawAnimation.begin().thenPlay(name);
        });
    }

    // 主・王の姿で「殻を閉じた」動きを見せるか (白い貝守の体の主)。体の敵が上書きする
    protected boolean bossClosed() {
        return false;
    }

    // 溜めの速さ (2 秒で作った動きを、予告の長さで終わるように)
    private double bossSpeed() {
        int act = bossAct();
        int ticks = (act >> 8) & 0xff;
        if (bossLook() == 0 || act == 0 || (act & (1 << 16)) != 0 || ticks <= 0) {
            return 1.0;
        }
        return jp.revati.skyabyss.abyss.boss.BossLook.WINDUP_SEC * 20.0 / ticks;
    }

    // 主・王の動き: 倒れる > 段の変わり目 > 技の溜め・当たり。どれでもなければ止めて、待機・歩きに任せる
    private PlayState bossState(AnimationState<GuardMob> state) {
        int look = bossLook();
        if (look == 0) {
            return PlayState.STOP;
        }
        String stem = "animation." + jp.revati.skyabyss.abyss.boss.BossLook.animStem(look) + ".";
        AnimationController<GuardMob> c = state.getController();
        if (isDeadOrDying()) {
            return state.setAndContinue(bossAnim(stem + "death", 1));
        }
        int phase = jp.revati.skyabyss.abyss.boss.BossLook.phase(look);
        if (phase != bossSeenPhase) {
            if (phase > bossSeenPhase && tickCount > 5) {
                bossPhaseAt = tickCount;
                c.forceAnimationReset();
            }
            bossSeenPhase = phase;
        }
        if (tickCount - bossPhaseAt < jp.revati.skyabyss.abyss.boss.BossLook.PHASE_TICKS) {
            return state.setAndContinue(bossAnim(stem + "phase", 1));
        }
        int act = bossAct();
        if (act != bossSeenAct) {
            bossSeenAct = act;
            bossActAt = tickCount;
            c.forceAnimationReset();
        }
        // ふつうの殴り (体の敵の近接。腕を振る合図 swinging)
        boolean striking = act != 0 && (act & (1 << 16)) != 0 && tickCount - bossActAt <= jp.revati.skyabyss.abyss.boss.BossLook.STRIKE_TICKS;
        boolean winding = act != 0 && (act & (1 << 16)) == 0;
        if (swinging && !bossSwung && !winding && !striking) {
            bossSwingAt = tickCount;
            c.forceAnimationReset();
        }
        bossSwung = swinging;
        if (!winding && !striking && tickCount - bossSwingAt < 10) {
            return state.setAndContinue(bossAnim(stem + "attack", 2));
        }
        if (act == 0 || (!winding && !striking)) {
            // 殻にこもっている間 (白殻の主): 首を引っこめた姿
            return bossClosed() ? state.setAndContinue(bossAnim(stem + "closed", 0)) : PlayState.STOP;
        }
        String move = jp.revati.skyabyss.abyss.boss.BossLook.actName(act & 0xff);
        if ((act & (1 << 16)) == 0) {
            return state.setAndContinue(bossAnim(stem + "windup_" + move, 1));
        }
        return state.setAndContinue(bossAnim(stem + "strike_" + move, 2));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            int look = bossLook();
            if (look != 0) {
                String stem = "animation." + jp.revati.skyabyss.abyss.boss.BossLook.animStem(look) + ".";
                return state.setAndContinue(bossAnim(stem + (state.isMoving() ? "walk" : "idle"), 0));
            }
            return state.setAndContinue(state.isMoving() ? walk : idle);
        }));
        AnimationController<GuardMob> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        for (String name : actions) {
            // 倒れる動きは、終わった姿で止める (消えるまで元の姿に戻らない)
            String id = "animation." + animPrefix + "." + name;
            action.triggerableAnim(name, name.equals("death") ? RawAnimation.begin().thenPlayAndHold(id) : RawAnimation.begin().thenPlay(id));
        }
        controllers.add(action);
        controllers.add(new AnimationController<>(this, "boss", 3, this::bossState).setAnimationSpeedHandler(m -> m.bossSpeed()));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // 足音。高さの差で調子が変わらないよう、ほんの少し音程を揺らす
    protected void modStep(SoundEvent sound, float volume) {
        playSound(sound, volume, 0.9f + random.nextFloat() * 0.2f);
    }

    // 番兵は試練の一部なので、遠くに離れても消えない
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ---- 試練で湧いた者 (広間の番兵・番兵長・心臓) の扱い ----

    // 試練の仕組みが湧かせた個体か (スポーンエッグなどで出したものは、ふつうのモブとして振る舞う)
    public boolean isTrialMob() {
        return getTags().contains(TitanBackTrials.MOB_TAG) || getTags().contains(TitanBackTrials.HEART_TAG);
    }

    // 試練の戦いの状態はメモリにしかないので、試練の個体はディスクに書かない (保存して終了したあとに二重に湧かないように)
    @Override
    public boolean shouldBeSaved() {
        // 地上の場所の湧き台・章のひとり歩き (guide.SiteSpawner / ChapterMobSpawner) の個体も保存しない
        return !isTrialMob() && !getTags().contains(jp.revati.skyabyss.guide.SiteSpawner.TAG) && !getTags().contains(TEMP_TAG) && super.shouldBeSaved();
    }

    // 人へ与える傷の出どころ (番兵長と心臓の技が使う)。
    // ピースフルのあいだは試練に入れない (TitanBackTrials) ので、バニラどおりモンスターの攻撃として与える
    public DamageSource attackSource() {
        return damageSources().mobAttack(this);
    }

    // 技の予告の粒。近くの人ごとに「制限を無視する」印を付けて送る (パーティクル「最小」の設定でも描かれる)
    protected void warn(ServerLevel server, ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : Players.snapshot(server.players())) {
            if (player.distanceToSqr(x, y, z) < 64 * 64) {
                server.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    // from から離れる向きの、歩いて行ける点 (向きを少しずつ振って探す。なければ null)。逃げる・距離をとる動きに使う
    @javax.annotation.Nullable
    public static net.minecraft.world.phys.Vec3 awayPoint(net.minecraft.world.entity.PathfinderMob mob, net.minecraft.world.phys.Vec3 from, double dist) {
        net.minecraft.world.phys.Vec3 away = mob.position().subtract(from).multiply(1, 0, 1);
        double base = away.lengthSqr() < 1.0E-4 ? mob.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
        for (int i = 0; i < 8; i++) {
            double a = base + (mob.getRandom().nextDouble() - 0.5) * Math.PI * 0.8 + (i > 4 ? Math.PI * 0.5 * (mob.getRandom().nextBoolean() ? 1 : -1) : 0);
            double x = mob.getX() + Math.cos(a) * dist;
            double z = mob.getZ() + Math.sin(a) * dist;
            net.minecraft.core.BlockPos column = net.minecraft.core.BlockPos.containing(x, mob.getY(), z);
            if (!mob.level().hasChunkAt(column)) {
                continue;
            }
            int y = mob.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(x, y, z, 0);
            if (path != null && path.canReach()) {
                return new net.minecraft.world.phys.Vec3(x, y, z);
            }
        }
        return null;
    }
}
