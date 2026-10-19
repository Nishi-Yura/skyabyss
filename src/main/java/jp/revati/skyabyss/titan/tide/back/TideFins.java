package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// ひれの道 (26 の 9 章): 前ひれの上に、貝の間 2 つと先端の台。西のひれは珊瑚の庭の台 c の壁から、東のひれは潮見の間の広間の東の端から、トンネルで入る。
// 貝の間は一つの決まりを教え、次の間でひねる (どちらも任意の翼。先端に碑と宝):
//   西「貝の囁き」  1 の間: 三つの貝を、低い声から高い声へ順に叩く / 2 の間: 同じ決まりを、潮がひねる (引き潮は昇り、満ち潮は降りる)
//   東「潮の灯り」  1 の間: 壁の四つの色を、北から南へ読んで同じ色の貝を叩く / 2 の間: 壁を南から北へ読む (鏡)
// 間違えると最初からやり直し (罰はない)。解くとその間の奥の戸 (封印の扉) がそのグループにだけ開く。一人でも解ける (人数で変わらない)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideFins {
    private TideFins() {
    }

    enum Mode {
        ASC, ASC_TIDE, READ, READ_REV
    }

    // 室 (id・向き・奥行きの範囲 u1〜u2・貝の台の u と z・色・声の高さの順位・壁の読み取り (色の番号。READ 系だけ)・奥の戸)
    record Room(String id, int side, int u1, int u2, Mode mode, int pedU, int[] pedZ, int[] colors, int[] rank, int[] pattern) {
        int x(int u) {
            return side * u;
        }

        // 戸 (奥の壁、中ほどの幅 5・高さ 4)
        List<BlockPos> gate() {
            List<BlockPos> list = new ArrayList<>();
            int gu = u2 + 1;
            for (int z = -22; z <= -18; z++) {
                for (int y = FIN_FLOOR + 1; y <= FIN_FLOOR + 4; y++) {
                    list.add(new BlockPos(x(gu), y, z));
                }
            }
            return list;
        }

        BlockPos pedestal(int i) {
            return new BlockPos(x(pedU), FIN_FLOOR + 1, pedZ[i]);
        }

        // i 番目に叩く貝 (添字)。dir は ASC_TIDE の向き (+1 昇り / -1 降り)
        int expected(int k, int dir) {
            int n = pedZ.length;
            return switch (mode) {
                case ASC -> indexOfRank(k);
                case ASC_TIDE -> indexOfRank(dir > 0 ? k : n - 1 - k);
                case READ -> pattern[k];
                case READ_REV -> pattern[n - 1 - k];
            };
        }

        int indexOfRank(int r) {
            for (int i = 0; i < rank.length; i++) {
                if (rank[i] == r) {
                    return i;
                }
            }
            return 0;
        }

        BlockPos center() {
            return new BlockPos(x((u1 + u2) / 2), FIN_FLOOR + 1, -20);
        }
    }

    // 室の床 (版 20 の甲羅の高さ。前ひれの面は 70〜68)・奥行きの範囲
    public static final int FIN_FLOOR = 71;
    public static final int FIN_Z1 = -29;
    public static final int FIN_Z2 = -11;
    public static final int ROOM1_U1 = 120;
    public static final int ROOM1_U2 = 138;
    public static final int ROOM2_U1 = 140;
    public static final int ROOM2_U2 = 158;
    public static final int TIP_FLOOR = 65;
    public static final int TIP_U = 175;
    static final BlockState[] CONCRETE = {Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.MAGENTA_CONCRETE.defaultBlockState(),
            Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(), Blocks.WHITE_CONCRETE.defaultBlockState()};

    public static final List<Room> ROOMS = List.of(
            new Room("fin_w1", -1, ROOM1_U1, ROOM1_U2, Mode.ASC, 128, new int[] {-24, -20, -16}, new int[] {0, 1, 2}, new int[] {2, 0, 1}, null),
            new Room("fin_w2", -1, ROOM2_U1, ROOM2_U2, Mode.ASC_TIDE, 148, new int[] {-26, -22, -18, -14}, new int[] {3, 4, 0, 1}, new int[] {1, 3, 0, 2}, null),
            new Room("fin_e1", 1, ROOM1_U1, ROOM1_U2, Mode.READ, 128, new int[] {-26, -22, -18, -14}, new int[] {1, 3, 0, 4}, new int[] {0, 1, 2, 3}, new int[] {2, 0, 3, 1}),
            new Room("fin_e2", 1, ROOM2_U1, ROOM2_U2, Mode.READ_REV, 148, new int[] {-28, -24, -20, -16, -12}, new int[] {1, 3, 0, 4, 5}, new int[] {0, 1, 2, 3, 4},
                    new int[] {3, 4, 1, 0, 2}));

    // 先端の台の宝と碑 (TideCaches に登録)・トンネルの口の碑
    public static BlockPos tipCenter(int side) {
        return new BlockPos(side * TIP_U, TIP_FLOOR, -20);
    }

    // ---------------------------------------------------------------- 建てる
    static void build(TideKit kit) {
        for (int side : new int[] {-1, 1}) {
            tunnel(kit, side);
            plaza(kit, side);
            tip(kit, side);
        }
        for (Room r : ROOMS) {
            room(kit, r);
        }
    }

    // トンネル: 西は庭の台 c の壁 (x -84、床 65 = 台の外から 2 段目) から、東は潮見の間の広間の東の端 (x 99、床 74) から、甲羅のへりまで
    private static void tunnel(TideKit kit, int side) {
        kit.zone(TideZone.FIN);
        int zc = side < 0 ? -11 : -12;
        for (int u = side < 0 ? 83 : 99; u <= 119; u++) {
            int f = tunnelFloor(side, u);
            boolean open = u >= 108;
            for (int z = zc - 3; z <= zc + 3; z++) {
                boolean wall = Math.abs(z - zc) == 3;
                int x = side * u;
                int top = open ? 81 : f + 6;
                for (int y = f - 1; y <= top; y++) {
                    BlockState s;
                    if (wall) {
                        if (open && y > f + 1) {
                            continue;
                        }
                        s = y == f + 3 && Math.floorMod(u, 6) == 0 ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (y < f) {
                        s = TideKit.BRICK;
                    } else if (y == f) {
                        s = stairAt(kit, side, u, x, y, z);
                        if (s == null) {
                            continue;
                        }
                    } else if (!open && y == top) {
                        s = Math.floorMod(u, 6) == 3 && Math.abs(z - zc) <= 1 ? TideKit.LAMP : TideZone.FIN.dark;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 灯りの道 (床に埋めた灯り)
        List<int[]> cells = new ArrayList<>();
        for (int u = side < 0 ? 83 : 99; u <= 118; u++) {
            cells.add(new int[] {side * u, zc});
        }
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            int f = tunnelFloor(side, Math.abs(c[0]));
            if (i % 2 == 0 && kit.get(c[0], f, c[1]).getBlock() != Blocks.AIR && !(kit.get(c[0], f, c[1]).getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
                kit.set(c[0], f, c[1], TideKit.LAMP);
            }
        }
    }

    // トンネルの床の高さ (u = 原点からの距離)。西: 99 まで 65、100〜105 で 66〜71、以降 71。東: 99 は 74、100〜102 で 73〜71、以降 71
    static int tunnelFloor(int side, int u) {
        if (side < 0) {
            // 台の外 (満ちても 67 まで) から 3 段で 68 へ上がり、68 のまま 99 まで。そこからひれの面の 71 へ
            if (u <= 84) {
                return 65;
            }
            if (u <= 87) {
                return 65 + (u - 84);
            }
            return u <= 99 ? 68 : Math.min(FIN_FLOOR, 68 + (u - 99));
        }
        return u <= 99 ? 74 : Math.max(FIN_FLOOR, 74 - (u - 99));
    }

    // 段になる所は階段 (上りの向きは広間・台の側へ)
    private static BlockState stairAt(TideKit kit, int side, int u, int x, int y, int z) {
        int here = tunnelFloor(side, u);
        int toward = tunnelFloor(side, u - 1);
        boolean stepUp = side < 0 ? here > toward : here < toward;
        // 西: u が増えると上がる (台から離れるほど高い) → 登る向きは西 (-x)。東: u が増えると下がる → 登る向きは西 (-x)
        if (side < 0 ? here > toward : here < toward) {
            kit.set(x, y - 1, z, TideKit.FLOOR);
            kit.stair(x, y, z, Direction.WEST, Blocks.TUFF_BRICK_STAIRS.defaultBlockState());
            return null;
        }
        if (stepUp) {
            return null;
        }
        return Math.floorMod(u, 3) == 0 ? Blocks.POLISHED_TUFF.defaultBlockState() : kit.floorAt(x, z);
    }

    // 出口の広場 (u 113〜119): 床 71 の開けた台。ひれの面に載る
    private static void plaza(TideKit kit, int side) {
        kit.zone(TideZone.FIN);
        for (int u = 112; u <= ROOM1_U1 - 2; u++) {
            for (int z = FIN_Z1 + 6; z <= FIN_Z2 + 2; z++) {
                int x = side * u;
                for (int y = 60; y <= FIN_FLOOR; y++) {
                    BlockState cur = kit.get(x, y, z);
                    if (y == FIN_FLOOR) {
                        kit.set(x, y, z, kit.floorAt(x, z));
                    } else if (cur.isAir() || cur.is(Blocks.WATER)) {
                        kit.set(x, y, z, TideKit.SANDSTONE);
                    }
                }
                for (int y = FIN_FLOOR + 1; y <= FIN_FLOOR + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
    }

    // 貝の間 (甲羅の上のひれに建つ石の館): 壁・床・屋根・柱・灯り・窓。奥行き u1〜u2、幅 z -29〜-11、床 71、高さ 7
    private static void room(TideKit kit, Room r) {
        kit.zone(TideZone.FIN);
        int s = r.side();
        int top = FIN_FLOOR + 8;
        for (int u = r.u1() - 1; u <= r.u2() + 1; u++) {
            for (int z = FIN_Z1 - 1; z <= FIN_Z2 + 1; z++) {
                boolean wall = u == r.u1() - 1 || u == r.u2() + 1 || z == FIN_Z1 - 1 || z == FIN_Z2 + 1;
                int x = s * u;
                for (int y = 60; y <= top; y++) {
                    BlockState s2;
                    if (y < FIN_FLOOR) {
                        s2 = TideKit.SANDSTONE;
                    } else if (y == FIN_FLOOR) {
                        s2 = wall ? kit.wall(x, y, z) : kit.floorAt(x, z);
                    } else if (y == top) {
                        s2 = Math.floorMod(u + z, 6) == 0 && !wall ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (wall) {
                        s2 = windowAt(r, u, z, y) ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : kit.wall(x, y, z);
                    } else {
                        s2 = TideKit.AIR;
                    }
                    kit.set(x, y, z, s2);
                }
            }
        }
        // 奥の壁を、ひれの幅いっぱいまで横へ伸ばす (戸を通らずに脇を回れない。幅 z -46〜4、海の底から屋根の高さまで)
        for (int u = r.u2() + 1; u <= r.u2() + 1; u++) {
            for (int z = -46; z <= 4; z++) {
                if (z >= FIN_Z1 - 1 && z <= FIN_Z2 + 1) {
                    continue;
                }
                for (int y = 52; y <= top; y++) {
                    kit.set(s * u, y, z, kit.wall(s * u, y, z));
                }
            }
        }
        // 戸口: 手前の壁 (u1 - 1) は空いている。奥の壁 (u2 + 1) は戸 (封印の扉)
        for (int z = -22; z <= -18; z++) {
            for (int y = FIN_FLOOR + 1; y <= FIN_FLOOR + 4; y++) {
                kit.set(s * (r.u1() - 1), y, z, TideKit.AIR);
            }
        }
        // 柱 (壁ぎわ、6 マスおき。骨と灯り)
        for (int u = r.u1() + 2; u <= r.u2() - 1; u += 6) {
            for (int z : new int[] {FIN_Z1, FIN_Z2}) {
                for (int y = FIN_FLOOR + 1; y <= FIN_FLOOR + 7; y++) {
                    kit.set(s * u, y, z, y == FIN_FLOOR + 4 ? TideKit.LAMP : Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        // 貝の台 (色つきの石に、小さな珊瑚)
        for (int i = 0; i < r.pedZ().length; i++) {
            BlockPos p = r.pedestal(i);
            kit.set(p, CONCRETE[r.colors()[i]]);
            kit.set(p.above(), TideIslandBuilder.deadFan(p.getZ() * 3 + i));
            kit.set(p.below(), TideKit.LAMP);
        }
        // 壁の読み取り (READ 系): 奥の壁の内側に、色の列 (北から南へ)
        if (r.pattern() != null) {
            int gu = r.u2();
            for (int k = 0; k < r.pattern().length; k++) {
                int z = -26 + 2 * k - (r.pattern().length - 4);
                kit.set(s * gu, FIN_FLOOR + 5, z, CONCRETE[r.colors()[r.pattern()[k]]]);
                kit.set(s * gu, FIN_FLOOR + 6, z, TideKit.LAMP);
            }
        }
        // 戸の枠 (奥の戸の両脇に光る石)
        for (int z : new int[] {-23, -17}) {
            for (int y = FIN_FLOOR + 1; y <= FIN_FLOOR + 5; y++) {
                kit.set(s * (r.u2() + 1), y, z, y == FIN_FLOOR + 3 ? TideKit.LAMP : TideZone.FIN.cap);
            }
        }
        // 潮の標し (2 の間の奥の壁の上): 満ち潮は海のランタン、引き潮は暗い石
        if (r.mode() == Mode.ASC_TIDE) {
            for (int z = -22; z <= -18; z += 2) {
                kit.set(s * (r.u2() + 1), FIN_FLOOR + 6, z, TideZone.FIN.dark);
            }
        }
    }

    // 側の壁の窓 (海が見える。3 マス幅、高さ 3〜5)
    private static boolean windowAt(Room r, int u, int z, int y) {
        if ((z == FIN_Z1 - 1 || z == FIN_Z2 + 1) && y >= FIN_FLOOR + 3 && y <= FIN_FLOOR + 5) {
            int mid = (r.u1() + r.u2()) / 2;
            return Math.abs(u - mid) <= 1 || Math.abs(u - mid) == 6 && false;
        }
        return false;
    }

    // 先端の台 (半径 9 の円。床 65。階段で 71 から下りる)
    private static void tip(TideKit kit, int side) {
        kit.zone(TideZone.FIN);
        int cu = TIP_U;
        // 階段 (u 160〜165。登る向きは根もと側)
        for (int i = 0; i < 6; i++) {
            int u = ROOM2_U2 + 2 + i;
            int f = FIN_FLOOR - 1 - i;
            for (int z = -22; z <= -18; z++) {
                int x = side * u;
                for (int y = 58; y < f; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.stair(x, f, z, side < 0 ? Direction.EAST : Direction.WEST, Blocks.TUFF_BRICK_STAIRS.defaultBlockState());
                for (int y = f + 1; y <= f + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        for (int du = -10; du <= 10; du++) {
            for (int z = -31; z <= -9; z++) {
                double d = Math.hypot(du, z + 20);
                if (d > 9.7) {
                    continue;
                }
                int x = side * (cu + du);
                for (int y = 58; y <= TIP_FLOOR; y++) {
                    kit.set(x, y, z, y == TIP_FLOOR ? (d > 8.4 && !(du < -5 && Math.abs(z + 20) <= 2) ? Blocks.POLISHED_TUFF.defaultBlockState() : (Math.floorMod(du + z, 4) == 0 ? TideKit.LAMP : kit.floorAt(x, z))) : TideKit.SANDSTONE);
                }
                for (int y = TIP_FLOOR + 1; y <= TIP_FLOOR + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                // 縁の低い壁 (輪の厚さ 1〜2。根もとの側に幅 5 の切れ目)
                if (d > 8.4 && !(du < -5 && Math.abs(z + 20) <= 2)) {
                    kit.set(x, TIP_FLOOR + 1, z, Blocks.POLISHED_TUFF.defaultBlockState());
                    kit.set(x, TIP_FLOOR + 2, z, Math.floorMod(du + z, 4) == 0 ? TideKit.LAMP : Blocks.TUFF_BRICKS.defaultBlockState());
                }
            }
        }
        // 先端の見晴らしの石柱 (碑の台)。碑と宝は TideCaches
        int px = side * (cu + 5);
        for (int y = TIP_FLOOR + 1; y <= TIP_FLOOR + 4; y++) {
            kit.set(px, y, -20, TideZone.FIN.dark);
        }
        kit.set(side * (cu - 3), TIP_FLOOR + 1, -20, Blocks.PRISMARINE_WALL.defaultBlockState());
        kit.set(side * (cu - 3), TIP_FLOOR + 2, -20, TideKit.LAMP);
    }

    // ---------------------------------------------------------------- 仕掛け
    private static final Map<String, int[]> PROGRESS = new HashMap<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    private static String key(UUID group, Room r) {
        return group + "/" + r.id();
    }

    public static List<BlockPos> gateOf(String id) {
        for (Room r : ROOMS) {
            if (r.id().equals(id)) {
                return r.gate();
            }
        }
        return List.of();
    }

    // 戸をすべて置く (建てたあと・起動のあと)
    static void placeGates(ServerLevel level, boolean force) {
        for (Room r : ROOMS) {
            List<BlockPos> g = r.gate();
            if (force) {
                GroupGates.place(level, g, false);
            } else {
                TideGates.ensure(level, g, false);
            }
        }
    }

    // 潮の標し (満ち潮のあいだ海のランタン)
    static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        TideClock.Phase ph = TideClock.phase(level);
        boolean flood = ph == TideClock.Phase.HIGH || ph == TideClock.Phase.RISING;
        for (Room r : ROOMS) {
            if (r.mode() != Mode.ASC_TIDE) {
                continue;
            }
            BlockState want = flood ? TideKit.LAMP : TideZone.FIN.dark;
            for (int z = -22; z <= -18; z += 2) {
                BlockPos p = new BlockPos(r.x(r.u2() + 1), FIN_FLOOR + 6, z);
                if (level.isLoaded(p) && !level.getBlockState(p).equals(want)) {
                    level.setBlock(p, want, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().dimension() != TideTravel.TIDE_BACK
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Room r : ROOMS) {
            for (int i = 0; i < r.pedZ().length; i++) {
                if (r.pedestal(i).equals(pos)) {
                    event.setCanceled(true);
                    press((ServerLevel) event.getLevel(), player, r, i);
                    return;
                }
            }
        }
    }

    // 貝を叩く (テストからも呼ぶ)。返すのは、その叩きの結果 (0 = 違った、1 = 合っていた、2 = 解けた)
    static int press(ServerLevel level, ServerPlayer player, Room r, int i) {
        UUID group = Groups.keyOf(player);
        if (GroupGates.isOpen(level, group, r.gate())) {
            tone(level, r, i, true);
            Notice.bar(player, ModText.hint("message.skyabyss.tide_fin_done"));
            return 2;
        }
        String k = key(group, r);
        long now = level.getGameTime();
        int[] st = PROGRESS.computeIfAbsent(k, x -> new int[] {0, 0});
        if (now - LAST.getOrDefault(k, 0L) > 30 * 20) {
            st[0] = 0;
        }
        LAST.put(k, now);
        if (st[0] == 0) {
            // 向きは叩き始めの潮で決まる (引き潮・引き始めは昇り、満ち潮・満ち始めは降り)
            TideClock.Phase ph = TideClock.phase(level);
            st[1] = ph == TideClock.Phase.HIGH || ph == TideClock.Phase.RISING ? -1 : 1;
        }
        int want = r.expected(st[0], st[1]);
        if (i == want) {
            st[0]++;
            tone(level, r, i, true);
            if (st[0] >= r.pedZ().length) {
                st[0] = 0;
                GroupGates.open(level, group, r.gate());
                for (ServerPlayer p : Groups.online(level, group)) {
                    if (p.position().distanceToSqr(r.center().getX(), r.center().getY(), r.center().getZ()) < 40 * 40) {
                        Notice.narrate(p, "message.skyabyss.tide_fin_open");
                    }
                }
                level.playSound(null, r.center(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.6f, 1f);
                return 2;
            }
            Notice.bar(player, ModText.progress("message.skyabyss.tide_fin_step", st[0], r.pedZ().length));
            return 1;
        }
        st[0] = 0;
        tone(level, r, i, false);
        Notice.bar(player, ModText.deny("message.skyabyss.tide_fin_wrong"));
        return 0;
    }

    private static void tone(ServerLevel level, Room r, int i, boolean good) {
        BlockPos p = r.pedestal(i);
        float pitch = (float) Math.pow(2.0, (r.rank()[i] * 3 - 6) / 12.0);
        level.playSound(null, p, good ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.4f, good ? pitch : 0.6f);
        level.sendParticles(good ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5, good ? 8 : 6, 0.2, 0.2, 0.2, 0.02);
    }

    // 自己テスト用: 進みを消す
    static void clear() {
        PROGRESS.clear();
        LAST.clear();
    }

    static List<BlockPos> allPedestals() {
        List<BlockPos> list = new ArrayList<>();
        for (Room r : ROOMS) {
            for (int i = 0; i < r.pedZ().length; i++) {
                list.add(r.pedestal(i));
            }
        }
        return list;
    }
}
