package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 島の祠で職を選ぶ画面を開く (docs/design/43 の 4 節)
public record JobChoiceOpenPayload() implements CustomPacketPayload {
    public static final Type<JobChoiceOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "job_choice_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobChoiceOpenPayload> STREAM_CODEC = StreamCodec.unit(new JobChoiceOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
