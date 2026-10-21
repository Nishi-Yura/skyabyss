package jp.revati.skyabyss.town;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 町のクエスト (docs/design/35 の F5・F6) の登録: 限定の人 (town_guest) と、宿の噂の出どころ。
// 共有の登録ファイルには触らず、ここにまとめる。SkyAbyss のコンストラクタから register を一度だけ呼ぶ
public final class TownSetup {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);

    public static final String GUEST_ID = SkyAbyss.MODID + ":town_guest";

    // 町のクエストの間だけ現れる人 (誰かは TownGuestEntity が持つ。保存しない)
    public static final DeferredHolder<EntityType<?>, EntityType<TownGuestEntity>> GUEST = ENTITIES.register("town_guest",
            () -> EntityType.Builder.<TownGuestEntity>of(TownGuestEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(GUEST_ID));

    private TownSetup() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(TownSetup::onAttributes);
        modEventBus.addListener(TownSetup::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(GUEST.get(), NpcEntity.attributes(0.24).build());
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // 宿の女将の「噂を聞く」の出どころ (町のクエストの入口)
        jp.revati.skyabyss.facility.FacilityActions.addRumorSource(TownQuests::rumor);
    }
}
