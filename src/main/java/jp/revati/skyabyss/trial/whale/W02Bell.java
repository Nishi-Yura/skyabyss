package jp.revati.skyabyss.trial.whale;

import static jp.revati.skyabyss.titan.dungeon.WhaleDeck.Y;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialFx;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// W02 鐘楼の綱渡り (歩・拍・★★。docs/design/29 の 3.1)。肋骨の梁を渡る縦の穴。梁は鐘の拍で「止まって」(乗れて)、「振れて」(通れなくなって) を繰り返す。
//   梁 = 封印の扉のブロックで、止まっているあいだはそのグループにだけ閉じ (骨の梁に見える)、振れているあいだは開く (消える)。
//   ほかのグループの梁は、そのグループの拍で動く (同じ部屋に同時にいても混ざらない)。
//   一人でも: 岩棚と中ほどの台の鐘を撞くと、10 秒のあいだ梁がみな止まる (留め。25 秒に一度)。複数なら、後ろの人が撞いて前の人の梁を止められる。
//   足を踏み外したら、直前の休み場へ戻される。向こうの岩棚に着けば済み (石は岩棚の端、戻りの光る床つき)。
// 修飾: 配置 3 (梁の並び)・数字 2 (周期・止まる長さ)・障害 2 (突風・こだま)・任意の目標 (一度も落ちずに)
public final class W02Bell implements TrialCell {
    public static final int BEAMS = 6;
    // 梁の北端の z (梁は 3×3)。間の z 41〜47 は中ほどの台
    public static final int[] BEAM_Z = {28, 33, 38, 48, 53, 58};
    // 配置ごとの梁の中心の x
    public static final int[][] CENTERS = {{13, 14, 12, 13, 12, 14}, {10, 12, 14, 15, 17, 16}, {16, 15, 13, 11, 10, 12}};
    public static final BlockPos BELL_A = new BlockPos(9, Y + 1, 22);
    public static final BlockPos BELL_M = new BlockPos(12, Y + 1, 43);
    // 周期 (tick)・止まる割合 (やさしい / 標準 / きびしい)
    static final int[] PERIOD = {140, 110, 85};
    static final double[] WINDOW = {0.80, 0.65, 0.50};
    static final int BELL_HOLD = 200;
    static final int BELL_COOLDOWN = 500;
    // 突風の間 (tick)・予告 (tick)
    static final int GUST_EVERY = 100;
    static final int GUST_WARN = 20;
    // 足を踏み外した高さ (梁の面 Y より下)
    static final double FALL_Y = Y - 1.5;

    private final Def def = TrialCatalog.get("W02");
    private final Ports ports = new Ports(WhaleDeck.W02_GATE, false, List.of(), WhaleDeck.W02_STONE, WhaleDeck.W02_PAD, WhaleDeck.REST_D0,
            WhaleDeck.W02.aabb(), WhaleDeck.rests().stream().filter(r -> r.id().startsWith("w02")).toList());

    // 梁 i の九つのブロック
    public static List<BlockPos> beam(int layout, int i) {
        List<BlockPos> out = new ArrayList<>();
        int cx = CENTERS[layout][i];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                out.add(new BlockPos(cx + dx, Y, BEAM_Z[i] + dz));
            }
        }
        return out;
    }

    // 三つの配置すべての梁のブロック (建てるとき。重なりは一つ)
    public static List<BlockPos> union() {
        Set<BlockPos> set = new LinkedHashSet<>();
        for (int l = 0; l < CENTERS.length; l++) {
            for (int i = 0; i < BEAMS; i++) {
                set.addAll(beam(l, i));
            }
        }
        return List.copyOf(set);
    }

    public static List<BlockPos> active(int layout) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < BEAMS; i++) {
            out.addAll(beam(layout, i));
        }
        return out;
    }

    // 配置に使わない梁のブロック (そのグループには消える)
    public static List<BlockPos> inactive(int layout) {
        Set<BlockPos> act = new LinkedHashSet<>(active(layout));
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!act.contains(p)) {
                out.add(p);
            }
        }
        return out;
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
        return Set.of(BELL_A, BELL_M);
    }

    // 梁 i が、いま止まっているか (乗れるか)
    static boolean solid(Run run, int i, long now) {
        if (now < run.getLong("bellUntil")) {
            return true;
        }
        Mods m = run.mods;
        int period = jp.revati.skyabyss.abyss.Abyss.shorter(m, PERIOD[m.number(0)]);
        double window = jp.revati.skyabyss.abyss.Abyss.narrower(m, WINDOW[m.number(1)]);
        if (m.hasHazard(1)) {
            window *= 0.75;
        }
        if (m.abyss()) {
            window = Math.max(0.35, window);
        }
        long t = Math.max(0, now - run.startTick);
        long phase = (t + (long) i * period / BEAMS) % period;
        return phase < window * period;
    }

    // 0 = 振れている (通れない)、1 = 止まっている、2 = 止まっているが、まもなく振れる (予告)
    static int state(Run run, int i, long now) {
        if (!solid(run, i, now)) {
            return 0;
        }
        return solid(run, i, now + 14) ? 1 : 2;
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        int layout = run.mods.layout();
        GroupGates.open(back, run.group, inactive(layout));
        int[] states = new int[BEAMS];
        long now = back.getGameTime();
        for (int i = 0; i < BEAMS; i++) {
            states[i] = state(run, i, now);
            GroupGates.set(back, run.group, beam(layout, i), states[i] == 0);
        }
        run.put("states", states);
        run.put("gustAt", now + 100L);
        run.put("gustDir", 1);
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        // 梁を元の既定 (閉じ) に戻す。ほかのグループの記録には触らない
        GroupGates.release(back, run.group, union());
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        int layout = run.mods.layout();
        int[] states = run.get("states", () -> new int[BEAMS]);
        boolean visual = false;
        for (int i = 0; i < BEAMS; i++) {
            int s = state(run, i, now);
            if (s != states[i]) {
                boolean openChanged = (s == 0) != (states[i] == 0);
                states[i] = s;
                if (openChanged) {
                    GroupGates.set(back, run.group, beam(layout, i), s == 0);
                    if (s != 0) {
                        BlockPos c = new BlockPos(CENTERS[layout][i], Y + 1, BEAM_Z[i] + 1);
                        TrialFx.sound(back, run.group, c, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5f, 0.6f + 0.1f * i);
                    }
                } else {
                    visual = true;
                }
            }
        }
        if (visual) {
            TrialRuntime.refresh(back, run);
        }
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w02.start"));
        }
        // 突風 (障害): 予告の粒 → 横へ押す
        if (run.mods.hasHazard(0)) {
            long gustAt = run.getLong("gustAt");
            int dir = run.getInt("gustDir");
            if (now >= gustAt - GUST_WARN && now < gustAt) {
                for (ServerPlayer p : inside) {
                    if (onBeams(p)) {
                        TrialFx.particles(back, run.group, ParticleTypes.CLOUD, p.getX() - dir * 1.2, p.getY() + 1, p.getZ(), 3, 0.1, 0.3, 0.3, 0.05 * dir);
                    }
                }
            } else if (now >= gustAt) {
                for (ServerPlayer p : inside) {
                    if (onBeams(p)) {
                        p.setDeltaMovement(p.getDeltaMovement().add(0.45 * dir, 0.12, 0));
                        p.hurtMarked = true;
                    }
                }
                run.put("gustAt", now + GUST_EVERY + new Random(run.seed ^ now).nextInt(30));
                run.put("gustDir", -dir);
            }
        }
        // 足を踏み外した: 直前の休み場へ
        for (ServerPlayer p : inside) {
            if (p.getY() < FALL_Y && p.getZ() >= 27 && p.getZ() < 62) {
                String cp = TitanBackTrials.checkpointOf(p);
                TitanBackTrials.returnToCheckpoint(p, cp.startsWith("w02") ? cp : WhaleDeck.REST_W02_A);
                if (p.getHealth() > 3f) {
                    p.hurt(p.damageSources().fall(), 2f);
                }
                run.put("falls", run.getInt("falls") + 1);
                if (run.mods.goal() == 0) {
                    run.goalOk = false;
                }
                Notice.bar(p, ModText.deny("message.skyabyss.trial.w02.fell"));
            }
        }
        // 向こうの岩棚に着いた
        if (!run.cleared) {
            for (ServerPlayer p : inside) {
                if (p.getZ() >= 62 && p.getY() >= Y + 0.9 && p.onGround()) {
                    run.cleared = true;
                    run.clearedAt = now;
                    TrialFx.sound(back, run.group, WhaleDeck.W02_STONE, SoundEvents.BELL_RESONATE, 1.5f, 0.9f);
                    TrialFx.bar(inside, ModText.progress("message.skyabyss.trial.w02.crossed"));
                    TrialRuntime.refresh(back, run);
                    break;
                }
            }
        }
    }

    private static boolean onBeams(ServerPlayer p) {
        return p.getZ() >= 27 && p.getZ() < 62 && p.getY() > Y;
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (!pos.equals(BELL_A) && !pos.equals(BELL_M)) {
            return false;
        }
        long until = run.getLong("bellUntil");
        long cool = run.getLong("bellCool");
        if (now < until) {
            Notice.bar(player, ModText.hint("message.skyabyss.trial.w02.bell_ringing"));
            return true;
        }
        if (now < cool) {
            Notice.bar(player, ModText.deny("message.skyabyss.trial.w02.bell_cooling", (int) Math.ceil((cool - now) / 20.0)));
            return true;
        }
        run.put("bellUntil", now + BELL_HOLD);
        run.put("bellCool", now + BELL_COOLDOWN);
        TrialFx.sound(back, run.group, pos, SoundEvents.BELL_BLOCK, 2.0f, 0.8f);
        TrialFx.particles(back, run.group, ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.5);
        Notice.bar(player, ModText.progress("message.skyabyss.trial.w02.bell_held", BELL_HOLD / 20));
        return true;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int layout = run.mods.layout();
        int[] states = run.get("states", () -> new int[BEAMS]);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState warn = Blocks.CALCITE.defaultBlockState();
        for (int i = 0; i < BEAMS; i++) {
            if (states[i] == 0) {
                continue;
            }
            for (BlockPos p : beam(layout, i)) {
                out.accept(p, states[i] == 2 ? warn : bone);
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (BlockPos p : union()) {
            if (!back.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                bad.add("W02 beam block missing at " + p);
                break;
            }
        }
        for (int l = 0; l < CENTERS.length; l++) {
            // 配置ごとに、隣の梁の中心が 2 以内に並ぶ (跳べる)・中ほどの台 (x 11〜15) と 3 以内
            int[] c = CENTERS[l];
            for (int i = 0; i + 1 < BEAMS; i++) {
                if (i + 1 == 3) {
                    if (Math.abs(c[i] - 13) > 3 || Math.abs(c[i + 1] - 13) > 3) {
                        bad.add("W02 layout " + l + ": beam next to the middle platform is too far");
                    }
                } else if (Math.abs(c[i] - c[i + 1]) > 2) {
                    bad.add("W02 layout " + l + ": beams " + i + " and " + (i + 1) + " are too far apart");
                }
            }
        }
        if (!back.getBlockState(WhaleDeck.W02_STONE).is(ModBlocks.RESONANCE_STONE.get())) {
            bad.add("W02 stone missing");
        }
        return bad;
    }

    // 文の部品 (板・図鑑から使う)
    public static Component hazardName(int h) {
        return Component.translatable("trial.skyabyss.hazard." + TrialCatalog.W02_HAZARDS.get(h));
    }
}
