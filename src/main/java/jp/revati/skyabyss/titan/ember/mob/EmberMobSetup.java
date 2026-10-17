package jp.revati.skyabyss.titan.ember.mob;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 余燼の背の敵 (灰被り・火の粉蛾・燠虫) の登録: エンティティ、スポーンエッグ、音 (13 の 11.2節の担当 D)。
// 名前はすべて EmberIds から取る。落とす素材 (余燼の欠片・火の粉の粉・燠の殻・熾火の角杯) と鎧は担当 G が登録し、
// EmberMobLoot は ID で引く (登録されていなければ落とさない)。SkyAbyss のコンストラクタから register を呼ぶ
public final class EmberMobSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 余燼の推奨レベル (13 の 1.3節。成長の経験値の減り方に使う)
    public static final int CONTENT_LEVEL = 30;

    // ---------------------------------------------------------------- エンティティ
    public static final DeferredHolder<EntityType<?>, EntityType<AshThrall>> ASH_THRALL = ENTITIES.register(EmberIds.ASH_THRALL,
            () -> EntityType.Builder.of(AshThrall::new, MobCategory.MONSTER).sized(0.8f, 2.3f).eyeHeight(2.0f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":" + EmberIds.ASH_THRALL));
    public static final DeferredHolder<EntityType<?>, EntityType<EmberMoth>> EMBER_MOTH = ENTITIES.register(EmberIds.EMBER_MOTH,
            () -> EntityType.Builder.of(EmberMoth::new, MobCategory.MONSTER).sized(0.7f, 0.6f).eyeHeight(0.5f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":" + EmberIds.EMBER_MOTH));
    public static final DeferredHolder<EntityType<?>, EntityType<CinderRoller>> CINDER_ROLLER = ENTITIES.register(EmberIds.CINDER_ROLLER,
            () -> EntityType.Builder.of(CinderRoller::new, MobCategory.MONSTER).sized(0.85f, 0.75f).eyeHeight(0.4f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":" + EmberIds.CINDER_ROLLER));

    // テスト用のスポーンエッグ (Mod のタブの末尾)
    public static final DeferredItem<Item> ASH_THRALL_EGG = ITEMS.register(EmberIds.ASH_THRALL_EGG,
            () -> new DeferredSpawnEggItem(ASH_THRALL, 0x827C76, 0xD2551A, new Item.Properties()));
    public static final DeferredItem<Item> EMBER_MOTH_EGG = ITEMS.register(EmberIds.EMBER_MOTH_EGG,
            () -> new DeferredSpawnEggItem(EMBER_MOTH, 0x5F5955, 0xF5A623, new Item.Properties()));
    public static final DeferredItem<Item> CINDER_ROLLER_EGG = ITEMS.register(EmberIds.CINDER_ROLLER_EGG,
            () -> new DeferredSpawnEggItem(CINDER_ROLLER, 0x34303B, 0xF58A2A, new Item.Properties()));

    // ---------------------------------------------------------------- 音 (tools/gen_ember_mob_sounds.py。独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_AMBIENT = sound(EmberIds.SOUND_THRALL_AMBIENT);
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_HURT = sound(EmberIds.SOUND_THRALL_HURT);
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_DEATH = sound(EmberIds.SOUND_THRALL_DEATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_SHELL_BREAK = sound(EmberIds.SOUND_THRALL_SHELL_BREAK);
    public static final DeferredHolder<SoundEvent, SoundEvent> THRALL_IGNITE = sound(EmberIds.SOUND_THRALL_IGNITE);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_FLUTTER = sound(EmberIds.SOUND_MOTH_FLUTTER);
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTH_DEATH = sound(EmberIds.SOUND_MOTH_DEATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLER_CURL = sound(EmberIds.SOUND_ROLLER_CURL);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLER_ROLL = sound(EmberIds.SOUND_ROLLER_ROLL);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLER_BONK = sound(EmberIds.SOUND_ROLLER_BONK);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLER_HURT = sound(EmberIds.SOUND_ROLLER_HURT);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLER_DEATH = sound(EmberIds.SOUND_ROLLER_DEATH);

    // 成長の経験値 (13 の 6.6節)。GrowthRewards.MOD_ENEMIES に足す (rpg の表には一行だけ書く)。
    // 入れ子のクラスにしたのは、GrowthRewards の初期化で EmberMobSetup (登録) を読み込まないため
    public static final class Xp {
        public static final int THRALL = 26;
        public static final int MOTH = 4;
        public static final int ROLLER = 16;

        private Xp() {
        }

        public static void put(java.util.Map<String, jp.revati.skyabyss.rpg.GrowthRewards.Source> table) {
            table.put(EmberIds.id(EmberIds.ASH_THRALL).toString(), new jp.revati.skyabyss.rpg.GrowthRewards.Source(THRALL, CONTENT_LEVEL));
            table.put(EmberIds.id(EmberIds.EMBER_MOTH).toString(), new jp.revati.skyabyss.rpg.GrowthRewards.Source(MOTH, CONTENT_LEVEL));
            table.put(EmberIds.id(EmberIds.CINDER_ROLLER).toString(), new jp.revati.skyabyss.rpg.GrowthRewards.Source(ROLLER, CONTENT_LEVEL));
        }
    }

    private EmberMobSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(EmberMobSetup::onAttributes);
        modEventBus.addListener(EmberMobSetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ASH_THRALL.get(), AshThrall.createAttributes().build());
        event.put(EMBER_MOTH.get(), EmberMoth.createAttributes().build());
        event.put(CINDER_ROLLER.get(), CinderRoller.createAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(ASH_THRALL_EGG.get());
            event.accept(EMBER_MOTH_EGG.get());
            event.accept(CINDER_ROLLER_EGG.get());
        }
    }
}
