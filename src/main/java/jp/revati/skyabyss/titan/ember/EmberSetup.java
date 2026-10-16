package jp.revati.skyabyss.titan.ember;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 第四の巨神「余燼の巨神」の骨組みの登録 (13 の 11.2節の担当 0)。SkyAbyss のコンストラクタから register を呼ぶ。
// ここに登録するのは骨組みの持ち物 (熱の音) と、起動時の背中の口 (Titans.bindBack) だけ。
// ほかの担当は自分の Setup (EmberTitanSetup・EmberBackSetup・EmberMobSetup・EmberSmithSetup・EmberHeartSetup・EmberGearSetup など) を作り、
// 名前は EmberIds から取り、SkyAbyss に一行ずつ足す (潮殻と同じ。ぶつかるのはその一行と、自己テストの一覧の一行だけ)
public final class EmberSetup {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> HEAT_WARN = sound(EmberIds.SOUND_HEAT_WARN);
    public static final DeferredHolder<SoundEvent, SoundEvent> SCORCH = sound(EmberIds.SOUND_SCORCH);

    private EmberSetup() {
    }

    // 音の登録の書き方 (各担当の Setup も同じ形で)
    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(EmberIds.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
        modEventBus.addListener(EmberSetup::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // 背中の口 (14 の 5.2節の約束 1): 乗る資格の見回りと、資格のない人のディメンション移動の取り消しが効く。
        // 地上へ戻す道は EmberTravel.leave (A が巨神の口の前へ戻す形に直す)
        event.enqueueWork(() -> Titans.bindBack(EmberIds.BACK, Titans.EMBER, EmberTravel::leave));
    }
}
