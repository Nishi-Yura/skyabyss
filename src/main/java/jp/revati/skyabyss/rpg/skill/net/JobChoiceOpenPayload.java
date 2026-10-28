package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// サーバー → 持ち主: 職の画面を開く。switching = false は島の祠で初めて選ぶ (docs/design/43 の 4 節)、true は街の職の間で替える (docs/design/48 の 3 章)
public record JobChoiceOpenPayload(boolean switching) implements CustomPacketPayload {
    public static final Type<JobChoiceOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "job_choice_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobChoiceOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JobChoiceOpenPayload::switching, JobChoiceOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
