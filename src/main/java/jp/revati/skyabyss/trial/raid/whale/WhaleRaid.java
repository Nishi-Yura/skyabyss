package jp.revati.skyabyss.trial.raid.whale;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidRooms;

// 岩鯨のレイドの部屋 (docs/design/32 の 7 章。岩鯨の 02)。巨神の体の外の、区画に建てる閉じた部屋。枠ごとに二つあり、周ごとにどちらかが引かれる:
//   歩 WR1「骨玉運び」 — 骨玉を三つ台座へ。持つと遅く跳べず、敵に狙われる。落とすと元の場所へ戻る
//   智 WR2「共鳴の骨」 — 四隅の骨が鳴らす順を、同じ順に叩く。二つ同時の音は二人で同時に。間違えると最初から聴き直し (傷なし)
//   闘 WR3「鯨の核を守る」 — 中央の核を三つの波から守る。核が壊れたら部屋のやり直し
//   歩 WR4「胃の縦穴の上り」 — 上がってくる胃液から、生える階段を使って頂へ上る (スイッチを叩く人と登る人で分けると早い)
//   智 WR5「石の海の床合わせ」 — 床の石板を回して、壁の見本どおりの鯨の骨をつなぐ (形は毎回違う)
//   闘 WR6「縮む輪」 — 縮む安全な輪の中で三つの波を倒す
public final class WhaleRaid {
    private WhaleRaid() {
    }

    public static final String WALK = "WR1";
    public static final String WIT = "WR2";
    public static final String FIGHT = "WR3";
    // 二つ目の部屋 (枠ごとに、周ごとにどちらか一つ引く)
    public static final String WALK2 = "WR4";
    public static final String WIT2 = "WR5";
    public static final String FIGHT2 = "WR6";

    public static void register() {
        RaidRooms.register(Titans.STONE_WHALE, Slot.WALK, WALK, WhaleCarryRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.WIT, WIT, WhaleBonesRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.FIGHT, FIGHT, WhaleCoreRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.WALK, WALK2, WhaleShaftRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.WIT, WIT2, WhaleTilesRoom::new);
        RaidRooms.register(Titans.STONE_WHALE, Slot.FIGHT, FIGHT2, WhaleRingRoom::new);
    }
}
