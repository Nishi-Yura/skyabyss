package jp.revati.skyabyss.titan.tide.chapter;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 大干潮 (二章の中盤の出来事。01 の 4.2、08 の 2.8節)。巨神が現れたあと、海がいつもより遠くまで引き、浅い海底に遺跡の道が現れる。
// 人ごとに一度ずつ (自分の大干潮)。安全に歩ける (水は膝まで。道の両側は壁で、海は元のまま。溺れず、敵も入らない)。道の先に潮の民の頁。
//
//   始まり   巨神が現れていて (TideKeeper の記録)、まだ自分の大干潮を迎えていない二章 (段階 1) の人が海から 96 ブロック以内にいて (時刻では閉じない。2026-10-27)、
//            近くで途中の大干潮があればそれを共有し (道の枠から 128 ブロック以内)、なければその近くに道が通せる海があるとき (途中の道と重ならない所だけ)。道は岸の最初の水から沖へまっすぐ (北南東西のどれか) 24〜56 ブロック、幅 3、
//            海底に沿って (一歩の差は 1 まで、水深 1〜10)。海底の高さに床 (遺跡の石)、両側と先に壁 (海面まで。上の段は窓)、
//            中は膝まで (床の上 1 ブロック) だけ水を残し、上は空気。岸の側は陸を削って床へつなぐ
//   途中     壁の中から敵を除く。道の先 (台) に 3 ブロック以内まで来た人に、潮の民の頁 (伝承 chapter1/ch2_ebb_page) と合図 ch2_ebb_walked
//   終わり   始まりから MAX_TICKS で、水が戻る。壁と中の水は元の海へ (床と壁の根もとは海の底の遺跡の道として残る)。
//            中にいる人は岸へ戻す (水中呼吸つき)。終わる 400 tick 前に語りで知らせる
//   保存     EbbData (オーバーワールド。途中の大干潮の列と、自分の大干潮を迎えた人の UUID)。再起動をまたぐ。途中で止まっても、次に動いたとき時刻が過ぎていれば水を戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideEbb {
    public static final String LORE_PAGE = "chapter1/ch2_ebb_page";
    public static final String SIGNAL_WALKED = "ch2_ebb_walked";
    public static final String QUEST = "ch2/ebb_road";
    // 大干潮が始まったとき二章の人に立てる会話の印 (クエストの start_when が見る)
    public static final String STORYTELLER = "skyabyss:storyteller";
    public static final String FLAG_EBB = "ch2_ebb";

    public static final int LEN_MIN = 24;
    public static final int LEN_MAX = 56;
    // 始められる時刻 (dayTime % 24000)
    static final long MAX_TICKS = 8000;
    static final long WARN_BEFORE = 400;
    static final int SEARCH = 96;
    static final int CHECK_EVERY = 40;
    static final int ARRIVE_RADIUS = 3;

    private TideEbb() {
    }

    // ---------------------------------------------------------------- 道の計画 (純粋に海の形だけを見る。何も置かない)
    public record Plan(int startX, int startZ, int dx, int dz, int len, int[] floor) {
        // i 番目の中心の列
        public BlockPos center(int i) {
            return new BlockPos(startX + dx * i, floor[Math.min(i, floor.length - 1)], startZ + dz * i);
        }

        // 台 (道の先) の真ん中。人が立つ高さ (床の上)
        public BlockPos dais() {
            BlockPos c = center(len - 2);
            return c.above();
        }
    }

    // (px, pz) の近くで、岸から沖へ通せる道を探す。見つからなければ null
    @Nullable
    public static Plan plan(ServerLevel level, int px, int pz) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Plan best = null;
        for (int[] d : dirs) {
            Plan p = planAlong(level, px, pz, d[0], d[1]);
            if (p != null && (best == null || p.len > best.len)) {
                best = p;
            }
        }
        return best;
    }

    @Nullable
    static Plan planAlong(ServerLevel level, int px, int pz, int dx, int dz) {
        int sea = level.getSeaLevel();
        // 岸: px から d の向きに歩いて、最初に水の列が現れる所
        int s = 0;
        int sx = Integer.MIN_VALUE;
        int sz = 0;
        for (; s <= SEARCH; s++) {
            int x = px + dx * s;
            int z = pz + dz * s;
            if (!loaded(level, x, z)) {
                return null;
            }
            if (isWater(level, x, z)) {
                sx = x;
                sz = z;
                break;
            }
        }
        if (sx == Integer.MIN_VALUE) {
            return null;
        }
        int[] floor = new int[LEN_MAX + 1];
        int len = 0;
        int prev = Integer.MIN_VALUE;
        for (int i = 0; i < LEN_MAX; i++) {
            int x = sx + dx * i;
            int z = sz + dz * i;
            if (!(loaded(level, x, z) && isWater(level, x, z))) {
                break;
            }
            // 両側 (幅 3 の外の壁の列を含む) も水で、読み込まれていること
            boolean ok = true;
            for (int w = -2; w <= 2 && ok; w++) {
                int wx = x + (dx == 0 ? w : 0);
                int wz = z + (dz == 0 ? w : 0);
                ok = loaded(level, wx, wz) && isWater(level, wx, wz);
            }
            if (!ok) {
                break;
            }
            int f = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
            if (f > sea - 2 || f < sea - 11 || (prev != Integer.MIN_VALUE && Math.abs(f - prev) > 1)) {
                break;
            }
            floor[i] = f;
            prev = f;
            len = i + 1;
        }
        if (len < LEN_MIN) {
            return null;
        }
        floor[len] = floor[len - 1];
        int[] cut = java.util.Arrays.copyOf(floor, len + 1);
        return new Plan(sx, sz, dx, dz, len, cut);
    }

    private static boolean loaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    private static boolean isWater(ServerLevel level, int x, int z) {
        BlockPos p = new BlockPos(x, level.getSeaLevel() - 1, z);
        return level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER);
    }

    // ---------------------------------------------------------------- 置く・戻す
    private static final int F = 2 | 16;

    private static BlockState floorBlock(int x, int z) {
        int h = Math.floorMod(x * 31 + z * 17, 7);
        return switch (h) {
            case 0, 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case 2 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case 3 -> Blocks.CUT_SANDSTONE.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    private static BlockState wallBlock(int i, int y, int floorY, int x, int z) {
        int rel = y - floorY;
        if (rel >= 3 && Math.floorMod(i, 4) >= 1 && Math.floorMod(i, 4) <= 2) {
            return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        }
        if (rel == 3 && Math.floorMod(i, 8) == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        int h = Math.floorMod(x * 13 + z * 7 + y * 5, 6);
        return switch (h) {
            case 0 -> Blocks.DARK_PRISMARINE.defaultBlockState();
            case 1, 2 -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case 3 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    // 横の列 (中心から w の位置)
    private static BlockPos lateral(Plan p, int i, int w, int y) {
        return new BlockPos(p.startX + p.dx * i + (p.dx == 0 ? w : 0), y, p.startZ + p.dz * i + (p.dz == 0 ? w : 0));
    }

    // 海を引かせる: 道を置く。置いたブロックの数を返す
    static int build(ServerLevel level, Plan p) {
        int sea = level.getSeaLevel();
        int top = sea - 1;
        int placed = 0;
        for (int i = 0; i <= p.len; i++) {
            int f = p.floor[Math.min(i, p.floor.length - 1)];
            boolean cap = i == p.len;
            for (int w = -2; w <= 2; w++) {
                boolean side = Math.abs(w) == 2;
                for (int y = f; y <= top; y++) {
                    BlockPos at = lateral(p, i, w, y);
                    BlockState state;
                    if (y == f) {
                        state = floorBlock(at.getX(), at.getZ());
                    } else if (side || cap) {
                        state = wallBlock(i, y, f, at.getX(), at.getZ());
                    } else if (y == f + 1) {
                        // 膝まで水を残す
                        state = Blocks.WATER.defaultBlockState();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(at, state, F);
                    placed++;
                }
            }
        }
        // 台: 先の 3 マスは床を彫りのある石に、真ん中の脇に海のランタン
        for (int i = Math.max(0, p.len - 4); i < p.len; i++) {
            int f = p.floor[i];
            for (int w = -1; w <= 1; w++) {
                level.setBlock(lateral(p, i, w, f), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), F);
            }
        }
        level.setBlock(lateral(p, p.len - 1, -1, p.floor[p.len - 1] + 1), Blocks.SEA_LANTERN.defaultBlockState(), F);
        level.setBlock(lateral(p, p.len - 1, 1, p.floor[p.len - 1] + 1), Blocks.SEA_LANTERN.defaultBlockState(), F);
        // 岸の側: 陸を削って床へなだらかにつなぐ (一歩 1 ブロックの上り)
        int stand = p.floor[0] + 1;
        for (int k = 1; k <= 4; k++) {
            int i = -k;
            BlockPos c = lateral(p, i, 0, 0);
            if (!loaded(level, c.getX(), c.getZ())) {
                break;
            }
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ());
            int next = Math.min(h, stand + 1);
            if (next >= h) {
                break;
            }
            for (int w = -1; w <= 1; w++) {
                BlockPos col = lateral(p, i, w, 0);
                level.setBlock(new BlockPos(col.getX(), next - 1, col.getZ()), floorBlock(col.getX(), col.getZ()), F);
                for (int y = next; y < h + 2; y++) {
                    level.setBlock(new BlockPos(col.getX(), y, col.getZ()), Blocks.AIR.defaultBlockState(), F);
                }
            }
            stand = next;
        }
        return placed;
    }

    // 水を戻す: 壁と中の水を元の海へ。床と壁の根もとは残す (海の底の遺跡の道)
    static void restore(ServerLevel level, Plan p) {
        int top = level.getSeaLevel() - 1;
        for (int i = 0; i <= p.len; i++) {
            int f = p.floor[Math.min(i, p.floor.length - 1)];
            for (int w = -2; w <= 2; w++) {
                for (int y = f + 1; y <= top; y++) {
                    level.setBlock(lateral(p, i, w, y), Blocks.WATER.defaultBlockState(), F);
                }
            }
        }
        // 台の彫りのある石は床のまま残る。道の外の壁の根もと (y == f) の側の列も残る
    }

    // 道の中 (壁の内側。床の上〜海面)
    static AABB inside(ServerLevel level, Plan p) {
        int f0 = Integer.MAX_VALUE;
        for (int f : p.floor) {
            f0 = Math.min(f0, f);
        }
        int x0 = Math.min(p.startX, p.startX + p.dx * p.len) - (p.dx == 0 ? 2 : 0);
        int x1 = Math.max(p.startX, p.startX + p.dx * p.len) + (p.dx == 0 ? 3 : 1);
        int z0 = Math.min(p.startZ, p.startZ + p.dz * p.len) - (p.dz == 0 ? 2 : 0);
        int z1 = Math.max(p.startZ, p.startZ + p.dz * p.len) + (p.dz == 0 ? 3 : 1);
        return new AABB(x0, f0 + 1, z0, x1, level.getSeaLevel() + 4, z1);
    }

    // 岸 (陸の側) の立つ場所
    static BlockPos landfall(ServerLevel level, Plan p) {
        BlockPos c = lateral(p, -4, 0, 0);
        int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, c.getX(), c.getZ());
        return new BlockPos(c.getX(), Math.max(h, level.getSeaLevel()), c.getZ());
    }

    // ---------------------------------------------------------------- 保存
    // 一つの大干潮 (道の計画と時刻)。途中のものだけを持つ。終われば消える
    public static final class Run {
        public final Plan plan;
        public long startTick;
        public long endTick;
        public boolean warned;

        Run(Plan plan, long startTick, long endTick) {
            this.plan = plan;
            this.startTick = startTick;
            this.endTick = endTick;
        }
    }

    public static final class EbbData extends SavedData {
        private static final String NAME = "skyabyss_tide_ebb";
        private static final Factory<EbbData> FACTORY = new Factory<>(EbbData::new, EbbData::load);

        // 途中の大干潮 (離れた人ごとに別々に起きうる。近い人は同じものを共有する)
        public final List<Run> runs = new ArrayList<>();
        // 自分の大干潮を迎えた人 (一人に一度)
        public final java.util.Set<java.util.UUID> witnessed = new java.util.HashSet<>();

        public static EbbData get(ServerLevel overworld) {
            return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        public boolean active() {
            return !runs.isEmpty();
        }

        static EbbData load(CompoundTag tag, HolderLookup.Provider registries) {
            EbbData d = new EbbData();
            ListTag list = tag.getList("runs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                Run r = new Run(new Plan(t.getInt("sx"), t.getInt("sz"), t.getInt("dx"), t.getInt("dz"), t.getInt("len"), t.getIntArray("floor")),
                        t.getLong("startTick"), t.getLong("endTick"));
                r.warned = t.getBoolean("warned");
                d.runs.add(r);
            }
            // 古い保存 (世界に一度きりの時代): 途中のものを一つの Run として引き継ぐ
            if (d.runs.isEmpty() && tag.getInt("state") == 1 && tag.contains("floor")) {
                Run r = new Run(new Plan(tag.getInt("sx"), tag.getInt("sz"), tag.getInt("dx"), tag.getInt("dz"), tag.getInt("len"), tag.getIntArray("floor")),
                        tag.getLong("startTick"), tag.getLong("endTick"));
                r.warned = tag.getBoolean("warned");
                d.runs.add(r);
            }
            ListTag w = tag.getList("witnessed", Tag.TAG_STRING);
            for (int i = 0; i < w.size(); i++) {
                try {
                    d.witnessed.add(java.util.UUID.fromString(w.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // 壊れた項目は捨てる
                }
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Run r : runs) {
                CompoundTag t = new CompoundTag();
                t.putInt("sx", r.plan.startX);
                t.putInt("sz", r.plan.startZ);
                t.putInt("dx", r.plan.dx);
                t.putInt("dz", r.plan.dz);
                t.putInt("len", r.plan.len);
                t.putIntArray("floor", r.plan.floor);
                t.putLong("startTick", r.startTick);
                t.putLong("endTick", r.endTick);
                t.putBoolean("warned", r.warned);
                list.add(t);
            }
            tag.put("runs", list);
            ListTag w = new ListTag();
            for (java.util.UUID id : witnessed) {
                w.add(StringTag.valueOf(id.toString()));
            }
            tag.put("witnessed", w);
            return tag;
        }
    }

    // ---------------------------------------------------------------- 始める・終える
    // 始められる状態か (巨神が現れていれば、時刻を問わない)。人ごとの大干潮なので、前に起きたかどうかは見ない
    static boolean mayBegin(ServerLevel level, EbbData data) {
        return TideKeeper.KEEPER.slot(level).spawned;
    }

    // 自己テスト用: 二章の人とみなす条件を差し替える (FakePlayer を二章の人にする)
    @Nullable
    static java.util.function.Predicate<ServerPlayer> testEligible;

    // 二章 (段階 1) を遊んでいて、場面の最中でなく、オーバーワールドにいる人
    static boolean eligible(ServerPlayer p) {
        if (testEligible != null && testEligible.test(p)) {
            return p.isAlive() && p.level().dimension() == Level.OVERWORLD;
        }
        return p.isAlive() && !p.isSpectator() && p.level().dimension() == Level.OVERWORLD && !Cinematics.active(p)
                && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && Chapters.of(p) == Chapters.TIDE;
    }

    // 近くの大干潮として共有してよい距離 (道の枠から水平にこれだけ以内の人は、同じ大干潮を迎える)
    static final int SHARE_RADIUS = 128;
    // 新しい道は、途中の道の枠からこれだけ離す (重ならない・隣り合わない)
    static final int KEEP_APART = 48;

    // この人の近くで途中の大干潮があるか
    @Nullable
    static Run runNear(EbbData data, ServerPlayer p) {
        for (Run r : data.runs) {
            AABB box = inside(p.serverLevel(), r.plan).inflate(SHARE_RADIUS, 200, SHARE_RADIUS);
            if (box.contains(p.position())) {
                return r;
            }
        }
        return null;
    }

    // この人を、自分の大干潮を迎えた人にする (二章の人にだけ。クエスト ch2/ebb_road が始まる)。迎えたら true
    static boolean witness(EbbData data, ServerPlayer p) {
        if (!eligible(p) || !data.witnessed.add(p.getUUID())) {
            return false;
        }
        data.setDirty();
        Notice.narrate(p, "message.skyabyss.ch2.ebb.begin");
        // クエスト ch2/ebb_road は、この印で自動で始まる (start_when)
        jp.revati.skyabyss.npc.NpcBonds.of(p).bond(STORYTELLER).flags().add(FLAG_EBB);
        jp.revati.skyabyss.npc.NpcBonds.sync(p);
        return true;
    }

    // 計画を実行して始める。始まれば true。途中の道と近すぎる (重なる) 計画は始めない
    static boolean begin(ServerLevel level, EbbData data, Plan plan) {
        return begin(level, data, plan, Players.snapshot(level.players()));
    }

    static boolean begin(ServerLevel level, EbbData data, Plan plan, java.util.Collection<? extends ServerPlayer> players) {
        AABB mine = inside(level, plan).inflate(KEEP_APART, 200, KEEP_APART);
        for (Run r : data.runs) {
            if (mine.intersects(inside(level, r.plan))) {
                return false;
            }
        }
        long now = level.getGameTime();
        Run run = new Run(plan, now, now + MAX_TICKS);
        data.runs.add(run);
        data.setDirty();
        int n = build(level, plan);
        SkyAbyss.LOGGER.info("[tide ebb] a great ebb begins: {} blocks, {} long, from {},{} toward {},{}", n, plan.len, plan.startX, plan.startZ, plan.dx, plan.dz);
        AABB area = inside(level, plan).inflate(120, 80, 120);
        for (ServerPlayer p : players) {
            if (area.contains(p.position())) {
                p.playNotifySound(TideChapterSetup.EBB_RECEDE.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
                witness(data, p);
            }
        }
        return true;
    }

    // 水を戻して終える (途中のものすべて)。中にいる人は岸へ戻す
    static void end(ServerLevel level, EbbData data) {
        end(level, data, Players.snapshot(level.players()));
    }

    static void end(ServerLevel level, EbbData data, java.util.Collection<? extends ServerPlayer> players) {
        for (Run r : new ArrayList<>(data.runs)) {
            end(level, data, r, players);
        }
    }

    // players は、中にいる人を岸へ戻し・知らせる相手 (自己テストは FakePlayer を足して呼ぶ)
    static void end(ServerLevel level, EbbData data, Run run, java.util.Collection<? extends ServerPlayer> players) {
        Plan plan = run.plan;
        AABB box = inside(level, plan);
        BlockPos land = landfall(level, plan);
        for (ServerPlayer p : players) {
            if (box.contains(p.position())) {
                p.teleportTo(land.getX() + 0.5, land.getY(), land.getZ() + 0.5);
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 200, 0, false, false));
                Notice.narrate(p, "message.skyabyss.ch2.ebb.pushed_back");
            }
        }
        // 範囲のチャンクが読み込まれていなくても、水を戻す
        for (int i = 0; i <= plan.len; i += 8) {
            BlockPos c = lateral(plan, i, 0, 0);
            level.getChunk(c.getX() >> 4, c.getZ() >> 4);
        }
        restore(level, plan);
        AABB near = box.inflate(120, 80, 120);
        for (ServerPlayer p : players) {
            if (near.contains(p.position())) {
                p.playNotifySound(TideChapterSetup.EBB_RETURN.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
                if (eligible(p)) {
                    Notice.narrate(p, "message.skyabyss.ch2.ebb.return");
                }
            }
        }
        data.runs.remove(run);
        data.setDirty();
        SkyAbyss.LOGGER.info("[tide ebb] a great ebb ends");
    }

    // 途中の見回り: 敵を除く、頁、予告、終わり (途中のものすべて)
    static void tickActive(ServerLevel level, EbbData data) {
        tickActive(level, data, Players.snapshot(level.players()));
    }

    static void tickActive(ServerLevel level, EbbData data, java.util.Collection<? extends ServerPlayer> players) {
        for (Run r : new ArrayList<>(data.runs)) {
            tickRun(level, data, r, players);
        }
    }

    static void tickRun(ServerLevel level, EbbData data, Run run, java.util.Collection<? extends ServerPlayer> players) {
        Plan plan = run.plan;
        long now = level.getGameTime();
        if (now >= run.endTick) {
            end(level, data, run, players);
            return;
        }
        AABB box = inside(level, plan);
        if (level.getChunkSource().hasChunk(plan.startX >> 4, plan.startZ >> 4)) {
            List<Entity> intruders = new ArrayList<>();
            for (Entity e : level.getEntities((Entity) null, box.inflate(2), en -> en instanceof Enemy || en.getType().getCategory() == MobCategory.MONSTER
                    || en.getType().getCategory() == MobCategory.WATER_CREATURE || en.getType().getCategory() == MobCategory.UNDERGROUND_WATER_CREATURE)) {
                intruders.add(e);
            }
            intruders.forEach(Entity::discard);
        }
        if (!run.warned && run.endTick - now <= WARN_BEFORE) {
            run.warned = true;
            data.setDirty();
            for (ServerPlayer p : players) {
                if (eligible(p) && box.inflate(60, 40, 60).contains(p.position())) {
                    Notice.narrate(p, "message.skyabyss.ch2.ebb.warn");
                }
            }
        }
        BlockPos d = plan.dais();
        for (ServerPlayer p : players) {
            if (!p.isAlive() || p.isSpectator() || p.level() != level) {
                continue;
            }
            double dx = p.getX() - (d.getX() + 0.5);
            double dz = p.getZ() - (d.getZ() + 0.5);
            if (dx * dx + dz * dz <= ARRIVE_RADIUS * ARRIVE_RADIUS && Math.abs(p.getY() - d.getY()) <= 3) {
                arrive(p);
            }
        }
    }

    // 道の先に着いた人 (何度呼んでもよい。頁は一度だけ)
    static boolean arrive(ServerPlayer p) {
        boolean first = Lore.unlock(p, LORE_PAGE, true);
        if (first) {
            Notice.narrate(p, "message.skyabyss.ch2.ebb.page");
        }
        Quests.signal(p, SIGNAL_WALKED);
        return first;
    }

    // まだ自分の大干潮を迎えていない二章の人に、大干潮を迎えさせる (見回りごと)。
    // 近くで途中のものがあればそれを共有し、なければ新しく道を通す (途中の道と重ならない所だけ)
    static void tryTrigger(ServerLevel level, EbbData data) {
        tryTrigger(level, data, Players.snapshot(level.players()));
    }

    static void tryTrigger(ServerLevel level, EbbData data, java.util.Collection<? extends ServerPlayer> players) {
        if (!mayBegin(level, data)) {
            return;
        }
        for (ServerPlayer p : players) {
            if (!eligible(p) || data.witnessed.contains(p.getUUID())) {
                continue;
            }
            if (runNear(data, p) != null) {
                witness(data, p);
                continue;
            }
            Plan plan = plan(level, p.getBlockX(), p.getBlockZ());
            if (plan != null) {
                begin(level, data, plan, players);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % CHECK_EVERY != 7) {
            return;
        }
        EbbData data = EbbData.get(level);
        tryTrigger(level, data);
        tickActive(level, data);
    }
}
