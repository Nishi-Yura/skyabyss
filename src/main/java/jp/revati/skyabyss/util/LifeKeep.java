package jp.revati.skyabyss.util;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 倒れても消えてはいけない、人ごとの一度きりの印 (2026-10-25 に見つけた不具合)。
// 人の永続データ (getPersistentData) は、倒れて新しい体になると PlayerPersisted の中しか引き継がれない。
// 下の印はその外に書いていたので、倒れると消えて、はじめの持ち物・暖石・潮殻の殻を二度もらえ、町めぐりの鐘銭も二度、
// 大陸では「渡し船で都へ渡った」が消えて、はじまりの島で目覚め直した。倒れたときに古い体から新しい体へ写す
// (その命の間だけの値 = 待ち時間・旅の途中の状態などは写さない)。2026-10-31: 月と終章の記録 (結末・試しの段・神器を渡した) も足した
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LifeKeep {
    public static final List<String> KEYS = List.of(
            "skyabyss_rpg_kit",                       // はじめの持ち物を渡した (RpgRules)
            "skyabyss_ferried",                       // はじまりの島から渡し船で都へ渡った (ContinentOutskirts)
            "skyabyss_town_tour",                     // 町めぐりの進み (TownTour)
            "skyabyss_warm_stone_given",              // 暖石を渡した (WarmStoneItem)
            "skyabyss_tide_watching_shell_given",     // 潮見の殻を渡した (TideChapter)
            "skyabyss_shrine_pair",                   // 祠の対の札を見せた (CapitalShrine)
            "skyabyss_ch2_stakes_read",               // 二章の杭を読んだ (Ch2Ground)
            "skyabyss_ch2_wreck_seen",                // 二章の難破船を見た (Wreck)
            "skyabyss_ch2_salt_seen",                 // 塩の標を見た (SaltVault)
            "skyabyss_ch2_salt_counted",              // 塩の標を数えた (SaltVault)
            "skyabyss_abyss_max_depth",               // 深淵のいちばん深い所 (AbyssPressure)
            "skyabyss_ember_end",                     // 四章の後日談の進み (EmberAftermath)
            "skyabyss_towns_visited",                 // 行ったことのある土地の町 (ContinentAreas。地図の跡地の噂が本当の位置に)
            "skyabyss_island",                        // はじまりの島の道の進み (IslandTrail)
            "skyabyss_island_kills",                  // 島の峠で退けた野盗の数 (IslandTrail)
            "skyabyss_first_job",                     // 島の祠で選んだ職 (JobChoice)
            "skyabyss_starter_lent",                  // 師範に借りた、ほかの職の始めの武器 (JobChoice)
            "skyabyss_moon_light_body",               // 星術「軽身」を覚えた (story/ch6/cliff/LightBody)
            "skyabyss_moon_moment_stop",              // 星術「一瞬止め」を覚えた (story/ch6/field/MomentStop)
            "skyabyss_moon_trial_tier",               // 位相の挑戦の越えた段 (story/ch6/MoonTrial)
            "skyabyss_finale_ascended",               // 巨神に運ばれて雲海へ着いた (story/finale/FinaleGate)
            "skyabyss_finale_hints",                  // 終章の初めての一言 (story/finale/FinaleHints)
            "skyabyss_finale_ending",                 // 選んだ結末 (story/finale/FinaleEnding)
            "skyabyss_finale_seen",                   // 見終えた結末 (story/finale/FinaleEnding。三つで初代の空見の頼み)
            "skyabyss_pilgrim_trial",                 // 巡礼の試しの越えた段 (story/finale/PilgrimTrial)
            "skyabyss_divine_given",                  // 神器を渡した (story/finale/gear/DivineGear)
            "skyabyss_founder",                       // 初代の空見の進み (story/finale/founder/Founder)
            "skyabyss_founder_best",                  // 初代の空見の最速 (tick)
            "skyabyss_founder_shawl",                 // 空見の肩掛けを着ている
            "skyabyss_tower_day");                    // あいだの塔に入った日 (一日一回。tower/Towers)
    // 頭が同じ印 (巨神ごと・主ごとに分かれる記録)
    public static final List<String> PREFIXES = List.of(
            "skyabyss_moon_trial.");                  // 位相の挑戦の主ごとの最速 (story/ch6/MoonTrial)

    private LifeKeep() {
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            copy(event.getOriginal(), event.getEntity());
        }
    }

    public static void copy(Player from, Player to) {
        CompoundTag a = from.getPersistentData();
        CompoundTag b = to.getPersistentData();
        for (String k : KEYS) {
            if (a.contains(k) && !b.contains(k)) {
                b.put(k, a.get(k).copy());
            }
        }
        for (String k : a.getAllKeys()) {
            if (!b.contains(k) && PREFIXES.stream().anyMatch(k::startsWith)) {
                b.put(k, a.get(k).copy());
            }
        }
    }
}
