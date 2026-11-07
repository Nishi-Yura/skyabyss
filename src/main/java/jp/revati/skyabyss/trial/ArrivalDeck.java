package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 到着の台の作りと、地上へ帰る石 (2026-11-07 の声「塀で囲むのは突貫工事すぎる。ちゃんと建築を凝る (最低でも岩鯨のように)。帰る場所も用意して。
// 巨神によっては全然帰れないことがあった」「岩鯨の集まる所に塀はない。建築ちゃんと凝って」)。
//   台 (潮殻・雷翼・余燼。岩鯨は TitanBackBuilder の左のわき腹の台): 部品 arrival_<巨神> (tools/gen/world/build_arrival_decks.py) を到着の足もとに置く。
//     床 ±6 (縁の帯・十字の道・紋・市松)、縁 ±7 は欄干 (塀の石 1 段。跳んで越せない)、±8 は植え込みの棚、外は一段ずつ下がる石組みの裾。
//     四隅に屋根つきの塔、三辺の真ん中に灯りの柱と旗、北の辺に帰りの石を納める切妻の祠。下は地面まで支える。石と飾りは巨神ごと
//   帰りの石 (天然磁石): 到着の台の祠の中と、心臓の前室 (休み場 heart) のそば。右クリックで地上へ (Titans.sendDown。笛がなくても帰れる)。
//     岩鯨の到着の台は錨石 (TitanTravel) がその役。名札は world/LodestoneLabels
//   背を建て直しても戻るように、人が背にいるあいだ CHECK_EVERY ごとに見て、欠けた所を置く (何度置いても同じ形)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArrivalDeck {
    public static final int RING = 7;
    static final int CHECK_EVERY = 200;
    static final int DOWN = 14;
    static final int UP = 2;

    // 巨神ごとの石: 支え・台石・塀・柱・灯・床
    public record Palette(BlockState footing, BlockState base, BlockState wall, BlockState post, BlockState light, BlockState floor, BlockState floor2) {
    }

    @Nullable
    public static Palette paletteOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(),
                    Blocks.PRISMARINE_WALL.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState());
            case Titans.THUNDER_WING -> new Palette(Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
                    Blocks.DIORITE_WALL.defaultBlockState(), Blocks.WAXED_CUT_COPPER.defaultBlockState(),
                    Blocks.LANTERN.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState());
            case Titans.STONE_WHALE -> new Palette(Blocks.DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.SOUL_LANTERN.defaultBlockState(),
                    Blocks.POLISHED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState());
            case Titans.EMBER -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
                    Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState(),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            default -> null;
        };
    }

    // 帰りの石の場所 (巨神 → 到着の台の石 / 前室の石)。前室の石は前室が建ってから決める
    private static final Map<String, BlockPos> HEART_STONES = new ConcurrentHashMap<>();

    private ArrivalDeck() {
    }

    // 待機場所の帰りの石 (立石の輪の祠の真ん中。部品の印 home_stone)。岩鯨は錨石も帰りの石の役 (TitanTravel)
    @Nullable
    public static BlockPos homeStone(String titan) {
        int[] m = mark(titan, "home_stone");
        return m == null ? null : RouteSeals.safeFeet(titan).offset(m[0], m[1], m[2]);
    }

    // 渦の中心 (足もとの高さ)
    @Nullable
    public static BlockPos vortex(String titan) {
        int[] m = mark(titan, "vortex");
        return m == null ? null : RouteSeals.safeFeet(titan).offset(m[0], m[1], m[2]);
    }

    // 部品の印 (足もとからのずれ)。部品がなければ null
    @Nullable
    static int[] mark(String titan, String name) {
        if (paletteOf(titan) == null) {
            return null;
        }
        Blueprint bp = Blueprint.get("arrival_" + titan);
        return bp == null ? null : bp.marks.get(name);
    }

    @Nullable
    public static BlockPos heartStone(String titan) {
        return HEART_STONES.get(titan);
    }

    // その背の帰りの石か
    public static boolean isReturnStone(ServerLevel level, BlockPos pos) {
        String titan = Titans.titanOfBack(level.dimension());
        return titan != null && (pos.equals(homeStone(titan)) || pos.equals(heartStone(titan)));
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 41) {
            return;
        }
        for (String titan : Titans.all()) {
            var dim = Titans.backOf(titan);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back != null && !back.players().isEmpty()) {
                place(back, titan);
                placeHeartStone(back, titan);
            }
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HEART_STONES.clear();
    }

    // 待機場所を置く (読み込まれていない所は飛ばす)。置いたブロックの数。
    // 部品 arrival_<巨神> (固い部品。tools/gen/world/build_arrival_lobbies.py) は部品どおりに置く (空気も: 広場の上・堀・渦・洞)。
    //   ただし残すもの (天然磁石・鐘・鎖・封じの戸・箱。岩鯨の背では錨石の鎖の骨の梁も) と、液体 (堀に入った海の水) はそのまま。
    // 部品 arrival_<巨神>_soft (柔らかい部品: 広場の下の岩の塊) は、空き・地形・前の台の石の所にだけ置く。
    // 塀・階段・鉄格子は置いたあとに隣とつなぐ形へ直す。前の版の帰りの石 (台の隅 / 北の祠) と見えない壁は外す
    public static int place(ServerLevel back, String titan) {
        if (paletteOf(titan) == null) {
            return 0;
        }
        Blueprint hard = Blueprint.get("arrival_" + titan);
        Blueprint soft = Blueprint.get("arrival_" + titan + "_soft");
        BlockPos f = RouteSeals.safeFeet(titan);
        boolean whale = titan.equals(Titans.STONE_WHALE);
        // 待機場所の広さのチャンクを先に読み込む (置くときの隣の形の直しが、まだ読まれていないチャンクを読み、二度目に残りを置くことになるので)
        for (Blueprint bp : new Blueprint[] {hard, soft}) {
            if (bp == null) {
                continue;
            }
            for (int cx = (f.getX() + bp.rx0 - 1) >> 4; cx <= (f.getX() + bp.rx1 + 1) >> 4; cx++) {
                for (int cz = (f.getZ() + bp.rz0 - 1) >> 4; cz <= (f.getZ() + bp.rz1 + 1) >> 4; cz++) {
                    back.getChunk(cx, cz);
                }
            }
        }
        int n = 0;
        List<BlockPos> shaped = new ArrayList<>();
        for (Blueprint.Cell c : hard.cells) {
            BlockPos p = f.offset(c.x(), c.y(), c.z());
            if (!back.hasChunkAt(p)) {
                continue;
            }
            BlockState now = back.getBlockState(p);
            BlockState want = c.state();
            if (now.getBlock() == want.getBlock() || keep(now, whale)) {
                continue;
            }
            if (want.isAir() && !now.getFluidState().isEmpty()) {
                continue;
            }
            back.setBlock(p, want, Block.UPDATE_CLIENTS);
            n++;
            Block wb = want.getBlock();
            if (wb instanceof WallBlock || wb instanceof StairBlock || wb instanceof IronBarsBlock) {
                shaped.add(p);
            }
        }
        if (soft != null) {
            for (Blueprint.Cell c : soft.cells) {
                BlockPos p = f.offset(c.x(), c.y(), c.z());
                if (!back.hasChunkAt(p)) {
                    continue;
                }
                BlockState now = back.getBlockState(p);
                if (now.getBlock() == c.state().getBlock() || keep(now, whale) || !(open(back, p) || natural(back, p) || oldDeck(now))) {
                    continue;
                }
                back.setBlock(p, c.state(), Block.UPDATE_CLIENTS);
                n++;
            }
        }
        // 堀や渦で掘った所のそばに、地形が一つだけ宙に残ったら外す (まわりの六面に固い物がない地形)
        if (n > 0) {
            java.util.Set<Long> mine = new java.util.HashSet<>();
            for (Blueprint bp : new Blueprint[] {hard, soft}) {
                if (bp != null) {
                    for (Blueprint.Cell c : bp.cells) {
                        mine.add(BlockPos.asLong(c.x(), c.y(), c.z()));
                    }
                }
            }
            for (Blueprint.Cell c : hard.cells) {
                if (!c.state().isAir()) {
                    continue;
                }
                BlockPos p = f.offset(c.x(), c.y(), c.z());
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos q = p.relative(d);
                    if (!mine.contains(BlockPos.asLong(c.x() + d.getStepX(), c.y() + d.getStepY(), c.z() + d.getStepZ())) && solid(back, q) && natural(back, q)
                            && lone(back, q)) {
                        back.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        n++;
                    }
                }
            }
        }
        for (BlockPos p : shaped) {
            BlockState st = back.getBlockState(p);
            BlockState fixed = Block.updateFromNeighbourShapes(st, back, p);
            if (fixed != st && !fixed.isAir()) {
                back.setBlock(p, fixed, Block.UPDATE_CLIENTS);
            }
        }
        // 前の版の帰りの石 (台の隅 +4, +4 / 北の祠 0, -6) を外す。帰りの石は立石の輪の祠へ移った
        BlockPos stone = homeStone(titan);
        for (BlockPos old : new BlockPos[] {f.offset(4, 0, 4), f.offset(0, 0, -6)}) {
            if (!old.equals(stone) && back.hasChunkAt(old) && back.getBlockState(old).is(Blocks.LODESTONE) && !(whale && old.equals(TitanBackAnchor.ANCHOR))) {
                back.setBlock(old, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
        if (stone != null && back.hasChunkAt(stone) && !back.getBlockState(stone).is(Blocks.LODESTONE)) {
            back.setBlock(stone, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
            n++;
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("Arrival lobby of the {}: placed {} blocks", titan, n);
        }
        return n;
    }

    // まわりの六面のどこにも固い物がない
    private static boolean lone(ServerLevel back, BlockPos q) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (solid(back, q.relative(d))) {
                return false;
            }
        }
        return true;
    }

    // 錨石の位置 (岩鯨。外さない)
    static final class TitanBackAnchor {
        static final BlockPos ANCHOR = jp.revati.skyabyss.titan.dungeon.TitanBackLayout.anchor();
    }

    // 残すもの: 天然磁石・鐘・鎖・封じの戸・箱など (旗は台の物なので置き換えてよい)。岩鯨の背では錨石の鎖の骨の梁も
    private static boolean keep(BlockState s, boolean whale) {
        if (s.is(Blocks.LODESTONE) || s.is(Blocks.BELL) || s.is(Blocks.CHAIN) || s.getBlock() instanceof jp.revati.skyabyss.titan.dungeon.SealedGateBlock) {
            return true;
        }
        if (whale && s.is(Blocks.BONE_BLOCK)) {
            return true;
        }
        return s.hasBlockEntity() && !(s.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock);
    }

    // 前の版の台の石 (ArrivalPen・前の ArrivalDeck の塀・柱・灯)
    private static boolean oldDeck(BlockState s) {
        for (String t : Titans.all()) {
            Palette p = paletteOf(t);
            if (p != null && (s.is(p.wall().getBlock()) || s.is(p.base().getBlock()) || s.is(p.post().getBlock()) || s.is(p.light().getBlock())
                    || s.is(p.footing().getBlock()))) {
                return true;
            }
        }
        return s.is(Blocks.BARRIER);
    }

    // ---------------------------------------------------------------- 落ちた人を戻す
    // 広場の外 (堀・渦・空・海・口の外の落ち込み) へ落ちた人を、足もとへ戻す。広場の中心から (印 radius + 4) の円の中で、
    // 足もとより 2.5 低く 48 までの所にいる人 (見物のほかの全員)。10 tick ごと。順路の部屋と心臓の間は遠くにあるので入らない
    static final double FALL_BELOW = 2.5;
    static final double FALL_DEPTH = 48;

    public static boolean fallen(String titan, double x, double y, double z) {
        int[] c = mark(titan, "center");
        int[] r = mark(titan, "radius");
        if (c == null || r == null) {
            return false;
        }
        BlockPos f = RouteSeals.safeFeet(titan);
        double cx = f.getX() + c[0] + 0.5;
        double cz = f.getZ() + c[2] + 0.5;
        double rad = r[0] + 4;
        return (x - cx) * (x - cx) + (z - cz) * (z - cz) <= rad * rad && y < f.getY() - FALL_BELOW && y > f.getY() - FALL_DEPTH;
    }

    @SubscribeEvent
    static void onFallTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 3) {
            return;
        }
        for (String titan : Titans.all()) {
            var dim = Titans.backOf(titan);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back == null || back.players().isEmpty() || paletteOf(titan) == null) {
                continue;
            }
            BlockPos f = RouteSeals.safeFeet(titan);
            for (ServerPlayer p : jp.revati.skyabyss.util.Players.snapshot(back.players())) {
                if (p.isSpectator() || !fallen(titan, p.getX(), p.getY(), p.getZ())) {
                    continue;
                }
                p.teleportTo(back, f.getX() + 0.5, f.getY(), f.getZ() + 0.5, p.getYRot(), 0f);
                p.resetFallDistance();
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                Notice.hint(p, "message.skyabyss.arrival.fell");
                SkyAbyss.LOGGER.info("{} fell off the {} arrival lobby and was set back", p.getGameProfile().getName(), titan);
            }
        }
    }

    // 心臓の前室の帰りの石: 休み場の立つ所のまわり (2〜4) で、足もとが空き・下が固い所。決めたら覚える
    public static void placeHeartStone(ServerLevel back, String titan) {
        BlockPos known = HEART_STONES.get(titan);
        if (known != null) {
            if (back.hasChunkAt(known) && back.getBlockState(known).isAir()) {
                back.setBlock(known, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
        BlockPos rest = jp.revati.skyabyss.trial.raid.InstanceArenas.heartRest(back, titan);
        if (rest == null || !back.hasChunkAt(rest)) {
            return;
        }
        BlockPos entry = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntry(titan);
        for (int r = 2; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = rest.offset(dx, dy, dz);
                        if (entry != null && p.distSqr(entry) < 9) {
                            continue;
                        }
                        // 壁ぎわ (隣のどれかが固い) で、置いても通り道を塞がない所
                        boolean wallSide = solid(back, p.north()) || solid(back, p.south()) || solid(back, p.east()) || solid(back, p.west());
                        if (back.getBlockState(p).isAir() && back.getBlockState(p.above()).isAir() && solid(back, p.below()) && wallSide
                                && !back.getBlockState(p.below()).is(Blocks.CAMPFIRE) && !back.getBlockState(p.below()).is(Blocks.SOUL_CAMPFIRE)) {
                            back.setBlock(p, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
                            HEART_STONES.put(titan, p.immutable());
                            SkyAbyss.LOGGER.info("Heart front room return stone of the {} at {}", titan, p.toShortString());
                            return;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 帰る
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p)
                || !(event.getLevel() instanceof ServerLevel level) || !level.getBlockState(event.getPos()).is(Blocks.LODESTONE)) {
            return;
        }
        if (!isReturnStone(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        String titan = Titans.titanOfBack(level.dimension());
        Notice.narrate(p, "message.skyabyss.return_beacon.descending");
        SkyAbyss.LOGGER.info("{} went down from the {} back by the return stone", p.getGameProfile().getName(), titan);
        Titans.sendDown(p, titan);
    }

    // ---------------------------------------------------------------- 調べ (自己テスト)
    // 足もとのまわり (半径 4) の床が抜けている、または頭の高さまでが塞がっている所の数 (0 なら集まる所は平らに立てる)
    public static int badStanding(ServerLevel back, String titan) {
        BlockPos f = RouteSeals.safeFeet(titan);
        int bad = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16) {
                    continue;
                }
                BlockPos feet = f.offset(dx, 0, dz);
                boolean ok = solid(back, feet.below()) && !solid(back, feet) && !solid(back, feet.above());
                if (!ok && !back.getBlockState(feet).is(Blocks.BELL)) {
                    bad++;
                }
            }
        }
        return bad;
    }

    // 均してよい地形 (背の砂浜・土・石)。台の物 (鐘・石・灯・模様の石・木) は含めない
    private static boolean natural(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        if (s.isAir() || s.is(Blocks.BELL) || s.is(Blocks.LODESTONE) || s.is(Blocks.LIGHT) || s.hasBlockEntity()) {
            return false;
        }
        return s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.CLAY) || s.is(net.minecraft.tags.BlockTags.TERRACOTTA) || s.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS)
                || s.getBlock().getDescriptionId().contains("concrete_powder") || s.is(Blocks.TUFF) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.SMOOTH_SANDSTONE)
                || s.is(Blocks.CUT_SANDSTONE) || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    private static boolean open(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty();
    }

    private static boolean solid(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return !s.getCollisionShape(back, p).isEmpty() && !s.is(Blocks.BARRIER);
    }

    // 下へ空いている所を、地面に着くまで (max 段) 支えの石で埋める
    private static int footing(ServerLevel back, BlockPos from, BlockState footing, int max) {
        int n = 0;
        BlockPos p = from;
        for (int i = 0; i < max && open(back, p); i++) {
            back.setBlock(p, footing, Block.UPDATE_ALL);
            n++;
            p = p.below();
        }
        return n;
    }
}
