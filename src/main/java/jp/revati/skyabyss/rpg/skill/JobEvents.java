package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 持ち替えで職が替わる (docs/design/43)。手の武器の職が替わったら、その職の枠へ入れ替えて送り、帯の下に職の名前を一度出す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class JobEvents {
    private JobEvents() {
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator() || (p.tickCount + p.getId()) % 5 != 0 || SkillManager.IGNORE_JOBS) {
            return;
        }
        SkillData d = SkillManager.get(p);
        Job job = Job.held(p);
        // 武器でない物 (食べ物・地図・素手) を持っている間は、直前の職の枠のまま (2026-10-28 の試遊「武器切替の時、スキルが全部消える」)
        if (job != null && d.useJob(job)) {
            SkillManager.sync(p);
            if (job != null) {
                Notice.bar(p, Component.translatable("message.skyabyss.skill.job_now", Component.translatable(job.nameKey())));
            }
        }
    }
}
