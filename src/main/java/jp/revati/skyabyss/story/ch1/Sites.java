package jp.revati.skyabyss.story.ch1;

import java.util.List;

import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 序章・一章の小要素の登録 (ChapterSites。docs/design/17 の 3.8)。部品は data/skyabyss/opening/ch1_*.json (tools/gen_ch1_blueprints.py)。
// 章が開いたとき、その人の拠点のまわりに扇形の 3 方向へ置かれる。拠点から 150 ブロック以内に 2 つ以上 (序章は 3 つ)。
//   序章 (日 1)  旅の跡 3 つ (石の輪・倒れた荷車・小さな祭壇)。どれも箱 (食料・松明・低い装備) と頁 1 枚と積み石
//   一章         旅の跡 2 つ (井戸・置き去りの荷)、見張りの丘、歌石 5 つ、失せ物の荷車、日記の場所
public final class Sites {
    // 序章
    public static final String TRACE_CAMP = "ch1_trace_camp";
    public static final String TRACE_CART = "ch1_trace_cart";
    public static final String TRACE_ALTAR = "ch1_trace_altar";
    public static final List<String> PROLOGUE = List.of(TRACE_CAMP, TRACE_CART, TRACE_ALTAR);
    // 一章
    public static final String TRACE_WELL = "ch1_trace_well";
    public static final String TRACE_PACK = "ch1_trace_pack";
    public static final String LOOKOUT = "ch1_lookout";
    public static final String LOST_CART = "ch1_lost_cart";
    public static final String DIARY_SPOT = "ch1_diary_spot";
    public static final List<String> WHALE = List.of(TRACE_WELL, TRACE_PACK, LOOKOUT, LOST_CART, DIARY_SPOT,
            songStoneId(1), songStoneId(2), songStoneId(3), songStoneId(4), songStoneId(5));
    // 一章の地上の新しい場所 (21 の 3.1、P6): 鯨道の石標 7 (7 つ目が祠)・骨の集め場・割れ殻の洞。荷車の傍らの道は失せ物の荷車の隣 (LOST_CART の post)、
    // 歌の間は宿りの鐘の下 (SongChamber)
    public static final List<String> WHALE_P6 = buildP6();

    private Sites() {
    }

    private static List<String> buildP6() {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (int n = 1; n <= Ch1Prep.CAIRNS; n++) {
            ids.add(roadCairnId(n));
        }
        ids.add(Ch1Prep.BONE_CACHE);
        ids.add(Ch1Prep.CLEFT_CAVE);
        return List.copyOf(ids);
    }

    public static String roadCairnId(int n) {
        return Ch1Prep.CAIRN_PREFIX + n;
    }

    public static String songStoneId(int n) {
        return "ch1_song_stone_" + n;
    }

    // 旅の跡 (箱と頁と積み石を持つ要素)
    public static boolean isTrace(String id) {
        return id.startsWith("ch1_trace_");
    }

    static void register() {
        // ---- 序章
        trace(TRACE_CAMP, Chapters.PROLOGUE, 40, 120);
        trace(TRACE_CART, Chapters.PROLOGUE, 50, 130);
        trace(TRACE_ALTAR, Chapters.PROLOGUE, 45, 125);
        // ---- 一章
        trace(TRACE_WELL, Chapters.WHALE, 50, 130);
        trace(TRACE_PACK, Chapters.WHALE, 45, 135);
        ChapterSites.element(LOOKOUT, Chapters.WHALE, LOOKOUT).dist(60, 150).prefer(Prefer.HIGH).arriveRadius(12)
                .onArrive((p, s) -> arrive(p, s)).register();
        ChapterSites.element(LOST_CART, Chapters.WHALE, LOST_CART).dist(60, 140).arriveRadius(10)
                .post(job -> {
                    fill(job, false, null);
                    placeCartTrack(job);
                })
                .onArrive((p, s) -> {
                    arrive(p, s);
                    Quests.start(p, Ch1.Q_TRAVELLER);
                    // 荷車の傍らの道 (轍の先の旅の職人の荷。職人は npc/ArtisanSpawner が、イサナと会ったあとの人の近くに立たせる)
                    Tasks.later(80, () -> Notice.narrate(p, "message.skyabyss.ch1.arrive.ch1_cart_track"));
                }).register();
        ChapterSites.element(DIARY_SPOT, Chapters.WHALE, DIARY_SPOT).dist(100, 150).arriveRadius(8)
                .post(job -> fill(job, false, Ch1.PAGE_DIARY))
                .onArrive((p, s) -> arrive(p, s)).register();
        for (int n = 1; n <= SongStones.COUNT; n++) {
            ChapterSites.element(songStoneId(n), Chapters.WHALE, songStoneId(n)).dist(50, 150).arriveRadius(8)
                    .onArrive((p, s) -> arrive(p, s)).register();
        }
        registerP6();
    }

    // ---------------------------------------------------------------- 21 の 3.1 の新しい場所 (P6)
    static void registerP6() {
        // 鯨道の石標: 拠点から外へ、空の下へ向かう道すじ (n 番目は 24 + 18 (n-1) から 16 ブロックの幅の輪に置く。扇の 3 方向に散る)。7 つ目は祠
        for (int n = 1; n <= Ch1Prep.CAIRNS; n++) {
            int min = 24 + 18 * (n - 1);
            int last = n;
            ChapterSites.element(roadCairnId(n), Chapters.WHALE, last == Ch1Prep.CAIRNS ? "ch1_road_shrine" : "ch1_road_cairn").dist(min, Math.min(150, min + 16))
                    .arriveRadius(6).onArrive((p, s) -> {
                        arrive(p, s);
                        if (last == Ch1Prep.CAIRNS) {
                            Tasks.later(40, () -> Lore.unlock(p, Ch1Prep.ROAD_CAIRNS, true));
                        }
                    }).register();
        }
        // 骨の集め場: 骨の山。人ごとの一度きりの抽選 (旅装の素材 + 道の固焼き 3)。TODO(P7): 骨負いがここへ導く (ch1/follow_hauler)
        SiteTreasure.register(Ch1Prep.BONE_CACHE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:rock_shell_scrap", 3),
                new SiteTreasure.Entry("skyabyss:bone_chip", 3),
                new SiteTreasure.Entry("skyabyss:road_hardtack", 3))));
        ChapterSites.element(Ch1Prep.BONE_CACHE, Chapters.WHALE, Ch1Prep.BONE_CACHE).dist(60, 140).arriveRadius(8).onArrive((p, s) -> {
            arrive(p, s);
            if (!SiteTreasure.claim(p, Ch1Prep.BONE_CACHE).isEmpty()) {
                Tasks.later(40, () -> Notice.narrate(p, "message.skyabyss.ch1.cache.found"));
            }
        }).register();
        CleftCave.register();
        Ch1Prep.register();
        Ch1Mobs.registerWanderers();
    }

    // 荷車の傍らの道: 失せ物の荷車の隣 (東へ 10) に轍と荷を置く。印 "artisan" (職人が立つ所) を記録に残す
    static void placeCartTrack(SiteJob job) {
        BlockPos at = Blueprint.transform(10, 0, 0, job.rot, job.mirror, job.origin);
        BlockPos sign = job.placeSmall(Blueprint.get(Ch1Prep.CART_TRACK_BLUEPRINT), at.getX(), at.getZ(), job.rot);
        if (sign != null) {
            job.entry.extra("artisan", sign);
        }
    }

    private static void trace(String id, Chapters chapter, int min, int max) {
        ChapterSites.element(id, chapter, id).dist(min, max).arriveRadius(10)
                .post(job -> fill(job, true, Ch1.PAGE_TRACE))
                .onArrive((p, s) -> {
                    arrive(p, s);
                    // 積み石の見回りの数に数える
                    Ch1Data data = Ch1Data.get(p.serverLevel().getServer().overworld());
                    if (data.player(p.getUUID()).rounds.add("s:" + s.id() + "#" + s.index())) {
                        data.setDirty();
                    }
                }).register();
    }

    private static void arrive(ServerPlayer player, ChapterSites.Site site) {
        Notice.narrate(player, "message.skyabyss.ch1.arrive." + site.id());
    }

    // 箱の中身: 食料・松明・低い装備 (跡のとき)。page が null でなければ頁を 1 枚。印 "chest" の箱か樽に入れる
    static void fill(SiteJob job, boolean gear, String page) {
        BlockPos at = job.mark("chest");
        if (at == null) {
            return;
        }
        ServerLevel level = job.level;
        if (!(level.getBlockEntity(at) instanceof Container box)) {
            return;
        }
        int slot = 0;
        box.setItem(slot++, new ItemStack(Items.BREAD, 1 + level.random.nextInt(2)));
        box.setItem(slot++, new ItemStack(Items.TORCH, 2 + level.random.nextInt(3)));
        if (gear) {
            ItemStack low = switch (level.random.nextInt(4)) {
                case 0 -> new ItemStack(Items.WOODEN_SWORD);
                case 1 -> new ItemStack(Items.LEATHER_HELMET);
                case 2 -> new ItemStack(Items.STONE_PICKAXE);
                default -> new ItemStack(Items.LEATHER_BOOTS);
            };
            box.setItem(slot++, low);
        }
        if (page != null) {
            box.setItem(slot, LorePageItem.of(page));
        }
        box.setChanged();
    }
}
