package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.titan.dungeon.CrumblingBoneBlock;
import jp.revati.skyabyss.titan.dungeon.ResonanceStoneBlock;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自のブロック
public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);

    // 叩くと歌い出す古代の石。3つそろうと封印が解ける
    public static final DeferredBlock<ResonanceStoneBlock> RESONANCE_STONE = BLOCKS.register("resonance_stone",
            () -> new ResonanceStoneBlock(unbreakable(MapColor.COLOR_GRAY)
                    .lightLevel(state -> state.getValue(ResonanceStoneBlock.ACTIVE) ? 11 : 0)));
    // 封印の扉。共鳴石がそろうまで壊せない
    public static final DeferredBlock<SealedGateBlock> SEALED_GATE = BLOCKS.register("sealed_gate",
            () -> new SealedGateBlock(unbreakable(MapColor.COLOR_PURPLE).lightLevel(state -> 3)));
    // 宝物庫を囲む、守りの刻まれた石。掘って封印を迂回できないようにする
    public static final DeferredBlock<Block> WARDED_STONE = BLOCKS.register("warded_stone",
            () -> new Block(unbreakable(MapColor.DEEPSLATE)));

    // 乗ると崩れ、しばらくすると戻る骨の足場
    public static final DeferredBlock<CrumblingBoneBlock> CRUMBLING_BONE = BLOCKS.register("crumbling_bone",
            () -> new CrumblingBoneBlock(unbreakable(MapColor.SAND).sound(SoundType.BONE_BLOCK).noOcclusion()));

    // 共鳴の炉: 装備を鍛え (+1〜+10)、要らない装備を分解する。誰でも作って置ける
    public static final DeferredBlock<ResonanceForgeBlock> RESONANCE_FORGE = BLOCKS.register("resonance_forge",
            () -> new ResonanceForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.5f, 1200f)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(state -> 9)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
