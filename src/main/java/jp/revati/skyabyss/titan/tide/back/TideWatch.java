package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 東の試練 潮見の間 (08 の 4.2節) のブロック側。決まりは TidePuzzle。
// 三段の溜め (下 B・中 M・上 T) の水位を、潮の栓 (取っ手) で動かす。B と M には浮き石が浮かび、水位と一緒に上下する。
// 浮き石が 5 段 (壁の灯りの目盛り) に届けば次の段へ。上の溜め T が空になると、底の戸から岩棚 (東の潮鳴り石) へ。
// 溜めの戸は、水が 1 段以下 (T は 0) のときだけ開く。戸の中に人がいるあいだは、水は動かない (挟まない)。
// 重石の床に誰かが立っている間は、つながった栓の向きが逆になる (二人なら近道。一人でも解ける)。
// 前室の戻しの栓を引くと、三段を最初の水位に戻す (30 秒に一度)。潮の時計とは関係なく、栓でだけ動く
public final class TideWatch {
    public static final int RESET_COOLDOWN = 30 * 20;
    public static final String WATCH_TAG = "skyabyss_watch_mob";

    // 取っ手を置く所 (段ごとに 3 つ)。台の石の上に取っ手、看板は石の段の内側の面
    static final BlockPos[][] SLOTS = {
            {new BlockPos(31, 75, -13), new BlockPos(31, 75, -7), new BlockPos(29, 75, -7)},
            {new BlockPos(42, 81, -13), new BlockPos(42, 81, -7), new BlockPos(40, 81, -7)},
            {new BlockPos(55, 87, -9), new BlockPos(55, 87, -12), new BlockPos(51, 87, -9)}};
    static final BlockPos[] PLATES = {new BlockPos(29, 75, -13), new BlockPos(40, 81, -13), new BlockPos(51, 87, -14)};
    static final BlockPos RESET_LEVER = new BlockPos(24, 75, -8);

    private static int config;
    private static final int[] levels = new int[3];
    private static boolean applied;
    private static int flowTimer;
    private static long lastReset = -RESET_COOLDOWN;
    private static boolean spawned;

    private TideWatch() {
    }

    public static int config() {
        return config;
    }

    public static int[] levels() {
        return levels.clone();
    }

    static TidePuzzle.Config cfg() {
        return TidePuzzle.CONFIGS[Math.floorMod(config, TidePuzzle.CONFIGS.length)];
    }

    // ---------------------------------------------------------------- リセット (配置を決め、取っ手と看板を置き直し、最初の水位へ)
    static void reset(ServerLevel level, TideBackState state) {
        config = state.eastConfig();
        TidePuzzle.Config c = cfg();
        // 取っ手の台をすべて片付ける
        for (BlockPos[] tier : SLOTS) {
            for (BlockPos s : tier) {
                clearSlot(level, s);
            }
        }
        for (BlockPos p : PLATES) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(p.west(), Blocks.AIR.defaultBlockState(), 3);
        }
        int[] used = new int[3];
        for (int i = 0; i < c.plugs().length; i++) {
            TidePuzzle.Plug plug = c.plugs()[i];
            BlockPos slot = SLOTS[plug.tier()][used[plug.tier()]++];
            placeLever(level, slot, "sign.skyabyss.tide_plug_" + "bmt".charAt(plug.from()) + "bmt".charAt(plug.to()));
        }
        BlockPos plate = PLATES[c.plateTier()];
        level.setBlock(plate, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.defaultBlockState(), 3);
        TidePuzzle.Plug pp = c.plugs()[c.platePlug()];
        level.setBlock(plate.west(), Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 12), 3);
        new TideKit(level, level.random).writeSign(plate.west(), "sign.skyabyss.tide_plate_" + "bmt".charAt(pp.from()) + "bmt".charAt(pp.to()),
                net.minecraft.world.item.DyeColor.LIGHT_BLUE);
        placeLever(level, RESET_LEVER, "sign.skyabyss.tide_plug_reset");
        // 溜めの壁を建て直し、上限の線 (青い線) を引く
        for (int k = 0; k < 3; k++) {
            int[] b = BASIN[k];
            int top = k < 2 ? TIER_FLOOR[k + 1] : LEDGE_FLOOR;
            int capY = TIER_FLOOR[k] + c.caps()[k] - 1;
            for (int x = b[0] - 1; x <= b[1] + 1; x++) {
                for (int z = b[2] - 1; z <= b[3] + 1; z++) {
                    boolean inside = x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3];
                    if (inside) {
                        continue;
                    }
                    for (int y = TIER_FLOOR[k]; y <= top; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (isDoor(p)) {
                            continue;
                        }
                        BlockState s = y == capY ? Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState() : wall(x, y, z, k);
                        // 段の床の高さの縁 (次の段の床) は床のまま
                        if (y == top && k < 2 && x == b[1] + 1) {
                            s = TideKit.FLOOR;
                        }
                        level.setBlock(p, s, 3);
                    }
                }
            }
        }
        System.arraycopy(c.init(), 0, levels, 0, 3);
        applied = false;
        apply(level, null);
        flowTimer = TidePuzzle.FLOW_TICKS;
        lastReset = -RESET_COOLDOWN;
        for (Entity e : level.getEntitiesOfClass(Entity.class, area().inflate(4), e -> e.getTags().contains(WATCH_TAG))) {
            e.discard();
        }
        spawned = false;
        // 鳴っている (眠っている) なら、岩棚の石も鳴らしておく
        level.setBlock(STONE_EAST.below(), Blocks.CHISELED_SANDSTONE.defaultBlockState(), 3);
        level.setBlock(STONE_EAST, TideBackSetup.CHIME_STONE.get().defaultBlockState()
                .setValue(TideChimeStoneBlock.ACTIVE, state.rung(TideBackState.Trial.EAST)), 3);
        level.setBlock(RETURN_EAST, state.rung(TideBackState.Trial.EAST) ? TideKit.LAMP : TideKit.DARK, 3);
    }

    private static BlockState wall(int x, int y, int z, int k) {
        int mark = TIER_FLOOR[k] + TidePuzzle.REACH;
        if (y == mark && (x + z) % 2 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return (y - TIER_FLOOR[k]) % 2 == 0 ? TideKit.DARK : TideKit.BRICK;
    }

    private static boolean isDoor(BlockPos p) {
        for (BlockPos d : BASIN_DOOR) {
            if (p.equals(d) || p.equals(d.above()) || p.equals(d.below())) {
                return true;
            }
        }
        return p.equals(T_EXIT_DOOR) || p.equals(T_EXIT_DOOR.above()) || p.equals(T_EXIT_DOOR.below());
    }

    private static void clearSlot(ServerLevel level, BlockPos s) {
        level.setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(s.relative(d)).getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
                level.setBlock(s.relative(d), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(s, Blocks.AIR.defaultBlockState(), 3);
    }

    // 台の石と取っ手、取っ手の行き先を書いた看板 (段の内側を向く)
    private static void placeLever(ServerLevel level, BlockPos slot, String signKey) {
        level.setBlock(slot, Blocks.CHISELED_TUFF.defaultBlockState(), 3);
        Direction face = slot.getZ() <= -12 && slot.getX() < 50 ? Direction.SOUTH : slot.getZ() >= -8 && slot.getX() < 50 ? Direction.NORTH
                : slot.getX() >= 54 ? Direction.WEST : slot.getX() <= 25 ? Direction.EAST : Direction.EAST;
        level.setBlock(slot.above(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, face).setValue(LeverBlock.POWERED, false), 3);
        BlockPos sign = slot.relative(face);
        level.setBlock(sign, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, face), 3);
        new TideKit(level, level.random).writeSign(sign, signKey, net.minecraft.world.item.DyeColor.LIGHT_BLUE);
    }

    static boolean leverOn(ServerLevel level, BlockPos slot) {
        BlockState s = level.getBlockState(slot.above());
        return s.is(Blocks.LEVER) && s.getValue(LeverBlock.POWERED);
    }

    static void setLever(ServerLevel level, BlockPos slot, boolean on) {
        BlockState s = level.getBlockState(slot.above());
        if (s.is(Blocks.LEVER) && s.getValue(LeverBlock.POWERED) != on) {
            level.setBlock(slot.above(), s.setValue(LeverBlock.POWERED, on), 3);
        }
    }

    static BlockPos leverSlot(int plugIndex) {
        TidePuzzle.Config c = cfg();
        int[] used = new int[3];
        for (int i = 0; i <= plugIndex; i++) {
            int t = c.plugs()[i].tier();
            if (i == plugIndex) {
                return SLOTS[t][used[t]];
            }
            used[t]++;
        }
        return SLOTS[0][0];
    }

    static boolean platePressed(ServerLevel level) {
        BlockState s = level.getBlockState(PLATES[cfg().plateTier()]);
        return s.getBlock() instanceof PressurePlateBlock && s.getValue(PressurePlateBlock.POWERED);
    }

    // ---------------------------------------------------------------- 毎 tick
    static void tick(ServerLevel level, TideBackState state, boolean peaceful) {
        if (level.getEntitiesOfClass(ServerPlayer.class, area().inflate(6), p -> !p.isSpectator()).isEmpty()) {
            return;
        }
        if (!applied) {
            apply(level, null);
        }
        // 戻しの栓
        if (leverOn(level, RESET_LEVER)) {
            setLever(level, RESET_LEVER, false);
            long now = level.getGameTime();
            if (now - lastReset < RESET_COOLDOWN) {
                for (ServerPlayer p : near(level)) {
                    p.displayClientMessage(ModText.deny("message.skyabyss.tide.reset_plug_wait", (RESET_COOLDOWN - (now - lastReset)) / 20 + 1), true);
                }
            } else {
                lastReset = now;
                TidePuzzle.Config c = cfg();
                for (int i = 0; i < c.plugs().length; i++) {
                    setLever(level, leverSlot(i), false);
                }
                int[] before = levels.clone();
                System.arraycopy(c.init(), 0, levels, 0, 3);
                apply(level, before);
                level.playSound(null, RESET_LEVER, TideBackSetup.PLUG_FLOW.get(), SoundSource.BLOCKS, 2f, 0.7f);
                for (ServerPlayer p : near(level)) {
                    p.sendSystemMessage(ModText.lore("message.skyabyss.tide.reset_plug"));
                }
            }
        }
        if (!peaceful && !spawned && !state.rung(TideBackState.Trial.EAST)) {
            spawned = true;
            spawnGuards(level);
        }
        if (--flowTimer > 0) {
            return;
        }
        flowTimer = TidePuzzle.FLOW_TICKS;
        TidePuzzle.Config c = cfg();
        boolean[] open = new boolean[c.plugs().length];
        for (int i = 0; i < open.length; i++) {
            open[i] = leverOn(level, leverSlot(i));
        }
        int[] next = levels.clone();
        if (!TidePuzzle.step(c, next, open, platePressed(level))) {
            return;
        }
        // 戸が閉じる溜めの戸口に人がいれば、待つ
        for (int k = 0; k < 3; k++) {
            boolean wasOpen = doorOpen(k, levels[k]);
            boolean willOpen = doorOpen(k, next[k]);
            if (wasOpen && !willOpen && someoneInDoor(level, k)) {
                for (ServerPlayer p : near(level)) {
                    p.displayClientMessage(ModText.hint("message.skyabyss.tide.door_blocked"), true);
                }
                return;
            }
        }
        int[] before = levels.clone();
        System.arraycopy(next, 0, levels, 0, 3);
        apply(level, before);
        // 管を流れる水の粒と音
        for (int k = 0; k < 3; k++) {
            if (next[k] < before[k]) {
                for (int j = 0; j < 3; j++) {
                    if (next[j] > before[j]) {
                        pipe(level, k, j);
                    }
                }
            }
        }
        level.playSound(null, BASIN_DOOR[1], TideBackSetup.PLUG_FLOW.get(), SoundSource.BLOCKS, 1.5f, 1f);
    }

    static boolean doorOpen(int k, int level) {
        return k < 2 ? level <= 1 : level == 0;
    }

    private static boolean someoneInDoor(ServerLevel level, int k) {
        BlockPos d = BASIN_DOOR[k];
        AABB box = new AABB(d).expandTowards(0, 1, 0).inflate(0.05);
        if (k == 2) {
            box = box.minmax(new AABB(T_EXIT_DOOR).expandTowards(0, 1, 0));
        }
        return !level.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator()).isEmpty();
    }

    private static List<ServerPlayer> near(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, area().inflate(6), p -> !p.isSpectator());
    }

    static AABB area() {
        return new AABB(22, HUB_LEDGE, -37, 59, LEDGE_FLOOR + 8, -3);
    }

    private static void pipe(ServerLevel level, int from, int to) {
        Vec3 a = center(from);
        Vec3 b = center(to);
        for (int i = 0; i <= 10; i++) {
            Vec3 p = a.lerp(b, i / 10.0);
            level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.05);
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP, b.x, b.y, b.z, 12, 1.2, 0.2, 1.2, 0.05);
    }

    private static Vec3 center(int k) {
        int[] b = BASIN[k];
        return new Vec3((b[0] + b[1]) / 2.0 + 0.5, TIER_FLOOR[k] + levels[k] + 0.5, (b[2] + b[3]) / 2.0 + 0.5);
    }

    // ---------------------------------------------------------------- 水と浮き石と戸を、いまの水位に合わせる
    static void apply(ServerLevel level, int[] before) {
        applied = true;
        for (int k = 0; k < 3; k++) {
            int[] b = BASIN[k];
            int f = TIER_FLOOR[k];
            int top = k < 2 ? TIER_FLOOR[k + 1] : LEDGE_FLOOR;
            int L = levels[k];
            boolean hasFloat = k < 2;
            int floatY = f + L;
            // 浮き石に乗っている人を、上がる分だけ持ち上げる
            if (hasFloat && before != null && before[k] < L) {
                int oldY = f + before[k];
                AABB on = new AABB(b[0], oldY + 0.5, -11, b[1] + 1, oldY + 2.6, -8);
                for (Entity e : level.getEntitiesOfClass(Entity.class, on, e -> !e.isSpectator())) {
                    e.teleportTo(e.getX(), floatY + 1.0, e.getZ());
                    e.fallDistance = 0;
                }
            }
            for (int x = b[0]; x <= b[1]; x++) {
                for (int z = b[2]; z <= b[3]; z++) {
                    boolean floatCell = hasFloat && z >= -11 && z <= -9;
                    for (int y = f; y <= top; y++) {
                        BlockState s;
                        if (y < f + L) {
                            s = Blocks.WATER.defaultBlockState();
                        } else if (floatCell && y == floatY) {
                            s = (x + z) % 2 == 0 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
                        } else {
                            s = Blocks.AIR.defaultBlockState();
                        }
                        BlockPos p = new BlockPos(x, y, z);
                        if (!level.getBlockState(p).equals(s)) {
                            level.setBlock(p, s, TideKit.FLAGS);
                        }
                    }
                }
            }
            boolean open = doorOpen(k, L);
            setDoor(level, BASIN_DOOR[k], open);
            if (k == 2) {
                setDoor(level, T_EXIT_DOOR, open);
            }
        }
    }

    private static void setDoor(ServerLevel level, BlockPos d, boolean open) {
        BlockState want = open ? Blocks.AIR.defaultBlockState() : ModBlocks.SEALED_GATE.get().defaultBlockState();
        boolean changed = false;
        for (BlockPos p : new BlockPos[] {d, d.above()}) {
            if (!level.getBlockState(p).is(want.getBlock())) {
                level.setBlock(p, want, 3);
                changed = true;
            }
        }
        if (changed) {
            level.playSound(null, d, TideBackSetup.LOCK.get(), SoundSource.BLOCKS, 1.2f, open ? 1.2f : 0.8f);
        }
    }

    // 前室の手前と、中の溜めの縁に泡吹き (謎解きを急かすほど多くはしない)
    private static void spawnGuards(ServerLevel level) {
        BlockPos[] spots = {new BlockPos(20, 78, -3), new BlockPos(42, 84, -10), new BlockPos(30, 79, -10)};
        for (BlockPos s : spots) {
            Mob m = TideFlats.spawn(level, TideMobSetup.BUBBLE_PUFFER.get(), s);
            if (m != null) {
                m.addTag(WATCH_TAG);
            }
        }
    }

    static void clear() {
        applied = false;
        flowTimer = 0;
        spawned = false;
        lastReset = -RESET_COOLDOWN;
    }

    // テスト用: 水位を直接決める
    public static void setLevels(ServerLevel level, int b, int m, int t) {
        int[] before = levels.clone();
        levels[0] = b;
        levels[1] = m;
        levels[2] = t;
        apply(level, before);
    }
}
