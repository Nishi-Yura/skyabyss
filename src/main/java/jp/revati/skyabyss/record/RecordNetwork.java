package jp.revati.skyabyss.record;

import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 控え帳のパケット。ほかの担当とぶつからないよう、自分の版で登録する。
// サーバーが中身を作って送り、クライアントは「見せて」「受け取る」を頼むだけ。頼みはサーバーで検める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RecordNetwork {
    private static final String PROTOCOL_VERSION = "record-1";

    private RecordNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // クライアント側の処理はラムダの中だけで呼ぶので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(Show.TYPE, Show.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.record.RecordClient.receive(payload.snapshot(), payload.open(), payload.tab()))
                .playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RecordService.request(player, payload.target());
                    }
                })
                .playToServer(Claim.TYPE, Claim.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RecordService.claim(player);
                    }
                });
    }

    // サーバー → 見る人: 控え帳の中身。open なら画面を開く (tab は開くつまみ。空なら前のまま)。
    // open でなければ、同じ人の控え帳が開いているときだけ中身を差し替える
    public record Show(RecordSnapshot snapshot, boolean open, String tab) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "record_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                RecordSnapshot.STREAM_CODEC, Show::snapshot,
                ByteBufCodecs.BOOL, Show::open,
                ByteBufCodecs.STRING_UTF8, Show::tab,
                Show::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 見る人 → サーバー: この人の控え帳を最新にして (画面が開いているあいだ、ときどき送る)
    public record Request(UUID target) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "record_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Request::target, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 本人 → サーバー: 預かっている報酬を受け取る
    public record Claim() implements CustomPacketPayload {
        public static final Type<Claim> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "record_claim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Claim> STREAM_CODEC = StreamCodec.unit(new Claim());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
