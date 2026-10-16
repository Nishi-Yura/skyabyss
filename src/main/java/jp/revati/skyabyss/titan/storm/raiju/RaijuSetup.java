package jp.revati.skyabyss.titan.storm.raiju;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 中ボス「雷獣」(12 の 5.2節) の登録: 雷獣と音。SkyAbyss のコンストラクタから register を呼ぶ。
// 共有の登録 (ModEntities、ModSounds) には手を入れず、ここにまとめる (潮殻の心臓と同じ形)
public final class RaijuSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 判定は 1.8 x 2.4 (模型は鼻先から尾の付け根まで約 4.5 ブロック。尾を入れると 7.6)。跳び移りを滑らかに見せるため毎 tick 送る
    public static final DeferredHolder<EntityType<?>, EntityType<Raiju>> RAIJU = ENTITIES.register("raiju",
            () -> EntityType.Builder.<Raiju>of(Raiju::new, MobCategory.MONSTER)
                    .sized(1.8f, 2.4f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":raiju"));

    // 技ごとに別の音 (tools/gen_raiju_sounds.py の独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = sound("raiju_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("raiju_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("raiju_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL_CHARGE = sound("raiju_rail_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL = sound("raiju_rail");
    public static final DeferredHolder<SoundEvent, SoundEvent> POUNCE = sound("raiju_pounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW = sound("raiju_claw");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROAR = sound("raiju_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> STUN = sound("raiju_stun");
    public static final DeferredHolder<SoundEvent, SoundEvent> GROUND = sound("raiju_ground");

    public static final String[] SOUND_NAMES = {"ambient", "hurt", "death", "rail_charge", "rail", "pounce", "claw", "roar", "stun", "ground"};

    private RaijuSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(RaijuSetup::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(RAIJU.get(), Raiju.createAttributes().build());
    }
}
