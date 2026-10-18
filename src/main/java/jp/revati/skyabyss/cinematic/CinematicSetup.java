package jp.revati.skyabyss.cinematic;

import java.util.function.Supplier;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 場面の登録 (見た場面の記録)。SkyAbyss のコンストラクタから register を呼ぶ
public final class CinematicSetup {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final Supplier<AttachmentType<CinematicState>> STATE = ATTACHMENTS.register("cinematic",
            () -> AttachmentType.builder(CinematicState::new).serialize(CinematicState.CODEC).copyOnDeath().build());

    private CinematicSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        // 心臓の登場の演出 (HeartBoss が投げる) が始まったら、近くの人に心臓の場面を見せる
        NeoForge.EVENT_BUS.addListener(TitanCinematics::onHeartCinematic);
    }
}
