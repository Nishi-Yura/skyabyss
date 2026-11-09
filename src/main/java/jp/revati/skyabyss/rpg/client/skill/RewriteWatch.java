package jp.revati.skyabyss.rpg.client.skill;

import java.util.HashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.notice.NoticePayload;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 武器を持ち替えて技の書き換えが効いた・外れたとき、手もとで一行知らせる (2026-11-09 の声「炉の踏み → 潮の踏みに変わっても、
// スキルの見た目もアイコンも変わらないので、何がなにかわからない (説明もないので)」)。技の帯の絵は SkillChanges.icon が毎フレーム替える。
// 枠に置いた技だけを見る。入った直後の一回目は知らせない (持っていた武器のまま)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class RewriteWatch {
    private static final Map<String, Rune> LAST = new HashMap<>();
    private static boolean primed;

    private RewriteWatch() {
    }

    @SubscribeEvent
    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        LAST.clear();
        primed = false;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.tickCount % 5 != 0) {
            return;
        }
        SkillData d = SkillManager.get(mc.player);
        for (int i = 0; i < d.slotCount(); i++) {
            Skill s = d.slot(i);
            if (s == null) {
                continue;
            }
            Rune now = SkillManager.legendRune(mc.player, s);
            Rune before = LAST.get(s.id);
            if (now == before) {
                continue;
            }
            if (now == null) {
                LAST.remove(s.id);
            } else {
                LAST.put(s.id, now);
            }
            if (!primed) {
                continue;
            }
            Component text = now != null
                    ? Component.translatable("message.skyabyss.skill.rewrite_on", Component.translatable(s.nameKey()), Component.translatable(now.nameKey()),
                            Component.translatable(now.effectKey()))
                    : Component.translatable("message.skyabyss.skill.rewrite_off", Component.translatable(s.nameKey()));
            // 持ち替えるたびに画面下の一行 (短く)。何が変わるかの説明は、その書き換えで初めてのときだけ手がかりで
            mc.gui.setOverlayMessage(now != null
                    ? Component.translatable("message.skyabyss.skill.rewrite_bar", Component.translatable(s.nameKey()), Component.translatable(now.nameKey())).withColor(0xF2D78A)
                    : Component.translatable("message.skyabyss.skill.rewrite_off", Component.translatable(s.nameKey())).withColor(0xA9AEBC), false);
            if (now != null) {
                jp.revati.skyabyss.client.notice.NoticeClient.receive(new NoticePayload(Notice.Kind.HINT, text, Component.empty()));
            }
        }
        primed = true;
    }
}
