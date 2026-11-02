package jp.revati.skyabyss.tower.gear.client;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.tower.gear.TowerArmorItem;

// 塔の防具の 3D 描画。低ポリの共通部品 LowPolyArmorRenderer が lowpoly/armor/<一式>/<部位>_legend.json と絵を描く。
// 絵とメッシュは tools/install/install_tower_armor.py が入れる (形は tools/rough/lowpoly/armor/tower/)
public class TowerArmorRenderer extends LowPolyArmorRenderer<TowerArmorItem> {
    public TowerArmorRenderer(String set) {
        super(set);
    }
}
