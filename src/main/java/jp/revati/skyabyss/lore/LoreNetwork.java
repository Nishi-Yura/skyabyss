package jp.revati.skyabyss.lore;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.client.LoreClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 伝承の書のパケット。ModNetwork とは別の版で登録する (ほかの担当とぶつからないように)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LoreNetwork {
    private static final String PROTOCOL_VERSION = "lore-1";

    private LoreNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Sync.TYPE, Sync.STREAM_CODEC, (payload, context) -> LoreClient.receiveSync(payload))
                .playToClient(Unlocked.TYPE, Unlocked.STREAM_CODEC, (payload, context) -> LoreClient.receiveUnlocked(payload.id()))
                .playToServer(Seen.TYPE, Seen.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Lore.markSeen(player, payload.id());
                    }
                });
    }

    // サーバー → 参加者: 章と項目の一覧、記された項目、読んだ項目のすべて
    public record Sync(LoreCatalog catalog, List<String> unlocked, List<String> seen) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lore_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    p.catalog.write(buf);
                    writeList(buf, p.unlocked);
                    writeList(buf, p.seen);
                },
                buf -> new Sync(LoreCatalog.read(buf), readList(buf), readList(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 参加者: 一つの項目が新しく記された (控えめな知らせを出す)
    public record Unlocked(String id) implements CustomPacketPayload {
        public static final Type<Unlocked> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lore_unlocked"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlocked> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id), buf -> new Unlocked(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: 書でその項目を開いた (新しい印を消す)
    public record Seen(String id) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lore_seen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seen> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id), buf -> new Seen(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void writeList(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        list.forEach(buf::writeUtf);
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }
}
