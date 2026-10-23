package jp.revati.skyabyss.abyss.mobs;

import java.util.List;

import org.joml.Vector3f;

import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 深淵の敵 A の 9 種の本体 (docs/design/36 の 4・4.2)。種ごとの行動は AbyssMobKind.Mode で分ける。
//   段 (STAGE: 1 元の姿・2 歪み・3 崩れ) と湧いた高さ (spawnY) は湧くときに決まる。体力と傷は AbyssScaling (高さだけで決まる)。
//   段は見た目と仕組み (崩れからの突き抜け・落とし物) にだけ使う。見た目は段ごとのメッシュ・アトラス (クライアントの AbyssMobRenderers)。
//   予告は赤い粒だけ (避雷の像・潮だまりの手・骨笛)。焦げた鳥の影は暗い粒 (影だけが見える)。
public class AbyssMob extends GuardMob {
    public static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(AbyssMob.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(AbyssMob.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.12f, 0.12f), 1.0f);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.07f, 0.05f, 0.05f), 1.4f);
    private static final DustParticleOptions CHALK = new DustParticleOptions(new Vector3f(0.9f, 0.95f, 1f), 0.8f);

    private final AbyssMobKind kind;
    private double spawnY;
    private int warn;
    private int cool;
    private boolean awake;
    private int touchCooldown;
    private int diveStage;   // 0 上で待つ、1 影を出す、2 急降下
    private int diveLeft;
    private Vec3 divePoint = Vec3.ZERO;
    private boolean touched;

    public AbyssMob(EntityType<? extends Monster> type, Level level, AbyssMobKind kind) {
        super(type, level, kind.id, actionsOf(kind));
        this.kind = kind;
        this.xpReward = kind.xp;
        this.cool = 40 + random.nextInt(40);
    }

    // 種ごとの動作アニメ (tools/gen/abyss/gen_abyss_mobs_a_anims.py)。予告の構えは予告の長さと同じ
    private static String[] actionsOf(AbyssMobKind k) {
        return switch (k.mode) {
            case FLUTE -> new String[]{"call", "hurt", "death"};
            case AMBUSH -> new String[]{"crack", "wake", "attack", "hurt", "death"};
            case SNARE -> new String[]{"grasp", "hurt", "death"};
            case STRIKE -> new String[]{"charge", "hurt", "death"};
            case DIVE -> new String[]{"shadow", "dive", "attack", "hurt", "death"};
            default -> new String[]{"attack", "hurt", "death"};
        };
    }

    // 白い貝守: 殻の開け閉めは同期した OPEN で選ぶ (身と鋏だけ。殻の揺れは待機・歩きの側)
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        if (kind != null && kind.mode == AbyssMobKind.Mode.SHELL) {
            software.bernie.geckolib.animation.RawAnimation open = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation." + kind.id + ".open");
            software.bernie.geckolib.animation.RawAnimation shut = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation." + kind.id + ".shut");
            controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "shell", 6,
                    state -> state.setAndContinue(entityData.get(OPEN) ? open : shut)));
        }
    }

    // 近づいて殴る種 (骨拾い・肋の番・起きた塩の抜け殻・白い貝守) の振り
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playAction("attack");
        }
        return hit;
    }

    // 被弾・倒れる動き (GuardMob が流す。予告の構えの途中は被弾の動きを流さない)
    @Override
    protected boolean autoHurtDeath() {
        return true;
    }

    public AbyssMobKind kind() {
        return kind;
    }

    // 白殻の主 (白い貝守の体): 殻が閉じている間は首を引っこめる (BossLook の見た目だけ)
    @Override
    protected boolean bossClosed() {
        return kind != null && kind.mode == AbyssMobKind.Mode.SHELL && !entityData.get(OPEN);
    }

    public int stage() {
        return entityData.get(STAGE);
    }

    // 湧いた高さ (体力と傷はこの高さで決まる)
    public double spawnY() {
        return spawnY;
    }

    // 湧くときに一度だけ決める (段と湧いた高さ。体力・攻撃を AbyssScaling の数字どおりにする)
    public void setStage(int form, double y) {
        this.spawnY = y;
        entityData.set(STAGE, form);
        double hp = AbyssScaling.hp(kind.baseHp, y);
        double dmg = AbyssScaling.dmg(kind.baseDamage, y);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg);
        setHealth((float) hp);
        this.xpReward = kind.xp;
    }

    // 心臓の下の上乗せ (深みの区画で湧くとき。AbyssScaling.deepSteps)。体力と傷を、その段の数字にする
    public void setDeep(double steps) {
        double hp = AbyssScaling.hpDeep(kind.baseHp, spawnY, steps);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(AbyssScaling.dmgDeep(kind.baseDamage, spawnY, steps));
        setHealth((float) hp);
    }

    public static AttributeSupplier.Builder createAttributes(AbyssMobKind k) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, k.baseHp)
                .add(Attributes.ATTACK_DAMAGE, k.baseDamage)
                .add(Attributes.MOVEMENT_SPEED, Math.max(0.0, k.baseSpeed))
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, 1).define(OPEN, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("abyss_stage", stage());
        tag.putDouble("abyss_y", spawnY);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("abyss_y")) {
            setStage(tag.getInt("abyss_stage"), tag.getDouble("abyss_y"));
        }
    }

    // Mob の構築中に呼ばれるので、kind はまだない。目標と追いかけは実行のとき (canUse) に種で決める
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override
            public boolean canUse() {
                return chaser() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override
            public boolean canUse() {
                return chaser() && super.canUse();
            }
        });
    }

    // 歩いて追う種 (塩の抜け殻は割れてから)
    private boolean chaser() {
        if (kind == null) {
            return false;
        }
        return switch (kind.mode) {
            case SWARM, SHIELD, SHELL -> true;
            case AMBUSH -> awake;
            default -> false;
        };
    }

    // 種ごとの動き (サーバー側)
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isAlive()) {
            return;
        }
        if (touchCooldown > 0) {
            touchCooldown--;
        }
        ServerLevel server = (ServerLevel) level();
        Player p = nearestPlayer(24);
        switch (kind.mode) {
            case FLUTE -> flute(server, p);
            case AMBUSH -> ambush(server, p);
            case SNARE -> snare(server);
            case SHELL -> entityData.set(OPEN, AbyssMobStats.shellOpen(tickCount));
            case SWARM_FLY -> swarmFly(server, p);
            case STRIKE -> strike(server, p);
            case DIVE -> dive(server, p);
            default -> {
            }
        }
    }

    private Player nearestPlayer(double range) {
        Player best = null;
        double bestD = range * range;
        for (Player q : level().players()) {
            if (q.isCreative() || q.isSpectator() || !q.isAlive() || !canAttack(q)) {
                continue;
            }
            double d = q.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = q;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 骨笛: 浮いて、予告のあと骨拾いを呼ぶ
    private void flute(ServerLevel server, Player p) {
        setNoGravity(true);
        if (p != null) {
            Vec3 hover = p.position().add(2.5, 3.2, 0);
            moveToward(hover, 0.12);
        }
        if (warn > 0) {
            warn--;
            ringAround(server, position().add(0, 0.8, 0), 0.9, RED, 6);
            if (warn == 0) {
                callPickers(server);
            }
        } else if (--cool <= 0 && p != null) {
            warn = AbyssMobStats.WARN_CALL;
            cool = AbyssMobStats.CALL_EVERY;
            playAction("call");
        }
    }

    private void callPickers(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.HOSTILE, 2.0f, 0.6f);
        int near = server.getEntities(this, new AABB(blockPosition()).inflate(16),
                e -> e instanceof AbyssMob m && m.kind == AbyssMobKind.BONE_PICKER).size();
        int n = Math.max(0, Math.min(2, 6 - near));
        for (int i = 0; i < n; i++) {
            AbyssMob m = AbyssMobsA.create(AbyssMobKind.BONE_PICKER, server, stage(), spawnY);
            if (m != null) {
                m.moveTo(getX() + (i == 0 ? -2.0 : 2.0), getY() - 2.5, getZ(), random.nextFloat() * 360f, 0f);
                m.addTag(TEMP_TAG);
                server.addFreshEntity(m);
            }
        }
    }

    // ---------------------------------------------------------------- 塩の抜け殻: 固まって待ち、近づくと割れる
    private void ambush(ServerLevel server, Player p) {
        if (awake) {
            return;
        }
        if (warn > 0) {
            warn--;
            ringAround(server, position().add(0, 0.2, 0), 0.7, CHALK, 5);
            if (warn == 0) {
                wake(server);
            }
        } else if (p != null && AbyssMobStats.ambushWakes(p.distanceTo(this))) {
            warn = AbyssMobStats.WARN_CRACK;
            playAction("crack");
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
        }
    }

    public void wake(ServerLevel server) {
        if (awake) {
            return;
        }
        awake = true;
        playAction("wake");
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
        server.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.05);
    }

    // ---------------------------------------------------------------- 潮だまりの手: 動かず、予告の輪の中の人を縛る
    private void snare(ServerLevel server) {
        if (warn > 0) {
            warn--;
            ringAround(server, position().add(0, 0.1, 0), AbyssMobStats.SNARE_RADIUS, RED, 14);
            if (warn == 0) {
                double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5;
                for (Player p : playersWithin(AbyssMobStats.SNARE_RADIUS, 1.5)) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AbyssMobStats.SNARE_TICKS, AbyssMobStats.SNARE_SLOW_AMP));
                    p.hurt(attackSource(), (float) dmg);
                }
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.HOSTILE, 2.0f, 0.6f);
            }
        } else if (--cool <= 0) {
            warn = AbyssMobStats.WARN_RING;
            cool = AbyssMobStats.SNARE_EVERY;
            playAction("grasp");
        }
    }

    // ---------------------------------------------------------------- 帯電の羽虫: 光る群れで人のまわりを飛ぶ
    private void swarmFly(ServerLevel server, Player p) {
        setNoGravity(true);
        if (p == null) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        double t = tickCount;
        Vec3 want = p.position().add(Math.sin(t / 7.0) * 2.2, 1.4 + Math.sin(t / 5.0) * 0.5, Math.cos(t / 7.0) * 2.2);
        moveToward(want, 0.25);
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2, getZ(), 1, 0.2, 0.2, 0.2, 0.02);
        }
        if (touchCooldown == 0 && distanceTo(p) <= 1.2) {
            touchCooldown = 20;
            playAction("attack");
            p.hurt(attackSource(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }

    // ---------------------------------------------------------------- 避雷の像: 雷を集めて落とす。段 2 からは直線の先も打つ
    private void strike(ServerLevel server, Player p) {
        if (warn > 0) {
            warn--;
            ringAround(server, divePoint, AbyssMobStats.STRIKE_RADIUS, RED, 12);
            if (warn == 0) {
                double dmg = getAttributeValue(Attributes.ATTACK_DAMAGE);
                for (Player q : playersWithin(divePoint, AbyssMobStats.STRIKE_RADIUS)) {
                    q.hurt(attackSource(), (float) dmg);
                }
                if (stage() >= 2) {
                    Vec3 dir = divePoint.subtract(position()).multiply(1, 0, 1);
                    if (dir.lengthSqr() > 1e-6) {
                        Vec3 end = divePoint.add(dir.normalize().scale(AbyssMobStats.PIERCE_LENGTH));
                        for (Player q : playersOnSegment(divePoint, end, 1.0)) {
                            q.hurt(attackSource(), (float) dmg * 0.6f);
                        }
                    }
                }
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, divePoint.x, divePoint.y + 0.5, divePoint.z, 30, 0.6, 1.0, 0.6, 0.3);
                server.playSound(null, divePoint.x, divePoint.y, divePoint.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.6f, 1.3f);
            }
        } else if (--cool <= 0 && p != null && p.distanceTo(this) <= 16) {
            divePoint = p.position();
            warn = AbyssMobStats.WARN_RING;
            cool = AbyssMobStats.STRIKE_EVERY;
            playAction("charge");
        }
    }

    // ---------------------------------------------------------------- 焦げた鳥の影: 上で待ち、暗い影を出し、急降下して掴む
    private void dive(ServerLevel server, Player p) {
        setNoGravity(true);
        if (diveStage == 2) {
            // 急降下 (diveLeft の間)
            Vec3 to = divePoint.add(0, 1.0, 0);
            moveToward(to, 1.1);
            diveLeft--;
            for (Player q : playersWithin(position(), 1.3)) {
                if (!touched) {
                    touched = true;
                    playAction("attack");
                    q.hurt(attackSource(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
                    q.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 0));
                }
            }
            if (diveLeft <= 0 || touched) {
                diveStage = 0;
                touched = false;
                cool = AbyssMobStats.DIVE_EVERY;
            }
            return;
        }
        if (p != null) {
            moveToward(p.position().add(0, 6.0, 0), 0.2);
        }
        if (diveStage == 1) {
            if (warn > 0) {
                warn--;
                server.sendParticles(DARK, divePoint.x, divePoint.y + 0.05, divePoint.z, 3, 0.5, 0.0, 0.5, 0.0);
                if (warn == 0) {
                    diveStage = 2;
                    diveLeft = 40;
                    playAction("dive");
                    server.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.4f, 0.9f);
                }
            }
            return;
        }
        if (--cool <= 0 && p != null) {
            divePoint = p.position();
            diveStage = 1;
            warn = AbyssMobStats.WARN_SHADOW;
            playAction("shadow");
        }
    }

    // ---------------------------------------------------------------- 殻と盾の受け方
    @Override
    public boolean hurt(DamageSource source, float amount) {
        float a = amount;
        if (kind.mode == AbyssMobKind.Mode.SHIELD && source.getEntity() instanceof LivingEntity attacker) {
            Vec3 front = Vec3.directionFromRotation(0, getYRot());
            Vec3 toward = attacker.position().subtract(position()).multiply(1, 0, 1);
            if (toward.lengthSqr() > 1e-6) {
                double dot = front.dot(toward.normalize());
                a = (float) (a * AbyssMobStats.shieldKeep(dot));
                if (AbyssMobStats.inFrontOfShield(dot) && level() instanceof ServerLevel s) {
                    s.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.2, getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
        if (kind.mode == AbyssMobKind.Mode.SHELL) {
            a = (float) (a * AbyssMobStats.shellKeep(tickCount));
        }
        if (kind.mode == AbyssMobKind.Mode.AMBUSH && !awake && level() instanceof ServerLevel s) {
            wake(s);
        }
        return super.hurt(source, a);
    }

    // ---------------------------------------------------------------- 落とし物 (段が上がるほど出やすい)
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        double chance = 0.35 + 0.15 * (stage() - 1);
        if (random.nextDouble() < chance) {
            spawnAtLocation(new ItemStack(AbyssMobsA.materialOf(kind), 1));
        }
    }

    // ---------------------------------------------------------------- 小さな道具
    private void moveToward(Vec3 target, double speed) {
        Vec3 d = target.subtract(position());
        double len = d.length();
        if (len < 1e-4) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setDeltaMovement(d.scale(Math.min(speed, len * 0.2) / len));
    }

    private void ringAround(ServerLevel server, Vec3 c, double r, DustParticleOptions dust, int n) {
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2 * i) / n + tickCount * 0.1;
            server.sendParticles(dust, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private List<Player> playersWithin(double r, double h) {
        return playersWithin(position(), r, h);
    }

    private List<Player> playersWithin(Vec3 c, double r, double h) {
        return level().getEntitiesOfClass(Player.class, new AABB(c.x - r, c.y - h, c.z - r, c.x + r, c.y + h, c.z + r),
                q -> q.isAlive() && !q.isCreative() && !q.isSpectator() && q.position().multiply(1, 0, 1).distanceTo(c.multiply(1, 0, 1)) <= r);
    }

    private List<Player> playersWithin(Vec3 c, double r) {
        return playersWithin(c, r, 1.5);
    }

    private List<Player> playersOnSegment(Vec3 a, Vec3 b, double width) {
        Vec3 ab = b.subtract(a).multiply(1, 0, 1);
        double len2 = ab.lengthSqr();
        return level().getEntitiesOfClass(Player.class, new AABB(a, b).inflate(width + 1, 2, width + 1), q -> {
            if (!q.isAlive() || q.isCreative() || q.isSpectator()) {
                return false;
            }
            Vec3 ap = q.position().subtract(a).multiply(1, 0, 1);
            double t = len2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ap.dot(ab) / len2));
            Vec3 closest = a.multiply(1, 0, 1).add(ab.scale(t));
            return closest.distanceTo(q.position().multiply(1, 0, 1)) <= width;
        });
    }

}
