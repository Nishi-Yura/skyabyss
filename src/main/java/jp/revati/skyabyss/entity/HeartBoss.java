package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 四つの心臓 (岩鯨・潮殻・雷翼・余燼) の共通部分: 登場と倒れる演出、高さの固定。
//
// 登場 (INTRO_TICKS = 80 tick): 湧いた最初の tick から始まる。そのあいだ心臓は傷を受けず、技も狙いもしない
//   (各心臓の customServerAiStep は isIntroPlaying() のあいだ早めに戻る)。アニメーション "intro" と、各心臓の粒・音 (introEffects) が流れる。
// 倒れる (DEATH_TICKS = 100 tick): 体力が 0 になっても、すぐには消えない。アニメーション "death" と粒・音 (deathEffects) のあとで
//   戦利品 (経験値) を落とし、撃破の報告 (onDeathFinished。報酬・扉・光の柱) を出し、そこで消える。倒れている間は isAlive() が偽。
// 演出のカメラ: 別の仕組みが画面を決められるよう、次を出す。
//   - HeartCinematicEvent (NeoForge.EVENT_BUS。サーバー側で 登場の始まり/終わり、倒れる始まり/終わり を知らせる)
//   - cinematicStage() / cinematicTicks() / cinematicProgress() / focusPoint() (両側で読める。登場の最中は同期データで届く)
// アニメーションは tools/install_heart_cinematics.py が書く (長さは INTRO_TICKS・DEATH_TICKS と合わせる)。
@net.neoforged.fml.common.EventBusSubscriber(modid = SkyAbyss.MODID)
public abstract class HeartBoss extends GuardMob {
    public static final int INTRO_TICKS = 80;
    public static final int DEATH_TICKS = 100;

    public enum Stage { NONE, INTRO, DEATH }

    private static final EntityDataAccessor<Boolean> DATA_INTRO = SynchedEntityData.defineId(HeartBoss.class, EntityDataSerializers.BOOLEAN);

    // 0 = まだ始めていない、1 = 流れている、2 = 終わった (保存する。読み込みで二度流さない)
    private int introState;
    private int introTicks;
    private DamageSource deathSource;
    private boolean deathStarted;
    // 高さの固定 (自分で高さを変える心臓 = 雷翼の段階 2 以外)。最初のサーバー tick の高さを覚える
    private double homeY = Double.NaN;

    // いま世界にいる心臓 (サーバー側)。人が別のディメンションへ運ばれたとき・切断したときに、ボスゲージを確実に外すための一覧。
    // 心臓の部屋が空 (人が運び出された) になると、心臓は tick されず、自分では外せない
    private static final java.util.Set<HeartBoss> LIVE = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // 各心臓のボスゲージ
    protected abstract net.minecraft.server.level.ServerBossEvent heartBar();

    // この人をボスゲージの見ている人から外す
    protected final void dropViewer(ServerPlayer player) {
        heartBar().removePlayer(player);
    }

    // ボスゲージを見ている人 (自己テスト用)
    public final java.util.Collection<ServerPlayer> barViewers() {
        return heartBar().getPlayers();
    }

    // ボスゲージを見せる (自己テスト用)
    public final void showBarTo(ServerPlayer player) {
        heartBar().addPlayer(player);
    }

    // 人が別のディメンションへ移った・切断した・復活した: どの心臓のボスゲージからも外す
    private static void dropFromAll(ServerPlayer player) {
        for (HeartBoss heart : java.util.List.copyOf(LIVE)) {
            heart.dropViewer(player);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dropFromAll(player);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dropFromAll(player);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            dropFromAll(player);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        LIVE.clear();
    }

    // 自己テスト用: いま登録されている心臓の数
    public static int liveCount() {
        return LIVE.size();
    }

    @Override
    public void remove(RemovalReason reason) {
        LIVE.remove(this);
        super.remove(reason);
    }

    protected HeartBoss(EntityType<? extends Monster> type, Level level, String animPrefix, String... actions) {
        super(type, level, animPrefix, withCinematic(actions));
    }

    private static String[] withCinematic(String[] actions) {
        String[] all = java.util.Arrays.copyOf(actions, actions.length + 2);
        all[actions.length] = "intro";
        all[actions.length + 1] = "death";
        return all;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_INTRO, false);
    }

    // ---------------------------------------------------------------- 演出の状態 (両側で読める)

    // 登場の最中か (傷を受けず、動かない)
    public boolean isIntroPlaying() {
        return entityData.get(DATA_INTRO);
    }

    public Stage cinematicStage() {
        if (isDeadOrDying()) {
            return Stage.DEATH;
        }
        return isIntroPlaying() ? Stage.INTRO : Stage.NONE;
    }

    // 演出が始まってからの tick (登場は 0 から INTRO_TICKS、倒れるは 0 から DEATH_TICKS)。演出でなければ 0
    public int cinematicTicks() {
        return switch (cinematicStage()) {
            case INTRO -> introTicks;
            case DEATH -> deathTime;
            default -> 0;
        };
    }

    public static int cinematicLength(Stage stage) {
        return switch (stage) {
            case INTRO -> INTRO_TICKS;
            case DEATH -> DEATH_TICKS;
            default -> 0;
        };
    }

    // 演出の進み具合 (0 から 1)。カメラが滑らかに動くよう、部分 tick も足す
    public float cinematicProgress(float partialTick) {
        Stage stage = cinematicStage();
        int length = cinematicLength(stage);
        return length == 0 ? 0f : Mth.clamp((cinematicTicks() + partialTick) / length, 0f, 1f);
    }

    // カメラが狙う点 (心臓の核のあたり)。各心臓が核の高さを教える
    public Vec3 focusPoint() {
        return new Vec3(getX(), coreHeight(), getZ());
    }

    // 核の高さ (ワールドの y)
    protected abstract double coreHeight();

    // テスト・コマンド用: 登場を飛ばす (最初の tick の前に呼ぶ)。すぐ戦える
    public void skipIntro() {
        introState = 2;
        introTicks = 0;
        if (!level().isClientSide) {
            entityData.set(DATA_INTRO, false);
        }
    }

    // ---------------------------------------------------------------- 各心臓が決める演出

    // 登場の 1 tick ごと (tick は 0 から INTRO_TICKS)
    protected void introEffects(ServerLevel server, int tick) {
    }

    // 倒れる演出の 1 tick ごと (tick は 1 から DEATH_TICKS)
    protected void deathEffects(ServerLevel server, int tick) {
    }

    // 倒れる演出が終わった。報酬・扉・光の柱などの撃破の報告をここで出す
    protected void onDeathFinished(ServerLevel server) {
    }

    // 自分で高さを変える心臓 (雷翼の段階 2) は、固定を使わない
    protected boolean ownsHeight() {
        return false;
    }

    // ---------------------------------------------------------------- 毎 tick

    @Override
    public void tick() {
        super.tick();
        if (isIntroPlaying()) {
            introTicks++;
        }
        if (level() instanceof ServerLevel server) {
            if (!isRemoved()) {
                LIVE.add(this);
            }
            if (!isNoGravity()) {
                // /summon の NBT 読み込みは無重力の印を消すので、毎 tick 付け直す (浮いたまま落ちない)
                setNoGravity(true);
            }
            tickCinematicServer(server);
            holdHeight();
        }
    }

    private void tickCinematicServer(ServerLevel server) {
        if (isDeadOrDying()) {
            return;
        }
        if (introState == 0) {
            introState = 1;
            introTicks = 0;
            entityData.set(DATA_INTRO, true);
            playAction("intro");
            NeoForge.EVENT_BUS.post(new HeartCinematicEvent(this, HeartCinematicEvent.Kind.INTRO_START, INTRO_TICKS));
            introEffects(server, 0);
            return;
        }
        if (introState == 1) {
            // introTicks は super.tick() の直後に数える。ここでは 1 から INTRO_TICKS
            introEffects(server, introTicks);
            if (introTicks >= INTRO_TICKS) {
                introState = 2;
                entityData.set(DATA_INTRO, false);
                NeoForge.EVENT_BUS.post(new HeartCinematicEvent(this, HeartCinematicEvent.Kind.INTRO_END, INTRO_TICKS));
            }
        }
    }

    // 浮いたまま動かない心臓が、何かに押されて (水・溶岩・突き飛ばし) 上へ流れないよう、湧いた高さに留める。
    // 地面に届く高さから離れたまま戻らない、を防ぐ (ベータで「上がったまま降りてこない」と言われた)
    private void holdHeight() {
        if (isDeadOrDying()) {
            return;
        }
        if (ownsHeight()) {
            // 高さは自分で決める (setPos)。流れ込んだ縦の速さだけ消す
            if (Math.abs(getDeltaMovement().y) > 0.01) {
                setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
            }
            return;
        }
        if (Double.isNaN(homeY)) {
            homeY = getY();
            return;
        }
        Vec3 v = getDeltaMovement();
        if (Math.abs(getY() - homeY) > 0.02 || Math.abs(v.y) > 0.01) {
            setPos(getX(), homeY, getZ());
            setDeltaMovement(v.x, 0, v.z);
        }
    }

    // 水や溶岩の流れで流されない (浮いたまま動かない心臓。流されると、届く高さから離れてしまう)
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // 登場の最中は傷を受けない。ただし /kill や奈落などの「無敵を貫く」傷は通す
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isIntroPlaying() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    // ---------------------------------------------------------------- 倒れる

    @Override
    public void die(DamageSource source) {
        boolean first = !deathStarted;
        deathStarted = true;
        deathSource = source;
        super.die(source);
        if (first && level() instanceof ServerLevel) {
            playAction("death");
            NeoForge.EVENT_BUS.post(new HeartCinematicEvent(this, HeartCinematicEvent.Kind.DEATH_START, DEATH_TICKS));
        }
    }

    // 戦利品 (経験値) は、倒れる演出が終わってから落とす
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
    }

    // 倒れている間は、決まった長さだけ立ったまま演出を流す。終わりで戦利品・報告・消える
    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server && !isRemoved()) {
            deathEffects(server, deathTime);
            if (deathTime >= DEATH_TICKS) {
                super.dropAllDeathLoot(server, deathSource != null ? deathSource : damageSources().generic());
                onDeathFinished(server);
                NeoForge.EVENT_BUS.post(new HeartCinematicEvent(this, HeartCinematicEvent.Kind.DEATH_END, DEATH_TICKS));
                server.broadcastEntityEvent(this, (byte) 60);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IntroDone", introState == 2);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("IntroDone")) {
            introState = 2;
        }
    }
}
