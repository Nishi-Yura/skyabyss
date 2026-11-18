package jp.revati.skyabyss.notice;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// サーバー → クライアント: 手に入れた品 (左の列には絵だけを並べる。2026-11-10 の声「アイテムのアイコンのみに」)。
// text は知らせの記録・チャットに残す文 (「◯◯を手に入れた」)
public record GainItemsPayload(List<ItemStack> items, Component text) implements CustomPacketPayload {
    public static final Type<GainItemsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gain_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GainItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64)), GainItemsPayload::items,
            ComponentSerialization.STREAM_CODEC, GainItemsPayload::text,
            GainItemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
