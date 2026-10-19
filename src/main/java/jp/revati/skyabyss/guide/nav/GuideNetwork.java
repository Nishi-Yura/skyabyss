package jp.revati.skyabyss.guide.nav;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.guide.GuideClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 目的の道しるべ (docs/design/18 の 8.3 節)。クライアントが「いまの目的の場所」を送り (GuideTarget)、
// サーバーが歩ける道を探して、その人にだけ道の点を返す (GuideTrail)。探索は GuideScheduler が 1 tick の予算内で進める。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuideNetwork {
    private static final String PROTOCOL_VERSION = "guide-nav-1";
    // 1 tick に全員で広げるノードの数
    public static final int BUDGET_PER_TICK = 800;
    // 同じ人からの知らせは 10 tick に 1 回まで
    private static final int MIN_GAP = 10;

    private static final Map<UUID, Long> LAST_HEARD = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static final GuideScheduler<UUID> SCHEDULER = new GuideScheduler<>(GuideNetwork::deliver);

    private GuideNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(GuideTarget.TYPE, GuideTarget.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onTarget(player, payload);
                    }
                })
                .playToClient(GuideTrail.TYPE, GuideTrail.STREAM_CODEC, (payload, context) -> GuideClient.receive(payload));
    }

    // クライアントからの知らせ (自分の道だけ。ほかの人の道は触れない)
    static void onTarget(ServerPlayer player, GuideTarget t) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_HEARD.get(player.getUUID());
        if (last != null && now - last < MIN_GAP && now >= last && t.active()) {
            return;
        }
        LAST_HEARD.put(player.getUUID(), now);
        server = player.server;
        SCHEDULER.update(player.getUUID(), t.active(), t.target(), now);
    }

    private static void deliver(UUID owner, GuideScheduler.Delivery d) {
        if (server == null) {
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            PacketDistributor.sendToPlayer(p, new GuideTrail(d.target(), d.found(), d.nodes()));
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        server = s;
        if (SCHEDULER.size() == 0) {
            return;
        }
        SCHEDULER.tick(s.getTickCount(), owner -> {
            ServerPlayer p = s.getPlayerList().getPlayer(owner);
            if (p == null || !p.isAlive() || p.serverLevel().dimension() != Level.OVERWORLD) {
                return null;
            }
            ServerLevel level = p.serverLevel();
            return new GuideScheduler.Where(p.blockPosition(), new LevelTerrain(level));
        }, BUDGET_PER_TICK);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SCHEDULER.remove(id);
        LAST_HEARD.remove(id);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SCHEDULER.clear();
        LAST_HEARD.clear();
        server = null;
    }

    // ---- パケット ----

    // クライアント → サーバー: いまの目的の場所 (active = false なら道は要らない)
    public record GuideTarget(boolean active, BlockPos target) implements CustomPacketPayload {
        public static final Type<GuideTarget> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "guide_target"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuideTarget> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.active);
                    buf.writeBlockPos(p.target);
                },
                buf -> new GuideTarget(buf.readBoolean(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → その人だけ: 道の点 (found = false なら道なし。nodes が空なら「着いた」か「道なし」)
    public record GuideTrail(BlockPos target, boolean found, List<BlockPos> nodes) implements CustomPacketPayload {
        public static final Type<GuideTrail> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "guide_trail"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GuideTrail> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.target);
                    buf.writeBoolean(p.found);
                    int n = Math.min(p.nodes.size(), TrailCodec.MAX_NODES);
                    buf.writeVarInt(n);
                    if (n > 0) {
                        List<BlockPos> sub = p.nodes.subList(0, n);
                        buf.writeBlockPos(sub.get(0));
                        buf.writeByteArray(TrailCodec.steps(sub));
                    }
                },
                buf -> {
                    BlockPos target = buf.readBlockPos();
                    boolean found = buf.readBoolean();
                    int n = buf.readVarInt();
                    if (n <= 0 || n > TrailCodec.MAX_NODES) {
                        return new GuideTrail(target, found, List.of());
                    }
                    BlockPos origin = buf.readBlockPos();
                    byte[] steps = buf.readByteArray(TrailCodec.MAX_NODES);
                    return new GuideTrail(target, found, TrailCodec.decode(origin, steps));
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 開発・テスト用: 持ち主の鍵ごとの予定表を外から見る
    @Nullable
    static GuideScheduler<UUID> scheduler() {
        return SCHEDULER;
    }
}
