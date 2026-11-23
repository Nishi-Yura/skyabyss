package jp.revati.skyabyss.titan.tide.gear.client;

import jp.revati.skyabyss.titan.tide.gear.TideShellArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 潮殻の鎧の 3D 描画。低ポリ (案 C 骨組み) は LowPolyArmorRenderer が描く。
public class TideShellArmorRenderer extends LowPolyArmorRenderer<TideShellArmorItem> {
    public TideShellArmorRenderer() {
        super("tide_shell");
    }
}
