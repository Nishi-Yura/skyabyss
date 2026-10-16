package jp.revati.skyabyss.titan.ember.smith;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 中ボス「灰の鍛え手」(13 の 5.2節、11.2節の担当 E) の登録: エンティティと音、部屋の建て方・リセットの受け口。
// 名前はすべて EmberIds から取る。SkyAbyss のコンストラクタから register を呼ぶ
public final class EmberSmithSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 推奨レベルと成長の経験値 (13 の 6.6節: 220、初回 +100、推奨 Lv 30)
    public static final int CONTENT_LEVEL = 30;
    public static final int GROWTH_XP = 220;
    public static final int FIRST_BONUS = 100;

    // 判定は 1.7 x 4.5 (背丈 4.5 ブロック)。技の動きを滑らかに見せるため毎 tick 送る
    public static final DeferredHolder<EntityType<?>, EntityType<AshSmith>> ASH_SMITH = ENTITIES.register(EmberIds.ASH_SMITH,
            () -> EntityType.Builder.<AshSmith>of(AshSmith::new, MobCategory.MONSTER)
                    .sized(1.7f, 4.5f)
                    .eyeHeight(3.7f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build(SkyAbyss.MODID + ":" + EmberIds.ASH_SMITH));

    // 技ごとに別の音 (tools/gen_ember_smith_sounds.py の独自合成)
    public static final DeferredHolder<SoundEvent, SoundEvent> SLAM = sound(EmberIds.SOUND_SMITH_SLAM);
    public static final DeferredHolder<SoundEvent, SoundEvent> INHALE = sound(EmberIds.SOUND_SMITH_INHALE);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST = sound(EmberIds.SOUND_SMITH_BLAST);
    public static final DeferredHolder<SoundEvent, SoundEvent> SPARKS = sound(EmberIds.SOUND_SMITH_SPARKS);
    public static final DeferredHolder<SoundEvent, SoundEvent> RAGE = sound(EmberIds.SOUND_SMITH_RAGE);
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound(EmberIds.SOUND_SMITH_HURT);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound(EmberIds.SOUND_SMITH_DEATH);

    public static final String[] SOUND_NAMES = {EmberIds.SOUND_SMITH_SLAM, EmberIds.SOUND_SMITH_INHALE, EmberIds.SOUND_SMITH_BLAST,
            EmberIds.SOUND_SMITH_SPARKS, EmberIds.SOUND_SMITH_RAGE, EmberIds.SOUND_SMITH_HURT, EmberIds.SOUND_SMITH_DEATH};

    private EmberSmithSetup() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        SOUNDS.register(modEventBus);
        modEventBus.addListener(EmberSmithSetup::attributes);
        modEventBus.addListener(EmberSmithSetup::commonSetup);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ASH_SMITH.get(), AshSmith.createAttributes().build());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // 体を建てるとき (B) と試練のリセットで呼ばれる (13 の 19.2節)
        event.enqueueWork(() -> {
            EmberBackRooms.register("smith", AshSmithArena::build);
            EmberBackRooms.onReset("smith", (level, sleeping) -> AshSmithBattle.reset(level));
            // 鍛えの間の扉が開いたら (C2)、鍛え手の戦いの受け付けを始める
            jp.revati.skyabyss.titan.ember.back.trial.Forge.onDoorOpened(AshSmithBattle::onForgeDoorOpened);
        });
    }
}
