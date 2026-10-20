package jp.revati.skyabyss.titan.storm.chapter;

import java.util.List;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

// 三章の小要素 4 種 (docs/design/12 の 12.3、01 の 4.2)。章が開いた人 (段階 2) の拠点のまわり 24〜150 ブロックに置く (置き方は 17 の 3.8)。
// 部品は data/skyabyss/opening/ch3_*.json (tools/gen_ch3_content.py)。着いたときの一度きりの知らせ・伝承・クエストの合図は onArrive。
//   storm_vane   風見の丘     高い所。石の台と銅の針の柱。矢が巨神の方角 (いなければ風の逆) を指す (StormVane)        着いたら 合図 ch3_vane
//   storm_ruin   避雷の塔の跡  折れた石の輪と針が 1 本。バニラの避雷針は雷を引き寄せる、と先に見せる                       着いたら 合図 ch3_ruin
//   storm_pack   焼けた荷     焦げた台地に割れた荷車と樽 (パン・松明・避雷針)。伝承「焼けた荷の頁」(筆跡が自分に似る。N1)   着いたら 伝承 + 合図 ch3_pack
//   storm_tablet 風の碑 3 枚  碑 (skyabyss:tablet) が文を刻んでいる (sign.skyabyss.ch3_wind_1〜3)。見誤りの話。伝承「風の碑」   着いたら 合図 ch3_wind_<n>
// 経験値は、クエスト ch3/sub_wind_marks (150 + 150) だけ。小要素そのものは経験値を渡さない。
public final class StormSites {
    public static final String VANE = StormVane.ID;
    public static final String RUIN = "storm_ruin";
    public static final String PACK = "storm_pack";
    public static final String TABLET = "storm_tablet";
    public static final int TABLETS = 3;
    public static final List<String> ELEMENTS = List.of(VANE, RUIN, PACK, TABLET);

    private static boolean registered;

    private StormSites() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ChapterSites.element(VANE, Chapters.STORM, "ch3_vane").dist(40, 140).prefer(Prefer.HIGH).arriveRadius(10)
                .post(StormVane::finish)
                .erase((level, e, mark) -> {
                    BlockPos top = mark.apply("top");
                    if (top != null) {
                        StormVane.clear(level, top);
                    }
                })
                .onArrive((p, s) -> {
                    Quests.signal(p, "ch3_vane");
                    Notice.narrate(p, "message.skyabyss.ch3.vane");
                }).register();
        ChapterSites.element(RUIN, Chapters.STORM, "ch3_ruin").dist(50, 140).arriveRadius(12)
                .onArrive((p, s) -> {
                    Quests.signal(p, "ch3_ruin");
                    Notice.narrate(p, "message.skyabyss.ch3.ruin");
                }).register();
        ChapterSites.element(PACK, Chapters.STORM, "ch3_pack").dist(50, 140).arriveRadius(8)
                .post(StormSites::finishPack)
                .onArrive((p, s) -> {
                    Lore.unlock(p, StormChapter.LORE_BURNT_PACK, true);
                    Quests.signal(p, "ch3_pack");
                    Notice.narrate(p, "message.skyabyss.ch3.pack");
                }).register();
        ChapterSites.element(TABLET, Chapters.STORM, "ch3_tablet").dist(40, 145).count(TABLETS).arriveRadius(6)
                .post(StormSites::finishTablet)
                .onArrive((p, s) -> tabletArrive(p, s.index())).register();
    }

    static void tabletArrive(ServerPlayer player, int index) {
        Lore.unlock(player, StormChapter.LORE_TABLETS, true);
        Quests.signal(player, "ch3_wind_" + (index + 1));
    }

    static String tabletKey(int index) {
        return "sign.skyabyss.ch3_wind_" + (Math.floorMod(index, TABLETS) + 1);
    }

    // 樽: パン 2・松明 4・避雷針 1 (バニラの避雷針の使い方を、荷から先に渡す)
    static void finishPack(SiteJob job) {
        BlockPos barrel = job.mark("barrel");
        if (barrel != null && job.level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
            be.setItem(0, new ItemStack(Items.BREAD, 2));
            be.setItem(1, new ItemStack(Items.TORCH, 4));
            be.setItem(2, new ItemStack(Items.LIGHTNING_ROD, 1));
        }
    }

    // 碑: 台の上 (mark "tablet") に立て、文の鍵を刻む。何枚目かは場所の番号 (0〜2)
    static void finishTablet(SiteJob job) {
        BlockPos at = job.mark("tablet");
        if (at == null) {
            return;
        }
        job.set(at, Tablets.standingState().setValue(jp.revati.skyabyss.relic.RelicStyle.PROP, jp.revati.skyabyss.relic.RelicStyle.STORM));
        Tablets.write(job.level, at, tabletKey(job.entry.index));
    }
}
