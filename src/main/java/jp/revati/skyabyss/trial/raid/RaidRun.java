package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.RouteCard;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialFrame;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// レイドの部屋を動かす枠組み (docs/design/32 の 7 章)。順路 (TrialRoute) が段 1〜3 でレイドの部屋を運ぶとき、ここの enter が呼ばれる。
//  - enter: 区画 (TrialSlot) の遠い島に部屋を建て (RaidRoom.build)、参加者を入口の間の休み場へ運び、準備 (3 秒) のあと戸を開けて start
//  - 毎 tick: room.tick → 全員が倒れた (または ctx.fail) なら全滅の処理 (全員を起こして入口の間へ戻し、reset → 準備 → start)、
//    越えたら出口の戸を開け、順路の「越えた」(onCleared) を呼ぶ (4 秒後に次の段へ運ばれる)
//  - 目的の帯の中身 (RaidHudNet) を 10 tick ごとに部屋の人へ送る
//  - 順路が終わると (finish) 部屋を片付ける。サーバーを再起動したら、順路の段の部屋を建て直して最初から
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaidRun {
    private RaidRun() {
    }

    public enum Phase {
        COUNTDOWN, ACTIVE, COMPLETE
    }

    public static final int FLOOR_Y = 64;
    // 一つの区画の中の部屋の間隔 (段 1・2・3 を x 方向に並べる)
    public static final int STEP_SPACING = 96;
    public static final int READY_TICKS = 60;
    public static final int HUD_EVERY = 10;

    public static final class Run {
        public final RaidRoom room;
        public final RaidCtx ctx;
        public final int step;
        public Phase phase = Phase.COUNTDOWN;
        public long until;
        public int wipes;
        public RaidHudNet.Hud lastHud;
        public boolean hudOff;

        Run(RaidRoom room, RaidCtx ctx, int step) {
            this.room = room;
            this.ctx = ctx;
            this.step = step;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    // 建てた部屋の箱 (グループごと。順路が終わると空気に戻す)
    private static final Map<UUID, List<AABB>> BUILT = new HashMap<>();

    @Nullable
    public static Run of(UUID group) {
        return RUNS.get(group);
    }

    public static BlockPos originOf(int slot, int step) {
        TrialFrame f = TrialSlot.frame(slot);
        return new BlockPos(f.dx() + (step - 1) * STEP_SPACING, FLOOR_Y, f.dz());
    }

    // 段 step が、このグループの順路でレイドの部屋か
    public static boolean isRaidStep(ServerLevel back, UUID group, int step) {
        if (step < 1 || step >= TrialRoute.LAST_STEP) {
            return false;
        }
        Trial.Def d = TrialRoute.defOf(TrialData.current(back, group), step);
        return d != null && RaidRooms.isRaid(d.id());
    }

    // 段 step の部屋へ参加者を運ぶ (部屋を建てる)。レイドの段でなければ false (呼ぶ側は従来どおり運ぶ)
    public static boolean enter(ServerLevel back, UUID group, String titan, int step) {
        TrialDraw.Draw draw = TrialData.current(back, group);
        Trial.Def d = step >= 1 && step < TrialRoute.LAST_STEP ? TrialRoute.defOf(draw, step) : null;
        if (d == null || !RaidRooms.isRaid(d.id())) {
            return false;
        }
        BackGroupData.Record r = BackGroupData.get(back).peek(group);
        if (r == null) {
            return false;
        }
        int slot = TrialSlot.of(r);
        if (slot <= 0) {
            slot = TrialSlot.assign(back, r);
            r.changed();
        }
        Run prev = RUNS.remove(group);
        if (prev != null) {
            safeEnd(prev);
        }
        RaidRoom room = RaidRooms.create(d.id());
        long seed = group.getLeastSignificantBits() ^ (back.getGameTime() * 0x9E3779B97F4A7C15L) ^ (step * 0xC2B2AE3D27D4EB4FL);
        RaidCtx ctx = new RaidCtx(back, group, titan, d.id(), slot, step, originOf(slot, step), seed);
        room.build(ctx);
        if (ctx.cell() == null) {
            SkyAbyss.LOGGER.warn("Raid room {} did not build a cell (RaidKit.buildCell)", d.id());
            return false;
        }
        BUILT.computeIfAbsent(group, k -> new ArrayList<>()).add(ctx.cell().bounds());
        Run run = new Run(room, ctx, step);
        run.until = back.getGameTime() + READY_TICKS;
        RUNS.put(group, run);
        Component name = Component.translatable(d.nameKey());
        float yaw = 180f;
        for (ServerPlayer p : TrialRoute.members(back, group)) {
            TrialRoute.move(back, p, ctx.cell().rest(), yaw);
            Notice.narrate(p, "message.skyabyss.route.step" + step, name);
            RouteCard.show(p, back, group, titan, draw, step, false);
        }
        sendHud(run, false);
        SkyAbyss.LOGGER.info("Raid: group {} entered room {} (step {}, slot {}, origin {})", group, d.id(), step, slot, ctx.origin().toShortString());
        return true;
    }

    // ---------------------------------------------------------------- tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || Titans.titanOfBack(level.dimension()) == null) {
            return;
        }
        long now = level.getGameTime();
        for (Run run : new ArrayList<>(RUNS.values())) {
            if (run.ctx.level() == level) {
                tickRun(run, now);
            }
        }
        if (now % 40 == 11) {
            resume(level);
        }
    }

    // 順路がレイドの段で動いているのに部屋が無い (サーバーの再起動のあと) とき、部屋を建て直す
    private static void resume(ServerLevel level) {
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null || !RaidRooms.active(titan)) {
            return;
        }
        BackGroupData groups = BackGroupData.get(level);
        for (UUID g : groups.ids()) {
            BackGroupData.Record r = groups.peek(g);
            if (r == null) {
                continue;
            }
            int step = TrialRoute.step(r);
            if (step < 1 || step >= TrialRoute.LAST_STEP || r.getInt(TrialRoute.MID) > 0 || TrialRoute.pending(g)) {
                continue;
            }
            Run run = RUNS.get(g);
            if ((run == null || run.step != step) && !TrialRoute.members(level, g).isEmpty() && isRaidStep(level, g, step)) {
                enter(level, g, titan, step);
            }
        }
    }

    private static void tickRun(Run run, long now) {
        RaidCtx ctx = run.ctx;
        switch (run.phase) {
            case COUNTDOWN -> {
                if (now >= run.until) {
                    run.phase = Phase.ACTIVE;
                    RaidKit.setGate(ctx, ctx.cell().entryGate(), true);
                    try {
                        run.room.start(ctx);
                    } catch (RuntimeException e) {
                        SkyAbyss.LOGGER.warn("Raid room {} start failed: {}", ctx.roomId(), e.toString());
                    }
                }
            }
            case ACTIVE -> {
                try {
                    run.room.tick(ctx);
                } catch (RuntimeException e) {
                    SkyAbyss.LOGGER.warn("Raid room {} tick failed: {}", ctx.roomId(), e.toString());
                }
                String fail = ctx.takeFail();
                if (fail != null) {
                    wipe(run, fail.isEmpty() ? null : fail);
                } else if (wiped(ctx)) {
                    wipe(run, "message.skyabyss.raid.wipe");
                } else if (run.room.isComplete()) {
                    complete(run);
                }
            }
            case COMPLETE -> {
                // 越えたあと数秒で帯から消す (次の段へ運ばれる)
                if (!run.hudOff && now >= run.until) {
                    run.hudOff = true;
                    run.lastHud = RaidHudNet.Hud.OFF;
                    for (ServerPlayer p : ctx.players()) {
                        sendTo(p, RaidHudNet.Hud.OFF);
                    }
                }
            }
        }
        if (now % HUD_EVERY == 0 && !run.hudOff) {
            sendHud(run, false);
        }
    }

    // 部屋にいる参加者が全員倒れているか (一人でも立っていれば続く)
    private static boolean wiped(RaidCtx ctx) {
        List<ServerPlayer> ps = ctx.players();
        if (ps.isEmpty()) {
            return false;
        }
        for (ServerPlayer p : ps) {
            if (!Downed.isDowned(p)) {
                return false;
            }
        }
        return true;
    }

    // 全滅・やり直し: 全員を起こして入口の間へ戻し、部屋を戻して、準備のあと最初から
    public static void wipe(Run run, @Nullable String reasonKey) {
        RaidCtx ctx = run.ctx;
        run.wipes++;
        for (ServerPlayer p : ctx.players()) {
            Downed.reviveFull(p);
            p.setHealth(p.getMaxHealth());
            p.removeAllEffects();
            p.getFoodData().setFoodLevel(20);
            TrialRoute.move(ctx.level(), p, ctx.cell().rest(), 180f);
            if (reasonKey != null) {
                Notice.warn(p, reasonKey);
            }
        }
        ctx.clearMobs();
        try {
            run.room.reset(ctx);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("Raid room {} reset failed: {}", ctx.roomId(), e.toString());
        }
        RaidKit.setGate(ctx, ctx.cell().entryGate(), false);
        RaidKit.setGate(ctx, ctx.cell().exitGate(), false);
        run.phase = Phase.COUNTDOWN;
        run.until = ctx.now() + READY_TICKS;
        sendHud(run, false);
        SkyAbyss.LOGGER.info("Raid: room {} reset for group {} (wipe {})", ctx.roomId(), ctx.group(), run.wipes);
    }

    private static void complete(Run run) {
        RaidCtx ctx = run.ctx;
        run.phase = Phase.COMPLETE;
        run.until = ctx.now() + 100;
        ctx.clearMobs();
        RaidKit.setGate(ctx, ctx.cell().exitGate(), true);
        sendHud(run, true);
        TrialDraw.Draw draw = TrialData.current(ctx.level(), ctx.group());
        String key = TrialRoute.keyOf(ctx.titan(), draw, run.step);
        SkyAbyss.LOGGER.info("Raid: room {} cleared by group {} (wipes {})", ctx.roomId(), ctx.group(), run.wipes);
        if (key != null) {
            TrialRoute.onCleared(ctx.level(), ctx.group(), ctx.titan(), key, null);
        }
    }

    // 順路が終わった (TrialRoute.finish から): 部屋を片付ける
    public static void onFinish(ServerLevel back, UUID group) {
        Run run = RUNS.remove(group);
        if (run != null) {
            safeEnd(run);
            for (ServerPlayer p : TrialRoute.present(back, group)) {
                sendTo(p, RaidHudNet.Hud.OFF);
            }
        }
        List<AABB> built = BUILT.remove(group);
        if (built != null) {
            for (AABB box : built) {
                RaidKit.clear(back, box);
            }
        }
        RaidKit.release(back, group);
    }

    private static void safeEnd(Run run) {
        try {
            run.room.end(run.ctx);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.warn("Raid room {} end failed: {}", run.ctx.roomId(), e.toString());
        }
        run.ctx.clearMobs();
    }

    // 倒れてよい場面か (Downed.mayDown から): 部屋の中なら、最後の一人も倒れる (死なない。全滅の処理が入口へ戻す)
    public static boolean inRoom(Player player) {
        if (RUNS.isEmpty() || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        Run run = RUNS.get(Groups.keyOf(sp));
        return run != null && run.phase != Phase.COMPLETE && run.ctx.level() == sp.level() && run.ctx.cell().bounds().contains(sp.position());
    }

    // ---------------------------------------------------------------- 入力と保護
    @Nullable
    public static Run runAt(Player player, BlockPos pos) {
        if (RUNS.isEmpty() || !(player instanceof ServerPlayer sp)) {
            return null;
        }
        Run run = RUNS.get(Groups.keyOf(sp));
        return run != null && run.ctx.level() == sp.level() && run.ctx.cell().bounds().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) ? run : null;
    }

    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private static void handleUse(Player player, BlockPos pos, boolean attack, Runnable cancel) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        Run run = runAt(sp, pos);
        if (run == null) {
            return;
        }
        // 部屋のブロックは壊せない
        if (attack) {
            cancel.run();
        }
        long now = sp.level().getGameTime();
        Long last = LAST_USE.get(sp.getUUID());
        if (last != null && last == now && !attack) {
            cancel.run();
            return;
        }
        if (run.phase == Phase.ACTIVE && run.room.onUse(run.ctx, sp, pos, attack)) {
            LAST_USE.put(sp.getUUID(), now);
            cancel.run();
        }
    }

    @SubscribeEvent
    static void onLeftClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        handleUse(event.getEntity(), event.getPos(), true, () -> event.setCanceled(true));
    }

    @SubscribeEvent
    static void onRightClick(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        handleUse(event.getEntity(), event.getPos(), false, () -> event.setCanceled(true));
    }

    @SubscribeEvent
    static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (runAt(event.getPlayer(), event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && runAt(p, event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 目的の帯
    private static void sendHud(Run run, boolean done) {
        RaidRoom room = run.room;
        RaidHudNet.Hud hud;
        if (run.phase == Phase.COUNTDOWN) {
            hud = new RaidHudNet.Hud(run.ctx.roomId(), "raid.skyabyss.hud.ready", List.of(), 0, 0, "", 0, 0, (int) Math.max(0, run.until - run.ctx.now()), false);
        } else {
            List<String> args = new ArrayList<>();
            for (Object o : room.objectiveArgs()) {
                args.add(String.valueOf(o));
            }
            boolean fin = done || run.phase == Phase.COMPLETE;
            hud = new RaidHudNet.Hud(run.ctx.roomId(), room.objectiveKey(), args, room.counter(), room.counterMax(), room.secondaryKey(), room.secondary(),
                    room.secondaryMax(), room.timerTicks(), fin);
        }
        run.lastHud = hud;
        for (ServerPlayer p : run.ctx.players()) {
            sendTo(p, hud);
        }
    }

    private static void sendTo(ServerPlayer p, RaidHudNet.Hud hud) {
        if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer) && p.connection != null) {
            PacketDistributor.sendToPlayer(p, hud);
        }
    }

    // 最後に送った中身 (自己テスト・撮影用)
    @Nullable
    public static RaidHudNet.Hud lastHud(UUID group) {
        Run run = RUNS.get(group);
        return run == null ? null : run.lastHud;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    public static void clearAll() {
        RUNS.clear();
        BUILT.clear();
        RaidKit.clearAll();
    }
}
