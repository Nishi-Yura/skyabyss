package jp.revati.skyabyss.titan.storm.lore;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// 雷翼の伝承とクエストの約束 (docs/design/12_thunder_wing.md の 9 章・11.1 節)。担当 6 (伝承とクエスト) が持つ。
// ほかの担当 (本体と背中・敵・雷獣・心臓) は、ここの名前で看板を立て、合図を送り、伝承を記す。
// 背中や敵のクラスに依存しない (どの担当のコードが先に入っても、ここだけで組み上がるように)
public final class StormLore {
    public static final String NS = SkyAbyss.MODID + "_storm";
    // 12 の 11.1 節の ID (背中・巨神・敵は別の担当が登録する)
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));
    public static final String TITAN = "skyabyss:thunder_wing";
    public static final String RAIJU = "skyabyss:raiju";
    public static final String HEART = "skyabyss:thunder_wing_heart";
    public static final List<String> BACK_MOBS = List.of("skyabyss:feather_warden", "skyabyss:static_moth", "skyabyss:thunder_supplicant");
    public static final String WEATHERCOCK = "skyabyss:weathercock";
    public static final String SHARD = "skyabyss:thunder_wing_shard";

    // ---------------------------------------------------------------- クエストの合図 (Quests.signal)
    // 風鳴り石が鳴ったとき、鳴らした場に居合わせた人へ (人ごと): storm_trial_west / storm_trial_east / storm_trial_front。
    // あわせて、背中の共通の合図 FreeQuestEvents.TRIAL_CLEARED を signalWithParty で (週替わり「背中の試練」が数える)
    public static final String SIGNAL_TRIAL = "storm_trial_";
    public static final List<String> TRIALS = List.of("west", "east", "front");
    // 雷翼の心臓が倒れたとき、参加者へ (人ごと。潮殻の tide_heart_stilled と同じ形)
    public static final String SIGNAL_HEART = "storm_heart_stilled";

    public static String trialSignal(String trial) {
        return SIGNAL_TRIAL + trial;
    }

    // ---------------------------------------------------------------- 伝承の項目 (data/skyabyss/lore/entry/chapter1)
    public static final String LORE_SIGHT = "chapter1/storm_wing";      // 風に逆らう嵐 (240 ブロック以内に入る / 背中に入る)
    public static final String LORE_TITAN = "chapter1/thunder_wing";    // 雷翼の巨神 (96 以内。14 の約束で本体の担当が manual でも記す)
    public static final String LORE_RUINS = "chapter1/storm_ruins";     // 風の民の遺跡 (背中に入る)
    public static final String LORE_COWER = "chapter1/storm_cower";     // うずくまる巨神 (本体の担当が manual で記す)
    public static final String LORE_LEDGER = "chapter1/wind_ledger";    // 風見の帳 (破れた頁。出どころ PAGE_SOURCE)
    public static final String LORE_RAIJU = "chapter1/raiju";           // 雷獣 (倒れる)
    public static final String LORE_STILLED = "chapter1/storm_stilled"; // 晴れた空 (心臓が倒れる)
    // 雷乞いが落とす破れた頁の出どころ (LorePageItem.of(PAGE_SOURCE))。読むと風見の帳が記される
    public static final String PAGE_SOURCE = "storm_back";

    // ---------------------------------------------------------------- 看板 (lang のキー。末尾の .1〜.4 を除く)
    // 碑文 6 枚 (光る文字、4 行)。場所は 12 の 3.3 節
    public static final Map<String, String> INSCRIPTIONS = Map.of(
            "tail", "sign.skyabyss.lore_storm_tail",
            "hub", "sign.skyabyss.lore_storm_hub",
            "west", "sign.skyabyss.lore_storm_west",
            "east", "sign.skyabyss.lore_storm_east",
            "eye", "sign.skyabyss.lore_storm_eye",
            "heart", "sign.skyabyss.lore_storm_heart");
    // 試練の入口の一言 3 枚 (2 行): 左の肩の門・右の肩の門・首の前室
    public static final Map<String, String> TRIAL_SIGNS = Map.of(
            "west", "sign.skyabyss.storm_west",
            "east", "sign.skyabyss.storm_east",
            "front", "sign.skyabyss.storm_front");

    public static String inscriptionLore(String place) {
        return "chapter1/inscription_storm_" + place;
    }

    // 碑文に「近くで目を向けるだけで読める」を効かせるための置き場所。背中の担当が建てたあとで登録する
    // (StormLore.inscriptionPositions(() -> List.of(Map.entry(キー, 位置), ...)))。登録がなければ、右クリックで読むだけになる
    private static Supplier<List<Map.Entry<String, BlockPos>>> positions = List::of;

    public static void inscriptionPositions(Supplier<List<Map.Entry<String, BlockPos>>> where) {
        positions = where;
    }

    public static List<Map.Entry<String, BlockPos>> inscriptionPositions() {
        return positions.get();
    }

    // ---------------------------------------------------------------- クエスト (data/skyabyss/quest/*storm*。tools/gen/story/gen_storm_lore.py が書く)
    public static final Set<String> QUESTS = Set.of("daily_storm_rounds", "weekly_storm_heart");

    // ほかの担当が登録する ID。まだ登録されていないうちは、伝承と自己テストは「待っている」として扱う
    public static final Set<String> PENDING = Set.of(TITAN, RAIJU, HEART, "skyabyss:storm_back", WEATHERCOCK, SHARD,
            "skyabyss:feather_warden", "skyabyss:static_moth", "skyabyss:thunder_supplicant");

    public static boolean pending(String id) {
        return PENDING.contains(id);
    }

    private StormLore() {
    }
}
