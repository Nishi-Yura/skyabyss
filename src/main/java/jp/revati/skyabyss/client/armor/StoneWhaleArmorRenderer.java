package jp.revati.skyabyss.client.armor;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.StoneWhaleArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 岩鯨の甲殻の 3D 描画。モデルは geo/armor/stone_whale_armor.geo.json、
// 青い発光部は textures/armor/stone_whale_armor_glowmask.png で光らせる
public class StoneWhaleArmorRenderer extends GeoArmorRenderer<StoneWhaleArmorItem> {
    public StoneWhaleArmorRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // geo/armor/, textures/armor/ を読む (DefaultedItemGeoModel は geo/item/ を読むため使わない)
    private static final class Model extends DefaultedGeoModel<StoneWhaleArmorItem> {
        private Model() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "stone_whale_armor"));
        }

        @Override
        protected String subtype() {
            return "armor";
        }
    }
}
