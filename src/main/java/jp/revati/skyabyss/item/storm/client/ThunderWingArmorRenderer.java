package jp.revati.skyabyss.item.storm.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.storm.ThunderWingArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 雷翼の羽衣の 3D 描画 (潮殻の鎧と同じ作り)。モデルは geo/armor/thunder_wing_armor.geo.json、
// 光る部分 (稲妻の筋) は textures/armor/thunder_wing_armor_glowmask.png。素材は tools/gen_storm_items.py
public class ThunderWingArmorRenderer extends GeoArmorRenderer<ThunderWingArmorItem> {
    public ThunderWingArmorRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static final class Model extends DefaultedGeoModel<ThunderWingArmorItem> {
        private Model() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "thunder_wing_armor"));
        }

        @Override
        protected String subtype() {
            return "armor";
        }
    }
}
