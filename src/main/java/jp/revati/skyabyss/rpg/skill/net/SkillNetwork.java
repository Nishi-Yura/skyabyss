package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.client.skill.SkillClient;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 技のパケット。ModNetwork・RpgNetwork とは別に登録する (担当ごとに分けて、共有ファイルを触らないため)。
// 技の記録そのものは Data Attachment の同期で届く。ここは要求 (発動・習得・置き場・振り直し) と、マナと発動の結果の知らせ。
// 要求はすべてサーバーで検める (クライアントの言うことをそのまま信じない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SkillNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private SkillNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
                .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC, (payload, context) -> SkillClient.onMana(payload))
                .playToClient(SkillCastResultPayload.TYPE, SkillCastResultPayload.STREAM_CODEC, (payload, context) -> SkillClient.onCastResult(payload))
                .playToServer(SkillCastPayload.TYPE, SkillCastPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillManager.cast(player, payload.slot());
                    }
                })
                .playToServer(SkillLearnPayload.TYPE, SkillLearnPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillData.LearnResult r = SkillManager.learn(player, Skill.byId(payload.skill()));
                        if (r != SkillData.LearnResult.OK) {
                            SkillManager.sync(player);
                            player.displayClientMessage(ModText.deny("message.skyabyss.skill.learn_failed." + r.name().toLowerCase()), true);
                        }
                    }
                })
                .playToServer(SkillSlotPayload.TYPE, SkillSlotPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Skill skill = payload.skill().isEmpty() ? null : Skill.byId(payload.skill());
                        if (!payload.skill().isEmpty() && skill == null || !SkillManager.setSlot(player, payload.slot(), skill)) {
                            SkillManager.sync(player);
                        }
                    }
                })
                .playToServer(SkillRespecPayload.TYPE, SkillRespecPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int cost = SkillRules.respecCost(SkillManager.get(player).respecs);
                        if (payload.cost() != cost && !player.isCreative()) {
                            SkillManager.sync(player);
                            return;
                        }
                        SkillManager.RespecResult r = SkillManager.respec(player);
                        if (r != SkillManager.RespecResult.OK) {
                            player.displayClientMessage(ModText.deny("message.skyabyss.skill.respec_failed." + r.name().toLowerCase(),
                                    Component.literal(String.valueOf(cost))), true);
                        }
                    }
                });
    }
}
