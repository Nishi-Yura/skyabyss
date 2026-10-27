package jp.revati.skyabyss.story.ch4;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.ch4.mob.AshDragger;
import jp.revati.skyabyss.story.ch4.mob.AshHusk;
import jp.revati.skyabyss.story.ch4.mob.CinderMite;
import jp.revati.skyabyss.story.ch4.mob.ElderThrall;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 四章の地上 (docs/design/34 の C4、21 の 6.1) の登録: 敵 4 種 (熾り蟲・灰被りの屑・灰ひきずり・古い灰被り)・スポーンエッグ・品 5 つ・音。
// 敵と品の ID は skyabyss の名前空間。音の出来事だけは skyabyss_ch4 の名前空間 (assets/skyabyss_ch4/sounds.json。ほかの作業の sounds.json と分ける)。
// 素材と見た目は tools/gen/ch4/gen_ch4_ground_mobs.py、文は tools/gen/ch4/gen_ch4_ground.py。SkyAbyss のコンストラクタから register を呼ぶ
public final class Ch4Setup {
    public static final String SOUND_NS = "skyabyss_ch4";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SOUND_NS);

    // 四章の推奨レベル (34 の 4.5。Lv 24 → 30。成長の経験値の減り方に使う)
    public static final int CONTENT_LEVEL = 27;

    // ---------------------------------------------------------------- 敵
    public static final DeferredHolder<EntityType<?>, EntityType<CinderMite>> CINDER_MITE = ENTITIES.register("cinder_mite",
            () -> EntityType.Builder.of(CinderMite::new, MobCategory.MONSTER).sized(0.65f, 0.45f).eyeHeight(0.3f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":cinder_mite"));
    public static final DeferredHolder<EntityType<?>, EntityType<AshHusk>> ASH_HUSK = ENTITIES.register("ash_husk",
            () -> EntityType.Builder.of(AshHusk::new, MobCategory.MONSTER).sized(0.7f, 1.95f).eyeHeight(1.7f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":ash_husk"));
    public static final DeferredHolder<EntityType<?>, EntityType<AshDragger>> ASH_DRAGGER = ENTITIES.register("ash_dragger",
            () -> EntityType.Builder.of(AshDragger::new, MobCategory.MONSTER).sized(0.8f, 0.55f).eyeHeight(0.4f).fireImmune()
                    .clientTrackingRange(8).build(SkyAbyss.MODID + ":ash_dragger"));
    public static final DeferredHolder<EntityType<?>, EntityType<ElderThrall>> ELDER_THRALL = ENTITIES.register("elder_thrall",
            () -> EntityType.Builder.of(ElderThrall::new, MobCategory.MONSTER).sized(0.95f, 2.65f).eyeHeight(2.3f).fireImmune()
                    .clientTrackingRange(10).build(SkyAbyss.MODID + ":elder_thrall"));

    public static final DeferredItem<Item> CINDER_MITE_EGG = ITEMS.register("cinder_mite_spawn_egg",
            () -> new DeferredSpawnEggItem(CINDER_MITE, 0x241C1E, 0xFF9228, new Item.Properties()));
    public static final DeferredItem<Item> ASH_HUSK_EGG = ITEMS.register("ash_husk_spawn_egg",
            () -> new DeferredSpawnEggItem(ASH_HUSK, 0x8A8480, 0x3C2420, new Item.Properties()));
    public static final DeferredItem<Item> ASH_DRAGGER_EGG = ITEMS.register("ash_dragger_spawn_egg",
            () -> new DeferredSpawnEggItem(ASH_DRAGGER, 0x42424C, 0xB89E70, new Item.Properties()));
    public static final DeferredItem<Item> ELDER_THRALL_EGG = ITEMS.register("elder_thrall_spawn_egg",
            () -> new DeferredSpawnEggItem(ELDER_THRALL, 0x5A5654, 0xD2551A, new Item.Properties()));

    // ---------------------------------------------------------------- 品 (21 の 6.1。煤鉄・灰麻・灰の護り糸・煤の砥石・灰の冷やし水)
    public static final DeferredItem<Item> SOOT_IRON = ITEMS.register("soot_iron", () -> new Described(new Item.Properties()));
    public static final DeferredItem<Item> ASH_HEMP = ITEMS.register("ash_hemp", () -> new Described(new Item.Properties()));
    public static final DeferredItem<Item> ASH_WARD_THREAD = ITEMS.register("ash_ward_thread",
            () -> new Described(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> SOOT_WHETSTONE = ITEMS.register("soot_whetstone", () -> new SootWhetstoneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> ASH_COOLER = ITEMS.register("ash_cooler", () -> new AshCoolerItem(new Item.Properties().stacksTo(16)));

    // ---------------------------------------------------------------- 音 (既存の音の別名。assets/skyabyss_ch4/sounds.json)
    public static final DeferredHolder<SoundEvent, SoundEvent> MITE_AMBIENT = sound("cinder_mite_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> MITE_DEATH = sound("cinder_mite_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGGER_CURL = sound("ash_dragger_curl");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAGGER_ROLL = sound("ash_dragger_roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_CRACK = sound("elder_thrall_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_BURST = sound("elder_thrall_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_WAVE = sound("elder_thrall_wave");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELDER_RAKE = sound("elder_thrall_rake");

    // 成長の経験値 (章の経験値の枠には数えない小さな分): 熾り蟲 2、灰被りの屑 10、灰ひきずり 3、古い灰被り 80
    public static final int XP_MITE = 2;
    public static final int XP_HUSK = 10;
    public static final int XP_DRAGGER = 3;
    public static final int XP_ELDER = 80;

    // この章から鍛冶場・薬師で誂えられる品 (旅の覚書の章の品。自己テストが確かめる)
    public static final List<String> MADE = List.of("ash_walking_helmet", "ash_walking_chestplate", "ash_walking_leggings", "ash_walking_boots",
            "soot_whetstone", "ash_cooler", "ash_baked_bread");

    private Ch4Setup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SOUND_NS, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUNDS.register(modEventBus);
        Ch4Gear.register(modEventBus);
        modEventBus.addListener(Ch4Setup::onAttributes);
        modEventBus.addListener(Ch4Setup::onCreativeTab);
        modEventBus.addListener(Ch4Setup::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CINDER_MITE.get(), CinderMite.createAttributes().build());
        event.put(ASH_HUSK.get(), AshHusk.createHuskAttributes().build());
        event.put(ASH_DRAGGER.get(), AshDragger.createAttributes().build());
        event.put(ELDER_THRALL.get(), ElderThrall.createElderAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.LEGACY_TAB)) {
            for (DeferredItem<Item> i : List.of(CINDER_MITE_EGG, ASH_HUSK_EGG, ASH_DRAGGER_EGG, ELDER_THRALL_EGG, SOOT_IRON, ASH_HEMP, ASH_WARD_THREAD,
                    SOOT_WHETSTONE, ASH_COOLER)) {
                event.accept(i.get());
            }
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":cinder_mite", new GrowthRewards.Source(XP_MITE, CONTENT_LEVEL));
            GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":ash_husk", new GrowthRewards.Source(XP_HUSK, CONTENT_LEVEL));
            GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":ash_dragger", new GrowthRewards.Source(XP_DRAGGER, CONTENT_LEVEL));
            GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":elder_thrall", new GrowthRewards.Source(XP_ELDER, CONTENT_LEVEL));
        });
    }

    // 説明の一行つきの素材 (item.skyabyss.<id>.desc)
    public static class Described extends Item {
        public Described(Properties properties) {
            super(properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
