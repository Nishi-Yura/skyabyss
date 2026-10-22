package jp.revati.skyabyss.story.ch4.client;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.story.ch4.Ch4Gear;

// 灰歩きの旅装 (四章の旅装) の 3D 描画。lowpoly/armor/ash_walking/<部位>.json と textures/lowpoly/armor/ash_walking/<部位>.png (+ _glowmask.png)。
// 入れ方は tools/gen/ch4/gen_ch4_gear.py。階級はアンコモン = 無印のメッシュ
public class AshWalkingArmorRenderer extends LowPolyArmorRenderer<Ch4Gear.AshWalkingArmorItem> {
    public AshWalkingArmorRenderer() {
        super("ash_walking");
    }
}
