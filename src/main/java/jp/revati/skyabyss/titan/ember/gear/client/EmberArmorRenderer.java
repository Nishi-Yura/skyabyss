package jp.revati.skyabyss.titan.ember.gear.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.gear.EmberArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 余燼の鎧の 3D 描画 (潮殻の鎧と同じ作り)。モデルは geo/armor/ember_armor.geo.json、
// 光る溶岩の継ぎ目は textures/armor/ember_armor_glowmask.png。素材は tools/install_ember_armor.py
public class EmberArmorRenderer extends GeoArmorRenderer<EmberArmorItem> {
    public EmberArmorRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static final class Model extends DefaultedGeoModel<EmberArmorItem> {
        private Model() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ember_armor"));
        }

        @Override
        protected String subtype() {
            return "armor";
        }
    }
}
