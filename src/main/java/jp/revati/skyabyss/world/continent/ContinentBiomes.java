package jp.revati.skyabyss.world.continent;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

// 大陸の土地 (生物群系) の割り当て。番号は tools/gen/world/gen_continent.py の BIOME_NAMES の並びで、
// 実際の生物群系は world_preset/continent.json の biomes に同じ並びで書く
public final class ContinentBiomes extends BiomeSource {
    public static final int DEEP_OCEAN = 0;
    public static final int OCEAN = 1;
    public static final int INLAND_SEA = 2;
    public static final int BEACH = 3;
    public static final int PLAINS = 4;
    public static final int FOREST = 5;
    public static final int MEADOW = 6;
    public static final int WINDSWEPT_HILLS = 7;
    public static final int TAIGA = 8;
    public static final int SAVANNA = 9;
    public static final int GRAVELLY_HILLS = 10;
    public static final int STONY_PEAKS = 11;
    public static final int SNOWY_SLOPES = 12;
    public static final int BADLANDS = 13;
    public static final int ERODED_BADLANDS = 14;

    public static final MapCodec<ContinentBiomes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.listOf().fieldOf("biomes").forGetter(s -> s.biomes)).apply(i, ContinentBiomes::new));

    private final List<Holder<Biome>> biomes;

    public ContinentBiomes(List<Holder<Biome>> biomes) {
        this.biomes = List.copyOf(biomes);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int b = ContinentMap.get().biome((x << 2) + 2, (z << 2) + 2);
        return biomes.get(Math.min(b, biomes.size() - 1));
    }
}
