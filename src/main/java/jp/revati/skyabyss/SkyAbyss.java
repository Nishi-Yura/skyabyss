package jp.revati.skyabyss;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import jp.revati.skyabyss.registry.ModArmorMaterials;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModEffects;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.registry.ModMenus;
import jp.revati.skyabyss.registry.ModSounds;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// 天と淵のあいだ (Between Sky and Abyss) のメインクラス。
// 値は META-INF/neoforge.mods.toml の modId と一致させる。
@Mod(SkyAbyss.MODID)
public class SkyAbyss {
    public static final String MODID = "skyabyss";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkyAbyss(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        ModSounds.register(modEventBus);
        ModEffects.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModArmorMaterials.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        // 成長 (レベル・ステータス・記憶の断片)。登録は rpg パッケージにまとめてある
        jp.revati.skyabyss.rpg.Rpg.register(modEventBus);
        jp.revati.skyabyss.prologue.PrologueRegistry.register(modEventBus);
        // 1章の空 (削られた山頂・雲の目) の音
        jp.revati.skyabyss.sky.ChapterSkyRegistry.register(modEventBus);
        // 伝承の書 (アイテム・頁の出どころ・プレイヤーの記録)
        jp.revati.skyabyss.lore.LoreSetup.register(modEventBus);
        // 技 (スキル・マナ・技の枠)。登録は rpg.skill パッケージにまとめてある
        jp.revati.skyabyss.rpg.skill.Skills.register(modEventBus);
        // NPC (語り部・鯨追い、好感度) とクエスト (記録)。登録は npc / quest パッケージにまとめてある
        jp.revati.skyabyss.npc.NpcSetup.register(modEventBus);
        jp.revati.skyabyss.quest.QuestSetup.register(modEventBus);
        // 自由なモードのサブクエスト (言伝ての板)。docs/design/07 の 15 章
        jp.revati.skyabyss.quest.free.FreeQuestSetup.register(modEventBus);
        // 巨神での移動 (呼び笛・碑・音・プレイヤーの記録)。登録は titan.travel パッケージにまとめてある
        jp.revati.skyabyss.titan.travel.TravelSetup.register(modEventBus);
        // 第二の巨神「潮殻の巨神」(エンティティ・音)。登録は titan.tide パッケージにまとめてある
        jp.revati.skyabyss.titan.tide.TideSetup.register(modEventBus);
        // 第三の巨神「雷翼の巨神」(エンティティ・音・背中の結びつけ)。登録は titan.storm パッケージにまとめてある
        jp.revati.skyabyss.titan.storm.StormSetup.register(modEventBus);
        // 潮殻の背の敵 (珊瑚憑き・磯走り・泡吹き)
        jp.revati.skyabyss.titan.tide.mob.TideMobSetup.register(modEventBus);
        // 雷翼の背の敵 (羽守・雷蛾・雷乞い)。docs/design/12 の 5.1節
        jp.revati.skyabyss.titan.storm.mob.StormMobSetup.register(modEventBus);
        // 装飾の枠 (護符 1・装飾 2。記録・画面・パケット)。登録は item.accessory パッケージにまとめてある
        jp.revati.skyabyss.item.accessory.AccessorySetup.register(modEventBus);
        // 潮殻の心臓 (最終ボス・泡・真珠・音)。登録は titan.tide.heart パッケージにまとめてある
        jp.revati.skyabyss.titan.tide.heart.TideHeartSetup.register(modEventBus);
        // 潮殻の背の本番 (潮鳴り石、潮の真珠、群れの巣、潮招き)
        jp.revati.skyabyss.titan.tide.back.TideBackSetup.register(modEventBus);
        // 雷翼の背 (鳥の形の背中・三つの試練: 風鳴り石、羽鏡、受け羽、風切りの羽根)
        jp.revati.skyabyss.titan.storm.back.StormBackSetup.register(modEventBus);
        // 1 章の終わり (天の鍵、ベータの終わり)。docs/design/14_beta.md
        jp.revati.skyabyss.story.StorySetup.register(modEventBus);
        // 雷翼の中ボス 雷獣 (12 の 5.2節)。登録は titan.storm.raiju パッケージにまとめてある
        jp.revati.skyabyss.titan.storm.raiju.RaijuSetup.register(modEventBus);
        // 潮殻の装備 (鎧・三叉槍・盾・護符・専用伝説・心核) と図鑑の表。登録は titan.tide.gear パッケージにまとめてある
        jp.revati.skyabyss.titan.tide.gear.TideGearSetup.register(modEventBus);
        // 第四の巨神「余燼の巨神」の骨組み (熱の音、背中の口)。ID の一覧は titan.ember.EmberIds。docs/design/13_ember.md の 11章
        jp.revati.skyabyss.titan.ember.EmberSetup.register(modEventBus);
        // 余燼の背の敵 (灰被り・火の粉蛾・燠虫、スポーンエッグ、音)。13 の 11.2節の担当 D
        jp.revati.skyabyss.titan.ember.mob.EmberMobSetup.register(modEventBus);
        // 余燼の中ボス 灰の鍛え手 (エンティティ・音・鍛え手の間の建て方)。13 の 11.2節の担当 E
        jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup.register(modEventBus);
        // 雷翼の装備 (羽衣・弓・足環・護符・専用伝説) と素材 4 つ (欠片・心核・干し果・鱗粉)、図鑑の表。item.storm パッケージ
        jp.revati.skyabyss.item.storm.StormGearSetup.register(modEventBus);
        // 余燼の装備 (鎧・戦斧・外套・護符・専用伝説・角杯・素材) と図鑑の表、伝承の締め。13 の 11.2節の担当 G
        jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.register(modEventBus);

        // サーバー設定はワールドごとに保存され、マルチプレイでは参加者に同期される。
        // クライアント設定は各プレイヤーのPCごとの好み
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[{}] 天と淵のあいだ を読み込みました", MODID);
    }
}
