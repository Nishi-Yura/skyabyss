package jp.revati.skyabyss.dungeon.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.entity.foe.RegionBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 地下のダンジョンの主の共通 (docs/design/55 の 2 章「予告と隙のある主 (47)。帯・技名の知らせ」)。
// 技の名前はいつも出す (名づきかどうかによらない)。体力の帯と人数の強さは dungeon/DungeonFloor が持つ。
// 掘り穴・柱など、主の間の印の場所は holes (DungeonFloor が湧かせたときに渡す。卵から出したときは空)
// つっかえ直し (2026-11-06 の声「ダンジョンのボスのムカデ、めっちゃブロックにつっかえる。他のダンジョンのボスもそうかも。体力高いだけのやつになってる」):
//   主の体は大きく (幅 2〜3.6)、道探しは広い体の通れる道を見つけられずに止まり、技は「地面にいて人が見える」ときしか出さないので、
//   柱の陰や段差でつっかえると殴られるだけになっていた。
//   - 段差: 1 ブロックまで歩いて上がる (STEP)
//   - 道が見つからない・STUCK_CHECK ごとに 0.6 も進まないときは、人へまっすぐ歩く
//   - それでも STUCK_LIMIT tick 進まない、または人が BLIND_LIMIT tick 見えないときは unstick: 人のそばの、体が収まって人の見える所へ
//     地を蹴って移る (土煙と音。主ごとに変えられる。大百足は潜る)。UNSTICK_GAP tick に一度まで
public abstract class DungeonBoss extends RegionBoss {
    public static final double STEP = 1.1;
    static final int STUCK_CHECK = 10;
    public static final int STUCK_LIMIT = 60;
    public static final int BLIND_LIMIT = 80;
    static final int UNSTICK_GAP = 160;

    protected final List<Vec3> holes = new ArrayList<>();
    private Vec3 lastPos;
    private int stuck;
    private int blind;
    private int unstickWait;

    protected DungeonBoss(EntityType<? extends Monster> type, Level level, String animPrefix, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, String... actions) {
        super(type, level, animPrefix, ambient, hurt, death, actions);
        setPersistenceRequired();
        var step = getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && step.getBaseValue() < STEP) {
            step.setBaseValue(STEP);
        }
    }

    @Override
    protected void cue(String boss, String attack, int ticks) {
        BossCue.send(this, boss, attack, ticks);
    }

    public void setHoles(List<Vec3> list) {
        holes.clear();
        holes.addAll(list);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // 体力の割合 (段の変わり目)
    protected float hp() {
        return getHealth() / getMaxHealth();
    }

    // 近づいて止まる距離 (これより遠ければ「寄りたい」)
    protected double reach() {
        return 4.0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (unstickWait > 0) {
            unstickWait--;
        }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || busy() || isInvisible()) {
            stuck = 0;
            blind = 0;
            lastPos = position();
            return;
        }
        if (tickCount % STUCK_CHECK != 0) {
            return;
        }
        boolean far = distanceTo(t) > reach() + 1.5;
        if (lastPos != null && far && position().distanceTo(lastPos) < 0.6) {
            stuck += STUCK_CHECK;
        } else {
            stuck = Math.max(0, stuck - STUCK_CHECK);
        }
        lastPos = position();
        blind = hasLineOfSight(t) ? 0 : blind + STUCK_CHECK;
        // 広い体の通れる道が見つからない・進めないときは、まっすぐ人へ
        if (far && (getNavigation().isDone() || stuck > 0)) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
        }
        if ((stuck >= STUCK_LIMIT || blind >= BLIND_LIMIT) && unstickWait == 0) {
            stuck = 0;
            blind = 0;
            unstickWait = UNSTICK_GAP;
            unstick(t);
        }
    }

    // つっかえたとき: 人のそばの、体が収まって人の見える所へ地を蹴って移る。見つからなければ何もしない
    protected void unstick(LivingEntity target) {
        Vec3 to = freeSpotNear(target);
        if (to == null) {
            return;
        }
        server().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 12, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.02);
        server().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.6f, 0.6f);
        teleportTo(to.x, to.y, to.z);
        getNavigation().stop();
        server().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, to.x, to.y + 0.5, to.z, 16, getBbWidth() * 0.4, 0.3, getBbWidth() * 0.4, 0.02);
        server().playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 0.7f);
    }

    // 人のまわり (3〜8) で、体が収まり・足もとが固く・人の見える所 (なければ null)
    @Nullable
    public Vec3 freeSpotNear(LivingEntity target) {
        double base = Math.atan2(getZ() - target.getZ(), getX() - target.getX());
        for (double r : new double[] {reach() + 1.0, reach() + 3.0, 3.0, 8.0}) {
            for (int k = 0; k < 12; k++) {
                // 今いる側から順に試す
                double a = base + (k % 2 == 0 ? 1 : -1) * (k / 2) * Math.PI / 6;
                double x = target.getX() + Math.cos(a) * r;
                double z = target.getZ() + Math.sin(a) * r;
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos feet = BlockPos.containing(x, target.getY() + dy, z);
                    if (!level().getBlockState(feet.below()).isFaceSturdy(level(), feet.below(), net.minecraft.core.Direction.UP)) {
                        continue;
                    }
                    Vec3 at = new Vec3(x, feet.getY(), z);
                    AABB box = getDimensions(getPose()).makeBoundingBox(at);
                    if (level().noCollision(this, box) && !level().containsAnyLiquid(box)
                            && level().clip(new net.minecraft.world.level.ClipContext(at.add(0, getEyeHeight(), 0), target.getEyePosition(),
                                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
                                    == net.minecraft.world.phys.HitResult.Type.MISS) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    // 自己テスト用: つっかえの数え
    public int stuckTicks() {
        return stuck;
    }
}
