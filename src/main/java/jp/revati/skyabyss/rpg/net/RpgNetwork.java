package jp.revati.skyabyss.rpg.net;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.client.GrowthClient;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 成長のパケット。ModNetwork とは別に登録する (担当ごとに分けて、共有ファイルを触らないため)。
// 成長の記録そのものは Data Attachment の同期で届くので、ここは「得た」の知らせと、振り分け・振り直しの要求だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RpgNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private RpgNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
                .playToClient(GrowthGainPayload.TYPE, GrowthGainPayload.STREAM_CODEC, (payload, context) -> GrowthClient.onGain(payload))
                .playToServer(GrowthAllocatePayload.TYPE, GrowthAllocatePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && !Growth.allocate(player, payload.add())) {
                        // 画面が古いとき (ほかの所で振った・レベルを下げられた) など。同期し直して知らせる
                        Growth.sync(player);
                        Notice.bar(player, ModText.deny("message.skyabyss.growth.allocate_failed"));
                    }
                })
                .playToServer(GrowthRespecPayload.TYPE, GrowthRespecPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int cost = jp.revati.skyabyss.rpg.GrowthRules.statRespecCost(Growth.get(player).statRespecs);
                        // 画面に見えていた費用と違う (古い画面) ときは、払わせずに同期し直す
                        if (payload.cost() != cost && !player.isCreative()) {
                            Growth.sync(player);
                            return;
                        }
                        jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult r = Growth.respecStats(player);
                        if (r != jp.revati.skyabyss.rpg.skill.SkillManager.RespecResult.OK) {
                            Notice.bar(player, ModText.deny("message.skyabyss.growth.respec_failed." + r.name().toLowerCase(),
                                    net.minecraft.network.chat.Component.literal(String.valueOf(cost))));
                        }
                    }
                });
    }
}
