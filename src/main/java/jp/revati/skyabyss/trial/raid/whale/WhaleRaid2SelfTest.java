package jp.revati.skyabyss.trial.raid.whale;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 岩鯨のレイドの二つ目の部屋 (WR4 胃の縦穴の上り・WR5 石の海の床合わせ・WR6 縮む輪) の検査。順路を通さず、部屋を直接建てて台本で解く:
//   登録と周ごとの引き (枠ごとに二つのどちらも引かれうる)・建てた形・仕掛けの動き・目的の帯の中身・越える・リセットで build 直後へ戻る
// SKYABYSS_SELFTEST_ONLY="raid" (実時間で 1 分ほど)。結果は SELFTEST raid whale 2 PASSED / FAILED
public final class WhaleRaid2SelfTest {
    // 目的の文のキーの頭 (lang の検査が文字列の頭をキーと見ないよう、分けて書く)
    private static final String KEY_HEAD = "raid.skyabyss" + ".";

    private WhaleRaid2SelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.until("raid whale 2", WhaleRaid2SelfTest::tick));
    }

    private static final int CX = 31000;
    private static final int CZ = 31000;
    private static final int FLOOR = 230;
    private static final List<String> FAILS = new ArrayList<>();
    private static final List<BooleanSupplier> STAGES = new ArrayList<>();
    private static boolean started;
    private static boolean finished;
    private static int stage;
    private static int waited;
    private static ServerLevel level;
    private static MinecraftServer server;
    private static Difficulty difficulty;
    private static final List<FakePlayer> FAKES = new ArrayList<>();
    private static final List<RaidCtx> BUILT = new ArrayList<>();
    private static FakePlayer p1;
    private static FakePlayer p2;
    private static UUID group;
    private static RaidRoom room;
    private static RaidCtx ctx;
    private static boolean driving;
    private static int holdN;
    // 見取り図の素 (上から見た形と、南から見た断面)。run/raid2_whale_dump.json → tools/sheets/raid2_whale_sheet.py
    private static final List<String> DUMP = new ArrayList<>();

    private static void snap(String key) {
        var c = ctx.cell();
        BlockPos o = c.origin();
        StringBuilder sb = new StringBuilder("\"" + key + "\":{\"top\":[");
        for (int z = -c.hz(); z <= c.hz(); z++) {
            sb.append(z > -c.hz() ? "," : "").append('[');
            for (int x = -c.hx(); x <= c.hx(); x++) {
                String name = "air";
                int top = 0;
                for (int y = c.height(); y >= 0; y--) {
                    BlockState st = level.getBlockState(o.offset(x, y, z));
                    if (!st.isAir()) {
                        name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        top = y;
                        break;
                    }
                }
                sb.append(x > -c.hx() ? "," : "").append("[\"").append(name).append("\",").append(top).append(']');
            }
            sb.append(']');
        }
        sb.append("],\"elev\":[");
        for (int y = c.height(); y >= 0; y--) {
            sb.append(y < c.height() ? "," : "").append('[');
            for (int x = -c.hx(); x <= c.hx(); x++) {
                String name = "air";
                int depth = 0;
                for (int z = c.hz(); z >= -c.hz() - 1; z--) {
                    BlockState st = level.getBlockState(o.offset(x, y, z));
                    if (!st.isAir()) {
                        name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        depth = c.hz() - z;
                        break;
                    }
                }
                sb.append(x > -c.hx() ? "," : "").append("[\"").append(name).append("\",").append(depth).append(']');
            }
            sb.append(']');
        }
        sb.append("]}");
        DUMP.add(sb.toString());
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static boolean waitFor(BooleanSupplier cond, int maxTicks, String what) {
        if (cond.getAsBoolean()) {
            waited = 0;
            return true;
        }
        if (++waited > maxTicks) {
            FAILS.add("timed out waiting for: " + what);
            waited = 0;
            finished = true;
            return true;
        }
        return false;
    }

    private static boolean tick(MinecraftServer s) {
        if (!started) {
            started = true;
            server = s;
            level = s.overworld();
            difficulty = s.getWorldData().getDifficulty();
            s.setDifficulty(Difficulty.NORMAL, true);
            try {
                setup();
            } catch (RuntimeException | ReflectiveOperationException e) {
                SkyAbyss.LOGGER.error("SELFTEST raid whale 2 setup crashed", e);
                FAILS.add("setup crashed: " + e);
                finished = true;
            }
        }
        if (!finished && stage < STAGES.size()) {
            try {
                if (driving && room != null && ctx != null) {
                    room.tick(ctx);
                }
                if (STAGES.get(stage).getAsBoolean()) {
                    stage++;
                }
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST raid whale 2 crashed", e);
                FAILS.add("crashed in stage " + stage + ": " + e);
                finished = true;
            }
        }
        if (finished || stage >= STAGES.size()) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("raid2_whale_dump.json"), "{" + String.join(",", DUMP) + "}");
            } catch (java.io.IOException e) {
                FAILS.add("dump write failed: " + e);
            }
            teardown();
            if (FAILS.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST raid whale 2 PASSED (WR4-WR6 registered and drawn either way per slot; shaft stairs/fluid/switches, floor tile pattern vs mural, "
                        + "shrinking ring/outside damage/waves; each solved by script, HUD strip, reset back to the built state)");
            } else {
                FAILS.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST raid whale 2 FAILED: {}", f));
            }
            return true;
        }
        return false;
    }

    private static void teardown() {
        driving = false;
        try {
            for (RaidCtx c : BUILT) {
                try {
                    if (c.cell() != null) {
                        c.clearMobs();
                        jp.revati.skyabyss.trial.raid.RaidKit.clear(level, c.cell().bounds());
                    }
                } catch (RuntimeException ignored) {
                    // 片付けの失敗は数えない
                }
            }
            for (FakePlayer p : FAKES) {
                p.discard();
            }
        } finally {
            BUILT.clear();
            FAKES.clear();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            if (server != null) {
                server.setDifficulty(difficulty, true);
            }
        }
    }

    // ---------------------------------------------------------------- 準備
    private static RaidCtx newCtx(String id, int slot, int idx, long seed) throws ReflectiveOperationException {
        Constructor<RaidCtx> c = RaidCtx.class.getDeclaredConstructor(ServerLevel.class, UUID.class, String.class, String.class, int.class, int.class,
                BlockPos.class, long.class);
        c.setAccessible(true);
        RaidCtx x = c.newInstance(level, group, Titans.STONE_WHALE, id, slot, 1, new BlockPos(CX + 80 * idx, FLOOR, CZ), seed);
        BUILT.add(x);
        return x;
    }

    private static FakePlayer fake(String tag) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("raid whale 2 " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "[rw2_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.setPos(CX, FLOOR + 1, CZ);
        level.addFreshEntity(p);
        FAKES.add(p);
        return p;
    }

    private static void setup() throws ReflectiveOperationException {
        // 登録と周ごとの引き
        RaidRooms.ensureLoaded();
        String[][] pairs = {{"WR1", "WR4"}, {"WR2", "WR5"}, {"WR3", "WR6"}};
        for (int i = 0; i < 3; i++) {
            Slot slot = Slot.values()[i];
            List<String> ids = RaidRooms.idsOf(Titans.STONE_WHALE, slot);
            check(ids.size() == 2 && ids.contains(pairs[i][0]) && ids.contains(pairs[i][1]), "slot " + slot + " does not have the two rooms: " + ids);
            Set<String> drawn = new HashSet<>();
            for (long seed = 1; seed <= 400; seed++) {
                drawn.add(RaidRooms.drawOf(Titans.STONE_WHALE, slot, seed * 7919));
            }
            check(drawn.size() == 2, "the per-run draw does not pick both rooms of slot " + slot + ": " + drawn);
            check(RaidRooms.create(pairs[i][1]) != null && RaidRooms.create(pairs[i][1]) != RaidRooms.create(pairs[i][1]), "WR" + (4 + i) + " factory does not make a new instance each time");
        }
        check(jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(RaidRooms.create("WR4")) instanceof WhaleShaftRoom && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(RaidRooms.create("WR5")) instanceof WhaleTilesRoom && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(RaidRooms.create("WR6")) instanceof WhaleRingRoom,
                "WR4-WR6 are not the expected room classes");
        p1 = fake("a");
        p2 = fake("b");
        group = Groups.keyOf(p1);
        List<ServerPlayer> both = List.of(p1, p2);
        TrialRoute.PLAYERS = l -> both;
        buildStages();
    }

    private static void startRoom(String id, int slot, int idx, long seed) {
        try {
            room = RaidRooms.create(id);
            ctx = newCtx(id, slot, idx, seed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        room.build(ctx);
        check(ctx.cell() != null, id + ": no cell was built");
    }

    private static void put(FakePlayer p, BlockPos at) {
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
    }

    private static void healAll() {
        for (FakePlayer p : FAKES) {
            p.setHealth(p.getMaxHealth());
            p.invulnerableTime = 0;
        }
    }

    // ---------------------------------------------------------------- 台本
    private static void buildStages() {
        shaft();
        tiles();
        ring();
    }

    // ---- WR4
    private static WhaleShaftRoom shaftRoom() {
        return (WhaleShaftRoom) room;
    }

    private static void shaft() {
        STAGES.add(() -> {
            startRoom("WR4", 0, 0, 4242L);
            WhaleShaftRoom r = shaftRoom();
            check(!r.isComplete(), "WR4: complete before it started");
            for (int f = 0; f < 4; f++) {
                for (int i = 1; i <= 5; i++) {
                    for (BlockPos p : r.stepCells(ctx, f, i)) {
                        check(level.getBlockState(p).isAir(), "WR4: step f" + f + " i" + i + " is not retracted at the start (" + p.toShortString() + ")");
                    }
                }
                // 階段の形: 最後の段は次の踊り場と同じ高さでつながり、最初の段の後ろは足場
                BlockPos top = r.stepCells(ctx, f, 5).get(1);
                BlockPos beyond = top.relative(r.ascend(f));
                check(!level.getBlockState(beyond).isAir() && top.getY() == beyond.getY(), "WR4: flight " + f + " does not meet the landing (" + beyond.toShortString() + " is "
                        + level.getBlockState(beyond).getBlock() + ")");
                BlockPos first = r.stepCells(ctx, f, 1).get(1);
                BlockPos behind = first.relative(r.ascend(f).getOpposite()).below();
                check(!level.getBlockState(behind).isAir(), "WR4: flight " + f + " has nothing to step from (" + behind.toShortString() + ")");
                check(level.getBlockState(r.masterSwitch(ctx, f)).is(Blocks.BONE_BLOCK) && level.getBlockState(r.localSwitch(ctx, f)).is(Blocks.BONE_BLOCK), "WR4: switch pillar " + f + " missing");
            }
            for (int n = 1; n <= 4; n++) {
                BlockPos at = r.landingSpot(ctx, n);
                check(level.getBlockState(at.below()).isSolid() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir(), "WR4: landing " + n + " cannot be stood on");
            }
            snap("WR4a");
            put(p1, ctx.cell().rest());
            put(p2, ctx.cell().rest());
            room.start(ctx);
            driving = true;
            return true;
        });
        // 胃液: 床にいると傷を受ける (準備のあと)
        STAGES.add(() -> {
            put(p1, ctx.cell().floor(0, 0));
            if (shaftRoom().fluidLevel(ctx) < 0.3) {
                healAll();
            }
            return waitFor(() -> shaftRoom().fluidLevel(ctx) >= 0.3, 600, "WR4 fluid rising");
        });
        STAGES.add(() -> {
            put(p1, ctx.cell().floor(0, 0));
            return waitFor(() -> p1.getHealth() < p1.getMaxHealth(), 60, "WR4 fluid hurts the player on the floor");
        });
        STAGES.add(() -> {
            WhaleShaftRoom r = shaftRoom();
            check(r.fluidLevel(ctx) > 0 && r.fluidLevel(ctx) < 3, "WR4: the fluid is not rising slowly (" + r.fluidLevel(ctx) + ")");
            // 目的の帯
            check(r.objectiveKey().startsWith(KEY_HEAD + "WR4") && r.counterMax() == 4 && r.secondaryMax() > 0 && !r.secondaryKey().isEmpty(), "WR4: HUD values are empty");
            // 高いところ (踊り場 3) は無事。傷を受けない
            put(p1, r.landingSpot(ctx, 3));
            healAll();
            return true;
        });
        STAGES.add(() -> waitFor(() -> shaftRoom().tier() == 3, 20, "WR4 tier 3 reached"));
        STAGES.add(() -> {
            check(p1.getHealth() >= p1.getMaxHealth(), "WR4: the player above the fluid was hurt");
            WhaleShaftRoom r = shaftRoom();
            // 親台のスイッチ (二人目が壇に残る) と手元のスイッチ (一人) で登りを出す
            put(p1, r.landingSpot(ctx, 1));
            healAll();
            check(room.onUse(ctx, p2, r.masterSwitch(ctx, 0), false), "WR4: the master switch did not respond");
            check(!room.onUse(ctx, p2, ctx.origin().offset(1, 1, 1), false), "WR4: a plain floor block responded as a switch");
            return true;
        });
        STAGES.add(() -> waitFor(() -> shaftRoom().grown(0) == 5, 60, "WR4 flight 0 grows"));
        STAGES.add(() -> {
            WhaleShaftRoom r = shaftRoom();
            for (int i = 1; i <= 5; i++) {
                for (BlockPos p : r.stepCells(ctx, 0, i)) {
                    check(level.getBlockState(p).getBlock() instanceof StairBlock, "WR4: flight 0 step " + i + " did not appear");
                }
            }
            check(level.getBlockState(r.masterSwitch(ctx, 0).above()).is(Blocks.SEA_LANTERN), "WR4: the struck switch is not lit");
            // 残りの三つ: 踊り場の手元のスイッチ (一人で交互に)
            for (int f = 1; f < 4; f++) {
                put(p1, r.landingSpot(ctx, f));
                check(room.onUse(ctx, p1, r.localSwitch(ctx, f), true), "WR4: the local switch " + f + " did not respond");
            }
            return true;
        });
        STAGES.add(() -> waitFor(() -> shaftRoom().grown(1) == 5 && shaftRoom().grown(2) == 5 && shaftRoom().grown(3) == 5, 80, "WR4 flights 1-3 grow"));
        STAGES.add(() -> {
            snap("WR4b");
            return true;
        });
        // 引っ込む (終わりかけの知らせのあと)
        STAGES.add(() -> waitFor(() -> shaftRoom().grown(0) == 0, WhaleShaftRoom.EXTEND_TICKS + 100, "WR4 flight 0 retracts again"));
        STAGES.add(() -> {
            WhaleShaftRoom r = shaftRoom();
            for (int i = 1; i <= 5; i++) {
                for (BlockPos p : r.stepCells(ctx, 0, i)) {
                    check(level.getBlockState(p).isAir(), "WR4: flight 0 step " + i + " did not retract");
                }
            }
            // 頂へ: 踊り場 1 → 2 → 3 → 4。途中の高さが帯の数に出る
            put(p1, r.landingSpot(ctx, 2));
            return true;
        });
        STAGES.add(() -> waitFor(() -> shaftRoom().tier() == 2 && shaftRoom().counter() == 2, 20, "WR4 counter shows 2"));
        STAGES.add(() -> {
            WhaleShaftRoom r = shaftRoom();
            check(!r.isComplete(), "WR4: complete before the top");
            put(p1, r.landingSpot(ctx, 4));
            return true;
        });
        STAGES.add(() -> waitFor(() -> room.isComplete(), 20, "WR4 complete at the top"));
        // リセット: 階段・灯り・胃液が build 直後へ
        STAGES.add(() -> {
            WhaleShaftRoom r = shaftRoom();
            room.reset(ctx);
            check(!room.isComplete() && r.grown(1) == 0 && r.fluidLevel(ctx) == 0 && r.tier() == 0, "WR4: reset did not restore the room state");
            for (int f = 0; f < 4; f++) {
                for (int i = 1; i <= 5; i++) {
                    for (BlockPos p : r.stepCells(ctx, f, i)) {
                        check(level.getBlockState(p).isAir(), "WR4: step f" + f + " i" + i + " is still there after the reset");
                    }
                }
                check(level.getBlockState(r.masterSwitch(ctx, f).above()).is(Blocks.CHISELED_STONE_BRICKS) && level.getBlockState(r.localSwitch(ctx, f).above()).is(Blocks.CHISELED_STONE_BRICKS),
                        "WR4: switch " + f + " is still lit after the reset");
            }
            room.start(ctx);
            return true;
        });
        STAGES.add(() -> {
            driving = false;
            return true;
        });
    }

    // ---- WR5
    private static WhaleTilesRoom tilesRoom() {
        return (WhaleTilesRoom) room;
    }

    private static boolean pixel(WhaleTilesRoom r, int mask, int sx, int sz) {
        return WhaleTilesRoom.arm(mask, sx, sz);
    }

    private static void floorMatches(WhaleTilesRoom r, boolean target, String what) {
        for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
            for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                int mask = target ? r.targetMask(c, rr) : r.currentMask(c, rr);
                BlockPos center = r.tileCenter(ctx, c, rr);
                for (int sz = 0; sz < 3; sz++) {
                    for (int sx = 0; sx < 3; sx++) {
                        boolean bone = level.getBlockState(center.offset(sx - 1, 0, sz - 1)).is(Blocks.BONE_BLOCK);
                        if (bone != pixel(r, mask, sx, sz)) {
                            check(false, "WR5: the floor tile " + c + "," + rr + " does not show the " + what + " pattern");
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void tiles() {
        STAGES.add(() -> {
            driving = false;
            startRoom("WR5", 1, 1, 777L);
            WhaleTilesRoom r = tilesRoom();
            // 目標の骨はつながっている (隣の石板と筋が合う)・背骨がある
            int sensitive = 0;
            int off = 0;
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    int m = r.targetMask(c, rr);
                    if ((m & WhaleTilesRoom.E) != 0) {
                        check(c + 1 < WhaleTilesRoom.COLS && (r.targetMask(c + 1, rr) & WhaleTilesRoom.W) != 0, "WR5: the target is not connected (E of " + c + "," + rr + ")");
                    }
                    if ((m & WhaleTilesRoom.S) != 0) {
                        check(rr + 1 < WhaleTilesRoom.ROWS && (r.targetMask(c, rr + 1) & WhaleTilesRoom.N) != 0, "WR5: the target is not connected (S of " + c + "," + rr + ")");
                    }
                    if (WhaleTilesRoom.rotate(m, 1) != m) {
                        sensitive++;
                        off += r.turnsNeeded(c, rr) > 0 ? 1 : 0;
                    }
                    check(r.turnsNeeded(c, rr) >= 0, "WR5: tile " + c + "," + rr + " cannot be solved by turning");
                }
            }
            check(sensitive >= 8 && off * 10 >= sensitive * 5, "WR5: the tiles are not scrambled (" + off + "/" + sensitive + ")");
            check(!room.isComplete(), "WR5: solved at the start");
            floorMatches(r, false, "scrambled");
            snap("WR5a");
            // 見本 (北の壁) = 目標の姿
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    for (int sz = 0; sz < 3; sz++) {
                        for (int sx = 0; sx < 3; sx++) {
                            boolean bone = level.getBlockState(r.muralPos(ctx, c, rr, sx, sz)).is(Blocks.BONE_BLOCK);
                            check(bone == pixel(r, r.targetMask(c, rr), sx, sz), "WR5: the mural differs from the target at " + c + "," + rr + "," + sx + "," + sz);
                        }
                    }
                }
            }
            // 見張り台
            BlockPos b = r.balconySpot(ctx);
            check(level.getBlockState(b.below()).isSolid() && level.getBlockState(b).isAir() && level.getBlockState(b.above()).isAir(), "WR5: the balcony cannot be stood on");
            for (int i = 1; i <= 4; i++) {
                check(level.getBlockState(ctx.origin().offset(WhaleTilesRoom.HX - 5 + i, i, 8)).getBlock() instanceof StairBlock, "WR5: balcony stair " + i + " missing");
            }
            // 目的の帯
            check(room.objectiveKey().startsWith(KEY_HEAD + "WR5"), "WR5: no objective text");
            // 挑戦ごとに形が違う (三つの種で同じにならない) と、どれも解ける
            return true;
        });
        STAGES.add(() -> {
            // ほかの種で建てて比べる
            RaidRoom keep = room;
            RaidCtx keepCtx = ctx;
            String sig0 = signature((WhaleTilesRoom) keep);
            Set<String> sigs = new HashSet<>();
            sigs.add(sig0);
            for (int k = 0; k < 3; k++) {
                startRoom("WR5", 1, 2 + k, 1000L + 37 * k);
                WhaleTilesRoom o = tilesRoom();
                sigs.add(signature(o));
                for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                    for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                        check(o.turnsNeeded(c, rr) >= 0, "WR5: seed " + k + " has an unsolvable tile");
                    }
                }
            }
            check(sigs.size() >= 3, "WR5: the pattern/scramble is the same for different runs (" + sigs.size() + " distinct of 4)");
            room = keep;
            ctx = keepCtx;
            return true;
        });
        STAGES.add(() -> {
            WhaleTilesRoom r = tilesRoom();
            room.start(ctx);
            int[][] snap = new int[WhaleTilesRoom.COLS][WhaleTilesRoom.ROWS];
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    snap[c][rr] = r.currentMask(c, rr);
                }
            }
            // 一枚叩く: 回る・床が変わる (一人の台本。足場の高さのブロックは扱わない)
            BlockPos ground = r.tileCenter(ctx, 3, 2);
            check(!room.onUse(ctx, p1, ground.above(), false), "WR5: a block above the floor responded");
            int before = r.strikes();
            check(room.onUse(ctx, p1, r.tileCenter(ctx, 0, 0).offset(1, 0, 1), true), "WR5: striking a tile corner did not respond");
            check(r.strikes() == before + 1, "WR5: the strike was not counted");
            floorMatches(r, false, "rotated");
            // 解く: 各石板を必要な回数だけ (二人で分けて)
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    FakePlayer who = (c + rr) % 2 == 0 ? p1 : p2;
                    int n = r.turnsNeeded(c, rr);
                    for (int k = 0; k < n; k++) {
                        check(room.onUse(ctx, who, r.tileCenter(ctx, c, rr), false), "WR5: a tile did not respond");
                    }
                }
            }
            check(room.isComplete(), "WR5: all tiles turned to the target but the room is not complete");
            floorMatches(r, true, "target");
            snap("WR5b");
            // リセット: 最初のばらばらへ (叩いた一回も戻る)
            room.reset(ctx);
            check(!room.isComplete(), "WR5: reset left the room complete");
            floorMatches(r, false, "scrambled");
            check(r.currentMask(0, 0) == snap[0][0], "WR5: reset did not restore the scrambled start (tile 0,0)");
            int same = 0;
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    same += r.currentMask(c, rr) == snap[c][rr] ? 1 : 0;
                }
            }
            check(same == WhaleTilesRoom.COLS * WhaleTilesRoom.ROWS, "WR5: reset did not restore every tile (" + same + ")");
            return true;
        });
    }

    private static String signature(WhaleTilesRoom r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
            for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                sb.append(r.targetMask(c, rr)).append(',').append(r.currentMask(c, rr)).append(';');
            }
        }
        return sb.toString();
    }

    // ---- WR6
    private static WhaleRingRoom ringRoom() {
        return (WhaleRingRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(room);
    }

    private static final List<BlockPos> SAMPLES = new ArrayList<>();
    private static final List<BlockState> SAMPLE_STATES = new ArrayList<>();

    private static void ring() {
        STAGES.add(() -> {
            startRoom("WR6", 2, 5, 31L);
            WhaleRingRoom r = ringRoom();
            for (int[] xz : new int[][] {{0, 0}, {5, 5}, {-9, 3}, {12, 6}, {13, 5}, {-14, 1}, {14, 14}, {0, 14}, {15, 0}}) {
                BlockPos p = ctx.origin().offset(xz[0], 0, xz[1]);
                SAMPLES.add(p);
                SAMPLE_STATES.add(level.getBlockState(p));
            }
            // 輪の外の床は最初から赤い (隅)・中は元の床
            check(level.getBlockState(ctx.origin().offset(14, 0, 14)).is(Blocks.RED_NETHER_BRICKS), "WR6: the corner outside the ring is not red");
            check(!level.getBlockState(ctx.origin().offset(5, 0, 5)).is(Blocks.RED_NETHER_BRICKS) && !level.getBlockState(ctx.origin().offset(5, 0, 5)).is(Blocks.REDSTONE_BLOCK),
                    "WR6: the floor inside the ring is painted");
            check(room.objectiveKey().startsWith(KEY_HEAD + "WR6") && room.counterMax() == 3 && room.secondaryMax() > 0 && !room.secondaryKey().isEmpty(), "WR6: HUD values are empty");
            snap("WR6a");
            put(p1, ctx.origin().offset(12, 1, 12));
            healAll();
            room.start(ctx);
            driving = true;
            return true;
        });
        // 輪の外 (半径 17) は傷、中 (中央) は無傷。参加者は一人 (p1) なので、立つ場所を替えて見る
        STAGES.add(() -> {
            put(p1, ctx.origin().offset(12, 1, 12));
            return waitFor(() -> p1.getHealth() < p1.getMaxHealth(), 40, "WR6 outside the ring hurts");
        });
        STAGES.add(() -> {
            check(ringRoom().radius() <= WhaleRingRoom.R0 + 1e-6, "WR6: radius above the start");
            put(p1, ctx.origin().offset(0, 1, 3));
            healAll();
            holdN = 0;
            return true;
        });
        STAGES.add(() -> {
            put(p1, ctx.origin().offset(0, 1, 3));
            if (++holdN < 25) {
                return false;
            }
            check(p1.getHealth() >= p1.getMaxHealth(), "WR6: the player inside the ring was hurt");
            return true;
        });
        // 縮む: 時間を進める
        STAGES.add(() -> {
            WhaleRingRoom r = ringRoom();
            r.rewindShrink(WhaleRingRoom.SHRINK_TICKS / 2);
            return true;
        });
        STAGES.add(() -> waitFor(() -> ringRoom().radius() < WhaleRingRoom.R0 - 3, 10, "WR6 ring shrinks"));
        STAGES.add(() -> {
            WhaleRingRoom r = ringRoom();
            snap("WR6b");
            check(room.timerTicks() > 0 && room.secondary() < room.secondaryMax(), "WR6: HUD ring size/timer not live (" + room.timerTicks() + ", " + room.secondary() + ")");
            // 縮んだ分の床が赤い
            BlockPos a = ctx.origin().offset(11, 0, 4);
            check(r.radius() < Math.hypot(11, 4) && (level.getBlockState(a).is(Blocks.RED_NETHER_BRICKS) || level.getBlockState(a).is(Blocks.REDSTONE_BLOCK)), "WR6: the floor outside the shrunken ring is not red");
            // 縮んだ輪の外 (半径 11) の人は傷、輪の中は無傷
            put(p1, ctx.origin().offset(11, 1, 0));
            healAll();
            return true;
        });
        STAGES.add(() -> waitFor(() -> p1.getHealth() < p1.getMaxHealth(), 40, "WR6 outside the shrunken ring hurts"));
        // 波: 倒しつづけて三つ越える
        STAGES.add(() -> {
            put(p1, ctx.origin().offset(0, 1, 2));
            healAll();
            return true;
        });
        STAGES.add(() -> waitFor(() -> ringRoom().wave() >= 1 && !ctx.aliveMobs().isEmpty(), 400, "WR6 wave 1"));
        STAGES.add(() -> {
            // 湧いた敵は輪の中
            for (Mob m : ctx.aliveMobs()) {
                check(!ringRoom().outside(ctx, m.getX(), m.getZ()), "WR6: an enemy spawned outside the ring");
            }
            check(room.counter() == 1, "WR6: wave counter is not 1");
            return true;
        });
        STAGES.add(() -> {
            if (!(room instanceof jp.revati.skyabyss.trial.raid.mid.FightBossRoom fb && fb.bossStage())) {
                for (Mob m : new ArrayList<>(ctx.aliveMobs())) {
                    m.discard();
                }
            }
            healAll();
            return waitFor(() -> jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(room, null) && false || room.isComplete(), 1500, "WR6 complete (three waves)");
        });
        STAGES.add(() -> {
            check(ringRoom().wave() == 3, "WR6: completed with wave " + ringRoom().wave());
            // リセット: 輪も床も最初へ
            ctx.clearMobs();
            room.reset(ctx);
            WhaleRingRoom r = ringRoom();
            check(!room.isComplete() && r.wave() == 0 && r.radius() >= WhaleRingRoom.R0 - 1e-6 && ctx.aliveMobs().isEmpty(), "WR6: reset did not restore the state");
            for (int i = 0; i < SAMPLES.size(); i++) {
                check(level.getBlockState(SAMPLES.get(i)).equals(SAMPLE_STATES.get(i)), "WR6: the floor at " + SAMPLES.get(i).toShortString() + " was not restored");
            }
            driving = false;
            return true;
        });
    }
}
