package jp.revati.skyabyss.entity.foe.late;

import java.util.function.Supplier;

import jp.revati.skyabyss.entity.foe.ContinentFoe;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 嵐と灰の土地に足した敵 6 種 (issue #20。docs/design/44 の 4 章の後半) の共通部分。
// 前からの守り (guardTaken) と、技のあとの柔らかい間 (softTaken) を持てる。体力・攻撃は岩鯨の土地を 1 とした素の値 (ContinentDanger が土地の強さを掛ける)
public abstract class LateFoe extends ContinentFoe {
    // 前からの傷を減らす残りの tick と、その割合 (0.3 = 3 割だけ通る)
    protected int guardTicks;
    protected float guardTaken = 1f;
    // 柔らかい残りの tick と、その倍率 (1.3 = 3 割増し)
    protected int softTicks;
    protected float softTaken = 1f;

    protected LateFoe(EntityType<? extends Monster> type, Level level, String anim, Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt,
            Supplier<SoundEvent> death, String... actions) {
        super(type, level, anim, ambient, hurt, death, actions);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (guardTicks > 0) {
            guardTicks--;
        }
        if (softTicks > 0) {
            softTicks--;
        }
    }

    public boolean guarding() {
        return guardTicks > 0;
    }

    public boolean soft() {
        return softTicks > 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            if (softTicks > 0) {
                amount *= softTaken;
            }
            Vec3 from = source.getSourcePosition();
            if (guardTicks > 0 && from != null) {
                Vec3 look = Vec3.directionFromRotation(0, getYRot()).multiply(1, 0, 1).normalize();
                Vec3 to = from.subtract(position()).multiply(1, 0, 1).normalize();
                if (look.dot(to) > 0.5) {
                    amount *= guardTaken;
                    onGuarded();
                }
            }
        }
        return super.hurt(source, amount);
    }

    // 前の守りで受けたとき (音・粒)
    protected void onGuarded() {
    }
}
