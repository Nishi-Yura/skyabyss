package jp.revati.skyabyss.trial.raid.whale;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidRooms;

// 岩鯨のレイドの三つの部屋 (docs/design/32 の 7 章。岩鯨の 02)。巨神の体の外の、区画に建てる閉じた部屋。毎回同じ三つ:
//   歩 WR1「骨玉運び」 — 骨玉を三つ台座へ。持つと遅く跳べず、敵に狙われる。落とすと元の場所へ戻る
//   智 WR2「共鳴の骨」 — 四隅の骨が鳴らす順を、同じ順に叩く。二つ同時の音は二人で同時に。間違えると最初から聴き直し (傷なし)
//   闘 WR3「鯨の核を守る」 — 中央の核を三つの波から守る。核が壊れたら部屋のやり直し
public final class WhaleRaid {
    private WhaleRaid() {
    }

    public static final String WALK = "WR1";
    public static final String WIT = "WR2";
    public static final String FIGHT = "WR3";

    public static void register() {
        RaidRooms.register(Titans.STONE_WHALE, Slot.WALK, WALK, WhaleCarryRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.WIT, WIT, WhaleBonesRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.FIGHT, FIGHT, WhaleCoreRoom::new);
    }
}
