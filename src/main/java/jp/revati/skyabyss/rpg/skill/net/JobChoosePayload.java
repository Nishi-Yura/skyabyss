package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// 持ち主 → サーバー: 島の祠で職を選んだ (職の id)
public record JobChoosePayload(String job) implements CustomPacketPayload {
    public static final Type<JobChoosePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "job_choose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobChoosePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.job(), 32),
            buf -> new JobChoosePayload(buf.readUtf(32)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
