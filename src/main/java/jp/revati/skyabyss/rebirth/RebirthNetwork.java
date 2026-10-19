package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 転生のパケット。ほかの系統とは別に登録する。転生の記録そのものは Data Attachment の同期 (GrowthData・RebirthData) で届く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RebirthNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private RebirthNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(RebirthOpenPayload.TYPE, RebirthOpenPayload.STREAM_CODEC,
                        (payload, context) -> jp.revati.skyabyss.client.rebirth.RebirthClient.open())
                .playToServer(RebirthActionPayload.TYPE, RebirthActionPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handle(player, payload);
                    }
                });
    }

    static void handle(ServerPlayer player, RebirthActionPayload p) {
        if (p.action() == RebirthActionPayload.SHARD_NODE) {
            if (!Rebirth.claimShardNode(player, p.arm())) {
                Rebirth.sync(player);
                Notice.bar(player, ModText.deny("message.skyabyss.rebirth.fail.shard"));
            }
            return;
        }
        Rebirth.Result r = Rebirth.perform(player, p.arm(), p.boon(), true);
        if (r != Rebirth.Result.OK) {
            // 画面が古いとき (別の所で変わった) のために同期し直す
            Rebirth.sync(player);
            jp.revati.skyabyss.rpg.Growth.sync(player);
            Notice.bar(player, ModText.deny("message.skyabyss.rebirth.fail." + r.name().toLowerCase(),
                    Component.literal(String.valueOf(RebirthRules.COST_CRYSTALS))));
        }
    }
}
