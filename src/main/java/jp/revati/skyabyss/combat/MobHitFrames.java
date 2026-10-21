package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 人が当てた敵の「無敵の間」をほぼ無くす (mod 全体。2026-10-22 の二人の試遊: 二人で同じ敵を叩くと、あとの一撃が無敵の間に吸われていた)。
// バニラは傷のあと 20 tick の無敵 (10 tick を過ぎるまで、それより弱い傷は消える)。人 (と人の矢など) が当てた人でない者は、HIT_FRAMES tick だけにする。
// 人が受ける傷の無敵はそのまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MobHitFrames {
    // 人が当てたあとの無敵の tick (バニラの数え方で 10 を超えた分だけ守られる)。0 なら同じ tick の二人の一撃も両方通る
    public static final int HIT_FRAMES = 0;

    private MobHitFrames() {
    }

    // 傷を受ける者と、その傷の出どころ (人なら当たる)
    public static boolean applies(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        return !(target instanceof Player) && source.getEntity() instanceof Player;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (applies(event.getEntity(), event.getSource())) {
            event.setInvulnerabilityTicks(10 + HIT_FRAMES);
        }
    }
}
