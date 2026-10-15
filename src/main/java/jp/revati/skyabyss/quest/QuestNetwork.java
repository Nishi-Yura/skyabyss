package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.quest.QuestClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// クエストのパケット。ほかの担当とぶつからないよう、別の版 (quest-1) で登録する。
// 記録そのものは Data Attachment の同期で届く。ここは定義の一覧と知らせと「追跡する」だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuestNetwork {
    private static final String PROTOCOL_VERSION = "quest-1";

    private QuestNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Catalog.TYPE, Catalog.STREAM_CODEC, (payload, context) -> QuestClient.receiveCatalog(payload.quests()))
                .playToClient(Notice.TYPE, Notice.STREAM_CODEC, (payload, context) -> QuestClient.receiveNotice(payload.kind(), payload.quest()))
                .playToServer(Track.TYPE, Track.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Quests.track(player, payload.quest());
                    }
                });
    }

    public static void sendCatalog(ServerPlayer player) {
        if (player.connection != null && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new Catalog(QuestManager.all()));
        }
    }

    // サーバー → 参加者: クエストの定義の一覧 (表示に要る分だけ)
    public record Catalog(List<QuestDef> quests) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "quest_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.quests.size());
                    p.quests.forEach(q -> q.write(buf));
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<QuestDef> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(QuestDef.read(buf));
                    }
                    return new Catalog(list);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 参加者: クエストが始まった・進んだ・終わった (右上に小さな札と音)
    public record Notice(byte kind, String quest) implements CustomPacketPayload {
        public static final byte STARTED = 0;
        public static final byte UPDATED = 1;
        public static final byte FINISHED = 2;
        public static final Type<Notice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "quest_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeUtf(p.quest);
                },
                buf -> new Notice(buf.readByte(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: このクエストを追跡する (空ならやめる)
    public record Track(String quest) implements CustomPacketPayload {
        public static final Type<Track> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "quest_track"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Track> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.quest), buf -> new Track(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
