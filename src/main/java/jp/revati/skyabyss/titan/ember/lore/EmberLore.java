package jp.revati.skyabyss.titan.ember.lore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.titan.ember.EmberIds;

// 余燼の伝承・碑文・囁き・語りの文の約束 (docs/design/13_ember.md の 9 章、11.2節の担当 G)。
// 文はすべて assets/skyabyss_ember/lang の「__ember.G」の区切りの下にある (生成元は tools/gen/story/gen_ember_lore.py)。
// ほかの担当 (A 地上・B 体と広間・C 試練・D 敵・E 鍛え手・F 心臓) は、ここの鍵で文を出す。自分で同じ文を書かない
// (同じ lang のファイルで鍵が二重になるのを避けるため。文を直したいときは tools/gen/story/gen_ember_lore.py を直して実行する)。
// クラスへの依存はない (どの担当のコードが先に入っても組み上がるように)
public final class EmberLore {
    private EmberLore() {
    }

    // ---------------------------------------------------------------- 伝承の項目 (data/skyabyss/lore/entry/chapter1)。ID は EmberIds
    public static final String LORE_FOUND = EmberIds.LORE_FOUND;      // 余燼の巨神 (14 の約束の項目。文は story の担当。A が見つけたときに記す)
    public static final String LORE_TITAN = EmberIds.LORE_TITAN;      // 地を泳ぐもの (200 以内に入る / 背中に入る)
    public static final String LORE_RUINS = EmberIds.LORE_RUINS;      // 灰の民の炉 (背中に入る)
    public static final String LORE_LEDGER = EmberIds.LORE_LEDGER;    // 鍛えの帳 (破れた頁。出どころ PAGE_SOURCE)
    public static final String LORE_SMITH = EmberIds.LORE_SMITH;      // 灰の鍛え手 (倒れる)
    public static final String LORE_STILLED = EmberIds.LORE_STILLED;  // 消えた火 (心臓が倒れる)
    // 13 の 9.3節の「天の鍵」(chapter1/heaven_key) は作らない。story の SkyKey が記す chapter1/sky_key_whole がそれにあたる (14 の 4章)
    public static final String LORE_SKY_KEY = "chapter1/sky_key_whole";
    // 灰被り (D、5%) と尾の祠 (B) が落とす破れた頁の出どころ (LorePageItem.of(PAGE_SOURCE))。読むと鍛えの帳が記される
    public static final String PAGE_SOURCE = "ember_back";

    public static String inscriptionLore(String inscription) {
        // lore_ember_mouth → chapter1/inscription_ember_mouth
        return "chapter1/inscription_" + inscription.substring("lore_".length());
    }

    // ---------------------------------------------------------------- 看板 (lang の鍵。末尾の .1〜.4 を除く。B が置く)
    // 碑文 6 枚 (光る文字、4 行): EmberIds.INSCRIPTIONS → sign.skyabyss.lore_ember_*
    public static String sign(String inscription) {
        return "sign.skyabyss." + inscription;
    }

    // 試練の入口の一言 3 枚 (2 行。3 行目と 4 行目は空)
    public static final Map<String, String> TRIAL_SIGNS = ordered(
            EmberIds.WEST, "sign.skyabyss.ember_west",
            EmberIds.EAST, "sign.skyabyss.ember_east",
            EmberIds.SOUTH, "sign.skyabyss.ember_south");

    // ---------------------------------------------------------------- 囁き (B の EmberWhispers が一度だけ出す。場所 → 鍵)
    // lore は場所の気配 (語りの色)、hint は使い方 (手がかりの色)。場所は 13 の 3.3節
    public static final String WHISPER = "message.skyabyss.ember.lore.whisper.";
    public static final Map<String, String> WHISPERS = ordered(
            "tongue", WHISPER + "tongue",     // lore  到着 (舌の上)
            "throat", WHISPER + "throat",     // hint  喉の休み場 (熱の目盛り)
            "hub", WHISPER + "hub",           // hint  炉心の広間 (熾り石三つ)
            "west", WHISPER + "west",         // hint  灰の道の岸 (壺・殻・灰溜まり)
            "east", WHISPER + "east",         // hint  鍛えの間の前室 (色が合ったら打て)
            "south", WHISPER + "south",       // hint  火守りの谷の入口 (熾・噴気)
            "heart", WHISPER + "heart");      // lore  熾の底の前室
    public static final List<String> HINT_WHISPERS = List.of("throat", "hub", "west", "east", "south");

    // ---------------------------------------------------------------- 語りの文 (13 の各章から想定した鍵。担当ごとに頭を分けた)
    public static final String LINE = "message.skyabyss.ember.lore.";
    // A 地上 (2 章・2.6節の温石)
    public static final String SURFACE_SLEEP_NEAR = LINE + "surface.sleep_near";        // 眠る場所から 200 以内 (語り)
    public static final String SURFACE_BREATH_HINT = LINE + "surface.breath_hint";      // 吸うあいだ、口の 24 以内 (初めての夜だけ)
    public static final String SURFACE_SPAT_OUT = LINE + "surface.spat_out";            // 乗る資格がない
    public static final String SURFACE_STONE_GIVEN = LINE + "surface.stone_given";      // 雷翼を止めて温石を受け取った
    public static final String SURFACE_WARM = LINE + "surface.warm.";                   // + right / left / ahead / behind
    public static final String SURFACE_DIST = LINE + "surface.dist.";                   // + far / near / close
    public static final String SURFACE_STATE = LINE + "surface.state.";                 // + sleeping / burrowing
    public static final String SURFACE_ASKED = LINE + "surface.asked";                  // 今夜はそばで眠るよう頼んだ
    public static final String SURFACE_ASKED_OTHER = LINE + "surface.asked_other";      // %s の近くで眠る (ほかの人の温石)
    public static final String SURFACE_ASK_TAKEN = LINE + "surface.ask_taken";          // 今夜はもう頼まれている
    // B 体と広間
    public static final String BACK_STONE_RANG = LINE + "back.stone_rang";              // 熾り石が一つ鳴った
    public static final String BACK_ALL_STONES = LINE + "back.all_stones";              // 三つ鳴って炉の底が開く
    public static final String BACK_TRIAL_QUIET = LINE + "back.trial_quiet";          // %s (試練の名) の火は今宵は静かだ
    public static final String BACK_TRIAL_NAME = LINE + "back.trial.";                  // + west / east / south
    // C1 灰の道・C2 鍛えの間・C3 火守り
    public static final String WEST_ENGULFED = LINE + "west.engulfed";
    public static final String WEST_URN_EMPTY = LINE + "west.urn_empty";
    public static final String WEST_URN_FULL = LINE + "west.urn_full";
    public static final String EAST_DOUSED = LINE + "east.doused";
    public static final String EAST_KEY_CRACKED = LINE + "east.key_cracked";
    public static final String EAST_QUENCHED = LINE + "east.quenched";
    public static final String EAST_DOOR = LINE + "east.door";
    public static final String SOUTH_LOW = LINE + "south.low";
    public static final String SOUTH_FULL = LINE + "south.full";
    // E 鍛え手・F 心臓
    public static final String SMITH_WAKE = LINE + "smith.wake";
    public static final String SMITH_FALLEN = LINE + "smith.fallen";
    public static final String HEART_CHILL = LINE + "heart.chill";                       // 段階 3 に入る
    public static final String HEART_STILLED = LINE + "heart.stilled";                   // 心臓が止まった (本人へ)
    public static final String HEART_FRAGMENT = LINE + "heart.fragment";                 // 四つ目の欠片を受け取った (鍵がまだのとき)
    // G 締め (EmberFinale)
    public static final String FINALE_DAWN = LINE + "finale.dawn";                       // 心臓を止めて地上へ出た最初の夜明け

    // 想定した鍵の一覧 (自己テストが日英の文があるかを見る)
    public static final List<String> LINES = List.of(SURFACE_SLEEP_NEAR, SURFACE_BREATH_HINT, SURFACE_SPAT_OUT, SURFACE_STONE_GIVEN,
            SURFACE_WARM + "right", SURFACE_WARM + "left", SURFACE_WARM + "ahead", SURFACE_WARM + "behind",
            SURFACE_DIST + "far", SURFACE_DIST + "near", SURFACE_DIST + "close", SURFACE_STATE + "sleeping", SURFACE_STATE + "burrowing",
            SURFACE_ASKED, SURFACE_ASKED_OTHER, SURFACE_ASK_TAKEN,
            BACK_STONE_RANG, BACK_ALL_STONES, BACK_TRIAL_QUIET, BACK_TRIAL_NAME + "west", BACK_TRIAL_NAME + "east", BACK_TRIAL_NAME + "south",
            WEST_ENGULFED, WEST_URN_EMPTY, WEST_URN_FULL, EAST_DOUSED, EAST_KEY_CRACKED, EAST_QUENCHED, EAST_DOOR, SOUTH_LOW, SOUTH_FULL,
            SMITH_WAKE, SMITH_FALLEN, HEART_CHILL, HEART_STILLED, HEART_FRAGMENT, FINALE_DAWN);

    // 伝承の条件に使う、ほかの担当が登録するもの (巨神 A・鍛え手 E・心臓 F)。まだ登録されていないうちは、伝承の自己テストは「待っている」として扱う
    public static final List<String> PENDING = List.of(EmberIds.id(EmberIds.EMBER_TITAN).toString(), EmberIds.id(EmberIds.ASH_SMITH).toString(),
            EmberIds.id(EmberIds.EMBER_HEART).toString());

    public static boolean pending(String id) {
        return PENDING.contains(id);
    }

    // 碑文の看板の鍵 (B が背中に置く。伝承の自己テストが「置かれる看板」として数える)
    public static List<String> inscriptionSigns() {
        return EmberIds.INSCRIPTIONS.stream().map(EmberLore::sign).toList();
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
