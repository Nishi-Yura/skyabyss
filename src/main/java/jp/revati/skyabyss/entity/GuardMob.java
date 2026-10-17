package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// 岩鯨の背に棲む者たちの共通部分。
// 動き (待機・歩き) は自動で切り替え、攻撃などの動作はサーバーから playAction で流す (全員の画面に同期される)
public abstract class GuardMob extends Monster implements GeoEntity {
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

    // 攻撃などの動作アニメーションを流す (サーバー側で呼ぶ)
    public void playAction(String name) {
        triggerAnim("action", name);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> state.setAndContinue(state.isMoving() ? walk : idle)));
        AnimationController<GuardMob> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        for (String name : actions) {
            // 倒れる動きは、終わった姿で止める (消えるまで元の姿に戻らない)
            String id = "animation." + animPrefix + "." + name;
            action.triggerableAnim(name, name.equals("death") ? RawAnimation.begin().thenPlayAndHold(id) : RawAnimation.begin().thenPlay(id));
        }
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
        return !isTrialMob() && super.shouldBeSaved();
    }

    // 人へ与える傷の出どころ (番兵長と心臓の技が使う)。
    // ピースフルのあいだは試練に入れない (TitanBackTrials) ので、バニラどおりモンスターの攻撃として与える
    public DamageSource attackSource() {
        return damageSources().mobAttack(this);
    }

    // 技の予告の粒。近くの人ごとに「制限を無視する」印を付けて送る (パーティクル「最小」の設定でも描かれる)
    protected void warn(ServerLevel server, ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(x, y, z) < 64 * 64) {
                server.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }
}
