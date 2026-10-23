package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 技 (スキル) の登録: 記録 (Data Attachment)、独自の光の粒、独自に合成した音。
// SkyAbyss のコンストラクタから register を一度だけ呼ぶ
public final class Skills {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // 技の記録。持ち主のクライアントにだけ同期する
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SkillData>> DATA = ATTACHMENTS.register("skills",
            () -> AttachmentType.serializable(SkillData::new)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, SkillData.STREAM_CODEC)
                    .build());

    // 碑文の青緑の小さな火花 (剣士の技、習得)
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RUNE_SPARK = PARTICLES.register("rune_spark",
            () -> new SimpleParticleType(false));
    // 空の淡い羽のような粒 (弓使いの技)
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SKY_MOTE = PARTICLES.register("sky_mote",
            () -> new SimpleParticleType(false));
    // 雷翼の心臓の技の羽 (羽の雨など。client/combat/AttackFx・StormFeatherParticle)。粒の登録がここにまとまっているので、ここに置く
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STORM_FEATHER = PARTICLES.register("storm_feather",
            () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_CLEAVE = sound("skill_cleave");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_RIPOSTE = sound("skill_riposte");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_LUNGE = sound("skill_lunge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_SWEEP = sound("skill_sweep");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_PIERCE = sound("skill_pierce");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_BACKSTEP = sound("skill_backstep");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_MARK = sound("skill_mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_RAIN = sound("skill_rain");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_LEARN = sound("skill_learn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_FAIL = sound("skill_fail");

    private Skills() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        PARTICLES.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
