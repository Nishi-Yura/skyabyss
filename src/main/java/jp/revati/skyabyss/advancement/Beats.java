package jp.revati.skyabyss.advancement;

import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import jp.revati.skyabyss.SkyAbyss;

// この Mod の進捗 (data/skyabyss/advancement/、生成は tools/gen_advancements.py) に物語の区切りを伝える窓口。
// 既存の物語・成長のコードから、区切りごとに一行 Beats.fire(player, "<名前>") を呼ぶ (呼んだ先は何も変えない)。
//
// 名前の一覧:
//   signal:<合図>        Quests.signal の合図 (Quests.signal の中で自動。例 signal:sky_key_assembled、signal:tide_heart_stilled)
//   talk:<役>            NPC と話した (DialogueService → Quests.onTalk の中で自動。例 talk:storyteller、talk:whale_chaser)
//   lore:<項目>          伝承の頁が記された (Lore.unlock の中で自動。例 lore:prologue/shadow)
//   place:<場所>         序盤の場所に着いた (OpeningDirector。camp / shelter / footprint / ribs)
//   trial:<巨神>         その巨神の背で、試練を初めて越えた (Quests.signal の TRIAL_CLEARED から自動)
//   stopped:<巨神>       その巨神の心臓を止めた (Progression.onTitanStopped)
//   level:<n>            レベル n になった (Growth.celebrate。飛び越えた分も送る)
//   skill:learned / skill:tree:<木>   技を覚えた / その木の最初の技 (SkillManager.learn)
//   forge:enhance / forge:dismantle / forge:reforge   共鳴の炉で鍛えた / 分解した / 打ち直した (ResonanceForgeMenu)
// 乗ること (背中への移動) と本・炉・鍵などの持ち物は、バニラの条件 (changed_dimension、inventory_changed) で足りる
public final class Beats {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, SkyAbyss.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, BeatTrigger> BEAT = TRIGGERS.register("beat", BeatTrigger::new);

    private Beats() {
    }

    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }

    public static void fire(ServerPlayer player, String event) {
        BEAT.get().trigger(player, event);
    }
}
