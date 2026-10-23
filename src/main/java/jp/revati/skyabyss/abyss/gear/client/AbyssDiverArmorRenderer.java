package jp.revati.skyabyss.abyss.gear.client;

import jp.revati.skyabyss.abyss.gear.AbyssDiverArmorItem;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 淵潜りの鎧の 3D 描画。低ポリの共通部品 LowPolyArmorRenderer が lowpoly/armor/abyss_diver/<部位>[_rare].json と
// textures/lowpoly/armor/abyss_diver/<部位>_rare[_plus5|_plus10].png (+ 光る画素 _glowmask.png) を描く。
// 絵とメッシュは tools/install/install_abyss_gear.py が入れる (ラフ 3 案は docs/review/roughs_gear_gaps/)
public class AbyssDiverArmorRenderer extends LowPolyArmorRenderer<AbyssDiverArmorItem> {
    public AbyssDiverArmorRenderer() {
        super("abyss_diver");
    }
}
