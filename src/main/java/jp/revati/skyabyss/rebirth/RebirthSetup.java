package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 転生の見せ方の登録 (同期する人ごとの記録・順位の石・飾り板・設定)。SkyAbyss のコンストラクタから register を一度呼ぶ。文は assets/skyabyss_rebirth/lang
public final class RebirthSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);

    // 持ち主と周りの人みんなへ同期 (名札・背中の飾り・墓・一覧が読む)。死んでも残る
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RebirthData>> DATA = ATTACHMENTS.register("rebirth",
            () -> AttachmentType.builder(() -> RebirthData.EMPTY)
                    .serialize(RebirthData.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> true, RebirthData.STREAM_CODEC)
                    .build());

    public static final DeferredBlock<RebirthStoneBlock> STONE = BLOCKS.register("rebirth_stone",
            () -> new RebirthStoneBlock(unbreakable(MapColor.COLOR_CYAN).noOcclusion().lightLevel(s -> 7)));
    public static final DeferredBlock<RecordBoardBlock> BOARD = BLOCKS.register("record_board",
            () -> new RecordBoardBlock(unbreakable(MapColor.COLOR_GRAY).noOcclusion().lightLevel(s -> s.getValue(RecordBoardBlock.FRESH) ? 13 : 3)));

    private RebirthSetup() {
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable().sound(SoundType.DEEPSLATE_BRICKS)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    public static void register(IEventBus modEventBus, ModContainer container) {
        ATTACHMENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        container.registerConfig(ModConfig.Type.CLIENT, RebirthConfig.CLIENT_SPEC, "skyabyss-rebirth-client.toml");
        container.registerConfig(ModConfig.Type.SERVER, RebirthConfig.SERVER_SPEC, "skyabyss-rebirth-server.toml");
    }
}
