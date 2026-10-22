package jp.revati.skyabyss.trial.raid.ember;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidRooms;

// 余燼のレイドの三つの部屋 (docs/design/13_ember.md の「レイドの部屋」)。巨神の体の外の、区画に建てる閉じた部屋。
//   歩 ER1「熾火運び」 — 噴き口の熾を、冷める前に冷えた炉へ三つ。運ぶ人は熱で傷み、仲間へ渡せる
//   智 ER2「鍛冶の手順」 — 壁画の順 (型・火・水・槌) に四つの台を使う。間違えると台が戻る (傷なし)
//   闘 ER3「炉を守る」 — 大炉を三つの波から守る。灰の敵は炉を消しに来る。火鉢を灯し直すと敵が遅くなる
public final class EmberRaid {
    private EmberRaid() {
    }

    public static final String WALK = EmberCarry.ID;
    public static final String WIT = EmberForgeOrder.ID;
    public static final String FIGHT = EmberForgeDefense.ID;

    public static void register() {
        RaidRooms.register(Titans.EMBER, Slot.WALK, WALK, EmberCarry::new);
        RaidRooms.register(Titans.EMBER, Slot.WIT, WIT, EmberForgeOrder::new);
        RaidRooms.register(Titans.EMBER, Slot.FIGHT, FIGHT, EmberForgeDefense::new);
    }
}
