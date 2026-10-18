package jp.revati.skyabyss.titan.ember.gear.client;

import jp.revati.skyabyss.titan.ember.gear.EmberArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 余燼の鎧の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
// 元のキューブ版 (geo/armor/ember_armor.geo.json、光る部分は textures/armor/ember_armor_glowmask.png) は LowPolyArmor.enabled = false で出る
public class EmberArmorRenderer extends LowPolyArmorRenderer<EmberArmorItem> {
    public EmberArmorRenderer() {
        super("ember");
    }
}
