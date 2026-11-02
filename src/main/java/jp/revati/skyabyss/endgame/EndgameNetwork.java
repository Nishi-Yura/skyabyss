package jp.revati.skyabyss.endgame;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// エンドコンテンツ (巡り路など。docs/design/54) のパケット。共有の ModNetwork とは別の登録口にして、版を独立させる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EndgameNetwork {
    // 2: 旅の記録 (Journey)。3: チャットの窓を開く (ChatMenus)。4: プロフィール (Profile)
    private static final String PROTOCOL_VERSION = "4";

    private EndgameNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(PROTOCOL_VERSION)
                .playToClient(CircuitPayload.TYPE, CircuitPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.endgame.client.CircuitClient.receive(payload)))
                .playToClient(Journey.Snapshot.TYPE, Journey.Snapshot.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.endgame.client.JourneyScreen.receive(payload)))
                .playToClient(ChatMenus.Open.TYPE, ChatMenus.Open.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(jp.revati.skyabyss.endgame.client.ChatMenuClient::receive))
                .playToClient(Profile.Snapshot.TYPE, Profile.Snapshot.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.endgame.client.ProfileScreen.receive(payload)))
                .playToServer(Profile.Request.TYPE, Profile.Request.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        Profile.answer(sp);
                    }
                }))
                .playToServer(Journey.Request.TYPE, Journey.Request.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                        Journey.answer(sp);
                    }
                }));
    }
}
