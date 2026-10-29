package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.ChapterCards;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 六章の、今の終わり (docs/design/46 の 5 章。区画 3「満月の野」まで)。
//   メイン「満月の野」(main_moon_field) を終えた人に、六章の余韻の札「六章　月　完」と、続きはまだ作っていないという一言を一度だけ出す
//   (人ごとに一度。記録は ArcData の余韻の札)。遊ぶ人が「次に何をするか」を探して迷わないための札。
//   区画 4「下弦の淵」のメインができたら、この札はそのメインを終えた人に出す形に移す (46 の 4.2)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonEnd {
    public static final String QUEST = "main_moon_field";
    public static final String LINE = "chapter.skyabyss.aftermath." + Chapters.MOON.id();
    public static final String TODO = "message.skyabyss.moon_end.todo";

    private MoonEnd() {
    }

    // 札を出す条件 (自己テストからも)
    public static boolean due(ServerPlayer p) {
        if (ArcData.of(p).aftermathShown(Chapters.MOON)) {
            return false;
        }
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        return e != null && e.done;
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 0 || !due(p)) {
            return;
        }
        if (ChapterCards.aftermath(p, Chapters.MOON, Component.translatable(LINE))) {
            Notice.narrate(p, TODO);
        }
    }
}
