package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// あいだの塔の自己テスト (docs/design/54 の 2 章。橋の上の一戦)。段: "tower"
//   次元・橋の印 / 資格と一日一回 / 挑戦: 区画に流し込む (橋・帰りの天然磁石・印の石)・時間切れの判定・終わり / 区画は隊ごとに別
public final class TowerSelfTest {
    private TowerSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<TowerRun> runs = new ArrayList<>();
        try {
            ServerLevel level = TowerWorld.level(server);
            check(f, level != null, "the tower dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            TowerTerrain t = TowerTerrain.of(TowerWorld.ARENA);
            for (String m : List.of("entry", "heal", "return", "sigil_1", "sigil_2", "sigil_3", "eye_l_1", "eye_r_1", "eye_l_2", "eye_r_2", "eye_big_l", "eye_big_r",
                    "hand_l", "hand_r", "healer_l", "healer_r", "moon", "boss", "bridge_n", "bridge_s")) {
                check(f, t.has(m), "the bridge lacks mark " + m);
            }
            FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000070e1"), "tower_test"));
            FakePlayer q = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000070e2"), "tower_test_b"));
            p.getPersistentData().remove(FinaleEnding.KEY);
            p.getPersistentData().remove(Towers.DAY);
            check(f, !Towers.eligible(p), "someone without an ending can enter the tower");
            p.getPersistentData().putString(FinaleEnding.KEY, "calm");
            check(f, Towers.eligible(p) && !Towers.usedToday(p), "an eligible player is not eligible");
            Towers.markUsed(p);
            check(f, Towers.usedToday(p), "the daily entry was not recorded");
            p.getPersistentData().remove(Towers.DAY);

            TowerRun a = Towers.start(server, List.of(p), true);
            TowerRun b = Towers.start(server, List.of(q), true);
            if (a != null) {
                runs.add(a);
            }
            if (b != null) {
                runs.add(b);
            }
            check(f, a != null && b != null && a.slot != b.slot && Towers.runOf(p) == a, "two squads did not get their own slots");
            check(f, !Towers.usedToday(p), "a dev start used the daily entry");
            if (a != null) {
                check(f, level.getBlockState(a.mark("return")).is(Blocks.LODESTONE) && level.getBlockState(a.mark("sigil_2").below()).is(Blocks.CRYING_OBSIDIAN),
                        "the bridge was not poured in the slot");
                check(f, !a.arena.timedOut(a.startedTick + TowerArena.LIMIT - 1) && a.arena.timedOut(a.startedTick + TowerArena.LIMIT), "the 30 minute limit is wrong");
                Towers.end(a);
                runs.remove(a);
                check(f, Towers.runOf(p) == null && !Towers.RUNS.containsKey(a.id), "the run did not end");
            }
            p.getPersistentData().remove(FinaleEnding.KEY);
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("SELFTEST tower crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            for (TowerRun r : runs) {
                Towers.end(r);
            }
        }
        report(f);
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tower PASSED (dimension, bridge marks, eligibility and daily entry, slots per squad, bridge poured, time limit, end)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tower FAILED: {}", m));
        }
    }
}
