package jp.revati.skyabyss.titan.audit;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.titan.audit.BuildAudit.Grid;
import jp.revati.skyabyss.titan.audit.BuildAudit.Report;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;

// 建築の監査の広い検査 (BuildAudit.scan から呼ばれる。docs/design/08 の「建築の監査」の飾り・浮き・流体の章)。
//  FLOAT_BLOCK      まわり 26 マスに固いブロックが無い、宙に浮いた 1 個 (わざとなら Allow に理由を書く)
//  ATTACH_SUPPORT   松明・ランタン・鎖・看板・ボタン・レバー・旗・カーペット・感圧板・レール・つた・花が、支えの無い所についている
//  ATTACH_FACING    壁についた松明・看板・ボタンの表が、固いブロックに塞がれている / 箱・かまどの前が塞がれて開けられない
//  GATE_*           柵の戸: GATE_ALONE (どちらの隣にも柵・壁が無い)、GATE_BLOCKED (戸の前後が壁)
//  STACKED_FLAT     カーペット・感圧板・レールの上に、さらに平たい飾りが重なる (面が重なって z ファイティング)
//  HEAD_DENT        廊下 (両隣は高さ 3 以上の同じ床) の真ん中にだけ、頭の高さにブロックが出ている (天井の出っぱり・空気のはずがブロック)
//  STAIR_HEAD       通り道の階段の頭の上が塞がれている (立つと頭がつかえる)
//  FLOOR_DIMPLE     まわり 4 方向が 1 高い床の中の 1 マスだけ、1 低い (1 つ低く埋まったブロック)
//  FLUID_LEAK       水・溶岩の源の横・下が空気で、流れ出す (水槽・溶岩の池が漏れる)
//  SIGN_OVERFLOW    看板の行が 90 px を超える、または 4 行を超える
//  LIGHT_DARK       歩ける所 (route) で明るさ 0〜2 のかたまり (monsters が湧く暗さ)
public final class BuildAuditExtra {
    private BuildAuditExtra() {
    }

    // 暗さの限界 (この値以下は暗い)
    static final int DARK = 3;

    private static final Direction[] H = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static void flag(Report out, String structure, String kind, BlockPos p, String msg) {
        out.add(new BuildAudit.Finding(structure, kind, p.immutable(), msg));
    }

    private static boolean sturdy(Grid g, BlockPos p, Direction face) {
        return g.get(p).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
    }

    private static boolean sturdyCenter(Grid g, BlockPos p, Direction face) {
        return g.get(p).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face, net.minecraft.world.level.block.SupportType.CENTER);
    }

    private static boolean sturdyRigid(Grid g, BlockPos p, Direction face) {
        return g.get(p).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face, net.minecraft.world.level.block.SupportType.RIGID);
    }

    private static boolean solid(Grid g, BlockPos p) {
        return BuildAudit.top(g.get(p)) > 0;
    }

    // 地上の部品 (bp: site: built:) は地形の上に建つので、葉・木・土・砂利などの自然のブロックは浮き・くぼみ・天井の出っぱりの対象にしない
    private static boolean natural(BlockState s, String st) {
        boolean outdoor = st.startsWith("bp:") || st.startsWith("site:") || st.startsWith("built:");
        if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)) {
            return true;
        }
        // 崩れる骨は、宙に浮いた足場として置く試練の部品 (跳ぶ道)
        if (s.getBlock() instanceof jp.revati.skyabyss.titan.dungeon.CrumblingBoneBlock) {
            return true;
        }
        return outdoor && (s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.world.level.block.Blocks.GRAVEL) || s.is(net.minecraft.world.level.block.Blocks.CLAY));
    }

    // まわり 2 マスの空気でないブロックの名前 (最大 6 つ)。何のそばの浮きかを読むため
    private static String around(Grid g, BlockPos p) {
        StringBuilder sb = new StringBuilder(" near=");
        int n = 0;
        for (int dx = -2; dx <= 2 && n < 6; dx++) {
            for (int dy = -2; dy <= 2 && n < 6; dy++) {
                for (int dz = -2; dz <= 2 && n < 6; dz++) {
                    BlockState s = g.get(p.offset(dx, dy, dz));
                    if ((dx | dy | dz) != 0 && !s.isAir()) {
                        sb.append(dx).append(',').append(dy).append(',').append(dz).append('=').append(s.getBlock().getDescriptionId().replace("block.minecraft.", "")).append(' ');
                        n++;
                    }
                }
            }
        }
        return n == 0 ? " near=nothing within 2" : sb.toString();
    }

    private static String name(BlockState s) {
        return s.getBlock().getDescriptionId();
    }

    static void scan(Grid g, BackReach.Box box, String st, Report out) {
        g.all(box, (p, s) -> {
            Block b = s.getBlock();
            // ---- 浮き
            if (BuildAudit.top(s) > 0 && !(b instanceof LiquidBlock) && !natural(s, st)) {
                isolated(g, p, s, st, out);
            }
            // ---- 流体
            FluidState fs = s.getFluidState();
            if (!fs.isEmpty() && fs.isSource() && b instanceof LiquidBlock) {
                leak(g, p, s, st, out);
            }
            // ---- 取りつけ
            attach(g, p, s, st, out);
            // ---- 看板の文
            if (b instanceof StandingSignBlock || b instanceof WallSignBlock || b instanceof CeilingHangingSignBlock || b instanceof WallHangingSignBlock) {
                signText(g, p, st, out);
            }
            // ---- 階段の頭
            if (b instanceof net.minecraft.world.level.block.StairBlock) {
                stairHead(g, p, s, st, out);
            }
            // ---- 天井の出っぱり・床のくぼみ
            if (BuildAudit.top(s) >= 1 && !natural(s, st)) {
                headDent(g, p, s, st, out);
                dimple(g, p, s, st, out);
            }
        });
    }

    // 浮いた 1 個: 26 近傍に固いブロックが無い
    private static void isolated(Grid g, BlockPos p, BlockState s, String st, Report out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx | dy | dz) != 0 && solid(g, p.offset(dx, dy, dz))) {
                        return;
                    }
                }
            }
        }
        flag(out, st, "FLOAT_BLOCK", p, "single block floating with no solid block around it: " + name(s) + around(g, p));
    }

    // 水・溶岩の源: 横と下が空気なら流れ出す
    private static void leak(Grid g, BlockPos p, BlockState s, String st, Report out) {
        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            BlockState n = g.get(p.relative(d));
            if (n.isAir() || (n.getFluidState().isEmpty() && BuildAudit.top(n) == 0 && n.canBeReplaced())) {
                flag(out, st, "FLUID_LEAK", p, name(s) + " source opens to air on its " + d + " side at " + p.relative(d).toShortString() + " (" + name(n) + ")" + around(g, p));
                return;
            }
        }
    }

    private static void attach(Grid g, BlockPos p, BlockState s, String st, Report out) {
        Block b = s.getBlock();
        if (b instanceof WallTorchBlock || b instanceof RedstoneWallTorchBlock) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (!sturdy(g, p.relative(f.getOpposite()), f)) {
                flag(out, st, "ATTACH_SUPPORT", p, "wall torch facing " + f + " has no support behind it: " + g.get(p.relative(f.getOpposite())));
            }
            if (BuildAudit.top(g.get(p.relative(f))) >= 1) {
                flag(out, st, "ATTACH_FACING", p, "wall torch faces into a solid block at " + p.relative(f).toShortString());
            }
        } else if (b instanceof TorchBlock || b instanceof RedstoneTorchBlock) {
            if (!sturdyCenter(g, p.below(), Direction.UP)) {
                flag(out, st, "ATTACH_SUPPORT", p, "standing torch with nothing to stand on: " + g.get(p.below()));
            }
        } else if (b instanceof LanternBlock) {
            if (s.getValue(LanternBlock.HANGING)) {
                BlockState up = g.get(p.above());
                if (!(up.getBlock() instanceof ChainBlock) && !sturdyCenter(g, p.above(), Direction.DOWN)) {
                    flag(out, st, "ATTACH_SUPPORT", p, "hanging lantern with nothing above to hang from: " + up);
                }
            } else if (!sturdyCenter(g, p.below(), Direction.UP)) {
                flag(out, st, "ATTACH_SUPPORT", p, "standing lantern with nothing under it: " + g.get(p.below()));
            }
        } else if (b instanceof ChainBlock) {
            if (s.getValue(ChainBlock.AXIS) == Direction.Axis.Y) {
                // 上へたどって、固いものか鎖に吊るされた先 (ランタン) の天井に着くこと
                BlockPos q = p.above();
                int guard = 0;
                while (g.get(q).getBlock() instanceof ChainBlock && g.get(q).getValue(ChainBlock.AXIS) == Direction.Axis.Y && guard++ < 64) {
                    q = q.above();
                }
                if (!sturdyCenter(g, q, Direction.DOWN) && !(g.get(q).getBlock() instanceof LanternBlock)) {
                    flag(out, st, "ATTACH_SUPPORT", p, "vertical chain hangs from nothing (top at " + q.below().toShortString() + ", above it " + g.get(q) + ")");
                }
            }
        } else if (b instanceof StandingSignBlock || b instanceof BannerBlock) {
            if (!g.get(p.below()).isSolid()) {
                flag(out, st, "ATTACH_SUPPORT", p, "standing sign/banner with nothing solid under it: " + g.get(p.below()));
            }
        } else if (b instanceof WallSignBlock || b instanceof WallBannerBlock) {
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (!g.get(p.relative(f.getOpposite())).isSolid()) {
                flag(out, st, "ATTACH_SUPPORT", p, "wall sign/banner facing " + f + " has no wall behind it: " + g.get(p.relative(f.getOpposite())));
            }
            if (BuildAudit.top(g.get(p.relative(f))) >= 1) {
                flag(out, st, "ATTACH_FACING", p, "wall sign/banner faces into a solid block at " + p.relative(f).toShortString() + " (unreadable)");
            }
        } else if (b instanceof CeilingHangingSignBlock) {
            if (!sturdyCenter(g, p.above(), Direction.DOWN)) {
                flag(out, st, "ATTACH_SUPPORT", p, "hanging sign with nothing above: " + g.get(p.above()));
            }
        } else if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            AttachFace face = s.getValue(BlockStateProperties.ATTACH_FACE);
            Direction f = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            Direction toSupport = face == AttachFace.FLOOR ? Direction.DOWN : face == AttachFace.CEILING ? Direction.UP : f.getOpposite();
            if (!sturdy(g, p.relative(toSupport), toSupport.getOpposite())) {
                flag(out, st, "ATTACH_SUPPORT", p, name(s) + " (" + face + ", facing " + f + ") has nothing to attach to on its " + toSupport + " side: " + g.get(p.relative(toSupport)));
            }
            if (face == AttachFace.WALL && BuildAudit.top(g.get(p.relative(f))) >= 1) {
                flag(out, st, "ATTACH_FACING", p, name(s) + " faces into a solid block at " + p.relative(f).toShortString());
            }
        } else if (b instanceof CarpetBlock) {
            if (g.get(p.below()).isAir() || (BuildAudit.top(g.get(p.below())) == 0 && g.get(p.below()).getFluidState().isEmpty())) {
                flag(out, st, "ATTACH_SUPPORT", p, "carpet over nothing: " + g.get(p.below()));
            }
            if (g.get(p.below()).getBlock() instanceof CarpetBlock || g.get(p.below()).getBlock() instanceof BasePressurePlateBlock) {
                flag(out, st, "STACKED_FLAT", p, "carpet stacked on another flat decor block (" + name(g.get(p.below())) + "): coplanar faces z-fight");
            }
        } else if (b instanceof BasePressurePlateBlock || b instanceof BaseRailBlock) {
            if (!sturdyRigid(g, p.below(), Direction.UP)) {
                flag(out, st, "ATTACH_SUPPORT", p, name(s) + " has no solid floor under it: " + g.get(p.below()));
            }
            if (g.get(p.below()).getBlock() instanceof CarpetBlock) {
                flag(out, st, "STACKED_FLAT", p, name(s) + " on top of a carpet: coplanar faces z-fight");
            }
        } else if (b instanceof VineBlock) {
            boolean ok = BuildAudit.top(g.get(p.above())) >= 1;
            for (Direction d : H) {
                if (s.getValue(VineBlock.getPropertyForFace(d)) && BuildAudit.top(g.get(p.relative(d))) >= 1) {
                    ok = true;
                }
            }
            BlockState up = g.get(p.above());
            if (!ok && up.getBlock() instanceof VineBlock) {
                for (Direction d : H) {
                    if (s.getValue(VineBlock.getPropertyForFace(d)) && up.getValue(VineBlock.getPropertyForFace(d))) {
                        ok = true;
                    }
                }
            }
            if (!ok) {
                flag(out, st, "ATTACH_SUPPORT", p, "vine attached to nothing");
            }
        } else if (b instanceof FenceGateBlock) {
            gate(g, p, s, st, out);
        } else if (b instanceof ChestBlock || b instanceof jp.revati.skyabyss.relic.RelicCacheBlock) {
            Direction f = s.getValue(ChestBlock.FACING);
            if (BuildAudit.top(g.get(p.relative(f))) >= 1) {
                flag(out, st, "ATTACH_FACING", p, "chest faces into a solid block at " + p.relative(f).toShortString());
            }
            if (g.get(p.above()).isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                flag(out, st, "ATTACH_FACING", p, "chest has a solid block on top (cannot be opened): " + g.get(p.above()));
            }
        } else if (b instanceof AbstractFurnaceBlock) {
            Direction f = s.getValue(AbstractFurnaceBlock.FACING);
            if (BuildAudit.top(g.get(p.relative(f))) >= 1) {
                flag(out, st, "ATTACH_FACING", p, "furnace faces into a solid block at " + p.relative(f).toShortString());
            }
        } else if (b instanceof net.minecraft.world.level.block.BushBlock && !(b instanceof net.minecraft.world.level.block.LiquidBlockContainer)) {
            BlockState below = g.get(p.below());
            boolean upperHalf = s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
            if (upperHalf) {
                if (below.getBlock() != b) {
                    flag(out, st, "ATTACH_SUPPORT", p, "upper half of a tall plant without its lower half: " + name(s));
                }
            } else if (below.isAir() || (BuildAudit.top(below) == 0 && below.getFluidState().isEmpty())) {
                flag(out, st, "ATTACH_SUPPORT", p, "plant over nothing: " + name(s));
            }
        }
    }

    private static void gate(Grid g, BlockPos p, BlockState s, String st, Report out) {
        Direction f = s.getValue(FenceGateBlock.FACING);
        Direction along = f.getClockWise();
        boolean a = BuildAudit.top(g.get(p.relative(along))) > 0 || !g.get(p.relative(along)).isAir();
        boolean b = BuildAudit.top(g.get(p.relative(along.getOpposite()))) > 0 || !g.get(p.relative(along.getOpposite())).isAir();
        if (!a && !b) {
            flag(out, st, "GATE_ALONE", p, "fence gate with nothing on either side of it (no fence/wall to connect to)");
        }
        for (Direction d : new Direction[] {f, f.getOpposite()}) {
            BlockPos n = p.relative(d);
            if (BuildAudit.top(g.get(n)) > 0 || BuildAudit.top(g.get(n.above())) > 0) {
                flag(out, st, "GATE_BLOCKED", p, "fence gate's " + d + " side (passage axis) is blocked at " + n.toShortString() + ": " + g.get(n));
            }
        }
    }

    // 看板の文: 各行が 90 px に収まるか。translate キーなら両言語で測る
    private static void signText(Grid g, BlockPos p, String st, Report out) {
        BlockEntity be = g.blockEntity(p);
        if (!(be instanceof SignBlockEntity sign)) {
            return;
        }
        for (boolean front : new boolean[] {true, false}) {
            SignText t = sign.getText(front);
            for (int i = 0; i < 4; i++) {
                Component c = t.getMessage(i, false);
                for (String lang : SignFit.LANGUAGES) {
                    String text;
                    if (c.getContents() instanceof TranslatableContents tc) {
                        text = SignFit.lang(lang).getOrDefault(tc.getKey(), tc.getKey());
                    } else {
                        text = c.getString();
                    }
                    int w = SignFit.approxWidth(text);
                    if (w > SignFit.MAX_LINE_WIDTH) {
                        flag(out, st, "SIGN_OVERFLOW", p, "sign line " + (i + 1) + " (" + (front ? "front" : "back") + ", " + lang + ") is " + w + " px (max " + SignFit.MAX_LINE_WIDTH + "): " + text);
                    }
                }
            }
        }
    }

    // 通り道の階段の頭の上
    private static void stairHead(Grid g, BlockPos p, BlockState s, String st, Report out) {
        if (s.getValue(net.minecraft.world.level.block.StairBlock.HALF) != net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
            return;
        }
        Direction f = s.getValue(net.minecraft.world.level.block.StairBlock.FACING);
        BlockPos lo = p.relative(f.getOpposite());
        BlockPos hi = p.relative(f);
        // 通り道: 低い側に立てる床 (段より 1 低い〜同じ高さ) があり、高い側へも出られる
        double sLo = BuildAudit.surf(g, lo.getX(), lo.getY(), lo.getZ());
        if (Double.isNaN(sLo) || sLo > p.getY() + 0.6 || sLo < p.getY() - 0.6) {
            return;
        }
        BlockState u1 = g.get(p.above());
        BlockState u2 = g.get(p.above(2));
        boolean blocked = BuildAudit.top(u1) > 0 || BuildAudit.top(u2) > 0;
        // 高い側が次の段・床でふさがるのは正常。この段そのものの頭上が塞がっていて、両隣 (低い側の頭上) は空いているときだけ
        if (blocked && BuildAudit.top(g.get(lo.above())) == 0 && BuildAudit.top(g.get(lo.above(2))) == 0 && BuildAudit.top(g.get(hi)) == 0) {
            flag(out, st, "STAIR_HEAD", p, "headroom over the stair step is blocked (" + name(u1) + " / " + name(u2) + ") while the approach is open");
        }
    }

    // 廊下の真ん中だけ頭の高さにブロック: 足もと F の上の F+1 が塞がり、反対どうしの両隣は高さ 3 以上の同じ床
    private static void headDent(Grid g, BlockPos p, BlockState s, String st, Report out) {
        BlockPos feet = p.below();
        if (BuildAudit.top(g.get(feet)) != 0 || BuildAudit.top(g.get(feet.below())) < 1) {
            return;
        }
        if (!g.get(feet).getFluidState().isEmpty()) {
            return;
        }
        for (int axis = 0; axis < 2; axis++) {
            Direction a = axis == 0 ? Direction.NORTH : Direction.EAST;
            Direction w = a.getClockWise();
            boolean walled = BuildAudit.top(g.get(feet.relative(w))) >= 1 && BuildAudit.top(g.get(feet.relative(w.getOpposite()))) >= 1;
            if (walled && openColumn(g, feet.relative(a)) && openColumn(g, feet.relative(a.getOpposite()))) {
                flag(out, st, "HEAD_DENT", p, "block " + name(s) + " pokes down into a corridor at head height (neighbours on the " + a + "/" + a.getOpposite()
                        + " sides have 3+ blocks of room over the same floor)");
                return;
            }
        }
    }

    private static boolean openColumn(Grid g, BlockPos feet) {
        return BuildAudit.top(g.get(feet)) == 0 && BuildAudit.top(g.get(feet.above())) == 0 && BuildAudit.top(g.get(feet.above(2))) == 0
                && BuildAudit.top(g.get(feet.below())) >= 1 && g.get(feet).getFluidState().isEmpty();
    }

    // 4 方向が 1 高い床の中で 1 マスだけ 1 低い (床ブロックが 1 つ低く埋まった)
    private static void dimple(Grid g, BlockPos p, BlockState s, String st, Report out) {
        // p はその柱の床の丸ごとブロック。上 2 マスが空き、4 方向の柱の同じ高さの隣 (p と同じ y) が丸ごとの床で、その上も空き
        if (BuildAudit.top(g.get(p.above())) != 0 || BuildAudit.top(g.get(p.above(2))) != 0 || !g.get(p.above()).getFluidState().isEmpty()) {
            return;
        }
        // p の 1 つ上 (y+1) が、4 方向の床の上面。つまり p のすぐ横の 4 つは y+1 の床で、p の上は空き
        BlockPos q = p.above();
        for (Direction d : H) {
            BlockPos n = q.relative(d);
            if (BuildAudit.top(g.get(n)) < 1 || BuildAudit.top(g.get(n.above())) != 0 || BuildAudit.top(g.get(n.above(2))) != 0 || BuildAudit.top(g.get(n.above(3))) != 0) {
                return;
            }
        }
        // 上が屋根で覆われた 1 マスの段 (天井が低い) は除く。くぼみの上が 3 マス空いていること
        if (BuildAudit.top(g.get(q.above())) != 0 || BuildAudit.top(g.get(q.above(2))) != 0) {
            return;
        }
        flag(out, st, "FLOOR_DIMPLE", p, "single floor block " + name(s) + " is one lower than the floor on all four sides (buried one block too low?) ring=" + name(g.get(q.north()))
                + "/" + name(g.get(q.east())) + " below=" + name(g.get(p.below())));
    }

    // ---------------------------------------------------------------- 歩ける所 (route) の暗さ
    // route: BackReach.Report.reached (立つ位置の long)。4x4x4 のかたまりごとに 1 件
    // 岩鯨の下の迷路 (光の台の遊びのため、暗いのが設計) の中の立ち位置か。暗さの数えから外す (数は灯りの台の点き具合で動くので)
    public static boolean mazeDark(String st, BlockPos p) {
        if (!st.equals("whale")) {
            return false;
        }
        int x0 = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_X0;
        int z0 = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_Z0;
        int y0 = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_FLOOR;
        int span = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_CELL + 1;
        return p.getX() >= x0 && p.getX() <= x0 + jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_W * span
                && p.getZ() >= z0 && p.getZ() <= z0 + jp.revati.skyabyss.titan.dungeon.TitanBackLayout.MAZE_H * span
                && p.getY() >= y0 && p.getY() <= y0 + 3;
    }

    static void light(Grid g, LongOpenHashSet route, BackReach.Box box, String st, Report out) {
        java.util.Map<Long, int[]> cells = new java.util.LinkedHashMap<>();
        List<BlockPos> first = new ArrayList<>();
        LongIterator it = route.iterator();
        while (it.hasNext()) {
            BlockPos p = BlockPos.of(it.nextLong());
            // 封じた広間 (誰も入らない。灯りを置かない) は数えない
            if (!box.contains(p) || mazeDark(st, p) || jp.revati.skyabyss.trial.RouteSeals.inHall(st, p)) {
                continue;
            }
            int l = g.light(p);
            if (l < 0 || l > DARK) {
                continue;
            }
            long key = BlockPos.asLong(p.getX() >> 2, p.getY() >> 2, p.getZ() >> 2);
            int[] c = cells.get(key);
            if (c == null) {
                c = new int[] {0, l};
                cells.put(key, c);
                first.add(p);
            }
            c[0]++;
            c[1] = Math.min(c[1], l);
        }
        int i = 0;
        for (var e : cells.entrySet()) {
            flag(out, st, "LIGHT_DARK", first.get(i), e.getValue()[0] + " walkable cells in this 4x4x4 area have light level <= " + DARK + " (min " + e.getValue()[1] + "; first cell: " + g.lightInfo(first.get(i)) + ")");
            i++;
        }
    }

    static boolean isSlabTop(BlockState s) {
        return s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.TOP;
    }
}
