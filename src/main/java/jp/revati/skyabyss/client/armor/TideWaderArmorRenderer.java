package jp.revati.skyabyss.client.armor;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.guide.TideWaderArmorItem;

// 潮渡りの旅装 (二章の旅装) の 3D 描画。lowpoly/armor/tide_wader/<部位>.json と textures/lowpoly/armor/tide_wader/<部位>.png (+ _glowmask.png)。
// 入れ方は tools/install/install_ch2_gear_assets.py。階級はアンコモン = 無印のメッシュ
public class TideWaderArmorRenderer extends LowPolyArmorRenderer<TideWaderArmorItem> {
    public TideWaderArmorRenderer() {
        super("tide_wader");
    }
}
