package jp.revati.skyabyss.facility;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.guild.GuildBadge;
import jp.revati.skyabyss.npc.NpcEntity;
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

// 都の施設 (docs/design/35 の F2・F3) の登録: 係の人、鐘銭、預かり所の中身、店の記録、ギルドの印。
// 共有の登録ファイルには触らず、ここにまとめる。SkyAbyss のコンストラクタから register を一度だけ呼ぶ
public final class FacilitySetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyAbyss.MODID);

    public static final String ATTENDANT_ID = SkyAbyss.MODID + ":attendant";

    // 係の人 (役は AttendantEntity が持つ)
    public static final DeferredHolder<EntityType<?>, EntityType<AttendantEntity>> ATTENDANT = ENTITIES.register("attendant",
            () -> EntityType.Builder.<AttendantEntity>of(AttendantEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(ATTENDANT_ID));

    // 鐘銭 (都の専用の銭。35 の 4 章、00 の 224)。頼み・町のクエスト・敵の落とし物・店への売りで手に入る
    public static final DeferredItem<Item> BELL_COIN = ITEMS.register("bell_coin", () -> new BellCoinItem(new Item.Properties().stacksTo(99)));

    // 預かり所の中身 (人ごと。死んでも引き継ぐ。同期はしない = 画面を開いたときに箱の仕組みが送る)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StashData>> STASH = ATTACHMENTS.register("stash",
            () -> AttachmentType.serializable(StashData::new).copyOnDeath().build());

    // 訓練場の木偶の見えない的 (TrainingYard が柱にかぶせる。描かない)
    public static final DeferredHolder<EntityType<?>, EntityType<TrainingDummy>> TRAINING_DUMMY = ENTITIES.register("training_dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC)
                    .sized(1.05f, 3.02f)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .noSummon()
                    .build(SkyAbyss.MODID + ":training_dummy"));

    // 施設で一度だけのことの印 (地図の写し・醸造の手ほどき。死んでも引き継ぐ)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FacilityMarks>> MARKS = ATTACHMENTS.register("facility_marks",
            () -> AttachmentType.serializable(FacilityMarks::new).copyOnDeath().build());

    // 店の記録 (闇市の日ごとの買った数など。死んでも引き継ぐ)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ShopLedger>> LEDGER = ATTACHMENTS.register("shop_ledger",
            () -> AttachmentType.serializable(ShopLedger::new).copyOnDeath().build());

    // ギルドの印 (名札に出す。持ち主と、その人が見えている人に同期する。中身の正本は GuildData)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GuildBadge>> BADGE = ATTACHMENTS.register("guild_badge",
            () -> AttachmentType.builder(() -> GuildBadge.NONE)
                    .serialize(GuildBadge.CODEC)
                    .copyOnDeath()
                    .sync((holder, to) -> true, GuildBadge.STREAM_CODEC)
                    .build());

    private FacilitySetup() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(FacilitySetup::onAttributes);
        modEventBus.addListener(FacilitySetup::onCreativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ATTENDANT.get(), NpcEntity.attributes(0.18).build());
        event.put(TRAINING_DUMMY.get(), TrainingDummy.attributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            event.accept(BELL_COIN.get());
        }
    }
}
