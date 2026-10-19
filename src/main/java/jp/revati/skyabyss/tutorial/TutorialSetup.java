package jp.revati.skyabyss.tutorial;

import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 初回の手引きの登録 (人ごとの記録)。SkyAbyss のコンストラクタから register を呼ぶ。文は assets/skyabyss_tutorial/lang
public final class TutorialSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final Supplier<AttachmentType<TutorialData>> STATE = ATTACHMENTS.register("tutorial",
            () -> AttachmentType.builder(TutorialData::new).serialize(TutorialData.CODEC).copyOnDeath().build());

    private TutorialSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
