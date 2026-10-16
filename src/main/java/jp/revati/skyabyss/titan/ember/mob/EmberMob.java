package jp.revati.skyabyss.titan.ember.mob;

import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 余燼の背の敵の共通部分 (docs/design/13_ember.md の 5.1節)。潮殻の敵 (TideMob) と同じ型:
// 岩鯨の番兵 GuardMob の決まり (遠くでも消えない、試練の個体は保存しない、予告の粒) に、アニメーションの制御 4 つを足す。
// 後に足したものほど優先される (同じ骨を動かすときは後のものが勝つ):
//   move   待機・移動 (frozen() の間は止める。walkAnimation() で燃え上がった歩きなどに替えられる)
//   state  丸まり・目を回すなど、続く姿勢 (state() が返す名前。null なら止める)
//   hurt   被弾 (短い身震い)
//   action 攻撃・殻が割れる・開くなど、サーバーから playAction で流す動作 (death を含む)
public abstract class EmberMob extends GuardMob {
    // 試練の仕組み (背中のダンジョン) とテスト用コマンドが湧かせた個体の印。この印があるとディスクに保存しない。
    // 試練の担当 (C1・C2・C3) は、湧かせた敵にこの印を付ける
    public static final String TRIAL_TAG = "skyabyss_ember_trial";

    private final String prefix;
    private final String[] actionNames;

    protected EmberMob(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
        super(type, level, prefix, actions);
        this.prefix = prefix;
        this.actionNames = actions;
    }

    @Override
    public boolean isTrialMob() {
        return getTags().contains(TRIAL_TAG) || super.isTrialMob();
    }

    // 続く姿勢のアニメーションの名前 (クライアントで毎フレーム呼ばれる。同期したデータから決める)。null なら止める
    protected String state() {
        return null;
    }

    // 移動のアニメーションを止めるか
    protected boolean frozen() {
        return false;
    }

    // 歩くときのアニメーションの名前
    protected String walkAnimation() {
        return "walk";
    }

    // 毎フレーム同じ物を返す (GeckoLib はアニメーションが替わったかを比べて、替わったときだけ繋ぎ直す)
    private final java.util.Map<String, RawAnimation> cached = new java.util.HashMap<>();

    protected RawAnimation anim(String name, boolean loop) {
        return cached.computeIfAbsent(name + (loop ? "#loop" : "#once"), k -> {
            String full = "animation." + prefix + "." + name;
            return loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full);
        });
    }

    protected RawAnimation sequence(String first, String loop) {
        return cached.computeIfAbsent(first + ">" + loop,
                k -> RawAnimation.begin().thenPlay("animation." + prefix + "." + first).thenLoop("animation." + prefix + "." + loop));
    }

    // 続く姿勢の名前から、流すアニメーション (「始め → 続き」を持つものは上書きする)
    protected RawAnimation stateAnimation(String name) {
        return anim(name, true);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = anim("idle", true);
        controllers.add(new AnimationController<>(this, "move", 4, s -> {
            if (frozen() || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(s.isMoving() ? anim(walkAnimation(), true) : idle);
        }));
        controllers.add(new AnimationController<>(this, "state", 3, s -> {
            String name = state();
            if (name == null || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(stateAnimation(name));
        }));
        controllers.add(new AnimationController<>(this, "hurt", 0, s -> PlayState.STOP)
                .triggerableAnim("hurt", anim("hurt", false)));
        AnimationController<EmberMob> action = new AnimationController<>(this, "action", 0, s -> PlayState.STOP);
        for (String name : actionNames) {
            action.triggerableAnim(name, anim(name, false));
        }
        action.triggerableAnim("death", anim("death", false));
        controllers.add(action);
    }

    // 余燼の傷の種類 (EmberIds.HEAT など) の出どころ
    public DamageSource damage(net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type,
            @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity by) {
        return new DamageSource(level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type), by);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            triggerAnim("hurt", "hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            playAction("death");
        }
    }
}
