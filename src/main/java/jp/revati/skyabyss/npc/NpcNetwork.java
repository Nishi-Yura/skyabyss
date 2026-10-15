package jp.revati.skyabyss.npc;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.npc.NpcClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 会話のパケット。ほかの担当とぶつからないよう、別の版 (npc-1) で登録する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NpcNetwork {
    private static final String PROTOCOL_VERSION = "npc-1";

    private NpcNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Show.TYPE, Show.STREAM_CODEC, (payload, context) -> NpcClient.show(payload))
                .playToClient(Close.TYPE, Close.STREAM_CODEC, (payload, context) -> NpcClient.close())
                .playToServer(Choose.TYPE, Choose.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DialogueService.choose(player, payload.node(), payload.index());
                    }
                })
                .playToServer(Closed.TYPE, Closed.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DialogueService.onClientClosed(player);
                    }
                });
    }

    // 画面に出す選択肢。index は節の中の番号 (条件で隠れた選択肢があっても番号は変わらない)
    public record OptionView(int index, String textKey, String kind) {
    }

    // サーバー → 参加者: この節を映す
    public record Show(int npcId, String role, String node, String face, String textKey, List<OptionView> options, boolean hasNext)
            implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dialogue_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.npcId);
                    buf.writeUtf(p.role);
                    buf.writeUtf(p.node);
                    buf.writeUtf(p.face);
                    buf.writeUtf(p.textKey);
                    buf.writeVarInt(p.options.size());
                    for (OptionView o : p.options) {
                        buf.writeVarInt(o.index());
                        buf.writeUtf(o.textKey());
                        buf.writeUtf(o.kind());
                    }
                    buf.writeBoolean(p.hasNext);
                },
                buf -> {
                    int npcId = buf.readVarInt();
                    String role = buf.readUtf();
                    String node = buf.readUtf();
                    String face = buf.readUtf();
                    String text = buf.readUtf();
                    int n = buf.readVarInt();
                    List<OptionView> options = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        options.add(new OptionView(buf.readVarInt(), buf.readUtf(), buf.readUtf()));
                    }
                    return new Show(npcId, role, node, face, text, options, buf.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → 参加者: 会話を閉じる
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dialogue_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: node の index 番を選んだ (CONTINUE = 続き、LEAVE = 立ち去る)
    public record Choose(String node, int index) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dialogue_choose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.node);
                    buf.writeVarInt(p.index + 2);
                },
                buf -> new Choose(buf.readUtf(), buf.readVarInt() - 2));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 参加者 → サーバー: 画面を閉じた
    public record Closed() implements CustomPacketPayload {
        public static final Type<Closed> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dialogue_closed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(new Closed());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
