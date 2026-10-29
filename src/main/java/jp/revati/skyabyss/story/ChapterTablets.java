package jp.revati.skyabyss.story;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

// 背の外の探索の碑 (docs/design/01 の 4.1。三章の風の碑 titan/storm/chapter/StormSites と同じ形を、一章と四章にも)。
//   一章「鯨の碑」 ch1_whale_tablet ×3 (部品 ch1_tablet。碑の飾りは鯨骨)、文 sign.skyabyss.ch1_whale_1〜3、伝承 chapter1/whale_tablets
//   四章「灰の碑」 ember_tablet ×3 (部品 ch4_tablet。玄武岩と琥珀)、文 sign.skyabyss.ch4_ash_1〜3、伝承 chapter1/ember_tablets
//   どれも章の拠点から 40〜145 の地面。着くと伝承 (三枚どれでも同じ項目) と、その碑の番号の合図 (<合図>_<n>)
public final class ChapterTablets {
    public static final int COUNT = 3;

    public record Set(String element, Chapters chapter, String blueprint, RelicStyle style, String signPrefix, String lore, String signal) {
        public String signKey(int index) {
            return "sign.skyabyss." + signPrefix + "_" + (Math.floorMod(index, COUNT) + 1);
        }
    }

    public static final Set WHALE = new Set("ch1_whale_tablet", Chapters.WHALE, "ch1_tablet", RelicStyle.WHALE, "ch1_whale", "chapter1/whale_tablets", "ch1_whale");
    public static final Set EMBER = new Set("ember_tablet", Chapters.EMBER, "ch4_tablet", RelicStyle.EMBER, "ch4_ash", "chapter1/ember_tablets", "ch4_ash");

    private ChapterTablets() {
    }

    public static void register(Set set) {
        ChapterSites.element(set.element(), set.chapter(), set.blueprint()).atGround().dist(40, 145).count(COUNT).arriveRadius(6)
                .post(job -> finish(set, job))
                .onArrive((p, s) -> arrive(set, p, s.index())).register();
    }

    static void finish(Set set, SiteJob job) {
        BlockPos at = job.mark("tablet");
        if (at == null) {
            return;
        }
        job.set(at, Tablets.standingState().setValue(RelicStyle.PROP, set.style()));
        Tablets.write(job.level, at, set.signKey(job.entry.index));
    }

    static void arrive(Set set, ServerPlayer p, int index) {
        if (Lore.unlock(p, set.lore(), true)) {
            Notice.narrate(p, "message.skyabyss.tablets." + set.signPrefix());
        }
        jp.revati.skyabyss.quest.Quests.signal(p, set.signal() + "_" + (index + 1));
    }
}
