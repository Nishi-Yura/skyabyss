package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 三つの試練のわかりやすさ (ユーザーの試遊 2026-10-20「亀の試練色々分かりにくい」への手当て。docs/design/08 の 4 章の「見える進み」)。
//  - 目的の板 (画面右上、潮見の目盛りの下): 今いる試練の「いま何をするか」の一行と、進みの点。サーバーが人ごとに決めて Gauge に載せる
//  - 石の上と入口の看板の上の「進みの点」: 点がいくつ灯っているか (その人のグループの進み。その人にだけ見える)
//  - 触れるもの・目当ての光: 栓 (元の溜めの色と行き先の色の粉)、重石の床、戻しの栓、祭壇の光の柱、大貝の泡、岩棚の光
//  - 成功の手応え: 栓を引いた・戻した (どの溜めからどの溜めへか)、重石、浮き石が灯りに届いた、戸が開いた、真珠が半分まで来た (音 + 光 + 一行)
//  - 戸の前に立つと、閉じている理由と今の水位を言う
// 状態は持たない (西・東の「段」だけ、試練の部屋の持ち主が替わる・建て直すと戻す)。進みの数え方は progress() 一か所に置いてある
final class TideGuide {
    // 目的の板に載せる内容。id は lang の gui.skyabyss.tide_goal.<id>、先頭の語 (west/east/front/hub) が見出し。
    // done/total は点の数。a・b・c は文に入れる数 (東は下・中・上の水位)
    record Goal(String id, int done, int total, int a, int b, int c) {
        static final Goal NONE = new Goal("", 0, 0, 0, 0, 0);
    }

    // 目的の板の文の id (lang の gui.skyabyss.tide_goal.<id>)。自己テストが ja・en の両方にあることを見る
    static final java.util.List<String> GOAL_IDS = java.util.List.of("west.take", "west.carry", "west.place", "west.help", "west.done", "west.sleep",
            "east.go", "east.exit", "east.done", "east.sleep", "front.nests", "front.boss", "front.stone", "front.done", "front.sleep", "hub.stones",
            "hub.wait", "hub.bottom", "up.start", "up.climb", "up.arena", "up.keeper", "up.lift", "up.done");

    // 点の色 (灯った点は金、まだの点は暗い青灰)
    private static final DustParticleOptions LIT = new DustParticleOptions(new Vector3f(1f, 0.88f, 0.45f), 1.1f);
    private static final DustParticleOptions DIM = new DustParticleOptions(new Vector3f(0.28f, 0.36f, 0.4f), 1.0f);
    // 溜めの色 (下 = 黄、中 = 黄緑、上 = 赤紫。栓の台・敷物・角の柱と同じ)
    private static final DustParticleOptions[] BASIN = {new DustParticleOptions(new Vector3f(1f, 0.85f, 0.15f), 1.2f),
            new DustParticleOptions(new Vector3f(0.5f, 0.9f, 0.15f), 1.2f), new DustParticleOptions(new Vector3f(0.85f, 0.2f, 0.85f), 1.2f)};

    // 入口の看板の上に出す点の中心 (庭・潮見の間・干潟の順 = Trial の順 WEST・EAST・FRONT)
    private static final Vec3[] ENTRANCE_PIPS = {new Vec3(-18.5, PLATFORM_TOP + 3.6, -2.5), new Vec3(24.5, HUB_LEDGE + 3.6, -13.5),
            new Vec3(3.5, TUNNEL_FLOOR + 3.6, -29.5)};

    // 西の「半ばを越えた」(真珠が PLAT_C の線を北へ越えた)、東の「いちばん上まで来た段」
    private static boolean westPast;
    private static int eastStage;
    private static int[] prevLevels;
    private static boolean[] prevLever;
    private static boolean prevPlate;
    private static boolean prevPlateKnown;
    private static boolean prevWestOut;
    private static int counter;
    private static final Map<UUID, Goal> LAST = new HashMap<>();
    private static final Map<UUID, Long> DOOR_HINT = new HashMap<>();

    private TideGuide() {
    }

    static void resetEast() {
        eastStage = 0;
        prevLevels = null;
        prevLever = null;
        prevPlateKnown = false;
    }

    static void clear() {
        westPast = false;
        resetEast();
        prevWestOut = false;
        counter = 0;
        LAST.clear();
        DOOR_HINT.clear();
    }

    // ---------------------------------------------------------------- 目的の板の中身
    // 試練ごとの進みの点 (done, total)。player の組がその部屋を持っていれば、部屋の「いま」(真珠・段) も数える
    static int[] progress(ServerLevel level, TideBackState.Group state, TideBackState.Trial trial, boolean ownsRoom) {
        switch (trial) {
            case WEST -> {
                if (state.rung(trial)) {
                    return new int[] {3, 3};
                }
                int done = 0;
                if (ownsRoom && TideGarden.pearlState() != TideGarden.Pearl.CLAM) {
                    done = westPast ? 2 : 1;
                }
                return new int[] {done, 3};
            }
            case EAST -> {
                if (state.rung(trial)) {
                    return new int[] {3, 3};
                }
                return new int[] {ownsRoom ? Math.min(3, eastStage) : 0, 3};
            }
            default -> {
                int nests = Integer.bitCount(state.nestSites());
                if (state.rung(trial)) {
                    return new int[] {nests + 1, nests + 1};
                }
                int broken = 0;
                for (int s = 0; s < 6; s++) {
                    if ((state.nestSites() & (1 << s)) != 0 && state.nestBroken(s)) {
                        broken++;
                    }
                }
                return new int[] {broken + (state.beckonerDefeated() ? 1 : 0), nests + 1};
            }
        }
    }

    // その人の「いま何をするか」。試練の部屋の外では広間でだけ (三つの石の数)、ほかは NONE
    static Goal goalFor(ServerLevel level, ServerPlayer p) {
        Vec3 v = p.position();
        TideBackState.Group st = TideBackTrials.state(level, p);
        if (TideRooms.gardenBox().contains(v)) {
            int[] pr = progress(level, st, TideBackState.Trial.WEST, TideRooms.owns(TideRooms.GARDEN, p));
            if (st.rung(TideBackState.Trial.WEST)) {
                return new Goal(st.sleeping() == TideBackState.Trial.WEST ? "west.sleep" : "west.done", pr[0], pr[1], 0, 0, 0);
            }
            boolean mine = TideRooms.owns(TideRooms.GARDEN, p);
            String id = "west.take";
            if (mine && TideGarden.pearlState() == TideGarden.Pearl.OUT) {
                if (TideGarden.holds(p)) {
                    id = p.position().distanceToSqr(STONE_WEST.getX() + 0.5, STONE_WEST.getY(), STONE_WEST.getZ() + 0.5) < 14 * 14 ? "west.place" : "west.carry";
                } else {
                    id = "west.help";
                }
            }
            return new Goal(id, pr[0], pr[1], 0, 0, 0);
        }
        if (watchZone().contains(v)) {
            int[] pr = progress(level, st, TideBackState.Trial.EAST, TideRooms.owns(TideRooms.WATCH, p));
            if (st.rung(TideBackState.Trial.EAST)) {
                return new Goal(st.sleeping() == TideBackState.Trial.EAST ? "east.sleep" : "east.done", pr[0], pr[1], 0, 0, 0);
            }
            int[] lv = TideWatch.levels();
            return new Goal(lv[2] == 0 ? "east.exit" : "east.go", pr[0], pr[1], lv[0], lv[1], lv[2]);
        }
        if (frontZone().contains(v)) {
            int[] pr = progress(level, st, TideBackState.Trial.FRONT, TideRooms.owns(TideRooms.FLATS, p));
            int nests = Integer.bitCount(st.nestSites());
            if (st.rung(TideBackState.Trial.FRONT)) {
                return new Goal(st.sleeping() == TideBackState.Trial.FRONT ? "front.sleep" : "front.done", pr[0], pr[1], 0, 0, 0);
            }
            String id = !st.allNestsBroken() ? "front.nests" : !st.beckonerDefeated() ? "front.boss" : "front.stone";
            return new Goal(id, pr[0], pr[1], nests, 0, 0);
        }
        Goal up = upGoal(level, p, st);
        if (up != null) {
            return up;
        }
        if (HUB.aabb().inflate(2, 8, 2).contains(v) || LEDGE_S.aabb().inflate(1, 2, 1).contains(v) || GALLERY_E.aabb().inflate(1, 2, 1).contains(v)) {
            int n = st.rungCount();
            if (n >= 3) {
                return new Goal(st.bottomOpen() ? "hub.bottom" : "hub.wait", 3, 3, 0, 0, 0);
            }
            return new Goal("hub.stones", n, 3, 0, 0, 0);
        }
        return Goal.NONE;
    }

    // 息の間の「いま何をするか」: 縁 (入口の泡) → 昇る (高さ・輪の数) → 舞台 (戦い・風の柱) → 展望の間。輪 = 下・中・上の壁の輪 (3 つ)
    @javax.annotation.Nullable
    static Goal upGoal(ServerLevel level, ServerPlayer p, TideBackState.Group st) {
        Vec3 v = p.position();
        if (!Breath.active(v) || v.y < BreathLayout.LIP - 1) {
            return null;
        }
        int rings = Breath.ringsLit(st.upBest());
        int h = BreathLayout.heightOf(v.y);
        int total = BreathLayout.TOTAL_HEIGHT;
        if (v.y >= BreathLayout.DECK_FLOOR + 0.7) {
            return new Goal("up.done", 3, 3, h, total, st.upBest());
        }
        if (BreathLayout.arenaBox().contains(v) && v.y >= BreathLayout.ARENA_FLOOR + 0.7) {
            boolean open = Breath.deckOpen(level, p, level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL);
            if (open) {
                return new Goal("up.lift", 3, 3, h, total, st.upBest());
            }
            return new Goal(BreathSummit.fight() == BreathSummit.Fight.ACTIVE ? "up.keeper" : "up.arena", 3, 3, h, total, st.upBest());
        }
        if (v.y <= BreathLayout.LIP + 2.5 && st.upBest() == 0) {
            return new Goal("up.start", 0, 3, h, total, 0);
        }
        return new Goal("up.climb", rings, 3, h, total, st.upBest());
    }

    private static AABB watchZone() {
        return new AABB(13, HUB_LEDGE - 2, -37, 60, LEDGE_FLOOR + 8, -3);
    }

    private static AABB frontZone() {
        return new AABB(-26, 56, -84, 26, 80, -17);
    }

    // ---------------------------------------------------------------- 毎 5 tick (TideBackTrials から。人がいるときだけ)
    static void tick(ServerLevel level) {
        counter++;
        refreshGoals(level);
        tickWest(level);
        tickEast(level);
        if (counter % 2 == 0) {
            tickPips(level);
        }
        if (counter % 2 == 0) {
            tickLeverGlow(level);
        }
        BreathGuide.tick(level, counter);
    }

    // 目的が変わった人にだけ、目盛りの板を送り直す (毎秒の送りを待たない)
    private static void refreshGoals(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            Goal g = goalFor(level, p);
            Goal old = LAST.put(p.getUUID(), g);
            if (old != null && !old.equals(g)) {
                PacketDistributor.sendToPlayer(p, TideBackTrials.gauge(level, p));
            }
        }
    }

    // ---------------------------------------------------------------- 西: 真珠の道しるべ
    private static void tickWest(ServerLevel level) {
        UUID owner = TideRooms.owner(TideRooms.GARDEN);
        TideGarden.Pearl pearl = TideGarden.pearlState();
        if (owner == null || pearl == TideGarden.Pearl.CLAM) {
            westPast = false;
            prevWestOut = false;
            // 大貝は口を開けたまま光る (触れよの合図)。泡を、庭に人がいる間だけ
            if (owner != null && counter % 8 == 0 && !TideBackState.of(level, owner).rung(TideBackState.Trial.WEST)) {
                BlockPos c = TideGarden.pearlBlock();
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.getX() + 0.5, c.getY() + 0.9, c.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.02);
                level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.6, c.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
            }
            return;
        }
        if (pearl != TideGarden.Pearl.OUT) {
            return;
        }
        prevWestOut = true;
        // 半ばを越えた (運んでいる人か、落ちた真珠が PLAT_C の線を北へ越えた)
        if (!westPast) {
            boolean past = false;
            for (ServerPlayer p : Groups.online(level, owner)) {
                if (TideGarden.holds(p) && p.getZ() < PLAT_C[1]) {
                    past = true;
                    break;
                }
            }
            if (past) {
                westPast = true;
                for (ServerPlayer p : Groups.online(level, owner)) {
                    if (TideRooms.gardenBox().contains(p.position())) {
                        Notice.bar(p, ModText.progress("message.skyabyss.tide.pearl_halfway"));
                        success(p, 1.3f);
                    }
                }
            }
        }
        // 祭壇の光の柱 (庭の中の持ち主の組にだけ。天井まで届く)。運んでいるあいだの道しるべ
        if (counter % 2 == 0) {
            for (ServerPlayer p : Groups.online(level, owner)) {
                if (!TideRooms.gardenBox().contains(p.position())) {
                    continue;
                }
                for (int i = 0; i < 12; i++) {
                    level.sendParticles(p, ParticleTypes.END_ROD, false, STONE_WEST.getX() + 0.5, STONE_WEST.getY() + 1.5 + i * 1.2, STONE_WEST.getZ() + 0.5, 1,
                            0.08, 0.1, 0.08, 0.0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 東: 栓・重石・水位・戸
    private static void tickEast(ServerLevel level) {
        UUID owner = TideRooms.owner(TideRooms.WATCH);
        if (owner == null) {
            return;
        }
        TideBackState.Group state = TideBackState.of(level, owner);
        int[] lv = TideWatch.levels();
        TidePuzzle.Config c = TideWatch.cfg();
        // 人の進んだ段
        for (ServerPlayer p : TideWatch.near(level)) {
            BlockPos b = p.blockPosition();
            if (b.getX() >= 38 && b.getY() >= TIER_FLOOR[1] + 1) {
                eastStage = Math.max(eastStage, 1);
            }
            if (b.getX() >= 50 && b.getY() >= TIER_FLOOR[2] + 1) {
                eastStage = Math.max(eastStage, 2);
            }
        }
        if (lv[2] == 0) {
            eastStage = Math.max(eastStage, 3);
        }
        // 栓の引き・戻し
        if (prevLever == null || prevLever.length != c.plugs().length) {
            prevLever = new boolean[c.plugs().length];
            for (int i = 0; i < prevLever.length; i++) {
                prevLever[i] = TideWatch.leverOn(level, TideWatch.leverSlot(i));
            }
        } else {
            for (int i = 0; i < prevLever.length; i++) {
                boolean on = TideWatch.leverOn(level, TideWatch.leverSlot(i));
                if (on == prevLever[i]) {
                    continue;
                }
                prevLever[i] = on;
                TidePuzzle.Plug plug = c.plugs()[i];
                BlockPos slot = TideWatch.leverSlot(i);
                Component from = basinName(plug.from());
                Component to = basinName(plug.to());
                level.sendParticles(BASIN[plug.from()], slot.getX() + 0.5, slot.getY() + 1.5, slot.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.02);
                level.sendParticles(ParticleTypes.END_ROD, slot.getX() + 0.5, slot.getY() + 1.6, slot.getZ() + 0.5, 6, 0.25, 0.3, 0.25, 0.03);
                level.playSound(null, slot, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, on ? 1.4f : 0.8f);
                for (ServerPlayer p : TideWatch.near(level)) {
                    Notice.bar(p, on ? ModText.progress("message.skyabyss.tide.plug_on", from, to) : ModText.hint("message.skyabyss.tide.plug_off", from, to));
                }
            }
        }
        // 重石の床
        boolean plate = TideWatch.platePressed(level);
        if (prevPlateKnown && plate != prevPlate) {
            TidePuzzle.Plug pp = c.plugs()[c.platePlug()];
            BlockPos at = TideWatch.PLATES[c.plateTier()];
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.05);
            for (ServerPlayer p : TideWatch.near(level)) {
                Notice.bar(p, plate ? ModText.progress("message.skyabyss.tide.plate_down", basinName(pp.to()), basinName(pp.from()))
                        : ModText.hint("message.skyabyss.tide.plate_up"));
            }
        }
        prevPlate = plate;
        prevPlateKnown = true;
        // 水位の変わり目 (浮き石が灯りに届いた・戸が開いた)
        if (prevLevels != null) {
            for (int k = 0; k < 3; k++) {
                if (lv[k] == prevLevels[k]) {
                    continue;
                }
                if (k < 2 && prevLevels[k] < TidePuzzle.REACH && lv[k] >= TidePuzzle.REACH) {
                    for (ServerPlayer p : TideWatch.near(level)) {
                        Notice.bar(p, ModText.progress("message.skyabyss.tide.float_reached", basinName(k)));
                        success(p, 1.5f);
                    }
                    BlockPos d = BASIN_DOOR[k];
                    level.sendParticles(ParticleTypes.END_ROD, d.getX() + 0.5, TIER_FLOOR[k] + lv[k] + 1.5, d.getZ() + 0.5 + (k == 0 ? 2.5 : 2.5), 10, 1.5, 0.3, 1.5, 0.02);
                }
                if (TideWatch.doorOpen(k, lv[k]) && !TideWatch.doorOpen(k, prevLevels[k])) {
                    for (ServerPlayer p : TideWatch.near(level)) {
                        Notice.bar(p, ModText.progress(k < 2 ? "message.skyabyss.tide.door_opened" : "message.skyabyss.tide.exit_opened", basinName(k)));
                        success(p, k < 2 ? 1.0f : 1.4f);
                    }
                }
            }
        }
        prevLevels = lv.clone();
        // 戸の前: 閉じている理由と今の水位 (3 秒に一度)
        long now = level.getGameTime();
        for (ServerPlayer p : TideWatch.near(level)) {
            for (int k = 0; k < 3; k++) {
                if (TideWatch.doorOpen(k, lv[k])) {
                    continue;
                }
                BlockPos d = k == 2 ? T_EXIT_DOOR : BASIN_DOOR[k];
                if (p.distanceToSqr(d.getX() + 0.5, d.getY() + 0.5, d.getZ() + 0.5) > 2.6 * 2.6) {
                    continue;
                }
                if (now - DOOR_HINT.getOrDefault(p.getUUID(), -1000L) < 60) {
                    break;
                }
                DOOR_HINT.put(p.getUUID(), now);
                Notice.bar(p, ModText.hint(k < 2 ? "message.skyabyss.tide.door_shut" : "message.skyabyss.tide.exit_shut", basinName(k), lv[k]));
                break;
            }
        }
        // 岩棚の石: 底の戸が開いたら光の柱 (まだ鳴らしていない組の人にだけ)
        if (lv[2] == 0 && counter % 2 == 0) {
            for (ServerPlayer p : TideWatch.near(level)) {
                if (state.rung(TideBackState.Trial.EAST) || !Groups.isMember(p, owner)) {
                    continue;
                }
                for (int i = 0; i < 6; i++) {
                    level.sendParticles(p, ParticleTypes.END_ROD, false, STONE_EAST.getX() + 0.5, STONE_EAST.getY() + 1.5 + i * 1.2, STONE_EAST.getZ() + 0.5, 1,
                            0.08, 0.1, 0.08, 0.0);
                }
            }
        }
    }

    // 栓の台・重石・戻しの栓の「触れよ」の光 (10 tick に一度。部屋に人がいて持ち主がいるときだけ)
    private static void tickLeverGlow(ServerLevel level) {
        if (TideRooms.owner(TideRooms.WATCH) == null || TideWatch.near(level).isEmpty()) {
            return;
        }
        TidePuzzle.Config c = TideWatch.cfg();
        for (int i = 0; i < c.plugs().length; i++) {
            TidePuzzle.Plug plug = c.plugs()[i];
            BlockPos slot = TideWatch.leverSlot(i);
            boolean on = TideWatch.leverOn(level, slot);
            // 栓の上: 元の溜めの色の粉。引かれていれば行き先の色の粉が一緒に立つ
            level.sendParticles(BASIN[plug.from()], slot.getX() + 0.5, slot.getY() + 2.1, slot.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.0);
            if (on) {
                level.sendParticles(BASIN[plug.to()], slot.getX() + 0.5, slot.getY() + 2.5, slot.getZ() + 0.5, 2, 0.15, 0.2, 0.15, 0.0);
            }
        }
        BlockPos plate = TideWatch.PLATES[c.plateTier()];
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, plate.getX() + 0.5, plate.getY() + 0.6, plate.getZ() + 0.5, 1, 0.3, 0.1, 0.3, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, TideWatch.RESET_LEVER.getX() + 0.5, TideWatch.RESET_LEVER.getY() + 2.0, TideWatch.RESET_LEVER.getZ() + 0.5, 2,
                0.2, 0.2, 0.2, 0.3);
    }

    // ---------------------------------------------------------------- 石と入口の看板の上の「進みの点」
    private static void tickPips(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            TideBackState.Group st = TideBackTrials.state(level, p);
            for (TideBackState.Trial t : TideBackState.Trial.values()) {
                Vec3 stone = Vec3.atCenterOf(TideBackTrials.stonePos(t)).add(0, 1.4, 0);
                Vec3 entrance = ENTRANCE_PIPS[t.ordinal()];
                boolean nearStone = p.position().distanceToSqr(stone) < 26 * 26;
                boolean nearEntrance = p.position().distanceToSqr(entrance) < 20 * 20;
                if (!nearStone && !nearEntrance) {
                    continue;
                }
                String room = t == TideBackState.Trial.WEST ? TideRooms.GARDEN : t == TideBackState.Trial.EAST ? TideRooms.WATCH : TideRooms.FLATS;
                int[] pr = progress(level, st, t, TideRooms.owns(room, p));
                if (nearStone) {
                    pips(level, p, stone, pr[0], pr[1]);
                }
                if (nearEntrance) {
                    pips(level, p, entrance, pr[0], pr[1]);
                }
            }
        }
    }

    // 輪になった点。灯った分は金、残りは暗い。見せるのはその人にだけ
    static void pips(ServerLevel level, ServerPlayer p, Vec3 center, int done, int total) {
        for (int i = 0; i < total; i++) {
            double a = Math.PI * 2 * i / total;
            double x = center.x + Math.cos(a) * 0.55;
            double z = center.z + Math.sin(a) * 0.55;
            level.sendParticles(p, i < done ? LIT : DIM, false, x, center.y, z, 1, 0, 0, 0, 0);
        }
        if (done >= total && total > 0) {
            level.sendParticles(p, ParticleTypes.END_ROD, false, center.x, center.y + 0.3, center.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    // ---------------------------------------------------------------- 成功の手応え
    private static void success(ServerPlayer p, float pitch) {
        TideBackTrials.notify(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, pitch);
        TideBackTrials.notify(p, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, pitch);
    }

    static Component basinName(int k) {
        return Component.translatable("gui.skyabyss.tide_basin." + k);
    }

    // 自己テスト用
    static int eastStage() {
        return eastStage;
    }

    static boolean westPast() {
        return westPast;
    }

    static void setEastStage(int stage) {
        eastStage = stage;
    }
}
