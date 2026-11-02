package jp.revati.skyabyss.story.finale.gear;

import jp.revati.skyabyss.tower.gear.TowerArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;

// 終章の神器の防具 (docs/design/50 の 4.5.1。神器・必要 Lv 50、一式 4 部位)。鐘守の重鎧・霞渡りの装束・灯守の法衣。
// 作りは塔の防具と同じ (低ポリの描画・部位の数え方) で、由来だけが雲海 (燃料は心核の欠片、雲上の箱に入る)。4 部位の効果は DivineSecondEvents
public class DivineArmorItem extends TowerArmorItem {
    public DivineArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String set) {
        super(material, type, properties, set);
    }

    @Override
    public String dungeon() {
        return DivineGear.DUNGEON;
    }
}
