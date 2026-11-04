package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 転生の見せ方のパケット。順位表・飾り板の中身 (サーバー → 画面を開いた人)、倒れ・立ち上がりの光 (周りの人へ)、称号の選択・順位表を断る (持ち主 → サーバー)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RebirthNet {
    private RebirthNet() {
    }

    // 順位表の 1 行。value = 転生数 / tick / 死の数 (表の種類による)
    public record Line(String name, int rebirths, long value) {
    }

    // 飾り板の 1 行 (巨神ごとの世界の最速)。ticks < 0 = まだ記録なし。fresh = 最近更新された
    public record RecordLine(Component titan, String who, long ticks, boolean fresh, boolean here) {
    }

    // kind 0 = 順位表の石 (lists に 3 表)、1 = 飾り板 (records)
    public record BurstPayload(int entityId, boolean rise) implements CustomPacketPayload {
        public static final Type<BurstPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rebirth_burst"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BurstPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BurstPayload::entityId, ByteBufCodecs.BOOL, BurstPayload::rise, BurstPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SelectTitlePayload(String id) implements CustomPacketPayload {
        public static final Type<SelectTitlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rebirth_title"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectTitlePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), SelectTitlePayload::id, SelectTitlePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HiddenPayload(boolean hidden) implements CustomPacketPayload {
        public static final Type<HiddenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "rebirth_hidden"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HiddenPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, HiddenPayload::hidden, HiddenPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2")
                .playToClient(BurstPayload.TYPE, BurstPayload.STREAM_CODEC, (p, ctx) -> jp.revati.skyabyss.client.rebirth.RebirthClient.burst(p))
                .playToServer(SelectTitlePayload.TYPE, SelectTitlePayload.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        Rebirths.selectTitle(sp, p.id());
                    }
                })
                .playToServer(HiddenPayload.TYPE, HiddenPayload.STREAM_CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        Rebirths.setHidden(sp, p.hidden());
                    }
                });
    }
}
