package jp.revati.skyabyss.trial.raid.ember;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidRooms;

// 余燼のレイドの三つの部屋 (docs/design/13_ember.md の「レイドの部屋」)。巨神の体の外の、区画に建てる閉じた部屋。
//   歩 ER1「熾火運び」 — 噴き口の熾を、冷める前に冷えた炉へ三つ。運ぶ人は熱で傷み、仲間へ渡せる
//   智 ER2「鍛冶の手順」 — 壁画の順 (型・火・水・槌) に四つの台を使う。間違えると台が戻る (傷なし)
//   闘 ER3「炉を守る」 — 大炉を三つの波から守る。灰の敵は炉を消しに来る。火鉢を灯し直すと敵が遅くなる
// 二案め (周ごとに一つ引く):
//   歩 ER4「落ちる鎖の道」 — 鎖で吊った板の橋が、乗った人の後ろから順に落ちる。渡りきる
//   智 ER5「炉の圧合わせ」 — 四つの弁で四つの圧力計の針を動かす。隣の針も動く。針を同じ目盛りへそろえる
//   闘 ER6「鍛え手の弟子たち」 — 弟子が一人ずつ、鍛え手の技を借りて出てくる。四人倒す
public final class EmberRaid {
    private EmberRaid() {
    }

    public static final String WALK = EmberCarry.ID;
    public static final String WIT = EmberForgeOrder.ID;
    public static final String FIGHT = EmberForgeDefense.ID;

    public static final String WALK2 = EmberChainPath.ID;
    public static final String WIT2 = EmberValves.ID;
    public static final String FIGHT2 = EmberApprentices.ID;

    public static void register() {
        RaidRooms.register(Titans.EMBER, Slot.WALK, WALK, EmberCarry::new);
        RaidRooms.register(Titans.EMBER, Slot.WIT, WIT, EmberForgeOrder::new);
        RaidRooms.register(Titans.EMBER, Slot.FIGHT, FIGHT, EmberForgeDefense::new);
        RaidRooms.register(Titans.EMBER, Slot.WALK, WALK2, EmberChainPath::new);
        RaidRooms.register(Titans.EMBER, Slot.WIT, WIT2, EmberValves::new);
        RaidRooms.register(Titans.EMBER, Slot.FIGHT, FIGHT2, EmberApprentices::new);
    }
}
