package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 倒れた参加者の再入場 (Reentry) のゲームルールと、戻った直後の 3 秒の守り (傷を受けない・光る)。
//   skyabyssHeartReentryLimit  一人が一つの戦いで戻れる回数。既定 3。0 = 数えない (何度でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReentryRules {
    public static final int DEFAULT_LIMIT = 3;

    public static GameRules.Key<GameRules.IntegerValue> LIMIT;
    // 守りの記録。切れるゲーム時刻に加え、守りを受けたサーバー (MinecraftServer の同一性) と次元でも縛る。
    // 一人用で別のワールドへ入り直すとゲーム時刻が巻き戻るので、時刻だけだと前の世界の守りが残って見えるため
    private record Guard(long until, MinecraftServer server, ResourceKey<Level> dimension) {
    }

    private static final Map<UUID, Guard> GUARD = new HashMap<>();

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
        GUARD.put(player.getUUID(), new Guard(player.level().getGameTime() + Reentry.GLOW_TICKS, player.getServer(), player.level().dimension()));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, Reentry.GLOW_TICKS, 0, false, false));
        if (notify) {
            Notice.bar(player, ModText.battle("message.skyabyss.gate_rejoin"));
        }
    }

    public static boolean guarded(ServerPlayer player) {
        Guard g = GUARD.get(player.getUUID());
        if (g == null) {
            return false;
        }
        // 別のサーバー・別の次元・長すぎる残り (巻き戻った時刻) の記録は無効
        long now = player.level().getGameTime();
        if (g.server != player.getServer() || !g.dimension.equals(player.level().dimension()) || g.until > now + Reentry.GLOW_TICKS
                || g.until <= now) {
            GUARD.remove(player.getUUID());
            return false;
        }
        return true;
    }

    // 後始末: サーバー停止・退出・次元の移動で、守りの記録を捨てる (P1-4)
    public static void clearAll() {
        GUARD.clear();
    }

    public static int guardCount() {
        return GUARD.size();
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        clearAll();
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        GUARD.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Guard g = GUARD.get(event.getEntity().getUUID());
        if (g != null && !g.dimension.equals(event.getTo())) {
            GUARD.remove(event.getEntity().getUUID());
        }
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
