package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import jp.revati.skyabyss.story.ch5.depths.DepthFloor.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 階を建てる (docs/design/36 の 9)。組ごとの場所 (slot) に、二つの枠 (parity = 階の偶奇) を交互に使って階を建てる。
//   次の階は、今の階に着いたとき先に頼む (反対の枠に建つ)。頼まれた階だけ建つ。1 tick に BUDGET_MS だけ進める。
//   建て方: 箱 (DepthFloorBuild.Volume) の全部を、いまの世界と見比べて、違う所だけ置く。前の階の残りは空気に置き換わる。
//   建て終えたら、前の階の敵・行商を片づけ、箱を置いて、宝箱の中身 (DepthLoot) を入れる。
//   階の中身 (間取り・箱) は、種 (世界の種・組の場所・階・潜りの通し番号) から同じものを作り直せる。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DepthBuilder {
    static final long BUDGET_MS = 20;
    private static final int TOTAL = DepthPlan.SIZE * DepthPlan.SIZE * DepthPlan.BOX_H;

    // 建てた (建てている) 一つの階。実行の間だけ持つ (ワールドを開き直したら、要るときに作り直す)
    public static final class Inst {
        public final UUID group;
        public final int slot;
        public final int floor;
        public final int salt;
        public final long seed;
        public final Plan plan;
        public final int ox;
        public final int oz;
        final DepthFloorBuild.Volume volume;
        int cursor;
        boolean cleaned;
        public boolean ready;
        // 実行の間の状態
        public boolean populated;
        public boolean houseFired;
        public boolean stairsOpen;
        public final Set<Long> trapsUsed = new HashSet<>();
        public DepthBoss.Fight boss;
        public int bossDeadAt = -1;
        public boolean shopped;

        Inst(UUID group, int slot, int floor, int salt, long worldSeed) {
            this.group = group;
            this.slot = slot;
            this.floor = floor;
            this.salt = salt;
            this.seed = DepthPlan.floorSeed(worldSeed, slot, floor, salt);
            this.plan = DepthFloor.generate(seed, floor);
            this.stairsOpen = !plan.boss();
            this.volume = DepthFloorBuild.build(plan, plan.boss());
            this.ox = DepthPlan.originX(DepthPlan.parityOf(floor));
            this.oz = DepthPlan.originZ(slot);
        }

        DepthFloorBuild.Volume volumeForTest() {
            return volume;
        }

        public int parity() {
            return DepthPlan.parityOf(floor);
        }

        public BlockPos pos(int tx, int yi, int tz) {
            return new BlockPos(ox + tx, DepthPlan.boxMinY() + yi, oz + tz);
        }

        // 床に立つ足の位置 (タイルの上)
        public BlockPos feet(int tx, int tz) {
            return new BlockPos(ox + tx, DepthPlan.FLOOR_Y + 1, oz + tz);
        }

        public BlockPos entryFeet() {
            return feet(plan.entry().x(), plan.entry().z());
        }

        public BlockPos stairsFeet() {
            return feet(plan.stairs().x(), plan.stairs().z());
        }

        public AABB box() {
            return new AABB(ox - 1, DepthPlan.boxMinY() - 1, oz - 1, ox + DepthPlan.SIZE + 1, DepthPlan.boxMaxY() + 2, oz + DepthPlan.SIZE + 1);
        }

        public boolean contains(BlockPos b) {
            return b.getX() >= ox && b.getX() < ox + DepthPlan.SIZE && b.getZ() >= oz && b.getZ() < oz + DepthPlan.SIZE
                    && b.getY() >= DepthPlan.boxMinY() && b.getY() <= DepthPlan.boxMaxY();
        }

        // 灯籠 (蝋燭) と鎖の場所
        @Nullable
        public BlockPos lantern() {
            return plan.lantern() == null ? null : pos(plan.lantern().x(), DepthFloorBuild.FLOOR_YI + 2, plan.lantern().z());
        }

        @Nullable
        public BlockPos chainTop() {
            return plan.chain() == null ? null : pos(plan.chain().x(), DepthFloorBuild.FLOOR_YI + 2, plan.chain().z());
        }
    }

    private static final Map<Long, Inst> INSTS = new HashMap<>();
    private static final ArrayDeque<Inst> QUEUE = new ArrayDeque<>();

    private DepthBuilder() {
    }

    private static long key(int slot, int parity) {
        return slot * 2L + parity;
    }

    @Nullable
    public static Inst get(int slot, int parity) {
        return INSTS.get(key(slot, parity));
    }

    public static java.util.Collection<Inst> all() {
        return INSTS.values();
    }

    // 座標の階 (箱の中なら)。なければ null
    @Nullable
    public static Inst at(BlockPos b) {
        int rx = b.getX() - DepthPlan.ORIGIN_X;
        if (rx < -2 || b.getZ() < -2) {
            return null;
        }
        int parity = Math.floorDiv(rx + 2, DepthPlan.PAR_STEP);
        int slot = Math.floorDiv(b.getZ() + 2, DepthPlan.SLOT_STEP);
        if (parity < 0 || parity > 1 || slot < 0) {
            return null;
        }
        Inst i = get(slot, parity);
        return i != null && i.contains(b) ? i : null;
    }

    // 階の箱の中か (建っていなくても。幾何だけ)
    public static boolean inRegion(BlockPos b) {
        int rx = b.getX() - DepthPlan.ORIGIN_X;
        if (rx < -2 || b.getZ() < -2 || b.getY() < DepthPlan.boxMinY() - 2 || b.getY() > DepthPlan.boxMaxY() + 2) {
            return false;
        }
        int px = Math.floorMod(rx + 2, DepthPlan.PAR_STEP) - 2;
        int pz = Math.floorMod(b.getZ() + 2, DepthPlan.SLOT_STEP) - 2;
        return Math.floorDiv(rx + 2, DepthPlan.PAR_STEP) <= 1 && px < DepthPlan.SIZE + 2 && pz < DepthPlan.SIZE + 2;
    }

    public static long seed(MinecraftServer server) {
        return server.overworld().getSeed();
    }

    // 頼む: 組 group の、潜りの通し番号 salt での階 floor。同じ階が建っている (建てている) なら何もしない
    public static Inst request(MinecraftServer server, UUID group, int slot, int floor, int salt) {
        Inst cur = get(slot, DepthPlan.parityOf(floor));
        if (cur != null && cur.floor == floor && cur.salt == salt && cur.group.equals(group)) {
            return cur;
        }
        Inst i = new Inst(group, slot, floor, salt, seed(server));
        INSTS.put(key(slot, i.parity()), i);
        QUEUE.removeIf(q -> q.slot == slot && q.parity() == i.parity());
        QUEUE.addLast(i);
        SkyAbyss.LOGGER.info("[abyss depth] queued floor {} (slot {}, salt {})", floor, slot, salt);
        return i;
    }

    public static boolean busy() {
        return !QUEUE.isEmpty();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        step(event.getServer());
    }

    static void step(MinecraftServer server) {
        Inst job = QUEUE.peekFirst();
        if (job == null) {
            return;
        }
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            QUEUE.pollFirst();
            return;
        }
        if (!job.cleaned) {
            // 前の階の敵・行商・落とし物を片づけ、中にいる人は今の階へ戻す
            for (Player p : level.getEntitiesOfClass(Player.class, job.box())) {
                if (p instanceof ServerPlayer sp) {
                    DepthRun.evacuate(sp);
                }
            }
            for (Entity e : level.getEntitiesOfClass(Entity.class, job.box(), e -> !(e instanceof Player))) {
                e.discard();
            }
            job.cleaned = true;
        }
        long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L;
        int n = DepthPlan.SIZE;
        int h = DepthPlan.BOX_H;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int minY = DepthPlan.boxMinY();
        while (job.cursor < TOTAL) {
            int c = job.cursor++;
            int x = c % n;
            int z = (c / n) % n;
            int yi = c / (n * n);
            BlockState want = DepthFloorBuild.desired(job.volume, x, yi, z);
            mp.set(job.ox + x, minY + yi, job.oz + z);
            if (level.getBlockState(mp) != want) {
                level.setBlock(mp, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if ((c & 1023) == 0 && System.nanoTime() > deadline) {
                return;
            }
        }
        QUEUE.pollFirst();
        fillChests(level, job);
        job.ready = true;
        SkyAbyss.LOGGER.info("[abyss depth] floor {} built (slot {}, {} cells, kind {})", job.floor, job.slot, h * n * n, job.plan.kind());
    }

    // 箱の中身 (王の褒美は倒したあと別に)
    static void fillChests(ServerLevel level, Inst i) {
        java.util.Random r = new java.util.Random(i.seed ^ 0xC4E57);
        for (DepthFloor.Chest c : i.plan.chests()) {
            if (c.tier() == 3) {
                continue;
            }
            BlockPos at = i.pos(c.x(), DepthFloorBuild.FLOOR_YI + 1, c.z());
            BlockEntity be = level.getBlockEntity(at);
            if (be instanceof ChestBlockEntity cb) {
                cb.clearContent();
                DepthLoot.fill(cb, i.floor, c.tier(), r);
                cb.setChanged();
            }
        }
    }

    // 頼まれた階を、その場で最後まで建てる (自己テストと開発用)
    public static void finishNow(MinecraftServer server) {
        int guard = 0;
        while (busy() && guard++ < 200_000) {
            step(server);
        }
    }

    // 停止のとき (開き直しに備えて捨てる)
    public static void reset() {
        QUEUE.clear();
        INSTS.clear();
    }
}
