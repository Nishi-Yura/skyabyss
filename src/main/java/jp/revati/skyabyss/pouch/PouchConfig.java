package jp.revati.skyabyss.pouch;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

// 留め袋の決まりを一か所にまとめた定数 (docs/design/22_mod_pouch.md の 14 章の未決の答えが出たら、ここだけを直す)。
//   名前          : 画面・知らせの文は lang の skyabyss_pouch.name だけ (旅嚢)。コードに名前は書かない
//   範囲 (204)    : ENABLED_CATEGORIES。小さな素材を外すなら SMALL を、書を外すなら BOOK を取る
//   取り出し (203): WITHDRAW_CATEGORIES (取り出して渡せる区分) と、ゲームルール skyabyssPouchWithdraw (0 で全部不可)
//   使う道具      : USE_TOOLS_IN_POUCH。笛・貝・風見鶏・書は「手に持って右クリック」で使うので、
//                   使う入力 (22 の P6) ができるまでは袋に入れない (入れると使えなくなる)
public final class PouchConfig {
    private PouchConfig() {
    }

    public enum Category {
        // 使う道具 (呼び笛・呼び貝・潮見の貝・風見鶏)
        TOOL("tool"),
        // 鍵と心核 (天の鍵の欠片・天の鍵・心核)
        KEY("key"),
        // 共鳴の結晶・共鳴の欠片
        RESONANCE("resonance"),
        // 書 (伝承の書・破れた頁)
        BOOK("book"),
        // 巨神の素材 (鱗片・殻片・羽軸・燠石)
        MATERIAL("material"),
        // 小さな素材 (鱗粉・泡袋・火の粉の粉・燠の殻・潮干しの貝)
        SMALL("small");

        public final String id;

        Category(String id) {
            this.id = id;
        }
    }

    // 袋に入れる区分 (22 の 14 章 204 の推奨: 鍵・心核・笛・書・巨神の素材 + 小さな素材。食べ物は持ち物のまま)
    public static final Set<Category> ENABLED_CATEGORIES = EnumSet.allOf(Category.class);

    // 取り出して持ち物へ出せる区分 (203 の推奨: 素材と共鳴だけ。鍵・心核・笛・欠片・書は出せない)
    public static final Set<Category> WITHDRAW_CATEGORIES = EnumSet.of(Category.RESONANCE, Category.MATERIAL, Category.SMALL);

    // 手に持たないと使えない道具 (笛・貝・風見鶏・書・頁) を袋へ入れるか。使う入力 (P6) ができたら true にする
    public static final boolean USE_TOOLS_IN_POUCH = true;

    // ゲームルール skyabyssPouchWithdraw の既定 (1 = 取り出せる、0 = 取り出せない)
    public static final int WITHDRAW_DEFAULT = 0;

    // 取りこぼしの掃除 (持ち物にある袋の品を袋へ移す) の間隔 (tick)
    public static final int SWEEP_INTERVAL = 20;

    // 「袋がいっぱい」を同じ人に繰り返さない間隔 (tick)
    public static final int FULL_NOTICE_COOLDOWN = 600;

    // 袋の親スイッチ (ゲームルール skyabyssPouch。既定は入。切にすると)。切の間は、拾う・掃除・移し替え・渡しの袋行きをしない (昔の渡し方のまま)。
    // 袋の中身は読む側が数え続け、`/skyabyss pouch take` で取り出せる (切にしても中身は失われない)。画面 (PouchScreen) と使う入力 (PouchUsable) ができたので、既定は入
    public static final boolean ENABLED_DEFAULT = true;

    public static GameRules.Key<GameRules.BooleanValue> MASTER_RULE;

    public static boolean enabled() {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? ENABLED_DEFAULT : enabled(server);
    }

    public static boolean enabled(MinecraftServer server) {
        return MASTER_RULE == null ? ENABLED_DEFAULT : server.getGameRules().getBoolean(MASTER_RULE);
    }

    public static GameRules.Key<GameRules.IntegerValue> WITHDRAW_RULE;

    public static boolean withdrawEnabled(MinecraftServer server) {
        return WITHDRAW_RULE == null ? WITHDRAW_DEFAULT > 0 : server.getGameRules().getInt(WITHDRAW_RULE) > 0;
    }
}
