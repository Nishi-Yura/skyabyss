package jp.revati.skyabyss.story.ch5.warden;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 心臓の間の灯籠 (docs/design/36 の 6.3)。設計図 abyss_layers の印 beacon_0〜7 に魂の焚き火を置き、点いている / 消えているで見せる。
//   点ける   消えた焚き火を右クリックして、そばに 2 秒いる (だれでも)。続くのは番人の段で 40 / 30 / 20 秒
//   消える   時間が来るか、番人が吹き消す (snuff) か、三段目の闇の波 (allOut)
//   見える   litNear(pos): 点いた灯籠の 7 ブロック以内 (番人の姿が見える所)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssBeacons {
    public static final int COUNT = 8;
    public static final double SIGHT = 7.0;
    static final int LIGHT_TICKS = 40;
    static final int[] LAST_TICKS = {40 * 20, 30 * 20, 20 * 20};

    // 灯籠の番号 → 消える時刻 (gameTime)。無ければ消えている
    private static final Map<Integer, Long> LIT = new HashMap<>();
    // 点けている人 → (灯籠の番号, 終わる時刻)
    private static final Map<UUID, long[]> LIGHTING = new HashMap<>();
    // 今の番人の段 (1〜3)。灯籠の持ちが変わる
    static volatile int phase = 1;

    private AbyssBeacons() {
    }

    @Nullable
    public static BlockPos pos(int i) {
        BlockPos m = AbyssBuild.mark("beacon_" + i);
        return m;
    }

    // その段で点けられる灯籠か (悪夢の段 4 以降は番号 6 と 7 が点かない)
    public static boolean usable(int i) {
        return i < Nightmare.tier().lanterns();
    }

    public static boolean lit(int i) {
        return LIT.containsKey(i);
    }

    public static int litCount() {
        return LIT.size();
    }

    // 点いた灯籠の 7 以内か
    public static boolean litNear(BlockPos at) {
        for (int i : LIT.keySet()) {
            BlockPos p = pos(i);
            if (p != null && p.distSqr(at) <= SIGHT * SIGHT) {
                return true;
            }
        }
        return false;
    }

    // 一番近い灯籠の番号 (点いているものだけ / 全部)
    public static int nearest(BlockPos at, boolean litOnly) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < COUNT; i++) {
            BlockPos p = pos(i);
            if (p == null || (litOnly && !lit(i))) {
                continue;
            }
            double d = p.distSqr(at);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    public static void light(ServerLevel level, int i, long now) {
        // 悪夢の段の灯籠の持ち (普通の戦いは倍率 1)
        long life = Math.round(LAST_TICKS[Math.max(0, Math.min(2, phase - 1))] * Nightmare.tier().lanternLife());
        LIT.put(i, now + life);
        show(level, i, true);
        BlockPos p = pos(i);
        if (p != null) {
            level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1f, 0.6f);
        }
    }

    public static void snuff(ServerLevel level, int i) {
        if (LIT.remove(i) != null) {
            show(level, i, false);
            BlockPos p = pos(i);
            if (p != null) {
                level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.5f);
            }
        }
    }

    public static void allOut(ServerLevel level) {
        for (int i = 0; i < COUNT; i++) {
            snuff(level, i);
        }
    }

    // 戦いの始まりと終わりに: 全部消して、焚き火を置き直す
    public static void reset(ServerLevel level) {
        LIT.clear();
        LIGHTING.clear();
        phase = 1;
        for (int i = 0; i < COUNT; i++) {
            show(level, i, false);
        }
    }

    static void show(ServerLevel level, int i, boolean on) {
        BlockPos p = pos(i);
        if (p == null || !level.isLoaded(p)) {
            return;
        }
        BlockState st = Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, on);
        level.setBlock(p, st, 3);
    }

    @Nullable
    static Integer beaconAt(BlockPos clicked) {
        for (int i = 0; i < COUNT; i++) {
            BlockPos p = pos(i);
            if (p != null && p.equals(clicked)) {
                return i;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p)) {
            return;
        }
        Integer i = beaconAt(event.getPos());
        if (i == null) {
            return;
        }
        event.setCanceled(true);
        // 悪夢の段の灯籠の数 (段 4 以降は六つ。残りは点かない)
        if (!usable(i)) {
            Notice.hint(p, "message.skyabyss.abyss_nm.lantern_dark");
            return;
        }
        if (lit(i) || LIGHTING.containsKey(p.getUUID())) {
            return;
        }
        LIGHTING.put(p.getUUID(), new long[] {i, p.serverLevel().getGameTime() + LIGHT_TICKS});
        Notice.hint(p, "message.skyabyss.warden.lighting");
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        LIT.entrySet().removeIf(e -> {
            if (now >= e.getValue()) {
                show(level, e.getKey(), false);
                return true;
            }
            return false;
        });
        LIGHTING.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            int i = (int) e.getValue()[0];
            BlockPos b = pos(i);
            if (p == null || b == null || !p.isAlive() || p.level() != level || p.blockPosition().distSqr(b) > 3.5 * 3.5) {
                return true;
            }
            if (now >= e.getValue()[1]) {
                light(level, i, now);
                return true;
            }
            return false;
        });
    }

    // 自己テスト用: 時間の進みをじかに
    static void tickAt(ServerLevel level, long now) {
        LIT.entrySet().removeIf(e -> {
            if (now >= e.getValue()) {
                show(level, e.getKey(), false);
                return true;
            }
            return false;
        });
    }
}
