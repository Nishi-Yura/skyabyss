package jp.revati.skyabyss.abyss.mobs;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 深淵の敵 A の登録 (docs/design/36 の 4・4.1): 9 種の型・スポーンエッグ・素材 3 つ・経験値。
// 素材の見た目と数字は tools/gen_abyss_mobs_a.py と AbyssMobStats。音は既存の音の別名を使う (新しい音の素材は作っていない)。
public final class AbyssMobsA {
    // 経験値は成長の枠 (深淵の内容の段の目安。34 の 4.5 の数字が決まるまでの仮)
    public static final int CONTENT_LEVEL = 33;

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);

    private static final Map<AbyssMobKind, DeferredHolder<EntityType<?>, EntityType<AbyssMob>>> TYPES = new EnumMap<>(AbyssMobKind.class);
    private static final Map<AbyssMobKind, DeferredItem<Item>> EGGS = new EnumMap<>(AbyssMobKind.class);

    // 素材 (仮。名前は lang で付ける)
    public static final DeferredItem<Item> BONE_CHIP = ITEMS.register("abyss_bone_chip", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SALT_CRYSTAL = ITEMS.register("abyss_salt_crystal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> STORM_COPPER = ITEMS.register("abyss_storm_copper",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    static {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            TYPES.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<AbyssMob>of((t, l) -> new AbyssMob(t, l, k), MobCategory.MONSTER)
                    .sized(k.width, k.height).eyeHeight(k.height * 0.8f).fireImmune().clientTrackingRange(10)
                    .build(SkyAbyss.MODID + ":" + k.id)));
            EGGS.put(k, ITEMS.register(k.id + "_spawn_egg", () -> new DeferredSpawnEggItem(TYPES.get(k), eggBase(k), eggSpot(k), new Item.Properties())));
        }
    }

    private AbyssMobsA() {
    }

    public static DeferredHolder<EntityType<?>, EntityType<AbyssMob>> type(AbyssMobKind k) {
        return TYPES.get(k);
    }

    public static Item egg(AbyssMobKind k) {
        return EGGS.get(k).get();
    }

    // 湧かせる (段と深度を決めてから。呼んだ側が addFreshEntity する)
    public static AbyssMob create(AbyssMobKind k, ServerLevel level, int stage, int depth) {
        AbyssMob m = TYPES.get(k).get().create(level);
        if (m != null) {
            m.setStage(stage, depth);
        }
        return m;
    }

    // 倒したときの素材 (層ごと)
    public static Item materialOf(AbyssMobKind k) {
        return switch (k.native_) {
            case 0 -> BONE_CHIP.get();
            case 1 -> SALT_CRYSTAL.get();
            default -> STORM_COPPER.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(AbyssMobsA::onAttributes);
        modEventBus.addListener(AbyssMobsA::onCreativeTab);
        modEventBus.addListener(AbyssMobsA::onCommonSetup);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            event.put(TYPES.get(k).get(), AbyssMob.createAttributes(k).build());
        }
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModItems.TAB.getKey())) {
            for (AbyssMobKind k : AbyssMobKind.values()) {
                event.accept(EGGS.get(k).get());
            }
            for (DeferredItem<Item> i : List.of(BONE_CHIP, SALT_CRYSTAL, STORM_COPPER)) {
                event.accept(i.get());
            }
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (AbyssMobKind k : AbyssMobKind.values()) {
                GrowthRewards.MOD_ENEMIES.put(SkyAbyss.MODID + ":" + k.id, new GrowthRewards.Source(k.xp, CONTENT_LEVEL));
            }
        });
    }

    // スポーンエッグの色 (地色・斑)
    private static int eggBase(AbyssMobKind k) {
        return switch (k.native_) {
            case 0 -> 0xD8CCAA;
            case 1 -> 0xE8ECEE;
            default -> 0x3A3F52;
        };
    }

    private static int eggSpot(AbyssMobKind k) {
        return switch (k.native_) {
            case 0 -> 0x4A3F30;
            case 1 -> 0x5E7A8C;
            default -> 0xB8F4FF;
        };
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }
}
