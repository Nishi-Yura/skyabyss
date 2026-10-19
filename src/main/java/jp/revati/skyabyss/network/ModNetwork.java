package jp.revati.skyabyss.network;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.sky.SkyPassClient;
import jp.revati.skyabyss.sky.SkyPassPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// パケットの登録。プロトコルを変えたらバージョンを上げる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ModNetwork {
    // 版は mod の版そのもの。版の違う client と server は、NeoForge の接続の段階で「版が合わない」と断る
    // (版が合わないまま入れると、パケットだけが黙って落ち、入った人には演出も帯も何も出ない)。ほかの通信の版は個別に上げる
    private static String protocolVersion() {
        return net.neoforged.fml.ModList.get().getModContainerById(SkyAbyss.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("1");
    }

    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
        event.registrar(protocolVersion())
                .playToClient(SkyPassPayload.TYPE, SkyPassPayload.STREAM_CODEC, (payload, context) -> SkyPassClient.receive(payload.pass()))
                .playToClient(jp.revati.skyabyss.combat.TelegraphStartPayload.TYPE, jp.revati.skyabyss.combat.TelegraphStartPayload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.combat.TelegraphClient.start(payload))
                .playToClient(jp.revati.skyabyss.combat.TelegraphCancelPayload.TYPE, jp.revati.skyabyss.combat.TelegraphCancelPayload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.combat.TelegraphClient.cancel(payload.id()))
                .playToClient(jp.revati.skyabyss.combat.BossCue.Payload.TYPE, jp.revati.skyabyss.combat.BossCue.Payload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.titan.BossCueClient.receive(payload))
                .playToClient(jp.revati.skyabyss.titan.dungeon.LampBand.Payload.TYPE, jp.revati.skyabyss.titan.dungeon.LampBand.Payload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.titan.LampBandClient.receive(payload))
                .playToClient(jp.revati.skyabyss.titan.domain.DomainPayload.TYPE, jp.revati.skyabyss.titan.domain.DomainPayload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.titan.domain.DomainClient.receive(payload));
    }
}
