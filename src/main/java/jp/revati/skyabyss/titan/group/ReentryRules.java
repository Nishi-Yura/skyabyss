package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 倒れた参加者の再入場 (Reentry) のゲームルールと、戻った直後の 3 秒の守り (傷を受けない・光る)。
//   skyabyssHeartReentryLimit  一人が一つの戦いで戻れる回数。既定 3。0 = 数えない (何度でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReentryRules {
    public static final int DEFAULT_LIMIT = 3;

    public static GameRules.Key<GameRules.IntegerValue> LIMIT;
    // 守りが切れるゲーム時刻
    private static final Map<UUID, Long> GUARD = new HashMap<>();

    private ReentryRules() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LIMIT = GameRules.register("skyabyssHeartReentryLimit", GameRules.Category.PLAYER,
                GameRules.IntegerValue.create(DEFAULT_LIMIT)));
    }

    public static int limit(MinecraftServer server) {
        return LIMIT == null ? DEFAULT_LIMIT : Math.max(0, server.getGameRules().getInt(LIMIT));
    }

    // 戻った人に 3 秒の守りと光
    public static void guard(ServerPlayer player) {
        guard(player, true);
    }

    // notify = false: 背中の復活の守り (RespawnGuard)。扉の再入場の知らせは出さない
    public static void guard(ServerPlayer player, boolean notify) {
        GUARD.put(player.getUUID(), player.level().getGameTime() + Reentry.GLOW_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, Reentry.GLOW_TICKS, 0, false, false));
        if (notify) {
            Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
        }
    }

    public static boolean guarded(ServerPlayer player) {
        Long until = GUARD.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (until <= player.level().getGameTime()) {
            GUARD.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // 外にいる倒れた人へ、残りの待ち時間・戻れる・戻れない、を画面の下に (チャットには出さない)
    public static void tell(ServerPlayer player, Reentry.Status status, long remainingTicks, int used, int limit) {
        switch (status) {
            case WAITING -> Notice.bar(player, ModText.deny("message.skyabyss.gate_wait", (remainingTicks + 19) / 20));
            case READY -> Notice.bar(player, ModText.battle(limit > 0 ? "message.skyabyss.gate_ready" : "message.skyabyss.gate_ready_free",
                    Math.max(0, limit - used)));
            case EXHAUSTED -> Notice.bar(player, ModText.deny("message.skyabyss.gate_fallen"));
            default -> {
            }
        }
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && guarded(player)) {
            event.setCanceled(true);
        }
    }
}
