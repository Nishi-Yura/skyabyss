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
    public static final DeferredItem<Item> RESONANCE_SHARD = ITEMS.register("resonance_shard",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.RARE), 0xB89CFF, "item.skyabyss.resonance_shard.desc", false));
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
    public static final DeferredItem<Item> WATERWAY_MAW_EGG = ITEMS.register("waterway_maw_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WATERWAY_MAW, 0x46565C, 0x7FD6D0, new Item.Properties()));
    public static final DeferredItem<Item> WHALEBONE_CAPTAIN_EGG = ITEMS.register("whalebone_captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WHALEBONE_CAPTAIN, 0xE2D8C0, 0x3C8CAA, new Item.Properties()));
    public static final DeferredItem<Item> STONE_WHALE_HEART_EGG = ITEMS.register("stone_whale_heart_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.STONE_WHALE_HEART, 0x96505A, 0x7FD6D0, new Item.Properties()));

    public static final DeferredItem<BlockItem> RESONANCE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_STONE);
    public static final DeferredItem<BlockItem> SEALED_GATE = ITEMS.registerSimpleBlockItem(ModBlocks.SEALED_GATE);
    public static final DeferredItem<BlockItem> WARDED_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.WARDED_STONE);
    public static final DeferredItem<BlockItem> CRUMBLING_BONE = ITEMS.registerSimpleBlockItem(ModBlocks.CRUMBLING_BONE);
    public static final DeferredItem<BlockItem> RESONANCE_FORGE = ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_FORGE);

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
                output.accept(RESONANCE_SHARD.get());
                output.accept(STONE_WHALE_CORE.get());
                output.accept(RESONANCE_FORGE.get());
                output.accept(BONE_SENTINEL_EGG.get());
                output.accept(RIB_ARCHER_EGG.get());
                output.accept(MOSS_CRAWLER_EGG.get());
                output.accept(WATERWAY_MAW_EGG.get());
                output.accept(WHALEBONE_CAPTAIN_EGG.get());
                output.accept(STONE_WHALE_HEART_EGG.get());
                output.accept(RESONANCE_STONE.get());
                output.accept(SEALED_GATE.get());
                output.accept(WARDED_STONE.get());
                output.accept(CRUMBLING_BONE.get());
            })
            .build());

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
