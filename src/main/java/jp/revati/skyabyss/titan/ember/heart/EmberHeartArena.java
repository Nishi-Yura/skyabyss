package jp.revati.skyabyss.titan.ember.heart;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 熾の底 (心臓の間、13 の 3.7節・5.3節)。部屋の箱は EmberBackLayout.HEART (中心 (0, 50, -4)、半径 16、床のブロックは y 50、天井 y 64)。
// 床の輪 (中心からの距離): 台 0〜4 / 区画 4〜15 (六つの扇) / 溝 15〜16 (溶岩)。区画の下には深さ 3 の溶岩の溜め (y 47〜49)。
//  - 台は呑まれた人の戻る場所で、沈まない。区画は段階 2 で順に 2.0 秒の予告 (割れて光る) のあと 8 秒溶岩に沈み、また冷え殻に戻る
//  - 沈んだ区画か溝か溜めに落ちた人は「呑まれた」(傷 12・熱 +40) で台へ戻される (溶岩の傷にしない。持ち物が燃えない)
//  - 壁の血管 (溶岩の筋と光る苔) と天井の灯は、段階 3 で消える (setCold)。そのあいだ見えなくなりすぎないよう、見えない光 (LIGHT) を数か所に置く
//  - 北の壁に入口 (幅 3、高さ 4、前室から。戦いの間は封印)。溝をまたぐ橋がかかっている
// 箱の中だけにブロックを置く。入口 (HEART_DOOR) と予約の場所 (reserved("heart")) は B が休み場などを置く所と重なるので、床の高さだけそろえる
public final class EmberHeartArena {
    public static final int CX = EmberBackLayout.HEART_CENTER.getX();
    public static final int CZ = EmberBackLayout.HEART_CENTER.getZ();
    public static final int FLOOR = EmberBackLayout.FLOOR_HEART;
    // 人が立つ高さ (床のブロックの上面)
    public static final int SURFACE = FLOOR + 1;
    public static final int RADIUS = EmberBackLayout.HEART_RADIUS;
    public static final int PLATFORM_R = 4;
    public static final int SECTION_R = 15;
    public static final int BOTTOM = EmberBackLayout.HEART.minY();
    public static final int CEILING = EmberBackLayout.HEART.maxY();
    public static final int SECTIONS = 6;
    // 予告と沈みの長さ (tick)。13 の 5.3節: 予告 2.0 秒、沈む 8 秒
    public static final int CRACK_TICKS = 40;
    public static final int SINK_TICKS = 160;
    // 同時に沈む区画の上限
    public static final int MAX_SINKING = 2;
    // 呑まれた: 傷 18 (1 秒に一度だけ、台の上へ戻る)、熱 +40
    public static final float ENGULF_DAMAGE = 18f;
    public static final float ENGULF_HEAT = 40f;
    // 見えない光 (段階 3 の暗さのなかで、色の粒の予告が沈まないように)
    private static final int AMBIENT_LIGHT = 6;
    private static final int EMBER_LIGHT = 11;

    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // 区画の状態: 0 ふつう / 1 割れている (予告) / 2 沈んでいる
    private static final int[] state = new int[SECTIONS];
    private static final int[] timer = new int[SECTIONS];
    private static boolean cold;
    private static final Map<UUID, Long> engulfedAt = new HashMap<>();

    // 区画の床のブロックの位置 (一度だけ数える)
    private static List<List<BlockPos>> sectionBlocks;
    private static List<BlockPos> veinCells;
    private static List<BlockPos> lampCells;
    private static List<BlockPos> moatCells;
    private static List<BlockPos> ambientCells;

    private EmberHeartArena() {
    }

    // ---------------------------------------------------------------- 配置
    // 心臓の居場所 (台の真ん中の上。判定の箱の足もと)
    public static Vec3 heartHome() {
        return new Vec3(CX + 0.5, SURFACE + 1.5, CZ + 0.5);
    }

    // 戦いの部屋 (溶岩の溜めの高さから天井まで)。技と狙いもこの中に限る
    public static AABB arena() {
        return new AABB(CX - RADIUS, BOTTOM, CZ - RADIUS, CX + RADIUS + 1, CEILING, CZ + RADIUS + 1);
    }

    // 前室と入口の通り道 (この中の人は、集合の終わりに部屋へ入れる)
    public static AABB ante() {
        return new AABB(EmberBackLayout.HEART_ANTE.minX(), FLOOR, EmberBackLayout.HEART_ANTE.minZ(),
                EmberBackLayout.HEART_ANTE.maxX() + 1, FLOOR + 7, CZ - RADIUS);
    }

    // 入口 (封印を置く 3 × 4 の 1 枚の壁)
    public static List<BlockPos> gate() {
        List<BlockPos> list = new ArrayList<>();
        EmberBackLayout.Box b = EmberBackLayout.HEART_DOOR;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        return list;
    }

    // 前室に着いたときの位置 (休み場 heart の南)
    public static Vec3 anteSpawn() {
        return new Vec3(0.5, SURFACE, EmberBackLayout.HEART_ANTE.minZ() + 3.5);
    }

    // 部屋に入った人の置き場 (入口の橋の奥)
    public static Vec3 insideSpawn(double x) {
        return new Vec3(Math.max(CX - 0.5, Math.min(CX + 1.5, x)), SURFACE, CZ - 14.5);
    }

    static double dist(int x, int z) {
        return Math.sqrt((double) (x - CX) * (x - CX) + (double) (z - CZ) * (z - CZ));
    }

    // 扇の番号 (0〜5)。台・溝・外は -1。扇 k は中心の向きが 90° + 60°k (真南が 0、入口の北が 3)
    public static int sectorOf(int x, int z) {
        double d = dist(x, z);
        if (d < PLATFORM_R || d >= SECTION_R) {
            return -1;
        }
        double a = Math.toDegrees(Math.atan2(z - CZ, x - CX));
        double rel = ((a - 60) % 360 + 360) % 360;
        return Math.min(SECTIONS - 1, (int) (rel / 60));
    }

    public static int sectorAt(double x, double z) {
        return sectorOf((int) Math.floor(x), (int) Math.floor(z));
    }

    // 扇の中心の向き (度、atan2(dz, dx))
    public static double sectorAngle(int k) {
        return 90 + 60 * k;
    }

    // 扇の中ほどの立てる場所 (距離 9.5)
    public static Vec3 sectorCenter(int k) {
        double a = Math.toRadians(sectorAngle(k));
        return new Vec3(CX + 0.5 + Math.cos(a) * 9.5, SURFACE, CZ + 0.5 + Math.sin(a) * 9.5);
    }

    public static boolean onPlatform(double x, double z) {
        return dist((int) Math.floor(x), (int) Math.floor(z)) < PLATFORM_R;
    }

    private static List<List<BlockPos>> sectionBlocks() {
        if (sectionBlocks == null) {
            List<List<BlockPos>> lists = new ArrayList<>();
            for (int k = 0; k < SECTIONS; k++) {
                lists.add(new ArrayList<>());
            }
            for (int x = CX - SECTION_R; x <= CX + SECTION_R; x++) {
                for (int z = CZ - SECTION_R; z <= CZ + SECTION_R; z++) {
                    int k = sectorOf(x, z);
                    if (k >= 0) {
                        lists.get(k).add(new BlockPos(x, FLOOR, z));
                    }
                }
            }
            sectionBlocks = lists;
        }
        return sectionBlocks;
    }

    // ---------------------------------------------------------------- 建てる
    // 床のブロック (区画: 黒い殻。扇の継ぎ目は玄武岩の筋)
    static BlockState floorState(int x, int z) {
        double d = dist(x, z);
        int dx = x - CX;
        int dz = z - CZ;
        if (d < PLATFORM_R) {
            return d < 1.5 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : (Math.floorMod(dx * 3 + dz * 5, 7) == 0
                    ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        double a = Math.toDegrees(Math.atan2(dz, dx));
        double toSeam = Math.abs(((a - 60) % 60 + 60) % 60);
        toSeam = Math.min(toSeam, 60 - toSeam);
        if (toSeam * Math.PI / 180 * d < 0.55) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        int h = Math.floorMod(dx * 31 + dz * 17 + dx * dz, 11);
        if (h == 0) {
            return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        }
        return h < 4 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private static BlockState wallState(int y, int x, int z) {
        if ((y - FLOOR) % 5 == 4) {
            return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        }
        return Math.floorMod(x * 7 + z * 13 + y * 3, 5) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    // 入口の通り道 (橋の上の空き): 北の壁の 3 幅
    private static boolean passage(int dx, int dz) {
        return Math.abs(dx) <= 1 && dz <= -RADIUS;
    }

    // 壁の血管の柱か (八方、内側の 1 層)
    private static boolean veinColumn(int dx, int dz) {
        double d = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (d < RADIUS || d >= RADIUS + 1.2) {
            return false;
        }
        double a = Math.toDegrees(Math.atan2(dz, dx));
        double diff = Math.abs(a - Math.round(a / 45.0) * 45.0);
        return diff * Math.PI / 180 * d < 0.6 && !passage(dx, dz);
    }

    private static void computeLists() {
        if (veinCells != null) {
            return;
        }
        List<BlockPos> veins = new ArrayList<>();
        List<BlockPos> lamps = new ArrayList<>();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                if (veinColumn(dx, dz)) {
                    for (int y = SURFACE + 1; y < CEILING; y++) {
                        BlockPos p = new BlockPos(CX + dx, y, CZ + dz);
                        if (y % 4 == 0) {
                            lamps.add(p);
                        } else {
                            veins.add(p);
                        }
                    }
                }
            }
        }
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 5 : 11;
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(60 * k + 30 * ring + 15);
                lamps.add(new BlockPos(CX + (int) Math.round(Math.cos(a) * r), CEILING, CZ + (int) Math.round(Math.sin(a) * r)));
            }
        }
        List<BlockPos> moat = new ArrayList<>();
        for (int x = CX - RADIUS; x <= CX + RADIUS; x++) {
            for (int z = CZ - RADIUS; z <= CZ + RADIUS; z++) {
                double d = dist(x, z);
                if (d >= SECTION_R && d < RADIUS && !bridge(x - CX, z - CZ)) {
                    moat.add(new BlockPos(x, FLOOR, z));
                }
            }
        }
        List<BlockPos> ambient = new ArrayList<>();
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 6 : 11;
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(60 * k + 30 * ring);
                ambient.add(new BlockPos(CX + (int) Math.round(Math.cos(a) * r), SURFACE + 3, CZ + (int) Math.round(Math.sin(a) * r)));
            }
        }
        veinCells = veins;
        lampCells = lamps;
        moatCells = moat;
        ambientCells = ambient;
    }

    // 入口の橋 (溝の上、北。幅 3)
    private static boolean bridge(int dx, int dz) {
        return Math.abs(dx) <= 1 && dz <= -SECTION_R;
    }

    // 建っているか (台の中心・天井・溝・血管で見る。段階 3 の暗さは見ない)
    public static boolean isBuilt(ServerLevel level) {
        computeLists();
        return level.getBlockState(new BlockPos(CX, FLOOR, CZ)).is(Blocks.CHISELED_POLISHED_BLACKSTONE)
                && level.getBlockState(new BlockPos(CX, CEILING - 2, CZ)).isAir()
                && level.getBlockState(new BlockPos(CX, CEILING, CZ)).getBlock() != Blocks.AIR
                && level.getBlockState(new BlockPos(CX, FLOOR - 1, CZ + 8)).is(Blocks.LAVA)
                && level.getBlockState(new BlockPos(CX + SECTION_R, FLOOR, CZ)).getBlock() != Blocks.AIR
                && !level.getBlockState(new BlockPos(CX + 7, FLOOR, CZ)).is(Blocks.LAVA)
                && level.getBlockState(new BlockPos(CX, FLOOR + 1, CZ - SECTION_R)).isAir();
    }

    public static void ensureBuilt(ServerLevel level) {
        if (!isBuilt(level)) {
            build(level);
        }
    }

    // B の体の builder から呼ばれる形 (EmberBackRooms.register)。箱の中は空気で、いちばん下の段に仮の床がある
    public static void buildRoom(ServerLevel level, EmberBackLayout.Box box) {
        build(level);
        // 作り直すと部屋の中の歩く道の灯りも消えるので置き直す (置き換えてよい石の所だけ)
        jp.revati.skyabyss.titan.light.RouteLamps.place(level, "ember");
    }

    public static void build(ServerLevel level) {
        computeLists();
        int flags = 2 | 16;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState base = Blocks.BLACKSTONE.defaultBlockState();
        BlockState platformStone = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState bridgeStone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState ceiling = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int y = CEILING; y >= BOTTOM; y--) {
            for (int x = CX - RADIUS - 1; x <= CX + RADIUS + 1; x++) {
                for (int z = CZ - RADIUS - 1; z <= CZ + RADIUS + 1; z++) {
                    int dx = x - CX;
                    int dz = z - CZ;
                    double d = dist(x, z);
                    p.set(x, y, z);
                    BlockState s;
                    if (y == BOTTOM) {
                        s = base;
                    } else if (y == CEILING) {
                        s = ceiling;
                    } else if (d >= RADIUS) {
                        if (passage(dx, dz) && y >= SURFACE && y <= SURFACE + 3) {
                            s = AIR;
                        } else if (passage(dx, dz) && y == FLOOR) {
                            s = bridgeStone;
                        } else {
                            s = wallState(y, x, z);
                        }
                    } else if (y < FLOOR) {
                        s = d < PLATFORM_R ? platformStone : (bridge(dx, dz) && y == FLOOR - 1 ? bridgeStone : LAVA);
                    } else if (y == FLOOR) {
                        if (d >= SECTION_R) {
                            s = bridge(dx, dz) ? bridgeStone : LAVA;
                        } else {
                            s = floorState(x, z);
                        }
                    } else {
                        s = AIR;
                    }
                    level.setBlock(p, s, flags);
                }
            }
        }
        BlockState vein = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState lamp = Blocks.SHROOMLIGHT.defaultBlockState();
        for (BlockPos v : veinCells) {
            level.setBlock(v, vein, flags);
        }
        for (BlockPos l : lampCells) {
            level.setBlock(l, lamp, flags);
        }
        // 入口の壁の 1 枚 (前室とのあいだ) は、開いた既定の扉 (戦うグループにだけ閉じる)
        GroupGates.place(level, gate(), true);
        java.util.Arrays.fill(state, 0);
        java.util.Arrays.fill(timer, 0);
        cold = false;
        SkyAbyss.LOGGER.info("Built the ember heart chamber (the bottom of the embers)");
    }

    // ---------------------------------------------------------------- 区画の予告・沈み
    public static boolean sunk(int k) {
        return state[k] == 2;
    }

    public static boolean cracking(int k) {
        return state[k] == 1;
    }

    public static boolean standing(int k) {
        return state[k] == 0;
    }

    public static int sinkingCount() {
        int n = 0;
        for (int s : state) {
            n += s != 0 ? 1 : 0;
        }
        return n;
    }

    // 扇 k の床を割る (予告 2.0 秒のあと沈む)。上限か、もう割れていれば false
    public static boolean crack(ServerLevel level, int k) {
        if (k < 0 || k >= SECTIONS || state[k] != 0 || sinkingCount() >= MAX_SINKING) {
            return false;
        }
        state[k] = 1;
        timer[k] = CRACK_TICKS;
        Vec3 c = sectorCenter(k);
        level.playSound(null, c.x, c.y, c.z, EmberHeartSetup.FLOOR.get(), SoundSource.HOSTILE, 2.5f, 1f);
        // 床が赤く割れる: 市松に光る苔 (砕けた割れ目) を置く
        BlockState glow = Blocks.SHROOMLIGHT.defaultBlockState();
        for (BlockPos b : sectionBlocks().get(k)) {
            if (((b.getX() + b.getZ()) & 1) == 0) {
                level.setBlock(b, glow, 2 | 16);
            }
        }
        return true;
    }

    // 次に沈める扇 (沈んでおらず、割れてもいない扇。人の多い扇を優先し、なければ番号順)
    public static int pickSector(ServerLevel level, int after) {
        int best = -1;
        int bestCount = -1;
        for (int i = 1; i <= SECTIONS; i++) {
            int k = Math.floorMod(after + i, SECTIONS);
            if (state[k] != 0) {
                continue;
            }
            int n = 0;
            for (ServerPlayer player : players(level)) {
                if (sectorAt(player.getX(), player.getZ()) == k) {
                    n++;
                }
            }
            if (n > bestCount) {
                best = k;
                bestCount = n;
            }
        }
        return best;
    }

    private static List<ServerPlayer> players(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, arena(), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    // 毎 tick (EmberHeartBattle から)。割れた床の予告の粒と、沈み・戻り、落ちた人の救い上げ
    static void tick(ServerLevel level, @Nullable EmberHeart heart) {
        for (int k = 0; k < SECTIONS; k++) {
            if (state[k] == 1) {
                timer[k]--;
                if (timer[k] % 3 == 0) {
                    warnCrack(level, k);
                }
                if (timer[k] % 8 == 0) {
                    Vec3 c = sectorCenter(k);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.BASALT_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f + 0.1f * ((CRACK_TICKS - timer[k]) / 8));
                }
                if (timer[k] <= 0) {
                    sink(level, k, heart);
                }
            } else if (state[k] == 2) {
                timer[k]--;
                if (timer[k] % 8 == 0) {
                    Vec3 c = sectorCenter(k);
                    for (int i = 0; i < 4; i++) {
                        double a = level.random.nextDouble() * Math.PI * 2;
                        double r = 3 + level.random.nextDouble() * 7;
                        level.sendParticles(ParticleTypes.LAVA, CX + 0.5 + Math.cos(Math.toRadians(sectorAngle(k)) + a * 0.2) * r,
                                SURFACE + 0.2, CZ + 0.5 + Math.sin(Math.toRadians(sectorAngle(k)) + a * 0.2) * r, 1, 0.1, 0, 0.1, 0);
                    }
                    level.sendParticles(ParticleTypes.LAVA, c.x, SURFACE + 0.5, c.z, 2, 3, 0.1, 3, 0);
                }
                if (timer[k] <= 0) {
                    restore(level, k);
                }
            }
        }
        if (level.getGameTime() % 2 == 0) {
            rescue(level, heart);
        }
    }

    private static void warnCrack(ServerLevel level, int k) {
        List<BlockPos> blocks = sectionBlocks().get(k);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1f, 0.25f, 0.1f), 1.5f);
        for (int i = 0; i < 14; i++) {
            BlockPos b = blocks.get(level.random.nextInt(blocks.size()));
            warn(level, red, b.getX() + 0.5, SURFACE + 0.15, b.getZ() + 0.5, 1, 0.3, 0.05, 0.3, 0);
        }
        BlockPos b = blocks.get(level.random.nextInt(blocks.size()));
        warn(level, ParticleTypes.LAVA, b.getX() + 0.5, SURFACE + 0.2, b.getZ() + 0.5, 1, 0.2, 0, 0.2, 0);
    }

    // 予告の粒は「制限を無視する」印を付けて送る (パーティクル「最小」の設定でも描かれる)
    static void warn(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (player.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    private static void sink(ServerLevel level, int k, @Nullable EmberHeart heart) {
        state[k] = 2;
        timer[k] = SINK_TICKS;
        for (BlockPos b : sectionBlocks().get(k)) {
            level.setBlock(b, LAVA, 2 | 16);
        }
        Vec3 c = sectorCenter(k);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 2.0f, 0.5f);
        level.sendParticles(ParticleTypes.LAVA, c.x, SURFACE + 0.5, c.z, 40, 4, 0.3, 4, 0);
        // 沈んだとき、その扇に立っていた人は呑まれる
        for (ServerPlayer player : players(level)) {
            if (sectorAt(player.getX(), player.getZ()) == k && player.getY() < SURFACE + 1.0) {
                engulf(level, player, heart);
            }
        }
    }

    private static void restore(ServerLevel level, int k) {
        for (BlockPos b : sectionBlocks().get(k)) {
            level.setBlock(b, floorState(b.getX(), b.getZ()), 2 | 16);
        }
        state[k] = 0;
        timer[k] = 0;
        Vec3 c = sectorCenter(k);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 1.4f, 1.1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, SURFACE + 0.5, c.z, 30, 4, 0.2, 4, 0.02);
    }

    // すべて元に戻す (放棄・撃破・段階 3 に入る・やり直し)
    public static void restoreAll(ServerLevel level) {
        for (int k = 0; k < SECTIONS; k++) {
            if (state[k] != 0) {
                restore(level, k);
            }
            state[k] = 0;
            timer[k] = 0;
        }
    }

    // ---------------------------------------------------------------- 呑まれた
    // 区画が沈んだ・溝や溜めに落ちた。台の上へ戻す (傷 12、熱 +40)
    public static void engulf(ServerLevel level, ServerPlayer player, @Nullable Entity by) {
        long now = level.getGameTime();
        Long last = engulfedAt.get(player.getUUID());
        if (last != null && now - last < 20) {
            return;
        }
        engulfedAt.put(player.getUUID(), now);
        player.clearFire();
        player.fallDistance = 0;
        double a = level.random.nextDouble() * Math.PI * 2;
        double r = 1.8 + level.random.nextDouble() * 1.4;
        double tx = CX + 0.5 + Math.cos(a) * r;
        double tz = CZ + 0.5 + Math.sin(a) * r;
        player.teleportTo(level, tx, SURFACE, tz, player.getYRot(), player.getXRot());
        if (player.distanceToSqr(tx, SURFACE, tz) > 1) {
            // 通信の先を持たない人 (自己テストの偽のプレイヤー) は、位置を直接置く
            player.moveTo(tx, SURFACE, tz, player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;
        player.hurt(source(level, EmberIds.ENGULFED, by), ENGULF_DAMAGE);
        EmberHeat.add(player, ENGULF_HEAT, EmberHeat.Reason.ENGULFED);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_ON_FIRE, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 14, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
    }

    public static DamageSource source(ServerLevel level, net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> type,
            @Nullable Entity by) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), by);
    }

    // 溶岩の面より下 (沈んだ区画・溝・溜め) にいる人は呑まれる
    // tick が 2 tick に一度だけ呼ぶ。自己テストは同じ tick のうちに続けて呼ぶので、直接呼べるようにしてある
    static void rescue(ServerLevel level, @Nullable EmberHeart heart) {
        for (ServerPlayer player : players(level)) {
            if (player.getY() < SURFACE - 0.35 && player.getY() > BOTTOM && dist((int) Math.floor(player.getX()), (int) Math.floor(player.getZ())) < RADIUS) {
                engulf(level, player, heart);
            }
        }
    }

    // ---------------------------------------------------------------- 段階 3 の冷え (血管と灯が消える)
    public static boolean isCold() {
        return cold;
    }

    public static void setCold(ServerLevel level, boolean on) {
        computeLists();
        if (cold == on && !on) {
            // 温かいまま。見えない光だけ片づける
            clearAmbient(level);
            return;
        }
        cold = on;
        int flags = 2 | 16;
        BlockState vein = on ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockState lamp = on ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState();
        for (BlockPos v : veinCells) {
            level.setBlock(v, vein, flags);
        }
        for (BlockPos l : lampCells) {
            level.setBlock(l, lamp, flags);
        }
        for (BlockPos m : moatCells) {
            level.setBlock(m, on ? Blocks.BLACKSTONE.defaultBlockState() : LAVA, flags);
        }
        if (on) {
            BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, AMBIENT_LIGHT);
            for (BlockPos a : ambientCells) {
                level.setBlock(a, light, flags);
            }
        } else {
            clearAmbient(level);
        }
    }

    private static void clearAmbient(ServerLevel level) {
        if (ambientCells == null) {
            return;
        }
        for (BlockPos a : ambientCells) {
            if (level.getBlockState(a).is(Blocks.LIGHT)) {
                level.setBlock(a, AIR, 2 | 16);
            }
        }
    }

    // 残り火の灯 (見えない光)。段階 3 の暗さのなかでも、火のそばは明るい
    static void emberLight(ServerLevel level, double x, double z, boolean on) {
        BlockPos at = BlockPos.containing(x, SURFACE, z);
        if (on) {
            if (level.getBlockState(at).isAir()) {
                level.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, EMBER_LIGHT), 2 | 16);
            }
        } else if (level.getBlockState(at).is(Blocks.LIGHT)) {
            level.setBlock(at, AIR, 2 | 16);
        }
    }

    // ---------------------------------------------------------------- 入口
    // 入口はいつも置いてある扉 (開いた既定)。戦っているグループにだけ閉じる (GroupGates。ほかのグループは部屋の占有で入れない)
    static void seal(ServerLevel level, UUID group) {
        GroupGates.place(level, gate(), true);
        GroupGates.seal(level, group, gate());
    }

    static void open(ServerLevel level, UUID group) {
        if (GroupGates.open(level, group, gate())) {
            for (BlockPos p : gate()) {
                jp.revati.skyabyss.titan.ember.back.EmberBackTrials.particlesTo(level, group, ParticleTypes.SMALL_FLAME, p.getX() + 0.5, p.getY() + 0.5,
                        p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    // そのグループにとって入口が閉じているか
    public static boolean sealed(ServerLevel level, UUID group) {
        return !GroupGates.isOpen(level, group, gate().get(0));
    }

    // いま戦っている (最後に戦った) グループにとって入口が閉じているか (自己テスト・コマンド)
    public static boolean sealed(ServerLevel level) {
        UUID group = EmberHeartBattle.lastGroup();
        return group != null && sealed(level, group);
    }

    // 戦いを終える・放棄するときの片づけ (区画・血管・灯・残り火の光)
    static void cleanup(ServerLevel level) {
        restoreAll(level);
        setCold(level, false);
        clearEmberLights(level);
    }

    static void clearEmberLights(ServerLevel level) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - RADIUS; x <= CX + RADIUS; x++) {
            for (int z = CZ - RADIUS; z <= CZ + RADIUS; z++) {
                p.set(x, SURFACE, z);
                if (level.getBlockState(p).is(Blocks.LIGHT)) {
                    level.setBlock(p, AIR, 2 | 16);
                }
            }
        }
    }

    static void resetState() {
        java.util.Arrays.fill(state, 0);
        java.util.Arrays.fill(timer, 0);
        cold = false;
        engulfedAt.clear();
    }

    // 自己テストが見る: 沈んでいる扇のブロックの数
    public static int lavaCount(ServerLevel level, int k) {
        int n = 0;
        for (BlockPos b : sectionBlocks().get(k)) {
            if (level.getBlockState(b).is(Blocks.LAVA)) {
                n++;
            }
        }
        return n;
    }

    public static int sectionSize(int k) {
        return sectionBlocks().get(k).size();
    }

    static void registerRoom() {
        EmberBackRooms.register("heart", EmberHeartArena::buildRoom);
    }
}
