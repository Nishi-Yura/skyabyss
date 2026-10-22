package jp.revati.skyabyss.client.armor;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.guide.PeakCrossingArmorItem;

// 峰越えの旅装 (三章の旅装) の 3D 描画。lowpoly/armor/peak_crossing/<部位>.json と textures/lowpoly/armor/peak_crossing/<部位>.png (+ _glowmask.png)。
// 入れ方は tools/install/install_ch3_gear_assets.py。階級はアンコモン = 無印のメッシュ
public class PeakCrossingArmorRenderer extends LowPolyArmorRenderer<PeakCrossingArmorItem> {
    public PeakCrossingArmorRenderer() {
        super("peak_crossing");
    }
}
