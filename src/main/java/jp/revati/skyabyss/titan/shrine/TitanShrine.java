package jp.revati.skyabyss.titan.shrine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.util.Players;

// 巨神の祠 (docs/design/32 の 3.1)。周回の順路の入口。巨神ごとに世界に一つ、地上に建つ。壊せない (台座 9×9 を守る)。
//   置き場 (仮): はじまりの都 (鐘) から、その巨神の跡地の方位 岩鯨 北・潮殻 東・雷翼 西・余燼 南) へ、距離の帯の近い端。
//     本当の置き場は、地図と地形の生成 が決まったら差し替える。心臓を初めて止めたとき (誰かが) に建てる (ensure)
//   形: 深層岩の台座 (9×9)・巨神ごとの材質の輪 (半径 3)・中央の碑 (titan_shrine)・台座の東の縁に共鳴の炉。まわりに順位の石と飾り板 (RebirthBoards)
//   使い方 (Wynncraft のレイドの入口と同じ。2026-11-03 の声「始まるときに集まる場所」): 拝殿の南の入口の外の黒い台 (集合台。PAD_*) に
//     パーティのオンラインの全員 (一人なら一人) が立つと、GATHER_TICKS の数え下ろしのあと、台の上の誰かの歌片が一つ減り (旅嚢か持ち物。代表が払う)、
//     雲の帳のあいだに台の上の人をその巨神の背へ運び、順路の段 1 へ (TrialRoute.start)。数えるあいだに誰かが台を降りると止まる
//   断るとき: 心臓を止めていない人が台にいる / 台の誰も歌片を持っていない / そのグループの順路がもう動いている (台の上の人の帯に理由)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanShrine {
    private TitanShrine() {
    }

    public static final int RING = 3;
    public static final int PEDESTAL = 4;
    // 集合台: 拝殿の南の入口 (ShrineArchitect の柱廊の口 z = 5) の前、下の屋根の軒 (±7) の外の黒い床。碑からの相対 x -PAD_HALF..PAD_HALF・z PAD_Z0..PAD_Z1
    public static final int PAD_HALF = 2;
    public static final int PAD_Z0 = 8;
    public static final int PAD_Z1 = 10;
    // 全員がそろってから始まるまで (5 秒)
    public static final int GATHER_TICKS = 100;
    // 雲の帳を上げるまで
    private static final int VEIL_TICKS = 30;
    // 誓いを選べる距離
    public static final double VOW_REACH = 8.0;

    // 数え下ろし中のグループ → {始めた tick, 最後に鳴らした秒}
    private static final Map<UUID, long[]> GATHER = new HashMap<>();

    // 心臓を止めたか (自己テストは差し替える)
    public interface Stopped {
        boolean test(MinecraftServer server, UUID player, String titan);
    }

    public static Stopped STOPPED = Progression::stopped;
    private static final Map<UUID, long[]> VEILS = new HashMap<>();

    // ---------------------------------------------------------------- 置き場
    // 跡地の方位 (x, z) と距離 (帯の近い端)
    static int[] bearing(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new int[] {1, 0, 800};
            case Titans.THUNDER_WING -> new int[] {-1, 0, 1000};
            case Titans.EMBER -> new int[] {0, 1, 1200};
            default -> new int[] {0, -1, 280};
        };
    }

    // はじまりの都の鐘 (なければ宿り、それもなければ世界の出発点)
    static BlockPos town(MinecraftServer server) {
        return jp.revati.skyabyss.opening.OpeningSites.bell(server)
                .or(() -> jp.revati.skyabyss.opening.OpeningSites.shelter(server))
                .orElse(server.overworld().getSharedSpawnPos());
    }

    // 祠の中央の碑の位置 (まだ建っていなければ null)
    @Nullable
    public static BlockPos posOf(MinecraftServer server, String titan) {
        return Data.get(server).pos.get(titan);
    }

    // 建てた記録を消す (地上を作り直したとき。upgrade/ContinentRegen が、建っていた巨神の分を ensure で今の場所に建て直す)。記録があったか
    public static boolean forget(MinecraftServer server, String titan) {
        Data data = Data.get(server);
        if (data.pos.remove(titan) == null) {
            return false;
        }
        data.setDirty();
        return true;
    }

    // 祠を建てる (もう建っていれば、その位置)。チャンクを読み込む (心臓を止めたときに一度)
    public static BlockPos ensure(MinecraftServer server, String titan) {
        Data data = Data.get(server);
        BlockPos have = data.pos.get(titan);
        if (have != null) {
            // 集合台より前に建った祠には、ここでも台を塗る
            if (server.overworld().hasChunkAt(have) && !padPainted(server.overworld(), have)) {
                paintPad(server.overworld(), have);
            }
            return have;
        }
        ServerLevel level = server.overworld();
        // 跡地 (ChapterGrounds) が取っておいた台座の場所。跡地がまだ決まっていない世界だけ、方位と距離の帯の近い端
        // 大陸: 決まった道ばたの広場 (world.continent.ContinentLairs.shrine。2026-10-28 の試遊「祠が地形になじまない、どうやって行けばいい」)
        int[] fixed = jp.revati.skyabyss.world.continent.ContinentLairs.shrine(server.overworld(), titan);
        BlockPos spot = fixed != null ? new BlockPos(fixed[0], 0, fixed[1])
                : jp.revati.skyabyss.opening.site.ChapterGrounds.shrineSpot(server, titan).orElse(null);
        int x;
        int z;
        if (spot != null) {
            x = spot.getX();
            z = spot.getZ();
        } else {
            int[] b = bearing(titan);
            BlockPos t = town(server);
            x = t.getX() + b[0] * b[2];
            z = t.getZ() + b[1] * b[2];
        }
        level.getChunk(x >> 4, z >> 4);
        int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), level.getSeaLevel() + 1);
        BlockPos center = new BlockPos(x, y, z);
        build(level, center, titan);
        data.pos.put(titan, center);
        data.setDirty();
        SkyAbyss.LOGGER.info("Built the shrine of the {} at {}", titan, center);
        return center;
    }

    // 輪の材質
    static BlockState ringBlock(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case Titans.THUNDER_WING -> Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
            case Titans.EMBER -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
            default -> Blocks.BONE_BLOCK.defaultBlockState();
        };
    }

    // 台座 (足もとの高さ center.y - 1 の 9×9)・輪・碑・四隅の灯り・炉。台座の下は地面まで深層岩で埋め、上は空ける
    static void build(ServerLevel level, BlockPos center, String titan) {
        int cx = center.getX();
        int cz = center.getZ();
        int top = center.getY() - 1;
        ShrineArchitect.Plan plan = ShrineArchitect.plan(level, center);
        ShrineArchitect.prepare(level, center, plan);
        for (int dx = -PEDESTAL; dx <= PEDESTAL; dx++) {
            for (int dz = -PEDESTAL; dz <= PEDESTAL; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double r = Math.sqrt(dx * dx + dz * dz);
                BlockState floor = Math.abs(r - RING) < 0.55 ? ringBlock(titan)
                        : Math.max(Math.abs(dx), Math.abs(dz)) == PEDESTAL ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
                level.setBlock(new BlockPos(x, top, z), floor, 2);
                for (int y = top - 1; y > top - 16; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) {
                        break;
                    }
                    level.setBlock(p, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
                }
                for (int y = top + 1; y <= top + 6; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(center, ModBlocks.TITAN_SHRINE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH)
                .setValue(RelicStyle.PROP, RelicStyle.ofTitan(titan)), 3);
        for (int sx : new int[] {-PEDESTAL, PEDESTAL}) {
            for (int sz : new int[] {-PEDESTAL, PEDESTAL}) {
                level.setBlock(new BlockPos(cx + sx, top + 1, cz + sz), Blocks.LANTERN.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(cx + PEDESTAL, top + 1, cz), ModBlocks.RESONANCE_FORGE.get().defaultBlockState()
                .setValue(jp.revati.skyabyss.forge.ResonanceForgeBlock.FACING, Direction.WEST), 3);
        int blocks = ShrineArchitect.finish(level, center, titan, plan);
        paintPad(level, center);
        SkyAbyss.LOGGER.info("Shrine of the {} architecture: {} blocks, path to z+{}", titan, blocks, plan.end());
    }

    // 台座の守る箱 (壊せない)
    static AABB guard(BlockPos center) {
        return new AABB(center.getX() - ShrineArchitect.TERRACE, center.getY() - 2, center.getZ() - ShrineArchitect.TERRACE, center.getX() + ShrineArchitect.TERRACE + 1,
                center.getY() + 24, center.getZ() + ShrineArchitect.TERRACE + 1);
    }

    // 集合台の床 (黒) を塗る。古い世界の祠にも、人が近づいたときに塗る (onServerTick)
    static final BlockState PAD_BLOCK = Blocks.BLACK_CONCRETE.defaultBlockState();

    static void paintPad(ServerLevel level, BlockPos center) {
        int top = center.getY() - 1;
        for (int dx = -PAD_HALF; dx <= PAD_HALF; dx++) {
            for (int dz = PAD_Z0; dz <= PAD_Z1; dz++) {
                level.setBlock(new BlockPos(center.getX() + dx, top, center.getZ() + dz), PAD_BLOCK, 2);
            }
        }
    }

    static boolean padPainted(ServerLevel level, BlockPos center) {
        return level.getBlockState(center.offset(0, -1, PAD_Z0 + 1)).equals(PAD_BLOCK);
    }

    // 集合台の上か
    public static boolean onPad(ServerPlayer p, BlockPos center) {
        double x = p.getX() - center.getX();
        double z = p.getZ() - center.getZ();
        return p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && !p.isSpectator() && p.isAlive()
                && x >= -PAD_HALF && x < PAD_HALF + 1 && z >= PAD_Z0 && z < PAD_Z1 + 1 && Math.abs(p.getY() - center.getY()) <= 2;
    }

    // 祠のそばか (誓いを選べる)
    public static boolean near(ServerPlayer p, String titan) {
        BlockPos c = posOf(p.server, titan);
        return c != null && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) <= VOW_REACH * VOW_REACH;
    }

    // 祠の輪の南の縁へ送る (あきらめた周回の順路の出口)。送れたか
    public static boolean sendTo(ServerPlayer p, String titan) {
        BlockPos c = posOf(p.server, titan);
        if (c == null) {
            return false;
        }
        p.stopRiding();
        p.teleportTo(p.server.overworld(), c.getX() + 0.5, c.getY(), c.getZ() + 2.5, 180f, 0f);
        p.fallDistance = 0;
        return true;
    }

    // ---------------------------------------------------------------- 使う
    // 右クリック: 祠の画面 (しゃがむと誓いの一覧)
    static void open(ServerPlayer sp, String titan, boolean sneak) {
        if (sneak) {
            jp.revati.skyabyss.titan.vow.VowCommands.show(sp, titan);
            return;
        }
        jp.revati.skyabyss.trial.TrialBoard.open(sp, titan);
        if (!Progression.stopped(sp.server, sp.getUUID(), titan)) {
            Notice.hint(sp, "message.skyabyss.shrine.unknown");
        }
    }

    // 歌片を持って碑に触れた: 祠の画面と、集合台の案内
    static void shardTouch(ServerPlayer sp, String titan) {
        open(sp, titan, false);
        Notice.hint(sp, "message.skyabyss.shrine.pad_hint");
    }

    // 断る理由 (null なら使える)。純粋に近い部分 (自己テストが直接呼ぶ)
    @Nullable
    public static Component refusal(MinecraftServer server, ServerPlayer by, String titan, List<ServerPlayer> ring, boolean hasShard, boolean routeActive) {
        if (!STOPPED.test(server, by.getUUID(), titan)) {
            return ModText.deny("message.skyabyss.shrine.unknown");
        }
        for (ServerPlayer p : ring) {
            if (!STOPPED.test(server, p.getUUID(), titan)) {
                return ModText.deny("message.skyabyss.shrine.member_unknown", p.getGameProfile().getName());
            }
        }
        if (!hasShard) {
            return ModText.deny("message.skyabyss.shrine.no_shard", ModItems.songShard(titan).getDescription());
        }
        if (routeActive) {
            return ModText.deny("message.skyabyss.shrine.busy");
        }
        return null;
    }

    // 集合台の上の、その人のグループのオンラインの人 (本人を先に)
    public static List<ServerPlayer> ring(ServerPlayer by, BlockPos center) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(by);
        UUID group = Groups.keyOf(by);
        for (ServerPlayer p : Players.snapshot(by.server.getPlayerList().getPlayers())) {
            if (p != by && Groups.keyOf(p).equals(group) && onPad(p, center)) {
                out.add(p);
            }
        }
        return out;
    }

    // 歌片を払える人 (旅嚢か持ち物に一つでもある。クリエイティブは払わない)
    static boolean hasShard(ServerPlayer p, String titan) {
        return p.isCreative() || jp.revati.skyabyss.pouch.Pouch.countAll(p, ModItems.songShard(titan)) > 0;
    }

    public static void activate(ServerPlayer by, String titan, BlockPos pos) {
        MinecraftServer server = by.server;
        ServerLevel back = Titans.hasBack(titan) ? server.getLevel(Titans.backOf(titan)) : null;
        if (back == null) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.no_back"));
            return;
        }
        UUID group = Groups.keyOf(by);
        List<ServerPlayer> ring = ring(by, pos);
        Component why = refusal(server, by, titan, ring, hasShard(by, titan), TrialRoute.active(back, group));
        if (why != null) {
            Notice.bar(by, why);
            return;
        }
        if (!by.isCreative()) {
            jp.revati.skyabyss.pouch.Pouch.takeAny(by, ModItems.songShard(titan), 1);
        }
        // 輪の外の仲間には、名前つきの一行
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : ring) {
            names.add(p.getGameProfile().getName());
        }
        for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
            if (Groups.keyOf(p).equals(group) && !ring.contains(p)) {
                Notice.hint(p, "message.skyabyss.shrine.left_behind", String.join("・", names));
            }
        }
        int style = Titans.TIDE_SHELL.equals(titan) ? 1 : 0;
        BlockPos arrival = ReturnBeacons.arrivalOf(titan);
        long now = server.getTickCount();
        for (ServerPlayer p : ring) {
            Notice.narrate(p, "message.skyabyss.shrine.song");
            PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.travel.TravelNetwork.Veil(true, style));
            VEILS.put(p.getUUID(), new long[] {now + VEIL_TICKS, style});
            p.stopRiding();
            p.teleportTo(back, arrival.getX() + 0.5, arrival.getY() + 1, arrival.getZ() + 0.5, p.getYRot(), 0f);
            p.fallDistance = 0;
            jp.revati.skyabyss.lore.Lore.unlock(p, loreId(titan), true);
        }
        // 区画の写しは帳のあいだに tick ごとに少しずつ (一 tick に止まらない)。段 1 の写しが済んだら運ばれる
        TrialRoute.startSoon(back, group, titan, ring);
        SkyAbyss.LOGGER.info("{} used the shrine of the {} with {} in the ring", by.getGameProfile().getName(), titan, names);
    }

    public static String loreId(String titan) {
        return "chapter1/shrine_" + jp.revati.skyabyss.trial.TrialRegions.signalTitan(titan);
    }

    // ---------------------------------------------------------------- 毎 tick: 雲の帳を上げる・祠のまわり (順位の石と飾り板)
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (!VEILS.isEmpty()) {
            for (Map.Entry<UUID, long[]> e : new ArrayList<>(VEILS.entrySet())) {
                if (now < e.getValue()[0]) {
                    continue;
                }
                VEILS.remove(e.getKey());
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    PacketDistributor.sendToPlayer(p, new jp.revati.skyabyss.titan.travel.TravelNetwork.Veil(false, (int) e.getValue()[1]));
                }
            }
        }
        if (now % 10 == 3) {
            gather(server, now);
        }
        if (now % 200 != 37) {
            return;
        }
        ServerLevel level = server.overworld();
        for (Map.Entry<String, BlockPos> e : Data.get(server).pos.entrySet()) {
            BlockPos c = e.getValue();
            if (!level.hasChunkAt(c)) {
                continue;
            }
            boolean someone = false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < 48 * 48) {
                    someone = true;
                    break;
                }
            }
            if (someone) {
                // 碑が無くなっていたら (古い世界の書き換え・コマンド) 建て直す
                if (!level.getBlockState(c).is(ModBlocks.TITAN_SHRINE.get())) {
                    build(level, c, e.getKey());
                } else if (!padPainted(level, c)) {
                    paintPad(level, c);
                }
                jp.revati.skyabyss.rebirth.RebirthBoards.install(level, e.getKey(), c);
            }
        }
    }

    // 集合台: 台の上のグループごとに、全員がそろったら数え下ろし、0 で始める (0.5 秒ごと)
    static void gather(MinecraftServer server, long now) {
        ServerLevel level = server.overworld();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Map.Entry<String, BlockPos> e : Data.get(server).pos.entrySet()) {
            String titan = e.getKey();
            BlockPos c = e.getValue();
            if (!level.hasChunkAt(c)) {
                continue;
            }
            Map<UUID, List<ServerPlayer>> pads = new java.util.LinkedHashMap<>();
            for (ServerPlayer p : Players.snapshot(level.players())) {
                if (onPad(p, c)) {
                    pads.computeIfAbsent(Groups.keyOf(p), k -> new ArrayList<>()).add(p);
                }
            }
            for (Map.Entry<UUID, List<ServerPlayer>> g : pads.entrySet()) {
                UUID group = g.getKey();
                List<ServerPlayer> on = g.getValue();
                int members = 0;
                for (ServerPlayer p : Players.snapshot(server.getPlayerList().getPlayers())) {
                    if (!p.isSpectator() && Groups.keyOf(p).equals(group)) {
                        members++;
                    }
                }
                if (on.size() < members) {
                    for (ServerPlayer p : on) {
                        Notice.bar(p, ModText.progress("message.skyabyss.shrine.pad_wait", on.size(), members));
                    }
                    continue;
                }
                ServerPlayer payer = on.get(0);
                for (ServerPlayer p : on) {
                    if (hasShard(p, titan)) {
                        payer = p;
                        break;
                    }
                }
                ServerLevel back = Titans.hasBack(titan) ? server.getLevel(Titans.backOf(titan)) : null;
                Component why = back == null ? ModText.deny("message.skyabyss.shrine.no_back")
                        : refusal(server, payer, titan, on, hasShard(payer, titan), TrialRoute.active(back, group));
                if (why != null) {
                    for (ServerPlayer p : on) {
                        Notice.bar(p, why);
                    }
                    continue;
                }
                seen.add(group);
                long[] st = GATHER.computeIfAbsent(group, k -> new long[] {now, -1});
                long left = GATHER_TICKS - (now - st[0]);
                if (left <= 0) {
                    seen.remove(group);
                    activate(payer, titan, c);
                    continue;
                }
                long sec = (left + 19) / 20;
                for (ServerPlayer p : on) {
                    Notice.bar(p, ModText.progress("message.skyabyss.shrine.pad_count", sec));
                }
                if (st[1] != sec) {
                    st[1] = sec;
                    level.playSound(null, c.offset(0, 0, PAD_Z0 + 1), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8f, sec == 1 ? 1.4f : 0.9f);
                }
            }
        }
        // 台を降りた・そろわなくなったグループの数え下ろしは消す (始めたグループも)
        GATHER.keySet().retainAll(seen);
    }

    // 台座は壊せない (クリエイティブは除く。33 の保護は祠の台座 9×9 だけ)
    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD
                || event.getPlayer() == null || event.getPlayer().isCreative()) {
            return;
        }
        for (BlockPos c : Data.get(level.getServer()).pos.values()) {
            if (guard(c).contains(event.getPos().getCenter())) {
                event.setCanceled(true);
                Notice.bar(event.getPlayer(), ModText.deny("message.skyabyss.shrine.guarded"));
                return;
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        GATHER.clear();
        VEILS.clear();
        STOPPED = Progression::stopped;
    }

    // ---------------------------------------------------------------- 記録 (オーバーワールド)
    public static final class Data extends SavedData {
        private static final String NAME = "skyabyss_titan_shrines";
        final Map<String, BlockPos> pos = new HashMap<>();

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            for (String titan : tag.getAllKeys()) {
                d.pos.put(titan, BlockPos.of(tag.getLong(titan)));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            pos.forEach((t, p) -> tag.putLong(t, p.asLong()));
            return tag;
        }

        // 自己テスト用
        public void forget(String titan) {
            pos.remove(titan);
            setDirty();
        }
    }
}
