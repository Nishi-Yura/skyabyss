package jp.revati.skyabyss.opening.site;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;

// 都の閉じた施設が章で開く仕組み (CapitalDoors) の自己テスト。段の名前は "capital doors"。
//  - 都が建っていれば、四つの施設の戸の印と碑の印が記録にある
//  - 建てた直後は鉄の戸・鉄格子が閉じている。force で開けると消え、記録に「開いた」が残り、二度目は何もしない
public final class CapitalDoorsSelfTest {
    private CapitalDoorsSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("opening (capital doors)", 10, CapitalDoorsSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static int ironNear(ServerLevel level, BlockPos at) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 6; dy++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.is(Blocks.IRON_DOOR) || st.is(Blocks.IRON_BARS)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        SiteData.Entry e = data.first("shelter");
        if (e == null || !e.built) {
            SiteBuilds.buildAllNow(server);
            e = data.first("shelter");
        }
        if (e == null || !e.built) {
            SkyAbyss.LOGGER.info("SELFTEST capital doors: the capital is not built here (small shelter or no site), skipped");
            SkyAbyss.LOGGER.info("SELFTEST capital doors PASSED (skipped)");
            return;
        }
        boolean small = System.getenv("SKYABYSS_SMALL_SHELTER") != null;
        if (small) {
            SkyAbyss.LOGGER.info("SELFTEST capital doors PASSED (small shelter, skipped)");
            return;
        }
        for (CapitalDoors.Facility fac : CapitalDoors.ALL) {
            check(f, e.extra("sign_" + fac.id()) != null, fac.id() + ": the tablet mark is missing");
            for (String m : fac.marks()) {
                BlockPos at = e.extra(m);
                check(f, at != null, fac.id() + ": the door mark " + m + " is missing");
                if (at != null) {
                    level.getChunk(at);
                    check(f, CapitalDoors.opened(server, fac.id()) || ironNear(level, at) > 0, fac.id() + ": no iron door or bars near " + m + " at " + at);
                }
            }
        }
        // 訓練場ははじめから開いている (門に鉄格子がない)
        BlockPos training = e.extra("open_training");
        check(f, training != null, "training: the gate mark open_training is missing");
        if (training != null) {
            level.getChunk(training);
            check(f, ironNear(level, training) == 0, "training: the gate should be open from the start, found iron near " + training);
        }
        // 裏通りの隠し (35 の 6.9): 古井戸の下と酒場の地下の奥の印。奥の壁は閉じたまま建つ
        for (String m : List.of("well_down", "well_chest", "cellar_gap", "cellar_passage", "abyss_hint")) {
            check(f, e.extra(m) != null, "back alley: the mark " + m + " is missing");
        }
        BlockPos gap = e.extra("cellar_gap");
        if (gap != null) {
            check(f, !level.getBlockState(gap).isAir() && !level.getBlockState(gap.above()).isAir(), "back alley: the cellar wall is open at build (" + gap + ")");
        }
        BlockPos chest = e.extra("well_chest");
        if (chest != null) {
            check(f, level.getBlockState(chest).is(Blocks.CHEST), "back alley: no chest at well_chest " + chest + ": " + level.getBlockState(chest));
        }
        // 社の祭壇 (転生の台座) の印、組合の中の言伝ての板 2 枚、都の鐘のそばに板を自動で立てない記録
        check(f, e.extra("altar") != null, "the shrine altar mark is missing");
        BlockPos guildDoor = e.extra("open_guild");
        if (guildDoor != null) {
            int boards = 0;
            for (BlockPos q : BlockPos.betweenClosed(guildDoor.offset(-45, -2, -45), guildDoor.offset(45, 4, 45))) {
                if (level.getBlockState(q).is(jp.revati.skyabyss.quest.free.FreeQuestSetup.MESSAGE_BOARD.get())) {
                    boards++;
                }
            }
            check(f, boards == 2, "the guild should hold 2 message boards, found " + boards);
        }
        BlockPos bell = e.extra("bell");
        check(f, bell != null && jp.revati.skyabyss.quest.free.FreeQuestEvents.boardSuppressed(level, bell), "a message board may still be placed by the capital bell");
        // 鍵の間の四つの石の印、組合の名簿の碑
        for (String t : CapitalShrine.TITANS) {
            check(f, e.extra("stone_" + t) != null, "the shrine stone mark for " + t + " is missing");
        }
        // 組合を開ける: 戸が消え、記録が残る
        if (!CapitalDoors.opened(server, "guild")) {
            BlockPos at = e.extra("open_guild");
            check(f, CapitalDoors.force(server, "guild"), "force(guild) returned false");
            check(f, at == null || ironNear(level, at) == 0, "the guild doors are still there after opening");
            check(f, CapitalDoors.opened(server, "guild"), "the guild is not recorded as opened");
            check(f, !CapitalDoors.opened(server, "hall"), "the hall opened with the guild");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST capital doors PASSED (gated facilities have door and tablet marks; the training yard is open; back-alley marks; the guild opens on force and stays recorded)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST capital doors FAILED: {}", s);
            }
        }
    }
}
