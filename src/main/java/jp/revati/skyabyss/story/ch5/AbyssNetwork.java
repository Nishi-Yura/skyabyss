package jp.revati.skyabyss.story.ch5;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 深淵のパケット (別の版で登録する)。潜るときの圧 (淵の声・重さ・深さ) をその人へ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssNetwork {
    private static final String PROTOCOL_VERSION = "abyss-3";

    private AbyssNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Pressure.TYPE, Pressure.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.story.ch5.client.AbyssHud.set(payload.voice(), payload.weight(), payload.depth()))
                .playToClient(Section.TYPE, Section.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.story.ch5.client.AbyssSectionClient.set(payload))
                .playToClient(Flash.TYPE, Flash.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.story.ch5.client.AbyssFlashClient.receive(payload));
    }

    // サーバー → その人: いる区切り (0〜3 = 層、4 = 心臓、-1 = 深淵の外)、目的の帯の一言 (戦いの一言の翻訳キー。"" = 出さない)、
    // 道案内 (story/ch5/layers/AbyssGuide。翻訳キーと引数 (引数は "#" で始まれば文字のまま、ほかは翻訳キー)、行き先。has = false なら行き先なし)
    public record Section(int section, String objective, String guide, java.util.List<String> args, boolean has, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<Section> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_section"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Section> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.section());
                    buf.writeUtf(p.objective(), 256);
                    buf.writeUtf(p.guide(), 256);
                    buf.writeVarInt(p.args().size());
                    for (String a : p.args()) {
                        buf.writeUtf(a, 256);
                    }
                    buf.writeBoolean(p.has());
                    buf.writeInt(p.x());
                    buf.writeInt(p.y());
                    buf.writeInt(p.z());
                },
                buf -> {
                    int s = buf.readInt();
                    String o = buf.readUtf(256);
                    String g = buf.readUtf(256);
                    int n = Math.min(8, buf.readVarInt());
                    java.util.List<String> args = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        args.add(buf.readUtf(256));
                    }
                    return new Section(s, o, g, java.util.List.copyOf(args), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
                });

        public static Section of(int section, String objective) {
            return new Section(section, objective, "", java.util.List.of(), false, 0, 0, 0);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → その人: 画面の光 (地上へ帰る磁石石。色 rgb へ in tick で明るくなり、hold tick そのまま、out tick で戻る)
    public record Flash(int in, int hold, int out, int rgb) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flash> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Flash::in, ByteBufCodecs.VAR_INT, Flash::hold, ByteBufCodecs.VAR_INT, Flash::out, ByteBufCodecs.INT, Flash::rgb, Flash::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // サーバー → その人: 淵の声 0〜100 (-1 = 深淵の外)、重さ 0〜100 (%)、深さ (ブロック)
    public record Pressure(int voice, int weight, int depth) implements CustomPacketPayload {
        public static final Type<Pressure> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_pressure"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pressure> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pressure::voice, ByteBufCodecs.VAR_INT, Pressure::weight, ByteBufCodecs.VAR_INT, Pressure::depth, Pressure::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
