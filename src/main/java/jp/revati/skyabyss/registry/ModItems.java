package jp.revati.skyabyss.registry;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.BoneSpikeArrowItem;
import jp.revati.skyabyss.item.CaptainPikeItem;
import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.MaterialItem;
import jp.revati.skyabyss.item.ResonanceCharmItem;
import jp.revati.skyabyss.item.StoneWhaleArmorItem;
import jp.revati.skyabyss.item.WaterwayFangItem;
import jp.revati.skyabyss.item.WhaleboneGreatswordItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Mod独自のアイテムとクリエイティブタブ
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkyAbyss.MODID);

    // 天の鍵の欠片。巨神の背から一つずつ持ち帰り、すべて集めると雲の上への階段が開く
    public static final DeferredItem<Item> SKY_KEY_FRAGMENT = ITEMS.register("sky_key_fragment",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("item.skyabyss.sky_key_fragment.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }

                @Override
                public boolean isFoil(ItemStack stack) {
                    return true;
                }
            });

    // 鯨骨の大剣・岩鯨の甲殻・共鳴の護符・雲上の干し肉 (岩鯨の背の番兵長・心臓・番兵から低い確率で手に入る)
    public static final DeferredItem<Item> WHALEBONE_GREATSWORD = ITEMS.register("whalebone_greatsword",
            () -> new WhaleboneGreatswordItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> STONE_WHALE_HELMET = ITEMS.register("stone_whale_helmet",
            () -> new StoneWhaleArmorItem(ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> STONE_WHALE_CHESTPLATE = ITEMS.register("stone_whale_chestplate",
            () -> new StoneWhaleArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> STONE_WHALE_LEGGINGS = ITEMS.register("stone_whale_leggings",
            () -> new StoneWhaleArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> STONE_WHALE_BOOTS = ITEMS.register("stone_whale_boots",
            () -> new StoneWhaleArmorItem(ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<Item> RESONANCE_CHARM = ITEMS.register("resonance_charm",
            () -> new ResonanceCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> CLOUD_JERKY = ITEMS.register("cloud_jerky",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7f)
                    .effect(() -> new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0), 1f).build())));

    // 岩鯨の専用装備 (周回で集める)。伝説は個人抽選と「巨神の記憶」の天井で手に入る
    public static final DeferredItem<Item> CAPTAIN_PIKE = ITEMS.register("captain_pike",
            () -> new CaptainPikeItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> WATERWAY_FANG = ITEMS.register("waterway_fang",
            () -> new WaterwayFangItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> STONE_WHALE_PLATING = ITEMS.register("stone_whale_plating",
            () -> new GearItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.LEGENDARY,
                    "item.skyabyss.stone_whale_plating.desc", false));
    public static final DeferredItem<Item> MOSS_TALISMAN = ITEMS.register("moss_talisman",
            () -> new GearItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), GearTier.RARE,
                    "item.skyabyss.moss_talisman.desc", false));
    public static final DeferredItem<Item> BONE_SPIKE_ARROW = ITEMS.register("bone_spike_arrow",
            () -> new BoneSpikeArrowItem(new Item.Properties()));

    // 素材: 欠片と結晶は共鳴の炉で装備を鍛えるのに、共鳴の欠片は分解で、心核は神器 (のちの章) に使う
    public static final DeferredItem<Item> STONE_WHALE_SHARD = ITEMS.register("stone_whale_shard",
            () -> new MaterialItem(new Item.Properties(), 0xC9C2B0, "item.skyabyss.stone_whale_shard.desc", false));
    public static final DeferredItem<Item> RESONANCE_CRYSTAL = ITEMS.register("resonance_crystal",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0x9FE8FF, "item.skyabyss.resonance_crystal.desc", false));
    // 共鳴の欠片 (交換の通貨) は巨神ごと。交換は、その巨神の欠片で払う (ForgeRules.exchangeAffordable)。
    // 以前の共通の共鳴の欠片 (resonance_shard) は古い品として残し、どの巨神の交換にも使える (炉が手もちの欠片へ換えて払う)。新しくは落ちない
    public static final DeferredItem<Item> RESONANCE_SHARD = ITEMS.register("resonance_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xB89CFF, "item.skyabyss.resonance_shard.desc", false));
    public static final DeferredItem<Item> RESONANCE_SHARD_STONE_WHALE = ITEMS.register("resonance_shard_stone_whale",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xD9CBA6, "item.skyabyss.resonance_shard_stone_whale.desc", false));
    public static final DeferredItem<Item> RESONANCE_SHARD_TIDE_SHELL = ITEMS.register("resonance_shard_tide_shell",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0x7FE0D2, "item.skyabyss.resonance_shard_tide_shell.desc", false));
    public static final DeferredItem<Item> RESONANCE_SHARD_THUNDER_WING = ITEMS.register("resonance_shard_thunder_wing",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xFFD866, "item.skyabyss.resonance_shard_thunder_wing.desc", false));
    public static final DeferredItem<Item> RESONANCE_SHARD_EMBER = ITEMS.register("resonance_shard_ember",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xFF9A5A, "item.skyabyss.resonance_shard_ember.desc", false));
    public static final DeferredItem<Item> STONE_WHALE_CORE = ITEMS.register("stone_whale_core",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), 0x7FD6D0,
                    "item.skyabyss.stone_whale_core.desc", true));

    // 番兵たちのスポーンエッグ (クリエイティブでの確認用)
    public static final DeferredItem<Item> BONE_SENTINEL_EGG = ITEMS.register("bone_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_SENTINEL, 0xD8CDB2, 0x5A6070, new Item.Properties()));
    public static final DeferredItem<Item> RIB_ARCHER_EGG = ITEMS.register("rib_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.RIB_ARCHER, 0xD8CDB2, 0x5C7C3E, new Item.Properties()));
    public static final DeferredItem<Item> MOSS_CRAWLER_EGG = ITEMS.register("moss_crawler_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MOSS_CRAWLER, 0x46565C, 0x5C7C3E, new Item.Properties()));
    public static final DeferredItem<Item> ROCK_SHELL_BEETLE_EGG = ITEMS.register("rock_shell_beetle_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ROCK_SHELL_BEETLE, 0x7C8794, 0x8A5C34, new Item.Properties()));
    public static final DeferredItem<Item> SPINE_HARE_EGG = ITEMS.register("spine_hare_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SPINE_HARE, 0x6B6050, 0xD8CDB2, new Item.Properties()));
    public static final DeferredItem<Item> BONE_HAULER_EGG = ITEMS.register("bone_hauler_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_HAULER, 0x5C7C3E, 0xD8CDB2, new Item.Properties()));
    public static final DeferredItem<Item> ELDER_SENTRY_EGG = ITEMS.register("elder_sentry_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ELDER_SENTRY, 0xD8CDB2, 0x5C7C3E, new Item.Properties()));
    public static final DeferredItem<Item> WATERWAY_MAW_EGG = ITEMS.register("waterway_maw_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WATERWAY_MAW, 0x46565C, 0x7FD6D0, new Item.Properties()));
    public static final DeferredItem<Item> WHALEBONE_CAPTAIN_EGG = ITEMS.register("whalebone_captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WHALEBONE_CAPTAIN, 0xE2D8C0, 0x3C8CAA, new Item.Properties()));
    public static final DeferredItem<Item> STONE_WHALE_HEART_EGG = ITEMS.register("stone_whale_heart_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.STONE_WHALE_HEART, 0x96505A, 0x7FD6D0, new Item.Properties()));

    public static final DeferredItem<BlockItem> RESONANCE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_STONE);
    public static final DeferredItem<BlockItem> SEALED_GATE = ITEMS.registerSimpleBlockItem(ModBlocks.SEALED_GATE);
    public static final DeferredItem<BlockItem> CRUMBLING_BONE = ITEMS.registerSimpleBlockItem(ModBlocks.CRUMBLING_BONE);
    public static final DeferredItem<BlockItem> RESONANCE_FORGE = ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_FORGE);

    // 闇の水路の灯 (docs/design/23): 持ち運ぶ光る海のイソギンチャクと、灯の台・苗床・数え石
    public static final DeferredItem<Item> GLOW_ANEMONE = ITEMS.register("glow_anemone",
            () -> new jp.revati.skyabyss.titan.dungeon.GlowAnemoneItem(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> ANEMONE_PEDESTAL = ITEMS.registerSimpleBlockItem(ModBlocks.ANEMONE_PEDESTAL);
    public static final DeferredItem<BlockItem> ANEMONE_BED = ITEMS.registerSimpleBlockItem(ModBlocks.ANEMONE_BED);
    public static final DeferredItem<BlockItem> ANEMONE_TALLY = ITEMS.registerSimpleBlockItem(ModBlocks.ANEMONE_TALLY);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.skyabyss"))
            .icon(() -> SKY_KEY_FRAGMENT.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(SKY_KEY_FRAGMENT.get());
                output.accept(WHALEBONE_GREATSWORD.get());
                output.accept(STONE_WHALE_HELMET.get());
                output.accept(STONE_WHALE_CHESTPLATE.get());
                output.accept(STONE_WHALE_LEGGINGS.get());
                output.accept(STONE_WHALE_BOOTS.get());
                output.accept(RESONANCE_CHARM.get());
                output.accept(CLOUD_JERKY.get());
                output.accept(CAPTAIN_PIKE.get());
                output.accept(WATERWAY_FANG.get());
                output.accept(STONE_WHALE_PLATING.get());
                output.accept(MOSS_TALISMAN.get());
                output.accept(BONE_SPIKE_ARROW.get());
                output.accept(STONE_WHALE_SHARD.get());
                output.accept(RESONANCE_CRYSTAL.get());
                output.accept(RESONANCE_SHARD_STONE_WHALE.get());
                output.accept(RESONANCE_SHARD_TIDE_SHELL.get());
                output.accept(RESONANCE_SHARD_THUNDER_WING.get());
                output.accept(RESONANCE_SHARD_EMBER.get());
                output.accept(RESONANCE_SHARD.get());
                output.accept(STONE_WHALE_CORE.get());
                output.accept(RESONANCE_FORGE.get());
                output.accept(BONE_SENTINEL_EGG.get());
                output.accept(RIB_ARCHER_EGG.get());
                output.accept(Ch1BackEntities.BONE_MORTAR_EGG.get());
                output.accept(Ch1BackEntities.BONE_GULL_EGG.get());
                output.accept(Ch1BackEntities.ELDER_SENTINEL_EGG.get());
                output.accept(MOSS_CRAWLER_EGG.get());
                output.accept(ROCK_SHELL_BEETLE_EGG.get());
                output.accept(SPINE_HARE_EGG.get());
                output.accept(BONE_HAULER_EGG.get());
                output.accept(ELDER_SENTRY_EGG.get());
                output.accept(WATERWAY_MAW_EGG.get());
                output.accept(WHALEBONE_CAPTAIN_EGG.get());
                output.accept(STONE_WHALE_HEART_EGG.get());
                output.accept(RESONANCE_STONE.get());
                output.accept(SEALED_GATE.get());
                output.accept(CRUMBLING_BONE.get());
                output.accept(GLOW_ANEMONE.get());
                output.accept(ANEMONE_PEDESTAL.get());
                output.accept(ANEMONE_BED.get());
                output.accept(ANEMONE_TALLY.get());
            })
            .build());

    private ModItems() {
    }

    // 巨神の id (stone_whale / tide_shell / thunder_wing / ember) に合う共鳴の欠片。知らない巨神は岩鯨のもの
    public static Item resonanceShard(String titan) {
        return switch (titan) {
            case "tide_shell" -> RESONANCE_SHARD_TIDE_SHELL.get();
            case "thunder_wing" -> RESONANCE_SHARD_THUNDER_WING.get();
            case "ember" -> RESONANCE_SHARD_EMBER.get();
            default -> RESONANCE_SHARD_STONE_WHALE.get();
        };
    }

    // 共鳴の欠片か (巨神ごとのものと、古い共通のもの)
    public static boolean isResonanceShard(Item item) {
        return item == RESONANCE_SHARD.get() || item == RESONANCE_SHARD_STONE_WHALE.get() || item == RESONANCE_SHARD_TIDE_SHELL.get()
                || item == RESONANCE_SHARD_THUNDER_WING.get() || item == RESONANCE_SHARD_EMBER.get();
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
