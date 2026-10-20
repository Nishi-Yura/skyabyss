package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.FirstRun;

// 岩鯨の背の「初回だけ働く部屋」(21 の 1.7。FirstRun に登録する既知の道)。周回 (心臓を一度止めたグループ) には、敵のいない静かな通路になり、戸が開く:
//   whale_bell : 数え歌の鐘楼。崩れ橋への戸 (BELL_GATE) が開く (鐘は鳴るだけ)            -> BellTower
//   whale_e2   : 第二の広間。第一の広間の北の戸と第二の広間の出口が開き、射手は出ない
//   whale_ck   : 番兵の詰所。番兵長の広間への戸 (割符の戸) が開き、老番兵は出ない
//   whale_maze : 闇の水路の出口の手前の戸が開く (灯の台。AnemoneLights が登録)
// 任意の翼 (書庫・胸びれ・尾の展望・歌の祠・宿) は登録しない (いつも開いている。FirstRun.Room.optional で名前だけ残す)
public final class WhaleFirstRun {
    // 版 22: 射手の回廊 (E2) は戦わない通路。名前は残し、周回では第一の広間の北の戸 (E1_EXIT) を最初から開ける印だけに使う
    public static final String E2 = "whale_e2";
    public static final String CK = "whale_ck";
    public static final String LIBRARY_ROOM = "whale_library";

    private WhaleFirstRun() {
    }

    // 建てたあと・起動のあとに何度呼んでもよい (同じ名前は置き換える)
    public static void register() {
        BellTower.register();
        FirstRun.register(FirstRun.Room.firstRun(E2, TitanTravel.TITAN_BACK, TitanBackLayout.E2.aabb().minmax(CE1.aabb()))
                .doors(E1_EXIT));
        FirstRun.register(FirstRun.Room.firstRun(CK, TitanTravel.TITAN_BACK, TitanBackLayout.CK.aabb().minmax(CK_PASS.aabb()))
                .doors(E3_LOCK));
        FirstRun.register(FirstRun.Room.optional(LIBRARY_ROOM, TitanTravel.TITAN_BACK, LIBRARY.aabb()));
    }
}
