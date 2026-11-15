package jp.revati.skyabyss.titan.ember.gear.client;

import jp.revati.skyabyss.titan.ember.gear.EmberArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 余燼の鎧の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
public class EmberArmorRenderer extends LowPolyArmorRenderer<EmberArmorItem> {
    public EmberArmorRenderer() {
        super("ember");
    }
}
