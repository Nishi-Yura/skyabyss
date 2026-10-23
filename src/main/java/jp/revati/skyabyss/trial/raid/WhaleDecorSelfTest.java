package jp.revati.skyabyss.trial.raid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.mid.MidRooms;
import jp.revati.skyabyss.trial.raid.whale.WhaleDecor;
import jp.revati.skyabyss.trial.raid.whale.WhaleRaidDemo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 岩鯨のレイドの部屋の飾り (WhaleDecor。docs/design/39)。SKYABYSS_SELFTEST_ONLY="raid decor whale"
// 七つの部屋 (WR1〜WR6・WRM) を、飾りなしと飾りありで同じ種から並べて建て、次を見る:
//  - 飾りが仕掛けを上書きしていない (飾りなしの広間の中の空気でないブロック、床のパレットでないブロックが、飾りありでも同じ)
//  - 閉じている (休み場から透ける・通れるマスをたどって、部屋の箱の外へ出られない = 外が見えない)
//  - 費用の上限 (39 の 6 章): 飾り 8 千ブロック・明るさ 12 以上 40・ブロックエンティティ 24・エンティティ 16
//  - 広間の床に立てるマスの大半が、灯りの届く明るさ (ブロックの明るさ 1 以上。3 tick 待つ代わりに光の計算を進める)
public final class WhaleDecorSelfTest {
    private WhaleDecorSelfTest() {
    }

    // 建てた部屋 (明るさを見る次の段で片付ける)
    private static final List<RaidCtx> BUILT = new ArrayList<>();
    private static final List<String> FAILS = new ArrayList<>();
    private static String summary = "";

    public static List<jp.revati.skyabyss.SelfTestRunner.Step> steps() {
        return List.of(jp.revati.skyabyss.SelfTestRunner.Step.once("raid decor whale", WhaleDecorSelfTest::run),
                jp.revati.skyabyss.SelfTestRunner.Step.once("raid decor whale (light)", 40, WhaleDecorSelfTest::light));
    }

    public static void run(MinecraftServer server) {
        List<String> f = FAILS;
        f.clear();
        BUILT.clear();
        ServerLevel level = server.overworld();
        RaidRooms.ensureLoaded();
        StringBuilder sum = new StringBuilder();
        int i = 0;
        for (String id : WhaleRaidDemo.IDS) {
            BlockPos a = new BlockPos(33000 + 128 * i, 200, 33000);
            BlockPos b = new BlockPos(33000 + 128 * i, 200, 33200);
            i++;
            RaidCtx plain = null;
            RaidCtx deco = null;
            try {
                WhaleDecor.enabled = false;
                plain = build(level, id, a);
                WhaleDecor.enabled = true;
                deco = build(level, id, b);
                if (plain == null || deco == null || plain.cell() == null || deco.cell() == null) {
                    f.add(id + ": could not be built");
                    continue;
                }
                check(level, id, plain, deco, f);
                WhaleDecor.Stats s = WhaleDecor.stats(id);
                if (s == null) {
                    f.add(id + ": no decoration stats");
                } else {
                    sum.append(id).append(" [").append(s).append("] ");
                    if (s.decor() > 8000) {
                        f.add(id + ": " + s.decor() + " decoration blocks (limit 8000)");
                    }
                    if (s.bright() > 40) {
                        f.add(id + ": " + s.bright() + " bright blocks (limit 40)");
                    }
                    if (s.blockEntities() > 24) {
                        f.add(id + ": " + s.blockEntities() + " block entities (limit 24)");
                    }
                    if (s.entities() > 16) {
                        f.add(id + ": " + s.entities() + " entities (limit 16)");
                    }
                    if (s.decor() < 500) {
                        f.add(id + ": only " + s.decor() + " decoration blocks (the room was not decorated)");
                    }
                }
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST raid decor whale crashed on " + id, e);
                f.add(id + ": crashed: " + e);
            } finally {
                WhaleDecor.enabled = true;
                for (RaidCtx c : new RaidCtx[] {plain, deco}) {
                    if (c != null && c.cell() != null) {
                        BUILT.add(c);
                    }
                }
            }
        }
        summary = sum.toString().trim();
    }

    // 二つ目の段 (光の計算が済んだあと): 飾りありの部屋の床の明るさを見て、全部片付ける
    public static void light(MinecraftServer server) {
        List<String> f = FAILS;
        ServerLevel level = server.overworld();
        for (int i = 1; i < BUILT.size(); i += 2) {
            RaidCtx dc = BUILT.get(i);
            if (dc.cell().origin().getZ() != 33200) {
                continue;
            }
            litFloor(level, dc.roomId(), dc.cell(), f);
        }
        for (RaidCtx c : BUILT) {
            RaidKit.clear(level, c.cell().bounds());
            RaidKit.release(level, c.group());
        }
        BUILT.clear();
        String sum = summary;
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid decor whale PASSED ({} rooms: gameplay blocks untouched, sealed, within budget, floor lit): {}", WhaleRaidDemo.IDS.length,
                    sum.toString().trim());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid decor whale FAILED: {}", x));
        }
    }

    private static RaidCtx build(ServerLevel level, String id, BlockPos o) {
        RaidRoom room = id.equals(jp.revati.skyabyss.trial.raid.mid.WhaleMidRoom.ID) ? MidRooms.create(Titans.STONE_WHALE) : RaidRooms.create(id);
        if (room == null) {
            return null;
        }
        UUID g = UUID.nameUUIDFromBytes(("raid decor whale " + id + o.toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RaidCtx ctx = RaidCtx.forSolo(level, g, Titans.STONE_WHALE, id, 1, o, 77L);
        room.build(ctx);
        return ctx;
    }

    private static void check(ServerLevel level, String id, RaidCtx plain, RaidCtx deco, List<String> f) {
        RaidKit.Cell pc = plain.cell();
        RaidKit.Cell dc = deco.cell();
        RaidKit.Palette pal = pc.palette();
        // 1. 仕掛けのブロックはそのまま
        int changed = 0;
        AABB pb = pc.bounds();
        int ox = pc.origin().getX();
        int oy = pc.origin().getY();
        int oz = pc.origin().getZ();
        for (BlockPos p : BlockPos.betweenClosed((int) pb.minX, (int) pb.minY, (int) pb.minZ, (int) pb.maxX - 1, (int) pb.maxY - 1, (int) pb.maxZ - 1)) {
            int x = p.getX() - ox;
            int y = p.getY() - oy;
            int z = p.getZ() - oz;
            BlockState was = level.getBlockState(p);
            boolean hall = Math.abs(x) <= pc.hx() && Math.abs(z) <= pc.hz() && y >= 1 && y <= pc.height();
            boolean floor = Math.abs(x) <= pc.hx() && Math.abs(z) <= pc.hz() && y == 0;
            boolean gameplay = hall && !was.isAir() || floor && was != pal.floor() && was != pal.trim() || was == pal.gate();
            if (!gameplay) {
                continue;
            }
            BlockState now = level.getBlockState(dc.origin().offset(x, y, z));
            if (now.getBlock() != was.getBlock()) {
                if (changed++ < 5) {
                    f.add(id + ": gameplay block at " + x + "," + y + "," + z + " changed from " + was + " to " + now);
                }
            }
        }
        // 2. 閉じている: 休み場から、透ける (不透明な満ちたブロックでない) マスをたどる
        AABB box = dc.bounds();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(dc.rest());
        seen.add(dc.rest().asLong());
        boolean leak = false;
        while (!q.isEmpty() && !leak && seen.size() < 200000) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong())) {
                    continue;
                }
                if (level.getBlockState(n).isSolidRender(level, n)) {
                    continue;
                }
                if (!box.contains(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5)) {
                    f.add(id + ": the room is open to the outside at " + n.toShortString() + " (from " + p.toShortString() + ")");
                    leak = true;
                    break;
                }
                q.add(n);
            }
        }
    }

    // 3. 床の明るさ: 広間の立てるマスの 9 割以上がブロックの明るさ 1 以上
    private static void litFloor(ServerLevel level, String id, RaidKit.Cell dc, List<String> f) {
        int stand = 0;
        int lit = 0;
        for (int x = -dc.hx(); x <= dc.hx(); x++) {
            for (int z = -dc.hz(); z <= dc.hz(); z++) {
                BlockPos p = dc.floor(x, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()) {
                    stand++;
                    if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p) >= 1) {
                        lit++;
                    }
                }
            }
        }
        if (stand > 0 && lit * 10 < stand * 9) {
            f.add(id + ": only " + lit + " of " + stand + " floor spots are lit");
        }
    }
}
