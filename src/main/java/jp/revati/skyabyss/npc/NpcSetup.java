package jp.revati.skyabyss.npc;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// NPC の登録 (エンティティ、プレイヤーとの記録)。共有の ModEntities は触らず、ここでまとめて登録する。
// SkyAbyss のコンストラクタから register を呼ぶ
public final class NpcSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final String STORYTELLER_ID = SkyAbyss.MODID + ":storyteller";
    public static final String WHALE_CHASER_ID = SkyAbyss.MODID + ":whale_chaser";
    public static final String ARTISAN_ID = SkyAbyss.MODID + ":artisan";

    // 語り部: 村の鐘のそばにいる年寄り。空の影と村の言い伝えを語る (家のまわり 8 ブロックを歩く)
    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> STORYTELLER = ENTITIES.register("storyteller",
            () -> EntityType.Builder.<NpcEntity>of((type, level) -> new NpcEntity(type, level, 8), MobCategory.MISC)
                    .sized(0.6f, 1.6f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(STORYTELLER_ID));
    // 鯨追い: 岩鯨を追って旅する人。村の外れで火を焚いている (火のまわり 4 ブロック)
    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> WHALE_CHASER = ENTITIES.register("whale_chaser",
            () -> EntityType.Builder.<NpcEntity>of((type, level) -> new NpcEntity(type, level, 4), MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(WHALE_CHASER_ID));

    // 旅の職人 (P4): 荷車の傍らの道に荷を広げている。章ごとに装いが変わる (NpcEntity.variant = 章)。荷のまわり 3 ブロック
    public static final DeferredHolder<EntityType<?>, EntityType<NpcEntity>> ARTISAN = ENTITIES.register("artisan",
            () -> EntityType.Builder.<NpcEntity>of((type, level) -> new NpcEntity(type, level, 3), MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(ARTISAN_ID));

    // プレイヤーと NPC の記録 (好感度・会話の印)。死んでも引き継ぎ、持ち主にだけ同期する
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcBonds>> BONDS = ATTACHMENTS.register("npc_bonds",
            () -> AttachmentType.builder(NpcBonds::new)
                    .serialize(NpcBonds.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> holder == to, NpcBonds.STREAM_CODEC)
                    .build());

    private NpcSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(NpcSetup::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(STORYTELLER.get(), NpcEntity.attributes(0.2).build());
        event.put(WHALE_CHASER.get(), NpcEntity.attributes(0.25).build());
        event.put(ARTISAN.get(), NpcEntity.attributes(0.22).build());
    }
}
