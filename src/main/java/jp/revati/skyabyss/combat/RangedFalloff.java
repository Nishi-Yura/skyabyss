package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 遠くからの矢は、大きな敵 (体力 BIG_HP 以上のボス・中ボス・心臓) には弱まる (2026-10-29 の試遊「弓強すぎるかも。ボスとかそんなに遠くまで攻撃しないから」)。
// 射手と的の距離が NEAR までは そのまま、FAR で FLOOR 倍まで、その間はまっすぐ下がる。ふつうの敵には掛けない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RangedFalloff {
    public static final float BIG_HP = 100f;
    public static final double NEAR = 10;
    public static final double FAR = 26;
    public static final float FLOOR = 0.55f;

    private RangedFalloff() {
    }

    // 距離 d で射た矢の倍率
    public static float factor(double d) {
        if (d <= NEAR) {
            return 1f;
        }
        if (d >= FAR) {
            return FLOOR;
        }
        return (float) (1.0 - (1.0 - FLOOR) * (d - NEAR) / (FAR - NEAR));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow) || !(event.getSource().getEntity() instanceof Player shooter)
                || target.getMaxHealth() < BIG_HP) {
            return;
        }
        float k = factor(Math.sqrt(shooter.distanceToSqr(target)));
        if (k < 1f) {
            event.setAmount(event.getAmount() * k);
        }
    }
}
