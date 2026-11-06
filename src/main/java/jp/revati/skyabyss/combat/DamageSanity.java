package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 傷と体力の数が有限であるようにする保険 (2026-11-07: 弱りの倍率が /kill の Float.MAX を Infinity にし、防具の計算で体力が NaN になって
// 「体力 0 のまま生きていて何もできない」になった。根本は DeathWeakness で直した。ほかの倍率が同じ形であふれても壊れないように)。
//   傷: すべての倍率がかかったあと (LOWEST) に、NaN は 0、1e9 を超えるものは 1e9 に (致死のまま。Infinity にしない)。
//       NaN は防具の計算 (actuallyHurt) の中で生まれるので、LivingDamageEvent.Pre では間に合わない。ここ (受ける前) で丸める
//   体力: ログインと 1 秒ごとに、吸収量と体力が有限か見て直す (吸収量だけ NaN でも、次の傷で体力も NaN になる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DamageSanity {
    public static final float CAP = 1.0e9f;

    private DamageSanity() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncoming(LivingIncomingDamageEvent event) {
        float a = event.getAmount();
        if (Float.isNaN(a)) {
            event.setAmount(0f);
        } else if (a > CAP) {
            event.setAmount(CAP);
        }
    }

    // 壊れた数を直す。直したら true
    public static boolean repair(LivingEntity e) {
        boolean fixed = false;
        if (!Float.isFinite(e.getAbsorptionAmount())) {
            e.setAbsorptionAmount(0f);
            fixed = true;
        }
        if (!Float.isFinite(e.getHealth())) {
            SkyAbyss.LOGGER.warn("{} had non-finite health; restored", e.getName().getString());
            e.setHealth(e.getMaxHealth());
            fixed = true;
        }
        return fixed;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            repair(p);
        }
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 13) {
            repair(p);
        }
    }
}
