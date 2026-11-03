package jp.revati.skyabyss.upgrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteBuilds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 作り直しのあとの読み込み画面 (2026-11-03 の声「アップデート後にワールドに初参加する時だけ、全地形のロードおわるまでロード画面出して」)。
// 大陸の版が上がってから初めて入った人 (ContinentRegen.onLogin) に、まわりの地形が揃い、建物の建て直し (SiteBuilds) が終わるまで画面を出す。
//   終わり: まわり RADIUS チャンクがすべて読めて、まわり BUILD_NEAR の建物 (都・町・野営地…) を建て終えた (か BUILD_MAX たった)。
//           待つ間、まわりの建物を待ちの先頭へ回す。短くても MIN_TICKS、長くても MAX_TICKS
//   待つ間: その人は傷つかず、落ちない (待ち始めの所に留める)。手元の画面は、手元にもまわりのチャンクが届いて描けるまで閉じない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RegenLoading {
    static final int RADIUS = 6;
    // 待つ建物の範囲 (ブロック)。この中の都・町・野営地などが建ち終わるまで
    static final double BUILD_NEAR = 200;
    static final int MIN_TICKS = 60;
    static final int BUILD_MAX = 20 * 90;
    static final int MAX_TICKS = 20 * 150;

    private record Wait(long start, Vec3 at, boolean wasInvulnerable) {
    }

    private static final Map<UUID, Wait> WAITING = new HashMap<>();

    private RegenLoading() {
    }

    // 待たせ始める (ContinentRegen.onLogin から。大陸の版が上がってから初めて入った人)
    static void begin(ServerPlayer p) {
        if (p instanceof FakePlayer || p.connection == null) {
            return;
        }
        WAITING.put(p.getUUID(), new Wait(p.serverLevel().getGameTime(), p.position(), p.isInvulnerable()));
        p.setInvulnerable(true);
        SiteBuilds.prioritizeNear(p.blockPosition(), BUILD_NEAR);
        PacketDistributor.sendToPlayer(p, new State(true, false, SiteBuilds.pendingNear(p.blockPosition(), BUILD_NEAR), 0));
        SkyAbyss.LOGGER.info("Regen loading: holding {} until the land around is ready", p.getGameProfile().getName());
    }

    public static boolean waiting(ServerPlayer p) {
        return WAITING.containsKey(p.getUUID());
    }

    // まわりのチャンクがすべて読めているか
    static int loaded(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int cx = p.getBlockX() >> 4;
        int cz = p.getBlockZ() >> 4;
        int n = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (level.hasChunk(cx + dx, cz + dz)) {
                    n++;
                }
            }
        }
        return n;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (WAITING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int total = (2 * RADIUS + 1) * (2 * RADIUS + 1);
        for (var it = WAITING.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Wait w = e.getValue();
            long t = p.serverLevel().getGameTime() - w.start();
            // 落ちない (下の地面がまだ無いことがある)
            if (p.getY() < w.at().y - 1.5) {
                p.teleportTo(w.at().x, w.at().y, w.at().z);
            }
            p.setDeltaMovement(Vec3.ZERO);
            if (server.getTickCount() % 10 != 0) {
                continue;
            }
            int got = loaded(p);
            SiteBuilds.prioritizeNear(p.blockPosition(), BUILD_NEAR);
            int near = SiteBuilds.pendingNear(p.blockPosition(), BUILD_NEAR);
            boolean builds = near > 0 && t < BUILD_MAX;
            boolean done = t >= MIN_TICKS && got >= total && !builds || t >= MAX_TICKS;
            PacketDistributor.sendToPlayer(p, new State(!done, done, near, Math.round(100f * got / total)));
            if (done) {
                p.setInvulnerable(w.wasInvulnerable());
                it.remove();
                SkyAbyss.LOGGER.info("Regen loading: released {} after {} ticks", p.getGameProfile().getName(), t);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Wait w = WAITING.remove(event.getEntity().getUUID());
        if (w != null) {
            event.getEntity().setInvulnerable(w.wasInvulnerable());
        }
    }

    // サーバー → 人: 読み込み画面 (open = 出す / 出し続ける、done = サーバー側は終わり。builds = 建てている物の数、percent = まわりのチャンク)
    public record State(boolean open, boolean done, int builds, int percent) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "regen_loading"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeBoolean(s.open());
                    buf.writeBoolean(s.done());
                    buf.writeVarInt(s.builds());
                    buf.writeVarInt(s.percent());
                },
                buf -> new State(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(State.TYPE, State.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.upgrade.RegenLoadingScreen.receive(payload)));
    }
}
