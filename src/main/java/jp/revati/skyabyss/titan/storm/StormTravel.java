package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// 地上と「嵐の上」(雷翼の背、専用のディメンション storm_back) を行き来させる (12 の 2.4〜2.5節)。
public final class StormTravel {
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));

    private StormTravel() {
    }
}
