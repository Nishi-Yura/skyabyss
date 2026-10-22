package jp.revati.skyabyss.mount;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// 乗り物 (docs/design/38) の登録: 乗り物、呼び笛、人ごとの持ち物。SkyAbyss のコンストラクタから register を一度だけ呼ぶ
public final class MountSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MountEntity>> MOUNT = ENTITIES.register("mount",
            () -> EntityType.Builder.<MountEntity>of(MountEntity::new, MobCategory.MISC)
                    .sized(1.4f, 1.6f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .noSummon()
                    .build(SkyAbyss.MODID + ":mount"));

    public static final DeferredItem<Item> WHISTLE = ITEMS.register("mount_whistle", () -> new MountWhistleItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MountData>> DATA = ATTACHMENTS.register("mount_data",
            () -> AttachmentType.serializable(MountData::new).copyOnDeath().build());

    private MountSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(MountSetup::onAttributes);
        modEventBus.addListener(MountSetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MOUNT.get(), MountEntity.attributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(WHISTLE.get());
        }
    }
}
