package jp.revati.skyabyss.loot.box;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 封切り師の画面の通信 (docs/design/52 の 6 章)。サーバー → 画面: 持っている箱 (View)・開けた結果とルーレットの帯 (Result)・閉じろ (Close)。
// 画面 → サーバー: 開ける (Open)・閉じた (Closed)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoxNetwork {
    private static final String PROTOCOL_VERSION = "box-1";
    static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> STACKS = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list());

    private BoxNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(View.TYPE, View.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.box.BoxClient.view(p))
                .playToClient(Result.TYPE, Result.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.box.BoxClient.result(p))
                .playToClient(Close.TYPE, Close.STREAM_CODEC, (p, c) -> jp.revati.skyabyss.client.box.BoxClient.close())
                .playToServer(Open.TYPE, Open.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        BoxService.openBoxes(player, p.kind(), p.count());
                    }
                })
                .playToServer(Closed.TYPE, Closed.STREAM_CODEC, (p, c) -> {
                    if (c.player() instanceof ServerPlayer player) {
                        BoxService.closed(player);
                    }
                });
    }

    // 持っている箱の種類と数 (旅嚢と持ち物の合計。並びは BoxKind.ALL の順)
    public record View(List<String> kinds, List<Integer> counts) implements CustomPacketPayload {
        public static final Type<View> TYPE = typeOf("box_view");
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), View::kinds,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), View::counts,
                View::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 開けた結果: 出た品 (特性つき) と、一つごとのルーレットの帯 (LootBoxes.REEL 個、当たりは LootBoxes.WIN)
    public record Result(String kind, List<ItemStack> gear, List<List<ItemStack>> reels) implements CustomPacketPayload {
        public static final Type<Result> TYPE = typeOf("box_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Result::kind,
                STACKS, Result::gear,
                STACKS.apply(ByteBufCodecs.list()), Result::reels,
                Result::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = typeOf("box_close");
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Open(String kind, int count) implements CustomPacketPayload {
        public static final Type<Open> TYPE = typeOf("box_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Open::kind,
                ByteBufCodecs.VAR_INT, Open::count,
                Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Closed() implements CustomPacketPayload {
        public static final Type<Closed> TYPE = typeOf("box_closed");
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(new Closed());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
