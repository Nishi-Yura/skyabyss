package jp.revati.skyabyss.rpg.skill.net;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.client.skill.SkillClient;
import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillCalling;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillKeystone;
import jp.revati.skyabyss.rpg.skill.SkillStance;
import jp.revati.skyabyss.rpg.skill.SkillExtOps;
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
    // 2: 技の見た目の合図に当たりの点 (SkillFxPayload.target)
    // 3: 島の祠の職選び (JobChoiceOpenPayload・JobChoosePayload)
    private static final String PROTOCOL_VERSION = "3";

    private SkillNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                // ラムダの中身はクライアントで受信したときにだけ実行されるので、専用サーバーでも安全
                .playToClient(ManaPayload.TYPE, ManaPayload.STREAM_CODEC, (payload, context) -> SkillClient.onMana(payload))
                .playToClient(SkillCastResultPayload.TYPE, SkillCastResultPayload.STREAM_CODEC, (payload, context) -> SkillClient.onCastResult(payload))
                .playToClient(SkillFxPayload.TYPE, SkillFxPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> SkillFx.onCast(payload)))
                .playToClient(CallingOpenPayload.TYPE, CallingOpenPayload.STREAM_CODEC, (payload, context) -> SkillClient.onCallingOpen(payload))
                // 島の祠で職を選ぶ (docs/design/43 の 4 節)。選び直しはできない (サーバーが検める)
                .playToClient(JobChoiceOpenPayload.TYPE, JobChoiceOpenPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(SkillClient::onJobChoiceOpen))
                .playToServer(JobChoosePayload.TYPE, JobChoosePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        jp.revati.skyabyss.rpg.skill.JobChoice.choose(player, jp.revati.skyabyss.rpg.skill.Job.byId(payload.job()));
                    }
                })
                .playToServer(CallingChoosePayload.TYPE, CallingChoosePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillCalling.Result r = SkillCalling.choose(player, payload.tree());
                        if (r != SkillCalling.Result.OK) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.calling.failed"));
                        }
                    }
                })
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
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.learn_failed." + r.name().toLowerCase()));
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
                .playToServer(SkillRunePayload.TYPE, SkillRunePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillManager.RuneResult r = SkillManager.chooseRune(player, Skill.byId(payload.skill()), payload.index());
                        if (r != SkillManager.RuneResult.OK) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.rune_failed." + r.name().toLowerCase()));
                        }
                    }
                })
                .playToServer(SkillLoadoutPayload.TYPE, SkillLoadoutPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (payload.save()) {
                            SkillManager.saveLoadout(player, payload.index());
                            Notice.bar(player, ModText.lore("message.skyabyss.skill.loadout_saved", payload.index() + 1));
                        } else {
                            SkillManager.LoadoutResult r = SkillManager.loadLoadout(player, payload.index());
                            if (r != SkillManager.LoadoutResult.OK) {
                                SkillManager.sync(player);
                                Notice.bar(player, ModText.deny("message.skyabyss.skill.loadout_failed." + r.name().toLowerCase()));
                            }
                        }
                    }
                })
                .playToServer(SkillStancePayload.TYPE, SkillStancePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillManager.StanceResult r = SkillManager.setStance(player, SkillStance.of(payload.stance()));
                        if (r == SkillManager.StanceResult.COOLDOWN) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.stance_cooldown"));
                        }
                    }
                })
                .playToServer(SkillChainPayload.TYPE, SkillChainPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillManager.ChainResult r = SkillManager.toggleChain(player, payload.slot());
                        if (r != SkillManager.ChainResult.OK) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.chain_failed." + r.name().toLowerCase()));
                        }
                    }
                })
                .playToServer(SkillKeystonePayload.TYPE, SkillKeystonePayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SkillManager.KeystoneResult r = SkillManager.toggleKeystone(player, SkillKeystone.byId(payload.id()));
                        if (r != SkillManager.KeystoneResult.OK) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.keystone_failed." + r.name().toLowerCase()));
                        }
                    }
                })
                .playToServer(SkillExtPayload.TYPE, SkillExtPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        String result = switch (payload.action()) {
                            case SkillExtPayload.MUTATE -> SkillExtOps.mutate(player, Skill.byId(payload.skill())).name();
                            case SkillExtPayload.PLACE -> SkillExtOps.place(player, payload.a(), payload.b(), Skill.byId(payload.skill())).name();
                            case SkillExtPayload.REMOVE -> SkillExtOps.remove(player, payload.a()).name();
                            default -> "BAD";
                        };
                        if (!result.equals("OK")) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.ext_failed." + result.toLowerCase()));
                        }
                    }
                })
                .playToServer(SkillTryonPayload.TYPE, SkillTryonPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int n = SkillManager.learnAll(player, payload.skills());
                        if (n < payload.skills().size()) {
                            SkillManager.sync(player);
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.tryon_partial", n, payload.skills().size()));
                        }
                    }
                })
                .playToServer(SkillRespecPayload.TYPE, SkillRespecPayload.STREAM_CODEC, (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        int cost = SkillManager.respecCostFor(player);
                        if (payload.cost() != cost && !player.isCreative()) {
                            SkillManager.sync(player);
                            return;
                        }
                        SkillManager.RespecResult r = SkillManager.respec(player);
                        if (r != SkillManager.RespecResult.OK) {
                            Notice.bar(player, ModText.deny("message.skyabyss.skill.respec_failed." + r.name().toLowerCase(),
                                    Component.literal(String.valueOf(cost))));
                        }
                    }
                });
    }
}
