package jp.revati.skyabyss.item.storm.client;

import jp.revati.skyabyss.item.storm.ThunderWingArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 雷翼の羽衣の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
// 元のキューブ版 (geo/armor/thunder_wing_armor.geo.json、光る部分は textures/armor/thunder_wing_armor_glowmask.png) は LowPolyArmor.enabled = false で出る
public class ThunderWingArmorRenderer extends LowPolyArmorRenderer<ThunderWingArmorItem> {
    public ThunderWingArmorRenderer() {
        super("thunder_wing");
    }
}
