package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.RoomPlay;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 戦いの部屋の罠・柱 (RoomPlay) を、背のディメンションの部屋ごとに 5 tick おきに動かす入口
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RoomPlayEvents {
    private RoomPlayEvents() {
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % RoomPlay.STEP != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            String titan = Titans.titanOfBack(level.dimension());
            if (titan == null || level.players().isEmpty()) {
                continue;
            }
            for (RoomPlay.Room room : RoomPlayRooms.of(titan)) {
                RoomPlay.step(level, room, level.getGameTime());
                stepCopies(level, room, level.getGameTime());
            }
        }
    }

    // 区画の写しの部屋 (docs/design/32 の 5 章): 写しで遊んでいるグループごとに、手本の座標で一回り動かす (TrialWindow)
    private static void stepCopies(ServerLevel level, RoomPlay.Room room, long now) {
        jp.revati.skyabyss.trial.TrialCell cell = jp.revati.skyabyss.trial.TrialCells.get(room.id().toUpperCase(java.util.Locale.ROOT));
        if (cell == null || !cell.relocatable()) {
            return;
        }
        java.util.Map<java.util.UUID, java.util.List<net.minecraft.server.level.ServerPlayer>> byGroup = new java.util.HashMap<>();
        for (net.minecraft.server.level.ServerPlayer p : jp.revati.skyabyss.util.Players.snapshot(level.players())) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            java.util.UUID g = jp.revati.skyabyss.titan.group.Groups.keyOf(p);
            jp.revati.skyabyss.trial.TrialFrame f = jp.revati.skyabyss.trial.TrialSlot.frameOf(level, g, cell);
            if (!f.isZero() && f.box(room.aabb()).contains(p.position())) {
                byGroup.computeIfAbsent(g, k -> new java.util.ArrayList<>()).add(p);
            }
        }
        for (var e : byGroup.entrySet()) {
            jp.revati.skyabyss.trial.TrialFrame f = jp.revati.skyabyss.trial.TrialSlot.frameOf(level, e.getKey(), cell);
            jp.revati.skyabyss.trial.TrialWindow.run(level, e.getKey(), cell, f, () -> RoomPlay.stepGroupForTest(level, room, e.getKey(), e.getValue(), now));
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RoomPlay.clearAll();
    }
}
