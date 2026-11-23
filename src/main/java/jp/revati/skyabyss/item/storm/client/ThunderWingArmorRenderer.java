package jp.revati.skyabyss.item.storm.client;

import jp.revati.skyabyss.item.storm.ThunderWingArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 雷翼の羽衣の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
public class ThunderWingArmorRenderer extends LowPolyArmorRenderer<ThunderWingArmorItem> {
    public ThunderWingArmorRenderer() {
        super("thunder_wing");
    }
}
