package jp.revati.skyabyss.client.armor;

import jp.revati.skyabyss.item.StoneWhaleArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 岩鯨の甲殻の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
public class StoneWhaleArmorRenderer extends LowPolyArmorRenderer<StoneWhaleArmorItem> {
    public StoneWhaleArmorRenderer() {
        super("stone_whale");
    }
}
