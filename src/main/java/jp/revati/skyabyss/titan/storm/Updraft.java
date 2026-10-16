package jp.revati.skyabyss.titan.storm;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// [仮の実装 / STUB] 昇り風の動き (12 の 10.3節。乗り込みの柱・西の試練の穴・心臓の風穴で共通)。
// 本物は背中の担当 (担当 1) が作る。11.1節の名前と引数だけそろえ、心臓の風穴が試せる最小限の動きを入れた。統合で置き換える。
// maxHeight は上がる上の端の y (ワールドの座標)。柱の中にいる者に毎 tick 呼ぶ:
//  - 上の端より下なら毎秒 8 ブロックで上がり、上の端では漂う。しゃがむと下りる
//  - 速さはサーバーから送る (hurtMarked)。専用サーバーの「飛んでいる」見回りに掛からないよう、短い浮遊を付ける
public final class Updraft {
    public static final double RISE = 0.4;

    private Updraft() {
    }

    public static void lift(Entity entity, double maxHeight) {
        Vec3 v = entity.getDeltaMovement();
        double vy;
        if (entity.isShiftKeyDown()) {
            vy = -0.15;
        } else if (entity.getY() < maxHeight - 0.8) {
            vy = RISE;
        } else if (entity.getY() < maxHeight) {
            vy = 0.05;
        } else {
            vy = -0.05;
        }
        entity.setDeltaMovement(v.x, vy, v.z);
        entity.fallDistance = 0;
        entity.hurtMarked = true;
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 0, false, false, false));
        }
    }
}
