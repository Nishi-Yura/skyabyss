package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 余燼の背の三つの試練 (灰の道・鍛えの間・火守り。13 の 4 章、11.2節の担当 C1・C2・C3) の登録: ブロック、品、音。
// 名前はすべて EmberIds から取る。SkyAbyss のコンストラクタから register を呼ぶ。
// 試練のブロックはどれも壊せない・置けない (背中の決まり)。品は背中の外へ持ち出せない (EmberTrials が見回る)
public final class EmberTrialSetup {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // ---------------------------------------------------------------- ブロック (C1)
    // 冷え殻: 溶岩に灰をまくとできる黒い殻。12 秒で崩れて溶岩に戻る (AshPath)。段階 0 黒・1 割れ目が赤く光る・2 沈みかけ
    public static final DeferredBlock<CooledCrustBlock> COOLED_CRUST = BLOCKS.register(EmberIds.COOLED_CRUST,
            () -> new CooledCrustBlock(props(MapColor.COLOR_BLACK).sound(SoundType.BASALT)
                    .lightLevel(s -> CooledCrustBlock.LIGHT[s.getValue(CooledCrustBlock.STAGE)])));
    public static final DeferredBlock<AshUrnStandBlock> ASH_URN_STAND = BLOCKS.register(EmberIds.ASH_URN_STAND,
            () -> new AshUrnStandBlock(props(MapColor.COLOR_GRAY).sound(SoundType.BASALT).lightLevel(s -> 4).noOcclusion()));

    // ---------------------------------------------------------------- ブロック (C2)
    public static final DeferredBlock<ForgeBlocks.AshForgeBlock> ASH_FORGE = BLOCKS.register(EmberIds.ASH_FORGE,
            () -> new ForgeBlocks.AshForgeBlock(props(MapColor.COLOR_ORANGE).sound(SoundType.BASALT)
                    .lightLevel(s -> ForgeBlocks.AshForgeBlock.LIGHT[s.getValue(ForgeBlocks.AshForgeBlock.STAGE)])));
    public static final DeferredBlock<ForgeBlocks.BellowsBlock> FORGE_BELLOWS = BLOCKS.register(EmberIds.FORGE_BELLOWS,
            () -> new ForgeBlocks.BellowsBlock(props(MapColor.COLOR_BROWN).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<ForgeBlocks.AshAnvilBlock> ASH_ANVIL = BLOCKS.register(EmberIds.ASH_ANVIL,
            () -> new ForgeBlocks.AshAnvilBlock(props(MapColor.METAL).sound(SoundType.ANVIL).noOcclusion()));
    public static final DeferredBlock<ForgeBlocks.QuenchTroughBlock> QUENCH_TROUGH = BLOCKS.register(EmberIds.QUENCH_TROUGH,
            () -> new ForgeBlocks.QuenchTroughBlock(props(MapColor.COLOR_BLUE).sound(SoundType.DEEPSLATE_TILES).noOcclusion()));

    // ---------------------------------------------------------------- ブロック (C3)
    public static final DeferredBlock<HearthBlock> EMBER_HEARTH = BLOCKS.register(EmberIds.EMBER_HEARTH,
            () -> new HearthBlock(props(MapColor.COLOR_ORANGE).sound(SoundType.BASALT)
                    .lightLevel(s -> HearthBlock.LIGHT[s.getValue(HearthBlock.LEVEL)]).noOcclusion()));
    public static final DeferredBlock<HearthVents.VentBlock> HEAT_VENT = BLOCKS.register(EmberIds.HEAT_VENT,
            () -> new HearthVents.VentBlock(props(MapColor.COLOR_BLACK).sound(SoundType.BASALT)
                    .lightLevel(s -> HearthVents.VentBlock.LIGHT[s.getValue(HearthVents.VentBlock.PHASE)])));

    // ブロックの品 (クリエイティブのタブとテスト用。背中では置けない)
    public static final DeferredItem<BlockItem> COOLED_CRUST_ITEM = ITEMS.registerSimpleBlockItem(COOLED_CRUST);
    public static final DeferredItem<BlockItem> ASH_URN_STAND_ITEM = ITEMS.registerSimpleBlockItem(ASH_URN_STAND);
    public static final DeferredItem<BlockItem> ASH_FORGE_ITEM = ITEMS.registerSimpleBlockItem(ASH_FORGE);
    public static final DeferredItem<BlockItem> FORGE_BELLOWS_ITEM = ITEMS.registerSimpleBlockItem(FORGE_BELLOWS);
    public static final DeferredItem<BlockItem> ASH_ANVIL_ITEM = ITEMS.registerSimpleBlockItem(ASH_ANVIL);
    public static final DeferredItem<BlockItem> QUENCH_TROUGH_ITEM = ITEMS.registerSimpleBlockItem(QUENCH_TROUGH);
    public static final DeferredItem<BlockItem> EMBER_HEARTH_ITEM = ITEMS.registerSimpleBlockItem(EMBER_HEARTH);
    public static final DeferredItem<BlockItem> HEAT_VENT_ITEM = ITEMS.registerSimpleBlockItem(HEAT_VENT);

    // ---------------------------------------------------------------- 試練の品 (背中の外へ持ち出せない。預からない)
    public static final DeferredItem<AshUrnItem> ASH_URN = ITEMS.register(EmberIds.ASH_URN,
            () -> new AshUrnItem(new Item.Properties().stacksTo(1).durability(AshPath.URN_CHARGES).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<FireKeyItem> FIRE_KEY_HOT = ITEMS.register(EmberIds.FIRE_KEY_HOT,
            () -> new FireKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), true));
    public static final DeferredItem<FireKeyItem> FIRE_KEY = ITEMS.register(EmberIds.FIRE_KEY,
            () -> new FireKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), false));
    public static final DeferredItem<EmberItem> LIVE_COAL = ITEMS.register(EmberIds.LIVE_COAL,
            () -> new EmberItem(new Item.Properties().stacksTo(16)));

    // ---------------------------------------------------------------- 音 (tools/gen/sound/gen_ember_trial_sounds.py。独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> ASH_SCATTER = sound(EmberIds.SOUND_ASH_SCATTER);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUST_CRACK = sound(EmberIds.SOUND_CRUST_CRACK);
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUST_SINK = sound(EmberIds.SOUND_CRUST_SINK);
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGULF = sound(EmberIds.SOUND_ENGULF);
    public static final DeferredHolder<SoundEvent, SoundEvent> BELLOWS = sound(EmberIds.SOUND_BELLOWS);
    public static final DeferredHolder<SoundEvent, SoundEvent> ANVIL_STRIKE = sound(EmberIds.SOUND_ANVIL_STRIKE);
    public static final DeferredHolder<SoundEvent, SoundEvent> ANVIL_TING = sound(EmberIds.SOUND_ANVIL_TING);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOOM_CRACK = sound(EmberIds.SOUND_BLOOM_CRACK);
    public static final DeferredHolder<SoundEvent, SoundEvent> QUENCH = sound(EmberIds.SOUND_QUENCH);
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_KINDLE = sound(EmberIds.SOUND_DOOR_KINDLE);
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_HISS = sound(EmberIds.SOUND_VENT_HISS);
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_BURST = sound(EmberIds.SOUND_VENT_BURST);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTH_FEED = sound(EmberIds.SOUND_HEARTH_FEED);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTH_DOUSE = sound(EmberIds.SOUND_HEARTH_DOUSE);

    private EmberTrialSetup() {
    }

    private static BlockBehaviour.Properties props(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0f, 3600000.0f).noLootTable();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(EmberTrialSetup::onCreativeTab);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(EmberTrialSetup::hook));
    }

    // 部屋の中身の建て方とリセットを B の受け口 (EmberBackRooms) に渡す。蛾の誘いに火床を足す
    private static void hook() {
        EmberBackRooms.register(EmberIds.WEST, AshPathRoom::build);
        EmberBackRooms.onReset(EmberIds.WEST, AshPath::reset);
        EmberBackRooms.register(EmberIds.EAST, ForgeRoom::build);
        EmberBackRooms.onReset(EmberIds.EAST, Forge::reset);
        EmberBackRooms.register(EmberIds.SOUTH, HearthRoom::build);
        EmberBackRooms.onReset(EmberIds.SOUTH, Hearth::reset);
        Hearth.registerLure();
        Forge.registerOverlay();
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(ASH_URN.get());
            event.accept(FIRE_KEY_HOT.get());
            event.accept(FIRE_KEY.get());
            event.accept(LIVE_COAL.get());
        }
    }
}
