package jp.revati.skyabyss.item.wild.client;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.item.wild.WildArmorItem;

// 野の装備の防具の 3D 描画。低ポリの共通部品 LowPolyArmorRenderer が lowpoly/armor/<一式>/<部位>[_rare|_legend].json と
// textures/lowpoly/armor/<一式>/<部位>[_rare|_legend][_plus5|_plus10].png (+ 光る画素 _glowmask.png) を描く。
// 絵とメッシュは tools/install/install_wild_gear.py が入れる (形は tools/rough/lowpoly/armor/wild/)
public class WildArmorRenderer extends LowPolyArmorRenderer<WildArmorItem> {
    public WildArmorRenderer(String set) {
        super(set);
    }
}
