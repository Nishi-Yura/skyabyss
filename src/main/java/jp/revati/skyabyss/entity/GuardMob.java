package jp.revati.skyabyss.entity;

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
            action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation." + animPrefix + "." + name));
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
}
