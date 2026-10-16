package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 東の試練 鍛えの間 (13 の 4.2節): 色を読んで鍛え、火の鍵を作る。
//   炉の熱 0〜100 (塊の色: 黒 0〜19、赤 20〜44、橙 45〜69、黄 70〜89、白 90〜)。鞴の踏み板 (2 つ) を踏むと 1 回 +12、1 秒に −4 で下がる。
//   金床の刻み (3〜4 つの色) の順に、その色のときに打つ。違う色で打つとひび (2 回で塊が割れる。5 秒後に新しい塊)。白で打つとすぐ割れる。
//   打ち終えると火の鍵 (焼き入れ前) が持ち物に入る (熱 +20)。10 秒以内に槽へ入れて焼き入れる。その鍵で鍛え手の扉を開ける
//   灰被りが大炉に触れると、灰で熱が −30。打つ順は 4 通り (ワールドで初めてだけいちばんやさしい 赤→橙→黄)
// 部屋の座標は ForgeRoom。熱はメモリだけに持つ (保存しない。リセットで 0)
public final class Forge {
    // 部屋の道具の位置 (EAST の箱の中。床 y 80)
    public static final BlockPos FORGE = new BlockPos(26, 82, 22);
    public static final BlockPos BELLOWS_A = new BlockPos(22, 81, 20);
    public static final BlockPos BELLOWS_B = new BlockPos(30, 81, 20);
    public static final BlockPos ANVIL = new BlockPos(26, 81, 12);
    public static final BlockPos TROUGH_A = new BlockPos(32, 81, 13);
    public static final BlockPos TROUGH_B = new BlockPos(33, 81, 13);
    // 金床の刻み (4 つ。打つ回数が 3 のときは 4 つ目は黒石)。上の蝋燭が「次に打つ色」を指す
    public static final BlockPos[] MARKS = {new BlockPos(24, 82, 11), new BlockPos(25, 82, 11), new BlockPos(26, 82, 11), new BlockPos(27, 82, 11)};
    // 灰被りが歩いてくる炉の前
    public static final BlockPos FORGE_FRONT = new BlockPos(26, 81, 19);
    // 灰被りが湧く前室の奥
    public static final BlockPos ANTE_SPAWN = new BlockPos(26, 81, 3);
    public static final String DOUSER_TAG = "skyabyss_forge_douser";

    // 数 (4.2節)
    public static final float PRESS_HEAT = 12f;
    public static final float DECAY_PER_TICK = 4f / 20f;
    public static final float DOUSE_HEAT = 30f;
    public static final int BLOOM_RETURN_TICKS = 100;
    public static final int KEY_TICKS = 200;
    public static final float KEY_HEAT = 20f;
    public static final int PRESS_GAP = 6;
    public static final int DOOR_WARNING_TICKS = 100;
    // 帯の色 (0 黒 1 赤 2 橙 3 黄 4 白)
    public static final int BLACK = 0;
    public static final int RED = 1;
    public static final int ORANGE = 2;
    public static final int YELLOW = 3;
    public static final int WHITE = 4;
    // 打つ順 (4 通り。先頭がいちばんやさしい)
    public static final int[][] PATTERNS = {{RED, ORANGE, YELLOW}, {RED, YELLOW, ORANGE}, {ORANGE, RED, YELLOW}, {RED, ORANGE, YELLOW, RED}};

    public enum StrikeResult {
        NO_BLOOM, SLEEPING, FULL, HIT, DONE, CRACK, BROKEN
    }

    // ---------------------------------------------------------------- メモリ
    private static float heat;
    private static int hits;
    private static int cracks;
    private static long brokenUntil;
    private static int lastBand;
    private static final boolean[] WAS_PRESSED = new boolean[2];
    private static final long[] LAST_PRESS = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    private static boolean spawned;
    private static long lastThrall;
    // 扉: 0 閉じている、1 開いている途中、2 開いた (戦いの前の予告)、3 戦いが始まった
    private static int doorState;
    private static int doorStep;
    private static int doorTimer;
    private static final List<Consumer<ServerLevel>> DOOR_LISTENERS = new ArrayList<>();

    private Forge() {
    }

    static void clearMemory() {
        heat = 0;
        hits = 0;
        cracks = 0;
        brokenUntil = 0;
        lastBand = 0;
        WAS_PRESSED[0] = false;
        WAS_PRESSED[1] = false;
        spawned = false;
        doorState = 0;
        doorStep = 0;
        doorTimer = 0;
    }

    // E (鍛え手) が、扉が開いて 5 秒の予告が終わったときに戦いを始めるための受け口
    public static synchronized void onDoorOpened(Consumer<ServerLevel> listener) {
        DOOR_LISTENERS.add(listener);
    }

    public static float heat() {
        return heat;
    }

    public static void setHeat(float value) {
        heat = Math.max(0f, Math.min(EmberHeat.MAX, value));
    }

    public static int hits() {
        return hits;
    }

    public static int cracks() {
        return cracks;
    }

    public static int doorState() {
        return doorState;
    }

    public static boolean bloomPresent() {
        return brokenUntil == 0;
    }

    public static int bandOf(float h) {
        return h < 20 ? BLACK : h < 45 ? RED : h < 70 ? ORANGE : h < 90 ? YELLOW : WHITE;
    }

    public static int[] patternFor(boolean firstUsed, int seed) {
        return PATTERNS[firstUsed ? Math.floorMod(seed, PATTERNS.length) : 0];
    }

    // 金床の刻みの色の並び (部屋のブロックから読む。サーバーを再起動しても同じになる)
    public static int[] pattern(ServerLevel level) {
        int[] out = new int[MARKS.length];
        int n = 0;
        for (BlockPos p : MARKS) {
            int c = colorOf(level.getBlockState(p));
            if (c < 0) {
                break;
            }
            out[n++] = c;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    static int colorOf(BlockState s) {
        if (s.is(Blocks.RED_WOOL)) {
            return RED;
        }
        if (s.is(Blocks.ORANGE_WOOL)) {
            return ORANGE;
        }
        if (s.is(Blocks.YELLOW_WOOL)) {
            return YELLOW;
        }
        return -1;
    }

    static BlockState woolOf(int color) {
        return switch (color) {
            case RED -> Blocks.RED_WOOL.defaultBlockState();
            case ORANGE -> Blocks.ORANGE_WOOL.defaultBlockState();
            default -> Blocks.YELLOW_WOOL.defaultBlockState();
        };
    }

    // ---------------------------------------------------------------- 見た目の更新
    private static void showForge(ServerLevel level) {
        int stage = brokenUntil != 0 ? 5 : bandOf(heat);
        BlockState cur = level.getBlockState(FORGE);
        if (!cur.is(EmberTrialSetup.ASH_FORGE.get())) {
            return;
        }
        BlockState want = cur.setValue(ForgeBlocks.AshForgeBlock.STAGE, stage).setValue(ForgeBlocks.AshForgeBlock.CRACKED, cracks > 0 && brokenUntil == 0);
        if (want != cur) {
            level.setBlock(FORGE, want, Block.UPDATE_CLIENTS);
        }
    }

    // 刻みの蝋燭: 打った分は消した蝋燭、次に打つ色の上は火をつけた蝋燭
    static void showMarks(ServerLevel level) {
        int[] pat = pattern(level);
        for (int i = 0; i < pat.length; i++) {
            BlockPos candle = MARKS[i].above();
            if (i < hits) {
                level.setBlock(candle, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false), Block.UPDATE_CLIENTS);
            } else if (i == hits && brokenUntil == 0) {
                level.setBlock(candle, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(candle, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static int required(ServerLevel level) {
        int[] pat = pattern(level);
        return hits < pat.length ? pat[hits] : -1;
    }

    // ---------------------------------------------------------------- 熱の上がり下がり (毎 tick)
    // now は呼ぶ側の時計 (本番はゲーム時間、テストは仮の時計)
    public static void stepHeat(ServerLevel level, long now) {
        if (heat > 0) {
            heat = Math.max(0f, heat - DECAY_PER_TICK);
        }
        int band = bandOf(heat);
        if (band != lastBand) {
            lastBand = band;
            if (brokenUntil == 0 && band == required(level)) {
                level.playSound(null, ANVIL, EmberTrialSetup.ANVIL_TING.get(), SoundSource.BLOCKS, 0.8f, 1.5f);
            }
        }
        if (brokenUntil != 0 && now >= brokenUntil) {
            brokenUntil = 0;
            hits = 0;
            cracks = 0;
            showMarks(level);
        }
        showForge(level);
    }

    // 鞴を踏んだ (1 回 +12)
    public static void press(ServerLevel level, int index) {
        heat = Math.min(EmberHeat.MAX, heat + PRESS_HEAT);
        BlockPos at = index == 0 ? BELLOWS_A : BELLOWS_B;
        level.playSound(null, at, EmberTrialSetup.BELLOWS.get(), SoundSource.BLOCKS, 0.9f, 0.9f + level.random.nextFloat() * 0.2f);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, FORGE.getX() + 0.5, FORGE.getY() + 0.3, FORGE.getZ() - 0.4, 3, 0.2, 0.1, 0.05, 0.02);
        showForge(level);
    }

    public static void douse(ServerLevel level) {
        heat = Math.max(0f, heat - DOUSE_HEAT);
        level.playSound(null, FORGE, EmberTrialSetup.HEARTH_DOUSE.get(), SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, FORGE.getX() + 0.5, FORGE.getY() + 0.5, FORGE.getZ() - 0.3, 14, 0.4, 0.3, 0.1, 0.03);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.EAST)) {
            p.displayClientMessage(ModText.hint(EmberIds.LANG_EAST + "doused"), true);
        }
        showForge(level);
    }

    // ---------------------------------------------------------------- 大炉・金床・槽・扉
    public static void inspect(ServerLevel level, ServerPlayer player) {
        if (brokenUntil != 0) {
            EmberTrials.hint(player, EmberIds.LANG_EAST + "no_bloom");
        } else {
            EmberTrials.hint(player, EmberIds.LANG_EAST + "read_" + bandOf(heat));
        }
    }

    public static StrikeResult strike(ServerLevel level, ServerPlayer player) {
        if (EmberBackTrials.sleeping(level, EmberIds.EAST)) {
            EmberTrials.hint(player, EmberIds.LANG_EAST + "sleeping");
            return StrikeResult.SLEEPING;
        }
        if (brokenUntil != 0) {
            EmberTrials.hint(player, EmberIds.LANG_EAST + "no_bloom");
            return StrikeResult.NO_BLOOM;
        }
        EmberBackState state = EmberBackState.get(level);
        int[] pat = pattern(level);
        if (pat.length == 0) {
            return StrikeResult.NO_BLOOM;
        }
        boolean last = hits + 1 >= pat.length;
        boolean right = bandOf(heat) == pat[Math.min(hits, pat.length - 1)];
        if (right && last && !hasSpace(player)) {
            player.displayClientMessage(ModText.deny(EmberIds.LANG_EAST + "inventory_full"), true);
            return StrikeResult.FULL;
        }
        if (!state.firstForgeUsed()) {
            state.markFirstForgeUsed();
        }
        level.playSound(null, ANVIL, EmberTrialSetup.ANVIL_STRIKE.get(), SoundSource.BLOCKS, 1f, 1f);
        int band = bandOf(heat);
        if (band == WHITE) {
            breakBloom(level, true);
            return StrikeResult.BROKEN;
        }
        if (!right) {
            cracks++;
            if (cracks >= 2) {
                breakBloom(level, false);
                return StrikeResult.BROKEN;
            }
            level.playSound(null, ANVIL, EmberTrialSetup.BLOOM_CRACK.get(), SoundSource.BLOCKS, 0.8f, 1.2f);
            level.sendParticles(ParticleTypes.SMOKE, ANVIL.getX() + 0.5, ANVIL.getY() + 1.1, ANVIL.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.02);
            player.displayClientMessage(ModText.deny(EmberIds.LANG_EAST + "wrong_color"), true);
            showForge(level);
            return StrikeResult.CRACK;
        }
        hits++;
        level.sendParticles(ParticleTypes.LAVA, ANVIL.getX() + 0.5, ANVIL.getY() + 1.1, ANVIL.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.1);
        level.sendParticles(ParticleTypes.FLAME, ANVIL.getX() + 0.5, ANVIL.getY() + 1.1, ANVIL.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.05);
        if (hits >= pat.length) {
            finishKey(level, player);
            return StrikeResult.DONE;
        }
        showMarks(level);
        player.displayClientMessage(ModText.hint(EmberIds.LANG_EAST + "struck", hits, pat.length), true);
        return StrikeResult.HIT;
    }

    private static boolean hasSpace(ServerPlayer player) {
        Inventory inv = player.getInventory();
        return inv.getFreeSlot() >= 0;
    }

    private static void breakBloom(ServerLevel level, boolean tooHot) {
        hits = 0;
        cracks = 0;
        brokenUntil = level.getGameTime() + BLOOM_RETURN_TICKS;
        level.playSound(null, ANVIL, EmberTrialSetup.BLOOM_CRACK.get(), SoundSource.BLOCKS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.EXPLOSION, FORGE.getX() + 0.5, FORGE.getY() + 0.5, FORGE.getZ() - 0.2, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, FORGE.getX() + 0.5, FORGE.getY() + 0.5, FORGE.getZ() - 0.3, 10, 0.3, 0.3, 0.1, 0.1);
        for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.EAST)) {
            p.displayClientMessage(ModText.deny(EmberIds.LANG_EAST + (tooHot ? "bloom_melted" : "bloom_cracked")), true);
        }
        showForge(level);
        showMarks(level);
    }

    // 打ち終えた: 焼き入れ前の鍵が持ち物に入る。塊は 5 秒で新しいものに替わる
    private static void finishKey(ServerLevel level, ServerPlayer player) {
        ItemStack key = new ItemStack(EmberTrialSetup.FIRE_KEY_HOT.get());
        FireKeyItem.stamp(key, level.getGameTime());
        player.getInventory().add(key);
        EmberHeat.add(player, KEY_HEAT, EmberHeat.Reason.FIRE_KEY);
        hits = 0;
        cracks = 0;
        brokenUntil = level.getGameTime() + BLOOM_RETURN_TICKS;
        level.playSound(null, ANVIL, EmberTrialSetup.ANVIL_TING.get(), SoundSource.BLOCKS, 1.2f, 0.7f);
        player.sendSystemMessage(ModText.lore(EmberIds.LANG_EAST + "key_forged"));
        showForge(level);
        showMarks(level);
        // 鍵を持つ人に火の粉蛾が寄ってくる (D の誘い)
        int cap = EmberMoth.roomCap(Math.max(1, EmberTrials.playersIn(level, EmberBackLayout.EAST).size()));
        for (int i = 0; i < 2 && EmberMoth.countIn(level, EmberBackLayout.EAST.aabb()) < cap; i++) {
            EmberTrials.spawn(level, EmberMobSetup.EMBER_MOTH.get(), ANTE_SPAWN.getX() + i, ANTE_SPAWN.getY() + 2, ANTE_SPAWN.getZ());
        }
    }

    // 焼き入れ: 焼き入れ前の鍵が、焼き入れた鍵になる
    public static boolean quench(ServerLevel level, ServerPlayer player, InteractionHand hand, BlockPos at) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(EmberTrialSetup.FIRE_KEY_HOT.get())) {
            return false;
        }
        player.setItemInHand(hand, new ItemStack(EmberTrialSetup.FIRE_KEY.get()));
        level.playSound(null, at, EmberTrialSetup.QUENCH.get(), SoundSource.BLOCKS, 1.2f, 1f);
        level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 18, 0.3, 0.2, 0.3, 0.05);
        player.sendSystemMessage(ModText.lore(EmberIds.LANG_EAST + "key_quenched"));
        return true;
    }

    // 焼き入れた鍵を扉に差す。開け始めたか
    public static boolean openDoor(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (doorState != 0 || !held.is(EmberTrialSetup.FIRE_KEY.get())) {
            return false;
        }
        held.shrink(1);
        startDoor(level);
        player.sendSystemMessage(ModText.lore(EmberIds.LANG_EAST + "door_kindled"));
        return true;
    }

    static void startDoor(ServerLevel level) {
        doorState = 1;
        doorStep = 0;
        doorTimer = 0;
    }

    // 扉の 3 枚の壁を、奥へ向かって一枚ずつ灯して開ける。そのあと 5 秒の予告
    private static void tickDoor(ServerLevel level) {
        EmberBackLayout.Box d = EmberBackLayout.SMITH_DOOR;
        if (doorState == 1) {
            if (doorTimer++ % 10 != 0) {
                return;
            }
            int step = doorStep++;
            if (step > 0) {
                fillLayer(level, d.minZ() + step - 1, Blocks.AIR.defaultBlockState());
            }
            if (step < 3) {
                fillLayer(level, d.minZ() + step, Blocks.SHROOMLIGHT.defaultBlockState());
                level.playSound(null, d.minX() + 1, d.minY() + 1, d.minZ() + step, EmberTrialSetup.DOOR_KINDLE.get(), SoundSource.BLOCKS, 1.5f,
                        0.8f + 0.15f * step);
            } else {
                doorState = 2;
                doorTimer = DOOR_WARNING_TICKS;
                for (ServerPlayer p : EmberTrials.playersIn(level, EmberBackLayout.EAST)) {
                    p.sendSystemMessage(ModText.battle(EmberIds.LANG_EAST + "door_open"));
                }
            }
        } else if (doorState == 2 && --doorTimer <= 0) {
            doorState = 3;
            List<Consumer<ServerLevel>> ls;
            synchronized (Forge.class) {
                ls = new ArrayList<>(DOOR_LISTENERS);
            }
            ls.forEach(l -> l.accept(level));
        }
    }

    static void fillLayer(ServerLevel level, int z, BlockState state) {
        EmberBackLayout.Box d = EmberBackLayout.SMITH_DOOR;
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int y = d.minY(); y <= d.maxY(); y++) {
                level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (doorState == 1 || doorState == 2) {
            tickDoor(level);
        }
        List<ServerPlayer> in = EmberTrials.playersIn(level, EmberBackLayout.EAST);
        if (in.isEmpty() && heat <= 0 && brokenUntil == 0) {
            return;
        }
        if (!EmberBackTrials.sleeping(level, EmberIds.EAST)) {
            stepHeat(level, now);
            pollBellows(level, now);
        }
        if (in.isEmpty()) {
            return;
        }
        if (now % 10 == 0) {
            keys(level, in, now);
        }
        if (now % 10 == 5 && !EmberBackTrials.sleeping(level, EmberIds.EAST)) {
            dousers(level);
        }
        if (now % 20 == 0 && !EmberBackTrials.sleeping(level, EmberIds.EAST) && doorState == 0) {
            keepThralls(level, in, now);
        }
    }

    private static boolean pressedNow(ServerLevel level, BlockPos pos) {
        AABB plate = new AABB(pos.getX() + 0.05, pos.getY() + 0.1, pos.getZ() + 0.05, pos.getX() + 0.95, pos.getY() + 0.5, pos.getZ() + 0.95);
        return !level.getEntitiesOfClass(LivingEntity.class, plate, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private static void pollBellows(ServerLevel level, long now) {
        BlockPos[] plates = {BELLOWS_A, BELLOWS_B};
        for (int i = 0; i < 2; i++) {
            boolean pressed = pressedNow(level, plates[i]);
            if (pressed && !WAS_PRESSED[i] && now - LAST_PRESS[i] >= PRESS_GAP) {
                LAST_PRESS[i] = now;
                press(level, i);
            }
            if (pressed != WAS_PRESSED[i]) {
                BlockState s = level.getBlockState(plates[i]);
                if (s.is(EmberTrialSetup.FORGE_BELLOWS.get())) {
                    level.setBlock(plates[i], s.setValue(ForgeBlocks.BellowsBlock.PRESSED, pressed), Block.UPDATE_ALL);
                }
            }
            WAS_PRESSED[i] = pressed;
        }
    }

    // 焼き入れ前の鍵は 10 秒で割れる。持つ人は光る
    private static void keys(ServerLevel level, List<ServerPlayer> in, long now) {
        for (ServerPlayer p : in) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.is(EmberTrialSetup.FIRE_KEY_HOT.get())) {
                    continue;
                }
                if (now - FireKeyItem.forgedAt(s) > KEY_TICKS) {
                    inv.setItem(i, ItemStack.EMPTY);
                    level.playSound(null, p.blockPosition(), EmberTrialSetup.BLOOM_CRACK.get(), SoundSource.PLAYERS, 1f, 1.3f);
                    p.sendSystemMessage(ModText.deny(EmberIds.LANG_EAST + "key_cracked"));
                } else {
                    p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                }
            }
        }
    }

    private static List<AshThrall> thralls(ServerLevel level) {
        return level.getEntitiesOfClass(AshThrall.class, EmberBackLayout.EAST.aabb(), t -> t.isAlive() && t.getTags().contains(DOUSER_TAG));
    }

    // 灰被りは炉へ向かい、触れる所まで来たら灰をかけて熱を下げる。人が近ければそちらと戦う
    private static void dousers(ServerLevel level) {
        Vec3 front = Vec3.atBottomCenterOf(FORGE_FRONT);
        for (AshThrall t : thralls(level)) {
            if (t.distanceToSqr(Vec3.atCenterOf(FORGE)) < 4.5 * 4.5) {
                t.removeTag(DOUSER_TAG);
                douse(level);
                continue;
            }
            net.minecraft.world.entity.LivingEntity target = t.getTarget();
            if (target == null || t.distanceToSqr(target) > 36) {
                t.setTarget(null);
                t.getNavigation().moveTo(front.x, front.y, front.z, 1.0);
            }
        }
    }

    // 灰被りの湧き: 最初に人が入ったとき 2 体 (+1 体/人)。いなくなって 40 秒たったら 1 体足す
    private static void keepThralls(ServerLevel level, List<ServerPlayer> in, long now) {
        int n = Math.max(1, Math.min(4, in.size()));
        if (!spawned) {
            spawned = true;
            lastThrall = now;
            for (int i = 0; i < 1 + n; i++) {
                spawnThrall(level, i);
            }
            return;
        }
        if (!thralls(level).isEmpty() || !level.getEntitiesOfClass(AshThrall.class, EmberBackLayout.EAST.aabb(), e -> e.isAlive()).isEmpty()) {
            lastThrall = now;
        } else if (now - lastThrall >= 800) {
            lastThrall = now;
            spawnThrall(level, 0);
        }
    }

    private static void spawnThrall(ServerLevel level, int i) {
        EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), ANTE_SPAWN.getX() - 3 + i * 3, ANTE_SPAWN.getY(), ANTE_SPAWN.getZ(), DOUSER_TAG);
    }

    // ---------------------------------------------------------------- リセット (EmberBackRooms.onReset("east", ...))
    public static void reset(ServerLevel level, boolean sleeping) {
        EmberTrials.clearMobs(level, EmberBackLayout.EAST);
        EmberTrials.stripAll(level, EmberTrialSetup.FIRE_KEY_HOT.get(), EmberTrialSetup.FIRE_KEY.get());
        clearMemory();
        EmberBackState state = EmberBackState.get(level);
        ForgeRoom.buildInner(level, patternFor(state.firstForgeUsed(), state.seed()));
        doorState = 0;
    }
}
