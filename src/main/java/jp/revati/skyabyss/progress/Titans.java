package jp.revati.skyabyss.progress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

// 1 章の巨神 4 体の一覧 (物語の進みから見た顔)。順番・推奨レベル・上限は ProgressRules、ここは「背中の口」をまとめる。
// 巨神での移動の一覧 (titan.travel.TitanKind) は、乗って旅ができる巨神 (本体のある巨神) だけを持つ。こちらは本体がまだない巨神も含む。
//
// 雷翼・余燼の本体を作る担当へ (docs/design/14_beta.md の 5 章):
//   1. 背中のディメンションを作ったら、起動時 (FMLCommonSetupEvent の enqueueWork など) に
//        Titans.bindBack(THUNDER_BACK, Titans.THUNDER_WING, ThunderTravel::leave);
//      を一行呼ぶ。乗る資格の見回り (背中にいる資格のない人を地上へ戻す) とディメンション移動の取り消しが効くようになる
//   2. 本体を出す前に Progression.worldAllows(server, Titans.THUNDER_WING) を見る (潮殻の心臓をワールドの誰かが止めたあと)
//   3. 入口 (鎖・門・コマンド) で Progression.checkBoard(player, Titans.THUNDER_WING) を見る
//   4. 心臓を倒したら、報酬より先に Progression.onTitanStopped(server, 参加者, Titans.THUNDER_WING) を呼び、
//      巨神の記憶 (TitanMemoryData) の record.fragment で一人一つ、天の鍵の欠片 (ModItems.SKY_KEY_FRAGMENT) を渡す
//   5. 見つけたら伝承 "chapter1/thunder_wing" (余燼は "chapter1/ember") を記す (Lore.unlock)。今は manual の仮の項目で、
//      本体ができたら項目の unlock を near_entity に替えてよい。メインクエストはこの項目で「見つけた」を数える
public final class Titans {
    public static final String STONE_WHALE = "stone_whale";
    public static final String TIDE_SHELL = "tide_shell";
    public static final String THUNDER_WING = "thunder_wing";
    public static final String EMBER = "ember";

    // 背中のディメンション → 巨神の名前、巨神の名前 → 背中から地上へ戻す道
    private static final Map<ResourceKey<Level>, String> BACKS = new LinkedHashMap<>();
    private static final Map<String, Consumer<ServerPlayer>> LEAVE = new LinkedHashMap<>();

    static {
        bindBack(TitanTravel.TITAN_BACK, STONE_WHALE, TitanTravel::leave);
        bindBack(TideTravel.TIDE_BACK, TIDE_SHELL, TideTravel::leave);
    }

    private Titans() {
    }

    public static List<String> all() {
        return ProgressRules.TITAN_ORDER;
    }

    // 巨神の背中を登録する (後の巨神の担当が一行呼ぶ)。同じディメンションを二度呼んでも上書きするだけ
    public static synchronized void bindBack(ResourceKey<Level> dimension, String titan, Consumer<ServerPlayer> leave) {
        if (ProgressRules.titanIndex(titan) < 0) {
            throw new IllegalArgumentException("unknown titan " + titan);
        }
        BACKS.put(dimension, titan);
        LEAVE.put(titan, leave);
    }

    // 自己テスト用: 仮に登録した背中を外す
    public static synchronized void unbindBack(ResourceKey<Level> dimension) {
        String titan = BACKS.remove(dimension);
        if (titan != null && !BACKS.containsValue(titan)) {
            LEAVE.remove(titan);
        }
    }

    // 背中のディメンションの巨神 (背中でなければ null)
    @Nullable
    public static String titanOfBack(ResourceKey<Level> dimension) {
        return BACKS.get(dimension);
    }

    // その巨神の背中がもうあるか (本体の担当が bindBack を呼んだか)
    public static boolean hasBack(String titan) {
        return LEAVE.containsKey(titan);
    }

    // 背中から地上へ戻す。道が登録されていなければ何もしない
    public static void sendDown(ServerPlayer player, String titan) {
        Consumer<ServerPlayer> leave = LEAVE.get(titan);
        if (leave != null) {
            leave.accept(player);
        }
    }

    // その巨神がワールドに現れてよいか (前の巨神の心臓をワールドの誰かが止めた。物語の決まりを切っていれば常に)
    public static boolean mayAppear(MinecraftServer server, String titan) {
        return Progression.worldAllows(server, titan);
    }

    public static int recommendedLevel(String titan) {
        int i = ProgressRules.titanIndex(titan);
        return i < 0 ? 0 : ProgressRules.TITAN_LEVEL[i];
    }
}
