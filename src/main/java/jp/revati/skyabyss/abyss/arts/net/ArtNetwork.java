package jp.revati.skyabyss.abyss.arts.net;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.client.ArtClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 深淵術のパケット。担当ごとに分けて登録する (技の SkillNetwork とは別)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArtNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ArtNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(ArtCastPayload.TYPE, ArtCastPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && payload.art() >= 0 && payload.art() < AbyssArt.values().length) {
                        AbyssArts.press(player, AbyssArt.values()[payload.art()]);
                    }
                })
                .playToClient(ArtCooldownPayload.TYPE, ArtCooldownPayload.STREAM_CODEC, (payload, context) -> ArtClient.onCooldown(payload));
    }
}
