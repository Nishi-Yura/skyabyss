package jp.revati.skyabyss.titan.ember.back;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;

// 部屋の中身の建て方とリセットの受け口 (13 の 11.2節「部屋の箱の約束」、18 章)。
// C1 (west)・C2 (east)・E (smith)・C3 (south)・F (heart) は、自分の Setup の共通の準備 (FMLCommonSetupEvent) か static 初期化で一度だけ
//   EmberBackRooms.register("west", 1, AshPathRoom::build);      (1 は中身の版。中身を変えたら上げると、起動時に建て直す)
//   EmberBackRooms.onReset("west", AshPath::reset);
// のように渡す。体を建てるとき (EmberBodyBuilder) と、部屋の持ち主 (EmberRooms・RoomLocks) が替わるとき・グループのリセットで B が呼ぶ。
//
// - build(level, box): 体を置き、B が箱を掘ったあとに呼ぶ。箱の中はすべて空気で、いちばん下の段 (box.minY()) にだけ仮の床がある。
//   箱の中だけに置くこと。EmberBackLayout.reserved(部屋) の場所は空けておく (B が後で休み場・熾り石・碑文を置く)。
//   建て直し (LAYOUT_VERSION が上がった・titan rebuild ember) のたびに呼ばれるので、何度呼ばれても同じ物になるように書く。
// - reset(level, quiet): 部屋の持ち主が替わるたび (手放したとき = 持ち主なし、新しい持ち主が入ったとき)。部屋の中身は持ち主のためだけに動くので、
//   持ち主の記録 (EmberBackState.forRoom) から作り直す。quiet は「持ち主にとって、この周この試練は静かな試練」
//   (静かな試練も石は鳴っておらず、最後までできる。東が静かな周は smith も quiet)。熾り石・休み場は B が戻すので触らなくてよい。
//   ほかのグループの人の持ち物には触らない (試練の品は EmberTrials の見回りが、部屋の外で取り上げる)
public final class EmberBackRooms {
    @FunctionalInterface
    public interface Builder {
        void build(ServerLevel level, EmberBackLayout.Box box);
    }

    @FunctionalInterface
    public interface Resetter {
        void reset(ServerLevel level, boolean quiet);
    }

    private static final Map<String, List<Builder>> BUILDERS = new LinkedHashMap<>();
    private static final Map<String, Integer> VERSIONS = new LinkedHashMap<>();
    private static final Map<String, List<Resetter>> RESETTERS = new LinkedHashMap<>();

    private EmberBackRooms() {
    }

    public static synchronized void register(String room, Builder builder) {
        register(room, 1, builder);
    }

    // version: 中身を変えたら上げる (体を建て直す。各部屋の版の和が変わると、起動時に建て直す)
    public static synchronized void register(String room, int version, Builder builder) {
        check(room);
        BUILDERS.computeIfAbsent(room, k -> new ArrayList<>()).add(builder);
        VERSIONS.merge(room, version, Integer::sum);
    }

    // 中身の版 (部屋ごとの版を名前つきで混ぜた数)
    public static synchronized int contentVersion() {
        int v = 0;
        for (Map.Entry<String, Integer> e : VERSIONS.entrySet()) {
            v = v * 31 + e.getKey().hashCode() % 97 + e.getValue() * 7;
        }
        return v;
    }

    public static synchronized void onReset(String room, Resetter resetter) {
        check(room);
        RESETTERS.computeIfAbsent(room, k -> new ArrayList<>()).add(resetter);
    }

    private static void check(String room) {
        if (!EmberBackLayout.TRIAL_ROOMS.contains(room)) {
            throw new IllegalArgumentException("unknown ember room " + room + " (use one of " + EmberBackLayout.TRIAL_ROOMS + ")");
        }
    }

    // 担当が建て方を渡しているか (自己テストと、仮の床を残すかの判断)
    public static synchronized boolean hasBuilder(String room) {
        return BUILDERS.containsKey(room);
    }

    // 占有の部屋 (EmberRooms) を最初に戻す / いまの持ち主の記録から作り直す。持ち主 (RoomLocks) を決めてから呼ぶ。
    // quiet は持ち主のグループにとってその部屋が静かな周か (east が静かなら smith も)。持ち主がいなければ静かでない最初の形
    static void resetRoom(ServerLevel level, EmberRooms.Room room) {
        EmberBackState owner = EmberBackState.ofNullable(level, EmberRooms.owner(room));
        List<Map.Entry<String, List<Resetter>>> list;
        synchronized (EmberBackRooms.class) {
            list = new ArrayList<>(RESETTERS.entrySet());
        }
        for (Map.Entry<String, List<Resetter>> e : list) {
            if (!room.parts().contains(e.getKey())) {
                continue;
            }
            for (Resetter r : e.getValue()) {
                try {
                    r.reset(level, owner.roomQuiet(e.getKey()));
                } catch (RuntimeException ex) {
                    SkyAbyss.LOGGER.error("ember back: resetting room {} failed", e.getKey(), ex);
                }
            }
        }
        // 部屋を作り直すと壁の灯り (歩く道の灯り) も石に戻るので、置き直す (置いてある灯りは飛ばす)
        jp.revati.skyabyss.titan.light.RouteLamps.place(level, "ember");
    }
}
