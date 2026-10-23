package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch5.warden.WardenArena;
import jp.revati.skyabyss.town.TownData;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 五章の深淵の中の段 (docs/design/36 の A7)。main_abyss の段 gate・descend・heart を進める (warden は WardenArena)。
//   門       都の大坑道の底の格子 (印 abyss_gate の北 1 の列、東西 3・高さ 5)。段 gate 以降の人が鍵を持って 3 以内に来ると格子が溶ける
//            (TownData.abyssGateOpen。開いたまま。都を建て直して格子が戻っていたら、人が近くに来たとき溶かし直す)
//   くぐる   開いた門の列に入ると深淵の着く所へ。合図 ch5_abyss_enter。だれでも何度でも (調べるために潜る)
//   戻る     骨の野営の祠の白い磁石石 (印 exit の磁石石か鎖) を右クリックすると、光の中で底の境の門の前へ戻る (AbyssReturn)
//   底       段 descend の人が心臓の間に入ると合図 abyss_heart_room
//   心臓     番人が倒れたあと、段 heart の人が台 (印 heart のまわり 2) を右クリックすると、三つの言葉 (錨の真実) と合図 abyss_heart_touch
//   確かめ   SELFTEST ch5 entrance (門の開き・列・合図) と abyss warden
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssGate {
    static final String SIGNAL_ENTER = "ch5_abyss_enter";
    static final String SIGNAL_HEART_ROOM = "abyss_heart_room";
    static final String SIGNAL_HEART_TOUCH = "abyss_heart_touch";
    static final double OPEN_NEAR = 3.5;
    static final int HEART_LINE_GAP = 70;

    private AbyssGate() {
    }

    // 格子の列の真ん中の印 (新しい都の設計図だけが持つ。古い都は枠の柱から向きを求める)
    static final String BARS = "abyss_gate_bars";
    // 格子の列の半分の幅・高さの上限 (溶かすときに見る範囲。都は回して建つので、列の向きは都ごとに違う)
    static final int MAX_HALF = 4;
    static final int MAX_H = 8;

    // 門の向き: 門の印 (立つ所) から格子の列への向きと、列の半分の幅。2026-10-23 の試遊「鉄格子全部消えなかった」:
    // 前は列を印の北 1 と決め打ちしていたので、都が回って建つと 3 列のうち 1 列しか溶けなかった
    record Facing(Direction toward, int half) {
        BlockPos row(BlockPos gate) {
            return gate.relative(toward);
        }
    }

    private static BlockPos facingFor;
    private static Facing facingCache;

    static Facing facing(MinecraftServer server, ServerLevel level, BlockPos gate) {
        if (gate.equals(facingFor) && facingCache != null) {
            return facingCache;
        }
        Facing f = detect(TownGuests.mark(server, BARS), level, gate);
        if (f != null) {
            facingFor = gate;
            facingCache = f;
            return f;
        }
        return new Facing(Direction.NORTH, 1);
    }

    // 印か枠の柱 (強化深層岩) か格子から向きを決める。決まらなければ null (読み込まれていない)
    @Nullable
    static Facing detect(@Nullable BlockPos barsMark, ServerLevel level, BlockPos gate) {
        Direction byMark = null;
        if (barsMark != null) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (gate.relative(d).getX() == barsMark.getX() && gate.relative(d).getZ() == barsMark.getZ()) {
                    byMark = d;
                }
            }
        }
        if (!level.isLoaded(gate)) {
            return null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (byMark != null && d != byMark) {
                continue;
            }
            BlockPos row = gate.relative(d);
            Direction side = d.getClockWise();
            for (int k = 2; k <= MAX_HALF + 1; k++) {
                if (level.getBlockState(row.relative(side, k)).is(Blocks.REINFORCED_DEEPSLATE)
                        && level.getBlockState(row.relative(side.getOpposite(), k)).is(Blocks.REINFORCED_DEEPSLATE)) {
                    return new Facing(d, k - 1);
                }
            }
        }
        if (byMark != null) {
            return new Facing(byMark, 1);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(gate.relative(d).above()).is(Blocks.IRON_BARS)) {
                return new Facing(d, 1);
            }
        }
        return null;
    }

    // 格子の場所 (along = 列の横の位置、dy = 高さ)
    static BlockPos bar(BlockPos gate, Facing f, int along, int dy) {
        return f.row(gate).relative(f.toward().getClockWise(), along).above(dy);
    }

    // 開いた門の列の中か (足の位置)
    static boolean inGate(BlockPos at, BlockPos gate, Facing f) {
        BlockPos row = f.row(gate);
        Direction side = f.toward().getClockWise();
        int dx = at.getX() - row.getX();
        int dz = at.getZ() - row.getZ();
        int depth = dx * f.toward().getStepX() + dz * f.toward().getStepZ();
        int along = dx * side.getStepX() + dz * side.getStepZ();
        return depth == 0 && Math.abs(along) <= f.half() && at.getY() >= gate.getY() && at.getY() <= gate.getY() + 2;
    }

    static boolean readyFor(@Nullable String stage) {
        return "gate".equals(stage) || "descend".equals(stage) || "warden".equals(stage) || "heart".equals(stage) || "report".equals(stage);
    }

    public static boolean isOpen(MinecraftServer server) {
        return TownData.get(server).abyssGateOpen;
    }

    // 門を開ける (記録して、読み込まれていれば格子を溶かす)
    public static void open(MinecraftServer server, @Nullable ServerPlayer by) {
        TownData data = TownData.get(server);
        boolean first = !data.abyssGateOpen;
        if (first) {
            data.abyssGateOpen = true;
            data.setDirty();
        }
        BlockPos gate = TownGuests.mark(server, AbyssEntrance.GATE);
        if (gate != null) {
            melt(server.overworld(), gate, first);
        }
        if (by != null && first) {
            Notice.narrate(by, "message.skyabyss.ch5.gate_open");
        }
    }

    private static void melt(ServerLevel level, BlockPos gate, boolean loud) {
        if (!level.isLoaded(gate)) {
            return;
        }
        Facing f = facing(level.getServer(), level, gate);
        // 列の横の幅いっぱい・高さいっぱいを見る (古い都の 3×5 も、新しい都の 5×6 も、回った都も)
        for (int a = -MAX_HALF; a <= MAX_HALF; a++) {
            for (int dy = 0; dy <= MAX_H; dy++) {
                BlockPos b = bar(gate, f, a, dy);
                if (level.isLoaded(b) && level.getBlockState(b).is(Blocks.IRON_BARS)) {
                    level.setBlock(b, Blocks.CAVE_AIR.defaultBlockState(), 3);
                    if (loud) {
                        level.sendParticles(ParticleTypes.SCULK_SOUL, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0.02);
                    }
                }
            }
        }
        if (loud) {
            level.playSound(null, gate, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2.5f, 0.5f);
            level.playSound(null, gate, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 1f, 0.5f);
            level.playSound(null, gate, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5f, 0.5f);
        }
    }

    // 残った格子の数 (自己テスト・開発用)
    static int barsLeft(ServerLevel level, BlockPos gate) {
        Facing f = facing(level.getServer(), level, gate);
        int n = 0;
        for (int a = -MAX_HALF; a <= MAX_HALF; a++) {
            for (int dy = 0; dy <= MAX_H; dy++) {
                if (level.getBlockState(bar(gate, f, a, dy)).is(Blocks.IRON_BARS)) {
                    n++;
                }
            }
        }
        return n;
    }

    static void forgetFacing() {
        facingFor = null;
        facingCache = null;
    }

    // くぐった人を深淵へ (move が偽なら合図だけ。自己テスト用)
    static void passThrough(ServerPlayer p, boolean move) {
        Quests.signalWithParty(p.serverLevel(), List.of(p), SIGNAL_ENTER);
        if (move) {
            AbyssWorld.enter(p);
        }
    }

    // 深淵から底の境の門の南へ戻す (都の印が無ければ起きる所へ)
    public static void up(ServerPlayer p) {
        BlockPos gate = TownGuests.mark(p.server, AbyssEntrance.GATE);
        if (gate == null) {
            AbyssWorld.leave(p);
            return;
        }
        Notice.hint(p, "message.skyabyss.abyss.exit");
        ServerLevel ow = p.server.overworld();
        Facing f = facing(p.server, ow, gate);
        // 格子の列から離れる向きに一歩 (都が回っていても、門を背にして大広間の側へ)
        BlockPos at = gate.relative(f.toward().getOpposite());
        p.teleportTo(ow, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, f.toward().getOpposite().toYRot(), 0f);
    }

    // ---------------------------------------------------------------- 見回り (都の底の境。0.25 秒ごと)
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 5 != 2) {
            return;
        }
        ServerLevel level = server.overworld();
        BlockPos gate = null;
        boolean looked = false;
        // くぐった人は別の次元へ移って一覧から抜けるので、写しを回す (0.5.0 で深淵に入ると落ちた)
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (!looked) {
                gate = TownGuests.mark(server, AbyssEntrance.GATE);
                looked = true;
            }
            if (gate == null) {
                break;
            }
            if (!p.blockPosition().closerThan(gate, 8)) {
                continue;
            }
            check(server, p, gate, true);
        }
        heartTick(server);
    }

    static void check(MinecraftServer server, ServerPlayer p, BlockPos gate, boolean move) {
        boolean open = isOpen(server);
        if (open) {
            melt(server.overworld(), gate, false);
        } else if (readyFor(TownGuests.stageOf(p, AbyssEntrance.QUEST)) && p.blockPosition().closerThan(gate, OPEN_NEAR)) {
            if (AbyssEntrance.holdsKey(p)) {
                open(server, p);
                open = true;
            }
        }
        if (open && inGate(p.blockPosition(), gate, facing(server, server.overworld(), gate))) {
            passThrough(p, move);
        }
    }

    // 心臓の間: 段 descend の人に合図、倒れたあとの台に鼓動
    private static void heartTick(MinecraftServer server) {
        ServerLevel abyss = server.getLevel(AbyssWorld.LEVEL);
        if (abyss == null || abyss.players().isEmpty()) {
            return;
        }
        for (ServerPlayer p : WardenArena.inside(abyss)) {
            if ("descend".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST))) {
                Quests.signalWithParty(p.serverLevel(), List.of(p), SIGNAL_HEART_ROOM);
            }
        }
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart != null && server.getTickCount() % 40 == 2 && WardenArena.current(abyss) == null && WardenArena.cleared(server) && abyss.isLoaded(heart)) {
            abyss.sendParticles(ParticleTypes.SCULK_SOUL, heart.getX() + 0.5, heart.getY() + 0.4, heart.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.02);
            abyss.playSound(null, heart, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.5f, 0.7f);
        }
    }

    // ---------------------------------------------------------------- 右クリック (深淵の鎖・心臓の台)
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getLevel().dimension() != AbyssWorld.LEVEL) {
            return;
        }
        BlockPos at = event.getPos();
        BlockPos exit = AbyssBuild.mark("exit");
        if (exit != null && at.getX() == exit.getX() && at.getZ() == exit.getZ() && at.getY() >= exit.getY() && at.getY() <= exit.getY() + 14) {
            event.setCanceled(true);
            AbyssReturn.begin(p, true);
            return;
        }
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart != null && at.closerThan(heart, 2.5) && touch(p)) {
            event.setCanceled(true);
        }
    }

    // 心臓に触れる (段 heart の人。番人が倒れていること)。三つの言葉を少しずつ
    public static boolean touch(ServerPlayer p) {
        if (!"heart".equals(TownGuests.stageOf(p, AbyssEntrance.QUEST)) || p.getServer() == null || WardenArena.current(p.serverLevel()) != null) {
            return false;
        }
        if (!TOUCHING.add(p.getUUID())) {
            return true;
        }
        MinecraftServer server = p.getServer();
        Notice.narrate(p, "message.skyabyss.abyss.heart_1");
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 2f, 0.6f);
        later(server, HEART_LINE_GAP, () -> Notice.narrate(p, "message.skyabyss.abyss.heart_2"));
        later(server, HEART_LINE_GAP * 2, () -> {
            Notice.narrate(p, "message.skyabyss.abyss.heart_3");
            Quests.signalWithParty(p.serverLevel(), List.of(p), SIGNAL_HEART_TOUCH);
            TOUCHING.remove(p.getUUID());
        });
        return true;
    }

    private static void later(MinecraftServer server, int ticks, Runnable r) {
        LATER.add(new Pending(server.getTickCount() + ticks, r));
    }

    private record Pending(int at, Runnable run) {
    }

    private static final List<Pending> LATER = new ArrayList<>();
    // 心臓の言葉を聞いている人 (重ねて触れても一度だけ)
    private static final Set<UUID> TOUCHING = new HashSet<>();

    @SubscribeEvent
    static void onLater(ServerTickEvent.Post event) {
        if (LATER.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        List<Pending> due = new ArrayList<>();
        LATER.removeIf(e -> e.at() <= now && due.add(e));
        due.forEach(e -> e.run().run());
    }
}
