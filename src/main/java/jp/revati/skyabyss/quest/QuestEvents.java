package jp.revati.skyabyss.quest;

import jp.revati.skyabyss.util.Players;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// クエストの目的を、既存の出来事から拾う。岩鯨・伝承・空の影のコードには手を入れず、
// 伝承の書の記録 (Lore.state)、持ち物、いる場所、死亡イベントだけを見る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuestEvents {
    // 倒したときに数える距離の上限 (目的ごとの radius はこの内側で決める)
    private static final double KILL_SEARCH = 64;

    private QuestEvents() {
    }

    // 参加したときと /reload のあと、定義の一覧を送る (記録は Attachment の同期で届く)
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(p -> {
            QuestNetwork.sendCatalog(p);
            Quests.sync(p);
        });
    }

    // 1 秒ごとに見回る (人ごとに時刻をずらす)。近くの生き物は 2 秒ごと
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        long now = player.serverLevel().getGameTime() + (player.getId() & 0xFF);
        if (now % 20 == 7) {
            Quests.poll(player, now % 40 == 7);
        }
    }

    // 倒れた: 倒した人と、近くにいた人 (倒れて復活を待っている人も含む。観戦者は除く) の目的を進める。
    // 取り消された死亡は数えない (いちばん最後に動く)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer) {
            return;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        if (!anyKillObjective(level, id)) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        credit(level, dead, id, killer, uuid -> level.getServer().getPlayerList().getPlayer(uuid));
    }

    // 倒した生き物の目的を数える。lookup はパーティの仲間を UUID から引く (自己テストでは偽のプレイヤーを渡す)
    public static void credit(ServerLevel level, LivingEntity dead, String id, ServerPlayer killer,
                       java.util.function.Function<java.util.UUID, ServerPlayer> lookup) {
        Set<ServerPlayer> players = new LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class,
                dead.getBoundingBox().inflate(KILL_SEARCH), p -> !p.isSpectator()));
        if (killer != null && killer.level() == level) {
            players.add(killer);
        }
        // 居合わせた人 (倒した人と、ふつうの半径 32 の内) のパーティの仲間は、経験値の分け前と同じ条件 (64 ブロック以内、
        // 同じ巨神の背中なら距離を問わない、10 レベルを超えて低い人は除く) で、party の目的を数える (10 の 8.5 節)
        java.util.List<ServerPlayer> present = new java.util.ArrayList<>();
        for (ServerPlayer p : players) {
            if (p == killer || p.distanceTo(dead) <= SHARE_PRESENT) {
                present.add(p);
            }
        }
        java.util.List<ServerPlayer> mates = jp.revati.skyabyss.party.Parties.absentMates(level, present, lookup);
        for (ServerPlayer p : players) {
            if (!mates.contains(p)) {
                Quests.onKill(p, id, p == killer, p.distanceTo(dead));
            }
        }
        for (ServerPlayer mate : mates) {
            // 一度だけ数える (仲間として party の目的だけ。party でない目的は、居合わせた人だけのもの)
            Quests.onKillShared(mate, id);
        }
    }

    private static final double SHARE_PRESENT = 32;

    // その生き物を倒す目的が、だれかの進行中の段階にあるか (毎回の死亡で全員を回さないため)
    private static boolean anyKillObjective(ServerLevel level, String id) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            for (Map.Entry<String, QuestLog.Entry> e : QuestLog.of(p).quests().entrySet()) {
                QuestDef def = QuestManager.quest(e.getKey());
                QuestLog.Entry entry = e.getValue();
                if (def == null || entry.done || entry.stage >= def.stages().size()) {
                    continue;
                }
                for (QuestDef.Objective ob : def.stages().get(entry.stage).objectives()) {
                    if (ob.type().equals("kill") && ob.targets().contains(id)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
