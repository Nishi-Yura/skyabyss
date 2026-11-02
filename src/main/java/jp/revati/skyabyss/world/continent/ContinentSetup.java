package jp.revati.skyabyss.world.continent;

import com.mojang.serialization.MapCodec;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// 「天と淵の大陸」のワールドの種類 (data/skyabyss/worldgen/world_preset/continent.json) が使う、地形と土地の型の登録
public final class ContinentSetup {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, SkyAbyss.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, SkyAbyss.MODID);

    static {
        GENERATORS.register("continent", () -> ContinentChunkGenerator.CODEC);
        BIOME_SOURCES.register("continent", () -> ContinentBiomes.CODEC);
    }

    private ContinentSetup() {
    }

    public static void register(IEventBus bus) {
        GENERATORS.register(bus);
        BIOME_SOURCES.register(bus);
    }
}
