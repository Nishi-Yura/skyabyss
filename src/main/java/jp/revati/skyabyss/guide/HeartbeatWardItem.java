package jp.revati.skyabyss.guide;

import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;

// 鼓動守 (docs/design/21 の 1.4。巨神ごと)。装飾の枠に付けているあいだ、心臓の戦いで一度だけ、致命の一撃を体力 1 で耐える (HeartbeatWard)。
// 巨神ごとに別の品 (由来の巨神 = 必要 Lv の決まり方)。階級はアンコモン。鍛えない・特性なし
public class HeartbeatWardItem extends GearItem {
    private final String titan;

    public HeartbeatWardItem(Properties properties, String titan) {
        super(properties, GearTier.UNCOMMON, "item.skyabyss.heartbeat_ward.desc", false);
        this.titan = titan;
    }

    @Override
    public String dungeon() {
        return titan;
    }

    public String titan() {
        return titan;
    }
}
