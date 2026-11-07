package jp.revati.skyabyss.titan.storm.chapter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.titan.storm.chapter.mob.LightningGleaner;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 雷拾いの巣 (三章の支度の段「空の石」。2026-11-04 の決めごと c3_play = nest。追跡)。
// 雷拾いは光る石 (空石) を巣へ運ぶ。雷拾いの原 (Ch3Ground.FIELD) から、焼けて硝子になった砂の筋をたどると、風読みの崖 (Ch3Ground.CLIFF) のそばの巣に着く。
// 目印の矢印は出さない。市の素材屋の空石・武具屋の胴は残す (急ぐ人向け。頼みの目的は「どれか一つ」)。
//   - 筋: 原から巣までの道のりの FRACTIONS の所 (左右に互い違いに ZIG ずらす)。地面の上の面を 4 マス、次のほうへ向けて硝子に。近づくと (GLINT) 青白い光の粒
//   - 見つける: FIND の中に入ると合図 ch3_glass_streak (三つで段 trail が済む) と、その筋の小さな手がかりの字幕
//   - 巣: 崖の中心から原と直角の向きへ NEST_OFF の自然の地面に、樽 (巣の底) と枯れ草 4・避雷針 1。近づくと金の光の粒。
//     その段の人が NEST_NEAR に来ると、雷拾いが 2 羽まで巣のまわりにとどまる (保存しない)。雷拾いは人が近づくと原へ逃げる (= 追い払う)
//   - 巣の底: 雷拾いが GUARD の中にいる間は開かない。いなければ人ごとに一度、空石 5 (SiteTreasure ch3_gleaner_nest)
// 建てるのは、その頼みを進めている人が BUILD の中に来たとき (チャンクが読まれていれば)。世界に一度 (SiteData の KIND。消えていれば建て直す)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GleanerNest {
    public static final String QUEST = "ch3/sky_stone";
    public static final String SIGNAL = "ch3_glass_streak";
    public static final String SIGNAL_NEST = "ch3_gleaner_nest";
    public static final String TREASURE = "ch3_gleaner_nest";
    static final String KIND = "ch3_gleaner_trail";
    static final String FOUND = "skyabyss_glass_streaks";
    static final String NEST_TAG = "skyabyss_gleaner_nest";
    static final double[] FRACTIONS = {0.25, 0.5, 0.75};
    static final int NEST_INDEX = FRACTIONS.length;
    static final double ZIG = 8;
    static final int NEST_OFF = 7;
    static final int NATURAL_R = 6;
    static final double FIND = 4;
    static final double BUILD = 72;
    static final double GLINT = 40;
    static final double NEST_NEAR = 40;
    static final double GUARD = 5;
    static final int BIRDS = 2;
    static final int STREAK_LEN = 4;

    private GleanerNest() {
    }

    static void register() {
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:sky_stone", 5))));
    }

    // この人が頼み「空の石」を進めているか
    public static boolean tracking(ServerPlayer p) {
        QuestLog.Entry e = QuestLog.of(p).entry(QUEST);
        return e != null && !e.done;
    }

    @Nullable
    static BlockPos nearest(MinecraftServer server, String id, @Nullable BlockPos from) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (ChapterSites.Site s : ChapterSites.sites(server, id)) {
            if (!s.built()) {
                continue;
            }
            double d = from == null ? 0 : Math.hypot(s.pos().getX() - from.getX(), s.pos().getZ() - from.getZ());
            if (d < bd) {
                bd = d;
                best = s.pos();
            }
        }
        return best;
    }

    // 筋 3 つと巣の置き場 (高さは 0。建てるときに地面を見る)。原か崖がなければ空
    public static List<BlockPos> spots(MinecraftServer server) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos field = nearest(server, Ch3Ground.FIELD, null);
        BlockPos cliff = field == null ? null : nearest(server, Ch3Ground.CLIFF, field);
        if (field == null || cliff == null) {
            return out;
        }
        double dx = cliff.getX() - field.getX();
        double dz = cliff.getZ() - field.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        double ux = dx / len;
        double uz = dz / len;
        // 巣: 崖の中心から、原と直角の向きへ NEST_OFF (崖の舞台の上を避ける)
        BlockPos nest = new BlockPos((int) Math.round(cliff.getX() - uz * NEST_OFF), 0, (int) Math.round(cliff.getZ() + ux * NEST_OFF));
        double nx = nest.getX() - field.getX();
        double nz = nest.getZ() - field.getZ();
        double nlen = Math.max(1, Math.hypot(nx, nz));
        for (int i = 0; i < FRACTIONS.length; i++) {
            double side = (i % 2 == 0 ? 1 : -1) * ZIG;
            out.add(new BlockPos((int) Math.round(field.getX() + nx * FRACTIONS[i] - nz / nlen * side), 0,
                    (int) Math.round(field.getZ() + nz * FRACTIONS[i] + nx / nlen * side)));
        }
        out.add(nest);
        return out;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 11 || server.overworld() == null) {
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
        if (spots.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < spots.size(); i++) {
                BlockPos at = placed(level, i, spots, trackers);
                if (at == null) {
                    continue;
                }
                boolean nest = i == NEST_INDEX;
                boolean anyNear = false;
                for (ServerPlayer p : trackers) {
                    double d = Math.sqrt(p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
                    if (d <= GLINT) {
                        level.sendParticles(p, new DustParticleOptions(nest ? new Vector3f(0.95f, 0.78f, 0.3f) : new Vector3f(0.55f, 0.8f, 1.0f), 1.6f), true,
                                at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 8, 0.25, 0.9, 0.25, 0.0);
                    }
                    if (!nest && d <= FIND) {
                        find(p, i);
                    }
                    anyNear |= nest && d <= NEST_NEAR && !SiteTreasure.claimed(p, TREASURE);
                }
                if (nest) {
                    keepBirds(level, at, anyNear);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("[gleaner nest] tick failed", e);
        }
    }

    // i 番目の、建った置き場 (まだなら近くの人のために建てる。建てられなければ null)
    @Nullable
    static BlockPos placed(ServerLevel level, int i, List<BlockPos> spots, List<ServerPlayer> near) {
        SiteData data = SiteData.get(level);
        SiteData.Entry e = null;
        for (SiteData.Entry x : data.entries(KIND)) {
            if (x.index == i) {
                e = x;
            }
        }
        boolean nest = i == NEST_INDEX;
        // 巣の樽が消えていたら建て直す (筋は地面しだいで硝子にならない所もあるので、確かめない)
        if (e != null && e.built && nest && level.hasChunkAt(e.pos) && !level.getBlockState(e.pos).is(Blocks.BARREL)) {
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
        BlockPos at = nest ? buildNest(level, xz) : buildStreak(level, xz, spots.get(i + 1));
        if (e != null) {
            data.remove(e);
        }
        data.add(KIND, i, at, 0).built = true;
        data.setDirty();
        SkyAbyss.LOGGER.info("[gleaner nest] {} built at {}", nest ? "nest" : "streak " + i, at.toShortString());
        return at;
    }

    // 硝子の筋: 地面の上の面を STREAK_LEN マス、次のほうへ向けて硝子に。立つ所 (筋の頭の上) を返す
    static BlockPos buildStreak(ServerLevel level, BlockPos spot, BlockPos next) {
        BlockPos head = natural(level, spot);
        double dx = next.getX() - head.getX();
        double dz = next.getZ() - head.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        for (int k = 0; k < STREAK_LEN; k++) {
            int x = (int) Math.round(head.getX() + dx / len * k);
            int z = (int) Math.round(head.getZ() + dz / len * k);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos g = ground(level, x, z);
            BlockState over = level.getBlockState(g);
            if (!over.isAir() && over.canBeReplaced()) {
                level.setBlock(g, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockPos top = g.below();
            if (soil(level.getBlockState(top))) {
                level.setBlock(top, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return ground(level, head.getX(), head.getZ());
    }

    // 巣: 樽 (巣の底) を真ん中に、まわりに枯れ草 4 と避雷針 1。樽の場所を返す
    static BlockPos buildNest(ServerLevel level, BlockPos spot) {
        BlockPos at = natural(level, spot);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos g = ground(level, at.getX() + dx, at.getZ() + dz);
                BlockState s = level.getBlockState(g);
                if (!s.isAir() && s.canBeReplaced()) {
                    level.setBlock(g, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        at = ground(level, at.getX(), at.getZ());
        level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos b = ground(level, at.relative(d).getX(), at.relative(d).getZ());
            if (level.getBlockState(b).isAir() && Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, b)) {
                level.setBlock(b, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos rod = ground(level, at.getX() + 1, at.getZ() + 1);
        if (level.getBlockState(rod).isAir()) {
            level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return at;
    }

    static boolean soil(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
    }

    // spot に近い自然の地面 (土・草・砂・砂利・石。上が空きか草で、水のそばでない)。なければ spot の地面
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
                    if (soil(level.getBlockState(at.below())) && (level.getBlockState(at).isAir() || level.getBlockState(at).canBeReplaced())
                            && level.getFluidState(at).isEmpty() && level.getFluidState(at.below()).isEmpty()) {
                        return at;
                    }
                }
            }
        }
        return ground(level, spot.getX(), spot.getZ());
    }

    // 地面の上の空き (葉と草は数えない)
    static BlockPos ground(ServerLevel level, int x, int z) {
        BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        while (p.getY() > level.getMinBuildHeight() + 1 && level.getBlockState(p.below()).canBeReplaced()) {
            p = p.below();
        }
        return p;
    }

    // 巣のまわりの雷拾い: その段の人が近くにいる間、BIRDS 羽まで。いなければ帰す (保存しない)
    static void keepBirds(ServerLevel level, BlockPos nest, boolean near) {
        AABB box = new AABB(nest).inflate(NEST_NEAR, 16, NEST_NEAR);
        List<LightningGleaner> birds = level.getEntitiesOfClass(LightningGleaner.class, box, b -> b.getTags().contains(NEST_TAG));
        if (!near) {
            birds.forEach(LightningGleaner::discard);
            return;
        }
        for (int i = birds.size(); i < BIRDS; i++) {
            LightningGleaner bird = Ch3Mobs.LIGHTNING_GLEANER_TYPE.get().create(level);
            if (bird == null) {
                return;
            }
            double a = level.random.nextDouble() * Math.PI * 2;
            int x = nest.getX() + (int) Math.round(Math.cos(a) * 2);
            int z = nest.getZ() + (int) Math.round(Math.sin(a) * 2);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            bird.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0);
            bird.finalizeSpawn(level, level.getCurrentDifficultyAt(bird.blockPosition()), MobSpawnType.EVENT, null);
            level.addFreshEntity(bird);
            bird.addTag(NEST_TAG);
            bird.addTag(jp.revati.skyabyss.guide.SiteSpawner.TAG);
        }
    }

    // 見つけた筋の数
    public static int foundCount(ServerPlayer p) {
        return Integer.bitCount(p.getPersistentData().getInt(FOUND));
    }

    // i 番目の筋を見つけた (人ごとに一度)。新しく見つけたら true
    public static boolean find(ServerPlayer p, int i) {
        int mask = p.getPersistentData().getInt(FOUND);
        if ((mask & (1 << i)) != 0) {
            return false;
        }
        p.getPersistentData().putInt(FOUND, mask | (1 << i));
        Quests.signal(p, SIGNAL);
        Notice.hint(p, "message.skyabyss.ch3g.streak." + Integer.bitCount(mask | (1 << i)));
        return true;
    }

    // 巣の底を開く: 雷拾いがそばにいれば開かない。いなければ人ごとに一度、空石 5。取れたら true
    public static boolean openNest(ServerPlayer p, ServerLevel level, BlockPos at) {
        boolean guarded = !level.getEntitiesOfClass(LightningGleaner.class, new AABB(at).inflate(GUARD, 3, GUARD), b -> b.getTags().contains(NEST_TAG)).isEmpty();
        level.playSound(null, at, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.7f, 1.3f);
        if (SiteTreasure.claimed(p, TREASURE)) {
            Notice.narrate(p, "message.skyabyss.ch3g.nest.empty");
            return false;
        }
        if (guarded) {
            Notice.narrate(p, "message.skyabyss.ch3g.nest.guarded");
            return false;
        }
        SiteTreasure.claim(p, TREASURE);
        Quests.signal(p, SIGNAL_NEST);
        Notice.narrate(p, "message.skyabyss.ch3g.nest.found");
        return true;
    }

    @Nullable
    static BlockPos nestAt(MinecraftServer server) {
        for (SiteData.Entry e : SiteData.get(server.overworld()).entries(KIND)) {
            if (e.index == NEST_INDEX && e.built) {
                return e.pos;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld() || !level.getBlockState(event.getPos()).is(Blocks.BARREL)) {
            return;
        }
        if (event.getPos().equals(nestAt(level.getServer()))) {
            event.setCanceled(true);
            openNest(player, level, event.getPos());
        }
    }

    // 自己テスト用
    static void forget(ServerPlayer p) {
        p.getPersistentData().remove(FOUND);
    }

    // 開発・撮影: /skyabyss_nest start (頼みを始め直す) | goto <0-3> (i 番目の筋・巣の 6 ブロック手前へ。3 = 巣)
    @SubscribeEvent
    static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("skyabyss_nest").requires(s -> s.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("start").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    forget(p);
                    Quests.forget(p, QUEST);
                    boolean ok = Quests.start(p, QUEST);
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("gleaner nest: started " + ok + ", spots " + spots(p.server)), false);
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("goto")
                        .then(net.minecraft.commands.Commands.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, NEST_INDEX)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            List<BlockPos> spots = spots(p.server);
                            int i = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "i");
                            if (i >= spots.size()) {
                                return 0;
                            }
                            BlockPos s = spots.get(i);
                            ServerLevel level = p.server.overworld();
                            level.getChunk(s.getX() >> 4, s.getZ() >> 4);
                            level.getChunk(s.getX() >> 4, (s.getZ() - 6) >> 4);
                            BlockPos g = ground(level, s.getX(), s.getZ() - 6);
                            p.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0f, 12f);
                            return 1;
                        }))));
    }
}
