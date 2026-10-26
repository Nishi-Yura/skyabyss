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
    // レイドの部屋「骨玉運び」の骨玉 (docs/design/32 の 7 章)。部屋の中だけの物で、持つと遅くなり跳べない
    public static final DeferredItem<Item> RAID_BONE_ORB = ITEMS.register("raid_bone_orb",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> RESONANCE_CHARM = ITEMS.register("resonance_charm",
            () -> new ResonanceCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> CLOUD_JERKY = ITEMS.register("cloud_jerky",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7f)
                    .effect(() -> new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0), 1f).build())));

    // 岩鯨の専用装備 (周回で集める)。専用の逸品は個人抽選と「巨神の記憶」の天井で手に入る
    public static final DeferredItem<Item> CAPTAIN_PIKE = ITEMS.register("captain_pike",
            () -> new CaptainPikeItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> WATERWAY_FANG = ITEMS.register("waterway_fang",
            () -> new WaterwayFangItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<Item> STONE_WHALE_PLATING = ITEMS.register("stone_whale_plating",
            () -> new GearItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(), GearTier.HEROIC,
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
    // 歌片 (順路の鍵。docs/design/32 の 3.2): 巨神の祠で一つ使うと、その巨神の順路へ呼ばれる。巨神ごとに一種類
    public static final DeferredItem<Item> SONG_SHARD_STONE_WHALE = ITEMS.register("song_shard_stone_whale",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.EPIC), 0xE8DDB8, "item.skyabyss.song_shard_stone_whale.desc", true));
    public static final DeferredItem<Item> SONG_SHARD_TIDE_SHELL = ITEMS.register("song_shard_tide_shell",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.EPIC), 0x9FF0E4, "item.skyabyss.song_shard_tide_shell.desc", true));
    public static final DeferredItem<Item> SONG_SHARD_THUNDER_WING = ITEMS.register("song_shard_thunder_wing",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.EPIC), 0xFFE58A, "item.skyabyss.song_shard_thunder_wing.desc", true));
    public static final DeferredItem<Item> SONG_SHARD_EMBER = ITEMS.register("song_shard_ember",
            () -> new MaterialItem(new Item.Properties().rarity(Rarity.EPIC), 0xFFB27A, "item.skyabyss.song_shard_ember.desc", true));
    public static final DeferredItem<Item> STONE_WHALE_CORE = ITEMS.register("stone_whale_core",
            () -> new MaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), 0x7FD6D0,
                    "item.skyabyss.stone_whale_core.desc", true));

    // 装備の自由度 (docs/design/31 の A02・A06・A07): 属性の欠片 5 種、アスペクトの芯、技の修飾ルーン。使うのは共鳴の炉の「刻み」
    public static final DeferredItem<Item> SOCKET_SHARD_STONE = ITEMS.register("socket_shard_stone",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard(jp.revati.skyabyss.item.extra.Element.STONE));
    public static final DeferredItem<Item> SOCKET_SHARD_WIND = ITEMS.register("socket_shard_wind",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard(jp.revati.skyabyss.item.extra.Element.WIND));
    public static final DeferredItem<Item> SOCKET_SHARD_TIDE = ITEMS.register("socket_shard_tide",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard(jp.revati.skyabyss.item.extra.Element.TIDE));
    public static final DeferredItem<Item> SOCKET_SHARD_ASH = ITEMS.register("socket_shard_ash",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard(jp.revati.skyabyss.item.extra.Element.ASH));
    public static final DeferredItem<Item> SOCKET_SHARD_BONE = ITEMS.register("socket_shard_bone",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.ElementShard(jp.revati.skyabyss.item.extra.Element.BONE));
    public static final DeferredItem<Item> ASPECT_CORE = ITEMS.register("aspect_core",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.AspectCore());
    public static final DeferredItem<Item> SKILL_RUNE = ITEMS.register("skill_rune",
            () -> new jp.revati.skyabyss.item.extra.GearFreedomItems.SkillRune());

    public static DeferredItem<Item> socketShard(jp.revati.skyabyss.item.extra.Element e) {
        return switch (e) {
            case STONE -> SOCKET_SHARD_STONE;
            case WIND -> SOCKET_SHARD_WIND;
            case TIDE -> SOCKET_SHARD_TIDE;
            case ASH -> SOCKET_SHARD_ASH;
            case BONE -> SOCKET_SHARD_BONE;
        };
    }

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
    // 二章の地上の敵 (P12)
    public static final DeferredItem<Item> TIDEPOOL_CLAM_EGG = ITEMS.register("tidepool_clam_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.TIDEPOOL_CLAM, 0xE48070, 0xE0DCE8, new Item.Properties()));
    public static final DeferredItem<Item> SAND_BURROWER_EGG = ITEMS.register("sand_burrower_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SAND_BURROWER, 0xBCA478, 0x408E86, new Item.Properties()));
    public static final DeferredItem<Item> DRIFT_HERMIT_EGG = ITEMS.register("drift_hermit_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DRIFT_HERMIT, 0x6C5C4C, 0xE48070, new Item.Properties()));
    public static final DeferredItem<Item> SHORE_WARDEN_EGG = ITEMS.register("shore_warden_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SHORE_WARDEN, 0x3A6470, 0xF0F0EC, new Item.Properties()));
    public static final DeferredItem<Item> BANDIT_EGG = ITEMS.register("bandit_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIT, 0x5A4632, 0x8C2E22, new Item.Properties()));
    public static final DeferredItem<Item> BANDIT_ARCHER_EGG = ITEMS.register("bandit_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIT_ARCHER, 0x6B7040, 0x5A4632, new Item.Properties()));
    public static final DeferredItem<Item> BANDIT_CHIEF_EGG = ITEMS.register("bandit_chief_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANDIT_CHIEF, 0x4A3A2C, 0xB0302A, new Item.Properties()));
    public static final DeferredItem<Item> BONE_FOOTMAN_EGG = ITEMS.register("bone_footman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_FOOTMAN, 0xD8CDB2, 0x7A5A3A, new Item.Properties()));
    public static final DeferredItem<Item> BONE_BOWMAN_EGG = ITEMS.register("bone_bowman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_BOWMAN, 0xD8CDB2, 0x4A5A3A, new Item.Properties()));
    public static final DeferredItem<Item> BONE_STANDARD_EGG = ITEMS.register("bone_standard_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BONE_STANDARD, 0xD8CDB2, 0x2E3E6A, new Item.Properties()));
    public static final DeferredItem<Item> GRAIN_RAT_EGG = ITEMS.register("grain_rat_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GRAIN_RAT, 0x8A6A40, 0xD8B470, new Item.Properties()));
    public static final DeferredItem<Item> STRAY_WISP_EGG = ITEMS.register("stray_wisp_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.STRAY_WISP, 0x9ACAEE, 0x2A2E3A, new Item.Properties()));
    public static final DeferredItem<Item> WHALEBONE_CAPTAIN_EGG = ITEMS.register("whalebone_captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.WHALEBONE_CAPTAIN, 0xE2D8C0, 0x3C8CAA, new Item.Properties()));
    public static final DeferredItem<Item> STONE_WHALE_HEART_EGG = ITEMS.register("stone_whale_heart_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.STONE_WHALE_HEART, 0x96505A, 0x7FD6D0, new Item.Properties()));

    public static final DeferredItem<BlockItem> SEALED_GATE = ITEMS.registerSimpleBlockItem(ModBlocks.SEALED_GATE);
    public static final DeferredItem<BlockItem> CRUMBLING_BONE = ITEMS.registerSimpleBlockItem(ModBlocks.CRUMBLING_BONE);
    public static final DeferredItem<BlockItem> RESONANCE_FORGE = ITEMS.registerSimpleBlockItem(ModBlocks.RESONANCE_FORGE);

    // 光る海のイソギンチャク (持つと、まわりをうっすら照らす灯。店で売る)
    public static final DeferredItem<Item> GLOW_ANEMONE = ITEMS.register("glow_anemone",
            () -> new jp.revati.skyabyss.titan.dungeon.GlowAnemoneItem(new Item.Properties().stacksTo(3).rarity(Rarity.UNCOMMON)));

    // 遺物ブロック (巨神ごとの様式は設置側が決める。持ち物のは鯨の様式)
    public static final DeferredItem<BlockItem> RELIC_CACHE = ITEMS.registerSimpleBlockItem(ModBlocks.RELIC_CACHE);
    public static final DeferredItem<BlockItem> REST_HEARTH = ITEMS.registerSimpleBlockItem(ModBlocks.REST_HEARTH);
    public static final DeferredItem<BlockItem> RELIC_LEVER = ITEMS.registerSimpleBlockItem(ModBlocks.RELIC_LEVER);
    public static final DeferredItem<BlockItem> RELIC_BUTTON = ITEMS.registerSimpleBlockItem(ModBlocks.RELIC_BUTTON);
    public static final DeferredItem<BlockItem> RELIC_PLATE = ITEMS.registerSimpleBlockItem(ModBlocks.RELIC_PLATE);
    public static final DeferredItem<BlockItem> RELIC_BELL = ITEMS.registerSimpleBlockItem(ModBlocks.RELIC_BELL);

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
                output.accept(SONG_SHARD_STONE_WHALE.get());
                output.accept(SONG_SHARD_TIDE_SHELL.get());
                output.accept(SONG_SHARD_THUNDER_WING.get());
                output.accept(SONG_SHARD_EMBER.get());
                for (jp.revati.skyabyss.item.extra.Element e : jp.revati.skyabyss.item.extra.Element.values()) {
                    output.accept(socketShard(e).get());
                }
                for (jp.revati.skyabyss.rpg.skill.Rune r : jp.revati.skyabyss.rpg.skill.Rune.values()) {
                    if (r.legendary()) {
                        output.accept(jp.revati.skyabyss.item.extra.GearFreedom.core(r));
                    }
                }
                for (jp.revati.skyabyss.rpg.skill.Skill s : jp.revati.skyabyss.item.extra.RuneData.skills()) {
                    output.accept(jp.revati.skyabyss.item.extra.GearFreedomItems.rune(new jp.revati.skyabyss.item.extra.RuneData(s.id, false, 1200)));
                }
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
                output.accept(TIDEPOOL_CLAM_EGG.get());
                output.accept(SAND_BURROWER_EGG.get());
                output.accept(DRIFT_HERMIT_EGG.get());
                output.accept(SHORE_WARDEN_EGG.get());
                output.accept(BANDIT_EGG.get());
                output.accept(BANDIT_ARCHER_EGG.get());
                output.accept(BANDIT_CHIEF_EGG.get());
                output.accept(BONE_FOOTMAN_EGG.get());
                output.accept(BONE_BOWMAN_EGG.get());
                output.accept(BONE_STANDARD_EGG.get());
                output.accept(GRAIN_RAT_EGG.get());
                output.accept(STRAY_WISP_EGG.get());
                output.accept(WHALEBONE_CAPTAIN_EGG.get());
                output.accept(STONE_WHALE_HEART_EGG.get());
                output.accept(SEALED_GATE.get());
                output.accept(CRUMBLING_BONE.get());
                output.accept(GLOW_ANEMONE.get());
            })
            .build());

    private ModItems() {
    }

    // 巨神の id に合う歌片 (順路の鍵)。知らない巨神は岩鯨のもの
    public static Item songShard(String titan) {
        return switch (titan) {
            case "tide_shell" -> SONG_SHARD_TIDE_SHELL.get();
            case "thunder_wing" -> SONG_SHARD_THUNDER_WING.get();
            case "ember" -> SONG_SHARD_EMBER.get();
            default -> SONG_SHARD_STONE_WHALE.get();
        };
    }

    // 歌片の巨神 (歌片でなければ null)
    @javax.annotation.Nullable
    public static String songShardTitan(Item item) {
        for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
            if (songShard(titan) == item) {
                return titan;
            }
        }
        return null;
    }

    // 巨神の id (stone_whale / tide_shell / thunder_wing / ember) に合う共鳴の欠片。知らない巨神は岩鯨のもの
    public static Item resonanceShard(String titan) {
        return switch (titan) {
            case "tide_shell" -> RESONANCE_SHARD_TIDE_SHELL.get();
            case "thunder_wing" -> RESONANCE_SHARD_THUNDER_WING.get();
            case "ember" -> RESONANCE_SHARD_EMBER.get();
            case "abyss" -> jp.revati.skyabyss.abyss.gear.AbyssGearSetup.RESONANCE_SHARD.get();
            default -> RESONANCE_SHARD_STONE_WHALE.get();
        };
    }

    // 共鳴の欠片か (巨神ごとのものと、古い共通のもの)
    public static boolean isResonanceShard(Item item) {
        return item == RESONANCE_SHARD.get() || item == RESONANCE_SHARD_STONE_WHALE.get() || item == RESONANCE_SHARD_TIDE_SHELL.get()
                || item == RESONANCE_SHARD_THUNDER_WING.get() || item == RESONANCE_SHARD_EMBER.get()
                || item == jp.revati.skyabyss.abyss.gear.AbyssGearSetup.RESONANCE_SHARD.get();
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
