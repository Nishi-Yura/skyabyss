package jp.revati.skyabyss.titan.ember.back.trial;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberRooms;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.CinderRoller;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 西の試練 灰の道 (13 の 4.1節): 溶岩の湖に灰をまいて「冷え殻」の道を作り、崩れる前に渡って、奥の岸へ着く。
//   - 灰の壺 (灰 5 回分。AshUrnItem)。向いている先 4 ブロック以内の溶岩の面に、3 x 3 の冷え殻をつくる。島の灰溜まり (灰色の羊毛) で満ちる
//   - 冷え殻は 12 秒で崩れる: 8 秒まで黒、8〜11 秒で割れ目が赤く光る (ぴしぴし)、11〜12 秒で沈む。
//     火の粉蛾が触れると 3 秒早く崩れる (最短 5 秒は残る)。人が乗っているあいだは、沈むのを最大 3 秒まで待つ
//   - 溶岩に入ると「呑まれた」: 最後に立った島 (なければ入口の岸) へ戻し、4 の傷 (engulfed) と熱 +30。持ち物は燃えない
//   - 島は 7 つ。並びはリセットの種で 3 通り。島は灰溜まりを持つ。蛾の群れと、大きな島の燠虫
// 溶岩の面は床と同じ高さ (FLOOR_WEST = 76) に置く: 冷え殻と島の床が同じ高さで続き、段差で跳ばずに歩ける
public final class AshPath {
    public static final int URN_CHARGES = 5;
    public static final int REACH = 4;
    // 溶岩の面 (ブロックの y)。島・岸の床と同じ
    public static final int LAVA_Y = EmberBackLayout.FLOOR_WEST;
    // 湖の x の範囲 (部屋の内側いっぱい)
    public static final int LAKE_X1 = -61;
    public static final int LAKE_X2 = -25;
    // 試しの流れ (幅いっぱいの溶岩の溝。灰の壺の使い方を教える)・第一の湖・第二の湖の z の範囲。その間は岸 (試しの岸・中ノ岸)
    public static final int CHANNEL_Z1 = 15;
    public static final int CHANNEL_Z2 = 21;
    public static final int LAKE1_Z1 = 32;
    public static final int LAKE1_Z2 = 62;
    public static final int LAKE2_Z1 = 77;
    public static final int LAKE2_Z2 = 104;
    // 溶岩の溜め (z1, z2)。溶岩は y LAVA_Y - 2 〜 LAVA_Y、底は y LAVA_Y - 3
    public static final int[][] LAVA_RANGES = {{CHANNEL_Z1, CHANNEL_Z2}, {LAKE1_Z1, LAKE1_Z2}, {LAKE2_Z1, LAKE2_Z2}};
    // 中ノ岸 (休み場 w1。第一の湖の向こう) と試しの岸 (流れの向こう)
    public static final int MID_Z1 = 63;
    public static final int MID_Z2 = 76;
    public static final int TEACH_Z1 = 22;
    public static final int TEACH_Z2 = 31;

    // 冷え殻の時計 (tick)
    public static final int LIFE = 240;
    public static final int CRACK_BEFORE = 80;
    public static final int SINK_BEFORE = 20;
    public static final int MOTH_SHORTEN = 60;
    public static final int MOTH_MIN_LIFE = 100;
    public static final int STAND_EXTRA_MAX = 60;
    public static final int MOTH_CRUST_COOLDOWN = 40;

    // 呑まれたとき
    public static final float ENGULF_DAMAGE = 4f;
    public static final float ENGULF_HEAT = 30f;

    // 奥の岸 (着いたら石を鳴らせる)
    public static final EmberBackLayout.Box FAR_SHORE = new EmberBackLayout.Box(-48, LAVA_Y + 1, 105, -38, LAVA_Y + 5, 111);

    // ---------------------------------------------------------------- 島の並び (3 通り。第一の湖に 4 つ、第二の湖に 4 つ。どの並びも島と島のあいだは 8 ブロックまで)
    // half 1 = 3 x 3、half 2 = 5 x 5 (四隅を丸める)。第二の湖の大きな島には燠虫が 1 体ずつ。中ノ岸 (休み場 w1) は島ではなく湖のあいだの岸
    public record Island(int cx, int cz, int half) {
        public boolean contains(int x, int z) {
            return Math.abs(x - cx) <= half && Math.abs(z - cz) <= half && !corner(x, z);
        }

        // 5 x 5 の島は四隅を丸める
        boolean corner(int x, int z) {
            return half == 2 && Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
        }

        public boolean big() {
            return half == 2;
        }

        // 第二の湖の島か (燠虫が乗る・蛾が飛ぶ)
        public boolean second() {
            return cz >= LAKE2_Z1;
        }

        // 灰溜まり (灰色の羊毛) の位置 (島の真ん中)
        public BlockPos puddle() {
            return new BlockPos(cx, LAVA_Y, cz);
        }
    }

    public static final List<List<Island>> PATTERNS = List.of(
            List.of(new Island(-50, 38, 1), new Island(-41, 44, 2), new Island(-47, 53, 1), new Island(-36, 57, 2),
                    new Island(-50, 83, 2), new Island(-40, 90, 1), new Island(-48, 97, 2), new Island(-39, 101, 1)),
            List.of(new Island(-36, 38, 1), new Island(-45, 44, 2), new Island(-39, 53, 1), new Island(-50, 57, 2),
                    new Island(-36, 83, 2), new Island(-46, 90, 1), new Island(-38, 97, 2), new Island(-48, 101, 1)),
            List.of(new Island(-43, 38, 2), new Island(-50, 46, 1), new Island(-36, 46, 1), new Island(-43, 55, 2),
                    new Island(-43, 82, 2), new Island(-51, 90, 1), new Island(-36, 91, 1), new Island(-43, 98, 2)));

    // 島の並び: 部屋の持ち主のグループの種から (持ち主がいなければ種 0 の並び)
    public static int patternOf(ServerLevel level) {
        return Math.floorMod(EmberBackState.forRoom(level, EmberIds.WEST).seed(), PATTERNS.size());
    }

    // ---------------------------------------------------------------- 冷え殻の時計
    static final class Crust {
        long born;
        int shorten;
        int extra;
        long lastMoth = Long.MIN_VALUE / 2;
        int stage;
    }

    private static final Map<BlockPos, Crust> CRUSTS = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_STAND = new HashMap<>();
    private static boolean spawned;
    private static long lastMothSpawn;
    // いま湖に建ててある島の並び (-1 = わからない。起動のあとは建て直す)
    private static int builtPattern = -1;

    private AshPath() {
    }

    public static int crustCount() {
        return CRUSTS.size();
    }

    static Crust crustAt(BlockPos pos) {
        return CRUSTS.get(pos);
    }

    // 殻の寿命 (tick。蛾の分を引いたあと、人が乗って待った分は含めない)
    public static int lifeOf(Crust c) {
        return Math.max(MOTH_MIN_LIFE, LIFE - c.shorten);
    }

    public static int stageAt(Crust c, long age) {
        int life = lifeOf(c);
        if (age >= life - SINK_BEFORE) {
            return 2;
        }
        return age >= Math.max(20, life - CRACK_BEFORE) ? 1 : 0;
    }

    static List<BlockPos> crustKeys() {
        return new ArrayList<>(CRUSTS.keySet());
    }

    static void setLastStand(UUID id, BlockPos pos) {
        LAST_STAND.put(id, pos);
    }

    static void clearMemory() {
        CRUSTS.clear();
        LAST_STAND.clear();
        spawned = false;
        builtPattern = -1;
    }

    static void forget(UUID id) {
        LAST_STAND.remove(id);
    }

    static void adopt(ServerLevel level, BlockPos pos) {
        CRUSTS.computeIfAbsent(pos.immutable(), p -> {
            Crust c = new Crust();
            c.born = level.getGameTime();
            return c;
        });
    }

    // 3 x 3 の冷え殻 (溶岩の所だけ)。置いた数
    public static int placeCrust(ServerLevel level, int cx, int cz) {
        int n = 0;
        long now = level.getGameTime();
        BlockState crust = EmberTrialSetup.COOLED_CRUST.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = new BlockPos(cx + dx, LAVA_Y, cz + dz);
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.LAVA)) {
                    level.setBlock(p, crust, Block.UPDATE_ALL);
                    Crust c = new Crust();
                    c.born = now;
                    CRUSTS.put(p, c);
                    n++;
                } else if (s.is(EmberTrialSetup.COOLED_CRUST.get())) {
                    // 重ねてまくと、時計が戻る
                    Crust c = CRUSTS.computeIfAbsent(p, k -> new Crust());
                    c.born = now;
                    c.shorten = 0;
                    c.extra = 0;
                    c.stage = 0;
                    level.setBlock(p, crust, Block.UPDATE_ALL);
                    n++;
                }
            }
        }
        return n;
    }

    // 殻を溶岩に戻す
    static void sink(ServerLevel level, BlockPos pos) {
        CRUSTS.remove(pos);
        if (level.getBlockState(pos).is(EmberTrialSetup.COOLED_CRUST.get())) {
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- 壺
    public static void giveUrn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(EmberTrialSetup.ASH_URN.get())) {
                AshUrnItem.setCharges(s, URN_CHARGES);
                EmberTrials.hint(player, EmberIds.LANG_WEST + "urn_refilled");
                return;
            }
        }
        ItemStack urn = new ItemStack(EmberTrialSetup.ASH_URN.get());
        if (!player.getInventory().add(urn)) {
            Notice.bar(player, ModText.deny(EmberIds.LANG_WEST + "urn_full"));
            return;
        }
        level.playSound(null, player.blockPosition(), EmberTrialSetup.ASH_SCATTER.get(), SoundSource.PLAYERS, 0.6f, 1.3f);
        Notice.narrate(player, EmberIds.LANG_WEST + "urn_given");
    }

    // 灰の壺を使う: 向いている先の溶岩の面へ。まいた数 (0 ならまけなかった)
    public static int useUrn(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (AshUrnItem.charges(stack) <= 0) {
            Notice.bar(player, ModText.deny(EmberIds.LANG_WEST + "urn_empty"));
            return 0;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK || !level.getBlockState(hit.getBlockPos()).is(Blocks.LAVA)) {
            Notice.bar(player, ModText.hint(EmberIds.LANG_WEST + "no_lava"));
            return 0;
        }
        int n = placeCrust(level, hit.getBlockPos().getX(), hit.getBlockPos().getZ());
        if (n == 0) {
            Notice.bar(player, ModText.hint(EmberIds.LANG_WEST + "no_lava"));
            return 0;
        }
        AshUrnItem.setCharges(stack, AshUrnItem.charges(stack) - 1);
        player.getCooldowns().addCooldown(stack.getItem(), 6);
        Vec3 at = Vec3.atCenterOf(hit.getBlockPos()).add(0, 0.5, 0);
        level.playSound(null, at.x, at.y, at.z, EmberTrialSetup.ASH_SCATTER.get(), SoundSource.PLAYERS, 1f, 1f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()), at.x, at.y + 0.3, at.z, 30, 1.0,
                0.2, 1.0, 0.05);
        Notice.bar(player, ModText.hint(EmberIds.LANG_WEST + "urn_left", AshUrnItem.charges(stack), URN_CHARGES));
        return n;
    }

    // 灰溜まり (灰色の羊毛) に触れて満ちる。満ちたか
    public static boolean refillAt(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!EmberBackLayout.WEST.contains(pos) || !level.getBlockState(pos).is(Blocks.GRAY_WOOL)) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(EmberTrialSetup.ASH_URN.get()) && AshUrnItem.charges(s) < URN_CHARGES) {
                AshUrnItem.setCharges(s, URN_CHARGES);
                any = true;
            }
        }
        if (any) {
            level.playSound(null, pos, EmberTrialSetup.ASH_SCATTER.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            EmberTrials.hint(player, EmberIds.LANG_WEST + "urn_refilled");
        }
        return any;
    }

    // ---------------------------------------------------------------- 呑まれた
    // 毎 tick の頭に、足が溶岩に入った人を見る (バニラの溶岩の傷が入る前に運び出す)
    static void checkEngulfed(ServerPlayer p) {
        if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (!EmberBackLayout.WEST.contains(p.position())) {
            return;
        }
        if (level.getFluidState(p.blockPosition()).is(FluidTags.LAVA) || p.isInLava()) {
            engulf(level, p);
        }
    }

    public static void engulf(ServerLevel level, ServerPlayer p) {
        BlockPos to = LAST_STAND.get(p.getUUID());
        if (to == null) {
            to = EmberBackLayout.rest("w0").floor().above();
        }
        level.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, p.blockPosition(), EmberTrialSetup.ENGULF.get(), SoundSource.PLAYERS, 1f, 1f);
        p.clearFire();
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
        if (p.distanceToSqr(to.getX() + 0.5, to.getY(), to.getZ() + 0.5) > 1.0) {
            // 位置合わせができない人 (接続のない偽のプレイヤーなど) は、そのまま置く
            p.setPos(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.invulnerableTime = 0;
        p.hurt(EmberTrials.damage(level, EmberIds.ENGULFED, null), ENGULF_DAMAGE);
        EmberHeat.add(p, ENGULF_HEAT, EmberHeat.Reason.ENGULFED);
        Notice.narrate(p, EmberIds.LANG_WEST + "engulfed");
    }

    // ---------------------------------------------------------------- 毎 tick
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (!CRUSTS.isEmpty()) {
            tickCrusts(level, now, Players.snapshot(level.players()));
            if (now % 5 == 0) {
                mothTouch(level, now);
            }
        }
        if (now % 10 != 0) {
            return;
        }
        List<ServerPlayer> in = EmberTrials.playersIn(level, EmberBackLayout.WEST);
        for (ServerPlayer p : in) {
            trackStand(level, p);
            rescueUrn(level, p);
            if (FAR_SHORE.contains(p.position())
                    && !EmberBackState.forRoom(level, EmberIds.WEST).ready(EmberBackState.Trial.WEST)) {
                EmberTrials.markReady(level, EmberBackState.Trial.WEST);
                Notice.narrate(p, EmberIds.LANG_WEST + "far_shore");
            }
        }
        if (!in.isEmpty() && !EmberBackTrials.quiet(level, EmberIds.WEST)) {
            keepEnemies(level, in, now);
        }
    }

    // 島の上で壺を失った人 (死んで休み場 w1 で目覚めた、ログアウト、落とした) は湖の真ん中で動けなくなる。
    // 島に立っていて壺を持っていなければ、手元へ戻す (入口の岸は台から受け取るので対象外)
    static void rescueUrn(ServerLevel level, ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
            return;
        }
        BlockPos feet = p.blockPosition();
        if (feet.getY() != LAVA_Y + 1) {
            return;
        }
        boolean onIsland = feet.getZ() >= TEACH_Z1 && feet.getZ() <= TEACH_Z2 || feet.getZ() >= MID_Z1 && feet.getZ() <= MID_Z2;
        for (Island i : PATTERNS.get(patternOf(level))) {
            if (i.contains(feet.getX(), feet.getZ())) {
                onIsland = true;
                break;
            }
        }
        if (!onIsland) {
            return;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(EmberTrialSetup.ASH_URN.get())) {
                return;
            }
        }
        giveUrn(p);
    }

    private static void trackStand(ServerLevel level, ServerPlayer p) {
        if (!p.onGround()) {
            return;
        }
        BlockPos feet = p.blockPosition();
        BlockPos below = feet.below();
        if (feet.getY() == LAVA_Y + 1 && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                && !level.getBlockState(below).is(EmberTrialSetup.COOLED_CRUST.get()) && !level.getBlockState(below).is(Blocks.LAVA)) {
            LAST_STAND.put(p.getUUID(), feet.immutable());
        }
    }

    private static boolean occupied(List<ServerPlayer> players, BlockPos pos) {
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator() && Math.abs(p.getX() - (pos.getX() + 0.5)) < 0.8 && Math.abs(p.getZ() - (pos.getZ() + 0.5)) < 0.8
                    && p.getY() >= pos.getY() + 0.7 && p.getY() <= pos.getY() + 2.0) {
                return true;
            }
        }
        return false;
    }

    static void tickCrusts(ServerLevel level, long now, List<ServerPlayer> players) {
        Iterator<Map.Entry<BlockPos, Crust>> it = CRUSTS.entrySet().iterator();
        List<BlockPos> sunk = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Crust> e = it.next();
            BlockPos pos = e.getKey();
            Crust c = e.getValue();
            if (!level.getBlockState(pos).is(EmberTrialSetup.COOLED_CRUST.get())) {
                it.remove();
                continue;
            }
            long age = now - c.born;
            int life = lifeOf(c);
            if (age >= life + c.extra) {
                if (c.extra < STAND_EXTRA_MAX && occupied(players, pos)) {
                    // 人が乗っている: 最大 3 秒まで待つ (足もとから急に消えない)
                    c.extra++;
                } else {
                    sunk.add(pos);
                    continue;
                }
            }
            int stage = c.extra > 0 ? 2 : stageAt(c, age);
            if (stage != c.stage) {
                c.stage = stage;
                level.setBlock(pos, level.getBlockState(pos).setValue(CooledCrustBlock.STAGE, stage), Block.UPDATE_CLIENTS);
                if (stage == 1) {
                    level.playSound(null, pos, EmberTrialSetup.CRUST_CRACK.get(), SoundSource.BLOCKS, 0.8f, 1f);
                } else if (stage == 2) {
                    level.playSound(null, pos, EmberTrialSetup.CRUST_SINK.get(), SoundSource.BLOCKS, 0.6f, 1f);
                }
            } else if (stage >= 1 && level.random.nextInt(14) == 0) {
                level.playSound(null, pos, EmberTrialSetup.CRUST_CRACK.get(), SoundSource.BLOCKS, 0.4f, 0.8f + level.random.nextFloat() * 0.5f);
                level.sendParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.01);
            }
        }
        for (BlockPos p : sunk) {
            level.sendParticles(ParticleTypes.LAVA, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.0);
            sink(level, p);
        }
    }

    // 火の粉蛾が殻に触れたら、その殻は 3 秒早く崩れる (最短 5 秒は残る)
    static void mothTouch(ServerLevel level, long now) {
        for (EmberMoth moth : level.getEntitiesOfClass(EmberMoth.class, EmberBackLayout.WEST.aabb())) {
            BlockPos p = new BlockPos(moth.getBlockX(), LAVA_Y, moth.getBlockZ());
            Crust c = CRUSTS.get(p);
            if (c != null && moth.getY() < LAVA_Y + 2.6 && now - c.lastMoth >= MOTH_CRUST_COOLDOWN) {
                c.lastMoth = now;
                c.shorten += MOTH_SHORTEN;
            }
        }
    }

    // ---------------------------------------------------------------- 敵
    // 最初の蛾の数 (一人 6、一人増えるごとに +2。パーティの上限 6 人まで数える)
    public static int mothTarget(int players) {
        return 6 + 2 * (Math.max(1, Math.min(Parties.MAX_MEMBERS, players)) - 1);
    }

    private static void keepEnemies(ServerLevel level, List<ServerPlayer> in, long now) {
        AABB room = EmberBackLayout.WEST.aabb();
        List<Island> islands = PATTERNS.get(patternOf(level));
        if (!spawned) {
            spawned = true;
            for (Island i : islands) {
                if (i.big() && i.second()) {
                    EmberTrials.spawn(level, EmberMobSetup.CINDER_ROLLER.get(), i.cx() + 0.5, LAVA_Y + 1, i.cz() + 0.5);
                }
            }
            lastMothSpawn = now;
            int n = mothTarget(in.size());
            for (int k = 0; k < n; k++) {
                spawnMoth(level, k);
            }
        } else if (now - lastMothSpawn >= 100) {
            lastMothSpawn = now;
            int cap = Math.min(EmberMoth.roomCap(in.size()), mothTarget(in.size()));
            if (EmberMoth.countIn(level, room, EmberRooms.owner(EmberIds.WEST)) < cap) {
                spawnMoth(level, (int) now);
            }
        }
    }

    private static void spawnMoth(ServerLevel level, int salt) {
        double x = LAKE_X1 + 3 + level.random.nextInt(LAKE_X2 - LAKE_X1 - 5);
        double z = LAKE2_Z1 + 3 + level.random.nextInt(LAKE2_Z2 - LAKE2_Z1 - 5);
        EmberTrials.spawn(level, EmberMobSetup.EMBER_MOTH.get(), x + 0.5, LAVA_Y + 3 + (salt % 3), z + 0.5);
    }

    // ---------------------------------------------------------------- リセット (EmberBackRooms.onReset("west", ...))
    // 部屋の持ち主が替わるたび (手放した・新しい持ち主が入った)。冷え殻を溶岩に戻し、島の並びが持ち主の種と違えば湖を建て直す
    public static void reset(ServerLevel level, boolean quiet) {
        EmberTrials.clearMobs(level, EmberBackLayout.WEST);
        EmberTrials.clearItems(level, EmberBackLayout.WEST, EmberTrialSetup.ASH_URN.get());
        for (BlockPos p : crustKeys()) {
            sink(level, p);
        }
        CRUSTS.clear();
        LAST_STAND.clear();
        spawned = false;
        int pattern = patternOf(level);
        if (pattern != builtPattern) {
            AshPathRoom.buildLake(level, pattern);
            builtPattern = pattern;
        }
    }

    // 湖を建て直したことを覚える (AshPathRoom から)
    static void lakeBuilt(int pattern) {
        builtPattern = pattern;
    }

    // 試練の敵の数 (テスト用)
    public static int enemyCount(ServerLevel level) {
        int n = 0;
        for (Mob m : level.getEntitiesOfClass(Mob.class, EmberBackLayout.WEST.aabb(), e -> e instanceof EmberMoth || e instanceof CinderRoller)) {
            n++;
        }
        return n;
    }
}
