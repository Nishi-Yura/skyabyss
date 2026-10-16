package jp.revati.skyabyss.titan.tide.gear.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.tide.gear.TideShellArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 潮殻の鎧の 3D 描画 (岩鯨の甲殻と同じ作り)。モデルは geo/armor/tide_shell_armor.geo.json、
// 光る部分は textures/armor/tide_shell_armor_glowmask.png。素材は tools/install_tide_armor.py
public class TideShellArmorRenderer extends GeoArmorRenderer<TideShellArmorItem> {
    public TideShellArmorRenderer() {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private static final class Model extends DefaultedGeoModel<TideShellArmorItem> {
        private Model() {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_shell_armor"));
        }

        @Override
        protected String subtype() {
            return "armor";
        }
    }
}
