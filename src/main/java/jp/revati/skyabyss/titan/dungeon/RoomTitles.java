package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の背の「何の部屋か分からない」部屋に、入ったときの題と一行を出す (試遊の声: 入って左の部屋・アスレの途中の鐘の部屋は何)。
//  数え歌の鐘楼 (西の翼の途中。初回のみ働く): 五つの鐘を低い方から順に叩くと、崩れ橋への戸がほどける。周回は戸が最初から開いている
// 入るたびに出すが、同じ部屋には 60 秒は出し直さない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RoomTitles {
    public static final String BELL_ROOM = "bell";
    private static final int AGAIN = 1200;

    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final Map<String, Long> SHOWN = new HashMap<>();

    private RoomTitles() {
    }

    // 位置から、いる部屋の名前 (なければ null)
    @Nullable
    public static String roomAt(BlockPos p) {
        if (BELL.contains(p) || BELL_PASS.contains(p)) {
            return BELL_ROOM;
        }
        return null;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 5 || !player.isAlive()
                || !(player.level() instanceof ServerLevel level) || level.dimension() != TitanTravel.TITAN_BACK) {
            return;
        }
        String room = roomAt(player.blockPosition());
        String before = room == null ? INSIDE.remove(player.getUUID()) : INSIDE.put(player.getUUID(), room);
        if (room == null || room.equals(before)) {
            return;
        }
        show(level, player, room);
    }

    // 題と一行を出す (同じ人・同じ部屋は AGAIN tick は出し直さない)。出したら true
    public static boolean show(ServerLevel level, ServerPlayer player, String room) {
        String k = player.getUUID() + "|" + room;
        Long last = SHOWN.get(k);
        long now = level.getGameTime();
        if (last != null && now - last < AGAIN) {
            return false;
        }
        SHOWN.put(k, now);
        String sub = subtitleKey(level, player, room);
        Notice.place(player, ModText.title("title.skyabyss.room." + room), ModText.subtitle(sub));
        return true;
    }

    public static String subtitleKey(ServerLevel level, ServerPlayer player, String room) {
        if (BELL_ROOM.equals(room) && GroupProgress.of(level, Groups.keyOf(player)).flag(BellTower.FLAG)) {
            return "subtitle.skyabyss.room.bell_done";
        }
        return "subtitle.skyabyss.room." + room;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        INSIDE.clear();
        SHOWN.clear();
    }
}
