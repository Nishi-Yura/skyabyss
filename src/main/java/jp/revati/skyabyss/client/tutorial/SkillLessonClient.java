package jp.revati.skyabyss.client.tutorial;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.client.quest.QuestClient;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.rpg.client.skill.SkillClientSetup;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.tutorial.SkillLesson;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// 技の稽古 (tutorial.SkillLesson) の画面の案内。同期されたクエストの記録から今の段を読み、
//   ・ホットバーの上の手引きの列に、今のキー設定のキー名を入れた一行 (TutorialClient が並べる)
//   ・HUD の技の枠: 使う段では使う枠を金の縁で呼吸させる (SkillHud)
//   ・技の画面: 覚える段では覚えられる能動の技と「習得」の釦を光らせ、上に一行 (SkillScreen)
public final class SkillLessonClient {
    private SkillLessonClient() {
    }

    // 今の段 (受けていない・済んだなら null)
    @Nullable
    public static String stage(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        QuestLog.Entry e = QuestLog.of(mc.player).entry(SkillLesson.QUEST);
        QuestDef def = QuestClient.quest(SkillLesson.QUEST);
        if (e == null || e.done || def == null || e.stage >= def.stages().size()) {
            return null;
        }
        return def.stages().get(e.stage).id();
    }

    // 手引きの列に出す一行 (なければ null)
    @Nullable
    public static Component hint(Minecraft mc) {
        String st = stage(mc);
        if (st == null) {
            return null;
        }
        return switch (st) {
            case "learn" -> Component.translatable("lesson.skyabyss.hint.learn", SkillClientSetup.SKILLS_KEY.getTranslatedKeyMessage());
            case "cast" -> {
                int slot = Math.max(0, castSlot(mc));
                yield Component.translatable("lesson.skyabyss.hint.cast", SkillClientSetup.SLOT_KEYS[slot].getTranslatedKeyMessage());
            }
            case "behind" -> Component.translatable("lesson.skyabyss.hint.behind");
            case "stance" -> Component.translatable("lesson.skyabyss.hint.stance", SkillClientSetup.STANCE_KEY.getTranslatedKeyMessage());
            case "reset" -> Component.translatable("lesson.skyabyss.hint.reset");
            default -> null;
        };
    }

    // 使う段で光らせる枠 (最初の技のある枠。ほかの段・技がなければ -1)
    public static int castSlot(Minecraft mc) {
        if (mc.player == null || !"cast".equals(stage(mc))) {
            return -1;
        }
        SkillData d = SkillManager.get(mc.player);
        for (int i = 0; i < d.slotCount(); i++) {
            Skill s = d.slot(i);
            if (s != null && s.active) {
                return i;
            }
        }
        return -1;
    }

    // 覚える段か (技の画面が光らせる)
    public static boolean learning(Minecraft mc) {
        return "learn".equals(stage(mc));
    }

    // 呼吸する金の縁 (x, y から w × h の外側に 1 px)
    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        float pulse = 0.55f + 0.45f * Mth.sin(Util.getMillis() / 260f);
        int c = ((int) (pulse * 255) << 24) | 0xF2C14E;
        g.fill(x - 1, y - 1, x + w + 1, y, c);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, c);
        g.fill(x - 1, y, x, y + h, c);
        g.fill(x + w, y, x + w + 1, y + h, c);
    }
}
