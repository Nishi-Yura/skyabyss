package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.story.ch2.Ch2Mobs;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 潮だまり貝 (二章の地上): 待ち伏せ・珊瑚憑きの「像」の予習。動かない。
//   閉じた貝にしか見えない (前の合わせ目に小真珠がのぞく)。人が NOTICE の近さに来ると、0.8 秒かけて殻を開く (予告。足もとの円)。
//   開ききった瞬間に円の中の人を挟む (5 と鈍足 II 1.5 秒)。開いている間に全員が LEAVE より離れたら、挟まずに閉じる。
//   閉じた殻は硬い (傷は CLOSED_TAKEN 倍。倒せなくはない)。挟んだあとはしばらく開いたままで、そこが狙い目。
// 殻の開き閉じはサーバーの状態 (SHELL) を同期し、クライアントのアニメーションはその状態で選ぶ (開いたまま止まる姿があるため)
public class TidepoolClam extends GroundFoe {
    public static final int WARN = 16;
    public static final double NOTICE = 2.4;
    public static final double LEAVE = 3.4;
    public static final double REACH = 1.7;
    public static final float PINCH_DAMAGE = 5f;
    public static final int SLOW_TICKS = 30;
    public static final int SLOW_AMPLIFIER = 1;
    public static final float CLOSED_TAKEN = 0.35f;
    // 挟んでから開いたままでいる長さ、閉じる長さ、閉じてから次に開けるまで
    public static final int OPEN_HOLD = 40;
    public static final int CLOSE_TICKS = 8;
    public static final int REST = 30;

    public static final int CLOSED = 0;
    public static final int OPENING = 1;
    public static final int OPEN = 2;
    public static final int CLOSING = 3;
    public static final int DEAD = 4;

    private static final EntityDataAccessor<Integer> SHELL = SynchedEntityData.defineId(TidepoolClam.class, EntityDataSerializers.INT);
    private static final RawAnimation A_CLOSED = RawAnimation.begin().thenLoop("animation.tidepool_clam.idle");
    private static final RawAnimation A_OPENING = RawAnimation.begin().thenPlayAndHold("animation.tidepool_clam.open");
    private static final RawAnimation A_PINCH = RawAnimation.begin().thenPlay("animation.tidepool_clam.pinch").thenLoop("animation.tidepool_clam.open_hold");
    private static final RawAnimation A_CLOSING = RawAnimation.begin().thenPlayAndHold("animation.tidepool_clam.close");
    private static final RawAnimation A_DEAD = RawAnimation.begin().thenPlayAndHold("animation.tidepool_clam.death");

    // 状態の中の tick (OPENING / OPEN / CLOSING)
    private int phase;
    private Telegraph pending;

    public TidepoolClam(EntityType<? extends Monster> type, Level level) {
        super(type, level, "tidepool_clam", "hurt");
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, PINCH_DAMAGE)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHELL, CLOSED);
    }

    public int shell() {
        return entityData.get(SHELL);
    }

    private void setShell(int s) {
        entityData.set(SHELL, s);
        phase = 0;
    }

    // 動かない (狙いの目も首振りもない。近さだけで動く)
    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shell", 2, state -> state.setAndContinue(switch (shell()) {
            case OPENING -> A_OPENING;
            case OPEN -> A_PINCH;
            case CLOSING -> A_CLOSING;
            case DEAD -> A_DEAD;
            default -> A_CLOSED;
        })));
        AnimationController<TidepoolClam> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        action.triggerableAnim("hurt", RawAnimation.begin().thenPlay("animation.tidepool_clam.hurt"));
        // 撮影・開発用 (skyabyss dev anim): 状態を変えずに、開いた姿・挟む姿を見せる
        action.triggerableAnim("open", RawAnimation.begin().thenPlayAndHold("animation.tidepool_clam.open"));
        action.triggerableAnim("pinch", RawAnimation.begin().thenPlayAndHold("animation.tidepool_clam.pinch"));
        controllers.add(action);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        getNavigation().stop();
        if (cooldown > 0) {
            cooldown--;
        }
        phase++;
        switch (shell()) {
            case CLOSED -> {
                if (cooldown == 0 && nearest(NOTICE) != null) {
                    startOpening();
                }
            }
            case OPENING -> {
                // 開ききる前に、みな離れた: 挟まずに閉じる (予告は黙って取り消す)
                if (phase < WARN && nearest(LEAVE) == null) {
                    if (pending != null) {
                        pending.cancel();
                        pending = null;
                    }
                    startClosing();
                } else if (phase >= WARN) {
                    pending = null;
                    setShell(OPEN);
                }
            }
            case OPEN -> {
                if (phase >= OPEN_HOLD) {
                    startClosing();
                }
            }
            case CLOSING -> {
                if (phase >= CLOSE_TICKS) {
                    setShell(CLOSED);
                    cooldown = REST;
                }
            }
            default -> {
            }
        }
    }

    // 近くの、狙える人 (いちばん近い一人。いなければ null)
    @javax.annotation.Nullable
    private ServerPlayer nearest(double range) {
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
            double d = p.distanceToSqr(this);
            if (d <= bestD && Math.abs(p.getY() - getY()) < 2.5 && canHit(p)) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    // 開く (予告 0.8 秒): 足もとの円が満ちる。満ちた瞬間に円の中の人を挟む
    private void startOpening() {
        setShell(OPENING);
        double cx = getX();
        double cz = getZ();
        pending = Telegraph.in((ServerLevel) level(), TelegraphShape.circle(cx, cz, REACH))
                .floor(getY()).warn(WARN).danger().owner(this)
                .startSound(MobSounds.TIDEPOOL_CLAM_OPEN.get(), 1.0f, 1.0f)
                .victims(this::canHit)
                .onImpact(server -> {
                    server.playSound(null, cx, getY(), cz, MobSounds.TIDEPOOL_CLAM_SNAP.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
                    server.sendParticles(ParticleTypes.SPLASH, cx, getY() + 0.4, cz, 12, 0.5, 0.1, 0.5, 0.05);
                })
                .onHit((server, player) -> {
                    player.hurt(attackSource(), PINCH_DAMAGE);
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));
                })
                .start();
    }

    private void startClosing() {
        setShell(CLOSING);
        playSound(MobSounds.TIDEPOOL_CLAM_CLOSE.get(), 0.8f, 1.0f);
    }

    // 閉じた殻は硬い (CLOSED_TAKEN 倍)。開いている間 (開きかけ・挟んだあと) は全部通る
    @Override
    public boolean hurt(DamageSource source, float amount) {
        int s = shell();
        if ((s == CLOSED || s == CLOSING) && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            amount *= CLOSED_TAKEN;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            Telegraph.cancelAll(this);
            entityData.set(SHELL, DEAD);
        }
    }

    // 小真珠 50% (1〜2)、塩結晶 20%。素材がまだ登録されていなければ落とさない
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        Item pearl = Ch2Mobs.item(Ch2Mobs.SMALL_PEARL);
        Item salt = Ch2Mobs.item(Ch2Mobs.SALT_CRYSTAL);
        if (random.nextFloat() < 0.5f && pearl != null) {
            spawnAtLocation(new ItemStack(pearl, 1 + random.nextInt(2)));
        }
        if (random.nextFloat() < 0.2f && salt != null) {
            spawnAtLocation(new ItemStack(salt));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return shell() == CLOSED ? MobSounds.TIDEPOOL_CLAM_AMBIENT.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.TIDEPOOL_CLAM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.TIDEPOOL_CLAM_DEATH.get();
    }
}
