package jp.revati.skyabyss.registry;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.tablet.TabletBlock;
import jp.revati.skyabyss.titan.dungeon.AnemoneBedBlock;
import jp.revati.skyabyss.titan.dungeon.AnemonePedestalBlock;
import jp.revati.skyabyss.titan.dungeon.AnemoneTallyBlock;
import jp.revati.skyabyss.titan.dungeon.CrumblingBoneBlock;
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
    // 封印の扉。共鳴石がそろうまで壊せない
    public static final DeferredBlock<SealedGateBlock> SEALED_GATE = BLOCKS.register("sealed_gate",
            () -> new SealedGateBlock(unbreakable(MapColor.COLOR_PURPLE).noOcclusion().dynamicShape()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(SealedGateBlock.OPEN) ? 0 : 3)));

    // 闇の水路の灯 (docs/design/23): 灯の台 (ともした姿はグループごとの上書き)・灯の苗床・入口の数え石
    public static final DeferredBlock<AnemonePedestalBlock> ANEMONE_PEDESTAL = BLOCKS.register("anemone_pedestal",
            () -> new AnemonePedestalBlock(unbreakable(MapColor.COLOR_CYAN).noOcclusion()
                    .lightLevel(state -> state.getValue(AnemonePedestalBlock.LIT) ? 15 : 5)));
    public static final DeferredBlock<AnemoneBedBlock> ANEMONE_BED = BLOCKS.register("anemone_bed",
            () -> new AnemoneBedBlock(unbreakable(MapColor.SAND).noOcclusion().sound(net.minecraft.world.level.block.SoundType.SAND).lightLevel(state -> 7)));
    public static final DeferredBlock<AnemoneTallyBlock> ANEMONE_TALLY = BLOCKS.register("anemone_tally",
            () -> new AnemoneTallyBlock(unbreakable(MapColor.COLOR_GRAY)
                    .lightLevel(state -> state.getValue(AnemoneTallyBlock.COUNT) == 0 ? 0 : 6 + 2 * state.getValue(AnemoneTallyBlock.COUNT))));

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

    // ---- 遺物ブロック (見た目だけ巨神ごと: titan = whale / tide / storm / ember。挙動はバニラの箱・焚き火・レバー等と同じ)
    // 遺物の箱 (宝のキャッシュ)。光る錠。OPEN で蓋が上がる
    public static final DeferredBlock<jp.revati.skyabyss.relic.RelicCacheBlock> RELIC_CACHE = BLOCKS.register("relic_cache",
            () -> new jp.revati.skyabyss.relic.RelicCacheBlock(unbreakable(MapColor.COLOR_GRAY).noOcclusion().lightLevel(state -> 5)));
    // 休み火 (休み場所)。灯ると明るい
    public static final DeferredBlock<jp.revati.skyabyss.relic.RestHearthBlock> REST_HEARTH = BLOCKS.register("rest_hearth",
            () -> new jp.revati.skyabyss.relic.RestHearthBlock(unbreakable(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(state -> state.getValue(jp.revati.skyabyss.relic.RestHearthBlock.LIT) ? 12 : 2)));
    // 遺物のレバー・ボタン・感圧板 (入れた/押した/踏んだ間は筋が光る)
    public static final DeferredBlock<jp.revati.skyabyss.relic.RelicLeverBlock> RELIC_LEVER = BLOCKS.register("relic_lever",
            () -> new jp.revati.skyabyss.relic.RelicLeverBlock(unbreakable(MapColor.COLOR_GRAY).noCollission()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.LeverBlock.POWERED) ? 5 : 1)));
    public static final DeferredBlock<jp.revati.skyabyss.relic.RelicButtonBlock> RELIC_BUTTON = BLOCKS.register("relic_button",
            () -> new jp.revati.skyabyss.relic.RelicButtonBlock(unbreakable(MapColor.COLOR_GRAY).noCollission()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.ButtonBlock.POWERED) ? 5 : 1)));
    public static final DeferredBlock<jp.revati.skyabyss.relic.RelicPlateBlock> RELIC_PLATE = BLOCKS.register("relic_plate",
            () -> new jp.revati.skyabyss.relic.RelicPlateBlock(unbreakable(MapColor.COLOR_GRAY).noCollission()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.PressurePlateBlock.POWERED) ? 5 : 1)));
    // 遺物の鐘。鳴る間は音の輪が光る
    public static final DeferredBlock<jp.revati.skyabyss.relic.RelicBellBlock> RELIC_BELL = BLOCKS.register("relic_bell",
            () -> new jp.revati.skyabyss.relic.RelicBellBlock(unbreakable(MapColor.COLOR_GRAY).noOcclusion()
                    .lightLevel(state -> state.getValue(jp.revati.skyabyss.relic.RelicBellBlock.RING) ? 8 : 2)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS);
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
