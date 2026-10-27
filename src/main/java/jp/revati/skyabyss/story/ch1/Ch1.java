package jp.revati.skyabyss.story.ch1;

import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.arc.ForeshadowRegisterEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

// 序章 (日 1) と一章「空を泳ぐもの」(岩鯨) の中身 (P4a)。docs/design/17 の 1.5・3.8、01 の 4.2、docs/review/chapter_per_titan_plan.md の 1.3〜1.4。
// 足すのは「跡・頼み・音・小さな異変」だけ (敵・ダンジョン・ジャンプスケアは足さない)。チャットには流さない (知らせは Notice)。
//
//   小要素 (ChapterSites)  序章: 旅の跡 3 (Sites.PROLOGUE)。一章: 旅の跡 2・見張りの丘・歌石 5・失せ物の荷車と日記の場所 (Sites.WHALE)
//   宿りの炉端 (LodgeNook)   鐘の宿りの小屋に、ベッド・かまど・言伝ての板 (初日から使える)
//   小さな異変 (Oddities)    毎日 1 つ (日 1 から。夜に岩鯨の方を向く見る者・縁の音は別の担当に残す)
//   歌石 (SongStones)        5 つ。積み石の低い順に叩くと 5 つ目で宿りの鐘が応える
//   見張りの丘 (Lookout)     夕方に筒を覗くと遠い雲に縁
//   歩哨の巡回 (SentryPatrol) 骨の丘のそばを夜だけ、既存の骨の番兵が一体歩く。昼は眠る (消える)
//   予兆 3 拍 (Beats)         石の粉 → 地面を渡る影 → 空から鎖の環
//   中盤の出来事 (WhaleHush)  岩鯨が初めて降りるとき、空の影 (巡礼者) が通り、岩鯨が歌をやめる。影が去ると鎖が降りる
//   余韻 (Aftermath)         心臓を止めたあと、章の終わりの札と語り部の一言、欠けた歌
//
// 経験値: 小要素の経験値は一章全体で 1〜1.5 レベル分まで (01 の 4.1)。頁 6〜15、お使い 20、失せ物 40 (合計で約 270)。LORE_XP に足す
// 文は assets/skyabyss_ch1/lang (tools/gen/ch1/gen_ch1_content.py)。部品は tools/gen/ch1/gen_ch1_blueprints.py
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch1 {
    // ---------------------------------------------------------------- 伝承 (12 項目。lore/entry/ch1/)
    public static final String PAGE_TRACE = "ch1_trace";
    public static final String PAGE_DIARY = "ch1_diary";
    // 旅の跡の頁 5 (出どころ ch1_trace。読む人ごとにまだ記していないものが一つ)
    public static final List<String> TRACE_LORE = List.of("ch1/trace_ring", "ch1/trace_cart", "ch1/trace_altar", "ch1/trace_well", "ch1/trace_pack");
    public static final String DIARY = "ch1/traveler_diary";
    public static final String LOOKOUT = "ch1/lookout";
    public static final String SONG = "ch1/song_stones";
    public static final String SENTRY = "ch1/sentry";
    public static final String HUSH = "ch1/whale_hush";
    public static final String WRONGS = "ch1/small_wrongs";
    // 頁の数 (控え帳の「縁と頁」が埋まる)
    public static final List<String> ALL_LORE = List.of("ch1/trace_ring", "ch1/trace_cart", "ch1/trace_altar", "ch1/trace_well", "ch1/trace_pack",
            DIARY, LOOKOUT, SONG, SENTRY, HUSH, WRONGS);
    // コードが直接記す項目 (条件が manual のもの)
    public static final List<String> MANUAL_LORE = List.of(LOOKOUT, SONG, SENTRY, HUSH, WRONGS);
    // 経験値 (「はじめて見た物」。GrowthRewards.LORE_XP に足す。一人一回)
    public static final Map<String, Integer> LORE_XP = Map.ofEntries(
            Map.entry("ch1/trace_ring", 6), Map.entry("ch1/trace_cart", 6), Map.entry("ch1/trace_altar", 6),
            Map.entry("ch1/trace_well", 6), Map.entry("ch1/trace_pack", 6),
            Map.entry(DIARY, 12), Map.entry(LOOKOUT, 15), Map.entry(SONG, 20), Map.entry(SENTRY, 15),
            Map.entry(HUSH, 15), Map.entry(WRONGS, 8));

    // ---------------------------------------------------------------- クエスト (quest/ch1/)
    public static final String Q_TRAVELLER = "ch1/lost_traveller";
    public static final String SIGNAL_ROUND = "ch1_trace_round";
    public static final String TOPIC_DIARY_TELL = "ch1_diary_tell";

    public static final ResourceLocation SONG_STONE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "song_stone");

    private Ch1() {
    }

    // 歌石のブロック (BlockItem は作らない: 世界に置かれるだけ)
    public static Block songStone() {
        return BuiltInRegistries.BLOCK.get(SONG_STONE_ID);
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> helper.register(SONG_STONE_ID, new SongStoneBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY).strength(3.0f, 3600000.0f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> state.getValue(SongStoneBlock.LIT) ? 9 : 0))));
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LORE_XP.forEach(GrowthRewards.LORE_XP::putIfAbsent);
            Sites.register();
        });
    }

    @SubscribeEvent
    static void onForeshadow(ForeshadowRegisterEvent event) {
        Beats.register(event);
    }
}
