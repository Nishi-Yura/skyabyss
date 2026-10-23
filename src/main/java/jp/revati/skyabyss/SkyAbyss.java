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
import jp.revati.skyabyss.registry.MobSounds;
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
        SelfTestRunner.listAndExitIfRequested();
        modEventBus.addListener(this::commonSetup);
        ModSounds.register(modEventBus);
        MobSounds.register(modEventBus);
        ModEffects.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        jp.revati.skyabyss.advancement.Beats.register(modEventBus);
        jp.revati.skyabyss.registry.ModBlockEntities.register(modEventBus);
        ModArmorMaterials.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        // 装備の技の修飾 (ルーン・属性タグ) を SkillModifier へつなぐ
        jp.revati.skyabyss.item.extra.GearSkillModifier.register();
        // 大事な場面のカメラ演出 (見た場面の記録)。docs/design/20_cinematics.md
        jp.revati.skyabyss.cinematic.CinematicSetup.register(modEventBus);
        // 成長 (レベル・ステータス・記憶の断片)。登録は rpg パッケージにまとめてある
        jp.revati.skyabyss.rpg.Rpg.register(modEventBus);
        jp.revati.skyabyss.rebirth.Rebirth.register(modEventBus);
        jp.revati.skyabyss.prologue.PrologueRegistry.register(modEventBus);
        // 1章の空 (削られた山頂・雲の目) の音
        jp.revati.skyabyss.sky.ChapterSkyRegistry.register(modEventBus);
        // 伝承の書 (アイテム・頁の出どころ・プレイヤーの記録)
        jp.revati.skyabyss.lore.LoreSetup.register(modEventBus);
        // 技 (スキル・マナ・技の枠)。登録は rpg.skill パッケージにまとめてある
        jp.revati.skyabyss.rpg.skill.Skills.register(modEventBus);
        jp.revati.skyabyss.abyss.arts.AbyssArts.register(modEventBus);
        // 深淵の心臓の番人 (docs/design/36 の 6)
        jp.revati.skyabyss.story.ch5.warden.WardenSetup.register(modEventBus);
        // NPC (語り部・鯨追い、好感度) とクエスト (記録)。登録は npc / quest パッケージにまとめてある
        jp.revati.skyabyss.npc.NpcSetup.register(modEventBus);
        // 都の施設 (係の人・鐘銭・店・預かり所・宿・ギルド)。docs/design/35
        jp.revati.skyabyss.facility.FacilitySetup.register(modEventBus);
        jp.revati.skyabyss.mount.MountSetup.register(modEventBus);
        // 町のクエストの限定の人と宿の噂 (docs/design/35 の F5・F6)
        jp.revati.skyabyss.town.TownSetup.register(modEventBus);
        jp.revati.skyabyss.quest.QuestSetup.register(modEventBus);
        // 自由なモードのサブクエスト (言伝ての板)。docs/design/07 の 15 章
        jp.revati.skyabyss.quest.free.FreeQuestSetup.register(modEventBus);
        // 巨神での移動 (呼び笛・碑・音・プレイヤーの記録)。登録は titan.travel パッケージにまとめてある
        jp.revati.skyabyss.titan.travel.TravelSetup.register(modEventBus);
        // 巨神の子 (岩鯨の仔・潮殻の稚貝・雷翼の雛・余燼の仔。パーティ全員を乗せて運ぶ)。docs/design/28
        jp.revati.skyabyss.titan.young.YoungSetup.register(modEventBus);
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
        // 留め袋 (この Mod の集める品・鍵を入れる専用の持ち物。記録・取り出し済みの印・ゲームルール)。docs/design/22
        jp.revati.skyabyss.pouch.PouchSetup.register(modEventBus);
        // 初回の手引き (品・出来事で始まり、した操作で済む。人ごとの記録)。docs/design/18 の 12 章
        jp.revati.skyabyss.tutorial.TutorialSetup.register(modEventBus);
        // 潮殻の心臓 (最終ボス・泡・真珠・音)。登録は titan.tide.heart パッケージにまとめてある
        jp.revati.skyabyss.titan.tide.heart.TideHeartSetup.register(modEventBus);
        // 雷翼の心臓 (第三の巨神の最終ボス・音)。登録は titan.storm.heart パッケージにまとめてある
        jp.revati.skyabyss.titan.storm.heart.StormHeartSetup.register(modEventBus);
        // 潮殻の背の本番 (潮鳴り石、潮の真珠、群れの巣、潮招き)
        jp.revati.skyabyss.titan.tide.back.TideBackSetup.register(modEventBus);
        // 雷翼の背 (鳥の形の背中・三つの試練: 風鳴り石、羽鏡、受け羽、風切りの羽根)
        jp.revati.skyabyss.titan.storm.back.StormBackSetup.register(modEventBus);
        // 1 章の終わり (天の鍵、ベータの終わり)。docs/design/14_beta.md
        jp.revati.skyabyss.story.StorySetup.register(modEventBus);
        // 雷翼の中ボス 雷獣 (12 の 5.2節)。登録は titan.storm.raiju パッケージにまとめてある
        jp.revati.skyabyss.titan.storm.raiju.RaijuSetup.register(modEventBus);
        // 潮殻の装備 (鎧・三叉槍・盾・護符・専用の逸品・心核) と図鑑の表。登録は titan.tide.gear パッケージにまとめてある
        jp.revati.skyabyss.titan.tide.gear.TideGearSetup.register(modEventBus);
        // 第四の巨神「余燼の巨神」の骨組み (熱の音、背中の口)。ID の一覧は titan.ember.EmberIds。docs/design/13_ember.md の 11章
        jp.revati.skyabyss.titan.ember.EmberSetup.register(modEventBus);
        // 余燼の背の体と広間 (熾り石・背中の音)。docs/design/13_ember.md の 18章
        jp.revati.skyabyss.titan.ember.back.EmberBackSetup.register(modEventBus);
        // 余燼の背の敵 (灰被り・火の粉蛾・燠虫、スポーンエッグ、音)。13 の 11.2節の担当 D
        jp.revati.skyabyss.titan.ember.mob.EmberMobSetup.register(modEventBus);
        // 四章の地上の敵・品・音 (docs/design/34 の C4。story/ch4)
        jp.revati.skyabyss.story.ch4.Ch4Setup.register(modEventBus);
        // 深淵の敵 A (骨・塩・雷の層の 9 種、段ごとの型・素材・経験値)。docs/design/36 の 4 節。abyss/mobs
        jp.revati.skyabyss.abyss.mobs.AbyssMobsA.register(modEventBus);
        // 余燼の中ボス 灰の鍛え手 (エンティティ・音・鍛え手の間の建て方)。13 の 11.2節の担当 E
        jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup.register(modEventBus);
        // 雷翼の装備 (羽衣・弓・足環・護符・専用の逸品) と素材 4 つ (欠片・心核・干し果・鱗粉)、図鑑の表。item.storm パッケージ
        jp.revati.skyabyss.item.storm.StormGearSetup.register(modEventBus);
        // 余燼の巨神の地上 (本体・熱の口・温石・地上の音)。13 の 2章、11.2節の担当 A
        jp.revati.skyabyss.titan.ember.EmberTitanSetup.register(modEventBus);
        // 余燼の装備 (鎧・戦斧・外套・護符・専用の逸品・角杯・素材) と図鑑の表、伝承の締め。13 の 11.2節の担当 G
        jp.revati.skyabyss.titan.ember.gear.EmberGearSetup.register(modEventBus);
        jp.revati.skyabyss.abyss.AbyssSetup.register(modEventBus);
        jp.revati.skyabyss.abyss.gear.AbyssGearSetup.register(modEventBus);
        // 前の私の落とし物 (深淵の墓の遺品 12。docs/design/36 の 9 章)
        jp.revati.skyabyss.story.ch5.AbyssRelics.register(modEventBus);
        jp.revati.skyabyss.abyss.mobs.AbyssMobsBSetup.register(modEventBus);
        // 余燼の心臓 (最終ボス: 熾の底、三段階、灰の礫、報酬、天の鍵の四つ目の欠片)。13 の 11.2節の担当 F
        jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup.register(modEventBus);

        // 序盤の拍子 (プレイヤーごとの状態・音)。docs/design/17 の担当 3
        jp.revati.skyabyss.opening.OpeningSetup.register(modEventBus);
        // 章の演出の共通部品 (題字・余韻の札、予兆の枠)。docs/design/18 の 3 章、01 の 4.1
        jp.revati.skyabyss.story.arc.ArcSetup.register(modEventBus);

        // 章の導き (備えの頼み・旅の覚書・鼓動守・場所の湧き台と宝・ひとり歩きの湧き・経験値の枠)。docs/design/21 の 1.4・1.5、P3
        jp.revati.skyabyss.guide.GuideSetup.register(modEventBus);
        // 旅装・砥石・糧と薬 (一章の分。21 の 1.2、P5)
        jp.revati.skyabyss.guide.TravelGear.register(modEventBus);

        // クエストの見え方 (章の札・NPC の頭の印・更新の音)。docs/design/18 の 3.5 節
        jp.revati.skyabyss.quest.QuestUx.register(modEventBus);

        // 心臓の遺物の選択画面 (眠りの場面が終わったら開く)。docs/design/30_heart_reward_ui.md
        jp.revati.skyabyss.loot.reward.HeartReward.register();

        // 転生の見せ方 (色の段・記号・名札・称号・背中の飾り・順位の石・飾り板)。docs/design/31
        jp.revati.skyabyss.rebirth.RebirthSetup.register(modEventBus, modContainer);

        // サーバー設定はワールドごとに保存され、マルチプレイでは参加者に同期される。
        // クライアント設定は各プレイヤーのPCごとの好み
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 層の主の戦いの合図を見た目 (BossLook) へつなぐ
        jp.revati.skyabyss.abyss.boss.BossLookHook.install();
        LOGGER.info("[{}] 天と淵のあいだ を読み込みました", MODID);
    }
}
