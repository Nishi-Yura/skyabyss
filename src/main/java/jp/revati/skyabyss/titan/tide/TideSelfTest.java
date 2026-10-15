package jp.revati.skyabyss.titan.tide;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 潮殻の巨神の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide PASSED / FAILED (サーバーを止めるのは TitanBackSelfTest)。
// 干潮の刻、潮門の口の判定、戻る先、一体だけの決まりと状態の引き継ぎ、チケット、記録の保存と読み込み、背中の仮の島、背中の決まり
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideSelfTest {
    // 岩鯨の自己テスト (4000, 4000) と重ならない所
    private static final int TEST_X = -4000;
    private static final int TEST_Z = 4000;
    private static int countdown = -1;

    private TideSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        for (int dx = 0; dx <= 4; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, true);
        }
        countdown = 50;
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (countdown < 0 || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (--countdown == 0) {
            countdown = -1;
            List<String> failures = new ArrayList<>();
            try {
                run(level, failures);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST tide crashed", e);
                failures.add("crashed: " + e);
            }
            for (int dx = 0; dx <= 4; dx++) {
                level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, false);
            }
            if (failures.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST tide PASSED (low tide times, gate mouth, landing, one titan, carried state, ticket, save, tide back island, rules)");
            } else {
                failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide FAILED: {}", f));
            }
        }
    }

    private static void run(ServerLevel level, List<String> failures) {
        checkTimes(failures);
        checkRules(failures);
        checkBack(level, failures);
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        try {
            checkKeeper(level, data, failures);
        } finally {
            TideShellEntity t = TideKeeper.KEEPER.current(level);
            if (t != null) {
                t.discard();
            }
            TideKeeper.KEEPER.releaseTicket(level);
            data.restoreSlot(TideKeeper.SLOT, saved, level.registryAccess());
        }
        // 岩鯨の記録が変わっていない (潮殻の記録は別の場所)
        CompoundTag after = data.snapshot(level.registryAccess());
        for (String key : List.of("StoneWhaleSpawned", "WhaleId", "LastX", "State")) {
            if (!java.util.Objects.equals(saved.get(key), after.get(key))) {
                failures.add("the stone whale's record changed: " + key);
            }
        }
    }

    // 干潮の刻: 朝 23000〜2600、夕 11000〜14600。刻の 1200 前から寄る場所を選ぶ
    private static void checkTimes(List<String> failures) {
        long[] low = {23000, 23999, 24000, 0, 2599, 11000, 14599, 24000 * 5 + 1000, 24000 * 3 + 12000};
        long[] high = {2600, 6000, 10999, 14600, 18000, 22999, 21800};
        for (long t : low) {
            if (!TideOcean.isLowTide(t)) {
                failures.add("expected low tide at " + t);
            }
        }
        for (long t : high) {
            if (TideOcean.isLowTide(t)) {
                failures.add("expected no low tide at " + t);
            }
        }
        if (!TideOcean.isGathering(9800) || TideOcean.isGathering(9799) || !TideOcean.isGathering(21800) || TideOcean.isGathering(14600)) {
            failures.add("the gathering window (1200 ticks before low tide) is wrong");
        }
        if (TideOcean.ticksUntilLowTide(10000) != 1000 || TideOcean.ticksUntilLowTide(12000) != 0 || TideOcean.ticksUntilLowTide(14600) != 8400) {
            failures.add("ticksUntilLowTide is wrong: " + TideOcean.ticksUntilLowTide(10000) + " " + TideOcean.ticksUntilLowTide(14600));
        }
        if (TideOcean.windowId(23500) != TideOcean.windowId(24000 + 2000) || TideOcean.windowId(12000) == TideOcean.windowId(24000)) {
            failures.add("a low tide that crosses midnight must be one window");
        }
    }

    private static void checkRules(List<String> failures) {
        if (!TitanBacks.isBack(TideTravel.TIDE_BACK) || !TitanBacks.isBack(TitanTravel.TITAN_BACK) || TitanBacks.isBack(Level.OVERWORLD)) {
            failures.add("the back dimension list is wrong");
        }
        if (!TitanBacks.extraBanned(TideTravel.TIDE_BACK, new ItemStack(Items.OAK_BOAT))
                || TitanBacks.extraBanned(TitanTravel.TITAN_BACK, new ItemStack(Items.OAK_BOAT))
                || TitanBacks.extraBanned(TideTravel.TIDE_BACK, new ItemStack(Items.BREAD))) {
            failures.add("boats must be banned only on the tide back");
        }
    }

    // 背中の仮の島: 到着地点に立てる、戻りの門の口が開いていて判定に入る、到着地点は門の判定の外
    private static void checkBack(ServerLevel overworld, List<String> failures) {
        ServerLevel back = overworld.getServer().getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            failures.add("tide_back dimension is missing");
            return;
        }
        BlockPos a = TideBackBuilder.arrival();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                back.setChunkForced((a.getX() >> 4) + dx, (a.getZ() >> 4) + dz, true);
            }
        }
        try {
            TitanBackData data = TitanBackData.get(back);
            TideBackBuilder.build(back, data);
            if (data.needsBuild(TideBackBuilder.LAYOUT_VERSION)) {
                failures.add("the tide back layout version was not recorded");
            }
            if (!back.getBlockState(a.below()).isSolidRender(back, a.below()) || !back.getBlockState(a).isAir() || !back.getBlockState(a.above()).isAir()) {
                failures.add("cannot stand at the tide back arrival " + a);
            }
            if (TideBackBuilder.inReturnGate(a.getX() + 0.5, a.getY(), a.getZ() + 0.5)) {
                failures.add("the arrival is inside the return gate");
            }
            BlockPos g = new BlockPos(0, TideBackBuilder.GATE_Y0, TideBackBuilder.GATE_Z);
            if (!TideBackBuilder.inReturnGate(0.5, g.getY(), g.getZ() + 0.5)) {
                failures.add("the return gate mouth is not detected");
            }
            for (int y = 0; y < 2; y++) {
                if (!back.getBlockState(g.above(y)).isAir() || !back.getBlockState(g.above(y).south()).isAir()) {
                    failures.add("the return gate mouth is blocked at " + g.above(y));
                }
            }
            if (!back.getBlockState(g.below()).isSolidRender(back, g.below())) {
                failures.add("the return gate has no floor");
            }
            // 到着地点から門へ、平らに歩ける (間の地面がふさがっていない)
            for (int z = a.getZ(); z < TideBackBuilder.GATE_Z; z++) {
                int top = TideBackBuilder.surfaceY(0, z);
                if (top + 1 - a.getY() > 1 && z > a.getZ()) {
                    failures.add("a step up of more than one block on the way to the gate at z=" + z);
                    break;
                }
            }
            // 島の外は海 (落ちたら泳げる)
            BlockPos sea = new BlockPos(TideBackBuilder.ISLAND_R + 6, TideBackBuilder.SEA - 1, 0);
            back.getChunk(sea);
            if (!back.getFluidState(sea).isSource()) {
                failures.add("no sea around the tide back island at " + sea);
            }
        } finally {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 2; dz++) {
                    back.setChunkForced((a.getX() >> 4) + dx, (a.getZ() >> 4) + dz, false);
                }
            }
        }
    }

    private static void checkKeeper(ServerLevel level, TitanWorldData data, List<String> failures) {
        int x = TEST_X + 8;
        int z = TEST_Z + 8;
        TideShellEntity first = TideKeeper.KEEPER.spawn(level, x, z, null);
        if (TideKeeper.KEEPER.current(level) != first) {
            failures.add("the spawned tide shell is not the current one");
        }
        if (TideKeeper.KEEPER.ticketPos() == null || !TideKeeper.KEEPER.ticketPos().equals(first.chunkPosition())) {
            failures.add("no ticket on the tide shell's chunk");
        }
        if (data.slot(TideKeeper.SLOT).id == null || !first.getUUID().equals(data.slot(TideKeeper.SLOT).id)
                || first.getUUID().equals(data.whaleId())) {
            failures.add("the tide shell is not recorded in its own slot");
        }
        // 二体目は加わらない
        TideShellEntity stray = TideSetup.TIDE_SHELL.get().create(level);
        if (stray != null) {
            stray.moveTo(x + 20, 40, z, 0f, 0f);
            if (level.addFreshEntity(stray) || level.getEntity(stray.getUUID()) != null) {
                failures.add("a second tide shell was allowed to join");
            }
        }
        // 潮門の口の判定 (向きを変えても、門の口の真ん中は入り、出る所と体の中心は入らない)
        for (float yaw : new float[] {0f, 37f, 90f, 200f, -135f}) {
            first.setYRot(yaw);
            Vec3 mouth = first.gateCenter();
            if (!inMouth(first, mouth)) {
                failures.add("the gate center is not in the mouth at yaw " + yaw);
            }
            Vec3 exit = first.gateExit();
            if (inMouth(first, new Vec3(exit.x, first.getY() + 3.5, exit.z))) {
                failures.add("the gate exit is inside the mouth at yaw " + yaw);
            }
            if (inMouth(first, first.position().add(0, 4, 0))) {
                failures.add("the body center is inside the mouth at yaw " + yaw);
            }
            Vec3 l = first.local(mouth.x, mouth.y, mouth.z);
            if (Math.abs(l.x - TideShellEntity.GATE_SIDE) > 1e-3 || Math.abs(l.z + TideShellEntity.GATE_BACK) > 1e-3) {
                failures.add("local() does not invert offset() at yaw " + yaw + ": " + l);
            }
        }
        // 休んでいれば、背中から戻る先は門の前の水の上 (門の口の外)
        first.restHere();
        if (!first.isResting()) {
            failures.add("restHere did not rest");
        }
        TideTravel.Landing landing = TideTravel.landing(level);
        if (landing.pos().distanceTo(first.gateExit()) > 1e-6 || Float.isNaN(landing.yaw())) {
            failures.add("leaving a resting titan does not land at the gate exit: " + landing);
        }
        if (Math.abs(first.getY() - (level.getSeaLevel() - TideShellEntity.REST_DRAFT)) > 1e-6) {
            failures.add("restHere is not at the rest height: " + first.getY());
        }
        // 状態の引き継ぎ: 最後に休んだ場所と刻は残り、巡航から始める
        CompoundTag state = first.saveState();
        TideShellEntity second = TideKeeper.KEEPER.spawn(level, x + 64, z, state);
        if (!first.isRemoved()) {
            failures.add("calling the tide shell again left the old one");
        }
        if (TideKeeper.KEEPER.current(level) != second || !second.getUUID().equals(data.slot(TideKeeper.SLOT).id)) {
            failures.add("the new tide shell is not registered");
        }
        if (second.mode() != TideShellEntity.Mode.CRUISE || second.lastRestSpot() == null
                || Math.abs(second.lastRestSpot().x - x) > 1e-6) {
            failures.add("the carried state is wrong: " + second.saveState());
        }
        if (second.saveState().getInt("ForcedTicks") != 0) {
            failures.add("a forced rest was carried over");
        }
        // 記録の保存と読み込み (岩鯨の記録と並んで残る)
        TitanWorldData copy = new TitanWorldData();
        copy.restore(data.snapshot(level.registryAccess()), level.registryAccess());
        if (!second.getUUID().equals(copy.slot(TideKeeper.SLOT).id) || !java.util.Objects.equals(copy.whaleId(), data.whaleId())
                || !copy.slot(TideKeeper.SLOT).state.contains("LastRestX")) {
            failures.add("the titan record did not survive save and load");
        }
        long count = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof TideShellEntity) {
                count++;
            }
        }
        if (count != 1) {
            failures.add(count + " tide shells are loaded");
        }
        // 光を測る所は海面より上 (水の底で真っ黒に描かれない)
        if (second.getLightProbePosition(1f).y < level.getSeaLevel()) {
            failures.add("the light probe is under the sea");
        }
    }

    private static boolean inMouth(TideShellEntity t, Vec3 p) {
        Entity probe = new net.minecraft.world.entity.item.ItemEntity(t.level(), p.x, p.y, p.z, ItemStack.EMPTY);
        return t.inGateMouth(probe);
    }
}
