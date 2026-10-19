package jp.revati.skyabyss.titan.vow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.vow.VowHud;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 誓いの帯 (画面の右上の細い帯) のためのパケット。サーバー → 同じグループの全員 (どこにいても)。
// 立てている誓いが変わったとき・入ったとき・背中に入り出たときにすぐ、時間制限があるあいだは 5 秒ごとに送る。
// 帯に出す巨神は、今いる背中の巨神。背中の外では、誓いを立てている最初の巨神
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class VowNetwork {
    private static final String PROTOCOL_VERSION = "vow-1";
    // 人 → 最後に送った中身 (同じなら送らない。タイマーの残りは含めない)
    private static final Map<UUID, String> LAST = new HashMap<>();

    private VowNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> VowHud.receive(payload));
    }

    // titan: 帯に出す巨神 (誓いなしなら空)。timerSec: 残り秒 (時間制限がなければ -1)
    public record Sync(String titan, List<String> ids, int level, int timerSec) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "vow_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Sync::titan,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Sync::ids,
                ByteBufCodecs.VAR_INT, Sync::level,
                ByteBufCodecs.VAR_INT, Sync::timerSec,
                Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // その人に見せる中身を決める (サーバー側。自己テストもこれを見る)
    public static Sync snapshot(ServerPlayer p) {
        MinecraftServer server = p.server;
        UUID group = Groups.keyOf(p);
        String here = Titans.titanOfBack(p.level().dimension());
        List<String> order = new ArrayList<>();
        if (here != null) {
            order.add(here);
        }
        order.addAll(Titans.all());
        long now = server.overworld().getGameTime();
        for (String titan : order) {
            ServerLevel back = Vows.backLevel(server, titan);
            if (back == null) {
                continue;
            }
            List<Vow> vows = Vows.active(back, group);
            if (!vows.isEmpty()) {
                long left = Vows.timerRemaining(back, group, now);
                return new Sync(titan, vows.stream().map(Vow::id).toList(), Vows.level(vows), left < 0 ? -1 : (int) (left / 20));
            }
        }
        return new Sync("", List.of(), 0, -1);
    }

    public static void send(ServerPlayer p, boolean force) {
        if (p instanceof FakePlayer) {
            return;
        }
        Sync s = snapshot(p);
        String key = s.titan() + "|" + String.join(",", s.ids()) + "|" + (s.timerSec() < 0 ? "-" : "t");
        if (!force && key.equals(LAST.get(p.getUUID()))) {
            return;
        }
        LAST.put(p.getUUID(), key);
        PacketDistributor.sendToPlayer(p, s);
    }

    // そのグループのオンラインの全員へ (誓いを変えたとき・降ろしたとき)
    public static void syncGroup(MinecraftServer server, UUID group) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Groups.isMember(p, group)) {
                send(p, true);
            }
        }
    }

    // 時間制限の残りを知らせ直す (5 秒ごと。VowEvents から)
    static void resend(ServerPlayer p) {
        Sync s = snapshot(p);
        if (s.timerSec() >= 0 && !(p instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(p, s);
        } else {
            send(p, false);
        }
    }

    @Nullable
    static String lastKey(UUID player) {
        return LAST.get(player);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            send(p, true);
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            send(p, true);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LAST.clear();
    }
}
