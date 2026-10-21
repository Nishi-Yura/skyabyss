package jp.revati.skyabyss.story.ch2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterGrounds;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteFinder;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 沈んだ塩の蔵 (docs/design/21 の 4.1。二章の支度「塩の道」の舞台。12〜16 分の小さなダンジョン)。部品は data/skyabyss/opening/ch2_salt_vault.json (tools/gen_ch2_ground.py)。
//
//   場所   二章の跡地 (都の東の浜) の中心から 90〜150、水辺。地表は塩で白い積み石の低い屋根と坑口の碑 (sign.skyabyss.ch2_salt_vault)。階段で地下 12 へ
//   潮の喉 階段の先の井戸 → 水の底の抜け道 → 井戸。世界の潮 (TideOcean.isLowTide) が引いていれば乾いて梯子で歩け、満ちていれば水で、息を止めて潜れば抜けられる。
//          どちらでも通れる (状態で閉じない。状態は通り方を変えるだけ)。2 秒ごとに、人が近くにいる蔵だけ切り替える
//   部屋   入口の間   潮だまり貝 3 (閉じて待つ)                                                  予習: 珊瑚憑きの「像」。待ち・読み
//          塩樽の間   砂潜り 2 と塩樽 3。樽を抱える (右クリック。鈍足) → 北の乾いた棚に置く。抱えたまま傷を受けると割れて元の場所へ戻る。
//                     三つそろうと塩の戸 (白い石英の 3x3) が開く (世界に一つ。一度開けば残る)             予習: 運搬の拍 (珊瑚の庭の真珠運び)
//          奥の間     潮の番人 (名づき)。膝までの浅瀬、北の壇に前の私の刻み (sign.skyabyss.ch2_salt_wall。読むと伝承 ch2/salt_vault)。壇の裏に地上への梯子
//   クリア 湧かせた敵がすべて倒れる → 初めての人に経験値 60・宝 (塩結晶 6・真珠の護り糸 1・小真珠 4)・合図 site_clear:ch2_salt_vault。再訪は 1 日ごとに奥の間の番人が戻る
//   塩の道 蔵を建て終えたとき、跡地の中心から蔵へ向かう線に塩の道の標 5 つの場所を決め (浜側が 1、蔵の手前が 5)、チャンクが読み込まれたときに一つずつ建てる。
//          二章の人が近く (6) に来ると一行。支度「塩の道」の道の段にいれば、人ごとに一つずつ合図 ch2_salt_mark (5 で済む)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SaltVault {
    public static final String SITE = Ch2Ground.VAULT;
    public static final String TREASURE = "ch2_salt_vault";
    public static final String REPEAT_TREASURE = "ch2_salt_vault_repeat";
    public static final int XP = 60;
    public static final String SIGN = "sign.skyabyss.ch2_salt_vault";
    public static final String WALL_SIGN = "sign.skyabyss.ch2_salt_wall";
    public static final String ROOM_HALL = "hall";
    public static final String ROOM_BARRELS = "barrels";
    public static final String ROOM_DEEP = "deep";
    public static final int BARRELS = 3;
    public static final int DOOR_CELLS = 9;
    public static final int MARKS = 5;
    // 塩の道の標の、まだ建てていない場所の y (記録の印)
    public static final int PLAN_Y = -2000;
    public static final String TOKEN = "skyabyss_salt_barrel";
    static final String SEEN = "skyabyss_ch2_salt_seen";
    static final String COUNTED = "skyabyss_ch2_salt_counted";
    static final double MARK_NEAR = 6;
    static final double ACTIVE = 64;

    // 場所の基点 → 向き (SiteFinder の符号化)。再起動のあとは SiteData から引き直す
    private static final Map<Long, Integer> YAW = new HashMap<>();
    // 自己テスト・撮影の蔵 (SiteData にない。開発コマンドで置いたもの)
    static final List<Vault> EXTRA = new CopyOnWriteArrayList<>();
    // 潮の喉を潮に関わらず決める (自己テスト・撮影。基点 → 沈めるか)
    static final Map<Long, Boolean> FORCE = new HashMap<>();

    private SaltVault() {
    }

    // 一つの蔵の見え方 (基点 = SiteData の pos = 部品の地面の一つ上、と向き)。部品の印の世界の座標を引く
    public record Vault(BlockPos pos, int yaw) {
        int rot() {
            return SiteFinder.rot(yaw);
        }

        boolean mirror() {
            return SiteFinder.mirror(yaw);
        }

        public BlockPos at(int x, int y, int z) {
            return Blueprint.transform(x, y, z, rot(), mirror(), pos);
        }

        @Nullable
        public BlockPos mark(String name) {
            int[] m = Blueprint.get(SITE).marks.get(name);
            return m == null ? null : at(m[0], m[1], m[2]);
        }

        public BlockPos barrel(int k) {
            return mark("barrel" + k);
        }

        public BlockPos shelf(int k) {
            return mark("shelf" + k);
        }

        public BlockPos door(int k) {
            return mark("door" + k);
        }
    }

    // ---------------------------------------------------------------- 登録
    static void register() {
        ChapterSites.element(SITE, Chapters.TIDE, SITE).atGround().dist(90, 150).prefer(Prefer.SHORE).arriveRadius(14)
                .post(SaltVault::finish)
                .onArrive((p, s) -> {
                    Notice.narrate(p, "message.skyabyss.ch2.arrive." + SITE);
                    Tasks.later(60, () -> Notice.hint(p, flooded(p.serverLevel()) ? "message.skyabyss.ch2.vault.wet" : "message.skyabyss.ch2.vault.dry"));
                }).register();
        SiteTreasure.register(TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:salt_crystal", 6),
                new SiteTreasure.Entry("skyabyss:pearl_ward_thread", 1), new SiteTreasure.Entry("skyabyss:small_pearl", 4))));
        SiteTreasure.register(REPEAT_TREASURE, SiteTreasure.Table.of(List.of(), 1, 2, List.of(new SiteTreasure.Entry("skyabyss:salt_crystal", 2, 3, 3),
                new SiteTreasure.Entry("skyabyss:small_pearl", 1, 2, 2), new SiteTreasure.Entry("skyabyss:shell_chip", 1, 1, 1))));
        SiteSpawner.register(new SiteSpawner.Def(SITE, Chapters.TIDE, pads(), XP, TREASURE, REPEAT_TREASURE, SiteSpawner.MAX_MOBS, SaltVault::origins, SaltVault::yawAt));
    }

    // 湧き台 (部品の座標。床は y = -12、敵が立つのは -11)。最後の部屋 (奥の間) がクリア後の再訪で戻る
    static List<SiteSpawner.Pad> pads() {
        String clam = Ch2Ground.pick(Ch2Ground.CLAM, "skyabyss:reef_scuttler");
        String burrower = Ch2Ground.pick(Ch2Ground.BURROWER, "skyabyss:reef_scuttler");
        String warden = Ch2Ground.pick(Ch2Ground.WARDEN, "skyabyss:coral_thrall");
        List<SiteSpawner.Pad> pads = new ArrayList<>();
        pads.add(new SiteSpawner.Pad(ROOM_HALL, -3, -11, 25, List.of(new SiteSpawner.Spawn(clam, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_HALL, 3, -11, 28, List.of(new SiteSpawner.Spawn(clam, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_HALL, 0, -11, 32, List.of(new SiteSpawner.Spawn(clam, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_BARRELS, 15, -11, 27, List.of(new SiteSpawner.Spawn(burrower, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_BARRELS, 21, -11, 31, List.of(new SiteSpawner.Spawn(burrower, 1))));
        pads.add(new SiteSpawner.Pad(ROOM_DEEP, 37, -11, 27, List.of(new SiteSpawner.Spawn(warden, 1))));
        return pads;
    }

    // ---------------------------------------------------------------- 場所と向き
    static List<BlockPos> origins(MinecraftServer server) {
        List<BlockPos> list = new ArrayList<>();
        for (Vault v : vaults(server)) {
            list.add(v.pos());
        }
        return list;
    }

    // 場所の基点の向き (蔵・潮だまり・筏の跡で共通に使う)
    public static int yawAt(BlockPos origin) {
        Integer y = YAW.get(origin.asLong());
        return y != null ? y : 0;
    }

    static void rememberYaw(SiteJob job) {
        YAW.put(job.entry.pos.asLong(), job.entry.yaw);
    }

    // 建て終わった蔵 (と、開発コマンドで置いた蔵)
    public static List<Vault> vaults(MinecraftServer server) {
        List<Vault> list = new ArrayList<>();
        for (SiteData.Entry e : entries(server)) {
            list.add(new Vault(e.pos, e.yaw));
        }
        list.addAll(EXTRA);
        return list;
    }

    public static List<SiteData.Entry> entries(MinecraftServer server) {
        List<SiteData.Entry> list = new ArrayList<>();
        for (SiteData.ChapterPlan plan : SiteData.get(server.overworld()).plans()) {
            for (SiteData.Entry e : plan.sites) {
                if (e.built && (e.siteId.equals(SITE) || e.siteId.equals(Ch2Ground.POOLS) || e.siteId.equals(Ch2Ground.RAFT))) {
                    YAW.put(e.pos.asLong(), e.yaw);
                }
                if (e.siteId.equals(SITE) && e.built) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- 建て終わり
    static void finish(SiteJob job) {
        rememberYaw(job);
        Vault v = new Vault(job.entry.pos, job.entry.yaw);
        decorate(job.level, v);
        // 塩の道の標の場所 (跡地の中心から蔵へ向かう線)。建てるのはチャンクが読み込まれてから (tick)
        ChapterGrounds.center(job.level.getServer(), Chapters.TIDE).ifPresent(c -> planRoad(job.entry, c, v.mark("stand")));
    }

    // 碑の文を刻み、出口の縦穴の口を地表まで延ばす (開発コマンドの蔵にも)
    public static void decorate(ServerLevel level, Vault v) {
        BlockPos tablet = v.mark("tablet");
        if (tablet != null) {
            Tablets.write(level, tablet, SIGN);
        }
        BlockPos wall = v.mark("wall_tablet");
        if (wall != null) {
            Tablets.write(level, wall, WALL_SIGN);
        }
        BlockPos exit = v.mark("exit");
        if (exit != null) {
            BlockPos top = exit.below();
            BlockState trap = level.getBlockState(top);
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,
                    trap.hasProperty(TrapDoorBlock.FACING) ? trap.getValue(TrapDoorBlock.FACING) : Direction.SOUTH);
            BlockPos cur = exit;
            for (int i = 0; i < 16; i++) {
                BlockState s = level.getBlockState(cur);
                if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(top, ladder, 2 | 16);
                level.setBlock(cur, trap.hasProperty(TrapDoorBlock.HALF) ? trap.setValue(TrapDoorBlock.HALF, Half.TOP) : trap, 2 | 16);
                top = cur;
                cur = cur.above();
            }
        }
    }

    static void planRoad(SiteData.Entry entry, BlockPos center, @Nullable BlockPos vaultStand) {
        BlockPos to = vaultStand != null ? vaultStand : entry.pos;
        double dx = center.getX() - to.getX();
        double dz = center.getZ() - to.getZ();
        double d = Math.max(24, Math.hypot(dx, dz));
        double ux = dx / d;
        double uz = dz / d;
        for (int n = 1; n <= MARKS; n++) {
            double along = 12 + (d - 20) * (MARKS - n) / (double) (MARKS - 1);
            entry.extra("road" + n, new BlockPos((int) Math.round(to.getX() + ux * along), PLAN_Y, (int) Math.round(to.getZ() + uz * along)));
        }
    }

    // 塩の道の標 n の場所 (建てていれば地面の上の柱の根もと。まだなら y = PLAN_Y)
    @Nullable
    public static BlockPos roadMark(SiteData.Entry vault, int n) {
        return vault.extra("road" + n);
    }

    // 標を一つ建てる。toward = 矢印の向きの先。柱の根もと (印 sign) を返す
    public static BlockPos placeMark(ServerLevel level, int x, int z, BlockPos toward) {
        BlockPos ground = dryGround(level, x, z);
        Blueprint bp = Blueprint.get("ch2_salt_mark");
        int rot = rotToward(toward.getX() - ground.getX(), toward.getZ() - ground.getZ());
        for (Blueprint.Cell c : bp.cells) {
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, false, ground);
            level.setBlock(at, Blueprint.transform(c.state(), rot, false), 2 | 16);
            if (c.y() == 0) {
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos below = at.below(dy);
                    BlockState s = level.getBlockState(below);
                    if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) {
                        level.setBlock(below, Blocks.SAND.defaultBlockState(), 2 | 16);
                    } else {
                        break;
                    }
                }
            }
        }
        // 柱の上の空き (草・花) を片づける
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = ground.above(dy + 5);
            if (level.getBlockState(up).canBeReplaced() && !level.getBlockState(up).isAir() && level.getBlockState(up).getFluidState().isEmpty()) {
                level.setBlock(up, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        int[] m = bp.marks.get("sign");
        return Blueprint.transform(m[0], m[1] + 1, m[2], rot, false, ground);
    }

    // (x, z) のそばの、水でない地面の上面 (半径 5 まで探す。なければ海の底)
    static BlockPos dryGround(ServerLevel level, int x, int z) {
        for (int r = 0; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
                    BlockPos top = new BlockPos(x + dx, y, z + dz);
                    BlockState s = level.getBlockState(top);
                    if (s.getFluidState().isEmpty() && !s.isAir() && !s.is(net.minecraft.tags.BlockTags.LOGS)) {
                        return top;
                    }
                }
            }
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
    }

    // 部品の北 (-z) を (dx, dz) へ向ける回し方
    static int rotToward(double dx, double dz) {
        int best = 0;
        double bestDot = -2;
        double len = Math.max(1e-6, Math.hypot(dx, dz));
        for (int r = 0; r < 4; r++) {
            BlockPos f = Blueprint.transform(0, 0, -1, r, false, BlockPos.ZERO);
            double dot = (f.getX() * dx + f.getZ() * dz) / len;
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 潮の喉
    // 世界の潮で喉を沈めるか (満ちていれば true)
    public static boolean flooded(ServerLevel level) {
        return !TideOcean.isLowTide(level.getDayTime());
    }

    static boolean wantFlooded(ServerLevel level, Vault v) {
        Boolean f = FORCE.get(v.pos().asLong());
        return f != null ? f : flooded(level);
    }

    // 喉の中身のセル (部品で空気か梯子の所)。部品の座標
    private static List<int[]> sumpCells;

    static synchronized List<int[]> sumpCells() {
        if (sumpCells == null) {
            Blueprint bp = Blueprint.get(SITE);
            int[] lo = bp.marks.get("sump_lo");
            int[] hi = bp.marks.get("sump_hi");
            Map<Long, BlockState> cells = new HashMap<>();
            for (Blueprint.Cell c : bp.cells) {
                cells.put(BlockPos.asLong(c.x(), c.y(), c.z()), c.state());
            }
            List<int[]> list = new ArrayList<>();
            for (int x = lo[0]; x <= hi[0]; x++) {
                for (int y = lo[1]; y <= hi[1]; y++) {
                    for (int z = lo[2]; z <= hi[2]; z++) {
                        BlockState s = cells.get(BlockPos.asLong(x, y, z));
                        if (s != null && (s.isAir() || s.is(Blocks.LADDER))) {
                            list.add(new int[] {x, y, z});
                        }
                    }
                }
            }
            sumpCells = list;
        }
        return sumpCells;
    }

    // 喉が今水か (喉の空気のセルの一つ目で見る)
    public static boolean sumpWet(ServerLevel level, Vault v) {
        for (int[] c : sumpCells()) {
            BlockState s = level.getBlockState(v.at(c[0], c[1], c[2]));
            if (!s.is(Blocks.LADDER)) {
                return !s.getFluidState().isEmpty();
            }
        }
        return false;
    }

    // 喉を沈める / 乾かす。変えたら true
    public static boolean setSump(ServerLevel level, Vault v, boolean wet) {
        if (sumpWet(level, v) == wet) {
            return false;
        }
        for (int[] c : sumpCells()) {
            BlockPos at = v.at(c[0], c[1], c[2]);
            BlockState s = level.getBlockState(at);
            if (s.is(Blocks.LADDER)) {
                level.setBlock(at, s.setValue(BlockStateProperties.WATERLOGGED, wet), 2 | 16);
            } else if (wet && s.isAir()) {
                level.setBlock(at, Blocks.WATER.defaultBlockState(), 2 | 16);
            } else if (!wet && s.is(Blocks.WATER)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 塩樽と塩の戸
    public static ItemStack token() {
        ItemStack stack = new ItemStack(Items.BARREL);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TOKEN, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.skyabyss.ch2_salt_barrel"));
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    public static boolean isToken(ItemStack stack) {
        if (!stack.is(Items.BARREL)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TOKEN);
    }

    public static int carried(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (isToken(s)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (isToken(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    static void dropTokens(Player p) {
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (int i = 0; i < list.size(); i++) {
                if (isToken(list.get(i))) {
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    public static boolean doorOpen(ServerLevel level, Vault v) {
        for (int k = 0; k < DOOR_CELLS; k++) {
            BlockPos at = v.door(k);
            if (at != null && level.isLoaded(at) && level.getBlockState(at).is(Blocks.SMOOTH_QUARTZ)) {
                return false;
            }
        }
        return true;
    }

    public static int onShelf(ServerLevel level, Vault v) {
        int n = 0;
        for (int k = 0; k < BARRELS; k++) {
            if (level.getBlockState(v.shelf(k)).is(Blocks.BARREL)) {
                n++;
            }
        }
        return n;
    }

    public static int atHome(ServerLevel level, Vault v) {
        int n = 0;
        for (int k = 0; k < BARRELS; k++) {
            if (level.getBlockState(v.barrel(k)).is(Blocks.BARREL)) {
                n++;
            }
        }
        return n;
    }

    // 樽を抱える (自己テストからも)。抱えたら true
    public static boolean lift(ServerPlayer p, ServerLevel level, Vault v, int k) {
        BlockPos at = v.barrel(k);
        if (carried(p) > 0 || !level.getBlockState(at).is(Blocks.BARREL) || doorOpen(level, v)) {
            return false;
        }
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, at, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.8f, 0.7f);
        ItemStack stack = token();
        if (!p.getInventory().add(stack)) {
            level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
            return false;
        }
        Notice.hint(p, "message.skyabyss.ch2.vault.lift");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        return true;
    }

    // 抱えた樽を棚に置く (空いた棚のうち near にいちばん近い所)。置いたら true。三つそろえば戸を開ける
    public static boolean deliver(ServerPlayer p, ServerLevel level, Vault v, BlockPos near) {
        if (carried(p) == 0) {
            return false;
        }
        int best = -1;
        double bestD = 3.2 * 3.2;
        for (int k = 0; k < BARRELS; k++) {
            BlockPos s = v.shelf(k);
            if (!level.getBlockState(s).isAir()) {
                continue;
            }
            double d = Math.min(s.distSqr(near), s.below().distSqr(near));
            if (d <= bestD) {
                bestD = d;
                best = k;
            }
        }
        if (best < 0) {
            return false;
        }
        BlockPos s = v.shelf(best);
        removeOne(p);
        level.setBlock(s, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
        level.playSound(null, s, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.9f, 1.0f);
        Notice.hint(p, "message.skyabyss.ch2.vault.shelf");
        if (onShelf(level, v) >= BARRELS) {
            openDoor(level, v);
        }
        return true;
    }

    static void removeOne(Player p) {
        for (List<ItemStack> list : List.of(p.getInventory().items, p.getInventory().offhand)) {
            for (int i = 0; i < list.size(); i++) {
                if (isToken(list.get(i))) {
                    list.get(i).shrink(1);
                    return;
                }
            }
        }
    }

    // 抱えたまま傷を受けた: 樽は割れる (元の場所には次の見回りで戻る)
    public static void breakBarrel(ServerPlayer p) {
        if (carried(p) == 0) {
            return;
        }
        dropTokens(p);
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.4, 0.5, 0.02);
        Notice.hint(p, "message.skyabyss.ch2.vault.broke");
    }

    public static void openDoor(ServerLevel level, Vault v) {
        BlockPos centre = v.door(4);
        for (int k = 0; k < DOOR_CELLS; k++) {
            BlockPos at = v.door(k);
            if (level.getBlockState(at).is(Blocks.SMOOTH_QUARTZ)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.playSound(null, centre, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
        level.playSound(null, centre, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.WHITE_ASH, centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 60, 0.4, 1.0, 1.0, 0.02);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < 32 * 32) {
                Notice.narrate(p, "message.skyabyss.ch2.vault.door");
            }
        }
    }

    // 見回り: 割れた・落とした樽を元の場所へ。戸が開いたあとは、残った樽を手から消す
    static void tidyBarrels(ServerLevel level, Vault v, List<ServerPlayer> players) {
        BlockPos room = v.mark("room2");
        if (doorOpen(level, v)) {
            for (ServerPlayer p : players) {
                if (carried(p) > 0 && room != null && p.distanceToSqr(room.getX(), room.getY(), room.getZ()) < 96 * 96) {
                    dropTokens(p);
                }
            }
            return;
        }
        int held = 0;
        for (ServerPlayer p : players) {
            if (room != null && p.distanceToSqr(room.getX(), room.getY(), room.getZ()) < 128 * 128) {
                held += carried(p);
            }
        }
        int missing = BARRELS - atHome(level, v) - onShelf(level, v) - held;
        for (int k = 0; k < BARRELS && missing > 0; k++) {
            BlockPos at = v.barrel(k);
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 3);
                missing--;
            }
        }
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        if (t % 20 != 7) {
            return;
        }
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = Players.snapshot(level.players());
        for (ServerPlayer p : players) {
            if (carried(p) > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false, true));
            }
        }
        if (t % 40 == 7) {
            for (Vault v : vaults(server)) {
                if (!level.isLoaded(v.pos()) || !anyNear(players, v.pos(), ACTIVE)) {
                    continue;
                }
                boolean wet = wantFlooded(level, v);
                if (setSump(level, v, wet)) {
                    BlockPos mouth = v.mark("mouth");
                    for (ServerPlayer p : players) {
                        if (mouth != null && p.distanceToSqr(mouth.getX(), mouth.getY(), mouth.getZ()) < 40 * 40) {
                            Notice.hint(p, wet ? "message.skyabyss.ch2.vault.wet" : "message.skyabyss.ch2.vault.dry");
                        }
                    }
                }
                tidyBarrels(level, v, players);
            }
        }
        roadTick(server, level, players);
    }

    static boolean anyNear(List<ServerPlayer> players, BlockPos at, double r) {
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()) <= r) {
                return true;
            }
        }
        return false;
    }

    // 塩の道の標: 読み込まれたら建て、二章の人が近くに来たら一行と合図
    static void roadTick(MinecraftServer server, ServerLevel level, List<ServerPlayer> players) {
        List<SiteData.Entry> list = entries(server);
        boolean dirty = false;
        for (SiteData.Entry e : list) {
            for (int n = 1; n <= MARKS; n++) {
                BlockPos at = roadMark(e, n);
                if (at == null || at.getY() != PLAN_Y || !level.hasChunk(at.getX() >> 4, at.getZ() >> 4)
                        || !level.hasChunk((at.getX() + 3) >> 4, (at.getZ() + 3) >> 4) || !level.hasChunk((at.getX() - 3) >> 4, (at.getZ() - 3) >> 4)) {
                    continue;
                }
                BlockPos next = n < MARKS ? roadMark(e, n + 1) : new Vault(e.pos, e.yaw).mark("mouth");
                BlockPos placed = placeMark(level, at.getX(), at.getZ(), next != null ? next : e.pos);
                e.extra("road" + n, placed);
                dirty = true;
            }
        }
        if (dirty) {
            SiteData.get(level).setDirty();
        }
        for (ServerPlayer p : players) {
            if (p.isSpectator() || !Ch2Ground.inChapter(p)) {
                continue;
            }
            boolean road = atRoadStage(p);
            if (road && Lore.state(p).has(Ch2Ground.LORE_VAULT)) {
                // 蔵の刻みを先に読んだ人: 道の段を済ませる
                for (int n = 1; n <= MARKS; n++) {
                    count(p, n);
                }
                continue;
            }
            for (SiteData.Entry e : list) {
                for (int n = 1; n <= MARKS; n++) {
                    BlockPos at = roadMark(e, n);
                    if (at != null && at.getY() != PLAN_Y && Math.hypot(p.getX() - at.getX() - 0.5, p.getZ() - at.getZ() - 0.5) <= MARK_NEAR
                            && Math.abs(p.getY() - at.getY()) < 10) {
                        seeMark(p, n, road);
                    }
                }
            }
        }
    }

    // 標 n を見た (自己テストからも)。初めてなら一行。道の段にいれば合図 (人ごとに一つずつ)
    public static void seeMark(ServerPlayer p, int n, boolean road) {
        CompoundTag d = p.getPersistentData();
        int seen = d.getInt(SEEN);
        if ((seen & (1 << n)) == 0) {
            d.putInt(SEEN, seen | (1 << n));
            Notice.narrate(p, "message.skyabyss.ch2.salt_mark." + n);
        }
        if (road) {
            count(p, n);
        }
    }

    static void count(ServerPlayer p, int n) {
        CompoundTag d = p.getPersistentData();
        int counted = d.getInt(COUNTED);
        if ((counted & (1 << n)) == 0) {
            d.putInt(COUNTED, counted | (1 << n));
            Quests.signal(p, Ch2Ground.SIGNAL_MARK);
        }
    }

    public static boolean atRoadStage(ServerPlayer p) {
        return atStage(p, Ch2Ground.Q_SALT, "road");
    }

    public static boolean atStage(ServerPlayer p, String quest, String stage) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        return e != null && !e.done && def != null && e.stage < def.stages().size() && def.stages().get(e.stage).id().equals(stage);
    }

    // ---------------------------------------------------------------- 触れる
    @Nullable
    static Vault vaultNear(MinecraftServer server, BlockPos pos, double r) {
        for (Vault v : vaults(server)) {
            BlockPos room = v.mark("room2");
            if (room != null && room.distSqr(pos) <= r * r) {
                return v;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean carrying = carried(player) > 0;
        if (!carrying && !state.is(Blocks.BARREL) && !state.is(Blocks.SMOOTH_QUARTZ)) {
            return;
        }
        Vault v = vaultNear(level.getServer(), pos, 40);
        if (v == null) {
            if (carrying && isToken(player.getMainHandItem())) {
                // 塩樽は蔵の外では置けない (割れる)
                breakBarrel(player);
                event.setCanceled(true);
            }
            return;
        }
        if (state.is(Blocks.BARREL)) {
            for (int k = 0; k < BARRELS; k++) {
                if (pos.equals(v.barrel(k))) {
                    event.setCanceled(true);
                    lift(player, level, v, k);
                    return;
                }
                if (pos.equals(v.shelf(k))) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
        if (state.is(Blocks.SMOOTH_QUARTZ)) {
            for (int k = 0; k < DOOR_CELLS; k++) {
                if (pos.equals(v.door(k))) {
                    Notice.hint(player, "message.skyabyss.ch2.vault.door_shut");
                    event.setCanceled(true);
                    return;
                }
            }
        }
        if (carrying) {
            event.setCanceled(true);
            if (!deliver(player, level, v, pos.above())) {
                Notice.hint(player, "message.skyabyss.ch2.vault.wet_floor");
            }
        }
    }

    @SubscribeEvent
    static void onHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAmount() > 0 && carried(p) > 0) {
            breakBarrel(p);
        }
    }

    @SubscribeEvent
    static void onToss(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide && isToken(event.getEntity().getItem())) {
            event.getEntity().discard();
            if (event.getPlayer() instanceof ServerPlayer p) {
                Notice.hint(p, "message.skyabyss.ch2.vault.broke");
            }
        }
    }

    static String describe(ServerLevel level, Vault v) {
        return "vault at " + v.pos() + " sump " + (sumpWet(level, v) ? "wet" : "dry") + ", barrels home " + atHome(level, v) + " shelf " + onShelf(level, v)
                + ", door " + (doorOpen(level, v) ? "open" : "shut");
    }

    static void log(String msg) {
        SkyAbyss.LOGGER.info("[ch2 vault] {}", msg);
    }
}
