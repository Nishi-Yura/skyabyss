package jp.revati.skyabyss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// この mod のキーの既定の割り当て (GLFW のキーコード。サーバーの自己テストからも読めるよう、LWJGL に依存しない数で持つ)。
// クライアントの KeyMapping はこの値で作る。2 つの操作が同じ既定を持たないこと・バニラの既定と重ならないことは KeyDefaultsSelfTest が見る。
//   H 護符 / ; 道しるべ / N 知らせの記録 / Y 旅嚢で使う / I 旅嚢 / U クエスト / B 控え帳 / O 仲間 / M 旅の地図 / ' 合図 / J 状態 / K 技
//   . 構えの切り替え / R G V Z , 技の枠 1〜5 / 左 Alt 輪の選び画面
public final class KeyDefaults {
    public static final int CHARM = 72; // H
    public static final int GUIDE_TRAIL = 59; // ;
    public static final int NOTICE_LOG = 78; // N
    public static final int POUCH_USE = 89; // Y
    public static final int POUCH_OPEN = 73; // I
    public static final int QUEST_LOG = 85; // U
    public static final int RECORD = 66; // B
    public static final int PARTY = 79; // O
    public static final int MAP = 77; // M 旅の地図
    public static final int PING = 39; // ' 合図 (「ここ」。日本語配列では : の位置)
    public static final int STATUS = 74; // J
    public static final int SKILLS = 75; // K
    public static final int STANCE = 46; // .
    public static final int WEAPON_INSPECT = 91; // [ 武器の点検 (かざして回す)
    public static final int WHEEL = 342; // 左 Alt 長押しで輪の選び画面 (クエスト・旅嚢・技・地図…を選んで開く)
    public static final int[] SLOTS = {82, 71, 86, 90, 44}; // R G V Z ,

    private KeyDefaults() {
    }

    // この mod の全キー (名前 → コード)
    public static Map<String, Integer> all() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("charm", CHARM);
        m.put("guide_trail", GUIDE_TRAIL);
        m.put("notice_log", NOTICE_LOG);
        m.put("pouch_use", POUCH_USE);
        m.put("pouch_open", POUCH_OPEN);
        m.put("quest_log", QUEST_LOG);
        m.put("record", RECORD);
        m.put("party", PARTY);
        m.put("map", MAP);
        m.put("ping", PING);
        m.put("status", STATUS);
        m.put("skills", SKILLS);
        m.put("stance", STANCE);
        m.put("weapon_inspect", WEAPON_INSPECT);
        m.put("wheel", WHEEL);
        for (jp.revati.skyabyss.abyss.arts.AbyssArt art : jp.revati.skyabyss.abyss.arts.AbyssArt.values()) {
            m.put("abyss_art_" + art.id, art.key);
        }
        for (int i = 0; i < SLOTS.length; i++) {
            m.put("slot_" + (i + 1), SLOTS[i]);
        }
        return m;
    }

    // バニラ (1.21) の既定のキー。W A S D / ジャンプ・スニーク・ダッシュ / 持ち物・捨てる・持ち替え / チャット・コマンド / 進捗・社交 / 一覧 / ホットバー 1〜9 /
    // クリエイティブのホットバー保存と読み込み (C・X) / スクリーンショット・デバッグなど
    public static final Map<Integer, String> VANILLA = Map.ofEntries(
            Map.entry(87, "W forward"), Map.entry(65, "A left"), Map.entry(83, "S back"), Map.entry(68, "D right"),
            Map.entry(32, "space jump"), Map.entry(340, "shift sneak"), Map.entry(341, "ctrl sprint"), Map.entry(69, "E inventory"),
            Map.entry(81, "Q drop"), Map.entry(70, "F swap hands"), Map.entry(84, "T chat"), Map.entry(47, "/ command"),
            Map.entry(76, "L advancements"), Map.entry(80, "P social"), Map.entry(258, "tab player list"), Map.entry(67, "C save hotbar"),
            Map.entry(88, "X load hotbar"), Map.entry(49, "1"), Map.entry(50, "2"), Map.entry(51, "3"), Map.entry(52, "4"), Map.entry(53, "5"),
            Map.entry(54, "6"), Map.entry(55, "7"), Map.entry(56, "8"), Map.entry(57, "9"), Map.entry(290, "F1"), Map.entry(292, "F3"),
            Map.entry(294, "F5"), Map.entry(300, "F11"));

    // 重なりの一覧 (空なら良い)
    public static List<String> conflicts() {
        List<String> out = new java.util.ArrayList<>();
        Map<Integer, String> seen = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : all().entrySet()) {
            String prev = seen.put(e.getValue(), e.getKey());
            if (prev != null) {
                out.add("default key " + e.getValue() + " is shared by " + prev + " and " + e.getKey());
            }
            String vanilla = VANILLA.get(e.getValue());
            if (vanilla != null) {
                out.add("default key " + e.getValue() + " of " + e.getKey() + " collides with vanilla " + vanilla);
            }
        }
        return out;
    }
}
