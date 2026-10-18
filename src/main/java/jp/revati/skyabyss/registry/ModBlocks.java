package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.tablet.TabletBlock;
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

    // 乗ると崩れ、しばらくすると戻る骨の足場
    public static final DeferredBlock<CrumblingBoneBlock> CRUMBLING_BONE = BLOCKS.register("crumbling_bone",
            () -> new CrumblingBoneBlock(unbreakable(MapColor.SAND).sound(SoundType.BONE_BLOCK).noOcclusion()));

    // 共鳴の炉: 装備を鍛え (+1〜+10)、要らない装備を分解する。誰でも作って置ける
    public static final DeferredBlock<ResonanceForgeBlock> RESONANCE_FORGE = BLOCKS.register("resonance_forge",
            () -> new ResonanceForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.5f, 1200f)
                    .requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).lightLevel(state -> 9)));

    // 碑: 看板の代わりの刻まれた石。目を向けるか右クリックで、字幕に文が出る。かすかに光る
    public static final DeferredBlock<TabletBlock> TABLET = BLOCKS.register("tablet",
            () -> new TabletBlock(unbreakable(MapColor.COLOR_GRAY).noOcclusion().noCollission().lightLevel(state -> 5)));

    // 墓: 倒れた人の持ち物の入口。苔むした石の積み石で、かすかに光る。壊せず (クリエイティブは別)、消えない。docs/design/11 の 6 章
    public static final DeferredBlock<jp.revati.skyabyss.grave.GraveBlock> GRAVE = BLOCKS.register("grave",
            () -> new jp.revati.skyabyss.grave.GraveBlock(unbreakable(MapColor.COLOR_GREEN).sound(SoundType.MOSS).lightLevel(state -> 5)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
