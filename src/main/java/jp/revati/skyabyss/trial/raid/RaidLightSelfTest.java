package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.mid.MidRooms;
import jp.revati.skyabyss.trial.raid.mid.WhaleMidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

// 全部の部屋 (登録した二十四の部屋と四体の中ボスの間) の床の明るさ。SKYABYSS_SELFTEST_ONLY="raid light"
// 夜の部屋は空が届かないので、灯りは部屋の中の灯りだけ。床の立てるマスごとに、ブロックの明るさを見る:
//  - 最も暗いマス (min) と平均 (avg)
//  - 明るさ 4 以上・8 以上の割合 (読める・はっきり見える)
// 閾値は RaidLightSelfTest.MIN_FLOOR / MIN_SHARE_4 / MIN_SHARE_8 (docs/design/39 の 3.7 節)。部屋ごとの内訳はログに一行ずつ出す。
public final class RaidLightSelfTest {
    private RaidLightSelfTest() {
    }

    // 床の最低の明るさ (どのマスも、これ以上)。暗い床は敵が湧いて見えない
    static final int MIN_FLOOR = 3;
    // 明るさ 4 以上の床の割合 (%) と、明るさ 8 以上の床の割合 (%)
    static final int MIN_SHARE_4 = 95;
    static final int MIN_SHARE_8 = 60;
    // 暗い部屋 (Cell.dark: 暗さが遊びの部屋。TR1 の真珠の広間) は、灯りを持つ人がいない所の床がこれ以下のまま
    // 5: 入口・出口の間の灯り (出口の枠の真珠の灯り 15 など) が、戸の鉄格子を抜けて広間の手前に一段だけ届く (実測 5)。奥の床は 4 以下 (TideRaidSelfTest の見る所)
    static final int MAX_DARK_FLOOR = 5;

    private static final List<RaidCtx> BUILT = new ArrayList<>();
    private static final List<String> FAILS = new ArrayList<>();
    private static final List<String> LINES = new ArrayList<>();
    private static int DARK_ROOMS;

    public static List<Step> steps() {
        return List.of(Step.once("raid light", RaidLightSelfTest::build),
                Step.once("raid light (measure)", 40, RaidLightSelfTest::measure));
    }

    public static void build(MinecraftServer server) {
        FAILS.clear();
        LINES.clear();
        BUILT.clear();
        DARK_ROOMS = 0;
        ServerLevel level = server.overworld();
        RaidRooms.ensureLoaded();
        int i = 0;
        for (Map.Entry<String, String> e : RaidRooms.all().entrySet()) {
            build(level, e.getKey(), e.getValue(), i++);
        }
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
            String id = MidRooms.idOf(titan);
            if (id != null) {
                build(level, id, titan, i++);
            }
        }
    }

    private static void build(ServerLevel level, String id, String titan, int i) {
        BlockPos o = new BlockPos(60000 + 128 * i, 200, 60000);
        RaidRoom room = MidRooms.isMid(id) ? MidRooms.create(titan) : RaidRooms.create(id);
        if (room == null) {
            FAILS.add(id + ": could not be created");
            return;
        }
        UUID g = UUID.nameUUIDFromBytes(("raid light " + id + o.toShortString()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RaidCtx ctx = RaidCtx.forSolo(level, g, titan, id, 1, o, 91L);
        try {
            room.build(ctx);
            BUILT.add(ctx);
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST raid light crashed on " + id, ex);
            FAILS.add(id + ": crashed: " + ex);
        }
    }

    // 階段の向き (docs/design/39 の段の決まり): 下付きの段は、向き (低い側) の前が壁でなく、後ろ (高い側) が支えられている。
    // 後ろが塞がり・段の続きのどれでもない = 空中へ上がる段。前が壁 = 向きが逆。上付きの段は天井の飾りとして見ない
    private static void checkStairs(ServerLevel level, RaidCtx ctx) {
        AABB b = ctx.cell().bounds();
        BlockPos o = ctx.origin();
        for (BlockPos p : BlockPos.betweenClosed((int) b.minX, (int) b.minY, (int) b.minZ, (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1)) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof StairBlock) || st.getValue(StairBlock.HALF) != Half.BOTTOM) {
                continue;
            }
            Direction f = st.getValue(StairBlock.FACING);
            BlockPos back = p.relative(f.getOpposite());
            BlockPos front = p.relative(f);
            BlockState bs = level.getBlockState(back);
            boolean backSupported = bs.getBlock() instanceof StairBlock || bs.isSolidRender(level, back);
            boolean frontWall = level.getBlockState(front).isSolidRender(level, front);
            String where = ctx.roomId() + " stair at " + (p.getX() - o.getX()) + "," + (p.getY() - o.getY()) + "," + (p.getZ() - o.getZ()) + " facing " + f;
            if (!backSupported) {
                FAILS.add(where + ": the high side is open (the step climbs into the air)");
            }
            if (frontWall) {
                FAILS.add(where + ": the low side is a wall (the stair faces the wrong way)");
            }
            // 段の続き: 上る先の段が直角に向きを変えるのは角 (正しい形もある)。目で見る一覧として出す (失敗にはしない)
            BlockPos ahead = p.relative(f.getOpposite());
            BlockState ab = level.getBlockState(ahead);
            if (ab.getBlock() instanceof StairBlock && ab.getValue(StairBlock.HALF) == Half.BOTTOM
                    && ab.getValue(StairBlock.FACING) != f && ab.getValue(StairBlock.FACING) != f.getOpposite()) {
                LINES.add(where + ": the next step turns to " + ab.getValue(StairBlock.FACING) + " (a corner?)");
            }
        }
    }

    public static void measure(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (RaidCtx ctx : BUILT) {
            RaidKit.Cell c = ctx.cell();
            if (c == null) {
                continue;
            }
            checkStairs(level, ctx);
            int stand = 0;
            int lit4 = 0;
            int lit8 = 0;
            int min = 15;
            int max = 0;
            String minAt = "";
            int sum = 0;
            for (int x = -c.hx(); x <= c.hx(); x++) {
                for (int z = -c.hz(); z <= c.hz(); z++) {
                    BlockPos p = c.floor(x, z);
                    if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isSolid()) {
                        continue;
                    }
                    // 四方と上を塞がれた窪み (張り出しの下の袋小路) は歩けない所なので、床の数えには入れない
                    if (!level.getBlockState(p.above()).isAir() && !level.getBlockState(p.north()).isAir() && !level.getBlockState(p.south()).isAir()
                            && !level.getBlockState(p.east()).isAir() && !level.getBlockState(p.west()).isAir()) {
                        continue;
                    }
                    int b = level.getBrightness(LightLayer.BLOCK, p);
                    stand++;
                    sum += b;
                    // 暗い部屋の戸の近く (三マス以内) は、入口・出口の間の灯りが戸の鉄格子を抜けて届く。その分は数えない (広間の奥を見る)
                    if (!c.dark() || (z >= -c.hz() + 3 && z <= c.hz() - 3)) {
                        max = Math.max(max, b);
                    }
                    if (b < min) {
                        min = b;
                        minAt = "" + x + "," + z;
                    }
                    if (b >= 4) {
                        lit4++;
                    }
                    if (b >= 8) {
                        lit8++;
                    }
                }
            }
            if (stand == 0) {
                FAILS.add(ctx.roomId() + ": no floor to stand on");
                continue;
            }
            int share4 = lit4 * 100 / stand;
            int share8 = lit8 * 100 / stand;
            String line = String.format("%s min=%d avg=%.1f >=4=%d%% >=8=%d%%", ctx.roomId(), min, sum / (double) stand, share4, share8);
            LINES.add(line + (c.dark() ? " (dark room: stays dark, max " + max + ")" : ""));
            if (c.dark()) {
                // 暗さが遊び: 床を明るくしない。灯りは持つ人のまわりだけ (テストは灯りを持つ人を置かないので、どの床も暗い)
                DARK_ROOMS++;
                if (max > MAX_DARK_FLOOR) {
                    // 原因の目安: 部屋の中で光る物 (発光 1 以上) を、原点からの位置で並べる
                    StringBuilder emitters = new StringBuilder();
                    int shown = 0;
                    for (BlockPos p : BlockPos.betweenClosed((int) c.bounds().minX, (int) c.bounds().minY, (int) c.bounds().minZ,
                            (int) c.bounds().maxX - 1, (int) c.bounds().maxY - 1, (int) c.bounds().maxZ - 1)) {
                        BlockState st = level.getBlockState(p);
                        if (st.getLightEmission(level, p) > 0 && shown++ < 16) {
                            emitters.append(' ').append(p.getX() - c.origin().getX()).append(',').append(p.getY() - c.origin().getY())
                                    .append(',').append(p.getZ() - c.origin().getZ()).append('=').append(st.getBlock()).append(':')
                                    .append(st.getLightEmission(level, p));
                        }
                    }
                    FAILS.add(ctx.roomId() + ": a dark room is lit: floor light " + max + " (max " + MAX_DARK_FLOOR + " without a carrier); emitters:" + emitters);
                }
                continue;
            }
            if (min < MIN_FLOOR) {
                BlockPos dark = c.origin().offset(Integer.parseInt(minAt.split(",")[0]), 1, Integer.parseInt(minAt.split(",")[1]));
                FAILS.add(ctx.roomId() + ": a floor spot at (x,z) " + minAt + " from the origin has light " + min + " (need " + MIN_FLOOR + "); above: "
                        + level.getBlockState(dark.above()) + " / " + level.getBlockState(dark.above(2)) + " / east " + level.getBlockState(dark.east()) + " / west " + level.getBlockState(dark.west())
                        + " / north " + level.getBlockState(dark.north()) + " / south " + level.getBlockState(dark.south()));
            }
            if (share4 < MIN_SHARE_4) {
                FAILS.add(ctx.roomId() + ": only " + share4 + "% of the floor is at light 4 or more (need " + MIN_SHARE_4 + ")");
            }
            if (share8 < MIN_SHARE_8) {
                FAILS.add(ctx.roomId() + ": only " + share8 + "% of the floor is at light 8 or more (need " + MIN_SHARE_8 + ")");
            }
        }
        for (String line : LINES) {
            SkyAbyss.LOGGER.info("SELFTEST raid light: {}", line);
        }
        for (RaidCtx c : BUILT) {
            RaidKit.clear(level, c.cell().bounds());
            RaidKit.release(level, c.group());
        }
        BUILT.clear();
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid light PASSED ({} rooms: every floor spot lit; {} dark rooms stay dark)", LINES.size() - DARK_ROOMS, DARK_ROOMS);
        } else {
            FAILS.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid light FAILED: {}", x));
        }
    }
}
