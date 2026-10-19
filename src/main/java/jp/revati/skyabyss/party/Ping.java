package jp.revati.skyabyss.party;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.client.PingClient;
import jp.revati.skyabyss.titan.group.GroupOwned;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 「ここ」を仲間に知らせる印 (docs/design/10 の 10.13)。キー 1 つ (既定 X) で、見ている先 (ブロック・敵) の位置を同じパーティの
// 仲間 (と自分) の画面に、名前と距離つきの印として TTL_TICKS (8 秒) 出す。見ている先はサーバーが調べる (クライアントの値を信じない)。
// 敵を見ているときは「敵」の印。パーティの外・一人のときは何も送らない。出しすぎ防止: COOLDOWN_TICKS (0.5 秒) あけ、WINDOW_TICKS (10 秒) に MAX_PER_WINDOW 回まで。
// 背中でも地上でも使える (印の位置は同じ次元の仲間にだけ見える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ping {
    private static final String PROTOCOL_VERSION = "party-ping-1";
    public static final double RANGE = 64.0;
    public static final int TTL_TICKS = 160;
    public static final int COOLDOWN_TICKS = 10;
    public static final int WINDOW_TICKS = 200;
    public static final int MAX_PER_WINDOW = 5;

    // 位置 (印を出す所)、entityId (見ていた生き物。なければ -1。印が追う)、enemy (敵の印か)
    public record Target(Vec3 pos, int entityId, boolean enemy) {
    }

    private static final Map<UUID, Deque<Long>> RECENT = new HashMap<>();

    private Ping() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Show.TYPE, Show.STREAM_CODEC, (payload, context) -> PingClient.receive(payload))
                .playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        request(player);
                    }
                });
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        RECENT.clear();
    }

    // 実際の入口: 見ている先を調べて送る
    static void request(ServerPlayer sender) {
        MinecraftServer server = sender.server;
        Target target = aim(sender);
        if (target == null) {
            return;
        }
        send(sender, target, sender.level().getGameTime(), id -> server.getPlayerList().getPlayer(id), (p, show) -> {
            if (p.connection != null) {
                PacketDistributor.sendToPlayer(p, show);
            }
        });
    }

    // 見ている先 (RANGE まで)。何も当たらなければ null。近い方 (ブロックか生き物か) を取る
    @Nullable
    public static Target aim(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockSq = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(block.getLocation());
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0),
                e -> e != player && !e.isSpectator() && e.isPickable() && !(e instanceof Player) && GroupOwned.visibleTo(e, player));
        if (hit != null && eye.distanceToSqr(hit.getLocation()) <= blockSq) {
            Entity e = hit.getEntity();
            return new Target(e.position().add(0, e.getBbHeight() * 0.5, 0), e.getId(), e instanceof Enemy);
        }
        if (block.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new Target(Vec3.atBottomCenterOf(block.getBlockPos()).add(0, 1.2, 0), -1, false);
    }

    // 送る (自己テストからも呼ぶ)。送ったら true。パーティの外・一人・出しすぎは送らない
    // lookup: 仲間を UUID から引く。sink: 送り先ごとの送り方
    public static boolean send(ServerPlayer sender, Target target, long now, Function<UUID, ServerPlayer> lookup,
            BiConsumer<ServerPlayer, Show> sink) {
        PartyData.Party party = PartyData.get(sender.server).of(sender.getUUID());
        if (party == null || party.members.size() < 2) {
            Notice.hint(sender, "message.skyabyss.ping.alone");
            return false;
        }
        if (!allow(sender.getUUID(), now)) {
            return false;
        }
        String dimension = sender.level().dimension().location().toString();
        String name = party.names.getOrDefault(sender.getUUID(), sender.getGameProfile().getName());
        Show show = new Show(sender.getUUID().toString(), name, dimension, target.pos.x, target.pos.y, target.pos.z, target.entityId, target.enemy,
                TTL_TICKS);
        List<ServerPlayer> to = new ArrayList<>();
        to.add(sender);
        for (UUID id : party.members) {
            ServerPlayer m = id.equals(sender.getUUID()) ? null : lookup.apply(id);
            if (m != null && !m.isSpectator() && m.level().dimension().equals(sender.level().dimension())) {
                to.add(m);
            }
        }
        to.forEach(p -> sink.accept(p, show));
        return true;
    }

    // 出しすぎの防止。受け付けたら記録して true
    public static boolean allow(UUID id, long now) {
        Deque<Long> q = RECENT.computeIfAbsent(id, k -> new ArrayDeque<>());
        // 巻き戻った時刻・古い記録は捨てる
        q.removeIf(t -> t > now || now - t >= WINDOW_TICKS);
        if (!q.isEmpty() && now - q.peekLast() < COOLDOWN_TICKS) {
            return false;
        }
        if (q.size() >= MAX_PER_WINDOW) {
            return false;
        }
        q.addLast(now);
        return true;
    }

    public static void clearForTest() {
        RECENT.clear();
    }

    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_ping_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Show(String senderId, String name, String dimension, double x, double y, double z, int entityId, boolean enemy, int ttl)
            implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_ping_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUtf(s.senderId, 64);
                    buf.writeUtf(s.name, 32);
                    buf.writeUtf(s.dimension, 64);
                    buf.writeDouble(s.x);
                    buf.writeDouble(s.y);
                    buf.writeDouble(s.z);
                    buf.writeInt(s.entityId);
                    buf.writeBoolean(s.enemy);
                    buf.writeVarInt(s.ttl);
                },
                buf -> new Show(buf.readUtf(64), buf.readUtf(32), buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(),
                        buf.readBoolean(), Math.min(buf.readVarInt(), TTL_TICKS)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
