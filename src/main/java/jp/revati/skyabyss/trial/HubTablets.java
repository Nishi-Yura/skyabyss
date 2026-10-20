package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.TrialSteles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

// 鼓動の間 (背中の真ん中) の試練の碑 (docs/design/29 の 4.6)。試練ごとに一つ (岩鯨・潮殻・雷翼は 歩/智/闘の三つ、余燼は西・東・南)。
// 越えた試練の碑は、そのグループの画面でだけ光る (GroupOverlays の見かけの上書き。当たり判定は同じ)。どこへ行って、どこへ行っていないかが分かる。
// 置き場は、鼓動の間の休み場 ("hub" の復活位置) のまわりの開いた床から、決まった手順で三つ選ぶ (背の作りが変わっても追う。
// 同じ作りなら毎回同じ場所)。岩鯨は体の作り直しが済むまで置かない (WhaleDeck.placed)。
public final class HubTablets {
    private HubTablets() {
    }

    public record Spot(String key, BlockPos pos, Direction facing) {
    }

    // 巨神ごとの置き場 (背のディメンション → 巨神の名前 → 置き場)。保存しない
    private static final Map<String, List<Spot>> SPOTS = new HashMap<>();

    public static boolean enabled(String titan) {
        return (!Titans.STONE_WHALE.equals(titan) || WhaleDeck.placed()) && (!Titans.TIDE_SHELL.equals(titan) || jp.revati.skyabyss.titan.tide.back.TideDeck.placed());
    }

    public static void clearCache() {
        SPOTS.clear();
    }

    public static List<Spot> spots(ServerLevel back, String titan) {
        if (!enabled(titan)) {
            return List.of();
        }
        List<Spot> cached = SPOTS.get(titan);
        if (cached != null) {
            return cached;
        }
        BlockPos hub = ReturnBeacons.hubOf(titan);
        if (hub == null || !back.hasChunkAt(hub)) {
            return List.of();
        }
        List<Spot> found = find(back, titan, hub);
        if (found.size() == TrialRegions.keys(titan).size()) {
            SPOTS.put(titan, found);
        }
        return found;
    }

    // ---------------------------------------------------------------- 弧の並べ (版 22。試遊の声「碑の置き方が雑」)
    // 三つの碑を、休み場 (hub) のまわりに、左右対称の浅い弧として並べる。方眼に合わせ (整数の座標)、高さ (hub と同じ床)・向き (hub のほう。三つとも同じ向き) をそろえ、
    // 台座 (その巨神の材質) の上に立て (向きは弧の外側 = 入ってきた人に表が見える)、台座のまわりの床に灯り (四隅)。左から 歩・智・闘 の順 (岩鯨は石の番号 0 歩・2 智・1 闘 の並び)
    // 弧の向きは、南・北・東・西の順に、全部入る最初のもの。型は (横のずれ, 弧の両端の奥行き, 真ん中の奥行き) を小さい順に
    private static final int[][] ARCS = {{5, 4, 6}, {6, 4, 7}, {4, 3, 5}, {7, 5, 8}, {5, 3, 6}};
    private static final Direction[] ARC_SIDES = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};

    // 左から右へ並べる試練の名前 (歩・智・闘)
    static List<String> arcOrder(String titan) {
        List<String> keys = TrialRegions.keys(titan);
        if (keys.size() == 3 && Titans.STONE_WHALE.equals(titan)) {
            return List.of(keys.get(0), keys.get(2), keys.get(1));
        }
        return keys;
    }

    // 台座の材質・灯り (巨神の背の材質に合わせる)
    static BlockState pedestal(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> net.minecraft.world.level.block.Blocks.DARK_PRISMARINE.defaultBlockState();
            case Titans.THUNDER_WING -> net.minecraft.world.level.block.Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
            case Titans.EMBER -> net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            default -> net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        };
    }

    static BlockState inlayLight(String titan) {
        return Titans.EMBER.equals(titan) ? net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState();
    }

    // 弧の碑の一つ分が入るか: 台座の床 (足もとの高さ = hub と同じ) の上に、台座 1・碑 1・頭上 2 が空き、まわりの四方が空気
    private static boolean arcFree(ServerLevel back, String titan, BlockPos feet) {
        BlockState at = back.getBlockState(feet);
        if (!(at.isAir() || at.is(pedestal(titan).getBlock())) || !back.getBlockState(feet.below()).isFaceSturdy(back, feet.below(), Direction.UP)) {
            return false;
        }
        BlockPos tab = feet.above();
        BlockState t = back.getBlockState(tab);
        if (!(t.isAir() || t.is(ModBlocks.TRIAL_TABLET.get())) || !back.getBlockState(tab.above()).isAir() || !back.getBlockState(tab.above(2)).isAir()) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!back.getBlockState(feet.relative(d)).isAir() || !back.getBlockState(tab.relative(d)).isAir()) {
                return false;
            }
        }
        return true;
    }

    // 弧の置き場 (左から 歩・智・闘)。全部入る型がなければ空
    static List<Spot> findArc(ServerLevel back, String titan, BlockPos hub) {
        List<String> order = arcOrder(titan);
        if (order.size() != 3) {
            return List.of();
        }
        for (Direction side : ARC_SIDES) {
            // 碑の表 (向き) は弧の外側 (side)。表を見る人 (side の反対を向く人) の右手が、左から右への並びの右
            Direction right = side.getCounterClockWise();
            for (int[] arc : ARCS) {
                // 弧の左 (a = 奥行き arc[1]、横 -arc[0])・真ん中 (奥行き arc[2])・右。hub の足もとの高さそのまま
                BlockPos[] feet = new BlockPos[3];
                int[] lateral = {-arc[0], 0, arc[0]};
                int[] depth = {arc[1], arc[2], arc[1]};
                boolean ok = true;
                for (int i = 0; i < 3 && ok; i++) {
                    BlockPos p = hub.relative(side, depth[i]).relative(right, lateral[i]);
                    feet[i] = p;
                    ok = arcFree(back, titan, p);
                }
                if (ok) {
                    List<Spot> out = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        // 碑は台座の上 (feet の 1 上)。向きは三つとも弧の外側 (side)
                        out.add(new Spot(order.get(i), feet[i].above(), side));
                    }
                    return out;
                }
            }
        }
        return List.of();
    }

    // 台座とまわりの灯りを置く (何度でも)。碑の足もと (pos の 1 下) が台座、その 1 下の床の四隅 (台座から斜めに 1) が灯り
    static void placePedestal(ServerLevel back, String titan, BlockPos tablet) {
        BlockPos base = tablet.below();
        BlockState ped = pedestal(titan);
        if (!back.getBlockState(base).is(ped.getBlock())) {
            back.setBlock(base, ped, 3);
        }
        BlockState lamp = inlayLight(titan);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos c = base.offset(dx, -1, dz);
                if (!back.getBlockState(c).is(lamp.getBlock()) && back.getBlockState(c).isFaceSturdy(back, c, Direction.UP)) {
                    back.setBlock(c, lamp, 3);
                }
            }
        }
    }

    // 鼓動の間の休み場のまわり (半径 4〜9) の、碑を立てられる床を決まった順に探す。角度は 12 度おき、半径は 6・5・7・4・8・9 の順
    static List<Spot> find(ServerLevel back, String titan, BlockPos hub) {
        List<Spot> arc = findArc(back, titan, hub);
        return arc.isEmpty() ? findScan(back, titan, hub) : arc;
    }

    // 弧が入らない作りのとき (予備): 休み場のまわりの開いた床から、決まった順に三つ
    static List<Spot> findScan(ServerLevel back, String titan, BlockPos hub) {
        List<BlockPos> cand = new ArrayList<>();
        int[] radii = {6, 5, 7, 4, 8, 9};
        for (int a = 0; a < 360; a += 12) {
            for (int r : radii) {
                double rad = Math.toRadians(a);
                int x = hub.getX() + (int) Math.round(Math.cos(rad) * r);
                int z = hub.getZ() + (int) Math.round(Math.sin(rad) * r);
                BlockPos p = standing(back, x, hub.getY(), z);
                if (p != null) {
                    cand.add(p);
                    break;
                }
            }
        }
        List<String> keys = TrialRegions.keys(titan);
        List<BlockPos> chosen = new ArrayList<>();
        // 角度の順に、互いに 5 ブロック以上離れた三つ (最初は角度 0 から。同じ手順なら同じ結果)
        for (BlockPos p : cand) {
            boolean far = true;
            for (BlockPos c : chosen) {
                if (c.distSqr(p) < 25) {
                    far = false;
                    break;
                }
            }
            if (far) {
                chosen.add(p);
                if (chosen.size() == keys.size()) {
                    break;
                }
            }
        }
        List<Spot> out = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            BlockPos p = chosen.get(i);
            out.add(new Spot(keys.get(i), p, facingTo(p, hub)));
        }
        return out;
    }

    // 立てられる床 (足もとが当たりのあるブロック、体の高さ 2 が空気、まわり 4 方向が空気)。y は基準の ±2 を探す
    @Nullable
    private static BlockPos standing(ServerLevel back, int x, int y0, int z) {
        for (int dy = 0; dy <= 2; dy++) {
            for (int sign : new int[] {1, -1}) {
                int y = y0 + dy * sign;
                BlockPos p = new BlockPos(x, y, z);
                if (isFree(back, p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean isFree(ServerLevel back, BlockPos p) {
        // 立ててある碑の位置は、そのまま候補 (再起動のあとも同じ場所を選ぶ)
        boolean self = back.getBlockState(p).is(ModBlocks.TRIAL_TABLET.get());
        if (!(self || back.getBlockState(p).isAir()) || !back.getBlockState(p.above()).isAir() || !back.getBlockState(p.above(2)).isAir()) {
            return false;
        }
        BlockState floor = back.getBlockState(p.below());
        if (!floor.isFaceSturdy(back, p.below(), Direction.UP)) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!back.getBlockState(p.relative(d)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static Direction facingTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    public static BlockState unlit(Direction facing) {
        return ModBlocks.TRIAL_TABLET.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(TrialTabletBlock.LIT, false);
    }

    public static BlockState lit(Direction facing) {
        return unlit(facing).setValue(TrialTabletBlock.LIT, true);
    }

    // 碑を世界に立てる (何度でも。足りなければ置き直す)。置いた数
    public static int install(ServerLevel back, String titan) {
        int n = 0;
        for (Spot s : spots(back, titan)) {
            BlockState under = back.getBlockState(s.pos().below());
            if (s.pos().getY() == ReturnBeacons.hubOf(titan).getY() + 1 && (under.isAir() || under.is(pedestal(titan).getBlock()))) {
                placePedestal(back, titan, s.pos());
            }
            if (!back.getBlockState(s.pos()).is(ModBlocks.TRIAL_TABLET.get())) {
                back.setBlock(s.pos(), unlit(s.facing()), 3);
                n++;
            }
        }
        return n;
    }

    // 試験用: 石の記録を触らずに「越えた」ことにする印 ("<グループ>|<巨神>|<試練>")
    public static final java.util.Set<String> TEST_CLEARED = new java.util.HashSet<>();

    public static boolean cleared(ServerLevel back, UUID group, String titan, String key) {
        if (!TEST_CLEARED.isEmpty() && TEST_CLEARED.contains(group + "|" + titan + "|" + key)) {
            return true;
        }
        return TrialSteles.rung(back.getServer(), group, TrialSteles.signal(TrialRegions.signalTitan(titan), key));
    }

    // GroupOverlays から: そのグループが越えた試練の碑を光らせる
    public static void overlay(ServerLevel level, UUID group, java.util.function.BiConsumer<BlockPos, BlockState> out) {
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null) {
            return;
        }
        List<Spot> spots = SPOTS.get(titan);
        if (spots == null) {
            return;
        }
        for (Spot s : spots) {
            if (cleared(level, group, titan, s.key())) {
                out.accept(s.pos(), lit(s.facing()));
            }
        }
    }

    public static void refresh(ServerLevel back, UUID group) {
        GroupOverlays.refresh(back, group);
    }

    @Nullable
    public static Spot spotOf(ServerLevel back, String titan, String key) {
        for (Spot s : spots(back, titan)) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        return null;
    }
}
