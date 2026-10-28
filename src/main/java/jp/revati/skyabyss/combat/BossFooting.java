package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

// 主と名のある敵は殴られても下がらない (2026-10-28 の試遊「ボスがノックバックするのはどうなの」→ 主と名のある敵は下がらない)。
// 主 = 画面の帯 (ServerBossEvent) を持つ型・大物 (体力が ShieldRules.BIG_HEALTH 以上)。名のある敵 = NamedFoes の印。小さな敵は今までどおり下がる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossFooting {
    private BossFooting() {
    }

    public static boolean planted(LivingEntity e) {
        if (e instanceof Player) {
            return false;
        }
        if (e.getTags().contains(jp.revati.skyabyss.world.continent.NamedFoes.TAG) || MobTuning.hasBossBar(e.getClass())) {
            return true;
        }
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        return hp != null && hp.getBaseValue() >= ShieldRules.BIG_HEALTH;
    }

    @SubscribeEvent
    static void onKnockback(LivingKnockBackEvent event) {
        if (planted(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
