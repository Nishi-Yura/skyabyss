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
// (その命の間だけの値 = 待ち時間・旅の途中の状態などは写さない)
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
            "skyabyss_first_job");                    // 島の祠で選んだ職 (JobChoice)

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
    }
}
