package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 息の間のイベント: 気室の中の落下は傷を受けない (落ちて死ぬことはない)、倒れた・出た人の運びを終える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BreathEvents {
    private BreathEvents() {
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() == TideTravel.TIDE_BACK && Breath.active(p.position())) {
            event.setDamageMultiplier(0f);
            event.setDistance(0f);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Breath.forget(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Breath.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Breath.forget(event.getEntity().getUUID());
    }
}
