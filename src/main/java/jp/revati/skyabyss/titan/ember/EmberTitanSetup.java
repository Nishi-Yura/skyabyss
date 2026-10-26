package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 余燼の巨神の地上の登録 (13 の 11.2節の担当 A): 巨神本体・熱の口・温石・地上の音。SkyAbyss のコンストラクタから register を呼ぶ。
// 名前はすべて EmberIds から取る (ID の約束)
public final class EmberTitanSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    // 当たり判定は小さく、見た目は描画側で 8 倍にする (岩鯨・潮殻と同じ)。位置はモデルの底 (geo の y=0)
    public static final DeferredHolder<EntityType<?>, EntityType<EmberTitanEntity>> EMBER_TITAN = ENTITIES.register(EmberIds.EMBER_TITAN,
            () -> EntityType.Builder.<EmberTitanEntity>of(EmberTitanEntity::new, MobCategory.MISC)
                    .sized(8.0f, 8.0f)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":" + EmberIds.EMBER_TITAN));

    // 熱の口: 舌の上の見えない判定 (幅 5・高さ 4)。巨神が眠っているあいだだけ出し、保存しない
    public static final DeferredHolder<EntityType<?>, EntityType<EmberMouthEntity>> EMBER_MOUTH = ENTITIES.register(EmberIds.EMBER_MOUTH,
            () -> EntityType.Builder.<EmberMouthEntity>of(EmberMouthEntity::new, MobCategory.MISC)
                    .sized((float) EmberTitanEntity.MOUTH_WIDTH, (float) EmberTitanEntity.MOUTH_HEIGHT)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .fireImmune()
                    .noSave()
                    .build(SkyAbyss.MODID + ":" + EmberIds.EMBER_MOUTH));

    public static final DeferredItem<Item> WARM_STONE = ITEMS.register(EmberIds.WARM_STONE,
            () -> new WarmStoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));

    public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = sound(EmberIds.SOUND_RUMBLE);
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_IN = sound(EmberIds.SOUND_BREATH_IN);
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH_OUT = sound(EmberIds.SOUND_BREATH_OUT);
    public static final DeferredHolder<SoundEvent, SoundEvent> SWALLOW = sound(EmberIds.SOUND_SWALLOW);
    public static final DeferredHolder<SoundEvent, SoundEvent> WALL_SCRAPE = sound(EmberIds.SOUND_WALL_SCRAPE);
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_BEAT = sound(EmberIds.SOUND_FAR_BEAT);
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_TURN = sound(EmberIds.SOUND_WORLD_TURN);

    private EmberTitanSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(EmberTitanSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            event.accept(WARM_STONE.get());
        }
    }
}
