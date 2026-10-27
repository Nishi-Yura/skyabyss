package jp.revati.skyabyss.item.outfit;

import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;

// 市の防具の 3D 描画。lowpoly/armor/<組>/<部位>.json と textures/lowpoly/armor/<組>/<部位>.png を、旅装と同じ共通部品で描く
public class OutfitArmorRenderer extends LowPolyArmorRenderer<OutfitArmorItem> {
    public OutfitArmorRenderer(String set) {
        super(set);
    }
}
