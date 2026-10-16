package jp.revati.skyabyss.titan.storm.mob;

import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 雷翼の背の敵 (羽守・雷蛾・雷乞い) の共通部分。潮殻の背の敵 (TideMob) と同じ作り (岩鯨の番兵 GuardMob の型を使う: 遠くでも消えない、試練の個体は保存しない、予告の粒)。
// アニメーションの制御は 4 つ。後に足したものほど優先される (同じ骨を動かすときは後のものが勝つ):
//   move   待機・移動 (state() が姿勢を返す間は止める)
//   state  かがみ・突撃・痺れなど、続く姿勢 (state() が返す名前。null なら止める)
//   hurt   被弾 (短い身震い)
//   action 攻撃・目覚め・死亡など、サーバーから playAction で流す動作
public abstract class StormMob extends GuardMob {
    // 試練の仕組み (背中のダンジョン) とテスト用コマンドが湧かせた個体の印。この印があるとディスクに保存しない
    public static final String TRIAL_TAG = "skyabyss_storm_trial";
    // 雷翼の背 (12 の 3.1節)。ディメンションの登録は背中の担当 (担当 1)。ここでは鍵だけ持つ
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID, "storm_back"));

    // 狙ってよい人 (生きていて、観戦者でも創造モードでもない)
    public static boolean fair(LivingEntity e) {
        return e != null && e.isAlive() && !(e instanceof Player p && (p.isSpectator() || p.isCreative()));
    }

    // 相手のほうへ向きを変える (一 tick に最大 max 度)
    protected void face(Entity t, float max) {
        float want = (float) (Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * (180 / Math.PI)) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, max);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    // 水平の向き (前) と、相手への向きの角度が half 度以内か
    protected boolean inFront(Entity t, double half) {
        Vec3 to = t.position().subtract(position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) {
            return true;
        }
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        return look.dot(to.normalize()) >= Math.cos(Math.toRadians(half));
    }

    private final String prefix;
    private final String[] actionNames;

    protected StormMob(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
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

    // 移動のアニメーションを止めるか (像・裏返りの間)
    protected boolean frozen() {
        return false;
    }

    private RawAnimation anim(String name, boolean loop) {
        String full = "animation." + prefix + "." + name;
        return loop ? RawAnimation.begin().thenLoop(full) : RawAnimation.begin().thenPlay(full);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = anim("idle", true);
        RawAnimation walk = anim("walk", true);
        controllers.add(new AnimationController<>(this, "move", 4, s -> {
            if (frozen() || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(s.isMoving() ? walk : idle);
        }));
        controllers.add(new AnimationController<>(this, "state", 4, s -> {
            String name = state();
            if (name == null || isDeadOrDying()) {
                return PlayState.STOP;
            }
            return s.setAndContinue(stateAnimation(name));
        }));
        controllers.add(new AnimationController<>(this, "hurt", 0, s -> PlayState.STOP)
                .triggerableAnim("hurt", anim("hurt", false)));
        AnimationController<StormMob> action = new AnimationController<>(this, "action", 0, s -> PlayState.STOP);
        for (String name : actionNames) {
            action.triggerableAnim(name, anim(name, false));
        }
        action.triggerableAnim("death", anim("death", false));
        controllers.add(action);
    }

    // 続く姿勢の名前から、流すアニメーション (ふくらみのように「始め → 続き」を持つものは上書きする)
    protected RawAnimation stateAnimation(String name) {
        return anim(name, true);
    }

    // 最後のこまで流して止める (かがみのように「最後の姿勢のまま待つ」もの)
    protected RawAnimation hold(String name) {
        return RawAnimation.begin().thenPlayAndHold("animation." + prefix + "." + name);
    }

    protected RawAnimation sequence(String first, String loop) {
        return RawAnimation.begin().thenPlay("animation." + prefix + "." + first).thenLoop("animation." + prefix + "." + loop);
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
