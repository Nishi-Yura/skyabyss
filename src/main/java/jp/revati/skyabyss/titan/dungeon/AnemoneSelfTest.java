package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 闇の水路の灯 (docs/design/23。AnemoneLights) の自己テスト。段は "titan back (lights)" (SKYABYSS_SELFTEST_ONLY="titan back" で流れる)。
// 置き場・摘む・ともす・上書き・なだめる・戸が開く・グループごと・リセット・手持ちをなくしても詰まない・持ち歩きの光・初回の既知の道
public final class AnemoneSelfTest {
    private AnemoneSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add("lights: " + what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static int lamps(FakePlayer p) {
        return p.getInventory().countItem(ModItems.GLOW_ANEMONE.get());
    }

    public static List<String> run(ServerLevel back) {
        List<String> f = new ArrayList<>();
        long seed = TitanBackData.get(back).mazeSeed();
        FloodedMaze maze = MazeBuilder.maze(seed);
        List<BlockPos> stands = AnemoneLights.stands(seed);
        List<BlockPos> gate = AnemoneLights.exitGate(seed);
        FakePlayer a = player(back, "lamp_a");
        FakePlayer b = player(back, "lamp_b");
        FakePlayer c = player(back, "lamp_c");
        UUID ga = a.getUUID();
        UUID gb = b.getUUID();
        UUID gc = c.getUUID();
        AnemoneLights.extraMembers = List.of(a, b, c);
        BlockPos probeLight = null;
        try {
            // ---- 数の決まり: 台は灯より多く (全部はともせず)、戸が開くのは灯の数以下 (どう置いても詰まない)
            check(f, AnemoneLights.STANDS == 5 && AnemoneLights.LIGHTS == 4 && AnemoneLights.NEED == 3, "the counts are not 5 stands / 4 lamps / 3 needed");
            check(f, AnemoneLights.STANDS > AnemoneLights.LIGHTS && AnemoneLights.LIGHTS >= AnemoneLights.NEED, "stands > lamps >= needed does not hold");
            check(f, LAYOUT_VERSION >= 12, "LAYOUT_VERSION was not bumped for the lamps (" + LAYOUT_VERSION + ")");

            // ---- 置き場: 台は正しい道の上の、息継ぎでないマスの真ん中。戸は出口の手前の通路。苗床は入口と中ほど
            check(f, stands.size() == AnemoneLights.STANDS && stands.stream().distinct().count() == stands.size(), "stands are not 5 distinct places");
            List<int[]> path = maze.solution();
            for (int i = 0; i < stands.size(); i++) {
                BlockPos s = stands.get(i);
                BlockState st = back.getBlockState(s);
                check(f, st.is(ModBlocks.ANEMONE_PEDESTAL.get()) && !st.getValue(AnemonePedestalBlock.LIT) && st.getValue(AnemonePedestalBlock.WATERLOGGED),
                        "stand " + i + " is not an unlit waterlogged pedestal at " + s + " (" + st + ")");
                boolean onPath = false;
                for (int k = 1; k < path.size() - 2; k++) {
                    if (k % MazeBuilder.POCKET_EVERY != 0 && mazeCell(path.get(k)[0], path.get(k)[1]).offset(MAZE_CELL / 2, 0, MAZE_CELL / 2).equals(s)) {
                        onPath = true;
                    }
                }
                check(f, onPath, "stand " + i + " is not on the correct path (or sits in a breathing pocket) at " + s);
            }
            check(f, !gate.isEmpty(), "the exit gate is empty");
            for (BlockPos g : gate) {
                BlockState st = back.getBlockState(g);
                check(f, st.is(ModBlocks.SEALED_GATE.get()) && !st.getValue(SealedGateBlock.OPEN), "exit gate block at " + g + " is not a closed sealed gate (" + st + ")");
            }
            BlockPos exitCell = mazeCell(MAZE_EXIT_I, MAZE_EXIT_J);
            BlockPos beforeCell = mazeCell(path.get(path.size() - 2)[0], path.get(path.size() - 2)[1]);
            check(f, gate.stream().allMatch(g -> g.distSqr(exitCell) <= 60 && g.distSqr(beforeCell) <= 60), "the exit gate is not in the passage before the exit");
            check(f, back.getBlockState(AnemoneLights.TALLY_POS).is(ModBlocks.ANEMONE_TALLY.get()), "tally stone missing in the entry room");
            check(f, back.getBlockState(AnemoneLights.ENTRY_BED_POS).is(ModBlocks.ANEMONE_BED.get()), "anemone bed missing in the entry room");
            check(f, MAZE_ENTRY_ROOM.contains(AnemoneLights.TALLY_POS) && MAZE_ENTRY_ROOM.contains(AnemoneLights.ENTRY_BED_POS), "tally / bed are outside the entry room");
            BlockPos midBed = MazeBuilder.midPocket(maze).offset(MAZE_CELL - 1, 2, 0);
            check(f, back.getBlockState(midBed).is(ModBlocks.ANEMONE_BED.get()), "anemone bed missing at the mid rest ledge " + midBed);
            check(f, back.getBlockState(MazeBuilder.restSpot(MazeBuilder.midPocket(maze))).isAir(), "the mid rest spot is blocked");
            check(f, FirstRun.room(AnemoneLights.FIRST_ROOM) != null, "the first-run room for the waterway is not registered");

            // ---- 苗床: 灯は 4 つまで摘める。5 つめは出ない
            check(f, AnemoneLights.growable(back, ga) == 4, "a fresh group can grow " + AnemoneLights.growable(back, ga) + " lamps (want 4)");
            for (int i = 0; i < 6; i++) {
                AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            }
            check(f, lamps(a) == 4, "the bed gave " + lamps(a) + " lamps (want exactly 4)");
            check(f, AnemoneLights.growable(back, ga) == 0, "the bed can still grow lamps for a full-handed group");
            check(f, AnemoneLights.growable(back, gb) == 4, "one group's lamps used up another group's bed");

            // ---- ともす: 一つ減る・記録・上書き (その人のグループだけ)・数え石
            ItemStack inHand = a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get())));
            AnemoneLights.tryLight(back, a, stands.get(0), inHand);
            BackGroupData.Record ra = BackGroupData.get(back).of(ga);
            check(f, AnemoneLights.isLit(ra, 0) && AnemoneLights.litCount(ra) == 1 && lamps(a) == 3, "lighting stand 0 did not record / consume (lit=" + AnemoneLights.litCount(ra) + ", held=" + lamps(a) + ")");
            check(f, !back.getBlockState(stands.get(0)).getValue(AnemonePedestalBlock.LIT), "lighting changed the shared world block");
            var overlayA = GroupOverlays.compute(back, ga);
            var overlayB = GroupOverlays.compute(back, gb);
            BlockState shown = overlayA.get(stands.get(0));
            check(f, shown != null && shown.getValue(AnemonePedestalBlock.LIT), "the lit stand is not in the group's overlay");
            check(f, shown != null && shown.getLightEmission() == 15, "the lit stand does not give light 15");
            BlockState tally = overlayA.get(AnemoneLights.TALLY_POS);
            check(f, tally != null && tally.getValue(AnemoneTallyBlock.COUNT) == 1, "the tally stone does not show 1 lit stand");
            check(f, !overlayB.containsKey(stands.get(0)) && !overlayB.containsKey(AnemoneLights.TALLY_POS), "a lit stand leaked into another group's overlay");
            check(f, !AnemoneLights.calmedAt(back, gb, Vec3.atCenterOf(stands.get(0))), "the lamp calms another group");
            // もう一度: 減らない
            AnemoneLights.tryLight(back, a, stands.get(0), a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            check(f, lamps(a) == 3 && AnemoneLights.litCount(ra) == 1, "lighting a lit stand again used a lamp");
            // 苗床: 3 持って 1 つともした → 4 を超えない
            AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            check(f, lamps(a) == 3, "the bed gave a lamp beyond lit + carried = 4");

            // ---- 魚をなだめる: 灯のまわり (6.5) の内側だけ。ともした灯の分だけ
            Vec3 near = Vec3.atCenterOf(stands.get(0)).add(0, 1, 0);
            Vec3 far = Vec3.atCenterOf(stands.get(0)).add(AnemoneLights.CALM_RADIUS + 3, 0, 0);
            check(f, AnemoneLights.calmedAt(back, ga, near) && !AnemoneLights.calmedAt(back, ga, far), "lamp calming radius is wrong near/far");
            Vec3 unlitSpot = Vec3.atCenterOf(stands.get(1)).add(0, 1, 0);
            if (unlitSpot.distanceToSqr(Vec3.atCenterOf(stands.get(0))) > AnemoneLights.CALM_RADIUS * AnemoneLights.CALM_RADIUS) {
                check(f, !AnemoneLights.calmedAt(back, ga, unlitSpot), "an unlit stand calms fish");
            }
            a.setPos(near.x, near.y, near.z);
            b.setPos(near.x, near.y, near.z);
            check(f, AnemoneLights.calms(back, a) && !AnemoneLights.calms(back, b), "calms() is not per group");
            check(f, !AnemoneLights.calms(back.getServer().overworld(), a), "calms() applied outside the whale's back");

            // ---- 戸: 灯が 3 本そろうまで閉じている (そのグループにだけ開く)
            check(f, !GroupGates.isOpen(back, ga, gate), "the exit gate was open before the lamps");
            AnemoneLights.tryLight(back, a, stands.get(2), a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            check(f, !GroupGates.isOpen(back, ga, gate), "the exit gate opened with only 2 lamps");
            AnemoneLights.tryLight(back, a, stands.get(4), a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            check(f, AnemoneLights.litCount(ra) == 3 && GroupGates.isOpen(back, ga, gate), "the exit gate did not open at 3 lamps");
            check(f, !GroupGates.isOpen(back, gb, gate), "the exit gate opened for another group");
            check(f, GroupOverlays.compute(back, ga).get(AnemoneLights.TALLY_POS).getValue(AnemoneTallyBlock.COUNT) == 3, "the tally stone does not show 3");
            // 4 つめまで (台は 5 つ・灯は 4 つ)。5 つめは、灯を持っていても、ともらない
            AnemoneLights.tryLight(back, a, stands.get(3), a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            check(f, AnemoneLights.litCount(ra) == 4 && lamps(a) == 0, "the 4th lamp did not light (lit=" + AnemoneLights.litCount(ra) + ")");
            ItemStack spare = new ItemStack(ModItems.GLOW_ANEMONE.get());
            a.getInventory().add(spare);
            AnemoneLights.tryLight(back, a, stands.get(1), spare);
            check(f, AnemoneLights.litCount(ra) == 4 && !AnemoneLights.isLit(ra, 1) && lamps(a) == 1, "a 5th stand lit although only 4 lamps are allowed");
            a.getInventory().clearContent();

            // ---- 詰まない: どう置いても (4 つとも置いても) 戸は開く。なくしても苗床で戻る
            check(f, GroupGates.isOpen(back, ga, gate), "the gate closed again after 4 lamps");
            AnemoneLights.takeFromBed(back, c, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.takeFromBed(back, c, AnemoneLights.ENTRY_BED_POS);
            check(f, lamps(c) == 2, "group c did not get its lamps");
            // c がともすのは 1 つ。持ち物 1 を落として失った: 手持ち 0・灯 1 → 3 つ摘める
            AnemoneLights.tryLight(back, c, stands.get(0), c.getInventory().getItem(c.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            c.getInventory().clearContent();
            check(f, AnemoneLights.growable(back, gc) == 3, "after losing its lamps a group cannot regrow them (growable=" + AnemoneLights.growable(back, gc) + ")");

            // ---- リセット: そのグループの灯・戸が元に戻る。ほかのグループは変わらない
            AnemoneLights.takeFromBed(back, b, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.tryLight(back, b, stands.get(1), b.getInventory().getItem(b.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            check(f, AnemoneLights.isLit(BackGroupData.get(back).of(gb), 1) && !AnemoneLights.isLit(BackGroupData.get(back).of(gb), 0), "group b's lamp is not independent of a");
            GroupProgress.reset(back, ga);
            BackGroupData.Record ra2 = BackGroupData.get(back).of(ga);
            check(f, AnemoneLights.litCount(ra2) == 0, "the reset did not clear the lamps");
            check(f, !GroupGates.isOpen(back, ga, gate), "the reset did not close the exit gate");
            check(f, !GroupOverlays.compute(back, ga).containsKey(stands.get(0)) && !GroupOverlays.compute(back, ga).containsKey(AnemoneLights.TALLY_POS),
                    "the reset left lit stands in the overlay");
            check(f, AnemoneLights.isLit(BackGroupData.get(back).of(gb), 1), "resetting group a cleared group b's lamp");
            check(f, AnemoneLights.growable(back, ga) == 4, "after the reset a can regrow only " + AnemoneLights.growable(back, ga));

            // ---- 迷路が作り直されるとき: 全グループの灯と戸の上書きを消す
            AnemoneLights.takeFromBed(back, a, AnemoneLights.ENTRY_BED_POS);
            AnemoneLights.tryLight(back, a, stands.get(0), a.getInventory().getItem(a.getInventory().findSlotMatchingItem(new ItemStack(ModItems.GLOW_ANEMONE.get()))));
            AnemoneLights.clearGroups(back, seed);
            check(f, AnemoneLights.litCount(BackGroupData.get(back).of(ga)) == 0 && AnemoneLights.litCount(BackGroupData.get(back).of(gb)) == 0,
                    "clearGroups left lamps in a record");

            // ---- 持ち歩きの光: 持っている間だけ目の位置の水が見えない光になり、離れる・しまうと水に戻る
            BlockPos eye = stands.get(0).offset(0, 2, 0);
            check(f, back.getBlockState(eye).is(Blocks.WATER), "test setup: no water at " + eye);
            a.setPos(eye.getX() + 0.5, eye.getY() - 1.62 + 0.5, eye.getZ() + 0.5);
            AnemoneLights.updateCarryLight(back, a, true);
            probeLight = AnemoneLights.carryLightAt(ga);
            check(f, probeLight != null && back.getBlockState(probeLight).is(Blocks.LIGHT) && back.getBlockState(probeLight).getValue(LightBlock.WATERLOGGED),
                    "the carried light was not placed in the water (" + probeLight + ")");
            AnemoneLights.updateCarryLight(back, a, false);
            check(f, probeLight != null && back.getBlockState(probeLight).is(Blocks.WATER) && AnemoneLights.carryLightAt(ga) == null,
                    "the carried light did not return the water");
            probeLight = null;

            // ---- 初回を終えたグループは、灯なしで戸が開く (既知の道)
            check(f, !GroupGates.isOpen(back, gc, gate), "test setup: gate open for a fresh group");
            FirstRun.onFirstCleared(back, List.of(gc));
            check(f, GroupGates.isOpen(back, gc, gate), "a group that finished its first run still needs lamps for the exit gate");
            check(f, !GroupGates.isOpen(back, gb, gate), "the known-path door opened for a first-run group");
        } finally {
            AnemoneLights.extraMembers = List.of();
            if (probeLight != null) {
                AnemoneLights.updateCarryLight(back, a, false);
            }
            for (UUID id : List.of(ga, gb, gc)) {
                GroupGates.reset(back, id);
                BackGroupData.get(back).remove(id);
            }
        }
        return f;
    }
}
