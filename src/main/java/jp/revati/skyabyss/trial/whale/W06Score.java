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

// W06 骨の楽譜 (智・解く・★。docs/design/29 の 3.1)。壁の譜 (色の並び) を、同じ順に肋骨 (右クリック) で叩き返す。
//   肋骨の頭の色・壁の譜は、そのグループにだけ見える (見かけの上書き)。同じ部屋に二つのグループがいても、譜は別々。
//   間違えたら最初から (損はない)。叩いた順は壁の「進み」の灯に残る。見返しの台 (書見台) を押すと、譜が 6 秒だけ出る。
//   譜の長さ 3・4・6 (数字)、見え方 (数字: 出しっぱなし / 叩き始めたら隠れる / 6 秒だけ)、肋骨の色の割り当て 3 通り (配置)。任意の目標: 間違えずに
public final class W06Score implements TrialCell {
    public static final int RIBS = 6;
    public static final int[] RIB_X = {26, 28, 30, 32, 34, 36};
    public static final int RIB_Z = 34;
    // 譜の壁 (北の壁 z 26): 目標の列と進みの列
    public static final int[] SLOT_X = {27, 29, 31, 33, 35, 37};
    public static final int SLOT_Z = 26;
    public static final int SLOT_Y_TARGET = Y + 6;
    public static final int SLOT_Y_PROGRESS = Y + 5;
    public static final BlockPos LECTERN = new BlockPos(31, Y + 1, 29);
    // 色の割り当て: 肋骨の位置 → 色の番号 (配置ごと)
    public static final int[][] RIB_COLOR = {{0, 1, 2, 3, 4, 5}, {5, 4, 3, 2, 1, 0}, {2, 0, 4, 1, 5, 3}};
    static final int[] LENGTH = {3, 4, 6};
    static final int FLASH_TICKS = 120;

    public static int length(Run run) {
        return jp.revati.skyabyss.abyss.Abyss.plusOne(run.mods, LENGTH[run.mods.number(0)]);
    }
    static final float[] PITCH = {0.7f, 0.8f, 0.9f, 1.1f, 1.25f, 1.45f};

    private static final BlockState[] CONCRETE = {Blocks.RED_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(),
            Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
            Blocks.MAGENTA_CONCRETE.defaultBlockState()};

    private final Def def = TrialCatalog.get("W06");
    private final Ports ports = new Ports(WhaleDeck.W06_GATE, false, WhaleDeck.W06_EXIT, WhaleDeck.W06_STONE, WhaleDeck.W06_PAD, WhaleDeck.REST_D0,
            WhaleDeck.W06.aabb(), List.of());

    public static BlockPos cap(int rib) {
        return new BlockPos(RIB_X[rib], Y + 4, RIB_Z);
    }

    public static BlockPos slotTarget(int k) {
        return new BlockPos(SLOT_X[k], SLOT_Y_TARGET, SLOT_Z);
    }

    public static BlockPos slotProgress(int k) {
        return new BlockPos(SLOT_X[k], SLOT_Y_PROGRESS, SLOT_Z);
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
        for (int r = 0; r < RIBS; r++) {
            for (int dy = 1; dy <= 4; dy++) {
                set.add(new BlockPos(RIB_X[r], Y + dy, RIB_Z));
            }
        }
        set.add(LECTERN);
        return set;
    }

    private static int[] sequence(Run run) {
        return run.get("seq", () -> {
            int n = length(run);
            Random rng = new Random(run.seed);
            int[] seq = new int[n];
            for (int k = 0; k < n; k++) {
                int v;
                do {
                    v = rng.nextInt(RIBS);
                } while (k >= 2 && v == seq[k - 1] && v == seq[k - 2]);
                seq[k] = v;
            }
            return seq;
        });
    }

    private static boolean visible(Run run, long now) {
        return switch (run.mods.number(1)) {
            case 0 -> true;
            case 1 -> run.getInt("progress") == 0 || now < run.getLong("shownUntil");
            default -> now < run.getLong("shownUntil");
        };
    }

    @Override
    public void arm(ServerLevel back, Run run) {
        sequence(run);
        run.put("progress", 0);
        run.put("shownUntil", back.getGameTime() + FLASH_TICKS);
        run.put("visible", true);
        if (run.cleared) {
            run.put("progress", length(run));
            GroupGates.open(back, run.group, WhaleDeck.W06_EXIT);
        }
    }

    @Override
    public void disarm(ServerLevel back, Run run) {
        // 出口の戸は、済んだグループには開けたまま (記録にある)。済んでいなければ閉じ直す
        if (!run.cleared) {
            GroupGates.release(back, run.group, WhaleDeck.W06_EXIT);
        }
    }

    @Override
    public void tick(ServerLevel back, Run run, List<ServerPlayer> inside, long now) {
        if (!run.data.containsKey("welcomed")) {
            run.put("welcomed", true);
            TrialFx.bar(inside, ModText.objective("message.skyabyss.trial.w06.start"));
        }
        boolean vis = visible(run, now);
        if (vis != (Boolean) run.data.getOrDefault("visible", true)) {
            run.put("visible", vis);
            TrialRuntime.refresh(back, run);
        }
    }

    @Override
    public boolean onUse(ServerLevel back, Run run, ServerPlayer player, BlockPos pos, boolean sneaking, long now) {
        if (pos.equals(LECTERN)) {
            run.put("shownUntil", now + FLASH_TICKS);
            run.put("visible", true);
            TrialFx.sound(back, run.group, pos, SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
            Notice.bar(player, ModText.hint("message.skyabyss.trial.w06.recall"));
            TrialRuntime.refresh(back, run);
            return true;
        }
        int rib = -1;
        for (int r = 0; r < RIBS; r++) {
            if (pos.getX() == RIB_X[r] && pos.getZ() == RIB_Z && pos.getY() >= Y + 1 && pos.getY() <= Y + 4) {
                rib = r;
            }
        }
        if (rib < 0) {
            return false;
        }
        TrialFx.sound(back, run.group, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, PITCH[rib]);
        TrialFx.particles(back, run.group, ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.1, 0.1, 0.1, rib / 5.0);
        if (run.cleared) {
            return true;
        }
        int[] seq = sequence(run);
        int progress = run.getInt("progress");
        if (rib == seq[progress]) {
            progress++;
            run.put("progress", progress);
            if (progress >= seq.length) {
                run.cleared = true;
                run.clearedAt = now;
                GroupGates.open(back, run.group, WhaleDeck.W06_EXIT);
                TrialFx.sound(back, run.group, WhaleDeck.W06_STONE, SoundEvents.BELL_RESONATE, 1.5f, 1.0f);
                TrialFx.bar(List.of(player), ModText.progress("message.skyabyss.trial.w06.solved"));
            }
        } else {
            run.put("progress", 0);
            run.put("mistakes", run.getInt("mistakes") + 1);
            if (run.mods.goal() == 0) {
                run.goalOk = false;
            }
            TrialFx.sound(back, run.group, pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.5f, 0.5f);
            Notice.bar(player, ModText.hint("message.skyabyss.trial.w06.again"));
        }
        TrialRuntime.refresh(back, run);
        return true;
    }

    @Override
    public void overlay(ServerLevel back, Run run, BiConsumer<BlockPos, BlockState> out) {
        int[] colors = RIB_COLOR[run.mods.layout()];
        for (int r = 0; r < RIBS; r++) {
            out.accept(cap(r), CONCRETE[colors[r]]);
        }
        int[] seq = sequence(run);
        long now = back.getGameTime();
        boolean vis = visible(run, now) || run.cleared;
        int progress = run.getInt("progress");
        BlockState dark = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState lit = Blocks.SEA_LANTERN.defaultBlockState();
        for (int k = 0; k < seq.length; k++) {
            out.accept(slotTarget(k), vis ? CONCRETE[colors[seq[k]]] : dark);
            if (k < progress) {
                out.accept(slotProgress(k), lit);
            }
        }
    }

    @Override
    public List<String> audit(ServerLevel back) {
        List<String> bad = new ArrayList<>();
        for (int l = 0; l < RIB_COLOR.length; l++) {
            Set<Integer> seen = new HashSet<>();
            for (int c : RIB_COLOR[l]) {
                seen.add(c);
            }
            if (seen.size() != RIBS) {
                bad.add("W06 layout " + l + ": rib colors are not a permutation");
            }
        }
        for (int r = 0; r < RIBS; r++) {
            if (!back.getBlockState(new BlockPos(RIB_X[r], Y + 2, RIB_Z)).is(Blocks.BONE_BLOCK)) {
                bad.add("W06 rib " + r + " missing");
            }
        }
        for (int k = 0; k < RIBS; k++) {
            if (back.getBlockState(slotTarget(k)).isAir() || back.getBlockState(slotProgress(k)).isAir()) {
                bad.add("W06 score slot " + k + " missing");
            }
        }
        if (!back.getBlockState(WhaleDeck.W06_STONE).is(ModBlocks.RESONANCE_STONE.get())) {
            bad.add("W06 stone missing");
        }
        return bad;
    }
}
