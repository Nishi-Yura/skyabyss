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
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        RoomPlay.clearAll();
    }
}
