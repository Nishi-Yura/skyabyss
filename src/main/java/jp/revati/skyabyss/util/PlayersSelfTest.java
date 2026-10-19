package jp.revati.skyabyss.util;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// Players.snapshot の自己テスト。ループの途中で一覧から人を外しても (別の次元へ移す・切断する相当)、写しなら最後まで回る。
// 生きた一覧を直接回すと ConcurrentModificationException になること (= 写しが要る理由) も確かめる。
// 実サーバーでは、オーバーワールドの level.players() の写しが別のリストであることも確かめる
public final class PlayersSelfTest {
    private PlayersSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            List<String> live = new ArrayList<>(List.of("a", "b", "c", "d"));
            boolean threw = false;
            try {
                for (String s : live) {
                    live.remove(s);
                }
            } catch (ConcurrentModificationException e) {
                threw = true;
            }
            if (!threw) {
                failures.add("the raw loop did not throw (the test premise is broken)");
            }

            live = new ArrayList<>(List.of("a", "b", "c", "d"));
            List<String> seen = new ArrayList<>();
            for (String s : Players.snapshot(live)) {
                seen.add(s);
                live.remove(s); // 回している最中に外す
                live.add("x" + s); // 足されても写しには出ない
            }
            if (!seen.equals(List.of("a", "b", "c", "d"))) {
                failures.add("snapshot loop saw " + seen);
            }
            if (live.size() != 4 || !live.contains("xa")) {
                failures.add("the live list was not changed as expected: " + live);
            }

            ServerLevel overworld = server.overworld();
            List<ServerPlayer> copy = Players.snapshot(overworld.players());
            if (copy == overworld.players() || copy.size() != overworld.players().size()) {
                failures.add("the level snapshot is not a separate copy");
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST players crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST players PASSED");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST players FAILED: {}", f));
        }
    }
}
