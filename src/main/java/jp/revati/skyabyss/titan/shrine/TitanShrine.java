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
import net.minecraft.world.item.ItemStack;
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
//   置き場 (仮): はじまりの都 (鐘) から、その巨神の跡地の方位 (33 の 3 章: 岩鯨 北・潮殻 東・雷翼 西・余燼 南) へ、距離の帯の近い端。
//     本当の置き場は、地図と地形の生成 (33 の W2) が決まったら差し替える。心臓を初めて止めたとき (誰かが) に建てる (ensure)
//   形: 深層岩の台座 (9×9)・巨神ごとの材質の輪 (半径 3)・中央の碑 (titan_shrine)・台座の東の縁に共鳴の炉。まわりに順位の石と飾り板 (RebirthBoards)
//   使い方: パーティ全員 (一人なら一人) が輪の中に立ち、その巨神の歌片を持った人が碑を長押し (USE_HOLD tick) → 品が一つ減り、
//     雲の帳のあいだに輪の中の人をその巨神の背へ運び、順路の段 1 へ (TrialRoute.start)。輪の外の仲間は運ばない
//   断るとき: 心臓を止めていない人が輪にいる / 歌片がない / そのグループの順路がもう動いている
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanShrine {
    private TitanShrine() {
    }

    public static final int RING = 3;
    public static final int PEDESTAL = 4;
    // 長押し: 碑を右クリックし続けて (4 tick ごとに届く) この長さで成立。間が GAP を超えたら最初から
    public static final int USE_HOLD = 30;
    private static final int USE_GAP = 8;
    // 雲の帳を上げるまで
    private static final int VEIL_TICKS = 30;
    // 誓いを選べる距離
    public static final double VOW_REACH = 8.0;

    private record Hold(String titan, long start, long last) {
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    // 心臓を止めたか (自己テストは差し替える)
    public interface Stopped {
        boolean test(MinecraftServer server, UUID player, String titan);
    }

    public static Stopped STOPPED = Progression::stopped;
    private static final Map<UUID, long[]> VEILS = new HashMap<>();

    // ---------------------------------------------------------------- 置き場
    // 跡地の方位 (x, z) と距離 (33 の 3 章の帯の近い端)
    static int[] bearing(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new int[] {1, 0, 800};
            case Titans.THUNDER_WING -> new int[] {-1, 0, 1000};
            case Titans.EMBER -> new int[] {0, 1, 1200};
            default -> new int[] {0, -1, 300};
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

    // 祠を建てる (もう建っていれば、その位置)。チャンクを読み込む (心臓を止めたときに一度)
    public static BlockPos ensure(MinecraftServer server, String titan) {
        Data data = Data.get(server);
        BlockPos have = data.pos.get(titan);
        if (have != null) {
            return have;
        }
        ServerLevel level = server.overworld();
        int[] b = bearing(titan);
        BlockPos t = town(server);
        int x = t.getX() + b[0] * b[2];
        int z = t.getZ() + b[1] * b[2];
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
    }

    // 台座の守る箱 (壊せない)
    static AABB guard(BlockPos center) {
        return new AABB(center.getX() - PEDESTAL, center.getY() - 2, center.getZ() - PEDESTAL, center.getX() + PEDESTAL + 1, center.getY() + 5,
                center.getZ() + PEDESTAL + 1);
    }

    // 輪の中か
    public static boolean inRing(ServerPlayer p, BlockPos center) {
        double dx = p.getX() - (center.getX() + 0.5);
        double dz = p.getZ() - (center.getZ() + 0.5);
        return p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && dx * dx + dz * dz <= (RING + 0.5) * (RING + 0.5)
                && Math.abs(p.getY() - center.getY()) <= 3;
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

    // 歌片を持って右クリックし続ける。USE_HOLD tick 続いたら使う
    static void hold(ServerPlayer sp, String titan, BlockPos pos) {
        long now = sp.server.getTickCount();
        Hold h = HOLDS.get(sp.getUUID());
        if (h == null || !h.titan().equals(titan) || now - h.last() > USE_GAP) {
            h = new Hold(titan, now, now);
        } else {
            h = new Hold(titan, h.start(), now);
        }
        long held = now - h.start();
        if (held >= USE_HOLD) {
            HOLDS.remove(sp.getUUID());
            activate(sp, titan, pos);
            return;
        }
        HOLDS.put(sp.getUUID(), h);
        int dots = 1 + (int) (held * 3 / USE_HOLD);
        Notice.bar(sp, ModText.progress("message.skyabyss.shrine.filling", "・".repeat(dots)));
        if (held == 0) {
            sp.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 0.7f);
        }
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

    // 輪の中の、その人のグループのオンラインの人 (本人を先に)
    public static List<ServerPlayer> ring(ServerPlayer by, BlockPos center) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(by);
        UUID group = Groups.keyOf(by);
        for (ServerPlayer p : Players.snapshot(by.server.getPlayerList().getPlayers())) {
            if (p != by && !p.isSpectator() && p.isAlive() && Groups.keyOf(p).equals(group) && inRing(p, center)) {
                out.add(p);
            }
        }
        return out;
    }

    static void activate(ServerPlayer by, String titan, BlockPos pos) {
        MinecraftServer server = by.server;
        ServerLevel back = Titans.hasBack(titan) ? server.getLevel(Titans.backOf(titan)) : null;
        if (back == null) {
            Notice.bar(by, ModText.deny("message.skyabyss.shrine.no_back"));
            return;
        }
        UUID group = Groups.keyOf(by);
        List<ServerPlayer> ring = ring(by, pos);
        ItemStack hand = by.getMainHandItem();
        boolean hasShard = by.isCreative() || hand.is(ModItems.songShard(titan));
        Component why = refusal(server, by, titan, ring, hasShard, TrialRoute.active(back, group));
        if (why != null) {
            Notice.bar(by, why);
            return;
        }
        if (!by.isCreative()) {
            hand.shrink(1);
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
        TrialRoute.start(back, group, titan, ring);
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
                }
                jp.revati.skyabyss.rebirth.RebirthBoards.install(level, e.getKey(), c);
            }
        }
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
        HOLDS.clear();
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
