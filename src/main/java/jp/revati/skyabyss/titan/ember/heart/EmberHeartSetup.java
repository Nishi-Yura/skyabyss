package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 余燼の心臓 (第四の巨神の最終ボス) の登録: 心臓、灰の礫、音 (13 の 11.2節の担当 F)。SkyAbyss のコンストラクタから register を呼ぶ。
// 名前はすべて EmberIds から取る (ID の約束)。共有の登録 (ModEntities、ModSounds) と、骨組み (EmberSetup) には手を入れず、ここにまとめる
public final class EmberHeartSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 判定は 3 x 4.2。模型は描画で 2 倍 (殻の幅は約 3 ブロック。火の粉は判定の外まで上る)
    public static final DeferredHolder<EntityType<?>, EntityType<EmberHeart>> EMBER_HEART = ENTITIES.register(EmberIds.EMBER_HEART,
            () -> EntityType.Builder.<EmberHeart>of(EmberHeart::new, MobCategory.MONSTER)
                    .sized(3.0f, 4.2f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":" + EmberIds.EMBER_HEART));
    // 灰の礫 (段階 2): 人を追う遅い礫。叩けば消える
    public static final DeferredHolder<EntityType<?>, EntityType<EmberAshPellet>> ASH_PELLET = ENTITIES.register(EmberIds.ASH_PELLET,
            () -> EntityType.Builder.<EmberAshPellet>of(EmberAshPellet::new, MobCategory.MISC)
                    .sized(0.7f, 0.7f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":" + EmberIds.ASH_PELLET));

    public static final DeferredHolder<SoundEvent, SoundEvent> BEAT = sound(EmberIds.SOUND_HEART_BEAT);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = sound(EmberIds.SOUND_HEART_ROAR);
    public static final DeferredHolder<SoundEvent, SoundEvent> BREATH = sound(EmberIds.SOUND_HEART_BREATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> PULSE = sound(EmberIds.SOUND_HEART_PULSE);
    public static final DeferredHolder<SoundEvent, SoundEvent> ORB = sound(EmberIds.SOUND_HEART_ORB);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOR = sound(EmberIds.SOUND_HEART_FLOOR);
    public static final DeferredHolder<SoundEvent, SoundEvent> GALE = sound(EmberIds.SOUND_HEART_GALE);
    public static final DeferredHolder<SoundEvent, SoundEvent> PELLET = sound(EmberIds.SOUND_HEART_PELLET);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHILL = sound(EmberIds.SOUND_HEART_CHILL);
    public static final DeferredHolder<SoundEvent, SoundEvent> EMBERS = sound(EmberIds.SOUND_HEART_EMBERS);
    public static final DeferredHolder<SoundEvent, SoundEvent> FADING = sound(EmberIds.SOUND_HEART_FADING);
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO = sound(EmberIds.SOUND_HEART_ECHO);
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIL = sound(EmberIds.SOUND_HEART_VEIL);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound(EmberIds.SOUND_HEART_DEATH);

    // 成長の経験値 (13 の 6.6節)。GrowthRewards.MOD_ENEMIES に足す (rpg の表には一行だけ書く)。
    // 入れ子のクラスにしたのは、GrowthRewards の初期化で EmberHeartSetup (登録) を読み込まないため (EmberMobSetup.Xp と同じ)
    public static final class Xp {
        public static final int HEART = 650;
        public static final int HEART_FIRST_BONUS = 450;
        public static final int LEVEL = 30;

        private Xp() {
        }

        public static void put(java.util.Map<String, GrowthRewards.Source> table) {
            table.put(EmberIds.id(EmberIds.EMBER_HEART).toString(), new GrowthRewards.Source(HEART, LEVEL));
        }
    }

    private EmberHeartSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(EmberHeartSetup::attributes);
        modEventBus.addListener(EmberHeartSetup::commonSetup);
    }

    // 熾の底を体の builder と試練のリセットの受け口 (EmberBackRooms) に渡す
    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(EmberHeartBattle::registerHooks);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(EMBER_HEART.get(), EmberHeart.createAttributes().build());
    }
}
