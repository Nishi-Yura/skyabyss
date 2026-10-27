package jp.revati.skyabyss.opening;

import jp.revati.skyabyss.util.Players;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcSetup;
import jp.revati.skyabyss.npc.NpcSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

// 語り部に会ったか (岩鯨の出現の条件・序盤の目的) と、方角の 8 方位。語り部は都の大鐘楼にいる (NpcSpawner)
public final class OpeningBeats {
    private OpeningBeats() {
    }

    // 語り部と話したことがあるか (記録は人ごと。月のループで印が「覚え」に移っても、会った人は会った人)
    static boolean metStoryteller(ServerPlayer player) {
        NpcBonds.Bond bond = NpcBonds.of(player).bonds().get(NpcSetup.STORYTELLER_ID);
        return bond != null && (bond.talks() > 0 || bond.flags().contains("met") || bond.echoes().contains("met"));
    }

    // オーバーワールドにいる誰かが語り部と話したことがあるか (岩鯨の出現の条件)
    public static boolean anyoneMetStoryteller(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && metStoryteller(p)) {
                return true;
            }
        }
        return false;
    }

    // 北を 0 として 8 方位 (0 北 1 北東 2 東 …)
    public static int compass(BlockPos from, BlockPos to) {
        double degrees = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        return Math.floorMod(Math.round((float) (degrees / 45.0)), 8);
    }
}
