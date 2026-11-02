package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 歩いている人: 巡り路の残り秒・印・いまの課題の文と行き先 (目的の帯が出す)。active = false で消す
public record CircuitPayload(boolean active, int seconds, int marks, Component task, boolean hasTarget, BlockPos target) implements CustomPacketPayload {
    public static final Type<CircuitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "circuit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CircuitPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.active());
                buf.writeVarInt(p.seconds());
                buf.writeVarInt(p.marks());
                ComponentSerialization.STREAM_CODEC.encode(buf, p.task());
                buf.writeBoolean(p.hasTarget());
                buf.writeBlockPos(p.target());
            },
            buf -> new CircuitPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readBoolean(), buf.readBlockPos()));

    public static CircuitPayload off() {
        return new CircuitPayload(false, 0, 0, Component.empty(), false, BlockPos.ZERO);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
