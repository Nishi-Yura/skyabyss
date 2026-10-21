package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;

// 区画の写し (docs/design/32 の 5 章) の自己テスト。段の名前は "trial slot"。背が建ててある巨神の、写せるセル (relocatable) ごとに:
//  - 写す: 区画に手本と同じブロックが並ぶ (箱の中の決まった点を比べる)。空気に戻すと写しが消える
//  - 動かす: 写しの入口の休み場に偽の人を立たせ、順路の段にして arm → tick を 40 回。人は写しの中に残る (手本の部屋へ動かされない)、
//    そのグループの見かけの上書きは写しの箱の中だけ、そのグループの敵は手本の部屋に残らない。止めると敵が消える
//  - 写せないセルは frameOf が ZERO (手本の部屋を共有)
public final class TrialSlotSelfTest {
    private TrialSlotSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("trial slot", 10, TrialSlotSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int cells = 0;
        int moved = 0;
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null || !TrialCatalog.deckPlaced(titan)) {
                    continue;
                }
                for (TrialCell cell : TrialCells.of(titan)) {
                    if (!cell.runtime()) {
                        continue;
                    }
                    if (!cell.relocatable()) {
                        continue;
                    }
                    cells++;
                    moved += cell(f, back, titan, cell);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial slot crashed", e);
            f.add("crashed: " + e);
        } finally {
            TrialRoute.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial slot PASSED ({} relocatable cell(s) copied and run in a slot, {} entity move(s) checked)", cells, moved);
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial slot FAILED: {}", s);
            }
        }
    }

    private static int cell(List<String> f, ServerLevel back, String titan, TrialCell cell) {
        String id = cell.def().id();
        int slot = TrialSlot.MAX;
        TrialFrame frame = TrialSlot.frame(slot);
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000510" + String.format("%02d", Math.abs(id.hashCode()) % 100)), "slot_" + id));
        UUID group = Groups.keyOf(p);
        BackGroupData.get(back).remove(group);
        TrialDraw.Draw draw = new TrialDraw.Draw(2, List.of(new TrialDraw.Pick(id, Mods.PLAIN)));
        int checks = 0;
        try {
            // 写す
            int copied = TrialSlot.build(back, slot, draw);
            check(f, copied == 1, id + ": the cell was not copied (" + copied + ")");
            AABB box = cell.buildBox();
            int diff = 0;
            for (int i = 0; i < 64; i++) {
                double x = box.minX + (box.maxX - box.minX) * ((i * 37) % 64) / 64.0;
                double y = box.minY + (box.maxY - box.minY) * ((i * 11) % 64) / 64.0;
                double z = box.minZ + (box.maxZ - box.minZ) * ((i * 23) % 64) / 64.0;
                BlockPos t = BlockPos.containing(x, y, z);
                if (back.getBlockState(t) != back.getBlockState(frame.at(t))) {
                    diff++;
                }
            }
            check(f, diff == 0, id + ": the copy differs from the template at " + diff + " of 64 points");
            // 順路の段にする (この試練だけの順路。区画の番号つき)
            BackGroupData.Record r = GroupProgress.of(back, group);
            r.putInt(TrialRoute.STEP, 1);
            r.putString(TrialRoute.ROUTE, id);
            r.putInt(TrialSlot.SLOT, slot);
            check(f, TrialSlot.frameOf(back, group, cell).equals(frame), id + ": frameOf did not give the slot's frame");
            String rest = TrialRoute.entryRest(titan, cell.def(), draw);
            BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
            check(f, at != null, id + ": no entry rest");
            if (at == null) {
                return checks;
            }
            BlockPos inCopy = frame.at(at);
            check(f, !back.getBlockState(inCopy.below()).isAir() && back.getBlockState(inCopy).isAir(), id + ": the entry rest in the copy has no floor: " + inCopy);
            p.setPos(Vec3.atBottomCenterOf(inCopy));
            long now = back.getGameTime();
            TrialCell.Run run = TrialRuntime.armed(back, group, cell, draw, now);
            check(f, run.frame.equals(frame), id + ": the run was armed in the template frame");
            AABB copyArea = frame.box(box).inflate(16);
            AABB templateArea = box.inflate(8);
            for (int t = 0; t < 40; t++) {
                long tick = now + t * 5L;
                List<ServerPlayer> inside = frame.box(cell.ports().area()).contains(p.position()) ? List.of(p) : List.of();
                TrialWindow.run(back, group, cell, run.frame, () -> cell.tick(back, run, inside, tick));
                checks++;
            }
            check(f, copyArea.contains(p.position()), id + ": the player left the copy: " + p.position());
            check(f, !templateArea.contains(p.position()), id + ": the player was moved into the template room");
            Map<BlockPos, BlockState> overlay = GroupOverlays.compute(back, group);
            int outside = 0;
            for (BlockPos q : overlay.keySet()) {
                if (TrialRuntime.isTrialEntrance(q) || TrialSlot.slotAt(q.getX(), q.getZ()) == slot) {
                    continue;
                }
                if (templateArea.contains(Vec3.atCenterOf(q))) {
                    outside++;
                }
            }
            check(f, outside == 0, id + ": " + outside + " overlay block(s) were drawn in the template room");
            List<Entity> left = back.getEntities((Entity) null, templateArea, e -> !(e instanceof ServerPlayer) && group.equals(Groups.keyOf(e)));
            check(f, left.isEmpty(), id + ": " + left.size() + " of the group's entities stayed in the template room");
            TrialRuntime.stopGroup(back, group);
            List<Entity> after = back.getEntities((Entity) null, copyArea, e -> !(e instanceof ServerPlayer) && group.equals(Groups.keyOf(e)));
            check(f, after.isEmpty(), id + ": " + after.size() + " of the group's entities stayed in the copy after stopping");
        } finally {
            TrialRuntime.stopGroup(back, group);
            TrialSlot.clear(back, slot);
            GroupGates.reset(back, group);
            BackGroupData.get(back).remove(group);
            p.setPos(0, 0, 0);
        }
        // 空気に戻した
        AABB box = cell.buildBox();
        BlockPos mid = BlockPos.containing(box.getCenter());
        check(f, back.getBlockState(frame.at(mid)).isAir(), id + ": the copy was not cleared");
        Def d = cell.def();
        check(f, d.id().equals(id), "def");
        return checks;
    }
}
