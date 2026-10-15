package jp.revati.skyabyss.quest;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// クエストの登録 (プレイヤーの記録)。SkyAbyss のコンストラクタから register を呼ぶ
public final class QuestSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    // クエストの記録。死んでも引き継ぎ、持ち主のクライアントにだけ同期する (ログ画面と左の追跡表示が読む)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<QuestLog>> LOG = ATTACHMENTS.register("quest_log",
            () -> AttachmentType.builder(QuestLog::new)
                    .serialize(QuestLog.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, QuestLog.STREAM_CODEC)
                    .build());

    private QuestSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
