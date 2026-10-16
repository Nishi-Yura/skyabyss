package jp.revati.skyabyss.rpg;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 成長 (RPG) の登録をまとめる。SkyAbyss のコンストラクタから register を一度だけ呼ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Rpg {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, SkyAbyss.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    // プレイヤーの成長の記録。持ち主のクライアントにだけ同期する
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GrowthData>> GROWTH = ATTACHMENTS.register("growth",
            () -> AttachmentType.serializable(GrowthData::new)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, GrowthData.STREAM_CODEC)
                    .build());

    // 最大マナ。精神で増える。今は値を持つだけで、マナを使うスキルは後で作る
    public static final DeferredHolder<Attribute, Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute.skyabyss.max_mana", GrowthRules.BASE_MANA, 0, 1024).setSyncable(true));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = SOUNDS.register("growth_level_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "growth_level_up")));

    private Rpg() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        ATTRIBUTES.register(modEventBus);
        SOUNDS.register(modEventBus);
    }

    @SubscribeEvent
    static void onAttributes(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.PLAYER, MAX_MANA)) {
            event.add(EntityType.PLAYER, MAX_MANA);
        }
    }
}
