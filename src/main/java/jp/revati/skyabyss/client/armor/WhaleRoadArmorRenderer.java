package jp.revati.skyabyss.client.armor;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.guide.WhaleRoadArmorItem;

// 鯨道の旅装 (一章の旅装。案 A 殻肩) の 3D 描画。低ポリの共通部品 LowPolyArmorRenderer が、lowpoly/armor/whale_road/<部位>.json と
// textures/lowpoly/armor/whale_road/<部位>.png (+ 光る画素 _glowmask.png) を描く。階級はアンコモン = 無印のメッシュ
public class WhaleRoadArmorRenderer extends LowPolyArmorRenderer<WhaleRoadArmorItem> {
    public WhaleRoadArmorRenderer() {
        super("whale_road");
    }
}
