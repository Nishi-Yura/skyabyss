package jp.revati.skyabyss.story.ch1;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.world.continent.ContinentLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 鉄の跡をたどる (一章の支度の段「鉄の跡」の最初の段 track。2026-11-04 の声「その頼みにしかない遊び (謎解き・追跡・潜入) を入れる」。追跡)。
// 鯨見の町から骨の坑 (ch1_bone_pit) へ、前の旅人の鉄くずが三つ落ちている。目印の矢印は出さない。
//   - 鉄くず: 町の外 (EDGE) から坑までの道のりの FRACTIONS の所 (左右に互い違いに ZIG ずらす。畑・水・道・建物の上ならそばの自然の地面へ)。
//     打ち捨てられた欠けた金床と、ほどけた鎖 (まわりの草は刈る)。近づくと (GLINT) 錆色の光の粒が昇る
//   - 鉄靴の跡: 鉄くずから次の鉄くず (最後は坑) のほうへ、PRINT_STEP おきに PRINTS 個、草や土を粗い土に (足跡の向きが次を指す)
//   - 見つける: FIND の中に入ると合図 ch1_iron_scrap (三つで段が済む) と、その鉄くずの小さな話 (手がかりの字幕)
// 建てるのは、その段の人が BUILD の中に来たとき (チャンクが読まれていれば)。世界に一度 (SiteData の KIND。消えていれば建て直す)
// 段 track は、坑の頁 (ch1/trail_iron) をもう読んだ人には飛ばす (クエストの skip_if)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class IronTrail {
    public static final String QUEST = "ch1/trail_iron";
    public static final String STAGE = "track";
    public static final String SIGNAL = "ch1_iron_scrap";
    static final String KIND = "ch1_iron_trail";
    static final String FOUND = "skyabyss_iron_scraps";
    static final double[] FRACTIONS = {0.18, 0.48, 0.78};
    // 道のりは町の畑と家の外 (中心から EDGE。鯨見の町は 70 ほどまで畑がある) から数える。坑が近ければ道のりの EDGE_SHARE まで
    static final double EDGE = 100;
    static final double EDGE_SHARE = 0.35;
    // 置き場が畑・水・道・建物の上なら、まわり NATURAL_R の中の自然の地面へずらす
    static final int NATURAL_R = 6;
    static final double ZIG = 14;
    static final double FIND = 3.5;
    static final double BUILD = 72;
    static final double GLINT = 40;
    static final int PRINTS = 6;
    static final int PRINT_STEP = 3;
    static final long NO_TRAIL_WAIT = 1200;
    // 跡がないまま段 track にいる人 → 気づいた tick
    private static final java.util.Map<java.util.UUID, Long> NO_TRAIL = jp.revati.skyabyss.util.PlayerMaps.track(new java.util.HashMap<>());

    private IronTrail() {
    }

    // 町と坑のあいだの鉄くずの置き場 (高さは 0。建てるときに地面を見る)。町か坑がなければ空
    public static List<BlockPos> spots(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos town = ContinentLayout.get().town("whale");
        BlockPos pit = pit(server, town);
        if (town == null || pit == null) {
            return out;
        }
        double dx = pit.getX() - town.getX();
        double dz = pit.getZ() - town.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        double ux = dx / len;
        double uz = dz / len;
        double edge = Math.min(EDGE, len * EDGE_SHARE);
        double sx = town.getX() + ux * edge;
        double sz = town.getZ() + uz * edge;
        double rest = len - edge;
        for (int i = 0; i < FRACTIONS.length; i++) {
            double side = (i % 2 == 0 ? 1 : -1) * ZIG;
            out.add(new BlockPos((int) Math.round(sx + ux * rest * FRACTIONS[i] - uz * side), 0,
                    (int) Math.round(sz + uz * rest * FRACTIONS[i] + ux * side)));
        }
        return out;
    }

    // 町にいちばん近い骨の坑
    @Nullable
    static BlockPos pit(MinecraftServer server, @Nullable BlockPos town) {
        if (town == null) {
            return null;
        }
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (ChapterSites.Site s : ChapterSites.sites(server, Ch1Prep.BONE_PIT)) {
            double d = Math.hypot(s.pos().getX() - town.getX(), s.pos().getZ() - town.getZ());
            if (d < bd) {
                bd = d;
                best = s.pos();
            }
        }
        return best;
    }

    // この人が段 track にいるか
    public static boolean tracking(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        QuestDef def = QuestManager.quest(QUEST);
        return e != null && !e.done && def != null && e.stage >= 0 && e.stage < def.stages().size() && STAGE.equals(def.stages().get(e.stage).id());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 13) {
            return;
        }
        List<ServerPlayer> trackers = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() == server.overworld() && !p.isSpectator() && tracking(p)) {
                trackers.add(p);
            }
        }
        if (trackers.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        List<BlockPos> spots = spots(server);
        // 町か坑がない世界 (章の場所の計画が遅れている・置けなかった): たどる跡がないまま NO_TRAIL_WAIT たったら、段を済ませて坑へ進ませる (詰まらせない)
        if (spots.isEmpty()) {
            long now = level.getGameTime();
            for (ServerPlayer p : trackers) {
                long since = NO_TRAIL.computeIfAbsent(p.getUUID(), k -> now);
                if (now - since >= NO_TRAIL_WAIT) {
                    NO_TRAIL.remove(p.getUUID());
                    skipAll(p);
                }
            }
            return;
        }
        NO_TRAIL.clear();
        for (int i = 0; i < spots.size(); i++) {
            BlockPos at = placed(level, i, spots, trackers);
            if (at == null) {
                continue;
            }
            for (ServerPlayer p : trackers) {
                double d = Math.sqrt(p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
                if (d <= GLINT) {
                    level.sendParticles(p, new DustParticleOptions(new Vector3f(0.78f, 0.42f, 0.18f), 2.0f), true,
                            at.getX() + 0.5, at.getY() + 1.4, at.getZ() + 0.5, 10, 0.2, 1.1, 0.2, 0.0);
                }
                if (d <= FIND) {
                    find(p, i);
                }
            }
        }
    }

    // i 番目の鉄くずの、建った置き場 (まだなら近くの人のために建てる。建てられなければ null)
    @Nullable
    static BlockPos placed(ServerLevel level, int i, List<BlockPos> spots, List<ServerPlayer> near) {
        SiteData data = SiteData.get(level);
        SiteData.Entry e = null;
        for (SiteData.Entry x : data.entries(KIND)) {
            if (x.index == i) {
                e = x;
            }
        }
        if (e != null && e.built && level.hasChunkAt(e.pos) && !level.getBlockState(e.pos).is(Blocks.DAMAGED_ANVIL)) {
            data.remove(e);
            e = null;
        }
        if (e != null && e.built) {
            return e.pos;
        }
        BlockPos xz = spots.get(i);
        boolean someoneNear = false;
        for (ServerPlayer p : near) {
            someoneNear |= Math.hypot(p.getX() - xz.getX(), p.getZ() - xz.getZ()) <= BUILD;
        }
        if (!someoneNear || !level.hasChunkAt(xz)) {
            return null;
        }
        BlockPos next = i + 1 < spots.size() ? spots.get(i + 1) : pit(level.getServer(), ContinentLayout.get().town("whale"));
        BlockPos at = build(level, xz, next);
        if (e != null) {
            data.remove(e);
        }
        data.add(KIND, i, at, 0).built = true;
        data.setDirty();
        SkyAbyss.LOGGER.info("Iron trail: scrap {} built at {}", i, at.toShortString());
        return at;
    }

    // 欠けた金床と、ほどけた鎖。そこから次の場所へ向かう鉄靴の跡。金床の場所を返す
    static BlockPos build(ServerLevel level, BlockPos spot, @Nullable BlockPos next) {
        BlockPos xz = natural(level, spot);
        // まわりの草を刈る (金床が草に埋もれて見えない)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos g = ground(level, xz.getX() + dx, xz.getZ() + dz);
                for (int up = 0; up < 2; up++) {
                    BlockPos q = g.above(up);
                    if (!level.getBlockState(q).isAir() && level.getBlockState(q).canBeReplaced()) {
                        level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        BlockPos at = ground(level, xz.getX(), xz.getZ());
        Direction.Axis axis = next != null && Math.abs(next.getX() - xz.getX()) > Math.abs(next.getZ() - xz.getZ()) ? Direction.Axis.X : Direction.Axis.Z;
        level.setBlock(at, Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING,
                axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH), Block.UPDATE_CLIENTS);
        BlockPos chain = axis == Direction.Axis.X ? at.south() : at.east();
        BlockPos chainAt = ground(level, chain.getX(), chain.getZ());
        if (level.getBlockState(chainAt).canBeReplaced()) {
            level.setBlock(chainAt, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis), Block.UPDATE_CLIENTS);
        }
        if (next == null) {
            return at;
        }
        double dx = next.getX() - at.getX();
        double dz = next.getZ() - at.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        for (int k = 1; k <= PRINTS; k++) {
            double t = 2 + k * PRINT_STEP;
            // 左右の足を交互に (進む向きに対して ±1)
            double side = k % 2 == 0 ? 1 : -1;
            int x = (int) Math.round(at.getX() + dx / len * t - dz / len * side);
            int z = (int) Math.round(at.getZ() + dz / len * t + dx / len * side);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos top = ground(level, x, z).below();
            if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) || level.getBlockState(top).is(Blocks.DIRT) || level.getBlockState(top).is(Blocks.PODZOL)) {
                level.setBlock(top, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                // 上の薄い雪や草をどける (雪の野では踏んだ所だけ土が見える)
                BlockState over = level.getBlockState(top.above());
                if (!over.isAir() && over.canBeReplaced()) {
                    level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return at;
    }

    // spot に近い自然の地面 (土・草・砂・砂利・石。上が空きか草で、となりに水がない)。畑や水路・道・建物の上には置かない。なければ spot のまま
    static BlockPos natural(ServerLevel level, BlockPos spot) {
        for (int r = 0; r <= NATURAL_R; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = spot.getX() + dx;
                    int z = spot.getZ() + dz;
                    if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                        continue;
                    }
                    BlockPos at = ground(level, x, z);
                    if (wild(level, at)) {
                        return at;
                    }
                }
            }
        }
        return spot;
    }

    static boolean wild(ServerLevel level, BlockPos at) {
        BlockState under = level.getBlockState(at.below());
        boolean soil = under.is(net.minecraft.tags.BlockTags.DIRT) || under.is(net.minecraft.tags.BlockTags.SAND) || under.is(Blocks.GRAVEL)
                || under.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
        if (!soil || !(level.getBlockState(at).isAir() || level.getBlockState(at).canBeReplaced()) || !level.getFluidState(at).isEmpty()) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!level.getFluidState(at.below().relative(d)).isEmpty() || !level.getFluidState(at.relative(d)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 地面の上の空き (葉と草は数えない)
    static BlockPos ground(ServerLevel level, int x, int z) {
        BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        while (p.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(p.below()).canBeReplaced()) {
            p = p.below();
        }
        return p;
    }

    // i 番目を見つけた (人ごとに一度)。新しく見つけたら true
    public static boolean find(ServerPlayer p, int i) {
        int mask = p.getPersistentData().getInt(FOUND);
        if ((mask & (1 << i)) != 0) {
            return false;
        }
        p.getPersistentData().putInt(FOUND, mask | (1 << i));
        Quests.signal(p, SIGNAL);
        Notice.hint(p, "message.skyabyss.ch1.iron_scrap." + Integer.bitCount(mask | (1 << i)));
        return true;
    }

    // 跡がないときに段を済ませる (字幕は出さない)
    static void skipAll(ServerPlayer p) {
        int mask = p.getPersistentData().getInt(FOUND);
        for (int i = 0; i < FRACTIONS.length; i++) {
            if ((mask & (1 << i)) == 0) {
                mask |= 1 << i;
                Quests.signal(p, SIGNAL);
            }
        }
        p.getPersistentData().putInt(FOUND, mask);
        SkyAbyss.LOGGER.info("Iron trail: no town or bone pit yet, skipped the track stage for {}", p.getName().getString());
    }

    // 自己テスト用
    static void forget(ServerPlayer p) {
        p.getPersistentData().remove(FOUND);
    }

    // 開発・撮影: /skyabyss_iron start (段 track から始め直す) | goto <0-2> (i 番目の鉄くずの 6 ブロック手前へ)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_iron").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("start").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    forget(p);
                    jp.revati.skyabyss.lore.Lore.lock(p, Ch1Prep.PAGE_IRON);
                    Quests.forget(p, QUEST);
                    boolean ok = Quests.start(p, QUEST);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("iron trail: started " + ok + ", tracking " + tracking(p)
                            + ", spots " + spots(p.server)), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("goto")
                        .then(net.minecraft.commands.Commands.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            List<BlockPos> spots = spots(p.server);
                            int i = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "i");
                            if (i >= spots.size()) {
                                return 0;
                            }
                            BlockPos s = spots.get(i);
                            ServerLevel level = p.server.overworld();
                            level.getChunk(s.getX() >> 4, s.getZ() >> 4);
                            // 立つ所が隣の区画のこともある (読み込まれていないと高さが世界の底になる)
                            level.getChunk(s.getX() >> 4, (s.getZ() - 6) >> 4);
                            BlockPos g = ground(level, s.getX(), s.getZ() - 6);
                            p.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 12f);
                            return 1;
                        }))));
    }
}
