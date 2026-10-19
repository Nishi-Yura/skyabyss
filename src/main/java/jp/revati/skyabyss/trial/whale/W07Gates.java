package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// W07 三つの水門 (智・合わせる・★★。docs/design/29 の 3.1)。広間の離れた四つの取っ手のうち三つが生きている。取っ手を引く (右クリック) か、
// 取っ手の台に立つと、その水門が「猶予」のあいだだけ開く。三つの水門が同時に開いたまま「保つ」と、奥の小部屋の戸が開く。
//   一人: 取っ手を順に回り、猶予のうちに三つ目を引く。重りの石は二つ (しゃがんで取っ手を押す = 載せる / 外す) で、載せた水門は開いたまま留まる。
//   複数: 取っ手ごとに一人が立つ。声をかけ合う。
// 修飾: 配置 3 (どの三つが生きているか)・数字 2 (猶予 14・11・8 秒 / 保つ 1・2・3 秒)・障害 (水位: 一つの取っ手が 4 秒だけ水没して効かない)・任意の目標 (一度も猶予を切らさずに)
public final class W07Gates implements TrialCell {
    public static final int[] PAD_X = {-16, 4, -16, 4};
    public static final int[] PAD_Z = {88, 88, 106, 106};
    public static final int[][] LIVE = {{0, 1, 2}, {1, 2, 3}, {0, 2, 3}};
    // 猶予 (tick)・保つ時間 (tick) (やさしい / 標準 / きびしい)
    static final int[] GRACE = {280, 220, 160};
    static final int[] HOLD = {20, 40, 60};
    static final int WEIGHTS = 2;
    static final int FLOOD_EVERY = 240;
    static final int FLOOD_LEN = 80;
    static final int FLOOD_WARN = 20;
    // 出口の戸の上の三つの灯
    public static final int[] LAMP_X = {-7, -6, -5};
    public static final int LAMP_Y = Y + 6;
    public static final int LAMP_Z = 108;

    private final Def def = TrialCatalog.get("W07");
    private final Ports ports = new Ports(WhaleDeck.W07_GATE, false, WhaleDeck.W07_EXIT, WhaleDeck.W07_STONE, WhaleDeck.W07_PAD, WhaleDeck.REST_S0,
            WhaleDeck.W07.aabb(), List.of());

    public static BlockPos handle(int pad) {
        return new BlockPos(PAD_X[pad], Y, PAD_Z[pad]);
    }

    public static BlockPos lamp(int i) {
        return new BlockPos(LAMP_X[i], LAMP_Y, LAMP_Z);
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public Ports ports() {
        return ports;
    }

    @Override
    public Set<BlockPos> interactive() {
        Set<BlockPos> set = new HashSet<>();
        for (int k = 0; k < 4; k++) {
            set.add(handle(k));
        }
        return set;
    }

    private static boolean live(Run run, int pad) {
        for (int p : LIVE[run.mods.layout()]) {
            if (p == pad) {
                return true;
            }
        }
        return false;
    }

    private static boolean flooded(Run run, int pad, long now) {
        return run.getInt("floodPad") == pad + 1 && now < run.getLong("floodUntil");
    }

    private static boolean standing(List<ServerPlayer> inside, int pad) {
        for (ServerPlayer p : inside) {
            if (p.getX() >= PAD_X[pad] - 1 && p.getX() < PAD_X[pad] + 2 && p.getZ() >= PAD_Z[pad] - 1 && p.getZ() < PAD_Z[pad] + 2 && p.getY() >= Y + 0.9
                    && p.getY() < Y + 2.5) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpen(Run run, int pad, long now) {
        long[] openUntil = run.get("openUntil", () -> new long[4]);
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        return live(run, pad) && !flooded(run, pad, now) && (weight[pad] || now < openUntil[pad]);
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        run.put("openUntil", new long[4]);
        run.put("weight", new boolean[4]);
        run.put("weightsLeft", WEIGHTS);
        run.put("prev", new boolean[4]);
        run.put("view", -1);
        run.put("nextFlood", back.getGameTime() + 200L);
        if (run.cleared) {
            GroupGates.open(back, run.group, WhaleDeck.W07_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W07_EXIT);
        }
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w07.start", WEIGHTS));
        }
        long[] openUntil = run.get("openUntil", () -> new long[4]);
        boolean[] prev = run.get("prev", () -> new boolean[4]);
        int grace = GRACE[run.mods.number(0)];
        int hold = HOLD[run.mods.number(1)];
        // 水位 (障害): 生きている取っ手の一つが、4 秒のあいだ水没して効かない。予告の粒が 1 秒前から
        if (run.mods.hasHazard(0) && !run.cleared) {
            long next = run.getLong("nextFlood");
            Random rng = new Random(run.seed ^ next);
            int target = LIVE[run.mods.layout()][rng.nextInt(3)];
            if (now >= next - FLOOD_WARN && now < next) {
                TrialFx.particles(back, run.group, ParticleTypes.SPLASH, PAD_X[target] + 0.5, Y + 1.2, PAD_Z[target] + 0.5, 8, 1.0, 0.2, 1.0, 0.1);
            } else if (now >= next) {
                run.put("floodPad", target + 1);
                run.put("floodUntil", now + FLOOD_LEN);
                run.put("nextFlood", now + FLOOD_EVERY);
                TrialFx.sound(back, run.group, handle(target), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.5f, 0.8f);
            }
        }
        int open = 0;
        int mask = 0;
        for (int k = 0; k < 4; k++) {
            if (live(run, k) && !flooded(run, k, now) && standing(inside, k)) {
                openUntil[k] = now + grace;
            }
            boolean o = isOpen(run, k, now);
            if (prev[k] && !o && !run.cleared) {
                run.put("lapses", run.getInt("lapses") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
                TrialFx.sound(back, run.group, handle(k), SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 0.6f);
            }
            prev[k] = o;
            if (o) {
                open++;
                mask |= 1 << k;
            }
            if (flooded(run, k, now)) {
                mask |= 1 << (k + 4);
            }
        }
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        for (int k = 0; k < 4; k++) {
            if (weight[k]) {
                mask |= 1 << (k + 8);
            }
        }
        if (run.cleared) {
            mask |= 1 << 12;
        }
        if (mask != run.getInt("view")) {
            run.put("view", mask);
            TrialRuntime.refresh(back, run);
        }
        if (!run.cleared) {
            if (open >= 3) {
                long since = run.getLong("allSince");
                if (since == 0) {
                    since = now;
                    run.put("allSince", now);
                }
                if (now - since >= hold) {
                    run.cleared = true;
                    run.clearedAt = now;
                    GroupGates.open(back, run.group, WhaleDeck.W07_EXIT);
                    TrialFx.sound(back, run.group, WhaleDeck.W07_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.8f);
                    TrialFx.sound(back, run.group, handle(1), SoundEvents.PISTON_EXTEND, 1.2f, 0.6f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w07.opened"));
                    TrialRuntime.refresh(back, run);
                }
            } else {
                run.put("allSince", 0L);
            }
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        int k = -1;
        for (int i = 0; i < 4; i++) {
            if (pos.equals(handle(i))) {
                k = i;
            }
        }
        if (k < 0) {
            return false;
        }
        if (!live(run, k)) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.w07.dormant"));
            return true;
        }
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        long[] openUntil = run.get("openUntil", () -> new long[4]);
        if (flooded(run, k, now)) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.w07.flooded"));
            return true;
        }
        if (sneaking) {
            int left = run.getInt("weightsLeft");
            if (weight[k]) {
                weight[k] = false;
                run.put("weightsLeft", left + 1);
                Notice.bar(player, ModText.hint("message.skyabyss.trial.w07.weight_off", left + 1));
            } else if (left > 0) {
                weight[k] = true;
                run.put("weightsLeft", left - 1);
                TrialFx.sound(back, run.group, pos, SoundEvents.ANVIL_PLACE, 0.8f, 1.2f);
                Notice.bar(player, ModText.hint("message.skyabyss.trial.w07.weight_on", left - 1));
            } else {
                Notice.bar(player, ModText.deny("message.skyabyss.trial.w07.no_weight"));
            }
            return true;
        }
        openUntil[k] = now + GRACE[run.mods.number(0)];
        TrialFx.sound(back, run.group, pos, SoundEvents.IRON_DOOR_OPEN, 1f, 0.7f);
        TrialFx.particles(back, run.group, ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.5, 0.2, 0.5, 0.1);
        Notice.bar(player, ModText.progress("message.skyabyss.trial.w07.pulled", GRACE[run.mods.number(0)] / 20));
        return true;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        long now = back.getGameTime();
        boolean[] weight = run.get("weight", () -> new boolean[4]);
        int open = 0;
        for (int k = 0; k < 4; k++) {
            if (!live(run, k)) {
                continue;
            }
            boolean o = isOpen(run, k, now);
            if (o) {
                open++;
            }
            BlockState s;
            if (flooded(run, k, now)) {
                s = Blocks.BLUE_ICE.defaultBlockState();
            } else if (weight[k]) {
                s = o ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
            } else {
                s = o ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState();
            }
            out.accept(handle(k), s);
        }
        int lit = run.cleared ? 3 : Math.min(3, open);
        for (int i = 0; i < lit; i++) {
            out.accept(lamp(i), Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < LIVE.length; l++) {
            Set<Integer> s = new HashSet<>();
            for (int p : LIVE[l]) {
                s.add(p);
            }
            if (s.size() != 3) {
                bad.add("W07 layout " + l + ": need three distinct live handles");
            }
        }
        // 取っ手は互いに 12 マス以上離れる (一人が二つの台に同時に立てない)
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                if (Math.hypot(PAD_X[a] - PAD_X[b], PAD_Z[a] - PAD_Z[b]) < 12) {
                    bad.add("W07 handles " + a + " and " + b + " are too close");
                }
            }
        }
        for (int k = 0; k < 4; k++) {
            if (back.getBlockState(handle(k)).isAir()) {
                bad.add("W07 handle " + k + " missing");
            }
        }
        if (!back.getBlockState(WhaleDeck.W07_STONE).is(ModBlocks.RESONANCE_STONE.get())) {
            bad.add("W07 stone missing");
        }
        return bad;
    }
}
