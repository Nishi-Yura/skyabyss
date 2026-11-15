package jp.revati.skyabyss.client.lowpoly;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

// 近・中・遠の 3 段の LOD を持つ巨神の GeoModel (岩鯨・潮殻・雷翼・余燼)。
// 段 (0 近 / 1 中 / 2 遠) は派生の lod() が決める。名前は <name> / <name>_mid / <name>_far。
// 骨だけの geo/entity/<名前>_lowpoly.geo.json と、点の模型の元の lowpoly/<名前>.json + textures/lowpoly/<名前>.png
// (tools/install/install_lowpoly_titans.py が 3 段ぶん入れる)
public abstract class LowPolyLodModel<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
    private static final String[] SUFFIX = {"", "_mid", "_far"};

    private final String name;
    private final ResourceLocation[] lowGeo = new ResourceLocation[3];
    private final ResourceLocation[] atlas = new ResourceLocation[3];

    protected LowPolyLodModel(String name) {
        super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
        this.name = name;
        for (int i = 0; i < 3; i++) {
            String n = name + SUFFIX[i];
            lowGeo[i] = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + n + "_lowpoly.geo.json");
            atlas[i] = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + n + ".png");
        }
    }

    // 0 近 / 1 中 / 2 遠
    protected abstract int lod(T animatable);

    // 今の段のメッシュの名前 (lowpoly/<名前>.json)
    public String meshName(T animatable) {
        return name + SUFFIX[lod(animatable)];
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        int l = lod(animatable);
        return lowGeo[l];
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return atlas[lod(animatable)];
    }
}
