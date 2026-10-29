package jp.revati.skyabyss.story.ch6.field;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jp.revati.skyabyss.story.ch6.MoonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

// 光の満ち引き (docs/design/46 の 4.5)。野だけの時計 (世界で一つ。月の一日の位相とは別)。
//   一巡り 24 秒 = 満ち 10 秒 → 移り 2 秒 → 引き 10 秒 → 移り 2 秒。満月の位相では満ち 14 秒・引き 6 秒。
//   満ちのあいだは光の石が足場、引きのあいだは影の石が足場。満ちと引きの終わりの 1 秒は瞬く (予告)。移りの 2 秒はどちらも透ける。
//   主の段 3 は満ち引きが 2 倍の速さ (fast)。
//   一瞬止め (MomentStop) が石を止めると、その石は 2 秒のあいだ止めたときの状態のまま (hold)。
public final class FieldTide {
    public enum Tide {
        BRIGHT, TO_DIM, DIM, TO_BRIGHT
    }

    public static final int CYCLE = 480;
    public static final int TURN = 40;
    public static final int BLINK = 20;
    private static long clock;
    private static boolean fast;
    private static Tide last;
    // 止めた石 -> {止め終わる tick (ゲームの時刻), 止めたときの状態}
    private static final Map<BlockPos, long[]> HOLDS = new HashMap<>();

    private FieldTide() {
    }

    static int bright(ServerLevel level) {
        return MoonClock.phase(level) == 2 ? 280 : 200;
    }

    // 一巡りの中の位置 (0〜479)
    public static int pos() {
        return (int) Math.floorMod(clock, (long) CYCLE);
    }

    public static Tide tide(ServerLevel level) {
        int b = bright(level);
        int p = pos();
        if (p < b) {
            return Tide.BRIGHT;
        }
        if (p < b + TURN) {
            return Tide.TO_DIM;
        }
        if (p < CYCLE - TURN) {
            return Tide.DIM;
        }
        return Tide.TO_BRIGHT;
    }

    // 今の満ち引きの残りの tick (次の区切りまで)
    public static int left(ServerLevel level) {
        int b = bright(level);
        int p = pos();
        int[] ends = {b, b + TURN, CYCLE - TURN, CYCLE};
        for (int e : ends) {
            if (p < e) {
                return e - p;
            }
        }
        return 0;
    }

    // 敵の振る舞いの明るさ: 満ちと、満ちから引きへの移りは「明るい」。野の外 (や野がまだ無い) では明るい
    public static boolean lit(ServerLevel level, BlockPos at) {
        if (level.dimension() != jp.revati.skyabyss.story.ch6.MoonWorld.LEVEL || !jp.revati.skyabyss.story.ch6.MoonWorld.fieldBuilt(level.getServer())
                || !MoonField.inside(at)) {
            return true;
        }
        Tide t = tide(level);
        return t == Tide.BRIGHT || t == Tide.TO_DIM;
    }

    // 石のあるべき状態 (止めていなければ)
    public static int stateFor(ServerLevel level, boolean shade) {
        Tide t = tide(level);
        boolean on = shade ? t == Tide.DIM : t == Tide.BRIGHT;
        if (!on) {
            return 0;
        }
        return left(level) <= BLINK && (level.getGameTime() / 3) % 2 == 0 ? 2 : 1;
    }

    public static boolean fast() {
        return fast;
    }

    public static void setFast(boolean on) {
        fast = on;
    }

    // 開発・自己テスト: 満ち引きの頭へ (BRIGHT / TO_DIM / DIM / TO_BRIGHT) と、そこからの tick
    public static void setTo(ServerLevel level, Tide t, int after) {
        int b = bright(level);
        int start = switch (t) {
            case BRIGHT -> 0;
            case TO_DIM -> b;
            case DIM -> b + TURN;
            case TO_BRIGHT -> CYCLE - TURN;
        };
        clock = start + after;
        HOLDS.clear();
        refresh(level, true);
    }

    // 止める (一瞬止め): pos の石を until (ゲームの時刻) まで今の状態のまま
    public static boolean hold(ServerLevel level, BlockPos pos, long until) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof TideStoneBlock)) {
            return false;
        }
        int s = st.getValue(TideStoneBlock.STATE);
        HOLDS.put(pos.immutable(), new long[] {until, s == 2 ? 1 : s});
        return true;
    }

    public static boolean held(BlockPos pos) {
        return HOLDS.containsKey(pos);
    }

    // FieldTicker から毎 tick
    static void tick(ServerLevel level) {
        clock += fast ? 2 : 1;
        Tide now = tide(level);
        if (now != last) {
            if (last != null) {
                turned(level, now);
            }
            last = now;
        }
        long gt = level.getGameTime();
        for (Iterator<Map.Entry<BlockPos, long[]>> it = HOLDS.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue()[0] <= gt) {
                it.remove();
            }
        }
        if (gt % 2 == 0) {
            refresh(level, false);
        }
    }

    // 石を今の満ち引きに合わせる (読み込まれている所だけ)
    static void refresh(ServerLevel level, boolean force) {
        for (MoonField.Stone s : MoonField.stones()) {
            BlockPos p = s.pos();
            if (!force && !level.isLoaded(p)) {
                continue;
            }
            long[] h = HOLDS.get(p);
            int want = h != null ? (int) h[1] : stateFor(level, s.shade());
            BlockState cur = level.getBlockState(p);
            BlockState next = FieldBlocks.TIDE_STONE.get().defaultBlockState().setValue(TideStoneBlock.SHADE, s.shade()).setValue(TideStoneBlock.STATE, want);
            if (cur != next) {
                level.setBlock(p, next, 2 | 16);
            }
        }
    }

    static void placeAll(ServerLevel level) {
        refresh(level, true);
    }

    // 満ち引きが変わった: 野の中の人に音 (満ちは高く澄んだ音、引きは低い音)
    private static void turned(ServerLevel level, Tide now) {
        for (ServerPlayer p : level.players()) {
            if (!MoonField.inside(p.blockPosition())) {
                continue;
            }
            switch (now) {
                case BRIGHT -> p.playNotifySound(FieldBlocks.TIDE_BRIGHT.get(), SoundSource.AMBIENT, 0.8f, 1.0f);
                case DIM -> p.playNotifySound(FieldBlocks.TIDE_DIM.get(), SoundSource.AMBIENT, 0.8f, 1.0f);
                default -> p.playNotifySound(FieldBlocks.TIDE_TURN.get(), SoundSource.AMBIENT, 0.5f, 1.0f);
            }
        }
    }
}
